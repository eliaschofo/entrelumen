package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArkFieldJournalsTest {
  @Test
  void eachDisciplineNarratesOnlyRecordedTeamEvidence() {
    var team = new Campaigns.Campaign();
    var outsider = new Campaigns.Campaign();
    assertEquals(ArkFieldJournals.Narrative.EMPTY,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.ARCANE).narrative());
    team.completed.add("spectral_archive");
    assertEquals(ArkFieldJournals.Narrative.PARTIAL,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.ARCANE).narrative());
    team.completed.addAll(Set.of("sealed_memory", "atlas_voices"));
    assertEquals(ArkFieldJournals.Narrative.RECORDED,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.ARCANE).narrative());
    assertEquals(ArkFieldJournals.Narrative.EMPTY,
        ArkFieldJournals.view(outsider, ArkFieldJournals.Kind.ARCANE).narrative());

    team.completed.add("nursery_protocol");
    assertEquals(ArkFieldJournals.Narrative.PARTIAL,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.NATURE).narrative());
    team.completed.addAll(Set.of("pollinator_treaty", "renewal_engine"));
    assertEquals(ArkFieldJournals.Narrative.RECORDED,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.NATURE).narrative());

    team.completed.add("travellers_table");
    assertEquals(ArkFieldJournals.Narrative.PARTIAL,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.HABITATION).narrative());
    team.completed.addAll(Set.of("travelling_pantry", "settlement_supply"));
    assertEquals(ArkFieldJournals.Narrative.RECORDED,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.HABITATION).narrative());
  }

  @Test
  void explorationSeparatesSurveyFromWitnessedJourneysAndModuleDelivery() {
    var team = new Campaigns.Campaign();
    team.completed.add("horizon_survey");
    team.completed.add("exploration_module");
    var surveyOnly = ArkFieldJournals.view(team, ArkFieldJournals.Kind.EXPLORATION);
    assertTrue(surveyOnly.moduleProjectRecorded());
    assertEquals(ArkFieldJournals.Narrative.PARTIAL, surveyOnly.narrative());
    assertTrue(surveyOnly.projects().getFirst().recorded());
    assertTrue(surveyOnly.journeys().stream().noneMatch(ArkFieldJournals.Evidence::recorded));
    assertEquals(4, surveyOnly.journeys().size());

    assertTrue(Expeditions.record(team, "aether:the_aether"));
    var oneJourney = ArkFieldJournals.view(team, ArkFieldJournals.Kind.EXPLORATION);
    assertEquals(1, oneJourney.journeys().stream().filter(ArkFieldJournals.Evidence::recorded).count());
    assertFalse(oneJourney.journeys().stream().filter(e -> e.id().equals("end_arrival"))
        .findFirst().orElseThrow().recorded());
    for (String dimension : Set.of("twilightforest:twilight_forest",
        "the_bumblezone:the_bumblezone", "minecraft:the_end"))
      assertTrue(Expeditions.record(team, dimension));
    assertEquals(ArkFieldJournals.Narrative.RECORDED,
        ArkFieldJournals.view(team, ArkFieldJournals.Kind.EXPLORATION).narrative());
  }

  @Test
  void ownBatchMovesFromPlannedToStoredToCompleteWithoutWritingProgress() {
    var campaign = new Campaigns.Campaign();
    campaign.act = 6;
    campaign.completed.addAll(CampaignMilestones.MODULE_IDS);
    var costs = Map.of(
        ArkFieldJournals.Kind.ARCANE, Map.entry("entrelumen:containment_seal", 2),
        ArkFieldJournals.Kind.NATURE, Map.entry("entrelumen:ecosystem_capsule", 2),
        ArkFieldJournals.Kind.EXPLORATION, Map.entry("entrelumen:horizon_chart", 1),
        ArkFieldJournals.Kind.HABITATION, Map.entry("entrelumen:ration_bundle", 8));
    for (var kind : ArkFieldJournals.Kind.values()) {
      campaign.arkPhase = 0;
      campaign.arkDeposits.clear();
      var planned = ArkFieldJournals.view(campaign, kind);
      assertEquals(ArkFieldJournals.BatchState.UPCOMING, planned.batchState());
      assertEquals(0, planned.materials().getFirst().deposited());
      assertEquals(costs.get(kind).getKey(), planned.materials().getFirst().item());
      assertEquals(costs.get(kind).getValue(), planned.materials().getFirst().required());

      campaign.arkPhase = kind.step();
      campaign.arkDeposits.put(costs.get(kind).getKey(), 1);
      var before = campaign.copy();
      var current = ArkFieldJournals.view(campaign, kind);
      assertEquals(ArkFieldJournals.BatchState.CURRENT, current.batchState());
      assertEquals(1, current.materials().getFirst().deposited());
      assertEquals(costs.get(kind).getValue() - 1, current.materials().getFirst().remaining());
      assertTrue(current.batchEligible());
      ArkFieldJournals.view(campaign, kind);
      assertEquals(before.arkPhase, campaign.arkPhase);
      assertEquals(before.arkDeposits, campaign.arkDeposits);
      assertEquals(before.completed, campaign.completed);

      campaign.arkPhase = kind.step() + 1;
      campaign.arkDeposits.clear();
      var complete = ArkFieldJournals.view(campaign, kind);
      assertEquals(ArkFieldJournals.BatchState.COMPLETE, complete.batchState());
      assertTrue(complete.materials().isEmpty());
    }
  }
}
