package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** The Signal Tower's light relay: the rules, and every shipped floor with its markers' solution. */
class LightRelayTest {
  static LightRelay.Cell at(int x, int z) {
    return new LightRelay.Cell(x, 0, z);
  }

  static LightRelay.Vitral vitral(String colour, LightRelay.Turn turn) {
    return new LightRelay.Vitral(colour, turn);
  }

  @Test
  void coloursAddUpAndOnlyTheExactSetLights() {
    // A light at the origin; red then green east of it; the receptor over the green vitral.
    Map<LightRelay.Cell, LightRelay.Element> elements = new HashMap<>();
    elements.put(at(1, 0), vitral("red", LightRelay.Turn.PASS));
    elements.put(at(2, 0), vitral("green", LightRelay.Turn.UP));
    var floor = new LightRelay.Floor(at(0, 0), elements, new LightRelay.Cell(2, 5, 0), Set.of("red", "green"));
    var trace = LightRelay.trace(floor, cell -> cell.y() < 8 && Math.abs(cell.x()) < 6 && Math.abs(cell.z()) < 6);
    assertEquals(Set.of("red", "green"), trace.received());
    assertTrue(LightRelay.satisfied(floor, trace));
    // An amber pane on the way adds its colour: too much fails.
    elements.put(at(1, 0), vitral("orange", LightRelay.Turn.PASS));
    elements.put(at(-1, 0), vitral("red", LightRelay.Turn.EAST));
    var decoy = new LightRelay.Floor(at(0, 0), elements, new LightRelay.Cell(2, 5, 0), Set.of("red", "green"));
    var wrong = LightRelay.trace(decoy, cell -> cell.y() < 8 && Math.abs(cell.x()) < 6 && Math.abs(cell.z()) < 6);
    assertEquals(Set.of("orange", "green"), wrong.received(), "The light going west turns back and is lost");
    assertFalse(LightRelay.satisfied(decoy, wrong));
  }

  @Test
  void mirrorsTurnAQuarterAndCollectorsSendTheUnionUp() {
    Map<LightRelay.Cell, LightRelay.Element> elements = new HashMap<>();
    elements.put(at(1, 0), vitral("red", LightRelay.Turn.SOUTH));
    elements.put(at(1, 2), vitral("blue", LightRelay.Turn.EAST));
    elements.put(at(3, 2), new LightRelay.Mirror(LightRelay.Dir.NORTH));
    elements.put(at(3, 0), new LightRelay.Collector());
    elements.put(at(-1, 0), vitral("green", LightRelay.Turn.PASS));
    var floor = new LightRelay.Floor(at(0, 0), elements, new LightRelay.Cell(3, 6, 0), Set.of("red", "blue"));
    var open = (java.util.function.Predicate<LightRelay.Cell>) cell -> Math.abs(cell.x()) < 5 && Math.abs(cell.z()) < 5
        && cell.y() < 10;
    assertEquals(Set.of("red", "blue"), LightRelay.trace(floor, open).received());
    // A mirror facing along the light (or back) blocks it.
    elements.put(at(3, 2), new LightRelay.Mirror(LightRelay.Dir.EAST));
    assertTrue(LightRelay.trace(new LightRelay.Floor(at(0, 0), elements, new LightRelay.Cell(3, 6, 0),
        Set.of("red", "blue")), open).received().isEmpty());
    // Beams that reach the collector by two ways merge: red straight east, green and blue round the mirror.
    elements.put(at(3, 2), new LightRelay.Mirror(LightRelay.Dir.NORTH));
    elements.put(at(1, 0), vitral("red", LightRelay.Turn.PASS));
    elements.remove(at(-1, 0));
    elements.put(at(0, 2), vitral("green", LightRelay.Turn.EAST));
    assertEquals(Set.of("red", "green", "blue"), LightRelay.trace(new LightRelay.Floor(at(0, 0), elements,
        new LightRelay.Cell(3, 6, 0), Set.of()), open).received());
  }

  record Template(Map<LightRelay.Cell, RuinMarkers.Marker> markers, Map<LightRelay.Cell, String> blocks) {}

  static Template tower() throws Exception {
    CompoundTag tag;
    try (InputStream stream = Files.newInputStream(RuinTemplatesTest.RESOURCES.resolve(
        "data/entrelumen/structure/ruins/signal_tower.nbt"))) {
      tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
    }
    var palette = tag.getList("palette", Tag.TAG_COMPOUND);
    Map<LightRelay.Cell, RuinMarkers.Marker> markers = new HashMap<>();
    Map<LightRelay.Cell, String> blocks = new HashMap<>();
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      var block = (CompoundTag) element;
      var p = block.getList("pos", Tag.TAG_INT);
      var cell = new LightRelay.Cell(p.getInt(0), p.getInt(1), p.getInt(2));
      var nbt = block.getCompound("nbt");
      if ("DATA".equals(nbt.getString("mode")))
        RuinMarkers.parse(nbt.getString("metadata")).ifPresent(marker -> markers.put(cell, marker));
      else blocks.put(cell, palette.getCompound(block.getInt("state")).getString("Name"));
    }
    return new Template(markers, blocks);
  }

  /** The shipped tower, floor by floor: the markers' solution lights each receptor, the start does not. */
  @Test
  void everyTowerFloorLightsWithItsSolutionOnly() throws Exception {
    var tower = tower();
    Map<Integer, LightRelay.Cell> lights = new TreeMap<>();
    Map<Integer, LightRelay.Cell> receptors = new HashMap<>();
    Map<Integer, Set<String>> targets = new HashMap<>();
    tower.markers().forEach((cell, marker) -> {
      if (marker.kind() == RuinMarkers.Kind.LIGHT) lights.put(marker.floor(), cell);
      if (marker.kind() == RuinMarkers.Kind.RECEPTOR && marker.challenge().equals("relay")) {
        receptors.put(marker.floor(), cell);
        targets.put(marker.floor(), LightRelay.colours(marker.param("target", "")));
      }
    });
    assertEquals(List.of(1, 2, 3, 4), List.copyOf(lights.keySet()), "Four floors");
    var open = (java.util.function.Predicate<LightRelay.Cell>) cell -> {
      String block = tower.blocks().get(cell);
      return block == null || block.endsWith(":air") || block.endsWith("cave_air") || block.endsWith("vine")
          || block.endsWith("torch");
    };
    for (int floor : lights.keySet()) {
      Map<LightRelay.Cell, LightRelay.Element> start = new HashMap<>(), solved = new HashMap<>();
      for (var entry : tower.markers().entrySet()) {
        var marker = entry.getValue();
        if (marker.floor() != floor || !marker.challenge().equals("relay")) continue;
        switch (marker.kind()) {
          case VITRAL -> {
            start.put(entry.getKey(), vitral(marker.param("colour", ""), LightRelay.Turn.of(marker.param("turn", "pass")).orElseThrow()));
            solved.put(entry.getKey(), vitral(marker.param("colour", ""),
                LightRelay.Turn.of(marker.param("solve", marker.param("turn", "pass"))).orElseThrow()));
          }
          case MIRROR -> {
            start.put(entry.getKey(), new LightRelay.Mirror(LightRelay.Dir.of(marker.param("facing", "n")).orElseThrow()));
            solved.put(entry.getKey(), new LightRelay.Mirror(
                LightRelay.Dir.of(marker.param("solve", marker.param("facing", "n"))).orElseThrow()));
          }
          case COLLECTOR -> {
            start.put(entry.getKey(), new LightRelay.Collector());
            solved.put(entry.getKey(), new LightRelay.Collector());
          }
          default -> {}
        }
      }
      var answer = new LightRelay.Floor(lights.get(floor), solved, receptors.get(floor), targets.get(floor));
      assertTrue(LightRelay.satisfied(answer, LightRelay.trace(answer, open)), "Floor " + floor + " with its solution");
      var begin = new LightRelay.Floor(lights.get(floor), start, receptors.get(floor), targets.get(floor));
      assertFalse(LightRelay.satisfied(begin, LightRelay.trace(begin, open)), "Floor " + floor + " as it starts");
      if (floor == 4)
        assertEquals(Set.of("red", "green", "orange"), LightRelay.trace(begin, open).received(),
            "The amber decoy reaches the lens before the answer");
    }
  }
}
