"""ENTRELUMEN quest book v3: the engine behind sector chapters (content/sectors/*.json).

What it expresses (catalogue: docs/research/ftbquests-2101-features.md; design and rules:
docs/design/quest-book-v3.md; copy rules: docs/design/quest-copy.md):

- a node grammar by role (entry, step, milestone, side, tip, info, secret, bounty, boss, capstone),
  shipped both as FTB visual presets (data.snbt "presets") and as explicit shape and size;
- rich text compiled from a small markup into FTB description lines: item names with the real item
  tooltip, keybinds as the player's own key, clickable links to quests and chapters, hover notes,
  accents, runes in the enchanting-table font, pro-tip prefixes, page breaks and inline images;
- layouts that draw pictures: figures (rings, gears, lines, rails) whose slots place the nodes and
  whose outlines are drawn with thin rotated images, branch panels with captions, labels,
  medallions, textures and art revealed by completing a quest;
- Bezier dependency curves (dep_control_pts), hidden lines, reveal-as-you-go branches, secrets,
  exclusive branches, N-of-M capstones, repeatable bounties;
- item tasks that accept any of several items or item tags: an FTB Filter System smart filter, which
  FTB Quests matches through FTB XMod Compat (both must be in catalog/curated.json);
- reward tables and loot crates tiered by act, choice rewards, secret toasts and capstone fanfares; on top,
  the items a quest names itself (with data components) and each chapter's own reward tables, drawn at
  random or chosen by the player;
- the FTB theme for all of it and the companion's ftbquests-namespace strings (shape names, crate
  names, toast texts).

Pure: it reads only the repository. Whether items, entities, structures, advancements, keybinds and
textures exist in the pinned JARs, and whether Almost Unified keeps an item, is checked by
tools/check_guides.py; FTB loading by a real server (docs/design/quest-book-v3.md).
"""
import json
import math
import re
from pathlib import Path

import quest_art    # presentation v2: canvas art kinds and decor nodes
import quest_text   # presentation v2: text markup and the companion fonts

ROOT = Path(__file__).resolve().parents[1]
SECTORS = ROOT / "content/sectors"
LOCALES = ("en_us", "es_es")
NODE_PX, GRID_PX = 24, 28
VISUAL = NODE_PX / GRID_PX
QUESTS_ART = "entrelumen:textures/gui/quests/"
PX = QUESTS_ART + "px.png"  # 4x4 white: panels and lines are this pixel, tinted and scaled
SHAPES_DIR = ROOT / "companion/src/main/resources/assets/ftbquests/textures/shapes"
FTB_SHAPES = {"circle", "diamond", "gear", "heart", "hexagon", "none", "octagon", "pentagon", "rsquare", "square"}
CUSTOM_SHAPES = ("el_sunburst", "el_star", "el_rosette", "el_tag", "el_shield")

# Node grammar of the sectors: role -> (shape, size, optional). Sizes 0.75, 1, 2 and 3 keep 16 px item
# icons on whole texels (QuestButton.draw: icon = 2/3 of the node). A motif may swap shapes by role.
ROLES = {
    "entry": ("hexagon", 2.0, None),       # None: optional only when it is a checkmark
    "step": ("square", 1.0, False),
    "milestone": ("hexagon", 2.0, False),
    "side": ("diamond", 1.0, True),
    "tip": ("el_tag", 1.0, True),
    "info": ("circle", 0.75, True),
    "secret": ("el_star", 1.0, True),
    "bounty": ("el_rosette", 1.0, True),
    "boss": ("el_shield", 2.0, True),
    "capstone": ("el_sunburst", 3.0, False),
    "decor": ("none", 1.0, True),          # a toy on the canvas, never content (quest_art.decor)
}
CHECK_ROLES = {"tip", "info"}
TASK_TYPES = {"item", "checkmark", "advancement", "dimension", "biome", "structure", "kill", "observation", "stat"}
AUTO_TASKS = TASK_TYPES - {"checkmark"}  # tasks the server can detect: the only ones a secret may use
OBSERVE_TYPES = {"block", "block_tag", "block_state", "block_entity", "block_entity_type", "entity_type", "entity_type_tag"}
ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")
TEXTURE = re.compile(r"[a-z0-9_.-]+:textures/[a-z0-9_./-]+\.png")
# "Any of these items": FTB Quests 2101.1.34 hands an item task whose item is a filter to the adapter that claims
# it (ItemMatchingSystem); FTB XMod Compat 21.1.11 registers FTB Filter System's (ftbquests/filtering/FFSSetup).
# The smart filter keeps its expression in a string component (FTB Filter System 21.1.4, ModDataComponents).
SMART_FILTER = "ftbfiltersystem:smart_filter"
FILTER_COMPONENT = "ftbfiltersystem:filter"
FILTER_MODS = ("ftbfiltersystem", "ftbxmodcompat")
ANY_ENTRY = re.compile(r"#?[a-z0-9_.-]+:[a-z0-9_./-]+")
_FILTER_MODS_LOCKED = None
EXPLICIT_TASK_ID = re.compile(r"[0-9A-F]{16}")
# Equippables a player wears or holds off the main inventory (armour, off hand, Curios): FTB's item task counts only
# the 36 main slots, so a non-consuming task on one of these is the companion's entrelumen:carried_item (F25).
CARRIED_ITEMS_FILE = ROOT / "tools/carried_items.json"
CARRIED_TASK = "entrelumen:carried_item"
_CARRIED = None
# Keys the pack ships unbound (tools/unbound_keys.json): a quest that cites one says where to set it (F49).
UNBOUND_KEYS_FILE = ROOT / "tools/unbound_keys.json"
_UNBOUND = None

# Copy palette (docs/design/quest-copy.md). Items teal, keys yellow, links blue, tips gold.
COLORS = {"item": "#8FD6C8", "key": "#F2D060", "link": "#9DC3FF", "tip": "#E8B04A", "warn": "#FF8C7A",
          "hover": "#D8C8FF", "rune": "#B98CFF", "good": "#9BE08A"}
TIP_PREFIX = {"en_us": "Pro tip:", "es_es": "La posta:"}
TIP_MARK = "» "
# Meta phrases the copy rules ban (quest-copy.md): a sign that says "I am a sign".
BANNED = {
    "en_us": ("this quest", "this chapter", "in this chapter", "this guide", "you will learn", "you'll learn",
              "complete this", "welcome to", "let's ", "in this section", "confirm when", "confirm once",
              "click to complete", "check this off"),
    "es_es": ("esta quest", "esta misión", "este capítulo", "en este capítulo", "esta guía", "vas a aprender",
              "completá esta", "bienvenido a", "bienvenida a", "confirmá cuando", "confirmá al",
              "tildá esta", "hacé clic para completar"),
}
HARD_KEYS = re.compile(r"\b(F[1-9]|F1[0-2])\b|\b(Alt|Ctrl|Shift)\+")
MAX_PAGE_CHARS = 330   # visible characters of the first page: one to three short sentences
MAX_SUBTITLE = 64
MAX_TITLE = 40


def num(value):
    return round(float(value), 4)


def stable_id(key):
    import hashlib
    value = int.from_bytes(hashlib.sha256(("entrelumen:v1:" + key).encode()).digest()[:8], "big")
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def table_id(name):
    """Reward-table ID small enough for an SNBT int. RandomReward reads "table_id" with getLong, and a
    plain SNBT number beyond int range parses as a double (FTB Library SNBTUtils.getNumberType), which
    would lose the low bits of a 63-bit ID. Our generator writes JSON, which cannot add the L suffix, so
    reward tables get 31-bit IDs: an IntTag reads back exactly through getLong."""
    import hashlib
    value = int.from_bytes(hashlib.sha256(("entrelumen:v1:reward_table:" + name).encode()).digest()[:4], "big") & 0x7FFFFFFF
    assert value, name
    return value


def load_sectors():
    return [json.loads(p.read_text(encoding="utf-8")) for p in sorted(SECTORS.glob("sector_*.json"))]


# --------------------------------------------------------------------------------------------------
# Rich text

TAG = re.compile(r"\[([a-z]+)(?::([^\]|]*))?((?:\|[^\]|]*)*)\]")
TEXT_TAGS = {"item", "name", "key", "quest", "chapter", "hl", "b", "i", "warn", "good", "rune", "hover", "tip"} | quest_text.TAGS
LINKED_TAGS = {"item", "name", "key", "quest", "chapter"}  # the same set in both languages (quest-copy.md, rule 10)


class TextError(AssertionError):
    pass


def _plain(segments):
    return "".join(s.get("text", "") if "text" in s else ("<" + s.get("keybind", s.get("translate", "")) + ">")
                   for s in segments)


def compile_paragraph(text, lang, ctx, where):
    """One paragraph of markup -> one FTB description line (plain text or a JSON component array)."""
    if text == "{page}":
        return "{@pagebreak}", ""
    if text.startswith("{image:"):
        m = re.fullmatch(r"\{image:(\S+)((?: [a-z_]+:\S+)*)\}", text)
        assert m, f"{where}: malformed image line"
        ref = m.group(1)
        assert TEXTURE.fullmatch(ref), f"{where}: inline images take a texture path"
        props = dict(p.split(":", 1) for p in m.group(2).split())
        assert set(props) <= {"width", "height", "align", "fit"}, f"{where}: image properties"
        assert props.get("align", "center") in ("left", "center", "right"), f"{where}: image align"
        ctx["textures"].add(ref)
        return text, ""
    if text == "{rule}":
        return quest_text.rule_line(), ("", False)
    segments = []
    pos = 0
    tip = False
    para_style = None   # quest_text: lead, li, careful, note, big
    for m in TAG.finditer(text):
        before = text[pos:m.start()]
        if before:
            segments.append({"text": before})
        name, arg, rest = m.group(1), m.group(2), m.group(3)
        parts = [p for p in rest.split("|")[1:]] if rest else []
        assert name in TEXT_TAGS, f"{where}: unknown tag [{name}]"
        seg = None
        if name == "tip":
            assert m.start() == 0 and not arg and not parts, f"{where}: [tip] opens a paragraph"
            tip = True
            if ctx.get("presentation", 1) >= 2:
                segments.extend(quest_text.tip_prefix(lang, ctx))
            else:
                segments.append({"text": TIP_MARK, "color": COLORS["tip"]})
                segments.append({"text": TIP_PREFIX[lang] + " ", "color": COLORS["tip"], "bold": True})
        elif name in quest_text.TAGS:
            segs, style = quest_text.compile_tag(name, arg, parts, m.start() == 0, lang, ctx, where)
            segments.extend(segs)
            para_style = style if name in quest_text.PARAGRAPH_TAGS else para_style
        elif name == "item":
            assert arg and ID.fullmatch(arg) and len(parts) == 1 and parts[0], f"{where}: [item:id|text]"
            ctx["items"].add(arg)
            seg = {"text": parts[0], "color": COLORS["item"],
                   "hoverEvent": {"action": "show_item", "contents": {"id": arg, "count": 1}}}
        elif name == "name":
            assert arg and re.fullmatch(r"(item|block|entity)\.[a-z0-9_]+\.[a-z0-9_./]+", arg) and not parts, \
                f"{where}: [name:translation.key]"
            ctx["names"].add(arg)
            seg = {"translate": arg, "color": COLORS["item"]}
        elif name == "key":
            assert arg and re.fullmatch(r"[a-z0-9_]+(\.[A-Za-z0-9_]+)+", arg) and not parts, f"{where}: [key:key.id]"
            # EMI's binds live in its own config, not in Controls: the keybind component would print the action's
            # name ("[View Uses]"). Write the default key in words instead.
            assert not arg.startswith("key.emi."), \
                f"{where}: [key:{arg}] is an EMI config bind, not a key mapping: write its default key in words"
            ctx["keys"].add(arg)
            seg = {"text": "", "extra": [{"text": "["}, {"keybind": arg}, {"text": "]"}], "color": COLORS["key"]}
        elif name in ("quest", "chapter"):
            assert arg and len(parts) == 1 and parts[0], f"{where}: [{name}:target|text]"
            page = ""
            target = arg
            if name == "quest" and "/" in arg:
                target, page = arg.split("/", 1)
                assert page.isdigit(), f"{where}: page number"
            hex_id = ctx["resolve_" + name](target, where)
            seg = {"text": parts[0], "color": COLORS["link"], "underlined": True,
                   "clickEvent": {"action": "change_page", "value": hex_id + ("/" + page if page else "")}}
        elif name == "hover":
            assert not arg and len(parts) == 2 and all(parts), f"{where}: [hover|text|tooltip]"
            seg = {"text": parts[0], "color": COLORS["hover"], "underlined": True,
                   "hoverEvent": {"action": "show_text", "contents": parts[1]}}
        else:
            assert not arg and len(parts) == 1 and parts[0], f"{where}: [{name}|text]"
            style = {"hl": {"color": ctx["accent"]}, "b": {"bold": True}, "i": {"italic": True},
                     "warn": {"color": COLORS["warn"]}, "good": {"color": COLORS["good"]},
                     "rune": {"color": COLORS["rune"], "font": "minecraft:alt"}}[name]
            seg = {"text": parts[0], **style}
        if seg:
            segments.append(seg)
        pos = m.end()
        if name == "tip" or name in quest_text.PARAGRAPH_TAGS:
            while pos < len(text) and text[pos] == " ":
                pos += 1
    tail = text[pos:]
    if tail:
        segments.append({"text": tail})
    plain = _plain(segments)
    for s in segments:
        for field in ("text",):
            value = s.get(field, "")
            assert not re.search(r"[\[\]{}§\\]", value), f"{where}: stray markup character in {value!r}"
    if len(segments) == 1 and set(segments[0]) == {"text"} and "&" not in segments[0]["text"] and not para_style:
        line = segments[0]["text"]
        assert not line.startswith((" ", "[", "{")), f"{where}: leading space or bracket"
    else:
        line = json.dumps([quest_text.root(para_style)] + segments, ensure_ascii=False, separators=(",", ":"))
    return line, (plain, tip)


def compile_text(paragraphs, lang, ctx, where):
    """Markup paragraphs -> quest_desc lines (a blank line between paragraphs, none around page breaks)."""
    assert isinstance(paragraphs, list) and paragraphs, f"{where}: text is a list of paragraphs"
    lines, first_page, pages = [], [], 1
    quest_text.check_paragraphs(paragraphs, where)
    for i, text in enumerate(paragraphs):
        assert isinstance(text, str) and text.strip() == text and text, f"{where}: empty or padded paragraph"
        line, info = compile_paragraph(text, lang, ctx, f"{where}#{i}")
        if line == "{@pagebreak}":
            assert lines and lines[-1] != "{@pagebreak}", f"{where}: page break needs text before it"
            pages += 1
            lines.append(line)
            continue
        if lines and lines[-1] != "{@pagebreak}" and not quest_text.joins(paragraphs[i - 1], text):
            lines.append("")
        lines.append(line)
        if pages == 1 and info:
            first_page.append(info[0])
    assert lines[-1] != "{@pagebreak}", f"{where}: page break at the end"
    return lines, " ".join(first_page)


def quest_copy(q, name, ctx):
    """One quest's EN/ES copy compiled and checked (quest-copy.md): {lang: (lines, first page)}. Shared by
    compile_sector and tools/quest_draft.py, so a draft passes exactly the rules the book compiles with."""
    key = q["key"]
    texts = {}
    paragraphs = with_notes(q, name, ctx)
    for lang in LOCALES:
        copy = q[lang]
        where = f"{name}:{key}:{lang}"
        lines, first = compile_text(paragraphs[lang], lang, ctx, where)
        check_copy(copy["title"], copy.get("subtitle"), first, lang, where)
        texts[lang] = (lines, first)
    assert bool(q["en_us"].get("subtitle")) == bool(q["es_es"].get("subtitle")), f"{key}: subtitle parity"
    assert texts["en_us"][0].count("{@pagebreak}") == texts["es_es"][0].count("{@pagebreak}"), f"{key}: page parity"
    refs = {}
    for lang in LOCALES:
        raw = " ".join(paragraphs[lang])
        refs[lang] = {(m.group(1), m.group(2)) for m in TAG.finditer(raw) if m.group(1) in LINKED_TAGS}
        visible = TAG.sub(lambda m: " ".join(m.group(3).split("|")[1:]) if m.group(3) else "", raw)
        assert not HARD_KEYS.search(visible), f"{name}:{key}:{lang}: hard-coded key; use [key:...]"
    assert refs["en_us"] == refs["es_es"], \
        f"{key}: linked items, keys and quests differ between languages ({sorted(refs['en_us'] ^ refs['es_es'])})"
    return texts


# Notes the generator adds from a quest's own data (quest-copy.md), in both languages or in neither.
UNBOUND_NOTE = {"en_us": ("[note] This key ships unbound: set it in Controls.",
                          "[note] These keys ship unbound: set them in Controls."),
                "es_es": ("[note] Esta tecla viene sin asignar: asignala en Controles.",
                          "[note] Estas teclas vienen sin asignar: asignalas en Controles.")}
UNBOUND_SAID = {"en_us": re.compile(r"\bControls\b|\bunbound\b", re.I),
                "es_es": re.compile(r"\bControles\b|\bsin asignar\b", re.I)}
CARRY_NOTE = {"en_us": ("[note] Carry all {n} at once; placed or installed ones don't count.",
                        "[note] Carry each full amount at once; placed or installed ones don't count."),
              "es_es": ("[note] Tené las {n} unidades encima a la vez; las colocadas o instaladas no cuentan.",
                        "[note] Tené cada cantidad completa encima a la vez; las unidades colocadas o instaladas no cuentan.")}
CARRY_SAID = {"en_us": re.compile(r"\bat once\b", re.I), "es_es": re.compile(r"\ba la vez\b", re.I)}


def held_counts(q):
    """Counts of the quest's non-consuming item tasks that ask for two or more: FTB checks the most items held in the
    main inventory at one moment, so placed or installed ones never count (F40). Sector tasks, or the one task of a
    guide or story quest."""
    if "tasks" in q or "task" in q:
        tasks = tasks_of(q)
    elif "item" in q and q.get("type", "item") == "item":
        tasks = [{"item": q["item"], "count": q.get("count", 1)}]
    else:
        tasks = []
    return [t.get("count", 1) for t in tasks
            if task_kind(t) == "item" and not t.get("consume") and t.get("count", 1) >= 2]


def cited_unbound_keys(paragraphs):
    return sorted({m.group(2) for m in TAG.finditer(" ".join(paragraphs)) if m.group(1) == "key"} & unbound_keys())


def auto_notes(q):
    """{lang: [note paragraphs]} the quest's data asks for: the unbound keys it cites (F49), unless the text already
    says where to set them, and the held count of its item tasks (F40), unless it already says "at once"."""
    notes = {lang: [] for lang in LOCALES}
    texts = {lang: q[lang]["text"] for lang in LOCALES}
    keys = cited_unbound_keys(texts["en_us"] + texts["es_es"])
    if keys and not all(UNBOUND_SAID[lang].search(" ".join(texts[lang])) for lang in LOCALES):
        for lang in LOCALES:
            notes[lang].append(UNBOUND_NOTE[lang][len(keys) > 1])
    counts = held_counts(q)
    if counts and not all(CARRY_SAID[lang].search(" ".join(texts[lang])) for lang in LOCALES):
        for lang in LOCALES:
            notes[lang].append(CARRY_NOTE[lang][0].format(n=counts[0]) if len(counts) == 1 else CARRY_NOTE[lang][1])
    return notes


def with_notes(q, name, ctx):
    """{lang: paragraphs}: the quest's text with its auto_notes at the end. When they would push a one-page text past
    MAX_PAGE_CHARS they open a page of their own."""
    notes = auto_notes(q)
    texts = {lang: list(q[lang]["text"]) for lang in LOCALES}
    if not notes["en_us"]:
        return texts
    paged = False
    if all("{page}" not in texts[lang] for lang in LOCALES):
        for lang in LOCALES:
            probe = dict(ctx, items=set(), names=set(), keys=set(), textures=set())
            _, first = compile_text(texts[lang] + notes[lang], lang, probe, f"{name}:{q['key']}:{lang}")
            paged |= len(first) > MAX_PAGE_CHARS
    return {lang: texts[lang] + (["{page}"] if paged else []) + notes[lang] for lang in LOCALES}


def check_copy(title, subtitle, first_page, lang, where):
    """The copy rules that a machine can check (docs/design/quest-copy.md)."""
    assert title and len(title) <= MAX_TITLE, f"{where}: title length"
    assert not re.search(r"[&§\[\]{}]", title), f"{where}: markup in the title"
    if subtitle:
        assert len(subtitle) <= MAX_SUBTITLE and not re.search(r"[&§\[\]{}]", subtitle), f"{where}: subtitle"
    assert len(first_page) <= MAX_PAGE_CHARS, f"{where}: first page {len(first_page)} > {MAX_PAGE_CHARS} characters"
    low = (title + " " + (subtitle or "") + " " + first_page).lower()
    for phrase in BANNED[lang]:
        assert phrase not in low, f"{where}: meta phrase {phrase!r}"
    assert not HARD_KEYS.search(first_page), f"{where}: hard-coded key; use [key:...]"
    head = first_page.lower().lstrip("» ").split(".")[0]
    assert head.strip() != title.lower().strip(), f"{where}: the text restates the title"


# --------------------------------------------------------------------------------------------------
# Geometry: figures, slots, lines

def figure_slots(fig):
    """Slot positions and a local frame (radial/normal unit vector) for a figure."""
    kind = fig["kind"]
    slots = []
    if kind in ("ring", "arc"):
        cx, cy, r, count = fig["cx"], fig["cy"], fig["r"], fig["count"]
        start = math.radians(fig.get("start", -90))
        if kind == "ring":
            step = 2 * math.pi / count
        else:
            span = math.radians(fig["span"])
            step = span / max(count - 1, 1)
        direction = -1 if fig.get("counterclockwise") else 1
        for i in range(count):
            a = start + direction * step * i
            ux, uy = math.cos(a), math.sin(a)
            slots.append((cx + r * ux, cy + r * uy, ux, uy))
    elif kind == "line":
        (x0, y0), (x1, y1), count = fig["from"], fig["to"], fig["count"]
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        nx, ny = -dy / length, dx / length
        for i in range(count):
            t = i / max(count - 1, 1)
            slots.append((x0 + dx * t, y0 + dy * t, nx, ny))
    else:
        raise AssertionError(f"figure kind {kind}")
    return slots


def place(q, figures, placed, by_key, stack=()):
    """Resolve a quest position: explicit x/y, a figure slot (+ radial/tangent offsets) or near another quest."""
    key = q["key"]
    if key in placed:
        return placed[key]
    assert key not in stack, f"placement cycle at {key}"
    at = q["at"]
    if "x" in at:
        x, y = at["x"], at["y"]
    elif "figure" in at:
        fig = figures[at["figure"]]
        slots = fig["_slots"]
        assert 0 <= at["slot"] < len(slots), f"{key}: slot"
        sx, sy, ux, uy = slots[at["slot"]]
        out, along = at.get("out", 0.0), at.get("along", 0.0)
        # tangent = radial rotated 90 degrees clockwise on screen (y down)
        tx, ty = -uy, ux
        x, y = sx + ux * out + tx * along, sy + uy * out + ty * along
    elif "near" in at:
        base = by_key.get(at["near"])
        assert base, f"{key}: near unknown quest {at['near']}"
        bx, by = place(base, figures, placed, by_key, stack + (key,))
        x, y = bx + at.get("dx", 0.0), by + at.get("dy", 0.0)
    else:
        raise AssertionError(f"{key}: at needs x/y, figure or near")
    placed[key] = (num(x), num(y))
    return placed[key]


def min_distance(a, b):
    return (a + b) / 2 * VISUAL + 0.4


IMAGE_KEYS = {}   # image id -> its key, for messages (tools/check_guides.py names images by key)


def image(key, x, y, w, h, picture, **extra):
    out = {"id": stable_id("image:" + key), "x": num(x), "y": num(y), "width": num(w), "height": num(h),
           "rotation": num(extra.pop("rotation", 0.0)), "image": picture}
    IMAGE_KEYS[out["id"]] = key
    out.update({k: v for k, v in extra.items() if v is not None})
    return out


def rgb(color):
    assert re.fullmatch(r"#[0-9A-Fa-f]{6}", color), color
    return int(color[1:], 16)


def line_image(key, x0, y0, x1, y1, color, alpha, thickness_px=1.0, order=-2, overlap=1.04):
    """A straight line between two points (grid units) as a thin rotated pixel image. An image of width w
    is drawn 24*w px wide while one grid unit is 28 px, so a line of length L units is L/VISUAL wide."""
    length = math.hypot(x1 - x0, y1 - y0)
    angle = math.degrees(math.atan2(y1 - y0, x1 - x0))
    return image(key, (x0 + x1) / 2, (y0 + y1) / 2, length / VISUAL * overlap, thickness_px / NODE_PX, PX,
                 rotation=angle, color=rgb(color), alpha=alpha, order=order, position_locked=True)


def rect_image(key, x0, y0, x1, y1, color, alpha, order=-3):
    """A filled rectangle between drawn-space corners (grid units)."""
    return image(key, (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / VISUAL, (y1 - y0) / VISUAL, PX,
                 color=rgb(color), alpha=alpha, order=order, position_locked=True)


def draw_figure(name, fig, palette, chapter):
    """Outlines that turn a constellation into a picture: rings, gear teeth, spokes, rails, polylines."""
    images = []
    for n, style in enumerate(fig.get("draw", [])):
        kind = style["style"]
        color = style.get("color", palette["line"])
        alpha = style.get("alpha", 110)
        thick = style.get("thickness_px", 1.0)
        base = f"{chapter}:figure:{name}:{n}"
        if kind in ("ring", "teeth"):
            cx, cy = fig["cx"], fig["cy"]
            r = style.get("r", fig.get("r"))
            if kind == "ring":
                segs = style.get("segments", 48)
                a0 = math.radians(style.get("from", 0))
                span = math.radians(style.get("span", 360))
                closed = style.get("span", 360) >= 360
                pts = [(cx + r * math.cos(a0 + span * i / segs), cy + r * math.sin(a0 + span * i / segs))
                       for i in range(segs + (0 if closed else 1))]
                pairs = list(zip(pts, pts[1:] + (pts[:1] if closed else [])))
                for i, ((x0, y0), (x1, y1)) in enumerate(pairs):
                    images.append(line_image(f"{base}:{i}", x0, y0, x1, y1, color, alpha, thick))
            else:
                count, depth, width = style["count"], style["depth"], style["width"]
                start = math.radians(style.get("start", 0))
                for i in range(count):
                    a = start + 2 * math.pi * i / count
                    mx, my = cx + (r + depth / 2) * math.cos(a), cy + (r + depth / 2) * math.sin(a)
                    images.append(image(f"{base}:{i}", mx, my, depth / VISUAL, width / VISUAL, PX,
                                        rotation=math.degrees(a), color=rgb(color), alpha=alpha,
                                        order=-2, position_locked=True))
        elif kind == "spokes":
            cx, cy = fig["cx"], fig["cy"]
            r0 = style.get("from_r", 0.0)
            r1 = style.get("to_r", fig["r"])
            for i, (sx, sy, ux, uy) in enumerate(fig["_slots"]):
                if i in style.get("skip", []):
                    continue
                images.append(line_image(f"{base}:{i}", cx + ux * r0, cy + uy * r0, cx + ux * r1, cy + uy * r1,
                                         color, alpha, thick))
        elif kind == "rails":
            (x0, y0), (x1, y1) = style.get("from", fig["from"]), style.get("to", fig["to"])
            gauge = style.get("gauge", 0.5)
            dx, dy = x1 - x0, y1 - y0
            length = math.hypot(dx, dy)
            nx, ny = -dy / length * gauge / 2, dx / length * gauge / 2
            images.append(line_image(f"{base}:a", x0 + nx, y0 + ny, x1 + nx, y1 + ny, color, alpha, thick, overlap=1.0))
            images.append(line_image(f"{base}:b", x0 - nx, y0 - ny, x1 - nx, y1 - ny, color, alpha, thick, overlap=1.0))
            spacing = style.get("sleepers", 0.75)
            count = int(length / spacing)
            for i in range(count + 1):
                t = i / max(count, 1)
                px, py = x0 + dx * t, y0 + dy * t
                images.append(line_image(f"{base}:s{i}", px - nx * 1.6, py - ny * 1.6, px + nx * 1.6, py + ny * 1.6,
                                         style.get("sleeper_color", color), style.get("sleeper_alpha", alpha),
                                         style.get("sleeper_px", 2.0), order=-3, overlap=1.0))
        elif kind == "polyline":
            pts = [(s[0], s[1]) for s in fig["_slots"]]
            if style.get("closed"):
                pts.append(pts[0])
            for i, ((x0, y0), (x1, y1)) in enumerate(zip(pts, pts[1:])):
                images.append(line_image(f"{base}:{i}", x0, y0, x1, y1, color, alpha, thick))
        elif kind == "glyphs":
            # Mod item textures (by reference, never copied) set around a ring as runes.
            cx, cy, r = fig["cx"], fig["cy"], style["r"]
            refs = style["textures"]
            scale = style.get("scale", 1)
            start = math.radians(style.get("start", -90))
            for i, ref in enumerate(refs):
                a = start + 2 * math.pi * i / len(refs)
                images.append(image(f"{base}:{i}", cx + r * math.cos(a), cy + r * math.sin(a),
                                    16 * scale / NODE_PX, 16 * scale / NODE_PX, ref,
                                    rotation=style.get("tilt", 0) and math.degrees(a) + 90,
                                    alpha=style.get("alpha", 120), order=-1, position_locked=True))
        else:
            raise AssertionError(f"figure {name}: draw style {kind}")
    return images


def bezier_points(q_xy, q_size, d_xy, bend):
    """dep_control_pts entry for a curve between a quest and its dependency. The curve bows to one side by
    bend * chord length. FTB stores control points shifted by 3/7 of the dependent quest's size
    (QuestButton.positionControlPoints subtracts size*bs/2 px, i.e. size*3/7 units): add it back."""
    (qx, qy), (dx, dy) = q_xy, d_xy
    vx, vy = dx - qx, dy - qy
    length = math.hypot(vx, vy)
    nx, ny = -vy / length, vx / length
    off = bend * length
    near_dep = (dx - vx * 0.3 + nx * off, dy - vy * 0.3 + ny * off)
    near_q = (qx + vx * 0.3 + nx * off, qy + vy * 0.3 + ny * off)
    shift = q_size * 3 / 7
    return [num(near_dep[0] + shift), num(near_dep[1] + shift), num(near_q[0] + shift), num(near_q[1] + shift)]


# --------------------------------------------------------------------------------------------------
# Labels (text drawn by FTB on an empty image)

WIDE_GLYPHS = {"@": 7, "~": 7, "«": 7, "»": 7, "–": 7, "—": 9, "·": 3, "i": 2, "l": 3, "'": 3, ".": 2, ",": 2,
               ":": 2, ";": 2, "!": 2, "|": 2, " ": 4, "t": 4, "I": 4, "f": 5, "k": 5, "(": 5, ")": 5}


def text_px(text):
    return sum(WIDE_GLYPHS.get(c, 6) for c in text)


def label(key, x, y, texts, languages, scale=2, color="#E8DCB5", anchor="center", align="middle",
          font=None, bold=False, order=5, hover=None):
    """A caption drawn at an exact scale: the box is exactly 9*scale px per line tall, and wide enough that
    FTB's fit (min of width/text and height/lines) is decided by the height."""
    align = {"center": "middle"}.get(align, align)   # FTB's ChapterImage.TextAlign: start, middle, end
    assert align in ("start", "middle", "end"), f"{key}: align {align}"
    for lang in LOCALES:
        t = texts[lang]
        assert t and not re.search(r"[§{}\[\]\\]", t), f"{key}: label text"  # JSON text: '&' is literal
    lines = {lang: texts[lang].split("\n") for lang in LOCALES}
    rows = max(len(v) for v in lines.values())
    for lang in LOCALES:
        lines[lang] += [" "] * (rows - len(lines[lang]))
    widest = max(text_px(line) + (len(line) if bold else 0) for v in lines.values() for line in v)
    w = (widest * scale * (1.4 if font else 1.0) + 6) / NODE_PX
    h = 9 * scale * rows / NODE_PX
    cy = {"center": y, "top": y + h * VISUAL / 2, "bottom": y - h * VISUAL / 2}[anchor]
    cx = x
    if align == "start":
        cx = x + w * VISUAL / 2
    elif align == "end":
        cx = x - w * VISUAL / 2
    img = image(key, cx, cy, w, h, "", text_on_image=True, text_shadow=True, order=order,
                text_h_align=None if align == "middle" else align, position_locked=True)
    for lang in LOCALES:
        seg = {"text": "\n".join(lines[lang]), "color": color}
        if font:
            seg["font"] = font
        if bold:
            seg["bold"] = True
        languages[lang][f"image.{img['id']}.title"] = json.dumps(["", seg], ensure_ascii=False, separators=(",", ":"))
    return img


# --------------------------------------------------------------------------------------------------
# Sector chapters

def sector_role(q):
    role = q["role"]
    assert role in ROLES, f"{q['key']}: role {role}"
    return role


def tasks_of(q):
    tasks = q.get("tasks") or ([q["task"]] if "task" in q else [])
    assert tasks, f"{q['key']}: no task"
    return tasks


def task_kind(t):
    """FTB task type; a task with only "item" or "any" is an item task."""
    return t.get("type", "item" if "item" in t or "any" in t else None)


def filter_mods_locked():
    """Both mods an any-of task needs are in catalog/curated.json (read once)."""
    global _FILTER_MODS_LOCKED
    if _FILTER_MODS_LOCKED is None:
        lock = json.loads((ROOT / "catalog/curated.json").read_text(encoding="utf-8"))
        mods = {m["id"] for entry in lock["mods"] for m in entry["metadata"]["mods"]}
        _FILTER_MODS_LOCKED = all(mod in mods for mod in FILTER_MODS)
    return _FILTER_MODS_LOCKED


def filter_expression(alternatives):
    """FTB Filter System's own string form (SmartFilter.asString): item(id) and item_tag(tag), inside or(...) when
    there are several, because the filter's root ANDs its top-level terms (RootFilter extends AndFilter)."""
    terms = [f"item_tag({a[1:]})" if a.startswith("#") else f"item({a})" for a in alternatives]
    return terms[0] if len(terms) == 1 else "or(" + "".join(terms) + ")"


def any_item_task(t, key, ctx):
    """{"any": [ids and #tags], "count", "consume", "icon", "title"} -> the item task fields for a smart filter.
    One missing item makes FTB Filter System reject the whole expression (ItemFilter.fromString throws), so
    tools/check_guides.py checks every entry against the pinned JARs. The title is required: FTB would show
    "Smart Filter". The icon defaults to the first item listed."""
    alternatives = t["any"]
    assert isinstance(alternatives, list) and alternatives and all(
        isinstance(a, str) and ANY_ENTRY.fullmatch(a) for a in alternatives), f"{key}: any-of entries are item ids or #tags"
    assert len(set(alternatives)) == len(alternatives), f"{key}: repeated any-of entry"
    assert len(alternatives) > 1 or alternatives[0].startswith("#"), f"{key}: a single item is a plain item task"
    assert "item" not in t and "components" not in t, f"{key}: any-of replaces item and components"
    assert filter_mods_locked(), f"{key}: any-of tasks need FTB Filter System and FTB XMod Compat in catalog/curated.json"
    assert "title" in t, f"{key}: an any-of task needs a title in en_us and es_es (FTB would show 'Smart Filter')"
    items = [a for a in alternatives if not a.startswith("#")]
    assert items or "icon" in t, f"{key}: an any-of task of tags only needs an icon"
    count = t.get("count", 1)
    assert isinstance(count, int) and 1 <= count <= 4096, f"{key}: count"
    ctx["items"].update(items)
    ctx.setdefault("item_tags", set()).update(a[1:] for a in alternatives if a.startswith("#"))
    return {"item": {"id": SMART_FILTER, "count": 1, "components": {FILTER_COMPONENT: filter_expression(alternatives)}},
            "count": count, "consume_items": bool(t.get("consume", False))}


def any_icon(t, ctx, key=""):
    """The given icon, or the first item listed (a quest without an icon of its own takes it too)."""
    if "icon" in t:
        return icon_stack(t["icon"], ctx)
    items = [a for a in t["any"] if isinstance(a, str) and not a.startswith("#")]
    assert items, f"{key}: an any-of task of tags only needs an icon"
    return {"id": items[0]}


def carried_items():
    """The equippables of tools/carried_items.json (read once)."""
    global _CARRIED
    if _CARRIED is None:
        items = json.loads(CARRIED_ITEMS_FILE.read_text(encoding="utf-8"))["items"]
        assert all(ID.fullmatch(i) for i in items), "tools/carried_items.json: item ids"
        _CARRIED = frozenset(items)
    return _CARRIED


def carried_task(out):
    """An item task that waits for an equippable without taking it -> entrelumen:carried_item, which also counts
    armour, the off hand and Curios: {"type", "item", "count"}; ID, icon and title stay. A component variant (the
    nine-pocket Tool Belt) stays an FTB item task: the companion's task matches the item id only, so it would pass on
    any belt, while the variant is crafted into the inventory, where FTB's task sees it."""
    if (out["type"] != "item" or out.get("consume_items") or out["item"]["id"] not in carried_items()
            or out["item"].get("components")):
        return out
    out["type"] = CARRIED_TASK
    out.pop("consume_items", None)
    return out


def unbound_keys():
    """Key mapping ids the pack ships unbound (tools/unbound_keys.json, read once)."""
    global _UNBOUND
    if _UNBOUND is None:
        _UNBOUND = frozenset(json.loads(UNBOUND_KEYS_FILE.read_text(encoding="utf-8"))["keys"])
    return _UNBOUND


def task_target(t):
    """What a task asks for, without its count or consume flag: the item and its components, the any-of filter, or
    the advancement, dimension, biome, structure, entity, observed target or stat. A checkmark asks for nothing."""
    kind = task_kind(t)
    if kind == "item" and "any" in t:
        return filter_expression(sorted(t["any"]))
    if kind == "item":
        components = t.get("components")
        return t["item"] + (json.dumps(components, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
                            if components else "")
    if kind == "advancement":
        return t["advancement"] + ("#" + t["criterion"] if t.get("criterion") else "")
    if kind in ("dimension", "biome", "structure"):
        return t[kind]
    if kind == "kill":
        return t["entity"]
    if kind == "observation":
        return f"{t['observe']}={t['target']}"
    if kind == "stat":
        return t["stat"]
    return ""


def task_ids(q):
    """[(task ID, content signature)] of a sector quest. FTB keeps a team's progress by task ID, so an ID must keep
    meaning the same requirement whatever is inserted or reordered around it (F48):
    - the signature is task:<quest>:<kind>:<target> (task_target), with :2, :3… for a repeated identical task; the
      count and consume flag are not part of it, so raising a count keeps the progress;
    - a quest with several tasks takes stable_id(signature) for each;
    - a quest with one task keeps task:<quest>, the ID the book always gave it;
    - a task may pin its ID with "id" (16 hex digits), to keep a team's progress when its target is rewritten.
    tools/task_ids.json records every ID's signature; tools/generate_quests.py refuses to let an existing ID change
    meaning without --accept-task-id-changes."""
    key = q["key"]
    tasks = tasks_of(q)
    seen, out = {}, []
    for t in tasks:
        base = f"task:{key}:{task_kind(t)}:{task_target(t)}"
        seen[base] = seen.get(base, 0) + 1
        signature = base if seen[base] == 1 else f"{base}:{seen[base]}"
        if "id" in t:
            tid = t["id"]
            assert isinstance(tid, str) and EXPLICIT_TASK_ID.fullmatch(tid) and 0 < int(tid, 16) < 2 ** 63, \
                f"{key}: a task's own id is 16 uppercase hex digits of a positive 63-bit number"
        elif len(tasks) == 1:
            tid = stable_id(f"task:{key}")
        else:
            tid = stable_id(signature)
        out.append((tid, signature))
    assert len({tid for tid, _ in out}) == len(out), f"{key}: two tasks share an ID"
    return out


def compile_task(t, key, tid, languages, ctx):
    """One task of a sector quest; tid comes from task_ids."""
    kind = task_kind(t)
    assert kind in TASK_TYPES, f"{key}: task type {kind}"
    assert "any" not in t or kind == "item", f"{key}: any-of is an item task"
    assert isinstance(tid, str) and EXPLICIT_TASK_ID.fullmatch(tid), f"{key}: task ID {tid!r}"
    out = {"id": tid, "type": kind}
    if kind == "item" and "any" in t:
        out.update(any_item_task(t, key, ctx))
        if "icon" not in t:
            out["icon"] = any_icon(t, ctx, key)
    elif kind == "item":
        assert ID.fullmatch(t["item"]), f"{key}: item id"
        count = t.get("count", 1)
        assert isinstance(count, int) and 1 <= count <= 4096, f"{key}: count"
        out.update(item={"id": t["item"], "count": 1}, count=count, consume_items=bool(t.get("consume", False)))
        if "components" in t:
            # An item that only exists as a component variant (Ars Technica's Arcane Wrench is a Create
            # wrench with ars_technica:runic_wrench). FTB compares the listed components only ("fuzzy",
            # ItemMatchingSystem.ComponentMatchType); the default "none" would accept any wrench.
            components = t["components"]
            assert isinstance(components, dict) and components and all(ID.fullmatch(k) for k in components), \
                f"{key}: components"
            out["item"]["components"] = components
            out["match_components"] = "fuzzy"
        ctx["items"].add(t["item"])
        carried_task(out)
    elif kind == "advancement":
        assert ID.fullmatch(t["advancement"]), f"{key}: advancement"
        out.update(advancement=t["advancement"], criterion=t.get("criterion", ""))
        ctx["advancements"].add(t["advancement"])
    elif kind == "dimension":
        out["dimension"] = t["dimension"]
    elif kind == "biome":
        out["biome"] = t["biome"]
    elif kind == "structure":
        assert re.fullmatch(r"#?[a-z0-9_.-]+:[a-z0-9_./-]+", t["structure"]), f"{key}: structure"
        out["structure"] = t["structure"]
        ctx["structures"].add(t["structure"])
    elif kind == "kill":
        assert ID.fullmatch(t["entity"]), f"{key}: entity"
        out.update(entity=t["entity"], value=t.get("value", 1))
        ctx["entities"].add(t["entity"])
    elif kind == "observation":
        assert t["observe"] in OBSERVE_TYPES, f"{key}: observation type"
        assert 1 <= t.get("ticks", 40) <= 1200, f"{key}: observation ticks (1..1200)"
        out.update(observation_type=t["observe"], to_observe=t["target"], timer=t.get("ticks", 40))
        if t["observe"] in ("entity_type",):
            ctx["entities"].add(t["target"])
        elif t["observe"] in ("block",):
            ctx["items"].add(t["target"])
    elif kind == "stat":
        out.update(stat=t["stat"], value=t["value"])
    if "icon" in t:
        out["icon"] = icon_stack(t["icon"], ctx)
    if "title" in t:
        assert set(t["title"]) == set(LOCALES), f"{key}: task title locales"
        for lang in LOCALES:
            assert t["title"][lang] and not re.search(r"[&§\[\]{}]", t["title"][lang]), f"{key}: task title"
            languages[lang][f"task.{tid}.title"] = t["title"][lang]
    return out


def icon_stack(spec, ctx):
    """Quest/task/chapter icon: an item id, {"texture": path} or {"entity": id} (FTB custom_icon item)."""
    if isinstance(spec, str):
        assert ID.fullmatch(spec), f"icon {spec}"
        ctx["items"].add(spec)
        return {"id": spec}
    if "texture" in spec:
        assert TEXTURE.fullmatch(spec["texture"]), spec
        ctx["textures"].add(spec["texture"])
        return {"id": "ftbquests:custom_icon", "components": {"ftbquests:icon": spec["texture"]}}
    if "entity" in spec:
        ctx["entities"].add(spec["entity"])
        return {"id": "ftbquests:custom_icon", "components": {"ftbquests:entity_face": spec["entity"]}}
    raise AssertionError(f"icon {spec}")


def crate_stack(table):
    return {"id": "ftbquests:lootcrate", "components": {"ftbquests:loot_crate": table["crate"]["string_id"]}}


ACT_TIER = {"any": 1, "I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6}

# --------------------------------------------------------------------------------------------------
# Rewards a quest names itself: items ("rewards") and the chapter's own reward tables ("reward_tables",
# picked at random with "reward_table" or chosen by the player with "reward_choice"). They come on top of
# the role's rewards. The FTB side, read in FTB Quests 2101.1.34:
# - ItemReward.readData: "item" goes through QuestObjectBase.itemOrMissingFromNBT, which hands a compound
#   without the legacy "Count"/"tag" keys whole to ItemStack.parse (1.21.1 ItemStack.CODEC: "id", "count"
#   1..99, "components"); "count" outside the stack is the reward's amount. When the stack does not parse,
#   FTB shows its missing item instead: DataComponentPatch refuses an unregistered or non-persistent
#   component type, so check_guides.py checks every key against the pinned JARs.
# - BaseQuestFile.loadRewardTableFile reads every reward_tables/*.snbt: "id", "order_index" and the file
#   name, which RewardTable.sanitizeFilename keeps only when it is [a-z0-9_] without edge underscores.
# - RandomReward.claim draws loot_size times with replacement (RewardTable.generateWeightedRandomRewards,
#   empty weight left out); a ChoiceReward lets the player pick one entry (ClaimChoiceRewardMessage), so
#   weights and loot_size do not count there.
# - FTB Library's SNBT reader turns a JSON int into an IntTag (a bigger one into a double), a decimal into a
#   DoubleTag and true/false into bytes, and refuses a list that mixes tag types, which fails the whole file.
EXTRA_REWARDS = ("rewards", "reward_table", "reward_choice")
REWARD_MAX_COUNT = 64   # moderate, like the book's tables
TABLE_MAX_ROLLS = 8
TABLE_NAME = re.compile(r"[a-z0-9]+(?:_[a-z0-9]+)*")


def snbt_kind(value, where):
    """The NBT tag FTB Library's SNBT reader makes of a JSON component value, after checking that it reads
    back as written: no null, ints within int range, finite decimals, no control characters, lists of one
    kind. Returns "byte", "int", "double", "string", "list" or "compound"."""
    if isinstance(value, bool):
        return "byte"
    if isinstance(value, int):
        assert -2 ** 31 <= value < 2 ** 31, f"{where}: {value} is beyond int range (FTB reads it as a double)"
        return "int"
    if isinstance(value, float):
        assert math.isfinite(value), f"{where}: {value} is not a number"
        return "double"
    if isinstance(value, str):
        assert not re.search(r"[\x00-\x1f\x7f]", value), f"{where}: control character in {value!r}"
        return "string"
    if isinstance(value, list):
        kinds = {snbt_kind(v, f"{where}[{i}]") for i, v in enumerate(value)}
        assert len(kinds) <= 1, f"{where}: a list mixes {sorted(kinds)} (SNBT lists hold one tag type)"
        return "list"
    if isinstance(value, dict):
        for k, v in value.items():
            assert not re.search(r"[\x00-\x1f\x7f]", k), f"{where}: control character in key {k!r}"
            snbt_kind(v, f"{where}.{k}")
        return "compound"
    raise AssertionError(f"{where}: {value!r} has no SNBT form")


def check_components(components, where):
    """Data components of a reward stack: {"<registered type>": value}, as ItemStack.CODEC reads them."""
    assert isinstance(components, dict) and components, f"{where}: components map component types to values"
    for k, v in components.items():
        assert ID.fullmatch(k), f"{where}: component type {k!r} (namespace:path; removing one with ! is not supported)"
        snbt_kind(v, f"{where}: {k}")
    return components


def reward_item(spec, where, gated=frozenset(), weighted=False):
    """{"item", "count", "components"} (and "weight" in a table) -> an FTB item reward without its id. Like the
    book's tables: at most 64, never ENTRELUMEN's own items nor outputs of gated recipes."""
    allowed = ("item", "count", "components") + (("weight",) if weighted else ())
    assert isinstance(spec, dict) and set(spec) <= set(allowed), f"{where}: a reward takes {', '.join(allowed)}"
    item = spec.get("item")
    assert isinstance(item, str) and ID.fullmatch(item), f"{where}: item id"
    assert not item.startswith("entrelumen:"), f"{where}: {item} is one of ENTRELUMEN's own items"
    assert item not in gated, f"{where}: {item} comes from a gated recipe"
    count = spec.get("count", 1)
    assert type(count) is int and 1 <= count <= REWARD_MAX_COUNT, f"{where}: count is 1..{REWARD_MAX_COUNT}"
    stack = {"id": item, "count": 1}
    if "components" in spec:
        stack["components"] = check_components(spec["components"], where)
    out = {"type": "item", "item": stack, "count": count}
    if weighted:
        weight = spec.get("weight", 1)
        assert type(weight) in (int, float) and math.isfinite(weight) and weight > 0, f"{where}: weight > 0"
        out["weight"] = float(weight)
    return out


def local_table(chapter, name):
    """Key of a chapter's own reward table, among the book's tables (whose names never hold a slash)."""
    return f"{chapter}/{name}"


def extra_rewards(q, chapter, tables, gated=frozenset()):
    """The items, reward table and choice a quest names itself. IDs follow the quest key and the item or table
    name, not the position, so reordering the list never hands out a claimed reward again; a second stack of
    the same item (another bee type, say) is ':2'."""
    key = q["key"]
    out = []
    rewards = q.get("rewards", [])
    assert isinstance(rewards, list) and (rewards or "rewards" not in q), f"{key}: rewards is a list of items"
    stacks, seen = [], {}
    for i, spec in enumerate(rewards):
        r = reward_item(spec, f"{key}: reward {i}", gated)
        assert r["item"] not in stacks, f"{key}: reward {i} repeats {r['item']['id']}: add up the counts"
        stacks.append(r["item"])
        item = r["item"]["id"]
        seen[item] = seen.get(item, 0) + 1
        suffix = "" if seen[item] == 1 else f":{seen[item]}"
        out.append({"id": stable_id(f"reward:{key}:item:{item}{suffix}"), **r})
    for field, kind in (("reward_table", "random"), ("reward_choice", "choice")):
        if field in q:
            name = q[field]
            assert chapter and isinstance(name, str) and local_table(chapter, name) in tables, \
                f"{key}: {field} {name!r} is not a reward table of {chapter}"
            out.append({"id": stable_id(f"reward:{key}:{field}:{name}"), "type": kind,
                        "table_id": tables[local_table(chapter, name)]["long"]})
    return out


LOCAL_ORDER_SPAN = 1_000_000  # a chapter table's order_index: start + its ID modulo this, whatever other chapters add


def build_local_tables(sectors, start, gated=frozenset(), taken=()):
    """The sectors' own reward tables -> (tables by local_table key, files by name, titles by locale).

    A table is {"title": {en_us, es_es}, "rolls": draws (1), "entries": [{"item", "count", "weight", "components"}]}.
    Its ID is table_id("<chapter>/<name>"), so it never moves when other tables come or go; so does its order_index,
    start + ID % LOCAL_ORDER_SPAN, which keeps a chapter's files byte-identical when another chapter adds tables; the
    file is reward_tables/<chapter>__<name>.snbt. With no icon of its own FTB cycles through the entries' icons
    (RewardTable.getAltIcon)."""
    tables, files, languages = {}, {}, {lang: {} for lang in LOCALES}
    ids = set(taken)
    orders = {}
    for data in sorted(sectors, key=lambda d: d["chapter"]):
        chapter = data["chapter"]
        specs = data.get("reward_tables", {})
        assert isinstance(specs, dict), f"{chapter}: reward_tables maps names to tables"
        uses = {}
        for q in data["quests"]:
            for field in ("reward_table", "reward_choice"):
                if isinstance(q.get(field), str):
                    uses.setdefault(q[field], set()).add(field)
        for name in sorted(specs):
            spec = specs[name]
            where = f"{chapter}: reward table {name}"
            assert TABLE_NAME.fullmatch(name), f"{where}: the name is lowercase words joined by _"
            assert name in uses, f"{where}: no quest uses it"
            assert isinstance(spec, dict) and set(spec) <= {"title", "rolls", "entries"}, \
                f"{where}: a table takes title, rolls and entries"
            title = spec.get("title")
            assert isinstance(title, dict) and set(title) == set(LOCALES), f"{where}: title in en_us and es_es"
            for lang in LOCALES:
                assert isinstance(title[lang], str) and title[lang].strip() == title[lang] and title[lang] \
                    and len(title[lang]) <= MAX_TITLE and not re.search(r"[&§\[\]{}]", title[lang]), f"{where}: title"
            rolls = spec.get("rolls", 1)
            assert type(rolls) is int and 1 <= rolls <= TABLE_MAX_ROLLS, f"{where}: rolls is 1..{TABLE_MAX_ROLLS}"
            assert rolls == 1 or "reward_table" in uses[name], \
                f"{where}: a choice gives one entry; rolls only counts for reward_table"
            entries = spec.get("entries")
            assert isinstance(entries, list) and entries, f"{where}: entries is a list of items"
            key = local_table(chapter, name)
            long_id = table_id(key)
            assert long_id not in ids, f"{where}: table ID collision"
            ids.add(long_id)
            tid = f"{long_id:016X}"
            rewards, stacks = [], []
            for i, entry in enumerate(entries):
                r = reward_item(entry, f"{where}, entry {i}", gated, weighted=True)
                assert r["item"] not in stacks, f"{where}: entry {i} repeats {r['item']['id']}: add up the weights"
                stacks.append(r["item"])
                rewards.append({"id": stable_id(f"reward_table:{key}:{i}"), **r})
            filename = f"{chapter}__{name}"
            index = start + long_id % LOCAL_ORDER_SPAN
            assert index not in orders, f"{where}: its order index collides with {orders.get(index)}: rename one of the two"
            orders[index] = key
            files[filename] = {"id": tid, "order_index": index, "loot_size": rolls, "use_title": True, "rewards": rewards}
            for lang in LOCALES:
                languages[lang][f"reward_table.{tid}.title"] = title[lang]
            tables[key] = {"id": tid, "long": long_id, "file": filename}
    return tables, files, languages


def sector_rewards(q, role, act, book, tables, chapter=None, gated=frozenset()):
    """Reward cadence by role (quest_book.json "sector_rewards"); tables by act. What the quest names itself
    (extra_rewards) comes on top."""
    rules = book["sector_rewards"]
    key = q["key"]
    base = book["rewards"]["guides"]["xp"][act]
    out = []
    xp = rules["xp"].get(role, 0) * base
    tasks = tasks_of(q)
    if all(t.get("type", "item" if "item" in t else "") == "checkmark" for t in tasks):
        assert not [f for f in EXTRA_REWARDS if f in q], f"{key}: a free click never pays: drop its rewards"
        return []  # a free click never pays
    if xp:
        out.append({"id": stable_id(f"reward:{key}:xp"), "type": "xp", "xp": xp})
    tier = ACT_TIER[act]
    for kind in ("choice", "random", "loot"):
        if role in rules[kind]:
            table = rules["tables"][kind].format(n=tier)
            assert table in tables, f"{key}: reward table {table}"
            out.append({"id": stable_id(f"reward:{key}:{kind}"), "type": kind, "table_id": tables[table]["long"]})
    if role in rules["crate"]:
        table = tables[rules["tables"]["crate"].format(n=tier)]
        crate = {"id": stable_id(f"reward:{key}:crate"), "type": "item", "item": crate_stack(table), "count": 1}
        if role == "bounty":
            crate["random_bonus"] = 1
        out.append(crate)
    if role == "secret":
        out.append({"id": stable_id(f"reward:{key}:toast"), "type": "toast", "description": "entrelumen.quests.toast.secret",
                    "auto": "invisible"})
    if role == "capstone":
        out.append({"id": stable_id(f"reward:{key}:fanfare"), "type": "command", "auto": "invisible",
                    "command": rules["fanfare"], "permission_level": 2, "silent": True})
    return out + extra_rewards(q, chapter, tables, gated)


def compile_sector(data, book, tables, languages, seen_ids, all_keys, chapter_ids, group_id, order_index,
                   gated=frozenset()):
    """One sector chapter -> FTB chapter dict (lang entries go into languages). Returns (chapter, needs).
    tables holds the book's tables and the chapters' own (build_local_tables)."""
    name = data["chapter"]
    assert re.fullmatch(r"sector_[a-z0-9_]+", name), name
    motif = book["motifs"][data["motif"]]
    act = data["act"]
    chapter_id = stable_id("chapter:" + name)
    quests = data["quests"]
    by_key = {q["key"]: q for q in quests}
    assert len(by_key) == len(quests), f"{name}: duplicate key"
    ctx = {"items": set(), "names": set(), "keys": set(), "textures": set(), "entities": set(),
           "structures": set(), "advancements": set(), "accent": motif["accent"],
           "presentation": data.get("presentation", 1), "task_ids": {}}

    def resolve_quest(target, where):
        assert target in all_keys, f"{where}: link to unknown quest {target}"
        return stable_id("quest:" + target)

    def resolve_chapter(target, where):
        assert target in chapter_ids, f"{where}: link to unknown chapter {target}"
        return stable_id("chapter:" + target)
    ctx["resolve_quest"], ctx["resolve_chapter"] = resolve_quest, resolve_chapter

    for lang in LOCALES:
        t = data["title"][lang]
        assert t and len(t) <= 48 and not re.search(r"[&§\[\]{}]", t), f"{name}: chapter title"
        languages[lang][f"chapter.{chapter_id}.title"] = t
        subtitle = data["subtitle"][lang]
        assert isinstance(subtitle, list) and subtitle
        languages[lang][f"chapter.{chapter_id}.chapter_subtitle"] = subtitle

    figures = {}
    for fname, fig in data.get("figures", {}).items():
        fig = dict(fig)
        fig["_slots"] = figure_slots(fig)
        figures[fname] = fig
    placed = {}
    for q in quests:
        place(q, figures, placed, by_key)

    capstones = [q for q in quests if q["role"] == "capstone"]
    assert len(capstones) == 1, f"{name}: one capstone per chapter"
    entry = data["entry"]
    assert entry in by_key and not [d for d in by_key[entry]["deps"] if d in by_key], f"{name}: entry"
    shapes = dict(motif.get("shapes", {}))
    chapter = {"id": chapter_id, "filename": name, "group": group_id, "order_index": order_index,
               "icon": icon_stack(data["icon"], ctx), "default_quest_shape": "square",
               "default_min_width": book["min_width"], "progression_mode": "flexible",
               "autofocus_id": stable_id("quest:" + entry), "tags": ["entrelumen_motif_" + data["motif"]],
               "quests": [], "quest_links": [], "images": []}
    presets = {}
    nodes = []
    for q in quests:
        key = q["key"]
        role = sector_role(q)
        shape, size, optional = ROLES[role]
        shape = shapes.get(role, shape)
        size = float(q.get("size", size))
        assert shape in FTB_SHAPES or shape in CUSTOM_SHAPES, f"{key}: shape {shape}"
        tasks = tasks_of(q)
        kinds = [task_kind(t) for t in tasks]
        if role in CHECK_ROLES:
            assert kinds == ["checkmark"], f"{key}: {role} nodes are one checkmark"
        if role == "secret":
            assert all(k in AUTO_TASKS for k in kinds), f"{key}: a secret needs a task the server detects"
        if role == "bounty":
            assert kinds and all(k == "item" for k in kinds) and all(t.get("consume") for t in tasks), \
                f"{key}: bounties consume items"
        if "checkmark" in kinds:
            assert kinds == ["checkmark"], f"{key}: a checkmark quest has only that task"
        if optional is None:
            optional = kinds == ["checkmark"]
        if kinds == ["checkmark"]:
            optional = True
        qid = stable_id("quest:" + key)
        assert qid not in seen_ids, f"global ID collision: {key}"
        seen_ids.add(qid)
        x, y = placed[key]
        preset = f"el_{role}" + (f"_{data['motif']}" if role in shapes else "") + ("" if size == ROLES[role][1] else f"_{size:g}".replace(".", "p"))
        presets[preset] = {"shape": shape, "size": size}
        out = {"id": qid, "x": x, "y": y, "shape": shape, "size": size, "preset": preset,
               "dependencies": [stable_id("quest:" + d) for d in q["deps"]],
               "tasks": [], "rewards": sector_rewards(q, role, act, book, tables, name, gated),
               "tags": [f"entrelumen_{role}"]}
        for d in q["deps"]:
            assert d in all_keys, f"{key}: missing dependency {d}"
        if optional:
            out["optional"] = True
        if "icon" in q:
            out["icon"] = icon_stack(q["icon"], ctx)
        elif role not in ("tip",):
            first = tasks[0]
            if "item" in first:
                out["icon"] = {"id": first["item"]}
            elif "any" in first:
                out["icon"] = any_icon(first, ctx, key)
        if "icon_scale" in q:
            assert 0.1 <= q["icon_scale"] <= 2.0
            out["icon_scale"] = float(q["icon_scale"])
        elif role == "tip":
            out["icon_scale"] = 0.75
        # Visibility and flow by role
        if role == "secret":
            out["invisible"] = True
        if role == "side" and any(by_key.get(d, {}).get("role") == "side" for d in q["deps"]):
            out["hide_until_deps_complete"] = True
        if q.get("reveal"):
            out["hide_until_deps_complete"] = True
        if role == "capstone":
            # In a flexible chapter canStartTasks is always true (TeamData.canStartTasks), which would make
            # hide_details_until_startable a no-op: the capstone itself runs linear.
            out["progression_mode"] = "linear"
            out["hide_details_until_startable"] = True
            out["hide_dependency_lines"] = bool(q.get("hide_lines", False))
        if q.get("hide_lines"):
            out["hide_dependency_lines"] = True
        if q.get("hide_dependent_lines"):
            out["hide_dependent_lines"] = True
        if role in CHECK_ROLES:
            out["disable_toast"] = True
            out["hide_lock_icon"] = True
        if role == "decor":
            quest_art.decor(q, out)
        if role == "bounty":
            out["can_repeat"] = True
            out["repeat_cooldown"] = int(q.get("cooldown", book["sector_rewards"]["bounty_cooldown"]))
            out["hide_dependency_lines"] = True
        if "min_deps" in q:
            assert 0 < q["min_deps"] < len(q["deps"]), f"{key}: min_deps"
            out["min_required_dependencies"] = q["min_deps"]
        if q.get("any_dep"):
            assert len(q["deps"]) >= 2, f"{key}: any_dep needs alternatives"
            out["dependency_requirement"] = "one_completed"
        if "exclusive" in q:
            out["max_completable_dependents"] = int(q["exclusive"])
        if q.get("sequential"):
            assert len(tasks) >= 2, f"{key}: sequential needs several tasks"
            out["require_sequential_tasks"] = True
        if q.get("lore_after"):
            out["hide_text_until_complete"] = True
        curves = {}
        for dep, bend in q.get("curve", {}).items():
            assert dep in q["deps"] and dep in by_key, f"{key}: curve for a local dependency"
            curves[stable_id("quest:" + dep)] = bezier_points((x, y), size, placed[dep], bend)
        if curves:
            out["dep_control_pts"] = curves
        for t, (tid, signature) in zip(tasks, task_ids(q)):
            assert tid not in seen_ids, f"{key}: task ID {tid} ({signature}) is already taken"
            seen_ids.add(tid)
            ctx["task_ids"][tid] = signature
            out["tasks"].append(compile_task(t, key, tid, languages, ctx))
        chapter["quests"].append(out)
        nodes.append((key, x, y, size))
        texts = quest_copy(q, name, ctx)
        for lang in LOCALES:
            copy = q[lang]
            languages[lang][f"quest.{qid}.title"] = copy["title"]
            if copy.get("subtitle"):
                languages[lang][f"quest.{qid}.quest_subtitle"] = copy["subtitle"]
            languages[lang][f"quest.{qid}.quest_desc"] = texts[lang][0]
        for r in out["rewards"]:
            if r["type"] == "toast":
                for lang in LOCALES:
                    languages[lang][f"reward.{r['id']}.title"] = book["sector_rewards"]["toast_title"][lang]
    # Quest links (nodes that show another chapter's quest)
    for link in data.get("links", []):
        assert link["target"] in all_keys, f"{name}: link to unknown quest {link['target']}"
        lx, ly = place({"key": "link:" + link["target"], "at": link["at"]}, figures, placed, by_key)
        lid = stable_id(f"quest_link:{name}:{link['target']}")
        chapter["quest_links"].append({"id": lid, "linked_quest": stable_id("quest:" + link["target"]),
                                       "x": num(lx), "y": num(ly), "shape": link.get("shape", "octagon"),
                                       "size": float(link.get("size", 1.0))})
        nodes.append(("link:" + link["target"], lx, ly, float(link.get("size", 1.0))))
    for i, (ka, xa, ya, sa) in enumerate(nodes):
        for kb, xb, yb, sb in nodes[:i]:
            assert math.hypot(xa - xb, ya - yb) >= min_distance(sa, sb) - 1e-6, f"{name}: overlapping nodes {ka} and {kb}"
    chapter["images"] = decorate_sector(data, motif, figures, placed, nodes, languages, ctx, by_key)
    if not chapter["quest_links"]:
        del chapter["quest_links"]
    return chapter, ctx, presets


def decorate_sector(data, motif, figures, placed, nodes, languages, ctx, by_key):
    name = data["chapter"]
    palette = {"line": motif["line"], "panel": motif["panel"], "accent": motif["accent"]}
    images = []
    for fname, fig in figures.items():
        images += draw_figure(fname, fig, palette, name)
    # Branch panels: a tinted panel behind the members of a group, four corners and a caption. The panel
    # follows the branch: it is rotated to the axis through its two farthest members (a radial branch
    # gets a radial panel), snapped to 0 or 90 degrees when it is nearly axis-aligned.
    size_of = {k: s for k, x, y, s in nodes}
    for gid, group in data.get("groups", {}).items():
        keys = [q["key"] for q in data["quests"] if q.get("group") == gid]
        members = [(placed[k], size_of[k]) for k in keys]
        assert len(members) >= 2, f"{name}: group {gid} needs two members"
        margin = group.get("margin", 0.5)
        pts = [xy for xy, _ in members]
        axis = group.get("axis", "radial" if "center" in data else "auto")
        if axis == "radial":
            ox, oy = data["center"]
            gx, gy = sum(x for x, y in pts) / len(pts), sum(y for x, y in pts) / len(pts)
            theta = math.degrees(math.atan2(gy - oy, gx - ox)) % 180
        elif axis == "auto":
            a, b = max(((p, q) for p in pts for q in pts), key=lambda pq: math.hypot(pq[0][0] - pq[1][0], pq[0][1] - pq[1][1]))
            theta = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0])) % 180
        else:
            theta = {"x": 0.0, "y": 90.0}.get(axis, axis)
        theta = float(theta)
        for snap in (0, 90, 180):
            if abs(theta - snap) < 6:
                theta = snap % 180
        t = math.radians(theta)
        cos, sin = math.cos(t), math.sin(t)
        cx = sum(x for x, y in pts) / len(pts)
        cy = sum(y for x, y in pts) / len(pts)

        def local(x, y):
            dx, dy = x - cx, y - cy
            return dx * cos + dy * sin, -dx * sin + dy * cos

        def world(u, v):
            return cx + u * cos - v * sin, cy + u * sin + v * cos
        loc = [(local(x, y), s) for (x, y), s in members]
        u0 = min(u - s * VISUAL / 2 for (u, v), s in loc) - margin
        v0 = min(v - s * VISUAL / 2 for (u, v), s in loc) - margin
        u1 = max(u + s * VISUAL / 2 for (u, v), s in loc) + margin
        v1 = max(v + s * VISUAL / 2 for (u, v), s in loc) + margin
        for k, x, y, s in nodes:
            if k not in keys:
                u, v = local(x, y)
                assert not (u0 - s * VISUAL / 2 < u < u1 + s * VISUAL / 2 and v0 - s * VISUAL / 2 < v < v1 + s * VISUAL / 2),                     f"{name}: node {k} inside the panel of {gid}"
        pcx, pcy = world((u0 + u1) / 2, (v0 + v1) / 2)
        images.append(image(f"{name}:group:{gid}:panel", pcx, pcy, (u1 - u0) / VISUAL, (v1 - v0) / VISUAL, PX,
                            rotation=theta, color=rgb(palette["panel"]),
                            alpha=group.get("alpha", motif.get("panel_alpha", 44)), order=-3, position_locked=True))
        c = 16 / NODE_PX * VISUAL / 2
        for i, (cu, cv) in enumerate(((u0 + c, v0 + c), (u1 - c, v0 + c), (u1 - c, v1 - c), (u0 + c, v1 - c))):
            wx, wy = world(cu, cv)
            images.append(image(f"{name}:group:{gid}:corner{i}", wx, wy, 16 / NODE_PX, 16 / NODE_PX,
                                QUESTS_ART + "corner.png", rotation=(theta + 90.0 * i) % 360, order=-1,
                                position_locked=True))
        # Caption: horizontal, just outside the panel on the side named by "caption" (default: the end
        # of the branch farthest from the chapter's centre of mass).
        corners = [world(u, v) for u, v in ((u0, v0), (u1, v0), (u1, v1), (u0, v1))]
        where = group.get("caption", "auto")
        if where == "auto":
            ends = [world(u0, (v0 + v1) / 2), world(u1, (v0 + v1) / 2)]
            ox, oy = data.get("center", (0.0, 0.0))
            ex, ey = max(ends, key=lambda e: math.hypot(e[0] - ox, e[1] - oy))
            dx, dy = ex - pcx, ey - pcy
            where = ("below" if dy > 0 else "above") if abs(dy) >= abs(dx) else ("right" if dx > 0 else "left")
        xs, ys = [x for x, y in corners], [y for x, y in corners]
        cap = quest_art.caption_style(data, group, palette)   # size, tint and weight (presentation v2)
        if where == "above":
            img = label(f"{name}:group:{gid}:label", (min(xs) + max(xs)) / 2, min(ys) - 0.12, group["label"], languages,
                        anchor="bottom", **cap)
        elif where == "below":
            img = label(f"{name}:group:{gid}:label", (min(xs) + max(xs)) / 2, max(ys) + 0.12, group["label"], languages,
                        anchor="top", **cap)
        elif where == "right":
            img = label(f"{name}:group:{gid}:label", max(xs) + 0.2, (min(ys) + max(ys)) / 2, group["label"], languages,
                        align="start", **cap)
        else:
            img = label(f"{name}:group:{gid}:label", min(xs) - 0.2, (min(ys) + max(ys)) / 2, group["label"], languages,
                        align="end", **cap)
        images.append(img)
    ctx["placed"] = placed   # quest_art: paths drawn "through" quests
    for i, art in enumerate(data.get("art", [])):
        images += quest_art.art_images(name, i, art, palette, languages, ctx, by_key)
    return images


ART_PX = {"sun_heliodor": (128, 128), "medallion": (64, 64), "corner": (16, 16), "divider": (96, 9),
          **{f"numeral_{n}": (w, 45) for n, w in zip(range(1, 7), (27, 51, 75, 63, 39, 63))},
          **{f"act_{n}": (32, 32) for n in range(1, 7)},
          "banner_create": (192, 48), "banner_ars": (192, 48), "banner_plain": (192, 48), "tip": (16, 16), "secret": (16, 16)}
# banner_plain is a pale plate meant to be tinted with the chapter's own colour: "color": "#rrggbb" on the art entry.


def art_image(chapter, i, art, palette, languages, ctx, by_key):
    key = f"{chapter}:art:{art.get('id', i)}"
    extra = {"order": art.get("order", -1), "position_locked": True}
    if "reveal" in art:
        assert art["reveal"] in by_key, f"{key}: reveal quest"
        extra["dependency"] = stable_id("quest:" + art["reveal"])
    if "click" in art:
        kind, target = art["click"].split(":", 1)
        extra["click_action"] = "open_quest:" + stable_id(f"{kind}:{target}")
    if "alpha" in art:
        extra["alpha"] = art["alpha"]
    if "color" in art:
        extra["color"] = rgb(art["color"])
    if "texture" in art:
        ref = art["texture"]
        assert TEXTURE.fullmatch(ref), f"{key}: texture"
        ctx["textures"].add(ref)
        name = ref.rsplit("/", 1)[-1][:-4]
        w, h = ART_PX.get(name, tuple(art.get("px", (16, 16))))
        scale = art.get("scale", 1)
        assert float(scale).is_integer(), f"{key}: textures sit at whole texel scales"
        img = image(key, art["x"], art["y"], w * scale / NODE_PX, h * scale / NODE_PX, ref,
                    rotation=art.get("rotation", 0.0), **extra)
    elif "panel" in art:
        x0, y0, x1, y1 = art["panel"]
        img = rect_image(key, x0, y0, x1, y1, art.get("tint", palette["panel"]), art.get("alpha", 44),
                         order=art.get("order", -3))
    elif "line" in art:
        (x0, y0), (x1, y1) = art["line"]
        img = line_image(key, x0, y0, x1, y1, art.get("tint", palette["line"]), art.get("alpha", 110),
                         art.get("thickness_px", 1.0), order=art.get("order", -2))
    elif "label" in art:
        img = label(key, art["x"], art["y"], art["label"], languages, scale=art.get("scale", 2),
                    color=art.get("tint", palette["accent"]), anchor=art.get("anchor", "center"),
                    align=art.get("align", "middle"), font=art.get("font"), bold=art.get("bold", False),
                    order=art.get("order", 5))
        for k, v in extra.items():
            if k not in ("order",):
                img[k] = v
        return img
    else:
        raise AssertionError(f"{key}: art kind")
    if "hover" in art:
        for lang in LOCALES:
            text = art["hover"][lang]
            assert text and not re.search(r"[&§{}\[\]\\]", text), f"{key}: hover"
            languages[lang][f"image.{img['id']}.title"] = text
    return img


# --------------------------------------------------------------------------------------------------
# Reward tables, presets, theme, companion strings

def build_reward_tables(book, gated):
    """reward_tables/*.snbt from quest_book.json. Items stay moderate and never ENTRELUMEN components or
    outputs of gated recipes (tools/generate_family_balance.py)."""
    tables, files, languages = {}, {}, {lang: {} for lang in LOCALES}
    specs = sorted((k, v) for k, v in book["reward_tables"].items() if not k.startswith("_"))
    for index, (name, spec) in enumerate(specs):
        long_id = table_id(name)
        tid = f"{long_id:016X}"
        rewards = []
        for i, (item, count, weight) in enumerate(spec["rewards"]):
            assert ID.fullmatch(item) and not item.startswith("entrelumen:"), f"{name}: {item}"
            assert item not in gated, f"{name}: {item} comes from a gated recipe"
            assert 1 <= count <= 64 and weight > 0, f"{name}: {item}"
            rewards.append({"id": stable_id(f"reward_table:{name}:{i}"), "type": "item",
                            "item": {"id": item, "count": 1}, "count": count, "weight": float(weight)})
        table = {"id": tid, "order_index": index, "icon": {"id": spec["icon"]}, "loot_size": spec.get("loot_size", 1),
                 "use_title": True, "rewards": rewards}
        if spec.get("empty_weight"):
            table["empty_weight"] = float(spec["empty_weight"])
        if "crate" in spec:
            crate = spec["crate"]
            table["loot_crate"] = {"string_id": crate["string_id"], "item_name": f"entrelumen.quests.crate.{name}",
                                   "color": rgb(crate["color"]), "glow": bool(crate.get("glow", False)),
                                   "drops": {"passive": 0, "monster": 0, "boss": 0}}
            for lang in LOCALES:
                languages[lang][f"entrelumen.quests.crate.{name}"] = spec["title"][lang]
        tables[name] = dict(spec, id=tid, long=long_id)
        files[name] = table
    return tables, files, languages


def presets_block(sector_presets):
    presets = {"normal": {"shape": "square", "size": 1.0}, "info": {"shape": "gear", "size": 1.0},
               "goal": {"shape": "hexagon", "size": 2.0}}
    presets.update(sector_presets)
    return dict(sorted(presets.items()))


def theme_lines(book):
    """Theme blocks for roles and motifs (appended to the v2 tag colours)."""
    lines = ["", "// Sector chapters (quest-book v3): node colours by role, lines and panel text by motif."]
    for role, spec in book["role_colors"].items():
        if role.startswith("_"):
            continue
        lines += ["", f"[#entrelumen_{role}]"]
        for prop in ("locked", "available", "started", "completed"):
            if prop in spec:
                assert re.fullmatch(r"#[0-9A-F]{8}", spec[prop]), (role, prop)
                lines.append(f"quest_{'not_started' if prop == 'available' else prop}_color: {spec[prop]}")
        if "icon" in spec:
            lines.append(f"icon: {spec['icon']}")
    for motif, spec in book["motifs"].items():
        lines += ["", f"[#entrelumen_motif_{motif}]"]
        for prop, value in spec["theme"].items():
            assert re.fullmatch(r"[a-z_]+", prop) and re.fullmatch(r"#[0-9A-F]{6}([0-9A-F]{2})?|[0-9.]+", value), (motif, prop)
            lines.append(f"{prop}: {value}")
    return lines


def companion_strings(book, table_langs):
    """assets/ftbquests/lang/<locale>.json in the companion: our shape names, crate names and toasts."""
    out = {}
    for lang in LOCALES:
        values = {f"ftbquests.quest.shape.{k}": v[lang] for k, v in book["shape_names"].items()}
        values.update(table_langs[lang])
        values.update({k: v[lang] for k, v in book["companion_strings"].items()})
        out[lang] = dict(sorted(values.items()))
    return out
