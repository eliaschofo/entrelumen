package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The same authoritative route serves chat commands and Atlas network actions. */
public final class CampaignActions {
  public enum Action {
    REFRESH,
    DELIVER,
    ADVANCE
  }

  public record Result(boolean success, String message) {}

  public static UUID campaignId(ServerPlayer player) {
    var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
    return team.isPartyTeam() ? team.getId() : player.getUUID();
  }

  public static Result perform(
      ServerPlayer player, UUID expectedCampaign, Action action, String project) {
    if (!campaignId(player).equals(expectedCampaign)) {
      player.sendSystemMessage(Component.translatable("entrelumen.atlas.stale"));
      return new Result(false, "entrelumen.atlas.stale");
    }
    return switch (action) {
      case REFRESH -> new Result(true, "");
      case DELIVER -> {
        boolean ok = Entrelumen.deliver(player, project) == 1;
        yield new Result(ok, ok ? "entrelumen.atlas.delivered" : "entrelumen.delivery.failed");
      }
      case ADVANCE -> {
        boolean ok = Entrelumen.advance(player) == 1;
        yield new Result(ok, ok ? "entrelumen.atlas.advanced" : "entrelumen.advance.failed");
      }
    };
  }
}
