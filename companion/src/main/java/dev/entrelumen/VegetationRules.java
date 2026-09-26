package dev.entrelumen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry-free rules of the Altar of Renewal, the vegetation altar: which role each column of its
 * square plays, the symmetric dye garden, the ink caps, tree density, the giant cactus shape and the
 * dye closure used to prove that its harvest makes all sixteen dyes.
 *
 * <p>Every choice depends only on the world seed and the column's position (or its offset from the
 * altar for the garden), so a repeated pass finds the same plan: already grown columns are left as
 * they are and harvested ones grow back.
 */
final class VegetationRules {
  /** Fewest and most tree placements per 16×16 chunk the altar grows, whatever the biome says. */
  static final double MIN_TREES_PER_CHUNK = 1.5;
  static final double MAX_TREES_PER_CHUNK = 8.0;
  /** Share of free columns that get a plant, and of those, the share that gets a flower. */
  static final double FLORA_DENSITY = 0.28;
  static final double FLOWER_SHARE = 0.4;
  /** Share of the free flowers that are dye-garden species rather than the biome's own. */
  static final double DYE_SHARE = 0.35;
  /** Garden beds sit at offsets ≡ 2 (mod 4) on both axes: never on the altar's cross, 1 in 16 columns. */
  static final int BED_STEP = 4;
  static final int BED_OFFSET = 2;
  /** No new trunk within this many blocks of a log, stem or sapling. */
  static final int TREE_SPACING = 3;
  /** The altar's own column and its neighbours stay free. */
  static final int CLEAR_CENTER = 1;
  static final long TREE_SALT = 0x76656765745F7472L;
  static final long FLORA_SALT = 0x76656765745F666CL;
  static final long FLOWER_SALT = 0x76656765745F6677L;
  static final long DYE_SALT = 0x76656765745F6479L;
  static final long SPECIES_SALT = 0x76656765745F7370L;
  static final long SHAPE_SALT = 0x76656765745F7368L;

  /** What a column is for. Trees and ink caps grow in the first pass, the rest in the second. */
  enum Role { NONE, TREE, INK_CAP, BED, FLORA }

  /** The sixteen dyes, in vanilla's order. */
  enum Dye {
    WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY, LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN,
    RED, BLACK;

    String item() {
      return "minecraft:" + name().toLowerCase(java.util.Locale.ROOT) + "_dye";
    }

    static Dye of(String item) {
      for (Dye dye : values()) if (dye.item().equals(item)) return dye;
      return null;
    }
  }

  /** The seven colours no vanilla combination makes; every other dye mixes from these. */
  static final Set<Dye> PRIMARIES = EnumSet.of(Dye.WHITE, Dye.RED, Dye.YELLOW, Dye.BLUE, Dye.BROWN, Dye.GREEN,
      Dye.BLACK);

  private VegetationRules() {}

  // ---- Roles ---------------------------------------------------------------------------------

  /** A garden bed: offsets ≡ 2 (mod 4) on both axes, symmetric under the eight symmetries of the square. */
  static boolean bed(int dx, int dz) {
    return Math.floorMod(dx, BED_STEP) == BED_OFFSET && Math.floorMod(dz, BED_STEP) == BED_OFFSET;
  }

  /**
   * Which dye species a bed grows. Beds are grouped by their orbit under the square's symmetries, the
   * pair (larger, smaller) of |dx| and |dz|; orbits are numbered from the centre outwards and deal the
   * species in turn, so the garden is a symmetric pattern of coloured rings and every species gets
   * whole orbits. A square at least 29 blocks wide has ten orbits, enough for seven species.
   */
  static int bedSpecies(int dx, int dz, int species) {
    if (species <= 0) return -1;
    int a = Math.max(Math.abs(dx), Math.abs(dz)), b = Math.min(Math.abs(dx), Math.abs(dz));
    int ring = (a - BED_OFFSET) / BED_STEP, step = (b - BED_OFFSET) / BED_STEP;
    int orbit = ring * (ring + 1) / 2 + step;
    return orbit % species;
  }

  /** Number of distinct bed orbits in a square of this half-width. */
  static int bedOrbits(int radius) {
    int rings = radius < BED_OFFSET ? 0 : (radius - BED_OFFSET) / BED_STEP + 1;
    return rings * (rings + 1) / 2;
  }

  /** Offset of the four ink caps on the square's diagonals: half-way out, off the bed lattice. */
  static int inkCapOffset(int radius) {
    return BED_STEP * Math.max(1, (int) Math.round(radius / 8.0));
  }

  static boolean inkCap(int dx, int dz, int radius) {
    int c = inkCapOffset(radius);
    return c < radius && Math.abs(dx) == c && Math.abs(dz) == c;
  }

  /** Within two blocks of an ink cap: kept free so the cap has room. */
  static boolean nearInkCap(int dx, int dz, int radius) {
    int c = inkCapOffset(radius);
    return c < radius && Math.abs(Math.abs(dx) - c) <= 2 && Math.abs(Math.abs(dz) - c) <= 2;
  }

  /** Trees per chunk as a per-column probability, clamped to the altar's range; zero stays zero. */
  static double treeProbability(double treesPerChunk) {
    if (!(treesPerChunk > 0)) return 0;
    return Math.max(MIN_TREES_PER_CHUNK, Math.min(MAX_TREES_PER_CHUNK, treesPerChunk)) / 256.0;
  }

  /**
   * The role of a column at world position (x, z), offset (dx, dz) from the altar. The garden and
   * the ink caps follow the altar's symmetry; trees and free flora follow a stable number per
   * world and column, so neighbouring altars agree on them.
   */
  static Role role(long seed, int x, int z, int dx, int dz, int radius, double treeProbability) {
    if (Math.abs(dx) <= CLEAR_CENTER && Math.abs(dz) <= CLEAR_CENTER) return Role.NONE;
    if (inkCap(dx, dz, radius)) return Role.INK_CAP;
    if (bed(dx, dz)) return Role.BED;
    if (nearInkCap(dx, dz, radius)) return Role.NONE;
    if (NatureRestorationRules.selected(seed, x, z, TREE_SALT, treeProbability)) return Role.TREE;
    if (NatureRestorationRules.selected(seed, x, z, FLORA_SALT, FLORA_DENSITY)) return Role.FLORA;
    return Role.NONE;
  }

  /** Whether a flora column carries a flower rather than grass. */
  static boolean flower(long seed, int x, int z) {
    return NatureRestorationRules.selected(seed, x, z, FLOWER_SALT, FLOWER_SHARE);
  }

  /** Whether a flower column takes a dye-garden species rather than one of the biome's flowers. */
  static boolean dyeFlower(long seed, int x, int z) {
    return NatureRestorationRules.selected(seed, x, z, DYE_SALT, DYE_SHARE);
  }

  /** A stable index into {@code count} options for this column and purpose. */
  static int pick(long seed, int x, int z, long salt, int count) {
    if (count <= 0) return -1;
    return Math.min(count - 1, (int) (NatureRestorationRules.unit(seed, x, z, salt) * count));
  }

  // ---- Giant cactus --------------------------------------------------------------------------

  /** One column of a giant cactus: offset from its trunk and height in blocks. */
  record CactusColumn(int dx, int dz, int height) {}

  /**
   * A giant cactus: a trunk of 5 to 7 blocks and two to four arms of 2 to 4 blocks on its
   * diagonals, each rooted in sand. Vanilla cactus dies beside any solid block on its four sides,
   * so arms cannot bend out of the trunk; diagonal columns never touch the trunk or each other
   * face to face, and read as a candelabra from any side.
   */
  static List<CactusColumn> giantCactus(long seed, int x, int z) {
    long mixed = NatureRestorationRules.mix(seed, x, z, SHAPE_SALT);
    int trunk = 5 + (int) Math.floorMod(mixed, 3L);
    int arms = 2 + (int) Math.floorMod(mixed >>> 8, 3L);
    int first = (int) Math.floorMod(mixed >>> 16, 4L);
    int[][] diagonals = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
    List<CactusColumn> columns = new ArrayList<>();
    columns.add(new CactusColumn(0, 0, trunk));
    for (int i = 0; i < arms; i++) {
      int[] d = diagonals[(first + i) % 4];
      int height = 2 + (int) Math.floorMod(mixed >>> (24 + 3 * i), 3L);
      columns.add(new CactusColumn(d[0], d[1], Math.min(height, trunk - 2)));
    }
    return List.copyOf(columns);
  }

  // ---- Dye closure ---------------------------------------------------------------------------

  /** A crafting or smelting recipe reduced to what matters here: one option set per slot, and its result. */
  record Recipe(List<Set<String>> slots, String result) {}

  /**
   * Every item reachable from the harvest by the given recipes, as many times as needed: a recipe
   * fires once each of its slots accepts an item already reached. Counts are ignored, since the
   * altar regrows its harvest.
   */
  static Set<String> closure(Collection<String> harvest, Collection<Recipe> recipes) {
    return new Closure(recipes).reach(harvest);
  }

  /**
   * The recipes indexed by the items their slots accept, so a closure only looks at the recipes an
   * item can advance: the full pack's twenty thousand recipes cost milliseconds, not seconds.
   */
  static final class Closure {
    private final List<Recipe> recipes;
    private final int[] slotStart;
    private final Map<String, List<Integer>> uses = new java.util.HashMap<>();

    Closure(Collection<Recipe> all) {
      recipes = new ArrayList<>();
      for (Recipe recipe : all) if (!recipe.slots().isEmpty()) recipes.add(recipe);
      slotStart = new int[recipes.size() + 1];
      for (int r = 0; r < recipes.size(); r++) {
        slotStart[r + 1] = slotStart[r] + recipes.get(r).slots().size();
        int slot = 0;
        for (Set<String> options : recipes.get(r).slots()) {
          int global = slotStart[r] + slot++;
          for (String option : options) uses.computeIfAbsent(option, key -> new ArrayList<>()).add(global);
        }
      }
    }

    Set<String> reach(Collection<String> harvest) {
      Set<String> reached = new HashSet<>(harvest);
      java.util.BitSet done = new java.util.BitSet(slotStart[recipes.size()]);
      int[] satisfied = new int[recipes.size()];
      java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(reached);
      while (!queue.isEmpty()) {
        List<Integer> slots = uses.get(queue.poll());
        if (slots == null) continue;
        for (int global : slots) {
          if (done.get(global)) continue;
          done.set(global);
          int r = recipe(global);
          if (++satisfied[r] == recipes.get(r).slots().size() && reached.add(recipes.get(r).result()))
            queue.add(recipes.get(r).result());
        }
      }
      return reached;
    }

    Set<Dye> missing(Collection<String> harvest) {
      Set<Dye> missing = EnumSet.allOf(Dye.class);
      missing.removeAll(dyes(reach(harvest)));
      return missing;
    }

    private int recipe(int global) {
      int index = java.util.Arrays.binarySearch(slotStart, global);
      if (index >= 0) {
        // Skip recipes with no slots, which share a start with the next one.
        while (index + 1 < slotStart.length && slotStart[index + 1] == global) index++;
        return index;
      }
      return -index - 2;
    }

    int size() {
      return recipes.size();
    }
  }

  /** The dyes among reached items. */
  static Set<Dye> dyes(Collection<String> items) {
    Set<Dye> result = EnumSet.noneOf(Dye.class);
    for (String item : items) {
      Dye dye = Dye.of(item);
      if (dye != null) result.add(dye);
    }
    return result;
  }

  /** Dyes missing from a harvest under the given recipes. */
  static Set<Dye> missing(Collection<String> harvest, Collection<Recipe> recipes) {
    Set<Dye> missing = EnumSet.allOf(Dye.class);
    missing.removeAll(dyes(closure(harvest, recipes)));
    return missing;
  }

  /** Vanilla's dye mixing recipes, for rules tests without a registry: results of combining dyes. */
  static List<Recipe> vanillaMixing() {
    List<Recipe> recipes = new ArrayList<>();
    Map<String, List<String>> mixes = Map.ofEntries(
        Map.entry("orange", List.of("red", "yellow")), Map.entry("magenta", List.of("purple", "pink")),
        Map.entry("light_blue", List.of("blue", "white")), Map.entry("lime", List.of("green", "white")),
        Map.entry("pink", List.of("red", "white")), Map.entry("gray", List.of("black", "white")),
        Map.entry("light_gray", List.of("gray", "white")), Map.entry("cyan", List.of("blue", "green")),
        Map.entry("purple", List.of("blue", "red")));
    mixes.forEach((result, parts) -> {
      List<Set<String>> slots = new ArrayList<>();
      for (String part : parts) slots.add(Set.of("minecraft:" + part + "_dye"));
      recipes.add(new Recipe(List.copyOf(slots), "minecraft:" + result + "_dye"));
    });
    return List.copyOf(recipes);
  }
}
