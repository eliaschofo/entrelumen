package dev.entrelumen;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block of the Altars of Peace, Growth and Time: fuel in the main hand feeds it, an empty hand
 * reports and shows the area, crouching with an empty hand pauses or resumes.
 */
public final class AreaAltarBlock extends AltarBlock {
  private final Supplier<? extends BlockEntityType<? extends AreaAltarEntity>> entityType;
  private final BiFunction<BlockPos, BlockState, ? extends AreaAltarEntity> factory;

  public AreaAltarBlock(Properties properties, Supplier<? extends BlockEntityType<? extends AreaAltarEntity>> entityType,
      BiFunction<BlockPos, BlockState, ? extends AreaAltarEntity> factory) {
    super(properties, Altars.PEDESTAL_SHAPE);
    this.entityType = entityType;
    this.factory = factory;
  }

  @Override
  protected BlockEntityType<? extends AltarBlockEntity> entityType() {
    return entityType.get();
  }

  @Override
  public AreaAltarEntity newBlockEntity(BlockPos pos, BlockState state) {
    return factory.apply(pos, state);
  }
}
