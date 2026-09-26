package dev.entrelumen;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The companion blocks of the ruin challenges. They hold only their look and state; what a click
 * means is decided by {@link RuinChallenges} from the ruin's markers, so the same block type serves
 * every ruin.
 */
public final class RuinBlocks {
  private RuinBlocks() {}

  /** What a gate cell or a hidden trigger looks like; {@link #SEAL} is a pale light. */
  public enum Look implements StringRepresentable {
    SEAL, TUFF_BRICKS, POLISHED_TUFF, CALCITE, MOSSY_STONE_BRICKS, MOSS_BLOCK, ROOTED_DIRT,
    POLISHED_BLACKSTONE_BRICKS, QUARTZ_BRICKS, END_STONE_BRICKS;

    @Override
    public String getSerializedName() {
      return name().toLowerCase(Locale.ROOT);
    }

    static Look of(String name) {
      for (Look look : values()) if (look.getSerializedName().equals(name)) return look;
      return SEAL;
    }
  }

  public enum SocketLook implements StringRepresentable {
    POT, ALTAR;

    @Override
    public String getSerializedName() {
      return name().toLowerCase(Locale.ROOT);
    }

    static SocketLook of(String name) {
      return "altar".equals(name) ? ALTAR : POT;
    }
  }

  public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);
  public static final EnumProperty<SocketLook> SOCKET_LOOK = EnumProperty.create("look", SocketLook.class);
  /** A mirror's aim, clockwise from north in eighths (see {@link RuinMarkers#DIRECTIONS}). */
  public static final IntegerProperty AIM = IntegerProperty.create("aim", 0, 7);
  public static final BooleanProperty FILLED = BooleanProperty.create("filled");
  public static final BooleanProperty CLIMB = BooleanProperty.create("climb");

  /**
   * A block whose click belongs to its ruin. The server answers from {@link RuinChallenges} before
   * the block is asked; on the client the click is simply taken, so a held block is never predicted
   * as placed against it.
   */
  public static class Answering extends Block {
    public Answering(Properties properties) {
      super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit) {
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hit) {
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
  }

  /** A silvered plate on a copper stand, turned an eighth per click. */
  public static final class Mirror extends Answering {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public Mirror(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(AIM, 0));
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

  /** An offering socket: a pot for plants or a small altar, shown filled for the last team. */
  public static final class Socket extends Answering {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public Socket(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(FILLED, false).setValue(SOCKET_LOOK, SocketLook.POT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(FILLED, SOCKET_LOOK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
    }
  }

  /** A stone like any other in the wall, except that it answers a touch. */
  public static final class Hidden extends Answering {
    public Hidden(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(LOOK, Look.TUFF_BRICKS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(LOOK);
    }
  }

  /** The core of a redstone lock: power reaching it solves its challenge for the crediting team. */
  public static final class Lock extends Block {
    public Lock(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(BlockStateProperties.POWERED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from,
        boolean moving) {
      if (!(level instanceof ServerLevel server)) return;
      boolean powered = level.hasNeighborSignal(pos);
      if (powered == state.getValue(BlockStateProperties.POWERED)) return;
      level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
      if (powered) RuinChallenges.lockPowered(server, pos);
    }
  }

  /**
   * One cell of a per-team gate: solid for everyone, except players whose team opened it. The
   * server decides from the team's progress; the client from the cells the server sent it
   * ({@link RuinGates}). The shape is dynamic, so it is never cached per state.
   */
  public static final class Gate extends Block {
    public Gate(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(LOOK, Look.SEAL).setValue(CLIMB, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(LOOK, CLIMB);
    }

    static boolean passes(BlockGetter level, BlockPos pos, Entity entity) {
      return entity instanceof Player player && RuinGates.open(player, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
        CollisionContext context) {
      if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() != null
          && passes(level, pos, entityContext.getEntity())) return Shapes.empty();
      return Shapes.block();
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
      return state.getValue(CLIMB) && entity instanceof Player player && RuinGates.open(player, pos);
    }
  }
}
