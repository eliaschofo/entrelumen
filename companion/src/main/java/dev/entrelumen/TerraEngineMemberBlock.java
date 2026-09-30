package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A member of Terra's garden engine: the casing and the outlet. Unformed it is a plain block; once the lamp
 * wakes the engine the core marks its members {@code formed} and they stop drawing themselves (no model, no
 * shade on their neighbours) while the core draws one model over the whole engine. This is the technique of
 * Oritech's machine cores (a hidden member block and a controller model), studied from its JAR's bytecode and
 * blockstates, never copied. The core unforms its members when the engine loses a block or its lamp.
 */
public class TerraEngineMemberBlock extends Block {
  public static final MapCodec<TerraEngineMemberBlock> CODEC = simpleCodec(TerraEngineMemberBlock::new);
  public static final BooleanProperty FORMED = BooleanProperty.create("formed");

  public TerraEngineMemberBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(FORMED, false));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FORMED);
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
  }

  /** Formed, the member lets its neighbours draw their faces: the engine model covers it. */
  @Override
  protected boolean useShapeForLightOcclusion(BlockState state) {
    return state.getValue(FORMED);
  }

  @Override
  protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
    return state.getValue(FORMED) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getOcclusionShape(state, level, pos);
  }

  @Override
  protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
    return state.getValue(FORMED) ? 1.0f : super.getShadeBrightness(state, level, pos);
  }
}
