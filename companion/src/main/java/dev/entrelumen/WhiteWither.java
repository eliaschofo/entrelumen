package dev.entrelumen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The White Wither, the Envés's boss (placeholder that stays for v1.0; Elias, 26/9): the vessel Terra
 * built and Bodhi sealed round the sour light. The vanilla Wither's body with its own texture, and its
 * own mind:
 * <ul>
 * <li>it barely flies: it hovers {@link EnvesBalance.Boss#hover} blocks over the arena's floor and
 * slides round its target, keeping its distance;</li>
 * <li>skulls from its three heads, which never break a block ({@link SourSkull});</li>
 * <li>a charge told well in advance: it stops, shakes, marks its path on the floor and hums, then
 * sweeps along that line and is left exposed on the ground, taking more damage, for a few seconds;</li>
 * <li>under half its health it charges more often and calls two lesser echoes;</li>
 * <li>it never breaks blocks and drops no Nether star: its reward is the boss chest that rises by the
 * victory portal when it falls ({@link EnvesBoss}).</li>
 * </ul>
 * Health and damage scale with the attempt's World Tier like every echo (it carries
 * {@link EnvesEchoes.Data} with the boss role).
 */
public class WhiteWither extends WitherBoss {
  public enum Phase {INTRO, FIGHT, TELEGRAPH, CHARGE, EXPOSED}

  private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(WhiteWither.class, EntityDataSerializers.INT);
  static final DustParticleOptions WARNING = new DustParticleOptions(new Vector3f(0.96f, 0.84f, 0.48f), 1.6f);
  public static final int INTRO_TICKS = 60, CHARGE_MAX = 26;
  public static final double CHARGE_SPEED = 1.05, CHARGE_REACH = 1.4, ARENA_RADIUS = 24, KEEP_AWAY = 9;

  private final ServerBossEvent bar = (ServerBossEvent) new ServerBossEvent(Component.translatable("entity.entrelumen.white_wither"),
      BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
  /** The arena's centre (feet level of its floor); the boss never strays far from it. */
  private BlockPos home = BlockPos.ZERO;
  private int phaseTicks, cooldown = 120, skullTicks = 40, orbit = 1;
  private boolean called;
  private Vec3 chargeFrom = Vec3.ZERO, chargeDir = new Vec3(1, 0, 0);
  private double chargeLength;
  private final Set<UUID> struck = new HashSet<>();

  public WhiteWither(EntityType<? extends WitherBoss> type, Level level) {
    super(type, level);
    this.moveControl = new MoveControl(this);
    this.setNoGravity(true);
    this.xpReward = 0;
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 600.0).add(Attributes.MOVEMENT_SPEED, 0.3)
        .add(Attributes.FLYING_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ARMOR, 8.0)
        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_PHASE, Phase.INTRO.ordinal());
  }

  public Phase phase() {
    int i = entityData.get(DATA_PHASE);
    return Phase.values()[Math.clamp(i, 0, Phase.values().length - 1)];
  }

  void phase(Phase phase) {
    entityData.set(DATA_PHASE, phase.ordinal());
    phaseTicks = 0;
  }

  public void home(BlockPos center) {
    this.home = center.immutable();
  }

  public BlockPos home() {
    return home;
  }

  /** The line of the coming charge (for the tests): start, direction and length. */
  public Vec3 chargeFrom() {
    return chargeFrom;
  }

  public Vec3 chargeDir() {
    return chargeDir;
  }

  public double chargeLength() {
    return chargeLength;
  }

  // ---- Wither overrides ---------------------------------------------------------------------

  @Override
  protected void registerGoals() {
    targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
    targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, false));
  }

  /** Never the vanilla wither's power armour, so arrows always count. */
  @Override
  public boolean isPowered() {
    return false;
  }

  @Override
  public boolean shouldDespawnInPeaceful() {
    return false;
  }

  @Override
  public void startSeenByPlayer(ServerPlayer player) {
    bar.addPlayer(player);
  }

  @Override
  public void stopSeenByPlayer(ServerPlayer player) {
    bar.removePlayer(player);
  }

  @Override
  protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {}

  @Override
  public boolean hurt(DamageSource source, float amount) {
    if (phase() == Phase.INTRO && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
    if (phase() == Phase.EXPOSED) amount *= (float) (1 + EnvesContentConfig.balance().boss().exposedBonus());
    return super.hurt(source, amount);
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putLong("home", home.asLong());
    tag.putBoolean("called", called);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    home = BlockPos.of(tag.getLong("home"));
    called = tag.getBoolean("called");
    phase(Phase.FIGHT);
    setNoGravity(true);
  }

  // ---- The fight ----------------------------------------------------------------------------

  /** Replaces the Wither's own: no block is ever broken, no invulnerable birth, no free flight. */
  @Override
  protected void customServerAiStep() {
    phaseTicks++;
    bar.setProgress(getHealth() / getMaxHealth());
    var boss = EnvesContentConfig.balance().boss();
    LivingEntity target = getTarget();
    if (target != null && (!target.isAlive() || target.distanceToSqr(this) > 48 * 48)) {
      setTarget(null);
      target = null;
    }
    switch (phase()) {
      case INTRO -> {
        hoverTo(null, boss.hover() * Math.min(1, phaseTicks / (double) INTRO_TICKS), 0.05);
        if (level() instanceof ServerLevel server && phaseTicks % 4 == 0)
          server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 6, 0.8, 1.2, 0.8, 0.05);
        if (phaseTicks >= INTRO_TICKS) phase(Phase.FIGHT);
      }
      case FIGHT -> {
        cooldown--;
        Vec3 goal = null;
        if (target != null) {
          Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
          if (away.lengthSqr() < 1e-3) away = new Vec3(1, 0, 0);
          away = away.normalize();
          Vec3 around = new Vec3(-away.z, 0, away.x).scale(orbit * 0.35);
          goal = target.position().add(away.add(around).normalize().scale(KEEP_AWAY));
          if (random.nextInt(160) == 0) orbit = -orbit;
          lookAt(target, 30, 30);
          setAlternativeTarget(1, target.getId());
          setAlternativeTarget(2, nearestOther(target).getId());
          if (--skullTicks <= 0) {
            volley(target);
            skullTicks = belowHalf() ? 26 : 40;
          }
          int wait = belowHalf() ? (int) (boss.chargeCooldown() * 0.7) : boss.chargeCooldown();
          double distance = distanceTo(target);
          if (cooldown <= 0 && distance > 5 && distance < 26 && hasLineOfSight(target)) {
            telegraph(target);
            cooldown = wait;
          }
        }
        hoverTo(goal, boss.hover(), 0.16);
        if (belowHalf() && !called) callEchoes(target);
      }
      case TELEGRAPH -> {
        setDeltaMovement(Vec3.ZERO);
        hoverTo(null, 1.2, 0.08);
        if (level() instanceof ServerLevel server) {
          if (phaseTicks % 3 == 0) markPath(server);
          server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 2.2, getZ(), 2, 0.5, 0.6, 0.5, 0.02);
        }
        setPos(getX() + (random.nextDouble() - 0.5) * 0.06, getY(), getZ() + (random.nextDouble() - 0.5) * 0.06);
        if (phaseTicks >= boss.telegraphTicks()) {
          struck.clear();
          moveTo(chargeFrom.x, getY(), chargeFrom.z, getYRot(), getXRot());
          phase(Phase.CHARGE);
          playSound(SoundEvents.WITHER_SHOOT, 2f, 0.5f);
        }
      }
      case CHARGE -> {
        Vec3 step = chargeDir.scale(CHARGE_SPEED);
        Vec3 next = position().add(step);
        boolean blocked = !level().noCollision(this, getBoundingBox().move(step));
        boolean done = phaseTicks * CHARGE_SPEED >= chargeLength || phaseTicks > CHARGE_MAX;
        if (blocked || done) {
          setDeltaMovement(Vec3.ZERO);
          phase(Phase.EXPOSED);
          playSound(SoundEvents.ANVIL_LAND, 1.5f, 0.5f);
          if (level() instanceof ServerLevel server)
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.5, 0.5, 0.5, 0);
          break;
        }
        setPos(next.x, getY(), next.z);
        setDeltaMovement(Vec3.ZERO);
        strike();
      }
      case EXPOSED -> {
        hoverTo(null, 0.3, 0.1);
        if (level() instanceof ServerLevel server && phaseTicks % 5 == 0)
          server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.5, getZ(), 6, 0.6, 0.8, 0.6, 0.02);
        if (phaseTicks >= boss.exposedTicks()) {
          phase(Phase.FIGHT);
          playSound(SoundEvents.WITHER_AMBIENT, 1.2f, 0.9f);
        }
      }
    }
  }

  boolean belowHalf() {
    return getHealth() <= getMaxHealth() / 2;
  }

  private LivingEntity nearestOther(LivingEntity target) {
    LivingEntity best = target;
    double bestDistance = Double.MAX_VALUE;
    for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(24), p -> p != target && p.isAlive()
        && !p.isSpectator() && !p.isCreative())) {
      double d = player.distanceToSqr(this);
      if (d < bestDistance) {
        bestDistance = d;
        best = player;
      }
    }
    return best;
  }

  /** Glides toward {@code goal} (or stays), at {@code height} blocks over the arena's floor, never far from home. */
  void hoverTo(Vec3 goal, double height, double speed) {
    double floorY = home.equals(BlockPos.ZERO) ? getY() - height : home.getY();
    Vec3 here = position();
    Vec3 want = goal == null ? here : goal;
    Vec3 fromHome = new Vec3(want.x - (home.getX() + 0.5), 0, want.z - (home.getZ() + 0.5));
    if (!home.equals(BlockPos.ZERO) && fromHome.length() > ARENA_RADIUS)
      want = new Vec3(home.getX() + 0.5, want.y, home.getZ() + 0.5).add(fromHome.normalize().scale(ARENA_RADIUS));
    Vec3 horizontal = new Vec3(want.x - here.x, 0, want.z - here.z);
    if (horizontal.length() > speed) horizontal = horizontal.normalize().scale(speed);
    double dy = Mth.clamp(floorY + height - here.y, -0.12, 0.12);
    setDeltaMovement(horizontal.x, dy, horizontal.z);
  }

  /** Three skulls, one from each head: the middle one at the target, the others near it. */
  void volley(LivingEntity target) {
    if (!(level() instanceof ServerLevel server)) return;
    playSound(SoundEvents.WITHER_SHOOT, 1f, 1f);
    for (int head = 0; head < 3; head++) {
      double angle = (yBodyRot + (head == 0 ? 0 : 180 * (head - 1))) * Math.PI / 180;
      double hx = head == 0 ? getX() : getX() + Math.cos(angle) * 1.3;
      double hz = head == 0 ? getZ() : getZ() + Math.sin(angle) * 1.3;
      double hy = getY() + (head == 0 ? 3.0 : 2.2);
      Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
      if (head > 0) aim = aim.add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
      SourSkull skull = new SourSkull(server, this, new Vec3(hx, hy, hz), aim.subtract(hx, hy, hz));
      server.addFreshEntity(skull);
    }
  }

  /** Picks the charge's line, clipped where a wall or pillar would stop it, and starts the warning. */
  void telegraph(LivingEntity target) {
    Vec3 from = position();
    Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
    if (dir.lengthSqr() < 1e-3) return;
    dir = dir.normalize();
    double reach = Math.min(dir.length() * (distanceTo(target) + 6), 30);
    double length = 0;
    AABB box = getBoundingBox();
    double floorY = home.equals(BlockPos.ZERO) ? getY() - 1.2 : home.getY() + 1.2;
    box = box.move(0, floorY - getY(), 0);
    while (length + 1 <= reach && level().noCollision(this, box.move(dir.scale(length + 1)))) length += 1;
    chargeFrom = new Vec3(getX(), floorY, getZ());
    chargeDir = dir;
    chargeLength = Math.max(3, length);
    phase(Phase.TELEGRAPH);
    playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.7f);
    if (level() instanceof ServerLevel server)
      for (ServerPlayer player : server.getPlayers(p -> p.distanceToSqr(this) < 40 * 40))
        player.displayClientMessage(Component.translatable("entrelumen.enves.boss.charge"), true);
  }

  /** The warning on the floor: the line the charge will sweep, gold dust at every block. */
  void markPath(ServerLevel server) {
    double floor = home.equals(BlockPos.ZERO) ? getY() - 1.2 : home.getY();
    for (double t = 1; t <= chargeLength; t += 0.8) {
      Vec3 p = chargeFrom.add(chargeDir.scale(t));
      server.sendParticles(WARNING, p.x, floor + 0.15, p.z, 1, 0.25, 0, 0.25, 0);
    }
  }

  /** Every player the charge passes through is struck once: heavy damage, thrown aside. */
  void strike() {
    var boss = EnvesContentConfig.balance().boss();
    for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(CHARGE_REACH, 0.5, CHARGE_REACH),
        p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
      if (!struck.add(player.getUUID())) continue;
      if (player.hurt(damageSources().mobAttack(this), (float) boss.chargeDamage())) {
        Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
        if (side.dot(player.position().subtract(position())) < 0) side = side.scale(-1);
        player.push(side.x * 1.2 + chargeDir.x * 0.6, 0.5, side.z * 1.2 + chargeDir.z * 0.6);
        player.hurtMarked = true;
      }
    }
  }

  /** Under half its health, once: two lesser echoes at the arena's edges. */
  void callEchoes(LivingEntity target) {
    called = true;
    EnvesBoss.callEchoes(this, target instanceof ServerPlayer player ? player : null);
  }

  @Override
  public void die(DamageSource source) {
    bar.setProgress(0);
    bar.removeAllPlayers();
    super.die(source);
  }

  @Override
  public void remove(RemovalReason reason) {
    bar.removeAllPlayers();
    super.remove(reason);
  }

  /** For the tests: the players the running charge has already struck. */
  public List<UUID> struck() {
    return new ArrayList<>(struck);
  }
}
