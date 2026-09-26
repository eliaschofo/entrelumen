package dev.entrelumen;

/**
 * Where the Envés puts things, in block coordinates of {@code entrelumen:enves}. Pure.
 *
 * <p>Every attempt owns a slot; slots sit {@link #SLOT_SPACING} blocks apart on a row-major grid,
 * so a slot's floors ({@link EnvesLayout#W} × {@link #CELL} = 209 blocks a side) never meet
 * another's. A descent's floors are stacked: floor {@code d} (0 is the vestibule above floor I,
 * 1–5 the floors) has its floor layer at {@link #floorY}, one template ({@link #FLOOR_H} blocks)
 * below the floor above, so floor d+1's ceiling sits right under floor d's slab. Cell (x, z) of a
 * floor spans {@code [origin, origin + CELL)} on both axes from {@link #cellOrigin}.
 */
public final class EnvesGeometry {
  /** Template edge and height; the cell centre is {@link #C} on both axes. */
  public static final int CELL = 19, FLOOR_H = 12, C = 9;
  public static final int SLOT_SPACING = 2048, SLOTS_PER_ROW = 64, MAX_SLOTS = SLOTS_PER_ROW * SLOTS_PER_ROW;
  /** The vestibule's floor layer; floor V's is 60 blocks lower. */
  public static final int TOP_Y = 88;
  /** Depths: 0 the vestibule, 1..5 the floors. */
  public static final int VESTIBULE = 0, DEPTHS = EnvesLayout.FLOORS + 1;
  /** Below this a player has left every floor: the void guard brings them back. */
  public static final int VOID_Y = TOP_Y - FLOOR_H * EnvesLayout.FLOORS - 16;

  private EnvesGeometry() {}

  public static int floorY(int depth) {
    return TOP_Y - FLOOR_H * depth;
  }

  public static int slotX(int slot) {
    return (slot % SLOTS_PER_ROW) * SLOT_SPACING;
  }

  public static int slotZ(int slot) {
    return (slot / SLOTS_PER_ROW) * SLOT_SPACING;
  }

  /** Lowest corner of a cell's template: {x, y, z}. */
  public static int[] cellOrigin(int slot, int depth, int cell) {
    return new int[] {slotX(slot) + EnvesLayout.x(cell) * CELL, floorY(depth), slotZ(slot) + EnvesLayout.z(cell) * CELL};
  }

  /** The slot a column belongs to, or -1 between slots. */
  public static int slotAt(int x, int z) {
    if (x < 0 || z < 0) return -1;
    int sx = x / SLOT_SPACING, sz = z / SLOT_SPACING;
    if (sx >= SLOTS_PER_ROW || sz >= SLOTS_PER_ROW) return -1;
    int lx = x - sx * SLOT_SPACING, lz = z - sz * SLOT_SPACING;
    if (lx >= EnvesLayout.W * CELL || lz >= EnvesLayout.H * CELL) return -1;
    return sz * SLOTS_PER_ROW + sx;
  }

  /** The cell of a column inside a slot, or -1. */
  public static int cellAt(int slot, int x, int z) {
    int lx = x - slotX(slot), lz = z - slotZ(slot);
    if (lx < 0 || lz < 0 || lx >= EnvesLayout.W * CELL || lz >= EnvesLayout.H * CELL) return -1;
    return EnvesLayout.index(lx / CELL, lz / CELL);
  }

  /** The depth whose template band holds block height {@code y}, or -1 above or below all floors. */
  public static int depthAt(int y) {
    int top = floorY(VESTIBULE) + FLOOR_H - 1;
    if (y > top || y < floorY(EnvesLayout.FLOORS)) return -1;
    return (top - y) / FLOOR_H;
  }

  /** Whether a column lies inside a cell's walls (not on its outer ring). */
  public static boolean interior(int slot, int x, int z) {
    int lx = Math.floorMod(x - slotX(slot), CELL), lz = Math.floorMod(z - slotZ(slot), CELL);
    return lx > 0 && lz > 0 && lx < CELL - 1 && lz < CELL - 1;
  }

  // ---- The stairwell (art/dungeon/tiles.py: RING, ring_step) ------------------------------------

  /**
   * The stairwell's ring, template-local {x, z}: a 5×5 ring round a 3×3 core, one clockwise turn from
   * the north-west corner. Corners (every fourth cell) are landings; the three cells of each side are
   * stairs facing back up. Each side drops 3 blocks, the turn drops {@link #FLOOR_H}: the exit cell
   * holds the top of the turn and the next floor's start cell, right below, the rest.
   */
  public static final int[][] RING = ring();

  private static int[][] ring() {
    int[][] out = new int[16][];
    for (int i = 0; i < 4; i++) {
      out[i] = new int[] {C - 2 + i, C - 2};
      out[4 + i] = new int[] {C + 2, C - 2 + i};
      out[8 + i] = new int[] {C + 2 - i, C + 2};
      out[12 + i] = new int[] {C - 2, C + 2 - i};
    }
    return out;
  }

  /** Whether ring cell {@code p} (0..16, 16 being corner 0 one floor down) is a landing. */
  public static boolean landing(int p) {
    return p % 4 == 0;
  }

  /**
   * Height of ring cell {@code p}'s block, relative to the upper floor's floor layer: landings at
   * 0, -3, -6, -9, -12, stairs one to three below the landing before them.
   */
  public static int ringY(int p) {
    int k = p / 4, j = p % 4;
    return j == 0 ? -3 * k : 1 - 3 * k - j;
  }

  /** The horizontal direction a ring stair's high side faces (back up the turn): west, north, east, south. */
  public static int ringSide(int p) {
    return (p / 4) % 4;
  }
}
