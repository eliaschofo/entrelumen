package dev.entrelumen;

/**
 * Pure rules of the Heart of Heliodor (story bible, 24 September 2026): the crystal the Sun Spirit
 * confiscated from Bodhi. One per campaign and world, recorded in the campaign's own milestones, so a
 * party inherits it from its founder like every other observation and an archived party keeps it.
 *
 * <ol>
 *   <li>{@link #RECOVERED}: a Sun Spirit fell to the team and dropped its Heart (observation).</li>
 *   <li>{@link #PROJECT}: the act IV delivery. The Atlas keeps the Heart; that is what makes it speak
 *       clearly, and closing act IV requires it.</li>
 *   <li>{@link #RELEASED}: in act VI the Atlas hands the Heart back once, so Bodhi can bless it into
 *       the third portal relic ({@code entrelumen:heliodor_relic_3}).</li>
 * </ol>
 */
public final class HeliodorHeartRules {
  public static final String RECOVERED = "heart_recovered";
  public static final String PROJECT = "heliodor_heart";
  public static final String RELEASED = "heart_released";

  private HeliodorHeartRules() {}

  /** The team holds or held its Heart: it dropped for them, or it is already in their Atlas. */
  public static boolean recovered(Campaigns.Campaign campaign) {
    return campaign.completed.contains(RECOVERED) || campaign.completed.contains(PROJECT);
  }

  /**
   * Records a defeated Sun Spirit's Heart for this campaign. False, and no Heart, when the campaign is
   * archived or already recovered one: a second Sun Spirit never drops another.
   */
  public static boolean claim(Campaigns.Campaign campaign) {
    if (campaign == null || campaign.archived || recovered(campaign)) return false;
    return campaign.completed.add(RECOVERED);
  }

  /** The Heart sits in the team's Atlas: delivered and not handed back yet. */
  public static boolean inAtlas(Campaigns.Campaign campaign) {
    return campaign.completed.contains(PROJECT) && !campaign.completed.contains(RELEASED);
  }

  /** The Atlas may hand the Heart back: act VI (Solsticio), active campaign, Heart still inside. */
  public static boolean canRelease(Campaigns.Campaign campaign) {
    return !campaign.archived && campaign.act >= Campaigns.FINAL_ACT && inAtlas(campaign);
  }

  /** Records the hand-back; the caller gives the item only when this returns true. */
  public static boolean release(Campaigns.Campaign campaign) {
    return canRelease(campaign) && campaign.completed.add(RELEASED);
  }
}
