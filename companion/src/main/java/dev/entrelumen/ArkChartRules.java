package dev.entrelumen;

import java.util.List;
import java.util.Objects;

/**
 * Registry-free arithmetic for the Ark chart room. It follows vanilla filled-map geometry: a
 * 128 by 128 grid whose pixels each cover 2^scale blocks per side, with 0 meaning unexplored.
 * Absorption only fills unexplored target pixels, so repeating it adds nothing.
 */
final class ArkChartRules {
  static final int SIZE = 128;
  static final int PIXELS = SIZE * SIZE;
  static final int MAX_SCALE = 4;

  record Grid(int centerX, int centerZ, int scale) {
    Grid {
      if (scale < 0 || scale > MAX_SCALE) throw new IllegalArgumentException("Map scale " + scale);
    }

    int step() {
      return 1 << scale;
    }

    /** First block of pixel 0, exactly as MapItem.update computes it (integer division). */
    long originX() {
      return (long) (centerX / step() - SIZE / 2) * step();
    }

    long originZ() {
      return (long) (centerZ / step() - SIZE / 2) * step();
    }

    long extent() {
      return (long) SIZE * step();
    }
  }

  enum Fit { FITS, COARSER, OUTSIDE }

  record Chart(Grid grid, byte[] colors) {
    Chart {
      Objects.requireNonNull(grid);
      if (colors.length != PIXELS) throw new IllegalArgumentException("A map holds 16384 pixels");
    }
  }

  /** New target colours, the number of pixels filled and which sources supplied any of them. */
  record Absorption(byte[] colors, int filled, boolean[] contributed) {
    int contributors() {
      int count = 0;
      for (boolean value : contributed) if (value) count++;
      return count;
    }
  }

  private ArkChartRules() {}

  /** A source can add detail only at the same or a finer scale and only where it overlaps. */
  static Fit fit(Grid target, Grid source) {
    if (source.scale() > target.scale()) return Fit.COARSER;
    if (!overlaps(target.originX(), target.extent(), source.originX(), source.extent())
        || !overlaps(target.originZ(), target.extent(), source.originZ(), source.extent()))
      return Fit.OUTSIDE;
    return Fit.FITS;
  }

  private static boolean overlaps(long start, long length, long otherStart, long otherLength) {
    return start < otherStart + otherLength && otherStart < start + length;
  }

  /**
   * Each unexplored target pixel takes the most common explored colour among the source cells
   * that start inside it, weighted by the area each cell covers; ties go to the lowest colour id.
   * The result does not depend on source order. Explored target pixels are never replaced.
   */
  static Absorption absorb(Chart target, List<Chart> sources) {
    Grid grid = target.grid();
    int step = grid.step();
    byte[] result = target.colors().clone();
    boolean[] contributed = new boolean[sources.size()];
    boolean[] fitting = new boolean[sources.size()];
    for (int s = 0; s < sources.size(); s++) fitting[s] = fit(grid, sources.get(s).grid()) == Fit.FITS;
    long[] weights = new long[256];
    int[] touched = new int[256];
    int[] voters = new int[sources.size()];
    int filled = 0;
    for (int tz = 0; tz < SIZE; tz++) {
      long z0 = grid.originZ() + (long) tz * step;
      for (int tx = 0; tx < SIZE; tx++) {
        int index = tx + tz * SIZE;
        if (result[index] != 0) continue;
        long x0 = grid.originX() + (long) tx * step;
        int colours = 0;
        int voterCount = 0;
        for (int s = 0; s < sources.size(); s++) {
          if (!fitting[s]) continue;
          Chart source = sources.get(s);
          Grid cells = source.grid();
          long cell = cells.step();
          int sx0 = firstCell(x0 - cells.originX(), cell);
          int sx1 = firstCell(x0 + step - cells.originX(), cell);
          int sz0 = firstCell(z0 - cells.originZ(), cell);
          int sz1 = firstCell(z0 + step - cells.originZ(), cell);
          boolean voted = false;
          for (int sz = sz0; sz < sz1; sz++)
            for (int sx = sx0; sx < sx1; sx++) {
              int colour = source.colors()[sx + sz * SIZE] & 0xFF;
              if (colour == 0) continue;
              if (weights[colour] == 0) touched[colours++] = colour;
              weights[colour] += cell * cell;
              voted = true;
            }
          if (voted) voters[voterCount++] = s;
        }
        if (colours == 0) continue;
        int best = touched[0];
        for (int i = 1; i < colours; i++) {
          int colour = touched[i];
          if (weights[colour] > weights[best] || weights[colour] == weights[best] && colour < best)
            best = colour;
        }
        for (int i = 0; i < colours; i++) weights[touched[i]] = 0;
        result[index] = (byte) best;
        filled++;
        for (int i = 0; i < voterCount; i++) contributed[voters[i]] = true;
      }
    }
    return new Absorption(result, filled, contributed);
  }

  /** Index of the first cell whose start lies at or after offset, clamped to the grid. */
  private static int firstCell(long offset, long cell) {
    return (int) Math.clamp(Math.ceilDiv(offset, cell), 0, SIZE);
  }
}
