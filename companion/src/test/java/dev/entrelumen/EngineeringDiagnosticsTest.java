package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class EngineeringDiagnosticsTest {
  @Test
  void partialBatchReadsDepositsAndMissingGatesWithoutChangingLedger() {
    var campaign = new Campaigns.Campaign();
    campaign.act = 6;
    campaign.completed.add("engineering_module");
    campaign.arkDeposits.put("entrelumen:calibration_frame", 2);
    var before = campaign.copy();

    var view = EngineeringDiagnostics.campaignView(campaign, Map.of(
        "world_network", new Projects.Project(5, Map.of(),
            Set.of("resilient_backbone", "renewal_engine", "settlement_supply"), ""),
        "engineering_module", new Projects.Project(6, Map.of(), Set.of("world_network"), "")));
    assertEquals(0, view.step());
    assertEquals(Set.of("arcane_module", "nature_module", "exploration_module",
        "logistics_module", "habitation_module"), Set.copyOf(view.missingProjects()));
    assertTrue(view.missingWorldNetwork());
    assertTrue(view.missingEndJourney());
    assertEquals(Set.of("resilient_backbone", "renewal_engine", "settlement_supply"),
        Set.copyOf(view.missingPrerequisites()));
    assertEquals(Map.of("entrelumen:calibration_frame", 2,
        "entrelumen:power_regulator", 2), view.materials().stream().collect(
        java.util.stream.Collectors.toMap(EngineeringDiagnostics.Material::item,
            EngineeringDiagnostics.Material::remaining)));
    assertEquals(before.arkDeposits, campaign.arkDeposits);
    assertEquals(before.completed, campaign.completed);
    assertEquals(before.arkPhase, campaign.arkPhase);
  }

  @Test
  void commissionedAndFinalEndingAreDistinct() {
    var campaign = new Campaigns.Campaign();
    campaign.act = 6;
    campaign.arkPhase = ArkCommissioning.STEPS.size();
    campaign.completed.addAll(CampaignMilestones.MODULE_IDS);
    campaign.completed.addAll(Set.of("world_network", "end_arrival"));
    var commissioned = EngineeringDiagnostics.campaignView(campaign);
    assertTrue(commissioned.commissioned());
    assertFalse(commissioned.ending());
    assertTrue(commissioned.materials().isEmpty());
    campaign.completed.add(CampaignMilestones.LAST_HORIZON);
    var ended = EngineeringDiagnostics.campaignView(campaign);
    assertTrue(ended.commissioned() && ended.ending());
  }

  @Test
  void physicalScanSeparatesAbsentUnloadedAmbiguousAndKnownMissing() {
    var module = new BlockPos(0, 70, 0);
    var controller = module.offset(3, 1, 0);
    var blocks = new HashMap<BlockPos, String>();
    blocks.put(module, "engineering_module");
    blocks.put(controller, "controller");
    var loaded = EngineeringDiagnostics.scan(module, pos -> true,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.FOUND, loaded.state());
    assertEquals(controller, loaded.controller());
    assertTrue(loaded.missingModules().contains("nature_module"));
    assertFalse(loaded.missingModules().contains("engineering_module"));
    assertFalse(loaded.moduleAreaUnloaded());

    var incompleteArea = EngineeringDiagnostics.scan(module, pos -> pos.getX() != 4,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.FOUND, incompleteArea.state());
    assertTrue(incompleteArea.moduleAreaUnloaded());

    var unknownController = EngineeringDiagnostics.scan(module, pos -> pos.getX() != -3,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.UNLOADED, unknownController.state());
    assertNull(unknownController.controller());

    blocks.put(module.offset(-1, 0, 0), "controller");
    var ambiguous = EngineeringDiagnostics.scan(module, pos -> true,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.AMBIGUOUS, ambiguous.state());
    blocks.clear();
    var absent = EngineeringDiagnostics.scan(module, pos -> true,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.ABSENT, absent.state());
  }
}
