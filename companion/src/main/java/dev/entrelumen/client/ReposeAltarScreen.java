package dev.entrelumen.client;

import dev.entrelumen.Altars;
import dev.entrelumen.ReposeAltarMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The Altar of Repose's coffer, drawn on vanilla's dispenser panel (176×166, inspected at
 * {@code assets/minecraft/textures/gui/container/dispenser.png}): its centred 3×3 grid is replaced
 * by a centred 2×2 grid of vanilla slot frames, with the title and a status line centred above it,
 * so the panel is symmetric about its middle. The texture is the game's own, read at runtime.
 */
public final class ReposeAltarScreen extends AbstractContainerScreen<ReposeAltarMenu> {
  private static final ResourceLocation PANEL =
      ResourceLocation.withDefaultNamespace("textures/gui/container/dispenser.png");
  /** Vanilla's panel and slot colours, sampled from the dispenser texture. */
  private static final int FACE = 0xFFC6C6C6, SLOT = 0xFF8B8B8B, SLOT_SHADOW = 0xFF373737,
      SLOT_LIGHT = 0xFFFFFFFF, TEXT = 0xFF404040;
  /** The dispenser's 3×3 grid, frames included, which the 2×2 grid replaces. */
  private static final int GRID_X = 61, GRID_Y = 16, GRID_SIZE = 54;

  public ReposeAltarScreen(ReposeAltarMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = ReposeAltarMenu.WIDTH;
    imageHeight = ReposeAltarMenu.HEIGHT;
  }

  @Override
  protected void init() {
    super.init();
    titleLabelX = (imageWidth - font.width(title)) / 2;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int x = leftPos, y = topPos;
    graphics.blit(PANEL, x, y, 0, 0, imageWidth, imageHeight);
    graphics.fill(x + GRID_X, y + GRID_Y, x + GRID_X + GRID_SIZE, y + GRID_Y + GRID_SIZE, FACE);
    for (int[] slot : ReposeAltarMenu.SLOT_POSITIONS) frame(graphics, x + slot[0] - 1, y + slot[1] - 1);
  }

  /** A vanilla 18×18 slot: shadow on the top and left, light on the bottom and right. */
  private static void frame(GuiGraphics graphics, int x, int y) {
    graphics.fill(x, y, x + 18, y + 18, SLOT);
    graphics.fill(x, y, x + 17, y + 1, SLOT_SHADOW);
    graphics.fill(x, y, x + 1, y + 17, SLOT_SHADOW);
    graphics.fill(x + 1, y + 17, x + 18, y + 18, SLOT_LIGHT);
    graphics.fill(x + 17, y + 1, x + 18, y + 18, SLOT_LIGHT);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    super.renderLabels(graphics, mouseX, mouseY);
    int seconds = menu.secondsLeft();
    String time = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    Component status = switch (menu.state()) {
      case 1 -> Component.translatable("entrelumen.altar.repose.gui.repairing", menu.fuel(), time);
      case 2 -> Component.translatable("entrelumen.altar.repose.gui.no_fuel");
      default -> Component.translatable("entrelumen.altar.repose.gui.idle", menu.fuel());
    };
    graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, 17, TEXT, false);
  }

  /** Registers the screen on the client. */
  @EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Registration {
    private Registration() {}

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
      event.register(Altars.REPOSE_MENU.get(), ReposeAltarScreen::new);
    }
  }
}
