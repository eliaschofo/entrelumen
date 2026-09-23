package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Personal kit targets and a complete-or-unchanged transfer from the module's physical stock. */
public final class LogisticsProvisioning {
  private static final ResourceLocation MATRIX =
      ResourceLocation.fromNamespaceAndPath("entrelumen", "routing_matrix");
  private static final String KEY = "entrelumen:logistics_kit";
  private static final int VERSION = 1;
  private static final int MAX_ENTRIES = 8;
  private static final int MAX_TOTAL = MAX_ENTRIES * 99;

  public enum Status {
    RECORDED, PREPARED, ALREADY_READY, NO_PROFILE, EMPTY_SELECTION, UNSUPPORTED,
    INVALID_PROFILE, INSUFFICIENT_STOCK, INSUFFICIENT_SPACE, UNAVAILABLE
  }

  /** Count means supply kinds, transferred items, or missing stock according to the status. */
  public record Outcome(Status status, int count, Component item) {
    public boolean success() {
      return status == Status.RECORDED || status == Status.PREPARED
          || status == Status.ALREADY_READY;
    }

    public Component message() {
      return switch (status) {
        case RECORDED -> Component.translatable("entrelumen.logistics.kit.recorded", count);
        case PREPARED -> Component.translatable("entrelumen.logistics.kit.prepared", count);
        case ALREADY_READY -> Component.translatable("entrelumen.logistics.kit.already");
        case NO_PROFILE -> Component.translatable("entrelumen.logistics.kit.no_profile");
        case EMPTY_SELECTION -> Component.translatable("entrelumen.logistics.kit.empty");
        case UNSUPPORTED -> Component.translatable("entrelumen.logistics.kit.unsupported", item);
        case INVALID_PROFILE -> Component.translatable("entrelumen.logistics.kit.invalid_profile");
        case INSUFFICIENT_STOCK -> Component.translatable(
            "entrelumen.logistics.kit.no_stock", item, count);
        case INSUFFICIENT_SPACE -> Component.translatable("entrelumen.logistics.kit.no_space");
        case UNAVAILABLE -> Component.translatable("entrelumen.logistics.kit.unavailable");
      };
    }
  }

  private enum ReadState { ABSENT, VALID, INVALID }

  private record Supply(ItemStack sample, int desired) {}

  private record Profile(ReadState state, List<Supply> supplies) {}

  private record Transfer(Outcome outcome, List<ItemStack> inventory, List<ItemStack> stock) {}

  private LogisticsProvisioning() {}

  /** Crouch with a Routing Matrix; the other eight hotbar slots become desired quantities. */
  public static Outcome record(ServerPlayer player, BlockPos module) {
    LogisticsStock stock = available(player, module, true);
    if (stock == null) return result(Status.UNAVAILABLE);

    List<Supply> supplies = new ArrayList<>();
    int selected = player.getInventory().selected;
    for (int slot = 0; slot < 9; slot++) {
      if (slot == selected) continue;
      ItemStack stack = player.getInventory().items.get(slot);
      if (stack.isEmpty()) continue;
      if (!supported(stack)) return new Outcome(Status.UNSUPPORTED, 0, stack.getHoverName());
      int at = indexOf(supplies, stack);
      if (at < 0) supplies.add(new Supply(stack.copyWithCount(1), stack.getCount()));
      else {
        Supply earlier = supplies.get(at);
        supplies.set(at, new Supply(earlier.sample(), earlier.desired() + stack.getCount()));
      }
    }
    if (supplies.isEmpty()) return result(Status.EMPTY_SELECTION);

    CompoundTag root = player.getPersistentData();
    if (root.contains(Player.PERSISTED_NBT_TAG)
        && !root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
      return result(Status.INVALID_PROFILE);
    HolderLookup.Provider registries = player.registryAccess();
    CompoundTag encoded = new CompoundTag();
    encoded.putInt("version", VERSION);
    ListTag entries = new ListTag();
    try {
      for (Supply supply : supplies) {
        CompoundTag entry = new CompoundTag();
        entry.put("stack", supply.sample().save(registries, new CompoundTag()));
        entry.putInt("count", supply.desired());
        entries.add(entry);
      }
      encoded.put("entries", entries);
      Profile roundTrip = decode(encoded, registries);
      if (roundTrip.state() != ReadState.VALID
          || roundTrip.supplies().size() != supplies.size())
        return result(Status.INVALID_PROFILE);
      for (int at = 0; at < supplies.size(); at++) {
        Supply original = supplies.get(at);
        Supply saved = roundTrip.supplies().get(at);
        if (original.desired() != saved.desired()
            || !ItemStack.isSameItemSameComponents(original.sample(), saved.sample()))
          return result(Status.INVALID_PROFILE);
      }
    } catch (RuntimeException invalidComponent) {
      return result(Status.INVALID_PROFILE);
    }
    CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
    persisted.put(KEY, encoded);
    root.put(Player.PERSISTED_NBT_TAG, persisted);
    return new Outcome(Status.RECORDED, supplies.size(), null);
  }

  /** Normal Routing Matrix use transfers all missing supplies, or leaves both inventories intact. */
  public static Outcome prepare(ServerPlayer player, BlockPos module) {
    LogisticsStock stock = available(player, module, false);
    if (stock == null) return result(Status.UNAVAILABLE);
    Profile profile = read(player);
    if (profile.state() == ReadState.ABSENT) return result(Status.NO_PROFILE);
    if (profile.state() != ReadState.VALID) return result(Status.INVALID_PROFILE);

    List<ItemStack> held = snapshot(player.getInventory().items);
    List<ItemStack> stored = new ArrayList<>(LogisticsStock.SLOTS);
    for (int slot = 0; slot < LogisticsStock.SLOTS; slot++) stored.add(stock.getItem(slot).copy());
    Transfer transfer = plan(profile.supplies(), held, stored, player.getInventory().getMaxStackSize());
    if (transfer.outcome().status() != Status.PREPARED) return transfer.outcome();

    // No callbacks or inventory writes occur during planning. Recheck before the first commit.
    if (!stock.stillValid(player) || !sameSlots(held, player.getInventory().items)
        || !sameStock(stored, stock)) return result(Status.UNAVAILABLE);
    for (int slot = 0; slot < LogisticsStock.SLOTS; slot++)
      if (!ItemStack.matches(stored.get(slot), transfer.stock().get(slot)))
        stock.setItem(slot, transfer.stock().get(slot));
    for (int slot = 0; slot < 36; slot++)
      if (!ItemStack.matches(held.get(slot), transfer.inventory().get(slot)))
        player.getInventory().setItem(slot, transfer.inventory().get(slot));
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
    return transfer.outcome();
  }

  private static LogisticsStock available(ServerPlayer player, BlockPos module, boolean recording) {
    if (!player.isAlive() || player.isSpectator()
        || player.isSecondaryUseActive() != recording
        || !MATRIX.equals(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()))
        || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof LogisticsModuleBlock)
        || LogisticsModuleActions.controllerForDeposit(
            EngineeringDiagnostics.physicalView(player.serverLevel(), module)) == null)
      return null;
    return player.serverLevel().getBlockEntity(module) instanceof LogisticsStock stock ? stock : null;
  }

  private static boolean supported(ItemStack stack) {
    try {
      if (stack.isEmpty() || stack.has(DataComponents.MAX_DAMAGE)
          || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)
          || stack.has(DataComponents.BLOCK_ENTITY_DATA)
          || stack.has(DataComponents.CONTAINER_LOOT)) return false;
      if (stack.getCapability(Capabilities.ItemHandler.ITEM) != null) return false;
      return stack.getMaxStackSize() > 1 || stack.has(DataComponents.FOOD)
          || stack.getUseAnimation() == UseAnim.DRINK
          || stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)
          || stack.is(Items.LINGERING_POTION);
    } catch (RuntimeException brokenCapability) {
      return false;
    }
  }

  private static Profile read(ServerPlayer player) {
    CompoundTag root = player.getPersistentData();
    if (!root.contains(Player.PERSISTED_NBT_TAG)) return new Profile(ReadState.ABSENT, List.of());
    if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND))
      return new Profile(ReadState.INVALID, List.of());
    CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
    if (!persisted.contains(KEY)) return new Profile(ReadState.ABSENT, List.of());
    if (!persisted.contains(KEY, Tag.TAG_COMPOUND))
      return new Profile(ReadState.INVALID, List.of());
    return decode(persisted.getCompound(KEY), player.registryAccess());
  }

  private static Profile decode(CompoundTag tag, HolderLookup.Provider registries) {
    Profile invalid = new Profile(ReadState.INVALID, List.of());
    if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION
        || !tag.contains("entries", Tag.TAG_LIST)) return invalid;
    ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
    if (entries.isEmpty() || entries.size() > MAX_ENTRIES) return invalid;
    List<Supply> supplies = new ArrayList<>();
    int total = 0;
    try {
      for (int at = 0; at < entries.size(); at++) {
        CompoundTag entry = entries.getCompound(at);
        if (!entry.contains("stack", Tag.TAG_COMPOUND)
            || !entry.contains("count", Tag.TAG_INT)) return invalid;
        ItemStack sample = ItemStack.parse(registries, entry.getCompound("stack"))
            .orElse(ItemStack.EMPTY);
        int desired = entry.getInt("count");
        if (sample.isEmpty() || sample.getCount() != 1 || !supported(sample)
            || desired < 1 || desired > MAX_ENTRIES * sample.getMaxStackSize()
            || indexOf(supplies, sample) >= 0) return invalid;
        total += desired;
        if (total > MAX_TOTAL) return invalid;
        supplies.add(new Supply(sample, desired));
      }
    } catch (RuntimeException invalidComponent) {
      return invalid;
    }
    return new Profile(ReadState.VALID, List.copyOf(supplies));
  }

  private static Transfer plan(List<Supply> supplies, List<ItemStack> inventory,
      List<ItemStack> stock, int inventoryLimit) {
    List<ItemStack> afterInventory = snapshot(inventory);
    List<ItemStack> afterStock = snapshot(stock);
    int moved = 0;
    for (Supply supply : supplies) {
      int already = count(inventory, supply.sample());
      int shortage = Math.max(0, supply.desired() - already);
      if (shortage == 0) continue;
      int available = count(stock, supply.sample());
      if (available < shortage) return new Transfer(
          new Outcome(Status.INSUFFICIENT_STOCK, shortage - available,
              supply.sample().getHoverName()), inventory, stock);
      int toPlace = shortage;
      for (int slot = 0; slot < afterInventory.size() && toPlace > 0; slot++) {
        ItemStack present = afterInventory.get(slot);
        if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, supply.sample()))
          continue;
        int fit = Math.min(toPlace,
            Math.max(0, Math.min(inventoryLimit, present.getMaxStackSize()) - present.getCount()));
        if (fit > 0) {
          present.grow(fit);
          toPlace -= fit;
        }
      }
      for (int slot = 0; slot < afterInventory.size() && toPlace > 0; slot++) {
        if (!afterInventory.get(slot).isEmpty()) continue;
        int fit = Math.min(toPlace, Math.min(inventoryLimit, supply.sample().getMaxStackSize()));
        if (fit > 0) {
          afterInventory.set(slot, supply.sample().copyWithCount(fit));
          toPlace -= fit;
        }
      }
      if (toPlace > 0) return new Transfer(result(Status.INSUFFICIENT_SPACE), inventory, stock);

      int toTake = shortage;
      for (int slot = 0; slot < afterStock.size() && toTake > 0; slot++) {
        ItemStack present = afterStock.get(slot);
        if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, supply.sample())) {
          int take = Math.min(toTake, present.getCount());
          present.shrink(take);
          toTake -= take;
        }
      }
      if (toTake != 0) return new Transfer(result(Status.UNAVAILABLE), inventory, stock);
      moved += shortage;
    }
    return moved == 0
        ? new Transfer(result(Status.ALREADY_READY), inventory, stock)
        : new Transfer(new Outcome(Status.PREPARED, moved, null), afterInventory, afterStock);
  }

  private static List<ItemStack> snapshot(List<ItemStack> stacks) {
    List<ItemStack> copies = new ArrayList<>(stacks.size());
    for (ItemStack stack : stacks) copies.add(stack.copy());
    return copies;
  }

  private static int count(List<ItemStack> stacks, ItemStack sample) {
    int total = 0;
    for (ItemStack stack : stacks)
      if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample))
        total += stack.getCount();
    return total;
  }

  private static int indexOf(List<Supply> supplies, ItemStack stack) {
    for (int at = 0; at < supplies.size(); at++)
      if (ItemStack.isSameItemSameComponents(supplies.get(at).sample(), stack)) return at;
    return -1;
  }

  private static boolean sameSlots(List<ItemStack> original, List<ItemStack> current) {
    if (original.size() != current.size()) return false;
    for (int slot = 0; slot < original.size(); slot++)
      if (!ItemStack.matches(original.get(slot), current.get(slot))) return false;
    return true;
  }

  private static boolean sameStock(List<ItemStack> original, LogisticsStock current) {
    for (int slot = 0; slot < LogisticsStock.SLOTS; slot++)
      if (!ItemStack.matches(original.get(slot), current.getItem(slot))) return false;
    return true;
  }

  private static Outcome result(Status status) {
    return new Outcome(status, 0, null);
  }
}
