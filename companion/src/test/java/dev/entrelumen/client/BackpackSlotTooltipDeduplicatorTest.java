package dev.entrelumen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BackpackSlotTooltipDeduplicatorTest {
  private static final ResourceLocation BACKPACK = id("sophisticatedbackpacks:backpack");

  @Test
  void removesOnlyAccessoriesBackForBothPinnedHeaderForms() {
    for (String header : List.of("accessories.slot.tooltip.singular",
        "accessories.slot.tooltip.plural")) {
      for (String titleText : List.of("Backpack", "Mochila")) {
        Component title = Component.literal(titleText);
        Component accessories = accessories(header, "accessories.slot.back");
        Component curios = curios("curios.identifier.back", "Back");
        Component styledDetail = Component.literal("Extra detail").withStyle(ChatFormatting.LIGHT_PURPLE);
        Component unknown = Component.translatable("other.mod.backpack.note", "Back");
        List<Component> lines = new ArrayList<>(List.of(title, curios, accessories,
            styledDetail, unknown));

        assertTrue(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, lines));
        assertEquals(4, lines.size());
        assertSame(title, lines.get(0));
        assertSame(curios, lines.get(1));
        assertSame(styledDetail, lines.get(2));
        assertSame(unknown, lines.get(3));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), curios.getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW),
            curios.getSiblings().get(1).getStyle().getColor());
      }
    }
  }

  @Test
  void leavesDifferentSlotsAndCompoundLinesUnchanged() {
    Component accessoriesBack = accessories("accessories.slot.tooltip.plural",
        "accessories.slot.back");
    Component curiosBelt = curios("curios.identifier.belt", "Belt");
    List<Component> different = new ArrayList<>(List.of(accessoriesBack, curiosBelt));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, different));
    assertEquals(List.of(accessoriesBack, curiosBelt), different);

    MutableComponent accessoriesWithAnotherSlot = accessories(
        "accessories.slot.tooltip.plural", "accessories.slot.back");
    accessoriesWithAnotherSlot.getSiblings().get(0).getSiblings().get(0).getSiblings()
        .add(Component.translatable("accessories.slot.charm"));
    Component curiosBack = curios("curios.identifier.back", "Back");
    List<Component> compound = new ArrayList<>(List.of(accessoriesWithAnotherSlot, curiosBack));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, compound));
    assertEquals(2, compound.size());

    MutableComponent accessoriesWithHeaderDetail = accessories(
        "accessories.slot.tooltip.plural", "accessories.slot.back");
    accessoriesWithHeaderDetail.getSiblings().get(0).getSiblings()
        .add(Component.literal("extra"));
    List<Component> headerDetail = new ArrayList<>(List.of(accessoriesWithHeaderDetail, curiosBack));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, headerDetail));
    assertEquals(2, headerDetail.size());

    MutableComponent accessoriesWithSlotDetail = accessories(
        "accessories.slot.tooltip.plural", "accessories.slot.back");
    accessoriesWithSlotDetail.getSiblings().get(0).getSiblings().get(0).getSiblings().get(0)
        .getSiblings().add(Component.literal("extra"));
    List<Component> slotDetail = new ArrayList<>(List.of(accessoriesWithSlotDetail, curiosBack));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, slotDetail));
    assertEquals(2, slotDetail.size());

    MutableComponent curiosWithSlotDetail = curios("curios.identifier.back", "Back");
    curiosWithSlotDetail.getSiblings().get(1).getSiblings().add(Component.literal("extra"));
    List<Component> curiosSlotDetail = new ArrayList<>(
        List.of(accessoriesBack, curiosWithSlotDetail));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, curiosSlotDetail));
    assertEquals(2, curiosSlotDetail.size());

    curiosBack.getSiblings().get(1).getSiblings().add(Component.literal(", "));
    curiosBack.getSiblings().add(Component.translatableWithFallback(
        "curios.identifier.charm", "Charm").withStyle(ChatFormatting.YELLOW));
    List<Component> curiosCompound = new ArrayList<>(List.of(accessoriesBack, curiosBack));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, curiosCompound));
    assertEquals(2, curiosCompound.size());
  }

  @Test
  void leavesSingleProviderAndOtherItemsUnchanged() {
    Component accessories = accessories("accessories.slot.tooltip.plural",
        "accessories.slot.back");
    Component curios = curios("curios.identifier.back", "Back");
    List<Component> onlyAccessories = new ArrayList<>(List.of(accessories));
    List<Component> onlyCurios = new ArrayList<>(List.of(curios));
    List<Component> otherItem = new ArrayList<>(List.of(accessories, curios));

    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, onlyAccessories));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(BACKPACK, onlyCurios));
    assertFalse(BackpackSlotTooltipDeduplicator.removeDuplicate(
        id("minecraft:bundle"), otherItem));
    assertSame(accessories, onlyAccessories.get(0));
    assertSame(curios, onlyCurios.get(0));
    assertEquals(List.of(accessories, curios), otherItem);
  }

  private static ResourceLocation id(String id) {
    return ResourceLocation.parse(id);
  }

  // Pinned Accessories beta.48: empty root -> gray header -> blue empty body -> Back key.
  private static MutableComponent accessories(String header, String slot) {
    MutableComponent body = Component.literal("")
        .append(Component.translatable(slot)).withStyle(ChatFormatting.BLUE);
    return Component.literal("")
        .append(Component.translatable(header).withStyle(ChatFormatting.GRAY).append(body));
  }

  // Pinned Curios 9.5.1: gold key -> literal U+0020 -> yellow slot key.
  private static MutableComponent curios(String slot, String fallback) {
    return Component.translatable("curios.tooltip.slot").append(" ")
        .withStyle(ChatFormatting.GOLD)
        .append(Component.translatableWithFallback(slot, fallback)
            .withStyle(ChatFormatting.YELLOW));
  }
}
