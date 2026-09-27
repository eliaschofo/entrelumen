package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Envés gate in the Sealed Stair's antechamber (the {@code entrelumen:enves_gate} marker of the
 * start ruin, a 3×4 doorway). Clicking it opens the gate screen: pay the offering and pick a
 * difficulty, or enter the team's open attempt. Placeholder look (vanilla reinforced deepslate).
 */
public final class EnvesGateBlock extends Block {
  public static final MapCodec<EnvesGateBlock> CODEC = simpleCodec(EnvesGateBlock::new);

  public EnvesGateBlock(Properties properties) {
    super(properties);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
      BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) EnvesEntrance.useGate(serverPlayer, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) EnvesEntrance.useGate(serverPlayer, pos);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }
}
