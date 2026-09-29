package dev.entrelumen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Sour Light's skull: a wither skull that bursts without breaking anything. Its damage is the
 * boss's {@link EnvesBalance.Boss#skullDamage}, scaled by the attempt like every echo's; it withers for
 * four seconds, whatever the difficulty.
 */
public class SourSkull extends WitherSkull {
  public static final int WITHER_TICKS = 80;

  public SourSkull(EntityType<? extends WitherSkull> type, Level level) {
    super(type, level);
  }

  public SourSkull(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 direction) {
    this(EnvesContent.SOUR_SKULL.get(), level);
    setOwner(owner);
    moveTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
    reapplyPosition();
    setDeltaMovement(direction.normalize().scale(accelerationPower));
    hasImpulse = true;
  }

  @Override
  protected void onHitEntity(EntityHitResult result) {
    if (!(level() instanceof ServerLevel)) return;
    if (!(getOwner() instanceof LivingEntity owner) || result.getEntity() == owner) return;
    boolean hit = result.getEntity().hurt(damageSources().witherSkull(this, owner),
        (float) EnvesContentConfig.balance().boss().skullDamage());
    if (hit && result.getEntity() instanceof LivingEntity living)
      living.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0), getEffectSource());
  }

  /**
   * Hits like a wither skull and bursts like one, but breaks nothing: no block reacts to it (not even
   * the ones a projectile may break) and the burst is {@link Level.ExplosionInteraction#NONE}.
   */
  @Override
  protected void onHit(HitResult result) {
    if (result.getType() == HitResult.Type.ENTITY) onHitEntity((EntityHitResult) result);
    if (!level().isClientSide) {
      level().explode(this, getX(), getY(), getZ(), 1.0F, false, Level.ExplosionInteraction.NONE);
      discard();
    }
  }
}
