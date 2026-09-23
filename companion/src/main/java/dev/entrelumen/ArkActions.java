package dev.entrelumen;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One bounded server transaction per deliberate controller interaction. */
public final class ArkActions {
  private ArkActions() {}

  private static boolean reachableController(ServerPlayer player, BlockPos pos) {
    return !player.isSpectator()
        && player.canInteractWithBlock(pos, 1.0)
        && player.serverLevel().hasChunkAt(pos)
        && player.serverLevel().getBlockState(pos).getBlock() instanceof ArkControllerBlock;
  }

  static Set<String> missingModules(ServerPlayer player, BlockPos controller) {
    Set<String> missing = new HashSet<>(Entrelumen.MODULES);
    var level = player.serverLevel();
    for (BlockPos pos : BlockPos.betweenClosed(controller.offset(-3, -1, -3), controller.offset(3, 2, 3))) {
      // A controller at a chunk edge must never generate/load neighbors to inspect them.
      if (!level.hasChunkAt(pos)) continue;
      var key = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
      if (key.getNamespace().equals("entrelumen")) missing.remove(key.getPath());
      if (missing.isEmpty()) break;
    }
    return Set.copyOf(missing);
  }

  public static boolean deposit(ServerPlayer player, UUID expectedCampaign, BlockPos controller,
      int expectedStep) {
    if (!CampaignActions.campaignId(player).equals(expectedCampaign)) {
      player.sendSystemMessage(Component.translatable("entrelumen.atlas.stale"));
      return false;
    }
    if (!reachableController(player, controller) || !missingModules(player, controller).isEmpty()) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.structure"));
      return false;
    }
    var campaign = Entrelumen.current(player);
    boolean accepted = ArkCommissioning.deposit(campaign, expectedStep,
        Entrelumen.availableMaterials(player), acceptedItems -> {
          for (var entry : acceptedItems.entrySet()) {
            int remaining = entry.getValue();
            for (var stack : Entrelumen.deliveryStacks(player)) {
              if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(entry.getKey())) continue;
              int take = Math.min(remaining, stack.getCount());
              stack.shrink(take);
              remaining -= take;
              if (remaining == 0) break;
            }
          }
          player.getInventory().setChanged();
          player.containerMenu.broadcastChanges();
        });
    if (accepted) CampaignData.get(player.server).setDirty();
    player.sendSystemMessage(Component.translatable(
        accepted ? "entrelumen.ark.accepted" : "entrelumen.ark.no_deposit"));
    return accepted;
  }

  public static boolean activate(ServerPlayer player, UUID expectedCampaign, BlockPos controller) {
    if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) return false;
    if (!CampaignActions.campaignId(player).equals(expectedCampaign)) {
      player.sendSystemMessage(Component.translatable("entrelumen.atlas.stale"));
      return false;
    }
    if (!reachableController(player, controller) || !missingModules(player, controller).isEmpty()) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.structure"));
      return false;
    }
    if (!CampaignMilestones.finish(Entrelumen.current(player))) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.activation_unavailable"));
      return false;
    }
    CampaignData.get(player.server).setDirty();
    return true;
  }

  public static void inspect(ServerPlayer player, BlockPos controller) {
    if (!reachableController(player, controller)) return;
    var campaign = Entrelumen.current(player);
    if (campaign.completed.contains(CampaignMilestones.LAST_HORIZON)) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.ending"));
      return;
    }
    if (campaign.arkPhase >= ArkCommissioning.STEPS.size()) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.commissioned"));
      player.sendSystemMessage(Component.translatable("entrelumen.ark.activate_hint"));
      return;
    }
    var step = ArkCommissioning.STEPS.get(campaign.arkPhase);
    player.sendSystemMessage(Component.translatable("entrelumen.ark.current",
        Component.translatable("entrelumen.ark.stage." + step.phase()),
        Component.translatable("entrelumen.project." + step.module()),
        campaign.arkPhase, ArkCommissioning.STEPS.size()));
    for (var entry : step.requirements().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
      int stored = campaign.arkDeposits.getOrDefault(entry.getKey(), 0);
      var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
      player.sendSystemMessage(Component.translatable("entrelumen.ark.material",
          new ItemStack(item).getHoverName(), stored, entry.getValue(), entry.getValue() - stored));
    }
    if (!ArkCommissioning.eligible(campaign)) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.requirements", campaign.arkPhase));
    }
    for (String missing : missingModules(player, controller).stream().sorted().toList()) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.missing_module",
          Component.translatable("entrelumen.project." + missing)));
    }
    player.sendSystemMessage(Component.translatable("entrelumen.ark.deposit_hint"));
  }
}
