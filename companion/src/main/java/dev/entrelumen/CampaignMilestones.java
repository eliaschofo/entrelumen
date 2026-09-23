package dev.entrelumen;

import java.util.List;
import java.util.Set;

/** Read-only commissioning projections and the persisted first ending. */
public final class CampaignMilestones {
  public static final List<String> PHASE_IDS = List.of(
      "ark_calibrated", "ark_contained", "ark_renewed", "ark_routed",
      "ark_provisioned", "ark_charted");
  public static final String LAST_HORIZON = "last_horizon";
  public static final Set<String> RESERVED_IDS = Set.copyOf(PHASE_IDS);
  public static final Set<String> MODULE_IDS = ArkCommissioning.STEPS.stream()
      .map(ArkCommissioning.Step::module).collect(java.util.stream.Collectors.toUnmodifiableSet());

  private CampaignMilestones() {}

  public static boolean allModules(Campaigns.Campaign campaign) {
    return campaign.completed.containsAll(MODULE_IDS);
  }

  public static boolean isComplete(Campaigns.Campaign campaign, String id) {
    int step = PHASE_IDS.indexOf(id);
    if (step >= 0)
      return !campaign.archived && campaign.act == 6 && allModules(campaign)
          && campaign.arkPhase > step;
    return campaign.completed.contains(id);
  }

  public static boolean canFinish(Campaigns.Campaign campaign) {
    return !campaign.archived && campaign.act == 6
        && campaign.completed.contains("world_network")
        && campaign.completed.contains("end_arrival")
        && allModules(campaign) && campaign.arkPhase == PHASE_IDS.size()
        && !campaign.completed.contains(LAST_HORIZON);
  }

  /** Caller must first validate the live team, reach and physical structure. */
  static boolean finish(Campaigns.Campaign campaign) {
    return canFinish(campaign) && campaign.completed.add(LAST_HORIZON);
  }
}
