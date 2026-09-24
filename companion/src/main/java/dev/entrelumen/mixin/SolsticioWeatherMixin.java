package dev.entrelumen.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Solsticio shares the Overworld's weather data like every other dimension; its light is
 * eternal, so its rain and thunder levels stay at zero and no weather packet is ever sent.
 */
@Mixin(ServerLevel.class)
public abstract class SolsticioWeatherMixin {
  @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true, require = 1)
  private void entrelumen$eternalLight(CallbackInfo callback) {
    ServerLevel level = (ServerLevel) (Object) this;
    var id = level.dimension().location();
    if (!id.getNamespace().equals("entrelumen") || !id.getPath().equals("solsticio")) return;
    if (level.getRainLevel(1.0F) != 0.0F) level.setRainLevel(0.0F);
    if (level.getThunderLevel(1.0F) != 0.0F) level.setThunderLevel(0.0F);
    callback.cancel();
  }
}
