package dev.entrelumen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The definitive portal, skeleton version. City placement sets it, dormant, on the
 * {@code town_hall_portal} marker. A team that unlocked Solsticio sets the three Heliodor relics
 * into it; with all three in place, a player holding their own broken Light Key opens it. Open,
 * anyone may walk in: from Solsticio it leads home, from its Overworld twin (only without
 * Waystones) it leads to the arrival point. The key is only shown, never consumed.
 */
public final class SolsticioPortalBlock extends Block {
  public static final MapCodec<SolsticioPortalBlock> CODEC = simpleCodec(SolsticioPortalBlock::new);
  public static final BooleanProperty ARMED = BooleanProperty.create("armed");
  /** Dormant: a keystone pedestal to click. */
  private static final VoxelShape DORMANT = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(4, 4, 4, 12, 13, 12));
  /** Open: a low sill you walk into. */
  private static final VoxelShape OPEN = Block.box(2, 0, 2, 14, 2, 14);

  public SolsticioPortalBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(ARMED, false));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ARMED);
  }

  @Override
  protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return state.getValue(ARMED) ? OPEN : DORMANT;
  }

  @Override
  protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return state.getValue(ARMED) ? Shapes.empty() : DORMANT;
  }

  @Override
  protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
      Player player, InteractionHand hand, BlockHitResult hit) {
    int relic = Solsticio.relicOf(stack);
    if (relic == 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (player instanceof ServerPlayer serverPlayer) insertRelic(serverPlayer, pos, stack, relic);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
      BlockHitResult hit) {
    if (player instanceof ServerPlayer serverPlayer) {
      if (!tryArm(serverPlayer, pos)) describe(serverPlayer, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    if (!state.getValue(ARMED) || !(entity instanceof ServerPlayer player) || player.isSpectator()) return;
    SolsticioData data = SolsticioData.get(player.server);
    boolean solsticioSide = level.dimension().equals(Solsticio.LEVEL) && pos.equals(data.portal);
    boolean overworldSide = level.dimension().equals(Level.OVERWORLD) && pos.equals(data.overworldPortal);
    if (!data.portalArmed || (!solsticioSide && !overworldSide)) return;
    SolsticioTravel.portalContact(player, solsticioSide);
  }

  @Override
  public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    int count = state.getValue(ARMED) ? 3 : 1;
    for (int i = 0; i < count; i++)
      level.addParticle(state.getValue(ARMED) ? ParticleTypes.END_ROD : ParticleTypes.WAX_ON,
          pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3 + random.nextDouble() * 1.4,
          pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
  }

  // ---- Server logic -----------------------------------------------------------------------

  private static boolean isAnchor(ServerPlayer player, BlockPos pos) {
    SolsticioData data = SolsticioData.get(player.server);
    return player.level().dimension().equals(Solsticio.LEVEL) && pos.equals(data.portal);
  }

  static boolean insertRelic(ServerPlayer player, BlockPos pos, ItemStack stack, int relic) {
    SolsticioData data = SolsticioData.get(player.server);
    if (!isAnchor(player, pos)) {
      player.displayClientMessage(Component.translatable("entrelumen.portal.inert"), true);
      return false;
    }
    if (data.portalArmed) {
      player.displayClientMessage(Component.translatable("entrelumen.portal.open"), true);
      return false;
    }
    if (!Solsticio.GATE.satisfiedBy(StructureProtection.actor(player))) {
      player.displayClientMessage(Component.translatable("entrelumen.protection.locked"), true);
      return false;
    }
    if (data.portalRelics.contains(relic)) {
      player.displayClientMessage(Component.translatable("entrelumen.portal.relic_present"), true);
      return false;
    }
    stack.consume(1, player);
    data.portalRelics.add(relic);
    data.setDirty();
    player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.9F + relic * 0.1F);
    player.sendSystemMessage(Component.translatable("entrelumen.portal.relic_set", data.portalRelics.size(), 3));
    tryArm(player, pos);
    return true;
  }

  /** Opens the portal when all relics are set and the player carries their own broken key. */
  static boolean tryArm(ServerPlayer player, BlockPos pos) {
    SolsticioData data = SolsticioData.get(player.server);
    if (!isAnchor(player, pos) || data.portalArmed || data.portalRelics.size() < Solsticio.RELICS.size()) return false;
    if (!Solsticio.GATE.satisfiedBy(StructureProtection.actor(player))) return false;
    if (!carriesOwnKey(player)) {
      player.sendSystemMessage(Component.translatable("entrelumen.portal.needs_key"));
      return false;
    }
    ServerLevel level = player.serverLevel();
    data.portalArmed = true;
    data.portalArmedBy = player.getUUID();
    data.portalArmedAt = level.getGameTime();
    level.setBlock(pos, level.getBlockState(pos).setValue(ARMED, true), Block.UPDATE_ALL);
    // Arming by hand is not an arrival: the player may use it right away.
    SolsticioTravel.forget(player);
    link(player.server);
    data.setDirty();
    level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
    player.server.getPlayerList().broadcastSystemMessage(
        Component.translatable("entrelumen.portal.opened", player.getDisplayName()), false);
    return true;
  }

  /** Waystones when installed, otherwise a twin portal beside the Overworld spawn. */
  static void link(net.minecraft.server.MinecraftServer server) {
    SolsticioData data = SolsticioData.get(server);
    ServerLevel solsticio = server.getLevel(Solsticio.LEVEL);
    if (solsticio == null) return;
    if (!data.waystoneRegistered && WaystonesBridge.available()) {
      BlockPos spot = data.waystone != null ? data.waystone : null;
      if (spot != null && WaystonesBridge.placeGlobalWaystone(solsticio, spot,
          Component.translatable("entrelumen.portal.waystone_name"))) {
        data.waystoneRegistered = true;
        data.setDirty();
        return;
      }
    }
    if (data.waystoneRegistered || data.overworldPortal != null) return;
    ServerLevel overworld = server.overworld();
    BlockPos spawn = overworld.getSharedSpawnPos();
    BlockPos spot = null;
    for (int r = 3; r <= 12 && spot == null; r++)
      for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
        BlockPos column = spawn.offset(offset[0], 0, offset[1]);
        overworld.getChunk(column.getX() >> 4, column.getZ() >> 4);
        BlockPos top = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (SolsticioTravel.standable(overworld, top)) {
          spot = top;
          break;
        }
      }
    if (spot == null) spot = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.east(3));
    overworld.setBlock(spot, Solsticio.PORTAL.get().defaultBlockState().setValue(ARMED, true), Block.UPDATE_ALL);
    data.overworldPortal = spot.immutable();
    data.setDirty();
    StructureProtection.invalidate(server);
  }

  static boolean carriesOwnKey(ServerPlayer player) {
    var inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.is(Solsticio.LIGHT_KEY_BROKEN.get())) continue;
      var owner = stack.get(Solsticio.KEY_OWNER);
      if (owner != null && owner.id().equals(player.getUUID())) return true;
    }
    return false;
  }

  private static void describe(ServerPlayer player, BlockPos pos) {
    SolsticioData data = SolsticioData.get(player.server);
    if (!isAnchor(player, pos)) {
      player.displayClientMessage(Component.translatable("entrelumen.portal.inert"), true);
      return;
    }
    if (data.portalArmed) player.displayClientMessage(Component.translatable("entrelumen.portal.open"), true);
    else player.displayClientMessage(Component.translatable("entrelumen.portal.dormant", data.portalRelics.size(), 3), true);
  }
}
