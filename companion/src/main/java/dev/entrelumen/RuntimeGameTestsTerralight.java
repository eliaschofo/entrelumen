package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for the Terralight crystal (docs/design/terra-garden.md, «La economía de las lámparas»):
 * the setup's validation, that nothing but rain speeds it up, the rain's ×8 and its window after, and the
 * harvest (one shard, only when full, then from nothing again). The fixture's {@code test_cable} stands in for
 * any mod's energy cable. Excluded from the distributable jar.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsTerralight {
  private static final ResourceLocation CABLE = ResourceLocation.fromNamespaceAndPath("entrelumen_gametest_fixture", "test_cable");
  /** The rod in the plot; its crystal's column stands east of it. */
  private static final BlockPos ROD = new BlockPos(4, 2, 5);

  private RuntimeGameTestsTerralight() {}

  private static BlockState cable() {
    return BuiltInRegistries.BLOCK.get(CABLE).defaultBlockState();
  }

  /** Column B: dirt, the rod, two cables. Column A (east): grass, air, the lamp as a block. */
  private static GroundingRodEntity build(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos rod = helper.absolutePos(ROD);
    BlockPos column = rod.east();
    level.setBlock(rod.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(rod, Terralight.ROD.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(rod.above(), cable(), Block.UPDATE_ALL);
    level.setBlock(rod.above(2), cable(), Block.UPDATE_ALL);
    level.setBlock(column.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(column, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(column.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(column.above(2), TerraGarden.LAMP_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
    if (level.getBlockEntity(rod) instanceof GroundingRodEntity entity) {
      entity.forceRain(false);
      return entity;
    }
    throw new IllegalStateException("No grounding rod entity");
  }

  private static BlockPos crystal(GameTestHelper helper) {
    return helper.absolutePos(ROD).east().above();
  }

  /** One look after {@code ticks} of loaded, continuous time (two looks a second apart each, when short). */
  private static void elapse(GroundingRodEntity rod, ServerLevel level, long ticks) {
    rod.rewind(ticks);
    rod.serverTick(level);
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 100)
  public static void terralightSetupValidatesAndGrowsOnlyWhenWhole(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var rod = build(helper);
    rod.serverTick(level);
    helper.assertTrue(rod.whole(), "The setup was not recognised");
    elapse(rod, level, 20);
    helper.assertTrue(rod.progress() == 20, "Twenty dry ticks did not count: " + rod.progress());
    helper.assertTrue(level.getBlockState(crystal(helper)).is(Terralight.CRYSTAL.get()), "No bud in the air block");
    // a cable that is not a cable, a missing lamp, the wrong soil: growth waits
    BlockPos top = helper.absolutePos(ROD).above(2);
    level.setBlock(top, Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    elapse(rod, level, 20);
    helper.assertTrue(!rod.whole() && rod.progress() == 20, "It grew without its second cable");
    level.setBlock(top, cable(), Block.UPDATE_ALL);
    BlockPos lamp = crystal(helper).above();
    level.setBlock(lamp, Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
    elapse(rod, level, 20);
    helper.assertTrue(!rod.whole() && rod.progress() == 20, "It grew under a lantern instead of a grow lamp");
    level.setBlock(lamp, TerraGarden.LAMP_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
    level.setBlock(helper.absolutePos(ROD).below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
    elapse(rod, level, 20);
    helper.assertTrue(!rod.whole() && rod.progress() == 20, "It grew with stone under the rod");
    level.setBlock(helper.absolutePos(ROD).below(), Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_ALL);
    elapse(rod, level, 20);
    helper.assertTrue(rod.whole() && rod.progress() == 40, "Rooted dirt (any #minecraft:dirt) did not count");
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 100)
  public static void terralightCannotBeAccelerated(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var rod = build(helper);
    rod.serverTick(level);
    elapse(rod, level, 20);
    long before = rod.progress();
    // an accelerator ticking the rod a hundred times in the same game tick
    for (int i = 0; i < 100; i++) rod.serverTick(level);
    helper.assertTrue(rod.progress() == before, "Extra ticks in one game tick made it grow");
    // random ticks and bone meal on the crystal
    BlockPos spot = crystal(helper);
    BlockState bud = level.getBlockState(spot);
    helper.assertTrue(!bud.isRandomlyTicking(), "The crystal takes random ticks");
    for (int i = 0; i < 50; i++) bud.randomTick(level, spot, level.random);
    helper.assertTrue(level.getBlockState(spot).equals(bud), "Random ticks changed the crystal");
    helper.assertTrue(!BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL, 64), level, spot), "Bone meal grew the crystal");
    helper.assertTrue(level.getBlockState(spot).equals(bud) && rod.progress() == before, "Bone meal changed the crystal");
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 100)
  public static void terralightGrowsEightTimesAsFastInTheRainAndAWhileAfter(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var rod = build(helper);
    rod.serverTick(level);
    elapse(rod, level, 20);
    long dry = rod.progress();
    rod.forceRain(true);
    elapse(rod, level, 20);
    helper.assertTrue(rod.progress() == dry + 20L * TerralightRules.RAIN_MULTIPLIER, "Rain did not count eight times: " + rod.progress());
    long rained = rod.progress();
    rod.forceRain(false);
    elapse(rod, level, 20);
    helper.assertTrue(rod.progress() == rained + 20L * TerralightRules.RAIN_MULTIPLIER, "Right after the rain it grew dry");
    rod.forgetRain();
    long after = rod.progress();
    elapse(rod, level, 20);
    helper.assertTrue(rod.progress() == after + 20, "Long after the rain it still grew fast");
    // a long gap (an unloaded chunk) counts dry even in the rain
    rod.forceRain(true);
    long before = rod.progress();
    elapse(rod, level, 1_000);
    helper.assertTrue(rod.progress() == before + 1_000, "A long gap counted as rain: " + (rod.progress() - before));
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 100)
  public static void terralightGivesOneShardOnlyWhenFullAndStartsAgain(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var rod = build(helper);
    rod.serverTick(level);
    BlockPos spot = crystal(helper);
    var silk = new ItemStack(Items.DIAMOND_PICKAXE);
    silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
    var fortune = new ItemStack(Items.DIAMOND_PICKAXE);
    fortune.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
    // half grown: nothing
    elapse(rod, level, Terralight.fullGrowthTicks() / 2);
    BlockState half = level.getBlockState(spot);
    helper.assertTrue(half.is(Terralight.CRYSTAL.get()) && half.getValue(Terralight.CrystalBlock.STAGE) == 2, "Half grown is not the large bud: " + half);
    helper.assertTrue(Block.getDrops(half, level, spot, null, null, fortune).isEmpty(), "A half-grown crystal dropped something");
    // full: exactly one shard, whatever the tool
    elapse(rod, level, Terralight.fullGrowthTicks());
    BlockState full = level.getBlockState(spot);
    helper.assertTrue(full.getValue(Terralight.CrystalBlock.STAGE) == 3, "Not full after the whole time: " + full);
    for (ItemStack tool : new ItemStack[] {ItemStack.EMPTY, silk, fortune}) {
      var drops = Block.getDrops(full, level, spot, null, null, tool);
      helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Terralight.SHARD.get()) && drops.getFirst().getCount() == 1,
          "A full crystal gave " + drops + " with " + tool);
    }
    // mined: it starts again from nothing
    level.destroyBlock(spot, false);
    elapse(rod, level, 20);
    helper.assertTrue(rod.progress() == 20 && level.getBlockState(spot).getValue(Terralight.CrystalBlock.STAGE) == 0,
        "After the harvest it did not start again: " + rod.progress());
    helper.succeed();
  }
}
