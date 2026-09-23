package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** The module's real, persistent depot; never a virtual source of kit supplies. */
public final class LogisticsStock extends BlockEntity implements Container, MenuProvider {
  public static final int SLOTS = 27;

  private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
  private boolean dropped;
  private final IItemHandler itemHandler = new InvWrapper(this) {
    @Override
    public ItemStack getStackInSlot(int slot) {
      return active() ? super.getStackInSlot(slot).copy() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return active() ? super.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return active() ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
      if (active()) super.setStackInSlot(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return active() && super.isItemValid(slot, stack);
    }
  };

  public LogisticsStock(BlockPos pos, BlockState state) {
    super(Entrelumen.LOGISTICS_STOCK.get(), pos, state);
  }

  public IItemHandler itemHandler() {
    return itemHandler;
  }

  private boolean active() {
    Level level = getLevel();
    return !isRemoved() && !dropped && level != null && level.hasChunkAt(worldPosition)
        && level.getBlockEntity(worldPosition) == this
        && level.getBlockState(worldPosition).getBlock() instanceof LogisticsModuleBlock;
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(tag, items, registries);
    dropped = false;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    ContainerHelper.saveAllItems(tag, items, registries);
  }

  @Override
  public int getContainerSize() {
    return SLOTS;
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack stack : items) if (!stack.isEmpty()) return false;
    return true;
  }

  @Override
  public ItemStack getItem(int slot) {
    return items.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int amount) {
    if (isRemoved() || dropped) return ItemStack.EMPTY;
    ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
    if (!removed.isEmpty()) setChanged();
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    if (isRemoved() || dropped) return ItemStack.EMPTY;
    ItemStack removed = ContainerHelper.takeItem(items, slot);
    if (!removed.isEmpty()) setChanged();
    return removed;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    if (isRemoved() || dropped) return;
    ItemStack stored = stack.copy();
    stored.limitSize(getMaxStackSize(stored));
    items.set(slot, stored);
    setChanged();
  }

  @Override
  public void clearContent() {
    if (isRemoved() || dropped) return;
    for (int slot = 0; slot < SLOTS; slot++) items.set(slot, ItemStack.EMPTY);
    setChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return active() && !player.isSpectator()
        && Container.stillValidBlockEntity(this, player);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("entrelumen.logistics.stock");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
    return ChestMenu.threeRows(id, playerInventory, this);
  }

  /** Empty before spawning drops, so repeated removal callbacks cannot duplicate stock. */
  void dropAll(Level level, BlockPos pos) {
    if (dropped) return;
    dropped = true;
    for (int slot = 0; slot < SLOTS; slot++) {
      ItemStack stack = items.set(slot, ItemStack.EMPTY);
      if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
    }
    setChanged();
  }
}
