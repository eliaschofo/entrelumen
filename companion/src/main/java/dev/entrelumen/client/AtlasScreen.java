package dev.entrelumen.client;

import dev.entrelumen.AtlasNetwork;
import dev.entrelumen.CampaignActions;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native, keyboard-accessible list/detail Atlas. Only active-act data is sent by the server. */
public final class AtlasScreen extends Screen {
  private static final int PAPER = 0xFF382F25, MUTED = 0xFF77684E,
      COPPER = 0xFF8B4C2D;
  private static final net.minecraft.resources.ResourceLocation BOOK =
      net.minecraft.resources.ResourceLocation.parse("entrelumen:textures/gui/atlas_book.png");
  private int bookY;
  private AtlasNetwork.Snapshot snapshot;
  private String selectedId = "";
  private ProjectList projects;
  private DetailList details;
  private Button deliver, advance, refresh;
  private boolean waiting;
  private long requestedAt;
  private String feedback = "";
  private long completedCount;
  private int panelX, panelWidth, leftWidth, contentTop, contentBottom, detailX, detailWidth;

  public AtlasScreen(AtlasNetwork.Snapshot snapshot) {
    super(Component.translatable("entrelumen.atlas.title"));
    this.snapshot = snapshot;
    this.feedback = snapshot.message();
  }

  public static void receive(AtlasNetwork.Snapshot snapshot) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.screen instanceof AtlasScreen atlas) atlas.update(snapshot);
    else if (snapshot.open()) minecraft.setScreen(new AtlasScreen(snapshot));
  }

  private void update(AtlasNetwork.Snapshot updated) {
    if (!updated.campaign().equals(snapshot.campaign()) || updated.act() != snapshot.act())
      selectedId = "";
    double scroll = projects == null ? 0 : projects.getScrollAmount();
    snapshot = updated;
    feedback = updated.message();
    waiting = false;
    rebuildWidgets();
    projects.setScrollAmount(scroll);
  }

  @Override
  protected void init() {
    completedCount =
        snapshot.projects().stream().filter(AtlasNetwork.ProjectView::completed).count();
    panelWidth = 300;
    panelX = (width - panelWidth) / 2;
    bookY = (height - 210) / 2;
    leftWidth = 110;
    contentTop = bookY + 45;
    contentBottom = bookY + 154;
    detailX = panelX + 166;
    detailWidth = 106;
    projects = new ProjectList(leftWidth, contentBottom - contentTop, contentTop);
    projects.setX(panelX + 24);
    details = new DetailList(detailWidth, contentBottom - contentTop, contentTop);
    details.setX(detailX);
    addRenderableWidget(projects);
    addRenderableWidget(details);
    addRenderableWidget(new BookButton(panelX + 247, bookY + 15, 25,
        Component.translatable("gui.done"), button -> onClose()));
    refresh = addRenderableWidget(new BookButton(panelX + 24, bookY + 158, 52,
        Component.translatable("entrelumen.atlas.refresh_short"),
        button -> request(CampaignActions.Action.REFRESH, "")));
    advance = addRenderableWidget(new BookButton(panelX + 81, bookY + 158, 53,
        Component.translatable("entrelumen.atlas.advance"),
        button -> request(CampaignActions.Action.ADVANCE, "")));
    deliver = addRenderableWidget(new BookButton(detailX + 10, bookY + 158, 86,
        Component.translatable("entrelumen.atlas.deliver"),
        button -> request(CampaignActions.Action.DELIVER, selectedId)));
    projects.populate();
    if (selectedId.isEmpty())
      selectedId =
          snapshot.projects().stream()
              .filter(p -> !p.completed())
              .findFirst()
              .or(() -> snapshot.projects().stream().findFirst())
              .map(AtlasNetwork.ProjectView::id)
              .orElse("");
    projects.children().stream()
        .filter(entry -> entry.project.id().equals(selectedId))
        .findFirst()
        .ifPresent(projects::setSelected);
    updateButtons();
  }

  private AtlasNetwork.ProjectView selected() {
    return projects == null || projects.getSelected() == null
        ? null
        : projects.getSelected().project;
  }

  private void updateButtons() {
    if (deliver == null) return;
    var selected = selected();
    deliver.active = !waiting && selected != null && selected.ready();
    advance.active = !waiting && snapshot.canAdvance();
    refresh.active = !waiting;
  }

  private void request(CampaignActions.Action action, String project) {
    if (waiting) return;
    waiting = true;
    requestedAt = System.currentTimeMillis();
    feedback = "entrelumen.atlas.submitting";
    if (selected() != null) details.populate(selected());
    updateButtons();
    PacketDistributor.sendToServer(new AtlasNetwork.Request(snapshot.campaign(), action, project));
  }

  @Override
  public void tick() {
    if (waiting && System.currentTimeMillis() - requestedAt > 10000) {
      waiting = false;
      feedback = "entrelumen.atlas.timeout";
      if (selected() != null) details.populate(selected());
      updateButtons();
    }
  }

  @Override
  public boolean isPauseScreen() {
    // Integrated singleplayer pauses world ticks; its task queue still handles deliveries.
    // Minecraft naturally keeps remote servers and published LAN worlds running.
    return true;
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    // Own pixel-art surface; never blur the world or blend panel edges.
    // Preserve the surrounding world, without blur. Opaque book pages carry all text.
    graphics.blit(BOOK, panelX, bookY, 0, 0, 300, 210, 300, 210);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    graphics.drawString(font, title, panelX + 24, bookY + 17, PAPER, false);
    graphics.drawString(font, Component.translatable("entrelumen.atlas.page_progress",
        snapshot.act(), completedCount, snapshot.projects().size()), panelX + 24, bookY + 28, MUTED, false);
    graphics.drawString(font, Component.translatable("entrelumen.atlas.page_details"),
        detailX + 4, bookY + 31, PAPER, false);
    graphics.drawString(font, Integer.toString(snapshot.act()), panelX + 143, bookY + 199, 0xFFDCCDA7, false);
  }

  /** Paper labels with a copper rule: native button focus, narration and activation. */
  private final class BookButton extends Button {
    BookButton(int x, int y, int width, Component label, OnPress action) {
      super(x, y, width, 14, label, action, DEFAULT_NARRATION);
    }
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      int color = !active ? 0xFF9E9278 : isHoveredOrFocused() ? COPPER : PAPER;
      if (isHoveredOrFocused()) graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFFD7C59B);
      graphics.fill(getX() + 2, getBottom() - 1, getRight() - 2, getBottom(), color);
      String label = getWidth() == 25 ? "x" : getMessage().getString();
      graphics.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
          getY() + 2, color, false);
      if (isHoveredOrFocused()) setTooltipForNextRenderPass(getMessage());
    }
  }

  private void renderPaperScrollbar(GuiGraphics graphics, AbstractSelectionList<?> list) {
    int x = list.getRight() - 6, top = list.getY(), h = list.getHeight();
    if (list.getMaxScroll() <= 0) return;
    // Cover the native gray scrollbar with the page's flat parchment color.
    graphics.fill(x, top, x + 6, top + h, 0xFFF1E3C1);
    int thumb = Math.max(8, h * h / (list.getMaxScroll() + h));
    int y = top + (int) (list.getScrollAmount() * (h - thumb) / list.getMaxScroll());
    graphics.fill(x + 2, top, x + 3, top + h, 0xFFD7C59B);
    graphics.fill(x + 1, y, x + 4, y + thumb, 0xFF9A7750);
  }

  private Component projectName(String id) {
    return Component.translatable("entrelumen.project." + id);
  }

  private String fitLabel(String text, int availableWidth) {
    if (font.width(text) <= availableWidth) return text;
    String suffix = "...";
    return font.plainSubstrByWidth(text, Math.max(0, availableWidth - font.width(suffix)))
        + suffix;
  }

  private Component status(AtlasNetwork.ProjectView project) {
    String key =
        project.completed()
            ? "complete"
            : project.ready()
                ? "ready"
                : project.prerequisites().stream().anyMatch(p -> !p.completed())
                    ? "blocked"
                    : "materials_missing";
    return Component.translatable("entrelumen.atlas." + key);
  }

  private final class ProjectList extends ObjectSelectionList<ProjectEntry> {
    ProjectList(int width, int height, int top) {
      super(AtlasScreen.this.minecraft, width, height, top, 36);
    }

    void populate() {
      clearEntries();
      snapshot.projects().forEach(project -> addEntry(new ProjectEntry(project)));
    }

    @Override
    public int getRowWidth() {
      return getWidth() - 12;
    }

    @Override
    protected int getScrollbarPosition() {
      return getRight() - 6;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(graphics, mouseX, mouseY, partialTick);
      renderPaperScrollbar(graphics, this);
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {}

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {}

    @Override
    protected void renderSelection(GuiGraphics graphics, int top, int width, int height,
        int outerColor, int innerColor) {
      graphics.fill(getX() + 2, top, getRight() - 8, top + height, 0xFFD8C79F);
      graphics.fill(getX() + 2, top, getX() + 4, top + height, COPPER);
    }

    @Override
    public void setSelected(ProjectEntry entry) {
      super.setSelected(entry);
      if (entry != null) {
        selectedId = entry.project.id();
        details.populate(entry.project);
        updateButtons();
      }
    }
  }

  private final class ProjectEntry extends ObjectSelectionList.Entry<ProjectEntry> {
    final AtlasNetwork.ProjectView project;
    final Component name, label;

    ProjectEntry(AtlasNetwork.ProjectView project) {
      this.project = project;
      name = projectName(project.id());
      label = status(project);
    }

    @Override
    public Component getNarration() {
      return name.copy().append(". ").append(label);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
      if (button == 0) {
        projects.setSelected(this);
        return true;
      }
      return false;
    }

    @Override
    public void render(
        GuiGraphics graphics,
        int index,
        int y,
        int x,
        int rowWidth,
        int rowHeight,
        int mouseX,
        int mouseY,
        boolean hovered,
        float partialTick) {
      graphics.drawString(
          font,
          fitLabel(name.getString(), rowWidth - 8),
          x + 3,
          y + 5,
          PAPER,
          false);
      graphics.drawString(
          font,
          fitLabel(label.getString(), rowWidth - 8),
          x + 3,
          y + 19,
          project.ready() ? COPPER : MUTED,
          false);
      if (hovered) setTooltipForNextRenderPass(name);
    }
  }

  private final class DetailList extends ObjectSelectionList<DetailEntry> {
    DetailList(int width, int height, int top) {
      super(AtlasScreen.this.minecraft, width, height, top, 12);
    }

    void populate(AtlasNetwork.ProjectView project) {
      clearEntries();
      setScrollAmount(0);
      addText(projectName(project.id()), PAPER);
      addText(status(project), COPPER);
      for (var material : project.materials()) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(material.item()));
        var nameLines = font.split(stack.getHoverName(), getRowWidth() - 25);
        // A native 16px icon spans two 12px rows. Keep every name line indented,
        // including an empty second row for short names, before drawing quantities.
        for (int i = 0; i < Math.max(2, nameLines.size()); i++)
          addEntry(new DetailEntry(i == 0 ? stack : ItemStack.EMPTY,
              i < nameLines.size() ? nameLines.get(i) : net.minecraft.util.FormattedCharSequence.EMPTY,
              stack.getHoverName(), PAPER, 23));
        addText(Component.translatable("entrelumen.atlas.amounts", material.available(),
            material.required(), Math.max(0, material.required() - material.available())),
            material.available() >= material.required() ? MUTED : COPPER);
      }
      if (!feedback.isEmpty()) addText(Component.translatable(feedback), MUTED);
      // Keep blockers ahead of historical prerequisites, without hiding either.
      for (boolean completed : new boolean[] {false, true}) {
        for (var prerequisite : project.prerequisites()) {
          if (prerequisite.completed() != completed) continue;
          addText(projectName(prerequisite.id()), PAPER);
          addText(Component.translatable(completed
              ? "entrelumen.atlas.prerequisite_complete" : "entrelumen.atlas.prerequisite_missing"), MUTED);
        }
      }
    }

    private void addText(Component text, int color) {
      for (var line : font.split(text, getRowWidth() - 6))
        addEntry(new DetailEntry(ItemStack.EMPTY, line, text, color));
    }

    @Override
    protected void renderSelection(GuiGraphics graphics, int top, int width, int height,
        int outerColor, int innerColor) {
      graphics.fill(getX() + 2, top, getRight() - 8, top + height, 0xFFE0D0AA);
    }

    @Override
    public int getRowWidth() {
      return getWidth() - 12;
    }

    @Override
    protected int getScrollbarPosition() {
      return getRight() - 6;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(graphics, mouseX, mouseY, partialTick);
      renderPaperScrollbar(graphics, this);
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {}

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {}
  }

  private final class DetailEntry extends ObjectSelectionList.Entry<DetailEntry> {
    final ItemStack item;
    final net.minecraft.util.FormattedCharSequence line;
    final Component narration;
    final int color;
    final int textOffset;
    DetailEntry(ItemStack item, Component text, int color) {
      this(item, net.minecraft.util.FormattedCharSequence.EMPTY, text, color);
    }
    DetailEntry(ItemStack item, net.minecraft.util.FormattedCharSequence line, Component narration, int color) {
      this(item, line, narration, color, item.isEmpty() ? 3 : 23);
    }
    DetailEntry(ItemStack item, net.minecraft.util.FormattedCharSequence line, Component narration,
        int color, int textOffset) {
      this.item = item; this.line = line; this.narration = narration; this.color = color;
      this.textOffset = textOffset;
    }
    @Override public Component getNarration() { return narration; }
    @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth,
        int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
      if (!item.isEmpty()) graphics.renderItem(item, x + 2, y);
      graphics.drawString(font, line, x + textOffset, y + 1, color, false);
      if (hovered) setTooltipForNextRenderPass(narration);
    }
  }
}
