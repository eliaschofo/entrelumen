package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CampaignMilestonesTest {
  private static Campaigns.Campaign ready() {
    var campaign = new Campaigns.Campaign();
    campaign.act = CampaignMilestones.ARK_ACT;
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
    campaign.act = 4;
    assertFalse(CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.getLast()));
    // The phases stay complete once the activation moved the campaign on to act VI.
    campaign.act = Campaigns.FINAL_ACT;
    assertTrue(CampaignMilestones.isComplete(campaign, CampaignMilestones.PHASE_IDS.getLast()));
    campaign.act = CampaignMilestones.ARK_ACT;
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
    for (int act : new int[] {4, 6}) {
      campaign.act = act;
      assertFalse(CampaignMilestones.finish(campaign), "act " + act);
    }
    campaign.act = CampaignMilestones.ARK_ACT;
    campaign.archived = true;
    assertFalse(CampaignMilestones.finish(campaign));
    campaign.archived = false;
    assertTrue(CampaignMilestones.finish(campaign));
    // The activation opens act VI, Solsticio, in the same mutation.
    assertEquals(Campaigns.FINAL_ACT, campaign.act);
    assertFalse(CampaignMilestones.finish(campaign));
    assertEquals(6, campaign.arkPhase);
    assertTrue(campaign.arkDeposits.isEmpty());
    assertTrue(CampaignMilestones.isComplete(campaign, CampaignMilestones.LAST_HORIZON));
  }

  @Test
  void leavingTheArkActNeedsTheActivationAndNothingAdvancesPastSolsticio() {
    var projects = java.util.Set.of("world_network", "engineering_module");
    var required = Campaigns.advanceRequirements(CampaignMilestones.ARK_ACT, projects);
    assertTrue(required.contains(CampaignMilestones.LAST_HORIZON));
    assertEquals(projects, Campaigns.advanceRequirements(4, projects));
    assertTrue(Campaigns.advanceRequirements(CampaignMilestones.ARK_ACT, java.util.Set.of()).isEmpty());
    var campaign = new Campaigns.Campaign();
    campaign.act = CampaignMilestones.ARK_ACT;
    campaign.completed.addAll(projects);
    assertFalse(Campaigns.advance(campaign, required), "an Advance cannot skip the activation");
    campaign.completed.add(CampaignMilestones.LAST_HORIZON);
    assertTrue(Campaigns.advance(campaign, required));
    assertEquals(Campaigns.FINAL_ACT, campaign.act);
    assertFalse(Campaigns.advance(campaign, java.util.Set.of("world_network")));
  }

  @Test
  void observationsAreTheArrivalsTheSolsticioCrossingAndTheHeart() {
    assertTrue(CampaignMilestones.OBSERVATIONS.containsAll(Expeditions.IDS));
    assertTrue(CampaignMilestones.OBSERVATIONS.contains(Expeditions.SOLSTICIO_ARRIVAL));
    assertTrue(CampaignMilestones.OBSERVATIONS.contains(HeliodorHeartRules.RECOVERED));
    assertEquals(Expeditions.IDS.size() + 2, CampaignMilestones.OBSERVATIONS.size());
  }
}
