package dev.entrelumen;

/**
 * Registry-free rules of the Terralight crystal, the only renewable source of Terra's grow lamps
 * (docs/design/terra-garden.md, «La economía de las lámparas»).
 *
 * <p>Growth is measured in game time that the grounding rod's block entity has seen elapse, never in random
 * ticks or tick counts, so bone meal, accelerators, a time wand and extra ticks change nothing. The one
 * thing that speeds it up is rain: while it rains at the crystal and for {@link #AFTER_RAIN_TICKS} after, the
 * elapsed time counts {@link #RAIN_MULTIPLIER} times. Numbers are Elias's and the controller's (29 September
 * 2026); the server config can change them.
 */
public final class TerralightRules {
  /** Four hours of play, dry, from nothing to a full crystal. */
  public static final long FULL_GROWTH_TICKS = 4L * 60 * 60 * 20;
  /** Rain makes it grow eight times as fast: a full crystal in about thirty minutes of constant rain. */
  public static final int RAIN_MULTIPLIER = 8;
  /** Five minutes after the rain stops the crystal still drinks. */
  public static final long AFTER_RAIN_TICKS = 5L * 60 * 20;
  /** A gap longer than this between two looks (an unloaded chunk) counts dry, whatever the weather was. */
  public static final long CONTINUOUS_TICKS = 40;
  /** Shards a full crystal gives: fortune and silk touch do not change it. */
  public static final int SHARDS_PER_CRYSTAL = 1;

  private TerralightRules() {}

  /** How fast elapsed time counts: {@code multiplier} while it rains or rained less than {@code after} ago. */
  public static int factor(boolean raining, long sinceRain, int multiplier, long after) {
    return raining || (sinceRain >= 0 && sinceRain < after) ? multiplier : 1;
  }

  /**
   * The progress after {@code elapsed} ticks. A continuous look ({@code elapsed <= CONTINUOUS_TICKS}) takes
   * the weather's factor; a longer gap counts dry. Never beyond {@code full}; never backwards.
   */
  public static long advance(long progress, long elapsed, int factor, long full) {
    if (elapsed <= 0) return Math.min(progress, full);
    long counted = elapsed <= CONTINUOUS_TICKS ? Math.multiplyExact(elapsed, factor) : elapsed;
    return Math.min(full, progress + counted);
  }

  /**
   * The crystal's stage for a progress: none (-1) before it starts, then a small bud (0), a medium one (1)
   * and a large one (2) by quarters, and the cluster (3) only when full.
   */
  public static int stage(long progress, long full) {
    if (progress <= 0) return -1;
    if (progress >= full) return 3;
    return (int) Math.min(2, progress * 4 / full);
  }
}
