package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Ark v2 rules: state, effects, homes, random travel and the remote catalog. */
class ArkRulesTest {
  private static final Set<String> ALL = Set.copyOf(ArkRules.moduleIds());

  @Test
  void oneModulePerActDeliveredByTheProjectOfItsName() {
    assertEquals(java.util.List.of(1, 2, 3, 4, 5, 5),
        java.util.Arrays.stream(ArkRules.Module.values()).map(module -> module.act).toList());
    for (var module : ArkRules.Module.values()) {
      assertEquals(module.id, module.project());
      assertSame(module, ArkRules.Module.byId(module.id));
    }
    assertEquals(7, ArkRules.SLOTS.size());
    assertEquals(ArkMultiblock.CONTROLLER, ArkRules.SLOTS.getFirst());
  }

  @Test
  void aModuleWorksInItsSlotOfAStandingArkWithItsController() {
    assertTrue(ArkRules.Status.NONE.activeModules().isEmpty());
    assertEquals(0, ArkRules.Status.NONE.level());
    var noCore = new ArkRules.Status(true, false, true, ALL, 4);
    var noController = new ArkRules.Status(true, true, false, ALL, 4);
    assertTrue(noCore.activeModules().isEmpty() && noController.activeModules().isEmpty(),
        "the core and the controller are both required (Elias)");
    var two = new ArkRules.Status(true, true, true, Set.of("nature_module", "arcane_module"), 0);
    assertEquals(Set.of(ArkRules.Module.NATURE, ArkRules.Module.ARCANE), two.activeModules());
    assertEquals(1, two.level());
    assertFalse(two.assembled());
    // One Ark per team: more beacons than places cannot add levels.
    assertEquals(5, new ArkRules.Status(true, true, true, ALL, 9).level());
    assertTrue(new ArkRules.Status(true, true, true, ALL, 0).assembled());
  }

  @Test
  void theActivationChecklistIsTheWholeArkTheLastProjectAndTheEnd() {
    var items = ArkRules.checklist(ArkRules.Status.NONE, Set.of());
    assertEquals(10, items.size());
    assertTrue(items.stream().noneMatch(ArkRules.Item::done));
    var whole = new ArkRules.Status(true, true, true, ALL, 0);
    assertFalse(ArkRules.checklistDone(whole, Set.of("world_network")));
    assertFalse(ArkRules.checklistDone(whole, Set.of("end_arrival")));
    assertTrue(ArkRules.checklistDone(whole, Set.of("world_network", "end_arrival")));
    assertFalse(ArkRules.checklistDone(new ArkRules.Status(true, true, false, ALL, 0),
        Set.of("world_network", "end_arrival")), "the controller is on the checklist");
    assertEquals(ArkRules.Check.CORE, items.getFirst().check());
    assertEquals(ArkRules.Check.JOURNEY, items.getLast().check());
  }

  @Test
  void wirelessChargingIsAShareOfCapacityWithAFloorAndACeiling() {
    var rate = ArkRules.Charging.DEFAULT;
    assertEquals(0.5, rate.percentPerSecond());
    // 0.5 % of 1,000,000 FE is 5,000 FE/s; two seconds of charging.
    assertEquals(10_000, ArkRules.chargeOffer(1_000_000, 0, rate, 2));
    // A small battery gets the floor of 100 FE/s.
    assertEquals(200, ArkRules.chargeOffer(10_000, 0, rate, 2));
    // A huge one stops at the ceiling of 10,000 FE/s.
    assertEquals(20_000, ArkRules.chargeOffer(100_000_000, 0, rate, 2));
    // Never more than it lacks, nothing for a full or empty-capacity item.
    assertEquals(50, ArkRules.chargeOffer(10_000, 9_950, rate, 2));
    assertEquals(0, ArkRules.chargeOffer(10_000, 10_000, rate, 2));
    assertEquals(0, ArkRules.chargeOffer(0, 0, rate, 2));
    assertEquals(Integer.MAX_VALUE, ArkRules.chargeOffer(Long.MAX_VALUE, 0,
        new ArkRules.Charging(100, 0, Integer.MAX_VALUE), 1e9));
  }

  @Test
  void oneHomeFifteenMinutesApartAfterFiveStillSecondsAndNeverInSolsticio() {
    assertEquals(15 * 60 * 20, ArkRules.HOME_COOLDOWN_TICKS);
    assertEquals(5 * 20, ArkRules.HOME_WARMUP_TICKS);
    assertEquals(0, ArkRules.cooldownLeft(1000, 0, ArkRules.HOME_COOLDOWN_TICKS));
    assertEquals(ArkRules.HOME_COOLDOWN_TICKS - 20, ArkRules.cooldownLeft(1020, 1000, ArkRules.HOME_COOLDOWN_TICKS));
    assertEquals(0, ArkRules.cooldownLeft(1000 + ArkRules.HOME_COOLDOWN_TICKS, 1000, ArkRules.HOME_COOLDOWN_TICKS));
    // A clock that went back (a restored world) never locks a player out.
    assertEquals(0, ArkRules.cooldownLeft(500, 1000, ArkRules.HOME_COOLDOWN_TICKS));
    assertFalse(ArkRules.moved(0.1, 0, 0.1));
    assertTrue(ArkRules.moved(0.3, 0, 0));
    assertTrue(ArkRules.moved(0, 0.5, 0));
    assertTrue(ArkRules.homeAllowed("minecraft:overworld"));
    assertTrue(ArkRules.homeAllowed("minecraft:the_nether"));
    assertFalse(ArkRules.homeAllowed("entrelumen:solsticio"));
  }

  @Test
  void randomTravelStaysInTheRingAndInTheExplorationDimensions() {
    assertEquals(60 * 60 * 20, ArkRules.RTP_COOLDOWN_TICKS);
    for (String dimension : Set.of("minecraft:overworld", "aether:the_aether", "twilightforest:twilight_forest",
        "the_bumblezone:the_bumblezone", "minecraft:the_end"))
      assertTrue(ArkRules.rtpAllowed(dimension), dimension);
    for (String dimension : Set.of("entrelumen:solsticio", "minecraft:the_nether", "undergarden:undergarden"))
      assertFalse(ArkRules.rtpAllowed(dimension), dimension);
    var random = new java.util.Random(7);
    for (int i = 0; i < 2000; i++) {
      long[] point = ArkRules.rtpCandidate(120, -40, random.nextDouble(), random.nextDouble());
      double distance = Math.hypot(point[0] - 120, point[1] + 40);
      assertTrue(distance >= ArkRules.RTP_MIN - 1 && distance <= ArkRules.RTP_MAX + 1, "distance " + distance);
    }
    long[] near = ArkRules.rtpCandidate(0, 0, 0, 0);
    assertEquals(ArkRules.RTP_MIN, near[0]);
    long[] far = ArkRules.rtpCandidate(0, 0, 0.25, 0.999999999);
    assertEquals(ArkRules.RTP_MAX, far[1], 1);
  }

  @Test
  void aShopIsKnownByItsTypeAndPostAndVisitedFromEightBlocks() {
    assertEquals("bakery@123", ArkRules.shopId("bakery", 123));
    assertTrue(ArkRules.visits(8, 4, 0));
    assertTrue(ArkRules.visits(5, -3, 5));
    assertFalse(ArkRules.visits(6, 0, 6));
    assertFalse(ArkRules.visits(0, 5, 0));
  }

  @Test
  void theArkStateHomesCooldownsAndKnownShopsSurviveARestart() {
    var data = new ArkData();
    UUID team = UUID.randomUUID(), player = UUID.randomUUID();
    ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    var ark = new ArkData.Ark(overworld, new ArkMultiblock.Anchor(new BlockPos(10, 64, -5), 3));
    ark.core = true;
    ark.controller = true;
    ark.modules.addAll(Set.of("nature_module", "logistics_module"));
    ark.missingCore = 0;
    ark.beacons = 2;
    data.arks.put(team, ark);
    data.homes.put(player, new ArkData.Home(overworld, 1.5, 70, -2.25, 90f, 10f));
    data.homeUsed.put(player, 1234L);
    data.rtpUsed.put(player, 99L);
    data.knownShops.put(team, new java.util.TreeSet<>(Set.of("bakery@1", "smithy@2")));
    var loaded = ArkData.load(data.save(new CompoundTag(), null), null);
    var back = loaded.arks.get(team);
    assertEquals(ark.anchor, back.anchor);
    assertEquals(overworld, back.dimension);
    assertEquals(ark.status(), back.status());
    assertEquals(2, back.beacons);
    assertEquals(data.homes, loaded.homes);
    assertEquals(Map.of(player, 1234L), loaded.homeUsed);
    assertEquals(Map.of(player, 99L), loaded.rtpUsed);
    assertEquals(Set.of("bakery@1", "smithy@2"), loaded.knownShops.get(team));
    assertEquals(team, loaded.owner(overworld, back.anchor));
    assertEquals(ArkRules.Status.NONE, loaded.status(UUID.randomUUID()));
    var future = new CompoundTag();
    future.putInt("version", ArkData.VERSION + 1);
    assertThrows(IllegalStateException.class, () -> ArkData.load(future, null));
  }
}
