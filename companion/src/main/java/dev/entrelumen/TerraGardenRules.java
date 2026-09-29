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
 * <p>The rate is set against the strongest per-block producer measured in the pinned JARs, More
 * Machine's ultimate planting factory with eight speed upgrades (nine operations a second, five wheat
 * each: 45 wheat a second per block). A garden's bounding box (7 x 9 x 7, 441 blocks) filled with those
 * factories and nothing else, no cables, power or pipes, would make 19,845 wheat a second; the garden
 * makes 32,768. Every other setup measured is far lower (the design doc has the table).
 */
public final class TerraGardenRules {
  /** One batch a second. */
  public static final int BATCH_TICKS = 20;
  /** Harvests a second: 2^15. One harvest is one roll of the crop's loot table at full age. */
  public static final long HARVESTS_PER_BATCH = 32_768;
  /** Loot rolls actually made per batch; their sum is scaled to {@link #HARVESTS_PER_BATCH}. */
  public static final int SAMPLE_ROLLS = 16;
  /** Items the core holds before it stops: 4,096 stacks of 64, about three batches of wheat. */
  public static final long STORE_CAPACITY = 262_144;
  /** Game ticks between two checks of the garden's blocks. */
  public static final int CHECK_TICKS = 100;
  /** Stack insertions the core tries per batch when it exports, over all its neighbours. */
  public static final int EXPORT_INSERTS_PER_BATCH = 8_192;
  /** Output slots the core shows to pipes (its store, 64 items per slot). */
  public static final int OUTPUT_SLOTS = 64;
  /** Harvests on a player's statistic for the big-numbers quest. */
  public static final long MILESTONE_HARVESTS = 10_000_000L;

  // ---- the measured alternatives (docs/design/terra-garden.md, «Números») ------------------------
  /** Garden bounding box: 7 wide, 9 tall (plinth to the rod on the dome), 7 deep. */
  public static final int GARDEN_VOLUME = 7 * 9 * 7;
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

  /** The planting-factory wall that fills the garden's box, wheat a second (an upper bound). */
  public static double factoryWallWheatPerSecond() {
    return GARDEN_VOLUME * PLANTING_FACTORY_WHEAT_PER_SECOND;
  }

  /** Harvests a second of the garden. */
  public static double harvestsPerSecond() {
    return HARVESTS_PER_BATCH * 20.0 / BATCH_TICKS;
  }
}
