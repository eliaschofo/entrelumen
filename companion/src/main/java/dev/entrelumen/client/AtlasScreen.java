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
  private static final int PAPER = 0xFFF1E7D1,
      MUTED = 0xFFB7B6A6,
      COPPER = 0xFFD7A36B,
      SURFACE = 0xF218302F;
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
    panelWidth = Math.min(700, width - 24);
    panelX = (width - panelWidth) / 2;
    leftWidth = Math.max(100, panelWidth / 3);
    contentTop = 64;
    contentBottom = Math.max(contentTop + 45, height - 76);
    detailX = panelX + leftWidth + 12;
    detailWidth = panelWidth - leftWidth - 12;
    projects = new ProjectList(leftWidth, contentBottom - contentTop, contentTop);
    projects.setX(panelX);
    details =
        new DetailList(detailWidth, Math.max(18, contentBottom - contentTop - 32), contentTop + 32);
    details.setX(detailX);
    addRenderableWidget(projects);
    addRenderableWidget(details);
    int buttonWidth = (panelWidth - 18) / 4, buttonY = height - 28;
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(panelX, buttonY, buttonWidth, 20)
            .build());
    refresh =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("entrelumen.atlas.refresh"),
                    button -> request(CampaignActions.Action.REFRESH, ""))
                .bounds(panelX + buttonWidth + 6, buttonY, buttonWidth, 20)
                .build());
    deliver =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("entrelumen.atlas.deliver"),
                    button -> request(CampaignActions.Action.DELIVER, selectedId))
                .bounds(panelX + (buttonWidth + 6) * 2, buttonY, buttonWidth, 20)
                .build());
    advance =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("entrelumen.atlas.advance"),
                    button -> request(CampaignActions.Action.ADVANCE, ""))
                .bounds(panelX + (buttonWidth + 6) * 3, buttonY, buttonWidth, 20)
                .build());
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
    updateButtons();
    PacketDistributor.sendToServer(new AtlasNetwork.Request(snapshot.campaign(), action, project));
  }

  @Override
  public void tick() {
    if (waiting && System.currentTimeMillis() - requestedAt > 10000) {
      waiting = false;
      feedback = "entrelumen.atlas.timeout";
      updateButtons();
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderTransparentBackground(graphics);
    graphics.fill(panelX - 8, 16, panelX + panelWidth + 8, height - 8, SURFACE);
    graphics.drawString(font, title, panelX, 25, PAPER, false);
    graphics.drawString(
        font,
        Component.translatable(
            "entrelumen.atlas.summary", snapshot.act(), completedCount, snapshot.projects().size()),
        panelX,
        41,
        MUTED,
        false);
    graphics.fill(detailX - 7, contentTop, detailX - 6, contentBottom, 0xFF405653);
    var selected = selected();
    if (selected != null) {
      Component name = projectName(selected.id());
      graphics.drawString(
          font,
          font.plainSubstrByWidth(name.getString(), detailWidth - 8),
          detailX,
          contentTop,
          PAPER,
          false);
      graphics.drawString(
          font,
          status(selected),
          detailX,
          contentTop + 14,
          selected.completed() ? MUTED : COPPER,
          false);
      if (mouseX >= detailX
          && mouseX < detailX + detailWidth
          && mouseY >= contentTop
          && mouseY < contentTop + 12) setTooltipForNextRenderPass(name);
    }
    if (!feedback.isEmpty())
      graphics.drawWordWrap(
          font,
          Component.translatable(feedback),
          panelX,
          height - 61,
          panelWidth,
          feedback.equals("entrelumen.atlas.stale") ? COPPER : MUTED);
    super.render(graphics, mouseX, mouseY, partialTick);
  }

  private Component projectName(String id) {
    return Component.translatable("entrelumen.project." + id);
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
    protected void renderListBackground(GuiGraphics graphics) {}

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {}

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
          font.plainSubstrByWidth(name.getString(), rowWidth - 8),
          x + 3,
          y + 5,
          PAPER,
          false);
      graphics.drawString(
          font,
          font.plainSubstrByWidth(label.getString(), rowWidth - 8),
          x + 3,
          y + 19,
          project.ready() ? COPPER : MUTED,
          false);
      if (hovered) setTooltipForNextRenderPass(name);
    }
  }

  private final class DetailList extends ObjectSelectionList<DetailEntry> {
    DetailList(int width, int height, int top) {
      super(AtlasScreen.this.minecraft, width, height, top, 36);
    }

    void populate(AtlasNetwork.ProjectView project) {
      clearEntries();
      setScrollAmount(0);
      for (var prerequisite : project.prerequisites())
        addEntry(
            new DetailEntry(
                ItemStack.EMPTY,
                projectName(prerequisite.id()),
                Component.translatable(
                    prerequisite.completed()
                        ? "entrelumen.atlas.prerequisite_complete"
                        : "entrelumen.atlas.prerequisite_missing"),
                prerequisite.completed()));
      for (var material : project.materials()) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(material.item()));
        addEntry(
            new DetailEntry(
                stack,
                stack.getHoverName(),
                Component.translatable(
                    "entrelumen.atlas.amounts",
                    material.available(),
                    material.required(),
                    Math.max(0, material.required() - material.available())),
                material.available() >= material.required()));
      }
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
    protected void renderListBackground(GuiGraphics graphics) {}

    @Override
    protected void renderListSeparators(GuiGraphics graphics) {}
  }

  private final class DetailEntry extends ObjectSelectionList.Entry<DetailEntry> {
    final ItemStack item;
    final Component name, description;
    final boolean fulfilled;

    DetailEntry(ItemStack item, Component name, Component description, boolean fulfilled) {
      this.item = item;
      this.name = name;
      this.description = description;
      this.fulfilled = fulfilled;
    }

    @Override
    public Component getNarration() {
      return name.copy().append(". ").append(description);
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
      int textX = x + 3;
      if (!item.isEmpty()) {
        graphics.renderItem(item, x + 2, y + 4);
        textX = x + 23;
      }
      graphics.drawString(
          font,
          font.plainSubstrByWidth(name.getString(), rowWidth - (textX - x) - 4),
          textX,
          y + 3,
          PAPER,
          false);
      graphics.drawString(
          font,
          font.plainSubstrByWidth(description.getString(), rowWidth - 6),
          x + 3,
          y + 18,
          fulfilled ? MUTED : COPPER,
          false);
      if (hovered) setTooltipForNextRenderPass(name.copy().append("\n").append(description));
    }
  }
}
