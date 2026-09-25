package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ArkFieldJournalsTest {
  private static final UUID PLAYER = UUID.randomUUID();
  private static final UUID TEAM = UUID.randomUUID();
  private static final EngineeringDiagnostics.PhysicalView READY =
      new EngineeringDiagnostics.PhysicalView(EngineeringDiagnostics.ControllerState.FOUND,
          new BlockPos(1, 64, -3), Set.of(), false);
  private static final EngineeringDiagnostics.PhysicalView ABSENT =
      new EngineeringDiagnostics.PhysicalView(EngineeringDiagnostics.ControllerState.ABSENT,
          null, Set.of(), false);

  private static Map<String, Projects.Project> projects() throws Exception {
    try (var reader = new InputStreamReader(ArkFieldJournalsTest.class.getResourceAsStream(
        "/data/entrelumen/campaign/projects.json"), StandardCharsets.UTF_8)) {
      return Projects.parse(JsonParser.parseReader(reader), id -> true);
    }
  }

  private static JsonObject lang(String code) throws Exception {
    try (var reader = new InputStreamReader(ArkFieldJournalsTest.class.getResourceAsStream(
        "/assets/entrelumen/lang/" + code + ".json"), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static JournalBookNetwork.Snapshot screen(Campaigns.Campaign campaign,
      ArkFieldJournals.Kind kind, EngineeringDiagnostics.PhysicalView physical,
      Map<String, Projects.Project> projects, Predicate<String> itemExists) {
    return ArkFieldJournals.screen(PLAYER, TEAM, ArkFieldJournals.view(campaign, kind), physical,
        id -> ArkFieldJournals.projectIcon(id, projects), itemExists, Optional.empty());
  }

  private static Campaigns.Campaign arkCampaign() {
    var campaign = new Campaigns.Campaign();
    campaign.act = CampaignMilestones.ARK_ACT;
    campaign.completed.addAll(CampaignMilestones.MODULE_IDS);
    return campaign;
  }

  private static String key(Component text) {
    return ((TranslatableContents) text.getContents()).getKey();
  }

  private static Object[] args(Component text) {
    return ((TranslatableContents) text.getContents()).getArgs();
  }

  @Test
  void journalDepositStateTracksOnlyItsOwnAuthoritativeBatch() {
    var campaign = new Campaigns.Campaign();
    campaign.act = CampaignMilestones.ARK_ACT;
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
  void statusIsOneLineWithTheBatchNumberAndOneWord() throws Exception {
    var projects = projects();
    for (var kind : ArkFieldJournals.Kind.values()) {
      var campaign = arkCampaign();
      campaign.arkPhase = kind.step() - 1;
      var waiting = screen(campaign, kind, READY, projects, id -> true);
      assertEquals(ArkFieldJournals.Status.WAITING, waiting.status());
      assertEquals("entrelumen.journal.status.waiting", key(waiting.statusLine()));
      assertArrayEquals(new Object[] {kind.step() + 1, 6}, args(waiting.statusLine()));
      assertEquals("entrelumen.journal.hint.waiting", key(waiting.hint().orElseThrow()));
      assertArrayEquals(new Object[] {kind.step()}, args(waiting.hint().orElseThrow()));

      campaign.arkPhase = kind.step();
      var delivering = screen(campaign, kind, READY, projects, id -> true);
      assertEquals(ArkFieldJournals.Status.DELIVERING, delivering.status());
      assertEquals("entrelumen.journal.status.delivering", key(delivering.statusLine()));
      assertEquals("entrelumen.journal.hint.deliver", key(delivering.hint().orElseThrow()));

      campaign.completed.remove("logistics_module");
      var blocked = screen(campaign, kind, READY, projects, id -> true);
      assertEquals(ArkFieldJournals.Status.BLOCKED, blocked.status());
      assertEquals("entrelumen.journal.batch_blocked", key(blocked.hint().orElseThrow()));

      campaign.arkPhase = kind.step() + 1;
      var delivered = screen(campaign, kind, READY, projects, id -> true);
      assertEquals(ArkFieldJournals.Status.DELIVERED, delivered.status());
      assertEquals("entrelumen.journal.status.delivered", key(delivered.statusLine()));
      assertTrue(delivered.hint().isEmpty());

      campaign.archived = true;
      var archived = screen(campaign, kind, READY, projects, id -> true);
      assertEquals(ArkFieldJournals.Status.ARCHIVED, archived.status());
      assertEquals("entrelumen.journal.status.archived", key(archived.statusLine()));
      assertEquals("entrelumen.engineering.archived", key(archived.hint().orElseThrow()));
    }
  }

  @Test
  void missingProjectsReadFirstWithTheirOwnItemAndACheck() throws Exception {
    var projects = projects();
    var campaign = arkCampaign();
    campaign.completed.add("sealed_memory");
    campaign.arkPhase = ArkFieldJournals.Kind.ARCANE.step();
    campaign.arkDeposits.put("entrelumen:containment_seal", 1);
    var snapshot = screen(campaign, ArkFieldJournals.Kind.ARCANE, READY, projects, id -> true);

    var rows = snapshot.entries(JournalBookNetwork.Section.PROJECTS);
    assertEquals(List.of("spectral_archive", "atlas_voices", "sealed_memory", "arcane_module"),
        rows.stream().map(row -> key(row.label().orElseThrow())
            .substring("entrelumen.project.".length())).toList());
    assertEquals(List.of(false, false, true, true), rows.stream().map(JournalBookNetwork.Entry::complete).toList());
    assertEquals(List.of("entrelumen:spectral_lens", "minecraft:paper",
            "entrelumen:containment_seal", "entrelumen:arcane_module"),
        rows.stream().map(row -> row.icon().toString()).toList());

    var batch = snapshot.entries(JournalBookNetwork.Section.BATCH);
    assertEquals(1, batch.size());
    assertEquals(ResourceLocation.parse("entrelumen:containment_seal"), batch.getFirst().icon());
    assertEquals(1, batch.getFirst().done());
    assertEquals(2, batch.getFirst().total());
    assertTrue(batch.getFirst().label().isEmpty(), "Materials are named by their item on the client");
    assertEquals("entrelumen.journal.arcane.partial", key(snapshot.flavor()));
    assertTrue(snapshot.entries(JournalBookNetwork.Section.JOURNEYS).isEmpty());
  }

  @Test
  void everyProjectIconIsAnItemTheProjectTakesOrUnlocks() throws Exception {
    var projects = projects();
    for (var kind : ArkFieldJournals.Kind.values()) {
      for (String id : kind.projects()) {
        var project = projects.get(id);
        String icon = ArkFieldJournals.projectIcon(id, projects);
        assertTrue(project.items().containsKey(icon), id + " icon " + icon);
      }
      assertEquals("entrelumen:" + kind.module(), ArkFieldJournals.projectIcon(kind.module(), projects));
    }
    assertEquals(ArkFieldJournals.FALLBACK_ICON, ArkFieldJournals.projectIcon("unknown", projects));
  }

  @Test
  void journeysAreDimensionIconsWithVanillaStandIns() throws Exception {
    var projects = projects();
    var campaign = arkCampaign();
    assertTrue(Expeditions.record(campaign, "aether:the_aether"));
    var snapshot = screen(campaign, ArkFieldJournals.Kind.EXPLORATION, READY, projects, id -> true);
    var journeys = snapshot.entries(JournalBookNetwork.Section.JOURNEYS);
    assertEquals(List.of("aether:aether_portal_frame",
            "twilightforest:twilight_portal_miniature_structure",
            "the_bumblezone:honeycomb_brood_block", "minecraft:end_stone"),
        journeys.stream().map(row -> row.icon().toString()).toList());
    assertEquals(List.of(true, false, false, false),
        journeys.stream().map(JournalBookNetwork.Entry::complete).toList());

    var vanillaOnly = screen(campaign, ArkFieldJournals.Kind.EXPLORATION, READY, projects,
        id -> id.startsWith("minecraft:"));
    assertEquals(List.of("minecraft:glowstone", "minecraft:oak_sapling",
            "minecraft:honeycomb_block", "minecraft:end_stone"),
        vanillaOnly.entries(JournalBookNetwork.Section.JOURNEYS).stream()
            .map(row -> row.icon().toString()).toList());
    assertTrue(vanillaOnly.entries().stream()
        .allMatch(row -> row.icon().getNamespace().equals("minecraft")),
        "Every icon falls back to a vanilla item when its mod is absent");
  }

  @Test
  void arkRowNamesTheOnePhysicalProblem() throws Exception {
    var projects = projects();
    var campaign = arkCampaign();
    java.util.function.Function<EngineeringDiagnostics.PhysicalView, JournalBookNetwork.Entry> ark =
        physical -> screen(campaign, ArkFieldJournals.Kind.NATURE, physical, projects, id -> true)
            .entries(JournalBookNetwork.Section.ARK).getFirst();

    var ready = ark.apply(READY);
    assertEquals("entrelumen.journal.ark.ready", key(ready.label().orElseThrow()));
    assertTrue(ready.complete());
    assertEquals("entrelumen.engineering.controller_at", key(ready.details().getFirst()));
    assertEquals(ResourceLocation.parse(ArkFieldJournals.CONTROLLER_ICON), ready.icon());

    var absent = ark.apply(ABSENT);
    assertEquals("entrelumen.journal.ark.absent", key(absent.label().orElseThrow()));
    assertFalse(absent.complete());
    assertEquals("entrelumen.journal.no_controller", key(absent.details().getFirst()));

    for (var state : List.of(EngineeringDiagnostics.ControllerState.UNLOADED,
        EngineeringDiagnostics.ControllerState.AMBIGUOUS)) {
      var row = ark.apply(new EngineeringDiagnostics.PhysicalView(state, null, Set.of(), false));
      assertFalse(row.complete());
      assertEquals("entrelumen.journal.ark." + state.name().toLowerCase(java.util.Locale.ROOT),
          key(row.label().orElseThrow()));
    }

    var missing = ark.apply(new EngineeringDiagnostics.PhysicalView(
        EngineeringDiagnostics.ControllerState.FOUND, BlockPos.ZERO,
        Set.of("nature_module", "logistics_module"), false));
    assertEquals("entrelumen.journal.ark.missing", key(missing.label().orElseThrow()));
    assertArrayEquals(new Object[] {2}, args(missing.label().orElseThrow()));
    assertEquals(List.of("entrelumen.engineering.controller_at", "entrelumen.ark.missing_module",
        "entrelumen.ark.missing_module"), missing.details().stream().map(ArkFieldJournalsTest::key).toList());

    var unloaded = ark.apply(new EngineeringDiagnostics.PhysicalView(
        EngineeringDiagnostics.ControllerState.FOUND, BlockPos.ZERO, Set.of(), true));
    assertEquals("entrelumen.journal.ark.unloaded", key(unloaded.label().orElseThrow()));

    campaign.completed.add(CampaignMilestones.LAST_HORIZON);
    var active = ark.apply(ABSENT);
    assertEquals("entrelumen.journal.ark.active", key(active.label().orElseThrow()));
    assertTrue(active.complete());
  }

  @Test
  void snapshotIsBoundedImmutableAndValidated() {
    var row = new JournalBookNetwork.Entry(JournalBookNetwork.Section.ARK,
        ResourceLocation.parse("minecraft:paper"), Optional.empty(), 0, 1, List.of());
    List<JournalBookNetwork.Entry> source = new ArrayList<>(List.of(row));
    var status = Component.translatable("entrelumen.journal.status.waiting", 2, 6);
    var snapshot = new JournalBookNetwork.Snapshot(PLAYER, TEAM, ArkFieldJournals.Kind.ARCANE,
        ArkFieldJournals.Status.WAITING, status, Component.empty(), Optional.empty(), source);
    source.clear();
    assertEquals(List.of(row), snapshot.entries());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Snapshot(PLAYER,
        TEAM, ArkFieldJournals.Kind.ARCANE, ArkFieldJournals.Status.WAITING, status,
        Component.empty(), Optional.empty(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Snapshot(PLAYER,
        TEAM, ArkFieldJournals.Kind.ARCANE, ArkFieldJournals.Status.WAITING, status,
        Component.empty(), Optional.empty(),
        Collections.nCopies(JournalBookNetwork.MAX_ENTRIES + 1, row)));
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Entry(
        JournalBookNetwork.Section.BATCH, ResourceLocation.parse("minecraft:paper"),
        Optional.empty(), 3, 2, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Entry(
        JournalBookNetwork.Section.BATCH, ResourceLocation.parse("minecraft:paper"),
        Optional.empty(), -1, 2, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new JournalBookNetwork.Entry(
        JournalBookNetwork.Section.SERVICE, ResourceLocation.parse("minecraft:paper"),
        Optional.empty(), 0, 0,
        Collections.nCopies(JournalBookNetwork.MAX_DETAILS + 1, Component.empty())));
  }

  @Test
  void everyScreenTextExistsInBothLanguagesAndFlavorStaysShort() throws Exception {
    var projects = projects();
    Set<String> keys = new TreeSet<>();
    var physicals = List.of(READY, ABSENT,
        new EngineeringDiagnostics.PhysicalView(EngineeringDiagnostics.ControllerState.UNLOADED,
            null, Set.of(), false),
        new EngineeringDiagnostics.PhysicalView(EngineeringDiagnostics.ControllerState.AMBIGUOUS,
            null, Set.of(), false),
        new EngineeringDiagnostics.PhysicalView(EngineeringDiagnostics.ControllerState.FOUND,
            BlockPos.ZERO, Set.of("logistics_module"), false),
        new EngineeringDiagnostics.PhysicalView(EngineeringDiagnostics.ControllerState.FOUND,
            BlockPos.ZERO, Set.of(), true));
    for (var kind : ArkFieldJournals.Kind.values()) {
      keys.add("block.entrelumen." + kind.module());
      for (int evidence = 0; evidence <= kind.projects().size() + kind.journeys().size(); evidence++) {
        for (int phase = kind.step() - 1; phase <= kind.step() + 1; phase++) {
          for (var physical : physicals) {
            var campaign = arkCampaign();
            campaign.arkPhase = phase;
            var ids = new ArrayList<>(kind.projects());
            ids.addAll(kind.journeys());
            campaign.completed.addAll(ids.subList(0, evidence));
            if (phase == kind.step() + 1) campaign.completed.add(CampaignMilestones.LAST_HORIZON);
            if (evidence == 0 && phase == kind.step()) campaign.archived = true;
            if (evidence == 1 && phase == kind.step()) campaign.completed.remove("nature_module");
            screen(campaign, kind, physical, projects, id -> true).texts()
                .forEach(text -> collect(text, keys));
          }
        }
      }
    }
    for (String section : List.of("batch", "projects", "journeys"))
      keys.add("entrelumen.journal.section." + section);
    for (String client : List.of("count", "amount", "material_left", "material_done", "recorded",
        "pending", "witnessed", "not_witnessed"))
      keys.add("entrelumen.journal." + client);
    for (String service : List.of("arcane", "nature.none", "nature.site", "exploration",
        "habitation.corrupt", "habitation.none", "habitation.active", "habitation.pending",
        "habitation.stale"))
      keys.add("entrelumen.journal.service." + service);
    keys.addAll(List.of("entrelumen.arcane.tooltip", "entrelumen.exploration.chart.journal",
        "entrelumen.nature.journal.none", "entrelumen.nature.journal.site",
        "entrelumen.nature.journal.instructions", "entrelumen.habitation.instructions"));

    for (String code : List.of("en_us", "es_es")) {
      var lang = lang(code);
      for (String key : keys) assertTrue(lang.has(key), code + " lacks " + key);
      for (var kind : ArkFieldJournals.Kind.values())
        for (var narrative : ArkFieldJournals.Narrative.values()) {
          String line = lang.get("entrelumen.journal." + kind.name().toLowerCase(java.util.Locale.ROOT)
              + "." + narrative.name().toLowerCase(java.util.Locale.ROOT)).getAsString();
          assertTrue(line.length() <= 40, code + " flavor must fit one line: " + line);
        }
    }
    assertTrue(keys.contains("entrelumen.journal.status.blocked"));
    assertTrue(keys.contains("entrelumen.journal.ark.active"));
    assertTrue(keys.contains("entrelumen.project.end_arrival"));
  }

  private static void collect(Component text, Set<String> keys) {
    if (text.getContents() instanceof TranslatableContents translatable) {
      keys.add(translatable.getKey());
      for (Object arg : translatable.getArgs())
        if (arg instanceof Component component) collect(component, keys);
    }
    text.getSiblings().forEach(sibling -> collect(sibling, keys));
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
    var campaign = arkCampaign();
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
      screen(campaign, kind, READY, Map.of(), id -> true);
      assertEquals(before.arkPhase, campaign.arkPhase);
      assertEquals(before.arkDeposits, campaign.arkDeposits);
      assertEquals(before.completed, campaign.completed);

      campaign.arkPhase = kind.step() + 1;
      campaign.arkDeposits.clear();
      var complete = ArkFieldJournals.view(campaign, kind);
      assertEquals(ArkFieldJournals.BatchState.COMPLETE, complete.batchState());
      assertTrue(complete.materials().stream().allMatch(material -> material.remaining() == 0
          && material.deposited() == material.required()), "A delivered batch reads as full");
    }
  }
}
