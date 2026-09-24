package dev.entrelumen;

import java.util.UUID;

/**
 * Pure rules of the Light Key. The whole key crosses once, for a team that activated the Ark, and
 * breaks into a personal return key bound to whoever crossed; the broken key only ever carries its
 * owner, both ways, with no other condition. Mounts and passengers never travel: the traveller is
 * dismounted and passengers are left behind.
 */
public final class LightKeyRules {
  /** Channel length in ticks: releasing the use button earlier cancels cleanly. */
  public static final int CHANNEL_TICKS = 40;

  private LightKeyRules() {}

  public enum Outcome {
    /** Whole key: cross into Solsticio and break into a return key bound to the user. */
    CROSS_AND_BREAK(true),
    /** Broken key: its owner goes to Solsticio. */
    TO_SOLSTICIO(true),
    /** Broken key: its owner goes home to the Overworld. */
    HOME(true),
    /** Broken key held by someone else. */
    NOT_OWNER(false),
    /** Whole key, but the user's team has not activated the Ark. */
    LOCKED(false),
    /** Whole key used inside Solsticio: nothing to open. */
    ALREADY_INSIDE(false),
    /** Solsticio is still being placed; nothing is consumed. */
    CITY_FORMING(false);

    public final boolean travels;

    Outcome(boolean travels) {
      this.travels = travels;
    }
  }

  /**
   * @param broken whether the key is the broken return key
   * @param owner the broken key's bound owner (null for a whole key, or a legacy unbound one)
   * @param user who is using it
   * @param inSolsticio whether the user stands in Solsticio
   * @param arkActivated whether the user's team recorded the Ark activation
   * @param cityReady whether Solsticio's city finished placement
   */
  public static Outcome decide(boolean broken, UUID owner, UUID user, boolean inSolsticio,
      boolean arkActivated, boolean cityReady) {
    if (broken) {
      if (owner == null || !owner.equals(user)) return Outcome.NOT_OWNER;
      if (inSolsticio) return Outcome.HOME;
      return cityReady ? Outcome.TO_SOLSTICIO : Outcome.CITY_FORMING;
    }
    if (inSolsticio) return Outcome.ALREADY_INSIDE;
    if (!arkActivated) return Outcome.LOCKED;
    return cityReady ? Outcome.CROSS_AND_BREAK : Outcome.CITY_FORMING;
  }
}
