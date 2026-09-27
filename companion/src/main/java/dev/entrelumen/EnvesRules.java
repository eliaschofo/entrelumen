package dev.entrelumen;

import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesLayout.Dir;
import dev.entrelumen.EnvesLayout.Role;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The pure rules of the Envés (Elias, 26/9): who may enter, the shared fall pool, when an attempt
 * ends, when a stairwell opens, what the fog map shows and when a player counts as flying.
 * Registry-free and unit-tested; {@link Enves} only feeds it observations.
 */
public final class EnvesRules {
  /** Ticks airborne and rising, or hovering, before the Envés pulls a player down. */
  public static final int RISE_TICKS = 20, HOVER_TICKS = 30;
  /** Rising faster than this counts as climbing through the air; slower and still counts as hovering. */
  static final double RISE_SPEED = 0.02, HOVER_SPEED = 0.03;

  private EnvesRules() {}

  // ---- Entry --------------------------------------------------------------------------------

  /** The Sealed Stair and the gate answer only a campaign that reached World Tier Frontier. */
  public static boolean frontier(List<Tier> reached) {
    return reached.contains(Tier.FRONTIER);
  }

  /** The difficulties a player may pick: every World Tier up to their current one. */
  public static List<Tier> choices(Tier current) {
    List<Tier> out = new ArrayList<>();
    if (current == null) return out;
    for (Tier tier : Tier.values()) if (tier.ordinal() <= current.ordinal()) out.add(tier);
    return out;
  }

  public static boolean validChoice(Tier chosen, Tier current) {
    return chosen != null && current != null && chosen.ordinal() <= current.ordinal();
  }

  // ---- The fall pool ------------------------------------------------------------------------

  /** The group's falls: {@code perMember} for every member online when the attempt starts. */
  public static int pool(int members, int perMember) {
    return Math.max(1, members) * Math.max(1, perMember);
  }

  /** Falls left after one more fall; the attempt fails when this reaches 0. */
  public static int afterFall(int left) {
    return Math.max(0, left - 1);
  }

  public static boolean exhausted(int left) {
    return left <= 0;
  }

  // ---- Lifecycle ----------------------------------------------------------------------------

  /** An attempt ends once nobody of the group has been inside for {@code limit} ticks. */
  public static boolean abandoned(long emptySince, long now, long limit) {
    return emptySince >= 0 && now - emptySince >= limit;
  }

  /** Floor k+1 is generated once someone reaches floor k's guard or exit, or lights every seal. */
  public static boolean nextFloorDue(boolean guardReached, boolean exitReached, int lit, int seals) {
    return guardReached || exitReached || (seals > 0 && lit >= seals);
  }

  /** The stairwell opens when every seal of its floor is lit and the floor below is ready. */
  public static boolean stairOpens(int lit, int seals, boolean nextReady) {
    return lit >= seals && nextReady;
  }

  // ---- Movement -----------------------------------------------------------------------------

  /**
   * Catches every way of flying the Envés forbids, whatever mod provides it (jetpacks, flight
   * spells, creative-style flight): a player off the ground who keeps rising, or hangs in the air,
   * for longer than a jump or a knock-back lasts. Feed it one observation per tick.
   */
  public static final class Lift {
    int rising, hovering;

    /**
     * {@code airborne}: off the ground, out of fluids, off ladders and not riding. {@code exempt}:
     * levitation or slow falling, creative or spectator, or the operator bypass. Returns true when
     * the player must be pulled down; the counters then restart.
     */
    public boolean observe(boolean airborne, boolean exempt, double dy) {
      if (!airborne || exempt) {
        rising = hovering = 0;
        return false;
      }
      rising = dy > RISE_SPEED ? rising + 1 : 0;
      hovering = Math.abs(dy) < HOVER_SPEED ? hovering + 1 : 0;
      if (rising > RISE_TICKS || hovering > HOVER_TICKS) {
        rising = hovering = 0;
        return true;
      }
      return false;
    }
  }

  /** Whether a command line (with or without its slash) starts with a denied command. */
  public static boolean commandDenied(String input, Set<String> denied) {
    if (input == null) return false;
    String line = input.strip();
    if (line.startsWith("/")) line = line.substring(1);
    int space = line.indexOf(' ');
    String root = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
    int colon = root.indexOf(':');
    if (colon >= 0) root = root.substring(colon + 1);
    return !root.isEmpty() && denied.contains(root);
  }

  // ---- The fog map --------------------------------------------------------------------------

  /**
   * One cell the group knows about. Explored cells show their doors and, once found, their role;
   * glimpsed cells (seen through a door of an explored one) are only a blur with no doors or role.
   */
  public record KnownCell(int cell, int doors, Role role, boolean explored, boolean lit) {}

  /** Neighbours through a door of an explored cell that nobody has stepped in yet. */
  public static BitSet glimpsed(EnvesLayout.Floor floor, BitSet explored) {
    BitSet out = new BitSet(EnvesLayout.CELLS);
    for (int c = explored.nextSetBit(0); c >= 0; c = explored.nextSetBit(c + 1)) {
      if (!floor.has(c)) continue;
      for (Dir dir : Dir.values()) {
        int n = EnvesLayout.neighbour(c, dir);
        if (n >= 0 && floor.linked(c, dir) && !explored.get(n)) out.set(n);
      }
    }
    return out;
  }

  /** What the group's map shows of a floor: explored cells in full, glimpsed ones blurred. */
  public static List<KnownCell> known(EnvesLayout.Floor floor, BitSet explored, BitSet lit) {
    List<KnownCell> out = new ArrayList<>();
    BitSet glimpsed = glimpsed(floor, explored);
    for (int c = 0; c < EnvesLayout.CELLS; c++) {
      if (!floor.has(c)) continue;
      if (explored.get(c)) out.add(new KnownCell(c, floor.doors(c), floor.role(c), true, lit.get(c)));
      else if (glimpsed.get(c)) out.add(new KnownCell(c, 0, null, false, false));
    }
    return out;
  }
}
