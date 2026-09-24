package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Projects the team campaign onto Apotheosis World Tier unlock advancements.
 *
 * <p>The pack overrides {@code apotheosis:progression/*} so Frontier to Pinnacle carry only an
 * impossible criterion (Haven keeps a tick criterion). This class grants the remaining criteria of
 * every tier the player's current campaign has reached, through the vanilla advancement API. It
 * only grants, never revokes: a World Tier stays chosen by the player, like any other
 * advancement. Absent advancements (Apotheosis not loaded, or a datapack removed one) are skipped.
 */
public final class ApotheosisTiers {
  public enum Tier {
    HAVEN("haven"),
    FRONTIER("frontier"),
    ASCENT("ascent"),
    SUMMIT("summit"),
    PINNACLE("pinnacle");

    public final ResourceLocation advancement;

    Tier(String name) {
      advancement = ResourceLocation.fromNamespaceAndPath("apotheosis", "progression/" + name);
    }
  }

  /** Campaign act reached after completing Act II, III and V respectively. */
  public static final int FRONTIER_ACT = 3, ASCENT_ACT = 4, SUMMIT_ACT = 6;

  static final int SYNC_INTERVAL = 20;

  private ApotheosisTiers() {}

  /** Pure rule. Haven is always open; later tiers follow the campaign and the Ark activation. */
  public static List<Tier> reached(Campaigns.Campaign campaign) {
    List<Tier> tiers = new ArrayList<>(List.of(Tier.HAVEN));
    if (campaign == null || campaign.archived) return List.copyOf(tiers);
    if (campaign.act >= FRONTIER_ACT) tiers.add(Tier.FRONTIER);
    if (campaign.act >= ASCENT_ACT) tiers.add(Tier.ASCENT);
    if (campaign.act >= SUMMIT_ACT) tiers.add(Tier.SUMMIT);
    if (campaign.completed.contains(CampaignMilestones.LAST_HORIZON)) tiers.add(Tier.PINNACLE);
    return List.copyOf(tiers);
  }

  /** Read-only lookup: never creates a campaign or marks SavedData dirty. */
  static Campaigns.Campaign campaignOf(ServerPlayer player) {
    var api = FTBTeamsAPI.api();
    if (!api.isManagerLoaded()) return null;
    var team = api.getManager().getTeamForPlayer(player);
    if (team.isEmpty()) return null;
    var campaigns = CampaignData.get(player.server).campaigns;
    return team.get().isPartyTeam()
        ? campaigns.parties.get(team.get().getId())
        : campaigns.personal.get(player.getUUID());
  }

  /** Grants every missing criterion of each reached tier; returns the number of criteria granted. */
  public static int sync(ServerPlayer player) {
    return grant(player, reached(campaignOf(player)));
  }

  static int grant(ServerPlayer player, List<Tier> tiers) {
    int granted = 0;
    var advancements = player.server.getAdvancements();
    for (Tier tier : tiers) {
      var holder = advancements.get(tier.advancement);
      if (holder == null) continue;
      var progress = player.getAdvancements().getOrStartProgress(holder);
      if (progress.isDone()) continue;
      List<String> remaining = new ArrayList<>();
      progress.getRemainingCriteria().forEach(remaining::add);
      for (String criterion : remaining)
        if (player.getAdvancements().award(holder, criterion)) granted++;
    }
    return granted;
  }

  public static void tick(ServerTickEvent.Post event) {
    if (event.getServer().getTickCount() % SYNC_INTERVAL != 7) return;
    for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) sync(player);
  }
}
