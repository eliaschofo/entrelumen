package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class QuestClaimGuardTest {
  @Test
  void partyEntryTimesRoundTripAndDefaultToZero() {
    var since = new QuestClaimGuard.PartySince();
    UUID player = UUID.randomUUID(), other = UUID.randomUUID();
    assertEquals(0L, since.get(player), "An unrecorded player must read as joined at 0 (strict side)");
    since.put(player, 1_727_000_000_000L);
    assertTrue(since.isDirty());

    CompoundTag saved = since.save(new CompoundTag(), null);
    saved.putLong("not-a-uuid", 5L);
    var loaded = QuestClaimGuard.PartySince.load(saved, null);
    assertEquals(1_727_000_000_000L, loaded.get(player));
    assertEquals(0L, loaded.get(other));

    loaded.remove(player);
    assertEquals(0L, loaded.get(player));
    assertTrue(loaded.save(new CompoundTag(), null).isEmpty(), "The malformed key and the removed entry must not persist");
  }

  @Test
  void carriedItemTaskTextExistsInEnglishAndSpanish() throws Exception {
    JsonObject en = lang("en_us"), es = lang("es_es");
    for (String key : new String[] {"ftbquests.task.entrelumen.carried_item", "entrelumen.task.carried_item.hint"}) {
      assertTrue(en.has(key), "en_us lacks " + key);
      assertTrue(es.has(key), "es_es lacks " + key);
      assertNotEquals(en.get(key).getAsString(), es.get(key).getAsString(), key + " is not translated");
    }
  }

  private JsonObject lang(String code) throws Exception {
    try (var in = getClass().getResourceAsStream("/assets/entrelumen/lang/" + code + ".json")) {
      assertNotNull(in, code + " is not on the test classpath");
      return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
}
