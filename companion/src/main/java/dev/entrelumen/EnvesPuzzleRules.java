package dev.entrelumen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The pure rules of the Envés's seals, vaults and shrines (docs/design/dungeon-enves.md, «Acertijos y
 * bóvedas»): which variant each seal and vault gets, and the puzzles themselves: the braziers' echo,
 * the glyph stones read backwards, the levers and their lamps, and the sour light bent by mirrors.
 * Everything is drawn from the attempt's seed, so a restart rebuilds the same puzzle. Unit-tested.
 */
public final class EnvesPuzzleRules {
  private EnvesPuzzleRules() {}

  /** A deterministic random for one purpose of one cell of one floor. */
  public static Random random(long seed, int depth, int cell, long salt) {
    long mix = seed * 0x9E3779B97F4A7C15L + depth * 0xC2B2AE3D27D4EB4FL + cell * 0x165667B19E3779F9L + salt * 0xD6E8FEB86659FD93L;
    mix ^= mix >>> 31;
    return new Random(mix);
  }

  // ---- Seals --------------------------------------------------------------------------------

  /** The three ways a seal asks to be lit (Elias's proposal of 27/9). */
  public enum SealVariant {
    /** A guardian echo stands at the seal; it lights once the guardian falls. */
    GUARDIAN,
    /** Touch it and hold the circle round it for 20 s while echoes arrive. */
    CIRCLE,
    /** A small puzzle of the ruins' family: braziers, mirrors or levers. */
    PUZZLE
  }

  public enum SealPuzzle {BRAZIERS, MIRRORS, LEVERS}

  /**
   * The variant of each seal of a floor, in the layout's order: the three variants shuffled once per
   * floor, so two seals never share one and three seals show all three.
   */
  public static Map<Integer, SealVariant> sealVariants(long seed, int depth, List<Integer> seals) {
    List<SealVariant> order = new ArrayList<>(List.of(SealVariant.values()));
    Collections.shuffle(order, random(seed, depth, 0, 11));
    Map<Integer, SealVariant> out = new HashMap<>();
    for (int i = 0; i < seals.size(); i++) out.put(seals.get(i), order.get(i % order.size()));
    return out;
  }

  public static SealPuzzle sealPuzzle(long seed, int depth, int cell) {
    SealPuzzle[] kinds = SealPuzzle.values();
    return kinds[random(seed, depth, cell, 12).nextInt(kinds.length)];
  }

  // ---- Vaults -------------------------------------------------------------------------------

  /** A vault's lock. The offering is rarer: it only asks for shards. */
  public enum VaultPuzzle {
    GLYPHS(3), BRAZIERS(3), LEVERS(3), OFFERING(1);

    final int weight;

    VaultPuzzle(int weight) {
      this.weight = weight;
    }
  }

  public static VaultPuzzle vaultPuzzle(long seed, int depth, int cell) {
    Random random = random(seed, depth, cell, 21);
    int total = Arrays.stream(VaultPuzzle.values()).mapToInt(p -> p.weight).sum();
    int pick = random.nextInt(total);
    for (VaultPuzzle puzzle : VaultPuzzle.values()) {
      pick -= puzzle.weight;
      if (pick < 0) return puzzle;
    }
    return VaultPuzzle.GLYPHS;
  }

  // ---- Shrines ------------------------------------------------------------------------------

  /** The five blessings; each lasts while its taker is on the floor where it was taken. */
  public enum Blessing {
    /** +25% damage dealt. */
    FERVOR,
    /** 25% less damage taken. */
    REFUGE,
    /** Speed II and Haste II. */
    HASTE,
    /** The floor's whole map. */
    CLARITY,
    /** Loot of the floor rolls one rarity higher more often. */
    FORTUNE;

    public String id() {
      return switch (this) {
        case FERVOR -> "fervor";
        case REFUGE -> "refugio";
        case HASTE -> "presteza";
        case CLARITY -> "claridad";
        case FORTUNE -> "fortuna";
      };
    }

    public static Blessing byId(String id) {
      for (Blessing blessing : values()) if (blessing.id().equals(id) || blessing.name().equalsIgnoreCase(id)) return blessing;
      return null;
    }
  }

  public static final double FERVOR_DAMAGE = 0.25, REFUGE_REDUCTION = 0.25;

  public static Blessing blessing(long seed, int depth) {
    Blessing[] all = Blessing.values();
    return all[random(seed, depth, 0, 31).nextInt(all.length)];
  }

  // ---- Braziers: the echo -------------------------------------------------------------------

  /** The order the braziers flash in: {@code length} picks of {@code braziers}, never the same twice running. */
  public static int[] echo(Random random, int braziers, int length) {
    int[] out = new int[length];
    for (int i = 0; i < length; i++) {
      int pick;
      do pick = random.nextInt(braziers);
      while (i > 0 && pick == out[i - 1]);
      out[i] = pick;
    }
    return out;
  }

  /** Progress after a brazier is touched: the next index on a match, 0 on a miss (a miss that starts the echo counts as 1). */
  public static int echoStep(int[] sequence, int progress, int touched) {
    if (progress < sequence.length && sequence[progress] == touched) return progress + 1;
    return sequence[0] == touched ? 1 : 0;
  }

  // ---- Glyph stones: read backwards ---------------------------------------------------------

  public static final int GLYPHS = 4;

  /** The code shown behind the gate: three glyphs, never a palindrome (it must be read backwards). */
  public static int[] glyphCode(Random random) {
    int[] code = new int[3];
    do for (int i = 0; i < 3; i++) code[i] = random.nextInt(GLYPHS);
    while (code[0] == code[2]);
    return code;
  }

  /** The stones open the gate when they spell the code backwards: the Envés copies everything reversed. */
  public static boolean glyphsOpen(int[] stones, int[] code) {
    if (stones.length != code.length) return false;
    for (int i = 0; i < code.length; i++) if (stones[i] != code[code.length - 1 - i]) return false;
    return true;
  }

  /** Whether the stones spell the code as seen (the first mistake everyone makes). */
  public static boolean glyphsLiteral(int[] stones, int[] code) {
    return Arrays.equals(stones, code);
  }

  /** Where the stones start: anything but the answer or the literal reading. */
  public static int[] glyphStart(Random random, int[] code) {
    int[] stones = new int[code.length];
    do for (int i = 0; i < stones.length; i++) stones[i] = random.nextInt(GLYPHS);
    while (glyphsOpen(stones, code) || glyphsLiteral(stones, code));
    return stones;
  }

  // ---- Levers and lamps ---------------------------------------------------------------------

  /** A lever board: what each lever flips (bit masks over the lamps) and the lamps' starting state. */
  public record Levers(int lamps, int[] masks, int start) {
    public int full() {
      return (1 << lamps) - 1;
    }

    /** The fewest levers that light every lamp from {@code state}, or -1 if none does. */
    public int fewest(int state) {
      int best = -1;
      for (int set = 0; set < 1 << masks.length; set++) {
        int s = state;
        for (int i = 0; i < masks.length; i++) if ((set & 1 << i) != 0) s ^= masks[i];
        if (s == full() && (best < 0 || Integer.bitCount(set) < best)) best = Integer.bitCount(set);
      }
      return best;
    }
  }

  /**
   * A board of {@code levers} over {@code lamps}: each lever flips one to {@code lamps - 1} lamps; the
   * lamps start dark or half lit, and lighting them all takes at least two pulls.
   */
  public static Levers levers(Random random, int levers, int lamps) {
    while (true) {
      int[] masks = new int[levers];
      for (int i = 0; i < levers; i++) {
        int mask;
        do mask = random.nextInt(1 << lamps);
        while (mask == 0 || Integer.bitCount(mask) >= lamps);
        masks[i] = mask;
      }
      int start = random.nextInt((1 << lamps) - 1);
      Levers board = new Levers(lamps, masks, start);
      int fewest = board.fewest(start);
      if (fewest >= 2) return board;
    }
  }

  // ---- Mirrors: the light to the seal -------------------------------------------------------

  /**
   * The seal room's mirror lattice, in offsets from the seal: {-6, -3, 0, 3, 6} on both axes, less the
   * four corners (outside the cross-shaped room) and the seal itself. The light runs one block above
   * the pedestals.
   */
  public static final int[] LATTICE = {-6, -3, 0, 3, 6};

  public static boolean latticePoint(int dx, int dz) {
    boolean onX = Arrays.stream(LATTICE).anyMatch(v -> v == dx), onZ = Arrays.stream(LATTICE).anyMatch(v -> v == dz);
    return onX && onZ && !(dx == 0 && dz == 0) && !(Math.abs(dx) == 6 && Math.abs(dz) == 6);
  }

  /** Whether an offset lies on the room's open floor (the cross of the seal template). */
  public static boolean openFloor(int dx, int dz) {
    int a = Math.max(Math.abs(dx), Math.abs(dz)), b = Math.min(Math.abs(dx), Math.abs(dz));
    return a <= 8 && (b <= 3 || a <= 5);
  }

  /** '/' turns east into north; '\' turns east into south. Aim 0 is '/', 1 is '\'. */
  public static int[] reflect(int[] dir, int aim) {
    return aim == 0 ? new int[] {-dir[1], -dir[0]} : new int[] {dir[1], dir[0]};
  }

  public static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

  /**
   * A mirror puzzle: the font's offset and the direction it shines, every mirror's offset with its
   * solved aim, which of them lie on the light's path, and the aims they start with.
   */
  public record Mirrors(int[] font, int[] direction, List<int[]> mirrors, int[] solution, boolean[] onPath, int[] start) {}

  /** The light's walk: the offsets it crosses and whether it reaches the seal. */
  public record Beam(List<int[]> cells, boolean reached) {}

  /**
   * Traces the light from the font: straight on through the open floor, turned by every mirror on its
   * way, stopped by the room's walls; it reaches the seal at (0, 0).
   */
  public static Beam trace(Mirrors puzzle, int[] aims) {
    Map<Long, Integer> at = new HashMap<>();
    for (int i = 0; i < puzzle.mirrors().size(); i++) at.put(key(puzzle.mirrors().get(i)), i);
    List<int[]> cells = new ArrayList<>();
    int x = puzzle.font()[0], z = puzzle.font()[1];
    int[] dir = puzzle.direction();
    Set<Long> seen = new HashSet<>();
    for (int step = 0; step < 96; step++) {
      x += dir[0];
      z += dir[1];
      if (x == 0 && z == 0) return new Beam(cells, true);
      if (!openFloor(x, z)) return new Beam(cells, false);
      cells.add(new int[] {x, z});
      Integer mirror = at.get(key(new int[] {x, z}));
      if (mirror != null) {
        if (!seen.add(key(new int[] {x, z}) * 4 + dirIndex(dir))) return new Beam(cells, false);
        dir = reflect(dir, aims[mirror]);
      } else if (x == puzzle.font()[0] && z == puzzle.font()[1]) return new Beam(cells, false);
    }
    return new Beam(cells, false);
  }

  private static int dirIndex(int[] dir) {
    for (int i = 0; i < DIRS.length; i++) if (DIRS[i][0] == dir[0] && DIRS[i][1] == dir[1]) return i;
    return 0;
  }

  private static long key(int[] p) {
    return ((long) p[0] << 32) ^ (p[1] & 0xFFFFFFFFL);
  }

  /**
   * A mirror puzzle whose light needs {@code turns} mirrors to reach the seal, plus two or three
   * mirrors that lead nowhere; it never starts solved.
   */
  public static Mirrors mirrors(Random random, int turns) {
    while (true) {
      Mirrors puzzle = tryMirrors(random, turns);
      if (puzzle != null) return puzzle;
    }
  }

  private static Mirrors tryMirrors(Random random, int turns) {
    // Walk back from the seal: the last leg enters it straight, every leg before turns 90 degrees.
    List<int[]> points = new ArrayList<>();
    List<int[]> legs = new ArrayList<>();
    int[] here = {0, 0};
    int[] into = DIRS[random.nextInt(4)];
    Set<Long> used = new HashSet<>();
    used.add(key(here));
    for (int leg = 0; leg <= turns; leg++) {
      List<int[]> options = new ArrayList<>();
      for (int s : new int[] {3, 6, 9, 12}) {
        int[] p = {here[0] - into[0] * s, here[1] - into[1] * s};
        if (!latticePoint(p[0], p[1]) || used.contains(key(p))) continue;
        if (!clear(here, p, into, used)) continue;
        options.add(p);
      }
      if (options.isEmpty()) return null;
      int[] p = options.get(random.nextInt(options.size()));
      points.add(p);
      legs.add(into);
      markLeg(here, p, into, used);
      used.add(key(p));
      here = p;
      if (leg < turns) {
        int[][] side = into[0] == 0 ? new int[][] {{1, 0}, {-1, 0}} : new int[][] {{0, 1}, {0, -1}};
        into = side[random.nextInt(2)];
      }
    }
    // points: [M_k, ..., M_1, F] from the seal outwards; legs[i] is the direction of travel into points[i-1] (or the seal).
    int[] font = points.getLast();
    int[] direction = legs.getLast();
    List<int[]> mirrors = new ArrayList<>();
    List<Integer> solution = new ArrayList<>();
    List<Boolean> onPath = new ArrayList<>();
    for (int i = points.size() - 2; i >= 0; i--) {
      int[] incoming = legs.get(i + 1), outgoing = legs.get(i);
      int aim = Arrays.equals(reflect(incoming, 0), outgoing) ? 0 : 1;
      mirrors.add(points.get(i));
      solution.add(aim);
      onPath.add(true);
    }
    // Decoys: mirrors off the light's path.
    List<int[]> free = new ArrayList<>();
    for (int dx : LATTICE)
      for (int dz : LATTICE)
        if (latticePoint(dx, dz) && !used.contains(key(new int[] {dx, dz}))) free.add(new int[] {dx, dz});
    Collections.shuffle(free, random);
    int decoys = 2 + random.nextInt(2);
    for (int i = 0; i < Math.min(decoys, free.size()); i++) {
      mirrors.add(free.get(i));
      solution.add(random.nextInt(2));
      onPath.add(false);
    }
    int[] solved = solution.stream().mapToInt(Integer::intValue).toArray();
    boolean[] path = new boolean[onPath.size()];
    for (int i = 0; i < path.length; i++) path[i] = onPath.get(i);
    Mirrors puzzle = new Mirrors(font, direction, List.copyOf(mirrors), solved, path, new int[solved.length]);
    if (!trace(puzzle, solved).reached()) return null;
    int[] start = new int[solved.length];
    for (int attempt = 0; attempt < 32; attempt++) {
      for (int i = 0; i < start.length; i++) start[i] = random.nextInt(2);
      if (!trace(puzzle, start).reached() && !Arrays.equals(start, solved))
        return new Mirrors(font, direction, puzzle.mirrors(), solved, path, start.clone());
    }
    return null;
  }

  /** The cells strictly between two lattice points hold no used point and lie on open floor. */
  private static boolean clear(int[] from, int[] to, int[] into, Set<Long> used) {
    int x = to[0], z = to[1];
    while (true) {
      x += into[0];
      z += into[1];
      if (x == from[0] && z == from[1]) return true;
      if (!openFloor(x, z) || used.contains(key(new int[] {x, z}))) return false;
    }
  }

  private static void markLeg(int[] from, int[] to, int[] into, Set<Long> used) {
    int x = to[0], z = to[1];
    while (true) {
      x += into[0];
      z += into[1];
      if (x == from[0] && z == from[1]) return;
      used.add(key(new int[] {x, z}));
    }
  }
}
