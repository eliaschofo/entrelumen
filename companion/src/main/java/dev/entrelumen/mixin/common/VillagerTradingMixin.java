package dev.entrelumen.mixin.common;

import dev.entrelumen.SolsticioCommerce;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Solsticio prices ride on vanilla's per-session special prices: applied when a player starts
 * trading (after reputation and Hero of the Village) and undone with them when trading stops.
 */
@Mixin(Villager.class)
public abstract class VillagerTradingMixin {
  @Inject(method = "updateSpecialPrices", at = @At("TAIL"), require = 1)
  private void entrelumen$solsticioPrices(Player player, CallbackInfo callback) {
    SolsticioCommerce.onTradeOpen((Villager) (Object) this, player);
  }

  @Inject(method = "resetSpecialPrices", at = @At("TAIL"), require = 1)
  private void entrelumen$solsticioPricesReset(CallbackInfo callback) {
    SolsticioCommerce.onTradeClose((Villager) (Object) this);
  }
}
