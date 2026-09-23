package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;

/** Material repairs at an assembled Ark. No ticking, remote inventory or campaign mutation. */
public final class EngineeringWorkshop {
  private EngineeringWorkshop() {}

  public static boolean repair(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
        || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof EngineeringModuleBlock))
      return false;

    var tool = player.getMainHandItem();
    var material = player.getOffhandItem();
    if (tool.getCount() != 1 || !tool.isDamageableItem() || !tool.isDamaged()
        || !tool.isRepairable() || material.isEmpty()
        || !tool.getItem().isValidRepairItem(tool, material)) {
      player.sendSystemMessage(Component.translatable("entrelumen.engineering.repair_material"));
      return false;
    }
    int repaired = Math.min(tool.getDamageValue(), tool.getMaxDamage() / 4);
    if (repaired <= 0) return false;

    var physical = EngineeringDiagnostics.physicalView(player.serverLevel(), module);
    if (LogisticsModuleActions.controllerForDeposit(physical) == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.engineering.repair_structure"));
      return false;
    }

    // Match one unit of anvil material repair, preserving every other stack component.
    // Material is paid even in creative; there is no free-material branch to leak into survival.
    material.shrink(1);
    tool.setDamageValue(tool.getDamageValue() - repaired);
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
    player.serverLevel().playSound(null, module, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35f, 1.0f);
    player.sendSystemMessage(Component.translatable("entrelumen.engineering.repaired", repaired));
    return true;
  }
}
