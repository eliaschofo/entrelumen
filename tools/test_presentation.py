"""Contracts of presentation v2 (docs/design/quest-book-v3.md, "Presentación v2"): the text markup and fonts of
tools/quest_text.py, the canvas vocabulary and decor nodes of tools/quest_art.py, the two pilot chapters, and v2 for the
guide and story chapters (tools/quest_v2.py) with their two pilots, the draft and the format that come with it.
Static: what a font or an image looks like in the client is still to be seen (the preview renderer imitates it).
Run on its own or through tools/test_sector_book.py, which imports these cases."""
import json
import math
import unittest

import quest_art
import quest_client
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
        glyphs = set()
        for name in ("quest_icons", "quest_tiles"):   # inline icons, and canvas sheets cut into tiles (quest_client)
            font = json.loads((qe.ROOT / quest_text.FONT_DIR / f"{name}.json").read_text(encoding="utf-8"))
            glyphs |= {p["chars"][0][0] for p in font["providers"]}
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
        # each piece reaches SEAM under the next one (FTB rounds position and size apart); the last ends the path
        self.assertAlmostEqual(imgs[0]["x"], 0.5 + quest_art.SEAM / 2)
        self.assertAlmostEqual(imgs[0]["height"], qe.num((1.0 + quest_art.SEAM) / qe.VISUAL))
        self.assertAlmostEqual(imgs[-1]["x"], 3.5)
        self.assertAlmostEqual(imgs[-1]["height"], qe.num(1.0 / qe.VISUAL))
        faint, _, _ = self.compile({"path": [[0, 0], [4, 0]], "texture": "create:textures/block/axis.png", "width": 1.0,
                                    "step": 1.0, "alpha": 120})
        self.assertAlmostEqual(faint[0]["x"], 0.5)        # a translucent overlap would draw a darker joint
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


SHEETS = {"sheets": {
    "m:textures/block/glow.png": {"kind": "animation", "size": [16, 64], "tile": [16, 16]},
    "m:textures/gui/flow.png": {"kind": "animation", "size": [16, 32], "tile": [16, 16]},
    "m:textures/block/tiles.png": {"kind": "fusion", "size": [80, 16], "tile": [16, 16]}}}


def chapter_of(*images):
    return {"id": "C", "filename": "c", "quests": [], "images": [dict(i) for i in images]}


def picture(ref, **extra):
    return {"id": "I" + str(abs(hash(ref)) % 1000), "x": 0.0, "y": 0.0, "width": 1.0, "height": 1.0, "rotation": 0.0,
            "image": ref, **extra}


class ClientDraw(unittest.TestCase):
    """What FTB Quests' client really draws, fixed at compile time (tools/quest_client.py)."""

    def setUp(self):
        quest_client.reset()

    def fix(self, *images, facts=SHEETS):
        languages = {lang: {} for lang in LOCALES}
        return quest_client.fix_chapter(chapter_of(*images), languages, facts)["images"], languages

    def test_an_animated_atlas_texture_draws_as_its_sprite(self):
        imgs, _ = self.fix(picture("m:textures/block/glow.png", color=0x336699, alpha=120))
        self.assertEqual(imgs[0]["image"], "m:block/glow")   # the atlas animates it; ImageIcon drew the strip
        self.assertEqual((imgs[0]["color"], imgs[0]["alpha"]), (0x336699, 120))
        self.assertIn("m:textures/block/glow.png", quest_client.SEEN["textures"])

    def test_other_sheets_draw_their_first_tile_as_a_glyph(self):
        imgs, languages = self.fix(picture("m:textures/block/tiles.png", color=0x80C0FF), picture("m:textures/gui/flow.png"))
        tile, flow = imgs
        self.assertEqual(tile["image"], "")
        self.assertTrue(tile["text_on_image"])
        self.assertAlmostEqual(tile["width"], 10 / 9, places=3)   # the glyph advances 10 px for 9 drawn
        self.assertAlmostEqual(tile["x"], (10 / 9 - 1) / 2 * qe.VISUAL, places=3)   # the glyph square keeps its centre
        self.assertNotIn("color", tile)
        glyphs = quest_client.tile_glyphs(SHEETS)
        for lang in LOCALES:
            title = json.loads(languages[lang][f"image.{tile['id']}.title"])
            self.assertEqual(title[1], {"text": glyphs["m:textures/block/tiles.png"], "font": quest_client.FONT_TILES,
                                        "color": "#80C0FF"})
        self.assertEqual(flow["image"], "")                  # animated, but outside the atlas: its first frame
        self.assertNotIn("m:textures/block/glow.png", glyphs)

    def test_the_tile_font_cuts_each_sheet_into_its_grid(self):
        font = quest_client.tile_font(SHEETS)["providers"]
        by_file = {p["file"]: p for p in font}
        self.assertEqual(set(by_file), {"m:block/tiles.png", "m:gui/flow.png"})
        self.assertEqual(by_file["m:block/tiles.png"]["chars"], [chr(quest_client.TILE_FIRST + 0) + "\u0000" * 4])
        self.assertEqual(len(by_file["m:gui/flow.png"]["chars"]), 2)
        self.assertEqual((by_file["m:gui/flow.png"]["height"], by_file["m:gui/flow.png"]["ascent"]), (9, 7))

    def test_a_translucent_or_noted_sheet_stays_and_is_reported(self):
        imgs, _ = self.fix(picture("m:textures/block/tiles.png", alpha=60))
        self.assertEqual(imgs[0]["image"], "m:textures/block/tiles.png")
        self.assertEqual(quest_client.KEPT[0][2:], ("m:textures/block/tiles.png", "a glyph cannot be translucent"))

    def test_textures_without_facts_are_left_alone(self):
        imgs, _ = self.fix(picture("m:textures/block/stone.png"), picture(qe.PX, color=0xFFFFFF))
        self.assertEqual([i["image"] for i in imgs], ["m:textures/block/stone.png", qe.PX])

    def test_flat_items_draw_as_sprites_in_canvas_order(self):
        facts = dict(SHEETS, items={"m:gem": "m:item/gem", "m:machine": None})
        imgs, _ = self.fix(picture("item:m:gem", width=1.0, height=1.5, alpha=90, order=-1), facts=facts)
        gem = imgs[0]
        self.assertEqual(gem["image"], "m:item/gem")      # an atlas sprite: drawn at the canvas' depth, in order
        self.assertEqual((gem["width"], gem["height"]), (1.0, 1.0))   # ItemIcon drew the shorter side
        self.assertNotIn("alpha", gem)                    # the render ignored alpha and tint; the sprite would not
        self.assertEqual(gem["order"], -1)
        self.assertIn("m:gem", quest_client.SEEN["items"])

    def test_3d_items_on_a_node_are_reported(self):
        facts = dict(SHEETS, items={"m:machine": None})
        chapter = chapter_of(dict(picture("item:m:machine"), id="ON", x=0.3), dict(picture("item:m:machine"), id="OFF", x=3.0))
        chapter["quests"] = [{"id": "Q", "x": 0.0, "y": 0.0, "size": 1.0}]
        chapter["quest_links"] = [{"id": "L", "x": 6.0, "y": 0.0, "linked_quest": "Q2"}]
        chapter["images"].append(dict(picture("item:m:machine"), id="LINK", x=6.4))
        quest_client.fix_chapter(chapter, {lang: {} for lang in LOCALES}, facts)
        self.assertEqual(quest_client.OVER_NODES, [("c", "ON", "m:machine", "Q"), ("c", "LINK", "m:machine", "Q2")])
        self.assertEqual(chapter["images"][0]["image"], "item:m:machine")

    def test_the_facts_name_sprites_for_flat_items_only(self):
        for item, sprite in quest_client.load_facts()["items"].items():
            if sprite:
                with self.subTest(item=item):
                    ns, path = item.split(":", 1)
                    self.assertEqual(sprite, f"{ns}:item/{path}")

    def test_touching_tiles_overlap_under_the_later_opaque_one(self):
        w = 1.0 / qe.VISUAL   # one grid unit
        a = picture(qe.PX, id="A", x=0.0, width=w, height=w, color=0x112233)
        b = picture(qe.PX, id="B", x=1.0, width=w, height=w, color=0x445566)
        under = picture(qe.PX, id="C", x=0.0, y=1.0, width=w, height=w, color=0x778899, alpha=100)
        imgs, _ = self.fix(a, b, under)
        self.assertAlmostEqual(imgs[0]["x"], quest_client.SEAM / 2)            # A reaches under B, drawn after it
        self.assertAlmostEqual(imgs[0]["width"], qe.num(w + quest_client.SEAM / qe.VISUAL))
        self.assertEqual(imgs[0]["height"], w)                                 # C below is translucent: no overlap
        self.assertEqual((imgs[1]["x"], imgs[1]["width"]), (1.0, w))           # B is on top: nothing to hide it

    def test_seams_stay_where_the_overlap_would_show(self):
        w = 1.0 / qe.VISUAL
        a = picture(qe.PX, id="A", x=0.0, width=w, height=w)
        cases = {
            "later tile translucent": picture(qe.PX, id="B", x=1.0, width=w, height=w, alpha=200),
            "later tile shown later": picture(qe.PX, id="B", x=1.0, width=w, height=w, dependency="Q"),
            "later tile shorter": picture(qe.PX, id="B", x=1.0, y=0.25, width=w, height=w / 2),
            "drawn before": picture(qe.PX, id="B", x=1.0, width=w, height=w, order=-5),
        }
        for why, b in cases.items():
            with self.subTest(why):
                imgs, _ = self.fix(a, b)
                self.assertEqual((imgs[0]["x"], imgs[0]["width"]), (0.0, w))

    def test_texture_tiles_overlap_by_a_texel_at_most(self):
        w = 0.5 / qe.VISUAL
        a = picture("m:textures/block/stone.png", id="A", x=0.0, width=w, height=w)
        b = picture("m:textures/block/stone.png", id="B", x=0.0, y=0.5, width=w, height=w)
        imgs, _ = self.fix(a, b)
        self.assertAlmostEqual(imgs[0]["height"], qe.num(w + 0.5 / 16 / qe.VISUAL))   # a texel of a 16 px texture
        self.assertAlmostEqual(imgs[0]["y"], 0.5 / 32, places=4)

    def test_colour_fills_turned_by_right_angles_are_unturned(self):
        imgs, _ = self.fix(picture(qe.PX, id="Q", width=4.0, height=0.1, rotation=90.0, color=1),
                           picture(qe.PX, id="H", width=4.0, height=0.1, rotation=-180.0, color=1),
                           picture(qe.PX, id="T", width=4.0, height=0.1, rotation=-90.0, color=1),
                           picture(qe.PX, id="D", width=4.0, height=0.1, rotation=45.0, color=1),
                           picture("m:textures/block/stone.png", id="X", width=4.0, height=0.1, rotation=90.0))
        q, h, t, d, x = imgs
        self.assertEqual((q["width"], q["height"], q["rotation"]), (0.1, 4.0, 0.0))   # FTB can cull it now
        self.assertEqual((h["width"], h["height"], h["rotation"]), (4.0, 0.1, 0.0))
        self.assertEqual((t["width"], t["height"], t["rotation"]), (0.1, 4.0, 0.0))
        self.assertEqual(d["rotation"], 45.0)                                        # a diagonal stays turned
        self.assertEqual((x["width"], x["rotation"]), (4.0, 90.0))                    # a texture keeps its grain

    def test_every_spanish_locale_reads_the_spanish_strings(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            hand = root / "pack/resourcepacks/entrelumen/assets/m/lang/es_es.json"
            hand.parent.mkdir(parents=True)
            hand.write_text('{"a": "Che"}', encoding="utf-8")
            book = root / "quests/lang/es_es.snbt"
            copies = quest_client.spanish_copies(root, {book: "{}", root / "quests/lang/en_us.snbt": "{}"})
        self.assertEqual(set(copies), {p.with_name(f"{loc}{p.suffix}") for p in (hand, book) for loc in quest_client.SPANISH})
        self.assertEqual(copies[hand.with_name("es_ar.json")], '{"a": "Che"}')
        self.assertEqual(copies[book.with_name("es_mx.snbt")], "{}")
        self.assertEqual(set(quest_client.SPANISH), {"es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve"})

    def test_the_generated_copies_match_es_es(self):
        sources = [OUT / "lang/es_es.snbt"]
        for base in ("companion/src/main/resources/assets", "pack/resourcepacks/entrelumen/assets"):
            sources += sorted((qe.ROOT / base).glob("*/lang/es_es.json"))
        self.assertGreaterEqual(len(sources), 4)
        for source in sources:
            for locale in quest_client.SPANISH:
                with self.subTest(file=str(source.relative_to(qe.ROOT)), locale=locale):
                    copy = source.with_name(locale + source.suffix)
                    self.assertEqual(copy.read_text(encoding="utf-8"), source.read_text(encoding="utf-8"))

    def test_the_facts_file_names_frames_like_minecraft(self):
        import check_guides as cg
        self.assertEqual(cg.frame_size({}, 16, 256), [16, 16])
        self.assertEqual(cg.frame_size({"height": 8}, 16, 64), [16, 8])
        self.assertEqual(cg.frame_size({"width": 32}, 32, 128), [32, 128])
        facts = quest_client.load_facts()
        self.assertEqual(json.loads(quest_client.facts_text(facts)), facts)
        for texture, sheet in facts["sheets"].items():
            with self.subTest(texture=texture):
                self.assertNotEqual(sheet["tile"], sheet["size"])
                self.assertIn(sheet["kind"], ("animation",) + cg.CONNECTED)


V2_PILOTS = {"guide": "guide_entrelumen_start", "story": "the_lost_crafts"}


def strong_share(images):
    """(strong images, share shown before any quest): tools/check_guides.py image_budget's sketch-first measure."""
    strong = [i for i in images if i["image"].startswith("item:") or i.get("alpha", 255) > quest_art.FAINT]
    fresh = [i for i in strong if not i.get("dependency")]
    return len(strong), len(fresh) / len(strong)


class GuidesAndStory(unittest.TestCase):
    """Presentation v2 for guides and story chapters (tools/quest_v2.py): opt-in, the same text and art as a sector,
    the story's own rules kept, and the kit (draft, format, checks) working on both."""

    @classmethod
    def setUpClass(cls):
        import generate_quests as gq
        cls.gq = gq
        cls.book = gq.load_book()
        cls.story = gq.load_chapters()
        cls.guides = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(gq.GUIDES.glob("guide_*.json"))]
        cls.guide = next(g for g in cls.guides if g["chapter"] == V2_PILOTS["guide"])
        cls.act = next(c for c in cls.story if c["chapter"] == V2_PILOTS["story"])
        sectors = qe.load_sectors()
        cls.names = {g["chapter"] for g in cls.guides + cls.story + sectors}
        cls.keys = {q["key"] for g in cls.guides + cls.story + sectors for q in g["quests"]}

    def compile_guide(self, data):
        languages = {lang: {} for lang in LOCALES}
        chapter = self.gq.generate_guide(data, "g", 0, self.book, languages, set(), self.keys, self.names)
        return chapter, languages

    def compile_story(self, chapters=None):
        chapters = chapters or self.story
        files = self.gq.generate_all(chapters, self.book)
        return ({c["chapter"]: json.loads(files[OUT / "chapters" / (c["chapter"] + ".snbt")]) for c in chapters},
                {lang: json.loads(files[OUT / "lang" / (lang + ".snbt")]) for lang in LOCALES})

    def test_chapters_that_do_not_opt_in_carry_nothing_of_v2(self):
        import copy
        import quest_v2
        # Every guide and story chapter is in presentation v2 since 30/9, so the v1 side is a made-up guide.
        for data in self.guides + self.story:
            if data.get("presentation", 1) < 2:
                self.assertFalse(quest_v2.enabled(data))
        data = {"chapter": "guide_v1", "quests": [{"key": "v1_quest", "en_us": ["Title", "Plain &etext&r."],
                                                   "es_es": ["Título", "Texto &eplano&r."]}]}
        self.assertFalse(quest_v2.enabled(copy.deepcopy(data)))
        for change in ({"art": []}, {"motif": "travel"}):
            with self.subTest(change=change), self.assertRaises(AssertionError):
                quest_v2.enabled({**data, **change})
        q = data["quests"][0]
        q["en_us"] = {"title": q["en_us"][0], "text": ["[lead] A v2 paragraph."]}
        with self.assertRaises(AssertionError):
            quest_v2.enabled(data)

    def test_a_v2_guide_compiles_like_a_sector(self):
        chapter, languages = self.compile_guide(self.guide)
        quests = {q["id"]: q for q in chapter["quests"]}
        welcome = languages["en_us"][f"quest.{qe.stable_id('quest:entrelumen_start_welcome')}.quest_desc"]
        self.assertEqual(json.loads(welcome[0])[0], {"text": "", "bold": True})          # [lead]
        pedestal = languages["en_us"][f"quest.{qe.stable_id('quest:entrelumen_start_pedestal')}.quest_desc"]
        clicks = [seg.get("clickEvent") for line in pedestal if line.startswith("[") for seg in json.loads(line)[1:]]
        self.assertIn({"action": "change_page", "value": qe.stable_id("chapter:guide_entrelumen_compass")}, clicks)
        decor = [q for q in self.guide["quests"] if q.get("role") == "decor"]
        self.assertTrue(1 <= len(decor) <= 3)
        for q in decor:
            out = quests[qe.stable_id("quest:" + q["key"])]
            self.assertEqual((out["shape"], out["rewards"], out["optional"], out["tags"]),
                             ("none", [], True, ["entrelumen_decor"]))
            self.assertFalse(quest_art.is_counted(q))
        pictures = [i["image"] for i in chapter["images"]]
        self.assertEqual(pictures[:2], ["entrelumen:textures/gui/quests/medallion.png", self.guide["emblem"]])
        self.assertTrue(any(i.get("dependency") for i in chapter["images"]))

    def test_a_v2_story_chapter_keeps_its_campaign_and_draws_its_scene(self):
        compiled, languages = self.compile_story()
        c = compiled[V2_PILOTS["story"]]
        campaign = [q for q in c["quests"] if q["tasks"][0]["type"] == "entrelumen:campaign"]
        self.assertEqual(len(campaign), len(self.act["milestones"]))
        numeral = [i for i in c["images"] if i["image"].endswith("numeral_2.png")]
        self.assertEqual(len(numeral), 1)
        drawn_top = min(i["y"] - i["height"] * qe.VISUAL / 2 for i in c["images"]
                        if not i["image"].startswith("entrelumen:textures/gui/quests/") and not i.get("text_on_image"))
        self.assertLess(numeral[0]["y"], drawn_top)                                 # the title block stays above the scene
        entry = languages["es_es"][f"quest.{qe.stable_id('quest:crafts_welcome')}.quest_desc"]
        self.assertEqual(entry[:2], [self.act["layout_groups"]["archive"]["es_es"], ""])   # the route label first, as in v1
        glitch = [seg for line in languages["en_us"][f"quest.{qe.stable_id('quest:crafts_archive')}.quest_desc"]
                  if line.startswith("[") for seg in json.loads(line)[1:] if seg.get("obfuscated")]
        self.assertEqual([g["text"] for g in glitch], ["Te"])                         # the Atlas's interference survives

    def test_the_story_keeps_its_own_rules_in_v2(self):
        import copy
        import quest_v2
        key = "crafts_press"

        def text(q):
            return q["en_us"]["text"]
        breaks = {
            "lore last": lambda q: text(q).append("[li] A list item at the end."),
            "no subtitle": lambda q: q["en_us"].update(subtitle="A subtitle"),
            "no & codes": lambda q: text(q).__setitem__(0, "[lead] Press &eiron&r ingots."),
            "two glitches": lambda q: text(q).__setitem__(-1, "[glitch|a] [glitch|b] [glitch|c] and the lore."),
            "short glitch": lambda q: text(q).__setitem__(-1, "The lore, [glitch|far too long]."),
            "story links": lambda q: text(q).__setitem__(-1, "See [chapter:guide_entrelumen_start|the guide] for the lore."),
        }
        for name, change in breaks.items():
            data = copy.deepcopy(self.act)
            change(next(x for x in data["quests"] if x["key"] == key))
            chapters = [data if c["chapter"] == data["chapter"] else c for c in self.story]
            with self.subTest(rule=name), self.assertRaises(AssertionError):
                self.compile_story(chapters)
        q = next(x for x in self.act["quests"] if x["key"] == key)
        self.assertTrue(quest_v2.copy_lines(q, self.act["chapter"], ctx(), story=True))

    def test_glitch_and_strike_are_markup(self):
        line = json.loads(qe.compile_paragraph("A voice: …[glitch|those] who [strike|have] arrived.", "en_us", ctx(), "t")[0])
        self.assertEqual(line[2], {"text": "those", "obfuscated": True})
        self.assertEqual(line[4], {"text": "have", "strikethrough": True})

    def test_pilots_start_as_a_sketch_within_the_image_budget(self):
        guide, _ = self.compile_guide(self.guide)
        story = self.compile_story()[0][V2_PILOTS["story"]]
        for name, chapter in ((V2_PILOTS["guide"], guide), (V2_PILOTS["story"], story)):
            strong, share = strong_share(chapter["images"])
            with self.subTest(chapter=name):
                self.assertLessEqual(len(chapter["images"]), 700)
                self.assertLessEqual(share, 0.4, f"{share:.0%} of {strong} strong images show on a fresh book")

    def test_the_draft_turns_v1_guides_and_story_into_v2(self):
        import copy
        import quest_draft
        import quest_v2
        v1 = [c for c in self.guides + self.story if c.get("presentation", 1) < 2]
        for original in v1:
            data = copy.deepcopy(original)
            d = quest_draft.draft_chapter(data)
            story = quest_draft.lore_last(data)
            fixes = {k for k, note in d.notes if note.startswith("fix by hand")}
            with self.subTest(chapter=data["chapter"]):
                self.assertEqual(data["presentation"], 2)
                for a, b in zip(original["quests"], data["quests"]):
                    self.assertEqual({k: v for k, v in a.items() if k not in LOCALES},
                                     {k: v for k, v in b.items() if k not in LOCALES})     # keys, tasks, sources, layout
                    for lang in LOCALES:
                        self.assertEqual(b[lang]["title"], a[lang][0])
                        self.assertNotRegex(" ".join(b[lang]["text"]), r"&[0-9a-fk-or]")
                        if story:   # the lore stays whole and last
                            self.assertEqual(b[lang]["text"][-1],
                                             quest_draft.codes_to_markup(a[lang][1].split("\n\n")[-1].strip())[0])
                    if b["key"] not in fixes:
                        quest_v2.copy_lines(b, data["chapter"], quest_draft._ctx(), story=story)

    def test_codes_become_markup(self):
        import quest_draft
        self.assertEqual(quest_draft.codes_to_markup("Use the &eHeliodor Compass&r now."),
                         ("Use the [hl|Heliodor Compass] now.", []))
        self.assertEqual(quest_draft.codes_to_markup("…&kthose&r who &mhave&r arrived."),
                         ("…[glitch|those] who [strike|have] arrived.", []))
        self.assertEqual(quest_draft.codes_to_markup("&lBold&r and &oitalic&r.")[0], "[b|Bold] and [i|italic].")

    def test_the_kit_covers_v2_guides_and_story(self):
        import check_guides
        import format_sector
        self.assertLessEqual(set(V2_PILOTS.values()), check_guides.v2_chapters())      # image budget, sketch-first
        files = format_sector.v2_chapter_files()
        self.assertLessEqual({"guide_entrelumen_start.json", "act_two.json"}, {p.name for p in files})
        for path in files:
            self.assertEqual(format_sector.dumps(json.loads(path.read_text(encoding="utf-8"))),
                             path.read_text(encoding="utf-8"), path.name)


if __name__ == "__main__":
    unittest.main()

