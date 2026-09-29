package dev.entrelumen;

import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesBalance.Role;
import dev.entrelumen.EnvesData.Attempt;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Floor V, El Eclipse (docs/design/dungeon-enves.md, «Jefe»): the Sour Light wakes when somebody of
 * the group steps into the middle of the arena, and the attempt's World Tier sets its health and
 * damage. When it falls the victory portal wakes ({@link Enves#bossDefeated}) and the boss chest (a
 * Lootr chest of {@code entrelumen:enves/boss}, one loot per player) rises by the portal: it is not
 * there before, so nobody loots it past the boss. A boss that leaves the world without falling comes
 * back to the arena.
 */
public final class EnvesBoss {
  private EnvesBoss() {}

  /** The hook: floor V is placed. Nothing wakes yet; the victory portal stays dark (the engine's default would light it). */
  static void floorReady(EnvesHooks.Floor floor, EnvesHooks.WorldMarker center) {
    var runs = EnvesRuns.get(floor.level().getServer());
    runs.run(floor.attempt().id);
    runs.setDirty();
  }

  /** Somebody steps into the middle of the arena: the Sour Light condenses. */
  static WhiteWither awaken(EnvesHooks.Floor floor, BlockPos center, ServerPlayer first) {
    if (floor.attempt().bossDefeated) return null;
    var runs = EnvesRuns.get(floor.level().getServer());
    var run = runs.run(floor.attempt().id);
    if (run.bossSpawned && EnvesEchoes.find(run.boss).orElse(null) instanceof WhiteWither alive && alive.isAlive()) return alive;
    WhiteWither boss = spawn(floor.level(), floor.attempt(), center);
    if (boss == null) return null;
    run.boss = boss.getUUID();
    run.bossSpawned = true;
    runs.setDirty();
    if (first != null) boss.setTarget(first);
    for (ServerPlayer player : Enves.membersInside(floor.level().getServer(), floor.attempt()))
      player.sendSystemMessage(Component.translatable("entrelumen.enves.boss.awakens"));
    return boss;
  }

  /** Makes the boss at an arena centre (feet level of its floor), scaled for the attempt. */
  static WhiteWither spawn(ServerLevel level, Attempt attempt, BlockPos center) {
    WhiteWither boss = EnvesContent.WHITE_WITHER.get().create(level);
    if (boss == null) return null;
    var balance = EnvesContentConfig.balance();
    Tier tier = attempt.tier;
    boss.home(center);
    boss.moveTo(center.getX() + 0.5, center.getY() + 0.2, center.getZ() + 0.5, 0, 0);
    EnvesEchoes.skipApotheosisAugments(boss);
    boss.setData(EnvesEchoes.ATTACHMENT.get(), new EnvesEchoes.Data(attempt.id, EnvesLayout.BOSS_DEPTH, Role.BOSS.id(), List.of(),
        (float) balance.damage(Role.BOSS, null, tier, EnvesLayout.BOSS_DEPTH), tier.ordinal()));
    var health = boss.getAttribute(Attributes.MAX_HEALTH);
    if (health != null) health.setBaseValue(balance.health(Role.BOSS, null, tier, EnvesLayout.BOSS_DEPTH));
    boss.setHealth(boss.getMaxHealth());
    boss.setPersistenceRequired();
    if (!level.addFreshEntity(boss)) return null;
    level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4f, 0.8f);
    level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 2, center.getZ() + 0.5, 60, 1.5, 2, 1.5, 0.1);
    return boss;
  }

  /** Called when the boss's echo data reports its death. */
  static void defeated(ServerLevel level, Mob boss, EnvesEchoes.Data data) {
    EnvesData.get(level.getServer()).attempt(data.attempt()).ifPresent(attempt -> {
      for (ServerPlayer player : Enves.membersInside(level.getServer(), attempt))
        player.sendSystemMessage(Component.translatable("entrelumen.enves.boss.defeated"));
      Enves.bossDefeated(attempt);
    });
  }

  /** The Lifecycle's part: the victory is set, so the boss chest rises by the portal. */
  static void bossDefeated(Attempt attempt) {
    MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    if (server == null) return;
    ServerLevel level = Enves.level(server);
    if (level == null || attempt.floor(EnvesLayout.BOSS_DEPTH).placement != EnvesData.Placement.READY) return;
    var runs = EnvesRuns.get(server);
    var run = runs.run(attempt.id);
    if (EnvesEchoes.find(run.boss).orElse(null) instanceof WhiteWither boss && boss.isAlive() && !boss.isDeadOrDying())
      boss.discard();
    if (run.bossChestPlaced) return;
    run.bossChestPlaced = true;
    runs.setDirty();
    var floor = Enves.floor(level, attempt, EnvesLayout.BOSS_DEPTH);
    int portal = floor.layout().exit();
    var markers = floor.markers(portal);
    var chest = markers.stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.CHEST && m.marker().head().equals("boss")).findFirst();
    EnvesHooks.WorldMarker marker = chest.orElseGet(() -> new EnvesHooks.WorldMarker(
        new EnvesMarkers.Marker(EnvesMarkers.Kind.CHEST, "boss/south"),
        floor.origin(portal).offset(EnvesGeometry.C - 4, 1, EnvesGeometry.C - 4), portal, EnvesLayout.Role.PORTAL));
    EnvesPlacer.defaultChest(floor, marker);
    BlockPos pos = marker.pos();
    level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1f, 1f);
    level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.5, 0.8, 0.5, 0.05);
  }

  /** Under half its health the boss calls two lesser echoes (floor V's escorts) to the arena's edges. */
  static void callEchoes(WhiteWither boss, ServerPlayer target) {
    if (!(boss.level() instanceof ServerLevel level)) return;
    var data = EnvesEchoes.data(boss);
    if (data.isEmpty()) return;
    var attempt = EnvesData.get(level.getServer()).attempt(data.get().attempt());
    if (attempt.isEmpty()) return;
    var table = EnvesContentConfig.table(Enves.tileset(EnvesLayout.BOSS_DEPTH));
    var random = EnvesPuzzleRules.random(attempt.get().seed, EnvesLayout.BOSS_DEPTH, 0, 71);
    BlockPos home = boss.home().equals(BlockPos.ZERO) ? boss.blockPosition() : boss.home();
    for (int i = 0; i < 2 && !table.escorts().isEmpty(); i++) {
      double angle = random.nextDouble() * Math.PI * 2;
      BlockPos at = EnvesEchoes.safeSpot(level, home.offset((int) (Math.cos(angle) * 12), 0, (int) (Math.sin(angle) * 12)), 4);
      EnvesEchoes.spawn(level, new EnvesEchoes.Spec(EnvesEchoTables.draw(table.escorts(), random), Role.ESCORT, attempt.get(),
          EnvesLayout.BOSS_DEPTH, List.of()), Vec3.atBottomCenterOf(at), target);
    }
    level.playSound(null, home, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.6f);
  }

  /** Seconds each attempt's boss has been missing from a loaded, ticking arena. */
  private static final java.util.Map<java.util.UUID, Integer> MISSING = new java.util.HashMap<>();

  /**
   * Once a second: a boss that left the world without falling comes back when somebody stands in the
   * arena, once the arena's centre ticks and the boss has been missing there for three checks in a
   * row (a chunk that just loaded shows its entities a moment later; never two bosses).
   */
  static void tick(MinecraftServer server) {
    if (server.getTickCount() % 20 != 17) return;
    ServerLevel level = Enves.level(server);
    if (level == null) return;
    for (var run : EnvesRuns.get(server).runs()) {
      if (!run.bossSpawned || run.boss == null) continue;
      var attempt = EnvesData.get(server).attempt(run.attempt);
      if (attempt.isEmpty() || attempt.get().bossDefeated || attempt.get().status != EnvesData.Status.OPEN) continue;
      if (EnvesEchoes.find(run.boss).isPresent()) {
        MISSING.remove(run.attempt);
        continue;
      }
      var floor = Enves.floor(level, attempt.get(), EnvesLayout.BOSS_DEPTH);
      var centers = floor.layout().withRole(EnvesLayout.Role.ARENA_CENTER);
      if (centers.isEmpty()) continue;
      int center = centers.getFirst();
      for (ServerPlayer player : Enves.membersInside(server, attempt.get())) {
        int cell = EnvesGeometry.cellAt(attempt.get().slot, player.getBlockX(), player.getBlockZ());
        var role = floor.layout().role(cell);
        if (EnvesGeometry.depthAt(player.getBlockY()) != EnvesLayout.BOSS_DEPTH
            || (role != EnvesLayout.Role.ARENA && role != EnvesLayout.Role.ARENA_CENTER)) continue;
        BlockPos at = floor.markers(center).stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.BOSS_CENTER)
            .map(EnvesHooks.WorldMarker::pos).findFirst().orElse(floor.origin(center).offset(EnvesGeometry.C, 1, EnvesGeometry.C));
        if (!level.isPositionEntityTicking(at)) break;
        int missing = MISSING.merge(run.attempt, 1, Integer::sum);
        if (missing < 3) break;
        MISSING.remove(run.attempt);
        run.bossSpawned = false;
        awaken(floor, at, player);
        break;
      }
    }
  }
}
