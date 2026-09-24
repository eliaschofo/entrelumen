package dev.entrelumen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure rules of Solsticio's commerce: the shop and native tables ({@code data/entrelumen/
 * solsticio_shops/<type>.json} and {@code solsticio_natives/<discipline>.json}), price multipliers,
 * restocking, the trading hall zone, how common villagers are spread under their cap and where
 * natives stand. Registry-free and unit-tested; {@link SolsticioCommerce} turns it into villagers.
 * See {@code docs/design/solsticio-commerce.md}.
 */
public final class CommerceRules {
  private CommerceRules() {}

  /** The shop types of the {@code shop:<type>} marker, in the controller's order. */
  public static final List<String> SHOP_TYPES = List.of("bookstore", "rarities", "parts", "seeds", "smithy",
      "apothecary", "maps", "minerals", "records", "textiles", "nursery", "creatures", "museum", "bakery",
      "apiary", "curiosities");

  /** The emerald, the default currency of a price written as a bare number. */
  public static final String EMERALD = "minecraft:emerald";
  public static final int DEFAULT_MAX_USES = 4;
  public static final float DEFAULT_PRICE_MULTIPLIER = 0.05F;
  /** Every villager placed by the city stands at this trade level (Master): it never levels up. */
  public static final int MERCHANT_LEVEL = 5;
  /** Side-quest NPCs take one of this many innkeeper personas (name and greeting). */
  public static final int INNKEEPERS = 4;
  /** Common villagers' ambient lines, picked at random on a right-click. */
  public static final int TOWNSFOLK_LINES = 8;
  /** Common villagers stroll this far from their home marker at most. */
  public static final int TOWNSFOLK_RANGE = 12;
  /** The trading hall zone around its marker: horizontal radius comes from the config. */
  public static final int HALL_DOWN = 2, HALL_UP = 12;

  static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

  /** What a villager placed or adopted by Solsticio is. */
  public enum Role {
    /** Behind a shop counter: no AI, the shop's table, bound to its marker. */
    SHOP("shop"),
    /** A native of the trading hall: no AI, asks for a biome, sells a Luminosity once there. */
    NATIVE("native"),
    /** A common villager at a {@code resident} home: AI, strolls around it. */
    TOWNSFOLK("townsfolk"),
    /** The NPC of a side quest: no AI, a short dialogue, bound to its marker. */
    SIDEQUEST("sidequest"),
    /** A villager from elsewhere that settled in the trading hall: permanent discount. */
    MOVED("moved");

    public final String id;

    Role(String id) {
      this.id = id;
    }

    public static Role byId(String id) {
      for (Role role : values()) if (role.id.equals(id)) return role;
      return null;
    }

    /** Placed at a marker and put back there if it dies or is carried away. */
    public boolean bound() {
      return this == SHOP || this == SIDEQUEST;
    }
  }

  // ---- prices ---------------------------------------------------------------------------

  /**
   * Price multipliers, each in (0, 1]: the whole city once the Entrelumen is liberated, a villager
   * that moved into the trading hall, and a native that has reached its biome.
   */
  public record Prices(double liberated, double moved, double nativeHome) {
    public static final Prices DEFAULT = new Prices(0.6, 0.5, 0.5);

    public Prices {
      for (double value : new double[] {liberated, moved, nativeHome})
        if (!(value > 0 && value <= 1)) throw new IllegalArgumentException("Multiplier out of (0, 1]: " + value);
    }
  }

  /** The multiplier on a Solsticio villager's first cost; 1 for villagers without a role. */
  public static double multiplier(Role role, boolean liberated, boolean awakened, Prices prices) {
    if (role == null) return 1;
    double multiplier = switch (role) {
      case SHOP -> 1;
      case MOVED -> prices.moved();
      case NATIVE -> awakened ? prices.nativeHome() : 1;
      case TOWNSFOLK, SIDEQUEST -> 1;
    };
    if (liberated && role != Role.TOWNSFOLK && role != Role.SIDEQUEST) multiplier *= prices.liberated();
    return multiplier;
  }

  /**
   * A price after a multiplier: rounded to the nearest item, at least one item cheaper whenever
   * there is a discount (so it always shows), and never below one.
   */
  public static int discounted(int price, double multiplier) {
    if (multiplier >= 1 || price <= 1) return price;
    return Math.max(1, Math.min(price - 1, (int) Math.round(price * multiplier)));
  }

  /**
   * The amount to add to an offer's special price difference so that its first cost becomes
   * {@link #discounted} of what it is now. {@code base}, {@code demand}, {@code priceMultiplier}
   * and {@code special} are the offer's own values; the current cost mirrors vanilla: base plus
   * the demand surcharge plus the special difference, clamped to 1..{@code maxStack}.
   */
  public static int priceAdjustment(int base, int demand, float priceMultiplier, int special, int maxStack,
      double multiplier) {
    if (multiplier >= 1) return 0;
    int surcharge = Math.max(0, (int) Math.floor((float) (base * demand) * priceMultiplier));
    int raw = base + surcharge + special;
    int current = Math.clamp(raw, 1, Math.max(1, maxStack));
    return discounted(current, multiplier) - raw;
  }

  /** Whether a lazily restocked merchant is due: never stocked, the interval passed, or time went back. */
  public static boolean restockDue(long now, long last, long interval) {
    return last <= 0 || now < last || now - last >= interval;
  }

  // ---- places ---------------------------------------------------------------------------

  /** Whether a position is inside the trading hall zone around its marker. */
  public static boolean inHall(int x, int y, int z, int hallX, int hallY, int hallZ, int radius) {
    return Math.abs(x - hallX) <= radius && Math.abs(z - hallZ) <= radius && y >= hallY - HALL_DOWN
        && y <= hallY + HALL_UP;
  }

  /**
   * Which of {@code total} homes (in marker order) get a common villager when at most {@code cap}
   * may live in the city: all of them, or {@code cap} evenly spread over the list.
   */
  public static List<Integer> spread(int total, int cap) {
    List<Integer> chosen = new ArrayList<>();
    if (total <= 0 || cap <= 0) return chosen;
    if (cap >= total) {
      for (int i = 0; i < total; i++) chosen.add(i);
      return chosen;
    }
    for (int i = 0; i < cap; i++) chosen.add((int) ((long) i * total / cap));
    return chosen;
  }

  /**
   * Offsets (x, z) around the trading hall marker where natives may stand, nearest first; the
   * runtime keeps the first standable ones.
   */
  public static final List<int[]> NATIVE_RING = List.of(new int[] {2, 0}, new int[] {-2, 0}, new int[] {0, 2},
      new int[] {0, -2}, new int[] {2, 2}, new int[] {-2, -2}, new int[] {2, -2}, new int[] {-2, 2},
      new int[] {3, 0}, new int[] {-3, 0}, new int[] {0, 3}, new int[] {0, -3}, new int[] {4, 0},
      new int[] {-4, 0}, new int[] {0, 4}, new int[] {0, -4}, new int[] {1, 0}, new int[] {-1, 0},
      new int[] {0, 1}, new int[] {0, -1});

  /** Side-quest ids in a stable order: numeric prefix first ({@code 7_inn} before {@code 12_inn}). */
  static final Comparator<String> SIDEQUEST_ORDER = Comparator.comparingLong(CommerceRules::numericPrefix)
      .thenComparing(Comparator.naturalOrder());

  private static long numericPrefix(String id) {
    int end = 0;
    while (end < id.length() && end < 18 && Character.isDigit(id.charAt(end))) end++;
    return end == 0 ? Long.MAX_VALUE : Long.parseLong(id.substring(0, end));
  }

  /** The innkeeper persona of a side quest: its rank among all side-quest ids, wrapped. */
  public static int persona(String id, Collection<String> ids) {
    List<String> sorted = ids.stream().distinct().sorted(SIDEQUEST_ORDER).toList();
    int rank = sorted.indexOf(id);
    return Math.floorMod(rank < 0 ? id.hashCode() : rank, INNKEEPERS);
  }

  // ---- tables ---------------------------------------------------------------------------

  /** A cost: item id and count (1..64). */
  public record Cost(String item, int count) {}

  /**
   * A map sold blank that charts the nearest structure of a tag ({@code #namespace:path}) when used
   * in the Overworld; {@code name} is the translation key of the charted map.
   */
  public record Survey(String structures, String decoration, String name) {}

  /** A written book of Heliodor lore: {@code entrelumen.solsticio.lore.<id>.*} keys. */
  public record LoreBook(String id, int pages) {}

  /**
   * What an offer sells. Either one item ({@code item}, with optional enchantments, raw data
   * components, survey or lore book) or, with {@code tag}, one offer per tagged item that the
   * table does not already sell.
   */
  public record ItemSpec(String item, int count, Map<String, Integer> enchantments, JsonObject components,
      Survey survey, LoreBook book, String tag) {
    public boolean expandsTag() {
      return tag != null;
    }
  }

  /** One offer of a table. */
  public record OfferSpec(ItemSpec sell, Cost price, Cost extra, int maxUses, int xp, float priceMultiplier,
      ProtectionRules.ActGate gate) {}

  /**
   * A shop or native table. Natives also carry the biomes they ask for (ids or {@code #tags}) and
   * their Luminosity offer, locked until they reach one of those biomes.
   */
  public record Table(String id, String profession, String villagerType, List<OfferSpec> offers,
      List<String> biomes, OfferSpec luminosity) {
    public boolean isNative() {
      return luminosity != null;
    }

    /** Explicit item ids this table sells, which a tag offer skips. */
    public List<String> explicitItems() {
      List<String> items = new ArrayList<>();
      for (OfferSpec offer : offers) if (!offer.sell().expandsTag()) items.add(offer.sell().item());
      if (luminosity != null) items.add(luminosity.sell().item());
      return items;
    }
  }

  /** A parsed table (null when unusable) and every problem found; bad offers are dropped. */
  public record Parsed(Table table, List<String> problems) {}

  /** Parses a shop table; {@code nativeTable} demands {@code biomes} and {@code luminosity}. */
  public static Parsed parse(String id, JsonElement root, boolean nativeTable) {
    List<String> problems = new ArrayList<>();
    if (root == null || !root.isJsonObject()) {
      problems.add(id + ": not a JSON object");
      return new Parsed(null, problems);
    }
    JsonObject json = root.getAsJsonObject();
    String profession = string(json, "profession", "minecraft:none");
    String type = string(json, "villager_type", "minecraft:plains");
    if (!ID.matcher(profession).matches()) problems.add(id + ": bad profession " + profession);
    if (!ID.matcher(type).matches()) problems.add(id + ": bad villager_type " + type);
    ProtectionRules.ActGate defaultGate = ProtectionRules.ActGate.NONE;
    if (json.has("gate")) {
      defaultGate = gate(json.get("gate"), id + ".gate", problems);
      if (defaultGate == null) defaultGate = ProtectionRules.ActGate.NONE;
    }
    List<OfferSpec> offers = new ArrayList<>();
    if (json.has("offers") && json.get("offers").isJsonArray()) {
      int index = 0;
      for (JsonElement element : json.getAsJsonArray("offers")) {
        OfferSpec offer = offer(element, id + ".offers[" + index++ + "]", defaultGate, problems);
        if (offer != null) offers.add(offer);
      }
    } else if (!nativeTable) {
      problems.add(id + ": no offers array");
    }
    List<String> biomes = new ArrayList<>();
    OfferSpec luminosity = null;
    if (nativeTable) {
      if (json.has("biomes") && json.get("biomes").isJsonArray()) {
        for (JsonElement element : json.getAsJsonArray("biomes")) {
          String biome = element.isJsonPrimitive() ? element.getAsString() : "";
          if (ID.matcher(biome.startsWith("#") ? biome.substring(1) : biome).matches()) biomes.add(biome);
          else problems.add(id + ": bad biome " + element);
        }
      }
      if (biomes.isEmpty()) problems.add(id + ": a native needs at least one biome");
      if (json.has("luminosity")) luminosity = offer(json.get("luminosity"), id + ".luminosity", defaultGate, problems);
      if (luminosity != null && luminosity.sell().expandsTag()) {
        problems.add(id + ": the Luminosity offer cannot be a tag");
        luminosity = null;
      }
      if (luminosity == null) problems.add(id + ": a native needs a luminosity offer");
      if (biomes.isEmpty() || luminosity == null) return new Parsed(null, problems);
    }
    return new Parsed(new Table(id, profession, type, List.copyOf(offers), List.copyOf(biomes), luminosity),
        problems);
  }

  private static OfferSpec offer(JsonElement element, String where, ProtectionRules.ActGate defaultGate,
      List<String> problems) {
    if (!element.isJsonObject()) {
      problems.add(where + ": not an object");
      return null;
    }
    JsonObject json = element.getAsJsonObject();
    ItemSpec sell = json.has("sell") ? item(json.get("sell"), where + ".sell", problems) : null;
    if (sell == null) {
      if (!json.has("sell")) problems.add(where + ": no sell");
      return null;
    }
    Cost price = json.has("price") ? cost(json.get("price"), where + ".price", problems) : null;
    if (price == null) {
      if (!json.has("price")) problems.add(where + ": no price");
      return null;
    }
    Cost extra = null;
    if (json.has("extra")) {
      extra = cost(json.get("extra"), where + ".extra", problems);
      if (extra == null) return null;
    }
    int maxUses = integer(json, "max_uses", DEFAULT_MAX_USES);
    if (maxUses < 1 || maxUses > 128) {
      problems.add(where + ": max_uses " + maxUses + " outside 1..128");
      return null;
    }
    int xp = integer(json, "xp", 0);
    if (xp < 0 || xp > 1000) {
      problems.add(where + ": xp " + xp + " outside 0..1000");
      return null;
    }
    float multiplier = json.has("price_multiplier") ? json.get("price_multiplier").getAsFloat() : DEFAULT_PRICE_MULTIPLIER;
    if (!(multiplier >= 0 && multiplier <= 1)) {
      problems.add(where + ": price_multiplier " + multiplier + " outside 0..1");
      return null;
    }
    ProtectionRules.ActGate gate = defaultGate;
    if (json.has("gate")) {
      gate = gate(json.get("gate"), where + ".gate", problems);
      if (gate == null) return null;
    }
    return new OfferSpec(sell, price, extra, maxUses, xp, multiplier, gate);
  }

  private static ItemSpec item(JsonElement element, String where, List<String> problems) {
    if (element.isJsonPrimitive()) {
      String id = element.getAsString();
      if (!ID.matcher(id).matches()) {
        problems.add(where + ": bad item id " + id);
        return null;
      }
      return new ItemSpec(id, 1, Map.of(), null, null, null, null);
    }
    if (!element.isJsonObject()) {
      problems.add(where + ": not an item");
      return null;
    }
    JsonObject json = element.getAsJsonObject();
    int count = integer(json, "count", 1);
    if (count < 1 || count > 64) {
      problems.add(where + ": count " + count + " outside 1..64");
      return null;
    }
    if (json.has("tag")) {
      String tag = json.get("tag").getAsString();
      if (tag.startsWith("#")) tag = tag.substring(1);
      if (!ID.matcher(tag).matches()) {
        problems.add(where + ": bad tag " + tag);
        return null;
      }
      if (json.has("id") || json.has("enchantments") || json.has("components") || json.has("survey")
          || json.has("lore_book")) {
        problems.add(where + ": a tag offer sells plain items only");
        return null;
      }
      return new ItemSpec(null, count, Map.of(), null, null, null, tag);
    }
    String id = string(json, "id", "");
    if (!ID.matcher(id).matches()) {
      problems.add(where + ": bad item id '" + id + "'");
      return null;
    }
    Map<String, Integer> enchantments = new LinkedHashMap<>();
    if (json.has("enchantments")) {
      if (!json.get("enchantments").isJsonObject()) {
        problems.add(where + ": enchantments is not an object");
        return null;
      }
      for (var entry : json.getAsJsonObject("enchantments").entrySet()) {
        int level = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsInt() : 0;
        if (!ID.matcher(entry.getKey()).matches() || level < 1 || level > 255) {
          problems.add(where + ": bad enchantment " + entry.getKey() + " " + entry.getValue());
          return null;
        }
        enchantments.put(entry.getKey(), level);
      }
    }
    JsonObject components = null;
    if (json.has("components")) {
      if (!json.get("components").isJsonObject()) {
        problems.add(where + ": components is not an object");
        return null;
      }
      components = json.getAsJsonObject("components").deepCopy();
    }
    Survey survey = null;
    if (json.has("survey")) {
      JsonObject s = json.get("survey").isJsonObject() ? json.getAsJsonObject("survey") : new JsonObject();
      String structures = string(s, "structures", "");
      String decoration = string(s, "decoration", "");
      String name = string(s, "name", "");
      if (!structures.startsWith("#") || !ID.matcher(structures.substring(1)).matches()
          || !ID.matcher(decoration).matches() || name.isBlank()) {
        problems.add(where + ": a survey needs a #structure tag, a decoration and a name");
        return null;
      }
      survey = new Survey(structures, decoration, name);
    }
    LoreBook book = null;
    if (json.has("lore_book")) {
      JsonObject b = json.get("lore_book").isJsonObject() ? json.getAsJsonObject("lore_book") : new JsonObject();
      String bookId = string(b, "id", "");
      int pages = integer(b, "pages", 0);
      if (!bookId.matches("[a-z0-9_]{1,40}") || pages < 1 || pages > 20) {
        problems.add(where + ": a lore book needs an id and 1..20 pages");
        return null;
      }
      book = new LoreBook(bookId, pages);
    }
    return new ItemSpec(id, count, Map.copyOf(enchantments), components, survey, book, null);
  }

  private static Cost cost(JsonElement element, String where, List<String> problems) {
    String item = EMERALD;
    int count;
    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
      count = element.getAsInt();
    } else if (element.isJsonObject()) {
      item = string(element.getAsJsonObject(), "id", "");
      count = integer(element.getAsJsonObject(), "count", 1);
    } else {
      problems.add(where + ": a cost is a number of emeralds or {id, count}");
      return null;
    }
    if (!ID.matcher(item).matches()) {
      problems.add(where + ": bad item id '" + item + "'");
      return null;
    }
    if (count < 1 || count > 64) {
      problems.add(where + ": count " + count + " outside 1..64");
      return null;
    }
    return new Cost(item, count);
  }

  private static ProtectionRules.ActGate gate(JsonElement element, String where, List<String> problems) {
    if (!element.isJsonObject()) {
      problems.add(where + ": a gate is {act, milestone}");
      return null;
    }
    JsonObject json = element.getAsJsonObject();
    int act = integer(json, "act", 1);
    String milestone = string(json, "milestone", "");
    if (act < 1 || act > 99 || !milestone.matches("[a-z0-9_]*")) {
      problems.add(where + ": bad gate " + element);
      return null;
    }
    return act == 1 && milestone.isEmpty() ? ProtectionRules.ActGate.NONE : new ProtectionRules.ActGate(act, milestone);
  }

  private static String string(JsonObject json, String key, String fallback) {
    return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
  }

  private static int integer(JsonObject json, String key, int fallback) {
    try {
      return json.has(key) ? json.get(key).getAsInt() : fallback;
    } catch (RuntimeException notANumber) {
      return Integer.MIN_VALUE;
    }
  }
}
