package dev.entrelumen.mixin.client;

import com.google.gson.JsonElement;
import dev.entrelumen.client.compat.JeedEffectJson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEED 2.3.4 omits the EMI ingredient type when saving an effect to history. */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.jeed.plugin.emi.ingredient.EffectIngredientSerializer", remap = false)
public abstract class JeedEffectIngredientSerializerMixin {
  @Inject(
      method = "serialize(Lnet/mehvahdjukaar/jeed/plugin/emi/ingredient/EffectInstanceStack;)Lcom/google/gson/JsonElement;",
      at = @At("RETURN"),
      remap = false,
      require = 1)
  private void entrelumen$addEffectType(CallbackInfoReturnable<JsonElement> result) {
    JeedEffectJson.addTypeIfMissing(result.getReturnValue());
  }
}
