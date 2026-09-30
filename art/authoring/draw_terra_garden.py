"""Terra's hydroponic garden: the grow lamp (8 frames), the garden plan, the outlet and the core's pot.

Native 16x16 grids, hand-placed texel by texel and mirror-symmetric left-right (Elias's canon); every
grid is built from its left half and mirrored, so symmetry holds by construction.

- terra_grow_lamp (item, animated): Terra's grow lamp. A verdigris hood hung from a copper ring holds a
  captive luminous flame, drawn in the luminosities' language (dark outline, bright ramp, a 2x2 white-hot
  core, tongues that flicker every frame) but upside down: it hangs from the hood and drips light onto
  the crops. Sunlight gold edged in leaf green, a pairing no luminosity uses. Eight frames, frametime 2.
- terra_garden_plan (item): a hanging scroll between two copper rods; verdigris ink on parchment draws
  the engine's front elevation (pistons and skylight, grilles and the crop in its pot, headlamps over the
  green bed, the sump).
- terra_garden_outlet (block): a cut-copper casing with a brass flange round a round port, where the
  harvest leaves the engine.
- terra_engine_casing (block): riveted copper plates; the members' look while the engine is unformed.
- terra_garden_core (block): unformed, a casing cube whose front shows a round window onto a sleeping pot
  (dark, teal once the engine stands). Formed, one freestyle model over the whole 3 x 2 x 2 engine
  (engine_model): sump, block, finned cylinder banks, valve covers, four brass pistons, a radiator grille
  with warm headlamps and a green light strip, a flywheel and a belt to a pulley at the back, exhaust pipes,
  and on top the heart: a small copper pot whose rim glows a breathing green (8 frames, emissive). Its block
  entity draws the crop growing in the soil. 1 texel per unit (big boxes are cut at the 16-unit grid),
  mirror-symmetric about the middle column.
- terralight_* and the lamp as a block: see below.

    python art/authoring/draw_terra_garden.py            # write the grids and the core's models
    python art/authoring/draw_terra_garden.py --check    # compare with the repo
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
GRIDS = os.path.join(ROOT, 'art', 'grids')

VER = R['verdigris']            # oxidized copper: '#1c3f37' .. '#b0e8cc'
CU = R['copper']
TEAL = R['teal']
LEAF = R['leaf']
STRAW = R['straw']
BRASS = R['brass']
PARCH = R['parch']
GLASS = R['glass']
IRON = R['iron']
INK = R['ink'][1]
CALCITE = ['#6d7076', IRON[3], '#aeb2b6', IRON[4], '#d6d9dc', IRON[5]]   # vanilla calcite's cool white stone


def mirror(half_rows):
    """16 rows of 8 characters (the left half) -> 16 rows of 16, mirrored about the vertical axis."""
    rows = []
    for r in half_rows:
        assert len(r) == 8, r
        rows.append(r + r[::-1])
    assert len(rows) == 16
    return rows


def grid_text(palette, rows):
    used = sorted({c for r in rows for c in r if c != '.'})
    missing = [c for c in used if c not in palette]
    assert not missing, missing
    lines = [f'{c} {palette[c]}' for c in used]
    return '\n'.join(lines) + '\n\n' + '\n'.join(rows) + '\n'


# ---- the grow lamp ---------------------------------------------------------------------------------

LAMP_PAL = {
    'k': VER[0], 'v': VER[1], 'V': VER[2], 'w': VER[3], 'W': VER[4], 'X': VER[5],     # hood
    'c': CU[2], 'C': CU[4], 'D': CU[5],                                                # ring and rim
    'a': LEAF[1], 'b': LEAF[3], 'g': LEAF[5],                                          # flame edge, green halo
    's': STRAW[2], 'S': STRAW[3], 'y': BRASS[5], 'Y': '#fffbe6',                       # sunlight ramp, core
}
# The body: ring (rows 0-2), hood (3-6), rim (7). Left halves; the flame is added per frame.
LAMP_BODY = [
    '......cC',
    '.....c..',
    '......cD',
    '.....kVW',
    '....kVWX',
    '...kvVWX',
    '..kvVVWW',
    '..cCCCCD',
]
# The hanging flame, rows 8-15, one list per frame: an inverted fireball under the rim (rows 8-11, a
# white-hot core 'Y' in the sunlight ramp 'y'/'S'/'s', edged in the green 'g'/'b' and outlined in 'a')
# whose three tongues drip down and flicker, now and then letting a drop of light fall free.
_FLAME_BODY = ['..agsSyy', '..bgSyYY', '..bgSyYY', '..abgSyy']
_TONGUES = [
    ['...abgSS', '....a.gS', '.......g', '........'],
    ['...abgSS', '...ab.gS', '....a..S', '.......g'],
    ['...abgSs', '....ab.g', '.....g..', '.......S'],
    ['...abgSS', '....a.gS', '......gS', '.......g'],
    ['...abgSS', '...ab.gs', '....b..g', '........'],
    ['...abgSS', '....a.gS', '.......S', '....g...'],
    ['...abgSs', '....ab.S', '......g.', '.......g'],
    ['...abgSS', '...a..gS', '....g..g', '........'],
]
LAMP_FLAMES = [_FLAME_BODY + tongues for tongues in _TONGUES]

def lamp_frames():
    return [mirror(LAMP_BODY + flame) for flame in LAMP_FLAMES]



# ---- the garden plan -------------------------------------------------------------------------------

PLAN_PAL = {
    'k': CU[0], 'c': CU[2], 'C': CU[4], 'D': CU[5],                # rods
    'p': PARCH[2], 'q': PARCH[4], 'Q': PARCH[5],                   # paper (shadow to light)
    'i': VER[1], 'I': VER[3],                                       # verdigris ink
    'y': STRAW[3],                                                  # headlamps
    'g': LEAF[4], 'G': LEAF[5],                                     # the crop and the green light
}
# The engine's front elevation in ink outlines: the head with its skylight, the banks down the sides, the
# heart open to the front with the crop in its glowing pot over the green bed, a headlamp, the sump.
PLAN = mirror([
    '.kcCCCCC',
    '..pqqqqq',
    '..pQiiii',
    '..pQiQII',
    '..pQiiii',
    '..pQiQQQ',
    '..pQiQQg',
    '..pQiQgg',
    '..pQiQGG',
    '..pQyQDD',
    '..pQiGGG',
    '..pQiiii',
    '..pQiQiQ',
    '..pQiiii',
    '.kcCCCCD',
    '........',
])


# ---- the outlet ------------------------------------------------------------------------------------

OUTLET_PAL = {
    'k': CU[0], 'l': CU[1], 'c': CU[2], 'C': CU[3], 'D': CU[4], 'E': CU[5],   # cut copper casing
    'b': BRASS[1], 'B': BRASS[3], 'Y': BRASS[4],                                # the brass flange
    'n': INK, 'v': VER[1], 'V': VER[3],                                          # the port, verdigris
}
_OUTLET_HALF = [
    'kccccccc',
    'cDDDDDDD',
    'cDCCCCCC',
    'cDCbbbbb',
    'cDCbYBBB',
    'cDCbBbbn',
    'cDCbBnnn',
    'cDCbBnvv',
]
OUTLET = mirror(_OUTLET_HALF + [
    'cDCbBnvV',
    'cDCbBnnn',
    'cDCbBbbn',
    'cDCbYBBB',
    'cDCbbbbb',
    'cDCCCCCC',
    'cDDDDDDD',
    'lccccccc',
])


# ---- the core: a small pot with a green glow ------------------------------------------------------

POT_PAL = {
    'k': CU[0], 'l': CU[1], 'c': CU[2], 'C': CU[3], 'D': CU[4], 'E': CU[5],   # copper pot
    'v': VER[1], 'V': VER[2], 'w': VER[3],                                       # its verdigris band
    'Y': BRASS[4],                                                                # rivets
}
# The pot's side is 8 wide and 6 tall (1 texel a unit); the 16 x 16 texture holds it twice across and
# twice down, so any face of the body maps its own 8 x 6 (or 8 x 8) window.
_POT_TILE = [
    'DEEEEEED',
    'cDDDDDDc',
    'vVwYYwVv',
    'vVVVVVVv',
    'cCCCCCCc',
    'lccccccl',
    'kllllllk',
    'kkkkkkkk',
]
POT = [r + r for r in _POT_TILE] * 2

SOIL_PAL = {'a': '#2a1a10', 'b': '#3b2616', 'c': '#4f341e', 'd': '#664528', 'g': LEAF[3]}
_SOIL_TILE = [
    'bcbbcbbc',
    'cbdcbbcb',
    'bbcbbdcb',
    'cbbcgbbc',
    'bdcbbcbb',
    'cbbacbdb',
    'bcbbcbbc',
    'cbdbbcab',
]
SOIL = [r + r for r in _SOIL_TILE] * 2

# The rim's glow: 8 frames that breathe from a low green to a bright one and back (frametime 3); dark
# verdigris while the garden is unbuilt, teal once it stands.
_GLOW_RAMP = [LEAF[4], LEAF[5], '#e2ffb8', LEAF[5], LEAF[4], LEAF[3], LEAF[2], LEAF[3]]


def glow(colour, edge):
    """A 16 x 16 texture of one colour with a darker checker of texels, symmetric both ways."""
    rows = []
    for y in range(16):
        rows.append(''.join('b' if (min(x, 15 - x) + min(y, 15 - y)) % 4 == 0 else 'a' for x in range(16)))
    return {'a': colour, 'b': edge}, rows


# ---- models (1 texel per 1/16 unit) ------------------------------------------------------------------

EMISSIVE = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


def _box(frm, to, faces):
    return {'from': list(frm), 'to': list(to), 'faces': faces}


def _uv_faces(frm, to, tex, emissive=False, sides=('north', 'south', 'east', 'west', 'up', 'down')):
    """Faces whose UVs span the element's own size: 1 texel per unit, nothing stretched."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    size = {'north': (w, h), 'south': (w, h), 'east': (d, h), 'west': (d, h), 'up': (w, d), 'down': (w, d)}
    out = {}
    for s in sides:
        u, v = size[s]
        face = {'uv': [0, 0, u, v], 'texture': tex}
        if emissive:
            face['neoforge_data'] = dict(EMISSIVE)
        out[s] = face
    return out


# ---- the engine: the casing, the unformed core and the formed model --------------------------------

ENGINE_PAL = {
    'k': CU[0], 'l': CU[1], 'c': CU[2], 'C': CU[3], 'D': CU[4], 'E': CU[5],        # copper
    'b': BRASS[1], 'B': BRASS[3], 'Y': BRASS[4], 'W': BRASS[5],                      # brass
    'v': VER[1], 'V': VER[2], 'w': VER[3], 'X': VER[4],                               # verdigris
    'n': INK, 'g': GLASS[1], 'G': GLASS[3],
}
# Casing: riveted copper plates, the members' look while unformed and the formed engine's skin (8 x 8, tiled).
_CASING_TILE = ['EDDDDDDC', 'DCCCCCCl', 'DCYCCYCl', 'DCCCCCCl', 'DCCCCCCl', 'DCYCCYCl', 'DCCCCCCl', 'Clllllll']
CASING = [r + r for r in _CASING_TILE] * 2
# Cooling fins: verdigris ribs.
_FINS_TILE = ['XXXXXXXX', 'wwwwwwww', 'vvvvvvvv', 'nnnnnnnn', 'XXXXXXXX', 'wwwwwwww', 'vvvvvvvv', 'nnnnnnnn']
FINS = [r + r for r in _FINS_TILE] * 2
# Radiator grille: a brass frame round vertical slats.
GRILLE = mirror(['bBBBBBBB', 'BYYYYYYY', 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYlClClC',
                 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYlClClC', 'BYYYYYYY', 'bBBBBBBB'])
# Brass: pistons, caps, pipes, the belt.
_BRASS_TILE = ['WYYYYYYB', 'YBBBBBBb', 'YBBBBBBb', 'YBBBBBBb', 'YBBBBBBb', 'YBBBBBBb', 'YBBBBBBb', 'Bbbbbbbb']
BRASS_T = [r + r for r in _BRASS_TILE] * 2
# The flywheel and the pulley: a brass rim, copper spokes, a hub.
WHEEL = mirror(['.....bBB', '...bBYYY', '..bYYCCC', '.bYCClCC', '.BYCClCC', 'bYCClllC', 'BYCCCClC', 'BYClllCW',
                'BYClllCW', 'BYCCCClC', 'bYCClllC', '.BYCClCC', '.bYCClCC', '..bYYCCC', '...bBYYY', '.....bBB'])
# Headlamps: warm light (emissive in the model).
HEADLIGHT_PAL = {'a': BRASS[2], 'b': STRAW[2], 'c': STRAW[3], 'd': BRASS[5]}
HEADLIGHT = mirror(['aaaaaaaa', 'abbbbbbb', 'abcccccc', 'abcddddd', 'abcddddd', 'abcddddd', 'abcddddd', 'abcddddd',
                    'abcddddd', 'abcddddd', 'abcddddd', 'abcddddd', 'abcddddd', 'abcccccc', 'abbbbbbb', 'aaaaaaaa'])
# The unformed core's front: casing with a round window onto a sleeping pot (dark) or, once the engine
# stands, a teal one.
CORE_FRONT_PAL = dict(ENGINE_PAL, **{'t': TEAL[1], 'T': TEAL[2], 'u': TEAL[3], 'm': LEAF[3]})


def core_front(built):
    a, b, c = ('t', 'T', 'u') if built else ('n', 'g', 'G')
    half = ['EDDDDDDD', 'DCCCCCCC', 'DCYCCCCC', 'DCCCbBBB', 'DCCbB' + a * 3, 'DCCB' + a + b + b + b, 'DCCB' + a + b + c + c,
            'DCCB' + a + b + c + 'm', 'DCCB' + a + b + 'mm', 'DCCB' + a + b + 'CC', 'DCCB' + a + 'CCC', 'DCCbBCCC',
            'DCCCbBBB', 'DCYCCCCC', 'DCCCCCCC', 'Clllllll']
    return mirror(half)


def _split(lo, hi):
    """Cut [lo, hi] at the 16-unit grid so every piece maps inside one 16 x 16 texture."""
    cuts = [lo]
    g = (lo // 16 + 1) * 16
    while g < hi:
        cuts.append(g)
        g += 16
    cuts.append(hi)
    return list(zip(cuts, cuts[1:]))


def engine_boxes(frm, to, tex, emissive=False):
    """A box as elements of at most 16 units along each axis, UVs taken from the model coordinates modulo 16:
    1 texel per unit, textures tile seamlessly across the pieces."""
    out = []
    for x0, x1 in _split(frm[0], to[0]):
        for y0, y1 in _split(frm[1], to[1]):
            for z0, z1 in _split(frm[2], to[2]):
                bx, by, bz = (x0 // 16) * 16, (y0 // 16) * 16, (z0 // 16) * 16
                u = (x0 - bx, x1 - bx)
                v = (16 - (y1 - by), 16 - (y0 - by))
                w = (z0 - bz, z1 - bz)
                faces = {'north': [u[0], v[0], u[1], v[1]], 'south': [u[0], v[0], u[1], v[1]],
                         'east': [w[0], v[0], w[1], v[1]], 'west': [w[0], v[0], w[1], v[1]],
                         'up': [u[0], w[0], u[1], w[1]], 'down': [u[0], w[0], u[1], w[1]]}
                element = {'from': [x0, y0, z0], 'to': [x1, y1, z1], 'faces': {}}
                for side, uv in faces.items():
                    face = {'uv': uv, 'texture': tex}
                    if emissive:
                        face['neoforge_data'] = dict(EMISSIVE)
                    element['faces'][side] = face
                out.append(element)
    return out


def mirrored(frm, to):
    """The box and its mirror about x = 8 (the middle column's axis)."""
    return [(frm, to), ((16 - to[0], frm[1], frm[2]), (16 - frm[0], to[1], to[2]))]


def engine_model():
    """The formed engine over the whole 3 x 2 x 2 volume, for a core facing south at the front of the middle
    column: x -16..32, z -16..16 (the back row behind), y 0..32. Mirror-symmetric about x = 8."""
    parts = []

    def add(frm, to, tex, emissive=False, mirror_it=False, soil=False):
        for f, t in (mirrored(frm, to) if mirror_it else [(frm, to)]):
            for element in engine_boxes(f, t, tex, emissive):
                if soil:
                    element['faces']['up']['texture'] = '#soil'
                parts.append(element)

    add((-14, 0, -14), (30, 3, 14), '#casing')                          # sump
    add((-8, 3, -12), (24, 17, 8), '#casing')                           # engine block
    add((-14, 5, -10), (-8, 17, 6), '#fins', mirror_it=True)            # cylinder banks with fins
    add((-15, 17, -11), (-7, 20, 7), '#casing', mirror_it=True)         # valve covers
    for z in (-7, 1):                                                   # two pistons a side, capped
        add((-13, 20, z), (-9, 27, z + 4), '#brass', mirror_it=True)
        add((-14, 27, z - 1), (-8, 29, z + 5), '#brass', mirror_it=True)
    add((-6, 3, 8), (22, 15, 13), '#grille')                            # radiator grille
    add((-12, 5, 8), (-8, 9, 12), '#headlight', emissive=True, mirror_it=True)   # headlamps
    add((-6, 15, 8), (22, 16, 9), '#glow', emissive=True)               # the green light strip over the grille
    add((1, 3, -15), (15, 17, -12), '#wheel')                           # flywheel at the back
    add((6, 17, -14), (10, 23, -13), '#brass')                          # the belt up to the pulley
    add((4, 21, -15), (12, 29, -12), '#wheel')                          # pulley
    add((-12, 20, -14), (-10, 31, -12), '#brass', mirror_it=True)       # exhaust pipes
    add((3, 17, -5), (13, 24, 5), '#pot', soil=True)                    # the heart: a small pot on the block
    for frm, to in (((3, 24, 4), (13, 25, 5)), ((3, 24, -5), (13, 25, -4)), ((3, 24, -4), (4, 25, 4)), ((12, 24, -4), (13, 25, 4))):
        add(frm, to, '#glow', emissive=True)                            # its glowing rim
    textures = {'particle': 'entrelumen:block/terra_engine_casing', 'casing': 'entrelumen:block/terra_engine_casing',
                'fins': 'entrelumen:block/terra_engine_fins', 'grille': 'entrelumen:block/terra_engine_grille',
                'brass': 'entrelumen:block/terra_engine_brass', 'wheel': 'entrelumen:block/terra_engine_wheel',
                'headlight': 'entrelumen:block/terra_engine_headlight', 'pot': 'entrelumen:block/terra_garden_core_pot',
                'soil': 'entrelumen:block/terra_garden_core_soil', 'glow': 'entrelumen:block/terra_garden_core_glow'}
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': parts}


def core_unformed(built):
    return {'parent': 'minecraft:block/orientable_with_bottom', 'textures': {
        'front': 'entrelumen:block/terra_engine_core_front' + ('_built' if built else ''),
        'side': 'entrelumen:block/terra_engine_casing', 'top': 'entrelumen:block/terra_engine_casing',
        'bottom': 'entrelumen:block/terra_engine_casing'}}


# ---- the Terralight crystal, its shard, its grounding rod and the lamp placed as a block -----------

# Terralight green: a cool emerald-mint, not the leaf green of the lamp's halo nor any luminosity's ramp.
MINT = ['#0c3326', '#185a40', '#2a8a5e', '#4cc085', '#94f0bb', '#e4fff0']
# Crystal stages as thin spikes (Elias: «más finito»): (column offset from the axis, height), mirrored.
_SPIKES = [
    [(0, 5), (2, 3)],
    [(0, 8), (2, 5), (4, 3)],
    [(0, 11), (2, 8), (4, 5), (6, 3)],
    [(0, 15), (2, 11), (4, 8), (6, 5)],
]


def crystal_stage(stage):
    """A cross-model texture: thin needles rising from the bottom, the tallest on the axis (two texels wide),
    the others one texel wide and leaning out a texel every three rows; each ends in a bright tip."""
    grid = [['.'] * 16 for _ in range(16)]
    for offset, height in _SPIKES[stage]:
        for side in ((-1, 1) if offset else (0,)):
            for k in range(height):
                y = 15 - k
                tip = k >= height - 2
                c = ('f' if k == height - 1 else 'e') if tip else ('b' if k < 2 else ('d' if side <= 0 else 'c'))
                if side == 0:
                    grid[y][7] = c if tip else ('d' if k >= 2 else 'b')
                    grid[y][8] = c if tip else ('c' if k >= 2 else 'b')
                else:
                    lean = k // 3
                    x = (7 - offset - lean) if side < 0 else (8 + offset + lean)
                    if 0 <= x < 16:
                        grid[y][x] = c
    return [''.join(r) for r in grid]


CRYSTAL_PAL = {'a': MINT[0], 'b': MINT[1], 'c': MINT[2], 'd': MINT[3], 'e': MINT[4], 'f': MINT[5]}

# The shard, 8 frames in the luminosities' language (dark outline, a ramp, a white-hot glint that runs up
# its facets and a spark that blinks beside it), upright and mirrored in every frame.
_SHARD = [
    '.......a',
    '......ab',
    '......ac',
    '.....abd',
    '.....acd',
    '....abcd',
    '....acdd',
    '...abcdd',
    '...acddd',
    '...abcdd',
    '....acdd',
    '....abcd',
    '.....acd',
    '.....abc',
    '......ab',
    '.......a',
]


def shard_frames():
    frames = []
    for f in range(8):
        rows = [list(r) for r in _SHARD]
        band = 13 - f * 2 if f < 7 else None          # the glint climbs two rows a frame, then rests
        if band is not None:
            for y in (band, band - 1):
                if 0 <= y < 16:
                    for x in range(8):
                        if rows[y][x] in 'cd':
                            rows[y][x] = 'e' if rows[y][x] == 'c' else 'f'
        if f in (2, 6):                                  # a spark beside the shard
            rows[3][2] = 'e'
        if f == 4:
            rows[11][1] = 'e'
        frames.append(mirror([''.join(r) for r in rows]))
    return frames


SHARD_PAL = {'a': MINT[0], 'b': MINT[1], 'c': MINT[2], 'd': MINT[3], 'e': MINT[4], 'f': MINT[5]}

# The grounding rod: one copper stick, 2 texels wide, with bronze collars; only columns 7-8 are mapped.
ROD_PAL = {'k': CU[1], 'c': CU[3], 'C': CU[4], 'D': CU[5], 'b': BRASS[2], 'B': BRASS[4]}
_ROD_COLUMN = ['DC', 'bB', 'Cc', 'Cc', 'Cc', 'bB', 'Cc', 'Cc', 'Cc', 'Cc', 'bB', 'Cc', 'Cc', 'Cc', 'bB', 'ck']


def rod_rows():
    return ['.......' + c + '.......' for c in _ROD_COLUMN]


# The lamp as a block: a verdigris hood and a flame that breathes (4 frames).
HOOD_PAL = {'k': VER[0], 'v': VER[1], 'V': VER[2], 'w': VER[3], 'W': VER[4]}
_HOOD_TILE = ['WWWWWWWW', 'wwwwwwww', 'wVwVVwVw', 'VVVVVVVV', 'vVvvvvVv', 'vvvvvvvv', 'kvkvvkvk', 'kkkkkkkk']
HOOD = [r + r for r in _HOOD_TILE] * 2
_FLAME_RAMP = [(LEAF[4], STRAW[3]), (LEAF[5], BRASS[5]), ('#e2ffb8', '#fffbe6'), (LEAF[5], BRASS[5])]


def lamp_flame(frame):
    edge, core = _FLAME_RAMP[frame]
    rows = []
    for y in range(16):
        rows.append(''.join('b' if max(abs(x - 7.5), abs(y - 7.5)) < 3 else 'a' for x in range(16)))
    return {'a': edge, 'b': core}, rows


def rod_model():
    frm, to = (7, 0, 7), (9, 16, 9)
    faces = {s: {'uv': [7, 0, 9, 16], 'texture': '#rod'} for s in ('north', 'south', 'east', 'west')}
    faces['up'] = {'uv': [7, 0, 9, 2], 'texture': '#rod'}
    faces['down'] = {'uv': [7, 14, 9, 16], 'texture': '#rod'}
    return {'parent': 'minecraft:block/block', 'textures': {'particle': 'entrelumen:block/terralight_grounding_rod',
            'rod': 'entrelumen:block/terralight_grounding_rod'}, 'elements': [_box(frm, to, faces)]}


def lamp_block_model():
    elements = [_box((7, 13, 7), (9, 16, 9), _uv_faces((7, 13, 7), (9, 16, 9), '#hood')),
                _box((4, 8, 4), (12, 13, 12), _uv_faces((4, 8, 4), (12, 13, 12), '#hood')),
                _box((5, 3, 5), (11, 8, 11), _uv_faces((5, 3, 5), (11, 8, 11), '#flame', emissive=True))]
    return {'parent': 'minecraft:block/block', 'textures': {'particle': 'entrelumen:block/terra_grow_lamp_hood',
            'hood': 'entrelumen:block/terra_grow_lamp_hood', 'flame': 'entrelumen:block/terra_grow_lamp_flame'},
            'elements': elements}


def crystal_model(stage):
    return {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout',
            'textures': {'cross': f'entrelumen:block/terralight_crystal_{stage}'}}


def models():
    """Block models by name (art/models/block/<name>.json) and the core's item model."""
    out = {'terra_garden_core': core_unformed(False), 'terra_garden_core_built': core_unformed(True),
           'terra_garden_core_formed': engine_model(),
           'terralight_grounding_rod': rod_model(), 'terra_grow_lamp': lamp_block_model()}
    for stage in range(4):
        out[f'terralight_crystal_{stage}'] = crystal_model(stage)
    return out


def grids():
    """Every grid this script owns: relative path under art/grids -> text."""
    out = {}
    for i, frame in enumerate(lamp_frames()):
        out[f'item/terra_grow_lamp__f{i}.txt'] = grid_text(LAMP_PAL, frame)
    out['item/terra_garden_plan.txt'] = grid_text(PLAN_PAL, PLAN)
    out['block/terra_garden_outlet.txt'] = grid_text(OUTLET_PAL, OUTLET)
    out['block/terra_garden_core_pot.txt'] = grid_text(POT_PAL, POT)
    out['block/terra_garden_core_soil.txt'] = grid_text(SOIL_PAL, SOIL)
    for i, colour in enumerate(_GLOW_RAMP):
        pal, rows = glow(colour, LEAF[1] if i in (6, 5) else LEAF[2])
        out[f'block/terra_garden_core_glow__f{i}.txt'] = grid_text(pal, rows)
    out['block/terra_engine_casing.txt'] = grid_text(ENGINE_PAL, CASING)
    out['block/terra_engine_fins.txt'] = grid_text(ENGINE_PAL, FINS)
    out['block/terra_engine_grille.txt'] = grid_text(ENGINE_PAL, GRILLE)
    out['block/terra_engine_brass.txt'] = grid_text(ENGINE_PAL, BRASS_T)
    out['block/terra_engine_wheel.txt'] = grid_text(ENGINE_PAL, WHEEL)
    out['block/terra_engine_headlight.txt'] = grid_text(HEADLIGHT_PAL, HEADLIGHT)
    out['block/terra_engine_core_front.txt'] = grid_text(CORE_FRONT_PAL, core_front(False))
    out['block/terra_engine_core_front_built.txt'] = grid_text(CORE_FRONT_PAL, core_front(True))
    for stage in range(4):
        out[f'block/terralight_crystal_{stage}.txt'] = grid_text(CRYSTAL_PAL, crystal_stage(stage))
    for i, frame in enumerate(shard_frames()):
        out[f'item/terralight_shard__f{i}.txt'] = grid_text(SHARD_PAL, frame)
    out['block/terralight_grounding_rod.txt'] = grid_text(ROD_PAL, rod_rows())
    out['block/terra_grow_lamp_hood.txt'] = grid_text(HOOD_PAL, HOOD)
    for i in range(4):
        pal, rows = lamp_flame(i)
        out[f'block/terra_grow_lamp_flame__f{i}.txt'] = grid_text(pal, rows)
    return out


def main():
    import json
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    files = {os.path.join(GRIDS, rel): text for rel, text in grids().items()}
    model_dir = os.path.join(ROOT, 'art', 'models', 'block')
    for name, model in models().items():
        files[os.path.join(model_dir, name + '.json')] = json.dumps(model, indent=1) + '\n'
    stale = []
    for path, text in files.items():
        if args.check:
            if not os.path.exists(path) or open(path, encoding='utf-8').read() != text:
                stale.append(path)
            continue
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
    if args.check:
        assert not stale, f'stale: {stale}'
        print(f'PASS: {len(files)} Terra garden grids and models match draw_terra_garden.py')
    else:
        print(f'Wrote {len(files)} grids and models')


if __name__ == '__main__':
    main()
