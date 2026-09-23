package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ArkChartRulesTest {
  private static final int SIZE = ArkChartRules.SIZE;

  /** The centre MapItemSavedData.createFresh snaps a new map to at this scale. */
  private static ArkChartRules.Grid fresh(double x, double z, int scale) {
    int size = 128 * (1 << scale);
    int cx = (int) Math.floor((x + 64.0) / size) * size + size / 2 - 64;
    int cz = (int) Math.floor((z + 64.0) / size) * size + size / 2 - 64;
    return new ArkChartRules.Grid(cx, cz, scale);
  }

  private static ArkChartRules.Chart chart(ArkChartRules.Grid grid, byte fill) {
    byte[] colors = new byte[ArkChartRules.PIXELS];
    Arrays.fill(colors, fill);
    return new ArkChartRules.Chart(grid, colors);
  }

  private static void set(ArkChartRules.Chart chart, int x, int z, int colour) {
    chart.colors()[x + z * SIZE] = (byte) colour;
  }

  private static int at(byte[] colors, int x, int z) {
    return colors[x + z * SIZE] & 0xFF;
  }

  @Test
  void gridFollowsVanillaMapGeometry() {
    for (int scale = 0; scale <= ArkChartRules.MAX_SCALE; scale++)
      for (int x : new int[] {-30_000_000, -1000, -65, -64, -1, 0, 63, 64, 1000, 12_345, 29_999_999}) {
        var grid = fresh(x, -x, scale);
        long size = 128L << scale;
        // Pixel 0 starts on the snapped tile boundary, as MapItem.update draws it.
        assertEquals(Math.floorDiv(x + 64L, size) * size - 64, grid.originX());
        assertEquals(Math.floorDiv(-x + 64L, size) * size - 64, grid.originZ());
        long pixel = (x - grid.originX()) / grid.step();
        assertTrue(pixel >= 0 && pixel < SIZE, "Block outside its own map at scale " + scale);
      }
    assertThrows(IllegalArgumentException.class, () -> new ArkChartRules.Grid(0, 0, 5));
    assertThrows(IllegalArgumentException.class, () -> new ArkChartRules.Grid(0, 0, -1));
    assertThrows(IllegalArgumentException.class,
        () -> new ArkChartRules.Chart(new ArkChartRules.Grid(0, 0, 0), new byte[10]));
  }

  @Test
  void sameScaleFillsOnlyBlankPixelsAndRepeatsAsNoOp() {
    var grid = fresh(0, 0, 0);
    var target = chart(grid, (byte) 0);
    set(target, 3, 4, 41);
    set(target, 127, 127, 9);
    var source = chart(grid, (byte) 18);
    set(source, 5, 5, 0);
    var first = ArkChartRules.absorb(target, List.of(source));
    assertEquals(ArkChartRules.PIXELS - 3, first.filled());
    assertEquals(41, at(first.colors(), 3, 4), "An explored pixel was replaced");
    assertEquals(9, at(first.colors(), 127, 127));
    assertEquals(0, at(first.colors(), 5, 5), "An unexplored source pixel was invented");
    assertEquals(18, at(first.colors(), 0, 0));
    assertArrayEquals(new boolean[] {true}, first.contributed());
    assertEquals(0, at(target.colors(), 0, 0), "Absorption mutated its input");
    var again = ArkChartRules.absorb(new ArkChartRules.Chart(grid, first.colors()), List.of(source));
    assertEquals(0, again.filled());
    assertArrayEquals(first.colors(), again.colors());
    assertEquals(0, again.contributors());
  }

  @Test
  void finerSourcesDownsampleByAreaWeightedModeWithLowestTie() {
    var target = chart(fresh(0, 0, 1), (byte) 0);
    // The four scale-0 tiles that make up this scale-1 map.
    var north = fresh(0, 0, 0);
    assertEquals(target.grid().originX(), north.originX());
    var northwest = chart(north, (byte) 0);
    // Target pixel (0,0) covers source pixels (0..1, 0..1).
    set(northwest, 0, 0, 20); set(northwest, 1, 0, 20); set(northwest, 0, 1, 30);
    // (1,0): one 20, two 30, one 40 -> 30.
    set(northwest, 2, 0, 20); set(northwest, 3, 0, 30); set(northwest, 2, 1, 30); set(northwest, 3, 1, 40);
    // (2,0): a one-to-one tie resolves to the lower colour id.
    set(northwest, 4, 0, 90); set(northwest, 5, 1, 70);
    // (3,0): nothing explored stays blank.
    var southeast = chart(fresh(128, 128, 0), (byte) 12);
    var result = ArkChartRules.absorb(target, List.of(northwest, southeast));
    assertEquals(20, at(result.colors(), 0, 0));
    assertEquals(30, at(result.colors(), 1, 0));
    assertEquals(70, at(result.colors(), 2, 0));
    assertEquals(0, at(result.colors(), 3, 0));
    for (int x = 64; x < SIZE; x++)
      for (int z = 64; z < SIZE; z++) assertEquals(12, at(result.colors(), x, z));
    assertEquals(0, at(result.colors(), 70, 10), "The north-east quarter had no source");
    assertEquals(3 + 64 * 64, result.filled());
    assertEquals(2, result.contributors());
  }

  @Test
  void scaleZeroTileLandsInItsOwnEighthOfAScaleFourChart() {
    var target = chart(fresh(0, 0, 4), (byte) 0);
    var tile = chart(fresh(0, 0, 0), (byte) 3);
    var result = ArkChartRules.absorb(target, List.of(tile));
    assertEquals(64, result.filled());
    for (int x = 0; x < SIZE; x++)
      for (int z = 0; z < SIZE; z++)
        assertEquals(x < 8 && z < 8 ? 3 : 0, at(result.colors(), x, z), "Wrong pixel " + x + "," + z);
    var far = chart(fresh(1000, 1000, 0), (byte) 5);
    var both = ArkChartRules.absorb(target, List.of(tile, far));
    // Block 1000 lies in scale-4 pixel (1000 + 64) / 16 = 66; its tile (blocks 960..1087) spans 64..71.
    assertEquals(5, at(both.colors(), 66, 66));
    assertEquals(5, at(both.colors(), 64, 64));
    assertEquals(5, at(both.colors(), 71, 71));
    assertEquals(0, at(both.colors(), 63, 63));
    assertEquals(0, at(both.colors(), 72, 72));
    assertEquals(128, both.filled());
  }

  @Test
  void coarserAndDistantSourcesDoNotFit() {
    var target = fresh(0, 0, 1);
    assertEquals(ArkChartRules.Fit.COARSER, ArkChartRules.fit(target, fresh(0, 0, 2)));
    assertEquals(ArkChartRules.Fit.OUTSIDE, ArkChartRules.fit(target, fresh(5000, 0, 0)));
    assertEquals(ArkChartRules.Fit.OUTSIDE, ArkChartRules.fit(target, fresh(0, 192, 1)));
    assertEquals(ArkChartRules.Fit.FITS, ArkChartRules.fit(target, fresh(191, 191, 0)));
    // An off-grid map that only partly overlaps still fits; the absorbed cells are its overlap.
    var shifted = new ArkChartRules.Grid(100, 0, 1);
    assertEquals(ArkChartRules.Fit.FITS, ArkChartRules.fit(target, shifted));
    var result = ArkChartRules.absorb(chart(target, (byte) 0), List.of(chart(shifted, (byte) 8)));
    // Its cells start at x -28 and z -128: columns 18..127 and rows 0..95 of the target overlap.
    assertEquals((SIZE - 18) * (SIZE - 32), result.filled(), "Only the overlap may be filled");
    assertEquals(8, at(result.colors(), 18, 95));
    assertEquals(0, at(result.colors(), 17, 0));
    assertEquals(0, at(result.colors(), 18, 96));
    // Defensive: a coarser chart passed directly still adds nothing.
    var coarse = ArkChartRules.absorb(chart(target, (byte) 0), List.of(chart(fresh(0, 0, 3), (byte) 8)));
    assertEquals(0, coarse.filled());
    assertArrayEquals(new boolean[] {false}, coarse.contributed());
  }

  @Test
  void resultDoesNotDependOnSourceOrder() {
    var random = new Random(0x436861727473L);
    var grid = fresh(0, 0, 2);
    var target = chart(grid, (byte) 0);
    for (int i = 0; i < 2000; i++) set(target, random.nextInt(SIZE), random.nextInt(SIZE), 1 + random.nextInt(200));
    List<ArkChartRules.Chart> sources = new ArrayList<>();
    for (int scale = 0; scale <= 2; scale++)
      for (int n = 0; n < 3; n++) {
        var source = chart(fresh(random.nextInt(512) - 64, random.nextInt(512) - 64, scale), (byte) 0);
        for (int i = 0; i < 9000; i++)
          set(source, random.nextInt(SIZE), random.nextInt(SIZE), random.nextInt(6) * 40);
        sources.add(source);
      }
    var forward = ArkChartRules.absorb(target, sources);
    var reversed = new ArrayList<>(sources);
    java.util.Collections.reverse(reversed);
    var backward = ArkChartRules.absorb(target, reversed);
    assertArrayEquals(forward.colors(), backward.colors());
    assertEquals(forward.filled(), backward.filled());
    assertTrue(forward.filled() > 0);
    for (int i = 0; i < ArkChartRules.PIXELS; i++)
      if (target.colors()[i] != 0) assertEquals(target.colors()[i], forward.colors()[i]);
  }

  @Test
  void viewKeysMustMatchInPresenceAndValue() {
    var plain = new CompoundTag();
    plain.putInt("xCenter", 0);
    plain.putByteArray("colors", new byte[4]);
    plain.putBoolean("antique", true);
    var slice = plain.copy();
    slice.putInt("depth_lock", 32);
    var sameSlice = new CompoundTag();
    sameSlice.putInt("depth_lock", 32);
    var otherSlice = new CompoundTag();
    otherSlice.putInt("depth_lock", 64);
    assertEquals(new CompoundTag(), ArkCharts.viewOf(plain), "Presentation or vanilla keys changed the view");
    assertEquals(ArkCharts.viewOf(slice), ArkCharts.viewOf(sameSlice));
    assertNotEquals(ArkCharts.viewOf(plain), ArkCharts.viewOf(slice));
    assertNotEquals(ArkCharts.viewOf(slice), ArkCharts.viewOf(otherSlice));
  }
}
