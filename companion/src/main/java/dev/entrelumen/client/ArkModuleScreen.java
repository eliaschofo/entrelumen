package dev.entrelumen.client;

import dev.entrelumen.JournalBookNetwork;
import dev.entrelumen.JournalBookNetwork.Entry;
import dev.entrelumen.JournalBookNetwork.Section;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Read-only status of one Ark module, drawn with vanilla parts only: the container panel
 * ({@code textures/gui/demo_background.png}), 18×18 item slots ({@code container/slot}), the
 * experience bar as progress ({@code hud/experience_bar_*}) and the vanilla checkmark
 * ({@code icon/checkmark}). Layout follows the advancement screen's done/missing reading and FTB
 * Quests' task icon with a completion check; see {@code docs/design/ark-field-journals.md}.
 */
public final class ArkModuleScreen extends Screen {
  static final int WIDTH = 220;
  private static final ResourceLocation PANEL =
      ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
  private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
  private static final ResourceLocation CHECK = ResourceLocation.withDefaultNamespace("icon/checkmark");
  private static final ResourceLocation BAR =
      ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
  private static final ResourceLocation BAR_FILL =
      ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
  /** Vanilla label ink and slot colours; the accents are darkened for the light-grey face. */
  private static final int TEXT = 0xFF404040, MUTED = 0xFF707070, SLOT_FACE = 0xFF8B8B8B,
      SLOT_SHADOW = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF, DIM = 0x90C6C6C6, GREEN = 0xFF1F6F1F,
      BLUE = 0xFF24589A, GOLD = 0xFF7F5F00, RED = 0xFF9E2A2A, COPPER = 0xFF8B4C2D;
  private static final int PAD = 8, TEXT_X = 30, BAR_WIDTH = 182, RIGHT = TEXT_X + BAR_WIDTH,
      HEADER = 32, FOOTER = 30, ROW = 18, MATERIAL_ROW = 20, SECTION = 12, SEPARATOR = 7;

  private final JournalBookNetwork.Snapshot snapshot;
  private final List<Block> blocks = new ArrayList<>();
  private int left, top, panelHeight, bodyTop, bodyHeight, contentHeight;
  private double scroll;

  public ArkModuleScreen(JournalBookNetwork.Snapshot snapshot) {
    super(Component.translatable("block.entrelumen." + snapshot.kind().module()));
    this.snapshot = snapshot;
  }

  @Override
  protected void init() {
    layout();
    int maxBody = Math.max(40, height - 16 - HEADER - FOOTER);
    bodyHeight = Math.min(contentHeight, maxBody);
    panelHeight = HEADER + bodyHeight + FOOTER;
    left = (width - WIDTH) / 2;
    top = (height - panelHeight) / 2;
    bodyTop = top + HEADER;
    scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - bodyHeight));
    addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
        .bounds(left + (WIDTH - 90) / 2, top + panelHeight - FOOTER + 4, 90, 20).build());
  }

  private void layout() {
    blocks.clear();
    var materials = snapshot.entries(Section.BATCH);
    if (!materials.isEmpty()) {
      blocks.add(section(Component.translatable("entrelumen.journal.section.batch"), null));
      materials.forEach(entry -> blocks.add(new MaterialRow(entry)));
    }
    var projects = snapshot.entries(Section.PROJECTS);
    if (!projects.isEmpty()) {
      blocks.add(section(Component.translatable("entrelumen.journal.section.projects"), count(projects)));
      projects.forEach(entry -> blocks.add(new CheckRow(entry)));
    }
    var journeys = snapshot.entries(Section.JOURNEYS);
    if (!journeys.isEmpty()) {
      blocks.add(section(Component.translatable("entrelumen.journal.section.journeys"), count(journeys)));
      blocks.add(new JourneyStrip(journeys));
    }
    blocks.add(new Separator());
    snapshot.entries(Section.ARK).forEach(entry -> blocks.add(new CheckRow(entry)));
    snapshot.entries(Section.SERVICE).forEach(entry -> blocks.add(new CheckRow(entry)));
    blocks.add(new Separator());
    blocks.add(new Paragraph(snapshot.flavor(), COPPER));
    snapshot.hint().ifPresent(hint -> blocks.add(new Paragraph(hint, switch (snapshot.status()) {
      case BLOCKED -> RED;
      case DELIVERING -> TEXT;
      default -> MUTED;
    })));
    contentHeight = blocks.stream().mapToInt(Block::height).sum() + 2;
  }

  private static Component count(List<Entry> entries) {
    long done = entries.stream().filter(Entry::complete).count();
    return Component.translatable("entrelumen.journal.count", done, entries.size());
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderTransparentBackground(graphics);
    panel(graphics, left, top, WIDTH, panelHeight);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    ItemStack module = stack(ResourceLocation.fromNamespaceAndPath("entrelumen",
        snapshot.kind().module()));
    graphics.renderItem(module, left + PAD, top + 8);
    graphics.drawString(font, fit(title, WIDTH - TEXT_X + 2 - PAD), left + TEXT_X - 2, top + 7, TEXT, false);
    graphics.drawString(font, fit(snapshot.statusLine(), WIDTH - TEXT_X + 2 - PAD), left + TEXT_X - 2,
        top + 18, statusColor(), false);
    etch(graphics, left + PAD, top + HEADER - 4, WIDTH - 2 * PAD);

    graphics.enableScissor(left + 4, bodyTop, left + WIDTH - 4, bodyTop + bodyHeight);
    int y = bodyTop - (int) scroll;
    Block hovered = null;
    int hoveredY = 0;
    boolean inBody = mouseX >= left && mouseX < left + WIDTH && mouseY >= bodyTop
        && mouseY < bodyTop + bodyHeight;
    for (Block block : blocks) {
      if (y + block.height() > bodyTop && y < bodyTop + bodyHeight) block.render(graphics, left, y);
      if (inBody && mouseY >= y && mouseY < y + block.height()) {
        hovered = block;
        hoveredY = y;
      }
      y += block.height();
    }
    graphics.disableScissor();
    if (contentHeight > bodyHeight) scrollbar(graphics);
    if (hovered != null) {
      var tooltip = hovered.tooltip(mouseX - left, mouseY - hoveredY);
      if (!tooltip.isEmpty()) graphics.renderTooltip(font, wrap(tooltip), mouseX, mouseY);
    }
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (contentHeight <= bodyHeight) return false;
    scroll = Math.clamp(scroll - scrollY * ROW, 0, contentHeight - bodyHeight);
    return true;
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    int step = switch (keyCode) {
      case GLFW.GLFW_KEY_UP -> -ROW;
      case GLFW.GLFW_KEY_DOWN -> ROW;
      case GLFW.GLFW_KEY_PAGE_UP -> -bodyHeight;
      case GLFW.GLFW_KEY_PAGE_DOWN -> bodyHeight;
      default -> 0;
    };
    if (step != 0 && contentHeight > bodyHeight) {
      scroll = Math.clamp(scroll + step, 0, contentHeight - bodyHeight);
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  private int statusColor() {
    return switch (snapshot.status()) {
      case DELIVERING -> BLUE;
      case DELIVERED -> GOLD;
      case BLOCKED -> RED;
      case WAITING, ARCHIVED -> MUTED;
    };
  }

  private void scrollbar(GuiGraphics graphics) {
    int x = left + WIDTH - 6;
    int thumb = Math.max(12, bodyHeight * bodyHeight / contentHeight);
    int y = bodyTop + (int) ((bodyHeight - thumb) * scroll / (contentHeight - bodyHeight));
    graphics.fill(x, bodyTop, x + 2, bodyTop + bodyHeight, SLOT_FACE);
    graphics.fill(x, y, x + 2, y + thumb, SLOT_SHADOW);
  }

  /** The vanilla container panel, nine-sliced from its 4-pixel corners. */
  private static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
    int c = 4, tw = 248, th = 166, mid = tw - 2 * c, midH = th - 2 * c;
    graphics.blit(PANEL, x, y, 0, 0, c, c, 256, 256);
    graphics.blit(PANEL, x + w - c, y, tw - c, 0, c, c, 256, 256);
    graphics.blit(PANEL, x, y + h - c, 0, th - c, c, c, 256, 256);
    graphics.blit(PANEL, x + w - c, y + h - c, tw - c, th - c, c, c, 256, 256);
    graphics.blit(PANEL, x + c, y, w - 2 * c, c, c, 0, mid, c, 256, 256);
    graphics.blit(PANEL, x + c, y + h - c, w - 2 * c, c, c, th - c, mid, c, 256, 256);
    graphics.blit(PANEL, x, y + c, c, h - 2 * c, 0, c, c, midH, 256, 256);
    graphics.blit(PANEL, x + w - c, y + c, c, h - 2 * c, tw - c, c, c, midH, 256, 256);
    graphics.blit(PANEL, x + c, y + c, w - 2 * c, h - 2 * c, c, c, mid, midH, 256, 256);
  }

  /** An engraved rule: vanilla slot shadow over highlight. */
  private static void etch(GuiGraphics graphics, int x, int y, int w) {
    graphics.fill(x, y, x + w, y + 1, SLOT_FACE);
    graphics.fill(x, y + 1, x + w, y + 2, SLOT_LIGHT);
  }

  /** A 9×9 empty box in vanilla slot colours: the "not yet" mark. */
  private static void emptyBox(GuiGraphics graphics, int x, int y) {
    graphics.fill(x, y, x + 9, y + 9, SLOT_FACE);
    graphics.fill(x, y, x + 8, y + 1, SLOT_SHADOW);
    graphics.fill(x, y, x + 1, y + 8, SLOT_SHADOW);
    graphics.fill(x + 1, y + 8, x + 9, y + 9, SLOT_LIGHT);
    graphics.fill(x + 8, y + 1, x + 9, y + 9, SLOT_LIGHT);
  }

  private static ItemStack stack(ResourceLocation id) {
    var item = BuiltInRegistries.ITEM.get(id);
    return new ItemStack(item);
  }

  private Component label(Entry entry) {
    return entry.label().orElseGet(() -> stack(entry.icon()).getHoverName());
  }

  private FormattedCharSequence fit(Component text, int width) {
    if (font.width(text) <= width) return text.getVisualOrderText();
    FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("...")));
    return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
  }

  private List<FormattedCharSequence> wrap(List<Component> lines) {
    List<FormattedCharSequence> wrapped = new ArrayList<>();
    for (Component line : lines) wrapped.addAll(font.split(line, 200));
    return wrapped;
  }

  private Block section(Component label, Component count) {
    return new Block() {
      @Override public int height() { return SECTION; }

      @Override public void render(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, label, x + PAD, y + 3, TEXT, false);
        if (count != null)
          graphics.drawString(font, count, x + RIGHT - font.width(count), y + 3, MUTED, false);
      }
    };
  }

  private interface Block {
    int height();

    void render(GuiGraphics graphics, int x, int y);

    default List<Component> tooltip(int localX, int localY) {
      return List.of();
    }
  }

  private static final class Separator implements Block {
    @Override public int height() { return SEPARATOR; }

    @Override public void render(GuiGraphics graphics, int x, int y) {
      etch(graphics, x + PAD, y + 2, WIDTH - 2 * PAD);
    }
  }

  /** A batch material: icon, name, {@code done / total} and the experience bar as progress. */
  private final class MaterialRow implements Block {
    private final Entry entry;

    MaterialRow(Entry entry) { this.entry = entry; }

    @Override public int height() { return MATERIAL_ROW; }

    @Override public void render(GuiGraphics graphics, int x, int y) {
      graphics.blitSprite(SLOT, x + PAD, y, 18, 18);
      graphics.renderItem(stack(entry.icon()), x + PAD + 1, y + 1);
      var count = Component.translatable("entrelumen.journal.amount", entry.done(), entry.total());
      int countWidth = font.width(count);
      graphics.drawString(font, fit(label(entry), BAR_WIDTH - countWidth - 4), x + TEXT_X, y + 1,
          TEXT, false);
      graphics.drawString(font, count, x + RIGHT - countWidth, y + 1,
          entry.complete() ? GREEN : TEXT, false);
      graphics.blitSprite(BAR, x + TEXT_X, y + 12, BAR_WIDTH, 5);
      int filled = entry.total() == 0 ? 0 : BAR_WIDTH * entry.done() / entry.total();
      if (filled > 0) graphics.blitSprite(BAR_FILL, BAR_WIDTH, 5, 0, 0, x + TEXT_X, y + 12, filled, 5);
    }

    @Override public List<Component> tooltip(int localX, int localY) {
      return List.of(label(entry), entry.complete()
          ? Component.translatable("entrelumen.journal.material_done")
          : Component.translatable("entrelumen.journal.material_left", entry.total() - entry.done()));
    }
  }

  /** A project, the Ark or a service: icon, label and a check, an empty box or nothing. */
  private final class CheckRow implements Block {
    private final Entry entry;

    CheckRow(Entry entry) { this.entry = entry; }

    @Override public int height() { return ROW; }

    @Override public void render(GuiGraphics graphics, int x, int y) {
      graphics.blitSprite(SLOT, x + PAD, y, 18, 18);
      graphics.renderItem(stack(entry.icon()), x + PAD + 1, y + 1);
      int room = RIGHT - TEXT_X - (entry.total() > 0 ? 13 : 0);
      graphics.drawString(font, fit(label(entry), room), x + TEXT_X, y + 5, TEXT, false);
      if (entry.total() == 0) return;
      if (entry.complete()) graphics.blitSprite(CHECK, x + RIGHT - 9, y + 5, 9, 8);
      else emptyBox(graphics, x + RIGHT - 9, y + 4);
    }

    @Override public List<Component> tooltip(int localX, int localY) {
      List<Component> lines = new ArrayList<>();
      lines.add(label(entry));
      if (!entry.details().isEmpty()) lines.addAll(entry.details());
      else if (entry.total() > 0)
        lines.add(Component.translatable(entry.complete()
            ? "entrelumen.journal.recorded" : "entrelumen.journal.pending"));
      return lines;
    }
  }

  /** Witnessed journeys as dimension icons; a missing one is greyed, a witnessed one checked. */
  private final class JourneyStrip implements Block {
    private static final int STEP = 22;
    private final List<Entry> journeys;

    JourneyStrip(List<Entry> journeys) { this.journeys = List.copyOf(journeys); }

    @Override public int height() { return ROW + 2; }

    @Override public void render(GuiGraphics graphics, int x, int y) {
      for (int i = 0; i < journeys.size(); i++) {
        var entry = journeys.get(i);
        int slotX = x + PAD + i * STEP;
        graphics.blitSprite(SLOT, slotX, y, 18, 18);
        graphics.renderItem(stack(entry.icon()), slotX + 1, y + 1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        if (entry.complete()) graphics.blitSprite(CHECK, slotX + 10, y + 11, 9, 8);
        else graphics.fill(slotX + 1, y + 1, slotX + 17, y + 17, DIM);
        graphics.pose().popPose();
      }
    }

    @Override public List<Component> tooltip(int localX, int localY) {
      int index = (localX - PAD) / STEP;
      if (localX < PAD || index >= journeys.size() || (localX - PAD) % STEP >= 18) return List.of();
      var entry = journeys.get(index);
      return List.of(label(entry), Component.translatable(entry.complete()
          ? "entrelumen.journal.witnessed" : "entrelumen.journal.not_witnessed"));
    }
  }

  /** Flavor or hint text, centred and wrapped inside the panel. */
  private final class Paragraph implements Block {
    private final List<FormattedCharSequence> lines;
    private final int color;

    Paragraph(Component text, int color) {
      this.lines = font.split(text, WIDTH - 2 * PAD - 4);
      this.color = color;
    }

    @Override public int height() { return lines.size() * 10 + 3; }

    @Override public void render(GuiGraphics graphics, int x, int y) {
      for (int i = 0; i < lines.size(); i++) {
        var line = lines.get(i);
        graphics.drawString(font, line, x + (WIDTH - font.width(line)) / 2, y + 2 + i * 10, color, false);
      }
    }
  }
}
