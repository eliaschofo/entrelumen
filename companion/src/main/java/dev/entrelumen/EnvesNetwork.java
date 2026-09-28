package dev.entrelumen;

import dev.entrelumen.EnvesLayout.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Envés's packets. The server sends each member the HUD numbers and party positions twice a
 * second, the fog map of their floor when it changes (only cells the group knows: explored ones with
 * doors and role, glimpsed ones as a blur; a few hundred bytes at most), and the gate screen; the
 * client sends the gate's choice back, which the server validates again.
 */
public final class EnvesNetwork {
  static final int MAX_CELLS = EnvesLayout.CELLS, MAX_MATES = 64, MAX_TIERS = 8, MAX_OFFERS = EnvesOffering.MAX_OFFERS;

  private EnvesNetwork() {}

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", path);
  }

  static int bounded(RegistryFriendlyByteBuf buf, int max) {
    int count = buf.readVarInt();
    if (count < 0 || count > max) throw new IllegalArgumentException("Envés packet collection exceeds limit");
    return count;
  }

  /** A party member on the map: block coordinates and floor. */
  public record Mate(String name, float x, float z, int depth) {}

  /**
   * HUD numbers. {@code inside} false clears the client state. Depth 0 is the vestibule. Tier is the
   * {@link ApotheosisTiers.Tier} ordinal.
   */
  public record Hud(boolean inside, int depth, int tier, int poolLeft, int poolTotal, int sealsLit, int sealsTotal,
      boolean stairOpen, boolean forming, List<Mate> mates) implements CustomPacketPayload {
    public static final Type<Hud> TYPE = new Type<>(id("enves_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.ofMember(Hud::write, Hud::read);
    public static final Hud OUTSIDE = new Hud(false, 0, 0, 0, 0, 0, 0, false, false, List.of());

    public Hud {
      mates = List.copyOf(mates);
    }

    @Override
    public Type<Hud> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeBoolean(inside);
      buf.writeVarInt(depth);
      buf.writeVarInt(tier);
      buf.writeVarInt(poolLeft);
      buf.writeVarInt(poolTotal);
      buf.writeVarInt(sealsLit);
      buf.writeVarInt(sealsTotal);
      buf.writeBoolean(stairOpen);
      buf.writeBoolean(forming);
      buf.writeVarInt(mates.size());
      for (Mate mate : mates) {
        buf.writeUtf(mate.name(), 32);
        buf.writeFloat(mate.x());
        buf.writeFloat(mate.z());
        buf.writeVarInt(mate.depth());
      }
    }

    private static Hud read(RegistryFriendlyByteBuf buf) {
      boolean inside = buf.readBoolean();
      int depth = buf.readVarInt(), tier = buf.readVarInt(), left = buf.readVarInt(), total = buf.readVarInt();
      int lit = buf.readVarInt(), seals = buf.readVarInt();
      boolean open = buf.readBoolean(), forming = buf.readBoolean();
      int count = bounded(buf, MAX_MATES);
      List<Mate> mates = new ArrayList<>(count);
      for (int i = 0; i < count; i++) mates.add(new Mate(buf.readUtf(32), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
      return new Hud(inside, depth, tier, left, total, lit, seals, open, forming, mates);
    }
  }

  /**
   * The fog map of one floor: the grid's world corner and the cells the group knows. Role 0 is
   * unknown; otherwise {@link Role#ordinal} + 1.
   */
  public record MapView(int depth, int originX, int originZ, List<EnvesRules.KnownCell> cells) implements CustomPacketPayload {
    public static final Type<MapView> TYPE = new Type<>(id("enves_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapView> CODEC = StreamCodec.ofMember(MapView::write, MapView::read);

    public MapView {
      cells = List.copyOf(cells);
    }

    @Override
    public Type<MapView> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeVarInt(depth);
      buf.writeInt(originX);
      buf.writeInt(originZ);
      buf.writeVarInt(cells.size());
      for (var cell : cells) {
        buf.writeByte(cell.cell());
        buf.writeByte(cell.doors() | (cell.explored() ? 0x10 : 0) | (cell.lit() ? 0x20 : 0));
        buf.writeByte(cell.role() == null ? 0 : cell.role().ordinal() + 1);
      }
    }

    private static MapView read(RegistryFriendlyByteBuf buf) {
      int depth = buf.readVarInt(), originX = buf.readInt(), originZ = buf.readInt();
      int count = bounded(buf, MAX_CELLS);
      List<EnvesRules.KnownCell> cells = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        int cell = buf.readUnsignedByte(), flags = buf.readUnsignedByte(), role = buf.readUnsignedByte();
        if (cell >= EnvesLayout.CELLS) throw new IllegalArgumentException("Envés cell out of the grid");
        Role r = role == 0 || role > Role.values().length ? null : Role.values()[role - 1];
        cells.add(new EnvesRules.KnownCell(cell, flags & 0x0F, r, (flags & 0x10) != 0, (flags & 0x20) != 0));
      }
      return new MapView(depth, originX, originZ, cells);
    }
  }

  /** One way to pay the gate, as the gate shows it to one player. */
  public record Offer(String item, int count, boolean affordable) {}

  /**
   * What the gate shows one player. {@code state}: see {@link EnvesEntrance.GateState}. {@code offers}:
   * the alternatives of the offering, in the config's order.
   */
  public record Gate(int state, List<Offer> offers, List<Integer> tiers, int attemptTier, int frontline, int poolLeft,
      int poolTotal) implements CustomPacketPayload {
    public static final Type<Gate> TYPE = new Type<>(id("enves_gate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Gate> CODEC = StreamCodec.ofMember(Gate::write, Gate::read);

    public Gate {
      offers = List.copyOf(offers);
      tiers = List.copyOf(tiers);
    }

    /** Whether the player carries any of the offers. */
    public boolean hasOffering() {
      return offers.stream().anyMatch(Offer::affordable);
    }

    @Override
    public Type<Gate> type() {
      return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
      buf.writeVarInt(state);
      buf.writeVarInt(offers.size());
      for (Offer offer : offers) {
        buf.writeUtf(offer.item(), 256);
        buf.writeVarInt(offer.count());
        buf.writeBoolean(offer.affordable());
      }
      buf.writeVarInt(tiers.size());
      for (int tier : tiers) buf.writeVarInt(tier);
      buf.writeVarInt(attemptTier);
      buf.writeVarInt(frontline);
      buf.writeVarInt(poolLeft);
      buf.writeVarInt(poolTotal);
    }

    private static Gate read(RegistryFriendlyByteBuf buf) {
      int state = buf.readVarInt();
      int o = bounded(buf, MAX_OFFERS);
      List<Offer> offers = new ArrayList<>(o);
      for (int i = 0; i < o; i++) offers.add(new Offer(buf.readUtf(256), buf.readVarInt(), buf.readBoolean()));
      int n = bounded(buf, MAX_TIERS);
      List<Integer> tiers = new ArrayList<>(n);
      for (int i = 0; i < n; i++) tiers.add(buf.readVarInt());
      return new Gate(state, offers, tiers, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
  }

  public enum Action {OPEN, ENTER, GIVE_UP}

  /**
   * The gate screen's choice; {@code tier} and {@code offer} (the index of the offering paid, -1 for
   * the first the player carries) only matter for {@link Action#OPEN}.
   */
  public record GateAction(Action action, int tier, int offer) implements CustomPacketPayload {
    public static final Type<GateAction> TYPE = new Type<>(id("enves_gate_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GateAction> CODEC = StreamCodec.ofMember(
        (action, buf) -> {
          buf.writeEnum(action.action());
          buf.writeVarInt(action.tier());
          buf.writeVarInt(action.offer() + 1);
        },
        buf -> new GateAction(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt() - 1));

    @Override
    public Type<GateAction> type() {
      return TYPE;
    }
  }

  /** Assigned by the client-side subscriber only; common code never touches client classes. */
  public static Consumer<Hud> hudReceiver = hud -> {};
  public static Consumer<MapView> mapReceiver = map -> {};
  public static Consumer<Gate> gateReceiver = gate -> {};

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("2");
    registrar.playToClient(Hud.TYPE, Hud.CODEC, (hud, context) -> context.enqueueWork(() -> hudReceiver.accept(hud)));
    registrar.playToClient(MapView.TYPE, MapView.CODEC, (map, context) -> context.enqueueWork(() -> mapReceiver.accept(map)));
    registrar.playToClient(Gate.TYPE, Gate.CODEC, (gate, context) -> context.enqueueWork(() -> gateReceiver.accept(gate)));
    registrar.playToServer(GateAction.TYPE, GateAction.CODEC, (action, context) -> context.enqueueWork(() -> {
      if (context.player() instanceof ServerPlayer player) EnvesEntrance.act(player, action);
    }));
  }

  static void send(ServerPlayer player, CustomPacketPayload payload) {
    if (player.connection != null) PacketDistributor.sendToPlayer(player, payload);
  }
}
