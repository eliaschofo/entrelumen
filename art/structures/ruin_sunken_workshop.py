"""Act II landmark: the Sunken Workshop (Taller hundido), Terra's lost workshop.

The site is about 77 blocks across:
- A flooded octagonal pit in three terraces. In the middle stands the drowned copper engine; four
  axle bridges cross from the rim to it.
- Four wheelhouses on the axes, one per craft. Each keeps a waterwheel in a channel that pours
  into the pit.
- Four brick chimneys on the diagonals, the skyline you see from far off.
- A broken perimeter wall, open on the diagonals.

The challenge (docs/design/heliodor-ruins.md, Plan v2) is mechanical:
- each wheelhouse has a sluice lever;
- with all four open, the pit drains and the engine's base shows four doors to the vault beneath;
- the vault holds Terra's Blueprint on the pedestal, the chest with Terra's Arm, and loot.
There are drowned in the water.

D4-symmetric (the wheelhouses are mirror images of one another). Centred coordinates, layer 0 is
the ground, and the pit goes down to -16.

    python art/structures/ruin_sunken_workshop.py
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from voxkit import Voxels, ab  # noqa: E402

B = lambda n: n if ':' in n.split('[')[0] else 'minecraft:' + n
S2 = math.sqrt(2)
WALL = 38                                   # perimeter wall (octagon apothem)
PIT = ((0, 16), (-4, 14), (-8, 12))         # terraces: (top y, apothem)
FLOOR = -12                                 # pit floor
WATER = -8                                  # water surface (top block)
HALL_X = (19, 33)                           # wheelhouse extent along its axis
HALL_Z = 8                                  # half width
WHEEL_C = (26, -2)                          # the Create large water wheel, axis z, in the channel


def m(x, z):
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def pit_r(y):
    for top, r in PIT:
        if y <= top and y > top - 4:
            return r
    return PIT[-1][1]


def build():
    v = Voxels()
    mk = {'pedestal': [], 'levers': [], 'drain_volume': None, 'vault_doors': [], 'arm_chest': [],
          'barrels': [], 'drowned': [], 'lore': []}
    N = WALL + 2

    # --- ground plate, perimeter wall, gates on the diagonals ---
    for x in range(-N, N + 1):
        for z in range(-N, N + 1):
            d = m(x, z)
            a, b = ab(x, z)
            if d <= WALL and d > PIT[0][1] and noise(x, 0, z, 1) < 0.55:
                v.put(x, 0, z, B('polished_tuff') if noise(x, 0, z, 2) < 0.6 else B('tuff'))
            if WALL - 1.5 < d <= WALL and a - b > 3 and noise(x, 8, z, 4) < 0.8:
                h = 1 + int(noise(x, 9, z, 3) * 4)
                for y in range(1, h + 1):
                    v.put(x, y, z, B('tuff_bricks') if y < h else B('polished_tuff'))
                v.put(x, 0, z, B('tuff_bricks'))

    # --- the pit: three terraces, flooded ---
    for x in range(-17, 18):
        for z in range(-17, 18):
            d = m(x, z)
            for y in range(FLOOR - 1, 1):
                r = pit_r(y)
                if d > PIT[0][1]:
                    continue
                if y == FLOOR - 1:
                    v.put(x, y, z, B('tuff_bricks'))
                elif d > r:
                    top_of_ledge = any(y == top - 4 for top, _ in PIT[:-1]) or y == 0
                    v.put(x, y, z, B('polished_tuff') if top_of_ledge else B('tuff_bricks'))
                elif y <= WATER:
                    v.put(x, y, z, B('water'))
                else:
                    v.put(x, y, z, B('air'))
    for x in range(-17, 18):                                            # a copper lip round the rim
        for z in range(-17, 18):
            if PIT[0][1] - 1 < m(x, z) <= PIT[0][1]:
                v.put(x, 0, z, B('waxed_oxidized_cut_copper'))
    mk['drain_volume'] = [[-PIT[-1][1], FLOOR, -PIT[-1][1]], [PIT[-1][1], WATER, PIT[-1][1]]]

    # --- the vault beneath the pit ---
    for x in range(-9, 10):
        for z in range(-9, 10):
            d = m(x, z)
            for y in range(FLOOR - 5, FLOOR):
                if d <= 8.5:
                    wall = d > 7.5 or y in (FLOOR - 5, FLOOR - 1)
                    v.put(x, y, z, B('tuff_bricks') if wall else B('air'))
            if d <= 7.5:
                v.put(x, FLOOR - 5, z, B('chiseled_tuff') if abs(math.hypot(x, z) - 3) < 0.5 else B('polished_tuff'))
    v.put(0, FLOOR - 4, 0, B('chiseled_tuff_bricks'))
    mk['pedestal'] = [[0, FLOOR - 3, 0]]
    v.sym(5, FLOOR - 4, 5, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[sx * 5, FLOOR - 4, sz * 5] for sx in (1, -1) for sz in (1, -1)]
    v.sym(3, FLOOR - 4, 0, B('barrel[facing=up,open=false]'))            # the arm's chest: one of four alike
    mk['arm_chest'] = [[3, FLOOR - 4, 0], [-3, FLOOR - 4, 0], [0, FLOOR - 4, 3], [0, FLOOR - 4, -3]]
    for y in (FLOOR - 2,):
        v.sym(3, y, 3, B('lantern[hanging=true,waterlogged=false]'))

    # --- the drowned engine: a copper boiler with a dome and a tall stack ---
    ER, ETOP = 6.3, 10
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = math.hypot(x, z)
            for y in range(FLOOR, ETOP + 6):
                if y <= ETOP and d <= ER:
                    if y % 5 == 0:
                        blk = B('waxed_oxidized_cut_copper')
                    elif y <= WATER:
                        blk = B('waxed_weathered_copper')
                    else:
                        blk = B('waxed_oxidized_copper')
                    v.put(x, y, z, blk)
                elif y > ETOP and math.hypot(d, (y - ETOP) * 1.25) <= ER:
                    v.put(x, y, z, B('waxed_oxidized_cut_copper') if (y - ETOP) % 2 else B('waxed_oxidized_copper'))
                elif ER < d <= ER + 1 and y in (0, 1, ETOP) and not (y == 1 and ab(x, z)[1] <= 1):
                    v.put(x, y, z, B('waxed_oxidized_cut_copper'))     # collar flanges
    # the engine room (Terra's bench): a round hall inside the boiler, the Create sandbox in the middle
    for x in range(-7, 8):
        for z in range(-7, 8):
            d = math.hypot(x, z)
            if d <= 5.2:
                v.put(x, 1, z, B('waxed_cut_copper') if d > 4.2 else B('polished_tuff'))
                for y in range(2, 7):
                    v.put(x, y, z, B('air'))
                v.put(x, 7, z, B('waxed_oxidized_cut_copper'))
            if abs(d - 4.7) < 0.5 and ab(x, z)[1] > 1 and (ab(x, z)[0] + ab(x, z)[1]) % 3 == 0:
                v.put(x, 6, z, B('lantern[hanging=true,waterlogged=false]'))
    for a in (5, 6, 7):                                                 # doors from the four bridges
        for b in (0, 1):
            for y in (1, 2, 3):
                v.sym(a, y, b, B('air'))
            v.sym(a, 4, b, B('waxed_oxidized_cut_copper'))
    mk['sandbox'] = [[-4, 2, -4], [4, 6, 4]]                            # cells with hypot <= 4.2 inside this box
    mk['input_ports'] = [[4, 4, 0], [-4, 4, 0], [0, 4, 4], [0, 4, -4]]
    mk['pump_ports'] = [[3, 1, 3], [-3, 1, 3], [3, 1, -3], [-3, 1, -3]]
    mk['seal_port'] = [[0, 1, 0]]
    mk['pumps'] = [[3, -3, 3], [-3, -3, 3], [3, -3, -3], [-3, -3, -3]]
    mk['pump_intakes'] = [[5, WATER - 1, 5], [-5, WATER - 1, 5], [5, WATER - 1, -5], [-5, WATER - 1, -5]]
    mk['seal_bearing'] = [[0, FLOOR - 1, 0]]
    mk['notes'] = [[3, 2, 4]]                                           # Terra's engine note, on the walkway
    v.put(3, 2, 4, B('lectern[facing=north,has_book=false,powered=false]'))
    for y in range(ETOP + 5, ETOP + 18):                                # the stack
        for x in range(-1, 2):
            for z in range(-1, 2):
                v.put(x, y, z, B('bricks') if (x or z) else B('air'))
    for x in range(-2, 3):
        for z in range(-2, 3):
            if max(abs(x), abs(z)) == 2:
                v.put(x, ETOP + 18, z, B('waxed_oxidized_cut_copper'))
                v.put(x, ETOP + 5, z, B('bricks'))

    for y in range(FLOOR - 4, FLOOR):                                   # four ladder shafts down to the vault
        v.sym(7, y, 0, B('ladder[facing=west,waterlogged=false]'))
    mk['vault_doors'] = [[7, FLOOR - 1, 0], [-7, FLOOR - 1, 0], [0, FLOOR - 1, 7], [0, FLOOR - 1, -7]]

    # --- four axle bridges from the rim to the engine ---
    for a in range(7, PIT[0][1] + 1):
        v.sym(a, -1, 0, B('polished_basalt[axis=x]'))
        for b in (0, 1):
            v.sym(a, 0, b, B('spruce_planks'))
        v.sym(a, 1, 2, B('chain[axis=y,waterlogged=false]') if a % 3 == 0 else B('air'))
    for a in range(7, PIT[0][1] + 1, 3):
        v.sym(a, 0, 2, B('spruce_slab[type=bottom,waterlogged=false]'))
    for a in range(5, HALL_X[0] + 1):                                   # the drive line: wheelhouse to engine, overhead
        v.sym(a, 4, 0, B('create:shaft[axis=x,waterlogged=false]'))
    for a in range(8, PIT[0][1] + 1, 4):                                # brackets on posts over the deck
        for y in (1, 2, 3):
            v.sym(a, y, 2, B('spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]'))
        v.sym(a, 4, 2, B('spruce_planks'))
        v.sym(a, 4, 1, B('create:andesite_casing'))
    mk['drive_lines'] = [[5, 4, 0], [HALL_X[0], 4, 0]]

    # --- wheelhouses on the axes ---
    x0, x1 = HALL_X
    for x in range(x0, x1 + 1):
        for z in range(0, HALL_Z + 1):
            edge_x = x in (x0, x1)
            edge_z = z == HALL_Z
            v.sym(x, 0, z, B('polished_tuff'))
            if edge_x or edge_z:
                corner = edge_x and edge_z
                for y in range(1, 9):
                    blk = B('calcite') if corner else (B('polished_tuff') if y in (1, 8) else B('tuff_bricks'))
                    v.sym(x, y, z, blk)
            # gable roof along the axis: ridge at z = 0, copper stairs down to the eaves
            ry = 9 + (HALL_Z - z)
            if z < HALL_Z + 1:
                rs = 'south'
                if z == 0:
                    v.sym(x, ry, 0, B('waxed_oxidized_cut_copper'))
                else:
                    v.sym(x, ry, z, B('waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))
                if edge_x:                                               # gable ends filled
                    for y in range(9, ry):
                        v.sym(x, y, z, B('tuff_bricks'))
    for x in range(x0 + 4, x1 - 3):                                     # a collapsed stretch of roof
        for z in range(0, 4):
            if noise(x, 0, z, 11) < 0.7:
                v.sym(x, 9 + HALL_Z - z, z, B('air'))
    for z in range(0, 3):                                               # the big door to the pit
        for y in range(1, 7):
            if not (y == 6 and z == 2):
                v.sym(x0, y, z, B('air'))
    for z in range(0, 3):
        v.sym(x0, 7, z, B('calcite'))
    for x in (x0 + 4, x1 - 4):                                          # windows in the long walls
        for y in (3, 4, 5):
            v.sym(x, y, HALL_Z, B('glass'))

    # the Create large water wheel in its channel (axis z), under a gantry; the drive rises to the overhead line
    cx = WHEEL_C[0]
    v.sym(cx, -2, 0, B('create:large_water_wheel[axis=z,extension=false,waterlogged=false]'))
    mk['wheels'] = [[cx, -2, 0]]
    for y in range(-1, 4):                                               # a gantry of posts over the wheel pit
        for zz in (2, 3):
            v.sym(cx - 2, y, zz, B('stripped_spruce_log[axis=y]'))
            v.sym(cx + 2, y, zz, B('stripped_spruce_log[axis=y]'))
    mk['wheel_to_line'] = [[cx, -2, 1], [cx, 4, 1]]                     # the worker routes axle -> overhead line here
    v.sym(cx, 1, 6, B('create:speedometer[axis=z,facing=up,waterlogged=false]'))
    mk['gauges'] = [[cx, 1, 6]]
    mk['notes'] = mk.get('notes', []) + [[cx - 4, 1, 6]]
    v.sym(cx - 4, 1, 6, B('lectern[facing=north,has_book=false,powered=false]'))
    # channel: a basin upstream behind the sluice, a one-block drop past the wheel, a fall into the pit
    for z in (0, 1, 2):
        for y in range(-7, 0):
            v.sym(WALL, y, z, B('tuff_bricks'))
    for x in range(PIT[0][1] + 1, WALL):
        upstream = x > HALL_X[1]
        bed = -5 if x >= cx - 2 else -6
        for z in (0, 1, 2):
            for y in range(-7, 1):
                if z == 2:
                    v.sym(x, y, z, B('tuff_bricks') if y < 0 else B('polished_tuff'))
                elif y < bed:
                    v.sym(x, y, z, B('tuff_bricks'))
                elif y == bed:
                    v.sym(x, y, z, B('polished_tuff'))
                elif upstream and y in (bed + 1, bed + 2):
                    v.sym(x, y, z, B('water'))                          # the basin: still water behind the gate
                elif HALL_X[0] <= x <= HALL_X[1] and y == 0:
                    v.sym(x, y, z, B('polished_tuff') if abs(x - cx) > 2 else B('air'))
                else:
                    v.sym(x, y, z, B('air'))
    for z in (0, 1):
        for y in (-4, -3):
            v.sym(HALL_X[1], y, z, B('waxed_copper_block'))              # the sluice gate the lever lifts
    mk['sluice_gates'] = [[HALL_X[1], -4, 0], [HALL_X[1], -3, 0], [HALL_X[1], -4, 1], [HALL_X[1], -3, 1]]
    for x in range(PIT[0][1] - 2, PIT[0][1] + 1):                        # cut through the pit wall for the fall
        for z in (0, 1):
            for y in range(-6, 0):
                v.sym(x, y, z, B('air'))
    v.sym(cx, -2, 0, B('create:large_water_wheel[axis=z,extension=false,waterlogged=false]'))

    # sluice lever beside the wheel, on the inner wall
    v.sym(HALL_X[1] - 2, 1, 5, B('polished_tuff'))
    v.sym(HALL_X[1] - 2, 2, 5, B('lever[face=floor,facing=north,powered=false]'))
    mk['levers'] = [[HALL_X[1] - 2, 2, 5]]
    v.sym(x1 - 2, 1, 6, B('barrel[facing=up,open=false]'))
    v.sym(x0 + 2, 1, 6, B('smithing_table'))
    v.sym(x0 + 3, 1, 6, B('grindstone[face=floor,facing=north]'))

    # --- four brick chimneys on the diagonals ---
    for x in range(-N, N + 1):
        for z in range(-N, N + 1):
            a, b = ab(x, z)
            c = max(abs(a - 24), abs(b - 24))
            if c <= 2:
                for y in range(0, 31):
                    if c == 2 or y == 0:
                        blk = B('bricks')
                        if y in (10, 20):
                            blk = B('waxed_oxidized_cut_copper')
                        v.put(x, y, z, blk)
                    else:
                        v.put(x, y, z, B('air'))
            if c == 3:
                for y in (0, 1, 2, 30, 31):
                    v.put(x, y, z, B('bricks') if y < 30 else B('waxed_oxidized_cut_copper'))
    mk['lore'] = [[x0 - 2, 1, 0]]
    mk['drowned'] = [[0, WATER, 8]]
    return v, mk


if __name__ == '__main__':
    V, M = build()
    probe = Voxels({k: (b if k != (3, 2, 4) else 'minecraft:air') for k, b in V.items()})   # Terra's engine note: the one exception
    assert probe.is_symmetric(), 'not D4-symmetric'
    ys = [y for (_, y, _) in V]
    xs = [x for (x, _, _) in V]
    print('blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
          'width', max(xs) - min(xs) + 1)
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    with open(os.path.join(out, 'ruin_sunken_workshop.markers.json'), 'w') as f:
        json.dump(M, f, indent=1)
    from voxrender import render
    render(V, os.path.join(out, 'ruin_sunken_workshop.png'), scale=3, ground=41)
    cut = lambda x, y, z: not (x > 3 and z > 3)
    render(V, os.path.join(out, 'ruin_sunken_workshop_cut.png'), scale=4, keep=cut, ground=41)
    print('ok')
