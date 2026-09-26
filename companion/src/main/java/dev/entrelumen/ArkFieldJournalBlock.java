package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One of the Ark's six modules (Ark v2). Placed in its slot of the team's Ark it turns its global
 * effect on; it reports its own placement and removal to {@link ArkState}, whatever the cause. A use
 * with an empty hand opens the Ark's status screen.
 */
public class ArkFieldJournalBlock extends Block {
  private final ArkFieldJournals.Kind kind;

  public ArkFieldJournalBlock(ArkFieldJournals.Kind kind, Properties properties) {
    super(properties);
    this.kind = kind;
  }

  public ArkFieldJournals.Kind kind() {
    return kind;
  }

  @Override
  public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (level instanceof ServerLevel server) ArkState.onPlaced(server, pos, kind.module(), placer);
  }

  @Override
  protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    super.onPlace(state, level, pos, oldState, movedByPiston);
    // Placed without a player (commands, structures, pistons): only an existing Ark takes it in.
    if (level instanceof ServerLevel server && !state.is(oldState.getBlock())) ArkState.refreshAt(server, pos);
  }

  @Override
  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    super.onRemove(state, level, pos, newState, movedByPiston);
    if (level instanceof ServerLevel server && !state.is(newState.getBlock())) ArkState.onRemoved(server, pos);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
      BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (kind == ArkFieldJournals.Kind.NATURE && hand == InteractionHand.MAIN_HAND
        && (stack.is(Items.COMPASS) || stack.is(Items.BONE_MEAL))) {
      if (player instanceof ServerPlayer serverPlayer) NatureRestoration.use(serverPlayer, pos, hand);
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer) ArkFieldJournals.inspect(serverPlayer, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
