package dev.entrelumen;

/**
 * Square rings around a center, nearest ring first. The cursor keeps its place, so a search can
 * stop when its tick budget runs out and resume on the next tick.
 */
public final class RingCursor {
  private final int radius;
  private int ring;
  private int index;

  public RingCursor(int radius) {
    if (radius < 0) throw new IllegalArgumentException("radius must be >= 0");
    this.radius = radius;
  }

  /** Ring of the offset returned by the last successful {@link #next}. */
  public int ring() {
    return ring;
  }

  public int radius() {
    return radius;
  }

  /** Writes the next offset into {@code out} as {dx, dz}; false once every ring is exhausted. */
  public boolean next(int[] out) {
    while (ring <= radius) {
      int count = ring == 0 ? 1 : 8 * ring;
      if (index < count) {
        offset(ring, index++, out);
        return true;
      }
      ring++;
      index = 0;
    }
    return false;
  }

  /** Skips the rest of the current ring and every later ring. */
  public void finish() {
    ring = radius + 1;
    index = 0;
  }

  static void offset(int r, int i, int[] out) {
    if (r == 0) {
      out[0] = 0;
      out[1] = 0;
      return;
    }
    int side = 2 * r, edge = i / side, t = i % side;
    switch (edge) {
      case 0 -> {
        out[0] = -r + t;
        out[1] = -r;
      }
      case 1 -> {
        out[0] = r;
        out[1] = -r + t;
      }
      case 2 -> {
        out[0] = r - t;
        out[1] = r;
      }
      default -> {
        out[0] = -r;
        out[1] = r - t;
      }
    }
  }
}
