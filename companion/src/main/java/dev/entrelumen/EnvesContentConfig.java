package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * The Envés content's data: {@code data/entrelumen/enves/balance.json} ({@link EnvesBalance}) and the
 * echo tables {@code data/entrelumen/enves/encounters/<tileset>.json} ({@link EnvesEchoTables}). A
 * datapack may replace either; a file that does not validate keeps the previous value and logs why.
 */
public final class EnvesContentConfig {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceLocation BALANCE = ResourceLocation.fromNamespaceAndPath("entrelumen", "enves/balance.json");

  /** Used for a tileset with no table: vanilla echoes only, so nothing is ever missing. */
  public static final EnvesEchoTables.Table PLAIN = new EnvesEchoTables.Table(
      List.of(new EnvesEchoTables.Entry("minecraft:skeleton", 1, null, null, null, Map.of(), "")),
      List.of(new EnvesEchoTables.Entry("minecraft:zombie", 1, null, null, null, Map.of(), "")),
      List.of(new EnvesEchoTables.Entry("minecraft:wither_skeleton", 1, null, null, null, Map.of(), "")), null);

  private static volatile EnvesBalance.Settings balance = EnvesBalance.DEFAULTS;
  private static volatile Map<String, EnvesEchoTables.Table> tables = Map.of();

  private EnvesContentConfig() {}

  public static EnvesBalance.Settings balance() {
    return balance;
  }

  /** The echo table of a tileset; {@link #PLAIN} when it has none. */
  public static EnvesEchoTables.Table table(String tileset) {
    var table = tables.get(tileset);
    return table != null ? table : PLAIN;
  }

  public static Map<String, EnvesEchoTables.Table> tables() {
    return tables;
  }

  record Loaded(EnvesBalance.Settings balance, Map<String, EnvesEchoTables.Table> tables) {}

  public static final class Listener extends SimplePreparableReloadListener<Loaded> {
    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
      var loaded = balance;
      var resource = manager.getResource(BALANCE);
      if (resource.isPresent()) {
        try (var reader = resource.get().openAsReader()) {
          loaded = EnvesBalance.parse(JsonParser.parseReader(reader));
        } catch (Exception e) {
          LOGGER.error("Rejected {} from datapack {}; the previous Envés balance is kept. {}", BALANCE,
              resource.get().sourcePackId(), e.getMessage());
        }
      }
      Map<String, EnvesEchoTables.Table> found = new LinkedHashMap<>(tables);
      manager.listResources("enves/encounters", id -> id.getNamespace().equals("entrelumen") && id.getPath().endsWith(".json"))
          .forEach((id, res) -> {
            String name = id.getPath().substring("enves/encounters/".length(), id.getPath().length() - 5);
            try (var reader = res.openAsReader()) {
              found.put(name, EnvesEchoTables.parse(JsonParser.parseReader(reader)));
            } catch (Exception e) {
              LOGGER.error("Rejected Envés echo table {}: {}", id, e.getMessage());
            }
          });
      return new Loaded(loaded, Map.copyOf(found));
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
      balance = loaded.balance();
      tables = loaded.tables();
      LOGGER.info("Envés content: echo tables {}", tables.keySet());
    }
  }
}
