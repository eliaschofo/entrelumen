package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A way out of the Envés (the {@code enves:exit_portal} marker): the vestibule's return portal is
 * always active; floor V's victory portal wakes when the boss falls ({@link Enves#bossDefeated}).
 * Walking into an active portal leaves to the antechamber. Placeholder look (vanilla obsidian and
 * crying obsidian models).
 */
public final class EnvesPortalBlock extends Block {
  public static final MapCodec<EnvesPortalBlock> CODEC = simpleCodec(EnvesPortalBlock::new);
  public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

  public EnvesPortalBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ACTIVE);
  }

  @Override
  protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return state.getValue(ACTIVE) ? Shapes.empty() : Shapes.block();
  }

  @Override
  protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    if (state.getValue(ACTIVE) && entity instanceof ServerPlayer player && !player.isSpectator())
      Enves.leaveThroughPortal(player);
  }
}
