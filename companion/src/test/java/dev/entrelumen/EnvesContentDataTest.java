package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.entrelumen.EnvesLayout.Role;
import dev.entrelumen.EnvesPuzzleRules.SealVariant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Envés content's data files on disk: the echo tables, the loot tables, the lang keys the code
 * uses, and the calibration of the gate's price in shards against what a descent drops.
 */
class EnvesContentDataTest {
  static final Path DATA = Path.of("src/main/resources/data/entrelumen");
  static final Path LANG = Path.of("src/main/resources/assets/entrelumen/lang");
  static final Path JAVA = Path.of("src/main/java/dev/entrelumen");

  /**
   * Every non-vanilla echo, checked by name in the pinned JAR's entity registry
   * ({@code ModEntities} of L_Ender's Cataclysm 3.33, {@code EntityHandler} of Mowzie's Mobs 1.8.2) on
   * 27/9/2026; the full-pack GameTest checks them again in the live registry.
   */
  static final Set<String> VERIFIED = Set.of("cataclysm:draugr", "cataclysm:elite_draugr", "cataclysm:royal_draugr",
      "cataclysm:deepling_brute", "cataclysm:deepling_angler", "cataclysm:deepling_priest", "cataclysm:coral_golem",
      "cataclysm:ignited_revenant", "cataclysm:ignited_berserker", "cataclysm:the_prowler", "cataclysm:amethyst_crab",
      "cataclysm:the_watcher", "cataclysm:ender_golem", "mowziesmobs:grottol");

  static JsonObject json(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  static Map<String, EnvesEchoTables.Table> tables() throws IOException {
    var out = new java.util.TreeMap<String, EnvesEchoTables.Table>();
    try (Stream<Path> files = Files.list(DATA.resolve("enves/encounters"))) {
      for (Path file : files.toList()) {
        String name = file.getFileName().toString().replace(".json", "");
        out.put(name, EnvesEchoTables.parse(JsonParser.parseString(Files.readString(file))));
      }
    }
    return out;
  }

  @Test
  void everyFloorHasItsEchoesFromTheProposal() throws IOException {
    var tables = tables();
    var settings = EnvesConfig.parse(JsonParser.parseString(Files.readString(DATA.resolve("enves/config.json"))), id -> true);
    for (String tileset : settings.tilesets()) assertTrue(tables.containsKey(tileset), "no echo table for " + tileset);
    Map<String, List<String>> expected = Map.of(
        "osarios", List.of("minecraft:skeleton", "minecraft:stray", "cataclysm:draugr", "cataclysm:elite_draugr", "cataclysm:royal_draugr"),
        "cisternas", List.of("minecraft:drowned", "cataclysm:deepling_brute", "cataclysm:deepling_angler", "cataclysm:deepling_priest",
            "cataclysm:coral_golem"),
        "fundicion", List.of("minecraft:blaze", "minecraft:wither_skeleton", "cataclysm:ignited_revenant", "cataclysm:ignited_berserker",
            "cataclysm:the_prowler"),
        "geodas", List.of("minecraft:vex", "cataclysm:amethyst_crab", "cataclysm:the_watcher", "cataclysm:ender_golem", "mowziesmobs:grottol"),
        "eclipse", List.of("minecraft:wither_skeleton"));
    for (var entry : expected.entrySet()) {
      Set<String> ids = new TreeSet<>();
      for (var e : tables.get(entry.getKey()).all()) ids.add(e.entity());
      assertEquals(new TreeSet<>(entry.getValue()), ids, entry.getKey());
    }
    for (var table : tables.entrySet()) {
      for (var e : table.getValue().all()) {
        if (e.entity().startsWith("minecraft:")) continue;
        assertTrue(VERIFIED.contains(e.entity()), e.entity() + " was not checked in its pinned JAR");
        if (e != table.getValue().treasure())
          assertTrue(e.fallback() != null && e.fallback().startsWith("minecraft:"), e.entity() + " needs a vanilla fallback");
      }
      if (!table.getKey().equals("eclipse")) {
        assertFalse(table.getValue().elites().isEmpty(), table.getKey() + " has no elites");
        assertEquals(1, table.getValue().champions().size(), table.getKey() + " has one stair champion");
      }
    }
    assertTrue(tables.get("eclipse").elites().isEmpty() && tables.get("eclipse").champions().isEmpty(),
        "floor V: only the boss's lesser echoes");
    assertEquals("mowziesmobs:grottol", tables.get("geodas").treasure().entity(), "the Geodes' treasure");
  }

  /** The loot tables the config and the echoes name, with the Envés's own vocabulary only. */
  @Test
  void theLootTablesExistAndGiveShards() throws IOException {
    Set<String> known = Set.of("minecraft:item", "entrelumen:enves_gear", "entrelumen:enves_gem", "entrelumen:enves_material",
        "entrelumen:sour_shackle");
    for (String name : List.of("room", "vault", "boss", "echo_elite", "echo_guardian", "echo_champion", "grottol")) {
      var table = json(DATA.resolve("loot_table/enves/" + name + ".json"));
      boolean shards = false;
      for (JsonElement pool : table.getAsJsonArray("pools"))
        for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
          var e = entry.getAsJsonObject();
          assertTrue(known.contains(e.get("type").getAsString()), name + ": " + e.get("type"));
          if (e.has("name") && e.get("name").getAsString().equals("entrelumen:sour_light_shard")) shards = true;
          if (e.has("name")) assertFalse(e.get("name").getAsString().startsWith("apotheosis:"),
              name + " names an Apotheosis item directly: the table would not load without it");
        }
      assertTrue(shards, name + " gives no shards");
    }
    var boss = json(DATA.resolve("loot_table/enves/boss.json"));
    var gear = boss.getAsJsonArray("pools").get(0).getAsJsonObject();
    assertEquals(3, gear.get("rolls").getAsInt(), "the boss chest gives three pieces");
    assertTrue(gear.getAsJsonArray("entries").get(0).getAsJsonObject().get("top").getAsBoolean(), "of the tier's top rarity");
  }

  /** Mean shards of a table's shard pool on a floor: set_count's mean plus the floor bonus. */
  static double shards(String table, int depth) throws IOException {
    var json = json(DATA.resolve("loot_table/enves/" + table + ".json"));
    for (JsonElement pool : json.getAsJsonArray("pools")) {
      var entry = pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
      if (!entry.has("name") || !entry.get("name").getAsString().equals("entrelumen:sour_light_shard")) continue;
      double mean = 0, perFloor = 0;
      for (JsonElement f : entry.getAsJsonArray("functions")) {
        var fn = f.getAsJsonObject();
        if (fn.get("function").getAsString().equals("minecraft:set_count")) {
          var count = fn.get("count");
          mean = count.isJsonPrimitive() ? count.getAsDouble()
              : (count.getAsJsonObject().get("min").getAsDouble() + count.getAsJsonObject().get("max").getAsDouble()) / 2;
        }
        if (fn.get("function").getAsString().equals("entrelumen:enves_floor_bonus")) perFloor = fn.get("per_floor").getAsDouble();
      }
      return mean + perFloor * (depth - 1);
    }
    return 0;
  }

  /** Shares assumed for a group that plays a full descent: what it walks through and what it bothers with. */
  static final double VISITED = 0.7, VAULTS_SOLVED = 0.75, QUIET_WITH_CHEST = 2 / 3.0, GROTTOLS_CAUGHT = 0.5;

  /** Expected shards of one floor of a descent. */
  static double floorShards(EnvesLayout.Descent descent, int depth, long seed) throws IOException {
    var floor = descent.floor(depth);
    var balance = EnvesBalance.DEFAULTS;
    double elitesPerFight = 0;
    for (var group : EnvesEchoTables.Group.values()) elitesPerFight += group.elites * group.weight / 100.0;
    int fights = floor.withRole(Role.FIGHT).size(), quiet = floor.withRole(Role.QUIET).size(), vaults = floor.withRole(Role.VAULT).size();
    long guardians = EnvesPuzzleRules.sealVariants(seed, depth, floor.seals()).values().stream()
        .filter(v -> v == SealVariant.GUARDIAN).count();
    double out = VISITED * fights * elitesPerFight * shards("echo_elite", depth)
        + shards("echo_champion", depth)
        + guardians * shards("echo_guardian", depth)
        + VAULTS_SOLVED * vaults * shards("vault", depth)
        + VISITED * quiet * QUIET_WITH_CHEST * EnvesConfig.DEFAULTS.roomChestChance() * shards("room", depth);
    if (depth == 4) out += VISITED * fights * balance.grottolChance() * GROTTOLS_CAUGHT * shards("grottol", depth);
    return out;
  }

  /**
   * Elias, 27/9: the star is mostly the price of the first descents; a full descent drops more shards
   * than the door asks for (the surplus goes to Solsticio later), while an attempt that dies on floor
   * I does not pay for the next one.
   */
  @Test
  void aFullDescentPaysForTheNextDoorAndAFailedOneDoesNot() throws IOException {
    var settings = EnvesConfig.parse(JsonParser.parseString(Files.readString(DATA.resolve("enves/config.json"))), id -> true);
    int price = settings.offerings().stream().filter(o -> o.item().equals("entrelumen:sour_light_shard")).findFirst().orElseThrow().count();
    assertEquals("minecraft:nether_star", settings.offerings().getFirst().item(), "the star comes first");
    double full = 0, first = 0, three = 0;
    int seeds = 200;
    for (long seed = 1; seed <= seeds; seed++) {
      var descent = EnvesLayout.descent(seed);
      double[] floors = new double[5];
      for (int depth = 1; depth <= 4; depth++) floors[depth] = floorShards(descent, depth, seed);
      full += floors[1] + floors[2] + floors[3] + floors[4] + shards("boss", 5);
      first += floors[1];
      three += floors[1] + floors[2] + floors[3];
    }
    full /= seeds;
    first /= seeds;
    three /= seeds;
    System.out.printf("Envés shards: floor I %.1f, floors I-III %.1f, full descent %.1f; the door asks %d%n", first, three, full, price);
    assertTrue(full >= 1.5 * price, "a full descent drops " + full + ", not enough over " + price);
    assertTrue(full <= 3.5 * price, "a full descent drops " + full + ": the shards are too cheap for a door of " + price);
    assertTrue(first < price, "dying on floor I pays for the next door: " + first);
    assertTrue(three >= price, "reaching the Foundry pays for the next door: " + three);
  }

  static Map<String, String> lang(String locale) throws IOException {
    var out = new java.util.HashMap<String, String>();
    for (var entry : json(LANG.resolve(locale + ".json")).entrySet()) out.put(entry.getKey(), entry.getValue().getAsString());
    return out;
  }

  /** Every lang key the content's code names, in English and in Spanish, and their placeholders agree. */
  @Test
  void everyPlayerFacingKeyIsInBothLanguages() throws IOException {
    var en = lang("en_us");
    var es = lang("es_es");
    Pattern literal = Pattern.compile("\"((?:entrelumen\\.enves|entity\\.entrelumen|item\\.entrelumen\\.sour_light_shard)[a-z0-9_.]*)\"");
    Set<String> keys = new TreeSet<>();
    List<Path> sources = new ArrayList<>();
    try (Stream<Path> files = Files.list(JAVA)) {
      files.filter(p -> {
        String n = p.getFileName().toString();
        return (n.startsWith("Enves") || n.equals("WhiteWither.java") || n.equals("SourSkull.java")) && !n.startsWith("EnvesRules");
      }).forEach(sources::add);
    }
    sources.add(JAVA.resolve("client/EnvesGateScreen.java"));
    for (Path source : sources) {
      var m = literal.matcher(Files.readString(source));
      while (m.find()) if (!m.group(1).endsWith(".")) keys.add(m.group(1));
    }
    for (EnvesAffix affix : EnvesAffix.values()) keys.add(affix.langKey());
    for (var blessing : EnvesPuzzleRules.Blessing.values()) {
      keys.add("entrelumen.enves.blessing." + blessing.id());
      keys.add("entrelumen.enves.blessing." + blessing.id() + ".effect");
    }
    keys.add("item.entrelumen.sour_light_shard");
    for (String block : List.of("enves_brazier", "enves_mirror", "enves_glyph", "enves_shrine")) keys.add("block.entrelumen." + block);
    assertTrue(keys.size() > 60, "the scan found too few keys: " + keys.size());
    Pattern placeholder = Pattern.compile("%(\\d+\\$)?s");
    for (String key : keys) {
      assertTrue(en.containsKey(key), "en_us lacks " + key);
      assertTrue(es.containsKey(key), "es_es lacks " + key);
      assertEquals(placeholder.matcher(en.get(key)).results().count(), placeholder.matcher(es.get(key)).results().count(),
          "placeholders differ in " + key);
    }
    assertEquals("Esquirla de luz agria", es.get("item.entrelumen.sour_light_shard"));
    assertEquals("La Luz Agria", es.get("entity.entrelumen.white_wither"));
  }
}
