package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.common.world.NoneBiomeModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Loaded Pam tree modifiers, native market offers and renewable sapling recipes. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class CookingWorldgenGameTests {
  private static final Set<String> ALL = Set.of(("acorn almond apple apricot avocado banana breadfruit "
      + "candlenut cashew cherry chestnut cinnamon coconut date dragonfruit durian fig gooseberry "
      + "grapefruit guava hazelnut jackfruit lemon lime lychee mango maple nutmeg olive orange "
      + "papaya paperbark passionfruit pawpaw peach pear pecan peppercorn persimmon pinenut "
      + "pistachio plum pomegranate rambutan soursop spiderweb starfruit tamarind vanillabean "
      + "walnut").split(" "));
  private static final Set<String> DUPLICATE = Set.of(
      "avocado", "cherry", "cinnamon", "lemon", "lime", "orange", "peach", "pear", "plum");

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void pamTreesKeepMarketAcquisitionWithoutDuplicateWorldgen(GameTestHelper helper)
      throws Exception {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Cooking worldgen QA requires native mods");
    for (String mod : List.of("pamhc2trees", "pamhc2foodcore", "herbsandharvest",
        "sushigocrafting", "farmingforblockheads"))
      helper.assertTrue(ModList.get().isLoaded(mod), "Missing native cooking mod " + mod);

    var modifiers = helper.getLevel().registryAccess()
        .registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
    Set<String> observed = new HashSet<>();
    int muted = 0;
    int retained = 0;
    for (var entry : modifiers.entrySet()) {
      ResourceLocation id = entry.getKey().location();
      if (!id.getNamespace().equals("pamhc2trees")) continue;
      helper.assertTrue(id.getPath().endsWith("_placed"),
          "Unexpected Pam tree biome modifier ID " + id);
      String species = id.getPath().substring(0, id.getPath().length() - "_placed".length());
      observed.add(species);
      if (DUPLICATE.contains(species)) {
        helper.assertTrue(entry.getValue() instanceof NoneBiomeModifier,
            "Duplicate Pam tree still inserts a natural feature: " + id);
        muted++;
      } else {
        helper.assertTrue(entry.getValue() instanceof BiomeModifiers.AddFeaturesBiomeModifier,
            "Unique Pam tree lost native add-features modifier: " + id);
        retained++;
      }
    }
    helper.assertTrue(observed.equals(ALL),
        "Loaded Pam modifier IDs differ; missing=" + difference(ALL, observed)
            + " extra=" + difference(observed, ALL));
    helper.assertTrue(muted == 9 && retained == 41,
        "Expected 9 suppressed and 41 retained Pam tree modifiers; got "
            + muted + " and " + retained);

    // Reflect the actual loaded Farming for Blockheads API so the QA-only
    // source set adds no compile dependency. isRecipeEnabled consults live
    // preset/config state and rejects a missing/disabled or empty offer.
    Class<?> marketRecipeClass = Class.forName(
        "net.blay09.mods.farmingforblockheads.recipe.MarketRecipe");
    Class<?> presetRegistryClass = Class.forName(
        "net.blay09.mods.farmingforblockheads.registry.MarketPresetRegistry");
    Class<?> presetClass = Class.forName(
        "net.blay09.mods.farmingforblockheads.api.MarketPreset");
    Class<?> paymentClass = Class.forName(
        "net.blay09.mods.farmingforblockheads.api.Payment");
    Object presetRegistry = presetRegistryClass.getField("INSTANCE").get(null);
    ResourceLocation presetId = ResourceLocation.parse("pamhc2trees:saplings");
    Optional<?> preset = (Optional<?>) presetRegistryClass
        .getMethod("get", ResourceLocation.class).invoke(presetRegistry, presetId);
    helper.assertTrue(preset.isPresent(), "Native Pam sapling market preset is not loaded");
    helper.assertTrue(Boolean.TRUE.equals(presetClass.getMethod("enabledByDefault")
        .invoke(preset.orElseThrow())), "Pam sapling market preset is disabled by default");
    Method enabled = presetRegistryClass.getMethod("isRecipeEnabled", marketRecipeClass);
    Method getPreset = marketRecipeClass.getMethod("getPreset");
    Method getPayment = marketRecipeClass.getMethod("getPaymentOrDefault");
    Method paymentCount = paymentClass.getMethod("count");
    Method paymentIngredient = paymentClass.getMethod("ingredient");

    for (String species : DUPLICATE) {
      ResourceLocation saplingId = ResourceLocation.parse("pamhc2trees:" + species + "_sapling");
      ResourceLocation marketId = ResourceLocation.parse(
          "farmingforblockheads:market/pamhc2trees/" + species + "_sapling");
      var marketHolder = helper.getLevel().getRecipeManager().byKey(marketId);
      helper.assertTrue(marketHolder.isPresent(), "Native Pam sapling offer missing: " + marketId);
      var market = marketHolder.orElseThrow().value();
      helper.assertTrue(marketRecipeClass.isInstance(market),
          "Pam sapling offer lost its native MarketRecipe: " + marketId);
      helper.assertTrue(presetId.equals(getPreset.invoke(market)),
          "Pam sapling offer uses another preset: " + marketId);
      helper.assertTrue(Boolean.TRUE.equals(enabled.invoke(null, market)),
          "Pam sapling offer is disabled by loaded Market config: " + marketId);
      ItemStack offered = market.getResultItem(helper.getLevel().registryAccess());
      helper.assertTrue(saplingId.equals(BuiltInRegistries.ITEM.getKey(offered.getItem()))
          && offered.getCount() == 1,
          "Pam Market does not offer one native sapling: " + marketId);
      Object payment = getPayment.invoke(market);
      Ingredient ingredient = (Ingredient) paymentIngredient.invoke(payment);
      helper.assertTrue(((Integer) paymentCount.invoke(payment)) == 1
          && ingredient.test(new ItemStack(Items.EMERALD))
          && !ingredient.test(new ItemStack(Items.DIAMOND)),
          "Pam sapling offer payment is not one emerald: " + marketId);

      var nativeHolder = helper.getLevel().getRecipeManager().byKey(saplingId);
      helper.assertTrue(nativeHolder.isPresent(), "Native Pam sapling recipe missing: " + saplingId);
      helper.assertTrue(nativeHolder.orElseThrow().value() instanceof CraftingRecipe,
          "Pam sapling propagation is not crafting: " + saplingId);
      CraftingRecipe recipe = (CraftingRecipe) nativeHolder.orElseThrow().value();
      ResourceLocation fruitId = ResourceLocation.parse("pamhc2trees:" + species + "item");
      helper.assertTrue(BuiltInRegistries.ITEM.containsKey(fruitId),
          "Native orchard fruit is missing: " + fruitId);
      List<ItemStack> grid = new ArrayList<>();
      for (int count = 0; count < 8; count++)
        grid.add(new ItemStack(BuiltInRegistries.ITEM.get(fruitId)));
      grid.add(new ItemStack(Items.OAK_SAPLING));
      CraftingInput input = CraftingInput.of(3, 3, grid);
      helper.assertTrue(recipe.matches(input, helper.getLevel()),
          "Native fruit-plus-sapling propagation fails: " + saplingId);
      ItemStack propagated = recipe.assemble(input, helper.getLevel().registryAccess());
      helper.assertTrue(saplingId.equals(BuiltInRegistries.ITEM.getKey(propagated.getItem()))
          && propagated.getCount() == 1,
          "Pam fruit propagation produced another item: " + saplingId);
    }
    LogUtils.getLogger().info(
        "ENTRELUMEN_COOKING_WORLDGEN_NATIVE suppressed={} retained={} liveMarketOffers={} nativeSaplingRecipes={}",
        muted, retained, DUPLICATE.size(), DUPLICATE.size());
    helper.succeed();
  }

  private static Set<String> difference(Set<String> left, Set<String> right) {
    Set<String> result = new HashSet<>(left);
    result.removeAll(right);
    return result;
  }
}
