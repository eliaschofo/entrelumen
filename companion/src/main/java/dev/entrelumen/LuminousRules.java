package dev.entrelumen;

import java.util.List;

/**
 * Pure numbers and rules of the luminous content: the six Luminosities, the Luminous Ingot, the
 * armour material and the tool tier. No Minecraft types, so the stat table, the "dims instead of
 * breaking" rule, light repair and the set bonus are unit-tested directly; {@link Luminous} reads
 * everything from here.
 *
 * <p>The gear never breaks: damage stops one point short of the maximum and the piece goes dark
 * (no armour, no mining speed, no attack bonus) until light or a Luminous Ingot repairs it, the way
 * an elytra stops gliding at its last point. Light at the wearer's eyes repairs every luminous piece
 * the player carries by {@code brightness - 11} points per second: nothing below 12, four in full
 * daylight. Night outdoors (sky light darkened) does not repair.
 *
 * <p>Elias's direction of 24 September 2026 makes the luminous gear absurdly strong, far above every
 * other set of the pack, with creative-style flight for the full set. The Silent Gear material made
 * from the same ingot ({@code data/entrelumen/silentgear_materials/luminous.json}) matches these
 * numbers, and either set, or a mix of both, earns the same set bonus.
 */
public final class LuminousRules {
  private LuminousRules() {}

  /** One Luminosity per Ark discipline, in the story bible's colours (name colour, RGB). */
  public enum Discipline {
    ENGINEERING("engineering", 0xE07B39),
    ARCANE("arcane", 0xA66BE8),
    NATURE("nature", 0x6CC66C),
    EXPLORATION("exploration", 0x7CC8F0),
    LOGISTICS("logistics", 0x3CC7B4),
    HABITATION("habitation", 0xF2B84B);

    public final String id;
    public final int color;

    Discipline(String id, int color) {
      this.id = id;
      this.color = color;
    }

    public String item() {
      return "luminosity_" + id;
    }
  }

  /** Cream-white gold of the ingot and of every luminous piece's name. */
  public static final int LUMINOUS_COLOR = 0xF3E3B0;

  // ---- armour material ----------------------------------------------------------------------
  /** Armour points per piece: helmet, chestplate, leggings, boots (60 for the set). */
  public static final int HELMET_ARMOR = 12, CHESTPLATE_ARMOR = 22, LEGGINGS_ARMOR = 16, BOOTS_ARMOR = 10;
  /** Toughness per piece (60 for the set). */
  public static final float TOUGHNESS = 15f;
  /** Knockback resistance per piece (1.0, full immunity, for the set). */
  public static final float KNOCKBACK_RESISTANCE = 0.25f;
  /** Vanilla durability factor: helmet 2750, chestplate 4000, leggings 3750, boots 3250. */
  public static final int ARMOR_DURABILITY_FACTOR = 250;
  public static final int ENCHANTABILITY = 60;

  // ---- tool tier ------------------------------------------------------------------------------
  public static final int TOOL_USES = 32768;
  public static final float TOOL_SPEED = 60f;
  /** Tier attack bonus; the sword adds 3 and the player 1, for 30 damage. */
  public static final float TOOL_ATTACK_BONUS = 26f;
  /** Per-tool attack modifier and attack-speed modifier, as vanilla netherite uses them. */
  public static final float SWORD_DAMAGE = 3f, SWORD_SPEED = -2.4f;
  public static final float AXE_DAMAGE = 5f, AXE_SPEED = -3.0f;
  public static final float PICKAXE_DAMAGE = 1f, PICKAXE_SPEED = -2.8f;
  public static final float SHOVEL_DAMAGE = 1.5f, SHOVEL_SPEED = -3.0f;
  /** The hoe cancels the tier bonus, as every vanilla hoe does: 1 damage, 4 swings per second. */
  public static final float HOE_DAMAGE = -TOOL_ATTACK_BONUS, HOE_SPEED = 0f;
  /** Extra damage of the sword against undead, as a fraction of the hit. */
  public static final float SWORD_UNDEAD_BONUS = 0.25f;
  /** Glowing applied by a sword hit, in ticks. */
  public static final int SWORD_REVEAL_TICKS = 100;

  // ---- light and set bonus -------------------------------------------------------------------
  /** How often (ticks) light repair and the set bonus are evaluated for each player. */
  public static final int INTERVAL_TICKS = 20;
  /** Lowest raw brightness (sky darkened by time, or block light) that repairs. */
  public static final int REPAIR_MIN_BRIGHTNESS = 12;
  /** Night vision duration refreshed every interval; stays above the 200-tick flicker window. */
  public static final int NIGHT_VISION_TICKS = 260;
  /** Slow falling granted when the set stops flying in mid-air, so losing it never kills. */
  public static final int FLIGHT_LANDING_TICKS = 160;

  public static int setArmor() {
    return HELMET_ARMOR + CHESTPLATE_ARMOR + LEGGINGS_ARMOR + BOOTS_ARMOR;
  }

  public static List<Integer> armorByPiece() {
    return List.of(HELMET_ARMOR, CHESTPLATE_ARMOR, LEGGINGS_ARMOR, BOOTS_ARMOR);
  }

  /**
   * Damage a luminous stack may actually take: never enough to reach {@code max}, so the stack is
   * never destroyed. Zero once it rests on its last point.
   */
  public static int allowedDamage(int current, int max, int amount) {
    if (amount <= 0 || max <= 1) return amount;
    return Math.max(0, Math.min(amount, max - 1 - current));
  }

  /** A piece on its last point gives no protection, speed or attack until repaired. */
  public static boolean dimmed(int damage, int max) {
    return max > 1 && damage >= max - 1;
  }

  /** Durability restored per interval at the given raw brightness, capped by the damage left. */
  public static int lightRepair(int brightness, int damage) {
    if (damage <= 0 || brightness < REPAIR_MIN_BRIGHTNESS) return 0;
    return Math.min(damage, Math.min(brightness, 15) - (REPAIR_MIN_BRIGHTNESS - 1));
  }

  /** The set bonus needs all four pieces worn and none of them dimmed. */
  public static boolean fullSet(boolean[] worn, boolean[] dimmed) {
    if (worn.length != 4 || dimmed.length != 4) return false;
    for (int i = 0; i < 4; i++) if (!worn[i] || dimmed[i]) return false;
    return true;
  }

  /** Remaining night vision never enters the vanilla flicker window between two refreshes. */
  public static boolean nightVisionSteady() {
    return NIGHT_VISION_TICKS - INTERVAL_TICKS > 200;
  }

  public static int swordDamage() {
    return Math.round(1 + TOOL_ATTACK_BONUS + SWORD_DAMAGE);
  }

  public static int axeDamage() {
    return Math.round(1 + TOOL_ATTACK_BONUS + AXE_DAMAGE);
  }
}
