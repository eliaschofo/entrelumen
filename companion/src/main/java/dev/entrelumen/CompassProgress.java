package dev.entrelumen;

import java.util.*;
import java.util.function.Predicate;

/** Pure objective selection: the first unreached objective of the team, in listed order. */
public final class CompassProgress {
  /**
   * {@code current} is null when every objective is reached. When {@code lockedByAct} is true,
   * {@code current} is the next objective, which the campaign act does not reveal yet.
   */
  public record Evaluation(
      CompassTargets.Objective current, List<String> newlyReached, boolean lockedByAct) {
    public boolean complete() {
      return current == null;
    }
  }

  private CompassProgress() {}

  /**
   * Walks the list in order. An objective whose condition already holds is latched as reached and
   * the walk continues, so a team that did things early skips ahead at once. The walk stops at the
   * first objective whose act exceeds the campaign act, without testing its condition.
   */
  public static Evaluation evaluate(
      List<CompassTargets.Objective> objectives,
      int act,
      Set<String> reached,
      Predicate<CompassTargets.Condition> satisfied) {
    List<String> newlyReached = new ArrayList<>();
    for (var objective : objectives) {
      if (reached.contains(objective.id())) continue;
      if (objective.act() > act) return new Evaluation(objective, List.copyOf(newlyReached), true);
      if (satisfied.test(objective.advanceWhen())) {
        newlyReached.add(objective.id());
        continue;
      }
      return new Evaluation(objective, List.copyOf(newlyReached), false);
    }
    return new Evaluation(null, List.copyOf(newlyReached), false);
  }

  /** Reached objectives that carry lore, in campaign order: the Atlas's recovered fragments. */
  public static List<String> lore(List<CompassTargets.Objective> objectives, Set<String> reached) {
    return objectives.stream()
        .filter(o -> o.lore() && reached.contains(o.id()))
        .map(CompassTargets.Objective::id)
        .toList();
  }
}
