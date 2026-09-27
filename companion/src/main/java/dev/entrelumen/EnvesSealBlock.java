package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A seal of an Envés floor (the {@code enves:seal} marker of a seal room). A member of the group
 * lights it with a click, once {@link EnvesHooks.Seals#mayLight} agrees; when every seal of the floor
 * burns, the stairwell opens. Placeholder look (vanilla lodestone and respawn anchor models); the
 * second worker may dress it.
 */
public final class EnvesSealBlock extends Block {
  public static final MapCodec<EnvesSealBlock> CODEC = simpleCodec(EnvesSealBlock::new);
  public static final BooleanProperty LIT = BlockStateProperties.LIT;

  public EnvesSealBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(LIT, false));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(LIT);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
      BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) Enves.lightSeal(serverPlayer, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
