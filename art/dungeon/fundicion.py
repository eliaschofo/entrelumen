"""Fundición, floor III of the Envés: a dead foundry of blackstone and basalt. Lava burns inside
piers and hearths behind iron bars, crucibles of lava hang from chains, lanterns on chains light the
aisles.

Rules (Elias, 27/9): lava must never be reachable by accident. Here every lava block is sealed on
all six faces by stone or by an iron-bar grille that spans the opening (kit.validate refuses
anything else), so neither a player nor an item or a knocked-back mob can reach it; the crucibles are
lava cauldrons hung five blocks up. All art is D4 around the cell centre; only the doors and the
stairwell break it. Floors are on the slab (kit DECK = 0), rooms nine blocks tall.

References inspected before drawing (hook of AGENTS.md), rendered with voxkit.load_nbt and
voxrender from the pinned JARs:
- L_Ender's Cataclysm 1.21.1-3.33, `data/cataclysm/structure/soul_black_smith_4.nbt` and `_7.nbt`
  (the Netherite Monstrosity's forge): blackstone halls whose piers carry lava in vertical slots
  behind iron-bar grilles, polished basalt as beams and trims, chains hanging from the vault. Taken:
  lava inside the piers seen through bars, basalt against blackstone, chains.
- vanilla `data/minecraft/structure/bastion/treasure/bases/lava_basin.nbt`: blackstone islands in a
  lava lake; taken only as the warning of how quickly open lava makes a floor unplayable.
- Cataclysm `burning_arena2.nbt` (Ignis's arena): basalt columns as cover in a fire fight.
What is ours: the lava-core piers with four barred windows, the hearths in the corner masses of the
cross, the hanging crucibles, the room plans.
"""
import kit
from kit import AIR, B, C, CEIL_Y, octant_body, read_quadrant, stairs

NAME = 'fundicion'
DECK = 0
LANTERN = B('lantern', hanging=True)
CHAIN = B('chain', axis='y')
P = dict(
    base=B('polished_blackstone_bricks'),
    floor=B('smooth_basalt'),
    floor2=B('polished_blackstone_bricks'),
    chiseled=B('chiseled_polished_blackstone'),
    gilded=B('gilded_blackstone'),
    dark=B('smooth_basalt'),
    wall=B('polished_blackstone_bricks'),
    plinth=B('polished_blackstone'),
    course=B('smooth_basalt'),
    cracked=B('cracked_polished_blackstone_bricks'),
    basalt=B('polished_basalt', axis='y'),
    pillar=B('polished_basalt', axis='y'),
    capital=B('chiseled_polished_blackstone'),
    landing=B('polished_blackstone'),
    step='minecraft:polished_blackstone_brick_stairs',
    corbel='minecraft:polished_blackstone_brick_stairs',
    well=B('polished_blackstone'),
    threshold=B('polished_blackstone'),
    lintel=B('chiseled_polished_blackstone'),
    jamb=B('polished_basalt', axis='y'),
    door_lamp=B('shroomlight'),
    ceil=B('polished_blackstone_bricks'),
    bars=B('iron_bars'),
)
COURSE_Y = 5


# ------------------------------------------------------------------ columns of blocks
def ceil(s):
    s[CEIL_Y] = P['ceil']
    s[CEIL_Y + 1] = P['ceil']
    return s


def wall(a, b):
    s = {0: P['base'], 1: P['plinth']}
    for y in range(2, CEIL_Y):
        s[y] = P['wall']
    s[COURSE_Y] = P['course']
    return ceil(s)


def floor(block=None):
    s = {0: block or P['floor']}
    for y in range(1, CEIL_Y):
        s[y] = AIR
    return ceil(s)


def hanging(block, low, top=CEIL_Y - 1, under=None):
    """Floor with something hung on a chain: `block` at y=low, chain up to the ceiling."""
    s = floor(under)
    s[low] = block
    for y in range(low + 1, top + 1):
        s[y] = CHAIN
    return s


def pier(a, b):
    """A solid pier block, basalt at the corners of the course."""
    s = wall(a, b)
    s[1] = P['basalt']
    return s


def core(height=6):
    """The lava core of a pier: lava from the floor up to `height`, stone above."""
    s = wall(0, 0)
    for y in range(1, height + 1):
        s[y] = B('lava')
    return s


def grille(height=6):
    """A barred window onto a lava core or hearth: bars from the floor up, stone above."""
    s = wall(0, 0)
    for y in range(1, height + 1):
        s[y] = P['bars']
    s[height + 1] = P['capital']
    return s


def column():
    s = {0: P['base']}
    for y in range(1, CEIL_Y):
        s[y] = P['basalt']
    s[1] = P['plinth']
    s[CEIL_Y - 1] = P['capital']
    return ceil(s)


LEGEND = {
    '#': wall,
    '.': lambda a, b: floor(),
    ',': lambda a, b: floor(P['floor2']),
    'x': lambda a, b: floor(P['chiseled']),
    'g': lambda a, b: floor(P['gilded']),
    'b': lambda a, b: floor(B('polished_basalt', axis='y')),
    'P': pier,
    'L': lambda a, b: core(6),
    '|': lambda a, b: grille(6),
    'H': lambda a, b: core(3),
    'h': lambda a, b: grille(3),
    'B': lambda a, b: column(),
    'l': lambda a, b: hanging(LANTERN, 6),
    'C': lambda a, b: hanging(B('lava_cauldron'), 5, under=P['chiseled']),
}


def from_map(text, reach=8):
    q = read_quadrant(text)

    def col(a, b):
        if a > reach:
            return wall(a, b)
        return LEGEND[q[(a, b)]](a, b)
    return octant_body(col)


# ------------------------------------------------------------------ passes
def corbels(v):
    top = {(x, z): v.get((x, CEIL_Y - 1, z)) for x in range(19) for z in range(19)}
    for x in range(1, 18):
        for z in range(1, 18):
            if top[(x, z)] != AIR:
                continue
            walls = [f for f, (dx, dz) in kit.FACE.items() if kit.full(top[(x + dx, z + dz)])]
            if len(walls) == 1:
                v[(x, CEIL_Y - 1, z)] = stairs(P['corbel'], kit.OPP[walls[0]], half='top')
            elif len(walls) >= 2:
                v[(x, CEIL_Y - 1, z)] = P['capital']


def wall_lanterns(v, offsets=(4, 8)):
    """Lanterns on chains a block in from the straight outer walls, where the room reaches them."""
    for t in offsets:
        if all(v.get((C + 8, y, C + t)) == AIR for y in range(1, CEIL_Y)):
            kit.sym_put(v, 8, 6, t, LANTERN)
            for y in range(7, CEIL_Y):
                kit.sym_put(v, 8, y, t, CHAIN)


# ------------------------------------------------------------------ rooms
# Quadrants: rows are dz 0..8, columns dx 0..8 from the centre; mirrored across the diagonal.
NAVE = """
x,..,...,
,x..,...,
..b.,...,
...b,...,
,,,,P|P.,
....|L|.,
....P|P.,
........,
,,,,,,,,,
"""                                  # the forge nave: four piers with a lava core behind barred windows
HORNOS = """
gx,..,.x.
x,...,.x.
,.b..,.x.
...l.,.x.
....,,#hh
,,,,,.#HH
....#####
xxxxhH###
....hH###
"""                                  # the furnaces: a cross whose corner masses hold barred hearths
CRISOL = """
CC,..x...
CC,..x...
,,x..x...
....B....
...B.....
xxx.....#
.......##
......###
.....####
"""                                  # the crucible: lava cauldrons hung over the middle, a ring of basalt
MARTINETE = """
gx,..,...
x,x..,...
,x,x.,...
..x,.,...
....,,...
,,,,,P|P.
.....|L|.
.....P|P.
.........
"""                                  # the drop-hammer hall (guard): open floor, piers in the corners
SALA = MARTINETE


def kind_of(role, variant):
    if role in ('quiet', 'fight'):
        return variant % 3
    return {'guard': 'guard', 'shrine': 2, 'seal': 1, 'vault': 0, 'start': 0, 'exit': 0, 'vestibule': 0,
            'portal': 2, 'arena': 'arena', 'arena_center': 'arena'}[role]


def room(role, variant):
    kind = kind_of(role, variant)
    v = from_map({0: NAVE, 1: HORNOS, 2: CRISOL, 'guard': MARTINETE, 'arena': SALA}[kind])
    if kind == 0 and role not in ('start', 'exit', 'vestibule'):
        for (dx, dz) in ((0, 0), (2, 2)):              # crucibles over the nave
            kit.sym_put(v, dx, 6, dz, B('lava_cauldron'))
            for y in range(7, CEIL_Y):
                kit.sym_put(v, dx, y, dz, CHAIN)
    if role in ('shrine', 'seal', 'portal'):
        clear_centre(v)
        dais(v)
        kit.sym_put(v, 2, 6, 2, B('lava_cauldron'))       # four crucibles keep watch over the altar
        for y in range(7, CEIL_Y):
            kit.sym_put(v, 2, y, 2, CHAIN)
    if kind in ('guard', 'arena'):                     # the hammer: an iron weight on chains over the floor
        kit.sym_put(v, 0, CEIL_Y - 1, 0, B('iron_block'))
        kit.sym_put(v, 0, CEIL_Y - 2, 0, B('iron_block'))
        kit.sym_put(v, 1, CEIL_Y - 1, 1, CHAIN)
        kit.sym_put(v, 3, 6, 3, LANTERN)
        for y in range(7, CEIL_Y):
            kit.sym_put(v, 3, y, 3, CHAIN)
    if kind == 2:                                      # the octagon: embers in the corner masses, lanterns round
        kit.sym_put(v, 7, 2, 6, B('lava_cauldron'))
        kit.sym_put(v, 5, 6, 2, LANTERN)
        for y in range(7, CEIL_Y):
            kit.sym_put(v, 5, y, 2, CHAIN)
    wall_lanterns(v)
    corbels(v)
    return v


def clear_centre(v):
    """Take the crucibles off the middle (a dais or the stairwell goes there)."""
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            v[(C + dx, 0, C + dz)] = P['chiseled']
            for y in range(1, CEIL_Y):
                v[(C + dx, y, C + dz)] = AIR


def dais(v):
    """A 3x3 dais of chiseled blackstone one step up, with stairs on its four sides."""
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            v[(C + dx, 1, C + dz)] = P['gilded'] if (dx, dz) == (0, 0) else P['chiseled']
    kit.sym_put(v, 2, 1, 0, stairs(P['step'], 'west'))
    kit.sym_put(v, 2, 1, 1, stairs(P['step'], 'west'))


def corridor(variant):
    """An east-west casting gallery: basalt ribs every four blocks, barred lava slots in the side
    walls between them, a chiseled strip down the middle; variant 2 hangs lanterns instead of slots."""
    v = {}
    for x in range(0, 19):
        for z in range(0, 19):
            t = abs(z - C)
            along = abs(x - C)
            if t >= 3 or x in (0, 18):
                s = wall(t, along)
            else:
                s = floor(P['chiseled'] if t == 0 and along % 2 == 0 else (P['floor2'] if t == 0 else P['floor']))
            for y, st in s.items():
                v[(x, y, z)] = st
    for x in range(1, 18):
        along = abs(x - C)
        if along % 4 == 0:                              # a basalt rib
            for z in range(C - 2, C + 3):
                for y in range(6, CEIL_Y):
                    v[(x, y, z)] = P['course'] if y == 6 else P['wall']
            for z in (C - 3, C + 3):
                for y in range(1, CEIL_Y):
                    v[(x, y, z)] = P['basalt']
        elif along % 4 == 2:
            if variant % 3 == 2:
                v[(x, 5, C)] = LANTERN
                for y in range(6, CEIL_Y):
                    v[(x, y, C)] = CHAIN
            else:
                for z, zz in ((C - 3, C - 4), (C + 3, C + 4)):
                    for y in range(1, 4):
                        v[(x, y, z)] = P['bars']
                        v[(x, y, zz)] = B('lava')
                    v[(x, 4, z)] = P['capital']
    corbels(v)
    return v


def rot4(dx, dz):
    return [(C + dx, C + dz), (C - dz, C + dx), (C - dx, C - dz), (C + dz, C - dx)]


SPAWN = {0: 7, 1: 3, 2: 6, 'guard': 4, 'arena': 4}
CHEST = {0: (7, 3), 1: (3, 7), 2: (6, 3), 'guard': (8, 3), 'arena': (8, 3)}


def spots(role, variant, corridor):
    kind = kind_of(role, variant)
    d = SPAWN[kind]
    return {'encounter': [(C - d, C - d), (C + d, C - d), (C + d, C + d), (C - d, C + d)],
            'arrival': [(C - 3, C - 3)],
            'chest': rot4(*CHEST[kind]),
            'boss_chest': [(C - 3, C - 3), (C + 3, C - 3)],
            'portal': [(C + 7, C)]}
