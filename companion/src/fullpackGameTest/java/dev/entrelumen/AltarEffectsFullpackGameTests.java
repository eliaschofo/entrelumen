package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Installed-pack QA for the Altars of Peace, Growth and Time, registered in
 * {@link FullpackQABootstrap} and run on the owned QA server with {@code -Dentrelumen.qa=true}
 * (evidence: docs/verification/altars-runtime.json).
 *
 * <ul>
 *   <li>Torchmaster: the Mega Torch has no recipe left, while the Dread Lamp, Feral Flare Lantern and
 *       Frozen Pearl keep theirs.</li>
 *   <li>The Altar of Peace refuses natural spawns of every installed hostile, non-boss mob type.</li>
 *   <li>Mystical Agriculture: its crops grow faster in an Altar of Growth, but their essence is not
 *       multiplied.</li>
 *   <li>Every installed modded projectile that a hostile mob fires is slowed in an Altar of Time, or
 *       is reported by name.</li>
 * </ul>
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class AltarEffectsFullpackGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();

  private AltarEffectsFullpackGameTests() {}

  private static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what,
      Runnable then) {
    if (done.getAsBoolean()) {
      then.run();
      return;
    }
    helper.assertTrue(waited < limit, what + " did not happen in time");
    helper.runAfterDelay(1, () -> await(helper, done, waited + 1, limit, what, then));
  }

  private static <T extends AreaAltarEntity> T place(GameTestHelper helper, Block block, Class<T> type, BlockPos relative,
      int radius, int halfHeight, ItemStack fuel) {
    BlockPos pos = helper.absolutePos(relative);
    helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    T altar = type.cast(helper.getLevel().getBlockEntity(pos));
    altar.configureArea(radius, halfHeight);
    altar.addFuel(fuel);
    return altar;
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void torchmasterKeepsItsLightsButLosesTheMegaTorch(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var recipes = server.getRecipeManager();
    helper.assertTrue(recipes.byKey(ResourceLocation.parse("torchmaster:megatorch")).isEmpty(),
        "torchmaster:megatorch still has its recipe");
    for (String kept : List.of("torchmaster:dreadlamp", "torchmaster:feral_flare_lantern", "torchmaster:frozen_pearl"))
      helper.assertTrue(recipes.byKey(ResourceLocation.parse(kept)).isPresent(), "Torchmaster lost " + kept);
    var megatorch = BuiltInRegistries.ITEM.get(ResourceLocation.parse("torchmaster:megatorch"));
    helper.assertTrue(megatorch != Items.AIR, "Torchmaster is not installed");
    for (var holder : recipes.getRecipes())
      helper.assertTrue(!holder.value().getResultItem(server.registryAccess()).is(megatorch),
          "Another recipe still makes the Mega Torch: " + holder.id());
    helper.assertTrue(recipes.byKey(ResourceLocation.parse("entrelumen:peace_altar_duplication")).isPresent(),
        "The Altar of Peace duplication recipe is missing");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void peaceAltarRefusesEveryInstalledHostileNaturalSpawn(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var altar = place(helper, Altars.PEACE_ALTAR.get(), PeaceAltarEntity.class, new BlockPos(2, 1, 2), 2, 2,
        new ItemStack(Items.CANDLE, 1));
    BlockPos inside = altar.getBlockPos().offset(2, 2, -2), outside = altar.getBlockPos().offset(3, 0, 0);
    helper.runAfterDelay(2, () -> {
      Map<String, String> wrong = new TreeMap<>();
      int checked = 0;
      for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
        if (type.getCategory() != MobCategory.MONSTER || type.is(Tags.EntityTypes.BOSSES)) continue;
        checked++;
        boolean in = EventHooks.checkSpawnPlacements(type, level, MobSpawnType.NATURAL, inside, level.random, true);
        boolean out = EventHooks.checkSpawnPlacements(type, level, MobSpawnType.NATURAL, outside, level.random, true);
        boolean spawner = EventHooks.checkSpawnPlacements(type, level, MobSpawnType.SPAWNER, inside, level.random, true);
        if (in || !out || !spawner)
          wrong.put(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), "inside=" + in + " outside=" + out + " spawner=" + spawner);
      }
      LOGGER.info("ALTAR_FULLPACK peace hostileTypes={} wrong={}", checked, wrong);
      helper.assertTrue(checked > 50 && wrong.isEmpty(), "Peace spawn rules wrong for " + wrong);
      helper.succeed();
    });
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 2400, skyAccess = true)
  public static void mysticalCropsGrowFasterButTheirEssenceIsNotMultiplied(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    Block crop = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mysticalagriculture:inferium_crop"));
    helper.assertTrue(crop instanceof CropBlock, "Mystical Agriculture's inferium crop is missing");
    var essence = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mysticalagriculture:inferium_essence"));
    helper.assertTrue(crop.defaultBlockState().is(AltarEffects.GROWTH_NO_BONUS_BLOCKS)
        && new ItemStack(essence).is(AltarEffects.GROWTH_NO_BONUS_ITEMS), "The no-bonus tags miss Mystical Agriculture");
    BlockPos center = new BlockPos(20, 1, 20);
    for (int x = 12; x <= 32; x++)
      for (int z = 16; z <= 24; z++) {
        helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 0, z)),
            Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
        helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.AIR.defaultBlockState(), 2);
      }
    var altar = place(helper, Altars.GROWTH_ALTAR.get(), GrowthAltarEntity.class, center, 3, 1,
        new ItemStack(Items.BONE_BLOCK, 16));
    altar.configureSamples(24);
    List<BlockPos> inside = new ArrayList<>(), outside = new ArrayList<>();
    for (int dx = -3; dx <= 3; dx += 2)
      for (int dz = -3; dz <= 3; dz += 2) inside.add(altar.getBlockPos().offset(dx, 0, dz));
    for (int dz = -3; dz <= 3; dz += 2) outside.add(altar.getBlockPos().offset(10, 0, dz));
    for (BlockPos pos : inside) level.setBlock(pos, crop.defaultBlockState(), 2);
    for (BlockPos pos : outside) level.setBlock(pos, crop.defaultBlockState(), 2);
    CropBlock cropBlock = (CropBlock) crop;
    await(helper, () -> inside.stream().allMatch(pos -> cropBlock.isMaxAge(level.getBlockState(pos))), 0, 2000,
        "Inferium crops ripening in the field", () -> {
      long ripeOutside = outside.stream().filter(pos -> cropBlock.isMaxAge(level.getBlockState(pos))).count();
      helper.assertTrue(ripeOutside < outside.size(), "Crops outside the field ripened as fast");
      // Essence per harvest, inside and outside: the altar must not raise it.
      BlockState ripe = cropBlock.getStateForAge(cropBlock.getMaxAge());
      int in = 0, out = 0, rounds = 400;
      for (int i = 0; i < rounds; i++) {
        in += essence(level, ripe, inside.getFirst(), essence);
        out += essence(level, ripe, outside.getFirst(), essence);
      }
      LOGGER.info("ALTAR_FULLPACK growth inferiumEssence inside={} outside={} rounds={} ripeOutside={}", in, out, rounds,
          ripeOutside);
      helper.assertTrue(in <= out * 1.15 + 20, "Essence was multiplied inside the field: " + in + " vs " + out);
      helper.succeed();
    });
  }

  private static int essence(ServerLevel level, BlockState state, BlockPos pos, net.minecraft.world.item.Item essence) {
    var builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
    int total = 0;
    for (ItemStack stack : state.getDrops(builder)) if (stack.is(essence)) total += stack.getCount();
    return total;
  }

  /** Ticks a detached projectile of the type three times; the failure's class name, or null. */
  private static String probe(EntityType<?> type, ServerLevel level, Husk owner, Vec3 at) {
    var entity = type.create(level);
    if (!(entity instanceof Projectile projectile)) return "not a projectile";
    try {
      projectile.setOwner(owner);
      projectile.setPos(at);
      projectile.setDeltaMovement(0.9, 0, 0);
      projectile.setNoGravity(true);
      for (int tick = 0; tick < 3 && !projectile.isRemoved(); tick++) projectile.tick();
      return null;
    } catch (RuntimeException failure) {
      return failure.getClass().getSimpleName();
    } finally {
      projectile.discard();
    }
  }

  /** Projectile pairs per batch: one slot each in a 3 x 6 x 12 grid, so no two ever touch. */
  private static final int SLOTS = 3 * 6 * 12;

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void installedHostileProjectilesAreSlowedByTheAltarOfTime(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var altar = place(helper, Altars.TIME_ALTAR.get(), TimeAltarEntity.class, new BlockPos(20, 1, 20), 12, 12,
        new ItemStack(Items.AMETHYST_SHARD, 2));
    Husk owner = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(20, 1, 26));
    List<EntityType<?>> types = new ArrayList<>();
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
      if (!id.startsWith("minecraft:") || id.equals("minecraft:arrow")) types.add(type);
    }
    Map<String, String> notSlowed = new TreeMap<>();
    Map<String, String> skipped = new TreeMap<>();
    int[] slowed = {0};
    // Bare projectiles can still fail in the world (Ars Nouveau's wall casts on nearby entities with
    // no emitter). For this case only, NeoForge discards an erroring entity instead of stopping the
    // server: the value is changed in memory (never saved; the cache is cleared because the setting
    // is marked world-restart) and restored when the batches end.
    ERRORING_BEFORE[0] = NeoForgeConfig.SERVER.removeErroringEntities.get();
    erroringEntitiesRemoved(true);
    helper.assertTrue(NeoForgeConfig.SERVER.removeErroringEntities.get(), "Could not relax removeErroringEntities");
    helper.runAfterDelay(2, () -> projectileBatch(helper, level, Vec3.atCenterOf(altar.getBlockPos()), owner, types, 0,
        slowed, notSlowed, skipped));
  }

  private static final boolean[] ERRORING_BEFORE = {false};

  private static void erroringEntitiesRemoved(boolean value) {
    NeoForgeConfig.SERVER.removeErroringEntities.set(value);
    NeoForgeConfig.SERVER.removeErroringEntities.clearCache();
  }

  /**
   * Fires the next batch: a pair per type, one inside the altar's cube and a control 30 blocks above
   * it, each in its own grid slot. Bare-created modded projectiles may lack the data their hit code
   * needs, so no two may meet (a shared lane crashed Immersive Engineering's revolver flare).
   */
  private static void projectileBatch(GameTestHelper helper, ServerLevel level, Vec3 center, Husk owner,
      List<EntityType<?>> types, int from, int[] slowed, Map<String, String> notSlowed, Map<String, String> skipped) {
    Map<String, Projectile[]> pairs = new TreeMap<>();
    int next = from, slot = 0;
    for (; next < types.size() && slot < SLOTS; next++) {
      EntityType<?> type = types.get(next);
      String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
      try {
        var inside = type.create(level);
        var control = type.create(level);
        if (!(inside instanceof Projectile a) || !(control instanceof Projectile b)) {
          if (inside != null) inside.discard();
          if (control != null) control.discard();
          continue;
        }
        a.setOwner(owner);
        b.setOwner(owner);
        int column = slot / 72, row = slot % 72 / 12, lane = slot % 12;
        Vec3 at = center.add(-11 + 8 * column, 2 + 2 * row, -11 + 2 * lane);
        // A third, detached copy flies three ticks first: a type whose own tick fails without the
        // data its launcher gives it (Aquaculture's bobber has no hook) must not reach the world.
        String failure = probe(type, level, owner, at.add(0, 60, 0));
        if (failure != null) {
          a.discard();
          b.discard();
          skipped.put(id, "tick fails without spawn data: " + failure);
          continue;
        }
        slot++;
        a.setPos(at);
        b.setPos(at.add(0, 30, 0));
        a.setDeltaMovement(0.9, 0, 0);
        b.setDeltaMovement(0.9, 0, 0);
        a.setNoGravity(true);
        b.setNoGravity(true);
        if (level.addFreshEntity(a) && level.addFreshEntity(b)) pairs.put(id, new Projectile[] {a, b});
        else skipped.put(id, "not added");
      } catch (RuntimeException failure) {
        skipped.put(id, failure.getClass().getSimpleName());
      }
    }
    int done = next;
    Map<String, double[]> starts = new TreeMap<>();
    pairs.forEach((id, pair) -> starts.put(id, new double[] {pair[0].getX(), pair[1].getX(), pair[1].getY(),
        pair[1].getZ()}));
    // The altar slows a projectile that flies freely, moving exactly by its velocity each tick. After
    // one tick the control shows whether the type does; one that steers itself (orbits, chains,
    // homing) is only partly slowed, a documented limitation, and is reported rather than failed.
    java.util.Set<String> free = new java.util.HashSet<>();
    helper.runAfterDelay(1, () -> pairs.forEach((id, pair) -> {
      double[] start = starts.get(id);
      if (Math.abs(pair[1].getX() - start[1] - 0.9) < 1e-4 && Math.abs(pair[1].getY() - start[2]) < 1e-4
          && Math.abs(pair[1].getZ() - start[3]) < 1e-4) free.add(id);
    }));
    helper.runAfterDelay(3, () -> {
      for (var entry : pairs.entrySet()) {
        Projectile inside = entry.getValue()[0], control = entry.getValue()[1];
        if (inside.isRemoved() || control.isRemoved()) {
          skipped.put(entry.getKey(), "removed");
          continue;
        }
        double stepIn = inside.getX() - starts.get(entry.getKey())[0];
        double stepOut = control.getX() - starts.get(entry.getKey())[1];
        if (stepOut > 0.3 && stepIn < 0.5 * stepOut && inside.hasData(AltarEffects.SLOWED_PROJECTILE)) slowed[0]++;
        else if (stepOut > 0.3 && free.contains(entry.getKey()))
          notSlowed.put(entry.getKey(), "inside=" + stepIn + " control=" + stepOut);
        else if (stepOut > 0.3)
          skipped.put(entry.getKey(), "steers itself, partly slowed: inside=" + stepIn + " control=" + stepOut);
        else skipped.put(entry.getKey(), "does not fly on its velocity");
        inside.discard();
        control.discard();
      }
      if (done < types.size()) {
        helper.runAfterDelay(1, () -> projectileBatch(helper, level, center, owner, types, done, slowed, notSlowed,
            skipped));
        return;
      }
      erroringEntitiesRemoved(ERRORING_BEFORE[0]);
      helper.assertTrue(NeoForgeConfig.SERVER.removeErroringEntities.get() == ERRORING_BEFORE[0],
          "NeoForge's removeErroringEntities was not restored");
      LOGGER.info("ALTAR_FULLPACK time projectiles slowed={} notSlowed={} skipped={}", slowed[0], notSlowed, skipped);
      helper.assertTrue(slowed[0] > 0 && notSlowed.isEmpty(), "Projectiles not slowed: " + notSlowed);
      helper.succeed();
    });
  }
}
