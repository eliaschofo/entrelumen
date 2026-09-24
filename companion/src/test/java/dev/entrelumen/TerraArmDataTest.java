package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Data of Terra's Arm: how it is obtained, where Curios accepts it and its EN/ES text. */
class TerraArmDataTest {
  private static final String ARM = "entrelumen:terra_arm";

  private JsonObject resource(String path) throws IOException {
    var stream = getClass().getResourceAsStream(path);
    assertNotNull(stream, path);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  @Test
  void closingTheActTwoArchiveGrantsTheArmAndNothingElseDoes() throws Exception {
    var projects = Projects.parse(resource("/data/entrelumen/campaign/projects.json"), id -> true);
    var archive = projects.get("lost_workshop");
    assertEquals(2, archive.act());
    assertEquals(ARM, archive.reward());
    assertEquals(java.util.Map.of("minecraft:paper", 3, "minecraft:copper_ingot", 1), archive.items());
    assertEquals(List.of("lost_workshop"),
        projects.entrySet().stream().filter(e -> e.getValue().reward().equals(ARM)).map(e -> e.getKey()).toList());
  }

  @Test
  void workshopChestHoldsExactlyOneArm() throws Exception {
    var table = resource("/data/entrelumen/loot_table/chests/ruin_act2_workshop.json");
    assertEquals("minecraft:chest", table.get("type").getAsString());
    var pools = table.getAsJsonArray("pools");
    assertEquals(1, pools.size());
    var pool = pools.get(0).getAsJsonObject();
    assertEquals(1, pool.get("rolls").getAsInt());
    assertFalse(pool.has("conditions") || pool.has("functions") || pool.has("bonus_rolls"));
    var entries = pool.getAsJsonArray("entries");
    assertEquals(1, entries.size());
    var entry = entries.get(0).getAsJsonObject();
    assertEquals("minecraft:item", entry.get("type").getAsString());
    assertEquals(ARM, entry.get("name").getAsString());
    assertFalse(entry.has("conditions") || entry.has("functions"));
  }

  @Test
  void curiosAcceptsTheArmOnThePlayersHands() throws Exception {
    var tag = resource("/data/curios/tags/item/hands.json");
    assertFalse(tag.get("replace").getAsBoolean(), "the tag must add to other mods' hands items");
    assertEquals(List.of(ARM), tag.getAsJsonArray("values").asList().stream().map(v -> v.getAsString()).toList());
    var slots = resource("/data/entrelumen/curios/entities/terra_arm.json");
    assertFalse(slots.has("replace"), "the slot assignment must not replace other mods' slots");
    assertEquals(List.of("player"), slots.getAsJsonArray("entities").asList().stream().map(v -> v.getAsString()).toList());
    assertEquals(List.of("hands"), slots.getAsJsonArray("slots").asList().stream().map(v -> v.getAsString()).toList());
  }

  @Test
  void nameAndLoreExistInEnglishAndSpanish() throws Exception {
    var en = resource("/assets/entrelumen/lang/en_us.json");
    var es = resource("/assets/entrelumen/lang/es_es.json");
    assertEquals("Terra's Arm", en.get("item.entrelumen.terra_arm").getAsString());
    assertEquals("Brazo de Terra", es.get("item.entrelumen.terra_arm").getAsString());
    for (var lang : List.of(en, es)) {
      String lore = lang.get("entrelumen.terra_arm.tooltip").getAsString();
      assertTrue(lore.startsWith("Terra ") && !lore.contains("%") && !lore.contains("\n"), lore);
    }
    assertNotEquals(en.get("entrelumen.terra_arm.tooltip"), es.get("entrelumen.terra_arm.tooltip"));
  }
}
