package dev.entrelumen;

import com.mojang.brigadier.arguments.*;
import dev.ftb.mods.ftbteams.api.*;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.*;

@Mod("entrelumen")
public final class Entrelumen {
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "entrelumen");
  public static final DeferredBlock<LogisticsModuleBlock> LOGISTICS_MODULE = BLOCKS.register(
      "logistics_module", () -> new LogisticsModuleBlock(
          BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops()));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticsStock>>
      LOGISTICS_STOCK = BLOCK_ENTITIES.register("logistics_stock",
          () -> BlockEntityType.Builder.of(LogisticsStock::new, LOGISTICS_MODULE.get()).build(null));
  public static final Set<String> MODULES =
      Set.of(
          "engineering_module",
          "arcane_module",
          "nature_module",
          "exploration_module",
          "logistics_module",
          "habitation_module");

  static {
    IntegrationItems.register(ITEMS);
    ApotheosisContent.register(ITEMS, BLOCKS, BLOCK_ENTITIES);
    ITEMS.register(
        "atlas",
        () ->
            new Item(new Item.Properties().stacksTo(1)) {
              @Override
              public InteractionResultHolder<ItemStack> use(
                  Level level, Player player, InteractionHand hand) {
                if (player instanceof ServerPlayer serverPlayer) AtlasNetwork.open(serverPlayer);
                return InteractionResultHolder.sidedSuccess(
                    player.getItemInHand(hand), level.isClientSide);
              }
            });
    for (String id : List.of("raw_lens", "survey_notes", "signal_core"))
      ITEMS.registerSimpleItem(id);
    for (String id : MODULES) {
      if (id.equals("engineering_module")) {
        var engineering = BLOCKS.register(id, () -> new EngineeringModuleBlock(
            BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops()));
        ITEMS.register(id, () -> new EngineeringModuleItem(engineering.get(), new Item.Properties()));
        continue;
      }
      if (id.equals("logistics_module")) {
        ITEMS.register(id, () -> new LogisticsModuleItem(LOGISTICS_MODULE.get(), new Item.Properties()));
        continue;
      }
      ArkFieldJournals.Kind journalKind = null;
      for (var kind : ArkFieldJournals.Kind.values())
        if (kind.module().equals(id)) journalKind = kind;
      if (journalKind != null) {
        var selected = journalKind;
        var journal = BLOCKS.register(id, () -> new ArkFieldJournalBlock(selected,
            BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops()));
        ITEMS.register(id, () -> new ArkFieldJournalItem(journal.get(), new Item.Properties()));
        continue;
      }
      var block =
          BLOCKS.registerSimpleBlock(
              id, BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops());
      ITEMS.registerSimpleBlockItem(id, block);
    }
    var surveyStation = BLOCKS.register("survey_station", () -> new SignalStationBlock(
        BlockBehaviour.Properties.of().strength(3f).sound(SoundType.WOOD).noOcclusion().requiresCorrectToolForDrops()));
    ITEMS.registerSimpleBlockItem("survey_station", surveyStation);
    var controller = BLOCKS.register("ark_controller",
        () -> new ArkControllerBlock(BlockBehaviour.Properties.of().strength(4f)));
    ITEMS.registerSimpleBlockItem("ark_controller", controller);
  }

  public Entrelumen(IEventBus bus) {
    ITEMS.register(bus);
    bus.addListener(AtlasNetwork::register);
    bus.addListener(JournalBookNetwork::register);
    ApotheosisContent.bootstrap(bus);
    BLOCKS.register(bus);
    BLOCK_ENTITIES.register(bus);
    Altars.register(bus);
    bus.addListener(this::registerCapabilities);
    NeoForge.EVENT_BUS.addListener(this::commands);
    NeoForge.EVENT_BUS.addListener(Expeditions::onDimensionChanged);
    NeoForge.EVENT_BUS.addListener(Expeditions::onLogin);
    NeoForge.EVENT_BUS.addListener(Expeditions::onRespawn);
    NeoForge.EVENT_BUS.addListener(ArkHabitation::onLogin);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
        ArkHabitation::onRespawnPosition);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
        ArkHabitation::onPostRespawn);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
        ArkFieldJournalBlock::allowCrouchedBedUse);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
        ArkControllerBlock::allowEmptyHandDeposit);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
        LogisticsModuleBlock::allowCrouchedUse);
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.AddReloadListenerEvent event) ->
            event.addListener(new ProjectReloadListener()));
    CampaignTask.register();
    NeoForge.EVENT_BUS.addListener(CampaignTask::tick);
    TeamEvent.CREATED.register(
        event -> {
          if (event.getTeam().isPartyTeam()) {
            var data = CampaignData.get(FTBTeamsAPI.api().getManager().getServer());
            data.campaigns.party(event.getTeam().getId(), event.getCreatorId());
            data.setDirty();
          }
        });
    TeamEvent.DELETED.register(
        event -> {
          if (event.getTeam().isPartyTeam()) {
            var data = CampaignData.get(FTBTeamsAPI.api().getManager().getServer());
            data.campaigns.archive(event.getTeam().getId());
            data.setDirty();
          }
        });
  }

  private void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LOGISTICS_STOCK.get(),
        (stock, side) -> stock.itemHandler());
  }

  public static Campaigns.Campaign current(ServerPlayer player) {
    var data = CampaignData.get(player.server);
    Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
    boolean exists =
        team.isPartyTeam()
            ? data.campaigns.parties.containsKey(team.getId())
            : data.campaigns.personal.containsKey(player.getUUID());
    var result =
        data.campaigns.current(
            player.getUUID(), team.isPartyTeam() ? team.getId() : null, team.getOwner());
    if (!exists) data.setDirty();
    return result;
  }

  public static void status(ServerPlayer player) {
    statusLines(player).forEach(player::sendSystemMessage);
  }

  static List<ItemStack> deliveryStacks(ServerPlayer player) {
    return java.util.stream.Stream.concat(
            player.getInventory().items.stream(), player.getInventory().offhand.stream())
        .toList();
  }

  static Map<String, Integer> availableMaterials(ServerPlayer player) {
    Map<String, Integer> result = new HashMap<>();
    for (var stack : deliveryStacks(player))
      result.merge(
          BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
          stack.getCount(),
          Integer::sum);
    return result;
  }

  public static List<Component> statusLines(ServerPlayer player) {
    var c = current(player);
    var inventory = availableMaterials(player);
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable("entrelumen.status", c.act, c.completed.size(), c.arkPhase));
    Projects.all()
        .forEach(
            (id, project) -> {
              if (project.act() != c.act || c.completed.contains(id)) return;
              lines.add(
                  Component.translatable(
                      "entrelumen.project.pending",
                      Component.translatable("entrelumen.project." + id),
                      id));
              project.prerequisites().stream()
                  .sorted()
                  .forEach(
                      prerequisite ->
                          lines.add(
                              Component.translatable(
                                  c.completed.contains(prerequisite)
                                      ? "entrelumen.project.prerequisite.complete"
                                      : "entrelumen.project.prerequisite.missing",
                                  Component.translatable("entrelumen.project." + prerequisite))));
              project.items().entrySet().stream()
                  .sorted(Map.Entry.comparingByKey())
                  .forEach(
                      entry -> {
                        var item =
                            BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.parse(entry.getKey()));
                        int available = inventory.getOrDefault(entry.getKey(), 0);
                        lines.add(
                            Component.translatable(
                                "entrelumen.project.material",
                                new ItemStack(item).getHoverName(),
                                available,
                                entry.getValue(),
                                Math.max(0, entry.getValue() - available)));
                      });
            });
    return List.copyOf(lines);
  }

  static int deliver(ServerPlayer player, String id) {
    var p = Projects.all().get(id);
    var c = current(player);
    if (p == null || !c.completed.containsAll(p.prerequisites())) {
      player.sendSystemMessage(Component.translatable("entrelumen.delivery.failed"));
      return 0;
    }
    Map<String, Integer> inventory = availableMaterials(player);
    boolean ok =
        Campaigns.deliver(
            c,
            id,
            p.act(),
            p.items(),
            inventory,
            () -> {
              p.items()
                  .forEach(
                      (item, count) -> {
                        int remaining = count;
                        for (var stack : deliveryStacks(player))
                          if (BuiltInRegistries.ITEM
                              .getKey(stack.getItem())
                              .toString()
                              .equals(item)) {
                            int take = Math.min(remaining, stack.getCount());
                            stack.shrink(take);
                            remaining -= take;
                          }
                      });
              player.getInventory().setChanged();
              player.containerMenu.broadcastChanges();
            });
    if (ok) {
      CampaignData.get(player.server).setDirty();
      if (!p.reward().isEmpty()) {
        var reward =
            new ItemStack(
                BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(p.reward())));
        Component rewardName = reward.getHoverName();
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        player.sendSystemMessage(Component.translatable("entrelumen.delivery.reward", rewardName));
      }
    }
    player.sendSystemMessage(
        Component.translatable(
            ok ? "entrelumen.delivery.success" : "entrelumen.delivery.failed",
            Component.translatable("entrelumen.project." + id)));
    return ok ? 1 : 0;
  }

  static int advance(ServerPlayer player) {
    var campaign = current(player);
    boolean ok = Campaigns.advance(campaign, Projects.forAct(campaign.act));
    if (ok) CampaignData.get(player.server).setDirty();
    player.sendSystemMessage(
        Component.translatable(
            ok ? "entrelumen.advance.success" : "entrelumen.advance.failed", campaign.act));
    return ok ? 1 : 0;
  }

  private void commands(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("entrelumen")
                .executes(
                    ctx -> {
                      AtlasNetwork.open(ctx.getSource().getPlayerOrException());
                      return 1;
                    })
                .then(
                    Commands.literal("status")
                        .executes(
                            ctx -> {
                              status(ctx.getSource().getPlayerOrException());
                              return 1;
                            }))
                .then(
                    Commands.literal("deliver")
                        .then(
                            Commands.argument("project", StringArgumentType.word())
                                .suggests(
                                    (ctx, builder) -> {
                                      Projects.all().keySet().forEach(builder::suggest);
                                      return builder.buildFuture();
                                    })
                                .executes(
                                    ctx ->
                                        CampaignActions.perform(
                                                    ctx.getSource().getPlayerOrException(),
                                                    CampaignActions.campaignId(
                                                        ctx.getSource().getPlayerOrException()),
                                                    CampaignActions.Action.DELIVER,
                                                    StringArgumentType.getString(ctx, "project"))
                                                .success()
                                            ? 1
                                            : 0)))
                .then(
                    Commands.literal("advance")
                        .executes(
                            ctx -> {
                              var player = ctx.getSource().getPlayerOrException();
                              return CampaignActions.perform(
                                          player,
                                          CampaignActions.campaignId(player),
                                          CampaignActions.Action.ADVANCE,
                                          "")
                                      .success()
                                  ? 1
                                  : 0;
                            }))
                .then(
                    Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.literal("diagnostic")
                                .executes(
                                    ctx -> {
                                      var d = CampaignData.get(ctx.getSource().getServer());
                                      ctx.getSource()
                                          .sendSuccess(
                                              () ->
                                                  Component.literal(
                                                      "schema=2 personal="
                                                          + d.campaigns.personal.size()
                                                          + " parties="
                                                          + d.campaigns.parties.size()),
                                              false);
                                      return 1;
                                    }))
                        .then(
                            Commands.literal("recover")
                                .then(
                                    Commands.argument("uuid", StringArgumentType.word())
                                        .executes(
                                            ctx -> {
                                              UUID id;
                                              try {
                                                id =
                                                    UUID.fromString(
                                                        StringArgumentType.getString(ctx, "uuid"));
                                              } catch (IllegalArgumentException e) {
                                                return 0;
                                              }
                                              var player = ctx.getSource().getPlayerOrException();
                                              var d = CampaignData.get(player.server);
                                              var archived = d.campaigns.parties.get(id);
                                              if (archived == null || !archived.archived) return 0;
                                              var team =
                                                  FTBTeamsAPI.api()
                                                      .getManager()
                                                      .getTeamForPlayer(player)
                                                      .orElseThrow();
                                              if (team.isPartyTeam())
                                                d.campaigns.parties.put(
                                                    team.getId(), archived.copy());
                                              else
                                                d.campaigns.personal.put(
                                                    player.getUUID(), archived.copy());
                                              d.setDirty();
                                              status(player);
                                              return 1;
                                            })))
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("act", IntegerArgumentType.integer(1, 6))
                                        .executes(
                                            ctx -> {
                                              var p = ctx.getSource().getPlayerOrException();
                                              current(p).act =
                                                  IntegerArgumentType.getInteger(ctx, "act");
                                              CampaignData.get(p.server).setDirty();
                                              return 1;
                                            })))));
  }
}
