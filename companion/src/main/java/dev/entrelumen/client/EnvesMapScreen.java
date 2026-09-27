package dev.entrelumen.client;

import dev.entrelumen.AtlasNetwork;
import dev.entrelumen.EnvesLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Atlas inside the Envés: the fog map of the current floor, big, with the legend and the
 * numbers; a button turns to the Atlas's usual pages.
 */
public final class EnvesMapScreen extends Screen {
  private static final int PAPER = 0xFF382F25, MUTED = 0xFF77684E;
  private final AtlasNetwork.Snapshot atlas;
  private int cell, mapX, mapY, size;

  public EnvesMapScreen(AtlasNetwork.Snapshot atlas) {
    super(Component.translatable("entrelumen.enves.map.title"));
    this.atlas = atlas;
  }

  @Override
  protected void init() {
    cell = Math.max(10, Math.min(20, (Math.min(width - 150, height - 40)) / EnvesLayout.W));
    size = EnvesLayout.W * cell + 16;
    mapX = (width - size - 130) / 2;
    mapY = (height - size) / 2;
    if (atlas != null)
      addRenderableWidget(Button.builder(Component.translatable("entrelumen.enves.map.atlas"),
          button -> minecraft.setScreen(new AtlasScreen(atlas))).bounds(mapX + size + 12, mapY + size - 44, 110, 20).build());
    addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
        .bounds(mapX + size + 12, mapY + size - 20, 110, 20).build());
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    EnvesMapRenderer.draw(graphics, mapX, mapY, cell, 8, true);
    int x = mapX + size + 12, y = mapY + 4;
    graphics.fill(x - 6, mapY, x + 118, mapY + size - 50, 0xE8EADBB6);
    graphics.drawString(font, title, x, y, PAPER, false);
    y += 14;
    var hud = EnvesClientState.hud();
    if (hud != null)
      for (Component line : EnvesClient.lines(hud)) {
        graphics.drawString(font, line, x, y, PAPER, false);
        y += 11;
      }
    y += 6;
    String[] legend = {"start", "stairs", "seal", "seal_lit", "vault", "shrine", "guard", "portal"};
    for (int i = 0; i < legend.length && y < mapY + size - 60; i++) {
      graphics.blit(EnvesMapRenderer.ICONS, x, y, 8, 8, i * 8, 0, 8, 8, 64, 8);
      graphics.drawString(font, Component.translatable("entrelumen.enves.map.legend." + legend[i]), x + 12, y, MUTED, false);
      y += 11;
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
