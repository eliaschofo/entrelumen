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
  /**
   * The team's own crossing into Solsticio, act VI's entry quest. Unlike the journeys above it only
   * counts for a campaign that passes Solsticio's gate (the Ark activated): visitors who come through
   * another team's portal or waystone do not record it.
   */
  public static final String SOLSTICIO_ARRIVAL = "solsticio_arrival";
  static final String SOLSTICIO = "entrelumen:solsticio";

  private Expeditions() {}

  public static boolean record(Campaigns.Campaign campaign, String dimension) {
    if (campaign.archived || dimension == null) return false;
    if (SOLSTICIO.equals(dimension))
      return campaign.act >= Campaigns.FINAL_ACT
          && campaign.completed.contains(CampaignMilestones.LAST_HORIZON)
          && campaign.completed.add(SOLSTICIO_ARRIVAL);
    String observation = DIMENSIONS.get(dimension);
    return observation != null && campaign.completed.add(observation);
  }

  public static boolean observe(ServerPlayer player) {
    String dimension = player.serverLevel().dimension().location().toString();
    String observation = SOLSTICIO.equals(dimension) ? SOLSTICIO_ARRIVAL : DIMENSIONS.get(dimension);
    if (observation == null) return false;
    if (!record(Entrelumen.current(player), dimension)) return false;
    CampaignData.get(player.server).setDirty();
    player.sendSystemMessage(Component.translatable("entrelumen.expedition.recorded",
        Component.translatable("entrelumen.project." + observation)));
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
