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
BRAZIER_LEVELS = (2, 15, 27, 39)  # the floor each ring of braziers stands on (2 = podium top)


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
                    blk = B('polished_tuff') if (y in FLOORS or y == TOP) else B('tuff_bricks')
                    if y < 14 and noise(x, y, z, 3) < 0.12:
                        blk = B('tuff')                                     # weathered courses low down
                    if (a, b) == (pa, pb):
                        blk = B('calcite')
                    v.put(x, y, z, blk)
                elif (a, b) == (pa + 1, pb):
                    v.put(x, y, z, B('calcite'))                            # the pilaster stands proud
                elif r < d <= r + 1 and y in FLOORS:
                    v.put(x, y, z, B('waxed_oxidized_cut_copper'))          # copper cornice per floor

    for a in range(6, 9):                                               # doors on the four axes, arched
        for b in (0, 1):
            for y in range(3, 7):
                v.sym(a, y, b, B('calcite') if (y == 6 and b == 1) else B('air'))
            v.sym(a, 7, b, B('calcite'))

    for f in FLOORS:                                                    # stained-glass windows, a pair per axis face
        outer = int(r_out(f + 2))
        for y in range(f + 2, f + 6):
            for a in range(6, outer):
                v.sym(a, y, 2, B('air'))
            v.sym(outer, y, 2, B(GLASS[f] + '_stained_glass'))
        v.sym(outer, f + 6, 2, B('calcite'))
        for y in range(f + 3, f + 6):                                   # arrow slits on the diagonals
            for a in range(4, 8):
                if WALL_IN < m(a, a) <= r_out(y):
                    v.sym(a, y, a, B('air'))

    # --- floors, atrium and the way up ---
    for f in FLOORS:
        hole = 3.2 if f == 39 else 2.5
        for x in range(-6, 7):
            for z in range(-6, 7):
                d = math.hypot(x, z)
                if m(x, z) <= WALL_IN and d >= hole:
                    blk = B('chiseled_tuff') if abs(d - hole - 0.7) < 0.5 else B('polished_tuff')
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

    # --- braziers, lit from the bottom up ---
    for k, f in enumerate(BRAZIER_LEVELS):
        pos = (4, 0) if f == 39 else (3, 0)
        v.sym(pos[0], f, pos[1], B('chiseled_tuff'))
        v.sym(pos[0], f + 1, pos[1], B('campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]'))
        markers['braziers'][k + 1] = [[s * pos[0], f + 1, 0] for s in (1, -1)] + [[0, f + 1, s * pos[0]] for s in (1, -1)]

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
                    v.put(x, y, z, B('waxed_oxidized_cut_copper') if dy % 3 else B('waxed_oxidized_copper'))
    top = max(y for (_, y, _) in v)
    v.put(0, top + 1, 0, B('waxed_chiseled_copper'))
    for y in range(top + 2, top + 5):
        v.put(0, y, 0, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))

    # the lens over the key pedestal
    v.put(0, GALLERY + 1, 0, B('chiseled_tuff_bricks'))
    markers['pedestal'] = [[0, GALLERY + 2, 0]]
    for y in range(GALLERY + 3, GALLERY + 7):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                v.put(x, y, z, B('pearlescent_froglight[axis=y]') if x == z == 0 else B('glass'))
    for y in (GALLERY + 4, GALLERY + 5):
        v.sym(2, y, 0, B('waxed_oxidized_copper_bulb[lit=false,powered=false]'))
        markers['lantern'] += [[2, y, 0], [-2, y, 0], [0, y, 2], [0, y, -2]]
    markers['arrival'] = [[PODIUM + 5, 1, 0]]

    # moss on the podium, symmetric
    for x in range(-PODIUM, PODIUM + 1):
        for z in range(-PODIUM, PODIUM + 1):
            if STAGES[0][2] + 1 < m(x, z) <= PODIUM and (x, 3, z) not in v and noise(x, 3, z, 7) < 0.1:
                v.put(x, 3, z, B('moss_carpet'))
    return v, markers


if __name__ == '__main__':
    V, M = build()
    probe = Voxels({k: b for k, b in V.items() if k != (0, 3, 0)})     # the lectern is the one exception
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
