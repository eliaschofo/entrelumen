package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Data-driven Heliodor compass objectives, in campaign order. The server swaps the whole list only
 * after a complete validated reload; a rejected document keeps the previous list.
 */
public final class CompassTargets {
  /** Elias, 24 Sep: the compass never sends a team thousands of blocks away. */
  public static final int DEFAULT_RADIUS = 1500;
  public static final int MAX_RADIUS = 1500;
  public static final int MIN_RADIUS = 32;
  public static final String OVERWORLD = "minecraft:overworld";

  /** Narrative kind; its ordinal is the stable {@code entrelumen:kind} item property value. */
  public enum Kind {
    STRUCTURE,
    BOSS,
    ARTIFACT
  }

  public enum TargetType {
    STRUCTURE,
    BIOME,
    ANCHOR,
    POSITION,
    DIMENSION,
    /**
     * A marker of Solsticio's placed city ({@code mayor}, {@code inventor}, {@code gardener},
     * {@code priest} or {@code town_hall_portal}), read from the city at runtime, so the needle follows
     * whatever template the controller exports (act VI, 24 September 2026).
     */
    CITY_MARKER
  }

  /** The city markers a {@code city_marker} target may name. */
  public static final Set<String> CITY_MARKERS = Set.of("mayor", "inventor", "gardener", "priest", "town_hall_portal");

  public enum ConditionType {
    ITEM,
    MILESTONE,
    ADVANCEMENT
  }

  /**
   * Where the needle points. {@code value} is a structure or biome ID, a {@code #tag}, or an anchor
   * ID; {@code pos} is only set for fixed positions.
   */
  public record Target(TargetType type, String value, String dimension, BlockPos pos, int radius) {
    public boolean tag() {
      return value.startsWith("#");
    }

    /** Stable cache key: two objectives with the same search share discovered sites. */
    public String key() {
      return type.name().toLowerCase(Locale.ROOT) + "|" + dimension + "|" + value;
    }
  }

  public record Condition(ConditionType type, String value, int count) {}

  public record Objective(
      String id, int act, Kind kind, Target target, Condition advanceWhen, boolean lore) {}

  private static volatile List<Objective> active = List.of();

  private CompassTargets() {}

  public static List<Objective> active() {
    return active;
  }

  static void publish(List<Objective> objectives) {
    active = List.copyOf(objectives);
  }

  /**
   * Parses and validates the complete document. Objectives whose {@code mods} are not all loaded
   * are skipped, so optional expedition mods never block a smaller runtime.
   */
  public static List<Objective> parse(
      JsonElement root, Predicate<String> modLoaded, Predicate<ResourceLocation> itemExists) {
    if (root == null || !root.isJsonObject()) throw invalid("root", "expected an object");
    JsonObject object = root.getAsJsonObject();
    for (String field : object.keySet())
      if (!Set.of("draft", "note", "objectives").contains(field))
        throw invalid("root", "unknown field " + field);
    if (!object.has("objectives") || !object.get("objectives").isJsonArray())
      throw invalid("objectives", "expected an array");
    List<Objective> result = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    int previousAct = 1;
    for (JsonElement element : object.getAsJsonArray("objectives")) {
      if (!element.isJsonObject()) throw invalid("objectives", "expected objective objects");
      JsonObject entry = element.getAsJsonObject();
      String id = string(entry, "id", "objective");
      String path = "objectives." + id;
      if (!id.matches("[a-z0-9_]{1,64}"))
        throw invalid(path, "IDs use 1..64 lowercase letters, digits or underscores");
      if (!ids.add(id)) throw invalid(path, "duplicate objective ID");
      for (String field : entry.keySet())
        if (!Set.of("id", "act", "kind", "target", "advance_when", "lore", "mods", "note")
            .contains(field)) throw invalid(path, "unknown field " + field);
      int act = integer(entry, "act", path, 1, 6);
      if (act < previousAct) throw invalid(path + ".act", "objectives must be listed in act order");
      previousAct = act;
      Kind kind = enumValue(Kind.class, string(entry, "kind", path), path + ".kind");
      Target target = target(object(entry, "target", path), path + ".target");
      boolean lore = entry.has("lore") && bool(entry.get("lore"), path + ".lore");
      List<String> mods = new ArrayList<>();
      if (entry.has("mods")) {
        if (!entry.get("mods").isJsonArray()) throw invalid(path + ".mods", "expected mod IDs");
        for (JsonElement mod : entry.getAsJsonArray("mods")) {
          if (!mod.isJsonPrimitive() || !mod.getAsString().matches("[a-z0-9_.-]{1,64}"))
            throw invalid(path + ".mods", "expected mod IDs");
          mods.add(mod.getAsString());
        }
      }
      boolean available = mods.stream().allMatch(modLoaded);
      Condition condition =
          condition(object(entry, "advance_when", path), path + ".advance_when", available, itemExists);
      if (available) result.add(new Objective(id, act, kind, target, condition, lore));
    }
    return Collections.unmodifiableList(result);
  }

  private static Target target(JsonObject target, String path) {
    TargetType type = enumValue(TargetType.class, string(target, "type", path), path + ".type");
    Set<String> allowed =
        switch (type) {
          case STRUCTURE -> Set.of("type", "structure", "dimension", "radius");
          case BIOME -> Set.of("type", "biome", "dimension", "radius");
          case ANCHOR -> Set.of("type", "anchor", "dimension", "radius");
          case POSITION -> Set.of("type", "pos", "dimension");
          case DIMENSION -> Set.of("type", "dimension");
          case CITY_MARKER -> Set.of("type", "marker");
        };
    for (String field : target.keySet())
      if (!allowed.contains(field)) throw invalid(path, "unknown field " + field + " for " + type);
    String dimension =
        target.has("dimension")
            ? location(string(target, "dimension", path), path + ".dimension", false)
            : null;
    if (type == TargetType.DIMENSION && dimension == null)
      throw invalid(path + ".dimension", "dimension targets need a dimension");
    if (type == TargetType.POSITION && dimension == null)
      throw invalid(path + ".dimension", "fixed positions need a dimension");
    if (type == TargetType.CITY_MARKER) dimension = "entrelumen:solsticio";
    if (dimension == null) dimension = OVERWORLD;
    int radius =
        target.has("radius")
            ? integer(target, "radius", path, MIN_RADIUS, MAX_RADIUS)
            : DEFAULT_RADIUS;
    return switch (type) {
      case STRUCTURE ->
          new Target(type, location(string(target, "structure", path), path + ".structure", true),
              dimension, null, radius);
      case BIOME ->
          new Target(type, location(string(target, "biome", path), path + ".biome", true),
              dimension, null, radius);
      case ANCHOR ->
          new Target(type, location(string(target, "anchor", path), path + ".anchor", false),
              dimension, null, radius);
      case POSITION -> new Target(type, "", dimension, position(target, path), 0);
      case DIMENSION -> new Target(type, "", dimension, null, 0);
      case CITY_MARKER -> {
        String marker = string(target, "marker", path);
        if (!CITY_MARKERS.contains(marker)) throw invalid(path + ".marker", "unknown city marker " + marker);
        yield new Target(type, marker, dimension, null, 0);
      }
    };
  }

  private static BlockPos position(JsonObject target, String path) {
    if (!target.has("pos") || !target.get("pos").isJsonArray())
      throw invalid(path + ".pos", "expected [x, y, z]");
    JsonArray pos = target.getAsJsonArray("pos");
    if (pos.size() != 3) throw invalid(path + ".pos", "expected [x, y, z]");
    int[] value = new int[3];
    for (int i = 0; i < 3; i++) value[i] = exactInt(pos.get(i), path + ".pos");
    return new BlockPos(value[0], value[1], value[2]);
  }

  private static Condition condition(
      JsonObject condition, String path, boolean validateItem, Predicate<ResourceLocation> items) {
    ConditionType type =
        enumValue(ConditionType.class, string(condition, "type", path), path + ".type");
    Set<String> allowed =
        switch (type) {
          case ITEM -> Set.of("type", "item", "count");
          case MILESTONE -> Set.of("type", "milestone");
          case ADVANCEMENT -> Set.of("type", "advancement");
        };
    for (String field : condition.keySet())
      if (!allowed.contains(field)) throw invalid(path, "unknown field " + field + " for " + type);
    return switch (type) {
      case ITEM -> {
        String item = location(string(condition, "item", path), path + ".item", true);
        if (validateItem && !item.startsWith("#") && !items.test(ResourceLocation.parse(item)))
          throw invalid(path + ".item", "unknown item " + item);
        int count = condition.has("count") ? integer(condition, "count", path, 1, 4096) : 1;
        yield new Condition(type, item, count);
      }
      case MILESTONE -> {
        String milestone = string(condition, "milestone", path);
        if (!milestone.matches("[a-z0-9_]{1,128}"))
          throw invalid(path + ".milestone", "expected a campaign milestone ID");
        yield new Condition(type, milestone, 1);
      }
      case ADVANCEMENT ->
          new Condition(
              type, location(string(condition, "advancement", path), path + ".advancement", false),
              1);
    };
  }

  private static JsonObject object(JsonObject parent, String field, String path) {
    if (!parent.has(field) || !parent.get(field).isJsonObject())
      throw invalid(path + "." + field, "expected an object");
    return parent.getAsJsonObject(field);
  }

  private static String string(JsonObject parent, String field, String path) {
    JsonElement element = parent.get(field);
    if (element == null
        || !element.isJsonPrimitive()
        || !element.getAsJsonPrimitive().isString()
        || element.getAsString().isBlank())
      throw invalid(path + "." + field, "expected a nonempty string");
    return element.getAsString();
  }

  private static boolean bool(JsonElement element, String path) {
    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
      throw invalid(path, "expected true or false");
    return element.getAsBoolean();
  }

  private static int integer(JsonObject parent, String field, String path, int min, int max) {
    int value = exactInt(parent.get(field), path + "." + field);
    if (value < min || value > max)
      throw invalid(path + "." + field, "expected " + min + ".." + max);
    return value;
  }

  private static int exactInt(JsonElement element, String path) {
    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
      throw invalid(path, "expected an integer");
    try {
      return element.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw invalid(path, "expected a 32-bit integer");
    }
  }

  private static String location(String value, String path, boolean tagAllowed) {
    boolean tag = value.startsWith("#");
    if (tag && !tagAllowed) throw invalid(path, "tags are not allowed here");
    ResourceLocation id = ResourceLocation.tryParse(tag ? value.substring(1) : value);
    if (id == null) throw invalid(path, "invalid resource location " + value);
    return (tag ? "#" : "") + id;
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
    for (E constant : type.getEnumConstants())
      if (constant.name().toLowerCase(Locale.ROOT).equals(value)) return constant;
    throw invalid(path, "unknown value " + value);
  }

  private static IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException("Entrelumen compass " + path + ": " + message);
  }
}
