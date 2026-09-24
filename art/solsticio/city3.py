"""Solsticio, draft 3: the frozen capital of Heliodor, "wild" pass.

A floating island in three terraces (acropolis, city ring, hanging gardens) under an armillary
sphere that holds the frozen sun. Everything is D4-symmetric; hall interiors are mirror-symmetric
per hall. Player scale: doors 3 high, rooms 5+ high, stairs one block per step, paths 3-7 wide.

Coordinates are centred; y = 0 is the acropolis floor (markers stand on y = 1). Export shifts
everything so the lowest block is template layer 0 and writes city.nbt with CityLayout markers.

    python art/solsticio/city3.py [--export path/to/city.nbt]
"""
import gzip
import math
import os
import struct
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
from voxkit import Voxels, ab, tf, T8, rot_state, FACING, INV  # noqa: E402

R = 64                      # island half size
P = 2.4                     # superellipse exponent of the outline
T1, T2 = 20, 50             # terrace radii: acropolis d <= T1, city ring d <= T2, gardens beyond
Y1, Y2, Y3 = 0, -5, -10     # terrace floor heights
SUN_Y, SUN_R = 64, 22       # armillary sphere centre height and radius
B = lambda n: 'minecraft:' + n
LAMP = B('waxed_copper_bulb[lit=true,powered=false]')
LEAVES = B('flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]')
V = Voxels()
EXTRA = {}                  # preview-only: falling water and beacon beams
MARKERS = []


def outline(x, z):
    return ((abs(x) / R) ** P + (abs(z) / R) ** P) ** (1 / P)


def floor_y(d):
    return Y1 if d <= T1 else Y2 if d <= T2 else Y3


def everywhere(r):
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            yield x, z


def column(x, z, y0, y1, b):
    for y in range(y0, y1 + 1):
        V[(x, y, z)] = b


def sym_column(x, z, y0, y1, b):
    for y in range(y0, y1 + 1):
        V.sym(x, y, z, b)


# ---------------- island: hollow shell with terraces and a hanging underside ----------------
def island():
    for x, z in everywhere(R):
        e = outline(x, z)
        if e > 1:
            continue
        a, b = ab(x, z)
        d = math.hypot(x, z)
        top = floor_y(d)
        depth = int(8 + (1 - e) ** 0.55 * 62 + 5 * (math.cos(a * 0.37) * math.cos(b * 0.37)) ** 2)
        bottom = Y1 - depth
        for y in range(bottom, top + 1):
            k = top - y
            if k > 5 and y - bottom > 4 and e < 0.97:
                continue                                           # hollow: nobody sees inside
            if k == 0:
                blk = 'grass_block'
            elif k <= 3:
                blk = 'dirt'
            elif (top - y) % 9 == 0:
                blk = 'calcite'
            elif y - bottom < 6:
                blk = 'deepslate' if (a + b + y) % 5 else 'tuff'
            else:
                blk = 'stone' if (a * 3 + b + y) % 7 else 'tuff'
            V[(x, y, z)] = B(blk)
        # glowing roots and crystals under the belly
        if e < 0.9 and (a * 5 + b * 3) % 11 == 0:
            V[(x, bottom - 1, z)] = B('rooted_dirt')
            for dy in range(2, 3 + (a + b) % 4):
                V[(x, bottom - dy, z)] = B('hanging_roots')
        elif e < 0.9 and (a * 7 + b * 2) % 23 == 0:
            V[(x, bottom, z)] = B('verdant_froglight')
    # stalactite spires around the rim of the belly
    for (a, b, L) in ((56, 0, 22), (50, 20, 30), (40, 40, 26), (30, 12, 38), (18, 18, 44), (44, 8, 34)):
        base_bottom = Y1 - int(8 + (1 - outline(a, b)) ** 0.55 * 62)
        for k in range(0, L):
            r = 3.4 * (1 - k / L) ** 0.8
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if math.hypot(dx, dz) <= r:
                        blk = 'tuff' if k % 6 else 'calcite'
                        if k > L * 0.7 and (dx + dz + k) % 3 == 0:
                            blk = 'amethyst_block'
                        V.sym(a + dx, base_bottom - k, b + dz, B(blk))
        V.sym(a, base_bottom - L, b, B('pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]'))
    # the root of light: a huge amethyst crystal at the very bottom tip
    tip = Y1 - int(8 + 62 + 5)
    for k in range(0, 22):
        r = 6.5 * (1 - k / 22) ** 0.7
        for dx in range(-7, 8):
            for dz in range(-7, 8):
                dd = math.hypot(dx, dz)
                if dd <= r:
                    blk = 'budding_amethyst' if dd < r - 2 else 'amethyst_block'
                    if k % 5 == 2 and dd > r - 1:
                        blk = 'pearlescent_froglight'
                    V[(dx, tip - k, dz)] = B(blk)
    V[(0, tip - 22, 0)] = B('amethyst_cluster[facing=down,waterlogged=false]')


# ---------------- terrace walls, stairs and balustrades ----------------
def terraces():
    for x, z in everywhere(R):
        d = math.hypot(x, z)
        if outline(x, z) > 1:
            continue
        a, b = ab(x, z)
        for edge, hi, lo in ((T1, Y1, Y2), (T2, Y2, Y3)):
            if edge - 1.0 < d <= edge:
                column(x, z, lo, hi, B('calcite') if hi - lo > 0 else B('calcite'))
                V[(x, hi, z)] = B('waxed_cut_copper')
                V[(x, hi - 2, z)] = B('waxed_oxidized_cut_copper')
                if b > 4 and abs(b - a * 0.4142) > 2.5:
                    V[(x, hi + 1, z)] = B('polished_tuff_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]') \
                        if (a + b) % 3 == 0 else B('waxed_copper_grate')
        # rim of the island: a low calcite lip
        e = outline(x, z)
        if 0.975 < e <= 1 and b > 4 and abs(b - a * 0.4142) > 2.5:
            V[(x, Y3 + 1, z)] = B('calcite')
    # grand stairs on the axes (7 wide), down from each terrace edge
    for w in range(0, 4):
        for k in range(0, Y1 - Y2 + 1):
            s = T1 - 1 + k
            V.sym(s, Y1 - k, w, B('polished_tuff_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                  if k < Y1 - Y2 else B('polished_tuff'))
            for y in range(Y1 - k + 1, Y1 + 3):
                V.sym(s, y, w, B('air'))
        for k in range(0, Y2 - Y3 + 1):
            s = T2 - 1 + k
            V.sym(s, Y2 - k, w, B('polished_tuff_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                  if k < Y2 - Y3 else B('polished_tuff'))
            for y in range(Y2 - k + 1, Y2 + 3):
                V.sym(s, y, w, B('air'))
    # diagonal flights down to the plots
    for k in range(0, Y1 - Y2 + 1):
        s = 14 + k
        for w in (-1, 0, 1):
            V.sym(s + w, Y1 - k if k < 6 else Y2, s, B('polished_tuff'))


# ---------------- acropolis: plaza, canal ring, rotunda ----------------
def acropolis():
    for x, z in everywhere(T1):
        d = math.hypot(x, z)
        a, b = ab(x, z)
        th = math.degrees(math.atan2(b, a)) if a else 0
        if d <= 15.5:
            blk = 'calcite'
            if 13 <= d < 14 or (d > 13 and (th < 3 or th > 42)):
                blk = 'waxed_cut_copper'
            elif int(d) % 2 and d > 13:
                blk = 'polished_tuff'
            V[(x, Y1, z)] = B(blk)
        elif d <= 18.4:
            V[(x, Y1, z)] = B('water')
            V[(x, Y1 - 1, z)] = B('water')
            V[(x, Y1 - 2, z)] = B('prismarine_bricks')
        elif d <= T1 - 1:
            V[(x, Y1, z)] = B('waxed_oxidized_cut_copper')
    for w in range(0, 4):                                        # four bridges over the canal
        for s in range(15, T1):
            V.sym(s, Y1, w, B('calcite') if w < 3 else B('waxed_cut_copper'))
    for s in (16, 18):
        V.sym(s, Y1 + 1, 4, B('tuff_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]'))
        V.sym(s, Y1 + 2, 4, LAMP)


def rotunda():
    for x, z in everywhere(13):
        d = math.hypot(x, z)
        a, b = ab(x, z)
        th = math.degrees(math.atan2(b, a)) if a else 0
        if d <= 12.5:
            V[(x, Y1, z)] = B('polished_tuff')
            V[(x, Y1 + 1, z)] = B('calcite') if (d < 4 or d > 8) else B('waxed_cut_copper')
            if d < 2.5:
                V[(x, Y1 + 1, z)] = B('waxed_chiseled_copper')
            if abs(d - 6) < 0.5 and (th < 4 or th > 41):
                V[(x, Y1 + 1, z)] = B('gold_block')
        if 8.6 <= d < 9.7:                                        # inner wall, tall windows, doors
            for y in range(Y1 + 2, Y1 + 14):
                win = abs(th - 22.5) < 8 and Y1 + 4 <= y <= Y1 + 11
                door = b <= 1 and y <= Y1 + 4
                if door:
                    V[(x, y, z)] = B('air')
                else:
                    V[(x, y, z)] = B('yellow_stained_glass') if win else B('calcite')
        if d <= 12.8 and d >= 8.6:                                # ambulatory roof and solar ring
            V[(x, Y1 + 14, z)] = B('waxed_cut_copper')
            if 10 <= d <= 12 and b > 1:
                V[(x, Y1 + 15, z)] = B('daylight_detector')
        if 7.6 <= d < 9.0:                                        # drum
            for y in range(Y1 + 14, Y1 + 18):
                V[(x, y, z)] = B('glass') if y in (Y1 + 15, Y1 + 16) and abs(th - 22.5) < 10 else B('calcite')
    for k in range(0, 3):                                         # 16 columns, 12 blocks tall
        th = math.radians(k * 22.5)
        cx, cz = round(12 * math.cos(th)), round(12 * math.sin(th))
        for y in range(Y1 + 2, Y1 + 13):
            V.sym(cx, y, cz, B('quartz_pillar[axis=y]'))
        V.sym(cx, Y1 + 13, cz, B('chiseled_quartz_block'))
    # dome: calcite ribs, copper panels aging from the base; glass lantern with the beacon on top
    RH, HD = 9.4, 10.5
    inside = lambda x, y, z: y >= 0 and (x * x + z * z) / RH ** 2 + (y / HD) ** 2 <= 1
    ages = ['oxidized_', 'oxidized_', 'oxidized_', 'weathered_', 'weathered_', 'weathered_', 'exposed_', 'exposed_',
            '', '', '', '']
    for x, z in everywhere(10):
        for y in range(0, 11):
            if not inside(x, y, z):
                continue
            if all(inside(x + dx, y + dy, z + dz) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1))):
                continue
            a, b = ab(x, z)
            th = math.degrees(math.atan2(b, a)) if a else 0
            if math.hypot(x, z) < 2.3 and y >= 9:
                continue
            blk = 'calcite' if (th < 5 or th > 40) else 'waxed_' + ages[y] + 'cut_copper'
            V[(x, Y1 + 18 + y, z)] = B(blk)
    top = Y1 + 18 + 10
    for x, z in everywhere(1):
        V[(x, top - 1, z)] = B('gold_block')                       # beacon base, hidden in the crown
    V[(0, top, 0)] = B('beacon')
    for y in range(top, top + 3):
        V.sym(2, y, 0, B('glass'))
        V.sym(2, y, 1, B('glass'))
        V.sym(2, y, 2, B('calcite'))
    for x, z in everywhere(2):
        V[(x, top + 3, z)] = B('glass') if abs(x) < 2 and abs(z) < 2 else B('waxed_cut_copper')
    for y in range(top + 1, SUN_Y - 7):
        EXTRA[(0, y, 0)] = B('white_stained_glass')              # the beam that feeds the sun
    # the portal chamber keeps its centre free for the portal worker's block
    MARKERS.extend([('town_hall_portal', (0, Y1 + 2, 0)), ('arrival', (0, Y1 + 2, 6)),
                    ('town_hall_waystone', (0, Y1 + 2, -6)), ('mayor', (6, Y1 + 2, 0))])
    for (a, b) in ((3, 3),):
        V.sym(a, Y1 + 2, b, B('pearlescent_froglight'))


# ---------------- the armillary sphere and the frozen sun ----------------
def armillary():
    for x in range(-SUN_R - 1, SUN_R + 2):
        for z in range(-SUN_R - 1, SUN_R + 2):
            for y in range(-SUN_R - 1, SUN_R + 2):
                rho = math.sqrt(x * x + y * y + z * z)
                if not (SUN_R - 1.2 <= rho <= SUN_R + 0.3):
                    continue
                a, b = ab(x, z)
                equator = abs(y) <= 1
                meridian = b <= 0.8
                tropic = abs(abs(y) - 13) <= 0.6
                if equator:
                    blk = 'waxed_copper_block' if abs(y) == 0 else 'waxed_cut_copper'
                elif meridian:
                    blk = 'waxed_exposed_cut_copper'
                elif tropic:
                    blk = 'waxed_weathered_cut_copper'
                else:
                    continue
                V[(x, SUN_Y + y, z)] = B(blk)
    # zodiac studs on the equator
    for k in range(0, 3):
        th = math.radians(11.25 + k * 22.5)
        V.sym(round(SUN_R * math.cos(th)), SUN_Y + 2, round(SUN_R * math.sin(th)), B('end_rod[facing=up]'))
    # the frozen sun: a hollow glowing sphere pierced by the beam, with rays
    for x in range(-7, 8):
        for z in range(-7, 8):
            for y in range(-7, 8):
                rho = math.sqrt(x * x + y * y + z * z)
                if 5.2 <= rho <= 6.6:
                    if math.hypot(x, z) < 1.5:
                        blk = 'yellow_stained_glass'
                    else:
                        blk = ('ochre_froglight', 'shroomlight', 'glowstone')[(abs(x) + abs(y) + abs(z)) % 3]
                    V[(x, SUN_Y + y, z)] = B(blk)
    for k in range(7, 13):
        V.sym(k, SUN_Y, 0, B('end_rod[facing=east]'))
        V.sym(k - 2, SUN_Y, k - 2, B('end_rod[facing=up]')) if k < 11 else None
        V[(0, SUN_Y + k, 0)] = B('end_rod[facing=up]')
        V[(0, SUN_Y - k, 0)] = B('end_rod[facing=down]')
    for y in range(SUN_Y + 13, SUN_Y + 60):
        EXTRA[(0, y, 0)] = B('white_stained_glass')


# ---------------- city ring: four greenhouse halls ----------------
CZ, HW, HL, SPRING, RISE = 38, 7, 11, 6, 8


def vault_inside(x, y):
    return y >= 0 and (x / (HW + 0.6)) ** 2 + (y / RISE) ** 2 <= 1


def halls():
    floor = Y2
    for x in range(-HW, HW + 1):
        for z in range(CZ - HL, CZ + HL + 1):
            V.sym(x, floor, z, B('calcite') if x == 0 else B('polished_tuff'))
            edge_x, edge_z = abs(x) == HW, abs(z - CZ) == HL
            for y in range(floor + 1, floor + 1 + SPRING):
                if edge_x or edge_z:
                    pillar = (z - CZ) % 4 == 0
                    V.sym(x, y, z, B('waxed_cut_copper') if (y == floor + 1 or pillar) else
                          (B('glass') if (edge_x and floor + 3 <= y <= floor + 5) else B('calcite')))
            for y in range(0, RISE + 1):
                if not vault_inside(x, y):
                    continue
                shell = any(not vault_inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1)))
                if shell or edge_z:
                    rib = (z - CZ) % 4 == 0 or (edge_z and shell) or y == RISE
                    V.sym(x, floor + 1 + SPRING + y, z, B('waxed_cut_copper') if rib else B('glass'))
    for w in (-1, 0, 1):                                          # doors at both ends, 3 high
        for y in range(floor + 1, floor + 4):
            V.sym(w, y, CZ - HL, B('air'))
            V.sym(w, y, CZ + HL, B('air'))
    for s in (CZ - HL - 1, CZ + HL + 1):
        V.sym(2, floor + 4, s, LAMP)
    # a ridge lantern of froglights along the crown of each vault
    for z in range(CZ - HL + 2, CZ + HL - 1, 4):
        V.sym(0, floor + SPRING + RISE + 2, z, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    interiors(floor)
    for r, name in HALLS.items():
        x, z = rot(r, 0, CZ)
        MARKERS.append((name, (x, floor + 1, z)))


HALLS = {0: 'trading_hall', 1: 'inventor', 2: 'gardener', 3: 'priest'}


def rot(r, x, z):
    for _ in range(r):
        x, z = z, -x
    return x, z


def hall(r, u, y, s, b):
    for m in (1, -1):
        def f(vx, vz):
            return rot(r, m * vx, vz)
        p, q = f(u, s)
        state = b
        if '[' in b:
            name, props = b[:-1].split('[')
            out = []
            for pr in props.split(','):
                k, v = pr.split('=')
                if k == 'facing' and v in FACING:
                    v = INV[f(*FACING[v])]
                if k == 'axis' and r % 2 and v in 'xz':
                    v = 'z' if v == 'x' else 'x'
                out.append(k + '=' + v)
            state = name + '[' + ','.join(out) + ']'
        V[(p, y, q)] = state


def interiors(floor):
    lo, hi = CZ - HL + 2, CZ + HL - 2
    # gardener (Juan): terraced crop beds and rills, a seed vault of composters at the back
    for s in range(lo, hi + 1):
        for u, crop in ((2, 'carrots[age=7]'), (3, 'wheat[age=7]'), (5, 'potatoes[age=7]')):
            hall(2, u, floor, s, B('farmland[moisture=7]'))
            hall(2, u, floor + 1, s, B(crop))
        hall(2, 4, floor, s, B('water'))
        hall(2, 6, floor + 1, s, B('flowering_azalea') if s % 3 == 0 else B('moss_carpet'))
    # inventor (Terra): benches of crafters, a caged light engine at the back
    for s in range(lo, hi - 3, 2):
        hall(1, 3, floor + 1, s, B('crafter'))
        hall(1, 5, floor + 1, s, B('smithing_table' if s % 4 == 0 else 'cartography_table'))
        hall(1, 6, floor + 3, s, LAMP)
    for u in (0, 1, 2):
        for y in range(floor + 1, floor + 5):
            for s in (hi - 2, hi - 1, hi):
                core = u == 0 and s == hi - 1 and floor + 2 <= y <= floor + 3
                hall(1, u, y, s, B('pearlescent_froglight') if core else B('waxed_copper_grate'))
    # priest (Bodhi): birch pews facing a candle altar under a golden window
    for s in range(lo, hi - 3, 2):
        for u in (2, 3, 4, 5):
            hall(3, u, floor + 1, s, B('birch_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))
    for u in (0, 1, 2):
        hall(3, u, floor + 1, hi, B('calcite'))
        hall(3, u, floor + 2, hi, B('candle[candles=4,lit=true,waterlogged=false]') if u != 1 else B('air'))
    hall(3, 0, floor + 2, hi, B('lodestone'))
    # trading hall: stalls with job sites along the walls
    sites = ['barrel[facing=up,open=false]', 'fletching_table', 'cartography_table', 'smithing_table',
             'composter', 'loom[facing=west]', 'grindstone[face=floor,facing=west]', 'lectern[facing=west,has_book=false,powered=false]']
    for i, s in enumerate(range(lo, hi + 1, 2)):
        hall(0, 6, floor + 1, s, B(sites[i % len(sites)]))
        hall(0, 4, floor + 1, s, B('waxed_cut_copper_slab[type=bottom,waterlogged=false]'))


# ---------------- plots ----------------
P0 = 20


def plots():
    for x in range(P0, P0 + 16):
        for z in range(P0, P0 + 16):
            for y in range(Y2 + 1, Y2 + 14):
                for t in T8:
                    p, q = tf(x, z, t)
                    V.pop((p, y, q), None)
            border = x in (P0, P0 + 15) or z in (P0, P0 + 15)
            V.sym(x, Y2, z, B('waxed_cut_copper') if border else B('grass_block'))
    for (x, z) in ((P0 + 15, P0), (P0 + 15, P0 + 15), (P0, P0 + 15)):
        V.sym(x, Y2 + 1, z, B('tuff_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]'))
        V.sym(x, Y2 + 2, z, LAMP)
    for sx in (1, -1):
        for sz in (1, -1):
            x0 = P0 if sx > 0 else -P0 - 15
            z0 = P0 if sz > 0 else -P0 - 15
            MARKERS.append(('player_plot', (x0, Y2 + 1, z0)))


# ---------------- the eight cascades ----------------
def cascades():
    t = math.tan(math.radians(22.5))
    for x, z in everywhere(R):
        if outline(x, z) > 1:
            continue
        a, b = ab(x, z)
        d = math.hypot(x, z)
        if d < 18.5 or abs(b - a * t) > 1.3:
            continue
        y = floor_y(d)
        if d > T1 and d <= T1 + 0.5 or d > T2 and d <= T2 + 0.5:
            continue
        V[(x, y, z)] = B('water')
        V[(x, y - 1, z)] = B('calcite')
        for dy in range(1, 3):
            V.pop((x, y + dy, z), None)
    # preview: the falls at each terrace edge and off the rim
    for k in range(0, 3):
        for (edge, top, fall) in ((T1, Y1, Y1 - Y2), (T2, Y2, Y2 - Y3), (R - 1, Y3, 70)):
            th = math.radians(22.5)
            dd = edge + 0.8
            a, b = dd * math.cos(th), dd * math.sin(th)
            for w in (-1, 0, 1):
                bx, bz = round(a) + w, round(b)
                for yy in range(top - fall, top):
                    for tt in T8:
                        p, q = tf(bx, bz, tt)
                        EXTRA.setdefault((p, yy, q), B('water'))


# ---------------- lighthouses with beacon beams ----------------
def lighthouses():
    cx = 42
    base = Y3
    for y in range(base + 1, base + 30):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                edge = max(abs(dx), abs(dz)) == 2
                if not edge:
                    continue
                corner = abs(dx) == 2 and abs(dz) == 2
                band = (y - base) % 7 == 0
                window = not corner and dx == 0 or not corner and dz == 0
                blk = 'waxed_cut_copper' if band else ('quartz_pillar[axis=y]' if corner else
                                                       ('glass' if window and (y - base) % 7 in (3, 4) else 'calcite'))
                V.sym(cx + dx, y, cx + dz, B(blk))
    g = base + 30
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            V.sym(cx + dx, g, cx + dz, B('waxed_cut_copper_slab[type=bottom,waterlogged=false]') if max(abs(dx), abs(dz)) == 3
                  else B('gold_block'))
    V.sym(cx, g + 1, cx, B('beacon'))
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if (dx, dz) != (0, 0):
                V.sym(cx + dx, g + 1, cx + dz, B('glass') if dx * dz == 0 else B('quartz_pillar[axis=y]'))
                V.sym(cx + dx, g + 2, cx + dz, B('glass') if dx * dz == 0 else B('quartz_pillar[axis=y]'))
            V.sym(cx + dx, g + 3, cx + dz, B('waxed_cut_copper') if dx * dz else B('glass'))
    for (dx, dz) in ((3, 3),):
        V.sym(cx + dx, g + 1, cx + dz, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    for y in range(g + 2, g + 70):
        for t in T8:
            p, q = tf(cx, cx, t)
            EXTRA[(p, y, q)] = B('white_stained_glass')
    # arched sun gates at the end of each axis stair, framing the void
    for w in (4, 5):
        for y in range(Y3 + 1, Y3 + 9):
            V.sym(R - 3, y, w, B('calcite'))
    for w in range(0, 6):
        V.sym(R - 3, Y3 + 9 if w < 4 else Y3 + 8, w, B('waxed_cut_copper'))
    V.sym(R - 3, Y3 + 10, 0, B('gold_block'))


# ---------------- gardens and trees ----------------
FLOWERS = ['allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet', 'oxeye_daisy', 'blue_orchid']


def tree(x, z, y0, h=9, r=4):
    for y in range(y0 + 1, y0 + h + 1):
        V.sym(x, y, z, B('stripped_birch_log[axis=y]'))
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(-2, r):
                if math.sqrt(dx * dx + dz * dz + (dy * 1.4) ** 2) <= r + 0.3 and (dx or dz or dy > 0):
                    if (x + dx, y0 + h + dy, z + dz) not in V:
                        V.sym(x + dx, y0 + h + dy, z + dz, LEAVES)


def gardens():
    for x, z in everywhere(R):
        if V.get((x, floor_y(math.hypot(x, z)), z)) != B('grass_block') or (x, floor_y(math.hypot(x, z)) + 1, z) in V:
            continue
        a, b = ab(x, z)
        y = floor_y(math.hypot(x, z))
        n = (a * 7 + b * 13) % 29
        if y == Y3 and (a + b) % 4 == 0 and n < 12:
            V[(x, y + 1, z)] = B(FLOWERS[(a // 3 + b // 3) % len(FLOWERS)])
        elif n in (3, 17):
            V[(x, y + 1, z)] = B('short_grass')
        elif n == 9 and y == Y3:
            V[(x, y + 1, z)] = B('tall_grass[half=lower]')
    for (x, z, y0, h, r) in ((56, 10, Y3, 10, 4), (48, 30, Y3, 9, 4), (26, 13, Y2, 8, 3), (12, 26, Y2, 8, 3),
                             (58, 24, Y3, 8, 3)):
        tree(x, z, y0, h, r)


def build():
    V.clear()
    EXTRA.clear()
    MARKERS.clear()
    island()
    terraces()
    acropolis()
    cascades()
    gardens()
    halls()
    rotunda()
    armillary()
    plots()
    lighthouses()
    return V


# ---------------- NBT export ----------------
DATA_VERSION = 3955


def _nbt(tag, name, value):
    return bytes([tag]) + struct.pack('>H', len(name.encode())) + name.encode() + _payload(tag, value)


def _payload(tag, v):
    if tag == 3:
        return struct.pack('>i', v)
    if tag == 8:
        return struct.pack('>H', len(v.encode())) + v.encode()
    if tag == 10:
        return b''.join(_nbt(t, k, val) for k, (t, val) in v.items()) + b'\x00'
    if tag == 9:
        t, items = v
        return bytes([t if items else 0]) + struct.pack('>i', len(items)) + b''.join(_payload(t, it) for it in items)
    raise ValueError(tag)


def export(path):
    keys = [k for k, b in V.items() if not b.endswith(':air')]
    x0 = min(k[0] for k in keys)
    y0 = min(k[1] for k in keys)
    z0 = min(k[2] for k in keys)
    size = [max(k[0] for k in keys) - x0 + 1, max(k[1] for k in keys) - y0 + 1, max(k[2] for k in keys) - z0 + 1]
    palette, index, blocks = [], {}, []

    def state(b):
        if b not in index:
            name, props = (b[:-1].split('[') if '[' in b else (b, ''))
            entry = {'Name': (8, name)}
            if props:
                entry['Properties'] = (10, {k: (8, v) for k, v in (p.split('=') for p in props.split(','))})
            index[b] = len(palette)
            palette.append(entry)
        return index[b]

    for (x, y, z) in sorted(keys, key=lambda k: (k[1], k[2], k[0])):
        blocks.append({'pos': (9, (3, [x - x0, y - y0, z - z0])), 'state': (3, state(V[(x, y, z)]))})
    for name, (x, y, z) in MARKERS:
        blocks.append({'pos': (9, (3, [x - x0, y - y0, z - z0])),
                       'state': (3, state('minecraft:structure_block[mode=data]')),
                       'nbt': (10, {'id': (8, 'minecraft:structure_block'), 'mode': (8, 'DATA'),
                                    'metadata': (8, name)})})
    root = {'DataVersion': (3, DATA_VERSION), 'size': (9, (3, size)), 'palette': (9, (10, palette)),
            'blocks': (9, (10, blocks)), 'entities': (9, (10, []))}
    with gzip.GzipFile(path, 'wb', mtime=0) as f:
        f.write(_nbt(10, '', root))
    return size, len(blocks), len(palette), -y0


if __name__ == '__main__':
    t0 = time.time()
    build()
    print(len(V), 'blocks', round(time.time() - t0, 1), 's')
    from voxrender import render
    out = os.path.join(HERE, 'preview')
    os.makedirs(out, exist_ok=True)
    t0 = time.time()
    render(V, os.path.join(out, 'solsticio3.png'), scale=3, extra=EXTRA)
    render(V, os.path.join(out, 'solsticio3-center.png'), scale=6, extra=EXTRA,
           keep=lambda x, y, z: abs(x) <= 30 and abs(z) <= 30 and y > -12)
    print('render', round(time.time() - t0, 1), 's')
    if '--export' in sys.argv:
        print('export', export(sys.argv[sys.argv.index('--export') + 1]))
