package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CommerceRulesTest {
  private static final Path DATA = Path.of("src/main/resources/data/entrelumen");

  private static Map<String, CommerceRules.Table> tables(String directory, boolean natives) throws IOException {
    Map<String, CommerceRules.Table> tables = new TreeMap<>();
    try (Stream<Path> files = Files.list(DATA.resolve(directory))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
        String id = file.getFileName().toString().replace(".json", "");
        var parsed = CommerceRules.parse(id, JsonParser.parseString(Files.readString(file)), natives);
        assertEquals(List.of(), parsed.problems(), "Problems in " + file);
        tables.put(id, parsed.table());
      }
    }
    return tables;
  }

  private static CommerceRules.OfferSpec selling(CommerceRules.Table table, String item) {
    return table.offers().stream().filter(offer -> item.equals(offer.sell().item())).findFirst()
        .orElseThrow(() -> new AssertionError(table.id() + " does not sell " + item));
  }

  private static List<CommerceRules.OfferSpec> all(CommerceRules.Table table) {
    List<CommerceRules.OfferSpec> offers = new ArrayList<>(table.offers());
    if (table.luminosity() != null) offers.add(table.luminosity());
    return offers;
  }

  // ---- the real tables ------------------------------------------------------------------

  @Test
  void everyShopTypeHasATableThatParsesCleanly() throws IOException {
    var shops = tables("solsticio_shops", false);
    assertEquals(Set.copyOf(CommerceRules.SHOP_TYPES), shops.keySet());
    for (var table : shops.values()) {
      assertTrue(table.offers().size() >= 5, table.id() + " sells too little");
      assertFalse(table.profession().equals("minecraft:none"), table.id() + " has no profession");
    }
  }

  @Test
  void shopsSellWhatEliasAskedFor() throws IOException {
    var shops = tables("solsticio_shops", false);
    var books = shops.get("bookstore");
    assertTrue(books.offers().stream().anyMatch(offer -> offer.sell().enchantments().containsKey("minecraft:mending")),
        "The bookstore has no Mending");
    assertTrue(books.offers().stream().allMatch(offer -> offer.extra() != null && offer.extra().item().equals("minecraft:book")),
        "Enchanted books cost a book too");
    var rarities = shops.get("rarities");
    assertTrue(selling(rarities, "minecraft:elytra").gate().act() >= 6, "Elytra before the last act");
    assertTrue(selling(rarities, "minecraft:totem_of_undying").gate().act() >= 5, "Totems before a high act");
    for (String item : List.of("minecraft:shulker_shell", "minecraft:heart_of_the_sea", "minecraft:echo_shard",
        "minecraft:netherite_scrap"))
      selling(rarities, item);
    var smithy = shops.get("smithy");
    assertTrue(selling(smithy, "minecraft:netherite_upgrade_smithing_template").gate().act() >= 4);
    assertEquals(18, smithy.offers().stream().filter(offer -> offer.sell().item().endsWith("_armor_trim_smithing_template")).count());
    var records = shops.get("records");
    for (String disc : List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11",
        "wait", "otherside", "5", "pigstep", "relic", "creator", "creator_music_box", "precipice"))
      selling(records, "minecraft:music_disc_" + disc);
    assertTrue(records.offers().stream().anyMatch(offer -> "c:music_discs".equals(offer.sell().tag())),
        "Modded discs are not sold");
    var maps = shops.get("maps");
    assertTrue(maps.offers().stream().filter(offer -> offer.sell().survey() != null).count() >= 6);
    var museum = shops.get("museum");
    assertEquals(5, museum.offers().stream().filter(offer -> offer.sell().book() != null).count());
    assertEquals(23, museum.offers().stream().filter(offer -> offer.sell().item().endsWith("_pottery_sherd")).count());
  }

  @Test
  void nativesAskForDistinctBiomesAndSellTheirOwnLuminosity() throws IOException {
    var natives = tables("solsticio_natives", true);
    assertEquals(6, natives.size());
    Set<String> biomes = new HashSet<>();
    for (var discipline : LuminousRules.Discipline.values()) {
      var table = natives.get(discipline.id);
      assertNotNull(table, "No native for " + discipline.id);
      assertEquals("entrelumen:" + discipline.item(), table.luminosity().sell().item());
      assertEquals(ProtectionRules.ActGate.NONE, table.luminosity().gate());
      for (String biome : table.biomes()) assertTrue(biomes.add(biome), biome + " is asked by two natives");
    }
  }

  @Test
  void nothingIsSoldThatBreaksTheEconomyOrTheStory() throws IOException {
    List<CommerceRules.Table> tables = new ArrayList<>(tables("solsticio_shops", false).values());
    tables.addAll(tables("solsticio_natives", true).values());
    Set<String> forbidden = Set.of("minecraft:emerald", "minecraft:emerald_block", "minecraft:nether_star",
        "minecraft:dragon_egg", "minecraft:sponge", "minecraft:wet_sponge", "minecraft:beacon",
        "minecraft:wither_skeleton_skull", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:villager_spawn_egg",
        "entrelumen:luminous_ingot", "entrelumen:light_key", "entrelumen:light_key_broken", "entrelumen:heliodor_relic_1",
        "entrelumen:heliodor_relic_2", "entrelumen:heliodor_relic_3", "apotheosis:godforged_pearl",
        "naturesaura:sky_ingot", "mekanism:alloy_atomic", "draconicevolution:dragon_heart");
    for (var table : tables) {
      for (var offer : all(table)) {
        String item = offer.sell().item();
        if (item == null) continue;
        assertFalse(forbidden.contains(item), table.id() + " sells " + item);
        assertFalse(item.endsWith("_spawn_egg"), table.id() + " sells a spawn egg");
        assertFalse(item.startsWith("entrelumen:luminosity_") && offer != table.luminosity(),
            table.id() + " sells a Luminosity outside a native's offer");
        assertNotEquals(item, offer.price().item(), table.id() + " trades " + item + " for itself");
        assertTrue(offer.price().item().equals(CommerceRules.EMERALD) || offer.price().item().equals("minecraft:emerald_block"),
            table.id() + " is not priced in emeralds");
      }
    }
  }

  // ---- parsing --------------------------------------------------------------------------

  @Test
  void parsingDropsBadOffersAndReportsThem() {
    var json = JsonParser.parseString("""
        {"profession": "minecraft:librarian", "gate": {"act": 2},
         "offers": [
           {"sell": "minecraft:book", "price": 3},
           {"sell": {"id": "minecraft:elytra"}, "price": {"id": "minecraft:emerald_block", "count": 30}, "max_uses": 1,
            "gate": {"act": 6, "milestone": "last_horizon"}},
           {"sell": {"id": "minecraft:stone", "count": 65}, "price": 1},
           {"sell": {"id": "Bad Id"}, "price": 1},
           {"sell": {"tag": "c:music_discs", "id": "minecraft:stone"}, "price": 1},
           {"sell": {"id": "minecraft:map", "survey": {"structures": "minecraft:monument", "decoration": "minecraft:monument", "name": "x"}}, "price": 1},
           {"sell": "minecraft:stone"},
           {"sell": "minecraft:stone", "price": 1, "max_uses": 0}
         ]}""");
    var parsed = CommerceRules.parse("test", json, false);
    assertEquals(2, parsed.table().offers().size());
    assertEquals(6, parsed.problems().size(), parsed.problems().toString());
    var book = parsed.table().offers().getFirst();
    assertEquals(new CommerceRules.Cost(CommerceRules.EMERALD, 3), book.price());
    assertEquals(ProtectionRules.ActGate.act(2), book.gate(), "The table gate is the default");
    assertEquals(CommerceRules.DEFAULT_MAX_USES, book.maxUses());
    var elytra = parsed.table().offers().get(1);
    assertEquals(new ProtectionRules.ActGate(6, "last_horizon"), elytra.gate());
    assertEquals(new CommerceRules.Cost("minecraft:emerald_block", 30), elytra.price());
  }

  @Test
  void aNativeNeedsBiomesAndALuminosity() {
    var noBiome = CommerceRules.parse("arcane", JsonParser.parseString(
        "{\"luminosity\": {\"sell\": \"entrelumen:luminosity_arcane\", \"price\": 5}, \"offers\": []}"), true);
    assertNull(noBiome.table());
    var ok = CommerceRules.parse("arcane", JsonParser.parseString(
        "{\"biomes\": [\"#minecraft:is_jungle\", \"minecraft:swamp\"], \"luminosity\": {\"sell\": \"entrelumen:luminosity_arcane\", \"price\": 5}}"),
        true);
    assertTrue(ok.table().isNative());
    assertEquals(List.of("#minecraft:is_jungle", "minecraft:swamp"), ok.table().biomes());
    assertTrue(CommerceRules.parse("x", JsonParser.parseString("[]"), false).table() == null);
  }

  // ---- prices ---------------------------------------------------------------------------

  @Test
  void multipliersStackAndOnlyTouchMerchants() {
    var prices = CommerceRules.Prices.DEFAULT;
    assertEquals(1.0, CommerceRules.multiplier(CommerceRules.Role.SHOP, false, false, prices));
    assertEquals(0.6, CommerceRules.multiplier(CommerceRules.Role.SHOP, true, false, prices), 1e-9);
    assertEquals(0.5, CommerceRules.multiplier(CommerceRules.Role.MOVED, false, false, prices), 1e-9);
    assertEquals(0.3, CommerceRules.multiplier(CommerceRules.Role.MOVED, true, false, prices), 1e-9);
    assertEquals(1.0, CommerceRules.multiplier(CommerceRules.Role.NATIVE, false, false, prices), "A sleeping native");
    assertEquals(0.5, CommerceRules.multiplier(CommerceRules.Role.NATIVE, false, true, prices), 1e-9);
    assertEquals(1.0, CommerceRules.multiplier(CommerceRules.Role.TOWNSFOLK, true, true, prices));
    assertEquals(1.0, CommerceRules.multiplier(null, true, true, prices));
    assertThrows(IllegalArgumentException.class, () -> new CommerceRules.Prices(0, 0.5, 0.5));
  }

  @Test
  void discountsRoundAlwaysShowAndNeverReachZero() {
    assertEquals(24, CommerceRules.discounted(40, 0.6));
    assertEquals(10, CommerceRules.discounted(16, 0.6), "9.6 rounds to 10");
    assertEquals(1, CommerceRules.discounted(2, 0.6), "1.2 rounds to 1");
    assertEquals(9, CommerceRules.discounted(10, 0.95), "A discount always takes one off");
    assertEquals(1, CommerceRules.discounted(1, 0.3));
    assertEquals(40, CommerceRules.discounted(40, 1.0));
    for (double multiplier : new double[] {0.05, 0.3, 0.5, 0.6, 0.99})
      for (int price = 2; price <= 64; price++) {
        int discounted = CommerceRules.discounted(price, multiplier);
        assertTrue(discounted < price && discounted >= 1, multiplier + " does not lower " + price);
      }
  }

  @Test
  void priceAdjustmentLandsOnTheDiscountedVanillaCost() {
    // base 40, no demand: 40 -> 24
    assertEquals(-16, CommerceRules.priceAdjustment(40, 0, 0.05F, 0, 64, 0.6));
    // demand 4 at 0.05 adds floor(40 * 4 * 0.05) = 8: 48 -> 29
    assertEquals(29 - 48, CommerceRules.priceAdjustment(40, 4, 0.05F, 0, 64, 0.6));
    // a reputation discount of -10 is kept: 30 -> 18
    assertEquals(18 - 30, CommerceRules.priceAdjustment(40, 0, 0.05F, -10, 64, 0.6));
    // clamped to the stack: 60 + 20 = 80 shows as 64, which becomes 38
    int adjustment = CommerceRules.priceAdjustment(60, 20, 0.0167F, 0, 64, 0.6);
    assertEquals(38, Math.clamp(60 + (int) Math.floor((float) (60 * 20) * 0.0167F) + adjustment, 1, 64));
    assertEquals(0, CommerceRules.priceAdjustment(40, 3, 0.05F, 0, 64, 1.0), "No multiplier, no change");
  }

  @Test
  void restockIsLazyAndSurvivesClockResets() {
    assertTrue(CommerceRules.restockDue(100, 0, 24000), "Never stocked");
    assertFalse(CommerceRules.restockDue(10_000, 5_000, 24000));
    assertTrue(CommerceRules.restockDue(29_000, 5_000, 24000));
    assertTrue(CommerceRules.restockDue(1_000, 5_000, 24000), "Time went back");
  }

  // ---- places ---------------------------------------------------------------------------

  @Test
  void commonVillagersSpreadEvenlyUnderTheCap() {
    assertEquals(List.of(0, 1, 2), CommerceRules.spread(3, 40));
    assertEquals(List.of(0, 2, 5, 7), CommerceRules.spread(10, 4));
    assertEquals(40, new HashSet<>(CommerceRules.spread(137, 40)).size());
    assertEquals(List.of(), CommerceRules.spread(10, 0));
  }

  @Test
  void theHallZoneIsABoxAroundItsMarker() {
    assertTrue(CommerceRules.inHall(12, 70, -12, 0, 70, 0, 12));
    assertFalse(CommerceRules.inHall(13, 70, 0, 0, 70, 0, 12));
    assertTrue(CommerceRules.inHall(0, 68, 0, 0, 70, 0, 12));
    assertFalse(CommerceRules.inHall(0, 67, 0, 0, 70, 0, 12));
    assertFalse(CommerceRules.inHall(0, 70 + CommerceRules.HALL_UP + 1, 0, 0, 70, 0, 12));
  }

  @Test
  void innkeepersFollowTheNumericOrderOfTheirInns() {
    var ids = List.of("37_inn", "7_inn", "112_inn", "58_inn", "9_inn");
    assertEquals(0, CommerceRules.persona("7_inn", ids));
    assertEquals(1, CommerceRules.persona("9_inn", ids));
    assertEquals(2, CommerceRules.persona("37_inn", ids));
    assertEquals(3, CommerceRules.persona("58_inn", ids));
    assertEquals(0, CommerceRules.persona("112_inn", ids), "Personas wrap around");
  }

  @Test
  void sitesComeFromMarkersKeepTheirVillagersAndRoundTrip() {
    var markers = List.of(
        new CommerceSites.Found(CityLayout.Marker.SHOP, "bookstore", new BlockPos(5, 70, 9)),
        new CommerceSites.Found(CityLayout.Marker.SHOP, "rarities", new BlockPos(-5, 70, -9)),
        new CommerceSites.Found(CityLayout.Marker.SIDEQUEST, "7_inn", new BlockPos(0, 70, 30)),
        new CommerceSites.Found(CityLayout.Marker.RESIDENT, "", new BlockPos(1, 75, 1)),
        new CommerceSites.Found(CityLayout.Marker.RESIDENT, "", new BlockPos(2, 75, 2)),
        new CommerceSites.Found(CityLayout.Marker.RESIDENT, "", new BlockPos(3, 75, 3)),
        new CommerceSites.Found(CityLayout.Marker.EASTER, "coin", new BlockPos(9, 71, 9)),
        new CommerceSites.Found(CityLayout.Marker.EASTER, "coin", new BlockPos(9, 71, 10)));
    var sites = new CommerceSites();
    BlockPos hall = new BlockPos(0, 64, 38);
    sites.rebuild(markers, hall);
    assertEquals(2 + 1 + 3 + 6, sites.sites.size());
    assertEquals("rarities", sites.sites.getFirst().key, "Sites follow the north-to-south marker order");
    assertEquals(2, sites.easterEggs.get("coin").size());
    assertEquals(6, sites.sites.stream().filter(site -> site.role == CommerceRules.Role.NATIVE && site.pos.equals(hall)).count());
    assertEquals(2 + 1 + 2 + 6, sites.pending(2).size(), "The cap leaves one home empty");
    UUID keeper = UUID.randomUUID();
    sites.sites.getFirst().entity = keeper;
    sites.rebuild(markers, hall);
    assertEquals(keeper, sites.sites.getFirst().entity, "A re-run placement kept the villager");
    var loaded = new CommerceSites();
    loaded.load(sites.save());
    assertEquals(sites.save(), loaded.save());
    assertEquals(keeper, loaded.byEntity(keeper).entity);
    assertEquals(hall, loaded.hall);
    assertSame(loaded.sites.getFirst(), loaded.find(CommerceRules.Role.SHOP, "rarities", new BlockPos(-5, 70, -9)));
  }

  @Test
  void solsticioDataKeepsTheLiberationAndTheCommerce() {
    var data = new SolsticioData();
    data.liberated = true;
    data.commerce.rebuild(List.of(new CommerceSites.Found(CityLayout.Marker.SHOP, "maps", new BlockPos(1, 2, 3))),
        new BlockPos(0, 64, 0));
    var loaded = SolsticioData.load(data.save(new CompoundTag(), null), null);
    assertTrue(loaded.liberated);
    assertEquals(7, loaded.commerce.sites.size());
    loaded.resetCity();
    assertTrue(loaded.liberated && loaded.commerce.sites.isEmpty(), "A city reset keeps the liberation only");
    assertFalse(SolsticioData.load(new CompoundTag(), null).liberated, "Old saves start chained");
  }
}
