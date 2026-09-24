package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ark arcane service: restores an item's forging history. The anvil prior-work penalty
 * ({@code minecraft:repair_cost}) returns to zero while every other component (enchantments,
 * curses, damage, name, lore, custom data, Apotheosis affixes and gems) stays exactly as it was.
 * It creates no enchantment and removes no curse; no installed mod lowers an existing penalty
 * without stripping enchantments.
 */
public final class ArcaneRestoration {
  /** Experience levels charged per recorded anvil operation. */
  public static final int LEVELS_PER_OPERATION = 5;

  private ArcaneRestoration() {}

  /**
   * Anvil operations recorded on an item: vanilla leaves 2^n − 1 after n operations, so this is
   * ceil(log2(cost + 1)). Zero for no penalty; at most 31 for any int.
   */
  public static int operations(int repairCost) {
    if (repairCost <= 0) return 0;
    return 32 - Integer.numberOfLeadingZeros(repairCost);
  }

  public static int levels(int operations) {
    return operations * LEVELS_PER_OPERATION;
  }

  public static boolean eligible(ItemStack stack) {
    return !stack.isEmpty() && stack.getCount() == 1 && !stack.is(Items.ENCHANTED_BOOK)
        && !stack.is(Items.BOOK) && stack.getOrDefault(DataComponents.REPAIR_COST, 0) > 0;
  }

  public static boolean restore(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
        || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block)
        || block.kind() != ArkFieldJournals.Kind.ARCANE) return false;

    var item = player.getMainHandItem();
    var payment = player.getOffhandItem();
    int operations = eligible(item) ? operations(item.getOrDefault(DataComponents.REPAIR_COST, 0)) : 0;
    int levels = levels(operations);
    if (operations == 0 || !payment.is(Items.BOOK) || payment.getCount() < operations
        || player.experienceLevel < levels) {
      player.sendSystemMessage(Component.translatable("entrelumen.arcane.materials"));
      return false;
    }
    if (LogisticsModuleActions.controllerForDeposit(
        EngineeringDiagnostics.physicalView(player.serverLevel(), module)) == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.arcane.structure"));
      return false;
    }

    // Validation is complete; this is one server-thread transaction. Creative players also pay.
    payment.shrink(operations);
    player.giveExperienceLevels(-levels);
    item.set(DataComponents.REPAIR_COST, 0);
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
    player.serverLevel().playSound(null, module, SoundEvents.ENCHANTMENT_TABLE_USE,
        SoundSource.BLOCKS, 0.35f, 0.8f);
    player.sendSystemMessage(Component.translatable("entrelumen.arcane.restored", operations, levels));
    return true;
  }
}
