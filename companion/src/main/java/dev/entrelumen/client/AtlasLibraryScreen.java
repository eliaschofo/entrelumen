package dev.entrelumen.client;

import dev.entrelumen.AtlasLibraryMenu;
import dev.entrelumen.AtlasLibraryNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * Native-widget view of a server snapshot: a searchable list of every enchantment the library
 * offers. Clicking asks the server for the next level, shift-clicking for the highest affordable
 * one; the server re-validates everything.
 */
public final class AtlasLibraryScreen extends AbstractContainerScreen<AtlasLibraryMenu> {
  private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555,
      SLOT = 0xFF8B8B8B, SLOT_DARK = 0xFF373737, TEXT = 0xFF404040, NAME = 0xFFFFFFFF,
      OK = 0xFFE0B25A, NO = 0xFFD06060, MUTED = 0xFFA0A0A0;
  private static final int LIST_X = 8, LIST_Y = 32, LIST_W = 160, LIST_H = 92, ROW = 22;
  private EditBox search;
  private OfferList list;

  public AtlasLibraryScreen(AtlasLibraryMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = AtlasLibraryMenu.WIDTH;
    imageHeight = AtlasLibraryMenu.HEIGHT;
    inventoryLabelX = 48;
    inventoryLabelY = 129;
  }

  @Override
  protected void init() {
    super.init();
    String previous = search == null ? "" : search.getValue();
    search = addRenderableWidget(new EditBox(font, leftPos + LIST_X, topPos + 17, LIST_W, 12,
        Component.translatable("entrelumen.atlas_library.search")));
    search.setHint(Component.translatable("entrelumen.atlas_library.search"));
    search.setValue(previous);
    search.setResponder(ignored -> list.populate());
    list = addRenderableWidget(new OfferList());
    list.populate();
    menu.onSnapshot = () -> list.populate();
  }

  @Override
  public void removed() {
    menu.onSnapshot = () -> {};
    super.removed();
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (search.isFocused() && key != GLFW.GLFW_KEY_ESCAPE)
      return search.keyPressed(key, scan, modifiers) || search.canConsumeInput();
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
    graphics.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
    graphics.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
    graphics.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, SHADOW);
    graphics.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, SHADOW);
    frame(graphics, x + LIST_X - 1, y + LIST_Y - 1, LIST_W + 2, LIST_H + 2);
    for (Slot slot : menu.slots) frame(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18);
  }

  private static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
    graphics.fill(x, y, x + w, y + h, SLOT);
    graphics.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
    graphics.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
    graphics.fill(x + 1, y + h - 1, x + w, y + h, LIGHT);
    graphics.fill(x + w - 1, y + 1, x + w, y + h, LIGHT);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, title, 8, 6, TEXT, false);
    graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    graphics.drawString(font, Component.translatable("entrelumen.atlas_library.deposit"), 172, 29, TEXT, false);
    graphics.drawString(font, Component.translatable("entrelumen.atlas_library.output"), 172, 77, TEXT, false);
    var snapshot = menu.snapshot;
    if (snapshot == null) return;
    graphics.drawString(font, Component.translatable("entrelumen.atlas_library.pool", compact(snapshot.pool())),
        172, 110, TEXT, false);
    graphics.drawString(font, Component.translatable("entrelumen.atlas_library.eterna",
        String.format(Locale.ROOT, "%.1f", snapshot.eterna())), 172, 120, TEXT, false);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
  }

  static String compact(long value) {
    if (value < 10_000) return Long.toString(value);
    String[] units = {"K", "M", "B", "T", "P", "E"};
    double scaled = value;
    int unit = -1;
    while (scaled >= 1000 && unit < units.length - 1) {
      scaled /= 1000;
      unit++;
    }
    return String.format(Locale.ROOT, "%.1f%s", scaled, units[unit]);
  }

  private void request(AtlasLibraryNetwork.Offer offer, boolean max) {
    int target = max ? offer.max() : offer.next();
    if (target <= 0 || minecraft == null || minecraft.gameMode == null) return;
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
        AtlasLibraryMenu.encode(offer.enchantment(), target));
  }

  private final class OfferList extends ObjectSelectionList<OfferRow> {
    OfferList() {
      super(AtlasLibraryScreen.this.minecraft, LIST_W, LIST_H, topPos + LIST_Y, ROW);
      setX(leftPos + LIST_X);
    }

    void populate() {
      double scroll = getScrollAmount();
      clearEntries();
      var snapshot = menu.snapshot;
      if (snapshot != null && minecraft.level != null) {
        var registry = minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        String filter = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<OfferRow> rows = new ArrayList<>();
        for (var offer : snapshot.offers()) {
          var holder = registry.getHolder(offer.enchantment());
          if (holder.isEmpty()) continue;
          Component name = holder.get().value().description();
          if (!filter.isEmpty() && !name.getString().toLowerCase(Locale.ROOT).contains(filter)) continue;
          rows.add(new OfferRow(offer, name));
        }
        rows.sort(Comparator.comparing(row -> row.name.getString()));
        rows.forEach(this::addEntry);
      }
      setScrollAmount(scroll);
    }

    @Override
    public int getRowWidth() {
      return getWidth() - 10;
    }

    @Override
    public int getRowLeft() {
      return getX() + 2;
    }

    @Override
    protected int getScrollbarPosition() {
      return getRight() - 6;
    }
  }

  private final class OfferRow extends ObjectSelectionList.Entry<OfferRow> {
    final AtlasLibraryNetwork.Offer offer;
    final Component name;

    OfferRow(AtlasLibraryNetwork.Offer offer, Component name) {
      this.offer = offer;
      this.name = name;
    }

    Component status() {
      if (offer.next() == 0)
        return Component.translatable("entrelumen.atlas_library.capped", offer.cap());
      return Component.translatable("entrelumen.atlas_library.next", offer.next(), compact(offer.nextCost()));
    }

    boolean affordable() {
      var snapshot = menu.snapshot;
      return offer.next() > 0 && snapshot != null && snapshot.pool() >= offer.nextCost();
    }

    @Override
    public Component getNarration() {
      return name.copy().append(". ").append(status());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
      if (button != 0 || x >= AtlasLibraryScreen.this.list.getScrollbarPosition()) return false;
      AtlasLibraryScreen.this.list.setSelected(this);
      request(offer, Screen.hasShiftDown());
      return true;
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
        int mouseX, int mouseY, boolean hovered, float partialTick) {
      Component title = offer.current() > 0
          ? name.copy().append(" " + offer.current())
          : name;
      graphics.drawString(font, font.plainSubstrByWidth(title.getString(), rowWidth - 4), x + 2, y + 1, NAME, false);
      graphics.drawString(font, font.plainSubstrByWidth(status().getString(), rowWidth - 4), x + 2, y + 11,
          offer.next() == 0 ? MUTED : affordable() ? OK : NO, false);
      if (hovered) {
        List<Component> lines = new ArrayList<>();
        lines.add(name);
        lines.add(Component.translatable("entrelumen.atlas_library.cap", offer.cap()));
        lines.add(status());
        if (offer.max() > 0)
          lines.add(Component.translatable("entrelumen.atlas_library.max", offer.max(), compact(offer.maxCost())));
        lines.add(Component.translatable("entrelumen.atlas_library.click_hint"));
        setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
      }
    }
  }
}
