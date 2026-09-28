"""Presentation v2 for the chapter canvas (docs/design/quest-book-v3.md, "Presentación v2").

Every FTB Quests element is a grain of sand: a chapter image is not only a picture, a label not only a title,
a node not only a quest. This module compiles the drawing vocabulary of a sector's "art" list into chapter
images, on top of the kinds tools/quest_engine.py already draws (texture, panel, line, label):

- picture: any texture, from our companion or a mod's JAR (referenced, never copied), stretched to any size;
- sprite: a block-atlas sprite ("minecraft:block/water_still"): animated in game, tinted by the image colour;
- item: a real item render ("item:<id>"), 3D block models included, at any size and angle;
- path: a polyline or smooth curve drawn with a colour, a texture or a sprite cut in near-square pieces, with
  dots or items set along it: rivers, shafts, belts, pipes, wires, roots, orbits;
- mosaic: pixel art whose pixels are colours, textures, sprites or items;
- glow: a soft light (the vanilla flash particle), usually revealed by a quest;
- scatter: a seeded sprinkle of small pictures (stars, sparks, smoke, leaves);
- frame: a rectangle outline with corners, optionally filled;
- text: a label at any scale, font, colour and angle; lettering: one label per letter along a path.

Every kind takes "order", "alpha", "tint" (the image colour), "rotation", "reveal" (appears when that quest is
complete: fog of war, lamps that light, drawings that grow), "hover" and "click".

What FTB syncs to the client limits the vocabulary: a chapter image travels as Icon.toString(), so the
"; u0=… tile_size=…" properties an ImageIcon can parse are lost on the way (ImageIcon.toString is the bare
texture). No crops and no tiling: a repeated pattern is repeated images. Atlas sprites and item icons survive.

Decor: a quest with role "decor" is a toy on the canvas (a lamp to light, a whistle to pull): a checkmark,
optional, no reward, no toast, no lock icon, lines hidden, and it never counts as content (tools/test_sector_book.py
leaves it out of every total and ratio). It can have any shape and size.
"""
import math
import random
import re

import quest_engine as qe

# A block-atlas sprite: no "textures/" and no ".png" (block/…, item/… or a folder a mod adds to the atlas,
# like mekanism:liquid/liquid; tools/check_guides.py checks the atlas lists it).
SPRITE = re.compile(r"[a-z0-9_.-]+:(?!textures/)[a-z0-9_.-]+(?:/[a-z0-9_.-]+)+(?<!\.png)")
ITEM_IMAGE = re.compile(r"item:[a-z0-9_.-]+:[a-z0-9_./-]+")
FLASH = "minecraft:textures/particle/flash.png"
# By precedence: a path may draw with a sprite, lettering follows a path, and so on.
KINDS = ("lettering", "path", "mosaic", "scatter", "frame", "glow", "text", "item", "sprite", "picture")
DECOR_SIZES = (0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0)
DEFAULT_ORDER = {"picture": -5, "sprite": -4, "item": -1, "path": -2, "mosaic": -3, "glow": -4, "scatter": -4,
                 "frame": -3, "text": 5, "lettering": 5}


def kind_of(art):
    if any(k in art for k in ("texture", "panel", "line", "label")) and not any(k in art for k in KINDS[:6]):
        return None   # a legacy kind of quest_engine.art_image
    return next((k for k in KINDS if k in art), None)


def handles(art):
    return kind_of(art) is not None


def picture_ref(spec, ctx=None):
    """A drawable: a texture path, a block-atlas sprite, "item:<id>" or "#rrggbb" (a tinted pixel)."""
    if spec.startswith("#"):
        qe.rgb(spec)
        return qe.PX, spec
    if spec.startswith("item:"):
        assert ITEM_IMAGE.fullmatch(spec), spec
        if ctx is not None:
            ctx["items"].add(spec[5:])
        return spec, None
    if qe.TEXTURE.fullmatch(spec):
        if ctx is not None:
            ctx["textures"].add(spec)
        return spec, None
    assert SPRITE.fullmatch(spec), f"{spec!r}: a texture path, a sprite (ns:block/…), item:<id> or #rrggbb"
    if ctx is not None:
        ctx.setdefault("sprites", set()).add(spec)
    return spec, None


def _extra(art, key, by_key, languages, default_order):
    extra = {"order": art.get("order", default_order), "position_locked": True}
    if "reveal" in art:
        assert art["reveal"] in by_key, f"{key}: reveal quest {art['reveal']}"
        extra["dependency"] = qe.stable_id("quest:" + art["reveal"])
    if "click" in art:
        kind, target = art["click"].split(":", 1)
        extra["click_action"] = "open_quest:" + qe.stable_id(f"{kind}:{target}")
    if "alpha" in art:
        assert 0 <= art["alpha"] <= 255, key
        extra["alpha"] = art["alpha"]
    if "tint" in art:
        extra["color"] = qe.rgb(art["tint"])
    return extra


def _hover(img, art, languages, key):
    if "hover" in art:
        for lang in qe.LOCALES:
            text = art["hover"][lang]
            assert text and not re.search(r"[&§{}\[\]\\]", text), f"{key}: hover"
            languages[lang][f"image.{img['id']}.title"] = text
    return img


def _pic(key, x, y, w, h, spec, extra, ctx, rotation=0.0):
    ref, color = picture_ref(spec, ctx)
    e = dict(extra)
    if color is not None:
        e["color"] = qe.rgb(color)
        e.setdefault("alpha", 255)
    if ref.startswith("item:"):
        e.pop("color", None)   # items draw with their own colours (ItemIcon ignores the tint)
    return qe.image(key, x, y, w / qe.VISUAL, h / qe.VISUAL, ref, rotation=rotation, **e)


def smooth(points, steps):
    """Catmull-Rom through the points, steps pieces per span."""
    if steps <= 1 or len(points) < 3:
        return [tuple(p) for p in points]
    pts = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for s in range(steps):
            t = s / steps
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)))
    out.append(tuple(points[-1]))
    return out


def resample(points, step):
    """Points every `step` units of arc length (the ends included)."""
    out = [tuple(points[0])]
    carry = 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        seg = math.hypot(x1 - x0, y1 - y0)
        if seg == 0:
            continue
        d = step - carry
        while d <= seg + 1e-9:
            t = d / seg
            out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
            d += step
        carry = seg - (d - step)
    if math.hypot(out[-1][0] - points[-1][0], out[-1][1] - points[-1][1]) > step * 0.25:
        out.append(tuple(points[-1]))
    return out


def art_images(chapter, i, art, palette, languages, ctx, by_key):
    """The chapter images of one art entry (a list); legacy kinds go to quest_engine.art_image."""
    kind = kind_of(art)
    if kind is None:
        return [qe.art_image(chapter, i, art, palette, languages, ctx, by_key)]
    key = f"{chapter}:art:{art.get('id', i)}"
    extra = _extra(art, key, by_key, languages, DEFAULT_ORDER[kind])
    rot = float(art.get("rotation", 0.0))
    if kind == "picture":
        img = _pic(key, art["x"], art["y"], art["w"], art["h"], art["picture"], extra, ctx, rot)
        return [_hover(img, art, languages, key)]
    if kind == "sprite":
        cells = art.get("cells") or [[art["x"], art["y"]]]
        w, h = art.get("w", art.get("cell", 1.0)), art.get("h", art.get("cell", 1.0))
        out = [_pic(f"{key}:{n}" if len(cells) > 1 else key, cx, cy, w, h, art["sprite"], extra, ctx, rot)
               for n, (cx, cy) in enumerate(cells)]
        return [_hover(img, art, languages, key) for img in out]
    if kind == "item":
        size = art.get("size", 1.0)
        img = _pic(key, art["x"], art["y"], size, size, "item:" + art["item"], extra, ctx, rot)
        return [_hover(img, art, languages, key)]
    if kind == "glow":
        r = art.get("r", 1.5)
        e = dict(extra)
        e.setdefault("alpha", 160)
        return [_pic(key, art["glow"][0], art["glow"][1], 2 * r, 2 * r, FLASH, e, ctx)]
    if kind == "path":
        return path_images(key, art, extra, ctx)
    if kind == "mosaic":
        return mosaic_images(key, art, extra, ctx)
    if kind == "scatter":
        return scatter_images(key, art, extra, ctx)
    if kind == "frame":
        return frame_images(key, art, extra, ctx)
    if kind == "text":
        return [text_image(key, art, art["x"], art["y"], art["text"], rot, extra, languages)]
    return lettering_images(key, art, extra, languages)


def path_images(key, art, extra, ctx):
    pts = [tuple(p) for p in art["path"]]
    assert len(pts) >= 2, f"{key}: a path needs two points"
    if art.get("closed"):
        pts.append(pts[0])
    pts = smooth(pts, int(art.get("smooth", 1)))
    width = art.get("width", 0.2)
    out = []
    if "dots" in art or "items" in art:
        spec = art.get("dots") or "item:" + art["items"]
        size = art.get("size", width)
        for n, (x, y) in enumerate(resample(pts, art.get("step", 1.0))):
            out.append(_pic(f"{key}:{n}", x, y, size, size, spec, extra, ctx, float(art.get("rotation", 0.0))))
        return out
    spec = art.get("texture") or art.get("sprite") or art.get("color")
    assert spec, f"{key}: a path draws a color, a texture, a sprite, dots or items"
    if "step" in art:
        pts = resample(pts, art["step"])
    overlap = art.get("overlap", 1.04 if spec.startswith("#") else 1.0)
    turn = bool(art.get("turn"))   # the texture's grain runs top to bottom (a shaft, a pipe): turn it along the path
    for n, ((x0, y0), (x1, y1)) in enumerate(zip(pts, pts[1:])):
        length = math.hypot(x1 - x0, y1 - y0)
        if length < 1e-6:
            continue
        angle = math.degrees(math.atan2(y1 - y0, x1 - x0)) + float(art.get("rotation", 0.0))
        w, h = (width, length * overlap) if turn else (length * overlap, width)
        out.append(_pic(f"{key}:{n}", (x0 + x1) / 2, (y0 + y1) / 2, w, h, spec, extra, ctx, angle + (90.0 if turn else 0.0)))
    return out


def mosaic_images(key, art, extra, ctx):
    x0, y0 = art["mosaic"]
    cell = art.get("cell", 0.5)
    legend = art["legend"]
    rows = art["rows"]
    out = []
    for r, row in enumerate(rows):
        c = 0
        while c < len(row):
            ch = row[c]
            if ch in (" ", "."):
                c += 1
                continue
            assert ch in legend, f"{key}: mosaic char {ch!r} has no legend"
            spec = legend[ch]
            run = 1
            if spec.startswith("#"):   # a run of one colour is one rectangle
                while c + run < len(row) and row[c + run] == ch:
                    run += 1
            cx = x0 + (c + run / 2) * cell
            cy = y0 + (r + 0.5) * cell
            out.append(_pic(f"{key}:{r}:{c}", cx, cy, cell * run, cell, spec, extra, ctx))
            c += run
    assert out, f"{key}: empty mosaic"
    return out


def distance_to(points, x, y):
    best = float("inf")
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        dx, dy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / ((dx * dx + dy * dy) or 1.0)))
        best = min(best, math.hypot(x - ax - t * dx, y - ay - t * dy))
    return best


def scatter_images(key, art, extra, ctx):
    rng = random.Random(art.get("seed", 1))
    x0, y0, x1, y1 = art["region"]
    lo, hi = art.get("size", [0.3, 0.6])
    alo, ahi = art.get("alphas", [extra.get("alpha", 255)] * 2)
    specs = art["scatter"] if isinstance(art["scatter"], list) else [art["scatter"]]
    avoid = [tuple(a) for a in art.get("avoid", [])]   # [x, y, r] circles to keep clear (nodes, labels)
    near = smooth([tuple(p) for p in art["near"]], 6) if "near" in art else None   # along a path: banks, edges
    band = art.get("band", [0.0, 1.0])
    out = []
    tries = 0
    while len(out) < art["count"] and tries < art["count"] * 80:
        tries += 1
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        if any(math.hypot(x - ax, y - ay) < ar for ax, ay, ar in avoid):
            continue
        if near and not band[0] <= distance_to(near, x, y) <= band[1]:
            continue
        s = rng.uniform(lo, hi)
        e = dict(extra)
        e["alpha"] = int(rng.uniform(alo, ahi))
        spec = specs[rng.randrange(len(specs))]
        rot = rng.uniform(0, 360) if art.get("spin") else 0.0
        out.append(_pic(f"{key}:{len(out)}", x, y, s, s, spec, e, ctx, rot))
    return out


def frame_images(key, art, extra, ctx):
    x0, y0, x1, y1 = art["frame"]
    color = art.get("color", "#E8DCB5")
    thick = art.get("thickness_px", 1.0)
    out = []
    if "fill" in art:
        e = dict(extra, color=qe.rgb(art["fill"]), alpha=art.get("fill_alpha", 60))
        e["order"] = extra["order"] - 1
        out.append(qe.image(f"{key}:fill", (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / qe.VISUAL, (y1 - y0) / qe.VISUAL,
                            qe.PX, **e))
    e = dict(extra, color=qe.rgb(color))
    e.setdefault("alpha", 200)
    for n, (a, b) in enumerate((((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)), ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0)))):
        line = qe.line_image(f"{key}:{n}", a[0], a[1], b[0], b[1], color, e["alpha"], thick, order=e["order"])
        for k in ("dependency",):
            if k in e:
                line[k] = e[k]
        out.append(line)
    if art.get("corners", True):
        c = 16 / qe.NODE_PX * qe.VISUAL / 2
        for n, (cx, cy) in enumerate(((x0 + c, y0 + c), (x1 - c, y0 + c), (x1 - c, y1 - c), (x0 + c, y1 - c))):
            ce = {k: v for k, v in extra.items() if k not in ("color", "alpha")}
            ce["order"] = extra["order"] + 1
            out.append(qe.image(f"{key}:corner{n}", cx, cy, 16 / qe.NODE_PX, 16 / qe.NODE_PX, qe.QUESTS_ART + "corner.png",
                                rotation=90.0 * n, **ce))
    return out


def text_image(key, art, x, y, texts, rotation, extra, languages):
    img = qe.label(key, x, y, texts, languages, scale=art.get("scale", 2), color=art.get("tint", "#E8DCB5"),
                   anchor=art.get("anchor", "center"), align=art.get("align", "middle"), font=art.get("font"),
                   bold=art.get("bold", False), order=extra["order"])
    img["rotation"] = qe.num(rotation)
    if not art.get("shadow", True):
        img["text_shadow"] = False
    for k, v in extra.items():
        if k not in ("order", "color"):
            img[k] = v
    return img


def lettering_images(key, art, extra, languages):
    """One label per letter along a path. Both languages share the positions, so the texts have the same length
    (a proper noun, or two translations padded with spaces)."""
    texts = art["lettering"]
    assert len({len(texts[lang]) for lang in qe.LOCALES}) == 1, f"{key}: lettering has one length in both languages"
    n = len(texts["en_us"])
    scale = art.get("scale", 2)
    pts = smooth([tuple(p) for p in art["path"]], int(art.get("smooth", 8)))
    advance = [max(qe.text_px(texts[lang][i]) for lang in qe.LOCALES) * scale / qe.GRID_PX for i in range(n)]
    total = sum(advance)
    # arc-length table
    lengths = [0.0]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        lengths.append(lengths[-1] + math.hypot(xb - xa, yb - ya))
    start = max(0.0, (lengths[-1] - total) / 2) if art.get("center", True) else 0.0
    out = []
    s = start
    for i in range(n):
        mid = s + advance[i] / 2
        s += advance[i]
        if all(texts[lang][i] == " " for lang in qe.LOCALES):
            continue
        j = max(1, min(len(pts) - 1, next((k for k, L in enumerate(lengths) if L >= mid), len(pts) - 1)))
        (xa, ya), (xb, yb) = pts[j - 1], pts[j]
        seg = lengths[j] - lengths[j - 1] or 1.0
        t = (mid - lengths[j - 1]) / seg
        x, y = xa + (xb - xa) * t, ya + (yb - ya) * t
        angle = math.degrees(math.atan2(yb - ya, xb - xa)) if art.get("follow", True) else 0.0
        letter = {lang: texts[lang][i] for lang in qe.LOCALES}   # a space in one language draws nothing there
        out.append(text_image(f"{key}:{i}", art, x, y, letter, angle, extra, languages))
    return out


# --------------------------------------------------------------------------------------------------
# Decor quests

def decor(q, out):
    """A toy on the canvas: optional checkmark without reward, toast, lock icon or lines, never counted."""
    tasks = qe.tasks_of(q)
    assert [qe.task_kind(t) for t in tasks] == ["checkmark"], f"{q['key']}: a decor node is one checkmark"
    size = float(q.get("size", 1.0))
    assert size in DECOR_SIZES, f"{q['key']}: decor size {size}"
    out.update(optional=True, disable_toast=True, hide_lock_icon=True, hide_dependency_lines=True,
               hide_dependent_lines=True, rewards=[])
    return out


def is_counted(q):
    """Content, as every total and ratio of the book sees it: everything but decor."""
    return q.get("role") != "decor"


# --------------------------------------------------------------------------------------------------
# References for tools/check_guides.py

def references(data):
    """{'textures', 'sprites', 'items'} that the art list and the decor nodes of a sector draw."""
    ctx = {"textures": set(), "items": set(), "sprites": set()}
    for art in data.get("art", []):
        kind = kind_of(art)
        if kind is None:
            continue
        specs = []
        if kind == "picture":
            specs.append(art["picture"])
        elif kind == "sprite":
            specs.append(art["sprite"])
        elif kind == "item":
            specs.append("item:" + art["item"])
        elif kind == "glow":
            specs.append(FLASH)
        elif kind == "path":
            for k in ("texture", "sprite", "dots"):
                if k in art:
                    specs.append(art[k])
            if "items" in art:
                specs.append("item:" + art["items"])
        elif kind == "mosaic":
            specs += list(art["legend"].values())
        elif kind == "scatter":
            specs += art["scatter"] if isinstance(art["scatter"], list) else [art["scatter"]]
        for spec in specs:
            if not spec.startswith("#"):
                picture_ref(spec, ctx)
    return ctx
