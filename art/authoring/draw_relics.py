"""The Light Key and the three portal relics of Solsticio, on the 16x16 item grid.

- light_key: traced from the vanilla trial key (Elias: "trazá sprites vanilla") and repainted by
  luminance rank: a cream-gold head with teal eyes of light, a brass shaft and bit.
- light_key_broken: the same key snapped below the collar, dimmer, with a dark crack.
- heliodor_relic_1, Juan's Luminous Seed: a bioengineered seed, a golden-green teardrop with a
  glowing core and a two-leaf sprout.
- heliodor_relic_2, Terra's Terraprism: a glass prism in a copper frame that splits the light into
  rays of energy.
- heliodor_relic_3, the Blessed Heart of Heliodor (Bodhi's sacred crystal): a faceted gold crystal
  with a glowing heart inside.
The three relics are mirror-symmetric about the vertical axis; the key keeps vanilla's silhouette.

    python art/authoring/draw_relics.py     # writes art/grids/item/<id>.txt and a preview sheet
"""
import colorsys
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

GRIDS = os.path.join(HERE, '..', 'grids', 'item')
VANILLA = 'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/ref/vanilla/assets/minecraft/textures/item/'
GOLD = ['#3a2a0e', '#6e5220', '#a8862f', '#d9b95a', '#f1dc98', '#fff6dc', '#ffffff']
INK = '#1f1d22'


def lum(h):
    c = tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) if isinstance(h, str) else h
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def hexs(c):
    return '#%02x%02x%02x' % c


def rank_map(colours, ramp):
    cs = sorted(set(colours), key=lum)
    if len(cs) == 1:
        return {cs[0]: ramp[len(ramp) // 2]}
    return {c: ramp[round(i * (len(ramp) - 1) / (len(cs) - 1))] for i, c in enumerate(cs)}


def key_grids():
    im = Image.open(VANILLA + 'trial_key.png').convert('RGBA').crop((0, 0, 16, 16))
    px = im.load()
    eyes, head, shaft = [], [], []
    for y in range(16):
        for x in range(16):
            p = px[x, y]
            if p[3] < 128:
                continue
            h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in p[:3]))
            if s > 0.55 and v > 0.9 and y < 8:
                eyes.append(p[:3])
            elif y <= 7 and s < 0.3:
                head.append(p[:3])
            else:
                shaft.append(p[:3])
    maps = {}
    maps.update(rank_map(head, GOLD[1:]))
    maps.update(rank_map(shaft, [R['brass'][i] for i in range(6)]))
    for c in set(eyes):
        maps[c] = R['teal'][4]
    whole = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            p = px[x, y]
            if p[3] >= 128:
                whole[y][x] = maps[p[:3]]
    # the broken key: snapped just below the collar, one step dimmer, with a crack
    dim = {}
    for ramp in (GOLD, R['brass'], R['teal']):
        for i, c in enumerate(ramp):
            dim[c] = ramp[max(0, i - 1)]
    broken = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c = whole[y][x]
            if c is None or y >= 11 or (y == 10 and x == 9):
                continue
            broken[y][x] = dim.get(c, c)
    for (x, y) in ((7, 8), (8, 9), (7, 10)):
        broken[y][x] = R['brass'][0]
    broken[10][6] = R['brass'][1]
    return whole, broken


def sym_grid(fn):
    """Build a grid mirror-symmetric about the vertical axis from fn(dx, y), dx = |x - 7.5|."""
    g = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            g[y][x] = fn(abs(x - 7.5), y)
    return outline(g)


def outline(g, ink=INK):
    out = [row[:] for row in g]
    for y in range(16):
        for x in range(16):
            if g[y][x] is None and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and g[y + dy][x + dx]
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = ink
    return out


def seed(dx, y):
    le, st, br = R['leaf'], R['straw'], R['brass']
    body = {5: 0.5, 6: 1.0, 7: 1.5, 8: 2.5, 9: 3.0, 10: 3.5, 11: 3.5, 12: 3.5, 13: 2.5, 14: 1.5}
    if y in body and dx <= body[y]:
        if dx <= 0.5 and 9 <= y <= 11:
            return '#ffffff' if y == 10 else le[5]
        if dx <= 1.5 and 8 <= y <= 12:
            return le[4]
        if dx >= body[y] - 0.6:
            return le[1] if y > 10 else le[2]
        return st[3] if dx < 2.6 else st[2]
    if 2 <= y <= 4 and dx <= 0.5:
        return le[3]
    if (y == 2 and dx == 1.5) or (y == 1 and dx == 2.5) or (y == 3 and dx == 1.5):
        return le[4] if y < 3 else le[3]
    return None


def terraprism(dx, y):
    cu, gl, br = R['copper'], R['glass'], R['brass']
    if y == 0 and dx <= 0.5:
        return '#ffffff'
    if 1 <= y <= 9:
        half = 0.5 + (y - 1) * 0.6
        if dx <= half:
            if dx >= half - 0.7:
                return cu[4] if y < 5 else cu[3]
            if dx <= 0.5:
                return '#ffffff' if y < 6 else gl[4]
            return gl[3] if dx < half - 1.5 else gl[2]
        return None
    if y == 10 and dx <= 5.5:
        return cu[2] if dx > 4.5 else cu[4]
    if 11 <= y <= 15:
        k = y - 10
        if dx <= 0.5 + k * 0.25:
            return br[5]
        if dx <= 0.5 + k * 0.7:
            return R['teal'][4]
        if dx <= 0.5 + k * 1.15:
            return R['sky'][3]
        if dx <= 0.5 + k * 1.55:
            return R['violet'][4]
    return None


def heart(dx, y):
    br, cr = R['brass'], R['crimson']
    half = {1: 0.5, 2: 1.5, 3: 2.5, 4: 3.5, 5: 4.5, 6: 5.5, 7: 5.5, 8: 5.0, 9: 4.5, 10: 3.5, 11: 2.5, 12: 2.0, 13: 1.0,
            14: 0.5}
    if y not in half or dx > half[y]:
        return None
    heart_px = {7: (1.5, 2.5), 8: (0.5, 1.5, 2.5), 9: (0.5, 1.5), 10: (0.5,)}
    if y in heart_px and dx in heart_px[y]:
        return cr[5] if (y == 7 and dx == 1.5) or (y == 8 and dx == 0.5) else cr[4]
    if y == 6:
        return GOLD[5] if dx < half[y] - 0.6 else GOLD[3]
    if dx >= half[y] - 0.6:
        return GOLD[2] if y > 6 else GOLD[3]
    if dx <= 0.5:
        return GOLD[5] if y < 6 else GOLD[4]
    return GOLD[4] if y < 6 else GOLD[3]


def write(name, g):
    cols = sorted({c for row in g for c in row if c}, key=lum)
    keys = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij'
    lines = ['%s %s' % (keys[i], c) for i, c in enumerate(cols)] + ['']
    lines += [''.join(keys[cols.index(c)] if c else '.' for c in row) for row in g]
    with open(os.path.join(GRIDS, name + '.txt'), 'w', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


def image(g):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if g[y][x]:
                c = g[y][x]
                im.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,))
    return im


if __name__ == '__main__':
    key, broken = key_grids()
    sheets = {'light_key': key, 'light_key_broken': broken, 'heliodor_relic_1': sym_grid(seed),
              'heliodor_relic_2': sym_grid(terraprism), 'heliodor_relic_3': sym_grid(heart)}
    for name in ('heliodor_relic_1', 'heliodor_relic_2', 'heliodor_relic_3'):
        g = sheets[name]
        assert all(g[y][x] == g[y][15 - x] for y in range(16) for x in range(16)), name
    for name, g in sheets.items():
        write(name, g)
    sheet = Image.new('RGBA', (len(sheets) * 144 + 16, 160 + 40), (139, 139, 139, 255))
    for i, g in enumerate(sheets.values()):
        im = image(g)
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (16 + i * 144, 16))
        sheet.alpha_composite(im, (16 + i * 144 + 56, 160))
    sheet.save(os.path.join(os.environ.get('TEMP', '.'), 'relics_preview.png'))
    print('ok')
