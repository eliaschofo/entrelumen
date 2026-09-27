package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Envés's puzzle and shrine blocks. They only hold their look; a click on one is answered by
 * {@link EnvesPuzzles} (braziers, mirrors, glyph stones) or {@link EnvesShrines} (the altar) before the
 * block is asked, so the same block serves every seal and vault. Placeholder looks, all from vanilla
 * textures: a blackstone bowl with fire, a quartz mirror on a calcite foot, four vanilla chiseled
 * faces, the respawn anchor.
 */
public final class EnvesContentBlocks {
  private EnvesContentBlocks() {}

  /** Answers a click with success on both sides; the server's answer comes from the puzzle. */
  abstract static class Answering extends Block {
    Answering(Properties properties) {
      super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
  }

  /** A brazier of the braziers' echo and of the offering lock; {@code lit} while it burns. */
  public static final class Brazier extends Answering {
    public static final MapCodec<Brazier> CODEC = simpleCodec(Brazier::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 7, 14);

    public Brazier(Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
    }
  }

  /** A mirror of the sour light: aim 0 is '/', 1 is '\' seen from above. */
  public static final class Mirror extends Answering {
    public static final MapCodec<Mirror> CODEC = simpleCodec(Mirror::new);
    public static final IntegerProperty AIM = IntegerProperty.create("aim", 0, 1);
    static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public Mirror(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(AIM, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
      return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(AIM);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
    }
  }

  /** A glyph stone of a vault's lock: four faces, each a vanilla chiseled block. */
  public static final class Glyph extends Answering {
    public static final MapCodec<Glyph> CODEC = simpleCodec(Glyph::new);
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, EnvesPuzzleRules.GLYPHS - 1);

    public Glyph(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(GLYPH, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
      return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(GLYPH);
    }
  }

  /** A shrine's altar; {@code spent} once its blessing is taken. */
  public static final class Shrine extends Answering {
    public static final MapCodec<Shrine> CODEC = simpleCodec(Shrine::new);
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");

    public Shrine(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(SPENT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
      return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(SPENT);
    }
  }
}
