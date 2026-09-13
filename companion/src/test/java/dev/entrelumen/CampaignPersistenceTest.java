package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CampaignPersistenceTest {
  @Test
  void restartPreservesSnapshotsArchiveAndArkPhase() {
    var data = new CampaignData();
    UUID player = UUID.randomUUID(), team = UUID.randomUUID();
    data.campaigns.personal(player).completed.add("first_signal");
    var party = data.campaigns.party(team, player);
    party.act = 6;
    party.arkPhase = 3;
    party.archived = true;
    party.arkDeposits.put("entrelumen:routing_matrix", 1);
    var loaded = CampaignData.load(data.save(new CompoundTag(), null), null);
    assertEquals(1, loaded.campaigns.personal(player).act);
    assertEquals(6, loaded.campaigns.parties.get(team).act);
    assertEquals(3, loaded.campaigns.parties.get(team).arkPhase);
    assertTrue(loaded.campaigns.parties.get(team).archived);
    assertEquals(party.arkDeposits, loaded.campaigns.parties.get(team).arkDeposits);
    assertTrue(loaded.campaigns.personal(player).completed.contains("first_signal"));
  }

  @Test
  void legacySchemaPreservesEveryCreditedStepAndIgnoresLedger() {
    for (int phase = 0; phase <= 6; phase++) {
      var data = new CampaignData();
      UUID player = UUID.randomUUID();
      data.campaigns.personal(player).arkPhase = phase;
      data.campaigns.personal(player).arkDeposits.put("entrelumen:calibration_frame", 3);
      var tag = data.save(new CompoundTag(), null);
      tag.putInt("version", 1);
      var restored = CampaignData.load(tag, null).campaigns.personal(player);
      assertEquals(phase, restored.arkPhase);
      assertTrue(restored.arkDeposits.isEmpty());
    }
  }

  @Test
  void boundsCurrentStepDepositsAndDiscardsUnknownAndFinishedEntries() {
    var data = new CampaignData();
    UUID player = UUID.randomUUID();
    var campaign = data.campaigns.personal(player);
    campaign.arkDeposits.put("entrelumen:calibration_frame", 999);
    campaign.arkDeposits.put("entrelumen:power_regulator", -2);
    campaign.arkDeposits.put("unrelated", 9);
    var restored = CampaignData.load(data.save(new CompoundTag(), null), null).campaigns.personal(player);
    assertEquals(java.util.Map.of("entrelumen:calibration_frame", 4), restored.arkDeposits);
    campaign.arkPhase = 6;
    assertTrue(CampaignData.load(data.save(new CompoundTag(), null), null)
        .campaigns.personal(player).arkDeposits.isEmpty());
  }

  @Test
  void rejectsFutureSchemaInsteadOfDestroyingData() {
    var tag = new CompoundTag();
    tag.putInt("version", 3);
    assertThrows(IllegalStateException.class, () -> CampaignData.load(tag, null));
  }
}
