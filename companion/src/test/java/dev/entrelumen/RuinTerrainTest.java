package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How the ruins meet the terrain (Elias, 26 September): platforms and supports, the blend, terraces, soil. */
class RuinTerrainTest {
  /** A 12 x 8 base: a 6 x 6 plinth, a wall two blocks thick, a 3 x 3 pier and a lone block. */
  static boolean[] base() {
    boolean[] mask = new boolean[12 * 8];
    for (int z = 1; z <= 6; z++)
      for (int x = 0; x <= 5; x++) mask[z * 12 + x] = true;
    for (int x = 6; x <= 11; x++) {
      mask[0 * 12 + x] = true;
      mask[1 * 12 + x] = true;
    }
    for (int z = 4; z <= 6; z++)
      for (int x = 8; x <= 10; x++) mask[z * 12 + x] = true;
    mask[7 * 12 + 11] = true;
    return mask;
  }

  @Test
  void anOpeningKeepsPlatformsAndLeavesPiersAndWalls() {
    boolean[] mask = base();
    boolean[] platform = RuinTerrain.opening(mask, 12, 8, RuinTerrain.PLATFORM_RADIUS);
    for (int z = 1; z <= 6; z++)
      for (int x = 0; x <= 5; x++) assertTrue(platform[z * 12 + x], "plinth " + x + "," + z);
    for (int x = 6; x <= 11; x++) assertFalse(platform[x] || platform[12 + x], "the wall is a support");
    assertFalse(platform[5 * 12 + 9], "the 3 x 3 pier is a support");
    assertFalse(platform[7 * 12 + 11], "a lone block is a support");
    for (int i = 0; i < mask.length; i++) assertFalse(platform[i] && !mask[i], "an opening never grows");
  }

  @Test
  void distancesGrowAroundThePlatformsOnly() {
    boolean[] platform = RuinTerrain.opening(base(), 12, 8, RuinTerrain.PLATFORM_RADIUS);
    int m = 4, w = 12 + 2 * m;
    float[] distance = RuinTerrain.distance(platform, 12, 8, m);
    assertEquals(0f, distance[(1 + m) * w + (0 + m)]);
    assertEquals(1f, distance[(1 + m) * w + (-1 + m)], 1e-6, "next to the plinth");
    assertEquals(Math.sqrt(2), distance[(0 + m) * w + (-1 + m)], 1e-6, "diagonally off its corner");
    assertEquals(3f, distance[(3 + m) * w + (8 + m)], 1e-6, "the pier is not a platform: it measures to the plinth");
    assertEquals(Float.POSITIVE_INFINITY, distance[(3 + m) * w + (15 + m)], "beyond the margin");
    float[] none = RuinTerrain.distance(new boolean[4], 2, 2, 3);
    for (float d : none) assertEquals(Float.POSITIVE_INFINITY, d, "no platform, no blend");
  }

  @Test
  void theBlendMeetsTheRuinFlatAndTheLandSmoothly() {
    int ground = 70;
    for (int natural : new int[] {60, 64, 70, 76, 80}) {
      int margin = RuinTerrain.margin(Math.abs(natural - ground));
      assertTrue(margin >= RuinTerrain.MIN_MARGIN && margin <= RuinTerrain.MAX_MARGIN);
      assertEquals(ground, RuinTerrain.target(natural, ground, 0, margin), "the platform");
      assertEquals(natural, RuinTerrain.target(natural, ground, margin, margin), "the margin's edge");
      assertEquals(natural, RuinTerrain.target(natural, ground, Float.POSITIVE_INFINITY, margin), "the land beyond");
      assertTrue(Math.abs(RuinTerrain.target(natural, ground, 1, margin) - ground) <= 1, "no cliff at the edge");
      int previous = ground;
      for (int d = 1; d <= margin; d++) {
        int here = RuinTerrain.target(natural, ground, d, margin);
        assertTrue(natural >= ground ? here >= previous : here <= previous, "monotone");
        // Within the margin the steepest step stays within 1.5 times the average one.
        assertTrue(Math.abs(here - previous) <= Math.ceil(1.5 * Math.abs(natural - ground) / margin) + 1,
            "step " + (here - previous) + " at " + d + " for " + natural);
        previous = here;
      }
    }
    assertEquals(6, RuinTerrain.margin(2), "a gentle site blends over the narrowest margin");
    // A cliff or a chasm beside the ruin is left alone; a platform spans a chasm instead of plugging it.
    assertEquals(20, RuinTerrain.target(20, 70, 3, 10), "a chasm 50 down stays");
    assertEquals(110, RuinTerrain.target(110, 70, 3, 10), "a cliff 40 up stays");
    assertTrue(RuinTerrain.fills(70 - 33, 70) && !RuinTerrain.fills(70 - 34, 70));
    assertEquals(10, RuinTerrain.margin(25), "a steep one over the widest");
    assertEquals(0.5, RuinTerrain.weight(0.5), 1e-9);
    assertEquals(0.0, RuinTerrain.weight(-1), 1e-9);
  }

  @Test
  void aFillThatDropsTwoOrMoreShowsTerraceMasonry() {
    assertTrue(RuinTerrain.riser(70, 70, 68), "a two-block face");
    assertTrue(RuinTerrain.riser(69, 70, 68));
    assertFalse(RuinTerrain.riser(68, 70, 68), "under the neighbour's surface the fill is hidden");
    assertFalse(RuinTerrain.riser(70, 70, 69), "a one-block step stays earth");
  }

  @Test
  void theGroundLevelBalancesFillAndCut() {
    assertEquals(66, RuinTerrain.median(new int[] {60, 62, 66, 70, 90}));
    assertEquals(64, RuinTerrain.median(new int[] {64}));
    assertEquals(5, RuinTerrain.median(new int[] {9, 5, 1, 7}), "an even count takes the lower middle");
    assertEquals("sand", RuinTerrain.mostCommon(List.of("sand", "grass", "sand"), "dirt"));
    assertEquals("dirt", RuinTerrain.mostCommon(List.<String>of(), "dirt"));
  }

  @Test
  void soilIsAPlaceholderOutsideKeptBeds() {
    for (String soil : List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
        "minecraft:moss_block"))
      assertTrue(RuinTerrain.soil(soil), soil);
    assertFalse(RuinTerrain.soil("minecraft:rooted_dirt"), "a planter's rooted dirt is drawn, not ground");
    assertFalse(RuinTerrain.soil("minecraft:tuff_bricks"));
    var bed = RuinMarkers.parse("keep_soil size=3,2,4 block=minecraft:grass_block").orElseThrow();
    assertEquals(RuinMarkers.Kind.KEEP_SOIL, bed.kind());
    assertArrayEquals(new int[] {3, 2, 4}, bed.size());
    var box = new ProtectionRules.Box(10, 5, 10, 12, 6, 13);
    assertTrue(RuinTerrain.kept(12, 6, 13, List.of(box)));
    assertFalse(RuinTerrain.kept(13, 6, 13, List.of(box)));
    assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse("keep_soil"), "a bed needs its size");
    assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse("keep_soil size=3,2,4 radius=2"));
  }

  @Test
  void aSitesRocksAreItsCommonestStonesUnderTheSoil() {
    java.util.function.Function<String, String> kind = RuinTerrain::rockKind;
    List<String> plains = new ArrayList<>();
    for (int i = 0; i < 90; i++) plains.add("minecraft:stone");
    for (int i = 0; i < 7; i++) plains.add("minecraft:andesite");
    for (int i = 0; i < 3; i++) plains.add("minecraft:granite");
    assertEquals(List.of("minecraft:stone", "minecraft:andesite"), RuinTerrain.rocks(plains, kind, "minecraft:stone"),
        "a rock under 5% of the samples is no slot");
    List<String> desert = new ArrayList<>();
    for (int i = 0; i < 80; i++) desert.add("minecraft:sandstone");
    for (int i = 0; i < 20; i++) desert.add("minecraft:stone");
    assertEquals(List.of("minecraft:sandstone"), RuinTerrain.rocks(desert, kind, "minecraft:stone"),
        "the stone under a thin sandstone is another stratum: a desert crag is all sandstone");
    List<String> badlands = new ArrayList<>();
    for (String band : List.of("orange", "white", "orange", "red", "", "orange", "white", "red", "brown", "orange"))
      for (int i = 0; i < 10; i++) badlands.add("minecraft:" + (band.isEmpty() ? "" : band + "_") + "terracotta");
    for (int i = 0; i < 30; i++) badlands.add("minecraft:red_sandstone");
    assertEquals(List.of("minecraft:orange_terracotta", "minecraft:white_terracotta", "minecraft:red_terracotta"),
        RuinTerrain.rocks(badlands, kind, "minecraft:stone"), "three bands at most, commonest first, all terracotta");
    assertEquals(List.of("minecraft:sandstone"), RuinTerrain.rocks(List.of(), kind, "minecraft:sandstone"),
        "nothing sampled: the fallback");
    assertEquals(List.of("minecraft:stone", "minecraft:andesite"), RuinTerrain.rocks(
        List.of("minecraft:stone", "minecraft:andesite", "minecraft:andesite", "minecraft:stone"), kind, "minecraft:stone"),
        "ties keep the order of the samples");
    assertEquals("terracotta", RuinTerrain.rockKind("minecraft:terracotta"));
    assertEquals("stone", RuinTerrain.rockKind("minecraft:white_glazed_terracotta"));
    assertEquals("sandstone", RuinTerrain.rockKind("minecraft:red_sandstone"));
    assertEquals("stone", RuinTerrain.rockKind("minecraft:deepslate"));
    var rocks = List.of("sandstone");
    assertEquals("sandstone", RuinTerrain.rock(rocks, 0));
    assertEquals("sandstone", RuinTerrain.rock(rocks, 2), "slots past the site's rocks wrap");
    assertEquals("andesite", RuinTerrain.rock(List.of("stone", "andesite"), 1));
    assertEquals("stone", RuinTerrain.rock(List.of("stone", "andesite"), 2));
  }

  @Test
  void aSiteRockMarkerNamesItsBoxAndItsPlaceholders() {
    var crag = RuinMarkers.parse("site_rock size=41,17,41 rock=minecraft:stone,minecraft:andesite,minecraft:tuff").orElseThrow();
    assertEquals(RuinMarkers.Kind.SITE_ROCK, crag.kind());
    assertArrayEquals(new int[] {41, 17, 41}, crag.size());
    assertEquals(List.of("minecraft:stone", "minecraft:andesite", "minecraft:tuff"), crag.rock());
    assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse("site_rock size=4,4,4"), "no placeholders");
    assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse("site_rock rock=minecraft:stone"), "no box");
    assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse("site_rock size=4,4,4 rock=stone"), "ids are namespaced");
    assertThrows(IllegalArgumentException.class,
        () -> RuinMarkers.parse("site_rock size=4,4,4 rock=minecraft:stone,minecraft:stone"), "a block twice");
    assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse(
        "site_rock size=4,4,4 rock=minecraft:stone,minecraft:andesite,minecraft:tuff,minecraft:diorite,minecraft:granite"),
        "four slots at most");
  }
}
