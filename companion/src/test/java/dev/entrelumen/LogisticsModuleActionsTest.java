package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LogisticsModuleActionsTest {
  @Test
  void onlyOneFullyKnownCompleteControllerCanReceiveADeposit() {
    var module = new BlockPos(0, 70, 0);
    var controller = module.offset(3, 1, 0);
    var blocks = new HashMap<BlockPos, String>();
    blocks.put(controller, "controller");
    blocks.put(module, "logistics_module");
    int index = 0;
    for (String id : CampaignMilestones.MODULE_IDS)
      blocks.put(controller.offset(index++ - 2, 0, 1), id);

    var complete = scan(module, blocks, pos -> true);
    assertEquals(controller, LogisticsModuleActions.controllerForDeposit(complete));

    var removed = blocks.remove(controller.offset(0, 0, 1));
    assertNull(LogisticsModuleActions.controllerForDeposit(scan(module, blocks, pos -> true)));
    blocks.put(controller.offset(0, 0, 1), removed);
    assertNull(LogisticsModuleActions.controllerForDeposit(
        scan(module, blocks, pos -> pos.getX() != 4)));
    assertNull(LogisticsModuleActions.controllerForDeposit(
        scan(module, blocks, pos -> pos.getX() != -3)));
    blocks.put(module.offset(-1, 0, 0), "controller");
    assertNull(LogisticsModuleActions.controllerForDeposit(scan(module, blocks, pos -> true)));
    blocks.clear();
    assertNull(LogisticsModuleActions.controllerForDeposit(scan(module, blocks, pos -> true)));
  }

  private static EngineeringDiagnostics.PhysicalView scan(BlockPos module,
      Map<BlockPos, String> blocks, java.util.function.Predicate<BlockPos> loaded) {
    return EngineeringDiagnostics.scan(module, loaded,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
  }
}
