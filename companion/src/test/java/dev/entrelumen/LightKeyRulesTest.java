package dev.entrelumen;

import static dev.entrelumen.LightKeyRules.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LightKeyRulesTest {
  private static final UUID OWNER = UUID.randomUUID(), STRANGER = UUID.randomUUID();

  @Test
  void theWholeKeyCrossesOnceForATeamThatActivatedTheArk() {
    assertEquals(CROSS_AND_BREAK, LightKeyRules.decide(false, null, OWNER, false, true, true));
    assertEquals(LOCKED, LightKeyRules.decide(false, null, OWNER, false, false, true));
    assertEquals(ALREADY_INSIDE, LightKeyRules.decide(false, null, OWNER, true, true, true));
    assertEquals(CITY_FORMING, LightKeyRules.decide(false, null, OWNER, false, true, false));
    assertTrue(CROSS_AND_BREAK.travels);
  }

  @Test
  void theBrokenKeyCarriesOnlyItsOwnerBothWaysAlways() {
    assertEquals(TO_SOLSTICIO, LightKeyRules.decide(true, OWNER, OWNER, false, false, true),
        "no team condition for the return key");
    assertEquals(HOME, LightKeyRules.decide(true, OWNER, OWNER, true, false, true));
    assertEquals(HOME, LightKeyRules.decide(true, OWNER, OWNER, true, false, false),
        "going home never waits for the city");
    assertEquals(CITY_FORMING, LightKeyRules.decide(true, OWNER, OWNER, false, true, false));
    assertEquals(NOT_OWNER, LightKeyRules.decide(true, OWNER, STRANGER, false, true, true));
    assertEquals(NOT_OWNER, LightKeyRules.decide(true, OWNER, STRANGER, true, true, true));
    assertEquals(NOT_OWNER, LightKeyRules.decide(true, null, OWNER, false, true, true), "unbound keys carry no one");
    assertFalse(NOT_OWNER.travels);
  }

  @Test
  void theChannelIsLongEnoughToCancel() {
    assertTrue(LightKeyRules.CHANNEL_TICKS >= 20);
  }
}
