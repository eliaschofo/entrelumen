package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CompassTargetsTest {
  private static JsonElement shipped() throws IOException {
    try (var reader = new InputStreamReader(
        CompassTargetsTest.class.getResourceAsStream("/data/entrelumen/compass/targets.json"),
        StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader);
    }
  }

  private static JsonObject document(String objectives) {
    return JsonParser.parseString("{\"objectives\": [" + objectives + "]}").getAsJsonObject();
  }

  private static final String RUIN = """
      {"id": "ruin", "act": 1, "kind": "structure",
       "target": {"type": "anchor", "anchor": "entrelumen:heliodor_ruin_start"},
       "advance_when": {"type": "milestone", "milestone": "atlas_awakened"}, "lore": true}""";

  @Test
  void theBuildShipsTheAuthoredDraftUnchanged() throws Exception {
    var authored = JsonParser.parseString(
        Files.readString(Path.of("..", "content", "compass_targets.json"), StandardCharsets.UTF_8));
    assertEquals(authored, shipped(), "processResources must copy content/compass_targets.json");
    assertTrue(authored.getAsJsonObject().get("draft").getAsBoolean(), "The list stays a draft");
  }

  @Test
  void theDraftParsesWithEveryModAndDropsObjectivesOfMissingMods() throws Exception {
    var all = CompassTargets.parse(shipped(), mod -> true, item -> true);
    var vanilla = CompassTargets.parse(shipped(), mod -> false, item -> true);
    assertEquals("heliodor_ruin", all.getFirst().id());
    assertEquals(CompassTargets.TargetType.ANCHOR, all.getFirst().target().type());
    assertTrue(all.size() > vanilla.size(), "Mod-specific objectives must be optional");
    assertTrue(vanilla.stream().noneMatch(o -> o.id().equals("gold_dungeon")));
    // Since 24 September 2026 the Sun Spirit closes act IV: the gold dungeon is an act IV boss and
    // the compass moves on once the team recovered the Heart of Heliodor.
    assertTrue(all.stream().anyMatch(o -> o.id().equals("gold_dungeon") && o.act() == 4
        && o.kind() == CompassTargets.Kind.BOSS
        && o.advanceWhen().type() == CompassTargets.ConditionType.MILESTONE
        && o.advanceWhen().value().equals(HeliodorHeartRules.RECOVERED)
        && o.target().dimension().equals("aether:the_aether")));
    assertTrue(all.stream().anyMatch(o -> o.id().equals("silver_dungeon") && o.act() == 4));
    for (String id : java.util.List.of("ocean_monument", "stronghold", "the_end"))
      assertTrue(all.stream().anyMatch(o -> o.id().equals(id) && o.act() == 5), id);
    // Act VI: the crossing, then (mission 2's reward) Aurelia's hall, the three halls and the portal.
    var ids = all.stream().map(CompassTargets.Objective::id).toList();
    var crossing = all.get(ids.indexOf("solsticio"));
    assertEquals(6, crossing.act());
    assertEquals(Expeditions.SOLSTICIO_ARRIVAL, crossing.advanceWhen().value());
    assertEquals(java.util.List.of("solsticio", "solsticio_town_hall", "solsticio_gardens", "solsticio_workshop",
        "solsticio_chapel", "solsticio_portal"), ids.subList(ids.indexOf("solsticio"), ids.size()));
    assertEquals(java.util.List.of(SolsticioStoryRules.MAYOR, SolsticioStoryRules.HARVEST, SolsticioStoryRules.TERRAPRISM,
            SolsticioStoryRules.BLESSING, SolsticioStoryRules.PORTAL),
        all.subList(ids.indexOf("solsticio") + 1, all.size()).stream().map(o -> o.advanceWhen().value()).toList());
    for (var hall : all.subList(ids.indexOf("solsticio") + 1, all.size()))
      assertTrue(hall.act() == 6 && hall.target().type() == CompassTargets.TargetType.POSITION
          && hall.target().dimension().equals("entrelumen:solsticio"), hall.id());
    int act = 1;
    for (var objective : all) {
      assertTrue(objective.act() >= act, "Objectives are listed in act order");
      act = objective.act();
      assertTrue(objective.target().type() == CompassTargets.TargetType.DIMENSION
          || objective.target().type() == CompassTargets.TargetType.POSITION
          || objective.target().radius() <= CompassTargets.MAX_RADIUS);
    }
    assertThrows(UnsupportedOperationException.class, () -> all.add(all.getFirst()));
  }

  @Test
  void targetsAndConditionsParseEveryType() {
    var list = CompassTargets.parse(document(RUIN + ","
        + """
        {"id": "villages", "act": 1, "kind": "structure",
         "target": {"type": "structure", "structure": "#minecraft:village", "radius": 600},
         "advance_when": {"type": "item", "item": "#minecraft:logs", "count": 3}},
        {"id": "jungle", "act": 2, "kind": "artifact",
         "target": {"type": "biome", "biome": "minecraft:jungle"},
         "advance_when": {"type": "item", "item": "minecraft:cocoa_beans"}},
        {"id": "fixed", "act": 3, "kind": "boss",
         "target": {"type": "position", "dimension": "minecraft:the_nether", "pos": [10, 64, -20]},
         "advance_when": {"type": "advancement", "advancement": "minecraft:nether/root"}},
        {"id": "sky", "act": 4, "kind": "structure",
         "target": {"type": "dimension", "dimension": "aether:the_aether"},
         "advance_when": {"type": "milestone", "milestone": "aether_arrival"}}"""),
        mod -> true, item -> true);
    assertEquals(5, list.size());
    var villages = list.get(1);
    assertTrue(villages.target().tag());
    assertEquals(600, villages.target().radius());
    assertEquals(CompassTargets.OVERWORLD, villages.target().dimension());
    assertEquals(new CompassTargets.Condition(CompassTargets.ConditionType.ITEM, "#minecraft:logs", 3),
        villages.advanceWhen());
    assertEquals(CompassTargets.DEFAULT_RADIUS, list.get(2).target().radius());
    assertEquals(new net.minecraft.core.BlockPos(10, 64, -20), list.get(3).target().pos());
    assertEquals("dimension|aether:the_aether|", list.get(4).target().key());
    assertTrue(list.getFirst().lore());
    assertFalse(villages.lore());
  }

  @Test
  void invalidDocumentsAreRejectedWithTheirPath() {
    Map<String, String> invalid = new LinkedHashMap<>();
    invalid.put("unknown field", RUIN.replace("\"lore\": true", "\"color\": 3"));
    invalid.put("duplicate", RUIN + "," + RUIN);
    invalid.put("act order", RUIN.replace("\"act\": 1", "\"act\": 2") + ","
        + RUIN.replace("\"ruin\"", "\"later\""));
    invalid.put("act range", RUIN.replace("\"act\": 1", "\"act\": 7"));
    invalid.put("kind", RUIN.replace("\"structure\"", "\"villager\""));
    invalid.put("radius", RUIN.replace("\"entrelumen:heliodor_ruin_start\"",
        "\"entrelumen:heliodor_ruin_start\", \"radius\": 5000"));
    invalid.put("anchor tag", RUIN.replace("\"entrelumen:heliodor_ruin_start\"", "\"#entrelumen:ruins\""));
    invalid.put("bad id", RUIN.replace("\"ruin\"", "\"Ruin!\""));
    invalid.put("condition type", RUIN.replace("\"milestone\", \"milestone\"", "\"kills\", \"milestone\""));
    invalid.put("dimension without id", RUIN.replace(
        "{\"type\": \"anchor\", \"anchor\": \"entrelumen:heliodor_ruin_start\"}", "{\"type\": \"dimension\"}"));
    invalid.put("position size", RUIN.replace(
        "{\"type\": \"anchor\", \"anchor\": \"entrelumen:heliodor_ruin_start\"}",
        "{\"type\": \"position\", \"dimension\": \"minecraft:overworld\", \"pos\": [1, 2]}"));
    invalid.put("lore type", RUIN.replace("\"lore\": true", "\"lore\": \"yes\""));
    invalid.forEach((name, objectives) -> {
      var error = assertThrows(IllegalArgumentException.class,
          () -> CompassTargets.parse(document(objectives), mod -> true, item -> true), name);
      assertTrue(error.getMessage().startsWith("Entrelumen compass "), name);
    });
    assertThrows(IllegalArgumentException.class,
        () -> CompassTargets.parse(JsonParser.parseString("[]"), mod -> true, item -> true));
  }

  @Test
  void unknownItemsFailOnlyForLoadedMods() {
    String objective = """
        {"id": "scale", "act": 1, "kind": "boss", "mods": ["twilightforest"],
         "target": {"type": "dimension", "dimension": "twilightforest:twilight_forest"},
         "advance_when": {"type": "item", "item": "twilightforest:naga_scale"}}""";
    assertTrue(CompassTargets.parse(document(objective), mod -> false, item -> false).isEmpty());
    assertThrows(IllegalArgumentException.class,
        () -> CompassTargets.parse(document(objective), mod -> true, item -> false));
    assertEquals(1, CompassTargets.parse(document(objective), mod -> true, item -> true).size());
  }
}
