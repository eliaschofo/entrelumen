package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Fullpack-only checks against the installed Macaw recipes, blocks and loot tables. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class SettlementArchitectureGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String BASE = "mcwwindows:window_base";
  private static final String WINDOW = "mcwwindows:oak_window";
  private static final String SHUTTER = "mcwwindows:oak_shutter";
  private static final String FENCE = "mcwfences:oak_picket_fence";
  private static final String GATE = "mcwfences:oak_curved_gate";
  private static final String GARDEN_LIGHT = "mcwlights:thin_garden_light";
  private static final String FAN_LIGHT = "mcwlights:oak_ceiling_fan_light";

  private record RecipeCase(String id, int width, int height, int count, int requiredSlot,
      String... slots) {}

  private static final RecipeCase BASE_RECIPE = new RecipeCase(BASE, 3, 3, 4, 4,
      "minecraft:stick", "minecraft:stick", "minecraft:stick",
      "minecraft:stick", "minecraft:glass", "minecraft:stick",
      "minecraft:stick", "minecraft:stick", "minecraft:stick");
  private static final RecipeCase WINDOW_RECIPE = new RecipeCase(WINDOW, 3, 3, 4, 4,
      "_", BASE, "_", BASE, "minecraft:oak_log", BASE, "_", BASE, "_");
  private static final RecipeCase SHUTTER_RECIPE = new RecipeCase(SHUTTER, 1, 3, 3, 1,
      "minecraft:oak_trapdoor", "minecraft:oak_trapdoor", "minecraft:oak_trapdoor");
  private static final RecipeCase FENCE_RECIPE = new RecipeCase(FENCE, 3, 2, 3, 1,
      "minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_log",
      "minecraft:oak_log", "minecraft:stick", "minecraft:oak_log");
  private static final RecipeCase GATE_RECIPE = new RecipeCase(GATE, 3, 2, 4, 0,
      "minecraft:oak_log", "_", "minecraft:oak_planks",
      "minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_planks");
  private static final RecipeCase GARDEN_LIGHT_RECIPE = new RecipeCase(GARDEN_LIGHT, 3, 3, 1, 4,
      "_", "minecraft:iron_nugget", "_",
      "_", "minecraft:glowstone_dust", "_",
      "_", "minecraft:iron_ingot", "_");
  private static final RecipeCase FAN_LIGHT_RECIPE = new RecipeCase(FAN_LIGHT, 3, 3, 1, 4,
      "_", "minecraft:iron_nugget", "_",
      "minecraft:iron_ingot", "minecraft:oak_slab", "minecraft:iron_ingot",
      "_", "minecraft:glowstone_dust", "_");

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void nativeMacawRecipesKeepActualIngredientsAndYields(GameTestHelper helper) {
    requireNativeMods();
    ItemStack baseSupply = craft(helper, BASE_RECIPE, grid(BASE_RECIPE));
    List<ItemStack> windowGrid = grid(WINDOW_RECIPE);
    for (int slot : new int[] {1, 3, 5, 7}) windowGrid.set(slot, baseSupply.split(1));
    helper.assertTrue(baseSupply.isEmpty(), "One native window-base craft no longer supplies four windows");
    craft(helper, WINDOW_RECIPE, windowGrid);
    for (RecipeCase row : List.of(SHUTTER_RECIPE, FENCE_RECIPE, GATE_RECIPE,
        GARDEN_LIGHT_RECIPE, FAN_LIGHT_RECIPE)) craft(helper, row, grid(row));
    LOGGER.info("ENTRELUMEN_SETTLEMENT_NATIVE_RECIPES {}",
        List.of(BASE, WINDOW, SHUTTER, FENCE, GATE, GARDEN_LIGHT, FAN_LIGHT));
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void nativeShutterGateAndGardenLightWorkWhenPlaced(GameTestHelper helper) {
    requireNativeMods();
    var level = helper.getLevel();
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    BlockPos shutterPos = helper.absolutePos(new BlockPos(1, 1, 1));
    BlockPos gatePos = helper.absolutePos(new BlockPos(3, 1, 1));
    BlockPos lightPos = helper.absolutePos(new BlockPos(1, 1, 3));
    for (BlockPos pos : List.of(shutterPos, gatePos, lightPos))
      level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());

    ItemStack shutters = craft(helper, SHUTTER_RECIPE, grid(SHUTTER_RECIPE));
    place(helper, player, shutters, shutterPos.below(), shutterPos, SHUTTER);
    helper.assertTrue(shutters.getCount() == 2, "Native shutter placement did not consume one item");
    BlockState closedShutter = level.getBlockState(shutterPos);
    helper.assertTrue(!closedShutter.getValue(BlockStateProperties.OPEN),
        "Placed shutter did not start closed");
    assertThinCollision(helper, closedShutter.getCollisionShape(level, shutterPos), "closed shutter");
    interact(helper, player, shutterPos);
    BlockState openShutter = level.getBlockState(shutterPos);
    helper.assertTrue(openShutter.getValue(BlockStateProperties.OPEN),
        "Native shutter interaction did not open it");
    // In the pinned Windows JAR, OPEN drives the model; both states keep a thin native collision.
    assertThinCollision(helper, openShutter.getCollisionShape(level, shutterPos), "open shutter");
    assertOneDrop(helper, shutterPos, SHUTTER);

    ItemStack gates = craft(helper, GATE_RECIPE, grid(GATE_RECIPE));
    place(helper, player, gates, gatePos.below(), gatePos, GATE);
    helper.assertTrue(level.getBlockState(gatePos.above()).isAir() && gates.getCount() == 3,
        "First native gate item created a free second piece");
    place(helper, player, gates, gatePos, gatePos.above(), GATE);
    helper.assertTrue(gates.getCount() == 2, "Stacked native gate did not cost two items");
    BlockState bottom = level.getBlockState(gatePos);
    BlockState top = level.getBlockState(gatePos.above());
    helper.assertTrue("bottom".equals(propertyValue(bottom, "fencepart"))
        && "top".equals(propertyValue(top, "fencepart")),
        "Native neighbor update failed to form bottom/top gate pieces");
    helper.assertTrue(!bottom.getCollisionShape(level, gatePos).isEmpty()
        && !top.getCollisionShape(level, gatePos.above()).isEmpty(),
        "Closed gate lost native collision");
    interact(helper, player, gatePos);
    bottom = level.getBlockState(gatePos);
    top = level.getBlockState(gatePos.above());
    helper.assertTrue(bottom.getValue(BlockStateProperties.OPEN)
        && top.getValue(BlockStateProperties.OPEN)
        && bottom.getCollisionShape(level, gatePos).isEmpty()
        && top.getCollisionShape(level, gatePos.above()).isEmpty(),
        "Native gate interaction failed to open both pieces and clear passage");
    var dropArea = new net.minecraft.world.phys.AABB(gatePos).inflate(3);
    int beforeDrops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, dropArea)
        .stream().filter(entity -> entity.getItem().is(item(GATE))).mapToInt(entity -> entity.getItem().getCount()).sum();
    helper.assertTrue(level.destroyBlock(gatePos, true, player), "Bottom gate did not break");
    if (!level.getBlockState(gatePos.above()).isAir()) level.destroyBlock(gatePos.above(), true, player);
    int afterDrops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, dropArea)
        .stream().filter(entity -> entity.getItem().is(item(GATE))).mapToInt(entity -> entity.getItem().getCount()).sum();
    helper.assertTrue(level.getBlockState(gatePos).isAir() && level.getBlockState(gatePos.above()).isAir()
        && afterDrops - beforeDrops == 2, "Breaking two paid gate pieces did not spawn exactly two items");

    ItemStack lights = craft(helper, GARDEN_LIGHT_RECIPE, grid(GARDEN_LIGHT_RECIPE));
    place(helper, player, lights, lightPos.below(), lightPos, GARDEN_LIGHT);
    helper.assertTrue(lights.isEmpty(), "Garden-light placement did not consume its sole item");
    BlockState lit = level.getBlockState(lightPos);
    helper.assertTrue(lit.getValue(BlockStateProperties.LIT) && lit.getLightEmission() == 15,
        "Placed native garden light does not emit level 15");
    interact(helper, player, lightPos);
    BlockState unlit = level.getBlockState(lightPos);
    helper.assertTrue(!unlit.getValue(BlockStateProperties.LIT) && unlit.getLightEmission() == 0,
        "Native light interaction did not extinguish emission");
    assertOneDrop(helper, lightPos, GARDEN_LIGHT);
    LOGGER.info("ENTRELUMEN_SETTLEMENT_NATIVE_BLOCKS shutter=open gate=bottom+top drops=2 light=15_to_0");
    helper.succeed();
  }

  private static void requireNativeMods() {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Fullpack QA cannot run against the synthetic fixture");
    for (String mod : List.of("mcwwindows", "mcwfences", "mcwlights"))
      if (!ModList.get().isLoaded(mod))
        throw new IllegalStateException("Required real settlement mod is absent: " + mod);
  }

  private static ItemStack craft(GameTestHelper helper, RecipeCase row, List<ItemStack> grid) {
    helper.assertTrue(grid.size() == row.width() * row.height(), "Invalid QA grid: " + row.id());
    var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(row.id()));
    helper.assertTrue(holder.isPresent(), "Loaded native recipe is missing: " + row.id());
    if (!(holder.orElseThrow().value() instanceof CraftingRecipe recipe))
      throw new IllegalStateException("Loaded native recipe is not crafting: " + row.id());
    String serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
    helper.assertTrue("minecraft:crafting_shaped".equals(serializer),
        "Native shaped recipe was replaced: " + row.id() + " -> " + serializer);
    CraftingInput input = CraftingInput.of(row.width(), row.height(), grid);
    helper.assertTrue(recipe.matches(input, helper.getLevel()),
        "Loaded native recipe rejects its real inputs: " + row.id());
    helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(
        net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, helper.getLevel())
        .orElseThrow().id().equals(holder.orElseThrow().id()), "Another recipe intercepts " + row.id());
    assertOutput(helper, row, recipe.getResultItem(helper.getLevel().registryAccess()));
    ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
    assertOutput(helper, row, output);
    var remainders = recipe.getRemainingItems(input);
    helper.assertTrue(remainders.size() == input.size()
        && remainders.stream().allMatch(ItemStack::isEmpty),
        "Native recipe gained or lost container remainders: " + row.id());
    List<ItemStack> missingCost = new ArrayList<>();
    for (ItemStack stack : grid) missingCost.add(stack.copy());
    helper.assertTrue(!missingCost.get(row.requiredSlot()).isEmpty(),
        "QA required slot is empty: " + row.id());
    missingCost.set(row.requiredSlot(), ItemStack.EMPTY);
    helper.assertTrue(!recipe.matches(CraftingInput.of(row.width(), row.height(), missingCost),
        helper.getLevel()), "Native recipe bypasses a required ingredient: " + row.id());
    return output;
  }

  private static List<ItemStack> grid(RecipeCase row) {
    List<ItemStack> result = new ArrayList<>();
    for (String slot : row.slots())
      result.add("_".equals(slot) ? ItemStack.EMPTY : new ItemStack(item(slot)));
    return result;
  }

  private static void assertOutput(GameTestHelper helper, RecipeCase row, ItemStack stack) {
    String actual = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    helper.assertTrue(actual.equals(row.id()) && stack.getCount() == row.count(),
        row.id() + " produced " + actual + " x" + stack.getCount()
            + " instead of x" + row.count());
  }

  private static void place(GameTestHelper helper, Player player, ItemStack stack,
      BlockPos clicked, BlockPos target, String id) {
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    int before = stack.getCount();
    InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
        hit(clicked, Direction.UP)));
    helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(target).is(block(id))
        && stack.getCount() == before - 1,
        "Native BlockItem failed to place exactly one " + id + ": " + result);
  }

  private static void interact(GameTestHelper helper, Player player, BlockPos pos) {
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    BlockState state = helper.getLevel().getBlockState(pos);
    ItemInteractionResult result = state.useItemOn(ItemStack.EMPTY, helper.getLevel(), player,
        InteractionHand.MAIN_HAND, hit(pos, Direction.UP));
    helper.assertTrue(result.consumesAction(),
        "Native block interaction was not accepted at " + pos + ": " + result);
  }

  private static BlockHitResult hit(BlockPos pos, Direction face) {
    return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0,
        pos.getZ() + 0.5), face, pos, false);
  }

  private static int assertOneDrop(GameTestHelper helper, BlockPos pos, String id) {
    BlockState state = helper.getLevel().getBlockState(pos);
    var drops = Block.getDrops(state, helper.getLevel(), pos,
        helper.getLevel().getBlockEntity(pos), null, new ItemStack(Items.IRON_PICKAXE));
    helper.assertTrue(drops.size() == 1 && drops.getFirst().is(item(id))
        && drops.getFirst().getCount() == 1,
        "Native loot table must return exactly one " + id + " at " + pos + ": " + drops);
    return drops.getFirst().getCount();
  }

  private static void assertThinCollision(GameTestHelper helper, VoxelShape shape, String part) {
    helper.assertTrue(!shape.isEmpty(), "Native " + part + " lost collision");
    var bounds = shape.bounds();
    double volume = bounds.getXsize() * bounds.getYsize() * bounds.getZsize();
    helper.assertTrue(volume > 0 && volume < 0.25,
        "Native " + part + " collision is no longer a thin panel: " + bounds);
  }

  private static String propertyValue(BlockState state, String name) {
    Property<?> property = state.getProperties().stream()
        .filter(value -> value.getName().equals(name)).findFirst()
        .orElseThrow(() -> new IllegalStateException("Native state lacks " + name));
    return ((StringRepresentable) state.getValue(property)).getSerializedName();
  }

  private static Block block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Required real Macaw block is absent: " + id);
    Block block = BuiltInRegistries.BLOCK.get(key);
    if (block == Blocks.AIR)
      throw new IllegalStateException("Required real Macaw block is air: " + id);
    return block;
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Required real Macaw item is absent: " + id);
    Item item = BuiltInRegistries.ITEM.get(key);
    if (item == Items.AIR)
      throw new IllegalStateException("Required real Macaw item is air: " + id);
    return item;
  }
}
