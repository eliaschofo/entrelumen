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
    helper.assertTrue(clicked.consumesAction()
        && campaign.completed.contains(CampaignMilestones.LAST_HORIZON)
        && campaign.arkPhase == 6 && campaign.arkDeposits.isEmpty()
        && Entrelumen.availableMaterials(player).equals(materials),
        "Explicit empty-hand controller activation failed or consumed supplies");
    var completed = Set.copyOf(campaign.completed);
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
        net.minecraft.world.InteractionHand.MAIN_HAND, arkHit(pos));
    helper.assertTrue(campaign.completed.equals(completed)
        && Entrelumen.availableMaterials(player).equals(materials)
        && helper.getLevel().getBlockState(removedPos).equals(module),
        "Finished controller replay changed campaign, supplies or blocks");
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
    helper.assertTrue(prototypes.values().stream().allMatch(item -> player.getInventory().countItem(item) == 1)
        && player.getInventory().countItem(Items.ANVIL) == 1
        && player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 9
        && helper.getLevel().getBlockState(installedPos).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE),
        "Deliveries granted items or consumed infrastructure/unrelated supplies");
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
    helper.assertTrue(prototypes.values().stream().allMatch(item -> player.getInventory().countItem(item) == 1)
        && player.getInventory().countItem(Items.ANVIL) == 1
        && player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 10
        && helper.getLevel().getBlockState(installedPos).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE),
        "Deliveries granted items or consumed infrastructure/unrelated supplies");
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
      expectedCompleted.add(entry.getKey());
      helper.assertTrue(result.message().equals("entrelumen.atlas.delivered") && campaign.act == 4
          && campaign.completed.equals(expectedCompleted)
          && Entrelumen.availableMaterials(player).equals(expectedInventory),
          "Act IV delivery changed unrelated progress, consumed surplus or granted rewards: " + entry.getKey());
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
}
