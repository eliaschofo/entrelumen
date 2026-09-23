package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Conserves a compound book's enchantments in separate volumes at a complete Ark. */
public final class ArcaneLibrary {
  private ArcaneLibrary() {}

  public static boolean separate(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
        || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block)
        || block.kind() != ArkFieldJournals.Kind.ARCANE) return false;

    var source = player.getMainHandItem();
    var stored = source.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
    var payment = player.getOffhandItem();
    int cost = stored.size() - 1;
    if (!source.is(Items.ENCHANTED_BOOK) || source.getCount() != 1 || cost < 1
        || !payment.is(Items.BOOK) || payment.getCount() < cost) {
      player.sendSystemMessage(Component.translatable("entrelumen.arcane.materials"));
      return false;
    }
    if (LogisticsModuleActions.controllerForDeposit(
        EngineeringDiagnostics.physicalView(player.serverLevel(), module)) == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.arcane.structure"));
      return false;
    }

    var inventory = player.getInventory();
    var emptySlots = new ArrayList<Integer>();
    for (int slot = 0; slot < inventory.items.size(); slot++)
      if (inventory.items.get(slot).isEmpty()) emptySlots.add(slot);
    if (emptySlots.size() < cost) {
      player.sendSystemMessage(Component.translatable("entrelumen.arcane.space", cost));
      return false;
    }

    var entries = new ArrayList<>(stored.entrySet());
    entries.sort(Comparator.comparing(entry -> entry.getKey().unwrapKey()
        .map(key -> key.location().toString()).orElse("")));
    var output = new ArrayList<ItemStack>();
    for (var entry : entries) {
      var single = new ItemEnchantments.Mutable(stored);
      single.removeIf(holder -> !holder.equals(entry.getKey()));
      // Arbitrary source components belong to exactly one volume, never every copy.
      var volume = output.isEmpty() ? source.copy() : new ItemStack(Items.ENCHANTED_BOOK);
      volume.set(DataComponents.STORED_ENCHANTMENTS, single.toImmutable());
      var repairCost = source.get(DataComponents.REPAIR_COST);
      if (repairCost != null) volume.set(DataComponents.REPAIR_COST, repairCost);
      output.add(volume);
    }

    // All validation and output construction precede this server-thread-only transaction.
    payment.shrink(cost);
    inventory.setItem(inventory.selected, output.getFirst());
    for (int i = 1; i < output.size(); i++) inventory.setItem(emptySlots.get(i - 1), output.get(i));
    inventory.setChanged();
    player.containerMenu.broadcastChanges();
    player.serverLevel().playSound(null, module, SoundEvents.ENCHANTMENT_TABLE_USE,
        SoundSource.BLOCKS, 0.35f, 1.0f);
    player.sendSystemMessage(Component.translatable("entrelumen.arcane.separated", output.size(), cost));
    return true;
  }
}
