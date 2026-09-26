package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VegetationRulesTest {
  private static final int[][] SYMMETRIES = {{1, 0, 0, 1}, {-1, 0, 0, 1}, {1, 0, 0, -1}, {-1, 0, 0, -1},
      {0, 1, 1, 0}, {0, -1, 1, 0}, {0, 1, -1, 0}, {0, -1, -1, 0}};

  private static int[] image(int[] m, int dx, int dz) {
    return new int[] {m[0] * dx + m[1] * dz, m[2] * dx + m[3] * dz};
  }

  @Test
  void theGardenIsSymmetricOffTheCrossAndOneColumnInSixteen() {
    int radius = 24, beds = 0;
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++) {
        boolean bed = VegetationRules.bed(dx, dz);
        if (bed) beds++;
        if (dx == 0 || dz == 0) assertFalse(bed, "a bed on the altar's cross at " + dx + "," + dz);
        for (int[] m : SYMMETRIES) {
          int[] other = image(m, dx, dz);
          assertEquals(bed, VegetationRules.bed(other[0], other[1]));
          if (bed) for (int species = 1; species <= 9; species++)
            assertEquals(VegetationRules.bedSpecies(dx, dz, species),
                VegetationRules.bedSpecies(other[0], other[1], species), "species differ under a symmetry");
        }
      }
    assertEquals(12 * 12, beds, "offsets 2, 6, ..., 22 on each side of both axes");
    assertEquals(21, VegetationRules.bedOrbits(24));
    assertEquals(10, VegetationRules.bedOrbits(14));
    assertEquals(6, VegetationRules.bedOrbits(12));
  }

  @Test
  void everySpeciesGetsWholeOrbitsInEverySquareOfTheAltar() {
    for (int radius : new int[] {AltarRules.RENEWAL_RADIUS, AltarRules.RENEWAL_ARK_RADIUS, 14})
      for (int species = 1; species <= 7; species++) {
        Map<Integer, Integer> perSpecies = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx++)
          for (int dz = -radius; dz <= radius; dz++)
            if (VegetationRules.bed(dx, dz)) perSpecies.merge(VegetationRules.bedSpecies(dx, dz, species), 1, Integer::sum);
        assertEquals(species, perSpecies.size(), "radius " + radius + " misses a species of " + species);
        if (radius >= AltarRules.RENEWAL_RADIUS)
          for (int count : perSpecies.values()) assertTrue(count >= 12, "too few beds of one species: " + perSpecies);
      }
  }

  @Test
  void inkCapsSitOnTheDiagonalsOffTheGarden() {
    for (int radius : new int[] {10, 12, 24, 32}) {
      int c = VegetationRules.inkCapOffset(radius);
      assertTrue(c < radius && c % VegetationRules.BED_STEP == 0, "offset " + c + " for radius " + radius);
      List<int[]> caps = new ArrayList<>();
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++)
          if (VegetationRules.role(1L, dx, dz, dx, dz, radius, 0) == VegetationRules.Role.INK_CAP) {
            caps.add(new int[] {dx, dz});
            assertFalse(VegetationRules.bed(dx, dz));
          }
      assertEquals(4, caps.size(), "four ink caps for radius " + radius);
      for (int[] cap : caps) assertEquals(c, Math.abs(cap[0]));
    }
    assertEquals(12, VegetationRules.inkCapOffset(24));
    assertEquals(16, VegetationRules.inkCapOffset(32));
  }

  @Test
  void rolesAreStableAndFollowTheirShares() {
    long seed = 20260925L;
    double p = VegetationRules.treeProbability(8);
    int trees = 0, flora = 0, total = 0;
    for (int x = -60; x < 60; x++)
      for (int z = -60; z < 60; z++) {
        var role = VegetationRules.role(seed, x + 1000, z - 500, x, z, 64, p);
        assertEquals(role, VegetationRules.role(seed, x + 1000, z - 500, x, z, 64, p));
        if (VegetationRules.bed(x, z)) assertEquals(VegetationRules.Role.BED, role);
        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) assertEquals(VegetationRules.Role.NONE, role);
        if (role == VegetationRules.Role.TREE) trees++;
        if (role == VegetationRules.Role.FLORA) flora++;
        total++;
      }
    assertEquals(p, trees / (double) total, 0.01);
    assertTrue(flora > total * 0.2 && flora < total * 0.3, "flora share " + flora / (double) total);
    assertEquals(0, VegetationRules.treeProbability(0));
    assertEquals(0, VegetationRules.treeProbability(Double.NaN));
    assertEquals(VegetationRules.MIN_TREES_PER_CHUNK / 256.0, VegetationRules.treeProbability(0.05), 1e-12);
    assertEquals(VegetationRules.MAX_TREES_PER_CHUNK / 256.0, VegetationRules.treeProbability(40), 1e-12);
    for (int count : new int[] {1, 3, 7})
      for (int x = 0; x < 200; x++) {
        int pick = VegetationRules.pick(seed, x, -x, VegetationRules.SPECIES_SALT, count);
        assertTrue(pick >= 0 && pick < count);
      }
    assertEquals(-1, VegetationRules.pick(seed, 1, 1, VegetationRules.SPECIES_SALT, 0));
  }

  @Test
  void giantCactiStandOnTheirOwnAndNeverTouchFaceToFace() {
    for (int x = -40; x < 40; x++)
      for (int z = -40; z < 40; z += 7) {
        var shape = VegetationRules.giantCactus(99L, x, z);
        assertEquals(shape, VegetationRules.giantCactus(99L, x, z));
        var trunk = shape.getFirst();
        assertTrue(trunk.dx() == 0 && trunk.dz() == 0 && trunk.height() >= 5 && trunk.height() <= 7);
        assertTrue(shape.size() >= 3 && shape.size() <= 5, "two to four arms");
        Set<String> blocks = new HashSet<>();
        for (var column : shape) {
          assertTrue(column.height() >= 1 && column.height() <= trunk.height());
          if (column != trunk) {
            assertEquals(1, Math.abs(column.dx()));
            assertEquals(1, Math.abs(column.dz()));
            assertTrue(column.height() >= 2 && column.height() <= trunk.height() - 2);
          }
          for (int h = 1; h <= column.height(); h++) blocks.add(column.dx() + "," + h + "," + column.dz());
        }
        for (String block : blocks) {
          String[] p = block.split(",");
          int bx = Integer.parseInt(p[0]), by = Integer.parseInt(p[1]), bz = Integer.parseInt(p[2]);
          for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            assertFalse(blocks.contains((bx + side[0]) + "," + by + "," + (bz + side[1])),
                "cactus touches itself face to face at " + block);
        }
      }
  }

  /** The pinned mods' dye recipes, read from their JARs (see docs/design/ark-altars.md). */
  private static List<VegetationRules.Recipe> packRecipes() {
    List<VegetationRules.Recipe> recipes = new ArrayList<>(VegetationRules.vanillaMixing());
    Map<String, String> direct = Map.of("minecraft:lily_of_the_valley", "white", "minecraft:poppy", "red",
        "minecraft:dandelion", "yellow", "minecraft:cornflower", "blue", "eternal_starlight:conebloom", "brown",
        "eternal_starlight:swamp_rose", "green", "undergarden:ink_mushroom", "black");
    direct.forEach((plant, dye) -> recipes.add(new VegetationRules.Recipe(List.of(Set.of(plant)),
        "minecraft:" + dye + "_dye")));
    // Utilitarian's shapeless utility/green_dye: c:dyes/blue + c:dyes/yellow.
    recipes.add(new VegetationRules.Recipe(List.of(Set.of("minecraft:blue_dye"), Set.of("minecraft:yellow_dye")),
        "minecraft:green_dye"));
    return recipes;
  }

  @Test
  void theGardenHarvestMakesAllSixteenDyes() {
    var recipes = packRecipes();
    var garden = Set.of("minecraft:lily_of_the_valley", "minecraft:poppy", "minecraft:dandelion",
        "minecraft:cornflower", "eternal_starlight:conebloom", "eternal_starlight:swamp_rose", "undergarden:ink_mushroom");
    assertEquals(EnumSet.noneOf(VegetationRules.Dye.class), VegetationRules.missing(garden, recipes));
    assertEquals(List.copyOf(AltarVegetation.GARDEN).size(), garden.size());
    assertTrue(garden.containsAll(AltarVegetation.GARDEN));
    // Every primary has exactly one source in the garden, and nothing else is needed.
    for (var primary : VegetationRules.PRIMARIES) {
      Set<String> without = new HashSet<>(garden);
      without.removeIf(plant -> VegetationRules.closure(Set.of(plant), recipes).contains(primary.item()));
      if (primary == VegetationRules.Dye.GREEN) {
        // Green has two routes: the swamp rose, or blue and yellow through Utilitarian.
        assertFalse(VegetationRules.missing(without, recipes).contains(VegetationRules.Dye.GREEN));
      } else {
        assertTrue(VegetationRules.missing(without, recipes).contains(primary), "no other source of " + primary);
      }
    }
    // Without the pinned mods' three flowers, black, brown and what mixes from black are lost.
    var vanillaOnly = Set.of("minecraft:lily_of_the_valley", "minecraft:poppy", "minecraft:dandelion",
        "minecraft:cornflower");
    var missing = VegetationRules.missing(vanillaOnly, packRecipes());
    assertEquals(EnumSet.of(VegetationRules.Dye.BLACK, VegetationRules.Dye.BROWN, VegetationRules.Dye.GRAY,
        VegetationRules.Dye.LIGHT_GRAY), missing);
    var noUtilitarian = new ArrayList<>(packRecipes());
    noUtilitarian.removeIf(recipe -> recipe.slots().size() == 2 && recipe.result().equals("minecraft:green_dye"));
    assertTrue(VegetationRules.missing(vanillaOnly, noUtilitarian).containsAll(
        EnumSet.of(VegetationRules.Dye.GREEN, VegetationRules.Dye.LIME, VegetationRules.Dye.CYAN)));
  }

  @Test
  void theClosureNeedsEverySlotAndReachesThroughChains() {
    var recipes = List.of(
        new VegetationRules.Recipe(List.of(Set.of("a"), Set.of("b", "c")), "d"),
        new VegetationRules.Recipe(List.of(Set.of("d")), "e"),
        new VegetationRules.Recipe(List.of(), "free"));
    assertEquals(Set.of("a"), VegetationRules.closure(Set.of("a"), recipes));
    assertEquals(Set.of("a", "c", "d", "e"), VegetationRules.closure(Set.of("a", "c"), recipes));
    assertEquals(VegetationRules.Dye.BLACK, VegetationRules.Dye.of("minecraft:black_dye"));
    assertNull(VegetationRules.Dye.of("minecraft:dirt"));
    assertEquals(16, VegetationRules.Dye.values().length);
  }
}
