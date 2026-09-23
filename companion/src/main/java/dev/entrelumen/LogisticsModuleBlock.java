package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Physical stock, personal expedition kits and the existing Ark ledger gesture. */
public final class LogisticsModuleBlock extends Block implements EntityBlock {
  public LogisticsModuleBlock(Properties properties) {
    super(properties);
  }

  /** Keep the empty-hand ledger and crouched Matrix gesture on the native block path. */
  public static void allowCrouchedUse(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE
        || event.getHand() != InteractionHand.MAIN_HAND
        || !event.getEntity().isSecondaryUseActive()
        || (!event.getEntity().getMainHandItem().isEmpty()
            && !isMatrix(event.getEntity().getMainHandItem()))) return;
    if (event.getLevel().hasChunkAt(event.getPos())) {
      var block = event.getLevel().getBlockState(event.getPos()).getBlock();
      if (block instanceof LogisticsModuleBlock
          || (event.getEntity().getMainHandItem().isEmpty()
              && block instanceof ArkFieldJournalBlock))
        event.setUseBlock(TriState.TRUE);
    }
  }

  private static boolean isMatrix(ItemStack stack) {
    return ResourceLocation.fromNamespaceAndPath("entrelumen", "routing_matrix")
        .equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  @Override
  public LogisticsStock newBlockEntity(BlockPos pos, BlockState state) {
    return new LogisticsStock(pos, state);
  }

  @Override
  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
      boolean movedByPiston) {
    if (!state.is(newState.getBlock()) && !level.isClientSide
        && level.getBlockEntity(pos) instanceof LogisticsStock stock)
      stock.dropAll(level, pos);
    super.onRemove(state, level, pos, newState, movedByPiston);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
      BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (hand != InteractionHand.MAIN_HAND || !isMatrix(stack))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer) {
      var outcome = player.isSecondaryUseActive()
          ? LogisticsProvisioning.record(serverPlayer, pos)
          : LogisticsProvisioning.prepare(serverPlayer, pos);
      serverPlayer.sendSystemMessage(outcome.message());
    }
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer) {
      if (player.isSecondaryUseActive()) {
        ArkActions.depositFromModule(serverPlayer, CampaignActions.campaignId(serverPlayer), pos,
            EngineeringDiagnostics.currentReadOnly(serverPlayer).arkPhase);
        LogisticsModuleActions.inspect(serverPlayer, pos);
      } else if (player.isAlive() && !player.isSpectator()
          && player.canInteractWithBlock(pos, 1.0)
          && level.hasChunkAt(pos)
          && level.getBlockEntity(pos) instanceof LogisticsStock stock) {
        serverPlayer.openMenu(stock);
        LogisticsModuleActions.inspect(serverPlayer, pos);
      }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
