package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Logistics module. Like the other five it turns its global effect on in the team's Ark; it keeps
 * a block entity only so an old depot (Ark v1) loads and hands its stock back ({@link LogisticsStock}).
 */
public final class LogisticsModuleBlock extends ArkFieldJournalBlock implements EntityBlock {
  public LogisticsModuleBlock(Properties properties) {
    super(ArkFieldJournals.Kind.LOGISTICS, properties);
  }

  @Override
  public LogisticsStock newBlockEntity(BlockPos pos, BlockState state) {
    return new LogisticsStock(pos, state);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    if (level.isClientSide || type != Entrelumen.LOGISTICS_STOCK.get()) return null;
    return (tickLevel, pos, tickState, entity) -> LogisticsStock.tick(tickLevel, pos, tickState, (LogisticsStock) entity);
  }

  @Override
  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LogisticsStock stock)
      stock.returnStock(level, pos, false);
    super.onRemove(state, level, pos, newState, movedByPiston);
  }
}
