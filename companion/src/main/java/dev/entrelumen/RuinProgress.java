package dev.entrelumen;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Each campaign's progress in the Heliodor ruins, persisted in {@code entrelumen_ruin_progress}:
 * solved challenges, lit nodes, filled sockets, the generation of every key piece handed over, the
 * ruins visited and the gifts a pedestal handed over. Keys are {@code <ruin id>|<challenge>}. A new party starts from its founder's
 * record, like its campaign and its compass.
 */
public final class RuinProgress extends SavedData {
  static final int VERSION = 1;

  public static final class Team {
    public final Set<String> solved = new TreeSet<>();
    public final Map<String, Set<Integer>> lit = new TreeMap<>();
    public final Map<String, Set<Integer>> filled = new TreeMap<>();
    /** Ruin id to the generation of the last piece its pedestal gave (absent: never). */
    public final Map<String, Integer> pieces = new TreeMap<>();
    public final Set<String> visited = new TreeSet<>();
    /** Items a pedestal handed to the team as gifts (the Signal Tower's Atlas): its recipe opens then. */
    public final Set<String> gifts = new TreeSet<>();

    Team copy() {
      Team copy = new Team();
      copy.solved.addAll(solved);
      lit.forEach((key, value) -> copy.lit.put(key, new TreeSet<>(value)));
      filled.forEach((key, value) -> copy.filled.put(key, new TreeSet<>(value)));
      copy.pieces.putAll(pieces);
      copy.visited.addAll(visited);
      copy.gifts.addAll(gifts);
      return copy;
    }

    public Set<Integer> lit(String key) {
      return lit.computeIfAbsent(key, ignored -> new TreeSet<>());
    }

    public Set<Integer> filled(String key) {
      return filled.computeIfAbsent(key, ignored -> new TreeSet<>());
    }

    public int piece(String ruin) {
      return pieces.getOrDefault(ruin, 0);
    }
  }

  private final Map<UUID, Team> teams = new HashMap<>();

  public static RuinProgress get(MinecraftServer server) {
    return server.overworld().getDataStorage()
        .computeIfAbsent(new Factory<>(RuinProgress::new, RuinProgress::load), "entrelumen_ruin_progress");
  }

  public static String key(String ruin, String challenge) {
    return ruin + "|" + challenge;
  }

  /** The campaign's record, created from the founder's on first use by a party. */
  public Team team(UUID campaign, @Nullable UUID founder) {
    Team existing = teams.get(campaign);
    if (existing != null) return existing;
    Team created = founder != null && !founder.equals(campaign) && teams.containsKey(founder)
        ? teams.get(founder).copy() : new Team();
    teams.put(campaign, created);
    setDirty();
    return created;
  }

  /** The record if it exists, without creating one (item ticks read it for any campaign). */
  @Nullable
  public Team peek(UUID campaign) {
    return teams.get(campaign);
  }

  /** Forgets one ruin for every team (an operator reset); returns how many teams changed. */
  public int forget(String ruin) {
    int changed = 0;
    String prefix = ruin + "|";
    for (Team team : teams.values()) {
      boolean touched = team.solved.removeIf(key -> key.startsWith(prefix));
      touched |= team.lit.keySet().removeIf(key -> key.startsWith(prefix));
      touched |= team.filled.keySet().removeIf(key -> key.startsWith(prefix));
      touched |= team.visited.remove(ruin);
      if (touched) changed++;
    }
    if (changed > 0) setDirty();
    return changed;
  }

  public static RuinProgress load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION)
      throw new IllegalStateException("Unsupported Entrelumen ruin progress version");
    RuinProgress data = new RuinProgress();
    CompoundTag teams = tag.getCompound("teams");
    for (String id : teams.getAllKeys()) {
      UUID campaign;
      try {
        campaign = UUID.fromString(id);
      } catch (IllegalArgumentException e) {
        continue;
      }
      CompoundTag entry = teams.getCompound(id);
      Team team = new Team();
      for (Tag value : entry.getList("solved", Tag.TAG_STRING)) team.solved.add(value.getAsString());
      for (Tag value : entry.getList("visited", Tag.TAG_STRING)) team.visited.add(value.getAsString());
      for (Tag value : entry.getList("gifts", Tag.TAG_STRING)) team.gifts.add(value.getAsString());
      readSets(entry.getCompound("lit"), team.lit);
      readSets(entry.getCompound("filled"), team.filled);
      CompoundTag pieces = entry.getCompound("pieces");
      for (String ruin : pieces.getAllKeys()) team.pieces.put(ruin, pieces.getInt(ruin));
      data.teams.put(campaign, team);
    }
    return data;
  }

  private static void readSets(CompoundTag tag, Map<String, Set<Integer>> target) {
    for (String key : tag.getAllKeys()) {
      Set<Integer> values = new TreeSet<>();
      for (int value : tag.getIntArray(key)) values.add(value);
      target.put(key, values);
    }
  }

  private static CompoundTag writeSets(Map<String, Set<Integer>> source) {
    CompoundTag tag = new CompoundTag();
    source.forEach((key, values) -> {
      if (!values.isEmpty()) tag.putIntArray(key, values.stream().mapToInt(Integer::intValue).toArray());
    });
    return tag;
  }

  private static ListTag strings(Collection<String> values) {
    ListTag list = new ListTag();
    for (String value : values) list.add(StringTag.valueOf(value));
    return list;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    CompoundTag teams = new CompoundTag();
    this.teams.forEach((campaign, team) -> {
      CompoundTag entry = new CompoundTag();
      entry.put("solved", strings(team.solved));
      entry.put("visited", strings(team.visited));
      entry.put("gifts", strings(team.gifts));
      entry.put("lit", writeSets(team.lit));
      entry.put("filled", writeSets(team.filled));
      CompoundTag pieces = new CompoundTag();
      team.pieces.forEach(pieces::putInt);
      entry.put("pieces", pieces);
      teams.put(campaign.toString(), entry);
    });
    tag.put("teams", teams);
    return tag;
  }
}
