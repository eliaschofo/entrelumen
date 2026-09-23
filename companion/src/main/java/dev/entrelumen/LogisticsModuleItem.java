package dev.entrelumen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class LogisticsModuleItem extends BlockItem {
  public LogisticsModuleItem(Block block, Item.Properties properties) {
    super(block, properties);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context,
      List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.translatable("entrelumen.logistics.tooltip"));
    tooltip.add(Component.translatable("entrelumen.logistics.kit.tooltip"));
  }
}
