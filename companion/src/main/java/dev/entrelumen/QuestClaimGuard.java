package dev.entrelumen;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Keeps FTB Quests team rewards from being claimed twice across a party.
 *
 * <p>With {@code default_reward_team} every reward is a team reward, whose claim FTB keys to the nil
 * UUID. Creating a party copies the personal data, but leaving one merges back only the claims keyed to
 * the personal team id ({@code TeamData.mergeClaimedRewards}), so the party's nil claims are dropped and
 * the personal team offers the same rewards again: complete solo, create a party, claim, leave, claim
 * again. This guard records when a player entered a party and, when they go back to their personal team
 * (leave, kick or disband, online or not), marks in the personal data every team reward the party
 * claimed since then. Claims the party made before the player joined are not copied, so a joiner whose
 * solo rewards the party had already claimed can still claim them after leaving. Repeatable quests are
 * skipped: they are claimable again by design, and marking one claimed would reset its progress.
 *
 * <p>The join time lives in server SavedData keyed by player UUID, never on the player, because an
 * offline kick has no player entity. Applying the same leave twice adds nothing: a claim already present
 * is never rewritten.
 */
public final class QuestClaimGuard {
  private QuestClaimGuard() {}

  /** {@code TeamEvent.PLAYER_CHANGED}: records party entry and copies party claims back on leaving. */
  public static void onChanged(PlayerChangedTeamEvent event) {
    var previous = event.getPreviousTeam().orElse(null);
    var current = event.getTeam();
    UUID playerId = event.getPlayerId();
    if (previous == null || current == null || playerId == null) return;
    ServerQuestFile.getInstance().ifPresent(file -> {
      PartySince since = PartySince.get(file.server);
      if (previous.isPlayerTeam() && current.isPartyTeam()) {
        since.put(playerId, System.currentTimeMillis());
      } else if (previous.isPartyTeam() && current.isPlayerTeam()) {
        copyClaims(file, file.getOrCreateTeamData(previous), file.getOrCreateTeamData(current),
            playerId, since.get(playerId));
        since.remove(playerId);
      }
    });
  }

  /**
   * Marks in {@code to} each non-repeatable team reward that {@code from} claimed at or after
   * {@code since} and {@code to} has not claimed, keeping the original claim time. Returns how many
   * were marked.
   */
  static int copyClaims(ServerQuestFile file, TeamData from, TeamData to, UUID playerId, long since) {
    int[] marked = {0};
    file.forAllQuests(quest -> {
      if (quest.canBeRepeated()) return;
      for (Reward reward : quest.getRewards()) {
        if (!reward.isTeamReward()) continue;
        var claimed = from.getRewardClaimTime(playerId, reward);
        if (claimed.isEmpty() || claimed.get().getTime() < since) continue;
        if (to.isRewardClaimed(playerId, reward)) continue;
        if (to.markRewardAsClaimed(playerId, reward, claimed.get().getTime())) marked[0]++;
      }
    });
    return marked[0];
  }

  /** When each player last entered a party, in epoch milliseconds; absent means "not recorded". */
  static final class PartySince extends SavedData {
    private static final String NAME = "entrelumen_party_since";
    private final Map<UUID, Long> since = new HashMap<>();

    static PartySince get(MinecraftServer server) {
      return server.overworld().getDataStorage()
          .computeIfAbsent(new Factory<>(PartySince::new, PartySince::load), NAME);
    }

    static PartySince load(CompoundTag tag, HolderLookup.Provider lookup) {
      PartySince data = new PartySince();
      for (String key : tag.getAllKeys()) {
        try {
          data.since.put(UUID.fromString(key), tag.getLong(key));
        } catch (IllegalArgumentException ignored) {
          // A malformed key is dropped; the player then reads as "joined at 0", the strict side.
        }
      }
      return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      since.forEach((id, time) -> tag.putLong(id.toString(), time));
      return tag;
    }

    /** The recorded entry time, or 0 when none is recorded (every party claim then counts). */
    long get(UUID player) {
      return since.getOrDefault(player, 0L);
    }

    void put(UUID player, long time) {
      since.put(player, time);
      setDirty();
    }

    void remove(UUID player) {
      if (since.remove(player) != null) setDirty();
    }
  }

  /** Test hook: the recorded party entry time, or 0. */
  static long partyEntry(MinecraftServer server, UUID player) {
    return PartySince.get(server).get(player);
  }

  /** True when {@code quest} has a team reward; used by the GameTests to pick a fixture quest. */
  static boolean hasTeamReward(Quest quest) {
    return quest.getRewards().stream().anyMatch(Reward::isTeamReward);
  }
}
