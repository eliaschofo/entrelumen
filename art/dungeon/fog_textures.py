"""The Envés fog map's pre-baked textures: ink fog, the soft blur of a glimpsed room, and icons.

References inspected (26/9): vanilla `assets/minecraft/textures/map/map_background.png` (64x64,
tan parchment with a torn darker edge; the minimap draws on it as is) and our Atlas book
`assets/entrelumen/textures/gui/atlas_book.png` (cream pages #F1E3C1, ink #382F25, copper #8B4C2D).
The icons follow drlg.py's review maps (triangle start, spiral stairs, hexagon seal, chest vault,
sun shrine, red skull guard, gold ring portal) on an 8x8 grid, like vanilla map decorations.

    python art/dungeon/fog_textures.py      # writes into the companion's assets
"""
import math
import os
import random

from PIL import Image, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                   'companion/src/main/resources/assets/entrelumen/textures/gui/enves')
INK = (56, 47, 37)
COPPER = (139, 76, 45)
RED = (140, 40, 30)
TEAL = (30, 90, 90)
GOLD = (200, 150, 40)
LIT = (120, 220, 210)


def fog():
    """32x32, tileable: soft ink-wash clouds, half transparent (the unknown)."""
    size = 32
    rng = random.Random(2609)
    im = Image.new('L', (size * 3, size * 3), 0)
    px = im.load()
    for _ in range(90):
        cx, cy, r = rng.uniform(0, size), rng.uniform(0, size), rng.uniform(3, 8)
        for dx in (-size, 0, size):
            for dy in (-size, 0, size):
                x0, y0 = cx + size + dx, cy + size + dy
                for x in range(int(x0 - r), int(x0 + r) + 1):
                    for y in range(int(y0 - r), int(y0 + r) + 1):
                        if 0 <= x < size * 3 and 0 <= y < size * 3 and math.hypot(x - x0, y - y0) <= r:
                            px[x, y] = min(255, px[x, y] + 26)
    im = im.filter(ImageFilter.GaussianBlur(2.5)).crop((size, size, size * 2, size * 2))
    out = Image.new('RGBA', (size, size))
    for x in range(size):
        for y in range(size):
            v = im.getpixel((x, y))
            a = 70 + v * 100 // 255
            out.putpixel((x, y), (118, 98, 74, min(175, a)))
    return out


def glimpse():
    """16x16 white blob with a soft edge; tinted parchment when drawn (a room seen through a door)."""
    size = 16
    out = Image.new('RGBA', (size, size))
    for x in range(size):
        for y in range(size):
            d = max(abs(x + 0.5 - size / 2), abs(y + 0.5 - size / 2)) / (size / 2)
            a = max(0.0, min(1.0, (1.0 - d) / 0.55))
            out.putpixel((x, y), (255, 255, 255, int(255 * a * a)))
    return out


def icons():
    """64x8: start, stairs, seal, lit seal, vault, shrine, guard, portal."""
    rows = [
        # start: a downward triangle (where you came in)
        ["........", ".######.", ".#....#.", "..#..#..", "..#..#..", "...##...", "........", "........"],
        # stairs: a spiral
        ["..####..", ".#....#.", "#..##..#", "#.#..#.#", "#.#.##.#", "#..#...#", ".#......", "..######"],
        # seal: a hexagon with a dot
        ["..####..", ".#....#.", "#......#", "#..##..#", "#..##..#", "#......#", ".#....#.", "..####.."],
        # lit seal: filled
        ["..####..", ".######.", "########", "########", "########", "########", ".######.", "..####.."],
        # vault: a chest
        ["........", ".######.", "#......#", "########", "#..##..#", "#......#", "########", "........"],
        # shrine: a sun
        ["#..#..#.", ".#.#.#..", "..###...", "####.###", "..###...", ".#.#.#..", "#..#..#.", "........"],
        # guard: a skull
        ["..####..", ".######.", "##.##.##", "##.##.##", ".######.", "..#..#..", "..####..", "........"],
        # portal: a ring
        ["..####..", ".#....#.", "#......#", "#......#", "#......#", "#......#", ".#....#.", "..####.."],
    ]
    colours = [INK, INK, TEAL, LIT, COPPER, COPPER, RED, GOLD]
    out = Image.new('RGBA', (64, 8))
    for i, (rows_i, colour) in enumerate(zip(rows, colours)):
        for y, row in enumerate(rows_i):
            for x, ch in enumerate(row):
                if ch == '#':
                    out.putpixel((i * 8 + x, y), colour + (255,))
    return out


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    fog().save(os.path.join(OUT, 'fog.png'))
    glimpse().save(os.path.join(OUT, 'glimpse.png'))
    icons().save(os.path.join(OUT, 'icons.png'))
    print('ok', os.path.normpath(OUT))
