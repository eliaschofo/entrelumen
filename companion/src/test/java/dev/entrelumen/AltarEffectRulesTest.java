package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

class AltarEffectRulesTest {
  private static final int[][] SQUARE_SYMMETRIES = {{1, 0, 0, 1}, {-1, 0, 0, 1}, {1, 0, 0, -1}, {-1, 0, 0, -1},
      {0, 1, 1, 0}, {0, -1, 1, 0}, {0, 1, -1, 0}, {0, -1, -1, 0}};

  @Test
  void everyAreaIsACentredBoxSymmetricUnderTheSquaresSymmetriesAndAVerticalMirror() {
    int[][] areas = {{AltarEffectRules.PEACE_RADIUS, AltarEffectRules.PEACE_HALF_HEIGHT},
        {AltarEffectRules.GROWTH_RADIUS, AltarEffectRules.GROWTH_HALF_HEIGHT},
        {AltarEffectRules.TIME_RADIUS, AltarEffectRules.TIME_HALF_HEIGHT}, {6, 3}, {0, 0}};
    for (int[] area : areas) {
      int radius = area[0], half = area[1];
      long inside = 0;
      boolean every = half <= 32;
      java.util.TreeSet<Integer> layers = new java.util.TreeSet<>();
      for (int dy = -half - 1; dy <= half + 1; dy++)
        if (every || Math.abs(dy) >= half - 1 || dy % 9 == 0) layers.add(dy);
      for (int dx = -radius - 1; dx <= radius + 1; dx++)
        for (int dz = -radius - 1; dz <= radius + 1; dz++)
          for (int dy : layers) {
            boolean in = AltarEffectRules.inBox(dx, dy, dz, radius, half);
            if (in) inside++;
            assertEquals(Math.abs(dx) <= radius && Math.abs(dz) <= radius && Math.abs(dy) <= half, in);
            for (int[] m : SQUARE_SYMMETRIES) {
              int x = m[0] * dx + m[1] * dz, z = m[2] * dx + m[3] * dz;
              assertEquals(in, AltarEffectRules.inBox(x, dy, z, radius, half), "not symmetric");
              assertEquals(in, AltarEffectRules.inBox(x, -dy, z, radius, half), "not mirrored vertically");
            }
          }
      if (every) assertEquals(AltarEffectRules.volume(radius, half), inside);
    }
    assertEquals(129L * 129 * 129, AltarEffectRules.volume(AltarEffectRules.PEACE_RADIUS, AltarEffectRules.PEACE_HALF_HEIGHT));
    assertEquals(33L * 33 * 9, AltarEffectRules.volume(AltarEffectRules.GROWTH_RADIUS, AltarEffectRules.GROWTH_HALF_HEIGHT));
    assertEquals(33L * 33 * 33, AltarEffectRules.volume(AltarEffectRules.TIME_RADIUS, AltarEffectRules.TIME_HALF_HEIGHT));
  }

  @Test
  void theRegistryAnswersBoxesInConstantTimeAndCountsActiveAltarsPerType() {
    var index = new AltarRegistry.Index();
    var peace = new BlockPos(-300, 70, 45);
    var time = new BlockPos(12, -20, 3);
    index.put(new AltarRegistry.Entry(AltarType.PEACE, peace, 64, 64));
    index.put(new AltarRegistry.Entry(AltarType.TIME, time, 16, 16));
    assertEquals(1, index.count(AltarType.PEACE));
    assertEquals(0, index.count(AltarType.GROWTH));
    for (int sx : new int[] {-1, 1})
      for (int sy : new int[] {-1, 1})
        for (int sz : new int[] {-1, 1}) {
          assertTrue(index.anyCovering(AltarType.PEACE, peace.getX() + 64 * sx, peace.getY() + 64 * sy, peace.getZ() + 64 * sz));
          assertFalse(index.anyCovering(AltarType.PEACE, peace.getX() + 65 * sx, peace.getY(), peace.getZ()));
          assertFalse(index.anyCovering(AltarType.PEACE, peace.getX(), peace.getY() + 65 * sy, peace.getZ()));
          assertFalse(index.anyCovering(AltarType.PEACE, peace.getX(), peace.getY(), peace.getZ() + 65 * sz));
          assertTrue(index.anyCovering(AltarType.TIME, time.getX() + 16 * sx, time.getY() + 16 * sy, time.getZ() + 16 * sz));
          assertFalse(index.anyCovering(AltarType.TIME, time.getX(), time.getY() + 17 * sy, time.getZ()));
        }
    assertFalse(index.anyCovering(AltarType.TIME, peace.getX(), peace.getY(), peace.getZ()), "types stay apart");
    // The constant-time answer agrees with a brute-force membership test everywhere near both.
    Random random = new Random(20260924);
    for (int i = 0; i < 20_000; i++) {
      BlockPos probe = (i % 2 == 0 ? peace : time).offset(random.nextInt(161) - 80, random.nextInt(161) - 80,
          random.nextInt(161) - 80);
      assertEquals(Math.abs(probe.getX() - peace.getX()) <= 64 && Math.abs(probe.getY() - peace.getY()) <= 64
          && Math.abs(probe.getZ() - peace.getZ()) <= 64, index.anyCovering(AltarType.PEACE, probe.getX(),
          probe.getY(), probe.getZ()));
      assertEquals(!index.covering(AltarType.TIME, probe).isEmpty(),
          index.anyCovering(AltarType.TIME, probe.getX(), probe.getY(), probe.getZ()));
    }
    index.put(new AltarRegistry.Entry(AltarType.PEACE, peace, 8, 8));
    assertEquals(1, index.count(AltarType.PEACE), "re-registering replaces");
    assertFalse(index.anyCovering(AltarType.PEACE, peace.getX() + 9, peace.getY(), peace.getZ()));
    index.remove(peace);
    index.remove(peace);
    assertEquals(0, index.count(AltarType.PEACE));
    assertEquals(1, index.count(AltarType.TIME));
    var column = new AltarRegistry.Entry(AltarType.RENEWAL, BlockPos.ZERO, 4);
    assertTrue(column.covers(4, -2000, -4) && column.covers(0, 2000, 0), "the default area is the whole column");
  }

  @Test
  void peaceRefusesOnlyNaturalHostileSpawnsInsideAnActiveAltar() {
    for (MobSpawnType type : MobSpawnType.values()) {
      assertEquals(type == MobSpawnType.NATURAL, AltarEffectRules.peaceBlocks(type, true, false, true), type.name());
      assertFalse(AltarEffectRules.peaceBlocks(type, false, false, true), "passive mobs: " + type);
      assertFalse(AltarEffectRules.peaceBlocks(type, true, true, true), "bosses: " + type);
      assertFalse(AltarEffectRules.peaceBlocks(type, true, false, false), "outside: " + type);
    }
  }

  @Test
  void growthSamplesAFixedBudgetForAboutTwentyTimesVanillaGrowth() {
    int samples = AltarEffectRules.samplesPerTick(AltarEffectRules.GROWTH_RADIUS, AltarEffectRules.GROWTH_HALF_HEIGHT);
    long volume = AltarEffectRules.volume(AltarEffectRules.GROWTH_RADIUS, AltarEffectRules.GROWTH_HALF_HEIGHT);
    assertEquals(137, samples, "33×33×9 field");
    double speedup = AltarEffectRules.speedup(samples, volume);
    assertTrue(speedup >= 20 && speedup < 20.2, "speedup " + speedup);
    for (int radius = 0; radius <= 24; radius++)
      for (int half = 0; half <= 6; half++) {
        int n = AltarEffectRules.samplesPerTick(radius, half);
        assertTrue(n >= 1 && n <= AltarEffectRules.GROWTH_MAX_SAMPLES);
        if (n < AltarEffectRules.GROWTH_MAX_SAMPLES)
          assertTrue(AltarEffectRules.speedup(n, AltarEffectRules.volume(radius, half)) >= 20, radius + "," + half);
      }
    assertEquals(AltarEffectRules.GROWTH_MAX_SAMPLES, AltarEffectRules.samplesPerTick(64, 64), "huge areas are capped");
    assertEquals(2, AltarEffectRules.GROWTH_HARVEST_MULTIPLIER);
  }

  @Test
  void bonusesMultiplyOnlyPlainStackableItemsAndSplitIntoStacks() {
    assertTrue(AltarEffectRules.bonusEligible(64, true, false));
    assertFalse(AltarEffectRules.bonusEligible(1, true, false), "unique items");
    assertFalse(AltarEffectRules.bonusEligible(64, false, false), "named, enchanted or changed items");
    assertFalse(AltarEffectRules.bonusEligible(64, true, true), "seeds and tagged items");
    assertEquals(List.of(10), AltarEffectRules.multiplied(5, 64, 2, true));
    assertEquals(List.of(64, 56), AltarEffectRules.multiplied(40, 64, 3, true));
    assertEquals(List.of(16, 16, 4), AltarEffectRules.multiplied(12, 16, 3, true));
    assertEquals(List.of(3), AltarEffectRules.multiplied(3, 64, 2, false), "an ineligible stack keeps its count");
    assertEquals(List.of(1), AltarEffectRules.multiplied(1, 1, 3, false));
    assertEquals(List.of(), AltarEffectRules.multiplied(0, 64, 3, true));
    // Wheat: one wheat doubles, its seeds keep their roll.
    int wheat = AltarEffectRules.multiplied(1, 64, AltarEffectRules.GROWTH_HARVEST_MULTIPLIER,
        AltarEffectRules.bonusEligible(64, true, false)).getFirst();
    int seeds = AltarEffectRules.multiplied(3, 64, AltarEffectRules.GROWTH_HARVEST_MULTIPLIER,
        AltarEffectRules.bonusEligible(64, true, true)).getFirst();
    assertEquals(2, wheat);
    assertEquals(3, seeds);
    assertEquals(3, AltarEffectRules.TIME_LOOT_MULTIPLIER);
    assertEquals(3, AltarEffectRules.TIME_XP_MULTIPLIER);
  }

  /** One vanilla arrow tick: move, drag, gravity. */
  private static void arrowTick(double[] pos, double[] v, double drag, double gravity) {
    for (int a = 0; a < 3; a++) pos[a] += v[a];
    for (int a = 0; a < 3; a++) v[a] *= drag;
    v[1] -= gravity;
  }

  @Test
  void slowedProjectilesFollowTheSamePathAtAThirdOfTheSpeedWithoutBraking() {
    double gravity = 0.05, drag = 0.99;
    int ticks = 40;
    double[] normalPos = new double[3], normal = {1.2, 0.4, -0.5};
    double[] slowPos = new double[3], slow = {1.2 / 3, 0.4 / 3, -0.5 / 3};
    double[] naivePos = new double[3], naive = {1.2 / 3, 0.4 / 3, -0.5 / 3};
    for (int i = 0; i < ticks; i++) arrowTick(normalPos, normal, drag, gravity);
    for (int i = 0; i < 3 * ticks; i++) {
      double[] pre = slow.clone();
      arrowTick(slowPos, slow, drag, gravity);
      slow = AltarEffectRules.slowStep(pre, slow, gravity);
      // The naive alternative: a third of the change and a third of gravity per tick.
      double[] naivePre = naive.clone();
      arrowTick(naivePos, naive, drag, gravity);
      for (int a = 0; a < 3; a++) naive[a] = naivePre[a] + (naive[a] - naivePre[a] + (a == 1 ? gravity : 0)) / 3;
      naive[1] -= gravity / 3;
    }
    double path = Math.hypot(normalPos[0], normalPos[2]);
    assertEquals(normalPos[0], slowPos[0], 0.01 * path, "horizontal x");
    assertEquals(normalPos[2], slowPos[2], 0.01 * path, "horizontal z");
    // Finer time steps drop a little more (g·t/3 over the flight): the same path within 3 %.
    assertEquals(normalPos[1], slowPos[1], gravity * ticks / 3 + 0.01 * path, "height");
    assertTrue(Math.abs(naivePos[1] - normalPos[1]) > 10 * Math.abs(slowPos[1] - normalPos[1]),
        "a third of gravity per tick would drop like lead: " + naivePos[1] + " vs " + normalPos[1]);
    for (int a = 0; a < 3; a++)
      assertEquals(normal[a] / 3, slow[a], 0.02 * Math.abs(normal[a]) + gravity / 9, "velocity is a third, axis " + a);
  }

  @Test
  void slowedFireballsReachAThirdOfTheirTerminalSpeed() {
    double inertia = 0.95, power = 0.1;
    double normal = 0, slow = 0;
    for (int i = 0; i < 2000; i++) {
      normal = (normal + power) * inertia;
      double post = (slow + power * AltarEffectRules.SLOW) * inertia;
      slow = AltarEffectRules.slowStep(new double[] {slow, 0, 0}, new double[] {post, 0, 0}, 0)[0];
    }
    assertEquals(power * inertia / (1 - inertia), normal, 1e-6);
    assertEquals(normal / 3, slow, 1e-6);
    assertEquals(-2.0 / 3, AltarEffectRules.SLOW_MODIFIER, 1e-12, "movement and attack keep a third");
  }

  @Test
  void reposeMendsOnePointPerItemEveryFiveSecondsOfPaidTime() {
    assertEquals(100, AltarEffectRules.REPAIR_INTERVAL);
    assertEquals(4, AltarEffectRules.REPOSE_SLOTS);
    assertEquals(1200, AltarType.REPOSE.ticksPerFuel(), "one bottle: one minute");
    assertEquals(12, AltarEffectRules.pointsPerFuel(AltarType.REPOSE.ticksPerFuel(), 1));
    assertEquals(48, AltarEffectRules.pointsPerFuel(AltarType.REPOSE.ticksPerFuel(), 4));
    // Diamond pickaxe from nothing: 1,561 points, about 2 h 10 min and 131 bottles alone.
    assertEquals(156_100, AltarEffectRules.ticksToRepair(1561));
    assertEquals(131, Math.ceilDiv(AltarEffectRules.ticksToRepair(1561), AltarType.REPOSE.ticksPerFuel()));
  }

  @Test
  void fuelsAndDurationsAreTheDocumentedOnes() {
    assertEquals(12_000, AltarType.PEACE.ticksPerFuel(), "candle: ten minutes");
    assertEquals(3_600, AltarType.GROWTH.ticksPerFuel(), "bone block: three minutes");
    assertEquals(2_400, AltarType.TIME.ticksPerFuel(), "amethyst shard: two minutes");
    assertEquals("entrelumen:peace_altar_fuels", AltarType.PEACE.fuel().location().toString());
    assertEquals("entrelumen:growth_altar_fuels", AltarType.GROWTH.fuel().location().toString());
    assertEquals("entrelumen:time_altar_fuels", AltarType.TIME.fuel().location().toString());
    assertEquals("entrelumen:repose_altar_fuels", AltarType.REPOSE.fuel().location().toString());
  }
}
