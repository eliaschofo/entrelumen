package dev.entrelumen;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The pure decision model behind {@link StructureProtection}: protected regions with holes, act
 * gates and the verdict for one action at one position. Registry-free and unit-tested; the runtime
 * only translates events into these calls.
 *
 * <p>A region protects every block of its box except those inside an <em>active</em> hole. A hole
 * is active when it is open (not claimable) or claimed; an unclaimed plot stays protected until a
 * team claims it. Several regions may overlap: an action must pass every region covering it.
 */
public final class ProtectionRules {
  private ProtectionRules() {}

  /** Inclusive integer box. */
  public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box {
      if (minX > maxX || minY > maxY || minZ > maxZ)
        throw new IllegalArgumentException("Inverted box " + minX + "," + minY + "," + minZ
            + " .. " + maxX + "," + maxY + "," + maxZ);
    }

    public boolean contains(int x, int y, int z) {
      return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Box other) {
      return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
          && minZ <= other.maxZ && maxZ >= other.minZ;
    }
  }

  /**
   * What unlocks a region's containers, lecterns, altars and displays for a team: its campaign act
   * must be at least {@code act} and, when {@code milestone} is not empty, the campaign must have
   * recorded it. {@link #NONE} is always open.
   */
  public record ActGate(int act, String milestone) {
    public static final ActGate NONE = new ActGate(1, "");

    public ActGate {
      milestone = milestone == null ? "" : milestone;
    }

    public static ActGate act(int act) {
      return new ActGate(act, "");
    }

    public boolean satisfiedBy(Actor actor) {
      if (this.equals(NONE)) return true;
      return actor.campaign() != null && actor.act() >= act
          && (milestone.isEmpty() || actor.milestones().contains(milestone));
    }
  }

  /**
   * A gap in a region. Open holes ({@code claimable == false}) exempt everyone; claimable holes
   * are plots that exempt only their owning campaign once claimed and stay protected before that.
   */
  public record Hole(String id, Box box, UUID owner, boolean claimable) {
    public Hole {
      Objects.requireNonNull(id);
      Objects.requireNonNull(box);
    }

    public boolean active() {
      return !claimable || owner != null;
    }
  }

  /** One protected structure: a stable id, the dimension id, its box, gate and holes. */
  public record Region(String id, String dimension, Box box, ActGate gate, List<Hole> holes) {
    public Region {
      Objects.requireNonNull(id);
      Objects.requireNonNull(dimension);
      Objects.requireNonNull(box);
      gate = gate == null ? ActGate.NONE : gate;
      holes = List.copyOf(holes);
    }

    /** The active hole containing the position, or null. */
    public Hole activeHoleAt(int x, int y, int z) {
      for (Hole hole : holes) if (hole.active() && hole.box().contains(x, y, z)) return hole;
      return null;
    }

    /** Whether the region itself guards the position (inside the box and outside active holes). */
    public boolean guards(int x, int y, int z) {
      return box.contains(x, y, z) && activeHoleAt(x, y, z) == null;
    }
  }

  /**
   * Who acts. {@code campaign} is the team's campaign id (party id, or the player for a solo
   * campaign) and may be null for players without a team. {@code automaton} marks fake players
   * (deployers and similar): they may work inside active holes but never in protected blocks.
   */
  public record Actor(UUID campaign, int act, Set<String> milestones, boolean bypass,
      boolean automaton) {
    /** Explosions, fire, fluids, pistons and mobs. */
    public static final Actor ENVIRONMENT = new Actor(null, 0, Set.of(), false, true);

    public Actor {
      milestones = Set.copyOf(milestones);
    }
  }

  public enum Action {
    BREAK, PLACE,
    /** An item used on a protected block (tools, buckets, flint, bone meal, spawn eggs...). */
    USE_ITEM,
    /** Doors, buttons, levers, bells and stateless workstations: always usable. */
    USE_FREE_BLOCK,
    /** Containers, lecterns, altars and similar: usable once the region's gate opens. */
    USE_GATED_BLOCK,
    /** Anything else that would change the block (cakes, note blocks, repeaters...). */
    USE_LOCKED_BLOCK,
    /** Breaking a frame, painting or armor stand. */
    ENTITY_BREAK,
    /** Rotating or filling a frame, dressing an armor stand. */
    ENTITY_USE,
    /** Taking the item displayed in a frame without breaking it. */
    TAKE_DISPLAYED,
    EXPLOSION, FIRE, FLUID, PISTON, GRIEF;

    public boolean environmental() {
      return this == EXPLOSION || this == FIRE || this == FLUID || this == PISTON || this == GRIEF;
    }

    boolean gated() {
      return this == USE_GATED_BLOCK || this == ENTITY_USE || this == TAKE_DISPLAYED;
    }
  }

  public enum Verdict {
    ALLOW, DENY_PROTECTED, DENY_LOCKED, DENY_NOT_OWNER;

    public boolean allowed() {
      return this == ALLOW;
    }
  }

  /** The verdict of every region covering the position; the first refusal wins. */
  public static Verdict decide(List<Region> covering, int x, int y, int z, Action action, Actor actor) {
    if (actor.bypass()) return Verdict.ALLOW;
    for (Region region : covering) {
      Verdict verdict = decide(region, x, y, z, action, actor);
      if (!verdict.allowed()) return verdict;
    }
    return Verdict.ALLOW;
  }

  static Verdict decide(Region region, int x, int y, int z, Action action, Actor actor) {
    if (!region.box().contains(x, y, z)) return Verdict.ALLOW;
    Hole hole = region.activeHoleAt(x, y, z);
    if (hole != null) {
      if (action.environmental() || !hole.claimable() || actor.automaton()) return Verdict.ALLOW;
      return hole.owner().equals(actor.campaign()) ? Verdict.ALLOW : Verdict.DENY_NOT_OWNER;
    }
    if (action == Action.USE_FREE_BLOCK) return Verdict.ALLOW;
    if (action.gated() && !actor.automaton())
      return region.gate().satisfiedBy(actor) ? Verdict.ALLOW : Verdict.DENY_LOCKED;
    return Verdict.DENY_PROTECTED;
  }

  /**
   * Fluids may flow into a guarded block only from a block the same regions guard: the
   * structure's own water keeps working, nothing pours in from a plot or from outside.
   */
  public static boolean fluidMayFlow(List<Region> coveringTarget, int fromX, int fromY, int fromZ,
      int toX, int toY, int toZ) {
    for (Region region : coveringTarget)
      if (region.guards(toX, toY, toZ) && !region.guards(fromX, fromY, fromZ)) return false;
    return true;
  }
}
