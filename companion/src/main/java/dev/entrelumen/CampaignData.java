package dev.entrelumen;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class CampaignData extends SavedData {
  /**
   * 3 since 24 September 2026: acts were renumbered (the Ark moved from act 6 to act 5, Solsticio
   * is act 6). {@link #migrateActs} maps older saves once, when they are read. 4 since 25 September
   * 2026 (Ark v2): the Ark's batches are gone; supplies deposited into an unfinished batch become a
   * refund ({@link #migrateBatches}).
   */
  public static final int VERSION = 4;

  /**
   * The six Ark batches as they were (step, then item and cost), kept only to read older saves: an
   * unfinished batch's deposits, capped at its cost, are refunded.
   */
  static final java.util.List<Map<String, Integer>> LEGACY_BATCHES = java.util.List.of(
      Map.of("entrelumen:calibration_frame", 4, "entrelumen:power_regulator", 2),
      Map.of("entrelumen:containment_seal", 2),
      Map.of("entrelumen:ecosystem_capsule", 2),
      Map.of("entrelumen:routing_matrix", 2),
      Map.of("entrelumen:ration_bundle", 8),
      Map.of("entrelumen:horizon_chart", 1));

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
      c.archived = value.getBoolean("archived");
      for (Tag milestone : value.getList("completed", Tag.TAG_STRING))
        c.completed.add(milestone.getAsString());
      if (version < 4) migrateBatches(c, value, version);
      else {
        CompoundTag refunds = value.getCompound("refunds");
        for (String item : refunds.getAllKeys()) {
          int count = refunds.getInt(item);
          if (count > 0) c.refunds.put(item, count);
        }
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

  /**
   * Version 3 and earlier kept the Ark's current batch ({@code arkPhase}) and what was deposited into
   * it ({@code arkDeposits}, since version 2). The batches are gone: the unfinished batch's deposits,
   * capped at its cost as they were read before, become a refund. Finished batches are not refunded.
   */
  static void migrateBatches(Campaigns.Campaign c, CompoundTag value, int version) {
    int phase = Math.clamp(value.getInt("arkPhase"), 0, LEGACY_BATCHES.size());
    if (version < 2 || phase >= LEGACY_BATCHES.size()) return;
    CompoundTag deposits = value.getCompound("arkDeposits");
    LEGACY_BATCHES.get(phase).forEach((item, cost) -> {
      int count = Math.clamp(deposits.getInt(item), 0, cost);
      if (count > 0) c.refunds.merge(item, count, Integer::sum);
    });
  }

  private static CompoundTag write(Map<UUID, Campaigns.Campaign> source) {
    CompoundTag tag = new CompoundTag();
    source.forEach(
        (id, c) -> {
          CompoundTag value = new CompoundTag();
          value.putInt("act", c.act);
          value.putBoolean("archived", c.archived);
          CompoundTag refunds = new CompoundTag();
          c.refunds.forEach(refunds::putInt);
          value.put("refunds", refunds);
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
