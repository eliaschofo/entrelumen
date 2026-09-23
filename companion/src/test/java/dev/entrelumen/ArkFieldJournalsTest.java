package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ArkFieldJournalsTest {
  @Test
  void journalDepositStateTracksOnlyItsOwnAuthoritativeBatch() {
    var campaign = new Campaigns.Campaign();
    campaign.act = 6;
    for (var kind : ArkFieldJournals.Kind.values()) {
      campaign.completed.clear();
      assertEquals(kind.module(), ArkCommissioning.STEPS.get(kind.step()).module());
      campaign.arkPhase = kind.step() - 1;
      assertEquals(ArkFieldJournals.DepositState.FUTURE,
          ArkFieldJournals.depositState(campaign, kind));
      campaign.arkPhase = kind.step();
      assertEquals(ArkFieldJournals.DepositState.BLOCKED,
          ArkFieldJournals.depositState(campaign, kind));
      campaign.completed.addAll(CampaignMilestones.MODULE_IDS);
      assertEquals(ArkFieldJournals.DepositState.CURRENT,
          ArkFieldJournals.depositState(campaign, kind));
      campaign.arkPhase++;
      assertEquals(ArkFieldJournals.DepositState.COMPLETE,
          ArkFieldJournals.depositState(campaign, kind));
    }
    assertTrue(campaign.arkDeposits.isEmpty());
  }

  @Test
  void bookPagesKeepLongEvidenceAndEveryLineWithinVanillaRowLimit() {
    List<Component> evidence = List.of(Component.literal("Arcane testimony"),
        Component.literal("spectral_archive recorded"),
        Component.literal("sealed_memory recorded"),
        Component.literal("a very long last testimony that spans more than one page"));
    java.util.function.ToIntFunction<Component> rows = text ->
        java.util.Arrays.stream(text.getString().split("\\n", -1))
            .mapToInt(part -> Math.max(1, (part.length() + 7) / 8)).sum();
    var pages = JournalBookPagination.pages(evidence, 3, rows, line -> {
      String value = line.getString();
      var pieces = new java.util.ArrayList<Component>();
      for (int at = 0; at < value.length(); at += 8)
        pieces.add(Component.literal(value.substring(at, Math.min(at + 8, value.length()))));
      return pieces;
    });
    assertTrue(pages.size() > 2);
    assertTrue(pages.stream().allMatch(page -> rows.applyAsInt(page) <= 3));
    assertEquals(evidence.stream().map(Component::getString).collect(java.util.stream.Collectors.joining()),
        pages.stream().map(Component::getString).collect(java.util.stream.Collectors.joining())
            .replace("\n", ""));
  }

  @Test
  void bookSnapshotIsBoundedAndKeepsTranslationComponents() {
    var player = UUID.randomUUID();
    var campaign = UUID.randomUUID();
    var title = Component.translatable("entrelumen.journal.arcane.title");
    List<Component> source = new java.util.ArrayList<>(List.of(title));
    var snapshot = new JournalBookNetwork.Snapshot(player, campaign,
        ArkFieldJournals.Kind.ARCANE, source);
    source.clear();
    assertEquals(List.of(title), snapshot.lines());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.lines().clear());
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Snapshot(
        player, campaign, ArkFieldJournals.Kind.ARCANE, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Snapshot(
        player, campaign, ArkFieldJournals.Kind.ARCANE,
        java.util.Collections.nCopies(33, title)));
  }

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
