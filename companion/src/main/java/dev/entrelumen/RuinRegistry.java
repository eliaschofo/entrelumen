package dev.entrelumen;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * The loaded ruin definitions ({@code data/<namespace>/heliodor_ruin/<name>.json}). A file that
 * fails to parse is logged and left out; the rest still load. Definitions of absent mods are kept
 * (their pieces move to a landmark pedestal) but never placed.
 */
public final class RuinRegistry {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final String DIRECTORY = "heliodor_ruin";
  private static volatile Map<String, RuinDefinitions.Definition> active = Map.of();
  /** Definitions added at runtime (GameTest fixtures); they survive datapack reloads. */
  private static volatile Map<String, RuinDefinitions.Definition> added = Map.of();

  private RuinRegistry() {}

  /** The datapack's definitions (what the world ships). */
  public static Map<String, RuinDefinitions.Definition> all() {
    return active;
  }

  public static Optional<RuinDefinitions.Definition> get(ResourceLocation id) {
    return get(id.toString());
  }

  public static Optional<RuinDefinitions.Definition> get(String id) {
    var definition = active.get(id);
    return Optional.ofNullable(definition != null ? definition : added.get(id));
  }

  public static boolean modLoaded(String mod) {
    var list = ModList.get();
    return list != null && list.isLoaded(mod);
  }

  /** Definitions whose mods are loaded: the ruins that exist in this world. */
  public static List<RuinDefinitions.Definition> available() {
    return active.values().stream().filter(d -> d.available(RuinRegistry::modLoaded)).toList();
  }

  /** The definition whose piece is {@code item}, if any. */
  public static Optional<RuinDefinitions.Definition> byPiece(String item) {
    return active.values().stream().filter(d -> d.piece().equals(item)).findFirst();
  }

  /** Reload target: replaces the whole set at once. */
  static void publish(Map<String, RuinDefinitions.Definition> definitions) {
    active = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
  }

  /** Adds one definition next to the datapack's (GameTest fixtures); reloads keep it. */
  static synchronized void add(RuinDefinitions.Definition definition) {
    Map<String, RuinDefinitions.Definition> copy = new LinkedHashMap<>(added);
    copy.put(definition.id(), definition);
    added = Collections.unmodifiableMap(copy);
  }

  static synchronized void remove(String id) {
    Map<String, RuinDefinitions.Definition> copy = new LinkedHashMap<>(added);
    copy.remove(id);
    added = Collections.unmodifiableMap(copy);
  }

  static final class ReloadListener extends SimpleJsonResourceReloadListener {
    ReloadListener() {
      super(new com.google.gson.Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
      Map<String, RuinDefinitions.Definition> parsed = new TreeMap<>();
      for (var entry : files.entrySet()) {
        String id = entry.getKey().toString();
        try {
          parsed.put(id, RuinDefinitions.parse(id, entry.getValue(), RuinRegistry::modLoaded,
              item -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item)),
              entity -> BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entity))));
        } catch (RuntimeException e) {
          LOGGER.error("Rejected Heliodor ruin {}: {}", id, e.getMessage());
        }
      }
      for (String problem : RuinDefinitions.problems(parsed.values()))
        LOGGER.warn("Heliodor ruins: {}", problem);
      publish(parsed);
      LOGGER.info("Loaded {} Heliodor ruin definitions ({} available here)", parsed.size(),
          parsed.values().stream().filter(d -> d.available(RuinRegistry::modLoaded)).count());
    }
  }
}
