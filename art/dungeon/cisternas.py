"""Cisternas, floor II of the Envés: the city's old water works. Tuff and oxidized copper, a deck
over shallow channels whose copper beds glow, walkways and grates over the water, pumps and
copper ducts.

Rules (Elias, 27/9): water slows you and the drowned and deeplings use it, but no room becomes a
swim: every channel is one block deep and there is always a dry way round. All art is D4 around the
cell centre; only the doors and the stairwell break it.

The rooms stand on a deck one block over the slab (kit DECK = 1), so every channel has its bed
inside the template: oxidized copper, and sea lanterns under the water where the room needs light.
Water never touches the template edge or anything it could flow into (kit.validate).

Reference inspected before drawing (hook of AGENTS.md), rendered with voxkit.load_nbt and
voxrender from the 1.21.1 server JAR (`server-1.21.1-...-extra.jar`):
- `data/minecraft/structure/trial_chambers/intersection/intersection_1.nbt`, the flooded trial
  chamber: tuff bricks walls, a frieze of chiseled tuff bricks alternating with chiseled tuff at one
  height, waxed copper bulbs set into the walls at a fixed pitch just over the water line, waxed
  oxidized copper as the floor under the water (it reads green through it), waxed copper blocks
  framing the openings, copper grates. Taken: the palette and its proportions, the frieze, bulbs at a
  pitch of four, copper frames round the doors, the green bed.
- `trial_chambers/corridor/end_2.nbt`: how little copper a tuff wall needs to read as copper work.
What is ours: the deck with walkable waterlogged grates flush over the channels, the lit beds, the
pump shafts and ducts, and the room plans.
"""
import kit
from kit import AIR, B, C, CEIL_Y, octant_body, read_quadrant, stairs

NAME = 'cisternas'
DECK = 1
BULB = B('waxed_copper_bulb', lit=True, powered=False)
BULB_OLD = B('waxed_exposed_copper_bulb', lit=True, powered=False)
P = dict(
    base=B('tuff'),
    bed=B('waxed_oxidized_copper'),
    bed_lamp=B('sea_lantern'),
    deck=B('polished_tuff'),
    deck2=B('tuff_bricks'),
    threshold=B('polished_tuff'),
    walk=B('waxed_oxidized_cut_copper'),
    copper=B('waxed_copper_block'),
    copper_cut=B('waxed_cut_copper'),
    chiseled_copper=B('waxed_oxidized_chiseled_copper'),
    grate=B('waxed_oxidized_copper_grate', waterlogged=True),
    dry_grate=B('waxed_oxidized_copper_grate'),
    wall=B('tuff_bricks'),
    course=B('polished_tuff'),
    frieze=(B('chiseled_tuff_bricks'), B('chiseled_tuff')),
    pillar=B('chiseled_tuff_bricks'),
    capital=B('chiseled_tuff_bricks'),
    landing=B('polished_tuff'),
    step='minecraft:tuff_brick_stairs',
    corbel='minecraft:tuff_brick_stairs',
    well=B('waxed_oxidized_cut_copper'),
    lintel=B('waxed_cut_copper'),
    jamb=B('waxed_copper_block'),
    door_lamp=BULB,
    ceil=B('tuff_bricks'),
    duct=B('waxed_oxidized_cut_copper'),
    joint=B('waxed_oxidized_chiseled_copper'),
)
FRIEZE_Y = DECK + 5                 # the chiseled band, like the trial chamber's
LAMP_Y = DECK + 2                   # wall bulbs, two over the deck


# ------------------------------------------------------------------ columns of blocks
def ceil(s):
    s[CEIL_Y] = P['ceil']
    s[CEIL_Y + 1] = P['ceil']
    return s


def wall(a, b):
    s = {0: P['base'], 1: P['course']}
    for y in range(2, CEIL_Y):
        s[y] = P['wall']
    s[FRIEZE_Y] = P['frieze'][(a + b) % 2]
    return ceil(s)


def deck(block=None):
    s = {0: P['base'], 1: block or P['deck']}
    for y in range(2, CEIL_Y):
        s[y] = AIR
    return ceil(s)


def water(lamp=False):
    s = deck()
    s[0] = P['bed_lamp'] if lamp else P['bed']
    s[1] = B('water')
    return s


def grate(lamp=False):
    s = water(lamp)
    s[1] = P['grate']
    return s


def column(bulb=True):
    """A tuff column with a copper plinth, a band of bulbs and a chiseled capital."""
    s = {0: P['base'], 1: P['course']}
    for y in range(2, CEIL_Y):
        s[y] = P['wall']
    s[LAMP_Y + 1] = BULB if bulb else P['copper']
    s[FRIEZE_Y] = P['frieze'][1]
    s[CEIL_Y - 1] = P['capital']
    return ceil(s)


def pump():
    """The pump shaft: a copper stack with grates and a lamp, from the deck to the ceiling."""
    s = {0: P['base'], 1: P['chiseled_copper'], 2: P['dry_grate'], 3: P['dry_grate'], 4: BULB,
         5: P['duct'], 6: P['duct'], 7: P['joint'], 8: P['duct'], 9: P['chiseled_copper']}
    return ceil(s)


def from_map(text, legend, reach=8):
    """A D4 body from a quadrant drawing: legend[char](a, b) -> column. The outer ring is wall."""
    q = read_quadrant(text)

    def col(a, b):
        if a > reach:
            return wall(a, b)
        return legend[q[(a, b)]](a, b)
    return octant_body(col)


LEGEND = {
    '#': wall,
    '.': lambda a, b: deck(),
    ':': lambda a, b: deck(P['deck2']),
    '=': lambda a, b: deck(P['walk']),
    'c': lambda a, b: deck(P['copper']),
    '~': lambda a, b: water(),
    '*': lambda a, b: water(lamp=True),
    'g': lambda a, b: grate(),
    'G': lambda a, b: grate(lamp=True),
    'o': lambda a, b: column(),
    'O': lambda a, b: column(bulb=False),
    'P': lambda a, b: pump(),
}


# ------------------------------------------------------------------ passes
def corbels(v):
    """Upside-down tuff stairs along the top of every wall (the Osarios vault, in tuff); a cell in an
    inner corner gets a capital block instead, so the pass stays symmetric."""
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


def wall_lamps(v, pitch=4, offset=2):
    """Bulbs in the room's straight outer walls at a fixed pitch, two blocks over the deck."""
    for t in range(-8, 9):
        if (abs(t) - offset) % pitch or abs(t) < offset:
            continue
        for (x, z, fx, fz) in ((C + t, 0, 0, 1), (C + t, 18, 0, -1), (0, C + t, 1, 0), (18, C + t, -1, 0)):
            if v.get((x + fx, LAMP_Y, z + fz)) == AIR and kit.full(v.get((x, LAMP_Y, z))):
                v[(x, LAMP_Y, z)] = BULB_OLD


def duct(v, cells, y, joint_every=4):
    """A square copper duct through the given (dx, dz) offsets of the canonical octant, D4-copied."""
    for i, (dx, dz) in enumerate(cells):
        kit.sym_put(v, dx, y, dz, P['joint'] if i % joint_every == joint_every - 1 else P['duct'])


# ------------------------------------------------------------------ rooms
# Quadrants: rows are dz 0..8 from the centre, columns dx 0..8; the drawing mirrors across its
# diagonal and the kit copies it to the four quadrants. The outer wall (dx or dz = 9) is implicit.
ALJIBE = """
c======::
=......::
=.~~~~~.:
=.~o~o~.:
=.~~*~~.:
=.~o~o~.:
=.~~~~~.:
::.....::
:::::::::
"""                                  # the cistern: causeways between four basins, a forest of columns in the water
ROTONDA = """
Gc=:.gg..
cc=:.gg..
==c:.~~..
:::.*~...
...*i~...
gg~~~...#
gg~....##
......###
.....####
"""                                  # the rotunda: a round island ringed by a lit canal, intakes dipping in it
GALERIA = """
*~.:~~~gg
~~.:~*~gg
..c:=====
:::......
~~=.#####
~*=.#####
~~=.#####
gg=.#####
gg=.#####
"""                                  # the gallery: a canal down each arm, grates at the doors, a basin at the crossing
BOMBAS = """
Lc=.:....
c.=.:....
==c.:....
....:....
::::c..~~
.......~*
......TT~
....~~TT~
....~*~~~
"""                                  # the pump hall (guard): open floor for the champion, pump towers in corner sumps
SALA = """
Lc=.:....
c.=.:....
==c.:....
....:....
::::c....
.....oo..
.....oo..
.........
.........
"""                                  # the arena cells: dry floor and columns, open to their neighbours


def tower():
    """A pump tower (2x2 in the pump hall): oxidized copper with a lit band and a grate window."""
    s = {0: P['base'], 1: P['chiseled_copper'], 2: B('waxed_oxidized_copper'), 3: B('waxed_oxidized_copper'),
         4: BULB, 5: P['duct'], 6: P['dry_grate'], 7: P['duct'], 8: P['chiseled_copper'], 9: P['capital']}
    return ceil(s)


def intake():
    """A copper pipe that dips into the canal: water under it, the pipe from just over the water up."""
    s = water()
    for y in range(DECK + 1, CEIL_Y):
        s[y] = P['duct']
    s[DECK + 4] = P['joint']
    s[CEIL_Y - 1] = P['chiseled_copper']
    return s


LEGEND.update({'T': lambda a, b: tower(), 'i': lambda a, b: intake(), 'L': lambda a, b: deck(P['bed_lamp'])})


def hang(v, dx, dz, low=CEIL_Y - 3):
    """A copper bulb hung on a chain from the ceiling at (dx, dz) and its D4 images."""
    for y in range(low + 1, CEIL_Y):
        kit.sym_put(v, dx, y, dz, B('chain', axis='y'))
    kit.sym_put(v, dx, low, dz, BULB)


def room(role, variant):
    kind = kind_of(role, variant)
    text = {0: ALJIBE, 1: ROTONDA, 2: GALERIA, 'guard': BOMBAS, 'arena': SALA}[kind]
    v = from_map(text, LEGEND)
    if role in ('shrine', 'seal', 'portal', 'vault', 'start', 'exit', 'vestibule'):
        for dx in range(-1, 2):                        # a dry copper centre
            for dz in range(-1, 2):
                v[(C + dx, 0, C + dz)] = P['base']
                v[(C + dx, 1, C + dz)] = P['copper']
    if role in ('shrine', 'seal', 'portal'):
        dais(v, P['chiseled_copper'] if role == 'shrine' else P['copper_cut'])
    if kind == 0:                                      # the pump head over the crossing, ducts to the walls
        duct(v, [(a, 0) for a in range(2, 9)], CEIL_Y - 2)
        kit.sym_put(v, 0, CEIL_Y - 1, 0, P['chiseled_copper'])
        kit.sym_put(v, 0, CEIL_Y - 2, 0, BULB)
        kit.sym_put(v, 1, CEIL_Y - 1, 0, P['joint'])
        kit.sym_put(v, 1, CEIL_Y - 1, 1, P['duct'])
    if kind in ('guard', 'arena'):                     # the open halls: bulbs hung over the floor
        hang(v, 3, 3)
    if kind == 2:
        tanks(v)
    corbels(v)
    wall_lamps(v, offset=3)
    return v


def tanks(v):
    """The gallery's corner masses are cisterns: water behind copper grates on the two faces that look
    onto the arms, a sea lantern under it. Sealed on every side, so it stays put."""
    for dx in (5, 6):
        for dz in (5, 6):
            kit.sym_put(v, dx, DECK, dz, P['bed_lamp'])
            for y in range(DECK + 1, DECK + 4):
                kit.sym_put(v, dx, y, dz, B('water'))
    for t in (5, 6):
        for y in range(DECK + 1, DECK + 4):
            kit.sym_put(v, 4, y, t, P['dry_grate'])
            kit.sym_put(v, t, y, 4, P['dry_grate'])
        kit.sym_put(v, 4, DECK + 4, t, P['joint'])
        kit.sym_put(v, t, DECK + 4, 4, P['joint'])


def dais(v, top):
    """A 3x3 copper dais one step over the deck, with stairs on its four sides."""
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            v[(C + dx, DECK + 1, C + dz)] = top
    kit.sym_put(v, 2, DECK + 1, 0, stairs(P['step'], 'west'))
    kit.sym_put(v, 2, DECK + 1, 1, stairs(P['step'], 'west'))


def corridor(variant):
    """An east-west conduit: a copper walkway between two lit channels under tuff ribs every four
    blocks, each with a bulb at its crown; variant 2 has grates over the channels instead."""
    v = {}
    for x in range(0, 19):
        for z in range(0, 19):
            t = abs(z - C)
            along = abs(x - C)
            if t >= 3 or x in (0, 18):
                s = wall(t, along)
            elif t == 2 and along <= 7:
                s = (grate if variant % 3 == 2 else water)(lamp=(along % 4 == 2))
            elif t == 2:
                s = deck(P['deck2'])
            else:
                s = deck(P['walk'] if t == 1 else P['deck'])
            for y, st in s.items():
                v[(x, y, z)] = st
    for x in range(1, 18):
        along = abs(x - C)
        if along % 4 == 0:                              # a rib: the vault drops to six over the deck
            for z in range(C - 2, C + 3):
                for y in range(DECK + 6, CEIL_Y):
                    v[(x, y, z)] = P['frieze'][0] if y == DECK + 6 else P['wall']
            v[(x, DECK + 6, C)] = BULB
            for z in (C - 3, C + 3):
                v[(x, LAMP_Y, z)] = P['frieze'][1]
        elif along % 4 == 2:
            for z in (C - 3, C + 3):
                v[(x, LAMP_Y, z)] = BULB_OLD
    corbels(v)
    return v


KIND_NAMES = {0: 'Aljibe', 1: 'Rotonda', 2: 'Galería', 'guard': 'Sala de bombas', 'arena': 'Sala',
              'corridor': 'Conducto'}


def kind_of(role, variant):
    if role in ('quiet', 'fight'):
        return variant % 3
    return {'guard': 'guard', 'shrine': 1, 'seal': 2, 'vault': 1, 'start': 1, 'exit': 1, 'vestibule': 1,
            'portal': 1, 'arena': 'arena', 'arena_center': 'arena'}[role]


def rot4(dx, dz):
    """The four rotations of a centred offset, as template (x, z)."""
    return [(C + dx, C + dz), (C - dz, C + dx), (C - dx, C - dz), (C + dz, C - dx)]


# per room kind: the diagonal of the four spawn points and where a room chest may stand
SPAWN = {0: 7, 1: 6, 2: 3, 'guard': 4, 'arena': 4}
CHEST = {0: (7, 3), 1: (6, 3), 2: (3, 6), 'guard': (6, 3), 'arena': (6, 3)}


def spots(role, variant, corridor):
    kind = kind_of(role, variant)
    d = SPAWN[kind]
    return {'encounter': [(C - d, C - d), (C + d, C - d), (C + d, C + d), (C - d, C + d)],
            'arrival': [(C - 3, C - 3)],
            'chest': rot4(*CHEST[kind]),
            'boss_chest': [(C - 3, C - 3), (C + 3, C - 3)],
            'portal': [(C + 7, C)]}
