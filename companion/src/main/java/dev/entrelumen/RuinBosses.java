package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

/**
 * The boss challenges: when a member of a team that has not beaten one comes within its radius,
 * its mobs rise for that team, named, with the definition's attributes and one shared boss bar.
 * Killing them all solves the challenge for that team. A team that walks away leaves them to fade
 * (they rise again on its return); mobs of a finished run never come back from disk. On peaceful
 * the guardians step aside.
 */
public final class RuinBosses {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final String TAG = "entrelumen_ruin_boss";
  static final int INTERVAL = 10, BAR_RANGE = 48, ABANDON_TICKS = 600;

  static final class Group {
    final ResourceLocation ruin;
    final String challenge;
    final UUID campaign;
    @Nullable final UUID founder;
    final ResourceKey<Level> dimension;
    final BlockPos spawn;
    final int radius;
    final List<UUID> mobs = new ArrayList<>();
    final ServerBossEvent bar;
    long lastSeen;

    Group(ResourceLocation ruin, String challenge, UUID campaign, @Nullable UUID founder, ResourceKey<Level> dimension,
        BlockPos spawn, int radius, ServerBossEvent bar, long now) {
      this.ruin = ruin;
      this.challenge = challenge;
      this.campaign = campaign;
      this.founder = founder;
      this.dimension = dimension;
      this.spawn = spawn;
      this.radius = radius;
      this.bar = bar;
      this.lastSeen = now;
    }

    String key() {
      return RuinBosses.key(ruin, challenge, campaign);
    }
  }

  private static final Map<MinecraftServer, Map<String, Group>> GROUPS = new WeakHashMap<>();

  private RuinBosses() {}

  static String key(ResourceLocation ruin, String challenge, UUID campaign) {
    return ruin + "|" + challenge + "|" + campaign;
  }

  static Map<String, Group> groups(MinecraftServer server) {
    synchronized (GROUPS) {
      return GROUPS.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
    }
  }

  static void forget(MinecraftServer server) {
    synchronized (GROUPS) {
      var groups = GROUPS.remove(server);
      if (groups != null) groups.values().forEach(group -> group.bar.removeAllPlayers());
    }
  }

  static void tick(MinecraftServer server) {
    if (server.getTickCount() % INTERVAL == 0) scan(server);
  }

  /** Raises guardians for teams in range, updates the bars and lets abandoned groups fade. */
  static void scan(MinecraftServer server) {
    var data = RuinData.get(server);
    long now = server.overworld().getGameTime();
    var groups = groups(server);
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      if (player.isSpectator() || player instanceof FakePlayer || player.isCreative()) continue;
      for (var ruin : data.ruins()) {
        if (!ruin.dimension().equals(player.level().dimension())) continue;
        for (var marker : ruin.markers(RuinMarkers.Kind.BOSS)) {
          var definition = RuinRegistry.get(ruin.id()).orElse(null);
          var challenge = definition == null ? null : definition.challenges().get(marker.marker().challenge());
          if (challenge == null || challenge.boss() == null) continue;
          int radius = challenge.boss().radius();
          if (player.distanceToSqr(Vec3.atCenterOf(marker.pos())) > (double) radius * radius) continue;
          var context = HeliodorCompass.context(player).orElse(null);
          if (context == null) continue;
          String groupKey = key(ruin.id(), challenge.id(), context.campaignId());
          Group group = groups.get(groupKey);
          if (group != null) {
            group.lastSeen = now;
            continue;
          }
          var team = RuinProgress.get(server).team(context.campaignId(), context.founder());
          if (team.solved.contains(RuinProgress.key(definition.id(), challenge.id()))) continue;
          if (!RuinRules.ready(RuinChallenges.keys(definition, challenge.requires()), team.solved)) continue;
          spawn(player.serverLevel(), ruin, definition, challenge, marker.pos(), context, now);
        }
      }
    }
    for (var iterator = groups.values().iterator(); iterator.hasNext(); ) {
      Group group = iterator.next();
      ServerLevel level = server.getLevel(group.dimension);
      if (level == null) {
        group.bar.removeAllPlayers();
        iterator.remove();
        continue;
      }
      float health = 0, max = 0;
      boolean alive = false;
      for (UUID id : group.mobs) {
        Entity entity = level.getEntity(id);
        // A guardian spawned where the chunk's entities are still loading is not visible yet: it is still in
        // the fight. One missing where they are loaded is gone (a death leaves the group through onDeath).
        if (entity == null) {
          if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(group.spawn))) alive = true;
        } else if (entity instanceof LivingEntity living && living.isAlive()) {
          alive = true;
          health += living.getHealth();
          max += living.getMaxHealth();
        }
      }
      if (!alive || now - group.lastSeen > ABANDON_TICKS) {
        for (UUID id : group.mobs) {
          Entity entity = level.getEntity(id);
          if (entity != null && entity.isAlive()) entity.discard();
        }
        group.bar.removeAllPlayers();
        iterator.remove();
        continue;
      }
      group.bar.setProgress(max <= 0 ? 0 : Math.clamp(health / max, 0f, 1f));
      Set<ServerPlayer> watching = new HashSet<>();
      for (ServerPlayer player : level.players())
        if (player.distanceToSqr(Vec3.atCenterOf(group.spawn)) <= BAR_RANGE * BAR_RANGE) watching.add(player);
      for (ServerPlayer player : List.copyOf(group.bar.getPlayers()))
        if (!watching.contains(player)) group.bar.removePlayer(player);
      for (ServerPlayer player : watching) group.bar.addPlayer(player);
    }
  }

  /** Raises one team's guardians; on peaceful, solves the challenge instead. */
  static void spawn(ServerLevel level, RuinData.Ruin ruin, RuinDefinitions.Definition definition,
      RuinDefinitions.Challenge challenge, BlockPos marker, HeliodorCompass.Context context, long now) {
    var boss = challenge.boss();
    Component name = Component.translatable(boss.name());
    if (level.getDifficulty() == Difficulty.PEACEFUL) {
      RuinChallenges.solve(level, ruin, definition, challenge.id(), context.campaignId(), context.founder());
      for (ServerPlayer member : context.members())
        member.displayClientMessage(Component.translatable("entrelumen.ruin.boss.yields", name), true);
      return;
    }
    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(boss.entity())).orElse(null);
    if (type == null) return;
    BlockPos spawn = standing(level, marker);
    var bar = new ServerBossEvent(name, BossEvent.BossBarColor.byName(boss.color()), BossEvent.BossBarOverlay.NOTCHED_10);
    var group = new Group(ruin.id(), challenge.id(), context.campaignId(), context.founder(), level.dimension(), spawn,
        boss.radius(), bar, now);
    // Registered first: the join check lets in only mobs of a live group.
    groups(level.getServer()).put(group.key(), group);
    for (int i = 0; i < boss.count(); i++) {
      Entity entity = type.create(level);
      if (!(entity instanceof Mob mob)) {
        if (entity != null) entity.discard();
        LOGGER.warn("Ruin boss {} of {} is not a mob; nothing spawned", boss.entity(), ruin.id());
        groups(level.getServer()).remove(group.key());
        return;
      }
      double angle = boss.count() == 1 ? 0 : i * Math.PI * 2 / boss.count();
      double spread = boss.count() == 1 ? 0 : 2.5;
      mob.moveTo(spawn.getX() + 0.5 + Math.cos(angle) * spread, spawn.getY(), spawn.getZ() + 0.5 + Math.sin(angle) * spread,
          level.getRandom().nextFloat() * 360f, 0f);
      mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);
      mob.setCustomName(name);
      mob.setCustomNameVisible(true);
      mob.setPersistenceRequired();
      for (var attribute : boss.attributes().entrySet()) {
        var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(attribute.getKey())).orElse(null);
        var instance = holder == null ? null : mob.getAttribute(holder);
        if (instance != null) instance.setBaseValue(attribute.getValue());
        else LOGGER.warn("Ruin boss {} has no attribute {}", boss.entity(), attribute.getKey());
      }
      mob.setHealth(mob.getMaxHealth());
      mob.getPersistentData().putString(TAG, group.key());
      group.mobs.add(mob.getUUID());
      if (!level.addFreshEntity(mob)) group.mobs.remove(mob.getUUID());
    }
    if (group.mobs.isEmpty()) {
      groups(level.getServer()).remove(group.key());
      return;
    }
    for (ServerPlayer member : context.members())
      member.displayClientMessage(Component.translatable("entrelumen.ruin.boss.rises", name), true);
  }

  /** The first cell at or above the marker with room for a mob. */
  static BlockPos standing(ServerLevel level, BlockPos marker) {
    BlockPos pos = marker;
    for (int i = 0; i < 8; i++, pos = pos.above())
      if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
          && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return pos;
    return marker.above();
  }

  static void onDeath(LivingDeathEvent event) {
    if (!(event.getEntity().level() instanceof ServerLevel level)) return;
    String key = event.getEntity().getPersistentData().getString(TAG);
    if (key.isEmpty()) return;
    var groups = groups(level.getServer());
    Group group = groups.get(key);
    if (group == null) return;
    group.mobs.remove(event.getEntity().getUUID());
    boolean alive = false;
    for (UUID id : group.mobs) {
      Entity other = level.getEntity(id);
      if (other instanceof LivingEntity living && living.isAlive()) alive = true;
    }
    if (alive) return;
    group.bar.removeAllPlayers();
    groups.remove(key);
    var ruin = RuinData.get(level.getServer()).find(group.ruin).orElse(null);
    var definition = RuinRegistry.get(group.ruin).orElse(null);
    if (ruin != null && definition != null)
      RuinChallenges.solve(level, ruin, definition, group.challenge, group.campaign, group.founder);
  }

  /** Guardians saved with a chunk belong to a finished run: they never come back. */
  static void onJoin(EntityJoinLevelEvent event) {
    if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;
    String key = event.getEntity().getPersistentData().getString(TAG);
    if (key.isEmpty()) return;
    Group group = groups(level.getServer()).get(key);
    if (group == null || !group.mobs.contains(event.getEntity().getUUID())) event.setCanceled(true);
  }

  /** The live group of a team for one boss challenge (tests). */
  @Nullable
  static Group group(MinecraftServer server, ResourceLocation ruin, String challenge, UUID campaign) {
    return groups(server).get(key(ruin, challenge, campaign));
  }
}
