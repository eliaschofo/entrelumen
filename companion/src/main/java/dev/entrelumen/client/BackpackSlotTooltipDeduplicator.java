package dev.entrelumen.client;

import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/** Removes only Accessories' Back slot line when Curios shows the same slot. */
final class BackpackSlotTooltipDeduplicator {
  private static final String BACKPACKS = "sophisticatedbackpacks";
  private static final Set<ResourceLocation> BACKPACK_ITEMS = Set.of(
      backpack("backpack"), backpack("copper_backpack"), backpack("iron_backpack"),
      backpack("gold_backpack"), backpack("diamond_backpack"), backpack("netherite_backpack"));

  private static final Style GRAY = Style.EMPTY.withColor(ChatFormatting.GRAY);
  private static final Style BLUE = Style.EMPTY.withColor(ChatFormatting.BLUE);
  private static final Style GOLD = Style.EMPTY.withColor(ChatFormatting.GOLD);
  private static final Style YELLOW = Style.EMPTY.withColor(ChatFormatting.YELLOW);

  private BackpackSlotTooltipDeduplicator() {}

  static boolean removeDuplicate(ResourceLocation itemId, List<Component> lines) {
    if (!BACKPACK_ITEMS.contains(itemId) || lines.stream().noneMatch(
        BackpackSlotTooltipDeduplicator::isCuriosBack)) return false;

    for (int i = 0; i < lines.size(); i++) {
      if (isAccessoriesBack(lines.get(i))) {
        lines.remove(i);
        return true;
      }
    }
    return false;
  }

  private static ResourceLocation backpack(String path) {
    return ResourceLocation.fromNamespaceAndPath(BACKPACKS, path);
  }

  private static boolean isAccessoriesBack(Component line) {
    if (!emptyLiteral(line, Style.EMPTY) || line.getSiblings().size() != 1) return false;
    Component header = line.getSiblings().get(0);
    if (header.getSiblings().size() != 1 || !header.getStyle().equals(GRAY)
        || !(key(header, "accessories.slot.tooltip.singular")
            || key(header, "accessories.slot.tooltip.plural"))) return false;

    Component slots = header.getSiblings().get(0);
    return emptyLiteral(slots, BLUE) && slots.getSiblings().size() == 1
        && bareKey(slots.getSiblings().get(0), "accessories.slot.back", Style.EMPTY);
  }

  private static boolean isCuriosBack(Component line) {
    return key(line, "curios.tooltip.slot") && line.getStyle().equals(GOLD)
        && line.getSiblings().size() == 2
        && literal(line.getSiblings().get(0), " ", Style.EMPTY)
        && line.getSiblings().get(0).getSiblings().isEmpty()
        && bareKey(line.getSiblings().get(1), "curios.identifier.back", YELLOW);
  }

  private static boolean emptyLiteral(Component line, Style style) {
    return literal(line, "", style);
  }

  private static boolean literal(Component line, String value, Style style) {
    return line.getContents() instanceof PlainTextContents text && text.text().equals(value)
        && line.getStyle().equals(style);
  }

  private static boolean bareKey(Component line, String translationKey, Style style) {
    return key(line, translationKey) && line.getStyle().equals(style)
        && line.getSiblings().isEmpty();
  }

  private static boolean key(Component line, String translationKey) {
    return line.getContents() instanceof TranslatableContents text
        && text.getKey().equals(translationKey) && text.getArgs().length == 0;
  }
}
