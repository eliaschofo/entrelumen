package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * The shipped ruins agree with each other: every definition's template exists, its markers parse
 * and name the definition's challenges and gates, every challenge has what it needs to be solved,
 * and the pieces, loot tables, models and EN/ES text are all there.
 */
class RuinTemplatesTest {
  static final Path RESOURCES = Path.of("src/main/resources");

  record Found(List<RuinMarkers.Marker> markers, int[] size, Set<String> blocks) {
    List<RuinMarkers.Marker> of(RuinMarkers.Kind kind) {
      return markers.stream().filter(m -> m.kind() == kind).toList();
    }

    List<RuinMarkers.Marker> of(RuinMarkers.Kind kind, String challenge) {
      return markers.stream().filter(m -> m.kind() == kind && m.challenge().equals(challenge)).toList();
    }
  }

  static Found read(String template) throws Exception {
    String path = template.substring(template.indexOf(':') + 1);
    CompoundTag tag;
    try (InputStream stream = Files.newInputStream(RESOURCES.resolve("data/entrelumen/structure/" + path + ".nbt"))) {
      tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
    }
    var size = tag.getList("size", Tag.TAG_INT);
    var palette = tag.getList("palette", Tag.TAG_COMPOUND);
    Set<String> blocks = new TreeSet<>();
    for (int i = 0; i < palette.size(); i++) blocks.add(palette.getCompound(i).getString("Name"));
    List<RuinMarkers.Marker> markers = new ArrayList<>();
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      var block = (CompoundTag) element;
      var nbt = block.getCompound("nbt");
      if (!"DATA".equals(nbt.getString("mode"))) continue;
      assertEquals("minecraft:structure_block", palette.getCompound(block.getInt("state")).getString("Name"));
      markers.add(RuinMarkers.parse(nbt.getString("metadata")).orElseThrow(
          () -> new AssertionError(template + ": not a ruin marker: " + nbt.getString("metadata"))));
    }
    return new Found(markers, new int[] {size.getInt(0), size.getInt(1), size.getInt(2)}, blocks);
  }

  @Test
  void everyTemplateCarriesTheMarkersItsDefinitionNeeds() throws Exception {
    for (var definition : RuinDefinitionsTest.shipped(mod -> true).values()) {
      String id = definition.id();
      var found = read(definition.template());
      assertEquals(1, found.of(RuinMarkers.Kind.PEDESTAL).size(), id + ": one key pedestal");
      assertTrue(found.of(RuinMarkers.Kind.GROUND).size() == 1, id + ": one ground marker");
      assertFalse(found.of(RuinMarkers.Kind.CHEST).isEmpty(), id + ": loot");
      for (var marker : found.markers()) {
        if (marker.kind().challenge())
          assertTrue(definition.challenges().containsKey(marker.challenge()) || definition.dormant().contains(marker.challenge()),
              id + ": marker for unknown challenge " + marker);
        if (marker.kind() == RuinMarkers.Kind.GATE)
          assertTrue(definition.gates().containsKey(marker.param("id", "")), id + ": gate without a rule " + marker);
      }
      for (var gate : definition.gates().keySet())
        assertTrue(found.markers().stream().anyMatch(m -> m.kind() == RuinMarkers.Kind.GATE && m.param("id", "").equals(gate)),
            id + ": gate " + gate + " has no cell");
      for (var challenge : definition.challenges().values()) {
        String c = challenge.id();
        switch (challenge.type()) {
          case BRAZIERS -> assertFalse(found.of(RuinMarkers.Kind.BRAZIER, c).isEmpty(), id + "/" + c);
          case MIRRORS -> {
            assertFalse(found.of(RuinMarkers.Kind.MIRROR, c).isEmpty(), id + "/" + c);
            assertEquals(1, found.of(RuinMarkers.Kind.RECEPTOR, c).size(), id + "/" + c);
          }
          case REDSTONE -> assertFalse(found.of(RuinMarkers.Kind.LEVER, c).isEmpty() && found.of(RuinMarkers.Kind.LOCK, c).isEmpty(),
              id + "/" + c);
          case OFFERING -> {
            var sockets = found.of(RuinMarkers.Kind.SOCKET, c);
            assertFalse(sockets.isEmpty(), id + "/" + c);
            assertTrue(challenge.need() <= sockets.size(), id + "/" + c);
            for (var socket : sockets)
              assertFalse(socket.param("item", challenge.item()).isEmpty(), id + "/" + c + ": what does it take?");
          }
          case BOSS -> assertEquals(1, found.of(RuinMarkers.Kind.BOSS, c).size(), id + "/" + c);
          case HIDDEN -> assertFalse(found.of(RuinMarkers.Kind.HIDDEN, c).isEmpty(), id + "/" + c);
          case PUMPS -> assertFalse(found.of(RuinMarkers.Kind.PUMP, c).isEmpty() || found.of(RuinMarkers.Kind.DRAIN).isEmpty(),
              id + "/" + c);
          case SEAL -> assertEquals(1, found.of(RuinMarkers.Kind.SEAL, c).size(), id + "/" + c);
        }
      }
      for (var marker : found.of(RuinMarkers.Kind.CHEST))
        assertTrue(Files.exists(RESOURCES.resolve("data/entrelumen/loot_table/"
            + marker.param("loot", definition.loot()).substring("entrelumen:".length()) + ".json")), id + ": " + marker);
      for (var marker : found.markers())
        if (!marker.block().isEmpty()) assertFalse(marker.kind().ownBlock(), id + ": " + marker);
    }
  }

  @Test
  void theWorkshopKeepsTerrasArm() throws Exception {
    var found = read("entrelumen:ruins/sunken_workshop");
    assertEquals(1, found.of(RuinMarkers.Kind.CHEST).stream()
        .filter(m -> m.param("loot", "").equals("entrelumen:chests/ruin_act2_workshop")).count());
  }

  @Test
  void theWorkshopCarriesTerrasEngine() throws Exception {
    var found = read("entrelumen:ruins/sunken_workshop");
    assertEquals(4, found.of(RuinMarkers.Kind.WHEEL).size(), "Four wheels");
    var pumps = found.of(RuinMarkers.Kind.PUMP, "engine");
    assertEquals(4, pumps.size());
    assertEquals(2, pumps.stream().filter(p -> p.turn() > 0).count(), "Two pumps turn one way, two the other");
    for (var pump : pumps) assertTrue(pump.block().startsWith("create:mechanical_pump[facing=up"), pump.toString());
    var parts = found.of(RuinMarkers.Kind.PART);
    assertEquals(4, parts.size(), "One missing piece per wheelhouse");
    assertEquals(3, parts.stream().map(RuinMarkers.Marker::block).map(b -> b.substring(0, b.indexOf('['))).distinct().count(),
        "The pieces differ: gearboxes, a shaft and a cogwheel");
    var sandbox = found.of(RuinMarkers.Kind.SANDBOX);
    assertEquals(1, sandbox.size());
    assertEquals(4.5, sandbox.getFirst().radius(), "A disc of nine blocks across");
    var seal = found.of(RuinMarkers.Kind.SEAL, "seal");
    assertEquals(1, seal.size());
    assertEquals("2", seal.getFirst().param("scroll", ""), "The bearing never places its ring back by itself");
    assertEquals(4, found.of(RuinMarkers.Kind.MODBLOCK).size(), "The ring plugs the four shafts only with Create");
    assertEquals(16, found.of(RuinMarkers.Kind.SLUICE).size());
    var levers = found.of(RuinMarkers.Kind.LEVER, "sluices");
    assertEquals(8, levers.size());
    assertEquals(Set.of("1", "2", "3", "4"), levers.stream().map(l -> l.param("sluice", "")).collect(java.util.stream.Collectors.toSet()));
    assertEquals(Set.of("engine", "house1", "house2", "house3", "house4"),
        found.of(RuinMarkers.Kind.NOTE).stream().map(n -> n.param("key", "")).collect(java.util.stream.Collectors.toSet()));
    assertEquals(1, found.of(RuinMarkers.Kind.RETURNS).size());
    assertEquals(Set.of("input", "pump", "seal"),
        found.of(RuinMarkers.Kind.PORT).stream().map(p -> p.param("role", "")).collect(java.util.stream.Collectors.toSet()));
    assertEquals(8, found.of(RuinMarkers.Kind.CHEST).stream()
        .filter(m -> m.param("loot", "").equals("entrelumen:chests/ruin_act2_wheelhouse")).count(), "A barrel of parts per house side");
    var en = json(RESOURCES.resolve("assets/entrelumen/lang/en_us.json"));
    var es = json(RESOURCES.resolve("assets/entrelumen/lang/es_es.json"));
    for (String key : List.of("engine", "house1", "house2", "house3", "house4"))
      for (var lang : List.of(en, es)) assertTrue(lang.has("entrelumen.ruin.note." + key), key);
    assertTrue(en.get("entrelumen.ruin.note.house2").getAsString().contains("%s"), "The pumps note names R");
    assertTrue(es.get("entrelumen.ruin.note.house2").getAsString().contains("%s"), "The pumps note names R");
    var sandboxTag = json(RESOURCES.resolve("data/entrelumen/tags/block/ruin/sandbox.json")).toString();
    for (String part : List.of("create:shaft", "create:gearbox", "create:sequenced_gearshift", "minecraft:redstone_wire"))
      assertTrue(sandboxTag.contains("\"" + part + "\""), part);
    for (String banned : List.of("create:creative_motor", "create:rotation_speed_controller", "create:hand_crank",
        "create:water_wheel", "create:large_water_wheel", "create:windmill_bearing", "create:steam_engine", "create:mechanical_bearing"))
      assertFalse(sandboxTag.contains("\"" + banned + "\""), banned);
    var solution = Path.of("src/fullpackGameTest/resources/data/entrelumen/ruin_solution/sunken_workshop.json");
    var builds = json(solution);
    assertEquals(128, builds.get("rpm").getAsInt(), "R for the pack's Create defaults");
    assertEquals(Set.of("correct", "wrong", "overstress", "seal"), builds.getAsJsonObject("builds").keySet());
  }

  @Test
  void theSanctuaryStonesHaveOneStrictOrder() throws Exception {
    var orders = read("entrelumen:ruins/twilight_sanctuary").of(RuinMarkers.Kind.BRAZIER, "stones").stream()
        .map(RuinMarkers.Marker::order).sorted().toList();
    assertFalse(orders.isEmpty());
    for (int i = 0; i < orders.size(); i++) assertEquals(i + 1, orders.get(i), "Each stone has its own turn");
  }

  private static JsonObject json(Path path) throws Exception {
    return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  @Test
  void piecesHaveTextModelsAndNoRecipe() throws Exception {
    var en = json(RESOURCES.resolve("assets/entrelumen/lang/en_us.json"));
    var es = json(RESOURCES.resolve("assets/entrelumen/lang/es_es.json"));
    for (String piece : KeyPieces.PIECES.keySet()) {
      for (var lang : List.of(en, es)) {
        assertTrue(lang.has("item.entrelumen." + piece), piece);
        assertTrue(lang.has("item.entrelumen." + piece + ".tooltip"), piece);
      }
      assertTrue(Files.exists(RESOURCES.resolve("assets/entrelumen/models/item/" + piece + ".json")), piece);
    }
    try (var recipes = Files.list(RESOURCES.resolve("data/entrelumen/recipe"))) {
      for (Path recipe : recipes.toList()) {
        String text = Files.readString(recipe, StandardCharsets.UTF_8);
        for (String piece : KeyPieces.PIECES.keySet())
          assertFalse(text.contains("\"entrelumen:" + piece + "\"") && text.contains("\"result\""),
              recipe + " makes or uses " + piece);
      }
    }
    for (String block : List.of("ruin_pedestal", "ruin_mirror", "ruin_socket", "ruin_hidden", "ruin_lock", "ruin_gate")) {
      assertTrue(Files.exists(RESOURCES.resolve("assets/entrelumen/blockstates/" + block + ".json")), block);
      assertTrue(en.has("block.entrelumen." + block) && es.has("block.entrelumen." + block), block);
    }
    for (var definition : RuinDefinitionsTest.shipped(mod -> true).values()) {
      String name = definition.name();
      for (var lang : List.of(en, es))
        for (String key : List.of("entrelumen.compass.objective." + name, "entrelumen.compass.objective." + name + ".why",
            "entrelumen.compass.lore." + name))
          assertTrue(lang.has(key), key);
      for (var challenge : definition.challenges().values())
        if (challenge.boss() != null)
          assertTrue(en.has(challenge.boss().name()) && es.has(challenge.boss().name()), challenge.boss().name());
    }
    var usable = json(RESOURCES.resolve("data/entrelumen/tags/block/protection/usable.json")).getAsJsonArray("values").toString();
    assertTrue(usable.contains("minecraft:lever") && usable.contains("#minecraft:buttons"),
        "Redstone locks need levers and buttons usable inside protected ruins");
  }
}
