package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;

/** Development-only integration tests; excluded from the distributable jar. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTests {
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arcaneRestorationResetsForgingHistoryAndKeepsEverythingElse(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "arcane_module");
      // An early-campaign visitor using a gifted, heavily forged sword.
      var campaign = Entrelumen.current(player);
      campaign.act = 1;
      campaign.completed.clear();
      var data = CampaignData.get(player.server);
      var beforeCampaign = data.save(new CompoundTag(), helper.getLevel().registryAccess());
      var source = forgedSword(helper, 31);
      var expected = source.copy();
      expected.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, source);
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.BOOK, 7));
      player.giveExperienceLevels(30);
      var result = player.gameMode.useItemOn(player, helper.getLevel(), source,
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(result.consumesAction(), "Native interaction did not run");
      helper.assertTrue(ItemStack.matches(player.getMainHandItem(), expected)
          && player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, -1) == 0,
          "Restoration changed more than the forging history");
      helper.assertTrue(player.getOffhandItem().getCount() == 2 && player.experienceLevel == 5,
          "Five recorded operations did not cost exactly five books and 25 levels");
      helper.assertTrue(beforeCampaign.equals(data.save(new CompoundTag(), helper.getLevel().registryAccess())),
          "Restoration changed campaign state");
      // Replay: nothing left to restore, nothing paid.
      helper.assertTrue(!ArcaneRestoration.restore(player, module, net.minecraft.world.InteractionHand.MAIN_HAND)
          && player.getOffhandItem().getCount() == 2 && player.experienceLevel == 5, "Replay charged again");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arcaneRestorationRejectsWithoutPartialPayment(GameTestHelper helper) throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "arcane_module");
      var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
      var book = new ItemStack(Items.ENCHANTED_BOOK);
      book.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 7);
      player.setItemInHand(hand, book);
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.BOOK, 3));
      player.giveExperienceLevels(15);
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Enchanted book accepted");
      var sword = forgedSword(helper, 7);
      var original = sword.copy();
      player.setItemInHand(hand, sword);
      player.getOffhandItem().setCount(2);
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Insufficient books accepted");
      player.getOffhandItem().setCount(3);
      player.giveExperienceLevels(-1);
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Insufficient levels accepted");
      player.giveExperienceLevels(1);
      helper.assertTrue(!ArcaneRestoration.restore(player, module, net.minecraft.world.InteractionHand.OFF_HAND),
          "Wrong hand accepted");
      var missing = arkModule(helper, controller, "nature_module");
      var state = helper.getLevel().getBlockState(missing);
      helper.getLevel().removeBlock(missing, false);
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Incomplete Ark accepted");
      helper.getLevel().setBlockAndUpdate(missing, state);
      helper.getLevel().setBlockAndUpdate(controller.above(), helper.getLevel().getBlockState(controller));
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Ambiguous Ark accepted");
      helper.getLevel().removeBlock(controller.above(), false);
      player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Spectator accepted");
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.teleportTo(module.getX() + 30.0, module.getY(), module.getZ());
      helper.assertTrue(!ArcaneRestoration.restore(player, module, hand), "Remote interaction accepted");
      player.teleportTo(controller.getX() + 0.5, controller.getY() + 1.0, controller.getZ() + 0.5);
      helper.assertTrue(ItemStack.matches(original, player.getMainHandItem())
          && player.getOffhandItem().getCount() == 3 && player.experienceLevel == 15,
          "A rejected request partially mutated the item, books or levels");
      player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      helper.assertTrue(ArcaneRestoration.restore(player, module, hand) && player.getOffhandItem().isEmpty()
          && player.experienceLevel == 0
          && player.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, -1) == 0,
          "Exact payment failed or creative did not pay");
    }
    helper.succeed();
  }

  private static ItemStack forgedSword(GameTestHelper helper, int repairCost) {
    var sword = new ItemStack(Items.DIAMOND_SWORD);
    var registry = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
    var enchantments = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
    enchantments.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
    enchantments.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
    enchantments.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE), 1);
    sword.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    sword.set(net.minecraft.core.component.DataComponents.REPAIR_COST, repairCost);
    sword.set(net.minecraft.core.component.DataComponents.DAMAGE, 321);
    sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Borrowed history"));
    sword.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
        List.of(net.minecraft.network.chat.Component.literal("Forged by many hands"))));
    var custom = new CompoundTag();
    custom.putString("provenance", "gifted-qa");
    sword.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
    return sword;
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void engineeringWorkshopRepairsGiftedToolsWithoutCampaignOrComponentChanges(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "engineering_module");
      // A recipient at the start of the story can use gifted infrastructure.
      var campaign = Entrelumen.current(player);
      campaign.act = 1;
      campaign.completed.clear();
      var data = CampaignData.get(player.server);
      var beforeCampaign = data.save(new CompoundTag(), helper.getLevel().registryAccess());
      var tool = new ItemStack(Items.DIAMOND_PICKAXE);
      tool.setDamageValue(500);
      tool.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
          net.minecraft.network.chat.Component.literal("Gift from another horizon"));
      tool.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 31);
      tool.enchant(helper.getLevel().registryAccess().lookupOrThrow(
          net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(
              net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 3));
      player.giveExperienceLevels(7);
      int levels = player.experienceLevel;
      var expected = tool.copy();
      expected.setDamageValue(500 - tool.getMaxDamage() / 4);
      var used = player.gameMode.useItemOn(player, helper.getLevel(), tool,
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(used.consumesAction() && ItemStack.matches(tool, expected)
          && player.getOffhandItem().getCount() == 2, "Native interaction did not pay exactly one material per quarter repair");
      player.gameMode.useItemOn(player, helper.getLevel(), tool,
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      expected.setDamageValue(0);
      helper.assertTrue(ItemStack.matches(tool, expected) && player.getOffhandItem().getCount() == 1,
          "Partial final repair changed components or material cost");
      player.gameMode.useItemOn(player, helper.getLevel(), tool,
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(ItemStack.matches(tool, expected) && player.getOffhandItem().getCount() == 1
          && player.experienceLevel == levels, "Full-durability replay spent material or XP");
      helper.assertTrue(beforeCampaign.equals(data.save(new CompoundTag(), helper.getLevel().registryAccess())),
          "Workshop changed campaign state or rewarded a milestone");
      // Native repair ingredients differ by equipment, not an Entrelumen whitelist.
      for (var pair : List.of(new Item[] {Items.LEATHER_CHESTPLATE, Items.LEATHER},
          new Item[] {Items.ELYTRA, Items.PHANTOM_MEMBRANE}, new Item[] {Items.IRON_PICKAXE, Items.IRON_INGOT})) {
        var equipment = new ItemStack(pair[0]);
        equipment.setDamageValue(1);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, equipment);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(pair[1]));
        helper.assertTrue(EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND)
            && equipment.getDamageValue() == 0 && player.getOffhandItem().isEmpty(),
            "Native equipment repair failed: " + pair[0]);
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void engineeringWorkshopRejectsInvalidStructureAndUseWithoutConsumption(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "engineering_module");
      var tool = new ItemStack(Items.IRON_PICKAXE);
      tool.setDamageValue(100);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 3));
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND)
          && player.getOffhandItem().getCount() == 3, "Wrong material was accepted");
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 3));
      var missing = arkModule(helper, controller, "nature_module");
      var missingState = helper.getLevel().getBlockState(missing);
      helper.getLevel().removeBlock(missing, false);
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND),
          "Incomplete Ark repaired a tool");
      helper.getLevel().setBlockAndUpdate(missing, missingState);
      var duplicate = controller.above();
      helper.getLevel().setBlockAndUpdate(duplicate, helper.getLevel().getBlockState(controller));
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND),
          "Ambiguous controllers repaired a tool");
      helper.getLevel().removeBlock(duplicate, false);
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.OFF_HAND),
          "Offhand repaired a tool");
      player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND),
          "Spectator repaired a tool");
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.teleportTo(module.getX() + 30.0, module.getY(), module.getZ());
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND),
          "Remote use repaired a tool");
      player.teleportTo(controller.getX() + 0.5, controller.getY() + 1.0, controller.getZ() + 0.5);
      tool.setCount(2);
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND),
          "Stacked damageable items repaired for a single payment");
      tool.setCount(1);
      helper.assertTrue(tool.getDamageValue() == 100 && player.getOffhandItem().getCount() == 3,
          "Rejected repair changed tool or material");
      player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      player.gameMode.useItemOn(player, helper.getLevel(), tool,
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(tool.getDamageValue() == 100 - tool.getMaxDamage() / 4
          && player.getOffhandItem().getCount() == 2, "Creative repair bypassed material cost");
      helper.getLevel().removeBlock(module, false);
      helper.assertTrue(!EngineeringWorkshop.repair(player, module, net.minecraft.world.InteractionHand.MAIN_HAND)
          && player.getOffhandItem().getCount() == 2, "Removed workshop still consumed material");
    }
    helper.succeed();
  }

  private static final class WorkshopPlayer implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    WorkshopPlayer(GameTestHelper helper) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), "WorkshopQA"), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
          cookie.gameProfile(), cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      try {
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    public void close() {
      try {
        connection.disconnect(net.minecraft.network.chat.Component.literal("Workshop QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }


  @GameTest(template = "empty", timeoutTicks = 200)
  public static void habitationBedClickChecksInAndOutWithoutChangingCampaign(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var level = helper.getLevel();
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "habitation_module");
      var missing = arkModule(helper, controller, "nature_module");
      var campaign = Entrelumen.current(player);
      campaign.act = 1;
      campaign.completed.clear();
      var home = controller.offset(12, 0, 0);
      player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, home, 37.5F, true, false);
      var bed = new ItemStack(Items.RED_BED);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bed);
      var before = CampaignData.get(player.server).save(new CompoundTag(), level.registryAccess());

      var missingState = level.getBlockState(missing);
      level.removeBlock(missing, false);
      habitationClick(player, module, false);
      helper.assertTrue(home.equals(player.getRespawnPosition()) && !habitationBooked(player),
          "Incomplete physical Ark registered lodging");
      level.setBlockAndUpdate(missing, missingState);

      var overhead = new HashMap<net.minecraft.core.BlockPos,
          net.minecraft.world.level.block.state.BlockState>();
      for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
        var pos = module.offset(dx, 1, dz);
        overhead.put(pos, level.getBlockState(pos));
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      }
      habitationClick(player, module, false);
      helper.assertTrue(home.equals(player.getRespawnPosition()) && player.isRespawnForced()
          && !habitationBooked(player) && bed.getCount() == 1,
          "Fully obstructed lodging changed home or reserved a false spawn");
      overhead.forEach(level::setBlockAndUpdate);

      habitationClick(player, module, false);
      helper.assertTrue(module.equals(player.getRespawnPosition())
          && !player.isRespawnForced() && habitationBooked(player) && bed.getCount() == 1,
          "Native bed click failed to register non-forced lodging");
      var booking = player.getPersistentData().copy();
      habitationClick(player, module, false);
      helper.assertTrue(booking.equals(player.getPersistentData()) && bed.getCount() == 1,
          "Repeated check-in changed backup or consumed bed");
      level.removeBlock(missing, false);
      habitationClick(player, module, true);
      helper.assertTrue(home.equals(player.getRespawnPosition())
          && player.getRespawnDimension().equals(net.minecraft.world.level.Level.OVERWORLD)
          && Float.compare(player.getRespawnAngle(), 37.5F) == 0
          && player.isRespawnForced() && !habitationBooked(player) && bed.getCount() == 1,
          "Checkout with incomplete Ark failed to restore original spawn tuple");
      helper.assertTrue(campaign.act == 1 && campaign.completed.isEmpty()
          && before.equals(CampaignData.get(player.server).save(new CompoundTag(), level.registryAccess())),
          "Lodging changed campaign progress or rewards");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void habitationUsesNativeNonForcedRespawnAndRejectsUnsafeSpaces(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var level = helper.getLevel();
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "habitation_module");
      var unrelated = arkModule(helper, controller, "arcane_module");
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BLUE_BED));
      habitationClick(player, module, false);
      var initial = player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
      helper.assertTrue(!initial.missingRespawnBlock() && initial.newLevel() == level
          && !player.isRespawnForced()
          && net.minecraft.core.BlockPos.containing(initial.pos()).equals(module.above()),
          "Vanilla did not select safe space over lodging");
      level.removeBlock(unrelated, false);
      helper.assertTrue(!player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).missingRespawnBlock(),
          "Respawn required other Ark modules again");
      for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
        if (dx != 0 || dz != 0)
          level.setBlockAndUpdate(module.offset(dx, 0, dz),
              net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(module.above(),
          net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
      helper.assertTrue(player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).missingRespawnBlock(),
          "Native respawn accepted fluid feet");
      level.setBlockAndUpdate(module.above(),
          net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      helper.assertTrue(player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).missingRespawnBlock(),
          "Native respawn accepted obstructed space");
      var north = module.north();
      level.setBlockAndUpdate(north.below(),
          net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(north, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(north.above(),
          net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
      var lateral = player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
      helper.assertTrue(!lateral.missingRespawnBlock()
          && net.minecraft.core.BlockPos.containing(lateral.pos()).equals(north),
          "Vanilla did not select the safe lateral exit");
      level.removeBlock(module, false);
      helper.assertTrue(player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).missingRespawnBlock(),
          "Removed module still provided a respawn");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void habitationBrokenModuleFallsBackToNativeBedAndSurvivesClone(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var level = helper.getLevel();
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "habitation_module");
      var home = controller.offset(8, 0, 0);
      habitationTestBed(level, home, net.minecraft.core.Direction.EAST,
          net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState());
      player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, home, 19.0F, false, false);
      helper.assertTrue(!player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).missingRespawnBlock(),
          "Original bed fixture is not a native respawn");
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
          new ItemStack(Items.GREEN_BED));
      habitationClick(player, module, false);
      level.removeBlock(module, false);
      var missing = player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
      helper.assertTrue(missing.missingRespawnBlock(), "Vanilla did not detect missing lodging");
      var event = new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(
          player, missing, false);
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
      helper.assertTrue(!event.getDimensionTransition().missingRespawnBlock()
          && event.getDimensionTransition().newLevel() == level
          && event.copyOriginalSpawnPosition() && home.equals(player.getRespawnPosition())
          && Float.compare(player.getRespawnAngle(), 19.0F) == 0 && !player.isRespawnForced(),
          "Respawn event did not restore original native bed");
      var fresh = new ServerPlayer(player.server, level, player.getGameProfile(),
          player.clientInformation());
      fresh.connection = player.connection;
      fresh.restoreFrom(player, false);
      fresh.copyRespawnPosition(player);
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
          new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(fresh, false));
      helper.assertTrue(home.equals(fresh.getRespawnPosition()) && !habitationBooked(fresh),
          "Clone/post-respawn lost the home or retained stale booking");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void habitationNativeBedReplacingModuleRestoresOriginalHome(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var level = helper.getLevel();
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "habitation_module");
      var home = controller.offset(8, 0, 0);
      habitationTestBed(level, home, net.minecraft.core.Direction.EAST,
          net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState());
      player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, home, 13.0F, false, false);
      helper.assertTrue(!player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING).missingRespawnBlock(),
          "Original bed fixture is not a native respawn");
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BLUE_BED));
      habitationClick(player, module, false);
      habitationTestBed(level, module, net.minecraft.core.Direction.NORTH,
          net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState());
      var replacement = player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
      helper.assertTrue(!replacement.missingRespawnBlock()
          && level.getBlockState(module).is(net.minecraft.world.level.block.Blocks.RED_BED),
          "Same-coordinate replacement is not a valid native bed");
      var event = new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(
          player, replacement, false);
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
      var expectedHome = player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
      helper.assertTrue(home.equals(player.getRespawnPosition())
          && Float.compare(player.getRespawnAngle(), 13.0F) == 0
          && !player.isRespawnForced() && event.copyOriginalSpawnPosition()
          && !expectedHome.missingRespawnBlock()
          && event.getDimensionTransition().pos().equals(expectedHome.pos())
          && !event.getDimensionTransition().pos().equals(replacement.pos()),
          "Replacement native bed stole lodging instead of restoring original home");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void habitationNewBedWinsAndBookingSurvivesPlayerSerialization(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "habitation_module");
      var home = controller.offset(10, 0, 0);
      player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, home, 27.0F, true, false);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
          new ItemStack(Items.YELLOW_BED));
      habitationClick(player, module, false);
      var saved = player.saveWithoutId(new CompoundTag());
      var fresh = new ServerPlayer(player.server, helper.getLevel(), player.getGameProfile(),
          player.clientInformation());
      fresh.connection = player.connection;
      fresh.load(saved);
      helper.assertTrue(module.equals(fresh.getRespawnPosition()) && !fresh.isRespawnForced()
          && habitationBooked(fresh), "Player save/load lost lodging or native spawn");
      ArkHabitation.onLogin(
          new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(fresh));
      helper.assertTrue(habitationBooked(fresh), "Reconnect discarded active booking");
      fresh.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
          new ItemStack(Items.YELLOW_BED));
      fresh.teleportTo(module.getX() + 0.5, module.getY() + 1.0, module.getZ() + 0.5);
      fresh.setShiftKeyDown(true);
      helper.assertTrue(ArkHabitation.use(fresh, module,
          net.minecraft.world.InteractionHand.MAIN_HAND, true)
          && home.equals(fresh.getRespawnPosition())
          && Float.compare(fresh.getRespawnAngle(), 27.0F) == 0
          && fresh.isRespawnForced() && !habitationBooked(fresh),
          "Saved booking could not restore original tuple");
      var newerBed = controller.offset(14, 0, 0);
      player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD,
          newerBed, 64.0F, false, false);
      ArkHabitation.onLogin(
          new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
      helper.assertTrue(newerBed.equals(player.getRespawnPosition())
          && Float.compare(player.getRespawnAngle(), 64.0F) == 0
          && !habitationBooked(player), "Newer native bed lost to stale Ark booking");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void habitationCanceledSpawnSettersKeepBackupAndRecoverClone(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "habitation_module");
      var home = controller.offset(11, 0, 0);
      player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, home, 48.0F, true, false);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
          new ItemStack(Items.ORANGE_BED));
      var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
      java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent>
          rejectEntry = event -> {
            if (event.getEntity() == player && module.equals(event.getNewSpawn()))
              event.setCanceled(true);
          };
      bus.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
          net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent.class, rejectEntry);
      try {
        habitationClick(player, module, false);
        helper.assertTrue(home.equals(player.getRespawnPosition()) && !habitationBooked(player),
            "Canceled check-in changed home or saved false booking");
      } finally {
        bus.unregister(rejectEntry);
      }
      habitationClick(player, module, false);
      java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent>
          rejectExit = event -> {
            if (event.getEntity() == player && home.equals(event.getNewSpawn()))
              event.setCanceled(true);
          };
      bus.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
          net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent.class, rejectExit);
      try {
        habitationClick(player, module, true);
        helper.assertTrue(module.equals(player.getRespawnPosition()) && habitationBooked(player),
            "Canceled checkout discarded active backup");
      } finally {
        bus.unregister(rejectExit);
      }
      player.setShiftKeyDown(false);
      var selected = player.findRespawnPositionAndUseSpawnBlock(false,
          net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
      helper.assertTrue(!selected.missingRespawnBlock(), "Fixture lodging is not native spawn");
      bus.post(new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(
          player, selected, false));
      var fresh = new ServerPlayer(player.server, helper.getLevel(), player.getGameProfile(),
          player.clientInformation());
      fresh.connection = player.connection;
      fresh.restoreFrom(player, false);
      var firstCopy = new java.util.concurrent.atomic.AtomicBoolean(true);
      java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent>
          rejectClone = event -> {
            if (event.getEntity() == fresh && module.equals(event.getNewSpawn())
                && firstCopy.getAndSet(false)) event.setCanceled(true);
          };
      bus.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
          net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent.class, rejectClone);
      try {
        fresh.copyRespawnPosition(player);
        helper.assertTrue(fresh.getRespawnPosition() == null && habitationBooked(fresh),
            "Canceled clone setter lost persisted backup");
        bus.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(
            fresh, false));
        helper.assertTrue(module.equals(fresh.getRespawnPosition())
            && !fresh.isRespawnForced() && habitationBooked(fresh),
            "Post-respawn failed to recover canceled clone setter");
      } finally {
        bus.unregister(rejectClone);
      }

      var vetoCopy = new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(
          player, selected, false);
      vetoCopy.setCopyOriginalSpawnPosition(false);
      bus.post(vetoCopy);
      var noCopy = new ServerPlayer(player.server, helper.getLevel(), player.getGameProfile(),
          player.clientInformation());
      noCopy.connection = player.connection;
      noCopy.restoreFrom(player, false);
      bus.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(
          noCopy, false));
      helper.assertTrue(noCopy.getRespawnPosition() == null && !habitationBooked(noCopy),
          "Another listener's copy veto was overridden by habitation recovery");
    }
    helper.succeed();
  }

  private static void habitationClick(ServerPlayer player, net.minecraft.core.BlockPos module,
      boolean crouched) {
    player.setShiftKeyDown(crouched);
    var result = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
    if (!result.consumesAction())
      throw new IllegalStateException("Native habitation block interaction was not consumed");
  }

  private static boolean habitationBooked(ServerPlayer player) {
    return player.getPersistentData().getCompound(
        net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
        .contains("entrelumen:habitation");
  }

  private static void habitationTestBed(net.minecraft.server.level.ServerLevel level,
      net.minecraft.core.BlockPos foot, net.minecraft.core.Direction facing,
      net.minecraft.world.level.block.state.BlockState bed) {
    var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
    var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    level.setBlock(foot.below(), stone, 2);
    level.setBlock(foot.relative(facing).below(), stone, 2);
    for (var side : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
      var exit = foot.relative(side);
      level.setBlock(exit.below(), stone, 2);
      level.setBlock(exit, air, 2);
      level.setBlock(exit.above(), air, 2);
    }
    bed = bed.setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
    level.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
        net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
    level.setBlock(foot.relative(facing),
        bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
            net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arkFieldJournalsReadCurrentTeamWithoutMutation(GameTestHelper helper)
      throws Exception {
    var reader = player(helper, "JournalReader");
    var outsider = player(helper, "JournalOther");
    var controller = ark(helper, reader);
    var personal = Entrelumen.current(reader);
    personal.completed.addAll(Set.of("spectral_archive", "sealed_memory", "atlas_voices",
        "nursery_protocol", "horizon_survey", "aether_arrival", "travellers_table"));
    personal.arkPhase = 2;
    personal.arkDeposits.put("entrelumen:ecosystem_capsule", 1);
    var other = Entrelumen.current(outsider);
    other.act = 6;
    var team = FTBTeamsAPI.api().getManager().createPartyTeam(reader,
        "Journals " + reader.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var shared = Entrelumen.current(reader);
    reader.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(Items.DIAMOND, 3));
    var inventory = Entrelumen.availableMaterials(reader);
    var data = CampaignData.get(reader.server);
    var before = data.save(new CompoundTag(), helper.getLevel().registryAccess());
    var dirty = data.isDirty();
    var blocks = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
    for (var kind : ArkFieldJournals.Kind.values()) {
      var module = arkModule(helper, controller, kind.module());
      blocks.put(module, helper.getLevel().getBlockState(module));
      reader.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
      var result = reader.gameMode.useItemOn(reader, helper.getLevel(), reader.getMainHandItem(),
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(result.consumesAction(), "Field journal did not respond: " + kind.module());
      var snapshot = ArkFieldJournals.snapshot(reader, module, kind);
      var outsiderSnapshot = ArkFieldJournals.snapshot(outsider, module, kind);
      helper.assertTrue(snapshot.player().equals(reader.getUUID())
          && snapshot.campaign().equals(team.getId()) && snapshot.kind() == kind
          && !snapshot.campaign().equals(outsiderSnapshot.campaign())
          && !snapshot.lines().equals(outsiderSnapshot.lines())
          && snapshot.lines().getFirst().getContents()
              instanceof net.minecraft.network.chat.contents.TranslatableContents,
          "Journal packet lost team identity, distinct observations or translation keys");
      var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
          io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
      try {
        JournalBookNetwork.Snapshot.CODEC.encode(buffer, snapshot);
        var decoded = JournalBookNetwork.Snapshot.CODEC.decode(buffer);
        helper.assertTrue(decoded.equals(snapshot)
            && decoded.lines().getFirst().getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents,
            "Journal packet changed team identity or translated narrative on the wire");
      } finally {
        buffer.release();
      }
    }
    var oversized = new JournalBookNetwork.Snapshot(reader.getUUID(), team.getId(),
        ArkFieldJournals.Kind.ARCANE, List.of(net.minecraft.network.chat.Component.literal(
            "x".repeat(33 * 1024))));
    var oversizedBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(
        io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
    try {
      boolean rejected = false;
      try {
        JournalBookNetwork.Snapshot.CODEC.encode(oversizedBuffer, oversized);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      helper.assertTrue(rejected, "Oversized journal payload escaped the wire limit");
    } finally {
      oversizedBuffer.release();
    }
    helper.assertTrue(ArkFieldJournals.view(shared, ArkFieldJournals.Kind.ARCANE).narrative()
            == ArkFieldJournals.Narrative.RECORDED
        && ArkFieldJournals.view(other, ArkFieldJournals.Kind.ARCANE).narrative()
            == ArkFieldJournals.Narrative.EMPTY
        && ArkFieldJournals.view(shared, ArkFieldJournals.Kind.NATURE).materials().getFirst()
            .deposited() == 1
        && ArkFieldJournals.view(shared, ArkFieldJournals.Kind.EXPLORATION).journeys().stream()
            .filter(ArkFieldJournals.Evidence::recorded).count() == 1
        && Entrelumen.availableMaterials(reader).equals(inventory)
        && data.isDirty() == dirty
        && data.save(new CompoundTag(), helper.getLevel().registryAccess()).equals(before)
        && blocks.entrySet().stream().allMatch(e -> helper.getLevel().getBlockState(e.getKey()).equals(e.getValue()))
        && CampaignActions.campaignId(reader).equals(team.getId()),
        "Journal read consumed supplies, changed team progress or confused recorded journeys");
    var arcane = arkModule(helper, controller, "arcane_module");
    reader.teleportTo(arcane.getX() + 20.5, arcane.getY() + 1, arcane.getZ() + 0.5);
    helper.assertTrue(!ArkFieldJournals.inspect(reader, arcane), "Remote journal bypassed reach");
    reader.teleportTo(arcane.getX() + 0.5, arcane.getY() + 1, arcane.getZ() + 0.5);
    reader.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    helper.assertTrue(!ArkFieldJournals.inspect(reader, arcane), "Spectator read a journal");
    reader.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    helper.getLevel().removeBlock(arcane, false);
    helper.assertTrue(!ArkFieldJournals.inspect(reader, arcane), "Removed journal still responded");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arkFieldJournalsDepositOnlyTheirOwnBatches(GameTestHelper helper)
      throws Exception {
    var keeper = player(helper, "JournalKeeper");
    var outsider = player(helper, "JournalGuest");
    var controller = ark(helper, keeper);
    var team = FTBTeamsAPI.api().getManager().createPartyTeam(keeper,
        "Journal deposit " + keeper.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var campaign = Entrelumen.current(keeper);
    campaign.act = 6;
    campaign.completed.addAll(CampaignMilestones.MODULE_IDS);
    var other = Entrelumen.current(outsider);
    other.act = 6;
    other.completed.addAll(CampaignMilestones.MODULE_IDS);
    keeper.setShiftKeyDown(true);
    outsider.setShiftKeyDown(true);
    var data = CampaignData.get(keeper.server);
    for (var kind : ArkFieldJournals.Kind.values()) {
      var module = arkModule(helper, controller, kind.module());
      keeper.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
      outsider.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
      var requirement = ArkCommissioning.STEPS.get(kind.step()).requirements().entrySet()
          .iterator().next();
      var supply = arkItem(requirement.getKey().substring("entrelumen:".length()));
      int required = requirement.getValue();
      campaign.arkPhase = kind.step() - 1;
      campaign.arkDeposits.clear();
      keeper.getInventory().setItem(1, ItemStack.EMPTY);
      keeper.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
          new ItemStack(supply, required > 1 ? 1 : 2));
      int before = keeper.getOffhandItem().getCount();
      var wrongPhase = keeper.gameMode.useItemOn(keeper, helper.getLevel(),
          keeper.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(wrongPhase.consumesAction() && campaign.arkPhase == kind.step() - 1
          && campaign.arkDeposits.isEmpty() && keeper.getOffhandItem().getCount() == before,
          "An earlier Ark batch was charged through " + kind.module());

      campaign.arkPhase = kind.step();
      outsider.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
          new ItemStack(supply, required));
      helper.assertTrue(!ArkActions.depositFromJournal(outsider, team.getId(), module, kind)
          && outsider.getOffhandItem().getCount() == required
          && other.arkDeposits.isEmpty(),
          "A foreign campaign deposited through " + kind.module());
      var first = keeper.gameMode.useItemOn(keeper, helper.getLevel(),
          keeper.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(first.consumesAction() && keeper.getOffhandItem().getCount()
          == (required > 1 ? 0 : 1)
          && campaign.arkPhase == (required > 1 ? kind.step() : kind.step() + 1)
          && campaign.arkDeposits.equals(required > 1
              ? Map.of(requirement.getKey(), 1) : Map.of()),
          "Journal did not take only the available offhand quantity: " + kind.module());
      var restored = CampaignData.load(data.save(new CompoundTag(),
          helper.getLevel().registryAccess()), helper.getLevel().registryAccess())
          .campaigns.parties.get(team.getId());
      helper.assertTrue(restored != null && restored.arkPhase == campaign.arkPhase
          && restored.arkDeposits.equals(campaign.arkDeposits),
          "Journal deposit did not persist: " + kind.module());
      if (required > 1) {
        keeper.getInventory().setItem(1, new ItemStack(supply, required));
        var finish = keeper.gameMode.useItemOn(keeper, helper.getLevel(),
            keeper.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
        helper.assertTrue(finish.consumesAction() && campaign.arkPhase == kind.step() + 1
            && campaign.arkDeposits.isEmpty() && keeper.getInventory().getItem(1).getCount() == 1,
            "Journal top-up consumed surplus or advanced the wrong batch: " + kind.module());
      }
      var remaining = Entrelumen.availableMaterials(keeper);
      var replay = keeper.gameMode.useItemOn(keeper, helper.getLevel(),
          keeper.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
      helper.assertTrue(replay.consumesAction() && campaign.arkPhase == kind.step() + 1
          && campaign.arkDeposits.isEmpty()
          && Entrelumen.availableMaterials(keeper).equals(remaining)
          && !campaign.completed.contains(CampaignMilestones.LAST_HORIZON),
          "Journal replay consumed supplies or activated the ending: " + kind.module());
    }
    helper.assertTrue(other.arkPhase == 0 && other.arkDeposits.isEmpty(),
        "Journal deposits leaked into another team's campaign");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arkFieldJournalDepositNeedsAUniqueCompleteReachableArk(GameTestHelper helper) {
    var reader = player(helper, "JournalGuard");
    var controller = ark(helper, reader);
    var module = arkModule(helper, controller, "arcane_module");
    var campaign = Entrelumen.current(reader);
    campaign.arkPhase = ArkFieldJournals.Kind.ARCANE.step();
    reader.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    reader.setShiftKeyDown(true);
    reader.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("containment_seal"), 2));
    var id = CampaignActions.campaignId(reader);
    var extra = module.above();
    helper.getLevel().setBlockAndUpdate(extra, helper.getLevel().getBlockState(controller));
    helper.assertTrue(!ArkActions.depositFromJournal(reader, id, module,
        ArkFieldJournals.Kind.ARCANE) && reader.getOffhandItem().getCount() == 2,
        "Ambiguous controllers accepted a journal deposit");
    helper.getLevel().removeBlock(extra, false);
    var missing = arkModule(helper, controller, "nature_module");
    var state = helper.getLevel().getBlockState(missing);
    helper.getLevel().removeBlock(missing, false);
    helper.assertTrue(!ArkActions.depositFromJournal(reader, id, module,
        ArkFieldJournals.Kind.ARCANE) && reader.getOffhandItem().getCount() == 2,
        "Missing physical module accepted a journal deposit");
    helper.getLevel().setBlockAndUpdate(missing, state);
    reader.teleportTo(module.getX() + 20.5, module.getY() + 1, module.getZ() + 0.5);
    helper.assertTrue(!ArkActions.depositFromJournal(reader, id, module,
        ArkFieldJournals.Kind.ARCANE), "Remote journal deposited supplies");
    reader.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    reader.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    helper.assertTrue(!ArkActions.depositFromJournal(reader, id, module,
        ArkFieldJournals.Kind.ARCANE), "Spectator deposited through a journal");
    reader.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    helper.assertTrue(campaign.arkPhase == 1 && campaign.arkDeposits.isEmpty()
        && reader.getOffhandItem().getCount() == 2,
        "Rejected journal interactions mutated inventory or batch");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void logisticsModuleDepositsCurrentBatchFromTeamInventoryOnce(GameTestHelper helper)
      throws Exception {
    var player = player(helper, "LogisticsMain");
    var outsider = player(helper, "LogisticsOther");
    var controller = ark(helper, player);
    var module = arkModule(helper, controller, "logistics_module");
    player.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    outsider.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    var personal = Entrelumen.current(player);
    personal.arkDeposits.put("entrelumen:calibration_frame", 1);
    var other = Entrelumen.current(outsider);
    other.act = 6;
    other.completed.addAll(Entrelumen.MODULES);
    other.arkDeposits.put("entrelumen:power_regulator", 1);
    var team = FTBTeamsAPI.api().getManager().createPartyTeam(player,
        "Logistics " + player.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var campaign = Entrelumen.current(player);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 2));
    player.getInventory().setItem(1, new ItemStack(arkItem("power_regulator"), 3));
    player.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 5));
    var blocks = Map.of(module, helper.getLevel().getBlockState(module),
        controller, helper.getLevel().getBlockState(controller));
    var materials = Entrelumen.availableMaterials(player);
    var ordinary = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
    helper.assertTrue(ordinary.consumesAction() && Entrelumen.availableMaterials(player).equals(materials)
        && campaign.arkDeposits.equals(Map.of("entrelumen:calibration_frame", 1)),
        "Ordinary logistics inspection consumed or changed the current batch");

    outsider.setShiftKeyDown(true);
    outsider.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 4));
    helper.assertTrue(!ArkActions.depositFromModule(outsider, team.getId(), module, 0)
        && outsider.getOffhandItem().getCount() == 4
        && other.arkDeposits.equals(Map.of("entrelumen:power_regulator", 1)),
        "Foreign campaign identity entered the team's logistics ledger");

    player.setShiftKeyDown(true);
    var accepted = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
    helper.assertTrue(accepted.consumesAction() && campaign.arkPhase == 0
        && campaign.arkDeposits.equals(Map.of("entrelumen:calibration_frame", 3,
            "entrelumen:power_regulator", 2))
        && player.getOffhandItem().isEmpty()
        && player.getInventory().getItem(1).getCount() == 1
        && player.getInventory().countItem(Items.DIAMOND) == 5,
        "Crouched logistics click failed to cap the partial batch to exact player inventory");
    var restored = CampaignData.load(CampaignData.get(player.server).save(new CompoundTag(),
        helper.getLevel().registryAccess()), helper.getLevel().registryAccess())
        .campaigns.parties.get(team.getId());
    helper.assertTrue(restored != null && restored.arkPhase == 0
        && restored.arkDeposits.equals(campaign.arkDeposits),
        "Partial logistics batch did not survive SavedData round trip");

    player.getInventory().setItem(3, new ItemStack(arkItem("calibration_frame"), 4));
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
    helper.assertTrue(campaign.arkPhase == 1 && campaign.arkDeposits.isEmpty()
        && player.getInventory().getItem(3).getCount() == 3,
        "Top-up did not advance exactly one batch and retain surplus");
    var afterTopup = Entrelumen.availableMaterials(player);
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
    helper.assertTrue(campaign.arkPhase == 1 && campaign.arkDeposits.isEmpty()
        && Entrelumen.availableMaterials(player).equals(afterTopup)
        && personal.arkDeposits.equals(Map.of("entrelumen:calibration_frame", 1))
        && other.arkDeposits.equals(Map.of("entrelumen:power_regulator", 1)),
        "Replay consumed surplus or crossed personal/team boundaries");
    campaign.arkPhase = ArkCommissioning.STEPS.size();
    campaign.completed.addAll(Set.of("world_network", "end_arrival"));
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(module));
    helper.assertTrue(!campaign.completed.contains(CampaignMilestones.LAST_HORIZON)
        && campaign.arkPhase == ArkCommissioning.STEPS.size()
        && Entrelumen.availableMaterials(player).equals(afterTopup)
        && blocks.entrySet().stream().allMatch(e -> helper.getLevel().getBlockState(e.getKey()).equals(e.getValue())),
        "Logistics click activated the ending, consumed supplies or changed the structure");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void logisticsModuleRejectsAmbiguousMissingRemoteAndSpectator(GameTestHelper helper) {
    var player = player(helper, "LogisticsGuard");
    var controller = ark(helper, player);
    var module = arkModule(helper, controller, "logistics_module");
    player.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    player.setShiftKeyDown(true);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 4));
    var campaign = Entrelumen.current(player);
    var id = CampaignActions.campaignId(player);
    var extra = module.above();
    helper.getLevel().setBlockAndUpdate(extra, helper.getLevel().getBlockState(controller));
    helper.assertTrue(!ArkActions.depositFromModule(player, id, module, 0)
        && player.getOffhandItem().getCount() == 4 && campaign.arkDeposits.isEmpty(),
        "Ambiguous controllers accepted a logistics deposit");
    helper.getLevel().removeBlock(extra, false);
    var missing = arkModule(helper, controller, "nature_module");
    var state = helper.getLevel().getBlockState(missing);
    helper.getLevel().removeBlock(missing, false);
    helper.assertTrue(!ArkActions.depositFromModule(player, id, module, 0)
        && player.getOffhandItem().getCount() == 4 && campaign.arkDeposits.isEmpty(),
        "Incomplete Ark accepted a logistics deposit");
    helper.getLevel().setBlockAndUpdate(missing, state);
    player.teleportTo(module.getX() + 20.5, module.getY() + 1, module.getZ() + 0.5);
    helper.assertTrue(!ArkActions.depositFromModule(player, id, module, 0),
        "Remote logistics interaction bypassed reach");
    player.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    helper.assertTrue(!ArkActions.depositFromModule(player, id, module, 0),
        "Spectator deposited through logistics");
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    var denied = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
        player, net.minecraft.world.InteractionHand.MAIN_HAND, module, arkHit(module));
    denied.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
    LogisticsModuleBlock.allowCrouchedUse(denied);
    helper.assertTrue(denied.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE,
        "Logistics hook overrode another mod's explicit denial");
    helper.assertTrue(campaign.arkPhase == 0 && campaign.arkDeposits.isEmpty()
        && player.getOffhandItem().getCount() == 4,
        "Denied logistics interactions consumed supplies or changed progress");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void engineeringModuleInspectionIsReadOnlyAndTeamScoped(GameTestHelper helper)
      throws Exception {
    var engineer = player(helper, "EngInspector");
    var outsider = player(helper, "EngOutsider");
    var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
    var controller = pos.offset(1, 0, 0);
    helper.getLevel().setBlockAndUpdate(pos,
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:engineering_module"))
            .defaultBlockState());
    helper.getLevel().setBlockAndUpdate(controller,
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:ark_controller"))
            .defaultBlockState());
    engineer.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    outsider.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    var personal = Entrelumen.current(engineer);
    personal.act = 6;
    personal.completed.addAll(Set.of("world_network", "engineering_module"));
    personal.arkDeposits.put("entrelumen:calibration_frame", 1);
    var other = Entrelumen.current(outsider);
    other.act = 6;
    other.arkDeposits.put("entrelumen:calibration_frame", 3);
    var team = FTBTeamsAPI.api().getManager().createPartyTeam(engineer,
        "Engineering " + engineer.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var shared = Entrelumen.current(engineer);
    engineer.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 4));
    var supplies = Entrelumen.availableMaterials(engineer);
    var data = CampaignData.get(engineer.server);
    var before = data.save(new CompoundTag(), helper.getLevel().registryAccess());
    var dirty = data.isDirty();
    var state = helper.getLevel().getBlockState(pos);
    for (int i = 0; i < 2; i++) {
      var result = engineer.gameMode.useItemOn(engineer, helper.getLevel(),
          engineer.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
      helper.assertTrue(result.consumesAction(), "Engineering module empty-hand interaction failed");
    }
    helper.assertTrue(EngineeringDiagnostics.currentReadOnly(engineer) == shared
        && EngineeringDiagnostics.currentReadOnly(outsider) == other
        && EngineeringDiagnostics.campaignView(shared).materials().getFirst().deposited() == 1
        && EngineeringDiagnostics.campaignView(other).materials().getFirst().deposited() == 3
        && EngineeringDiagnostics.physicalView(helper.getLevel(), pos).state()
            == EngineeringDiagnostics.ControllerState.FOUND
        && Entrelumen.availableMaterials(engineer).equals(supplies)
        && data.isDirty() == dirty
        && data.save(new CompoundTag(), helper.getLevel().registryAccess()).equals(before)
        && helper.getLevel().getBlockState(pos).equals(state)
        && personal.arkDeposits.equals(Map.of("entrelumen:calibration_frame", 1))
        && team.getId().equals(CampaignActions.campaignId(engineer)),
        "Repeated inspection changed supplies, progress, party identity or physical structure");
    engineer.teleportTo(pos.getX() + 20.5, pos.getY() + 1, pos.getZ() + 0.5);
    helper.assertTrue(!EngineeringDiagnostics.inspect(engineer, pos),
        "Remote engineering inspection bypassed reach");
    engineer.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    engineer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    helper.assertTrue(!EngineeringDiagnostics.inspect(engineer, pos),
        "Spectator inspected the engineering module");
    engineer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    helper.getLevel().removeBlock(pos, false);
    helper.assertTrue(!EngineeringDiagnostics.inspect(engineer, pos),
        "Removed engineering module remained interactable");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void actSixDeliveriesUseCrossModCostsAndRewardOnce(GameTestHelper helper) {
    var player = player(helper, "ActSixCosts");
    var campaign = Entrelumen.current(player);
    campaign.act = 6;
    for (String id : Entrelumen.MODULES.stream().sorted().toList()) {
      campaign.completed.remove("world_network");
      var project = Projects.all().get(id);
      helper.assertTrue(project != null, "Act VI project missing: " + id);
      player.getInventory().clearContent();
      project.items().forEach((item, count) -> player.getInventory().add(
          new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)), count)));
      var supplied = Entrelumen.availableMaterials(player);
      helper.assertTrue(command(player, "entrelumen deliver " + id) == 0
          && Entrelumen.availableMaterials(player).equals(supplied),
          "Module delivery bypassed World Network: " + id);
      campaign.completed.add("world_network");
      if (id.equals("exploration_module")) {
        helper.assertTrue(command(player, "entrelumen deliver " + id) == 0
            && Entrelumen.availableMaterials(player).equals(supplied),
            "Exploration delivery bypassed End observation");
        campaign.completed.add("end_arrival");
      }
      helper.assertTrue(command(player, "entrelumen deliver " + id) == 1,
          "Cross-mod module delivery rejected: " + id);
      helper.assertTrue(campaign.completed.contains(id)
          && player.getInventory().countItem(arkItem(id)) == 1,
          "Module delivery failed to credit one reward: " + id);
      for (String item : project.items().keySet())
        helper.assertTrue(Entrelumen.availableMaterials(player).getOrDefault(item, 0) == 0,
            "Module delivery did not consume exact input: " + item);
      var remaining = Entrelumen.availableMaterials(player);
      helper.assertTrue(command(player, "entrelumen deliver " + id) == 0
          && Entrelumen.availableMaterials(player).equals(remaining)
          && player.getInventory().countItem(arkItem(id)) == 1,
          "Repeated module delivery consumed or rewarded again: " + id);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void lastHorizonNeedsCurrentCampaignReachAndPhysicalArk(GameTestHelper helper) {
    var player = player(helper, "LastHorizon");
    var intruder = player(helper, "OtherTeam");
    var pos = ark(helper, player);
    var campaign = Entrelumen.current(player);
    campaign.arkPhase = 6;
    campaign.completed.add("world_network");
    player.setShiftKeyDown(true);
    intruder.setShiftKeyDown(true);
    var id = CampaignActions.campaignId(player);
    helper.assertTrue(!ArkActions.activate(player, id, pos)
        && !campaign.completed.contains(CampaignMilestones.LAST_HORIZON),
        "End observation was bypassed");
    campaign.completed.add("end_arrival");
    helper.assertTrue(!ArkActions.activate(player, UUID.randomUUID(), pos)
        && !ArkActions.activate(intruder, id, pos),
        "Stale or foreign campaign activated the Ark");
    var removedPos = pos.offset(-1, 0, 1);
    var module = helper.getLevel().getBlockState(removedPos);
    helper.getLevel().removeBlock(removedPos, false);
    helper.assertTrue(!ArkActions.activate(player, id, pos),
        "Missing physical module activated the Ark");
    helper.getLevel().setBlockAndUpdate(removedPos, module);
    player.teleportTo(pos.getX() + 20.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    helper.assertTrue(!ArkActions.activate(player, id, pos),
        "Out-of-reach controller activated the Ark");
    player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    var materials = Entrelumen.availableMaterials(player);
    player.setShiftKeyDown(true);
    var clicked = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    // Activation forges the team's one Light Key (act VI) and consumes nothing.
    var forged = new HashMap<>(materials);
    forged.merge("entrelumen:light_key", 1, Integer::sum);
    helper.assertTrue(clicked.consumesAction()
        && campaign.completed.contains(CampaignMilestones.LAST_HORIZON)
        && campaign.arkPhase == 6 && campaign.arkDeposits.isEmpty()
        && Entrelumen.availableMaterials(player).equals(forged),
        "Explicit empty-hand controller activation failed, consumed supplies or forged no single Light Key");
    var completed = Set.copyOf(campaign.completed);
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(campaign.completed.equals(completed)
        && Entrelumen.availableMaterials(player).equals(forged)
        && helper.getLevel().getBlockState(removedPos).equals(module),
        "Finished controller replay changed campaign, supplies, blocks or forged a second key");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void surveyStationBookmarksWithoutCampaignOrLodestone(GameTestHelper helper) {
    var player = player(helper, "SurveyTest");
    var level = helper.getLevel();
    var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
    var station = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:survey_station"));
    level.setBlockAndUpdate(pos, station.defaultBlockState());
    var compass = new ItemStack(Items.COMPASS, 2);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, compass);
    var before = Entrelumen.current(player).completed.size();
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var hit = new net.minecraft.world.phys.BlockHitResult(
        net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
    var firstAttempt = player.gameMode.useItemOn(player, level, player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(!firstAttempt.consumesAction()
        && !compass.has(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER),
        "Empty main hand consumed the click before the offhand compass attempt");
    var secondAttempt = player.gameMode.useItemOn(player, level, compass,
        net.minecraft.world.InteractionHand.OFF_HAND, hit);
    helper.assertTrue(secondAttempt.consumesAction(), "Offhand compass did not consume its attempt");
    var tracker = compass.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
    helper.assertTrue(tracker != null && !tracker.tracked()
        && tracker.target().orElseThrow().equals(net.minecraft.core.GlobalPos.of(level.dimension(), pos))
        && compass.getCount() == 2, "Station failed to bind the actual held compass without consumption");
    level.removeBlock(pos, false);
    compass.getItem().inventoryTick(compass, level, player, 0, false);
    helper.assertTrue(compass.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER).equals(tracker)
        && Entrelumen.current(player).completed.size() == before,
        "Bookmark disappeared after removal or interaction completed story");
    var shape = station.defaultBlockState().getShape(level, pos);
    helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,
        net.minecraft.world.level.block.Block.box(7, 0, 7, 9, 2, 9),
        net.minecraft.world.phys.shapes.BooleanOp.AND), "Station has invisible solid space below shelf");
    var drops = net.minecraft.world.level.block.Block.getDrops(station.defaultBlockState(), level,
        pos, null, player, new ItemStack(Items.IRON_PICKAXE));
    helper.assertTrue(drops.size() == 1 && drops.getFirst().is(station.asItem())
        && drops.getFirst().getCount() == 1 && !station.defaultBlockState().hasBlockEntity(),
        "Station does not drop exactly one item or unexpectedly has a block entity");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void registeredRecipesCraftAtlas(GameTestHelper helper) {
    for (String id :
        List.of("atlas", "raw_lens", "survey_notes", "signal_core", "ark_controller")) {
      ResourceLocation key = ResourceLocation.fromNamespaceAndPath("entrelumen", id);
      helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key), "Missing item: " + id);
      helper.assertTrue(
          helper.getLevel().getRecipeManager().byKey(key).isPresent(), "Missing recipe: " + id);
    }
    for (String id : Entrelumen.MODULES)
      helper.assertTrue(
          BuiltInRegistries.BLOCK.containsKey(
              ResourceLocation.fromNamespaceAndPath("entrelumen", id)),
          "Missing module block: " + id);
    var input =
        CraftingInput.of(
            2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(Items.COPPER_INGOT)));
    var recipe =
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    helper.assertTrue(recipe.isPresent(), "Atlas ingredients do not match a crafting recipe");
    var output = recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess());
    helper.assertTrue(
        BuiltInRegistries.ITEM.getKey(output.getItem()).toString().equals("entrelumen:atlas")
            && output.getCount() == 1,
        "Crafting did not produce one Atlas");
    helper.succeed();
  }

  private static ServerPlayer player(GameTestHelper helper, String name) {
    if (name.length() > 16) throw new IllegalArgumentException("GameTest profile exceeds 16 characters: " + name);
    var cookie =
        net.minecraft.server.network.CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), name), false);
    ServerPlayer player =
        new ServerPlayer(
            helper.getLevel().getServer(),
            helper.getLevel(),
            cookie.gameProfile(),
            cookie.clientInformation());
    var connection =
        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    // Full packs can grant starter supplies on login; each test defines its own inventory.
    player.getInventory().clearContent();
    return player;
  }

  private static int command(ServerPlayer player, String command) {
    try {
      return player
          .server
          .getCommands()
          .getDispatcher()
          .execute(command, player.createCommandSourceStack());
    } catch (Exception e) {
      throw new IllegalStateException("Player command failed", e);
    }
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void deliveryConsumesServerInventoryOnce(GameTestHelper helper) {
    var player = player(helper, "DeliveryTest");
    player.getInventory().add(new ItemStack(Items.BOOK, 2));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 3));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 1, "First delivery rejected");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1
            && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
        "Delivery did not consume exact materials");
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 0, "Repeated delivery accepted");
    helper.assertTrue(
        player
                .getInventory()
                .countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")))
            == 1,
        "Atlas reward missing or duplicated");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1
            && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
        "Repeated delivery consumed inventory");
    helper.assertTrue(
        Entrelumen.current(player).completed.contains("atlas_awakened"),
        "Delivery did not persist milestone");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void savedDataDiskRoundTrip(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    var data = CampaignData.get(server);
    UUID id = UUID.randomUUID();
    var original = data.campaigns.personal(id);
    original.act = 5;
    original.completed.add("field_survey");
    original.completed.addAll(List.of("resilient_backbone", "renewal_engine", "settlement_supply"));
    Expeditions.record(original, "aether:the_aether");
    Expeditions.record(original, "twilightforest:twilight_forest");
    Expeditions.record(original, "the_bumblezone:the_bumblezone");
    Expeditions.record(original, "minecraft:the_end");
    original.arkPhase = 2;
    original.arkDeposits.put("entrelumen:ecosystem_capsule", 1);
    UUID endingId = UUID.randomUUID();
    var ending = data.campaigns.personal(endingId);
    ending.act = 6;
    ending.completed.addAll(Entrelumen.MODULES);
    ending.completed.addAll(List.of("world_network", "end_arrival"));
    ending.arkPhase = 6;
    helper.assertTrue(CampaignMilestones.finish(ending), "Ending fixture failed");
    data.setDirty();
    server.overworld().getDataStorage().save();
    var path =
        server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/entrelumen_campaigns.dat");
    helper.succeedWhen(
        () -> {
          try {
            var tag =
                net.minecraft.nbt.NbtIo.readCompressed(
                    path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var loaded =
                CampaignData.load(tag.getCompound("data"), helper.getLevel().registryAccess());
            var restored = loaded.campaigns.personal(id);
            var restoredEnding = loaded.campaigns.personal(endingId);
            helper.assertTrue(
                restored.act == 5
                    && restored.arkPhase == 2
                    && restored.arkDeposits.equals(Map.of("entrelumen:ecosystem_capsule", 1))
                    && restored.completed.contains("field_survey")
                    && restored.completed.containsAll(
                        List.of("resilient_backbone", "renewal_engine", "settlement_supply"))
                    && restored.completed.containsAll(Expeditions.IDS)
                    && restoredEnding.arkPhase == 6
                    && restoredEnding.completed.contains(CampaignMilestones.LAST_HORIZON)
                    && !CampaignMilestones.finish(restoredEnding)
                    && !Expeditions.record(restored, "aether:the_aether"),
                "Persisted campaign did not survive disk read");
          } catch (java.io.IOException e) {
            helper.fail("SavedData IO has not completed: " + e.getMessage());
          }
        });
  }

  private static net.minecraft.core.BlockPos ark(GameTestHelper helper, ServerPlayer player) {
    var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
    helper.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(
        ResourceLocation.parse("entrelumen:ark_controller")).defaultBlockState());
    int index = 0;
    for (String module : Entrelumen.MODULES) {
      helper.getLevel().setBlockAndUpdate(pos.offset(index % 3 - 1, 0, index / 3 + 1),
          BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:" + module)).defaultBlockState());
      index++;
    }
    player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var campaign = Entrelumen.current(player);
    campaign.act = 6;
    campaign.completed.addAll(Entrelumen.MODULES);
    return pos;
  }

  private static net.minecraft.world.phys.BlockHitResult arkHit(net.minecraft.core.BlockPos pos) {
    return new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
        net.minecraft.core.Direction.UP, pos, false);
  }

  private static net.minecraft.core.BlockPos arkModule(GameTestHelper helper,
      net.minecraft.core.BlockPos controller, String id) {
    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:" + id));
    for (var pos : net.minecraft.core.BlockPos.betweenClosed(controller.offset(-1, 0, 1),
        controller.offset(1, 0, 2)))
      if (helper.getLevel().getBlockState(pos).is(block)) return pos.immutable();
    throw new IllegalStateException("Ark fixture lacks " + id);
  }

  private static Item arkItem(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:" + id));
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arkControllerPartialOffhandTopupAndReplay(GameTestHelper helper) {
    var player = player(helper, "ArkDepositTest");
    var pos = ark(helper, player);
    var campaign = Entrelumen.current(player);
    var id = CampaignActions.campaignId(player);
    var blocks = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
    net.minecraft.core.BlockPos.betweenClosed(pos.offset(-1, 0, 0), pos.offset(1, 0, 2))
        .forEach(p -> blocks.put(p.immutable(), helper.getLevel().getBlockState(p)));
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 2));
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(player.getOffhandItem().getCount() == 2 && campaign.arkPhase == 0
        && campaign.arkDeposits.isEmpty(), "Ordinary controller inspection consumed supplies");
    player.setShiftKeyDown(true);
    var result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(result.consumesAction() && player.getOffhandItem().isEmpty()
        && campaign.arkPhase == 0
        && campaign.arkDeposits.equals(Map.of("entrelumen:calibration_frame", 2))
        && CampaignData.get(player.server).isDirty(), "Crouched offhand partial deposit failed");
    var restored = CampaignData.load(CampaignData.get(player.server).save(new CompoundTag(),
        helper.getLevel().registryAccess()), helper.getLevel().registryAccess()).campaigns.personal(id);
    helper.assertTrue(restored.arkDeposits.equals(campaign.arkDeposits) && restored.arkPhase == 0,
        "Partial controller ledger did not survive SavedData serialization");
    player.getInventory().setItem(1, new ItemStack(arkItem("calibration_frame"), 5));
    player.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 3));
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("power_regulator"), 4));
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(campaign.arkPhase == 1 && campaign.arkDeposits.isEmpty()
        && player.getInventory().getItem(1).getCount() == 3 && player.getOffhandItem().getCount() == 2
        && player.getInventory().countItem(Items.DIAMOND) == 3,
        "Topup did not consume exactly the outstanding cost and preserve surplus");
    player.getInventory().setItem(3, new ItemStack(arkItem("containment_seal"), 2));
    helper.assertTrue(!ArkActions.deposit(player, id, pos, 0) && campaign.arkPhase == 1
        && campaign.arkDeposits.isEmpty() && player.getInventory().getItem(3).getCount() == 2
        && player.getInventory().getItem(1).getCount() == 3 && player.getOffhandItem().getCount() == 2,
        "Stale expected step consumed supplies");
    // Explicit acceptance costs, independent of the production commissioning table.
    var remainingSteps = List.of(Map.entry("containment_seal", 2),
        Map.entry("ecosystem_capsule", 2), Map.entry("routing_matrix", 2),
        Map.entry("ration_bundle", 8), Map.entry("horizon_chart", 1));
    int expectedPhase = 1;
    for (var step : remainingSteps) {
      player.getInventory().setItem(3, new ItemStack(arkItem(step.getKey()), step.getValue() + 3));
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      player.setShiftKeyDown(true);
      player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
          net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
      expectedPhase++;
      helper.assertTrue(campaign.arkPhase == expectedPhase && campaign.arkDeposits.isEmpty()
          && player.getInventory().getItem(3).is(arkItem(step.getKey()))
          && player.getInventory().getItem(3).getCount() == 3,
          "Controller did not complete the exact cost and preserve surplus for " + step.getKey());
    }
    var inventoryBeforeCompletedClick = Entrelumen.availableMaterials(player);
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(campaign.arkPhase == 6 && campaign.arkDeposits.isEmpty()
        && Entrelumen.availableMaterials(player).equals(inventoryBeforeCompletedClick)
        && player.getInventory().getItem(1).getCount() == 3
        && player.getInventory().getItem(3).getCount() == 3
        && player.getOffhandItem().getCount() == 2
        && player.getInventory().countItem(Items.DIAMOND) == 3,
        "Completed controller click changed phase, ledger or remaining materials");
    helper.assertTrue(blocks.entrySet().stream().allMatch(e ->
        helper.getLevel().getBlockState(e.getKey()).equals(e.getValue())),
        "Commissioning changed source blocks");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arkMissingModuleAndDeniedHookPreserveSupplies(GameTestHelper helper) {
    var player = player(helper, "ArkDeniedTest");
    var pos = ark(helper, player);
    player.setShiftKeyDown(true);
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 4));
    var denied = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
        player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, arkHit(pos));
    denied.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
    ArkControllerBlock.allowEmptyHandDeposit(denied);
    helper.assertTrue(denied.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE,
        "Controller hook overwrote explicit block denial");
    var canceled = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
        player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, arkHit(pos));
    canceled.setCanceled(true);
    var before = canceled.getUseBlock();
    ArkControllerBlock.allowEmptyHandDeposit(canceled);
    helper.assertTrue(canceled.isCanceled() && canceled.getUseBlock() == before,
        "Controller hook overrode cancellation");
    helper.getLevel().removeBlock(pos.offset(-1, 0, 1), false);
    helper.assertTrue(ArkActions.missingModules(player, pos).size() == 1,
        "Fixture must lack exactly one placed module");
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(player.getOffhandItem().getCount() == 4
        && Entrelumen.current(player).arkPhase == 0 && Entrelumen.current(player).arkDeposits.isEmpty(),
        "Missing physical module permitted a deposit");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void arkPartyLedgerRejectsStaleIdentityAndRestoresPersonal(GameTestHelper helper)
      throws Exception {
    var founder = player(helper, "ArkFounder");
    var guest = player(helper, "ArkGuest");
    var pos = ark(helper, founder);
    guest.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    var personal = Entrelumen.current(founder);
    personal.arkDeposits.put("entrelumen:calibration_frame", 1);
    var guestPersonal = Entrelumen.current(guest);
    guestPersonal.act = 6;
    guestPersonal.completed.addAll(Entrelumen.MODULES);
    guestPersonal.arkDeposits.put("entrelumen:power_regulator", 1);
    var team = (dev.ftb.mods.ftbteams.data.PartyTeam) FTBTeamsAPI.api().getManager()
        .createPartyTeam(founder, "Ark " + founder.getUUID(), "",
            dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    founder.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 2));
    helper.assertTrue(!ArkActions.deposit(founder, founder.getUUID(), pos, 0)
        && founder.getOffhandItem().getCount() == 2, "Stale personal identity accepted after party creation");
    guest.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 2));
    helper.assertTrue(!ArkActions.deposit(guest, team.getId(), pos, 0)
        && guest.getOffhandItem().getCount() == 2
        && guestPersonal.arkDeposits.equals(Map.of("entrelumen:power_regulator", 1)),
        "Nonmember could target another team's ledger");
    team.invite(founder, List.of(guest.getGameProfile()));
    team.join(guest);
    helper.assertTrue(ArkActions.deposit(founder, team.getId(), pos, 0)
        && Entrelumen.current(guest).arkDeposits.equals(Map.of("entrelumen:calibration_frame", 3))
        && personal.arkDeposits.equals(Map.of("entrelumen:calibration_frame", 1)),
        "Team deposit did not share progress or mutated founder snapshot");
    team.leave(guest.getUUID());
    guest.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(arkItem("calibration_frame"), 2));
    helper.assertTrue(Entrelumen.current(guest).arkDeposits.equals(Map.of("entrelumen:power_regulator", 1))
        && !ArkActions.deposit(guest, team.getId(), pos, 0) && guest.getOffhandItem().getCount() == 2,
        "Leaving lost personal ledger or accepted stale party identity");
    team.leave(founder.getUUID());
    helper.assertTrue(Entrelumen.current(founder).arkDeposits.equals(Map.of("entrelumen:calibration_frame", 1))
        && CampaignData.get(founder.server).campaigns.parties.get(team.getId()).archived,
        "Dissolution did not preserve personal ledger and archive the party");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void questMirrorCannotGrantCampaign(GameTestHelper helper) {
    var player = player(helper, "QuestMirrorTest");
    var file = ServerQuestFile.getInstance().orElseThrow();
    var chapter = new Chapter(0x7E110001L, file, file.getDefaultChapterGroup());
    var quest = new Quest(0x7E110002L, chapter);
    chapter.addQuest(quest);
    var task = new CampaignTask(0x7E110003L, quest);
    quest.addTask(task);
    var tag = new CompoundTag();
    tag.putString("milestone", "atlas_awakened");
    task.readData(tag, helper.getLevel().registryAccess());
    var teamData = file.getOrCreateTeamData(player);
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 0, "Unfinished milestone appeared completed");
    player.getInventory().add(new ItemStack(Items.BOOK));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 1, "Delivery rejected");
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 1, "Delivered milestone did not mirror");
    Entrelumen.current(player).completed.remove("atlas_awakened");
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 0, "Stale quest progress was not reset");
    helper.assertTrue(
        teamData.getCompletedTime(task.id).isEmpty()
            && teamData.getCompletedTime(quest.id).isEmpty(),
        "Stale completion timestamps still unlock quest dependencies");
    helper.assertTrue(
        !Entrelumen.current(player).completed.contains("atlas_awakened"),
        "Quest projection granted campaign progress");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void realPartyLifecyclePreservesPersonalSnapshots(GameTestHelper helper)
      throws Exception {
    var founder = player(helper, "FounderTest");
    var guest = player(helper, "GuestTest");
    Entrelumen.current(founder).act = 3;
    Entrelumen.current(founder).completed.add("first_signal");
    Entrelumen.current(guest).act = 2;
    var team =
        (dev.ftb.mods.ftbteams.data.PartyTeam)
            FTBTeamsAPI.api()
                .getManager()
                .createPartyTeam(
                    founder,
                    "Runtime party " + founder.getUUID(),
                    "",
                    dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    helper.assertTrue(Entrelumen.current(founder).act == 3, "Party creation lost founder progress");
    Entrelumen.current(founder).act = 4;
    team.invite(founder, List.of(guest.getGameProfile()));
    team.join(guest);
    helper.assertTrue(
        Entrelumen.current(guest).act == 4, "Joining did not use destination campaign");
    team.leave(guest.getUUID());
    helper.assertTrue(
        Entrelumen.current(guest).act == 2, "Leaving did not restore personal snapshot");
    team.leave(founder.getUUID());
    helper.assertTrue(Entrelumen.current(founder).act == 3, "Founder personal snapshot changed");
    helper.assertTrue(
        CampaignData.get(founder.server).campaigns.parties.get(team.getId()).archived,
        "Dissolved campaign not archived");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void offhandDeliveryAndMissingMaterialAreAtomic(GameTestHelper helper) {
    var player = player(helper, "OffhandTest");
    player.getInventory().add(new ItemStack(Items.BOOK));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 0, "Incomplete delivery accepted");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1,
        "Failed delivery consumed a partial ingredient");
    player.setItemInHand(
        net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.COPPER_INGOT, 2));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 1,
        "Offhand ingredient was not counted");
    helper.assertTrue(player.getOffhandItem().getCount() == 1, "Offhand exact consumption failed");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 0, "Main inventory ingredient not consumed");
    helper.assertTrue(
        player
                .getInventory()
                .countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")))
            == 1,
        "Delivery did not grant one Atlas");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void statusReportsMissingMaterialsAndPrerequisites(GameTestHelper helper) {
    var player = player(helper, "StatusTest");
    player.setItemInHand(
        net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.COPPER_INGOT, 1));
    var lines = Entrelumen.statusLines(player);
    boolean foundCopper = false, foundPrerequisite = false;
    for (var line : lines)
      if (line.getContents()
          instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
        if (translated.getKey().equals("entrelumen.project.material")) {
          Object[] args = translated.getArgs();
          if (args[0] instanceof net.minecraft.network.chat.Component name
              && name.getString()
                  .equals(new ItemStack(Items.COPPER_INGOT).getHoverName().getString())
              && args[1].equals(1)
              && args[2].equals(1)
              && args[3].equals(0)) foundCopper = true;
        }
        if (translated.getKey().equals("entrelumen.project.prerequisite.missing"))
          foundPrerequisite = true;
      }
    helper.assertTrue(
        foundCopper, "Status does not report the offhand available/required/missing amount");
    helper.assertTrue(foundPrerequisite, "Status omitted prerequisite information");
    helper.succeed();
  }

  @GameTest(template = "empty", batch = "campaign_reload", timeoutTicks = 1200)
  public static void datapackReloadIsAtomicAndRejectsCycles(GameTestHelper helper)
      throws Exception {
    var server = helper.getLevel().getServer();
    var selected = List.copyOf(server.getPackRepository().getSelectedIds());
    var directory =
        server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
            .resolve("entrelumen_runtime_reload");
    var definition = directory.resolve("data/entrelumen/campaign/projects.json");
    java.nio.file.Files.createDirectories(definition.getParent());
    java.nio.file.Files.writeString(
        directory.resolve("pack.mcmeta"),
        "{\"pack\":{\"pack_format\":48,\"description\":\"Entrelumen runtime reload test\"}}");
    com.google.gson.JsonObject original;
    try (var reader =
        server
            .getResourceManager()
            .getResource(ProjectReloadListener.RESOURCE)
            .orElseThrow()
            .openAsReader()) {
      original = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
    }
    var valid = original.deepCopy();
    valid
        .getAsJsonObject("atlas_awakened")
        .getAsJsonObject("items")
        .addProperty("minecraft:book", 2);
    java.nio.file.Files.writeString(definition, valid.toString());
    server.getPackRepository().reload();
    var withTest = new ArrayList<>(selected);
    withTest.add("file/entrelumen_runtime_reload");
    class ReloadSequence {
      int step;
      java.util.concurrent.CompletableFuture<Void> pending = server.reloadResources(withTest);
      Map<String, Projects.Project> accepted;

      void run() {
        helper.assertTrue(pending.isDone(), "Waiting for asynchronous datapack reload");
        try {
          if (step == 0) {
            pending.join();
            accepted = Projects.all();
            helper.assertTrue(
                accepted.get("atlas_awakened").items().get("minecraft:book") == 2,
                "Higher priority datapack did not replace bundled definitions");
            var invalid = valid.deepCopy();
            invalid
                .getAsJsonObject("atlas_awakened")
                .getAsJsonObject("items")
                .addProperty("minecraft:book", 7);
            invalid
                .getAsJsonObject("world_network")
                .addProperty("reward", "entrelumen:nonexistent_item");
            java.nio.file.Files.writeString(definition, invalid.toString());
            step = 1;
            pending = server.reloadResources(withTest);
            helper.fail("Waiting for invalid resource rejection");
          } else if (step == 1) {
            assertRejected("unknown or unusable item ID");
            helper.assertTrue(
                Projects.all() == accepted
                    && Projects.all().get("atlas_awakened").items().get("minecraft:book") == 2,
                "Invalid reload partially replaced active definitions");
            var cycle = valid.deepCopy();
            var requires = new com.google.gson.JsonArray();
            requires.add("first_signal");
            cycle.getAsJsonObject("atlas_awakened").add("requires", requires);
            java.nio.file.Files.writeString(definition, cycle.toString());
            step = 2;
            pending = server.reloadResources(withTest);
            helper.fail("Waiting for cyclic resource rejection");
          } else if (step == 2) {
            assertRejected("prerequisite cycle");
            helper.assertTrue(
                Projects.all() == accepted, "Cyclic reload replaced active definitions");
            java.nio.file.Files.writeString(definition, original.toString());
            step = 3;
            pending = server.reloadResources(selected);
            helper.fail("Waiting for original datapacks to restore");
          } else {
            pending.join();
            helper.assertTrue(
                Projects.all().get("atlas_awakened").items().get("minecraft:book")
                    == original
                        .getAsJsonObject("atlas_awakened")
                        .getAsJsonObject("items")
                        .get("minecraft:book")
                        .getAsInt(),
                "Original data not restored");
          }
        } catch (java.io.IOException e) {
          throw new IllegalStateException("Could not write isolated test datapack", e);
        }
      }

      void assertRejected(String expected) {
        helper.assertTrue(
            pending.isCompletedExceptionally(), "Invalid datapack unexpectedly accepted");
        try {
          pending.join();
          throw new IllegalStateException("Expected rejection");
        } catch (java.util.concurrent.CompletionException e) {
          StringBuilder causes = new StringBuilder();
          for (Throwable cause = e; cause != null; cause = cause.getCause())
            causes.append(cause.getMessage()).append('\n');
          helper.assertTrue(
              causes.toString().contains(expected),
              "Reload failed for unrelated reason: " + causes);
        }
      }
    }
    var sequence = new ReloadSequence();
    helper.succeedWhen(sequence::run);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasRouteRejectsStaleTeamAndCannotDoubleConsume(GameTestHelper helper)
      throws Exception {
    var player = player(helper, "AtlasRouteTest");
    player.getInventory().add(new ItemStack(Items.BOOK, 2));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
    var original = AtlasNetwork.snapshot(player, true, "");
    var team =
        (dev.ftb.mods.ftbteams.data.PartyTeam)
            FTBTeamsAPI.api()
                .getManager()
                .createPartyTeam(
                    player,
                    "Atlas " + player.getUUID(),
                    "",
                    dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var stale =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(
                original.campaign(), CampaignActions.Action.DELIVER, "atlas_awakened"));
    helper.assertTrue(
        stale.message().equals("entrelumen.atlas.stale") && stale.campaign().equals(team.getId()),
        "Stale action was not rejected with refreshed team identity");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 2
            && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
        "Stale team action consumed materials");
    var delivered =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(
                stale.campaign(), CampaignActions.Action.DELIVER, "atlas_awakened"));
    helper.assertTrue(
        delivered.projects().stream()
            .anyMatch(project -> project.id().equals("atlas_awakened") && project.completed()),
        "GUI route did not report authoritative completion");
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 0,
        "Command replay after GUI route accepted");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1
            && player.getInventory().countItem(Items.COPPER_INGOT) == 1,
        "Cross-route replay consumed materials");
    helper.assertTrue(
        player
                .getInventory()
                .countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")))
            == 1,
        "Cross-route reward duplicated");
    team.leave(player.getUUID());
    var afterLeave =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(
                team.getId(), CampaignActions.Action.DELIVER, "atlas_awakened"));
    helper.assertTrue(
        afterLeave.message().equals("entrelumen.atlas.stale")
            && afterLeave.campaign().equals(original.campaign()),
        "Leaving did not invalidate old party screen");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1,
        "Stale party screen consumed personal materials");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasAdvanceAndSnapshotCodecUseAuthoritativeState(GameTestHelper helper) {
    var player = player(helper, "AtlasAdvanceTest");
    var campaign = Entrelumen.current(player);
    var initial = AtlasNetwork.snapshot(player, true, "");
    var denied =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(denied.act() == 1 && !denied.canAdvance(), "GUI advanced without projects");
    campaign.completed.addAll(Projects.forAct(1));
    var advanced =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(
        advanced.act() == 2 && campaign.act == 2,
        "GUI advancement did not use persistent campaign");
    helper.assertTrue(
        advanced.projects().stream()
            .allMatch(project -> Projects.all().get(project.id()).act() == 2),
        "Snapshot leaked unrelated acts");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
    try {
      AtlasNetwork.Snapshot.CODEC.encode(buffer, advanced);
      var decoded = AtlasNetwork.Snapshot.CODEC.decode(buffer);
      helper.assertTrue(decoded.equals(advanced), "Snapshot wire roundtrip changed state");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void firstActCompletesThroughRealDeliveries(GameTestHelper helper) {
    var player = player(helper, "FirstActTest");
    var initial = AtlasNetwork.handleOpen(player);
    var campaign = Entrelumen.current(player);
    player.getInventory().add(new ItemStack(Items.BOOK));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 9));
    player.getInventory().add(new ItemStack(Items.GLASS, 4));
    player.getInventory().add(new ItemStack(Items.BREAD, 3));
    player.getInventory().add(new ItemStack(Items.BOWL, 4));
    player.getInventory().add(new ItemStack(Items.PAPER, 3));
    player.getInventory().add(new ItemStack(Items.COMPASS));
    // Supplies may be gifted: possession alone never grants a milestone.
    helper.assertTrue(campaign.completed.isEmpty(), "Supplies completed the story automatically");
    var denied = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        initial.campaign(), CampaignActions.Action.DELIVER, "first_signal"));
    helper.assertTrue(!denied.canAdvance() && campaign.completed.isEmpty()
        && player.getInventory().countItem(Items.COPPER_INGOT) == 9,
        "Premature final delivery consumed supplies or advanced the story");
    for (String id : List.of("atlas_awakened", "travellers_table", "lens_assembled",
        "field_survey", "first_signal")) {
      var delivered = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
          initial.campaign(), CampaignActions.Action.DELIVER, id));
      helper.assertTrue(delivered.message().equals("entrelumen.atlas.delivered")
          && delivered.projects().stream().anyMatch(p -> p.id().equals(id) && p.completed()),
          "First-act delivery failed: " + id);
    }
    helper.assertTrue(campaign.completed.size() == 5
        && AtlasNetwork.handleOpen(player).canAdvance(), "Complete first act cannot advance");
    var atlas = BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas"));
    helper.assertTrue(player.getInventory().countItem(atlas) == 1
        && player.getInventory().items.stream().filter(s -> !s.isEmpty()).count() == 2
          && player.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:signal_core"))) == 1,
        "First-act deliveries consumed incorrect amounts or lost the portable Atlas");
    var replay = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        initial.campaign(), CampaignActions.Action.DELIVER, "first_signal"));
    helper.assertTrue(replay.message().equals("entrelumen.delivery.failed")
        && campaign.completed.size() == 5 && player.getInventory().countItem(atlas) == 1
          && player.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:signal_core"))) == 1,
        "Completed signal replay changed progress or inventory");
    var advanced = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(advanced.act() == 2 && campaign.act == 2,
        "Completed first act did not advance to act two");
    var repeatedAdvance = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(repeatedAdvance.act() == 2 && !repeatedAdvance.canAdvance(),
        "Repeated advancement bypassed act two");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void secondActConsumesGiftedPrototypesAndGatesClosure(GameTestHelper helper) {
    var player = player(helper, "SecondActTest");
    var campaign = Entrelumen.current(player);
    var campaignId = CampaignActions.campaignId(player);
    var prototypes = new LinkedHashMap<String, Item>();
    prototypes.put("precision_bench", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:calibration_frame")));
    prototypes.put("crystal_grid", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:energy_coupler")));
    prototypes.put("living_workshop", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:living_matrix")));
    prototypes.put("travelling_pantry", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:ration_bundle")));
    // Gifted prototypes are valid. This tests delivery, not the full pack's crafting recipes.
    prototypes.values().forEach(item -> player.getInventory().add(new ItemStack(item, 2)));
    player.getInventory().add(new ItemStack(Items.PAPER, 6));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
    player.getInventory().add(new ItemStack(Items.ANVIL));
    var installedPos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
    helper.getLevel().setBlockAndUpdate(installedPos,
        net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
    helper.assertTrue(campaign.completed.isEmpty(), "Possession granted Act II progress");
    campaign.completed.add("first_signal");
    for (var entry : prototypes.entrySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, entry.getKey()).success()
          && player.getInventory().countItem(entry.getValue()) == 2,
          "Wrong-act delivery accepted or consumed prototype: " + entry.getKey());
    }
    campaign.act = 2;
    campaign.completed.clear();
    for (var entry : prototypes.entrySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, entry.getKey()).success()
          && player.getInventory().countItem(entry.getValue()) == 2,
          "Missing first signal accepted or consumed prototype: " + entry.getKey());
    }
    campaign.completed.add("first_signal");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "living_workshop").success()
        && player.getInventory().countItem(prototypes.get("living_workshop")) == 2,
        "Living workshop bypassed precision bench");
    int deliveredCount = 0;
    for (var entry : prototypes.entrySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, "lost_workshop").success()
          && player.getInventory().countItem(Items.PAPER) == 6
          && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
          "Archive accepted incomplete projects or consumed materials");
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.ADVANCE, "").success() && campaign.act == 2,
          "Incomplete Act II advanced");
      helper.assertTrue(CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, entry.getKey()).success()
          && campaign.completed.contains(entry.getKey())
          && player.getInventory().countItem(entry.getValue()) == 1,
          "Prototype delivery did not consume exactly one: " + entry.getKey());
      deliveredCount++;
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, entry.getKey()).success()
          && player.getInventory().countItem(entry.getValue()) == 1
          && campaign.completed.size() == deliveredCount + 1,
          "Duplicate prototype delivery changed inventory or campaign");
    }
    helper.assertTrue(!AtlasNetwork.handleOpen(player).canAdvance()
        && !CampaignActions.perform(player, campaignId,
            CampaignActions.Action.ADVANCE, "").success(),
        "Four prototypes bypassed the closing archive");
    helper.assertTrue(CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "lost_workshop").success()
        && player.getInventory().countItem(Items.PAPER) == 3
        && player.getInventory().countItem(Items.COPPER_INGOT) == 1,
        "Archive did not consume exactly three paper and one copper");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "lost_workshop").success()
        && player.getInventory().countItem(Items.PAPER) == 3
        && player.getInventory().countItem(Items.COPPER_INGOT) == 1
        && campaign.completed.size() == 6,
        "Archive replay changed supplies or progress");
    // The closing archive grants Terra's Arm once; the prototype deliveries grant nothing.
    helper.assertTrue(prototypes.values().stream().allMatch(item -> player.getInventory().countItem(item) == 1)
        && player.getInventory().countItem(Items.ANVIL) == 1
        && player.getInventory().countItem(TerraArm.ITEM.get()) == 1
        && player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 10
        && helper.getLevel().getBlockState(installedPos).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE),
        "Deliveries granted items other than Terra's Arm or consumed infrastructure/unrelated supplies");
    helper.assertTrue(AtlasNetwork.handleOpen(player).canAdvance()
        && CampaignActions.perform(player, campaignId, CampaignActions.Action.ADVANCE, "").success()
        && campaign.act == 3,
        "Closed Act II cannot advance to Act III");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.ADVANCE, "").success() && campaign.act == 3,
        "Repeated advancement bypassed Act III");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void questMirrorRepairsSavedProgressThroughDependencyDag(GameTestHelper helper) {
    var owner = player(helper, "FTBMirrorOwner");
    var outsider = player(helper, "FTBMirrorOther");
    var campaign = Entrelumen.current(owner);
    campaign.completed.addAll(List.of("atlas_awakened", "first_signal", "lost_workshop", "field_survey"));
    var authoritative = Set.copyOf(campaign.completed);
    var file = ServerQuestFile.getInstance().orElseThrow();
    var chapter = new Chapter(0x7E660001L, file, file.getDefaultChapterGroup());
    // Deliberately visit the child first, as a loaded quest file may not be topologically sorted.
    var leafQuest = new Quest(0x7E660002L, chapter);
    var middleQuest = new Quest(0x7E660003L, chapter);
    var rootQuest = new Quest(0x7E660004L, chapter);
    var secondRootQuest = new Quest(0x7E660005L, chapter);
    for (var quest : List.of(leafQuest, middleQuest, rootQuest, secondRootQuest))
      chapter.addQuest(quest);
    middleQuest.addDependency(rootQuest);
    leafQuest.addDependency(middleQuest);
    leafQuest.addDependency(secondRootQuest);
    var leaf = campaignTask(0x7E660006L, leafQuest, "field_survey", helper);
    var middle = campaignTask(0x7E660007L, middleQuest, "lost_workshop", helper);
    var root = campaignTask(0x7E660008L, rootQuest, "atlas_awakened", helper);
    var secondRoot = campaignTask(0x7E660009L, secondRootQuest, "first_signal", helper);
    var data = file.getOrCreateTeamData(owner);
    var otherData = file.getOrCreateTeamData(outsider);
    helper.assertTrue(!data.getTeamId().equals(otherData.getTeamId()), "Fixture teams merged");

    // Full progress written before dependencies, plus a legacy root missing completion stamps.
    data.setProgress(leaf, 1);
    data.setProgress(middle, 1);
    data.setProgress(root, 1);
    data.setProgress(secondRoot, 1);
    data.setCompleted(root.id, null);
    data.setCompleted(rootQuest.id, null);
    helper.assertTrue(data.getProgress(root) == 1 && !data.isCompleted(root)
        && !data.isCompleted(rootQuest) && data.getProgress(middle) == 1
        && !data.isCompleted(middle) && data.getProgress(leaf) == 1
        && !data.isCompleted(leaf), "Fixture did not reproduce saved progress without completion");

    leaf.submitTask(data, owner, ItemStack.EMPTY);
    middle.submitTask(data, owner, ItemStack.EMPTY);
    root.submitTask(data, owner, ItemStack.EMPTY);
    helper.assertTrue(data.isCompleted(root) && data.isCompleted(rootQuest)
        && !data.isCompleted(middle) && !data.isCompleted(leaf),
        "Root repair bypassed the dependency graph");
    leaf.submitTask(data, owner, ItemStack.EMPTY);
    middle.submitTask(data, owner, ItemStack.EMPTY);
    helper.assertTrue(data.isCompleted(middle) && data.isCompleted(middleQuest)
        && !data.isCompleted(leaf), "Middle repair did not unlock in dependency order");
    leaf.submitTask(data, owner, ItemStack.EMPTY);
    helper.assertTrue(data.isCompleted(leaf) && data.isCompleted(leafQuest)
        && data.getCompletionCount(leafQuest) == 1,
        "Fully authoritative DAG did not converge to completed FTB quests");

    // FTB forgery on another team must not grant campaign authority or affect this team's repair.
    otherData.setProgress(root, 1);
    otherData.setProgress(leaf, 1);
    root.submitTask(otherData, outsider, ItemStack.EMPTY);
    leaf.submitTask(otherData, outsider, ItemStack.EMPTY);
    helper.assertTrue(otherData.getProgress(root) == 0 && otherData.getProgress(leaf) == 0
        && !otherData.isCompleted(rootQuest) && !otherData.isCompleted(leafQuest)
        && Entrelumen.current(outsider).completed.isEmpty(),
        "False FTB progress escaped its team or granted campaign authority");

    long rootTime = data.getCompletedTime(root.id).orElseThrow().getTime();
    long middleTime = data.getCompletedTime(middle.id).orElseThrow().getTime();
    long leafTime = data.getCompletedTime(leaf.id).orElseThrow().getTime();
    helper.runAfterDelay(2, () -> {
      for (var task : List.of(leaf, middle, root, secondRoot))
        task.submitTask(data, owner, ItemStack.EMPTY);
      helper.assertTrue(data.getCompletedTime(root.id).orElseThrow().getTime() == rootTime
          && data.getCompletedTime(middle.id).orElseThrow().getTime() == middleTime
          && data.getCompletedTime(leaf.id).orElseThrow().getTime() == leafTime
          && data.getCompletionCount(rootQuest) == 1
          && data.getCompletionCount(middleQuest) == 1
          && data.getCompletionCount(leafQuest) == 1
          && owner.getInventory().isEmpty() && campaign.completed.equals(authoritative),
          "Replay re-emitted FTB completion or changed rewards, inventory, or campaign authority");
      helper.succeed();
    });
  }

  private static CampaignTask campaignTask(long id, Quest quest, String milestone, GameTestHelper helper) {
    var task = new CampaignTask(id, quest);
    quest.addTask(task);
    var tag = new CompoundTag();
    tag.putString("milestone", milestone);
    task.readData(tag, helper.getLevel().registryAccess());
    return task;
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void phaseAndEndingQuestTasksOnlyMirrorSavedAuthority(GameTestHelper helper) {
    var player = player(helper, "ActSixMirror");
    var campaign = Entrelumen.current(player);
    var file = ServerQuestFile.getInstance().orElseThrow();
    var chapter = new Chapter(0x7E120001L, file, file.getDefaultChapterGroup());
    var phaseQuest = new Quest(0x7E120002L, chapter);
    var endingQuest = new Quest(0x7E120003L, chapter);
    chapter.addQuest(phaseQuest);
    chapter.addQuest(endingQuest);
    var phaseTask = new CampaignTask(0x7E120004L, phaseQuest);
    var endingTask = new CampaignTask(0x7E120005L, endingQuest);
    phaseQuest.addTask(phaseTask);
    endingQuest.addTask(endingTask);
    var phaseTag = new CompoundTag();
    phaseTag.putString("milestone", CampaignMilestones.PHASE_IDS.getFirst());
    phaseTask.readData(phaseTag, helper.getLevel().registryAccess());
    var endingTag = new CompoundTag();
    endingTag.putString("milestone", CampaignMilestones.LAST_HORIZON);
    endingTask.readData(endingTag, helper.getLevel().registryAccess());
    var teamData = file.getOrCreateTeamData(player);
    campaign.completed.add(CampaignMilestones.PHASE_IDS.getFirst());
    campaign.arkPhase = 1;
    phaseTask.submitTask(teamData, player, ItemStack.EMPTY);
    endingTask.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(phaseTask) == 0
        && teamData.getProgress(endingTask) == 0,
        "FTB accepted a fabricated phase or ending");
    campaign.act = 6;
    campaign.completed.addAll(Entrelumen.MODULES);
    phaseTask.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(phaseTask) == 1,
        "Commissioning phase did not project to FTB");
    campaign.arkPhase = 6;
    campaign.completed.addAll(List.of("world_network", "end_arrival"));
    endingTask.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(endingTask) == 0,
        "FTB granted the ending from prerequisites alone");
    helper.assertTrue(CampaignMilestones.finish(campaign), "Ending fixture rejected");
    endingTask.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(endingTask) == 1,
        "Persisted ending did not project to FTB");
    campaign.completed.remove("nature_module");
    phaseTask.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(phaseTask) == 0,
        "FTB retained a derived phase after authority became invalid");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void thirdActConsumesGiftedPrototypesAndGatesClosure(GameTestHelper helper) {
    var player = player(helper, "ThirdActTest");
    var campaign = Entrelumen.current(player);
    var campaignId = CampaignActions.campaignId(player);
    var prototypes = new LinkedHashMap<String, Item>();
    prototypes.put("signal_exchange", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:routing_matrix")));
    prototypes.put("nursery_protocol", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:propagation_core")));
    prototypes.put("measured_logistics", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:inventory_sensor")));
    prototypes.put("distributed_power", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:power_regulator")));
    prototypes.put("workshop_hands", BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:handling_core")));
    // Gifted prototypes are valid. This tests delivery, not the full pack's crafting recipes.
    prototypes.values().forEach(item -> player.getInventory().add(new ItemStack(item, 2)));
    player.getInventory().add(new ItemStack(Items.PAPER, 6));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
    player.getInventory().add(new ItemStack(Items.ANVIL));
    var installedPos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
    helper.getLevel().setBlockAndUpdate(installedPos,
        net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
    helper.assertTrue(campaign.completed.isEmpty(), "Possession granted Act III progress");
    campaign.completed.add("lost_workshop");
    for (var entry : prototypes.entrySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, entry.getKey()).success()
          && player.getInventory().countItem(entry.getValue()) == 2,
          "Wrong-act delivery accepted or consumed prototype: " + entry.getKey());
    }
    campaign.act = 3;
    campaign.completed.clear();
    for (var entry : prototypes.entrySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, entry.getKey()).success()
          && player.getInventory().countItem(entry.getValue()) == 2,
          "Missing lost workshop accepted or consumed prototype: " + entry.getKey());
    }
    campaign.completed.add("lost_workshop");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "measured_logistics").success()
        && player.getInventory().countItem(prototypes.get("measured_logistics")) == 2,
        "Measured logistics bypassed signal exchange");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "workshop_hands").success()
        && player.getInventory().countItem(prototypes.get("workshop_hands")) == 2,
        "Workshop hands bypassed distributed power");
    var initial = AtlasNetwork.handleOpen(player);
    for (var entry : prototypes.entrySet()) {
      var view = initial.projects().stream().filter(p -> p.id().equals(entry.getKey())).findFirst().orElseThrow();
      helper.assertTrue(!view.completed() && view.materials().equals(List.of(
          new AtlasNetwork.Material(BuiltInRegistries.ITEM.getKey(entry.getValue()), 2, 1)))
          && view.prerequisites().stream().anyMatch(p -> p.id().equals("lost_workshop") && p.completed()),
          "Atlas prototype costs or prerequisite status differ: " + entry.getKey());
    }
    var closure = initial.projects().stream().filter(p -> p.id().equals("exchange_route")).findFirst().orElseThrow();
    helper.assertTrue(!closure.ready() && !closure.completed()
        && closure.prerequisites().stream().map(AtlasNetwork.Prerequisite::id).collect(java.util.stream.Collectors.toSet()).equals(prototypes.keySet())
        && closure.prerequisites().stream().noneMatch(AtlasNetwork.Prerequisite::completed)
        && new HashSet<>(closure.materials()).equals(Set.of(
            new AtlasNetwork.Material(ResourceLocation.parse("minecraft:paper"), 6, 3),
            new AtlasNetwork.Material(ResourceLocation.parse("minecraft:copper_ingot"), 2, 1))),
        "Atlas closure snapshot has incorrect costs or status");
    int deliveredCount = 0;
    for (var entry : prototypes.entrySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, "exchange_route").success()
          && player.getInventory().countItem(Items.PAPER) == 6
          && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
          "Archive accepted incomplete projects or consumed materials");
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.ADVANCE, "").success() && campaign.act == 3,
          "Incomplete Act III advanced");
      var delivered = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
          campaignId, CampaignActions.Action.DELIVER, entry.getKey()));
      helper.assertTrue(delivered.message().equals("entrelumen.atlas.delivered")
          && delivered.projects().stream().anyMatch(p -> p.id().equals(entry.getKey()) && p.completed()
              && !p.ready() && p.materials().getFirst().available() == 1)
          && campaign.completed.contains(entry.getKey())
          && player.getInventory().countItem(entry.getValue()) == 1,
          "Prototype delivery did not consume exactly one: " + entry.getKey());
      deliveredCount++;
      helper.assertTrue(command(player, "entrelumen deliver " + entry.getKey()) == 0
          && player.getInventory().countItem(entry.getValue()) == 1
          && campaign.completed.size() == deliveredCount + 1,
          "Duplicate prototype delivery changed inventory or campaign");
    }
    helper.assertTrue(!AtlasNetwork.handleOpen(player).canAdvance()
        && !CampaignActions.perform(player, campaignId,
            CampaignActions.Action.ADVANCE, "").success(),
        "Five prototypes bypassed the closing archive");
    var readyClosure = AtlasNetwork.handleOpen(player).projects().stream()
        .filter(p -> p.id().equals("exchange_route")).findFirst().orElseThrow();
    helper.assertTrue(readyClosure.ready() && !readyClosure.completed()
        && readyClosure.prerequisites().stream().allMatch(AtlasNetwork.Prerequisite::completed),
        "Atlas closure did not become ready after five deliveries");
    helper.assertTrue(CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "exchange_route").success()
        && player.getInventory().countItem(Items.PAPER) == 3
        && player.getInventory().countItem(Items.COPPER_INGOT) == 1,
        "Archive did not consume exactly three paper and one copper");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.DELIVER, "exchange_route").success()
        && player.getInventory().countItem(Items.PAPER) == 3
        && player.getInventory().countItem(Items.COPPER_INGOT) == 1
        && campaign.completed.size() == 7,
        "Archive replay changed supplies or progress");
    // Signal exchange, nursery protocol and workshop hands each grant their altar, exactly once.
    var altarRewards = List.of(Altars.PEACE_ALTAR.get().asItem(), Altars.GROWTH_ALTAR.get().asItem(),
        Altars.TERRAFORM_ALTAR.get().asItem());
    helper.assertTrue(prototypes.values().stream().allMatch(item -> player.getInventory().countItem(item) == 1)
        && altarRewards.stream().allMatch(item -> player.getInventory().countItem(item) == 1)
        && player.getInventory().countItem(Items.ANVIL) == 1
        && player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 10 + altarRewards.size()
        && helper.getLevel().getBlockState(installedPos).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE),
        "Deliveries granted items other than one altar each, or consumed infrastructure/unrelated supplies");
    helper.assertTrue(AtlasNetwork.handleOpen(player).canAdvance()
        && CampaignActions.perform(player, campaignId, CampaignActions.Action.ADVANCE, "").success()
        && campaign.act == 4,
        "Closed Act III cannot advance to Act IV");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.ADVANCE, "").success() && campaign.act == 4,
        "Repeated advancement bypassed Act IV");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void fourthActRequiresJourneysAndFiveDeliveries(GameTestHelper helper) throws Exception {
    var player = player(helper, "FourthActTest");
    var other = player(helper, "FourthActOther");
    var otherTeam = FTBTeamsAPI.api().getManager().createPartyTeam(other,
        "Fourth " + other.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var otherCampaign = Entrelumen.current(other);
    var otherBefore = Set.copyOf(otherCampaign.completed);
    var campaign = Entrelumen.current(player);
    campaign.act = 4;
    for (int act = 1; act <= 3; act++) campaign.completed.addAll(Projects.forAct(act));
    var expectedCompleted = new HashSet<>(campaign.completed);
    var prototypes = new LinkedHashMap<String, Item>();
    prototypes.put("spectral_archive", arkItem("spectral_lens"));
    prototypes.put("horizon_survey", arkItem("horizon_chart"));
    prototypes.put("pollinator_treaty", arkItem("ecosystem_capsule"));
    prototypes.put("sealed_memory", arkItem("containment_seal"));
    prototypes.values().forEach(item -> player.getInventory().add(new ItemStack(item, 2)));
    player.getInventory().add(new ItemStack(Items.PAPER, 6));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
    player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
    var expectedInventory = new HashMap<>(Entrelumen.availableMaterials(player));
    var prerequisites = Map.of("spectral_archive", Set.of("exchange_route"),
        "horizon_survey", Set.of("exchange_route", "aether_arrival", "twilight_arrival"),
        "pollinator_treaty", Set.of("exchange_route", "bumblezone_arrival"),
        "sealed_memory", Set.of("exchange_route", "spectral_archive"));
    var initial = AtlasNetwork.handleOpen(player);
    for (var entry : prototypes.entrySet()) {
      var view = initial.projects().stream().filter(p -> p.id().equals(entry.getKey())).findFirst().orElseThrow();
      helper.assertTrue(!view.completed() && view.materials().equals(List.of(
          new AtlasNetwork.Material(BuiltInRegistries.ITEM.getKey(entry.getValue()), 2, 1)))
          && view.prerequisites().stream().map(AtlasNetwork.Prerequisite::id)
              .collect(java.util.stream.Collectors.toSet()).equals(prerequisites.get(entry.getKey()))
          && view.prerequisites().stream().allMatch(p -> p.completed() == expectedCompleted.contains(p.id())),
          "Act IV prototype snapshot differs: " + entry.getKey());
    }
    var closure = initial.projects().stream().filter(p -> p.id().equals("atlas_voices")).findFirst().orElseThrow();
    helper.assertTrue(!closure.ready() && !closure.completed()
        && closure.prerequisites().stream().map(AtlasNetwork.Prerequisite::id)
            .collect(java.util.stream.Collectors.toSet()).equals(prototypes.keySet())
        && closure.prerequisites().stream().noneMatch(AtlasNetwork.Prerequisite::completed)
        && new HashSet<>(closure.materials()).equals(Set.of(
            new AtlasNetwork.Material(ResourceLocation.parse("minecraft:paper"), 6, 3),
            new AtlasNetwork.Material(ResourceLocation.parse("minecraft:copper_ingot"), 2, 1))),
        "Atlas voices closure identity, prerequisites or costs changed");
    for (String id : List.of("aether_arrival", "horizon_survey", "pollinator_treaty", "sealed_memory"))
      fourthActDenied(helper, player, id);
    helper.assertTrue(player.serverLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD),
        "Forged-transition fixture must stay in overworld");
    var aether = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
        ResourceLocation.parse("aether:the_aether"));
    Expeditions.onDimensionChanged(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
        player, player.serverLevel().dimension(), aether));
    var file = ServerQuestFile.getInstance().orElseThrow();
    var chapter = new Chapter(0x7E440001L, file, file.getDefaultChapterGroup());
    var quest = new Quest(0x7E440002L, chapter);
    chapter.addQuest(quest);
    var task = new CampaignTask(0x7E440003L, quest);
    quest.addTask(task);
    var tag = new CompoundTag();
    tag.putString("milestone", "aether_arrival");
    task.readData(tag, helper.getLevel().registryAccess());
    var teamData = file.getOrCreateTeamData(player);
    teamData.setProgress(task, 1);
    task.submitTask(teamData, player, new ItemStack(arkItem("horizon_chart")));
    helper.assertTrue(teamData.getProgress(task) == 0 && campaign.completed.equals(expectedCompleted)
        && Entrelumen.availableMaterials(player).equals(expectedInventory),
        "Gifts, forged transition or FTB projection fabricated an observation");
    // Synthetic observations test delivery gates; this is not a portal/travel playtest.
    helper.assertTrue(Expeditions.record(campaign, "aether:the_aether"), "Synthetic Aether record failed");
    fourthActDenied(helper, player, "horizon_survey");
    helper.assertTrue(Expeditions.record(campaign, "twilightforest:twilight_forest"), "Synthetic Twilight record failed");
    fourthActDenied(helper, player, "pollinator_treaty");
    helper.assertTrue(Expeditions.record(campaign, "the_bumblezone:the_bumblezone"), "Synthetic Bumblezone record failed");
    expectedCompleted.addAll(Set.of("aether_arrival", "twilight_arrival", "bumblezone_arrival"));
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 1, "Recorded observation did not mirror");
    for (var entry : prototypes.entrySet()) {
      fourthActDenied(helper, player, "atlas_voices");
      helper.assertTrue(command(player, "entrelumen advance") == 0 && campaign.act == 4,
          "Incomplete Act IV advanced");
      var result = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
          initial.campaign(), CampaignActions.Action.DELIVER, entry.getKey()));
      expectedInventory.compute(BuiltInRegistries.ITEM.getKey(entry.getValue()).toString(), (id, count) -> count - 1);
      String reward = Projects.all().get(entry.getKey()).reward();
      if (!reward.isEmpty()) expectedInventory.merge(reward, 1, Integer::sum);
      expectedCompleted.add(entry.getKey());
      helper.assertTrue(result.message().equals("entrelumen.atlas.delivered") && campaign.act == 4
          && campaign.completed.equals(expectedCompleted)
          && Entrelumen.availableMaterials(player).equals(expectedInventory),
          "Act IV delivery changed unrelated progress, consumed surplus or granted other rewards: " + entry.getKey());
      fourthActDenied(helper, player, entry.getKey());
    }
    helper.assertTrue(!AtlasNetwork.handleOpen(player).canAdvance(), "Prototypes bypassed Atlas voices");
    var closed = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        initial.campaign(), CampaignActions.Action.DELIVER, "atlas_voices"));
    expectedInventory.compute("minecraft:paper", (id, count) -> count - 3);
    expectedInventory.compute("minecraft:copper_ingot", (id, count) -> count - 1);
    expectedCompleted.add("atlas_voices");
    helper.assertTrue(closed.message().equals("entrelumen.atlas.delivered") && closed.canAdvance()
        && campaign.completed.equals(expectedCompleted)
        && Entrelumen.availableMaterials(player).equals(expectedInventory), "Atlas voices consumption differs");
    fourthActDenied(helper, player, "atlas_voices");
    var advanced = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(advanced.act() == 5 && campaign.act == 5
        && command(player, "entrelumen advance") == 0 && campaign.act == 5
        && campaign.completed.equals(expectedCompleted)
        && Entrelumen.availableMaterials(player).equals(expectedInventory)
        && otherCampaign.completed.equals(otherBefore) && otherCampaign.act == 1
        && CampaignActions.campaignId(other).equals(otherTeam.getId()),
        "Advancement changed inventory, unrelated progress or another team's campaign");
    helper.succeed();
  }

  private static void fourthActDenied(GameTestHelper helper, ServerPlayer player, String project) {
    var campaign = Entrelumen.current(player);
    var completed = Set.copyOf(campaign.completed);
    var inventory = Entrelumen.availableMaterials(player);
    var result = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        CampaignActions.campaignId(player), CampaignActions.Action.DELIVER, project));
    helper.assertTrue(result.message().equals("entrelumen.delivery.failed")
        && campaign.completed.equals(completed) && campaign.act == 4
        && Entrelumen.availableMaterials(player).equals(inventory),
        "Rejected Act IV delivery mutated campaign or supplies: " + project);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void fifthActRequiresFourDeliveriesBeforeAdvancing(GameTestHelper helper) throws Exception {
    var player = player(helper, "FifthActTest");
    var other = player(helper, "FifthActOther");
    var otherTeam = FTBTeamsAPI.api().getManager().createPartyTeam(other,
        "Fifth " + other.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var otherCampaign = Entrelumen.current(other);
    var otherBefore = Set.copyOf(otherCampaign.completed);
    var campaign = Entrelumen.current(player);
    var campaignId = CampaignActions.campaignId(player);
    campaign.act = 4;
    for (int act = 1; act <= 3; act++) campaign.completed.addAll(Projects.forAct(act));
    campaign.completed.addAll(Projects.forAct(4));
    campaign.completed.remove("atlas_voices");
    var prototypes = new LinkedHashMap<String, Item>();
    prototypes.put("resilient_backbone", arkItem("ark_bus"));
    prototypes.put("renewal_engine", arkItem("renewal_engine"));
    prototypes.put("settlement_supply", arkItem("habitation_contract"));
    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
        new ItemStack(prototypes.get("resilient_backbone"), 2));
    player.getInventory().add(new ItemStack(prototypes.get("renewal_engine"), 2));
    player.getInventory().add(new ItemStack(prototypes.get("settlement_supply"), 2));
    player.getInventory().add(new ItemStack(Items.PAPER, 6));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
    player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
    var installedPos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
    helper.getLevel().setBlockAndUpdate(installedPos,
        net.minecraft.world.level.block.Blocks.CRAFTER.defaultBlockState());
    var expectedInventory = new HashMap<>(Entrelumen.availableMaterials(player));
    var expectedCompleted = new HashSet<>(campaign.completed);
    for (String id : prototypes.keySet()) {
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.DELIVER, id).success()
          && campaign.completed.equals(expectedCompleted)
          && Entrelumen.availableMaterials(player).equals(expectedInventory),
          "Early gifted sample completed an Act V delivery: " + id);
    }
    campaign.act = 5;
    for (String id : prototypes.keySet()) fifthActDenied(helper, player, id);
    fifthActDenied(helper, player, "world_network");
    helper.assertTrue(!CampaignActions.perform(player, campaignId,
        CampaignActions.Action.ADVANCE, "").success() && campaign.act == 5,
        "Missing Act IV closure advanced Act V");
    campaign.completed.add("atlas_voices");
    expectedCompleted.add("atlas_voices");

    var file = ServerQuestFile.getInstance().orElseThrow();
    var chapter = new Chapter(0x7E550001L, file, file.getDefaultChapterGroup());
    var quest = new Quest(0x7E550002L, chapter);
    chapter.addQuest(quest);
    var task = new CampaignTask(0x7E550003L, quest);
    quest.addTask(task);
    var tag = new CompoundTag();
    tag.putString("milestone", "resilient_backbone");
    task.readData(tag, helper.getLevel().registryAccess());
    var teamData = file.getOrCreateTeamData(player);
    teamData.setProgress(task, 1);
    task.submitTask(teamData, player, new ItemStack(prototypes.get("resilient_backbone")));
    helper.assertTrue(teamData.getProgress(task) == 0
        && campaign.completed.equals(expectedCompleted)
        && Entrelumen.availableMaterials(player).equals(expectedInventory),
        "FTB mirror or gifted sample granted Act V progress");

    var initial = AtlasNetwork.handleOpen(player);
    helper.assertTrue(initial.projects().stream().map(AtlasNetwork.ProjectView::id)
        .collect(java.util.stream.Collectors.toSet()).equals(Set.of(
            "resilient_backbone", "renewal_engine", "settlement_supply", "world_network")),
        "Atlas omitted an Act V delivery");
    for (var entry : prototypes.entrySet()) {
      var view = initial.projects().stream().filter(p -> p.id().equals(entry.getKey()))
          .findFirst().orElseThrow();
      helper.assertTrue(view.ready() && !view.completed()
          && view.prerequisites().equals(List.of(new AtlasNetwork.Prerequisite("atlas_voices", true)))
          && view.materials().equals(List.of(new AtlasNetwork.Material(
              BuiltInRegistries.ITEM.getKey(entry.getValue()), 2, 1))),
          "Atlas Act V material or prerequisite differs: " + entry.getKey());
    }
    var closure = initial.projects().stream().filter(p -> p.id().equals("world_network"))
        .findFirst().orElseThrow();
    helper.assertTrue(!closure.ready() && !closure.completed()
        && closure.prerequisites().stream().map(AtlasNetwork.Prerequisite::id)
            .collect(java.util.stream.Collectors.toSet()).equals(prototypes.keySet())
        && closure.prerequisites().stream().noneMatch(AtlasNetwork.Prerequisite::completed)
        && new HashSet<>(closure.materials()).equals(Set.of(
            new AtlasNetwork.Material(ResourceLocation.parse("minecraft:paper"), 6, 3),
            new AtlasNetwork.Material(ResourceLocation.parse("minecraft:copper_ingot"), 2, 1))),
        "World network closure has wrong cost or prerequisite state");

    for (var entry : prototypes.entrySet()) {
      fifthActDenied(helper, player, "world_network");
      helper.assertTrue(!CampaignActions.perform(player, campaignId,
          CampaignActions.Action.ADVANCE, "").success() && campaign.act == 5,
          "Incomplete Act V advanced");
      var delivered = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
          campaignId, CampaignActions.Action.DELIVER, entry.getKey()));
      expectedInventory.compute(BuiltInRegistries.ITEM.getKey(entry.getValue()).toString(),
          (id, count) -> count - 1);
      expectedCompleted.add(entry.getKey());
      helper.assertTrue(delivered.message().equals("entrelumen.atlas.delivered")
          && !delivered.canAdvance() && campaign.completed.equals(expectedCompleted)
          && Entrelumen.availableMaterials(player).equals(expectedInventory),
          "Act V delivery consumed surplus or granted rewards: " + entry.getKey());
      fifthActDenied(helper, player, entry.getKey());
    }
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 1,
        "FTB mirror did not reflect an authoritative Act V delivery");
    helper.assertTrue(player.getOffhandItem().getCount() == 1
        && player.getInventory().countItem(prototypes.get("renewal_engine")) == 1
        && player.getInventory().countItem(prototypes.get("settlement_supply")) == 1
        && !AtlasNetwork.handleOpen(player).canAdvance(),
        "Prototype surplus or closure gate changed");
    var paper = player.getInventory().items.stream().filter(stack -> stack.is(Items.PAPER))
        .findFirst().orElseThrow();
    paper.setCount(2);
    expectedInventory.put("minecraft:paper", 2);
    fifthActDenied(helper, player, "world_network");
    helper.assertTrue(!AtlasNetwork.handleOpen(player).projects().stream()
        .filter(p -> p.id().equals("world_network")).findFirst().orElseThrow().ready(),
        "World network accepted missing paper");
    player.getInventory().add(new ItemStack(Items.PAPER, 4));
    expectedInventory.put("minecraft:paper", 6);
    var readyClosure = AtlasNetwork.handleOpen(player).projects().stream()
        .filter(p -> p.id().equals("world_network")).findFirst().orElseThrow();
    helper.assertTrue(readyClosure.ready()
        && readyClosure.prerequisites().stream().allMatch(AtlasNetwork.Prerequisite::completed),
        "World network did not become ready after the three deliveries");
    var closed = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        campaignId, CampaignActions.Action.DELIVER, "world_network"));
    expectedInventory.compute("minecraft:paper", (id, count) -> count - 3);
    expectedInventory.compute("minecraft:copper_ingot", (id, count) -> count - 1);
    expectedCompleted.add("world_network");
    helper.assertTrue(closed.message().equals("entrelumen.atlas.delivered")
        && closed.canAdvance() && campaign.completed.equals(expectedCompleted)
        && Entrelumen.availableMaterials(player).equals(expectedInventory)
        && helper.getLevel().getBlockState(installedPos).is(net.minecraft.world.level.block.Blocks.CRAFTER),
        "World network consumed the wrong supplies, awarded an item or touched an installed machine");
    fifthActDenied(helper, player, "world_network");
    var advanced = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        campaignId, CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(advanced.act() == 6 && campaign.act == 6
        && campaign.completed.equals(expectedCompleted)
        && Entrelumen.availableMaterials(player).equals(expectedInventory)
        && command(player, "entrelumen advance") == 0 && campaign.act == 6
        && otherCampaign.act == 1 && otherCampaign.completed.equals(otherBefore)
        && CampaignActions.campaignId(other).equals(otherTeam.getId()),
        "Act V advancement changed historical progress, supplies or another campaign");
    helper.succeed();
  }

  private static void fifthActDenied(GameTestHelper helper, ServerPlayer player, String project) {
    var campaign = Entrelumen.current(player);
    var completed = Set.copyOf(campaign.completed);
    var inventory = Entrelumen.availableMaterials(player);
    var result = AtlasNetwork.handleRequest(player, new AtlasNetwork.Request(
        CampaignActions.campaignId(player), CampaignActions.Action.DELIVER, project));
    helper.assertTrue(result.message().equals("entrelumen.delivery.failed")
        && campaign.completed.equals(completed) && campaign.act == 5
        && Entrelumen.availableMaterials(player).equals(inventory),
        "Rejected Act V delivery mutated campaign or supplies: " + project);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasOpenNeedsNoItemOrCampaignToken(GameTestHelper helper) throws Exception {
    var player = player(helper, "AtlasOpenTest");
    var original = AtlasNetwork.handleOpen(player);
    helper.assertTrue(
        original.open() && original.campaign().equals(player.getUUID()),
        "Open did not resolve personal campaign");
    helper.assertTrue(
        player.getInventory().isEmpty() && Entrelumen.current(player).completed.isEmpty(),
        "Opening consumed or granted inventory/progression");
    var party =
        FTBTeamsAPI.api()
            .getManager()
            .createPartyTeam(
                player,
                "Bootstrap " + player.getUUID(),
                "",
                dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var current = AtlasNetwork.handleOpen(player);
    helper.assertTrue(
        current.campaign().equals(party.getId()) && current.open(),
        "Open reused stale personal campaign");
    helper.assertTrue(
        command(player, "entrelumen") == 1, "No-argument accessibility command failed");
    helper.assertTrue(
        player.getInventory().isEmpty() && Entrelumen.current(player).completed.isEmpty(),
        "Accessibility command changed inventory/progression");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
    try {
      AtlasNetwork.OpenRequest.CODEC.encode(buffer, AtlasNetwork.OpenRequest.INSTANCE);
      helper.assertTrue(
          buffer.readableBytes() == 0
              && AtlasNetwork.OpenRequest.CODEC
                  .decode(buffer)
                  .equals(AtlasNetwork.OpenRequest.INSTANCE),
          "Open request unexpectedly requires payload state");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }
  // Nature restoration: a 40x40 meadow template keeps every simulated canopy inside this test's area.
  private static final int NATURE_EXTENT = 40;

  private static net.minecraft.core.BlockPos natureSite(GameTestHelper helper) {
    return helper.absolutePos(new net.minecraft.core.BlockPos(20, 1, 20));
  }

  private static void natureMeadow(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < NATURE_EXTENT; x++)
      for (int z = 0; z < NATURE_EXTENT; z++) {
        level.setBlock(helper.absolutePos(new net.minecraft.core.BlockPos(x, -1, z)),
            net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(helper.absolutePos(new net.minecraft.core.BlockPos(x, 0, z)),
            net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
  }

  /** Forced template chunks load their entity sections asynchronously; never test before that. */
  private static void whenNatureLoaded(GameTestHelper helper, net.minecraft.core.BlockPos site,
      int waited, Runnable body) {
    if (NatureRestoration.loaded(helper.getLevel(), site)) {
      body.run();
      return;
    }
    helper.assertTrue(waited < 100, "Nature restoration fixture never finished loading");
    helper.runAfterDelay(1, () -> whenNatureLoaded(helper, site, waited + 1, body));
  }

  private static Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>
      natureSnapshot(GameTestHelper helper) {
    Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> states = new HashMap<>();
    for (int x = 0; x < NATURE_EXTENT; x++)
      for (int y = -1; y <= 27; y++)
        for (int z = 0; z < NATURE_EXTENT; z++) {
          var pos = helper.absolutePos(new net.minecraft.core.BlockPos(x, y, z));
          states.put(pos, helper.getLevel().getBlockState(pos));
        }
    return states;
  }

  private static Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>
      natureChanges(Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> before,
          GameTestHelper helper) {
    Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> changed = new HashMap<>();
    before.forEach((pos, state) -> {
      var now = helper.getLevel().getBlockState(pos);
      if (!now.equals(state)) changed.put(pos, now);
    });
    return changed;
  }

  private static boolean naturePlant(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)
        || state.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)
        || state.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock
            && state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
  }

  /** Restored ground, plants and whole native trees (one trunk base each) in one pass. */
  private record NatureDelta(int ground, int plants, int trunks) {}

  private static NatureDelta natureDelta(
      Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> before,
      Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> changed, int groundY) {
    int ground = 0, plants = 0, trunks = 0;
    for (var entry : changed.entrySet()) {
      int y = entry.getKey().getY();
      var now = entry.getValue();
      if (y == groundY && before.get(entry.getKey()).is(net.minecraft.world.level.block.Blocks.DIRT)
          && now.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) ground++;
      if (y == groundY + 1 && naturePlant(now)) plants++;
      if (y == groundY + 1 && now.is(net.minecraft.tags.BlockTags.LOGS)) trunks++;
    }
    return new NatureDelta(ground, plants, trunks);
  }

  private static ItemStack natureBookmark(
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
      net.minecraft.core.BlockPos target) {
    var compass = new ItemStack(Items.COMPASS);
    compass.set(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER,
        new net.minecraft.world.item.component.LodestoneTracker(
            Optional.of(net.minecraft.core.GlobalPos.of(dimension, target)), false));
    return compass;
  }

  private static void natureEvidence(String scenario, int pass, NatureRestoration.Outcome outcome) {
    com.mojang.logging.LogUtils.getLogger().info("NATURE_QA scenario={} pass={} {}", scenario, pass, outcome);
  }

  private static int natureSquare(int value) {
    return value * value;
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void natureRestorationHealsScarsAtExactCostAndSparesBuilds(GameTestHelper helper) {
    natureMeadow(helper);
    var site = natureSite(helper);
    whenNatureLoaded(helper, site, 0, () -> {
      try (var session = new WorkshopPlayer(helper)) {
        natureMeadowScenario(helper, session.player, site);
      }
      helper.succeed();
    });
  }

  private static void natureMeadowScenario(GameTestHelper helper, ServerPlayer player,
      net.minecraft.core.BlockPos site) {
    var level = helper.getLevel();
    var main = net.minecraft.world.InteractionHand.MAIN_HAND;
    var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
    var controller = ark(helper, player);
    var module = arkModule(helper, controller, "nature_module");
    var data = CampaignData.get(player.server);
    var beforeCampaign = data.save(new CompoundTag(), level.registryAccess());
    int cx = site.getX(), cz = site.getZ(), gy = site.getY() - 1;
    List<net.minecraft.core.BlockPos> scars = new ArrayList<>();
    for (int dx = -3; dx <= 1; dx++)
      for (int dz = -3; dz <= 1; dz++) {
        var pos = new net.minecraft.core.BlockPos(cx + dx, gy, cz + dz);
        level.setBlock(pos, dirt, 2);
        scars.add(pos);
      }
    var besideWall = new net.minecraft.core.BlockPos(cx + 3, gy, cz);
    var outside = new net.minecraft.core.BlockPos(cx, gy, cz + 10);
    var claimed = new net.minecraft.core.BlockPos(cx - 2, gy, cz + 6);
    var underStatue = new net.minecraft.core.BlockPos(cx - 6, gy, cz - 2);
    for (var pos : List.of(besideWall, outside, claimed, underStatue)) level.setBlock(pos, dirt, 2);
    var wall = new net.minecraft.core.BlockPos(cx + 4, gy + 1, cz);
    level.setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
    level.setBlockAndUpdate(wall.above(), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
    var chestPos = new net.minecraft.core.BlockPos(cx + 4, gy + 1, cz + 1);
    level.setBlockAndUpdate(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
    chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 7));
    var statue = new net.minecraft.world.entity.decoration.ArmorStand(level,
        underStatue.getX() + 0.5, gy + 1, underStatue.getZ() + 0.5);
    level.addFreshEntity(statue);

    var compass = natureBookmark(level.dimension(), site);
    var original = compass.copy();
    player.setItemInHand(main, compass);
    var used = player.gameMode.useItemOn(player, level, compass, main, arkHit(module));
    var team = CampaignActions.campaignId(player);
    var marks = NatureRestorationData.get(player.server);
    helper.assertTrue(used.consumesAction()
        && net.minecraft.core.GlobalPos.of(level.dimension(), site).equals(marks.site(team))
        && ItemStack.matches(original, player.getMainHandItem()),
        "Bookmarked compass did not mark the team's site or was changed");
    helper.assertTrue(NatureRestoration.mark(player, module, main).status()
        == NatureRestoration.Status.ALREADY_MARKED, "Repeated mark was not idempotent");

    java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> claim =
        event -> {
          if (event.getPos().equals(claimed)) event.setCanceled(true);
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.NORMAL, false,
        net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class, claim);
    try {
      var before = natureSnapshot(helper);
      player.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 5));
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.OAK_SAPLING, 8));
      used = player.gameMode.useItemOn(player, level, player.getMainHandItem(), main, arkHit(module));
      var changed = natureChanges(before, helper);
      var nearest = scars.stream().sorted(Comparator
              .comparingInt((net.minecraft.core.BlockPos p) -> natureSquare(p.getX() - cx) + natureSquare(p.getZ() - cz))
              .thenComparingInt(net.minecraft.core.BlockPos::getX)
              .thenComparingInt(net.minecraft.core.BlockPos::getZ))
          .limit(5).toList();
      helper.assertTrue(used.consumesAction() && player.getMainHandItem().isEmpty()
          && changed.keySet().equals(Set.copyOf(nearest))
          && changed.values().stream().allMatch(state -> state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)),
          "Five bone meal did not restore exactly the five nearest scars: " + changed.keySet());

      boolean settled = false;
      int saplings = player.getOffhandItem().getCount();
      for (int pass = 0; pass < 6 && !settled; pass++) {
        var snapshot = natureSnapshot(helper);
        player.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 64));
        var outcome = NatureRestoration.restore(player, module, main);
        natureEvidence("meadow", pass, outcome);
        var delta = natureChanges(snapshot, helper);
        var counted = natureDelta(snapshot, delta, gy);
        int spent = 64 - player.getMainHandItem().getCount();
        int planted = saplings - player.getOffhandItem().getCount();
        saplings = player.getOffhandItem().getCount();
        helper.assertTrue(spent == outcome.boneMeal() && planted == outcome.saplings()
            && outcome.ground() == counted.ground() && outcome.plants() == counted.plants()
            && outcome.trees() == counted.trunks() && planted == counted.trunks()
            && spent == counted.ground() + counted.plants()
                + NatureRestorationRules.TREE_BONE_MEAL * counted.trunks(),
            "Payment did not match applied restoration: " + outcome + " " + counted);
        helper.assertTrue(counted.trunks() > 0 || delta.keySet().stream().allMatch(pos ->
                natureSquare(pos.getX() - cx) + natureSquare(pos.getZ() - cz) <= 64
                    && pos.getY() >= gy && pos.getY() <= gy + 2),
            "Ground and flora left the marked radius");
        if (delta.isEmpty()) {
          helper.assertTrue(spent == 0 && planted == 0, "A settled site still charged materials");
          settled = true;
        }
      }
      helper.assertTrue(settled, "Repeated restoration kept changing the site");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(claim);
    }

    helper.assertTrue(level.getBlockState(besideWall).is(net.minecraft.world.level.block.Blocks.DIRT)
        && level.getBlockState(besideWall.above()).isAir()
        && level.getBlockState(outside).is(net.minecraft.world.level.block.Blocks.DIRT)
        && level.getBlockState(claimed).is(net.minecraft.world.level.block.Blocks.DIRT)
        && level.getBlockState(underStatue).is(net.minecraft.world.level.block.Blocks.DIRT)
        && scars.stream().allMatch(pos -> level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)),
        "Scar, build margin, claim, decoration or radius boundary was handled incorrectly");
    helper.assertTrue(level.getBlockState(wall).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
        && level.getBlockState(wall.above()).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
        && level.getBlockState(chestPos).is(net.minecraft.world.level.block.Blocks.CHEST)
        && ItemStack.matches(chest.getItem(0), new ItemStack(Items.WHEAT_SEEDS, 7))
        && statue.isAlive() && !statue.isRemoved(),
        "Restoration disturbed a build, container or decoration");
    var area = new net.minecraft.world.phys.AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO))
        .expandTowards(NATURE_EXTENT, 30, NATURE_EXTENT);
    helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty()
        && beforeCampaign.equals(data.save(new CompoundTag(), level.registryAccess())),
        "Restoration dropped items or changed campaign progress");
    var journal = ArkFieldJournals.snapshot(player, module, ArkFieldJournals.Kind.NATURE);
    helper.assertTrue(journal.lines().stream().anyMatch(line -> line.getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents text
            && text.getKey().equals("entrelumen.nature.journal.site")),
        "Nature journal omitted the team's restoration site");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void natureRestorationGrowsWholeNativeTreesOrNone(GameTestHelper helper) {
    natureMeadow(helper);
    var level = helper.getLevel();
    var forest = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
        .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.FOREST);
    var filled = net.minecraft.server.commands.FillBiomeCommand.fill(level,
        helper.absolutePos(new net.minecraft.core.BlockPos(0, -4, 0)),
        helper.absolutePos(new net.minecraft.core.BlockPos(NATURE_EXTENT - 1, 11, NATURE_EXTENT - 1)), forest);
    helper.assertTrue(filled.left().isPresent(), "Forest biome fixture was refused");
    var site = natureSite(helper);
    whenNatureLoaded(helper, site, 0, () -> {
      try (var session = new WorkshopPlayer(helper)) {
        natureForestScenario(helper, session.player, site);
      }
      helper.succeed();
    });
  }

  private static void natureForestScenario(GameTestHelper helper, ServerPlayer player,
      net.minecraft.core.BlockPos site) {
    var level = helper.getLevel();
    var main = net.minecraft.world.InteractionHand.MAIN_HAND;
    var off = net.minecraft.world.InteractionHand.OFF_HAND;
    helper.assertTrue(level.getBiome(site).is(net.minecraft.world.level.biome.Biomes.FOREST),
        "Fixture site is not a forest");
    var controller = ark(helper, player);
    var module = arkModule(helper, controller, "nature_module");
    var data = CampaignData.get(player.server);
    var beforeCampaign = data.save(new CompoundTag(), level.registryAccess());
    int cx = site.getX(), cz = site.getZ(), gy = site.getY() - 1;
    var post = new net.minecraft.core.BlockPos(cx + 2, gy + 1, cz - 5);
    List<net.minecraft.core.BlockPos> postBlocks = List.of(post, post.above(), post.above(2));
    for (var pos : postBlocks)
      level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
    player.setItemInHand(main, natureBookmark(level.dimension(), site));
    helper.assertTrue(NatureRestoration.mark(player, module, main).status() == NatureRestoration.Status.MARKED,
        "Forest site was not marked");
    player.setItemInHand(off, new ItemStack(Items.BIRCH_SAPLING, 16));

    int reach = NatureRestorationRules.writeReach();
    var permitted = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx - reach,
        level.getMinBuildHeight(), cz - reach, cx + reach, level.getMaxBuildHeight(), cz + reach);
    java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> noCanopy =
        event -> {
          if (permitted.isInside(event.getPos())
              && event.getPlacedBlock().is(net.minecraft.tags.BlockTags.LEAVES)) event.setCanceled(true);
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.NORMAL, false,
        net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class, noCanopy);
    try {
      var before = natureSnapshot(helper);
      player.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 64));
      var refused = NatureRestoration.restore(player, module, main);
      natureEvidence("forest-vetoed-canopy", 0, refused);
      var delta = natureChanges(before, helper);
      helper.assertTrue(refused.trees() == 0 && refused.guarded() > 0 && refused.saplings() == 0
          && player.getOffhandItem().getCount() == 16
          && delta.values().stream().noneMatch(state -> state.is(net.minecraft.tags.BlockTags.LOGS)
              || state.is(net.minecraft.tags.BlockTags.LEAVES))
          && 64 - player.getMainHandItem().getCount() == refused.plants() + refused.ground(),
          "A refused canopy left part of a tree or charged for it: " + refused);
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(noCanopy);
    }

    boolean settled = false;
    int trunks = 0;
    for (int pass = 0; pass < 6 && !settled; pass++) {
      var snapshot = natureSnapshot(helper);
      int saplings = player.getOffhandItem().getCount();
      player.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 64));
      var outcome = NatureRestoration.restore(player, module, main);
      natureEvidence("forest", pass, outcome);
      var delta = natureChanges(snapshot, helper);
      var counted = natureDelta(snapshot, delta, gy);
      int spent = 64 - player.getMainHandItem().getCount();
      int planted = saplings - player.getOffhandItem().getCount();
      helper.assertTrue(spent == outcome.boneMeal() && planted == outcome.saplings()
          && outcome.trees() == counted.trunks() && planted == counted.trunks()
          && outcome.plants() == counted.plants() && outcome.ground() == counted.ground()
          && spent == counted.ground() + counted.plants()
              + NatureRestorationRules.TREE_BONE_MEAL * counted.trunks(),
          "Tree payment did not match whole trees grown: " + outcome + " " + counted);
      for (var pos : delta.keySet()) {
        helper.assertTrue(permitted.isInside(pos), "A native tree grew outside its permitted area: " + pos);
        for (var direction : net.minecraft.core.Direction.values())
          helper.assertTrue(!postBlocks.contains(pos.relative(direction)),
              "Restoration touched the cobblestone post at " + pos);
      }
      trunks += counted.trunks();
      if (delta.isEmpty()) {
        helper.assertTrue(spent == 0 && planted == 0, "A settled forest site still charged materials");
        settled = true;
      }
    }
    helper.assertTrue(settled && trunks >= 1, "Forest restoration grew no native tree or never settled");
    helper.assertTrue(postBlocks.stream().allMatch(pos -> level.getBlockState(pos)
            .is(net.minecraft.world.level.block.Blocks.COBBLESTONE)),
        "Restoration changed the cobblestone post");
    List<net.minecraft.core.BlockPos> bases = new ArrayList<>();
    for (int x = cx - reach; x <= cx + reach; x++)
      for (int z = cz - reach; z <= cz + reach; z++) {
        var pos = new net.minecraft.core.BlockPos(x, gy + 1, z);
        if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) bases.add(pos);
      }
    for (var first : bases)
      for (var second : bases)
        helper.assertTrue(first.equals(second) || Math.max(Math.abs(first.getX() - second.getX()),
                Math.abs(first.getZ() - second.getZ())) > 3,
            "Native trees were planted closer than their spacing: " + first + " " + second);
    var area = new net.minecraft.world.phys.AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO))
        .expandTowards(NATURE_EXTENT, 30, NATURE_EXTENT);
    helper.assertTrue(bases.size() == trunks
        && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty()
        && beforeCampaign.equals(data.save(new CompoundTag(), level.registryAccess())),
        "Tree count, drops or campaign progress were wrong");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void natureRestorationRefusesWithoutMutation(GameTestHelper helper) {
    natureMeadow(helper);
    var site = natureSite(helper);
    whenNatureLoaded(helper, site, 0, () -> {
      try (var session = new WorkshopPlayer(helper)) {
        natureRefusalScenario(helper, session.player, site);
      }
      helper.succeed();
    });
  }

  private static void natureRefusalScenario(GameTestHelper helper, ServerPlayer player,
      net.minecraft.core.BlockPos site) {
    var level = helper.getLevel();
    var main = net.minecraft.world.InteractionHand.MAIN_HAND;
    var controller = ark(helper, player);
    var module = arkModule(helper, controller, "nature_module");
    int cx = site.getX(), cz = site.getZ(), gy = site.getY() - 1;
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++)
        level.setBlock(new net.minecraft.core.BlockPos(cx + dx, gy, cz + dz),
            net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
    var team = CampaignActions.campaignId(player);
    var marks = NatureRestorationData.get(player.server);
    var before = natureSnapshot(helper);

    player.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 64));
    helper.assertTrue(NatureRestoration.restore(player, module, main).status() == NatureRestoration.Status.NO_SITE
        && player.getMainHandItem().getCount() == 64, "Restoration ran without a marked site");
    player.setItemInHand(main, new ItemStack(Items.COMPASS));
    helper.assertTrue(NatureRestoration.mark(player, module, main).status()
        == NatureRestoration.Status.UNBOUND_COMPASS, "An unbound compass marked a site");
    player.setItemInHand(main, natureBookmark(net.minecraft.world.level.Level.NETHER, site));
    helper.assertTrue(NatureRestoration.mark(player, module, main).status()
        == NatureRestoration.Status.OTHER_DIMENSION, "A site in another dimension was accepted");
    player.setItemInHand(main, natureBookmark(level.dimension(), site.offset(200, 0, 0)));
    helper.assertTrue(NatureRestoration.mark(player, module, main).status()
        == NatureRestoration.Status.TOO_FAR, "A distant site was accepted");
    var engineering = arkModule(helper, controller, "engineering_module");
    var engineeringState = level.getBlockState(engineering);
    level.removeBlock(engineering, false);
    player.setItemInHand(main, natureBookmark(level.dimension(), site));
    helper.assertTrue(NatureRestoration.mark(player, module, main).status()
        == NatureRestoration.Status.STRUCTURE && marks.site(team) == null,
        "An incomplete Ark marked a site");
    level.setBlockAndUpdate(engineering, engineeringState);
    helper.assertTrue(NatureRestoration.mark(player, module, main).status() == NatureRestoration.Status.MARKED,
        "A complete Ark refused a valid site");

    level.removeBlock(engineering, false);
    player.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 64));
    helper.assertTrue(NatureRestoration.restore(player, module, main).status() == NatureRestoration.Status.STRUCTURE,
        "An incomplete Ark restored the site");
    level.setBlockAndUpdate(engineering, engineeringState);
    level.setBlockAndUpdate(controller.above(), level.getBlockState(controller));
    helper.assertTrue(NatureRestoration.restore(player, module, main).status() == NatureRestoration.Status.STRUCTURE,
        "Ambiguous controllers restored the site");
    level.removeBlock(controller.above(), false);
    helper.assertTrue(NatureRestoration.restore(player, module, net.minecraft.world.InteractionHand.OFF_HAND)
        .status() == NatureRestoration.Status.UNAVAILABLE, "Offhand use restored the site");
    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    helper.assertTrue(NatureRestoration.restore(player, module, main).status() == NatureRestoration.Status.UNAVAILABLE,
        "A spectator restored the site");
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    player.teleportTo(module.getX() + 30.5, module.getY() + 1, module.getZ() + 0.5);
    helper.assertTrue(NatureRestoration.restore(player, module, main).status() == NatureRestoration.Status.UNAVAILABLE,
        "A remote player restored the site");
    player.teleportTo(controller.getX() + 0.5, controller.getY() + 1.0, controller.getZ() + 0.5);

    var guest = player(helper, "NatureGuest");
    guest.teleportTo(controller.getX() + 0.5, controller.getY() + 1.0, controller.getZ() + 0.5);
    guest.setItemInHand(main, new ItemStack(Items.BONE_MEAL, 64));
    helper.assertTrue(NatureRestoration.restore(guest, module, main).status() == NatureRestoration.Status.NO_SITE
        && guest.getMainHandItem().getCount() == 64
        && ArkFieldJournals.snapshot(guest, module, ArkFieldJournals.Kind.NATURE).lines().stream()
            .anyMatch(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("entrelumen.nature.journal.none")),
        "Another team used or saw this team's restoration site");

    java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny =
        event -> {
          if (event.getEntity() == player) event.setCanceled(true);
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.NORMAL, false,
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, deny);
    try {
      player.gameMode.useItemOn(player, level, player.getMainHandItem(), main, arkHit(module));
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);
    }
    helper.assertTrue(player.getMainHandItem().getCount() == 64 && natureChanges(before, helper).isEmpty(),
        "A refused or canceled request consumed bone meal or changed the site");

    var saved = marks.save(new CompoundTag(), level.registryAccess());
    helper.assertTrue(net.minecraft.core.GlobalPos.of(level.dimension(), site).equals(
            NatureRestorationData.load(saved, level.registryAccess()).site(team)),
        "The team's site did not survive a save round trip");
    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    var paid = NatureRestoration.restore(player, module, main);
    helper.assertTrue(paid.status() == NatureRestoration.Status.RESTORED && paid.ground() == 9
        && paid.boneMeal() > 0 && 64 - player.getMainHandItem().getCount() == paid.boneMeal(),
        "Creative restoration bypassed payment: " + paid);
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    level.removeBlock(module, false);
    helper.assertTrue(NatureRestoration.restore(player, module, main).status() == NatureRestoration.Status.UNAVAILABLE,
        "A removed module still restored the site");
  }

  // Exploration chart room: field maps compile into a held chart at a complete Ark.
  private static byte chartColour(net.minecraft.world.level.material.MapColor colour) {
    return colour.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL);
  }

  private static ItemStack chartMap(GameTestHelper helper, int x, int z, int scale) {
    return net.minecraft.world.item.MapItem.create(helper.getLevel(), x, z, (byte) scale, true, false);
  }

  private static net.minecraft.world.level.saveddata.maps.MapItemSavedData chartData(
      GameTestHelper helper, ItemStack map) {
    return net.minecraft.world.item.MapItem.getSavedData(map, helper.getLevel());
  }

  private static void chartFill(net.minecraft.world.level.saveddata.maps.MapItemSavedData data,
      int x0, int z0, int x1, int z1, byte colour) {
    for (int x = x0; x < x1; x++)
      for (int z = z0; z < z1; z++) data.colors[x + z * 128] = colour;
  }

  private static int chartAt(net.minecraft.world.level.saveddata.maps.MapItemSavedData data, int x, int z) {
    return data.colors[x + z * 128];
  }

  private static List<ItemStack> chartInventory(ServerPlayer player) {
    List<ItemStack> stacks = new ArrayList<>();
    for (var stack : player.getInventory().items) stacks.add(stack.copy());
    for (var stack : player.getInventory().armor) stacks.add(stack.copy());
    for (var stack : player.getInventory().offhand) stacks.add(stack.copy());
    return stacks;
  }

  private static boolean chartInventoryUnchanged(List<ItemStack> before, ServerPlayer player) {
    var now = chartInventory(player);
    if (now.size() != before.size()) return false;
    for (int i = 0; i < now.size(); i++)
      if (!ItemStack.matches(before.get(i), now.get(i))) return false;
    return true;
  }

  private static ItemStack chartInDimension(GameTestHelper helper,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
    var level = helper.getLevel();
    var id = level.getFreeMapId();
    level.setMapData(id, net.minecraft.world.level.saveddata.maps.MapItemSavedData.createFresh(
        x, z, (byte) 0, true, false, dimension));
    var map = new ItemStack(Items.FILLED_MAP);
    map.set(net.minecraft.core.component.DataComponents.MAP_ID, id);
    return map;
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void explorationChartsCompileFieldMapsWithoutConsumingAnything(GameTestHelper helper)
      throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var level = helper.getLevel();
      var main = net.minecraft.world.InteractionHand.MAIN_HAND;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "exploration_module");
      // An early visitor with gifted maps: the chart room never reads or writes the campaign.
      var campaign = Entrelumen.current(player);
      campaign.act = 1;
      campaign.completed.clear();
      var data = CampaignData.get(player.server);
      var beforeCampaign = data.save(new CompoundTag(), level.registryAccess());

      byte grass = chartColour(net.minecraft.world.level.material.MapColor.GRASS);
      byte water = chartColour(net.minecraft.world.level.material.MapColor.WATER);
      byte stone = chartColour(net.minecraft.world.level.material.MapColor.STONE);
      byte sand = chartColour(net.minecraft.world.level.material.MapColor.SAND);
      byte snow = chartColour(net.minecraft.world.level.material.MapColor.SNOW);
      byte wood = chartColour(net.minecraft.world.level.material.MapColor.WOOD);
      // A blank scale-1 chart, as a cartography table zoom-out leaves it, and its field tiles.
      var chart = chartMap(helper, controller.getX(), controller.getZ(), 1);
      var target = chartData(helper, chart);
      var grid = ArkCharts.grid(target);
      int ox = (int) grid.originX(), oz = (int) grid.originZ();
      target.colors[0] = wood;
      var northwest = chartMap(helper, ox + 10, oz + 10, 0);
      chartFill(chartData(helper, northwest), 0, 0, 128, 128, grass);
      var northeast = chartMap(helper, ox + 138, oz + 10, 0);
      chartFill(chartData(helper, northeast), 0, 0, 64, 128, water);
      var southwest = chartMap(helper, ox + 10, oz + 138, 0);
      var southwestData = chartData(helper, southwest);
      southwestData.colors[0] = stone;
      southwestData.colors[1] = stone;
      southwestData.colors[128] = sand;
      southwestData.colors[2] = sand;
      southwestData.colors[3] = stone;
      var older = chartMap(helper, controller.getX(), controller.getZ(), 1);
      var olderData = chartData(helper, older);
      olderData.colors[120 + 120 * 128] = snow;
      olderData.colors[127 + 127 * 128] = snow;
      olderData.colors[0] = snow;
      // Left out: another dimension, a coarser scale and a distant area.
      var nether = chartInDimension(helper, net.minecraft.world.level.Level.NETHER, ox + 10, oz + 10);
      chartFill(chartData(helper, nether), 0, 0, 128, 128, stone);
      var coarse = chartMap(helper, controller.getX(), controller.getZ(), 2);
      chartFill(chartData(helper, coarse), 0, 0, 128, 128, sand);
      var distant = chartMap(helper, ox + 5000, oz, 0);
      chartFill(chartData(helper, distant), 0, 0, 128, 128, sand);
      List<net.minecraft.world.level.saveddata.maps.MapItemSavedData> sources = List.of(
          chartData(helper, northwest), chartData(helper, northeast), southwestData, olderData,
          chartData(helper, nether), chartData(helper, coarse), chartData(helper, distant));
      List<byte[]> sourceColours = sources.stream().map(source -> source.colors.clone()).toList();

      var inventory = player.getInventory();
      inventory.selected = 0;
      inventory.setItem(0, chart);
      inventory.setItem(1, northwest);
      inventory.setItem(2, northeast.copyWithCount(2));
      inventory.setItem(3, older);
      inventory.setItem(4, nether);
      inventory.setItem(5, coarse);
      inventory.setItem(6, distant);
      inventory.setItem(7, chart.copy());
      inventory.setItem(9, northeast.copy());
      inventory.setItem(10, new ItemStack(Items.MAP, 3));
      inventory.setItem(11, new ItemStack(Items.PAPER, 5));
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, southwest);
      var beforeInventory = chartInventory(player);
      var beforeChart = target.colors.clone();
      target.setDirty(false);

      var result = player.gameMode.useItemOn(player, level, chart, main, arkHit(module));
      helper.assertTrue(result.consumesAction(), "Native chart interaction did not run");
      int changed = 0;
      for (int i = 0; i < beforeChart.length; i++) if (beforeChart[i] != target.colors[i]) changed++;
      // North-west quarter less the pre-drawn corner, the explored half of the north-east quarter,
      // two south-west pixels and two south-east pixels from the older chart.
      helper.assertTrue(changed == 64 * 64 - 1 + 32 * 64 + 2 + 2,
          "Unexpected number of charted pixels: " + changed);
      helper.assertTrue(chartAt(target, 0, 0) == wood && chartAt(target, 63, 63) == grass
          && chartAt(target, 64, 0) == water && chartAt(target, 95, 63) == water
          && chartAt(target, 96, 0) == 0 && chartAt(target, 0, 64) == stone
          && chartAt(target, 1, 64) == (Byte.toUnsignedInt(stone) < Byte.toUnsignedInt(sand) ? stone : sand)
          && chartAt(target, 2, 64) == 0 && chartAt(target, 120, 120) == snow
          && chartAt(target, 127, 127) == snow && chartAt(target, 100, 100) == 0,
          "Charted pixels do not follow their field maps");
      helper.assertTrue(target.isDirty(), "The chart's saved data was not marked for saving");
      var reloaded = net.minecraft.world.level.saveddata.maps.MapItemSavedData.load(
          target.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
      helper.assertTrue(java.util.Arrays.equals(reloaded.colors, target.colors) && !reloaded.locked,
          "The compiled chart did not survive a save round trip");
      for (int i = 0; i < sources.size(); i++)
        helper.assertTrue(java.util.Arrays.equals(sourceColours.get(i), sources.get(i).colors),
            "A field map was changed by compiling");
      helper.assertTrue(chartInventoryUnchanged(beforeInventory, player),
          "Compiling consumed, created or changed an item");
      helper.assertTrue(beforeCampaign.equals(data.save(new CompoundTag(), level.registryAccess())),
          "Compiling changed campaign data");

      var compiled = target.colors.clone();
      target.setDirty(false);
      var repeat = ArkCharts.compile(player, module, main);
      helper.assertTrue(repeat.status() == ArkCharts.Status.NOTHING_NEW && repeat.matched() == 4
          && repeat.skipped() == 3 && repeat.pixels() == 0,
          "A repeated compilation was not a no-op: " + repeat);
      helper.assertTrue(java.util.Arrays.equals(compiled, target.colors) && !target.isDirty()
          && chartInventoryUnchanged(beforeInventory, player), "A repeated compilation changed something");
      helper.assertTrue(ArkFieldJournals.snapshot(player, module, ArkFieldJournals.Kind.EXPLORATION).lines()
          .stream().anyMatch(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
              && text.getKey().equals("entrelumen.exploration.chart.journal")),
          "The exploration journal does not explain the chart room");
      helper.assertTrue(beforeCampaign.equals(data.save(new CompoundTag(), level.registryAccess())),
          "Reading or repeating changed campaign data");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void explorationChartsRefuseWithoutMutation(GameTestHelper helper) throws Exception {
    try (var session = new WorkshopPlayer(helper)) {
      var player = session.player;
      var level = helper.getLevel();
      var main = net.minecraft.world.InteractionHand.MAIN_HAND;
      var controller = ark(helper, player);
      var module = arkModule(helper, controller, "exploration_module");
      byte grass = chartColour(net.minecraft.world.level.material.MapColor.GRASS);
      var chart = chartMap(helper, controller.getX(), controller.getZ(), 0);
      var target = chartData(helper, chart);
      var field = chartMap(helper, controller.getX(), controller.getZ(), 0);
      var fieldData = chartData(helper, field);
      chartFill(fieldData, 0, 0, 128, 128, grass);
      var fieldColours = fieldData.colors.clone();
      var blank = target.colors.clone();
      var inventory = player.getInventory();
      inventory.selected = 0;
      inventory.setItem(0, chart);

      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.NO_SOURCES,
          "A lone chart compiled");
      var nether = chartInDimension(helper, net.minecraft.world.level.Level.NETHER,
          controller.getX(), controller.getZ());
      chartFill(chartData(helper, nether), 0, 0, 128, 128, grass);
      var coarse = chartMap(helper, controller.getX(), controller.getZ(), 1);
      chartFill(chartData(helper, coarse), 0, 0, 128, 128, grass);
      inventory.setItem(1, nether);
      inventory.setItem(2, coarse);
      var onlySkipped = ArkCharts.compile(player, module, main);
      helper.assertTrue(onlySkipped.status() == ArkCharts.Status.NO_SOURCES && onlySkipped.skipped() == 2,
          "Another dimension or a coarser scale was used: " + onlySkipped);
      inventory.setItem(3, field);
      var before = chartInventory(player);

      var locked = chart.copy();
      net.minecraft.world.item.MapItem.lockMap(level, locked);
      var lockedData = chartData(helper, locked);
      helper.assertTrue(lockedData.locked, "Lock fixture failed");
      inventory.setItem(0, locked);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.LOCKED
          && java.util.Arrays.equals(blank, lockedData.colors), "A locked chart changed");
      var missing = new ItemStack(Items.FILLED_MAP);
      inventory.setItem(0, missing);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.NO_DATA,
          "A map without an id compiled");
      var unknown = new ItemStack(Items.FILLED_MAP);
      unknown.set(net.minecraft.core.component.DataComponents.MAP_ID,
          new net.minecraft.world.level.saveddata.maps.MapId(Integer.MAX_VALUE - 7));
      inventory.setItem(0, unknown);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.NO_DATA,
          "A map without saved data compiled");
      inventory.setItem(0, chart);

      var engineering = arkModule(helper, controller, "engineering_module");
      var engineeringState = level.getBlockState(engineering);
      level.removeBlock(engineering, false);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.STRUCTURE,
          "An incomplete Ark compiled");
      level.setBlockAndUpdate(engineering, engineeringState);
      level.setBlockAndUpdate(controller.above(), level.getBlockState(controller));
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.STRUCTURE,
          "Ambiguous controllers compiled");
      level.removeBlock(controller.above(), false);
      helper.assertTrue(ArkCharts.compile(player, module, net.minecraft.world.InteractionHand.OFF_HAND)
          .status() == ArkCharts.Status.UNAVAILABLE, "The offhand compiled");
      helper.assertTrue(ArkCharts.compile(player, arkModule(helper, controller, "nature_module"), main)
          .status() == ArkCharts.Status.UNAVAILABLE, "Another module compiled");
      player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.UNAVAILABLE,
          "A spectator compiled");
      player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
      player.teleportTo(module.getX() + 30.5, module.getY() + 1, module.getZ() + 0.5);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.UNAVAILABLE,
          "A remote player compiled");
      player.teleportTo(controller.getX() + 0.5, controller.getY() + 1.0, controller.getZ() + 0.5);

      java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny =
          event -> {
            if (event.getEntity() == player) event.setCanceled(true);
          };
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
          net.neoforged.bus.api.EventPriority.NORMAL, false,
          net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, deny);
      try {
        player.gameMode.useItemOn(player, level, chart, main, arkHit(module));
      } finally {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);
      }
      // An empty main hand with the chart in the offhand keeps the journal gesture.
      inventory.setItem(0, ItemStack.EMPTY);
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, chart);
      player.gameMode.useItemOn(player, level, ItemStack.EMPTY, main, arkHit(module));
      player.gameMode.useItemOn(player, level, chart, net.minecraft.world.InteractionHand.OFF_HAND, arkHit(module));
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
      inventory.setItem(0, chart);
      helper.assertTrue(java.util.Arrays.equals(blank, target.colors)
          && java.util.Arrays.equals(fieldColours, fieldData.colors)
          && chartInventoryUnchanged(before, player),
          "A refused or canceled request changed a chart or the inventory");

      var accepted = ArkCharts.compile(player, module, main);
      helper.assertTrue(accepted.status() == ArkCharts.Status.COMPILED && accepted.pixels() == 128 * 128
          && accepted.matched() == 1 && accepted.contributed() == 1 && accepted.skipped() == 2
          && java.util.Arrays.equals(fieldColours, target.colors)
          && chartInventoryUnchanged(before, player),
          "The same request failed once the Ark and gesture were valid: " + accepted);
      level.removeBlock(module, false);
      helper.assertTrue(ArkCharts.compile(player, module, main).status() == ArkCharts.Status.UNAVAILABLE,
          "A removed module still compiled");
    }
    helper.succeed();
  }
}
