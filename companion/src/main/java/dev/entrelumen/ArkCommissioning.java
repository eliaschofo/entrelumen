package dev.entrelumen;

import java.util.*;
import java.util.function.Consumer;

/** Recoverable, capped deposits for the current Ark step. Callers persist successful changes. */
public final class ArkCommissioning {
  public record Step(String module, String phase, Map<String, Integer> requirements) {
    public Step {
      requirements = Map.copyOf(requirements);
    }
  }

  public static final List<Step> STEPS = List.of(
      new Step("engineering_module", "calibrate", Map.of("entrelumen:calibration_frame", 4, "entrelumen:power_regulator", 2)),
      new Step("arcane_module", "stabilize", Map.of("entrelumen:containment_seal", 2)),
      new Step("nature_module", "stabilize", Map.of("entrelumen:ecosystem_capsule", 2)),
      new Step("logistics_module", "provision", Map.of("entrelumen:routing_matrix", 2)),
      new Step("habitation_module", "provision", Map.of("entrelumen:ration_bundle", 8)),
      new Step("exploration_module", "chart", Map.of("entrelumen:horizon_chart", 1)));

  private ArkCommissioning() {}

  public static boolean eligible(Campaigns.Campaign campaign) {
    return !campaign.archived && campaign.act == 6
        && campaign.arkPhase >= 0 && campaign.arkPhase < STEPS.size()
        && STEPS.stream().allMatch(step -> campaign.completed.contains(step.module()));
  }

  public static Map<String, Integer> missing(Campaigns.Campaign campaign) {
    if (campaign.arkPhase < 0 || campaign.arkPhase >= STEPS.size()) return Map.of();
    Map<String, Integer> missing = new TreeMap<>();
    STEPS.get(campaign.arkPhase).requirements().forEach((item, cost) -> {
      int remaining = cost - Math.clamp(campaign.arkDeposits.getOrDefault(item, 0), 0, cost);
      if (remaining > 0) missing.put(item, remaining);
    });
    return Map.copyOf(missing);
  }

  public static boolean deposit(Campaigns.Campaign campaign, int expectedStep,
      Map<String, Integer> inventory, Consumer<Map<String, Integer>> consume) {
    if (!eligible(campaign) || campaign.arkPhase != expectedStep) return false;
    Map<String, Integer> accepted = new TreeMap<>();
    missing(campaign).forEach((item, remaining) -> {
      int count = Math.min(remaining, inventory.getOrDefault(item, 0));
      if (count > 0) accepted.put(item, count);
    });
    if (accepted.isEmpty()) return false;
    consume.accept(Map.copyOf(accepted));
    accepted.forEach((item, count) -> campaign.arkDeposits.merge(item, count, Integer::sum));
    if (missing(campaign).isEmpty()) {
      campaign.arkDeposits.clear();
      campaign.arkPhase++;
    }
    return true;
  }
}
