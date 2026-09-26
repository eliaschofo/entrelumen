package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import dev.entrelumen.EnvesLayout.Dir;
import dev.entrelumen.EnvesLayout.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvesLayoutTest {
  private static final int SEEDS = 2000;

  @Test
  void theOraclesInvariantsHoldOverManySeeds() {
    for (long seed = 0; seed < SEEDS; seed++) {
      var descent = EnvesLayout.descent(seed * 7919 + 13);
      assertNull(EnvesLayout.check(descent), "seed " + seed);
    }
  }

  @Test
  void eachFloorStartsRightUnderThePreviousStairwell() {
    for (long seed = 0; seed < 300; seed++) {
      var floors = EnvesLayout.descent(seed).floors();
      for (int i = 1; i < floors.size(); i++)
        assertEquals(floors.get(i - 1).exit(), floors.get(i).start(), "seed " + seed + " floor " + (i + 1));
    }
  }

  @Test
  void floorsGrowToTheirTargetsWithTwoThenThreeSeals() {
    for (long seed = 0; seed < 300; seed++) {
      var descent = EnvesLayout.descent(seed);
      for (int depth = 1; depth <= 4; depth++) {
        var floor = descent.floor(depth);
        assertTrue(floor.size() >= EnvesLayout.target(depth), "seed " + seed + " depth " + depth);
        assertEquals(depth <= 2 ? 2 : 3, floor.seals().size(), "seed " + seed + " depth " + depth);
        for (int seal : floor.seals()) {
          assertEquals(1, floor.doorCount(seal), "a seal hides in a dead end");
          assertEquals(Role.SEAL, floor.role(seal));
        }
      }
      assertEquals(List.of(26, 31, 36, 41), List.of(EnvesLayout.target(1), EnvesLayout.target(2),
          EnvesLayout.target(3), EnvesLayout.target(4)));
    }
  }

  @Test
  void theSameSeedDrawsTheSameDescent() {
    for (long seed : new long[] {0, 1, 2609, -42, Long.MAX_VALUE}) {
      var a = EnvesLayout.descent(seed);
      var b = EnvesLayout.descent(seed);
      for (int depth = 1; depth <= 5; depth++) {
        var fa = a.floor(depth);
        var fb = b.floor(depth);
        assertEquals(fa.cells(), fb.cells());
        for (int c : fa.cells()) {
          assertEquals(fa.doors(c), fb.doors(c));
          assertEquals(fa.role(c), fb.role(c));
        }
      }
    }
  }

  @Test
  void theBossFloorKeepsItsFixedShapeInsideTheGrid() {
    for (long seed = 0; seed < 1000; seed++) {
      var boss = EnvesLayout.descent(seed).floor(5);
      assertEquals(13, boss.size(), "start, approach, guard, 3x3 arena and portal; seed " + seed);
      int center = boss.withRole(Role.ARENA_CENTER).getFirst();
      assertEquals(0b1111, boss.doors(center), "the arena centre opens to four arena cells");
      int portal = boss.exit();
      assertEquals(Role.PORTAL, boss.role(portal));
      int door = Integer.numberOfTrailingZeros(boss.doors(portal));
      int edge = EnvesLayout.neighbour(portal, Dir.values()[door]);
      assertEquals(Role.ARENA, boss.role(edge), "the portal opens onto the arena");
      var guard = boss.withRole(Role.GUARD);
      assertEquals(1, guard.size());
      assertEquals(3, boss.path(boss.start(), guard.getFirst()).size(), "start, approach, guard");
      for (int c : boss.cells())
        assertTrue(EnvesLayout.inside(EnvesLayout.x(c), EnvesLayout.z(c)), "seed " + seed);
    }
  }

  @Test
  void everyStartIsAwayFromTheGridEdge() {
    for (long seed = 0; seed < 500; seed++) {
      int start = EnvesLayout.descent(seed).floor(1).start();
      assertTrue(EnvesLayout.x(start) >= 2 && EnvesLayout.x(start) <= EnvesLayout.W - 3);
      assertTrue(EnvesLayout.z(start) >= 2 && EnvesLayout.z(start) <= EnvesLayout.H - 3);
    }
  }

  @Test
  void checkReportsBrokenFloors() {
    var descent = EnvesLayout.descent(5);
    var floor = descent.floor(2);
    int seal = floor.seals().getFirst();
    floor.roles[seal] = Role.QUIET;
    assertNotNull(EnvesLayout.check(descent), "a seal cell without its role");
    floor.roles[seal] = Role.SEAL;
    assertNull(EnvesLayout.check(descent));
    int start = floor.start;
    floor.start = floor.exit;
    assertNotNull(EnvesLayout.check(descent), "floor II must start under floor I's stairs");
    floor.start = start;
  }
}
