package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** QA-JAR-only checks against loaded building recipes, blocks and Rechiseled's native API. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class BuilderUtilitiesGameTests {
  private record Craft(String id, String result, int count, int width, int height,
      String serializer, String... slots) {}

  private static final List<Craft> CRAFTS = List.of(
      new Craft("measurements:tape_measure", "measurements:tape_measure", 1, 3, 3,
          "minecraft:crafting_shaped",
          "_", "minecraft:gray_wool", "_",
          "minecraft:gray_wool", "minecraft:iron_ingot", "minecraft:yellow_wool",
          "_", "minecraft:gray_wool", "minecraft:yellow_wool"),
      new Craft("rechiseledcreate:mechanical_chisel", "rechiseledcreate:mechanical_chisel",
          1, 2, 1, "minecraft:crafting_shapeless",
          "create:andesite_casing", "rechiseled:chisel"),
      new Craft("glassential:glass_redstone", "glassential:glass_redstone", 8, 3, 3,
          "minecraft:crafting_shaped",
          "minecraft:glass", "minecraft:glass", "minecraft:glass",
          "minecraft:glass", "minecraft:redstone_block", "minecraft:glass",
          "minecraft:glass", "minecraft:glass", "minecraft:glass"),
      new Craft("glassential:glass_light", "glassential:glass_light", 8, 3, 3,
          "minecraft:crafting_shaped",
          "minecraft:glass", "minecraft:glass", "minecraft:glass",
          "minecraft:glass", "minecraft:glowstone", "minecraft:glass",
          "minecraft:glass", "minecraft:glass", "minecraft:glass"),
      new Craft("glassential:glass_ghostly", "glassential:glass_ghostly", 4, 3, 2,
          "minecraft:crafting_shapeless",
          "minecraft:glass", "minecraft:glass", "minecraft:glass",
          "minecraft:glass", "minecraft:ender_pearl", "_"),
      new Craft("simplylight:edge_light", "simplylight:edge_light", 6, 3, 3,
          "minecraft:crafting_shaped",
          "minecraft:stone", "_", "minecraft:stone",
          "minecraft:glowstone", "minecraft:glowstone", "minecraft:glowstone",
          "minecraft:stone", "_", "minecraft:stone"),
      new Craft("simplylight:illuminant_block_on", "simplylight:illuminant_block_on", 4, 3, 3,
          "minecraft:crafting_shaped",
          "minecraft:stone", "minecraft:glowstone", "minecraft:stone",
          "minecraft:glowstone", "minecraft:redstone_torch", "minecraft:glowstone",
          "minecraft:stone", "minecraft:glowstone", "minecraft:stone"),
      new Craft("simplylight:illuminant_block_on_toggle", "simplylight:illuminant_block_on",
          1, 1, 1, "minecraft:crafting_shapeless", "simplylight:illuminant_block"));

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void nativeBuilderCraftsKeepCostsAndOutputs(GameTestHelper helper) {
    requireNativeMods();
    List<String> checked = new ArrayList<>();
    for (Craft row : CRAFTS) {
      helper.assertTrue(row.slots().length == row.width() * row.height(),
          "Invalid builder QA grid: " + row.id());
      ResourceLocation id = ResourceLocation.parse(row.id());
      var manager = helper.getLevel().getRecipeManager();
      var holder = manager.byKey(id);
      helper.assertTrue(holder.isPresent() && holder.orElseThrow().value() instanceof CraftingRecipe,
          "Loaded native crafting recipe missing: " + id);
      CraftingRecipe recipe = (CraftingRecipe) holder.orElseThrow().value();
      ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
      helper.assertTrue(serializer.equals(ResourceLocation.parse(row.serializer())),
          "Unexpected native serializer for " + id + ": " + serializer);
      List<ItemStack> slots = new ArrayList<>();
      for (String itemId : row.slots())
        slots.add("_".equals(itemId) ? ItemStack.EMPTY : new ItemStack(item(itemId)));
      CraftingInput input = CraftingInput.of(row.width(), row.height(), slots);
      helper.assertTrue(recipe.matches(input, helper.getLevel()),
          "Native builder recipe rejected its required inputs: " + id);
      helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
          .orElseThrow().id().equals(id), "Another recipe intercepts " + id);
      assertOutput(helper, row, recipe.getResultItem(helper.getLevel().registryAccess()));
      assertOutput(helper, row, recipe.assemble(input, helper.getLevel().registryAccess()));
      List<ItemStack> missing = new ArrayList<>(slots);
      for (int i = 0; i < missing.size(); i++) {
        if (!missing.get(i).isEmpty()) {
          missing.set(i, ItemStack.EMPTY);
          break;
        }
      }
      helper.assertTrue(!recipe.matches(CraftingInput.of(row.width(), row.height(), missing),
          helper.getLevel()), "Builder recipe accepts a missing input: " + id);
      checked.add(row.id());
    }
    LogUtils.getLogger().info("ENTRELUMEN_BUILDER_CRAFTS {}", checked);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void functionalGlassAndSwitchedLightsUseNativeBlockBehavior(GameTestHelper helper) {
    requireNativeMods();
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    BlockPos signal = pos.east();
    level.setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());

    level.setBlockAndUpdate(pos, block("glassential:glass_redstone").defaultBlockState());
    var redstone = level.getBlockState(pos);
    helper.assertTrue(redstone.isSignalSource()
        && redstone.getSignal(level, pos, Direction.NORTH) == 15,
        "Loaded redstone glass does not supply signal 15");

    level.setBlockAndUpdate(pos, block("glassential:glass_light").defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15,
        "Loaded luminous glass does not emit level 15");

    level.setBlockAndUpdate(pos, block("glassential:glass_ghostly").defaultBlockState());
    var ghost = level.getBlockState(pos);
    ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
        new ItemStack(Items.STONE));
    helper.assertTrue(ghost.getCollisionShape(level, pos, CollisionContext.of(entity)).isEmpty(),
        "Ghostly glass still collides with a real entity");
    helper.assertTrue(!ghost.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty(),
        "Ghostly glass unexpectedly lost non-entity selection shape");

    level.setBlockAndUpdate(pos, block("simplylight:illuminant_block_on").defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15,
        "Inverted illuminant block should start lit");
    level.setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0,
        "Inverted illuminant block did not extinguish under redstone signal");
    level.setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15,
        "Inverted illuminant block did not relight after signal removal");

    level.setBlockAndUpdate(pos, block("simplylight:illuminant_block").defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0,
        "Ordinary illuminant block should start unlit");
    level.setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 15,
        "Ordinary illuminant block did not light under redstone signal");
    level.setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
    helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0,
        "Ordinary illuminant block stayed lit after signal removal");
    LogUtils.getLogger().info("ENTRELUMEN_BUILDER_BLOCKS redstone=15 glassLight=15 ghostEntityPass=true lightSwitch=both");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void chippedVariantsJoinLoadedRechiseledConversionGroup(GameTestHelper helper)
      throws Exception {
    requireNativeMods();
    Item carved = item("chipped:carved_stone");
    Class<?> managerApi = Class.forName(
        "com.supermartijn642.rechiseled.api.chiseling.ChiselingRecipeManager");
    Class<?> recipeApi = Class.forName(
        "com.supermartijn642.rechiseled.api.chiseling.ChiselingRecipe");
    Class<?> entryApi = Class.forName(
        "com.supermartijn642.rechiseled.api.chiseling.ChiselingEntry");
    Class<?> worthApi = Class.forName(
        "com.supermartijn642.rechiseled.api.chiseling.ItemWithWorth");
    Object manager = managerApi.getMethod("get", LevelReader.class)
        .invoke(null, helper.getLevel());
    helper.assertTrue(manager != null, "Loaded Rechiseled chiseling manager is absent");
    Object recipe = managerApi.getMethod("getRecipeForItem", ItemLike.class)
        .invoke(manager, carved);
    helper.assertTrue(recipe != null, "Chipped carved stone has no loaded chiseling group");
    helper.assertTrue((boolean) recipeApi.getMethod("contains", ItemLike.class)
        .invoke(recipe, Items.STONE), "Chipped variant is not grouped with its native stone base");
    boolean ownedVariant = false;
    @SuppressWarnings("unchecked")
    List<Object> entries = (List<Object>) recipeApi.getMethod("entries").invoke(recipe);
    for (Object entry : entries) {
      ResourceLocation owner = (ResourceLocation) entryApi.getMethod("owner").invoke(entry);
      ResourceLocation sourceRecipe = (ResourceLocation) entryApi.getMethod("recipe").invoke(entry);
      if (!owner.equals(ResourceLocation.parse("rechiseled:datapacks"))
          || !sourceRecipe.equals(ResourceLocation.parse("rechiseled_chipped:stone"))) continue;
      Object withWorth = entryApi.getMethod("getAnyItem").invoke(entry);
      if (withWorth == null) continue;
      Item entryItem = (Item) worthApi.getMethod("item").invoke(withWorth);
      if (entryItem == carved) {
        ownedVariant = true;
        break;
      }
    }
    helper.assertTrue(ownedVariant,
        "Carved stone was not registered by the real Rechiseled: Chipped bridge");

    Method getWorth = recipeApi.getMethod("getWorth", ItemLike.class);
    Object source = getWorth.invoke(recipe, Items.STONE);
    Object target = getWorth.invoke(recipe, carved);
    helper.assertTrue(source != null && target != null,
        "Loaded chisel group lacks source or target worth");
    Class<?> conversionApi = Class.forName(
        "com.supermartijn642.rechiseled.api.chiseling.conversion.ChiselingConversionHelper");
    Object result = conversionApi.getMethod("convert", int.class, worthApi, worthApi)
        .invoke(null, 1, source, target);
    Class<?> resultApi = Class.forName(
        "com.supermartijn642.rechiseled.api.chiseling.conversion.ConversionResult");
    int converted = (int) resultApi.getMethod("numberOfConversions").invoke(result);
    int leftover = (int) resultApi.getMethod("leftover").invoke(result);
    int produced = (int) resultApi.getMethod("result").invoke(result);
    helper.assertTrue(converted == 1 && leftover == 0 && produced == 1,
        "Native stone-to-Chipped conversion is not one-for-one: "
            + converted + "/" + leftover + "/" + produced);
    LogUtils.getLogger().info("ENTRELUMEN_BUILDER_CHIPPED group=stone target=chipped:carved_stone conversion=1:1");
    helper.succeed();
  }

  private static void requireNativeMods() {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Builder QA requires the real full pack, not the fixture");
    for (String mod : List.of("measurements", "rechiseledcreate", "rechiseled_chipped",
        "glassential", "simplylight", "rechiseled", "chipped", "create"))
      if (!ModList.get().isLoaded(mod))
        throw new IllegalStateException("Required real builder mod is absent: " + mod);
  }

  private static void assertOutput(GameTestHelper helper, Craft row, ItemStack output) {
    ResourceLocation actual = BuiltInRegistries.ITEM.getKey(output.getItem());
    helper.assertTrue(actual.equals(ResourceLocation.parse(row.result()))
        && output.getCount() == row.count(),
        row.id() + " produced " + actual + " x" + output.getCount()
            + " instead of " + row.result() + " x" + row.count());
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key) || BuiltInRegistries.ITEM.get(key) == Items.AIR)
      throw new IllegalStateException("Required native builder item is absent: " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static Block block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key) || BuiltInRegistries.BLOCK.get(key) == Blocks.AIR)
      throw new IllegalStateException("Required native builder block is absent: " + id);
    return BuiltInRegistries.BLOCK.get(key);
  }
}
