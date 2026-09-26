package dev.entrelumen;

import net.minecraft.core.BlockPos;

/** Kept for code that predates Ark v2 ({@code NatureRestoration}): a complete Ark's controller. */
public final class LogisticsModuleActions {
  private LogisticsModuleActions() {}

  static BlockPos controllerForDeposit(EngineeringDiagnostics.PhysicalView physical) {
    return physical.state() == EngineeringDiagnostics.ControllerState.FOUND
        && !physical.moduleAreaUnloaded() && physical.missingModules().isEmpty()
        ? physical.controller() : null;
  }
}
