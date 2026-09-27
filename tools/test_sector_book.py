"""Contracts of the quest book v3 (docs/design/quest-book-v3.md): the sector engine (tools/quest_engine.py),
the two flagship sectors (Create in three chapters, Ars Nouveau), reward tables and loot crates, presets,
theme, custom shapes and the companion's ftbquests strings. Static only: FTB loading is checked on a
server (quest-book-v3.md, "QA"), and the look inside the client is still to be seen."""
import copy
import json
import math
import re
import unittest

import quest_engine as qe
import build_quest_placeholders as placeholders
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
                  "sector_create_addons": (20, 40), "sector_ars_nouveau": (60, 110),
                  "sector_ars_addons": (40, 70)}
        for name, data in self.sectors.items():
            roles = [q["role"] for q in data["quests"]]
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
                    self.assertEqual((q["shape"], q["size"]), (shape, size))
                    self.assertEqual(presets[q["preset"]], {"shape": shape, "size": size})
                    self.assertIn(f"entrelumen_{source['role']}", q["tags"])
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
                        self.assertRegex(img["image"], qe.TEXTURE)
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
        self.assertEqual(set(self.tables), set(spec))
        crates = [t for t in self.tables.values() if "loot_crate" in t]
        self.assertEqual(len(crates), 6)
        ids = set()
        for name, t in self.tables.items():
            with self.subTest(table=name):
                self.assertEqual(t["id"], f"{qe.table_id(name):016X}")
                self.assertLess(qe.table_id(name), 2 ** 31)  # read back exactly as an SNBT int (see table_id)
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


if __name__ == "__main__":
    unittest.main()
