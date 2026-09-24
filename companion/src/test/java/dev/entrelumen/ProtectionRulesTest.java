package dev.entrelumen;

import static dev.entrelumen.ProtectionRules.Action.*;
import static dev.entrelumen.ProtectionRules.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.entrelumen.ProtectionRules.ActGate;
import dev.entrelumen.ProtectionRules.Actor;
import dev.entrelumen.ProtectionRules.Box;
import dev.entrelumen.ProtectionRules.Hole;
import dev.entrelumen.ProtectionRules.Region;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectionRulesTest {
  private static final UUID TEAM = UUID.randomUUID(), OTHER = UUID.randomUUID();
  private static final ActGate SIX = new ActGate(6, "last_horizon");
  private static final Box CITY = new Box(-100, 0, -100, 100, 255, 100);
  private static final Box PLOT = new Box(10, 60, 10, 25, 100, 25);

  private static Actor team(UUID id, int act, String... milestones) {
    return new Actor(id, act, Set.of(milestones), false, false);
  }

  private static Region city(UUID plotOwner) {
    return new Region("entrelumen:solsticio", "entrelumen:solsticio", CITY, SIX,
        List.of(new Hole("plot_0", PLOT, plotOwner, true)));
  }

  @Test
  void everythingInsideTheBoxIsProtectedFromEveryone() {
    var regions = List.of(city(null));
    var unlocked = team(TEAM, 6, "last_horizon");
    for (var action : List.of(BREAK, PLACE, USE_ITEM, USE_LOCKED_BLOCK, ENTITY_BREAK))
      assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 0, 64, 0, action, unlocked), action.name());
    for (var action : List.of(EXPLOSION, FIRE, FLUID, PISTON, GRIEF))
      assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 0, 64, 0, action, Actor.ENVIRONMENT), action.name());
    assertEquals(ALLOW, ProtectionRules.decide(regions, 0, 64, 0, USE_FREE_BLOCK, team(null, 0)));
    assertEquals(ALLOW, ProtectionRules.decide(regions, 101, 64, 0, BREAK, team(null, 0)), "outside the box");
  }

  @Test
  void gatedUseOpensOnlyForTeamsThatReachedTheAct() {
    var regions = List.of(city(null));
    assertEquals(DENY_LOCKED, ProtectionRules.decide(regions, 0, 64, 0, USE_GATED_BLOCK, team(TEAM, 5)));
    assertEquals(DENY_LOCKED, ProtectionRules.decide(regions, 0, 64, 0, USE_GATED_BLOCK, team(TEAM, 6)),
        "act six without the Ark activation");
    assertEquals(DENY_LOCKED, ProtectionRules.decide(regions, 0, 64, 0, USE_GATED_BLOCK, team(null, 0)),
        "no team, no key");
    var unlocked = team(TEAM, 6, "last_horizon");
    assertEquals(ALLOW, ProtectionRules.decide(regions, 0, 64, 0, USE_GATED_BLOCK, unlocked));
    assertEquals(ALLOW, ProtectionRules.decide(regions, 0, 64, 0, ENTITY_USE, unlocked));
    assertEquals(ALLOW, ProtectionRules.decide(regions, 0, 64, 0, TAKE_DISPLAYED, unlocked));
    // Unlocking never lets anyone break the structure.
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 0, 64, 0, BREAK, unlocked));
    // Fake players never unlock gated blocks.
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 0, 64, 0, USE_GATED_BLOCK,
        new Actor(TEAM, 6, Set.of("last_horizon"), false, true)));
  }

  @Test
  void rulesForActGatesWithoutMilestone() {
    var ruin = new Region("entrelumen:ruin_act_2", "minecraft:overworld", new Box(0, 0, 0, 9, 9, 9),
        ActGate.act(2), List.of());
    assertEquals(DENY_LOCKED, ProtectionRules.decide(List.of(ruin), 1, 1, 1, USE_GATED_BLOCK, team(TEAM, 1)));
    assertEquals(ALLOW, ProtectionRules.decide(List.of(ruin), 1, 1, 1, USE_GATED_BLOCK, team(TEAM, 2)));
    assertEquals(ALLOW, ProtectionRules.decide(List.of(ruin), 1, 1, 1, USE_GATED_BLOCK, team(TEAM, 4)));
    assertTrue(ActGate.NONE.satisfiedBy(team(null, 0)));
  }

  @Test
  void anUnclaimedPlotStaysProtectedUntilClaimed() {
    var regions = List.of(city(null));
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 12, 64, 12, BREAK, team(TEAM, 6, "last_horizon")));
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 12, 64, 12, EXPLOSION, Actor.ENVIRONMENT));
  }

  @Test
  void aClaimedPlotBelongsToItsTeamOnly() {
    var regions = List.of(city(TEAM));
    var owner = team(TEAM, 6, "last_horizon");
    for (var action : List.of(BREAK, PLACE, USE_ITEM, USE_LOCKED_BLOCK, USE_GATED_BLOCK, ENTITY_BREAK))
      assertEquals(ALLOW, ProtectionRules.decide(regions, 12, 64, 12, action, owner), action.name());
    assertEquals(DENY_NOT_OWNER, ProtectionRules.decide(regions, 12, 64, 12, BREAK, team(OTHER, 6, "last_horizon")));
    assertEquals(DENY_NOT_OWNER, ProtectionRules.decide(regions, 12, 64, 12, USE_GATED_BLOCK, team(null, 0)));
    for (var action : List.of(EXPLOSION, FIRE, FLUID, PISTON))
      assertEquals(ALLOW, ProtectionRules.decide(regions, 12, 64, 12, action, Actor.ENVIRONMENT), action.name());
    // The plot's edges are exact: one block outside is the city again.
    assertEquals(ALLOW, ProtectionRules.decide(regions, 25, 100, 25, BREAK, owner));
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 26, 64, 12, BREAK, owner));
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 12, 101, 12, PLACE, owner));
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(regions, 12, 59, 12, BREAK, owner));
  }

  @Test
  void openHolesExemptEveryoneAndBypassOverridesAll() {
    var open = new Region("r", "minecraft:overworld", new Box(0, 0, 0, 20, 20, 20), SIX,
        List.of(new Hole("garden", new Box(5, 0, 5, 8, 20, 8), null, false)));
    assertEquals(ALLOW, ProtectionRules.decide(List.of(open), 6, 3, 6, BREAK, team(null, 0)));
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(List.of(open), 9, 3, 9, BREAK, team(null, 0)));
    var op = new Actor(null, 0, Set.of(), true, false);
    assertEquals(ALLOW, ProtectionRules.decide(List.of(open), 9, 3, 9, BREAK, op));
  }

  @Test
  void overlappingRegionsMustAllAgree() {
    var outer = city(TEAM);
    var inner = new Region("vault", "entrelumen:solsticio", new Box(10, 60, 10, 12, 62, 12), ActGate.act(6), List.of());
    var owner = team(TEAM, 6, "last_horizon");
    assertEquals(DENY_PROTECTED, ProtectionRules.decide(List.of(outer, inner), 11, 61, 11, BREAK, owner),
        "the plot hole of one region does not open another region");
    assertEquals(ALLOW, ProtectionRules.decide(List.of(outer, inner), 14, 61, 14, BREAK, owner));
  }

  @Test
  void fluidsFlowInsideTheStructureButNeverIntoIt() {
    var regions = List.of(city(TEAM));
    // City water spreading inside the city.
    assertTrue(ProtectionRules.fluidMayFlow(regions, 0, 64, 0, 1, 64, 0));
    // From the claimed plot into the city.
    assertFalse(ProtectionRules.fluidMayFlow(regions, 25, 64, 12, 26, 64, 12));
    // From outside the box into it.
    assertFalse(ProtectionRules.fluidMayFlow(regions, 101, 64, 0, 100, 64, 0));
    // Inside the plot.
    assertTrue(ProtectionRules.fluidMayFlow(regions, 12, 64, 12, 13, 64, 12));
    // From the city into the plot.
    assertTrue(ProtectionRules.fluidMayFlow(regions, 26, 64, 12, 25, 64, 12));
  }

  @Test
  void boxesRejectInversionAndKnowTheirEdges() {
    assertThrows(IllegalArgumentException.class, () -> new Box(1, 0, 0, 0, 0, 0));
    Box box = new Box(0, 0, 0, 0, 0, 0);
    assertTrue(box.contains(0, 0, 0));
    assertFalse(box.contains(1, 0, 0));
    assertTrue(box.intersects(new Box(0, 0, 0, 5, 5, 5)));
    assertFalse(box.intersects(new Box(1, 1, 1, 5, 5, 5)));
  }
}
