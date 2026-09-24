package dev.entrelumen;

import static dev.entrelumen.ApotheosisTiers.Tier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApotheosisTiersTest {
  private static Campaigns.Campaign at(int act) {
    var campaign = new Campaigns.Campaign();
    campaign.act = act;
    return campaign;
  }

  /** Acts renumbered 24 September 2026: I-II Haven, III Frontier, IV Ascent, V Summit, VI Pinnacle. */
  @Test
  void tiersFollowCompletedActsAndTheArkActivation() {
    assertEquals(List.of(HAVEN), ApotheosisTiers.reached(at(1)));
    assertEquals(List.of(HAVEN), ApotheosisTiers.reached(at(2)));
    assertEquals(List.of(HAVEN, FRONTIER), ApotheosisTiers.reached(at(3)));
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT), ApotheosisTiers.reached(at(4)));
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT, SUMMIT), ApotheosisTiers.reached(at(5)));
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT, SUMMIT, PINNACLE), ApotheosisTiers.reached(at(6)));
    // The activation opens act VI in the same mutation; the milestone alone also counts.
    var activated = at(5);
    activated.completed.add(CampaignMilestones.LAST_HORIZON);
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT, SUMMIT, PINNACLE), ApotheosisTiers.reached(activated));
    assertEquals(5, ApotheosisTiers.SUMMIT_ACT);
    assertEquals(Campaigns.FINAL_ACT, ApotheosisTiers.PINNACLE_ACT);
  }

  @Test
  void aCampaignMigratedFromTheOldNumberingKeepsItsTier() {
    // Old act 6 (building the Ark) was Summit; after the migration it is act 5, still Summit.
    var building = at(6);
    CampaignData.migrateActs(building);
    assertEquals(5, building.act);
    assertEquals(SUMMIT, ApotheosisTiers.target(building, tier -> true));
    // Old act 6 with the activation stays act 6: Pinnacle, as before.
    var activated = at(6);
    activated.completed.add(CampaignMilestones.LAST_HORIZON);
    CampaignData.migrateActs(activated);
    assertEquals(6, activated.act);
    assertEquals(PINNACLE, ApotheosisTiers.target(activated, tier -> true));
  }

  @Test
  void missingOrArchivedCampaignsOnlyOpenHavenAndNothingIsMutated() {
    assertEquals(List.of(HAVEN), ApotheosisTiers.reached(null));
    var archived = at(6);
    archived.completed.add(CampaignMilestones.LAST_HORIZON);
    archived.archived = true;
    var before = archived.copy();
    assertEquals(List.of(HAVEN), ApotheosisTiers.reached(archived));
    assertEquals(before.act, archived.act);
    assertEquals(before.completed, archived.completed);
  }

  @Test
  void theCampaignImposesExactlyOneTierPerStage() {
    java.util.function.Predicate<ApotheosisTiers.Tier> all = tier -> true;
    assertEquals(HAVEN, ApotheosisTiers.target(at(1), all));
    assertEquals(HAVEN, ApotheosisTiers.target(at(2), all));
    assertEquals(FRONTIER, ApotheosisTiers.target(at(3), all));
    assertEquals(ASCENT, ApotheosisTiers.target(at(4), all));
    assertEquals(SUMMIT, ApotheosisTiers.target(at(5), all));
    assertEquals(PINNACLE, ApotheosisTiers.target(at(6), all));
    var ending = at(5);
    ending.completed.add(CampaignMilestones.LAST_HORIZON);
    assertEquals(PINNACLE, ApotheosisTiers.target(ending, all));
    // A fresh campaign (a player back in a personal team) reaches only Haven.
    assertEquals(HAVEN, ApotheosisTiers.target(new Campaigns.Campaign(), all));
  }

  @Test
  void theStoryTierOnlyRises() {
    for (var recorded : ApotheosisTiers.Tier.values())
      for (var campaign : ApotheosisTiers.Tier.values()) {
        var held = ApotheosisTiers.story(recorded, campaign);
        assertEquals(Math.max(recorded.ordinal(), campaign.ordinal()), held.ordinal());
        assertTrue(held.ordinal() >= recorded.ordinal(), "the tier went down");
      }
    // Leaving a party at Ascent for a fresh personal campaign keeps Ascent.
    assertEquals(ASCENT, ApotheosisTiers.story(ASCENT, ApotheosisTiers.target(new Campaigns.Campaign(), t -> true)));
    // Outside any campaign the record stays; with neither there is nothing to apply.
    assertEquals(SUMMIT, ApotheosisTiers.story(SUMMIT, null));
    assertEquals(FRONTIER, ApotheosisTiers.story(null, FRONTIER));
    assertNull(ApotheosisTiers.story(null, null));
  }

  @Test
  void targetSkipsMissingUnlocksAndLeavesPlayersOutsideCampaignsAlone() {
    // A datapack removed Summit: Act V stays on Ascent rather than a tier nobody can unlock.
    assertEquals(ASCENT, ApotheosisTiers.target(at(5), tier -> tier != SUMMIT));
    assertNull(ApotheosisTiers.target(at(6), tier -> false));
    assertNull(ApotheosisTiers.target(null, tier -> true));
    var archived = at(6);
    archived.archived = true;
    assertNull(ApotheosisTiers.target(archived, tier -> true));
    // Without Pinnacle, act VI falls back to Summit, the highest tier it can unlock.
    var ending = at(6);
    ending.completed.add(CampaignMilestones.LAST_HORIZON);
    assertEquals(SUMMIT, ApotheosisTiers.target(ending, tier -> tier != PINNACLE));
  }

  @Test
  void tierNamesUseApotheosisKeys() {
    assertEquals("text.apotheosis.world_tier.frontier", FRONTIER.nameKey);
    assertEquals("text.apotheosis.world_tier.pinnacle", PINNACLE.nameKey);
  }

  @Test
  void advancementIdsMatchApotheosisProgressionTree() {
    assertEquals("apotheosis:progression/haven", HAVEN.advancement.toString());
    assertEquals("apotheosis:progression/frontier", FRONTIER.advancement.toString());
    assertEquals("apotheosis:progression/ascent", ASCENT.advancement.toString());
    assertEquals("apotheosis:progression/summit", SUMMIT.advancement.toString());
    assertEquals("apotheosis:progression/pinnacle", PINNACLE.advancement.toString());
  }
}
