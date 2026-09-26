package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fuel in the main hand feeds it. Empty hand: preview, then use again to start; while working,
 * pause or resume. Crouching with an empty hand picks the next setting during the preview (four
 * flat sizes, then the repair) and otherwise reports its status. It asks for no materials.
 */
public final class TerraformAltarBlock extends AltarBlock {
  public TerraformAltarBlock(Properties properties) {
    super(properties, Altars.TERRAFORM_SHAPE);
  }

  @Override
  protected BlockEntityType<? extends AltarBlockEntity> entityType() {
    return Altars.TERRAFORM_ENTITY.get();
  }

  @Override
  public TerraformAltarEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TerraformAltarEntity(pos, state);
  }
}
