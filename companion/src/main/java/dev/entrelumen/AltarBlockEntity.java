package dev.entrelumen;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * What every Ark altar shares: its type, an owner that claims are judged for, one fuel slot of the
 * type's tag, active time paid unit by unit from that slot, a centred square area, its entry in
 * the level's {@link AltarRegistry} while it is active, and an optional inventory of any size
 * (none for the Altar of Renewal, 27 for the Altar of Levelling's buffer, 4 for the Altar of
 * Repose). Subclasses decide what the altar does while active and how it is used; a pure area
 * effect only needs {@link #payActiveTick()} each tick and {@link #areaRadius()}.
 */
public abstract class AltarBlockEntity extends BlockEntity implements Container {
  protected final AltarType type;
  protected UUID owner;
  protected String ownerName = "";
  protected ItemStack fuel = ItemStack.EMPTY;
  /** Active ticks already paid for. */
  protected int activeTicks;
  /** Fuel units drawn since the current run started. */
  protected int fuelUsed;
  /** The optional inventory; empty for altars without one. */
  protected final NonNullList<ItemStack> contents;
  protected boolean contentsDropped;
  private boolean registered;
  private int registeredRadius = -1;
  private boolean actorReady;
  /** One-time cost of resolving the acting player, kept out of tick measurements. */
  long actorNanos = -1;

  private final IItemHandlerModifiable fuelHandler = new IItemHandlerModifiable() {
    @Override
    public int getSlots() {
      return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      return slot == 0 && active() ? fuel.copy() : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
      if (slot == 0 && active() && (stack.isEmpty() || isItemValid(0, stack))) {
        fuel = stack.copy();
        setChanged();
      }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      if (slot != 0 || !active() || !isItemValid(0, stack)) return stack;
      int moved = Math.min(acceptsFuel(stack), stack.getCount());
      if (moved <= 0) return stack;
      if (!simulate) addFuel(stack.copyWithCount(moved));
      ItemStack rest = stack.copy();
      rest.shrink(moved);
      return rest;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
      return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return slot == 0 && stack.is(type.fuel());
    }
  };

  protected AltarBlockEntity(BlockEntityType<?> entityType, BlockPos pos, BlockState state, AltarType type) {
    this(entityType, pos, state, type, 0);
  }

  protected AltarBlockEntity(BlockEntityType<?> entityType, BlockPos pos, BlockState state, AltarType type,
      int slots) {
    super(entityType, pos, state);
    this.type = type;
    this.contents = NonNullList.withSize(slots, ItemStack.EMPTY);
  }

  public AltarType type() {
    return type;
  }

  /** The fuel slot: insert-only for the type's tag, so pipes and hoppers can feed the altar. */
  public IItemHandlerModifiable fuelHandler() {
    return fuelHandler;
  }

  protected boolean active() {
    return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == this;
  }

  /** Half-width of the square the altar covers while active. */
  protected abstract int areaRadius();

  /** Whether the altar is active now: registered, and paying time. */
  protected abstract boolean working();

  public abstract void serverTick(ServerLevel world);

  /** Standing use with an empty main hand. */
  public abstract void use(ServerPlayer player);

  /** Crouched use with an empty main hand. */
  public abstract void crouchUse(ServerPlayer player);

  // ---- Owner ----------------------------------------------------------------------------------

  void setOwner(UUID id, String name) {
    owner = id;
    ownerName = name == null ? "" : name;
    setChanged();
    // While the owner is online their advancements and stats are cached, so the fake player that
    // will act for them later costs nothing now; created later from disk it can stall a tick.
    if (level instanceof ServerLevel world) {
      actor(world);
      if (owner != null) net.neoforged.neoforge.common.util.FakePlayerFactory.get(world,
          new com.mojang.authlib.GameProfile(owner, ownerName.isBlank() ? "[Entrelumen]" : ownerName));
      actorReady = true;
    }
  }

  protected void claimOwner(ServerPlayer player) {
    if (owner == null) setOwner(player.getUUID(), player.getGameProfile().getName());
  }

  /** The owner when online, else a fake player with the owner's profile; see {@link LandWorks#actor}. */
  protected Player actor(ServerLevel world) {
    return LandWorks.actor(world, owner, ownerName);
  }

  /**
   * Resolves the acting player once, in a tick of its own: a fake player for an offline owner loads
   * that owner's saved advancements and stats. Returns false on that tick.
   */
  protected boolean actorWarm(ServerLevel world) {
    if (actorReady) return true;
    long started = System.nanoTime();
    actor(world);
    if (owner != null) net.neoforged.neoforge.common.util.FakePlayerFactory.get(world,
        new com.mojang.authlib.GameProfile(owner, ownerName.isBlank() ? "[Entrelumen]" : ownerName));
    actorNanos = System.nanoTime() - started;
    actorReady = true;
    return false;
  }

  // ---- Fuel and active time ------------------------------------------------------------------

  public ItemStack fuel() {
    return fuel.copy();
  }

  public int activeTicks() {
    return activeTicks;
  }

  public int fuelUsed() {
    return fuelUsed;
  }

  /** Room for this stack: the type's fuel only, one kind, one stack. */
  int acceptsFuel(ItemStack stack) {
    if (!stack.is(type.fuel())) return 0;
    if (fuel.isEmpty()) return Math.min(64, stack.getMaxStackSize());
    if (!ItemStack.isSameItemSameComponents(fuel, stack)) return 0;
    return fuel.getMaxStackSize() - fuel.getCount();
  }

  void addFuel(ItemStack stack) {
    if (stack.isEmpty()) return;
    if (fuel.isEmpty()) fuel = stack.copy();
    else fuel.grow(stack.getCount());
    setChanged();
  }

  /** Main-hand fuel: moved into the slot. Subclasses may start work when fed. */
  public void feed(ServerPlayer player, ItemStack held) {
    int moved = Math.min(acceptsFuel(held), held.getCount());
    if (moved <= 0) {
      player.sendSystemMessage(Component.translatable("entrelumen.altar.fuel.full", fuel.getCount()));
      return;
    }
    addFuel(held.copyWithCount(moved));
    held.shrink(moved);
    player.getInventory().setChanged();
    claimOwner(player);
    player.sendSystemMessage(Component.translatable("entrelumen.altar.fuel.fed", moved, fuel.getCount(),
        type.ticksPerFuel() / 20));
  }

  /** Draws one unit: it pays {@link AltarType#ticksPerFuel()} active ticks. */
  protected boolean drawFuel() {
    if (fuel.isEmpty()) return false;
    fuel.shrink(1);
    if (fuel.isEmpty()) fuel = ItemStack.EMPTY;
    activeTicks += type.ticksPerFuel();
    fuelUsed++;
    onFuelDrawn();
    setChanged();
    return true;
  }

  /** What else one unit gives; the Renewal Altar adds work charge. */
  protected void onFuelDrawn() {}

  /** Pays one active tick, drawing a unit when the paid time is used up; false when none is left. */
  protected boolean payActiveTick() {
    if (activeTicks <= 0 && !drawFuel()) return false;
    activeTicks--;
    return true;
  }

  protected boolean fuelAvailable() {
    return activeTicks > 0 || !fuel.isEmpty();
  }

  // ---- Registry ------------------------------------------------------------------------------

  /** Keeps the level's registry in step with {@link #working()}; cheap, called every tick. */
  protected void refreshRegistration() {
    if (!(level instanceof ServerLevel world)) return;
    boolean wanted = working() && !isRemoved();
    int radius = areaRadius();
    if (wanted && (!registered || registeredRadius != radius)) {
      AltarRegistry.put(world, new AltarRegistry.Entry(type, worldPosition, radius));
      registeredRadius = radius;
    } else if (!wanted && registered) {
      AltarRegistry.remove(world, worldPosition);
    }
    registered = wanted;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    refreshRegistration();
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    if (level instanceof ServerLevel world && registered) AltarRegistry.remove(world, worldPosition);
    registered = false;
  }

  @Override
  public void onChunkUnloaded() {
    super.onChunkUnloaded();
    if (level instanceof ServerLevel world && registered) AltarRegistry.remove(world, worldPosition);
    registered = false;
  }

  // ---- Optional inventory ----------------------------------------------------------------------

  /** Called after the inventory changes through the container, a menu or a pipe. */
  protected void onContentsChanged() {}

  protected boolean contentsOpen() {
    return !isRemoved() && !contentsDropped;
  }

  @Override
  public int getContainerSize() {
    return contents.size();
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack stack : contents) if (!stack.isEmpty()) return false;
    return true;
  }

  @Override
  public ItemStack getItem(int slot) {
    return contents.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int amount) {
    if (!contentsOpen()) return ItemStack.EMPTY;
    ItemStack removed = ContainerHelper.removeItem(contents, slot, amount);
    if (!removed.isEmpty()) {
      setChanged();
      onContentsChanged();
    }
    return removed;
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    if (!contentsOpen()) return ItemStack.EMPTY;
    ItemStack removed = ContainerHelper.takeItem(contents, slot);
    if (!removed.isEmpty()) {
      setChanged();
      onContentsChanged();
    }
    return removed;
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    if (!contentsOpen()) return;
    ItemStack stored = stack.copy();
    stored.limitSize(getMaxStackSize(stored));
    contents.set(slot, stored);
    setChanged();
    onContentsChanged();
  }

  @Override
  public void clearContent() {
    if (!contentsOpen()) return;
    for (int slot = 0; slot < contents.size(); slot++) contents.set(slot, ItemStack.EMPTY);
    setChanged();
    onContentsChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return active() && !contentsDropped && !player.isSpectator() && Container.stillValidBlockEntity(this, player);
  }

  // ---- Drops and persistence -------------------------------------------------------------------

  /** Empties before spawning drops, so repeated removal callbacks cannot duplicate anything. */
  void dropContents(Level world, BlockPos pos) {
    ItemStack stack = fuel;
    fuel = ItemStack.EMPTY;
    if (!stack.isEmpty()) Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    if (!contentsDropped) {
      contentsDropped = true;
      for (int slot = 0; slot < contents.size(); slot++) {
        ItemStack held = contents.set(slot, ItemStack.EMPTY);
        if (!held.isEmpty()) Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), held);
      }
    }
    setChanged();
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    CompoundTag common = tag.getCompound("altar");
    fuel = common.contains("fuel", Tag.TAG_COMPOUND)
        ? ItemStack.parseOptional(registries, common.getCompound("fuel")) : ItemStack.EMPTY;
    owner = common.hasUUID("owner") ? common.getUUID("owner") : null;
    ownerName = common.getString("ownerName");
    activeTicks = Math.max(0, common.getInt("activeTicks"));
    fuelUsed = Math.max(0, common.getInt("fuelUsed"));
    for (int slot = 0; slot < contents.size(); slot++) contents.set(slot, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(common, contents, registries);
    contentsDropped = false;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    CompoundTag common = new CompoundTag();
    if (!fuel.isEmpty()) common.put("fuel", fuel.save(registries));
    if (owner != null) common.putUUID("owner", owner);
    common.putString("ownerName", ownerName);
    common.putInt("activeTicks", activeTicks);
    common.putInt("fuelUsed", fuelUsed);
    if (!contents.isEmpty()) ContainerHelper.saveAllItems(common, contents, registries);
    tag.put("altar", common);
  }
}
