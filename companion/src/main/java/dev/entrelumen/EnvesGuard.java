package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.integration.stages.StageHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The rules of the Envés that are not block protection (that is {@link StructureProtection}, with
 * the whole dimension as one region: nothing is broken or placed, explosions and mobs break nothing,
 * no fluids): no flight of any kind, no teleporting of players (pearls, chorus, blink spells and
 * staffs: every {@link EntityTeleportEvent} but the commands'), no passing through walls (a per-tick
 * no-clip check catches mods that move a player without any event), no capturing or leashing echoes,
 * no way in or out but the gate and the exit portals (waystones, portals and teleport commands
 * included), no natural spawns, and no map but the Envés's own (FTB Chunks through its
 * {@code ftbchunks_mapping} stage; JourneyMap is switched off on the client). Operators with the
 * protection bypass are exempt.
 */
public final class EnvesGuard {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Items that would skip the maze; {@code data/entrelumen/tags/item/enves_forbidden.json}. */
  public static final TagKey<Item> FORBIDDEN = ItemTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "enves_forbidden"));
  /** FTB Chunks shows its map and minimap only to players with this stage when the pack sets {@code require_game_stage}. */
  public static final String MAP_STAGE = "ftbchunks_mapping";
  private static final int MESSAGE_INTERVAL = 40;

  private static final class Mover {
    final EnvesRules.Lift lift = new EnvesRules.Lift();
    double lastY = Double.NaN;
    Vec3 ground;
    /** Where the player stood last tick, for the no-clip check; null after the server moved them. */
    Vec3 lastPos;
    /**
     * A pull-back decided during the player's own tick, sent after the server tick by
     * {@link #applySnaps}: a teleport sent from inside the connection's tick is undone right after it
     * (vanilla puts the player back where the tick started until the client accepts), so the check
     * would see the same move again and teleport again, and the client's accept would never match.
     */
    Vec3 snapTo;
    String snapKey;
    long lastMessage = Long.MIN_VALUE / 2;
  }

  private static final Map<UUID, Mover> MOVERS = new HashMap<>();
  private static boolean stageFailed;
  /** Vanilla's per-tick move packet counters, by reflection; null when they are not reachable. */
  private static Field receivedMoves, knownMoves;
  private static boolean movesFailed;
  /** Wall time between the starts of the last two server ticks, for when the counters are not reachable. */
  private static long lastTickStart, tickGapNanos;

  private EnvesGuard() {}

  static void register() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener(EventPriority.HIGHEST, EnvesGuard::onTravel);
    bus.addListener(EnvesGuard::onTeleport);
    bus.addListener(EventPriority.HIGH, EnvesGuard::onInteractEntity);
    bus.addListener(EventPriority.HIGH, EnvesGuard::onInteractEntitySpecific);
    bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> moved(event.getEntity()));
    bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> moved(event.getEntity()));
    bus.addListener(EventPriority.HIGH, EnvesGuard::onUseItem);
    bus.addListener(EventPriority.HIGH, EnvesGuard::onCommand);
    bus.addListener(EnvesGuard::onPlayerTick);
    bus.addListener(EnvesGuard::onSpawnCheck);
    bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> MOVERS.remove(event.getEntity().getUUID()));
    bus.addListener((ServerTickEvent.Pre event) -> {
      long now = System.nanoTime();
      tickGapNanos = lastTickStart == 0 ? 0 : now - lastTickStart;
      lastTickStart = now;
    });
    bus.addListener((ServerTickEvent.Post event) -> {
      applySnaps(event.getServer());
      if (event.getServer().getTickCount() % 100 == 51)
        event.getServer().getPlayerList().getPlayers().forEach(EnvesGuard::mapStage);
    });
  }

  /** Operators with the protection bypass on ({@code /entrelumen admin protection bypass}). */
  static boolean bypass(ServerPlayer player) {
    return player.hasPermissions(2) && StructureProtection.actor(player).bypass();
  }

  /**
   * The server moved the player (entry, rescue, respawn, an operator's teleport): the no-clip check
   * starts over, and a pull-back not sent yet is dropped, since the server's own move wins.
   */
  static void moved(Player player) {
    Mover mover = MOVERS.get(player.getUUID());
    if (mover == null) return;
    mover.lastPos = null;
    mover.snapTo = null;
    mover.snapKey = null;
  }

  static void tell(ServerPlayer player, String key) {
    Mover mover = MOVERS.computeIfAbsent(player.getUUID(), id -> new Mover());
    long now = player.serverLevel().getGameTime();
    if (now - mover.lastMessage < MESSAGE_INTERVAL && now >= mover.lastMessage) return;
    mover.lastMessage = now;
    player.displayClientMessage(Component.translatable(key), true);
  }

  // ---- Ways in and out ----------------------------------------------------------------------

  /** Only the gate and the exit portals cross into or out of the Envés. */
  static void onTravel(EntityTravelToDimensionEvent event) {
    if (Enves.ownTravel) return;
    Entity entity = event.getEntity();
    boolean involved = entity.level().dimension().equals(Enves.LEVEL) || event.getDimension().equals(Enves.LEVEL);
    if (!involved) return;
    if (entity instanceof ServerPlayer player) {
      if (bypass(player)) return;
      tell(player, "entrelumen.enves.no_way_out");
    }
    event.setCanceled(true);
  }

  /**
   * No player teleports inside: pearls, chorus fruit, and any mod's blink or travel staff that fires
   * the event. Mobs (endermen, blinking echoes) still do. The teleport commands are left to
   * {@link #onCommand}, which denies them to players inside, so operators can still move people.
   */
  static void onTeleport(EntityTeleportEvent event) {
    if (event instanceof EntityTeleportEvent.TeleportCommand || event instanceof EntityTeleportEvent.SpreadPlayersCommand) {
      // An operator moved them: the no-clip check starts over from where they land.
      if (event.getEntity() instanceof ServerPlayer player) moved(player);
      return;
    }
    if (!(event.getEntity() instanceof ServerPlayer player) || !Enves.inEnves(player) || bypass(player)) return;
    event.setCanceled(true);
    tell(player, "entrelumen.enves.no_teleport");
  }

  /** Echoes are not captured, leashed, named or ridden inside: a soul vial would leave a seal without its guardian. */
  static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
    if (!shieldsEcho(event.getEntity(), event.getTarget())) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.FAIL);
  }

  static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
    if (!shieldsEcho(event.getEntity(), event.getTarget())) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.FAIL);
  }

  private static boolean shieldsEcho(Player player, Entity target) {
    return player instanceof ServerPlayer server && Enves.inEnves(server) && EnvesEchoes.isEcho(target) && !bypass(server);
  }

  /** Pearls, chorus, warp scrolls and the rest of the forbidden tag do nothing inside (and are kept). */
  static void onUseItem(PlayerInteractEvent.RightClickItem event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !Enves.inEnves(player) || bypass(player)) return;
    if (!event.getItemStack().is(FORBIDDEN)) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.FAIL);
    tell(player, "entrelumen.enves.no_teleport");
  }

  static void onCommand(CommandEvent event) {
    var source = event.getParseResults().getContext().getSource();
    if (!(source.getEntity() instanceof ServerPlayer player) || !Enves.inEnves(player) || bypass(player)) return;
    if (!EnvesRules.commandDenied(event.getParseResults().getReader().getString(), EnvesConfig.settings().deniedCommands()))
      return;
    event.setCanceled(true);
    player.displayClientMessage(Component.translatable("entrelumen.enves.no_command"), false);
  }

  // ---- Flight -------------------------------------------------------------------------------

  static void onPlayerTick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !Enves.inEnves(player)) return;
    if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
      moved(player); // where they stood before switching back to survival is no reference
      return;
    }
    if (player.getAbilities().mayfly || player.getAbilities().flying) {
      if (bypass(player)) return;
      player.getAbilities().mayfly = false;
      player.getAbilities().flying = false;
      player.onUpdateAbilities();
      tell(player, "entrelumen.enves.no_flight");
    }
    if (player.isFallFlying()) {
      player.stopFallFlying();
      tell(player, "entrelumen.enves.no_flight");
    }
    if (player.isPassenger()) player.stopRiding();
    Mover mover = MOVERS.computeIfAbsent(player.getUUID(), id -> new Mover());
    if (mover.snapTo != null) return; // already going back, after this server tick
    double y = player.getY();
    double dy = Double.isNaN(mover.lastY) ? 0 : y - mover.lastY;
    mover.lastY = y;
    if (player.onGround()) mover.ground = player.position();
    BlockPos feet = player.blockPosition();
    boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()
        && player.level().getBlockState(feet).isAir();
    boolean exempt = player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING);
    if (mover.lift.observe(airborne, exempt, dy) && !bypass(player)) {
      snap(mover, mover.ground != null ? mover.ground : player.position(), "entrelumen.enves.no_flight");
      return;
    }
    noClip(player, mover);
  }

  /**
   * A player who covered more than two blocks per client step through a block (a blink spell, a
   * travel staff, any mod that sets the position without an event) goes back where they stood.
   * Both a ray at the waist and one at the eyes must cross a block, so a step or a slab on the way is
   * no wall.
   */
  private static void noClip(ServerPlayer player, Mover mover) {
    Vec3 pos = player.position();
    Vec3 last = mover.lastPos;
    if (last != null && EnvesRules.longMove(last.distanceToSqr(pos), moveSteps(player)) && !bypass(player)
        && blocked(player, last, pos, 0.5) && blocked(player, last, pos, player.getEyeHeight())) {
      snap(mover, last, "entrelumen.enves.no_teleport");
      return;
    }
    mover.lastPos = pos;
  }

  private static boolean blocked(ServerPlayer player, Vec3 from, Vec3 to, double height) {
    var hit = player.level().clip(new ClipContext(from.add(0, height, 0), to.add(0, height, 0), ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE, CollisionContext.empty()));
    return hit.getType() == HitResult.Type.BLOCK;
  }

  private static void snap(Mover mover, Vec3 to, String key) {
    mover.snapTo = to;
    mover.snapKey = key;
  }

  /**
   * Sends the pull-backs decided this tick, outside every connection's tick, so they stick: the
   * server holds the player there until the client accepts, and the next check starts from there.
   */
  static void applySnaps(MinecraftServer server) {
    for (var entry : MOVERS.entrySet()) {
      Mover mover = entry.getValue();
      Vec3 to = mover.snapTo;
      if (to == null) continue;
      String key = mover.snapKey;
      mover.snapTo = null;
      mover.snapKey = null;
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      if (player == null || player.isDeadOrDying() || !Enves.inEnves(player)) continue;
      player.fallDistance = 0;
      player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
      mover.lastY = to.y;
      mover.lastPos = to;
      if (key != null) tell(player, key);
    }
  }

  /**
   * How many move packets the server took from this player since its last tick: one a tick
   * normally, several after a lag spike on the server or the network. Read from vanilla's own
   * counters (the connection evens them right after the player's tick); when those are not
   * reachable, guessed from how long the server tick took to come.
   */
  private static int moveSteps(ServerPlayer player) {
    if (!movesFailed && receivedMoves == null) {
      try {
        Field received = ServerGamePacketListenerImpl.class.getDeclaredField("receivedMovePacketCount");
        Field known = ServerGamePacketListenerImpl.class.getDeclaredField("knownMovePacketCount");
        received.setAccessible(true);
        known.setAccessible(true);
        knownMoves = known;
        receivedMoves = received;
      } catch (ReflectiveOperationException | RuntimeException e) {
        movesFailed = true;
        LOGGER.warn("Envés: the move packet counters are not reachable ({}); the no-clip check judges lag by tick time", e.toString());
      }
    }
    if (receivedMoves != null && player.connection != null) {
      try {
        return receivedMoves.getInt(player.connection) - knownMoves.getInt(player.connection);
      } catch (ReflectiveOperationException | RuntimeException e) {
        movesFailed = true;
        receivedMoves = null;
        LOGGER.warn("Envés: the move packet counters could not be read ({}); the no-clip check judges lag by tick time", e.toString());
      }
    }
    return (int) Math.max(1, Math.round(tickGapNanos / 50_000_000.0));
  }

  // ---- Spawning -----------------------------------------------------------------------------

  /** No natural or spawner mobs in the Envés or in the Sealed Stair; encounters spawn by hook. */
  static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
    MobSpawnType type = event.getSpawnType();
    boolean natural = type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION || type == MobSpawnType.SPAWNER
        || type == MobSpawnType.PATROL || type == MobSpawnType.STRUCTURE;
    if (!natural) return;
    Level level = event.getLevel().getLevel();
    if (level.dimension().equals(Enves.LEVEL)) {
      event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
      return;
    }
    // Chunk generation checks spawns on worker threads; the saved data is only read on the server's.
    MinecraftServer server = level.getServer();
    if (level.dimension().equals(Level.OVERWORLD) && server != null && server.isSameThread()
        && EnvesData.get(server).entrance.inBox(BlockPos.containing(event.getX(), event.getY(), event.getZ())))
      event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
  }

  // ---- Maps ---------------------------------------------------------------------------------

  /**
   * FTB Chunks draws what the client has loaded, which would reveal the whole floor: inside the Envés
   * the player loses the {@code ftbchunks_mapping} stage, outside they have it (the pack sets
   * {@code require_game_stage}; FTB Library syncs the stage to the client).
   */
  static void mapStage(ServerPlayer player) {
    if (stageFailed || !EnvesConfig.settings().mapStage()) return;
    try {
      var provider = StageHelper.getInstance().getProvider();
      boolean inside = Enves.inEnves(player) && !bypass(player);
      boolean has = provider.has(player, MAP_STAGE);
      if (inside && has) provider.remove(player, MAP_STAGE);
      else if (!inside && !has) provider.add(player, MAP_STAGE);
    } catch (RuntimeException | LinkageError e) {
      stageFailed = true;
      LOGGER.error("FTB Library stages failed; the FTB Chunks map is not switched off in the Envés", e);
    }
  }

  // ---- Waystones ----------------------------------------------------------------------------

  /**
   * Waystones 21.1.41's {@code WaystoneTeleportEvent.Pre} (a cancellable Balm event), by reflection
   * like {@link WaystonesBridge}: warps from or to the Envés are refused before any cost is paid.
   * Registered once, at common setup.
   */
  static void hookWaystones() {
    if (!WaystonesBridge.available()) return;
    try {
      String api = "net.blay09.mods.waystones.api.";
      Class<?> pre = Class.forName(api + "event.WaystoneTeleportEvent$Pre");
      Method context = pre.getMethod("getContext");
      Class<?> contextType = Class.forName(api + "WaystoneTeleportContext");
      Method entity = contextType.getMethod("getEntity");
      Method target = contextType.getMethod("getTargetWaystone");
      Method dimension = Class.forName(api + "Waystone").getMethod("getDimension");
      Consumer<Object> handler = event -> {
        try {
          Object ctx = context.invoke(event);
          boolean from = entity.invoke(ctx) instanceof Entity e && e.level().dimension().equals(Enves.LEVEL);
          Object waystone = target.invoke(ctx);
          boolean to = waystone != null && Enves.LEVEL.equals(dimension.invoke(waystone));
          if (from || to) {
            if (entity.invoke(ctx) instanceof ServerPlayer player && bypass(player)) return;
            if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) cancellable.setCanceled(true);
            if (entity.invoke(ctx) instanceof ServerPlayer player) tell(player, "entrelumen.enves.no_way_out");
          }
        } catch (ReflectiveOperationException | RuntimeException failure) {
          LOGGER.debug("Could not judge a waystone warp", failure);
        }
      };
      Object events = Class.forName("net.blay09.mods.balm.api.Balm").getMethod("getEvents").invoke(null);
      Class.forName("net.blay09.mods.balm.api.event.BalmEvents").getMethod("onEvent", Class.class, Consumer.class)
          .invoke(events, pre, handler);
      LOGGER.info("Waystones: warps from and to the Envés are refused");
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      LOGGER.warn("Waystones is loaded but its teleport event could not be hooked; the dimension guard still refuses warps",
          failure);
    }
  }

  /** For tests: whether a pull-back is waiting for the end of the server tick. */
  static boolean pullingBack(ServerPlayer player) {
    Mover mover = MOVERS.get(player.getUUID());
    return mover != null && mover.snapTo != null;
  }

  /** For tests: forget a player's movement history. */
  static void forget(ServerPlayer player) {
    MOVERS.remove(player.getUUID());
  }

}
