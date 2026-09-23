package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Ark diagnostics with an empty hand; material repairs with a damaged tool. */
public final class EngineeringModuleBlock extends Block {
  public EngineeringModuleBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
      BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (hand != InteractionHand.MAIN_HAND || stack.isEmpty() || !stack.isDamageableItem())
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer)
      EngineeringWorkshop.repair(serverPlayer, pos, hand);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer)
      EngineeringDiagnostics.inspect(serverPlayer, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
