package dev.entrelumen.client;

import dev.entrelumen.ApotheosisTiers;
import dev.entrelumen.EnvesEntrance;
import dev.entrelumen.EnvesNetwork;
import dev.entrelumen.EnvesRules;
import java.util.ArrayList;
import java.util.List;
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
 * The gate's screen, kept minimal: without an attempt, the offering's alternatives (a Nether star or
 * sour light shards, Elias 27/9) to pick one from, and one button per difficulty the player may pick
 * (every World Tier up to theirs); with one, enter or give it up. Giving up sits below Cancel and
 * asks twice: the first click only turns it into "Confirm give up". The server judges every choice again.
 */
public final class EnvesGateScreen extends Screen {
  private static final int TEXT = 0xFFF1E3C1, MUTED = 0xFFB9A98A, MISSING = 0xFFE08070;
  private static final int OFFER_W = 96, OFFER_H = 22;
  private final EnvesNetwork.Gate gate;
  /** The offer the payer picked; starts on the first one they carry. */
  private int offer;
  private final List<Button> tierButtons = new ArrayList<>();
  /** The give-up button was clicked once (the server armed it too): the next click confirms it. */
  private boolean giveUpArmed;
  /** Screen ticks since the first click; the arm lapses with the server's window. */
  private int giveUpArmedTicks;
  private Button giveUpButton;

  EnvesGateScreen(EnvesNetwork.Gate gate) {
    super(Component.translatable("entrelumen.enves.gate.title"));
    this.gate = gate;
    offer = 0;
    for (int i = 0; i < gate.offers().size(); i++)
      if (gate.offers().get(i).affordable()) {
        offer = i;
        break;
      }
  }

  static void receive(EnvesNetwork.Gate gate) {
    Minecraft.getInstance().setScreen(new EnvesGateScreen(gate));
  }

  private EnvesEntrance.GateState state() {
    var states = EnvesEntrance.GateState.values();
    return gate.state() >= 0 && gate.state() < states.length ? states[gate.state()] : EnvesEntrance.GateState.READY;
  }

  private boolean payable() {
    return offer >= 0 && offer < gate.offers().size() && gate.offers().get(offer).affordable();
  }

  private int offersLeft() {
    int n = gate.offers().size();
    return width / 2 - (n * OFFER_W + (n - 1) * 4) / 2;
  }

  @Override
  protected void init() {
    tierButtons.clear();
    int x = width / 2 - 100, y = height / 2 - 10;
    switch (state()) {
      case READY -> {
        int ox = offersLeft(), oy = height / 2 - 44;
        for (int i = 0; i < gate.offers().size(); i++) {
          int index = i;
          addRenderableWidget(Button.builder(Component.empty(), b -> {
            offer = index;
            for (Button tier : tierButtons) tier.active = payable();
          }).bounds(ox + i * (OFFER_W + 4), oy, OFFER_W, OFFER_H).build());
        }
        var tiers = ApotheosisTiers.Tier.values();
        for (int ordinal : gate.tiers()) {
          if (ordinal < 0 || ordinal >= tiers.length) continue;
          var button = Button.builder(Component.translatable(tiers[ordinal].nameKey),
              b -> send(EnvesNetwork.Action.OPEN, ordinal, offer)).bounds(x, y, 200, 20).build();
          button.active = payable();
          tierButtons.add(addRenderableWidget(button));
          y += 22;
        }
      }
      case OPEN -> {
        addRenderableWidget(Button.builder(Component.translatable("entrelumen.enves.gate.enter"),
            b -> send(EnvesNetwork.Action.ENTER, 0, -1)).bounds(x, y, 200, 20).build());
        y += 22;
      }
      case FORMING -> {}
    }
    addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x, y + 6, 200, 20).build());
    if (state() == EnvesEntrance.GateState.OPEN) {
      // Far from Enter, under Cancel, and two clicks: ending the run for everyone is never a slip.
      // The server keeps the two steps too: the first click arms it there, and only a second click within
      // its window ends the run.
      giveUpButton = addRenderableWidget(Button.builder(giveUpLabel(), b -> {
        if (!giveUpArmed) {
          giveUpArmed = true;
          giveUpArmedTicks = 0;
          b.setMessage(giveUpLabel());
          PacketDistributor.sendToServer(new EnvesNetwork.GateAction(EnvesNetwork.Action.ARM_GIVE_UP, 0, -1));
          return;
        }
        send(EnvesNetwork.Action.GIVE_UP, 0, -1);
      }).bounds(x, y + 6 + 20 + 16, 200, 20).build());
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (giveUpArmed && ++giveUpArmedTicks > EnvesRules.GIVE_UP_WINDOW) {
      giveUpArmed = false;
      if (giveUpButton != null) giveUpButton.setMessage(giveUpLabel());
    }
  }

  private Component giveUpLabel() {
    return Component.translatable(giveUpArmed ? "entrelumen.enves.giveup.confirm_button" : "entrelumen.enves.gate.give_up");
  }

  private void send(EnvesNetwork.Action action, int tier, int chosen) {
    PacketDistributor.sendToServer(new EnvesNetwork.GateAction(action, tier, chosen));
    onClose();
  }

  private static ItemStack stack(EnvesNetwork.Offer offer) {
    return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.item())), offer.count());
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    int cx = width / 2, y = height / 2 - 76;
    graphics.drawCenteredString(font, title, cx, y, TEXT);
    y += 16;
    switch (state()) {
      case READY -> {
        graphics.drawCenteredString(font, Component.translatable(gate.offers().size() > 1
            ? "entrelumen.enves.gate.offering_pick" : "entrelumen.enves.gate.offering_one"), cx, y, MUTED);
        int ox = offersLeft(), oy = height / 2 - 44;
        for (int i = 0; i < gate.offers().size(); i++) {
          var offer = gate.offers().get(i);
          int bx = ox + i * (OFFER_W + 4);
          ItemStack stack = stack(offer);
          graphics.renderItem(stack, bx + 4, oy + 3);
          graphics.renderItemDecorations(font, stack, bx + 4, oy + 3);
          Component name = stack.getHoverName();
          String label = font.plainSubstrByWidth(name.getString(), OFFER_W - 26);
          graphics.drawString(font, label, bx + 23, oy + 7, offer.affordable() ? TEXT : MISSING, true);
          if (i == this.offer) graphics.renderOutline(bx - 1, oy - 1, OFFER_W + 2, OFFER_H + 2, 0xFFF6D77A);
        }
        graphics.drawCenteredString(font, Component.translatable(payable()
            ? "entrelumen.enves.gate.pick" : "entrelumen.enves.gate.missing"), cx, height / 2 - 20, MUTED);
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
