package dev.entrelumen;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class CampaignData extends SavedData {
  public final Campaigns campaigns = new Campaigns();

  public static CampaignData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new Factory<>(CampaignData::new, CampaignData::load), "entrelumen_campaigns");
  }

  public static CampaignData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > 2)
      throw new IllegalStateException("Unsupported Entrelumen campaign version");
    CampaignData data = new CampaignData();
    read(tag.getCompound("personal"), data.campaigns.personal, tag.getInt("version"));
    read(tag.getCompound("parties"), data.campaigns.parties, tag.getInt("version"));
    return data;
  }

  private static void read(CompoundTag tag, Map<UUID, Campaigns.Campaign> target, int version) {
    for (String key : tag.getAllKeys()) {
      CompoundTag value = tag.getCompound(key);
      Campaigns.Campaign c = new Campaigns.Campaign();
      c.act = Math.clamp(value.getInt("act"), 1, 6);
      c.arkPhase = Math.clamp(value.getInt("arkPhase"), 0, 6);
      c.archived = value.getBoolean("archived");
      for (Tag milestone : value.getList("completed", Tag.TAG_STRING))
        c.completed.add(milestone.getAsString());
      if (version >= 2 && c.arkPhase < ArkCommissioning.STEPS.size()) {
        CompoundTag deposits = value.getCompound("arkDeposits");
        ArkCommissioning.STEPS.get(c.arkPhase).requirements().forEach((item, cost) -> {
          int count = Math.clamp(deposits.getInt(item), 0, cost);
          if (count > 0) c.arkDeposits.put(item, count);
        });
      }
      target.put(UUID.fromString(key), c);
    }
  }

  private static CompoundTag write(Map<UUID, Campaigns.Campaign> source) {
    CompoundTag tag = new CompoundTag();
    source.forEach(
        (id, c) -> {
          CompoundTag value = new CompoundTag();
          value.putInt("act", c.act);
          value.putInt("arkPhase", c.arkPhase);
          value.putBoolean("archived", c.archived);
          CompoundTag deposits = new CompoundTag();
          c.arkDeposits.forEach(deposits::putInt);
          value.put("arkDeposits", deposits);
          ListTag completed = new ListTag();
          c.completed.forEach(s -> completed.add(StringTag.valueOf(s)));
          value.put("completed", completed);
          tag.put(id.toString(), value);
        });
    return tag;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", 2);
    tag.put("personal", write(campaigns.personal));
    tag.put("parties", write(campaigns.parties));
    return tag;
  }
}
