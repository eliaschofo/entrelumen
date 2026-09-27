package dev.entrelumen;

import dev.entrelumen.EnvesLayout.Role;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where the Envés's systems plug in: encounters, loot chests, shrines, vault puzzles, seals and the
 * boss (the second worker's part, docs/design/dungeon-enves.md). The engine places floors, strips
 * the template markers and calls these hooks with world positions; each has a plain default so the
 * dungeon works end to end without them. Register replacements once, during mod setup.
 */
public final class EnvesHooks {
  private EnvesHooks() {}

  /** A template marker placed in the world, with the cell and role it belongs to. */
  public record WorldMarker(EnvesMarkers.Marker marker, BlockPos pos, int cell, Role role) {}

  /** One floor of an attempt as the hooks see it. Depth 1..5; tileset as in the config. */
  public record Floor(ServerLevel level, EnvesData.Attempt attempt, EnvesLayout.Floor layout, int depth, String tileset) {
    /** The lowest corner of a cell's template. */
    public BlockPos origin(int cell) {
      int[] o = EnvesGeometry.cellOrigin(attempt.slot, depth, cell);
      return new BlockPos(o[0], o[1], o[2]);
    }

    /** The markers of one cell, in world coordinates. */
    public List<WorldMarker> markers(int cell) {
      return Enves.markers(this, cell);
    }

    public ApotheosisTiers.Tier tier() {
      return attempt.tier;
    }
  }

  public enum EndReason {
    /** The fall pool ran out: everyone was sent back, the gate asks for a new offering. */
    FALLS_SPENT,
    /** Nobody of the group came back within the abandonment time. */
    ABANDONED,
    /** The team gave it up at the gate or with a command. */
    GIVEN_UP,
    /** An operator ended it. */
    ADMIN
  }

  /** Fight and guard rooms: called once per room, the first time a member steps in. */
  public interface Encounters {
    void roomEntered(Floor floor, int cell, Role role, List<WorldMarker> spawns, ServerPlayer first);
  }

  /** {@code enves:chest:<kind>[/<facing>]}: default places a Lootr chest (a vanilla one without Lootr). */
  public interface Chests {
    void place(Floor floor, WorldMarker chest);
  }

  /** {@code enves:shrine}: default places a beacon as the altar placeholder. */
  public interface Shrines {
    void place(Floor floor, WorldMarker altar);
  }

  /** Vault rooms: the gate blocks (default: the configured gate block) and the puzzle anchor. */
  public interface Vaults {
    void place(Floor floor, int cell, List<WorldMarker> gate, WorldMarker mechanism);
  }

  /** Seal rooms: whether a member may light the seal now, and what follows once lit. */
  public interface Seals {
    boolean mayLight(Floor floor, int cell, ServerPlayer player);

    default void lit(Floor floor, int cell, ServerPlayer player, int lit, int total) {}
  }

  /**
   * Floor V: called when the boss floor is ready. The default has no boss and wakes the victory
   * portal at once; a boss calls {@link Enves#bossDefeated} when it falls.
   */
  public interface Boss {
    void floorReady(Floor floor, WorldMarker center);
  }

  /** Attempt and floor events, for scaling, rewards and achievements. */
  public interface Lifecycle {
    default void attemptOpened(EnvesData.Attempt attempt) {}

    default void floorReady(Floor floor) {}

    default void playerFell(EnvesData.Attempt attempt, ServerPlayer player, int left) {}

    default void attemptEnded(EnvesData.Attempt attempt, EndReason reason) {}
  }

  static final Encounters NO_ENCOUNTERS = (floor, cell, role, spawns, first) -> {};
  static final Seals ALWAYS_LIGHT = (floor, cell, player) -> true;
  static final Boss NO_BOSS = (floor, center) -> Enves.bossDefeated(floor.attempt());
  static final Lifecycle NOTHING = new Lifecycle() {};

  private static volatile Encounters encounters = NO_ENCOUNTERS;
  private static volatile Chests chests = EnvesPlacer::defaultChest;
  private static volatile Shrines shrines = EnvesPlacer::defaultShrine;
  private static volatile Vaults vaults = EnvesPlacer::defaultVault;
  private static volatile Seals seals = ALWAYS_LIGHT;
  private static volatile Boss boss = NO_BOSS;
  private static volatile Lifecycle lifecycle = NOTHING;

  public static Encounters encounters() {
    return encounters;
  }

  public static Chests chests() {
    return chests;
  }

  public static Shrines shrines() {
    return shrines;
  }

  public static Vaults vaults() {
    return vaults;
  }

  public static Seals seals() {
    return seals;
  }

  public static Boss boss() {
    return boss;
  }

  public static Lifecycle lifecycle() {
    return lifecycle;
  }

  public static void setEncounters(Encounters hook) {
    encounters = Objects.requireNonNull(hook);
  }

  public static void setChests(Chests hook) {
    chests = Objects.requireNonNull(hook);
  }

  public static void setShrines(Shrines hook) {
    shrines = Objects.requireNonNull(hook);
  }

  public static void setVaults(Vaults hook) {
    vaults = Objects.requireNonNull(hook);
  }

  public static void setSeals(Seals hook) {
    seals = Objects.requireNonNull(hook);
  }

  public static void setBoss(Boss hook) {
    boss = Objects.requireNonNull(hook);
  }

  public static void setLifecycle(Lifecycle hook) {
    lifecycle = Objects.requireNonNull(hook);
  }
}
