"""Solsticio, draft 4: a real city. About 250 blocks across, inside a barrier of light.

Rings from the centre:
  - acropolis (Ayuntamiento rotunda under the armillary sun), from draft 3;
  - civic ring: the four NPC halls, the four player plots, gardens and cascade basins;
  - the city: a square grid of streets with rows of houses and shops, fountains at the crossings,
    a hidden tavern under each inner fountain, a diagonal park with the lighthouses;
  - the promenade: gardens along the rim, sun gates on the axes, waterfalls into the void.

Symmetry: every building shell sits on the D4 grid. Shops differ by district (east, west, north,
south), and each district is mirror-symmetric across its own avenue. Player scale: doors 2 high,
storeys 4 high, streets 5-9 wide.

Markers: CityLayout's (arrival, town_hall_portal, town_hall_waystone, mayor, trading_hall,
inventor, gardener, priest, player_plot) plus new ones for the commerce work:
  shop:<type>            the shopkeeper's spot behind the counter
  sidequest:<district>_inn  the innkeeper of each district's inn
  resident               a villager's home (upstairs)
  easter:tavern          the hidden tavern under an inner fountain

    python art/solsticio/city4.py [--export path/to/city.nbt]
"""
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import city3  # noqa: E402
from voxkit import Voxels, ab, tf, T8, rot_state, orient, FACING  # noqa: E402

R, P = 125, 6
T1, T2, T3 = 20, 50, 106           # acropolis and civic ring are round (d); the city is square (a)
Y1, Y2, Y3, Y4 = 0, -5, -10, -14
B = lambda n: 'minecraft:' + n
AIR = B('air')
LAMP = B('waxed_copper_bulb[lit=true,powered=false]')
POST = B('tuff_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
PANE_U = B('glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]')
PANE_W = B('glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]')
LEAVES = B('flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]')

V = Voxels()
EXTRA = {}
MARKERS = []
city3.V, city3.EXTRA, city3.MARKERS = V, EXTRA, MARKERS
city3.SUN_Y, city3.SUN_R = 80, 30


def outline(x, z):
    return ((abs(x) / R) ** P + (abs(z) / R) ** P) ** (1 / P)


def floor_at(x, z):
    d = math.hypot(x, z)
    a = max(abs(x), abs(z))
    return Y1 if d <= T1 else Y2 if d <= T2 else Y3 if a <= T3 else Y4


def everywhere(r):
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            yield x, z


def district(t):
    sx, sz, sw = t
    if not sw:
        return 'east' if sx > 0 else 'west'
    return 'south' if sz > 0 else 'north'


# ---------------- island (a plain hollow shell; the belly matters little) ----------------
def island():
    for x, z in everywhere(R):
        e = outline(x, z)
        if e > 1:
            continue
        a, b = ab(x, z)
        top = floor_at(x, z)
        depth = int(8 + (1 - e) ** 0.6 * 58 + 4 * (math.cos(a * 0.29) * math.cos(b * 0.29)) ** 2)
        bottom = top - depth
        for y in range(bottom, top + 1):
            k = top - y
            if k > 5 and y - bottom > 3 and e < 0.985:
                continue
            blk = 'grass_block' if k == 0 else 'dirt' if k <= 3 else \
                ('calcite' if k % 9 == 0 else 'tuff' if (a + b + y) % 4 == 0 else 'stone')
            V[(x, y, z)] = B(blk)
        if e < 0.92 and (a * 5 + b * 3) % 13 == 0:
            for dy in range(1, 2 + (a + b) % 4):
                V[(x, bottom - dy, z)] = B('hanging_roots')
        elif e < 0.92 and (a * 7 + b * 2) % 29 == 0:
            V[(x, bottom, z)] = B('verdant_froglight')
    tip = Y1 - 8 - 58 - 4
    for k in range(0, 18):
        r = 6 * (1 - k / 18) ** 0.7
        for dx in range(-6, 7):
            for dz in range(-6, 7):
                dd = math.hypot(dx, dz)
                if dd <= r:
                    V[(dx, tip - k, dz)] = B('amethyst_block' if dd > r - 1.5 else 'budding_amethyst')


# ---------------- terrace walls, balustrades, stairs ----------------
def terraces():
    tan = math.tan(math.radians(22.5))
    for x, z in everywhere(R):
        e = outline(x, z)
        if e > 1:
            continue
        a, b = ab(x, z)
        d = math.hypot(x, z)
        for inside, hi, lo in ((T1 - 1 < d <= T1, Y1, Y2), (T2 - 1 < d <= T2, Y2, Y3), (T3 - 1 < a <= T3 and d > T2, Y3, Y4)):
            if not inside:
                continue
            for y in range(lo, hi + 1):
                V[(x, y, z)] = B('calcite')
            V[(x, hi, z)] = B('waxed_cut_copper')
            V[(x, hi - 2, z)] = B('waxed_oxidized_cut_copper')
            gap = b <= 4 or abs(a - b) <= 2 or (hi == Y1 and abs(b - a * tan) <= 1.5)
            if not gap:
                V[(x, hi + 1, z)] = POST if (a + b) % 4 == 0 else B('waxed_copper_grate')
        if 0.985 < e <= 1 and b > 4 and not (abs(b - a * tan) <= 2):
            V[(x, Y4 + 1, z)] = B('calcite')
            if (a + b) % 6 == 0:
                V[(x, Y4 + 2, z)] = B('waxed_copper_grate')
    # grand stairs on the axes (9 wide) down every terrace edge
    for (edge, hi, lo) in ((T1, Y1, Y2), (T2, Y2, Y3), (T3, Y3, Y4)):
        n = hi - lo
        for w in range(0, 5):
            for k in range(0, n + 1):
                s = edge - 1 + k
                V.sym(s, hi - k, w, B('polished_tuff_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                      if k < n else B('polished_tuff'))
                for y in range(hi - k + 1, hi + 4):
                    V.sym(s, y, w, AIR)
    # diagonal flights: acropolis to the plots, civic ring to the park
    for (start, hi, n) in ((14, Y1, 5), (35, Y2, 5)):
        for k in range(0, n + 1):
            s = start + k
            for w in (-1, 0, 1):
                V.sym(s + w, hi - k, s, B('polished_tuff'))
                for y in range(hi - k + 1, hi + 3):
                    V.sym(s + w, y, s, AIR)


# ---------------- civic ring: gardens and cascade basins ----------------
def civic():
    tan = math.tan(math.radians(22.5))
    for x, z in everywhere(T2):
        d = math.hypot(x, z)
        if d <= T1 or d > T2 - 1:
            continue
        a, b = ab(x, z)
        if abs(b - a * tan) <= 1.3 and d < 44:
            V[(x, Y2, z)] = B('water')
            V[(x, Y2 - 1, z)] = B('calcite')
        elif abs(b - a * tan) <= 3.3 and 43 <= d <= 47:
            V[(x, Y2, z)] = B('water')
            V[(x, Y2 - 1, z)] = B('calcite')
        elif abs(b - a * tan) <= 4.3 and 42 <= d <= 48:
            V[(x, Y2 + 1, z)] = B('calcite')
        elif V.get((x, Y2, z)) == B('grass_block') and (x, Y2 + 1, z) not in V:
            n = (a * 7 + b * 13) % 23
            if n in (1, 12):
                V[(x, Y2 + 1, z)] = B(('allium', 'cornflower', 'azure_bluet', 'lily_of_the_valley')[(a + b) % 4])
            elif n == 5:
                V[(x, Y2 + 1, z)] = B('short_grass')
    # channels from the canal ring over the acropolis edge, spilling into the basins
    for x, z in everywhere(T1 + 1):
        d = math.hypot(x, z)
        a, b = ab(x, z)
        if 18.4 < d <= T1 and abs(b - a * tan) <= 1.3:
            V[(x, Y1, z)] = B('water')
            V[(x, Y1 - 1, z)] = B('calcite')
    # the acropolis channels spill down into the basins
    th = math.radians(22.5)
    if True:
        for w in (-1, 0, 1):
            bx, bz = round((T1 + 0.8) * math.cos(th)) + w, round((T1 + 0.8) * math.sin(th))
            for yy in range(Y2 + 1, Y1):
                for t in T8:
                    p, q = tf(bx, bz, t)
                    EXTRA.setdefault((p, yy, q), B('water'))
    for (x, z) in ((26, 11), (11, 26), (40, 8)):
        tree(x, z, Y2, 8, 3)


def tree(x, z, y0, h=8, r=3):
    for y in range(y0 + 1, y0 + h + 1):
        V.sym(x, y, z, B('stripped_birch_log[axis=y]'))
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(-2, r):
                if math.sqrt(dx * dx + dz * dz + (dy * 1.4) ** 2) <= r + 0.3 and (dx or dz or dy > 0):
                    if (x + dx, y0 + h + dy, z + dz) not in V or V[(x + dx, y0 + h + dy, z + dz)] == AIR:
                        V.sym(x + dx, y0 + h + dy, z + dz, LEAVES)


# ---------------- buildings ----------------
SHOPS = {'south': ['rarities', 'minerals', 'creatures', 'bakery'],
         'east': ['parts', 'smithy', 'textiles', 'maps'],
         'north': ['seeds', 'nursery', 'apothecary', 'apiary'],
         'west': ['bookstore', 'records', 'museum', 'curiosities']}
SHOP_STYLE = {   # awning carpet colour, sign band, shelf
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
WALLS = ['calcite', 'smooth_quartz', 'calcite', 'polished_tuff']
TRIMS = ['stripped_birch_log', 'stripped_cherry_log']
ROOFS = ['waxed_cut_copper', 'waxed_exposed_cut_copper', 'waxed_weathered_cut_copper', 'waxed_oxidized_cut_copper']
BEDS = ['light_blue', 'yellow', 'white', 'cyan']


def building(kind, st):
    """Local frame: u across the street (-4..4), w depth (0 = front, facing 'north' = the street),
    y from the foundation (0). Returns {(u, y, w): state} and the local marker spots."""
    L, marks = {}, []
    F = st['floors']
    top = 5 * F
    wall, trim, roof = B(st['wall']), B(st['trim'] + '[axis=y]'), st['roof']
    shop = kind.startswith('shop:')
    for u in range(-4, 5):
        for w in range(0, 9):
            L[(u, 0, w)] = B('polished_tuff')
            edge = abs(u) == 4 or w in (0, 8)
            corner = abs(u) == 4 and w in (0, 8)
            for y in range(1, top + 1):
                band = y % 5 == 0
                if edge:
                    L[(u, y, w)] = trim if corner else (B('waxed_cut_copper') if band else wall)
                else:
                    L[(u, y, w)] = B('birch_planks') if band else AIR
    # openings
    for s in range(F):
        y0 = 5 * s
        for u in (-2, -1, 1, 2) if s else (-2, 2):
            for y in (y0 + 2, y0 + 3):
                if s or not shop:
                    L[(u, y, 0)] = PANE_U
                L[(u, y, 8)] = PANE_U
        for w in (2, 6):
            for y in (y0 + 2, y0 + 3):
                if not (shop and s == 0 and w == 6):
                    L[(-4, y, w)] = PANE_W
                    L[(4, y, w)] = PANE_W
    L[(0, 1, 0)] = B('birch_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
    L[(0, 2, 0)] = B('birch_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    L[(0, 3, 0)] = B('glass')
    if shop:
        carpet, band, shelf = SHOP_STYLE[kind.split(':')[1]]
        for u in (-3, -2, -1, 1, 2, 3):
            for y in (1, 2, 3):
                L[(u, y, 0)] = B('glass')
        for u in range(-3, 4):
            L[(u, 4, 0)] = B(band)
        for u in range(-4, 5):
            L[(u, 4, -1)] = B(roof + '_slab[type=top,waterlogged=false]')
            L[(u, 5, -1)] = B(carpet + '_carpet')
        for u in range(-2, 3):
            L[(u, 1, 4)] = B('stripped_birch_wood[axis=x]')
        for u in range(-3, 3):
            for y in (1, 2):
                L[(u, y, 7)] = B(shelf)
        for y in (1,):
            for w in (5, 6):
                L[(-3, y, w)] = B(shelf)
        L[(0, 4, 2)] = LAMP
        marks.append((kind, (0, 1, 6)))
    else:
        for u in (-1, 0, 1):
            L[(u, 4, -1)] = B(roof + '_slab[type=top,waterlogged=false]')
        L[(-3, 1, 7)] = B('crafting_table')
        L[(-3, 1, 6)] = B('barrel[facing=up,open=false]')
        L[(0, 4, 4)] = LAMP
        if kind == 'inn':
            for u in (-2, 2):
                L[(u, 1, 3)] = B('barrel[facing=up,open=false]')
                L[(u, 1, 2)] = B('birch_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
            marks.append(('inn', (0, 1, 5)))
    # ladder up the storeys, bed upstairs
    for y in range(1, top):
        L[(3, y, 7)] = B('ladder[facing=west,waterlogged=false]')
    L[(-2, 6, 5)] = B(st['bed'] + '_bed[facing=south,occupied=false,part=foot]')
    L[(-2, 6, 6)] = B(st['bed'] + '_bed[facing=south,occupied=false,part=head]')
    if kind == 'house' and st.get('resident'):
        marks.append(('resident', (0, 6, 3)))
    # gable roof, ridge across the depth, gables toward the street
    for r in range(0, 5):
        y = top + 1 + r
        for w in range(-1, 10):
            if r < 4:
                L[(4 - r, y, w)] = B(roof + '_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                L[(-(4 - r), y, w)] = B(roof + '_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
            else:
                L[(0, y, w)] = B(roof)
        if r < 4:
            for u in range(-(3 - r), 4 - r):
                for w in (0, 8):
                    L[(u, y, w)] = wall
    L[(0, top + 2, 0)] = B('glass')
    L[(0, top + 2, 8)] = B('glass')
    L[(0, top + 6, -1)] = B('lightning_rod[facing=up,powered=false,waterlogged=false]')
    return L, marks


ROWS = [  # front on the octant's a axis, facing +a (toward a larger a) or -a
    dict(front=61, dirn=+1, slots=[(5, 13), (15, 23), (27, 35), (37, 45)], floors=2),
    dict(front=67, dirn=-1, slots=[(5, 13), (15, 23), (27, 35), (37, 45), (51, 59)], floors=2),
    dict(front=86, dirn=+1, slots=[(5, 13), (15, 23), (27, 35), (37, 45), (51, 59), (61, 69)], floors=3),
    dict(front=92, dirn=-1, slots=[(5, 13), (15, 23), (27, 35), (37, 45), (51, 59), (61, 69), (75, 83)], floors=2),
]


def kind_of(row, slot, t):
    if row in (0, 1) and slot in (0, 1):
        return 'shop:' + SHOPS[district(t)][row * 2 + slot]
    if row == 2 and slot == 0:
        return 'inn'
    return 'house'


def place_building(row, slot):
    rdef = ROWS[row]
    b0, b1 = rdef['slots'][slot]
    bc = (b0 + b1) // 2
    dirn, front = rdef['dirn'], rdef['front']
    floors = rdef['floors'] + (1 if (row + slot) % 3 == 2 and row != 2 else 0)
    st = dict(wall=WALLS[(row + slot) % 4], trim=TRIMS[(row * 3 + slot) % 2], roof=ROOFS[(row * 2 + slot) % 4],
              floors=floors, bed=BEDS[(row + 2 * slot) % 4], resident=(row >= 2 and slot % 2 == 1))
    f_local = lambda v: (-dirn * v[1], v[0])        # (du, dw) -> (dx, dz) in the octant frame
    mirror = dirn < 0
    cache = {}
    for t in T8:
        kind = kind_of(row, slot, t)
        if kind not in cache:
            if kind.startswith('shop:'):
                sst = dict(st, wall='calcite', floors=2)
                cache[kind] = building(kind, sst)
            else:
                cache[kind] = building(kind, st)
        L, marks = cache[kind]
        for (u, y, w), state in L.items():
            x, z = front - dirn * w, bc + u
            p, q = tf(x, z, t)
            V[(p, Y3 + y, q)] = rot_state(orient(state, f_local, mirror), t)
        for name, (u, y, w) in marks:
            x, z = front - dirn * w, bc + u
            p, q = tf(x, z, t)
            label = name if name != 'inn' else 'sidequest:' + district(t) + '_inn'
            MARKERS.append((label, (p, Y3 + y, q)))


# ---------------- streets, fountains, lamps, park ----------------
S1, S2 = (62, 66), (87, 91)
CROSS = [(24, 26), (48, 50), (72, 74), (96, 98)]


def is_street(a, b):
    if b <= 4:
        return True
    if S1[0] <= a <= S1[1] or S2[0] <= a <= S2[1]:
        return True
    return any(c0 <= b <= c1 for c0, c1 in CROSS) and b <= a - 4


def streets():
    for x, z in everywhere(T3):
        a, b = ab(x, z)
        d = math.hypot(x, z)
        if d <= T2 or a > T3 - 1:
            continue
        if is_street(a, b):
            center = (b <= 1) or a in (S1[0] + 2, S2[0] + 2)
            curb = b == 4 or a in S1 or a in S2
            V[(x, Y3, z)] = B('calcite' if center else 'waxed_cut_copper' if curb and b <= 4 else 'polished_tuff')
        elif a - b <= 3:
            n = (a * 5 + b * 3) % 17
            if n == 0:
                V[(x, Y3 + 1, z)] = B(('allium', 'cornflower', 'lily_of_the_valley')[a % 3])
        elif a in (76, 77) or a >= 101:
            if (a + b) % 7 == 0:
                V[(x, Y3 + 1, z)] = B('short_grass')
    # fountains where the avenues cross the ring streets
    for cx in (64, 89):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                ring = max(abs(dx), abs(dz)) == 2
                V.sym(cx + dx, Y3, dz, B('calcite'))
                V.sym(cx + dx, Y3 + 1, dz, B('calcite') if ring else B('water'))
        for y in range(Y3 + 1, Y3 + 4):
            V.sym(cx, y, 0, B('waxed_chiseled_copper'))
        V.sym(cx, Y3 + 4, 0, LAMP)
    # hidden tavern under each inner fountain: two hatches in the ring street
    for x in range(60, 69):
        for z in range(0, 5):
            for y in range(Y3 - 4, Y3):
                V.sym(x, y, z, AIR)
            V.sym(x, Y3 - 5, z, B('stripped_birch_wood[axis=y]') if (x + z) % 2 else B('birch_planks'))
    for y in range(Y3 - 4, Y3):
        V.sym(60, y, 3, B('ladder[facing=east,waterlogged=false]'))
    V.sym(60, Y3, 3, B('waxed_copper_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]'))
    for x in (62, 66):
        for z in (0, 2):
            V.sym(x, Y3 - 4, z, B('barrel[facing=up,open=false]'))
    V.sym(64, Y3 - 4, 0, B('jukebox[has_record=false]'))
    for x in (61, 67):
        V.sym(x, Y3 - 1, 1, LAMP)
    for x in (60, 68):
        for y in range(Y3 - 4, Y3):
            V.sym(x, y, 4, B('stripped_cherry_log[axis=y]'))
    for p, q in sorted({tf(64, 0, t) for t in T8}):
        MARKERS.append(('easter:tavern', (p, Y3 - 4, q)))
    # lamps at the alley mouths along the ring streets, and along the avenues
    for (a0, a1) in (S1, S2):
        for b in (14, 36, 60, 84):
            for a in (a0, a1):
                if b <= a - 5:
                    V.sym(a, Y3 + 1, b, POST)
                    V.sym(a, Y3 + 2, b, POST)
                    V.sym(a, Y3 + 3, b, LAMP)
    for a in (56, 76, 82, 100):
        V.sym(a, Y3 + 1, 4, POST)
        V.sym(a, Y3 + 2, 4, POST)
        V.sym(a, Y3 + 3, 4, LAMP)
    # the diagonal park: trees on the diagonal, benches
    for c in (58, 72, 82):
        tree(c, c, Y3, 7, 3)
        V.sym(c - 3, Y3 + 1, c - 1, B('birch_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))


# ---------------- lighthouses in the park corners ----------------
def lighthouses():
    cx, base = 98, Y3
    for y in range(base + 1, base + 34):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) != 2:
                    V.sym(cx + dx, y, cx + dz, AIR)
                    continue
                corner = abs(dx) == 2 and abs(dz) == 2
                band = (y - base) % 8 == 0
                window = not corner and (dx == 0 or dz == 0) and (y - base) % 8 in (4, 5)
                V.sym(cx + dx, y, cx + dz, B('waxed_cut_copper' if band else 'quartz_pillar[axis=y]' if corner else
                                            'glass' if window else 'calcite'))
    g = base + 34
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            V.sym(cx + dx, g, cx + dz, B('waxed_cut_copper_slab[type=bottom,waterlogged=false]')
                  if max(abs(dx), abs(dz)) == 3 else B('gold_block'))
    V.sym(cx, g + 1, cx, B('beacon'))
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if (dx, dz) != (0, 0):
                V.sym(cx + dx, g + 1, cx + dz, B('glass') if dx * dz == 0 else B('quartz_pillar[axis=y]'))
                V.sym(cx + dx, g + 2, cx + dz, B('glass') if dx * dz == 0 else B('quartz_pillar[axis=y]'))
            V.sym(cx + dx, g + 3, cx + dz, B('waxed_cut_copper') if dx * dz else B('glass'))
    for y in range(g + 2, g + 80):
        for t in T8:
            p, q = tf(cx, cx, t)
            EXTRA[(p, y, q)] = B('white_stained_glass')


# ---------------- the promenade ----------------
def promenade():
    tan = math.tan(math.radians(22.5))
    for x, z in everywhere(R):
        a, b = ab(x, z)
        e = outline(x, z)
        if a <= T3 or e > 1:
            continue
        if V.get((x, Y4, z)) != B('grass_block'):
            continue
        if a in (T3 + 2, T3 + 3) or b <= 4:
            V[(x, Y4, z)] = B('calcite' if (a + b) % 2 else 'polished_tuff')
        elif abs(b - a * tan) <= 1.4 and a >= 112:
            V[(x, Y4, z)] = B('water')
            V[(x, Y4 - 1, z)] = B('calcite')
        elif (a * 3 + b * 5) % 19 == 0:
            V[(x, Y4 + 1, z)] = B(('oxeye_daisy', 'blue_orchid', 'azure_bluet', 'allium')[(a + b) % 4])
    # the eight falls into the void
    for w in (-1, 0, 1):
        rim = 125 / (1 + tan ** 6) ** (1 / 6)
        bx, bz = round(rim * math.cos(0) + 1), round(rim * tan) + w
        for yy in range(Y4 - 70, Y4):
            for t in T8:
                p, q = tf(bx, bz, t)
                EXTRA.setdefault((p, yy, q), B('water'))
    # sun gates on the axes
    for w in (5, 6):
        for y in range(Y4 + 1, Y4 + 11):
            V.sym(R - 4, y, w, B('calcite'))
    for w in range(0, 7):
        V.sym(R - 4, Y4 + 11 if w < 5 else Y4 + 10, w, B('waxed_cut_copper'))
    V.sym(R - 4, Y4 + 12, 0, B('gold_block'))
    for (a, b) in ((116, 20), (116, 40), (112, 70)):
        tree(a, b, Y4, 8, 3)


def build():
    V.clear()
    EXTRA.clear()
    MARKERS.clear()
    island()
    terraces()
    city3.acropolis()
    city3.rotunda()
    civic()
    streets()
    for row in range(len(ROWS)):
        for slot in range(len(ROWS[row]['slots'])):
            place_building(row, slot)
    city3.halls()
    city3.armillary()
    city3.plots()
    lighthouses()
    promenade()
    return V


if __name__ == '__main__':
    t0 = time.time()
    build()
    shops = sum(1 for m in MARKERS if m[0].startswith('shop:'))
    print(len(V), 'blocks,', len(MARKERS), 'markers,', shops, 'shops', round(time.time() - t0, 1), 's')
    from voxrender import render
    out = os.path.join(HERE, 'preview')
    os.makedirs(out, exist_ok=True)
    t0 = time.time()
    render(V, os.path.join(out, 'solsticio4.png'), scale=2, extra=EXTRA)
    render(V, os.path.join(out, 'solsticio4-district.png'), scale=5,
           keep=lambda x, y, z: 44 <= x <= 108 and -34 <= z <= 34 and y > -20)
    render(V, os.path.join(out, 'solsticio4-street.png'), scale=8,
           keep=lambda x, y, z: 52 <= x <= 80 and 0 <= z <= 26 and y > -16)
    print('render', round(time.time() - t0, 1), 's')
    if '--export' in sys.argv:
        city3.V, city3.MARKERS = V, MARKERS
        print('export', city3.export(sys.argv[sys.argv.index('--export') + 1]))
