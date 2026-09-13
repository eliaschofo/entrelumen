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

public final class ArkControllerBlock extends Block {
  public ArkControllerBlock(Properties properties) {
    super(properties);
  }

  /** Keep the explicit crouch gesture usable with supplies in the offhand. */
  public static void allowEmptyHandDeposit(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE
        || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive()
        || !event.getEntity().getMainHandItem().isEmpty()) return;
    if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ArkControllerBlock)
      event.setUseBlock(TriState.TRUE);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) {
      if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()) {
        ArkActions.deposit(serverPlayer, CampaignActions.campaignId(serverPlayer), pos,
            Entrelumen.current(serverPlayer).arkPhase);
      }
      ArkActions.inspect(serverPlayer, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
