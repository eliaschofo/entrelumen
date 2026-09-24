package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
    RuinData data = RuinData.get(event.getServer());
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
    BlockPos origin = chooseSite(level, near, size);
    StructurePlaceSettings settings = new StructurePlaceSettings();
    BoundingBox box = template.getBoundingBox(settings, origin);
    template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
    int lowest = pourFoundation(level, box);

    BlockPos arrival = null;
    for (var info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
      if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
      if (SPAWN_MARKERS.contains(info.nbt().getString("metadata"))) arrival = info.pos();
      level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
    if (arrival == null) arrival = besideRuin(level, box);
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
    if (moveSpawn) level.setDefaultSpawnPos(arrival, facing(arrival, ruin.center()));
    return Optional.of(ruin);
  }

  private record Evaluation(int floor, int spread, int canopy) {}

  /** Nearest candidate with a flat, dry, tree-free footprint; otherwise the best one seen. */
  static BlockPos chooseSite(ServerLevel level, BlockPos near, Vec3i size) {
    BlockPos best = null;
    int bestScore = Integer.MAX_VALUE;
    int[] offset = new int[2];
    RingCursor cursor = new RingCursor(SEARCH_RADIUS / STEP);
    while (cursor.next(offset)) {
      int x0 = near.getX() + offset[0] * STEP - size.getX() / 2;
      int z0 = near.getZ() + offset[1] * STEP - size.getZ() / 2;
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
    if (best != null) return best;
    loadColumn(level, near.getX(), near.getZ());
    int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()) - 1;
    return new BlockPos(near.getX() - size.getX() / 2, floor, near.getZ() - size.getZ() / 2);
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
  private static int pourFoundation(ServerLevel level, BoundingBox box) {
    int lowest = box.minY();
    for (int x = box.minX(); x <= box.maxX(); x++)
      for (int z = box.minZ(); z <= box.maxZ(); z++) {
        BlockPos floorPos = new BlockPos(x, box.minY(), z);
        BlockState floor = level.getBlockState(floorPos);
        boolean open = floor.canBeReplaced();
        if (open) floor = groundBelow(level, floorPos);
        BlockState fill =
            floor.isCollisionShapeFullBlock(level, floorPos)
                ? floor
                : Blocks.TUFF.defaultBlockState();
        for (int y = box.minY() - (open ? 0 : 1); y >= box.minY() - MAX_FOUNDATION; y--) {
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

  /** Middle of the south side, then the other sides; last resort, on the floor at the center. */
  static BlockPos besideRuin(ServerLevel level, BoundingBox box) {
    int cx = (box.minX() + box.maxX()) / 2, cz = (box.minZ() + box.maxZ()) / 2;
    int[][] sides = {
      {cx, box.maxZ() + 1}, {cx, box.minZ() - 1}, {box.maxX() + 1, cz}, {box.minX() - 1, cz}
    };
    for (int[] side : sides) {
      loadColumn(level, side[0], side[1]);
      BlockPos pos = new BlockPos(side[0],
          level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, side[0], side[1]), side[1]);
      if (standable(level, pos)) return pos;
    }
    BlockPos pos = new BlockPos(cx, box.minY() + 1, cz);
    while (!standable(level, pos) && pos.getY() < level.getMaxBuildHeight() - 2) pos = pos.above();
    return pos;
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
