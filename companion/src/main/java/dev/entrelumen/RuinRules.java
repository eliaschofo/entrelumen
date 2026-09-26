package dev.entrelumen;

import java.util.*;

/**
 * Pure rules of the Heliodor ruins, unit-tested: ordered nodes, offerings, gates, the key
 * pedestal, the validity of a piece and the site search. The runtime only feeds world facts in.
 */
public final class RuinRules {
  private RuinRules() {}

  // ---- Ordered nodes (braziers, lamps, standing stones) ----------------------------------------

  public enum Step {
    /** Accepted: the node lights. */
    LIT,
    /** Already lit: nothing changes. */
    AGAIN,
    /** Out of order: every node of the challenge goes dark. */
    RESET
  }

  /**
   * One touch of node {@code index}. {@code orders[i]} is each node's turn; nodes that share a turn
   * may be lit in any order among themselves, but every node of a lower turn must be lit first.
   * {@code lit} is updated in place (cleared on a reset).
   */
  public static Step light(int[] orders, Set<Integer> lit, int index) {
    if (index < 0 || index >= orders.length) throw new IllegalArgumentException("node " + index);
    if (lit.contains(index)) return Step.AGAIN;
    int current = Integer.MAX_VALUE;
    for (int i = 0; i < orders.length; i++)
      if (!lit.contains(i)) current = Math.min(current, orders[i]);
    if (orders[index] == current) {
      lit.add(index);
      return Step.LIT;
    }
    lit.clear();
    return Step.RESET;
  }

  public static boolean allLit(int[] orders, Set<Integer> lit) {
    for (int i = 0; i < orders.length; i++) if (!lit.contains(i)) return false;
    return true;
  }

  // ---- Offerings -------------------------------------------------------------------------------

  /** Sockets filled so far are enough: {@code need} of {@code sockets}, or all when need is 0. */
  public static boolean offered(int sockets, int need, Set<Integer> filled) {
    int wanted = need <= 0 ? sockets : Math.min(need, sockets);
    return sockets > 0 && filled.size() >= wanted;
  }

  // ---- Redstone locks --------------------------------------------------------------------------

  /** Every lever of the lock stands as its answer says. */
  public static boolean unlocked(boolean[] answer, boolean[] powered) {
    if (answer.length == 0 || answer.length != powered.length) return false;
    for (int i = 0; i < answer.length; i++) if (answer[i] != powered[i]) return false;
    return true;
  }

  // ---- Gates, requirements and the pedestal ----------------------------------------------------

  /** A challenge may be worked on once the challenges it requires are solved. */
  public static boolean ready(List<String> requires, Set<String> solved) {
    return solved.containsAll(requires);
  }

  /** A gate with no requirement stays shut: it only exists to be opened. */
  public static boolean open(List<String> needs, Set<String> solved) {
    return needs != null && !needs.isEmpty() && solved.containsAll(needs);
  }

  public enum Grant {
    /** The team has not solved what the pedestal asks for. */
    LOCKED,
    /** The project that needs the piece is delivered: nothing more to give. */
    DELIVERED,
    /** A member already carries the team's current piece. */
    HELD,
    /** First piece, or a replacement for one that was lost. */
    GIVE
  }

  public static Grant grant(boolean solved, boolean delivered, int generation, boolean held) {
    if (delivered) return Grant.DELIVERED;
    if (!solved) return Grant.LOCKED;
    if (generation > 0 && held) return Grant.HELD;
    return Grant.GIVE;
  }

  /**
   * A piece counts for a campaign when it was granted to it (or to the party's founder, whose
   * record the party inherited) and no later copy replaced it. Unbound pieces (commands, creative)
   * always count.
   */
  public static boolean valid(UUID boundTo, int generation, UUID campaign, UUID founder, int current) {
    if (boundTo == null) return true;
    boolean ours = boundTo.equals(campaign) || (founder != null && boundTo.equals(founder));
    return ours && generation == current;
  }

  // ---- Sites -----------------------------------------------------------------------------------

  /**
   * Deterministic candidate centres in the ring {@code min..max} around {@code (cx, cz)}: a golden
   * angle spiral whose phase comes from the world seed and the ruin, so a restart retries the same
   * sites in the same order.
   */
  public static List<int[]> candidates(long seed, String ruin, int cx, int cz, int min, int max, int count) {
    SplittableRandom random = new SplittableRandom(seed ^ (ruin.hashCode() * 0x9E3779B97F4A7C15L));
    double phase = random.nextDouble() * Math.PI * 2, offset = random.nextDouble();
    List<int[]> result = new ArrayList<>(count);
    double golden = Math.PI * (3 - Math.sqrt(5));
    for (int i = 0; i < count; i++) {
      double angle = phase + i * golden;
      double t = (offset + i * 0.6180339887498949) % 1.0;
      // Area-uniform in the ring: the square root spreads candidates evenly across it.
      double r = Math.sqrt(min * (double) min + t * (max * (double) max - min * (double) min));
      result.add(new int[] {cx + (int) Math.round(Math.cos(angle) * r), cz + (int) Math.round(Math.sin(angle) * r)});
    }
    return result;
  }

  /** A sampled surface site: ground heights, water flags and whether the biome suits. */
  public record Sample(int[] heights, boolean[] water, boolean biome) {}

  /** Lower is better; {@link Integer#MAX_VALUE} rejects the site. */
  public static int score(Sample sample, int maxSpread) {
    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
    for (int i = 0; i < sample.heights().length; i++) {
      min = Math.min(min, sample.heights()[i]);
      max = Math.max(max, sample.heights()[i]);
      if (sample.water()[i]) wet++;
    }
    if (sample.heights().length == 0) return Integer.MAX_VALUE;
    int spread = max - min;
    if (wet * 4 > sample.heights().length || spread > maxSpread * 4) return Integer.MAX_VALUE;
    return spread * 8 + wet * 40 + (sample.biome() ? 0 : 200);
  }

  /** The most common height of a sample: the floor a surface ruin stands on. */
  public static int floor(int[] heights) {
    Map<Integer, Integer> counts = new TreeMap<>(Comparator.reverseOrder());
    for (int h : heights) counts.merge(h, 1, Integer::sum);
    int best = heights[0], seen = -1;
    for (var entry : counts.entrySet())
      if (entry.getValue() > seen) {
        seen = entry.getValue();
        best = entry.getKey();
      }
    return best;
  }

  /** Whether a horizontal footprint keeps {@code margin} blocks away from every box in {@code others}. */
  public static boolean clear(ProtectionRules.Box footprint, List<ProtectionRules.Box> others, int margin) {
    for (var other : others)
      if (footprint.minX() - margin <= other.maxX() && footprint.maxX() + margin >= other.minX()
          && footprint.minZ() - margin <= other.maxZ() && footprint.maxZ() + margin >= other.minZ())
        return false;
    return true;
  }

  /**
   * The open cavern floor of a column (Nether): the lowest y in {@code lo..hi} whose block below
   * is ground or lava and with {@code headroom} free blocks above; -1 when none.
   */
  public static int cavernFloor(boolean[] solid, boolean[] lava, int bottom, int lo, int hi, int headroom) {
    for (int y = lo; y <= hi; y++) {
      int i = y - bottom;
      if (i - 1 < 0 || i + headroom > solid.length) continue;
      if (!(solid[i - 1] || lava[i - 1])) continue;
      boolean free = true;
      for (int k = 0; k < headroom && free; k++) free = !solid[i + k] && !lava[i + k];
      if (free) return y;
    }
    return -1;
  }
}
