package dev.entrelumen;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AtlasNetwork {
  public record Material(ResourceLocation item, int available, int required) {}

  public record Prerequisite(String id, boolean completed) {}

  public record ProjectView(
      String id, boolean completed, List<Prerequisite> prerequisites, List<Material> materials) {
    public boolean ready() {
      return !completed
          && prerequisites.stream().allMatch(Prerequisite::completed)
          && materials.stream().allMatch(m -> m.available() >= m.required());
    }
  }

  /**
   * The Heliodor compass as the Atlas explains it: objective ID (empty when none), a
   * {@link CompassState} state, the target dimension ID, the kind and recovered lore IDs.
   */
  public record CompassView(
      String objective, int state, String dimension, int kind, List<String> lore) {
    public static final CompassView NONE =
        new CompassView("", CompassState.COMPLETE, "", 0, List.of());

    public CompassView {
      lore = List.copyOf(lore);
    }
  }

  /**
   * The team's Ark as the Atlas shows it: where it is (for the guide), what stands, the modules in
   * their slots, the beacons, and the two campaign items of the activation checklist.
   */
  public record ArkView(boolean registered, String dimension, BlockPos origin, int rotation, boolean core,
      int missingCore, boolean controller, List<String> modules, int beacons, boolean lastProject,
      boolean endJourney, boolean activated) {
    public static final ArkView NONE =
        new ArkView(false, "", BlockPos.ZERO, 0, false, -1, false, List.of(), 0, false, false, false);

    public ArkView {
      modules = List.copyOf(modules);
    }

    public ArkRules.Status status() {
      return new ArkRules.Status(registered, core, controller, Set.copyOf(modules), beacons);
    }

    /** The campaign milestones the checklist reads. */
    public Set<String> completed() {
      Set<String> completed = new TreeSet<>();
      if (lastProject) completed.add(ArkRules.LAST_PROJECT);
      if (endJourney) completed.add(ArkRules.END_JOURNEY);
      if (activated) completed.add(CampaignMilestones.LAST_HORIZON);
      return completed;
    }

    void write(RegistryFriendlyByteBuf buf) {
      buf.writeBoolean(registered);
      buf.writeUtf(dimension, 256);
      buf.writeBlockPos(origin);
      buf.writeVarInt(rotation);
      buf.writeBoolean(core);
      buf.writeVarInt(missingCore + 1);
      buf.writeBoolean(controller);
      buf.writeVarInt(modules.size());
      for (String module : modules) buf.writeUtf(module, 64);
      buf.writeVarInt(beacons);
      buf.writeBoolean(lastProject);
      buf.writeBoolean(endJourney);
      buf.writeBoolean(activated);
    }

    static ArkView read(RegistryFriendlyByteBuf buf) {
      boolean registered = buf.readBoolean();
      String dimension = buf.readUtf(256);
      BlockPos origin = buf.readBlockPos();
      int rotation = buf.readVarInt(), missing = buf.readVarInt() - 1;
      boolean core = buf.readBoolean(), controller = buf.readBoolean();
      int count = Snapshot.boundedCount(buf);
      List<String> modules = new ArrayList<>(count);
      for (int i = 0; i < count; i++) modules.add(buf.readUtf(64));
      int beacons = buf.readVarInt();
      return new ArkView(registered, dimension, origin, rotation, core, missing, controller, modules, beacons,
          buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    static ArkView of(ServerPlayer player, Campaigns.Campaign campaign) {
      ArkData.Ark ark = ArkState.ark(player);
      boolean last = campaign.completed.contains(ArkRules.LAST_PROJECT);
      boolean end = campaign.completed.contains(ArkRules.END_JOURNEY);
      boolean activated = campaign.completed.contains(CampaignMilestones.LAST_HORIZON);
      if (ark == null) return new ArkView(false, "", BlockPos.ZERO, 0, false, -1, false, List.of(), 0, last, end, activated);
      return new ArkView(true, ark.dimension.location().toString(), ark.anchor.origin(), ark.anchor.rotation(), ark.core,
          ark.missingCore, ark.controller, List.copyOf(ark.modules), ark.beacons, last, end, activated);
    }
  }

  public record Snapshot(
      UUID campaign,
      int act,
      ArkView ark,
      List<ProjectView> projects,
      boolean canAdvance,
      boolean open,
      String message,
      CompassView compass)
      implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "atlas_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.ofMember(Snapshot::write, Snapshot::read);

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeUUID(campaign);
      buf.writeVarInt(act);
      ark.write(buf);
      buf.writeBoolean(canAdvance);
      buf.writeBoolean(open);
      buf.writeUtf(message, 128);
      buf.writeVarInt(projects.size());
      for (var project : projects) {
        buf.writeUtf(project.id(), 128);
        buf.writeBoolean(project.completed());
        buf.writeVarInt(project.prerequisites().size());
        for (var prerequisite : project.prerequisites()) {
          buf.writeUtf(prerequisite.id(), 128);
          buf.writeBoolean(prerequisite.completed());
        }
        buf.writeVarInt(project.materials().size());
        for (var material : project.materials()) {
          buf.writeResourceLocation(material.item());
          buf.writeVarInt(material.available());
          buf.writeVarInt(material.required());
        }
      }
      buf.writeUtf(compass.objective(), 128);
      buf.writeVarInt(compass.state());
      buf.writeUtf(compass.dimension(), 256);
      buf.writeVarInt(compass.kind());
      buf.writeVarInt(compass.lore().size());
      for (String lore : compass.lore()) buf.writeUtf(lore, 128);
    }

    private static Snapshot read(RegistryFriendlyByteBuf buf) {
      UUID campaign = buf.readUUID();
      int act = buf.readVarInt();
      ArkView ark = ArkView.read(buf);
      boolean advance = buf.readBoolean(), open = buf.readBoolean();
      String message = buf.readUtf(128);
      int count = boundedCount(buf);
      List<ProjectView> projects = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        String id = buf.readUtf(128);
        boolean completed = buf.readBoolean();
        int prerequisitesCount = boundedCount(buf);
        List<Prerequisite> prerequisites = new ArrayList<>();
        for (int j = 0; j < prerequisitesCount; j++)
          prerequisites.add(new Prerequisite(buf.readUtf(128), buf.readBoolean()));
        int materialCount = boundedCount(buf);
        List<Material> materials = new ArrayList<>();
        for (int j = 0; j < materialCount; j++)
          materials.add(
              new Material(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt()));
        projects.add(
            new ProjectView(id, completed, List.copyOf(prerequisites), List.copyOf(materials)));
      }
      String objective = buf.readUtf(128);
      int compassState = buf.readVarInt();
      String dimension = buf.readUtf(256);
      int kind = buf.readVarInt();
      int loreCount = boundedCount(buf);
      List<String> lore = new ArrayList<>(loreCount);
      for (int i = 0; i < loreCount; i++) lore.add(buf.readUtf(128));
      return new Snapshot(campaign, act, ark, List.copyOf(projects), advance, open, message,
          new CompassView(objective, compassState, dimension, kind, lore));
    }

    static int boundedCount(RegistryFriendlyByteBuf buf) {
      int count = buf.readVarInt();
      if (count < 0 || count > 4096)
        throw new IllegalArgumentException("Atlas snapshot collection exceeds limit");
      return count;
    }
  }

  public record Request(UUID campaign, CampaignActions.Action action, String project)
      implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "atlas_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.ofMember(Request::write, Request::read);

    @Override
    public Type<Request> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeUUID(campaign);
      buf.writeEnum(action);
      buf.writeUtf(project, 128);
    }

    private static Request read(RegistryFriendlyByteBuf buf) {
      return new Request(
          buf.readUUID(), buf.readEnum(CampaignActions.Action.class), buf.readUtf(128));
    }
  }

  public record OpenRequest() implements CustomPacketPayload {
    public static final OpenRequest INSTANCE = new OpenRequest();
    public static final Type<OpenRequest> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "atlas_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRequest> CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenRequest> type() {
      return TYPE;
    }
  }

  /**
   * The Logistics module's remote trade: an empty {@code shop} asks for the catalog, a shop ID opens
   * that shop's trades.
   */
  public record TradeRequest(String shop) implements CustomPacketPayload {
    public static final Type<TradeRequest> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "ark_trade_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeRequest> CODEC =
        StreamCodec.ofMember((request, buf) -> buf.writeUtf(request.shop(), 256),
            buf -> new TradeRequest(buf.readUtf(256)));

    @Override
    public Type<TradeRequest> type() {
      return TYPE;
    }
  }

  /** The catalog of the Solsticio shops the team knows; {@code active} while Logistics works. */
  public record Catalog(boolean active, List<ArkCommerce.Shop> shops) implements CustomPacketPayload {
    public static final Type<Catalog> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "ark_trade_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC =
        StreamCodec.ofMember(Catalog::write, Catalog::read);

    public Catalog {
      shops = List.copyOf(shops);
    }

    @Override
    public Type<Catalog> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeBoolean(active);
      buf.writeVarInt(shops.size());
      for (var shop : shops) {
        buf.writeUtf(shop.id(), 256);
        buf.writeUtf(shop.key(), 128);
        buf.writeBoolean(shop.available());
      }
    }

    private static Catalog read(RegistryFriendlyByteBuf buf) {
      boolean active = buf.readBoolean();
      int count = Snapshot.boundedCount(buf);
      List<ArkCommerce.Shop> shops = new ArrayList<>(count);
      for (int i = 0; i < count; i++) shops.add(new ArkCommerce.Shop(buf.readUtf(256), buf.readUtf(128), buf.readBoolean()));
      return new Catalog(active, shops);
    }
  }

  /** Assigned only by the client-side mod subscriber; common/server code never loads a Screen. */
  public static Consumer<Snapshot> clientReceiver = snapshot -> {};
  public static Consumer<Catalog> catalogReceiver = catalog -> {};

  public static void register(RegisterPayloadHandlersEvent event) {
    // Version 2 adds the Heliodor compass view to the snapshot; 3 (Ark v2) the team's Ark and trade.
    var registrar = event.registrar("3");
    registrar.playToServer(TradeRequest.TYPE, TradeRequest.CODEC, (request, context) -> context.enqueueWork(() -> {
      if (!(context.player() instanceof ServerPlayer player)) return;
      if (request.shop().isEmpty())
        PacketDistributor.sendToPlayer(player, new Catalog(ArkState.status(player).active(ArkRules.Module.LOGISTICS),
            ArkCommerce.catalog(player)));
      else ArkCommerce.open(player, request.shop());
    }));
    registrar.playToClient(Catalog.TYPE, Catalog.CODEC,
        (catalog, context) -> context.enqueueWork(() -> catalogReceiver.accept(catalog)));
    registrar.playToServer(
        OpenRequest.TYPE,
        OpenRequest.CODEC,
        (request, context) ->
            context.enqueueWork(
                () -> {
                  if (context.player() instanceof ServerPlayer player) open(player);
                }));
    registrar.playToClient(
        Snapshot.TYPE,
        Snapshot.CODEC,
        (snapshot, context) -> context.enqueueWork(() -> clientReceiver.accept(snapshot)));
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (request, context) ->
            context.enqueueWork(
                () -> {
                  if (context.player() instanceof ServerPlayer player) {
                    PacketDistributor.sendToPlayer(player, handleRequest(player, request));
                  }
                }));
  }

  public static Snapshot handleRequest(ServerPlayer player, Request request) {
    var result =
        CampaignActions.perform(player, request.campaign(), request.action(), request.project());
    return snapshot(player, false, result.message());
  }

  public static Snapshot handleOpen(ServerPlayer player) {
    return snapshot(player, true, "");
  }

  public static void open(ServerPlayer player) {
    PacketDistributor.sendToPlayer(player, handleOpen(player));
  }

  public static Snapshot snapshot(ServerPlayer player, boolean open, String message) {
    var campaign = Entrelumen.current(player);
    var inventory = Entrelumen.availableMaterials(player);
    List<ProjectView> projects = new ArrayList<>();
    Projects.all()
        .forEach(
            (id, project) -> {
              if (project.act() != campaign.act) return;
              var prerequisites =
                  project.prerequisites().stream()
                      .sorted()
                      .map(p -> new Prerequisite(p, campaign.completed.contains(p)))
                      .toList();
              var materials =
                  project.items().entrySet().stream()
                      .sorted(Map.Entry.comparingByKey())
                      .map(
                          e ->
                              new Material(
                                  ResourceLocation.parse(e.getKey()),
                                  inventory.getOrDefault(e.getKey(), 0),
                                  e.getValue()))
                      .toList();
              projects.add(
                  new ProjectView(id, campaign.completed.contains(id), prerequisites, materials));
            });
    return new Snapshot(
        CampaignActions.campaignId(player),
        campaign.act,
        ArkView.of(player, campaign),
        List.copyOf(projects),
        campaign.act < Campaigns.FINAL_ACT
            && !projects.isEmpty()
            && campaign.completed.containsAll(Campaigns.advanceRequirements(campaign.act,
                Projects.forAct(campaign.act))),
        open,
        message,
        HeliodorCompass.view(player));
  }
}
