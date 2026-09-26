package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Ark v2 on an isolated server: the astrolabe built from its definition in an empty 22 x 12 x 22 box
 * ({@code structure/ark.nbt}, never shipped), one Ark per team, the modules' global effects, the
 * beacons on the columns, the commands, the remote trade and what the first version leaves behind.
 * The fixture mod stands in for Rechiseled's polished amethyst and Iron's Spells' mana attributes.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsArk {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Set<String> ALL = Set.copyOf(ArkRules.moduleIds());

  private RuntimeGameTestsArk() {}

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
        ArkData.get(player.server).arks.remove(CampaignActions.campaignId(player));
        connection.disconnect(net.minecraft.network.chat.Component.literal("Ark QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  // ---- helpers -----------------------------------------------------------------------------------

  /** The controller's spot inside the Ark box: every part fits for every facing. */
  private static ArkMultiblock.Anchor anchor(GameTestHelper helper, int rotation) {
    return new ArkMultiblock.Anchor(helper.absolutePos(new BlockPos(10, 2, 10)), rotation);
  }

  private static BlockState state(ServerLevel level, ArkMultiblock.Part part, int rotation) {
    try {
      return BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), part.state(), false).blockState()
          .rotate(ArkMultiblock.rotation(rotation));
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
      throw new IllegalStateException(part.state(), invalid);
    }
  }

  /** Places the core, the chosen slots (the controller is a slot) and a number of beacons. */
  private static void build(GameTestHelper helper, ArkMultiblock.Anchor anchor, Set<String> slots, int beacons) {
    var level = helper.getLevel();
    int placedBeacons = 0;
    for (var part : ArkMultiblock.definition().parts()) {
      boolean beacon = ArkMultiblock.BEACON.equals(part.slot());
      if (beacon && placedBeacons >= beacons) continue;
      if (!beacon && !part.core() && !slots.contains(part.slot())) continue;
      level.setBlock(ArkMultiblock.world(anchor, part.offset()), state(level, part, anchor.rotation()), 3);
      if (beacon) placedBeacons++;
    }
  }

  private static BlockPos slot(ArkMultiblock.Anchor anchor, String slot) {
    return ArkMultiblock.world(anchor, ArkMultiblock.definition().slots().get(slot));
  }

  /** A standing Ark record for the player's team, far from any loaded chunk so no rescan touches it. */
  private static void fakeArk(ServerPlayer player, int beacons, ArkRules.Module... modules) {
    var ark = new ArkData.Ark(player.level().dimension(),
        new ArkMultiblock.Anchor(new BlockPos(29_000_000 - 64, 64, 29_000_000 - 64), 0));
    ark.core = true;
    ark.controller = true;
    ark.beacons = beacons;
    for (var module : modules) ark.modules.add(module.id);
    ArkData.get(player.server).arks.put(CampaignActions.campaignId(player), ark);
  }

  private static int command(ServerPlayer player, String command) {
    try {
      return player.server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
      return 0;
    }
  }

  // ---- the Ark and its team ----------------------------------------------------------------------

  @GameTest(template = "ark", timeoutTicks = 200)
  public static void oneArkPerTeamItsModulesNeedTheCoreAndTheController(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = ArkData.get(level.getServer());
    UUID team = UUID.randomUUID(), other = UUID.randomUUID();
    var anchor = anchor(helper, 3);
    try {
      build(helper, anchor, ALL, 0);
      // A module never founds an Ark: the controller marks where it is built.
      helper.assertTrue(ArkState.place(level, slot(anchor, "nature_module"), "nature_module", team)
          == ArkState.Placement.NEEDS_CONTROLLER && !data.arks.containsKey(team), "A module founded an Ark");
      var controllerState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:ark_controller")).defaultBlockState();
      level.setBlock(slot(anchor, ArkMultiblock.CONTROLLER), controllerState, 3);
      helper.assertTrue(ArkState.place(level, slot(anchor, ArkMultiblock.CONTROLLER), ArkMultiblock.CONTROLLER, team)
          == ArkState.Placement.FOUNDED, "The controller did not found the team's Ark");
      var ark = data.arks.get(team);
      helper.assertTrue(ark != null && ark.anchor.equals(anchor) && ark.core && ark.controller
          && ark.status().activeModules().size() == 6 && ark.status().level() == 1,
          "The whole Ark did not turn the six modules on, facing the way it was built: "
              + (ark == null ? "none" : ark.anchor + " " + ark.status()));
      // Without the controller no module works; the Ark stays the team's until it is put back.
      level.setBlock(slot(anchor, ArkMultiblock.CONTROLLER), Blocks.AIR.defaultBlockState(), 3);
      helper.assertTrue(data.arks.get(team) == ark && !ark.controller && ark.status().activeModules().isEmpty(),
          "Without the controller a module still worked");
      level.setBlock(slot(anchor, ArkMultiblock.CONTROLLER), controllerState, 3);
      helper.assertTrue(ark.controller && ark.status().activeModules().size() == 6,
          "Putting the controller back did not turn the modules on again");
      // Breaking a module turns off only its effect.
      level.setBlock(slot(anchor, "arcane_module"), Blocks.AIR.defaultBlockState(), 3);
      helper.assertTrue(!ark.status().active(ArkRules.Module.ARCANE) && ark.status().activeModules().size() == 5,
          "Breaking the Arcane Module did not switch off exactly its effect");
      // A missing core block stops the Ark; the rescan notices it without any hook.
      BlockPos core = ArkMultiblock.world(anchor, ArkMultiblock.definition().core().getFirst().offset());
      BlockState coreState = level.getBlockState(core);
      level.setBlock(core, Blocks.AIR.defaultBlockState(), 3);
      ArkState.rescan(level, ark, data);
      helper.assertTrue(!ark.core && ark.missingCore == 1 && ark.status().activeModules().isEmpty(),
          "A broken core left the modules working");
      level.setBlock(core, coreState, 3);
      ArkState.rescan(level, ark, data);
      helper.assertTrue(ark.core && ark.status().activeModules().size() == 5, "Repairing the core did not restore the Ark");
      // One Ark per team: another team cannot take it, and this team's pieces elsewhere do nothing.
      helper.assertTrue(ArkState.place(level, slot(anchor, ArkMultiblock.CONTROLLER), ArkMultiblock.CONTROLLER, other)
          == ArkState.Placement.OTHER_TEAM && !data.arks.containsKey(other), "Another team took this Ark");
      helper.assertTrue(ArkState.place(level, slot(anchor, "habitation_module"), "habitation_module", other)
          == ArkState.Placement.NEEDS_CONTROLLER && !data.arks.containsKey(other), "Another team's module took this Ark");
      helper.assertTrue(ArkState.place(level, helper.absolutePos(new BlockPos(1, 1, 1)).offset(200, 0, 0),
          "arcane_module", team) == ArkState.Placement.OTHER_ARK, "A second Ark counted for the same team");
      // Without controller and modules the Ark is forgotten, so the team may build again.
      for (String slotId : ArkRules.SLOTS) level.setBlock(slot(anchor, slotId), Blocks.AIR.defaultBlockState(), 3);
      helper.assertTrue(!data.arks.containsKey(team), "An empty Ark stayed registered");
    } finally {
      data.arks.remove(team);
      data.arks.remove(other);
    }
    helper.succeed();
  }

  @GameTest(template = "ark", timeoutTicks = 400, skyAccess = true)
  public static void beaconsOnTheColumnsWorkWithoutAPyramidAndRaiseTheLevel(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = ArkData.get(level.getServer());
    UUID team = UUID.randomUUID();
    var anchor = anchor(helper, 0);
    Set<String> slots = new java.util.HashSet<>(ALL);
    slots.add(ArkMultiblock.CONTROLLER);
    build(helper, anchor, slots, 1);
    helper.assertTrue(ArkState.place(level, slot(anchor, ArkMultiblock.CONTROLLER), ArkMultiblock.CONTROLLER, team)
        == ArkState.Placement.FOUNDED, "The controller did not found the Ark");
    var ark = data.arks.get(team);
    helper.assertTrue(ark.beacons == 1 && ark.status().level() == 2, "One beacon did not add one level: " + ark.status());
    BlockPos beaconPos = ArkMultiblock.world(anchor, ArkMultiblock.definition().beacons().getFirst());
    helper.assertTrue(ArkState.arkBeacon(level, beaconPos.getX(), beaconPos.getY(), beaconPos.getZ()),
        "The beacon place of a standing Ark is not recognised");
    helper.succeedWhen(() -> {
      try {
        var levels = BeaconBlockEntity.class.getDeclaredField("levels");
        levels.setAccessible(true);
        helper.assertTrue(level.getBlockEntity(beaconPos) instanceof BeaconBlockEntity beacon
            && levels.getInt(beacon) == 4, "The Ark's beacon did not reach level 4 without a pyramid");
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(failure);
      }
      data.arks.remove(team);
    });
  }

  // ---- the six effects ---------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void engineeringChargesEveryCarriedEnergyItemCheaply(GameTestHelper helper) {
    var battery = BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen_gametest_fixture:test_battery"));
    try (var qa = new QaPlayer(helper, "ArkCharge")) {
      var player = qa.player;
      for (int slot = 0; slot < 36; slot++) player.getInventory().items.set(slot, new ItemStack(battery));
      player.getInventory().armor.set(0, new ItemStack(battery));
      player.getInventory().offhand.set(0, new ItemStack(battery));
      var off = new ArkRules.Status(true, true, true, Set.of("nature_module"), 0);
      helper.assertTrue(ArkEffects.apply(player, off, true) == 0, "Charged without the Engineering Module");
      var on = new ArkRules.Status(true, true, true, Set.of("engineering_module"), 0);
      long start = System.nanoTime();
      int charged = ArkEffects.apply(player, on, true);
      long nanos = System.nanoTime() - start;
      // 0.5 % of 200,000 FE per second is 1,000 FE/s: 2,000 FE per two-second pass.
      int energy = player.getInventory().items.getFirst().getCapability(Capabilities.EnergyStorage.ITEM).getEnergyStored();
      helper.assertTrue(charged == 38 && energy == 2_000, "Charged " + charged + " items to " + energy + " FE");
      long best = Long.MAX_VALUE;
      for (int pass = 0; pass < 20; pass++) {
        long before = System.nanoTime();
        ArkEffects.apply(player, on, true);
        best = Math.min(best, System.nanoTime() - before);
      }
      LOGGER.info("ARK_PERF charging items=38 firstPassUs={} bestPassUs={} every={}ticks perTickUs={}",
          nanos / 1000, best / 1000, ArkEffects.PERIOD_TICKS, best / 1000.0 / ArkEffects.PERIOD_TICKS);
      helper.assertTrue(best < 5_000_000, "One charging pass of 38 items took " + best / 1000 + " us");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void arcaneRaisesManaByHalfPerLevelOnlyWhileActive(GameTestHelper helper) {
    var maxMana = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("irons_spellbooks:max_mana")).orElseThrow();
    try (var qa = new QaPlayer(helper, "ArkMana")) {
      var player = qa.player;
      double base = player.getAttributeValue(maxMana);
      ArkEffects.apply(player, new ArkRules.Status(true, true, true, Set.of("arcane_module"), 0), false);
      helper.assertTrue(Math.abs(player.getAttributeValue(maxMana) - base * 1.5) < 1e-6,
          "Level I did not add half the maximum mana: " + player.getAttributeValue(maxMana));
      ArkEffects.apply(player, new ArkRules.Status(true, true, true, Set.of("arcane_module"), 2), false);
      helper.assertTrue(Math.abs(player.getAttributeValue(maxMana) - base * 2.5) < 1e-6,
          "Level III did not add half per level: " + player.getAttributeValue(maxMana));
      ArkEffects.apply(player, ArkRules.Status.NONE, false);
      helper.assertTrue(Math.abs(player.getAttributeValue(maxMana) - base) < 1e-6, "The mana bonus outlived the module");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void natureGivesQuietRegenerationAndHalvesHunger(GameTestHelper helper) {
    try (var qa = new QaPlayer(helper, "ArkVital")) {
      var player = qa.player;
      var food = player.getFoodData();
      ArkEffects.apply(player, new ArkRules.Status(true, true, true, Set.of("nature_module"), 0), false);
      var regeneration = player.getEffect(MobEffects.REGENERATION);
      helper.assertTrue(regeneration != null && regeneration.isInfiniteDuration() && regeneration.isAmbient()
          && !regeneration.isVisible() && regeneration.getAmplifier() == 0,
          "Vitality is not an infinite, ambient Regeneration I without particles");
      food.setExhaustion(0);
      player.causeFoodExhaustion(1.0F);
      helper.assertTrue(Math.abs(food.getExhaustionLevel() - 0.5F) < 1e-6, "Hunger did not halve: " + food.getExhaustionLevel());
      // Two beacons: Regeneration III.
      ArkEffects.apply(player, new ArkRules.Status(true, true, true, Set.of("nature_module"), 2), false);
      helper.assertTrue(player.getEffect(MobEffects.REGENERATION).getAmplifier() == 2, "Beacons did not raise Regeneration");
      ArkEffects.apply(player, ArkRules.Status.NONE, false);
      food.setExhaustion(0);
      player.causeFoodExhaustion(1.0F);
      helper.assertTrue(player.getEffect(MobEffects.REGENERATION) == null && Math.abs(food.getExhaustionLevel() - 1.0F) < 1e-6,
          "Vitality outlived the module");
      // Someone else's effect is never removed: a potion stays.
      player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.REGENERATION, 200, 1));
      ArkEffects.apply(player, ArkRules.Status.NONE, false);
      helper.assertTrue(player.getEffect(MobEffects.REGENERATION) != null, "A potion's Regeneration was removed");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void logisticsMakesEveryVillagerSeeAHero(GameTestHelper helper) {
    try (var qa = new QaPlayer(helper, "ArkHero")) {
      var player = qa.player;
      ArkEffects.apply(player, new ArkRules.Status(true, true, true, Set.of("logistics_module"), 1), false);
      var hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
      helper.assertTrue(hero != null && hero.isInfiniteDuration() && !hero.isVisible() && hero.getAmplifier() == 1,
          "Logistics with one beacon is not Hero of the Village II");
      ArkEffects.apply(player, ArkRules.Status.NONE, false);
      helper.assertTrue(player.getEffect(MobEffects.HERO_OF_THE_VILLAGE) == null, "The hero outlived the module");
    }
    helper.succeed();
  }

  // ---- commands ----------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void homeIsOneWaitsFiveStillSecondsAndCoolsDown(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "ArkHome");
    var player = qa.player;
    var data = ArkData.get(player.server);
    BlockPos home = helper.absolutePos(new BlockPos(2, 0, 2));
    player.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    helper.assertTrue(command(player, "sethome") == 0 && !data.homes.containsKey(player.getUUID()),
        "/sethome worked without the Habitation Module");
    fakeArk(player, 0, ArkRules.Module.HABITATION);
    helper.assertTrue(command(player, "sethome") == 1 && data.homes.get(player.getUUID()).blockPos().equals(home),
        "/sethome did not keep the one home");
    BlockPos away = helper.absolutePos(new BlockPos(4, 0, 4)).offset(0, 0, 40);
    player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
    helper.assertTrue(command(player, "home") == 1 && ArkCommands.warming(player), "/home did not start its warm-up");
    player.teleportTo(away.getX() + 1.5, away.getY(), away.getZ() + 0.5);
    helper.runAfterDelay(2, () -> {
      helper.assertTrue(!ArkCommands.warming(player), "Moving did not cancel the trip home");
      helper.assertTrue(command(player, "home") == 1, "/home did not start again");
      helper.runAfterDelay(ArkRules.HOME_WARMUP_TICKS + 2, () -> {
        helper.assertTrue(player.blockPosition().equals(home), "The trip home did not arrive: " + player.blockPosition());
        helper.assertTrue(data.homeUsed.containsKey(player.getUUID()) && command(player, "home") == 0,
            "The fifteen-minute cooldown did not hold");
        data.homes.remove(player.getUUID());
        data.homeUsed.remove(player.getUUID());
        qa.close();
        helper.succeed();
      });
    });
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void rtpNeedsExplorationFindsASafePlaceInTheRingAndWaitsAnHour(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "ArkRtp");
    var player = qa.player;
    var data = ArkData.get(player.server);
    helper.assertTrue(command(player, "rtp") == 0, "/rtp worked without the Exploration Module");
    fakeArk(player, 0, ArkRules.Module.EXPLORATION);
    ArkEffects.apply(player, ArkState.status(player), false);
    helper.assertTrue(ArkEffects.traveller(player), "The traveller mark is missing");
    BlockPos spawn = helper.getLevel().getSharedSpawnPos();
    helper.assertTrue(command(player, "rtp") == 1 && command(player, "rtp") == 0, "/rtp did not start once");
    helper.succeedWhen(() -> {
      double distance = Math.hypot(player.getX() - spawn.getX(), player.getZ() - spawn.getZ());
      helper.assertTrue(distance >= ArkRules.RTP_MIN - 1 && distance <= ArkRules.RTP_MAX + 1,
          "Still searching or out of the ring: " + distance);
      helper.assertTrue(data.rtpUsed.containsKey(player.getUUID()) && command(player, "rtp") == 0,
          "The hour of cooldown did not hold");
      helper.assertTrue(ArkCommands.standable(player.serverLevel(), player.blockPosition()), "The arrival is not safe");
      data.rtpUsed.remove(player.getUUID());
      qa.close();
    });
  }

  // ---- remote trade ------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void logisticsOpensAKnownShopFromAfarAtItsCounter(GameTestHelper helper) {
    var level = helper.getLevel();
    var sites = new CommerceSites();
    BlockPos post = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(post.below(), Blocks.STONE.defaultBlockState());
    sites.rebuild(List.of(new CommerceSites.Found(CityLayout.Marker.SHOP, "bakery", post)), null);
    List<Villager> spawned = SolsticioCommerce.populateNow(level, sites, 0);
    var city = SolsticioData.get(level.getServer()).commerce;
    var site = sites.sites.getFirst();
    city.sites.add(site);
    try (var buyer = new QaPlayer(helper, "ArkTrade"); var rival = new QaPlayer(helper, "ArkRival")) {
      helper.assertTrue(spawned.size() == 1, "The bakery's shopkeeper did not spawn");
      Villager baker = spawned.getFirst();
      var player = buyer.player;
      String id = ArkRules.shopId("bakery", post.asLong());
      helper.assertTrue(ArkCommerce.catalog(player).isEmpty(), "An unvisited shop is in the catalog");
      ArkCommerce.know(player, "bakery", post.asLong());
      helper.assertTrue(ArkCommerce.catalog(player).equals(List.of(new ArkCommerce.Shop(id, "bakery", true))),
          "A shop the team talked to is not in its catalog");
      helper.assertTrue(!ArkCommerce.open(player, id), "The catalog opened without the Logistics Module");
      // From afar: the real counter, at the price the counter itself charges.
      player.teleportTo(post.getX() + 0.5, post.getY() + 60, post.getZ() + 40.5);
      helper.assertTrue(ArkCommerce.openLoaded(player, baker) && player.containerMenu instanceof MerchantMenu
          && baker.getTradingPlayer() == player, "The remote counter did not open");
      int remotePrice = baker.getOffers().getFirst().getCostA().getCount();
      helper.assertTrue(!ArkCommerce.openLoaded(rival.player, baker), "Two players shared one counter");
      player.closeContainer();
      helper.assertTrue(baker.getTradingPlayer() == null, "Closing the remote counter left the shopkeeper busy");
      player.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 2.5);
      player.interactOn(baker, net.minecraft.world.InteractionHand.MAIN_HAND);
      helper.assertTrue(player.containerMenu instanceof MerchantMenu
          && baker.getOffers().getFirst().getCostA().getCount() == remotePrice,
          "The remote price differs from the counter's");
      player.closeContainer();
      fakeArk(player, 0, ArkRules.Module.LOGISTICS);
      helper.assertTrue(ArkCommerce.open(player, id) && !ArkCommerce.open(rival.player, id),
          "The catalog gate does not follow the module and the team's knowledge");
      ArkCommerce.close(level.getServer(), player.getUUID());
      ArkData.get(level.getServer()).knownShops.remove(CampaignActions.campaignId(player));
    } finally {
      city.sites.remove(site);
      spawned.forEach(net.minecraft.world.entity.Entity::discard);
    }
    helper.succeed();
  }

  // ---- what the first version leaves behind ------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void unfinishedBatchesAndTheOldDepotComeBackWhole(GameTestHelper helper) {
    var level = helper.getLevel();
    var matrix = BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:routing_matrix"));
    try (var qa = new QaPlayer(helper, "ArkRefund")) {
      var player = qa.player;
      var campaign = Entrelumen.current(player);
      campaign.refunds.put("entrelumen:routing_matrix", 2);
      campaign.refunds.put("entrelumen:ration_bundle", 70);
      helper.assertTrue(ArkMigration.deliverRefunds(player) && campaign.refunds.isEmpty()
          && player.getInventory().countItem(matrix) == 2
          && player.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:ration_bundle"))) == 70,
          "The refund was not paid whole");
      helper.assertTrue(!ArkMigration.deliverRefunds(player), "The refund was paid twice");
    }
    BlockPos module = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(module, Entrelumen.LOGISTICS_MODULE.get().defaultBlockState());
    helper.assertTrue(level.getBlockEntity(module) instanceof LogisticsStock, "The old depot's block entity is gone");
    ((LogisticsStock) level.getBlockEntity(module)).setLegacyItem(0, new ItemStack(matrix, 5));
    ((LogisticsStock) level.getBlockEntity(module)).setLegacyItem(26, new ItemStack(Items.BREAD, 12));
    helper.succeedWhen(() -> {
      var stock = (LogisticsStock) level.getBlockEntity(module);
      var drops = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(module).inflate(2));
      int matrices = drops.stream().filter(item -> item.getItem().is(matrix)).mapToInt(item -> item.getItem().getCount()).sum();
      int bread = drops.stream().filter(item -> item.getItem().is(Items.BREAD)).mapToInt(item -> item.getItem().getCount()).sum();
      helper.assertTrue(stock.isEmpty() && matrices == 5 && bread == 12
          && drops.stream().allMatch(item -> item.getAge() == -32768),
          "The depot's stock did not come back whole and lasting");
      drops.forEach(net.minecraft.world.entity.Entity::discard);
    });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void aRetiredLodgingGivesTheHomeSpawnBack(GameTestHelper helper) {
    try (var qa = new QaPlayer(helper, "ArkLodging")) {
      var player = qa.player;
      BlockPos lodging = helper.absolutePos(new BlockPos(1, 1, 1));
      BlockPos home = helper.absolutePos(new BlockPos(3, 1, 3));
      player.setRespawnPosition(player.level().dimension(), lodging, 0, false, false);
      CompoundTag record = new CompoundTag();
      record.putInt("version", 1);
      record.put("reserved", spawn(player, lodging));
      record.put("home", spawn(player, home));
      record.putBoolean("pending_restore", false);
      record.putBoolean("pending_clone", false);
      CompoundTag persisted = new CompoundTag();
      persisted.put(ArkMigration.LODGING, record);
      persisted.put(ArkMigration.KIT, new CompoundTag());
      player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
      helper.assertTrue(ArkMigration.retireLodging(player) && home.equals(player.getRespawnPosition())
          && !player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(ArkMigration.LODGING)
          && !player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(ArkMigration.KIT),
          "The lodging did not give the home spawn back");
      helper.assertTrue(!ArkMigration.retireLodging(player), "The lodging was retired twice");
    }
    helper.succeed();
  }

  private static CompoundTag spawn(ServerPlayer player, BlockPos pos) {
    CompoundTag tag = new CompoundTag();
    tag.putString("dimension", player.level().dimension().location().toString());
    tag.putBoolean("has_position", true);
    tag.putIntArray("position", new int[] {pos.getX(), pos.getY(), pos.getZ()});
    tag.putFloat("angle", 0);
    tag.putBoolean("forced", false);
    return tag;
  }

  // ---- the activation ----------------------------------------------------------------------------

  @GameTest(template = "ark", timeoutTicks = 200)
  public static void theActivationNeedsTheWholeArkTheLastProjectAndTheEnd(GameTestHelper helper) {
    var level = helper.getLevel();
    var anchor = anchor(helper, 1);
    Set<String> slots = new java.util.HashSet<>(ALL);
    slots.add(ArkMultiblock.CONTROLLER);
    build(helper, anchor, slots, 0);
    try (var qa = new QaPlayer(helper, "ArkOpens")) {
      var player = qa.player;
      BlockPos controller = slot(anchor, ArkMultiblock.CONTROLLER);
      helper.assertTrue(ArkState.place(level, controller, ArkMultiblock.CONTROLLER, CampaignActions.campaignId(player))
          == ArkState.Placement.FOUNDED, "The controller did not found the team's Ark");
      var campaign = Entrelumen.current(player);
      campaign.act = CampaignMilestones.ARK_ACT;
      campaign.completed.add("world_network");
      player.teleportTo(controller.getX() + 0.5, controller.getY() + 1, controller.getZ() + 1.5);
      player.setShiftKeyDown(true);
      helper.assertTrue(!ArkActions.activate(player, controller) && !campaign.completed.contains(CampaignMilestones.LAST_HORIZON),
          "The Ark opened without the End journey");
      campaign.completed.add("end_arrival");
      level.setBlock(slot(anchor, "exploration_module"), Blocks.AIR.defaultBlockState(), 3);
      helper.assertTrue(!ArkActions.activate(player, controller), "The Ark opened without a module");
      level.setBlock(slot(anchor, "exploration_module"),
          BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:exploration_module")).defaultBlockState(), 3);
      helper.assertTrue(ArkActions.activate(player, controller) && campaign.completed.contains(CampaignMilestones.LAST_HORIZON)
          && campaign.act == Campaigns.FINAL_ACT && player.getInventory().countItem(Solsticio.LIGHT_KEY.get()) == 1,
          "The whole Ark did not open the light and forge the key");
      helper.assertTrue(!ArkActions.activate(player, controller) && player.getInventory().countItem(Solsticio.LIGHT_KEY.get()) == 1,
          "The activation replayed");
    }
    helper.succeed();
  }
}
