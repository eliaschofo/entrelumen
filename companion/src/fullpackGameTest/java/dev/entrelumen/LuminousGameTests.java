package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for the luminous content: every generated recipe (Luminous Ingot, the nine
 * smithing upgrades and the twelve creative items) loads with the real mod items, crafts what the
 * generator wrote, keeps its output exclusive, and the Luminosities and item duplicators stay
 * uncraftable. Mekanism is reached only by reflection.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class LuminousGameTests {
  private static final String SCRIPT = "kubejs/server_scripts/entrelumen_luminous_balance.js";
  private static final List<String> MODS = List.of("kubejs", "mekanism", "ae2", "megacells", "create", "powah",
      "ars_nouveau", "evilcraft", "create_enchantment_industry", "apothic_enchanting", "draconicevolution",
      "naturesaura", "apotheosis");

  private LuminousGameTests() {}

  static void requireSuite() {
    for (String mod : MODS)
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
  }

  private static Item item(String id) {
    var key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Unregistered item " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static JsonArray constant(String script, String name) {
    var match = Pattern.compile("^const entrelumenLuminous" + name + " = (.*);$", Pattern.MULTILINE).matcher(script);
    if (!match.find()) throw new IllegalStateException("Generated script lacks " + name);
    return JsonParser.parseString(match.group(1)).getAsJsonArray();
  }

  /** The generated recipe file of a creation, read from the installed KubeJS data pack. */
  private static JsonObject recipeFile(String id) throws Exception {
    var key = ResourceLocation.parse(id);
    Path path = FMLPaths.GAMEDIR.get().resolve("kubejs/data/" + key.getNamespace() + "/recipe/" + key.getPath() + ".json");
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static String keyItem(JsonObject recipe, char symbol) {
    return recipe.getAsJsonObject("key").getAsJsonObject(String.valueOf(symbol)).get("item").getAsString();
  }

  /** The 3x3 grid a shaped creation asks for, filled with the real items. */
  private static CraftingInput grid(JsonObject recipe) {
    List<ItemStack> slots = new ArrayList<>();
    for (var row : recipe.getAsJsonArray("pattern"))
      for (char symbol : row.getAsString().toCharArray())
        slots.add(symbol == ' ' ? ItemStack.EMPTY : new ItemStack(item(keyItem(recipe, symbol))));
    return CraftingInput.of(3, 3, slots);
  }

  private static boolean exactly(Ingredient ingredient, String itemId) {
    var items = ingredient.getItems();
    return items.length == 1 && BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString().equals(itemId);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void luminousRecipesLoadWithRealItemsAndExclusiveOutputs(GameTestHelper helper) throws Exception {
    requireSuite();
    var script = Files.readString(FMLPaths.GAMEDIR.get().resolve(SCRIPT));
    var manager = helper.getLevel().getRecipeManager();
    var registries = helper.getLevel().registryAccess();
    var creations = constant(script, "Creations");
    var uncraftable = constant(script, "Uncraftable");
    Map<String, String> exclusive = new HashMap<>();
    int shaped = 0, smithing = 0;
    for (var element : creations) {
      var row = element.getAsJsonObject();
      String id = row.get("id").getAsString(), output = row.get("output").getAsString();
      RecipeHolder<?> holder = manager.byKey(ResourceLocation.parse(id)).orElseThrow(
          () -> new IllegalStateException("Luminous recipe not loaded: " + id));
      var file = recipeFile(id);
      helper.assertTrue(BuiltInRegistries.ITEM.getKey(holder.value().getResultItem(registries).getItem()).toString()
          .equals(output), "Wrong output for " + id);
      if (holder.value() instanceof ShapedRecipe recipe) {
        var ingredients = recipe.getIngredients();
        int slot = 0;
        for (var line : file.getAsJsonArray("pattern"))
          for (char symbol : line.getAsString().toCharArray())
            helper.assertTrue(exactly(ingredients.get(slot++), keyItem(file, symbol)),
                id + " slot " + (slot - 1) + " is not " + keyItem(file, symbol));
        shaped++;
      } else if (holder.value() instanceof SmithingTransformRecipe recipe) {
        helper.assertTrue(recipe.isTemplateIngredient(new ItemStack(item(file.getAsJsonObject("template").get("item").getAsString())))
            && recipe.isBaseIngredient(new ItemStack(item(file.getAsJsonObject("base").get("item").getAsString())))
            && recipe.isAdditionIngredient(new ItemStack(Luminous.INGOT.get()))
            && !recipe.isBaseIngredient(new ItemStack(item("minecraft:diamond_sword"))), "Smithing inputs differ for " + id);
        smithing++;
      } else {
        helper.fail("Unexpected recipe class for " + id + ": " + holder.value().getClass().getName());
      }
      exclusive.put(output, id);
    }
    // No other loaded recipe produces a creation's output, a Luminosity or an item duplicator.
    List<String> uncraftableIds = new ArrayList<>();
    uncraftable.forEach(e -> uncraftableIds.add(e.getAsString()));
    List<String> intruders = new ArrayList<>();
    for (RecipeHolder<?> holder : manager.getRecipes()) {
      ItemStack result;
      try {
        result = holder.value().getResultItem(registries);
      } catch (RuntimeException error) {
        continue;
      }
      if (result == null || result.isEmpty()) continue;
      String produced = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
      String owner = exclusive.get(produced);
      if ((owner != null && !owner.equals(holder.id().toString())) || uncraftableIds.contains(produced))
        intruders.add(holder.id() + " -> " + produced);
    }
    for (String id : uncraftableIds) item(id);
    helper.assertTrue(intruders.isEmpty(), "Other recipes produce exclusive luminous outputs: " + intruders);
    helper.assertTrue(shaped == 13 && smithing == 9 && uncraftableIds.size() == 13,
        "Unexpected counts " + shaped + "/" + smithing + "/" + uncraftableIds.size());
    LogUtils.getLogger().info("ENTRELUMEN_LUMINOUS_RECIPES shaped={} smithing={} uncraftable={}", shaped, smithing,
        uncraftableIds.size());
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void luminousIngotAndCreativeItemsCraftFromTheirGrids(GameTestHelper helper) throws Exception {
    requireSuite();
    var level = helper.getLevel();
    var manager = level.getRecipeManager();
    var registries = level.registryAccess();
    var script = Files.readString(FMLPaths.GAMEDIR.get().resolve(SCRIPT));
    List<String> crafted = new ArrayList<>();
    for (var element : constant(script, "Creations")) {
      var row = element.getAsJsonObject();
      if (!row.get("kind").getAsString().equals("shaped")) continue;
      String id = row.get("id").getAsString(), output = row.get("output").getAsString();
      var file = recipeFile(id);
      var input = grid(file);
      var holder = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow(
          () -> new IllegalStateException("No crafting recipe accepts the grid of " + id));
      helper.assertTrue(holder.id().toString().equals(id), "Another recipe intercepts " + id + ": " + holder.id());
      ItemStack result = holder.value().assemble(input, registries);
      helper.assertTrue(BuiltInRegistries.ITEM.getKey(result.getItem()).toString().equals(output) && result.getCount() == 1,
          "Crafting " + id + " gave " + result);
      // One missing ingredient is never accepted.
      List<ItemStack> missing = new ArrayList<>(input.items());
      missing.set(4, ItemStack.EMPTY);
      helper.assertTrue(!holder.value().matches(CraftingInput.of(3, 3, missing), level), id + " accepts a missing centre");
      if (output.equals("mekanism:creative_energy_cube")) {
        // Crafted full, exactly like Mekanism's own creative-tab cube.
        var filled = (ItemStack) Class.forName("mekanism.common.util.StorageUtils")
            .getMethod("getFilledEnergyVariant", ItemStack.class).invoke(null, new ItemStack(item(output)));
        helper.assertTrue(ItemStack.isSameItemSameComponents(result, filled),
            "Crafted creative energy cube is not full: " + result.getComponentsPatch() + " vs " + filled.getComponentsPatch());
      }
      crafted.add(id);
    }
    helper.assertTrue(crafted.size() == 13, "Crafted " + crafted.size() + " shaped creations");
    LogUtils.getLogger().info("ENTRELUMEN_LUMINOUS_CRAFTED {}", crafted);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void luminousGearTakesApotheosisAffixes(GameTestHelper helper) throws Exception {
    requireSuite();
    var forItem = Class.forName("dev.shadowsoffire.apotheosis.loot.LootCategory").getMethod("forItem", ItemStack.class);
    // Same affix category as the netherite piece it upgrades; armour, sword and pickaxe must have one.
    Map<String, String> required = Map.of("helmet", "isArmor", "chestplate", "isArmor", "leggings", "isArmor",
        "boots", "isArmor", "sword", "isMelee", "pickaxe", "isBreaker");
    List<String> categories = new ArrayList<>();
    for (String piece : List.of("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe")) {
      Object category = forItem.invoke(null, new ItemStack(item("entrelumen:luminous_" + piece)));
      Object netherite = forItem.invoke(null, new ItemStack(item("minecraft:netherite_" + piece)));
      helper.assertTrue(category.toString().equals(netherite.toString()),
          "Apotheosis files luminous " + piece + " as " + category + ", netherite as " + netherite);
      if (required.containsKey(piece))
        helper.assertTrue((boolean) category.getClass().getMethod(required.get(piece)).invoke(category)
            && !(boolean) category.getClass().getMethod("isNone").invoke(category), "No affix category for " + piece);
      categories.add(piece + "=" + category);
    }
    LogUtils.getLogger().info("ENTRELUMEN_LUMINOUS_APOTHEOSIS {}", categories);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void luminousUpgradesKeepEnchantmentsDamageAndNames(GameTestHelper helper) {
    requireSuite();
    var level = helper.getLevel();
    var manager = level.getRecipeManager();
    var registries = level.registryAccess();
    var sharpness = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    var protection = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
    String[] pieces = {"helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"};
    for (String piece : pieces) {
      var base = new ItemStack(item("minecraft:netherite_" + piece));
      base.setDamageValue(300);
      base.set(DataComponents.CUSTOM_NAME, Component.literal("Heirloom " + piece));
      base.enchant(piece.equals("helmet") || piece.equals("chestplate") || piece.equals("leggings") || piece.equals("boots")
          ? protection : sharpness, 4);
      var input = new SmithingRecipeInput(new ItemStack(item("minecraft:nether_star")), base, new ItemStack(Luminous.INGOT.get()));
      var holder = manager.getRecipeFor(RecipeType.SMITHING, input, level).orElseThrow(
          () -> new IllegalStateException("No smithing upgrade for netherite " + piece));
      helper.assertTrue(holder.id().toString().equals("entrelumen:luminous_" + piece), "Another upgrade intercepts " + piece);
      ItemStack result = holder.value().assemble(input, registries);
      helper.assertTrue(BuiltInRegistries.ITEM.getKey(result.getItem()).toString().equals("entrelumen:luminous_" + piece)
          && result.getDamageValue() == 300 && result.getEnchantments().size() == 1
          && result.getEnchantments().keySet().iterator().next().value() == base.getEnchantments().keySet().iterator().next().value()
          && result.getEnchantments().getLevel(base.getEnchantments().keySet().iterator().next()) == 4
          && Component.literal("Heirloom " + piece).equals(result.get(DataComponents.CUSTOM_NAME)),
          "Upgrade lost the base's data: " + piece + " -> " + result.getComponentsPatch());
      // Without the ingot, or with a diamond base, nothing upgrades.
      helper.assertTrue(manager.getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(
              new ItemStack(item("minecraft:nether_star")), base, new ItemStack(item("minecraft:netherite_ingot"))), level)
          .map(h -> !h.id().getNamespace().equals("entrelumen")).orElse(true), "Upgrade without the ingot: " + piece);
    }
    helper.succeed();
  }
}
