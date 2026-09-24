package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Server-authoritative pool of the Atlas Library. Books are converted into points on deposit;
 * any eligible enchantment can be bought back up to the level the surrounding Eterna allows.
 */
public final class AtlasLibraryBlockEntity extends BlockEntity {
  public static final String POOL_TAG = "pool";
  private long pool;
  private final IItemHandler depositHandler = new DepositHandler();

  public AtlasLibraryBlockEntity(BlockPos pos, BlockState state) {
    super(ApotheosisContent.ATLAS_LIBRARY_ENTITY.get(), pos, state);
  }

  public long pool() {
    return pool;
  }

  public static AtlasLibraryLedger.Profile profile(Holder<Enchantment> holder) {
    var enchantment = holder.value();
    boolean treasure = holder.is(EnchantmentTags.TREASURE);
    boolean obtainable = treasure || holder.is(EnchantmentTags.IN_ENCHANTING_TABLE)
        || holder.is(EnchantmentTags.TRADEABLE) || holder.is(EnchantmentTags.ON_RANDOM_LOOT);
    String id = holder.unwrapKey().map(key -> key.location().toString()).orElse("");
    return AtlasLibraryLedger.Profile.of(id, enchantment.getWeight(), enchantment.getMaxLevel(),
        holder.is(EnchantmentTags.CURSE), treasure, obtainable);
  }

  /** Points a single enchanted book adds; zero for anything else or for curse-only books. */
  public static long bookValue(ItemStack book) {
    if (!book.is(Items.ENCHANTED_BOOK) || book.getCount() != 1) return 0;
    long value = 0;
    for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(book).entrySet())
      value = AtlasLibraryLedger.add(value,
          AtlasLibraryLedger.depositValue(profile(entry.getKey()), entry.getIntValue()));
    return value;
  }

  /** Consumes nothing itself: the caller removes the book only when this returns true. */
  public boolean deposit(ItemStack book) {
    long value = bookValue(book);
    if (value <= 0) return false;
    pool = AtlasLibraryLedger.add(pool, value);
    setChanged();
    return true;
  }

  /**
   * Validates against the live pool and a freshly measured Eterna and, when accepted, returns the
   * new output book. Returns null on rejection without touching the pool.
   */
  public ItemStack withdraw(Holder<Enchantment> enchantment, ItemStack output, int target) {
    if (level == null || level.isClientSide) return null;
    if (!output.isEmpty() && (!output.is(Items.ENCHANTED_BOOK) || output.getCount() != 1)) return null;
    int current = EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(enchantment);
    var result = AtlasLibraryLedger.withdraw(pool, profile(enchantment), current, target,
        AtlasLibraryEterna.around(level, worldPosition));
    if (!result.accepted()) return null;
    ItemStack book = output.isEmpty() ? new ItemStack(Items.ENCHANTED_BOOK) : output.copy();
    var enchantments = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(book));
    enchantments.set(enchantment, target);
    EnchantmentHelper.setEnchantments(book, enchantments.toImmutable());
    pool = result.pool();
    setChanged();
    return book;
  }

  public IItemHandler depositHandler() {
    return depositHandler;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putLong(POOL_TAG, pool);
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    pool = Math.max(0L, tag.getLong(POOL_TAG));
  }

  /** Hoppers and ME export buses may insert books; nothing can be extracted this way. */
  private final class DepositHandler implements IItemHandler {
    @Override
    public int getSlots() {
      return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      if (slot != 0 || stack.isEmpty() || stack.getCount() != 1 || bookValue(stack) <= 0) return stack;
      if (!simulate) deposit(stack);
      return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
      return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return slot == 0 && bookValue(stack) > 0;
    }
  }
}
