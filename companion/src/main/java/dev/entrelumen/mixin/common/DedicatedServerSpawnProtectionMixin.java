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
 * {@code entrelumen:spawn_protection_exempt} are left out of it. They are all unbreakable, and every
 * one has its own use handler that answers a main-hand click.
 *
 * <p>The check runs at RETURN and only when vanilla said "protected": vanilla's cheap early answers
 * (not the Overworld, no operators, an operator, radius 0, outside the radius) come first, so no
 * block state is read for them and no chunk is ever loaded by this mixin.
 *
 * <p>Vanilla only checks the clicked block, so the exemption is withheld while the player sneaks
 * with something in hand: that click skips the block's own use and would place a block, pour a
 * bucket or light fire beside it instead. Without sneaking the block's use answers the click.
 * Known limit: the check cannot see which hand a packet is for, and the off hand skips
 * useWithoutItem, so a modified client sending an off-hand click on an exempt block can still use
 * its off-hand item beside it. The Sealed Stair heart is not a block of its own (its cells are
 * template blocks), so it is not exempt: Frontier players open it by standing near it.
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerSpawnProtectionMixin {
  @Inject(method = "isUnderSpawnProtection", at = @At("RETURN"), cancellable = true, require = 1)
  private void entrelumen$exemptRuinBlocks(ServerLevel level, BlockPos pos, Player player,
      CallbackInfoReturnable<Boolean> callback) {
    if (!callback.getReturnValueZ()) return;
    if (player != null && player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()))
      return;
    if (level.getBlockState(pos).is(HeliodorRuins.SPAWN_PROTECTION_EXEMPT)) callback.setReturnValue(false);
  }
}
