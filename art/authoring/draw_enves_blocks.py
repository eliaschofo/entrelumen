"""The Envés's own puzzle and shrine blocks: native 16x16 grids plus sculpted models, 1 texel per unit.

The four blocks replace the provisional vanilla looks (docs/design/dungeon-enves.md, «Para una segunda
pasada»). One family: the White Wither's ivory and gold (draw_white_wither.py), the Osarios bones, a dark
Envés stone, and the sour light (TIP, BODY and ROT in draw_enves.py) as the only thing that glows.

- enves_shrine, «el relicario»: a drop of sour light held up by a crown of ribs on a spine of vertebrae.
  Fresh, the drop glows (emissive) and invites the touch; spent, it is a dead olive husk, shorter.
- enves_brazier, «la campana del revés»: the Envés copies everything backwards, so Heliodor's bells stand
  upside down and hold fire; the clapper points up like a wick. Lit, a sour flame (animated, emissive);
  cold, ash and a charred clapper. The braziers already chime when they play their order.
- enves_mirror, «el biombo»: a two-faced silver plate on the diagonal between two bone posts that stand
  in the block's corners, with a pilot light on each post. The posts and lights mark the two corners the
  mirror joins, so '/' (aim 0) and '\\' (aim 1) read from any side; gold edges along the foot repeat it.
- enves_glyph, «las piedras de glifo»: dark stone with a gold inlay, four silhouettes that differ in
  kind (radial sun, closed eye, the tree over its roots, the stair going down), so shape alone tells them apart.

Models keep 1 texel per 1/16 unit: every face's UV spans its own size and nothing is rescaled (the mirror's
plate and the shrine's diagonal ribs turn 45 degrees without rescale; parts centred on the block's axis snap
their UVs to whole texels). Every piece is mirror-symmetric: the shrine and the brazier under D4, the mirror
about its plate and the plane across it, the glyph faces left-right.

    python art/authoring/draw_enves_blocks.py            # grids + models + review sheets
    python art/authoring/draw_enves_blocks.py --check    # regenerate in memory and compare with the repo

Review sheets go to $ENVES_PREVIEW (default %TEMP%/enves-art); they are never committed.
"""
import argparse
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from draw_enves import TIP, BODY, ROT, CRACK  # noqa: E402  (the sour light)
from draw_white_wither import IVORY, GOLD  # noqa: E402     (the White Wither's ivory and gold)
from palette import RAMPS  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
GRIDS = os.path.join(ROOT, 'art', 'grids', 'block')
MODELS = os.path.join(ROOT, 'art', 'models', 'block')
DIRS = ('north', 'south', 'east', 'west', 'up', 'down')

INK = RAMPS['ink'][1]
STONE = ['#16161a', '#1e1e23', '#27272d', '#323239', '#3f3e46', '#4e4c55']     # the Envés's dark stone
BRONZE = ['#3a2912', '#57401b', '#7a5b25']                                      # the bell's shade, into GOLD
SILVER = RAMPS['iron'][1:]                                                       # '#41454d' .. '#e9ecef'


def noise(x, y, seed=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n & 0xffff) / 0xffff


def lr(x, y, seed):
    """Noise mirrored left-right."""
    return noise(min(x, 15 - x), y, seed)


def quad(x, y, seed):
    """Noise mirrored in both axes and across the diagonal (four-fold symmetric)."""
    a, b = min(x, 15 - x), min(y, 15 - y)
    return noise(min(a, b), max(a, b), seed)


# ---------------------------------------------------------------------------------------------- grids
class Grid:
    """A 16x16 texture as hex colours (None is transparent)."""

    def __init__(self, fill=None):
        self.g = [[fill] * 16 for _ in range(16)]

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = c

    def row(self, y, c, x0=0, x1=15):
        for x in range(x0, x1 + 1):
            self.px(x, y, c)

    def mirror_lr(self):
        """Copies the left half onto the right."""
        for y in range(16):
            for x in range(8):
                self.g[y][15 - x] = self.g[y][x]
        return self

    def image(self):
        from PIL import Image
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if c:
                    im.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,))
        return im

    def text(self):
        colours = sorted({c for row in self.g for c in row if c})
        keys = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
        letters = {c: keys[i] for i, c in enumerate(colours)}
        lines = [f'{letters[c]} {c}' for c in colours] + ['']
        lines += [''.join(letters[c] if c else '.' for c in row) for row in self.g]
        return '\n'.join(lines) + '\n'


def lr_symmetric(g):
    return all(g.g[y][x] == g.g[y][15 - x] for y in range(16) for x in range(16))


def d4_symmetric(g):
    return lr_symmetric(g) and all(g.g[y][x] == g.g[15 - y][x] == g.g[x][y] for y in range(16) for x in range(16))


def rings(fn):
    """A four-fold symmetric texture from a function of the ring index r = 0 (centre) .. 7 (edge) and the
    position along the ring's octant."""
    g = Grid()
    for y in range(16):
        for x in range(16):
            a, b = min(x, 15 - x), min(y, 15 - y)                  # 0 at the edge .. 7 at the centre
            g.px(x, y, fn(7 - min(a, b), min(a, b), max(a, b), x, y))
    return g


# ---------------------------------------------------------------------------------------------- textures
def shrine_plinth():
    """Top and sides of the plinth in one four-fold texture: its border rows are the side bands (row 15 is the
    plinth's foot, row 14 its top edge, row 13 the step). On top, eight carved rays point in to the drop: a sun
    turned inside out, drinking the light instead of giving it."""
    def f(r, a, b, x, y):
        if r == 7:
            return GOLD[0] if a == 0 and b == 0 else STONE[1]         # the foot line, gold at the corners
        if r == 6:
            return STONE[4]                                             # the top edge catches the light
        ray = (x in (7, 8) or y in (7, 8) or x == y or x + y == 15)
        if ray and r in (4, 5):
            return GOLD[1] if r == 4 else STONE[5]                      # the rays: gold where they reach the spine
        if r == 5:
            return STONE[3] if quad(x, y, 3) < 0.7 else STONE[2]
        return STONE[2] if quad(x, y, 4) < 0.75 else STONE[3]
    return rings(f)


def shrine_bone():
    """Sides of the spine and the ribs, drawn by height: rows 12..9 are two vertebrae (disc, core, disc, core),
    rows 7..2 the ribs, lighter towards the tips."""
    g = Grid()
    rows = {12: IVORY[3], 11: IVORY[0], 10: IVORY[3], 9: IVORY[0], 8: IVORY[1],
            7: IVORY[1], 6: IVORY[2], 5: IVORY[2], 4: IVORY[3], 3: IVORY[3], 2: IVORY[4]}
    for y in range(16):
        for x in range(8):
            c = rows.get(y, IVORY[2])
            if y in (12, 10, 5, 4) and lr(x, y, 11) < 0.2:
                c = IVORY[IVORY.index(c) - 1]                           # grain
            g.px(x, y, c)
    return g.mirror_lr()


def shrine_bone_top():
    """Tops of the vertebrae and ribs: light ivory, the discs' rims a step darker."""
    def f(r, a, b, x, y):
        if r == 3:
            return IVORY[3]
        return IVORY[5] if quad(x, y, 12) < 0.7 else IVORY[4]
    return rings(f)


def enves_gold():
    """The White Wither's gold, four-fold: the collar, the post caps, the bell's accents."""
    def f(r, a, b, x, y):
        if r == 2:
            return GOLD[2]
        if r <= 1:
            return GOLD[3] if quad(x, y, 22) < 0.5 else GOLD[2]
        return GOLD[2] if quad(x, y, 21) < 0.35 else GOLD[1]
    return rings(f)


def shrine_heart(dead=False):
    """The drop of sour light. Rows 0..7 are its side (y 15 down to 8), columns 5..10 its width; rows 9..14 hold
    its top view (the model shifts the up and down faces there). Fresh it burns from a white core; spent it is
    the dead olive of ROT, cracked down the middle."""
    g = Grid(STONE[1])
    side = ['..gg..', '..TT..', '.bTTb.', '.bTTb.', 'dbggbd', 'dbggbd', 'edbbde', '.edde.']
    top = ['..dd..', '.dbbd.', 'dbTTbd', 'dbTTbd', '.dbbd.', '..dd..']
    pal = {'T': TIP[3], 'g': TIP[2], 'b': BODY[3], 'd': BODY[2], 'e': BODY[1]}
    if dead:                                          # four layers left, broken open at the top
        side = ['......', '......', '......', '......', '.dccd.', 'drccrd', 'drrrrd', '.dddd.']
        top = ['..dd..', '.drrd.', 'drccrd', 'drccrd', '.drrd.', '..dd..']
        pal = {'r': ROT[2], 'c': CRACK, 'd': ROT[1]}
    for r, line in enumerate(side):
        for i, ch in enumerate(line):
            if ch != '.':
                g.px(5 + i, r, pal[ch])
    for r, line in enumerate(top):
        for i, ch in enumerate(line):
            if ch != '.':
                g.px(5 + i, 9 + r, pal[ch])
    return g.mirror_lr()


def brazier_bell():
    """Sides of the upturned bell, drawn by height (row 15 is the foot, row 9 the lip): bronze below, the
    Wither's gold above, the sound bow brightest."""
    g = Grid(BRONZE[1])
    rows = {15: BRONZE[1], 14: BRONZE[0], 13: BRONZE[2], 12: GOLD[0], 11: GOLD[1], 10: GOLD[2], 9: GOLD[1]}
    for y, c in rows.items():
        for x in range(8):
            g.px(x, y, c)
    for x in range(8):
        if lr(x, 11, 31) < 0.3:
            g.px(x, 11, GOLD[0])                                        # tarnish on the waist
        if lr(x, 13, 32) < 0.25:
            g.px(x, 13, BRONZE[1])
    g.px(7, 10, GOLD[3])
    g.px(4, 10, GOLD[3])
    return g.mirror_lr()


def brazier_bell_top():
    """Tops of the bell, four-fold: the lip's bright rim, the ledge inside, the foot's ring."""
    def f(r, a, b, x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 4.9:
            return GOLD[3] if d <= 5.5 else GOLD[2]
        if d > 3.6:
            return GOLD[0]
        return BRONZE[2] if d > 2.2 else BRONZE[1]
    return rings(f)


def brazier_floor(lit):
    """The bell's floor and the clapper standing in it: embers of sour light when lit, ash and char when cold.
    The floor's top is the disc in the middle (four-fold); columns 7..8 of rows 0..3 are the clapper's sides (the
    model shifts them there, out of the disc)."""
    g = Grid()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            n = quad(x, y, 41)
            if lit:
                c = TIP[3] if d < 1.6 else TIP[2] if d < 2.6 or n < 0.15 else BODY[3] if n < 0.7 else BODY[2]
            else:
                c = INK if n < 0.2 else ROT[0] if n < 0.75 else ROT[1]
            g.px(x, y, c)
    for y in range(0, 4):                                               # the clapper's sides (y 7 down to 4)
        for x in (7, 8):
            g.px(x, y, (TIP[3] if y == 0 else TIP[2]) if lit else (ROT[1] if y == 0 else BRONZE[1]))
    return g


FLAME = [
    # frames of the sour flame, columns 3..12 (mirror-symmetric), rows 0..9 (y 16 down to 6): a tall middle
    # tongue and two side ones that rise and fall; 1 edge .. 4 core
    ['....11....', '.1..22..1.', '.1..22..1.', '.21.33.12.', '.21233212.',
     '1223333221', '1233443321', '1234444321', '1234444321', '.12344321.'],
    ['.1......1.', '.1..11..1.', '.2..22..2.', '.21.22.12.', '.21233212.',
     '1223333221', '1233443321', '1234444321', '1234444321', '.12344321.'],
    ['..........', '....11....', '.1..22..1.', '.1.1331.1.', '.21233212.',
     '1223333221', '1233443321', '1234444321', '1234444321', '.12344321.'],
    ['....11....', '....22....', '.1..33..1.', '.1..33..1.', '.21233212.',
     '1223443221', '1233443321', '1234444321', '1234444321', '.12344321.'],
]


def brazier_flame(frame):
    g = Grid()
    cols = {'1': BODY[2], '2': BODY[3], '3': TIP[2], '4': TIP[3]}
    for r, line in enumerate(FLAME[frame]):
        assert line == line[::-1], (frame, r)
        for i, ch in enumerate(line):
            if ch != '.':
                g.px(3 + i, r, cols[ch])
    return g


def mirror_glass():
    """The plate, both faces (rows 3..12, all 16 columns): polished silver that holds a sour glint at the top and a
    pale band down the middle, framed by a darker edge."""
    g = Grid(SILVER[2])
    for y in range(3, 13):
        for x in range(8):
            c = SILVER[3]
            if x in (6, 7) and 5 <= y <= 11:
                c = SILVER[4]                                           # the band where the light crosses
            if (x + y) % 7 == 0 and 4 <= y <= 11 and x < 6:
                c = SILVER[4]                                           # glints, mirrored into a chevron
            if y == 4:
                c = TIP[2] if x >= 3 else SILVER[4]                     # the sour light it holds
            if y in (3, 12) or x == 0:
                c = SILVER[1]
            g.px(x, y, c)
    return g.mirror_lr()


def mirror_bone():
    """The posts (columns 1..2 and 13..14: stacked vertebrae from row 15 up to row 3) and the frame bars (rows 2
    and 13), in the shrine's ivory."""
    g = Grid(IVORY[3])
    for y in range(16):
        for x in range(8):
            g.px(x, y, IVORY[3] if lr(x, y, 51) < 0.8 else IVORY[4])
    for x in range(8):
        g.px(x, 2, IVORY[4])                                            # the top bar
        g.px(x, 13, IVORY[2])                                           # the bottom bar
    for y in range(3, 16):
        c = IVORY[4] if (15 - y) % 3 == 0 else IVORY[3] if (15 - y) % 3 == 1 else IVORY[1]
        for x in (1, 2):
            g.px(x, y, c)                                               # vertebrae: disc, body, joint
    return g.mirror_lr()


def mirror_foot():
    """The foot under the diagonal: dark stone with gold edges along the plate's direction, on its top (rows 5 and
    10, beside the plate) and its sides (row 14), so the diagonal shows from above and from low down."""
    g = Grid()
    for y in range(16):
        for x in range(16):
            g.px(x, y, STONE[2] if lr(x, y, 61) < 0.7 else STONE[3])
    g.row(14, GOLD[0])                                                  # the sides' top edge
    g.row(15, STONE[2])
    for y in (5, 10):
        g.row(y, GOLD[1])                                               # the top's edges, beside the plate
    for y in (6, 9):
        g.row(y, STONE[4])
    return g


GLYPHS = {
    # 12x12 inlays (columns 2..13, rows 2..13). Each differs in kind, not in detail.
    0: ['.....##.....',        # the sun: radial, a ring round a dot, rays out
        '.#...##...#.',
        '..#......#..',
        '....####....',
        '...#....#...',
        '##.#.##.#.##',
        '##.#.##.#.##',
        '...#....#...',
        '....####....',
        '..#......#..',
        '.#...##...#.',
        '.....##.....'],
    1: ['............',        # the eye: one closed horizontal shape with a pupil
        '............',
        '....####....',
        '..##....##..',
        '.#...##...#.',
        '#...####...#',
        '#...####...#',
        '.#...##...#.',
        '..##....##..',
        '....####....',
        '............',
        '............'],
    2: ['.....##.....',        # the tree and its reversed copy, the roots: one vertical axis
        '..#..##..#..',
        '..##.##.##..',
        '...######...',
        '.....##.....',
        '.....##.....',
        '.....##.....',
        '.....##.....',
        '...######...',
        '..##.##.##..',
        '..#..##..#..',
        '.....##.....'],
    3: ['............',        # the stair going down: bars that narrow to a point
        '############',
        '............',
        '.##########.',
        '............',
        '..########..',
        '............',
        '...######...',
        '............',
        '....####....',
        '............',
        '.....##.....'],
}
GLYPH_NAMES = {0: 'sun', 1: 'eye', 2: 'tree over its roots', 3: 'stair going down'}


def glyph_side(i):
    """A glyph stone's four sides: dark Envés stone, a bevelled rim with gold corner pins, and the glyph inlaid in
    gold, lit on its upper edges."""
    g = Grid()
    for y in range(16):
        for x in range(16):
            g.px(x, y, STONE[2] if lr(x, y, 71) < 0.72 else STONE[1])
    g.row(0, STONE[4])
    g.row(15, STONE[0])
    for y in range(1, 15):
        g.px(0, y, STONE[3])
        g.px(15, y, STONE[3])
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        g.px(x, y, GOLD[1])
    mask = GLYPHS[i]
    on = {(2 + c, 2 + r) for r, line in enumerate(mask) for c, ch in enumerate(line) if ch == '#'}
    for (x, y) in on:
        c = GOLD[3] if (x, y - 1) not in on else GOLD[2] if (x, y + 1) in on else GOLD[1]
        g.px(x, y, c)
        if (x, y + 1) not in on and y + 1 < 15:
            g.px(x, y + 1, STONE[0])                                    # the groove's shadow under the inlay
    return g


def glyph_top():
    """Top and bottom of the glyph stones: the pivot they turn on, a round gold ring in dark stone."""
    def f(r, a, b, x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if r == 7:
            return STONE[3]
        if 2.4 < d <= 3.6:
            return GOLD[2] if y < 8 else GOLD[1]
        if d <= 2.4:
            return STONE[0]
        return STONE[2] if quad(x, y, 81) < 0.75 else STONE[1]
    return rings(f)


TEXTURES = {
    'enves_shrine_plinth': shrine_plinth,
    'enves_shrine_bone': shrine_bone,
    'enves_shrine_bone_top': shrine_bone_top,
    'enves_gold': enves_gold,
    'enves_shrine_heart': lambda: shrine_heart(False),
    'enves_shrine_heart_dead': lambda: shrine_heart(True),
    'enves_brazier_bell': brazier_bell,
    'enves_brazier_bell_top': brazier_bell_top,
    'enves_brazier_embers': lambda: brazier_floor(True),
    'enves_brazier_ash': lambda: brazier_floor(False),
    'enves_mirror_glass': mirror_glass,
    'enves_mirror_bone': mirror_bone,
    'enves_mirror_foot': mirror_foot,
    'enves_glyph_top': glyph_top,
}
TEXTURES.update({f'enves_glyph_{i}': (lambda i=i: glyph_side(i)) for i in GLYPHS})
FLAME_FRAMES = {'enves_brazier_flame': len(FLAME)}                    # animated: <name>__f<N>.txt
# Symmetry each texture must keep: 'lr' (left-right) or 'd4' (four-fold).
SYMMETRY = {'enves_shrine_plinth': 'd4', 'enves_shrine_bone_top': 'd4', 'enves_gold': 'd4', 'enves_brazier_bell_top': 'd4',
            'enves_glyph_top': 'lr'}


# ---------------------------------------------------------------------------------------------- models
def box_uv(face, a, b):
    """Vanilla's automatic UV: a face samples the texture where it sits in the block, 1 texel per unit."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
            'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0],
            'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0]}[face]


def face_size(face, a, b):
    dx, dy, dz = (b[i] - a[i] for i in range(3))
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[face]


def num(v):
    return int(v) if float(v).is_integer() else v


def element(a, b, tex, faces=DIRS, glow=False, rotation=None, uv=None, name=None):
    """tex: a texture key, or {'side': key, 'up': key, 'down': key}; uv: explicit UVs per face (same size)."""
    e = {}
    if name:
        e['name'] = name
    e['from'] = [num(v) for v in a]
    e['to'] = [num(v) for v in b]
    if rotation:
        e['rotation'] = rotation
    e['faces'] = {}
    for f in faces:
        key = tex if isinstance(tex, str) else tex.get(f, tex['side'])
        face = {'uv': [num(v) for v in (uv or {}).get(f, box_uv(f, a, b))], 'texture': '#' + key}
        if f == 'down' and a[1] == 0 and not rotation:
            face['cullface'] = 'down'
        e['faces'][f] = face
    if glow:
        e['shade'] = False
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    return e


def voxel_boxes(V):
    """Greedy merge of voxels {(x, y, z): key} into boxes of one key: along x, then z, then y."""
    left = dict(V)
    boxes = []
    for (x, y, z) in sorted(V, key=lambda p: (p[1], p[2], p[0])):
        if (x, y, z) not in left:
            continue
        k = left[(x, y, z)]
        x1 = x
        while left.get((x1 + 1, y, z)) == k:
            x1 += 1
        z1 = z
        while all(left.get((i, y, z1 + 1)) == k for i in range(x, x1 + 1)):
            z1 += 1
        y1 = y
        while all(left.get((i, y1 + 1, j)) == k for i in range(x, x1 + 1) for j in range(z, z1 + 1)):
            y1 += 1
        for i in range(x, x1 + 1):
            for j in range(z, z1 + 1):
                for h in range(y, y1 + 1):
                    del left[(i, h, j)]
        boxes.append(((x, y, z), (x1 + 1, y1 + 1, z1 + 1), k))
    return boxes


def hidden(face, a, b, V):
    """A box face is hidden when every voxel just outside it is solid."""
    axis = {'north': 2, 'south': 2, 'east': 0, 'west': 0, 'up': 1, 'down': 1}[face]
    out = b[axis] if face in ('south', 'east', 'up') else a[axis] - 1
    ranges = [range(a[i], b[i]) for i in range(3)]
    ranges[axis] = [out]
    return all((x, y, z) in V for x in ranges[0] for y in ranges[1] for z in ranges[2])


def voxel_elements(V, spec):
    """spec: key -> {'tex': str or per-face dict, 'glow': bool, 'shift': {face: (du, dv)}}."""
    els = []
    for a, b, k in voxel_boxes(V):
        s = spec[k]
        faces = [f for f in DIRS if not hidden(f, a, b, V)]
        if not faces:
            continue
        uv = {}
        for f, (du, dv) in s.get('shift', {}).items():
            u0, v0, u1, v1 = box_uv(f, a, b)
            uv[f] = [u0 + du, v0 + dv, u1 + du, v1 + dv]
        els.append(element(a, b, s['tex'], faces, glow=s.get('glow', False), uv=uv))
    return els


def d4(V):
    return all(V.get((15 - x, y, z)) == k and V.get((x, y, 15 - z)) == k and V.get((z, y, x)) == k
               for (x, y, z), k in V.items())


def d4_fill(octant):
    """Mirrors voxels written in one quadrant (x, z <= 7) to the whole D4 orbit."""
    V = {}
    for (x, y, z), k in octant.items():
        for (p, q) in ((x, z), (z, x)):
            for (u, w) in ((p, q), (15 - p, q), (p, 15 - q), (15 - p, 15 - q)):
                V[(u, y, w)] = k
    return V


def disc(x, z, radius):
    """Voxel (x, z) of a quadrant lies in a round disc of this radius about the block's axis."""
    return math.hypot(7.5 - x, 7.5 - z) <= radius


def model_json(textures, elements, comment, particle, cutout=False):
    tex = {'particle': 'entrelumen:block/' + particle}
    tex.update({k: 'entrelumen:block/' + v for k, v in textures.items()})
    m = {'__comment': comment, 'parent': 'minecraft:block/block', 'ambientocclusion': False}
    if cutout:
        m['render_type'] = 'minecraft:cutout'
    m['textures'] = tex
    m['elements'] = elements
    return m


# -- the shrine
def shrine_voxels(spent):
    o = {}
    for x in range(8):
        for z in range(8):
            rx, rz = 7 - x, 7 - z
            r = max(rx, rz)
            o[(x, 0, z)] = o[(x, 1, z)] = 'plinth'
            if r <= 6:
                o[(x, 2, z)] = 'plinth'
            for y in (3, 5):                                    # two vertebrae: a disc with four processes
                if r <= 3 and not (rx == 3 and rz == 3):
                    o[(x, y, z)] = 'bone'
                if rx == 0 and rz == 4:
                    o[(x, y, z)] = 'bone'
            for y in (4, 6):
                if r <= 1:
                    o[(x, y, z)] = 'bone'
            if r <= 2:
                o[(x, 7, z)] = 'gold'                           # the setting
    drop = {8: 2, 9: 3, 10: 3, 11: 3, 12: 2, 13: 2, 14: 1, 15: 1} if not spent else {8: 2, 9: 3, 10: 3, 11: 2}
    for y, w in drop.items():
        for x in range(8 - w, 8):
            for z in range(8 - w, 8):
                if not (w == 3 and x == 5 and z == 5):
                    o[(x, y, z)] = 'heart'
    return d4_fill(o)


TURN = {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': False}
# The ribs, one unit thick along a line through the block's axis: (y0, y1, from, to) as distances out from it.
# Four tall ones on the diagonals leave the setting, spread and curl back in above the drop's belly; four short
# ones on the axes hold the belly. The diagonal ones are built on the axes of a frame turned 45 degrees.
DIAGONAL_RIB = [(8, 9, 2.5, 4.5), (9, 10, 3.5, 5.5), (10, 13, 4.5, 5.5), (13, 14, 4, 5)]
AXIS_RIB = [(8, 9, 2.5, 4.5), (9, 12, 3.5, 4.5)]


def snapped(e):
    """Moves each face's UV onto whole texels (a part centred on the axis sits half a unit off the grid)."""
    for face in e['faces'].values():
        u0, v0, u1, v1 = face['uv']
        face['uv'] = [num(math.floor(u0)), num(math.floor(v0)), num(math.floor(u0) + (u1 - u0)), num(math.floor(v0) + (v1 - v0))]
    return e


def ribs(path, rotation, tex):
    els = []
    for sx, sz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for y0, y1, d0, d1 in path:
            if sx:
                a, b = (8 + sx * d0, y0, 7.5), (8 + sx * d1, y1, 8.5)
            else:
                a, b = (7.5, y0, 8 + sz * d0), (8.5, y1, 8 + sz * d1)
            lo, hi = tuple(min(p, q) for p, q in zip(a, b)), tuple(max(p, q) for p, q in zip(a, b))
            els.append(snapped(element(lo, hi, tex, rotation=rotation)))
    return els


def shrine(spent):
    V = shrine_voxels(spent)
    assert d4(V), 'the shrine must be D4-symmetric'
    heart = 'heart_dead' if spent else 'heart'
    bone = {'side': 'bone', 'up': 'bone_top', 'down': 'bone_top'}
    spec = {'plinth': {'tex': 'plinth'}, 'bone': {'tex': bone},
            'gold': {'tex': 'gold'}, 'heart': {'tex': heart, 'glow': not spent, 'shift': {'up': (0, 4), 'down': (0, 4)}}}
    textures = {'plinth': 'enves_shrine_plinth', 'bone': 'enves_shrine_bone', 'bone_top': 'enves_shrine_bone_top',
                'gold': 'enves_gold', heart: 'enves_shrine_heart' + ('_dead' if spent else '')}
    comment = ('Envés shrine, spent: the drop of sour light is a dead husk (art/authoring/draw_enves_blocks.py)' if spent else
               'Envés shrine: a drop of sour light held up by a crown of ribs on a spine (art/authoring/draw_enves_blocks.py)')
    els = voxel_elements(V, spec) + ribs(DIAGONAL_RIB, TURN, bone) + ribs(AXIS_RIB, None, bone)
    return model_json(textures, els, comment, 'enves_shrine_bone')


# -- the brazier
BELL = [  # (y, outer radius, inner radius of the hollow or 0, key); y 0 holds the feet
    (1, 3.2, 0, 'bell'), (2, 4.0, 0, 'bell'), (3, 5.0, 0, 'bell'),
    (4, 5.0, 3.6, 'bell'), (5, 5.9, 4.4, 'bell'), (6, 5.9, 4.9, 'bell')]


def brazier_voxels(lit):
    o = {}
    for y, R, hollow, k in BELL:
        for x in range(8):
            for z in range(8):
                if disc(x, z, R) and not (hollow and disc(x, z, hollow)):
                    o[(x, y, z)] = k
    for x in range(8):
        for z in range(8):
            if disc(x, z, 3.6):
                o[(x, 3, z)] = 'floor'                          # the bell's floor, under the hollow
    for z in (4, 5):
        o[(7, 0, z)] = 'bell'                                   # the crown's loops, the canons, as four feet
    for y in (4, 5, 6):
        o[(7, y, 7)] = 'clapper'                                # the clapper stands up like a wick
    o[(7, 7, 7)] = 'clapper'
    return d4_fill(o)


def brazier(lit):
    V = brazier_voxels(lit)
    assert d4(V), 'the brazier must be D4-symmetric'
    floor = 'embers' if lit else 'ash'
    spec = {'bell': {'tex': {'side': 'bell', 'up': 'bell_top', 'down': 'bell_top'}},
            'floor': {'tex': floor, 'glow': lit},             # only its top shows, inside the bell
            'clapper': {'tex': floor, 'glow': lit, 'shift': {f: (0, -8) for f in ('north', 'south', 'east', 'west')}}}
    els = voxel_elements(V, spec)
    textures = {'bell': 'enves_brazier_bell', 'bell_top': 'enves_brazier_bell_top', floor: 'enves_brazier_' + floor}
    if lit:
        for axis in ('x', 'z'):                                 # the flame: two crossed planes, 10 x 10
            a, b = ((3, 6, 8), (13, 16, 8)) if axis == 'x' else ((8, 6, 3), (8, 16, 13))
            faces = ('north', 'south') if axis == 'x' else ('east', 'west')
            els.append(element(a, b, 'flame', faces, glow=True, uv={f: [3, 0, 13, 10] for f in faces}))
        textures['flame'] = 'enves_brazier_flame'
    comment = ('Envés brazier, lit: the upturned bell burns with sour light (art/authoring/draw_enves_blocks.py)' if lit else
               'Envés brazier: an upturned bell, cold; the clapper stands like a wick (art/authoring/draw_enves_blocks.py)')
    return model_json(textures, els, comment, 'enves_brazier_bell', cutout=lit)


# -- the mirror


def mirror():
    """aim 0 is '/' seen from above: the plate runs from the south-west corner to the north-east one."""
    els = [
        element((0, 0, 5), (16, 2, 11), 'foot', rotation=TURN, name='foot'),
        element((0, 2, 7), (16, 3, 9), 'bone', rotation=TURN, name='bottom bar'),
        element((0, 3, 7.5), (16, 13, 8.5), 'glass', ('north', 'south'), rotation=TURN, name='plate'),
        element((0, 13, 7), (16, 14, 9), 'bone', rotation=TURN, name='top bar'),
    ]
    for (x0, z0) in ((1, 13), (13, 1)):                          # the posts in the corners the plate joins
        els.append(element((x0, 0, z0), (x0 + 2, 13, z0 + 2), 'bone', name='post'))
        els.append(element((x0, 13, z0), (x0 + 2, 14, z0 + 2), 'gold', name='cap'))
        els.append(element((x0, 14, z0), (x0 + 2, 16, z0 + 2), 'light', glow=True, name='pilot light',
                           uv={f: [7, 0, 9, 2] for f in DIRS}))
    textures = {'foot': 'enves_mirror_foot', 'bone': 'enves_mirror_bone', 'glass': 'enves_mirror_glass',
                'gold': 'enves_gold', 'light': 'enves_shrine_heart'}
    return model_json(textures, els, "Envés mirror: a two-faced plate between two corner posts with pilot lights; aim 0 is '/', "
                      "aim 1 turns it 90 degrees (art/authoring/draw_enves_blocks.py)", 'enves_mirror_bone')


def glyph(i):
    return {'__comment': f'Envés glyph stone {i}: the {GLYPH_NAMES[i]} (art/authoring/draw_enves_blocks.py)',
            'parent': 'minecraft:block/cube_column',
            'textures': {'side': f'entrelumen:block/enves_glyph_{i}', 'end': 'entrelumen:block/enves_glyph_top'}}


MODEL_FNS = {
    'enves_shrine': lambda: shrine(False), 'enves_shrine_spent': lambda: shrine(True),
    'enves_brazier': lambda: brazier(False), 'enves_brazier_lit': lambda: brazier(True),
    'enves_mirror': mirror,
}
MODEL_FNS.update({f'enves_glyph_{i}': (lambda i=i: glyph(i)) for i in GLYPHS})

# ---------------------------------------------------------------------------------------------- checks
def one_texel_per_unit(model):
    """Every face's UV spans exactly its size in 1/16 units, inside the texture, and nothing is rescaled."""
    for e in model.get('elements', []):
        assert not e.get('rotation', {}).get('rescale'), 'a rescaled element stretches its texels'
        for f, face in e['faces'].items():
            w, h = face_size(f, e['from'], e['to'])
            u0, v0, u1, v1 = face['uv']
            assert (abs(u1 - u0), abs(v1 - v0)) == (w, h), (f, e['from'], e['to'], face['uv'])
            assert 0 <= min(u0, u1) and max(u0, u1) <= 16 and 0 <= min(v0, v1) and max(v0, v1) <= 16, face['uv']


def mirror_symmetric(model):
    """The mirror's parts map onto themselves across its plate and across the plane perpendicular to it."""
    def key(e):
        return (tuple(e['from']), tuple(e['to']), tuple(sorted((f, v['texture']) for f, v in e['faces'].items())))
    turned = [e for e in model['elements'] if e.get('rotation')]
    for e in turned:                                            # local frame: symmetric about x = 8 and z = 8
        a, b = e['from'], e['to']
        assert a[0] + b[0] == 16 and a[2] + b[2] == 16, e
    posts = [e for e in model['elements'] if not e.get('rotation')]
    for swap in (lambda x, z: (16 - z, 16 - x), lambda x, z: (z, x)):     # across the plate; across the plane normal to it
        image = set()
        for e in posts:
            (x0, z0), (x1, z1) = swap(e['from'][0], e['from'][2]), swap(e['to'][0], e['to'][2])
            image.add(key({'from': [min(x0, x1), e['from'][1], min(z0, z1)], 'to': [max(x0, x1), e['to'][1], max(z0, z1)],
                           'faces': e['faces']}))
        assert image == {key(e) for e in posts}, 'the corner posts must sit on the plate line, symmetric'


def build():
    textures = {name: fn() for name, fn in TEXTURES.items()}
    for name, frames in FLAME_FRAMES.items():
        for i in range(frames):
            textures[f'{name}__f{i}'] = brazier_flame(i)
    for name, g in textures.items():
        sym = SYMMETRY.get(name, 'lr')
        assert (d4_symmetric(g) if sym == 'd4' else lr_symmetric(g)), f'{name} must be {sym}-symmetric'
        assert len({c for row in g.g for c in row if c}) <= 24, f'{name}: too many colours'
    models = {name: fn() for name, fn in MODEL_FNS.items()}
    for name, m in models.items():
        one_texel_per_unit(m)
        used = {v['texture'][1:] for e in m.get('elements', []) for v in e['faces'].values()}
        assert used <= set(m['textures']), (name, used - set(m['textures']))
        for t in m['textures'].values():
            base = t.split('/')[-1]
            assert base in textures or base + '__f0' in textures, (name, t)
    mirror_symmetric(models['enves_mirror'])
    return textures, models


def grid_path(name):
    return os.path.join(GRIDS, name + '.txt')


def model_path(name):
    return os.path.join(MODELS, name + '.json')


def model_text(m):
    return json.dumps(m, indent=1) + '\n'


def write(textures, models):
    for name, g in textures.items():
        with open(grid_path(name), 'w', encoding='utf-8', newline='\n') as f:
            f.write(g.text())
    os.makedirs(MODELS, exist_ok=True)
    for name, m in models.items():
        with open(model_path(name), 'w', encoding='utf-8', newline='\n') as f:
            f.write(model_text(m))


def check(textures, models):
    stale = []
    for name, g in textures.items():
        p = grid_path(name)
        if not os.path.exists(p) or open(p, encoding='utf-8').read() != g.text():
            stale.append(p)
    for name, m in models.items():
        p = model_path(name)
        if not os.path.exists(p) or open(p, encoding='utf-8').read() != model_text(m):
            stale.append(p)
    assert not stale, 'stale Envés art (run art/authoring/draw_enves_blocks.py): ' + ', '.join(stale)
    print(f'PASS: {len(textures)} Enves grids and {len(models)} models match their authoring; '
          '1 texel per unit, symmetric. In-game review is separate.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--no-preview', action='store_true')
    args = parser.parse_args()
    textures, models = build()
    if args.check:
        check(textures, models)
    else:
        write(textures, models)
        print(f'wrote {len(textures)} grids and {len(models)} models')
        if not args.no_preview:
            import enves_sheets
            out = os.environ.get('ENVES_PREVIEW', os.path.join(os.environ.get('TEMP', '.'), 'enves-art'))
            enves_sheets.sheets(textures, models, out)
