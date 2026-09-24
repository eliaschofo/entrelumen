package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Altar of Growth. While it burns bone blocks, crops and saplings in the 33×33×9 field centred
 * on it grow about twenty times as fast: every tick it gives extra random ticks to positions drawn
 * uniformly from the field, a fixed number per tick with a wall-time cap, so it never scans the
 * area. Ripe harvests inside the field give twice their drops, except seeds and the items and crops
 * tagged {@code entrelumen:growth_altar_no_bonus}; see {@link AltarLootModifier}.
 */
public final class GrowthAltarEntity extends AreaAltarEntity {
  /** Positions sampled and random ticks given, for tests and status. */
  long sampled;
  long ticked;
  private int samplesOverride = -1;

  public GrowthAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.GROWTH_ENTITY.get(), pos, state, AltarType.GROWTH);
  }

  @Override
  protected String key() {
    return "growth";
  }

  @Override
  protected int defaultRadius() {
    return AltarEffectRules.GROWTH_RADIUS;
  }

  @Override
  protected int defaultHalfHeight() {
    return AltarEffectRules.GROWTH_HALF_HEIGHT;
  }

  @Override
  protected ParticleOptions particle() {
    return ParticleTypes.HAPPY_VILLAGER;
  }

  /** Test hook: a denser sampling, so a small test field grows within a test's time. */
  void configureSamples(int samples) {
    samplesOverride = samples;
  }

  int samplesPerTick() {
    return samplesOverride >= 0 ? samplesOverride
        : AltarEffectRules.samplesPerTick(areaRadius(), areaHalfHeight());
  }

  @Override
  protected void activeTick(ServerLevel world) {
    int radius = areaRadius(), half = areaHalfHeight(), samples = samplesPerTick();
    long deadline = System.nanoTime() + AltarEffectRules.GROWTH_NANOS_PER_TICK;
    RandomSource random = world.random;
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int i = 0; i < samples; i++) {
      if ((i & 15) == 15 && System.nanoTime() > deadline) break;
      cursor.set(worldPosition.getX() + random.nextInt(2 * radius + 1) - radius,
          worldPosition.getY() + random.nextInt(2 * half + 1) - half,
          worldPosition.getZ() + random.nextInt(2 * radius + 1) - radius);
      sampled++;
      if (!world.isLoaded(cursor) || !world.shouldTickBlocksAt(cursor)) continue;
      BlockState state = world.getBlockState(cursor);
      if (!state.isRandomlyTicking() || !accelerated(state)) continue;
      state.randomTick(world, cursor.immutable(), random);
      ticked++;
    }
  }

  /** Crops and saplings: the tag, plus every block of the vanilla growing classes and their mods. */
  static boolean accelerated(BlockState state) {
    if (state.is(AltarEffects.GROWTH_ACCELERATED)) return true;
    Block block = state.getBlock();
    return block instanceof CropBlock || block instanceof StemBlock || block instanceof SaplingBlock
        || block instanceof NetherWartBlock || block instanceof CocoaBlock || block instanceof SweetBerryBushBlock;
  }

  /**
   * A harvest whose ripeness lives in the block, so a placed block can never pass for a grown one:
   * crops at their last age, nether wart, cocoa and sweet berries. Melons, pumpkins, sugar cane,
   * cactus and bamboo grow faster but drop normally, because a placed one cannot be told from a
   * grown one.
   */
  static boolean ripe(BlockState state) {
    Block block = state.getBlock();
    if (block instanceof CropBlock crop) return crop.isMaxAge(state);
    if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
    if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
    if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 2;
    return false;
  }
}
