package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Copper medallion that carries one Apothic Spawners modifier. The effect itself lives in the
 * pack's {@code apothic_spawners:spawner_modifier} recipes, which name this item as the main-hand
 * ingredient; the item has no behaviour of its own.
 */
public final class SpawnerAugmentItem extends Item {
  public static final List<String> MODIFIERS = List.of(
      "burning", "echoing", "ignore_conditions", "ignore_light", "ignore_players",
      "initial_health", "max_delay", "max_nearby", "min_delay", "no_ai", "player_range",
      "redstone_control", "silent", "spawn_count", "spawn_range", "youthful");

  private final String modifier;

  public SpawnerAugmentItem(String modifier, Properties properties) {
    super(properties);
    this.modifier = modifier;
  }

  public static String id(String modifier) {
    return "augment_" + modifier;
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
      TooltipFlag flag) {
    tooltip.add(Component.translatable("item.entrelumen.augment." + modifier + ".effect")
        .withStyle(ChatFormatting.GOLD));
    tooltip.add(Component.translatable("item.entrelumen.augment.use").withStyle(ChatFormatting.GRAY));
    tooltip.add(Component.translatable("item.entrelumen.augment.inverse").withStyle(ChatFormatting.DARK_GRAY));
  }
}
