package dev.entrelumen.client;

import com.mojang.math.Axis;
import dev.entrelumen.EnvesGeometry;
import dev.entrelumen.EnvesLayout;
import dev.entrelumen.EnvesLayout.Dir;
import dev.entrelumen.EnvesLayout.Role;
import dev.entrelumen.EnvesNetwork;
import dev.entrelumen.EnvesRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Envés fog map in the Atlas's parchment look: the vanilla map parchment, an ink-wash fog
 * over the unknown, glimpsed rooms as a soft blur, explored rooms crisp with their doors, the icons
 * of what was found, the party and the player's arrow. The same drawing serves the HUD minimap and
 * the big view.
 */
public final class EnvesMapRenderer {
  static final ResourceLocation PARCHMENT = ResourceLocation.withDefaultNamespace("textures/map/map_background.png");
  static final ResourceLocation FOG = ResourceLocation.fromNamespaceAndPath("entrelumen", "textures/gui/enves/fog.png");
  static final ResourceLocation GLIMPSE = ResourceLocation.fromNamespaceAndPath("entrelumen", "textures/gui/enves/glimpse.png");
  static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("entrelumen", "textures/gui/enves/icons.png");
  static final ResourceLocation PLAYER = ResourceLocation.withDefaultNamespace("textures/map/decorations/player.png");
  static final ResourceLocation MATE = ResourceLocation.withDefaultNamespace("textures/map/decorations/blue_marker.png");
  /** Atlas palette: page, ink, faded ink, copper. */
  static final int PAGE = 0xFFEADBB6, INK = 0xFF382F25, FAINT = 0xFF77684E, COPPER = 0xFF8B4C2D, GLIMPSED = 0xE8D8C39A;

  /** Icon slots in icons.png. */
  enum Icon {START, STAIRS, SEAL, SEAL_LIT, VAULT, SHRINE, GUARD, PORTAL}

  private EnvesMapRenderer() {}

  /** The whole map with its frame: {@code size} = cells × {@code cell} + 2 × {@code border}. */
  static void draw(GuiGraphics graphics, int x, int y, int cell, int border, boolean big) {
    int grid = EnvesLayout.W * cell;
    int size = grid + 2 * border;
    graphics.blit(PARCHMENT, x, y, 0, 0, size, size, size, size);
    int gx = x + border, gy = y + border;
    EnvesNetwork.MapView map = EnvesClientState.map();
    // The unknown: ink fog over the whole grid, known cells drawn on top.
    graphics.blit(FOG, gx, gy, 0, 0, grid, grid, 32, 32);
    if (map != null) {
      for (var known : map.cells()) if (!known.explored()) drawGlimpse(graphics, gx, gy, cell, known.cell());
      for (var known : map.cells()) if (known.explored()) drawRoom(graphics, gx, gy, cell, known);
      for (var known : map.cells()) if (known.explored()) drawIcon(graphics, gx, gy, cell, known, big);
      drawPlayers(graphics, gx, gy, cell, map, big);
    }
  }

  private static void drawGlimpse(GuiGraphics graphics, int gx, int gy, int cell, int index) {
    int cx = gx + EnvesLayout.x(index) * cell, cy = gy + EnvesLayout.z(index) * cell;
    int pad = Math.max(1, cell / 5);
    graphics.setColor(0.93f, 0.86f, 0.72f, 0.9f);
    graphics.blit(GLIMPSE, cx - pad, cy - pad, 0, 0, cell + 2 * pad, cell + 2 * pad, cell + 2 * pad, cell + 2 * pad);
    graphics.setColor(1f, 1f, 1f, 1f);
  }

  private static void drawRoom(GuiGraphics graphics, int gx, int gy, int cell, EnvesRules.KnownCell known) {
    int x0 = gx + EnvesLayout.x(known.cell()) * cell, y0 = gy + EnvesLayout.z(known.cell()) * cell;
    int inset = Math.max(1, cell / 8);
    int ax = x0 + inset, ay = y0 + inset, bx = x0 + cell - inset, by = y0 + cell - inset;
    graphics.fill(ax, ay, bx, by, known.role() == Role.EXIT || known.role() == Role.PORTAL ? 0xFFF0CC78 : PAGE);
    // Walls in ink, broken by doors; corridors reach the cell edge toward the neighbour.
    int door = Math.max(1, cell / 3);
    int mx = x0 + cell / 2, my = y0 + cell / 2;
    for (Dir dir : Dir.values()) {
      boolean open = (known.doors() & dir.bit) != 0;
      switch (dir) {
        case N -> side(graphics, ax, ay, bx, ay + 1, open, mx - door / 2, mx + (door + 1) / 2, true);
        case S -> side(graphics, ax, by - 1, bx, by, open, mx - door / 2, mx + (door + 1) / 2, true);
        case W -> side(graphics, ax, ay, ax + 1, by, open, my - door / 2, my + (door + 1) / 2, false);
        case E -> side(graphics, bx - 1, ay, bx, by, open, my - door / 2, my + (door + 1) / 2, false);
      }
      if (open) {
        switch (dir) {
          case N -> graphics.fill(mx - door / 2, y0, mx + (door + 1) / 2, ay, PAGE);
          case S -> graphics.fill(mx - door / 2, by, mx + (door + 1) / 2, y0 + cell, PAGE);
          case W -> graphics.fill(x0, my - door / 2, ax, my + (door + 1) / 2, PAGE);
          case E -> graphics.fill(bx, my - door / 2, x0 + cell, my + (door + 1) / 2, PAGE);
        }
      }
    }
  }

  /** One wall line; an open side leaves a gap of the door's width. */
  private static void side(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean open, int g0, int g1, boolean horizontal) {
    if (!open) {
      graphics.fill(x0, y0, x1, y1, INK);
      return;
    }
    if (horizontal) {
      graphics.fill(x0, y0, g0, y1, INK);
      graphics.fill(g1, y0, x1, y1, INK);
    } else {
      graphics.fill(x0, y0, x1, g0, INK);
      graphics.fill(x0, g1, x1, y1, INK);
    }
  }

  private static void drawIcon(GuiGraphics graphics, int gx, int gy, int cell, EnvesRules.KnownCell known, boolean big) {
    Icon icon = switch (known.role() == null ? Role.QUIET : known.role()) {
      case START -> Icon.START;
      case EXIT -> Icon.STAIRS;
      case SEAL -> known.lit() ? Icon.SEAL_LIT : Icon.SEAL;
      case VAULT -> Icon.VAULT;
      case SHRINE -> Icon.SHRINE;
      case GUARD, ARENA_CENTER -> Icon.GUARD;
      case PORTAL -> Icon.PORTAL;
      default -> null;
    };
    if (icon == null) return;
    int scale = big ? Math.max(1, cell / 10) : 1;
    int s = 8 * scale;
    int x = gx + EnvesLayout.x(known.cell()) * cell + (cell - s) / 2;
    int y = gy + EnvesLayout.z(known.cell()) * cell + (cell - s) / 2;
    graphics.blit(ICONS, x, y, s, s, icon.ordinal() * 8, 0, 8, 8, 64, 8);
  }

  private static void drawPlayers(GuiGraphics graphics, int gx, int gy, int cell, EnvesNetwork.MapView map, boolean big) {
    var minecraft = Minecraft.getInstance();
    var hud = EnvesClientState.hud();
    float perBlock = cell / (float) EnvesGeometry.CELL;
    int marker = big ? 8 : 6;
    if (hud != null)
      for (var mate : hud.mates()) {
        if (Math.max(1, mate.depth()) != map.depth()) continue;
        float px = gx + (mate.x() - map.originX()) * perBlock, pz = gy + (mate.z() - map.originZ()) * perBlock;
        graphics.blit(MATE, Math.round(px - marker / 2f), Math.round(pz - marker / 2f), marker, marker, 0, 0, 8, 8, 8, 8);
      }
    var player = minecraft.player;
    if (player == null) return;
    float px = gx + (float) (player.getX() - map.originX()) * perBlock;
    float pz = gy + (float) (player.getZ() - map.originZ()) * perBlock;
    var pose = graphics.pose();
    pose.pushPose();
    pose.translate(px, pz, 0);
    pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot() + 180f));
    graphics.blit(PLAYER, -marker / 2, -marker / 2, marker, marker, 0, 0, 8, 8, 8, 8);
    pose.popPose();
  }
}
