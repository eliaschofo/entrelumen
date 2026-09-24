package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Registry-free rules of the Peace, Growth, Time and Repose altars: their areas, the growth rate,
 * the harvest and loot bonuses, slow-motion physics and the repair schedule. Everything here is
 * plain arithmetic, unit-tested without a server.
 */
final class AltarEffectRules {
  // ---- Areas: squares centred on the altar, and symmetric above and below it -----------------

  /** Altar of Peace: a 129×129×129 cube, the Mega Torch's reach in every direction. */
  static final int PEACE_RADIUS = 64;
  static final int PEACE_HALF_HEIGHT = 64;
  /** Altar of Growth: a 33×33 field, four blocks above and below the altar (nine layers). */
  static final int GROWTH_RADIUS = 16;
  static final int GROWTH_HALF_HEIGHT = 4;
  /** Altar of Time: a 33×33×33 cube. */
  static final int TIME_RADIUS = 16;
  static final int TIME_HALF_HEIGHT = 16;

  private AltarEffectRules() {}

  /** Membership of a box centred on the altar; the same test as {@link AltarRegistry.Entry#covers}. */
  static boolean inBox(int dx, int dy, int dz, int radius, int halfHeight) {
    return Math.abs(dx) <= radius && Math.abs(dz) <= radius
        && (halfHeight == AltarRegistry.FULL_HEIGHT || Math.abs(dy) <= halfHeight);
  }

  static long volume(int radius, int halfHeight) {
    long side = 2L * radius + 1;
    return side * side * (2L * halfHeight + 1);
  }

  // ---- Altar of Peace --------------------------------------------------------------------------

  /**
   * Whether the Altar of Peace refuses a spawn: only natural spawns of hostile, non-boss mobs
   * inside an active altar's area. Spawners, trial spawners, eggs, summons, conversions, raids,
   * patrols, sieges and every other event keep working.
   */
  static boolean peaceBlocks(MobSpawnType spawnType, boolean monster, boolean boss, boolean covered) {
    return spawnType == MobSpawnType.NATURAL && monster && !boss && covered;
  }

  // ---- Altar of Growth -------------------------------------------------------------------------

  /** Vanilla's default randomTickSpeed: random ticks per 16×16×16 section per game tick. */
  static final int VANILLA_TICKS_PER_SECTION = 3;
  static final int SECTION_BLOCKS = 16 * 16 * 16;
  /** Crops and saplings in the field grow about twenty times as fast as vanilla's default. */
  static final double GROWTH_SPEEDUP = 20.0;
  /** At most this many positions are sampled per tick, whatever the area. */
  static final int GROWTH_MAX_SAMPLES = 256;
  /** And at most this much wall time is spent on them. */
  static final long GROWTH_NANOS_PER_TICK = 1_000_000L;
  /** A ripe harvest in the field gives twice its drops, seeds excepted. */
  static final int GROWTH_HARVEST_MULTIPLIER = 2;

  /**
   * Positions sampled per tick so every block of the box receives {@code GROWTH_SPEEDUP - 1} times
   * vanilla's random-tick rate on top of it. Uniform sampling needs no scan of the area and costs
   * the same whether the field is full or empty.
   */
  static int samplesPerTick(int radius, int halfHeight) {
    double extraPerBlock = (GROWTH_SPEEDUP - 1) * VANILLA_TICKS_PER_SECTION / SECTION_BLOCKS;
    return (int) Math.min(GROWTH_MAX_SAMPLES, Math.ceil(volume(radius, halfHeight) * extraPerBlock));
  }

  /** The resulting growth speed relative to vanilla's default rate. */
  static double speedup(int samples, long volume) {
    return 1 + samples / (double) volume / ((double) VANILLA_TICKS_PER_SECTION / SECTION_BLOCKS);
  }

  // ---- Loot bonuses ----------------------------------------------------------------------------

  /** Mobs killed by a player inside the Altar of Time drop three times their loot and experience. */
  static final int TIME_LOOT_MULTIPLIER = 3;
  static final int TIME_XP_MULTIPLIER = 3;

  /**
   * Stack sizes after multiplying one generated stack: {@code count × factor} items, split into
   * stacks no larger than {@code maxStack}. An ineligible stack keeps its count.
   */
  static List<Integer> multiplied(int count, int maxStack, int factor, boolean eligible) {
    List<Integer> sizes = new ArrayList<>();
    long total = eligible ? (long) count * factor : count;
    int limit = Math.max(1, maxStack);
    while (total > 0) {
      int size = (int) Math.min(limit, total);
      sizes.add(size);
      total -= size;
    }
    return sizes;
  }

  /**
   * Which drops a bonus multiplies: stackable items without component changes that are not
   * excluded. Single items, named, enchanted or otherwise unique stacks, seeds and tagged items
   * keep their count, so no unique item is ever duplicated.
   */
  static boolean bonusEligible(int maxStack, boolean plainComponents, boolean excluded) {
    return maxStack > 1 && plainComponents && !excluded;
  }

  // ---- Altar of Time ---------------------------------------------------------------------------

  /** Hostile mobs and their projectiles move at a third of their speed. */
  static final double SLOW = 1.0 / 3.0;
  /** The attribute modifier for {@link #SLOW}: ADD_MULTIPLIED_TOTAL of -2/3 leaves one third. */
  static final double SLOW_MODIFIER = SLOW - 1.0;
  /** Mobs are checked for entering or leaving the area every half second, staggered by ID. */
  static final int MOB_CHECK_INTERVAL = 10;

  /**
   * One slow-motion step for a projectile. {@code pre} is the slowed velocity at the start of the
   * tick and {@code post} what the projectile's own tick left after moving by it: drag and any
   * propulsion applied at full strength, minus {@code gravity}. The result keeps a third of that
   * change and a ninth of the gravity, which is a third of gravity over a third of a tick. Time
   * runs at a third: the projectile follows the same path at a third of the speed, neither braked
   * nor dropping like lead. A third of gravity per tick would bend the path three times more.
   */
  static double[] slowStep(double[] pre, double[] post, double gravity) {
    double[] next = new double[3];
    for (int axis = 0; axis < 3; axis++) {
      double change = post[axis] - pre[axis] + (axis == 1 ? gravity : 0);
      next[axis] = pre[axis] + change * SLOW - (axis == 1 ? gravity * SLOW * SLOW : 0);
    }
    return next;
  }

  // ---- Altar of Repose -------------------------------------------------------------------------

  static final int REPOSE_SLOTS = 4;
  /** One durability point per stored item every five seconds of paid time, like slow Mending. */
  static final int REPAIR_INTERVAL = 100;

  /** Durability points one fuel unit repairs when every slot holds a worn item. */
  static int pointsPerFuel(int ticksPerFuel, int wornItems) {
    return ticksPerFuel / REPAIR_INTERVAL * wornItems;
  }

  /** Paid ticks needed to repair this much damage on one item. */
  static long ticksToRepair(int damage) {
    return (long) damage * REPAIR_INTERVAL;
  }
}
