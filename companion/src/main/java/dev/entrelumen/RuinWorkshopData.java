package dev.entrelumen;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the Sunken Workshop's engine keeps across restarts, per placed ruin: whether a team claimed
 * the piece (the ruin then resets for the next team once it has been empty long enough), since when
 * it is empty, and who placed each part in the engine room or a wheelhouse socket, so that the reset
 * gives parts back to their placer.
 */
public final class RuinWorkshopData extends SavedData {
  static final String NAME = "entrelumen_ruin_workshop";

  static final class Entry {
    boolean pending;
    long emptySince = -1;
    final Map<Long, UUID> placers = new HashMap<>();
  }

  private final Map<ResourceLocation, Entry> entries = new HashMap<>();

  public static RuinWorkshopData get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(
        new Factory<>(RuinWorkshopData::new, RuinWorkshopData::load), NAME);
  }

  Entry entry(ResourceLocation ruin) {
    return entries.computeIfAbsent(ruin, ignored -> new Entry());
  }

  Optional<Entry> peek(ResourceLocation ruin) {
    return Optional.ofNullable(entries.get(ruin));
  }

  void forget(ResourceLocation ruin) {
    if (entries.remove(ruin) != null) setDirty();
  }

  static RuinWorkshopData load(CompoundTag tag, HolderLookup.Provider registries) {
    var data = new RuinWorkshopData();
    for (Tag element : tag.getList("ruins", Tag.TAG_COMPOUND)) {
      var ruin = (CompoundTag) element;
      var id = ResourceLocation.tryParse(ruin.getString("id"));
      if (id == null) continue;
      var entry = data.entry(id);
      entry.pending = ruin.getBoolean("pending");
      entry.emptySince = ruin.contains("emptySince") ? ruin.getLong("emptySince") : -1;
      for (Tag placed : ruin.getList("placers", Tag.TAG_COMPOUND)) {
        var p = (CompoundTag) placed;
        if (p.hasUUID("who")) entry.placers.put(p.getLong("pos"), p.getUUID("who"));
      }
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    var list = new ListTag();
    for (var entry : new TreeMap<>(entries).entrySet()) {
      var ruin = new CompoundTag();
      ruin.putString("id", entry.getKey().toString());
      ruin.putBoolean("pending", entry.getValue().pending);
      ruin.putLong("emptySince", entry.getValue().emptySince);
      var placers = new ListTag();
      entry.getValue().placers.forEach((pos, who) -> {
        var p = new CompoundTag();
        p.putLong("pos", pos);
        p.putUUID("who", who);
        placers.add(p);
      });
      ruin.put("placers", placers);
      list.add(ruin);
    }
    tag.put("ruins", list);
    return tag;
  }
}
