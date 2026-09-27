package dev.entrelumen;

import java.util.*;
import java.util.function.Predicate;

/**
 * The Signal Tower's light relay (docs/design/heliodor-ruins.md, "La Torre de la Señal: el relevo de
 * luz"), as a pure model. On each floor a lit brazier sends light flat along the four grid axes at
 * its own height. A vitral the light crosses adds its colour (a set union) and then lets it pass,
 * sends it up, or turns it towards a compass direction; a mirror turns it 90°; a collector gathers
 * every beam that reaches it and sends their union up. Light going up stops at the first closed cell;
 * the floor's receptor lights when the colours reaching it are exactly its target, so an extra colour
 * (the amber decoy) fails.
 */
public final class LightRelay {
  private LightRelay() {}

  /** North, east, south, west, as in {@link RuinMarkers#DIRECTIONS} n, e, s, w. */
  public enum Dir {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    final int dx, dz;

    Dir(int dx, int dz) {
      this.dx = dx;
      this.dz = dz;
    }

    Dir reverse() {
      return values()[(ordinal() + 2) % 4];
    }

    boolean across(Dir other) {
      return (ordinal() + other.ordinal()) % 2 == 1;
    }

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Dir> of(String id) {
      for (Dir dir : values())
        if (dir.id().equals(id) || dir.id().substring(0, 1).equals(id)) return Optional.of(dir);
      return Optional.empty();
    }
  }

  /** What a vitral does with the light after tinting it; the turns name where the light leaves. */
  public enum Turn {
    PASS, UP, NORTH, EAST, SOUTH, WEST;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public Turn next() {
      return values()[(ordinal() + 1) % values().length];
    }

    public static Optional<Turn> of(String id) {
      for (Turn turn : values()) if (turn.id().equals(id)) return Optional.of(turn);
      return Optional.empty();
    }

    Optional<Dir> dir() {
      return switch (this) {
        case NORTH -> Optional.of(Dir.NORTH);
        case EAST -> Optional.of(Dir.EAST);
        case SOUTH -> Optional.of(Dir.SOUTH);
        case WEST -> Optional.of(Dir.WEST);
        default -> Optional.empty();
      };
    }
  }

  public sealed interface Element permits Vitral, Mirror, Collector {}

  public record Vitral(String colour, Turn turn) implements Element {}

  public record Mirror(Dir facing) implements Element {}

  public record Collector() implements Element {}

  /** A cell of the relay. */
  public record Cell(int x, int y, int z) {
    Cell step(Dir dir) {
      return new Cell(x + dir.dx, y, z + dir.dz);
    }

    Cell up() {
      return new Cell(x, y + 1, z);
    }
  }

  /** One floor: where its light starts, its elements, and the receptor with its exact colours. */
  public record Floor(Cell source, Map<Cell, Element> elements, Cell receptor, Set<String> target) {
    public Floor {
      elements = Map.copyOf(elements);
      target = Set.copyOf(target);
    }
  }

  /**
   * What a lit floor does: the colours that reach its receptor (empty when no light does) and the
   * cells the light crossed, horizontal and vertical, with the colours it carried there.
   */
  public record Trace(Set<String> received, Map<Cell, Set<String>> lit) {
    public boolean reaches() {
      return !received.isEmpty();
    }
  }

  public static final int MAX_STEPS = 32;

  public static boolean satisfied(Floor floor, Trace trace) {
    return trace.received().equals(floor.target());
  }

  /** Traces a lit floor; {@code open} says which cells without an element let light through. */
  public static Trace trace(Floor floor, Predicate<Cell> open) {
    Set<String> received = new TreeSet<>();
    Set<String> collected = new TreeSet<>();
    Cell[] collector = {null};
    Map<Cell, Set<String>> lit = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    Deque<Object[]> rays = new ArrayDeque<>();
    for (Dir dir : Dir.values()) rays.add(new Object[] {floor.source(), dir, Set.<String>of()});
    while (!rays.isEmpty()) {
      Object[] ray = rays.poll();
      Cell at = (Cell) ray[0];
      Dir dir = (Dir) ray[1];
      @SuppressWarnings("unchecked") Set<String> colours = new TreeSet<>((Set<String>) ray[2]);
      for (int step = 0; step < MAX_STEPS; step++) {
        at = at.step(dir);
        Element element = floor.elements().get(at);
        if (element == null) {
          if (!open.test(at)) break;
          mark(lit, at, colours);
          continue;
        }
        if (!seen.add(at + ":" + dir + ":" + colours)) break;
        if (element instanceof Vitral vitral) {
          colours.add(vitral.colour());
          mark(lit, at, colours);
          if (vitral.turn() == Turn.PASS) continue;
          if (vitral.turn() == Turn.UP) {
            up(floor, at, colours, open, received, lit);
            break;
          }
          Dir out = vitral.turn().dir().orElseThrow();
          if (out == dir.reverse()) break;
          dir = out;
        } else if (element instanceof Mirror mirror) {
          mark(lit, at, colours);
          if (!mirror.facing().across(dir)) break;
          dir = mirror.facing();
        } else {
          mark(lit, at, colours);
          collected.addAll(colours);
          collector[0] = at;
          break;
        }
      }
    }
    if (collector[0] != null && !collected.isEmpty()) up(floor, collector[0], collected, open, received, lit);
    return new Trace(Collections.unmodifiableSet(received), Collections.unmodifiableMap(lit));
  }

  private static void up(Floor floor, Cell from, Set<String> colours, Predicate<Cell> open, Set<String> received,
      Map<Cell, Set<String>> lit) {
    Cell at = from;
    for (int step = 0; step < MAX_STEPS * 2; step++) {
      at = at.up();
      if (at.equals(floor.receptor())) {
        received.addAll(colours);
        return;
      }
      if (!open.test(at)) return;
      mark(lit, at, colours);
    }
  }

  private static void mark(Map<Cell, Set<String>> lit, Cell at, Set<String> colours) {
    lit.computeIfAbsent(at, ignored -> new TreeSet<>()).addAll(colours);
  }

  /** Parses a relay target or colour list, {@code red+green+blue}. */
  public static Set<String> colours(String text) {
    Set<String> colours = new TreeSet<>();
    if (text == null || text.isBlank()) return colours;
    for (String colour : text.split("\\+")) if (!colour.isBlank()) colours.add(colour.trim());
    return colours;
  }
}
