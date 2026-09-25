package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests of act VI, Solsticio ({@code docs/design/act-six.md}), one per mission group and
 * gate; excluded from the distributable jar by the RuntimeGameTests* pattern. Named characters,
 * innkeepers and shopkeepers are local villagers in each test's volume (the dialogue depends on their
 * role, not on where they stand); places (chapel, secret garden, easter eggs, portal) are the shared
 * city's. Every body runs in one tick and restores the world state it touches (liberation,
 * elections, portal), because other tests share it.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsStory {
  private RuntimeGameTestsStory() {}

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
        connection.disconnect(Component.literal("Story QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** Local NPCs of one test, discarded at the end. */
  private static final class Cast implements AutoCloseable {
    final GameTestHelper helper;
    final List<Entity> spawned = new ArrayList<>();
    int next;

    Cast(GameTestHelper helper) {
      this.helper = helper;
      for (int x = 0; x <= 4; x++)
        for (int z = 0; z <= 4; z++)
          helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
    }

    Villager npc(CommerceRules.Role role, String key) {
      BlockPos pos = helper.absolutePos(new BlockPos(next % 5, 1, next / 5 % 5));
      next++;
      var sites = new CommerceSites();
      var site = new CommerceSites.Site(role, key, pos);
      sites.sites.add(site);
      Villager villager = SolsticioCommerce.spawn(helper.getLevel(), sites, site, SolsticioCommerce.LOCAL);
      helper.assertTrue(villager != null, "Could not spawn a local " + role.id + " " + key);
      spawned.add(villager);
      return villager;
    }

    Villager character(String key) {
      return npc(CommerceRules.Role.CHARACTER, key);
    }

    Villager innkeeper(int persona) {
      Villager inn = npc(CommerceRules.Role.SIDEQUEST, "story" + persona + "_inn");
      SolsticioCommerce.data(inn).putInt("persona", persona);
      return inn;
    }

    Villager shop(String type) {
      return npc(CommerceRules.Role.SHOP, type);
    }

    @Override
    public void close() {
      spawned.forEach(Entity::discard);
    }
  }

  /** Runs the body once, on the first tick the shared city is ready; then succeeds. */
  private static void whenCityReady(GameTestHelper helper, Runnable body) {
    var server = helper.getLevel().getServer();
    helper.assertTrue(server.getLevel(Solsticio.LEVEL) != null, "The Solsticio dimension is missing");
    AtomicBoolean done = new AtomicBoolean();
    helper.onEachTick(() -> {
      if (done.get() || !SolsticioCity.ensure(server)) return;
      done.set(true);
      body.run();
      helper.succeed();
    });
  }

  /** A campaign that activated the Ark and crossed with its own Light Key. */
  private static Campaigns.Campaign arrive(ServerPlayer player, String... missions) {
    var campaign = Entrelumen.current(player);
    campaign.act = Campaigns.FINAL_ACT;
    campaign.completed.add(CampaignMilestones.LAST_HORIZON);
    campaign.completed.add(SolsticioStoryRules.ARRIVAL);
    campaign.completed.addAll(List.of(missions));
    CampaignData.get(player.server).setDirty();
    return campaign;
  }

  private static final String[] RELIC_MISSIONS = {SolsticioStoryRules.MAYOR, SolsticioStoryRules.SEEDS,
      SolsticioStoryRules.HARVEST, SolsticioStoryRules.POWER, SolsticioStoryRules.TERRAPRISM,
      SolsticioStoryRules.GOOD_WORKS, SolsticioStoryRules.BLESSING};

  private static void give(ServerPlayer player, Item item, int count) {
    int max = new ItemStack(item).getMaxStackSize();
    while (count > 0) {
      int stack = Math.min(max, count);
      player.getInventory().add(new ItemStack(item, stack));
      count -= stack;
    }
  }

  private static int count(ServerPlayer player, Item item) {
    return player.getInventory().countItem(item);
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
  }

  private static void assertSaid(GameTestHelper helper, SolsticioStory.Talk talk, String suffix) {
    helper.assertTrue(talk != null && talk.said(suffix), "Expected '" + suffix + "', said " + (talk == null ? "nothing" : talk.keys()));
  }

  private static String key(Component component) {
    return component != null && component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
  }

  private static void toSolsticio(ServerPlayer player, BlockPos pos) {
    ServerLevel solsticio = player.server.getLevel(Solsticio.LEVEL);
    player.teleportTo(solsticio, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), 0f, 0f);
  }

  /**
   * The city's easter eggs, or, when the template carries no {@code easter:} markers (the terraced
   * citadel of 25 September has none yet), stand-ins around the arrival point for the duration of one
   * test body. Returns what to restore.
   */
  private static Map<String, List<BlockPos>> stageEggs(SolsticioData data) {
    Map<String, List<BlockPos>> before = new java.util.TreeMap<>();
    data.commerce.easterEggs.forEach((name, spots) -> before.put(name, List.copyOf(spots)));
    int i = 0;
    for (String egg : SolsticioStoryRules.EGGS) {
      if (!data.commerce.easterEggs.containsKey(egg))
        data.commerce.easterEggs.put(egg, List.of(data.arrival.offset(24 * (i - 1), 0, 24)));
      i++;
    }
    return before;
  }

  private static void restoreEggs(SolsticioData data, Map<String, List<BlockPos>> before) {
    data.commerce.easterEggs.clear();
    before.forEach((name, spots) -> data.commerce.easterEggs.put(name, new ArrayList<>(spots)));
  }

  private static void home(GameTestHelper helper, ServerPlayer player) {
    BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
    player.teleportTo(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, java.util.Set.of(), 0f, 0f);
  }

  // ---- missions 1-2 and the gate --------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void storyWaitsForTheTeamsOwnCrossingAndAureliaOpensIt(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var server = helper.getLevel().getServer();
      try (var cast = new Cast(helper); var visitor = new QaPlayer(helper, "StoryVisitorQA");
           var qa = new QaPlayer(helper, "StoryMayorQA")) {
        Villager aurelia = cast.character("mayor");
        Villager terra = cast.character("inventor");
        // Act VI without the team's own crossing: a visitor. Nothing is recorded.
        var visiting = Entrelumen.current(visitor.player);
        visiting.act = Campaigns.FINAL_ACT;
        visiting.completed.add(CampaignMilestones.LAST_HORIZON);
        var talk = SolsticioStory.character(visitor.player, aurelia, "mayor");
        helper.assertTrue(talk.keys().equals(List.of("entrelumen.solsticio.character.mayor.visitor"))
            && !visiting.completed.contains(SolsticioStoryRules.MAYOR), "A visitor started the story: " + talk.keys());
        helper.assertTrue(SolsticioStory.innkeeperTalk(visitor.player, cast.innkeeper(0)) == null,
            "An innkeeper gave a visitor an errand");

        var campaign = arrive(qa.player);
        assertSaid(helper, SolsticioStory.character(qa.player, terra, "inventor"), "inventor.protocol");
        // The real right-click path: Aurelia records mission 2.
        qa.player.interactOn(aurelia, InteractionHand.MAIN_HAND);
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.MAYOR), "Talking to Aurelia did not record mission 2");
        helper.assertTrue(!(qa.player.containerMenu instanceof MerchantMenu), "A character opened a trading screen");
        assertSaid(helper, SolsticioStory.character(qa.player, aurelia, "mayor"), "mayor.wait_gardener");
        assertSaid(helper, SolsticioStory.character(qa.player, terra, "inventor"), "inventor.power");

        // Mission 2's reward: the compass marks the halls where the placed city put the characters.
        var data = SolsticioData.get(server);
        Map<String, String> halls = Map.of("mayor", "solsticio_town_hall", "gardener", "solsticio_gardens",
            "inventor", "solsticio_workshop", "priest", "solsticio_chapel");
        for (var hall : halls.entrySet()) {
          var objective = CompassTargets.active().stream().filter(o -> o.id().equals(hall.getValue())).findFirst();
          helper.assertTrue(objective.isPresent()
              && data.npcs.get(hall.getKey()).equals(HeliodorCompass.cityMarker(data, objective.get().target().value())),
              "The compass does not mark " + hall.getKey() + " at " + data.npcs.get(hall.getKey()) + ": " + objective);
          helper.assertTrue(data.commerce.find(CommerceRules.Role.CHARACTER, hall.getKey(), data.npcs.get(hall.getKey())) != null,
              "The city has no site for " + hall.getKey());
        }
        var portal = CompassTargets.active().stream().filter(o -> o.id().equals("solsticio_portal")).findFirst();
        helper.assertTrue(portal.isPresent() && data.portal.equals(HeliodorCompass.cityMarker(data, portal.get().target().value())),
            "The compass does not mark the portal");
      }
    });
  }

  // ---- missions 3-4: Juan -----------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void juanWantsEightSpeciesThenSixteenOfFourMeals(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      try (var cast = new Cast(helper); var qa = new QaPlayer(helper, "StoryJuanQA")) {
        var player = qa.player;
        Villager juan = cast.character("gardener");
        var campaign = arrive(player, SolsticioStoryRules.MAYOR);
        give(player, Items.WHEAT_SEEDS, 3);
        for (Item seed : List.of(Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.POTATO, Items.SWEET_BERRIES))
          give(player, seed, 1);
        give(player, Items.CARROT, 5);
        var talk = SolsticioStory.character(player, juan, "gardener");
        assertSaid(helper, talk, "gardener.seeds");
        helper.assertTrue(!campaign.completed.contains(SolsticioStoryRules.SEEDS) && count(player, Items.WHEAT_SEEDS) == 3,
            "Seven species were enough, or something was taken");
        give(player, Items.COCOA_BEANS, 2);
        talk = SolsticioStory.character(player, juan, "gardener");
        assertSaid(helper, talk, "gardener.seeds_done");
        assertSaid(helper, talk, "gardener.basket");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.SEEDS) && count(player, Items.WHEAT_SEEDS) == 2
            && count(player, Items.COCOA_BEANS) == 1 && count(player, Items.BEETROOT_SEEDS) == 0,
            "Juan did not take exactly one of each species");
        talk = SolsticioStory.character(player, juan, "gardener");
        helper.assertTrue(talk.said("gardener.harvest") && !talk.said("gardener.basket"), "A second basket the same day: " + talk.keys());

        List<Item> meals = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(SolsticioStory.MEALS).forEach(holder -> meals.add(holder.value()));
        helper.assertTrue(meals.size() >= 4, "Fewer than four meals in entrelumen:solsticio/meals: " + meals.size());
        for (int i = 0; i < 3; i++) give(player, meals.get(i), 16);
        give(player, meals.get(3), 15);
        assertSaid(helper, SolsticioStory.character(player, juan, "gardener"), "gardener.harvest");
        helper.assertTrue(count(player, Solsticio.RELICS.get(0).get()) == 0, "Relic I for fifteen of a kind");
        give(player, meals.get(3), 1);
        give(player, meals.get(0), 5);
        assertSaid(helper, SolsticioStory.character(player, juan, "gardener"), "gardener.harvest_done");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.HARVEST) && count(player, Solsticio.RELICS.get(0).get()) == 1
            && count(player, meals.get(0)) == 5 && count(player, meals.get(1)) == 0 && count(player, meals.get(3)) == 0,
            "The harvest did not become the Luminous Seed");
        assertSaid(helper, SolsticioStory.character(player, juan, "gardener"), "gardener.after");
        helper.assertTrue(count(player, Solsticio.RELICS.get(0).get()) == 1, "A second Luminous Seed");
      }
    });
  }

  // ---- missions 5-6: Terra ----------------------------------------------------------------------

  /** A charged battery: the fixture's, or in the full pack any energy mod's item that holds 100,000 FE. */
  private static ItemStack battery(GameTestHelper helper, boolean charged) {
    Item fixture = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("entrelumen_gametest_fixture", "test_battery"));
    List<Item> candidates = new ArrayList<>();
    if (fixture != Items.AIR) candidates.add(fixture);
    else BuiltInRegistries.ITEM.forEach(candidates::add);
    for (Item item : candidates) {
      ItemStack stack = new ItemStack(item);
      var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
      if (energy == null || energy.getMaxEnergyStored() < SolsticioStoryRules.BATTERY_CAPACITY
          || SolsticioStoryRules.charged(energy.getEnergyStored(), energy.getMaxEnergyStored())) continue;
      if (!charged) return stack;
      for (int i = 0; i < 10_000 && !SolsticioStoryRules.charged(energy.getEnergyStored(), energy.getMaxEnergyStored()); i++)
        if (energy.receiveEnergy(Integer.MAX_VALUE, false) == 0) break;
      if (SolsticioStoryRules.charged(energy.getEnergyStored(), energy.getMaxEnergyStored())) return stack;
    }
    helper.fail("No item battery of at least " + SolsticioStoryRules.BATTERY_CAPACITY + " FE");
    return ItemStack.EMPTY;
  }

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void terraNeedsPowerFromOutsideThenSixLuminosities(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      try (var cast = new Cast(helper); var qa = new QaPlayer(helper, "StoryTerraQA")) {
        var player = qa.player;
        Villager terra = cast.character("inventor");
        var campaign = arrive(player, SolsticioStoryRules.MAYOR);
        Item coil = item("create_new_age:generator_coil");
        Item plate = item("create_new_age:advanced_solar_heating_plate");
        helper.assertTrue(coil != Items.AIR && plate != Items.AIR, "Create: New Age parts are not registered");
        give(player, coil, 4);
        give(player, plate, 2);
        var talk = SolsticioStory.character(player, terra, "inventor");
        assertSaid(helper, talk, "inventor.power");
        assertSaid(helper, talk, "story.battery");
        player.getInventory().add(battery(helper, false));
        assertSaid(helper, SolsticioStory.character(player, terra, "inventor"), "story.battery");
        helper.assertTrue(!campaign.completed.contains(SolsticioStoryRules.POWER), "An empty battery counted as charged");
        player.getInventory().clearContent();
        give(player, coil, 3);
        give(player, plate, 2);
        player.getInventory().add(battery(helper, true));
        talk = SolsticioStory.character(player, terra, "inventor");
        assertSaid(helper, talk, "story.missing");
        give(player, coil, 1);
        talk = SolsticioStory.character(player, terra, "inventor");
        assertSaid(helper, talk, "inventor.power_done");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.POWER) && count(player, coil) == 0 && count(player, plate) == 0
            && SolsticioStory.battery(player) == null, "Terra did not take the parts and the battery");
        ItemStack diagram = player.getInventory().items.stream().filter(stack -> stack.is(Items.WRITTEN_BOOK)).findFirst()
            .orElse(ItemStack.EMPTY);
        helper.assertTrue(key(diagram.get(DataComponents.CUSTOM_NAME)).equals("entrelumen.solsticio.lore.terraprism.title"),
            "No diagram of the Terraprism");

        var luminosities = new ArrayList<>(Luminous.LUMINOSITIES.values());
        for (int i = 0; i < 5; i++) give(player, luminosities.get(i).get(), 1);
        talk = SolsticioStory.character(player, terra, "inventor");
        assertSaid(helper, talk, "inventor.light");
        assertSaid(helper, talk, "story.missing");
        give(player, luminosities.get(5).get(), 1);
        assertSaid(helper, SolsticioStory.character(player, terra, "inventor"), "inventor.light_done");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.TERRAPRISM) && count(player, Solsticio.RELICS.get(1).get()) == 1
            && luminosities.stream().allMatch(luminosity -> count(player, luminosity.get()) == 0),
            "The six Luminosities did not become the Terraprism");
      }
    });
  }

  // ---- mission 7: the innkeepers' errands -------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void theEightErrandsRaiseTheRelationUntilBodhiListens(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var server = helper.getLevel().getServer();
      var data = SolsticioData.get(server);
      BlockPos oldElder = data.elder;
      var eggs = stageEggs(data);
      try (var cast = new Cast(helper); var qa = new QaPlayer(helper, "StoryErrandsQA")) {
        var player = qa.player;
        var campaign = arrive(player, SolsticioStoryRules.MAYOR);
        UUID team = CampaignActions.campaignId(player);
        Villager dorotea = cast.innkeeper(0), tobias = cast.innkeeper(1), amparo = cast.innkeeper(2), ciro = cast.innkeeper(3);
        Villager bakery = cast.shop("bakery"), books = cast.shop("bookstore"), maps = cast.shop("maps");
        Villager anselmo = cast.npc(CommerceRules.Role.TOWNSFOLK, "");
        data.elder = BlockPos.of(SolsticioCommerce.data(anselmo).getLong("site"));

        // Dorotea: honey for the bakery (the baker takes it through the real right-click path).
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, dorotea), "bakery_honey.offer");
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, dorotea), "bakery_honey.reminder");
        give(player, Items.HONEY_BOTTLE, 4);
        player.interactOn(bakery, InteractionHand.MAIN_HAND);
        helper.assertTrue(SolsticioStoryRules.relation(campaign) == 1 && count(player, Items.HONEY_BOTTLE) == 0
            && !(player.containerMenu instanceof MerchantMenu), "Miga did not take the honey");
        // Dorotea's cat: the real path offers it; standing close below its roof finds it.
        player.interactOn(dorotea, InteractionHand.MAIN_HAND);
        var cat = data.cats.get(team);
        helper.assertTrue(SolsticioStoryRules.taken(campaign, SolsticioStoryRules.Errand.LOST_CAT) && cat != null,
            "The lost cat was not placed");
        toSolsticio(player, cat.pos().offset(12, 0, 0));
        SolsticioStory.visit(player);
        helper.assertTrue(SolsticioStoryRules.relation(campaign) == 1, "The cat was found from twelve blocks away");
        toSolsticio(player, cat.pos().below(8).offset(2, 0, 1));
        assertSaid(helper, SolsticioStory.visit(player), "lost_cat.done");
        helper.assertTrue(SolsticioStoryRules.relation(campaign) == 2 && !data.cats.containsKey(team), "The cat did not go home");
        home(helper, player);
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, dorotea), "innkeeper.0.thanks");

        // Tobías: the songbook back to the bookstore, then candles in Bodhi's chapel.
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, tobias), "library_book.offer");
        var songbook = SolsticioStory.errandItemOf(SolsticioStoryRules.Errand.LIBRARY_BOOK);
        helper.assertTrue(SolsticioStory.count(player, songbook) == 1, "No songbook");
        assertSaid(helper, SolsticioStory.shopTalk(player, books, "bookstore"), "library_book.done");
        helper.assertTrue(SolsticioStory.count(player, songbook) == 0 && SolsticioStoryRules.relation(campaign) == 3,
            "Lucerna did not take the songbook");
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, tobias), "chapel_candles.offer");
        BlockPos chapel = data.npcs.get("priest");
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        helper.assertTrue(SolsticioStory.lightCandles(player, flint, chapel.offset(40, 0, 0)) == null,
            "Candles lit far from the chapel");
        assertSaid(helper, SolsticioStory.lightCandles(player, flint, chapel), "chapel_candles.more");
        give(player, Items.CANDLE, 2);
        give(player, Items.RED_CANDLE, 2);
        assertSaid(helper, SolsticioStory.lightCandles(player, flint, chapel.offset(-6, 1, 2)), "chapel_candles.done");
        helper.assertTrue(count(player, Items.CANDLE) + count(player, Items.RED_CANDLE) == 0 && flint.getDamageValue() == 1
            && SolsticioStoryRules.relation(campaign) == 4, "The chapel's candles were not lit");

        // Amparo: Rumbo's map for Anselmo; the fifth errand makes Bodhi listen.
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, amparo), "cartographer_map.offer");
        var map = SolsticioStory.errandItemOf(SolsticioStoryRules.Errand.CARTOGRAPHER_MAP);
        assertSaid(helper, SolsticioStory.shopTalk(player, maps, "maps"), "cartographer_map.handed");
        helper.assertTrue(SolsticioStory.count(player, map) == 1, "Rumbo gave no map");
        helper.assertTrue(SolsticioStory.shopTalk(player, maps, "maps") == null, "Rumbo gave a second map");
        var delivered = SolsticioStory.elderTalk(player, anselmo);
        assertSaid(helper, delivered, "cartographer_map.done");
        assertSaid(helper, delivered, "story.good_works");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.GOOD_WORKS) && SolsticioStory.count(player, map) == 0,
            "The fifth errand did not record mission 7");
        helper.assertTrue(key(anselmo.getCustomName()).equals("entrelumen.solsticio.elder")
            || SolsticioStory.isElder(server, anselmo), "Anselmo is not the old neighbour");
        // Amparo's shears come out of Solsticio's water.
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, amparo), "river_tool.offer");
        helper.assertTrue(!SolsticioStory.fish(player), "Fished Amparo's shears outside Solsticio");
        toSolsticio(player, data.arrival);
        helper.assertTrue(SolsticioStory.fish(player) && !SolsticioStory.fish(player), "The shears did not bite exactly once");
        home(helper, player);
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, amparo), "river_tool.done");

        // Ciro: the cellar lamp, then flowers in the secret garden.
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, ciro), "copper_lantern.offer");
        give(player, Items.LANTERN, 1);
        give(player, Items.WAXED_CUT_COPPER, 3);
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, ciro), "copper_lantern.reminder");
        give(player, Items.WAXED_CUT_COPPER, 1);
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, ciro), "copper_lantern.done");
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, ciro), "garden_flowers.offer");
        for (Item flower : List.of(Items.POPPY, Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.ALLIUM)) give(player, flower, 1);
        BlockPos garden = data.commerce.easterEggs.get("secret_garden").getFirst();
        toSolsticio(player, garden);
        var visit = SolsticioStory.visit(player);
        assertSaid(helper, visit, "garden_flowers.done");
        helper.assertTrue(count(player, Items.POPPY) == 1 && count(player, Items.ALLIUM) == 0, "The garden took the wrong flowers");
        home(helper, player);
        assertSaid(helper, SolsticioStory.innkeeperTalk(player, ciro), "innkeeper.3.thanks");
        helper.assertTrue(SolsticioStoryRules.relation(campaign) == 8, "Relation after eight errands: " + SolsticioStoryRules.relation(campaign));
      } finally {
        data.elder = oldElder;
        restoreEggs(data, eggs);
      }
    });
  }

  // ---- mission 8: the blessing -------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void bodhiBlessesTheHeartTheAtlasLetsGo(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      try (var cast = new Cast(helper); var qa = new QaPlayer(helper, "StoryBodhiQA");
           var carrier = new QaPlayer(helper, "StoryCarrierQA"); var careless = new QaPlayer(helper, "StoryLostHeartQA")) {
        var player = qa.player;
        Villager bodhi = cast.character("priest");
        var campaign = arrive(player, SolsticioStoryRules.MAYOR);
        var talk = SolsticioStory.character(player, bodhi, "priest");
        assertSaid(helper, talk, "priest.works");
        campaign.completed.add(SolsticioStoryRules.GOOD_WORKS);
        assertSaid(helper, SolsticioStory.character(player, bodhi, "priest"), "priest.spirit");
        // Act IV delivered the Heart to the Atlas: it hands it back now, once.
        campaign.completed.add(HeliodorHeartRules.RECOVERED);
        campaign.completed.add(HeliodorHeartRules.PROJECT);
        assertSaid(helper, SolsticioStory.character(player, bodhi, "priest"), "priest.release");
        helper.assertTrue(campaign.completed.contains(HeliodorHeartRules.RELEASED) && count(player, HeliodorHeart.ITEM.get()) == 1,
            "The Atlas did not let go of the Heart");
        helper.assertTrue(!HeliodorHeart.release(player), "The Atlas released a second Heart");
        talk = SolsticioStory.character(player, bodhi, "priest");
        assertSaid(helper, talk, "priest.blessing");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.BLESSING) && count(player, HeliodorHeart.ITEM.get()) == 0
            && count(player, Solsticio.RELICS.get(2).get()) == 1, "The Heart did not become relic III");
        assertSaid(helper, SolsticioStory.character(player, bodhi, "priest"), "priest.wait");
        helper.assertTrue(count(player, Solsticio.RELICS.get(2).get()) == 1, "A second blessing");

        // A Heart recovered but never delivered to the Atlas is blessed straight from the hand.
        var other = arrive(carrier.player, SolsticioStoryRules.MAYOR, SolsticioStoryRules.GOOD_WORKS, HeliodorHeartRules.RECOVERED);
        give(carrier.player, HeliodorHeart.ITEM.get(), 1);
        assertSaid(helper, SolsticioStory.character(carrier.player, bodhi, "priest"), "priest.blessing");
        helper.assertTrue(other.completed.contains(SolsticioStoryRules.BLESSING), "A carried Heart was not blessed");
        // Released and then lost: Bodhi waits for it (an operator can recover it).
        arrive(careless.player, SolsticioStoryRules.MAYOR, SolsticioStoryRules.GOOD_WORKS, HeliodorHeartRules.RECOVERED,
            HeliodorHeartRules.PROJECT, HeliodorHeartRules.RELEASED);
        assertSaid(helper, SolsticioStory.character(careless.player, bodhi, "priest"), "priest.heart");
      }
    });
  }

  // ---- missions 9-10: the table and the portal ------------------------------------------------

  private static BlockHitResult top(BlockPos pos) {
    return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
  }

  private static ItemStack returnKey(ServerPlayer owner) {
    ItemStack key = new ItemStack(Solsticio.LIGHT_KEY_BROKEN.get());
    key.set(Solsticio.KEY_OWNER, new Solsticio.KeyOwner(owner.getUUID(), owner.getGameProfile().getName()));
    return key;
  }

  /** Portal and liberation as they were, so the shared world is left untouched. */
  private record WorldState(java.util.Set<Integer> relics, boolean armed, UUID armedBy, boolean waystone, BlockPos twin,
      boolean liberated, long liberatedAt, boolean elections, long electionsAt) {
    static WorldState save(SolsticioData data) {
      return new WorldState(new java.util.TreeSet<>(data.portalRelics), data.portalArmed, data.portalArmedBy,
          data.waystoneRegistered, data.overworldPortal, data.liberated, data.liberatedAt, data.electionsHeld, data.electionsAt);
    }

    void restore(MinecraftServer server, SolsticioData data) {
      data.portalRelics.clear();
      data.portalRelics.addAll(relics);
      if (data.overworldPortal != null && !data.overworldPortal.equals(twin)) server.overworld().removeBlock(data.overworldPortal, false);
      data.overworldPortal = twin;
      data.portalArmed = armed;
      data.portalArmedBy = armedBy;
      data.waystoneRegistered = waystone;
      server.getLevel(Solsticio.LEVEL).setBlock(data.portal,
          Solsticio.PORTAL.get().defaultBlockState().setValue(SolsticioPortalBlock.ARMED, armed), 3);
      data.liberated = liberated;
      data.liberatedAt = liberatedAt;
      data.electionsHeld = elections;
      data.electionsAt = electionsAt;
      data.setDirty();
      StructureProtection.invalidate(server);
    }
  }

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void theTableOfTwoLightsOpensThePortalAndLiberatesTheEntrelumen(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var server = helper.getLevel().getServer();
      var data = SolsticioData.get(server);
      ServerLevel solsticio = server.getLevel(Solsticio.LEVEL);
      var before = WorldState.save(data);
      try (var cast = new Cast(helper); var first = new QaPlayer(helper, "StoryPortalQA");
           var second = new QaPlayer(helper, "StoryLateQA"); var early = new QaPlayer(helper, "StoryNoAccordQA")) {
        // Start from a dormant portal and a chained Entrelumen.
        data.portalRelics.clear();
        data.portalArmed = false;
        data.liberated = false;
        data.liberatedAt = 0;
        solsticio.setBlock(data.portal, Solsticio.PORTAL.get().defaultBlockState(), 3);
        Villager aurelia = cast.character("mayor"), bodhi = cast.character("priest");
        var player = first.player;
        var campaign = arrive(player, RELIC_MISSIONS);
        SolsticioTravel.toSolsticio(player);
        ItemStack relic = new ItemStack(Solsticio.RELICS.get(0).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, relic);
        player.gameMode.useItemOn(player, solsticio, relic, InteractionHand.MAIN_HAND, top(data.portal));
        helper.assertTrue(relic.getCount() == 1 && SolsticioStoryRules.relicsPresented(campaign) == 0,
            "A relic was set before the accord");

        // Mission 9: both voices, Aurelia first.
        assertSaid(helper, SolsticioStory.character(player, aurelia, "mayor"), "mayor.table");
        assertSaid(helper, SolsticioStory.character(player, aurelia, "mayor"), "mayor.table_waiting");
        var accord = SolsticioStory.character(player, bodhi, "priest");
        assertSaid(helper, accord, "priest.accord");
        assertSaid(helper, accord, "mayor.accord");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.ACCORD), "No accord at the table");

        // Mission 10: three relics and the player's own key open it for everyone and liberate the city.
        for (int i = 1; i <= 3; i++) {
          ItemStack stack = new ItemStack(Solsticio.RELICS.get(i - 1).get());
          player.setItemInHand(InteractionHand.MAIN_HAND, stack);
          player.gameMode.useItemOn(player, solsticio, stack, InteractionHand.MAIN_HAND, top(data.portal));
          helper.assertTrue(stack.isEmpty() && SolsticioStoryRules.presented(campaign, i), "Relic " + i + " was not set");
        }
        helper.assertTrue(!data.portalArmed && !campaign.completed.contains(SolsticioStoryRules.PORTAL), "Opened without the key");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(8, returnKey(player));
        player.gameMode.useItemOn(player, solsticio, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(data.portal));
        helper.assertTrue(data.portalArmed && solsticio.getBlockState(data.portal).getValue(SolsticioPortalBlock.ARMED)
            && campaign.completed.contains(SolsticioStoryRules.PORTAL), "The portal did not open");
        helper.assertTrue(data.liberated && data.liberatedAt > 0, "Opening the portal did not liberate the Entrelumen");
        helper.assertTrue(CommerceRules.multiplier(CommerceRules.Role.SHOP, data.liberated, false, SolsticioCommerce.prices()) < 1,
            "Shop prices did not fall");
        long liberatedAt = data.liberatedAt;

        // A later team sets its own relics in the open portal and completes its mission 10.
        var late = arrive(second.player, RELIC_MISSIONS);
        late.completed.add(SolsticioStoryRules.ACCORD);
        SolsticioTravel.toSolsticio(second.player);
        for (int i = 1; i <= 3; i++) {
          ItemStack stack = new ItemStack(Solsticio.RELICS.get(i - 1).get());
          second.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
          second.player.gameMode.useItemOn(second.player, solsticio, stack, InteractionHand.MAIN_HAND, top(data.portal));
        }
        second.player.getInventory().setItem(8, returnKey(second.player));
        second.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        second.player.gameMode.useItemOn(second.player, solsticio, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(data.portal));
        helper.assertTrue(late.completed.contains(SolsticioStoryRules.PORTAL) && data.portalArmed && data.liberatedAt == liberatedAt,
            "A later team could not complete the portal, or it re-liberated the city");

        // A team with its relics but no accord is refused even at the open portal.
        var hasty = arrive(early.player, RELIC_MISSIONS);
        SolsticioTravel.toSolsticio(early.player);
        ItemStack stack = new ItemStack(Solsticio.RELICS.get(0).get());
        early.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        early.player.gameMode.useItemOn(early.player, solsticio, stack, InteractionHand.MAIN_HAND, top(data.portal));
        helper.assertTrue(stack.getCount() == 1 && !hasty.completed.contains(SolsticioStoryRules.PORTAL), "A relic without the accord");
        home(helper, player);
        home(helper, second.player);
        home(helper, early.player);
      } finally {
        before.restore(server, data);
      }
    });
  }

  // ---- mission 11: the elections -----------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void electionsComeDaysAfterTheLiberationAndChangeWhatSolsticioSays(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var server = helper.getLevel().getServer();
      var data = SolsticioData.get(server);
      var before = WorldState.save(data);
      try (var cast = new Cast(helper); var qa = new QaPlayer(helper, "StoryElectionsQA")) {
        var player = qa.player;
        Villager aurelia = cast.character("mayor"), terra = cast.character("inventor");
        var campaign = arrive(player, RELIC_MISSIONS);
        campaign.completed.add(SolsticioStoryRules.ACCORD);
        campaign.completed.add(SolsticioStoryRules.PORTAL);
        // A fresh test world is younger than three days: the vote is driven with explicit game times.
        long freed = 1_000;
        data.liberated = true;
        data.electionsHeld = false;
        data.liberatedAt = freed;
        helper.assertTrue(!SolsticioStory.electionsIfDue(server, freed + SolsticioStoryRules.ELECTION_DELAY - SolsticioStoryRules.DAY / 2)
            && !data.electionsHeld, "The square voted too early");
        var talk = SolsticioStory.character(player, aurelia, "mayor");
        assertSaid(helper, talk, "mayor.campaign");
        helper.assertTrue(!campaign.completed.contains(SolsticioStoryRules.ELECTIONS), "Mission 11 before the vote");
        String line = key((Component) ((TranslatableContents) SolsticioCommerce.townsfolkLine(server.overworld().getRandom(),
            SolsticioStory.world(server)).getContents()).getArgs()[1]);
        helper.assertTrue(line.startsWith("entrelumen.solsticio.townsfolk.liberated."), "Neighbours ignore the liberation: " + line);

        long vote = freed + SolsticioStoryRules.ELECTION_DELAY;
        helper.assertTrue(SolsticioStory.electionsIfDue(server, vote) && data.electionsHeld && data.electionsAt == vote,
            "The square did not vote three days after the liberation");
        helper.assertTrue(!SolsticioStory.electionsIfDue(server, vote + 1), "The square voted twice");
        talk = SolsticioStory.character(player, aurelia, "mayor");
        assertSaid(helper, talk, "mayor.elected");
        assertSaid(helper, talk, "story.ending");
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.ELECTIONS), "Mission 11 was not recorded");
        assertSaid(helper, SolsticioStory.character(player, aurelia, "mayor"), "mayor.after");
        assertSaid(helper, SolsticioStory.character(player, terra, "inventor"), "inventor.after_elections");
        line = key((Component) ((TranslatableContents) SolsticioCommerce.townsfolkLine(server.overworld().getRandom(),
            SolsticioStory.world(server)).getContents()).getArgs()[1]);
        helper.assertTrue(line.startsWith("entrelumen.solsticio.townsfolk.elected."), "Neighbours ignore the elections: " + line);
      } finally {
        before.restore(server, data);
      }
    });
  }

  // ---- easter eggs ---------------------------------------------------------------------------

  private static long books(ServerPlayer player, String loreId) {
    return player.getInventory().items.stream().filter(stack -> stack.is(Items.WRITTEN_BOOK)
        && key(stack.get(DataComponents.CUSTOM_NAME)).equals("entrelumen.solsticio.lore." + loreId + ".title")).count();
  }

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void easterEggsGiveTheirPageOnceAndTogetherTheRumour(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var data = SolsticioData.get(helper.getLevel().getServer());
      var eggs = stageEggs(data);
      try (var qa = new QaPlayer(helper, "StoryEggsQA"); var other = new QaPlayer(helper, "StoryEggsOtherQA")) {
        var player = qa.player;
        var campaign = arrive(player);
        for (String egg : SolsticioStoryRules.EGGS) {
          BlockPos spot = data.commerce.easterEggs.get(egg).getFirst();
          toSolsticio(player, spot.above(8));
          helper.assertTrue(SolsticioStory.visit(player).keys().isEmpty(), "Found " + egg + " from far above");
          toSolsticio(player, spot.offset(2, 0, -1));
          assertSaid(helper, SolsticioStory.visit(player), "egg." + egg + ".found");
          helper.assertTrue(books(player, "egg_" + egg) == 1, "No lore page for " + egg);
          helper.assertTrue(SolsticioStory.visit(player).keys().isEmpty() && books(player, "egg_" + egg) == 1,
              "The " + egg + " gave its lore twice");
        }
        helper.assertTrue(campaign.completed.contains(SolsticioStoryRules.RUMOUR) && books(player, "rumour") == 1,
            "The three eggs did not reveal the rumour");
        long keepsakes = player.getInventory().items.stream().filter(stack -> key(stack.get(DataComponents.CUSTOM_NAME))
            .startsWith("entrelumen.solsticio.egg.")).count();
        helper.assertTrue(keepsakes == 3, "Keepsakes: " + keepsakes);
        // Another team finds its own page.
        arrive(other.player);
        toSolsticio(other.player, data.commerce.easterEggs.get("tavern").getFirst());
        assertSaid(helper, SolsticioStory.visit(other.player), "egg.tavern.found");
        home(helper, player);
        home(helper, other.player);
      } finally {
        restoreEggs(data, eggs);
      }
    });
  }

  // ---- Heliodor clothing ---------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void everyVillagerTheCitySpawnsWearsHeliodor(GameTestHelper helper) {
    var level = helper.getLevel();
    try (var cast = new Cast(helper)) {
      List<Villager> villagers = new ArrayList<>();
      villagers.add(cast.shop("bookstore"));
      villagers.add(cast.innkeeper(2));
      villagers.add(cast.npc(CommerceRules.Role.TOWNSFOLK, ""));
      villagers.add(cast.npc(CommerceRules.Role.NATIVE, "engineering"));
      for (String character : SolsticioStoryRules.CHARACTERS) villagers.add(cast.character(character));
      for (Villager villager : villagers)
        helper.assertTrue(villager.getVillagerData().getType() == SolsticioStory.HELIODOR.get(),
            SolsticioCommerce.role(villager) + " does not wear Heliodor");
      helper.assertTrue(villagers.getFirst().getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
          "The bookseller lost her profession");
      for (String character : SolsticioStoryRules.CHARACTERS) {
        Villager npc = villagers.get(4 + SolsticioStoryRules.CHARACTERS.indexOf(character));
        helper.assertTrue(npc.getVillagerData().getProfession() == SolsticioStory.CHARACTER_PROFESSIONS.get(character).get()
            && npc.isNoAi() && npc.isInvulnerable() && npc.isPersistenceRequired()
            && key(npc.getCustomName()).equals("entrelumen.solsticio.character." + character),
            character + " is not a named statue with its own profession");
        helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getKey(npc.getVillagerData().getProfession())
            .equals(ResourceLocation.fromNamespaceAndPath("entrelumen", character)), "Profession id of " + character);
      }
      // A villager the city spawned before the clothing existed changes when it loads.
      Villager old = EntityType.VILLAGER.create(level);
      BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
      old.moveTo(Vec3.atBottomCenterOf(pos));
      old.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.NITWIT, 1));
      var mark = new net.minecraft.nbt.CompoundTag();
      mark.putString("role", CommerceRules.Role.TOWNSFOLK.id);
      mark.putString("key", "");
      mark.putString("registry", SolsticioCommerce.LOCAL);
      old.getPersistentData().put(SolsticioCommerce.DATA, mark);
      old.addTag(SolsticioCommerce.TAG);
      level.addFreshEntity(old);
      cast.spawned.add(old);
      helper.assertTrue(old.getVillagerData().getType() == SolsticioStory.HELIODOR.get()
          && old.getVillagerData().getProfession() == VillagerProfession.NITWIT, "An old resident kept its plains clothes");
      // Villagers from elsewhere keep theirs.
      Villager visitor = EntityType.VILLAGER.create(level);
      visitor.moveTo(Vec3.atBottomCenterOf(pos));
      visitor.setVillagerData(new VillagerData(VillagerType.SWAMP, VillagerProfession.FARMER, 2));
      level.addFreshEntity(visitor);
      cast.spawned.add(visitor);
      SolsticioCommerce.settle(level, visitor);
      SolsticioStory.dress(level, visitor, SolsticioCommerce.data(visitor), CommerceRules.Role.MOVED);
      helper.assertTrue(visitor.getVillagerData().getType() == VillagerType.SWAMP, "A settled villager changed clothes");
    }
    helper.succeed();
  }
}
