package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * The echo tables of the Envés: {@code data/entrelumen/enves/encounters/<tileset>.json}, one per
 * tileset, so the enemies follow the floor's look (docs/design/dungeon-enves.md, «Contenido
 * propuesto»). Each lists escorts, elites and the stair's champion as entity ids of mods in the pack,
 * with an optional vanilla {@code fallback} for a pack without that mod, a base {@code health} and a
 * {@code damage} multiplier that override the role's, {@code equipment} and a {@code name} key.
 * Pure: parsing and drawing only.
 */
public final class EnvesEchoTables {
  private EnvesEchoTables() {}

  public static final Set<String> SLOTS = Set.of("mainhand", "offhand", "head", "chest", "legs", "feet");

  /** One echo kind. {@code name} is a lang key taking the entity's name, or empty for «Eco de …». */
  public record Entry(String entity, int weight, String fallback, Double health, Double damage,
      Map<String, String> equipment, String name) {
    public Entry {
      equipment = Map.copyOf(equipment);
      if (weight < 1) throw new IllegalArgumentException("weight must be at least 1: " + entity);
      if (ResourceLocation.tryParse(entity) == null) throw new IllegalArgumentException("bad entity id " + entity);
      if (fallback != null && ResourceLocation.tryParse(fallback) == null) throw new IllegalArgumentException("bad fallback " + fallback);
      if (health != null && health <= 0) throw new IllegalArgumentException("health must be positive: " + entity);
      if (damage != null && damage < 0) throw new IllegalArgumentException("damage must be 0 or more: " + entity);
      for (var slot : equipment.entrySet()) {
        if (!SLOTS.contains(slot.getKey())) throw new IllegalArgumentException("unknown slot " + slot.getKey());
        if (ResourceLocation.tryParse(slot.getValue()) == null) throw new IllegalArgumentException("bad item " + slot.getValue());
      }
    }

    /** Every item id this entry may equip. */
    public List<String> items() {
      return List.copyOf(equipment.values());
    }
  }

  /** A tileset's echoes; {@code treasure} may be null. */
  public record Table(List<Entry> escorts, List<Entry> elites, List<Entry> champions, Entry treasure) {
    public Table {
      escorts = List.copyOf(escorts);
      elites = List.copyOf(elites);
      champions = List.copyOf(champions);
      if (escorts.isEmpty() && elites.isEmpty()) throw new IllegalArgumentException("a table needs escorts or elites");
    }

    public List<Entry> all() {
      List<Entry> out = new ArrayList<>(escorts);
      out.addAll(elites);
      out.addAll(champions);
      if (treasure != null) out.add(treasure);
      return out;
    }
  }

  /** How a fight room is filled (Elias, 26/9: «1 élite más 1–2 escoltas, o 2 élites»). */
  public enum Group {
    ELITE_AND_ESCORT(1, 1, 45), ELITE_AND_TWO_ESCORTS(1, 2, 30), TWO_ELITES(2, 0, 25);

    public final int elites, escorts, weight;

    Group(int elites, int escorts, int weight) {
      this.elites = elites;
      this.escorts = escorts;
      this.weight = weight;
    }

    public static Group draw(Random random) {
      int total = 0;
      for (Group group : values()) total += group.weight;
      int pick = random.nextInt(total);
      for (Group group : values()) {
        pick -= group.weight;
        if (pick < 0) return group;
      }
      return ELITE_AND_ESCORT;
    }
  }

  public static Table parse(JsonElement element) {
    JsonObject root = element.getAsJsonObject();
    Entry treasure = root.has("treasure") ? entry(root.getAsJsonObject("treasure")) : null;
    return new Table(entries(root, "escorts"), entries(root, "elites"), entries(root, "champions"), treasure);
  }

  private static List<Entry> entries(JsonObject root, String key) {
    List<Entry> out = new ArrayList<>();
    if (!root.has(key)) return out;
    JsonArray array = root.getAsJsonArray(key);
    for (JsonElement e : array) out.add(entry(e.getAsJsonObject()));
    return out;
  }

  private static Entry entry(JsonObject o) {
    Map<String, String> equipment = new LinkedHashMap<>();
    if (o.has("equipment"))
      for (var slot : o.getAsJsonObject("equipment").entrySet())
        equipment.put(slot.getKey().toLowerCase(Locale.ROOT), slot.getValue().getAsString());
    return new Entry(o.get("entity").getAsString(), o.has("weight") ? o.get("weight").getAsInt() : 1,
        o.has("fallback") ? o.get("fallback").getAsString() : null, o.has("health") ? o.get("health").getAsDouble() : null,
        o.has("damage") ? o.get("damage").getAsDouble() : null, equipment, o.has("name") ? o.get("name").getAsString() : "");
  }

  /** One entry by weight, or null from an empty list. */
  public static Entry draw(List<Entry> entries, Random random) {
    if (entries.isEmpty()) return null;
    int total = entries.stream().mapToInt(Entry::weight).sum();
    int pick = random.nextInt(total);
    for (Entry entry : entries) {
      pick -= entry.weight();
      if (pick < 0) return entry;
    }
    return entries.getLast();
  }

  /**
   * The entity id to spawn for an entry: its own when the registry knows it, else its fallback, else
   * null (the entry is skipped).
   */
  public static String resolve(Entry entry, Predicate<String> known) {
    if (known.test(entry.entity())) return entry.entity();
    if (entry.fallback() != null && known.test(entry.fallback())) return entry.fallback();
    return null;
  }
}
