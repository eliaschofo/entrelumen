package dev.entrelumen;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.util.ProgressChange;
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
 * solo rewards the party had already claimed can still claim them after leaving.
 *
 * <p>Repeatable quests (the sector bounties) need their own rule, because a full claim there leaves no
 * claim to copy: FTB resets the quest and its claims and only bumps the team's completion count. The
 * personal data, meanwhile, still holds the completed and unclaimed copy the party started from. So the
 * guard snapshots the party's completion count of every repeatable quest at entry, and on leaving resets
 * the personal copy of each repeatable the party claimed since then (a higher count, or a claim dated
 * after the entry). That reset is the consumption the party claim already paid for; the personal cooldown
 * is left alone.
 *
 * <p>The entry record lives in server SavedData keyed by player UUID, never on the player, because an
 * offline kick has no player entity. Applying the same leave twice adds nothing: a claim already present
 * is never rewritten and a reset quest stays reset.
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
        since.put(playerId, System.currentTimeMillis(),
            repeatCounts(file, file.getNullableTeamData(current.getId())));
      } else if (previous.isPartyTeam() && current.isPlayerTeam()) {
        TeamData party = file.getOrCreateTeamData(previous);
        TeamData personal = file.getOrCreateTeamData(current);
        long entry = since.get(playerId);
        copyClaims(file, party, personal, playerId, entry);
        consumeRepeatables(file, party, personal, playerId, entry, since.counts(playerId));
        since.remove(playerId);
      }
    });
  }

  /** The completion count of every repeatable quest {@code party} has completed at least once. */
  static Map<Long, Integer> repeatCounts(ServerQuestFile file, TeamData party) {
    Map<Long, Integer> counts = new HashMap<>();
    if (party == null) return counts;
    file.forAllQuests(quest -> {
      if (!quest.canBeRepeated()) return;
      int count = party.getCompletionCount(quest);
      if (count > 0) counts.put(quest.getId(), count);
    });
    return counts;
  }

  /**
   * Resets in {@code to} each repeatable quest that {@code from} claimed since the entry: its completion
   * count grew past {@code entryCounts}, or one of its rewards carries a claim dated at or after
   * {@code since}. Returns how many quests were reset.
   */
  static int consumeRepeatables(ServerQuestFile file, TeamData from, TeamData to, UUID playerId,
      long since, Map<Long, Integer> entryCounts) {
    int[] reset = {0};
    file.forAllQuests(quest -> {
      if (!quest.canBeRepeated()) return;
      boolean claimedSince = from.getCompletionCount(quest) > entryCounts.getOrDefault(quest.getId(), 0)
          || quest.getRewards().stream().anyMatch(reward -> from.getRewardClaimTime(playerId, reward)
              .filter(time -> time.getTime() >= since).isPresent());
      if (!claimedSince || !holdsState(to, quest, playerId)) return;
      quest.forceProgressRaw(to, new ProgressChange(quest, playerId).setReset(true));
      reset[0]++;
    });
    return reset[0];
  }

  /** True when {@code data} holds any progress, completion or claim of {@code quest}. */
  private static boolean holdsState(TeamData data, Quest quest, UUID playerId) {
    return data.isStarted(quest) || data.isCompleted(quest)
        || quest.getTasks().stream().anyMatch(task -> data.getProgress(task) > 0)
        || quest.getRewards().stream().anyMatch(reward -> data.isRewardClaimed(playerId, reward));
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

  /**
   * When each player last entered a party, in epoch milliseconds, and the party's repeatable completion
   * counts at that moment. Absent means "not recorded".
   */
  static final class PartySince extends SavedData {
    private static final String NAME = "entrelumen_party_since";
    private static final String SINCE = "since", COUNTS = "counts";
    private final Map<UUID, Long> since = new HashMap<>();
    private final Map<UUID, Map<Long, Integer>> counts = new HashMap<>();

    static PartySince get(MinecraftServer server) {
      return server.overworld().getDataStorage()
          .computeIfAbsent(new Factory<>(PartySince::new, PartySince::load), NAME);
    }

    static PartySince load(CompoundTag tag, HolderLookup.Provider lookup) {
      PartySince data = new PartySince();
      for (String key : tag.getAllKeys()) {
        UUID player;
        try {
          player = UUID.fromString(key);
        } catch (IllegalArgumentException ignored) {
          continue; // A malformed key is dropped; the player then reads as "joined at 0", the strict side.
        }
        CompoundTag entry = tag.getCompound(key);
        data.since.put(player, entry.getLong(SINCE));
        CompoundTag saved = entry.getCompound(COUNTS);
        Map<Long, Integer> quests = new HashMap<>();
        for (String quest : saved.getAllKeys()) {
          try {
            quests.put(Long.parseUnsignedLong(quest, 16), saved.getInt(quest));
          } catch (NumberFormatException ignored) {
            // A malformed quest id reads as count 0: any later completion counts as claimed since entry.
          }
        }
        if (!quests.isEmpty()) data.counts.put(player, quests);
      }
      return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      since.forEach((id, time) -> {
        CompoundTag entry = new CompoundTag();
        entry.putLong(SINCE, time);
        CompoundTag quests = new CompoundTag();
        counts.getOrDefault(id, Map.of()).forEach((quest, count) ->
            quests.putInt(Long.toHexString(quest), count));
        if (!quests.isEmpty()) entry.put(COUNTS, quests);
        tag.put(id.toString(), entry);
      });
      return tag;
    }

    /** The recorded entry time, or 0 when none is recorded (every party claim then counts). */
    long get(UUID player) {
      return since.getOrDefault(player, 0L);
    }

    /** The party's repeatable completion counts at entry; empty when none is recorded. */
    Map<Long, Integer> counts(UUID player) {
      return counts.getOrDefault(player, Map.of());
    }

    void put(UUID player, long time, Map<Long, Integer> entryCounts) {
      since.put(player, time);
      if (entryCounts.isEmpty()) counts.remove(player);
      else counts.put(player, new HashMap<>(entryCounts));
      setDirty();
    }

    void remove(UUID player) {
      counts.remove(player);
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
