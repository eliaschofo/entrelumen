package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuinMarkersTest {
  private static RuinMarkers.Marker parse(String metadata) {
    return RuinMarkers.parse(metadata).orElseThrow();
  }

  @Test
  void everyKindParsesWithItsKeys() {
    var brazier = parse("brazier challenge=braziers order=3 block=minecraft:campfire[facing=west,lit=false]");
    assertEquals(RuinMarkers.Kind.BRAZIER, brazier.kind());
    assertEquals("braziers", brazier.challenge());
    assertEquals(3, brazier.order());
    assertEquals("minecraft:campfire[facing=west,lit=false]", brazier.block());
    assertEquals(1, parse("brazier challenge=lamps").order(), "Order defaults to 1: any order among equals");
    assertEquals(RuinMarkers.Kind.ARRIVAL, parse("spawn").kind(), "The start ruin's marker still reads");
    assertEquals(RuinMarkers.Kind.PEDESTAL, parse("entrelumen:pedestal").kind());
    var socket = parse("socket challenge=saplings item=#minecraft:saplings count=2 look=pot");
    assertEquals(2, socket.count());
    assertEquals("#minecraft:saplings", socket.param("item", ""));
    var gate = parse("gate id=cellar look=moss_block climb=true");
    assertEquals("cellar", gate.param("id", ""));
    assertEquals("", gate.challenge());
    assertArrayEquals(new int[] {25, 5, 25}, parse("drain challenge=sluices size=25,5,25").size());
    for (String metadata : List.of("chest loot=entrelumen:chests/ruin_act2_workshop block=minecraft:barrel[facing=up,open=false]",
        "lamp challenge=braziers", "mirror challenge=mirrors facing=se", "receptor challenge=mirrors block=minecraft:glass",
        "lever challenge=sluices on=false", "lock challenge=core", "hidden challenge=wall look=calcite",
        "boss challenge=keeper", "lore radius=8 height=5", "arrival", "ground block=minecraft:tuff_bricks"))
      assertEquals(parse(metadata), parse(parse(metadata).metadata()), "Round trip of " + metadata);
  }

  @Test
  void otherMetadataIsNotARuinMarker() {
    assertTrue(RuinMarkers.parse("").isEmpty());
    assertTrue(RuinMarkers.parse(null).isEmpty());
    assertTrue(RuinMarkers.parse("mayor").isEmpty());
    assertTrue(RuinMarkers.parse("shop:baker").isEmpty());
  }

  @Test
  void malformedMarkersSayWhatIsWrong() {
    for (String metadata : List.of("brazier order=2", "brazier challenge=Bad order=1", "brazier challenge=a order=0",
        "brazier challenge=a order=x", "mirror challenge=a", "mirror challenge=a facing=up", "socket challenge=a count=0",
        "socket challenge=a item=Not/An:Id", "gate look=seal", "gate id=a look=glass", "lore radius=99",
        "drain challenge=a size=1,2", "drain challenge=a size=0,2,2", "chest facing=north", "pedestal challenge=a",
        "lever challenge=a on=maybe", "brazier challenge=a order=1 order=2", "brazier challenge=a stray",
        "lamp challenge=a block=minecraft:bulb[lit]"))
      assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse(metadata), metadata);
  }

  @Test
  void mirrorsAimAtTheirReceptor() {
    assertEquals(0, RuinMarkers.direction(0, -5), "north is -z");
    assertEquals(2, RuinMarkers.direction(5, 0), "east is +x");
    assertEquals(7, RuinMarkers.direction(-10, -10), "north-west");
    assertEquals(3, RuinMarkers.direction(10, 10), "south-east");
    assertEquals(-1, RuinMarkers.direction(0, 0));
    // The cliff observatory: a mirror on the south-east turret aims north-west at the telescope.
    assertTrue(RuinMarkers.aimed(7, 10, 10, 0, 0));
    assertFalse(RuinMarkers.aimed(3, 10, 10, 0, 0));
    int facing = 3;
    for (int click = 0; click < 4; click++) facing = RuinMarkers.turn(facing);
    assertEquals(7, facing, "Four clicks turn a mirror from its opposite to its target");
    assertEquals(0, RuinMarkers.turn(7));
    assertTrue(RuinMarkers.aimed(5, 3, 3, 3, 3), "A receptor right above any mirror is always reached");
  }

  @Test
  void kindsKnowWhatTheyHold() {
    assertTrue(RuinMarkers.Kind.MIRROR.ownBlock());
    assertFalse(RuinMarkers.Kind.BRAZIER.ownBlock(), "Braziers keep the art's block (campfire, bulb, stone)");
    assertTrue(RuinMarkers.Kind.DRAIN.challenge());
    assertFalse(RuinMarkers.Kind.GATE.challenge(), "Gates open by a definition's list, not one challenge");
    assertEquals(Map.of("challenge", "a", "order", "2"), parse("brazier challenge=a order=2").params());
  }
  @Test
  void theEngineMarkersParse() {
    var sandbox = parse("sandbox size=9,5,9 radius=4.5");
    assertArrayEquals(new int[] {9, 5, 9}, sandbox.size());
    assertEquals(4.5, sandbox.radius());
    assertEquals(0, parse("sandbox size=3,3,3").radius(), "No radius keeps the whole box");
    var pump = parse("pump challenge=engine turn=- intake=2,-6,1 block=create:mechanical_pump[facing=up,waterlogged=false]");
    assertEquals(-1, pump.turn());
    assertArrayEquals(new int[] {2, -6, 1}, pump.triple("intake", "0,0,0"));
    assertEquals(1, parse("pump challenge=engine turn=+ block=create:mechanical_pump[facing=up]").turn());
    assertTrue(pump.kind().challenge());
    assertEquals("create:gearbox[axis=z]", parse("part block=create:gearbox[axis=z]").block());
    assertFalse(RuinMarkers.Kind.PART.ownBlock(), "A part names the piece it takes");
    assertEquals("2", parse("seal challenge=seal scroll=2 block=create:mechanical_bearing[facing=down]").param("scroll", ""));
    assertEquals("3", parse("lever challenge=sluices sluice=3").param("sluice", ""));
    assertEquals("pump", parse("port role=pump block=create:shaft[axis=y]").param("role", ""));
    for (String metadata : List.of("sandbox size=9,5,9 radius=4.5", "part block=create:shaft[axis=y,waterlogged=false]",
        "pump challenge=engine turn=- intake=2,-6,1 block=create:mechanical_pump[facing=up]", "port role=input",
        "seal challenge=seal scroll=2 block=create:mechanical_bearing[facing=down]", "sluice id=1 block=minecraft:waxed_copper_block",
        "wheel block=create:large_water_wheel[axis=z,extension=false]", "note key=house2 block=minecraft:lectern[facing=north]",
        "returns block=minecraft:barrel[facing=up,open=false]", "modblock mod=create block=minecraft:waxed_copper_block"))
      assertEquals(parse(metadata), parse(parse(metadata).metadata()), "Round trip of " + metadata);
    for (String metadata : List.of("sandbox size=9,5", "sandbox size=9,5,9 radius=0", "sandbox size=9,5,9 radius=wide",
        "part", "pump challenge=engine block=create:mechanical_pump", "pump challenge=engine turn=x block=create:mechanical_pump",
        "pump challenge=engine turn=+ intake=1,2 block=create:mechanical_pump", "port role=output", "port",
        "seal challenge=seal block=create:mechanical_bearing scroll=99", "sluice", "sluice id=A", "note",
        "modblock block=minecraft:stone", "modblock mod=create", "wheel", "lever challenge=a sluice=Bad"))
      assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse(metadata), metadata);
  }
  @Test
  void theRelayMarkersParse() {
    var light = parse("light challenge=relay floor=1 hand=true block=minecraft:campfire[lit=false]");
    assertEquals(1, light.floor());
    var vitral = parse("vitral challenge=relay floor=4 colour=green turn=pass solve=south block=minecraft:green_stained_glass");
    assertEquals("south", vitral.param("solve", ""));
    assertEquals(4, vitral.floor());
    assertEquals("red+green", parse("receptor challenge=relay floor=2 target=red+green").param("target", ""));
    assertEquals("n", parse("mirror challenge=relay floor=3 facing=s solve=n").param("solve", ""));
    assertTrue(parse("collector challenge=relay floor=4 block=minecraft:glass").kind().challenge());
    for (String metadata : List.of("light challenge=relay", "light challenge=relay floor=0", "vitral challenge=relay floor=1",
        "vitral challenge=relay floor=1 colour=Red", "vitral challenge=relay floor=1 colour=red turn=left",
        "receptor challenge=relay target=red,green", "mirror challenge=relay facing=n solve=up", "collector challenge=relay"))
      assertThrows(IllegalArgumentException.class, () -> RuinMarkers.parse(metadata), metadata);
  }
}
