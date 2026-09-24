package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;

/**
 * Registry-free rules of the Renewal Altar: its area, the order of work, the terrain a column may
 * regain, when a chunk is trusted to match its generator, fertilizer payment and the tick budget.
 */
final class AltarRules {
  /** Half-width of a Renewal Altar's square on its own, in blocks (a 49×49 square). */
  static final int RENEWAL_RADIUS = 24;
  /** Half-width near its team's Ark restoration site (a 65×65 square). */
  static final int RENEWAL_ARK_RADIUS = 32;
  /** Horizontal distance from the team's Ark restoration site that grants the larger radius. */
  static final int ARK_SITE_REACH = 64;
  /** Deepest hole, below the original surface, that the altar refills. */
  static final int FILL_DEPTH = 16;
  /** Tallest native vegetation, above the original surface, the altar considers. */
  static final int CANOPY_HEIGHT = 40;
  /** Horizontal reach of a native canopy beyond its trunk. */
  static final int TREE_MARGIN = 8;
  /** Charge one fertilizer item adds. */
  static final int CHARGE_PER_FERTILIZER = 4;
  /** Charge per restored terrain block, cover block or plant. */
  static final int BLOCK_CHARGE = 1;
  /** Most charge a whole tree costs; a partial repair costs one per block up to this. */
  static final int TREE_CHARGE = 16;
  /** Block writes per altar per tick. */
  static final int OPS_PER_TICK = 32;
  /** Wall time per altar per tick. */
  static final long NANOS_PER_TICK = 2_000_000L;
  /** A chunk needs at least this many natural columns to be compared with its generator. */
  static final int MIN_NATURAL_COLUMNS = 16;

  record ChunkKey(int x, int z) {
    int minX() {
      return x << 4;
    }

    int minZ() {
      return z << 4;
    }
  }

  /** How a current world position looks to the terrain refill. */
  enum Cell {
    /** Air, or a natural block that anything may replace (short grass, snow layer). */
    OPEN,
    /** Natural ground or another natural solid: the column is closed below it. */
    GROUND,
    /** A build, a container or a fluid: never filled, and nothing below it is. */
    BLOCKED
  }

  private AltarRules() {}

  static int renewalRadius(boolean nearArkSite) {
    return nearArkSite ? RENEWAL_ARK_RADIUS : RENEWAL_RADIUS;
  }

  static boolean nearArkSite(int altarX, int altarZ, int siteX, int siteZ) {
    return NatureRestorationRules.horizontalDistance(altarX, altarZ, siteX, siteZ) <= ARK_SITE_REACH;
  }

  /** A column offset from the altar. */
  record Offset(int dx, int dz) {
    int ring() {
      return Math.max(Math.abs(dx), Math.abs(dz));
    }
  }

  /** Every altar covers a square centred on itself: this is its membership test. */
  static boolean inSquare(int dx, int dz, int radius) {
    return Math.abs(dx) <= radius && Math.abs(dz) <= radius;
  }

  /**
   * Every column of the square in concentric square rings from the centre: by ring, then by
   * distance, then x, then z. The order is symmetric: a ring always finishes before the next.
   */
  static List<Offset> square(int radius) {
    List<Offset> result = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++) result.add(new Offset(dx, dz));
    result.sort(Comparator.comparingInt(Offset::ring)
        .thenComparingInt((Offset o) -> o.dx() * o.dx() + o.dz() * o.dz())
        .thenComparingInt(Offset::dx).thenComparingInt(Offset::dz));
    return List.copyOf(result);
  }

  /** Chunks the square touches, nearest first, ties by x then z. */
  static List<ChunkKey> areaChunks(int centerX, int centerZ, int radius) {
    List<ChunkKey> result = new ArrayList<>();
    for (int x = Math.floorDiv(centerX - radius, 16); x <= Math.floorDiv(centerX + radius, 16); x++)
      for (int z = Math.floorDiv(centerZ - radius, 16); z <= Math.floorDiv(centerZ + radius, 16); z++)
        result.add(new ChunkKey(x, z));
    result.sort(Comparator.comparingLong((ChunkKey key) -> chunkDistance(key, centerX, centerZ))
        .thenComparingInt(ChunkKey::x).thenComparingInt(ChunkKey::z));
    return List.copyOf(result);
  }

  /** Squared distance, doubled to stay integral, from a block to a chunk's center. */
  private static long chunkDistance(ChunkKey key, int centerX, int centerZ) {
    long dx = key.x() * 32L + 15 - 2L * centerX;
    long dz = key.z() * 32L + 15 - 2L * centerZ;
    return dx * dx + dz * dz;
  }

  /**
   * Inclusive chunk bounds [minX, minZ, maxX, maxZ] that must be loaded before the altar works:
   * the whole disc, every canopy it may grow and their neighbours.
   */
  static int[] loadedBounds(int centerX, int centerZ, int radius) {
    int reach = radius + TREE_MARGIN + 1;
    return new int[] {Math.floorDiv(centerX - reach, 16), Math.floorDiv(centerZ - reach, 16),
        Math.floorDiv(centerX + reach, 16), Math.floorDiv(centerZ + reach, 16)};
  }

  /**
   * Heights to refill in one column, bottom-up. The column is walked down from the original
   * surface while it stays open to the sky, and must reach existing ground within
   * {@link #FILL_DEPTH}; a build or a fluid on the way stops it with nothing filled. The refill then
   * rises from that ground while the generator had terrain there, so it is always supported:
   * closed caves, flooded or bottomless pits, overhangs and builds are never touched.
   */
  static int[] fillColumn(int referenceTop, IntPredicate referenceTerrain, IntFunction<Cell> current) {
    int floor = Integer.MIN_VALUE;
    for (int y = referenceTop; y >= referenceTop - FILL_DEPTH; y--) {
      Cell cell = current.apply(y);
      if (cell == Cell.BLOCKED) return new int[0];
      if (cell == Cell.GROUND) {
        floor = y;
        break;
      }
    }
    if (floor == Integer.MIN_VALUE) return new int[0];
    int count = 0;
    while (floor + 1 + count <= referenceTop && referenceTerrain.test(floor + 1 + count)) count++;
    int[] result = new int[count];
    for (int i = 0; i < count; i++) result[i] = floor + 1 + i;
    return result;
  }

  /**
   * Whether a chunk still matches what its generator produces. Natural columns are those whose
   * current and original ground tops are both known; agreeing ones are within one block, surplus
   * ones hold natural ground above the original surface. Players dig, so deficits are expected; a
   * different generator shows as surplus ground or too little agreement.
   */
  static boolean consistent(int natural, int agree, int surplus) {
    if (natural < MIN_NATURAL_COLUMNS) return false;
    return surplus * 10L <= natural && agree * 5L >= natural;
  }

  /** Fertilizer items to draw so the stored charge covers a cost. */
  static int fertilizerNeeded(int charge, int cost) {
    if (cost <= charge) return 0;
    return Math.ceilDiv(cost - charge, CHARGE_PER_FERTILIZER);
  }

  static boolean affordable(int charge, int fertilizer, int cost) {
    return fertilizerNeeded(charge, cost) <= fertilizer;
  }

  /** A partial tree repair costs one per block, a whole tree at most {@link #TREE_CHARGE}. */
  static int treeCost(int missingBlocks) {
    return Math.max(1, Math.min(TREE_CHARGE, missingBlocks));
  }

  /** Columns or units an altar may inspect per tick, whether or not they need work. */
  static final int SCANS_PER_TICK = 256;

  /**
   * Work allowed in one tick: block writes, inspected columns and wall time. A unit larger than
   * the whole write budget still runs alone when nothing was written yet this tick, so a big tree
   * is never starved; otherwise every limit applies.
   */
  static final class TickBudget {
    private final LongSupplier clock;
    private final long deadline;
    private final int limit;
    private final int scanLimit;
    private int used;
    private int scanned;

    TickBudget(int ops, long nanos, LongSupplier clock) {
      this(ops, SCANS_PER_TICK, nanos, clock);
    }

    TickBudget(int ops, int scans, long nanos, LongSupplier clock) {
      this.clock = clock;
      this.limit = ops;
      this.scanLimit = scans;
      this.deadline = clock.getAsLong() + nanos;
    }

    static TickBudget standard() {
      return new TickBudget(OPS_PER_TICK, NANOS_PER_TICK, System::nanoTime);
    }

    boolean open() {
      return used < limit && scanned < scanLimit
          && (used == 0 && scanned == 0 || clock.getAsLong() < deadline);
    }

    boolean allows(int cost) {
      if (used == 0) return true;
      return used + cost <= limit && clock.getAsLong() < deadline;
    }

    void spend(int cost) {
      used += Math.max(1, cost);
    }

    /** One column or unit inspected, with or without writes. */
    void scan() {
      scanned++;
    }

    int used() {
      return used;
    }
  }
}
