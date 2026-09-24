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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block half of every Ark altar: its shape, server ticking, the placing player as owner,
 * dropping its contents, and the shared gestures. Fuel of the altar's type in the main hand feeds
 * it; an empty main hand uses it; crouching with an empty main hand is the altar's second gesture.
 */
public abstract class AltarBlock extends Block implements EntityBlock {
  private final VoxelShape shape;

  protected AltarBlock(Properties properties, VoxelShape shape) {
    super(properties);
    this.shape = shape;
  }

  /** The altar's block entity type, to route ticks. */
  protected abstract BlockEntityType<? extends AltarBlockEntity> entityType();

  @Override
  protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return shape;
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
      BlockEntityType<T> type) {
    if (level.isClientSide || type != entityType()) return null;
    return (world, pos, blockState, entity) -> ((AltarBlockEntity) entity).serverTick((ServerLevel) world);
  }

  @Override
  public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (!level.isClientSide && placer instanceof Player player
        && level.getBlockEntity(pos) instanceof AltarBlockEntity altar)
      altar.setOwner(player.getUUID(), player.getGameProfile().getName());
  }

  @Override
  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock()) && !level.isClientSide
        && level.getBlockEntity(pos) instanceof AltarBlockEntity altar)
      altar.dropContents(level, pos);
    super.onRemove(state, level, pos, newState, movedByPiston);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof AltarBlockEntity altar)
        || !stack.is(altar.type().fuel()))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer && reachable(serverPlayer, pos)) altar.feed(serverPlayer, stack);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
      BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer && reachable(serverPlayer, pos)
        && level.getBlockEntity(pos) instanceof AltarBlockEntity altar) {
      if (player.isSecondaryUseActive()) altar.crouchUse(serverPlayer);
      else altar.use(serverPlayer);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  static boolean reachable(ServerPlayer player, BlockPos pos) {
    return player.isAlive() && !player.isSpectator() && player.canInteractWithBlock(pos, 1.0)
        && player.level().hasChunkAt(pos);
  }
}
