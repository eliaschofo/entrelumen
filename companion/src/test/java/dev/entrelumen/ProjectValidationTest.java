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
    assertEquals(15, projects.size());
    assertEquals("entrelumen:atlas", projects.get("atlas_awakened").reward());
    assertThrows(UnsupportedOperationException.class, () -> projects.clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> projects.get("atlas_awakened").items().put("minecraft:stone", 9));
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
