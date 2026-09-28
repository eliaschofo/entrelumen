package dev.entrelumen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Which gate cells a player may pass. The server keeps, per player, the cells of the ruins near
 * them whose gate their team opened, and sends the same set to that player's client, which needs
 * it to predict its own movement. Both are refreshed twice a second and at once after a solve.
 */
public final class RuinGates {
  /** Ruins further than this from a player are not looked at. */
  static final int RANGE = 192;
  static final int INTERVAL = 10;

  /** The local player's open cells (client side). */
  private static volatile LongSet client = LongSets.EMPTY_SET;
  private static final Map<MinecraftServer, Map<UUID, LongSet>> SERVER = new WeakHashMap<>();

  private RuinGates() {}

  public static boolean open(Player player, BlockPos pos) {
    if (player.level().isClientSide) return client.contains(pos.asLong());
    if (!(player instanceof ServerPlayer serverPlayer)) return false;
    Map<UUID, LongSet> cells;
    synchronized (SERVER) {
      cells = SERVER.get(serverPlayer.server);
    }
    if (cells == null) return false;
    LongSet set = cells.get(player.getUUID());
    return set != null && set.contains(pos.asLong());
  }

  private static Map<UUID, LongSet> cells(MinecraftServer server) {
    synchronized (SERVER) {
      return SERVER.computeIfAbsent(server, ignored -> new ConcurrentHashMap<>());
    }
  }

  static void forget(MinecraftServer server) {
    synchronized (SERVER) {
      SERVER.remove(server);
    }
  }

  static void tick(MinecraftServer server) {
    if (server.getTickCount() % INTERVAL != 0) return;
    for (ServerPlayer player : server.getPlayerList().getPlayers()) refresh(player);
    var online = new HashSet<UUID>();
    for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());
    cells(server).keySet().retainAll(online);
  }

  /** Recomputes one player's open cells and tells their client when they changed. */
  public static void refresh(ServerPlayer player) {
    LongOpenHashSet open = new LongOpenHashSet();
    var context = HeliodorCompass.context(player).orElse(null);
    if (context != null) {
      var team = RuinProgress.get(player.server).team(context.campaignId(), context.founder());
      BlockPos here = player.blockPosition();
      for (var ruin : RuinData.get(player.server).ruins()) {
        if (!ruin.dimension().equals(player.level().dimension()) || ruin.markers().isEmpty()) continue;
        if (!near(ruin, here)) continue;
        var definition = RuinRegistry.get(ruin.id()).orElse(null);
        if (definition == null) continue;
        for (var marker : ruin.markers(RuinMarkers.Kind.GATE)) {
          var needs = definition.gates().get(marker.marker().param("id", ""));
          List<String> solved = needs == null ? List.of() : needs.stream()
              .map(challenge -> RuinProgress.key(definition.id(), challenge)).toList();
          if (needs != null && RuinRules.open(solved, team.solved)) open.add(marker.pos().asLong());
        }
      }
    }
    var cells = cells(player.server);
    LongSet previous = cells.get(player.getUUID());
    if (previous != null && previous.equals(open)) return;
    cells.put(player.getUUID(), open);
    if (player.connection != null) PacketDistributor.sendToPlayer(player, new Cells(open.toLongArray()));
  }

  private static boolean near(RuinData.Ruin ruin, BlockPos here) {
    var box = ruin.box();
    int dx = Math.max(0, Math.max(box.minX() - here.getX(), here.getX() - box.maxX()));
    int dz = Math.max(0, Math.max(box.minZ() - here.getZ(), here.getZ() - box.maxZ()));
    return dx <= RANGE && dz <= RANGE;
  }

  /** The cells the local player may pass, in its current dimension. */
  public record Cells(long[] cells) implements CustomPacketPayload {
    public static final Type<Cells> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("entrelumen", "ruin_gates"));
    public static final StreamCodec<FriendlyByteBuf, Cells> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeLongArray(value.cells()),
        buf -> new Cells(buf.readLongArray(null, 4096)));

    @Override
    public Type<Cells> type() {
      return TYPE;
    }
  }

  static void registerPayloads(RegisterPayloadHandlersEvent event) {
    event.registrar("1").optional().playToClient(Cells.TYPE, Cells.CODEC,
        (payload, context) -> context.enqueueWork(() -> client = LongSets.unmodifiable(new LongOpenHashSet(payload.cells()))));
  }
}
