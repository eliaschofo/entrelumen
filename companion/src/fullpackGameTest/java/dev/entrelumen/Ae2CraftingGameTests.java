package dev.entrelumen;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-JAR-only, full-pack AE2 execution through actual blocks and the grid crafting service. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class Ae2CraftingGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ResourceLocation RECIPE = ResourceLocation.parse(
      "entrelumen:integration/settlement_supply");
  private static final ResourceLocation CONTRACT = ResourceLocation.parse("entrelumen:habitation_contract");
  private static final ResourceLocation STEW = ResourceLocation.parse("farmersdelight:fish_stew");
  private static final ResourceLocation SALAD = ResourceLocation.parse("farmersdelight:mixed_salad");
  private static final ResourceLocation PROCESSOR = ResourceLocation.parse("ae2:calculation_processor");
  private static final ResourceLocation RATION = ResourceLocation.parse("entrelumen:ration_bundle");
  private static final int[] INPUT_COUNTS = {2, 2, 1, 2};
  private static final ResourceLocation[] INPUT_IDS = {STEW, SALAD, PROCESSOR, RATION};

  private Ae2CraftingGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void ae2SettlementSupplyCraftsTwiceAndReturnsAllBowls(GameTestHelper helper) {
    for (String mod : List.of("ae2", "farmersdelight", "entrelumen"))
      helper.assertTrue(ModList.get().isLoaded(mod), "Required real mod is absent: " + mod);

    var level = helper.getLevel();
    Item[] inputs = Arrays.stream(INPUT_IDS).map(id -> item(helper, id)).toArray(Item[]::new);
    Item contract = item(helper, CONTRACT);
    var gridItems = new ItemStack[9];
    Arrays.fill(gridItems, ItemStack.EMPTY);
    int slot = 0;
    for (int i = 0; i < inputs.length; i++)
      for (int n = 0; n < INPUT_COUNTS[i]; n++) gridItems[slot++] = new ItemStack(inputs[i]);

    var loaded = level.getRecipeManager().byKey(RECIPE);
    helper.assertTrue(loaded.isPresent() && loaded.orElseThrow().value() instanceof ShapelessRecipe,
        "Loaded settlement_supply is missing or no longer a native shapeless recipe");
    CraftingRecipe recipe = (CraftingRecipe) loaded.orElseThrow().value();
    helper.assertTrue(recipe.matches(CraftingInput.of(3, 3, Arrays.asList(gridItems)), level),
        "The seven real settlement_supply inputs no longer match the loaded recipe");
    ItemStack declaredOutput = recipe.getResultItem(level.registryAccess());
    helper.assertTrue(declaredOutput.is(contract) && declaredOutput.getCount() == 1,
        "The loaded settlement_supply recipe no longer declares one habitation_contract");
    // AE2's encoder needs the recipe's declared output. The test never crafts or inserts it.
    ItemStack pattern = PatternDetailsHelper.encodeCraftingPattern(
        new RecipeHolder<>(RECIPE, recipe), gridItems, declaredOutput, false, false);
    helper.assertTrue(PatternDetailsHelper.decodePattern(pattern, level) != null,
        "AE2 could not decode the native settlement_supply crafting pattern");

    // The provider defaults to ALL faces. Its down face reaches the assembler; its other
    // faces join a real ME drive, a two-block crafting CPU, and a finite energy cell.
    BlockPos providerPos = helper.absolutePos(new BlockPos(2, 2, 2));
    BlockPos assemblerPos = helper.absolutePos(new BlockPos(2, 1, 2));
    BlockPos drivePos = helper.absolutePos(new BlockPos(1, 2, 2));
    BlockPos energyPos = helper.absolutePos(new BlockPos(3, 2, 2));
    BlockPos storagePos = helper.absolutePos(new BlockPos(2, 2, 1));
    BlockPos unitPos = helper.absolutePos(new BlockPos(2, 2, 0));
    level.setBlockAndUpdate(providerPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
    level.setBlockAndUpdate(assemblerPos, AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
    level.setBlockAndUpdate(drivePos, AEBlocks.DRIVE.block().defaultBlockState());
    level.setBlockAndUpdate(energyPos, AEBlocks.ENERGY_CELL.block().defaultBlockState());
    level.setBlockAndUpdate(storagePos, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
    level.setBlockAndUpdate(unitPos, AEBlocks.CRAFTING_UNIT.block().defaultBlockState());

    var provider = blockEntity(helper, providerPos, PatternProviderBlockEntity.class);
    var assembler = blockEntity(helper, assemblerPos, MolecularAssemblerBlockEntity.class);
    var drive = blockEntity(helper, drivePos, DriveBlockEntity.class);
    var energy = blockEntity(helper, energyPos, EnergyCellBlockEntity.class);
    var cpuBlock = blockEntity(helper, storagePos, CraftingBlockEntity.class);
    helper.assertTrue(drive.getInternalInventory()
            .insertItem(0, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
        "The real ME drive rejected its 1K item storage cell");
    helper.assertTrue(provider.getLogic().getPatternInv().insertItem(0, pattern, false).isEmpty(),
        "The real pattern provider rejected the encoded settlement_supply pattern");
    provider.getLogic().updatePatterns();
    energy.injectAEPower(energy.getAEMaxPower(), Actionable.MODULATE);
    helper.assertTrue(energy.getAECurrentPower() > 1000,
        "The finite AE2 energy cell did not charge");

    var network = new Network(provider, assembler, drive, energy, cpuBlock,
        Arrays.stream(inputs).map(AEItemKey::of).toArray(AEItemKey[]::new),
        AEItemKey.of(contract), AEItemKey.of(Items.BOWL));
    awaitGrid(helper, network, 0);
  }

  private static void awaitGrid(GameTestHelper helper, Network net, int waited) {
    IGrid grid = net.provider.getMainNode().getGrid();
    boolean ready = grid != null
        && net.provider.getMainNode().isActive()
        && net.assembler.getMainNode().isActive()
        && net.drive.getMainNode().isActive()
        && net.cpuBlock.getMainNode().isActive()
        && net.drive.getMainNode().getGrid() == grid
        && net.assembler.getMainNode().getGrid() == grid
        && net.cpuBlock.getMainNode().getGrid() == grid
        && net.energy.getMainNode().getGrid() == grid
        && grid.getEnergyService().isNetworkPowered()
        && net.drive.getCellInventory(0) != null
        && !grid.getCraftingService().getCpus().isEmpty()
        && !grid.getCraftingService().getCraftingFor(net.contract).isEmpty();
    if (!ready) {
      helper.assertTrue(waited < 200,
          "Physical AE2 grid, CPU, ME storage, energy, or provider pattern failed to become ready"
              + " after " + waited + " ticks");
      helper.runAfterDelay(5, () -> awaitGrid(helper, net, waited + 5));
      return;
    }
    helper.assertTrue(net.provider.getTargets().contains(net.provider.getBlockPos().getY()
        > net.assembler.getBlockPos().getY() ? Direction.DOWN : Direction.UP),
        "Provider does not target its adjacent assembler");
    insertInputs(helper, net, 1);
    awaitIndexedInputs(helper, net, 1, 0);
  }

  private static void insertInputs(GameTestHelper helper, Network net, int cycle) {
    var store = net.grid().getStorageService().getInventory();
    for (int i = 0; i < net.inputs.length; i++) {
      long accepted = store.insert(net.inputs[i], INPUT_COUNTS[i], Actionable.MODULATE,
          net.source());
      helper.assertTrue(accepted == INPUT_COUNTS[i],
          "ME item cell accepted only " + accepted + " of " + INPUT_COUNTS[i]
              + " native input " + INPUT_IDS[i] + " on cycle " + cycle);
    }
    for (int i = 0; i < net.inputs.length; i++)
      helper.assertTrue(amount(net, net.inputs[i]) == INPUT_COUNTS[i],
          "Seeded ME inventory does not contain exact native input " + INPUT_IDS[i]);
  }

  private static void calculate(GameTestHelper helper, Network net, int cycle) {
    var service = net.grid().getCraftingService();
    IActionSource source = net.source();
    helper.assertTrue(source.machine().orElseThrow().getActionableNode()
            == net.provider.getMainNode().getNode(),
        "AE2 crafting simulation requester has no provider grid node");
    Future<ICraftingPlan> future = service.beginCraftingCalculation(helper.getLevel(),
        () -> source, net.contract, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
    awaitPlan(helper, net, cycle, future, 0);
  }

  private static void awaitIndexedInputs(GameTestHelper helper, Network net, int cycle, int waited) {
    // AE2 plans from the storage service's tick-refreshed index for machine requests.
    // Inserting into real ME storage and planning on that same tick sees an older snapshot.
    KeyCounter indexed = net.grid().getStorageService().getCachedInventory();
    boolean ready = true;
    for (int i = 0; i < net.inputs.length; i++)
      ready &= indexed.get(net.inputs[i]) == INPUT_COUNTS[i];
    if (!ready) {
      helper.assertTrue(waited < 100,
          "AE2 storage index did not observe real seeded inputs on cycle " + cycle
              + " after " + waited + " ticks: indexed=" + describe(indexed)
              + " physical=" + describe(net.grid().getStorageService().getInventory()
                  .getAvailableStacks()));
      helper.runAfterDelay(2, () -> awaitIndexedInputs(helper, net, cycle, waited + 2));
      return;
    }
    calculate(helper, net, cycle);
  }

  private static void awaitPlan(GameTestHelper helper, Network net, int cycle,
      Future<ICraftingPlan> future, int waited) {
    if (!future.isDone()) {
      helper.assertTrue(waited < 200, "AE2 crafting calculation did not finish on cycle " + cycle);
      helper.runAfterDelay(5, () -> awaitPlan(helper, net, cycle, future, waited + 5));
      return;
    }
    ICraftingPlan plan;
    try {
      plan = future.get();
    } catch (Exception e) {
      helper.fail("AE2 crafting calculation failed on cycle " + cycle + ": " + e);
      return;
    }
    LOGGER.info("ENTRELUMEN_AE2_PLAN cycle={} simulation={} missing={} finalOutput={} used={}",
        cycle, plan.simulation(), describe(plan.missingItems()), plan.finalOutput(),
        describe(plan.usedItems()));
    helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty()
            && plan.finalOutput().what().equals(net.contract)
            && plan.finalOutput().amount() == 1,
        "AE2 could not plan exactly one real habitation_contract on cycle " + cycle
            + ": simulation=" + plan.simulation() + " missing=" + describe(plan.missingItems())
            + " finalOutput=" + plan.finalOutput());
    helper.assertTrue(plan.patternTimes().size() == 1
            && plan.patternTimes().values().iterator().next() == 1L,
        "AE2 did not select exactly one settlement_supply crafting operation");
    for (int i = 0; i < net.inputs.length; i++)
      helper.assertTrue(plan.usedItems().get(net.inputs[i]) == INPUT_COUNTS[i],
          "AE2 plan did not reserve exact native input " + INPUT_IDS[i]);
    var result = net.grid().getCraftingService().submitJob(plan, null, null, false,
        net.source());
    helper.assertTrue(result.successful(),
        "AE2 rejected the real crafting job on cycle " + cycle + ": " + result.errorCode());
    awaitOutput(helper, net, cycle, 0);
  }

  private static void awaitOutput(GameTestHelper helper, Network net, int cycle, int waited) {
    long contracts = amount(net, net.contract);
    long bowls = amount(net, net.bowl);
    helper.assertTrue(contracts <= cycle && bowls <= 4L * cycle,
        "AE2 duplicated settlement_supply output or bowl remainders on cycle " + cycle
            + ": contracts=" + contracts + " bowls=" + bowls);
    boolean ingredientsGone = true;
    for (AEItemKey input : net.inputs) ingredientsGone &= amount(net, input) == 0;
    boolean buffersEmpty = net.provider.getLogic().getReturnInv().isEmpty()
        && net.assembler.getInternalInventory().isEmpty()
        && net.cpuBlock.getCluster().craftingLogic.getInventory().list.isEmpty();
    boolean cpuIdle = net.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy());
    boolean noDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
        new AABB(net.provider.getBlockPos()).inflate(4)).isEmpty();
    if (contracts != cycle || bowls != 4L * cycle || !ingredientsGone || !buffersEmpty
        || !cpuIdle || !noDrops) {
      helper.assertTrue(waited < 300,
          "AE2 crafting failed exact native conservation on cycle " + cycle
              + " after " + waited + " ticks: contracts=" + contracts + " bowls=" + bowls
              + " inputsGone=" + ingredientsGone + " buffersEmpty=" + buffersEmpty
              + " cpuIdle=" + cpuIdle + " noDrops=" + noDrops);
      helper.runAfterDelay(5, () -> awaitOutput(helper, net, cycle, waited + 5));
      return;
    }
    helper.assertTrue(net.energy.getAECurrentPower() > 0,
        "Finite cell exhausted before completing AE2 crafting cycle " + cycle);
    LOGGER.info("ENTRELUMEN_AE2_CRAFT cycle={} contracts={} bowls={} inputsGone={} buffersEmpty={}"
            + " cpuIdle={} remainingAE={}",
        cycle, contracts, bowls, ingredientsGone, buffersEmpty, cpuIdle,
        net.energy.getAECurrentPower());
    if (cycle == 1) {
      insertInputs(helper, net, 2);
      awaitIndexedInputs(helper, net, 2, 0);
    } else {
      helper.succeed();
    }
  }

  private static long amount(Network net, AEItemKey key) {
    return net.grid().getStorageService().getInventory().getAvailableStacks().get(key);
  }

  private static String describe(KeyCounter stacks) {
    return stacks.keySet().stream().map(key -> key + "x" + stacks.get(key))
        .sorted().toList().toString();
  }

  private static Item item(GameTestHelper helper, ResourceLocation id) {
    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id)
        && BuiltInRegistries.ITEM.get(id) != Items.AIR, "Required real item is absent: " + id);
    return BuiltInRegistries.ITEM.get(id);
  }

  private static <T> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
    Object entity = helper.getLevel().getBlockEntity(pos);
    helper.assertTrue(type.isInstance(entity), "Missing native AE2 block entity " + type.getSimpleName());
    return type.cast(entity);
  }

  private record Network(PatternProviderBlockEntity provider,
                         MolecularAssemblerBlockEntity assembler,
                         DriveBlockEntity drive,
                         EnergyCellBlockEntity energy,
                         CraftingBlockEntity cpuBlock,
                         AEItemKey[] inputs,
                         AEItemKey contract,
                         AEItemKey bowl) {
    IGrid grid() {
      return provider.getMainNode().getGrid();
    }

    IActionSource source() {
      // AE2 discovers available patterns from the requester's machine grid node.
      return IActionSource.ofMachine(provider);
    }
  }
}
