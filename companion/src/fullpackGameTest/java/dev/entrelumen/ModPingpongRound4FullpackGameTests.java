package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;

/**
 * QA-JAR-only checks of round 4 of the mod ping-pong (docs/design/mod-pingpong.md): the batch is
 * loaded with the Dragons Plus build Central Kitchen needs, Create: New Age and Psi key pieces carry
 * their act components in the loaded recipe manager, both Patchouli guide books stay craftable and
 * Dungeons and Taverns registers its structures. entrelumen_pingpong4_balance.js checks the same
 * stages at load. The Create kitchen keeps one route per task: Central Kitchen automates the real
 * cooking pot and Slice & Dice's slicer does the cutting board, so Slice & Dice's basin cooking and
 * Central Kitchen's cutting-board saw and deployer conversions are off in the pack config.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ModPingpongRound4FullpackGameTests {
  static final List<String> BATCH = List.of("psi", "create_new_age", "create_central_kitchen", "sliceanddice",
      "mr_dungeons_andtaverns", "kotlinforforge");
  /** Recipe ID and the act component it must consume (see tools/generate_family_balance.py, family pingpong4). */
  static final Map<String, String> STAGED = new LinkedHashMap<>();
  static {
    STAGED.put("create_new_age:shaped/basic_solar_heating_plate", "entrelumen:calibration_frame");
    STAGED.put("create_new_age:shaped/generator_coil", "entrelumen:energy_coupler");
    STAGED.put("create_new_age:shaped/advanced_solar_heating_plate", "entrelumen:power_regulator");
    STAGED.put("create_new_age:shaped/advanced_energiser", "entrelumen:power_regulator");
    STAGED.put("create_new_age:shaped/advanced_motor", "entrelumen:power_regulator");
    STAGED.put("create_new_age:shaped/reinforced_energiser", "entrelumen:spectral_lens");
    STAGED.put("create_new_age:mechanical_crafting/reinforced_motor", "entrelumen:spectral_lens");
    STAGED.put("create_new_age:mechanical_crafting/reactor_rod", "entrelumen:containment_seal");
    STAGED.put("psi:assembler", "entrelumen:calibration_frame");
    STAGED.put("psi:cad_core_hyperclocked", "entrelumen:routing_matrix");
    STAGED.put("psi:cad_core_radiative", "entrelumen:routing_matrix");
  }
  static final List<String> STRUCTURES = List.of("nova_structures:tavern_oak", "nova_structures:illager_manor",
      "nova_structures:piglin_outstation", "nova_structures:end_castle");

  private static final Logger LOGGER = LogUtils.getLogger();

  private ModPingpongRound4FullpackGameTests() {}

  private static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound4BatchLoaded(GameTestHelper helper) {
    List<String> missing = BATCH.stream().filter(mod -> !ModList.get().isLoaded(mod)).toList();
    helper.assertTrue(missing.isEmpty(), "Round 4 batch missing: " + missing);
    String dragons = ModList.get().getModContainerById("create_dragons_plus")
        .map(container -> container.getModInfo().getVersion().toString()).orElse("0");
    helper.assertTrue(new DefaultArtifactVersion(dragons).compareTo(new DefaultArtifactVersion("1.11.9")) >= 0,
        "Central Kitchen needs Create: Dragons Plus 1.11.9 or newer, found " + dragons);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void heliodorSolarAndPsiPiecesNeedActComponents(GameTestHelper helper) {
    var recipes = helper.getLevel().getRecipeManager();
    List<String> problems = new ArrayList<>();
    STAGED.forEach((recipe, component) -> {
      var holder = recipes.byKey(id(recipe));
      var item = BuiltInRegistries.ITEM.getOptional(id(component));
      if (holder.isEmpty() || item.isEmpty()) {
        problems.add(recipe + (holder.isEmpty() ? " is not loaded" : " names an unregistered " + component));
        return;
      }
      ItemStack stack = new ItemStack(item.get());
      if (holder.get().value().getIngredients().stream().noneMatch(ingredient -> ingredient.test(stack)))
        problems.add(recipe + " does not consume " + component);
    });
    helper.assertTrue(problems.isEmpty(), "Unstaged round 4 pieces: " + problems);
    helper.succeed();
  }

  private static String bookOf(ItemStack stack) {
    for (TypedDataComponent<?> component : stack.getComponents())
      if ("patchouli:book".equals(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()))))
        return String.valueOf(component.value());
    return "none";
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pneumaticcraftAndPsiGuideBooksStayCraftable(GameTestHelper helper) {
    var recipes = helper.getLevel().getRecipeManager();
    var access = helper.getLevel().registryAccess();
    Map<String, String> expected = Map.of("patchouli:guide_book", "pneumaticcraft:book",
        "psi:encyclopaedia_psionica", "psi:encyclopaedia_psionica");
    List<String> problems = new ArrayList<>();
    expected.forEach((recipe, book) -> {
      var holder = recipes.byKey(id(recipe));
      String found = holder.map(h -> bookOf(h.value().getResultItem(access))).orElse("no recipe");
      if (!book.equals(found)) problems.add(recipe + " makes " + found + " instead of " + book);
    });
    helper.assertTrue(problems.isEmpty(), "Guide book recipes: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void kitchenAutomationKeepsOneRoutePerTask(GameTestHelper helper) throws IOException {
    var access = helper.getLevel().registryAccess();
    var cooking = BuiltInRegistries.RECIPE_TYPE.get(id("farmersdelight:cooking"));
    var mixing = BuiltInRegistries.RECIPE_TYPE.get(id("create:mixing"));
    Set<Item> potOutputs = new HashSet<>();
    Collection<RecipeHolder<?>> all = helper.getLevel().getRecipeManager().getRecipes();
    for (RecipeHolder<?> holder : all)
      if (holder.value().getType() == cooking) potOutputs.add(holder.value().getResultItem(access).getItem());
    // Slice & Dice injects its basin copies as sliceanddice:cooking/...; native Create mixing recipes that
    // happen to share an output (Farmer's Delight's own tomato sauce) are not copies.
    List<String> basinCooking = new ArrayList<>();
    for (RecipeHolder<?> holder : all)
      if (holder.value().getType() == mixing && "sliceanddice".equals(holder.id().getNamespace())
          && potOutputs.contains(holder.value().getResultItem(access).getItem()))
        basinCooking.add(holder.id().toString());
    var file = FMLPaths.CONFIGDIR.get().resolve("create_central_kitchen-common.toml");
    String text = Files.isRegularFile(file) ? Files.readString(file) : "";
    List<String> conversions = new ArrayList<>();
    for (String key : List.of("convertCuttingBoardRecipesToSawingRecipes", "convertCuttingBoardRecipesToDeployingRecipes"))
      if (!Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*false\\s*$").matcher(text).find()) conversions.add(key);
    LOGGER.info("ENTRELUMEN_KITCHEN cookingPotRecipes={} basinCookingCopies={} cuttingConversionsOn={}",
        potOutputs.size(), basinCooking.size(), conversions);
    helper.assertTrue(basinCooking.isEmpty(), basinCooking.size() + " cooking-pot outputs also mix in a basin, e.g. "
        + basinCooking.stream().limit(3).toList());
    helper.assertTrue(conversions.isEmpty(), "Central Kitchen cutting-board conversions still on: " + conversions);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void dungeonsAndTavernsStructuresRegistered(GameTestHelper helper) {
    var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
    List<String> missing = STRUCTURES.stream().filter(name -> !registry.containsKey(id(name))).toList();
    helper.assertTrue(missing.isEmpty(), "Dungeons and Taverns structures missing: " + missing);
    helper.succeed();
  }
}
