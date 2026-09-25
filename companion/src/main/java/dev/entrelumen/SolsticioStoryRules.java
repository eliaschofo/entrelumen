package dev.entrelumen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure rules of act VI, Solsticio ({@code docs/design/act-six.md}): the eleven story missions, the
 * innkeepers' errands that build the team's relation with Bodhi, the easter eggs, the relics each
 * team presents at the portal and the elections that follow the liberation. Registry-free and
 * unit-tested; {@link SolsticioStory} turns it into dialogue, items and events.
 *
 * <p><b>Where the state lives.</b> Everything a team achieves is a string in its campaign's
 * {@code completed} set (the same set the Atlas and the quest book read), so it follows the campaign
 * rules: a party inherits it from its founder, an archived party keeps it. Missions use plain ids
 * ({@code solsticio_mayor}...), observed by the {@code entrelumen:campaign} quest task; the steps
 * inside a mission use the {@link #PREFIX} namespace and are never quest milestones. The only world
 * state is the liberation and the elections ({@link SolsticioData}).
 */
public final class SolsticioStoryRules {
  private SolsticioStoryRules() {}

  // ---- missions ---------------------------------------------------------------------------

  /** 1 · Cross the light: the team's own crossing ({@link Expeditions#SOLSTICIO_ARRIVAL}). */
  public static final String ARRIVAL = Expeditions.SOLSTICIO_ARRIVAL;
  /** 2 · The city that never dawns: Aurelia explains the portal. */
  public static final String MAYOR = "solsticio_mayor";
  /** 3 · Seeds of the world: eight seed species for Juan. */
  public static final String SEEDS = "solsticio_seeds";
  /** 4 · A harvest for everyone: four Farmer's Delight meals, sixteen each; relic I. */
  public static final String HARVEST = "solsticio_harvest";
  /** 5 · Power from outside: Create: New Age parts and a charged battery for Terra. */
  public static final String POWER = "solsticio_power";
  /** 6 · Channelling the light: one Luminosity of each discipline; relic II. */
  public static final String TERRAPRISM = "solsticio_terraprism";
  /** 7 · Good works: relation 5 with Bodhi through the innkeepers' errands. */
  public static final String GOOD_WORKS = "solsticio_good_works";
  /** 8 · The blessing: the Heart of Heliodor becomes relic III. */
  public static final String BLESSING = "solsticio_blessing";
  /** 9 · The table of two lights: Aurelia and Bodhi agree. */
  public static final String ACCORD = "solsticio_accord";
  /** 10 · The portal: three relics and the broken key; the first team liberates the Entrelumen. */
  public static final String PORTAL = "solsticio_portal";
  /** 11 · Elections in Solsticio: Aurelia is re-elected, days after the liberation. */
  public static final String ELECTIONS = "solsticio_elections";

  public static final List<String> MISSIONS = List.of(ARRIVAL, MAYOR, SEEDS, HARVEST, POWER, TERRAPRISM,
      GOOD_WORKS, BLESSING, ACCORD, PORTAL, ELECTIONS);

  /**
   * What each mission needs first; the quest chapter's dependencies are the same graph (a JUnit test
   * compares them). Juan, Terra and Bodhi are three branches after Aurelia, so a team can split them.
   */
  public static final Map<String, List<String>> REQUIRES;

  static {
    Map<String, List<String>> requires = new LinkedHashMap<>();
    requires.put(ARRIVAL, List.of(CampaignMilestones.LAST_HORIZON));
    requires.put(MAYOR, List.of(ARRIVAL));
    requires.put(SEEDS, List.of(MAYOR));
    requires.put(HARVEST, List.of(SEEDS));
    requires.put(POWER, List.of(MAYOR));
    requires.put(TERRAPRISM, List.of(POWER));
    requires.put(GOOD_WORKS, List.of(MAYOR));
    requires.put(BLESSING, List.of(GOOD_WORKS));
    requires.put(ACCORD, List.of(HARVEST, TERRAPRISM, BLESSING));
    requires.put(PORTAL, List.of(ACCORD));
    requires.put(ELECTIONS, List.of(PORTAL));
    REQUIRES = Collections.unmodifiableMap(requires);
  }

  /** Steps inside missions: never quest milestones. */
  public static final String PREFIX = "solsticio.";
  static final String TAKEN = PREFIX + "taken.";
  static final String DONE = PREFIX + "errand.";
  static final String EGG = PREFIX + "egg.";
  static final String RELIC = PREFIX + "relic.";
  static final String HEARD = PREFIX + "heard.";
  /** The three easter eggs together revealed the corruption clue. */
  public static final String RUMOUR = PREFIX + "rumour";

  public static final int RELATION_NEEDED = 5;
  public static final int SEED_SPECIES = 8;
  public static final int MEAL_KINDS = 4;
  public static final int MEALS_EACH = 16;
  public static final int RELICS = 3;
  /** A charged battery holds at least this much (FE) and is at least 90% full. */
  public static final long BATTERY_CAPACITY = 100_000;
  public static final long DAY = 24_000L;
  /** The square votes three days of game time after the liberation. */
  public static final long ELECTION_DELAY = 3 * DAY;

  /** The team crossed with its own Light Key: every story step needs this. */
  public static boolean arrived(Campaigns.Campaign c) {
    return c != null && !c.archived && c.act >= Campaigns.FINAL_ACT
        && c.completed.contains(CampaignMilestones.LAST_HORIZON) && c.completed.contains(ARRIVAL);
  }

  public static boolean done(Campaigns.Campaign c, String id) {
    return c != null && c.completed.contains(id);
  }

  /** Every prerequisite recorded, the mission not yet, the campaign active and past the crossing. */
  public static boolean ready(Campaigns.Campaign c, String mission) {
    List<String> requires = REQUIRES.get(mission);
    if (requires == null || mission.equals(ARRIVAL) || !arrived(c) || done(c, mission)) return false;
    return c.completed.containsAll(requires);
  }

  /** Records a mission; false when it is not ready (or already recorded). */
  public static boolean record(Campaigns.Campaign c, String mission) {
    return ready(c, mission) && c.completed.add(mission);
  }

  /** The three relic missions, in relic order. */
  public static final List<String> RELIC_MISSIONS = List.of(HARVEST, TERRAPRISM, BLESSING);

  // ---- errands and relation ------------------------------------------------------------------

  /**
   * The eight errands of the four inns, two per innkeeper persona ({@code CommerceRules#persona}),
   * offered in this order. Each completed errand adds one to the team's relation with Bodhi.
   */
  public enum Errand {
    /** Dorotea: four honey bottles to Miga, the baker. */
    BAKERY_HONEY("bakery_honey", 0, Target.SHOP, "bakery"),
    /** Dorotea: Canela, lost on the roofs; walk close enough and she comes down. */
    LOST_CAT("lost_cat", 0, Target.CAT, ""),
    /** Tobías: his songbook back to Lucerna, the bookseller. */
    LIBRARY_BOOK("library_book", 1, Target.SHOP, "bookstore"),
    /** Tobías: four candles and a flame in Bodhi's chapel. */
    CHAPEL_CANDLES("chapel_candles", 1, Target.CHAPEL, "priest"),
    /** Amparo: Rumbo's map to Anselmo, the old neighbour. */
    CARTOGRAPHER_MAP("cartographer_map", 2, Target.ELDER, "maps"),
    /** Amparo: her pruning shears, fished out of Solsticio's water. */
    RIVER_TOOL("river_tool", 2, Target.INNKEEPER, ""),
    /** Ciro: a lantern and four waxed cut copper for the cellar lamp. */
    COPPER_LANTERN("copper_lantern", 3, Target.INNKEEPER, ""),
    /** Ciro: four different flowers carried into the secret garden. */
    GARDEN_FLOWERS("garden_flowers", 3, Target.GARDEN, "secret_garden");

    public final String id;
    public final int innkeeper;
    public final Target target;
    /** The shop type, character or easter egg the target needs; empty for the innkeeper and the cat. */
    public final String place;

    Errand(String id, int innkeeper, Target target, String place) {
      this.id = id;
      this.innkeeper = innkeeper;
      this.target = target;
      this.place = place;
    }

    public static Errand byId(String id) {
      for (Errand errand : values()) if (errand.id.equals(id)) return errand;
      return null;
    }
  }

  /** Where an errand ends. When its place is missing from the city, the innkeeper takes it instead. */
  public enum Target { SHOP, INNKEEPER, CAT, CHAPEL, ELDER, GARDEN }

  public enum ErrandState { OPEN, TAKEN, DONE }

  public static List<Errand> errands(int persona) {
    List<Errand> result = new ArrayList<>();
    for (Errand errand : Errand.values()) if (errand.innkeeper == Math.floorMod(persona, CommerceRules.INNKEEPERS))
      result.add(errand);
    return result;
  }

  public static ErrandState state(Campaigns.Campaign c, Errand errand) {
    if (done(c, DONE + errand.id)) return ErrandState.DONE;
    return done(c, TAKEN + errand.id) ? ErrandState.TAKEN : ErrandState.OPEN;
  }

  /** The innkeeper's next errand for the team: the first not done, or null when both are. */
  public static Errand current(Campaigns.Campaign c, int persona) {
    for (Errand errand : errands(persona)) if (state(c, errand) != ErrandState.DONE) return errand;
    return null;
  }

  public static boolean take(Campaigns.Campaign c, Errand errand) {
    return arrived(c) && state(c, errand) == ErrandState.OPEN && c.completed.add(TAKEN + errand.id);
  }

  public static boolean taken(Campaigns.Campaign c, Errand errand) {
    return arrived(c) && state(c, errand) == ErrandState.TAKEN;
  }

  /** Completes a taken errand: +1 relation; Good Works follows once the relation reaches five. */
  public static boolean complete(Campaigns.Campaign c, Errand errand) {
    if (!taken(c, errand)) return false;
    c.completed.remove(TAKEN + errand.id);
    c.completed.add(DONE + errand.id);
    recordGoodWorks(c);
    return true;
  }

  /** The team's relation with Bodhi: one per completed errand, 0..8. */
  public static int relation(Campaigns.Campaign c) {
    if (c == null) return 0;
    int relation = 0;
    for (Errand errand : Errand.values()) if (c.completed.contains(DONE + errand.id)) relation++;
    return relation;
  }

  /** Records mission 7 once the relation reaches five (errands may come before meeting Bodhi). */
  public static boolean recordGoodWorks(Campaigns.Campaign c) {
    return relation(c) >= RELATION_NEEDED && record(c, GOOD_WORKS);
  }

  // ---- easter eggs ---------------------------------------------------------------------------

  public static final List<String> EGGS = List.of("tavern", "secret_garden", "sundial");

  /** First discovery of an easter egg by the team; later visits return false. */
  public static boolean discover(Campaigns.Campaign c, String egg) {
    return arrived(c) && EGGS.contains(egg) && c.completed.add(EGG + egg);
  }

  public static boolean discovered(Campaigns.Campaign c, String egg) {
    return done(c, EGG + egg);
  }

  /** All three found: the clue of the rumour, once. */
  public static boolean revealRumour(Campaigns.Campaign c) {
    for (String egg : EGGS) if (!discovered(c, egg)) return false;
    return c.completed.add(RUMOUR);
  }

  // ---- the table and the portal --------------------------------------------------------------

  /** Aurelia ({@code mayor}) or Bodhi ({@code priest}) said their part; true when this completes the accord. */
  public static boolean hear(Campaigns.Campaign c, String who) {
    if (!ready(c, ACCORD)) return false;
    c.completed.add(HEARD + who);
    if (c.completed.contains(HEARD + "mayor") && c.completed.contains(HEARD + "priest")) return record(c, ACCORD);
    return false;
  }

  public static boolean heard(Campaigns.Campaign c, String who) {
    return done(c, HEARD + who);
  }

  /** The team sets one of its relics (1..3) in the portal: only after the accord, once each. */
  public static boolean presentRelic(Campaigns.Campaign c, int relic) {
    if (relic < 1 || relic > RELICS || !arrived(c) || !done(c, ACCORD) || done(c, PORTAL)) return false;
    return c.completed.add(RELIC + relic);
  }

  public static boolean presented(Campaigns.Campaign c, int relic) {
    return done(c, RELIC + relic);
  }

  public static int relicsPresented(Campaigns.Campaign c) {
    int count = 0;
    for (int relic = 1; relic <= RELICS; relic++) if (presented(c, relic)) count++;
    return count;
  }

  /** Mission 10: the team's three relics are set and it showed its own key. */
  public static boolean openPortal(Campaigns.Campaign c) {
    return relicsPresented(c) == RELICS && record(c, PORTAL);
  }

  // ---- elections -----------------------------------------------------------------------------

  public static boolean electionDue(boolean liberated, long liberatedAt, long now, boolean held) {
    return liberated && !held && liberatedAt > 0 && now - liberatedAt >= ELECTION_DELAY;
  }

  /** Whole days left until the vote (at least one while it has not happened). */
  public static int daysUntilElection(long liberatedAt, long now) {
    long left = liberatedAt + ELECTION_DELAY - now;
    return (int) Math.max(1, (left + DAY - 1) / DAY);
  }

  // ---- deliveries ----------------------------------------------------------------------------

  /** The first {@code count} distinct ids, in inventory order; empty when there are fewer. */
  public static List<String> distinct(List<String> ids, int count) {
    Set<String> kinds = new LinkedHashSet<>(ids);
    if (kinds.size() < count) return List.of();
    return List.copyOf(new ArrayList<>(kinds).subList(0, count));
  }

  /**
   * The first {@code kinds} ids that reach {@code each} items, from per-id totals in inventory
   * order; empty when fewer ids reach it.
   */
  public static List<String> enough(Map<String, Integer> totals, int kinds, int each) {
    List<String> result = new ArrayList<>();
    for (var entry : totals.entrySet()) {
      if (entry.getValue() >= each) result.add(entry.getKey());
      if (result.size() == kinds) return List.copyOf(result);
    }
    return List.of();
  }

  /** What is still missing from {@code required}, given what the player carries. */
  public static Map<String, Integer> missing(Map<String, Integer> required, Map<String, Integer> carried) {
    Map<String, Integer> missing = new TreeMap<>();
    required.forEach((item, count) -> {
      int lack = count - carried.getOrDefault(item, 0);
      if (lack > 0) missing.put(item, lack);
    });
    return missing;
  }

  public static boolean charged(long stored, long capacity) {
    return capacity >= BATTERY_CAPACITY && stored * 10 >= capacity * 9;
  }

  // ---- dialogue ------------------------------------------------------------------------------

  /** World state that changes what people say. */
  public record World(boolean liberated, boolean electionsHeld) {}

  /** The named characters, by their city marker. */
  public static final List<String> CHARACTERS = List.of("mayor", "inventor", "gardener", "priest");

  /**
   * What a named character says next to this team: a stage id, the suffix of
   * {@code entrelumen.solsticio.character.<character>.<stage>}. Stages that change the campaign are
   * applied by {@link SolsticioStory}; this only chooses.
   */
  public static String stage(String character, Campaigns.Campaign c, World world, HeartState heart) {
    if (!arrived(c)) return "visitor";
    if (!character.equals("mayor") && !done(c, MAYOR)) return "protocol";
    return switch (character) {
      case "mayor" -> mayor(c, world);
      case "inventor" -> !done(c, POWER) ? "power" : !done(c, TERRAPRISM) ? "light"
          : world.electionsHeld() ? "after_elections" : "after";
      case "gardener" -> !done(c, SEEDS) ? "seeds" : !done(c, HARVEST) ? "harvest"
          : world.electionsHeld() ? "after_elections" : "after";
      case "priest" -> priest(c, world, heart);
      default -> "visitor";
    };
  }

  private static String mayor(Campaigns.Campaign c, World world) {
    if (!done(c, MAYOR)) return "welcome";
    if (!done(c, HARVEST)) return "wait_gardener";
    if (!done(c, TERRAPRISM)) return "wait_inventor";
    if (!done(c, BLESSING)) return "wait_priest";
    if (!done(c, ACCORD)) return heard(c, "priest") ? "accord" : heard(c, "mayor") ? "table_waiting" : "table";
    if (!done(c, PORTAL)) return "portal";
    if (!world.electionsHeld()) return "campaign";
    if (!done(c, ELECTIONS)) return "elected";
    return "after";
  }

  /** Where the team's Heart of Heliodor is, as Bodhi sees it. */
  public enum HeartState {
    /** No Sun Spirit fell to the team yet. */
    WITH_SPIRIT,
    /** Delivered to the Atlas, not handed back yet: the Atlas releases it now. */
    IN_ATLAS,
    /** The player talking carries a Heart. */
    CARRIED,
    /** Recovered or released, but not in this player's hands. */
    ELSEWHERE
  }

  private static String priest(Campaigns.Campaign c, World world, HeartState heart) {
    if (!done(c, GOOD_WORKS)) return relation(c) >= RELATION_NEEDED ? "listening" : "works";
    if (!done(c, BLESSING)) return switch (heart) {
      case WITH_SPIRIT -> "spirit";
      case IN_ATLAS -> "release";
      case CARRIED -> "blessing";
      case ELSEWHERE -> "heart";
    };
    if (!done(c, ACCORD)) {
      if (!ready(c, ACCORD)) return "wait";
      return heard(c, "mayor") ? "accord" : heard(c, "priest") ? "table_waiting" : "table";
    }
    return world.electionsHeld() ? "after_elections" : "after";
  }

  /** Common villagers' line pool: the ambient lines, then liberation's, then the elections'. */
  public static String townsfolkPool(World world) {
    return world.electionsHeld() ? "elected." : world.liberated() ? "liberated." : "";
  }
}
