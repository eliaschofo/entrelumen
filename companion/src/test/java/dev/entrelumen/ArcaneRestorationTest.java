package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ArcaneRestorationTest {
  @Test
  void vanillaPriorWorkCountsAnvilOperations() {
    int cost = 0;
    for (int operations = 0; operations <= 31; operations++) {
      assertEquals(operations, ArcaneRestoration.operations(cost), "cost " + cost);
      cost = cost * 2 + 1; // AnvilMenu.calculateIncreasedRepairCost
    }
  }

  @Test
  void nonVanillaCostsRoundUpAndNothingIsFree() {
    assertEquals(0, ArcaneRestoration.operations(0));
    assertEquals(0, ArcaneRestoration.operations(-5));
    assertEquals(2, ArcaneRestoration.operations(2));
    assertEquals(3, ArcaneRestoration.operations(4));
    assertEquals(31, ArcaneRestoration.operations(Integer.MAX_VALUE));
    assertEquals(25, ArcaneRestoration.levels(ArcaneRestoration.operations(31)));
    assertEquals(155, ArcaneRestoration.levels(31));
  }
}
