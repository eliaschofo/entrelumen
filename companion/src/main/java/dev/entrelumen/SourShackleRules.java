package dev.entrelumen;

/**
 * The Sour Shackle's numbers and its mark cycle (docs/design/dungeon-enves.md, «El Grillete Agrio»),
 * without Minecraft types so the cycle is unit-tested. Elias's spec of 29/9:
 * <ul>
 *   <li>each melee hit of the wearer adds a mark, whatever living thing it lands on: the marks are the
 *       wearer's, shared by every target;</li>
 *   <li>the {@link #MARKS}th mark bursts: {@link #BURST_DAMAGE} to every living thing within
 *       {@link #BURST_RADIUS} blocks of the target that took it, {@link #BOSS_MULTIPLIER} times that
 *       against a boss;</li>
 *   <li>the marks go back to zero and for {@link #COOLDOWN_TICKS} no hit adds a mark.</li>
 * </ul>
 * Marks do not wear off with time: they wait for the next hit.
 */
public final class SourShackleRules {
  private SourShackleRules() {}

  public static final int MARKS = 5;
  public static final float BURST_DAMAGE = 15.0F;
  public static final double BURST_RADIUS = 5.0;
  public static final float BOSS_MULTIPLIER = 3.0F;
  /** Eight seconds. */
  public static final int COOLDOWN_TICKS = 160;
  /** Sour light shards a boss chest gives instead of a second shackle. */
  public static final int REPEAT_SHARDS = 64;

  public enum Outcome {
    /** The shackle is cooling down: the hit adds nothing. */
    COOLING,
    /** One more mark, short of the burst. */
    MARKED,
    /** The last mark: the burst goes off, the marks empty and the cooldown starts. */
    BURST
  }

  /** The wearer's cycle: the marks and the game time the cooldown ends at. */
  public static final class Cycle {
    private int marks;
    private long coolUntil;

    public int marks() {
      return marks;
    }

    public long coolUntil() {
      return coolUntil;
    }

    public boolean cooling(long now) {
      return now < coolUntil;
    }

    /** Ticks of cooldown left at {@code now}; 0 when none. */
    public int coolingTicks(long now) {
      return (int) Math.max(0, Math.min(Integer.MAX_VALUE, coolUntil - now));
    }

    /** A melee hit that landed at game time {@code now}. */
    public Outcome hit(long now) {
      if (cooling(now)) return Outcome.COOLING;
      if (++marks < MARKS) return Outcome.MARKED;
      marks = 0;
      coolUntil = now + COOLDOWN_TICKS;
      return Outcome.BURST;
    }

    /** Forgets the marks and the cooldown (a new body after death has a fresh cycle anyway). */
    public void reset() {
      marks = 0;
      coolUntil = 0;
    }
  }

  /** The burst's damage to one entity. */
  public static float burstDamage(boolean boss) {
    return boss ? BURST_DAMAGE * BOSS_MULTIPLIER : BURST_DAMAGE;
  }

  /** Whether an entity whose feet are {@code distanceSqr} away from the target's is caught by the burst. */
  public static boolean inBurst(double distanceSqr) {
    return distanceSqr <= BURST_RADIUS * BURST_RADIUS;
  }
}
