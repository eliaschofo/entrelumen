package dev.entrelumen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;

/**
 * Dev evidence of how the ruins meet the terrain (docs/design/heliodor-ruins.md, "El terreno"): every
 * overworld surface ruin placed on five kinds of site of this world's seed (plains, a hill or mountain
 * slope, desert, snowy taiga, dense forest), with each region and its blended margin dumped before and
 * after, one block per line: {@code x y z state}, relative to the ruin's centre and ground level, in the
 * art's convention (art/structures/voxrender.py draws them). A dump keeps every block of the ruin's box
 * and, around it, every block with a face open to the air.
 *
 * <p>Run by {@code /entrelumen admin ruins terrain} or, unattended, with the system property
 * {@code entrelumen.ruinTerrain=<folder>} ({@code entrelumen.ruinTerrain.stop=true} stops the server when
 * done; {@code entrelumen.ruinTerrain.spots=<an earlier index.json>} takes that survey's sites, found for
 * the same seed, instead of searching the noise again; {@code .ruins=<ids>} and {@code .kinds=<names>},
 * comma-separated, keep only those ruins and kinds of site). Placements are never registered as ruins;
 * the world is meant to be thrown away.
 */
public final class RuinTerrainSurvey {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final TicketType<ChunkPos> TICKET =
      TicketType.create("entrelumen_ruin_survey", Comparator.comparingLong(ChunkPos::toLong));
  /** Around the template: the widest margin and a little context. */
  static final int CONTEXT = RuinTerrain.MAX_MARGIN + 2;

  private RuinTerrainSurvey() {}

  /** A kind of site, by the vanilla biomes that make it. */
  record Kind(String name, Set<ResourceKey<Biome>> biomes, boolean slope) {}

  static final List<Kind> KINDS = List.of(
      new Kind("plains", Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS), false),
      new Kind("slope", Set.of(Biomes.MEADOW, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST,
          Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.CHERRY_GROVE), true),
      new Kind("desert", Set.of(Biomes.DESERT), false),
      new Kind("snowy_taiga", Set.of(Biomes.SNOWY_TAIGA), false),
      new Kind("dense_forest", Set.of(Biomes.DARK_FOREST), false));

  /** Where one ruin goes for one kind of site. */
  record Spot(Kind kind, RuinDefinitions.Definition ruin, int x, int z, int spread, String biome) {}

  /** A running survey. */
  static final class Run {
    final MinecraftServer server;
    final ServerLevel level;
    final Path out;
    final boolean stop;
    CompletableFuture<List<Spot>> finding;
    List<Spot> spots;
    /** The earlier survey whose sites this one took, if it did. */
    volatile Path spotsFrom;
    int index = -1;
    Spot spot;
    RuinPlacement.Job job;
    final List<ChunkPos> tickets = new ArrayList<>();
    int x0, y0, z0, x1, y1, z1;
    int waited;
    final List<CompletableFuture<?>> writes = new ArrayList<>();
    final List<Map<String, Object>> placements = new ArrayList<>();
    final long started = System.nanoTime();

    Run(MinecraftServer server, Path out, boolean stop) {
      this.server = server;
      this.level = server.overworld();
      this.out = out;
      this.stop = stop;
    }
  }

  private static final Map<MinecraftServer, Run> RUNS = new WeakHashMap<>();

  /** The folder of a survey: the system property, or ruins-terrain in the server's folder. */
  static Path folder(MinecraftServer server) {
    String property = System.getProperty("entrelumen.ruinTerrain", "");
    return property.isBlank() || property.equals("true") ? server.getServerDirectory().resolve("ruins-terrain")
        : Path.of(property);
  }

  /** Starts an unattended survey when the server was started with {@code entrelumen.ruinTerrain}. */
  public static void onStarted(MinecraftServer server) {
    if (System.getProperty("entrelumen.ruinTerrain") == null) return;
    start(server, folder(server), Boolean.getBoolean("entrelumen.ruinTerrain.stop"));
  }

  /** Starts a survey unless one runs; false when one does. */
  public static synchronized boolean start(MinecraftServer server, Path out, boolean stop) {
    if (RUNS.containsKey(server)) return false;
    Run run = new Run(server, out, stop);
    Set<String> onlyRuins = listed("entrelumen.ruinTerrain.ruins"), onlyKinds = listed("entrelumen.ruinTerrain.kinds");
    List<RuinDefinitions.Definition> ruins = RuinRegistry.available().stream()
        .filter(d -> d.dimension().equals("minecraft:overworld") && d.placement().mode() == RuinDefinitions.Mode.SURFACE)
        .filter(d -> onlyRuins.isEmpty() || onlyRuins.contains(d.id())
            || onlyRuins.contains(ResourceLocation.parse(d.id()).getPath()))
        .sorted(Comparator.comparing(RuinDefinitions.Definition::id)).toList();
    List<Kind> kinds = KINDS.stream().filter(k -> onlyKinds.isEmpty() || onlyKinds.contains(k.name())).toList();
    var source = run.level.getChunkSource();
    var generator = source.getGenerator();
    var random = source.randomState();
    var level = run.level;
    BlockPos spawn = level.getSharedSpawnPos();
    Path earlier = earlierSpots();
    run.finding = CompletableFuture.supplyAsync(() -> {
      if (earlier != null) {
        var spots = reused(earlier, level.getSeed(), ruins, kinds);
        if (spots != null) {
          run.spotsFrom = earlier;
          return spots;
        }
      }
      Map<String, int[]> sizes = new HashMap<>();
      for (var ruin : ruins) {
        try {
          var prepared = RuinPlacement.prepare(server, ResourceLocation.parse(ruin.template()));
          sizes.put(ruin.id(), new int[] {prepared.sizeX(), prepared.sizeZ()});
        } catch (IOException e) {
          throw new java.io.UncheckedIOException(e);
        }
      }
      return find(generator, random, level, generator.getBiomeSource(), spawn, ruins, sizes, kinds);
    }, Util.backgroundExecutor());
    RUNS.put(server, run);
    LOGGER.info("Ruin terrain survey: {} overworld ruins on {} kinds of site, into {}", ruins.size(), kinds.size(), out);
    return true;
  }

  /** A comma-separated system property as a set (empty when unset). */
  static Set<String> listed(String property) {
    String value = System.getProperty(property, "");
    return value.isBlank() ? Set.of() : Set.of(value.split(","));
  }

  // ---- Finding the sites (off the server thread: noise only) ----------------------------------

  /** The earlier survey's index named by {@code entrelumen.ruinTerrain.spots}, if any. */
  static @Nullable Path earlierSpots() {
    String property = System.getProperty("entrelumen.ruinTerrain.spots", "");
    return property.isBlank() ? null : Path.of(property);
  }

  /**
   * An earlier survey's sites for this seed, in the finder's order: each ruin keeps its centre there,
   * whatever its size now. Null, and a new search, when the index is from another seed or lacks one of
   * this survey's ruins on one of its kinds of site.
   */
  static @Nullable List<Spot> reused(Path index, long seed, List<RuinDefinitions.Definition> ruins, List<Kind> kinds) {
    try {
      JsonObject root = JsonParser.parseString(Files.readString(index, StandardCharsets.UTF_8)).getAsJsonObject();
      if (root.get("seed").getAsLong() != seed) {
        LOGGER.warn("Ruin terrain survey: {} is from seed {}, not {}; searching again", index, root.get("seed"), seed);
        return null;
      }
      Map<String, JsonObject> earlier = new HashMap<>();
      for (var element : root.getAsJsonArray("placements")) {
        JsonObject entry = element.getAsJsonObject();
        earlier.put(entry.get("site").getAsString() + " " + entry.get("ruin").getAsString(), entry);
      }
      List<Spot> spots = new ArrayList<>();
      for (Kind kind : kinds)
        for (var ruin : ruins) {
          JsonObject entry = earlier.get(kind.name() + " " + ruin.id());
          if (entry == null) {
            LOGGER.warn("Ruin terrain survey: {} has no {} site for {}; searching again", index, kind.name(), ruin.id());
            return null;
          }
          var centre = entry.getAsJsonArray("centre");
          spots.add(new Spot(kind, ruin, centre.get(0).getAsInt(), centre.get(1).getAsInt(),
              entry.get("noiseSpread").getAsInt(), entry.get("biome").getAsString()));
        }
      return spots;
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Ruin terrain survey: cannot take the sites of {}; searching again", index, e);
      return null;
    }
  }

  static String biomeAt(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, BiomeSource biomes,
      int x, int z) {
    int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
    return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler())
        .unwrapKey().map(key -> key.location().toString()).orElse("?");
  }

  static boolean in(Kind kind, String biome) {
    return kind.biomes().stream().anyMatch(key -> key.location().toString().equals(biome));
  }

  /** Points of a square spiral around a centre, ring by ring. */
  static List<int[]> spiral(int cx, int cz, int step, int radius) {
    List<int[]> points = new ArrayList<>();
    points.add(new int[] {cx, cz});
    for (int ring = 1; ring * step <= radius; ring++)
      for (int k = -ring; k < ring; k++) {
        points.add(new int[] {cx + k * step, cz - ring * step});
        points.add(new int[] {cx + ring * step, cz + k * step});
        points.add(new int[] {cx - k * step, cz + ring * step});
        points.add(new int[] {cx - ring * step, cz - k * step});
      }
    return points;
  }

  /** Every kind of site searched at once, each in its own biome far from the others. */
  static List<Spot> find(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, BiomeSource biomes,
      BlockPos spawn, List<RuinDefinitions.Definition> ruins, Map<String, int[]> sizes, List<Kind> kinds) {
    List<CompletableFuture<List<Spot>>> searches = new ArrayList<>();
    for (Kind kind : kinds)
      searches.add(CompletableFuture.supplyAsync(() -> find(generator, random, height, biomes, spawn, ruins, sizes, kind),
          Util.backgroundExecutor()));
    List<Spot> spots = new ArrayList<>();
    for (var future : searches) spots.addAll(future.join());
    return spots;
  }

  static List<Spot> find(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, BiomeSource biomes,
      BlockPos spawn, List<RuinDefinitions.Definition> ruins, Map<String, int[]> sizes, Kind kind) {
    List<Spot> spots = new ArrayList<>();
    List<ProtectionRules.Box> taken = new ArrayList<>();
    int[] anchor = null;
    for (int[] point : spiral(spawn.getX(), spawn.getZ(), 96, 12_000)) {
      if (!in(kind, biomeAt(generator, random, height, biomes, point[0], point[1]))) continue;
      int agree = 0;
      for (int[] d : new int[][] {{120, 0}, {-120, 0}, {0, 120}, {0, -120}})
        if (in(kind, biomeAt(generator, random, height, biomes, point[0] + d[0], point[1] + d[1]))) agree++;
      if (agree >= 3) {
        anchor = point;
        break;
      }
    }
    if (anchor == null) {
      LOGGER.warn("Ruin terrain survey: no {} within 12000 blocks of the spawn", kind.name());
      return spots;
    }
    LOGGER.info("Ruin terrain survey: {} around {}, {}", kind.name(), anchor[0], anchor[1]);
    for (var ruin : ruins) {
      int[] size = sizes.get(ruin.id());
      int hx = size[0] / 2 + CONTEXT, hz = size[1] / 2 + CONTEXT;
      Spot best = null;
      int bestScore = Integer.MAX_VALUE, valid = 0;
      for (int[] point : spiral(anchor[0], anchor[1], 24, 2_000)) {
        int x = point[0], z = point[1];
        var box = new ProtectionRules.Box(x - hx, 0, z - hz, x + hx, 0, z + hz);
        if (!RuinRules.clear(box, taken, 32)) continue;
        // The centre and at least seven of the nine points of the footprint and its margin in the kind's
        // biomes (mountains mix peaks into their slopes).
        String biome = biomeAt(generator, random, height, biomes, x, z);
        if (!in(kind, biome)) continue;
        int outside = 0;
        for (int i = -1; i <= 1 && outside <= 2; i++)
          for (int j = -1; j <= 1 && outside <= 2; j++)
            if ((i != 0 || j != 0) && !in(kind, biomeAt(generator, random, height, biomes, x + i * hx, z + j * hz)))
              outside++;
        if (outside > 2) continue;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
        for (int i = 0; i < 5; i++)
          for (int j = 0; j < 5; j++) {
            int sx = x - size[0] / 2 + i * (size[0] - 1) / 4, sz = z - size[1] / 2 + j * (size[1] - 1) / 4;
            int floor = generator.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, height, random);
            int top = generator.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, height, random);
            min = Math.min(min, floor);
            max = Math.max(max, floor);
            if (top > floor) wet++;
          }
        if (wet > 2) continue;
        int spread = max - min;
        // A slope shows the blend at work (about 22 blocks of difference); the others take their
        // gentlest spot nearby, as the placement would.
        if (kind.slope() && spread < 10) continue;
        int score = kind.slope() ? Math.abs(spread - 22) : spread;
        if (score < bestScore) {
          bestScore = score;
          best = new Spot(kind, ruin, x, z, spread, biome);
        }
        if (++valid >= 12) break;
      }
      if (best == null) {
        LOGGER.warn("Ruin terrain survey: no {} spot for {}", kind.name(), ruin.id());
        continue;
      }
      taken.add(new ProtectionRules.Box(best.x() - hx, 0, best.z() - hz, best.x() + hx, 0, best.z() + hz));
      spots.add(best);
    }
    return spots;
  }

  // ---- Running ------------------------------------------------------------------------------

  public static void tick(MinecraftServer server) {
    Run run;
    synchronized (RuinTerrainSurvey.class) {
      run = RUNS.get(server);
    }
    if (run == null) return;
    try {
      step(run);
    } catch (RuntimeException e) {
      LOGGER.error("Ruin terrain survey failed", e);
      finish(run);
    }
  }

  private static void step(Run run) {
    if (run.spots == null) {
      if (!run.finding.isDone()) return;
      run.spots = run.finding.join();
      LOGGER.info("Ruin terrain survey: {} spots{}", run.spots.size(),
          run.spotsFrom == null ? "" : ", the sites of " + run.spotsFrom);
      for (Spot spot : run.spots)
        LOGGER.info("Ruin terrain survey: {} on {} at {}, {} ({}, noise spread {})", spot.ruin().id(), spot.kind().name(),
            spot.x(), spot.z(), spot.biome(), spot.spread());
    }
    if (run.job == null) {
      release(run);
      if (++run.index >= run.spots.size()) {
        finish(run);
        return;
      }
      run.spot = run.spots.get(run.index);
      run.job = RuinPlacement.survey(run.level, run.spot.ruin(), new BlockPos(run.spot.x(), 0, run.spot.z()));
      run.waited = 0;
      return;
    }
    var job = run.job;
    if (job.failed()) {
      LOGGER.warn("Ruin terrain survey: {} on {} failed", run.spot.ruin().id(), run.spot.kind().name());
      run.job = null;
      return;
    }
    if (job.stage() == RuinPlacement.Stage.HOLD) {
      if (run.tickets.isEmpty()) hold(run);
      for (ChunkPos chunk : run.tickets)
        if (run.level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
          if (++run.waited > 6000) throw new IllegalStateException("survey chunks did not load");
          return;
        }
      region(run);
      dump(run, "before");
      job.stage = RuinPlacement.Stage.SCANNING;
      return;
    }
    if (job.done()) {
      dump(run, "after");
      record(run);
      run.job = null;
    }
  }

  /** Keeps the dump's chunks loaded until the after dump, whatever the job does with its own tickets. */
  private static void hold(Run run) {
    var p = run.job.prepared;
    var origin = run.job.origin;
    for (int cx = (origin.getX() - CONTEXT) >> 4; cx <= (origin.getX() + p.sizeX() + CONTEXT) >> 4; cx++)
      for (int cz = (origin.getZ() - CONTEXT) >> 4; cz <= (origin.getZ() + p.sizeZ() + CONTEXT) >> 4; cz++) {
        var chunk = new ChunkPos(cx, cz);
        run.level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
        run.tickets.add(chunk);
      }
  }

  private static void release(Run run) {
    for (ChunkPos chunk : run.tickets) run.level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
    run.tickets.clear();
  }

  /** The dumped region: the footprint and its context, from well under the ground to over the ruin and the trees. */
  private static void region(Run run) {
    var p = run.job.prepared;
    var origin = run.job.origin;
    var level = run.level;
    run.x0 = origin.getX() - CONTEXT;
    run.z0 = origin.getZ() - CONTEXT;
    run.x1 = origin.getX() + p.sizeX() - 1 + CONTEXT;
    run.z1 = origin.getZ() + p.sizeZ() - 1 + CONTEXT;
    int ground = origin.getY() + p.ground(), low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
    for (int x = run.x0; x <= run.x1; x += 2)
      for (int z = run.z0; z <= run.z1; z += 2) {
        low = Math.min(low, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1);
        high = Math.max(high, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
      }
    run.y0 = Math.max(level.getMinBuildHeight(), Math.min(low - 6, ground - RuinTerrain.MAX_PIER - 4));
    run.y1 = Math.min(level.getMaxBuildHeight() - 1, Math.max(high, origin.getY() + p.sizeY() - 1) + 2);
  }

  /** Reads the region on the server thread and writes it off it. */
  private static void dump(Run run, String when) {
    var level = run.level;
    int w = run.x1 - run.x0 + 1, h = run.y1 - run.y0 + 1, d = run.z1 - run.z0 + 1;
    BlockState[] states = new BlockState[w * h * d];
    var cursor = new BlockPos.MutableBlockPos();
    for (int x = 0; x < w; x++)
      for (int z = 0; z < d; z++) {
        var chunk = level.getChunkSource().getChunkNow((run.x0 + x) >> 4, (run.z0 + z) >> 4);
        for (int y = 0; y < h; y++)
          states[(y * d + z) * w + x] = chunk == null ? null
              : chunk.getBlockState(cursor.set(run.x0 + x, run.y0 + y, run.z0 + z));
      }
    var job = run.job;
    var p = job.prepared;
    int ground = job.origin.getY() + p.ground();
    int cx = job.origin.getX() + p.sizeX() / 2, cz = job.origin.getZ() + p.sizeZ() / 2;
    int bx0 = job.origin.getX(), bz0 = job.origin.getZ(), bx1 = bx0 + p.sizeX() - 1, bz1 = bz0 + p.sizeZ() - 1;
    int by0 = job.origin.getY();
    String header = String.join("\n",
        "# ENTRELUMEN ruin terrain survey (" + when + "): " + run.spot.ruin().id() + " on " + run.spot.kind().name()
            + " (" + run.spot.biome() + ")",
        "# seed " + level.getSeed() + ", Minecraft " + SharedConstants.getCurrentVersion().getName()
            + "; world centre " + cx + " " + cz + ", ground level " + ground + ", template origin "
            + job.origin.getX() + " " + job.origin.getY() + " " + job.origin.getZ(),
        "# region " + run.x0 + ".." + run.x1 + " " + run.y0 + ".." + run.y1 + " " + run.z0 + ".." + run.z1
            + "; margin " + job.margin + ", spread " + job.spread,
        "# one block per line, x y z state, relative to the centre and the ground level (art/structures convention);",
        "# every block of the ruin's box, and around it every block with a face open to the air",
        "");
    Path file = run.out.resolve(run.spot.kind().name()).resolve(run.spot.ruin().name() + "." + when + ".txt");
    run.writes.add(CompletableFuture.runAsync(() -> {
      StringBuilder out = new StringBuilder(header);
      Map<BlockState, String> names = new HashMap<>();
      int kept = 0;
      for (int y = 0; y < h; y++)
        for (int z = 0; z < d; z++)
          for (int x = 0; x < w; x++) {
            BlockState state = states[(y * d + z) * w + x];
            if (state == null || state.isAir()) continue;
            int wx = run.x0 + x, wy = run.y0 + y, wz = run.z0 + z;
            boolean box = wx >= bx0 && wx <= bx1 && wz >= bz0 && wz <= bz1 && wy >= by0;
            if (!box && !open(states, w, h, d, x, y, z)) continue;
            out.append(wx - cx).append(' ').append(wy - ground).append(' ').append(wz - cz).append(' ')
                .append(names.computeIfAbsent(state, BlockStateParser::serialize)).append('\n');
            kept++;
          }
      try {
        Files.createDirectories(file.getParent());
        Files.writeString(file, out, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
      LOGGER.info("Ruin terrain survey: wrote {} ({} blocks)", file, kept);
    }, Util.backgroundExecutor()));
  }

  /** Whether a block shows a face: a neighbour that does not hide it, or the region's edge. */
  private static boolean open(BlockState[] states, int w, int h, int d, int x, int y, int z) {
    int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    for (int[] s : steps) {
      int nx = x + s[0], ny = y + s[1], nz = z + s[2];
      if (nx < 0 || ny < 0 || nz < 0 || nx >= w || ny >= h || nz >= d) return true;
      BlockState next = states[(ny * d + nz) * w + nx];
      if (next == null || !next.canOcclude()) return true;
    }
    return false;
  }

  private static void record(Run run) {
    var job = run.job;
    var p = job.prepared;
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("ruin", run.spot.ruin().id());
    entry.put("site", run.spot.kind().name());
    entry.put("biome", run.spot.biome());
    entry.put("centre", List.of(job.origin.getX() + p.sizeX() / 2, job.origin.getZ() + p.sizeZ() / 2));
    entry.put("origin", List.of(job.origin.getX(), job.origin.getY(), job.origin.getZ()));
    entry.put("groundLevel", job.origin.getY() + p.ground());
    entry.put("spread", job.spread);
    entry.put("noiseSpread", run.spot.spread());
    entry.put("margin", job.margin);
    entry.put("siteGround", List.of(BlockStateParser.serialize(job.palette.surface()),
        BlockStateParser.serialize(job.palette.filler()), job.palette.snow() ? "snow" : "no snow"));
    entry.put("masonry", BlockStateParser.serialize(p.shape().masonry()));
    entry.put("siteRock", job.palette.rocks().stream().map(BlockStateParser::serialize).toList());
    entry.put("lowest", job.lowest == Integer.MAX_VALUE ? job.origin.getY() : job.lowest);
    entry.put("felledBlocks", job.felledCount);
    entry.put("busyMs", Math.round(job.busyNanos / 1e6));
    entry.put("busyTicks", job.busyTicks);
    entry.put("worstTickMs", Math.round(job.worstNanos / 1e6));
    entry.put("files", List.of(run.spot.kind().name() + "/" + run.spot.ruin().name() + ".before.txt",
        run.spot.kind().name() + "/" + run.spot.ruin().name() + ".after.txt"));
    run.placements.add(entry);
  }

  private static void finish(Run run) {
    release(run);
    synchronized (RuinTerrainSurvey.class) {
      RUNS.remove(run.server);
    }
    CompletableFuture.allOf(run.writes.toArray(CompletableFuture[]::new)).join();
    Map<String, Object> index = new LinkedHashMap<>();
    index.put("seed", run.level.getSeed());
    index.put("minecraft", SharedConstants.getCurrentVersion().getName());
    index.put("format", "x y z state per line, relative to the ruin's centre and ground level");
    index.put("seconds", Math.round((System.nanoTime() - run.started) / 1e9));
    if (run.spotsFrom != null) index.put("sitesFrom", run.spotsFrom.toString());
    index.put("placements", run.placements);
    try {
      Files.createDirectories(run.out);
      Files.writeString(run.out.resolve("index.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(index) + "\n", StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.error("Ruin terrain survey: could not write the index", e);
    }
    LOGGER.info("Ruin terrain survey finished: {} placements into {}", run.placements.size(), run.out);
    if (run.stop) run.server.halt(false);
  }
}
