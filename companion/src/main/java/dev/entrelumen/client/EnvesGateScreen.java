package dev.entrelumen.client;

import dev.entrelumen.ApotheosisTiers;
import dev.entrelumen.EnvesEntrance;
import dev.entrelumen.EnvesNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The gate's screen, kept minimal: without an attempt, the offering and one button per difficulty
 * the player may pick (every World Tier up to theirs); with one, enter or give it up. The server
 * judges every choice again.
 */
public final class EnvesGateScreen extends Screen {
  private static final int TEXT = 0xFFF1E3C1, MUTED = 0xFFB9A98A;
  private final EnvesNetwork.Gate gate;

  EnvesGateScreen(EnvesNetwork.Gate gate) {
    super(Component.translatable("entrelumen.enves.gate.title"));
    this.gate = gate;
  }

  static void receive(EnvesNetwork.Gate gate) {
    Minecraft.getInstance().setScreen(new EnvesGateScreen(gate));
  }

  private EnvesEntrance.GateState state() {
    var states = EnvesEntrance.GateState.values();
    return gate.state() >= 0 && gate.state() < states.length ? states[gate.state()] : EnvesEntrance.GateState.READY;
  }

  @Override
  protected void init() {
    int x = width / 2 - 100, y = height / 2 - 10;
    switch (state()) {
      case READY -> {
        var tiers = ApotheosisTiers.Tier.values();
        for (int ordinal : gate.tiers()) {
          if (ordinal < 0 || ordinal >= tiers.length) continue;
          var button = Button.builder(Component.translatable(tiers[ordinal].nameKey), b -> send(EnvesNetwork.Action.OPEN, ordinal))
              .bounds(x, y, 200, 20).build();
          button.active = gate.hasOffering();
          addRenderableWidget(button);
          y += 22;
        }
      }
      case OPEN -> {
        addRenderableWidget(Button.builder(Component.translatable("entrelumen.enves.gate.enter"),
            b -> send(EnvesNetwork.Action.ENTER, 0)).bounds(x, y, 200, 20).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.translatable("entrelumen.enves.gate.give_up"),
            b -> send(EnvesNetwork.Action.GIVE_UP, 0)).bounds(x, y, 200, 20).build());
        y += 22;
      }
      case FORMING -> {}
    }
    addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, y + 6, 200, 20).build());
  }

  private void send(EnvesNetwork.Action action, int tier) {
    PacketDistributor.sendToServer(new EnvesNetwork.GateAction(action, tier));
    onClose();
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    int cx = width / 2, y = height / 2 - 70;
    graphics.drawCenteredString(font, title, cx, y, TEXT);
    y += 16;
    switch (state()) {
      case READY -> {
        var item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(gate.offeringItem())), gate.offeringCount());
        Component offering = Component.translatable("entrelumen.enves.gate.offering", gate.offeringCount(), item.getHoverName());
        int w = font.width(offering) + 20;
        graphics.renderItem(item, cx - w / 2, y - 4);
        graphics.drawString(font, offering, cx - w / 2 + 20, y, gate.hasOffering() ? TEXT : 0xFFE08070, true);
        y += 18;
        graphics.drawCenteredString(font, Component.translatable(gate.hasOffering()
            ? "entrelumen.enves.gate.pick" : "entrelumen.enves.gate.missing"), cx, y, MUTED);
      }
      case OPEN -> {
        var tiers = ApotheosisTiers.Tier.values();
        Component tier = gate.attemptTier() >= 0 && gate.attemptTier() < tiers.length
            ? Component.translatable(tiers[gate.attemptTier()].nameKey) : Component.empty();
        graphics.drawCenteredString(font, Component.translatable("entrelumen.enves.gate.open", tier,
            Component.translatable("entrelumen.enves.floor." + gate.frontline()), gate.poolLeft(), gate.poolTotal()), cx, y, TEXT);
      }
      case FORMING -> graphics.drawCenteredString(font, Component.translatable("entrelumen.enves.forming"), cx, y, TEXT);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
