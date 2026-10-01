package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests of the guards around Solsticio's commerce and act VI hand-ins (adversarial review of
 * 1 October 2026, docs/design/solsticio-commerce.md): Easy Villagers cannot carry a role away, a trade
 * that pays emeralds keeps its price, hand-ins take plain stacks first and Juan's seeds leave
 * resource seeds out. Local villagers only; excluded from the distributable jar by the
 * RuntimeGameTests* pattern.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsSolsticioCommerceGuards {
  private RuntimeGameTestsSolsticioCommerceGuards() {}

  /** A real server player on an in-memory connection, with its own FTB team and campaign. */
  private static final class QaPlayer implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    QaPlayer(GameTestHelper helper, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      try {
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    public void close() {
      try {
        connection.disconnect(Component.literal("Commerce guard QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
    return helper.absolutePos(new BlockPos(x, y, z));
  }

  private static void floor(GameTestHelper helper) {
    for (int x = 0; x <= 4; x++)
      for (int z = 0; z <= 4; z++) helper.getLevel().setBlockAndUpdate(at(helper, x, 0, z), Blocks.STONE.defaultBlockState());
  }

  private static Villager local(GameTestHelper helper, CommerceRules.Role role, String key, BlockPos pos) {
    var sites = new CommerceSites();
    var site = new CommerceSites.Site(role, key, pos);
    sites.sites.add(site);
    Villager villager = SolsticioCommerce.spawn(helper.getLevel(), sites, site, SolsticioCommerce.LOCAL);
    helper.assertTrue(villager != null, "Could not spawn a local " + role.id + " " + key);
    return villager;
  }

  /** A villager from elsewhere with the given offers, settled in Solsticio (role MOVED). */
  private static Villager settled(GameTestHelper helper, BlockPos pos, VillagerProfession profession, MerchantOffers offers) {
    ServerLevel level = helper.getLevel();
    Villager villager = EntityType.VILLAGER.create(level);
    villager.moveTo(Vec3.atBottomCenterOf(pos));
    villager.setNoAi(true);
    villager.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, 2));
    villager.setOffers(offers);
    level.addFreshEntity(villager);
    helper.assertTrue(SolsticioCommerce.settle(level, villager), "The villager did not settle");
    return villager;
  }

  // ---- F11: Easy Villagers ------------------------------------------------------------------

  /**
   * With Easy Villagers installed, its pick-up (the same static method its server packet calls)
   * leaves a shopkeeper and a native in place, while a settled villager can still be carried.
   */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void easyVillagersLeavesShopkeepersAndNativesInPlace(GameTestHelper helper) {
    if (!ModList.get().isLoaded("easy_villagers")) {
      helper.succeed();
      return;
    }
    Method pickUp, conditions;
    try {
      Class<?> events = Class.forName("de.maxhenkel.easyvillagers.events.VillagerEvents");
      pickUp = events.getMethod("pickUp", Villager.class, Player.class);
      conditions = events.getMethod("arePickupConditionsMet", Villager.class);
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError("Easy Villagers changed its pick-up API: " + missing);
    }
    floor(helper);
    Villager keeper = local(helper, CommerceRules.Role.SHOP, "rarities", at(helper, 1, 1, 1));
    Villager nativeVillager = local(helper, CommerceRules.Role.NATIVE, LuminousRules.Discipline.NATURE.id, at(helper, 3, 1, 1));
    Villager moved = settled(helper, at(helper, 2, 1, 3), VillagerProfession.FARMER, new MerchantOffers());
    try (var qa = new QaPlayer(helper, "PickUpQA")) {
      var player = qa.player;
      player.teleportTo(helper.getLevel(), at(helper, 2, 1, 2).getX() + 0.5, at(helper, 2, 1, 2).getY(),
          at(helper, 2, 1, 2).getZ() + 0.5, java.util.Set.of(), 0f, 0f);
      for (Villager fixed : List.of(keeper, nativeVillager)) {
        String who = SolsticioCommerce.role(fixed).id;
        helper.assertTrue(!(boolean) conditions.invoke(null, fixed), "Easy Villagers may pick up a " + who);
        pickUp.invoke(null, fixed, player);
        helper.assertTrue(fixed.isAlive() && !fixed.isRemoved(), "Easy Villagers carried a " + who + " away");
        helper.assertTrue(player.getInventory().isEmpty(), "Picking up a " + who + " gave an item");
      }
      helper.assertTrue((boolean) conditions.invoke(null, moved), "A settled villager can no longer be carried");
      pickUp.invoke(null, moved, player);
      helper.assertTrue(moved.isRemoved() && !player.getMainHandItem().isEmpty(), "Easy Villagers did not pick up a settled villager");
      player.getInventory().clearContent();
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Easy Villagers' pick-up failed: " + failure);
    } finally {
      keeper.discard();
      nativeVillager.discard();
      if (!moved.isRemoved()) moved.discard();
    }
    helper.succeed();
  }

  // ---- F47: no discount on trades that pay emeralds --------------------------------------------

  /** A settled fletcher's "14 string for 1 emerald" stays 14 after the liberation; its sales still drop. */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void aSettledVillagersEmeraldPayingTradeKeepsItsPriceWhenLiberated(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = SolsticioData.get(level.getServer());
    boolean wasLiberated = data.liberated;
    floor(helper);
    MerchantOffers offers = new MerchantOffers();
    offers.add(new MerchantOffer(new ItemCost(Items.STRING, 14), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
    offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.ARROW, 16), 12, 1, 0.05F));
    offers.add(new MerchantOffer(new ItemCost(Items.STRING, 40), new ItemStack(Items.EMERALD_BLOCK), 4, 2, 0.05F));
    Villager fletcher = settled(helper, at(helper, 2, 1, 2), VillagerProfession.FLETCHER, offers);
    try (var qa = new QaPlayer(helper, "ResaleQA")) {
      var player = qa.player;
      player.teleportTo(level, fletcher.getX(), fletcher.getY(), fletcher.getZ() + 2, java.util.Set.of(), 0f, 0f);
      for (boolean liberated : new boolean[] {false, true}) {
        data.liberated = liberated;
        player.interactOn(fletcher, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof MerchantMenu && fletcher.getTradingPlayer() == player,
            "The trading screen did not open");
        MerchantOffers open = fletcher.getOffers();
        int string = open.get(0).getCostA().getCount(), arrows = open.get(1).getCostA().getCount(),
            block = open.get(2).getCostA().getCount();
        player.closeContainer();
        double multiplier = CommerceRules.multiplier(CommerceRules.Role.MOVED, liberated, false, SolsticioCommerce.prices());
        helper.assertTrue(string == 14 && block == 40,
            "A trade that pays emeralds took a Solsticio discount (liberated " + liberated + "): " + string + ", " + block);
        helper.assertTrue(arrows == CommerceRules.discounted(20, multiplier) && arrows < 20,
            "The settled discount no longer lowers what the villager sells: " + arrows);
      }
      helper.assertTrue(fletcher.getOffers().get(1).getCostA().getCount() == 20, "The discount outlived the trade");
    } finally {
      data.liberated = wasLiberated;
      fletcher.discard();
    }
    helper.succeed();
  }

  // ---- F41: hand-ins take plain stacks first -----------------------------------------------------

  /** A story hand-in takes a plain map before Amparo's errand map in the hotbar, and that one only when none is left. */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void storyHandInsTakePlainStacksBeforeOnesWithData(GameTestHelper helper) {
    try (var qa = new QaPlayer(helper, "HandInQA")) {
      var player = qa.player;
      ItemStack errand = SolsticioStory.errandItem(SolsticioStoryRules.Errand.CARTOGRAPHER_MAP);
      ItemStack named = new ItemStack(Items.WHEAT_SEEDS, 3);
      named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Keepsake"));
      player.getInventory().setItem(0, errand);
      player.getInventory().setItem(1, named);
      player.getInventory().setItem(20, new ItemStack(Items.MAP, 2));
      player.getInventory().setItem(21, new ItemStack(Items.WHEAT_SEEDS, 2));
      SolsticioStory.take(player, SolsticioStory.item("minecraft:map"), 2);
      helper.assertTrue(!errand.isEmpty() && player.getInventory().getItem(20).isEmpty(),
          "The hand-in took the errand map while plain maps were there");
      SolsticioStory.take(player, SolsticioStory.item("minecraft:wheat_seeds"), 3);
      helper.assertTrue(named.getCount() == 2 && player.getInventory().getItem(21).isEmpty(),
          "The hand-in did not use up the plain seeds before the named ones: " + named.getCount());
      SolsticioStory.take(player, SolsticioStory.item("minecraft:map"), 1);
      helper.assertTrue(errand.isEmpty(), "With no plain map left, the stack with data is not taken");
    }
    helper.succeed();
  }

  // ---- F42: Juan's seeds -----------------------------------------------------------------------

  /** Mystical Agriculture's resource seeds and Mowzie's foliaath seed never count as a garden species. */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void juansSeedsLeaveResourceSeedsOut(GameTestHelper helper) {
    List<String> tagged = new java.util.ArrayList<>();
    BuiltInRegistries.ITEM.getTagOrEmpty(SolsticioStory.SEEDS)
        .forEach(holder -> tagged.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
    helper.assertTrue(tagged.contains("minecraft:wheat_seeds") && tagged.contains("minecraft:cocoa_beans"),
        "The seed tag lost its vanilla species: " + tagged);
    for (String id : tagged) {
      String namespace = ResourceLocation.parse(id).getNamespace();
      helper.assertTrue(!namespace.equals("mysticalagriculture") && !id.equals("mowziesmobs:foliaath_seed"),
          "entrelumen:solsticio/seeds still holds " + id);
    }
    for (String id : List.of("mysticalagriculture:nether_star_seeds", "mysticalagriculture:dragon_egg_seeds",
        "mysticalcustomization:anything_seeds"))
      helper.assertTrue(!SolsticioStoryRules.gardenSeed(id), "Juan would take " + id);
    Item wheat = Items.WHEAT_SEEDS;
    helper.assertTrue(SolsticioStoryRules.gardenSeed(BuiltInRegistries.ITEM.getKey(wheat).toString()), "Wheat is no species");
    helper.succeed();
  }
}
