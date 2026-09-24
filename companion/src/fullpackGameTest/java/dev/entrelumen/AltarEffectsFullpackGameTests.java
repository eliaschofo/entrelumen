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
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Installed-pack QA for the Altars of Peace, Growth and Time, PENDING INTEGRATION: written and
 * compiled with the QA source set, not registered in {@link FullpackQABootstrap} and never run.
 * The root integrator registers this class there (registration and expected-name list) and runs it
 * on the owned QA server with {@code -Dentrelumen.qa=true}.
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

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void installedHostileProjectilesAreSlowedByTheAltarOfTime(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var altar = place(helper, Altars.TIME_ALTAR.get(), TimeAltarEntity.class, new BlockPos(20, 1, 20), 12, 12,
        new ItemStack(Items.AMETHYST_SHARD, 2));
    Husk owner = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(20, 1, 26));
    Vec3 base = Vec3.atCenterOf(altar.getBlockPos()).add(-8, 9, 0);
    Map<String, Projectile[]> pairs = new TreeMap<>();
    Map<String, String> skipped = new TreeMap<>();
    helper.runAfterDelay(2, () -> {
      int lane = 0;
      for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (id.startsWith("minecraft:") && !id.equals("minecraft:arrow")) continue;
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
          double z = -10 + (lane++ % 20);
          a.setPos(base.add(0, 0, z));
          b.setPos(base.add(0, 30, z));
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
      Map<String, double[]> starts = new TreeMap<>();
      pairs.forEach((id, pair) -> starts.put(id, new double[] {pair[0].getX(), pair[1].getX()}));
      helper.runAfterDelay(3, () -> {
        Map<String, String> notSlowed = new TreeMap<>();
        int slowed = 0;
        for (var entry : pairs.entrySet()) {
          Projectile inside = entry.getValue()[0], control = entry.getValue()[1];
          if (inside.isRemoved() || control.isRemoved()) {
            skipped.put(entry.getKey(), "removed");
            continue;
          }
          double stepIn = inside.getX() - starts.get(entry.getKey())[0];
          double stepOut = control.getX() - starts.get(entry.getKey())[1];
          if (stepOut > 0.3 && stepIn < 0.5 * stepOut && inside.hasData(AltarEffects.SLOWED_PROJECTILE)) slowed++;
          else if (stepOut > 0.3) notSlowed.put(entry.getKey(), "inside=" + stepIn + " control=" + stepOut);
          else skipped.put(entry.getKey(), "does not fly on its velocity");
          inside.discard();
          control.discard();
        }
        LOGGER.info("ALTAR_FULLPACK time projectiles slowed={} notSlowed={} skipped={}", slowed, notSlowed, skipped);
        helper.assertTrue(slowed > 0 && notSlowed.isEmpty(), "Projectiles not slowed: " + notSlowed);
        helper.succeed();
      });
    });
  }
}
