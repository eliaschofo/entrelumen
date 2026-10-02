package dev.entrelumen;

// Quest file format (SNBT), emitted by the quest engine for equippables:
//   { id: "<16 hex>", type: "entrelumen:carried_item", item: { id: "mod:item", count: 1 }, count: 1L }
// `item` is written exactly like FTB's own item task writes `item` (an ItemStack, or an FTB filter item
// such as a tag filter); `count` is optional and defaults to 1. Nothing else is read.

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An item task that also sees what the player wears. FTB's item task counts only the 36 main slots and
 * re-checks only when one of them changes, so an equipped item (armour, off hand, a Curios slot) never
 * counts. This task counts matches across the main inventory, armour, off hand and, when Curios is
 * installed, its active slots ({@link CuriosCompat}, no hard dependency). It is polled once a second from
 * {@link CampaignTask}'s server tick, on login and on inventory changes. It never consumes anything and
 * never lowers progress: once the count reaches the target, progress is set to the target.
 */
public final class CarriedItemTask extends Task {
  private static TaskType TYPE;
  private static ServerQuestFile cachedFile;
  private static List<CarriedItemTask> cachedTasks = List.of();

  private ItemStack item = ItemStack.EMPTY;
  private long count = 1L;

  public CarriedItemTask(long id, Quest quest) {
    super(id, quest);
  }

  /** Registers {@code entrelumen:carried_item}; called from {@link CampaignTask#register()}. */
  static void register() {
    TYPE =
        TaskTypes.register(
            ResourceLocation.fromNamespaceAndPath("entrelumen", "carried_item"),
            CarriedItemTask::new,
            () -> ItemIcon.getItemIcon(Items.IRON_CHESTPLATE));
  }

  /** One pass over every online player; called once a second by {@link CampaignTask#tick}. */
  static void poll(ServerQuestFile file, Collection<ServerPlayer> players) {
    if (cachedFile != file || file.server.getTickCount() % 100 == 0) {
      cachedFile = file;
      cachedTasks =
          file.getAllTasks().stream()
              .filter(CarriedItemTask.class::isInstance)
              .map(CarriedItemTask.class::cast)
              .toList();
    }
    if (cachedTasks.isEmpty()) return;
    for (ServerPlayer player : players) {
      TeamData data = file.getOrCreateTeamData(player);
      if (data.isLocked()) continue;
      file.withPlayerContext(player, () -> {
        for (CarriedItemTask task : cachedTasks) task.submitTask(data, player, ItemStack.EMPTY);
      });
    }
  }

  @Override
  public TaskType getType() {
    return TYPE;
  }

  @Override
  public long getMaxProgress() {
    return count;
  }

  /** The configured item or filter; empty only in a malformed quest file. */
  public ItemStack getItem() {
    return item;
  }

  /** Test and engine hook, mirroring {@code ItemTask.setStackAndCount}. */
  public CarriedItemTask setItemAndCount(ItemStack stack, long target) {
    item = stack.copyWithCount(1);
    count = Math.max(1L, target);
    return this;
  }

  /** True when {@code stack} matches the configured item (FTB filter items included). */
  public boolean matches(ItemStack stack) {
    if (item.isEmpty() || stack.isEmpty()) return false;
    return ItemMatchingSystem.INSTANCE.doesItemMatch(item, stack,
        ItemMatchingSystem.ComponentMatchType.NONE, getQuestFile().holderLookup());
  }

  /** Matching items across the main inventory, armour, off hand and Curios, capped at the target. */
  public long countCarried(ServerPlayer player) {
    var inventory = player.getInventory();
    long total = 0;
    for (List<ItemStack> stacks : List.<List<ItemStack>>of(inventory.items, inventory.armor, inventory.offhand,
        CuriosCompat.equippedStacks(player))) {
      for (ItemStack stack : stacks) {
        if (matches(stack)) total += stack.getCount();
        if (total >= count) return count;
      }
    }
    return total;
  }

  @Override
  public void submitTask(TeamData data, ServerPlayer player, ItemStack crafted) {
    if (data.isLocked() || !data.canStartTasks(getQuest()) || data.isCompleted(this)) return;
    if (countCarried(player) >= count && data.getProgress(this) < count) data.setProgress(this, count);
  }

  @Override
  public boolean submitItemsOnInventoryChange() {
    return true;
  }

  @Override
  public int autoSubmitOnPlayerTick() {
    return 0;
  }

  @Override
  public boolean checkOnLogin() {
    return true;
  }

  @Override
  public MutableComponent getAltTitle() {
    MutableComponent name = Component.empty().append(item.getHoverName());
    return count > 1 ? Component.literal(count + "x ").append(name) : name;
  }

  @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
  @Override
  public Icon getAltIcon() {
    return item.isEmpty() ? super.getAltIcon() : ItemIcon.getItemIcon(item.copyWithCount(1));
  }

  @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
  @Override
  public void addMouseOverText(dev.ftb.mods.ftblibrary.util.TooltipList tooltip, TeamData data) {
    super.addMouseOverText(tooltip, data);
    tooltip.add(Component.translatable("entrelumen.task.carried_item.hint"));
  }

  @Override
  public void writeData(CompoundTag tag, HolderLookup.Provider lookup) {
    super.writeData(tag, lookup);
    tag.put("item", saveItemSingleLine(item.copyWithCount(1)));
    if (count > 1) tag.putLong("count", count);
  }

  @Override
  public void readData(CompoundTag tag, HolderLookup.Provider lookup) {
    super.readData(tag, lookup);
    item = QuestObjectBase.itemOrMissingFromNBT(tag.get("item"), lookup);
    count = Math.max(tag.getLong("count"), 1L);
  }

  @Override
  public void writeNetData(RegistryFriendlyByteBuf buf) {
    super.writeNetData(buf);
    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
    buf.writeVarLong(count);
  }

  @Override
  public void readNetData(RegistryFriendlyByteBuf buf) {
    super.readNetData(buf);
    item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
    count = buf.readVarLong();
  }
}
