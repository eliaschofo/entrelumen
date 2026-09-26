package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Installed-pack QA of the recipe audit of 25 September 2026 (docs/design/recipe-design-rules.md), which
 * applies Elias's playtest of 24 September: the integration recipes are symmetric drawings equal to the
 * Ark deliveries, each component closes few loaded recipes, Mekanism's alloys and circuits come only
 * from Mekanism, and Emperor's Cloth keeps working in the grid while the viewers hide it.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RecipeDesignFullpackGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** The act components and story items of content/integration-design.json; goal: at most eight recipes. */
  static final List<String> COMPONENTS = List.of("raw_lens", "survey_notes", "signal_core", "calibration_frame",
      "energy_coupler", "living_matrix", "ration_bundle", "routing_matrix", "propagation_core", "power_regulator",
      "inventory_sensor", "handling_core", "spectral_lens", "horizon_chart", "ecosystem_capsule",
      "containment_seal", "ark_bus", "renewal_engine", "habitation_contract");
  static final int GOAL = 8;
  static final Map<String, String> MODULES = Map.of(
      "entrelumen:integration/ark_engineering", "engineering_module",
      "entrelumen:integration/ark_arcana", "arcane_module",
      "entrelumen:integration/ark_nature", "nature_module",
      "entrelumen:integration/ark_exploration", "exploration_module",
      "entrelumen:integration/ark_logistics", "logistics_module",
      "entrelumen:integration/ark_habitation", "habitation_module");

  private RecipeDesignFullpackGameTests() {}

  static void requireSuite() {
    for (String mod : List.of("kubejs", "mekanism", "oritech", "twilightforest", "ae2", "farmersdelight"))
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Fullpack QA cannot run against the synthetic fixture");
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
        .orElseThrow(() -> new IllegalStateException("Unregistered item " + id));
  }

  /** What an ingredient accepts, as sorted item IDs, so two cells can be compared. */
  private static List<String> kind(Ingredient ingredient) {
    return Arrays.stream(ingredient.getItems()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
        .sorted().distinct().toList();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void integrationDrawingsAreSymmetricAndMatchTheirDeliveries(GameTestHelper helper) {
    requireSuite();
    List<String> problems = new ArrayList<>();
    int drawings = 0;
    for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
      String id = holder.id().toString();
      if (!id.startsWith("entrelumen:integration/") || id.equals("entrelumen:integration/precision_bench")) continue;
      if (!(holder.value() instanceof ShapedRecipe shaped)) {
        problems.add(id + " is not a shaped recipe");
        continue;
      }
      drawings++;
      int width = shaped.getWidth(), height = shaped.getHeight();
      var cells = shaped.getIngredients();
      for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++)
          if (!kind(cells.get(y * width + x)).equals(kind(cells.get(y * width + width - 1 - x))))
            problems.add(id + " is not symmetric at row " + y);
      String module = MODULES.get(id);
      if (module != null) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Ingredient cell : cells)
          if (!cell.isEmpty()) counts.merge(kind(cell).getFirst(), 1, Integer::sum);
        var project = Projects.all().get(module);
        if (project == null || !new TreeMap<>(project.items()).equals(counts))
          problems.add(id + " grid " + counts + " differs from the " + module + " delivery");
      }
    }
    helper.assertTrue(drawings == 21, "Expected 21 drawn integration recipes, found " + drawings);
    LOGGER.info("ENTRELUMEN_RECIPE_DESIGN drawings={} problems={}", drawings, problems);
    helper.assertTrue(problems.isEmpty(), "Integration drawings: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void componentsCloseFewLoadedRecipes(GameTestHelper helper) {
    requireSuite();
    Map<String, ItemStack> stacks = new LinkedHashMap<>();
    for (String name : COMPONENTS) stacks.put(name, new ItemStack(item("entrelumen:" + name)));
    Map<String, List<String>> uses = new LinkedHashMap<>();
    for (String name : COMPONENTS) uses.put(name, new ArrayList<>());
    for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
      List<Ingredient> ingredients;
      try {
        ingredients = holder.value().getIngredients();
      } catch (RuntimeException error) {
        continue;
      }
      for (var entry : stacks.entrySet())
        if (ingredients.stream().anyMatch(i -> !i.isEmpty() && i.test(entry.getValue())))
          uses.get(entry.getKey()).add(holder.id().toString());
    }
    List<String> problems = new ArrayList<>();
    uses.forEach((name, ids) -> {
      if (ids.size() > GOAL) problems.add(name + " closes " + ids.size() + " loaded recipes: " + ids);
    });
    Map<String, Integer> sizes = new LinkedHashMap<>();
    uses.forEach((name, ids) -> sizes.put(name, ids.size()));
    LOGGER.info("ENTRELUMEN_RECIPE_FANOUT {}", sizes);
    helper.assertTrue(problems.isEmpty(), "Component fan-out: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void mekanismAlloysAndCircuitsComeOnlyFromMekanism(GameTestHelper helper) {
    requireSuite();
    var registries = helper.getLevel().registryAccess();
    List<String> guarded = List.of("mekanism:alloy_infused", "mekanism:alloy_reinforced", "mekanism:alloy_atomic",
        "mekanism:basic_control_circuit", "mekanism:advanced_control_circuit", "mekanism:elite_control_circuit",
        "mekanism:ultimate_control_circuit");
    List<String> intruders = new ArrayList<>();
    for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
      ItemStack made;
      try {
        made = holder.value().getResultItem(registries);
      } catch (RuntimeException error) {
        continue;
      }
      if (made.isEmpty()) continue;
      String output = BuiltInRegistries.ITEM.getKey(made.getItem()).toString();
      if (guarded.contains(output) && !holder.id().getNamespace().equals("mekanism"))
        intruders.add(holder.id() + " -> " + output);
    }
    for (String removed : List.of("oritech:foundry/alloy/compat/mekanism/infused_alloy",
        "oritech:atomicforge/compat/mekanism/basic_control_circuit"))
      if (helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(removed)).isPresent())
        intruders.add(removed + " is still loaded");
    helper.assertTrue(intruders.isEmpty(), "Mekanism entry bypassed: " + intruders);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void emperorsClothStillWorksWhileTheViewersHideIt(GameTestHelper helper) throws Exception {
    requireSuite();
    var level = helper.getLevel();
    var manager = level.getRecipeManager();
    var cloth = new ItemStack(item("twilightforest:emperors_cloth"));
    var armour = new ItemStack(Items.IRON_CHESTPLATE);
    var marker = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.parse("twilightforest:emperors_cloth"))
        .orElseThrow(() -> new IllegalStateException("Twilight Forest lacks its emperors_cloth component"));
    // The crafting recipe (a special recipe, no JSON ingredients) still dyes armour invisible in the grid.
    var input = CraftingInput.of(2, 1, List.of(armour.copy(), cloth.copy()));
    var crafted = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow(
        () -> new IllegalStateException("No crafting recipe takes armour and Emperor's Cloth"));
    helper.assertTrue(crafted.id().toString().equals("twilightforest:emperors_cloth_recipe"),
        "Armour and cloth matched " + crafted.id());
    var result = crafted.value().assemble(input, level.registryAccess());
    helper.assertTrue(result.is(Items.IRON_CHESTPLATE) && result.has(marker), "Crafted armour lacks the cloth");
    // The template-free smithing recipe still takes any applicable armour.
    var smithing = manager.byKey(ResourceLocation.parse("twilightforest:emperors_cloth_smithing")).orElseThrow(
        () -> new IllegalStateException("Emperor's Cloth smithing recipe is not loaded"));
    helper.assertTrue(smithing.value() instanceof SmithingRecipe recipe
        && recipe.matches(new SmithingRecipeInput(ItemStack.EMPTY, armour.copy(), cloth.copy()), level),
        "Emperor's Cloth smithing no longer takes armour");
    // The pack ships the viewer filters with those IDs (EMI reads the emi namespace, JEI the client script).
    var game = FMLPaths.GAMEDIR.get();
    var filter = JsonParser.parseString(Files.readString(
        game.resolve("kubejs/assets/emi/recipe/filters/entrelumen_hidden_filler.json"))).getAsJsonObject();
    List<String> hidden = new ArrayList<>();
    filter.getAsJsonArray("filters").forEach(f -> hidden.add(f.getAsJsonObject().get("id").getAsString()));
    helper.assertTrue(hidden.containsAll(List.of("twilightforest:/emperors_cloth_recipe",
        "twilightforest:emperors_cloth_smithing")), "EMI filter lacks Emperor's Cloth: " + hidden);
    String client = Files.readString(game.resolve("kubejs/client_scripts/entrelumen_recipe_viewer.js"));
    helper.assertTrue(client.contains("'twilightforest:emperors_cloth_recipe'")
        && client.contains("'twilightforest:emperors_cloth_smithing'"), "JEI client script lacks Emperor's Cloth");
    LOGGER.info("ENTRELUMEN_EMPERORS_CLOTH crafting={} smithing={} emiFilter={} jeiScript=present",
        crafted.id(), smithing.id(), hidden);
    helper.succeed();
  }
}
