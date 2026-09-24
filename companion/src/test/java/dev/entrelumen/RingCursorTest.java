package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RingCursorTest {
  @Test
  void visitsEverySquareOnceNearestRingFirst() {
    int radius = 7;
    var cursor = new RingCursor(radius);
    Set<Long> seen = new HashSet<>();
    int[] offset = new int[2];
    int previousRing = 0;
    while (cursor.next(offset)) {
      int ring = Math.max(Math.abs(offset[0]), Math.abs(offset[1]));
      assertEquals(cursor.ring(), ring, "Offset must lie on the reported ring");
      assertTrue(ring >= previousRing, "Rings never go back inward");
      previousRing = ring;
      assertTrue(seen.add(((long) offset[0] << 32) ^ (offset[1] & 0xffffffffL)), "Duplicate square");
    }
    assertEquals((2 * radius + 1) * (2 * radius + 1), seen.size());
    assertFalse(cursor.next(offset), "An exhausted cursor stays exhausted");
  }

  @Test
  void aResumedCursorContinuesWhereItStopped() {
    var whole = new RingCursor(3);
    var split = new RingCursor(3);
    int[] a = new int[2], b = new int[2];
    List<String> expected = new ArrayList<>(), actual = new ArrayList<>();
    while (whole.next(a)) expected.add(a[0] + "," + a[1]);
    // Simulate a budget that allows five squares per tick.
    boolean more = true;
    while (more) for (int i = 0; i < 5 && (more = split.next(b)); i++) actual.add(b[0] + "," + b[1]);
    assertEquals(expected, actual);
  }

  @Test
  void finishStopsTheSearch() {
    var cursor = new RingCursor(10);
    int[] offset = new int[2];
    assertTrue(cursor.next(offset));
    cursor.finish();
    assertFalse(cursor.next(offset));
  }

  @Test
  void aFifteenHundredBlockRadiusBoundsTheScanTo189By189Chunks() {
    // The chunk cost bound used by the locator: ceil(1500 / 16) = 94 rings.
    var cursor = new RingCursor((CompassTargets.MAX_RADIUS + 15) / 16);
    int count = 0;
    int[] offset = new int[2];
    while (cursor.next(offset)) count++;
    assertEquals(189 * 189, count);
  }
}
