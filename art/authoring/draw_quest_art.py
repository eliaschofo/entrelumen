"""Decorations for the quest book: images placed on chapter canvases around (not inside) the nodes.

References inspected on 2026-09-25 (research/quests, screenshots of the real books, read only):
ATM10 8.1 "Chapter 3: The ATM Star" (large title lettering, item and block renders around the
nodes) and FTB Evolution 1.43.1 "Create" (framed panels grouping nodes, a large round emblem per
chapter, machine renders as scenery). ENTRELUMEN keeps the idea, not the art: language-neutral
Roman numerals instead of lettering, copper and calcite frames, the Sun of Heliodor.

Everything is mirror-symmetric (pack rule) except the numerals, since IV and VI are each other's mirror, and drawn at native resolution, no smoothing. Writes
companion/src/main/resources/assets/entrelumen/textures/gui/quests/*.png and a preview sheet.

    python art/authoring/draw_quest_art.py
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

OUT = os.path.join(HERE, '..', '..', 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures', 'gui', 'quests')
INK = '#1f1d22'
GOLD = ['#3a2a0e', '#6e5220', '#a8862f', '#d9b95a', '#f1dc98', '#fff6dc']
CU = R['copper']
CALC = ['#8f8a86', '#b9b4ae', '#d9d5cf', '#ece9e4', '#faf8f4']
TEAL = R['teal']


def rgba(c, a=255):
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def canvas(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), rgba(c))


def outline(im, colour=INK):
    """One-pixel dark outline around every opaque shape (4-neighbourhood)."""
    src = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            if src.getpixel((x, y))[3]:
                continue
            if any(0 <= x + dx < im.width and 0 <= y + dy < im.height and src.getpixel((x + dx, y + dy))[3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), rgba(colour))
    return im


def mirrored(im):
    """Force exact mirror symmetry across the vertical axis (left half wins)."""
    w = im.width
    for y in range(im.height):
        for x in range(w // 2):
            im.putpixel((w - 1 - x, y), im.getpixel((x, y)))
    return im


# ---------------- the Sun of Heliodor ----------------
def sun(size=128):
    im = canvas(size, size)
    c = (size - 1) / 2
    inner = size * 0.27
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            th = math.atan2(dy, dx) + math.pi / 2          # ray 0 points straight up
            step = 2 * math.pi / 16
            k = round(th / step)
            off = abs(th - k * step)
            long_ray = k % 2 == 0
            reach = size * (0.495 if long_ray else 0.40)
            if r <= size * 0.23:
                shade = GOLD[5] if r < size * 0.08 else GOLD[4] if r < size * 0.15 else GOLD[3]
                if size * 0.19 < r:
                    shade = CU[3] if (int((th / (2 * math.pi)) * 48) % 2) else GOLD[2]
                put(im, x, y, shade)
            elif r <= inner:
                put(im, x, y, TEAL[2] if (int((th / (2 * math.pi)) * 32) % 2) else TEAL[3])
            elif r <= reach:
                t = (r - inner) / (reach - inner)
                half = (0.13 if long_ray else 0.10) * (1 - t) + 0.012
                if off <= half:
                    edge = off > half * 0.55
                    put(im, x, y, (GOLD[3] if edge else GOLD[4]) if t < 0.45 else (GOLD[2] if edge else GOLD[3]))
    return mirrored(outline(im))


# ---------------- Roman numerals ----------------
GLYPH = {  # serif capitals, two-pixel strokes, 13 rows
    'I': ['#######', '..###..', '..###..', '..###..', '..###..', '..###..', '..###..', '..###..', '..###..',
          '..###..', '..###..', '..###..', '#######'],
    'V': ['####...####', '.##.....##.', '.##.....##.', '..##...##..', '..##...##..', '..##...##..',
          '...##.##...', '...##.##...', '...##.##...', '....###....', '....###....', '.....#.....', '.....#.....'],
}


def numeral(text, scale=3):
    glyphs = [GLYPH[ch] for ch in text]
    w = sum(len(g[0]) for g in glyphs) + (len(glyphs) - 1)
    im = canvas((w + 2) * scale, (13 + 2) * scale)
    ox = 1
    for g in glyphs:
        for gy, row in enumerate(g):
            for gx, ch in enumerate(row):
                if ch != '#':
                    continue
                for sy in range(scale):
                    for sx in range(scale):
                        yy = (gy + 1) * scale + sy
                        shade = GOLD[4] if yy < im.height * 0.4 else GOLD[3] if yy < im.height * 0.7 else GOLD[2]
                        if sy == 0 and gy == 0:
                            shade = GOLD[5]
                        put(im, (ox + gx) * scale + sx, yy, shade)
        ox += len(g[0]) + 1
    return outline(outline(im), '#2a1a08') if False else outline(im)


# ---------------- frames, medallion, divider, corner ----------------
def frame(w, h, b=4):
    """Copper band with a calcite fillet and rivets, transparent inside; corners carry a sun stud."""
    im = canvas(w, h)
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            if d == 0:
                put(im, x, y, INK)
            elif d < b - 1:
                put(im, x, y, CU[4] if d == 1 else CU[3])
            elif d == b - 1:
                put(im, x, y, CALC[3])
    for (cx, cy) in ((b, b), (w - 1 - b, b), (b, h - 1 - b), (w - 1 - b, h - 1 - b)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if abs(dx) + abs(dy) <= 2:
                    put(im, cx + dx, cy + dy, GOLD[4] if abs(dx) + abs(dy) <= 1 else GOLD[2])
    for x in range(b + 8, w - b - 8, 8):
        put(im, x, 1, GOLD[3])
        put(im, x, h - 2, GOLD[3])
    for y in range(b + 8, h - b - 8, 8):
        put(im, 1, y, GOLD[3])
        put(im, w - 2, y, GOLD[3])
    return mirrored(im)


def medallion(size=64):
    """A round badge to set a large item or block texture on: parchment disc in a copper ring."""
    im = canvas(size, size)
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c)
            th = math.atan2(y - c, x - c)
            if r <= size * 0.5 - 0.5:
                if r > size * 0.5 - 2:
                    put(im, x, y, INK)
                elif r > size * 0.5 - 6:
                    tick = int((th / (2 * math.pi)) * 24) % 2
                    put(im, x, y, CU[4] if tick else CU[3])
                elif r > size * 0.5 - 7:
                    put(im, x, y, GOLD[3])
                else:
                    t = r / (size * 0.5 - 7)
                    put(im, x, y, R['parch'][5] if t < 0.55 else R['parch'][4] if t < 0.85 else R['parch'][3])
    return mirrored(im)


def divider(w=96, h=9):
    im = canvas(w, h)
    mid = h // 2
    c = (w - 1) / 2
    for x in range(w):
        d = abs(x - c)
        if d < 5:
            continue
        put(im, x, mid, CU[4] if (x % 6) else GOLD[3])
        if d < w / 2 - 6:
            put(im, x, mid - 1, CU[2])
            put(im, x, mid + 1, CU[2])
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            if abs(dx) + abs(dy) <= 4:
                put(im, round(c + dx - 0.5) if dx < 0 else round(c + dx + 0.5) - 1, mid + dy,
                    GOLD[4] if abs(dx) + abs(dy) <= 2 else GOLD[2])
    return mirrored(outline(im))


def corner(size=16):
    """Corner ornament (top-left; rotate it for the others): a quarter sun with a copper scroll."""
    im = canvas(size, size)
    for y in range(size):
        for x in range(size):
            if x < 2 or y < 2:
                if x + y < size:
                    put(im, x, y, CU[4] if x < 1 or y < 1 else CU[3])
            r = math.hypot(x - 1, y - 1)
            if 4 <= r <= 6:
                put(im, x, y, GOLD[3])
            if r < 3.2:
                put(im, x, y, GOLD[4])
    return outline(im)


# ---------------- act emblems (32x32, symmetric) ----------------
def emblem(kind, size=32):
    im = canvas(size, size)
    c = (size - 1) / 2

    def disc(cx, cy, r, colour):
        for y in range(size):
            for x in range(size):
                if math.hypot(x - cx, y - cy) <= r:
                    put(im, x, y, colour)

    def rect(x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                put(im, x, y, colour)

    if kind == 1:        # I · Despertar: a lantern with its light
        rect(12, 6, 19, 7, CU[3]); rect(15, 3, 16, 5, CU[2])
        rect(11, 8, 20, 22, CU[3]); rect(13, 10, 18, 20, GOLD[4]); rect(14, 12, 17, 18, GOLD[5])
        rect(10, 23, 21, 25, CU[2])
    elif kind == 2:      # II · a gear with a leaf: the workshop that breathes
        for y in range(size):
            for x in range(size):
                r = math.hypot(x - c, y - c)
                th = math.atan2(y - c, x - c)
                tooth = (int((th / (2 * math.pi)) * 16) % 2) == 0
                if r <= 11 + (2 if tooth else 0) and r >= 5:
                    put(im, x, y, R['iron'][3] if r > 9 else R['iron'][4])
        rect(15, 9, 16, 22, R['leaf'][2])
        for d in range(1, 5):
            rect(15 - d, 11 + d, 16 + d, 12 + d, R['leaf'][4] if d < 3 else R['leaf'][3])
    elif kind == 3:      # III · the altar crystal
        for y in range(4, 28):
            half = min(y - 4, 27 - y) // 2 + 1
            for x in range(16 - half, 16 + half):
                put(im, x, y, R['violet'][4] if x < 16 else R['violet'][3])
        rect(9, 26, 22, 28, CALC[2])
    elif kind == 4:      # IV · a compass rose
        disc(c, c, 12, R['parch'][4]); disc(c, c, 10, R['parch'][5])
        for y in range(4, 28):
            half = max(0, 3 - abs(y - 15.5) // 4)
            for x in range(int(16 - half), int(16 + half)):
                put(im, x, y, CU[3] if y < 16 else R['iron'][3])
        rect(4, 15, 27, 16, R['iron'][2])
    elif kind == 5:      # V · the Ark: a hull that carries the sun as its sail
        for x in range(4, 28):
            d = abs(x - 15.5)
            bottom = 25 - int(d * d / 36)
            rect(x, 19, x, bottom, R['wood'][3] if (x // 2) % 2 else R['wood'][2])
            put(im, x, 19, R['wood'][4])
        rect(15, 5, 16, 18, CU[2])
        disc(15.5, 11, 5.6, GOLD[3]); disc(15.5, 11, 3.8, GOLD[4]); disc(15.5, 11, 1.8, GOLD[5])
    else:                # VI · Solsticio: the domed tower under the sun
        rect(10, 16, 21, 27, CALC[3]); rect(14, 20, 17, 27, GOLD[3])
        for y in range(9, 16):
            half = int(math.sqrt(max(0, 36 - (15 - y) ** 2)))
            rect(16 - half, y, 15 + half, y, CU[3] if y > 11 else GOLD[3])
        rect(15, 3, 16, 8, GOLD[4])
        disc(15.5, 3, 1.6, GOLD[5])
    return mirrored(outline(im))


def main():
    os.makedirs(OUT, exist_ok=True)
    art = {'sun_heliodor': sun(128), 'medallion': medallion(64), 'divider': divider(), 'corner': corner(),
           'frame_1x1': frame(64, 64), 'frame_2x1': frame(128, 64), 'frame_3x1': frame(192, 64), 'frame_3x2': frame(192, 128)}
    for n, text in enumerate(['I', 'II', 'III', 'IV', 'V', 'VI'], 1):
        art['numeral_%d' % n] = numeral(text)
        art['act_%d' % n] = emblem(n)
    for name, im in art.items():
        for y in range(im.height):                           # every piece is mirror-symmetric
            for x in range(im.width):
                if name != 'corner' and not name.startswith('numeral'):   # IV and VI mirror each other
                    assert im.getpixel((x, y)) == im.getpixel((im.width - 1 - x, y)), name
        im.save(os.path.join(OUT, name + '.png'))
    # preview on a dark quest-book background
    sheet = Image.new('RGBA', (900, 520), (24, 26, 34, 255))
    x, y = 12, 12
    for name in ['sun_heliodor', 'medallion', 'frame_3x2', 'frame_2x1', 'frame_1x1']:
        im = art[name].resize((art[name].width * 2 if art[name].width <= 64 else art[name].width,
                               art[name].height * 2 if art[name].width <= 64 else art[name].height), Image.NEAREST)
        sheet.alpha_composite(im, (x, y))
        x += im.width + 16
    x, y = 12, 290
    for n in range(1, 7):
        sheet.alpha_composite(art['act_%d' % n].resize((96, 96), Image.NEAREST), (x, y))
        sheet.alpha_composite(art['numeral_%d' % n].resize((art['numeral_%d' % n].width * 2, art['numeral_%d' % n].height * 2), Image.NEAREST), (x + 8, y + 110))
        x += 140
    sheet.alpha_composite(art['divider'].resize((288, 27), Image.NEAREST), (560, 180))
    sheet.alpha_composite(art['corner'].resize((64, 64), Image.NEAREST), (860 - 40, 180 + 40))
    sheet.save(os.path.join(os.environ.get('TEMP', '.'), 'quest_art_preview.png'))
    print('wrote', len(art), 'images to', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
