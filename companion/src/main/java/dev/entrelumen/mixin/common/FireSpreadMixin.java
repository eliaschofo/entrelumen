package dev.entrelumen.mixin.common;

import dev.entrelumen.StructureProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fire neither burns nor spreads into blocks guarded by a Heliodor structure. */
@Mixin(FireBlock.class)
public abstract class FireSpreadMixin {
  @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true, require = 1)
  private void entrelumen$guardBurn(Level level, BlockPos pos, int chance, RandomSource random, int age,
      Direction face, CallbackInfo callback) {
    if (!StructureProtection.fireMaySpread(level, pos)) callback.cancel();
  }

  @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
      at = @At("HEAD"), cancellable = true, require = 1)
  private void entrelumen$guardIgnite(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
    if (level instanceof Level real && !StructureProtection.fireMaySpread(real, pos)) callback.setReturnValue(0);
  }
}
