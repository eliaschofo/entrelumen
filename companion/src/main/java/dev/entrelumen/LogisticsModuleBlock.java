package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Local view and deliberate partial deposits into the existing Ark ledger. */
public final class LogisticsModuleBlock extends Block {
  public LogisticsModuleBlock(Properties properties) {
    super(properties);
  }

  /** Vanilla sneak handling must still allow the empty-main-hand block gesture. */
  public static void allowCrouchedUse(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE
        || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive()
        || !event.getEntity().getMainHandItem().isEmpty()) return;
    if (event.getLevel().hasChunkAt(event.getPos())
        && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof LogisticsModuleBlock)
      event.setUseBlock(TriState.TRUE);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer) {
      if (player.isSecondaryUseActive())
        ArkActions.depositFromModule(serverPlayer, CampaignActions.campaignId(serverPlayer), pos,
            EngineeringDiagnostics.currentReadOnly(serverPlayer).arkPhase);
      LogisticsModuleActions.inspect(serverPlayer, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
