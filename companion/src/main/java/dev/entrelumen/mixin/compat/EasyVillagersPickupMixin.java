package dev.entrelumen.mixin.compat;

import dev.entrelumen.CommerceRules;
import dev.entrelumen.SolsticioCommerce;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Easy Villagers' pick-up keeps only a villager's own save data, so a Solsticio villager would lose
 * its role on the way: a shopkeeper copy without act gates, a native's Luminosity without its lock.
 * Only settled villagers and townsfolk may be picked up; the others travel by minecart or Carry On
 * (docs/design/solsticio-commerce.md). Both the client's sneak-click and the server's pick-up packet
 * ask this method; the role is only known on the server, which is the one that refuses.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.easyvillagers.events.VillagerEvents", remap = false)
public abstract class EasyVillagersPickupMixin {
  @Inject(
      method = "arePickupConditionsMet(Lnet/minecraft/world/entity/npc/Villager;)Z",
      at = @At("HEAD"),
      cancellable = true,
      remap = false,
      require = 0)
  private static void entrelumen$keepSolsticioRoles(Villager villager, CallbackInfoReturnable<Boolean> result) {
    CommerceRules.Role role = SolsticioCommerce.role(villager);
    if (role != null && role != CommerceRules.Role.MOVED && role != CommerceRules.Role.TOWNSFOLK)
      result.setReturnValue(false);
  }
}
