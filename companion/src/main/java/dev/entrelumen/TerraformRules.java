package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Registry-free rules of the Terraform Altar: the flat square and its blending band, the target
 * height of each column, the layers a filled column receives and the conservation ledger.
 */
final class TerraformRules {
  /** Half-widths of the flat square the altar cycles through. */
  static final int[] SIZES = {4, 8, 12, 16};
  static final int DEFAULT_SIZE = 8;
  /** Width of the slope that blends the flat square into the land around it. */
  static final int BAND = 4;
  /** Layers of subsurface material under the surface block. */
  static final int SUBSURFACE_DEPTH = 3;
  /** Highest natural block above the target that a column may cut. */
  static final int MAX_CUT = 32;
  /** Deepest hole below the target that a column may fill; deeper columns are left alone. */
  static final int MAX_FILL = 24;
  /** Block operations per altar per tick. */
  static final int OPS_PER_TICK = 48;
  /** Preview time after a first use, in ticks. */
  static final int PREVIEW_TICKS = 200;
  static final int UNKNOWN = Integer.MIN_VALUE;

  enum Layer { SURFACE, SUBSURFACE, DEEP }

  /** What a world position means to a terraform column. */
  enum Cell {
    AIR,
    /** Natural and not ground: plants, snow layers, logs, leaves. Cut when in the way. */
    LOOSE,
    /** Natural ground: dirt, sand, stone, ores, gravel, clay. */
    GROUND,
    /** Builds, containers and anything placed: the whole column is left alone. */
    BUILT,
    /** Fluid sources: the whole column is left alone. */
    FLUID
  }

  enum Kind { CUT, SWAP, FILL }

  record Op(Kind kind, int y, Layer layer) {}

  /** A column's operations in execution order, or a refusal with its reason. */
  record Plan(List<Op> ops, String refusal) {
    static Plan refused(String reason) {
      return new Plan(List.of(), reason);
    }

    boolean refused() {
      return refusal != null;
    }
  }

  record Column(int dx, int dz) {
    int ring() {
      return Math.max(Math.abs(dx), Math.abs(dz));
    }
  }

  private TerraformRules() {}

  static int nextSize(int size) {
    for (int i = 0; i < SIZES.length; i++)
      if (SIZES[i] == size) return SIZES[(i + 1) % SIZES.length];
    return DEFAULT_SIZE;
  }

  static boolean validSize(int size) {
    for (int value : SIZES) if (value == size) return true;
    return false;
  }

  /** Half-width of the whole affected square, slope included. */
  static int reach(int size) {
    return size + BAND;
  }

  /** Every affected column, center outwards: by ring, then distance, then x, then z. */
  static List<Column> columns(int size) {
    int reach = reach(size);
    List<Column> result = new ArrayList<>();
    for (int dx = -reach; dx <= reach; dx++)
      for (int dz = -reach; dz <= reach; dz++) result.add(new Column(dx, dz));
    result.sort(Comparator.comparingInt(Column::ring)
        .thenComparingInt((Column c) -> c.dx() * c.dx() + c.dz() * c.dz())
        .thenComparingInt(Column::dx).thenComparingInt(Column::dz));
    return List.copyOf(result);
  }

  /** Columns just outside the affected square: the untouched land the slope and palette follow. */
  static List<Column> outerRing(int size) {
    int ring = reach(size) + 1;
    List<Column> result = new ArrayList<>();
    for (int dx = -ring; dx <= ring; dx++)
      for (int dz = -ring; dz <= ring; dz++)
        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) result.add(new Column(dx, dz));
    return List.copyOf(result);
  }

  /** Smoothstep: flat at both ends of the slope so it meets both surfaces without a crease. */
  static double smooth(double t) {
    double clamped = Math.max(0, Math.min(1, t));
    return clamped * clamped * (3 - 2 * clamped);
  }

  /**
   * Target surface height of a column. The flat square sits at {@code flatTop}; in the band the
   * height eases towards the untouched land on the outer ring, projected outwards from the altar.
   * Unknown outer land (built or unloaded) keeps the flat height.
   */
  static int target(int dx, int dz, int size, int flatTop, OuterHeight outer) {
    int ring = Math.max(Math.abs(dx), Math.abs(dz));
    if (ring <= size) return flatTop;
    int outerRing = reach(size) + 1;
    int ox = roundAway(dx * (double) outerRing / ring);
    int oz = roundAway(dz * (double) outerRing / ring);
    int land = outer.height(ox, oz);
    if (land == UNKNOWN) return flatTop;
    double t = (ring - size) / (double) (BAND + 1);
    return flatTop + (int) Math.round((land - flatTop) * smooth(t));
  }

  /** Rounds half away from zero, so mirrored columns project onto mirrored ring columns. */
  static int roundAway(double value) {
    return (int) (Math.signum(value) * Math.round(Math.abs(value)));
  }

  @FunctionalInterface
  interface OuterHeight {
    /** Current ground top at an offset on the outer ring, or {@link #UNKNOWN}. */
    int height(int dx, int dz);
  }

  static Layer layer(int depth) {
    if (depth <= 0) return Layer.SURFACE;
    return depth <= SUBSURFACE_DEPTH ? Layer.SUBSURFACE : Layer.DEEP;
  }

  /**
   * Plans one column. Everything natural above the target is cut, top-down; loose plants in a hole
   * are cleared; the hole is filled bottom-up with surface, subsurface and deep layers; natural
   * ground in the top layers that does not match its layer is swapped. Builds and fluid sources
   * anywhere in the touched span, land taller than {@link #MAX_CUT} and holes deeper than
   * {@link #MAX_FILL} refuse the whole column.
   *
   * @param matches whether the ground at a height already has the right material for its layer
   */
  static Plan column(int target, IntFunction<Cell> cell, IntPredicate matches) {
    int top = target + MAX_CUT;
    int bottom = target - MAX_FILL;
    Cell above = cell.apply(top + 1);
    if (above == Cell.GROUND || above == Cell.LOOSE) return Plan.refused("too_tall");
    int ground = bottom - 1;
    for (int y = target; y >= bottom; y--)
      if (cell.apply(y) == Cell.GROUND) {
        ground = y;
        break;
      }
    if (ground < bottom) return Plan.refused("too_deep");
    int lowest = Math.min(ground, target - SUBSURFACE_DEPTH);
    for (int y = lowest; y <= top; y++) {
      Cell at = cell.apply(y);
      if (at == Cell.BUILT) return Plan.refused("built");
      if (at == Cell.FLUID) return Plan.refused("fluid");
    }
    List<Op> ops = new ArrayList<>();
    for (int y = top; y > target; y--) {
      Cell at = cell.apply(y);
      if (at == Cell.GROUND || at == Cell.LOOSE) ops.add(new Op(Kind.CUT, y, null));
    }
    for (int y = target; y > ground; y--)
      if (cell.apply(y) == Cell.LOOSE) ops.add(new Op(Kind.CUT, y, null));
    for (int y = Math.max(target - SUBSURFACE_DEPTH, lowest); y <= Math.min(ground, target); y++)
      if (cell.apply(y) == Cell.GROUND && !matches.test(y))
        ops.add(new Op(Kind.SWAP, y, layer(target - y)));
    for (int y = ground + 1; y <= target; y++) ops.add(new Op(Kind.FILL, y, layer(target - y)));
    return new Plan(List.copyOf(ops), null);
  }

  /**
   * Conservation ledger for materials: cut blocks are deposited, filled blocks withdrawn. Nothing
   * is created or destroyed; a withdrawal names the first available material from a preference.
   */
  static final class Ledger {
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    void deposit(String id, int count) {
      if (count < 0) throw new IllegalArgumentException("Negative deposit");
      if (count > 0) counts.merge(id, count, Integer::sum);
    }

    /** Takes one of the first material in the preference that is present; null if none is. */
    String withdraw(List<String> preference) {
      for (String id : preference) {
        int have = counts.getOrDefault(id, 0);
        if (have > 0) {
          if (have == 1) counts.remove(id);
          else counts.put(id, have - 1);
          return id;
        }
      }
      return null;
    }

    int count(String id) {
      return counts.getOrDefault(id, 0);
    }

    int total() {
      int total = 0;
      for (int value : counts.values()) total += value;
      return total;
    }

    Map<String, Integer> snapshot() {
      return Map.copyOf(counts);
    }
  }
}
