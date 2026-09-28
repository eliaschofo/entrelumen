"""El Eclipse, floor V of the Envés: the White Wither's hall. Obsidian and polished blackstone
banded with gold, crying obsidian in the joints, and in the middle of the arena a dark sun: a black
disc with a burning corona, hung over the place where the boss wakes.

The arena is 3x3 cells opened into one hall (kit.open_sides). Each outer cell carries one pillar of
obsidian at its centre, so eight pillars ring the middle cell like the spikes round the End's
fountain: a long, clear floor for the White Wither's telegraphed charge (it levitates low and
slides), and cover from its skulls a few steps away from anywhere. Gold lines seam the cells into a
great sundial. The floor is dry and flat; the light comes from the corona, the pillar capitals and
lanterns along the walls. Fewer ordinary rooms: floor V only draws start, the approach, the
antechamber (guard), the arena and the portal, but every role keeps its fifteen masks so any
datapack order of tilesets still works. D4 everywhere but the doors and the stairwell.

References inspected before drawing (hook of AGENTS.md), rendered with voxkit.load_nbt and
voxrender:
- L_Ender's Cataclysm 1.21.1-3.33, `data/cataclysm/structure/ruined_citadel5.nbt` (the Ender
  Guardian's arena): a round floor with a radial star of obsidian and crying obsidian at its centre.
  Taken: the boss's place marked by a radial emblem the whole floor points to.
- Cataclysm `burning_arena2.nbt` (Ignis): basalt columns grouped as cover round an open floor.
- vanilla `data/minecraft/structure/ruined_portal/giant_portal_1.nbt`: obsidian with crying obsidian
  and a single block of gold as the accent. Taken: gold as a rare, exact accent on obsidian.
- vanilla `bastion/treasure/bases/centers/center_0.nbt`: a crown of gold blocks on blackstone round a
  dark middle. Taken: the ring of gold round a dark centre, here the eclipse.
What is ours: the dark sun and its corona, the pillar ring, the sundial seams, the room plans.
"""
import kit
from kit import AIR, B, C, CEIL_Y, octant_body, read_quadrant, stairs

NAME = 'eclipse'
DECK = 0
LANTERN = B('lantern', hanging=True)
SOUL = B('soul_lantern', hanging=True)
CHAIN = B('chain', axis='y')
P = dict(
    base=B('polished_blackstone'),
    floor=B('polished_blackstone'),
    bricks=B('polished_blackstone_bricks'),
    gold=B('gold_block'),
    crying=B('crying_obsidian'),
    obsidian=B('obsidian'),
    dark=B('black_concrete'),
    chiseled=B('chiseled_polished_blackstone'),
    gilded=B('gilded_blackstone'),
    wall=B('obsidian'),
    plinth=B('polished_blackstone_bricks'),
    course=B('gold_block'),
    pillar=B('obsidian'),
    capital=B('chiseled_polished_blackstone'),
    lamp=B('glowstone'),
    landing=B('polished_blackstone'),
    step='minecraft:polished_blackstone_brick_stairs',
    corbel='minecraft:polished_blackstone_brick_stairs',
    well=B('polished_blackstone'),
    threshold=B('polished_blackstone'),
    lintel=B('gold_block'),
    jamb=B('crying_obsidian'),
    door_lamp=B('glowstone'),
    ceil=B('polished_blackstone_bricks'),
)
COURSE_Y = 6


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
    s[CEIL_Y - 1] = P['bricks']
    return ceil(s)


def floor(block=None):
    s = {0: block or P['floor']}
    for y in range(1, CEIL_Y):
        s[y] = AIR
    return ceil(s)


def pillar(a, b):
    """An obsidian pillar: gold plinth and band, crying obsidian in the corners, a ring of glowstone
    under a chiseled capital, so the pillar lights the floor round it."""
    s = {0: P['base'], 1: P['gold']}
    for y in range(2, CEIL_Y):
        s[y] = P['obsidian']
    if a == b and a > 0:
        for y in (2, 3, 4):
            s[y] = P['crying']
    s[5] = P['gold']
    if a > 0:
        s[7] = P['lamp']
        s[8] = P['gold']
    s[CEIL_Y - 1] = P['capital']
    return ceil(s)


LEGEND = {
    '#': wall,
    ',': lambda a, b: floor(),
    ':': lambda a, b: floor(P['bricks']),
    'G': lambda a, b: floor(P['gold']),
    'g': lambda a, b: floor(P['gilded']),
    'c': lambda a, b: floor(P['crying']),
    'o': lambda a, b: floor(P['obsidian']),
    'b': lambda a, b: floor(P['dark']),
    'x': lambda a, b: floor(P['chiseled']),
    'k': lambda a, b: floor(P['bricks']),
    'O': pillar,
    'L': lambda a, b: floor(P['lamp']),
    'a': lambda a, b: alcove(a, b),
}


def alcove(a, b):
    """A recess in a thick wall: gold floor, crying obsidian at the back, a lintel and a lamp over it."""
    s = floor(P['gold'])
    for y in range(5, CEIL_Y):
        s[y] = P['obsidian']
    s[5] = P['gold']
    s[6] = P['lamp']
    return s


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


def sconces(v, offsets=(4, 7)):
    """Glowstone set in the straight outer walls at head height, framed in gold."""
    for t in offsets:
        x, z = C + 9, C + t
        if v.get((x - 1, 3, z)) == AIR and kit.full(v.get((x, 3, z))):
            kit.sym_put(v, 9, 3, t, P['lamp'])
            kit.sym_put(v, 9, 2, t, P['gold'])
            kit.sym_put(v, 9, 4, t, P['gold'])


def hang(v, dx, dz, block, low):
    kit.sym_put(v, dx, low, dz, block)
    for y in range(low + 1, CEIL_Y):
        kit.sym_put(v, dx, y, dz, CHAIN)


def dark_sun(v, y=8):
    """The eclipse, hung over the arena's middle: a black disc three blocks across its radius, a ring
    of glowstone for the corona, end rods for its rays, a gold rim above."""
    for dx in range(0, 7):
        for dz in range(0, dx + 1):
            r2 = dx * dx + dz * dz
            if r2 <= 10:
                kit.sym_put(v, dx, y, dz, P['dark'])
                kit.sym_put(v, dx, y + 1, dz, P['dark'] if r2 <= 4 else P['obsidian'])
            elif r2 <= 18:
                kit.sym_put(v, dx, y, dz, P['lamp'])
                kit.sym_put(v, dx, y + 1, dz, P['gold'])
    for (dx, dz) in ((5, 0), (4, 3)):                    # rays leaving the corona
        f = 'east'
        kit.sym_put(v, dx, y, dz, B('end_rod', facing=f) if dz == 0 else B('end_rod', facing='down'))
    kit.sym_put(v, 0, y - 1, 0, B('end_rod', facing='down'))


# ------------------------------------------------------------------ rooms
# Quadrants: rows are dz 0..8, columns dx 0..8 from the centre; mirrored across the diagonal.
ARENA = """
OOGGGGGLG
OO,,,,,,,
G,c,,,,,,
G,,,,,,,,
G,,,:,,,,
G,,,,,,,,
G,,,,,,,,
L,,,,,,L,
G,,,,,,,,
"""                                  # an arena cell: its pillar, gold lines that run on from cell to cell
SOL = """
bbbbL,GLG
bbbbg,,,,
bbbg,,,,,
bbgL,c,,,
Lg,,c,,,,
,,,c,,,,,
G,,,,,,,,
L,,,,,,L,
G,,,,,,,,
"""                                  # the arena's middle: the dark sun inlaid under the hanging one
ANTESALA = """
gGGLGGGG#
G,,,,,,,#
G,,,,,,,#
L,,,,,,,a
G,,,O,,,a
G,,,,,,,a
G,,,,,,,#
G,,,,,,L#
###aaa###
"""                                  # the antechamber (guard): alcoves where the echoes wait
OCASO = """
gG,,:,,,,
G,,,:,,,,
,,c,:,,,,
,,,,:,,,,
::::L,,,,
,,,,,OO,,
,,,,,OO,,
,,,,,,,,,
,,,,,,,,,
"""                                  # the dusk hall: four gold-banded pillars round a gilded middle
ROTONDA = """
bbg,c,,,,
bbg,,,,,,
ggc,,,,,,
,,,,,,,,,
c,,,:,,,,
,,,,,,,,#
,,,,,,,##
,,,,,,###
,,,,,####
"""                                  # the rotunda: a small eclipse inlaid in an octagon
CLAUSTRO = """
gG,,,:,L,
G,,,,:,,,
,,x,,:,,,
,,,O,:,,,
,,,,L####
::::#####
,,,,#####
L,,,#####
,,,,#####
"""                                  # the cloister: a cross of processional arms, gold at the crossing


def kind_of(role, variant):
    if role in ('quiet', 'fight'):
        return variant % 3
    return {'guard': 'guard', 'shrine': 1, 'seal': 2, 'vault': 1, 'start': 1, 'exit': 1, 'vestibule': 1,
            'portal': 'portal', 'arena': 'arena', 'arena_center': 'sun'}[role]


def room(role, variant):
    kind = kind_of(role, variant)
    text = {0: OCASO, 1: ROTONDA, 2: CLAUSTRO, 'portal': ROTONDA, 'arena': ARENA, 'sun': SOL,
            'guard': ANTESALA}[kind]
    v = from_map(text)
    if kind == 'sun':
        dark_sun(v)
    if kind == 0 and role != 'guard':
        hang(v, 0, 0, LANTERN, 6)
    if kind in (1, 'portal') and role not in ('start', 'exit', 'vestibule'):
        ring_of_lanterns(v)
    if role in ('shrine', 'seal', 'portal'):
        dais(v)
    if kind == 'portal':
        arch(v)
    if kind in ('arena', 'sun'):
        hang(v, 5, 5, LANTERN, 6)
    if kind == 'guard':
        hang(v, 5, 2, LANTERN, 6)
    if kind == 2:
        hang(v, 6, 0, LANTERN, 6)
    if kind in (1, 'portal') and role in ('start', 'exit', 'vestibule'):
        hang(v, 4, 2, LANTERN, 6)
    sconces(v)
    corbels(v)
    return v


def ring_of_lanterns(v):
    hang(v, 4, 2, LANTERN, 6)


def dais(v):
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            v[(C + dx, 0, C + dz)] = P['obsidian']
            v[(C + dx, 1, C + dz)] = P['gold'] if (dx, dz) == (0, 0) else P['bricks']
            for y in range(2, CEIL_Y - 1):
                v[(C + dx, y, C + dz)] = AIR
    kit.sym_put(v, 2, 1, 0, stairs(P['step'], 'west'))
    kit.sym_put(v, 2, 1, 1, stairs(P['step'], 'west'))


def arch(v):
    """Four gold-and-obsidian posts round the portal's dais, joined by a lintel ring at y 6."""
    for y in range(1, 6):
        kit.sym_put(v, 3, y, 3, P['obsidian'] if y != 3 else P['crying'])
    for dx in range(0, 4):
        kit.sym_put(v, 3, 6, dx, P['gold'])


def corridor(variant):
    """An east-west processional way: gold runner, obsidian walls with glowstone sconces, gold ribs."""
    v = {}
    for x in range(0, 19):
        for z in range(0, 19):
            t = abs(z - C)
            along = abs(x - C)
            if t >= 3 or x in (0, 18):
                s = wall(t, along)
            else:
                s = floor(P['gold'] if (t == 0 and along % 4 == 0) else (P['obsidian'] if t == 0 else P['floor']))
            for y, st in s.items():
                v[(x, y, z)] = st
    for x in range(1, 18):
        along = abs(x - C)
        if along % 4 == 0:
            for z in range(C - 2, C + 3):
                v[(x, CEIL_Y - 1, z)] = P['gold']
                v[(x, CEIL_Y - 2, z)] = P['obsidian']
            for z in (C - 3, C + 3):
                for y in range(1, CEIL_Y):
                    v[(x, y, z)] = P['obsidian'] if y not in (1, 5) else P['gold']
        elif along % 4 == 2:
            for z in (C - 3, C + 3):
                v[(x, 3, z)] = P['lamp']
                v[(x, 2, z)] = P['gold']
                v[(x, 4, z)] = P['gold'] if variant % 3 == 1 else P['crying']
            if variant % 3 == 2:
                v[(x, 6, C)] = LANTERN
                for y in range(7, CEIL_Y):
                    v[(x, y, C)] = CHAIN
    corbels(v)
    return v


def rot4(dx, dz):
    return [(C + dx, C + dz), (C - dz, C + dx), (C - dx, C - dz), (C + dz, C - dx)]


SPAWN = {0: 3, 1: 5, 2: 2, 'portal': 5, 'arena': 4, 'sun': 4}
CHEST = {0: (7, 3), 1: (6, 3), 2: (3, 7), 'portal': (6, 3), 'arena': (6, 3), 'sun': (6, 3), 'guard': (6, 3)}


def spots(role, variant, corridor):
    kind = kind_of(role, variant)
    if kind == 'guard':                                   # the echoes wait in four of the alcoves
        enc = rot4(8, 4)
    else:
        d = SPAWN[kind]
        enc = [(C - d, C - d), (C + d, C - d), (C + d, C + d), (C - d, C + d)]
    return {'encounter': enc,
            'arrival': [(C - 3, C - 3)],
            'chest': rot4(*CHEST[kind]),
            'boss_chest': [(C - 5, C - 5), (C + 5, C - 5), (C - 4, C - 4)],
            'portal': [(C + 7, C)]}
