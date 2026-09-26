package dev.entrelumen;

import java.util.Set;

/** Read-only milestone projections and the persisted Ark activation. */
public final class CampaignMilestones {
  /**
   * Act V, "The Ark": the industrial preparation, the last two modules and the activation that forges
   * the Light Key (renumbered from act 6 on 24 September 2026). Since Ark v2 (25 September 2026) each
   * act delivers modules and there are no batches.
   */
  public static final int ARK_ACT = 5;
  public static final String LAST_HORIZON = "last_horizon";
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

  /** The six module projects, one per module (their IDs are the modules' IDs). */
  public static final Set<String> MODULE_IDS = Set.copyOf(ArkRules.moduleIds());

  private CampaignMilestones() {}

  public static boolean isComplete(Campaigns.Campaign campaign, String id) {
    // A Heart already delivered to the Atlas was recovered, however it arrived.
    if (HeliodorHeartRules.RECOVERED.equals(id)) return HeliodorHeartRules.recovered(campaign);
    return campaign.completed.contains(id);
  }

  /**
   * The activation's conditions: act V, the team's Ark standing with its six modules and its
   * controller, the last project of act V and the journey to the End ({@link ArkRules#checklist}).
   */
  public static boolean canFinish(Campaigns.Campaign campaign, ArkRules.Status ark) {
    return !campaign.archived && campaign.act == ARK_ACT
        && ArkRules.checklistDone(ark, campaign.completed)
        && !campaign.completed.contains(LAST_HORIZON);
  }

  /**
   * Records the activation and opens act VI, Solsticio, in the same mutation. Caller must first
   * validate the live team, reach and the controller.
   */
  static boolean finish(Campaigns.Campaign campaign, ArkRules.Status ark) {
    if (!canFinish(campaign, ark) || !campaign.completed.add(LAST_HORIZON)) return false;
    campaign.act = Campaigns.FINAL_ACT;
    return true;
  }
}
