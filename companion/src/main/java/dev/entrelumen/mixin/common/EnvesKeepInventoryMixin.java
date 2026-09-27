package dev.entrelumen.mixin.common;

import dev.entrelumen.EnvesDeaths;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A fall inside the Envés keeps the inventory: while that one death and its respawn are processed,
 * {@code keepInventory} reads true, so vanilla, Curios, backpacks and grave mods all keep exactly as
 * with the gamerule. See {@link EnvesDeaths}.
 */
@Mixin(GameRules.class)
public abstract class EnvesKeepInventoryMixin {
  @Inject(method = "getBoolean", at = @At("HEAD"), cancellable = true, require = 1)
  private void entrelumen$envesKeepsInventory(GameRules.Key<GameRules.BooleanValue> key,
      CallbackInfoReturnable<Boolean> callback) {
    if (key == GameRules.RULE_KEEPINVENTORY && EnvesDeaths.keepInventory()) callback.setReturnValue(true);
  }
}
