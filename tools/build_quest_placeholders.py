"""Placeholder textures for the quest book v3, until Elias draws the real ones.

Writes only files that do not exist yet, so drawn art that replaces a placeholder is never
overwritten. --check verifies that every texture the book references exists at the expected size.
The list is the art request of docs/design/quest-book-v3.md ("Arte pedido"); `placeholder` in
PLACEHOLDERS marks what is still geometric filler.

- entrelumen:textures/gui/quests/px.png is not art: a 4x4 white pixel that panels and figure lines
  tint and stretch (tools/quest_engine.py).
- Node shapes live in the ftbquests namespace (FTB Quests loads textures/shapes/<name>/ from it only:
  QuestShape): 128x128 white background, outline and shape masks with blur, like FTB's own.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "companion/src/main/resources/assets"
QUESTS = RES / "entrelumen/textures/gui/quests"
SHAPES = RES / "ftbquests/textures/shapes"
S = 128

# name -> (size, purpose); every one is a placeholder until drawn.
PLACEHOLDERS = {
    "px.png": ((4, 4), "utility: white pixel for panels and lines (not art)"),
    "tip.png": ((16, 16), "icon of pro-tip nodes (theme icon of #entrelumen_tip)"),
    "secret.png": ((16, 16), "icon for secret nodes without an item"),
    "banner_create.png": ((192, 48), "title plate behind the Create chapters' title"),
    "banner_ars.png": ((192, 48), "title plate behind the Ars Nouveau chapter's title"),
    "diagram_crushing.png": ((96, 48), "inline diagram: two crushing wheels turning inward, items falling between"),
    "diagram_train.png": ((96, 48), "inline diagram: station, signal, train on a loop"),
    "diagram_glyphs.png": ((96, 48), "inline diagram: a spell as Form + Effect + Augment slots"),
}


def polygon(points, size=S, scale=4):
    big = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(big).polygon([(x * scale, y * scale) for x, y in points], fill=255)
    return big.resize((size, size), Image.LANCZOS).point(lambda v: 255 if v >= 128 else 0)


def radial(n, r_outer, r_inner, start=-90, cx=64, cy=64):
    pts = []
    for i in range(2 * n):
        a = math.radians(start + 180 * i / n)
        r = r_outer if i % 2 == 0 else r_inner
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def curve(fn, steps=256, cx=64, cy=64):
    return [(cx + fn(2 * math.pi * i / steps) * math.cos(2 * math.pi * i / steps),
             cy + fn(2 * math.pi * i / steps) * math.sin(2 * math.pi * i / steps)) for i in range(steps)]


SHAPE_MASKS = {
    "el_sunburst": lambda: polygon(radial(12, 62, 46)),
    "el_star": lambda: polygon(radial(4, 62, 22, start=-90)),
    "el_rosette": lambda: polygon(curve(lambda t: 55 + 7 * math.cos(16 * t))),
    "el_tag": lambda: polygon([(6, 64), (34, 22), (120, 22), (120, 106), (34, 106)]),
    "el_shield": lambda: polygon([(14, 14), (114, 14), (114, 60)] + [
        (64 + 50 * math.cos(math.radians(a)), 60 + 60 * math.sin(math.radians(a))) for a in range(0, 181, 10)] + [(14, 60)]),
}


def erode(mask, px):
    return mask.filter(ImageFilter.MinFilter(px * 2 + 1))


def white(mask):
    img = Image.new("RGBA", mask.size, (255, 255, 255, 0))
    img.putalpha(mask)
    return img


def build_shape(name):
    folder = SHAPES / name
    mask = SHAPE_MASKS[name]()
    inner = erode(mask, 4)
    outline = Image.eval(Image.merge("L", [mask]), lambda v: v)
    outline = Image.composite(mask, Image.new("L", mask.size, 0), Image.eval(inner, lambda v: 255 - v))
    parts = {"shape": white(mask), "outline": white(outline), "background": white(erode(mask, 3))}
    folder.mkdir(parents=True, exist_ok=True)
    for part, img in parts.items():
        path = folder / f"{part}.png"
        if not path.exists():
            img.save(path)
        meta = path.with_name(path.name + ".mcmeta")
        if not meta.exists():
            meta.write_text(json.dumps({"texture": {"blur": True}}, indent=2) + "\n", encoding="utf-8", newline="\n")


def plate(size, border, fill):
    w, h = size
    img = Image.new("RGBA", size, fill)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=border)
    d.rectangle([1, 1, w - 2, h - 2], outline=border)
    for x in range(-h, w, 8):  # diagonal hatch: obviously a placeholder
        d.line([(x, h - 3), (x + h - 6, 3)], fill=border[:3] + (60,))
    return img


def build_placeholder(name):
    path = QUESTS / name
    if path.exists():
        return
    size = PLACEHOLDERS[name][0]
    if name == "px.png":
        img = Image.new("RGBA", size, (255, 255, 255, 255))
    elif name == "tip.png":
        img = Image.new("RGBA", size, (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.polygon([(8, 1), (14, 7), (8, 13), (2, 7)], fill=(232, 176, 74, 255), outline=(120, 80, 20, 255))
        d.rectangle([7, 4, 8, 8], fill=(255, 245, 210, 255))
        d.point([(7, 10), (8, 10)], fill=(255, 245, 210, 255))
    elif name == "secret.png":
        img = Image.new("RGBA", size, (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.polygon(radial(4, 7.5, 2.5, cx=8, cy=8), fill=(255, 216, 74, 255))
    elif name.startswith("banner_"):
        color = (200, 129, 74, 255) if "create" in name else (155, 111, 214, 255)
        img = plate(size, color, color[:3] + (40,))
    else:
        img = plate(size, (232, 220, 181, 200), (40, 40, 48, 160))
    QUESTS.mkdir(parents=True, exist_ok=True)
    img.save(path)


def check():
    failures = []
    for name, (size, _) in PLACEHOLDERS.items():
        path = QUESTS / name
        if not path.exists():
            failures.append(f"missing {path.relative_to(ROOT)}")
            continue
        with Image.open(path) as im:
            if im.size != size:
                failures.append(f"{name}: {im.size} != {size}")
    for name in SHAPE_MASKS:
        for part in ("background", "outline", "shape"):
            path = SHAPES / name / f"{part}.png"
            if not path.exists():
                failures.append(f"missing {path.relative_to(ROOT)}")
                continue
            with Image.open(path) as im:
                if im.size != (S, S):
                    failures.append(f"{name}/{part}: {im.size}")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        for name in SHAPE_MASKS:
            build_shape(name)
        for name in PLACEHOLDERS:
            build_placeholder(name)
    failures = check()
    if failures:
        raise SystemExit("FAIL: " + "; ".join(failures))
    print(f"PASS: {len(PLACEHOLDERS)} quest textures and {len(SHAPE_MASKS)} node shapes present")


if __name__ == "__main__":
    main()
