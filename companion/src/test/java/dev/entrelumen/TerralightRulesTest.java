package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The Terralight crystal's growth without a world: time, rain and stages. */
class TerralightRulesTest {
  private static final long FULL = TerralightRules.FULL_GROWTH_TICKS;

  @Test
  void theNumbersAreFourHoursEightTimesInTheRainAndFiveMinutesAfter() {
    assertEquals(288_000L, FULL);
    assertEquals(8, TerralightRules.RAIN_MULTIPLIER);
    assertEquals(6_000L, TerralightRules.AFTER_RAIN_TICKS);
    assertEquals(36_000L, FULL / TerralightRules.RAIN_MULTIPLIER, "thirty minutes of constant rain");
    assertEquals(1, TerralightRules.SHARDS_PER_CRYSTAL);
  }

  @Test
  void rainAndItsWindowCountEightTimes() {
    assertEquals(8, TerralightRules.factor(true, -1, 8, 6_000));
    assertEquals(8, TerralightRules.factor(false, 5_999, 8, 6_000));
    assertEquals(1, TerralightRules.factor(false, 6_000, 8, 6_000));
    assertEquals(1, TerralightRules.factor(false, -1, 8, 6_000), "it never rained");
  }

  @Test
  void onlyElapsedTimeCountsAndALongGapCountsDry() {
    assertEquals(20, TerralightRules.advance(0, 20, 1, FULL));
    assertEquals(160, TerralightRules.advance(0, 20, 8, FULL));
    assertEquals(0, TerralightRules.advance(0, 0, 8, FULL), "a second look in the same tick counts nothing");
    assertEquals(10, TerralightRules.advance(10, -5, 8, FULL), "time never runs backwards");
    assertEquals(1_000, TerralightRules.advance(0, 1_000, 8, FULL), "an unloaded gap counts dry");
    assertEquals(FULL, TerralightRules.advance(FULL - 5, 20, 8, FULL));
  }

  @Test
  void fourStagesTheClusterOnlyWhenFull() {
    assertEquals(-1, TerralightRules.stage(0, FULL));
    assertEquals(0, TerralightRules.stage(1, FULL));
    assertEquals(1, TerralightRules.stage(FULL / 4, FULL));
    assertEquals(2, TerralightRules.stage(FULL / 2, FULL));
    assertEquals(2, TerralightRules.stage(FULL - 1, FULL));
    assertEquals(3, TerralightRules.stage(FULL, FULL));
  }
}
