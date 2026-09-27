package dev.entrelumen;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The Envés marker contract: DATA structure blocks inside the room templates
 * ({@code data/entrelumen/structure/enves/<tileset>/}), written by {@code tools/export_enves_tiles.py}
 * from {@code art/dungeon/tiles.py}. Metadata reads {@code enves:<kind>} or
 * {@code enves:<kind>:<argument>}; positions are template-local. The engine turns every marker into
 * air when it places a cell and then acts on it (see {@link Kind}); docs/design/dungeon-enves.md
 * keeps the table. Pure.
 */
public final class EnvesMarkers {
  public static final String PREFIX = "enves:";
  static final Pattern ARGUMENT = Pattern.compile("[a-z0-9_][a-z0-9_./-]{0,63}");

  /** What a marker marks; {@link #argument} tells whether it takes one. */
  public enum Kind {
    /** start: where a player arrives on the floor and respawns after a fall (feet). */
    ARRIVAL("arrival", Argument.NONE),
    /** exit and vestibule: the top landing of the stairwell (feet). */
    STAIR_TOP("stair_top", Argument.NONE),
    /** start: the bottom landing of the stairwell (feet). */
    STAIR_BOTTOM("stair_bottom", Argument.NONE),
    /** exit: one block of the stairwell's floor opening; sealed until the floor's seals are lit. */
    STAIR_SEAL("stair_seal", Argument.NONE),
    /** fight and guard: a spawn point (feet); {@code champion} marks the guard's champion. */
    ENCOUNTER("encounter", Argument.OPTIONAL),
    /** A loot chest: {@code <kind>[/<facing>]}, kinds {@code room}, {@code vault}, {@code boss}. */
    CHEST("chest", Argument.REQUIRED),
    /** shrine: the altar anchor. */
    SHRINE("shrine", Argument.NONE),
    /** vault: one block of the 3×4 doorway the puzzle keeps shut; the argument is the side (n e s w). */
    VAULT_GATE("vault_gate", Argument.REQUIRED),
    /** vault: the puzzle's anchor, two blocks behind the gate on the door's axis (feet). */
    VAULT_MECHANISM("vault_mechanism", Argument.NONE),
    /** seal: the seal block the group lights. */
    SEAL("seal", Argument.NONE),
    /** arena_center: the middle of the boss arena (feet). */
    BOSS_CENTER("boss_center", Argument.NONE),
    /** A way out of the Envés: {@code return} in the vestibule, {@code victory} on floor V. */
    EXIT_PORTAL("exit_portal", Argument.REQUIRED);

    public final String id;
    final Argument argument;

    Kind(String id, Argument argument) {
      this.id = id;
      this.argument = argument;
    }
  }

  enum Argument {NONE, OPTIONAL, REQUIRED}

  /** A parsed marker; {@code argument} is empty when there is none. */
  public record Marker(Kind kind, String argument) {
    /** The first part of a {@code a/b} argument ({@code vault} of {@code vault/north}). */
    public String head() {
      int slash = argument.indexOf('/');
      return slash < 0 ? argument : argument.substring(0, slash);
    }

    /** The part after the slash, or empty. */
    public String tail() {
      int slash = argument.indexOf('/');
      return slash < 0 ? "" : argument.substring(slash + 1);
    }

    public String metadata() {
      return PREFIX + kind.id + (argument.isEmpty() ? "" : ":" + argument);
    }
  }

  private EnvesMarkers() {}

  /** Parses a DATA block's metadata; empty for anything outside the contract. */
  public static Optional<Marker> parse(String metadata) {
    if (metadata == null) return Optional.empty();
    String key = metadata.trim().toLowerCase(Locale.ROOT);
    if (!key.startsWith(PREFIX)) return Optional.empty();
    key = key.substring(PREFIX.length());
    int colon = key.indexOf(':');
    String name = colon < 0 ? key : key.substring(0, colon);
    String argument = colon < 0 ? "" : key.substring(colon + 1);
    for (Kind kind : Kind.values()) {
      if (!kind.id.equals(name)) continue;
      boolean has = colon >= 0;
      if (kind.argument == Argument.NONE && has) return Optional.empty();
      if (kind.argument == Argument.REQUIRED && !has) return Optional.empty();
      if (has && !ARGUMENT.matcher(argument).matches()) return Optional.empty();
      return Optional.of(new Marker(kind, argument));
    }
    return Optional.empty();
  }
}
