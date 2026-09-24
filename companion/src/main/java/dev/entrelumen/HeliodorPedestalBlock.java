package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The ruin's pedestal: every player may take exactly one Heliodor compass from it, once per world.
 * Unbreakable, so no player can remove the only source of compasses for later arrivals.
 */
public final class HeliodorPedestalBlock extends Block {
  public HeliodorPedestalBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) HeliodorCompass.claim(serverPlayer);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
