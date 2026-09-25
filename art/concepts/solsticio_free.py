"""Solsticio, free concept (no Minecraft palette, no scale limits): the city of the eternal noon.

A sunflower-geode island hangs in the between-light. Its underside is a cluster of glowing
crystals and hanging roots with rivers of light falling into the void. La Espiral, a helical
avenue, climbs two and a half turns round the mountain; freely turned houses line it on both
sides (tower houses with onion domes, arcaded halls, gabled homes, roof gardens). Grand stairs
and cascades cut straight down the slope. On the summit stands the Palace of the Solstice, a
sunflower dome of golden petals in a ring of eight spires with flying buttresses; above it the
frozen Sun floats inside an armillary of tilted rings, held by chains of light. Satellite islets
(greenhouse, observatory, windmill tavern, waterfall shrine) orbit on arched glass bridges; lanterns
drift in the air; a faint lattice of light closes the sky.

    python art/concepts/solsticio_free.py [overview|summit|spiral|all]
"""
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from freevox import Scene, pack, mix, noise2, fbm2, rnd, render, EMIT, GLASS  # noqa: E402

OUT = os.path.join(os.environ.get('ENTRELUMEN_CONCEPTS', os.path.join(HERE, 'out')))
S = Scene()

# ---------------- palette ----------------
WHITE, WARM, SHADE = '#f3ede2', '#e6d6b8', '#cbb999'
GOLD, GOLD_D, GOLD_L = '#f2c14e', '#c98f2a', '#ffe7a3'
COPPER, VERD, VERD_L = '#c8714f', '#4f9e7c', '#7cc9a4'
ROSE, ROSE_D, LILAC, SKY = '#eba3ad', '#c9798a', '#b9a3e3', '#9fd6ef'
TEAL, AMBER, SUN, SUN_H = '#46dcc9', '#ffb54a', '#fff6d2', '#ffd66b'
GRASS, GRASS_D, MOSS = '#86c24f', '#5f9a3b', '#6f9e4a'
BLOSSOM, BLOSSOM_D = '#f9bfd6', '#ec8fb4'
WATER = '#5cc4ea'
ROOFS = [VERD, '#3f8fb0', ROSE_D, '#d98a3d', '#8d6cc4', '#c95a4a', '#e2b23f']
WALLS = [WHITE, WARM, '#f0dcc8', '#e9e0f2', '#dcecf0', '#f6e6b8']

R_ISLAND = 150
SUMMIT = 96


# ---------------- island ----------------
def edge(x, z):
    th = math.atan2(z, x)
    return R_ISLAND * (1 + 0.07 * math.sin(5 * th + 0.4) + 0.05 * math.sin(3 * th + 2.1)) \
        + 22 * (fbm2(math.cos(th) * 60, math.sin(th) * 60, 40, 7) - 0.5)


HEIGHT = {}
BOTTOM = {}


def mountain(x, z):
    """A soft cone with a lumpy crown, flattened at the summit for the palace."""
    d = math.hypot(x, z)
    e = d / edge(x, z)
    h = SUMMIT * (1 - e) ** 1.35 + 14 * (fbm2(x, z, 38, 3) - 0.5) * (1 - e) + 6
    if d < 44:
        h = max(h, SUMMIT - 4)
    return h


def island():
    cols = []
    for x in range(-200, 201):
        for z in range(-200, 201):
            d = math.hypot(x, z)
            er = edge(x, z)
            if d >= er:
                continue
            e = d / er
            top = int(mountain(x, z))
            HEIGHT[(x, z)] = top
            cols.append((x, z, top, e))
    for (x, z, top, e) in cols:
        depth = int(26 + 120 * (1 - e) ** 1.6 + 16 * fbm2(x, z, 23, 11))
        bottom = top - depth
        BOTTOM[(x, z)] = bottom
        lo = min(HEIGHT.get((x + dx, z + dz), -999) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        rim = lo == -999
        for y in range(bottom, top + 1):
            k = top - y
            if not rim and k > 4 and y > bottom + 3 and y < lo - 2:
                continue
            if k == 0:
                c = mix(GRASS, GRASS_D, fbm2(x, z, 9, 2))
            elif k < 3:
                c = mix('#9a7b52', '#7a5d3a', rnd(x, y, z, 1))
            else:
                band = (y // 6) % 3
                c = ('#d9c7a8', '#c7b08d', '#e6d8bf')[band]
                if rnd(x, y, z, 5) < 0.004:
                    c = TEAL
            S[(x, y, z)] = pack(c, emit=(c == TEAL))
        # the underside: crystals and roots
        r = rnd(x, 0, z, 9)
        if r < 0.012 and e < 0.9:
            L = int(8 + 40 * rnd(x, 1, z, 9) * (1 - e))
            col = TEAL if rnd(x, 2, z, 9) < 0.6 else LILAC
            for i in range(L):
                w = max(0, int((L - i) / L * 2.5))
                for dx in range(-w, w + 1):
                    for dz in range(-w, w + 1):
                        if abs(dx) + abs(dz) <= w:
                            S[(x + dx, bottom - i, z + dz)] = pack(col, emit=True)
        elif r < 0.05:
            for i in range(int(3 + 10 * rnd(x, 3, z, 9))):
                S[(x, bottom - i, z)] = pack('#6b5a3a' if i < 4 else '#8fb85a')


def spiral_path():
    """Centre line of La Espiral: (x, z, y, heading) samples from the foot to the summit."""
    pts = []
    turns = 2.5
    n = 2600
    for i in range(n):
        t = i / (n - 1)
        th = -math.pi / 2 + turns * 2 * math.pi * t
        r = 128 - 84 * t
        x, z = r * math.cos(th), r * math.sin(th)
        y = mountain(x, z)
        pts.append((x, z, y, th))
    # smooth the road height along the path, never below the ground
    ys = [p[2] for p in pts]
    sm = []
    for i in range(n):
        lo, hi = max(0, i - 25), min(n, i + 26)
        sm.append(sum(ys[lo:hi]) / (hi - lo))
    return [(x, z, max(y0, y1), th) for (x, z, y0, th), y1 in zip(pts, sm)]


ROAD = {}


def espiral():
    pts = spiral_path()
    for (x, z, y, th) in pts:
        for w in range(-4, 5):
            rx, rz = -math.sin(th), math.cos(th)
            px, pz = round(x + math.cos(th) * w), round(z + math.sin(th) * w)
            yy = int(y)
            ROAD[(px, pz)] = yy
            c = GOLD if abs(w) == 4 else (WHITE if (px + pz) % 3 else WARM)
            S[(px, yy, pz)] = pack(c)
            for k in range(1, 7):
                S.pop((px, yy + k, pz), None)
            g = HEIGHT.get((px, pz), yy)
            for k in range(1, max(0, yy - g) + 1):
                S[(px, yy - k, pz)] = pack(SHADE if k % 4 else GOLD_D)
            HEIGHT[(px, pz)] = yy
    return pts


# ---------------- houses (freely turned) ----------------
def rot_box(cx, cz, y0, th, L, D, H, colour_fn):
    """Fill the box of length L (along th), depth D (across), height H, turned by th."""
    ca, sa = math.cos(th), math.sin(th)
    R = int(math.hypot(L, D) / 2) + 2
    for x in range(int(cx) - R, int(cx) + R + 1):
        for z in range(int(cz) - R, int(cz) + R + 1):
            dx, dz = x - cx, z - cz
            u = dx * ca + dz * sa
            v = -dx * sa + dz * ca
            if abs(u) > L / 2 or abs(v) > D / 2:
                continue
            for y in range(y0, y0 + H):
                c = colour_fn(u, v, y - y0, x, y, z)
                if c is not None:
                    S[(x, y, z)] = c


def house(cx, cz, y0, th, seed):
    kind = int(rnd(seed, 1, 2, 3) * 6)
    L = 8 + int(rnd(seed, 2, 2, 3) * 6)
    D = 8 + int(rnd(seed, 3, 2, 3) * 4)
    floors = 2 + int(rnd(seed, 4, 2, 3) * 3)
    H = floors * 5
    wall = WALLS[int(rnd(seed, 5, 2, 3) * len(WALLS))]
    roof = ROOFS[int(rnd(seed, 6, 2, 3) * len(ROOFS))]
    glass = [SKY, ROSE, GOLD_L, TEAL, LILAC][int(rnd(seed, 7, 2, 3) * 5)]
    # foundation down to the ground
    g = min(HEIGHT.get((int(cx + dx), int(cz + dz)), y0) for dx in (-4, 0, 4) for dz in (-4, 0, 4))
    rot_box(cx, cz, g - 2, th, L + 1, D + 1, y0 - g + 3,
            lambda u, v, k, x, y, z: pack(SHADE if (y % 4) else '#b9a47f'))

    def body(u, v, k, x, y, z):
        edge_u, edge_v = abs(u) > L / 2 - 1, abs(v) > D / 2 - 1
        if not (edge_u or edge_v):
            return pack(AMBER, emit=True) if (k % 5 == 2 and abs(u) < 1 and abs(v) < 1) else None
        if k % 5 == 0:
            return pack(GOLD_D if kind % 2 else '#b98a5a')
        if (k % 5) in (2, 3) and (round(u) % 3 == 0 if edge_v else round(v) % 3 == 0):
            return pack(AMBER if rnd(x, y, z, 4) < 0.18 else glass, emit=rnd(x, y, z, 4) < 0.18, glass=True)
        return pack(wall)
    rot_box(cx, cz, y0 + 1, th, L, D, H, body)
    top = y0 + 1 + H
    if kind in (0, 1):      # gable
        for r in range(0, D // 2 + 2):
            rot_box(cx, cz, top + r, th, L + 2, D + 2 - 2 * r, 1, lambda u, v, k, x, y, z: pack(roof if rnd(x, y, z, 8) > 0.08 else GOLD))
    elif kind == 2:         # onion dome tower
        tw = min(L, D) // 2
        for k in range(0, tw * 3):
            rr = tw * (1.0 + 0.5 * math.sin(math.pi * k / (tw * 3) * 1.15)) * (1 - k / (tw * 3)) ** 0.7
            S.cylinder(int(cx), int(cz), top + k, top + k, rr, roof if k % 4 else GOLD)
        S.line((cx, top + tw * 3, cz), (cx, top + tw * 3 + 6, cz), 0.6, GOLD)
        S.put(cx, top + tw * 3 + 7, cz, SUN_H, emit=True)
    elif kind == 3:         # roof garden with a pergola
        rot_box(cx, cz, top, th, L, D, 1, lambda u, v, k, x, y, z: pack(GRASS if rnd(x, y, z, 2) > 0.3 else BLOSSOM))
        rot_box(cx, cz, top + 1, th, L, D, 4, lambda u, v, k, x, y, z:
                pack('#b98a5a') if (abs(u) > L / 2 - 1 and abs(v) > D / 2 - 1) else None)
        rot_box(cx, cz, top + 5, th, L, D, 1, lambda u, v, k, x, y, z:
                pack(BLOSSOM if rnd(x, y, z, 3) > 0.4 else BLOSSOM_D) if rnd(x, y, z, 6) > 0.35 else None)
    elif kind == 4:         # stepped tower
        for s_ in range(3):
            w = L - 3 * s_
            if w < 3:
                break
            rot_box(cx, cz, top + s_ * 5, th, w, max(3, D - 3 * s_), 5, body)
        S.cone(int(cx), int(cz), top + 15, 8, 2.5, 0, roof)
    else:                   # arcade hall with a glass barrel roof
        for r in range(0, D // 2 + 1):
            rot_box(cx, cz, top + int(math.sqrt(max(0, (D / 2) ** 2 - (D / 2 - r) ** 2)) * 0.8), th, L, D - 2 * r, 1,
                    lambda u, v, k, x, y, z: pack(SKY if round(u) % 3 else GOLD, glass=round(u) % 3 != 0))
    # flower boxes and a lantern
    if rnd(seed, 9, 2, 3) < 0.6:
        S.put(cx + math.cos(th) * (L / 2 + 1), y0 + 4, cz + math.sin(th) * (L / 2 + 1), AMBER, emit=True)


def neighbourhood(pts):
    seed = 0
    for i in range(0, len(pts) - 20, 11):
        x, z, y, th = pts[i]
        for side in (-1, 1):
            off = 4 + 7
            nx, nz = math.cos(th) * side, math.sin(th) * side
            hx, hz = x + nx * off, z + nz * off
            if math.hypot(hx, hz) > edge(hx, hz) - 8:
                continue
            if any(abs(ROAD.get((round(hx + dx), round(hz + dz)), -99) - int(y)) < 2 and math.hypot(dx, dz) < 3
                   for dx in (-2, 0, 2) for dz in (-2, 0, 2)):
                continue
            seed += 1
            yy = int(max(y, HEIGHT.get((round(hx), round(hz)), y)))
            house(hx, hz, yy, th + math.pi / 2, seed)


def gardens():
    """Blossom trees and flower meadows on the open slopes."""
    for (x, z), top in list(HEIGHT.items()):
        if (x, z) in ROAD or math.hypot(x, z) < 46:
            continue
        r = rnd(x, 0, z, 21)
        if r < 0.006:
            h = 6 + int(rnd(x, 1, z, 21) * 6)
            S.line((x, top + 1, z), (x, top + h, z), 0.6, '#7a5230')
            rr = 3 + rnd(x, 2, z, 21) * 3
            S.sphere(x, top + h + 1, z, rr, BLOSSOM, fn=lambda a, b, c: (pack(BLOSSOM if rnd(a, b, c, 7) > 0.3 else BLOSSOM_D)
                                                                         if rnd(a, b, c, 8) > 0.2 else None))
        elif r < 0.05:
            S.put(x, top + 1, z, ['#f6e27a', '#f2a3c7', '#9ec7ff', '#ffffff', '#c7a3f2'][int(r * 100) % 5])


def cascades(pts):
    """Straight grand stairs from the foot to the summit, with a cascade beside each."""
    for ang in (math.pi / 2, math.pi / 2 + 2.3, math.pi / 2 - 2.3):
        prev = None
        for d in range(46, int(R_ISLAND) - 6):
            x, z = round(math.cos(ang) * d), round(math.sin(ang) * d)
            if (x, z) not in HEIGHT:
                break
            y = HEIGHT[(x, z)]
            for w in range(-3, 4):
                px, pz = round(x - math.sin(ang) * w), round(z + math.cos(ang) * w)
                if (px, pz) in ROAD:
                    continue
                S[(px, y, pz)] = pack(WHITE if abs(w) < 3 else GOLD)
                for k in range(1, 6):
                    S.pop((px, y + k, pz), None)
            for w in (5, 6):
                px, pz = round(x - math.sin(ang) * w), round(z + math.cos(ang) * w)
                if (px, pz) not in ROAD and (px, pz) in HEIGHT:
                    S[(px, HEIGHT[(px, pz)], pz)] = pack(WATER, glass=True)
            if d % 14 == 0:
                for side in (-4, 4):
                    px, pz = round(x - math.sin(ang) * side), round(z + math.cos(ang) * side)
                    S.line((px, y + 1, pz), (px, y + 7, pz), 0.5, GOLD_D)
                    S.put(px, y + 8, pz, SUN_H, emit=True)


def falls():
    """Rivers of light falling from the rim into the void."""
    for k in range(9):
        th = k * 2 * math.pi / 9 + 0.3
        r = edge(math.cos(th) * 100, math.sin(th) * 100) - 1
        x, z = round(math.cos(th) * r), round(math.sin(th) * r)
        top = HEIGHT.get((x, z))
        if top is None:
            continue
        for y in range(top - 150, top + 1):
            f = (top - y) / 150
            for w in (-1, 0, 1):
                px, pz = x + round(-math.sin(th) * w), z + round(math.cos(th) * w)
                if f < 0.55:
                    S[(px, y, pz)] = pack(WATER, glass=True)
                elif rnd(px, y, pz, 31) < 0.5 * (1 - f) + 0.08:
                    S[(px + round(math.cos(th) * (f * 6)), y, pz + round(math.sin(th) * (f * 6)))] = pack(SUN_H if rnd(px, y, pz, 32) < 0.5 else TEAL, emit=True)


# ---------------- the Palace of the Solstice and the Sun ----------------
def palace():
    y0 = SUMMIT - 4
    for x in range(-44, 45):
        for z in range(-44, 45):
            d = math.hypot(x, z)
            if d <= 44:
                c = GOLD if int(d) % 8 == 0 else (WHITE if int(d * 2 + math.atan2(z, x) * 12) % 2 else WARM)
                S[(x, y0, z)] = pack(c)
                for k in range(1, 12):
                    S.pop((x, y0 + k, z), None)
    # the drum: stained glass in sunburst colours between golden ribs
    S.cylinder(0, 0, y0 + 1, y0 + 24, 20, WHITE, shell=2)
    for y in range(y0 + 3, y0 + 23):
        for k in range(48):
            th = 2 * math.pi * k / 48
            x, z = round(math.cos(th) * 20), round(math.sin(th) * 20)
            if k % 4 == 0:
                S.put(x, y, z, GOLD)
            else:
                S.put(x, y, z, [GOLD_L, AMBER, ROSE, SKY][(y // 4 + k) % 4], emit=(y % 10 == 0), glass=True)
    # the sunflower dome: golden petals in two rings round a seed disc of glass
    top = y0 + 25
    S.sphere(0, top, 0, 18, GOLD_D, shell=2, fn=lambda x, y, z: (None if y < 0 else pack(GOLD if (int(math.degrees(math.atan2(z, x)) + 360) // 15) % 2 else GOLD_D)))
    for ring, (R, L, n) in enumerate(((19, 16, 24), (15, 12, 16))):
        for k in range(n):
            th = 2 * math.pi * (k + 0.5 * ring) / n
            bx, bz = math.cos(th) * R, math.sin(th) * R
            tip = (math.cos(th) * (R + L), top + 4 + ring * 6 - L * 0.35, math.sin(th) * (R + L))
            S.line((bx, top + 2 + ring * 6, bz), tip, 1.6 - ring * 0.4, GOLD if k % 2 else '#ffcf55')
    S.sphere(0, top + 16, 0, 7, '#6e4a1e', fn=lambda x, y, z: pack(AMBER if (x + y + z) % 3 else '#6e4a1e', emit=(x + y + z) % 3 != 0))
    # eight spires with flying buttresses to the drum
    for k in range(8):
        th = 2 * math.pi * k / 8 + math.pi / 8
        sx, sz = round(math.cos(th) * 36), round(math.sin(th) * 36)
        S.cylinder(sx, sz, y0 + 1, y0 + 34, 3, WHITE)
        for y in range(y0 + 4, y0 + 34, 6):
            S.put(sx + round(math.cos(th) * 3), y, sz + round(math.sin(th) * 3), AMBER, emit=True, glass=True)
        S.cone(sx, sz, y0 + 35, 26, 3.5, 0.3, GOLD)
        S.put(sx, y0 + 62, sz, SUN, emit=True)
        for yy in (y0 + 14, y0 + 26):
            S.line((sx, yy + 6, sz), (math.cos(th) * 21, yy + 10, math.sin(th) * 21), 1.1, WHITE)
    # grand stair down to La Espiral's end, south
    for d in range(44, 60):
        for w in range(-6, 7):
            S.put(w, y0 - (d - 44) // 2, d, WHITE if abs(w) < 6 else GOLD)


def sun():
    cy = SUMMIT + 96
    S.sphere(0, cy, 0, 22, SUN, emit=True, shell=3)
    S.sphere(0, cy, 0, 25, SUN_H, emit=True, shell=1.2, fn=lambda x, y, z: pack(SUN_H, emit=True, glass=True) if rnd(x, y, z, 41) < 0.35 else None)
    # the armillary: three tilted rings and an equator with zodiac studs
    S.torus(0, cy, 0, 38, 1.4, GOLD, tilt=0.0)
    S.torus(0, cy, 0, 42, 1.2, GOLD_D, tilt=1.1, yaw=0.5)
    S.torus(0, cy, 0, 46, 1.0, COPPER, tilt=-0.9, yaw=-0.7)
    S.torus(0, cy, 0, 50, 0.8, VERD_L, tilt=1.57, yaw=1.2)
    for k in range(12):
        th = 2 * math.pi * k / 12
        S.sphere(round(math.cos(th) * 38), cy, round(math.sin(th) * 38), 2.2, TEAL, emit=True)
    # rays: long spikes of light
    for k in range(16):
        th = 2 * math.pi * k / 16
        L = 36 if k % 2 == 0 else 26
        S.line((math.cos(th) * 25, cy, math.sin(th) * 25), (math.cos(th) * (25 + L), cy, math.sin(th) * (25 + L)), 1.0, SUN_H, emit=True)
    # chains of light down to the spires
    for k in range(8):
        th = 2 * math.pi * k / 8 + math.pi / 8
        sx, sz = math.cos(th) * 36, math.sin(th) * 36
        n = 40
        for i in range(n + 1):
            t = i / n
            x = sx + (math.cos(th) * 30 - sx) * t
            z = sz + (math.sin(th) * 30 - sz) * t
            y = SUMMIT + 58 + (cy - 20 - SUMMIT - 58) * t - 8 * math.sin(math.pi * t)
            if i % 3 != 2:
                S.put(x, y, z, GOLD_L, emit=True)


# ---------------- satellites, bridges, lanterns, the lattice ----------------
def islet(cx, cy, cz, r, kind):
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            d = math.hypot(x, z)
            if d > r:
                continue
            depth = int((r - d) * 1.4 + 3)
            for y in range(-depth, 1):
                S.put(cx + x, cy + y, cz + z, GRASS if y == 0 else '#c7b08d' if y > -3 else '#b59c78')
            if rnd(x, 0, z, 51) < 0.03 and d > 2:
                for i in range(int(4 + rnd(x, 1, z, 51) * 10)):
                    S.put(cx + x, cy - depth - i, cz + z, TEAL, emit=True)
    if kind == 'greenhouse':
        S.sphere(cx, cy, cz, r - 3, SKY, glass=True, shell=1, fn=lambda x, y, z: None if y < 0 else pack(GOLD if (x % 4 == 0 or z % 4 == 0) else SKY, glass=not (x % 4 == 0 or z % 4 == 0)))
        S.line((cx, cy, cz), (cx, cy + 9, cz), 1, '#7a5230')
        S.sphere(cx, cy + 11, cz, 5, BLOSSOM, fn=lambda x, y, z: pack(BLOSSOM if rnd(x, y, z, 9) > 0.3 else BLOSSOM_D))
    elif kind == 'observatory':
        S.cylinder(cx, cz, cy + 1, cy + 22, 4, WHITE)
        S.sphere(cx, cy + 23, cz, 6, VERD, fn=lambda x, y, z: None if y < 0 else pack(VERD if abs(x) > 0 else GOLD))
        S.line((cx, cy + 26, cz), (cx + 9, cy + 34, cz + 3), 1.3, COPPER)
        S.torus(cx, cy + 12, cz, 9, 0.7, GOLD, tilt=0.4)
    elif kind == 'windmill':
        S.cone(cx, cz, cy + 1, 16, 5, 3, WARM)
        S.cone(cx, cz, cy + 17, 5, 4, 0, ROSE_D)
        for k in range(4):
            th = k * math.pi / 2 + 0.3
            S.line((cx + 5, cy + 14, cz), (cx + 5, cy + 14 + math.sin(th) * 14, cz + math.cos(th) * 14), 0.8, WHITE)
        S.put(cx + 5, cy + 14, cz, AMBER, emit=True)
    else:                   # the waterfall shrine
        S.cylinder(cx, cz, cy + 1, cy + 6, 3, WHITE, shell=1)
        S.cone(cx, cz, cy + 7, 4, 4, 0, GOLD)
        for y in range(cy - 90, cy + 1):
            f = (cy - y) / 90
            if f < 0.6 or rnd(cx, y, cz, 61) < 0.4:
                S.put(cx + r, y, cz, WATER if f < 0.6 else SUN_H, emit=f >= 0.6, glass=f < 0.6)


def bridge(a, b, lift):
    n = int(math.dist(a, b) * 1.5)
    for i in range(n + 1):
        t = i / n
        x = a[0] + (b[0] - a[0]) * t
        z = a[2] + (b[2] - a[2]) * t
        y = a[1] + (b[1] - a[1]) * t + lift * math.sin(math.pi * t)
        hx, hz = -(b[2] - a[2]), (b[0] - a[0])
        L = math.hypot(hx, hz) or 1
        hx, hz = hx / L, hz / L
        for w in range(-2, 3):
            S.put(x + hx * w, y, z + hz * w, SKY if abs(w) < 2 else GOLD, glass=abs(w) < 2)
        if i % 6 == 0:
            for w in (-2, 2):
                S.put(x + hx * w, y + 1, z + hz * w, GOLD_D)
                S.put(x + hx * w, y + 2, z + hz * w, AMBER, emit=True)


def satellites():
    spots = [(math.radians(20), 205, 40, 16, 'greenhouse'), (math.radians(135), 215, 70, 13, 'observatory'),
             (math.radians(245), 205, 25, 14, 'windmill'), (math.radians(310), 195, 55, 11, 'shrine')]
    for (th, R, y, r, kind) in spots:
        cx, cz = round(math.cos(th) * R), round(math.sin(th) * R)
        islet(cx, y, cz, r, kind)
        ex = edge(math.cos(th) * 100, math.sin(th) * 100) - 4
        ax, az = round(math.cos(th) * ex), round(math.sin(th) * ex)
        ay = HEIGHT.get((ax, az), 20)
        bridge((ax, ay + 1, az), (cx - math.cos(th) * r, y + 1, cz - math.sin(th) * r), 14)


def lanterns():
    for k in range(260):
        x = (rnd(k, 1, 0, 71) - 0.5) * 460
        z = (rnd(k, 2, 0, 71) - 0.5) * 460
        y = 30 + rnd(k, 3, 0, 71) * 170
        if math.hypot(x, z) < 50 and y > SUMMIT:
            continue
        S.put(x, y, z, AMBER, emit=True)
        S.put(x, y + 1, z, GOLD_D)


def lattice():
    """The barrier of light: a faint geodesic lattice, only on the far half so the city stays clear."""
    R = 280
    for k in range(18):
        lon = 2 * math.pi * k / 18
        for i in range(400):
            lat = -0.2 + (math.pi / 2 + 0.2) * i / 400
            x = R * math.cos(lat) * math.cos(lon)
            z = R * math.cos(lat) * math.sin(lon)
            y = -40 + R * math.sin(lat)
            if x + z < -60 and i % 2 == 0:
                S.put(x, y, z, SUN_H, emit=True, glass=True)
    for j in range(1, 6):
        lat = j * 0.28
        for i in range(900):
            lon = 2 * math.pi * i / 900
            x = R * math.cos(lat) * math.cos(lon)
            z = R * math.cos(lat) * math.sin(lon)
            if x + z < -60 and i % 2 == 0:
                S.put(x, -40 + R * math.sin(lat), z, SUN_H, emit=True, glass=True)


# ---------------- v2: the twin city, the tree, airships, birds, the corona ----------------
DUSK = ['#2b2f6b', '#3d3a8a', '#5a3f94', '#1f5d7a', '#6b3f7a']


def twin_city():
    """Under the island hangs its twin in an eternal dusk: towers upside down, lit windows,
    rope bridges between them and crystal spikes at their tips."""
    tips = []
    k = 0
    for (x, z), b in sorted(BOTTOM.items()):
        if (x * 7 + z * 13) % 211 != 0:
            continue
        d = math.hypot(x, z)
        if d > edge(x, z) * 0.8 or d < 20:
            continue
        k += 1
        L = int(14 + 50 * rnd(x, 7, z, 81) * (1 - d / 170))
        r = 3 + int(rnd(x, 8, z, 81) * 4)
        wall = DUSK[int(rnd(x, 9, z, 81) * len(DUSK))]
        S.cylinder(x, z, b - L, b, r, wall, shell=1)
        for y in range(b - L + 2, b - 1, 3):
            for a in range(0, 360, 45):
                wx, wz = round(x + math.cos(math.radians(a)) * r), round(z + math.sin(math.radians(a)) * r)
                if rnd(wx, y, wz, 82) < 0.6:
                    S.put(wx, y, wz, AMBER if rnd(wx, y, wz, 83) < 0.7 else TEAL, emit=True, glass=True)
        for j in range(0, 3):
            S.cylinder(x, z, b - L + j * 6, b - L + j * 6, r + 1.5, GOLD_D)
        S.cone(x, z, b - L - 14, 14, 0.3, r + 1, DUSK[(k + 1) % len(DUSK)])
        for i in range(8):
            S.put(x, b - L - 15 - i, z, TEAL if i < 5 else SUN_H, emit=True)
        tips.append((x, b - L + 4, z))
    for i in range(len(tips) - 1):
        a, c = tips[i], tips[i + 1]
        if math.dist(a, c) < 45:
            n = int(math.dist(a, c) * 1.5)
            for j in range(n + 1):
                t = j / n
                px, py, pz = a[0] + (c[0] - a[0]) * t, a[1] + (c[1] - a[1]) * t - 6 * math.sin(math.pi * t), a[2] + (c[2] - a[2]) * t
                S.put(px, py, pz, '#8a6a3a')
                if j % 5 == 0:
                    S.put(px, py - 1, pz, AMBER, emit=True)


def world_tree():
    """A tree of light on the western shoulder: roots over the rim, a glowing canopy."""
    th = math.radians(200)
    R = edge(math.cos(th) * 100, math.sin(th) * 100) - 22
    cx, cz = round(math.cos(th) * R), round(math.sin(th) * R)
    base = HEIGHT.get((cx, cz), 20)
    H = 78
    for y in range(base - 4, base + H):
        t = (y - base) / H
        r = 7.5 * (1 - t) ** 0.8 + 2.2 + (3 if y < base + 4 else 0)
        wob = 2.5 * math.sin(t * 5)
        S.cylinder(round(cx + wob), cz, y, y, r, '#8a6440' if (y // 3) % 2 else '#7a5634')
    for k in range(7):                                  # branches
        a = 2 * math.pi * k / 7
        y0 = base + 40 + k * 4
        S.line((cx, y0, cz), (cx + math.cos(a) * 34, y0 + 18, cz + math.sin(a) * 34), 2.0 - k * 0.15, '#7a5634')
        S.sphere(round(cx + math.cos(a) * 34), y0 + 22, round(cz + math.sin(a) * 34), 13,
                 BLOSSOM, fn=lambda x, y, z: (pack(SUN_H, emit=True) if rnd(x, y, z, 91) < 0.05 else
                                              pack(BLOSSOM if rnd(x, y, z, 92) > 0.35 else BLOSSOM_D)) if rnd(x, y, z, 93) > 0.3 else None)
    S.sphere(cx, base + H + 4, cz, 20, BLOSSOM, fn=lambda x, y, z: (pack(SUN_H, emit=True) if rnd(x, y, z, 94) < 0.05 else
                                                                     pack(BLOSSOM if rnd(x, y, z, 95) > 0.35 else BLOSSOM_D)) if rnd(x, y, z, 96) > 0.3 else None)
    for k in range(10):                                 # roots spilling over the rim
        a = th + (k - 4.5) * 0.12
        pts = [(cx, base, cz)]
        for step in range(1, 9):
            rr = R + step * 4
            pts.append((math.cos(a) * rr, base - step * step * 0.9, math.sin(a) * rr))
        for i in range(len(pts) - 1):
            S.line(pts[i], pts[i + 1], 1.6 - i * 0.12, '#6b4a2c')


def airship(cx, cy, cz, heading, size):
    ca, sa = math.cos(heading), math.sin(heading)
    L, Rr = size, size * 0.32
    for i in range(-int(L), int(L) + 1):
        rr = Rr * math.sqrt(max(0, 1 - (i / L) ** 2))
        for a in range(0, 360, 4):
            v = rr * math.cos(math.radians(a))
            y = rr * math.sin(math.radians(a))
            x, z = cx + ca * i - sa * v, cz + sa * i + ca * v
            stripe = (a // 30) % 2
            S.put(x, cy + y, z, ROSE if stripe else GOLD_L)
    for i in range(-int(L * 0.4), int(L * 0.4) + 1):     # gondola
        for v in (-1, 0, 1):
            S.put(cx + ca * i - sa * v, cy - Rr - 4, cz + sa * i + ca * v, '#8a6440')
            if v == 0 and i % 3 == 0:
                S.put(cx + ca * i, cy - Rr - 3, cz + sa * i, AMBER, emit=True)
    for i in (-int(L * 0.3), int(L * 0.3)):
        S.line((cx + ca * i, cy - Rr, cz + sa * i), (cx + ca * i, cy - Rr - 4, cz + sa * i), 0.4, GOLD_D)
    tx, tz = cx - ca * (L + 2), cz - sa * (L + 2)       # tail fins and a propeller
    S.line((tx, cy - Rr * 0.8, tz), (tx, cy + Rr * 0.8, tz), 0.6, GOLD)
    S.line((tx - sa * Rr * 0.8, cy, tz + ca * Rr * 0.8), (tx + sa * Rr * 0.8, cy, tz - ca * Rr * 0.8), 0.6, GOLD)


def birds():
    for k in range(40):
        x = (rnd(k, 5, 1, 97) - 0.5) * 380
        z = (rnd(k, 6, 1, 97) - 0.5) * 380
        y = 110 + rnd(k, 7, 1, 97) * 90
        for d in (-2, -1, 1, 2):
            S.put(x + d, y - abs(d) * 0.6 + 1, z, '#4a4458')
        S.put(x, y, z, '#4a4458')


def corona():
    cy = SUMMIT + 96
    S.sphere(0, cy, 0, 21, '#fffbe8', emit=True, shell=2)
    for k in range(24):                                 # petals of light round the sun
        th = 2 * math.pi * k / 24
        for rr in range(26, 34):
            w = (34 - rr) / 8
            for dz in (-1, 0, 1):
                if rnd(k, rr, dz, 99) < 0.5 + 0.4 * w:
                    x = math.cos(th) * rr
                    z = math.sin(th) * rr
                    S.put(x - math.sin(th) * dz * w, cy + (rr - 26) * 0.2 * math.sin(3 * th), z + math.cos(th) * dz * w,
                          SUN_H if w > 0.4 else GOLD_L, emit=True, glass=True)


def build():
    t0 = time.time()
    island()
    pts = espiral()
    neighbourhood(pts)
    cascades(pts)
    gardens()
    falls()
    palace()
    sun()
    satellites()
    lanterns()
    lattice()
    twin_city()
    world_tree()
    airship(-150, 150, 60, 0.6, 22)
    airship(120, 175, -150, 2.4, 16)
    airship(-60, 200, -190, -0.4, 12)
    birds()
    corona()
    print(len(S), 'voxels', round(time.time() - t0, 1), 's')
    return S


if __name__ == '__main__':
    which = sys.argv[1] if len(sys.argv) > 1 else 'overview'
    os.makedirs(OUT, exist_ok=True)
    build()
    t0 = time.time()
    if which in ('overview', 'all'):
        print(render(S, os.path.join(OUT, 'solsticio_free.png'), scale=1, sky=('#fff1cf', '#9cc4ec'), bloom=4))
    if which in ('summit', 'all'):
        print(render(S, os.path.join(OUT, 'solsticio_free_summit.png'), scale=3, sky=('#fff1cf', '#9cc4ec'), bloom=8,
                     crop=lambda x, y, z: math.hypot(x, z) < 75 and y > SUMMIT - 30))
    if which in ('twin', 'all'):
        from freevox import Scene as _Scene
        from PIL import Image as _Image
        below = _Scene({(x, -y, -z): v for (x, y, z), v in S.items() if y < 20 and math.hypot(x, z) < 180})
        path = os.path.join(OUT, 'solsticio_free_twin.png')
        print(render(below, path, scale=2, sky=('#241c4a', '#8fa6d8'), bloom=6))
        _Image.open(path).transpose(_Image.FLIP_TOP_BOTTOM).save(path)
    if which in ('spiral', 'all'):
        print(render(S, os.path.join(OUT, 'solsticio_free_spiral.png'), scale=3, sky=('#fff1cf', '#9cc4ec'), bloom=8,
                     crop=lambda x, y, z: 20 < x < 150 and -40 < z < 90 and y > -10))
    print('render', round(time.time() - t0, 1), 's')
