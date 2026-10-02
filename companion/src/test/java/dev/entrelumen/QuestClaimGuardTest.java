package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class QuestClaimGuardTest {
  @Test
  void partyEntryTimesRoundTripAndDefaultToZero() {
    var since = new QuestClaimGuard.PartySince();
    UUID player = UUID.randomUUID(), other = UUID.randomUUID();
    assertEquals(0L, since.get(player), "An unrecorded player must read as joined at 0 (strict side)");
    assertTrue(since.counts(player).isEmpty());
    long bounty = 0xF00D_0000_0000_00AAL; // a quest id above Long.MAX_VALUE as unsigned hex
    since.put(player, 1_727_000_000_000L, Map.of(bounty, 3, 42L, 1));
    since.put(other, 1_727_000_000_500L, Map.of());
    assertTrue(since.isDirty());

    CompoundTag saved = since.save(new CompoundTag(), null);
    saved.putLong("not-a-uuid", 5L);
    var loaded = QuestClaimGuard.PartySince.load(saved, null);
    assertEquals(1_727_000_000_000L, loaded.get(player));
    assertEquals(Map.of(bounty, 3, 42L, 1), loaded.counts(player),
        "The party's repeatable completion counts at entry must survive a restart");
    assertEquals(1_727_000_000_500L, loaded.get(other));
    assertTrue(loaded.counts(other).isEmpty());

    loaded.remove(player);
    loaded.remove(other);
    assertEquals(0L, loaded.get(player));
    assertTrue(loaded.counts(player).isEmpty(), "Leaving must drop the entry counts too");
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
