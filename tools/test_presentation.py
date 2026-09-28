"""Contracts of presentation v2 (docs/design/quest-book-v3.md, "Presentación v2"): the text markup and fonts of
tools/quest_text.py, the canvas vocabulary and decor nodes of tools/quest_art.py, and the two pilot chapters.
Static: what a font or an image looks like in the client is still to be seen (the preview renderer imitates it).
Run on its own or through tools/test_sector_book.py, which imports these cases."""
import json
import math
import unittest

import quest_art
import quest_engine as qe
import quest_text
from generate_quests import LOCALES, OUT

PILOTS = ("sector_create_kinetics", "sector_mekanism_basics")


def ctx(accent="#E5A16A", presentation=2):
    c = {"items": set(), "names": set(), "keys": set(), "textures": set(), "entities": set(), "structures": set(),
         "advancements": set(), "accent": accent, "presentation": presentation}
    c["resolve_quest"] = lambda target, where: qe.stable_id("quest:" + target)
    c["resolve_chapter"] = lambda target, where: qe.stable_id("chapter:" + target)
    return c


class TextMarkup(unittest.TestCase):
    def test_lead_makes_the_whole_line_bold(self):
        line, (plain, tip) = qe.compile_paragraph("[lead] Half of early Create starts here.", "en_us", ctx(), "t")
        data = json.loads(line)
        self.assertEqual(data[0], {"text": "", "bold": True})   # the root style is inherited by every segment
        self.assertEqual(plain, "Half of early Create starts here.")

    def test_list_items_have_a_marker_and_no_blank_line_between_them(self):
        c = ctx()
        lines, first = qe.compile_text(["[lead] Three parts:", "[li] One.", "[li:create:andesite_alloy] Two.", "Three."],
                                       "en_us", c, "t")
        self.assertEqual(lines[1], "")                    # after the lead
        self.assertNotEqual(lines[3], "")                 # item right after item
        self.assertEqual(lines[4], "")                    # a plain paragraph gets its blank line back
        bullet = json.loads(lines[2])
        self.assertEqual(bullet[1], {"text": "• ", "color": "#E5A16A"})
        icon = json.loads(lines[3])[1]
        self.assertEqual(icon["font"], quest_text.FONT_ICONS)
        self.assertEqual(icon["color"], "#FFFFFF")        # the text colour would tint the glyph
        self.assertEqual(icon["hoverEvent"]["contents"]["id"], "create:andesite_alloy")
        self.assertIn("create:textures/item/andesite_alloy.png", c["glyphs"])

    def test_icons_take_names_items_and_textures(self):
        self.assertEqual(quest_text.icon_texture("right_click")[0], quest_text.NAMED["right_click"])
        self.assertEqual(quest_text.icon_texture("create:goggles"), ("create:textures/item/goggles.png", "create:goggles"))
        self.assertEqual(quest_text.icon_texture("minecraft:textures/block/andesite.png"),
                         ("minecraft:textures/block/andesite.png", None))
        with self.assertRaises(AssertionError):
            quest_text.icon_texture("not an id")

    def test_big_is_the_accent_font_and_short(self):
        seg = json.loads(qe.compile_paragraph("Links talk across [big|256] blocks.", "en_us", ctx("#7FD4F0"), "t")[0])[2]
        self.assertEqual(seg, {"text": "256", "font": quest_text.FONT_BIG, "color": "#7FD4F0"})
        with self.assertRaises(AssertionError):
            qe.compile_paragraph("[big|far too many characters]", "en_us", ctx(), "t")

    def test_placement_rules(self):
        bad = (["First.", "[lead] Not first."],                  # the lead opens the quest
               ["[big|5] opens the quest."],                     # big rises into the line above
               ["One.", "{page}", "[big|5] on a new page."],
               ["One.", "[li] a", "[li] [big|5] b"],             # an item has no blank line above
               ["{rule}", "One."])                               # a rule sits between paragraphs
        for paragraphs in bad:
            with self.subTest(paragraphs=paragraphs), self.assertRaises(AssertionError):
                qe.compile_text(paragraphs, "en_us", ctx(), "t")

    def test_callouts_and_the_v2_tip(self):
        v2 = json.loads(qe.compile_paragraph("[tip] Hacé varios.", "es_es", ctx(presentation=2), "t")[0])
        self.assertEqual(v2[1]["font"], quest_text.FONT_ICONS)
        self.assertEqual(v2[2]["text"], " La posta: ")
        v1 = json.loads(qe.compile_paragraph("[tip] Hacé varios.", "es_es", ctx(presentation=1), "t")[0])
        self.assertEqual(v1[1]["text"], "» ")             # chapters that did not opt in keep their prefix
        careful = json.loads(qe.compile_paragraph("[careful] Mind the arrow.", "en_us", ctx(), "t")[0])
        self.assertEqual(careful[2]["text"], " Careful: ")
        self.assertEqual(careful[2]["color"], quest_text.CALLOUTS["careful"][1])

    def test_rule_is_a_dim_line(self):
        line, _ = qe.compile_paragraph("{rule}", "en_us", ctx(), "t")
        self.assertIn("─", json.loads(line)[1]["text"])


class Fonts(unittest.TestCase):
    def test_big_font_doubles_the_vanilla_sheets_on_the_baseline(self):
        providers = quest_text.big_font()["providers"]
        bitmaps = [p for p in providers if p["type"] == "bitmap"]
        self.assertTrue(all(p["height"] == 16 and p["ascent"] == 14 for p in bitmaps))   # 2×, baseline at y+7
        ascii_sheet = next(p for p in bitmaps if p["file"] == "minecraft:font/ascii.png")
        self.assertEqual((len(ascii_sheet["chars"]), {len(r) for r in ascii_sheet["chars"]}), (16, {16}))
        self.assertEqual(ascii_sheet["chars"][3], "0123456789:;<=>?")
        nonlatin = next(p for p in bitmaps if "nonlatin" in p["file"])
        self.assertEqual(nonlatin["chars"][0][7], "×")
        self.assertEqual(providers[-1], {"type": "reference", "id": "minecraft:include/default"})

    def test_icon_font_has_one_glyph_per_texture(self):
        font = quest_text.icon_font({"create:textures/item/goggles.png", "minecraft:textures/gui/sprites/icon/info.png"})
        files = {p["file"]: p for p in font["providers"]}
        self.assertEqual(set(files), {"create:item/goggles.png", "minecraft:gui/sprites/icon/info.png"})
        self.assertTrue(all(p["height"] == 8 and p["ascent"] == 7 and len(p["chars"]) == 1 for p in files.values()))
        glyphs = [p["chars"][0] for p in files.values()]
        self.assertEqual(len(set(glyphs)), 2)
        self.assertTrue(all(0xE000 <= ord(g) <= 0xF8FF for g in glyphs))

    def test_generated_fonts_cover_every_glyph_in_the_book(self):
        font = json.loads((qe.ROOT / quest_text.FONT_DIR / "quest_icons.json").read_text(encoding="utf-8"))
        glyphs = {p["chars"][0] for p in font["providers"]}
        text = json.dumps([json.loads((OUT / "lang" / f"{lang}.snbt").read_text(encoding="utf-8")) for lang in LOCALES],
                          ensure_ascii=False)
        used = {ch for ch in text if 0xE100 <= ord(ch) <= 0xF8FF}
        self.assertTrue(used)
        self.assertLessEqual(used, glyphs)


class Canvas(unittest.TestCase):
    def compile(self, art, quests=("q",)):
        languages = {lang: {} for lang in LOCALES}
        c = ctx()
        return quest_art.art_images("sector_t", 0, art, {"line": "#FFFFFF", "panel": "#000000", "accent": "#E5A16A"},
                                    languages, c, {k: {} for k in quests}), languages, c

    def test_pictures_sprites_and_items(self):
        imgs, _, c = self.compile({"picture": "create:textures/gui/title/background/panorama_0.png", "x": 0, "y": 0, "w": 12,
                                   "h": 12, "alpha": 150, "reveal": "q"})
        self.assertEqual(imgs[0]["width"], qe.num(12 / qe.VISUAL))
        self.assertEqual(imgs[0]["dependency"], qe.stable_id("quest:q"))
        imgs, _, c = self.compile({"sprite": "minecraft:block/water_still", "cells": [[0, 0], [1, 0]], "cell": 1.0, "tint": "#3F76E4"})
        self.assertEqual([i["image"] for i in imgs], ["minecraft:block/water_still"] * 2)
        self.assertEqual(c["sprites"], {"minecraft:block/water_still"})
        imgs, _, c = self.compile({"item": "create:fluid_tank", "x": 1, "y": 2, "size": 2, "tint": "#FF0000"})
        self.assertEqual(imgs[0]["image"], "item:create:fluid_tank")
        self.assertNotIn("color", imgs[0])                # an item render ignores the tint
        self.assertIn("create:fluid_tank", c["items"])

    def test_bad_drawables_are_rejected(self):
        for spec in ("create:fluid_tank", "create:textures/block/axis", "#12345", "item:not an id"):
            with self.subTest(spec=spec), self.assertRaises(AssertionError):
                quest_art.picture_ref(spec)

    def test_paths_cut_textures_into_square_pieces_and_turn_the_grain(self):
        imgs, _, _ = self.compile({"path": [[0, 0], [4, 0]], "texture": "create:textures/block/axis.png", "width": 1.0, "step": 1.0,
                                   "turn": True})
        self.assertEqual(len(imgs), 4)
        self.assertTrue(all(i["rotation"] == 90.0 for i in imgs))
        self.assertAlmostEqual(imgs[0]["x"], 0.5)
        imgs, _, _ = self.compile({"path": [[0, 0], [0, 3], [3, 3]], "color": "#FFFFFF", "width": 0.1})
        self.assertEqual([i["rotation"] for i in imgs], [90.0, 0.0])

    def test_resample_and_smooth_keep_the_ends(self):
        pts = quest_art.resample([(0, 0), (10, 0)], 2.5)
        self.assertEqual((pts[0], pts[-1], len(pts)), ((0, 0), (10.0, 0.0), 5))
        curve = quest_art.smooth([(0, 0), (5, 5), (10, 0)], 6)
        self.assertEqual((curve[0], curve[-1]), ((0, 0), (10, 0)))

    def test_mosaic_merges_colour_runs(self):
        imgs, _, _ = self.compile({"mosaic": [0, 0], "cell": 0.5, "rows": ["aab", ".a."],
                                   "legend": {"a": "#FF0000", "b": "minecraft:textures/block/stone.png"}})
        self.assertEqual(len(imgs), 3)
        self.assertEqual(imgs[0]["width"], qe.num(1.0 / qe.VISUAL))

    def test_scatter_is_seeded_and_avoids(self):
        art = {"scatter": "minecraft:textures/particle/spark_3.png", "region": [0, 0, 10, 10], "count": 12, "seed": 3,
               "avoid": [[5, 5, 3]]}
        a, _, _ = self.compile(art)
        b, _, _ = self.compile(art)
        self.assertEqual(a, b)
        self.assertTrue(all(math.hypot(i["x"] - 5, i["y"] - 5) >= 3 for i in a))

    def test_text_rotates_and_lettering_shares_positions(self):
        imgs, languages, _ = self.compile({"text": {"en_us": "Brass", "es_es": "Latón"}, "x": 0, "y": 0, "scale": 2, "rotation": 30})
        self.assertEqual(imgs[0]["rotation"], 30.0)
        self.assertTrue(imgs[0]["text_on_image"])
        imgs, languages, _ = self.compile({"lettering": {"en_us": "RIVER", "es_es": "RÍO  "}, "path": [[0, 0], [8, 2]]})
        self.assertEqual(len(imgs), 5)                    # one per letter; a blank in both would be skipped
        with self.assertRaises(AssertionError):
            self.compile({"lettering": {"en_us": "RIVER", "es_es": "RÍO"}, "path": [[0, 0], [8, 2]]})

    def test_lines_and_panels_keep_reveal_and_click(self):
        imgs, _, _ = self.compile({"line": [[0, 0], [3, 0]], "reveal": "q"})
        self.assertEqual(imgs[0]["dependency"], qe.stable_id("quest:q"))
        imgs, _, _ = self.compile({"panel": [0, 0, 3, 2], "click": "quest:q"})
        self.assertEqual(imgs[0]["click_action"], "open_quest:" + qe.stable_id("quest:q"))

    def test_a_hover_note_gets_a_click_so_players_see_it(self):
        # ChapterImageButton.checkMouseOver: without a click action only editors get the tooltip
        for art in ({"line": [[0, 0], [3, 0]], "reveal": "q", "hover": {"en_us": "A note", "es_es": "Una nota"}},
                    {"glow": [0, 0], "reveal": "q", "hover": {"en_us": "A note", "es_es": "Una nota"}}):
            imgs, languages, _ = self.compile(art)
            with self.subTest(art=art):
                self.assertEqual(imgs[0]["click_action"], "open_quest:" + qe.stable_id("quest:q"))
                self.assertEqual(languages["es_es"][f"image.{imgs[0]['id']}.title"], "Una nota")
        imgs, languages, _ = self.compile({"path": [[0, 0], [4, 0]], "color": "#FFFFFF", "width": 0.2, "step": 1.0,
                                           "click": "quest:q", "hover": {"en_us": "River", "es_es": "Río"}})
        self.assertTrue(all(languages["en_us"][f"image.{i['id']}.title"] == "River" for i in imgs))
        warned = quest_art.lint({"chapter": "sector_t", "art": [{"id": "x", "texture": "a:textures/b.png", "x": 0, "y": 0,
                                                                  "hover": {"en_us": "n", "es_es": "n"}}]})
        self.assertEqual(len(warned), 1)

    def test_branch_captions_take_a_size(self):
        palette = {"accent": "#E5A16A"}
        self.assertEqual(quest_art.caption_style({"chapter": "c"}, {}, palette)["scale"], 1)       # untouched chapters
        self.assertEqual(quest_art.caption_style({"chapter": "c", "presentation": 2}, {}, palette)["scale"], 2)
        style = quest_art.caption_style({"chapter": "c"}, {"caption_scale": 1.5, "caption_tint": "#FFFFFF", "caption_bold": True}, palette)
        self.assertEqual(style, {"scale": 1.5, "color": "#FFFFFF", "bold": True})
        languages = {lang: {} for lang in LOCALES}
        img = qe.label("k", 0, 0, {"en_us": "Belts", "es_es": "Cintas"}, languages, **style)
        self.assertAlmostEqual(img["height"], 9 * 1.5 / qe.NODE_PX, places=3)

    def test_decor_is_a_toy_and_never_content(self):
        out = quest_art.decor({"key": "d", "task": {"type": "checkmark"}, "size": 1.5}, {"rewards": [{"type": "xp"}]})
        self.assertEqual((out["optional"], out["rewards"], out["hide_lock_icon"], out["disable_toast"]), (True, [], True, True))
        with self.assertRaises(AssertionError):
            quest_art.decor({"key": "d", "task": {"item": "minecraft:stone"}}, {})
        self.assertFalse(quest_art.is_counted({"role": "decor"}))


class Pilots(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = {name: json.loads((qe.SECTORS / f"{name}.json").read_text(encoding="utf-8")) for name in PILOTS}

    def test_pilots_opt_into_presentation_2_and_draw_their_page(self):
        for name, data in self.data.items():
            with self.subTest(chapter=name):
                self.assertEqual(data["presentation"], 2)
                self.assertNotIn("groups", data)          # scenes, not panels
                kinds = {quest_art.kind_of(a) for a in data["art"]}
                self.assertTrue({"picture", "path", "glow", "text"} <= kinds, kinds)
                self.assertTrue(any("reveal" in a for a in data["art"]))
                decor = [q for q in data["quests"] if q["role"] == "decor"]
                self.assertTrue(1 <= len(decor) <= 3)

    def test_every_pilot_quest_opens_with_a_lead_or_a_callout(self):
        for name, data in self.data.items():
            for q in data["quests"]:
                for lang in LOCALES:
                    first = q[lang]["text"][0]
                    with self.subTest(quest=q["key"], lang=lang):
                        self.assertTrue(first.startswith(("[lead]", "[tip]", "[i|")), first)


if __name__ == "__main__":
    unittest.main()
