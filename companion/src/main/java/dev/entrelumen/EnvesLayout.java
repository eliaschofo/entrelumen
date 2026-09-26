package dev.entrelumen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Envés's floor generator (DRLG), ported from {@code art/dungeon/drlg.py}, which stays the
 * oracle for its rules. A floor is a grid of {@link #W} × {@link #H} cells; each cell is one authored
 * room template. The generator only decides which cells exist, which neighbours share a door, and
 * what each cell is for. Pure and deterministic: {@link java.util.Random} seeded per attempt, every
 * collection walked in index order (the Python walks sets, so the two never draw the same maps; the
 * invariants are what the port keeps, see {@link #check}).
 *
 * <p>Cells are indexed {@code z * W + x}. Door masks use {@link Dir#bit}: N 1, E 2, S 4, W 8.
 */
public final class EnvesLayout {
  public static final int W = 11, H = 11, CELLS = W * H;
  /** Floors per descent: four generated floors and the fixed boss floor. */
  public static final int FLOORS = 5, BOSS_DEPTH = 5;
  /** grow() defaults of the oracle (Elias, 26/9: more rooms, more labyrinthine). */
  static final double KEEP_ON = 0.5, STRAIGHT = 0.4, LOOPS = 0.07;
  static final int MAX_TRIES = 5000;

  public enum Dir {
    N(0, -1), E(1, 0), S(0, 1), W(-1, 0);

    public final int dx, dz, bit;

    Dir(int dx, int dz) {
      this.dx = dx;
      this.dz = dz;
      this.bit = 1 << ordinal();
    }

    public Dir opposite() {
      return values()[(ordinal() + 2) % 4];
    }
  }

  /** What a cell is for; {@link #id} names the template role and the saved state. */
  public enum Role {
    START("start"), EXIT("exit"), SEAL("seal"), VAULT("vault"), SHRINE("shrine"), GUARD("guard"),
    FIGHT("fight"), QUIET("quiet"), ARENA("arena"), ARENA_CENTER("arena_center"), PORTAL("portal");

    public final String id;

    Role(String id) {
      this.id = id;
    }

    public static Role byId(String id) {
      for (Role role : values()) if (role.id.equals(id)) return role;
      return null;
    }
  }

  private EnvesLayout() {}

  public static int index(int x, int z) {
    return z * W + x;
  }

  public static int x(int cell) {
    return cell % W;
  }

  public static int z(int cell) {
    return cell / W;
  }

  public static boolean inside(int x, int z) {
    return x >= 0 && x < W && z >= 0 && z < H;
  }

  /** The neighbour of a cell through a side, or -1 outside the grid. */
  public static int neighbour(int cell, Dir dir) {
    int x = x(cell) + dir.dx, z = z(cell) + dir.dz;
    return inside(x, z) ? index(x, z) : -1;
  }

  /** One floor: cells, doors and roles. Mutable only while it is generated. */
  public static final class Floor {
    public final int depth;
    final BitSet cells = new BitSet(CELLS);
    final int[] doors = new int[CELLS];
    final Role[] roles = new Role[CELLS];
    int start = -1, exit = -1;
    final List<Integer> seals = new ArrayList<>();
    /** Cells added by {@link #sprout} after the exit was chosen: hiding places for seals. */
    final BitSet sprouted = new BitSet(CELLS);

    Floor(int depth) {
      this.depth = depth;
    }

    public boolean has(int cell) {
      return cell >= 0 && cell < CELLS && cells.get(cell);
    }

    public int start() {
      return start;
    }

    public int exit() {
      return exit;
    }

    public int size() {
      return cells.cardinality();
    }

    /** Cells in index order. */
    public List<Integer> cells() {
      List<Integer> out = new ArrayList<>(size());
      for (int c = cells.nextSetBit(0); c >= 0; c = cells.nextSetBit(c + 1)) out.add(c);
      return out;
    }

    public int doors(int cell) {
      return has(cell) ? doors[cell] : 0;
    }

    public int doorCount(int cell) {
      return Integer.bitCount(doors(cell));
    }

    public boolean linked(int a, Dir dir) {
      return (doors(a) & dir.bit) != 0;
    }

    public Role role(int cell) {
      return has(cell) ? roles[cell] : null;
    }

    public List<Integer> seals() {
      return Collections.unmodifiableList(seals);
    }

    /** The cells with this role, in index order. */
    public List<Integer> withRole(Role role) {
      List<Integer> out = new ArrayList<>();
      for (int c : cells()) if (roles[c] == role) out.add(c);
      return out;
    }

    /** The guard cell (the champion before the stairs), or -1. */
    public int guard() {
      List<Integer> guards = withRole(Role.GUARD);
      return guards.isEmpty() ? -1 : guards.getFirst();
    }

    void add(int cell) {
      cells.set(cell);
    }

    void link(int a, int b) {
      for (Dir dir : Dir.values())
        if (neighbour(a, dir) == b) {
          doors[a] |= dir.bit;
          doors[b] |= dir.opposite().bit;
          return;
        }
      throw new IllegalArgumentException("Cells " + a + " and " + b + " are not neighbours");
    }

    boolean linked(int a, int b) {
      for (Dir dir : Dir.values()) if (neighbour(a, dir) == b) return (doors[a] & dir.bit) != 0;
      return false;
    }

    /** Door distances from {@code src}; -1 where unreachable. */
    public int[] bfs(int src) {
      int[] dist = new int[CELLS];
      Arrays.fill(dist, -1);
      if (!has(src)) return dist;
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      dist[src] = 0;
      queue.add(src);
      while (!queue.isEmpty()) {
        int c = queue.poll();
        for (Dir dir : Dir.values()) {
          int n = neighbour(c, dir);
          if (n >= 0 && has(n) && dist[n] < 0 && linked(c, dir)) {
            dist[n] = dist[c] + 1;
            queue.add(n);
          }
        }
      }
      return dist;
    }

    /** A shortest door path from {@code a} to {@code b}, both included; empty if unreachable. */
    public List<Integer> path(int a, int b) {
      int[] prev = new int[CELLS];
      Arrays.fill(prev, -2);
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      prev[a] = -1;
      queue.add(a);
      while (!queue.isEmpty()) {
        int c = queue.poll();
        if (c == b) break;
        for (Dir dir : Dir.values()) {
          int n = neighbour(c, dir);
          if (n >= 0 && has(n) && prev[n] == -2 && linked(c, dir)) {
            prev[n] = c;
            queue.add(n);
          }
        }
      }
      if (prev[b] == -2) return List.of();
      List<Integer> out = new ArrayList<>();
      for (int c = b; c != -1; c = prev[c]) out.add(c);
      Collections.reverse(out);
      return out;
    }

    public int maxDistance() {
      int far = 0;
      for (int d : bfs(start)) far = Math.max(far, d);
      return far;
    }
  }

  /** A whole descent: floors I-IV generated, floor V the boss floor. */
  public record Descent(long seed, List<Floor> floors) {
    public Floor floor(int depth) {
      return floors.get(depth - 1);
    }
  }

  // ---- Generation -------------------------------------------------------------------------

  /**
   * Random growth with memory: mostly keep walking from the newest cell (corridors), sometimes
   * branch from anywhere (forks and dead ends), then close a few loops so it is not a pure tree.
   */
  static Floor grow(Random rng, int depth, int start, int target, double keepOn, double straight, double loops) {
    Floor f = new Floor(depth);
    f.start = start;
    f.add(start);
    List<Integer> recent = new ArrayList<>(List.of(start));
    Dir[] heading = new Dir[CELLS];
    int tries = 0;
    while (f.size() < target && tries < MAX_TRIES) {
      tries++;
      int base;
      if (!recent.isEmpty() && rng.nextDouble() < keepOn) base = recent.getLast();
      else {
        List<Integer> all = f.cells();
        base = all.get(rng.nextInt(all.size()));
      }
      List<Dir> options = new ArrayList<>();
      for (Dir dir : Dir.values()) {
        int n = neighbour(base, dir);
        if (n >= 0 && !f.has(n)) options.add(dir);
      }
      if (options.isEmpty()) {
        recent.remove(Integer.valueOf(base));
        continue;
      }
      Dir h = heading[base];
      Dir pick = null;
      if (h != null && rng.nextDouble() < straight && options.contains(h)) pick = h;
      Dir dir = pick != null ? pick : options.get(rng.nextInt(options.size()));
      int n = neighbour(base, dir);
      f.add(n);
      f.link(base, n);
      heading[n] = dir;
      recent.add(n);
    }
    for (int c : f.cells())
      for (Dir dir : new Dir[] {Dir.E, Dir.S}) {
        int n = neighbour(c, dir);
        if (n >= 0 && f.has(n) && !f.linked(c, n) && rng.nextDouble() < loops) f.link(c, n);
      }
    return f;
  }

  /** Grows a fresh dead-end branch from the farthest cell that has room: somewhere to hide a seal. */
  static int sprout(Random rng, Floor f, int[] dist, int length) {
    List<Integer> order = f.cells();
    double[] tie = new double[CELLS];
    for (int c : order) tie[c] = rng.nextDouble();
    order.sort((a, b) -> dist[a] != dist[b] ? Integer.compare(dist[b], dist[a]) : Double.compare(tie[a], tie[b]));
    for (int c : order) {
      // The oracle also skips only the exit and the start; a seal chosen earlier must stay a dead end.
      if (c == f.exit || c == f.start || f.seals.contains(c)) continue;
      List<Dir> dirs = new ArrayList<>(List.of(Dir.values()));
      Collections.shuffle(dirs, rng);
      for (Dir dir : dirs) {
        int n = neighbour(c, dir);
        if (n < 0 || f.has(n)) continue;
        f.add(n);
        f.sprouted.set(n);
        f.link(c, n);
        int cur = n;
        for (int i = 0; i < length - 1; i++) {
          List<Integer> opts = new ArrayList<>();
          for (Dir d : Dir.values()) {
            int m = neighbour(cur, d);
            if (m >= 0 && !f.has(m)) opts.add(m);
          }
          if (opts.isEmpty()) break;
          int next = opts.get(rng.nextInt(opts.size()));
          f.add(next);
          f.sprouted.set(next);
          f.link(cur, next);
          cur = next;
        }
        return cur;
      }
    }
    return -1;
  }

  /** Seals per floor: two on floors I-II, three on III-IV (Elias, 26/9). */
  public static int sealsFor(int depth) {
    return depth >= BOSS_DEPTH ? 0 : depth <= 2 ? 2 : 3;
  }

  static Floor assign(Random rng, Floor f) {
    int[] dist = f.bfs(f.start);
    int far = 0;
    for (int d : dist) far = Math.max(far, d);
    List<Integer> dead = new ArrayList<>();
    for (int c : f.cells()) if (c != f.start && f.doorCount(c) == 1) dead.add(c);
    List<Integer> cand = new ArrayList<>();
    for (int c : dead) if (dist[c] >= 0.8 * far) cand.add(c);
    if (cand.isEmpty()) {
      int best = f.start;
      for (int c : f.cells()) if (dist[c] > dist[best]) best = c;
      cand.add(best);
    }
    f.exit = cand.get(rng.nextInt(cand.size()));
    List<Integer> mainPath = f.path(f.start, f.exit);
    BitSet main = new BitSet(CELLS);
    mainPath.forEach(main::set);
    for (int c : f.cells()) f.roles[c] = Role.QUIET;
    f.roles[f.start] = Role.START;
    f.roles[f.exit] = Role.EXIT;
    if (mainPath.size() > 2) f.roles[mainPath.get(mainPath.size() - 2)] = Role.GUARD;
    // Seals (Elias, 26/9: «que tengas que sí o sí explorar»): the stairwell stays shut until the
    // group lights every seal; they sit in dead ends off the main path, as far apart as possible.
    int wanted = sealsFor(f.depth);
    List<Integer> side = new ArrayList<>();
    for (int c : dead) if (!main.get(c) && c != f.exit) side.add(c);
    for (int k = 0; k < wanted; k++) {
      List<Integer> pool = new ArrayList<>();
      for (int c : side) if (!f.seals.contains(c) && dist[c] >= 0.3 * far) pool.add(c);
      if (pool.isEmpty()) {
        int tip = sprout(rng, f, dist, 2);
        if (tip < 0) break;
        dist = f.bfs(f.start);
        for (int c : f.cells()) if (f.roles[c] == null) f.roles[c] = Role.QUIET;
        pool.add(tip);
      }
      int pick = -1;
      double bestKey = Double.NEGATIVE_INFINITY, bestTie = 0;
      for (int c : pool) {
        double key;
        if (f.seals.isEmpty()) key = dist[c];
        else {
          int nearest = Integer.MAX_VALUE;
          for (int s : f.seals) nearest = Math.min(nearest, Math.abs(x(c) - x(s)) + Math.abs(z(c) - z(s)));
          key = nearest;
        }
        double tie = rng.nextDouble();
        if (pick < 0 || key > bestKey || (key == bestKey && tie > bestTie)) {
          pick = c;
          bestKey = key;
          bestTie = tie;
        }
      }
      f.seals.add(pick);
      f.roles[pick] = Role.SEAL;
    }
    List<Integer> vaults = new ArrayList<>();
    for (int c : side) if (!f.seals.contains(c) && dist[c] >= 0.35 * far) vaults.add(c);
    if (!vaults.isEmpty()) f.roles[vaults.get(rng.nextInt(vaults.size()))] = Role.VAULT;
    List<Integer> mids = new ArrayList<>();
    for (int c : f.cells()) if (f.roles[c] == Role.QUIET && !main.get(c) && f.doorCount(c) >= 2) mids.add(c);
    if (!mids.isEmpty() && rng.nextDouble() < 0.7) f.roles[mids.get(rng.nextInt(mids.size()))] = Role.SHRINE;
    List<Integer> rest = new ArrayList<>();
    for (int c : f.cells()) if (f.roles[c] == Role.QUIET && dist[c] > 1) rest.add(c);
    Collections.shuffle(rest, rng);
    for (int i = 0; i < (int) (rest.size() * 0.42); i++) f.roles[rest.get(i)] = Role.FIGHT;
    return f;
  }

  /**
   * Floor V is fixed in shape: a short approach, the antechamber (guard) and the arena of 3×3 cells,
   * with the portal cell beyond the arena. The oracle walks toward the middle of the grid; the port
   * also keeps the arena and the portal inside the grid when the stairs land near an edge.
   */
  static Floor bossFloor(int start) {
    Floor f = new Floor(BOSS_DEPTH);
    f.start = start;
    int x0 = x(start), z0 = z(start);
    int sx = x0 < W / 2 ? 1 : -1;
    int[] path = {start, index(x0 + sx, z0), index(x0 + 2 * sx, z0)};
    int ax = sx > 0 ? x0 + 3 : x0 - 5;
    int r0 = Math.clamp(z0 - 1, 0, H - 3);
    boolean portalNorth = r0 >= 1;
    List<Integer> arena = new ArrayList<>();
    for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) arena.add(index(ax + i, r0 + j));
    for (int c : path) f.add(c);
    for (int c : arena) f.add(c);
    f.link(path[0], path[1]);
    f.link(path[1], path[2]);
    f.link(path[2], index(x(path[2]) + sx, z0));
    for (int c : arena)
      for (Dir dir : new Dir[] {Dir.E, Dir.S}) {
        int n = neighbour(c, dir);
        if (n >= 0 && arena.contains(n)) f.link(c, n);
      }
    int edge = portalNorth ? index(ax + 1, r0) : index(ax + 1, r0 + 2);
    int portal = portalNorth ? index(ax + 1, r0 - 1) : index(ax + 1, r0 + 3);
    f.add(portal);
    f.link(edge, portal);
    for (int c : arena) f.roles[c] = Role.ARENA;
    f.roles[index(ax + 1, r0 + 1)] = Role.ARENA_CENTER;
    f.roles[path[0]] = Role.START;
    f.roles[path[1]] = Role.QUIET;
    f.roles[path[2]] = Role.GUARD;
    f.roles[portal] = Role.PORTAL;
    f.exit = portal;
    return f;
  }

  /** Room target of a generated floor: 26, 31, 36 and 41 cells. */
  public static int target(int depth) {
    return 21 + 5 * depth;
  }

  public static Descent descent(long seed) {
    Random rng = new Random(seed);
    List<Floor> floors = new ArrayList<>();
    int start = index(2 + rng.nextInt(W - 4), 2 + rng.nextInt(H - 4));
    for (int depth = 1; depth < BOSS_DEPTH; depth++) {
      Floor f = assign(rng, grow(rng, depth, start, target(depth), KEEP_ON, STRAIGHT, LOOPS));
      floors.add(f);
      start = f.exit; // the stairwell goes straight down
    }
    floors.add(bossFloor(start));
    return new Descent(seed, List.copyOf(floors));
  }

  // ---- Invariants -------------------------------------------------------------------------

  /**
   * The oracle's {@code check()} and the promises of docs/design/dungeon-enves.md; returns the
   * first broken rule, or {@code null}.
   */
  public static String check(Descent descent) {
    List<Floor> floors = descent.floors();
    if (floors.size() != FLOORS) return "expected " + FLOORS + " floors, got " + floors.size();
    for (int i = 0; i < floors.size(); i++) {
      Floor f = floors.get(i);
      String where = "floor " + f.depth + ": ";
      if (f.depth != i + 1) return where + "wrong depth";
      int[] dist = f.bfs(f.start);
      for (int c : f.cells()) if (dist[c] < 0) return where + "unreachable cell " + c;
      for (int c = 0; c < CELLS; c++) {
        if (!f.has(c) && f.doors[c] != 0) return where + "doors on a missing cell " + c;
        for (Dir dir : Dir.values()) {
          if ((f.doors(c) & dir.bit) == 0) continue;
          int n = neighbour(c, dir);
          if (n < 0 || !f.has(n)) return where + "door out of the floor at " + c;
          if ((f.doors(n) & dir.opposite().bit) == 0) return where + "one-way door at " + c;
        }
        if (f.has(c) && f.roles[c] == null) return where + "cell without role " + c;
      }
      if (i > 0 && f.start != floors.get(i - 1).exit) return where + "does not start under the stairs";
      if (f.withRole(Role.START).size() != 1 || f.roles[f.start] != Role.START) return where + "start role";
      if (f.depth < BOSS_DEPTH) {
        int grown = 0;
        for (int c : f.cells()) if (!f.sprouted.get(c)) grown = Math.max(grown, dist[c]);
        if (f.roles[f.exit] != Role.EXIT) return where + "exit role";
        if (!(f.doorCount(f.exit) == 1 || dist[f.exit] == grown)) return where + "the exit is no dead end";
        // Far: at least 80% of the deepest cell the walk grew (seal branches come later).
        if (dist[f.exit] < 0.8 * grown) return where + "the exit is too close";
        if (f.size() < target(f.depth)) return where + "only " + f.size() + " cells";
        if (f.seals.size() != sealsFor(f.depth)) return where + f.seals.size() + " seals";
        List<Integer> main = f.path(f.start, f.exit);
        for (int s : f.seals) {
          if (main.contains(s)) return where + "a seal on the main path";
          if (f.doorCount(s) != 1) return where + "a seal outside a dead end";
          if (f.roles[s] != Role.SEAL) return where + "seal role";
        }
        for (Role offPath : new Role[] {Role.VAULT, Role.SHRINE})
          for (int c : f.withRole(offPath)) if (main.contains(c)) return where + offPath.id + " on the main path";
        if (main.size() > 2 && f.roles[main.get(main.size() - 2)] != Role.GUARD) return where + "no guard before the stairs";
      } else {
        if (f.withRole(Role.ARENA).size() + f.withRole(Role.ARENA_CENTER).size() != 9) return where + "arena size";
        if (f.withRole(Role.ARENA_CENTER).size() != 1) return where + "arena centre";
        if (f.roles[f.exit] != Role.PORTAL || f.doorCount(f.exit) != 1) return where + "portal";
      }
    }
    return null;
  }
}
