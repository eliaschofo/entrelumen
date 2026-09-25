package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Data of the vein resonators (Elias, 24 September 2026): six tiers of 16 to 96 Ultimine blocks, worn in
 * a Curios charm slot, named in EN/ES, and six pack recipes where each tier consumes the previous one.
 */
class VeinResonatorDataTest {
  private static final Path PACK_RECIPES = Path.of("../pack/kubejs/data/entrelumen/recipe");
  private static final Path ULTIMINE_CONFIG = Path.of("../pack/config/ftbultimine-server.snbt");

  private JsonObject resource(String path) throws IOException {
    var stream = getClass().getResourceAsStream(path);
    assertNotNull(stream, path);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static List<String> ids() {
    List<String> ids = new ArrayList<>();
    for (int tier = 1; tier <= VeinResonatorRules.TIERS; tier++) ids.add("entrelumen:" + VeinResonatorRules.id(tier));
    return ids;
  }

  @Test
  void sixTiersReachSixteenMoreBlocksEachUpToNinetySix() {
    assertEquals(6, VeinResonatorRules.TIERS);
    for (int tier = 0; tier <= 6; tier++) assertEquals(16 * tier, VeinResonatorRules.reach(tier));
    assertEquals(96, VeinResonatorRules.reach(VeinResonatorRules.TIERS));
    assertThrows(IllegalArgumentException.class, () -> VeinResonatorRules.reach(7));
    assertThrows(IllegalArgumentException.class, () -> VeinResonatorRules.reach(-1));
    // The modifier always aims at the tier's reach, whatever max_blocks a server configured.
    assertEquals(0, VeinResonatorRules.modifierAmount(0, 0));
    assertEquals(16, VeinResonatorRules.modifierAmount(1, 0));
    assertEquals(-64, VeinResonatorRules.modifierAmount(0, 64));
    assertEquals(32, VeinResonatorRules.modifierAmount(6, 64));
    assertEquals("entrelumen:vein_resonator", VeinResonatorRules.MODIFIER_ID);
    assertEquals("ftbultimine:max_blocks_modifier", VeinResonatorRules.ULTIMINE_ATTRIBUTE_ID);
    assertThrows(IllegalArgumentException.class, () -> VeinResonatorRules.id(0));
  }

  @Test
  void curiosAcceptsEveryTierInThePlayersCharmSlot() throws Exception {
    var tag = resource("/data/curios/tags/item/charm.json");
    assertFalse(tag.get("replace").getAsBoolean(), "the tag must add to other mods' charms");
    assertEquals(ids(), tag.getAsJsonArray("values").asList().stream().map(v -> v.getAsString()).toList());
    var slots = resource("/data/entrelumen/curios/entities/vein_resonator.json");
    assertFalse(slots.has("replace"), "the slot assignment must not replace other mods' slots");
    assertEquals(List.of("player"), slots.getAsJsonArray("entities").asList().stream().map(v -> v.getAsString()).toList());
    assertEquals(List.of("charm"), slots.getAsJsonArray("slots").asList().stream().map(v -> v.getAsString()).toList());
  }

  @Test
  void namesLoreAndReachLineExistInEnglishAndSpanish() throws Exception {
    var en = resource("/assets/entrelumen/lang/en_us.json");
    var es = resource("/assets/entrelumen/lang/es_es.json");
    String[] roman = {"I", "II", "III", "IV", "V", "VI"};
    for (int tier = 1; tier <= 6; tier++) {
      assertEquals("Vein Resonator " + roman[tier - 1], en.get("item.entrelumen.vein_resonator_" + tier).getAsString());
      assertEquals("Resonador de vetas " + roman[tier - 1], es.get("item.entrelumen.vein_resonator_" + tier).getAsString());
    }
    assertEquals("Ultimine: up to %s blocks", en.get("entrelumen.vein_resonator.reach").getAsString());
    assertEquals("Ultimine: hasta %s bloques", es.get("entrelumen.vein_resonator.reach").getAsString());
    for (var lang : List.of(en, es)) {
      String lore = lang.get("entrelumen.vein_resonator.tooltip").getAsString();
      assertTrue(lore.contains("Terra") && !lore.contains("%") && !lore.contains("\n"), lore);
    }
    assertNotEquals(en.get("entrelumen.vein_resonator.tooltip"), es.get("entrelumen.vein_resonator.tooltip"));
  }

  @Test
  void eachTierConsumesThePreviousOneAndTheLastTwoLuminosities() throws Exception {
    for (int tier = 1; tier <= 6; tier++) {
      JsonObject recipe;
      try (var reader = Files.newBufferedReader(PACK_RECIPES.resolve("vein_resonator_" + tier + ".json"))) {
        recipe = JsonParser.parseReader(reader).getAsJsonObject();
      }
      assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
      assertEquals("entrelumen:vein_resonator_" + tier, recipe.getAsJsonObject("result").get("id").getAsString());
      assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
      Map<String, Integer> items = new HashMap<>();
      var key = recipe.getAsJsonObject("key");
      for (var row : recipe.getAsJsonArray("pattern")) {
        String line = row.getAsString();
        assertEquals(3, line.length());
        for (int i = 0; i < 3; i++) {
          // Left-right symmetric, like every recipe the pack authors.
          assertEquals(line.charAt(i), line.charAt(2 - i), "vein_resonator_" + tier);
          if (line.charAt(i) != ' ')
            items.merge(key.getAsJsonObject(String.valueOf(line.charAt(i))).get("item").getAsString(), 1, Integer::sum);
        }
      }
      long previous = items.keySet().stream().filter(id -> id.startsWith("entrelumen:vein_resonator_")).count();
      long luminosities = items.keySet().stream().filter(id -> id.startsWith("entrelumen:luminosity_")).count();
      if (tier == 1) {
        assertEquals(0, previous, "tier 1 starts the chain");
        assertTrue(items.values().stream().mapToInt(Integer::intValue).sum() <= 6, "tier 1 stays cheap: " + items);
        assertTrue(items.keySet().stream().allMatch(id -> id.startsWith("minecraft:") || id.equals("entrelumen:raw_lens")),
            "tier 1 uses only vanilla materials and the raw lens: " + items);
      } else {
        assertEquals(1, items.get("entrelumen:vein_resonator_" + (tier - 1)), "tier " + tier);
        assertEquals(1, previous, "tier " + tier);
      }
      assertEquals(tier == 6 ? 2 : 0, luminosities, "Luminosities only in the last tier: " + items);
    }
  }

  @Test
  void thePackConfigKeepsUltimineOffWithoutAResonator() throws Exception {
    String config = Files.readString(ULTIMINE_CONFIG);
    assertTrue(config.contains("\t\tmax_blocks: 0\n"), "max_blocks must stay 0");
    assertEquals(1, config.split("max_blocks: ", -1).length - 1);
  }
}
