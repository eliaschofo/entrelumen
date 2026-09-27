package dev.entrelumen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 *   <li>the template is read and cut into 8-block cubes off-thread ({@link SolsticioCity#partition});
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
  static final long TICK_BUDGET_NANOS = 8_000_000L, SURVEY_BUDGET_NANOS = 40_000_000L;
  /**
   * Edge of the cubes a template is cut into. A cube is placed whole, so its size bounds the worst
   * tick (with 16-block cubes, one Temple tick took 67 ms in the isolated GameTests).
   */
  static final int SLICE = 8;
  static final int CANDIDATES = 48, MAX_ATTEMPTS = 6, TRIGGER_INTERVAL = 100;
  /** Chunks where players spent more than this many ticks are someone's place: never built over. */
  static final long INHABITED_LIMIT = 6000;
  static final int EXCLUSION_MARGIN = 48, LOAD_TIMEOUT_TICKS = 6000;
  /** At most this many blocks of trees are felled around one ruin. */
  static final int MAX_FELLED = 40_000;
  /**
   * A surface site whose ground under the ruin spreads more than this (highest minus lowest) is not
   * suitable; when no candidate of the ring is, the least uneven one wins and the blend meets it.
   */
  static final int MAX_SPREAD = 10;
  static final TicketType<ChunkPos> TICKET =
      TicketType.create("entrelumen_ruin", Comparator.comparingLong(ChunkPos::toLong));
  private static final String STRUCTURE_BLOCK = "minecraft:structure_block";

  private static final Map<MinecraftServer, Deque<Job>> JOBS = new WeakHashMap<>();
  /** A ruin whose placement failed waits this long before a trigger tries it again. */
  static final long RETRY_TICKS = 12_000;
  private static final Map<MinecraftServer, Map<ResourceLocation, Long>> FAILED = new WeakHashMap<>();

  private static boolean waiting(MinecraftServer server, ResourceLocation id) {
    synchronized (FAILED) {
      Long at = FAILED.getOrDefault(server, Map.of()).get(id);
      return at != null && server.overworld().getGameTime() - at < RETRY_TICKS;
    }
  }

  private RuinPlacement() {}

  // ---- The prepared template -----------------------------------------------------------------

  /** A marker in template coordinates. */
  record Local(RuinMarkers.Marker marker, int x, int y, int z) {}

  /**
   * A template read for placement: its size, the layer that meets the ground, its markers, the
   * cells it writes (so clearing leaves them alone), its columns above ground, the lowest solid
   * cell per column, the cubes to place and how it meets the terrain.
   */
  record Prepared(int sizeX, int sizeY, int sizeZ, int ground, List<Local> markers, LongOpenHashSet cells,
      long[] columns, long[] lowColumns, int[] lowY, List<StructureTemplate> slices, List<BlockPos> sliceOrigins,
      int blocks, Shape shape) {
    int above() {
      return sizeY - ground;
    }

    boolean cell(int x, int y, int z) {
      return x >= 0 && z >= 0 && x < sizeX && z < sizeZ && cells.contains(RuinPlacement.cell(x, y, z));
    }
  }

  /**
   * How a template meets the terrain ({@link RuinTerrain}), per column (index {@code z * sizeX + x}):
   * whether it stands on the ground layer, whether that part is platform or a thin support, its lowest
   * and highest solid cells (-1: none), the block a thin support goes down in, the ruin's masonry for
   * terrace faces, the distance of every column of the grid grown by {@link RuinTerrain#MAX_MARGIN} to
   * the platforms, the soil cells the site's ground replaces and the {@code keep_soil} boxes.
   */
  record Shape(boolean[] contact, boolean[] platform, int[] low, int[] top, BlockState[] pier, BlockState masonry,
      float[] distance, long[] soil, List<ProtectionRules.Box> keep) {
    boolean thin(int column) {
      return contact[column] && !platform[column];
    }

    boolean platformless() {
      for (boolean p : platform) if (p) return false;
      return true;
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
    var partition = SolsticioCity.partition(tag, SLICE, 0, 0, 0);
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
        columns.stream().mapToLong(Long::longValue).toArray(), lowColumns, lowY, slices, origins, blocks,
        shape(tag, palette, sizeX, sizeZ, ground, markers));
  }

  /** How the template meets the terrain; see {@link Shape}. */
  static Shape shape(CompoundTag tag, net.minecraft.nbt.ListTag palette, int sizeX, int sizeZ, int ground,
      List<Local> markers) {
    int n = sizeX * sizeZ;
    BlockState[] states = new BlockState[palette.size()];
    String[] names = new String[palette.size()];
    boolean[] solid = new boolean[palette.size()], full = new boolean[palette.size()];
    for (int i = 0; i < palette.size(); i++) {
      names[i] = palette.getCompound(i).getString("Name");
      states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
      solid[i] = !states[i].isAir() && states[i].getFluidState().isEmpty() && !names[i].equals(STRUCTURE_BLOCK)
          && !names[i].equals("minecraft:structure_void");
      full[i] = solid[i] && states[i].isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }
    List<ProtectionRules.Box> keep = new ArrayList<>();
    for (Local local : markers)
      if (local.marker().kind() == RuinMarkers.Kind.KEEP_SOIL) {
        int[] size = local.marker().size();
        keep.add(new ProtectionRules.Box(local.x(), local.y(), local.z(), local.x() + size[0] - 1,
            local.y() + size[1] - 1, local.z() + size[2] - 1));
      }
    int[] low = new int[n], top = new int[n];
    Arrays.fill(low, Integer.MAX_VALUE);
    Arrays.fill(top, -1);
    var blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
    for (Tag element : blocks) {
      CompoundTag block = (CompoundTag) element;
      var pos = block.getList("pos", Tag.TAG_INT);
      int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2), state = block.getInt("state");
      if (state < 0 || state >= solid.length || !solid[state]) continue;
      low[z * sizeX + x] = Math.min(low[z * sizeX + x], y);
      top[z * sizeX + x] = Math.max(top[z * sizeX + x], y);
    }
    // A marker that stands for a block counts as that block (the ground marker is a floor cell).
    for (Local local : markers) {
      String block = local.marker().block();
      if (block.isEmpty() || block.endsWith(":air") || block.equals("air")) continue;
      int column = local.z() * sizeX + local.x();
      low[column] = Math.min(low[column], local.y());
      top[column] = Math.max(top[column], local.y());
    }
    boolean[] contact = new boolean[n];
    for (int i = 0; i < n; i++) {
      contact[i] = low[i] <= ground + 1;
      if (low[i] == Integer.MAX_VALUE) low[i] = -1;
    }
    boolean[] platform = RuinTerrain.opening(contact, sizeX, sizeZ, RuinTerrain.PLATFORM_RADIUS);
    // The masonry: the commonest opaque full block of the platforms' ground layer that is not soil.
    List<BlockState> floor = new ArrayList<>(), any = new ArrayList<>();
    BlockState[] pier = new BlockState[n];
    int[] pierY = new int[n];
    Arrays.fill(pierY, Integer.MAX_VALUE);
    LongArrayList soil = new LongArrayList();
    for (Tag element : blocks) {
      CompoundTag block = (CompoundTag) element;
      var pos = block.getList("pos", Tag.TAG_INT);
      int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2), state = block.getInt("state");
      if (state < 0 || state >= solid.length) continue;
      int column = z * sizeX + x;
      if (RuinTerrain.soil(names[state])) {
        if (!RuinTerrain.kept(x, y, z, keep)) soil.add(cell(x, y, z));
        continue;
      }
      if (!full[state]) continue;
      any.add(states[state]);
      if (y == ground && platform[column]) floor.add(states[state]);
      // A thin support goes down in its lowest opaque full block near its foot.
      if (contact[column] && !platform[column] && y <= low[column] + 4 && y < pierY[column]) {
        pierY[column] = y;
        pier[column] = states[state];
      }
    }
    BlockState masonry = RuinTerrain.mostCommon(floor, RuinTerrain.mostCommon(any, Blocks.STONE_BRICKS.defaultBlockState()));
    for (int i = 0; i < n; i++) if (contact[i] && !platform[i] && pier[i] == null) pier[i] = masonry;
    return new Shape(contact, platform, low, top, pier, masonry,
        RuinTerrain.distance(platform, sizeX, sizeZ, RuinTerrain.MAX_MARGIN), soil.toLongArray(), List.copyOf(keep));
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
    if (!fits(in.height(), floor - in.below(), in.below() + in.above())) return null;
    var biome = in.biomes().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(floor), QuartPos.fromBlock(z),
        in.random().sampler());
    boolean suits = !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH);
    int score = RuinRules.score(new RuinRules.Sample(heights, water, suits));
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

  /** Whether a template of {@code height} layers whose lowest layer is at {@code bottom} fits the world. */
  static boolean fits(LevelHeightAccessor level, int bottom, int height) {
    return bottom > level.getMinBuildHeight() && bottom + height < level.getMaxBuildHeight();
  }

  // ---- Jobs ----------------------------------------------------------------------------------

  /**
   * A surface ruin scans its site (heights, ground palette), shapes the terrain (blend, carve, fill,
   * headroom), fells the trees it cut, places its cubes, sends its thin supports down and finishes;
   * cavern and sky ruins clear their columns instead. A survey job waits in HOLD until released.
   */
  enum Stage {
    PREPARING, SITING, CHECKING, LOADING, EVALUATING, HOLD, SCANNING, SHAPING, FELLING, CLEARING, PLACING, SUPPORTING,
    FINISHING
  }

  /** The site's own ground: its surface and filler blocks and whether snow covers it. */
  record Palette(BlockState surface, BlockState filler, boolean snow) {
    static final Palette DEFAULT = new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), false);
  }

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
    /** The lowest block the placement wrote under the template (fill, supports): the box goes down to it. */
    int lowest = Integer.MAX_VALUE;
    final long startedAt = System.nanoTime();
    long busyNanos, worstNanos;
    int busyTicks;
    boolean done, failed;
    RuinData.Ruin result;
    Candidate best;
    int bestScore = Integer.MAX_VALUE;
    /** Centred on {@code center}: sited on the real terrain there, never rejected, no occupancy check. */
    boolean fixed;
    /** A dev survey: fixed, held before shaping, and never registered. */
    boolean survey;
    int spread = -1;
    // The terrain, over the grid grown by RuinTerrain.MAX_MARGIN: natural ground and top per column.
    int[] natural, surfaceTop;
    int margin, scanIndex, shapeIndex, supportIndex;
    Palette palette = Palette.DEFAULT;
    final List<BlockState> surfaces = new ArrayList<>(), fillers = new ArrayList<>();
    int snowSamples, samples;
    /** Trunks and mushroom stems the shaping cut, whose trees are felled whole. */
    final ArrayDeque<BlockPos> felled = new ArrayDeque<>();
    final LongOpenHashSet felledSeen = new LongOpenHashSet();
    int felledCount;

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

  /**
   * A dev survey job ({@link RuinTerrainSurvey}): {@code definition} centred on {@code center}, sited on
   * the real terrain there without rejection, held before the terrain is shaped and never registered.
   */
  static Job survey(ServerLevel level, RuinDefinitions.Definition definition, BlockPos center) {
    Job job = new Job(definition, level, center);
    job.fixed = true;
    job.survey = true;
    synchronized (JOBS) {
      queue(level.getServer()).add(job);
    }
    return job;
  }

  /**
   * Starts {@code definition} centred on {@code center}, its height from the real terrain there and the
   * terrain shaped around it, skipping the ring search (tests of how ruins meet the terrain).
   */
  public static Optional<Job> startCentred(ServerLevel level, RuinDefinitions.Definition definition, BlockPos center) {
    var job = start(level, definition, center);
    job.ifPresent(j -> {
      if (j.stage == Stage.PREPARING && j.resume == null) j.fixed = true;
    });
    return job;
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
      // A survey world has nobody to keep the ticks short for.
      step(job, start + (job.survey ? SURVEY_BUDGET_NANOS : TICK_BUDGET_NANOS));
    } catch (RuntimeException e) {
      LOGGER.error("Heliodor ruin {} could not be placed", job.id, e);
      job.failed = true;
    }
    long spent = System.nanoTime() - start;
    job.busyNanos += spent;
    job.worstNanos = Math.max(job.worstNanos, spent);
    job.busyTicks++;
    if (job.failed && !job.survey)
      synchronized (FAILED) {
        FAILED.computeIfAbsent(server, ignored -> new HashMap<>()).put(job.id, server.overworld().getGameTime());
      }
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
      if (data.find(id).isPresent() || busy(server, id) || waiting(server, id)) continue;
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
      if (data.find(id).isPresent() || busy(player.server, id) || waiting(player.server, id)) continue;
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
        if (job.fixed) {
          job.candidates = List.of(new Candidate(job.center.getX(), job.center.getZ(), Integer.MIN_VALUE, 0));
          job.stage = Stage.CHECKING;
          return;
        }
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
        if (job.resume != null) {
          reserve(job);
          job.stage = shapingStage(job);
        } else {
          job.stage = Stage.EVALUATING;
        }
      }
      case EVALUATING -> evaluate(job);
      case HOLD -> {}
      case SCANNING -> {
        if (job.natural == null && !fits(job.level, job.origin.getY(), job.prepared.sizeY())) {
          LOGGER.error("Heliodor ruin {} does not fit the world at {}; not placed", job.id, job.origin);
          job.failed = true;
          return;
        }
        scanning(job, deadline);
      }
      case SHAPING -> shaping(job, deadline);
      case FELLING -> felling(job, deadline);
      case CLEARING -> {
        if (job.columnIndex == 0 && !fits(job.level, job.origin.getY(), job.prepared.sizeY())) {
          LOGGER.error("Heliodor ruin {} does not fit the world at {}; not placed", job.id, job.origin);
          job.failed = true;
          return;
        }
        clearing(job, deadline);
      }
      case PLACING -> placing(job, deadline);
      case SUPPORTING -> supporting(job, deadline);
      case FINISHING -> finish(job);
    }
  }

  /** Where a sited job goes next: surface ruins shape the terrain (survey jobs wait first), others clear. */
  private static Stage shapingStage(Job job) {
    if (job.definition.placement().mode() != RuinDefinitions.Mode.SURFACE) return Stage.CLEARING;
    return job.survey ? Stage.HOLD : Stage.SCANNING;
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
      if (job.best == null || job.bestScore == Integer.MAX_VALUE) {
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
    // The blended margin reshapes the ground too: a base right next to the footprint counts.
    for (ChunkPos chunk : job.fixed ? List.<ChunkPos>of() : chunks(x0, z0, p.sizeX(), p.sizeZ(), RuinTerrain.MAX_MARGIN)) {
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
    // The footprint, its blended margin and two blocks more, so that every read and write is loaded.
    for (ChunkPos chunk : chunks(job.origin.getX(), job.origin.getZ(), p.sizeX(), p.sizeZ(), RuinTerrain.MAX_MARGIN + 2)) {
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
    boolean good;
    if (mode == RuinDefinitions.Mode.SURFACE) {
      int n = 7;
      int[] heights = new int[n * n];
      boolean[] water = new boolean[n * n];
      int canopy = 0, wet = 0;
      var cursor = new BlockPos.MutableBlockPos();
      for (int i = 0; i < n; i++)
        for (int j = 0; j < n; j++) {
          int x = x0 + p.sizeX() / 8 + i * (p.sizeX() * 3 / 4) / (n - 1);
          int z = z0 + p.sizeZ() / 8 + j * (p.sizeZ() * 3 / 4) / (n - 1);
          int free = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
          heights[i * n + j] = groundAt(level, cursor, x, z, free - 1);
          water[i * n + j] = !level.getBlockState(new BlockPos(x, free - 1, z)).getFluidState().isEmpty();
          if (water[i * n + j]) wet++;
          if (level.getBlockState(new BlockPos(x, free, z)).is(BlockTags.LOGS)) canopy++;
        }
      // The ruin's ground level: the median of the natural ground under the columns it stands on, which
      // balances what the blend fills and what it carves.
      int[] base = base(level, cursor, p, x0, z0);
      floor = RuinTerrain.median(base.length > 0 ? base : heights);
      int[] under = base.length > 0 ? base : heights;
      int spread = Arrays.stream(under).max().getAsInt() - Arrays.stream(under).min().getAsInt();
      job.spread = spread;
      int structures = structures(level, x0, z0, p, floor);
      score = RuinRules.score(new RuinRules.Sample(under, new boolean[under.length], true));
      if (score != Integer.MAX_VALUE) score += wet * 40 + canopy * 2 + structures * 100;
      if (wet * 4 > heights.length) score = Integer.MAX_VALUE;
      // Suitable: gentle ground, almost no water, no big trees, no village or outpost in the way. When no
      // candidate is (Elias, 26 September), the one with the smallest spread wins and the blend does the rest.
      good = spread <= MAX_SPREAD && wet * 8 <= heights.length && canopy * 4 <= heights.length && structures == 0;
    } else if (mode == RuinDefinitions.Mode.CAVERN) {
      good = true;
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
      good = true;
      floor = job.origin.getY() + p.ground();
      int crowded = 0;
      for (int x = x0; x < x0 + p.sizeX(); x += 4)
        for (int z = z0; z < z0 + p.sizeZ(); z += 4)
          for (int y = floor - p.ground(); y < floor + p.above(); y += 2)
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) crowded++;
      score = crowded;
    }
    if (!fits(level, floor - p.ground(), p.sizeY())) score = Integer.MAX_VALUE;
    var candidate = new Candidate(x0 + p.sizeX() / 2, z0 + p.sizeZ() / 2, floor, score);
    if (score < job.bestScore) {
      job.bestScore = score;
      job.best = candidate;
    }
    good = job.fixed || score != Integer.MAX_VALUE && switch (mode) {
      case SURFACE -> good;
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
    if (!job.survey) reserve(job);
    job.stage = shapingStage(job);
  }

  /** The natural ground under the columns a surface ruin stands on (up to about 400 of them). */
  private static int[] base(ServerLevel level, BlockPos.MutableBlockPos cursor, Prepared p, int x0, int z0) {
    var contact = p.shape().contact();
    int count = 0;
    for (boolean c : contact) if (c) count++;
    if (count == 0) return new int[0];
    int step = Math.max(1, count / 400);
    int[] out = new int[(count + step - 1) / step];
    int k = 0, seen = 0;
    for (int i = 0; i < contact.length && k < out.length; i++) {
      if (!contact[i] || seen++ % step != 0) continue;
      int x = x0 + i % p.sizeX(), z = z0 + i / p.sizeX();
      out[k++] = groundAt(level, cursor, x, z, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
    }
    return Arrays.copyOf(out, k);
  }

  /**
   * The natural ground of a column: the highest block at or below {@code top} that a ruin can stand on,
   * past plants, snow, trees, mushroom caps and water.
   */
  static int groundAt(ServerLevel level, BlockPos.MutableBlockPos cursor, int x, int z, int top) {
    int bottom = level.getMinBuildHeight();
    for (int y = top; y > bottom && y > top - 96; y--)
      if (ground(level.getBlockState(cursor.set(x, y, z)))) return y;
    return top;
  }

  /** Terrain a ruin stands on: solid, not a plant, snow layer, tree, mushroom cap or fluid. */
  static boolean ground(BlockState state) {
    return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && state.isSolid()
        && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.is(Blocks.SNOW)
        && !(state.getBlock() instanceof HugeMushroomBlock)
        && !state.is(Blocks.BAMBOO) && !state.is(Blocks.CACTUS) && !state.is(Blocks.BEE_NEST);
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
    job.stage = job.definition.placement().mode() == RuinDefinitions.Mode.SURFACE ? Stage.SUPPORTING : Stage.FINISHING;
  }

  // ---- Terrain (surface ruins; RuinTerrain) ----------------------------------------------------

  /** The width and depth of the template grown by the widest margin on every side. */
  static int grownWidth(Prepared p) {
    return p.sizeX() + 2 * RuinTerrain.MAX_MARGIN;
  }

  static int grownDepth(Prepared p) {
    return p.sizeZ() + 2 * RuinTerrain.MAX_MARGIN;
  }

  /**
   * Reads the site: the natural ground and the top of every column of the footprint and its widest
   * margin, the site's ground palette from the columns around the template, and the margin that the
   * height difference at the platforms' rim needs.
   */
  private static void scanning(Job job, long deadline) {
    var p = job.prepared;
    var level = job.level;
    int m = RuinTerrain.MAX_MARGIN, w = grownWidth(p), n = w * grownDepth(p);
    if (job.natural == null) {
      job.natural = new int[n];
      job.surfaceTop = new int[n];
      job.scanIndex = 0;
    }
    var cursor = new BlockPos.MutableBlockPos();
    while (job.scanIndex < n) {
      int i = job.scanIndex++;
      int x = i % w - m, z = i / w - m;
      int wx = job.origin.getX() + x, wz = job.origin.getZ() + z;
      int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
      int ground = groundAt(level, cursor, wx, wz, top);
      job.natural[i] = ground;
      job.surfaceTop[i] = top;
      boolean outside = x < 0 || z < 0 || x >= p.sizeX() || z >= p.sizeZ();
      if (outside && Math.floorMod(x * 7 + z * 3, 5) == 0) sample(job, cursor, wx, ground, wz);
      if ((i & 63) == 0 && System.nanoTime() > deadline) return;
    }
    int floor = job.origin.getY() + p.ground(), rim = 0;
    float[] distance = p.shape().distance();
    for (int i = 0; i < n; i++)
      if (distance[i] > 0 && distance[i] <= 2) rim = Math.max(rim, Math.abs(job.natural[i] - floor));
    job.margin = p.shape().platformless() ? 0 : RuinTerrain.margin(rim);
    job.palette = new Palette(RuinTerrain.mostCommon(job.surfaces, Palette.DEFAULT.surface()),
        RuinTerrain.mostCommon(job.fillers, Palette.DEFAULT.filler()),
        job.samples > 0 && job.snowSamples * 3 >= job.samples);
    job.surfaces.clear();
    job.fillers.clear();
    job.shapeIndex = 0;
    job.stage = Stage.SHAPING;
    LOGGER.info("Heliodor ruin {}: ground level {} at {}, rim {} so margin {}, site ground {} over {}{}", job.id,
        floor, job.origin.toShortString(), rim, job.margin, BuiltInRegistries.BLOCK.getKey(job.palette.surface().getBlock()),
        BuiltInRegistries.BLOCK.getKey(job.palette.filler().getBlock()), job.palette.snow() ? " under snow" : "");
  }

  /** One column of the site's ground palette: its surface, the block under it and whether snow covers it. */
  private static void sample(Job job, BlockPos.MutableBlockPos cursor, int x, int ground, int z) {
    var level = job.level;
    BlockState above = level.getBlockState(cursor.set(x, ground + 1, z));
    if (!above.getFluidState().isEmpty()) return;          // a pond's bed is not the site's ground
    BlockState surface = level.getBlockState(cursor.set(x, ground, z));
    if (!ground(surface)) return;
    BlockState below = level.getBlockState(cursor.set(x, ground - 1, z));
    job.samples++;
    if (above.is(Blocks.SNOW) || surface.hasProperty(BlockStateProperties.SNOWY) && surface.getValue(BlockStateProperties.SNOWY))
      job.snowSamples++;
    job.surfaces.add(surface.getBlock().defaultBlockState());
    job.fillers.add(ground(below) ? below.getBlock().defaultBlockState() : surface.getBlock().defaultBlockState());
  }

  /**
   * Shapes the site of a surface ruin, column by column over the footprint and its margin. Platform
   * columns are carved down to the ruin's ground level and filled up to it; around them the natural
   * surface blends back over the margin; the ruin's other columns (decks, piers) keep headroom over
   * their blocks and nothing else is cleared. Template cells are left to the placement.
   */
  private static void shaping(Job job, long deadline) {
    var p = job.prepared;
    var shape = p.shape();
    int m = RuinTerrain.MAX_MARGIN, w = grownWidth(p), n = w * grownDepth(p);
    int floor = job.origin.getY() + p.ground();
    var cursor = new BlockPos.MutableBlockPos();
    while (job.shapeIndex < n) {
      int i = job.shapeIndex++;
      int x = i % w - m, z = i / w - m;
      boolean inside = x >= 0 && z >= 0 && x < p.sizeX() && z < p.sizeZ();
      int column = inside ? z * p.sizeX() + x : -1;
      int natural = job.natural[i], top = job.surfaceTop[i];
      if (inside && shape.platform()[column]) {
        carve(job, cursor, x, z, floor + 1, top);
        fill(job, cursor, i, x, z, natural + 1, Math.min(floor, job.origin.getY() + shape.low()[column]) - 1, false);
      } else {
        int target = surfaceOf(job, i);
        if (target < natural) {
          carve(job, cursor, x, z, target + 1, top);
          resurface(job, cursor, x, target, z);
        } else if (target > natural) {
          fill(job, cursor, i, x, z, natural + 1, target, true);
          // What grew on the old surface and now sticks out of the fill goes (not trees: their trunks stay buried).
          for (int y = target + 1; y <= Math.min(top, target + 3); y++) {
            BlockState state = job.level.getBlockState(cursor.set(job.origin.getX() + x, y, job.origin.getZ() + z));
            if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()
                && !p.cell(x, y - job.origin.getY(), z))
              job.level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
          }
        }
        if (inside && shape.top()[column] >= 0) {
          // Headroom over the ruin's own blocks where they do not stand on a platform (decks, piers).
          int from = job.origin.getY() + Math.max(shape.low()[column], p.ground() + 1);
          carve(job, cursor, x, z, from, job.origin.getY() + shape.top()[column] + RuinTerrain.HEADROOM);
        }
      }
      if ((i & 15) == 0 && System.nanoTime() > deadline) return;
    }
    job.stage = Stage.FELLING;
  }

  /** The shaped surface of a column of the grown grid: the ruin's ground on its platforms, blended around them. */
  static int surfaceOf(Job job, int i) {
    return RuinTerrain.target(job.natural[i], job.origin.getY() + job.prepared.ground(),
        job.prepared.shape().distance()[i], job.margin);
  }

  /** The lowest shaped surface among a column's four neighbours inside the grown grid. */
  private static int lowestNeighbour(Job job, int i, int fallback) {
    var p = job.prepared;
    int w = grownWidth(p), d = grownDepth(p), gx = i % w, gz = i / w, lowest = fallback;
    if (gx > 0) lowest = Math.min(lowest, surfaceOf(job, i - 1));
    if (gx < w - 1) lowest = Math.min(lowest, surfaceOf(job, i + 1));
    if (gz > 0) lowest = Math.min(lowest, surfaceOf(job, i - w));
    if (gz < d - 1) lowest = Math.min(lowest, surfaceOf(job, i + w));
    return lowest;
  }

  /** Empties the non-template cells of a column from {@code from} to {@code to}, noting the trees it cuts. */
  private static void carve(Job job, BlockPos.MutableBlockPos cursor, int x, int z, int from, int to) {
    var level = job.level;
    int wx = job.origin.getX() + x, wz = job.origin.getZ() + z;
    for (int y = Math.max(from, level.getMinBuildHeight()); y <= Math.min(to, level.getMaxBuildHeight() - 1); y++) {
      if (job.prepared.cell(x, y - job.origin.getY(), z)) continue;
      BlockState state = level.getBlockState(cursor.set(wx, y, wz));
      if (state.isAir()) continue;
      if (tree(state) && job.felledSeen.add(cursor.asLong())) job.felled.add(cursor.immutable());
      level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
  }

  /**
   * Fills the non-template cells of a column from {@code from} to {@code to}: the site's filler, its
   * surface on top of a margin column, and the ruin's masonry where the fill faces a neighbour two or
   * more blocks lower (a terrace face).
   */
  private static void fill(Job job, BlockPos.MutableBlockPos cursor, int i, int x, int z, int from, int to,
      boolean margin) {
    if (from > to) return;
    var level = job.level;
    var p = job.prepared;
    int wx = job.origin.getX() + x, wz = job.origin.getZ() + z;
    boolean inside = x >= 0 && z >= 0 && x < p.sizeX() && z < p.sizeZ();
    int lowest = lowestNeighbour(job, i, to);
    // The old surface goes under: grass and its kin become the filler.
    BlockState buried = level.getBlockState(cursor.set(wx, from - 1, wz));
    if ((buried.is(Blocks.GRASS_BLOCK) || buried.is(Blocks.MYCELIUM) || buried.is(Blocks.PODZOL))
        && !p.cell(x, from - 1 - job.origin.getY(), z))
      level.setBlock(cursor, job.palette.filler(), Block.UPDATE_CLIENTS);
    for (int y = Math.max(from, level.getMinBuildHeight()); y <= to; y++) {
      if (p.cell(x, y - job.origin.getY(), z)) continue;
      BlockState state = RuinTerrain.riser(y, to, lowest) ? p.shape().masonry()
          : margin && y == to ? job.palette.surface() : job.palette.filler();
      level.setBlock(cursor.set(wx, y, wz), state, Block.UPDATE_CLIENTS);
      if (inside) job.lowest = Math.min(job.lowest, y);
    }
    if (margin && !RuinTerrain.riser(to, to, lowest)) cover(job, cursor.set(wx, to, wz).immutable());
  }

  /** A carved column's new top: the site's surface block where the carving bared the ground. */
  private static void resurface(Job job, BlockPos.MutableBlockPos cursor, int x, int y, int z) {
    if (job.prepared.cell(x, y - job.origin.getY(), z)) return;
    BlockState state = job.level.getBlockState(cursor.set(job.origin.getX() + x, y, job.origin.getZ() + z));
    if (!ground(state)) return;
    job.level.setBlock(cursor, job.palette.surface(), Block.UPDATE_CLIENTS);
    cover(job, cursor.immutable());
  }

  /** Snow on a new surface where the site is snowy and the sky is open; a snowy surface block shows it. */
  private static void cover(Job job, BlockPos pos) {
    var level = job.level;
    BlockState surface = level.getBlockState(pos);
    BlockPos up = pos.above();
    // The heightmap is current; the sky light is not until the light engine catches up.
    boolean snow = job.palette.snow() && level.getBlockState(up).isAir()
        && level.getHeight(Heightmap.Types.MOTION_BLOCKING, up.getX(), up.getZ()) <= up.getY()
        && Blocks.SNOW.defaultBlockState().canSurvive(level, up);
    if (snow) level.setBlock(up, Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
    if (surface.hasProperty(BlockStateProperties.SNOWY))
      level.setBlock(pos, surface.setValue(BlockStateProperties.SNOWY, snow || level.getBlockState(up).is(Blocks.SNOW)),
          Block.UPDATE_CLIENTS);
  }

  private static boolean tree(BlockState state) {
    return state.is(BlockTags.LOGS) || state.getBlock() instanceof HugeMushroomBlock;
  }

  /** Blocks that hang on a tree and go with it. */
  private static boolean hanging(BlockState state) {
    return state.is(Blocks.VINE) || state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST) || state.is(Blocks.SNOW);
  }

  /** A felled tree's leaves: a position and its distance from the felled wood through leaves. */
  private record Leaf(BlockPos pos, int depth) {}

  /**
   * Fells the trees whose wood the shaping cut, so that no canopy floats: every log (or mushroom
   * block) joined to the cut one, then the leaves nearer to it than to any other tree, and what hangs
   * on them. A cluster of logs that touches no natural leaves is a building's frame and stays.
   */
  private static void felling(Job job, long deadline) {
    var p = job.prepared;
    var level = job.level;
    int reach = RuinTerrain.MAX_MARGIN + 8;
    int x0 = job.origin.getX() - reach, z0 = job.origin.getZ() - reach;
    int x1 = job.origin.getX() + p.sizeX() + reach, z1 = job.origin.getZ() + p.sizeZ() + reach;
    while (!job.felled.isEmpty() && job.felledCount < MAX_FELLED) {
      BlockPos seed = job.felled.poll();
      // The cluster of wood joined to the cut block (diagonals too, like branches).
      List<BlockPos> wood = new ArrayList<>();
      ArrayDeque<BlockPos> open = new ArrayDeque<>(List.of(seed));
      boolean leafy = false, mushroom = false;
      while (!open.isEmpty() && wood.size() < 512) {
        BlockPos at = open.poll();
        for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
          if (next.getX() < x0 || next.getX() > x1 || next.getZ() < z0 || next.getZ() > z1) continue;
          if (!loaded(level, next)) continue;
          BlockState state = level.getBlockState(next);
          if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT)
              && !state.getValue(LeavesBlock.PERSISTENT)) leafy = true;
          if (!tree(state) || !job.felledSeen.add(next.asLong())) continue;
          if (p.cell(next.getX() - job.origin.getX(), next.getY() - job.origin.getY(), next.getZ() - job.origin.getZ()))
            continue;
          mushroom |= state.getBlock() instanceof HugeMushroomBlock;
          BlockPos kept = next.immutable();
          wood.add(kept);
          open.add(kept);
        }
      }
      if (!leafy && !mushroom) continue;
      for (BlockPos log : wood) {
        level.setBlock(log, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        job.felledCount++;
      }
      ArrayDeque<Leaf> leaves = new ArrayDeque<>();
      leaves.add(new Leaf(seed, 0));
      for (BlockPos log : wood) leaves.add(new Leaf(log, 0));
      LongOpenHashSet seen = new LongOpenHashSet();
      while (!leaves.isEmpty()) {
        Leaf leaf = leaves.poll();
        for (var direction : net.minecraft.core.Direction.values()) {
          BlockPos next = leaf.pos().relative(direction);
          if (!seen.add(next.asLong()) || !loaded(level, next)) continue;
          BlockState state = level.getBlockState(next);
          if (hanging(state)) {
            level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            continue;
          }
          int depth = leaf.depth() + 1;
          // A leaf nearer to another tree than to this one belongs to that tree.
          if (depth > 6 || !state.is(BlockTags.LEAVES) || !state.hasProperty(LeavesBlock.DISTANCE)
              || state.getValue(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.DISTANCE) < depth) continue;
          level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
          job.felledCount++;
          leaves.add(new Leaf(next.immutable(), depth));
        }
      }
      if (System.nanoTime() > deadline) return;
    }
    job.felled.clear();
    job.stage = Stage.PLACING;
  }

  static boolean loaded(ServerLevel level, BlockPos pos) {
    return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
  }

  /** Thin supports (piers, pilasters, walls) that hang above the ground go down to it in their own block. */
  private static void supporting(Job job, long deadline) {
    var p = job.prepared;
    var shape = p.shape();
    var level = job.level;
    var cursor = new BlockPos.MutableBlockPos();
    int n = p.sizeX() * p.sizeZ();
    while (job.supportIndex < n) {
      int column = job.supportIndex++;
      if (!shape.thin(column)) continue;
      int x = column % p.sizeX(), z = column / p.sizeX();
      int wx = job.origin.getX() + x, wz = job.origin.getZ() + z;
      int y = job.origin.getY() + shape.low()[column] - 1;
      for (int depth = 0; depth < RuinTerrain.MAX_PIER && y > level.getMinBuildHeight(); depth++, y--) {
        if (p.cell(x, y - job.origin.getY(), z)) break;
        if (ground(level.getBlockState(cursor.set(wx, y, wz)))) break;
        level.setBlock(cursor, shape.pier()[column], Block.UPDATE_CLIENTS);
        job.lowest = Math.min(job.lowest, y);
      }
      if ((column & 31) == 0 && System.nanoTime() > deadline) return;
    }
    job.stage = Stage.FINISHING;
  }

  /** The template's soil becomes the site's ground: its surface where open to the air, its filler under cover. */
  private static void resoil(Job job) {
    var p = job.prepared;
    for (long cell : p.shape().soil())
      resoil(job, job.origin.offset(BlockPos.getX(cell), BlockPos.getY(cell), BlockPos.getZ(cell)));
    for (Local local : p.markers()) {
      String block = local.marker().block().replaceFirst("\\[.*", "");
      if (RuinTerrain.soil(block.contains(":") ? block : "minecraft:" + block)
          && !RuinTerrain.kept(local.x(), local.y(), local.z(), p.shape().keep()))
        resoil(job, job.origin.offset(local.x(), local.y(), local.z()));
    }
  }

  private static void resoil(Job job, BlockPos pos) {
    var level = job.level;
    BlockState current = level.getBlockState(pos);
    if (!RuinTerrain.soil(BuiltInRegistries.BLOCK.getKey(current.getBlock()).toString())) return;
    BlockPos up = pos.above();
    BlockState above = level.getBlockState(up);
    boolean open = !above.isSolidRender(level, up);
    BlockState state = open ? job.palette.surface() : job.palette.filler();
    // Sand or gravel over a gap would fall at the first update: its stone stands instead.
    if (state.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(pos.below())))
      state = steady(state, job.palette);
    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    if (!open) return;
    // A plant that cannot grow on the new ground goes (with the top of a tall one).
    if (!above.isAir() && above.getFluidState().isEmpty() && !above.canSurvive(level, up)) {
      level.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      if (level.getBlockState(up.above()).is(above.getBlock()))
        level.setBlock(up.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    cover(job, pos);
  }

  /** A block that does not fall, for a falling one that would. */
  static BlockState steady(BlockState state, Palette palette) {
    if (state.is(Blocks.SAND)) return Blocks.SANDSTONE.defaultBlockState();
    if (state.is(Blocks.RED_SAND)) return Blocks.RED_SANDSTONE.defaultBlockState();
    if (!(palette.filler().getBlock() instanceof FallingBlock)) return palette.filler();
    return Blocks.STONE.defaultBlockState();
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
    if (job.definition.placement().mode() == RuinDefinitions.Mode.SURFACE) resoil(job);
    var box = new BoundingBox(job.origin.getX(), Math.min(job.lowest, job.origin.getY()), job.origin.getZ(),
        job.origin.getX() + p.sizeX() - 1, job.origin.getY() + p.sizeY() - 1, job.origin.getZ() + p.sizeZ() - 1);
    if (arrival == null) arrival = HeliodorRuins.besideRuin(level, box, job.origin.getY() + p.ground());
    var ruin = new RuinData.Ruin(job.id, ResourceLocation.parse(job.definition.template()), level.dimension(),
        job.definition.act(), box, job.origin, arrival, pedestals, level.getGameTime(), placed);
    if (!job.survey) {
      var data = RuinData.get(level.getServer());
      data.add(ruin);
      StructureProtection.invalidate(level.getServer());
      RuinWorkshop.placed(level, ruin);
    }
    job.result = ruin;
    job.done = true;
    LOGGER.info("Placed Heliodor ruin {} at {} in {}: {} template blocks, {} markers, {} ms busy over {} ticks"
            + " (worst tick {} ms), {} ms wall", job.id, box, level.dimension().location(), p.blocks(), placed.size(),
        String.format(Locale.ROOT, "%.1f", job.busyNanos / 1e6), job.busyTicks,
        String.format(Locale.ROOT, "%.1f", job.worstNanos / 1e6),
        String.format(Locale.ROOT, "%.0f", (System.nanoTime() - job.startedAt) / 1e6));
  }

  /** Parses a marker's {@code block=} state; null when absent, unknown or of a mod that is not loaded. */
  @Nullable
  static BlockState blockOf(RuinMarkers.Marker marker) {
    String block = marker.block();
    if (block.isEmpty()) return null;
    int colon = block.indexOf(':');
    String namespace = colon < 0 ? "minecraft" : block.substring(0, colon);
    if (!namespace.equals("minecraft") && !RuinRegistry.modLoaded(namespace)) return null;
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
      // A missing piece starts missing; the sandbox is air to build in.
      case PART, SANDBOX -> Blocks.AIR.defaultBlockState();
      case MODBLOCK -> RuinRegistry.modLoaded(marker.param("mod", "")) && blockOf(marker) != null
          ? blockOf(marker) : Blocks.AIR.defaultBlockState();
      default -> Optional.ofNullable(blockOf(marker)).orElse(Blocks.AIR.defaultBlockState());
    };
    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    if (marker.kind() == RuinMarkers.Kind.CHEST) {
      String loot = marker.param("loot", definition == null ? "" : definition.loot());
      // A table of a mod that is not loaded is absent: the ruin's own loot fills the chest instead.
      if (!loot.isEmpty() && definition != null && !definition.loot().isEmpty() && !lootExists(level, loot))
        loot = definition.loot();
      if (!loot.isEmpty())
        RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos,
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(loot)));
    }
    if (marker.params().containsKey("scroll")) {
      var entity = level.getBlockEntity(pos);
      var tag = RuinWorkshop.scroll(marker);
      if (entity != null && tag != null) {
        CompoundTag data = entity.saveWithoutMetadata(level.registryAccess());
        data.merge(tag);
        entity.loadWithComponents(data, level.registryAccess());
        entity.setChanged();
      }
    }
    if (marker.kind() == RuinMarkers.Kind.VITRAL)
      RuinRelay.setTurn(level, pos, LightRelay.Turn.of(marker.param("turn", "pass")).orElse(LightRelay.Turn.PASS));
    if (marker.kind() == RuinMarkers.Kind.NOTE && state.getBlock() instanceof net.minecraft.world.level.block.LecternBlock
        && !state.getValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK))
      net.minecraft.world.level.block.LecternBlock.tryPlaceBook(null, level, pos, state,
          RuinWorkshop.note(marker.param("key", "engine"), 0));
  }

  static boolean lootExists(ServerLevel level, String id) {
    var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
    return level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY;
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
