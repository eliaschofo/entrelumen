package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-JAR-only checks of the loaded McJty recipes and native configured-card component copy. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RFToolsRecipeGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String QUARRY = "rftoolsbuilder:shape_card_quarry";

  /** changedSlots are the cells the pack gates; `former` is what they held natively (none: a native recipe). */
  private record RecipeCase(String id, int[] changedSlots, String former, String... slots) {}

  private static final String ALLOY = "mekanism:alloy_reinforced";
  private static final String IRONWOOD = "twilightforest:ironwood_ingot";

  // Elias's playtest of 24 September 2026 (docs/design/recipe-design-rules.md): the handling core closes
  // the builder, a milestone; the other gates take their act's material on the drawing's axis or in a
  // mirrored pair; the charged porter and the XNet routers stay native under their gated keystones.
  private static final List<RecipeCase> CASES = List.of(
      new RecipeCase("rftoolsbuilder:builder", new int[] {7}, "minecraft:redstone",
          "minecraft:bricks", "minecraft:ender_pearl", "minecraft:bricks",
          "minecraft:redstone", "rftoolsbase:machine_frame", "minecraft:redstone",
          "minecraft:bricks", "entrelumen:handling_core", "minecraft:bricks"),
      new RecipeCase(QUARRY, new int[] {0, 2}, "minecraft:redstone",
          IRONWOOD, "minecraft:diamond_pickaxe", IRONWOOD,
          "minecraft:iron_ingot", "rftoolsbuilder:shape_card_def", "minecraft:iron_ingot",
          "minecraft:redstone", "minecraft:diamond_shovel", "minecraft:redstone"),
      new RecipeCase("rftoolsutility:spawner", new int[] {6}, "minecraft:redstone",
          "minecraft:redstone", "minecraft:rotten_flesh", "minecraft:redstone",
          "minecraft:ender_pearl", "rftoolsbase:machine_frame", "minecraft:blaze_rod",
          IRONWOOD, "minecraft:bone", "minecraft:redstone"),
      new RecipeCase("rftoolsutility:matter_receiver", new int[] {1}, "minecraft:iron_ingot",
          "minecraft:iron_ingot", ALLOY, "minecraft:iron_ingot",
          "minecraft:redstone", "rftoolsbase:machine_frame", "minecraft:redstone",
          "minecraft:ender_pearl", "minecraft:ender_pearl", "minecraft:ender_pearl"),
      new RecipeCase("rftoolsutility:charged_porter", new int[] {}, "_",
          "_", "minecraft:ender_pearl", "_",
          "minecraft:ender_pearl", "minecraft:redstone_block", "minecraft:ender_pearl",
          "minecraft:iron_ingot", "minecraft:ender_pearl", "minecraft:iron_ingot"),
      new RecipeCase("rftoolsutility:environmental_controller", new int[] {8}, "minecraft:ender_pearl",
          "minecraft:ender_pearl", "minecraft:diamond_block", "minecraft:ender_pearl",
          "minecraft:gold_block", "rftoolsbase:machine_frame", "minecraft:iron_block",
          "minecraft:ender_pearl", "minecraft:emerald_block", ALLOY),
      new RecipeCase("rftoolspower:dimensionalcell_simple", new int[] {7}, "minecraft:diamond",
          "minecraft:redstone_block", "minecraft:diamond", "minecraft:redstone_block",
          "minecraft:quartz", "rftoolsbase:machine_frame", "minecraft:quartz",
          "minecraft:redstone_block", ALLOY, "minecraft:redstone_block"),
      new RecipeCase("rftoolspower:dimensionalcell", new int[] {6, 8}, "minecraft:redstone_block",
          "minecraft:redstone_block", "minecraft:diamond", "minecraft:redstone_block",
          "minecraft:prismarine_shard", "rftoolsbase:machine_frame", "minecraft:prismarine_shard",
          ALLOY, "minecraft:emerald", ALLOY),
      new RecipeCase("xnet:controller", new int[] {7}, "minecraft:gold_ingot",
          "minecraft:repeater", "minecraft:comparator", "minecraft:repeater",
          "minecraft:redstone", "rftoolsbase:machine_frame", "minecraft:redstone",
          "minecraft:iron_ingot", ALLOY, "minecraft:iron_ingot"),
      new RecipeCase("xnet:router", new int[] {}, "_",
          "minecraft:powered_rail", "minecraft:comparator", "minecraft:powered_rail",
          "minecraft:redstone", "rftoolsbase:machine_frame", "minecraft:redstone",
          "minecraft:iron_ingot", "minecraft:ender_pearl", "minecraft:iron_ingot"),
      new RecipeCase("xnet:wireless_router", new int[] {}, "_",
          "minecraft:ender_pearl", "minecraft:comparator", "minecraft:ender_pearl",
          "minecraft:redstone", "rftoolsbase:machine_frame", "minecraft:redstone",
          "minecraft:ender_pearl", "minecraft:redstone", "minecraft:ender_pearl"));

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void nativeRecipesRequireIntegrationComponents(GameTestHelper helper) {
    requireNativeMods();
    List<String> observed = new ArrayList<>();
    for (RecipeCase row : CASES) {
      helper.assertTrue(row.slots().length == 9, "Invalid QA grid: " + row.id());
      CraftingRecipe recipe = recipe(helper, row.id());
      String serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
      String expectedSerializer = row.id().equals(QUARRY)
          ? "mcjtylib:copy_components" : "minecraft:crafting_shaped";
      helper.assertTrue(serializer.equals(expectedSerializer),
          row.id() + " serializer changed: " + serializer);
      List<ItemStack> grid = grid(row);
      CraftingInput input = CraftingInput.of(3, 3, grid);
      helper.assertTrue(recipe.matches(input, helper.getLevel()),
          "Loaded recipe rejects real integration input: " + row.id());
      assertResult(helper, row.id(), recipe.getResultItem(helper.getLevel().registryAccess()));
      assertResult(helper, row.id(), recipe.assemble(input, helper.getLevel().registryAccess()));
      var remainders = recipe.getRemainingItems(input);
      helper.assertTrue(remainders.size() == grid.size()
          && remainders.stream().allMatch(ItemStack::isEmpty),
          "Native crafting remainders changed for " + row.id());
      if (row.changedSlots().length > 0) {
        List<ItemStack> former = copy(grid);
        for (int slot : row.changedSlots()) former.set(slot, stack(row.former()));
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, former), helper.getLevel()),
            "Former recipe still bypasses the act gate: " + row.id());
      }
      observed.add(row.id());
    }
    LOGGER.info("ENTRELUMEN_RFTOOLS_NATIVE_RECIPES {}", observed);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void configuredQuarryCardPreservesNativeShapeData(GameTestHelper helper)
      throws Exception {
    requireNativeMods();
    CraftingRecipe recipe = recipe(helper, QUARRY);
    String serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
    helper.assertTrue(serializer.equals("mcjtylib:copy_components"),
        "Configured quarry card lost its native copy-components serializer: " + serializer);

    ItemStack source = new ItemStack(item("rftoolsbuilder:shape_card_def"));
    Class<?> shapeCard = Class.forName("mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem");
    helper.assertTrue(shapeCard.isInstance(source.getItem()), "Native shape card item type changed");
    shapeCard.getMethod("setDimension", ItemStack.class, int.class, int.class, int.class)
        .invoke(null, source, 17, 19, 23);
    shapeCard.getMethod("setOffset", ItemStack.class, int.class, int.class, int.class)
        .invoke(null, source, 3, 4, 5);
    shapeCard.getMethod("setTagMatching", ItemStack.class, boolean.class)
        .invoke(null, source, true);
    Method dimension = shapeCard.getMethod("getDimension", ItemStack.class);
    Method offset = shapeCard.getMethod("getOffset", ItemStack.class);
    Method tagMatching = shapeCard.getMethod("isTagMatching", ItemStack.class);
    helper.assertTrue(new BlockPos(17, 19, 23).equals(dimension.invoke(null, source))
        && new BlockPos(3, 4, 5).equals(offset.invoke(null, source))
        && Boolean.TRUE.equals(tagMatching.invoke(null, source)),
        "Native configuration API did not set the test shape");

    @SuppressWarnings("unchecked")
    Collection<DataComponentType<?>> preserved = (Collection<DataComponentType<?>>)
        shapeCard.getMethod("getComponentsToPreserve").invoke(source.getItem());
    helper.assertTrue(!preserved.isEmpty(), "Native shape card no longer declares preserved components");
    RecipeCase quarry = CASES.stream().filter(row -> row.id().equals(QUARRY)).findFirst().orElseThrow();
    List<ItemStack> grid = grid(quarry);
    grid.set(4, source);
    CraftingInput input = CraftingInput.of(3, 3, grid);
    helper.assertTrue(recipe.matches(input, helper.getLevel()),
        "Configured native source card is rejected by the loaded quarry recipe");
    ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
    assertResult(helper, QUARRY, output);
    for (DataComponentType<?> type : preserved) {
      Object before = source.get(type);
      helper.assertTrue(before != null && Objects.equals(before, output.get(type)),
          "Quarry result lost native component " + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
    }
    helper.assertTrue(Objects.equals(dimension.invoke(null, source), dimension.invoke(null, output))
        && Objects.equals(offset.invoke(null, source), offset.invoke(null, output))
        && Objects.equals(tagMatching.invoke(null, source), tagMatching.invoke(null, output)),
        "Quarry result changed native shape settings");
    var remainders = recipe.getRemainingItems(input);
    helper.assertTrue(remainders.size() == 9 && remainders.stream().allMatch(ItemStack::isEmpty),
        "Configured quarry recipe has unexpected remainders");
    LOGGER.info("ENTRELUMEN_RFTOOLS_CONFIGURED_QUARRY preservedComponents={}", preserved.size());
    helper.succeed();
  }

  private static void requireNativeMods() {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Fullpack QA cannot run against the synthetic fixture");
    for (String mod : List.of("mcjtylib", "rftoolsbase", "rftoolsbuilder", "rftoolsutility",
        "rftoolspower", "xnet"))
      if (!ModList.get().isLoaded(mod))
        throw new IllegalStateException("Required real McJty mod is absent: " + mod);
  }

  private static CraftingRecipe recipe(GameTestHelper helper, String id) {
    var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id));
    helper.assertTrue(holder.isPresent(), "Loaded native recipe is missing: " + id);
    var raw = holder.orElseThrow().value();
    if (!(raw instanceof CraftingRecipe crafting))
      throw new IllegalStateException("Loaded recipe is not a crafting recipe: " + id);
    return crafting;
  }

  private static void assertResult(GameTestHelper helper, String id, ItemStack output) {
    String actual = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
    helper.assertTrue(actual.equals(id) && output.getCount() == 1,
        id + " produced " + actual + " x" + output.getCount() + " instead of " + id + " x1");
  }

  private static List<ItemStack> grid(RecipeCase row) {
    List<ItemStack> result = new ArrayList<>();
    for (String slot : row.slots()) result.add(stack(slot));
    return result;
  }

  private static List<ItemStack> copy(List<ItemStack> input) {
    List<ItemStack> result = new ArrayList<>();
    for (ItemStack stack : input) result.add(stack.copy());
    return result;
  }

  private static ItemStack stack(String id) {
    return "_".equals(id) ? ItemStack.EMPTY : new ItemStack(item(id));
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Required real McJty or integration item is absent: " + id);
    Item item = BuiltInRegistries.ITEM.get(key);
    if (item == Items.AIR)
      throw new IllegalStateException("Required real McJty or integration item is air: " + id);
    return item;
  }
}
