package dev.entrelumen;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Safe travel between the Overworld and Solsticio: departure points, the arrival marker, bed or
 * world-spawn fallbacks, dismounting, the portal's anti-bounce rest, the void rescue, the world
 * border around the city and the absence of hostile spawns.
 */
public final class SolsticioTravel {
  /** Arrivals younger than this make the next portal contact rest instead of travelling. */
  static final int ARRIVAL_WINDOW = 60;
  /** How long the portal rests for a player after an arrival; refreshed while they stay in it. */
  static final int PORTAL_REST = 100;
  /** Falling this far below the city floor brings the player back to the arrival point. */
  static final int VOID_MARGIN = 24;

  private static final Map<MinecraftServer, Map<UUID, Contact>> CONTACTS = new WeakHashMap<>();

  private static final class Contact {
    long arrivedAt = Long.MIN_VALUE / 2;
    long lastTouch = Long.MIN_VALUE / 2;
    long restUntil;
  }

  private SolsticioTravel() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(SolsticioTravel::onTravel);
    NeoForge.EVENT_BUS.addListener(SolsticioTravel::onChangedDimension);
    NeoForge.EVENT_BUS.addListener(SolsticioTravel::onLogin);
    NeoForge.EVENT_BUS.addListener(SolsticioTravel::onSpawnCheck);
  }

  public static boolean inSolsticio(ServerPlayer player) {
    return player.level().dimension().equals(Solsticio.LEVEL);
  }

  private static Contact contact(ServerPlayer player) {
    synchronized (CONTACTS) {
      return CONTACTS.computeIfAbsent(player.server, ignored -> new HashMap<>())
          .computeIfAbsent(player.getUUID(), ignored -> new Contact());
    }
  }

  // ---- Events -----------------------------------------------------------------------------

  /** Leaving the Overworld by any means remembers where, for the way home. */
  static void onTravel(EntityTravelToDimensionEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
    if (player.level().dimension().equals(Level.OVERWORLD) && !event.getDimension().equals(Level.OVERWORLD))
      rememberHome(player);
  }

  static void rememberHome(ServerPlayer player) {
    if (!player.level().dimension().equals(Level.OVERWORLD)) return;
    SolsticioData data = SolsticioData.get(player.server);
    data.returns.put(player.getUUID(), new SolsticioData.ReturnPoint(Level.OVERWORLD, player.blockPosition(),
        player.getYRot()));
    data.setDirty();
  }

  static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    contact(player).arrivedAt = player.serverLevel().getGameTime();
    if (event.getTo().equals(Solsticio.LEVEL)) arrived(player);
  }

  static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player && inSolsticio(player)) arrived(player);
  }

  private static void arrived(ServerPlayer player) {
    if (!SolsticioCity.ensure(player.server)) {
      rescue(player);
      return;
    }
    SolsticioCity.claimPlot(player);
  }

  /** No natural hostile spawns in Solsticio, whatever biome modifiers other mods add. */
  static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
    if (event.getEntity().getType().getCategory() == MobCategory.MONSTER
        && event.getLevel().getLevel().dimension().equals(Solsticio.LEVEL)
        && event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.SPAWN_EGG
        && event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.COMMAND)
      event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
  }

  /** Twice a second: void rescue and border upkeep for players in Solsticio. */
  static void tick(MinecraftServer server) {
    if (server.getTickCount() % 10 != 0) return;
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    if (level == null || level.players().isEmpty()) return;
    SolsticioData data = SolsticioData.get(server);
    if (server.getTickCount() % 200 == 0) applyBorder(server);
    int floor = data.footprint == null ? CityLayout.BASE_Y : data.footprint.minY();
    int radius = data.borderRadius > 0 ? data.borderRadius + 16 : Integer.MAX_VALUE;
    for (ServerPlayer player : level.players())
      if (!player.isSpectator() && (player.getY() < floor - VOID_MARGIN
          || Math.abs(player.getX()) > radius || Math.abs(player.getZ()) > radius))
        rescue(player);
  }

  /** Solsticio's own border: centred on the city, just wider than it. */
  static void applyBorder(MinecraftServer server) {
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    SolsticioData data = SolsticioData.get(server);
    if (level == null || data.borderRadius <= 0) return;
    var border = level.getWorldBorder();
    double size = data.borderRadius * 2.0;
    if (border.getCenterX() != 0 || border.getCenterZ() != 0) border.setCenter(0, 0);
    if (border.getSize() != size) border.setSize(size);
  }

  /**
   * Brings a player standing in Solsticio back to safety: the arrival point once the city is
   * ready, otherwise a slow fall above the origin while it forms.
   */
  static void rescue(ServerPlayer player) {
    if (!inSolsticio(player)) return;
    SolsticioData data = SolsticioData.get(player.server);
    ServerLevel level = player.serverLevel();
    player.fallDistance = 0;
    if (data.ready()) {
      Vec3 target = safeSpot(level, data.arrival);
      player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
    } else if (player.getY() < CityLayout.BASE_Y) {
      player.teleportTo(level, 0.5, CityLayout.BASE_Y + 24, 0.5, player.getYRot(), player.getXRot());
      player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
    } else {
      player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
    }
  }

  // ---- Destinations -----------------------------------------------------------------------

  /** Feet position standing on a sturdy block with two free blocks, near the wanted spot. */
  static Vec3 safeSpot(ServerLevel level, BlockPos wanted) {
    level.getChunk(wanted.getX() >> 4, wanted.getZ() >> 4);
    if (standable(level, wanted)) return Vec3.atBottomCenterOf(wanted);
    for (int dy = 1; dy <= 8; dy++) {
      if (standable(level, wanted.above(dy))) return Vec3.atBottomCenterOf(wanted.above(dy));
      if (standable(level, wanted.below(dy))) return Vec3.atBottomCenterOf(wanted.below(dy));
    }
    for (int r = 1; r <= 3; r++)
      for (int dx = -r; dx <= r; dx++)
        for (int dz = -r; dz <= r; dz++)
          for (int dy = -2; dy <= 2; dy++) {
            BlockPos pos = wanted.offset(dx, dy, dz);
            if (standable(level, pos)) return Vec3.atBottomCenterOf(pos);
          }
    return Vec3.atBottomCenterOf(wanted);
  }

  static boolean standable(ServerLevel level, BlockPos pos) {
    if (!level.isInWorldBounds(pos)) return false;
    BlockState below = level.getBlockState(pos.below());
    return below.getFluidState().isEmpty() && below.isFaceSturdy(level, pos.below(), Direction.UP)
        && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
        && level.getBlockState(pos).getFluidState().isEmpty()
        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
  }

  /** Highest standable block at the origin column of the city, for templates without arrival. */
  static BlockPos fallbackArrival(ServerLevel level, ProtectionRules.Box footprint) {
    int x = 0, z = 0;
    level.getChunk(0, 0);
    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    BlockPos pos = new BlockPos(x, Math.max(top, footprint == null ? CityLayout.BASE_Y + 1 : footprint.minY() + 1), z);
    return pos;
  }

  /** The Overworld spot a player returns to: last departure, then bed, then world spawn. */
  static Vec3 homeFor(ServerPlayer player, float[] yaw) {
    ServerLevel overworld = player.server.overworld();
    SolsticioData data = SolsticioData.get(player.server);
    var point = data.returns.get(player.getUUID());
    if (point != null && point.dimension().equals(Level.OVERWORLD)) {
      yaw[0] = point.yaw();
      overworld.getChunk(point.pos().getX() >> 4, point.pos().getZ() >> 4);
      if (standable(overworld, point.pos())) return Vec3.atBottomCenterOf(point.pos());
      Vec3 near = safeSpot(overworld, point.pos());
      if (standable(overworld, BlockPos.containing(near))) return near;
    }
    BlockPos respawn = player.getRespawnPosition();
    if (respawn != null && player.getRespawnDimension().equals(Level.OVERWORLD)) {
      overworld.getChunk(respawn.getX() >> 4, respawn.getZ() >> 4);
      BlockState state = overworld.getBlockState(respawn);
      Optional<Vec3> bed = state.getBlock() instanceof BedBlock
          ? BedBlock.findStandUpPosition(EntityType.PLAYER, overworld, respawn, state.getValue(BedBlock.FACING),
              player.getRespawnAngle())
          : Optional.empty();
      if (bed.isPresent()) {
        yaw[0] = player.getRespawnAngle();
        return bed.get();
      }
    }
    BlockPos spawn = overworld.getSharedSpawnPos();
    overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
    BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
    yaw[0] = overworld.getSharedSpawnAngle();
    return safeSpot(overworld, top);
  }

  // ---- Travel -----------------------------------------------------------------------------

  /** Mounts and passengers never travel: the player leaves them behind. */
  private static void dismount(ServerPlayer player) {
    player.stopRiding();
    player.ejectPassengers();
  }

  private static boolean move(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw) {
    dismount(player);
    ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));
    target.getChunk(chunk.x, chunk.z);
    target.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 1, player.getId());
    player.fallDistance = 0;
    if (target == player.level()) {
      player.teleportTo(target, pos.x, pos.y, pos.z, yaw, player.getXRot());
      return true;
    }
    var moved = player.changeDimension(new DimensionTransition(target, pos, Vec3.ZERO, yaw, player.getXRot(),
        DimensionTransition.DO_NOTHING));
    return moved != null && player.level() == target;
  }

  /** Into Solsticio at the arrival marker; false when refused (city forming, event cancelled). */
  public static boolean toSolsticio(ServerPlayer player) {
    if (!SolsticioCity.ensure(player.server)) {
      player.displayClientMessage(Component.translatable("entrelumen.solsticio.forming"), true);
      return false;
    }
    ServerLevel level = player.server.getLevel(Solsticio.LEVEL);
    if (level == null) return false;
    SolsticioData data = SolsticioData.get(player.server);
    rememberHome(player);
    Vec3 target = safeSpot(level, data.arrival);
    float yaw = data.portal == null ? player.getYRot() : facing(data.arrival, data.portal);
    if (!move(player, level, target, yaw)) return false;
    contact(player).arrivedAt = level.getGameTime();
    level.playSound(null, BlockPos.containing(target), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
    SolsticioCity.claimPlot(player);
    return true;
  }

  /** Back to the Overworld: last departure, bed or world spawn. */
  public static boolean home(ServerPlayer player) {
    float[] yaw = {player.getYRot()};
    Vec3 target = homeFor(player, yaw);
    ServerLevel overworld = player.server.overworld();
    if (!move(player, overworld, target, yaw[0])) return false;
    contact(player).arrivedAt = overworld.getGameTime();
    overworld.playSound(null, BlockPos.containing(target), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
    return true;
  }

  static float facing(BlockPos from, BlockPos target) {
    double dx = target.getX() - from.getX(), dz = target.getZ() - from.getZ();
    if (dx == 0 && dz == 0) return 0f;
    return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
  }

  /**
   * A player touching an open portal. Arriving (by any teleport, a waystone included) makes the
   * portal rest for that player; standing in it keeps it resting, so nobody bounces straight back.
   * Returns whether the player travelled.
   */
  public static boolean portalContact(ServerPlayer player, boolean solsticioSide) {
    long now = player.serverLevel().getGameTime();
    Contact contact = contact(player);
    boolean fresh = now - contact.lastTouch > 1;
    contact.lastTouch = now;
    if (fresh && now - contact.arrivedAt <= ARRIVAL_WINDOW) contact.restUntil = now + PORTAL_REST;
    if (now < contact.restUntil) {
      contact.restUntil = now + PORTAL_REST;
      if (fresh) player.displayClientMessage(Component.translatable("entrelumen.solsticio.portal_resting"), true);
      return false;
    }
    if (player.isPassenger() || player.isVehicle()) dismount(player);
    boolean moved = solsticioSide ? home(player) : toSolsticio(player);
    if (moved) contact.restUntil = player.serverLevel().getGameTime() + PORTAL_REST;
    return moved;
  }

  /** For tests: forget arrivals and rests. */
  static void forget(ServerPlayer player) {
    synchronized (CONTACTS) {
      var map = CONTACTS.get(player.server);
      if (map != null) map.remove(player.getUUID());
    }
  }
}
