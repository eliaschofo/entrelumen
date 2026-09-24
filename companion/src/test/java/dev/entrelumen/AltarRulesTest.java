package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AltarRulesTest {
  private static AltarRules.Cell[] column(int size, AltarRules.Cell fill) {
    AltarRules.Cell[] cells = new AltarRules.Cell[size];
    java.util.Arrays.fill(cells, fill);
    return cells;
  }

  @Test
  void areaCoversEveryChunkTouchingTheSquareNearestFirst() {
    for (int[] center : new int[][] {{0, 0}, {7, -9}, {-1000, 523}}) {
      int cx = center[0], cz = center[1], radius = AltarRules.RENEWAL_RADIUS;
      var chunks = AltarRules.areaChunks(cx, cz, radius);
      Set<AltarRules.ChunkKey> expected = new HashSet<>();
      for (int x = cx - radius; x <= cx + radius; x++)
        for (int z = cz - radius; z <= cz + radius; z++)
          if (AltarRules.inSquare(x - cx, z - cz, radius))
            expected.add(new AltarRules.ChunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
      assertEquals(expected, new HashSet<>(chunks));
      assertEquals(chunks.size(), new HashSet<>(chunks).size());
      assertEquals(new AltarRules.ChunkKey(Math.floorDiv(cx, 16), Math.floorDiv(cz, 16)), chunks.getFirst());
      assertEquals(chunks, AltarRules.areaChunks(cx, cz, radius));
      assertTrue(chunks.size() <= 25, "radius 24 stays within a 5x5 chunk square");
    }
    assertTrue(AltarRules.areaChunks(8, 8, AltarRules.RENEWAL_ARK_RADIUS).size() <= 25);
  }

  @Test
  void theRenewalSquareAdvancesInSymmetricRings() {
    for (int radius : new int[] {AltarRules.RENEWAL_RADIUS, AltarRules.RENEWAL_ARK_RADIUS, 10}) {
      var columns = AltarRules.square(radius);
      assertEquals((2 * radius + 1) * (2 * radius + 1), columns.size());
      assertEquals(new AltarRules.Offset(0, 0), columns.getFirst());
      var set = new HashSet<>(columns);
      assertEquals(columns.size(), set.size());
      for (int i = 0; i < columns.size(); i++) {
        var column = columns.get(i);
        int dx = column.dx(), dz = column.dz();
        assertTrue(AltarRules.inSquare(dx, dz, radius));
        for (int[] image : new int[][] {{-dx, dz}, {dx, -dz}, {-dx, -dz}, {dz, dx}, {-dz, dx}, {dz, -dx}, {-dz, -dx}})
          assertTrue(set.contains(new AltarRules.Offset(image[0], image[1])), "square is not symmetric");
        if (i > 0) assertTrue(columns.get(i - 1).ring() <= column.ring(), "rings out of order");
      }
    }
  }

  @Test
  void theRegistryAnswersByTypeAndSquareAndForgetsRemovedAltars() {
    var index = new AltarRegistry.Index();
    var renewal = new net.minecraft.core.BlockPos(100, 64, -40);
    var terraform = new net.minecraft.core.BlockPos(-17, 70, 5);
    index.put(new AltarRegistry.Entry(AltarType.RENEWAL, renewal, 24));
    index.put(new AltarRegistry.Entry(AltarType.TERRAFORM, terraform, 12));
    assertEquals(1, index.covering(AltarType.RENEWAL, renewal.offset(24, 0, -24)).size());
    assertTrue(index.covering(AltarType.RENEWAL, renewal.offset(25, 0, 0)).isEmpty());
    assertTrue(index.covering(AltarType.TERRAFORM, renewal).isEmpty(), "types stay apart");
    assertEquals(1, index.covering(AltarType.TERRAFORM, terraform.offset(-12, 5, 12)).size());
    index.put(new AltarRegistry.Entry(AltarType.RENEWAL, renewal, 32));
    assertEquals(2, index.size(), "re-registering replaces");
    assertEquals(1, index.covering(AltarType.RENEWAL, renewal.offset(32, 0, 32)).size());
    index.remove(renewal);
    assertTrue(index.covering(AltarType.RENEWAL, renewal).isEmpty());
    assertEquals(1, index.size());
  }

  @Test
  void theLoadedAreaCoversTheDiscItsCanopiesAndTheirNeighbours() {
    for (int[] center : new int[][] {{0, 0}, {15, -16}, {-1000, 523}}) {
      int radius = AltarRules.RENEWAL_ARK_RADIUS, reach = radius + AltarRules.TREE_MARGIN + 1;
      int[] bounds = AltarRules.loadedBounds(center[0], center[1], radius);
      assertTrue(bounds[0] * 16 <= center[0] - reach && (bounds[2] + 1) * 16 - 1 >= center[0] + reach);
      assertTrue(bounds[1] * 16 <= center[1] - reach && (bounds[3] + 1) * 16 - 1 >= center[1] + reach);
      assertTrue(bounds[2] - bounds[0] <= 6 && bounds[3] - bounds[1] <= 6, "at most 7x7 chunks");
    }
  }

  @Test
  void aDugPitIsRefilledBottomUpToTheOriginalSurface() {
    int top = 10;
    var cells = column(20, AltarRules.Cell.GROUND);
    for (int y = 8; y <= 10; y++) cells[y] = AltarRules.Cell.OPEN;
    int[] fills = AltarRules.fillColumn(top, y -> y <= top, y -> cells[y]);
    assertArrayEquals(new int[] {8, 9, 10}, fills);
  }

  @Test
  void closedCavesBuildsFluidsAndSurplusAreNeverFilled() {
    int top = 10;
    var cave = column(20, AltarRules.Cell.GROUND);
    for (int y = 4; y <= 7; y++) cave[y] = AltarRules.Cell.OPEN;
    assertEquals(0, AltarRules.fillColumn(top, y -> y <= top, y -> cave[y]).length, "closed cave");
    var chest = column(20, AltarRules.Cell.GROUND);
    chest[10] = AltarRules.Cell.OPEN;
    chest[9] = AltarRules.Cell.BLOCKED;
    assertEquals(0, AltarRules.fillColumn(top, y -> y <= top, y -> chest[y]).length, "build in the pit");
    var flooded = column(20, AltarRules.Cell.GROUND);
    flooded[10] = AltarRules.Cell.BLOCKED;
    assertEquals(0, AltarRules.fillColumn(top, y -> y <= top, y -> flooded[y]).length, "fluid");
    var intact = column(20, AltarRules.Cell.GROUND);
    assertEquals(0, AltarRules.fillColumn(top, y -> y <= top, y -> intact[y]).length, "untouched ground");
  }

  @Test
  void refillsRiseFromGroundAndSkipOverhangsAndBottomlessPits() {
    int top = 10;
    var cells = column(20, AltarRules.Cell.GROUND);
    for (int y = 6; y <= 10; y++) cells[y] = AltarRules.Cell.OPEN;
    assertArrayEquals(new int[] {6, 7, 8}, AltarRules.fillColumn(top, y -> y <= top && y != 9, y -> cells[y]),
        "original air under an overhang ends the supported refill");
    int deepTop = 60;
    assertEquals(0, AltarRules.fillColumn(deepTop, y -> y <= deepTop, y -> AltarRules.Cell.OPEN).length,
        "no ground within reach: nothing floats");
    var deep = column(80, AltarRules.Cell.OPEN);
    for (int y = 0; y <= deepTop - AltarRules.FILL_DEPTH; y++) deep[y] = AltarRules.Cell.GROUND;
    int[] reached = AltarRules.fillColumn(deepTop, y -> y <= deepTop, y -> deep[y]);
    assertEquals(AltarRules.FILL_DEPTH, reached.length);
    assertEquals(deepTop, reached[reached.length - 1]);
    var floating = column(80, AltarRules.Cell.OPEN);
    for (int y = 0; y <= 40; y++) floating[y] = AltarRules.Cell.GROUND;
    assertEquals(0, AltarRules.fillColumn(50, y -> y == 50 || y <= 40, y -> floating[y]).length,
        "terrain the generator left floating above a gap is not rebuilt");
  }

  @Test
  void chunksAreTrustedOnlyWhileTheyStillLookGenerated() {
    assertFalse(AltarRules.consistent(AltarRules.MIN_NATURAL_COLUMNS - 1, 15, 0), "mostly built");
    assertTrue(AltarRules.consistent(256, 200, 0), "a few pits");
    assertTrue(AltarRules.consistent(256, 60, 0), "a large dig still has intact ground");
    assertFalse(AltarRules.consistent(256, 40, 0), "quarried beyond recognition");
    assertFalse(AltarRules.consistent(256, 200, 30), "ground above the original surface");
    assertTrue(AltarRules.consistent(256, 200, 25));
  }

  @Test
  void fertilizerIsDrawnOnlyForWorkAndChargeCarriesOver() {
    assertEquals(0, AltarRules.fertilizerNeeded(4, 4));
    assertEquals(1, AltarRules.fertilizerNeeded(0, 1));
    assertEquals(4, AltarRules.fertilizerNeeded(1, AltarRules.TREE_CHARGE));
    assertTrue(AltarRules.affordable(3, 0, 3));
    assertFalse(AltarRules.affordable(3, 0, 4));
    assertTrue(AltarRules.affordable(0, 4, AltarRules.TREE_CHARGE));
    int charge = 0, items = 0, spent = 0;
    int[] costs = {1, 1, 16, 1, 3, 16, 1, 1, 1, 7};
    for (int cost : costs) {
      int drawn = AltarRules.fertilizerNeeded(charge, cost);
      items += drawn;
      charge += drawn * AltarRules.CHARGE_PER_FERTILIZER - cost;
      spent += cost;
      assertTrue(charge >= 0 && charge < AltarRules.CHARGE_PER_FERTILIZER + cost);
    }
    assertEquals(Math.ceilDiv(spent, AltarRules.CHARGE_PER_FERTILIZER), items, "never more than the work needs");
    assertEquals(items * AltarRules.CHARGE_PER_FERTILIZER - spent, charge);
    assertTrue(charge < AltarRules.CHARGE_PER_FERTILIZER);
  }

  @Test
  void treesCostTheirMissingBlocksUpToAWholeTree() {
    assertEquals(1, AltarRules.treeCost(0));
    assertEquals(5, AltarRules.treeCost(5));
    assertEquals(AltarRules.TREE_CHARGE, AltarRules.treeCost(400));
  }

  @Test
  void tickBudgetBoundsBlocksAndTimeButNeverStarvesAWholeUnit() {
    AtomicLong clock = new AtomicLong();
    var budget = new AltarRules.TickBudget(32, 2_000_000L, clock::get);
    assertTrue(budget.open() && budget.allows(500), "a fresh tick accepts a unit larger than the budget");
    budget.spend(10);
    assertTrue(budget.allows(22) && !budget.allows(23));
    clock.set(2_000_001L);
    assertFalse(budget.open() || budget.allows(1), "wall time exhausted");
    var fresh = new AltarRules.TickBudget(32, 2_000_000L, clock::get);
    fresh.spend(32);
    assertFalse(fresh.open());
    var scans = new AltarRules.TickBudget(32, 3, 2_000_000L, clock::get);
    scans.scan();
    scans.scan();
    assertTrue(scans.open() && scans.allows(32), "inspection alone leaves the write budget whole");
    scans.scan();
    assertFalse(scans.open(), "inspected columns are bounded even when none needs work");
    var late = new AltarRules.TickBudget(32, 100, 2_000_000L, clock::get);
    late.scan();
    clock.addAndGet(2_000_001L);
    assertFalse(late.open(), "a tick of pure inspection still respects wall time");
  }

  @Test
  void theArkSiteExtendsTheRadius() {
    assertEquals(AltarRules.RENEWAL_RADIUS, AltarRules.renewalRadius(false));
    assertEquals(AltarRules.RENEWAL_ARK_RADIUS, AltarRules.renewalRadius(true));
    assertTrue(AltarRules.nearArkSite(0, 0, 64, 0));
    assertFalse(AltarRules.nearArkSite(0, 0, 46, 46));
  }
}
