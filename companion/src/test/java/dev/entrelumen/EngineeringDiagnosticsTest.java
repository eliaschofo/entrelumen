package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class EngineeringDiagnosticsTest {
  @Test
  void physicalScanSeparatesAbsentUnloadedAmbiguousAndKnownMissing() {
    var module = new BlockPos(0, 70, 0);
    var controller = module.offset(3, 1, 0);
    var blocks = new HashMap<BlockPos, String>();
    blocks.put(module, "engineering_module");
    blocks.put(controller, "controller");
    var loaded = EngineeringDiagnostics.scan(module, pos -> true,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.FOUND, loaded.state());
    assertEquals(controller, loaded.controller());
    assertTrue(loaded.missingModules().contains("nature_module"));
    assertFalse(loaded.missingModules().contains("engineering_module"));
    assertFalse(loaded.moduleAreaUnloaded());

    var incompleteArea = EngineeringDiagnostics.scan(module, pos -> pos.getX() != 4,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.FOUND, incompleteArea.state());
    assertTrue(incompleteArea.moduleAreaUnloaded());

    var unknownController = EngineeringDiagnostics.scan(module, pos -> pos.getX() != -3,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.UNLOADED, unknownController.state());
    assertNull(unknownController.controller());

    blocks.put(module.offset(-1, 0, 0), "controller");
    var ambiguous = EngineeringDiagnostics.scan(module, pos -> true,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.AMBIGUOUS, ambiguous.state());
    blocks.clear();
    var absent = EngineeringDiagnostics.scan(module, pos -> true,
        pos -> "controller".equals(blocks.get(pos)), pos -> blocks.getOrDefault(pos, ""));
    assertEquals(EngineeringDiagnostics.ControllerState.ABSENT, absent.state());
  }
}
