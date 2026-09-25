package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Act VI's pure rules (docs/design/act-six.md); the runtime is in RuntimeGameTestsStory. */
class SolsticioStoryRulesTest {
  private static final SolsticioStoryRules.World CHAINED = new SolsticioStoryRules.World(false, false);
  private static final SolsticioStoryRules.World FREE = new SolsticioStoryRules.World(true, false);
  private static final SolsticioStoryRules.World VOTED = new SolsticioStoryRules.World(true, true);

  /** A campaign that activated the Ark and crossed with its own key. */
  private static Campaigns.Campaign arrived() {
    var c = new Campaigns.Campaign();
    c.act = Campaigns.FINAL_ACT;
    c.completed.add(CampaignMilestones.LAST_HORIZON);
    c.completed.add(SolsticioStoryRules.ARRIVAL);
    return c;
  }

  private static JsonObject json(String path) throws Exception {
    return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
  }

  @Test
  void theQuestChapterFollowsTheServerGraph() throws Exception {
    assertEquals(SolsticioStoryRules.MISSIONS, List.copyOf(SolsticioStoryRules.REQUIRES.keySet()));
    var chapter = json("../content/act_six.json");
    Map<String, String> milestoneOf = new LinkedHashMap<>();
    for (var element : chapter.getAsJsonArray("quests")) {
      var quest = element.getAsJsonObject();
      milestoneOf.put(quest.get("key").getAsString(), quest.get("milestone").getAsString());
    }
    milestoneOf.put("horizon_last", CampaignMilestones.LAST_HORIZON);
    List<String> milestones = new ArrayList<>();
    for (var element : chapter.getAsJsonArray("quests")) {
      var quest = element.getAsJsonObject();
      String milestone = quest.get("milestone").getAsString();
      milestones.add(milestone);
      List<String> deps = new ArrayList<>();
      quest.getAsJsonArray("deps").forEach(dep -> deps.add(milestoneOf.get(dep.getAsString())));
      assertEquals(SolsticioStoryRules.REQUIRES.get(milestone), deps, milestone);
    }
    assertEquals(SolsticioStoryRules.MISSIONS, milestones, "Eleven missions, in order, one quest each");
  }

  @Test
  void nothingHappensBeforeTheTeamCrossesWithItsOwnKey() {
    var building = new Campaigns.Campaign();
    building.act = 5;
    var visitor = new Campaigns.Campaign();
    visitor.act = 6;
    visitor.completed.add(CampaignMilestones.LAST_HORIZON);
    for (var c : List.of(building, visitor)) {
      assertFalse(SolsticioStoryRules.arrived(c));
      for (String mission : SolsticioStoryRules.MISSIONS) assertFalse(SolsticioStoryRules.record(c, mission), mission);
      assertFalse(SolsticioStoryRules.take(c, SolsticioStoryRules.Errand.BAKERY_HONEY));
      assertFalse(SolsticioStoryRules.discover(c, "tavern"));
      for (String character : SolsticioStoryRules.CHARACTERS)
        assertEquals("visitor", SolsticioStoryRules.stage(character, c, CHAINED, SolsticioStoryRules.HeartState.IN_ATLAS));
    }
    assertFalse(SolsticioStoryRules.record(arrived(), SolsticioStoryRules.ARRIVAL), "The crossing is observed, not recorded here");
  }

  @Test
  void missionsRecordOnceAndOnlyAfterTheirPrerequisites() {
    var c = arrived();
    assertFalse(SolsticioStoryRules.record(c, SolsticioStoryRules.SEEDS), "Juan before Aurelia");
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.MAYOR));
    assertFalse(SolsticioStoryRules.record(c, SolsticioStoryRules.MAYOR), "Recorded twice");
    assertFalse(SolsticioStoryRules.record(c, SolsticioStoryRules.HARVEST), "Harvest before seeds");
    for (String mission : List.of(SolsticioStoryRules.SEEDS, SolsticioStoryRules.POWER, SolsticioStoryRules.GOOD_WORKS))
      assertTrue(SolsticioStoryRules.record(c, mission), "Three branches after Aurelia: " + mission);
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.HARVEST));
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.TERRAPRISM));
    assertFalse(SolsticioStoryRules.record(c, SolsticioStoryRules.ACCORD), "The table without the blessing");
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.BLESSING));
    assertFalse(SolsticioStoryRules.record(c, SolsticioStoryRules.PORTAL), "The portal before the accord");
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.ACCORD));
    assertFalse(SolsticioStoryRules.record(c, SolsticioStoryRules.ELECTIONS), "Elections before the portal");
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.PORTAL));
    assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.ELECTIONS));
    var archived = arrived();
    archived.archived = true;
    assertFalse(SolsticioStoryRules.record(archived, SolsticioStoryRules.MAYOR), "An archived party changes nothing");
  }

  @Test
  void eightErrandsTwoPerInnBuildTheRelationWithBodhi() {
    Set<String> ids = new HashSet<>();
    for (int persona = 0; persona < CommerceRules.INNKEEPERS; persona++) {
      var errands = SolsticioStoryRules.errands(persona);
      assertEquals(2, errands.size(), "Two errands per innkeeper");
      errands.forEach(errand -> ids.add(errand.id));
    }
    assertEquals(8, ids.size());
    assertEquals(SolsticioStoryRules.errands(0), SolsticioStoryRules.errands(4), "Personas wrap like the inns");

    var c = arrived();
    var first = SolsticioStoryRules.current(c, 0);
    assertEquals(SolsticioStoryRules.Errand.BAKERY_HONEY, first);
    assertFalse(SolsticioStoryRules.complete(c, first), "An errand nobody asked for");
    assertTrue(SolsticioStoryRules.take(c, first));
    assertFalse(SolsticioStoryRules.take(c, first), "Taken twice");
    assertEquals(SolsticioStoryRules.ErrandState.TAKEN, SolsticioStoryRules.state(c, first));
    assertTrue(SolsticioStoryRules.complete(c, first));
    assertFalse(SolsticioStoryRules.complete(c, first), "Counted twice");
    assertEquals(1, SolsticioStoryRules.relation(c));
    assertEquals(SolsticioStoryRules.Errand.LOST_CAT, SolsticioStoryRules.current(c, 0), "The innkeeper's second errand");

    // Five errands before meeting Aurelia: Bodhi listens only once the story reaches him.
    for (var errand : List.of(SolsticioStoryRules.Errand.LOST_CAT, SolsticioStoryRules.Errand.LIBRARY_BOOK,
        SolsticioStoryRules.Errand.CHAPEL_CANDLES, SolsticioStoryRules.Errand.RIVER_TOOL)) {
      SolsticioStoryRules.take(c, errand);
      SolsticioStoryRules.complete(c, errand);
    }
    assertEquals(5, SolsticioStoryRules.relation(c));
    assertNull(SolsticioStoryRules.current(c, 0), "Dorotea has nothing left to ask");
    assertFalse(SolsticioStoryRules.done(c, SolsticioStoryRules.GOOD_WORKS));
    SolsticioStoryRules.record(c, SolsticioStoryRules.MAYOR);
    assertEquals("listening", SolsticioStoryRules.stage("priest", c, CHAINED, SolsticioStoryRules.HeartState.IN_ATLAS));
    assertTrue(SolsticioStoryRules.recordGoodWorks(c));

    // With Aurelia met first, the fifth errand records Good Works by itself.
    var d = arrived();
    SolsticioStoryRules.record(d, SolsticioStoryRules.MAYOR);
    int done = 0;
    for (var errand : SolsticioStoryRules.Errand.values()) {
      SolsticioStoryRules.take(d, errand);
      SolsticioStoryRules.complete(d, errand);
      assertEquals(++done >= SolsticioStoryRules.RELATION_NEEDED, SolsticioStoryRules.done(d, SolsticioStoryRules.GOOD_WORKS));
    }
    assertEquals(8, SolsticioStoryRules.relation(d));
  }

  @Test
  void theTableNeedsBothVoicesInEitherOrder() {
    for (String firstVoice : List.of("mayor", "priest")) {
      var c = arrived();
      for (String mission : List.of(SolsticioStoryRules.MAYOR, SolsticioStoryRules.SEEDS, SolsticioStoryRules.HARVEST,
          SolsticioStoryRules.POWER, SolsticioStoryRules.TERRAPRISM, SolsticioStoryRules.GOOD_WORKS))
        assertTrue(SolsticioStoryRules.record(c, mission));
      assertFalse(SolsticioStoryRules.hear(c, firstVoice), "Heard before the three relics");
      assertFalse(SolsticioStoryRules.heard(c, firstVoice));
      assertTrue(SolsticioStoryRules.record(c, SolsticioStoryRules.BLESSING));
      String other = firstVoice.equals("mayor") ? "priest" : "mayor";
      assertEquals("table", SolsticioStoryRules.stage(firstVoice, c, CHAINED, SolsticioStoryRules.HeartState.ELSEWHERE));
      assertFalse(SolsticioStoryRules.hear(c, firstVoice));
      assertEquals("table_waiting", SolsticioStoryRules.stage(firstVoice, c, CHAINED, SolsticioStoryRules.HeartState.ELSEWHERE));
      assertEquals("accord", SolsticioStoryRules.stage(other, c, CHAINED, SolsticioStoryRules.HeartState.ELSEWHERE));
      assertTrue(SolsticioStoryRules.hear(c, other));
      assertTrue(SolsticioStoryRules.done(c, SolsticioStoryRules.ACCORD));
    }
  }

  @Test
  void eachTeamPresentsItsOwnRelicsOnceAfterTheAccord() {
    var c = arrived();
    assertFalse(SolsticioStoryRules.presentRelic(c, 1), "Before the accord");
    for (String mission : List.of(SolsticioStoryRules.MAYOR, SolsticioStoryRules.SEEDS, SolsticioStoryRules.HARVEST,
        SolsticioStoryRules.POWER, SolsticioStoryRules.TERRAPRISM, SolsticioStoryRules.GOOD_WORKS,
        SolsticioStoryRules.BLESSING, SolsticioStoryRules.ACCORD))
      SolsticioStoryRules.record(c, mission);
    assertTrue(SolsticioStoryRules.presentRelic(c, 2));
    assertFalse(SolsticioStoryRules.presentRelic(c, 2), "The same relic twice");
    assertFalse(SolsticioStoryRules.presentRelic(c, 0));
    assertFalse(SolsticioStoryRules.presentRelic(c, 4));
    assertFalse(SolsticioStoryRules.openPortal(c), "Two relics short");
    assertTrue(SolsticioStoryRules.presentRelic(c, 1) && SolsticioStoryRules.presentRelic(c, 3));
    assertEquals(3, SolsticioStoryRules.relicsPresented(c));
    assertTrue(SolsticioStoryRules.openPortal(c));
    assertFalse(SolsticioStoryRules.openPortal(c));
    assertEquals("campaign", SolsticioStoryRules.stage("mayor", c, FREE, SolsticioStoryRules.HeartState.ELSEWHERE));
    assertEquals("elected", SolsticioStoryRules.stage("mayor", c, VOTED, SolsticioStoryRules.HeartState.ELSEWHERE));
  }

  @Test
  void easterEggsCountOnceAndAllThreeRevealTheRumour() {
    var c = arrived();
    assertFalse(SolsticioStoryRules.discover(c, "fountain_coin"), "Only the three story eggs");
    assertTrue(SolsticioStoryRules.discover(c, "tavern"));
    assertFalse(SolsticioStoryRules.discover(c, "tavern"), "Lore is given once");
    assertTrue(SolsticioStoryRules.discover(c, "sundial"));
    assertFalse(SolsticioStoryRules.revealRumour(c));
    assertTrue(SolsticioStoryRules.discover(c, "secret_garden"));
    assertTrue(SolsticioStoryRules.revealRumour(c));
    assertFalse(SolsticioStoryRules.revealRumour(c), "The clue is revealed once");
  }

  @Test
  void electionsComeThreeDaysOfGameTimeAfterTheLiberation() {
    long freed = 100_000;
    assertFalse(SolsticioStoryRules.electionDue(false, freed, freed + 10 * SolsticioStoryRules.DAY, false));
    assertFalse(SolsticioStoryRules.electionDue(true, 0, 10 * SolsticioStoryRules.DAY, false), "No date, no vote");
    assertFalse(SolsticioStoryRules.electionDue(true, freed, freed + SolsticioStoryRules.ELECTION_DELAY - 1, false));
    assertTrue(SolsticioStoryRules.electionDue(true, freed, freed + SolsticioStoryRules.ELECTION_DELAY, false));
    assertFalse(SolsticioStoryRules.electionDue(true, freed, freed + SolsticioStoryRules.ELECTION_DELAY, true), "Held once");
    assertEquals(3, SolsticioStoryRules.daysUntilElection(freed, freed));
    assertEquals(1, SolsticioStoryRules.daysUntilElection(freed, freed + SolsticioStoryRules.ELECTION_DELAY - 1));
    assertEquals(1, SolsticioStoryRules.daysUntilElection(freed, freed + SolsticioStoryRules.ELECTION_DELAY + 50));
    assertEquals("", SolsticioStoryRules.townsfolkPool(CHAINED));
    assertEquals("liberated.", SolsticioStoryRules.townsfolkPool(FREE));
    assertEquals("elected.", SolsticioStoryRules.townsfolkPool(VOTED));
  }

  @Test
  void deliveriesAskForVarietyAndAChargedBattery() {
    List<String> carried = List.of("a", "b", "a", "c", "d", "e", "f", "g", "b", "h", "i");
    assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h"), SolsticioStoryRules.distinct(carried, 8));
    assertEquals(List.of(), SolsticioStoryRules.distinct(List.of("a", "a", "a"), 2), "Quantity is not variety");
    Map<String, Integer> meals = new LinkedHashMap<>();
    meals.put("stew", 16);
    meals.put("soup", 15);
    meals.put("rice", 32);
    meals.put("pasta", 16);
    assertEquals(List.of(), SolsticioStoryRules.enough(meals, 4, 16), "Three kinds reach sixteen");
    meals.put("salmon", 20);
    assertEquals(List.of("stew", "rice", "pasta", "salmon"), SolsticioStoryRules.enough(meals, 4, 16));
    assertEquals(Map.of("coil", 3), SolsticioStoryRules.missing(Map.of("coil", 4, "plate", 2), Map.of("coil", 1, "plate", 5)));
    assertTrue(SolsticioStoryRules.charged(1_000_000, 1_000_000));
    assertTrue(SolsticioStoryRules.charged(90_000, 100_000));
    assertFalse(SolsticioStoryRules.charged(89_999, 100_000), "Nearly full means 90%");
    assertFalse(SolsticioStoryRules.charged(50_000, 50_000), "Too small to power Terra's work");
  }

  @Test
  void charactersFollowTheStory() {
    var c = arrived();
    var heart = SolsticioStoryRules.HeartState.IN_ATLAS;
    assertEquals("welcome", SolsticioStoryRules.stage("mayor", c, CHAINED, heart));
    for (String character : List.of("inventor", "gardener", "priest"))
      assertEquals("protocol", SolsticioStoryRules.stage(character, c, CHAINED, heart), character);
    SolsticioStoryRules.record(c, SolsticioStoryRules.MAYOR);
    assertEquals("wait_gardener", SolsticioStoryRules.stage("mayor", c, CHAINED, heart));
    assertEquals("seeds", SolsticioStoryRules.stage("gardener", c, CHAINED, heart));
    assertEquals("power", SolsticioStoryRules.stage("inventor", c, CHAINED, heart));
    assertEquals("works", SolsticioStoryRules.stage("priest", c, CHAINED, heart));
    SolsticioStoryRules.record(c, SolsticioStoryRules.SEEDS);
    SolsticioStoryRules.record(c, SolsticioStoryRules.POWER);
    assertEquals("harvest", SolsticioStoryRules.stage("gardener", c, CHAINED, heart));
    assertEquals("light", SolsticioStoryRules.stage("inventor", c, CHAINED, heart));
    SolsticioStoryRules.record(c, SolsticioStoryRules.GOOD_WORKS);
    assertEquals("spirit", SolsticioStoryRules.stage("priest", c, CHAINED, SolsticioStoryRules.HeartState.WITH_SPIRIT));
    assertEquals("release", SolsticioStoryRules.stage("priest", c, CHAINED, SolsticioStoryRules.HeartState.IN_ATLAS));
    assertEquals("blessing", SolsticioStoryRules.stage("priest", c, CHAINED, SolsticioStoryRules.HeartState.CARRIED));
    assertEquals("heart", SolsticioStoryRules.stage("priest", c, CHAINED, SolsticioStoryRules.HeartState.ELSEWHERE));
    SolsticioStoryRules.record(c, SolsticioStoryRules.BLESSING);
    assertEquals("wait", SolsticioStoryRules.stage("priest", c, CHAINED, heart), "Juan and Terra still hold their parts");
    assertEquals("wait_gardener", SolsticioStoryRules.stage("mayor", c, CHAINED, heart));
    SolsticioStoryRules.record(c, SolsticioStoryRules.HARVEST);
    assertEquals("wait_inventor", SolsticioStoryRules.stage("mayor", c, CHAINED, heart));
    SolsticioStoryRules.record(c, SolsticioStoryRules.TERRAPRISM);
    assertEquals("after", SolsticioStoryRules.stage("gardener", c, CHAINED, heart));
    assertEquals("after_elections", SolsticioStoryRules.stage("inventor", c, VOTED, heart));
    assertEquals("table", SolsticioStoryRules.stage("mayor", c, CHAINED, heart));
    SolsticioStoryRules.record(c, SolsticioStoryRules.ACCORD);
    assertEquals("portal", SolsticioStoryRules.stage("mayor", c, FREE, heart), "Another team's liberation is not this team's portal");
    assertEquals("after", SolsticioStoryRules.stage("priest", c, CHAINED, heart));
  }

  @Test
  void aPartyInheritsItsFoundersStory() {
    var campaigns = new Campaigns();
    UUID founder = UUID.randomUUID();
    var solo = campaigns.personal(founder);
    solo.act = Campaigns.FINAL_ACT;
    solo.completed.add(CampaignMilestones.LAST_HORIZON);
    solo.completed.add(SolsticioStoryRules.ARRIVAL);
    SolsticioStoryRules.record(solo, SolsticioStoryRules.MAYOR);
    SolsticioStoryRules.take(solo, SolsticioStoryRules.Errand.RIVER_TOOL);
    SolsticioStoryRules.complete(solo, SolsticioStoryRules.Errand.RIVER_TOOL);
    SolsticioStoryRules.discover(solo, "sundial");
    var party = campaigns.party(UUID.randomUUID(), founder);
    assertTrue(SolsticioStoryRules.done(party, SolsticioStoryRules.MAYOR));
    assertEquals(1, SolsticioStoryRules.relation(party));
    assertTrue(SolsticioStoryRules.discovered(party, "sundial"));
  }

  @Test
  void theNamedCharactersHaveSitesInTheCityAndInOlderCities() {
    BlockPos mayor = new BlockPos(-5, 144, 2), priest = new BlockPos(-49, 138, 2);
    var sites = new CommerceSites();
    sites.rebuild(List.of(new CommerceSites.Found(CityLayout.Marker.MAYOR, "mayor", mayor),
        new CommerceSites.Found(CityLayout.Marker.SIDEQUEST, "6_inn", new BlockPos(93, 124, 13))), null);
    assertNotNull(sites.find(CommerceRules.Role.CHARACTER, "mayor", mayor));
    assertTrue(CommerceRules.Role.CHARACTER.bound(), "Characters return to their post");
    assertEquals(1.0, CommerceRules.multiplier(CommerceRules.Role.CHARACTER, true, false, CommerceRules.Prices.DEFAULT));
    var older = new CommerceSites();
    Map<String, BlockPos> npcs = new LinkedHashMap<>();
    npcs.put("mayor", mayor);
    npcs.put("priest", priest);
    assertTrue(older.ensureCharacters(npcs), "A city placed before act VI gets its characters");
    assertFalse(older.ensureCharacters(npcs), "Only once");
    var loaded = new CommerceSites();
    loaded.load(older.save());
    assertEquals(2, loaded.sites.stream().filter(site -> site.role == CommerceRules.Role.CHARACTER).count());
    assertNotNull(loaded.find(CommerceRules.Role.CHARACTER, "priest", priest));
  }

  @Test
  void theWorldStateSurvivesASave() {
    var data = new SolsticioData();
    data.liberated = true;
    data.liberatedAt = 1234;
    data.electionsHeld = true;
    data.electionsAt = 99_999;
    data.elder = new BlockPos(3, 140, -7);
    UUID team = UUID.randomUUID();
    data.baskets.put(team, 12L);
    data.cats.put(team, new SolsticioData.ErrandCat(new BlockPos(1, 150, 2), UUID.randomUUID()));
    var loaded = SolsticioData.load(data.save(new CompoundTag(), null), null);
    assertTrue(loaded.liberated && loaded.electionsHeld);
    assertEquals(1234, loaded.liberatedAt);
    assertEquals(99_999, loaded.electionsAt);
    assertEquals(data.elder, loaded.elder);
    assertEquals(data.baskets, loaded.baskets);
    assertEquals(data.cats, loaded.cats);
    var old = SolsticioData.load(new CompoundTag(), null);
    assertTrue(old.liberatedAt == 0 && !old.electionsHeld && old.elder == null && old.cats.isEmpty(), "Old saves start chained");
    loaded.resetCity();
    assertTrue(loaded.cats.isEmpty() && loaded.elder == null && loaded.electionsHeld, "A city reset keeps the vote");
  }

  @Test
  void everyLineExistsInBothLanguages() throws Exception {
    var en = json("src/main/resources/assets/entrelumen/lang/en_us.json");
    var es = json("src/main/resources/assets/entrelumen/lang/es_es.json");
    assertEquals(en.keySet(), es.keySet(), "EN/ES parity");
    Set<String> keys = new HashSet<>();
    for (String character : SolsticioStoryRules.CHARACTERS) {
      keys.add("entrelumen.solsticio.character." + character);
      keys.add("entity.minecraft.villager.entrelumen." + character);
      keys.add("entrelumen.solsticio.character." + character + ".visitor");
      if (!character.equals("mayor")) keys.add("entrelumen.solsticio.character." + character + ".protocol");
      keys.add("entrelumen.solsticio.character." + character + ".after");
    }
    for (String stage : List.of("welcome.1", "welcome.2", "wait_gardener", "wait_inventor", "wait_priest", "table",
        "table_waiting", "accord", "portal", "campaign", "elected")) keys.add("entrelumen.solsticio.character.mayor." + stage);
    for (String stage : List.of("power", "power_done", "light", "light_done", "after_elections"))
      keys.add("entrelumen.solsticio.character.inventor." + stage);
    for (String stage : List.of("seeds", "seeds_done", "harvest", "harvest_done", "basket", "after_elections"))
      keys.add("entrelumen.solsticio.character.gardener." + stage);
    for (String stage : List.of("works", "listening", "spirit", "release", "heart", "blessing", "wait", "table",
        "table_waiting", "accord", "after_elections")) keys.add("entrelumen.solsticio.character.priest." + stage);
    for (var errand : SolsticioStoryRules.Errand.values())
      for (String part : List.of("offer", "reminder", "done")) keys.add("entrelumen.solsticio.errand." + errand.id + "." + part);
    for (int persona = 0; persona < CommerceRules.INNKEEPERS; persona++) keys.add("entrelumen.solsticio.innkeeper." + persona + ".thanks");
    for (String egg : SolsticioStoryRules.EGGS)
      for (String part : List.of("found", "keepsake", "keepsake.lore")) keys.add("entrelumen.solsticio.egg." + egg + "." + part);
    for (var book : Map.of("egg_tavern", 2, "egg_secret_garden", 2, "egg_sundial", 2, "rumour", 3, "terraprism", 3).entrySet()) {
      keys.add("entrelumen.solsticio.lore." + book.getKey() + ".title");
      for (int page = 1; page <= book.getValue(); page++) keys.add("entrelumen.solsticio.lore." + book.getKey() + "." + page);
    }
    for (String pool : List.of("", "liberated.", "elected."))
      for (int line = 0; line < CommerceRules.TOWNSFOLK_LINES; line++) keys.add("entrelumen.solsticio.townsfolk." + pool + line);
    for (int sector = 0; sector < 8; sector++) keys.add("entrelumen.solsticio.direction." + sector);
    for (String objective : List.of("solsticio_town_hall", "solsticio_gardens", "solsticio_workshop", "solsticio_chapel",
        "solsticio_portal")) {
      keys.add("entrelumen.compass.objective." + objective);
      keys.add("entrelumen.compass.objective." + objective + ".why");
    }
    for (String key : keys) {
      assertTrue(en.has(key), "EN misses " + key);
      assertFalse(en.get(key).getAsString().isBlank(), key);
      assertEquals(en.get(key).getAsString().split("%s", -1).length, es.get(key).getAsString().split("%s", -1).length,
          "Placeholder count differs: " + key);
    }
  }
}
