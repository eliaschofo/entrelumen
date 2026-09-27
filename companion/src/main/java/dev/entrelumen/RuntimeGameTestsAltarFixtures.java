package dev.entrelumen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/**
 * Fixtures shared by the isolated and the full-pack altar GameTests that must not depend on where a
 * test lands or on what ran before it: untouched land found far from every test, and a platform of
 * known land high over a test. Test code only: the release JAR leaves out every RuntimeGameTests*
 * class, and the QA JAR carries it for the full-pack cases.
 */
final class RuntimeGameTestsAltarFixtures {
  private static final ExecutorService REFERENCE_WORKER = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "Entrelumen altar QA reference");
    thread.setDaemon(true);
    return thread;
  });

  private RuntimeGameTestsAltarFixtures() {}

  // ---- Small helpers ----------------------------------------------------------------------------

  /**
   * Waits a tick at a time. The isolated GameTest server ticks as fast as it can, so there each waiting
   * tick also lasts at least 20 ms, giving the reference worker wall time; a real server keeps 20 TPS.
   */
  static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what, Runnable then,
      Runnable onTimeout) {
    if (done.getAsBoolean()) {
      then.run();
      return;
    }
    if (waited >= limit) onTimeout.run();
    helper.assertTrue(waited < limit, what + " did not finish in time");
    if (helper.getLevel().getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
      java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
    helper.runAfterDelay(1, () -> await(helper, done, waited + 1, limit, what, then, onTimeout));
  }

  /** Force-loads a square of chunks; the returned action releases them, once. */
  static Runnable forceChunks(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) level.setChunkForced(x, z, true);
    boolean[] released = {false};
    return () -> {
      if (released[0]) return;
      released[0] = true;
      for (int x = minX; x <= maxX; x++)
        for (int z = minZ; z <= maxZ; z++) level.setChunkForced(x, z, false);
    };
  }

  static Map<BlockPos, BlockState> box(ServerLevel level, BlockPos from, BlockPos to) {
    Map<BlockPos, BlockState> states = new HashMap<>();
    for (BlockPos pos : BlockPos.betweenClosed(from, to)) states.put(pos.immutable(), level.getBlockState(pos));
    return states;
  }

  static Map<BlockPos, BlockState> changed(ServerLevel level, Map<BlockPos, BlockState> before) {
    Map<BlockPos, BlockState> changed = new HashMap<>();
    before.forEach((pos, state) -> {
      BlockState now = level.getBlockState(pos);
      if (!now.equals(state)) changed.put(pos, now);
    });
    return changed;
  }

  /** Removes a test altar without dropping its fuel or fertilizer into the level. */
  static void removeAltar(ServerLevel level, BlockPos pos) {
    level.removeBlockEntity(pos);
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
  }

  /** Open space over a floor: air or a wild plant, such as grass, flowers, roots or a snow layer. */
  static boolean open(BlockState state) {
    return !TerraformRules.solid(TerraformAltarEntity.cell(state));
  }

  /** Plain ground at {@code y} with {@code height} blocks of open space over it. */
  static boolean openFloor(ServerLevel level, int x, int y, int z, int height) {
    if (!TerraformAltarEntity.basic(level.getBlockState(new BlockPos(x, y, z)))) return false;
    for (int dy = 1; dy <= height; dy++) if (!open(level.getBlockState(new BlockPos(x, y + dy, z)))) return false;
    return true;
  }

  /** Clears the wild plants standing on these floors, as a player digging there would first. */
  static void clearPlants(ServerLevel level, List<BlockPos> floors) {
    for (BlockPos floor : floors)
      for (BlockPos pos = floor.above(); open(level.getBlockState(pos)) && !level.getBlockState(pos).isAir(); pos = pos.above())
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
  }

  // ---- Untouched land far from the tests --------------------------------------------------------

  /** Open lowland: plains, savanna, desert or a light forest, without water, hills or mountains. */
  static boolean openLand(Holder<Biome> biome) {
    boolean kind = biome.is(Tags.Biomes.IS_PLAINS) || biome.is(BiomeTags.IS_SAVANNA) || biome.is(Tags.Biomes.IS_DESERT)
        || biome.is(BiomeTags.IS_FOREST) && !biome.is(Tags.Biomes.IS_DENSE_VEGETATION_OVERWORLD);
    return kind && !biome.is(BiomeTags.IS_MOUNTAIN) && !biome.is(BiomeTags.IS_HILL) && !biome.is(BiomeTags.IS_OCEAN)
        && !biome.is(BiomeTags.IS_RIVER) && !biome.is(Tags.Biomes.IS_SWAMP);
  }

  /**
   * A middle chunk about two thousand blocks from the test, away from every other test's structures,
   * whose 5×5 chunks are open lowland by the generator's own biomes. Ring by ring from a fixed offset,
   * so the choice depends only on the world.
   */
  static ChunkPos pristineLand(ServerLevel level, BlockPos origin) {
    var generator = level.getChunkSource().getGenerator();
    var sampler = level.getChunkSource().randomState().sampler();
    int y = QuartPos.fromBlock(generator.getSeaLevel() + 4);
    int baseX = (origin.getX() >> 4) - 128, baseZ = (origin.getZ() >> 4) + 64;
    for (int ring = 0; ring <= 24; ring++)
      for (int dx = -ring; dx <= ring; dx++)
        for (int dz = -ring; dz <= ring; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
          int mx = baseX + 4 * dx, mz = baseZ + 4 * dz;
          boolean land = true;
          for (int cx = mx - 2; cx <= mx + 2 && land; cx++)
            for (int cz = mz - 2; cz <= mz + 2 && land; cz++)
              land = openLand(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(cx * 16 + 8), y,
                  QuartPos.fromBlock(cz * 16 + 8), sampler));
          if (land) return new ChunkPos(mx, mz);
        }
    return null;
  }

  /** The altar's own trust test of a chunk in the open, run against the test's reference. */
  static boolean trusted(ServerLevel level, TerrainReference.Region reference, ChunkPos chunk) {
    if (reference.unresolved(chunk.x, chunk.z) || !reference.decorated(chunk.x, chunk.z)) return false;
    int natural = 0, agree = 0, surplus = 0;
    for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
      for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
        int original = TerraformAltarEntity.referenceGround(reference, x, z);
        int current = TerraformAltarEntity.currentGround(level, x, z);
        if (original == Integer.MIN_VALUE || current == Integer.MIN_VALUE) continue;
        natural++;
        if (Math.abs(current - original) <= 1) agree++;
        else if (current > original + 1) surplus++;
      }
    return natural >= 64 && AltarRules.consistent(natural, agree, surplus);
  }

  /** A repair site: the altar's floor, the floors the test clears, and the pit it digs. */
  record RepairSite(BlockPos floor, List<BlockPos> floors, List<BlockPos> pit) {}

  /**
   * The first place, ring by ring from the middle chunk, where the ground is open plain terrain, a 3×3
   * patch three to five blocks east of it is plain ground {@code depth} + 1 deep within two blocks of
   * the same height with no fluid beside it, all exactly as the generator made them, and every chunk of
   * the repair square passes the altar's trust test. {@code stats} counts how far the candidates got.
   */
  static RepairSite repairSite(ServerLevel level, TerrainReference.Region reference, ChunkPos middle, int radius,
      int depth, Map<String, Integer> stats) {
    int cx = middle.getMiddleBlockX(), cz = middle.getMiddleBlockZ();
    Map<Long, Boolean> chunks = new HashMap<>();
    for (int ring = 0; ring <= 12; ring++)
      for (int x = cx - ring; x <= cx + ring; x++)
        for (int z = cz - ring; z <= cz + ring; z++) {
          if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != ring) continue;
          int y = TerraformAltarEntity.currentGround(level, x, z);
          if (y == Integer.MIN_VALUE || !openFloor(level, x, y, z, 3)) continue;
          stats.merge("floors", 1, Integer::sum);
          List<BlockPos> floors = new ArrayList<>(List.of(new BlockPos(x, y, z)));
          List<BlockPos> pit = new ArrayList<>();
          boolean patch = true;
          for (int dx = 3; dx <= 5 && patch; dx++)
            for (int dz = -1; dz <= 1 && patch; dz++) {
              int f = TerraformAltarEntity.currentGround(level, x + dx, z + dz);
              patch = f != Integer.MIN_VALUE && Math.abs(f - y) <= 2 && openFloor(level, x + dx, f, z + dz, 2);
              for (int dy = 0; dy >= -depth && patch; dy--) {
                BlockPos pos = new BlockPos(x + dx, f + dy, z + dz);
                patch = TerraformAltarEntity.basic(level.getBlockState(pos));
                for (Direction side : Direction.Plane.HORIZONTAL)
                  if (!level.getBlockState(pos.relative(side)).getFluidState().isEmpty()) patch = false;
                if (dy > -depth) pit.add(pos);
              }
              if (patch) floors.add(new BlockPos(x + dx, f, z + dz));
            }
          if (!patch) continue;
          stats.merge("patches", 1, Integer::sum);
          boolean generated = true;
          for (BlockPos floor : floors) {
            for (int dy = 0; dy >= -depth && generated; dy--) {
              BlockState original = reference.state(floor.above(dy));
              generated = original != null && level.getBlockState(floor.above(dy)).is(original.getBlock());
            }
            BlockState over = reference.state(floor.above());
            if (over == null || !open(over)) generated = false;
          }
          if (!generated) continue;
          stats.merge("asGenerated", 1, Integer::sum);
          boolean comparable = true;
          for (var key : AltarRules.areaChunks(x, z, radius)) {
            ChunkPos chunk = new ChunkPos(key.x(), key.z());
            comparable &= chunks.computeIfAbsent(chunk.toLong(), ignored -> trusted(level, reference, chunk));
          }
          if (!comparable) {
            stats.merge("untrusted", 1, Integer::sum);
            continue;
          }
          return new RepairSite(new BlockPos(x, y, z), floors, pit);
        }
    return null;
  }

  /**
   * The repair on untouched land of the level's own overworld generator, far from every other test: a
   * pit {@code depth} deep dug there is refilled exactly with the generator's blocks, every chunk is
   * recognised, and the altar changes nothing the generator did not make. The place is chosen against
   * an independent regeneration, so it depends only on the world, never on where the test runs or on
   * the order of the tests.
   */
  static void pristineRepair(GameTestHelper helper, int radius, int depth, String tag) {
    ServerLevel level = helper.getLevel();
    ChunkPos middle = pristineLand(level, helper.absolutePos(BlockPos.ZERO));
    helper.assertTrue(middle != null, "No open lowland two thousand blocks from the test in this world");
    Runnable release = forceChunks(level, middle.x - 2, middle.z - 2, middle.x + 2, middle.z + 2);
    await(helper, () -> LandWorks.loaded(level, middle.x - 2, middle.z - 2, middle.x + 2, middle.z + 2), 0, 3000,
        "Untouched land loading", () -> {
      try {
        List<ChunkPos> decorate = new ArrayList<>(), captured = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++)
          for (int dz = -2; dz <= 2; dz++) {
            ChunkPos pos = new ChunkPos(middle.x + dx, middle.z + dz);
            captured.add(pos);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) decorate.add(pos);
          }
        var setup = TerrainReference.Setup.capture(level, captured);
        CompletableFuture<TerrainReference.Region> built = CompletableFuture.supplyAsync(
            () -> TerrainReference.build(setup, List.copyOf(decorate), () -> false), REFERENCE_WORKER);
        await(helper, built::isDone, 0, 3000, "Independent regeneration", () -> {
          try {
            repairOn(helper, built.join(), middle, radius, depth, tag, release);
          } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
          }
        }, release);
      } catch (RuntimeException | Error failure) {
        release.run();
        throw failure;
      }
    }, release);
  }

  private static void repairOn(GameTestHelper helper, TerrainReference.Region reference, ChunkPos middle, int radius,
      int depth, String tag, Runnable release) {
    ServerLevel level = helper.getLevel();
    Map<String, Integer> stats = new TreeMap<>();
    RepairSite site = repairSite(level, reference, middle, radius, depth, stats);
    com.mojang.logging.LogUtils.getLogger().info(
        "{} repair site={} middle={} biome={} stats={} reference=[terrain={} decorated={} terrainMillis={} decorationMillis={} failures={} unsupported={}]",
        tag, site == null ? null : site.floor(), middle, site == null ? null
            : level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(site.floor()).value()),
        stats, reference.terrainChunks, reference.decoratedChunks, reference.terrainNanos / 1_000_000,
        reference.decorationNanos / 1_000_000, reference.failures, reference.unsupported);
    helper.assertTrue(site != null, "No open ground as the generator made it near " + middle + ": " + stats);
    BlockPos floor = site.floor(), altarPos = floor.above();
    clearPlants(level, site.floors());
    level.setBlockAndUpdate(altarPos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
    var altar = (TerraformAltarEntity) level.getBlockEntity(altarPos);
    altar.configureSize(TerraformRules.REPAIR);
    altar.configureRepairRadius(radius);
    altar.addFuel(new ItemStack(Items.COAL, 4));
    BlockPos from = new BlockPos(floor.getX() - radius - 1, floor.getY() - 20, floor.getZ() - radius - 1);
    BlockPos to = new BlockPos(floor.getX() + radius + 1, floor.getY() + 24, floor.getZ() + radius + 1);
    var before = box(level, from, to);
    for (BlockPos pos : site.pit()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    altar.start(level);
    await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE
        || altar.state() == TerraformAltarEntity.State.FAILED, 0, 3000, "Repair of untouched land", () -> {
      try {
        BlockState rock = TerraformAltarEntity.defaultRock(level);
        var changes = changed(level, before);
        changes.remove(altarPos);
        int elsewhere = 0, covered = 0;
        for (var entry : changes.entrySet()) {
          BlockPos pos = entry.getKey();
          BlockState now = entry.getValue(), was = before.get(pos), original = reference.state(pos);
          helper.assertTrue(TerraformAltarEntity.basic(now), "A repair placed something that is not plain terrain: " + entry);
          if (site.pit().contains(pos)) continue;
          boolean refill = (was.isAir() || was.canBeReplaced()) && TerraformAltarEntity.fillable(original);
          boolean cover = (was.is(Blocks.DIRT) || was.is(Blocks.COARSE_DIRT)) && original != null && now.is(original.getBlock());
          helper.assertTrue(refill || cover, "The repair changed " + pos + " from " + was + " where the generator had " + original);
          if (cover) covered++;
          else elsewhere++;
        }
        com.mojang.logging.LogUtils.getLogger().info(
            "{} repair totals filled={} covered={} refused={} skipped={} fuel={} refusals={} pit={} elsewhere={} coveredElsewhere={}",
            tag, altar.totals().filled, altar.totals().covered, altar.totals().refused, altar.totals().skippedChunks,
            altar.fuelUsed(), altar.refusals(), site.pit().size(), elsewhere, covered);
        helper.assertTrue(altar.state() == TerraformAltarEntity.State.DONE && altar.totals().skippedChunks == 0,
            "The land was not recognised: " + altar.state() + " skipped=" + altar.totals().skippedChunks
                + " refusals=" + altar.refusals());
        for (BlockPos pos : site.pit()) {
          BlockState original = reference.state(pos), now = level.getBlockState(pos);
          helper.assertTrue(now.equals(TerraformAltarEntity.fill(original, pos.getY(), rock))
              && now.is(before.get(pos).getBlock()),
              "Repaired " + pos + " with " + now + " instead of the generator's " + original);
        }
        removeAltar(level, altarPos);
        helper.succeed();
      } finally {
        release.run();
      }
    }, release);
  }

  // ---- A platform of known land over a test -----------------------------------------------------

  /**
   * A platform of stone, two dirt and grass high over a test, four chunks square around the chunk
   * corner nearest to it, on clear sky. Every chunk an altar on the corner compares is the platform, so
   * a case there sees the same land and the same chunk borders wherever it runs and whatever ran
   * before it. It owns what the case registers with {@link #onClose} (mock players, claims, listeners),
   * the platform and the forced chunks, and gives them all back once, whatever happens.
   */
  static final class Arena {
    static final int HEADROOM = 40;
    final ServerLevel level;
    final int cornerX, cornerZ, top;
    private final Runnable release;
    private final List<Runnable> closing = new ArrayList<>();
    private boolean closed;

    Arena(GameTestHelper helper) {
      level = helper.getLevel();
      BlockPos origin = helper.absolutePos(BlockPos.ZERO);
      cornerX = Math.floorDiv(origin.getX() + 8, 16) * 16;
      cornerZ = Math.floorDiv(origin.getZ() + 8, 16) * 16;
      top = Math.min(level.getMaxBuildHeight() - 64, 256);
      release = forceChunks(level, (minX() >> 4) - 1, (minZ() >> 4) - 1, (maxX() >> 4) + 1, (maxZ() >> 4) + 1);
    }

    int minX() {
      return cornerX - 32;
    }

    int maxX() {
      return cornerX + 31;
    }

    int minZ() {
      return cornerZ - 32;
    }

    int maxZ() {
      return cornerZ + 31;
    }

    /** Where an altar stands: on the platform at the chunk corner. */
    BlockPos altar() {
      return new BlockPos(cornerX, top + 1, cornerZ);
    }

    /** The pit's top row: seven blocks either side of the altar, three blocks south, across the chunk border. */
    List<BlockPos> pit() {
      List<BlockPos> pit = new ArrayList<>();
      for (int dx = -7; dx <= 7; dx++) pit.add(new BlockPos(cornerX + dx, top, cornerZ + 3));
      return pit;
    }

    /** The chunk holding the pit's eastern half, which a claim covers. */
    ChunkPos east() {
      return new ChunkPos(new BlockPos(cornerX + 7, top, cornerZ + 3));
    }

    boolean loaded() {
      return LandWorks.loaded(level, (minX() >> 4) - 1, (minZ() >> 4) - 1, (maxX() >> 4) + 1, (maxZ() >> 4) + 1);
    }

    /**
     * The platform, on clear sky; whatever an interrupted run left there is cleared first. The land under
     * it does not matter: the altars see the platform's top, and its stone holds a pit three deep.
     */
    void build() {
      clear();
      for (int x = minX(); x <= maxX(); x++)
        for (int z = minZ(); z <= maxZ(); z++) {
          level.setBlock(new BlockPos(x, top - 3, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
          level.setBlock(new BlockPos(x, top - 2, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
          level.setBlock(new BlockPos(x, top - 1, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
          level.setBlock(new BlockPos(x, top, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
      quiet();
    }

    /** The land the platform stands for, as a reference: grass at its top over dirt, stone below. */
    TerrainReference.Setup reference() {
      var settings = new FlatLevelGeneratorSettings(Optional.empty(),
          level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS), List.of());
      settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
      settings.getLayersInfo().add(new FlatLayerInfo(top - 3 - level.getMinBuildHeight(), Blocks.STONE));
      settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
      settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
      settings.updateLayers();
      return new TerrainReference.Setup(new FlatLevelSource(settings), level.getChunkSource().randomState(),
          level.getSeed(), level.registryAccess(), level.enabledFeatures(), level.getMinBuildHeight(),
          level.getHeight(), level.dimensionType(), Map.of(), Map.of(), Set.of());
    }

    /** Air from the platform's base to its headroom, top-down, without updates or drops. */
    void clear() {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int y = Math.min(level.getMaxBuildHeight() - 1, top + HEADROOM); y >= top - 3; y--)
        for (int x = minX(); x <= maxX(); x++)
          for (int z = minZ(); z <= maxZ(); z++)
            if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
              level.removeBlockEntity(pos);
              level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
      quiet();
    }

    /** No creature and no item on the platform: a mob standing on a column rightly makes an altar leave it. */
    void quiet() {
      var box = new AABB(minX(), top - 4, minZ(), maxX() + 1, top + HEADROOM + 1, maxZ() + 1);
      for (var entity : level.getEntities((Entity) null, box, entity -> !(entity instanceof Player))) entity.discard();
    }

    boolean empty() {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int y = top - 3; y <= top + HEADROOM; y++)
        for (int x = minX(); x <= maxX(); x++)
          for (int z = minZ(); z <= maxZ(); z++)
            if (!level.getBlockState(pos.set(x, y, z)).isAir()) return false;
      return true;
    }

    /** Registers something to give back when the arena closes; later registrations run first. */
    void onClose(Runnable action) {
      closing.add(0, action);
    }

    /** Runs every registered action, removes the platform and releases the chunks, once, even if one fails. */
    void close() {
      if (closed) return;
      closed = true;
      RuntimeException first = null;
      for (Runnable action : closing)
        try {
          action.run();
        } catch (RuntimeException failure) {
          if (first == null) first = failure;
        }
      try {
        clear();
      } finally {
        release.run();
      }
      if (first != null) throw first;
    }
  }
}
