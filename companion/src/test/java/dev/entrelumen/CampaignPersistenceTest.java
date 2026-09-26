package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class CampaignPersistenceTest {
  @Test
  void restartPreservesSnapshotsArchiveAndRefunds() {
    var data = new CampaignData();
    UUID player = UUID.randomUUID(), team = UUID.randomUUID();
    data.campaigns.personal(player).completed.add("first_signal");
    var party = data.campaigns.party(team, player);
    party.act = 6;
    party.archived = true;
    party.refunds.put("entrelumen:routing_matrix", 1);
    var loaded = CampaignData.load(data.save(new CompoundTag(), null), null);
    assertEquals(1, loaded.campaigns.personal(player).act);
    assertEquals(6, loaded.campaigns.parties.get(team).act);
    assertTrue(loaded.campaigns.parties.get(team).archived);
    assertEquals(party.refunds, loaded.campaigns.parties.get(team).refunds);
    assertTrue(loaded.campaigns.personal(player).completed.contains("first_signal"));
    assertFalse(loaded.isDirty());
  }

  /** A campaign as versions 1 to 3 wrote it, with the Ark batch it was on and its deposits. */
  private static CompoundTag legacy(int version, UUID player, int act, int phase, Map<String, Integer> deposits,
      Set<String> completed) {
    CompoundTag value = new CompoundTag();
    value.putInt("act", act);
    value.putInt("arkPhase", phase);
    value.putBoolean("archived", false);
    CompoundTag ledger = new CompoundTag();
    deposits.forEach(ledger::putInt);
    value.put("arkDeposits", ledger);
    ListTag list = new ListTag();
    completed.forEach(id -> list.add(StringTag.valueOf(id)));
    value.put("completed", list);
    CompoundTag personal = new CompoundTag();
    personal.put(player.toString(), value);
    CompoundTag root = new CompoundTag();
    root.putInt("version", version);
    root.put("personal", personal);
    root.put("parties", new CompoundTag());
    return root;
  }

  /** Ark v2 (25 September 2026): the unfinished batch's deposits are owed back; finished ones are not. */
  @Test
  void version3BatchesBecomeRefundsOfTheUnfinishedBatchOnly() {
    UUID player = UUID.randomUUID();
    var loaded = CampaignData.load(legacy(3, player, 5, 2,
        Map.of("entrelumen:ecosystem_capsule", 1, "entrelumen:routing_matrix", 5), Set.of()), null);
    assertTrue(loaded.isDirty(), "the refund must be written back");
    assertEquals(Map.of("entrelumen:ecosystem_capsule", 1), loaded.campaigns.personal(player).refunds);
    // Capped at the batch's cost, as the old reader capped it.
    var capped = CampaignData.load(legacy(3, player, 5, 4, Map.of("entrelumen:ration_bundle", 99), Set.of()), null);
    assertEquals(Map.of("entrelumen:ration_bundle", 8), capped.campaigns.personal(player).refunds);
    // Every batch finished: nothing is owed.
    var done = CampaignData.load(legacy(3, player, 5, 6, Map.of("entrelumen:horizon_chart", 1), Set.of()), null);
    assertTrue(done.campaigns.personal(player).refunds.isEmpty());
    // Version 1 kept no ledger.
    var first = CampaignData.load(legacy(1, player, 5, 0, Map.of("entrelumen:calibration_frame", 3), Set.of()), null);
    assertTrue(first.campaigns.personal(player).refunds.isEmpty());
    // A refund is written as such and read back unchanged.
    var again = CampaignData.load(loaded.save(new CompoundTag(), null), null);
    assertEquals(Map.of("entrelumen:ecosystem_capsule", 1), again.campaigns.personal(player).refunds);
    assertFalse(again.isDirty());
  }

  @Test
  void aNewPartyNeverCopiesItsFoundersRefund() {
    var campaigns = new Campaigns();
    UUID founder = UUID.randomUUID(), team = UUID.randomUUID();
    campaigns.personal(founder).refunds.put("entrelumen:ecosystem_capsule", 1);
    assertTrue(campaigns.party(team, founder).refunds.isEmpty(), "a refund must be paid once");
    assertEquals(1, campaigns.personal(founder).refunds.get("entrelumen:ecosystem_capsule"));
  }

  @Test
  void rejectsFutureSchemaInsteadOfDestroyingData() {
    var tag = new CompoundTag();
    tag.putInt("version", CampaignData.VERSION + 1);
    assertThrows(IllegalStateException.class, () -> CampaignData.load(tag, null));
  }

  /** Version 2 numbered the Ark act 6; since 24 September 2026 it is act 5 and act 6 is Solsticio. */
  @Test
  void version2CampaignsMoveTheArkToActFiveAndKeepTheActivatedInSix() {
    UUID building = UUID.randomUUID();
    var loaded = CampaignData.load(legacy(2, building, 6, 3, Map.of("entrelumen:routing_matrix", 1),
        CampaignMilestones.MODULE_IDS), null);
    assertTrue(loaded.isDirty(), "the migrated numbering must be written back");
    var migrated = loaded.campaigns.personal(building);
    assertEquals(5, migrated.act);
    assertEquals(CampaignMilestones.MODULE_IDS, migrated.completed);
    assertEquals(Map.of("entrelumen:routing_matrix", 1), migrated.refunds);
    UUID activated = UUID.randomUUID();
    var done = CampaignData.load(legacy(2, activated, 6, 6, Map.of(), Set.of(CampaignMilestones.LAST_HORIZON)), null);
    assertEquals(6, done.campaigns.personal(activated).act);
    UUID preparing = UUID.randomUUID();
    assertEquals(5, CampaignData.load(legacy(2, preparing, 5, 0, Map.of(), Set.of()), null)
        .campaigns.personal(preparing).act);
    // Current saves are read as they are.
    var data = new CampaignData();
    data.campaigns.personal(building).act = 6;
    var current = CampaignData.load(data.save(new CompoundTag(), null), null);
    assertEquals(6, current.campaigns.personal(building).act);
    assertFalse(current.isDirty());
  }
}
