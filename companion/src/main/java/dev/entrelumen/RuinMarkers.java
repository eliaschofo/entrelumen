package dev.entrelumen;

import java.util.*;

/**
 * The marker convention of Heliodor ruin templates: DATA structure blocks whose metadata is
 * {@code <kind> key=value ...}. Placement reads them, puts their block in their cell and stores
 * them with the ruin, so final art keeps working as long as it keeps the markers. Pure and
 * unit-tested; docs/design/heliodor-ruins.md has the table for artists.
 *
 * <p>Every marker may carry {@code block=<block state>}: what stands in its cell once placed (air
 * when omitted). Kinds that place a block of their own (pedestal, mirror, socket, hidden, gate,
 * lock) ignore it. A challenge node can be any vanilla block: a campfire or a copper bulb shows
 * {@code lit}, a lever is read and reset, a stone only answers with a sound.
 */
public final class RuinMarkers {
  private RuinMarkers() {}

  public enum Kind {
    /** The key pedestal. */
    PEDESTAL,
    /** A Lootr container: {@code block} is a chest or a barrel, {@code loot} its table. */
    CHEST,
    /** A node lit or touched in order: {@code challenge}, {@code order} (default 1). */
    BRAZIER,
    /** Shows its challenge as solved ({@code lit}) for the team that last worked on it. */
    LAMP,
    /** A rotatable mirror: {@code challenge}, {@code facing} one of eight directions. */
    MIRROR,
    /** Where the mirrors must send the light: {@code challenge}. */
    RECEPTOR,
    /** A lever of a redstone lock: {@code challenge}, {@code on} (default true) is the answer. */
    LEVER,
    /** A redstone lock core: powered, it solves {@code challenge}. */
    LOCK,
    /** An offering socket: {@code challenge}, {@code item} (id or #tag), {@code count}. */
    SOCKET,
    /** A concealed trigger that looks like {@code look}: using it solves {@code challenge}. */
    HIDDEN,
    /** One cell of a per-team gate {@code id}; {@code look}, {@code climb=true} for a shaft. */
    GATE,
    /** Where a boss challenge raises its mobs: {@code challenge}. */
    BOSS,
    /** Water drained from a box of {@code size=x,y,z} (from this cell) once {@code challenge} is solved. */
    DRAIN,
    /** The volume that records a visit: {@code radius} (default 6) and {@code height} (default 5). */
    LORE,
    /** Where the ruin is entered; {@code spawn} is accepted for the start ruin. */
    ARRIVAL,
    /** The template layer that meets the terrain surface (default: layer 0). */
    GROUND,
    /**
     * Where players may build: a box of {@code size=x,y,z} from this cell, cut to a disc of
     * {@code radius} around its centre. Only the blocks of {@code #entrelumen:ruin/sandbox} go in.
     */
    SANDBOX,
    /** A missing piece the player puts back: only {@code block}'s block goes in this cell. */
    PART,
    /** A pump the engine drives: {@code challenge}, {@code turn} (+ or -), {@code intake} offset. */
    PUMP,
    /** Where a transmission meets the builders: {@code role} input, pump or seal. */
    PORT,
    /** The bearing of a seal ring: {@code challenge}; {@code scroll} is its movement mode. */
    SEAL,
    /** One cell of sluice {@code id}: open (air) while one of its levers is on. */
    SLUICE,
    /** A water wheel, placed last so that its frame has room. */
    WHEEL,
    /** A lectern with one of Terra's notes: {@code key}. */
    NOTE,
    /** The barrel where parts left in the ruin come back when it resets. */
    RETURNS,
    /** A block that stands only when {@code mod} is loaded (air otherwise). */
    MODBLOCK,
    /** A relay floor's light: {@code challenge}, {@code floor}; {@code hand=true} is lit by a player. */
    LIGHT,
    /**
     * A rotatable vitral of a relay: {@code challenge}, {@code floor}, {@code colour}, {@code turn}
     * (pass, up, north, east, south, west) and the {@code solve} state of the ruin's solution.
     */
    VITRAL,
    /** Gathers a relay floor's horizontal light and sends the union up: {@code challenge}, {@code floor}. */
    COLLECTOR,
    /**
     * A designed bed whose soil stays as drawn (planters, a pond, a garden): a box of {@code size=x,y,z}
     * from this cell. Elsewhere the template's soil becomes the site's own ground.
     */
    KEEP_SOIL;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** Kinds that belong to one challenge. */
    public boolean challenge() {
      return switch (this) {
        case BRAZIER, LAMP, MIRROR, RECEPTOR, LEVER, LOCK, SOCKET, HIDDEN, BOSS, DRAIN, PUMP, SEAL, LIGHT, VITRAL,
            COLLECTOR -> true;
        default -> false;
      };
    }

    /** Kinds whose cell holds a companion block instead of their {@code block} parameter. */
    public boolean ownBlock() {
      return this == PEDESTAL || this == MIRROR || this == SOCKET || this == HIDDEN || this == GATE
          || this == LOCK;
    }
  }

  public static final List<String> PORT_ROLES = List.of("input", "pump", "seal");
  public static final List<String> TURNS = List.of("pass", "up", "north", "east", "south", "west");
  public static final double MAX_SANDBOX_RADIUS = 16;

  /** The looks a hidden trigger or a gate cell can take ({@code seal} is a pale light). */
  public static final List<String> LOOKS = List.of("seal", "tuff_bricks", "polished_tuff", "calcite",
      "mossy_stone_bricks", "moss_block", "rooted_dirt", "polished_blackstone_bricks", "quartz_bricks",
      "end_stone_bricks");
  public static final List<String> SOCKET_LOOKS = List.of("pot", "altar");
  /** Eight directions, clockwise from north; {@link #DX}/{@link #DZ} give their steps. */
  public static final List<String> DIRECTIONS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");
  public static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1}, DZ = {-1, -1, 0, 1, 1, 1, 0, -1};
  public static final int MAX_LORE_RADIUS = 48, MAX_LORE_HEIGHT = 64, MAX_DRAIN = 96;

  /** One parsed marker: its kind and validated parameters. */
  public record Marker(Kind kind, Map<String, String> params) {
    public Marker {
      params = Collections.unmodifiableMap(new TreeMap<>(params));
    }

    public String param(String key, String fallback) {
      return params.getOrDefault(key, fallback);
    }

    public String challenge() {
      return params.getOrDefault("challenge", "");
    }

    public int order() {
      return Integer.parseInt(params.getOrDefault("order", "1"));
    }

    /** A relay marker's floor (1 when absent). */
    public int floor() {
      return Integer.parseInt(params.getOrDefault("floor", "1"));
    }

    public int count() {
      return Integer.parseInt(params.getOrDefault("count", "1"));
    }

    /** The block state string for this marker's cell, or empty for air. */
    public String block() {
      return params.getOrDefault("block", "");
    }

    /** {@code size=x,y,z} of a drain, a sandbox or a kept soil bed, in blocks from the marker cell. */
    public int[] size() {
      return triple("size", "1,1,1");
    }

    /** An {@code x,y,z} parameter, such as a pump's {@code intake} offset. */
    public int[] triple(String key, String fallback) {
      String[] parts = params.getOrDefault(key, fallback).split(",");
      return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    /** A sandbox's disc radius; 0 keeps the whole box. */
    public double radius() {
      return Double.parseDouble(params.getOrDefault("radius", "0"));
    }

    /** A pump's required turn: +1 or -1 (Create's speed sign along the pump's axis). */
    public int turn() {
      return "-".equals(params.get("turn")) ? -1 : 1;
    }

    /** The metadata that parses back to this marker. */
    public String metadata() {
      StringBuilder out = new StringBuilder(kind.id());
      params.forEach((key, value) -> out.append(' ').append(key).append('=').append(value));
      return out.toString();
    }
  }

  /**
   * Parses one DATA block's metadata. Empty for metadata that is not a ruin marker; an
   * {@link IllegalArgumentException} names what is wrong with a malformed ruin marker.
   */
  public static Optional<Marker> parse(String metadata) {
    if (metadata == null || metadata.isBlank()) return Optional.empty();
    String[] tokens = metadata.trim().split("\\s+");
    String name = tokens[0].toLowerCase(Locale.ROOT);
    if (name.startsWith("entrelumen:")) name = name.substring("entrelumen:".length());
    if (name.equals("spawn")) name = "arrival";
    Kind kind = null;
    for (Kind candidate : Kind.values())
      if (candidate.id().equals(name)) kind = candidate;
    if (kind == null) return Optional.empty();
    Map<String, String> params = new TreeMap<>();
    for (int i = 1; i < tokens.length; i++) {
      int eq = tokens[i].indexOf('=');
      if (eq <= 0 || eq == tokens[i].length() - 1)
        throw invalid(metadata, "expected key=value, got " + tokens[i]);
      String key = tokens[i].substring(0, eq).toLowerCase(Locale.ROOT);
      if (!key.matches("[a-z_]{1,32}")) throw invalid(metadata, "bad key " + key);
      if (params.put(key, tokens[i].substring(eq + 1)) != null)
        throw invalid(metadata, "duplicate key " + key);
    }
    validate(kind, params, metadata);
    return Optional.of(new Marker(kind, params));
  }

  private static void validate(Kind kind, Map<String, String> params, String metadata) {
    Set<String> allowed = new HashSet<>(switch (kind) {
      case PEDESTAL, ARRIVAL, GROUND -> Set.<String>of();
      case CHEST -> Set.of("loot");
      case BRAZIER -> Set.of("challenge", "order");
      case LAMP, LOCK, BOSS -> Set.of("challenge");
      case RECEPTOR -> Set.of("challenge", "floor", "target");
      case MIRROR -> Set.of("challenge", "facing", "floor", "solve");
      case LIGHT -> Set.of("challenge", "floor", "hand");
      case VITRAL -> Set.of("challenge", "floor", "colour", "turn", "solve");
      case COLLECTOR -> Set.of("challenge", "floor");
      case LEVER -> Set.of("challenge", "on", "sluice");
      case SOCKET -> Set.of("challenge", "item", "count", "look");
      case HIDDEN -> Set.of("challenge", "look");
      case GATE -> Set.of("id", "look", "climb");
      case DRAIN -> Set.of("challenge", "size");
      case LORE -> Set.of("radius", "height");
      case SANDBOX -> Set.of("size", "radius");
      case KEEP_SOIL -> Set.of("size");
      case PART, WHEEL, RETURNS -> Set.<String>of();
      case PUMP -> Set.of("challenge", "turn", "intake");
      case PORT -> Set.of("role");
      case SEAL -> Set.of("challenge", "scroll");
      case SLUICE -> Set.of("id");
      case NOTE -> Set.of("key");
      case MODBLOCK -> Set.of("mod");
    });
    allowed.add("block");
    for (String key : params.keySet())
      if (!allowed.contains(key)) throw invalid(metadata, "unknown key " + key + " for " + kind.id());
    if (kind.challenge()) requireId(params, "challenge", metadata);
    if (kind == Kind.GATE || kind == Kind.SLUICE) requireId(params, "id", metadata);
    if (kind == Kind.NOTE) requireId(params, "key", metadata);
    if (kind == Kind.MODBLOCK) requireId(params, "mod", metadata);
    if ((kind == Kind.PART || kind == Kind.MODBLOCK || kind == Kind.WHEEL || kind == Kind.PUMP || kind == Kind.SEAL)
        && !params.containsKey("block"))
      throw invalid(metadata, kind.id() + " needs block=");
    if (params.containsKey("block") && !params.get("block").matches(
        "([a-z0-9_.-]+:)?[a-z0-9_./-]+(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?"))
      throw invalid(metadata, "bad block state " + params.get("block"));
    switch (kind) {
      case BRAZIER -> integer(params, "order", 1, 64, false, metadata);
      case MIRROR -> {
        oneOf(params, "facing", DIRECTIONS, true, metadata);
        oneOf(params, "solve", DIRECTIONS, false, metadata);
        integer(params, "floor", 1, 64, false, metadata);
      }
      case RECEPTOR -> {
        integer(params, "floor", 1, 64, false, metadata);
        if (params.containsKey("target") && !params.get("target").matches("[a-z_]{1,24}(\\+[a-z_]{1,24})*"))
          throw invalid(metadata, "target must be colours joined by +");
      }
      case LIGHT -> {
        integer(params, "floor", 1, 64, true, metadata);
        oneOf(params, "hand", List.of("true", "false"), false, metadata);
      }
      case VITRAL -> {
        integer(params, "floor", 1, 64, true, metadata);
        if (params.get("colour") == null || !params.get("colour").matches("[a-z_]{1,24}"))
          throw invalid(metadata, "colour must be a colour name");
        oneOf(params, "turn", TURNS, false, metadata);
        oneOf(params, "solve", TURNS, false, metadata);
      }
      case COLLECTOR -> integer(params, "floor", 1, 64, true, metadata);
      case LEVER -> {
        oneOf(params, "on", List.of("true", "false"), false, metadata);
        if (params.containsKey("sluice")) requireId(params, "sluice", metadata);
      }
      case SANDBOX -> {
        size(params, metadata);
        String radius = params.get("radius");
        if (radius != null) {
          double value;
          try {
            value = Double.parseDouble(radius);
          } catch (NumberFormatException e) {
            throw invalid(metadata, "radius must be a number");
          }
          if (!(value > 0 && value <= MAX_SANDBOX_RADIUS)) throw invalid(metadata, "radius must be 0.." + MAX_SANDBOX_RADIUS);
        }
      }
      case PUMP -> {
        oneOf(params, "turn", List.of("+", "-"), true, metadata);
        if (params.containsKey("intake") && !params.get("intake").matches("-?\\d{1,3},-?\\d{1,3},-?\\d{1,3}"))
          throw invalid(metadata, "intake must be x,y,z");
      }
      case PORT -> oneOf(params, "role", PORT_ROLES, true, metadata);
      case SEAL -> integer(params, "scroll", 0, 64, false, metadata);
      case CHEST -> {
        if (params.containsKey("loot")) location(params.get("loot"), false, metadata);
      }
      case SOCKET -> {
        if (params.containsKey("item")) location(params.get("item"), true, metadata);
        integer(params, "count", 1, 64, false, metadata);
        oneOf(params, "look", SOCKET_LOOKS, false, metadata);
      }
      case HIDDEN -> oneOf(params, "look", LOOKS, false, metadata);
      case GATE -> {
        oneOf(params, "look", LOOKS, false, metadata);
        oneOf(params, "climb", List.of("true", "false"), false, metadata);
      }
      case DRAIN, KEEP_SOIL -> size(params, metadata);
      case LORE -> {
        integer(params, "radius", 0, MAX_LORE_RADIUS, false, metadata);
        integer(params, "height", 1, MAX_LORE_HEIGHT, false, metadata);
      }
      default -> {}
    }
  }

  private static void size(Map<String, String> params, String metadata) {
    String size = params.get("size");
    if (size == null || !size.matches("\\d{1,3},\\d{1,3},\\d{1,3}"))
      throw invalid(metadata, "size must be x,y,z");
    for (String part : size.split(","))
      if (Integer.parseInt(part) < 1 || Integer.parseInt(part) > MAX_DRAIN)
        throw invalid(metadata, "size must be 1.." + MAX_DRAIN + " per axis");
  }

  private static void requireId(Map<String, String> params, String key, String metadata) {
    String value = params.get(key);
    if (value == null || !value.matches("[a-z0-9_]{1,32}"))
      throw invalid(metadata, key + " must be 1..32 lowercase letters, digits or underscores");
  }

  private static void integer(Map<String, String> params, String key, int min, int max,
      boolean required, String metadata) {
    String value = params.get(key);
    if (value == null) {
      if (required) throw invalid(metadata, "missing " + key);
      return;
    }
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < min || parsed > max) throw invalid(metadata, key + " must be " + min + ".." + max);
    } catch (NumberFormatException e) {
      throw invalid(metadata, key + " must be an integer");
    }
  }

  private static void oneOf(Map<String, String> params, String key, List<String> values,
      boolean required, String metadata) {
    String value = params.get(key);
    if (value == null) {
      if (required) throw invalid(metadata, "missing " + key);
      return;
    }
    if (!values.contains(value)) throw invalid(metadata, key + " must be one of " + values);
  }

  /** Validates a resource location (or a {@code #tag} when allowed). */
  static void location(String value, boolean tagAllowed, String metadata) {
    boolean tag = value.startsWith("#");
    if (tag && !tagAllowed) throw invalid(metadata, "tags are not allowed: " + value);
    String id = tag ? value.substring(1) : value;
    if (!id.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) throw invalid(metadata, "bad id " + value);
  }

  private static IllegalArgumentException invalid(String metadata, String message) {
    return new IllegalArgumentException("Ruin marker '" + metadata + "': " + message);
  }

  // ---- Mirrors ------------------------------------------------------------------------------

  /** The direction (index into {@link #DIRECTIONS}) nearest to a horizontal offset; -1 for none. */
  public static int direction(int dx, int dz) {
    if (dx == 0 && dz == 0) return -1;
    double angle = Math.toDegrees(Math.atan2(dx, -dz));          // 0 = north, clockwise
    return Math.floorMod((int) Math.round(angle / 45.0), 8);
  }

  /** A mirror at {@code (mx, mz)} facing {@code facing} sends the light to a receptor at {@code (rx, rz)}. */
  public static boolean aimed(int facing, int mx, int mz, int rx, int rz) {
    int wanted = direction(rx - mx, rz - mz);
    return wanted < 0 || wanted == facing;
  }

  /** One click turns a mirror an eighth clockwise. */
  public static int turn(int facing) {
    return (facing + 1) % 8;
  }
}
