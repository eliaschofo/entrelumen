"""Previews of the generated FTB quest book, drawn the way FTB Quests 2101.1.34 draws it (presentation v2).

What it draws: the chapter canvas (every image kind of tools/quest_art.py: pictures, block-atlas sprites as their
first frame, item renders with 3D block and OBJ models, tinted pixels, rotated labels in any font), the nodes
with their shapes and icons, the dependency lines; and the quest panel laid out like ViewQuestPanel (width from
the chapter's default_min_width, 9 px lines without shadow, the companion fonts, inline images, page bar).
States: fresh (a new player), done, or a share of the chapter done in dependency order, to watch the drawing
complete itself (the sketch-first rule of docs/design/quest-book-v3.md).

Not an in-game screenshot: close enough to judge composition, reveal order and legibility. Every texture is read
from the pinned JARs (catalog/local-paths.json) and the vanilla client JAR when drawn; nothing is copied into the
repository, and the previews go outside it (E:/Elias/Codex/Entrelumen-ssd/previews/<worktree>/ by default).

Rendering is Pillow only (no browser) and runs one at a time on this PC: tools/preview/render_lock.py takes
E:/Elias/Codex/Entrelumen-ssd/render.lock for the whole batch and waits before each chapter while free memory is
under 1.5 GB. Render what you need in one call, then let it exit.

Any compiled chapter draws: sectors, guides and story chapters (by chapter name, or by content file:
guide_entrelumen_start, the_lost_crafts, content/act_two.json).

usage: python tools/preview/preview_v2.py sector_x [sector_y ...] [--state fresh,0.33,0.66,done | steps]
           [--locale es_es|en_us|both] [--panels all|KEY,KEY] [--gui 2] [--screen] [--sheet]
           [--tree <worktree>] [--out <folder>] [--scale N]
  Run generate_quests.py first: it draws the generated chapter files of --tree (default: this worktree).
"""
import argparse
import gc
import hashlib
import io
import json
import math
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from mcassets import Assets, VANILLA  # noqa: E402
from mcmodel import ModelRenderer  # noqa: E402
import render_lock  # noqa: E402

ROOT = HERE.parents[1]
OUT_BASE = Path("E:/Elias/Codex/Entrelumen-ssd/previews")


def pinned_jar(tree, prefix):
    """The pinned JAR whose file name starts with prefix (catalog/local-paths.json)."""
    paths = json.loads((Path(tree) / "catalog/local-paths.json").read_text(encoding="utf-8"))
    for name, path in sorted(paths.items()):
        if name.startswith(prefix):
            return path
    raise SystemExit(f"preview: no pinned JAR {prefix}* in catalog/local-paths.json")


NODE, GRID = 24, 28
DEFAULTS = {"quest_locked_color": "#FF999999", "quest_not_started_color": "#96FFFFFF",
            "quest_completed_color": "#C856FF56", "quest_started_color": "#C800FFFF",
            "dependency_line_completed_color": "#64DC64", "dependency_line_uncompleted_color": "#B4CCA3A3",
            "dependency_line_unavailable_color": "#64CCA3A3", "dependency_line_thickness": "0.17",
            "tasks_text_color": "#5555FF", "rewards_text_color": "#FFAA00"}
NAMED = {"black": "#000000", "dark_blue": "#0000AA", "dark_green": "#00AA00", "dark_aqua": "#00AAAA",
         "dark_red": "#AA0000", "dark_purple": "#AA00AA", "gold": "#FFAA00", "gray": "#AAAAAA", "dark_gray": "#555555",
         "blue": "#5555FF", "green": "#55FF55", "aqua": "#55FFFF", "red": "#FF5555", "light_purple": "#FF55FF",
         "yellow": "#FFFF55", "white": "#FFFFFF"}
KEYS = {"key.ponder.ponder": "W", "key.ftbquests.quests": "F8", "key.mekanism.mode": "M",
        "create.keyinfo.toolbelt": "Left Alt", "create.keyinfo.ctrl_modifier": "Left Ctrl"}


def argb(s):
    s = NAMED.get(s, s)
    s = s.strip().lstrip("#")
    v = int(s, 16)
    if len(s) == 6:
        return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255)


def stable_id(key):
    value = int.from_bytes(hashlib.sha256(("entrelumen:v1:" + key).encode()).digest()[:8], "big")
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def tint(im, rgba):
    r, g, b, a = rgba
    bands = im.convert("RGBA").split()
    return Image.merge("RGBA", [bands[0].point(lambda v: v * r // 255), bands[1].point(lambda v: v * g // 255),
                                bands[2].point(lambda v: v * b // 255), bands[3].point(lambda v: v * a // 255)])


# ------------------------------------------------------------------------------------------------------------
# Fonts: bitmap providers from the vanilla JAR, the pinned JARs and the companion

class Fonts:
    def __init__(self, assets, tree):
        self.A = assets
        self.tree = Path(tree)
        self.vz = zipfile.ZipFile(VANILLA)
        self.cache = {}

    def _json(self, font_id):
        ns, path = font_id.split(":", 1)
        rel = f"assets/{ns}/font/{path}.json"
        local = self.tree / "companion/src/main/resources" / rel
        if local.exists():
            return json.loads(local.read_text(encoding="utf-8"))
        try:
            return json.loads(self.vz.read(rel))
        except KeyError:
            raw = self.A.raw(rel)
            return json.loads(raw) if raw else {"providers": []}

    def _png(self, ref):
        ns, path = ref.split(":", 1)
        rel = f"assets/{ns}/textures/{path}"
        local = self.tree / "companion/src/main/resources" / rel
        if local.exists():
            return Image.open(local).convert("RGBA")
        try:
            return Image.open(io.BytesIO(self.vz.read(rel))).convert("RGBA")
        except KeyError:
            raw = self.A.raw(rel)
            return Image.open(io.BytesIO(raw)).convert("RGBA") if raw else None

    def glyphs(self, font_id, depth=0):
        """char -> ('space', advance) | (image, advance, ascent, scale)"""
        if font_id in self.cache:
            return self.cache[font_id]
        table = {}
        self.cache[font_id] = table
        for p in self._json(font_id).get("providers", []):
            t = p.get("type")
            if t == "reference" and depth < 5:
                for ch, g in self.glyphs(p["id"], depth + 1).items():
                    table.setdefault(ch, g)
            elif t == "space":
                for ch, adv in p.get("advances", {}).items():
                    table.setdefault(ch, ("space", adv))
            elif t == "bitmap":
                sheet = self._png(p["file"])
                if sheet is None:
                    continue
                rows = p["chars"]
                cols = max(len(r) for r in rows)
                cw, ch_ = sheet.width // cols, sheet.height // len(rows)
                height = p.get("height", 8)
                scale = height / ch_
                for ry, row in enumerate(rows):
                    for rx, c in enumerate(row):
                        if c == "\u0000" or c in table:
                            continue
                        cell = sheet.crop((rx * cw, ry * ch_, rx * cw + cw, ry * ch_ + ch_))
                        width = 0
                        px = cell.load()
                        for x in range(cw - 1, -1, -1):
                            if any(px[x, y][3] for y in range(ch_)):
                                width = x + 1
                                break
                        table[c] = (cell, int(0.5 + width * scale) + 1, p["ascent"], scale)
        return table

    def font_of(self, style):
        return style.get("font") or "minecraft:default"

    def advance(self, ch, style):
        g = self.glyphs(self.font_of(style)).get(ch) or self.glyphs("minecraft:default").get(ch)
        if g is None:
            adv = 6 if ch != " " else 4
        elif g[0] == "space":
            adv = g[1]
        else:
            adv = g[1]
        return adv + (1 if style.get("bold") and ch != " " else 0)

    def width(self, segs):
        return sum(self.advance(c, st) for text, st in segs for c in text)

    def draw(self, img, x, y, segs, shadow=False, base=(255, 255, 255, 255)):
        """Draw styled segments at GUI (x, y): the top of a 9 px line. Returns the end x."""
        for text, st in segs:
            col = argb(st["color"]) if st.get("color") else base
            font = self.glyphs(self.font_of(st))
            for c in text:
                g = font.get(c) or self.glyphs("minecraft:default").get(c)
                adv = self.advance(c, st)
                if g is not None and g[0] != "space":
                    cell, _, ascent, scale = g
                    gw, gh = max(1, round(cell.width * scale)), max(1, round(cell.height * scale))
                    glyph = cell.resize((gw, gh), Image.NEAREST) if (gw, gh) != cell.size else cell
                    top = y + 7 - ascent
                    if st.get("italic"):
                        # BakedGlyph: the top leans 1 px right, the bottom 1 px left (0.25 px per pixel of height)
                        pad = Image.new("RGBA", (glyph.width + 4, glyph.height), (0, 0, 0, 0))
                        pad.alpha_composite(glyph, (2, 0))
                        glyph = pad.transform(pad.size, Image.AFFINE, (1, 0.25, 0.25 * (7 - ascent), 0, 1, 0),
                                              resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
                        glyph_dx = -1
                    else:
                        glyph_dx = 0
                    passes = []
                    if shadow:
                        passes.append((1, (col[0] // 4, col[1] // 4, col[2] // 4, col[3])))
                    passes.append((0, col))
                    for off, pc in passes:
                        layer = tint(glyph, pc)
                        img.alpha_composite(layer, (int(x + off + glyph_dx), int(top + off)))
                        if st.get("bold"):
                            img.alpha_composite(layer, (int(x + off + 1 + glyph_dx), int(top + off)))
                elif g is None and c != " ":
                    ImageDraw.Draw(img).rectangle([x, y, x + 4, y + 7], outline=col)
                if st.get("underlined"):
                    ImageDraw.Draw(img).line([(x, y + 8), (x + adv - 1, y + 8)], fill=col)
                x += adv
        return x


def segments_of(raw):
    """A description line or label title -> [(text, style)] with inherited styles."""
    raw = raw.strip()
    if raw.startswith("[") and raw.endswith("]") or raw.startswith("{") and raw.endswith("}"):
        try:
            data = json.loads(raw)
        except Exception:
            data = None
        if data is not None:
            segs = []

            def walk(node, style, parent_first=False):
                if isinstance(node, str):
                    segs.append((node, dict(style)))
                    return
                if isinstance(node, list):
                    if not node:
                        return
                    first = node[0]
                    fst = dict(style)
                    if isinstance(first, dict):
                        for k in ("color", "font", "bold", "italic", "underlined"):
                            if k in first:
                                fst[k] = first[k]
                        walk(first, style)
                    else:
                        walk(first, style)
                    for n in node[1:]:
                        walk(n, fst)
                    return
                st = dict(style)
                for k in ("color", "font", "bold", "italic", "underlined"):
                    if k in node:
                        st[k] = node[k]
                if "text" in node:
                    segs.append((node["text"], st))
                if "keybind" in node:
                    segs.append((KEYS.get(node["keybind"], "?"), st))
                if "translate" in node:
                    segs.append((node["translate"].rsplit(".", 1)[-1].replace("_", " ").title(), st))
                for e in node.get("extra", []):
                    walk(e, st)
            walk(data, {})
            return segs
    segs = []
    color = None
    for part in re.split(r"(&(?:#[0-9A-Fa-f]{6}|[0-9a-fk-orz]))", raw):
        if part.startswith("&#"):
            color = "#" + part[2:]
        elif re.fullmatch(r"&[0-9a-fk-orz]", part or ""):
            pass
        elif part:
            segs.append((part, {"color": color} if color else {}))
    return segs


def wrap(fonts, segs, width):
    """Greedy word wrap like StringSplitter.splitLines: a list of lines, each a list of (text, style)."""
    words = []
    for text, st in segs:
        for piece in re.split(r"( )", text):
            if piece:
                words.append((piece, st))
    lines, cur, w = [], [], 0
    for piece, st in words:
        pw = fonts.width([(piece, st)])
        if piece != " " and w + pw > width and cur:
            while cur and cur[-1][0] == " ":
                w -= fonts.width([cur[-1]])
                cur.pop()
            lines.append(cur)
            cur, w = [], 0
        if piece == " " and not cur:
            continue
        cur.append((piece, st))
        w += pw
    if cur or not lines:
        lines.append(cur)
    return lines


# ------------------------------------------------------------------------------------------------------------

class Book:
    def __init__(self, tree):
        self.tree = Path(tree)
        self.A = Assets(tree)
        self.models = ModelRenderer(self.A)
        self.fonts = Fonts(self.A, tree)
        q = self.tree / "pack/config/ftbquests/quests"
        self.chapters = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in (q / "chapters").glob("*.snbt")}
        # en_us and es_es only: the other Spanish locales are copies of es_es (tools/quest_client.py)
        self.lang = {lang: json.loads((q / "lang" / f"{lang}.snbt").read_text(encoding="utf-8"))
                     for lang in ("en_us", "es_es")}
        self.quests = {qq["id"]: (name, qq) for name, c in self.chapters.items() for qq in c["quests"]}
        self.theme = self.load_theme()
        self.zq = zipfile.ZipFile(pinned_jar(tree, "ftb-quests-neoforge-"))
        self.zl = zipfile.ZipFile(pinned_jar(tree, "ftb-library-neoforge-"))
        self.shape_cache = {}
        self.keys = {}   # quest id -> key, for file names and sheet labels
        for p in (sorted((self.tree / "content/sectors").glob("sector_*.json")) + sorted((self.tree / "content/guides").glob("*.json"))
                  + sorted((self.tree / "content").glob("*.json"))):   # story chapters too
            try:
                for qq in json.loads(p.read_text(encoding="utf-8")).get("quests", []):
                    self.keys[stable_id("quest:" + qq["key"])] = qq["key"]
            except (ValueError, KeyError, TypeError, AttributeError):
                pass

    def key_of(self, qid):
        return self.keys.get(qid, qid)

    def load_theme(self):
        theme = {"*": dict(DEFAULTS)}
        p = self.tree / "companion/src/main/resources/assets/ftbquests/ftb_quests_theme.txt"
        sel = None
        for line in p.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("[#"):
                sel = line[2:-1]
                theme.setdefault(sel, {})
            elif sel and ":" in line and not line.startswith("//"):
                k, v = [s.strip() for s in line.split(":", 1)]
                theme[sel][k] = v
        return theme

    def prop(self, name, *objects):
        for obj in objects:
            for tag in (obj or {}).get("tags", []):
                if name in self.theme.get(tag, {}):
                    return self.theme[tag][name]
        return self.theme["*"].get(name)

    def shape(self, name, part, px):
        key = (name, part, px)
        if key not in self.shape_cache:
            local = self.tree / f"companion/src/main/resources/assets/ftbquests/textures/shapes/{name}/{part}.png"
            if local.exists():
                im = Image.open(local).convert("RGBA")
            else:
                im = Image.open(io.BytesIO(self.zq.read(f"assets/ftbquests/textures/shapes/{name}/{part}.png"))).convert("RGBA")
            self.shape_cache[key] = im.resize((px, px), Image.BILINEAR)
        return self.shape_cache[key]

    def picture(self, ref, w, h):
        """A chapter image or icon string at w×h px (None if it draws nothing)."""
        if not ref:
            return None
        if ref.startswith("item:"):
            s = max(1, min(w, h))   # ItemIcon.draw: the shorter side, centred
            im = self.models.render(ref[5:], s)
            if im is None or (w, h) == (s, s):
                return im
            box = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            box.alpha_composite(im, ((w - s) // 2, (h - s) // 2))
            return box
        if ref.startswith("#"):
            return Image.new("RGBA", (w, h), argb(ref))
        ns, _, path = ref.partition(":")
        if path.endswith(".png"):
            local = self.tree / "companion/src/main/resources/assets" / ns / path
            im = Image.open(local).convert("RGBA") if local.exists() else self.A.png(f"assets/{ns}/{path}", first_frame=False)
        else:   # block-atlas sprite: the first frame of textures/<path>.png
            im = self.A.png(f"assets/{ns}/textures/{path}.png")
        if im is None:
            return None
        return im.resize((max(1, w), max(1, h)), Image.NEAREST)

    def item_icon(self, item_id, px):
        return self.models.render(item_id, px)

    def icon_for(self, q, px):
        icon = q.get("icon")
        if icon:
            comps = icon.get("components", {})
            if "ftbquests:icon" in comps:
                return self.picture(comps["ftbquests:icon"], px, px)
            if "ftbquests:entity_face" in comps:
                ns, path = comps["ftbquests:entity_face"].split(":")
                try:
                    im = Image.open(io.BytesIO(self.zl.read(f"assets/ftblibrary/textures/faces/{ns}/{path}.png"))).convert("RGBA")
                    im = im.crop((0, 0, im.width, im.width)) if im.height > im.width else im
                    return im.resize((px, px), Image.NEAREST)
                except KeyError:
                    pass
                try:   # a face definition: a slice of the entity texture
                    face = json.loads(self.zl.read(f"assets/ftblibrary/textures/faces/{ns}/{path}.json"))
                    tex = self.picture(face["texture"], 0, 0) if False else None
                    tns, tpath = face["texture"].split(":", 1)
                    src = self.A.png(f"assets/{tns}/{tpath}", first_frame=False)
                    sl = face["slice"]
                    part = src.crop((sl["x"], sl["y"], sl["x"] + sl["width"], sl["y"] + sl["height"]))
                    s = px / max(part.size)
                    part = part.resize((max(1, round(part.width * s)), max(1, round(part.height * s))), Image.NEAREST)
                    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
                    out.alpha_composite(part, ((px - part.width) // 2, (px - part.height) // 2))
                    return out
                except (KeyError, ValueError, AttributeError):
                    return None
            if icon["id"] == "ftbquests:lootcrate":
                return self.picture("ftbquests:textures/item/loot_crate.png", px, px) if False else None
            return self.item_icon(icon["id"], px)
        tag_icon = self.prop("icon", q)
        if tag_icon and tag_icon != "none":
            return self.picture(tag_icon, px, px)
        t = q["tasks"][0]
        if t["type"] == "item":
            return self.item_icon(t["item"]["id"], px)
        if t["type"] == "advancement":
            i = self.A.advancement_icon(t["advancement"])
            return self.item_icon(i, px) if i else None
        if t["type"] == "checkmark":
            return Image.open(io.BytesIO(self.zl.read("assets/ftblibrary/textures/icons/accept.png"))).convert("RGBA").resize((px, px), Image.NEAREST)
        return None

    def text(self, lang, key):
        return self.lang[lang].get(key)

    # --------------------------------------------------------------------------------------------------------
    def draw_label(self, img, cx, cy, w, h, raw, halign, valign, rotation, shadow):
        """ChapterImageButton.maybeRenderText: scale = min(w / widest, h / (9 * lines)), then rotated."""
        segs = segments_of(raw)
        lines, cur = [], []
        for text, st in segs:
            parts = text.split("\n")
            for i, part in enumerate(parts):
                if i:
                    lines.append(cur)
                    cur = []
                if part:
                    cur.append((part, st))
        lines.append(cur)
        widest = max(1, max(self.fonts.width(l) for l in lines))
        scale = min(w / widest, h / (9 * len(lines)))
        s = max(1, math.ceil(scale))
        tw, th = int(widest * s + 2 * s + 2), int(9 * len(lines) * s + 2 * s + 2)
        layer = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        for i, l in enumerate(lines):
            lw = self.fonts.width(l)
            base = Image.new("RGBA", (lw + 2, 11), (0, 0, 0, 0))
            self.fonts.draw(base, 0, 1, l, shadow=shadow)
            base = base.resize((base.width * s, base.height * s), Image.NEAREST)
            lx = {"start": 0, "end": (widest - lw) * s}.get(halign, (widest - lw) * s // 2)
            layer.alpha_composite(base, (int(lx), int(i * 9 * s)))
        f = scale / s
        if abs(f - 1) > 1e-3:
            layer = layer.resize((max(1, round(layer.width * f)), max(1, round(layer.height * f))), Image.BILINEAR if f < 1 else Image.NEAREST)
        # position inside the w×h box (before rotation), box centred at (cx, cy)
        box = Image.new("RGBA", (max(1, int(round(w))), max(1, int(round(h)))), (0, 0, 0, 0))
        ox = {"start": 0, "end": box.width - layer.width}.get(halign, (box.width - layer.width) // 2)
        oy = {"start": 0, "end": box.height - layer.height}.get(valign, (box.height - layer.height) // 2)
        big = Image.new("RGBA", (max(box.width, layer.width) + 4, max(box.height, layer.height) + 4), (0, 0, 0, 0))
        bx, by = (big.width - box.width) // 2, (big.height - box.height) // 2
        big.alpha_composite(layer, (max(0, bx + ox), max(0, by + oy)))
        if rotation % 360:
            big = big.rotate(-rotation, expand=True, resample=Image.BICUBIC)
        img.alpha_composite(big, (int(round(cx - big.width / 2)), int(round(cy - big.height / 2))))

    def progress(self, c, state):
        """(quests done, whether quests of other chapters count as done) at a state: "fresh", "done", or a share
        of the chapter (0 to 1) completed in dependency order."""
        quests = c["quests"]
        if state == "fresh":
            return set(), False
        if state == "done":
            return {q["id"] for q in quests}, True
        local = {q["id"]: q for q in quests}
        index = {q["id"]: i for i, q in enumerate(quests)}
        depth = {}

        def deep(qid, stack=()):
            if qid not in depth:
                deps = [d for d in local[qid]["dependencies"] if d in local and d not in stack]
                depth[qid] = 1 + max((deep(d, stack + (qid,)) for d in deps), default=-1)
            return depth[qid]
        order = sorted(local, key=lambda qid: (deep(qid), index[qid]))
        return set(order[:round(float(state) * len(order))]), True

    def visible(self, q, local, done, outside):
        if q["id"] in done:
            return True
        if q.get("invisible"):
            return False
        if not q["dependencies"]:
            return True
        if q.get("hide_until_deps_complete"):
            return all(d in done if d in local else outside for d in q["dependencies"])
        return any(self.visible(local[d], local, done, outside) if d in local else True for d in q["dependencies"])

    def render(self, name, lang="es_es", scale=1, out=None, state="fresh", pad=1.2):
        c = self.chapters[name]
        local = {q["id"]: q for q in c["quests"]}
        done, outside = self.progress(c, state)
        shown = {qid for qid, q in local.items() if self.visible(q, local, done, outside)}
        elems = [(q["x"], q["y"], q["size"], q["size"]) for q in c["quests"]]
        elems += [(l["x"], l["y"], l.get("size", 1), l.get("size", 1)) for l in c.get("quest_links", [])]
        elems += [(i["x"], i["y"], i["width"], i["height"]) for i in c.get("images", [])]
        minx = min(x - w / 2 * NODE / GRID for x, y, w, h in elems) - pad
        maxx = max(x + w / 2 * NODE / GRID for x, y, w, h in elems) + pad
        miny = min(y - h / 2 * NODE / GRID for x, y, w, h in elems) - pad
        maxy = max(y + h / 2 * NODE / GRID for x, y, w, h in elems) + pad
        W, H = int((maxx - minx) * GRID), int((maxy - miny) * GRID)
        img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        bg = Image.open(io.BytesIO(self.zl.read("assets/ftblibrary/textures/gui/background_squares.png"))).convert("RGBA").resize((64, 64), Image.BILINEAR)
        bg.putalpha(bg.getchannel("A").point(lambda v: v * 0xDC // 255))
        for ty in range(0, H, 64):
            for tx in range(0, W, 64):
                img.alpha_composite(bg, (tx, ty))

        def P(x, y):
            return ((x - minx) * GRID, (y - miny) * GRID)
        self._P = P
        renders = []   # item renders: GuiGraphics.renderItem draws them about 150 above the canvas, so over the nodes

        def draw_image(im):
            cx, cy = P(im["x"], im["y"])
            w, h = max(1, round(NODE * im["width"])), max(1, round(NODE * im["height"]))
            pic = self.picture(im["image"], w, h) if im["image"] else None
            if pic is not None:
                color = im.get("color")
                alpha = im.get("alpha", 255)
                if not im["image"].startswith("item:") and (color is not None or alpha < 255):
                    col = color if color is not None else 0xFFFFFF
                    pic = tint(pic, ((col >> 16) & 255, (col >> 8) & 255, col & 255, alpha))
                rot = im.get("rotation", 0) % 360
                if rot:
                    pic = pic.rotate(-rot, expand=True, resample=Image.BICUBIC if not im["image"].startswith("item:") else Image.NEAREST)
                img.alpha_composite(pic, (int(round(cx - pic.width / 2)), int(round(cy - pic.height / 2))))
            if im.get("text_on_image"):
                raw = self.text(lang, f"image.{im['id']}.title") or ""
                if raw:
                    self.draw_label(img, cx, cy, w, h, raw, im.get("text_h_align", "middle"),
                                    im.get("text_v_align", "middle"), im.get("rotation", 0), im.get("text_shadow", False))
        for im in sorted(c.get("images", []), key=lambda i: i.get("order", 0)):
            if im.get("dependency") and im["dependency"] not in done:
                continue
            if im["image"].startswith("item:"):
                renders.append(im)
            else:
                draw_image(im)
        d = ImageDraw.Draw(img, "RGBA")
        thick = max(1, round(16 * float(self.prop("dependency_line_thickness", c)) / 4 * 3))
        for q in c["quests"]:
            if q["id"] not in shown:
                continue
            for dep in q["dependencies"]:
                p = local.get(dep)
                if not p or p["id"] not in shown or p.get("hide_dependent_lines") or q.get("hide_dependency_lines"):
                    continue
                if p["id"] in done and q["id"] in done:
                    col = argb(self.prop("dependency_line_completed_color", c))
                elif p["id"] in done:
                    col = argb(self.prop("dependency_line_uncompleted_color", c))
                else:
                    col = argb(self.prop("dependency_line_unavailable_color", c))
                ours, other = (q["x"], q["y"]), (p["x"], p["y"])
                ctrl = q.get("dep_control_pts", {}).get(dep)
                if ctrl:
                    shift = q["size"] * 3 / 7
                    c1 = (ctrl[0] - shift, ctrl[1] - shift)
                    c2 = (ctrl[2] - shift, ctrl[3] - shift)
                    pts = []
                    for i in range(33):
                        t = i / 32
                        x = ours[0] * (1 - t) ** 3 + c2[0] * 3 * (1 - t) ** 2 * t + c1[0] * 3 * (1 - t) * t * t + other[0] * t ** 3
                        y = ours[1] * (1 - t) ** 3 + c2[1] * 3 * (1 - t) ** 2 * t + c1[1] * 3 * (1 - t) * t * t + other[1] * t ** 3
                        pts.append(P(x, y))
                    d.line(pts, fill=col, width=thick)
                else:
                    d.line([P(*ours), P(*other)], fill=col, width=thick)

        def node(x, y, size, shape, obj, locked, complete):
            px = round(NODE * size)
            cx, cy = P(x, y)
            x0, y0 = int(round(cx - px / 2)), int(round(cy - px / 2))
            if complete:
                col = argb(self.prop("quest_completed_color", obj, c))
            else:
                col = argb(self.prop("quest_locked_color" if locked else "quest_not_started_color", obj, c))
            if shape != "none":
                img.alpha_composite(tint(self.shape(shape, "shape", px), (0x40, 0x40, 0x40, 255)), (x0, y0))
                img.alpha_composite(tint(self.shape(shape, "background", px), (255, 255, 255, 150)), (x0, y0))
                img.alpha_composite(tint(self.shape(shape, "outline", px), col), (x0, y0))
            s = int(px * 2 / 3 * obj.get("icon_scale", 1.0))
            icon = self.icon_for(obj, max(1, s))
            if icon is not None:
                img.alpha_composite(icon, (x0 + (px - s) // 2, y0 + (px - s) // 2))
            if locked and shape != "none" and not obj.get("hide_lock_icon"):
                img.alpha_composite(tint(self.shape(shape, "shape", px), (0, 0, 0, 100)), (x0, y0))
        for q in c["quests"]:
            if q["id"] not in shown:
                continue
            complete = q["id"] in done
            locked = not complete and any(d not in done if d in local else not outside for d in q["dependencies"])
            node(q["x"], q["y"], q["size"], q.get("shape") or c.get("default_quest_shape") or "circle", q, locked, complete)
        for l in c.get("quest_links", []):
            target = self.quests.get(l["linked_quest"])
            tq = target[1] if target else {"tags": [], "dependencies": [], "tasks": [{"type": "checkmark"}]}
            node(l["x"], l["y"], l.get("size", 1), l.get("shape") or "circle", tq,
                 not outside and bool(tq["dependencies"]), state == "done")
        for im in renders:
            draw_image(im)
        self.last_origin = (minx, miny)
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST) if scale != 1 else img
        if out:
            big.convert("RGB").save(out, optimize=True)
        return big

    # --------------------------------------------------------------------------------------------------------
    def panel(self, qid, lang="es_es", page=0, gui=2):
        """ViewQuestPanel at GUI pixels, drawn at `gui` physical pixels per GUI pixel."""
        name, q = self.quests[qid]
        c = self.chapters[name]
        f = self.fonts
        title = self.text(lang, f"quest.{qid}.title") or ""
        subtitle = self.text(lang, f"quest.{qid}.quest_subtitle") or ""
        desc = self.text(lang, f"quest.{qid}.quest_desc") or []
        pages, cur = [], []
        for line in desc:
            if line == "{@pagebreak}":
                pages.append(cur)
                cur = []
            else:
                cur.append(line)
        pages.append(cur)
        page = min(page, len(pages) - 1)
        title_w = f.width([(title, {})])
        w = max(200, title_w + 54)
        if c.get("default_min_width", 0) > 0:
            w = max(c["default_min_width"], w)
        n_tasks, n_rewards = len(q["tasks"]), max(1, len(q.get("rewards", [])))
        w = max(w, 70 * 2 + 10)
        if w % 2 == 0:
            w += 1
        inner = w - 6
        # widgets of the text panel
        rows = []  # (kind, payload, height)
        if subtitle:
            for l in wrap(f, [(subtitle, {"italic": True, "color": "#AAAAAA"})], inner):
                rows.append(("center", l, 9))
            rows[-1] = (rows[-1][0], rows[-1][1], 8)
            rows.append(("space", None, 7))
        body = pages[page]
        for para in body:
            if para.startswith("{image:"):
                m = re.match(r"\{image:(\S+)((?: [a-z_]+:\S+)*)\}", para)
                props = dict(p.split(":", 1) for p in m.group(2).split())
                iw, ih = int(props.get("width", 100)), int(props.get("height", 100))
                if props.get("fit") == "true":
                    ih, iw = int(ih * inner / iw), inner
                rows.append(("image", (m.group(1), iw, ih, props.get("align", "center")), ih))
                continue
            if not para:
                rows.append(("text", [], 8))
                continue
            lines = wrap(f, segments_of(para), inner)
            for i, l in enumerate(lines):
                rows.append(("text", l, 9 if i < len(lines) - 1 else 8))
        if len(pages) > 1:
            rows.append(("space", None, 3))
            rows.append(("pagebar", (page, len(pages)), 15))
        # heights: widgets separated by 1 px (WidgetLayout.Vertical(0, 1, 2)); rows of one TextField pitch 9
        text_h = 0
        prev_text = False
        for kind, payload, h in rows:
            text_h += h
        text_h += sum(1 for r in rows) + 2
        content_y = 16
        task_h = 18
        text_y = content_y + 16 + task_h + 12
        H = text_y + text_h + 6
        img = Image.new("RGBA", (w, H), (0, 0, 0, 0))
        bg = Image.open(io.BytesIO(self.zl.read("assets/ftblibrary/textures/gui/background_squares.png"))).convert("RGBA").resize((64, 64), Image.BILINEAR)
        bg = tint(bg, (0xB4, 0xB4, 0xB4, 255))
        for ty in range(0, H, 64):
            for tx in range(0, w, 64):
                img.alpha_composite(bg, (tx, ty))
        mask = Image.new("L", img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, H - 1], radius=2, fill=255)
        img.putalpha(mask)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([0, 0, w - 1, H - 1], radius=2, outline=(0x1B, 0x1D, 0x1E, 255))
        # title (centred, #AAAAAA, no shadow)
        f.draw(img, (w - title_w) // 2, 4, [(title, {})], base=(0xAA, 0xAA, 0xAA, 255))
        # close and pin buttons
        d.rectangle([w - 18, 4, w - 3, 19], outline=(0x55, 0x55, 0x55, 255))
        d.rectangle([w - 36, 4, w - 21, 19], outline=(0x55, 0x55, 0x55, 255))
        # tasks / rewards headers
        tasks_col = argb(self.prop("tasks_text_color", q, c))
        rew_col = argb(self.prop("rewards_text_color", q, c))
        w2 = w // 2
        t_label = "Tareas" if lang == "es_es" else "Tasks"
        r_label = "Recompensas" if lang == "es_es" else "Rewards"
        f.draw(img, (w2 - 3) // 2 + 2 - f.width([(t_label, {})]) // 2, content_y + 2 + 2, [(t_label, {})], base=tasks_col)
        f.draw(img, w2 + 2 + (w2 - 3) // 2 - f.width([(r_label, {})]) // 2, content_y + 2 + 2, [(r_label, {})], base=rew_col)
        # task and reward buttons
        def buttons(items, x0, colw):
            total = len(items) * 20 - 2
            x = x0 + (colw - total) // 2
            for icon in items:
                d.rectangle([x, content_y + 16, x + 17, content_y + 33], outline=(0x1B, 0x1D, 0x1E, 255))
                if icon is not None:
                    img.alpha_composite(icon, (x + 1, content_y + 17))
                x += 20
        task_icons = []
        for t in q["tasks"]:
            if t.get("icon"):
                task_icons.append(self.icon_for({"icon": t["icon"], "tasks": [t], "tags": []}, 16))
            elif t["type"] == "item":
                task_icons.append(self.item_icon(t["item"]["id"], 16))
            else:
                task_icons.append(self.icon_for({"tasks": [t], "tags": []}, 16))
        rew_icons = []
        for r in q.get("rewards", []):
            if r["type"] == "xp":
                rew_icons.append(self.picture("minecraft:textures/item/experience_bottle.png", 16, 16))
            elif r["type"] == "item":
                rew_icons.append(self.item_icon(r["item"]["id"], 16) if r["item"]["id"] != "ftbquests:lootcrate" else self.picture("minecraft:textures/block/barrel_side.png", 16, 16))
            elif r["type"] in ("choice", "random", "loot"):
                rew_icons.append(self.picture("minecraft:textures/item/bundle.png", 16, 16))
        buttons(task_icons, 2, w2 - 3)
        if rew_icons:
            buttons(rew_icons, w2 + 2, w2 - 3)
        else:
            nr = "Sin recompensas" if lang == "es_es" else "No Rewards"
            f.draw(img, w2 + 2 + (w2 - 3) // 2 - f.width([(nr, {})]) // 2, content_y + 21, [(nr, {})], base=(0x99, 0x99, 0x99, 255))
        border = (0x1B, 0x1D, 0x1E, 255)
        d.line([(w2, content_y), (w2, content_y + 16 + task_h + 6)], fill=border)
        d.line([(1, content_y + 16 + task_h + 6), (w - 2, content_y + 16 + task_h + 6)], fill=border)
        # text panel
        y = text_y
        for kind, payload, h in rows:
            if kind == "center":
                lw = f.width(payload)
                f.draw(img, 3 + (inner - lw) // 2, y, payload)
            elif kind == "text":
                f.draw(img, 3, y, payload)
            elif kind == "image":
                ref, iw, ih, align = payload
                pic = self.picture(ref, iw, ih)
                x = {"left": 3, "right": 3 + inner - iw}.get(align, 3 + (inner - iw) // 2)
                if pic is not None:
                    img.alpha_composite(pic, (x, y))
            elif kind == "pagebar":
                pg, total = payload
                lab = f"{pg + 1}/{total}"
                lw = f.width([(lab, {})])
                f.draw(img, 3 + inner - 24 - lw, y + 3, [(lab, {})], base=(0xAA, 0xAA, 0xAA, 255))
                d.polygon([(3 + inner - 14, y + 4), (3 + inner - 8, y + 7), (3 + inner - 14, y + 10)], fill=(0xDD, 0xDD, 0xDD, 255)) if pg < total - 1 else None
                if pg > 0:
                    d.polygon([(3 + inner - 36 - lw, y + 7), (3 + inner - 30 - lw, y + 4), (3 + inner - 30 - lw, y + 10)], fill=(0xDD, 0xDD, 0xDD, 255))
            y += h + 1
        return img.resize((w * gui, H * gui), Image.NEAREST)


def screen(book, name, lang, state, gui=2):
    """What a 1920×1080 screen shows at a GUI scale and zoom 16, centred on the chapter's autofocus quest."""
    full = book.render(name, lang, 1, None, state)
    minx, miny = book.last_origin
    c = book.chapters[name]
    focus = next((q for q in c["quests"] if q["id"] == c.get("autofocus_id")), c["quests"][0])
    cx, cy = (focus["x"] - minx) * GRID, (focus["y"] - miny) * GRID
    w, h = 1920 // gui, 1080 // gui
    view = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    view.alpha_composite(full.crop((int(cx - w / 2), int(cy - h / 2), int(cx + w / 2), int(cy + h / 2))))
    return view.resize((w * gui, h * gui), Image.NEAREST)


def sheet(fonts, pages, cols=3, pad=12):
    """Panels side by side with their quest key above each: one image to review a chapter's text."""
    if not pages:
        return None
    width = max(im.width for _, im in pages)
    rows = [pages[i:i + cols] for i in range(0, len(pages), cols)]
    heights = [max(im.height for _, im in row) + 22 for row in rows]
    out = Image.new("RGBA", (cols * (width + pad) + pad, sum(heights) + pad * (len(rows) + 1)), (0x20, 0x20, 0x24, 255))
    y = pad
    for row, h in zip(rows, heights):
        for n, (label, im) in enumerate(row):
            x = pad + n * (width + pad)
            tag = Image.new("RGBA", (fonts.width([(label, {})]) + 4, 11), (0, 0, 0, 0))
            fonts.draw(tag, 1, 1, [(label, {})], base=(0xE8, 0xDC, 0xB5, 255))
            out.alpha_composite(tag.resize((tag.width * 2, tag.height * 2), Image.NEAREST), (x, y))
            out.alpha_composite(im, (x, y + 22))
        y += h + pad
    return out


def states(spec):
    if spec == "steps":
        return ["fresh", "0.25", "0.5", "0.75", "done"]
    out = []
    for part in spec.split(","):
        part = part.strip()
        if part not in ("fresh", "done"):
            share = float(part)
            assert 0 < share < 1, f"--state: fresh, done or a share between 0 and 1, not {part}"
        out.append(part)
    return out


def state_name(state):
    return state if state in ("fresh", "done") else f"{round(float(state) * 100):02d}pct"


def chapter_name(tree, arg):
    """A chapter name, or the "chapter" of a content file (story files are named after their act, not their chapter)."""
    if arg.endswith(".json"):
        path = Path(arg) if Path(arg).is_absolute() else tree / arg
        return json.loads(path.read_text(encoding="utf-8"))["chapter"]
    return arg


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("chapters", nargs="+", help="chapter names (sector_x, guide_x, the_lost_crafts) or content files "
                                                "(content/act_two.json); the generated .snbt must exist")
    ap.add_argument("--tree", default=str(ROOT), help="worktree to draw (default: this one)")
    ap.add_argument("--out", help="output folder (default: E:/Elias/Codex/Entrelumen-ssd/previews/<worktree name>)")
    ap.add_argument("--state", default="fresh,done", help="fresh, done, shares like 0.5, or steps (5 states)")
    ap.add_argument("--locale", default="es_es", choices=("es_es", "en_us", "both"))
    ap.add_argument("--panels", default="", help="all, or quest keys separated by commas")
    ap.add_argument("--gui", type=int, default=2, help="GUI scale for panels and screen crops")
    ap.add_argument("--screen", action="store_true", help="also a 1920x1080 crop around the chapter's entry")
    ap.add_argument("--sheet", action="store_true", help="panels on sheets of 9 instead of one file each")
    ap.add_argument("--scale", type=int, default=1)
    args = ap.parse_args(argv)
    tree = Path(args.tree).resolve()
    args.chapters = [chapter_name(tree, c) for c in args.chapters]
    out = Path(args.out) if args.out else OUT_BASE / tree.name
    try:
        out.resolve().relative_to(ROOT.resolve())
        raise SystemExit("preview: previews carry mod textures; write them outside the repository")
    except ValueError:
        pass
    out.mkdir(parents=True, exist_ok=True)
    langs = ["es_es", "en_us"] if args.locale == "both" else [args.locale]
    render_lock.exit_on_signals()
    with render_lock.RenderLock():
        render_lock.wait_for_memory()
        book = Book(tree)
        missing = [n for n in args.chapters if n not in book.chapters]
        if missing:
            raise SystemExit(f"preview: no generated chapter {', '.join(missing)} in {tree} (run generate_quests.py)")
        for name in args.chapters:
            render_lock.wait_for_memory()
            for lang in langs:
                for st in states(args.state):
                    path = out / f"{name}-{lang}-{state_name(st)}.png"
                    book.render(name, lang, args.scale, path, st)
                    print("wrote", path)
                if args.screen:
                    for st in ("fresh", "done"):
                        path = out / f"{name}-{lang}-screen-{st}.png"
                        screen(book, name, lang, st, args.gui).convert("RGB").save(path, optimize=True)
                        print("wrote", path)
                wanted = args.panels.split(",") if args.panels and args.panels != "all" else None
                if args.panels:
                    ids = {stable_id("quest:" + k): k for k in wanted} if wanted else None
                    chosen = [q["id"] for q in book.chapters[name]["quests"] if ids is None or q["id"] in ids]
                    if wanted:
                        unknown = set(ids) - {q["id"] for q in book.chapters[name]["quests"]}
                        if unknown:
                            raise SystemExit(f"preview: {name} has no quest {', '.join(ids[i] for i in unknown)}")
                    pages, written = [], 0
                    for qid in chosen:
                        label = book.key_of(qid)
                        desc = book.text(lang, f"quest.{qid}.quest_desc") or []
                        for pg in range(desc.count("{@pagebreak}") + 1):
                            im = book.panel(qid, lang=lang, page=pg, gui=args.gui)
                            tag = label + (f" · {pg + 1}" if pg else "")
                            if args.sheet:
                                pages.append((tag, im))
                            else:
                                path = out / f"panel-{label}-{lang}{'-p' + str(pg + 1) if pg else ''}.png"
                                im.save(path)
                                written += 1
                    if args.sheet:
                        for n in range(0, len(pages), 9):
                            path = out / f"{name}-{lang}-panels-{n // 9 + 1}.png"
                            sheet(book.fonts, pages[n:n + 9]).convert("RGB").save(path, optimize=True)
                            print("wrote", path)
                    else:
                        print(f"wrote {written} panel pages of {name} to {out}")
            gc.collect()
        book.A.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
