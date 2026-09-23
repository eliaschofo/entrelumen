package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native loaded recipes, including the original provisions route and bowl returns. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class CookingProvisionsGameTests {
  private record Route(String id, String soup, String meal) {}
  private static final List<Route> ROUTES = List.of(
      new Route("entrelumen:integration/travelling_pantry",
          "farmersdelight:vegetable_soup", "aquaculture:fish_fillet_cooked"),
      new Route("entrelumen:cooking_provisions/homestead_chicken",
          "farmersdelight:vegetable_soup", "pamhc2foodcore:chickendinneritem"),
      new Route("entrelumen:cooking_provisions/field_supper",
          "farmersdelight:fish_stew", "herbsandharvest:beef_cheddar"));

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void threeProvisionsRoutesKeepCookedCostsAndBowls(GameTestHelper helper) {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Provisions checks require native food items");
    for (String mod : List.of("farmersdelight", "aquaculture", "pamhc2foodcore", "herbsandharvest"))
      helper.assertTrue(ModList.get().isLoaded(mod), "Missing native food mod " + mod);
    for (Route route : ROUTES) {
      var manager = helper.getLevel().getRecipeManager();
      var id = ResourceLocation.parse(route.id());
      var holder = manager.byKey(id).orElseThrow();
      helper.assertTrue(holder.value() instanceof CraftingRecipe, "Not a crafting recipe: " + id);
      var recipe = (CraftingRecipe) holder.value();
      helper.assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer())
          .equals(ResourceLocation.withDefaultNamespace("crafting_shapeless")),
          "Unexpected provisions serializer: " + id);
      var stacks = new ArrayList<>(List.of(stack(route.soup()), stack(route.soup()),
          stack(route.meal()), stack(route.meal())));
      for (int permutation = 0; permutation < 4; permutation++) {
        var input = CraftingInput.of(2, 2, stacks);
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Native inputs rejected: " + id);
        helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
            .orElseThrow().id().equals(id), "Another recipe intercepts provisions inputs: " + id);
        var output = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(output.is(stack("entrelumen:ration_bundle").getItem())
            && output.getCount() == 1, "Wrong ration result: " + id);
        var remainders = recipe.getRemainingItems(input);
        helper.assertTrue(remainders.size() == stacks.size(), "Remainder grid changed: " + id);
        int bowls = 0;
        for (int slot = 0; slot < stacks.size(); slot++) {
          var expected = stacks.get(slot).getCraftingRemainingItem();
          helper.assertTrue(ItemStack.matches(expected, remainders.get(slot)),
              "Native food remainder lost at " + id + " slot " + slot);
          if (remainders.get(slot).is(Items.BOWL)) bowls += remainders.get(slot).getCount();
        }
        helper.assertTrue(bowls == 2, "Two soup bowls were not returned: " + id);
        Collections.rotate(stacks, 1);
      }
      var missing = new ArrayList<>(stacks);
      missing.set(0, ItemStack.EMPTY);
      helper.assertTrue(!recipe.matches(CraftingInput.of(2, 2, missing), helper.getLevel()),
          "Incomplete provisions accepted: " + id);
      var raw = new ArrayList<>(stacks);
      raw.set(2, new ItemStack(Items.BEEF));
      helper.assertTrue(!recipe.matches(CraftingInput.of(2, 2, raw), helper.getLevel()),
          "Raw meat bypasses cooking: " + id);
      LogUtils.getLogger().info("ENTRELUMEN_PROVISIONS_NATIVE route={} ration=1 bowls=2 permutations=4 incompleteRejected=true rawRejected=true", id);
    }
    helper.succeed();
  }

  private static ItemStack stack(String id) {
    var key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key) || BuiltInRegistries.ITEM.get(key) == Items.AIR)
      throw new IllegalStateException("Missing native provisions item " + id);
    return new ItemStack(BuiltInRegistries.ITEM.get(key));
  }
}
