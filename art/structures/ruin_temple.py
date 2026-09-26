"""Act V landmark: the Temple of the Sacred Light (Templo de la Luz Sagrada), where the first light was fused.

The temple is a white stepped pyramid about 90 blocks across:
- four calcite and quartz terraces, with grand stairs up the four axes;
- obelisks on the diagonals of the third terrace;
- on top, the crater of the fusion: a glowing bowl with the pedestal at its heart;
- above the crater, a broken halo of gold and quartz floats on eight slender posts.

The challenge (docs/design/heliodor-ruins.md, Plan v2) combines light, an offering and combat:
- set an offering in the socket at the head of each stair;
- light the four lamps on the obelisks;
- then the Keeper of the Light rises from the crater;
- beat it, and the Sacred Flame burns on the pedestal.

D4-symmetric. Centred coordinates, layer 0 is the ground.

    python art/structures/ruin_temple.py
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
TERRACES = ((0, 3, 40), (4, 9, 32), (10, 17, 24), (18, 27, 16))   # (y0, y1, apothem)
TOP = TERRACES[-1][1]
CRATER_R, CRATER_D = 8.5, 6
HALO_Y, HALO_R = TOP + 11, 12.5
STAIR_W = 3                                                          # half width


def m(x, z):
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def terrace_r(y):
    for y0, y1, r in TERRACES:
        if y0 <= y <= y1:
            return r
    return None


def build():
    v = Voxels()
    mk = {'pedestal': [], 'offering_sockets': [], 'braziers': [], 'boss': [], 'barrels': [], 'lore': [], 'arrival': []}
    N = 46

    # --- the terraces ---
    for x in range(-N, N + 1):
        for z in range(-N, N + 1):
            d = m(x, z)
            a, b = ab(x, z)
            for y in range(0, TOP + 1):
                r = terrace_r(y)
                if d > r:
                    continue
                y1 = next(t[1] for t in TERRACES if t[0] <= y <= t[1])
                outer = d > r - 1
                if y == y1:
                    blk = B('smooth_quartz') if not outer else B('waxed_oxidized_cut_copper')
                    if not outer and (b == 0 or a == b) and d > CRATER_R + 2:
                        blk = B('calcite')                             # rays on each terrace floor
                elif outer:
                    blk = B('quartz_bricks') if (y1 - y) == 1 else B('calcite')
                    if abs(b - a * 0.4142) < 0.6:
                        blk = B('quartz_pillar[axis=y]')               # pilasters at the octagon's corners
                else:
                    blk = B('calcite')
                v.put(x, y, z, blk)
            if terrace_r(0) - 1 < d <= terrace_r(0) and noise(x, 0, z, 1) < 0.3:
                v.put(x, 1, z, B('moss_carpet'))                       # the ground reclaims the first step

    # --- grand stairs on the axes ---
    for a in range(TERRACES[-1][2] - 1, N + 1):
        h = TOP - (a - (TERRACES[-1][2] - 1)) + 1
        if h < 1:
            continue
        for b in range(0, STAIR_W + 1):
            for y in range(0, h):
                v.sym(a, y, b, B('calcite'))
            v.sym(a, h, b, B('quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
            for y in range(h + 1, h + 4):
                if terrace_r(y) is None or m(a, b) > terrace_r(y):
                    v.sym(a, y, b, B('air'))
                else:
                    v.sym(a, y, b, B('air'))
        for y in range(0, h + 2):                                       # stair cheeks
            v.sym(a, y, STAIR_W + 1, B('quartz_bricks') if y <= h else B('smooth_quartz_slab[type=bottom,waterlogged=false]'))
    # offering socket and lamp at the head of each stair
    head = TERRACES[-1][2] - 2
    v.sym(head, TOP + 1, 0, B('chiseled_quartz_block'))
    mk['offering_sockets'] = [[head, TOP + 2, 0], [-head, TOP + 2, 0], [0, TOP + 2, head], [0, TOP + 2, -head]]
    for b in (STAIR_W + 1,):
        v.sym(head, TOP + 1, b, B('quartz_pillar[axis=y]'))
        v.sym(head, TOP + 2, b, B('pearlescent_froglight[axis=y]'))

    # --- obelisks on the diagonals of the third terrace ---
    oy = TERRACES[2][1]
    for x in range(-N, N + 1):
        for z in range(-N, N + 1):
            a, b = ab(x, z)
            c = max(abs(a - 17), abs(b - 17))
            if c <= 1:
                for y in range(oy + 1, oy + 16):
                    taper = (y - oy) > 12 and c == 1
                    if not taper:
                        v.put(x, y, z, B('quartz_bricks') if (y - oy) % 5 else B('chiseled_quartz_block'))
            if c == 2:
                v.put(x, oy + 1, z, B('quartz_bricks'))
    for y in range(oy + 16, oy + 18):
        v.sym(17, y, 17, B('gold_block') if y == oy + 16 else B('waxed_copper_bulb[lit=false,powered=false]'))
    mk['braziers'] = [[sx * 17, oy + 17, sz * 17] for sx in (1, -1) for sz in (1, -1)]

    # --- the crater of the fusion ---
    for x in range(-10, 11):
        for z in range(-10, 11):
            d = math.hypot(x, z)
            if d <= CRATER_R:
                depth = int(round(CRATER_D * math.sqrt(max(0.0, 1 - (d / CRATER_R) ** 2))))
                floor = TOP - depth
                for y in range(floor + 1, TOP + 1):
                    v.put(x, y, z, B('air'))
                n = noise(x, floor, z, 3)
                v.put(x, floor, z, B('crying_obsidian') if n < 0.1 else (B('pearlescent_froglight[axis=y]') if n > 0.7 else B('calcite')))
            elif d <= CRATER_R + 1:
                v.put(x, TOP, z, B('gold_block'))                          # the gilt lip
    bottom = TOP - CRATER_D
    v.put(0, bottom, 0, B('chiseled_quartz_block'))
    v.put(0, bottom + 1, 0, B('chiseled_quartz_block'))
    mk['pedestal'] = [[0, bottom + 2, 0]]
    mk['boss'] = [[0, bottom + 2, 3]]

    # --- the halo on eight posts ---
    for x in range(-15, 16):
        for z in range(-15, 16):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            ang = math.degrees(math.atan2(b, a))                        # 0..45 in the octant
            if abs(d - HALO_R) <= 1.0 and not (19 < ang < 26):          # broken in eight narrow gaps
                v.put(x, HALO_Y, z, B('gold_block') if abs(d - HALO_R) < 0.3 else B('smooth_quartz'))
                if noise(x, 0, z, 4) < 0.35:
                    v.put(x, HALO_Y - 1, z, B('quartz_slab[type=top,waterlogged=false]'))
    for y in range(TOP + 1, HALO_Y):
        v.sym(HALO_R.__round__(), y, 0, B('quartz_pillar[axis=y]'))
        v.sym(9, y, 9, B('quartz_pillar[axis=y]'))

    # --- inner hall under the top terrace: barrels and the lore lectern, entered from the stairs ---
    hall_y = TERRACES[2][1] + 1
    for x in range(-13, 14):
        for z in range(-13, 14):
            d = m(x, z)
            if CRATER_R + 2 < d <= 13 and hall_y <= TOP - 1:
                for y in range(hall_y, hall_y + 4):
                    v.put(x, y, z, B('air'))
    for a in range(12, 17):                                             # passages from the stairs
        for b in range(0, 2):
            for y in range(hall_y, hall_y + 3):
                v.sym(a, y, b, B('air'))
    v.sym(11, hall_y, 6, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[11, hall_y, 6]]
    for y in range(hall_y, hall_y + 4):
        v.sym(8, y, 8, B('quartz_pillar[axis=y]'))
    v.sym(11, hall_y + 3, 3, B('pearlescent_froglight[axis=y]'))
    mk['arrival'] = [[N + 2, 1, 0]]
    return v, mk


if __name__ == '__main__':
    V, M = build()
    assert V.is_symmetric(), 'not D4-symmetric'
    ys = [y for (_, y, _) in V]
    xs = [x for (x, _, _) in V]
    print('blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
          'width', max(xs) - min(xs) + 1)
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    with open(os.path.join(out, 'ruin_temple.markers.json'), 'w') as f:
        json.dump(M, f, indent=1)
    from voxrender import render
    render(V, os.path.join(out, 'ruin_temple.png'), scale=3, ground=50)
    print('ok')
