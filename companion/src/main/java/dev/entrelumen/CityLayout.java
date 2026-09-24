package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure layout of Solsticio: how template pieces tile around the origin, how each piece splits into
 * the world-aligned cubes placed within a per-tick time budget, the marker vocabulary and plot
 * allocation. Registry-free and
 * unit-tested; {@link SolsticioCity} feeds it template sizes and marker strings.
 */
public final class CityLayout {
  /** Template directory under {@code data/entrelumen/structure/}. */
  public static final String TEMPLATE_DIR = "solsticio";
  /** {@code solsticio/city.nbt} alone, or a grid of {@code solsticio/piece_<x>_<z>.nbt}. */
  static final Pattern PIECE = Pattern.compile("piece_(\\d{1,3})_(\\d{1,3})");
  static final String SINGLE = "city";
  /** Layer 0 of every template sits at this world height. */
  public static final int BASE_Y = 64;
  /**
   * Edge of the cubes placed as one unit, aligned to world chunk sections so each cube touches one
   * section; the server places cubes until its per-tick time budget is spent.
   */
  public static final int CUBE = 16;
  /** Changes when the cutting changes, so an interrupted placement never resumes with other cubes. */
  public static final String CUT_FORMAT = "cubes16";
  /** Plots are always this wide and deep, from their {@code player_plot} marker. */
  public static final int PLOT_SIZE = 16;
  /** Blocks below the marker a plot owner may dig, and above it they may build. */
  public static final int PLOT_DEPTH = 5, PLOT_HEIGHT = 40;
  /** Void kept around the city inside the border, and the smallest border radius. */
  public static final int BORDER_MARGIN = 48, MIN_BORDER_RADIUS = 128;

  private CityLayout() {}

  /** Markers the controller places as DATA structure blocks; unknown metadata is ignored. */
  public enum Marker {
    ARRIVAL("arrival"),
    TOWN_HALL_PORTAL("town_hall_portal"),
    TOWN_HALL_WAYSTONE("town_hall_waystone"),
    TRADING_HALL("trading_hall"),
    PLAYER_PLOT("player_plot"),
    MAYOR("mayor"),
    INVENTOR("inventor"),
    GARDENER("gardener"),
    PRIEST("priest"),
    /** Present only in placeholder templates; the definitive city must not carry it. */
    PROVISIONAL("provisional"),
    /** {@code shop:<type>}: where a shopkeeper stands behind the counter. */
    SHOP("shop", true),
    /** {@code sidequest:<id>}: the NPC who gives a side quest. */
    SIDEQUEST("sidequest", true),
    /** The home of a common villager. */
    RESIDENT("resident"),
    /** {@code easter:<name>}: an easter egg; only its position is recorded. */
    EASTER("easter", true);

    public final String id;
    /** Written {@code id:<argument>}; the bare id is not a valid marker. */
    public final boolean parameterized;

    Marker(String id) {
      this(id, false);
    }

    Marker(String id, boolean parameterized) {
      this.id = id;
      this.parameterized = parameterized;
    }

    public boolean npc() {
      return this == MAYOR || this == INVENTOR || this == GARDENER || this == PRIEST;
    }

    /**
     * Accepts {@code id} and {@code entrelumen:id}, any case, surrounding spaces ignored; for
     * parameterized markers see {@link CityLayout#parseMarker}.
     */
    public static Optional<Marker> parse(String metadata) {
      return parseMarker(metadata).map(Parsed::marker);
    }
  }

  /** A marker with its argument: the shop type, side quest id or easter egg name, else empty. */
  public record Parsed(Marker marker, String argument) {}

  /** Arguments of parameterized markers: lower-case ids, as in resource paths. */
  static final Pattern ARGUMENT = Pattern.compile("[a-z0-9_][a-z0-9_./-]{0,63}");

  /**
   * Parses a DATA marker. Plain markers accept {@code id} or {@code entrelumen:id}; parameterized
   * ones need {@code id:<argument>} (or {@code entrelumen:id:<argument>}) with a valid argument.
   * Any case, surrounding spaces ignored. Anything else is empty (unknown, logged by the caller).
   */
  public static Optional<Parsed> parseMarker(String metadata) {
    if (metadata == null) return Optional.empty();
    String key = metadata.trim().toLowerCase(Locale.ROOT);
    if (key.startsWith("entrelumen:")) key = key.substring("entrelumen:".length());
    int colon = key.indexOf(':');
    String name = colon < 0 ? key : key.substring(0, colon);
    String argument = colon < 0 ? "" : key.substring(colon + 1).trim();
    for (Marker marker : Marker.values()) {
      if (!marker.id.equals(name)) continue;
      if (marker.parameterized != (colon >= 0)) return Optional.empty();
      if (marker.parameterized && !ARGUMENT.matcher(argument).matches()) return Optional.empty();
      return Optional.of(new Parsed(marker, argument));
    }
    return Optional.empty();
  }

  /** A template piece: grid cell and size. */
  public record Piece(String name, int gridX, int gridZ, int sizeX, int sizeY, int sizeZ) {}

  /** Where a piece's template origin (its minimum corner) lands in the world. */
  public record Placement(Piece piece, int x, int y, int z) {}

  /** A cube of a piece in template-local coordinates, half-open: [from, to). */
  public record Cube(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
    public int width() {
      return toX - fromX;
    }

    public int height() {
      return toY - fromY;
    }

    public int depth() {
      return toZ - fromZ;
    }
  }

  /** Grid cell of a template name without extension, or empty when it is not a city piece. */
  public static Optional<int[]> gridOf(String name) {
    if (SINGLE.equals(name)) return Optional.of(new int[] {0, 0});
    Matcher matcher = PIECE.matcher(name);
    if (!matcher.matches()) return Optional.empty();
    return Optional.of(new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))});
  }

  /**
   * Tiles the pieces: each grid column is as wide as its widest piece and each grid row as deep as
   * its deepest; the whole footprint is centred on x = z = 0 and layer 0 sits on {@link #BASE_Y}.
   * Pieces keep their own origin inside their cell (no stretching). A single {@code city} piece
   * and a {@code piece_x_z} grid may not be mixed.
   */
  public static List<Placement> place(List<Piece> pieces) {
    if (pieces.isEmpty()) return List.of();
    boolean single = pieces.stream().anyMatch(piece -> piece.name().equals(SINGLE));
    if (single && pieces.size() > 1)
      throw new IllegalArgumentException("solsticio/city.nbt cannot be combined with piece_x_z.nbt");
    Map<Integer, Integer> widths = new TreeMap<>(), depths = new TreeMap<>();
    for (Piece piece : pieces) {
      if (piece.sizeX() <= 0 || piece.sizeY() <= 0 || piece.sizeZ() <= 0)
        throw new IllegalArgumentException("Empty template " + piece.name());
      widths.merge(piece.gridX(), piece.sizeX(), Math::max);
      depths.merge(piece.gridZ(), piece.sizeZ(), Math::max);
    }
    Map<Integer, Integer> columnX = offsets(widths), rowZ = offsets(depths);
    int totalX = widths.values().stream().mapToInt(Integer::intValue).sum();
    int totalZ = depths.values().stream().mapToInt(Integer::intValue).sum();
    int startX = -totalX / 2, startZ = -totalZ / 2;
    List<Placement> placements = new ArrayList<>();
    pieces.stream()
        .sorted(Comparator.comparingInt(Piece::gridZ).thenComparingInt(Piece::gridX))
        .forEach(piece -> placements.add(new Placement(piece, startX + columnX.get(piece.gridX()),
            BASE_Y, startZ + rowZ.get(piece.gridZ()))));
    return List.copyOf(placements);
  }

  private static Map<Integer, Integer> offsets(Map<Integer, Integer> sizes) {
    Map<Integer, Integer> offsets = new TreeMap<>();
    int running = 0;
    for (var entry : sizes.entrySet()) {
      offsets.put(entry.getKey(), running);
      running += entry.getValue();
    }
    return offsets;
  }

  /**
   * Half-open intervals covering [0, size) of a piece whose layer 0 sits at world {@code origin},
   * cut wherever the world coordinate is a multiple of {@code edge}.
   */
  public static List<int[]> cuts(int size, int origin, int edge) {
    List<int[]> cuts = new ArrayList<>();
    int from = 0;
    while (from < size) {
      int to = Math.min(size, from + edge - Math.floorMod(origin + from, edge));
      cuts.add(new int[] {from, to});
      from = to;
    }
    return cuts;
  }

  /**
   * World-aligned cubes of one piece placed at {@code (originX, originY, originZ)}: north to south,
   * west to east, and bottom to top inside each column, so supports come before what they hold.
   */
  public static List<Cube> cubes(int sizeX, int sizeY, int sizeZ, int originX, int originY, int originZ, int edge) {
    List<Cube> cubes = new ArrayList<>();
    for (int[] z : cuts(sizeZ, originZ, edge))
      for (int[] x : cuts(sizeX, originX, edge))
        for (int[] y : cuts(sizeY, originY, edge))
          cubes.add(new Cube(x[0], y[0], z[0], x[1], y[1], z[1]));
    return cubes;
  }

  /** Border radius around the origin that keeps the whole footprint plus the margin. */
  public static int borderRadius(ProtectionRules.Box footprint) {
    int extent = Math.max(Math.max(Math.abs(footprint.minX()), Math.abs(footprint.maxX())),
        Math.max(Math.abs(footprint.minZ()), Math.abs(footprint.maxZ())));
    return Math.max(MIN_BORDER_RADIUS, extent + 1 + BORDER_MARGIN);
  }

  /** The plot box for a {@code player_plot} marker at its north-west corner, first free layer. */
  public static ProtectionRules.Box plotBox(int x, int y, int z) {
    return new ProtectionRules.Box(x, y - PLOT_DEPTH, z, x + PLOT_SIZE - 1, y + PLOT_HEIGHT, z + PLOT_SIZE - 1);
  }

  /**
   * The plot a campaign may use: the one it already owns, else the first free plot in marker
   * order, else -1 when every plot is taken. {@code owners} holds each plot's owner or null.
   */
  public static int plotFor(List<UUID> owners, UUID campaign) {
    int index = owners.indexOf(campaign);
    if (index >= 0) return index;
    return owners.indexOf(null);
  }
}
