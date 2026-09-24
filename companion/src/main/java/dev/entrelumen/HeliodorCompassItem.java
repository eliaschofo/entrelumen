package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Heliodor compass. The server refreshes its component about once a second while carried. */
public final class HeliodorCompassItem extends Item {
  static final int REFRESH_TICKS = 20;

  public HeliodorCompassItem(Properties properties) {
    super(properties);
  }

  @Override
  public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
    if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
    if (Math.floorMod(player.tickCount + slot, REFRESH_TICKS) != 0) return;
    HeliodorCompass.refresh(player, stack);
  }

  @Override
  public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
      TooltipFlag flag) {
    CompassState state = stack.get(HeliodorContent.COMPASS_STATE.get());
    if (state == null) {
      tooltip.add(Component.translatable("entrelumen.compass.tooltip.unattuned")
          .withStyle(ChatFormatting.GRAY));
      return;
    }
    if (!state.objective().isEmpty())
      tooltip.add(Component.translatable("entrelumen.compass.objective." + state.objective())
          .withStyle(ChatFormatting.GOLD));
    tooltip.add(Component.translatable("entrelumen.compass.state." + state.state())
        .withStyle(ChatFormatting.GRAY));
  }
}
