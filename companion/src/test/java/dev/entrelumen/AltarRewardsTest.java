package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The six altars are act rewards, one project each, and each duplicates like a smithing template. */
class AltarRewardsTest {
  /** Altar, the project that grants it, its act and the act component of its duplication recipe. */
  private record Grant(String project, int act, String component) {}

  private static final Map<String, Grant> GRANTS = new LinkedHashMap<>();

  static {
    GRANTS.put("terraform_altar", new Grant("workshop_hands", 3, "entrelumen:handling_core"));
    GRANTS.put("growth_altar", new Grant("nursery_protocol", 3, "entrelumen:propagation_core"));
    GRANTS.put("peace_altar", new Grant("signal_exchange", 3, "entrelumen:routing_matrix"));
    GRANTS.put("renewal_altar", new Grant("pollinator_treaty", 4, "entrelumen:propagation_core"));
    GRANTS.put("repose_altar", new Grant("horizon_survey", 4, "entrelumen:horizon_chart"));
    GRANTS.put("time_altar", new Grant("sealed_memory", 4, "entrelumen:containment_seal"));
  }

  private JsonObject resource(String path) throws IOException {
    var stream = getClass().getResourceAsStream(path);
    assertNotNull(stream, path);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  @Test
  void eachAltarIsTheRewardOfExactlyOneProjectOfItsAct() throws Exception {
    var projects = Projects.parse(resource("/data/entrelumen/campaign/projects.json"), id -> true);
    Map<String, String> grantedBy = new HashMap<>();
    projects.forEach((id, project) -> {
      if (project.reward().endsWith("_altar"))
        assertNull(grantedBy.put(project.reward(), id), "an altar is granted twice: " + project.reward());
    });
    assertEquals(GRANTS.size(), grantedBy.size());
    GRANTS.forEach((altar, grant) -> {
      assertEquals(grant.project(), grantedBy.get("entrelumen:" + altar), altar);
      assertEquals(grant.act(), projects.get(grant.project()).act(), altar);
    });
  }

  @Test
  void eachAltarDuplicatesWithItsActComponentAndDiamondsIntoTwo() throws Exception {
    for (var entry : GRANTS.entrySet()) {
      String altar = entry.getKey();
      var recipe = resource("/data/entrelumen/recipe/" + altar + "_duplication.json");
      assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
      var pattern = recipe.getAsJsonArray("pattern");
      Map<Character, Integer> counts = new HashMap<>();
      for (var row : pattern) for (char c : row.getAsString().toCharArray()) counts.merge(c, 1, Integer::sum);
      var key = recipe.getAsJsonObject("key");
      Map<String, Integer> items = new HashMap<>();
      counts.forEach((symbol, count) ->
          items.merge(key.getAsJsonObject(String.valueOf(symbol)).get("item").getAsString(), count, Integer::sum));
      assertEquals(Map.of("entrelumen:" + altar, 1, entry.getValue().component(), 4, "minecraft:diamond", 4), items, altar);
      // Symmetric: the pattern reads the same mirrored and transposed.
      String[] rows = new String[3];
      for (int i = 0; i < 3; i++) rows[i] = pattern.get(i).getAsString();
      for (int r = 0; r < 3; r++)
        for (int c = 0; c < 3; c++) {
          assertEquals(rows[r].charAt(c), rows[r].charAt(2 - c), altar);
          assertEquals(rows[r].charAt(c), rows[2 - r].charAt(c), altar);
          assertEquals(rows[r].charAt(c), rows[c].charAt(r), altar);
        }
      var result = recipe.getAsJsonObject("result");
      assertEquals("entrelumen:" + altar, result.get("id").getAsString());
      assertEquals(2, result.get("count").getAsInt());
    }
  }
}
