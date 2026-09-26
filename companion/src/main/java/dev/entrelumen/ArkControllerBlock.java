package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Ark's keel and sun: it marks where the Ark is built, the modules need it, and a crouched use
 * with an empty hand activates the Ark once the checklist is complete. A plain use opens the Ark's
 * status screen.
 */
public final class ArkControllerBlock extends Block {
  public ArkControllerBlock(Properties properties) {
    super(properties);
  }

  /** Keep the crouched activation usable with supplies in the offhand. */
  public static void allowEmptyHandDeposit(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE
        || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive()
        || !event.getEntity().getMainHandItem().isEmpty()) return;
    if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ArkControllerBlock)
      event.setUseBlock(TriState.TRUE);
  }

  @Override
  public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (level instanceof ServerLevel server) ArkState.onPlaced(server, pos, ArkMultiblock.CONTROLLER, placer);
  }

  @Override
  protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    super.onPlace(state, level, pos, oldState, movedByPiston);
    if (level instanceof ServerLevel server && !state.is(oldState.getBlock())) ArkState.refreshAt(server, pos);
  }

  @Override
  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    super.onRemove(state, level, pos, newState, movedByPiston);
    if (level instanceof ServerLevel server && !state.is(newState.getBlock())) ArkState.onRemoved(server, pos);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) {
      if (player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()) ArkActions.activate(serverPlayer, pos);
      else ArkFieldJournals.inspect(serverPlayer, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
