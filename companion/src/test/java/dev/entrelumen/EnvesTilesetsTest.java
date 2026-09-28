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
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * Every tileset of the descent against the marker contract, template by template. Floors II-V
 * (art/dungeon/cisternas.py, fundicion.py, geodas.py, eclipse.py through kit.py) must work wherever
 * the engine and the content hooks expect Osarios's: the same roles and masks, markers standable where
 * someone stands, vault gates in the doorway, one stairwell turn across exit and start, and fluids that
 * cannot flow out of their template nor lava that can be reached. Reads the committed resources.
 */
class EnvesTilesetsTest {
  private static final Path DATA = Path.of("src/main/resources/data/entrelumen");
  private static final Block AIR = new Block("minecraft:air", Map.of());
  private static final String[] DOORS = {"n", "e", "s", "w"};
  private static final String[] FACES = {"west", "north", "east", "south"};

  private record Block(String name, Map<String, String> props) {
    boolean air() {
      return name.equals("minecraft:air");
    }

    boolean fluid() {
      return name.equals("minecraft:water") || name.equals("minecraft:lava") || "true".equals(props.get("waterlogged"));
    }

    boolean bars() {
      return name.equals("minecraft:iron_bars");
    }
  }

  private record Template(String name, Map<List<Integer>, Block> blocks, List<EnvesTemplates.Local> markers) {
    /** Room air (layers 1-9) is not written; outside the box there is nothing. */
    Block at(int x, int y, int z) {
      if (x < 0 || y < 0 || z < 0 || x >= 19 || y >= 12 || z >= 19) return null;
      Block b = blocks.get(List.of(x, y, z));
      return b != null ? b : (y >= 1 && y <= 9 ? AIR : null);
    }

    List<EnvesTemplates.Local> of(Kind kind) {
      return markers.stream().filter(m -> m.marker().kind() == kind).toList();
    }
  }

  private static EnvesConfig.Settings settings() throws IOException {
    // The offering names the mod's own shard besides vanilla items.
    return EnvesConfig.parse(JsonParser.parseString(Files.readString(DATA.resolve("enves/config.json"))),
        id -> id.startsWith("minecraft:") || id.startsWith("entrelumen:"));
  }

  private static String folder(String tileset) throws IOException {
    return EnvesConfig.parseTileset(tileset,
        JsonParser.parseString(Files.readString(DATA.resolve("enves/tilesets/" + tileset + ".json")))).templates();
  }

  private static Template read(String folder, String name) throws IOException {
    CompoundTag tag = NbtIo.readCompressed(DATA.resolve("structure/enves/" + folder + "/" + name + ".nbt"),
        NbtAccounter.unlimitedHeap());
    ListTag size = tag.getList("size", Tag.TAG_INT);
    assertEquals(List.of(19, 12, 19), List.of(size.getInt(0), size.getInt(1), size.getInt(2)), name);
    List<EnvesTemplates.Local> markers = new ArrayList<>();
    EnvesTemplates.prepare(tag, Map.of(), markers, name);
    ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
    Map<List<Integer>, Block> blocks = new HashMap<>();
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      CompoundTag block = (CompoundTag) element;
      ListTag pos = block.getList("pos", Tag.TAG_INT);
      CompoundTag state = palette.getCompound(block.getInt("state"));
      Map<String, String> props = new TreeMap<>();
      CompoundTag p = state.getCompound("Properties");
      for (String k : p.getAllKeys()) props.put(k, p.getString(k));
      blocks.put(List.of(pos.getInt(0), pos.getInt(1), pos.getInt(2)), new Block(state.getString("Name"), props));
    }
    return new Template(name, blocks, markers);
  }

  /** Every template name the layout can ask a tileset for. */
  private static List<String> names() {
    List<String> out = new ArrayList<>();
    for (Role role : Role.values())
      for (int doors = 1; doors < 16; doors++)
        for (int variant = 0; variant < EnvesTemplates.variants(role.id); variant++)
          out.add(EnvesTemplates.name(role.id, doors, variant));
    out.add("vestibule_x_0");
    return out;
  }

  /** The tilesets drawn after Osarios, which EnvesContractTest covers on its own. */
  private static List<String> drawn() throws IOException {
    List<String> out = new ArrayList<>();
    for (String id : settings().tilesets()) if (!id.equals("osarios")) out.add(folder(id));
    return out;
  }

  private static String role(String name) {
    return name.substring(0, name.lastIndexOf('_', name.lastIndexOf('_') - 1));
  }

  private static String mask(String name) {
    String rest = name.substring(0, name.lastIndexOf('_'));
    return rest.substring(rest.lastIndexOf('_') + 1);
  }

  private static boolean standable(Template t, int x, int y, int z) {
    Block under = t.at(x, y - 1, z), feet = t.at(x, y, z), head = t.at(x, y + 1, z);
    return under != null && !under.air() && !under.fluid() && feet != null && feet.air() && head != null && head.air();
  }

  @Test
  void everyTilesetHasItsOwnCompleteSetAndIndex() throws IOException {
    for (String id : settings().tilesets()) {
      String folder = folder(id);
      assertEquals(id, folder, id + " reads its own templates");
      Path dir = DATA.resolve("structure/enves/" + folder);
      for (String name : names()) assertTrue(Files.isRegularFile(dir.resolve(name + ".nbt")), folder + "/" + name);
      var index = EnvesConfig.parseIndex(folder,
          JsonParser.parseString(Files.readString(DATA.resolve("enves/templates/" + folder + ".json"))));
      Set<String> files;
      try (var stream = Files.list(dir)) {
        files = stream.map(p -> p.getFileName().toString().replace(".nbt", "")).collect(Collectors.toSet());
      }
      assertEquals(files, index.templates().keySet(), folder + "'s index");
    }
  }

  @Test
  void everyTemplateKeepsTheMarkerContract() throws IOException {
    for (String folder : drawn())
      for (String name : names()) {
        Template t = read(folder, name);
        String role = role(name), where = folder + "/" + name;
        int doors = mask(name).equals("x") ? 0 : mask(name).length();
        for (var m : t.markers()) {
          BlockPosCheck.inside(m, where);
          switch (m.marker().kind()) {
            case ARRIVAL, STAIR_TOP, STAIR_BOTTOM, ENCOUNTER, CHEST, VAULT_MECHANISM, BOSS_CENTER ->
                assertTrue(standable(t, m.pos().getX(), m.pos().getY(), m.pos().getZ()),
                    where + ": " + m.marker().metadata() + " at " + m.pos() + " is not standable");
            default -> {}
          }
        }
        switch (role) {
          case "start" -> {
            assertEquals(1, t.of(Kind.ARRIVAL).size(), where);
            assertEquals(1, t.of(Kind.STAIR_BOTTOM).size(), where);
          }
          case "exit" -> {
            assertEquals(1, t.of(Kind.STAIR_TOP).size(), where);
            assertEquals(4, t.of(Kind.STAIR_SEAL).size(), where);
          }
          case "vestibule" -> {
            assertEquals(1, t.of(Kind.STAIR_TOP).size(), where);
            assertEquals("return", t.of(Kind.EXIT_PORTAL).getFirst().marker().argument(), where);
            assertEquals(0, t.of(Kind.STAIR_SEAL).size(), where);
          }
          case "guard" -> {
            assertEquals(5, t.of(Kind.ENCOUNTER).size(), where);
            var champion = t.of(Kind.ENCOUNTER).stream().filter(m -> m.marker().argument().equals("champion")).toList();
            assertEquals(1, champion.size(), where);
            assertEquals(List.of(9, 9), List.of(champion.getFirst().pos().getX(), champion.getFirst().pos().getZ()), where);
          }
          case "fight" -> assertEquals(4, t.of(Kind.ENCOUNTER).size(), where);
          case "quiet" -> assertTrue(t.of(Kind.CHEST).size() <= 1, where);
          case "shrine" -> assertEquals(1, t.of(Kind.SHRINE).size(), where);
          case "seal" -> assertEquals(1, t.of(Kind.SEAL).size(), where);
          case "arena_center" -> assertEquals(1, t.of(Kind.BOSS_CENTER).size(), where);
          case "portal" -> {
            assertEquals("victory", t.of(Kind.EXIT_PORTAL).getFirst().marker().argument(), where);
            assertEquals("boss", t.of(Kind.CHEST).getFirst().marker().head(), where);
          }
          case "vault" -> {
            assertEquals("vault", t.of(Kind.CHEST).getFirst().marker().head(), where);
            assertEquals(12 * doors, t.of(Kind.VAULT_GATE).size(), where);
            assertEquals(doors, t.of(Kind.VAULT_MECHANISM).size(), where);
            for (var gate : t.of(Kind.VAULT_GATE)) {
              String side = gate.marker().argument();
              int x = gate.pos().getX(), z = gate.pos().getZ();
              boolean onSide = switch (side) {
                case "n" -> z == 0 && x >= 8 && x <= 10;
                case "s" -> z == 18 && x >= 8 && x <= 10;
                case "w" -> x == 0 && z >= 8 && z <= 10;
                case "e" -> x == 18 && z >= 8 && z <= 10;
                default -> false;
              };
              assertTrue(onSide, where + ": gate " + side + " at " + gate.pos());
            }
          }
          default -> {}
        }
      }
  }

  @Test
  void fluidsStayInTheirTemplateAndLavaIsOutOfReach() throws IOException {
    for (String folder : drawn())
      for (String name : names()) {
        Template t = read(folder, name);
        String where = folder + "/" + name;
        for (var e : t.blocks().entrySet()) {
          Block b = e.getValue();
          if (!b.fluid()) continue;
          int x = e.getKey().get(0), y = e.getKey().get(1), z = e.getKey().get(2);
          assertTrue(x > 0 && x < 18 && z > 0 && z < 18 && y > 0, where + ": fluid on the template edge at " + e.getKey());
          int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
          for (int[] d : around) {
            Block n = t.at(x + d[0], y + d[1], z + d[2]);
            assertTrue(n != null && !n.air(), where + ": " + b.name() + " at " + e.getKey() + " can flow");
          }
          if (!b.name().equals("minecraft:lava")) continue;
          Block above = t.at(x, y + 1, z);
          assertTrue(above != null && !above.air() && !above.bars(), where + ": lava open above at " + e.getKey());
          for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Block n = t.at(x + d[0], y, z + d[1]);
            if (!n.bars()) continue;
            String a = d[0] != 0 ? "north" : "east", c = d[0] != 0 ? "south" : "west";
            assertTrue("true".equals(n.props().get(a)) && "true".equals(n.props().get(c)),
                where + ": the bars in front of the lava at " + e.getKey() + " leave a gap");
          }
        }
      }
  }

  @Test
  void theStairwellTurnsOnceInEveryTileset() throws IOException {
    for (String folder : drawn())
      for (String mask : List.of("n", "e", "s", "w", "ne", "nesw", "esw")) {
        int doors = 0;
        for (int i = 0; i < 4; i++) if (mask.contains(DOORS[i])) doors |= 1 << i;
        Template exit = read(folder, EnvesTemplates.name("exit", doors, 0));
        Template start = read(folder, EnvesTemplates.name("start", doors, 0));
        String where = folder + " " + mask;
        var seals = exit.of(Kind.STAIR_SEAL).stream().map(m -> List.of(m.pos().getX(), m.pos().getY(), m.pos().getZ()))
            .collect(Collectors.toSet());
        Set<List<Integer>> expected = new java.util.HashSet<>();
        for (int p = 2; p <= 5; p++) expected.add(List.of(EnvesGeometry.RING[p][0], 0, EnvesGeometry.RING[p][1]));
        assertEquals(expected, seals, where + ": the floor opening");
        Block first = exit.at(EnvesGeometry.RING[1][0], 0, EnvesGeometry.RING[1][1]);
        assertTrue(first.name().endsWith("_stairs") && "west".equals(first.props().get("facing")), where + ": first step");
        for (int p = 0; p <= 1; p++)
          for (int h = 1; h <= 3; h++)
            assertTrue(exit.at(EnvesGeometry.RING[p][0], h, EnvesGeometry.RING[p][1]).air(), where + ": headroom over " + p);
        var top = exit.of(Kind.STAIR_TOP).getFirst().pos();
        assertEquals(List.of(EnvesGeometry.RING[0][0], 1, EnvesGeometry.RING[0][1]), List.of(top.getX(), top.getY(), top.getZ()));
        for (int p = 2; p <= 16; p++) {
          int y = EnvesGeometry.ringY(p) + EnvesGeometry.FLOOR_H;
          int[] cell = EnvesGeometry.RING[p % 16];
          Block step = start.at(cell[0], y, cell[1]);
          if (EnvesGeometry.landing(p)) assertFalse(step.air() || step.fluid(), where + ": landing " + p);
          else assertTrue(step.name().endsWith("_stairs") && FACES[EnvesGeometry.ringSide(p)].equals(step.props().get("facing")),
              where + ": stair " + p + " was " + step);
          for (int h = 1; h <= 3; h++)
            if (y + h < EnvesGeometry.FLOOR_H) assertTrue(start.at(cell[0], y + h, cell[1]).air(), where + ": headroom over " + p);
          for (int below = 1; below < y; below++)
            assertFalse(start.at(cell[0], below, cell[1]).air(), where + ": solid under " + p);
        }
        for (int x = EnvesGeometry.C - 1; x <= EnvesGeometry.C + 1; x++)
          for (int z = EnvesGeometry.C - 1; z <= EnvesGeometry.C + 1; z++)
            for (int y = 0; y < EnvesGeometry.FLOOR_H; y++) assertFalse(start.at(x, y, z).air(), where + ": the core");
        var bottom = start.of(Kind.STAIR_BOTTOM).getFirst().pos();
        assertEquals(List.of(EnvesGeometry.RING[0][0], 1, EnvesGeometry.RING[0][1]),
            List.of(bottom.getX(), bottom.getY(), bottom.getZ()));
      }
  }

  private static final class BlockPosCheck {
    static void inside(EnvesTemplates.Local m, String where) {
      var p = m.pos();
      assertTrue(p.getX() >= 0 && p.getX() < 19 && p.getY() >= 0 && p.getY() < 12 && p.getZ() >= 0 && p.getZ() < 19,
          where + ": marker outside the template at " + p);
    }
  }
}
