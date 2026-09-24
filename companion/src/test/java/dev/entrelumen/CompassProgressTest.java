package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CompassProgressTest {
  private static CompassTargets.Objective objective(String id, int act, String milestone, boolean lore) {
    return new CompassTargets.Objective(id, act, CompassTargets.Kind.STRUCTURE,
        new CompassTargets.Target(CompassTargets.TargetType.DIMENSION, "", "minecraft:the_end", null, 0),
        new CompassTargets.Condition(CompassTargets.ConditionType.MILESTONE, milestone, 1), lore);
  }

  private static final List<CompassTargets.Objective> LIST = List.of(
      objective("ruin", 1, "atlas_awakened", true),
      objective("villages", 1, "field_survey", false),
      objective("forest", 2, "twilight_arrival", true),
      objective("dragon", 3, "end_arrival", true));

  private static CompassProgress.Evaluation evaluate(int act, Set<String> reached, Set<String> done) {
    return CompassProgress.evaluate(LIST, act, reached, condition -> done.contains(condition.value()));
  }

  @Test
  void pointsAtTheFirstUnreachedObjective() {
    var evaluation = evaluate(1, Set.of(), Set.of());
    assertEquals("ruin", evaluation.current().id());
    assertFalse(evaluation.lockedByAct());
    assertTrue(evaluation.newlyReached().isEmpty());
  }

  @Test
  void meetingTheConditionMovesToTheNextObjective() {
    var reached = new TreeSet<String>();
    var first = evaluate(1, reached, Set.of("atlas_awakened"));
    assertEquals(List.of("ruin"), first.newlyReached());
    assertEquals("villages", first.current().id());
    reached.addAll(first.newlyReached());
    // Latched: losing the condition later never rewinds the compass.
    var again = evaluate(1, reached, Set.of());
    assertEquals("villages", again.current().id());
    assertTrue(again.newlyReached().isEmpty());
  }

  @Test
  void aTeamThatDidThingsEarlySkipsAheadInOneStep() {
    var evaluation = evaluate(2, Set.of(), Set.of("atlas_awakened", "field_survey", "twilight_arrival"));
    assertEquals(List.of("ruin", "villages", "forest"), evaluation.newlyReached());
    assertTrue(evaluation.lockedByAct(), "Act 3 is not open yet");
    assertEquals("dragon", evaluation.current().id());
  }

  @Test
  void aLaterActHoldsTheCompassWithoutTestingItsCondition() {
    var tested = new ArrayList<String>();
    var evaluation = CompassProgress.evaluate(LIST, 1, Set.of("ruin", "villages"), condition -> {
      tested.add(condition.value());
      return true;
    });
    assertTrue(evaluation.lockedByAct());
    assertEquals("forest", evaluation.current().id());
    assertTrue(tested.isEmpty(), "A locked objective must not latch early");
  }

  @Test
  void everythingReachedMeansComplete() {
    var evaluation = evaluate(6, Set.of("ruin", "villages", "forest", "dragon"), Set.of());
    assertTrue(evaluation.complete());
    assertNull(evaluation.current());
  }

  @Test
  void unknownReachedIdsAreIgnoredAndLoreFollowsCampaignOrder() {
    var reached = Set.of("dragon", "retired_objective", "ruin", "villages");
    assertEquals("forest", evaluate(3, reached, Set.of()).current().id());
    assertEquals(List.of("ruin", "dragon"), CompassProgress.lore(LIST, reached));
  }
}
