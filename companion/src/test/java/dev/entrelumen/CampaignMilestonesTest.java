package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CampaignMilestonesTest {
  private static Campaigns.Campaign ready() {
    var campaign = new Campaigns.Campaign();
    campaign.act = 6;
    campaign.completed.add("world_network");
    campaign.completed.add("end_arrival");
    campaign.completed.addAll(CampaignMilestones.MODULE_IDS);
    return campaign;
  }

  @Test
  void phasesAreDerivedFromCreditedStepsAndActiveCampaign() {
    var campaign = ready();
    for (int phase = 0; phase <= 6; phase++) {
      campaign.arkPhase = phase;
      for (int i = 0; i < CampaignMilestones.PHASE_IDS.size(); i++)
        assertEquals(i < phase,
            CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.get(i)));
    }
    campaign.completed.add(CampaignMilestones.PHASE_IDS.getFirst());
    campaign.arkPhase = 0;
    assertFalse(CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.getFirst()));
    campaign.arkPhase = 6;
    campaign.act = 5;
    assertFalse(CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.getLast()));
    campaign.act = 6;
    campaign.completed.remove("nature_module");
    assertFalse(CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.getLast()));
    campaign.completed.add("nature_module");
    campaign.archived = true;
    assertFalse(CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.getLast()));
    assertEquals(Set.copyOf(CampaignMilestones.PHASE_IDS), CampaignMilestones.RESERVED_IDS);
  }

  @Test
  void endingRequiresAllAuthorityAndCannotReplay() {
    var campaign = ready();
    assertFalse(CampaignMilestones.finish(campaign));
    campaign.arkPhase = 6;
    for (String required : Set.of("world_network", "end_arrival", "nature_module")) {
      campaign.completed.remove(required);
      assertFalse(CampaignMilestones.finish(campaign), required);
      campaign.completed.add(required);
    }
    campaign.act = 5;
    assertFalse(CampaignMilestones.finish(campaign));
    campaign.act = 6;
    campaign.archived = true;
    assertFalse(CampaignMilestones.finish(campaign));
    campaign.archived = false;
    assertTrue(CampaignMilestones.finish(campaign));
    assertFalse(CampaignMilestones.finish(campaign));
    assertEquals(6, campaign.arkPhase);
    assertTrue(campaign.arkDeposits.isEmpty());
    assertTrue(CampaignMilestones.isComplete(campaign, CampaignMilestones.LAST_HORIZON));
  }
}
