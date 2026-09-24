package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
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
    assertEquals(34, LuminousRules.setArmor());
    for (var ceiling : CEILINGS) {
      assertTrue(LuminousRules.setArmor() > ceiling.setArmor(), ceiling.name());
      assertTrue(LuminousRules.TOUGHNESS > ceiling.toughness(), ceiling.name());
      assertTrue(LuminousRules.KNOCKBACK_RESISTANCE > ceiling.knockback(), ceiling.name());
      assertTrue(LuminousRules.ENCHANTABILITY > ceiling.enchantability(), ceiling.name());
    }
    // Four pieces reach full knockback immunity exactly, not beyond.
    assertEquals(1.0f, 4 * LuminousRules.KNOCKBACK_RESISTANCE, 1e-6);
    // Apothic Attributes lets toughness resist armour pierce/shred at 2 % per point up to 60 %.
    assertTrue(4 * LuminousRules.TOUGHNESS * 0.02f <= 0.6f);
  }

  @Test
  void toolsTopRefinedObsidianButStayBelowPoweredDraconic() {
    assertTrue(LuminousRules.TOOL_USES > 4096); // Refined Obsidian tools
    assertTrue(LuminousRules.TOOL_SPEED > 12f); // Refined Obsidian efficiency
    assertTrue(LuminousRules.TOOL_SPEED < 50f); // Draconic chaotic harvest speed (with energy and AoE)
    assertEquals(14, LuminousRules.swordDamage());
    assertTrue(LuminousRules.swordDamage() > 12); // Refined Obsidian sword
    assertTrue(LuminousRules.swordDamage() < 17.5f); // Draconic chaotic sword base (2.5 x 7) before modules
    assertEquals(16, LuminousRules.axeDamage());
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
    // A spent chestplate (1599 damage) is whole again after under seven minutes of daylight.
    int seconds = (int) Math.ceil(1599.0 / LuminousRules.lightRepair(15, 1599));
    assertTrue(seconds < 7 * 60, "seconds " + seconds);
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
}
