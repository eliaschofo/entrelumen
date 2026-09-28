"""Presentation v2 for quest text (docs/design/quest-book-v3.md, "Presentación v2"; docs/design/quest-copy.md).

FTB Quests 2101.1.34 draws a description with the player's GUI scale and nothing else: every paragraph is a
TextField at scale 1 with a 9 px line pitch (ViewQuestPanel.addDescriptionText), and no option, theme property
or client setting enlarges it. What a component can still choose is its font, its colour and its weight, and
Minecraft fonts come from resources. This module adds, on top of the markup of tools/quest_engine.py:

- [lead] opens the first paragraph of a quest: one short sentence in bold, the line to read if you read one;
- [li] and [li:<icon>] open a list item: an accent bullet or an item icon, and no blank line between items;
- [icon:<ref>] draws an icon inside the line: an item's own texture (item id) or any texture, through a
  bitmap font generated into the companion (entrelumen:quest_icons), with the item tooltip on hover;
- [big|×5] draws a few characters at twice the size, baseline-aligned, in the chapter's accent, through a
  second font (entrelumen:quest_big) that points at the vanilla glyph sheets with twice their height;
- [careful] and [note] open callouts like [tip]: an icon, a coloured label and the text;
- {rule} is a divider line;
- a chapter with "presentation": 2 draws its [tip] prefix with the tip icon instead of "» ".

Fonts are resources, so they are client-side: the server syncs text, the companion ships the glyphs. A missing
texture makes that one glyph a box, never breaks the text. Everything here is pure (reads the repository only);
tools/check_guides.py checks the icon textures against the pinned JARs.
"""
import hashlib
import json
import re

FONT_ICONS = "entrelumen:quest_icons"
FONT_BIG = "entrelumen:quest_big"
FONT_DIR = "companion/src/main/resources/assets/entrelumen/font"
TAGS = {"lead", "li", "icon", "big", "careful", "note"}
PARAGRAPH_TAGS = {"lead", "li", "careful", "note"}   # these open a paragraph, like [tip]
WHITE = "#FFFFFF"
RULE_COLOR = "#6B6456"
CALLOUTS = {
    # tag -> (icon, colour, label per language)
    "tip": ("tip", "#E8B04A", {"en_us": "Pro tip:", "es_es": "La posta:"}),
    "careful": ("alert", "#FF8C7A", {"en_us": "Careful:", "es_es": "Ojo:"}),
    "note": ("info", "#9DC3FF", {"en_us": "Note:", "es_es": "Dato:"}),
}
# Named glyphs: vanilla GUI sprites and the book's own art, referenced where they live (never copied).
NAMED = {
    "tip": "entrelumen:textures/gui/quests/tip.png",
    "secret": "entrelumen:textures/gui/quests/secret.png",
    "alert": "minecraft:textures/gui/sprites/icon/unseen_notification.png",
    "info": "minecraft:textures/gui/sprites/icon/info.png",
    "check": "minecraft:textures/gui/sprites/icon/checkmark.png",
    "click": "minecraft:textures/gui/sprites/toast/mouse.png",
    "right_click": "minecraft:textures/gui/sprites/toast/right_click.png",
    "heart": "minecraft:textures/gui/sprites/hud/heart/full.png",
}
ITEM_ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")
TEXTURE = re.compile(r"[a-z0-9_.-]+:textures/[a-z0-9_./-]+\.png")
BIG_CHARS = re.compile(r"[0-9A-Za-z ×·•→★%+./:,-]+")
MAX_BIG = 10
GLYPH_FIRST, GLYPH_LAST = 0xE100, 0xF0FF   # book glyphs, assigned in order (Private Use Area)
TEST_FIRST = 0xF100                          # glyphs of refs no chapter uses (unit tests), by hash
_REGISTRY = None


# --------------------------------------------------------------------------------------------------
# Icons

def icon_texture(ref):
    """'tip' | 'mod:item' | 'mod:textures/…png' -> the texture drawn, and the item it shows on hover (or None)."""
    if ref in NAMED:
        return NAMED[ref], None
    if TEXTURE.fullmatch(ref):
        return ref, None
    assert ITEM_ID.fullmatch(ref), f"icon {ref!r}: a name ({', '.join(sorted(NAMED))}), an item id or a texture"
    ns, path = ref.split(":", 1)
    return f"{ns}:textures/item/{path}.png", ref


def icon_refs_in(text):
    """Every icon a paragraph asks for, callout icons included."""
    refs = set()
    for m in re.finditer(r"\[(icon|li):([^\]|]+)\]", text):
        refs.add(m.group(2))
    for tag, (icon, _, _) in CALLOUTS.items():
        if text.startswith(f"[{tag}]"):
            refs.add(icon)
    return refs


def registry():
    """Code point of every icon texture the sectors use: sorted, so the font and the text always agree."""
    global _REGISTRY
    if _REGISTRY is None:
        import quest_engine
        textures = {NAMED["tip"]}
        for data in quest_engine.load_sectors():
            for q in data["quests"]:
                for lang in ("en_us", "es_es"):
                    for para in q.get(lang, {}).get("text", []):
                        textures |= {icon_texture(r)[0] for r in icon_refs_in(para)}
        ordered = sorted(textures)
        assert GLYPH_FIRST + len(ordered) <= GLYPH_LAST, "too many quest icons for the glyph range"
        _REGISTRY = {t: chr(GLYPH_FIRST + i) for i, t in enumerate(ordered)}
    return _REGISTRY


def glyph(texture):
    reg = registry()
    if texture in reg:
        return reg[texture]
    digest = int.from_bytes(hashlib.sha256(texture.encode()).digest()[:2], "big")
    return chr(TEST_FIRST + digest % 0x700)


def icon_segment(ref, ctx):
    texture, item = icon_texture(ref)
    ctx.setdefault("glyphs", set()).add(texture)
    ctx["textures"].add(texture)
    seg = {"text": glyph(texture), "font": FONT_ICONS, "color": WHITE}   # white: text colour tints a glyph
    if item:
        ctx["items"].add(item)
        seg["hoverEvent"] = {"action": "show_item", "contents": {"id": item, "count": 1}}
    return seg


# --------------------------------------------------------------------------------------------------
# Markup (called by quest_engine.compile_paragraph for the tags in TAGS)

def compile_tag(name, arg, parts, at_start, lang, ctx, where):
    """(segments, paragraph style) for one of our tags."""
    if name == "icon":
        assert arg and not parts, f"{where}: [icon:ref]"
        return [icon_segment(arg, ctx)], None
    if name == "big":
        assert not arg and len(parts) == 1 and parts[0], f"{where}: [big|text]"
        text = parts[0]
        assert len(text) <= MAX_BIG and BIG_CHARS.fullmatch(text), \
            f"{where}: [big|…] takes up to {MAX_BIG} characters of digits, basic letters and × · • → ★ % + . / : , -"
        return [{"text": text, "font": FONT_BIG, "color": ctx["accent"]}], "big"
    assert at_start and not parts, f"{where}: [{name}] opens a paragraph"
    if name == "lead":
        assert not arg, f"{where}: [lead]"
        return [], "lead"
    if name == "li":
        if arg:
            return [icon_segment(arg, ctx), {"text": " "}], "li"
        return [{"text": "• ", "color": ctx["accent"]}], "li"
    return callout(name, lang, ctx), name


def callout(name, lang, ctx):
    icon, color, label = CALLOUTS[name]
    return [icon_segment(icon, ctx), {"text": " " + label[lang] + " ", "color": color, "bold": True}]


def tip_prefix(lang, ctx):
    """[tip] in a "presentation": 2 chapter: the tip icon instead of "» "."""
    return callout("tip", lang, ctx)


def root(style):
    """The first element of a JSON line: its style is inherited by every segment after it."""
    return {"text": "", "bold": True} if style == "lead" else ""


def joins(previous, current):
    """No blank line between two list items."""
    return previous.startswith("[li") and current.startswith("[li")


def check_paragraphs(paragraphs, where):
    """Placement rules the renderer needs: one lead, first; [big] never opens a page (its glyphs rise 7 px
    above the line, into the blank line before it)."""
    for i, text in enumerate(paragraphs):
        if text.startswith("[lead]"):
            assert i == 0, f"{where}#{i}: [lead] is the first paragraph"
        if "[big|" in text:
            assert i > 0 and paragraphs[i - 1] != "{page}", f"{where}#{i}: [big|…] cannot open a page"
            assert not joins(paragraphs[i - 1], text), f"{where}#{i}: [big|…] needs the blank line above it, not a list item"
        if text == "{rule}":
            assert 0 < i < len(paragraphs) - 1 and "{page}" not in (paragraphs[i - 1], paragraphs[i + 1]), \
                f"{where}#{i}: a rule goes between paragraphs"


def rule_line():
    return json.dumps(["", {"text": "─" * 26, "color": RULE_COLOR}], ensure_ascii=False, separators=(",", ":"))


# --------------------------------------------------------------------------------------------------
# Fonts for the companion (written by tools/generate_quests.py)

# Vanilla glyph sheets at twice their height. The character layout is the one minecraft:include/default gives
# these sheets (ascii.png: 16×16 cells; nonlatin_european.png: 16×67); only the cells [big] may use are mapped.
ASCII_ROWS = ["\u0000" * 16, "\u0000" * 16, " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO",
              "PQRSTUVWXYZ[\\]^_", "`abcdefghijklmno", "pqrstuvwxyz{|}~\u0000"] + ["\u0000" * 16] * 8
NONLATIN_CELLS = {(0, 7): "×", (0, 3): "·", (15, 9): "•", (9, 5): "→", (56, 7): "★"}


def big_font():
    ascii_rows = [r.replace(" ", "\u0000") for r in ASCII_ROWS]   # the space is the space provider's
    nonlatin = [["\u0000"] * 16 for _ in range(67)]
    for (row, col), ch in NONLATIN_CELLS.items():
        nonlatin[row][col] = ch
    return {"providers": [
        {"type": "space", "advances": {" ": 8}},
        {"type": "bitmap", "file": "minecraft:font/ascii.png", "height": 16, "ascent": 14, "chars": ascii_rows},
        {"type": "bitmap", "file": "minecraft:font/nonlatin_european.png", "height": 16, "ascent": 14,
         "chars": ["".join(r) for r in nonlatin]},
        {"type": "reference", "id": "minecraft:include/default"},   # anything else: normal size, never a box
    ]}


def icon_font(textures):
    providers = []
    for texture in sorted(textures):
        ns, path = texture.split(":", 1)
        assert path.startswith("textures/") and path.endswith(".png"), texture
        providers.append({"type": "bitmap", "file": f"{ns}:{path[len('textures/'):]}", "height": 8, "ascent": 7,
                          "chars": [glyph(texture)]})
    return {"providers": providers}


def font_files(root, glyphs):
    """{path: text} for the two fonts; glyphs are the textures the compiled sectors drew."""
    out = {}
    for name, data in (("quest_big", big_font()), ("quest_icons", icon_font(glyphs | {NAMED["tip"]}))):
        out[root / FONT_DIR / f"{name}.json"] = json.dumps(data, indent=1) + "\n"   # glyphs as \u escapes
    return out


# --------------------------------------------------------------------------------------------------
# Width estimate (rules only; the preview renderer measures with the real glyphs)

NARROW = {"i": 2, "l": 3, "'": 2, ".": 2, ",": 2, ":": 2, ";": 2, "!": 2, "|": 2, " ": 4, "t": 4, "I": 4,
          "f": 5, "k": 5, "(": 5, ")": 5, "[": 4, "]": 4, "`": 3, "*": 5, "<": 5, ">": 5, "{": 5, "}": 5,
          "@": 7, "~": 7, "×": 6, "«": 7, "»": 7, "–": 7, "—": 9, "·": 2, "•": 4}


def width(text, bold=False, scale=1):
    px = sum(NARROW.get(c, 6) for c in text)
    return (px + (len(text) if bold else 0)) * scale
