package dev.entrelumen;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class ArkFieldJournalItem extends BlockItem {
  public ArkFieldJournalItem(Block block, Item.Properties properties) {
    super(block, properties);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context,
      List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.translatable("entrelumen.journal.tooltip"));
    if (getBlock() instanceof ArkFieldJournalBlock block && block.kind() == ArkFieldJournals.Kind.ARCANE)
      tooltip.add(Component.translatable("entrelumen.arcane.tooltip"));
    if (getBlock() instanceof ArkFieldJournalBlock block && block.kind() == ArkFieldJournals.Kind.HABITATION)
      tooltip.add(Component.translatable("entrelumen.habitation.tooltip"));
  }
}
