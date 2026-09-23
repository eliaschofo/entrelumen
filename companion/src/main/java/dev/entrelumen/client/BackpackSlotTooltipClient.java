package dev.entrelumen.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "entrelumen", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BackpackSlotTooltipClient {
  private BackpackSlotTooltipClient() {}

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void onItemTooltip(ItemTooltipEvent event) {
    ItemStack stack = event.getItemStack();
    if (!stack.isEmpty()) {
      BackpackSlotTooltipDeduplicator.removeDuplicate(
          BuiltInRegistries.ITEM.getKey(stack.getItem()), event.getToolTip());
    }
  }
}
