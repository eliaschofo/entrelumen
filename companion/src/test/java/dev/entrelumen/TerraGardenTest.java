package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Terra's hydroponic garden without a world: the layout, its rotations and the batch arithmetic. */
class TerraGardenTest {
  private static final TerraGardenLayout.Definition GARDEN = TerraGardenLayout.builtin();
  /** Copper at any stage or waxed is the plain copper block; a grown vine is a vine (the core's rule). */
  private static final UnaryOperator<ResourceLocation> NORMALISE = id -> {
    String path = id.getPath().replace("waxed_", "").replace("weathered_", "oxidized_").replace("exposed_", "oxidized_");
    if (path.equals("cave_vines_plant")) path = "cave_vines";
    return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
  };

  private JsonObject resource(String path) throws IOException {
    var stream = getClass().getResourceAsStream(path);
    assertNotNull(stream, path);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  /** A world holding the garden built at {@code core} and rotation {@code r}: ids and turned properties. */
  private record World(Map<BlockPos, ResourceLocation> blocks, Map<BlockPos, Map<String, String>> properties) {
    static World built(BlockPos core, int rotation) {
      Map<BlockPos, ResourceLocation> blocks = new HashMap<>();
      Map<BlockPos, Map<String, String>> properties = new HashMap<>();
      for (var part : GARDEN.parts()) {
        BlockPos at = TerraGardenLayout.world(core, rotation, part.offset());
        blocks.put(at, part.block());
        Map<String, String> turned = new HashMap<>();
        part.properties().forEach((name, value) ->
            turned.put(name, name.equals("facing") ? TerraGardenLayout.turn(value, rotation) : value));
        properties.put(at, turned);
      }
      return new World(blocks, properties);
    }

    TerraGardenLayout.Scan scan(BlockPos core, int rotation) {
      return TerraGardenLayout.scan(GARDEN, core, rotation, pos -> blocks.getOrDefault(pos,
          ResourceLocation.withDefaultNamespace("air")), (pos, name) -> properties.getOrDefault(pos, Map.of()).get(name), NORMALISE);
    }

    TerraGardenLayout.Scan find(BlockPos core, int preferred) {
      return TerraGardenLayout.find(GARDEN, core, preferred, pos -> blocks.getOrDefault(pos,
          ResourceLocation.withDefaultNamespace("air")), (pos, name) -> properties.getOrDefault(pos, Map.of()).get(name), NORMALISE);
    }
  }

  @Test
  void theLayoutHasOneCoreAtTheOriginAndTwentyFiveTroughs() {
    assertEquals(1, GARDEN.parts().stream().filter(TerraGardenLayout.Part::core).count());
    assertEquals(BlockPos.ZERO, GARDEN.parts().stream().filter(TerraGardenLayout.Part::core).findFirst().orElseThrow().offset());
    assertEquals(25, GARDEN.count(ResourceLocation.fromNamespaceAndPath("entrelumen", "hydroponic_trough")));
    assertEquals(219, GARDEN.size());
  }

  @Test
  void theLayoutIsMirrorSymmetricAboutTheCoresAxis() {
    Map<BlockPos, TerraGardenLayout.Part> byPos = new HashMap<>();
    GARDEN.parts().forEach(p -> byPos.put(p.offset(), p));
    for (var part : GARDEN.parts()) {
      var mirror = byPos.get(new BlockPos(-part.offset().getX(), part.offset().getY(), part.offset().getZ()));
      assertNotNull(mirror, "no mirror for " + part.offset());
      assertEquals(part.block(), mirror.block(), "mirror of " + part.offset());
      assertEquals(part.properties().keySet(), mirror.properties().keySet());
    }
  }

  @Test
  void theGardenFitsSevenWideNineTallSevenDeepWithTheCoreOnTheFrontEdge() {
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
    for (var part : GARDEN.parts()) {
      BlockPos p = part.offset();
      minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
      minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
      minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
    }
    assertEquals(7, maxX - minX + 1);
    assertEquals(9, maxY - minY + 1);
    assertEquals(7, maxZ - minZ + 1);
    assertEquals(0, maxZ, "nothing stands in front of the core");
    assertEquals(0, minY, "the core sits on the plinth's level");
    assertEquals(TerraGardenRules.GARDEN_VOLUME, 7 * 9 * 7);
  }

  @Test
  void aBuiltGardenStandsInEveryRotationAndOnlyInItsOwn() {
    BlockPos core = new BlockPos(100, 64, -40);
    for (int r = 0; r < 4; r++) {
      World world = World.built(core, r);
      assertTrue(world.scan(core, r).complete(), "rotation " + r);
      var found = world.find(core, r + 1);
      assertTrue(found.complete(), "found from another preferred rotation, " + r);
      assertEquals(r, found.rotation());
      for (int other = 0; other < 4; other++)
        if (other != r) assertFalse(world.scan(core, other).complete(), r + " read as " + other);
    }
  }

  @Test
  void theCoreLooksOutOfTheFrontInEveryRotation() {
    for (int r = 0; r < 4; r++)
      assertEquals(r, TerraGardenLayout.rotationFacing(TerraGardenLayout.front(r)));
    assertEquals(net.minecraft.core.Direction.SOUTH, TerraGardenLayout.front(0));
  }

  @Test
  void aMissingOrWrongBlockIsNamedAndWeatheredCopperStillCounts() {
    BlockPos core = BlockPos.ZERO;
    World world = World.built(core, 2);
    var troughAt = GARDEN.parts().stream().filter(p -> p.block().getPath().equals("hydroponic_trough")).findFirst().orElseThrow();
    BlockPos hole = TerraGardenLayout.world(core, 2, troughAt.offset());
    world.blocks().put(hole, ResourceLocation.withDefaultNamespace("air"));
    var scan = world.scan(core, 2);
    assertFalse(scan.complete());
    assertEquals(List.of(hole), scan.missing());
    world.blocks().put(hole, troughAt.block());
    // every copper block weathered and waxed, every vine grown: still the garden
    world.blocks().replaceAll((pos, id) -> id.getPath().startsWith("oxidized_")
        ? ResourceLocation.withDefaultNamespace("waxed_weathered_" + id.getPath().substring("oxidized_".length()))
        : id.getPath().equals("cave_vines") ? ResourceLocation.withDefaultNamespace("cave_vines_plant") : id);
    assertTrue(world.scan(core, 2).complete());
  }

  @Test
  void anArchShoulderMayLeanEitherWayButMustHangUpsideDown() {
    BlockPos core = BlockPos.ZERO;
    World world = World.built(core, 1);
    var stair = GARDEN.parts().stream().filter(p -> p.block().getPath().endsWith("_stairs")).findFirst().orElseThrow();
    assertFalse(stair.properties().containsKey("facing"));
    BlockPos at = TerraGardenLayout.world(core, 1, stair.offset());
    world.properties().put(at, new HashMap<>(Map.of("half", "bottom")));
    assertEquals(List.of(at), world.scan(core, 1).missing());
    world.properties().put(at, new HashMap<>(Map.of("half", "top", "facing", "north")));
    assertTrue(world.scan(core, 1).complete());
  }

  @Test
  void hangingLanternsMustHangAndTheStandingOneMustStand() {
    var lanterns = GARDEN.parts().stream().filter(p -> p.block().getPath().equals("lantern")).toList();
    assertEquals(6, lanterns.size());
    assertEquals(1, lanterns.stream().filter(p -> p.properties().get("hanging").equals("false")).count());
    assertTrue(lanterns.stream().filter(p -> p.properties().get("hanging").equals("false"))
        .allMatch(p -> p.offset().equals(new BlockPos(0, 1, 0))), "the standing lantern is on the core");
  }

  @Test
  void anUnloadedChunkNeverCountsAsBuilt() {
    BlockPos core = BlockPos.ZERO;
    World world = World.built(core, 0);
    var scan = TerraGardenLayout.scan(GARDEN, core, 0, pos -> pos.getY() > 5 ? null : world.blocks().get(pos),
        (pos, name) -> world.properties().getOrDefault(pos, Map.of()).get(name), NORMALISE);
    assertTrue(scan.unloaded());
    assertFalse(scan.complete());
  }

  @Test
  void theLayoutRejectsTwoCoresOrAnOffsetCore() {
    var two = JsonParser.parseString("{\"blocks\":[{\"pos\":[0,0,0],\"block\":\"entrelumen:terra_garden_core\"},"
        + "{\"pos\":[1,0,0],\"block\":\"entrelumen:terra_garden_core\"}]}");
    assertThrows(IllegalArgumentException.class, () -> TerraGardenLayout.parse(two));
    var offset = JsonParser.parseString("{\"blocks\":[{\"pos\":[0,1,0],\"block\":\"entrelumen:terra_garden_core\"}]}");
    assertThrows(IllegalArgumentException.class, () -> TerraGardenLayout.parse(offset));
  }

  // ---- the batch ------------------------------------------------------------------------------------

  @Test
  void scalingKeepsTheExpectationExact() {
    Map<String, Long> sampled = new LinkedHashMap<>();
    sampled.put("wheat", 16L);
    sampled.put("seeds", 27L);
    Map<String, Long> scaled = TerraGardenRules.scale(sampled, 16, TerraGardenRules.HARVESTS_PER_BATCH, () -> 0.99);
    assertEquals(TerraGardenRules.HARVESTS_PER_BATCH, scaled.get("wheat"), "one wheat a harvest");
    assertEquals(27L * TerraGardenRules.HARVESTS_PER_BATCH / 16, scaled.get("seeds"));
    // a fractional expectation comes out right on average
    Random random = new Random(7);
    long sum = 0;
    int runs = 20_000;
    for (int i = 0; i < runs; i++) sum += TerraGardenRules.scale(Map.of("x", 1L), 16, 3, random::nextDouble).getOrDefault("x", 0L);
    assertEquals(3.0 / 16, (double) sum / runs, 0.01);
  }

  @Test
  void aBatchThatDoesNotFitIsCutEvenlyAndNeverOverflows() {
    Map<String, Long> batch = new LinkedHashMap<>();
    batch.put("wheat", 32_768L);
    batch.put("seeds", 56_000L);
    assertSame(batch, TerraGardenRules.fit(batch, 1_000_000L));
    var cut = TerraGardenRules.fit(batch, 11_096L);   // an eighth of the batch
    assertTrue(TerraGardenRules.total(cut) <= 11_096L);
    assertEquals(4_096L, cut.get("wheat"));
    assertEquals(7_000L, cut.get("seeds"));
    var odd = TerraGardenRules.fit(batch, 8_876L);
    assertTrue(TerraGardenRules.total(odd) <= 8_876L && odd.get("wheat") == 3_276L && odd.get("seeds") == 5_599L);
    assertTrue(TerraGardenRules.fit(batch, 0).isEmpty());
    assertEquals(TerraGardenRules.HARVESTS_PER_BATCH, TerraGardenRules.harvestsKept(100, 100, TerraGardenRules.HARVESTS_PER_BATCH));
    assertEquals(0, TerraGardenRules.harvestsKept(100, 0, TerraGardenRules.HARVESTS_PER_BATCH));
    assertEquals(TerraGardenRules.HARVESTS_PER_BATCH / 2, TerraGardenRules.harvestsKept(100, 50, TerraGardenRules.HARVESTS_PER_BATCH));
  }

  @Test
  void theGardenOutgrowsAWallOfPlantingFactoriesAndMegaPotsInTheSameBox() {
    double garden = TerraGardenRules.harvestsPerSecond();   // one wheat a harvest
    assertEquals(32_768.0, garden);
    assertEquals(19_845.0, TerraGardenRules.factoryWallWheatPerSecond(), 1e-9);
    assertTrue(garden > 1.6 * TerraGardenRules.factoryWallWheatPerSecond());
    double megaPotWall = TerraGardenRules.GARDEN_VOLUME * TerraGardenRules.MEGA_POT_HARVESTS_PER_SECOND;
    assertTrue(garden > 50 * megaPotWall, "mega pots fill the box at one per block, hoppers ignored");
    assertTrue(TerraGardenRules.STORE_CAPACITY >= 2 * (TerraGardenRules.HARVESTS_PER_BATCH * 3),
        "the store holds two batches of a crop that drops about three items a harvest");
  }

  // ---- data ----------------------------------------------------------------------------------------

  @Test
  void renewableHorizonsGrantsThePlanAndNothingElseDoes() throws Exception {
    var projects = Projects.parse(resource("/data/entrelumen/campaign/projects.json"), id -> true);
    assertEquals("entrelumen:terra_garden_plan", projects.get("renewal_engine").reward());
    assertEquals(5, projects.get("renewal_engine").act());
    assertEquals(List.of("renewal_engine"), projects.entrySet().stream()
        .filter(e -> "entrelumen:terra_garden_plan".equals(e.getValue().reward())).map(Map.Entry::getKey).toList());
  }

  @Test
  void theExcludedSeedsAreTheBossDropsAndActSixAndAllOptional() throws Exception {
    var tag = resource("/data/entrelumen/tags/item/terra_garden_excluded.json");
    assertFalse(tag.get("replace").getAsBoolean());
    Set<String> ids = new java.util.HashSet<>();
    for (var value : tag.getAsJsonArray("values")) {
      assertFalse(value.getAsJsonObject().get("required").getAsBoolean(), value.toString());
      ids.add(value.getAsJsonObject().get("id").getAsString());
    }
    assertTrue(ids.contains("mysticalagradditions:nether_star_seeds"));
    assertTrue(ids.contains("mysticalagradditions:dragon_egg_seeds"));
    assertTrue(ids.containsAll(Set.of("mysticalagradditions:gaia_spirit_seeds", "mysticalagradditions:awakened_draconium_seeds",
        "mysticalagradditions:neutronium_seeds", "mysticalagradditions:nitro_crystal_seeds")));
    var drops = resource("/data/entrelumen/tags/item/terra_garden_forbidden_drops.json");
    assertEquals(Set.of("minecraft:nether_star", "minecraft:dragon_egg"), new java.util.HashSet<>(
        drops.getAsJsonArray("values").asList().stream().map(v -> v.getAsString()).toList()));
  }

  @Test
  void everyPlayerFacingStringHasEnglishAndSpanish() throws Exception {
    var en = resource("/assets/entrelumen/lang/en_us.json");
    var es = resource("/assets/entrelumen/lang/es_es.json");
    List<String> keys = en.keySet().stream().filter(k -> k.contains("terra_garden") || k.contains("terra_grow_lamp")
        || k.contains("hydroponic_trough")).toList();
    assertTrue(keys.size() >= 30, "keys: " + keys.size());
    for (String key : keys) {
      assertTrue(es.has(key), "es_es lacks " + key);
      assertFalse(es.get(key).getAsString().isBlank(), key);
    }
    for (String status : List.of("no_seed", "excluded", "incomplete", "asleep", "full", "growing"))
      assertTrue(en.has("entrelumen.terra_garden.report." + status), status);
    for (var status : TerraGardenCoreEntity.Status.values())
      assertTrue(en.has("entrelumen.terra_garden.report." + status.name().toLowerCase(java.util.Locale.ROOT)), status.name());
  }
}
