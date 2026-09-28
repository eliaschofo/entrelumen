package dev.entrelumen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Atlas recipe (copper over a book) replaces a lost Atlas; it does not skip the story beat that
 * hands the Atlas over (Elias, 27 September). A player crafts one only when their team has taken the
 * Atlas from the Signal Tower's pedestal, or when their campaign is past act I: such a campaign got
 * its Atlas there, or from the first project before the Atlas moved to the tower. The crafting grids
 * ({@code CraftingMenuMixin}) ask here; machines that craft on their own do not.
 */
public final class AtlasGate {
  public static final String ATLAS = "entrelumen:atlas";

  private AtlasGate() {}

  /** Whether the grid's result is an Atlas this player may not make yet. */
  public static boolean blocks(ServerPlayer player, ItemStack result) {
    if (result.isEmpty() || !BuiltInRegistries.ITEM.getKey(result.getItem()).equals(ResourceLocation.parse(ATLAS)))
      return false;
    if (mayCraft(player)) return false;
    player.displayClientMessage(Component.translatable("entrelumen.atlas.recipe_locked").withStyle(ChatFormatting.GRAY), true);
    return true;
  }

  public static boolean mayCraft(ServerPlayer player) {
    var context = HeliodorCompass.context(player).orElse(null);
    if (context == null) return false;
    if (context.campaign().act >= 2) return true;
    var team = RuinProgress.get(player.server).peek(context.campaignId());
    if (team == null && context.founder() != null) team = RuinProgress.get(player.server).peek(context.founder());
    return team != null && team.gifts.contains(ATLAS);
  }
}
