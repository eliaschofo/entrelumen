package dev.entrelumen;

import java.util.*;

/**
 * How a Heliodor ruin meets the terrain (docs/design/heliodor-ruins.md, "El terreno"): pure rules,
 * unit-tested; {@link RuinPlacement} feeds the template's columns and the site's heights in.
 *
 * <ul>
 *   <li>The columns that stand on the template's ground layer split into platforms (floors, plinths,
 *       lawns) and thin supports (piers, pilasters, walls): what survives an opening with a square of
 *       side {@code 2 * PLATFORM_RADIUS + 1} is platform.
 *   <li>Around the platforms the natural surface blends to the ruin's ground level over a margin of
 *       {@link #MIN_MARGIN} to {@link #MAX_MARGIN} blocks, with a smooth falloff: filled up or carved down.
 *   <li>A thin support that hangs above the ground goes down in its own block, up to {@link #MAX_PIER}.
 *   <li>A fill that drops two or more blocks to a neighbouring column shows the ruin's masonry on that
 *       face: the foundation is terraced, never a sheer wall of earth.
 *   <li>Soil in a template is a placeholder for the site's own ground, except inside {@code keep_soil}.
 * </ul>
 */
public final class RuinTerrain {
  private RuinTerrain() {}

  public static final int MIN_MARGIN = 6, MAX_MARGIN = 10;
  /**
   * How far a thin support goes down to find the ground, and how deep a platform's fill may be: ground
   * farther down is a chasm the ruin spans, not a dip it fills.
   */
  public static final int MAX_PIER = 32;
  /** Land more than this above or below the ruin's ground level is a cliff or a chasm: the blend leaves it. */
  public static final int MAX_BLEND = 32;
  /** Air kept above the highest block of a ruin column that does not stand on a platform. */
  public static final int HEADROOM = 3;
  /** Half the side of the square whose opening separates platforms from thin supports. */
  public static final int PLATFORM_RADIUS = 2;
  /** Template blocks that stand for the site's ground (Elias, 26 September). */
  public static final Set<String> SOIL = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
      "minecraft:podzol", "minecraft:moss_block");

  public static boolean soil(String block) {
    return SOIL.contains(block);
  }

  /**
   * The part of a {@code w} by {@code d} mask (row-major: index {@code z * w + x}) that an opening with
   * a square of side {@code 2r + 1} keeps: an erosion followed by a dilation. Cells outside the grid
   * count as unset, so a platform's edge is kept only where the platform is wide enough.
   */
  public static boolean[] opening(boolean[] mask, int w, int d, int r) {
    boolean[] eroded = new boolean[mask.length];
    for (int z = 0; z < d; z++)
      for (int x = 0; x < w; x++) {
        boolean all = true;
        for (int dz = -r; dz <= r && all; dz++)
          for (int dx = -r; dx <= r && all; dx++) {
            int nx = x + dx, nz = z + dz;
            all = nx >= 0 && nz >= 0 && nx < w && nz < d && mask[nz * w + nx];
          }
        eroded[z * w + x] = all;
      }
    boolean[] out = new boolean[mask.length];
    for (int z = 0; z < d; z++)
      for (int x = 0; x < w; x++) {
        if (!eroded[z * w + x]) continue;
        for (int dz = -r; dz <= r; dz++)
          for (int dx = -r; dx <= r; dx++) {
            int nx = x + dx, nz = z + dz;
            if (nx >= 0 && nz >= 0 && nx < w && nz < d) out[nz * w + nx] = true;
          }
      }
    return out;
  }

  /**
   * The distance from every column of the grid grown by {@code m} on each side to the nearest set cell
   * of the {@code w} by {@code d} mask: 0 on the mask, {@link Float#POSITIVE_INFINITY} beyond {@code m}
   * or when the mask is empty. The grown grid is row-major with width {@code w + 2m}: index
   * {@code (z + m) * (w + 2m) + (x + m)} for {@code x} in {@code -m .. w + m - 1}.
   */
  public static float[] distance(boolean[] mask, int w, int d, int m) {
    int gw = w + 2 * m, gd = d + 2 * m;
    float[] out = new float[gw * gd];
    for (int gz = 0; gz < gd; gz++)
      for (int gx = 0; gx < gw; gx++) {
        int x = gx - m, z = gz - m;
        if (x >= 0 && z >= 0 && x < w && z < d && mask[z * w + x]) continue;
        double best = Double.POSITIVE_INFINITY;
        for (int nz = Math.max(0, z - m); nz <= Math.min(d - 1, z + m); nz++)
          for (int nx = Math.max(0, x - m); nx <= Math.min(w - 1, x + m); nx++)
            if (mask[nz * w + nx]) best = Math.min(best, Math.hypot(nx - x, nz - z));
        out[gz * gw + gx] = best <= m ? (float) best : Float.POSITIVE_INFINITY;
      }
    return out;
  }

  /**
   * The blending margin for the largest height difference at a platform's rim: wide enough for a slope
   * of about one block per block, within {@link #MIN_MARGIN}..{@link #MAX_MARGIN}.
   */
  public static int margin(int rimDelta) {
    return Math.clamp((int) Math.ceil(Math.abs(rimDelta) * 1.5), MIN_MARGIN, MAX_MARGIN);
  }

  /** The smooth falloff: 0 at the platform, 1 at the margin's outer edge, flat at both ends. */
  public static double weight(double t) {
    t = Math.clamp(t, 0.0, 1.0);
    return t * t * (3 - 2 * t);
  }

  /**
   * The blended surface of a column {@code distance} away from the platform: the ruin's {@code ground}
   * on the platform, the {@code natural} surface from the margin on, a smooth blend between. Land more
   * than {@link #MAX_BLEND} away from the ground level stays as it is.
   */
  public static int target(int natural, int ground, double distance, int margin) {
    if (distance <= 0) return ground;
    if (margin <= 0 || distance >= margin || !blends(natural, ground)) return natural;
    return ground + (int) Math.round((natural - ground) * weight(distance / margin));
  }

  /** Whether land at {@code natural} is near enough to the ground level to blend (not a cliff or a chasm). */
  public static boolean blends(int natural, int ground) {
    return Math.abs(natural - ground) <= MAX_BLEND;
  }

  /** Whether a platform whose floor is at {@code floor} fills down to land at {@code natural} (not a chasm). */
  public static boolean fills(int natural, int floor) {
    return floor - natural <= MAX_PIER + 1;
  }

  /**
   * Whether the filled cell at {@code y} of a column whose new surface is {@code top} shows as a
   * terrace face: its lowest neighbouring surface lies two or more blocks below and the cell above it.
   */
  public static boolean riser(int y, int top, int lowestNeighbour) {
    return top - lowestNeighbour >= 2 && y > lowestNeighbour;
  }

  /** The median of some heights: the ground level that balances what is filled and what is carved. */
  public static int median(int[] heights) {
    if (heights.length == 0) throw new IllegalArgumentException("no heights");
    int[] sorted = heights.clone();
    Arrays.sort(sorted);
    return sorted[(sorted.length - 1) / 2];
  }

  /** The most common sample, the first seen on a tie; {@code fallback} when there is none. */
  public static <T> T mostCommon(List<T> samples, T fallback) {
    Map<T, Integer> counts = new LinkedHashMap<>();
    for (T sample : samples) counts.merge(sample, 1, Integer::sum);
    T best = fallback;
    int seen = 0;
    for (var entry : counts.entrySet())
      if (entry.getValue() > seen) {
        seen = entry.getValue();
        best = entry.getKey();
      }
    return best;
  }

  // ---- The site's rock ------------------------------------------------------------------------

  /**
   * How deep under a sampled column the rock is read, how many rock blocks it gives at most, how many
   * rocks a site offers (one per placeholder slot) and the share of the samples a rock needs to be one.
   */
  public static final int ROCK_DEPTH = 12, ROCKS_PER_COLUMN = 3, ROCK_SLOTS = 3;
  public static final double ROCK_SHARE = 0.05;

  /**
   * The site's rocks, commonest first: those that make at least {@link #ROCK_SHARE} of the samples, at
   * most {@link #ROCK_SLOTS}; {@code fallback} alone when nothing was sampled. Ties keep the order in
   * which the samples came.
   */
  public static <T> List<T> rocks(List<T> samples, T fallback) {
    if (samples.isEmpty()) return List.of(fallback);
    Map<T, Integer> counts = new LinkedHashMap<>();
    for (T sample : samples) counts.merge(sample, 1, Integer::sum);
    List<Map.Entry<T, Integer>> sorted = new ArrayList<>(counts.entrySet());
    sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
    List<T> rocks = new ArrayList<>();
    for (var entry : sorted) {
      if (!rocks.isEmpty() && entry.getValue() < ROCK_SHARE * samples.size()) break;
      rocks.add(entry.getKey());
      if (rocks.size() == ROCK_SLOTS) break;
    }
    return List.copyOf(rocks);
  }

  /** The site's rock for a template's {@code slot}-th placeholder: slots past the site's rocks wrap. */
  public static <T> T rock(List<T> rocks, int slot) {
    return rocks.get(Math.floorMod(slot, rocks.size()));
  }

  /** Whether a template cell lies in one of the {@code keep_soil} boxes. */
  public static boolean kept(int x, int y, int z, List<ProtectionRules.Box> boxes) {
    for (var box : boxes) if (box.contains(x, y, z)) return true;
    return false;
  }
}
