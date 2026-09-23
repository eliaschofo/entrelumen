package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry-free restoration rules. Column choice depends only on world seed and position, so a
 * retry after a finished pass finds nothing new instead of adding a second layer of growth.
 */
final class NatureRestorationRules {
  static final int RADIUS = 8;
  /** Horizontal reach of a native canopy beyond its trunk; the loaded and simulated area. */
  static final int TREE_MARGIN = 8;
  static final int VERTICAL_WINDOW = 12;
  static final int MAX_DISTANCE = 128;
  static final int TREE_BONE_MEAL = 4;
  static final int MAX_TREES = 16;
  static final double FLORA_DENSITY = 1.0 / 3.0;
  static final double FLOWER_SHARE = 1.0 / 8.0;
  static final long TREE_SALT = 0x6E61747572655F74L;
  static final long FLORA_SALT = 0x6E61747572655F66L;
  static final long FLOWER_SALT = 0x6E61747572655F62L;
  static final long SPECIES_SALT = 0x6E61747572655F73L;

  record Column(int dx, int dz) {
    int distanceSquared() {
      return dx * dx + dz * dz;
    }
  }

  private static final List<Column> COLUMNS = columns(RADIUS);

  private NatureRestorationRules() {}

  /** Every column in the disc, nearest first; ties are broken by x then z for a stable order. */
  static List<Column> columns(int radius) {
    List<Column> result = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++)
        if (dx * dx + dz * dz <= radius * radius) result.add(new Column(dx, dz));
    result.sort(Comparator.comparingInt(Column::distanceSquared)
        .thenComparingInt(Column::dx).thenComparingInt(Column::dz));
    return List.copyOf(result);
  }

  static List<Column> columns() {
    return COLUMNS;
  }

  /** Rounded-up horizontal distance, so a site at 128.4 blocks is refused. */
  static int horizontalDistance(int fromX, int fromZ, int toX, int toZ) {
    double dx = (double) toX - fromX;
    double dz = (double) toZ - fromZ;
    return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.sqrt(dx * dx + dz * dz)));
  }

  static boolean withinReach(int fromX, int fromZ, int toX, int toZ) {
    return horizontalDistance(fromX, fromZ, toX, toZ) <= MAX_DISTANCE;
  }

  /** Blocks a simulated canopy may occupy around the site center. */
  static int writeReach() {
    return RADIUS + TREE_MARGIN;
  }

  /**
   * Inclusive chunk coordinates [minX, minZ, maxX, maxZ] that must already be loaded: every
   * permitted write plus the one-block neighbourhood inspected around it.
   */
  static int[] chunkBounds(int centerX, int centerZ) {
    int reach = writeReach() + 1;
    return new int[] {Math.floorDiv(centerX - reach, 16), Math.floorDiv(centerZ - reach, 16),
        Math.floorDiv(centerX + reach, 16), Math.floorDiv(centerZ + reach, 16)};
  }

  static boolean withinWindow(int groundY, int siteY) {
    return Math.abs((long) groundY - siteY) <= VERTICAL_WINDOW;
  }

  static long mix(long seed, int x, int z, long salt) {
    long h = seed ^ salt;
    h ^= x * 0x9E3779B97F4A7C15L;
    h = Long.rotateLeft(h, 27) * 0xBF58476D1CE4E5B9L;
    h ^= z * 0xC2B2AE3D27D4EB4FL;
    h = Long.rotateLeft(h, 31) * 0x94D049BB133111EBL;
    h ^= h >>> 29;
    h *= 0xBF58476D1CE4E5B9L;
    return h ^ (h >>> 32);
  }

  /** A stable number in [0, 1) for this world, column and purpose. */
  static double unit(long seed, int x, int z, long salt) {
    return (mix(seed, x, z, salt) >>> 11) * 0x1.0p-53;
  }

  static boolean selected(long seed, int x, int z, long salt, double probability) {
    return probability > 0 && unit(seed, x, z, salt) < probability;
  }

  /** Native tree placements per chunk spread over its 256 columns. */
  static double treeProbability(double treesPerChunk) {
    if (!(treesPerChunk > 0)) return 0;
    return Math.min(1.0, treesPerChunk / 256.0);
  }

  /** Index of a weighted option chosen by the stable column number; -1 when there is no weight. */
  static int weightedIndex(double[] weights, double roll) {
    double total = 0;
    for (double weight : weights) if (weight > 0) total += weight;
    if (!(total > 0)) return -1;
    double target = roll * total;
    double seen = 0;
    int last = -1;
    for (int i = 0; i < weights.length; i++) {
      if (!(weights[i] > 0)) continue;
      last = i;
      seen += weights[i];
      if (target < seen) return i;
    }
    return last;
  }

  /** The cover most represented at the site; grass wins ties and bare sites. */
  static int dominantCover(int grass, int podzol, int mycelium) {
    if (podzol > grass && podzol >= mycelium) return 1;
    if (mycelium > grass && mycelium > podzol) return 2;
    return 0;
  }
}
