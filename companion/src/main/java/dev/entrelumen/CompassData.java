package dev.entrelumen;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Heliodor compass state: who already took a compass, which objectives each campaign reached, and
 * world facts found by searches (sites are shared by every team because worldgen is shared).
 */
public final class CompassData extends SavedData {
  static final int VERSION = 1;
  static final int MAX_FOUND = 32;
  static final int MAX_EMPTY = 64;
  /** Two finds closer than this are the same site. */
  static final int SAME_SITE = 48;

  public static final class Sites {
    public final List<BlockPos> found = new ArrayList<>();
    /** Search origins whose whole radius held no match. */
    public final List<BlockPos> emptyOrigins = new ArrayList<>();
  }

  public final Set<UUID> claimed = new HashSet<>();
  public final Map<UUID, Set<String>> reached = new HashMap<>();
  public final Map<String, Sites> sites = new HashMap<>();

  public static CompassData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(new Factory<>(CompassData::new, CompassData::load), "entrelumen_compass");
  }

  /** A new party starts from its founder's personal record, like the campaign itself. */
  public Set<String> reached(UUID campaign, @Nullable UUID founder) {
    Set<String> existing = reached.get(campaign);
    if (existing != null) return existing;
    Set<String> created = new TreeSet<>();
    if (founder != null && !founder.equals(campaign) && reached.containsKey(founder))
      created.addAll(reached.get(founder));
    reached.put(campaign, created);
    setDirty();
    return created;
  }

  public Sites sites(String key) {
    return sites.computeIfAbsent(key, ignored -> new Sites());
  }

  public void addFound(String key, BlockPos pos) {
    Sites entry = sites(key);
    for (BlockPos known : entry.found)
      if (horizontalDistanceSqr(known, pos) <= (long) SAME_SITE * SAME_SITE) return;
    entry.found.add(pos.immutable());
    while (entry.found.size() > MAX_FOUND) entry.found.removeFirst();
    setDirty();
  }

  public void addEmpty(String key, BlockPos origin) {
    Sites entry = sites(key);
    entry.emptyOrigins.add(origin.immutable());
    while (entry.emptyOrigins.size() > MAX_EMPTY) entry.emptyOrigins.removeFirst();
    setDirty();
  }

  static long horizontalDistanceSqr(BlockPos a, BlockPos b) {
    long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
    return dx * dx + dz * dz;
  }

  public static CompassData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION)
      throw new IllegalStateException("Unsupported Entrelumen compass data version");
    CompassData data = new CompassData();
    for (Tag id : tag.getList("claimed", Tag.TAG_INT_ARRAY)) data.claimed.add(NbtUtils.loadUUID(id));
    CompoundTag reached = tag.getCompound("reached");
    for (String key : reached.getAllKeys()) {
      Set<String> ids = new TreeSet<>();
      for (Tag id : reached.getList(key, Tag.TAG_STRING)) ids.add(id.getAsString());
      data.reached.put(UUID.fromString(key), ids);
    }
    for (Tag element : tag.getList("sites", Tag.TAG_COMPOUND)) {
      CompoundTag site = (CompoundTag) element;
      Sites entry = data.sites(site.getString("key"));
      for (long pos : site.getLongArray("found")) entry.found.add(BlockPos.of(pos));
      for (long pos : site.getLongArray("empty")) entry.emptyOrigins.add(BlockPos.of(pos));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    ListTag claimedList = new ListTag();
    claimed.stream().sorted().forEach(id -> claimedList.add(NbtUtils.createUUID(id)));
    tag.put("claimed", claimedList);
    CompoundTag reachedTag = new CompoundTag();
    reached.forEach(
        (campaign, ids) -> {
          ListTag list = new ListTag();
          ids.forEach(id -> list.add(StringTag.valueOf(id)));
          reachedTag.put(campaign.toString(), list);
        });
    tag.put("reached", reachedTag);
    ListTag siteList = new ListTag();
    new TreeMap<>(sites)
        .forEach(
            (key, entry) -> {
              CompoundTag site = new CompoundTag();
              site.putString("key", key);
              site.putLongArray("found", entry.found.stream().mapToLong(BlockPos::asLong).toArray());
              site.putLongArray(
                  "empty", entry.emptyOrigins.stream().mapToLong(BlockPos::asLong).toArray());
              siteList.add(site);
            });
    tag.put("sites", siteList);
    return tag;
  }
}
