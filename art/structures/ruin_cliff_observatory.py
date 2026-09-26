"""Act IV landmark: the Cliff Observatory (Observatorio del Risco), where Heliodor listened to the sky.

The site is about 60 blocks tall:
- A crag of layered stone rises 28 blocks. Four stairs are cut into it on the axes and end at
  arched portals, which lead through tunnels to a central room with ladders up.
- On the plateau stands a calcite drum with a copper dome, split by four meridian slits and open
  at the top.
- A zenith telescope rises through the dome. Its eyepiece is at the bottom, over the key
  pedestal.
- Four turrets on the diagonals each hold a mirror.

The challenge (docs/design/heliodor-ruins.md, Plan v2) is light plus exploration:
- turn the four mirrors so the beam enters the slits and runs down the telescope;
- the Eyepiece of Voices then rests on the pedestal;
- loot sits in the tunnel alcoves.

D4-symmetric. Centred coordinates, layer 0 is the ground.

    python art/structures/ruin_cliff_observatory.py
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
CRAG_H = 28
DRUM_OUT, DRUM_IN, DRUM_H = 8.5, 7.5, 10
STAIR_TOP = 17                   # the stairs arrive at portals this high


def m(x, z):
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def crag_r(y, x, z):
    """Radius of the crag at height y, a little ragged (from octant coordinates)."""
    base = 21 - 1.8 * (y // 6) - 0.2 * (y % 6)       # six-block cliff faces with ledges
    a, b = ab(x, z)
    ang = math.atan2(b, a)                           # 0..pi/4 in the octant
    lobes = 2.0 * math.cos(ang * 8)                  # eight buttresses of rock
    return base + lobes + (noise(x, y // 3, z, 1) - 0.5) * 1.0


def stone_at(x, y, z):
    band = (y + int(noise(x, 0, z, 2) * 2)) // 3 % 4
    return B(('stone', 'andesite', 'tuff', 'stone')[band])


def build():
    v = Voxels()
    mk = {'pedestal': [], 'mirrors': [], 'beam_receptor': [], 'barrels': [], 'lore': [], 'arrival': []}
    N = 30

    # --- the crag ---
    for x in range(-N, N + 1):
        for z in range(-N, N + 1):
            d = math.hypot(x, z)
            top = None
            for y in range(0, CRAG_H + 1):
                if d <= crag_r(y, x, z):
                    v.put(x, y, z, stone_at(x, y, z))
                    top = y
            if top is not None and top < CRAG_H and noise(x, top, z, 3) < 0.7:
                v.put(x, top, z, B('moss_block') if noise(x, top, z, 4) < 0.5 else B('grass_block[snowy=false]'))
    for x in range(-13, 14):                                            # the plateau
        for z in range(-13, 14):
            if m(x, z) <= 12.5:
                v.put(x, CRAG_H, z, B('polished_tuff') if m(x, z) > DRUM_OUT + 1 else B('chiseled_tuff'))
                if 11.5 < m(x, z) <= 12.5:
                    v.put(x, CRAG_H + 1, z, B('tuff_bricks'))
                    if noise(x, 0, z, 5) < 0.6:
                        v.put(x, CRAG_H + 2, z, B('polished_tuff'))

    # --- four stairs cut into the crag, ending at arched portals ---
    for a in range(9, N + 1):
        h = STAIR_TOP - (a - 11)                                        # the stair tread at a
        for b in range(0, 2):
            if a >= 11 and h >= 0:
                for y in range(0, h):                                   # a built ramp where the rock ends
                    if (a, y, b) not in v:
                        v.sym(a, y, b, B('tuff_bricks'))
                v.sym(a, h, b, B('tuff_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
                for y in range(h + 1, h + 5):
                    v.sym(a, y, b, B('air'))
            if 9 <= a < 11:                                             # landing at the portal
                v.sym(a, STAIR_TOP, b, B('polished_tuff'))
                for y in range(STAIR_TOP + 1, STAIR_TOP + 5):
                    v.sym(a, y, b, B('air'))
        if a >= 11 and h >= 0:                                          # low parapet on the outer edge
            for y in range(0, h + 2):
                if (a, y, 2) not in v or y == h + 1:
                    v.sym(a, y, 2, B('polished_tuff') if y == h + 1 else B('tuff_bricks'))
    for b in range(0, 3):                                               # portal arch
        for y in range(STAIR_TOP + 1, STAIR_TOP + 6):
            if b == 2 or y == STAIR_TOP + 5:
                v.sym(10, y, b, B('calcite'))
    # tunnels to the central room
    for a in range(3, 11):
        for b in range(0, 2):
            for y in range(STAIR_TOP + 1, STAIR_TOP + 5):
                v.sym(a, y, b, B('air'))
            v.sym(a, STAIR_TOP, b, B('polished_tuff'))
    for a in (5, 7):                                                    # alcoves with barrels
        v.sym(a, STAIR_TOP + 1, 2, B('barrel[facing=up,open=false]'))
        mk['barrels'] += [[a, STAIR_TOP + 1, 2]]
    for x in range(-4, 5):                                              # the central room and its ladders
        for z in range(-4, 5):
            if m(x, z) <= 4:
                for y in range(STAIR_TOP + 1, CRAG_H):
                    v.put(x, y, z, B('air'))
                v.put(x, STAIR_TOP, z, B('chiseled_tuff') if m(x, z) <= 1 else B('polished_tuff'))
    for y in range(STAIR_TOP + 1, CRAG_H + 1):
        v.sym(4, y, 0, B('ladder[facing=west,waterlogged=false]'))
    for x in range(-5, 6):
        for z in range(-5, 6):
            if 4 < m(x, z) <= 5:
                for y in range(STAIR_TOP + 1, CRAG_H):
                    if (x, y, z) not in v or v[(x, y, z)] == B('air'):
                        v.put(x, y, z, B('tuff_bricks'))
    for b in range(0, 2):                                               # reopen tunnel mouths into the room
        for y in range(STAIR_TOP + 1, STAIR_TOP + 5):
            v.sym(5, y, b, B('air'))
    v.put(0, STAIR_TOP + 1, 0, B('lectern[facing=north,has_book=false,powered=false]'))
    mk['lore'] = [[0, STAIR_TOP + 1, 0]]

    # --- the drum ---
    TOP = CRAG_H + DRUM_H
    for x in range(-10, 11):
        for z in range(-10, 11):
            d = m(x, z)
            a, b = ab(x, z)
            for y in range(CRAG_H + 1, TOP + 1):
                if DRUM_IN < d <= DRUM_OUT:
                    blk = B('calcite')
                    if y in (CRAG_H + 1, TOP) or (y - CRAG_H) % 4 == 0:
                        blk = B('waxed_oxidized_cut_copper')
                    v.put(x, y, z, blk)
                elif d <= DRUM_IN:
                    v.put(x, y, z, B('air'))
    for y in range(CRAG_H + 3, CRAG_H + 8):                             # tall windows on the diagonals
        v.sym(6, y, 5, B('glass'))
        v.sym(5, y, 6, B('glass'))
    for b in (0, 1):                                                    # doors on the axes
        for y in range(CRAG_H + 1, CRAG_H + 5):
            if not (b == 1 and y == CRAG_H + 4):
                v.sym(8, y, b, B('air'))
    for y in range(CRAG_H + 1, CRAG_H + 2):
        v.sym(4, y, 0, B('air'))

    # --- the dome with four meridian slits ---
    R = DRUM_OUT
    for x in range(-10, 11):
        for z in range(-10, 11):
            for dy in range(1, 10):
                rho = math.sqrt(m(x, z) ** 2 + (dy * 1.0) ** 2)
                if R - 1.1 <= rho <= R:
                    a, b = ab(x, z)
                    if b == 0 and dy >= 2 or math.hypot(x, z) < 2.5:
                        continue                                        # slits and the oculus
                    v.put(x, TOP + dy, z, B('waxed_oxidized_copper') if (a == b) else B('waxed_oxidized_cut_copper'))

    # --- the zenith telescope ---
    for y in range(CRAG_H + 3, TOP + 16):
        for x in range(-2, 3):
            for z in range(-2, 3):
                r = math.hypot(x, z)
                if r <= 1.6:
                    ring = (y - CRAG_H) % 5 == 0
                    blk = B('calcite') if ring else B('waxed_copper_block')
                    if r < 0.5 and y > CRAG_H + 3:
                        blk = B('glass')                                # the light path
                    v.put(x, y, z, blk)
    for x in range(-2, 3):                                              # the lens and its hood at the top
        for z in range(-2, 3):
            if math.hypot(x, z) <= 2.3:
                v.put(x, TOP + 16, z, B('waxed_oxidized_cut_copper') if math.hypot(x, z) > 1 else B('glass'))
    mk['beam_receptor'] = [[0, TOP + 16, 0]]
    for y in range(CRAG_H + 1, CRAG_H + 3):                             # the mount
        v.sym(2, y, 0, B('waxed_oxidized_cut_copper'))
        v.sym(2, y, 2, B('waxed_oxidized_cut_copper'))
    v.put(0, CRAG_H + 1, 0, B('chiseled_tuff_bricks'))
    mk['pedestal'] = [[0, CRAG_H + 2, 0]]

    # --- four mirror turrets on the diagonals ---
    for x in range(-13, 14):
        for z in range(-13, 14):
            a, b = ab(x, z)
            if math.hypot(a - 9.5, b - 9.5) <= 1.6:
                for y in range(CRAG_H + 1, CRAG_H + 9):
                    v.put(x, y, z, B('tuff_bricks') if y < CRAG_H + 8 else B('waxed_oxidized_cut_copper'))
    v.sym(9, CRAG_H + 9, 9, B('calcite'))
    v.sym(10, CRAG_H + 9, 10, B('calcite'))
    v.sym(10, CRAG_H + 9, 9, B('calcite'))
    mk['mirrors'] = [[sx * 10, CRAG_H + 10, sz * 10] for sx in (1, -1) for sz in (1, -1)]
    mk['arrival'] = [[N + 2, 1, 0]]
    return v, mk


if __name__ == '__main__':
    V, M = build()
    probe = Voxels({k: b for k, b in V.items() if k != (0, STAIR_TOP + 1, 0)})
    assert probe.is_symmetric(), 'not D4-symmetric'
    ys = [y for (_, y, _) in V]
    xs = [x for (x, _, _) in V]
    print('blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
          'width', max(xs) - min(xs) + 1)
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    with open(os.path.join(out, 'ruin_cliff_observatory.markers.json'), 'w') as f:
        json.dump(M, f, indent=1)
    from voxrender import render
    render(V, os.path.join(out, 'ruin_cliff_observatory.png'), scale=4, ground=34)
    render(V, os.path.join(out, 'ruin_cliff_observatory_cut.png'), scale=4, ground=34,
           keep=lambda x, y, z: not (x > 0 and z > 0))
    print('ok')
