package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Native chat view; the controller retains all deposit authority. */
public final class LogisticsModuleActions {
  private LogisticsModuleActions() {}

  static BlockPos controllerForDeposit(EngineeringDiagnostics.PhysicalView physical) {
    return physical.state() == EngineeringDiagnostics.ControllerState.FOUND
        && !physical.moduleAreaUnloaded() && physical.missingModules().isEmpty()
        ? physical.controller() : null;
  }

  public static boolean inspect(ServerPlayer player, BlockPos module) {
    if (player.isSpectator() || !player.canInteractWithBlock(module, 1.0)
        || !player.serverLevel().hasChunkAt(module)
        || !(player.serverLevel().getBlockState(module).getBlock() instanceof LogisticsModuleBlock))
      return false;
    var view = EngineeringDiagnostics.campaignView(EngineeringDiagnostics.currentReadOnly(player));
    var physical = EngineeringDiagnostics.physicalView(player.serverLevel(), module);
    lines(view, physical).forEach(player::sendSystemMessage);
    return true;
  }

  static List<Component> lines(EngineeringDiagnostics.CampaignView view,
      EngineeringDiagnostics.PhysicalView physical) {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable("entrelumen.logistics.title"));
    if (view.ending())
      lines.add(Component.translatable("entrelumen.logistics.ending"));
    else if (view.commissioned())
      lines.add(Component.translatable("entrelumen.logistics.commissioned"));
    else {
      lines.add(Component.translatable("entrelumen.logistics.batch", view.step() + 1,
          view.totalSteps(), Component.translatable("entrelumen.ark.stage." + view.phase()),
          Component.translatable("entrelumen.project." + view.module())));
      for (var material : view.materials()) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(material.item()));
        lines.add(Component.translatable("entrelumen.ark.material", new ItemStack(item).getHoverName(),
            material.deposited(), material.required(), material.remaining()));
      }
      if (view.archived() || view.act() != CampaignMilestones.ARK_ACT || !view.missingProjects().isEmpty())
        lines.add(Component.translatable("entrelumen.logistics.projects_required"));
    }
    switch (physical.state()) {
      case ABSENT -> lines.add(Component.translatable("entrelumen.logistics.no_controller"));
      case UNLOADED -> lines.add(Component.translatable("entrelumen.logistics.controller_unloaded"));
      case AMBIGUOUS -> lines.add(Component.translatable("entrelumen.logistics.ambiguous"));
      case FOUND -> {
        lines.add(Component.translatable("entrelumen.engineering.controller_at",
            physical.controller().getX(), physical.controller().getY(), physical.controller().getZ()));
        if (physical.moduleAreaUnloaded())
          lines.add(Component.translatable("entrelumen.logistics.modules_unloaded"));
        else if (!physical.missingModules().isEmpty())
          for (String id : physical.missingModules().stream().sorted().toList())
            lines.add(Component.translatable("entrelumen.ark.missing_module",
                Component.translatable("entrelumen.project." + id)));
      }
    }
    if (!view.commissioned() && !view.ending())
      lines.add(Component.translatable("entrelumen.logistics.hint"));
    return List.copyOf(lines);
  }
}
