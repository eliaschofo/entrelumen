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
  void advancementIdsMatchApotheosisProgressionTree() {
    assertEquals("apotheosis:progression/haven", HAVEN.advancement.toString());
    assertEquals("apotheosis:progression/frontier", FRONTIER.advancement.toString());
    assertEquals("apotheosis:progression/ascent", ASCENT.advancement.toString());
    assertEquals("apotheosis:progression/summit", SUMMIT.advancement.toString());
    assertEquals("apotheosis:progression/pinnacle", PINNACLE.advancement.toString());
  }
}
