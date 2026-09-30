package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import dev.entrelumen.SourShackleRules.Cycle;
import dev.entrelumen.SourShackleRules.Outcome;
import org.junit.jupiter.api.Test;

/** The Sour Shackle's cycle and numbers (Elias, 29/9). */
class SourShackleRulesTest {
  @Test
  void theNumbersAreTheSpec() {
    assertEquals(5, SourShackleRules.MARKS);
    assertEquals(15F, SourShackleRules.burstDamage(false));
    assertEquals(45F, SourShackleRules.burstDamage(true), "300% against bosses");
    assertEquals(5.0, SourShackleRules.BURST_RADIUS);
    assertEquals(8 * 20, SourShackleRules.COOLDOWN_TICKS);
    assertEquals(64, SourShackleRules.REPEAT_SHARDS);
  }

  @Test
  void theFifthMarkBurstsAndEmptiesTheMarks() {
    var cycle = new Cycle();
    for (int i = 1; i < 5; i++) {
      assertEquals(Outcome.MARKED, cycle.hit(100 + i));
      assertEquals(i, cycle.marks());
    }
    assertEquals(Outcome.BURST, cycle.hit(110));
    assertEquals(0, cycle.marks());
    assertTrue(cycle.cooling(110));
  }

  @Test
  void theCooldownBlocksMarksForEightSeconds() {
    var cycle = new Cycle();
    for (int i = 0; i < 4; i++) cycle.hit(0);
    assertEquals(Outcome.BURST, cycle.hit(1000));
    for (long t = 1000; t < 1160; t += 7) assertEquals(Outcome.COOLING, cycle.hit(t), "tick " + t);
    assertEquals(Outcome.COOLING, cycle.hit(1159));
    assertEquals(0, cycle.marks());
    assertEquals(1, cycle.coolingTicks(1159));
    assertEquals(Outcome.MARKED, cycle.hit(1160));
    assertEquals(1, cycle.marks());
    assertEquals(0, cycle.coolingTicks(1160));
  }

  @Test
  void marksDoNotWearOff() {
    var cycle = new Cycle();
    cycle.hit(0);
    cycle.hit(1);
    assertEquals(Outcome.MARKED, cycle.hit(1_000_000));
    assertEquals(3, cycle.marks());
  }

  @Test
  void theRadiusIsInclusiveAtFive() {
    assertTrue(SourShackleRules.inBurst(0));
    assertTrue(SourShackleRules.inBurst(25.0));
    assertFalse(SourShackleRules.inBurst(25.0001));
  }
}
