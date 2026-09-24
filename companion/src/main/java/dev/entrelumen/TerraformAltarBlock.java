package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fuel in the main hand feeds it. Empty hand: preview, then use again to start; while working,
 * pause or resume. Crouching with an empty hand changes the size during the preview and otherwise
 * opens the buffer. A block item in the main hand is added to the buffer as fill material.
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

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    if (hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof BlockItem && !player.isSecondaryUseActive()
        && !stack.is(AltarType.TERRAFORM.fuel()) && level.getBlockEntity(pos) instanceof TerraformAltarEntity altar) {
      if (player instanceof ServerPlayer serverPlayer && reachable(serverPlayer, pos)) altar.supply(serverPlayer, stack);
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return super.useItemOn(stack, state, level, pos, player, hand, hit);
  }
}
