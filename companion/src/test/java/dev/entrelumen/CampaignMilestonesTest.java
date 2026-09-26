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

  private static final ArkRules.Status WHOLE =
      new ArkRules.Status(true, true, true, Set.copyOf(ArkRules.moduleIds()), 0);

  @Test
  void moduleMilestonesAreTheirDeliveredProjects() {
    var campaign = new Campaigns.Campaign();
    for (String module : CampaignMilestones.MODULE_IDS) {
      assertFalse(CampaignMilestones.isComplete(campaign, module), module);
      campaign.completed.add(module);
      assertTrue(CampaignMilestones.isComplete(campaign, module), module);
    }
    assertEquals(Set.copyOf(ArkRules.moduleIds()), CampaignMilestones.MODULE_IDS);
  }

  @Test
  void endingRequiresTheWholeArkTheLastProjectAndTheEndAndCannotReplay() {
    var campaign = ready();
    assertFalse(CampaignMilestones.finish(campaign, ArkRules.Status.NONE), "no Ark");
    for (var partial : java.util.List.of(
        new ArkRules.Status(true, false, true, Set.copyOf(ArkRules.moduleIds()), 0),
        new ArkRules.Status(true, true, false, Set.copyOf(ArkRules.moduleIds()), 0),
        new ArkRules.Status(true, true, true, Set.of("engineering_module", "arcane_module"), 4)))
      assertFalse(CampaignMilestones.finish(campaign, partial), partial.toString());
    for (String required : Set.of("world_network", "end_arrival")) {
      campaign.completed.remove(required);
      assertFalse(CampaignMilestones.finish(campaign, WHOLE), required);
      campaign.completed.add(required);
    }
    for (int act : new int[] {4, 6}) {
      campaign.act = act;
      assertFalse(CampaignMilestones.finish(campaign, WHOLE), "act " + act);
    }
    campaign.act = CampaignMilestones.ARK_ACT;
    campaign.archived = true;
    assertFalse(CampaignMilestones.finish(campaign, WHOLE));
    campaign.archived = false;
    assertTrue(CampaignMilestones.finish(campaign, WHOLE));
    // The activation opens act VI, Solsticio, in the same mutation.
    assertEquals(Campaigns.FINAL_ACT, campaign.act);
    assertFalse(CampaignMilestones.finish(campaign, WHOLE));
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
