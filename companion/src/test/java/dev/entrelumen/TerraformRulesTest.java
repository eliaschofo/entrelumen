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
    assertEquals(TerraformRules.REPAIR, TerraformRules.nextSize(16));
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
  void protectedNaturalBlocksRefuseTheColumnInsteadOfBeingRemoved() {
    var ore = ground(64);
    ore.put(63, TerraformRules.Cell.PROTECTED);
    assertEquals("protected", plan(64, ore).refusal(), "an ore in the surface layers is never swapped");
    var tree = ground(64);
    for (int y = 65; y <= 70; y++) tree.put(y, TerraformRules.Cell.PROTECTED);
    assertEquals("protected", plan(64, tree).refusal(), "a trunk above the target is never cut");
    var deepOre = ground(64);
    deepOre.put(64 - TerraformRules.SUBSURFACE_DEPTH - 3, TerraformRules.Cell.PROTECTED);
    assertFalse(plan(64, deepOre).refused(), "an ore below the touched layers is not touched");
  }

  // ---- Under a roof ---------------------------------------------------------------------------

  /** A cave: ground up to {@code floor}, air up to the roof, ground from {@code roof} to 127 (the ceiling). */
  private static Map<Integer, TerraformRules.Cell> cave(int floor, int roof) {
    var cells = ground(floor);
    for (int y = roof; y < 128; y++) cells.put(y, GROUND);
    return cells;
  }

  private static TerraformRules.Plan roofed(int target, int bandTop, Map<Integer, TerraformRules.Cell> cells) {
    return TerraformRules.roofedColumn(target, bandTop, y -> cells.getOrDefault(y, AIR), y -> true);
  }

  private static int highestTouched(TerraformRules.Plan plan) {
    return plan.ops().stream().mapToInt(TerraformRules.Op::y).max().orElse(Integer.MIN_VALUE);
  }

  @Test
  void underARoofOnlyTheMoundOnTheFloorIsCutNeverTheCeiling() {
    // The old planner cut every natural block up to 32 over the target: here, the whole roof.
    var cells = cave(64, 74);
    for (int y = 78; y < 128; y++) cells.put(y, AIR);
    for (int y = 65; y <= 67; y++) cells.put(y, GROUND);
    cells.put(68, LOOSE);
    var plan = roofed(64, 74, cells);
    assertFalse(plan.refused());
    List<Integer> cuts = new ArrayList<>();
    for (var op : plan.ops()) if (op.kind() == TerraformRules.Kind.CUT) cuts.add(op.y());
    assertEquals(List.of(68, 67, 66, 65), cuts, "the mound and the plant on it, top-down, nothing else");
    assertTrue(highestTouched(plan) < 74);
    assertTrue(TerraformRules.column(64, y -> cells.getOrDefault(y, AIR), y -> true).ops().stream()
        .anyMatch(op -> op.y() >= 74), "the open-air planner would have cut the roof");
  }

  @Test
  void underARoofWhatHangsFromAboveIsNeverCut() {
    var stalactite = cave(64, 74);
    for (int y = 69; y < 74; y++) stalactite.put(y, GROUND);
    assertTrue(roofed(64, 74, stalactite).ops().isEmpty(), "a stalactite over a level floor stays");
    var vines = cave(64, 74);
    for (int y = 68; y < 74; y++) vines.put(y, LOOSE);
    assertTrue(roofed(64, 74, vines).ops().isEmpty(), "vines that do not reach the floor stay");
    var pillar = cave(64, 74);
    for (int y = 65; y < 74; y++) pillar.put(y, GROUND);
    assertEquals("ceiling", roofed(64, 74, pillar).refusal(), "rock from the floor into the roof is a wall");
    var lowRoof = cave(64, 70);
    assertTrue(roofed(64, 74, lowRoof).ops().isEmpty(), "a roof lower than the altar's is left alone");
    var tallMound = cave(64, 90);
    for (int y = 65; y <= 76; y++) tallMound.put(y, GROUND);
    assertEquals("ceiling", roofed(64, 74, tallMound).refusal(), "nothing at or over the band top is ever cut");
  }

  @Test
  void underARoofHolesAreFilledButNeverSealedAgainstTheRoof() {
    var pit = cave(60, 74);
    pit.put(61, LOOSE);
    var plan = roofed(64, 74, pit);
    assertFalse(plan.refused());
    List<Integer> fills = new ArrayList<>();
    for (var op : plan.ops()) if (op.kind() == TerraformRules.Kind.FILL) fills.add(op.y());
    assertEquals(List.of(61, 62, 63, 64), fills);
    assertTrue(highestTouched(plan) == 64);
    var sealed = cave(60, 74);
    for (int y = 65; y < 74; y++) sealed.put(y, GROUND);
    assertEquals("ceiling", roofed(64, 74, sealed).refusal(), "filling up to a stalactite would seal the cave");
    var hangingVines = cave(60, 74);
    for (int y = 65; y < 74; y++) hangingVines.put(y, LOOSE);
    var cleared = roofed(64, 74, hangingVines);
    assertFalse(cleared.refused());
    assertTrue(highestTouched(cleared) <= 73, "vines down to the fill are cut below the roof only");
    assertEquals("ceiling", roofed(73, 74, cave(64, 74)).refusal(), "no target inside the roof");
    assertEquals("too_deep", roofed(64, 74, cave(64 - TerraformRules.MAX_FILL - 1, 74)).refusal());
    var lava = cave(60, 74);
    lava.put(61, TerraformRules.Cell.FLUID);
    assertEquals("fluid", roofed(64, 74, lava).refusal());
    var bedrock = cave(64, 74);
    bedrock.put(63, TerraformRules.Cell.BUILT);
    assertEquals("built", roofed(64, 74, bedrock).refusal(), "bedrock and builds in the touched layers refuse");
  }

  @Test
  void theFloorUnderARoofIsTheSurfaceNearestTheAltarNeverTheRoof() {
    var cave = cave(60, 74);
    assertEquals(60, TerraformRules.floorNear(y -> cave.getOrDefault(y, AIR), 0, 64, 74));
    var ledge = cave(40, 74);
    for (int y = 41; y <= 66; y++) ledge.put(y, y == 66 ? GROUND : AIR);
    assertEquals(66, TerraformRules.floorNear(y -> ledge.getOrDefault(y, AIR), 0, 64, 74), "the nearer ledge wins");
    var tie = cave(62, 74);
    for (int y = 63; y <= 66; y++) tie.put(y, y == 66 ? GROUND : AIR);
    assertEquals(62, TerraformRules.floorNear(y -> tie.getOrDefault(y, AIR), 0, 64, 74), "below first on a tie");
    var plants = cave(63, 74);
    plants.put(64, LOOSE);
    assertEquals(63, TerraformRules.floorNear(y -> plants.getOrDefault(y, AIR), 0, 64, 74), "grass is not a floor");
    var wall = cave(127, 128);
    assertEquals(TerraformRules.UNKNOWN, TerraformRules.floorNear(y -> wall.getOrDefault(y, AIR), 0, 64, 74),
        "solid rock up to the roof has no floor");
    var roofOnly = cave(10, 74);
    assertEquals(TerraformRules.UNKNOWN, TerraformRules.floorNear(y -> roofOnly.getOrDefault(y, AIR), 0, 64, 74),
        "the top of the roof is outside the band");
  }

  @Test
  void theRepairIsTheFifthSettingOfTheCycle() {
    assertEquals(TerraformRules.REPAIR, TerraformRules.nextSize(16));
    assertEquals(4, TerraformRules.nextSize(TerraformRules.REPAIR));
    assertTrue(TerraformRules.validSize(TerraformRules.REPAIR) && TerraformRules.repair(TerraformRules.REPAIR));
    assertFalse(TerraformRules.repair(16));
    assertEquals(TerraformRules.REPAIR, TerraformRules.reach(TerraformRules.REPAIR));
    assertEquals(20, TerraformRules.reach(16));
    int side = 2 * TerraformRules.REPAIR + 1;
    var columns = TerraformRules.columns(TerraformRules.REPAIR);
    assertEquals(side * side, columns.size());
    assertEquals(new TerraformRules.Column(0, 0), columns.getFirst());
    int cycle = TerraformRules.DEFAULT_SIZE;
    for (int i = 0; i < 5; i++) cycle = TerraformRules.nextSize(cycle);
    assertEquals(TerraformRules.DEFAULT_SIZE, cycle, "five settings bring the cycle back");
  }
}
