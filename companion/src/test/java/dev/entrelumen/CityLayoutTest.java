package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class CityLayoutTest {
  @Test
  void pieceNamesMapToGridCells() {
    assertArrayEquals(new int[] {0, 0}, CityLayout.gridOf("city").orElseThrow());
    assertArrayEquals(new int[] {2, 13}, CityLayout.gridOf("piece_2_13").orElseThrow());
    assertTrue(CityLayout.gridOf("piece_a_1").isEmpty());
    assertTrue(CityLayout.gridOf("town_hall").isEmpty());
  }

  @Test
  void aSinglePieceIsCentredOnTheOriginAtBaseHeight() {
    var placements = CityLayout.place(List.of(new CityLayout.Piece("piece_0_0", 0, 0, 61, 12, 61)));
    var only = placements.getFirst();
    assertEquals(-30, only.x());
    assertEquals(-30, only.z());
    assertEquals(CityLayout.BASE_Y, only.y());
  }

  @Test
  void gridColumnsTakeTheWidestPieceAndTheWholeGridIsCentred() {
    var pieces = List.of(
        new CityLayout.Piece("piece_0_0", 0, 0, 100, 40, 60),
        new CityLayout.Piece("piece_1_0", 1, 0, 80, 20, 70),
        new CityLayout.Piece("piece_0_1", 0, 1, 90, 30, 50),
        new CityLayout.Piece("piece_1_1", 1, 1, 80, 10, 50));
    var placements = CityLayout.place(pieces);
    // Columns 100 + 80 = 180 wide, rows 70 + 50 = 120 deep.
    var byName = new java.util.HashMap<String, CityLayout.Placement>();
    placements.forEach(p -> byName.put(p.piece().name(), p));
    assertEquals(-90, byName.get("piece_0_0").x());
    assertEquals(10, byName.get("piece_1_0").x());
    assertEquals(-60, byName.get("piece_0_0").z());
    assertEquals(10, byName.get("piece_0_1").z());
    assertEquals(-90, byName.get("piece_0_1").x());
    assertEquals(10, byName.get("piece_1_1").z());
    // Pieces larger than a structure block (48) are fine.
    assertTrue(pieces.stream().allMatch(p -> p.sizeX() > 48));
  }

  @Test
  void cityAndPiecesCannotMix() {
    assertThrows(IllegalArgumentException.class, () -> CityLayout.place(List.of(
        new CityLayout.Piece("city", 0, 0, 10, 10, 10), new CityLayout.Piece("piece_1_0", 1, 0, 10, 10, 10))));
  }

  @Test
  void cutsFollowTheWorldGrid() {
    // A piece starting at world x = -52 is cut at -48, -32, -16, 0, 16...
    var cuts = CityLayout.cuts(105, -52, 16);
    assertArrayEquals(new int[] {0, 4}, cuts.get(0));
    assertArrayEquals(new int[] {4, 20}, cuts.get(1));
    assertArrayEquals(new int[] {100, 105}, cuts.getLast());
    assertEquals(105, cuts.stream().mapToInt(c -> c[1] - c[0]).sum());
    assertEquals(List.of(), CityLayout.cuts(0, 5, 16));
    assertEquals(1, CityLayout.cuts(16, 64, 16).size());
  }

  @Test
  void cubesCoverThePieceExactlyOnceBottomUpPerColumn() {
    var cubes = CityLayout.cubes(40, 20, 17, -8, 64, 0, 16);
    long volume = cubes.stream().mapToLong(c -> (long) c.width() * c.height() * c.depth()).sum();
    assertEquals(40L * 20 * 17, volume);
    // x cuts: [0,8) [8,24) [24,40); y cuts: [0,16) [16,20); z cuts: [0,16) [16,17).
    assertEquals(3 * 2 * 2, cubes.size());
    assertEquals(0, cubes.get(0).fromY());
    assertEquals(16, cubes.get(1).fromY());
    assertEquals(cubes.get(0).fromX(), cubes.get(1).fromX(), "the same column continues upward");
    assertEquals(8, cubes.get(2).fromX());
  }

  @Test
  void markersParseLeniently() {
    assertEquals(CityLayout.Marker.ARRIVAL, CityLayout.Marker.parse(" Arrival ").orElseThrow());
    assertEquals(CityLayout.Marker.TOWN_HALL_PORTAL, CityLayout.Marker.parse("entrelumen:town_hall_portal").orElseThrow());
    assertTrue(CityLayout.Marker.parse("dragon").isEmpty());
    assertTrue(CityLayout.Marker.MAYOR.npc() && CityLayout.Marker.PRIEST.npc() && !CityLayout.Marker.ARRIVAL.npc());
  }

  @Test
  void commerceMarkersCarryTheirArgument() {
    assertEquals(new CityLayout.Parsed(CityLayout.Marker.SHOP, "bookstore"), CityLayout.parseMarker("shop:bookstore").orElseThrow());
    assertEquals(new CityLayout.Parsed(CityLayout.Marker.SHOP, "curiosities"),
        CityLayout.parseMarker(" Entrelumen:Shop:Curiosities ").orElseThrow());
    assertEquals(new CityLayout.Parsed(CityLayout.Marker.SIDEQUEST, "37_inn"), CityLayout.parseMarker("sidequest:37_inn").orElseThrow());
    assertEquals(new CityLayout.Parsed(CityLayout.Marker.EASTER, "fountain_coin"), CityLayout.parseMarker("easter:fountain_coin").orElseThrow());
    assertEquals(new CityLayout.Parsed(CityLayout.Marker.RESIDENT, ""), CityLayout.parseMarker("resident").orElseThrow());
    assertEquals(new CityLayout.Parsed(CityLayout.Marker.ARRIVAL, ""), CityLayout.parseMarker("arrival").orElseThrow());
    assertEquals(CityLayout.Marker.SHOP, CityLayout.Marker.parse("shop:maps").orElseThrow());
    for (String bad : List.of("shop", "shop:", "shop:Two Words", "resident:1", "arrival:x", "easter:", "sidequest", "bogus:1"))
      assertTrue(CityLayout.parseMarker(bad).isEmpty(), bad + " should be unknown");
  }

  @Test
  void plotsAreSixteenSquareWithRoomToDigAndBuild() {
    var box = CityLayout.plotBox(10, 69, -20);
    assertEquals(16, box.maxX() - box.minX() + 1);
    assertEquals(16, box.maxZ() - box.minZ() + 1);
    assertEquals(69 - CityLayout.PLOT_DEPTH, box.minY());
    assertEquals(69 + CityLayout.PLOT_HEIGHT, box.maxY());
  }

  @Test
  void plotAllocationKeepsOnePerTeamAndReportsFull() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    List<UUID> owners = new ArrayList<>(java.util.Arrays.asList(null, null));
    assertEquals(0, CityLayout.plotFor(owners, a));
    owners.set(0, a);
    assertEquals(0, CityLayout.plotFor(owners, a), "a team keeps its plot");
    assertEquals(1, CityLayout.plotFor(owners, b));
    owners.set(1, b);
    assertEquals(-1, CityLayout.plotFor(owners, c), "full city");
  }

  @Test
  void borderKeepsTheCityPlusMargin() {
    assertEquals(CityLayout.MIN_BORDER_RADIUS, CityLayout.borderRadius(new ProtectionRules.Box(-30, 64, -30, 30, 75, 30)));
    assertEquals(301 + CityLayout.BORDER_MARGIN,
        CityLayout.borderRadius(new ProtectionRules.Box(-300, 64, -10, 300, 75, 10)));
  }

  private static ListTag ints(int... values) {
    ListTag list = new ListTag();
    for (int value : values) list.add(IntTag.valueOf(value));
    return list;
  }

  @Test
  void partitionMovesBlocksIntoSlicesAndTurnsMarkersIntoAir() {
    CompoundTag tag = new CompoundTag();
    tag.put("size", ints(40, 3, 5));
    ListTag palette = new ListTag();
    CompoundTag stone = new CompoundTag();
    stone.putString("Name", "minecraft:stone");
    CompoundTag marker = new CompoundTag();
    marker.putString("Name", "minecraft:structure_block");
    palette.add(stone);
    palette.add(marker);
    tag.put("palette", palette);
    ListTag blocks = new ListTag();
    for (int x = 0; x < 40; x++) {
      CompoundTag block = new CompoundTag();
      block.put("pos", ints(x, 0, 2));
      block.putInt("state", 0);
      blocks.add(block);
    }
    CompoundTag data = new CompoundTag();
    data.put("pos", ints(35, 1, 4));
    data.putInt("state", 1);
    CompoundTag nbt = new CompoundTag();
    nbt.putString("mode", "DATA");
    nbt.putString("metadata", "arrival");
    data.put("nbt", nbt);
    blocks.add(data);
    tag.put("blocks", blocks);
    ListTag entities = new ListTag();
    CompoundTag frame = new CompoundTag();
    frame.put("blockPos", ints(33, 1, 1));
    ListTag pos = new ListTag();
    pos.add(DoubleTag.valueOf(33.5));
    pos.add(DoubleTag.valueOf(1.0));
    pos.add(DoubleTag.valueOf(1.5));
    frame.put("pos", pos);
    frame.put("nbt", new CompoundTag());
    entities.add(frame);
    tag.put("entities", entities);
    tag.putInt("DataVersion", 3955);

    var partition = SolsticioCity.partition(tag, 32, 0, 64, 0);
    assertEquals(2, partition.slices().size());
    assertEquals(List.of("arrival"), partition.markerNames());
    assertArrayEquals(new int[] {35, 1, 4}, partition.markerPositions().getFirst());
    CompoundTag east = partition.slices().get(1);
    assertEquals(8, east.getList("size", Tag.TAG_INT).getInt(0));
    assertArrayEquals(new int[] {32, 0, 0}, partition.sliceOrigins().get(1));
    assertEquals(32, partition.blockCounts().get(0));
    assertEquals(9, partition.blockCounts().get(1), "8 stones and the marker, now air");
    ListTag eastBlocks = east.getList("blocks", Tag.TAG_COMPOUND);
    CompoundTag air = eastBlocks.getCompound(eastBlocks.size() - 1);
    assertEquals("minecraft:air", east.getList("palette", Tag.TAG_COMPOUND).getCompound(air.getInt("state")).getString("Name"));
    assertEquals(3, air.getList("pos", Tag.TAG_INT).getInt(0));
    assertFalse(air.contains("nbt"));
    CompoundTag movedFrame = east.getList("entities", Tag.TAG_COMPOUND).getCompound(0);
    assertEquals(1, movedFrame.getList("blockPos", Tag.TAG_INT).getInt(0));
    assertEquals(1.5, movedFrame.getList("pos", Tag.TAG_DOUBLE).getDouble(0));
    assertEquals(0, partition.slices().get(0).getList("entities", Tag.TAG_COMPOUND).size());
    assertEquals(3955, east.getInt("DataVersion"));
  }

  @Test
  void solsticioDataSurvivesARestart() {
    var data = new SolsticioData();
    data.status = SolsticioData.Status.READY;
    data.provisional = true;
    data.placements = 1;
    data.slicesDone = data.slicesTotal = 4;
    data.templates.add("piece_0_0@61x12x61");
    data.footprint = new ProtectionRules.Box(-30, 64, -30, 30, 75, 30);
    data.borderRadius = 128;
    data.arrival = new BlockPos(0, 69, 28);
    data.portal = new BlockPos(0, 69, 0);
    data.npcs.put("mayor", new BlockPos(0, 69, 2));
    var plot = new SolsticioData.Plot(new BlockPos(-25, 69, -25));
    plot.owner = UUID.randomUUID();
    plot.ownerName = "Tester";
    data.plots.add(plot);
    data.plots.add(new SolsticioData.Plot(new BlockPos(10, 69, -25)));
    data.portalRelics.add(2);
    data.forgedKeys.add(plot.owner);
    UUID player = UUID.randomUUID();
    data.returns.put(player, new SolsticioData.ReturnPoint(net.minecraft.world.level.Level.OVERWORLD,
        new BlockPos(5, 70, 5), 90f));
    var loaded = SolsticioData.load(data.save(new CompoundTag(), null), null);
    assertTrue(loaded.ready());
    assertTrue(loaded.provisional);
    assertEquals(1, loaded.placements);
    assertEquals(data.footprint, loaded.footprint);
    assertEquals(data.arrival, loaded.arrival);
    assertEquals(data.npcs, loaded.npcs);
    assertEquals(2, loaded.plots.size());
    assertEquals(plot.owner, loaded.plots.get(0).owner);
    assertNull(loaded.plots.get(1).owner);
    assertEquals(data.portalRelics, loaded.portalRelics);
    assertEquals(data.forgedKeys, loaded.forgedKeys);
    assertEquals(new BlockPos(5, 70, 5), loaded.returns.get(player).pos());
    assertEquals(0, loaded.plotOf(plot.owner));
  }
}
