"""Contracts of the presentation v2 kit: the text draft (tools/quest_draft.py), the sketch-first drawing helpers
(tools/quest_art.py: through, grow, sketch) and the render lock (tools/preview/render_lock.py).
No JAR is read: icons come from a stub, locks live in a temporary folder."""
import copy
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "preview"))
import quest_art  # noqa: E402
import quest_draft  # noqa: E402
import quest_engine as qe  # noqa: E402
import render_lock  # noqa: E402


def quest(en, es, key="k_test", role="step", task=None, title=("A thing", "Una cosa")):
    return {"key": key, "role": role, "deps": [], "task": task or {"item": "minecraft:stone"},
            "en_us": {"title": title[0], "text": en}, "es_es": {"title": title[1], "text": es}}


def draft(q, flat=None):
    data = {"chapter": "sector_test", "quests": [q]}
    d = quest_draft.draft_chapter(data, flat)
    return data["quests"][0], d


class Draft(unittest.TestCase):
    def test_sentences_keep_tags_and_abbreviations_whole(self):
        masked, tags = quest_draft.mask("Use [item:a:b|Thing. Two] now. Pick one, e.g. Iron. Then go! ¿Listo? Sí.")
        cut = [quest_draft.unmask(s, tags) for s in quest_draft.sentences(masked)]
        self.assertEqual(cut, ["Use [item:a:b|Thing. Two] now.", "Pick one, e.g. Iron.", "Then go!", "¿Listo?", "Sí."])

    def test_lead_then_a_list_of_short_sentences(self):
        q, d = draft(quest(["A [item:m:a|Widget] turns things. It spins fast. It hums. It never jams."],
                           ["Un [item:m:a|Aparato] gira cosas. Gira rápido. Zumba. Nunca se traba."]))
        self.assertEqual(q["en_us"]["text"], ["[lead] A [item:m:a|Widget] turns things.", "[li] It spins fast.",
                                              "[li] It hums.", "[li] It never jams."])
        self.assertEqual(q["es_es"]["text"][0], "[lead] Un [item:m:a|Aparato] gira cosas.")
        self.assertEqual(d.stats["drafted"], 1)

    def test_enumeration_becomes_intro_and_items(self):
        q, _ = draft(quest(["It starts here. The defaults: 2048 blocks, reach 16, 64 poles and 12 rows."],
                           ["Empieza acá. Los valores: 2048 bloques, alcance 16, 64 pértigas y 12 filas."]))
        self.assertEqual(q["en_us"]["text"], ["[lead] It starts here.", "The defaults:", "[li] [hl|2048] blocks",
                                              "[li] Reach [hl|16]", "[li] [hl|64] poles", "[li] [hl|12] rows"])
        self.assertEqual(q["es_es"]["text"][-1], "[li] [hl|12] filas")

    def test_a_phrase_with_commas_is_not_a_list(self):
        q, _ = draft(quest(["Two contacts make a pair. Facing each other they give a signal: the tidy way to tell a piston, "
                            "bearing or gantry where to stop."],
                           ["Dos contactos hacen un par. Enfrentados dan señal: la forma prolija de decirle a un pistón, "
                            "rodamiento o grúa dónde frenar."]))
        self.assertNotIn("[li]", " ".join(q["en_us"]["text"]))

    def test_clauses_become_items(self):
        q, _ = draft(quest(["A lever sits on a rail. Powered, it builds the cart; unpowered, it takes it apart."],
                           ["Una palanca va en un riel. Con señal, arma la vagoneta; sin señal, la desarma."]))
        self.assertEqual(q["en_us"]["text"][1:], ["[li] Powered, it builds the cart.", "[li] Unpowered, it takes it apart."])

    def test_warning_becomes_careful_without_its_lead_in(self):
        q, d = draft(quest(["A reactor makes power. Careful: past 100% it explodes."],
                           ["Un reactor da energía. Ojo: pasado el 100% explota."]))
        self.assertEqual(q["en_us"]["text"], ["[lead] A reactor makes power.", "[careful] Past [hl|100%] it explodes."])
        self.assertEqual(q["es_es"]["text"], ["[lead] Un reactor da energía.", "[careful] Pasado el [hl|100%] explota."])
        self.assertEqual(d.stats["careful"], 1)

    def test_pack_changes_become_notes(self):
        q, d = draft(quest(["A motor turns current into rotation. Pack change: the advanced one takes a Reinforced Alloy (act III)."],
                           ["Un motor vuelve la corriente rotación. Cambio del pack: el avanzado pide una Aleación reforzada (acto III)."]))
        self.assertEqual(q["en_us"]["text"], ["[lead] A motor turns current into rotation.",
                                              "[note] The advanced one takes a Reinforced Alloy (act III)."])
        self.assertEqual(q["es_es"]["text"][1], "[note] El avanzado pide una Aleación reforzada (acto III).")
        self.assertEqual(d.stats["note"], 1)
        # a recipe that merely names an act material stays in the text
        q, _ = draft(quest(["Iron and a Frame (act II) make it. It hums."], ["Hierro y un Marco (acto II) lo arman. Zumba."]))
        self.assertNotIn("[note]", " ".join(q["en_us"]["text"]))

    def test_icons_only_for_flat_items(self):
        flat = {"m:flat": "m:textures/item/flat.png"}.get
        q, _ = draft(quest(["Two parts make it. [item:m:flat|Gear] goes first. [item:m:cube|Block] goes next. Done then."],
                           ["Dos partes lo arman. [item:m:flat|Engranaje] va primero. [item:m:cube|Bloque] va después. Listo."]),
                     flat)
        self.assertEqual(q["en_us"]["text"][1:3], ["[li:m:flat] [item:m:flat|Gear] goes first.",
                                                   "[li] [item:m:cube|Block] goes next."])
        self.assertTrue(q["es_es"]["text"][1].startswith("[li:m:flat] "))

    def test_star_number_goes_big_where_big_may_stand(self):
        task = {"item": "minecraft:stone", "count": 32, "consume": True}
        q, d = draft(quest(["For the store.", "Bring 32 stones and 5 more."], ["Para el almacén.", "Traé 32 piedras y 5 más."],
                           role="bounty", task=task))
        self.assertEqual(q["en_us"]["text"][1], "Bring [big|32] stones and [hl|5] more.")
        self.assertEqual(q["es_es"]["text"][1], "Traé [big|32] piedras y [hl|5] más.")
        self.assertEqual(d.stats["big"], 1)
        # never in the first paragraph (the lead) and never on a page's first paragraph
        q, _ = draft(quest(["Bring 32 stones.", "{page}", "Keep 32 back."], ["Traé 32 piedras.", "{page}", "Guardá 32."],
                           role="bounty", task=task))
        self.assertNotIn("[big|", " ".join(q["en_us"]["text"]))

    def test_long_first_page_moves_to_page_two(self):
        long_en = ["A mill grinds ore.", "It takes a long while to spin up to speed, and every stone in the county has "
                   "to come by cart before the wheels can bite into it properly.", "The miller keeps a ledger of every sack "
                   "that goes in and every sack of flour that comes out, down to the last pinch.",
                   "Nobody has ever seen the miller sleep, and the village has theories about that."]
        long_es = ["Un molino muele mena.", "Tarda mucho en tomar velocidad, y cada piedra del condado tiene que llegar en "
                   "carro antes de que las ruedas la muerdan bien.", "El molinero anota cada bolsa que entra y cada bolsa de "
                   "harina que sale, hasta la última pizca.", "Nadie vio nunca dormir al molinero, y el pueblo tiene teorías."]
        q, d = draft(quest(long_en, long_es))
        text = q["en_us"]["text"]
        self.assertIn("{page}", text)
        self.assertEqual(text.count("{page}"), q["es_es"]["text"].count("{page}"))
        first = qe.compile_text(text, "en_us", quest_draft._ctx(), "t")[1]
        self.assertLessEqual(len(first), qe.MAX_PAGE_CHARS)
        self.assertEqual(d.stats["moved"], 1)

    def test_mismatched_languages_are_left_alone(self):
        q, d = draft(quest(["One. Two.", "Three."], ["Uno. Dos. Tres."]))
        self.assertEqual(q["en_us"]["text"], ["One. Two.", "Three."])
        self.assertTrue(any("paragraphs" in note for _, note in d.notes))

    def test_v2_quests_are_left_alone(self):
        text = (["[lead] Done already.", "[li] Yes."], ["[lead] Ya está.", "[li] Sí."])
        q, d = draft(quest(*map(list, text)))
        self.assertEqual(q["en_us"]["text"], list(text[0]))
        self.assertEqual(d.stats["v2"], 1)

    def test_every_sector_drafts_without_breaking_a_rule(self):
        """The draft of every v1 chapter compiles (a quest that would not keeps its text, so none should)."""
        for data in qe.load_sectors():
            if data.get("presentation", 1) >= 2:
                continue
            d = quest_draft.draft_chapter(copy.deepcopy(data))
            broken = [(k, n) for k, n in d.notes if "breaks a compile rule" in n or n.startswith("kept v1")]
            self.assertEqual(broken, [], data["chapter"])


class Drawing(unittest.TestCase):
    """through, grow and sketch (quest_art)."""

    def images(self, art, placed=None, keys=("a", "b", "c")):
        by_key = {k: {"key": k} for k in keys}
        ctx = {"textures": set(), "items": set(), "sprites": set(), "placed": placed or {}}
        languages = {lang: {} for lang in qe.LOCALES}
        return quest_art.art_images("sector_test", 0, art, {"line": "#FFFFFF", "panel": "#000000", "accent": "#FFFFFF"},
                                    languages, ctx, by_key)

    def test_a_path_through_quests_grows_stretch_by_stretch(self):
        placed = {"a": (0.0, 0.0), "b": (4.0, 0.0), "c": (4.0, 4.0)}
        imgs = self.images({"id": "river", "through": ["a", [2.0, 1.0], "b", "c"], "color": "#3F76E4", "grow": True}, placed)
        deps = [i.get("dependency") for i in imgs]
        b, c = qe.stable_id("quest:b"), qe.stable_id("quest:c")
        self.assertEqual(deps, [b, b, c])   # a -> point -> b appear with b; b -> c with c
        self.assertTrue(all(d for d in deps))

    def test_grow_lists_one_quest_per_segment(self):
        imgs = self.images({"id": "p", "path": [[0, 0], [1, 0], [2, 0]], "color": "#FFFFFF", "grow": [None, "c"]})
        self.assertEqual([i.get("dependency") for i in imgs], [None, qe.stable_id("quest:c")])

    def test_sketch_is_a_faint_copy_there_from_the_start(self):
        imgs = self.images({"id": "mural", "picture": "minecraft:textures/painting/kebab.png", "x": 0, "y": 0, "w": 4,
                            "h": 4, "reveal": "a", "sketch": True})
        sketch, ink = imgs
        self.assertNotIn("dependency", sketch)
        self.assertLessEqual(sketch["alpha"], quest_art.FAINT)
        self.assertLess(sketch["order"], ink["order"])
        self.assertEqual(ink["dependency"], qe.stable_id("quest:a"))
        pencil = self.images({"id": "p", "through": ["a", "b"], "texture": "create:textures/block/axis.png", "grow": True,
                              "sketch": {"alpha": 40}}, {"a": (0, 0), "b": (3, 0)})
        self.assertTrue(pencil[0]["image"].endswith("px.png") and "dependency" not in pencil[0] and pencil[0]["alpha"] == 40)

    def test_items_cannot_be_sketched_and_grow_is_for_paths(self):
        with self.assertRaises(AssertionError):
            self.images({"id": "i", "item": "minecraft:stone", "x": 0, "y": 0, "sketch": True})
        with self.assertRaises(AssertionError):
            self.images({"id": "g", "glow": [0, 0], "grow": True})
        with self.assertRaises(AssertionError):
            self.images({"id": "p", "through": ["a", "b"], "color": "#FFFFFF", "grow": True, "reveal": "a"},
                        {"a": (0, 0), "b": (1, 0)})


class RenderLock(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.path = Path(self.dir.name) / "render.lock"
        self.log = []

    def tearDown(self):
        self.dir.cleanup()

    def lock(self, **kw):
        return render_lock.RenderLock(self.path, log=self.log.append, sleep=lambda s: None, **kw)

    def test_taken_and_given_back(self):
        with self.lock() as held:
            owner = json.loads((self.path / "owner.json").read_text(encoding="utf-8"))
            self.assertEqual(owner["pid"], os.getpid())
            self.assertTrue(held.held)
        self.assertFalse(self.path.exists())

    def test_a_live_owner_makes_the_next_render_wait(self):
        with self.lock(slots=1):
            ticks = iter(range(0, 10 ** 6, 60))
            other = self.lock(max_wait=120, clock=lambda: next(ticks), slots=1)
            with self.assertRaises(TimeoutError):
                other.acquire()
        self.assertTrue(any("waiting" in line for line in self.log))

    def test_two_slots_let_a_second_render_in_and_make_the_third_wait(self):
        with self.lock(slots=2) as first, self.lock(slots=2) as second:
            self.assertEqual((first.path.name, second.path.name), ("render.lock", "render.lock.2"))
            ticks = iter(range(0, 10 ** 6, 60))
            third = self.lock(max_wait=120, clock=lambda: next(ticks), slots=2)
            with self.assertRaises(TimeoutError):
                third.acquire()
        self.assertFalse(self.path.exists())
        self.assertFalse(self.path.with_name("render.lock.2").exists())

    def test_a_dead_owner_is_cleared(self):
        self.path.mkdir()
        (self.path / "owner.json").write_text(json.dumps({"pid": 999999, "started": 1}), encoding="utf-8")
        with self.lock():
            self.assertEqual(json.loads((self.path / "owner.json").read_text(encoding="utf-8"))["pid"], os.getpid())
        self.assertTrue(any("stale" in line for line in self.log))

    def test_a_folder_with_other_things_is_never_cleared(self):
        self.path.mkdir()
        (self.path / "owner.json").write_text(json.dumps({"pid": 999999, "started": 1}), encoding="utf-8")
        (self.path / "notes.txt").write_text("mine", encoding="utf-8")
        with self.assertRaises(render_lock.LockError):
            self.lock().acquire()
        self.assertTrue((self.path / "notes.txt").exists())

    def test_waits_while_memory_is_short(self):
        reads = iter([1 * 2 ** 30, 1.2 * 2 ** 30, 2 * 2 ** 30])
        slept = []
        free = render_lock.wait_for_memory(read=lambda: next(reads), sleep=slept.append, log=self.log.append,
                                           minimum=int(1.5 * 2 ** 30))
        self.assertEqual((free, len(slept)), (2 * 2 ** 30, 2))
        ticks = iter(range(0, 10 ** 6, 600))
        with self.assertRaises(TimeoutError):
            render_lock.wait_for_memory(read=lambda: 2 ** 29, sleep=lambda s: None, clock=lambda: next(ticks),
                                        max_wait=1200, log=self.log.append)


if __name__ == "__main__":
    unittest.main()
