"""What FTB Quests 2101.1.34's client does with a compiled chapter, corrected at compile time.

An adversarial read of FTB Quests and FTB Library (30 September 2026) found images that the real client draws
differently from what the canvas vocabulary (tools/quest_art.py) means. This pass runs over every compiled chapter
in tools/generate_quests.py, after the chapters are built and before they are written:

- Sheets. ImageIcon.draw draws the whole PNG (UV 0 to 1) and ignores its .mcmeta, so an animated texture shows all
  its frames as a strip, and a connected-texture sheet (Fusion, as Rechiseled uses) shows every tile squeezed into
  the box. The "; u0= v1=" properties ImageIcon.setProperties accepts do not survive: ChapterImage.writeNetData sends
  the image as Icon.toString(), the bare texture. So an animated texture of the block atlas (textures/block/…,
  textures/item/…) is drawn as its atlas sprite (ns:block/x), which the client animates; any other sheet is drawn as
  one glyph of a bitmap font that cuts the sheet into its tiles (entrelumen:quest_tiles, written into the
  companion): the image becomes a text_on_image label whose text is that glyph, scaled to the box.
- Item renders. An "item:" image is drawn by GuiGraphics.renderItem, about 150 above the canvas with the depth test
  on, so it covers node frames and dependency lines whatever its order. An item whose inventory icon is its own flat
  texture is drawn as that atlas sprite instead (ns:item/x), in canvas order and at the canvas' depth, opaque and
  untinted as the render was and square like ItemIcon draws it. Any other item stays a render: the ones that sit on a
  node are listed (OVER_NODES) and tools/check_guides.py warns with the chapter, the image and the node.
- Spanish players. FTB sends a player the book's strings for their exact locale (TranslationManager
  .sendTableToPlayer) and English otherwise, and Minecraft loads a resource pack's or a mod's lang file for the exact
  locale too: a player on es_ar, es_mx, es_cl, es_uy, es_ve or es_ec would read the book, the companion and the
  pack's strings in English. Every es_es strings file (the book's, the companion's, the resource pack's) gets a copy
  under each of those locales (spanish_copies); the text is rioplatense either way.

Facts about textures live in the pinned JARs, and the generator reads the repository only, so they come from a
committed file, tools/quest_client_facts.json: the sheets among the textures the book draws (size, tile, kind) and,
for every item the canvas draws, the atlas sprite of its flat icon or null. It is written and checked by
tools/check_guides.py (--write-client-facts), which also sees what this pass met in the last compile (SEEN) and
errors when the file is stale. A texture or an item missing from the file is drawn as it is.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FACTS = ROOT / "tools/quest_client_facts.json"
FONT_TILES = "entrelumen:quest_tiles"
TILE_FONT_FILE = "companion/src/main/resources/assets/entrelumen/font/quest_tiles.json"
TILE_FIRST, TILE_LAST = 0xF800, 0xF8FF   # the end of the Private Use Area; quest_text uses 0xE100-0xF7FF
# A tile glyph is 9 px tall (the line height, so text_on_image fills the box's height with it) and, for an opaque
# tile, advances 10 px (BitmapProvider adds 1 to its 9). A box 10/9 as wide as it is tall fits the glyph exactly at
# its left edge.
TILE_HEIGHT, TILE_ASCENT, TILE_ADVANCE = 9, 7, 10
VISUAL = 24 / 28
SPANISH = ("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve")   # vanilla's Spanish locales besides es_es
SEEN = {"textures": set(), "items": set()}   # what the last pass met: check_guides.py checks the facts against it
KEPT = []         # sheets the last pass had to leave as strips: (chapter, image id, texture, why)
OVER_NODES = []   # 3D item renders on a node: (chapter, image id, item, quest id; for a link, its quest's)
_FACTS = None


def load_facts(path=FACTS):
    global _FACTS
    if path != FACTS:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    if _FACTS is None:
        _FACTS = json.loads(FACTS.read_text(encoding="utf-8")) if FACTS.exists() else {"sheets": {}, "items": {}}
    return _FACTS


def facts_text(facts):
    """The facts file, one entry per line, so a refresh reads as a short diff."""
    out = '{\n "_doc": ' + json.dumps(facts.get("_doc", ""))
    for section in ("sheets", "items"):
        rows = [f"  {json.dumps(k)}: {json.dumps(v, sort_keys=True)}" for k, v in sorted(facts.get(section, {}).items())]
        out += f',\n "{section}": {{\n' + ",\n".join(rows) + ("\n" if rows else "") + " }"
    return out + "\n}\n"


def reset():
    for refs in SEEN.values():
        refs.clear()
    KEPT.clear()
    OVER_NODES.clear()


def atlas_sprite(texture):
    """'ns:textures/block/x.png' -> 'ns:block/x' (vanilla's block atlas stitches every textures/block and
    textures/item PNG of every namespace), or None for a texture outside the atlas."""
    ns, path = texture.split(":", 1)
    if path.startswith(("textures/block/", "textures/item/")) and path.endswith(".png"):
        return f"{ns}:{path[len('textures/'):-len('.png')]}"
    return None


def tile_glyphs(facts):
    """Code point of every sheet drawn as a glyph: sorted, so the font and the chapters always agree."""
    names = sorted(t for t, s in facts["sheets"].items() if not (s["kind"] == "animation" and atlas_sprite(t)))
    assert TILE_FIRST + len(names) - 1 <= TILE_LAST, "too many tile glyphs for their range"
    return {t: chr(TILE_FIRST + i) for i, t in enumerate(names)}


def tile_font(facts):
    """The bitmap font that cuts each sheet into its tiles; only the first tile gets a character."""
    providers = []
    for texture, ch in sorted(tile_glyphs(facts).items(), key=lambda kv: kv[1]):
        sheet = facts["sheets"][texture]
        (w, h), (tw, th) = sheet["size"], sheet["tile"]
        cols, rows = w // tw, h // th
        assert cols * tw == w and rows * th == h, f"{texture}: tile {tw}x{th} does not divide {w}x{h}"
        ns, path = texture.split(":", 1)
        chars = [ch + "\u0000" * (cols - 1)] + ["\u0000" * cols] * (rows - 1)
        providers.append({"type": "bitmap", "file": f"{ns}:{path[len('textures/'):]}", "height": TILE_HEIGHT,
                          "ascent": TILE_ASCENT, "chars": chars})
    return {"providers": providers}


def font_files(root, facts=None):
    facts = facts or load_facts()
    return {root / TILE_FONT_FILE: json.dumps(tile_font(facts), indent=1) + "\n"}


def _num(value):
    return round(float(value), 4)


def _glyph_label(img, glyph, languages):
    """A sheet drawn as the glyph of its first tile: a text_on_image box, as tall as the image's shorter side and
    10/9 as wide, so the glyph lands square on the old box's centre. Tint becomes the text colour; no alpha."""
    side = min(img["width"], img["height"])
    width = side * TILE_ADVANCE / TILE_HEIGHT
    color = img.pop("color", 0xFFFFFF)
    out = {}
    for k, v in img.items():   # same keys in the same order, then the label's
        if k == "x":
            v = _num(img["x"] + (width - side) / 2 * VISUAL)
        elif k == "width":
            v = _num(width)
        elif k == "height":
            v = _num(side)
        elif k == "image":
            v = ""
        out[k] = v
    out.update(text_on_image=True, text_shadow=False)
    title = json.dumps(["", {"text": glyph, "font": FONT_TILES, "color": f"#{color:06X}"}], ensure_ascii=False,
                       separators=(",", ":"))
    for lang in languages:
        languages[lang][f"image.{img['id']}.title"] = title
    return out


def fix_sheet(img, chapter, facts, glyphs, languages):
    """An image whose texture is a sheet, drawn the way it means: atlas sprite or tile glyph."""
    texture = img["image"]
    sheet = facts["sheets"].get(texture)
    if sheet is None:
        return img
    sprite = atlas_sprite(texture) if sheet["kind"] == "animation" else None
    if sprite:
        img["image"] = sprite
        return img
    if img.get("alpha", 255) != 255:
        KEPT.append((chapter, img["id"], texture, "a glyph cannot be translucent"))
        return img
    if any(f"image.{img['id']}.title" in languages[lang] for lang in languages):
        KEPT.append((chapter, img["id"], texture, "its hover note would become the glyph"))
        return img
    return _glyph_label(img, glyphs[texture], languages)


def bounds(img):
    """(x0, y0, x1, y1) in grid units of an image's drawn box, rotation included (its bounding box)."""
    r = math.radians(float(img.get("rotation", 0.0)))
    w, h = img["width"] * VISUAL, img["height"] * VISUAL
    bw, bh = abs(w * math.cos(r)) + abs(h * math.sin(r)), abs(w * math.sin(r)) + abs(h * math.cos(r))
    return img["x"] - bw / 2, img["y"] - bh / 2, img["x"] + bw / 2, img["y"] + bh / 2


def node_under(img, nodes):
    """The first node (id, x, y, size) whose frame the image's box overlaps, or None."""
    x0, y0, x1, y1 = bounds(img)
    for nid, x, y, size in nodes:
        half = size * VISUAL / 2
        if x0 < x + half and x - half < x1 and y0 < y + half and y - half < y1:
            return nid
    return None


def fix_item(img, chapter, facts, nodes):
    """An item render drawn as its flat sprite where it has one; a render on a node is reported."""
    item = img["image"][len("item:"):].split(" ", 1)[0]
    SEEN["items"].add(item)
    sprite = facts.get("items", {}).get(item)
    if not sprite:
        node = node_under(img, nodes)
        if node:
            OVER_NODES.append((chapter, img["id"], item, node))
        return img
    side = min(img["width"], img["height"])   # ItemIcon.draw scales to the shorter side, centred
    img.update(image=sprite, width=side, height=side)
    img.pop("color", None)
    img.pop("alpha", None)
    return img


def spanish_copies(root, files):
    """{path: text}: a copy of every Spanish strings file for each other Spanish locale (SPANISH). The book's
    lang/es_es.snbt and the companion's ftbquests strings come from files (this compile); the companion's own and
    the resource pack's es_es.json are read from the repository, so editing one and regenerating keeps them in step
    (tools/generate_quests.py --check reports a copy that drifted)."""
    sources = {p: t for p, t in files.items() if p.name in ("es_es.snbt", "es_es.json") and p.parent.name == "lang"}
    for base in ("companion/src/main/resources/assets", "pack/resourcepacks/entrelumen/assets"):
        for p in sorted((root / base).glob("*/lang/es_es.json")):
            sources.setdefault(p, p.read_text(encoding="utf-8"))
    return {p.with_name(locale + p.suffix): text for p, text in sorted(sources.items()) for locale in SPANISH}


def fix_chapter(chapter, languages, facts=None):
    """Every client fix of one compiled chapter (a dict, changed in place)."""
    facts = facts or load_facts()
    glyphs = tile_glyphs(facts)
    name = chapter.get("filename", chapter.get("id"))
    nodes = [(q["id"], q["x"], q["y"], q.get("size", 1.0)) for q in chapter.get("quests", [])]
    nodes += [(link.get("linked_quest", link["id"]), link["x"], link["y"], link.get("size", 1.0))   # a link: its quest
              for link in chapter.get("quest_links", [])]
    images = []
    for img in chapter.get("images", []):
        ref = img.get("image", "")
        if ref.endswith(".png") and ":" in ref:
            SEEN["textures"].add(ref)
            img = fix_sheet(img, name, facts, glyphs, languages)
        elif ref.startswith("item:"):
            img = fix_item(img, name, facts, nodes)
        images.append(img)
    if "images" in chapter:
        chapter["images"] = images
    return chapter
