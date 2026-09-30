package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The grounding rod's memory: how far its Terralight crystal has grown, in game time it saw elapse.
 *
 * <p>The setup (docs/design/terra-garden.md): beside the rod, a column of grass (any {@code #minecraft:dirt}),
 * the crystal's air block and a grow lamp placed as a block; above the rod two «cables» (any block with an
 * energy capability); under it, dirt or grass. Once a second of game time the rod looks: while the setup is
 * whole the crystal grows by the time elapsed since the last look, eight times as fast in the rain; while it
 * is not, growth waits. The rod places and swaps the crystal's stages itself; when the crystal it placed is
 * gone (mined, broken, moved), growth starts again from nothing.
 *
 * <p>Nothing else can speed it up: the crystal takes no random ticks and no bone meal, and a second call of
 * the rod's ticker in the same game tick counts no time.
 */
public final class GroundingRodEntity extends BlockEntity {
  private long progress;
  private long lastTime = Long.MIN_VALUE;
  private long lastRain = Long.MIN_VALUE;
  private long lastTick = Long.MIN_VALUE;
  private long nextLook = Long.MIN_VALUE;
  private boolean placed;
  private boolean whole;
  /** Test hook: forces rain (true), dry weather (false) or the real weather (null). */
  private Boolean rainOverride;

  public GroundingRodEntity(BlockPos pos, BlockState state) {
    super(Terralight.ROD_ENTITY.get(), pos, state);
  }

  void serverTick(ServerLevel level) {
    long now = level.getGameTime();
    if (now == lastTick) return;
    lastTick = now;
    if (now < nextLook) return;
    nextLook = now + 20;
    look(level, now);
  }

  /** The side the crystal's column stands on, or null when no side holds a whole setup. */
  Direction column(ServerLevel level) {
    BlockPos rod = worldPosition;
    if (!Terralight.soil(level.getBlockState(rod.below())) || !Terralight.cable(level, rod.above())
        || !Terralight.cable(level, rod.above(2)))
      return null;
    for (Direction side : Direction.Plane.HORIZONTAL) {
      BlockPos ground = rod.relative(side);
      BlockState air = level.getBlockState(ground.above());
      if (Terralight.soil(level.getBlockState(ground)) && (air.isAir() || air.is(Terralight.CRYSTAL.get()))
          && level.getBlockState(ground.above(2)).is(TerraGarden.LAMP_BLOCK.get()))
        return side;
    }
    return null;
  }

  boolean raining(ServerLevel level, BlockPos at) {
    if (rainOverride != null) return rainOverride;
    return level.isRaining() && level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.RAIN;
  }

  void look(ServerLevel level, long now) {
    long elapsed = lastTime == Long.MIN_VALUE ? 0 : now - lastTime;
    lastTime = now;
    Direction side = column(level);
    boolean was = whole;
    whole = side != null;
    if (side == null) {
      if (was) setChanged();
      return;
    }
    BlockPos spot = worldPosition.relative(side).above();
    BlockState there = level.getBlockState(spot);
    if (placed && !there.is(Terralight.CRYSTAL.get())) {
      progress = 0;      // the crystal was mined or broken: it starts again
      placed = false;
    }
    boolean rain = raining(level, spot);
    long sinceRain = lastRain == Long.MIN_VALUE ? -1 : now - lastRain;
    if (rain) lastRain = now;
    int factor = TerralightRules.factor(rain, sinceRain, Terralight.rainMultiplier(), Terralight.afterRainTicks());
    long full = Terralight.fullGrowthTicks();
    progress = TerralightRules.advance(progress, elapsed, factor, full);
    int stage = TerralightRules.stage(progress, full);
    if (stage >= 0) {
      BlockState wanted = Terralight.CRYSTAL.get().defaultBlockState().setValue(Terralight.CrystalBlock.STAGE, stage);
      if (!there.equals(wanted)) level.setBlock(spot, wanted, Block.UPDATE_ALL);
      placed = true;
    }
    setChanged();
  }

  // ---- accessors and test hooks ------------------------------------------------------------------------

  public long progress() {
    return progress;
  }

  public boolean whole() {
    return whole;
  }

  /** Test hook: the last look happened {@code ticks} ago (time that elapsed with the setup loaded). */
  void rewind(long ticks) {
    if (lastTime != Long.MIN_VALUE) lastTime -= ticks;
    lastTick = Long.MIN_VALUE;
    nextLook = Long.MIN_VALUE;
  }

  void forceRain(Boolean raining) {
    rainOverride = raining;
  }

  /** Test hook: forget the last rain. */
  void forgetRain() {
    lastRain = Long.MIN_VALUE;
  }

  // ---- saving ----------------------------------------------------------------------------------------

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putLong("progress", progress);
    tag.putBoolean("placed", placed);
    if (lastTime != Long.MIN_VALUE) tag.putLong("lastTime", lastTime);
    if (lastRain != Long.MIN_VALUE) tag.putLong("lastRain", lastRain);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    progress = tag.getLong("progress");
    placed = tag.getBoolean("placed");
    lastTime = tag.contains("lastTime") ? tag.getLong("lastTime") : Long.MIN_VALUE;
    lastRain = tag.contains("lastRain") ? tag.getLong("lastRain") : Long.MIN_VALUE;
  }
}
