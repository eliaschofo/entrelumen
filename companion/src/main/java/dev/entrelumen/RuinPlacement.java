package dev.entrelumen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Places the Plan v2 Heliodor ruins once per world (docs/design/heliodor-ruins.md). Overworld
 * ruins start when the first team opens their act, in a ring around the world spawn; dimension
 * ruins at the first arrival, in a ring around it. Nothing blocks the server thread:
 *
 * <ol>
 *   <li>the template is read and cut into 16-block cubes off-thread ({@link SolsticioCity#partition});
 *   <li>candidate sites are scored off-thread from the generator's noise (no chunk is loaded);
 *   <li>a candidate whose stored chunks show players spent time there is skipped;
 *   <li>the site's chunks load in the background under a ticket, the real terrain is checked
 *       (slope, water, structures), and the site is reserved in {@link RuinData};
 *   <li>the columns are cleared, the cubes placed, the foundation poured and the markers turned
 *       into their blocks, each step within a per-tick time budget.
 * </ol>
 *
 * Then the ruin is registered: protected by {@link StructureProtection}, an anchor of the compass
 * and the home of its challenges. One job runs at a time.
 */
public final class RuinPlacement {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final long TICK_BUDGET_NANOS = 8_000_000L;
  static final int CANDIDATES = 48, MAX_ATTEMPTS = 6, TRIGGER_INTERVAL = 100;
  /** Chunks where players spent more than this many ticks are someone's place: never built over. */
  static final long INHABITED_LIMIT = 6000;
  static final int EXCLUSION_MARGIN = 48, LOAD_TIMEOUT_TICKS = 6000, MAX_FOUNDATION = 12;
  /** A surface site this uneven (highest minus lowest sampled ground) is rejected. */
  static final int MAX_SPREAD = 10;
  static final TicketType<ChunkPos> TICKET =
      TicketType.create("entrelumen_ruin", Comparator.comparingLong(ChunkPos::toLong));
  private static final String STRUCTURE_BLOCK = "minecraft:structure_block";

  private static final Map<MinecraftServer, Deque<Job>> JOBS = new WeakHashMap<>();

  private RuinPlacement() {}

  // ---- The prepared template -----------------------------------------------------------------

  /** A marker in template coordinates. */
  record Local(RuinMarkers.Marker marker, int x, int y, int z) {}

  /**
   * A template read for placement: its size, the layer that meets the ground, its markers, the
   * cells it writes (so clearing leaves them alone), its columns above ground, the lowest solid
   * cell per column (for the foundation) and the cubes to place.
   */
  record Prepared(int sizeX, int sizeY, int sizeZ, int ground, List<Local> markers, LongOpenHashSet cells,
      long[] columns, long[] lowColumns, int[] lowY, List<StructureTemplate> slices, List<BlockPos> sliceOrigins,
      int blocks) {
    int above() {
      return sizeY - ground;
    }
  }

  static long cell(int x, int y, int z) {
    return BlockPos.asLong(x, y, z);
  }

  static long column(int x, int z) {
    return ((long) x << 32) | (z & 0xFFFFFFFFL);
  }

  /** Reads and cuts one template; safe off the server thread. */
  static Prepared prepare(MinecraftServer server, ResourceLocation template) throws IOException {
    var resource = server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(
        template.getNamespace(), "structure/" + template.getPath() + ".nbt"))
        .orElseThrow(() -> new IOException("Missing structure template " + template));
    CompoundTag tag;
    try (InputStream stream = resource.open()) {
      tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
    }
    int version = NbtUtils.getDataVersion(tag, 500);
    if (version < SharedConstants.getCurrentVersion().getDataVersion().getVersion())
      tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, version);
    return prepare(tag, template.toString());
  }

  static Prepared prepare(CompoundTag tag, String name) {
    var size = tag.getList("size", Tag.TAG_INT);
    int sizeX = size.getInt(0), sizeY = size.getInt(1), sizeZ = size.getInt(2);
    if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) throw new IllegalArgumentException(name + " is empty");
    var palette = tag.contains("palettes", Tag.TAG_LIST)
        ? tag.getList("palettes", Tag.TAG_LIST).getList(0) : tag.getList("palette", Tag.TAG_COMPOUND);
    boolean[] solid = new boolean[palette.size()];
    for (int i = 0; i < palette.size(); i++) {
      String block = palette.getCompound(i).getString("Name");
      solid[i] = !block.equals("minecraft:air") && !block.equals("minecraft:structure_void")
          && !block.equals("minecraft:cave_air") && !block.equals("minecraft:water")
          && !block.equals("minecraft:lava") && !block.equals(STRUCTURE_BLOCK);
    }
    var partition = SolsticioCity.partition(tag, 16, 0, 0, 0);
    List<Local> markers = new ArrayList<>();
    int ground = 0;
    for (int i = 0; i < partition.markerNames().size(); i++) {
      int[] at = partition.markerPositions().get(i);
      Optional<RuinMarkers.Marker> marker;
      try {
        marker = RuinMarkers.parse(partition.markerNames().get(i));
      } catch (IllegalArgumentException e) {
        LOGGER.warn("{}: {}", name, e.getMessage());
        continue;
      }
      if (marker.isEmpty()) {
        LOGGER.warn("{}: unknown marker '{}' at {},{},{}", name, partition.markerNames().get(i), at[0], at[1], at[2]);
        continue;
      }
      if (marker.get().kind() == RuinMarkers.Kind.GROUND) ground = at[1];
      markers.add(new Local(marker.get(), at[0], at[1], at[2]));
    }
    LongOpenHashSet cells = new LongOpenHashSet();
    Map<Long, Integer> lowest = new HashMap<>();
    LinkedHashSet<Long> columns = new LinkedHashSet<>();
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      CompoundTag block = (CompoundTag) element;
      var pos = block.getList("pos", Tag.TAG_INT);
      int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
      cells.add(cell(x, y, z));
      if (y >= ground) columns.add(column(x, z));
      int state = block.getInt("state");
      if (state >= 0 && state < solid.length && solid[state]) lowest.merge(column(x, z), y, Math::min);
    }
    int blocks = partition.blockCounts().stream().mapToInt(Integer::intValue).sum();
    List<StructureTemplate> slices = new ArrayList<>();
    List<BlockPos> origins = new ArrayList<>();
    for (int i = 0; i < partition.slices().size(); i++) {
      StructureTemplate template = new StructureTemplate();
      template.load(BuiltInRegistries.BLOCK.asLookup(), partition.slices().get(i));
      slices.add(template);
      int[] origin = partition.sliceOrigins().get(i);
      origins.add(new BlockPos(origin[0], origin[1], origin[2]));
    }
    long[] lowColumns = new long[lowest.size()];
    int[] lowY = new int[lowest.size()];
    int k = 0;
    for (var entry : new TreeMap<>(lowest).entrySet()) {
      lowColumns[k] = entry.getKey();
      lowY[k++] = entry.getValue();
    }
    return new Prepared(sizeX, sizeY, sizeZ, ground, List.copyOf(markers), cells,
        columns.stream().mapToLong(Long::longValue).toArray(), lowColumns, lowY, slices, origins, blocks);
  }

  // ---- Siting --------------------------------------------------------------------------------

  /** A scored candidate: its centre, the floor the ground layer lands on, and its score. */
  record Candidate(int x, int z, int floor, int score) {}

  /** Everything the off-thread site search reads; the generator and biome source are thread-safe. */
  record Siting(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, BiomeSource biomes,
      RuinDefinitions.Mode mode, long seed, String ruin, int cx, int cy, int cz, int min, int max,
      int sizeX, int sizeZ, int above, int below, List<ProtectionRules.Box> exclusions) {}

  static ProtectionRules.Box footprint(int cx, int cz, int sizeX, int sizeZ) {
    int x0 = cx - sizeX / 2, z0 = cz - sizeZ / 2;
    return new ProtectionRules.Box(x0, 0, z0, x0 + sizeX - 1, 0, z0 + sizeZ - 1);
  }

  /** Scores ring candidates from the noise, best first. Never touches a chunk. */
  static List<Candidate> site(Siting in) {
    List<Candidate> found = new ArrayList<>();
    for (int[] c : RuinRules.candidates(in.seed(), in.ruin(), in.cx(), in.cz(), in.min(), in.max(), CANDIDATES)) {
      if (!RuinRules.clear(footprint(c[0], c[1], in.sizeX(), in.sizeZ()), in.exclusions(), EXCLUSION_MARGIN))
        continue;
      Candidate candidate;
      try {
        candidate = switch (in.mode()) {
          case SURFACE -> surface(in, c[0], c[1]);
          case CAVERN -> cavern(in, c[0], c[1]);
          case SKY -> sky(in, c[0], c[1]);
        };
      } catch (RuntimeException e) {
        // A generator without usable noise: the real terrain decides once the chunks are loaded.
        candidate = new Candidate(c[0], c[1], Integer.MIN_VALUE, 1000);
      }
      if (candidate != null) found.add(candidate);
    }
    found.sort(Comparator.comparingInt(Candidate::score));
    return found;
  }

  private static int[][] samples(Siting in, int x, int z, int n) {
    int x0 = x - in.sizeX() / 2, z0 = z - in.sizeZ() / 2;
    int[][] points = new int[n * n][];
    for (int i = 0; i < n; i++)
      for (int j = 0; j < n; j++)
        points[i * n + j] = new int[] {x0 + in.sizeX() / 6 + i * (in.sizeX() * 2 / 3) / Math.max(1, n - 1),
            z0 + in.sizeZ() / 6 + j * (in.sizeZ() * 2 / 3) / Math.max(1, n - 1)};
    return points;
  }

  private static Candidate surface(Siting in, int x, int z) {
    int[][] points = samples(in, x, z, 5);
    int[] heights = new int[points.length];
    boolean[] water = new boolean[points.length];
    for (int i = 0; i < points.length; i++) {
      int top = in.generator().getBaseHeight(points[i][0], points[i][1], Heightmap.Types.WORLD_SURFACE_WG,
          in.height(), in.random()) - 1;
      int floor = in.generator().getBaseHeight(points[i][0], points[i][1], Heightmap.Types.OCEAN_FLOOR_WG,
          in.height(), in.random()) - 1;
      heights[i] = floor;
      water[i] = top > floor;
      if (floor <= in.height().getMinBuildHeight() + 1) return null;
    }
    int floor = RuinRules.floor(heights);
    var biome = in.biomes().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(floor), QuartPos.fromBlock(z),
        in.random().sampler());
    boolean suits = !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH);
    int score = RuinRules.score(new RuinRules.Sample(heights, water, suits), MAX_SPREAD);
    if (floor + in.above() >= in.height().getMaxBuildHeight() - 2) return null;
    return score == Integer.MAX_VALUE ? null : new Candidate(x, z, floor, score);
  }

  private static Candidate cavern(Siting in, int x, int z) {
    int[][] points = samples(in, x, z, 3);
    int bottom = in.height().getMinBuildHeight();
    int[] floors = new int[points.length];
    for (int i = 0; i < points.length; i++) {
      var column = in.generator().getBaseColumn(points[i][0], points[i][1], in.height(), in.random());
      boolean[] solid = new boolean[in.height().getHeight()], lava = new boolean[in.height().getHeight()];
      for (int k = 0; k < solid.length; k++) {
        BlockState state = column.getBlock(bottom + k);
        lava[k] = state.getFluidState().is(Fluids.LAVA);
        solid[k] = !state.isAir() && state.getFluidState().isEmpty();
      }
      floors[i] = RuinRules.cavernFloor(solid, lava, bottom, Math.max(bottom + in.below() + 4, 30), 100,
          Math.min(in.above() + 2, 40));
      if (floors[i] < 0) return null;
    }
    int floor = RuinRules.floor(floors);
    int spread = Arrays.stream(floors).max().orElse(0) - Arrays.stream(floors).min().orElse(0);
    if (spread > MAX_SPREAD) return null;
    return new Candidate(x, z, floor, spread * 8 + Math.abs(floor - 32));
  }

  private static Candidate sky(Siting in, int x, int z) {
    int y = Math.clamp(in.cy() + 8, in.height().getMinBuildHeight() + in.below() + 16,
        in.height().getMaxBuildHeight() - in.above() - 16);
    int[][] points = samples(in, x, z, 3);
    int crowded = 0;
    for (int[] point : points) {
      var column = in.generator().getBaseColumn(point[0], point[1], in.height(), in.random());
      for (int k = y - in.below() - 2; k <= y + in.above() + 2; k++)
        if (!column.getBlock(k).isAir()) crowded++;
    }
    return new Candidate(x, z, y, crowded * 4);
  }

  // ---- Jobs ----------------------------------------------------------------------------------

  enum Stage { PREPARING, SITING, CHECKING, LOADING, EVALUATING, CLEARING, PLACING, FOUNDATION, FINISHING }

  static final class Job {
    final RuinDefinitions.Definition definition;
    final ResourceLocation id;
    final ServerLevel level;
    final BlockPos center;
    Stage stage = Stage.PREPARING;
    CompletableFuture<Prepared> preparing;
    Prepared prepared;
    CompletableFuture<List<Candidate>> siting;
    List<Candidate> candidates = List.of();
    int candidate, attempts;
    final Map<Long, CompletableFuture<Optional<CompoundTag>>> occupancy = new HashMap<>();
    final List<ChunkPos> tickets = new ArrayList<>();
    int waited;
    BlockPos origin;
    /** The reserved origin to resume at, skipping the search. */
    BlockPos resume;
    int columnIndex, sliceIndex;
    int lowest;
    final long startedAt = System.nanoTime();
    long busyNanos, worstNanos;
    int busyTicks;
    boolean done, failed;
    RuinData.Ruin result;
    Candidate best;
    int bestScore = Integer.MAX_VALUE;

    Job(RuinDefinitions.Definition definition, ServerLevel level, BlockPos center) {
      this.definition = definition;
      this.id = ResourceLocation.parse(definition.id());
      this.level = level;
      this.center = center.immutable();
    }

    public boolean done() {
      return done;
    }

    public boolean failed() {
      return failed;
    }

    public Stage stage() {
      return stage;
    }

    @Nullable
    public RuinData.Ruin result() {
      return result;
    }
  }

  static Deque<Job> queue(MinecraftServer server) {
    synchronized (JOBS) {
      return JOBS.computeIfAbsent(server, ignored -> new ArrayDeque<>());
    }
  }

  public static boolean busy(MinecraftServer server, ResourceLocation id) {
    synchronized (JOBS) {
      var queue = JOBS.get(server);
      return queue != null && queue.stream().anyMatch(job -> job.id.equals(id));
    }
  }

  /**
   * Queues the placement of {@code definition} in {@code level} around {@code center}, unless it is
   * placed or queued already. Returns the job (the queued one if it existed), or empty when placed.
   */
  public static Optional<Job> start(ServerLevel level, RuinDefinitions.Definition definition, BlockPos center) {
    var id = ResourceLocation.parse(definition.id());
    var data = RuinData.get(level.getServer());
    if (data.find(id).isPresent()) return Optional.empty();
    synchronized (JOBS) {
      var queue = queue(level.getServer());
      for (Job job : queue) if (job.id.equals(id)) return Optional.of(job);
      Job job = new Job(definition, level, center);
      data.reservation(id).filter(r -> r.dimension().equals(level.dimension())).ifPresent(r -> job.resume = r.origin());
      queue.add(job);
      LOGGER.info("Queued Heliodor ruin {} in {} around {}{}", id, level.dimension().location(),
          center.toShortString(), job.resume == null ? "" : " (resuming at " + job.resume.toShortString() + ")");
      return Optional.of(job);
    }
  }

  /** Starts {@code definition} at a fixed template origin, skipping the site search (commands, tests). */
  public static Optional<Job> startAt(ServerLevel level, RuinDefinitions.Definition definition, BlockPos origin) {
    var job = start(level, definition, origin);
    job.ifPresent(j -> {
      if (j.stage == Stage.PREPARING && j.resume == null) j.resume = origin.immutable();
    });
    return job;
  }

  /** Called every server tick: triggers, then the oldest job within the budget. */
  public static void tick(MinecraftServer server) {
    if (server.getTickCount() % TRIGGER_INTERVAL == 0 && !(server instanceof GameTestServer)) actTriggers(server);
    Job job;
    synchronized (JOBS) {
      var queue = JOBS.get(server);
      job = queue == null ? null : queue.peekFirst();
    }
    if (job == null) return;
    long start = System.nanoTime();
    try {
      step(job, start + TICK_BUDGET_NANOS);
    } catch (RuntimeException e) {
      LOGGER.error("Heliodor ruin {} could not be placed", job.id, e);
      job.failed = true;
    }
    long spent = System.nanoTime() - start;
    job.busyNanos += spent;
    job.worstNanos = Math.max(job.worstNanos, spent);
    job.busyTicks++;
    if (job.done || job.failed) {
      release(job);
      synchronized (JOBS) {
        queue(server).remove(job);
      }
    }
  }

  /** Overworld ruins whose act some team has opened. */
  static void actTriggers(MinecraftServer server) {
    int act = 0;
    var campaigns = CampaignData.get(server).campaigns;
    for (var campaign : campaigns.personal.values()) if (!campaign.archived) act = Math.max(act, campaign.act);
    for (var campaign : campaigns.parties.values()) if (!campaign.archived) act = Math.max(act, campaign.act);
    if (act == 0) return;
    var data = RuinData.get(server);
    ServerLevel overworld = server.overworld();
    for (var definition : RuinRegistry.available()) {
      if (definition.placement().trigger() != RuinDefinitions.Trigger.ACT || definition.act() > act) continue;
      var id = ResourceLocation.parse(definition.id());
      if (data.find(id).isPresent() || busy(server, id)) continue;
      if (!definition.dimension().equals(overworld.dimension().location().toString())) continue;
      start(overworld, definition, overworld.getSharedSpawnPos());
    }
  }

  /** Dimension ruins wait for the first arrival; a reserved site resumes when anyone is there. */
  public static void onArrival(ServerPlayer player) {
    if (player.server instanceof GameTestServer || player.isSpectator()) return;
    ServerLevel level = player.serverLevel();
    var data = RuinData.get(player.server);
    String dimension = level.dimension().location().toString();
    for (var definition : RuinRegistry.available()) {
      if (definition.placement().trigger() != RuinDefinitions.Trigger.ARRIVAL
          || !definition.dimension().equals(dimension)) continue;
      var id = ResourceLocation.parse(definition.id());
      if (data.find(id).isPresent() || busy(player.server, id)) continue;
      start(level, definition, player.blockPosition());
    }
  }

  public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) onArrival(player);
  }

  public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player && !player.serverLevel().dimension().equals(Level.OVERWORLD))
      onArrival(player);
  }

  /** Reserved sites of an interrupted run resume on start (their chunks load under the ticket). */
  public static void resumeReserved(MinecraftServer server) {
    if (server instanceof GameTestServer) return;
    var data = RuinData.get(server);
    for (var reservation : List.copyOf(data.reservations())) {
      var level = server.getLevel(reservation.dimension());
      var definition = RuinRegistry.get(reservation.id()).filter(d -> d.available(RuinRegistry::modLoaded));
      if (level == null || definition.isEmpty()) continue;
      start(level, definition.get(), reservation.origin());
    }
  }

  static void step(Job job, long deadline) {
    switch (job.stage) {
      case PREPARING -> {
        if (job.preparing == null) {
          MinecraftServer server = job.level.getServer();
          var template = ResourceLocation.parse(job.definition.template());
          job.preparing = CompletableFuture.supplyAsync(() -> {
            try {
              return prepare(server, template);
            } catch (IOException e) {
              throw new java.io.UncheckedIOException(e);
            }
          }, Util.backgroundExecutor());
          return;
        }
        if (!job.preparing.isDone()) return;
        job.prepared = job.preparing.join();
        if (job.resume != null) {
          job.origin = job.resume;
          job.stage = Stage.LOADING;
          addTickets(job);
          return;
        }
        job.stage = Stage.SITING;
      }
      case SITING -> {
        if (job.siting == null) {
          var p = job.prepared;
          var placement = job.definition.placement();
          var source = job.level.getChunkSource();
          var siting = new Siting(source.getGenerator(), source.randomState(), job.level,
              source.getGenerator().getBiomeSource(), placement.mode(), job.level.getSeed(), job.definition.id(),
              job.center.getX(), job.center.getY(), job.center.getZ(), placement.min(), placement.max(),
              p.sizeX(), p.sizeZ(), p.above(), p.ground(), exclusions(job.level));
          job.siting = CompletableFuture.supplyAsync(() -> site(siting), Util.backgroundExecutor());
          return;
        }
        if (!job.siting.isDone()) return;
        job.candidates = job.siting.join();
        if (job.candidates.isEmpty()) {
          // Nothing scored well: fall back to the ring's candidates, judged on the real terrain.
          List<Candidate> fallback = new ArrayList<>();
          for (int[] c : RuinRules.candidates(job.level.getSeed(), job.definition.id(), job.center.getX(),
              job.center.getZ(), job.definition.placement().min(), job.definition.placement().max(), 8))
            fallback.add(new Candidate(c[0], c[1], Integer.MIN_VALUE, 1000));
          job.candidates = fallback;
        }
        LOGGER.info("Heliodor ruin {}: {} candidate sites, best {}", job.id, job.candidates.size(),
            job.candidates.getFirst());
        job.stage = Stage.CHECKING;
      }
      case CHECKING -> checking(job);
      case LOADING -> {
        for (ChunkPos chunk : job.tickets)
          if (job.level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
            if (++job.waited > LOAD_TIMEOUT_TICKS) {
              LOGGER.warn("Heliodor ruin {}: chunks at {} did not load; trying another site", job.id, job.origin);
              nextCandidate(job);
            }
            return;
          }
        job.stage = job.resume != null ? Stage.CLEARING : Stage.EVALUATING;
        if (job.resume != null) reserve(job);
      }
      case EVALUATING -> evaluate(job);
      case CLEARING -> clearing(job, deadline);
      case PLACING -> placing(job, deadline);
      case FOUNDATION -> foundation(job, deadline);
      case FINISHING -> finish(job);
    }
  }

  private static List<ProtectionRules.Box> exclusions(ServerLevel level) {
    List<ProtectionRules.Box> boxes = new ArrayList<>();
    var data = RuinData.get(level.getServer());
    for (var ruin : data.ruins())
      if (ruin.dimension().equals(level.dimension())) boxes.add(StructureProtection.box(ruin.box()));
    for (var reservation : data.reservations())
      if (reservation.dimension().equals(level.dimension()))
        boxes.add(new ProtectionRules.Box(reservation.origin().getX(), 0, reservation.origin().getZ(),
            reservation.origin().getX() + 128, 0, reservation.origin().getZ() + 128));
    for (var region : StructureProtection.regions(level)) boxes.add(region.box());
    return boxes;
  }

  private static List<ChunkPos> chunks(int x0, int z0, int sizeX, int sizeZ, int margin) {
    List<ChunkPos> chunks = new ArrayList<>();
    for (int cx = (x0 - margin) >> 4; cx <= (x0 + sizeX - 1 + margin) >> 4; cx++)
      for (int cz = (z0 - margin) >> 4; cz <= (z0 + sizeZ - 1 + margin) >> 4; cz++)
        chunks.add(new ChunkPos(cx, cz));
    return chunks;
  }

  /** Skips candidates where players already spent time: a base, a farm, a path they use. */
  private static void checking(Job job) {
    if (job.candidate >= job.candidates.size()) {
      if (job.best == null) {
        LOGGER.error("Heliodor ruin {}: every candidate site is inhabited or unusable; not placed", job.id);
        job.failed = true;
        return;
      }
      LOGGER.warn("Heliodor ruin {}: no candidate passed; using the best seen ({})", job.id, job.best);
      job.origin = origin(job, job.best.x(), job.best.z(), job.best.floor());
      job.stage = Stage.LOADING;
      job.resume = job.origin;
      addTickets(job);
      return;
    }
    var candidate = job.candidates.get(job.candidate);
    var p = job.prepared;
    int x0 = candidate.x() - p.sizeX() / 2, z0 = candidate.z() - p.sizeZ() / 2;
    var chunkMap = job.level.getChunkSource().chunkMap;
    boolean pending = false;
    for (ChunkPos chunk : chunks(x0, z0, p.sizeX(), p.sizeZ(), 0)) {
      var loaded = job.level.getChunkSource().getChunkNow(chunk.x, chunk.z);
      long inhabited;
      if (loaded != null) {
        inhabited = loaded.getInhabitedTime();
      } else {
        var future = job.occupancy.computeIfAbsent(chunk.toLong(), key -> chunkMap.read(chunk));
        if (!future.isDone()) {
          pending = true;
          continue;
        }
        inhabited = future.join().map(stored -> stored.getLong("InhabitedTime")).orElse(0L);
      }
      if (inhabited > INHABITED_LIMIT) {
        LOGGER.info("Heliodor ruin {}: site {},{} skipped, players spent {} ticks in chunk {}", job.id,
            candidate.x(), candidate.z(), inhabited, chunk);
        job.occupancy.clear();
        job.candidate++;
        return;
      }
    }
    if (pending) return;
    job.occupancy.clear();
    job.origin = origin(job, candidate.x(), candidate.z(), candidate.floor());
    job.stage = Stage.LOADING;
    addTickets(job);
  }

  private static BlockPos origin(Job job, int cx, int cz, int floor) {
    var p = job.prepared;
    int y = floor == Integer.MIN_VALUE ? job.level.getSeaLevel() : floor;
    return new BlockPos(cx - p.sizeX() / 2, y - p.ground(), cz - p.sizeZ() / 2);
  }

  private static void addTickets(Job job) {
    release(job);
    var p = job.prepared;
    job.waited = 0;
    for (ChunkPos chunk : chunks(job.origin.getX(), job.origin.getZ(), p.sizeX(), p.sizeZ(), 1)) {
      job.level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
      job.tickets.add(chunk);
    }
  }

  private static void release(Job job) {
    for (ChunkPos chunk : job.tickets) job.level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
    job.tickets.clear();
  }

  private static void nextCandidate(Job job) {
    release(job);
    job.resume = null;
    job.candidate++;
    job.attempts++;
    job.stage = Stage.CHECKING;
    if (job.attempts >= MAX_ATTEMPTS) job.candidate = job.candidates.size();
  }

  /** The real terrain under a candidate, now that its chunks are loaded. */
  private static void evaluate(Job job) {
    var p = job.prepared;
    var level = job.level;
    int x0 = job.origin.getX(), z0 = job.origin.getZ();
    var mode = job.definition.placement().mode();
    int floor;
    int score;
    if (mode == RuinDefinitions.Mode.SURFACE) {
      int n = 7;
      int[] heights = new int[n * n];
      boolean[] water = new boolean[n * n];
      int canopy = 0;
      for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++) {
          int x = x0 + p.sizeX() / 8 + i * (p.sizeX() * 3 / 4) / (n - 1);
          int z = z0 + p.sizeZ() / 8 + j * (p.sizeZ() * 3 / 4) / (n - 1);
          int free = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
          heights[i * n + j] = free - 1;
          water[i * n + j] = !level.getBlockState(new BlockPos(x, free - 1, z)).getFluidState().isEmpty();
          if (level.getBlockState(new BlockPos(x, free, z)).is(BlockTags.LOGS)) canopy++;
        }
      floor = RuinRules.floor(heights);
      score = RuinRules.score(new RuinRules.Sample(heights, water, true), MAX_SPREAD);
      if (score != Integer.MAX_VALUE) score += canopy * 2 + structures(level, x0, z0, p, floor) * 100;
    } else if (mode == RuinDefinitions.Mode.CAVERN) {
      int[] floors = new int[9];
      for (int i = 0; i < 9; i++) {
        int x = x0 + p.sizeX() / 6 + (i % 3) * (p.sizeX() / 3), z = z0 + p.sizeZ() / 6 + (i / 3) * (p.sizeZ() / 3);
        floors[i] = realCavernFloor(level, x, z, p);
      }
      if (Arrays.stream(floors).anyMatch(f -> f < 0)) {
        floor = job.origin.getY() + p.ground();
        score = Integer.MAX_VALUE;
      } else {
        floor = RuinRules.floor(floors);
        score = (Arrays.stream(floors).max().getAsInt() - Arrays.stream(floors).min().getAsInt()) * 8;
      }
    } else {
      floor = job.origin.getY() + p.ground();
      int crowded = 0;
      for (int x = x0; x < x0 + p.sizeX(); x += 4)
        for (int z = z0; z < z0 + p.sizeZ(); z += 4)
          for (int y = floor - p.ground(); y < floor + p.above(); y += 2)
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) crowded++;
      score = crowded;
    }
    var candidate = new Candidate(x0 + p.sizeX() / 2, z0 + p.sizeZ() / 2, floor, score);
    if (score < job.bestScore) {
      job.bestScore = score;
      job.best = candidate;
    }
    boolean good = switch (mode) {
      case SURFACE -> score <= 400;
      case CAVERN -> score <= MAX_SPREAD * 8;
      case SKY -> score <= 24;
    };
    if (!good) {
      LOGGER.info("Heliodor ruin {}: site {} rejected on the real terrain (score {})", job.id,
          job.origin.toShortString(), score);
      nextCandidate(job);
      return;
    }
    // Only the height can change here, so the chunks under the ticket stay the same.
    job.origin = new BlockPos(x0, floor - p.ground(), z0);
    reserve(job);
    job.stage = Stage.CLEARING;
  }

  private static void reserve(Job job) {
    RuinData.get(job.level.getServer()).reserve(
        new RuinData.Reservation(job.id, job.level.dimension(), job.origin, job.level.getGameTime()));
    LOGGER.info("Heliodor ruin {} reserved at {} in {}", job.id, job.origin.toShortString(),
        job.level.dimension().location());
  }

  /** Surface structures (villages, outposts...) crossing the footprint, on a coarse grid. */
  private static int structures(ServerLevel level, int x0, int z0, Prepared p, int floor) {
    int hits = 0;
    for (int x = x0 + 2; x < x0 + p.sizeX(); x += 8)
      for (int z = z0 + 2; z < z0 + p.sizeZ(); z += 8)
        if (level.structureManager().getStructureWithPieceAt(new BlockPos(x, floor + 2, z), holder -> true).isValid())
          hits++;
    return hits;
  }

  private static int realCavernFloor(ServerLevel level, int x, int z, Prepared p) {
    int bottom = level.getMinBuildHeight(), height = level.getHeight();
    boolean[] solid = new boolean[height], lava = new boolean[height];
    var cursor = new BlockPos.MutableBlockPos(x, 0, z);
    for (int k = 0; k < height; k++) {
      BlockState state = level.getBlockState(cursor.setY(bottom + k));
      lava[k] = state.getFluidState().is(Fluids.LAVA);
      solid[k] = !state.isAir() && state.getFluidState().isEmpty();
    }
    return RuinRules.cavernFloor(solid, lava, bottom, Math.max(bottom + p.ground() + 4, 30), 100,
        Math.min(p.above() + 2, 40));
  }

  /** Clears terrain and plants inside the ruin's columns above the ground layer. */
  private static void clearing(Job job, long deadline) {
    var p = job.prepared;
    if (job.definition.placement().mode() == RuinDefinitions.Mode.SKY) {
      job.stage = Stage.PLACING;
      return;
    }
    var cursor = new BlockPos.MutableBlockPos();
    while (job.columnIndex < p.columns().length) {
      long column = p.columns()[job.columnIndex++];
      int x = (int) (column >> 32), z = (int) column;
      for (int y = p.ground() + 1; y < p.sizeY(); y++) {
        if (p.cells().contains(cell(x, y, z))) continue;
        cursor.set(job.origin.getX() + x, job.origin.getY() + y, job.origin.getZ() + z);
        if (!job.level.getBlockState(cursor).isAir())
          job.level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
      if (System.nanoTime() > deadline) return;
    }
    job.stage = Stage.PLACING;
  }

  private static void placing(Job job, long deadline) {
    var p = job.prepared;
    var settings = new StructurePlaceSettings().setKnownShape(true);
    while (job.sliceIndex < p.slices().size()) {
      BlockPos at = job.origin.offset(p.sliceOrigins().get(job.sliceIndex));
      p.slices().get(job.sliceIndex++).placeInWorld(job.level, at, at, settings, job.level.getRandom(),
          Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      if (System.nanoTime() > deadline) return;
    }
    job.columnIndex = 0;
    job.lowest = job.origin.getY();
    job.stage = Stage.FOUNDATION;
  }

  /** Fills dips under the ruin's lowest solid cells so nothing floats (surface ruins only). */
  private static void foundation(Job job, long deadline) {
    var p = job.prepared;
    if (job.definition.placement().mode() != RuinDefinitions.Mode.SURFACE) {
      job.stage = Stage.FINISHING;
      return;
    }
    while (job.columnIndex < p.lowColumns().length) {
      int index = job.columnIndex++;
      int x = (int) (p.lowColumns()[index] >> 32), z = (int) p.lowColumns()[index];
      int y = p.lowY()[index];
      if (y > p.ground()) continue;
      BlockPos base = job.origin.offset(x, y, z);
      BlockState floor = job.level.getBlockState(base);
      BlockState fill = floor.isCollisionShapeFullBlock(job.level, base) ? floor : Blocks.TUFF.defaultBlockState();
      for (int depth = 1; depth <= MAX_FOUNDATION; depth++) {
        BlockPos pos = base.below(depth);
        BlockState state = job.level.getBlockState(pos);
        if (!state.canBeReplaced() && !state.is(BlockTags.LEAVES)) break;
        job.level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
        job.lowest = Math.min(job.lowest, pos.getY());
      }
      if (System.nanoTime() > deadline) return;
    }
    job.stage = Stage.FINISHING;
  }

  /** Turns markers into their blocks and registers the ruin. */
  private static void finish(Job job) {
    var p = job.prepared;
    var level = job.level;
    List<RuinData.PlacedMarker> placed = new ArrayList<>();
    List<BlockPos> pedestals = new ArrayList<>();
    BlockPos arrival = null;
    for (Local local : p.markers()) {
      BlockPos pos = job.origin.offset(local.x(), local.y(), local.z());
      var marker = local.marker();
      if (marker.kind() == RuinMarkers.Kind.GROUND) {
        BlockState state = blockOf(marker);
        if (state != null) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        continue;
      }
      placeMarker(level, job.definition, marker, pos);
      if (marker.kind() == RuinMarkers.Kind.PEDESTAL) pedestals.add(pos);
      if (marker.kind() == RuinMarkers.Kind.ARRIVAL && arrival == null) arrival = pos;
      placed.add(new RuinData.PlacedMarker(marker, pos));
    }
    var box = new BoundingBox(job.origin.getX(), Math.min(job.lowest, job.origin.getY()), job.origin.getZ(),
        job.origin.getX() + p.sizeX() - 1, job.origin.getY() + p.sizeY() - 1, job.origin.getZ() + p.sizeZ() - 1);
    if (arrival == null) arrival = HeliodorRuins.besideRuin(level, box);
    var ruin = new RuinData.Ruin(job.id, ResourceLocation.parse(job.definition.template()), level.dimension(),
        job.definition.act(), box, job.origin, arrival, pedestals, level.getGameTime(), placed);
    var data = RuinData.get(level.getServer());
    data.add(ruin);
    StructureProtection.invalidate(level.getServer());
    job.result = ruin;
    job.done = true;
    LOGGER.info("Placed Heliodor ruin {} at {} in {}: {} template blocks, {} markers, {} ms busy over {} ticks"
            + " (worst tick {} ms), {} ms wall", job.id, box, level.dimension().location(), p.blocks(), placed.size(),
        String.format(Locale.ROOT, "%.1f", job.busyNanos / 1e6), job.busyTicks,
        String.format(Locale.ROOT, "%.1f", job.worstNanos / 1e6),
        String.format(Locale.ROOT, "%.0f", (System.nanoTime() - job.startedAt) / 1e6));
  }

  /** Parses a marker's {@code block=} state; null when absent or unknown. */
  @Nullable
  static BlockState blockOf(RuinMarkers.Marker marker) {
    String block = marker.block();
    if (block.isEmpty()) return null;
    try {
      return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), block, false).blockState();
    } catch (CommandSyntaxException e) {
      LOGGER.warn("Ruin marker '{}': unknown block state {}", marker.metadata(), block);
      return null;
    }
  }

  /** Puts a marker's block in its cell. */
  static void placeMarker(ServerLevel level, RuinDefinitions.Definition definition, RuinMarkers.Marker marker,
      BlockPos pos) {
    BlockState state = switch (marker.kind()) {
      case PEDESTAL -> RuinContent.PEDESTAL.get().defaultBlockState();
      case MIRROR -> RuinContent.MIRROR.get().defaultBlockState()
          .setValue(RuinBlocks.AIM, RuinMarkers.DIRECTIONS.indexOf(marker.param("facing", "n")));
      case SOCKET -> RuinContent.SOCKET.get().defaultBlockState()
          .setValue(RuinBlocks.SOCKET_LOOK, RuinBlocks.SocketLook.of(marker.param("look", "pot")));
      case HIDDEN -> RuinContent.HIDDEN.get().defaultBlockState()
          .setValue(RuinBlocks.LOOK, RuinBlocks.Look.of(marker.param("look", "tuff_bricks")));
      case GATE -> RuinContent.GATE.get().defaultBlockState()
          .setValue(RuinBlocks.LOOK, RuinBlocks.Look.of(marker.param("look", "seal")))
          .setValue(RuinBlocks.CLIMB, "true".equals(marker.param("climb", "false")));
      case LOCK -> RuinContent.LOCK.get().defaultBlockState();
      case CHEST -> container(marker);
      default -> Optional.ofNullable(blockOf(marker)).orElse(Blocks.AIR.defaultBlockState());
    };
    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    if (marker.kind() == RuinMarkers.Kind.CHEST) {
      String loot = marker.param("loot", definition == null ? "" : definition.loot());
      if (!loot.isEmpty())
        RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos,
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(loot)));
    }
  }

  /** The marker's chest or barrel, as its Lootr counterpart when Lootr is loaded. */
  static BlockState container(RuinMarkers.Marker marker) {
    BlockState vanilla = Optional.ofNullable(blockOf(marker)).orElse(Blocks.CHEST.defaultBlockState());
    if (!RuinRegistry.modLoaded("lootr")) return vanilla;
    String lootr = vanilla.is(Blocks.BARREL) ? "lootr:lootr_barrel"
        : vanilla.is(Blocks.TRAPPED_CHEST) ? "lootr:lootr_trapped_chest" : "lootr:lootr_chest";
    var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(lootr));
    if (block.isEmpty()) return vanilla;
    BlockState state = block.get().defaultBlockState();
    for (Property<?> property : vanilla.getProperties()) state = copy(state, vanilla, property);
    return state;
  }

  private static <T extends Comparable<T>> BlockState copy(BlockState target, BlockState source, Property<T> property) {
    return target.hasProperty(property) ? target.setValue(property, source.getValue(property)) : target;
  }

  /** A loot table key, for loot tests. */
  static ResourceKey<LootTable> loot(String id) {
    return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
  }
}
