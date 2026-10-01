package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The player-facing lines added or reworded by the ultra review's lang batch: both languages, every Spanish locale
 * copy, matching placeholders, the promises the text makes, and voseo.
 */
class LangFeedbackKeysTest {
  private static final Path LANG = Path.of("src/main/resources/assets/entrelumen/lang");
  private static final List<String> SPANISH_COPIES = List.of("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve");
  /** key -> number of format arguments the line takes (%s). */
  private static final java.util.Map<String, Integer> NEW_KEYS = java.util.Map.ofEntries(
      java.util.Map.entry("entrelumen.rtp.dragon", 0),
      java.util.Map.entry("entrelumen.rtp.hurt", 0),
      java.util.Map.entry("entrelumen.ruin.no_teleport", 0),
      java.util.Map.entry("entrelumen.enves.peaceful", 0),
      java.util.Map.entry("entrelumen.enves.giveup.confirm", 1),
      java.util.Map.entry("entrelumen.enves.giveup.confirm_button", 0),
      java.util.Map.entry("entrelumen.enves.giveup.members_inside", 0),
      java.util.Map.entry("entrelumen.enves.given_up_by", 1),
      java.util.Map.entry("entrelumen.enves.no_teleport", 0),
      java.util.Map.entry("entrelumen.apotheosis.tier.root.desc", 1),
      java.util.Map.entry("entrelumen.solsticio.shop.fixed", 0),
      java.util.Map.entry("entrelumen.solsticio.story.battery", 1),
      java.util.Map.entry("entrelumen.ark.effect.wireless_charging.detail", 0),
      java.util.Map.entry("entrelumen.enves.champion.fell", 1),
      java.util.Map.entry("entrelumen.ark.screen.reason.core", 1));
  private static final Pattern TU_FORM = Pattern.compile(
      "\\b(puedes|tienes|haz|vuelve|sostén|toma|usa|ten|mira|elige|pulsa|sal|ven|pagaste tú)\\b",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

  private static JsonObject json(String locale) throws Exception {
    return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json"))).getAsJsonObject();
  }

  private static int args(String text) {
    int n = 0;
    for (int i = 0; i + 1 < text.length(); i++) {
      if (text.charAt(i) != '%') continue;
      if (text.charAt(i + 1) == '%') i++;
      else if (text.charAt(i + 1) == 's') n++;
    }
    return n;
  }

  @Test
  void everySpanishLocaleCopiesEsEsAndTheLanguagesAgree() throws Exception {
    String es = Files.readString(LANG.resolve("es_es.json"));
    for (String locale : SPANISH_COPIES) assertEquals(es, Files.readString(LANG.resolve(locale + ".json")), locale + " drifted from es_es");
    assertEquals(json("en_us").keySet(), json("es_es").keySet(), "EN/ES parity");
  }

  @Test
  void theNewLinesExistInBothLanguagesWithTheSameArguments() throws Exception {
    var en = json("en_us");
    var es = json("es_es");
    for (var entry : NEW_KEYS.entrySet()) {
      String key = entry.getKey();
      assertTrue(en.has(key) && es.has(key), "Missing in a language: " + key);
      assertFalse(en.get(key).getAsString().isBlank(), key);
      assertEquals(entry.getValue(), args(en.get(key).getAsString()), "EN argument count: " + key);
      assertEquals(entry.getValue(), args(es.get(key).getAsString()), "ES argument count: " + key);
      assertNotEquals(en.get(key).getAsString(), es.get(key).getAsString(), "Untranslated: " + key);
      assertFalse(TU_FORM.matcher(es.get(key).getAsString()).find(), "Spanish line is not voseo: " + key);
    }
  }

  @Test
  void theBatteryErrandPromisesTheOffHand() throws Exception {
    var en = json("en_us");
    var es = json("es_es");
    assertTrue(en.get("entrelumen.solsticio.story.battery").getAsString().contains("off hand"));
    assertTrue(es.get("entrelumen.solsticio.story.battery").getAsString().contains("mano secundaria"));
    assertTrue(en.get("entrelumen.solsticio.character.inventor.power").getAsString().contains("off hand"));
    assertTrue(es.get("entrelumen.solsticio.character.inventor.power").getAsString().contains("mano secundaria"));
  }

  @Test
  void homeAndChargingTextsOnlyPromiseWhatTheCodeDoes() throws Exception {
    var en = json("en_us");
    var es = json("es_es");
    String home = en.get("entrelumen.ark.effect.home.detail").getAsString();
    assertTrue(home.contains("Solsticio") && home.contains("Envés"), home);
    String homeEs = es.get("entrelumen.ark.effect.home.detail").getAsString();
    assertTrue(homeEs.contains("Solsticio") && homeEs.contains("Envés"), homeEs);
    String charge = en.get("entrelumen.ark.effect.wireless_charging.detail").getAsString();
    String chargeEs = es.get("entrelumen.ark.effect.wireless_charging.detail").getAsString();
    assertFalse(charge.startsWith("Level") || chargeEs.startsWith("Nivel"), "Charging does not depend on the Ark level");
    assertTrue(charge.contains("%%") && chargeEs.contains("%%"), "The percent sign stays escaped");
  }

  @Test
  void countLinesHaveNoPluralToGetWrong() throws Exception {
    for (String locale : List.of("en_us", "es_es")) {
      var lang = json(locale);
      for (String key : List.of("entrelumen.enves.champion.fell", "entrelumen.ark.screen.reason.core")) {
        String text = lang.get(key).getAsString();
        assertTrue(text.matches(".*: %s[.]?"), locale + ": " + key + " is not a 'Label: %s' line: " + text);
        assertFalse(Pattern.compile("%s[ ]+(seals|blocks|sellos|bloques)").matcher(text).find(),
            locale + ": " + key + " reads wrong with 1: " + text);
      }
    }
  }
}
