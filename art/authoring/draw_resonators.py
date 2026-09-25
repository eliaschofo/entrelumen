"""The six tiers of the Ultimine curio (a vein resonator made by Terra), on the 16x16 item grid.

A mirror-symmetric tuning fork: two prongs joined in a U, a stem, a collar and a faceted gem at
the base that sets the resonance, all inside the outline. Each tier climbs in metal (copper, iron, brass, teal, dark alloy, luminous)
and gem, so the six read as one family at inventory size. Writes art/grids/item/vein_resonator_<n>.txt,
the grids of the items entrelumen:vein_resonator_1..6 that art/build_art.py registers.

    python art/authoring/draw_resonators.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

GRIDS = os.path.join(HERE, '..', 'grids', 'item')
INK = '#1f1d22'
GOLD = ['#3a2a0e', '#6e5220', '#a8862f', '#d9b95a', '#f1dc98', '#fff6dc', '#ffffff']
TIERS = [  # (metal ramp: dark..light, gem ramp: dark..light)
    ([R['copper'][1], R['copper'][3], R['copper'][5]], [R['straw'][1], R['straw'][2], R['straw'][3]]),
    ([R['iron'][1], R['iron'][3], R['iron'][5]], [R['sky'][1], R['sky'][2], R['sky'][4]]),
    ([R['brass'][1], R['brass'][3], R['brass'][5]], [R['leaf'][2], R['leaf'][4], R['leaf'][5]]),
    ([R['teal'][1], R['teal'][3], R['teal'][5]], [R['violet'][2], R['violet'][4], R['violet'][5]]),
    (['#241f2b', '#453d52', '#6f6680'], [R['crimson'][2], R['crimson'][4], R['crimson'][5]]),
    ([GOLD[2], GOLD[4], GOLD[6]], [R['teal'][3], R['teal'][4], '#ffffff']),
]


def fork(dx, y, metal, gem):
    dark, mid, light = metal
    # prongs: two bars at dx 2.5..3.5, rows 1..5, with an inner highlight
    if 1 <= y <= 5 and 2.5 <= dx <= 3.5:
        return light if dx == 2.5 and y < 4 else mid
    # the U joining the prongs
    if y == 6 and dx <= 3.5:
        return mid if dx > 0.5 else light
    if y == 7 and dx <= 2.5:
        return dark if dx > 1.5 else mid
    # stem and the collar that holds the gem
    if 8 <= y <= 10 and dx <= 0.5:
        return dark if y == 10 else mid
    if y == 11 and dx <= 1.5:
        return light if dx == 0.5 else mid
    # the gem at the base, whole and outlined
    if y == 12 and dx <= 1.5:
        return gem[2] if dx == 0.5 else gem[1]
    if y == 13 and dx <= 1.5:
        return gem[1] if dx == 0.5 else gem[0]
    if y == 14 and dx <= 0.5:
        return gem[0]
    return None


def grid(tier):
    metal, gem = TIERS[tier]
    g = [[fork(abs(x - 7.5), y, metal, gem) for x in range(16)] for y in range(16)]
    out = [row[:] for row in g]
    for y in range(16):
        for x in range(16):
            if g[y][x] is None and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and g[y + dy][x + dx]
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = INK
    return out


def lum(c):
    return 0.299 * int(c[1:3], 16) + 0.587 * int(c[3:5], 16) + 0.114 * int(c[5:7], 16)


def write(name, g):
    cols = sorted({c for row in g for c in row if c}, key=lum)
    keys = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    lines = ['%s %s' % (keys[i], c) for i, c in enumerate(cols)] + ['']
    lines += [''.join(keys[cols.index(c)] if c else '.' for c in row) for row in g]
    with open(os.path.join(GRIDS, name + '.txt'), 'w', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    sheet = Image.new('RGBA', (6 * 144 + 16, 200), (139, 139, 139, 255))
    for t in range(6):
        g = grid(t)
        assert all(g[y][x] == g[y][15 - x] for y in range(16) for x in range(16))
        write('vein_resonator_%d' % (t + 1), g)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if g[y][x]:
                    im.putpixel((x, y), tuple(int(g[y][x][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (16 + t * 144, 16))
        sheet.alpha_composite(im, (16 + t * 144 + 56, 160))
    sheet.save(os.path.join(os.environ.get('TEMP', '.'), 'resonators_preview.png'))
    print('ok')
