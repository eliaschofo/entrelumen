package dev.entrelumen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The Ark's final activation (act V to VI), one bounded server transaction at the controller. */
public final class ArkActions {
  private ArkActions() {}

  private static boolean reachableController(ServerPlayer player, BlockPos pos) {
    return !player.isSpectator()
        && player.canInteractWithBlock(pos, 1.0)
        && player.serverLevel().hasChunkAt(pos)
        && player.serverLevel().getBlockState(pos).getBlock() instanceof ArkControllerBlock;
  }

  /**
   * Activates the team's Ark at this controller when the checklist is complete: forges the Light Key
   * and opens act VI. Otherwise shows the screen with what is missing.
   */
  public static boolean activate(ServerPlayer player, BlockPos controller) {
    if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()
        || !reachableController(player, controller)) return false;
    var campaign = Entrelumen.current(player);
    if (campaign.completed.contains(CampaignMilestones.LAST_HORIZON)) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.ending"));
      // Teams that activated the Ark before the key existed receive it here, once.
      Solsticio.forgeKey(player);
      return false;
    }
    ArkData data = ArkData.get(player.server);
    ArkData.Ark ark = ArkState.ark(player);
    if (ark == null || !ark.dimension.equals(player.serverLevel().dimension())
        || !ark.anchor.origin().equals(controller)) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.not_team_controller"));
      ArkFieldJournals.inspect(player, controller);
      return false;
    }
    ArkState.rescan(player.serverLevel(), ark, data);
    if (!CampaignMilestones.finish(campaign, ark.status())) {
      player.sendSystemMessage(Component.translatable("entrelumen.ark.activation_unavailable"));
      ArkFieldJournals.inspect(player, controller);
      return false;
    }
    CampaignData.get(player.server).setDirty();
    // The activated Ark forges the team's Light Key (act VI: Solsticio).
    Solsticio.forgeKey(player);
    return true;
  }
}
