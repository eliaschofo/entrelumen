package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RuinRulesTest {
  @Test
  void braziersLightFloorByFloorAndResetOnAWrongTurn() {
    // The Signal Tower: four braziers per floor, floors lit from the bottom up.
    int[] orders = {1, 1, 1, 1, 2, 2, 2, 2};
    Set<Integer> lit = new HashSet<>();
    assertEquals(RuinRules.Step.LIT, RuinRules.light(orders, lit, 2));
    assertEquals(RuinRules.Step.LIT, RuinRules.light(orders, lit, 0), "Same floor, any order");
    assertEquals(RuinRules.Step.AGAIN, RuinRules.light(orders, lit, 0));
    assertEquals(RuinRules.Step.RESET, RuinRules.light(orders, lit, 5), "The next floor before this one is done");
    assertTrue(lit.isEmpty());
    for (int i = 0; i < 4; i++) assertEquals(RuinRules.Step.LIT, RuinRules.light(orders, lit, i));
    assertFalse(RuinRules.allLit(orders, lit));
    for (int i = 7; i >= 4; i--) assertEquals(RuinRules.Step.LIT, RuinRules.light(orders, lit, i));
    assertTrue(RuinRules.allLit(orders, lit));
    // Standing stones: strict order, a wrong stone starts over.
    int[] stones = {3, 1, 2};
    Set<Integer> touched = new HashSet<>();
    assertEquals(RuinRules.Step.RESET, RuinRules.light(stones, touched, 0));
    assertEquals(RuinRules.Step.LIT, RuinRules.light(stones, touched, 1));
    assertEquals(RuinRules.Step.LIT, RuinRules.light(stones, touched, 2));
    assertEquals(RuinRules.Step.LIT, RuinRules.light(stones, touched, 0));
    assertTrue(RuinRules.allLit(stones, touched));
    assertThrows(IllegalArgumentException.class, () -> RuinRules.light(stones, touched, 3));
  }

  @Test
  void offeringsLocksAndGates() {
    assertFalse(RuinRules.offered(8, 4, Set.of(0, 1, 2)));
    assertTrue(RuinRules.offered(8, 4, Set.of(0, 1, 2, 7)), "Four saplings in any four of eight beds");
    assertFalse(RuinRules.offered(4, 0, Set.of(0, 1, 2)), "Need 0 means every socket");
    assertTrue(RuinRules.offered(4, 0, Set.of(0, 1, 2, 3)));
    assertFalse(RuinRules.offered(0, 0, Set.of()), "No socket, no offering");
    assertTrue(RuinRules.unlocked(new boolean[] {true, true}, new boolean[] {true, true}));
    assertFalse(RuinRules.unlocked(new boolean[] {true, false}, new boolean[] {true, true}));
    assertFalse(RuinRules.unlocked(new boolean[0], new boolean[0]), "A lock without levers never opens");
    assertTrue(RuinRules.ready(List.of(), Set.of()));
    assertFalse(RuinRules.ready(List.of("r|guards"), Set.of("r|other")));
    assertTrue(RuinRules.open(List.of("r|a", "r|b"), Set.of("r|a", "r|b", "r|c")));
    assertFalse(RuinRules.open(List.of("r|a", "r|b"), Set.of("r|a")));
    assertFalse(RuinRules.open(List.of(), Set.of("r|a")), "A gate that needs nothing stays shut");
    assertFalse(RuinRules.open(null, Set.of()));
  }

  @Test
  void thePedestalGivesOnePiecePerTeamAndReplacesALostOne() {
    assertEquals(RuinRules.Grant.LOCKED, RuinRules.grant(false, false, 0, false));
    assertEquals(RuinRules.Grant.GIVE, RuinRules.grant(true, false, 0, false));
    assertEquals(RuinRules.Grant.HELD, RuinRules.grant(true, false, 1, true));
    assertEquals(RuinRules.Grant.GIVE, RuinRules.grant(true, false, 1, false), "Lost before delivery: again");
    assertEquals(RuinRules.Grant.DELIVERED, RuinRules.grant(true, true, 1, false));
    assertEquals(RuinRules.Grant.DELIVERED, RuinRules.grant(false, true, 0, false));
  }

  @Test
  void onlyTheTeamsCurrentCopyCounts() {
    UUID team = UUID.randomUUID(), founder = UUID.randomUUID(), other = UUID.randomUUID();
    assertTrue(RuinRules.valid(team, 2, team, null, 2));
    assertFalse(RuinRules.valid(team, 1, team, null, 2), "Replaced by a newer copy");
    assertFalse(RuinRules.valid(other, 1, team, null, 1), "Another team's piece");
    assertTrue(RuinRules.valid(founder, 1, team, founder, 1), "A party inherits its founder's piece");
    assertTrue(RuinRules.valid(null, 0, team, null, 5), "Unbound (commands, creative)");
  }

  @Test
  void candidatesAreDeterministicAndInsideTheRing() {
    var first = RuinRules.candidates(42L, "entrelumen:viaduct", 100, -50, 400, 1200, 48);
    var again = RuinRules.candidates(42L, "entrelumen:viaduct", 100, -50, 400, 1200, 48);
    assertEquals(48, first.size());
    for (int i = 0; i < first.size(); i++) assertArrayEquals(first.get(i), again.get(i), "A restart retries the same sites");
    for (int[] c : first) {
      double d = Math.hypot(c[0] - 100, c[1] + 50);
      assertTrue(d >= 399 && d <= 1201, "Outside the ring: " + d);
    }
    assertFalse(Arrays.equals(first.getFirst(), RuinRules.candidates(43L, "entrelumen:viaduct", 100, -50, 400, 1200, 1).getFirst()),
        "Another seed, another site");
    Set<Integer> quadrants = new HashSet<>();
    for (int[] c : first) quadrants.add((c[0] > 100 ? 1 : 0) + (c[1] > -50 ? 2 : 0));
    assertEquals(4, quadrants.size(), "Candidates go all round the spawn");
  }

  @Test
  void sitesPreferFlatDryGround() {
    var flat = new RuinRules.Sample(new int[] {64, 64, 65, 64}, new boolean[4], true);
    var slope = new RuinRules.Sample(new int[] {60, 64, 68, 72}, new boolean[4], true);
    var wet = new RuinRules.Sample(new int[] {62, 62, 62, 62}, new boolean[] {true, true, false, false}, true);
    var shore = new RuinRules.Sample(new int[] {64, 64, 64, 64}, new boolean[4], false);
    int maxSpread = 10;
    assertTrue(RuinRules.score(flat, maxSpread) < RuinRules.score(slope, maxSpread));
    assertEquals(Integer.MAX_VALUE, RuinRules.score(wet, maxSpread), "Half under water");
    assertTrue(RuinRules.score(shore, maxSpread) > RuinRules.score(flat, maxSpread), "Oceans, rivers and beaches cost");
    assertEquals(Integer.MAX_VALUE, RuinRules.score(new RuinRules.Sample(new int[] {10, 90}, new boolean[2], true), maxSpread));
    assertEquals(64, RuinRules.floor(new int[] {64, 63, 64, 70, 64}));
    assertEquals(70, RuinRules.floor(new int[] {63, 70}), "A tie takes the higher ground");
  }

  @Test
  void ruinsKeepTheirDistance() {
    var ruin = new ProtectionRules.Box(0, 0, 0, 40, 0, 40);
    assertFalse(RuinRules.clear(new ProtectionRules.Box(60, 0, 0, 100, 0, 40), List.of(ruin), 48));
    assertTrue(RuinRules.clear(new ProtectionRules.Box(100, 0, 0, 140, 0, 40), List.of(ruin), 48));
    assertTrue(RuinRules.clear(ruin, List.of(), 48));
  }

  @Test
  void cavernsNeedFloorAndHeadroom() {
    // bottom 0: solid 0..30, lava 31, air above; the ruin needs 10 free blocks.
    boolean[] solid = new boolean[128], lava = new boolean[128];
    for (int y = 0; y <= 30; y++) solid[y] = true;
    lava[31] = true;
    assertEquals(32, RuinRules.cavernFloor(solid, lava, 0, 30, 100, 10), "On the lava lake");
    for (int y = 36; y < 128; y++) solid[y] = true;
    assertEquals(-1, RuinRules.cavernFloor(solid, lava, 0, 30, 100, 10), "Ceiling too low");
    for (int y = 36; y < 60; y++) solid[y] = false;
    assertEquals(32, RuinRules.cavernFloor(solid, lava, 0, 30, 100, 10));
  }
}
