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

  @Test
  void tiersFollowCompletedActsAndTheArkActivation() {
    assertEquals(List.of(HAVEN), ApotheosisTiers.reached(at(1)));
    assertEquals(List.of(HAVEN), ApotheosisTiers.reached(at(2)));
    assertEquals(List.of(HAVEN, FRONTIER), ApotheosisTiers.reached(at(3)));
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT), ApotheosisTiers.reached(at(4)));
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT), ApotheosisTiers.reached(at(5)));
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT, SUMMIT), ApotheosisTiers.reached(at(6)));
    var ending = at(6);
    ending.completed.add(CampaignMilestones.LAST_HORIZON);
    assertEquals(List.of(HAVEN, FRONTIER, ASCENT, SUMMIT, PINNACLE), ApotheosisTiers.reached(ending));
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
    assertEquals(ASCENT, ApotheosisTiers.target(at(5), all));
    assertEquals(SUMMIT, ApotheosisTiers.target(at(6), all));
    var ending = at(6);
    ending.completed.add(CampaignMilestones.LAST_HORIZON);
    assertEquals(PINNACLE, ApotheosisTiers.target(ending, all));
    // A fresh campaign (a player back in a personal team) imposes Haven, which lowers the tier.
    assertEquals(HAVEN, ApotheosisTiers.target(new Campaigns.Campaign(), all));
  }

  @Test
  void targetSkipsMissingUnlocksAndLeavesPlayersOutsideCampaignsAlone() {
    // A datapack removed Summit: Act VI stays on Ascent rather than a tier nobody can unlock.
    assertEquals(ASCENT, ApotheosisTiers.target(at(6), tier -> tier != SUMMIT));
    assertNull(ApotheosisTiers.target(at(6), tier -> false));
    assertNull(ApotheosisTiers.target(null, tier -> true));
    var archived = at(6);
    archived.archived = true;
    assertNull(ApotheosisTiers.target(archived, tier -> true));
    // Pinnacle alone never skips the campaign's own act tier.
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
