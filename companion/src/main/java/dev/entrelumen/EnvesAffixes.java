package dev.entrelumen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What each elite affix does ({@link EnvesAffix}): attribute modifiers set once when the echo is
 * made, and the rest on its hits and ticks. Veloz and Blindado are modifiers; Vampírico heals on
 * damage dealt; Ardiente is fire-proof and lights everyone near it every second; Perforante keeps
 * only half of the armor's reduction of its blows; Espectral picks a spot behind its target, which
 * hisses for a moment, and comes back there.
 */
public final class EnvesAffixes {
  private EnvesAffixes() {}

  static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", "enves_affix_" + path);
  }

  /** Per-echo timers of the spectral blink. */
  static final class State {
    long nextBlink = -1;
    Vec3 blinkTo;
    long blinkAt = -1;
  }

  private static final Map<Integer, State> STATES = new HashMap<>();

  static void forget(Mob mob) {
    STATES.remove(mob.getId());
  }

  /** The permanent part: modifiers and fire immunity. */
  static void apply(Mob mob, List<EnvesAffix> affixes) {
    for (EnvesAffix affix : affixes) {
      switch (affix) {
        case SWIFT -> {
          add(mob, Attributes.MOVEMENT_SPEED, "veloz", EnvesAffix.SWIFT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
          add(mob, Attributes.FLYING_SPEED, "veloz_flying", EnvesAffix.SWIFT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
        case ARMORED -> {
          add(mob, Attributes.ARMOR, "blindado", EnvesAffix.ARMORED_ARMOR, AttributeModifier.Operation.ADD_VALUE);
          add(mob, Attributes.ARMOR_TOUGHNESS, "blindado_toughness", EnvesAffix.ARMORED_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
          add(mob, Attributes.KNOCKBACK_RESISTANCE, "blindado_knockback", EnvesAffix.ARMORED_KNOCKBACK,
              AttributeModifier.Operation.ADD_VALUE);
        }
        case BURNING -> mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, true, false));
        default -> {}
      }
    }
  }

  private static void add(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
      String name, double amount, AttributeModifier.Operation operation) {
    var instance = mob.getAttribute(attribute);
    if (instance != null) instance.addOrUpdateTransientModifier(new AttributeModifier(id(name), amount, operation));
  }

  /** The echo lands a blow: Perforante thins the armor's part, Ardiente sets the victim alight. */
  static void onHit(LivingIncomingDamageEvent event, Mob echo, EnvesEchoes.Data data) {
    if (data.has(EnvesAffix.PIERCING))
      event.getContainer().addModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * EnvesAffix.PIERCING_ARMOR_KEPT);
    if (data.has(EnvesAffix.BURNING) && event.getSource().getDirectEntity() == echo)
      event.getEntity().igniteForTicks(EnvesAffix.BURNING_FIRE_TICKS);
  }

  /** After a blow landed: Vampírico heals a share of it. */
  static void afterHit(LivingDamageEvent.Post event, Mob echo, EnvesEchoes.Data data) {
    if (!data.has(EnvesAffix.VAMPIRIC) || event.getNewDamage() <= 0 || !echo.isAlive()) return;
    echo.heal(event.getNewDamage() * EnvesAffix.VAMPIRIC_LEECH);
    if (echo.level() instanceof ServerLevel level)
      level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, echo.getX(), echo.getY() + echo.getBbHeight() * 0.7, echo.getZ(), 4,
          0.3, 0.3, 0.3, 0.05);
  }

  /** Every tick of a live echo: the burning aura and the spectral blink. */
  static void tick(ServerLevel level, Mob mob, EnvesEchoes.Data data, long now) {
    if (data.affixes().isEmpty()) return;
    if (data.has(EnvesAffix.BURNING)) burn(level, mob, now);
    if (data.has(EnvesAffix.SPECTRAL)) blink(level, mob, now);
  }

  private static void burn(ServerLevel level, Mob mob, long now) {
    if ((now + mob.getId()) % 4 == 0)
      level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 2,
          mob.getBbWidth() * 0.6, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.6, 0.01);
    if ((now + mob.getId()) % 20 != 0) return;
    double r = EnvesAffix.BURNING_RADIUS;
    for (Player player : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(r, 1.5, r),
        p -> !p.isCreative() && !p.isSpectator() && p.distanceToSqr(mob) <= r * r)) {
      if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) continue;
      player.igniteForTicks(EnvesAffix.BURNING_FIRE_TICKS);
    }
  }

  private static void blink(ServerLevel level, Mob mob, long now) {
    State state = STATES.computeIfAbsent(mob.getId(), id -> new State());
    if (state.nextBlink < 0) state.nextBlink = now + cooldown(mob);
    LivingEntity target = mob.getTarget();
    if (state.blinkAt >= 0) {
      if (now < state.blinkAt) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, state.blinkTo.x, state.blinkTo.y + 1, state.blinkTo.z, 3, 0.3, 0.6, 0.3, 0.02);
        return;
      }
      Vec3 to = state.blinkTo;
      state.blinkAt = -1;
      state.blinkTo = null;
      if (to != null && free(level, mob, to)) {
        level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        mob.teleportTo(to.x, to.y, to.z);
        if (target != null) mob.lookAt(target, 180f, 180f);
        level.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9f, 1.3f);
      }
      state.nextBlink = now + cooldown(mob);
      return;
    }
    if (now < state.nextBlink || target == null || !target.isAlive()) return;
    double distance = mob.distanceTo(target);
    if (distance < 2.5 || distance > 20) return;
    Vec3 spot = behind(level, mob, target);
    if (spot == null) {
      state.nextBlink = now + 20;
      return;
    }
    state.blinkTo = spot;
    state.blinkAt = now + EnvesAffix.SPECTRAL_WARNING;
    // The tell: a hiss and a shimmer where it will land, behind the target.
    level.playSound(null, BlockPos.containing(spot), SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.7f, 1.6f);
  }

  private static int cooldown(Mob mob) {
    return EnvesAffix.SPECTRAL_MIN_COOLDOWN + mob.getRandom().nextInt(EnvesAffix.SPECTRAL_MAX_COOLDOWN - EnvesAffix.SPECTRAL_MIN_COOLDOWN + 1);
  }

  /** A free spot about two blocks behind the target, at its feet's height (the same floor). */
  static Vec3 behind(ServerLevel level, Mob mob, LivingEntity target) {
    Vec3 look = target.getLookAngle();
    Vec3 back = new Vec3(look.x, 0, look.z);
    if (back.lengthSqr() < 1e-4) back = mob.position().subtract(target.position()).multiply(1, 0, 1);
    if (back.lengthSqr() < 1e-4) back = new Vec3(1, 0, 0);
    back = back.normalize();
    Vec3 side = new Vec3(-back.z, 0, back.x);
    for (double distance : new double[] {2.2, 1.6, 2.8})
      for (double lateral : new double[] {0, 0.9, -0.9}) {
        Vec3 spot = target.position().subtract(back.scale(distance)).add(side.scale(lateral));
        spot = new Vec3(spot.x, target.getY(), spot.z);
        if (free(level, mob, spot)) return spot;
      }
    return null;
  }

  static boolean free(ServerLevel level, Mob mob, Vec3 spot) {
    AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(spot);
    if (!level.noCollision(mob, box)) return false;
    BlockPos below = BlockPos.containing(spot.x, spot.y - 0.2, spot.z);
    return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
        && EnvesGeometry.depthAt(Mth.floor(spot.y)) == EnvesGeometry.depthAt(mob.getBlockY());
  }
}
