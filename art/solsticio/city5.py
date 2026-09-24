"""Solsticio, draft 5: an organic city with relief, inside a barrier of light.

The civic core on the summit stays symmetric (Ayuntamiento rotunda under the armillary sun, the four
NPC halls, the four player plots). Beyond it the island is amorphous: hills and valleys, winding
streets that follow the ground, buildings on levelled pads with plinths and retaining walls, canals,
fountains, lighthouses at the viewpoints. Every building is itself symmetric and detailed: stained
glass everywhere, shop windows, balconies, sun rosettes in the gables, solar ridges.

Player scale: doors 2 high, storeys 4 high, streets 4-6 wide, one-block steps on slopes.
Markers: CityLayout's plus shop:<type>, sidequest:<n>_inn, resident, easter:<name>.

    python art/solsticio/city5.py [--export path/to/city.nbt]
"""
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import city3  # noqa: E402
from voxkit import Voxels, ab, tf, T8, orient  # noqa: E402

R0 = 116                    # mean radius of the island
T1, T2 = 20, 50             # civic core: acropolis and civic ring (flat, symmetric)
Y1, Y2 = 0, -5
B = lambda n: 'minecraft:' + n
AIR = B('air')
LAMP = B('waxed_copper_bulb[lit=true,powered=false]')
POST = B('tuff_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
PANE_U = B('glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]')
PANE_W = B('glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]')
LEAVES = B('flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]')
HANG = B('lantern[hanging=true,waterlogged=false]')

V = Voxels()
EXTRA = {}
MARKERS = []
city3.V, city3.EXTRA, city3.MARKERS = V, EXTRA, MARKERS
city3.SUN_Y, city3.SUN_R = 80, 30


# ---------------- noise ----------------
def _h(i, j, seed):
    n = (i * 374761393 + j * 668265263 + seed * 1442695041) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def vnoise(x, z, scale, seed):
    fx, fz = x / scale, z / scale
    i, j = math.floor(fx), math.floor(fz)
    tx, tz = fx - i, fz - j
    sx, sz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a = _h(i, j, seed) + (_h(i + 1, j, seed) - _h(i, j, seed)) * sx
    b = _h(i, j + 1, seed) + (_h(i + 1, j + 1, seed) - _h(i, j + 1, seed)) * sx
    return a + (b - a) * sz


def fbm(x, z, seed=7):
    return 0.55 * vnoise(x, z, 52, seed) + 0.3 * vnoise(x, z, 23, seed + 1) + 0.15 * vnoise(x, z, 9, seed + 2)


def edge_radius(x, z):
    th = math.atan2(z, x)
    r = R0 * (1 + 0.11 * math.sin(3 * th + 1.3) + 0.07 * math.sin(5 * th + 0.4) + 0.04 * math.sin(8 * th + 2.1))
    return r + 16 * (vnoise(math.cos(th) * 40, math.sin(th) * 40, 17, 3) - 0.5)


HILLS = [(78, -30, 17, 17), (-62, 58, 15, 15), (20, 88, 12, 13), (-84, -44, 16, 16), (40, -86, 11, 12), (-30, -90, 9, 11)]


def terrain(x, z):
    """Ground height outside the civic core (float)."""
    d = math.hypot(x, z)
    er = edge_radius(x, z)
    t = (d - T2) / max(1.0, er - T2)                # 0 at the civic ring, 1 at the shore
    h = -9 - 20 * t ** 1.3 + 16 * (fbm(x, z) - 0.5) * min(1.0, (d - T2) / 18)
    for (hx, hz, amp, rad) in HILLS:
        h += amp * math.exp(-((x - hx) ** 2 + (z - hz) ** 2) / (2 * rad * rad))
    if t > 0.92:
        h -= (t - 0.92) * 40                         # the shore falls away into cliffs
    return h


def everywhere(r):
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            yield x, z


EXT = int(R0 * 1.35)
HEIGHT = {}       # (x, z) -> ground top y outside the core
ROAD = {}         # (x, z) -> (y, priority, kind)
USED = set()      # cells taken by lots, plazas and features


def inside(x, z, margin=0.0):
    return math.hypot(x, z) < edge_radius(x, z) - margin


# ---------------- ground ----------------
def ground():
    for x, z in everywhere(EXT):
        if not inside(x, z):
            continue
        d = math.hypot(x, z)
        if d <= T2:
            continue
        HEIGHT[(x, z)] = round(terrain(x, z))


def fill_ground():
    for x, z in everywhere(EXT):
        if not inside(x, z):
            continue
        d = math.hypot(x, z)
        top = Y1 if d <= T1 else Y2 if d <= T2 else HEIGHT[(x, z)]
        er = edge_radius(x, z)
        e = d / er
        depth = int(8 + (1 - e) ** 0.6 * 60)
        bottom = top - depth
        for y in range(bottom, top + 1):
            k = top - y
            if k > 5 and y - bottom > 3 and e < 0.97:
                continue
            if (x, y, z) in V:
                continue
            blk = 'grass_block' if k == 0 else 'dirt' if k <= 3 else \
                ('calcite' if (y % 9 == 0) else 'tuff' if (x + z + y) % 5 == 0 else 'stone')
            V[(x, y, z)] = B(blk)
        if e < 0.9 and (x * 5 + z * 3) % 13 == 0:
            for dy in range(1, 2 + abs(x + z) % 4):
                V[(x, bottom - dy, z)] = B('hanging_roots')


# ---------------- civic core ----------------
def core():
    tan = math.tan(math.radians(22.5))
    for x, z in everywhere(T2 + 1):
        d = math.hypot(x, z)
        a, b = ab(x, z)
        for ok, hi, lo in ((T1 - 1 < d <= T1, Y1, Y2), (T2 - 1 < d <= T2, Y2, None)):
            if not ok:
                continue
            if lo is None:
                lo = min(HEIGHT.get((x + dx, z + dz), Y2 - 4) for dx in (-1, 0, 1) for dz in (-1, 0, 1)) - 1
            for y in range(lo, hi + 1):
                V[(x, y, z)] = B('calcite')
            V[(x, hi, z)] = B('waxed_cut_copper')
            V[(x, hi - 2, z)] = B('waxed_oxidized_cut_copper')
            gap = b <= 4 or abs(a - b) <= 2 or (hi == Y1 and abs(b - a * tan) <= 1.5)
            if not gap:
                V[(x, hi + 1, z)] = POST if (a + b) % 4 == 0 else B('waxed_copper_grate')
        if T1 < d < T2 - 1 and V.get((x, Y2, z)) is None:
            pass
    # stairs down from the acropolis on the axes, flights to the plots
    for w in range(0, 5):
        for k in range(0, 6):
            s = T1 - 1 + k
            V.sym(s, Y1 - k, w, B('polished_tuff_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                  if k < 5 else B('polished_tuff'))
            for y in range(Y1 - k + 1, Y1 + 4):
                V.sym(s, y, w, AIR)
    for k in range(0, 6):
        s = 14 + k
        for w in (-1, 0, 1):
            V.sym(s + w, Y1 - k, s, B('polished_tuff'))
            for y in range(Y1 - k + 1, Y1 + 3):
                V.sym(s + w, y, s, AIR)
    # gardens, channels and basins of the civic ring
    for x, z in everywhere(T2):
        d = math.hypot(x, z)
        a, b = ab(x, z)
        if 18.4 < d <= T1 and abs(b - a * tan) <= 1.3:
            V[(x, Y1, z)] = B('water')
            V[(x, Y1 - 1, z)] = B('calcite')
        if not (T1 < d < T2 - 1):
            continue
        if V.get((x, Y2, z)) is None:
            V[(x, Y2, z)] = B('grass_block')
            for y in range(Y2 - 3, Y2):
                V[(x, y, z)] = B('dirt')
        if abs(b - a * tan) <= 1.3 and d < 44 or abs(b - a * tan) <= 3.3 and 43 <= d <= 47:
            V[(x, Y2, z)] = B('water')
            V[(x, Y2 - 1, z)] = B('calcite')
        elif abs(b - a * tan) <= 4.3 and 42 <= d <= 48:
            V[(x, Y2 + 1, z)] = B('calcite')
        elif (a * 7 + b * 13) % 23 in (1, 12):
            V[(x, Y2 + 1, z)] = B(('allium', 'cornflower', 'azure_bluet', 'lily_of_the_valley')[(a + b) % 4])
    for (x, z) in ((26, 11), (11, 26), (40, 8)):
        tree(x, z, Y2, 8, 3, sym=True)
    city3.acropolis()
    city3.rotunda()
    city3.halls()
    city3.armillary()
    city3.plots()
    for x, z in everywhere(T2):
        USED.add((x, z))


def tree(x, z, y0, h=8, r=3, sym=False):
    put = V.sym if sym else (lambda a, y, b, s: V.__setitem__((a, y, b), s))
    for y in range(y0 + 1, y0 + h + 1):
        put(x, y, z, B('stripped_birch_log[axis=y]'))
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(-2, r):
                if math.sqrt(dx * dx + dz * dz + (dy * 1.4) ** 2) <= r + 0.3 and (dx or dz or dy > 0):
                    if V.get((x + dx, y0 + h + dy, z + dz), AIR) == AIR:
                        put(x + dx, y0 + h + dy, z + dz, LEAVES)


# ---------------- streets ----------------
PATHS = []


def radial(k):
    th0 = math.radians(45 * k)
    pts, d = [], T2 + 0.5
    while True:
        wob = 0.32 * math.sin(d / 19 + k * 1.7) * min(1.0, (d - T2) / 25)
        th = th0 + wob
        x, z = d * math.cos(th), d * math.sin(th)
        if not inside(round(x), round(z), 7):
            break
        pts.append((x, z))
        d += 0.5
    return pts


def ring(frac, seed):
    pts = []
    for i in range(0, 1440):
        th = math.radians(i * 0.25)
        er = edge_radius(math.cos(th) * 60, math.sin(th) * 60)
        r = T2 + (er - T2) * frac + 5 * math.sin(3 * th + seed)
        pts.append((r * math.cos(th), r * math.sin(th)))
    return pts


def stamp_road(pts, width, prio, kind):
    """Rasterize a path; heights follow the ground, smoothed and limited to one block per step."""
    hs = []
    for (x, z) in pts:
        key = (round(x), round(z))
        hs.append(HEIGHT.get(key, Y2 - 4))
    sm = []
    for i in range(len(hs)):
        lo, hi = max(0, i - 8), min(len(hs), i + 9)
        sm.append(sum(hs[lo:hi]) / (hi - lo))
    PATHS.append((pts, sm, width, kind))
    r = width / 2
    for (x, z), h in zip(pts, sm):
        for dx in range(-int(r) - 1, int(r) + 2):
            for dz in range(-int(r) - 1, int(r) + 2):
                if math.hypot(dx + (round(x) - x), dz + (round(z) - z)) <= r:
                    c = (round(x) + dx, round(z) + dz)
                    if c not in HEIGHT:
                        continue
                    old = ROAD.get(c)
                    if old is None or old[1] < prio:
                        ROAD[c] = (round(h), prio, kind)


def streets():
    for k in range(8):
        stamp_road(radial(k), 5 if k % 2 == 0 else 4, 3, 'main' if k % 2 == 0 else 'side')
    stamp_road(ring(0.38, 0.7), 4, 2, 'ring')
    stamp_road(ring(0.7, 2.1), 4, 1, 'ring')
    # lay the roads: level the ground to the road, surface blocks, half steps
    for c, (h, prio, kind) in ROAD.items():
        HEIGHT[c] = h
    for (x, z), (h, prio, kind) in ROAD.items():
        edge = any((x + dx, z + dz) not in ROAD for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        V[(x, h, z)] = B('waxed_cut_copper' if edge and kind == 'main' else 'polished_tuff' if edge else
                         ('calcite' if (x + z) % 3 else 'polished_diorite'))
        for y in range(h + 1, h + 5):
            V.pop((x, y, z), None)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = ROAD.get((x + dx, z + dz))
            if n and n[0] == h + 1 and (x, h + 1, z) not in V:
                V[(x, h + 1, z)] = B('polished_tuff_slab[type=bottom,waterlogged=false]')
                break
        USED.add((x, z))
    # canals down the middle of the two long main streets (east and west)
    for (pts, sm, width, kind) in PATHS[:1] + PATHS[4:5]:
        for (x, z), h in zip(pts[20:], sm[20:]):
            c = (round(x), round(z))
            if c in ROAD:
                hh = ROAD[c][0]
                V[(c[0], hh, c[1])] = B('water')
                V[(c[0], hh - 1, c[1])] = B('sea_lantern') if (c[0] + c[1]) % 7 == 0 else B('calcite')


# ---------------- plazas, fountains, lamps ----------------
def plazas():
    spots = []
    for (pts, sm, width, kind) in PATHS:
        if kind != 'ring':
            continue
        for i in range(0, len(pts), 90):
            spots.append((pts[i], sm[i]))
    for (x, z), h in spots:
        cx, cz, hh = round(x), round(z), round(h)
        if not all((cx + dx, cz + dz) in HEIGHT for dx in (-6, 6) for dz in (-6, 6)):
            continue
        for dx in range(-6, 7):
            for dz in range(-6, 7):
                if math.hypot(dx, dz) <= 6.3:
                    c = (cx + dx, cz + dz)
                    HEIGHT[c] = hh
                    ROAD[c] = (hh, 5, 'plaza')
                    USED.add(c)
                    V[(c[0], hh, c[1])] = B('calcite' if (dx + dz) % 2 else 'polished_tuff')
                    for y in range(hh + 1, hh + 6):
                        V.pop((c[0], y, c[1]), None)
        fountain(cx, hh, cz)


def fountain(cx, h, cz):
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            ring = max(abs(dx), abs(dz)) == 2
            V[(cx + dx, h + 1, cz + dz)] = B('calcite') if ring else B('water')
            V[(cx + dx, h, cz + dz)] = B('sea_lantern') if (dx, dz) == (0, 0) else B('prismarine_bricks')
    for y in range(h + 1, h + 4):
        V[(cx, y, cz)] = B('waxed_chiseled_copper')
    V[(cx, h + 4, cz)] = B('yellow_stained_glass')
    V[(cx, h + 5, cz)] = B('ochre_froglight')
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        V[(cx + dx, h + 4, cz + dz)] = B('waxed_cut_copper_slab[type=top,waterlogged=false]')


def lamps():
    n = 0
    for (pts, sm, width, kind) in PATHS:
        for i in range(0, len(pts), 22):
            x, z = pts[i]
            if i + 1 >= len(pts):
                break
            tx, tz = pts[i + 1][0] - x, pts[i + 1][1] - z
            ln = math.hypot(tx, tz) or 1
            nx, nz = -tz / ln, tx / ln
            side = 1 if (n % 2) else -1
            n += 1
            c = (round(x + nx * side * (width / 2 + 0.6)), round(z + nz * side * (width / 2 + 0.6)))
            if c in USED and c not in ROAD or c not in HEIGHT:
                continue
            h = ROAD[c][0] if c in ROAD else HEIGHT[c]
            V[(c[0], h + 1, c[1])] = POST
            V[(c[0], h + 2, c[1])] = POST
            V[(c[0], h + 3, c[1])] = POST
            V[(c[0], h + 4, c[1])] = B('waxed_copper_grate')
            V[(c[0], h + 3, c[1])] = POST
            V[(c[0], h + 5, c[1])] = B('ochre_froglight' if n % 3 else 'pearlescent_froglight')
            V[(c[0], h + 6, c[1])] = B('waxed_cut_copper_slab[type=bottom,waterlogged=false]')
            USED.add(c)


# ---------------- buildings ----------------
SHOP_TYPES = ['bookstore', 'rarities', 'parts', 'seeds', 'smithy', 'apothecary', 'maps', 'minerals',
              'records', 'textiles', 'nursery', 'creatures', 'museum', 'bakery', 'apiary', 'curiosities']
SHOP_STYLE = {
    'rarities': ('purple', 'amethyst_block', 'barrel[facing=up,open=false]'),
    'minerals': ('black', 'raw_gold_block', 'blast_furnace[facing=north,lit=false]'),
    'creatures': ('green', 'moss_block', 'barrel[facing=up,open=false]'),
    'bakery': ('yellow', 'hay_block[axis=y]', 'smoker[facing=north,lit=false]'),
    'parts': ('orange', 'waxed_copper_grate', 'crafter'),
    'smithy': ('gray', 'smithing_table', 'anvil[facing=north]'),
    'textiles': ('pink', 'pink_wool', 'loom[facing=north]'),
    'maps': ('cyan', 'cartography_table', 'cartography_table'),
    'seeds': ('lime', 'moss_block', 'composter[level=0]'),
    'nursery': ('green', 'flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]', 'flowering_azalea'),
    'apothecary': ('light_blue', 'prismarine_bricks', 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]'),
    'apiary': ('yellow', 'honeycomb_block', 'beehive[facing=north,honey_level=0]'),
    'bookstore': ('red', 'bookshelf', 'bookshelf'),
    'records': ('magenta', 'jukebox[has_record=false]', 'jukebox[has_record=false]'),
    'museum': ('white', 'chiseled_quartz_block', 'glass'),
    'curiosities': ('brown', 'chiseled_tuff_bricks', 'barrel[facing=up,open=false]'),
}
STYLES = [
    dict(wall='calcite', trim='quartz_pillar', roof='waxed_cut_copper', glass=('yellow', 'orange'), bed='yellow'),
    dict(wall='smooth_quartz', trim='stripped_birch_log', roof='waxed_oxidized_cut_copper', glass=('light_blue', 'cyan'), bed='light_blue'),
    dict(wall='calcite', trim='stripped_cherry_log', roof='waxed_weathered_cut_copper', glass=('pink', 'magenta'), bed='pink'),
    dict(wall='polished_tuff', trim='quartz_pillar', roof='waxed_exposed_cut_copper', glass=('yellow', 'white'), bed='white'),
    dict(wall='calcite', trim='stripped_birch_log', roof='waxed_oxidized_cut_copper', glass=('orange', 'yellow'), bed='orange'),
    dict(wall='smooth_quartz', trim='stripped_cherry_log', roof='waxed_cut_copper', glass=('cyan', 'light_blue'), bed='cyan'),
]


def building(kind, st, floors):
    """Local frame: u across the street (-4..4), w depth (0 = front; 'north' faces the street),
    y from the foundation (0). Symmetric across u = 0 except the ladder and the bed."""
    L, marks = {}, []
    top = 5 * floors
    wall, roof = B(st['wall']), st['roof']
    trim = B(st['trim'] + '[axis=y]')
    g1, g2 = B(st['glass'][0] + '_stained_glass'), B(st['glass'][1] + '_stained_glass')
    shop = kind.startswith('shop:')
    for u in range(-4, 5):
        for w in range(0, 9):
            L[(u, 0, w)] = B('polished_tuff')
            edge = abs(u) == 4 or w in (0, 8)
            corner = abs(u) == 4 and w in (0, 8)
            for y in range(1, top + 1):
                band = y % 5 == 0
                if edge:
                    L[(u, y, w)] = trim if corner else B('waxed_cut_copper') if band else \
                        B('polished_tuff') if y == 1 else wall
                else:
                    L[(u, y, w)] = B('birch_planks') if band else AIR
    for s in range(floors):
        y0 = 5 * s
        if s == 0:
            if shop:
                for u in (-3, -2, -1, 1, 2, 3):
                    for y in (1, 2, 3):
                        L[(u, y, 0)] = B('glass')
            else:
                for u in (-3, -2, 2, 3):
                    for y in (2, 3):
                        L[(u, y, 0)] = PANE_U
                    L[(u, 4, 0)] = g1 if abs(u) == 2 else g2
                    L[(u, 1, -1)] = B('moss_block')
                    L[(u, 2, -1)] = B(('azure_bluet', 'allium', 'cornflower', 'oxeye_daisy')[(abs(u) + len(st['glass'][0])) % 4])
        else:
            for u in range(-3, 4):
                for y in (y0 + 1, y0 + 2, y0 + 3):
                    L[(u, y, 0)] = B('glass') if u == 0 else PANE_U
                L[(u, y0 + 4, 0)] = g1 if u % 2 == 0 else g2
                L[(u, y0, -1)] = B(roof + '_slab[type=top,waterlogged=false]')
                if abs(u) in (1, 3):
                    L[(u, y0 + 1, -1)] = B('waxed_copper_grate')
                elif abs(u) == 2:
                    L[(u, y0 + 1, -1)] = B('potted_azure_bluet' if st['glass'][0] in ('yellow', 'orange') else 'potted_allium')
            for u in (-2, 2):
                L[(u, y0 - 1, -1)] = HANG
        for w in (2, 6):
            if shop and s == 0 and w == 6:
                continue
            for y in (y0 + 2, y0 + 3):
                L[(-4, y, w)] = PANE_W
                L[(4, y, w)] = PANE_W
            L[(-4, y0 + 4, w)] = g1
            L[(4, y0 + 4, w)] = g1
        for u in (-2, -1, 1, 2):
            for y in (y0 + 2, y0 + 3):
                L[(u, y, 8)] = PANE_U
            L[(u, y0 + 4, 8)] = g2
    # the door with a stained-glass transom and hanging lanterns
    L[(0, 1, 0)] = B('waxed_copper_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
    L[(0, 2, 0)] = B('waxed_copper_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    L[(0, 3, 0)] = g1
    L[(0, 4, 0)] = B('ochre_froglight')
    if shop:
        carpet, band, shelf = SHOP_STYLE[kind.split(':')[1]]
        for u in range(-3, 4):
            if u:
                L[(u, 4, 0)] = B(band)
        for u in range(-4, 5):
            L[(u, 4, -1)] = B(roof + '_slab[type=top,waterlogged=false]')
            L[(u, 5, -1)] = B(carpet + '_carpet')
        for u in (-2, 2):
            L[(u, 1, 1)] = B(shelf)
            L[(u, 2, 1)] = B('lantern[hanging=false,waterlogged=false]')
        for u in range(-2, 3):
            L[(u, 1, 4)] = B('stripped_birch_wood[axis=x]')
        for u in range(-3, 3):
            for y in (1, 2, 3):
                L[(u, y, 7)] = B(shelf)
        marks.append((kind, (0, 1, 6)))
    else:
        for u in (-1, 0, 1):
            L[(u, 4, -1)] = B(roof + '_slab[type=top,waterlogged=false]')
        for u in (-1, 1):
            L[(u, 3, -1)] = HANG
            L[(u, 1, -1)] = B('potted_flowering_azalea_bush')
        L[(-3, 1, 7)] = B('crafting_table')
        L[(-3, 1, 6)] = B('barrel[facing=up,open=false]')
        if kind == 'inn':
            for u in (-2, 2):
                L[(u, 1, 3)] = B('barrel[facing=up,open=false]')
                L[(u, 1, 2)] = B('birch_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
            marks.append(('inn', (0, 1, 5)))
    L[(0, 4, 4)] = B('pearlescent_froglight')
    for y in range(1, top):
        L[(3, y, 7)] = B('ladder[facing=west,waterlogged=false]')
    L[(-2, 6, 5)] = B(st['bed'] + '_bed[facing=south,occupied=false,part=foot]')
    L[(-2, 6, 6)] = B(st['bed'] + '_bed[facing=south,occupied=false,part=head]')
    if kind == 'resident':
        marks.append(('resident', (0, 6, 3)))
    # roof with a solar ridge; sun rosettes in both gables
    for r in range(0, 5):
        y = top + 1 + r
        for w in range(-1, 10):
            if r < 4:
                L[(4 - r, y, w)] = B(roof + '_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                L[(-(4 - r), y, w)] = B(roof + '_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
            else:
                L[(0, y, w)] = B(roof)
                if 0 <= w <= 8:
                    L[(0, y + 1, w)] = B('daylight_detector[inverted=false,power=0]')
        if r < 4:
            for u in range(-(3 - r), 4 - r):
                for w in (0, 8):
                    L[(u, y, w)] = wall
    for w in (0, 8):
        for u, blk in ((-2, g2), (-1, g1), (0, g1), (1, g1), (2, g2)):
            L[(u, top + 1, w)] = blk
        for u, blk in ((-1, g1), (0, B('ochre_froglight')), (1, g1)):
            L[(u, top + 2, w)] = blk
        L[(0, top + 3, w)] = g2
    L[(0, top + 7, -1)] = B('lightning_rod[facing=up,powered=false,waterlogged=false]')
    return L, marks


LOTS = []


def lots():
    """Walk every street and try a lot on each side; buildings face the street."""
    shop_i = 0
    for pi, (pts, sm, width, kind) in enumerate(PATHS):
        step = 24
        count = 0
        for i in range(12, len(pts) - 2, step):
            x, z = pts[i]
            tx, tz = pts[i + 1][0] - x, pts[i + 1][1] - z
            ln = math.hypot(tx, tz) or 1
            nx, nz = -tz / ln, tx / ln
            for side in (1, -1):
                vx, vz = nx * side, nz * side
                if abs(vx) >= abs(vz):
                    s = (1 if vx > 0 else -1, 0)
                else:
                    s = (0, 1 if vz > 0 else -1)
                e = (s[1], -s[0])
                off = width / 2 + 2
                fx, fz = round(x + vx * off), round(z + vz * off)
                cells = [(fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]) for u in range(-4, 5) for w in range(0, 9)]
                margin = [(fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]) for u in range(-5, 6) for w in range(-1, 10)]
                if any(c not in HEIGHT or c in USED or c in RIVER for c in cells):
                    continue
                if any(c in USED and c not in ROAD for c in margin):
                    continue
                if not all(inside(c[0], c[1], 4) for c in cells[::8]):
                    continue
                hs = [HEIGHT[c] for c in cells]
                front = (fx - s[0], fz - s[1])
                pad = ROAD[front][0] if front in ROAD else round(sum(hs) / len(hs))
                if max(hs) - pad > 7 or pad - min(hs) > 9:
                    continue
                bkind = 'road:' + kind
                count += 1
                LOTS.append((fx, fz, s, e, pad, bkind, len(LOTS)))
                for c in margin:
                    USED.add(c)


def assign_kinds():
    """The 16 shops take the street-front lots nearest the centre; four inns, one per quadrant."""
    front = sorted((i for i, l in enumerate(LOTS) if l[5].startswith('road:')),
                   key=lambda i: math.hypot(LOTS[i][0], LOTS[i][1]))
    kinds = {}
    for n, i in enumerate(front[:len(SHOP_TYPES)]):
        kinds[i] = 'shop:' + SHOP_TYPES[n]
    for q in range(4):
        cand = [i for i in front[len(SHOP_TYPES):]
                if int((math.degrees(math.atan2(LOTS[i][1], LOTS[i][0])) + 360) // 90) % 4 == q]
        if cand:
            kinds[cand[0]] = 'inn'
    for i, l in enumerate(LOTS):
        k = kinds.get(i) or ('resident' if i % 3 == 0 else 'house')
        LOTS[i] = l[:5] + (k,) + l[6:]


def distance_map():
    """Breadth-first distance from the streets over open ground, with parents for footpaths."""
    from collections import deque
    dist, parent = {}, {}
    q = deque()
    for c in ROAD:
        dist[c] = 0
        q.append(c)
    while q:
        c = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + dx, c[1] + dz)
            if n in HEIGHT and n not in dist and n not in RIVER and abs(HEIGHT[n] - HEIGHT[c]) <= 2:
                dist[n] = dist[c] + 1
                parent[n] = c
                q.append(n)
    return dist, parent


PATHCELLS = set()


def infill():
    """Row houses: walk the ground outward from the streets (nearest first) and put a 9x9 lot
    wherever a door cell has the street, or a path to it, right behind. Neighbouring lots may
    touch, like terraced houses; lots further back get a decorated footpath to the street."""
    dist, parent = distance_map()
    dirs = ((1, 0), (-1, 0), (0, 1), (0, -1))
    cands = sorted((c for c, dd in dist.items() if 1 <= dd <= 24), key=lambda c: (dist[c], _h(c[0], c[1], 21)))
    for door in cands:
        dd = dist[door]
        if door in USED and door not in PATHCELLS:
            continue
        for s in sorted(dirs, key=lambda v: _h(door[0] + v[0], door[1] + v[1], 22)):
            back = (door[0] - s[0], door[1] - s[1])
            if back not in dist or dist[back] >= dd:
                continue
            e = (s[1], -s[0])
            fx, fz = door[0] + s[0], door[1] + s[1]
            cells = [(fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]) for u in range(-4, 5) for w in range(0, 9)]
            if any(c not in HEIGHT or c in USED or c in RIVER for c in cells):
                continue
            if not all(inside(c[0], c[1], 4) for c in cells[::8]):
                continue
            hs = [HEIGHT[c] for c in cells]
            pad = ROAD[back][0] if back in ROAD else HEIGHT[door]
            if max(hs) - pad > 7 or pad - min(hs) > 9:
                continue
            LOTS.append((fx, fz, s, e, pad, 'road:front' if dd <= 2 else 'infill', len(LOTS)))
            for c in cells:
                USED.add(c)
            c = door
            steps = 0
            while c not in ROAD and steps < 40:
                PATHCELLS.add(c)
                USED.add(c)
                if c not in parent:
                    break
                c = parent[c]
                steps += 1
            break


def footpaths():
    for (x, z) in PATHCELLS:
        h = HEIGHT[(x, z)]
        V[(x, h, z)] = B('dirt_path') if (x + z) % 4 else B('calcite')
        for y in range(h + 1, h + 4):
            V.pop((x, y, z), None)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n in PATHCELLS or n in ROAD or n not in HEIGHT:
                continue
            hn = HEIGHT[n]
            if V.get((n[0], hn, n[1])) == B('grass_block') and (n[0], hn + 1, n[1]) not in V:
                r = _h(n[0], n[1], 31)
                V[(n[0], hn + 1, n[1])] = B(('pink_petals[facing=north,flower_amount=4]', 'moss_carpet',
                                               'azure_bluet', 'lily_of_the_valley', 'potted_flowering_azalea_bush',
                                               'short_grass')[int(r * 6)])


RIVER = {}      # (x, z) -> water level
RIVER_END = []
RIVER_PTS = [(-44, -78), (-14, -66), (18, -72), (50, -60), (72, -24), (78, 18), (92, 50), (118, 72), (140, 86)]


def river():
    pts = []
    for i in range(len(RIVER_PTS) - 1):
        (x0, z0), (x1, z1) = RIVER_PTS[i], RIVER_PTS[i + 1]
        n = int(math.hypot(x1 - x0, z1 - z0) * 2)
        for k in range(n):
            t = k / n
            x = x0 + (x1 - x0) * t + 4 * math.sin(i * 2.3 + t * 5.1)
            z = z0 + (z1 - z0) * t + 4 * math.cos(i * 1.7 + t * 4.3)
            pts.append((x, z))
    level = None
    for (x, z) in pts:
        c = (round(x), round(z))
        if c not in HEIGHT:
            if level is not None:
                RIVER_END.append(c)
                break
            continue
        g = HEIGHT[c] if c not in ROAD else min(HEIGHT[c], ROAD[c][0] - 3)
        level = g - 1 if level is None else min(level, g - 1)
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                dd = math.hypot(dx + c[0] - x, dz + c[1] - z)
                n = (c[0] + dx, c[1] + dz)
                if n not in HEIGHT:
                    continue
                if dd <= 1.7:
                    RIVER[n] = min(RIVER.get(n, level), level)
                elif dd <= 3.6 and n not in ROAD:
                    HEIGHT[n] = min(HEIGHT[n], level + 1 + int(dd > 2.8))
    for (x, z), lvl in RIVER.items():
        on_road = (x, z) in ROAD
        top = ROAD[(x, z)][0] if on_road else HEIGHT[(x, z)]
        for y in range(lvl + 1, top + (0 if on_road else 1)):
            V.pop((x, y, z), None)
        V[(x, lvl, z)] = B('water')
        r = _h(x, z, 41)
        V[(x, lvl - 1, z)] = B('sea_lantern') if r < 0.05 else B('calcite') if r < 0.35 else B('gravel')
        if on_road:
            V[(x, top, z)] = B('waxed_cut_copper')
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nb = (x + dx, z + dz)
                if nb in RIVER and nb not in ROAD:
                    V[(x, top + 1, z)] = B('waxed_copper_grate')
        else:
            HEIGHT[(x, z)] = lvl
            if _h(x, z, 43) < 0.06:
                V[(x, lvl + 1, z)] = B('lily_pad')
        USED.add((x, z))
    for (x, z), lvl in list(RIVER.items()):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n in RIVER or n in ROAD or n not in HEIGHT:
                continue
            h = HEIGHT[n]
            r = _h(n[0], n[1], 44)
            if r < 0.25 and (n[0], h + 1, n[1]) not in V:
                V[(n[0], h + 1, n[1])] = B('sugar_cane') if r < 0.1 else B('blue_orchid')
            USED.add(n)


def footbridges():
    done = 0
    items = sorted(RIVER.items(), key=lambda kv: _h(kv[0][0], kv[0][1], 51))
    for (x, z), lvl in items:
        if done >= 7:
            break
        for (dx, dz) in ((1, 0), (0, 1)):
            span = [(x + dx * k, z + dz * k) for k in range(-4, 5)]
            if not all(c in HEIGHT and c not in ROAD for c in span):
                continue
            water = [c for c in span if c in RIVER]
            if len(water) < 3 or span[0] in RIVER or span[-1] in RIVER:
                continue
            if abs(HEIGHT[span[0]] - HEIGHT[span[-1]]) > 2:
                continue
            base = max(HEIGHT[span[0]], HEIGHT[span[-1]])
            side = (dz, dx)
            for k, c in enumerate(span):
                y = base + min(k, 8 - k, 2)
                for w in (-1, 0, 1):
                    cc = (c[0] + side[0] * w, c[1] + side[1] * w)
                    V[(cc[0], y, cc[1])] = B('stripped_cherry_wood[axis=y]') if w else B('calcite')
                    for yy in range(y + 1, y + 3):
                        V.pop((cc[0], yy, cc[1]), None)
                    if w:
                        V[(cc[0], y + 1, cc[1])] = B('waxed_copper_grate') if k % 2 else B('lantern[hanging=false,waterlogged=false]')
            done += 1
            break


def place_lots():
    for (fx, fz, s, e, pad, bkind, idx) in LOTS:
        st = STYLES[idx % len(STYLES)]
        floors = 2 if bkind.startswith('shop:') else 2 + (idx % 3 == 1)
        L, marks = building(bkind, st, floors)
        f = lambda v: (v[0] * e[0] + v[1] * s[0], v[0] * e[1] + v[1] * s[1])
        # plinth: fill down to the ground under the footprint and cut the ground above the pad
        for u in range(-4, 5):
            for w in range(0, 9):
                x, z = fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]
                g = HEIGHT.get((x, z), pad)
                for y in range(min(g, pad) - 1, pad):
                    V[(x, y, z)] = B('tuff_bricks' if (u in (-4, 4) or w in (0, 8)) else 'stone')
                for y in range(pad + 1, max(g, pad) + 1):
                    V.pop((x, y, z), None)
                HEIGHT[(x, z)] = pad
        for (u, y, w), state in L.items():
            x, z = fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]
            V[(x, pad + y, z)] = orient(state, f, False)
        for name, (u, y, w) in marks:
            x, z = fx + u * e[0] + w * s[0], fz + u * e[1] + w * s[1]
            label = 'sidequest:%d_inn' % idx if name == 'inn' else name
            MARKERS.append((label, (x, pad + y, z)))


# ---------------- nature and landmarks ----------------
def nature():
    for (x, z), h in list(HEIGHT.items()):
        if (x, z) in USED or math.hypot(x, z) <= T2 + 1:
            continue
        if V.get((x, h, z)) != B('grass_block') or (x, h + 1, z) in V:
            continue
        n = fbm(x * 3.1, z * 3.1, 11)
        r = _h(x, z, 5)
        if r < 0.012 and n > 0.45 and inside(x, z, 6):
            tree(x, z, h, 6 + int(r * 400) % 4, 3)
        elif r < 0.09 and n > 0.5:
            V[(x, h + 1, z)] = B(('azure_bluet', 'allium', 'cornflower', 'oxeye_daisy', 'lily_of_the_valley')[int(r * 1000) % 5])
        elif r < 0.2:
            V[(x, h + 1, z)] = B('short_grass')


def lighthouses():
    for (pts, sm, width, kind) in PATHS:
        if kind != 'main':
            continue
        x, z = pts[-1]
        cx, cz = round(x), round(z)
        base = HEIGHT.get((cx, cz))
        if base is None:
            continue
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if math.hypot(dx, dz) <= 4.5:
                    V[(cx + dx, base, cz + dz)] = B('calcite')
                    USED.add((cx + dx, cz + dz))
        for y in range(base + 1, base + 30):
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if max(abs(dx), abs(dz)) != 2:
                        continue
                    corner = abs(dx) == 2 and abs(dz) == 2
                    band = (y - base) % 8 == 0
                    window = not corner and (dx == 0 or dz == 0) and (y - base) % 8 in (4, 5)
                    V[(cx + dx, y, cz + dz)] = B('waxed_cut_copper' if band else 'quartz_pillar[axis=y]' if corner else
                                                 'yellow_stained_glass' if window else 'calcite')
        g = base + 30
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                V[(cx + dx, g, cz + dz)] = B('waxed_cut_copper_slab[type=bottom,waterlogged=false]') \
                    if max(abs(dx), abs(dz)) == 3 else B('gold_block')
        V[(cx, g + 1, cz)] = B('beacon')
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if (dx, dz) != (0, 0):
                    for y in (g + 1, g + 2):
                        V[(cx + dx, y, cz + dz)] = B('yellow_stained_glass') if dx * dz == 0 else B('quartz_pillar[axis=y]')
                V[(cx + dx, g + 3, cz + dz)] = B('waxed_cut_copper') if dx * dz else B('glass')
        for y in range(g + 2, g + 80):
            EXTRA[(cx, y, cz)] = B('yellow_stained_glass')


def falls():
    for (x, z) in RIVER_END[:1]:
        for w in (-1, 0, 1):
            for y in range(-120, -18):
                EXTRA[(x + w, y, z)] = B('water')
    for (pts, sm, width, kind) in PATHS[:1] + PATHS[4:5]:
        x, z = pts[-1]
        for k in range(1, 12):
            px, pz = round(x + (x / math.hypot(x, z)) * k), round(z + (z / math.hypot(x, z)) * k)
            if not inside(px, pz):
                for y in range(-120, HEIGHT.get((round(x), round(z)), -30)):
                    EXTRA[(px, y, pz)] = B('water')
                break


def build():
    for d in (V, EXTRA, HEIGHT, ROAD, RIVER):
        d.clear()
    PATHCELLS.clear()
    RIVER_END.clear()
    MARKERS.clear()
    USED.clear()
    PATHS.clear()
    LOTS.clear()
    ground()
    core()
    streets()
    plazas()
    river()
    infill()
    assign_kinds()
    fill_ground()
    place_lots()
    footpaths()
    footbridges()
    lamps()
    lighthouses()
    nature()
    falls()
    return V


if __name__ == '__main__':
    t0 = time.time()
    build()
    shops = sorted(m[0] for m in MARKERS if m[0].startswith('shop:'))
    print(len(V), 'blocks,', len(LOTS), 'buildings,', len(shops), 'shops,', len(MARKERS), 'markers', round(time.time() - t0, 1), 's')
    from voxrender import render
    out = os.path.join(HERE, 'preview')
    os.makedirs(out, exist_ok=True)
    t0 = time.time()
    render(V, os.path.join(out, 'solsticio5.png'), scale=2, extra=EXTRA)
    render(V, os.path.join(out, 'solsticio5-district.png'), scale=5,
           keep=lambda x, y, z: 40 <= x <= 115 and -40 <= z <= 35 and y > -45)
    render(V, os.path.join(out, 'solsticio5-street.png'), scale=9,
           keep=lambda x, y, z: 50 <= x <= 80 and -12 <= z <= 14 and y > -40)
    print('render', round(time.time() - t0, 1), 's')
    if '--export' in sys.argv:
        print('export', city3.export(sys.argv[sys.argv.index('--export') + 1]))
