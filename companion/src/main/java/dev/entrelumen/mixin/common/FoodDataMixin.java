package dev.entrelumen.mixin.common;

import dev.entrelumen.ArkEffects;
import dev.entrelumen.SatietyOverflowEvents;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two funnels of {@link FoodData}, each a one-line vanilla method:
 *
 * <ul>
 *   <li>{@code add(IF)V}, the private funnel behind both {@code eat} overloads: reports what a bite
 *       adds before vanilla clamps it, so blocks that feed the player in place (cake, pies) are
 *       measured without knowing their food values. Only an open block-bite window uses the report.
 *   <li>{@code addExhaustion(F)V}, where every source of hunger ends: movement, attacks, damage, the
 *       Hunger effect ({@code Player.causeFoodExhaustion}) and the saturation healing of
 *       {@code FoodData.tick}, which calls it directly. The Ark's Nature module scales it there.
 *       NeoForge 21.1.249 has no exhaustion event (its sources were searched on 25 September 2026).
 * </ul>
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
  @Inject(method = "add(IF)V", at = @At("HEAD"))
  private void entrelumen$measureBite(int nutrition, float saturation, CallbackInfo ci) {
    SatietyOverflowEvents.onFoodAdded((FoodData) (Object) this, nutrition, saturation);
  }

  @ModifyVariable(method = "addExhaustion(F)V", at = @At("HEAD"), argsOnly = true, require = 1)
  private float entrelumen$arkVitality(float exhaustion) {
    return ArkEffects.exhaustion((FoodData) (Object) this, exhaustion);
  }
}
