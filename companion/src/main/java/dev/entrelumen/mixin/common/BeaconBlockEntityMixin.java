package dev.entrelumen.mixin.common;

import dev.entrelumen.ArkState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A beacon on one of the Ark's column tops works without a pyramid: the level vanilla counts from the
 * base ({@code BeaconBlockEntity.updateBase}, a private static method called every 80 ticks while the
 * beam is clear) is raised to 4 when the beacon stands in a beacon place of a standing Ark (core and
 * controller). Its screen and effect choice stay vanilla's. NeoForge 21.1.249 has no event for a
 * beacon's level, so this is the narrowest hook: the method's return value, on the server only.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {
  @Inject(method = "updateBase", at = @At("RETURN"), cancellable = true, require = 1)
  private static void entrelumen$arkBeacon(Level level, int x, int y, int z, CallbackInfoReturnable<Integer> result) {
    if (result.getReturnValueI() < 4 && ArkState.arkBeacon(level, x, y, z)) result.setReturnValue(4);
  }
}
