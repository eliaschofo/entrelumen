package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * The Altar of Repose: a four-slot coffer that mends what it holds very slowly, like Mending
 * without experience orbs. While it has Bottles o' Enchanting and holds a worn item, every paid
 * tick advances a five-second round; each round restores one durability point to every worn item
 * inside. Only damageable, repairable items fit. Time is paid only while something is being
 * repaired.
 *
 * <p>Pipes and hoppers see the fuel slot (insert only) and the four slots: they may insert only
 * worn items, which need the altar, and extract only whole ones, so a pipe line through it is a
 * repair line that never pulls a tool out half-mended and never feeds whole tools back in.
 */
public final class ReposeAltarEntity extends AltarBlockEntity implements MenuProvider {
  public static final int SLOTS = AltarEffectRules.REPOSE_SLOTS;
  static final int VERSION = 1;
  /** Paid ticks toward the next repair round. */
  private int progress;
  /** Durability points restored since the altar was placed, for status and tests. */
  long repaired;

  /** What the menu shows: fuel count, seconds of repair left, state and round progress. */
  final ContainerData data = new ContainerData() {
    @Override
    public int get(int index) {
      return switch (index) {
        case 0 -> fuel.getCount();
        case 1 -> (int) Math.min(Short.MAX_VALUE, secondsLeft());
        case 2 -> state();
        case 3 -> progress;
        default -> 0;
      };
    }

    @Override
    public void set(int index, int value) {}

    @Override
    public int getCount() {
      return 4;
    }
  };

  private final IItemHandlerModifiable slots = new IItemHandlerModifiable() {
    @Override
    public int getSlots() {
      return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      return open() ? contents.get(slot).copy() : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
      if (open() && (stack.isEmpty() || repairable(stack))) setItem(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      if (!open() || stack.isEmpty() || !worn(stack) || !contents.get(slot).isEmpty()) return stack;
      if (!simulate) setItem(slot, stack.copyWithCount(1));
      ItemStack rest = stack.copy();
      rest.shrink(1);
      return rest;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      ItemStack held = open() ? contents.get(slot) : ItemStack.EMPTY;
      if (amount <= 0 || held.isEmpty() || held.isDamaged()) return ItemStack.EMPTY;
      return simulate ? held.copy() : removeItem(slot, 1);
    }

    @Override
    public int getSlotLimit(int slot) {
      return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return worn(stack);
    }
  };

  private final IItemHandler exposed;

  public ReposeAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.REPOSE_ENTITY.get(), pos, state, AltarType.REPOSE, SLOTS);
    exposed = new CombinedInvWrapper(fuelHandler(), slots);
  }

  /** What pipes and hoppers see: the fuel slot first, then the four repair slots. */
  public IItemHandler itemHandler(Direction side) {
    return exposed;
  }

  private boolean open() {
    return active() && !contentsDropped;
  }

  /** Damageable and repairable: what the coffer accepts. */
  static boolean repairable(ItemStack stack) {
    return !stack.isEmpty() && stack.isDamageableItem() && stack.isRepairable();
  }

  /** Repairable and damaged: what the coffer works on. */
  static boolean worn(ItemStack stack) {
    return repairable(stack) && stack.isDamaged();
  }

  boolean anyWorn() {
    for (ItemStack stack : contents) if (worn(stack)) return true;
    return false;
  }

  int progress() {
    return progress;
  }

  /** 0: nothing to repair, 1: repairing, 2: waiting for fuel. */
  int state() {
    if (!anyWorn()) return 0;
    return fuelAvailable() ? 1 : 2;
  }

  long secondsLeft() {
    return (activeTicks + (long) fuel.getCount() * type.ticksPerFuel()) / 20;
  }

  @Override
  protected int areaRadius() {
    return 0;
  }

  @Override
  protected boolean working() {
    return fuelAvailable() && anyWorn();
  }

  @Override
  public boolean canPlaceItem(int slot, ItemStack stack) {
    return repairable(stack);
  }

  @Override
  public int getMaxStackSize() {
    return 1;
  }

  @Override
  public void serverTick(ServerLevel world) {
    try {
      if (!anyWorn() || !payActiveTick()) return;
      if (++progress < AltarEffectRules.REPAIR_INTERVAL) return;
      progress = 0;
      boolean changed = false;
      for (int slot = 0; slot < SLOTS; slot++) {
        ItemStack stack = contents.get(slot);
        if (!worn(stack)) continue;
        stack.setDamageValue(stack.getDamageValue() - 1);
        repaired++;
        changed = true;
      }
      if (changed) setChanged();
    } finally {
      refreshRegistration();
    }
  }

  // ---- Gestures -------------------------------------------------------------------------------

  /** Empty hand or an item that is not fuel: open the coffer. */
  @Override
  public void use(ServerPlayer player) {
    player.openMenu(this);
  }

  /** Crouched empty hand: status. */
  @Override
  public void crouchUse(ServerPlayer player) {
    for (Component line : statusLines()) player.sendSystemMessage(line);
  }

  List<Component> statusLines() {
    List<Component> lines = new ArrayList<>();
    int worn = 0, damage = 0;
    for (ItemStack stack : contents)
      if (worn(stack)) {
        worn++;
        damage = Math.max(damage, stack.getDamageValue());
      }
    long seconds = secondsLeft();
    lines.add(Component.translatable("entrelumen.altar.repose.status", worn, SLOTS,
        AltarEffectRules.ticksToRepair(damage) / 20 / 60, fuel.getCount(),
        String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)));
    if (worn > 0 && !fuelAvailable()) lines.add(Component.translatable("entrelumen.altar.repose.no_fuel"));
    return lines;
  }

  // ---- Menu -----------------------------------------------------------------------------------

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.entrelumen.repose_altar");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new ReposeAltarMenu(id, inventory, this, data);
  }

  // ---- Persistence ----------------------------------------------------------------------------

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    CompoundTag own = tag.getCompound("repose");
    progress = Math.clamp(own.getInt("progress"), 0, AltarEffectRules.REPAIR_INTERVAL - 1);
    repaired = Math.max(0, own.getLong("repaired"));
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    CompoundTag own = new CompoundTag();
    own.putInt("version", VERSION);
    own.putInt("progress", progress);
    own.putLong("repaired", repaired);
    tag.put("repose", own);
  }
}
