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

/**
 * Native loaded recipes, including the original provisions route and bowl returns, and the
 * satiety overflow measured on a real Farmer's Delight pie eaten in place.
 */
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

  /**
   * PENDING: written on 24 September 2026, not yet run on the installed pack. A bite of the real
   * {@code farmersdelight:apple_pie} block at 19/20 hunger and 19 saturation must be measured
   * through the block window with the slice's own values (3 hunger, 1.8 saturation): 2 + 0.8
   * surplus points of glut, whatever effects the slice itself gives.
   */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void farmersDelightPieBiteCountsAsSatietySurplus(GameTestHelper helper) {
    if (!ModList.get().isLoaded("farmersdelight")) throw new IllegalStateException("Farmer's Delight is absent");
    var level = helper.getLevel();
    var pie = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:apple_pie"));
    var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
    level.setBlockAndUpdate(pos, pie.defaultBlockState());
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "PieQA"), false);
    var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(),
        cookie.clientInformation());
    var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
    try {
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.getInventory().clearContent();
      player.getFoodData().setFoodLevel(19);
      player.getFoodData().setSaturation(19);
      player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
          new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
              net.minecraft.core.Direction.UP, pos, false));
      SatietyOverflowEvents.closeBites();
      double glut = player.getPersistentData().getCompound("entrelumen_satiety").getDouble("glut");
      helper.assertTrue(player.getFoodData().getFoodLevel() == 20 && Math.abs(glut - 2.8) < 1e-3,
          "The pie bite was not measured as 2.8 surplus points: glut " + glut);
    } finally {
      connection.disconnect(net.minecraft.network.chat.Component.literal("Pie QA finished"));
      connection.handleDisconnection();
      channel.finishAndReleaseAll();
    }
    helper.succeed();
  }
}
