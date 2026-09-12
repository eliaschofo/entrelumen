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
    var loaded = CampaignData.load(data.save(new CompoundTag(), null), null);
    assertEquals(1, loaded.campaigns.personal(player).act);
    assertEquals(6, loaded.campaigns.parties.get(team).act);
    assertEquals(3, loaded.campaigns.parties.get(team).arkPhase);
    assertTrue(loaded.campaigns.parties.get(team).archived);
    assertTrue(loaded.campaigns.personal(player).completed.contains("first_signal"));
  }

  @Test
  void rejectsFutureSchemaInsteadOfDestroyingData() {
    var tag = new CompoundTag();
    tag.putInt("version", 2);
    assertThrows(IllegalStateException.class, () -> CampaignData.load(tag, null));
  }
}
