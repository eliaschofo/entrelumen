package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Altar of Peace. While it burns candles, no hostile mob spawns naturally in the 129×129×129
 * cube centred on it: the area of Torchmaster's Mega Torch, which this altar replaces. Spawners,
 * trial spawners, spawn eggs, summons, conversions, raids, patrols, sieges and bosses are not
 * affected. The work is done by {@link AltarEffects#onSpawnPlacement} and
 * {@link AltarEffects#onFinalizeSpawn}, which ask the {@link AltarRegistry} in constant time.
 */
public final class PeaceAltarEntity extends AreaAltarEntity {
  public PeaceAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.PEACE_ENTITY.get(), pos, state, AltarType.PEACE);
  }

  @Override
  protected String key() {
    return "peace";
  }

  @Override
  protected int defaultRadius() {
    return AltarEffectRules.PEACE_RADIUS;
  }

  @Override
  protected int defaultHalfHeight() {
    return AltarEffectRules.PEACE_HALF_HEIGHT;
  }

  @Override
  protected ParticleOptions particle() {
    return ParticleTypes.END_ROD;
  }
}
