"""Contracts of the quest book v3 (docs/design/quest-book-v3.md): the sector engine (tools/quest_engine.py),
the two flagship sectors (Create in three chapters, Ars Nouveau), reward tables and loot crates, presets,
theme, custom shapes and the companion's ftbquests strings. Static only: FTB loading is checked on a
server (quest-book-v3.md, "QA"), and the look inside the client is still to be seen."""
import copy
import json
import math
import re
import unittest

import quest_art
import quest_engine as qe
import quest_text
import build_quest_placeholders as placeholders
from test_presentation import Canvas, Fonts, Pilots, TextMarkup  # noqa: F401  presentation v2 runs with the book
from generate_quests import OUT, THEME, FTBQ_LANG, LOCALES, generate_book, load_book, load_chapters, load_guides, stable_id


def ctx_for(accent="#E5A16A", quests=("crb_press",), chapters=("sector_create_kinetics",)):
    ctx = {"items": set(), "names": set(), "keys": set(), "textures": set(), "entities": set(),
           "structures": set(), "advancements": set(), "accent": accent}
    ctx["resolve_quest"] = lambda target, where: (qe.stable_id("quest:" + target) if target in quests
                                                  else (_ for _ in ()).throw(AssertionError("unknown quest")))
    ctx["resolve_chapter"] = lambda target, where: qe.stable_id("chapter:" + target)
    return ctx


class RichText(unittest.TestCase):
    def test_plain_paragraph_stays_plain(self):
        line, (plain, tip) = qe.compile_paragraph("Belts carry items and mobs.", "en_us", ctx_for(), "t")
        self.assertEqual(line, "Belts carry items and mobs.")
        self.assertFalse(tip)

    def test_item_key_link_hover_and_rune_compile_to_json(self):
        ctx = ctx_for()
        text = ("[tip] Hold [key:key.ponder.ponder] over a [item:create:shaft|Shaft], see [quest:crb_press|the press], "
                "[hover|stress|the rotation budget] and [rune|old words].")
        line, (plain, tip) = qe.compile_paragraph(text, "en_us", ctx, "t")
        data = json.loads(line)
        self.assertEqual(data[0], "")
        self.assertTrue(tip)
        self.assertEqual(data[1]["text"], "» ")
        self.assertEqual(data[2]["text"], "Pro tip: ")
        item = next(s for s in data[1:] if s.get("text") == "Shaft")
        self.assertEqual(item["hoverEvent"], {"action": "show_item", "contents": {"id": "create:shaft", "count": 1}})
        key = next(s for s in data[1:] if "extra" in s)
        self.assertEqual(key["extra"][1], {"keybind": "key.ponder.ponder"})
        link = next(s for s in data[1:] if s.get("text") == "the press")
        self.assertEqual(link["clickEvent"], {"action": "change_page", "value": qe.stable_id("quest:crb_press")})
        self.assertTrue(link["underlined"])
        hover = next(s for s in data[1:] if s.get("text") == "stress")
        self.assertEqual(hover["hoverEvent"]["action"], "show_text")
        rune = next(s for s in data[1:] if s.get("text") == "old words")
        self.assertEqual(rune["font"], "minecraft:alt")
        self.assertEqual(ctx["items"], {"create:shaft"})
        self.assertEqual(ctx["keys"], {"key.ponder.ponder"})
        self.assertNotIn("[", plain)

    def test_spanish_tip_prefix(self):
        line, _ = qe.compile_paragraph("[tip] Hacé varios.", "es_es", ctx_for(), "t")
        self.assertEqual(json.loads(line)[2]["text"], "La posta: ")

    def test_link_to_a_page(self):
        line, _ = qe.compile_paragraph("[quest:crb_press/2|second page]", "en_us", ctx_for(), "t")
        self.assertEqual(json.loads(line)[1]["clickEvent"]["value"], qe.stable_id("quest:crb_press") + "/2")

    def test_pages_images_and_blank_lines(self):
        lines, first = qe.compile_text(["One.", "Two.", "{page}", "{image:entrelumen:textures/gui/quests/medallion.png width:64 height:64 align:center}", "Three."],
                                       "en_us", ctx_for(), "t")
        self.assertEqual(lines, ["One.", "", "Two.", "{@pagebreak}",
                                 "{image:entrelumen:textures/gui/quests/medallion.png width:64 height:64 align:center}", "", "Three."])
        self.assertEqual(first, "One. Two.")

    def test_bad_markup_is_rejected(self):
        for bad in ("[item:create:shaft]", "[bogus|x]", "stray { brace", "[quest:nowhere|x]", "mid [tip] tip",
                    "{image:create:shaft width:16}", "back\\slash"):
            with self.subTest(text=bad):
                with self.assertRaises(AssertionError):
                    qe.compile_paragraph(bad, "en_us", ctx_for(), "t")
        with self.assertRaises(AssertionError):
            qe.compile_text(["{page}", "x"], "en_us", ctx_for(), "t")
        with self.assertRaises(AssertionError):
            qe.compile_text(["x", "{page}"], "en_us", ctx_for(), "t")

    def test_ampersand_is_literal_in_json(self):
        line, _ = qe.compile_paragraph("Crafts & Additions", "en_us", ctx_for(), "t")
        self.assertEqual(json.loads(line)[1]["text"], "Crafts & Additions")

    def test_copy_rules(self):
        qe.check_copy("Belts", "Tiny.", "Belts carry items.", "en_us", "ok")
        bad = [("Title", None, "In this chapter you will learn belts.", "en_us"),
               ("Título", None, "Esta quest te enseña cintas.", "es_es"),
               ("Belts", None, "Press F8 to open the book.", "en_us"),
               ("Belts", None, "Belts. They carry items.", "en_us"),
               ("Belts", None, "x" * (qe.MAX_PAGE_CHARS + 1), "en_us"),
               ("x" * (qe.MAX_TITLE + 1), None, "Fine.", "en_us"),
               ("Belts", "y" * (qe.MAX_SUBTITLE + 1), "Fine.", "en_us")]
        for title, sub, text, lang in bad:
            with self.subTest(text=text[:30]):
                with self.assertRaises(AssertionError):
                    qe.check_copy(title, sub, text, lang, "bad")


class Geometry(unittest.TestCase):
    def test_ring_slots_start_and_turn_clockwise(self):
        slots = qe.figure_slots({"kind": "ring", "cx": 0, "cy": 0, "r": 8, "count": 4, "start": -90})
        self.assertEqual([(round(x, 6), round(y, 6)) for x, y, _, _ in slots], [(0, -8), (8, 0), (0, 8), (-8, 0)])

    def test_line_image_spans_the_segment(self):
        # An image of width w is drawn 24*w px; a grid unit is 28 px, so a line of L units is L*28/24 wide.
        img = qe.line_image("k", 0, 0, 6, 0, "#FFFFFF", 100, overlap=1.0)
        self.assertAlmostEqual(img["width"] * qe.NODE_PX, 6 * qe.GRID_PX, places=2)
        self.assertEqual((img["x"], img["y"], img["rotation"]), (3.0, 0.0, 0.0))
        self.assertAlmostEqual(img["height"] * qe.NODE_PX, 1.0, places=2)
        diagonal = qe.line_image("k", 0, 0, 3, 3, "#FFFFFF", 100)
        self.assertAlmostEqual(diagonal["rotation"], 45.0)

    def test_bezier_points_compensate_ftb_shift(self):
        # QuestButton.positionControlPoints: pixel = (p - minX - size/2)*(bs+bp) + bp/2 + bp*(size-1)/2,
        # while the node centre is (x - minX)*(bs+bp). Undo it and the control point lands where we asked.
        q, dep, size = (4.0, 2.0), (0.0, 0.0), 2.0
        pts = qe.bezier_points(q, size, dep, 0.2)
        bs, bp = 24, 4
        def ftb(px):
            return (px - size / 2) * (bs + bp) + bp / 2 + bp * (size - 1) / 2
        vx, vy = dep[0] - q[0], dep[1] - q[1]
        length = math.hypot(vx, vy)
        nx, ny = -vy / length, vx / length
        want = dep[0] - vx * 0.3 + nx * 0.2 * length
        self.assertAlmostEqual(ftb(pts[0]) / (bs + bp), want, places=3)


class AnyOfTasks(unittest.TestCase):
    """Item tasks that accept any of several items or item tags: an FTB Filter System smart filter, matched by
    FTB Quests through FTB XMod Compat. The server check is in docs/design/quest-book-v3.md."""
    TITLE = {"en_us": "Any four logs", "es_es": "Cuatro troncos cualesquiera"}

    @classmethod
    def setUpClass(cls):
        cls.book = load_book()
        cls.story = load_chapters()
        cls.guides = load_guides(cls.book)
        cls.sectors = {s["chapter"]: s for s in qe.load_sectors()}

    def compile(self, task, index=0):
        languages = {lang: {} for lang in LOCALES}
        ctx = ctx_for()
        return qe.compile_task(task, "crb_press", index, languages, ctx), languages, ctx

    def test_items_and_tags_compile_to_one_smart_filter(self):
        out, languages, ctx = self.compile({"any": ["minecraft:oak_log", "#minecraft:logs"], "count": 4,
                                            "title": self.TITLE})
        tid = qe.stable_id("task:crb_press")
        self.assertEqual(out, {"id": tid, "type": "item",
                               "item": {"id": "ftbfiltersystem:smart_filter", "count": 1,
                                        "components": {"ftbfiltersystem:filter":
                                                       "or(item(minecraft:oak_log)item_tag(minecraft:logs))"}},
                               "count": 4, "consume_items": False, "icon": {"id": "minecraft:oak_log"}})
        self.assertEqual({lang: languages[lang][f"task.{tid}.title"] for lang in LOCALES}, self.TITLE)
        self.assertEqual((ctx["items"], ctx["item_tags"]), ({"minecraft:oak_log"}, {"minecraft:logs"}))

    def test_one_tag_needs_no_or_and_takes_the_given_icon(self):
        out, _, _ = self.compile({"any": ["#c:ingots/copper"], "icon": "minecraft:copper_ingot", "consume": True,
                                  "title": self.TITLE}, 1)
        self.assertEqual(out["id"], qe.stable_id("task:crb_press:1"))
        self.assertEqual(out["item"]["components"], {"ftbfiltersystem:filter": "item_tag(c:ingots/copper)"})
        self.assertEqual((out["icon"], out["consume_items"], out["count"]), ({"id": "minecraft:copper_ingot"}, True, 1))

    def test_bad_any_of_tasks_are_rejected(self):
        t = self.TITLE
        cases = [({"any": ["minecraft:oak_log"], "title": t}, "single item"),
                 ({"any": ["minecraft:oak_log", "minecraft:oak_log"], "title": t}, "repeated"),
                 ({"any": ["minecraft:oak_log", "logs"], "title": t}, "item ids or #tags"),
                 ({"any": [], "title": t}, "item ids or #tags"),
                 ({"any": ["minecraft:oak_log", "#minecraft:logs"]}, "needs a title"),
                 ({"any": ["#minecraft:logs", "#minecraft:planks"], "title": t}, "needs an icon"),
                 ({"any": ["minecraft:oak_log", "minecraft:birch_log"], "item": "minecraft:oak_log", "title": t},
                  "replaces item"),
                 ({"any": ["minecraft:oak_log", "minecraft:birch_log"], "count": 0, "title": t}, "count"),
                 ({"type": "checkmark", "any": ["minecraft:oak_log", "minecraft:birch_log"], "title": t}, "item task"),
                 ({"any": ["minecraft:oak_log", "minecraft:birch_log"], "title": {"en_us": "Logs"}}, "locales")]
        for task, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(AssertionError, message):
                    self.compile(task)

    def test_both_filter_mods_must_be_locked(self):
        self.assertTrue(qe.filter_mods_locked())
        qe._FILTER_MODS_LOCKED = False
        try:
            with self.assertRaisesRegex(AssertionError, "FTB XMod Compat"):
                self.compile({"any": ["minecraft:oak_log", "minecraft:birch_log"], "title": self.TITLE})
        finally:
            qe._FILTER_MODS_LOCKED = None

    def test_a_sector_step_and_bounty_ask_for_any_of(self):
        data = copy.deepcopy(self.sectors["sector_create_addons"])
        step = next(q for q in data["quests"] if q["role"] == "step" and "icon" not in q and not q.get("sequential"))
        step.pop("tasks", None)
        step["task"] = {"any": ["create:andesite_alloy", "#c:ingots/zinc"], "count": 2,
                        "title": {"en_us": "Andesite alloy or zinc", "es_es": "Aleación de andesita o zinc"}}
        bounty = next(q for q in data["quests"] if q["role"] == "bounty")
        bounty.pop("tasks", None)
        bounty["task"] = {"any": ["#minecraft:logs"], "count": 64, "consume": True, "icon": "minecraft:oak_log",
                          "title": {"en_us": "Any logs", "es_es": "Troncos cualesquiera"}}
        sectors = [s for s in self.sectors.values() if s["chapter"] != data["chapter"]] + [data]
        files = generate_book(self.story, self.guides, self.book, sectors)
        chapter = json.loads(files[OUT / "chapters" / (data["chapter"] + ".snbt")])
        quests = {q["id"]: q for q in chapter["quests"]}
        q = quests[stable_id("quest:" + step["key"])]
        self.assertEqual(q["icon"], {"id": "create:andesite_alloy"})
        self.assertEqual(q["tasks"], [{"id": stable_id("task:" + step["key"]), "type": "item",
                                       "item": {"id": "ftbfiltersystem:smart_filter", "count": 1, "components": {
                                           "ftbfiltersystem:filter": "or(item(create:andesite_alloy)item_tag(c:ingots/zinc))"}},
                                       "count": 2, "consume_items": False, "icon": {"id": "create:andesite_alloy"}}])
        b = quests[stable_id("quest:" + bounty["key"])]
        self.assertTrue(b["can_repeat"])
        self.assertEqual((b["icon"], b["tasks"][0]["consume_items"], b["tasks"][0]["item"]["components"]),
                         ({"id": "minecraft:oak_log"}, True, {"ftbfiltersystem:filter": "item_tag(minecraft:logs)"}))
        es = json.loads(files[OUT / "lang" / "es_es.snbt"])
        self.assertEqual(es[f"task.{q['tasks'][0]['id']}.title"], "Aleación de andesita o zinc")
        # A quest whose first task lists tags only, with no icon anywhere, is refused by name.
        broken = copy.deepcopy(data)
        next(x for x in broken["quests"] if x["key"] == bounty["key"])["task"].pop("icon")
        with self.assertRaisesRegex(AssertionError, bounty["key"] + ": an any-of task of tags only needs an icon"):
            generate_book(self.story, self.guides, self.book,
                          [s for s in self.sectors.values() if s["chapter"] != data["chapter"]] + [broken])
        # Every other chapter is what generate_quests.py --check holds on disk.
        for path, content in files.items():
            if path.parent == OUT / "chapters" and path.stem != data["chapter"]:
                self.assertEqual(content, path.read_text(encoding="utf-8"), path.name)

    def test_check_guides_checks_every_alternative(self):
        # check_guides.py against a small stand-in registry (the real one reads the pinned JARs, not in CI).
        import tempfile
        from pathlib import Path
        import check_guides as cg
        items = {"minecraft:oak_log", "minecraft:birch_log", "create:zinc_ingot", "ftbfiltersystem:smart_filter"}
        reg = {"advancements": set(), "entities": set(), "structures": set(), "structure_tags": set(), "lang": set(),
               "item_tags": {}, "item_owner": {},
               "item_tags_all": {"minecraft:logs": ["#minecraft:oak_logs", "minecraft:birch_log"],
                                 "minecraft:oak_logs": ["minecraft:oak_log"], "c:ingots/zinc": ["create:zinc_ingot"],
                                 "c:ingots/tin": ["mekanism:ingot_tin"]}}
        saved = (cg._ITEMS, cg._SECTOR, cg._LOCKED)
        cg._ITEMS, cg._SECTOR = (items, {"minecraft", "create", "c", "ftbfiltersystem"}), reg

        def run(entries, locked=("ftbfiltersystem", "ftbxmodcompat")):
            cg._LOCKED = set(locked)
            quest = {"key": "anyof_probe", "deps": [], "sources": ["test"],
                     "task": {"any": entries, "title": self.TITLE},
                     "en_us": {"title": "Probe", "text": ["Logs."]}, "es_es": {"title": "Prueba", "text": ["Troncos."]}}
            data = {"icon": "minecraft:oak_log", "emblem": "entrelumen:textures/gui/quests/px.png", "quests": [quest]}
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "sector_probe.json"
                path.write_text(json.dumps(data), encoding="utf-8")
                errors = []
                cg.check_sector(path, errors, {"anyof_probe"})
            return errors

        try:
            self.assertEqual(run(["minecraft:oak_log", "#minecraft:logs", "#c:ingots/zinc"]), [])
            self.assertIn("entrelumen:luminous_gear", cg.known_item_tags(reg))  # the companion's own tags count
            self.assertRegex(" ".join(run(["minecraft:oak_log", "minecraft:acacia_log"])), "item minecraft:acacia_log not found")
            self.assertRegex(" ".join(run(["minecraft:oak_log", "#minecraft:nope"])), r"#minecraft:nope is defined by no")
            self.assertRegex(" ".join(run(["minecraft:oak_log", "#c:ingots/tin"])), r"#c:ingots/tin holds no item")
            self.assertRegex(" ".join(run(["minecraft:oak_log", "#minecraft:logs"], locked=("ftbfiltersystem",))),
                             "need ftbxmodcompat")
        finally:
            cg._ITEMS, cg._SECTOR, cg._LOCKED = saved

    def test_check_guides_caches_are_per_jar_set_and_replaced_whole(self):
        import tempfile
        from pathlib import Path
        import check_guides as cg
        base = Path("research/item-registry.json")
        self.assertNotEqual(cg.signed_cache(base, ["a.jar"], 2), cg.signed_cache(base, ["a.jar", "b.jar"], 2))
        self.assertNotEqual(cg.signed_cache(base, ["a.jar"], 2), cg.signed_cache(base, ["a.jar"], 3))
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "cache.json"
            cg.write_cache(target, {"version": 1})
            cg.write_cache(target, {"version": 2})
            self.assertEqual(json.loads(target.read_text(encoding="utf-8")), {"version": 2})
            self.assertEqual([p.name for p in Path(tmp).iterdir()], ["cache.json"])


class Sectors(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.book = load_book()
        cls.story = load_chapters()
        cls.guides = load_guides(cls.book)
        cls.sectors = {s["chapter"]: s for s in qe.load_sectors()}
        cls.files = generate_book(cls.story, cls.guides, cls.book, list(cls.sectors.values()))
        cls.chapters = {p.stem: json.loads(c) for p, c in cls.files.items() if p.parent == OUT / "chapters"}
        cls.lang = {l: json.loads(cls.files[OUT / "lang" / (l + ".snbt")]) for l in LOCALES}
        cls.quests = {q["id"]: q for c in cls.chapters.values() for q in c["quests"]}
        cls.tables = {p.stem: json.loads(c) for p, c in cls.files.items() if p.parent == OUT / "reward_tables"}
        cls.data = json.loads(cls.files[OUT / "data.snbt"])

    def sector_quests(self, name):
        return self.chapters[name]["quests"]

    def test_the_flagship_sectors_replace_their_guides(self):
        self.assertTrue({"sector_create_kinetics", "sector_create_logistics", "sector_create_addons",
                         "sector_ars_nouveau", "sector_ars_addons"} <= set(self.sectors))
        guide_names = {g["chapter"] for g in self.guides}
        self.assertFalse([g for g in guide_names if g.startswith(("guide_create_", "guide_ars_"))])
        # Keys reused from the old guides keep their quest IDs (player progress survives).
        self.assertEqual(self.quests[stable_id("quest:crb_alloy")]["id"], stable_id("quest:crb_alloy"))
        self.assertIn(stable_id("quest:arsspell_book"), self.quests)

    def test_sector_standard_is_met(self):
        # docs/design/quest-book-v3.md, "Estándar de una cadena".
        counts = {"sector_create_kinetics": (45, 90), "sector_create_logistics": (45, 90),
                  "sector_create_addons": (20, 90), "sector_ars_nouveau": (60, 110),
                  "sector_ars_addons": (40, 90)}  # depth follows the mod (Elias, 27/9): addons may grow to the cap
        for name, data in self.sectors.items():
            roles = [q["role"] for q in data["quests"] if quest_art.is_counted(q)]   # decor is never content
            with self.subTest(sector=name):
                lo, hi = counts.get(name, (20, 90))  # the standard for chapters without their own range
                self.assertTrue(lo <= len(roles) <= hi, len(roles))
                self.assertEqual(roles.count("capstone"), 1)
                self.assertEqual(roles.count("entry"), 1)
                self.assertGreaterEqual(roles.count("milestone"), 3)
                self.assertGreaterEqual(roles.count("tip"), 2)
                self.assertGreaterEqual(roles.count("secret"), 2)
                self.assertEqual(roles.count("bounty"), 1)
                self.assertLessEqual(roles.count("tip") + roles.count("info"), len(roles) // 4)

    def test_nodes_follow_the_role_grammar_and_presets(self):
        presets = self.data["presets"]
        for name, data in self.sectors.items():
            motif = self.book["motifs"][data["motif"]]
            for source, q in zip(data["quests"], self.sector_quests(name)):
                shape, size, _ = qe.ROLES[source["role"]]
                shape = motif.get("shapes", {}).get(source["role"], shape)
                with self.subTest(quest=source["key"]):
                    if source["role"] == "decor":   # a toy draws at any of its sizes (quest_art.decor)
                        size = float(source.get("size", size))
                        self.assertIn(size, quest_art.DECOR_SIZES)
                    self.assertEqual((q["shape"], q["size"]), (shape, size))
                    self.assertEqual(presets[q["preset"]], {"shape": shape, "size": size})
                    self.assertIn(f"entrelumen_{source['role']}", q["tags"])
                    if source["role"] != "decor":
                        # Sizes 0.75, 1, 2, 3 keep 16 px icons on whole texels.
                        self.assertIn(size, (0.75, 1.0, 2.0, 3.0))
        for default in ("normal", "info", "goal"):
            self.assertIn(default, presets)

    def test_role_behaviour(self):
        for name, data in self.sectors.items():
            for source, q in zip(data["quests"], self.sector_quests(name)):
                role = source["role"]
                types = [t["type"] for t in q["tasks"]]
                with self.subTest(quest=source["key"]):
                    if role == "secret":
                        self.assertTrue(q["invisible"])
                        self.assertNotIn("checkmark", types)
                        self.assertIn("toast", [r["type"] for r in q["rewards"]])
                    if role == "bounty":
                        self.assertTrue(q["can_repeat"])
                        self.assertEqual(q["repeat_cooldown"], 1200)
                        self.assertTrue(all(t["consume_items"] for t in q["tasks"]))
                        crate = next(r for r in q["rewards"] if r["type"] == "item")
                        self.assertEqual(crate["item"]["id"], "ftbquests:lootcrate")
                    if role == "capstone":
                        self.assertTrue(q["hide_details_until_startable"])
                        self.assertEqual(q["progression_mode"], "linear")  # else the chapter's flexible mode lets it open
                        self.assertIn("command", [r["type"] for r in q["rewards"]])
                    if role in ("tip", "info"):
                        self.assertEqual((types, q["rewards"], q["disable_toast"], q["hide_lock_icon"]), (["checkmark"], [], True, True))
                    if types == ["checkmark"]:
                        self.assertTrue(q["optional"])
                    self.assertTrue(all(not t.get("consume_items") for t in q["tasks"] if role != "bounty"))

    def test_every_catalogued_quest_feature_is_exercised(self):
        allq = [q for name in self.sectors for q in self.sector_quests(name)]
        tasks = {t["type"] for q in allq for t in q["tasks"]}
        self.assertTrue({"item", "checkmark", "advancement", "kill", "observation", "structure"} <= tasks)
        observations = {t["observation_type"] for q in allq for t in q["tasks"] if t["type"] == "observation"}
        self.assertTrue({"entity_type", "block_tag"} <= observations)
        rewards = {r["type"] for q in allq for r in q["rewards"]}
        self.assertTrue({"xp", "item", "choice", "random", "loot", "toast", "command"} <= rewards)
        self.assertTrue(any(q.get("dep_control_pts") for q in allq))
        self.assertTrue(any(q.get("hide_until_deps_complete") for q in allq))
        self.assertTrue(any(q.get("hide_text_until_complete") for q in allq))
        self.assertTrue(any(q.get("require_sequential_tasks") for q in allq))
        self.assertTrue(any(q.get("min_required_dependencies") for q in allq))
        self.assertTrue(any(q.get("dependency_requirement") == "one_completed" for q in allq))
        self.assertTrue(any(q.get("max_completable_dependents") for q in allq))
        self.assertTrue(any(q.get("hide_dependency_lines") for q in allq))
        self.assertTrue(any(q.get("icon", {}).get("components", {}).get("ftbquests:entity_face") for q in allq))
        self.assertTrue(any(t.get("icon") for q in allq for t in q["tasks"]))
        # An item variant by component (Ars Technica's Arcane Wrench) is matched fuzzily on that component.
        self.assertTrue(any(t.get("match_components") == "fuzzy" and t["item"].get("components") for q in allq for t in q["tasks"]))
        subtitle_keys = [k for k in self.lang["en_us"] if k.endswith(".quest_subtitle")]
        self.assertGreater(len(subtitle_keys), 40)
        task_titles = [k for k in self.lang["en_us"] if k.startswith("task.") and k.endswith(".title")]
        self.assertGreaterEqual(len(task_titles), 4)

    def test_every_rich_text_helper_is_used(self):
        # The catalogue marks these helpers as used (docs/research/ftbquests-2101-features.md, section 12).
        raw = " ".join(p for data in self.sectors.values() for q in data["quests"]
                       for lang in ("en_us", "es_es") for p in q[lang]["text"])
        used = {m.group(1) for m in qe.TAG.finditer(raw)}
        self.assertTrue({"item", "key", "quest", "chapter", "name", "hover", "hl", "b", "i", "warn", "good", "rune", "tip"} <= used,
                        sorted(qe.TEXT_TAGS - used))
        self.assertIn("{page}", raw)
        self.assertIn("{image:", raw)
        desc = json.dumps([v for k, v in self.lang["en_us"].items() if k.endswith(".quest_desc")])
        for needle in ("change_page", "show_item", "show_text", "keybind", "translate", "minecraft:alt", "{@pagebreak}"):
            self.assertIn(needle, desc)

    def test_dependencies_and_curves(self):
        for name in self.sectors:
            local = {q["id"]: q for q in self.sector_quests(name)}
            for q in self.sector_quests(name):
                for dep, pts in q.get("dep_control_pts", {}).items():
                    self.assertIn(dep, local)
                    self.assertIn(dep, q["dependencies"])
                    self.assertEqual(len(pts), 4)
                for dep in q["dependencies"]:
                    self.assertIn(dep, self.quests)
            links = self.chapters[name].get("quest_links", [])
            for link in links:
                self.assertIn(link["linked_quest"], self.quests)
                self.assertNotIn(link["linked_quest"], local)

    def test_rich_text_lines_parse_and_mirror_across_languages(self):
        for name, data in self.sectors.items():
            for q in self.sector_quests(name):
                key = f"quest.{q['id']}.quest_desc"
                en, es = self.lang["en_us"][key], self.lang["es_es"][key]
                with self.subTest(quest=q["id"]):
                    self.assertEqual(len(en), len(es))
                    for a, b in zip(en, es):
                        self.assertEqual(a.startswith("["), b.startswith("["))
                        self.assertEqual(a == "{@pagebreak}", b == "{@pagebreak}")
                        self.assertEqual(a.startswith("{image:"), b.startswith("{image:"))
                        if a.startswith("["):
                            ja, jb = json.loads(a), json.loads(b)
                            hovers = lambda j: sorted(s["hoverEvent"]["contents"]["id"] for s in j[1:] if isinstance(s, dict)
                                                      and s.get("hoverEvent", {}).get("action") == "show_item")
                            self.assertEqual(hovers(ja), hovers(jb))

    def test_images_are_locked_and_resolve(self):
        textures = set(placeholders.PLACEHOLDERS)
        for name in self.sectors:
            c = self.chapters[name]
            local = {q["id"] for q in c["quests"]}
            for img in c["images"]:
                with self.subTest(chapter=name, image=img["id"]):
                    self.assertTrue(img["position_locked"])
                    if img["image"] == qe.PX:
                        self.assertIn("color", img)
                        self.assertIn("alpha", img)
                    elif img["image"].startswith(qe.QUESTS_ART):
                        base = img["image"][len(qe.QUESTS_ART):]
                        name_ = base[:-4]
                        self.assertTrue(base in textures or name_ in qe.ART_PX, base)
                    elif img["image"]:
                        # A texture, a block-atlas sprite or an item render (quest_art): what survives the sync.
                        self.assertTrue(any(r.fullmatch(img["image"]) for r in (qe.TEXTURE, quest_art.SPRITE, quest_art.ITEM_IMAGE)),
                                        img["image"])
                    else:
                        self.assertTrue(img["text_on_image"])
                        for lang in LOCALES:
                            json.loads(self.lang[lang][f"image.{img['id']}.title"])
                    if "dependency" in img:
                        self.assertIn(img["dependency"], local)
                    if "click_action" in img:
                        target = img["click_action"].split(":", 1)[1]
                        self.assertTrue(target in self.quests or any(c2["id"] == target for c2 in self.chapters.values()))
                        for lang in LOCALES:
                            self.assertTrue(self.lang[lang][f"image.{img['id']}.title"])

    def test_table_references_are_exact_ints(self):
        ids = {int(t["id"], 16) for t in self.tables.values()}
        for name in self.sectors:
            for q in self.sector_quests(name):
                for r in q["rewards"]:
                    if r["type"] in ("choice", "random", "loot"):
                        self.assertIsInstance(r["table_id"], int)
                        self.assertIn(r["table_id"], ids)

    def test_reward_tables_are_moderate_and_safe(self):
        spec = {k: v for k, v in self.book["reward_tables"].items() if not k.startswith("_")}
        # The book's tables and the chapters' own (reward_tables/<chapter>__<name>.snbt, ID from "<chapter>/<name>").
        local = {f"{c}__{n}": qe.local_table(c, n) for c, s in self.sectors.items() for n in s.get("reward_tables", {})}
        self.assertEqual(set(self.tables), set(spec) | set(local))
        crates = [t for t in self.tables.values() if "loot_crate" in t]
        self.assertEqual(len(crates), 6)
        ids = set()
        for name, t in self.tables.items():
            with self.subTest(table=name):
                self.assertEqual(t["id"], f"{qe.table_id(local.get(name, name)):016X}")
                self.assertLess(qe.table_id(local.get(name, name)), 2 ** 31)  # read back exactly as an SNBT int
                self.assertTrue(t["use_title"])
                for r in t["rewards"]:
                    self.assertNotIn(r["id"], ids)
                    ids.add(r["id"])
                    self.assertEqual(r["type"], "item")
                    self.assertFalse(r["item"]["id"].startswith("entrelumen:"))
                    self.assertLessEqual(r["count"], 64)
                if "loot_crate" in t:
                    self.assertEqual(t["loot_crate"]["drops"], {"passive": 0, "monster": 0, "boss": 0})
                    self.assertRegex(t["loot_crate"]["string_id"], r"^entrelumen_crate_[1-6]$")
                    for lang in LOCALES:
                        self.assertTrue(json.loads(self.files[FTBQ_LANG / f"{lang}.json"])[t["loot_crate"]["item_name"]])
        self.assertFalse(self.data["drop_loot_crates"])
        glow = [t["loot_crate"]["glow"] for n, t in sorted(self.tables.items()) if "loot_crate" in t]
        self.assertEqual(glow, [False] * 5 + [True])
        # Diamonds and golden apples wait for the later acts.
        early = [r["item"]["id"] for n in ("crate_1", "crate_2", "choice_1", "choice_2", "supplies_1", "supplies_2")
                 for r in self.tables[n]["rewards"]]
        self.assertFalse({"minecraft:diamond", "minecraft:golden_apple", "minecraft:netherite_scrap"} & set(early))

    def test_gated_outputs_never_reward(self):
        from generate_quests import gated_outputs
        gated = gated_outputs()
        self.assertGreater(len(gated), 20)
        sample = sorted(gated)[0]  # any gated output; which ones are gated is the recipe audit's call
        book = copy.deepcopy(self.book)
        book["reward_tables"]["crate_1"]["rewards"].append([sample, 1, 1])
        with self.assertRaisesRegex(AssertionError, "gated"):
            qe.build_reward_tables(book, gated)

    def test_theme_companion_strings_and_shapes(self):
        theme = self.files[THEME]
        for motif in self.book["motifs"]:
            self.assertIn(f"[#entrelumen_motif_{motif}]", theme)
        self.assertIn("icon: entrelumen:textures/gui/quests/tip.png", theme)
        for name in self.sectors:
            self.assertEqual(self.chapters[name]["tags"], ["entrelumen_motif_" + self.sectors[name]["motif"]])
        for lang in LOCALES:
            strings = json.loads(self.files[FTBQ_LANG / f"{lang}.json"])
            for shape in qe.CUSTOM_SHAPES:
                self.assertIn(f"ftbquests.quest.shape.{shape}", strings)
            self.assertIn("entrelumen.quests.toast.secret", strings)
        self.assertEqual(placeholders.check(), [])

    def test_the_generated_book_is_what_the_repository_holds(self):
        # Byte for byte, every chapter, table, language and data file (what generate_quests.py --check holds).
        for path, content in self.files.items():
            with self.subTest(path=path.name):
                self.assertTrue(path.exists())
                self.assertEqual(content, path.read_text(encoding="utf-8"))

    def test_story_semantics_do_not_move(self):
        # The story chapters keep their v2 digests (test_generate_quests.py); the book only adds.
        for data in self.story:
            c = self.chapters[data["chapter"]]
            self.assertFalse(any(q.get("preset") for q in c["quests"]))

    def test_engine_rejects_broken_sectors(self):
        base = self.sectors["sector_create_addons"]
        cases = []
        broken = copy.deepcopy(base)
        next(q for q in broken["quests"] if q["role"] == "secret")["task"] = {"type": "checkmark"}
        cases.append((broken, "secret"))
        broken = copy.deepcopy(base)
        next(q for q in broken["quests"] if q["role"] == "bounty")["task"]["consume"] = False
        cases.append((broken, "bounties consume"))
        broken = copy.deepcopy(base)
        broken["quests"][3]["at"] = dict(broken["quests"][2]["at"])
        cases.append((broken, "overlapping"))
        broken = copy.deepcopy(base)
        broken["quests"][1]["en_us"]["text"] = ["In this chapter you will learn about showers."]
        cases.append((broken, "meta phrase"))
        broken = copy.deepcopy(base)
        broken["quests"][1]["en_us"]["text"] = broken["quests"][1]["en_us"]["text"] + ["{page}", "Hold Shift+U to tune it."]
        broken["quests"][1]["es_es"]["text"] = broken["quests"][1]["es_es"]["text"] + ["{page}", "Ajustalo."]
        cases.append((broken, "hard-coded key"))
        broken = copy.deepcopy(base)
        broken["quests"][1]["en_us"]["text"] = broken["quests"][1]["en_us"]["text"] + ["Grab a [item:minecraft:bucket|bucket]."]
        broken["quests"][1]["es_es"]["text"] = broken["quests"][1]["es_es"]["text"] + ["Agarrá un balde."]
        cases.append((broken, "differ between languages"))
        for data, message in cases:
            sectors = [s for s in self.sectors.values() if s["chapter"] != base["chapter"]] + [data]
            with self.subTest(message=message):
                with self.assertRaisesRegex(AssertionError, message):
                    generate_book(self.story, self.guides, self.book, sectors)


def egg(bee, **extra):
    """A Productive Bees spawn egg of one bee type, the way the mod builds it (BeeCreator.getSpawnEgg)."""
    return {"item": "productivebees:spawn_egg_configurable_bee", **extra,
            "components": {"minecraft:entity_data": {"id": "productivebees:configurable_bee", "type": bee}}}


def gene(attribute, value, purity=100, **extra):
    return {"item": "productivebees:gene", **extra,
            "components": {"productivebees:gene_group": {"attribute": attribute, "value": value, "purity": purity}}}


class QuestRewards(unittest.TestCase):
    """Items a quest names itself and a chapter's own reward tables (quest_engine.extra_rewards and
    build_local_tables), on top of the role's rewards. The FTB side is in docs/design/quest-book-v3.md; loading them
    on a server is still pending."""
    # The book's 18 tables, as players' progress and every random, choice and loot reward already hold them.
    BOOK_TABLES = {"choice_1": 0x14466D3A, "choice_2": 0x1998C87C, "choice_3": 0x61FBC412, "choice_4": 0x0323851D,
                   "choice_5": 0x3EF33D8B, "choice_6": 0x67F55CD0, "crate_1": 0x19FA24E6, "crate_2": 0x6551780F,
                   "crate_3": 0x5B3962F9, "crate_4": 0x70F8C581, "crate_5": 0x4D29D48E, "crate_6": 0x7EF5DAAD,
                   "supplies_1": 0x624A0EA0, "supplies_2": 0x4DE0911A, "supplies_3": 0x47901A17,
                   "supplies_4": 0x467286CE, "supplies_5": 0x12D36307, "supplies_6": 0x5C4C41E9}
    CHAPTER = "sector_bees_breeding"

    @classmethod
    def setUpClass(cls):
        from generate_quests import gated_outputs
        cls.book = load_book()
        cls.story = load_chapters()
        cls.guides = load_guides(cls.book)
        cls.sectors = {s["chapter"]: s for s in qe.load_sectors()}
        cls.gated = gated_outputs()
        cls.base = cls.stripped()
        cls.data = cls.fixture()
        cls.files = generate_book(cls.story, cls.guides, cls.book, cls.with_sector(cls.data))
        cls.chapter = json.loads(cls.files[OUT / "chapters" / (cls.CHAPTER + ".snbt")])
        # The same book with the chapter's own rewards stripped: the tests never depend on what the chain gives today.
        cls.base_files = generate_book(cls.story, cls.guides, cls.book, cls.with_sector(cls.base))
        cls.disk = json.loads(cls.base_files[OUT / "chapters" / (cls.CHAPTER + ".snbt")])

    @classmethod
    def stripped(cls):
        """The bees chapter without any reward of its own."""
        data = copy.deepcopy(cls.sectors[cls.CHAPTER])
        data.pop("reward_tables", None)
        for q in data["quests"]:
            for field in ("rewards", "reward_table", "reward_choice"):
                q.pop(field, None)
        return data

    @classmethod
    def fixture(cls):
        """The bees chapter with a bee, a perfect gene and combs on its diamond milestone, a random gene on the iron
        milestone and a choice of bees on the crystalline one."""
        data = cls.stripped()
        quests = {q["key"]: q for q in data["quests"]}
        quests["bees_diamond"]["rewards"] = [egg("productivebees:diamond"), egg("productivebees:iron", count=2),
                                             gene("productivity", "productivity.very_high"),
                                             {"item": "minecraft:honeycomb", "count": 8}]
        quests["bees_iron"]["reward_table"] = "perfect_genes"
        quests["bees_crystalline"]["reward_choice"] = "starter_bees"
        data["reward_tables"] = {
            "starter_bees": {"title": {"en_us": "Pick a bee", "es_es": "Elegí una abeja"},
                             "entries": [egg("productivebees:iron"), egg("productivebees:gold")]},
            "perfect_genes": {"title": {"en_us": "A perfect gene", "es_es": "Un gen perfecto"}, "rolls": 2,
                              "entries": [gene("productivity", "productivity.very_high", weight=3),
                                          gene("endurance", "endurance.strong", weight=1.5)]}}
        return data

    @classmethod
    def with_sector(cls, data):
        return [s for s in cls.sectors.values() if s["chapter"] != data["chapter"]] + [data]

    def quest(self, chapter, key):
        return next(q for q in chapter["quests"] if q["id"] == stable_id("quest:" + key))

    def test_the_book_tables_keep_their_ids_and_order(self):
        for files in (self.files, {p: p.read_text(encoding="utf-8") for p in (OUT / "reward_tables").glob("*.snbt")}):
            for index, (name, long_id) in enumerate(sorted(self.BOOK_TABLES.items())):
                with self.subTest(table=name):
                    self.assertEqual(qe.table_id(name), long_id)
                    table = json.loads(files[OUT / "reward_tables" / (name + ".snbt")])
                    self.assertEqual((table["id"], table["order_index"]), (f"{long_id:016X}", index))

    def test_chapter_tables_have_stable_ids(self):
        # table_id("<chapter>/<name>"): the chapter and the name, never the order or the other tables.
        self.assertEqual(qe.table_id("sector_bees_breeding/perfect_genes"), 0x3F3DF4D3)
        self.assertEqual(qe.table_id("sector_bees_breeding/starter_bees"), 0x75A01016)
        alone, _, _ = qe.build_local_tables([self.data], 18, self.gated)
        other = copy.deepcopy(self.sectors["sector_aa"])
        other["quests"][0]["reward_table"] = "aa_first"  # a chapter sorted before, with a table of its own
        other["reward_tables"] = {"aa_first": {"title": {"en_us": "Odds", "es_es": "Cosas"},
                                               "entries": [{"item": "minecraft:honeycomb"}]}}
        _, alone_files, _ = qe.build_local_tables([self.data], 18, self.gated)
        both, files, _ = qe.build_local_tables([self.data, other], 18, self.gated)
        for key, table in alone.items():
            self.assertEqual(both[key]["id"], table["id"])
        # The order index follows the ID too, so a chapter that adds tables moves no other chapter's files.
        for name, table in alone_files.items():
            self.assertEqual(files[name], table)
        self.assertEqual(files["sector_aa__aa_first"]["order_index"],
                         18 + qe.table_id("sector_aa/aa_first") % qe.LOCAL_ORDER_SPAN)

    def test_quest_rewards_come_on_top_of_the_role(self):
        role = self.quest(self.disk, "bees_diamond")["rewards"]
        rewards = self.quest(self.chapter, "bees_diamond")["rewards"]
        self.assertEqual(rewards[:len(role)], role)
        entity = lambda bee: {"minecraft:entity_data": {"id": "productivebees:configurable_bee", "type": bee}}
        self.assertEqual(rewards[len(role):], [
            {"id": stable_id("reward:bees_diamond:item:productivebees:spawn_egg_configurable_bee"), "type": "item",
             "item": {"id": "productivebees:spawn_egg_configurable_bee", "count": 1,
                      "components": entity("productivebees:diamond")}, "count": 1},
            {"id": stable_id("reward:bees_diamond:item:productivebees:spawn_egg_configurable_bee:2"), "type": "item",
             "item": {"id": "productivebees:spawn_egg_configurable_bee", "count": 1,
                      "components": entity("productivebees:iron")}, "count": 2},
            {"id": stable_id("reward:bees_diamond:item:productivebees:gene"), "type": "item",
             "item": {"id": "productivebees:gene", "count": 1, "components": {"productivebees:gene_group": {
                 "attribute": "productivity", "value": "productivity.very_high", "purity": 100}}}, "count": 1},
            {"id": stable_id("reward:bees_diamond:item:minecraft:honeycomb"), "type": "item",
             "item": {"id": "minecraft:honeycomb", "count": 1}, "count": 8}])
        self.assertEqual(rewards[len(role)]["id"], "7B1BB78E79ADACC5")  # reward IDs are progress: pinned
        iron = self.quest(self.chapter, "bees_iron")["rewards"]
        self.assertEqual(iron[:-1], self.quest(self.disk, "bees_iron")["rewards"])
        self.assertEqual(iron[-1], {"id": stable_id("reward:bees_iron:reward_table:perfect_genes"), "type": "random",
                                    "table_id": 0x3F3DF4D3})
        crystalline = self.quest(self.chapter, "bees_crystalline")["rewards"]
        self.assertEqual(crystalline[-1], {"id": stable_id("reward:bees_crystalline:reward_choice:starter_bees"),
                                           "type": "choice", "table_id": 0x75A01016})

    def test_chapter_tables_compile_to_reward_table_files(self):
        genes = json.loads(self.files[OUT / "reward_tables" / "sector_bees_breeding__perfect_genes.snbt"])
        key = "sector_bees_breeding/perfect_genes"
        self.assertEqual(genes, {"id": "000000003F3DF4D3", "order_index": 18 + 0x3F3DF4D3 % qe.LOCAL_ORDER_SPAN,
                                 "loot_size": 2, "use_title": True, "rewards": [
            {"id": stable_id(f"reward_table:{key}:0"), "type": "item", "item": {"id": "productivebees:gene", "count": 1,
             "components": {"productivebees:gene_group": {"attribute": "productivity", "value": "productivity.very_high",
                                                          "purity": 100}}}, "count": 1, "weight": 3.0},
            {"id": stable_id(f"reward_table:{key}:1"), "type": "item", "item": {"id": "productivebees:gene", "count": 1,
             "components": {"productivebees:gene_group": {"attribute": "endurance", "value": "endurance.strong",
                                                          "purity": 100}}}, "count": 1, "weight": 1.5}]})
        bees = json.loads(self.files[OUT / "reward_tables" / "sector_bees_breeding__starter_bees.snbt"])
        self.assertEqual((bees["id"], bees["order_index"], bees["loot_size"]),
                         ("0000000075A01016", 18 + 0x75A01016 % qe.LOCAL_ORDER_SPAN, 1))
        self.assertEqual([r["weight"] for r in bees["rewards"]], [1.0, 1.0])
        for lang, titles in (("en_us", ("A perfect gene", "Pick a bee")), ("es_es", ("Un gen perfecto", "Elegí una abeja"))):
            strings = json.loads(self.files[OUT / "lang" / f"{lang}.snbt"])
            self.assertEqual((strings["reward_table.000000003F3DF4D3.title"], strings["reward_table.0000000075A01016.title"]),
                             titles)
        # The SNBT FTB reads back: an int stays an int, a weight a double, the components a compound.
        self.assertIn('"purity": 100\n', self.files[OUT / "reward_tables" / "sector_bees_breeding__perfect_genes.snbt"])

    def test_every_other_file_stays_byte_identical(self):
        changed = {OUT / "chapters" / (self.CHAPTER + ".snbt"), OUT / "lang" / "en_us.snbt", OUT / "lang" / "es_es.snbt"}
        new = {OUT / "reward_tables" / f"{self.CHAPTER}__{n}.snbt" for n in ("perfect_genes", "starter_bees")}
        self.assertEqual({p for p in self.files if p not in self.base_files}, new)
        for path, content in self.files.items():
            if path not in changed | new:
                with self.subTest(path=path.name):
                    self.assertEqual(content, self.base_files[path])
        for lang in LOCALES:
            before = json.loads(self.base_files[OUT / "lang" / f"{lang}.snbt"])
            after = json.loads(self.files[OUT / "lang" / f"{lang}.snbt"])
            self.assertEqual({k: after[k] for k in before}, before)
            self.assertEqual(set(after) - set(before), {"reward_table.000000003F3DF4D3.title",
                                                         "reward_table.0000000075A01016.title"})
        # In the chapter itself only the three quests with rewards of their own change.
        moved = [q["id"] for q, d in zip(self.chapter["quests"], self.disk["quests"]) if q != d]
        self.assertEqual(moved, [stable_id("quest:" + k) for k in ("bees_crystalline", "bees_iron", "bees_diamond")])

    def test_bad_rewards_are_rejected(self):
        def rewards(specs):
            q = {"key": "probe", "rewards": specs}
            return lambda: qe.extra_rewards(q, self.CHAPTER, {}, self.gated)
        ok = gene("productivity", "productivity.very_high")
        cases = [(rewards([{"item": "minecraft:honeycomb", "count": 0}]), "count is 1..64"),
                 (rewards([{"item": "minecraft:honeycomb", "count": 65}]), "count is 1..64"),
                 (rewards([{"item": "minecraft:honeycomb", "count": "2"}]), "count is 1..64"),
                 (rewards([{"item": "minecraft:honeycomb", "amount": 2}]), "a reward takes item, count, components"),
                 (rewards([{"item": "Honeycomb"}]), "item id"),
                 (rewards([{"item": "entrelumen:heliodor_lens"}]), "ENTRELUMEN's own"),
                 (rewards([{"item": next(i for i in sorted(self.gated) if not i.startswith("entrelumen:"))}]),
                  "gated recipe"),
                 (rewards([{"item": "minecraft:stone", "components": {}}]), "components map"),
                 (rewards([{"item": "minecraft:stone", "components": {"Custom Data": 1}}]), "component type"),
                 (rewards([{"item": "minecraft:stone", "components": {"!minecraft:food": {}}}]), "removing one"),
                 (rewards([{"item": "minecraft:stone", "components": {"minecraft:custom_data": {"a": None}}}]), "no SNBT form"),
                 (rewards([{"item": "minecraft:stone", "components": {"minecraft:custom_data": {"a": [1, 2.5]}}}]), "mixes"),
                 (rewards([{"item": "minecraft:stone", "components": {"minecraft:custom_data": {"a": 2 ** 40}}}]), "int range"),
                 (rewards([{"item": "minecraft:stone", "components": {"minecraft:custom_data": {"a": float("nan")}}}]), "not a number"),
                 (rewards([ok, dict(ok)]), "repeats productivebees:gene"),
                 (rewards([]), "rewards is a list"),
                 (rewards({"item": "minecraft:stone"}), "rewards is a list"),
                 (lambda: qe.extra_rewards({"key": "probe", "reward_table": "nope"}, self.CHAPTER, {}, self.gated),
                  "not a reward table of sector_bees_breeding"),
                 # A chapter's tables are its own: another chapter's name does not resolve.
                 (lambda: qe.extra_rewards({"key": "probe", "reward_choice": "starter_bees"}, "sector_aa",
                                           {qe.local_table(self.CHAPTER, "starter_bees"): {"long": 1}}, self.gated),
                  "not a reward table of sector_aa")]
        for run, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(AssertionError, message):
                    run()
        # A free click never pays, not even with rewards of its own.
        tip = {"key": "probe", "task": {"type": "checkmark"}, "rewards": [{"item": "minecraft:honeycomb"}]}
        with self.assertRaisesRegex(AssertionError, "a free click never pays"):
            qe.sector_rewards(tip, "tip", "IV", self.book, {}, self.CHAPTER, self.gated)

    def test_bad_chapter_tables_are_rejected(self):
        def table(mutate):
            data = copy.deepcopy(self.data)
            mutate(data, data["reward_tables"]["perfect_genes"])
            return lambda: qe.build_local_tables([data], 18, self.gated)

        def unused(data, spec):
            next(q for q in data["quests"] if q["key"] == "bees_iron").pop("reward_table")

        def choice_rolls(data, spec):
            data["reward_tables"]["starter_bees"]["rolls"] = 2

        def rename(data, spec):
            data["reward_tables"]["Perfect-Genes"] = data["reward_tables"].pop("perfect_genes")
            next(q for q in data["quests"] if q["key"] == "bees_iron")["reward_table"] = "Perfect-Genes"
        cases = [(table(lambda d, s: s.update(rolls=0)), "rolls is 1..8"),
                 (table(lambda d, s: s.update(rolls=9)), "rolls is 1..8"),
                 (table(lambda d, s: s.update(entries=[])), "entries is a list"),
                 (table(lambda d, s: s["entries"][0].update(weight=0)), "weight > 0"),
                 (table(lambda d, s: s["entries"][0].update(weight=True)), "weight > 0"),
                 (table(lambda d, s: s["entries"].append(copy.deepcopy(s["entries"][0]))), "repeats productivebees:gene"),
                 (table(lambda d, s: s.update(icon="minecraft:honeycomb")), "a table takes title, rolls and entries"),
                 (table(lambda d, s: s["title"].pop("es_es")), "title in en_us and es_es"),
                 (table(lambda d, s: s["title"].update(en_us="[b|Genes]")), "title"),
                 (table(unused), "no quest uses it"),
                 (table(choice_rolls), "a choice gives one entry"),
                 (table(rename), "lowercase words joined by _")]
        for run, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(AssertionError, message):
                    run()

    def test_formatter_gives_rewards_a_row_and_each_table_a_line(self):
        import format_sector
        text = format_sector.dumps(self.data)
        self.assertEqual(json.loads(text), self.data)
        self.assertIn('\n      "rewards": [{"item": "productivebees:spawn_egg_configurable_bee", ', text)
        self.assertIn('\n    "perfect_genes": {"title": ', text)
        self.assertIn('\n    "starter_bees": {"title": ', text)

    def test_check_guides_checks_rewards_against_the_jars(self):
        # check_guides.py against a small stand-in of the pinned JARs (the real one reads them; CI has none).
        import tempfile
        from pathlib import Path
        import check_guides as cg
        items = {"productivebees:spawn_egg_configurable_bee", "productivebees:gene", "minecraft:honeycomb",
                 "minecraft:stone", "productivebees:configurable_honeycomb", "minecraft:diamond"}
        reg = {"advancements": set(), "entities": {"productivebees:configurable_bee", "productivebees:creeper_bee"},
               "structures": set(), "structure_tags": set(), "lang": set(), "item_tags": {}, "item_owner": {},
               "item_tags_all": {"c:storage_blocks/calorite": []}}
        not_empty = {"type": "neoforge:not", "value": {"type": "neoforge:tag_empty", "tag": "c:storage_blocks/calorite"}}
        components = {"types": {"minecraft": {"entity_data", "custom_data", "creative_slot_lock"},
                                "productivebees": {"gene_group", "bee_type"}},
                      "bees": {"productivebees:diamond": [[]], "productivebees:iron": [[]], "productivebees:gold": [[]],
                               "productivebees:calorite": [[not_empty]],
                               # defined twice: it loads when one of the two files does
                               "productivebees:butcher": [[{"type": "neoforge:not", "value": {
                                   "type": "neoforge:mod_loaded", "modid": "productivemetalworks"}}],
                                   [{"type": "neoforge:mod_loaded", "modid": "productivemetalworks"}]]}}
        saved = (cg._ITEMS, cg._SECTOR, cg._LOCKED, cg._COMPONENTS, cg._BEE_TAGS)
        cg._ITEMS, cg._SECTOR, cg._LOCKED, cg._COMPONENTS = (items, {"minecraft", "productivebees"}), reg, {"productivebees"}, components

        def run(mutate=lambda data: None):
            data = copy.deepcopy(self.data)
            data["quests"] = [q for q in data["quests"] if q["key"] in ("bees_diamond", "bees_iron", "bees_crystalline")]
            for q in data["quests"]:
                q.update(deps=[], icon="minecraft:diamond", task={"type": "checkmark"})
                q.pop("tasks", None)
                q["en_us"]["text"], q["es_es"]["text"] = ["Bees."], ["Abejas."]
            data.update(icon="minecraft:diamond", emblem="entrelumen:textures/gui/quests/px.png", art=[], figures={})
            mutate(data)
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "sector_probe.json"
                path.write_text(json.dumps(data), encoding="utf-8")
                errors = []
                cg.check_sector(path, errors, {q["key"] for q in data["quests"]})
            return " | ".join(errors)

        def diamond(data):
            return next(q for q in data["quests"] if q["key"] == "bees_diamond")
        try:
            self.assertEqual(run(lambda d: diamond(d)["rewards"].append(egg("productivebees:butcher"))), "")
            cases = [(lambda d: diamond(d)["rewards"].append({"item": "minecraft:nope"}), "item minecraft:nope not found"),
                     (lambda d: diamond(d)["rewards"].append({"item": "minecraft:stone", "count": 0}), "count 0 must be"),
                     (lambda d: d["reward_tables"]["perfect_genes"]["entries"][0].update(weight=0), "weight 0 must be"),
                     (lambda d: diamond(d)["rewards"].append(
                         {"item": "minecraft:stone", "components": {"minecraft:custom_datta": {}}}),
                      "minecraft:custom_datta is registered by no pinned JAR of minecraft"),
                     (lambda d: diamond(d)["rewards"].append(
                         {"item": "minecraft:stone", "components": {"nope:thing": 1}}), "no pinned JAR owns the namespace nope"),
                     (lambda d: diamond(d)["rewards"].append(
                         {"item": "minecraft:stone", "components": {"Custom Data": 1}}), "'Custom Data' is not namespace:path"),
                     (lambda d: diamond(d)["rewards"].append(
                         {"item": "minecraft:stone", "components": {"minecraft:creative_slot_lock": {}}}), "not persistent"),
                     (lambda d: diamond(d)["rewards"].append(
                         {"item": "minecraft:stone", "components": {"entrelumen:nope": 1}}), "not registered by the companion"),
                     (lambda d: diamond(d)["rewards"].append(egg("productivebees:diamnd")), "'productivebees:diamnd' is defined by no"),
                     (lambda d: diamond(d)["rewards"].append(egg("productivebees:calorite")), "switched off in this pack"),
                     (lambda d: diamond(d)["rewards"].append({"item": "productivebees:spawn_egg_configurable_bee", "components": {
                         "minecraft:entity_data": {"type": "productivebees:iron"}}}), "needs the entity \"id\""),
                     (lambda d: diamond(d)["rewards"].append(gene("productivity", "productivity.veryhigh")), "gene value"),
                     (lambda d: diamond(d)["rewards"].append(gene("speed", "speed.high")), "gene attribute 'speed'"),
                     (lambda d: diamond(d)["rewards"].append(gene("productivity", "productivity.high", 101)), "gene purity 101"),
                     (lambda d: diamond(d)["rewards"].append(gene("type", "productivebees:diamnd")), "type gene: bee type"),
                     (lambda d: diamond(d).update(reward_table="nope"), "reward_table 'nope' is not in this chapter"),
                     (lambda d: next(q for q in d["quests"] if q["key"] == "bees_iron").pop("reward_table"),
                      "reward table perfect_genes is used by no quest")]
            errors = run(lambda d: [mutate(d) for mutate, _ in cases])  # one run: each fault names itself
            for _, message in cases:
                with self.subTest(message=message):
                    self.assertIn(message, errors)
            self.assertEqual(errors.count(" | ") + 1, len(cases))
            self.assertIs(cg.condition_holds(not_empty, items, cg.known_item_tags(reg)), False)
            self.assertIsNone(cg.condition_holds({"type": "productivelib:fluid_tag_empty", "tag": "c:oil"}, items, {}))
        finally:
            cg._ITEMS, cg._SECTOR, cg._LOCKED, cg._COMPONENTS, cg._BEE_TAGS = saved

    def test_check_guides_reads_component_types_from_class_files(self):
        import check_guides as cg
        # A minimal class file: constant pool [Utf8 "bee_type", String #1, Utf8 DataComponentType, Long 7 (two slots)].
        def utf8(s):
            return b"\x01" + len(s).to_bytes(2, "big") + s.encode()
        pool = utf8("bee_type") + b"\x08\x00\x01" + utf8(cg.DATA_COMPONENT_TYPE) + b"\x05" + (7).to_bytes(8, "big") + utf8("tail")
        data = b"\xca\xfe\xba\xbe\x00\x00\x00\x41" + (7).to_bytes(2, "big") + pool
        literals, constants = cg.class_constants(data)
        self.assertEqual(literals, {"bee_type"})
        self.assertIn(cg.DATA_COMPONENT_TYPE, constants)
        self.assertIn("tail", constants)  # the long's second slot was skipped
        self.assertIsNone(cg.class_constants(b"not a class"))


if __name__ == "__main__":
    unittest.main()
