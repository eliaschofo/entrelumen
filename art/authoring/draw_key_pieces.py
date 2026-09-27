"""The ten key pieces of the Heliodor ruins (docs/design/heliodor-ruins.md), on the native 16x16 grid.

References (design hook, vanilla 1.21.1 item textures inspected):
- fire_charge and coal for the ember;
- map and filled_map for the chart and the blueprint's paper;
- spyglass for the eyepiece, diagonal like vanilla;
- trial_key for the Sun Key, diagonal;
- the flower pot and the brewing stand's bowl for the crucible;
- the heart of the sea's glow for the flame.

Tools, keys, scrolls and spyglasses lie diagonal like vanilla's. The rest are mirror-symmetric by nature,
and the script asserts it. Writes art/grids/item/<id>.txt (the grids art/build_art.py registers once
the items exist on main) and a preview.

    python art/authoring/draw_key_pieces.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

GRIDS = os.path.join(HERE, '..', 'grids', 'item')
INK = '#1f1d22'
GOLD = ['#6e5220', '#a8862f', '#d9b95a', '#f1dc98', '#fff6dc']


class G:
    def __init__(self):
        self.g = [[None] * 16 for _ in range(16)]

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def disc(self, cx, cy, r, c):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            self.px(round(x0 + (x1 - x0) * i / max(n, 1)), round(y0 + (y1 - y0) * i / max(n, 1)), c)

    def mirror(self):
        for y in range(16):
            for x in range(8):
                self.g[y][15 - x] = self.g[y][x]
        return self

    def outline(self, c=INK):
        occ = {(x, y) for y in range(16) for x in range(16) if self.g[y][x]}
        for y in range(16):
            for x in range(16):
                if (x, y) not in occ and any((x + a, y + b) in occ for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.g[y][x] = c
        return self


def signal_ember():
    """A live coal in a small copper brazier on three legs, cracks glowing, two sparks lifting off."""
    g = G()
    g.rect(2, 9, 13, 9, R['copper'][4])                 # the brazier's rim
    g.rect(3, 10, 12, 10, R['copper'][3])
    g.rect(4, 11, 11, 11, R['copper'][2])
    g.rect(4, 12, 4, 14, R['copper'][2]); g.rect(11, 12, 11, 14, R['copper'][2]); g.rect(7, 12, 8, 13, R['copper'][1])
    for y, (x0, x1) in ((4, (6, 9)), (5, (5, 10)), (6, (4, 11)), (7, (4, 11)), (8, (3, 12))):   # the coal
        g.rect(x0, y, x1, y, R['ink'][2])
    for (x, y, c) in ((6, 5, R['crimson'][3]), (5, 7, R['crimson'][3]), (7, 6, R['straw'][2]), (6, 7, R['straw'][3]),
                      (7, 8, R['crimson'][4]), (7, 4, R['crimson'][3]), (5, 8, R['straw'][2]), (6, 6, R['crimson'][4])):
        g.px(x, y, c)
    g.px(5, 2, R['straw'][3]); g.px(6, 1, GOLD[4])
    return g.mirror().outline()


def terra_blueprint():
    """A blueprint scroll, diagonal, tied with copper wire."""
    g = G()
    for i in range(10):                       # the rolled sheet, lower-left to upper-right
        x, y = 3 + i, 12 - i
        for k in (-1, 0, 1, 2):
            g.px(x + k, y + k, R['sky'][1] if k in (-1, 2) else R['sky'][2])
        if i % 3 == 1:
            g.px(x, y, R['sky'][4]); g.px(x + 1, y + 1, R['sky'][4])
    g.disc(3.5, 13.5, 2.0, R['sky'][1]); g.disc(3.5, 13.5, 1.0, R['sky'][3])        # the curled ends
    g.disc(13.2, 3.2, 2.0, R['sky'][1]); g.disc(13.2, 3.2, 1.0, R['sky'][3])
    g.line(7, 6, 10, 9, R['copper'][4]); g.px(6, 5, R['copper'][3]); g.px(11, 10, R['copper'][3])
    return g.outline()


def route_seal():
    """A copper seal struck with the crossroads of Heliodor's routes."""
    g = G()
    g.disc(7.5, 7.5, 6.6, R['copper'][2])
    g.disc(7.5, 7.5, 5.6, R['copper'][4])
    g.disc(7.5, 7.5, 4.4, R['copper'][3])
    g.rect(7, 3, 8, 12, GOLD[2]); g.rect(3, 7, 12, 8, GOLD[2])
    g.rect(6, 6, 9, 9, GOLD[3]); g.rect(7, 7, 8, 8, GOLD[4])
    g.px(4, 4, R['copper'][5]); g.px(5, 3, R['copper'][5])
    return g.mirror().outline()


def mother_seed():
    """A great seed, split open, with a glowing two-leaf sprout."""
    g = G()
    g.disc(7.5, 10, 4.6, R['wood'][2])
    g.disc(7.5, 10.4, 3.6, R['wood'][3])
    g.disc(7.5, 11, 2.2, R['wood'][4])
    g.rect(7, 4, 8, 8, R['leaf'][3])
    g.rect(4, 3, 6, 4, R['leaf'][4]); g.px(3, 2, R['leaf'][5]); g.px(5, 5, R['leaf'][3])
    g.px(7, 2, R['leaf'][5])
    return g.mirror().outline()


def heliodor_crucible():
    """A small crucible bowl brimming with molten light."""
    g = G()
    for y in range(6, 14):
        half = 6 - max(0, y - 9)
        g.rect(8 - half, y, 7 + half, y, R['iron'][2] if y > 11 else R['iron'][3])
    g.rect(3, 6, 12, 7, R['iron'][4])
    g.rect(4, 6, 11, 6, GOLD[3]); g.rect(5, 5, 10, 5, GOLD[4]); g.rect(6, 4, 9, 4, GOLD[3])
    g.rect(1, 7, 2, 8, R['iron'][3])
    g.rect(5, 14, 10, 14, R['iron'][1])
    return g.mirror().outline()


def voices_eyepiece():
    """A brass eyepiece, diagonal like vanilla's spyglass, a teal lens at the wide end."""
    g = G()
    for i in range(9):
        x, y = 3 + i, 12 - i
        w = 1 if i < 4 else 2
        for k in range(-w, w + 1):
            g.px(x + k, y, R['brass'][3] if k <= 0 else R['brass'][2])
    g.disc(12.5, 3.5, 2.4, R['brass'][2]); g.disc(12.5, 3.5, 1.5, R['teal'][3]); g.px(12, 3, R['teal'][5])
    g.rect(2, 12, 3, 13, R['brass'][1])
    g.line(6, 10, 7, 9, R['brass'][4]); g.line(9, 7, 10, 6, R['brass'][4])
    return g.outline()


def forest_testimony():
    """A tablet of bark with carved lines and a tuft of moss."""
    g = G()
    g.rect(3, 2, 12, 14, R['wood'][2])
    g.rect(4, 3, 11, 13, R['wood'][3])
    for y in (5, 7, 9, 11):
        g.rect(5, y, 10, y, R['wood'][1])
    g.px(5, 5, R['wood'][4]); g.px(10, 9, R['wood'][4])
    g.rect(3, 13, 6, 14, R['leaf'][3]); g.px(4, 12, R['leaf'][4])
    return g.mirror().outline()


def sun_key():
    """A key whose bow is a small sun, diagonal like vanilla's trial key."""
    g = G()
    g.line(4, 11, 10, 5, GOLD[2]); g.line(5, 11, 11, 5, GOLD[1])
    g.px(3, 12, GOLD[2]); g.px(4, 13, GOLD[1]); g.px(2, 11, GOLD[2]); g.px(5, 12, GOLD[2])     # the bit
    g.disc(11.5, 4.5, 3.0, GOLD[2]); g.disc(11.5, 4.5, 2.0, GOLD[3]); g.disc(11.5, 4.5, 1.0, GOLD[4])
    for (x, y) in ((11, 0), (15, 4), (11, 8), (8, 4), (14, 1), (14, 7), (9, 1)):
        g.px(x, y, GOLD[3])
    return g.outline()


def sacred_flame():
    """A white-gold flame, pointed and licking, in a calcite lamp."""
    g = G()
    rows = {1: (7, 7), 2: (7, 7), 3: (6, 7), 4: (6, 7), 5: (5, 7), 6: (5, 7), 7: (4, 7), 8: (4, 7), 9: (5, 7)}
    for y, (x0, x1) in rows.items():
        g.rect(x0, y, x1, y, GOLD[2])
    for y in range(3, 10):
        g.rect(max(6, rows[y][0] + 1), y, 7, y, GOLD[3])
    for y in range(6, 10):
        g.rect(7, y, 7, y, GOLD[4])
    g.px(7, 8, '#ffffff'); g.px(6, 8, GOLD[4])
    g.px(4, 4, GOLD[2]); g.px(5, 3, GOLD[3])                 # a lick of flame off the side
    g.rect(4, 10, 11, 10, '#ece9e4'); g.rect(3, 11, 12, 11, '#d9d5cf'); g.rect(5, 12, 10, 12, '#b9b4ae')
    g.rect(6, 13, 9, 13, '#8f8a86')
    return g.mirror().outline()


def star_chart():
    """A night-blue sheet with a constellation and a gold star, a sun at the corner, like a vanilla map."""
    g = G()
    g.rect(2, 2, 13, 13, R['parch'][3])
    g.rect(3, 3, 12, 12, R['sky'][0])
    for (x, y) in ((5, 5), (10, 5), (7, 8), (5, 10), (10, 10)):
        g.px(x, y, GOLD[4])
    g.line(5, 5, 7, 8, R['sky'][2]); g.line(10, 5, 7, 8, R['sky'][2])
    g.line(7, 8, 5, 10, R['sky'][2]); g.line(7, 8, 10, 10, R['sky'][2])
    for (x, y) in ((5, 5), (10, 5), (7, 8), (5, 10), (10, 10)):
        g.px(x, y, GOLD[4])
    g.px(7, 8, '#ffffff')
    return g.mirror().outline()


PIECES = {'signal_ember': signal_ember, 'terra_blueprint': terra_blueprint, 'route_seal': route_seal,
          'mother_seed': mother_seed, 'heliodor_crucible': heliodor_crucible, 'voices_eyepiece': voices_eyepiece,
          'forest_testimony': forest_testimony, 'sun_key': sun_key, 'sacred_flame': sacred_flame, 'star_chart': star_chart}
DIAGONAL = {'terra_blueprint', 'voices_eyepiece', 'sun_key'}


def lum(c):
    return 0.299 * int(c[1:3], 16) + 0.587 * int(c[3:5], 16) + 0.114 * int(c[5:7], 16)


def write(name, g):
    cols = sorted({c for row in g.g for c in row if c}, key=lum)
    keys = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    lines = ['%s %s' % (keys[i], c) for i, c in enumerate(cols)] + ['']
    lines += [''.join(keys[cols.index(c)] if c else '.' for c in row) for row in g.g]
    with open(os.path.join(GRIDS, name + '.txt'), 'w', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


def main():
    sheet = Image.new('RGBA', (5 * 150 + 20, 2 * 170 + 20), (139, 139, 139, 255))
    for i, (name, fn) in enumerate(PIECES.items()):
        g = fn()
        if name not in DIAGONAL:
            assert all(g.g[y][x] == g.g[y][15 - x] for y in range(16) for x in range(16)), name
        write(name, g)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                if g.g[y][x]:
                    im.putpixel((x, y), tuple(int(g.g[y][x][k:k + 2], 16) for k in (1, 3, 5)) + (255,))
        x0, y0 = 20 + (i % 5) * 150, 20 + (i // 5) * 170
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (x0, y0))
        sheet.alpha_composite(im, (x0 + 56, y0 + 140))
    sheet.save(os.path.join(os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.')), 'key_pieces_preview.png'))
    print('wrote', len(PIECES), 'grids')


if __name__ == '__main__':
    main()
