package dev.entrelumen;

import com.mojang.brigadier.arguments.*;
import dev.ftb.mods.ftbteams.api.*;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
  /**
   * The six Ark modules are one-time rewards without a recipe, so none may be lost: blast-proof (3 to
   * mine, 1200 against explosions, wither- and dragon-immune by tag), dropped by any tool, and as items
   * fireproof, undamageable and never despawning ({@link ArkFieldJournalItem}).
   */
  static BlockBehaviour.Properties moduleProperties() {
    return BlockBehaviour.Properties.of().strength(3f, 1200f);
  }

  public static final DeferredBlock<LogisticsModuleBlock> LOGISTICS_MODULE = BLOCKS.register(
      "logistics_module", () -> new LogisticsModuleBlock(moduleProperties()));
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
                if (player instanceof ServerPlayer serverPlayer) {
                  // A fake player FTB Teams does not know (a printed deployer) has no campaign.
                  if (CampaignActions.campaignIdOrNull(serverPlayer) == null)
                    return InteractionResultHolder.pass(player.getItemInHand(hand));
                  AtlasNetwork.open(serverPlayer);
                }
                return InteractionResultHolder.sidedSuccess(
                    player.getItemInHand(hand), level.isClientSide);
              }
            });
    for (String id : List.of("raw_lens", "survey_notes", "signal_core"))
      ITEMS.registerSimpleItem(id);
    // The six Ark modules (Ark v2): each turns its global effect on in its slot of the team's Ark.
    for (var kind : ArkFieldJournals.Kind.values()) {
      String id = kind.module();
      if (kind == ArkFieldJournals.Kind.LOGISTICS) {
        ITEMS.register(id, () -> new ArkFieldJournalItem(LOGISTICS_MODULE.get(), new Item.Properties().fireResistant()));
        continue;
      }
      var module = BLOCKS.register(id, () -> new ArkFieldJournalBlock(kind, moduleProperties()));
      ITEMS.register(id, () -> new ArkFieldJournalItem(module.get(), new Item.Properties().fireResistant()));
    }
    var surveyStation = BLOCKS.register("survey_station", () -> new SignalStationBlock(
        BlockBehaviour.Properties.of().strength(3f).sound(SoundType.WOOD).noOcclusion().requiresCorrectToolForDrops()));
    ITEMS.registerSimpleBlockItem("survey_station", surveyStation);
    var controller = BLOCKS.register("ark_controller",
        () -> new ArkControllerBlock(BlockBehaviour.Properties.of().strength(4f)));
    ITEMS.registerSimpleBlockItem("ark_controller", controller);
  }

  public Entrelumen(IEventBus bus, net.neoforged.fml.ModContainer container) {
    ITEMS.register(bus);
    bus.addListener(AtlasNetwork::register);
    bus.addListener(JournalBookNetwork::register);
    ApotheosisContent.bootstrap(bus);
    SatietyOverflowEvents.register();
    BLOCKS.register(bus);
    BLOCK_ENTITIES.register(bus);
    Altars.register(bus);
    Luminous.register(bus);
    HeliodorContent.register(bus);
    RuinContent.register(bus);
    TerraArm.register(bus);
    SourShackle.register(bus);
    TerraGarden.register(bus);
    Terralight.register(bus);
    VeinResonator.register(bus);
    HeliodorHeart.register(bus);
    Solsticio.register(bus, container);
    Enves.register(bus);
    if (container != null)
      container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ArkEffects.SPEC,
          "entrelumen-ark-server.toml");
    if (container != null)
      container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, Terralight.SPEC,
          "entrelumen-terralight-server.toml");
    ArkState.register();
    ArkEffects.register(bus);
    ArkCommands.register();
    ArkCommerce.register();
    ArkMigration.register();
    bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
        event.enqueueWork(WaystonesBridge::registerTraveller));
    NeoForge.EVENT_BUS.addListener(this::commands);
    NeoForge.EVENT_BUS.addListener(Expeditions::onDimensionChanged);
    NeoForge.EVENT_BUS.addListener(Expeditions::onLogin);
    NeoForge.EVENT_BUS.addListener(Expeditions::onRespawn);
    NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
        ArkControllerBlock::allowEmptyHandDeposit);
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.AddReloadListenerEvent event) -> {
          event.addListener(new ProjectReloadListener());
          event.addListener(new ArkMultiblockReloadListener());
        });
    CampaignTask.register();
    NeoForge.EVENT_BUS.addListener(CampaignTask::tick);
    TeamEvent.CREATED.register(
        event -> {
          if (event.getTeam().isPartyTeam()) {
            var server = FTBTeamsAPI.api().getManager().getServer();
            var data = CampaignData.get(server);
            data.campaigns.party(event.getTeam().getId(), event.getCreatorId());
            data.setDirty();
            TeamHandoff.onCreated(server, event.getTeam().getId(), event.getCreatorId());
          }
        });
    TeamEvent.DELETED.register(
        event -> {
          if (event.getTeam().isPartyTeam()) {
            var server = FTBTeamsAPI.api().getManager().getServer();
            var data = CampaignData.get(server);
            data.campaigns.archive(event.getTeam().getId());
            data.setDirty();
            TeamHandoff.onDeleted(server, event.getTeam().getId(), event.getTeam().getOwner());
          }
        });
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
    // Key pieces count only as the team's own current copy (docs/design/heliodor-ruins.md).
    return KeyPieces.deliverable(player, java.util.stream.Stream.concat(
            player.getInventory().items.stream(), player.getInventory().offhand.stream())
        .toList());
  }

  /** A Solsticio errand item (a named book, map or tool a story mission hands out) is never a material. */
  static boolean errand(ItemStack stack) {
    return SolsticioStory.errandOf(stack) != null;
  }

  /** Whether a stack counts as {@code item} for a project delivery. */
  static boolean material(ItemStack stack, String item) {
    return !stack.isEmpty() && !errand(stack)
        && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item);
  }

  static Map<String, Integer> availableMaterials(ServerPlayer player) {
    Map<String, Integer> result = new HashMap<>();
    for (var stack : deliveryStacks(player))
      if (!stack.isEmpty() && !errand(stack))
        result.merge(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            stack.getCount(),
            Integer::sum);
    return result;
  }

  /**
   * Takes {@code amount} matching items from the delivery stacks: plain stacks first, then those that
   * carry data (a lodestone-bound compass, a paid survey map, a named copy), each pass in slot order.
   * Takes what there is if there is less; the caller checks the count first.
   */
  static void consume(ServerPlayer player, Predicate<ItemStack> test, int amount) {
    int remaining = amount;
    List<ItemStack> stacks = deliveryStacks(player);
    for (boolean plain : new boolean[] {true, false})
      for (ItemStack stack : stacks) {
        if (remaining <= 0) break;
        if (stack.isEmpty() || stack.getComponentsPatch().isEmpty() != plain || !test.test(stack)) continue;
        int take = Math.min(remaining, stack.getCount());
        stack.shrink(take);
        remaining -= take;
      }
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
  }

  public static List<Component> statusLines(ServerPlayer player) {
    var c = current(player);
    var inventory = availableMaterials(player);
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable("entrelumen.status", c.act, c.completed.size(),
        ArkState.status(player).activeModules().size()));
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
            () -> p.items().forEach((item, count) -> consume(player, stack -> material(stack, item), count)));
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
      p.extraRewards().forEach((item, count) -> {
        var extra = new ItemStack(
            BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item)), count);
        Component extraName = Component.translatable("entrelumen.delivery.reward_count", count,
            extra.getHoverName());
        if (!player.getInventory().add(extra)) player.drop(extra, false);
        player.sendSystemMessage(Component.translatable("entrelumen.delivery.reward", extraName));
      });
    }
    player.sendSystemMessage(
        Component.translatable(
            ok ? "entrelumen.delivery.success" : "entrelumen.delivery.failed",
            Component.translatable("entrelumen.project." + id)));
    return ok ? 1 : 0;
  }

  static int advance(ServerPlayer player) {
    var campaign = current(player);
    boolean ok = Campaigns.advance(campaign,
        Campaigns.advanceRequirements(campaign.act, Projects.forAct(campaign.act)));
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
                                                      "schema=" + CampaignData.VERSION + " personal="
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
                                    Commands.argument("act", IntegerArgumentType.integer(1, Campaigns.FINAL_ACT))
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
