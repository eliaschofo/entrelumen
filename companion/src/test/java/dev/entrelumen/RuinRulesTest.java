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
    assertTrue(RuinRules.score(flat) < RuinRules.score(slope));
    assertEquals(Integer.MAX_VALUE, RuinRules.score(wet), "Half under water");
    assertTrue(RuinRules.score(shore) > RuinRules.score(flat), "Oceans, rivers and beaches cost");
    // The placement cap (26 September): a steep site costs but stays a candidate; the least steep wins.
    var steep = new RuinRules.Sample(new int[] {10, 90}, new boolean[2], true);
    var steeper = new RuinRules.Sample(new int[] {10, 120}, new boolean[2], true);
    assertTrue(RuinRules.score(steep) < Integer.MAX_VALUE && RuinRules.score(steep) < RuinRules.score(steeper));
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
  @Test
  void rIsWhatTheMergedWheelsJustCarry() {
    // Create 6.0.10 defaults: large water wheel 128 SU per RPM at 4 RPM, mechanical pump 4 SU per RPM.
    int r = RuinRules.pumpSpeed(128, 4, 4, 4, 4);
    assertEquals(128, r);
    double merged = 4 * 128 * 4, one = 128 * 4;
    assertTrue(4 * 4 * r <= merged, "Four wheels merged carry four pumps at R");
    assertTrue(4 * 4 * r > one, "One wheel cannot turn the four pumps at R");
    assertTrue(4 * 4 * (2 * r) > merged, "2R overstresses");
    assertEquals(32, one / (4 * 4), "One wheel turns the four pumps at 32 RPM at most");
    assertEquals(0, RuinRules.pumpSpeed(128, 0, 4, 4, 4));
    assertEquals(0, RuinRules.pumpSpeed(1, 4, 4, 1, 4), "No engine when the pumps outweigh the wheels even at wheel speed");
    assertEquals(RuinRules.MAX_RPM, RuinRules.pumpSpeed(100_000, 4, 1, 4, 4), "Never faster than Create allows");
  }

  @Test
  void pumpsDrinkTogetherTheirOwnWayOnOneNetwork() {
    assertEquals(RuinRules.Pump.DRINKING, RuinRules.pump(-128, -1, 128));
    assertEquals(RuinRules.Pump.DRINKING, RuinRules.pump(256, 1, 128), "Faster is fine while the wheels carry it");
    assertEquals(RuinRules.Pump.WRONG, RuinRules.pump(128, -1, 128));
    assertEquals(RuinRules.Pump.SLOW, RuinRules.pump(-64, -1, 128));
    assertEquals(RuinRules.Pump.STILL, RuinRules.pump(0, 1, 128), "Overstressed pumps read 0");
    float[] speeds = {-128, 128, -128, 128};
    int[] turns = {-1, 1, -1, 1};
    assertTrue(RuinRules.pumping(speeds, turns, new Long[] {7L, 7L, 7L, 7L}, 128));
    assertFalse(RuinRules.pumping(speeds, turns, new Long[] {7L, 7L, 8L, 7L}, 128), "Four engines are not Terra's");
    assertFalse(RuinRules.pumping(speeds, turns, new Long[] {7L, null, 7L, 7L}, 128));
    assertFalse(RuinRules.pumping(new float[] {-128, -128, -128, 128}, turns, new Long[] {7L, 7L, 7L, 7L}, 128),
        "One pump the wrong way stops the drain");
    assertFalse(RuinRules.pumping(new float[] {-127, 128, -128, 128}, turns, new Long[] {7L, 7L, 7L, 7L}, 128));
    assertFalse(RuinRules.pumping(new float[0], new int[0], new Long[0], 128));
  }

  @Test
  void theSealOpensAtAnEighthAndItsQuarterTurns() {
    for (float angle : new float[] {45, 135, 225, 315, 42, 48, -45, 405, 47.9f})
      assertTrue(RuinRules.aligned(angle, 45, 3), "Aligned at " + angle);
    for (float angle : new float[] {0, 90, 180, 41.5f, 48.5f, 30, 60, 89})
      assertFalse(RuinRules.aligned(angle, 45, 3), "Not aligned at " + angle);
    assertTrue(RuinRules.aligned(88.5f, 0, 3), "Target 0 wraps around 90");
  }

  @Test
  void theSandboxIsADiscAndTheResetWaits() {
    int[] size = {9, 5, 9};
    assertTrue(RuinRules.inSandbox(0, 2, 0, -4, 2, -4, size, 4.5), "The centre");
    assertTrue(RuinRules.inSandbox(3, 2, 3, -4, 2, -4, size, 4.5), "Above a pump port (distance 4.24)");
    assertTrue(RuinRules.inSandbox(4, 6, 2, -4, 2, -4, size, 4.5), "The rim at 4.47");
    assertFalse(RuinRules.inSandbox(4, 2, 3, -4, 2, -4, size, 4.5), "Past the rim (5.0)");
    assertFalse(RuinRules.inSandbox(0, 7, 0, -4, 2, -4, size, 4.5), "Above the box");
    assertTrue(RuinRules.inSandbox(4, 2, 4, -4, 2, -4, size, 0), "No radius: the whole box");
    assertFalse(RuinRules.resetDue(-1, 50_000, 12_000), "Someone is inside");
    assertFalse(RuinRules.resetDue(40_000, 51_999, 12_000));
    assertTrue(RuinRules.resetDue(40_000, 52_000, 12_000), "Ten minutes empty");
  }
}
