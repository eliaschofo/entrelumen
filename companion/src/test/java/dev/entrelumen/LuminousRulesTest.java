package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LuminousRulesTest {
  /**
   * Passive ceilings of the pinned pack, read on 24 September 2026 from the installed configs and
   * JAR bytecode (docs/design/luminous-gear.md): armour points of the set, toughness per piece,
   * knockback resistance per piece and enchantability.
   */
  private record Ceiling(String name, int setArmor, float toughness, float knockback, int enchantability) {}

  private static final Ceiling[] CEILINGS = {
    new Ceiling("netherite", 20, 3f, 0.1f, 15),
    new Ceiling("MekaSuit (mekanism startup.toml)", 20, 3f, 0.1f, 0),
    new Ceiling("Refined Obsidian (Mekanism Tools)", 31, 5f, 0.2f, 18),
    new Ceiling("Ignitium (Cataclysm Armortier)", 32, 4f, 0.15f, 15),
    new Ceiling("Draconic chestpiece (diamond material, chest only)", 8, 2f, 0f, 35),
  };

  @Test
  void armourTopsEveryPassiveSetOfThePack() {
    assertEquals(60, LuminousRules.setArmor());
    for (var ceiling : CEILINGS) {
      assertTrue(LuminousRules.setArmor() > ceiling.setArmor(), ceiling.name());
      assertTrue(LuminousRules.TOUGHNESS > ceiling.toughness(), ceiling.name());
      assertTrue(LuminousRules.KNOCKBACK_RESISTANCE > ceiling.knockback(), ceiling.name());
      assertTrue(LuminousRules.ENCHANTABILITY > ceiling.enchantability(), ceiling.name());
    }
    // Four pieces reach full knockback immunity exactly, not beyond.
    assertEquals(1.0f, 4 * LuminousRules.KNOCKBACK_RESISTANCE, 1e-6);
    // Apothic Attributes lets toughness resist armour pierce/shred at 2 % per point up to 60 %: capped.
    assertTrue(4 * LuminousRules.TOUGHNESS * 0.02f >= 0.6f);
    // Every piece at least doubles the best Silent Gear material's split (Refined Obsidian 5/12/8/5).
    int[] refinedObsidian = {5, 12, 8, 5};
    for (int i = 0; i < 4; i++)
      assertTrue(LuminousRules.armorByPiece().get(i) >= 1.8 * refinedObsidian[i], "piece " + i);
  }

  @Test
  void toolsTopEveryToolOfThePackIncludingChaoticBase() {
    assertTrue(LuminousRules.TOOL_USES > 3652 * 8); // Tyrian Steel, the most durable Silent Gear material
    assertTrue(LuminousRules.TOOL_SPEED > 50f); // Draconic chaotic harvest speed (with energy and AoE)
    assertEquals(30, LuminousRules.swordDamage());
    assertTrue(LuminousRules.swordDamage() > 17.5f); // Draconic chaotic sword base (2.5 x 7) before modules
    assertEquals(32, LuminousRules.axeDamage());
    assertEquals(1f, 1 + LuminousRules.TOOL_ATTACK_BONUS + LuminousRules.HOE_DAMAGE, 1e-6);
  }

  @Test
  void damageNeverBreaksAPieceAndItRestsDimmed() {
    int max = 1600;
    assertEquals(10, LuminousRules.allowedDamage(0, max, 10));
    assertEquals(max - 1, LuminousRules.allowedDamage(0, max, 100_000));
    assertEquals(4, LuminousRules.allowedDamage(max - 5, max, 9));
    assertEquals(0, LuminousRules.allowedDamage(max - 1, max, 1));
    assertEquals(0, LuminousRules.allowedDamage(max - 1, max, 0));
    assertEquals(-3, LuminousRules.allowedDamage(20, max, -3)); // repairs pass through untouched
    assertFalse(LuminousRules.dimmed(max - 2, max));
    assertTrue(LuminousRules.dimmed(max - 1, max));
    // Every reachable damage value after any sequence of hits stays below the maximum.
    int damage = 0;
    for (int hit = 1; hit < 5000; hit += 7) {
      damage += LuminousRules.allowedDamage(damage, max, hit);
      assertTrue(damage < max);
    }
    assertTrue(LuminousRules.dimmed(damage, max));
  }

  @Test
  void lightRepairsFromLevelTwelveAndFasterInSunlight() {
    for (int brightness = 0; brightness < 12; brightness++)
      assertEquals(0, LuminousRules.lightRepair(brightness, 500), "brightness " + brightness);
    assertEquals(1, LuminousRules.lightRepair(12, 500));
    assertEquals(2, LuminousRules.lightRepair(13, 500));
    assertEquals(4, LuminousRules.lightRepair(15, 500));
    assertEquals(4, LuminousRules.lightRepair(20, 500));
    assertEquals(2, LuminousRules.lightRepair(15, 2)); // never repairs past full
    assertEquals(0, LuminousRules.lightRepair(15, 0));
    // A spent chestplate (3999 damage) is whole again after under seventeen minutes of daylight.
    int spent = 16 * LuminousRules.ARMOR_DURABILITY_FACTOR - 1;
    int seconds = (int) Math.ceil((double) spent / LuminousRules.lightRepair(15, spent));
    assertTrue(seconds < 17 * 60, "seconds " + seconds);
    assertTrue(LuminousRules.FLIGHT_LANDING_TICKS >= 100);
  }

  @Test
  void setBonusNeedsFourLitPieces() {
    boolean[] all = {true, true, true, true};
    boolean[] none = {false, false, false, false};
    assertTrue(LuminousRules.fullSet(all, none));
    assertFalse(LuminousRules.fullSet(new boolean[] {true, true, true, false}, none));
    assertFalse(LuminousRules.fullSet(all, new boolean[] {false, false, true, false}));
    assertFalse(LuminousRules.fullSet(new boolean[3], new boolean[3]));
    assertTrue(LuminousRules.nightVisionSteady());
    assertEquals(20, LuminousRules.INTERVAL_TICKS);
  }

  @Test
  void sixLuminositiesWithDistinctStoryColours() {
    assertEquals(6, LuminousRules.Discipline.values().length);
    Set<Integer> colours = new HashSet<>();
    Set<String> items = new HashSet<>();
    for (var discipline : LuminousRules.Discipline.values()) {
      assertTrue(colours.add(discipline.color), discipline.id);
      assertTrue(items.add(discipline.item()), discipline.id);
      assertTrue(discipline.item().matches("luminosity_[a-z]+"));
    }
    assertEquals(0xE07B39, LuminousRules.Discipline.ENGINEERING.color); // copper orange
    assertEquals(0xA66BE8, LuminousRules.Discipline.ARCANE.color); // violet
    assertEquals(0x6CC66C, LuminousRules.Discipline.NATURE.color); // green
    assertEquals(0x7CC8F0, LuminousRules.Discipline.EXPLORATION.color); // sky blue
    assertEquals(0x3CC7B4, LuminousRules.Discipline.LOGISTICS.color); // turquoise
    assertEquals(0xF2B84B, LuminousRules.Discipline.HABITATION.color); // warm gold
    assertFalse(colours.contains(LuminousRules.LUMINOUS_COLOR));
  }

  /**
   * The Silent Gear material of the Luminous Ingot against the best value of every stat among the
   * 138 materials of Silent Gear 4.2.1.1 (read from the pinned JAR on 24 September 2026).
   */
  @Test
  void silentGearMaterialIsTheBestByFar() throws Exception {
    JsonObject material;
    try (var stream = LuminousRulesTest.class.getResourceAsStream("/data/entrelumen/silentgear_materials/luminous.json")) {
      assertNotNull(stream, "luminous Silent Gear material missing from resources");
      material = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    var main = material.getAsJsonObject("properties").getAsJsonObject("silentgear:main");
    Map<String, Double> best = new LinkedHashMap<>();
    best.put("armor", 30.0); // refined_obsidian
    best.put("armor/helmet", 5.0); // tyrian_steel, refined_obsidian
    best.put("armor/chestplate", 12.0); // refined_obsidian
    best.put("armor/leggings", 8.0); // refined_obsidian
    best.put("armor/boots", 5.0); // refined_obsidian
    best.put("armor_durability", 84.0); // barrier
    best.put("armor_toughness", 16.0); // refined_obsidian
    best.put("magic_armor", 19.0); // azure_electrum
    best.put("attack_damage", 10.0); // refined_obsidian
    best.put("attack_speed", 0.4); // glowstone
    best.put("magic_damage", 11.0); // azure_electrum
    best.put("ranged_damage", 4.0); // tyrian_steel, refined_obsidian
    best.put("durability", 3652.0); // tyrian_steel
    best.put("enchantment_value", 40.0); // refined_obsidian
    best.put("harvest_speed", 29.0); // azure_electrum
    best.put("charging_value", 1.5); // uranium, electrum, azure_electrum
    best.put("draw_speed", 0.4); // electrum
    best.put("projectile_accuracy", 1.5); // azure_electrum
    best.put("projectile_speed", 2.0); // azure_electrum
    best.put("rarity", 111.0); // barrier
    for (var entry : best.entrySet()) {
      double value = main.get(entry.getKey()).getAsDouble();
      assertTrue(value >= 1.3 * entry.getValue(), entry.getKey() + " " + value + " vs " + entry.getValue());
    }
    // The Silent Gear pieces match the companion set piece for piece.
    assertEquals(LuminousRules.HELMET_ARMOR, main.get("armor/helmet").getAsInt());
    assertEquals(LuminousRules.CHESTPLATE_ARMOR, main.get("armor/chestplate").getAsInt());
    assertEquals(LuminousRules.LEGGINGS_ARMOR, main.get("armor/leggings").getAsInt());
    assertEquals(LuminousRules.BOOTS_ARMOR, main.get("armor/boots").getAsInt());
    assertEquals(4 * LuminousRules.TOUGHNESS, main.get("armor_toughness").getAsFloat(), 1e-6); // split in four
    assertEquals(LuminousRules.KNOCKBACK_RESISTANCE, main.get("knockback_resistance").getAsFloat() / 10f, 1e-6);
    assertEquals(LuminousRules.ENCHANTABILITY, main.get("enchantment_value").getAsInt());
    assertEquals("entrelumen:incorrect_for_luminous_tool",
        main.getAsJsonObject("harvest_tier").get("incorrect_blocks_for_tool").getAsString());
    var traits = new HashSet<String>();
    main.getAsJsonArray("traits").forEach(t -> traits.add(t.getAsJsonObject().get("trait").getAsString()));
    assertTrue(traits.contains("entrelumen:radiant") && traits.contains("silentgear:lustrous")
        && traits.contains("silentgear:holy") && traits.contains("silentgear:refractive"), traits.toString());
    for (String part : List.of("silentgear:tip", "silentgear:coating"))
      assertTrue(material.getAsJsonObject("properties").getAsJsonObject(part).toString().contains("entrelumen:radiant"), part);
    var crafting = material.getAsJsonObject("crafting");
    assertFalse(crafting.get("can_salvage").getAsBoolean(), "salvaging would refund Luminosities");
    assertEquals("entrelumen:luminous_ingot", crafting.getAsJsonObject("ingredient").get("item").getAsString());
  }
}
