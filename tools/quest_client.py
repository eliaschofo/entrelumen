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
- Seams. QuestPanel.alignWidgets rounds each image's position and its width separately, so two tiles that touch
  exactly leave a 1-pixel gap at some zooms. Where an opaque tile is drawn after a tile it touches and covers that
  whole edge, appears whenever the first one does and is at least as wide as the overlap, the first tile grows under
  it by SEAM (0.1 grid units; at most one texel of a texture tile, whose texture stretches with it): the later tile
  hides the overlap, so nothing visible moves (close_seams). Translucent tiles keep their edges: an overlap would draw
  a darker stripe. Textured path pieces overlap the same way along the path (tools/quest_art.py).
- Rotated images. ChapterImageButton.collidesWith is true for any rotated image, so the panel never culls one and
  draws it every frame wherever the view is (15,328 in the book, 411 in Create · Kinetics). A colour fill turned a
  quarter turn is the same box with width and height swapped, and one turned half a turn is the same box unturned
  (unturn). Textures keep their rotation: their grain would change.
- Links to hidden quests. A description's change_page link and an image's open_quest click call QuestScreen.open,
  which opens a quest's panel whether or not the player may see it yet (viewQuest ignores visibility). A link to a
  quest that the canvas hides (an invisible secret, or hide_until_deps_complete with dependencies) opens that
  quest's chapter instead, keeping its text (fix_links); tools/check_guides.py lists them as info (LINKS).
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
PX = "entrelumen:textures/gui/quests/px.png"   # quest_engine.PX: a colour fill is this pixel, tinted
SEAM = 0.1   # grid units a tile grows under the later tile it touches (close_seams)
SPANISH = ("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve")   # vanilla's Spanish locales besides es_es
SEEN = {"textures": set(), "items": set()}   # what the last pass met: check_guides.py checks the facts against it
KEPT = []         # sheets the last pass had to leave as strips: (chapter, image id, texture, why)
OVER_NODES = []   # 3D item renders on a node: (chapter, image id, item, quest id; for a link, its quest's)
LINKS = []        # links retargeted to a chapter: (source: quest/image id, hidden quest id, its chapter's file name)
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
    LINKS.clear()


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


def unturn(img):
    """A colour fill turned by a multiple of 90 degrees, as the same box unrotated (so FTB can cull it)."""
    if img.get("image") != PX or img.get("text_on_image"):
        return img
    r = float(img.get("rotation", 0.0)) % 360
    quarter = round(r / 90)
    if r and abs(r - 90 * quarter) < 1e-6:
        if quarter % 2:
            img["width"], img["height"] = img["height"], img["width"]
        img["rotation"] = 0.0
    return img


def _tile(img):
    """(x0, y0, x1, y1) of an image that can close a seam: a colour fill, a texture or a sprite, unrotated (or turned
    half a turn, the same box), no text; None otherwise."""
    ref = img.get("image", "")
    if not ref or ref.startswith("item:") or img.get("text_on_image"):
        return None
    if float(img.get("rotation", 0.0)) % 180:
        return None
    w, h = img["width"] * VISUAL, img["height"] * VISUAL
    return img["x"] - w / 2, img["y"] - h / 2, img["x"] + w / 2, img["y"] + h / 2


def _covered(span, pieces, eps=1e-3):
    """Whether the intervals cover span."""
    lo, hi = span
    for a, b in sorted(pieces):
        if a > lo + eps:
            return False
        lo = max(lo, b)
        if lo >= hi - eps:
            return True
    return lo >= hi - eps


def close_seams(images):
    """Grow each tile under the later opaque tiles that touch it (see the module notes). Changes images in place;
    returns how many tiles grew."""
    eps = 1e-3
    tiles = {}
    for n, img in enumerate(images):
        box = _tile(img)
        if box:
            tiles[n] = box
    rank = {n: r for r, n in enumerate(sorted(tiles, key=lambda n: (images[n].get("order", 0), n)))}
    # sides: 0 right, 1 left, 2 bottom, 3 top; an edge is keyed by its rounded coordinate
    starts = [{}, {}, {}, {}]   # the edge a neighbour must start at, by side
    for n, (x0, y0, x1, y1) in tiles.items():
        for side, coord in ((1, x1), (0, x0), (3, y1), (2, y0)):   # n is a right/left/bottom/top neighbour there
            starts[side].setdefault(round(coord / eps), []).append(n)
    grown = 0
    for n, (x0, y0, x1, y1) in tiles.items():
        a = images[n]
        grow = []
        for side, coord, span in ((0, x1, (y0, y1)), (1, x0, (y0, y1)), (2, y1, (x0, x1)), (3, y0, (x0, x1))):
            cover, ok = [], True
            for m in starts[side].get(round(coord / eps), []):
                bx0, by0, bx1, by1 = tiles[m]
                other = (by0, by1) if side < 2 else (bx0, bx1)
                if m == n or min(span[1], other[1]) - max(span[0], other[0]) <= eps:
                    continue
                b = images[m]
                later = rank[m] > rank[n]
                together = b.get("dependency") in (None, a.get("dependency"))
                opaque = b.get("alpha", 255) == 255
                if not (later and together and opaque):
                    ok = False
                    break
                cover.append(other)
                depth = (bx1 - bx0) if side < 2 else (by1 - by0)
                size = (x1 - x0) if side < 2 else (y1 - y0)
                step = SEAM if a["image"] == PX else min(SEAM, size / 16)
                if depth < step + eps:
                    ok = False
                    break
            if ok and cover and _covered(span, cover):
                grow.append(side)
        for side in grow:
            size = (x1 - x0) if side < 2 else (y1 - y0)
            step = SEAM if a["image"] == PX else min(SEAM, size / 16)
            axis, extent = ("x", "width") if side < 2 else ("y", "height")
            sign = 1 if side in (0, 2) else -1
            a[axis] = _num(a[axis] + sign * step / 2)
            a[extent] = _num(a[extent] + step / VISUAL)
        grown += bool(grow)
    return grown


def hidden_quests(chapters):
    """{quest id: (chapter id, chapter file name)} of the quests a player does not see before their time: invisible
    ones, and hide_until_deps_complete ones that have dependencies."""
    out = {}
    for chapter in chapters:
        for q in chapter.get("quests", []):
            if q.get("invisible") or (q.get("hide_until_deps_complete") and q.get("dependencies")):
                out[q["id"]] = (chapter["id"], chapter.get("filename", chapter["id"]))
    return out


def _retarget(component, hidden, found):
    """Point every change_page click of a text component at a hidden quest to its chapter instead."""
    if isinstance(component, list):
        for part in component:
            _retarget(part, hidden, found)
    elif isinstance(component, dict):
        click = component.get("clickEvent")
        if isinstance(click, dict) and click.get("action") == "change_page":
            target = str(click.get("value", "")).split("/", 1)[0]
            if target in hidden:
                click["value"] = hidden[target][0]
                found.append(target)
        for key in ("extra", "with"):
            if key in component:
                _retarget(component[key], hidden, found)
        hover = component.get("hoverEvent")
        if isinstance(hover, dict) and hover.get("action") == "show_text":
            _retarget(hover.get("contents"), hidden, found)


def fix_links(chapters, languages):
    """Links and image clicks that would open a hidden quest open its chapter (see the module notes). chapters is
    every compiled chapter (dicts, changed in place); languages the book's tables (changed in place)."""
    hidden = hidden_quests(chapters)
    for chapter in chapters:
        for img in chapter.get("images", []):
            click = img.get("click_action", "")
            if click.startswith("open_quest:") and click[len("open_quest:"):] in hidden:
                target = click[len("open_quest:"):]
                img["click_action"] = "open_quest:" + hidden[target][0]
                LINKS.append((img["id"], target, hidden[target][1]))
    for lang, table in languages.items():
        for key, value in table.items():
            lines = value if isinstance(value, list) else [value]
            changed = False
            for n, line in enumerate(lines):
                if '"change_page"' not in line:
                    continue
                component, found = json.loads(line), []
                _retarget(component, hidden, found)
                if found:
                    lines[n] = json.dumps(component, ensure_ascii=False, separators=(",", ":"))
                    changed = True
                    if lang == "en_us":
                        LINKS.extend((key.split(".")[1], target, hidden[target][1]) for target in found)
            if changed and not isinstance(value, list):
                table[key] = lines[0]


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
        images.append(unturn(img))
    close_seams(images)
    if "images" in chapter:
        chapter["images"] = images
    return chapter
