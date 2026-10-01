"""Generate ENTRELUMEN's original bilingual FTB quest book with stable global IDs.

Run with --check for read-only validation and generated-file drift detection.
No third-party content is read. IDs are signed-positive 64-bit hashes of semantic
keys, independent of ordering and translated text. Chapters may carry an optional
subtitle (FTB chapter_subtitle); '&' formatting codes are validated so the Atlas's
interference stays parseable and legible. Runtime verification remains required:
static validation cannot prove FTB loading or campaign synchronization.

The book (25 September 2026, docs/design/quest-book.md): a hub chapter, the story
chapters, the optional inventory branch and the guides of content/guides in five
chapter groups. The story follows a node grammar (shape and size by role), every
chapter gets the reading width of content/quest_book.json, and chapter images
(numerals, act emblems, branch corners and labels, suns and medallions) are placed
around the nodes from the layout itself. Rewards follow the table in quest_book.json.
The FTB theme that colours the nodes ships in the companion (assets/ftbquests).

Quest book v3 (docs/design/quest-book-v3.md): sector chapters (content/sectors) compiled by
tools/quest_engine.py, reward tables and loot crates (reward_tables/), node presets in data.snbt, the
extended theme, custom node shapes and the companion's ftbquests-namespace strings.

Presentation v2 (tools/quest_v2.py): a guide or a story chapter with "presentation": 2 takes the sectors' text
markup and canvas art; one without it compiles exactly as before.

What the real client draws (tools/quest_client.py, 1 October 2026): every compiled chapter goes through a last pass
that corrects what FTB Quests' client would draw or open differently from what the book means (sheets drawn whole,
item renders above the nodes, seams, rotated fills never culled, links that open hidden quests), and
every Spanish strings file gets a copy for the other Spanish locales, which FTB and Minecraft match exactly. Its
facts about textures come from tools/quest_client_facts.json, which tools/check_guides.py keeps in sync with the JARs.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import math
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quest_client  # noqa: E402
import quest_engine  # noqa: E402
import quest_v2  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "pack/config/ftbquests/quests"
BOOK = ROOT / "content/quest_book.json"
GUIDES = ROOT / "content/guides"
THEME = ROOT / "companion/src/main/resources/assets/ftbquests/ftb_quests_theme.txt"
FTBQ_LANG = ROOT / "companion/src/main/resources/assets/ftbquests/lang"
TASK_IDS = ROOT / "tools/task_ids.json"   # sector task ID -> content signature (quest_engine.task_ids)
LOCALES = ("en_us", "es_es")
# Acts renumbered 24 September 2026: act V is two chapters (the plan and the activation), act VI Solsticio.
CHAPTER_SOURCES = ("first_hour.json", "act_two.json", "act_three.json", "act_four.json",
                   "act_five.json", "act_five_activation.json", "act_six.json", "inventory_that_remembers.json")

# Geometry at FTB Quests' default zoom 16 (QuestScreen.getQuestButtonSize = zoom*3/2 and
# getQuestButtonSpacing = zoom*quest_spacing/4, theme quest_spacing 1.0): one grid unit is 28 GUI
# pixels and an element of size s (node or image) is drawn 24*s pixels wide. So a node covers 6/7
# of its units, an item icon (2/3 of the node, QuestButton.draw) is 16 px at size 1, and an image
# drawn at an integer texel scale k is width_px*k/24 units wide.
NODE_PX, GRID_PX = 24, 28
VISUAL = NODE_PX / GRID_PX
QUESTS_ART = "entrelumen:textures/gui/quests/"
ART_PX = {"sun_heliodor": (128, 128), "medallion": (64, 64), "corner": (16, 16), "divider": (96, 9),
          **{f"numeral_{n}": (w, 45) for n, w in zip(range(1, 7), (27, 51, 75, 63, 39, 63))},
          **{f"act_{n}": (32, 32) for n in range(1, 7)}}

# Story node grammar: shape and size by role. Sizes 1, 2 and 3 keep 16x16 icons on whole texels.
GRAMMAR = {"finale": ("hexagon", 3.0), "milestone": ("hexagon", 2.0), "journey": ("octagon", 2.0),
           "task": ("square", 1.0), "optional": ("diamond", 1.0), "info": ("circle", 0.75)}
# Campaign milestones the server observes (arrivals and the Heart), not projects delivered at the Atlas.
OBSERVED = {"aether_arrival", "twilight_arrival", "bumblezone_arrival", "end_arrival", "heart_recovered"}
ACTS = ("I", "II", "III", "IV", "V", "VI")
# Upper bound of Minecraft default-font advances (font/include/default.json, 1.21.1) for label boxes:
# a box at least twice the text width keeps text_on_image at an exact 2x scale.
WIDE_GLYPHS = {"@": 7, "~": 7, "«": 7, "»": 7, "–": 7, "—": 9}
LABEL_COLOR = "&#E8DCB5"


def load_chapters():
    return [json.loads((ROOT / "content" / name).read_text(encoding="utf-8")) for name in CHAPTER_SOURCES]


def load_book():
    return json.loads(BOOK.read_text(encoding="utf-8"))


def load_guides(book=None):
    """Guides by the book's group order (see group_order)."""
    book = book or load_book()
    guides = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(GUIDES.glob("guide_*.json"))]
    names = {g["chapter"] for g in guides} | {s["chapter"] for s in quest_engine.load_sectors()}
    assert all(c in names for g in book["groups"] for c in g["lead"] + [g["emblem_guide"]]), "unknown guide in quest_book.json"
    return sorted(guides, key=lambda g: group_order(g, book))


def group_order(g, book):
    """Order inside the book: group, then the group's "lead" chapters, then 'any' and acts I-VI, then
    English title. Guides and sector chapters share it."""
    groups = {x["id"]: x for x in book["groups"]}
    rank = {"any": 0, **{a: i + 1 for i, a in enumerate(ACTS)}}
    lead = groups[g["group"]]["lead"]
    first = lead.index(g["chapter"]) if g["chapter"] in lead else len(lead)
    return (list(groups).index(g["group"]), first, rank[g["act"]], g["title"]["en_us"], g["chapter"])


def stable_id(key):
    value = int.from_bytes(hashlib.sha256(("entrelumen:v1:" + key).encode()).digest()[:8], "big")
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def snbt(value):
    # JSON is a valid quoted-key subset of SNBT; integer task counts fit int32.
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def num(value):
    return round(float(value), 6)


# FTB Library parses '&' formatting codes in quest text (0-9, a-f, k-o, r and &#RRGGBB).
# The Atlas speaks through interference: struck-through words (&m) and short glitches (&k).
FORMAT_CODE = re.compile(r"&(#[0-9A-Fa-f]{6}|[0-9a-fk-or])")
MAX_GLITCH_CHARS = 8
MAX_GLITCHES = 2


def check_formatting(text, where, allow_codes):
    """Reject text that FTB would fail to parse, style that bleeds or glitches that hide the story."""
    stripped = FORMAT_CODE.sub("", text)
    assert "&" not in stripped, f"invalid or stray formatting code: {where}"
    if not allow_codes:
        assert stripped == text, f"formatting codes are only allowed in descriptions: {where}"
        return
    glitches = 0
    for paragraph in text.split("\n\n"):
        open_style, glitch = False, None
        for part in re.split(r"(&(?:#[0-9A-Fa-f]{6}|[0-9a-fk-or]))", paragraph):
            if FORMAT_CODE.fullmatch(part):
                if glitch is not None:
                    assert 0 < len(glitch.strip()) <= MAX_GLITCH_CHARS, f"unreadable glitch: {where}"
                    glitch = None
                open_style = part != "&r"
                if part == "&k":
                    glitches += 1
                    glitch = ""
            elif glitch is not None:
                glitch += part
        assert not open_style and glitch is None, f"formatting must be reset before the paragraph ends: {where}"
    assert glitches <= MAX_GLITCHES, f"too many glitches to stay legible: {where}"


def paragraphs(text):
    """FTB draws each quest_desc entry as its own block; an empty entry keeps a blank line between paragraphs."""
    out = []
    for part in text.split("\n\n"):
        out += ([""] if out else []) + [part]
    return out


def finale_of(quests):
    """The chapter's closing milestone: the only campaign quest no other local campaign quest depends on."""
    local = {q["key"] for q in quests if "milestone" in q}
    needed = {dep for q in quests if "milestone" in q for dep in q["deps"] if dep in local}
    ends = [key for key in local if key not in needed]
    assert len(ends) <= 1, f"a chapter closes with one milestone, found {sorted(ends)}"
    return ends[0] if ends else None


def story_role(q, finale):
    if "milestone" in q:
        if q["key"] == finale:
            return "finale"
        return "journey" if q["milestone"] in OBSERVED else "milestone"
    if q.get("type") == "checkmark":
        return "info"
    return "optional" if q.get("optional") else "task"


def min_distance(a, b):
    """Centre distance two nodes need so that their drawn shapes keep a visible gap."""
    return (a + b) / 2 * VISUAL + 0.4


def label_px(text):
    assert all(c.isprintable() for c in text), f"label with control characters: {text!r}"
    return sum(WIDE_GLYPHS.get(c, 6) for c in text)


def image(key, x, y, w, h, picture="", **extra):
    out = {"id": stable_id("image:" + key), "x": num(x), "y": num(y), "width": num(w), "height": num(h),
           "rotation": num(extra.pop("rotation", 0.0)), "image": picture}
    quest_engine.IMAGE_KEYS[out["id"]] = key
    out.update(extra)
    return out


def art(name, key, x, y, scale, **extra):
    """A quest-book texture at an exact texel scale (zoom 16)."""
    w, h = ART_PX[name]
    return image(key, x, y, w * scale / NODE_PX, h * scale / NODE_PX, QUESTS_ART + name + ".png", **extra)


def wrap_label(text, max_px):
    """Split 'Name · phrase' labels into lines no wider than max_px (upper-bound font widths)."""
    lines = []
    for part in text.split(" · "):
        line = ""
        for word in part.split():
            candidate = (line + " " + word).strip()
            if line and label_px(candidate) > max_px:
                lines.append(line)
                line = word
            else:
                line = candidate
        lines.append(line)
    return lines


def label(key, x, y, texts, languages, scale=2, color=LABEL_COLOR, max_px=None, anchor="center"):
    """Text drawn by FTB on an empty image (text_on_image); the box keeps the text at an exact scale.
    Lines are joined with a literal backslash-n, which FTB Library's TextComponentParser turns into
    line breaks; anchor 'top' or 'bottom' keeps that edge at y whatever the number of lines."""
    lines = {lang: wrap_label(texts[lang], max_px) if max_px else [texts[lang]] for lang in LOCALES}
    for lang in LOCALES:
        check_formatting(texts[lang], f"{key}/{lang}", False)
        assert not re.search(r"[{}\\]", texts[lang]), f"{key}: braces and backslashes are FTB substitutes"
    widest = max(label_px(line) for lang in LOCALES for line in lines[lang])
    rows = max(len(lines[lang]) for lang in LOCALES)
    for lang in LOCALES:
        # Same row count in every locale: FTB fits the text to the box, so a shorter locale would scale up.
        lines[lang] += [" "] * (rows - len(lines[lang]))
    h = 9 * scale * rows / NODE_PX
    cy = {"center": y, "top": y + h * VISUAL / 2, "bottom": y - h * VISUAL / 2}[anchor]
    img = image(key, x, cy, (widest * scale + 4) / NODE_PX, h, "",
                text_on_image=True, text_shadow=True, order=5)
    for lang in LOCALES:
        languages[lang][f"image.{img['id']}.title"] = color + "\\n".join(lines[lang])
    return img


def extents(nodes):
    """Drawn bounding box, in grid units, of (x, y, size) nodes."""
    return (min(x - s * VISUAL / 2 for x, y, s in nodes), min(y - s * VISUAL / 2 for x, y, s in nodes),
            max(x + s * VISUAL / 2 for x, y, s in nodes), max(y + s * VISUAL / 2 for x, y, s in nodes))


def crosses(segment, img):
    """Whether a dependency line (grid units) passes through an image's drawn box."""
    (ax, ay), (bx, by) = segment
    hw, hh = img["width"] * VISUAL / 2, img["height"] * VISUAL / 2
    x0, x1, y0, y1 = img["x"] - hw, img["x"] + hw, img["y"] - hh, img["y"] + hh
    for i in range(41):
        t = i / 40
        x, y = ax + (bx - ax) * t, ay + (by - ay) * t
        if x0 < x < x1 and y0 < y < y1:
            return True
    return False


def decorate_story(data, chapter, finale, languages, hub_id, caption_scale=1, drawn=(), title_color=None):
    """Images around the nodes: numeral and act emblem (back to the hub), branch corners with their
    label, and the Sun of Heliodor behind the finale or, when the chapter asks for it, behind everything.
    A v2 chapter draws its branch captions larger (caption_scale: "book": {"caption_scale"}, 2 by default), keeps
    the title block above its art too (drawn) and writes its name beside the emblem (title_color, scale 3)."""
    book = data.get("book", {})
    name = data["chapter"]
    act = data["act"]
    quests = {q["key"]: q for q in data["quests"]}
    placed = [(q["x"], q["y"], q["size"]) for q in chapter["quests"]]
    x0, y0, x1, y1 = extents(placed)
    images = []
    if book.get("panels", True):
        by_key = {q["key"]: q["layout"] for q in data["quests"]}
        segments = [((by_key[d]["x"], by_key[d]["y"]), (q["layout"]["x"], q["layout"]["y"]))
                    for q in data["quests"] for d in q["deps"] if d in by_key and not by_key[d].get("hide_dependent_lines")]

        def compact(members, box):
            return not any(box[0] < q["layout"]["x"] < box[2] and box[1] < q["layout"]["y"] < box[3]
                           for q in data["quests"] if q not in members)
        panels = []
        for group, labels in data["layout_groups"].items():
            # A branch panel frames the small nodes; its milestones stand outside, below it. A group
            # interleaved with others (a spine, scattered tips) gets no panel.
            members = [q for q in data["quests"] if q["layout"]["group"] == group and "milestone" not in q]
            # A checkmark note of another group that only follows this branch hangs inside its panel.
            keys = {q["key"] for q in members}
            members += [q for q in data["quests"] if q not in members and q.get("type") == "checkmark" and q["deps"]
                        and all(d in keys for d in q["deps"])]
            if len(members) >= 2:
                box = extents([(q["layout"]["x"], q["layout"]["y"], q["layout"]["size"]) for q in members])
                box = [box[0] - 0.55, box[1] - 0.55, box[2] + 0.55, box[3] + 0.55]
                if compact(members, box):
                    panels.append((group, labels, members, box))
        for group, labels, members, box in panels:
            # Panels that start on the same row end on the same row, so their captions line up.
            aligned = box[:3] + [max(b[3] for _, _, _, b in panels if abs(b[1] - box[1]) < 0.3)]
            if compact(members, aligned):
                box[3] = aligned[3]
        for group, labels, members, (bx0, by0, bx1, by1) in panels:
            c = 16 / NODE_PX * VISUAL / 2
            for i, (cx, cy) in enumerate(((bx0 + c, by0 + c), (bx1 - c, by0 + c), (bx1 - c, by1 - c), (bx0 + c, by1 - c))):
                images.append(art("corner", f"{name}:{group}:corner{i}", cx, cy, 1, rotation=90.0 * i))
            width_px = max((bx1 - bx0) * GRID_PX, 120)
            # The caption goes under the panel unless a dependency line crosses it there.
            below = label(f"{name}:{group}:label", (bx0 + bx1) / 2, by1 + 0.25, labels, languages,
                          scale=caption_scale, max_px=width_px, anchor="top")
            if any(crosses(seg, below) for seg in segments):
                above = label(f"{name}:{group}:label", (bx0 + bx1) / 2, by0 - 0.25, labels, languages,
                              scale=caption_scale, max_px=width_px, anchor="bottom")
                if not any(crosses(seg, above) for seg in segments):
                    below = above
            images.append(below)
    if book.get("numeral", True):
        # Title block above the nodes and the panels: numeral, act emblem (back to the hub), divider.
        top_drawn = min([y0] + [i["y"] - i["height"] * VISUAL / 2 for i in images + list(drawn)])
        left = min([x0] + [i["x"] - i["width"] * VISUAL / 2 for i in images + list(drawn)])
        nw, nh = ART_PX[f"numeral_{act}"]
        numeral_w, numeral_h = nw * 2 / NODE_PX * VISUAL, nh * 2 / NODE_PX * VISUAL
        emblem_w = 64 / NODE_PX * VISUAL
        divider_w, divider_h = 96 * 2 / NODE_PX * VISUAL, 9 * 2 / NODE_PX * VISUAL
        top = top_drawn - 0.6 - divider_h - 0.45 - numeral_h
        images.append(art(f"numeral_{act}", f"{name}:numeral", left + numeral_w / 2, top + numeral_h / 2, 2))
        images.append(art(f"act_{act}", f"{name}:emblem", left + numeral_w + 0.4 + emblem_w / 2, top + numeral_h / 2, 2,
                          click_action=f"open_quest:{hub_id}"))
        for lang in LOCALES:
            languages[lang][f"image.{images[-1]['id']}.title"] = data["title"][lang]
        images.append(art("divider", f"{name}:divider", left + divider_w / 2, top + numeral_h + 0.45 + divider_h / 2, 2))
        if title_color:
            names = {lang: data["title"][lang].split(" · ", 1)[-1] for lang in LOCALES}
            images.append(quest_engine.label(f"{name}:title", left + numeral_w + 0.4 + emblem_w + 0.5, top + numeral_h / 2,
                                             names, languages, scale=3, color=title_color, align="start"))
    sun = book.get("sun")
    if sun:
        images.append(art("sun_heliodor", f"{name}:sun", sun["x"], sun["y"], sun["scale"], order=-1))
    # A v2 chapter starts as a sketch: the finale's sun and medallion are the prize of the finale.
    prize = {"dependency": stable_id("quest:" + finale)} if finale and title_color else {}
    if finale and book.get("finale_sun", True):
        f = quests[finale]["layout"]
        images.append(art("sun_heliodor", f"{name}:finale_sun", f["x"], f["y"], 1, order=-1, **prize))
    if finale and book.get("finale_medallion"):
        f = quests[finale]["layout"]
        images.append(art("medallion", f"{name}:finale_medallion", f["x"], f["y"], 2, order=-1, **prize))
    return images


def story_rewards(q, role, act, book):
    table = book["rewards"]["story"]
    key, rewards = q["key"], []
    xp = {"task": table["task_xp"], "optional": table["task_xp"], "milestone": table["milestone_xp"],
          "journey": table["milestone_xp"], "finale": table["finale_xp"]}.get(role)
    if xp:
        rewards.append({"id": stable_id(f"reward:{key}:xp"), "type": "xp", "xp": xp[act - 1]})
    item = {"milestone": table["milestone_item"], "journey": table["milestone_item"], "finale": table["finale_item"]}.get(role)
    if item:
        item_id, count = item[act - 1]
        rewards.append({"id": stable_id(f"reward:{key}:item"), "type": "item", "item": {"id": item_id, "count": 1}, "count": count})
    return rewards


def colour_tag(q, guide=False):
    if q.get("optional"):
        return "entrelumen_optional"
    return "entrelumen_guide" if guide else "entrelumen_story"


def generate(data, all_quests=None, order_index=0, book=None):
    book = book or load_book()
    quests = data["quests"]
    assert quests, "empty chapter"
    keys = [q["key"] for q in quests]
    assert len(keys) == len(set(keys)), "duplicate semantic quest key"
    visiting, done, ids = set(), set(), set()

    def ident(key):
        result = stable_id(key)
        assert result != "0000000000000000" and result not in ids, "ID collision"
        ids.add(result)
        return result

    by_key = {q["key"]: q for q in (all_quests or quests)}
    local_keys = set(keys)

    def visit(key):
        assert key in by_key, f"missing dependency: {key}"
        assert key not in visiting, f"dependency cycle: {key}"
        if key in done:
            return
        visiting.add(key)
        for dep in by_key[key]["deps"]:
            visit(dep)
        visiting.remove(key)
        done.add(key)

    for key in keys:
        visit(key)
    v2 = quest_v2.enabled(data)
    finale = finale_of(quests)
    positions = []
    assert data["layout_groups"] and all(set(labels) == set(LOCALES) for labels in data["layout_groups"].values())
    for q in quests:
        layout = q["layout"]
        assert layout["group"] in data["layout_groups"], "unknown layout group"
        assert all(isinstance(layout[c], (int, float)) and math.isfinite(layout[c]) for c in ("x", "y", "size"))
        decor = v2 and quest_v2.is_decor(q)
        role = "decor" if decor else story_role(q, finale)
        if not decor:
            assert (layout["shape"], float(layout["size"])) == GRAMMAR[role], f"node grammar: {q['key']} is a {role}"
        assert isinstance(layout.get("hide_dependent_lines", False), bool)
        for previous in positions:
            assert math.hypot(layout["x"]-previous["x"], layout["y"]-previous["y"]) >= min_distance(layout["size"], previous["size"]), \
                f"overlapping quest nodes: {q['key']}"
        positions.append(layout)
        if not data.get("book", {}).get("radial") and not decor:
            for dep in q["deps"]:
                if dep in local_keys:
                    assert by_key[dep]["layout"]["y"] <= layout["y"], "dependency runs against reading direction"

    assert data["autofocus"] in local_keys and not any(dep in local_keys for dep in by_key[data["autofocus"]]["deps"]), "focus must point to the chapter entry"
    chapter_id = ident("chapter:" + data["chapter"])
    languages = {lang: {f"chapter.{chapter_id}.title": data["title"][lang]} for lang in LOCALES}
    for lang in LOCALES:
        check_formatting(data["title"][lang], f"{data['chapter']}/title/{lang}", False)
        for group, labels in data["layout_groups"].items():
            check_formatting(labels[lang], f"{data['chapter']}/{group}/{lang}", False)
    if "subtitle" in data:
        # Chapter presentation: FTB reads chapter_subtitle as a list of lines.
        assert set(data["subtitle"]) == set(LOCALES), "subtitle locale mismatch"
        for lang in LOCALES:
            lines = data["subtitle"][lang]
            assert isinstance(lines, list) and lines and all(isinstance(line, str) and line.strip() for line in lines)
            for line in lines:
                check_formatting(line, f"{data['chapter']}/subtitle/{lang}", False)
            languages[lang][f"chapter.{chapter_id}.chapter_subtitle"] = lines
    act = data.get("act")
    assert act is None or act in range(1, 7), "act must be 1..6"
    chapter = {"id": chapter_id, "filename": data["chapter"], "order_index": order_index,
               "icon": {"id": "entrelumen:atlas"}, "default_quest_shape": "square",
               "default_min_width": book["min_width"],
               "autofocus_id": stable_id("quest:" + data["autofocus"]), "quests": []}
    milestones = {}
    seen_text = {lang: set() for lang in LOCALES}
    if v2:
        # Links in a story chapter reach its own story: generate() sees no guide or sector.
        def no_chapter_links(target, where):
            raise AssertionError(f"{where}: a story chapter links quests of the story, not chapters")

        def story_quest(target, where):
            assert target in by_key, f"{where}: link to unknown story quest {target}"
            return stable_id("quest:" + target)
        ctx = quest_v2.context(data, book, story_quest, no_chapter_links)
    for q in quests:
        key = q["key"]
        qid, tid = ident("quest:" + key), ident("task:" + key)
        kinds = sum(field in q for field in ("item", "milestone", "type"))
        assert kinds == 1, f"ambiguous task: {key}"
        task = {"id": tid}
        if "milestone" in q:
            assert q["milestone"] not in milestones, "duplicate campaign milestone"
            task.update(type="entrelumen:campaign", milestone=q["milestone"])
            milestones[q["milestone"]] = {"quest_id": qid, "task_id": tid}
        elif "item" in q:
            assert re.fullmatch(r"[a-z0-9_]+:[a-z0-9_/]+", q["item"])
            assert 1 <= q.get("count", 1) <= 64
            task.update(type="item", item={"id": q["item"], "count": 1}, count=q.get("count", 1), consume_items=False)
        else:
            assert q["type"] == "checkmark" and q.get("optional"), "checkmarks must be optional learning tasks"
            task.update(type="checkmark")
        layout = q["layout"]
        decor = v2 and quest_v2.is_decor(q)
        role = "decor" if decor else story_role(q, finale)
        output = {"id": qid, "x": float(layout["x"]), "y": float(layout["y"]),
                  "shape": layout["shape"], "size": float(layout["size"]),
                  "hide_dependent_lines": layout.get("hide_dependent_lines", False),
                  "dependencies": [stable_id("quest:" + dep) for dep in q["deps"]],
                  "icon": {"id": q.get("icon", q.get("item", "minecraft:book"))}, "tasks": [task],
                  "rewards": story_rewards(q, role, act, book) if act else [],
                  "tags": [colour_tag(q)]}
        if q.get("optional"):
            output["optional"] = True
        if v2:
            quest_v2.node_flags(q, output)
            if decor:
                quest_v2.decor(q, output)
            texts = quest_v2.copy_lines(q, data["chapter"], ctx, story=not data.get("book", {}).get("branch"))
        chapter["quests"].append(output)
        placeholders = []
        for lang in LOCALES:
            title, description = quest_v2.title_of(q, lang), quest_v2.description_of(q, lang)
            shown = texts[lang][1] if v2 else description
            assert title.strip() and (decor or len(shown) >= 80), f"missing text: {key}/{lang}"
            check_formatting(title, f"{key}/title/{lang}", False)
            if not v2:
                check_formatting(description, f"{key}/{lang}", True)
            assert description not in seen_text[lang], "duplicate description"
            seen_text[lang].add(description)
            languages[lang][f"quest.{qid}.title"] = title
            if v2 and q[lang].get("subtitle"):   # only the optional branch may have one (quest_v2.copy_lines)
                languages[lang][f"quest.{qid}.quest_subtitle"] = q[lang]["subtitle"]
            # Keep the route label separate from prose; no hardcoded UI shortcut keys.
            lines = texts[lang][0] if v2 else paragraphs(description)
            languages[lang][f"quest.{qid}.quest_desc"] = [data["layout_groups"][layout["group"]][lang], "", *lines]
            placeholders.append(re.findall(r"%[0-9$]*[sd]|\{[a-zA-Z_][a-zA-Z_0-9]*\}", title + description))
        assert sorted(placeholders[0]) == sorted(placeholders[1]), f"placeholder mismatch: {key}"
    drawn = []
    if v2:
        placed = {q["key"]: (q["layout"]["x"], q["layout"]["y"]) for q in quests}
        drawn = quest_v2.art_images(data, book, languages, ctx, {q["key"]: q for q in quests}, placed)
    if act:
        caption = data.get("book", {}).get("caption_scale", 2) if v2 else 1
        chapter["images"] = decorate_story(data, chapter, finale, languages, stable_id("chapter:" + book["hub"]["chapter"]),
                                           caption, drawn, quest_v2.palette(data, book)["accent"] if v2 else None)
    if drawn:
        chapter.setdefault("images", []).extend(drawn)
    assert languages["en_us"].keys() == languages["es_es"].keys(), "locale key mismatch"
    assert set(milestones) == set(data.get("milestones", ["atlas_awakened", "travellers_table", "lens_assembled", "field_survey", "first_signal"]))
    files = {OUT / "chapters" / (data["chapter"] + ".snbt"): snbt(chapter),
             OUT / "data.snbt": snbt({"version": 13, "default_consume_items": False, "default_reward_team": True,
                                         "default_autoclaim_rewards": "disabled", "fallback_locale": "en_us", "pause_game": True}),
             OUT / "chapter_groups.snbt": snbt({"chapter_groups": []}),
             ROOT / "content/campaign_task_ids.json": snbt(milestones)}
    for lang in LOCALES:
        files[OUT / "lang" / (lang + ".snbt")] = snbt(languages[lang])
    return files


def generate_all(chapters, book=None):
    """The story chapters (order 1..n; the hub takes 0)."""
    book = book or load_book()
    keys=[q['key'] for data in chapters for q in data['quests']]
    assert len(keys)==len(set(keys)), 'duplicate global quest key'
    assert len({data['chapter'] for data in chapters})==len(chapters), 'duplicate chapter'
    all_quests=[q for data in chapters for q in data['quests']]
    files={}; languages={lang:{} for lang in LOCALES}; milestones={}; ids=set(); descriptions={lang:set() for lang in LOCALES}
    for index,data in enumerate(chapters):
        assert set(data['title'])==set(LOCALES)
        generated=generate(data,all_quests,index+1,book)
        chapter_path=OUT/'chapters'/(data['chapter']+'.snbt')
        chapter=json.loads(generated[chapter_path])
        new_ids=[chapter['id']]+[i for q in chapter['quests'] for i in (q['id'],q['tasks'][0]['id'])]
        assert not ids.intersection(new_ids), 'global ID collision'
        ids.update(new_ids)
        for lang in LOCALES:
            for q in data['quests']:
                description = quest_v2.description_of(q, lang)
                assert description not in descriptions[lang], 'duplicate global description'
                descriptions[lang].add(description)
            path=OUT/'lang'/(lang+'.snbt'); values=json.loads(generated.pop(path))
            assert not languages[lang].keys() & values.keys(), 'duplicate locale key'
            languages[lang].update(values)
        mapping=json.loads(generated.pop(ROOT/'content/campaign_task_ids.json'))
        assert not milestones.keys() & mapping.keys(), 'duplicate global milestone'
        milestones.update(mapping); files.update(generated)
    for lang in LOCALES:files[OUT/'lang'/(lang+'.snbt')]=snbt(languages[lang])
    files[ROOT/'content/campaign_task_ids.json']=snbt(milestones)
    return files


GUIDE_TYPES = {"checkmark", "item", "dimension", "advancement"}
GUIDE_LINKS = {"quest", "chapter"}


def guide_lines(description, lang, ctx, where):
    """A guide description as quest_desc lines. A paragraph with a [quest:key|text] or [chapter:name|text]
    link compiles like a sector's rich text (quest_engine.compile_paragraph); it takes no & codes, which
    FTB does not read inside a text component. Every other paragraph stays plain."""
    lines = []
    for line in paragraphs(description):
        tags = {m.group(1) for m in quest_engine.TAG.finditer(line)}
        if tags:
            assert tags <= GUIDE_LINKS, f"{where}: guides only take [quest:...] and [chapter:...] links"
            assert not FORMAT_CODE.search(line), f"{where}: a paragraph with a link takes no & codes"
            line = quest_engine.compile_paragraph(line, lang, ctx, where)[0]
        lines.append(line)
    return lines


def generate_guide(data, group_id, order_index, book, languages, seen_ids, all_keys=frozenset(),
                   chapter_names=frozenset()):
    """One guide chapter of content/guides (validated against the pinned JARs by tools/check_guides.py).
    all_keys and chapter_names are the book's quest keys and chapters, the targets its links may name.
    A guide with "presentation": 2 compiles its copy and art like a sector (tools/quest_v2.py)."""
    name = data["chapter"]
    assert re.fullmatch(r"guide_[a-z0-9_]+", name), name
    quests = data["quests"]
    v2 = quest_v2.enabled(data)
    keys = {q["key"] for q in quests}
    assert len(keys) == len(quests), f"duplicate key in {name}"
    chapter_id = stable_id("chapter:" + name)

    def resolver(kind, known):
        def resolve(target, where):
            assert target in known, f"{where}: link to unknown {kind} {target}"
            return stable_id(f"{kind}:{target}")
        return resolve
    ctx = {"items": set(), "names": set(), "keys": set(), "textures": set(),
           "resolve_quest": resolver("quest", all_keys), "resolve_chapter": resolver("chapter", chapter_names)}
    if v2:
        ctx = quest_v2.context(data, book, ctx["resolve_quest"], ctx["resolve_chapter"])
    roots = [q for q in quests if not q["deps"] and not (v2 and quest_v2.is_decor(q))]
    assert roots, f"{name}: no entry quest"
    entry = min(roots, key=lambda q: (q["layout"]["x"], abs(q["layout"]["y"])))
    for lang in LOCALES:
        check_formatting(data["title"][lang], f"{name}/title/{lang}", False)
        check_formatting(data["subtitle"][lang], f"{name}/subtitle/{lang}", False)
        languages[lang][f"chapter.{chapter_id}.title"] = data["title"][lang]
        languages[lang][f"chapter.{chapter_id}.chapter_subtitle"] = [data["subtitle"][lang]]
    xp = book["rewards"]["guides"]["xp"][data["act"]]
    chapter = {"id": chapter_id, "filename": name, "group": group_id, "order_index": order_index,
               "icon": {"id": data["icon"]}, "default_quest_shape": "square",
               "default_min_width": book["min_width"],
               "autofocus_id": stable_id("quest:" + entry["key"]), "quests": []}
    placed = []
    for q in quests:
        key, layout = q["key"], q["layout"]
        qid, tid = stable_id("quest:" + key), stable_id("task:" + key)
        assert not seen_ids & {qid, tid}, f"global ID collision: {key}"
        seen_ids.update((qid, tid))
        kind = q.get("type", "item")
        assert kind in GUIDE_TYPES, f"{key}: task type"
        task = {"id": tid, "type": kind}
        if kind == "item":
            # No item-filter mod is installed, so a tag task names one concrete member in "item".
            assert "item" in q, f"{key}: item tasks need a concrete item (tag {q.get('tag')})"
            task.update(item={"id": q["item"], "count": 1}, count=q.get("count", 1), consume_items=False)
        elif kind == "advancement":
            task.update(advancement=q["advancement"], criterion="")
        elif kind == "dimension":
            task["dimension"] = q["dimension"]
        for dep in q["deps"]:
            assert dep in keys, f"{key}: dependency outside the chapter"
        output = {"id": qid, "x": float(layout["x"]), "y": float(layout["y"]), "shape": layout["shape"],
                  "size": float(layout["size"]), "dependencies": [stable_id("quest:" + d) for d in q["deps"]],
                  "tasks": [task], "rewards": [], "tags": [colour_tag(q, guide=True)]}
        icon = q.get("icon", q.get("item"))
        if icon:
            output["icon"] = {"id": icon}
        if q.get("optional"):
            output["optional"] = True
        if kind != "checkmark":
            output["rewards"].append({"id": stable_id(f"reward:{key}:xp"), "type": "xp", "xp": xp})
        decor = v2 and quest_v2.is_decor(q)
        if v2:
            quest_v2.node_flags(q, output)
            if decor:
                quest_v2.decor(q, output)
            texts = quest_v2.copy_lines(q, name, ctx)
        chapter["quests"].append(output)
        if not decor:
            placed.append((layout["x"], layout["y"], layout["size"]))
        for lang in LOCALES:
            if v2:
                copy = q[lang]
                check_formatting(copy["title"], f"{key}/title/{lang}", False)
                languages[lang][f"quest.{qid}.title"] = copy["title"]
                if copy.get("subtitle"):
                    languages[lang][f"quest.{qid}.quest_subtitle"] = copy["subtitle"]
                languages[lang][f"quest.{qid}.quest_desc"] = texts[lang][0]
                continue
            title, description = q[lang]
            check_formatting(title, f"{key}/title/{lang}", False)
            check_formatting(description, f"{key}/{lang}", True)
            languages[lang][f"quest.{qid}.title"] = title
            languages[lang][f"quest.{qid}.quest_desc"] = guide_lines(description, lang, ctx, f"{key}/{lang}")
        if not v2:   # quest_copy checks the links of a v2 quest, with its items and keys
            links = {lang: sorted(m.group(1, 2) for m in quest_engine.TAG.finditer(q[lang][1])) for lang in LOCALES}
            assert links["en_us"] == links["es_es"], f"{key}: EN and ES link to different quests or chapters"
    if v2:   # the canvas rule the sectors keep too: nodes never cover each other
        nodes = [(q["key"], q["layout"]["x"], q["layout"]["y"], q["layout"]["size"]) for q in quests]
        for i, (ka, xa, ya, sa) in enumerate(nodes):
            for kb, xb, yb, sb in nodes[:i]:
                assert math.hypot(xa - xb, ya - yb) >= min_distance(sa, sb) - 1e-6, f"{name}: overlapping nodes {ka} and {kb}"
    # Medallion with the guide's key item, left of the entry node (FTB Evolution's chapter emblem, redrawn).
    # A v2 guide may place it ("medallion": {"x", "y"}) or leave it to the scene ("medallion": false).
    x0 = extents(placed)[0]
    mx, my = x0 - 0.6 - 64 * 2 / NODE_PX * VISUAL / 2, entry["layout"]["y"]
    medallion = data.get("medallion", True)
    if isinstance(medallion, dict):
        mx, my = medallion["x"], medallion["y"]
    assert re.fullmatch(r"[a-z0-9_.-]+:textures/(items?|blocks?)/[a-z0-9_./-]+\.png", data["emblem"]), f"{name}: emblem"
    chapter["images"] = [art("medallion", f"{name}:medallion", mx, my, 2, order=0),
                         image(f"{name}:emblem", mx, my, 16 * 4 / NODE_PX, 16 * 4 / NODE_PX, data["emblem"], order=1)]
    if medallion is False:
        chapter["images"] = []
    if v2:
        placed_at = {q["key"]: (q["layout"]["x"], q["layout"]["y"]) for q in quests}
        chapter["images"] += quest_v2.art_images(data, book, languages, ctx, {q["key"]: q for q in quests}, placed_at)
    return chapter


def build_hub(book, story, first_guides, guides, languages):
    """The first chapter: the Sun of Heliodor with the six acts around it, clockwise from the top.
    Each emblem opens its act, each hexagon links to the act's closing quest, and the Heart of
    Heliodor sits on the sun. Below, one medallion per guide group opens the group's first guide."""
    hub = book["hub"]
    hub_id = stable_id("chapter:" + hub["chapter"])
    for lang in LOCALES:
        check_formatting(hub["title"][lang], "hub/title", False)
        languages[lang][f"chapter.{hub_id}.title"] = hub["title"][lang]
        languages[lang][f"chapter.{hub_id}.chapter_subtitle"] = hub["subtitle"][lang]
    by_act = {}
    for data in story:
        if data.get("act") and not data.get("book", {}).get("branch"):
            by_act.setdefault(data["act"], []).append(data)
    assert sorted(by_act) == list(range(1, 7)), "the hub presents six acts"
    heart = next(q["key"] for data in story for q in data["quests"] if q.get("milestone") == "heliodor_heart")
    images = [art("sun_heliodor", "hub:sun", 0, 0, 2, order=-1)]
    links = [{"id": stable_id("quest_link:hub:" + heart), "linked_quest": stable_id("quest:" + heart),
              "x": 0.0, "y": 0.0, "shape": "hexagon", "size": 3.0}]
    emblem_w = 64 / NODE_PX * VISUAL
    for act, chapters in sorted(by_act.items()):
        angle = math.radians(-90 + 60 * (act - 1))
        ux, uy = math.cos(angle), math.sin(angle)
        closing = finale_of(chapters[-1]["quests"])
        links.append({"id": stable_id("quest_link:hub:" + closing), "linked_quest": stable_id("quest:" + closing),
                      "x": num(6.8 * ux), "y": num(6.8 * uy), "shape": "hexagon", "size": 2.0})
        ex, ey = 10.6 * ux, 10.6 * uy
        emblem = art(f"act_{act}", f"hub:act{act}", ex, ey, 2, click_action="open_quest:" + stable_id("chapter:" + chapters[0]["chapter"]))
        numeral_w = ART_PX[f"numeral_{act}"][0] / NODE_PX * VISUAL
        block = numeral_w + 0.3 + emblem_w
        emblem["x"] = num(ex + block / 2 - emblem_w / 2)
        images.append(art(f"numeral_{act}", f"hub:numeral{act}", ex - block / 2 + numeral_w / 2, ey, 1))
        images.append(emblem)
        for lang in LOCALES:
            languages[lang][f"image.{emblem['id']}.title"] = chapters[0]["title"][lang]
        names = {lang: chapters[0]["title"][lang].split(" · ", 1)[-1] for lang in LOCALES}
        images.append(label(f"hub:act{act}:label", ex, ey + emblem_w / 2 + 0.2, names, languages, scale=1, anchor="top"))
    images.append(art("divider", "hub:divider", 0, 14.2, 2))
    by_name = {g["chapter"]: g for g in guides}
    groups = book["groups"]
    for i, group in enumerate(groups):
        gx, gy = (i - (len(groups) - 1) / 2) * 5.0, 16.8
        target = "open_quest:" + stable_id("chapter:" + first_guides[group["id"]]["chapter"])
        medal = art("medallion", f"hub:group:{group['id']}", gx, gy, 1, order=0, click_action=target)
        images.append(medal)
        images.append(image(f"hub:group:{group['id']}:emblem", gx, gy, 32 / NODE_PX, 32 / NODE_PX,
                            by_name[group["emblem_guide"]]["emblem"], order=1))
        for lang in LOCALES:
            languages[lang][f"image.{medal['id']}.title"] = group["title"][lang]
        images.append(label(f"hub:group:{group['id']}:label", gx, gy + 64 / NODE_PX * VISUAL / 2 + 0.2, group["title"],
                            languages, scale=1, anchor="top"))
    chapter = {"id": hub_id, "filename": hub["chapter"], "order_index": 0, "icon": {"id": hub["icon"]},
               "default_quest_shape": "hexagon", "default_min_width": book["min_width"],
               "autofocus_id": links[0]["id"], "quests": [], "quest_links": links, "images": images}
    return chapter


def theme(book):
    """FTB theme selectors by quest tag (ThemeSelector '#tag'), stacked after FTB's own theme file."""
    lines = ["// Generated by tools/generate_quests.py from content/quest_book.json; do not edit.",
             "// Node outline colours by quest tag: story, guide and optional. Completed and started keep FTB's."]
    for name, colours in book["colors"].items():
        assert set(colours) == {"locked", "available"} and all(re.fullmatch(r"#[0-9A-F]{8}", c) for c in colours.values())
        lines += ["", f"[#entrelumen_{name}]", f"quest_locked_color: {colours['locked']}",
                  f"quest_not_started_color: {colours['available']}"]
    lines += quest_engine.theme_lines(book)
    return "\n".join(lines) + "\n"


def gated_outputs():
    """Items that come out of a gated recipe (tools/generate_family_balance.py): never a reward."""
    import generate_family_balance as balance
    out = set()
    for family in balance.FAMILIES.values():
        for change in family.get("changes", []):
            rid = change.get("id") or ""
            if ":" in rid:
                ns, path = rid.split(":", 1)
                out.add(f"{ns}:{path.rsplit('/', 1)[-1]}")
            if isinstance(change.get("output"), str):
                out.add(change["output"])
    return out


def v2_glyphs(data):
    """Icon textures a v2 guide or story chapter draws in its text (the companion's icon font): the same refs
    quest_text.icon_segment adds while compiling, [tip] included."""
    if data.get("presentation", 1) < 2:
        return set()
    return {quest_engine.quest_text.icon_texture(ref)[0] for q in data["quests"] for lang in LOCALES
            for para in q[lang]["text"] for ref in quest_engine.quest_text.icon_refs_in(para)}


def generate_book(chapters=None, guides=None, book=None, sectors=None):
    """Every generated file: story (generate_all), hub, guides, sector chapters, chapter groups, reward
    tables, data.snbt presets, theme and the companion's ftbquests strings."""
    book = book or load_book()
    chapters = chapters if chapters is not None else load_chapters()
    guides = guides if guides is not None else load_guides(book)
    sectors = sectors if sectors is not None else quest_engine.load_sectors()
    files = generate_all(chapters, book)
    languages = {lang: json.loads(files[OUT / "lang" / (lang + ".snbt")]) for lang in LOCALES}
    # Every ID the story emitted, its quests' and their tasks': guides and sectors may take none of them.
    seen = {i for data in chapters for q in json.loads(files[OUT / "chapters" / (data["chapter"] + ".snbt")])["quests"]
            for i in [q["id"]] + [t["id"] for t in q["tasks"]]}
    task_ids = {}   # sector task ID -> its content signature (tools/task_ids.json)
    groups = []
    first = {}
    for group in book["groups"]:
        gid = stable_id("chapter_group:" + group["id"])
        groups.append({"id": gid, "icon": {"id": group["icon"]}})
        for lang in LOCALES:
            languages[lang][f"chapter_group.{gid}.title"] = group["title"][lang]
    names = {data["chapter"] for data in chapters} | {book["hub"]["chapter"]}
    all_keys = {q["key"] for data in chapters for q in data["quests"]} | {q["key"] for g in guides for q in g["quests"]}
    sector_keys = [q["key"] for sec in sectors for q in sec["quests"]]
    assert len(sector_keys) == len(set(sector_keys)) and not all_keys & set(sector_keys), "duplicate sector quest key"
    all_keys |= set(sector_keys)
    chapter_names = names | {g["chapter"] for g in guides} | {sec["chapter"] for sec in sectors}
    gated = gated_outputs()
    tables, table_files, table_langs = quest_engine.build_reward_tables(book, gated)
    for name, table in table_files.items():
        files[OUT / "reward_tables" / (name + ".snbt")] = snbt(table)
        for lang in LOCALES:
            languages[lang][f"reward_table.{table['id']}.title"] = book["reward_tables"][name]["title"][lang]
    # The chapters' own tables come after the book's: their IDs and the book's never move.
    local_tables, local_files, local_langs = quest_engine.build_local_tables(
        sectors, len(table_files), gated, {t["long"] for t in tables.values()})
    for name, table in local_files.items():
        assert name not in table_files, f"reward table file {name}"
        files[OUT / "reward_tables" / (name + ".snbt")] = snbt(table)
    for lang in LOCALES:
        languages[lang].update(local_langs[lang])
    tables = {**tables, **local_tables}
    ordered = sorted(list(guides) + list(sectors), key=lambda g: group_order(g, book))
    order = {}
    presets = {}
    glyphs = set()   # icon textures the sectors draw in their text: the companion's icon font (quest_text)
    for data in ordered:
        assert data["chapter"] not in names, f"duplicate chapter {data['chapter']}"
        names.add(data["chapter"])
        gid = stable_id("chapter_group:" + data["group"])
        first.setdefault(data["group"], data)
        order[gid] = order.get(gid, -1) + 1
        if data.get("format") == "sector":
            chapter, sector_ctx, sector_presets = quest_engine.compile_sector(
                data, book, tables, languages, seen, all_keys, chapter_names, gid, order[gid], gated)
            presets.update(sector_presets)
            glyphs |= sector_ctx.get("glyphs", set())
            task_ids.update(sector_ctx["task_ids"])
        else:
            chapter = generate_guide(data, gid, order[gid], book, languages, seen, all_keys, chapter_names)
            glyphs |= v2_glyphs(data)
        files[OUT / "chapters" / (data["chapter"] + ".snbt")] = snbt(chapter)
    for data in chapters:
        glyphs |= v2_glyphs(data)
    if guides:
        assert set(first) == {g["id"] for g in book["groups"]}, "every group needs guides"
        hub = build_hub(book, chapters, first, ordered, languages)
        files[OUT / "chapters" / (book["hub"]["chapter"] + ".snbt")] = snbt(hub)
    # What the real client draws (tools/quest_client.py): sheets as sprites or tile glyphs.
    quest_client.reset()
    compiled = {path: quest_client.fix_chapter(json.loads(files[path]), languages)
                for path in sorted(p for p in files if p.parent == OUT / "chapters")}
    quest_client.fix_links(list(compiled.values()), languages)   # links to hidden quests open their chapter
    for path, chapter in compiled.items():
        files[path] = snbt(chapter)
    files.update(quest_client.font_files(ROOT))
    files[OUT / "chapter_groups.snbt"] = snbt({"chapter_groups": groups})
    for lang in LOCALES:
        languages[lang]["file.0000000000000001.title"] = book["file_title"]
    assert languages["en_us"].keys() == languages["es_es"].keys(), "locale key mismatch"
    for lang in LOCALES:
        files[OUT / "lang" / (lang + ".snbt")] = snbt(languages[lang])
    files[TASK_IDS] = json.dumps(dict(sorted(task_ids.items())), ensure_ascii=False, indent=1) + "\n"
    data_snbt = json.loads(files[OUT / "data.snbt"])
    data_snbt.update(drop_loot_crates=False, icon={"id": book["hub"]["icon"]},
                     presets=quest_engine.presets_block(presets))
    files[OUT / "data.snbt"] = snbt(data_snbt)
    files[THEME] = theme(book)
    for lang, values in quest_engine.companion_strings(book, table_langs).items():
        files[FTBQ_LANG / (lang + ".json")] = json.dumps(values, ensure_ascii=False, indent=2) + "\n"
    files.update(quest_engine.quest_text.font_files(ROOT, glyphs))
    files.update(quest_client.spanish_copies(ROOT, files))   # es_ar, es_mx… would otherwise read English
    return files


def task_id_changes(old, new):
    """Task IDs that tools/task_ids.json knows and that now stand for another requirement: a team's saved progress
    on the old one would count for the new one (F48)."""
    return sorted(f"{tid}: {old[tid]} -> {new[tid]}" for tid in old.keys() & new.keys() if old[tid] != new[tid])


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true')
    parser.add_argument('--accept-task-id-changes',action='store_true',
                        help='let a task ID of tools/task_ids.json stand for another requirement (saved progress follows it)')
    args=parser.parse_args()
    chapters=load_chapters(); book=load_book(); guides=load_guides(book)
    files=generate_book(chapters,guides,book);failures=[]
    old_ids=json.loads(TASK_IDS.read_text(encoding='utf-8')) if TASK_IDS.exists() else {}
    moved=task_id_changes(old_ids,json.loads(files[TASK_IDS]))
    if moved and not args.accept_task_id_changes:
        raise SystemExit('Task IDs would change meaning (a team keeps its progress by task ID): '+'; '.join(moved)+
                         '. Give the rewritten task its own "id", or rerun with --accept-task-id-changes if the progress '
                         'should carry over.')
    expected={p.resolve() for p in files}
    stale=[p for folder in ('chapters','reward_tables') for p in (OUT/folder).glob('*.snbt') if p.resolve() not in expected]
    for path,content in files.items():
        if args.check:
            if not path.exists() or path.read_text(encoding='utf-8')!=content:failures.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8',newline='\n')
    if args.check:failures+=[str(p.relative_to(ROOT))+' (not generated)' for p in stale]
    else:
        for p in stale:p.unlink()
    if failures:raise SystemExit('Generated output missing or stale: '+', '.join(failures))
    sectors=quest_engine.load_sectors()
    quests=sum(1 for x in chapters+guides+sectors for q in x['quests'] if quest_engine.quest_art.is_counted(q))
    print(f"PASS: {len(chapters)+len(guides)+len(sectors)+1} chapters ({len(chapters)} story, {len(guides)} guides, "
          f"{len(sectors)} sectors, hub), {quests} quests, {len([p for p in files if p.parent == OUT / 'reward_tables'])} reward tables, "
          f"{len(book['groups'])} groups, global IDs/DAG, EN/ES parity; runtime not verified.")


if __name__=='__main__':main()
