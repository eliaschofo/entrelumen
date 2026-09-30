package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The Sour Shackle's data: where it drops, where Curios takes it, its damage type and its EN/ES text. */
class SourShackleDataTest {
  static final Path RES = Path.of("src/main/resources");

  static JsonObject json(String path) throws IOException {
    return JsonParser.parseString(Files.readString(RES.resolve(path))).getAsJsonObject();
  }

  static List<String> values(String path) throws IOException {
    List<String> out = new ArrayList<>();
    for (JsonElement e : json(path).getAsJsonArray("values")) out.add(e.getAsString());
    return out;
  }

  @Test
  void theBossChestAlwaysRollsIt() throws IOException {
    int pools = 0;
    for (JsonElement pool : json("data/entrelumen/loot_table/enves/boss.json").getAsJsonArray("pools")) {
      var p = pool.getAsJsonObject();
      for (JsonElement entry : p.getAsJsonArray("entries"))
        if (entry.getAsJsonObject().get("type").getAsString().equals("entrelumen:sour_shackle")) {
          pools++;
          assertEquals(1, p.get("rolls").getAsInt());
          assertEquals(1, p.getAsJsonArray("entries").size(), "100%: the pool has nothing else");
          assertFalse(p.has("conditions"));
        }
    }
    assertEquals(1, pools);
  }

  @Test
  void curiosTakesItOnTheBracelet() throws IOException {
    assertTrue(values("data/curios/tags/item/bracelet.json").contains("entrelumen:sour_shackle"));
    var entities = json("data/entrelumen/curios/entities/sour_shackle.json");
    assertEquals("player", entities.getAsJsonArray("entities").get(0).getAsString());
    assertEquals("bracelet", entities.getAsJsonArray("slots").get(0).getAsString());
  }

  @Test
  void theBurstIsMagicLikeAndIgnoresArmour() throws IOException {
    var type = json("data/entrelumen/damage_type/sour_burst.json");
    assertEquals("never", type.get("scaling").getAsString());
    assertEquals("entrelumen.sour_burst", type.get("message_id").getAsString());
    for (String tag : List.of("bypasses_armor", "bypasses_cooldown", "no_knockback", "witch_resistant_to", "avoids_guardian_thorns"))
      assertTrue(values("data/minecraft/tags/damage_type/" + tag + ".json").contains("entrelumen:sour_burst"), tag);
    assertTrue(values("data/neoforge/tags/damage_type/is_magic.json").contains("entrelumen:sour_burst"));
  }

  @Test
  void theSourLightIsABoss() throws IOException {
    assertTrue(values("data/c/tags/entity_type/bosses.json").contains("entrelumen:white_wither"));
  }

  @Test
  void itsTextIsInBothLanguages() throws IOException {
    var en = json("assets/entrelumen/lang/en_us.json");
    var es = json("assets/entrelumen/lang/es_es.json");
    Pattern placeholder = Pattern.compile("%(\\d+\\$)?s");
    for (String key : List.of("item.entrelumen.sour_shackle", "item.entrelumen.sour_shackle.marks", "item.entrelumen.sour_shackle.burst",
        "death.attack.entrelumen.sour_burst", "death.attack.entrelumen.sour_burst.item", "death.attack.entrelumen.sour_burst.player")) {
      assertTrue(en.has(key) && es.has(key), key);
      assertEquals(placeholder.matcher(en.get(key).getAsString()).results().count(),
          placeholder.matcher(es.get(key).getAsString()).results().count(), key);
    }
    assertEquals("Grillete Agrio", es.get("item.entrelumen.sour_shackle").getAsString());
    assertEquals("Sour Shackle", en.get("item.entrelumen.sour_shackle").getAsString());
    assertEquals(4, placeholder.matcher(en.get("item.entrelumen.sour_shackle.burst").getAsString()).results().count());
  }

  @Test
  void theIconAndTheCuffAreThere() {
    assertTrue(Files.exists(RES.resolve("assets/entrelumen/textures/item/sour_shackle.png")));
    assertTrue(Files.exists(RES.resolve("assets/entrelumen/models/item/sour_shackle.json")));
    assertTrue(Files.exists(RES.resolve("assets/entrelumen/textures/entity/sour_shackle/cuff.png")));
  }
}
