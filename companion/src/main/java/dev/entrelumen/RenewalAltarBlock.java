package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Fertilizer in the main hand feeds it; an empty hand reports; crouching pauses or resumes. */
public final class RenewalAltarBlock extends AltarBlock {
  public RenewalAltarBlock(Properties properties) {
    super(properties, Altars.RENEWAL_SHAPE);
  }

  @Override
  protected BlockEntityType<? extends AltarBlockEntity> entityType() {
    return Altars.RENEWAL_ENTITY.get();
  }

  @Override
  public RenewalAltarEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new RenewalAltarEntity(pos, state);
  }
}
