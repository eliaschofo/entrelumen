package dev.entrelumen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure rules of the Ark, second version ({@code docs/design/ark-modules-v2.md}): which module each
 * act gives, what the team's Ark turns on, the activation checklist, the charging rate and the rules
 * of {@code /home} and {@code /rtp}. Nothing here touches a world.
 */
public final class ArkRules {
  /** The six modules, in the order of the acts that give them, with their global effect. */
  public enum Module {
    HABITATION("habitation_module", 1, "home"),
    EXPLORATION("exploration_module", 2, "traveller"),
    NATURE("nature_module", 3, "vitality"),
    ARCANE("arcane_module", 4, "overflowing_mana"),
    LOGISTICS("logistics_module", 5, "remote_trade"),
    ENGINEERING("engineering_module", 5, "wireless_charging");

    public final String id;
    public final int act;
    public final String effect;

    Module(String id, int act, String effect) {
      this.id = id;
      this.act = act;
      this.effect = effect;
    }

    /** The Atlas project that delivers the module has the module's own ID. */
    public String project() {
      return id;
    }

    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Module byId(String id) {
      for (Module module : values()) if (module.id.equals(id)) return module;
      return null;
    }
  }

  /** The controller's slot and the six module slots, as {@code ark_multiblock.json} names them. */
  public static final List<String> SLOTS;

  static {
    List<String> slots = new ArrayList<>();
    slots.add(ArkMultiblock.CONTROLLER);
    for (Module module : Module.values()) slots.add(module.id);
    SLOTS = List.copyOf(slots);
  }

  /** The last project of act V: the activation needs it. */
  public static final String LAST_PROJECT = "world_network";
  /** The End journey stays a requirement of the activation, shown on the checklist. */
  public static final String END_JOURNEY = "end_arrival";

  /** Each beacon on a column adds one level; four is the most. */
  public static final int MAX_BEACONS = 4;

  /**
   * What the team's Ark holds, as saved: the core, the controller, the modules in their slots and
   * how many of the columns carry a beacon.
   */
  public record Status(boolean registered, boolean core, boolean controller, Set<String> modules, int beacons) {
    public static final Status NONE = new Status(false, false, false, Set.of(), 0);

    public Status {
      modules = Collections.unmodifiableSet(new TreeSet<>(modules));
      beacons = Math.clamp(beacons, 0, MAX_BEACONS);
    }

    /** The Ark stands: its required core is complete and its controller is in place (Elias: required). */
    public boolean standing() {
      return registered && core && controller;
    }

    /** A module works while it sits in its slot of the team's standing Ark. */
    public boolean active(Module module) {
      return standing() && modules.contains(module.id);
    }

    /**
     * The level of the modules' potion effects and buffs (Vitality's Regeneration, Overflowing Mana,
     * Hero of the Village): one, plus one per beacon on the columns.
     */
    public int level() {
      return standing() ? 1 + beacons : 0;
    }

    public Set<Module> activeModules() {
      Set<Module> active = new java.util.LinkedHashSet<>();
      for (Module module : Module.values()) if (active(module)) active.add(module);
      return Collections.unmodifiableSet(active);
    }

    /** The whole Ark: the core, the controller and the six modules. */
    public boolean assembled() {
      return registered && core && controller && modules.containsAll(moduleIds());
    }
  }

  /** One line of the activation checklist: what it is, whether it is done and where it comes from. */
  public enum Check { CORE, CONTROLLER, MODULE, PROJECT, JOURNEY }

  public record Item(Check check, String id, boolean done) {}

  private ArkRules() {}

  public static List<String> moduleIds() {
    return java.util.Arrays.stream(Module.values()).map(module -> module.id).toList();
  }

  /**
   * The activation, in reading order: the Ark's core, the controller, the six modules in their slots,
   * the last project of act V and the journey to the End. Nothing else is required.
   */
  public static List<Item> checklist(Status ark, Set<String> completed) {
    List<Item> items = new ArrayList<>();
    items.add(new Item(Check.CORE, "core", ark.registered() && ark.core()));
    items.add(new Item(Check.CONTROLLER, ArkMultiblock.CONTROLLER, ark.registered() && ark.controller()));
    for (Module module : Module.values())
      items.add(new Item(Check.MODULE, module.id, ark.registered() && ark.modules().contains(module.id)));
    items.add(new Item(Check.PROJECT, LAST_PROJECT, completed.contains(LAST_PROJECT)));
    items.add(new Item(Check.JOURNEY, END_JOURNEY, completed.contains(END_JOURNEY)));
    return List.copyOf(items);
  }

  public static boolean checklistDone(Status ark, Set<String> completed) {
    return checklist(ark, completed).stream().allMatch(Item::done);
  }

  // ---- Engineering: wireless charging --------------------------------------------------------

  /** Charging settings: a share of the capacity per second, with a floor and a ceiling (FE/s). */
  public record Charging(double percentPerSecond, int minPerSecond, int maxPerSecond) {
    public static final Charging DEFAULT = new Charging(0.5, 100, 10_000);
  }

  /**
   * FE offered to one item for {@code seconds} of charging: {@code percent} of its capacity per
   * second, never less than the floor nor more than the ceiling, and never more than it lacks.
   */
  public static int chargeOffer(long capacity, long stored, Charging charging, double seconds) {
    if (capacity <= 0 || stored >= capacity || seconds <= 0) return 0;
    double perSecond = capacity * charging.percentPerSecond() / 100.0;
    perSecond = Math.max(charging.minPerSecond(), Math.min(charging.maxPerSecond(), perSecond));
    long offer = (long) Math.floor(perSecond * seconds);
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.min(offer, capacity - stored)));
  }

  // ---- Habitation: one home per player -------------------------------------------------------

  /** One home per player: 15 minutes between trips, after 5 seconds standing still and unhurt (Elias). */
  public static final int HOME_COOLDOWN_TICKS = 15 * 60 * 20, HOME_WARMUP_TICKS = 5 * 20;
  /** How far a player may drift during the warm-up (blocks) before it counts as moving. */
  public static final double HOME_STILL = 0.25;
  /** One {@code /rtp} per hour (Elias). */
  public static final int RTP_COOLDOWN_TICKS = 60 * 60 * 20;
  public static final int RTP_MIN = 1_000, RTP_MAX = 5_000;
  static final String SOLSTICIO = Expeditions.SOLSTICIO;

  /** Ticks left before a command with {@code cooldown} can run again; 0 when ready. */
  public static long cooldownLeft(long now, long lastUse, int cooldown) {
    if (lastUse <= 0 || now < lastUse) return 0;
    return Math.max(0, lastUse + cooldown - now);
  }

  /** Whether a player moved during the warm-up: farther than {@link #HOME_STILL} from where they began. */
  public static boolean moved(double dx, double dy, double dz) {
    return dx * dx + dy * dy + dz * dz > HOME_STILL * HOME_STILL;
  }

  /** A home cannot be set or used in Solsticio, where the Light Key is the way in and out. */
  public static boolean homeAllowed(String dimension) {
    return !SOLSTICIO.equals(dimension);
  }

  // ---- Exploration: random travel ------------------------------------------------------------

  /** {@code /rtp} works in the Overworld and the exploration dimensions (the Atlas's journeys). */
  public static boolean rtpAllowed(String dimension) {
    return "minecraft:overworld".equals(dimension) || Expeditions.journeyDimensions().contains(dimension);
  }

  /**
   * A random point between {@link #RTP_MIN} and {@link #RTP_MAX} blocks from the spawn, uniform over
   * the ring's area. {@code u} and {@code v} are uniform samples in [0, 1).
   */
  public static long[] rtpCandidate(long spawnX, long spawnZ, double u, double v) {
    double angle = u * Math.PI * 2;
    double min2 = (double) RTP_MIN * RTP_MIN, max2 = (double) RTP_MAX * RTP_MAX;
    double radius = Math.sqrt(min2 + v * (max2 - min2));
    return new long[] {spawnX + Math.round(Math.cos(angle) * radius), spawnZ + Math.round(Math.sin(angle) * radius)};
  }

  // ---- Logistics: the Solsticio shops a team knows -------------------------------------------

  /** How close a player must stand to a shopkeeper's post to count as a visit. */
  public static final int VISIT_RADIUS = 8, VISIT_HEIGHT = 4;

  /** A shop site's stable ID: its type and its post. */
  public static String shopId(String key, long pos) {
    return key + "@" + pos;
  }

  public static boolean visits(int dx, int dy, int dz) {
    return dx * dx + dz * dz <= VISIT_RADIUS * VISIT_RADIUS && Math.abs(dy) <= VISIT_HEIGHT;
  }
}
