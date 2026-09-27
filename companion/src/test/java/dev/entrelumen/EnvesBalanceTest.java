package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesBalance.Rarity;
import dev.entrelumen.EnvesBalance.Role;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The numbers of the Envés's content: scaling, affixes, rarity and purity, and their data file. */
class EnvesBalanceTest {
  static final Path DATA = Path.of("src/main/resources/data/entrelumen");
  final EnvesBalance.Settings b = EnvesBalance.DEFAULTS;

  @Test
  void theDataFileSaysWhatTheCodeDefaultsSay() throws IOException {
    var parsed = EnvesBalance.parse(JsonParser.parseString(Files.readString(DATA.resolve("enves/balance.json"))));
    assertEquals(b, parsed, "balance.json and EnvesBalance.DEFAULTS must agree, so the docs' tables hold for both");
  }

  @Test
  void echoesGrowWithTheTierAndTheFloor() {
    for (Role role : List.of(Role.ESCORT, Role.ELITE, Role.GUARDIAN, Role.CHAMPION, Role.BOSS)) {
      double lastHealth = 0, lastDamage = 0;
      for (Tier tier : Tier.values()) {
        double health = b.health(role, null, tier, 1), damage = b.damage(role, null, tier, 1);
        assertTrue(health > lastHealth && damage > lastDamage, role + " at " + tier);
        lastHealth = health;
        lastDamage = damage;
        for (int depth = 2; depth <= EnvesLayout.FLOORS; depth++) {
          assertTrue(b.health(role, null, tier, depth) >= b.health(role, null, tier, depth - 1), role + " floor " + depth);
          assertTrue(b.damage(role, null, tier, depth) >= b.damage(role, null, tier, depth - 1), role + " floor " + depth);
        }
      }
    }
    // The documented examples: an elite at Frontier floor I, a champion at Pinnacle IV, the boss at Frontier.
    assertEquals(70, b.health(Role.ELITE, null, Tier.FRONTIER, 1));
    assertEquals(1140, b.health(Role.CHAMPION, null, Tier.PINNACLE, 4));
    assertEquals(1020, b.health(Role.BOSS, null, Tier.FRONTIER, 5));
    assertEquals(60, b.health(Role.ELITE, 60.0, Tier.FRONTIER, 1), "a table's own base wins over the role's");
    assertTrue(b.health(Role.ESCORT, null, Tier.FRONTIER, 1) < b.health(Role.ELITE, null, Tier.FRONTIER, 1));
    assertTrue(b.health(Role.ELITE, null, Tier.FRONTIER, 1) < b.health(Role.GUARDIAN, null, Tier.FRONTIER, 1));
    assertTrue(b.health(Role.GUARDIAN, null, Tier.FRONTIER, 1) < b.health(Role.CHAMPION, null, Tier.FRONTIER, 1));
  }

  @Test
  void elitesCarryOneAffixOnFloorIAndUpToThreeOnFloorIV() {
    Random random = new Random(7);
    Set<Integer> seen4 = new HashSet<>();
    for (int i = 0; i < 400; i++) {
      assertEquals(1, b.affixCount(Role.ELITE, 1, random));
      int two = b.affixCount(Role.ELITE, 2, random);
      assertTrue(two >= 1 && two <= 2);
      assertEquals(2, b.affixCount(Role.ELITE, 3, random));
      int four = b.affixCount(Role.ELITE, 4, random);
      assertTrue(four >= 2 && four <= 3);
      seen4.add(four);
      assertEquals(3, b.affixCount(Role.CHAMPION, 1 + random.nextInt(4), random), "a champion always has three");
      int guardian = b.affixCount(Role.GUARDIAN, 1 + random.nextInt(4), random);
      assertTrue(guardian >= 2 && guardian <= 3, "a guardian has one more than the floor's elites, at most three");
      assertEquals(0, b.affixCount(Role.ESCORT, 4, random));
      var affixes = EnvesAffix.pick(3, random);
      assertEquals(3, new HashSet<>(affixes).size(), "affixes never repeat");
    }
    assertEquals(Set.of(2, 3), seen4, "floor IV rolls both two and three");
    for (EnvesAffix affix : EnvesAffix.values()) assertEquals(affix, EnvesAffix.byId(affix.id));
  }

  @Test
  void lootIsRarerDeeperAndNeverAboveTheTiersTop() {
    Random random = new Random(11);
    for (Tier tier : Tier.values()) {
      Rarity top = b.tier(tier).top();
      assertEquals(top, b.rarity(tier, 1, 0, true, false, random), "the boss chest gives the tier's top");
      double shallow = 0, deep = 0, fortune = 0;
      int n = 4000;
      for (int i = 0; i < n; i++) {
        Rarity r1 = b.rarity(tier, 1, 0, false, false, random), r4 = b.rarity(tier, 4, 0, false, false, random);
        Rarity rf = b.rarity(tier, 1, 0, false, true, random), rb = b.rarity(tier, 1, 5, false, false, random);
        assertTrue(r1.ordinal() <= top.ordinal() && r4.ordinal() <= top.ordinal() && rf.ordinal() <= top.ordinal());
        assertEquals(top, rb, "bonus steps stop at the top");
        shallow += r1.ordinal();
        deep += r4.ordinal();
        fortune += rf.ordinal();
      }
      assertTrue(deep >= shallow, tier + ": floor IV is not rarer than floor I");
      assertTrue(fortune >= shallow, tier + ": Fortune does not help");
      if (tier != Tier.PINNACLE) assertTrue(deep > shallow, tier + ": floor IV must be strictly rarer");
    }
    // Frontier floor I follows Apotheosis's own Frontier weights: mostly uncommon.
    int[] counts = new int[5];
    for (int i = 0; i < 20000; i++) counts[b.rarity(Tier.FRONTIER, 1, 0, false, false, random).ordinal()]++;
    assertEquals(0.60, counts[1] / 20000.0, 0.02);
    assertEquals(0.29, counts[0] / 20000.0, 0.02);
    assertEquals(0, counts[4], "never mythic at Frontier");
  }

  @Test
  void gemsArePurerDeeperUpToPerfect() {
    assertEquals(1, b.purity(Tier.FRONTIER, 1, 0), "chipped");
    assertEquals(2, b.purity(Tier.FRONTIER, 3, 0), "flawed on floor III");
    assertEquals(5, b.purity(Tier.PINNACLE, 5, 1), "perfect from the Pinnacle boss");
    assertEquals(5, b.purity(Tier.PINNACLE, 5, 9), "never above perfect");
    assertEquals("perfect", EnvesBalance.PURITIES.get(5));
  }

  @Test
  void theFloorBonusAddsItsWholePartAndTheFractionByChance() {
    assertEquals(0, EnvesBalance.floorBonus(0.5f, 1, 0f));
    assertEquals(1, EnvesBalance.floorBonus(0.5f, 3, 0.99f));
    assertEquals(1, EnvesBalance.floorBonus(0.25f, 3, 0.2f), "half a shard: yes with a low roll");
    assertEquals(0, EnvesBalance.floorBonus(0.25f, 3, 0.7f), "and no with a high one");
    assertEquals(3, EnvesBalance.floorBonus(1f, 4, 0.5f));
  }

  @Test
  void aBadBalanceFileIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> EnvesBalance.parse(JsonParser.parseString("{\"floors\": [{}]}")));
    assertThrows(IllegalArgumentException.class, () -> EnvesBalance.parse(JsonParser.parseString(
        "{\"tiers\": {\"frontier\": {\"rarity\": [0, 0, 0, 0, 0]}}}")));
    assertThrows(IllegalArgumentException.class, () -> EnvesBalance.parse(JsonParser.parseString(
        "{\"affixes\": {\"elite\": [[1, 1], [1, 2], [2, 2], [2, 9], [2, 3]]}}")));
    assertThrows(IllegalArgumentException.class, () -> EnvesBalance.parse(JsonParser.parseString(
        "{\"boss\": {\"telegraph_ticks\": 2}}")), "a charge must be told in advance");
    assertEquals(b, EnvesBalance.parse(JsonParser.parseString("{}")), "an empty file keeps every default");
  }
}
