package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Altar of Time. While it burns amethyst shards, hostile mobs in the 33×33×33 cube centred on
 * it move and strike at a third of their strength, projectiles from hostile sources fly the same
 * path at a third of the speed, and mobs a player kills inside drop three times their loot and
 * experience. Bosses, their projectiles and players' projectiles are not affected. The work is done
 * by {@link AltarEffects} and {@link AltarLootModifier}.
 */
public final class TimeAltarEntity extends AreaAltarEntity {
  public TimeAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.TIME_ENTITY.get(), pos, state, AltarType.TIME);
  }

  @Override
  protected String key() {
    return "time";
  }

  @Override
  protected int defaultRadius() {
    return AltarEffectRules.TIME_RADIUS;
  }

  @Override
  protected int defaultHalfHeight() {
    return AltarEffectRules.TIME_HALF_HEIGHT;
  }

  @Override
  protected ParticleOptions particle() {
    return ParticleTypes.PORTAL;
  }
}
