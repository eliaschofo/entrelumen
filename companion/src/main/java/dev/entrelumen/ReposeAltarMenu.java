package dev.entrelumen;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Altar of Repose's coffer: four slots in a 2×2 grid centred on the panel, as vanilla's
 * dispenser centres its 3×3 grid, above the player's inventory. Only damageable, repairable items
 * fit, one per slot. The data slots carry fuel, time left, state and round progress to the screen.
 */
public final class ReposeAltarMenu extends AbstractContainerMenu {
  public static final int WIDTH = 176, HEIGHT = 166;
  /** Top-left item position of each slot: symmetric about the panel's centre line x = 88. */
  public static final int[][] SLOT_POSITIONS = {{71, 30}, {89, 30}, {71, 48}, {89, 48}};
  private final Container coffer;
  public final ContainerData data;

  /** Client side: an empty mirror filled by the server's slot and data updates. */
  public ReposeAltarMenu(int id, Inventory inventory) {
    this(id, inventory, new SimpleContainer(ReposeAltarEntity.SLOTS), new SimpleContainerData(4));
  }

  public ReposeAltarMenu(int id, Inventory inventory, Container coffer, ContainerData data) {
    super(Altars.REPOSE_MENU.get(), id);
    checkContainerSize(coffer, ReposeAltarEntity.SLOTS);
    checkContainerDataCount(data, 4);
    this.coffer = coffer;
    this.data = data;
    coffer.startOpen(inventory.player);
    for (int slot = 0; slot < ReposeAltarEntity.SLOTS; slot++)
      addSlot(new Slot(coffer, slot, SLOT_POSITIONS[slot][0], SLOT_POSITIONS[slot][1]) {
        @Override
        public boolean mayPlace(ItemStack stack) {
          return ReposeAltarEntity.repairable(stack);
        }

        @Override
        public int getMaxStackSize() {
          return 1;
        }
      });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    addDataSlots(data);
  }

  public int fuel() {
    return data.get(0);
  }

  public int secondsLeft() {
    return data.get(1);
  }

  /** 0: nothing to repair, 1: repairing, 2: waiting for fuel. */
  public int state() {
    return data.get(2);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    int coffer = ReposeAltarEntity.SLOTS;
    if (index < coffer) {
      if (!moveItemStackTo(stack, coffer, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!ReposeAltarEntity.repairable(stack) || !moveItemStackTo(stack, 0, coffer, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    return original;
  }

  @Override
  public boolean stillValid(Player player) {
    return coffer.stillValid(player);
  }

  @Override
  public void removed(Player player) {
    super.removed(player);
    coffer.stopOpen(player);
  }
}
