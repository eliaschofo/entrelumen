package dev.entrelumen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure rules that turn food lost to the vanilla caps (20 hunger, saturation up to the hunger
 * level) into short beneficial effects. It holds no game state: {@link SatietyOverflowEvents}
 * feeds it the food values measured just before the meal and the player's stored glut.
 *
 * <p>Units are vanilla food points (one point is half a drumstick). The formula, the default pool
 * and worked examples are in {@code docs/design/satiety-overflow.md}; the shipped datapack file
 * {@code data/entrelumen/satiety/overflow.json} must equal {@link #DEFAULTS}.
 */
public final class SatietyOverflow {
  public static final int MAX_FOOD = 20;
  /** Hard ceiling for any pool entry: level II. A datapack cannot raise it. */
  public static final int HARD_MAX_LEVEL = 2;
  /** Hard ceiling for any pool entry: ten minutes. */
  public static final int HARD_MAX_SECONDS = 600;

  /** One weighted pool entry. Levels are 1-based (1 = level I). */
  public record Entry(ResourceLocation effect, int weight, int minSeconds, int maxSeconds, int maxLevel) {
    public Entry {
      if (weight < 1) throw new IllegalArgumentException(effect + ": weight must be at least 1");
      if (minSeconds < 1 || maxSeconds < minSeconds || maxSeconds > HARD_MAX_SECONDS)
        throw new IllegalArgumentException(effect + ": seconds must satisfy 1 <= min <= max <= " + HARD_MAX_SECONDS);
      if (maxLevel < 1 || maxLevel > HARD_MAX_LEVEL)
        throw new IllegalArgumentException(effect + ": max_level must be 1 or 2");
    }
  }

  public record Settings(
      boolean enabled,
      double fullPoints,
      double minPoints,
      double extraBuffEvery,
      int maxBuffs,
      double levelTwoAt,
      double glutSoftness,
      int glutHalfLifeTicks,
      int cooldownTicks,
      boolean announce,
      List<Entry> pool) {
    public Settings {
      if (!(fullPoints > 0)) throw new IllegalArgumentException("full_points must be positive");
      if (!(minPoints >= 0)) throw new IllegalArgumentException("min_points must not be negative");
      if (!(extraBuffEvery > 0)) throw new IllegalArgumentException("extra_buff_every must be positive");
      if (maxBuffs < 1 || maxBuffs > 5) throw new IllegalArgumentException("max_buffs must be 1 to 5");
      if (!(levelTwoAt >= 0)) throw new IllegalArgumentException("level_two_at must not be negative");
      if (!(glutSoftness > 0)) throw new IllegalArgumentException("glut_softness must be positive");
      if (glutHalfLifeTicks < 1) throw new IllegalArgumentException("glut_half_life_ticks must be at least 1");
      if (cooldownTicks < 0) throw new IllegalArgumentException("cooldown_ticks must not be negative");
      var seen = new HashSet<ResourceLocation>();
      for (Entry entry : pool)
        if (!seen.add(entry.effect())) throw new IllegalArgumentException("Duplicate pool effect " + entry.effect());
      pool = List.copyOf(pool);
    }

    Settings withPool(List<Entry> replacement) {
      return new Settings(enabled, fullPoints, minPoints, extraBuffEvery, maxBuffs, levelTwoAt,
          glutSoftness, glutHalfLifeTicks, cooldownTicks, announce, replacement);
    }
  }

  private static Entry entry(String effect, int weight, int min, int max, int level) {
    return new Entry(ResourceLocation.withDefaultNamespace(effect), weight, min, max, level);
  }

  public static final Settings DEFAULTS = new Settings(true, 20, 2, 10, 3, 16, 20, 6000, 200, true,
      List.of(
          entry("regeneration", 8, 5, 20, 1),
          entry("haste", 12, 30, 180, 2),
          entry("speed", 12, 30, 180, 2),
          entry("strength", 6, 20, 90, 1),
          entry("absorption", 8, 30, 120, 2),
          entry("resistance", 6, 20, 60, 1),
          entry("water_breathing", 8, 60, 300, 1),
          entry("night_vision", 8, 60, 300, 1),
          entry("luck", 6, 60, 300, 2)));

  /** How many buffs, how far along each entry's duration range, and whether level II applies. */
  public record Plan(int count, double fraction, boolean levelTwo) {}

  private SatietyOverflow() {}

  /**
   * Food points the meal loses to the caps: nutrition above 20 plus saturation above the new
   * hunger level. Never more than the food itself provided, so negative foods yield nothing.
   */
  public static double overflow(int food, float saturation, int nutrition, float foodSaturation) {
    int newFood = Math.clamp((long) food + nutrition, 0, MAX_FOOD);
    double lostNutrition = Math.clamp((double) food + nutrition - MAX_FOOD, 0, Math.max(0, nutrition));
    double lostSaturation = Math.clamp((double) saturation + foodSaturation - newFood, 0, Math.max(0, foodSaturation));
    return lostNutrition + lostSaturation;
  }

  /** Glut left after {@code elapsed} ticks, halving every {@code halfLife} ticks. */
  public static double decay(double glut, long elapsed, int halfLife) {
    if (!(glut > 0)) return 0;
    return glut * Math.pow(0.5, Math.max(0, elapsed) / (double) halfLife);
  }

  /** Diminishing returns: the more recent surplus, the less each new point is worth. */
  public static double effective(double points, double glut, double softness) {
    return points * softness / (softness + Math.max(0, glut));
  }

  public static Optional<Plan> plan(double effective, Settings settings) {
    if (!settings.enabled() || effective < settings.minPoints() || !(effective > 0)) return Optional.empty();
    int count = (int) Math.min(settings.maxBuffs(), 1 + Math.floor(effective / settings.extraBuffEvery()));
    double fraction = Math.min(1, effective / settings.fullPoints());
    return Optional.of(new Plan(count, fraction, effective >= settings.levelTwoAt()));
  }

  /** Zero-based amplifier, as {@code MobEffectInstance} takes it. */
  public static int amplifier(Entry entry, Plan plan) {
    return plan.levelTwo() && entry.maxLevel() >= 2 ? 1 : 0;
  }

  public static int durationTicks(Entry entry, Plan plan) {
    double seconds = entry.minSeconds() + (entry.maxSeconds() - entry.minSeconds()) * plan.fraction();
    return (int) Math.round(seconds * 20);
  }

  /**
   * True when adding (amplifier, duration) improves on what the player already has. An existing
   * stronger or infinite effect is never touched; the same level is only extended.
   *
   * @param existingAmplifier -1 when the player does not have the effect
   * @param existingDuration remaining ticks, or -1 for an infinite effect
   */
  public static boolean improves(int existingAmplifier, int existingDuration, int amplifier, int duration) {
    if (existingAmplifier < 0) return true;
    if (existingDuration < 0) return false;
    if (existingAmplifier != amplifier) return existingAmplifier < amplifier;
    return existingDuration < duration;
  }

  /**
   * Weighted draw without replacement.
   *
   * @param random returns a value in {@code [0, bound)} for the given bound
   */
  public static List<Entry> pick(List<Entry> eligible, int count, IntUnaryOperator random) {
    List<Entry> remaining = new ArrayList<>(eligible);
    List<Entry> picked = new ArrayList<>();
    while (picked.size() < count && !remaining.isEmpty()) {
      int total = remaining.stream().mapToInt(Entry::weight).sum();
      int roll = random.applyAsInt(total);
      for (int i = 0; i < remaining.size(); i++) {
        roll -= remaining.get(i).weight();
        if (roll < 0) {
          picked.add(remaining.remove(i));
          break;
        }
      }
    }
    return List.copyOf(picked);
  }

  /**
   * Reads a datapack file. Omitted keys keep their defaults; an omitted {@code pool} keeps the
   * default pool. Entries whose effect is absent or unsuitable (instant or harmful) are skipped
   * with a warning, so a pool may name effects from optional mods.
   */
  public static Settings parse(JsonElement json, Predicate<ResourceLocation> usable, Consumer<String> warnings) {
    if (!json.isJsonObject()) throw new IllegalArgumentException("Satiety overflow settings must be an object");
    JsonObject o = json.getAsJsonObject();
    var d = DEFAULTS;
    List<Entry> pool = d.pool();
    if (o.has("pool")) {
      pool = new ArrayList<>();
      for (JsonElement element : o.getAsJsonArray("pool")) {
        JsonObject e = element.getAsJsonObject();
        var id = ResourceLocation.parse(e.get("effect").getAsString());
        var entry = new Entry(id, integer(e, "weight", 1), integer(e, "min_seconds", 1),
            integer(e, "max_seconds", 0), integer(e, "max_level", 1));
        if (usable.test(id)) pool.add(entry);
        else warnings.accept("Skipped satiety overflow effect " + id + ": absent, instant or harmful");
      }
    }
    var settings = new Settings(
        bool(o, "enabled", d.enabled()),
        number(o, "full_points", d.fullPoints()),
        number(o, "min_points", d.minPoints()),
        number(o, "extra_buff_every", d.extraBuffEvery()),
        integer(o, "max_buffs", d.maxBuffs()),
        number(o, "level_two_at", d.levelTwoAt()),
        number(o, "glut_softness", d.glutSoftness()),
        integer(o, "glut_half_life_ticks", d.glutHalfLifeTicks()),
        integer(o, "cooldown_ticks", d.cooldownTicks()),
        bool(o, "announce", d.announce()),
        pool);
    if (settings.enabled() && settings.pool().isEmpty())
      warnings.accept("Satiety overflow pool is empty; surplus food will only build glut");
    return settings;
  }

  private static double number(JsonObject o, String key, double fallback) {
    return o.has(key) ? o.get(key).getAsDouble() : fallback;
  }

  private static int integer(JsonObject o, String key, int fallback) {
    if (!o.has(key)) return fallback;
    double value = o.get(key).getAsDouble();
    if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE)
      throw new IllegalArgumentException(key + " must be a whole number");
    return (int) value;
  }

  private static boolean bool(JsonObject o, String key, boolean fallback) {
    return o.has(key) ? o.get(key).getAsBoolean() : fallback;
  }
}
