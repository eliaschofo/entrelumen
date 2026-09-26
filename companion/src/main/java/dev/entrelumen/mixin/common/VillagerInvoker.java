package dev.entrelumen.mixin.common;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's own start of trading ({@code Villager.startTrading}: special prices, trading player,
 * screen), for the Ark's remote trade with a Solsticio shop. A click at the counter runs the same
 * method through {@code mobInteract}.
 */
@Mixin(Villager.class)
public interface VillagerInvoker {
  @Invoker("startTrading")
  void entrelumen$startTrading(Player player);
}
