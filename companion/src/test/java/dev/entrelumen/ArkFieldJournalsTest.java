package dev.entrelumen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** The Ark's status screen (v2): the clicked module's effect, the six modules and the checklist. */
class ArkFieldJournalsTest {
  private static final UUID PLAYER = UUID.randomUUID();
  private static final UUID TEAM = UUID.randomUUID();
  private static final Set<String> ALL = Set.copyOf(ArkRules.moduleIds());

  private static JsonObject lang(String code) throws Exception {
    try (var reader = new InputStreamReader(ArkFieldJournalsTest.class.getResourceAsStream(
        "/assets/entrelumen/lang/" + code + ".json"), StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static JournalBookNetwork.Snapshot screen(String block, ArkRules.Status ark, int missing,
      Set<String> completed) {
    return ArkFieldJournals.screen(PLAYER, TEAM, block, ark, missing, completed, id -> true);
  }

  private static String key(Component component) {
    return component.getContents() instanceof TranslatableContents text ? text.getKey() : "";
  }

  @Test
  void aModuleShowsItsEffectOnlyInItsSlotOfAStandingArk() {
    var none = screen("nature_module", ArkRules.Status.NONE, -1, Set.of());
    assertEquals(ArkFieldJournals.Status.INACTIVE, none.status());
    assertEquals("entrelumen.ark.screen.status.inactive", key(none.statusLine()));
    var reason = (TranslatableContents) none.statusLine().getContents();
    assertEquals("entrelumen.ark.screen.reason.no_ark", key((Component) reason.getArgs()[0]));

    var noController = screen("nature_module", new ArkRules.Status(true, true, false, ALL, 0), 0, Set.of());
    assertEquals(ArkFieldJournals.Status.INACTIVE, noController.status());
    assertEquals("entrelumen.ark.screen.reason.controller",
        key((Component) ((TranslatableContents) noController.statusLine().getContents()).getArgs()[0]));

    var building = screen("nature_module", new ArkRules.Status(true, false, true, ALL, 0), 12, Set.of());
    var core = (TranslatableContents) ((Component) ((TranslatableContents) building.statusLine().getContents())
        .getArgs()[0]).getContents();
    assertEquals("entrelumen.ark.screen.reason.core", core.getKey());
    assertEquals(12, core.getArgs()[0]);

    var active = screen("nature_module", new ArkRules.Status(true, true, true, Set.of("nature_module"), 2), 0, Set.of());
    assertEquals(ArkFieldJournals.Status.ACTIVE, active.status());
    assertTrue(active.entries(JournalBookNetwork.Section.EFFECT).getFirst().complete());
    assertEquals(6, active.entries(JournalBookNetwork.Section.MODULES).size());
    assertEquals(1, active.entries(JournalBookNetwork.Section.MODULES).stream()
        .filter(JournalBookNetwork.Entry::complete).count());
  }

  @Test
  void theChecklistNamesEveryPieceAndWhereItComesFrom() {
    var ark = new ArkRules.Status(true, true, true, Set.of("habitation_module", "exploration_module"), 0);
    var snapshot = screen(ArkMultiblock.CONTROLLER, ark, 0, Set.of("world_network"));
    var checklist = snapshot.entries(JournalBookNetwork.Section.ACTIVATION);
    // Core, controller, six modules, the last project of act V and the End journey.
    assertEquals(10, checklist.size());
    assertEquals(5, checklist.stream().filter(JournalBookNetwork.Entry::complete).count());
    assertTrue(checklist.stream().allMatch(entry -> entry.label().isPresent() && !entry.details().isEmpty()));
    assertEquals(ArkFieldJournals.Status.STANDING, snapshot.status());
    assertEquals("entrelumen.ark.screen.hint.guide", key(snapshot.hint().orElseThrow()));

    var whole = new ArkRules.Status(true, true, true, ALL, 4);
    var ready = screen(ArkMultiblock.CONTROLLER, whole, 0, Set.of("world_network", "end_arrival"));
    assertTrue(ready.entries(JournalBookNetwork.Section.ACTIVATION).stream().allMatch(JournalBookNetwork.Entry::complete));
    assertEquals("entrelumen.ark.screen.hint.ready", key(ready.hint().orElseThrow()));
    var done = screen(ArkMultiblock.CONTROLLER, whole, 0, Set.of("world_network", "end_arrival",
        CampaignMilestones.LAST_HORIZON));
    assertEquals(ArkFieldJournals.Status.ACTIVATED, done.status());
    // The level row: one plus a level per beacon.
    var level = (TranslatableContents) done.entries(JournalBookNetwork.Section.EFFECT).getFirst().label().orElseThrow()
        .getContents();
    assertEquals(5, level.getArgs()[0]);
  }

  @Test
  void everyScreenTextIsTranslatedInEnglishAndSpanish() throws Exception {
    var en = lang("en_us");
    var es = lang("es_es");
    Set<String> keys = new TreeSet<>();
    var arks = java.util.List.of(ArkRules.Status.NONE, new ArkRules.Status(true, false, false, Set.of(), 0),
        new ArkRules.Status(true, true, false, ALL, 0), new ArkRules.Status(true, true, true, ALL, 3));
    var progress = java.util.List.of(Set.<String>of(), Set.of("world_network", "end_arrival"),
        Set.of("world_network", "end_arrival", CampaignMilestones.LAST_HORIZON));
    for (String block : ArkRules.SLOTS)
      for (var ark : arks)
        for (var completed : progress)
          for (Component text : screen(block, ark, 7, completed).texts()) collect(text, keys);
    for (String key : keys) {
      if (key.startsWith("block.minecraft") || key.startsWith("item.minecraft")) continue;
      assertTrue(en.has(key), "en_us lacks " + key);
      assertTrue(es.has(key), "es_es lacks " + key);
    }
  }

  private static void collect(Component component, Set<String> keys) {
    if (component.getContents() instanceof TranslatableContents text) {
      keys.add(text.getKey());
      for (Object arg : text.getArgs()) if (arg instanceof Component inner) collect(inner, keys);
    }
    component.getSiblings().forEach(sibling -> collect(sibling, keys));
  }
}
