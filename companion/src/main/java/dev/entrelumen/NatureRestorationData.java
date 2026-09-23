package dev.entrelumen;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One restoration site per campaign identity: a party, or a player's personal campaign. */
public final class NatureRestorationData extends SavedData {
  static final String NAME = "entrelumen_nature_restoration";
  static final int VERSION = 1;
  private final Map<UUID, GlobalPos> sites = new HashMap<>();

  public static NatureRestorationData get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(
        new Factory<>(NatureRestorationData::new, NatureRestorationData::load), NAME);
  }

  GlobalPos site(UUID campaign) {
    return sites.get(campaign);
  }

  /** Returns false for a repeated mark, so reconnect retries neither dirty nor move anything. */
  boolean mark(UUID campaign, GlobalPos site) {
    if (site.equals(sites.get(campaign))) return false;
    sites.put(campaign, GlobalPos.of(site.dimension(), site.pos().immutable()));
    setDirty();
    return true;
  }

  int size() {
    return sites.size();
  }

  public static NatureRestorationData load(CompoundTag tag, HolderLookup.Provider lookup) {
    int version = tag.getInt("version");
    if (version > VERSION)
      throw new IllegalStateException("Unsupported Entrelumen nature restoration version " + version);
    var data = new NatureRestorationData();
    for (Tag raw : tag.getList("sites", Tag.TAG_COMPOUND)) {
      if (!(raw instanceof CompoundTag entry) || !entry.hasUUID("campaign")) continue;
      ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
      if (dimension == null || !entry.contains("x", Tag.TAG_INT) || !entry.contains("y", Tag.TAG_INT)
          || !entry.contains("z", Tag.TAG_INT)) continue;
      data.sites.putIfAbsent(entry.getUUID("campaign"), GlobalPos.of(
          ResourceKey.create(Registries.DIMENSION, dimension),
          new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"))));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    ListTag list = new ListTag();
    sites.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
        .forEach(entry -> {
          CompoundTag value = new CompoundTag();
          value.putUUID("campaign", entry.getKey());
          value.putString("dimension", entry.getValue().dimension().location().toString());
          value.putInt("x", entry.getValue().pos().getX());
          value.putInt("y", entry.getValue().pos().getY());
          value.putInt("z", entry.getValue().pos().getZ());
          list.add(value);
        });
    tag.put("sites", list);
    return tag;
  }
}
