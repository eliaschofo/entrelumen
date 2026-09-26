package dev.entrelumen;

import dev.entrelumen.mixin.common.VillagerInvoker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Logistics module's remote trade with Solsticio. A team knows a shop once one of its members
 * talked to the shopkeeper (opened its trades) or stood within eight blocks of its post. From the
 * Atlas, a member with the module active opens a known shop's own trading screen from anywhere: the
 * real shopkeeper, so prices, act gates, restocks and stock are the physical shop's. Payment comes
 * from the player's inventory through the vanilla merchant screen; what does not fit is dropped at
 * the player's feet when the screen closes, as vanilla does.
 *
 * <p>The shopkeeper's chunk is held by a short ticket while the screen is open. The trade's
 * experience orb stays at the shop, as a villager drops it where it stands.
 */
public final class ArkCommerce {
  static final TicketType<ChunkPos> TICKET =
      TicketType.create("entrelumen_remote_trade", Comparator.comparingLong(ChunkPos::toLong), 20 * 10);
  /** Ticks a shop's chunk and villager may take to load before the request gives up. */
  static final int LOAD_TICKS = 20 * 10;
  private static final Map<UUID, Session> SESSIONS = new HashMap<>();

  private ArkCommerce() {}

  /** One remote shop visit: loading the shopkeeper, then trading with it. */
  private static final class Session {
    final UUID villager;
    final ChunkPos chunk;
    final long startedAt;
    boolean opened;

    Session(UUID villager, ChunkPos chunk, long startedAt) {
      this.villager = villager;
      this.chunk = chunk;
      this.startedAt = startedAt;
    }
  }

  /** A known shop as the Atlas lists it; {@code available} when its shopkeeper exists. */
  public record Shop(String id, String key, boolean available) {}

  static void register() {
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
  }

  // ---- which shops a team knows ----------------------------------------------------------------

  static void know(ServerPlayer player, String key, long site) {
    if (key.isEmpty()) return;
    ArkData data = ArkData.get(player.server);
    if (data.knownShops.computeIfAbsent(CampaignActions.campaignId(player), team -> new TreeSet<>())
        .add(ArkRules.shopId(key, site))) {
      data.setDirty();
      if (ArkState.status(player).active(ArkRules.Module.LOGISTICS))
        player.displayClientMessage(Component.translatable("entrelumen.trade.known",
            shopName(key)), true);
    }
  }

  /** Called every effect pass: a player standing near a shopkeeper's post in Solsticio visits it. */
  static void observeVisits(ServerPlayer player) {
    if (!player.level().dimension().equals(Solsticio.LEVEL) || player.isSpectator()) return;
    SolsticioData solsticio = SolsticioData.get(player.server);
    BlockPos at = player.blockPosition();
    for (CommerceSites.Site site : solsticio.commerce.sites) {
      if (site.role != CommerceRules.Role.SHOP) continue;
      if (ArkRules.visits(site.pos.getX() - at.getX(), site.pos.getY() - at.getY(), site.pos.getZ() - at.getZ()))
        know(player, site.key, site.pos.asLong());
    }
  }

  public static Component shopName(String key) {
    return Component.translatableWithFallback("entrelumen.solsticio.shop." + key, key);
  }

  /** The team's known shops that still stand in the city, by name. */
  public static List<Shop> catalog(ServerPlayer player) {
    Set<String> known = ArkData.get(player.server).knownShops.getOrDefault(CampaignActions.campaignId(player), Set.of());
    List<Shop> shops = new ArrayList<>();
    for (CommerceSites.Site site : SolsticioData.get(player.server).commerce.sites) {
      if (site.role != CommerceRules.Role.SHOP) continue;
      String id = ArkRules.shopId(site.key, site.pos.asLong());
      if (known.contains(id)) shops.add(new Shop(id, site.key, site.entity != null));
    }
    shops.sort(Comparator.comparing(Shop::key).thenComparing(Shop::id));
    return List.copyOf(shops);
  }

  // ---- opening a shop from afar ----------------------------------------------------------------

  /** Starts a remote visit to a known shop; the screen opens once its shopkeeper is loaded. */
  public static boolean open(ServerPlayer player, String id) {
    if (!ArkState.status(player).active(ArkRules.Module.LOGISTICS)) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.command_locked",
          Component.translatable("block.entrelumen." + ArkRules.Module.LOGISTICS.id)));
      return false;
    }
    if (!player.isAlive() || player.isSpectator()) return false;
    Set<String> known = ArkData.get(player.server).knownShops.getOrDefault(CampaignActions.campaignId(player), Set.of());
    CommerceSites.Site site = null;
    for (CommerceSites.Site candidate : SolsticioData.get(player.server).commerce.sites)
      if (candidate.role == CommerceRules.Role.SHOP && ArkRules.shopId(candidate.key, candidate.pos.asLong()).equals(id))
        site = candidate;
    ServerLevel level = player.server.getLevel(Solsticio.LEVEL);
    if (site == null || !known.contains(id) || level == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.trade.unknown"));
      return false;
    }
    if (site.entity == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.trade.absent", shopName(site.key)));
      return false;
    }
    close(player.server, player.getUUID());
    ChunkPos chunk = new ChunkPos(site.pos);
    level.getChunkSource().addRegionTicket(TICKET, chunk, 1, chunk);
    SESSIONS.put(player.getUUID(), new Session(site.entity, chunk, player.server.getTickCount()));
    return true;
  }

  /**
   * Opens a loaded shopkeeper's trades for a player anywhere: the same preparation as a click at the
   * counter (restock), then vanilla's own start of trading (reputation, Hero of the Village, the
   * Solsticio prices and act gates). False when it is busy with someone else or has nothing to sell.
   */
  public static boolean openLoaded(ServerPlayer player, Villager villager) {
    CompoundTag data = SolsticioCommerce.data(villager);
    if (data == null || SolsticioCommerce.role(villager) != CommerceRules.Role.SHOP || !villager.isAlive()) return false;
    if (villager.isTrading()) {
      player.sendSystemMessage(Component.translatable("entrelumen.trade.busy", villager.getDisplayName()));
      return false;
    }
    var level = (ServerLevel) villager.level();
    var table = SolsticioCommerce.shops().get(data.getString("key"));
    if (!SolsticioCommerce.prepareMerchant(level, villager, data, table)) {
      player.sendSystemMessage(Component.translatable("entrelumen.solsticio.shop.closed"));
      return false;
    }
    ((VillagerInvoker) villager).entrelumen$startTrading(player);
    return villager.getTradingPlayer() == player;
  }

  static void close(MinecraftServer server, UUID player) {
    Session session = SESSIONS.remove(player);
    if (session == null) return;
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    if (level != null) level.getChunkSource().removeRegionTicket(TICKET, session.chunk, 1, session.chunk);
  }

  static void tick(MinecraftServer server) {
    if (SESSIONS.isEmpty()) return;
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
    List<UUID> ended = new ArrayList<>();
    while (it.hasNext()) {
      var entry = it.next();
      Session session = entry.getValue();
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      if (player == null || level == null) {
        ended.add(entry.getKey());
        continue;
      }
      var entity = level.getEntity(session.villager);
      if (!session.opened) {
        if (entity instanceof Villager villager && level.areEntitiesLoaded(session.chunk.toLong())) {
          session.opened = true;
          if (!openLoaded(player, villager)) ended.add(entry.getKey());
        } else if (server.getTickCount() - session.startedAt > LOAD_TICKS) {
          player.sendSystemMessage(Component.translatable("entrelumen.trade.unavailable"));
          ended.add(entry.getKey());
        }
        continue;
      }
      boolean trading = entity instanceof Villager villager && villager.isAlive() && villager.getTradingPlayer() == player
          && player.containerMenu instanceof MerchantMenu;
      if (!trading) {
        // The shopkeeper vanished under an open screen: close it, so no trade lands on a stale copy.
        if (player.containerMenu instanceof MerchantMenu && !(entity instanceof Villager v && v.isAlive()))
          player.closeContainer();
        ended.add(entry.getKey());
      } else if ((server.getTickCount() - session.startedAt) % 20 == 0) {
        level.getChunkSource().addRegionTicket(TICKET, session.chunk, 1, session.chunk);
      }
    }
    for (UUID player : ended) close(server, player);
  }
}
