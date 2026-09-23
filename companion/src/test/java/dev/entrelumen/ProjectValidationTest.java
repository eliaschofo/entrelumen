package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProjectValidationTest {
  private JsonObject defaults() throws IOException {
    try (var reader =
        new InputStreamReader(
            getClass().getResourceAsStream("/data/entrelumen/campaign/projects.json"),
            StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  @Test
  void validDefinitionsRemainImmutable() throws Exception {
    var projects = Projects.parse(defaults(), id -> true);
    assertEquals("entrelumen:atlas", projects.get("atlas_awakened").reward());
    assertThrows(UnsupportedOperationException.class, () -> projects.clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> projects.get("atlas_awakened").items().put("minecraft:stone", 9));
  }

  @Test
  void actFiveDeliveriesRemainStableAndRequireThePreviousClosure() throws Exception {
    var definitions = defaults();
    var projects = Projects.parse(definitions, id -> true);
    var costs = java.util.Map.of(
        "resilient_backbone", "entrelumen:ark_bus",
        "renewal_engine", "entrelumen:renewal_engine",
        "settlement_supply", "entrelumen:habitation_contract");
    costs.forEach((id, item) -> {
      var project = projects.get(id);
      assertEquals(5, project.act());
      assertEquals(java.util.Set.of("atlas_voices"), project.prerequisites());
      assertEquals(java.util.Map.of(item, 1), project.items());
      assertTrue(project.reward().isEmpty());
      var missing = definitions.deepCopy();
      missing.remove(id);
      assertTrue(assertThrows(IllegalArgumentException.class,
          () -> Projects.parse(missing, key -> true)).getMessage().contains(id));
    });
    var closure = projects.get("world_network");
    assertEquals(5, closure.act());
    assertEquals(costs.keySet(), closure.prerequisites());
    assertEquals(java.util.Map.of("minecraft:paper", 3, "minecraft:copper_ingot", 1),
        closure.items());
    assertTrue(closure.reward().isEmpty());
  }

  @Test
  void actSixUsesExactCrossModCostsAndPreservesOneModuleRewards() throws Exception {
    var projects = Projects.parse(defaults(), id -> true);
    var expected = java.util.Map.of(
        "engineering_module", java.util.Map.of("entrelumen:calibration_frame", 2,
            "entrelumen:energy_coupler", 2, "entrelumen:ark_bus", 1,
            "mekanism:alloy_atomic", 2),
        "arcane_module", java.util.Map.of("entrelumen:spectral_lens", 2,
            "entrelumen:containment_seal", 2, "occultism:iesnium_ingot", 2),
        "nature_module", java.util.Map.of("entrelumen:renewal_engine", 1,
            "entrelumen:ecosystem_capsule", 2, "entrelumen:living_matrix", 2),
        "exploration_module", java.util.Map.of("entrelumen:horizon_chart", 1,
            "entrelumen:spectral_lens", 1, "twilightforest:steeleaf_ingot", 2,
            "aether:zanite_gemstone", 2),
        "logistics_module", java.util.Map.of("entrelumen:routing_matrix", 2,
            "entrelumen:handling_core", 2, "entrelumen:ark_bus", 1),
        "habitation_module", java.util.Map.of("entrelumen:habitation_contract", 1,
            "entrelumen:ration_bundle", 2, "entrelumen:living_matrix", 2));
    assertEquals(expected.keySet(), CampaignMilestones.MODULE_IDS);
    expected.forEach((id, items) -> {
      var project = projects.get(id);
      assertEquals(6, project.act());
      assertEquals(items, project.items());
      assertEquals("entrelumen:" + id, project.reward());
      assertEquals(id.equals("exploration_module")
          ? java.util.Set.of("world_network", "end_arrival")
          : java.util.Set.of("world_network"), project.prerequisites());
    });
  }

  @Test
  void phaseAndEndingIdsCannotBecomeDeliverableProjects() throws Exception {
    for (String id : java.util.stream.Stream.concat(CampaignMilestones.PHASE_IDS.stream(),
        java.util.stream.Stream.of(CampaignMilestones.LAST_HORIZON, "end_arrival")).toList()) {
      var collision = defaults();
      collision.add(id, collision.getAsJsonObject("atlas_awakened").deepCopy());
      assertThrows(IllegalArgumentException.class, () -> Projects.parse(collision, key -> true), id);
    }
  }

  @Test
  void projectAndPrerequisiteIdsRespectAtlasWireLimit() throws Exception {
    String boundary = "a".repeat(128);
    var valid = defaults();
    valid.add(boundary, valid.getAsJsonObject("atlas_awakened").deepCopy());
    var prerequisites = new JsonArray();
    prerequisites.add(boundary);
    valid.getAsJsonObject("travellers_table").add("requires", prerequisites);
    assertTrue(Projects.parse(valid, id -> true).containsKey(boundary));

    var oversizedProject = defaults();
    oversizedProject.add(boundary + "a", valid.getAsJsonObject(boundary).deepCopy());
    assertTrue(
        assertThrows(
                IllegalArgumentException.class, () -> Projects.parse(oversizedProject, id -> true))
            .getMessage()
            .contains("at most 128 characters"));

    var oversizedPrerequisite = defaults();
    var tooLong = new JsonArray();
    tooLong.add(boundary + "a");
    oversizedPrerequisite.getAsJsonObject("travellers_table").add("requires", tooLong);
    assertTrue(
        assertThrows(
                IllegalArgumentException.class,
                () -> Projects.parse(oversizedPrerequisite, id -> true))
            .getMessage()
            .contains("at most 128 characters"));
  }

  @Test
  void onlyReservedServerObservationsCanBeExternalPrerequisites() throws Exception {
    var json = defaults();
    var requirements = new JsonArray();
    requirements.add("exchange_route");
    requirements.add("aether_arrival");
    requirements.add("twilight_arrival");
    json.getAsJsonObject("horizon_survey").add("requires", requirements);
    assertEquals(java.util.Set.of("exchange_route", "aether_arrival", "twilight_arrival"),
        Projects.parse(json, id -> true).get("horizon_survey").prerequisites());

    requirements.add("invented_arrival");
    assertThrows(IllegalArgumentException.class, () -> Projects.parse(json, id -> true));
    var collision = defaults();
    collision.add("aether_arrival", collision.getAsJsonObject("atlas_awakened").deepCopy());
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> Projects.parse(collision, id -> true)).getMessage().contains("observation IDs"));
  }

  @Test
  void malformedCountsAndUnknownItemsAreRejected() throws Exception {
    for (JsonElement value :
        new JsonElement[] {
          new JsonPrimitive(0),
          new JsonPrimitive(-1),
          new JsonPrimitive(1.5),
          new JsonPrimitive("1")
        }) {
      var json = defaults();
      json.getAsJsonObject("atlas_awakened").getAsJsonObject("items").add("minecraft:book", value);
      assertThrows(IllegalArgumentException.class, () -> Projects.parse(json, id -> true));
    }
    var json = defaults();
    assertThrows(
        IllegalArgumentException.class,
        () -> Projects.parse(json, id -> !id.toString().equals("entrelumen:atlas")));
  }

  @Test
  void cyclesAndMissingPrerequisitesAreRejected() throws Exception {
    var cycle = defaults();
    var array = new JsonArray();
    array.add("first_signal");
    cycle.getAsJsonObject("atlas_awakened").add("requires", array);
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> Projects.parse(cycle, id -> true))
            .getMessage()
            .contains("cycle"));
    var unknown = defaults();
    var missing = new JsonArray();
    missing.add("missing_project");
    unknown.getAsJsonObject("atlas_awakened").add("requires", missing);
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> Projects.parse(unknown, id -> true))
            .getMessage()
            .contains("unknown prerequisite"));
  }
}
