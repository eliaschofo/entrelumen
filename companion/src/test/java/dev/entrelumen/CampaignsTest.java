package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CampaignsTest {
  @Test
  void founderSnapshotAndJoiningNeverMerge() {
    Campaigns d = new Campaigns();
    UUID founder = UUID.randomUUID(), guest = UUID.randomUUID(), team = UUID.randomUUID();
    d.personal(founder).act = 3;
    d.personal(founder).completed.add("signal");
    var party = d.party(team, founder);
    party.act = 4;
    assertEquals(3, d.personal(founder).act);
    assertSame(party, d.current(guest, team, founder));
    assertEquals(1, d.current(guest, null, guest).act);
    assertEquals(3, d.current(founder, null, founder).act);
    assertSame(party, d.party(team, guest));
  }

  @Test
  void deliveryIsAtomicAndIdempotent() {
    var c = new Campaigns.Campaign();
    int[] consumed = {0};
    var cost = Map.of("copper", 4);
    assertFalse(Campaigns.deliver(c, "signal", 1, cost, Map.of("copper", 3), () -> consumed[0]++));
    assertEquals(0, consumed[0]);
    assertTrue(Campaigns.deliver(c, "signal", 1, cost, Map.of("copper", 4), () -> consumed[0]++));
    assertFalse(Campaigns.deliver(c, "signal", 1, cost, Map.of("copper", 64), () -> consumed[0]++));
    assertEquals(1, consumed[0]);
  }

  @Test
  void failedConsumptionCannotRecordDelivery() {
    var c = new Campaigns.Campaign();
    assertThrows(
        IllegalStateException.class,
        () ->
            Campaigns.deliver(
                c,
                "x",
                1,
                Map.of(),
                Map.of(),
                () -> {
                  throw new IllegalStateException();
                }));
    assertTrue(c.completed.isEmpty());
  }

  @Test
  void archivedTeamsRequireExplicitRecovery() {
    Campaigns d = new Campaigns();
    UUID team = UUID.randomUUID();
    var c = d.party(team, UUID.randomUUID());
    c.completed.add("x");
    d.archive(team);
    assertFalse(Campaigns.advance(c, Set.of("x")));
    assertTrue(d.recover(team));
    assertTrue(Campaigns.advance(c, Set.of("x")));
    assertFalse(d.recover(team));
  }

  @Test
  void arkResumesWithoutRequiringContinuousPower() {
    var c = new Campaigns.Campaign();
    c.act = 6;
    var modules = Set.of("a", "b", "c", "d", "e", "f");
    c.completed.addAll(modules);
    assertTrue(Campaigns.commission(c, modules));
    var resumed = c.copy();
    for (int i = 1; i < 6; i++) assertTrue(Campaigns.commission(resumed, modules));
    assertEquals(6, resumed.arkPhase);
    assertFalse(Campaigns.commission(resumed, modules));
  }
}
