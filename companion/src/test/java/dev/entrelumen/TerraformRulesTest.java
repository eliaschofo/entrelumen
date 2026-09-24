package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TerraformRulesTest {
  private static final TerraformRules.Cell AIR = TerraformRules.Cell.AIR;
  private static final TerraformRules.Cell GROUND = TerraformRules.Cell.GROUND;
  private static final TerraformRules.Cell LOOSE = TerraformRules.Cell.LOOSE;

  /** A column from y=0 to 127 with natural ground up to {@code top}. */
  private static Map<Integer, TerraformRules.Cell> ground(int top) {
    Map<Integer, TerraformRules.Cell> cells = new HashMap<>();
    for (int y = 0; y < 128; y++) cells.put(y, y <= top ? GROUND : AIR);
    return cells;
  }

  private static TerraformRules.Plan plan(int target, Map<Integer, TerraformRules.Cell> cells) {
    return TerraformRules.column(target, y -> cells.getOrDefault(y, AIR), y -> true);
  }

  @Test
  void columnsCoverTheSquareAndItsSlopeCenterOutwards() {
    for (int size : TerraformRules.SIZES) {
      var columns = TerraformRules.columns(size);
      int side = 2 * TerraformRules.reach(size) + 1;
      assertEquals(side * side, columns.size());
      assertEquals(columns.size(), new HashSet<>(columns).size());
      assertEquals(new TerraformRules.Column(0, 0), columns.getFirst());
      for (int i = 1; i < columns.size(); i++) assertTrue(columns.get(i - 1).ring() <= columns.get(i).ring());
      assertEquals(8 * (TerraformRules.reach(size) + 1), TerraformRules.outerRing(size).size());
    }
    assertEquals(8, TerraformRules.nextSize(4));
    assertEquals(4, TerraformRules.nextSize(16));
    assertEquals(TerraformRules.DEFAULT_SIZE, TerraformRules.nextSize(5));
    assertFalse(TerraformRules.validSize(5));
  }

  @Test
  void targetIsFlatInsideAndEasesIntoTheLandOutside() {
    int size = 8, flat = 64;
    TerraformRules.OuterHeight hill = (dx, dz) -> 80;
    for (int dx = -size; dx <= size; dx++) assertEquals(flat, TerraformRules.target(dx, 3, size, flat, hill));
    int previous = flat;
    for (int ring = size + 1; ring <= TerraformRules.reach(size); ring++) {
      int height = TerraformRules.target(ring, 0, size, flat, hill);
      assertTrue(height >= previous && height < 80, "monotone and below the outer land: " + height);
      previous = height;
    }
    assertTrue(previous > flat + 8, "the last band row is close to the outer land");
    assertEquals(TerraformRules.target(size + 2, 1, size, flat, hill), TerraformRules.target(-size - 2, -1, size, flat, hill));
    assertEquals(flat, TerraformRules.target(size + 2, 0, size, flat, (dx, dz) -> TerraformRules.UNKNOWN));
    int valley = TerraformRules.target(size + 2, 0, size, flat, (dx, dz) -> 40);
    assertTrue(valley < flat && valley > 40);
    assertEquals(0, TerraformRules.smooth(0), 1e-12);
    assertEquals(1, TerraformRules.smooth(1), 1e-12);
    assertEquals(0.5, TerraformRules.smooth(0.5), 1e-12);
  }

  @Test
  void theSquareAndItsSlopeAreSymmetricOnAllFourSides() {
    for (int size : TerraformRules.SIZES) {
      TerraformRules.OuterHeight mirrored = (dx, dz) -> 60 + (Math.abs(dx) + Math.abs(dz)) % 5;
      int reach = TerraformRules.reach(size);
      for (int dx = -reach; dx <= reach; dx++)
        for (int dz = -reach; dz <= reach; dz++) {
          int target = TerraformRules.target(dx, dz, size, 64, mirrored);
          for (int[] image : new int[][] {{-dx, dz}, {dx, -dz}, {-dx, -dz}, {dz, dx}, {-dz, dx}, {dz, -dx}, {-dz, -dx}})
            assertEquals(target, TerraformRules.target(image[0], image[1], size, 64, mirrored),
                "size " + size + " column " + dx + "," + dz + " vs " + image[0] + "," + image[1]);
        }
      TerraformRules.OuterHeight level = (dx, dz) -> 70;
      for (int ring = size + 1; ring <= reach; ring++) {
        int side = TerraformRules.target(ring, 0, size, 64, level);
        for (int along = -ring; along <= ring; along++)
          for (int[] column : new int[][] {{ring, along}, {-ring, along}, {along, ring}, {along, -ring}})
            assertEquals(side, TerraformRules.target(column[0], column[1], size, 64, level),
                "the slope differs between sides at ring " + ring);
      }
    }
    assertEquals(-3, TerraformRules.roundAway(-2.5));
    assertEquals(3, TerraformRules.roundAway(2.5));
    assertEquals(0, TerraformRules.roundAway(-0.4));
  }

  @Test
  void layersFollowDepthBelowTheSurface() {
    assertEquals(TerraformRules.Layer.SURFACE, TerraformRules.layer(0));
    assertEquals(TerraformRules.Layer.SUBSURFACE, TerraformRules.layer(1));
    assertEquals(TerraformRules.Layer.SUBSURFACE, TerraformRules.layer(TerraformRules.SUBSURFACE_DEPTH));
    assertEquals(TerraformRules.Layer.DEEP, TerraformRules.layer(TerraformRules.SUBSURFACE_DEPTH + 1));
  }

  @Test
  void aHillIsCutTopDownAndItsNewTopIsResurfaced() {
    var cells = ground(67);
    cells.put(68, LOOSE);
    var plan = TerraformRules.column(64, y -> cells.getOrDefault(y, AIR), y -> y != 64);
    assertFalse(plan.refused());
    List<Integer> cuts = new ArrayList<>();
    for (var op : plan.ops()) if (op.kind() == TerraformRules.Kind.CUT) cuts.add(op.y());
    assertEquals(List.of(68, 67, 66, 65), cuts);
    assertEquals(new TerraformRules.Op(TerraformRules.Kind.SWAP, 64, TerraformRules.Layer.SURFACE), plan.ops().getLast());
    assertTrue(plan.ops().stream().noneMatch(op -> op.kind() == TerraformRules.Kind.FILL));
  }

  @Test
  void aPitIsClearedThenFilledBottomUpWithLayers() {
    var cells = ground(58);
    cells.put(59, LOOSE);
    var plan = plan(64, cells);
    assertFalse(plan.refused());
    assertEquals(new TerraformRules.Op(TerraformRules.Kind.CUT, 59, null), plan.ops().getFirst());
    List<TerraformRules.Op> fills = plan.ops().stream().filter(op -> op.kind() == TerraformRules.Kind.FILL).toList();
    assertEquals(6, fills.size());
    for (int i = 0; i < fills.size(); i++) {
      int y = 59 + i;
      assertEquals(y, fills.get(i).y());
      assertEquals(TerraformRules.layer(64 - y), fills.get(i).layer());
    }
    assertEquals(TerraformRules.Layer.DEEP, fills.getFirst().layer());
    assertEquals(TerraformRules.Layer.SURFACE, fills.getLast().layer());
  }

  @Test
  void operationsRunCutsThenSwapsThenFills() {
    var cells = ground(62);
    cells.put(70, LOOSE);
    var plan = TerraformRules.column(64, y -> cells.getOrDefault(y, AIR), y -> false);
    int stage = 0;
    for (var op : plan.ops()) {
      int order = op.kind().ordinal();
      assertTrue(order >= stage, "out of order: " + plan.ops());
      stage = order;
    }
    assertTrue(plan.ops().stream().anyMatch(op -> op.kind() == TerraformRules.Kind.SWAP));
  }

  @Test
  void buildsFluidsTallLandAndBottomlessHolesRefuseTheWholeColumn() {
    var built = ground(64);
    built.put(66, TerraformRules.Cell.BUILT);
    assertEquals("built", plan(64, built).refusal());
    var wet = ground(60);
    wet.put(61, TerraformRules.Cell.FLUID);
    assertEquals("fluid", plan(64, wet).refusal());
    var tall = ground(64 + TerraformRules.MAX_CUT + 1);
    assertEquals("too_tall", plan(64, tall).refusal());
    var bottomless = new HashMap<Integer, TerraformRules.Cell>();
    assertEquals("too_deep", plan(64, bottomless).refusal());
    var level = ground(64);
    assertTrue(plan(64, level).ops().isEmpty(), "already flat and matching");
    var buriedBuild = ground(64);
    buriedBuild.put(64 - TerraformRules.SUBSURFACE_DEPTH - 3, TerraformRules.Cell.BUILT);
    assertFalse(plan(64, buriedBuild).refused(), "a build below the touched layers is not touched");
  }

  @Test
  void theLedgerMovesMatterWithoutCreatingIt() {
    var ledger = new TerraformRules.Ledger();
    ledger.deposit("minecraft:dirt", 3);
    ledger.deposit("minecraft:cobblestone", 2);
    assertEquals(5, ledger.total());
    assertEquals("minecraft:dirt", ledger.withdraw(List.of("minecraft:grass_block", "minecraft:dirt")));
    assertEquals("minecraft:cobblestone", ledger.withdraw(List.of("minecraft:sand", "minecraft:cobblestone")));
    assertNull(ledger.withdraw(List.of("minecraft:sand")));
    assertEquals(3, ledger.total());
    assertThrows(IllegalArgumentException.class, () -> ledger.deposit("minecraft:dirt", -1));
  }

  @Test
  void applyingPlansConservesBlocksPlusBuffer() {
    // A 1D world of columns: cut blocks enter the ledger, filled blocks leave it.
    var ledger = new TerraformRules.Ledger();
    ledger.deposit("dirt", 2);
    int[] tops = {67, 70, 60, 64, 58};
    int before = ledger.total();
    for (int top : tops) before += top + 1;
    int target = 64;
    int placed = 0;
    for (int i = 0; i < tops.length; i++) {
      var cells = ground(tops[i]);
      var plan = plan(target, cells);
      assertFalse(plan.refused());
      int height = tops[i];
      for (var op : plan.ops()) {
        switch (op.kind()) {
          case CUT -> {
            ledger.deposit("dirt", 1);
            height--;
          }
          case FILL -> {
            assertNotNull(ledger.withdraw(List.of("dirt")), "the plan never fills from nothing");
            height++;
            placed++;
          }
          case SWAP -> fail("matching layers are not swapped");
        }
      }
      tops[i] = height;
      assertEquals(target, height);
    }
    int after = ledger.total();
    for (int top : tops) after += top + 1;
    assertEquals(before, after);
    assertTrue(placed > 0);
  }
}
