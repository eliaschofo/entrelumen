package dev.entrelumen.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class JeedEffectJsonTest {
  @Test
  void serializedEffectCanBeReadBackWithTypeAndCodecFieldsIntact() {
    JsonObject effect = JsonParser.parseString("""
        {"id":"minecraft:haste","show_icon":true,"neoforge:cures":["milk","protected_by_totem"]}
        """).getAsJsonObject();
    JsonObject codecFields = effect.deepCopy();

    JeedEffectJson.addTypeIfMissing(effect);
    JsonObject reloaded = JsonParser.parseString(effect.toString()).getAsJsonObject();
    assertEquals("mob_effect", reloaded.get("type").getAsString());
    reloaded.remove("type");
    assertEquals(codecFields, reloaded);

    JeedEffectJson.addTypeIfMissing(effect);
    assertEquals("mob_effect", effect.get("type").getAsString());
    assertEquals(4, effect.size());
  }

  @Test
  void existingTypeAndOtherJsonShapesAreUntouched() {
    JsonObject otherIngredient = JsonParser.parseString("""
        {"type":"item","id":"minecraft:stone","count":2}
        """).getAsJsonObject();
    JsonObject before = otherIngredient.deepCopy();
    JeedEffectJson.addTypeIfMissing(otherIngredient);
    assertEquals(before, otherIngredient);

    JsonElement primitive = JsonParser.parseString("\"unrelated\"");
    JeedEffectJson.addTypeIfMissing(primitive);
    assertEquals("unrelated", primitive.getAsString());
  }
}
