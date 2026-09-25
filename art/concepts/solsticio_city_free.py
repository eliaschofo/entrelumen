"""Solsticio built from its urban plan (solsticio_plan.py), in the free voxel engine.

Every cell of the plan becomes ground of its kind: the Axis of the Sun with its canal of light and
blossom rows, ring boulevards lined with trees and lamps, streets, lanes, the arcaded Street of
Crafts, plazas with a centrepiece each, the Edge Promenade with its balustrade and lighthouses,
the Midday Park with its lake, courtyard gardens. The blocks are split into lots along their
frontage; each lot is a building with its own height, district colours, windows, balconies,
awnings and a roof that follows the lot (hipped, domed on plaza corners, gardens on flat roofs).
The landmarks are built one by one: the Palace of the Solstice under the frozen Sun, the Great
Market of Light, the Clock Tower, the Temple of Dawn, Terra's workshop, the palm house, the
lighthouses on the viewpoints.

    python art/concepts/solsticio_city_free.py [overview|axis|market|all]
"""
import math
import os
import sys
import time
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import solsticio_plan as P  # noqa: E402
from freevox import Scene, pack, mix, fbm2, rnd, render  # noqa: E402

OUT = os.environ.get('ENTRELUMEN_CONCEPTS', os.path.join(HERE, 'out'))
S = Scene()

WHITE, WARM, STONE = '#f3ede2', '#e6d6b8', '#d2c3a6'
GOLD, GOLD_D, GOLD_L = '#f2c14e', '#c98f2a', '#ffe7a3'
COPPER, VERD = '#c8714f', '#4f9e7c'
SKY, ROSE, LILAC, TEAL = '#9fd6ef', '#eba3ad', '#b9a3e3', '#46dcc9'
AMBER, SUN, SUN_H = '#ffb54a', '#fffbe8', '#ffd66b'
GRASS, GRASS_D = '#86c24f', '#6aa83e'
BLOSSOM, BLOSSOM_D = '#f9bfd6', '#ec8fb4'
WATER = '#5cc4ea'
DISTRICT_COL = {
    'market': (['#f2d2b6', '#f6e0c8', '#eec3a0', '#f8e8d8'], ['#c8714f', '#d98a3d', '#b85a3a'], AMBER),
    'inns': (['#f7e3a8', '#f2d58e', '#fbeec8', '#f4dcb0'], ['#c95a4a', '#d98a3d', '#9a5a3a'], ROSE),
    'gardens': (['#e8f2dc', '#f3ede2', '#dcebd0', '#eef6e6'], ['#4f9e7c', '#3f8f6a', '#7cc9a4'], GOLD_L),
    'travellers': (['#e3eef8', '#f3ede2', '#d6e6f4', '#eef4fb'], ['#3f8fb0', '#2f6f9a', '#6fa8d8'], SKY),
    'temple': (['#f6e2ee', '#f3ede2', '#efd0e2', '#fbeef5'], ['#c9798a', '#b0569a', '#d890b0'], ROSE),
    'workshops': (['#e6e0f2', '#d9d2e8', '#f0ebf6', '#e2dcef'], ['#8d6cc4', '#6f4fc0', '#4f9e7c'], LILAC),
}
H = {}


def ground(x, z):
    return int(round(P.height(x, z)))


# ---------------- ground ----------------
def terrain():
    for (x, z), kind in P.CELL.items():
        H[(x, z)] = ground(x, z)
    for (x, z), kind in P.CELL.items():
        h = H[(x, z)]
        e = math.hypot(x, z) / P.edge(x, z)
        rim = any((x + a, z + b) not in P.CELL for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        lo = min(H.get((x + a, z + b), h) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        depth = int(20 + 90 * (1 - e) ** 1.5 + 12 * fbm2(x, z, 23, 11))
        for y in range(h - depth, h):
            k = h - y
            if rim or k <= 3 or y >= lo - 1 or y <= h - depth + 2:
                band = (y // 5) % 3
                c = '#9a7b52' if k <= 2 else ('#d9c7a8', '#c7b08d', '#e6d8bf')[band]
                S.put(x, y, z, c, emit=False)
        if rnd(x, 0, z, 9) < 0.01 and e < 0.85:
            col = TEAL if rnd(x, 2, z, 9) < 0.6 else LILAC
            L = int(6 + 30 * rnd(x, 1, z, 9) * (1 - e))
            for i in range(L):
                w = int((L - i) / L * 2)
                for dx in range(-w, w + 1):
                    for dz in range(-w, w + 1):
                        if abs(dx) + abs(dz) <= w:
                            S.put(x + dx, h - depth - i, z + dz, col, emit=True)


def tree(x, z, y, h=6, r=3.0, bloom=True):
    for k in range(1, h):
        S.put(x, y + k, z, '#7a5230')
    ir = int(r) + 1
    for dx in range(-ir, ir + 1):
        for dy in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                if dx * dx + (dy * 1.3) ** 2 + dz * dz <= r * r and rnd(x + dx, y + dy, z + dz, 3) > 0.18:
                    c = (BLOSSOM if rnd(x + dx, dy, z + dz, 4) > 0.3 else BLOSSOM_D) if bloom else \
                        (GRASS if rnd(x + dx, dy, z + dz, 4) > 0.4 else GRASS_D)
                    S.put(x + dx, y + h + dy, z + dz, c)


def lamp(x, z, y, h=5):
    for k in range(1, h):
        S.put(x, y + k, z, '#3a3440')
    S.put(x, y + h, z, AMBER, emit=True)
    S.put(x, y + h + 1, z, GOLD_D)


def surfaces():
    for (x, z), kind in P.CELL.items():
        y = H[(x, z)]
        r = rnd(x, 7, z, 1)
        if kind == 'axis':
            c = GOLD if (abs(x) in (9, 10)) else (WHITE if (x + z) % 4 else WARM)
            S.put(x, y, z, c)
        elif kind == 'canal':
            S.put(x, y, z, WATER, glass=True)
            S.put(x, y - 1, z, TEAL if r < 0.12 else '#3f8fb0', emit=r < 0.12)
        elif kind == 'trees':
            S.put(x, y, z, GRASS)
            if (z % 7 == 0) and (x % 2 == 0):
                tree(x, z, y, 6, 2.6)
        elif kind == 'boulevard':
            S.put(x, y, z, '#efe3cc' if (x * 3 + z) % 5 else WARM)
            edge = any(P.CELL.get((x + a, z + b)) not in ('boulevard', 'plaza', 'axis', 'canal') for a, b in ((2, 0), (-2, 0), (0, 2), (0, -2)))
            if edge and (x + z) % 9 == 0:
                tree(x, z, y, 5, 2.2, bloom=(x + z) % 2 == 0)
            elif edge and (x * 5 + z) % 13 == 0:
                lamp(x, z, y)
        elif kind == 'street':
            S.put(x, y, z, '#fbf6ec' if (x + z) % 3 else '#ece2cc')
            if (x * 7 + z * 3) % 29 == 0:
                lamp(x, z, y, 4)
        elif kind == 'lane':
            S.put(x, y, z, '#d8ccb4' if (x + 2 * z) % 3 else '#cbbd9e')
        elif kind == 'crafts':
            S.put(x, y, z, '#ffe0b5' if (x // 2 + z // 2) % 2 else '#f4c98e')
        elif kind == 'plaza':
            d = min(math.hypot(x - lx, z - lz) for (_, _, lx, lz, k) in P.LANDMARKS if k in ('plaza', 'small')) \
                if any(k in ('plaza', 'small') for *_, k in P.LANDMARKS) else 0
            S.put(x, y, z, GOLD if int(d) % 5 == 0 else (WHITE if (int(d * 3 + math.atan2(z, x) * 10)) % 2 else WARM))
        elif kind == 'promenade':
            outer = any((x + a, z + b) not in P.CELL for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            S.put(x, y, z, WARM if (x + z) % 4 else STONE)
            if outer:
                S.put(x, y + 1, z, WHITE)
                if (x + z) % 4 == 0:
                    S.put(x, y + 2, z, GOLD_D)
            elif r < 0.02:
                tree(x, z, y, 5, 2.4)
            elif r < 0.03:
                lamp(x, z, y, 4)
        elif kind in ('park', 'court', 'pocket', 'temple_ground'):
            S.put(x, y, z, GRASS if rnd(x, 1, z, 2) > 0.25 else GRASS_D)
            dens = {'park': 0.022, 'court': 0.03, 'pocket': 0.05, 'temple_ground': 0.02}[kind]
            if r < dens:
                tree(x, z, y, 5 + int(r * 200) % 4, 2.4 + (r * 100) % 1.5, bloom=rnd(x, 5, z, 1) > 0.35)
            elif r < dens + 0.12:
                S.put(x, y + 1, z, ['#f6e27a', '#f2a3c7', '#9ec7ff', '#ffffff', '#c7a3f2'][int(r * 1000) % 5])
        elif kind == 'path':
            S.put(x, y, z, '#efe2c4')
        elif kind == 'lake':
            S.put(x, y - 1, z, WATER, glass=True)
            S.put(x, y - 2, z, '#3f8fb0')
            if r < 0.02:
                S.put(x, y - 1, z, '#5fae4a')
        elif kind == 'plot':
            border = any(P.CELL.get((x + a, z + b)) != 'plot' for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            S.put(x, y, z, GOLD_D if border else GRASS)
        else:
            S.put(x, y, z, WARM)


# ---------------- blocks into lots, lots into buildings ----------------
LOT, FRONT, DIST = {}, {}, {}


def lots():
    cells = {c for c, k in P.CELL.items() if k == 'building'}
    q = deque()
    for c in cells:
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + a, c[1] + b)
            if n not in cells:
                FRONT[c] = P.CELL.get(n, 'void')
                DIST[c] = 0
                LOT[c] = (c[0] // 9, c[1] // 9, round(math.atan2(c[1], c[0]) * 3))
                q.append(c)
                break
    while q:
        c = q.popleft()
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + a, c[1] + b)
            if n in cells and n not in DIST:
                DIST[n] = DIST[c] + 1
                LOT[n] = LOT[c]
                FRONT[n] = FRONT[c]
                q.append(n)
    return cells


def buildings(cells):
    by_lot = {}
    for c in cells:
        by_lot.setdefault(LOT[c], []).append(c)
    for lid, cs in by_lot.items():
        cs_set = set(cs)
        d0 = min(DIST[c] for c in cs)
        fronts = [FRONT[c] for c in cs if DIST[c] == 0]
        grand = any(f in ('axis', 'boulevard', 'plaza', 'crafts') for f in fronts)
        plaza_corner = fronts.count('plaza') >= 3
        dist = P.DISTRICT.get(cs[0], 'market')
        walls, roofs, glass = DISTRICT_COL[dist]
        seed = hash(lid) & 0xFFFF
        wall = walls[seed % len(walls)]
        roof = roofs[(seed // 7) % len(roofs)]
        floors = (4 if grand else 3) + (seed % 3 == 0) + (1 if plaza_corner else 0)
        base = min(H[c] for c in cs)
        top = base + floors * 5
        # distance inside the lot, for the roof
        ld, q = {}, deque()
        for c in cs:
            if any((c[0] + a, c[1] + b) not in cs_set for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                ld[c] = 0
                q.append(c)
        while q:
            c = q.popleft()
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (c[0] + a, c[1] + b)
                if n in cs_set and n not in ld:
                    ld[n] = ld[c] + 1
                    q.append(n)
        style = 'dome' if plaza_corner else ('garden' if seed % 5 == 0 else 'hip')
        arcade = 'crafts' in fronts
        for c in cs:
            x, z = c
            outside = ld[c] == 0
            for y in range(H[c] + 1, base + 1):
                S.put(x, y, z, STONE)
            if outside:
                along = (x + z) if abs(x) > abs(z) else (x - z)
                for y in range(base + 1, top + 1):
                    k = (y - base - 1) % 5
                    fl = (y - base - 1) // 5
                    if k == 4:
                        c_ = GOLD_D if fl % 2 == 0 else wall
                        S.put(x, y, z, c_)
                    elif fl == 0 and arcade and DIST[c] == 0:
                        if along % 3 == 0 or k == 3:
                            S.put(x, y, z, WHITE)
                    elif k in (1, 2) and along % 3 != 0:
                        lit = rnd(x, y, z, 12) < 0.14
                        S.put(x, y, z, AMBER if lit else mix(glass, '#27304a', 0.55 if k == 1 else 0.4), emit=lit)
                    elif k == 3 and along % 3 != 0:
                        S.put(x, y, z, mix(glass, '#27304a', 0.2) if fl % 2 else wall)
                    else:
                        S.put(x, y, z, wall)
                # awnings over shop doors on the grand streets, flower boxes, balconies
                if DIST[c] == 0 and grand and along % 5 == 1 and not arcade:
                    fx, fz = x, z
                    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if (x + a, z + b) not in cells:
                            fx, fz = x + a, z + b
                            break
                    S.put(fx, base + 4, fz, [ROSE, SKY, GOLD, '#8fd18a', LILAC][seed % 5])
                if DIST[c] == 0 and along % 4 == 2 and floors > 3:
                    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if (x + a, z + b) not in cells:
                            S.put(x + a, base + 10, z + b, GOLD)
                            S.put(x + a, base + 11, z + b, BLOSSOM if seed % 2 else '#9ec7ff')
                            break
            else:
                S.put(x, top, z, wall)
            if outside and DIST[c] == 0:                    # a cornice over the street
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if (x + a, z + b) not in cells:
                        S.put(x + a, top, z + b, GOLD_D if (x + z) % 2 else WHITE)
                        S.put(x + a, base + 1, z + b, STONE)
                        break
            # roofs
            dd = ld[c]
            if style == 'hip':
                rise = min(dd, 4)
                S.put(x, top + 1 + rise, z, roof if (x + z + rise) % 7 else GOLD)
                for y in range(top + 1, top + 1 + rise):
                    if outside or dd <= 1:
                        S.put(x, y, z, roof)
            elif style == 'garden':
                if outside:
                    S.put(x, top + 1, z, WHITE)
                else:
                    S.put(x, top, z, GRASS)
                    if rnd(x, top, z, 13) < 0.08:
                        S.put(x, top + 1, z, BLOSSOM)
            else:
                rise = int(math.sqrt(max(0, dd * 6)))
                S.put(x, top + 1 + rise, z, GOLD if rise > 3 else roof)
                for y in range(top + 1, top + 1 + rise):
                    if outside or dd <= 1:
                        S.put(x, y, z, roof)
        if style == 'dome':
            peak = max(cs, key=lambda c: ld[c])
            h = top + 1 + int(math.sqrt(ld[peak] * 6))
            for k in range(1, 5):
                S.put(peak[0], h + k, peak[1], GOLD if k < 4 else SUN_H, emit=k == 4)
        # a chimney
        if style == 'hip' and len(cs) > 30:
            c = max(cs, key=lambda c: ld[c])
            for k in range(0, 4):
                S.put(c[0] + 1, top + 5 + k, c[1], STONE)


# ---------------- landmarks ----------------
def landmark(name):
    for (es, en, x, z, kind) in P.LANDMARKS:
        if es.startswith(name):
            return x, z, kind
    return None


def palace():
    cx, cz = P.PALACE
    cells = [c for c, k in P.CELL.items() if k == 'palace']
    y0 = max(H[c] for c in cells) + 2
    xs, zs = [c[0] for c in cells], [c[1] for c in cells]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    for (x, z) in cells:                                    # plinth and terrace
        for y in range(H[(x, z)] + 1, y0 + 1):
            S.put(x, y, z, STONE if y < y0 else (GOLD if (x + z) % 6 == 0 else WHITE))
    for i in range(8):                                      # the grand stair down to the Plaza Mayor
        for x in range(cx - 10, cx + 11):
            S.put(x, y0 - i, z1 + 1 + i, WHITE if abs(x - cx) < 10 else GOLD)
    # the body: a colonnade round a hall
    for x in range(x0 + 3, x1 - 2):
        for z in range(z0 + 3, z1 - 2):
            edge = x in (x0 + 3, x1 - 3) or z in (z0 + 3, z1 - 3)
            if not edge:
                S.put(x, y0 + 16, z, WHITE)
                continue
            along = x if z in (z0 + 3, z1 - 3) else z
            for y in range(y0 + 1, y0 + 17):
                if y >= y0 + 15:
                    S.put(x, y, z, GOLD_D)
                elif along % 4 == 0:
                    S.put(x, y, z, WHITE)
                elif y > y0 + 3:
                    S.put(x, y, z, [GOLD_L, AMBER, ROSE, SKY][(y // 3 + along) % 4], emit=(y % 6 == 0), glass=True)
    # the drum and the sunflower dome
    S.cylinder(cx, cz, y0 + 17, y0 + 28, 14, WHITE, shell=1.5)
    for y in range(y0 + 19, y0 + 27):
        for k in range(40):
            th = 2 * math.pi * k / 40
            x, z = round(cx + math.cos(th) * 14), round(cz + math.sin(th) * 14)
            S.put(x, y, z, GOLD if k % 4 == 0 else [GOLD_L, AMBER, ROSE, SKY][(y // 3 + k) % 4], emit=(y == y0 + 23), glass=k % 4 != 0)
    top = y0 + 29
    S.sphere(cx, top, cz, 13, GOLD_D, shell=2, fn=lambda x, y, z: None if y < 0 else pack(GOLD if (int(math.degrees(math.atan2(z, x)) + 360) // 15) % 2 else GOLD_D))
    for ring, (R_, L, n) in enumerate(((14, 12, 20), (11, 9, 14))):
        for k in range(n):
            th = 2 * math.pi * (k + 0.5 * ring) / n
            S.line((cx + math.cos(th) * R_, top + 2 + ring * 5, cz + math.sin(th) * R_),
                   (cx + math.cos(th) * (R_ + L), top + 3 + ring * 5 - L * 0.35, cz + math.sin(th) * (R_ + L)), 1.4 - ring * 0.3, GOLD if k % 2 else '#ffcf55')
    S.sphere(cx, top + 12, cz, 5, '#6e4a1e', fn=lambda x, y, z: pack(AMBER if (x + y + z) % 3 else '#6e4a1e', emit=(x + y + z) % 3 != 0))
    for (sx, sz) in ((x0 + 3, z0 + 3), (x1 - 3, z0 + 3), (x0 + 3, z1 - 3), (x1 - 3, z1 - 3)):
        S.cylinder(sx, sz, y0 + 1, y0 + 30, 2.5, WHITE)
        S.cone(sx, sz, y0 + 31, 18, 3, 0.3, GOLD)
        S.put(sx, y0 + 50, sz, SUN, emit=True)
    # the frozen Sun above, in its armillary, with chains of light to the towers
    sy = top + 70
    S.sphere(cx, sy, cz, 18, SUN_H, emit=True, shell=2.5, fn=lambda x, y, z: pack(SUN if x * x + y * y + z * z < 256 else (SUN_H if (x + y + z) % 3 else GOLD_L), emit=True))
    S.torus(cx, sy, cz, 28, 1.2, GOLD, tilt=0.0)
    S.torus(cx, sy, cz, 31, 1.0, GOLD_D, tilt=1.1, yaw=0.5)
    S.torus(cx, sy, cz, 34, 0.9, COPPER, tilt=-0.9, yaw=-0.7)
    for k in range(16):
        th = 2 * math.pi * k / 16
        L = 22 if k % 2 == 0 else 14
        S.line((cx + math.cos(th) * 20, sy, cz + math.sin(th) * 20), (cx + math.cos(th) * (20 + L), sy, cz + math.sin(th) * (20 + L)), 0.9, SUN_H, emit=True)
    for (sx, sz) in ((x0 + 3, z0 + 3), (x1 - 3, z0 + 3), (x0 + 3, z1 - 3), (x1 - 3, z1 - 3)):
        for i in range(0, 50):
            t = i / 50
            if i % 3 != 2:
                S.put(sx + (cx - sx) * t * 0.7, y0 + 50 + (sy - 18 - y0 - 50) * t, sz + (cz - sz) * t * 0.7, GOLD_L, emit=True)


def market():
    cells = [c for c, k in P.CELL.items() if k == 'market']
    xs, zs = [c[0] for c in cells], [c[1] for c in cells]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    y0 = max(H[c] for c in cells)
    for (x, z) in cells:
        for y in range(H[(x, z)] + 1, y0 + 1):
            S.put(x, y, z, STONE)
        S.put(x, y0, z, '#e9d9bc' if (x + z) % 2 else WHITE)
    wx = (x1 - x0) / 2
    cxm = (x0 + x1) / 2
    for x in range(x0, x1 + 1):                             # glass barrel vault on iron ribs
        for z in (z0, z1):
            for y in range(y0 + 1, y0 + 9):
                S.put(x, y, z, COPPER if x % 4 == 0 else (SKY if y > y0 + 3 else WHITE), glass=(x % 4 != 0 and y > y0 + 3))
        half = (z1 - z0) / 2
        for z in range(z0, z1 + 1):
            dz = z - (z0 + z1) / 2
            y = y0 + 9 + int(math.sqrt(max(0, half * half - dz * dz)) * 0.8)
            S.put(x, y, z, COPPER if x % 4 == 0 else SKY, glass=x % 4 != 0)
    for z in range(z0, z1 + 1):                             # end facades with the sunburst window
        for x in (x0, x1):
            dz = z - (z0 + z1) / 2
            for y in range(y0 + 1, y0 + 9 + int(math.sqrt(max(0, ((z1 - z0) / 2) ** 2 - dz * dz)) * 0.8)):
                ray = int((math.degrees(math.atan2(y - y0 - 9, dz)) + 360) // 15) % 2
                S.put(x, y, z, WHITE if y < y0 + 9 else (GOLD if ray else AMBER), emit=(y >= y0 + 9 and not ray), glass=(y >= y0 + 9 and not ray))
    # a transept dome in the middle
    S.sphere(round(cxm), y0 + 17, (z0 + z1) // 2, 7, GOLD_D, shell=1.5, fn=lambda x, y, z: None if y < 0 else pack(GOLD if (x + z) % 3 else SKY, glass=(x + z) % 3 == 0))
    S.put(round(cxm), y0 + 25, (z0 + z1) // 2, SUN_H, emit=True)
    # stalls with striped awnings inside
    for k, x in enumerate(range(x0 + 3, x1 - 2, 5)):
        for z in (z0 + 4, z1 - 4):
            col = [ROSE, SKY, GOLD, '#8fd18a', LILAC][k % 5]
            for dx in range(-1, 2):
                S.put(x + dx, y0 + 1, z, '#8a6440')
                S.put(x + dx, y0 + 4, z, col if (x + dx) % 2 else WHITE)


def clock_tower():
    lm = landmark('Torre del Reloj')
    if not lm:
        return
    x, z, _ = lm
    y0 = H.get((x, z), 20)
    for y in range(y0 + 1, y0 + 44):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if max(abs(dx), abs(dz)) == 3:
                    S.put(x + dx, y, z + dz, WHITE if (y - y0) % 8 else GOLD_D)
    for (dx, dz) in ((0, -4), (0, 4), (-4, 0), (4, 0)):     # clock faces
        for a in range(-2, 3):
            for b in range(-2, 3):
                if a * a + b * b <= 5:
                    px = x + dx + (a if dz else 0)
                    pz = z + dz + (a if dx else 0)
                    S.put(px, y0 + 36 + b, pz, SUN if a * a + b * b < 3 else GOLD, emit=a * a + b * b < 3)
    S.cone(x, z, y0 + 44, 12, 4.5, 0, VERD)
    S.put(x, y0 + 57, z, SUN_H, emit=True)


def temple():
    lm = landmark('Templo del Alba')
    if not lm:
        return
    x, z, _ = lm
    y0 = H.get((x, z), 20) + 1
    for dx in range(-7, 8):
        for dz in range(-9, 10):
            edge = abs(dx) == 7 or abs(dz) == 9
            for y in range(y0, y0 + (16 if edge else 1)):
                if edge and (dz % 3 == 0 or y < y0 + 3 or y > y0 + 13):
                    S.put(x + dx, y, z + dz, '#fbeef5')
                elif edge:
                    S.put(x + dx, y, z + dz, [ROSE, LILAC, GOLD_L][(y + dz) % 3], emit=(y % 5 == 0), glass=True)
                else:
                    S.put(x + dx, y, z + dz, WHITE)
        rr = 8 - abs(dx)
        S.put(x + dx, y0 + 16 + rr, z - 9, ROSE)
        for dz in range(-9, 10):
            S.put(x + dx, y0 + 16 + rr, z + dz, '#c9798a' if dz % 4 else GOLD)
    for a in range(-4, 5):                                  # the rose window
        for b in range(-4, 5):
            if a * a + b * b <= 16:
                S.put(x + a, y0 + 9 + b, z + 10, [ROSE, GOLD_L, LILAC, SKY][(abs(a) + abs(b)) % 4], emit=a * a + b * b < 4, glass=True)
    S.cylinder(x, z - 12, y0, y0 + 30, 3, '#fbeef5')         # bell tower
    S.cone(x, z - 12, y0 + 31, 10, 3.5, 0, '#b0569a')
    S.put(x, y0 + 42, z - 12, SUN_H, emit=True)


def workshop():
    lm = landmark('Taller de Terra')
    if not lm:
        return
    x, z, _ = lm
    y0 = H.get((x, z), 20) + 1
    for dx in range(-12, 13):
        for dz in range(-9, 10):
            edge = abs(dx) == 12 or abs(dz) == 9
            for y in range(y0, y0 + (10 if edge else 1)):
                S.put(x + dx, y, z + dz, '#d9d2e8' if (y % 3 or not edge) else COPPER)
            k = (dx + 12) % 6                                # sawtooth roof with north lights
            S.put(x + dx, y0 + 10 + min(k, 3), z + dz, '#6f4fc0' if k < 4 else SKY, glass=k >= 4)
    for (dx, dz) in ((-9, -6), (9, -6)):
        S.cylinder(x + dx, z + dz, y0 + 10, y0 + 26, 1.5, COPPER)
        S.put(x + dx, y0 + 27, z + dz, AMBER, emit=True)
    S.torus(x, y0 + 18, z + 10, 6, 0.9, GOLD, tilt=1.57)        # a great gear on the facade
    for k in range(12):
        th = 2 * math.pi * k / 12
        S.put(x + math.cos(th) * 7.5, y0 + 18 + math.sin(th) * 7.5, z + 10, GOLD_D)


def greenhouse():
    cells = [c for c, k in P.CELL.items() if k == 'greenhouse']
    if not cells:
        return
    cx = sum(c[0] for c in cells) // len(cells)
    cz = sum(c[1] for c in cells) // len(cells)
    y0 = H[(cx, cz)]
    S.sphere(cx, y0, cz, 9, SKY, glass=True, shell=1, fn=lambda x, y, z: None if y < 0 else pack(GOLD if (x % 3 == 0 or z % 3 == 0) else SKY, glass=not (x % 3 == 0 or z % 3 == 0)))
    tree(cx, cz, y0, 7, 3.5)


def plazas():
    for (es, en, x, z, kind) in P.LANDMARKS:
        y = H.get((x, z))
        if y is None:
            continue
        if es == 'Plaza Mayor':
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    d = math.hypot(dx, dz)
                    if d <= 5.4:
                        S.put(x + dx, y + 1, z + dz, WHITE if d > 4.4 else WATER, glass=d <= 4.4)
            for k in range(1, 9):
                S.put(x, y + k, z, GOLD if k < 7 else SUN_H, emit=k >= 7)
            for k in range(8):
                th = 2 * math.pi * k / 8
                S.line((x + math.cos(th) * 1, y + 6, z + math.sin(th) * 1), (x + math.cos(th) * 4, y + 2, z + math.sin(th) * 4), 0.4, WATER, glass=True)
        elif es == 'Plaza del Portal':
            for side in (-6, 6):
                for k in range(1, 13):
                    S.put(x + side, y + k, z, WHITE)
            for dx in range(-6, 7):
                S.put(x + dx, y + 13 + int(3 * math.cos(dx / 6 * math.pi / 2)), z, GOLD)
            S.cylinder(x, z, y + 1, y + 1, 3, SUN_H, emit=True)
        elif kind == 'plaza':
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    d = math.hypot(dx, dz)
                    if d <= 3.4:
                        S.put(x + dx, y + 1, z + dz, WHITE if d > 2.4 else WATER, glass=d <= 2.4)
            for k in range(1, 6):
                S.put(x, y + k, z, [GOLD, WHITE, GOLD, WHITE, SUN_H][k - 1], emit=k == 5)
        elif kind == 'small':
            tree(x, z, y, 7, 3.4)
        elif kind == 'mirador':
            for k in range(1, 22):
                for dx in range(-2, 3):
                    for dz in range(-2, 3):
                        if max(abs(dx), abs(dz)) == 2:
                            S.put(x + dx, y + k, z + dz, WHITE if k % 7 else GOLD_D)
            S.sphere(x, y + 23, z, 3, SUN_H, emit=True)
            S.cone(x, z, y + 26, 5, 2.5, 0, GOLD)
        elif kind == 'falls':
            for k in range(0, 120):
                f = k / 120
                for w in (-1, 0, 1):
                    if f < 0.6:
                        S.put(x + w, y - k, z, WATER, glass=True)
                    elif rnd(x + w, k, z, 31) < 0.45 * (1 - f) + 0.08:
                        S.put(x + w + round(f * 4), y - k, z, SUN_H, emit=True)


def build():
    t0 = time.time()
    P.plan()
    terrain()
    surfaces()
    cells = lots()
    buildings(cells)
    palace()
    market()
    clock_tower()
    temple()
    workshop()
    greenhouse()
    plazas()
    print(len(S), 'voxels', round(time.time() - t0, 1), 's')
    return S


if __name__ == '__main__':
    which = sys.argv[1] if len(sys.argv) > 1 else 'overview'
    os.makedirs(OUT, exist_ok=True)
    build()
    t0 = time.time()
    sky = ('#fff1cf', '#9cc4ec')
    if which in ('overview', 'all'):
        print(render(S, os.path.join(OUT, 'solsticio_city.png'), scale=2, sky=sky, bloom=5))
    if which in ('axis', 'all'):
        print(render(S, os.path.join(OUT, 'solsticio_city_axis.png'), scale=3, sky=sky, bloom=6,
                     crop=lambda x, y, z: -45 <= x <= 45 and -70 <= z <= 140 and y > -12))
    if which in ('market', 'all'):
        mx, mz = next((lx, lz) for (es, en, lx, lz, k) in P.LANDMARKS if es == 'Plaza del Mercado')
        print(render(S, os.path.join(OUT, 'solsticio_city_market.png'), scale=4, sky=sky, bloom=6,
                     crop=lambda x, y, z: mx - 30 <= x <= mx + 70 and mz - 45 <= z <= mz + 45 and y > -12))
    print('render', round(time.time() - t0, 1), 's')
