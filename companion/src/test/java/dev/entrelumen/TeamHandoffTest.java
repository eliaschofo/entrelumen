package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** What a party takes from its founder and gives back to its owner (F22, F31). */
class TeamHandoffTest {
  private static final ResourceKey<Level> OVERWORLD =
      ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
  private final UUID founder = UUID.randomUUID(), party = UUID.randomUUID();

  private static ArkData.Ark ark(int x) {
    var ark = new ArkData.Ark(OVERWORLD, new ArkMultiblock.Anchor(new BlockPos(x, 64, 0), 0));
    ark.core = ark.controller = true;
    return ark;
  }

  @Test
  void aNewPartyTakesTheFoundersArkAndShops() {
    var data = new ArkData();
    var ark = ark(0);
    data.arks.put(founder, ark);
    data.knownShops.put(founder, new TreeSet<>(Set.of("bakery@1")));
    assertTrue(TeamHandoff.arkToParty(data, party, founder));
    assertSame(ark, data.arks.get(party));
    assertFalse(data.arks.containsKey(founder));
    assertEquals(Set.of("bakery@1"), data.knownShops.get(party));
    assertEquals(Set.of("bakery@1"), data.knownShops.get(founder), "the founder's own list is copied, not moved");
    assertFalse(TeamHandoff.arkToParty(data, party, founder), "a replay changes nothing");
  }

  @Test
  void aPartyThatAlreadyHasAnArkKeepsIt() {
    var data = new ArkData();
    var mine = ark(0);
    var theirs = ark(100);
    data.arks.put(founder, mine);
    data.arks.put(party, theirs);
    TeamHandoff.arkToParty(data, party, founder);
    assertSame(theirs, data.arks.get(party));
  }

  @Test
  void disbandingHandsTheArkToAnOwnerWithoutOne() {
    var data = new ArkData();
    var ark = ark(0);
    data.arks.put(party, ark);
    assertTrue(TeamHandoff.arkToOwner(data, party, founder));
    assertSame(ark, data.arks.get(founder));
    assertFalse(data.arks.containsKey(party));

    var own = ark(100);
    data.arks.put(party, ark(200));
    data.arks.put(founder, own);
    assertTrue(TeamHandoff.arkToOwner(data, party, founder));
    assertSame(own, data.arks.get(founder), "an owner with a personal Ark keeps it");
    assertFalse(data.arks.containsKey(party), "the party's record is dropped; its blocks may found a new Ark");
    assertFalse(TeamHandoff.arkToOwner(data, party, founder));
  }

  @Test
  void aNewPartyTakesTheFoundersPlotAndForgedKey() {
    var data = new SolsticioData();
    var plot = new SolsticioData.Plot(new BlockPos(-25, 69, -25));
    plot.owner = founder;
    data.plots.add(plot);
    data.plots.add(new SolsticioData.Plot(new BlockPos(10, 69, -25)));
    data.forgedKeys.add(founder);
    assertTrue(TeamHandoff.solsticioToParty(data, party, founder));
    assertEquals(party, plot.owner);
    assertTrue(data.forgedKeys.containsAll(Set.of(founder, party)));
    assertFalse(TeamHandoff.solsticioToParty(data, party, founder));
  }

  @Test
  void disbandingReturnsThePlotOrFreesIt() {
    var data = new SolsticioData();
    var partyPlot = new SolsticioData.Plot(new BlockPos(-25, 69, -25));
    partyPlot.owner = party;
    partyPlot.ownerName = "Founder";
    partyPlot.claimedAt = 40;
    data.plots.add(partyPlot);
    var other = new SolsticioData.Plot(new BlockPos(10, 69, -25));
    data.plots.add(other);
    assertTrue(SolsticioCity.releaseCampaign(data, party, founder));
    assertEquals(founder, partyPlot.owner, "an owner without a plot gets the party's");
    assertFalse(SolsticioCity.releaseCampaign(data, party, founder));

    partyPlot.owner = party;
    other.owner = founder;
    assertTrue(SolsticioCity.releaseCampaign(data, party, founder));
    assertNull(partyPlot.owner, "an owner with a plot leaves the party's free");
    assertEquals("", partyPlot.ownerName);
    assertEquals(0, partyPlot.claimedAt);
    assertEquals(founder, other.owner);
  }

  @Test
  void aNewPartyKeepsTheFoundersGardenSite() {
    var data = new NatureRestorationData();
    var site = GlobalPos.of(OVERWORLD, new BlockPos(3, 70, 4));
    data.mark(founder, site);
    assertTrue(data.copyIfAbsent(founder, party));
    assertEquals(site, data.site(party));
    var elsewhere = GlobalPos.of(OVERWORLD, new BlockPos(30, 70, 40));
    var later = UUID.randomUUID();
    data.mark(later, elsewhere);
    assertFalse(data.copyIfAbsent(founder, later), "a party with a site keeps it");
    assertEquals(elsewhere, data.site(later));
    assertFalse(data.copyIfAbsent(UUID.randomUUID(), UUID.randomUUID()));
  }
}
