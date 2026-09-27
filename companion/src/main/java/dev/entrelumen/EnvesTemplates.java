package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.entrelumen.EnvesLayout.Dir;
import dev.entrelumen.EnvesLayout.Role;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Loads the Envés room templates of a tileset: reads the NBT exported by
 * {@code tools/export_enves_tiles.py}, applies the tileset's placeholder palette swaps, and turns
 * every DATA marker into air while keeping its kind and template-local position. Cached per
 * tileset and template until the next datapack reload.
 */
public final class EnvesTemplates {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String STRUCTURE_BLOCK = "minecraft:structure_block";

  /** A marker inside a loaded template, at its template-local position. */
  public record Local(EnvesMarkers.Marker marker, BlockPos pos) {}

  /** A template ready to place and its markers. */
  public record Loaded(String name, StructureTemplate template, List<Local> markers) {
    public List<Local> markers(EnvesMarkers.Kind kind) {
      return markers.stream().filter(local -> local.marker().kind() == kind).toList();
    }
  }

  /** Templates in use, least recently used first; a floor needs a few dozen, a tileset has ~230. */
  static final int CACHE_SIZE = 160;
  private static final Map<String, Optional<Loaded>> CACHE = new java.util.LinkedHashMap<>(64, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Optional<Loaded>> eldest) {
      return size() > CACHE_SIZE;
    }
  };

  private EnvesTemplates() {}

  static void clear() {
    synchronized (CACHE) {
      CACHE.clear();
    }
  }

  /** Door letters in NESW order ({@code nes}, {@code w}), {@code x} for none: the exporter's names. */
  public static String mask(int doors) {
    StringBuilder out = new StringBuilder();
    for (Dir dir : Dir.values()) if ((doors & dir.bit) != 0) out.append(Character.toLowerCase(dir.name().charAt(0)));
    return out.isEmpty() ? "x" : out.toString();
  }

  /** How many shapes a role has: quiet and fight rooms three, the rest one. */
  public static int variants(String role) {
    return role.equals(Role.QUIET.id) || role.equals(Role.FIGHT.id) ? 3 : 1;
  }

  public static String name(String role, int doors, int variant) {
    return role + "_" + mask(doors) + "_" + variant;
  }

  /**
   * The template of one cell for a depth's tileset; empty (and logged) when missing. Safe from any
   * thread: {@link EnvesPlacer} warms a floor's templates in the background.
   */
  public static Optional<Loaded> get(MinecraftServer server, String tilesetId, String name) {
    String key = tilesetId + "/" + name;
    synchronized (CACHE) {
      var cached = CACHE.get(key);
      if (cached != null) return cached;
    }
    Optional<Loaded> loaded = load(server, tilesetId, name);
    synchronized (CACHE) {
      var raced = CACHE.putIfAbsent(key, loaded);
      return raced != null ? raced : loaded;
    }
  }

  private static Optional<Loaded> load(MinecraftServer server, String tilesetId, String name) {
    var tileset = EnvesConfig.tileset(tilesetId);
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("entrelumen",
        "structure/enves/" + tileset.templates() + "/" + name + ".nbt");
    var resource = server.getResourceManager().getResource(id);
    if (resource.isEmpty()) {
      LOGGER.error("Missing Envés template {} for tileset {}", id, tilesetId);
      return Optional.empty();
    }
    try (InputStream stream = resource.get().open()) {
      CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
      int version = NbtUtils.getDataVersion(tag, 500);
      if (version < SharedConstants.getCurrentVersion().getDataVersion().getVersion())
        tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, version);
      List<Local> markers = new ArrayList<>();
      prepare(tag, tileset.palette(), markers, id.toString());
      StructureTemplate template = new StructureTemplate();
      template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
      return Optional.of(new Loaded(name, template, List.copyOf(markers)));
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Could not read Envés template {}", id, e);
      return Optional.empty();
    }
  }

  /**
   * Swaps palette names and strips DATA markers into {@code markers}; the marked blocks become air.
   * Package-visible for tests.
   */
  static void prepare(CompoundTag tag, Map<String, String> swaps, List<Local> markers, String source) {
    ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
    for (int i = 0; i < palette.size(); i++) {
      CompoundTag state = palette.getCompound(i);
      String swap = swaps.get(state.getString("Name"));
      if (swap != null) state.putString("Name", swap);
    }
    int air = palette.size();
    CompoundTag airState = new CompoundTag();
    airState.putString("Name", "minecraft:air");
    palette.add(airState);
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      CompoundTag block = (CompoundTag) element;
      int state = block.getInt("state");
      if (state < 0 || state >= air || !STRUCTURE_BLOCK.equals(palette.getCompound(state).getString("Name"))) continue;
      CompoundTag nbt = block.getCompound("nbt");
      if (!"DATA".equals(nbt.getString("mode"))) continue;
      ListTag pos = block.getList("pos", Tag.TAG_INT);
      String metadata = nbt.getString("metadata");
      var marker = EnvesMarkers.parse(metadata);
      if (marker.isPresent()) markers.add(new Local(marker.get(), new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))));
      else LOGGER.warn("Unknown Envés marker '{}' in {}", metadata, source);
      block.putInt("state", air);
      block.remove("nbt");
    }
  }
}
