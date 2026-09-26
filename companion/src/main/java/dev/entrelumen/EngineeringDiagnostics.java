package dev.entrelumen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * The first version's physical view of an Ark: one controller within reach of a module and the six
 * modules around it, read without loading chunks. Ark v2 no longer uses it (see {@link ArkState});
 * it stays, unchanged, for {@code NatureRestoration} until the altars take its restoration over.
 */
public final class EngineeringDiagnostics {
  public enum ControllerState { FOUND, ABSENT, UNLOADED, AMBIGUOUS }

  public record PhysicalView(ControllerState state, BlockPos controller,
      Set<String> missingModules, boolean moduleAreaUnloaded) {
    public PhysicalView {
      missingModules = Set.copyOf(missingModules);
    }
  }

  private EngineeringDiagnostics() {}

  /** Controller candidates are exactly those whose existing Ark volume can contain this module. */
  static PhysicalView physicalView(ServerLevel level, BlockPos module) {
    return scan(module,
        pos -> level.isOutsideBuildHeight(pos) || level.hasChunkAt(pos),
        pos -> !level.isOutsideBuildHeight(pos)
            && level.getBlockState(pos).getBlock() instanceof ArkControllerBlock,
        pos -> {
          if (level.isOutsideBuildHeight(pos)) return "";
          var key = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
          return key.getNamespace().equals("entrelumen") ? key.getPath() : "";
        });
  }

  /** Predicates are called only for inspectable positions, so callers never load missing chunks. */
  static PhysicalView scan(BlockPos module, Predicate<BlockPos> inspectable,
      Predicate<BlockPos> isController, Function<BlockPos, String> moduleAt) {
    List<BlockPos> controllers = new ArrayList<>();
    boolean unloaded = false;
    for (BlockPos candidate : BlockPos.betweenClosed(module.offset(-3, -2, -3),
        module.offset(3, 1, 3))) {
      if (!inspectable.test(candidate)) {
        unloaded = true;
        continue;
      }
      if (isController.test(candidate))
        controllers.add(candidate.immutable());
    }
    if (controllers.size() > 1)
      return new PhysicalView(ControllerState.AMBIGUOUS, null, Set.of(), unloaded);
    if (unloaded)
      return new PhysicalView(ControllerState.UNLOADED, null, Set.of(), true);
    if (controllers.isEmpty())
      return new PhysicalView(ControllerState.ABSENT, null, Set.of(), false);

    BlockPos controller = controllers.getFirst();
    Set<String> missing = new TreeSet<>(CampaignMilestones.MODULE_IDS);
    boolean moduleAreaUnloaded = false;
    for (BlockPos pos : BlockPos.betweenClosed(controller.offset(-3, -1, -3),
        controller.offset(3, 2, 3))) {
      if (!inspectable.test(pos)) {
        moduleAreaUnloaded = true;
        continue;
      }
      missing.remove(moduleAt.apply(pos));
    }
    return new PhysicalView(ControllerState.FOUND, controller, missing, moduleAreaUnloaded);
  }
}
