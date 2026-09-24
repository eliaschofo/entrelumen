package dev.entrelumen.mixin.common;

import dev.entrelumen.SatietyOverflowEvents;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports what a bite adds before vanilla clamps it. {@code FoodData.add} is the private funnel
 * behind both {@code eat} overloads, so blocks that feed the player in place (cake, pies) are
 * measured without knowing their food values. Only an open block-bite window uses the report.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
  @Inject(method = "add(IF)V", at = @At("HEAD"))
  private void entrelumen$measureBite(int nutrition, float saturation, CallbackInfo ci) {
    SatietyOverflowEvents.onFoodAdded((FoodData) (Object) this, nutrition, saturation);
  }
}
