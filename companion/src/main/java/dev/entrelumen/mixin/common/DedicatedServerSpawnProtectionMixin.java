package dev.entrelumen.mixin.common;

import dev.entrelumen.HeliodorRuins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The start ruin moves the world spawn beside its pedestal and the Envés gate. Vanilla spawn
 * protection (16 blocks by default on a dedicated server with operators) would refuse non-operators
 * the compass and the gate before any mod event fires, so the blocks tagged
 * {@code entrelumen:spawn_protection_exempt} are left out of it. They are all unbreakable.
 *
 * <p>Vanilla only checks the clicked block, so the exemption is withheld while the player sneaks
 * with something in hand: that click skips the block's own use and would place a block, pour a
 * bucket or light fire beside it instead. Without sneaking the block's use answers the click.
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerSpawnProtectionMixin {
  @Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true, require = 1)
  private void entrelumen$exemptRuinBlocks(ServerLevel level, BlockPos pos, Player player,
      CallbackInfoReturnable<Boolean> callback) {
    if (player != null && player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()))
      return;
    if (level.getBlockState(pos).is(HeliodorRuins.SPAWN_PROTECTION_EXEMPT)) callback.setReturnValue(false);
  }
}
