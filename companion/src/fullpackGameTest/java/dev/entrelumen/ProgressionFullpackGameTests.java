package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Installed-pack QA of the progression batch of 24 September 2026 (docs/design/progression-functions.md):
 * the calibration frame has no crafting recipe and is copied by Mekanism infusion, the metallurgic
 * infuser needs a frame, a real infuser turns a raw lens and redstone into a frame, every gate of the
 * functions family consumes its component with no other route to its output, and three Ark modules take
 * their boss drops. Mekanism is reached only through its recipes, blocks and NeoForge capabilities.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ProgressionFullpackGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String FRAME = "entrelumen:calibration_frame";
  private static final String INFUSION = "entrelumen:integration/precision_bench";
  private static final String FUNCTIONS_SCRIPT = "kubejs/server_scripts/entrelumen_functions_balance.js";

  private ProgressionFullpackGameTests() {}

  static void requireSuite() {
    for (String mod : List.of("mekanism", "kubejs", "ae2", "ars_nouveau", "pneumaticcraft", "occultism",
        "mysticalagriculture", "advanced_ae", "modern_industrialization", "mekanismgenerators"))
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
        .orElseThrow(() -> new IllegalStateException("Unregistered item " + id));
  }

  private static ItemStack stack(String id) {
    return new ItemStack(item(id));
  }

  private static ItemStack result(GameTestHelper helper, RecipeHolder<?> holder) {
    try {
      return holder.value().getResultItem(helper.getLevel().registryAccess());
    } catch (RuntimeException special) {
      return ItemStack.EMPTY;
    }
  }

  private static String type(RecipeHolder<?> holder) {
    return String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void calibrationFrameComesOnlyFromInfusion(GameTestHelper helper) throws Exception {
    requireSuite();
    var frame = item(FRAME);
    List<String> producers = new ArrayList<>();
    for (var holder : helper.getLevel().getRecipeManager().getRecipes())
      if (result(helper, holder).is(frame)) producers.add(holder.id() + " (" + type(holder) + ")");
    helper.assertTrue(producers.equals(List.of(INFUSION + " (mekanism:metallurgic_infusing)")),
        "The frame must come only from the infusion recipe: " + producers);
    var infusion = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(INFUSION)).orElseThrow().value();
    // Mekanism 10.7 infusing recipes are BiPredicate<ItemStack, ChemicalStack>; the chemical comes from
    // Mekanism's own registry through its public API.
    Class<?> api = Class.forName("mekanism.api.MekanismAPI");
    Class<?> chemicalClass = Class.forName("mekanism.api.chemical.Chemical");
    Class<?> stackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
    var registry = (net.minecraft.core.Registry<?>) api.getField("CHEMICAL_REGISTRY").get(null);
    Object redstone = registry.get(ResourceLocation.parse("mekanism:redstone"));
    Object carbon = registry.get(ResourceLocation.parse("mekanism:carbon"));
    var chemicalStack = stackClass.getConstructor(chemicalClass, long.class);
    @SuppressWarnings("unchecked")
    var test = (BiPredicate<Object, Object>) infusion;
    var lens = stack("entrelumen:raw_lens");
    helper.assertTrue(test.test(lens, chemicalStack.newInstance(redstone, 40L)),
        "A raw lens with 40 redstone infusion does not match the frame infusion");
    helper.assertTrue(!test.test(lens, chemicalStack.newInstance(carbon, 40L))
        && !test.test(stack("create:iron_sheet"), chemicalStack.newInstance(redstone, 40L)),
        "The frame infusion accepts another chemical or another item");
    var output = (ItemStack) infusion.getClass().getMethod("getOutput", ItemStack.class, stackClass)
        .invoke(infusion, lens, chemicalStack.newInstance(redstone, 40L));
    helper.assertTrue(output.is(frame) && output.getCount() == 1, "The infusion yields " + output);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void metallurgicInfuserNeedsACalibrationFrame(GameTestHelper helper) {
    requireSuite();
    var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("mekanism:metallurgic_infuser"));
    helper.assertTrue(holder.isPresent() && holder.get().value() instanceof CraftingRecipe, "Infuser recipe missing");
    var recipe = (CraftingRecipe) holder.get().value();
    // A drawing (Elias's playtest of 24 September 2026): the frame is the infuser's core.
    String[] slots = {"minecraft:iron_ingot", "minecraft:furnace", "minecraft:iron_ingot",
        "minecraft:redstone", FRAME, "minecraft:redstone",
        "minecraft:iron_ingot", "minecraft:furnace", "minecraft:iron_ingot"};
    List<ItemStack> grid = new ArrayList<>();
    for (String slot : slots) grid.add(stack(slot));
    helper.assertTrue(recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel())
        && recipe.assemble(CraftingInput.of(3, 3, grid), helper.getLevel().registryAccess()).is(item("mekanism:metallurgic_infuser")),
        "The infuser does not take the frame as its core");
    grid.set(4, stack("mekanism:ingot_osmium"));
    helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
        "The native osmium-core infuser still crafts without a frame");
    int producers = 0;
    for (var other : helper.getLevel().getRecipeManager().getRecipes())
      if (result(helper, other).is(item("mekanism:metallurgic_infuser"))) producers++;
    helper.assertTrue(producers == 1, producers + " recipes make the metallurgic infuser");
    helper.succeed();
  }

  /**
   * The infuser's own slots, through Mekanism's public inventory API ({@code TileEntityMekanism#getInventorySlots}
   * and {@code IInventorySlot}), by reflection: pipes and hoppers only reach the faces its side
   * configuration opens, which a fresh machine keeps closed.
   */
  private static List<Object> slots(Object tile) throws ReflectiveOperationException {
    List<Object> slots = new ArrayList<>();
    for (Object slot : (List<?>) tile.getClass().getMethod("getInventorySlots", net.minecraft.core.Direction.class)
        .invoke(tile, (Object) null)) slots.add(slot);
    return slots;
  }

  private static ItemStack slotStack(Object slot) throws ReflectiveOperationException {
    return (ItemStack) slot.getClass().getMethod("getStack").invoke(slot);
  }

  /** Puts the stack into the first empty slot that accepts it; returns that slot's index or -1. */
  private static int place(Object tile, ItemStack stack) throws ReflectiveOperationException {
    var slots = slots(tile);
    for (int i = 0; i < slots.size(); i++) {
      Object slot = slots.get(i);
      if (!slotStack(slot).isEmpty()) continue;
      if ((boolean) slot.getClass().getMethod("isItemValid", ItemStack.class).invoke(slot, stack)) {
        slot.getClass().getMethod("setStack", ItemStack.class).invoke(slot, stack);
        return i;
      }
    }
    return -1;
  }

  @GameTest(template = "empty", timeoutTicks = 900)
  public static void realInfuserCopiesTheFrame(GameTestHelper helper) throws ReflectiveOperationException {
    requireSuite();
    var pos = new BlockPos(2, 1, 2);
    helper.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:metallurgic_infuser")));
    Object tile = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    helper.assertTrue(tile != null, "No metallurgic infuser block entity");
    int lens = place(tile, stack("entrelumen:raw_lens"));
    int dust = place(tile, new ItemStack(Items.REDSTONE, 8));
    helper.assertTrue(lens >= 0 && dust >= 0 && lens != dust,
        "The infuser has no slot for the lens (" + lens + ") or the redstone (" + dust + ")");
    List<Object> energy = new ArrayList<>();
    for (Object container : (List<?>) tile.getClass().getMethod("getEnergyContainers", net.minecraft.core.Direction.class)
        .invoke(tile, (Object) null)) energy.add(container);
    helper.assertTrue(!energy.isEmpty(), "The infuser has no energy container");
    helper.onEachTick(() -> {
      try {
        for (Object container : energy)
          container.getClass().getMethod("setEnergy", long.class).invoke(container,
              container.getClass().getMethod("getMaxEnergy").invoke(container));
      } catch (ReflectiveOperationException error) {
        throw new IllegalStateException(error);
      }
    });
    helper.succeedWhen(() -> {
      boolean copied = false;
      try {
        for (Object slot : slots(tile)) if (slotStack(slot).is(item(FRAME))) copied = true;
      } catch (ReflectiveOperationException error) {
        throw new IllegalStateException(error);
      }
      helper.assertTrue(copied, "No frame yet");
      LOGGER.info("ENTRELUMEN_FRAME_INFUSION a powered metallurgic infuser copied a frame from a raw lens (slot {}) "
          + "and redstone (slot {})", lens, dust);
    });
  }

  /** The rows of entrelumen_functions_balance.js as installed in this QA server. */
  private static JsonArray functionRows() throws IOException {
    String script = Files.readString(FMLPaths.GAMEDIR.get().resolve(FUNCTIONS_SCRIPT));
    String prefix = "const entrelumenFunctionsRows = ";
    for (String line : script.split("\n"))
      if (line.startsWith(prefix)) return JsonParser.parseString(line.substring(prefix.length(), line.lastIndexOf(';'))).getAsJsonArray();
    throw new IOException("No rows in " + FUNCTIONS_SCRIPT);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void functionGatesConsumeTheirComponentWithoutBypass(GameTestHelper helper) throws IOException {
    requireSuite();
    var recipes = helper.getLevel().getRecipeManager();
    var rows = functionRows();
    helper.assertTrue(rows.size() == 22, "The functions family has " + rows.size() + " rows");
    List<String> problems = new ArrayList<>();
    Map<String, List<String>> producers = new LinkedHashMap<>();
    for (var holder : recipes.getRecipes()) {
      var made = result(helper, holder);
      if (!made.isEmpty())
        producers.computeIfAbsent(BuiltInRegistries.ITEM.getKey(made.getItem()).toString(), k -> new ArrayList<>())
            .add(holder.id().toString());
    }
    for (var element : rows) {
      JsonObject row = element.getAsJsonObject();
      String id = row.get("id").getAsString();
      String output = row.get("output").getAsString();
      var component = stack(row.get("component").getAsString());
      var holder = recipes.byKey(ResourceLocation.parse(id));
      if (holder.isEmpty()) {
        problems.add(id + " is not loaded");
        continue;
      }
      if (holder.get().value().getIngredients().stream().noneMatch(ingredient -> ingredient.test(component)))
        problems.add(id + " does not consume " + component.getItem());
      var outputItem = stack(output);
      for (String other : producers.getOrDefault(output, List.of())) {
        if (other.equals(id)) continue;
        // Reset and clear recipes turn the output into itself; anything else is a bypass.
        var ingredients = recipes.byKey(ResourceLocation.parse(other)).orElseThrow().value().getIngredients();
        if (ingredients.stream().noneMatch(i -> i.test(outputItem) || i.test(component)))
          problems.add(other + " makes " + output + " without " + component.getItem());
      }
    }
    LOGGER.info("ENTRELUMEN_FUNCTIONS checked {} gates; problems {}", rows.size(), problems);
    helper.assertTrue(problems.isEmpty(), "Function gates: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void arkModulesTakeTheirBossDrops(GameTestHelper helper) {
    requireSuite();
    Map<String, String> drops = Map.of(
        "entrelumen:integration/ark_arcana", "minecraft:nether_star",
        "entrelumen:integration/ark_nature", "minecraft:wet_sponge",
        "entrelumen:integration/ark_exploration", "minecraft:dragon_breath");
    Map<String, String> projects = Map.of(
        "entrelumen:integration/ark_arcana", "arcane_module",
        "entrelumen:integration/ark_nature", "nature_module",
        "entrelumen:integration/ark_exploration", "exploration_module");
    List<String> problems = new ArrayList<>();
    drops.forEach((id, drop) -> {
      var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id));
      var dropStack = stack(drop);
      if (holder.isEmpty() || holder.get().value().getIngredients().stream().filter(i -> i.test(dropStack)).count() != 1)
        problems.add(id + " does not take exactly one " + drop);
      var project = Projects.all().get(projects.get(id));
      if (project == null || project.items().getOrDefault(drop, 0) != 1
          || holder.isPresent() && project.items().values().stream().mapToInt(Integer::intValue).sum()
              != holder.get().value().getIngredients().size())
        problems.add(projects.get(id) + " delivery does not match its recipe with one " + drop);
    });
    helper.assertTrue(problems.isEmpty(), "Ark boss drops: " + problems);
    helper.succeed();
  }
}
