package dev.entrelumen;

import java.util.*;
import java.util.function.Consumer;
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

  public record Snapshot(
      UUID campaign,
      int act,
      int arkPhase,
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
      buf.writeVarInt(arkPhase);
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
      int act = buf.readVarInt(), phase = buf.readVarInt();
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
      return new Snapshot(campaign, act, phase, List.copyOf(projects), advance, open, message,
          new CompassView(objective, compassState, dimension, kind, lore));
    }

    private static int boundedCount(RegistryFriendlyByteBuf buf) {
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

  /** Assigned only by the client-side mod subscriber; common/server code never loads a Screen. */
  public static Consumer<Snapshot> clientReceiver = snapshot -> {};

  public static void register(RegisterPayloadHandlersEvent event) {
    // Version 2 adds the Heliodor compass view to the snapshot.
    var registrar = event.registrar("2");
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
        campaign.arkPhase,
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
