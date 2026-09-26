package dev.entrelumen.client;

import dev.entrelumen.ArkCommerce;
import dev.entrelumen.AtlasNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Logistics module's catalog, opened from the Atlas: one vanilla button per Solsticio shop the
 * team knows. A button asks the server to open that shop's own trading screen.
 */
public final class ArkCatalogScreen extends Screen {
  private static final int BUTTON = 150, GAP = 4, ROW = 22;
  private final AtlasNetwork.Catalog catalog;

  public ArkCatalogScreen(AtlasNetwork.Catalog catalog) {
    super(Component.translatable("entrelumen.trade.title"));
    this.catalog = catalog;
  }

  public static void receive(AtlasNetwork.Catalog catalog) {
    Minecraft.getInstance().setScreen(new ArkCatalogScreen(catalog));
  }

  @Override
  protected void init() {
    List<ArkCommerce.Shop> shops = catalog.shops();
    int columns = 2;
    int rows = (shops.size() + columns - 1) / columns;
    int left = (width - columns * BUTTON - GAP) / 2;
    int top = Math.max(40, (height - rows * ROW) / 2);
    for (int i = 0; i < shops.size(); i++) {
      var shop = shops.get(i);
      int x = left + (i % columns) * (BUTTON + GAP);
      int y = top + (i / columns) * ROW;
      var button = Button.builder(ArkCommerce.shopName(shop.key()), pressed -> {
        PacketDistributor.sendToServer(new AtlasNetwork.TradeRequest(shop.id()));
        onClose();
      }).bounds(x, y, BUTTON, 20).build();
      button.active = catalog.active() && shop.available();
      if (!shop.available()) button.setTooltip(Tooltip.create(Component.translatable("entrelumen.trade.absent_short")));
      addRenderableWidget(button);
    }
    addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, pressed -> onClose())
        .bounds((width - 100) / 2, Math.min(height - 28, top + Math.max(rows, 1) * ROW + 12), 100, 20).build());
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
    Component note = !catalog.active() ? Component.translatable("entrelumen.trade.locked")
        : catalog.shops().isEmpty() ? Component.translatable("entrelumen.trade.none")
        : Component.translatable("entrelumen.trade.hint");
    int y = 28;
    for (FormattedCharSequence line : font.split(note, Math.min(width - 32, 320))) {
      graphics.drawCenteredString(font, line, width / 2, y, 0xA0A0A0);
      y += font.lineHeight;
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
