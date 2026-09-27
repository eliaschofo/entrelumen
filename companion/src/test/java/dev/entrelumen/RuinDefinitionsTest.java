package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RuinDefinitionsTest {
  static final Path DEFINITIONS = Path.of("src/main/resources/data/entrelumen/heliodor_ruin");

  static Map<String, RuinDefinitions.Definition> shipped(java.util.function.Predicate<String> mods) throws IOException {
    Map<String, RuinDefinitions.Definition> result = new TreeMap<>();
    try (var files = Files.list(DEFINITIONS)) {
      for (Path file : files.sorted().toList()) {
        String id = "entrelumen:" + file.getFileName().toString().replace(".json", "");
        JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        result.put(id, RuinDefinitions.parse(id, json, mods, item -> true, entity -> true));
      }
    }
    return result;
  }

  @Test
  void theTenRuinsOfPlanTwoShip() throws Exception {
    var all = shipped(mod -> true);
    assertEquals(Set.of("entrelumen:signal_tower", "entrelumen:sunken_workshop", "entrelumen:viaduct",
        "entrelumen:dome_greenhouse", "entrelumen:nether_foundry", "entrelumen:cliff_observatory",
        "entrelumen:twilight_sanctuary", "entrelumen:sun_antechamber", "entrelumen:light_temple",
        "entrelumen:void_observatory"), all.keySet());
    assertEquals(List.of(), RuinDefinitions.problems(all.values()));
    // Exactly one landmark per act I-V, always in the Overworld (Elias, 25 September).
    Map<Integer, List<String>> landmarks = all.values().stream()
        .filter(d -> d.scale() == RuinDefinitions.Scale.LANDMARK)
        .collect(Collectors.groupingBy(RuinDefinitions.Definition::act, TreeMap::new,
            Collectors.mapping(RuinDefinitions.Definition::name, Collectors.toList())));
    assertEquals(Map.of(1, List.of("signal_tower"), 2, List.of("sunken_workshop"), 3, List.of("viaduct"),
        4, List.of("light_temple"), 5, List.of("void_observatory")), landmarks);
    for (var definition : all.values()) {
      boolean overworld = definition.dimension().equals("minecraft:overworld");
      var placement = definition.placement();
      assertEquals(overworld ? RuinDefinitions.Trigger.ACT : RuinDefinitions.Trigger.ARRIVAL, placement.trigger(), definition.id());
      if (!overworld) assertEquals(List.of(150, 500), List.of(placement.min(), placement.max()), definition.id());
      else if (definition.act() == 1) assertEquals(List.of(250, 500), List.of(placement.min(), placement.max()));
      else assertEquals(List.of(400, 1200), List.of(placement.min(), placement.max()), definition.id());
      assertEquals("entrelumen:ruins/" + definition.name(), definition.template());
      assertFalse(definition.pedestal().isEmpty(), definition.id() + ": the piece needs a challenge");
      assertTrue(definition.loot().startsWith("entrelumen:chests/ruin_act" + definition.act()), definition.id());
    }
    assertEquals(Map.of(
            "signal_tower", "first_signal", "sunken_workshop", "lost_workshop", "viaduct", "exchange_route",
            "dome_greenhouse", "nursery_protocol", "nether_foundry", "distributed_power",
            "cliff_observatory", "spectral_archive", "twilight_sanctuary", "spectral_archive",
            "sun_antechamber", "heliodor_heart", "light_temple", "spectral_archive", "void_observatory", "world_network"),
        all.values().stream().collect(Collectors.toMap(RuinDefinitions.Definition::name, RuinDefinitions.Definition::project)));
    assertEquals(new TreeSet<>(KeyPieces.PIECES.keySet()), all.values().stream()
        .map(d -> d.piece().substring("entrelumen:".length())).collect(Collectors.toCollection(TreeSet::new)));
    // Mixed challenges: every type appears somewhere.
    assertEquals(EnumSet.allOf(RuinDefinitions.ChallengeType.class).stream()
            .filter(t -> t != RuinDefinitions.ChallengeType.HIDDEN).collect(Collectors.toSet()),
        all.values().stream().flatMap(d -> d.challenges().values().stream()).map(RuinDefinitions.Challenge::type)
            .collect(Collectors.toSet()));
    var temple = all.get("entrelumen:light_temple");
    assertEquals(List.of("offerings", "lamps"), temple.challenges().get("keeper").requires(),
        "The Keeper rises after the offerings and the lamps");
  }

  @Test
  void piecesOfMissingModsMoveToTheirActsLandmark() throws Exception {
    var all = shipped(mod -> false);
    var fallbacks = RuinDefinitions.fallbacks(all.values(), mod -> false);
    // Act IV's landmark is the Temple since the roster of 26 September.
    assertEquals(Set.of("entrelumen:light_temple"), fallbacks.keySet());
    assertEquals(Set.of("entrelumen:twilight_sanctuary", "entrelumen:sun_antechamber"),
        fallbacks.get("entrelumen:light_temple").stream().map(RuinDefinitions.Definition::id)
            .collect(Collectors.toSet()));
    assertTrue(RuinDefinitions.fallbacks(all.values(), mod -> true).isEmpty());
    assertTrue(all.get("entrelumen:twilight_sanctuary").available(mod -> mod.equals("twilightforest")));
    assertFalse(all.get("entrelumen:sun_antechamber").available(mod -> mod.equals("twilightforest")));
  }

  private static final String BASE = """
      {"act": 3, "dimension": "minecraft:overworld", "scale": "medium", "template": "entrelumen:ruins/x",
       "placement": {"mode": "surface", "min": 400, "max": 1200}, "piece": "entrelumen:route_seal",
       "project": "exchange_route", "challenges": {"a": {"type": "braziers"},
       "b": {"type": "boss", "entity": "minecraft:zombie", "name": "entrelumen.ruin.boss.x", "requires": ["a"],
             "attributes": {"minecraft:generic.max_health": 40}}},
       "gates": {"vault": ["a", "b"]}, "pedestal": ["b"]}""";

  private static RuinDefinitions.Definition parse(String json) {
    return RuinDefinitions.parse("entrelumen:x", JsonParser.parseString(json), mod -> true, item -> true, entity -> true);
  }

  @Test
  void aDefinitionReadsEveryField() {
    var definition = parse(BASE);
    assertEquals(3, definition.act());
    assertEquals(RuinDefinitions.Trigger.ACT, definition.placement().trigger());
    assertEquals(List.of("a", "b"), definition.gates().get("vault"));
    var boss = definition.challenges().get("b").boss();
    assertEquals(1, boss.count());
    assertEquals(16, boss.radius());
    assertEquals(40.0, boss.attributes().get("minecraft:generic.max_health"));
    assertEquals(List.of("a", "b"), parse(BASE.replace(", \"pedestal\": [\"b\"]", "")).pedestal(),
        "Without a list the pedestal asks for every challenge");
  }

  @Test
  void badDefinitionsAreRejectedWithTheirPath() {
    Map<String, String> bad = new LinkedHashMap<>();
    bad.put("unknown field", BASE.replace("\"act\": 3", "\"act\": 3, \"colour\": 1"));
    bad.put("act", BASE.replace("\"act\": 3", "\"act\": 9"));
    bad.put("scale", BASE.replace("\"medium\"", "\"huge\""));
    bad.put("ring", BASE.replace("\"min\": 400", "\"min\": 1300"));
    bad.put("act trigger elsewhere", BASE.replace("\"minecraft:overworld\"", "\"minecraft:the_nether\"")
        .replace("\"mode\": \"surface\"", "\"mode\": \"cavern\", \"trigger\": \"act\""));
    bad.put("challenge type", BASE.replace("\"braziers\"", "\"puzzle\""));
    bad.put("later requirement", BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"braziers\", \"requires\": [\"b\"]}"));
    bad.put("gate reference", BASE.replace("[\"a\", \"b\"]", "[\"c\"]"));
    bad.put("pedestal reference", BASE.replace("\"pedestal\": [\"b\"]", "\"pedestal\": [\"z\"]"));
    bad.put("boss colour", BASE.replace("\"entity\": \"minecraft:zombie\"", "\"entity\": \"minecraft:zombie\", \"color\": \"gold\""));
    bad.put("attribute", BASE.replace(": 40}", ": -1}"));
    bad.put("item on a boss", BASE.replace("\"entity\": \"minecraft:zombie\"", "\"entity\": \"minecraft:zombie\", \"item\": \"minecraft:stone\""));
    bad.put("need on braziers", BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"braziers\", \"need\": 2}"));
    bad.forEach((name, json) -> {
      var error = assertThrows(IllegalArgumentException.class, () -> parse(json), name);
      assertTrue(error.getMessage().startsWith("Entrelumen ruin entrelumen:x"), name + ": " + error.getMessage());
    });
    assertThrows(IllegalArgumentException.class,
        () -> RuinDefinitions.parse("entrelumen:x", JsonParser.parseString(BASE), mod -> true, item -> false, entity -> true),
        "An unknown piece of a loaded mod");
    assertDoesNotThrow(() -> RuinDefinitions.parse("entrelumen:x",
        JsonParser.parseString(BASE.replace("\"act\": 3", "\"act\": 3, \"mods\": [\"absent\"]")), mod -> false,
        item -> false, entity -> false), "An absent mod's ruin still parses");
  }

  @Test
  void theSetFlagsDuplicatesAndExtraLandmarks() {
    var a = parse(BASE.replace("\"medium\"", "\"landmark\""));
    var b = RuinDefinitions.parse("entrelumen:y", JsonParser.parseString(BASE.replace("\"medium\"", "\"landmark\"")),
        mod -> true, item -> true, entity -> true);
    var problems = RuinDefinitions.problems(List.of(a, b));
    assertEquals(3, problems.size(), problems.toString());
  }
  @Test
  void theWorkshopPlaysTerrasEngineWithCreateAndItsLeversWithout() throws Exception {
    var engine = shipped(mod -> true).get("entrelumen:sunken_workshop");
    assertEquals(List.of("drowned", "engine", "seal"), List.copyOf(engine.challenges().keySet()));
    assertEquals(Set.of("sluices"), engine.dormant());
    assertEquals(Map.of("vault", List.of("seal")), engine.gates());
    assertEquals(List.of("drowned", "seal"), engine.pedestal());
    assertEquals(List.of("engine"), engine.challenges().get("seal").requires());
    assertEquals(RuinDefinitions.ChallengeType.PUMPS, engine.challenges().get("engine").type());
    assertEquals(0, engine.challenges().get("engine").engine().rpm(), "R comes from the pack's Create values");
    var seal = engine.challenges().get("seal").engine();
    assertEquals(List.of(45, 3, 60), List.of(seal.angle(), seal.tolerance(), seal.rest()));
    var levers = shipped(mod -> !mod.equals("create")).get("entrelumen:sunken_workshop");
    assertEquals(List.of("drowned", "sluices"), List.copyOf(levers.challenges().keySet()));
    assertEquals(Set.of("engine", "seal"), levers.dormant());
    assertEquals(Map.of("vault", List.of("sluices")), levers.gates());
    assertEquals(List.of("drowned", "sluices"), levers.pedestal());
  }

  @Test
  void engineFieldsBelongToTheirTypes() {
    String pumps = BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"pumps\", \"rpm\": 64}");
    assertEquals(64, parse(pumps).challenges().get("a").engine().rpm());
    String seal = BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"seal\", \"angle\": 30, \"rest\": 20}");
    var engine = parse(seal).challenges().get("a").engine();
    assertEquals(List.of(30, 3, 20), List.of(engine.angle(), engine.tolerance(), engine.rest()));
    for (String json : List.of(BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"braziers\", \"rpm\": 64}"),
        BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"pumps\", \"angle\": 45}"),
        BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"seal\", \"angle\": 90}"),
        BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"braziers\", \"mods\": \"create\"}")))
      assertThrows(IllegalArgumentException.class, () -> parse(json), json);
    // A gate whose every challenge sleeps without its mod has nothing to open it.
    String sleeping = BASE.replace("{\"type\": \"braziers\"}", "{\"type\": \"braziers\", \"mods\": [\"absent\"]}")
        .replace("[\"a\", \"b\"]", "[\"a\"]");
    assertThrows(IllegalArgumentException.class, () -> RuinDefinitions.parse("entrelumen:x", JsonParser.parseString(sleeping),
        mod -> !mod.equals("absent"), item -> true, entity -> true));
  }
}
