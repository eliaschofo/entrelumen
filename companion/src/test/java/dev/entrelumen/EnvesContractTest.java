package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.entrelumen.EnvesLayout.Role;
import dev.entrelumen.EnvesMarkers.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * The marker contract, the committed templates and the data files: what the second worker and the
 * engine rely on. Reads the resources from disk, no game needed.
 */
class EnvesContractTest {
  private static final Path DATA = Path.of("src/main/resources/data/entrelumen");
  private static final Path TEMPLATES = DATA.resolve("structure/enves/osarios");

  private record Template(Map<List<Integer>, String> blocks, List<EnvesTemplates.Local> markers) {
    /** Air inside the room is not written: an absent block there is air. */
    String at(int x, int y, int z) {
      return blocks.getOrDefault(List.of(x, y, z), y >= 1 && y <= 9 ? "minecraft:air" : "");
    }

    long count(Kind kind) {
      return markers.stream().filter(m -> m.marker().kind() == kind).count();
    }
  }

  private static Template read(String name) throws IOException {
    CompoundTag tag = NbtIo.readCompressed(TEMPLATES.resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    ListTag size = tag.getList("size", Tag.TAG_INT);
    assertEquals(List.of(19, 12, 19), List.of(size.getInt(0), size.getInt(1), size.getInt(2)), name);
    List<EnvesTemplates.Local> markers = new ArrayList<>();
    EnvesTemplates.prepare(tag, Map.of(), markers, name);
    ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
    Map<List<Integer>, String> blocks = new HashMap<>();
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      CompoundTag block = (CompoundTag) element;
      ListTag pos = block.getList("pos", Tag.TAG_INT);
      CompoundTag state = palette.getCompound(block.getInt("state"));
      String props = state.getCompound("Properties").getAllKeys().stream().sorted()
          .map(k -> k + "=" + state.getCompound("Properties").getString(k)).collect(Collectors.joining(","));
      blocks.put(List.of(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
          state.getString("Name") + (props.isEmpty() ? "" : "[" + props + "]"));
    }
    return new Template(blocks, markers);
  }

  @Test
  void markerMetadataParsesOnlyWithinTheContract() {
    assertEquals(Kind.ARRIVAL, EnvesMarkers.parse("enves:arrival").orElseThrow().kind());
    assertEquals("vault", EnvesMarkers.parse("ENVES:chest:vault/north").orElseThrow().head());
    assertEquals("north", EnvesMarkers.parse("enves:chest:vault/north").orElseThrow().tail());
    assertEquals("champion", EnvesMarkers.parse("enves:encounter:champion").orElseThrow().argument());
    assertTrue(EnvesMarkers.parse("enves:encounter").isPresent(), "the argument is optional there");
    assertTrue(EnvesMarkers.parse("enves:chest").isEmpty(), "a chest needs its kind");
    assertTrue(EnvesMarkers.parse("enves:arrival:x").isEmpty(), "the arrival takes none");
    assertTrue(EnvesMarkers.parse("entrelumen:spawn").isEmpty());
    assertTrue(EnvesMarkers.parse("enves:unknown").isEmpty());
    for (Kind kind : Kind.values()) {
      String metadata = EnvesMarkers.PREFIX + kind.id + (kind.argument == EnvesMarkers.Argument.REQUIRED ? ":x" : "");
      assertEquals(kind, EnvesMarkers.parse(metadata).orElseThrow().kind());
    }
  }

  @Test
  void everyCellTheLayoutCanDrawHasATemplate() {
    for (Role role : Role.values())
      for (int doors = 1; doors < 16; doors++)
        for (int variant = 0; variant < EnvesTemplates.variants(role.id); variant++) {
          String name = EnvesTemplates.name(role.id, doors, variant);
          assertTrue(Files.isRegularFile(TEMPLATES.resolve(name + ".nbt")), name);
        }
    assertTrue(Files.isRegularFile(TEMPLATES.resolve("vestibule_x_0.nbt")));
  }

  @Test
  void theIndexListsEveryTemplate() throws IOException {
    var index = EnvesConfig.parseIndex("osarios",
        JsonParser.parseString(Files.readString(DATA.resolve("enves/templates/osarios.json"))));
    Set<String> files;
    try (var stream = Files.list(TEMPLATES)) {
      files = stream.map(p -> p.getFileName().toString().replace(".nbt", "")).collect(Collectors.toSet());
    }
    assertEquals(files, index.templates().keySet());
  }

  @Test
  void roomsCarryTheirMarkers() throws IOException {
    var start = read("start_nesw_0");
    assertEquals(1, start.count(Kind.ARRIVAL));
    assertEquals(1, start.count(Kind.STAIR_BOTTOM));
    var fight = read("fight_ns_1");
    assertTrue(fight.count(Kind.ENCOUNTER) >= 3, "several spawn points per fight room");
    var guard = read("guard_w_0");
    assertTrue(guard.markers().stream().anyMatch(m -> m.marker().argument().equals("champion")));
    assertTrue(guard.count(Kind.ENCOUNTER) >= 4);
    var vault = read("vault_e_0");
    assertEquals(12, vault.count(Kind.VAULT_GATE), "a 3x4 doorway");
    assertEquals(1, vault.count(Kind.VAULT_MECHANISM));
    assertEquals("vault", vault.markers().stream().filter(m -> m.marker().kind() == Kind.CHEST).findFirst()
        .orElseThrow().marker().head());
    for (var gate : vault.markers()) {
      if (gate.marker().kind() != Kind.VAULT_GATE) continue;
      assertEquals(18, gate.pos().getX(), "the east doorway");
      assertEquals("e", gate.marker().argument());
    }
    assertEquals(1, read("seal_s_0").count(Kind.SEAL));
    assertEquals(1, read("shrine_ew_0").count(Kind.SHRINE));
    assertEquals(1, read("arena_center_nesw_0").count(Kind.BOSS_CENTER));
    var portal = read("portal_s_0");
    assertEquals("victory", portal.markers().stream().filter(m -> m.marker().kind() == Kind.EXIT_PORTAL).findFirst()
        .orElseThrow().marker().argument());
    assertEquals(1, portal.count(Kind.CHEST));
    var vestibule = read("vestibule_x_0");
    assertEquals(1, vestibule.count(Kind.STAIR_TOP));
    assertEquals("return", vestibule.markers().stream().filter(m -> m.marker().kind() == Kind.EXIT_PORTAL).findFirst()
        .orElseThrow().marker().argument());
    assertEquals(0, vestibule.count(Kind.STAIR_SEAL), "the vestibule's stair is always open");
  }

  @Test
  void theStairwellTurnsOnceAcrossTheExitAndTheNextStart() throws IOException {
    var exit = read("exit_n_0");
    var start = read("start_e_0");
    assertEquals(1, exit.count(Kind.STAIR_TOP));
    var seals = exit.markers().stream().filter(m -> m.marker().kind() == Kind.STAIR_SEAL)
        .map(m -> List.of(m.pos().getX(), m.pos().getY(), m.pos().getZ())).collect(Collectors.toSet());
    Set<List<Integer>> expected = new java.util.HashSet<>();
    for (int p = 2; p <= 5; p++) expected.add(List.of(EnvesGeometry.RING[p][0], 0, EnvesGeometry.RING[p][1]));
    assertEquals(expected, seals, "the floor opening of the turn");
    assertTrue(exit.at(EnvesGeometry.RING[1][0], 0, EnvesGeometry.RING[1][1]).contains("stairs[facing=west"),
        "the first step sits in the exit's floor layer");
    String[] faces = {"west", "north", "east", "south"};
    for (int p = 2; p < 16; p++) {
      int y = EnvesGeometry.ringY(p) + EnvesGeometry.FLOOR_H;
      int[] cell = EnvesGeometry.RING[p];
      String step = start.at(cell[0], y, cell[1]);
      if (EnvesGeometry.landing(p)) assertFalse(step.isEmpty() || step.endsWith(":air"), "landing " + p);
      else assertTrue(step.contains("stairs[facing=" + faces[EnvesGeometry.ringSide(p)]), "stair " + p + " was " + step);
      for (int h = 1; h <= 3; h++)
        if (y + h < EnvesGeometry.FLOOR_H)
          assertEquals("minecraft:air", start.at(cell[0], y + h, cell[1]), "headroom over ring cell " + p);
      for (int below = 1; below < y; below++)
        assertFalse(start.at(cell[0], below, cell[1]).endsWith(":air"), "solid under ring cell " + p);
    }
    for (int x = EnvesGeometry.C - 1; x <= EnvesGeometry.C + 1; x++)
      for (int z = EnvesGeometry.C - 1; z <= EnvesGeometry.C + 1; z++)
        for (int y = 0; y < EnvesGeometry.FLOOR_H; y++)
          assertFalse(start.at(x, y, z).endsWith(":air"), "the core is solid");
    assertEquals(1, start.count(Kind.STAIR_BOTTOM));
  }

  @Test
  void theDataFilesParseWithTheOffering() throws IOException {
    var settings = EnvesConfig.parse(JsonParser.parseString(Files.readString(DATA.resolve("enves/config.json"))),
        id -> id.startsWith("minecraft:"));
    assertEquals("minecraft:netherite_block", settings.offeringItem());
    assertEquals(1, settings.offeringCount());
    assertEquals(3, settings.fallsPerMember());
    assertEquals(10, settings.abandonMinutes());
    assertEquals(List.of("osarios", "cisternas", "fundicion", "geodas", "eclipse"), settings.tilesets());
    assertEquals("osarios", settings.tileset(0), "the vestibule uses floor I's");
    assertEquals("entrelumen:enves/vault", settings.lootTable("vault", ApotheosisTiers.Tier.FRONTIER, 2));
    for (String id : settings.tilesets()) {
      var tileset = EnvesConfig.parseTileset(id,
          JsonParser.parseString(Files.readString(DATA.resolve("enves/tilesets/" + id + ".json"))));
      assertEquals("osarios", tileset.templates(), id + " borrows Osarios until its art lands");
      if (!id.equals("osarios")) assertFalse(tileset.palette().isEmpty(), id + " has a placeholder palette");
    }
    assertThrows(IllegalArgumentException.class, () -> EnvesConfig.parse(JsonParser.parseString(
        "{\"offering\": {\"item\": \"minecraft:nothing\", \"count\": 1}}"), id -> false));
    assertThrows(IllegalArgumentException.class, () -> EnvesConfig.parse(JsonParser.parseString(
        "{\"offering\": {\"item\": \"minecraft:diamond\", \"count\": 0}}"), id -> true));
  }

  @Test
  void slotsAndFloorsNeverOverlap() {
    int span = EnvesLayout.W * EnvesGeometry.CELL;
    assertTrue(span < EnvesGeometry.SLOT_SPACING);
    assertEquals(0, EnvesGeometry.slotAt(EnvesGeometry.slotX(0), EnvesGeometry.slotZ(0)));
    assertEquals(65, EnvesGeometry.slotAt(EnvesGeometry.slotX(65) + span - 1, EnvesGeometry.slotZ(65) + span - 1));
    assertEquals(-1, EnvesGeometry.slotAt(EnvesGeometry.slotX(3) + span, EnvesGeometry.slotZ(3)), "between slots");
    for (int depth = 0; depth < EnvesGeometry.DEPTHS; depth++) {
      int y = EnvesGeometry.floorY(depth);
      assertEquals(depth, EnvesGeometry.depthAt(y));
      assertEquals(depth, EnvesGeometry.depthAt(y + EnvesGeometry.FLOOR_H - 1));
      assertTrue(y - 1 > EnvesGeometry.VOID_Y, "the underlay stays above the void guard");
    }
    assertEquals(-1, EnvesGeometry.depthAt(EnvesGeometry.floorY(0) + EnvesGeometry.FLOOR_H));
    assertEquals(EnvesLayout.index(2, 3), EnvesGeometry.cellAt(7, EnvesGeometry.slotX(7) + 2 * 19 + 5,
        EnvesGeometry.slotZ(7) + 3 * 19 + 18));
  }
}
