package dev.entrelumen;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A movable survey bookmark. No block entity, periodic work or campaign unlock. */
public final class SignalStationBlock extends Block {
  private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
      net.minecraft.world.phys.shapes.Shapes.or(
          Block.box(0, 9, 0, 16, 12, 16), Block.box(3, 3, 3, 13, 4, 13),
          Block.box(1, 0, 1, 5, 9, 5), Block.box(11, 0, 1, 15, 9, 5),
          Block.box(1, 0, 11, 5, 9, 15), Block.box(11, 0, 11, 15, 9, 15),
          Block.box(11, 12, 9, 15, 16, 14));

  @Override
  protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
      net.minecraft.world.level.BlockGetter level, BlockPos pos,
      net.minecraft.world.phys.shapes.CollisionContext context) {
    return SHAPE;
  }

  public SignalStationBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
      BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (!stack.is(Items.COMPASS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (!level.isClientSide) {
      stack.set(DataComponents.LODESTONE_TRACKER,
          new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos.immutable())), false));
      player.displayClientMessage(Component.translatable("entrelumen.station.bound",
          pos.getX(), pos.getY(), pos.getZ()), true);
    }
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    // Vanilla tries MAIN_HAND first. Do not consume that attempt before an offhand compass.
    if (!player.getMainHandItem().isEmpty() || player.getOffhandItem().is(Items.COMPASS))
      return InteractionResult.PASS;
    if (player instanceof ServerPlayer serverPlayer) AtlasNetwork.open(serverPlayer);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
