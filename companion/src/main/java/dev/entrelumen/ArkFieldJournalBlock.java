package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Four installed disciplines share one book view and one local batch interaction. */
public final class ArkFieldJournalBlock extends Block {
  private final ArkFieldJournals.Kind kind;

  public ArkFieldJournalBlock(ArkFieldJournals.Kind kind, Properties properties) {
    super(properties);
    this.kind = kind;
  }

  public ArkFieldJournals.Kind kind() {
    return kind;
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
      BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (kind != ArkFieldJournals.Kind.ARCANE || hand != InteractionHand.MAIN_HAND
        || !stack.is(Items.ENCHANTED_BOOK))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer) ArcaneLibrary.separate(serverPlayer, pos, hand);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer) {
      if (player.isSecondaryUseActive()) ArkFieldJournals.deposit(serverPlayer, pos);
      else ArkFieldJournals.inspect(serverPlayer, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
