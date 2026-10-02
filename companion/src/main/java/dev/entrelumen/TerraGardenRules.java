package dev.entrelumen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Registry-free numbers and arithmetic of Terra's hydroponic garden (docs/design/terra-garden.md).
 *
 * <p>The garden works in batches: once every {@link #BATCH_TICKS} of game time it rolls the crop's
 * harvest {@link #SAMPLE_ROLLS} times and scales the sum to {@link #HARVESTS_PER_BATCH} harvests, never
 * per-tick work. The batch clock is the level's game time, so a block-entity accelerator that ticks the
 * core more often in one game tick gains nothing.
 *
 * <p>The rate is Elias's (29 September 2026): «con ganarle x20 a cualquier método endgame basta». The best
 * single producer measured in the pinned JARs is More Machine's ultimate planting factory with eight speed
 * upgrades: nine operations a second, five wheat each, 45 a second. A garden makes 20 times that: 900
 * harvests a second, one wheat each. Every other setup measured is far lower (the design doc has the table).
 */
public final class TerraGardenRules {
  /** One batch a second. */
  public static final int BATCH_TICKS = 20;
  /** Harvests a second: 20 times the ultimate planting factory. One harvest is one roll of the crop's loot table at full age. */
  public static final long HARVESTS_PER_BATCH = 900;
  /** How many times the garden outgrows the best single endgame producer. */
  public static final int TIMES_THE_BEST = 20;
  /** Loot rolls actually made per batch; their sum is scaled to {@link #HARVESTS_PER_BATCH}. */
  public static final int SAMPLE_ROLLS = 16;
  /** Items the core holds before it stops: 256 stacks of 64, about six batches of wheat. */
  public static final long STORE_CAPACITY = 16_384;
  /** Game ticks between two checks of the garden's blocks. */
  public static final int CHECK_TICKS = 100;
  /** Stack insertions the core tries per batch when it exports, over all the outlets' neighbours. */
  public static final int EXPORT_INSERTS_PER_BATCH = 1_024;
  /** Output slots the core and its outlets show to pipes (the store, 64 items per slot). */
  public static final int OUTPUT_SLOTS = 64;
  /** Harvests on a player's statistic for the big-numbers quest: about 18.5 minutes of one garden. */
  public static final long MILESTONE_HARVESTS = 1_000_000L;

  // ---- the measured alternatives (docs/design/terra-garden.md, «Números») ------------------------
  /** The engine's box: 3 wide, 2 deep, 2 tall (twelve factories in it would make 540 wheat/s, under 900). */
  public static final int GARDEN_VOLUME = 3 * 2 * 2;
  /** More Machine's ultimate planting factory, 8 speed upgrades: 9 processes x 1 op/s x 5 wheat. */
  public static final double PLANTING_FACTORY_WHEAT_PER_SECOND = 9 * (20.0 / 20.0) * 5;
  /** Botany Pots Tiers' mega hopper pot, wheat on the best soil, Efficiency X hoe: 5 rolls per 108 ticks. */
  public static final double MEGA_POT_HARVESTS_PER_SECOND = 5 * 20.0 / 108;

  private TerraGardenRules() {}

  /**
   * Scales the harvest of {@code samples} rolls to {@code harvests} harvests. Each count is multiplied by
   * {@code harvests / samples}; the fractional part becomes one more item with that probability, so the
   * expectation is exact. {@code random} returns a uniform double in [0, 1).
   */
  public static <K> Map<K, Long> scale(Map<K, Long> sampled, int samples, long harvests, DoubleSupplier random) {
    if (samples <= 0) throw new IllegalArgumentException("samples must be positive");
    Map<K, Long> out = new LinkedHashMap<>();
    for (var entry : sampled.entrySet()) {
      long product = Math.multiplyExact(entry.getValue(), harvests);
      long whole = product / samples;
      long rest = product % samples;
      if (rest > 0 && random.getAsDouble() * samples < rest) whole++;
      if (whole > 0) out.put(entry.getKey(), whole);
    }
    return out;
  }

  public static long total(Map<?, Long> counts) {
    long sum = 0;
    for (long value : counts.values()) sum = Math.addExact(sum, value);
    return sum;
  }

  /**
   * The part of a batch that fits in {@code free} items: all of it, or every count cut by the same
   * fraction (rounded down), so the mix stays the crop's and nothing is ever made and thrown away.
   */
  public static <K> Map<K, Long> fit(Map<K, Long> batch, long free) {
    long total = total(batch);
    if (total <= free) return batch;
    Map<K, Long> out = new LinkedHashMap<>();
    if (free <= 0) return out;
    for (var entry : batch.entrySet()) {
      long kept = Math.multiplyExact(entry.getValue(), free) / total;
      if (kept > 0) out.put(entry.getKey(), kept);
    }
    return out;
  }

  /** Harvests that a fitted batch stands for: the full count scaled by what was kept. */
  public static long harvestsKept(long fullItems, long keptItems, long harvests) {
    if (fullItems <= 0) return 0;
    if (keptItems >= fullItems) return harvests;
    return Math.multiplyExact(harvests, keptItems) / fullItems;
  }

  /**
   * A core's first offset into a period after it loads, from a mixed hash of its packed position
   * ({@code BlockPos.hashCode} keeps neighbours close), so gardens that load together do not all work on
   * the same tick; {@code shift} draws the check's phase from other bits than the batch's.
   */
  public static long phase(long packedPos, int period, int shift) {
    return Math.floorMod(it.unimi.dsi.fastutil.HashCommon.mix(packedPos) >>> shift, (long) period);
  }

  /** Harvests a second of the garden. */
  public static double harvestsPerSecond() {
    return HARVESTS_PER_BATCH * 20.0 / BATCH_TICKS;
  }
}
