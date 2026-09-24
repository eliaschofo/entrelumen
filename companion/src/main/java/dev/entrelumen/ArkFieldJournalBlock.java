package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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

  /** Preserve the deliberate checkout gesture without overriding protection decisions. */
  public static void allowCrouchedBedUse(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE
        || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive()
        || !event.getEntity().getMainHandItem().is(ItemTags.BEDS)
        || !event.getLevel().hasChunkAt(event.getPos())) return;
    if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ArkFieldJournalBlock block
        && block.kind == ArkFieldJournals.Kind.HABITATION)
      event.setUseBlock(TriState.TRUE);
  }

  @Override
  public java.util.Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(
      BlockState state, EntityType<?> type, LevelReader level, BlockPos pos, float orientation) {
    return ArkHabitation.respawn(state, type, level, pos, orientation);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
      BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (kind == ArkFieldJournals.Kind.HABITATION && hand == InteractionHand.MAIN_HAND
        && stack.is(ItemTags.BEDS)) {
      if (player instanceof ServerPlayer serverPlayer)
        ArkHabitation.use(serverPlayer, pos, hand, player.isSecondaryUseActive());
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    if (kind == ArkFieldJournals.Kind.NATURE && hand == InteractionHand.MAIN_HAND
        && (stack.is(Items.COMPASS) || stack.is(Items.BONE_MEAL))) {
      if (player instanceof ServerPlayer serverPlayer) NatureRestoration.use(serverPlayer, pos, hand);
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    if (kind == ArkFieldJournals.Kind.EXPLORATION && hand == InteractionHand.MAIN_HAND
        && stack.is(Items.FILLED_MAP)) {
      if (player instanceof ServerPlayer serverPlayer) ArkCharts.compile(serverPlayer, pos, hand);
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    if (kind != ArkFieldJournals.Kind.ARCANE || hand != InteractionHand.MAIN_HAND
        || !ArcaneRestoration.eligible(stack))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer) ArcaneRestoration.restore(serverPlayer, pos, hand);
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
