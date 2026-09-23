package dev.entrelumen;

import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-observed journeys, stored alongside the current campaign's completed milestones. */
public final class Expeditions {
  private static final Map<String, String> DIMENSIONS = Map.of(
      "aether:the_aether", "aether_arrival",
      "twilightforest:twilight_forest", "twilight_arrival",
      "the_bumblezone:the_bumblezone", "bumblezone_arrival",
      "minecraft:the_end", "end_arrival");
  public static final Set<String> IDS = Set.copyOf(DIMENSIONS.values());

  private Expeditions() {}

  public static boolean record(Campaigns.Campaign campaign, String dimension) {
    if (campaign.archived || dimension == null) return false;
    String observation = DIMENSIONS.get(dimension);
    return observation != null && campaign.completed.add(observation);
  }

  public static boolean observe(ServerPlayer player) {
    String dimension = player.serverLevel().dimension().location().toString();
    if (!DIMENSIONS.containsKey(dimension)) return false;
    if (!record(Entrelumen.current(player), dimension)) return false;
    CampaignData.get(player.server).setDirty();
    player.sendSystemMessage(Component.translatable("entrelumen.expedition.recorded",
        Component.translatable("entrelumen.project." + DIMENSIONS.get(dimension))));
    return true;
  }

  public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && !event.getFrom().equals(event.getTo())
        && player.serverLevel().dimension().equals(event.getTo())) observe(player);
  }

  public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) observe(player);
  }

  public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) observe(player);
  }
}
