package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SatietyOverflowTest {
  private static final SatietyOverflow.Settings D = SatietyOverflow.DEFAULTS;
  // Vanilla and Farmer's Delight 1.3.3 values: saturation = nutrition x modifier x 2.
  private static final int BREAD_N = 5, CARROT_N = 6, ROAST_N = 14;
  private static final float BREAD_S = 6.0f, CARROT_S = 14.4f, ROAST_S = 21.0f;

  private static SatietyOverflow.Entry entry(String id) {
    return D.pool().stream().filter(e -> e.effect().getPath().equals(id)).findFirst().orElseThrow();
  }

  private static SatietyOverflow.Plan plan(double effective) {
    return SatietyOverflow.plan(effective, D).orElseThrow();
  }

  @Test
  void overflowCountsOnlyWhatTheCapsDiscard() {
    // Hungry: nothing is lost.
    assertEquals(0, SatietyOverflow.overflow(10, 5, BREAD_N, BREAD_S), 1e-6);
    // Bread at 19/20 with 15 saturation: 4 hunger and 1 saturation over the caps.
    assertEquals(5, SatietyOverflow.overflow(19, 15, BREAD_N, BREAD_S), 1e-5);
    // Full and saturated: the whole bread is surplus.
    assertEquals(11, SatietyOverflow.overflow(20, 20, BREAD_N, BREAD_S), 1e-5);
    // Farmer's Delight roast chicken at 14/20, saturation 8: 8 + 9.
    assertEquals(17, SatietyOverflow.overflow(14, 8, ROAST_N, ROAST_S), 1e-5);
    // Golden carrot at 16/20, saturation 10: 2 + 4.4.
    assertEquals(6.4, SatietyOverflow.overflow(16, 10, CARROT_N, CARROT_S), 1e-5);
    // Saturation is capped by the new hunger level, not by 20.
    assertEquals(3, SatietyOverflow.overflow(2, 2, 3, 6), 1e-6);
    // Never more than the food gave; foods with negative values yield nothing.
    assertEquals(0, SatietyOverflow.overflow(20, 20, -4, 0), 1e-6);
    assertEquals(0, SatietyOverflow.overflow(20, 20, 0, -2), 1e-6);
  }

  @Test
  void documentedExamplesMatchTheFormula() {
    // Bread, nearly full bar: one level-I buff at 25 % of its range (speed 67.5 s).
    var bread = plan(SatietyOverflow.overflow(19, 15, BREAD_N, BREAD_S));
    assertEquals(new SatietyOverflow.Plan(1, 0.25, false), round(bread));
    assertEquals(1350, SatietyOverflow.durationTicks(entry("speed"), bread));
    assertEquals(0, SatietyOverflow.amplifier(entry("speed"), bread));
    // Roast chicken: two buffs at 85 %; level II where the entry allows it.
    var roast = plan(SatietyOverflow.overflow(14, 8, ROAST_N, ROAST_S));
    assertEquals(new SatietyOverflow.Plan(2, 0.85, true), round(roast));
    assertEquals(1, SatietyOverflow.amplifier(entry("haste"), roast));
    assertEquals(0, SatietyOverflow.amplifier(entry("strength"), roast));
    assertEquals(3150, SatietyOverflow.durationTicks(entry("haste"), roast));
    assertEquals(355, SatietyOverflow.durationTicks(entry("regeneration"), roast));
    // Golden carrot: one level-I buff at 32 %.
    var carrot = plan(SatietyOverflow.overflow(16, 10, CARROT_N, CARROT_S));
    assertEquals(new SatietyOverflow.Plan(1, 0.32, false), round(carrot));
    // Below the floor nothing happens: a cookie at 19/20 and 19 saturation loses 1 point.
    assertTrue(SatietyOverflow.plan(SatietyOverflow.overflow(19, 19, 2, 0.4f), D).isEmpty());
  }

  private static SatietyOverflow.Plan round(SatietyOverflow.Plan plan) {
    return new SatietyOverflow.Plan(plan.count(), Math.round(plan.fraction() * 1000) / 1000.0, plan.levelTwo());
  }

  @Test
  void capsHoldForAnySurplus() {
    for (double points = 0; points <= 60; points += 0.5) {
      var plan = SatietyOverflow.plan(points, D);
      if (points < D.minPoints()) {
        assertTrue(plan.isEmpty());
        continue;
      }
      var p = plan.orElseThrow();
      assertTrue(p.count() >= 1 && p.count() <= 3, "count " + p.count());
      assertTrue(p.fraction() > 0 && p.fraction() <= 1);
      for (var entry : D.pool()) {
        int amplifier = SatietyOverflow.amplifier(entry, p);
        assertTrue(amplifier <= 1 && amplifier + 1 <= entry.maxLevel(), "level above the cap");
        assertTrue(SatietyOverflow.durationTicks(entry, p) <= entry.maxSeconds() * 20);
        assertTrue(SatietyOverflow.durationTicks(entry, p) >= entry.minSeconds() * 20);
      }
    }
    // Level II never goes to an entry capped at I, whatever the surplus.
    var huge = plan(60);
    for (var entry : D.pool())
      assertEquals(entry.maxLevel() == 2 ? 1 : 0, SatietyOverflow.amplifier(entry, huge));
  }

  @Test
  void glutMakesSpammingCheapFoodWorthLittle() {
    // Five honey bottles in ten seconds at a full bar: 7.2 points each.
    double honey = SatietyOverflow.overflow(20, 20, 6, 1.2f);
    assertEquals(7.2, honey, 1e-5);
    double glut = 0;
    double first = SatietyOverflow.effective(honey, glut, D.glutSoftness());
    assertEquals(honey, first, 1e-9);
    for (int i = 0; i < 5; i++) glut = SatietyOverflow.decay(glut, 40, D.glutHalfLifeTicks()) + honey;
    double afterCooldown = SatietyOverflow.effective(honey,
        SatietyOverflow.decay(glut, 200, D.glutHalfLifeTicks()), D.glutSoftness());
    assertTrue(afterCooldown < first * 0.4, "spam kept " + afterCooldown);
    // Glut halves every five minutes and never goes negative.
    assertEquals(18, SatietyOverflow.decay(36, 6000, 6000), 1e-9);
    assertEquals(36, SatietyOverflow.decay(36, -50, 6000), 1e-9);
    assertEquals(0, SatietyOverflow.decay(-3, 10, 6000), 1e-9);
    // Effective value is monotonic: more glut never pays more.
    double previous = Double.MAX_VALUE;
    for (int g = 0; g <= 200; g += 5) {
      double value = SatietyOverflow.effective(10, g, D.glutSoftness());
      assertTrue(value <= previous);
      previous = value;
    }
  }

  @Test
  void strongerOrInfiniteEffectsAreNeverReplaced() {
    assertTrue(SatietyOverflow.improves(-1, 0, 0, 100), "absent effect");
    assertFalse(SatietyOverflow.improves(1, 20, 0, 6000), "stronger effect replaced by a weaker one");
    assertFalse(SatietyOverflow.improves(0, -1, 0, 6000), "infinite effect touched");
    assertFalse(SatietyOverflow.improves(1, -1, 1, 6000), "infinite effect touched");
    assertFalse(SatietyOverflow.improves(0, 600, 0, 600), "equal effect re-applied");
    assertTrue(SatietyOverflow.improves(0, 100, 0, 600), "same level not extended");
    assertTrue(SatietyOverflow.improves(0, 6000, 1, 600), "weaker level not upgraded");
  }

  @Test
  void weightedPickNeverRepeatsAndRespectsCount() {
    var random = new Random(42);
    List<SatietyOverflow.Entry> pool = D.pool();
    int[] hits = new int[pool.size()];
    for (int run = 0; run < 20000; run++) {
      var picked = SatietyOverflow.pick(pool, 3, random::nextInt);
      assertEquals(3, picked.size());
      assertEquals(3, picked.stream().distinct().count());
      hits[pool.indexOf(picked.getFirst())]++;
    }
    // First draws follow the weights (12 for haste, 6 for strength) within a loose margin.
    double ratio = hits[pool.indexOf(entry("haste"))] / (double) hits[pool.indexOf(entry("strength"))];
    assertTrue(ratio > 1.7 && ratio < 2.3, "weight ratio " + ratio);
    assertEquals(2, SatietyOverflow.pick(pool.subList(0, 2), 3, random::nextInt).size());
    assertTrue(SatietyOverflow.pick(List.of(), 3, random::nextInt).isEmpty());
  }

  @Test
  void shippedDatapackFileEqualsTheDefaults() throws Exception {
    try (var reader = new InputStreamReader(
        getClass().getResourceAsStream("/data/entrelumen/satiety/overflow.json"), StandardCharsets.UTF_8)) {
      List<String> warnings = new ArrayList<>();
      assertEquals(D, SatietyOverflow.parse(JsonParser.parseReader(reader), id -> true, warnings::add));
      assertTrue(warnings.isEmpty());
    }
  }

  @Test
  void datapackOverridesAreValidatedAndHardCapped() {
    List<String> warnings = new ArrayList<>();
    var partial = SatietyOverflow.parse(JsonParser.parseString("{\"cooldown_ticks\": 400}"), id -> true, warnings::add);
    assertEquals(400, partial.cooldownTicks());
    assertEquals(D.pool(), partial.pool());
    var custom = SatietyOverflow.parse(JsonParser.parseString("""
        {"pool": [
          {"effect": "minecraft:speed", "weight": 3, "min_seconds": 10, "max_seconds": 20, "max_level": 2},
          {"effect": "othermod:glow", "weight": 1, "min_seconds": 10, "max_seconds": 20}
        ]}"""), id -> id.getNamespace().equals("minecraft"), warnings::add);
    assertEquals(List.of(new SatietyOverflow.Entry(ResourceLocation.parse("minecraft:speed"), 3, 10, 20, 2)), custom.pool());
    assertEquals(1, warnings.size(), "missing effect was not reported");
    // Level III, overlong durations, duplicates and nonsense numbers are refused.
    for (String bad : List.of(
        "{\"pool\": [{\"effect\": \"minecraft:speed\", \"min_seconds\": 1, \"max_seconds\": 5, \"max_level\": 3}]}",
        "{\"pool\": [{\"effect\": \"minecraft:speed\", \"min_seconds\": 1, \"max_seconds\": 601}]}",
        "{\"pool\": [{\"effect\": \"minecraft:speed\", \"min_seconds\": 9, \"max_seconds\": 5}]}",
        "{\"pool\": [{\"effect\": \"minecraft:speed\", \"max_seconds\": 5}, {\"effect\": \"minecraft:speed\", \"max_seconds\": 6}]}",
        "{\"pool\": [{\"effect\": \"minecraft:speed\", \"max_seconds\": 5, \"weight\": 0}]}",
        "{\"full_points\": 0}",
        "{\"max_buffs\": 9}",
        "{\"cooldown_ticks\": 2.5}",
        "[]"))
      assertThrows(RuntimeException.class, () -> SatietyOverflow.parse(JsonParser.parseString(bad), id -> true, w -> {}), bad);
    var disabled = SatietyOverflow.parse(JsonParser.parseString("{\"enabled\": false}"), id -> true, w -> {});
    assertTrue(SatietyOverflow.plan(30, disabled).isEmpty());
  }
}
