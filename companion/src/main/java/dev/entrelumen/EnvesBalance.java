package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.entrelumen.ApotheosisTiers.Tier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The numbers of the Envés's content (docs/design/dungeon-enves.md, «Contenido: números»): how echoes
 * scale with the World Tier of the attempt and the floor, the base of each role, how many affixes an
 * elite carries, how rare the loot is and how the puzzles, seals and the boss are tuned. Read from
 * {@code data/entrelumen/enves/balance.json}; every key is optional and falls back to
 * {@link #DEFAULTS}. Pure: no registries, unit-tested.
 */
public final class EnvesBalance {
  private EnvesBalance() {}

  /** Who an echo is in its room. */
  public enum Role {
    /** Weak company of an elite; no affixes, no drops. */
    ESCORT,
    /** The core of a fight: affixes by floor, a shard and a small chance of a gem. */
    ELITE,
    /** Stands at a seal (the guardian variant): one affix more than the floor's elites. */
    GUARDIAN,
    /** The stair's champion in the guard room: three affixes, and the stair waits for it. */
    CHAMPION,
    /** The White Wither. */
    BOSS,
    /** The grottol of the Geodes: runs, and pays if caught. */
    TREASURE;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Role byId(String id) {
      for (Role role : values()) if (role.id().equals(id)) return role;
      return ESCORT;
    }
  }

  /** Apotheosis's rarities, lowest first; {@link #id} is the registry id. */
  public enum Rarity {
    COMMON, UNCOMMON, RARE, EPIC, MYTHIC;

    public String id() {
      return "apotheosis:" + name().toLowerCase(Locale.ROOT);
    }

    public static Rarity byName(String name) {
      String key = name.toLowerCase(Locale.ROOT).replace("apotheosis:", "");
      for (Rarity rarity : values()) if (rarity.name().toLowerCase(Locale.ROOT).equals(key)) return rarity;
      throw new IllegalArgumentException("Unknown rarity " + name);
    }
  }

  /** Gem purities, lowest first (Apotheosis's {@code Purity}). */
  public static final List<String> PURITIES = List.of("cracked", "chipped", "flawed", "normal", "flawless", "perfect");

  /**
   * One World Tier: multipliers on echo health and damage, armor every echo gains, the rarity weights
   * of loot on floor I (common..mythic), the tier's top rarity (a cap, and what the boss chest gives),
   * and the base gem purity (an index of {@link #PURITIES}).
   */
  public record TierScale(double health, double damage, double armor, List<Integer> rarity, Rarity top, int purity) {
    public TierScale {
      rarity = List.copyOf(rarity);
      if (rarity.size() != Rarity.values().length) throw new IllegalArgumentException("rarity needs five weights");
      if (rarity.stream().mapToInt(Integer::intValue).sum() <= 0) throw new IllegalArgumentException("rarity weights sum to 0");
      if (health <= 0 || damage < 0 || armor < 0) throw new IllegalArgumentException("bad tier scale");
      if (purity < 0 || purity >= PURITIES.size()) throw new IllegalArgumentException("bad purity");
    }
  }

  /** One floor (I..V): multipliers, the chance that loot rolls one rarity higher, and extra gem purity. */
  public record FloorScale(double health, double damage, double rarityStep, int purity) {
    public FloorScale {
      if (health <= 0 || damage < 0 || rarityStep < 0 || rarityStep > 1 || purity < 0)
        throw new IllegalArgumentException("bad floor scale");
    }
  }

  /** A role's base: max health at Frontier floor I, a multiplier on the mob's own damage, experience. */
  public record RoleBase(double health, double damage, int xp) {
    public RoleBase {
      if (health <= 0 || damage < 0 || xp < 0) throw new IllegalArgumentException("bad role base");
    }
  }

  /** The seals: how long and how wide the circle is held. */
  public record Seals(int circleSeconds, double circleRadius, int circleGraceSeconds) {}

  /**
   * Vaults and puzzles: shards the offering lock asks per floor, the length of the brazier echo per
   * floor (I..IV) and how many mirrors the light has to turn on at least, per floor.
   */
  public record Puzzles(List<Integer> offeringShards, List<Integer> echoLength, List<Integer> mirrorTurns) {
    public Puzzles {
      offeringShards = List.copyOf(offeringShards);
      echoLength = List.copyOf(echoLength);
      mirrorTurns = List.copyOf(mirrorTurns);
    }
  }

  /** The White Wither's attacks, before scaling: skull and charge damage, the telegraph and the opening. */
  public record Boss(double skullDamage, double chargeDamage, int telegraphTicks, int exposedTicks, double exposedBonus,
      double hover, int chargeCooldown) {}

  public record Settings(Map<Tier, TierScale> tiers, List<FloorScale> floors, Map<Role, RoleBase> roles,
      List<List<Integer>> eliteAffixes, int championAffixes, double fortuneStep, double grottolChance,
      Seals seals, Puzzles puzzles, Boss boss) {
    public Settings {
      tiers = Map.copyOf(tiers);
      floors = List.copyOf(floors);
      roles = Map.copyOf(roles);
      eliteAffixes = eliteAffixes.stream().map(List::copyOf).toList();
      for (Tier tier : Tier.values()) if (!tiers.containsKey(tier)) throw new IllegalArgumentException("missing tier " + tier);
      for (Role role : Role.values()) if (!roles.containsKey(role)) throw new IllegalArgumentException("missing role " + role);
      if (floors.size() != EnvesLayout.FLOORS) throw new IllegalArgumentException("floors needs five entries");
      if (eliteAffixes.size() != EnvesLayout.FLOORS) throw new IllegalArgumentException("elite_affixes needs five [min, max]");
      for (var range : eliteAffixes)
        if (range.size() != 2 || range.get(0) < 0 || range.get(1) < range.get(0) || range.get(1) > EnvesAffix.values().length)
          throw new IllegalArgumentException("bad elite affix range " + range);
      if (championAffixes < 0 || championAffixes > EnvesAffix.values().length) throw new IllegalArgumentException("champion_affixes");
      if (fortuneStep < 0 || fortuneStep > 1 || grottolChance < 0 || grottolChance > 1)
        throw new IllegalArgumentException("chances must be 0..1");
    }

    public TierScale tier(Tier tier) {
      return tiers.get(tier);
    }

    /** Floor 1..5 (the vestibule and out-of-range depths clamp). */
    public FloorScale floor(int depth) {
      return floors.get(Math.clamp(depth, 1, EnvesLayout.FLOORS) - 1);
    }

    public RoleBase role(Role role) {
      return roles.get(role);
    }

    // ---- Echoes -------------------------------------------------------------------------------

    /** Max health of an echo: its table entry's base (or the role's) times the tier and the floor. */
    public double health(Role role, Double entryHealth, Tier tier, int depth) {
      double base = entryHealth != null ? entryHealth : role(role).health();
      return Math.max(1, Math.round(base * tier(tier).health() * floor(depth).health()));
    }

    /** What an echo's own damage is multiplied by: entry or role, times the tier and the floor. */
    public double damage(Role role, Double entryDamage, Tier tier, int depth) {
      double base = entryDamage != null ? entryDamage : role(role).damage();
      return base * tier(tier).damage() * floor(depth).damage();
    }

    /** Experience an echo drops: the role's, scaled like its health by the tier. */
    public int xp(Role role, Tier tier) {
      return (int) Math.round(role(role).xp() * tier(tier).health());
    }

    /** How many affixes an echo of this role carries on this floor. */
    public int affixCount(Role role, int depth, Random random) {
      return switch (role) {
        case ELITE -> roll(eliteAffixes.get(Math.clamp(depth, 1, EnvesLayout.FLOORS) - 1), random);
        case GUARDIAN -> Math.min(Math.max(championAffixes, 1),
            roll(eliteAffixes.get(Math.clamp(depth, 1, EnvesLayout.FLOORS) - 1), random) + 1);
        case CHAMPION -> championAffixes;
        default -> 0;
      };
    }

    private static int roll(List<Integer> range, Random random) {
      int min = range.get(0), max = range.get(1);
      return min + (max > min ? random.nextInt(max - min + 1) : 0);
    }

    // ---- Loot ---------------------------------------------------------------------------------

    /**
     * The rarity of one affixed piece: drawn from the tier's weights, one step higher with the
     * floor's chance (and the Fortune blessing's), plus {@code bonus} steps, never above the tier's top.
     * {@code top} gives the top outright (the boss chest).
     */
    public Rarity rarity(Tier tier, int depth, int bonus, boolean top, boolean fortune, Random random) {
      TierScale scale = tier(tier);
      if (top) return scale.top();
      int total = scale.rarity().stream().mapToInt(Integer::intValue).sum();
      int pick = random.nextInt(total), index = 0;
      for (int i = 0; i < scale.rarity().size(); i++) {
        pick -= scale.rarity().get(i);
        if (pick < 0) {
          index = i;
          break;
        }
      }
      if (random.nextDouble() < floor(depth).rarityStep()) index++;
      if (fortune && random.nextDouble() < fortuneStep) index++;
      index += bonus;
      return Rarity.values()[Math.clamp(index, 0, scale.top().ordinal())];
    }

    /** A gem's purity index ({@link #PURITIES}): the tier's, the floor's and a bonus, at most perfect. */
    public int purity(Tier tier, int depth, int bonus) {
      return Math.clamp(tier(tier).purity() + floor(depth).purity() + bonus, 0, PURITIES.size() - 1);
    }

    // ---- Puzzles ------------------------------------------------------------------------------

    public int offeringShards(int depth) {
      return pickFloor(puzzles.offeringShards(), depth);
    }

    public int echoLength(int depth) {
      return pickFloor(puzzles.echoLength(), depth);
    }

    public int mirrorTurns(int depth) {
      return pickFloor(puzzles.mirrorTurns(), depth);
    }

    private static int pickFloor(List<Integer> values, int depth) {
      return values.get(Math.clamp(depth, 1, values.size()) - 1);
    }
  }

  /** The loot's floor bonus: the whole part of per_floor × (depth − 1), plus one more with the fraction's chance. */
  public static int floorBonus(float perFloor, int depth, float roll) {
    float extra = Math.max(0, perFloor * (depth - 1));
    int whole = (int) Math.floor(extra);
    return whole + (roll < extra - whole ? 1 : 0);
  }

  private static TierScale tier(double health, double damage, double armor, List<Integer> rarity, Rarity top, int purity) {
    return new TierScale(health, damage, armor, rarity, top, purity);
  }

  public static final Settings DEFAULTS = new Settings(
      new EnumMap<>(Map.of(
          Tier.HAVEN, tier(0.75, 0.75, 0, List.of(60, 36, 4, 0, 0), Rarity.RARE, 1),
          Tier.FRONTIER, tier(1.0, 1.0, 2, List.of(29, 60, 10, 1, 0), Rarity.EPIC, 1),
          Tier.ASCENT, tier(1.6, 1.35, 4, List.of(10, 30, 50, 10, 0), Rarity.EPIC, 2),
          Tier.SUMMIT, tier(2.5, 1.8, 8, List.of(0, 12, 29, 54, 5), Rarity.MYTHIC, 3),
          Tier.PINNACLE, tier(3.8, 2.4, 12, List.of(0, 0, 10, 65, 25), Rarity.MYTHIC, 4))),
      List.of(new FloorScale(1.0, 1.0, 0.0, 0), new FloorScale(1.15, 1.08, 0.25, 0), new FloorScale(1.3, 1.16, 0.5, 1),
          new FloorScale(1.5, 1.25, 0.75, 1), new FloorScale(1.7, 1.35, 1.0, 2)),
      new EnumMap<>(Map.of(
          Role.ESCORT, new RoleBase(24, 1.0, 6),
          Role.ELITE, new RoleBase(70, 1.15, 20),
          Role.GUARDIAN, new RoleBase(110, 1.25, 35),
          Role.CHAMPION, new RoleBase(200, 1.35, 60),
          Role.BOSS, new RoleBase(600, 1.0, 300),
          Role.TREASURE, new RoleBase(20, 0, 10))),
      List.of(List.of(1, 1), List.of(1, 2), List.of(2, 2), List.of(2, 3), List.of(2, 3)), 3, 0.6, 0.2,
      new Seals(20, 4.5, 15),
      new Puzzles(List.of(3, 4, 5, 6), List.of(3, 4, 5, 5), List.of(2, 2, 3, 3)),
      new Boss(8, 14, 36, 70, 0.5, 2.5, 160));

  // ---- Parsing ------------------------------------------------------------------------------

  public static Settings parse(JsonElement element) {
    JsonObject root = element.getAsJsonObject();
    Settings d = DEFAULTS;
    Map<Tier, TierScale> tiers = new EnumMap<>(d.tiers());
    if (root.has("tiers"))
      for (var entry : root.getAsJsonObject("tiers").entrySet()) {
        Tier tier = Tier.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
        JsonObject o = entry.getValue().getAsJsonObject();
        TierScale was = tiers.get(tier);
        tiers.put(tier, new TierScale(num(o, "health", was.health()), num(o, "damage", was.damage()), num(o, "armor", was.armor()),
            o.has("rarity") ? ints(o.getAsJsonArray("rarity")) : was.rarity(),
            o.has("top") ? Rarity.byName(o.get("top").getAsString()) : was.top(), (int) num(o, "purity", was.purity())));
      }
    List<FloorScale> floors = new ArrayList<>(d.floors());
    if (root.has("floors")) {
      JsonArray array = root.getAsJsonArray("floors");
      if (array.size() != EnvesLayout.FLOORS) throw new IllegalArgumentException("floors needs five entries");
      for (int i = 0; i < array.size(); i++) {
        JsonObject o = array.get(i).getAsJsonObject();
        FloorScale was = floors.get(i);
        floors.set(i, new FloorScale(num(o, "health", was.health()), num(o, "damage", was.damage()),
            num(o, "rarity_step", was.rarityStep()), (int) num(o, "purity", was.purity())));
      }
    }
    Map<Role, RoleBase> roles = new EnumMap<>(d.roles());
    if (root.has("roles"))
      for (var entry : root.getAsJsonObject("roles").entrySet()) {
        Role role = Role.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
        JsonObject o = entry.getValue().getAsJsonObject();
        RoleBase was = roles.get(role);
        roles.put(role, new RoleBase(num(o, "health", was.health()), num(o, "damage", was.damage()), (int) num(o, "xp", was.xp())));
      }
    List<List<Integer>> affixes = d.eliteAffixes();
    int champion = d.championAffixes();
    if (root.has("affixes")) {
      JsonObject o = root.getAsJsonObject("affixes");
      if (o.has("elite")) {
        List<List<Integer>> list = new ArrayList<>();
        for (JsonElement range : o.getAsJsonArray("elite")) list.add(ints(range.getAsJsonArray()));
        affixes = list;
      }
      champion = (int) num(o, "champion", champion);
    }
    JsonObject loot = root.has("loot") ? root.getAsJsonObject("loot") : new JsonObject();
    Seals seals = d.seals();
    if (root.has("seals")) {
      JsonObject o = root.getAsJsonObject("seals");
      seals = new Seals((int) num(o, "circle_seconds", seals.circleSeconds()), num(o, "circle_radius", seals.circleRadius()),
          (int) num(o, "circle_grace_seconds", seals.circleGraceSeconds()));
      if (seals.circleSeconds() < 1 || seals.circleRadius() < 1 || seals.circleGraceSeconds() < 1)
        throw new IllegalArgumentException("bad seals");
    }
    Puzzles puzzles = d.puzzles();
    if (root.has("puzzles")) {
      JsonObject o = root.getAsJsonObject("puzzles");
      puzzles = new Puzzles(o.has("offering_shards") ? ints(o.getAsJsonArray("offering_shards")) : puzzles.offeringShards(),
          o.has("echo_length") ? ints(o.getAsJsonArray("echo_length")) : puzzles.echoLength(),
          o.has("mirror_turns") ? ints(o.getAsJsonArray("mirror_turns")) : puzzles.mirrorTurns());
      for (int v : puzzles.offeringShards()) if (v < 1 || v > 64) throw new IllegalArgumentException("offering_shards 1..64");
      for (int v : puzzles.echoLength()) if (v < 2 || v > 9) throw new IllegalArgumentException("echo_length 2..9");
      for (int v : puzzles.mirrorTurns()) if (v < 1 || v > 4) throw new IllegalArgumentException("mirror_turns 1..4");
      if (puzzles.offeringShards().isEmpty() || puzzles.echoLength().isEmpty() || puzzles.mirrorTurns().isEmpty())
        throw new IllegalArgumentException("puzzle lists cannot be empty");
    }
    Boss boss = d.boss();
    if (root.has("boss")) {
      JsonObject o = root.getAsJsonObject("boss");
      boss = new Boss(num(o, "skull_damage", boss.skullDamage()), num(o, "charge_damage", boss.chargeDamage()),
          (int) num(o, "telegraph_ticks", boss.telegraphTicks()), (int) num(o, "exposed_ticks", boss.exposedTicks()),
          num(o, "exposed_bonus", boss.exposedBonus()), num(o, "hover", boss.hover()),
          (int) num(o, "charge_cooldown", boss.chargeCooldown()));
      if (boss.telegraphTicks() < 10 || boss.exposedTicks() < 0 || boss.hover() < 0.5 || boss.hover() > 5
          || boss.chargeCooldown() < 40)
        throw new IllegalArgumentException("bad boss");
    }
    return new Settings(tiers, floors, roles, affixes, champion, num(loot, "fortune_step", d.fortuneStep()),
        num(root, "grottol_chance", d.grottolChance()), seals, puzzles, boss);
  }

  private static double num(JsonObject o, String key, double fallback) {
    return o.has(key) ? o.get(key).getAsDouble() : fallback;
  }

  private static List<Integer> ints(JsonArray array) {
    List<Integer> out = new ArrayList<>();
    for (JsonElement e : array) out.add(e.getAsInt());
    return out;
  }
}
