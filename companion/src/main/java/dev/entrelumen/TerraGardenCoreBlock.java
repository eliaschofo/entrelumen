package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The core of Terra's garden engine, in the middle of its front. Unformed ({@code unbuilt}, {@code built}) it
 * is a casing cube with a window; woken ({@code growing}) its model is the whole formed engine over the
 * 3 x 2 x 2 volume, with the small pot on top whose rim glows, while the members draw nothing
 * ({@link TerraEngineMemberBlock}). Its block entity renderer draws the crop growing in the pot.
 *
 * <p>Gestures: a seed in the main hand goes in (the old one comes back); the grow lamp wakes the garden
 * (the lamp's own use); an empty hand reads the garden's state; crouching with an empty hand takes the
 * seed out.
 */
public final class TerraGardenCoreBlock extends HorizontalDirectionalBlock implements EntityBlock {
  public static final MapCodec<TerraGardenCoreBlock> CODEC = simpleCodec(TerraGardenCoreBlock::new);

  public enum Garden implements StringRepresentable {
    UNBUILT, BUILT, GROWING;

    @Override
    public String getSerializedName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public static final EnumProperty<Garden> GARDEN = EnumProperty.create("garden", Garden.class);

  public TerraGardenCoreBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(GARDEN, Garden.UNBUILT));
  }

  @Override
  protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, GARDEN);
  }

  /** The lamp looks at whoever places it: that is the garden's front. */
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    return state.rotate(mirror.getRotation(state.getValue(FACING)));
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TerraGardenCoreEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    if (level.isClientSide || type != TerraGarden.CORE_ENTITY.get()) return null;
    return (world, pos, blockState, entity) -> ((TerraGardenCoreEntity) entity).serverTick((ServerLevel) world);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    // The lamp does its work in its own use (GrowLampItem.useOn), which runs after this is skipped.
    if (stack.is(TerraGarden.GROW_LAMP.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    if (hand != InteractionHand.MAIN_HAND || TerraGarden.crop(stack) == null
        || !(level.getBlockEntity(pos) instanceof TerraGardenCoreEntity core))
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer && AltarBlock.reachable(serverPlayer, pos))
      core.insertSeed(serverPlayer, stack);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
      BlockHitResult hit) {
    if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer && AltarBlock.reachable(serverPlayer, pos)
        && level.getBlockEntity(pos) instanceof TerraGardenCoreEntity core) {
      if (player.isSecondaryUseActive()) core.takeSeed(serverPlayer);
      else core.report(serverPlayer);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  /** Broken while awake, the pot gives its lamp back (its seed and store stay on the dropped item). */
  @Override
  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
        && level.getBlockEntity(pos) instanceof TerraGardenCoreEntity core)
      core.releaseLamp(server, pos);
    super.onRemove(state, level, pos, newState, movedByPiston);
  }

  @Override
  protected boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  /** 15 when the store is full, down to 0 when it is empty. */
  @Override
  protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    return level.getBlockEntity(pos) instanceof TerraGardenCoreEntity core ? core.comparatorLevel() : 0;
  }
}
