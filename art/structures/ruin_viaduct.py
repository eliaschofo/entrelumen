"""Act III landmark: the Viaduct (Viaducto), the junction where Heliodor's routes crossed.

Four arcaded viaducts meet at a domed station, 24 blocks up:
- the arms have two tiers of arches, like an aqueduct, and each ends in a collapsed span with its
  last pier standing alone;
- the station is octagonal, with a copper dome, a lantern cupola and arched windows;
- copper lamps line the decks.

The challenge (docs/design/heliodor-ruins.md, Plan v2) is combat plus exploration:
- the Toll Guardian waits in the station hall;
- after the fight, the Route Seal appears on the dais;
- barrels are hidden in the piers along the arms and in the toll room at the foot of the
  station.
The way up is by ladders inside the station base.

D4-symmetric. Centred coordinates, layer 0 is the ground.

    python art/structures/ruin_viaduct.py
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
DECK = 24
ST_OUT, ST_IN = 9.5, 7.5          # station walls (octagon apothems)
PIERS = (18, 27, 36, 45)          # outer cell of each pier along an arm; piers are 2 thick, spans 7
BREAK = 37                        # the deck ends here; the last span is gone
HALF_LOW, HALF_UP = 3, 2          # half widths of the lower and upper tiers


def m(x, z):
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def spans():
    """Lower-tier openings (lo, hi) along an arm, 7 wide, between the station and the piers."""
    prev = int(ST_OUT)
    for p in PIERS:
        yield prev + 1, p - 2
        prev = p


def arch(a, y, openings, spring):
    """'open', 'voussoir' or None (solid) for cell (a, y) of a tier of round arches."""
    for o1, o2 in openings:
        if o1 - 1 <= a <= o2 + 1:
            c, w = (o1 + o2) / 2, (o2 - o1 + 1) / 2
            r = math.hypot(a - c, max(0.0, y - spring))
            if o1 <= a <= o2 and (y < spring or r <= w):
                return 'open'
            if y >= spring and r <= w + 1:
                return 'voussoir'
    return None


LOWER = list(spans())
UPPER = [o for lo, hi in LOWER for o in ((lo, lo + 2), (lo + 4, hi))]


def build():
    v = Voxels()
    mk = {'boss': [], 'pedestal': [], 'barrels': [], 'arrival': [], 'lore': []}

    # --- the arms (drawn on the +x arm in octant coordinates, mirrored by sym) ---
    for a in range(int(ST_OUT) + 1, PIERS[-1] + 1):
        for b in range(0, HALF_LOW + 2):
            for y in range(0, DECK + 3):
                blk = None
                if y <= 13 and b <= HALF_LOW:                                   # lower tier
                    k = arch(a, y, LOWER, 8)
                    if k != 'open':
                        blk = B('calcite') if k == 'voussoir' else B('tuff_bricks')
                        if y == 13:
                            blk = B('polished_tuff')
                if 13 < y < DECK and b <= HALF_UP:                              # upper tier, two arches per span
                    k = arch(a, y, UPPER, 19)
                    if k != 'open':
                        blk = B('calcite') if k == 'voussoir' else B('tuff_bricks')
                        if y == DECK - 1:
                            blk = B('polished_tuff')
                if y == DECK - 1 and b == HALF_UP + 1:
                    blk = B('tuff_brick_slab[type=top,waterlogged=false]')          # corbels under the deck edge
                if y == DECK and b <= HALF_LOW:
                    blk = B('polished_tuff') if b < HALF_LOW else B('tuff_bricks')
                    if b == 0:
                        blk = B('waxed_oxidized_cut_copper')                    # the copper line of the route
                if y == DECK + 1 and b == HALF_LOW:
                    blk = B('waxed_oxidized_copper_grate')
                if blk:
                    v.sym(a, y, b, blk)
    for p in PIERS:                                                              # piers stand proud as buttresses
        for a in (p - 1, p):
            for y in range(0, 14):
                v.sym(a, y, HALF_LOW + 1, B('tuff_bricks') if y < 13 else B('polished_tuff'))
            v.sym(a, 0, HALF_LOW + 2, B('tuff_bricks'))
    for p in PIERS[:-1]:                                                         # lamp posts over the piers
        v.sym(p, DECK + 1, HALF_LOW, B('tuff_bricks'))
        v.sym(p, DECK + 2, HALF_LOW, B('waxed_copper_bulb[lit=true,powered=false]'))

    # --- the break: the outer span fell ---
    for a in range(BREAK, PIERS[-1] + 3):
        for b in range(0, HALF_LOW + 3):
            for y in range(9, DECK + 3):
                ragged = BREAK + (y - 9) * 0.12 + noise(a, y, b, 1) * 1.5
                if a >= ragged and not (a in (PIERS[-1] - 1, PIERS[-1]) and y <= 17):
                    v.sym(a, y, b, B('air'))
    for a in range(BREAK - 1, PIERS[-1] + 4):                                    # rubble beneath the break
        for b in range(0, HALF_LOW + 4):
            if noise(a, 0, b, 2) < 0.45 and not (PIERS[-1] - 1 <= a <= PIERS[-1] and b <= HALF_LOW + 1):
                h = 1 + int(noise(a, 1, b, 3) * 2)
                for y in range(1, h + 1):
                    v.sym(a, y, b, B('tuff_bricks') if noise(a, y, b, 4) < 0.75 else B('calcite'))

    # --- barrels hidden in the piers (a niche on the inner face at the foot) ---
    for p in PIERS[:2]:
        v.sym(p - 1, 1, HALF_LOW + 1, B('barrel[facing=up,open=false]'))
        mk['barrels'].append([p - 1, 1, HALF_LOW + 1])

    # --- the station base ---
    R = int(ST_OUT) + 2
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = m(x, z)
            a, b = ab(x, z)
            for y in range(0, DECK + 1):
                if ST_IN < d <= ST_OUT:
                    blk = B('tuff_bricks')
                    if abs(b - a * 0.4142) < 0.6 and d > ST_OUT - 1:
                        blk = B('calcite')                                  # corner pilasters
                    if y in (14, DECK):
                        blk = B('polished_tuff')
                    v.put(x, y, z, blk)
                elif d <= ST_IN and y in (0, DECK):
                    v.put(x, y, z, B('chiseled_tuff') if abs(math.hypot(x, z) - 4) < 0.5 else B('polished_tuff'))
                elif d <= ST_IN:
                    v.put(x, y, z, B('air'))
    for a in range(5, 9):                                                    # arched doors on the diagonals
        for y in range(1, 7):
            if y < 6 or a in (6, 7):
                v.sym(a, y, a, B('air'))
                v.sym(a, y, a - 1, B('air'))
    for y in range(1, DECK + 1):                                             # ladders up to the hall
        v.sym(7, y, 0, B('ladder[facing=west,waterlogged=false]'))
    v.sym(4, 1, 4, B('barrel[facing=up,open=false]'))
    mk['barrels'].append([4, 1, 4])
    v.put(0, 1, 0, B('lectern[facing=north,has_book=false,powered=false]'))
    mk['lore'] = [[0, 1, 0]]

    # --- the station hall on the deck ---
    HALL_TOP = DECK + 10
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = m(x, z)
            a, b = ab(x, z)
            for y in range(DECK + 1, HALL_TOP + 1):
                if ST_IN + 1 < d <= ST_OUT:
                    blk = B('calcite') if abs(b - a * 0.4142) < 0.6 else B('tuff_bricks')
                    if y == HALL_TOP:
                        blk = B('waxed_oxidized_cut_copper')
                    v.put(x, y, z, blk)
            if ST_OUT < d <= ST_OUT + 1:
                v.put(x, HALL_TOP, z, B('waxed_oxidized_cut_copper'))        # cornice
    for a in range(8, 10):                                                   # doors to the four decks
        for b in range(0, 2):
            for y in range(DECK + 1, DECK + 6):
                if not (b == 1 and y == DECK + 5):
                    v.sym(a, y, b, B('air'))
    for y in range(DECK + 3, DECK + 9):                                      # tall windows on the diagonals
        v.sym(6, y, 6, B('glass'))
        v.sym(7, y, 6, B('glass'))
        v.sym(7, y, 7, B('air'))
    # dome with ribs, lantern cupola, spire
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            for dy in range(1, 11):
                rho = math.sqrt(m(x, z) ** 2 + (dy * 0.92) ** 2)
                if ST_IN + 0.6 <= rho <= ST_OUT + 0.2:
                    a, b = ab(x, z)
                    rib = b == 0 or a == b
                    v.put(x, HALL_TOP + dy, z, B('waxed_oxidized_copper') if rib else B('waxed_oxidized_cut_copper'))
    top = max(y for (_, y, _) in v)
    for y in range(top + 1, top + 5):
        for x in range(-2, 3):
            for z in range(-2, 3):
                a, b = ab(x, z)
                if (a, b) == (2, 2):
                    v.put(x, y, z, B('calcite'))
                elif y == top + 4 and max(a, b) <= 2:
                    v.put(x, y, z, B('waxed_oxidized_cut_copper'))
    v.put(0, top + 1, 0, B('waxed_copper_bulb[lit=true,powered=false]'))
    v.put(0, top + 5, 0, B('waxed_chiseled_copper'))
    for y in range(top + 6, top + 9):
        v.put(0, y, 0, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))

    # the dais and the guardian's floor
    for x in range(-2, 3):
        for z in range(-2, 3):
            if m(x, z) <= 2:
                v.put(x, DECK + 1, z, B('polished_tuff') if m(x, z) > 1 else B('chiseled_tuff_bricks'))
    mk['pedestal'] = [[0, DECK + 2, 0]]
    mk['boss'] = [[0, DECK + 2, 4]]
    for y in (DECK + 7,):
        v.sym(4, y, 4, B('lantern[hanging=false,waterlogged=false]'))
    for x in range(-R, R + 1):                                               # hanging lanterns need a hook: posts
        for z in range(-R, R + 1):
            a, b = ab(x, z)
            if (a, b) == (4, 4):
                for y in range(DECK + 1, DECK + 7):
                    v.put(x, y, z, B('calcite'))
    mk['arrival'] = [[0, 1, 12]]
    return v, mk


if __name__ == '__main__':
    V, M = build()
    probe = Voxels({k: b for k, b in V.items() if k != (0, 1, 0)})
    assert probe.is_symmetric(), 'not D4-symmetric'
    ys = [y for (_, y, _) in V]
    xs = [x for (x, _, _) in V]
    print('blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
          'width', max(xs) - min(xs) + 1)
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    with open(os.path.join(out, 'ruin_viaduct.markers.json'), 'w') as f:
        json.dump(M, f, indent=1)
    from voxrender import render
    render(V, os.path.join(out, 'ruin_viaduct.png'), scale=3, ground=50)
    render(V, os.path.join(out, 'ruin_viaduct_arm.png'), scale=5, keep=lambda x, y, z: -6 <= z <= 6 and x >= -2)
    print('ok')
