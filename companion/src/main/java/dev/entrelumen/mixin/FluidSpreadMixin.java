package dev.entrelumen.mixin;

import dev.entrelumen.StructureProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No fluid flows into a guarded block unless the structure's own fluid is flowing inside it. */
@Mixin(FlowingFluid.class)
public abstract class FluidSpreadMixin {
  @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true, require = 1)
  private void entrelumen$guardFlow(BlockGetter level, BlockPos fromPos, BlockState fromState, Direction direction,
      BlockPos toPos, BlockState toState, FluidState toFluid, Fluid fluid, CallbackInfoReturnable<Boolean> callback) {
    if (!StructureProtection.fluidMayFlow(level, fromPos, toPos)) callback.setReturnValue(false);
  }
}
