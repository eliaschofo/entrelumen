package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The Ark's astrolabe as data ({@code data/entrelumen/ark_multiblock.json}) and its matching rules. */
class ArkMultiblockTest {
  private static final ArkMultiblock.Definition ARK = ArkMultiblock.builtin();
  /** Copper families for tests: weathered and waxed cut copper count as cut copper. */
  private static final Map<String, String> COPPER = Map.of(
      "minecraft:weathered_cut_copper", "minecraft:cut_copper",
      "minecraft:waxed_oxidized_cut_copper", "minecraft:cut_copper");

  private static ResourceLocation normalise(ResourceLocation id) {
    ResourceLocation plain = ArkMultiblock.withoutConnecting(id);
    return ResourceLocation.parse(COPPER.getOrDefault(plain.toString(), plain.toString()));
  }

  /** A world of block IDs and properties; unknown positions are air, {@code unloaded} ones unreadable. */
  private static final class World {
    final Map<BlockPos, String> blocks = new HashMap<>();
    final Map<BlockPos, Map<String, String>> properties = new HashMap<>();
    Set<BlockPos> unloaded = Set.of();

    ArkMultiblock.Matcher matcher() {
      return ArkMultiblock.matcher(pos -> unloaded.contains(pos) ? null
              : ResourceLocation.parse(blocks.getOrDefault(pos, "minecraft:air")),
          (pos, name) -> properties.getOrDefault(pos, Map.of()).get(name), ArkMultiblockTest::normalise);
    }

    /** Builds the whole shape for an anchor, as the drawings state it (stairs keep facing and half). */
    void build(ArkMultiblock.Anchor anchor, boolean beacons) {
      for (var part : ARK.parts()) {
        if (!part.required() && !(beacons && ArkMultiblock.BEACON.equals(part.slot()))) continue;
        BlockPos at = ArkMultiblock.world(anchor, part.offset());
        blocks.put(at, part.block().toString());
        properties.put(at, part.properties());
      }
    }
  }

  @Test
  void theShippedAstrolabeHasItsSlotsBeaconsAndCore() {
    assertEquals(Set.copyOf(ArkRules.SLOTS), ARK.slots().keySet());
    assertEquals(BlockPos.ZERO, ARK.slots().get(ArkMultiblock.CONTROLLER));
    assertEquals(4, ARK.beacons().size());
    assertEquals(Set.copyOf(ArkRules.moduleIds()), ARK.moduleSlots());
    long required = ARK.parts().stream().filter(ArkMultiblock.Part::required).count();
    assertEquals(required, ARK.core().size() + ArkRules.SLOTS.size());
    assertTrue(ARK.core().size() > 100, "the core is the platform, arches and columns");
    // Everything but the beacon places is required (Elias: no decoration).
    assertEquals(ARK.parts().size() - ARK.beacons().size(), required);
    // Stairs keep only their half: the game turns their corners.
    for (var part : ARK.parts())
      if (part.block().getPath().endsWith("_stairs")) assertEquals(Set.of("half"), part.properties().keySet());
    assertEquals(0, ARK.parts().stream().filter(part -> part.state().contains("[")
        && !part.block().getPath().endsWith("_stairs") && !part.properties().isEmpty()
        && part.properties().containsKey("shape")).count());
  }

  @Test
  void rejectsDefinitionsWithoutTheirSlotsOrWithDuplicates() {
    assertThrows(IllegalArgumentException.class, () -> ArkMultiblock.parse(JsonParser.parseString(
        "{\"blocks\":[{\"pos\":[0,0,0],\"block\":\"entrelumen:ark_controller\",\"required\":true,\"slot\":\"ark_controller\"}]}")));
    assertThrows(IllegalArgumentException.class, () -> ArkMultiblock.parse(JsonParser.parseString(
        "{\"blocks\":[{\"pos\":[0,0,0],\"block\":\"minecraft:stone\"},{\"pos\":[0,0,0],\"block\":\"minecraft:stone\"}]}")));
    assertThrows(IllegalArgumentException.class, () -> ArkMultiblock.parse(JsonParser.parseString(
        "{\"blocks\":[{\"pos\":[1,0,0],\"block\":\"minecraft:beacon\",\"required\":true,\"slot\":\"beacon\"}]}")));
  }

  @Test
  void aBuiltArkMatchesInEveryFacingAndFindsItsAnchorFromAnyModule() {
    for (int rotation = 0; rotation < 4; rotation++) {
      var anchor = new ArkMultiblock.Anchor(new BlockPos(100, 64, -40), rotation);
      var world = new World();
      world.build(anchor, false);
      var scan = ArkMultiblock.scan(ARK, anchor, world.matcher());
      assertTrue(scan.coreComplete(), "rotation " + rotation);
      assertEquals(Set.copyOf(ArkRules.SLOTS), scan.filledSlots());
      assertEquals(0, scan.beacons());
      for (String module : ArkRules.moduleIds()) {
        BlockPos at = ArkMultiblock.world(anchor, ARK.slots().get(module));
        assertEquals(anchor, ArkMultiblock.locate(ARK, module, at, world.matcher()), module + " r" + rotation);
        assertEquals(module, ArkMultiblock.slotAt(ARK, anchor, at));
      }
    }
  }

  @Test
  void missingCoreUnloadedPartsAndBeaconsAreReported() {
    var anchor = new ArkMultiblock.Anchor(new BlockPos(0, 70, 0), 1);
    var world = new World();
    world.build(anchor, true);
    assertEquals(4, ArkMultiblock.scan(ARK, anchor, world.matcher()).beacons());
    var corePart = ARK.core().getFirst();
    BlockPos hole = ArkMultiblock.world(anchor, corePart.offset());
    world.blocks.remove(hole);
    var holed = ArkMultiblock.scan(ARK, anchor, world.matcher());
    assertFalse(holed.coreComplete());
    assertEquals(java.util.List.of(hole), holed.missingCore());
    world.build(anchor, true);
    world.unloaded = Set.of(hole);
    var unread = ArkMultiblock.scan(ARK, anchor, world.matcher());
    assertTrue(unread.unloaded());
    assertFalse(unread.coreComplete());
    // A module out of its slot: the Ark stands, the module does not count.
    world.unloaded = Set.of();
    world.blocks.put(ArkMultiblock.world(anchor, ARK.slots().get("nature_module")), "minecraft:air");
    var withoutNature = ArkMultiblock.scan(ARK, anchor, world.matcher());
    assertTrue(withoutNature.coreComplete());
    assertFalse(withoutNature.filledSlots().contains("nature_module"));
  }

  @Test
  void stairsMatchAnyFacingAndShapeButKeepTheirHalf() {
    var anchor = new ArkMultiblock.Anchor(BlockPos.ZERO, 0);
    var stair = ARK.parts().stream().filter(part -> part.block().getPath().endsWith("_stairs")).findFirst().orElseThrow();
    var world = new World();
    world.build(anchor, false);
    BlockPos at = ArkMultiblock.world(anchor, stair.offset());
    world.properties.put(at, Map.of("half", stair.properties().get("half"), "facing", "west", "shape", "outer_left"));
    assertTrue(ArkMultiblock.scan(ARK, anchor, world.matcher()).coreComplete());
    world.properties.put(at, Map.of("half", "top".equals(stair.properties().get("half")) ? "bottom" : "top"));
    assertTrue(ArkMultiblock.scan(ARK, anchor, world.matcher()).missingCore().contains(at));
  }

  @Test
  void copperWeathersAndRechiseledConnectsWithoutBreakingTheArk() {
    var anchor = new ArkMultiblock.Anchor(BlockPos.ZERO, 2);
    var world = new World();
    world.build(anchor, false);
    for (var part : ARK.core()) {
      BlockPos at = ArkMultiblock.world(anchor, part.offset());
      if (part.block().toString().equals("minecraft:cut_copper")) world.blocks.put(at, "minecraft:waxed_oxidized_cut_copper");
      if (part.block().getNamespace().equals("rechiseled")) world.blocks.put(at, part.block() + "_connecting");
    }
    assertTrue(ArkMultiblock.scan(ARK, anchor, world.matcher()).coreComplete());
    assertEquals(ResourceLocation.parse("rechiseled:amethyst_block_polished_stairs"),
        ArkMultiblock.withoutConnecting(ResourceLocation.parse("rechiseled:amethyst_block_polished_stairs_connecting")));
    assertEquals(ResourceLocation.parse("minecraft:block_connecting"),
        ArkMultiblock.withoutConnecting(ResourceLocation.parse("minecraft:block_connecting")));
  }

  @Test
  void facingsTurnWithTheArk() {
    assertEquals("north", ArkMultiblock.turn("north", 0));
    assertEquals("east", ArkMultiblock.turn("north", 1));
    assertEquals("south", ArkMultiblock.turn("north", 2));
    assertEquals("west", ArkMultiblock.turn("north", 3));
    assertEquals("up", ArkMultiblock.turn("up", 1));
    assertEquals(Direction.EAST.getSerializedName(), ArkMultiblock.turn("south", 3));
    var offset = new BlockPos(3, 1, -2);
    for (int rotation = 0; rotation < 4; rotation++) {
      var anchor = ArkMultiblock.anchorFor(ARK, "habitation_module", new BlockPos(10, 5, 10), rotation);
      assertEquals(new BlockPos(10, 5, 10), ArkMultiblock.world(anchor, ARK.slots().get("habitation_module")));
      assertTrue(ArkMultiblock.contains(ARK, anchor, ArkMultiblock.world(anchor, ARK.core().getFirst().offset())));
      assertFalse(ArkMultiblock.contains(ARK, anchor, anchor.origin().offset(offset).above(40)));
    }
  }
}
