package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ExpeditionsTest {
  private static final Map<String, String> DESTINATIONS = Map.of(
      "aether:the_aether", "aether_arrival",
      "twilightforest:twilight_forest", "twilight_arrival",
      "the_bumblezone:the_bumblezone", "bumblezone_arrival",
      "minecraft:the_end", "end_arrival");

  @Test
  void knownDestinationsRecordOnceInEveryActWithoutChangingOtherProgress() {
    assertEquals(Set.copyOf(DESTINATIONS.values()), Expeditions.IDS);
    assertThrows(UnsupportedOperationException.class, () -> Expeditions.IDS.add("other"));
    for (int act = 1; act <= 6; act++) {
      var campaign = new Campaigns.Campaign();
      campaign.act = act;
      campaign.arkPhase = 2;
      campaign.arkDeposits.put("entrelumen:ecosystem_capsule", 1);
      campaign.completed.add("exchange_route");
      for (var destination : DESTINATIONS.entrySet()) {
        assertTrue(Expeditions.record(campaign, destination.getKey()));
        assertTrue(campaign.completed.contains(destination.getValue()));
        assertFalse(Expeditions.record(campaign, destination.getKey()));
      }
      assertEquals(act, campaign.act);
      assertEquals(2, campaign.arkPhase);
      assertEquals(Map.of("entrelumen:ecosystem_capsule", 1), campaign.arkDeposits);
      assertEquals(Set.of("exchange_route", "aether_arrival", "twilight_arrival",
          "bumblezone_arrival", "end_arrival"), campaign.completed);
      assertFalse(campaign.archived);
    }
  }

  @Test
  void unknownDimensionsAndObservationIdsCannotFabricateJourneys() {
    var campaign = new Campaigns.Campaign();
    for (String dimension : Set.of("minecraft:overworld", "minecraft:the_nether",
        "aether:other", "the_bumblezone:bumblezone", "aether_arrival", "")) {
      assertFalse(Expeditions.record(campaign, dimension));
    }
    assertFalse(Expeditions.record(campaign, null));
    assertTrue(campaign.completed.isEmpty());
  }

  @Test
  void archivedCampaignRejectsEveryDestination() {
    var campaign = new Campaigns.Campaign();
    campaign.completed.add("exchange_route");
    campaign.archived = true;
    for (String dimension : DESTINATIONS.keySet()) assertFalse(Expeditions.record(campaign, dimension));
    assertEquals(Set.of("exchange_route"), campaign.completed);
    assertTrue(campaign.archived);
  }

  @Test
  void teamObservationsUseIndependentFounderSnapshotsAndNeverMergeOnJoin() {
    var campaigns = new Campaigns();
    UUID founder = UUID.randomUUID(), guest = UUID.randomUUID();
    UUID firstTeam = UUID.randomUUID(), secondTeam = UUID.randomUUID();
    var personal = campaigns.personal(founder);
    assertTrue(Expeditions.record(personal, "aether:the_aether"));
    var first = campaigns.party(firstTeam, founder);
    var second = campaigns.party(secondTeam, founder);
    assertTrue(Expeditions.record(campaigns.personal(guest), "the_bumblezone:the_bumblezone"));
    var current = campaigns.current(guest, firstTeam, founder);
    assertSame(first, current);
    assertTrue(Expeditions.record(current, "twilightforest:twilight_forest"));
    assertEquals(Set.of("aether_arrival", "twilight_arrival"), first.completed);
    assertEquals(Set.of("aether_arrival"), second.completed);
    assertEquals(Set.of("aether_arrival"), personal.completed);
    assertEquals(Set.of("bumblezone_arrival"), campaigns.current(guest, null, guest).completed);
    first.copy().completed.clear();
    assertEquals(Set.of("aether_arrival", "twilight_arrival"), first.completed);
  }

  @Test
  void observationsRoundTripInExistingLedgerWithArchiveAndArkProgress() {
    var data = new CampaignData();
    UUID founder = UUID.randomUUID(), team = UUID.randomUUID();
    var personal = data.campaigns.personal(founder);
    personal.act = CampaignMilestones.ARK_ACT;
    personal.arkPhase = 2;
    personal.arkDeposits.put("entrelumen:ecosystem_capsule", 1);
    personal.completed.add("atlas_voices");
    assertTrue(Expeditions.record(personal, "aether:the_aether"));
    var party = data.campaigns.party(team, founder);
    assertTrue(Expeditions.record(party, "the_bumblezone:the_bumblezone"));
    data.campaigns.archive(team);
    CompoundTag saved = data.save(new CompoundTag(), null);
    assertEquals(CampaignData.VERSION, saved.getInt("version"));
    var restored = CampaignData.load(saved, null);
    var restoredPersonal = restored.campaigns.personal(founder);
    var restoredParty = restored.campaigns.parties.get(team);
    assertEquals(personal.completed, restoredPersonal.completed);
    assertEquals(party.completed, restoredParty.completed);
    assertEquals(CampaignMilestones.ARK_ACT, restoredParty.act);
    assertEquals(2, restoredParty.arkPhase);
    assertEquals(personal.arkDeposits, restoredParty.arkDeposits);
    assertTrue(restoredParty.archived);
    assertFalse(Expeditions.record(restoredParty, "twilightforest:twilight_forest"));
    assertFalse(Expeditions.record(restoredPersonal, "aether:the_aether"));
    assertTrue(Expeditions.record(restoredPersonal, "twilightforest:twilight_forest"));
    assertFalse(restoredParty.completed.contains("twilight_arrival"));
    assertFalse(personal.completed.contains("twilight_arrival"));
  }

  @Test
  void solsticioCountsOnlyForTheTeamsOwnCrossing() {
    var visitor = new Campaigns.Campaign();
    visitor.act = 4;
    assertFalse(Expeditions.record(visitor, "entrelumen:solsticio"), "a visitor through another team's portal");
    var sixByHand = new Campaigns.Campaign();
    sixByHand.act = 6;
    assertFalse(Expeditions.record(sixByHand, "entrelumen:solsticio"), "act VI without the activation");
    var team = new Campaigns.Campaign();
    team.act = 6;
    team.completed.add(CampaignMilestones.LAST_HORIZON);
    assertTrue(Expeditions.record(team, "entrelumen:solsticio"));
    assertTrue(team.completed.contains(Expeditions.SOLSTICIO_ARRIVAL));
    assertFalse(Expeditions.record(team, "entrelumen:solsticio"));
    team.archived = true;
    team.completed.remove(Expeditions.SOLSTICIO_ARRIVAL);
    assertFalse(Expeditions.record(team, "entrelumen:solsticio"));
    assertFalse(Expeditions.IDS.contains(Expeditions.SOLSTICIO_ARRIVAL));
  }
}
