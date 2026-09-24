package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Deposit slot, output slot and the player inventory. Every withdrawal arrives as a vanilla
 * container button click and is validated by the block entity against the live pool and Eterna.
 */
public final class AtlasLibraryMenu extends AbstractContainerMenu {
  public static final int DEPOSIT = 0, OUTPUT = 1, LEVEL_BITS = 64;
  public static final int WIDTH = 256, HEIGHT = 222;
  public final BlockPos pos;
  private final Level level;
  private final Player player;
  private final SimpleContainer io = new SimpleContainer(2);
  private boolean dirty = true;
  private long sentPool = -1;
  /** Client copy of the last server snapshot; null until the first one arrives. */
  public AtlasLibraryNetwork.Snapshot snapshot;
  public Runnable onSnapshot = () -> {};

  public AtlasLibraryMenu(int id, Inventory inventory, BlockPos pos) {
    super(ApotheosisContent.ATLAS_LIBRARY_MENU.get(), id);
    this.pos = pos;
    this.level = inventory.player.level();
    this.player = inventory.player;
    addSlot(new Slot(io, DEPOSIT, 180, 40) {
      @Override
      public boolean mayPlace(ItemStack stack) {
        return AtlasLibraryBlockEntity.bookValue(stack) > 0;
      }

      @Override
      public int getMaxStackSize() {
        return 1;
      }

      @Override
      public void setChanged() {
        super.setChanged();
        ItemStack book = getItem();
        if (!level.isClientSide && !book.isEmpty() && library() != null && library().deposit(book)) {
          io.setItem(DEPOSIT, ItemStack.EMPTY);
          dirty = true;
        }
      }
    });
    addSlot(new Slot(io, OUTPUT, 180, 88) {
      @Override
      public boolean mayPlace(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK);
      }

      @Override
      public int getMaxStackSize() {
        return 1;
      }

      @Override
      public void setChanged() {
        super.setChanged();
        dirty = true;
      }
    });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 48 + col * 18, 140 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 48 + col * 18, 198));
  }

  AtlasLibraryBlockEntity library() {
    return level.getBlockEntity(pos) instanceof AtlasLibraryBlockEntity library ? library : null;
  }

  public ItemStack output() {
    return io.getItem(OUTPUT);
  }

  public static int encode(int enchantment, int level) {
    return enchantment * LEVEL_BITS + level;
  }

  @Override
  public boolean clickMenuButton(Player clicker, int id) {
    var library = library();
    if (library == null || id < 0 || clicker.isSpectator()) return false;
    int target = id % LEVEL_BITS;
    var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id / LEVEL_BITS);
    if (holder.isEmpty()) return false;
    ItemStack book = library.withdraw(holder.get(), output(), target);
    dirty = true;
    if (book == null) return false;
    io.setItem(OUTPUT, book);
    return true;
  }

  /** Every eligible registry enchantment with the server's cap and exact prices. */
  public AtlasLibraryNetwork.Snapshot snapshot(AtlasLibraryBlockEntity library) {
    float eterna = AtlasLibraryEterna.around(level, pos);
    long pool = library.pool();
    var current = EnchantmentHelper.getEnchantmentsForCrafting(output());
    var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    List<AtlasLibraryNetwork.Offer> offers = new ArrayList<>();
    registry.holders().forEach(holder -> {
      var profile = AtlasLibraryBlockEntity.profile(holder);
      if (!profile.eligible() || offers.size() >= AtlasLibraryNetwork.Snapshot.LIMIT) return;
      int have = current.getLevel(holder);
      int cap = AtlasLibraryLedger.cap(profile, eterna);
      int next = have + 1 <= cap ? have + 1 : 0;
      long nextCost = next == 0 ? 0 : AtlasLibraryLedger.upgradeCost(profile, have, next);
      int max = AtlasLibraryLedger.maxAffordable(pool, profile, have, eterna);
      long maxCost = max > have ? AtlasLibraryLedger.upgradeCost(profile, have, max) : 0;
      offers.add(new AtlasLibraryNetwork.Offer(registry.getId(holder.value()), have, cap, next,
          nextCost, max > have ? max : 0, maxCost));
    });
    return new AtlasLibraryNetwork.Snapshot(containerId, pool, eterna,
        AtlasLibraryEterna.usesApothic(), List.copyOf(offers));
  }

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (!(player instanceof ServerPlayer serverPlayer)) return;
    var library = library();
    if (library == null) return;
    if (dirty || sentPool != library.pool()) {
      dirty = false;
      sentPool = library.pool();
      PacketDistributor.sendToPlayer(serverPlayer, snapshot(library));
    }
  }

  @Override
  public ItemStack quickMoveStack(Player clicker, int index) {
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    if (index <= OUTPUT) {
      if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
    } else if (stack.is(Items.ENCHANTED_BOOK) && output().isEmpty()) {
      // Shift-click only fills the output slot; depositing is always a deliberate placement.
      if (!moveItemStackTo(stack, OUTPUT, OUTPUT + 1, false)) return ItemStack.EMPTY;
    } else {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    return original;
  }

  @Override
  public boolean stillValid(Player clicker) {
    return library() != null
        && clicker.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
  }

  @Override
  public void removed(Player clicker) {
    super.removed(clicker);
    if (!clicker.level().isClientSide) clearContainer(clicker, io);
  }
}
