"""Act V landmark: the Void Observatory (Observatorio sobre el vacío), the End's lookout toward the Entrelumen.

Raised to act V's landmark on 26/9 (Elias), about 85 blocks across and 60 tall, floating over the void:
- a central observatory tower of purpur and end stone bricks, crowned by a great telescope tube aimed at
  the zenith;
- an orbital ring of purpur, 40 blocks out, held up by four obsidian pylons on four floating end stone
  islets on the diagonals;
- narrow bridges from the islets to the tower, two of them broken on purpose: that is the parkour;
- under the tower, the chart room with the pedestal of the Star Chart.

The challenge (docs/design/heliodor-ruins.md, Plan v2) is exploration, parkour over the void and combat:
- shulker nests guard the islets;
- each islet has a lens socket, and lighting all four aligns the telescope and opens the chart room.

D4-symmetric. Centred coordinates; y=0 is the tower's main deck. The structure floats (air-anchored
placement), with nothing below it but the void.

    python art/structures/ruin_void_observatory.py
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
RING_R, RING_Y = 40.0, 24
ISLET = 40                        # islet centre, on the diagonals (a = b = ISLET / sqrt 2)


def m(x, z):
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def build():
    v = Voxels()
    mk = {'pedestal': [], 'lens_sockets': [], 'shulker_nests': [], 'barrels': [], 'lore': [], 'arrival': []}

    # --- the tower: a stepped octagon of end stone bricks and purpur, the telescope on top ---
    for y in range(-14, 34):
        r = 9.5 if y < 0 else (8.5 if y < 16 else 6.5)
        for x in range(-11, 12):
            for z in range(-11, 12):
                d = m(x, z)
                a, b = ab(x, z)
                if y < -8:                                                 # the tapering keel under the deck
                    r_k = 9.5 - (-8 - y) * 1.3
                    if d <= r_k:
                        v.put(x, y, z, B('end_stone_bricks') if noise(x, y, z, 1) < 0.8 else B('end_stone'))
                    continue
                if d <= r:
                    wall = d > r - 1
                    if y in (0, 16) or (y < 0 and y in (-8, -1)):
                        v.put(x, y, z, B('purpur_block') if (a + b) % 4 else B('end_stone_bricks'))
                    elif wall:
                        pil = abs(b - a * 0.4142) < 0.7
                        glass = (b <= 1 and 3 <= (y % 16) <= 11) or ((a, b) == (6, 6) and 4 <= (y % 16) <= 10)
                        v.put(x, y, z, B('purpur_pillar[axis=y]') if pil else (B('magenta_stained_glass') if glass else B('end_stone_bricks')))
                    else:
                        v.put(x, y, z, B('air'))
            if y in (15, 31):
                pass
    for x in range(-12, 13):                                               # cornices at the setbacks
        for z in range(-12, 13):
            d = m(x, z)
            if 9.5 < d <= 10.5:
                v.put(x, 0, z, B('purpur_slab[type=bottom,waterlogged=false]'))
            if 8.5 < d <= 9.5:
                v.put(x, 16, z, B('purpur_block'))
    for b in range(0, 2):                                                  # doors to the four bridges
        for y in range(1, 5):
            for a in (8, 9):
                if not (b == 1 and y == 4):
                    v.sym(a, y, b, B('air'))
    # the chart room under the deck
    v.put(0, -7, 0, B('chiseled_quartz_block'))
    mk['pedestal'] = [[0, -6, 0]]
    for y in range(-7, -1):
        v.sym(8, y, 0, B('ladder[facing=west,waterlogged=false]'))
    v.sym(8, -1, 0, B('ladder[facing=west,waterlogged=false]'))
    v.sym(5, -7, 5, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[sx * 5, -7, sz * 5] for sx in (1, -1) for sz in (1, -1)]
    v.put(0, 1, 0, B('lectern[facing=north,has_book=false,powered=false]'))
    mk['lore'] = [[0, 1, 0]]

    # --- the great telescope: a tube rising from the top deck through a slotted cupola ---
    for y in range(17, 44):
        for x in range(-4, 5):
            for z in range(-4, 5):
                r = math.hypot(x, z)
                if y < 34:
                    if 2.4 < r <= 3.6:
                        v.put(x, y, z, B('purpur_pillar[axis=y]') if (y % 5) else B('end_stone_bricks'))
                    elif r <= 2.4:
                        v.put(x, y, z, B('air'))
                else:
                    rr = 3.6 + (y - 34) * 0.18
                    if rr - 1.2 < r <= rr:
                        v.put(x, y, z, B('purpur_block') if (y % 3) else B('crying_obsidian'))
    for x in range(-5, 6):
        for z in range(-5, 6):
            if math.hypot(x, z) <= 5.3:
                v.put(x, 44, z, B('glass') if math.hypot(x, z) <= 3.5 else B('end_stone_bricks'))
    v.put(0, 45, 0, B('end_rod[facing=up]'))

    # --- four islets on the diagonals, each with a pylon and a lens socket ---
    d0 = ISLET / S2
    for x in range(-50, 51):
        for z in range(-50, 51):
            a, b = ab(x, z)
            dd = math.hypot(a - d0, b - d0)
            if dd <= 9.5:
                depth = int(12 * math.sqrt(max(0.0, 1 - (dd / 9.5) ** 2)) + noise(x, 0, z, 2) * 2)
                for y in range(-depth, 1):
                    v.put(x, y, z, B('end_stone'))
                if dd <= 3:
                    for y in range(1, RING_Y):
                        v.put(x, y, z, B('obsidian') if y % 6 else B('crying_obsidian'))
                elif dd <= 7 and noise(x, 1, z, 3) < 0.08:
                    for y in range(1, 3 + int(noise(x, 2, z, 4) * 3)):
                        v.put(x, y, z, B('chorus_plant[down=true,east=false,north=false,south=false,up=true,west=false]'))
    for (a, b) in ((round(d0) + 4, round(d0) - 4),):
        v.sym(a, 1, b, B('end_stone_bricks'))
        v.sym(a, 2, b, B('chiseled_quartz_block'))
    mk['lens_sockets'] = [[sx * (round(d0) + 4), 3, sz * (round(d0) - 4)] for sx in (1, -1) for sz in (1, -1)] + \
                         [[sx * (round(d0) - 4), 3, sz * (round(d0) + 4)] for sx in (1, -1) for sz in (1, -1)]
    mk['shulker_nests'] = [[sx * round(d0), 1, sz * round(d0)] for sx in (1, -1) for sz in (1, -1)]

    # --- the orbital ring on the pylons ---
    for x in range(-42, 43):
        for z in range(-42, 43):
            d = math.hypot(x, z)
            if abs(d - RING_R) <= 1.0:
                v.put(x, RING_Y, z, B('purpur_block'))
                if abs(d - RING_R) <= 0.45:
                    v.put(x, RING_Y + 1, z, B('purpur_pillar[axis=y]') if noise(x, 0, z, 5) < 0.85 else B('end_rod[facing=up]'))

    # --- bridges from the tower to the islets (the ones on the axes run to the ring's feet), two broken ---
    for t in range(10, 40):
        for w in (-1, 0, 1):
            x, z = round(t / S2 + w / S2), round(t / S2 - w / S2)
            gap = 21 <= t <= 24                                             # a missing span: jump it
            if not gap:
                v.sym(x, 0, z, B('end_stone_brick_slab[type=top,waterlogged=false]'))
    for t in range(10, 39):
        for b in (0,):
            if not (18 <= t <= 20) and not (27 <= t <= 29):
                v.sym(t, 0, b, B('purpur_slab[type=top,waterlogged=false]'))
    for a in range(39, 42):                                                # landings under the ring, with a climb up to it
        for b in range(0, 2):
            v.sym(a, 0, b, B('end_stone_bricks'))
    for y in range(1, RING_Y):
        v.sym(40, y, 0, B('scaffolding[bottom=false,distance=0,waterlogged=false]'))
    mk['arrival'] = [[0, 1, 12]]
    return v, mk


if __name__ == '__main__':
    V, M = build()
    probe = Voxels({k: b for k, b in V.items() if k != (0, 1, 0)})       # the lectern is the one exception
    assert probe.is_symmetric(), 'not D4-symmetric'
    ys = [y for (_, y, _) in V]
    xs = [x for (x, _, _) in V]
    print('blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
          'width', max(xs) - min(xs) + 1)
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    with open(os.path.join(out, 'ruin_void_observatory.markers.json'), 'w') as f:
        json.dump(M, f, indent=1)
    from voxrender import render
    render(V, os.path.join(out, 'ruin_void_observatory.png'), scale=3, sky=((20, 12, 32), (50, 30, 70)))
    print('ok')
