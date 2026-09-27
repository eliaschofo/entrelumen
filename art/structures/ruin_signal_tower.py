"""Act I landmark: the Signal Tower (Torre de la Señal), the missing tower the first signal answers from.

An octagonal lighthouse of tuff bricks, about 84 blocks tall, on a stepped podium. Calcite
pilasters on the eight corners, copper cornices on each floor, flared buttresses on the diagonals.
The upper shaft is breached on the four diagonal faces, and rubble lies at the foot. On top there
is a railed gallery, a glass lantern room with the lens and the key pedestal, and a copper dome
with a spire.

Inside, the path up follows the challenge (docs/design/heliodor-ruins.md, Plan v2):
- ladders run from floor to floor on the four axes;
- the third flight is gone: stepping corbels climb from each axis to the diagonal corners, where
  ladders go on;
- each floor has four unlit braziers, lit from the bottom up; the stained glass of each floor
  shows its turn (orange, yellow, light blue, white);
- when all are lit, the lantern wakes. The loot barrels are on the top floor, and the Signal Ember
  is on the pedestal under the lens.

D4-symmetric: weathering and rubble come from octant coordinates. Centred coordinates, layer 0 is
the ground.

    python art/structures/ruin_signal_tower.py   # preview into the scratchpad or $TEMP
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from voxkit import Voxels, ab  # noqa: E402

B = lambda n: 'minecraft:' + n
S2 = math.sqrt(2)

PODIUM, GROUND = 18, 25          # podium and paved ring (octagon apothems)
WALL_IN = 5.5                    # the shaft's hollow; the outside steps in with the stages
STAGES = ((3, 26, 8.5), (27, 50, 7.5), (51, 62, 6.5))
PILASTER = {8.5: (8, 3), 7.5: (7, 3), 6.5: (6, 3)}   # octant cell of the corner pilaster per stage
WALL_OUT = STAGES[-1][2]
TOP = 62                         # last course of the shaft
FLOORS = (15, 27, 39, 51)        # floor slabs inside the shaft
GALLERY = 63
GLASS = {15: 'orange', 27: 'yellow', 39: 'light_blue', 51: 'white'}
ASYM = {(0, 3, 0)}               # functional cells exempt from the D4 check (the relay, the lectern)


def m(x, z):
    """Regular-octagon metric: an octagon of apothem r is m <= r."""
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def r_out(y):
    for lo, hi, r in STAGES:
        if lo <= y <= hi:
            return r
    return STAGES[-1][2]


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def octagon(v, y, r, block, inner=-1.0):
    n = int(r) + 2
    for x in range(-n, n + 1):
        for z in range(-n, n + 1):
            if inner < m(x, z) <= r:
                v.put(x, y, z, block)


def build():
    v = Voxels()
    markers = {'pedestal': [], 'braziers': {}, 'barrels': [], 'lantern': [], 'arrival': []}

    # --- the paved ring and the podium ---
    for x in range(-GROUND, GROUND + 1):
        for z in range(-GROUND, GROUND + 1):
            d = m(x, z)
            if PODIUM < d <= GROUND and noise(x, 0, z, 1) < 0.62 - (d - PODIUM) * 0.05:
                v.put(x, 0, z, B('polished_tuff') if noise(x, 0, z, 2) < 0.7 else B('tuff'))
            if d <= PODIUM:
                for y in (0, 1):
                    v.put(x, y, z, B('tuff_bricks'))
                a, b = ab(x, z)
                if d > PODIUM - 1:
                    top = B('tuff_bricks')
                elif b == 0 or a == b:                                  # eight calcite rays
                    top = B('calcite')
                elif abs(d - 12) < 0.5:                                 # a chiseled ring
                    top = B('chiseled_tuff')
                else:
                    top = B('polished_tuff')
                v.put(x, 2, z, top)
    for b in range(0, 3):                                               # four stair approaches
        v.sym(PODIUM + 2, 1, b, B('tuff_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        v.sym(PODIUM + 1, 0, b, B('tuff_bricks'))
        v.sym(PODIUM + 1, 1, b, B('tuff_bricks'))
        v.sym(PODIUM + 1, 2, b, B('tuff_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        v.sym(PODIUM + 2, 0, b, B('tuff_bricks'))

    # --- eight broken columns on the paved ring ---
    for y in range(1, 8):
        blk = B('chiseled_tuff_bricks') if y == 1 else (B('tuff_brick_wall') if y == 7 else B('calcite'))
        v.sym(22, y, 9, blk)
    v.sym(22, 0, 9, B('tuff_bricks'))

    # --- flared buttresses on the diagonals ---
    for t in range(4):
        h = 14 - 3 * t
        for x in range(-13, 14):
            for z in range(-13, 14):
                a, b = ab(x, z)
                if a - b <= 1 and abs(m(x, z) - (9 + t)) < 0.5:
                    for y in range(3, h):
                        v.put(x, y, z, B('tuff_bricks'))
                    v.put(x, h, z, B('polished_tuff'))

    # --- the shaft: three stages that step in, like a lighthouse ---
    for y in range(3, TOP + 1):
        r = r_out(y)
        pa, pb = PILASTER[r]
        for x in range(-10, 11):
            for z in range(-10, 11):
                d = m(x, z)
                a, b = ab(x, z)
                if WALL_IN < d <= r:
                    middle = STAGES[1][0] <= y <= STAGES[1][1]              # the middle stage turns bright: calcite and copper
                    blk = B('polished_tuff') if (y in FLOORS or y == TOP) else (B('calcite') if middle else B('tuff_bricks'))
                    if y < 14 and noise(x, y, z, 3) < 0.12:
                        blk = B('tuff')                                     # weathered courses low down
                    if (a, b) == (pa, pb):
                        blk = B('waxed_weathered_copper') if middle else B('calcite')
                    v.put(x, y, z, blk)
                elif (a, b) == (pa + 1, pb):
                    v.put(x, y, z, B('waxed_weathered_copper') if STAGES[1][0] <= y <= STAGES[1][1] else B('calcite'))
                elif r < d <= r + 1 and y in FLOORS:
                    v.put(x, y, z, B('waxed_oxidized_cut_copper'))          # copper cornice per floor

    for a in range(6, 9):                                               # doors on the four axes, arched
        for b in (0, 1):
            for y in range(3, 7):
                v.sym(a, y, b, B('calcite') if (y == 6 and b == 1) else B('air'))
            v.sym(a, 7, b, B('calcite'))

    for f in FLOORS:                                                    # tall stained-glass windows, amber at the foot to sky at the head
        outer = int(r_out(f + 2))
        for k, y in enumerate(range(f + 2, f + 8)):
            for a in range(6, outer):
                v.sym(a, y, 2, B('air'))
            v.sym(outer, y, 2, B(('orange', 'yellow', 'yellow', 'light_blue', 'light_blue', 'white')[k] + '_stained_glass'))
        v.sym(outer, f + 8, 2, B('waxed_weathered_cut_copper'))
        for k, y in enumerate(range(f + 2, f + 8)):                     # tall lights on the diagonal faces too
            for (a1, b1) in ((5, 4), (4, 5), (5, 5)):
                if WALL_IN < m(a1, b1) <= r_out(y):
                    v.sym(a1, y, b1, B(('orange', 'yellow', 'light_blue', 'light_blue', 'cyan', 'white')[k] + '_stained_glass'))

    # the upper stage: a glass lantern between the pilasters, copper bands at each floor
    lo, hi, r3 = STAGES[2]
    for y in range(lo, hi + 1):
        if y in FLOORS or y == TOP:
            continue
        for x in range(-8, 9):
            for z in range(-8, 9):
                a1, b1 = ab(x, z)
                if WALL_IN < m(x, z) <= r3 and (a1, b1) != PILASTER[r3] and not (a1 == 5 and b1 == 0):
                    v.put(x, y, z, B('light_blue_stained_glass') if (y - lo) % 4 == 3 else B('glass'))

    # --- floors, atrium and the way up ---
    for f in FLOORS:
        hole = -1.0                                                     # solid floors: the light relay climbs through them
        for x in range(-6, 7):
            for z in range(-6, 7):
                d = math.hypot(x, z)
                if m(x, z) <= WALL_IN and d >= hole:
                    blk = B('chiseled_tuff') if abs(d - 3.2) < 0.5 else B('polished_tuff')
                    if f == 39 and noise(x, f, z, 4) < 0.2:
                        continue                                            # the collapsed floor
                    v.put(x, f, z, blk)
    for x in range(-6, 7):                                              # ground floor inside the podium
        for z in range(-6, 7):
            if m(x, z) <= WALL_IN:
                v.put(x, 2, z, B('chiseled_tuff') if abs(math.hypot(x, z) - 3.2) < 0.5 else B('polished_tuff'))

    ladder = B('ladder[facing=west,waterlogged=false]')
    for lo, hi in ((3, 14), (16, 26), (28, 38), (52, TOP)):             # axis ladders, floor to floor
        for y in range(lo, hi + 1):
            v.sym(5, y, 0, ladder)
        if hi + 1 in FLOORS or hi == TOP:
            v.sym(5, hi + 1, 0, ladder)
    v.sym(5, 39, 0, B('polished_tuff'))                                 # no ladder hole on the broken floor
    # the missing flight: corbels climb from each axis to the diagonal corners
    for (a, b, y) in ((5, 0, 40), (5, 1, 41), (5, 2, 42), (4, 3, 43)):
        v.sym(a, y, b, B('polished_tuff'))
    for y in range(44, 52):
        v.sym(4, y, 3, B('ladder[facing=west,waterlogged=false]'))
    v.sym(5, 51, 0, B('polished_tuff'))                                 # floor 51 closes over the axes

    # --- the light relay (Elias, 26/9): vitrals tint the light and colours add up; each lit receptor lights
    # the brazier of the floor above. Reference: vanilla beacon beams take the colour of stained glass set in
    # them and blend several panes (textures/entity/beacon_beam.png); here the light travels flat on each floor.
    # Positions are functional and asymmetric: the one exception to D4 besides the lectern (see ASYM).
    relay = [
        dict(floor=1, brazier=(0, 16, 0), by_hand=True,
             vitrals=[((2, 16, 0), 'red'), ((0, 16, 2), 'blue')], mirrors=[],
             receptor=((2, 27, 0), ['red']),
             solution='red: up'),
        dict(floor=2, brazier=(0, 28, 0),
             vitrals=[((-2, 28, 0), 'red'), ((-3, 28, 0), 'green'), ((0, 28, -2), 'orange'), ((2, 28, 0), 'blue')], mirrors=[],
             receptor=((-3, 39, 0), ['red', 'green']),
             solution='red: pass; green: up'),
        dict(floor=3, brazier=(0, 40, 0),
             vitrals=[((0, 40, 2), 'green'), ((3, 40, 3), 'blue'), ((-2, 40, 0), 'red')], mirrors=[(0, 40, 3), (0, 40, -3)],
             receptor=((3, 51, 3), ['green', 'blue']),
             solution='green: pass; mirror (0,3): east; blue: up'),
        dict(floor=4, brazier=(-4, 52, 0),
             vitrals=[((-3, 52, 0), 'red'), ((-2, 52, 0), 'green'), ((-1, 52, 0), 'orange'), ((-2, 52, 2), 'blue')],
             mirrors=[(0, 52, 2)], collector=(0, 52, 0),
             receptor=((0, GALLERY + 3, 0), ['red', 'green', 'blue']),
             solution='red: pass; green: south; blue: east; mirror (0,2): north; collector: up to the lens'),
    ]
    for r in relay:
        bx, by, bz = r['brazier']
        v.put(bx, by - 1, bz, B('chiseled_tuff'))
        v.put(bx, by, bz, B('campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]'))
        for (x, y, z), colour in r['vitrals']:
            v.put(x, y - 1, z, B('waxed_cut_copper'))
            v.put(x, y, z, B(colour + '_stained_glass'))
        for (x, y, z) in r['mirrors']:
            v.put(x, y - 1, z, B('waxed_cut_copper'))
            v.put(x, y, z, B('calcite'))
        if 'collector' in r:
            x, y, z = r['collector']
            v.put(x, y - 1, z, B('waxed_chiseled_copper'))
            v.put(x, y, z, B('glass'))
        (x, y, z), target = r['receptor']
        if r['floor'] < 4:
            v.put(x, y, z, B('waxed_copper_bulb[lit=false,powered=false]'))
    for y in range(53, GALLERY + 3):                                    # the white light's shaft up to the lens
        v.put(0, y, 0, B('air'))
    markers['relay'] = [dict(floor=r['floor'], brazier=list(r['brazier']), by_hand=r.get('by_hand', False),
                             vitrals=[dict(pos=list(p), colour=c) for p, c in r['vitrals']],
                             mirrors=[list(p) for p in r['mirrors']],
                             collector=list(r['collector']) if 'collector' in r else None,
                             receptor=list(r['receptor'][0]), target=r['receptor'][1], solution=r['solution'])
                        for r in relay]
    for r in relay:
        cells = [r['brazier'], r['receptor'][0]] + [p for p, _ in r['vitrals']] + list(r['mirrors'])
        if 'collector' in r:
            cells.append(r['collector'])
        for (x, y, z) in cells:
            ASYM.update({(x, y, z), (x, y - 1, z)})
    ASYM.update({(0, y, 0) for y in range(53, GALLERY + 3)})

    # --- loot barrels on the top floor ---
    for y in (52,):
        v.sym(3, y, 3, B('barrel[facing=up,open=false]'))
    markers['barrels'] = [[sx * 3, 52, sz * 3] for sx in (1, -1) for sz in (1, -1)]

    # --- furnishing: a lectern under the atrium, shelves and hanging lanterns on the floors ---
    v.put(0, 3, 0, B('lectern[facing=north,has_book=false,powered=false]'))   # the one oriented piece
    markers['lore'] = [[0, 3, 0]]
    for f in (15, 27):
        for x in range(-6, 7):
            for z in range(-6, 7):
                a, b = ab(x, z)
                if 4.5 < m(x, z) <= WALL_IN and 2 < b and (x, f + 1, z) not in v and noise(x, f, z, 8) < 0.8:
                    for y in (f + 1, f + 2):
                        v.put(x, y, z, B('bookshelf'))
    for f in FLOORS + (GALLERY,):
        v.sym(2, f - 1, 2, B('lantern[hanging=true,waterlogged=false]'))

    # --- the four diagonal breaches in the upper shaft ---
    for x in range(-6, 7):
        for z in range(-6, 7):
            a, b = ab(x, z)
            if WALL_IN < m(x, z) <= r_out(45) and a - b <= 1:
                top = 48 - (a - b) * 2 + (1 if noise(x, 0, z, 5) < 0.5 else 0)
                for y in range(42, top + 1):
                    v.put(x, y, z, B('air'))

    # --- the rubble at the foot of each breach ---
    for x in range(-21, 22):
        for z in range(-21, 22):
            a, b = ab(x, z)
            if a != b and a - b > 4:
                continue
            r = math.hypot(a - 16, b - 16)
            if r < 3.6:
                h = 3 if r < 1.2 else (2 if r < 2.4 else 1)
                for y in range(1, h + 1):
                    n = noise(x, y, z, 6)
                    v.put(x, y, z, B('polished_tuff') if n < 0.2 else (B('waxed_oxidized_cut_copper') if n > 0.93 else B('tuff_bricks')))
                v.put(x, 0, z, B('tuff'))

    # --- the gallery ---
    octagon(v, TOP - 1, WALL_OUT + 1, B('tuff_bricks'), WALL_OUT)
    octagon(v, TOP, WALL_OUT + 2, B('polished_tuff'), WALL_OUT)
    octagon(v, GALLERY, WALL_OUT + 2.5, B('polished_tuff'))
    octagon(v, GALLERY, WALL_OUT + 2.5, B('waxed_oxidized_cut_copper'), WALL_OUT + 1.5)
    octagon(v, GALLERY + 1, WALL_OUT + 2.5, B('waxed_oxidized_copper_grate'), WALL_OUT + 1.5)
    v.sym(5, GALLERY, 0, B('ladder[facing=west,waterlogged=false]'))

    # --- the lantern room ---
    for y in range(GALLERY + 1, GALLERY + 8):
        for x in range(-7, 8):
            for z in range(-7, 8):
                a, b = ab(x, z)
                d = m(x, z)
                if 5.5 < d <= 6.5:
                    if (a, b) == (6, 2) or (a, b) == (5, 4):
                        v.put(x, y, z, B('calcite'))
                    elif b <= 1 and y <= GALLERY + 3:
                        v.put(x, y, z, B('air'))                            # four doors to the gallery
                    else:
                        v.put(x, y, z, B('glass'))
    for x in range(-8, 9):                                              # the dome
        for z in range(-8, 9):
            for dy in range(0, 9):
                rho = math.sqrt(m(x, z) ** 2 + (dy * 0.95) ** 2)
                y = GALLERY + 8 + dy
                if 5.6 <= rho <= 7.4:
                    patina = ('waxed_oxidized_copper', 'waxed_weathered_cut_copper', 'waxed_oxidized_cut_copper')[dy % 3]
                    v.put(x, y, z, B(patina))
    top = max(y for (_, y, _) in v)
    v.put(0, top + 1, 0, B('waxed_chiseled_copper'))
    for y in range(top + 2, top + 5):
        v.put(0, y, 0, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))

    # the lens over the key pedestal
    v.put(0, GALLERY + 1, 3, B('chiseled_tuff_bricks'))                 # the pedestal: the Atlas and the Signal Ember
    markers['pedestal'] = [[0, GALLERY + 2, 3]]
    markers['atlas'] = [[0, GALLERY + 2, 3]]
    ASYM.update({(0, GALLERY + 1, 3), (0, GALLERY + 1, 0)})
    for y in range(GALLERY + 3, GALLERY + 7):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                v.put(x, y, z, B('pearlescent_froglight[axis=y]') if x == z == 0 else B('glass'))
    for y in (GALLERY + 4, GALLERY + 5):
        v.sym(2, y, 0, B('waxed_oxidized_copper_bulb[lit=false,powered=false]'))
        markers['lantern'] += [[2, y, 0], [-2, y, 0], [0, y, 2], [0, y, -2]]
    markers['arrival'] = [[PODIUM + 5, 1, 0]]

    # planters with flowering azalea round the gallery rail
    for (a_, b_) in ((9, 0), (7, 5)):
        v.sym(a_, GALLERY + 1, b_, B('potted_flowering_azalea_bush'))
    # vines climbing the lower shaft on the diagonal faces
    opposite = {'west': 'east', 'east': 'west', 'north': 'south', 'south': 'north'}
    for x in range(-11, 12):
        for z in range(-11, 12):
            a_, b_ = ab(x, z)
            if r_out(5) < m(x, z) <= r_out(5) + 1 and (a_ - b_ <= 2 or noise(x, 0, z, 13) < 0.35):
                for y in range(4, 16):
                    if (x, y, z) in v or noise(x, y, z, 12) >= 0.8:
                        continue
                    props = {k2: 'false' for k2 in ('east', 'north', 'south', 'up', 'west')}
                    for (dx, dz), key in (((1, 0), 'east'), ((-1, 0), 'west'), ((0, 1), 'south'), ((0, -1), 'north')):
                        if v.get((x + dx, y, z + dz), '').endswith(('tuff_bricks', 'calcite', 'tuff', 'polished_tuff')):
                            props[key] = 'true'                         # every wall it touches, so corners stay symmetric
                    if 'true' in props.values():
                        v.put(x, y, z, B('vine[' + ','.join('%s=%s' % kv for kv in sorted(props.items())) + ']'))
    # vines hanging from under the gallery rim
    for x in range(-11, 12):
        for z in range(-11, 12):
            if WALL_OUT + 1.5 < m(x, z) <= WALL_OUT + 2.5 and (x, GALLERY - 1, z) not in v and noise(x, 1, z, 14) < 0.5:
                for dy in range(1, 2 + int(noise(x, 2, z, 15) * 4)):
                    v.put(x, GALLERY - dy, z, B('cave_vines[age=0,berries=false]') if dy > 1 else B('cave_vines_plant[berries=false]'))
    # moss on the podium, symmetric
    for x in range(-PODIUM, PODIUM + 1):
        for z in range(-PODIUM, PODIUM + 1):
            if STAGES[0][2] + 1 < m(x, z) <= PODIUM and (x, 3, z) not in v and noise(x, 3, z, 7) < 0.1:
                v.put(x, 3, z, B('moss_carpet'))
    return v, markers


if __name__ == '__main__':
    V, M = build()
    from voxkit import T8, tf
    skip = set()
    for (x, y, z) in ASYM:
        for t in T8:
            p_, q_ = tf(x, z, t)
            skip.add((p_, y, q_))
    probe = Voxels({k: b for k, b in V.items() if k not in skip})     # relay, pedestal and lectern exempt
    assert probe.is_symmetric(), 'not D4-symmetric'
    ys = [y for (_, y, _) in V]
    xs = [x for (x, _, _) in V]
    print('blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
          'width', max(xs) - min(xs) + 1)
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    with open(os.path.join(out, 'ruin_signal_tower.markers.json'), 'w') as f:
        json.dump(M, f, indent=1)
    from voxrender import render
    render(V, os.path.join(out, 'ruin_signal_tower.png'), scale=4, ground=27)
    cut = lambda x, y, z: x + z <= 1 or m(x, z) > STAGES[0][2] + 1
    render(V, os.path.join(out, 'ruin_signal_tower_cut.png'), scale=4, ground=27, keep=cut)
    print('ok')
