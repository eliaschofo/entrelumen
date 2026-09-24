package dev.entrelumen;

import java.util.List;
import java.util.Set;

/** Read-only commissioning projections and the persisted Ark activation. */
public final class CampaignMilestones {
  /**
   * Act V, "The Ark": the industrial preparation, the six modules, the six batches and the
   * activation that forges the Light Key (renumbered from act 6 on 24 September 2026).
   */
  public static final int ARK_ACT = 5;
  public static final List<String> PHASE_IDS = List.of(
      "ark_calibrated", "ark_contained", "ark_renewed", "ark_routed",
      "ark_provisioned", "ark_charted");
  public static final String LAST_HORIZON = "last_horizon";
  public static final Set<String> RESERVED_IDS = Set.copyOf(PHASE_IDS);
  /**
   * Milestones the server observes instead of taking deliveries: dimension arrivals, the team's own
   * arrival in Solsticio and the Heart a Sun Spirit dropped. They can be project prerequisites but
   * never projects.
   */
  public static final Set<String> OBSERVATIONS;

  static {
    Set<String> observations = new java.util.TreeSet<>(Expeditions.IDS);
    observations.add(Expeditions.SOLSTICIO_ARRIVAL);
    observations.add(HeliodorHeartRules.RECOVERED);
    OBSERVATIONS = Set.copyOf(observations);
  }
  public static final Set<String> MODULE_IDS = ArkCommissioning.STEPS.stream()
      .map(ArkCommissioning.Step::module).collect(java.util.stream.Collectors.toUnmodifiableSet());

  private CampaignMilestones() {}

  public static boolean allModules(Campaigns.Campaign campaign) {
    return campaign.completed.containsAll(MODULE_IDS);
  }

  public static boolean isComplete(Campaigns.Campaign campaign, String id) {
    // A Heart already delivered to the Atlas was recovered, however it arrived.
    if (HeliodorHeartRules.RECOVERED.equals(id)) return HeliodorHeartRules.recovered(campaign);
    int step = PHASE_IDS.indexOf(id);
    if (step >= 0)
      return !campaign.archived && campaign.act >= ARK_ACT && allModules(campaign)
          && campaign.arkPhase > step;
    return campaign.completed.contains(id);
  }

  public static boolean canFinish(Campaigns.Campaign campaign) {
    return !campaign.archived && campaign.act == ARK_ACT
        && campaign.completed.contains("world_network")
        && campaign.completed.contains("end_arrival")
        && allModules(campaign) && campaign.arkPhase == PHASE_IDS.size()
        && !campaign.completed.contains(LAST_HORIZON);
  }

  /**
   * Records the activation and opens act VI, Solsticio, in the same mutation. Caller must first
   * validate the live team, reach and physical structure.
   */
  static boolean finish(Campaigns.Campaign campaign) {
    if (!canFinish(campaign) || !campaign.completed.add(LAST_HORIZON)) return false;
    campaign.act = Campaigns.FINAL_ACT;
    return true;
  }
}
