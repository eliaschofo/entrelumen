package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AtlasLibraryLedgerTest {
  private static AtlasLibraryLedger.Profile common(int naturalMax) {
    return AtlasLibraryLedger.Profile.of("minecraft:sharpness", 10, naturalMax, false, false, true);
  }

  private static AtlasLibraryLedger.Profile profile(int weight, boolean treasure) {
    return AtlasLibraryLedger.Profile.of("test:any", weight, 5, false, treasure, true);
  }

  @Test
  void baseCostFollowsRarity() {
    assertEquals(1, AtlasLibraryLedger.base(10));
    assertEquals(2, AtlasLibraryLedger.base(5));
    assertEquals(5, AtlasLibraryLedger.base(2));
    assertEquals(10, AtlasLibraryLedger.base(1));
    assertEquals(10, AtlasLibraryLedger.base(0));
    assertEquals(1, AtlasLibraryLedger.base(50));
  }

  @Test
  void depositIsAtMostHalfOfThePriceSoRoundTripsAlwaysLose() {
    for (int weight = 1; weight <= 10; weight++)
      for (boolean treasure : new boolean[] {false, true}) {
        var profile = profile(weight, treasure);
        for (int level = 1; level <= AtlasLibraryLedger.ABSOLUTE_CAP; level++) {
          long deposit = AtlasLibraryLedger.depositValue(profile, level);
          long price = AtlasLibraryLedger.price(profile, level);
          assertTrue(deposit * 2 <= price, "deposit exceeds half at " + weight + "/" + level);
          if (treasure) assertTrue(deposit * 4 <= price, "treasure deposit exceeds a quarter");
          // Upgrading an owned book of level c to L and depositing it never beats depositing it as is.
          for (int owned = 0; owned < level; owned++) {
            long net = -AtlasLibraryLedger.upgradeCost(profile, owned, level) + deposit
                - AtlasLibraryLedger.depositValue(profile, owned);
            assertTrue(net <= 0, "upgrade then deposit gained points");
          }
        }
      }
  }

  @Test
  void upgradeChainsCostExactlyTheFullPrice() {
    var profile = common(5);
    long stepwise = 0;
    for (int level = 1; level <= 12; level++) stepwise += AtlasLibraryLedger.upgradeCost(profile, level - 1, level);
    assertEquals(AtlasLibraryLedger.price(profile, 12), stepwise);
    assertEquals(AtlasLibraryLedger.price(profile, 12), AtlasLibraryLedger.upgradeCost(profile, 0, 12));
  }

  @Test
  void sharpnessTwentyNeedsSixtyFiveThousandSharpnessFiveBooks() {
    var sharpness = common(5);
    assertEquals(8, AtlasLibraryLedger.depositValue(sharpness, 5));
    assertEquals(524_288, AtlasLibraryLedger.price(sharpness, 20));
    assertEquals(65_536, AtlasLibraryLedger.price(sharpness, 20) / AtlasLibraryLedger.depositValue(sharpness, 5));
    assertEquals(64, AtlasLibraryLedger.price(sharpness, 10) / AtlasLibraryLedger.depositValue(sharpness, 5));
    // Mending (weight 2, treasure, max I): five points per level, doubled; a Mending book returns 2.
    var mending = AtlasLibraryLedger.Profile.of("minecraft:mending", 2, 1, false, true, true);
    assertEquals(10, AtlasLibraryLedger.price(mending, 1));
    assertEquals(2, AtlasLibraryLedger.depositValue(mending, 1));
  }

  @Test
  void capsFollowEternaCeilingsAndSingleLevelEnchantments() {
    var sharpness = common(5);
    assertEquals(1, AtlasLibraryLedger.cap(sharpness, 0));
    assertEquals(12, AtlasLibraryLedger.cap(sharpness, 30));
    assertEquals(20, AtlasLibraryLedger.cap(sharpness, 50));
    assertEquals(40, AtlasLibraryLedger.cap(sharpness, 100));
    assertEquals(40, AtlasLibraryLedger.cap(sharpness, 400));
    var quickCharge = AtlasLibraryLedger.Profile.of("minecraft:quick_charge", 5, 3, false, false, true);
    assertEquals(5, AtlasLibraryLedger.cap(quickCharge, 100));
    var mending = AtlasLibraryLedger.Profile.of("minecraft:mending", 2, 1, false, true, true);
    assertEquals(1, AtlasLibraryLedger.cap(mending, 100));
  }

  @Test
  void cursesAndUnobtainableEnchantmentsAreWorthlessAndNeverOffered() {
    var curse = AtlasLibraryLedger.Profile.of("minecraft:binding_curse", 1, 1, true, true, true);
    var special = AtlasLibraryLedger.Profile.of("mod:internal", 5, 3, false, false, false);
    for (var profile : new AtlasLibraryLedger.Profile[] {curse, special}) {
      assertFalse(profile.eligible());
      assertEquals(0, AtlasLibraryLedger.depositValue(profile, 1));
      assertEquals(0, AtlasLibraryLedger.cap(profile, 100));
      assertFalse(AtlasLibraryLedger.withdraw(Long.MAX_VALUE, profile, 0, 1, 100).accepted());
    }
  }

  @Test
  void withdrawalValidatesCapPoolAndDirectionWithoutGoingNegative() {
    var sharpness = common(5);
    var ok = AtlasLibraryLedger.withdraw(100, sharpness, 0, 5, 30);
    assertTrue(ok.accepted());
    assertEquals(84, ok.pool());
    assertEquals(16, ok.cost());
    assertFalse(AtlasLibraryLedger.withdraw(100, sharpness, 0, 13, 30).accepted(), "above the Eterna cap");
    assertFalse(AtlasLibraryLedger.withdraw(15, sharpness, 0, 5, 30).accepted(), "insufficient pool");
    assertFalse(AtlasLibraryLedger.withdraw(100, sharpness, 5, 5, 30).accepted(), "no-op request");
    assertFalse(AtlasLibraryLedger.withdraw(100, sharpness, 6, 5, 30).accepted(), "downgrade request");
    assertEquals(100, AtlasLibraryLedger.withdraw(100, sharpness, 0, 13, 30).pool());
    var upgrade = AtlasLibraryLedger.withdraw(100, sharpness, 4, 6, 30);
    assertTrue(upgrade.accepted());
    assertEquals(32 - 8, upgrade.cost());
  }

  @Test
  void maxAffordableStopsAtPoolOrCap() {
    var sharpness = common(5);
    assertEquals(6, AtlasLibraryLedger.maxAffordable(32, sharpness, 0, 100));
    assertEquals(3, AtlasLibraryLedger.maxAffordable(1 << 20, sharpness, 0, 7.5f));
    assertEquals(4, AtlasLibraryLedger.maxAffordable(0, sharpness, 4, 100));
  }

  @Test
  void hugeLevelsSaturateInsteadOfOverflowing() {
    var sharpness = common(5);
    assertTrue(AtlasLibraryLedger.depositValue(sharpness, 127) > 0);
    assertEquals(Long.MAX_VALUE / 2, AtlasLibraryLedger.depositValue(sharpness, 127));
    assertEquals(Long.MAX_VALUE, AtlasLibraryLedger.price(profile(1, true), 90));
    long pool = AtlasLibraryLedger.add(Long.MAX_VALUE - 3, 10);
    assertEquals(Long.MAX_VALUE, pool);
    assertEquals(Long.MAX_VALUE, AtlasLibraryLedger.add(pool, AtlasLibraryLedger.depositValue(sharpness, 127)));
    var result = AtlasLibraryLedger.withdraw(pool, sharpness, 0, 40, 100);
    assertTrue(result.accepted());
    assertTrue(result.pool() > 0 && result.pool() < pool);
    assertEquals(0, AtlasLibraryLedger.depositValue(sharpness, 0));
    assertEquals(0, AtlasLibraryLedger.depositValue(sharpness, -3));
  }
}
