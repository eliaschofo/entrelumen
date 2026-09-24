package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * An altar whose whole work is an effect over its area while it has fuel: the Altars of Peace,
 * Growth and Time. Every active tick costs one tick of paid time; the altar is registered in the
 * {@link AltarRegistry} exactly while it is active, which is what the effects consult.
 *
 * <p>Gestures: fuel in the main hand feeds it; an empty hand reports its state and marks the
 * area's edge with particles for five seconds; crouching with an empty hand pauses or resumes it.
 * A paused altar burns no fuel and affects nothing.
 */
public abstract class AreaAltarEntity extends AltarBlockEntity {
  static final int PREVIEW_TICKS = 100;
  protected boolean paused;
  private long previewUntil;
  private int radiusOverride = -1;
  private int halfHeightOverride = -1;
  long workingTicks;
  long tickNanos;
  long maxTickNanos;
  final long[] tickSamples = new long[4096];

  protected AreaAltarEntity(BlockEntityType<?> entityType, BlockPos pos, BlockState state, AltarType type) {
    super(entityType, pos, state, type);
  }

  /** The translation key segment of this altar: peace, growth or time. */
  protected abstract String key();

  protected abstract int defaultRadius();

  protected abstract int defaultHalfHeight();

  /** The particle that marks the edge during a preview and rises from an active altar. */
  protected abstract ParticleOptions particle();

  /** The altar's own work on an active tick; effects answered by events need none. */
  protected void activeTick(ServerLevel world) {}

  @Override
  protected int areaRadius() {
    return radiusOverride >= 0 ? radiusOverride : defaultRadius();
  }

  @Override
  protected int areaHalfHeight() {
    return halfHeightOverride >= 0 ? halfHeightOverride : defaultHalfHeight();
  }

  /** Test hook: a smaller area, so an isolated test cannot reach its neighbours. Not saved. */
  void configureArea(int radius, int halfHeight) {
    radiusOverride = radius;
    halfHeightOverride = halfHeight;
  }

  @Override
  protected boolean working() {
    return !paused && fuelAvailable();
  }

  public boolean paused() {
    return paused;
  }

  /** Whether the area effect is on: not paused and paid or fuelled. */
  public boolean effectActive() {
    return working();
  }

  @Override
  public void serverTick(ServerLevel world) {
    try {
      long now = world.getGameTime();
      if (previewUntil > now && now % 10 == 0) preview(world);
      if (paused || !payActiveTick()) return;
      long started = System.nanoTime();
      activeTick(world);
      long spent = System.nanoTime() - started;
      tickSamples[(int) (workingTicks % tickSamples.length)] = spent;
      workingTicks++;
      tickNanos += spent;
      maxTickNanos = Math.max(maxTickNanos, spent);
      if (now % 40 == 0)
        world.sendParticles(particle(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
            worldPosition.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.01);
      if (activeTicks % 200 == 0) setChanged();
    } finally {
      refreshRegistration();
    }
  }

  // ---- Gestures -------------------------------------------------------------------------------

  /** Empty hand: status, and the area's edge in particles for five seconds. */
  @Override
  public void use(ServerPlayer player) {
    previewUntil = player.serverLevel().getGameTime() + PREVIEW_TICKS;
    for (Component line : statusLines()) player.sendSystemMessage(line);
  }

  /** Crouched empty hand: pause or resume. */
  @Override
  public void crouchUse(ServerPlayer player) {
    claimOwner(player);
    paused = !paused;
    player.sendSystemMessage(Component.translatable(paused ? "entrelumen.altar.paused" : "entrelumen.altar.resumed"));
    setChanged();
    refreshRegistration();
  }

  List<Component> statusLines() {
    List<Component> lines = new ArrayList<>();
    String state = paused ? "paused" : fuelAvailable() ? "running" : "unfueled";
    int side = 2 * areaRadius() + 1, height = 2 * areaHalfHeight() + 1;
    lines.add(Component.translatable("entrelumen.altar." + key() + ".status",
        Component.translatable("entrelumen.altar.state." + state), side, side, height));
    long seconds = (activeTicks + (long) fuel.getCount() * type.ticksPerFuel()) / 20;
    lines.add(Component.translatable("entrelumen.altar.area.fuel", fuel.getCount(),
        String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), fuelUsed));
    return lines;
  }

  /** Particles on the area's edge, on all four sides, at ground height within its vertical span. */
  private void preview(ServerLevel world) {
    int radius = areaRadius(), half = areaHalfHeight();
    int step = Math.max(1, radius / 16);
    java.util.TreeSet<Integer> marks = new java.util.TreeSet<>(List.of(-radius, radius));
    for (int k = 0; k <= radius; k += step) {
      marks.add(k);
      marks.add(-k);
    }
    for (int i : marks)
      for (int[] offset : new int[][] {{i, -radius}, {i, radius}, {-radius, i}, {radius, i}}) {
        int x = worldPosition.getX() + offset[0], z = worldPosition.getZ() + offset[1];
        if (!world.hasChunk(x >> 4, z >> 4)) continue;
        int ground = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int y = half == AltarRegistry.FULL_HEIGHT ? ground
            : Mth.clamp(ground, worldPosition.getY() - half, worldPosition.getY() + half);
        world.sendParticles(particle(), x + 0.5, y + 0.3, z + 0.5, 1, 0, 0, 0, 0);
      }
  }

  // ---- Persistence -----------------------------------------------------------------------------

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    paused = tag.getCompound("area").getBoolean("paused");
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    CompoundTag area = new CompoundTag();
    area.putBoolean("paused", paused);
    tag.put("area", area);
  }
}
