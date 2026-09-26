package dev.entrelumen;

/**
 * Registry-free rules of the Ark's garden site and the stable per-world numbers the Altar of
 * Renewal plants by. Column choice depends only on world seed and position, so a retry after a
 * finished pass finds nothing new instead of adding a second layer of growth.
 */
final class NatureRestorationRules {
  /** Farthest a team's garden site may be from its Nature Module. */
  static final int MAX_DISTANCE = 128;

  private NatureRestorationRules() {}

  /** Rounded-up horizontal distance, so a site at 128.4 blocks is refused. */
  static int horizontalDistance(int fromX, int fromZ, int toX, int toZ) {
    double dx = (double) toX - fromX;
    double dz = (double) toZ - fromZ;
    return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.sqrt(dx * dx + dz * dz)));
  }

  static boolean withinReach(int fromX, int fromZ, int toX, int toZ) {
    return horizontalDistance(fromX, fromZ, toX, toZ) <= MAX_DISTANCE;
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
}
