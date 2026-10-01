package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Places the start ruin once per new world, anchored to the ground near the vanilla spawn, and
 * moves the world spawn beside it. Works with any template stored at the start-ruin path.
 */
public final class HeliodorRuins {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceLocation START =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "heliodor_ruin_start");
  /** Candidate centers are sampled every {@code STEP} blocks up to {@code SEARCH_RADIUS}. */
  static final int SEARCH_RADIUS = 64, STEP = 4;
  /** Accept at once: at most this height difference across the footprint and no canopy. */
  static final int MAX_SPREAD = 2;
  /** Deepest foundation poured under the floor layer over a dip. */
  static final int MAX_FOUNDATION = 12;
  /** Optional data structure block ("spawn") inside a template marks the arrival point. */
  static final Set<String> SPAWN_MARKERS = Set.of("spawn", "entrelumen:spawn");
  /**
   * Optional data structure block standing on the floor layer of a template that reaches below
   * ground (the start ruin's Sealed Stair): the template sinks so that layer meets the ground.
   */
  static final String GROUND_MARKER = "entrelumen:ground";
  /**
   * The site search runs on the server thread inside ServerStartedEvent, within the 60 s watchdog
   * window: it generates at most this many new chunks and runs at most {@link #MAX_SEARCH_NANOS},
   * then settles for the best site seen.
   */
  static final int MAX_NEW_CHUNKS = 24;
  static final long MAX_SEARCH_NANOS = 10_000_000_000L;
  /**
   * Blocks every player may use (and nobody can break) that dedicated-server spawn protection
   * leaves alone: the start ruin moves the world spawn beside its pedestal and the Envés gate.
   */
  public static final TagKey<Block> SPAWN_PROTECTION_EXEMPT = TagKey.create(Registries.BLOCK,
      ResourceLocation.fromNamespaceAndPath("entrelumen", "spawn_protection_exempt"));

  private HeliodorRuins() {}

  /** Fires only while a brand-new overworld chooses its first spawn. */
  public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
    if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD)
      return;
    // Isolated GameTests share one flat world; they place ruins explicitly, never implicitly.
    if (level.getServer() instanceof GameTestServer) return;
    RuinData data = RuinData.get(level.getServer());
    data.pendingStart = true;
    data.setDirty();
  }

  public static void onServerStarted(ServerStartedEvent event) {
    var server = event.getServer();
    if (server.isDedicatedServer() && server.getSpawnProtectionRadius() > 0)
      LOGGER.info("spawn-protection is {}: blocks tagged #{} (the start ruin's pedestal, the Envés gate and seals,"
          + " the Solsticio portal) stay usable by every player; set spawn-protection=0 in server.properties"
          + " to open the rest of the spawn area to non-operators", server.getSpawnProtectionRadius(),
          SPAWN_PROTECTION_EXEMPT.location());
    RuinData data = RuinData.get(server);
    if (!data.pendingStart || data.find(START).isPresent()) return;
    ServerLevel overworld = event.getServer().overworld();
    place(overworld, data, overworld.getSharedSpawnPos(), true)
        .ifPresent(ruin -> LOGGER.info("Placed the Heliodor start ruin at {} (spawn {})",
            ruin.box(), ruin.arrival()));
  }

  /** A brand-new player appears at the arrival point, facing the ruin. */
  public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    RuinData.get(player.server).find(START).ifPresent(ruin -> welcome(player, ruin));
  }

  static boolean welcome(ServerPlayer player, RuinData.Ruin ruin) {
    if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0
        || player.getRespawnPosition() != null
        || !player.level().dimension().equals(ruin.dimension())) return false;
    ServerLevel level = player.server.getLevel(ruin.dimension());
    if (level == null) return false;
    BlockPos arrival = ruin.arrival();
    player.teleportTo(level, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
        facing(arrival, ruin.center()), 0f);
    return true;
  }

  /**
   * Places the start ruin unless {@code data} already holds one. Returns the stored record, which
   * callers must not assume was placed by this call.
   */
  public static Optional<RuinData.Ruin> place(
      ServerLevel level, RuinData data, BlockPos near, boolean moveSpawn) {
    var existing = data.find(START);
    if (existing.isPresent()) return existing;
    StructureTemplate template = level.getStructureManager().get(START).orElse(null);
    if (template == null) {
      LOGGER.error("Missing structure template {}; the start ruin was not placed", START);
      return Optional.empty();
    }
    Vec3i size = template.getSize();
    if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
      LOGGER.error("Structure template {} is empty; the start ruin was not placed", START);
      return Optional.empty();
    }
    StructurePlaceSettings settings = new StructurePlaceSettings();
    int ground = groundLayer(template, settings);
    BlockPos origin = chooseSite(level, near, size).below(ground);
    // Ground near the bottom of the world (a superflat world) would sink the Sealed Stair below it:
    // the template is lifted onto the world floor and the arrival step gets a stair down.
    int lift = Math.max(0, level.getMinBuildHeight() - origin.getY());
    origin = origin.above(lift);
    BoundingBox box = template.getBoundingBox(settings, origin);
    template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
    int lowest = pourFoundation(level, box, box.minY() + ground);
    // The foundation may pour into what the template carved below the floor; carve it again.
    if (ground > 0)
      template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);

    BlockPos arrival = null;
    for (var info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
      if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
      if (SPAWN_MARKERS.contains(info.nbt().getString("metadata"))) arrival = info.pos();
      level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    if (arrival == null) arrival = besideRuin(level, box, box.minY() + ground, lift > 0);
    List<BlockPos> pedestals = new ArrayList<>();
    for (var info : template.filterBlocks(origin, settings, HeliodorContent.PEDESTAL.get()))
      pedestals.add(info.pos().immutable());
    if (pedestals.isEmpty()) pedestals.add(fallbackPedestal(level, arrival));

    var ruin = new RuinData.Ruin(START, START, level.dimension(), 1,
        new BoundingBox(box.minX(), Math.min(lowest, box.minY()), box.minZ(),
            box.maxX(), box.maxY(), box.maxZ()),
        origin, arrival, pedestals, level.getGameTime());
    data.add(ruin);
    data.pendingStart = false;
    data.setDirty();
    // The foundation is already poured: from now on the whole registered box is protected.
    StructureProtection.invalidate(level.getServer());
    EnvesEntrance.onStartRuinPlaced(level, template, origin, settings, ground);
    if (moveSpawn) level.setDefaultSpawnPos(arrival, facing(arrival, ruin.center()));
    return Optional.of(ruin);
  }

  /** Height of the floor layer in the template: the ground marker stands on it; 0 without one. */
  static int groundLayer(StructureTemplate template, StructurePlaceSettings settings) {
    for (var info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.STRUCTURE_BLOCK))
      if (info.nbt() != null && "DATA".equals(info.nbt().getString("mode"))
          && GROUND_MARKER.equals(info.nbt().getString("metadata")))
        return Math.max(0, info.pos().getY() - 1);
    return 0;
  }

  private record Evaluation(int floor, int spread, int canopy) {}

  /**
   * Nearest candidate with a flat, dry, tree-free footprint; otherwise the best one seen. A
   * candidate whose noise surface is already too uneven is skipped before any chunk is loaded, and
   * the search stops after {@link #MAX_NEW_CHUNKS} new chunks or {@link #MAX_SEARCH_NANOS}.
   */
  static BlockPos chooseSite(ServerLevel level, BlockPos near, Vec3i size) {
    SiteBudget budget = new SiteBudget(level, MAX_NEW_CHUNKS, MAX_SEARCH_NANOS);
    BlockPos best = null;
    int bestScore = Integer.MAX_VALUE;
    // The screened-out candidate with the smallest noise spread: read only when none passed.
    int[] rough = null;
    int roughSpread = Integer.MAX_VALUE;
    int[] offset = new int[2];
    RingCursor cursor = new RingCursor(SEARCH_RADIUS / STEP);
    while (cursor.next(offset)) {
      // The noise screen costs time too: the clock is checked before every candidate.
      if (budget.expired()) break;
      int x0 = near.getX() + offset[0] * STEP - size.getX() / 2;
      int z0 = near.getZ() + offset[1] * STEP - size.getZ() / 2;
      int noise = noiseSpread(level, x0, z0, size);
      if (noise > MAX_SPREAD + 2) {
        if (noise < roughSpread) {
          roughSpread = noise;
          rough = new int[] {x0, z0};
        }
        continue;
      }
      if (!budget.afford(x0, z0, size)) break;
      Evaluation evaluation = evaluate(level, x0, z0, size);
      if (evaluation == null) continue;
      BlockPos origin = new BlockPos(x0, evaluation.floor(), z0);
      if (evaluation.spread() <= MAX_SPREAD && evaluation.canopy() == 0) return origin;
      int score = evaluation.spread() * 8 + evaluation.canopy() * 2 + cursor.ring();
      if (score < bestScore) {
        bestScore = score;
        best = origin;
      }
    }
    if (best == null && rough != null && budget.afford(rough[0], rough[1], size)) {
      Evaluation evaluation = evaluate(level, rough[0], rough[1], size);
      if (evaluation != null) best = new BlockPos(rough[0], evaluation.floor(), rough[1]);
    }
    if (budget.spent())
      LOGGER.info("The start ruin site search stopped after {} new chunks in {} ms; using the best site seen",
          budget.newChunks(), budget.elapsedMillis());
    if (best != null) return best;
    loadColumn(level, near.getX(), near.getZ());
    int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()) - 1;
    return new BlockPos(near.getX() - size.getX() / 2, floor, near.getZ() - size.getZ() / 2);
  }

  /**
   * Height difference of the generator's noise surface across the footprint's four corners and
   * centre, read without loading a chunk. Trees and surface features are not in it: {@link
   * #evaluate} judges those on the generated terrain.
   */
  static int noiseSpread(ServerLevel level, int x0, int z0, Vec3i size) {
    ChunkGenerator generator = level.getChunkSource().getGenerator();
    RandomState random = level.getChunkSource().randomState();
    int x1 = x0 + size.getX() - 1, z1 = z0 + size.getZ() - 1;
    int[][] samples = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {(x0 + x1) / 2, (z0 + z1) / 2}};
    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
    for (int[] sample : samples) {
      int height = generator.getBaseHeight(sample[0], sample[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
      min = Math.min(min, height);
      max = Math.max(max, height);
    }
    return max - min;
  }

  /** The new chunks and wall time the site search may still spend. */
  static final class SiteBudget {
    private final ServerLevel level;
    private final int maxNewChunks;
    private final long started = System.nanoTime();
    private final long maxNanos;
    private int newChunks;
    private boolean spent;

    SiteBudget(ServerLevel level, int maxNewChunks, long maxNanos) {
      this.level = level;
      this.maxNewChunks = maxNewChunks;
      this.maxNanos = maxNanos;
    }

    /**
     * Whether the footprint may be read: each of its chunks that is not loaded yet counts as a new
     * generation. Once the chunks or the time run out it answers false for good.
     */
    boolean afford(int x0, int z0, Vec3i size) {
      if (expired()) return false;
      int missing = 0;
      for (int cx = x0 >> 4; cx <= (x0 + size.getX() - 1) >> 4; cx++)
        for (int cz = z0 >> 4; cz <= (z0 + size.getZ() - 1) >> 4; cz++)
          if (level.getChunkSource().getChunkNow(cx, cz) == null) missing++;
      if (newChunks + missing > maxNewChunks) {
        spent = true;
        return false;
      }
      newChunks += missing;
      return true;
    }

    /** Whether the budget is gone, by chunks or by wall time; once it is, it stays gone. */
    boolean expired() {
      if (!spent && System.nanoTime() - started > maxNanos) spent = true;
      return spent;
    }

    boolean spent() {
      return spent;
    }

    int newChunks() {
      return newChunks;
    }

    long elapsedMillis() {
      return (System.nanoTime() - started) / 1_000_000L;
    }
  }

  /** Null for water, lava or tree trunks inside the footprint. Floor is the most common ground. */
  private static Evaluation evaluate(ServerLevel level, int x0, int z0, Vec3i size) {
    Map<Integer, Integer> grounds = new HashMap<>();
    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, canopy = 0;
    for (int x = x0; x < x0 + size.getX(); x++)
      for (int z = z0; z < z0 + size.getZ(); z++) {
        loadColumn(level, x, z);
        int free = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (free <= level.getMinBuildHeight() + 1) return null;
        BlockState ground = level.getBlockState(new BlockPos(x, free - 1, z));
        if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LOGS)) return null;
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > free) canopy++;
        grounds.merge(free - 1, 1, Integer::sum);
        min = Math.min(min, free - 1);
        max = Math.max(max, free - 1);
      }
    int floor =
        grounds.entrySet().stream()
            .max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
            .orElseThrow()
            .getKey();
    return new Evaluation(floor, max - min, canopy);
  }

  private static void loadColumn(ServerLevel level, int x, int z) {
    level.getChunk(x >> 4, z >> 4);
  }

  /**
   * Fills dips under the floor layer with the floor block itself, so nothing floats. Floor cells
   * the template leaves to the terrain (a round ruin's corners) are levelled with the ground found
   * under them instead, floor cell included.
   */
  private static int pourFoundation(ServerLevel level, BoundingBox box, int floorY) {
    int lowest = box.minY();
    for (int x = box.minX(); x <= box.maxX(); x++)
      for (int z = box.minZ(); z <= box.maxZ(); z++) {
        BlockPos floorPos = new BlockPos(x, floorY, z);
        BlockState floor = level.getBlockState(floorPos);
        boolean open = floor.canBeReplaced();
        if (open) floor = groundBelow(level, floorPos);
        BlockState fill =
            floor.isCollisionShapeFullBlock(level, floorPos)
                ? floor
                : Blocks.TUFF.defaultBlockState();
        for (int y = floorY - (open ? 0 : 1); y >= floorY - MAX_FOUNDATION; y--) {
          BlockPos pos = new BlockPos(x, y, z);
          BlockState state = level.getBlockState(pos);
          if (!state.canBeReplaced() && !state.is(BlockTags.LEAVES)) break;
          level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
          lowest = Math.min(lowest, y);
        }
      }
    return lowest;
  }

  /** The first solid terrain block under a floor cell, or tuff when the dip is deeper than a foundation. */
  private static BlockState groundBelow(ServerLevel level, BlockPos floorPos) {
    for (int dy = 1; dy <= MAX_FOUNDATION; dy++) {
      BlockState state = level.getBlockState(floorPos.below(dy));
      if (!state.canBeReplaced() && !state.is(BlockTags.LEAVES)) return state;
    }
    return Blocks.TUFF.defaultBlockState();
  }

  static BlockPos besideRuin(ServerLevel level, BoundingBox box, int floorY) {
    return besideRuin(level, box, floorY, false);
  }

  /**
   * Middle of the south side, then the other sides. When none of them offers footing (a ruin
   * surrounded by open water, lava or a canopy), a tuff step is laid at the south middle, level
   * with the ruin floor, so the arrival point is always beside the ruin and safe. Last resort, on
   * the floor at the center. A {@code raised} ruin (lifted onto the world floor, its patio above the
   * ground) always takes the step, with a tuff stair from it down to the ground.
   */
  static BlockPos besideRuin(ServerLevel level, BoundingBox box, int floorY, boolean raised) {
    int cx = (box.minX() + box.maxX()) / 2, cz = (box.minZ() + box.maxZ()) / 2;
    int[][] sides = raised ? new int[0][] : new int[][] {
      {cx, box.maxZ() + 1}, {cx, box.minZ() - 1}, {box.maxX() + 1, cz}, {box.minX() - 1, cz}
    };
    for (int[] side : sides) {
      loadColumn(level, side[0], side[1]);
      BlockPos pos = new BlockPos(side[0],
          level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, side[0], side[1]), side[1]);
      if (standable(level, pos)) return pos;
    }
    BlockPos step = new BlockPos(cx, floorY + 1, box.maxZ() + 1);
    if (step.getY() + 1 < level.getMaxBuildHeight()) {
      BlockPos below = step.below();
      if (!level.getBlockState(below).getFluidState().isEmpty()
          || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
        level.setBlock(below, Blocks.TUFF.defaultBlockState(), Block.UPDATE_CLIENTS);
      for (BlockPos pos : List.of(step, step.above())) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty())
          level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
      if (standable(level, step)) {
        if (raised) stairDown(level, step);
        return step;
      }
    }
    BlockPos pos = new BlockPos(cx, floorY + 1, cz);
    while (!standable(level, pos) && pos.getY() < level.getMaxBuildHeight() - 2) pos = pos.above();
    return pos;
  }

  /** Longest stair {@link #stairDown} lays. */
  static final int MAX_STAIR = 48;

  /**
   * A one-wide tuff stair from the arrival step down to the ground, heading south, away from the
   * ruin: each tread one block lower and one further out, with tuff poured under it so nothing
   * floats. It ends where a tread would sit on existing ground.
   */
  static void stairDown(ServerLevel level, BlockPos step) {
    BlockState tread = Blocks.TUFF_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
    for (int i = 1; i <= MAX_STAIR; i++) {
      BlockPos feet = step.offset(0, -i, i), support = feet.below();
      if (!level.isInWorldBounds(support) || !level.getBlockState(support).canBeReplaced()) return;
      level.setBlock(support, tread, Block.UPDATE_CLIENTS);
      for (BlockPos pos : List.of(feet, feet.above())) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty())
          level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
      for (int dy = 1; dy <= MAX_FOUNDATION; dy++) {
        BlockPos fill = support.below(dy);
        if (!level.isInWorldBounds(fill)) break;
        BlockState state = level.getBlockState(fill);
        if (!state.canBeReplaced() && !state.is(BlockTags.LEAVES)) break;
        level.setBlock(fill, Blocks.TUFF.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
    }
  }

  static boolean standable(ServerLevel level, BlockPos pos) {
    BlockState below = level.getBlockState(pos.below());
    return below.getFluidState().isEmpty()
        && below.isFaceSturdy(level, pos.below(), Direction.UP)
        && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
        && level.getBlockState(pos).getFluidState().isEmpty()
        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
  }

  /** A template without a pedestal still yields one, beside the arrival point. */
  private static BlockPos fallbackPedestal(ServerLevel level, BlockPos arrival) {
    for (Direction direction : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH)) {
      BlockPos pos = arrival.relative(direction);
      if (level.getBlockState(pos).canBeReplaced()) {
        level.setBlock(pos, HeliodorContent.PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        return pos;
      }
    }
    BlockPos pos = arrival.east();
    level.setBlock(pos, HeliodorContent.PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
    return pos;
  }

  /** Yaw that faces {@code target} from {@code from}. */
  static float facing(BlockPos from, BlockPos target) {
    double dx = target.getX() - from.getX(), dz = target.getZ() - from.getZ();
    if (dx == 0 && dz == 0) return 0f;
    return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
  }
}
