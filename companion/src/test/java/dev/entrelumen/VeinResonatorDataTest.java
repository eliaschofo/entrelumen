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
 * Data of the vein resonators (Elias, 24 September 2026; nerfed to four tiers on the 25th): 8, 16, 32 and 64
 * Ultimine blocks, worn in a Curios charm slot, named in EN/ES, and four pack recipes where each tier
 * consumes the previous one in the centre of a fork.
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
  void fourTiersReachEightSixteenThirtyTwoAndSixtyFourBlocks() {
    assertEquals(4, VeinResonatorRules.TIERS);
    int[] reach = {0, 8, 16, 32, 64};
    for (int tier = 0; tier <= 4; tier++) assertEquals(reach[tier], VeinResonatorRules.reach(tier));
    assertThrows(IllegalArgumentException.class, () -> VeinResonatorRules.reach(5));
    assertThrows(IllegalArgumentException.class, () -> VeinResonatorRules.reach(-1));
    assertThrows(IllegalArgumentException.class, () -> VeinResonatorRules.id(5));
    // The modifier always aims at the tier's reach, whatever max_blocks a server configured.
    assertEquals(0, VeinResonatorRules.modifierAmount(0, 0));
    assertEquals(8, VeinResonatorRules.modifierAmount(1, 0));
    assertEquals(-64, VeinResonatorRules.modifierAmount(0, 64));
    assertEquals(0, VeinResonatorRules.modifierAmount(4, 64));
    assertEquals(-32, VeinResonatorRules.modifierAmount(3, 64));
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
    String[] roman = {"I", "II", "III", "IV"};
    for (int tier = 1; tier <= 4; tier++) {
      assertEquals("Vein Resonator " + roman[tier - 1], en.get("item.entrelumen.vein_resonator_" + tier).getAsString());
      assertEquals("Resonador de vetas " + roman[tier - 1], es.get("item.entrelumen.vein_resonator_" + tier).getAsString());
    }
    for (int gone = 5; gone <= 6; gone++) {
      assertFalse(en.has("item.entrelumen.vein_resonator_" + gone), "tier " + gone + " was removed");
      assertFalse(es.has("item.entrelumen.vein_resonator_" + gone), "tier " + gone + " was removed");
    }
    assertEquals("Ultimine: up to %s blocks", en.get("entrelumen.vein_resonator.reach").getAsString());
    assertEquals("Ultimine: hasta %s bloques", es.get("entrelumen.vein_resonator.reach").getAsString());
    for (var lang : List.of(en, es)) {
      String lore = lang.get("entrelumen.vein_resonator.tooltip").getAsString();
      assertTrue(lore.contains("Terra") && !lore.contains("%") && !lore.contains("\n"), lore);
    }
    assertNotEquals(en.get("entrelumen.vein_resonator.tooltip"), es.get("entrelumen.vein_resonator.tooltip"));
  }

  /** Elias's drawings (25 September 2026): a fork, the previous tier in the centre, the new materials around it. */
  private static final Map<Integer, List<String>> PATTERNS = Map.of(
      1, List.of("G G", "GDG", " G "), 2, List.of("E E", "ERE", " N "), 3, List.of("X X", "XRX", " S "),
      4, List.of(" A ", " R ", " B "));
  private static final Map<Integer, Map<String, Integer>> INPUTS = Map.of(
      1, Map.of("minecraft:gold_ingot", 5, "minecraft:diamond", 1),
      2, Map.of("minecraft:emerald_block", 4, "minecraft:netherite_ingot", 1, "entrelumen:vein_resonator_1", 1),
      3, Map.of("minecraft:end_stone", 4, "minecraft:nether_star", 1, "entrelumen:vein_resonator_2", 1),
      4, Map.of("entrelumen:luminosity_exploration", 1, "entrelumen:luminosity_engineering", 1,
          "entrelumen:vein_resonator_3", 1));

  @Test
  void eachTierIsAForkAroundThePreviousOne() throws Exception {
    for (int tier = 1; tier <= 6; tier++) {
      var path = PACK_RECIPES.resolve("vein_resonator_" + tier + ".json");
      if (tier > VeinResonatorRules.TIERS) {
        assertFalse(Files.exists(path), "the pack still ships " + path.getFileName());
        continue;
      }
      JsonObject recipe;
      try (var reader = Files.newBufferedReader(path)) {
        recipe = JsonParser.parseReader(reader).getAsJsonObject();
      }
      assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
      assertEquals("entrelumen:vein_resonator_" + tier, recipe.getAsJsonObject("result").get("id").getAsString());
      assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
      List<String> pattern = recipe.getAsJsonArray("pattern").asList().stream().map(v -> v.getAsString()).toList();
      assertEquals(PATTERNS.get(tier), pattern, "vein_resonator_" + tier);
      Map<String, Integer> items = new HashMap<>();
      var key = recipe.getAsJsonObject("key");
      for (String line : pattern) {
        for (int i = 0; i < 3; i++) {
          // Left-right symmetric, like every recipe the pack authors.
          assertEquals(line.charAt(i), line.charAt(2 - i), "vein_resonator_" + tier);
          if (line.charAt(i) != ' ')
            items.merge(key.getAsJsonObject(String.valueOf(line.charAt(i))).get("item").getAsString(), 1, Integer::sum);
        }
      }
      assertEquals(INPUTS.get(tier), items, "vein_resonator_" + tier);
      if (tier > 1) assertEquals('R', pattern.get(1).charAt(1), "the previous tier sits in the centre");
    }
  }

  @Test
  void thePackConfigKeepsUltimineOffWithoutAResonator() throws Exception {
    String config = Files.readString(ULTIMINE_CONFIG);
    assertTrue(config.contains("\t\tmax_blocks: 0\n"), "max_blocks must stay 0");
    assertEquals(1, config.split("max_blocks: ", -1).length - 1);
  }
}
