package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A module item: its global effect and where it goes, readable before it is placed. */
public final class ArkFieldJournalItem extends BlockItem {
  public ArkFieldJournalItem(Block block, Item.Properties properties) {
    super(block, properties);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context,
      List<Component> tooltip, TooltipFlag flag) {
    if (!(getBlock() instanceof ArkFieldJournalBlock block)) return;
    var module = block.kind().arkModule();
    tooltip.add(Component.translatable("entrelumen.ark.module.tooltip.effect", ArkFieldJournals.effectName(module))
        .withStyle(ChatFormatting.GOLD));
    tooltip.add(Component.translatable("entrelumen.ark.module.tooltip." + module.key()).withStyle(ChatFormatting.GRAY));
    tooltip.add(Component.translatable("entrelumen.ark.module.tooltip.place").withStyle(ChatFormatting.DARK_GRAY));
  }
}
