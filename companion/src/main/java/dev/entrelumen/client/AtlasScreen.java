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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native, keyboard-accessible list/detail Atlas. Only active-act data is sent by the server. */
public final class AtlasScreen extends Screen {
  private static final int PAPER = 0xFF382F25, MUTED = 0xFF77684E,
      COPPER = 0xFF8B4C2D;
  /** Pseudo entry at the top of the list: the Heliodor compass's current objective. */
  private static final String COMPASS_ID = "#compass";
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
    boolean samePage = updated.campaign().equals(snapshot.campaign()) && updated.act() == snapshot.act();
    String previousId = selectedId;
    double projectScroll = projects == null ? 0 : projects.scrollPosition();
    double detailScroll = details == null ? 0 : details.getScrollAmount();
    int detailSelection = details == null ? -1 : details.children().indexOf(details.getSelected());
    int focusedWidget = children().indexOf(getFocused());
    if (!samePage) selectedId = "";
    snapshot = updated;
    feedback = updated.message();
    waiting = false;
    rebuildWidgets();
    // Widget order is stable; retain the active page/button across server replies.
    if (focusedWidget >= 0 && focusedWidget < children().size())
      setInitialFocus(children().get(focusedWidget));
    // Native keyboard focus may scroll a list, so restore reading positions afterwards.
    if (samePage && selectedId.equals(previousId)) {
      projects.restoreScrollPosition(projectScroll);
      details.restorePosition(detailScroll, detailSelection);
    }
  }

  @Override
  protected void init() {
    completedCount =
        snapshot.projects().stream().filter(AtlasNetwork.ProjectView::completed).count();
    panelWidth = 300;
    panelX = (width - panelWidth) / 2;
    bookY = (height - 210) / 2;
    leftWidth = 110;
    contentTop = bookY + 42;
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
    if (!selectedId.equals(COMPASS_ID)
        && snapshot.projects().stream().noneMatch(project -> project.id().equals(selectedId)))
      selectedId =
          snapshot.projects().stream()
              .filter(p -> !p.completed())
              .findFirst()
              .or(() -> snapshot.projects().stream().findFirst())
              .map(AtlasNetwork.ProjectView::id)
              .orElse(COMPASS_ID);
    projects.children().stream()
        .filter(entry -> entry.id().equals(selectedId))
        .findFirst()
        .ifPresent(projects::setSelected);
    if (projects.getSelected() != null) projects.ensureVisible(projects.getSelected());
    updateButtons();
  }

  private void repopulateDetails() {
    if (selected() != null) details.populate(selected());
    else if (selectedId.equals(COMPASS_ID)) details.populateCompass();
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
    repopulateDetails();
    updateButtons();
    PacketDistributor.sendToServer(new AtlasNetwork.Request(snapshot.campaign(), action, project));
  }

  @Override
  public void tick() {
    if (waiting && System.currentTimeMillis() - requestedAt > 10000) {
      waiting = false;
      feedback = "entrelumen.atlas.timeout";
      repopulateDetails();
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

  private void renderPaperListFocus(GuiGraphics graphics, AbstractSelectionList<?> list) {
    if (list.isFocused()) {
      // Selection remains visible on both pages; only the focused page receives arrow keys.
      // Keep the focus rule outside the scissor so it cannot cross text or a 16px item icon.
      graphics.renderOutline(list.getX() - 2, list.getY() - 2,
          list.getWidth() + 4, list.getHeight() + 4, COPPER);
    }
  }

  private Component projectName(String id) {
    return Component.translatable("entrelumen.project." + id);
  }

  private int projectRowHeight(int width) {
    int textWidth = width - 14;
    int lines = Math.max(
        font.split(Component.translatable("entrelumen.atlas.compass"), textWidth).size()
            + font.split(compassStatus(), textWidth).size(),
        snapshot.projects().stream()
            .mapToInt(project -> font.split(projectName(project.id()), textWidth).size()
                + font.split(status(project), textWidth).size())
            .max().orElse(2));
    // Native lists use equal-height hit targets. Size them to the longest translated
    // entry, including its status, with 2px padding/gap and the native 4px row gap.
    return 10 + lines * font.lineHeight;
  }

  private Component compassStatus() {
    var compass = snapshot.compass();
    return compass.objective().isEmpty()
        ? Component.translatable("entrelumen.compass.state." + compass.state())
        : Component.translatable("entrelumen.compass.objective." + compass.objective());
  }

  private static Component dimensionName(String dimension) {
    return dimension.isEmpty()
        ? Component.empty()
        : Component.translatableWithFallback(
            "entrelumen.compass.dimension." + dimension.replace(':', '.'), dimension);
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
      super(AtlasScreen.this.minecraft, width, height, top, projectRowHeight(width));
    }

    void populate() {
      clearEntries();
      addEntry(new ProjectEntry(null));
      snapshot.projects().forEach(project -> addEntry(new ProjectEntry(project)));
    }

    @Override
    public int getRowWidth() {
      return getWidth() - 8;
    }

    @Override
    public int getRowLeft() {
      return getX() + 2;
    }

    double scrollPosition() {
      return getScrollAmount() / itemHeight;
    }

    void restoreScrollPosition(double position) {
      setScrollAmount(position * itemHeight);
    }

    @Override
    protected void ensureVisible(ProjectEntry entry) {
      int index = children().indexOf(entry);
      if (index < 0) return;
      int top = getRowTop(index);
      if (top < getY() + 4)
        setScrollAmount(getScrollAmount() + top - getY() - 4);
      else if (top + itemHeight > getBottom())
        setScrollAmount(getScrollAmount() + top + itemHeight - getBottom());
    }

    @Override
    protected int getScrollbarPosition() {
      return getRight() - 6;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(graphics, mouseX, mouseY, partialTick);
      renderPaperScrollbar(graphics, this);
      renderPaperListFocus(graphics, this);
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
        selectedId = entry.id();
        if (entry.project == null) details.populateCompass();
        else details.populate(entry.project);
        updateButtons();
      }
    }
  }

  private final class ProjectEntry extends ObjectSelectionList.Entry<ProjectEntry> {
    final AtlasNetwork.ProjectView project;
    final Component name, label;
    final List<FormattedCharSequence> nameLines, labelLines;

    /** A null project is the compass entry. */
    ProjectEntry(AtlasNetwork.ProjectView project) {
      this.project = project;
      name = project == null
          ? Component.translatable("entrelumen.atlas.compass") : projectName(project.id());
      label = project == null ? compassStatus() : status(project);
      nameLines = font.split(name, projects.getRowWidth() - 6);
      labelLines = font.split(label, projects.getRowWidth() - 6);
    }

    String id() {
      return project == null ? COMPASS_ID : project.id();
    }

    @Override
    public Component getNarration() {
      return name.copy().append(". ").append(label);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
      if (button == 0 && x < projects.getScrollbarPosition()) {
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
      int textY = y + 2;
      for (var line : nameLines) {
        graphics.drawString(font, line, x + 3, textY, PAPER, false);
        textY += font.lineHeight;
      }
      textY += 2;
      for (var line : labelLines) {
        boolean highlight = project == null
            ? snapshot.compass().state() == dev.entrelumen.CompassState.POINTING
            : project.ready();
        graphics.drawString(font, line, x + 3, textY, highlight ? COPPER : MUTED, false);
        textY += font.lineHeight;
      }
      if (hovered) setTooltipForNextRenderPass(getNarration());
    }
  }

  private final class DetailList extends ObjectSelectionList<DetailEntry> {
    private String projectId = "";

    DetailList(int width, int height, int top) {
      super(AtlasScreen.this.minecraft, width, height, top, font.lineHeight + 1);
    }

    void populate(AtlasNetwork.ProjectView project) {
      boolean sameProject = project.id().equals(projectId);
      boolean hadFocus = isFocused();
      double scroll = sameProject ? getScrollAmount() : 0;
      int selection = sameProject ? children().indexOf(getSelected()) : -1;
      // clearEntries does not clear the native focused-entry reference.
      setFocused(null);
      clearEntries();
      projectId = project.id();
      addText(projectName(project.id()), PAPER);
      addText(status(project), COPPER);
      for (var material : project.materials()) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(material.item()));
        var nameLines = font.split(stack.getHoverName(), getRowWidth() - 25);
        // Keep the full 16px icon, using only the rows needed by its height/name.
        int iconRows = (16 + itemHeight - 1) / itemHeight;
        for (int i = 0; i < Math.max(iconRows, nameLines.size()); i++)
          addEntry(new DetailEntry(i == 0 ? stack : ItemStack.EMPTY,
              i < nameLines.size() ? nameLines.get(i) : FormattedCharSequence.EMPTY,
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
      restorePosition(scroll, selection, hadFocus);
    }

    /** Objective, where it lies, why it matters and the lore fragments recovered so far. */
    void populateCompass() {
      boolean same = COMPASS_ID.equals(projectId);
      boolean hadFocus = isFocused();
      double scroll = same ? getScrollAmount() : 0;
      int selection = same ? children().indexOf(getSelected()) : -1;
      setFocused(null);
      clearEntries();
      projectId = COMPASS_ID;
      var compass = snapshot.compass();
      if (compass.objective().isEmpty()) {
        addText(Component.translatable("entrelumen.atlas.compass"), PAPER);
      } else {
        String objective = "entrelumen.compass.objective." + compass.objective();
        addText(Component.translatable(objective), PAPER);
        addText(Component.translatable("entrelumen.atlas.compass.where",
            Component.translatable("entrelumen.compass.kind." + compass.kind()),
            dimensionName(compass.dimension())), COPPER);
        addGap();
        addText(Component.translatable(objective + ".why"), PAPER);
      }
      addGap();
      addText(Component.translatable("entrelumen.compass.state." + compass.state()), MUTED);
      if (!compass.lore().isEmpty()) {
        addGap();
        addText(Component.translatable("entrelumen.atlas.compass.lore"), COPPER);
        // Newest fragment first: it answers what the team just did.
        for (int i = compass.lore().size() - 1; i >= 0; i--) {
          addText(Component.translatable("entrelumen.compass.lore." + compass.lore().get(i)), PAPER);
          addGap();
        }
      }
      if (!feedback.isEmpty()) addText(Component.translatable(feedback), MUTED);
      restorePosition(scroll, selection, hadFocus);
    }

    private void addGap() {
      addEntry(new DetailEntry(ItemStack.EMPTY, FormattedCharSequence.EMPTY, Component.empty(), MUTED));
    }

    void restorePosition(double scroll, int selection) {
      restorePosition(scroll, selection, isFocused());
    }

    private void restorePosition(double scroll, int selection, boolean hadFocus) {
      if (!children().isEmpty() && (selection >= 0 || hadFocus)) {
        int index = Math.max(0, Math.min(selection, children().size() - 1));
        var entry = children().get(index);
        setSelected(entry);
        if (hadFocus) setFocused(entry);
      }
      setScrollAmount(scroll);
    }

    private void addText(Component text, int color) {
      for (var line : font.split(text, getRowWidth() - 6))
        addEntry(new DetailEntry(ItemStack.EMPTY, line, text, color));
    }

    @Override
    protected void renderSelection(GuiGraphics graphics, int top, int width, int height,
        int outerColor, int innerColor) {
      // A continuation line must not paint over the lower half of a 16px icon.
      int left = getSelected() != null && getSelected().textOffset > 3
          ? getRowLeft() + getSelected().textOffset - 1 : getX() + 2;
      graphics.fill(left, top, getRight() - 8, top + height + 4, 0xFFE0D0AA);
    }

    @Override
    public int getRowWidth() {
      return getWidth() - 8;
    }

    @Override
    public int getRowLeft() {
      return getX() + 2;
    }

    @Override
    protected int getRowBottom(int index) {
      // Keep an icon's visible lower pixels when its first text row scrolls out.
      return super.getRowBottom(index)
          + (getEntry(index).item.isEmpty() ? 0 : Math.max(0, 16 - itemHeight));
    }

    @Override
    protected int getScrollbarPosition() {
      return getRight() - 6;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(graphics, mouseX, mouseY, partialTick);
      renderPaperScrollbar(graphics, this);
      renderPaperListFocus(graphics, this);
    }

    @Override
    protected void renderListBackground(GuiGraphics graphics) {}

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {}
  }

  private final class DetailEntry extends ObjectSelectionList.Entry<DetailEntry> {
    final ItemStack item;
    final FormattedCharSequence line;
    final Component narration;
    final int color;
    final int textOffset;
    DetailEntry(ItemStack item, FormattedCharSequence line, Component narration, int color) {
      this(item, line, narration, color, item.isEmpty() ? 3 : 23);
    }
    DetailEntry(ItemStack item, FormattedCharSequence line, Component narration,
        int color, int textOffset) {
      this.item = item; this.line = line; this.narration = narration; this.color = color;
      this.textOffset = textOffset;
    }
    @Override public Component getNarration() { return narration; }
    @Override public boolean mouseClicked(double x, double y, int button) {
      return button == 0 && x < details.getScrollbarPosition();
    }
    @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth,
        int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
      if (!item.isEmpty()) graphics.renderItem(item, x + 2, y);
      graphics.drawString(font, line, x + textOffset, y + 1, color, false);
      if (hovered) setTooltipForNextRenderPass(narration);
    }
  }
}
