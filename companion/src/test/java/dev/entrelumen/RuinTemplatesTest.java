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
          assertTrue(definition.challenges().containsKey(marker.challenge()), id + ": marker for unknown challenge " + marker);
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
