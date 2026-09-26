package dev.entrelumen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * What is left of the Logistics module's shared depot (Ark v1, removed on 25 September 2026). The
 * block entity stays registered so a saved depot still loads with its 27 slots. The first time its
 * module ticks on the server, every stack is dropped on top of the module as an item that never
 * despawns, and the depot is emptied; nothing can be put back. Breaking the module drops the stock
 * too. Nothing is lost.
 */
public final class LogisticsStock extends BlockEntity {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final int SLOTS = 27;

  private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
  private boolean returned;

  public LogisticsStock(BlockPos pos, BlockState state) {
    super(Entrelumen.LOGISTICS_STOCK.get(), pos, state);
  }

  /** Server ticker: returns the old stock once. */
  public static void tick(Level level, BlockPos pos, BlockState state, LogisticsStock stock) {
    if (!stock.returned) stock.returnStock(level, pos, true);
  }

  public boolean isEmpty() {
    for (ItemStack stack : items) if (!stack.isEmpty()) return false;
    return true;
  }

  /** For tests and the migration: fills a slot of an old depot. */
  void setLegacyItem(int slot, ItemStack stack) {
    items.set(slot, stack.copy());
    returned = false;
    setChanged();
  }

  /**
   * Drops every stored stack at the module (on top when {@code above}) and empties the depot; the
   * slots are emptied before the drops spawn, so a repeated call cannot duplicate anything.
   */
  int returnStock(Level level, BlockPos pos, boolean above) {
    returned = true;
    if (isEmpty()) return 0;
    int dropped = 0;
    double y = above ? pos.getY() + 1.1 : pos.getY() + 0.5;
    for (int slot = 0; slot < SLOTS; slot++) {
      ItemStack stack = items.set(slot, ItemStack.EMPTY);
      if (stack.isEmpty()) continue;
      ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, y, pos.getZ() + 0.5, stack, 0, 0.1, 0);
      drop.setUnlimitedLifetime();
      level.addFreshEntity(drop);
      dropped += stack.getCount();
    }
    setChanged();
    LOGGER.info("Returned {} item(s) from the old Logistics depot at {} in {}", dropped, pos.toShortString(),
        level.dimension().location());
    return dropped;
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    ContainerHelper.loadAllItems(tag, items, registries);
    returned = false;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    ContainerHelper.saveAllItems(tag, items, registries);
  }
}
