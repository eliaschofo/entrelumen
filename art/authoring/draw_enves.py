"""Envés items on the native 16x16 grid: the sour light shard (esquirla de luz agria), the dungeon's currency.

References (design hook, vanilla 1.21.1 item textures inspected at 8x):
- echo_shard and amethyst_shard: a shard lies on the diagonal, lower-left to upper-right, about five
  pixels thick, with a dark outline;
- nether_star: light that comes from the core instead of from one side.

The sour light is the luminosity that went bad after the failed fusion (docs/design/story-synopsis.md), so the
shard glows from its axis: a pale, almost white tip, a sour yellow-green body and, past a fracture, a base
where the light dies into dark olive. It is mirror-symmetric about its long axis (x + y = 15), like the pack's
other art, which is why the light sits on the axis and not on the upper-left side. Writes
art/grids/item/sour_light_shard.txt and a preview.

    python art/authoring/draw_enves.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

GRIDS = os.path.join(HERE, '..', 'grids', 'item')
INK = R['ink'][1]
TIP = ['#6b6a2a', '#c9c65e', '#f3f0a8', '#fffbe2']       # edge, body, glow, core
BODY = ['#56601f', '#9fa73a', '#d4d65c', '#eef08c']
ROT = ['#262417', '#44441f', '#66652b', '#8a8838']           # the light dies towards the base
CRACK = '#2e2a14'


def half_width(s):
    """Half-width, in steps across the axis, at position s along it (s = x - y, -11 at the base, 11 at the tip)."""
    if s > 2:
        return round(3 * (11 - s) / 9)
    return 3


def base_cut(p):
    """The broken base, the same on both sides of the axis: a jagged break."""
    return {0: -9, 1: -10, 2: -9, 3: -8}.get(abs(p), 99)


def sour_light_shard():
    g = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            s, p = x - y, x + y - 15
            w = half_width(s)
            if abs(p) > w or s < base_cut(p) or s > 11:
                continue
            ramp = TIP if s >= 4 else (BODY if s >= -4 else ROT)
            depth = w - abs(p)                              # 0 on the edge, larger towards the axis
            c = ramp[0] if depth == 0 else ramp[1] if depth == 1 else ramp[2] if abs(p) >= 1 else ramp[3]
            if s == -3 and abs(p) < w:                      # the fracture across the shard
                c = CRACK
            g[y][x] = c
    occ = {(x, y) for y in range(16) for x in range(16) if g[y][x]}
    for y in range(16):
        for x in range(16):
            if (x, y) not in occ and any((x + a, y + b) in occ for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g[y][x] = INK
    return g


def symmetric_about_axis(g):
    """Mirror across the shard's long axis x + y = 15: (x, y) -> (15 - y, 15 - x)."""
    return all(g[y][x] == g[15 - x][15 - y] for y in range(16) for x in range(16))


def write(name, g):
    colours = sorted({c for row in g for c in row if c})
    letters = {c: chr(ord('A') + i) for i, c in enumerate(colours)}
    lines = [f'{letters[c]} {c}' for c in colours] + ['']
    lines += [''.join(letters[c] if c else '.' for c in row) for row in g]
    with open(os.path.join(GRIDS, name + '.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


def preview(g, path):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if g[y][x]:
                im.putpixel((x, y), tuple(int(g[y][x][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
    big = im.resize((256, 256), Image.NEAREST)
    slot = Image.new('RGBA', (18 * 4, 18 * 4), (139, 139, 139, 255))
    slot.paste(im.resize((64, 64), Image.NEAREST), (4, 4), im.resize((64, 64), Image.NEAREST))
    sheet = Image.new('RGBA', (256 + 16 + 72 + 16 + 18, 256), (58, 58, 66, 255))
    sheet.paste(big, (0, 0), big)
    sheet.paste(slot, (272, 0))
    sheet.paste(im, (272 + 72 + 16, 0), im)
    sheet.save(path)


if __name__ == '__main__':
    shard = sour_light_shard()
    assert symmetric_about_axis(shard), 'the shard must be symmetric about its long axis'
    write('sour_light_shard', shard)
    out = os.environ.get('ART_PREVIEW', os.path.join(os.environ.get('TEMP', '.'), 'sour_light_shard.png'))
    preview(shard, out)
    print('ok', out)
