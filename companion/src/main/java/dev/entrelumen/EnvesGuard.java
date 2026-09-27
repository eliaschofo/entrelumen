package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.integration.stages.StageHelper;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
 * no fluids): no flight of any kind, no ender pearls or chorus fruit, no way in or out but the
 * gate and the exit portals (waystones, portals and teleport commands included), no natural spawns,
 * and no map but the Envés's own (FTB Chunks through its {@code ftbchunks_mapping} stage;
 * JourneyMap is switched off on the client). Operators with the protection bypass are exempt.
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
    long lastMessage = Long.MIN_VALUE / 2;
  }

  private static final Map<UUID, Mover> MOVERS = new HashMap<>();
  private static boolean stageFailed;

  private EnvesGuard() {}

  static void register() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener(EventPriority.HIGHEST, EnvesGuard::onTravel);
    bus.addListener(EnvesGuard::onPearl);
    bus.addListener(EnvesGuard::onChorus);
    bus.addListener(EventPriority.HIGH, EnvesGuard::onUseItem);
    bus.addListener(EventPriority.HIGH, EnvesGuard::onCommand);
    bus.addListener(EnvesGuard::onPlayerTick);
    bus.addListener(EnvesGuard::onSpawnCheck);
    bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> MOVERS.remove(event.getEntity().getUUID()));
    bus.addListener((ServerTickEvent.Post event) -> {
      if (event.getServer().getTickCount() % 100 == 51)
        event.getServer().getPlayerList().getPlayers().forEach(EnvesGuard::mapStage);
    });
  }

  /** Operators with the protection bypass on ({@code /entrelumen admin protection bypass}). */
  static boolean bypass(ServerPlayer player) {
    return player.hasPermissions(2) && StructureProtection.actor(player).bypass();
  }

  private static void tell(ServerPlayer player, String key) {
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

  static void onPearl(EntityTeleportEvent.EnderPearl event) {
    if (event.getEntity().level().dimension().equals(Enves.LEVEL) && !(event.getPlayer() != null && bypass(event.getPlayer()))) {
      event.setCanceled(true);
      if (event.getPlayer() != null) tell(event.getPlayer(), "entrelumen.enves.no_teleport");
    }
  }

  static void onChorus(EntityTeleportEvent.ChorusFruit event) {
    if (!event.getEntity().level().dimension().equals(Enves.LEVEL)) return;
    if (event.getEntityLiving() instanceof ServerPlayer player) {
      if (bypass(player)) return;
      tell(player, "entrelumen.enves.no_teleport");
    }
    event.setCanceled(true);
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
    if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) return;
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
    double y = player.getY();
    double dy = Double.isNaN(mover.lastY) ? 0 : y - mover.lastY;
    mover.lastY = y;
    if (player.onGround()) mover.ground = player.position();
    BlockPos feet = player.blockPosition();
    boolean airborne = !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()
        && player.level().getBlockState(feet).isAir();
    boolean exempt = player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING);
    if (mover.lift.observe(airborne, exempt, dy) && !bypass(player)) {
      Vec3 back = mover.ground != null ? mover.ground : player.position();
      player.fallDistance = 0;
      player.connection.teleport(back.x, back.y, back.z, player.getYRot(), player.getXRot());
      mover.lastY = back.y;
      tell(player, "entrelumen.enves.no_flight");
    }
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
    if (level.dimension().equals(Level.OVERWORLD) && level.getServer() != null
        && EnvesData.get(level.getServer()).entrance.inBox(BlockPos.containing(event.getX(), event.getY(), event.getZ())))
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

  /** For tests: forget a player's movement history. */
  static void forget(ServerPlayer player) {
    MOVERS.remove(player.getUUID());
  }

}
