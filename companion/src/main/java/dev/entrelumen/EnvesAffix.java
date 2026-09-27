package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The elite affixes of the Envés, in the manner of Diablo (docs/design/dungeon-enves.md, «Afijos de
 * élite»): one on floor I, up to three on floor IV, three on a stair's champion. What each does lives
 * in {@link EnvesAffixes}; the numbers are here so the tests and the docs read the same values.
 */
public enum EnvesAffix {
  /** Veloz: moves 40% faster. */
  SWIFT("veloz"),
  /** Blindado: +8 armor, +4 toughness and 60% more knockback resistance. */
  ARMORED("blindado"),
  /** Vampírico: heals 35% of the damage it deals. */
  VAMPIRIC("vampirico"),
  /** Ardiente: immune to fire; sets everyone within 3 blocks on fire every second. */
  BURNING("ardiente"),
  /** Perforante: armor stops only half of what armor would stop of its blows. */
  PIERCING("perforante"),
  /** Espectral: every 5–8 s it fades and comes back behind its target, with a sound where it will land. */
  SPECTRAL("espectral");

  public static final double SWIFT_SPEED = 0.4;
  public static final double ARMORED_ARMOR = 8, ARMORED_TOUGHNESS = 4, ARMORED_KNOCKBACK = 0.6;
  public static final float VAMPIRIC_LEECH = 0.35f;
  public static final double BURNING_RADIUS = 3.0;
  public static final int BURNING_FIRE_TICKS = 60;
  /** The share of armor's reduction a piercing blow keeps. */
  public static final float PIERCING_ARMOR_KEPT = 0.5f;
  public static final int SPECTRAL_MIN_COOLDOWN = 100, SPECTRAL_MAX_COOLDOWN = 160, SPECTRAL_WARNING = 12;

  /** The id in data and lang ({@code entrelumen.enves.affix.<id>}). */
  public final String id;

  EnvesAffix(String id) {
    this.id = id;
  }

  public String langKey() {
    return "entrelumen.enves.affix." + id;
  }

  public static EnvesAffix byId(String id) {
    String key = id.toLowerCase(Locale.ROOT);
    for (EnvesAffix affix : values()) if (affix.id.equals(key) || affix.name().toLowerCase(Locale.ROOT).equals(key)) return affix;
    return null;
  }

  /** {@code count} distinct affixes drawn at random. */
  public static List<EnvesAffix> pick(int count, Random random) {
    List<EnvesAffix> pool = new ArrayList<>(List.of(values()));
    List<EnvesAffix> out = new ArrayList<>();
    for (int i = 0; i < count && !pool.isEmpty(); i++) out.add(pool.remove(random.nextInt(pool.size())));
    return out;
  }
}
