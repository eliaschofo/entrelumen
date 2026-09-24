package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bottles o' Enchanting in the main hand feed it. Any other use opens the coffer, like a chest, so
 * a worn tool can be put in straight from the hand; crouching with an empty hand reports.
 */
public final class ReposeAltarBlock extends AltarBlock {
  public ReposeAltarBlock(Properties properties) {
    super(properties, Altars.PEDESTAL_SHAPE);
  }

  @Override
  protected BlockEntityType<? extends AltarBlockEntity> entityType() {
    return Altars.REPOSE_ENTITY.get();
  }

  @Override
  public ReposeAltarEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ReposeAltarEntity(pos, state);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    if (hand == InteractionHand.MAIN_HAND && !stack.is(AltarType.REPOSE.fuel()) && !player.isSecondaryUseActive()
        && level.getBlockEntity(pos) instanceof ReposeAltarEntity altar) {
      if (player instanceof ServerPlayer serverPlayer && reachable(serverPlayer, pos)) altar.use(serverPlayer);
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return super.useItemOn(stack, state, level, pos, player, hand, hit);
  }
}
