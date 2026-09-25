"""Solsticio v2: a terraced citadel under the domed palace (docs/design/solsticio-city-v2.md).

The island climbs in four organic terraces (squarish superellipse contours, warped, each centred a
little further north, so the south opens wide and the north is steep) from the lower town to the
summit, where the palace stands: a colonnaded front over a grand stair, a golden dome with a sun
oculus, corner pavilions and the sun tower. Every terrace has a balustraded promenade on its outer
edge, a continuous row of three- and four-storey buildings facing out, and a back lane under the
retaining wall of the next terrace. Monumental stairs climb the four axes.

Ground follows city5's rules: pinned levels, faces closed against the hollow, barrier of light.

    python art/solsticio/city7.py [--export path/to/city.nbt]
"""
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import city3  # noqa: E402
import city5  # noqa: E402
import city6  # noqa: E402,F401  (dresses city5.building with the pack's decorative mods)
from voxkit import Voxels, orient  # noqa: E402

B = city5.B
AIR = city5.AIR
V = Voxels()
EXTRA = {}
MARKERS = []
city3.V, city3.EXTRA, city3.MARKERS = V, EXTRA, MARKERS
_h, vnoise = city5._h, city5.vnoise
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

LEVELS = [40, 30, 20, 10, 0]            # summit, terraces 1..3, lower town (top block y)
RADII = [38, 58, 78, 98]                # outer contour of summit and terraces 1..3
CENTRES = [(0, -12), (0, -8), (0, -5), (0, -2)]
SQUARE = 2.7                            # superellipse exponent: squarish, with rounded corners
EXT = 140
PROMENADE = 5                           # balustrade + promenade width on each terrace
DEPTH = 10                              # building depth


# ---------------- plan ----------------
def island_edge(x, z):
    th = math.atan2(z, x)
    return 120 + 5 * math.sin(3 * th + 0.7) + 4 * math.sin(5 * th + 2.0) + 8 * (vnoise(math.cos(th) * 30, math.sin(th) * 30, 13, 91) - 0.5)


def inside(x, z):
    return math.hypot(x, z) < island_edge(x, z)


def contour(k, x, z):
    """Distance-like measure of (x, z) against the contour of step k (negative inside)."""
    cx, cz = CENTRES[k]
    dx, dz = x - cx, z - cz
    r = (abs(dx) ** SQUARE + abs(dz) ** SQUARE) ** (1 / SQUARE)
    th = math.atan2(dz, dx)
    warp = 1 + 0.035 * math.sin(3 * th + k * 1.7) + 0.05 * (vnoise(math.cos(th) * 25 + k * 40, math.sin(th) * 25, 11, 70 + k) - 0.5)
    return r * warp - RADII[k]


def level(x, z):
    for k in range(4):
        if contour(k, x, z) < 0:
            return k
    return 4 if inside(x, z) else None


def depth_in(k, x, z):
    """Blocks from the outer edge of step k inward."""
    if k == 4:
        return island_edge(x, z) - math.hypot(x, z)
    return -contour(k, x, z)


def outward(k, x, z):
    """Cardinal direction pointing out of step k at (x, z)."""
    cx, cz = CENTRES[min(k, 3)]
    dx, dz = x - cx, z - cz
    return ((1 if dx > 0 else -1), 0) if abs(dx) >= abs(dz) else (0, (1 if dz > 0 else -1))


LEVEL = {}
TOP = {}
USED = set()
RESERVED = set()


def plan():
    for x in range(-EXT, EXT + 1):
        for z in range(-EXT, EXT + 1):
            k = level(x, z)
            if k is not None:
                LEVEL[(x, z)] = k
                TOP[(x, z)] = LEVELS[k]


# ---------------- ground ----------------
WALL = ('tuff_bricks', 'tuff_bricks', 'polished_tuff', 'tuff_bricks', 'chiseled_tuff_bricks')


def depth_at(c):
    e = math.hypot(*c) / island_edge(*c)
    return int(10 + (1 - e) ** 0.6 * 62)


def fill():
    for (x, z), top in TOP.items():
        e = math.hypot(x, z) / island_edge(x, z)
        bottom = top - depth_at((x, z))
        tops, bottoms = [], []
        for dx, dz in N4:
            n = (x + dx, z + dz)
            if n in TOP:
                tops.append(TOP[n])
                bottoms.append(TOP[n] - depth_at(n))
        low = min(tops) if tops else top
        face_top = low - 2
        face_bottom = (max(bottoms) + 3) if bottoms else bottom + 3
        wall = len(tops) == 4 and low < top - 1
        for y in range(bottom, top + 1):
            k = top - y
            if k > 5 and y < face_top and y - bottom > 3 and y > face_bottom and e < 0.97:
                continue
            if (x, y, z) in V:
                continue
            if wall and y > low:
                band = (top - y) % 5
                blk = 'calcite' if k == 0 else WALL[band]
                if band == 2 and (x * 7 + z * 3) % 9 == 0 and k >= 2:
                    blk = 'ochre_froglight'
            else:
                blk = 'grass_block' if k == 0 else 'dirt' if k <= 3 else \
                    ('calcite' if y % 9 == 0 else 'tuff' if (x + z + y) % 5 == 0 else 'stone')
            V[(x, y, z)] = B(blk)
        if e < 0.9 and (x * 5 + z * 3) % 13 == 0:
            for dy in range(1, 2 + abs(x + z) % 4):
                V.setdefault((x, bottom - dy, z), B('hanging_roots'))


def wall_state(x, y, z, cells, post):
    conn = {INV: ('low' if (x + dx, z + dz) in cells else 'none') for INV, (dx, dz) in
            (('east', (1, 0)), ('west', (-1, 0)), ('south', (0, 1)), ('north', (0, -1)))}
    straight = (conn['east'] == conn['west'] == 'low' and conn['north'] == conn['south'] == 'none') or \
               (conn['north'] == conn['south'] == 'low' and conn['east'] == conn['west'] == 'none')
    up = 'true' if post or not straight else 'false'
    return B('polished_diorite_wall[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (
        conn['east'], conn['north'], conn['south'], up, conn['west']))


def promenades():
    """Balustrade on every edge above a drop, a paved promenade behind it, lamps and benches."""
    rail = set()
    for (x, z), top in TOP.items():
        if (x, z) in RESERVED or (x, z) in LOTCELLS:
            continue
        drop = any(TOP.get((x + dx, z + dz), -99) < top - 1 for dx, dz in N4)
        if drop:
            rail.add((x, z))
    for (x, z) in rail:
        top = TOP[(x, z)]
        post = (x + 2 * z) % 6 == 0
        V[(x, top + 1, z)] = wall_state(x, top + 1, z, rail, post)
        if post and (x * 3 + z) % 18 == 0:
            V[(x, top + 2, z)] = B('lantern[hanging=false,waterlogged=false]')
    for (x, z), k in LEVEL.items():
        if (x, z) in rail or (x, z) in RESERVED or (x, z) in USED:
            continue
        d = depth_in(k, x, z)
        if d < PROMENADE:
            top = TOP[(x, z)]
            V[(x, top, z)] = B('polished_diorite' if (x + z) % 4 else 'calcite') if d >= 1.5 else B('calcite')
            USED.add((x, z))


# ---------------- stairs ----------------
AXES = [((0, 1), 9), ((0, -1), 5), ((1, 0), 5), ((-1, 0), 5)]


def stairs():
    """Monumental flights on the four axes: at each drop, ten steps down onto the lower terrace."""
    for (v, width) in AXES:
        e = (v[1], -v[0])
        hw = width // 2
        start = CENTRES[0]
        pos = start
        prev = LEVEL.get(pos)
        for t in range(1, EXT * 2):
            c = (start[0] + v[0] * t, start[1] + v[1] * t)
            k = LEVEL.get(c)
            if k is None:
                break
            if prev is not None and k != prev and TOP[c] < LEVELS[prev]:
                hi = LEVELS[prev]
                steps = hi - TOP[c]
                for i in range(-1, steps + 3):
                    for u in range(-hw - 1, hw + 2):
                        cc = (c[0] + v[0] * i + e[0] * u, c[1] + v[1] * i + e[1] * u)
                        if cc not in TOP or (i < 0 and TOP[cc] != hi):
                            continue
                        RESERVED.add(cc)
                        USED.add(cc)
                        if i >= steps or i < 0:
                            continue
                        y = hi - i
                        if abs(u) == hw + 1:
                            for yy in range(TOP[cc] + 1, y + 1):
                                V[(cc[0], yy, cc[1])] = B('tuff_bricks')
                            V[(cc[0], y + 1, cc[1])] = B('polished_diorite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
                            if i % 4 == 0:
                                V[(cc[0], y + 2, cc[1])] = B('lantern[hanging=false,waterlogged=false]')
                            continue
                        for yy in range(TOP[cc] + 1, y):
                            V[(cc[0], yy, cc[1])] = B('tuff_bricks')
                        facing = {(0, 1): 'north', (0, -1): 'south', (1, 0): 'west', (-1, 0): 'east'}[v]
                        V[(cc[0], y, cc[1])] = B('polished_diorite_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % facing) \
                            if abs(u) < hw or width < 7 else B('calcite_slab[type=bottom,waterlogged=false]'.replace('calcite_slab', 'polished_diorite_slab'))
                        if abs(u) == hw and width >= 7:
                            V[(cc[0], y, cc[1])] = B('polished_diorite_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % facing)
                # a gate arch at the foot of the flight
                foot = (c[0] + v[0] * (steps + 1), c[1] + v[1] * (steps + 1))
                ft = TOP.get(foot)
                if ft is not None:
                    for u in (-hw - 1, hw + 1):
                        px, pz = foot[0] + e[0] * u, foot[1] + e[1] * u
                        for yy in range(ft + 1, ft + 8):
                            V[(px, yy, pz)] = B('quartz_pillar[axis=y]')
                        V[(px, ft + 8, pz)] = B('chiseled_quartz_block')
                    for u in range(-hw - 1, hw + 2):
                        px, pz = foot[0] + e[0] * u, foot[1] + e[1] * u
                        V[(px, ft + 9, pz)] = B('waxed_cut_copper')
                        if abs(u) <= 1:
                            V[(px, ft + 10, pz)] = B('ochre_froglight') if u == 0 else B('waxed_cut_copper_slab[type=bottom,waterlogged=false]')
            prev = k


# ---------------- building rows ----------------
LOTS = []
LOTCELLS = set()


def rows():
    """Greedy packing along every terrace: a lot's front stands at the promenade's inner edge and
    faces out; widths 11, 9 or 7; neighbours touch, so the row reads as one continuous street."""
    cands = []
    for (x, z), k in LEVEL.items():
        if k == 0:
            continue
        d = depth_in(k, x, z)
        if PROMENADE <= d < PROMENADE + 1:
            cx, cz = CENTRES[min(k, 3)]
            cands.append((k, math.atan2(z - cz, x - cx), x, z))
    cands.sort()
    for k, _, x, z in cands:
        if (x, z) in USED:
            continue
        out = outward(k, x, z)
        s = (-out[0], -out[1])                     # from the front into the building
        e = (s[1], -s[0])
        for hw in (5, 4, 3):
            cells = [(x + u * e[0] + w * s[0], z + u * e[1] + w * s[1]) for u in range(-hw, hw + 1) for w in range(DEPTH)]
            ok = True
            for c in cells:
                if c in USED or LEVEL.get(c) != k:
                    ok = False
                    break
                d = depth_in(k, *c)
                if d < PROMENADE - 1.2 or d > PROMENADE + DEPTH + 4:
                    ok = False
                    break
            if not ok:
                continue
            LOTS.append((x, z, s, e, TOP[(x, z)], k, len(LOTS), hw))
            for c in cells:
                USED.add(c)
                LOTCELLS.add(c)
            break


def place_lots():
    shops = list(city5.SHOP_TYPES)
    front = sorted(range(len(LOTS)), key=lambda i: (LOTS[i][5] != 2, abs(math.atan2(LOTS[i][0], LOTS[i][1]))))
    kinds = {}
    for n, i in enumerate(front[:len(shops)]):
        kinds[i] = 'shop:' + shops[n]
    for q in range(4):
        cand = [i for i in front[len(shops):] if LOTS[i][5] == 3 and int((math.degrees(math.atan2(LOTS[i][1], LOTS[i][0])) + 360) // 90) % 4 == q]
        if cand:
            kinds[cand[0]] = 'inn'
    for i, (fx, fz, s, e, pad, k, idx, hw) in enumerate(LOTS):
        kind = kinds.get(i) or ('resident' if i % 3 == 0 else 'house')
        st = dict(city5.STYLES[idx % len(city5.STYLES)], variant=('gable', 'bay', 'greenhouse', 'turrets')[(idx * 7 + 3) % 4])
        floors = 3 if kind.startswith('shop:') else 3 + (idx % 3 == 1)
        L, marks = city5.building(kind, st, floors, hw, DEPTH)
        f = lambda v: (v[0] * e[0] + v[1] * s[0], v[0] * e[1] + v[1] * s[1])
        for (u, y, w), state in L.items():
            x, z = fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]
            V[(x, pad + y, z)] = orient(state, f, False)
        for name, (u, y, w) in marks:
            x, z = fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]
            MARKERS.append(('sidequest:%d_inn' % idx if name == 'inn' else name, (x, pad + y, z)))


def lanes():
    """Whatever is left on a terrace is a lane: paving with planters and lamps."""
    for (x, z), k in LEVEL.items():
        if (x, z) in USED or (x, z) in RESERVED or k == 0:
            continue
        top = TOP[(x, z)]
        if V.get((x, top + 1, z)) not in (None, AIR):
            continue
        V[(x, top, z)] = B('mossy_stone_bricks' if _h(x, z, 3) < 0.25 else 'stone_bricks')
        r = _h(x, z, 5)
        if r < 0.04:
            V[(x, top + 1, z)] = B('potted_flowering_azalea_bush')
        elif r < 0.06:
            V[(x, top + 1, z)] = city5.LAMP


# ---------------- the palace ----------------
PAL_C = (0, -16)
PAL_HX, PAL_HZ = 22, 18
PLINTH = 3
BODY = 18
DOME_R = 15
TOWER_H = 22


def palace():
    cx, cz = PAL_C
    y0 = LEVELS[0]
    fy = y0 + PLINTH                                  # hall floor
    # plinth with a grand stair across the whole south front
    for x in range(cx - PAL_HX - 3, cx + PAL_HX + 4):
        for z in range(cz - PAL_HZ - 3, cz + PAL_HZ + 4):
            for y in range(y0 + 1, fy + 1):
                V[(x, y, z)] = B('smooth_quartz' if y == fy else 'tuff_bricks')
    for i in range(PLINTH):
        z = cz + PAL_HZ + 4 + i
        for x in range(cx - 14, cx + 15):
            y = fy - i
            for yy in range(y0 + 1, y):
                V[(x, yy, z)] = B('tuff_bricks')
            V[(x, y, z)] = B('quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
            RESERVED.add((x, z))
    # body: calcite walls, tall windows of stained glass, copper cornice
    for x in range(cx - PAL_HX, cx + PAL_HX + 1):
        for z in range(cz - PAL_HZ, cz + PAL_HZ + 1):
            edge = x in (cx - PAL_HX, cx + PAL_HX) or z in (cz - PAL_HZ, cz + PAL_HZ)
            for y in range(fy + 1, fy + BODY + 1):
                if not edge:
                    if y == fy + BODY:
                        V[(x, y, z)] = B('smooth_quartz')
                    continue
                along = (z - cz) if x in (cx - PAL_HX, cx + PAL_HX) else (x - cx)
                corner = x in (cx - PAL_HX, cx + PAL_HX) and z in (cz - PAL_HZ, cz + PAL_HZ)
                pier = along % 4 == 0 or corner
                if y >= fy + BODY - 1:
                    V[(x, y, z)] = B('waxed_cut_copper')
                elif pier:
                    V[(x, y, z)] = B('quartz_pillar[axis=y]')
                elif fy + 3 <= y <= fy + BODY - 4:
                    V[(x, y, z)] = B(('yellow', 'orange', 'yellow', 'white')[(y - fy) % 4] + '_stained_glass') \
                        if (y - fy) % 7 else B('waxed_cut_copper')
                else:
                    V[(x, y, z)] = B('calcite')
    # the portico: a row of double columns before the south front, pediment with the sun
    pz = cz + PAL_HZ + 2
    for x in range(cx - 14, cx + 15, 4):
        for dx in (0, 1):
            for y in range(fy + 1, fy + 15):
                V[(x + dx, y, pz)] = B('quartz_pillar[axis=y]')
                V[(x + dx, y, pz + 1)] = B('quartz_pillar[axis=y]')
    for x in range(cx - 15, cx + 17):
        for z in range(cz + PAL_HZ + 1, pz + 3):
            V[(x, fy + 15, z)] = B('smooth_quartz')
            V[(x, fy + 16, z)] = B('waxed_cut_copper')
    for r in range(0, 9):
        for x in range(cx - 15 + r * 2, cx + 17 - r * 2):
            V[(x, fy + 17 + r, pz + 2)] = B('calcite')
            V[(x, fy + 17 + r, cz + PAL_HZ + 1)] = B('calcite')
    for dx in range(-3, 5):
        for dy in range(-3, 4):
            rr = math.hypot(dx - 0.5, dy)
            if rr <= 3.2:
                V[(cx + dx, fy + 21 + dy, pz + 3)] = B('ochre_froglight' if rr < 1.6 else 'yellow_stained_glass')
    # the dome: a copper-gold shell with ribs, a ring of glass and the sun oculus on top
    dy0 = fy + BODY
    for x in range(-DOME_R - 1, DOME_R + 2):
        for z in range(-DOME_R - 1, DOME_R + 2):
            for y in range(0, DOME_R + 2):
                rho = math.sqrt(x * x + y * y + z * z)
                if not (DOME_R - 1.1 <= rho <= DOME_R + 0.3):
                    continue
                if math.hypot(x, z) <= 3.2:
                    continue
                rib = abs(x) <= 0 or abs(z) <= 0 or abs(abs(x) - abs(z)) <= 0
                if y <= 2:
                    blk = 'waxed_cut_copper'
                elif rib:
                    blk = 'gold_block'
                elif 4 <= y <= 6:
                    blk = 'yellow_stained_glass'
                else:
                    blk = 'waxed_copper_block' if (x + z) % 2 else 'waxed_cut_copper'
                V[(cx + x, dy0 + y, cz + z)] = B(blk)
    top = dy0 + DOME_R
    for x in range(-3, 4):
        for z in range(-3, 4):
            if math.hypot(x, z) <= 3.2:
                V[(cx + x, top, cz + z)] = B('yellow_stained_glass' if math.hypot(x, z) > 1 else 'ochre_froglight')
    # the sun tower on the dome: a lantern shaft, a gallery and the frozen sun in its armillary
    for y in range(top + 1, top + TOWER_H):
        for x in range(-2, 3):
            for z in range(-2, 3):
                ring = max(abs(x), abs(z)) == 2
                if not ring:
                    continue
                corner = abs(x) == 2 and abs(z) == 2
                V[(cx + x, y, cz + z)] = B('quartz_pillar[axis=y]' if corner else
                                           ('gold_block' if (y - top) % 6 == 0 else 'yellow_stained_glass'))
    gy = top + TOWER_H
    for x in range(-4, 5):
        for z in range(-4, 5):
            if math.hypot(x, z) <= 4.4:
                V[(cx + x, gy, cz + z)] = B('waxed_cut_copper')
    sun_y = gy + 9
    R = 7
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            for y in range(-R - 1, R + 2):
                rho = math.sqrt(x * x + y * y + z * z)
                if R - 0.7 <= rho <= R + 0.3 and (abs(y) <= 0 or abs(x) <= 0 or abs(z) <= 0):
                    V[(cx + x, sun_y + y, cz + z)] = B('gold_block' if abs(y) == 0 else 'waxed_cut_copper')
                elif rho <= 3.6:
                    V[(cx + x, sun_y + y, cz + z)] = B(('ochre_froglight', 'shroomlight', 'glowstone')[(abs(x) + abs(y) + abs(z)) % 3])
    for k in range(4, 8):
        for (dx, dz) in N4:
            V[(cx + dx * k, sun_y, cz + dz * k)] = B('end_rod[facing=%s]' % ('east' if dx else 'south'))
    for y in range(gy + 1, sun_y - 3):
        V[(cx, y, cz)] = B('gold_block')
    # four corner pavilions: the halls of the mayor, the inventor, the gardener and the priest
    for (sx, sz), name in (((-1, -1), 'mayor'), ((1, -1), 'inventor'), ((-1, 1), 'gardener'), ((1, 1), 'priest')):
        px, pz2 = cx + sx * PAL_HX, cz + sz * PAL_HZ
        for x in range(px - 5, px + 6):
            for z in range(pz2 - 5, pz2 + 6):
                edge = abs(x - px) == 5 or abs(z - pz2) == 5
                for y in range(fy + 1, fy + BODY + 8):
                    if edge:
                        corner = abs(x - px) == 5 and abs(z - pz2) == 5
                        V[(x, y, z)] = B('quartz_pillar[axis=y]' if corner else
                                         ('yellow_stained_glass' if (y - fy) % 6 in (2, 3, 4) and (x + z) % 2 == 0 else 'calcite'))
                    else:
                        V[(x, y, z)] = AIR if y < fy + BODY + 7 else B('smooth_quartz')
            for r in range(6):
                for x in range(px - 5 + r, px + 6 - r):
                    for z in range(pz2 - 5 + r, pz2 + 6 - r):
                        if max(abs(x - px), abs(z - pz2)) == 5 - r:
                            V[(x, fy + BODY + 8 + r, z)] = B('waxed_cut_copper')
        V[(px, fy + BODY + 14, pz2)] = B('lightning_rod[facing=up,powered=false,waterlogged=false]')
        MARKERS.append((name, (px, fy + 1, pz2)))
    # the hall under the dome: the portal, the waystone, the arrival on the stair
    for x in range(cx - PAL_HX + 1, cx + PAL_HX):
        for z in range(cz - PAL_HZ + 1, cz + PAL_HZ):
            V[(x, fy, z)] = B('chiseled_quartz_block' if (x - cx) % 4 == 0 and (z - cz) % 4 == 0 else
                              'smooth_quartz' if (x + z) % 2 else 'polished_diorite')
    for x in range(-4, 5):
        for z in range(-4, 5):
            if math.hypot(x, z) <= 4.4:
                V[(cx + x, fy, cz + z)] = B('gold_block' if math.hypot(x, z) > 3.4 else 'yellow_stained_glass')
    MARKERS.append(('town_hall_portal', (cx, fy + 1, cz)))
    MARKERS.append(('town_hall_waystone', (cx + 6, fy + 1, cz + 8)))
    MARKERS.append(('arrival', (cx, fy + 1, cz + PAL_HZ + 8)))
    for x in range(cx - PAL_HX - 3, cx + PAL_HX + 4):
        for z in range(cz - PAL_HZ - 3, cz + PAL_HZ + 4 + PLINTH):
            USED.add((x, z))
            RESERVED.add((x, z))


def plots():
    """Four player plots on the summit's free ground around the palace, nearest first."""
    cx, cz = PAL_C
    done = 0
    cands = sorted(((x, z) for (x, z), k in LEVEL.items() if k == 0), key=lambda c: math.hypot(c[0] - cx, c[1] - cz))
    for (x, z) in cands:
        if done == 4:
            break
        cells = [(x + i, z + j) for i in range(-1, 17) for j in range(-1, 17)]
        if all(LEVEL.get(c) == 0 and c not in USED for c in cells):
            for c in cells:
                USED.add(c)
                RESERVED.add(c)
            MARKERS.append(('player_plot', (x, TOP[(x, z)] + 1, z)))
            done += 1
    return done


def summit():
    for (x, z), k in LEVEL.items():
        if k != 0 or (x, z) in USED:
            continue
        top = TOP[(x, z)]
        r = math.hypot(x - PAL_C[0], z - PAL_C[1])
        V[(x, top, z)] = B('calcite' if int(r) % 6 else 'waxed_cut_copper')


def build():
    for d in (V, EXTRA, LEVEL, TOP):
        d.clear()
    MARKERS.clear()
    LOTCELLS.clear()
    USED.clear()
    RESERVED.clear()
    LOTS.clear()
    plan()
    palace()
    n = plots()
    stairs()
    rows()
    promenades()
    place_lots()
    summit()
    lanes()
    fill()
    city5.edge_radius = island_edge            # the barrier follows this island's shore
    city5.EXT = EXT
    city5.top_at = lambda c: TOP.get(c)
    city5.depth_at = depth_at
    city5.inside = inside
    city5.V = V
    city5.rim_barrier.__globals__['V'] = V
    city5.rim_barrier(headroom=60)
    hall = min((l for l in LOTS if l[5] == 2), key=lambda l: abs(math.atan2(l[1], l[0]) - math.pi / 2))
    MARKERS.append(('trading_hall', (hall[0] - hall[2][0] * 0, LEVELS[2] + 1, hall[1] + hall[2][1] * 3)))
    print('plots', n)
    return V


if __name__ == '__main__':
    t0 = time.time()
    build()
    print(len(V), 'blocks,', len(LOTS), 'buildings,', len(MARKERS), 'markers', round(time.time() - t0, 1), 's')
    from voxrender import render
    out = os.path.join(HERE, 'preview')
    os.makedirs(out, exist_ok=True)
    t0 = time.time()
    no_barrier = lambda x, y, z: V.get((x, y, z), '') != 'minecraft:barrier'
    render(V, os.path.join(out, 'solsticio7.png'), scale=2, keep=no_barrier)
    render(V, os.path.join(out, 'solsticio7-south.png'), scale=5,
           keep=lambda x, y, z: -45 <= x <= 45 and z >= 10 and y > -20 and no_barrier(x, y, z))
    print('render', round(time.time() - t0, 1), 's')
    if '--export' in sys.argv:
        print('export', city3.export(sys.argv[sys.argv.index('--export') + 1]))
