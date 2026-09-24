package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ArkCommissioningTest {
  private Campaigns.Campaign ready() {
    var campaign = new Campaigns.Campaign();
    campaign.act = CampaignMilestones.ARK_ACT;
    ArkCommissioning.STEPS.forEach(step -> campaign.completed.add(step.module()));
    return campaign;
  }

  @Test
  void partialDepositCapsConsumptionPreservesSurplusAndRejectsReplay() {
    var campaign = ready();
    var inventory = new HashMap<>(Map.of("entrelumen:calibration_frame", 2,
        "entrelumen:power_regulator", 20, "unrelated", 64));
    assertTrue(ArkCommissioning.deposit(campaign, 0, inventory,
        accepted -> accepted.forEach((item, count) -> inventory.merge(item, -count, Integer::sum))));
    assertEquals(Map.of("entrelumen:calibration_frame", 2), ArkCommissioning.missing(campaign));
    assertEquals(18, inventory.get("entrelumen:power_regulator"));
    assertEquals(64, inventory.get("unrelated"));
    assertEquals(0, campaign.arkPhase);
    var resumed = campaign.copy();
    assertTrue(ArkCommissioning.deposit(resumed, 0, Map.of("entrelumen:calibration_frame", 64),
        accepted -> assertEquals(Map.of("entrelumen:calibration_frame", 2), accepted)));
    assertEquals(1, resumed.arkPhase);
    assertTrue(resumed.arkDeposits.isEmpty());
    assertFalse(ArkCommissioning.deposit(resumed, 0, Map.of("entrelumen:containment_seal", 2),
        accepted -> fail("Stale action consumed")));
    assertEquals(0, campaign.arkPhase);
  }

  @Test
  void rejectsInvalidCampaignsAndZeroDepositsWithoutMutation() {
    var campaign = ready();
    var cost = ArkCommissioning.STEPS.getFirst().requirements();
    // Only the Ark act (V since 24 September 2026) takes batches: not act IV, not act VI.
    for (int act : new int[] {4, 6}) {
      campaign.act = act;
      assertFalse(ArkCommissioning.deposit(campaign, 0, cost, accepted -> fail()), "act " + act);
    }
    campaign.act = CampaignMilestones.ARK_ACT;
    campaign.archived = true;
    assertFalse(ArkCommissioning.deposit(campaign, 0, cost, accepted -> fail()));
    campaign.archived = false;
    campaign.completed.remove("nature_module");
    assertFalse(ArkCommissioning.deposit(campaign, 0, cost, accepted -> fail()));
    campaign.completed.add("nature_module");
    assertFalse(ArkCommissioning.deposit(campaign, 0, Map.of("unrelated", 64), accepted -> fail()));
    assertFalse(ArkCommissioning.deposit(campaign, 0, Map.of("entrelumen:calibration_frame", -1), accepted -> fail()));
    assertEquals(0, campaign.arkPhase);
    assertTrue(campaign.arkDeposits.isEmpty());
  }

  @Test
  void callbackFailureDoesNotRecordConsumption() {
    var campaign = ready();
    campaign.arkDeposits.put("entrelumen:calibration_frame", 1);
    var before = campaign.copy();
    assertThrows(IllegalStateException.class, () -> ArkCommissioning.deposit(campaign, 0,
        ArkCommissioning.STEPS.getFirst().requirements(), accepted -> {
          assertEquals(before.arkDeposits, campaign.arkDeposits);
          throw new IllegalStateException();
        }));
    assertEquals(before.arkDeposits, campaign.arkDeposits);
    assertEquals(0, campaign.arkPhase);
  }

  @Test
  void allSixStepsCompleteExactlyOnceAndDefinitionsAreImmutable() {
    var campaign = ready();
    assertEquals(List.of("calibrate", "stabilize", "stabilize", "provision", "provision", "chart"),
        ArkCommissioning.STEPS.stream().map(ArkCommissioning.Step::phase).toList());
    for (int step = 0; step < 6; step++) {
      var cost = ArkCommissioning.STEPS.get(step).requirements();
      assertTrue(ArkCommissioning.deposit(campaign, step, cost, accepted -> assertEquals(cost, accepted)));
      assertEquals(step + 1, campaign.arkPhase);
      assertTrue(campaign.arkDeposits.isEmpty());
    }
    assertFalse(ArkCommissioning.eligible(campaign));
    assertFalse(ArkCommissioning.deposit(campaign, 6, Map.of(), accepted -> fail()));
    assertTrue(ArkCommissioning.missing(campaign).isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> ArkCommissioning.STEPS.clear());
    var mutable = new HashMap<>(Map.of("item", 1));
    var definition = new ArkCommissioning.Step("module", "phase", mutable);
    mutable.clear();
    assertEquals(Map.of("item", 1), definition.requirements());
    assertThrows(UnsupportedOperationException.class, () -> definition.requirements().clear());
  }
}
