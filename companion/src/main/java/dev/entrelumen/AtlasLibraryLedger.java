package dev.entrelumen;

import java.util.Map;

/**
 * Pure arithmetic of the Atlas Library pool. No Minecraft types: the block entity builds a
 * {@link Profile} from each enchantment holder and keeps the pool as a saturating long.
 *
 * <p>Deposit value of one enchantment at level L is {@code floor(base * 2^(L-1) / 2)}; the
 * withdrawal price of level L is {@code base * 2^(L-1)}, doubled for treasure enchantments. A
 * deposit is therefore worth at most half (a quarter for treasure) of what the same level costs,
 * so no deposit/withdraw cycle can create points. Curses and enchantments that no vanilla source
 * hands out are worth nothing and cannot be withdrawn.
 */
public final class AtlasLibraryLedger {
  /** Highest level the library ever writes. The Ender Library stops at 31. */
  public static final int ABSOLUTE_CAP = 40;
  /** Surrounding Eterna needed per level of cap: 100 Eterna reaches the absolute cap. */
  public static final float ETERNA_PER_LEVEL = 2.5f;
  /** Known vanilla levels that malfunction above their natural maximum. */
  public static final Map<String, Integer> CEILINGS = Map.of(
      "minecraft:quick_charge", 5, // charge time reaches zero at V and goes negative afterwards
      "minecraft:lure", 5); // the lure delay reaches the maximum bite window at V

  /**
   * @param weight vanilla/data weight (1 very rare .. 10 common)
   * @param naturalMax the enchantment definition's own maximum level
   * @param curse member of {@code #minecraft:curse}
   * @param treasure member of {@code #minecraft:treasure}
   * @param obtainable member of in_enchanting_table, tradeable, on_random_loot or treasure
   * @param ceiling hard ceiling for this enchantment, at most {@link #ABSOLUTE_CAP}
   */
  public record Profile(int weight, int naturalMax, boolean curse, boolean treasure,
      boolean obtainable, int ceiling) {
    public boolean eligible() {
      return obtainable && !curse;
    }

    public static Profile of(String id, int weight, int naturalMax, boolean curse,
        boolean treasure, boolean obtainable) {
      return new Profile(weight, naturalMax, curse, treasure, obtainable,
          Math.min(ABSOLUTE_CAP, CEILINGS.getOrDefault(id, ABSOLUTE_CAP)));
    }
  }

  private AtlasLibraryLedger() {}

  /** Rarer enchantments cost more per level: ceil(10 / weight), clamped to 1..10. */
  public static int base(int weight) {
    if (weight <= 0) return 10;
    return Math.clamp((10 + weight - 1) / weight, 1, 10);
  }

  /** {@code value * 2^(level-1)}, saturating at Long.MAX_VALUE; zero for levels below 1. */
  static long scaled(long value, int level) {
    if (level < 1 || value <= 0) return 0;
    int shift = level - 1;
    if (shift >= 63 || value > (Long.MAX_VALUE >> shift)) return Long.MAX_VALUE;
    return value << shift;
  }

  public static long add(long pool, long gain) {
    if (gain <= 0) return pool;
    return pool > Long.MAX_VALUE - gain ? Long.MAX_VALUE : pool + gain;
  }

  /** Points one book level adds to the pool. */
  public static long depositValue(Profile profile, int level) {
    if (!profile.eligible()) return 0;
    return scaled(base(profile.weight()), level) / 2;
  }

  /** Full price of holding the enchantment at {@code level}. */
  public static long price(Profile profile, int level) {
    long price = scaled(base(profile.weight()), level);
    if (!profile.treasure()) return price;
    return price > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : price * 2;
  }

  /** Price of raising the enchantment on the output book from {@code from} to {@code to}. */
  public static long upgradeCost(Profile profile, int from, int to) {
    if (to <= from) return Long.MAX_VALUE;
    return price(profile, to) - price(profile, Math.max(0, from));
  }

  /** Highest level the library offers with the given surrounding Eterna; 0 means none. */
  public static int cap(Profile profile, float eterna) {
    if (!profile.eligible()) return 0;
    if (profile.naturalMax() <= 1) return 1;
    int byEterna = (int) Math.floor(Math.max(0f, eterna) / ETERNA_PER_LEVEL);
    return Math.clamp(byEterna, 1, Math.max(1, profile.ceiling()));
  }

  public record Withdrawal(boolean accepted, long pool, long cost) {
    static Withdrawal rejected(long pool) {
      return new Withdrawal(false, pool, 0);
    }
  }

  /** Server-side validation of one request; the pool never goes negative. */
  public static Withdrawal withdraw(long pool, Profile profile, int current, int target,
      float eterna) {
    if (!profile.eligible() || current < 0 || target <= current || target > cap(profile, eterna))
      return Withdrawal.rejected(pool);
    long cost = upgradeCost(profile, current, target);
    if (cost <= 0 || cost > pool) return Withdrawal.rejected(pool);
    return new Withdrawal(true, pool - cost, cost);
  }

  /** Highest level in (current, cap] the pool can pay for, or {@code current} if none. */
  public static int maxAffordable(long pool, Profile profile, int current, float eterna) {
    int best = current;
    int cap = cap(profile, eterna);
    for (int level = current + 1; level <= cap; level++) {
      if (upgradeCost(profile, current, level) > pool) break;
      best = level;
    }
    return best;
  }
}
