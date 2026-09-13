package dev.entrelumen;

import java.util.*;

/** Pure server domain. Callers persist after every successful mutation. */
public final class Campaigns {
  public static final class Campaign {
    public int act = 1;
    public final Set<String> completed = new TreeSet<>();
    public int arkPhase;
    public final Map<String, Integer> arkDeposits = new TreeMap<>();
    public boolean archived;

    public Campaign copy() {
      Campaign copy = new Campaign();
      copy.act = act;
      copy.completed.addAll(completed);
      copy.arkPhase = arkPhase;
      copy.arkDeposits.putAll(arkDeposits);
      return copy;
    }
  }

  public final Map<UUID, Campaign> personal = new HashMap<>();
  public final Map<UUID, Campaign> parties = new HashMap<>();

  public Campaign personal(UUID player) {
    return personal.computeIfAbsent(player, ignored -> new Campaign());
  }

  public Campaign party(UUID team, UUID founder) {
    return parties.computeIfAbsent(team, ignored -> personal(founder).copy());
  }

  public Campaign current(UUID player, UUID party, UUID founder) {
    return party == null ? personal(player) : party(party, founder);
  }

  public void archive(UUID team) {
    if (parties.containsKey(team)) parties.get(team).archived = true;
  }

  public boolean recover(UUID team) {
    Campaign c = parties.get(team);
    if (c == null || !c.archived) return false;
    c.archived = false;
    return true;
  }

  public static boolean deliver(
      Campaign c,
      String project,
      int act,
      Map<String, Integer> requirements,
      Map<String, Integer> inventory,
      Runnable consume) {
    if (c.archived || c.act != act || c.completed.contains(project)) return false;
    for (var entry : requirements.entrySet())
      if (inventory.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
    consume.run();
    c.completed.add(project);
    return true;
  }

  public static boolean advance(Campaign c, Set<String> required) {
    if (c.archived || c.act >= 6 || required.isEmpty() || !c.completed.containsAll(required))
      return false;
    c.act++;
    return true;
  }

}
