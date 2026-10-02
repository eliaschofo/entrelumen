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
    // 26 September 2026: the Atlas waits on the Signal Tower's pedestal; awakening it gives no item.
    assertTrue(projects.get("atlas_awakened").reward().isEmpty());
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
      // Ruins v2, roster of 26 September: the Star Chart went to the network; the settlement takes no piece.
      assertEquals(java.util.Map.of(item, 1), project.items());
      // 29 September: Renewable Horizons hands over Terra's plan of the hydroponic garden (terra-garden.md).
      assertEquals(id.equals("renewal_engine") ? "entrelumen:terra_garden_plan" : "", project.reward());
      var missing = definitions.deepCopy();
      missing.remove(id);
      assertTrue(assertThrows(IllegalArgumentException.class,
          () -> Projects.parse(missing, key -> true)).getMessage().contains(id));
    });
    var closure = projects.get("world_network");
    assertEquals(5, closure.act());
    assertEquals(costs.keySet(), closure.prerequisites());
    assertEquals(java.util.Map.of("minecraft:paper", 3, "minecraft:copper_ingot", 1,
        "entrelumen:star_chart", 1), closure.items());
    assertTrue(closure.reward().isEmpty());
  }

  /** 24 September 2026: act IV closes only with the Heart of Heliodor delivered to the Atlas. */
  @Test
  void actFourClosesWithTheHeartTheTeamRecovered() throws Exception {
    var projects = Projects.parse(defaults(), id -> true);
    var heart = projects.get(HeliodorHeartRules.PROJECT);
    assertEquals(4, heart.act());
    assertEquals(java.util.Map.of("entrelumen:heart_of_heliodor", 1, "entrelumen:sun_key", 1), heart.items());
    assertEquals(java.util.Set.of("exchange_route", HeliodorHeartRules.RECOVERED), heart.prerequisites());
    assertTrue(heart.reward().isEmpty(), "the Atlas keeps the Heart");
    assertTrue(projects.get("atlas_voices").prerequisites().contains(HeliodorHeartRules.PROJECT));
    assertTrue(Projects.forAct(4).isEmpty() || Projects.forAct(4).contains(HeliodorHeartRules.PROJECT));
    assertTrue(projects.values().stream().noneMatch(p -> p.act() > Campaigns.FINAL_ACT));
    assertTrue(projects.values().stream().noneMatch(p -> p.act() == Campaigns.FINAL_ACT),
        "act VI has no Atlas deliveries yet; Solsticio's missions come later");
  }

  @Test
  void eachActDeliversItsModuleWithThatActsMaterials() throws Exception {
    var projects = Projects.parse(defaults(), id -> true);
    // Ark v2 (25 September 2026): one module per act, costed with that act's materials, at most one
    // ENTRELUMEN component each (the playtest's recipe rules: little fan-in, nothing nested).
    var expected = java.util.Map.of(
        "habitation_module", java.util.Map.of("minecraft:white_bed", 1, "minecraft:campfire", 1,
            "minecraft:lantern", 2, "minecraft:bread", 4),
        "exploration_module", java.util.Map.of("entrelumen:ration_bundle", 2, "minecraft:compass", 1,
            "minecraft:map", 4, "minecraft:spyglass", 1),
        "nature_module", java.util.Map.of("entrelumen:propagation_core", 1, "minecraft:moss_block", 8,
            "minecraft:glistering_melon_slice", 4),
        "arcane_module", java.util.Map.of("entrelumen:spectral_lens", 1, "minecraft:amethyst_shard", 16,
            "minecraft:lapis_lazuli", 16),
        "logistics_module", java.util.Map.of("entrelumen:routing_matrix", 1, "minecraft:emerald", 16,
            "minecraft:ender_pearl", 8),
        "engineering_module", java.util.Map.of("entrelumen:ark_bus", 1, "minecraft:redstone_block", 8,
            "minecraft:copper_block", 8));
    var requires = java.util.Map.of("habitation_module", java.util.Set.of("travellers_table"),
        "exploration_module", java.util.Set.of("first_signal"), "nature_module", java.util.Set.of("lost_workshop"),
        "arcane_module", java.util.Set.of("exchange_route"), "logistics_module", java.util.Set.of("atlas_voices"),
        "engineering_module", java.util.Set.of("atlas_voices"));
    assertEquals(expected.keySet(), CampaignMilestones.MODULE_IDS);
    for (var module : ArkRules.Module.values()) {
      var project = projects.get(module.id);
      assertEquals(module.act, project.act(), module.id);
      assertEquals(expected.get(module.id), project.items(), module.id);
      assertEquals("entrelumen:" + module.id, project.reward());
      assertEquals(requires.get(module.id), project.prerequisites(), module.id);
      assertTrue(project.items().keySet().stream().filter(item -> item.startsWith("entrelumen:")).count() <= 1);
    }
    // The finales of acts I-IV wait for their act's module; act V's modules close before the activation.
    assertTrue(projects.get("first_signal").prerequisites().contains("habitation_module"));
    assertTrue(projects.get("lost_workshop").prerequisites().contains("exploration_module"));
    assertTrue(projects.get("exchange_route").prerequisites().contains("nature_module"));
    assertTrue(projects.get("atlas_voices").prerequisites().contains("arcane_module"));
  }

  /**
   * The calibration frame has no crafting recipe and only the Metallurgic Infuser copies it, while the
   * infuser's own recipe takes a frame. Since 1 October 2026 First Signal, the Act I closure, grants the
   * infuser itself and one frame (it gave two frames, and a team that spent both on act II deliveries
   * before building the infuser was stuck); no other project hands out frames.
   */
  @Test
  void firstSignalGrantsTheInfuserAndTheOnlyStoryFrame() throws Exception {
    var projects = Projects.parse(defaults(), id -> true);
    var signal = projects.get("first_signal");
    assertEquals(1, signal.act());
    assertEquals("entrelumen:signal_core", signal.reward());
    assertEquals(java.util.Map.of("mekanism:metallurgic_infuser", 1, "entrelumen:calibration_frame", 1),
        signal.extraRewards());
    // Terra's garden (29/9): Renewable Horizons gives the plan and one grow lamp, the only other extra reward.
    var renewal = projects.get("renewal_engine");
    assertEquals("entrelumen:terra_garden_plan", renewal.reward());
    assertEquals(java.util.Map.of("entrelumen:terra_grow_lamp", 1), renewal.extraRewards());
    projects.forEach((id, project) -> {
      if (!id.equals("first_signal")) {
        if (!id.equals("renewal_engine")) {
          assertTrue(project.extraRewards().isEmpty(), id);
        }
        assertNotEquals("entrelumen:calibration_frame", project.reward(), id);
        assertFalse(project.extraRewards().containsKey("entrelumen:calibration_frame"), id);
      }
    });
    assertThrows(UnsupportedOperationException.class,
        () -> signal.extraRewards().put("minecraft:stone", 1));
    assertEquals(java.util.Map.of(), new Projects.Project(1, java.util.Map.of(), java.util.Set.of(), "").extraRewards());
  }

  /** Without Mekanism (the companion's own test server) the infuser is left out, not the whole file. */
  @Test
  void extraRewardsFromAModThatIsNotLoadedAreLeftOut() throws Exception {
    var withoutMekanism = Projects.parse(defaults(), id -> !id.getNamespace().equals("mekanism"),
        mod -> !mod.equals("mekanism"));
    assertEquals(java.util.Map.of("entrelumen:calibration_frame", 1),
        withoutMekanism.get("first_signal").extraRewards());
    // A loaded mod's unknown item is still an error, and so is a missing vanilla or companion item.
    assertThrows(IllegalArgumentException.class,
        () -> Projects.parse(defaults(), id -> !id.getNamespace().equals("mekanism"), mod -> true));
    var definitions = defaults();
    definitions.getAsJsonObject("first_signal").add("extraRewards",
        JsonParser.parseString("{\"entrelumen:no_such_item\": 1}"));
    assertThrows(IllegalArgumentException.class,
        () -> Projects.parse(definitions, id -> !id.getPath().equals("no_such_item"), mod -> false));
  }

  @Test
  void extraRewardsRejectUnknownItemsBadCountsAndDuplicates() throws Exception {
    for (var bad : java.util.List.of("{}", "[]", "{\"minecraft:stone\": 0}", "{\"minecraft:stone\": 65}",
        "{\"minecraft:stone\": 1.5}", "{\"entrelumen:signal_core\": 1}", "{\"minecraft:missing\": 1}")) {
      var definitions = defaults();
      definitions.getAsJsonObject("first_signal").add("extraRewards", JsonParser.parseString(bad));
      assertThrows(IllegalArgumentException.class,
          () -> Projects.parse(definitions, id -> !id.toString().equals("minecraft:missing")), bad);
    }
    var valid = defaults();
    valid.getAsJsonObject("first_signal").add("extraRewards", JsonParser.parseString("{\"minecraft:stone\": 64}"));
    assertEquals(64, Projects.parse(valid, id -> true).get("first_signal").extraRewards().get("minecraft:stone"));
  }

  @Test
  void endingAndObservedIdsCannotBecomeDeliverableProjects() throws Exception {
    for (String id : java.util.List.of(CampaignMilestones.LAST_HORIZON, "end_arrival",
        HeliodorHeartRules.RECOVERED, Expeditions.SOLSTICIO_ARRIVAL)) {
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
    // An unknown reward item is refused (the Atlas is no reward since 26 September; the lens is).
    var json = defaults();
    assertThrows(
        IllegalArgumentException.class,
        () -> Projects.parse(json, id -> !id.toString().equals("entrelumen:raw_lens")));
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
