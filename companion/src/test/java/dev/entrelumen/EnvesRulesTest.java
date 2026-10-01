package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesLayout.Role;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnvesRulesTest {
  @Test
  void theStairAndTheGateAnswerOnlyAFrontierCampaign() {
    var campaign = new Campaigns.Campaign();
    campaign.act = 2;
    assertFalse(EnvesRules.frontier(ApotheosisTiers.reached(campaign)));
    campaign.act = ApotheosisTiers.FRONTIER_ACT;
    assertTrue(EnvesRules.frontier(ApotheosisTiers.reached(campaign)));
    campaign.archived = true;
    assertFalse(EnvesRules.frontier(ApotheosisTiers.reached(campaign)), "an archived campaign opens nothing");
  }

  @Test
  void aPlayerPicksAnyTierUpToTheirs() {
    assertEquals(List.of(Tier.HAVEN, Tier.FRONTIER), EnvesRules.choices(Tier.FRONTIER));
    assertEquals(List.of(Tier.values()), EnvesRules.choices(Tier.PINNACLE));
    assertTrue(EnvesRules.validChoice(Tier.HAVEN, Tier.SUMMIT));
    assertTrue(EnvesRules.validChoice(Tier.SUMMIT, Tier.SUMMIT));
    assertFalse(EnvesRules.validChoice(Tier.PINNACLE, Tier.SUMMIT));
    assertFalse(EnvesRules.validChoice(null, Tier.SUMMIT));
    assertTrue(EnvesRules.choices(null).isEmpty());
  }

  @Test
  void eachMemberAddsThreeFallsOnTheirFirstEntryOnlyAndTheLastFallEndsIt() {
    assertEquals(3, EnvesRules.joinGrant(true, 3), "a first entry adds three falls");
    assertEquals(0, EnvesRules.joinGrant(false, 3), "a later entry adds nothing");
    assertEquals(1, EnvesRules.joinGrant(true, 0), "a member always adds at least one");
    int left = EnvesRules.joinGrant(true, 3) + EnvesRules.joinGrant(true, 3); // two members, six falls
    for (int fall = 1; fall < 6; fall++) {
      left = EnvesRules.afterFall(left);
      assertFalse(EnvesRules.exhausted(left), "fall " + fall + " of six keeps the attempt");
    }
    left = EnvesRules.afterFall(left);
    assertTrue(EnvesRules.exhausted(left), "the sixth fall empties the pool");
    assertEquals(0, EnvesRules.afterFall(left), "never below zero");
  }

  @Test
  void anAttemptEndsTenMinutesAfterTheLastMemberLeft() {
    long limit = 10 * 60 * 20;
    assertFalse(EnvesRules.abandoned(-1, 1_000_000, limit), "someone is inside");
    assertFalse(EnvesRules.abandoned(100, 100 + limit - 1, limit));
    assertTrue(EnvesRules.abandoned(100, 100 + limit, limit));
  }

  @Test
  void theNextFloorComesAtTheGuardAndTheStairOpensWhenEverySealBurns() {
    assertFalse(EnvesRules.nextFloorDue(false, false, 1, 2));
    assertTrue(EnvesRules.nextFloorDue(true, false, 0, 2), "the guard room");
    assertTrue(EnvesRules.nextFloorDue(false, true, 0, 2), "the stairwell itself");
    assertTrue(EnvesRules.nextFloorDue(false, false, 2, 2), "every seal lit");
    assertFalse(EnvesRules.stairOpens(1, 2, true));
    assertFalse(EnvesRules.stairOpens(2, 2, false), "never over a floor that is not there yet");
    assertTrue(EnvesRules.stairOpens(3, 3, true));
  }

  @Test
  void jumpsAndKnockbacksPassButFlightIsPulledDown() {
    var lift = new EnvesRules.Lift();
    // A jump: rises for a handful of ticks, then falls, then lands.
    double[] jump = {0.42, 0.33, 0.25, 0.17, 0.1, 0.02, -0.06, -0.14, -0.22, -0.3};
    for (int i = 0; i < 3; i++) {
      for (double dy : jump) assertFalse(lift.observe(true, false, dy));
      assertFalse(lift.observe(false, false, 0));
    }
    // A jetpack: keeps rising.
    boolean pulled = false;
    for (int t = 0; t < 40 && !pulled; t++) pulled = lift.observe(true, false, 0.15);
    assertTrue(pulled, "sustained rise");
    // Hovering in place.
    pulled = false;
    for (int t = 0; t < 60 && !pulled; t++) pulled = lift.observe(true, false, 0.0);
    assertTrue(pulled, "hovering");
    // Levitation and slow falling are the game's own, not flight.
    for (int t = 0; t < 100; t++) assertFalse(lift.observe(true, true, 0.1));
  }

  @Test
  void deniedCommandsMatchTheirRootWithOrWithoutSlashOrNamespace() {
    Set<String> denied = EnvesConfig.DEFAULTS.deniedCommands();
    assertTrue(EnvesRules.commandDenied("/home", denied));
    assertTrue(EnvesRules.commandDenied("rtp", denied));
    assertTrue(EnvesRules.commandDenied("/tpa Elias", denied));
    assertTrue(EnvesRules.commandDenied("/ftbessentials:home base", denied));
    assertFalse(EnvesRules.commandDenied("/homework", denied));
    assertFalse(EnvesRules.commandDenied("/entrelumen enves", denied));
    assertFalse(EnvesRules.commandDenied("", denied));
  }

  @Test
  void theMapShowsExploredCellsInFullAndGlimpsedOnesAsABlur() {
    var floor = EnvesLayout.descent(2609).floor(1);
    BitSet explored = new BitSet();
    explored.set(floor.start());
    BitSet lit = new BitSet();
    var known = EnvesRules.known(floor, explored, lit);
    var start = known.stream().filter(k -> k.cell() == floor.start()).findFirst().orElseThrow();
    assertTrue(start.explored());
    assertEquals(Role.START, start.role());
    assertEquals(floor.doors(floor.start()), start.doors());
    assertEquals(floor.doorCount(floor.start()) + 1, known.size(), "the start and one blur per door");
    for (var cell : known) {
      if (cell.cell() == floor.start()) continue;
      assertFalse(cell.explored());
      assertNull(cell.role(), "a glimpsed room shows no role (the stairs stay hidden)");
      assertEquals(0, cell.doors(), "nor its doors");
    }
    // The stairs only appear once found.
    assertTrue(known.stream().noneMatch(k -> k.role() == Role.EXIT));
    explored.set(floor.exit());
    assertTrue(EnvesRules.known(floor, explored, lit).stream().anyMatch(k -> k.role() == Role.EXIT));
  }

  @Test
  void nobodyStandsDeeperThanTheOpenStairsAllow() {
    assertEquals(1, EnvesRules.reachableDepth(d -> false, 5), "with every stair shut only floor I is walkable");
    assertEquals(2, EnvesRules.reachableDepth(d -> d == 1, 5));
    assertEquals(3, EnvesRules.reachableDepth(d -> d <= 2, 5));
    assertEquals(1, EnvesRules.reachableDepth(d -> d == 2, 5), "an open stair below a shut one leads nowhere");
    assertEquals(5, EnvesRules.reachableDepth(d -> true, 5), "never past the last floor");
  }

  @Test
  void givingUpAsksTwiceWithinTenSeconds() {
    assertFalse(EnvesRules.giveUpConfirmed(null, 1000), "the first ask only arms it");
    assertTrue(EnvesRules.giveUpConfirmed(1000L, 1000));
    assertTrue(EnvesRules.giveUpConfirmed(1000L, 1000 + EnvesRules.GIVE_UP_WINDOW));
    assertFalse(EnvesRules.giveUpConfirmed(1000L, 1001 + EnvesRules.GIVE_UP_WINDOW), "ten seconds later it asks again");
    assertFalse(EnvesRules.giveUpConfirmed(1000L, 999), "a clock that went back confirms nothing");
    assertEquals(200, EnvesRules.GIVE_UP_WINDOW);
  }

  @Test
  void whileTeammatesAreInsideOnlyThePayerTheOwnerOrSomeoneInsideGivesUp() {
    assertTrue(EnvesRules.mayGiveUp(false, false, false, 0), "with nobody else inside any member may");
    assertFalse(EnvesRules.mayGiveUp(false, false, false, 2), "a member outside cannot end a run others are playing");
    assertTrue(EnvesRules.mayGiveUp(true, false, false, 2), "the payer may");
    assertTrue(EnvesRules.mayGiveUp(false, true, false, 1), "the party owner may");
    assertTrue(EnvesRules.mayGiveUp(false, false, true, 1), "a member inside may");
  }

  @Test
  void onlyLongMovesAreCheckedForWallsAndALostEchoWaitsFiveSeconds() {
    assertFalse(EnvesRules.longMove(4.0), "two blocks in a tick is still a sprint jump");
    assertTrue(EnvesRules.longMove(4.01));
    assertFalse(EnvesRules.lostLongEnough(100, 199));
    assertTrue(EnvesRules.lostLongEnough(100, 100 + EnvesRules.LOST_ECHO_TICKS));
  }
}
