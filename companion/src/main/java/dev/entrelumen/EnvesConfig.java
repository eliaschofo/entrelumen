package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * The Envés's data: {@code data/entrelumen/enves/config.json} (the offering and the other tunables),
 * {@code enves/tilesets/<id>.json} (which templates a floor uses and its placeholder palette) and
 * {@code enves/templates/<folder>.json} (the template index {@code tools/export_enves_tiles.py}
 * writes). A datapack may replace any of them; a rejected file keeps the previous value.
 */
public final class EnvesConfig {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceLocation CONFIG = ResourceLocation.fromNamespaceAndPath("entrelumen", "enves/config.json");

  /**
   * {@code offerings}: what one attempt costs, any one of them (Elias, 27/9: a Nether star, or sour
   * light shards in its place; see {@link EnvesOffering}).
   * {@code tilesets}: the tileset of floors I–V in order (the vestibule uses floor I's).
   * {@code chestLootTable}: the loot table of a chest marker; {@code {kind}}, {@code {tier}} and
   * {@code {floor}} are replaced.
   */
  public record Settings(List<EnvesOffering.Offer> offerings, int fallsPerMember, int abandonMinutes,
      String stairSealBlock, String vaultGateBlock, double roomChestChance, String chestLootTable,
      Set<String> deniedCommands, List<String> tilesets, boolean mapStage) {
    public Settings {
      offerings = List.copyOf(offerings);
      if (offerings.isEmpty()) throw new IllegalArgumentException("at least one offering");
      deniedCommands = Set.copyOf(deniedCommands);
      tilesets = List.copyOf(tilesets);
    }

    public String tileset(int depth) {
      int index = Math.clamp(depth - 1, 0, tilesets.size() - 1);
      return tilesets.get(index);
    }

    public long abandonTicks() {
      return abandonMinutes * 60L * 20L;
    }

    public String lootTable(String kind, ApotheosisTiers.Tier tier, int floor) {
      return chestLootTable.replace("{kind}", kind).replace("{tier}", tier.name().toLowerCase(Locale.ROOT))
          .replace("{floor}", Integer.toString(floor));
    }
  }

  /** One Nether star, or 64 sour light shards (a full descent drops about twice that; see the docs). */
  public static final List<EnvesOffering.Offer> DEFAULT_OFFERINGS = List.of(
      new EnvesOffering.Offer("minecraft:nether_star", 1), new EnvesOffering.Offer("entrelumen:sour_light_shard", 64));

  public static final Settings DEFAULTS = new Settings(DEFAULT_OFFERINGS, 3, 10,
      "minecraft:reinforced_deepslate", "minecraft:iron_bars", 0.25, "entrelumen:enves/{kind}",
      Set.of("home", "homes", "sethome", "rtp", "wild", "back", "spawn", "tpa", "tpahere", "tpaccept", "warp", "warps"),
      List.of("osarios", "cisternas", "fundicion", "geodas", "eclipse"), true);

  /** A tileset: the template folder it reads and the block swaps of its placeholder palette. */
  public record Tileset(String id, String templates, Map<String, String> palette) {
    public Tileset {
      palette = Map.copyOf(palette);
    }
  }

  /** A template folder's index: template names with the markers they carry. */
  public record TemplateIndex(String folder, Map<String, Map<String, Integer>> templates) {
    public TemplateIndex {
      templates = Map.copyOf(templates);
    }
  }

  private static volatile Settings settings = DEFAULTS;
  private static volatile Map<String, Tileset> tilesets = Map.of();
  private static volatile Map<String, TemplateIndex> indexes = Map.of();

  private EnvesConfig() {}

  public static Settings settings() {
    return settings;
  }

  /** A tileset by id; an unknown one falls back to Osarios with no swaps. */
  public static Tileset tileset(String id) {
    Tileset tileset = tilesets.get(id);
    return tileset != null ? tileset : new Tileset(id, "osarios", Map.of());
  }

  public static TemplateIndex index(String folder) {
    return indexes.get(folder);
  }

  /** Test hook: replaces the settings and returns the previous ones. */
  static Settings swap(Settings replacement) {
    Settings previous = settings;
    settings = replacement;
    return previous;
  }

  // ---- Parsing (pure) -----------------------------------------------------------------------

  public static Settings parse(JsonElement element, Predicate<String> itemExists) {
    JsonObject root = element.getAsJsonObject();
    Settings d = DEFAULTS;
    List<EnvesOffering.Offer> offerings = d.offerings();
    if (root.has("offering")) offerings = EnvesOffering.parse(root.get("offering"), itemExists);
    else for (var offer : offerings)
      if (!itemExists.test(offer.item())) throw new IllegalArgumentException("Unknown offering item " + offer.item());
    int falls = intOr(root, "falls_per_member", d.fallsPerMember());
    if (falls < 1 || falls > 20) throw new IllegalArgumentException("falls_per_member must be 1..20");
    int minutes = intOr(root, "abandon_minutes", d.abandonMinutes());
    if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("abandon_minutes must be 1..1440");
    double chance = root.has("room_chest_chance") ? root.get("room_chest_chance").getAsDouble() : d.roomChestChance();
    if (chance < 0 || chance > 1) throw new IllegalArgumentException("room_chest_chance must be 0..1");
    Set<String> denied = new LinkedHashSet<>(d.deniedCommands());
    if (root.has("denied_commands")) {
      denied.clear();
      for (JsonElement command : root.getAsJsonArray("denied_commands"))
        denied.add(command.getAsString().toLowerCase(Locale.ROOT).replace("/", ""));
    }
    List<String> sets = new ArrayList<>(d.tilesets());
    if (root.has("tilesets")) {
      sets.clear();
      JsonArray array = root.getAsJsonArray("tilesets");
      for (JsonElement id : array) sets.add(id.getAsString());
      if (sets.size() != EnvesLayout.FLOORS)
        throw new IllegalArgumentException("tilesets needs one entry per floor (" + EnvesLayout.FLOORS + ")");
    }
    return new Settings(offerings, falls, minutes, stringOr(root, "stair_seal_block", d.stairSealBlock()),
        stringOr(root, "vault_gate_block", d.vaultGateBlock()), chance,
        stringOr(root, "chest_loot_table", d.chestLootTable()), denied, sets,
        !root.has("ftb_chunks_map_stage") || root.get("ftb_chunks_map_stage").getAsBoolean());
  }

  public static Tileset parseTileset(String id, JsonElement element) {
    JsonObject root = element.getAsJsonObject();
    String templates = root.has("templates") ? root.get("templates").getAsString() : id;
    Map<String, String> palette = new TreeMap<>();
    if (root.has("palette"))
      for (var entry : root.getAsJsonObject("palette").entrySet()) {
        if (ResourceLocation.tryParse(entry.getKey()) == null
            || ResourceLocation.tryParse(entry.getValue().getAsString()) == null)
          throw new IllegalArgumentException("Bad palette swap " + entry.getKey());
        palette.put(entry.getKey(), entry.getValue().getAsString());
      }
    return new Tileset(id, templates, palette);
  }

  public static TemplateIndex parseIndex(String folder, JsonElement element) {
    JsonObject root = element.getAsJsonObject();
    Map<String, Map<String, Integer>> templates = new TreeMap<>();
    for (var entry : root.getAsJsonObject("templates").entrySet()) {
      Map<String, Integer> markers = new TreeMap<>();
      JsonObject object = entry.getValue().getAsJsonObject();
      if (object.has("markers"))
        for (var marker : object.getAsJsonObject("markers").entrySet())
          markers.put(marker.getKey(), marker.getValue().getAsInt());
      templates.put(entry.getKey(), Map.copyOf(markers));
    }
    return new TemplateIndex(folder, templates);
  }

  private static int intOr(JsonObject root, String key, int fallback) {
    return root.has(key) ? root.get(key).getAsInt() : fallback;
  }

  private static String stringOr(JsonObject root, String key, String fallback) {
    return root.has(key) ? root.get(key).getAsString() : fallback;
  }

  // ---- Reload -------------------------------------------------------------------------------

  record Loaded(Settings settings, Map<String, Tileset> tilesets, Map<String, TemplateIndex> indexes) {}

  public static final class Listener extends SimplePreparableReloadListener<Loaded> {
    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
      Settings loaded = settings;
      var resource = manager.getResource(CONFIG);
      if (resource.isPresent()) {
        try (var reader = resource.get().openAsReader()) {
          loaded = parse(JsonParser.parseReader(reader), id -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)));
        } catch (Exception e) {
          LOGGER.error("Rejected {} from datapack {}; the previous Envés settings are kept. {}", CONFIG,
              resource.get().sourcePackId(), e.getMessage());
        }
      }
      Map<String, Tileset> sets = new LinkedHashMap<>(tilesets);
      manager.listResources("enves/tilesets", id -> id.getNamespace().equals("entrelumen") && id.getPath().endsWith(".json"))
          .forEach((id, res) -> {
            String name = id.getPath().substring("enves/tilesets/".length(), id.getPath().length() - 5);
            try (var reader = res.openAsReader()) {
              sets.put(name, parseTileset(name, JsonParser.parseReader(reader)));
            } catch (Exception e) {
              LOGGER.error("Rejected Envés tileset {}: {}", id, e.getMessage());
            }
          });
      Map<String, TemplateIndex> found = new LinkedHashMap<>(indexes);
      manager.listResources("enves/templates", id -> id.getNamespace().equals("entrelumen") && id.getPath().endsWith(".json"))
          .forEach((id, res) -> {
            String name = id.getPath().substring("enves/templates/".length(), id.getPath().length() - 5);
            try (var reader = res.openAsReader()) {
              found.put(name, parseIndex(name, JsonParser.parseReader(reader)));
            } catch (Exception e) {
              LOGGER.error("Rejected Envés template index {}: {}", id, e.getMessage());
            }
          });
      return new Loaded(loaded, Map.copyOf(sets), Map.copyOf(found));
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
      settings = loaded.settings();
      tilesets = loaded.tilesets();
      indexes = loaded.indexes();
      EnvesTemplates.clear();
      LOGGER.info("Envés: offerings {}, {} tilesets, template folders {}", settings.offerings(), tilesets.size(),
          indexes.keySet());
    }
  }
}
