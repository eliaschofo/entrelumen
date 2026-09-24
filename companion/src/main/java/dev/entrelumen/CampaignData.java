package dev.entrelumen;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class CampaignData extends SavedData {
  /**
   * 3 since 24 September 2026: acts were renumbered (the Ark moved from act 6 to act 5, Solsticio
   * is act 6). {@link #migrateActs} maps older saves once, when they are read.
   */
  public static final int VERSION = 3;

  public final Campaigns campaigns = new Campaigns();

  public static CampaignData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new Factory<>(CampaignData::new, CampaignData::load), "entrelumen_campaigns");
  }

  public static CampaignData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION)
      throw new IllegalStateException("Unsupported Entrelumen campaign version");
    CampaignData data = new CampaignData();
    read(tag.getCompound("personal"), data.campaigns.personal, tag.getInt("version"));
    read(tag.getCompound("parties"), data.campaigns.parties, tag.getInt("version"));
    // Write the migrated numbering back at the next save.
    if (tag.getInt("version") < VERSION) data.setDirty();
    return data;
  }

  private static void read(CompoundTag tag, Map<UUID, Campaigns.Campaign> target, int version) {
    for (String key : tag.getAllKeys()) {
      CompoundTag value = tag.getCompound(key);
      Campaigns.Campaign c = new Campaigns.Campaign();
      c.act = Math.clamp(value.getInt("act"), 1, Campaigns.FINAL_ACT);
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
      if (version < 3) migrateActs(c);
      target.put(UUID.fromString(key), c);
    }
  }

  /**
   * Version 2 numbered the Ark act 6. A campaign there that has not activated the Ark is building it,
   * which is act 5 now; one that activated it stays in act 6, which is Solsticio. Acts 1-5 keep their
   * number (the old act 5, the industrial preparation, is the first half of the new act 5). The
   * campaign's modules, batches and deposits are untouched. World Tiers never drop: the story tier a
   * player already holds is kept by {@link ApotheosisTiers}.
   */
  static void migrateActs(Campaigns.Campaign c) {
    if (c.act == Campaigns.FINAL_ACT && !c.completed.contains(CampaignMilestones.LAST_HORIZON))
      c.act = CampaignMilestones.ARK_ACT;
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
    tag.putInt("version", VERSION);
    tag.put("personal", write(campaigns.personal));
    tag.put("parties", write(campaigns.parties));
    return tag;
  }
}
