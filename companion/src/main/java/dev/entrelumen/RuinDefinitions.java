package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Predicate;

/**
 * Data-driven Heliodor ruins: one JSON per ruin under {@code data/entrelumen/heliodor_ruin/}. The
 * file says where and when the ruin is placed, its template, the key piece, the project that asks
 * for it and the challenges that open its gates and pedestal; the template's markers say where
 * each part stands. Pure parser, unit-tested.
 */
public final class RuinDefinitions {
  private RuinDefinitions() {}

  public enum Scale { LANDMARK, MEDIUM }

  /** How a site is found: on the ground, in an open cavern (Nether) or floating (Aether, End). */
  public enum Mode { SURFACE, CAVERN, SKY }

  /** Overworld ruins wait for the first team to open their act; others for the first arrival. */
  public enum Trigger { ACT, ARRIVAL }

  /**
   * PUMPS: the ruin's pumps turn together, each its own way, at {@code rpm} or faster (0: computed from
   * Create's stress values, see {@link RuinRules#pumpSpeed}). SEAL: a bearing holds its ring at
   * {@code angle} degrees (plus multiples of 90) within {@code tolerance}, at rest for {@code rest} ticks.
   * RELAY: the light relay of {@link LightRelay}, floor by floor up to the last receptor.
   */
  public enum ChallengeType { BRAZIERS, MIRRORS, REDSTONE, OFFERING, BOSS, HIDDEN, PUMPS, SEAL, RELAY }

  /** The mobs of a boss challenge: {@code count} copies share one boss bar. */
  public record Boss(String entity, String name, int count, int radius, String color,
      Map<String, Double> attributes) {
    public Boss {
      attributes = Collections.unmodifiableMap(new TreeMap<>(attributes));
    }
  }

  /** A pump or seal challenge's numbers (unused by the other types). */
  public record Engine(int rpm, int angle, int tolerance, int rest) {
    public static final Engine DEFAULT = new Engine(0, 45, 3, 60);
  }

  /**
   * One challenge. {@code requires} lists challenges the team must solve first (declared earlier);
   * {@code item}/{@code count} are the offering default for sockets that name none, and
   * {@code need} how many sockets must be filled (0: all).
   */
  public record Challenge(String id, ChallengeType type, List<String> requires, String item, int count,
      int need, Boss boss, Engine engine) {
    public Challenge {
      requires = List.copyOf(requires);
      engine = engine == null ? Engine.DEFAULT : engine;
    }

    public Challenge(String id, ChallengeType type, List<String> requires, String item, int count, int need, Boss boss) {
      this(id, type, requires, item, count, need, boss, Engine.DEFAULT);
    }
  }

  public record Placement(Mode mode, Trigger trigger, int min, int max) {}

  /**
   * {@code challenges} holds the challenges that play with the loaded mods; {@code dormant} names the
   * declared ones that do not (a challenge's {@code mods} missing, or one of its {@code without}
   * present), whose markers are then ignored. Gates, the pedestal and {@code requires} list only
   * playing challenges. {@code gifts} are items the solved pedestal also hands to a player who carries
   * none (the Signal Tower's Atlas).
   */
  public record Definition(String id, int act, String dimension, List<String> mods, Scale scale,
      String template, Placement placement, String piece, String project, String loot,
      Map<String, Challenge> challenges, Map<String, List<String>> gates, List<String> pedestal,
      Set<String> dormant, List<String> gifts) {
    public Definition {
      mods = List.copyOf(mods);
      challenges = Collections.unmodifiableMap(new LinkedHashMap<>(challenges));
      Map<String, List<String>> copy = new LinkedHashMap<>();
      gates.forEach((gate, needs) -> copy.put(gate, List.copyOf(needs)));
      gates = Collections.unmodifiableMap(copy);
      pedestal = List.copyOf(pedestal);
      dormant = Collections.unmodifiableSet(new TreeSet<>(dormant));
      gifts = List.copyOf(gifts);
    }

    public Definition(String id, int act, String dimension, List<String> mods, Scale scale, String template,
        Placement placement, String piece, String project, String loot, Map<String, Challenge> challenges,
        Map<String, List<String>> gates, List<String> pedestal) {
      this(id, act, dimension, mods, scale, template, placement, piece, project, loot, challenges, gates, pedestal,
          Set.of(), List.of());
    }

    /** The id's path: {@code entrelumen:signal_tower} gives {@code signal_tower}. */
    public String name() {
      return id.substring(id.indexOf(':') + 1);
    }

    public boolean available(Predicate<String> modLoaded) {
      return mods.stream().allMatch(modLoaded);
    }
  }

  public static final int MAX_DISTANCE = 4096;
  public static final int MAX_BOSSES = 8;

  /**
   * Parses one ruin. {@code items} and {@code entities} check ids only when every listed mod is
   * loaded, so a ruin of an absent mod still parses (its piece moves to a landmark pedestal).
   */
  public static Definition parse(String id, JsonElement root, Predicate<String> modLoaded,
      Predicate<String> items, Predicate<String> entities) {
    String path = id;
    if (!id.matches("[a-z0-9_.-]+:[a-z0-9_]{1,64}")) throw invalid(path, "bad ruin id");
    if (root == null || !root.isJsonObject()) throw invalid(path, "expected an object");
    JsonObject object = root.getAsJsonObject();
    allow(object, path, "act", "dimension", "mods", "scale", "template", "placement", "piece",
        "project", "loot", "challenges", "gates", "pedestal", "gifts", "note");
    int act = integer(object, "act", path, 1, Campaigns.FINAL_ACT);
    String dimension = location(string(object, "dimension", path), path + ".dimension");
    List<String> mods = new ArrayList<>();
    if (object.has("mods")) {
      if (!object.get("mods").isJsonArray()) throw invalid(path + ".mods", "expected mod ids");
      for (JsonElement mod : object.getAsJsonArray("mods")) {
        if (!mod.isJsonPrimitive() || !mod.getAsString().matches("[a-z0-9_.-]{1,64}"))
          throw invalid(path + ".mods", "expected mod ids");
        mods.add(mod.getAsString());
      }
    }
    boolean available = mods.stream().allMatch(modLoaded);
    Scale scale = enumValue(Scale.class, string(object, "scale", path), path + ".scale");
    String template = location(string(object, "template", path), path + ".template");
    Placement placement = placement(object, dimension, path);
    String piece = location(string(object, "piece", path), path + ".piece");
    if (available && !items.test(piece)) throw invalid(path + ".piece", "unknown item " + piece);
    String project = string(object, "project", path);
    if (!project.matches("[a-z0-9_]{1,128}")) throw invalid(path + ".project", "expected a project id");
    String loot = object.has("loot") ? location(string(object, "loot", path), path + ".loot") : "";
    Map<String, Challenge> declared = new LinkedHashMap<>();
    Set<String> dormant = new TreeSet<>();
    if (object.has("challenges")) {
      if (!object.get("challenges").isJsonObject()) throw invalid(path + ".challenges", "expected an object");
      for (var entry : object.getAsJsonObject("challenges").entrySet()) {
        String challenge = entry.getKey();
        String at = path + ".challenges." + challenge;
        if (!challenge.matches("[a-z0-9_]{1,32}")) throw invalid(at, "bad challenge id");
        if (!entry.getValue().isJsonObject()) throw invalid(at, "expected an object");
        JsonObject body = entry.getValue().getAsJsonObject();
        boolean plays = modList(body, "mods", at).stream().allMatch(modLoaded)
            && modList(body, "without", at).stream().noneMatch(modLoaded);
        declared.put(challenge, challenge(challenge, body, at, available && plays, items, entities, declared.keySet()));
        if (!plays) dormant.add(challenge);
      }
    }
    Map<String, Challenge> challenges = new LinkedHashMap<>();
    declared.forEach((key, challenge) -> {
      if (dormant.contains(key)) return;
      challenges.put(key, new Challenge(challenge.id(), challenge.type(),
          challenge.requires().stream().filter(r -> !dormant.contains(r)).toList(), challenge.item(), challenge.count(),
          challenge.need(), challenge.boss(), challenge.engine()));
    });
    Map<String, List<String>> gates = new LinkedHashMap<>();
    if (object.has("gates")) {
      if (!object.get("gates").isJsonObject()) throw invalid(path + ".gates", "expected an object");
      for (var entry : object.getAsJsonObject("gates").entrySet()) {
        if (!entry.getKey().matches("[a-z0-9_]{1,32}")) throw invalid(path + ".gates", "bad gate id");
        var needs = references(entry.getValue(), declared, path + ".gates." + entry.getKey());
        var playing = needs.stream().filter(c -> !dormant.contains(c)).toList();
        if (playing.isEmpty() && !needs.isEmpty())
          throw invalid(path + ".gates." + entry.getKey(), "no challenge that opens it plays with these mods");
        gates.put(entry.getKey(), playing);
      }
    }
    List<String> pedestal = object.has("pedestal")
        ? references(object.get("pedestal"), declared, path + ".pedestal").stream().filter(c -> !dormant.contains(c)).toList()
        : List.copyOf(challenges.keySet());
    List<String> gifts = new ArrayList<>();
    if (object.has("gifts")) {
      if (!object.get("gifts").isJsonArray()) throw invalid(path + ".gifts", "expected item ids");
      for (JsonElement gift : object.getAsJsonArray("gifts")) {
        if (!gift.isJsonPrimitive()) throw invalid(path + ".gifts", "expected item ids");
        String item = location(gift.getAsString(), path + ".gifts");
        if (available && !items.test(item)) throw invalid(path + ".gifts", "unknown item " + item);
        if (!gifts.contains(item)) gifts.add(item);
      }
    }
    return new Definition(id, act, dimension, mods, scale, template, placement, piece, project, loot,
        challenges, gates, pedestal, dormant, gifts);
  }

  private static List<String> modList(JsonObject object, String field, String path) {
    if (!object.has(field)) return List.of();
    if (!object.get(field).isJsonArray()) throw invalid(path + "." + field, "expected mod ids");
    List<String> mods = new ArrayList<>();
    for (JsonElement mod : object.getAsJsonArray(field)) {
      if (!mod.isJsonPrimitive() || !mod.getAsString().matches("[a-z0-9_.-]{1,64}"))
        throw invalid(path + "." + field, "expected mod ids");
      mods.add(mod.getAsString());
    }
    return mods;
  }

  private static Placement placement(JsonObject object, String dimension, String path) {
    String at = path + ".placement";
    if (!object.has("placement") || !object.get("placement").isJsonObject())
      throw invalid(at, "expected an object");
    JsonObject placement = object.getAsJsonObject("placement");
    allow(placement, at, "mode", "trigger", "min", "max");
    Mode mode = enumValue(Mode.class, string(placement, "mode", at), at + ".mode");
    Trigger trigger = placement.has("trigger")
        ? enumValue(Trigger.class, string(placement, "trigger", at), at + ".trigger")
        : dimension.equals("minecraft:overworld") ? Trigger.ACT : Trigger.ARRIVAL;
    if (trigger == Trigger.ACT && !dimension.equals("minecraft:overworld"))
      throw invalid(at + ".trigger", "only Overworld ruins wait for an act");
    int min = integer(placement, "min", at, 0, MAX_DISTANCE);
    int max = integer(placement, "max", at, 1, MAX_DISTANCE);
    if (min >= max) throw invalid(at, "min must be below max");
    return new Placement(mode, trigger, min, max);
  }

  private static Challenge challenge(String id, JsonObject object, String path, boolean available,
      Predicate<String> items, Predicate<String> entities, Set<String> earlier) {
    allow(object, path, "type", "requires", "item", "count", "need", "entity", "name", "radius", "color",
        "attributes", "mods", "without", "rpm", "angle", "tolerance", "rest");
    ChallengeType type = enumValue(ChallengeType.class, string(object, "type", path), path + ".type");
    List<String> requires = new ArrayList<>();
    if (object.has("requires")) {
      if (!object.get("requires").isJsonArray()) throw invalid(path + ".requires", "expected challenge ids");
      for (JsonElement entry : object.getAsJsonArray("requires")) {
        if (!entry.isJsonPrimitive() || !earlier.contains(entry.getAsString()))
          throw invalid(path + ".requires", "unknown or later challenge " + entry);
        if (!requires.contains(entry.getAsString())) requires.add(entry.getAsString());
      }
    }
    String item = "";
    int count = 1, need = 0;
    if (object.has("item")) {
      if (type != ChallengeType.OFFERING) throw invalid(path + ".item", "only offerings take items");
      item = string(object, "item", path);
      boolean tag = item.startsWith("#");
      String bare = location(tag ? item.substring(1) : item, path + ".item");
      item = (tag ? "#" : "") + bare;
      if (available && !tag && !items.test(bare)) throw invalid(path + ".item", "unknown item " + bare);
    }
    Boss boss = null;
    if (type == ChallengeType.BOSS) {
      String entity = location(string(object, "entity", path), path + ".entity");
      if (available && !entities.test(entity)) throw invalid(path + ".entity", "unknown entity " + entity);
      String name = string(object, "name", path);
      if (!name.matches("[a-z0-9_.]{1,128}")) throw invalid(path + ".name", "expected a translation key");
      int radius = object.has("radius") ? integer(object, "radius", path, 4, 64) : 16;
      String color = object.has("color") ? string(object, "color", path) : "yellow";
      if (!Set.of("pink", "blue", "red", "green", "yellow", "purple", "white").contains(color))
        throw invalid(path + ".color", "unknown boss bar colour " + color);
      Map<String, Double> attributes = new TreeMap<>();
      if (object.has("attributes")) {
        if (!object.get("attributes").isJsonObject()) throw invalid(path + ".attributes", "expected an object");
        for (var entry : object.getAsJsonObject("attributes").entrySet()) {
          String attribute = location(entry.getKey(), path + ".attributes");
          JsonElement value = entry.getValue();
          if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid(path + ".attributes." + attribute, "expected a number");
          double number = value.getAsDouble();
          if (!Double.isFinite(number) || number < 0 || number > 4096)
            throw invalid(path + ".attributes." + attribute, "expected 0..4096");
          attributes.put(attribute, number);
        }
      }
      count = object.has("count") ? integer(object, "count", path, 1, MAX_BOSSES) : 1;
      boss = new Boss(entity, name, count, radius, color, attributes);
    } else {
      for (String field : List.of("entity", "name", "radius", "color", "attributes"))
        if (object.has(field)) throw invalid(path + "." + field, "only boss challenges take " + field);
      if (object.has("count")) {
        if (type != ChallengeType.OFFERING) throw invalid(path + ".count", "only offerings take a count");
        count = integer(object, "count", path, 1, 64);
      }
    }
    if (object.has("need")) {
      if (type != ChallengeType.OFFERING) throw invalid(path + ".need", "only offerings take a need");
      need = integer(object, "need", path, 1, 64);
    }
    Engine engine = Engine.DEFAULT;
    if (object.has("rpm") && type != ChallengeType.PUMPS) throw invalid(path + ".rpm", "only pumps take an rpm");
    for (String field : List.of("angle", "tolerance", "rest"))
      if (object.has(field) && type != ChallengeType.SEAL) throw invalid(path + "." + field, "only seals take " + field);
    if (type == ChallengeType.PUMPS)
      engine = new Engine(object.has("rpm") ? integer(object, "rpm", path, 1, 256) : 0, 45, 3, 60);
    if (type == ChallengeType.SEAL)
      engine = new Engine(0, object.has("angle") ? integer(object, "angle", path, 0, 89) : 45,
          object.has("tolerance") ? integer(object, "tolerance", path, 1, 20) : 3,
          object.has("rest") ? integer(object, "rest", path, 1, 1200) : 60);
    return new Challenge(id, type, requires, item, count, need, boss, engine);
  }

  private static List<String> references(JsonElement element, Map<String, Challenge> challenges, String path) {
    if (!element.isJsonArray()) throw invalid(path, "expected an array of challenge ids");
    List<String> result = new ArrayList<>();
    for (JsonElement entry : (JsonArray) element) {
      if (!entry.isJsonPrimitive() || !challenges.containsKey(entry.getAsString()))
        throw invalid(path, "unknown challenge " + entry);
      if (!result.contains(entry.getAsString())) result.add(entry.getAsString());
    }
    return result;
  }

  /**
   * Pieces whose ruin is skipped for a missing mod move to the pedestal of the same act's landmark
   * (Elias, 25 September). Returns landmark id to the skipped definitions it adopts; a skipped
   * ruin without a landmark in its act (a data error) maps to nothing.
   */
  public static Map<String, List<Definition>> fallbacks(Collection<Definition> definitions,
      Predicate<String> modLoaded) {
    Map<String, List<Definition>> result = new TreeMap<>();
    for (Definition skipped : definitions) {
      if (skipped.available(modLoaded)) continue;
      definitions.stream()
          .filter(d -> d.scale() == Scale.LANDMARK && d.act() == skipped.act() && d.available(modLoaded))
          .findFirst()
          .ifPresent(landmark -> result.computeIfAbsent(landmark.id(), k -> new ArrayList<>()).add(skipped));
    }
    return result;
  }

  /** Problems across the whole set: ids, pieces or templates used twice, landmarks per act. */
  public static List<String> problems(Collection<Definition> definitions) {
    List<String> problems = new ArrayList<>();
    Map<String, String> pieces = new HashMap<>(), templates = new HashMap<>();
    Map<Integer, List<String>> landmarks = new TreeMap<>();
    for (Definition definition : definitions) {
      String piece = pieces.put(definition.piece(), definition.id());
      if (piece != null) problems.add(definition.piece() + " is the piece of both " + piece + " and " + definition.id());
      String template = templates.put(definition.template(), definition.id());
      if (template != null) problems.add(definition.template() + " is used by " + template + " and " + definition.id());
      if (definition.scale() == Scale.LANDMARK)
        landmarks.computeIfAbsent(definition.act(), k -> new ArrayList<>()).add(definition.id());
    }
    landmarks.forEach((act, ids) -> {
      if (ids.size() > 1) problems.add("act " + act + " has more than one landmark: " + ids);
    });
    for (Definition definition : definitions)
      if (definition.scale() == Scale.MEDIUM && !landmarks.containsKey(definition.act())
          && !definition.mods().isEmpty())
        problems.add(definition.id() + " has no landmark in act " + definition.act() + " to adopt its piece");
    return problems;
  }

  private static void allow(JsonObject object, String path, String... fields) {
    Set<String> allowed = Set.of(fields);
    for (String field : object.keySet())
      if (!allowed.contains(field)) throw invalid(path, "unknown field " + field);
  }

  private static String string(JsonObject parent, String field, String path) {
    JsonElement element = parent.get(field);
    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
        || element.getAsString().isBlank())
      throw invalid(path + "." + field, "expected a nonempty string");
    return element.getAsString();
  }

  private static int integer(JsonObject parent, String field, String path, int min, int max) {
    JsonElement element = parent.get(field);
    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
      throw invalid(path + "." + field, "expected an integer");
    int value;
    try {
      value = element.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw invalid(path + "." + field, "expected an integer");
    }
    if (value < min || value > max) throw invalid(path + "." + field, "expected " + min + ".." + max);
    return value;
  }

  private static String location(String value, String path) {
    if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw invalid(path, "expected namespace:path, got " + value);
    return value;
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
    for (E constant : type.getEnumConstants())
      if (constant.name().toLowerCase(Locale.ROOT).equals(value)) return constant;
    throw invalid(path, "unknown value " + value);
  }

  private static IllegalArgumentException invalid(String path, String message) {
    return new IllegalArgumentException("Entrelumen ruin " + path + ": " + message);
  }
}
