"""Solsticio v8, phase 1: the urban plan (art/concepts/solsticio_plan.py) in Minecraft blocks.

Terrain, street network, plazas, green and water, the massing of every block and the landmarks at
full scale. Levels come from net8 (every street a designed profile, plazas and platforms flat, lots
stepping with the street they face); this file turns them into blocks:

  - ground: each column is solid from its surface through a skin, and hollow only where every face
    that could ever be seen is closed (the rule of city5.fill_ground, checked by leaks());
    retaining faces are dressed stone, the island's rim and underside are rock strata;
  - network: the Axis of the Sun in quartz with the glass-covered canal of light (water over a lit
    bed, stepping with the flights), tree-lined boulevards with lamps, streets in sandstone, lanes
    in mud brick, the arcaded Street of Crafts in brick, stairs wherever a level changes by one;
  - plazas with their centrepieces, the Midday Park and its lake, courtyard and pocket gardens, the
    Edge Promenade with its balustrade and a templete at the end of every radial;
  - massing: every lot one building on its pad, storeys of four, heights by district and street,
    roofs by district; landmarks: the Palace of the Solstice (dome and sun tower), the Great Market
    of Light (glass nave), the Temple of Dawn, Terra's Workshop, the palm house, the Clock Tower;
  - markers: everything CityLayout, CommerceSites and SolsticioStory read.

    python art/solsticio/city8.py                   # build, check, report
    python art/solsticio/city8.py --render [names]  # plus renders into SOLSTICIO8_OUT
"""
import math
import os
import sys
import time
from collections import Counter, defaultdict, deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import net8 as N  # noqa: E402
from grid8 import Grid  # noqa: E402

OUT = os.environ.get('SOLSTICIO8_OUT', 'E:/Elias/Codex/Entrelumen-ssd/solsticio8'
                     if os.path.isdir('E:/Elias/Codex/Entrelumen-ssd') else os.path.join(HERE, 'preview', 'solsticio8'))
EXT = 174
YMIN, YMAX = -96, 174
G = Grid(-EXT, EXT, YMIN, YMAX, -EXT, EXT, alloc=False)   # allocated by build()
MARKERS = []
EXTRA = {}               # preview-only voxels (the falls)
TOP = {}                 # (x, z) -> y of the top ground block
N4, N8 = N.N4, N.N8
REPORT = []


def B(n):
    return n if ':' in n.split('[')[0] else 'minecraft:' + n


def stairs(mat, facing, half='bottom', shape='straight'):
    return B('%s_stairs[facing=%s,half=%s,shape=%s,waterlogged=false]' % (mat, facing, half, shape))


def wall(mat, e='none', n='none', s='none', w='none', up='true'):
    return B('%s_wall[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (mat, e, n, s, up, w))


def leaves(kind):
    return B('%s_leaves[distance=1,persistent=true,waterlogged=false]' % kind)


LANTERN = B('lantern[hanging=false,waterlogged=false]')
FACING = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}


def h(x, z, seed=0):
    n = (x * 374761393 + z * 668265263 + seed * 1442695041) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def kind(c):
    return N.CELL.get(c)


# ---------------- ground ----------------
BOTTOM = {}


def e_of(x, z):
    return math.hypot(x, z) / N.edge_r(x, z)


def bottom_at(x, z):
    """The island's underside: an inverted dome of rock, deepest under the summit."""
    e = min(1.0, e_of(x, z))
    return int(-(14 + 56 * (1 - e) ** 1.35) - 6 * h(x // 5, z // 5, 3) - 3 * h(x, z, 4))


def ground_tops():
    """Top ground block of every column: the level, except under water (the lake bed three below
    the surface, canal beds two below the walking level)."""
    wl = N.LAKE_LEVEL[0]
    for c, k in N.CELL.items():
        lev = N.LEVEL[c]
        if k == 'lake':
            TOP[c] = wl - 3
        elif k == 'canal':
            TOP[c] = N.CANAL_LEVEL.get(c, lev) - 2
        else:
            TOP[c] = lev
        BOTTOM[c] = min(bottom_at(*c), TOP[c] - 8)


GREEN = {'park', 'court', 'pocket', 'temple_ground', 'lake'}
STRATA = ['stone', 'stone', 'calcite', 'stone', 'tuff', 'stone', 'andesite', 'calcite', 'dripstone_block', 'stone']


def rock(x, y, z):
    band = (y + int(3 * h(x // 7, z // 7, 9))) // 3
    return B(STRATA[band % len(STRATA)])


def retaining(c, y, top):
    """Dressed face of a column above a lower neighbour: ashlar with courses in the city, garden
    stone in the green, the palace's podium in quartz."""
    k = kind(c)
    d = top - y
    if k == 'palace':
        return B('quartz_bricks' if d % 5 else 'chiseled_quartz_block')
    if k in GREEN or k == 'path':
        return B('mossy_stone_bricks' if h(c[0], y + c[1], 11) < 0.35 else 'stone_bricks')
    if k in ('building', 'plot', 'market', 'workshop', 'temple', 'greenhouse', 'tower'):
        return B('tuff_bricks' if d % 4 else 'polished_tuff')
    return B('calcite' if d % 4 else 'polished_diorite')


def fill_ground():
    """Every column solid from its surface down through a skin of five and up from its underside
    through a skin of four; between them hollow only where the neighbours' own skins cover every
    side (city5's rule), and never on the rim, whose outer face is rock all the way."""
    hollow = {}
    for c, top in TOP.items():
        x, z = c
        bot = BOTTOM[c]
        ns = [(x + a, z + b) for a, b in N4]
        rim = any(n not in TOP for n in ns)
        ntops = [TOP[n] for n in ns if n in TOP]
        low = min(ntops) if ntops else top
        face_top = low - 2
        face_bottom = max((BOTTOM[n] for n in ns if n in TOP), default=bot) + 3
        col = G.col(x, z) - G.y0
        d = G.data
        h0 = None
        green = kind(c) in GREEN
        for y in range(bot, top + 1):
            k = top - y
            if not rim and k > 5 and y < face_top and y - bot > 3 and y > face_bottom:
                if h0 is None:
                    h0 = y
                continue
            if y > low and not rim:
                st = retaining(c, y, top)
            elif rim and y > bot + 2:
                st = rock(x, y, z) if k > 2 or not green else B('dirt')
            elif k == 0:
                st = B('grass_block') if green else B('stone')
            elif k <= 3 and green:
                st = B('dirt')
            else:
                st = rock(x, y, z)
            d[col + y] = G.sid(st)
        if h0 is not None:
            hi = h0
            while hi + 1 <= top and not G.filled(x, hi + 1, z):
                hi += 1
            hollow[c] = (h0, hi)
        # roots and dripstone under the island
        r = h(x, z, 21)
        if e_of(x, z) < 0.95 and r < 0.05:
            for dy in range(1, 2 + int(r * 60)):
                G.setdefault(x, bot - dy, z, B('pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]')
                             if dy == 1 + int(r * 60) else B('dripstone_block'))
        elif e_of(x, z) < 0.9 and r < 0.12:
            for dy in range(1, 2 + int(r * 25)):
                G.setdefault(x, bot - dy, z, B('hanging_roots[waterlogged=false]'))
    return hollow


# ---------------- surfaces ----------------
STAIR_MAT = {'axis': 'smooth_quartz', 'trees': 'smooth_quartz', 'boulevard': 'polished_diorite', 'street': 'smooth_sandstone',
             'lane': 'mud_brick', 'crafts': 'brick', 'promenade': 'polished_diorite', 'plaza': 'polished_diorite',
             'path': 'mossy_stone_brick', 'palace': 'quartz', 'canal': 'smooth_sandstone', 'temple_ground': 'mossy_stone_brick'}


def cls(c):
    """What a walking cell is built as: its owner path's class (a boulevard crossing the Axis is Axis)."""
    k = kind(c)
    if k == 'plaza':
        return 'plaza'
    if c in N.OWNER:
        p = N.PATHS[N.OWNER[c]]
        if 'axis' in p.kinds:
            return 'axis'
        if k == 'canal':
            return 'canal'
        return p.cls if p.cls != 'canal' else k
    return k


def axis_part(c):
    x = abs(c[0])
    if x <= 1:
        return 'canal'
    if 6 <= x <= 8:
        return 'trees'
    return 'pave'


WALKABLE = N.WALK | {'plaza', 'palace'}


def walkable(c):
    k = kind(c)
    if k in WALKABLE or c in N.CULVERT:
        return True
    return k == 'temple_ground' and c in APPROACH


APPROACH = set()


def pave(c):
    x, z = c
    k = cls(c)
    if k == 'axis':
        part = axis_part(c)
        if part == 'trees':
            return B('grass_block')
        if abs(x) == 2:
            return B('waxed_cut_copper')
        if abs(x) >= 10:
            return B('polished_diorite')
        if z % 6 == 0:
            return B('quartz_bricks')
        return B('chiseled_quartz_block') if (abs(x) == 5 and z % 6 == 3) else B('smooth_quartz')
    if k == 'boulevard':
        edge = any(cls((x + a, z + b)) != 'boulevard' for a, b in N4)
        if edge:
            return B('polished_andesite')
        return B('polished_diorite' if (x + z) % 4 else 'calcite')
    if k == 'street':
        edge = any(not walkable((x + a, z + b)) for a, b in N4)
        if edge:
            return B('smooth_sandstone')
        return B('cut_sandstone' if (x // 2 + z // 2) % 3 else 'sandstone')
    if k == 'lane':
        return B('mud_bricks' if (x + 2 * z) % 5 else 'packed_mud')
    if k == 'crafts':
        return B('bricks' if (x // 2 + z // 2) % 2 else 'terracotta')
    if k == 'promenade':
        return B('calcite' if (x + z) % 5 else 'polished_diorite')
    if k == 'path':
        return B('dirt_path')
    if k == 'plaza':
        return plaza_floor(c)
    if k == 'palace':
        return B('chiseled_quartz_block' if (x % 6 == 0 and z % 6 == 0) else 'smooth_quartz')
    if k == 'temple_ground':                                    # the stair up the temple knoll
        return B('polished_diorite')
    return B('stone')


PLAZA_STYLE = {
    'Plaza Mayor': ('smooth_quartz', 'polished_diorite', 'gold_block'),
    'Plaza del Portal': ('calcite', 'smooth_quartz', 'gold_block'),
    'Plaza del Mercado': ('cut_sandstone', 'white_terracotta', 'terracotta'),
    'Plaza de las Fuentes': ('calcite', 'prismarine_bricks', 'polished_diorite'),
    'Plaza del Reloj': ('polished_diorite', 'calcite', 'waxed_cut_copper'),
    'Plaza de los Viajeros': ('calcite', 'polished_andesite', 'lapis_block'),
    'Plaza del Reloj de Sol': ('smooth_quartz', 'cut_sandstone', 'gold_block'),
    'Plazoleta de los Faroles': ('polished_andesite', 'calcite', 'polished_andesite'),
    'Plazoleta del Pan': ('cut_sandstone', 'bricks', 'cut_sandstone'),
}


def plaza_info(c):
    pl = N.PLAZAS[N.PLAZA_OF[c]]
    name = pl[0].replace(' (part)', '')
    return name, pl


def plaza_floor(c):
    name, pl = plaza_info(c)
    cx, cz = pl[1]
    a, b, acc = PLAZA_STYLE.get(name, ('calcite', 'polished_diorite', 'polished_diorite'))
    d = math.hypot(c[0] - cx, c[1] - cz)
    if int(d) % 6 == 5:
        return B(acc) if name != 'Plaza Mayor' or int(d) == 11 else B(b)
    return B(a if (int(d) // 2 + int(math.degrees(math.atan2(c[1] - cz, c[0] - cx)) // 15)) % 2 else b)


def surfaces():
    """Top blocks, the canal and lake water, and stairs wherever a walking level rises by one."""
    wl = N.LAKE_LEVEL[0]
    for c, k in N.CELL.items():
        x, z = c
        t = TOP[c]
        lev = N.LEVEL[c]
        if k == 'lake':
            G.set(x, t, z, B('sand' if h(x, z, 5) < 0.6 else 'clay'))
            for y in range(t + 1, wl + 1):
                G.set(x, y, z, B('water[level=0]'))
            continue
        if k == 'canal' or (cls(c) == 'axis' and axis_part(c) == 'canal'):
            lit = (x * 3 + z * 7) % 5 == 0
            covered = c in N.CULVERT or cls(c) == 'axis'
            if cls(c) == 'axis':
                G.set(x, lev - 2, z, B('ochre_froglight[axis=y]' if (z % 4) else 'sea_lantern'))
                G.set(x, lev - 1, z, B('water[level=0]'))
                G.set(x, lev, z, B('glass'))
                for y in range(lev - 4, lev - 2):             # the channel sits in the ground
                    G.setdefault(x, y, z, B('stone'))
            else:
                cl = N.CANAL_LEVEL.get(c, lev)
                G.set(x, cl - 2, z, B('sea_lantern' if lit else 'prismarine_bricks'))
                G.set(x, cl - 1, z, B('water[level=0]'))
                if covered:                                     # the street's glass deck over it
                    G.set(x, lev, z, B('glass'))
            continue
        if k in GREEN:
            G.set(x, t, z, B('grass_block'))
            continue
        if k == 'building':
            G.set(x, t, z, B('polished_andesite'))
            continue
        if k == 'plot':
            G.set(x, t, z, B('grass_block'))
            continue
        if k in ('market', 'workshop', 'temple', 'greenhouse', 'tower'):
            G.set(x, t, z, B('polished_diorite' if (x + z) % 2 else 'calcite'))
            continue
        if walkable(c):
            G.set(x, t, z, pave(c))
    # stairs: a walking cell with a walking neighbour one higher gets a step toward it
    for c in N.CELL:
        if not walkable(c) or kind(c) == 'canal' or (cls(c) == 'axis' and axis_part(c) != 'pave'):
            continue
        lev = N.LEVEL[c]
        for (a, b) in N4:
            n = (c[0] + a, c[1] + b)
            if walkable(n) and N.LEVEL.get(n) == lev + 1 and not (cls(n) == 'axis' and axis_part(n) == 'canal'):
                mat = STAIR_MAT.get(cls(c), 'stone_brick')
                G.set(c[0], lev + 1, c[1], stairs(mat, FACING[(a, b)]))
                break


def contain_water():
    """No water block may touch air: where one would, it becomes a weir of the canal's stone."""
    wid = G.sid(B('water[level=0]'))
    weir = G.sid(B('prismarine_bricks'))
    changed = True
    rounds = 0
    cells = []
    for c in N.CELL:
        base = G.col(*c)
        for k in range(G.ny):
            if G.data[base + k] == wid:
                cells.append((c[0], G.y0 + k, c[1]))
    while changed and rounds < 20:
        changed = False
        rounds += 1
        for (x, y, z) in cells:
            if G.gid(x, y, z) != wid:
                continue
            for (a, b, cc) in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                if a or b:
                    nx, ny_, nz = x + a, y, z + b
                else:
                    nx, ny_, nz = x, y - 1, z
                if (nx, nz) not in N.CELL or G.gid(nx, ny_, nz) == 0:
                    G.data[G.at(x, y, z)] = weir
                    changed = True
                    break
    return sum(1 for (x, y, z) in cells if G.gid(x, y, z) == weir)


# ---------------- trees, lamps, rails ----------------
def tree(x, z, y0, h_=5, r=2.4, leaf='flowering_azalea', log='stripped_birch_log', only=None):
    for y in range(y0 + 1, y0 + h_ + 1):
        if not G.setdefault(x, y, z, B('%s[axis=y]' % log)):
            return
    lb = leaves(leaf)
    ir = int(r) + 1
    for dx in range(-ir, ir + 1):
        for dz in range(-ir, ir + 1):
            for dy in range(-2, 3):
                if dx * dx + dz * dz + (dy * 1.5) ** 2 <= r * r and h(x + dx, z + dz + dy * 17, 8) > 0.12:
                    if only and not only((x + dx, z + dz)):
                        continue
                    G.setdefault(x + dx, y0 + h_ + dy, z + dz, lb)


def lamp(x, z, y0, height=3, mat='tuff_brick'):
    if G.filled(x, y0 + 1, z):
        return
    for y in range(y0 + 1, y0 + height + 1):
        G.set(x, y, z, wall(mat))
    G.set(x, y0 + height + 1, z, LANTERN)


def open_air(c, y, n=6):
    return all(not G.filled(c[0], y + k, c[1]) for k in range(n))


def planting():
    """Trees and lamps along the network: blossoming rows on the Axis, tree-lined boulevards with
    lamp posts between, lamps down the streets, trees and lamps on the promenade."""
    for c, k in N.CELL.items():
        if cls(c) == 'axis' and axis_part(c) == 'trees' and abs(c[0]) == 7 and c[1] % 6 == 0:
            y = TOP[c]
            tree(c[0], c[1], y, 5, 2.6, 'cherry', 'cherry_log', only=lambda q: cls(q) == 'axis')
        if cls(c) == 'axis' and abs(c[0]) == 10 and c[1] % 8 == 4 and walkable(c) and open_air(c, TOP[c] + 1, 4):
            if not G.filled(c[0], TOP[c] + 1, c[1]):
                lamp(c[0], c[1], TOP[c], 3, 'polished_blackstone')
    for pi, p in enumerate(N.PATHS):
        if not p.levels:
            continue
        n = len(p.pts)
        for si, (x, z) in enumerate(p.pts):
            if si + 1 < n:
                tx, tz = p.pts[si + 1][0] - x, p.pts[si + 1][1] - z
            else:
                tx, tz = x - p.pts[si - 1][0], z - p.pts[si - 1][1]
            ln = math.hypot(tx, tz) or 1
            nx, nz = -tz / ln, tx / ln
            if p.cls == 'boulevard':
                off, every, tphase, lphase = 2.5, 9, 0, 4
            elif p.cls == 'promenade':
                off, every, tphase, lphase = -1.5, 11, 0, 6
            elif p.cls == 'street' and p.width >= 7:
                off, every, tphase, lphase = 3.0, 12, None, 6
            elif p.cls == 'street':
                off, every, tphase, lphase = 1.5, 14, None, 7
            else:
                continue
            for side in ((1, -1) if p.cls != 'promenade' else (1,)):
                q = (round(x + nx * off * side), round(z + nz * off * side))
                if q not in N.OWNER or N.OWNER[q] != pi or G.filled(q[0], TOP[q] + 1, q[1]):
                    continue
                if tphase is not None and si % every == tphase:
                    y = TOP[q]
                    G.set(q[0], y, q[1], B('grass_block'))
                    leaf = 'cherry' if (si // every + side) % 2 else 'flowering_azalea'
                    tree(q[0], q[1], y, 5, 2.3, leaf, 'cherry_log' if leaf == 'cherry' else 'stripped_birch_log',
                         only=lambda c_: walkable(c_))
                elif si % every == lphase and (p.cls != 'street' or side == (1 if (si // every) % 2 else -1)):
                    lamp(q[0], q[1], TOP[q], 3)


def greenery():
    """The park, courtyards, pockets and the temple knoll: trees, flowers, hedges."""
    flowers = ['azure_bluet', 'allium', 'cornflower', 'oxeye_daisy', 'lily_of_the_valley', 'short_grass', 'short_grass']
    for c, k in N.CELL.items():
        if k not in ('park', 'court', 'pocket', 'temple_ground') or c in APPROACH:
            continue
        x, z = c
        y = TOP[c]
        if G.filled(x, y + 1, z):
            continue
        r = h(x, z, 31)
        dens = {'park': 0.02, 'court': 0.022, 'pocket': 0.04, 'temple_ground': 0.012}[k]
        inner = all(kind((x + a, z + b)) == k for a in (-2, 0, 2) for b in (-2, 0, 2))
        if r < dens and inner:
            pick = int(r * 1000) % 4
            leaf, lg = [('flowering_azalea', 'stripped_birch_log'), ('cherry', 'cherry_log'), ('oak', 'oak_log'),
                        ('birch', 'birch_log')][pick]
            tree(x, z, y, 5 + pick % 2, 2.5, leaf, lg)
        elif r < dens + 0.16:
            G.set(x, y + 1, z, B(flowers[int(r * 997) % len(flowers)]))


def rails():
    """A balustrade wherever a walking or garden edge stands two or more over open ground, and all
    along the island's rim."""
    rail = set()
    for c in N.CELL:
        k = kind(c)
        if not (walkable(c) or k in ('court', 'pocket', 'park', 'palace', 'temple_ground')):
            continue
        t = TOP[c]
        if k == 'canal' and c not in N.CULVERT:
            continue
        if G.filled(c[0], t + 1, c[1]):
            continue
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n not in N.CELL:
                rail.add(c)
                break
            if TOP[n] <= t - 2 and not G.filled(n[0], t + 1, n[1]) and kind(n) != 'lake':
                rail.add(c)
                break
    for c in rail:
        t = TOP[c]
        con = {}
        for (a, b), nm in FACING.items():
            n = (c[0] + a, c[1] + b)
            con[nm] = 'low' if (n in rail and TOP.get(n) == t) else 'none'
        straight = (con['east'] == con['west'] == 'low' and con['north'] == con['south'] == 'none') or \
                   (con['north'] == con['south'] == 'low' and con['east'] == con['west'] == 'none')
        post = not straight or (c[0] + 2 * c[1]) % 5 == 0
        mat = 'diorite' if kind(c) != 'palace' else 'andesite'
        G.set(c[0], t + 1, c[1], wall(mat, con['east'], con['north'], con['south'], con['west'], 'true' if post else 'false'))
        if post and (c[0] * 3 + c[1]) % 13 == 0 and not (kind(c) in GREEN):
            G.set(c[0], t + 2, c[1], LANTERN)
    return len(rail)


# ---------------- massing ----------------
DMAT = {
    'market': dict(walls=['white_terracotta', 'smooth_sandstone', 'calcite', 'white_terracotta'], trim='cut_sandstone',
                   roof='waxed_cut_copper', floors=3, styles=['gable', 'gable', 'gable', 'flat']),
    'inns': dict(walls=['cut_sandstone', 'smooth_sandstone', 'birch_planks', 'calcite'], trim='stripped_birch_log',
                 roof='brick', floors=3, styles=['gable', 'gable', 'gable', 'gable', 'flat']),
    'gardens': dict(walls=['calcite', 'smooth_quartz', 'calcite'], trim='quartz_bricks',
                    roof='waxed_oxidized_cut_copper', floors=2, styles=['garden', 'gable']),
    'travellers': dict(walls=['polished_diorite', 'calcite', 'smooth_quartz'], trim='quartz_bricks',
                       roof='prismarine_brick', floors=2, styles=['gable', 'gable', 'flat']),
    'temple': dict(walls=['white_terracotta', 'calcite', 'cherry_planks'], trim='stripped_cherry_log',
                   roof='cherry', floors=2, styles=['gable', 'gable', 'gable', 'garden']),
    'workshops': dict(walls=['calcite', 'polished_diorite', 'calcite', 'smooth_quartz'], trim='purpur_pillar',
                      roof='purpur', floors=2, styles=['saw', 'saw', 'gable']),
}
ROOF_BLOCK = {'waxed_cut_copper': 'waxed_cut_copper', 'brick': 'bricks', 'waxed_oxidized_cut_copper': 'waxed_oxidized_cut_copper',
              'prismarine_brick': 'prismarine_bricks', 'cherry': 'cherry_planks', 'purpur': 'purpur_block'}
STOREY = 4
LOT_TOP = {}
LOT_INFO = {}


def lot_plan():
    """Storeys and roof of every lot. A street wall is one height: the district's storeys, chosen
    once for each street a block faces, one more on the Axis, the boulevards, the plazas and the
    promenade; one lot in five differs by one. Low round the palace, so the palace rules the
    summit. Roofs by district; a dome where a lot turns a plaza corner."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        d = DMAT[lot['district']]
        seed = int(h(lot['id'], 7, 3) * 1000)
        fronts = [N.FRONT[c][3] for c in lot['front'] if c in N.FRONT]
        owner = Counter(('p', N.PLAZA_OF[f]) if f in N.PLAZA_OF else ('o', N.OWNER.get(f)) for f in fronts).most_common(1)
        key = owner[0][0] if owner else ('l', lot['id'])
        street_seed = int(h((key[1] or 0) * 31 + (7 if key[0] == 'p' else 0), len(lot['district']), 5) * 1000)
        floors = d['floors'] + (1 if street_seed % 3 == 0 else 0)
        if lot['prio'] >= 78:
            floors += 1
        if seed % 5 == 0:
            floors += 1 if seed % 2 else -1
        mx = sum(c[0] for c in cs) / len(cs)
        mz = sum(c[1] for c in cs) / len(cs)
        cap = 5 if lot['prio'] >= 95 else 4
        if math.hypot(mx, mz + 44) < 42:
            cap = 3
        if len(cs) < 24:
            cap = min(cap, 3)
        floors = max(2, min(floors, cap))
        kinds = lot['kinds']
        style = d['styles'][seed % len(d['styles'])]
        if kinds.get('plaza', 0) >= 3 and len(kinds) >= 2 and len(cs) >= 30:
            style = 'dome'
        if lot['prio'] >= 90 and style == 'saw':
            style = 'gable'
        LOT_INFO[lot['id']] = dict(floors=floors, style=style, wall=d['walls'][seed % len(d['walls'])], seed=seed)
        LOT_TOP[lot['id']] = lot['pad'] + floors * STOREY


def lot_dist(cs, exterior=False):
    """Distance inside a lot from its edge (exterior=True: only from the edges on the street or
    the court, not from the party walls, so neighbouring roofs join into one ridge)."""
    s = set(cs)
    dist, q = {}, deque()
    for c in cs:
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n not in s and (not exterior or n not in N.LOT):
                dist[c] = 0
                q.append(c)
                break
    if exterior and not q:
        return lot_dist(cs)
    while q:
        c = q.popleft()
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n in s and n not in dist:
                dist[n] = dist[c] + 1
                q.append(n)
    for c in cs:
        dist.setdefault(c, 0)
    return dist


def massing():
    lot_plan()
    count = 0
    for lot in N.LOTS:
        if not lot['cells']:
            continue
        building(lot, LOT_INFO[lot['id']])
        count += 1
    return count


def exposed(c, lid, y):
    """Faces of cell c (in lot lid) open to the air at height y."""
    out = []
    for a, b in N4:
        n = (c[0] + a, c[1] + b)
        if N.LOT.get(n) == lid:
            continue
        if n in N.LOT:
            if LOT_TOP.get(N.LOT[n], -999) < y:
                out.append((a, b))
        elif TOP.get(n, -999) < y:
            out.append((a, b))
    return out


def building(lot, info):
    """One lot, one building: walls on the lot's edge from the pad to the eaves, a band at every
    floor, windows in pairs where a face is open, shop windows on the ground floor of the grand
    streets, a pilaster where two houses meet on the street, an arcade on the Street of Crafts,
    a door on the frontage."""
    lid = lot['id']
    cs = lot['cells']
    s = set(cs)
    pad = lot['pad']
    d = DMAT[lot['district']]
    floors = info['floors']
    top = pad + floors * STOREY
    wallb = B(info['wall'])
    trim = d['trim']
    trimb = B(trim + ('[axis=y]' if trim.endswith(('_log', '_pillar')) else ''))
    band = B(trim if not trim.endswith(('_log', '_pillar')) else 'smooth_quartz')
    glass = B('glass')
    dist = lot_dist(cs)
    grand = lot['prio'] >= 78
    arcade = {}
    if lot['kinds'].get('crafts', 0):
        q = deque()
        for c in cs:
            if N.FRONT.get(c, (0, 0, ''))[2] == 'crafts':
                arcade[c] = 0
                q.append(c)
        while q:
            c = q.popleft()
            if arcade[c] >= 2:
                continue
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in s and n not in arcade:
                    arcade[n] = arcade[c] + 1
                    q.append(n)
    door = None
    fr = [c for c in lot['front'] if c in N.FRONT and c in s]
    if fr:
        mx = sum(c[0] for c in fr) / len(fr)
        mz = sum(c[1] for c in fr) / len(fr)
        door = min(fr, key=lambda c: (abs(N.LEVEL[N.FRONT[c][3]] - pad), math.hypot(c[0] - mx, c[1] - mz)))
    for c in cs:
        x, z = c
        G.set(x, pad, z, B('polished_andesite' if (x + z) % 2 else 'stone_bricks'))
        edge = dist[c] == 0
        party = edge and any(N.LOT.get((x + a, z + b), lid) != lid for a, b in N4)
        arc = arcade.get(c)
        for y in range(pad + 1, top + 1):
            k = (y - pad) % STOREY
            storey = (y - pad - 1) // STOREY
            if arc is not None and storey == 0:
                if y == pad + STOREY:
                    G.set(x, y, z, band if edge or arc < 2 else B('spruce_planks'))
                elif arc == 0 and (x + z) % 3 == 0:
                    G.set(x, y, z, trimb)
                elif arc == 2:
                    G.set(x, y, z, glass if (x + z) % 3 and y < pad + 3 else wallb)
                continue
            if y == top or k == 0:
                G.set(x, y, z, band if edge else B('spruce_planks'))
                continue
            if not edge:
                continue
            ex = exposed(c, lid, y)
            if not ex:
                G.set(x, y, z, wallb)
                continue
            if party:
                G.set(x, y, z, trimb)
                continue
            a, b = ex[0]
            along = z if a else x
            if storey == 0 and grand and along % 4 != 0:
                G.set(x, y, z, glass if k != 1 or lot['district'] == 'market' else wallb)
            elif k in (2, 3) and along % 3 != 0:
                G.set(x, y, z, glass)
            else:
                G.set(x, y, z, wallb)
    if door is not None:
        for y in (pad + 1, pad + 2):
            G.clear(door[0], y, door[1])
    roof(lot, info, cs, top)


def roof(lot, info, cs, top):
    d = DMAT[lot['district']]
    style = info['style']
    mat = d['roof']
    rb = B(ROOF_BLOCK[mat])
    wallb = B(info['wall'])
    s = set(cs)
    if style in ('gable', 'dome'):
        ext = lot_dist(cs, exterior=True)
        depth = max(ext.values())
        cap = 4 if depth >= 5 else 3
        for c in cs:
            r = min(ext[c], cap)
            y = top + 1 + r
            up = None
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in s and min(ext[n], cap) > r:
                    up = (a, b)
                    break
            G.set(c[0], y, c[1], stairs(mat, FACING[up]) if up else rb)
            if r and any((c[0] + a, c[1] + b) not in s for a, b in N4):
                for yy in range(top + 1, y):                       # the gable end on a party wall
                    G.set(c[0], yy, c[1], wallb)
        if style == 'dome':
            dist = lot_dist(cs)
            peak = max(cs, key=lambda c: dist[c])
            R = min(4, dist[peak] + 1)
            yb = top + 1 + min(ext[peak], cap)
            dome(peak[0], peak[1], yb, R, ribs=4)
            G.set(peak[0], yb + R + 1, peak[1], B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    elif style in ('flat', 'garden'):
        dist = lot_dist(cs)
        for c in cs:
            if dist[c] == 0:
                G.set(c[0], top + 1, c[1], wall('diorite' if mat != 'brick' else 'brick', up='true'))
            elif style == 'garden':
                G.set(c[0], top, c[1], B('moss_block' if dist[c] > 1 else 'grass_block'))
                if h(c[0], c[1], 44) < 0.12:
                    G.set(c[0], top + 1, c[1], B(['flowering_azalea', 'azalea', 'allium', 'azure_bluet'][int(h(c[0], c[1], 45) * 4)]))
    elif style == 'saw':
        dist = lot_dist(cs)
        for c in cs:
            k = c[0] % 6
            if k < 4:
                G.set(c[0], top + 1 + k, c[1], stairs(mat, 'east'))
                if dist[c] == 0:
                    for y in range(top + 1, top + 1 + k):
                        G.set(c[0], y, c[1], rb)
            elif k == 4:
                for y in range(top + 1, top + 5):
                    G.set(c[0], y, c[1], B('glass') if dist[c] > 0 else rb)
            elif dist[c] == 0:
                G.set(c[0], top + 1, c[1], rb)
        if len(cs) > 60 and info['seed'] % 3 == 0:
            peak = max(cs, key=lambda c: dist[c])
            for y in range(top + 1, top + 8):
                G.set(peak[0], y, peak[1], B('bricks'))
            G.set(peak[0], top + 8, peak[1], B('campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]'))


# ---------------- landmarks ----------------
def cells_of(k):
    return [c for c, kk in N.CELL.items() if kk == k]


def ring_xz(cx, cz, r0, r1):
    out = []
    ir = int(r1) + 1
    for dx in range(-ir, ir + 1):
        for dz in range(-ir, ir + 1):
            rr = math.hypot(dx, dz)
            if r0 <= rr <= r1:
                out.append((cx + dx, cz + dz, rr, dx, dz))
    return out


def dome(cx, cz, y0, R, shell='waxed_cut_copper', rib='gold_block', ribs=8, glass_band=None, oculus=0):
    for dx in range(-R - 1, R + 2):
        for dz in range(-R - 1, R + 2):
            for dy in range(0, R + 2):
                rr = math.sqrt(dx * dx + dy * dy + dz * dz)
                if not (R - 1.2 <= rr <= R + 0.3):
                    continue
                if oculus and math.hypot(dx, dz) < oculus and dy > 0:
                    continue
                th = math.degrees(math.atan2(dz, dx)) % 360
                is_rib = min(th % (360 / ribs), (360 / ribs) - th % (360 / ribs)) < 360 / ribs / 8 * (8 / max(3, R))
                if glass_band and glass_band[0] <= dy <= glass_band[1] and not is_rib:
                    st = 'yellow_stained_glass'
                else:
                    st = rib if is_rib and dy > 1 else shell
                G.set(cx + dx, y0 + dy, cz + dz, B(st))


def palace():
    """The Palace of the Solstice on its acropolis: a colonnaded front over a grand stair from the
    Plaza Mayor, a three-storey body with corner pavilions, a golden dome on a windowed drum over
    the portal hall, and the sun tower behind it, the highest thing on the island."""
    L = N.PADS['palace']
    cx, cz = 0, -47
    X0, X1, Z0, Z1 = -20, 20, -60, -35
    H = 24
    # body
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            for y in range(L + 1, L + H + 1):
                k = y - L
                if not edge:
                    if k in (6, 12, 18) and math.hypot(x - cx, z - cz) > 11.5:
                        G.set(x, y, z, B('birch_planks'))
                    continue
                along = z if x in (X0, X1) else x
                if k == H:
                    G.set(x, y, z, B('waxed_cut_copper'))
                elif along % 4 == 0 or (x in (X0, X1) and z in (Z0, Z1)):
                    G.set(x, y, z, B('quartz_pillar[axis=y]'))
                elif k % 6 == 0:
                    G.set(x, y, z, B('smooth_quartz'))
                elif k % 6 in (2, 3, 4):
                    G.set(x, y, z, B('yellow_stained_glass' if k % 6 != 4 else 'white_stained_glass'))
                else:
                    G.set(x, y, z, B('calcite'))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if math.hypot(x - cx, z - cz) > 12.5 or x in (X0, X1) or z in (Z0, Z1):
                G.set(x, L + H, z, B('smooth_quartz') if not (x in (X0, X1) or z in (Z0, Z1)) else B('waxed_cut_copper'))
            if x in (X0, X1) or z in (Z0, Z1):
                post = (x % 4 == 0 and z in (Z0, Z1)) or (z % 4 == 0 and x in (X0, X1))
                ew = z in (Z0, Z1)
                G.set(x, L + H + 1, z, wall('diorite', 'low' if ew else 'none', 'none' if ew else 'low',
                                            'none' if ew else 'low', 'low' if ew else 'none', 'true' if post else 'false'))
    # the great door and the portico of paired columns before it
    for x in range(-3, 4):
        for y in range(L + 1, L + 11):
            G.clear(x, y, Z1)
    for x in range(-13, 14, 3):
        for y in range(L + 1, L + 20):
            G.set(x, y, -33, B('quartz_pillar[axis=y]'))
    for x in range(-14, 15):
        for z in range(-34, -31):
            G.set(x, L + 20, z, B('smooth_quartz'))
            G.set(x, L + 21, z, B('waxed_cut_copper'))
    for r in range(0, 8):
        for x in range(-14 + 2 * r, 15 - 2 * r):
            G.set(x, L + 22 + r, -32, B('calcite'))
            G.set(x, L + 22 + r, -34, B('calcite'))
            G.set(x, L + 22 + r, -33, B('waxed_cut_copper' if abs(x) >= 13 - 2 * r else 'calcite'))
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            rr = math.hypot(dx, dy)
            if rr <= 3.3:
                G.set(dx, L + 25 + dy, -31, B('ochre_froglight[axis=y]' if rr < 1.5 else 'gold_block' if rr > 2.5 else 'yellow_stained_glass'))
    # corner pavilions with little domes
    for (px, pz) in ((-20, -35), (20, -35), (-20, -60), (20, -60)):
        for x in range(px - 4, px + 5):
            for z in range(pz - 4, pz + 5):
                edge = abs(x - px) == 4 or abs(z - pz) == 4
                for y in range(L + 1, L + 31):
                    if edge:
                        corner = abs(x - px) == 4 and abs(z - pz) == 4
                        G.set(x, y, z, B('quartz_pillar[axis=y]' if corner else ('yellow_stained_glass' if (y - L) % 6 in (2, 3, 4) and (x + z) % 2 else 'calcite')))
                    elif y == L + 30:
                        G.set(x, y, z, B('smooth_quartz'))
                    elif (y - L) % 6 == 0:
                        G.set(x, y, z, B('birch_planks'))
                    else:
                        G.clear(x, y, z)
        dome(px, pz, L + 31, 4, ribs=4)
        G.set(px, L + 36, pz, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    # drum and dome over the portal hall
    Rd = 12
    for (x, z, rr, dx, dz) in ring_xz(cx, cz, Rd - 0.9, Rd + 0.4):
        th = math.degrees(math.atan2(dz, dx)) % 360
        col = int(th // 10) % 3 == 0
        for y in range(L + H + 1, L + H + 9):
            G.set(x, y, z, B('waxed_cut_copper' if y == L + H + 8 else 'quartz_pillar[axis=y]' if col else
                               'yellow_stained_glass' if (y - L) % 3 else 'white_stained_glass'))
    for (x, z, rr, dx, dz) in ring_xz(cx, cz, 0, 12.4):          # the hall opens into the dome
        for y in range(L + 1, L + H + 1):
            if rr < 11.5 and not (y == L + H and rr > 10.5):
                G.clear(x, y, z)
        G.set(x, L, z, B('gold_block' if 3.4 < rr <= 4.4 else 'yellow_stained_glass' if rr <= 3.4 else
                          'chiseled_quartz_block' if int(rr) % 4 == 0 else 'smooth_quartz'))
    dome(cx, cz, L + H + 9, Rd, glass_band=(3, 5), ribs=12, oculus=2.5)
    top = L + H + 9 + Rd
    for y in range(top - 1, top + 6):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) == 2:
                    G.set(cx + dx, y, cz + dz, B('gold_block'))
                elif (dx, dz) != (0, 0):
                    G.set(cx + dx, y, cz + dz, B('yellow_stained_glass'))
    G.set(cx, top + 6, cz, B('gold_block'))
    G.set(cx, top + 5, cz, B('ochre_froglight[axis=y]'))
    for k in range(8):                                          # the columns round the portal
        th = math.radians(22.5 + 45 * k)
        px, pz = cx + round(8 * math.cos(th)), cz + round(8 * math.sin(th))
        for y in range(L + 1, L + 12):
            G.set(px, y, pz, B('quartz_pillar[axis=y]'))
        G.set(px, L + 12, pz, B('ochre_froglight[axis=y]'))
    # the sun tower behind the dome
    tx, tz = 0, -61
    TH = 80
    for y in range(L + 1, L + TH + 1):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if max(abs(dx), abs(dz)) != 3:
                    if y in (L + 1,):
                        G.set(tx + dx, y - 1, tz + dz, B('smooth_quartz'))
                    continue
                corner = abs(dx) == 3 and abs(dz) == 3
                k = y - L
                if corner:
                    st = 'quartz_pillar[axis=y]'
                elif k % 12 == 0:
                    st = 'waxed_cut_copper'
                elif k > TH - 12 and abs(dx) <= 1 or k > TH - 12 and abs(dz) <= 1:
                    st = 'air' if k < TH - 2 else 'calcite'
                elif (dx == 0 or dz == 0) and k % 12 in (4, 5, 6, 7):
                    st = 'yellow_stained_glass'
                else:
                    st = 'calcite'
                G.set(tx + dx, y, tz + dz, B(st))
    for y in range(L + 1, L + TH):
        G.set(tx, y, tz + 2, B('ladder[facing=north,waterlogged=false]'))
    ty = L + TH
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if max(abs(dx), abs(dz)) <= 4:
                G.set(tx + dx, ty + 1, tz + dz, B('waxed_cut_copper'))
    for k in range(1, 7):                                       # a spire of gold
        r = 4 - (k * 4) // 7
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) == r:
                    G.set(tx + dx, ty + 1 + k, tz + dz, B('gold_block'))
    sy = ty + 16                                                # the frozen sun
    R = 5
    for dx in range(-R - 1, R + 2):
        for dy in range(-R - 1, R + 2):
            for dz in range(-R - 1, R + 2):
                rr = math.sqrt(dx * dx + dy * dy + dz * dz)
                if rr <= R:
                    G.set(tx + dx, sy + dy, tz + dz, B(('ochre_froglight[axis=y]', 'shroomlight', 'glowstone')[(abs(dx) + abs(dy) + abs(dz)) % 3]))
    for y in range(ty + 7, sy - R):
        G.set(tx, y, tz, B('gold_block'))
    for k in range(R + 1, R + 6):
        for (a, b) in N4:
            G.set(tx + a * k, sy, tz + b * k, B('end_rod[facing=%s]' % FACING[(a, b)]))
        G.set(tx, sy + k, tz, B('end_rod[facing=up]'))
    for k in range(R + 1, R + 4):
        for (a, b) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            G.set(tx + a * k, sy + (k if a > 0 else -k) // 2, tz + b * k, B('gold_block'))
    # markers: the portal under the dome, the waystone by it, the mayor's hall in the west wing,
    # the secret garden on the roof behind the dome
    MARKERS.append(('town_hall_portal', (cx, L + 1, cz)))
    MARKERS.append(('town_hall_waystone', (cx + 6, L + 1, cz + 7)))
    for x in range(-18, -13):
        for z in range(-50, -44):
            G.set(x, L, z, B('polished_diorite'))
    MARKERS.append(('mayor', (-15, L + 1, -47)))
    gz = -58
    for x in range(8, 15):
        for z in range(-59, -55):
            G.set(x, L + H, z, B('moss_block'))
            if (x + z) % 3 == 0:
                G.set(x, L + H + 1, z, B(['flowering_azalea', 'azure_bluet', 'allium', 'lily_of_the_valley'][(x * 7 + z) % 4]))
    G.clear(12, L + H + 1, gz)
    G.clear(12, L + H + 2, gz)
    MARKERS.append(('easter:secret_garden', (12, L + H + 1, gz)))
    return top


def max_rect(cells):
    s = set(cells)
    xs = sorted({c[0] for c in cells})
    zs = sorted({c[1] for c in cells})
    best = (0, None)
    for x0 in xs:
        for x1 in xs:
            if x1 < x0 + 10:
                continue
            for z0 in zs:
                z1 = z0
                while all((x, z1) in s for x in range(x0, x1 + 1)):
                    z1 += 1
                z1 -= 1
                if z1 >= z0:
                    a = (x1 - x0 + 1) * (z1 - z0 + 1)
                    if a > best[0]:
                        best = (a, (x0, z0, x1, z1))
    return best[1]


def market_hall():
    """The Great Market of Light: a nave under a glass barrel vault on copper ribs, clerestory of
    glass over a calcite arcade, a sunburst in each end, a glass dome at the crossing."""
    cells = cells_of('market')
    x0, z0, x1, z1 = max_rect(cells)
    L = N.PADS['market']
    for c in cells:
        G.set(c[0], TOP[c], c[1], B('polished_diorite' if (c[0] + c[1]) % 2 else 'calcite'))
    W = 11
    zc = (z0 + z1) / 2
    R = (z1 - z0) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            side = z in (z0, z1)
            end = x in (x0, x1)
            if side or end:
                along = x if side else z
                for y in range(L + 1, L + W + 1):
                    k = y - L
                    pier = along % 4 == 0 or (side and end)
                    if k == W:
                        st = 'waxed_cut_copper'
                    elif pier:
                        st = 'waxed_copper_block' if k > 4 else 'quartz_pillar[axis=y]'
                    elif k <= 4:
                        st = 'calcite' if k == 4 or along % 8 in (1, 7) else 'air'
                    else:
                        st = 'glass'
                    G.set(x, y, z, B(st))
            dz = z - zc
            yv = L + W + int(math.sqrt(max(0.0, R * R - dz * dz)) * 0.75)
            rib = x % 4 == 0
            G.set(x, yv, z, B('waxed_cut_copper' if rib or end else 'glass'))
            if end:
                for y in range(L + W + 1, yv):
                    ray = int((math.degrees(math.atan2(y - L - W, dz)) + 360) // 15) % 2
                    G.set(x, y, z, B('gold_block' if ray else 'yellow_stained_glass'))
    # side aisles' lean-to roofs are part of the vault; a glass dome at the crossing
    mx = (x0 + x1) // 2
    top = L + W + int(R * 0.75)
    dome(mx, round(zc), top - 2, 7, shell='glass', rib='waxed_cut_copper', ribs=8)
    G.set(mx, top + 6, round(zc), B('gold_block'))
    G.set(mx, top + 7, round(zc), B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    # entrances: the west end opens onto the Plaza del Mercado
    for z in range(round(zc) - 2, round(zc) + 3):
        for y in range(L + 1, L + 6):
            G.clear(x0, y, z)
    MARKERS.append(('trading_hall', (mx, L + 1, round(zc))))
    return (x0, z0, x1, z1)


def temple():
    """The Temple of Dawn on its knoll: a gabled nave facing the city, a rose window over the
    door, a bell tower with a spire at the apse end."""
    tx, tz = N.landmark('Templo del Alba')
    L = N.PADS['temple']
    X0, X1, Z0, Z1 = tx - 7, tx + 7, tz - 9, tz + 9
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            for y in range(L + 1, L + 13):
                if edge:
                    along = z if x in (X0, X1) else x
                    k = y - L
                    st = 'quartz_pillar[axis=y]' if along % 3 == 0 else ('pink_stained_glass' if 3 <= k <= 10 else 'white_terracotta')
                    G.set(x, y, z, B(st))
            rr = 8 - abs(x - tx)
            y = L + 13 + min(rr, 7)
            if x - tx:
                G.set(x, y, z, stairs('cherry', 'east' if x < tx else 'west'))
            else:
                G.set(x, y, z, B('cherry_planks'))
            if z in (Z0, Z1):
                for yy in range(L + 13, y):
                    G.set(x, yy, z, B('white_terracotta'))
    for a in range(-4, 5):
        for b in range(-4, 5):
            if a * a + b * b <= 16:
                G.set(tx + a, L + 13 + b, Z1, B(['pink_stained_glass', 'yellow_stained_glass', 'magenta_stained_glass', 'white_stained_glass'][(abs(a) + abs(b)) % 4]))
    for x in range(tx - 1, tx + 2):
        for y in range(L + 1, L + 5):
            G.clear(x, y, Z1)
    for y in range(L + 1, L + 31):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) == 2:
                    k = y - L
                    G.set(tx + dx, y, Z0 - 2 + dz, B('quartz_pillar[axis=y]' if abs(dx) == 2 and abs(dz) == 2 else
                                                     'air' if (k > 24 and (dx == 0 or dz == 0)) else 'white_terracotta'))
    for k in range(0, 9):
        r = 3 - (k * 3) // 9
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) == r:
                    G.set(tx + dx, L + 31 + k, Z0 - 2 + dz, B('purpur_block'))
    G.set(tx, L + 40, Z0 - 2, B('gold_block'))
    G.set(tx, L + 41, Z0 - 2, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    MARKERS.append(('priest', (tx, L + 1, tz)))
    # an approach stair up the knoll from the south
    for c, k in N.CELL.items():
        if k == 'temple_ground' and abs(c[0] - tx) <= 2 and c[1] > Z1:
            APPROACH.add(c)


def workshop():
    """Terra's workshop: a hall under a sawtooth of north lights, two copper stacks, a great gear."""
    cells = cells_of('workshop')
    x0, z0, x1, z1 = max_rect(cells)
    L = N.PADS['workshop']
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                for y in range(L + 1, L + 11):
                    along = x if z in (z0, z1) else z
                    G.set(x, y, z, B('tuff_bricks' if along % 4 == 0 else 'glass' if 3 <= y - L <= 8 else 'calcite'))
            k = (x - x0) % 6
            if k < 4:
                G.set(x, L + 11 + k, z, stairs('purpur', 'east'))
                for y in range(L + 11, L + 11 + k):
                    if edge:
                        G.set(x, y, z, B('purpur_block'))
            else:
                for y in range(L + 11, L + 15):
                    G.set(x, y, z, B('glass' if k == 4 else 'purpur_block') if edge or k == 4 else B('air'))
            G.set(x, L + 10, z, B('waxed_cut_copper') if edge else B('air'))
    for (sx, sz) in ((x0 + 4, z0 + 3), (x1 - 4, z0 + 3)):
        for y in range(L + 11, L + 28):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (dx, dz) != (0, 0):
                        G.set(sx + dx, y, sz + dz, B('waxed_copper_block' if (y - L) % 5 else 'waxed_cut_copper'))
        G.set(sx, L + 28, sz, B('campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]'))
    gx, gz = x0 - 1, (z0 + z1) // 2
    for k in range(40):
        th = 2 * math.pi * k / 40
        for rr in (5.0, 5.8):
            G.set(gx, L + 8 + round(rr * math.sin(th)), gz + round(rr * math.cos(th)), B('gold_block'))
    for k in range(10):
        th = 2 * math.pi * k / 10
        G.set(gx, L + 8 + round(7 * math.sin(th)), gz + round(7 * math.cos(th)), B('gold_block'))
    for y in range(L + 1, L + 5):
        for z in range((z0 + z1) // 2 - 1, (z0 + z1) // 2 + 2):
            G.clear(x0, y, z)
    MARKERS.append(('inventor', ((x0 + x1) // 2, L + 1, (z0 + z1) // 2)))


def palm_house():
    """Juan's botanical garden: a palm house of glass, barrel vault on gold ribs, a dome over the
    great tree."""
    cells = cells_of('greenhouse')
    x0, z0, x1, z1 = max_rect(cells)
    L = N.PADS['greenhouse']
    zc = (z0 + z1) / 2
    R = (z1 - z0) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            G.set(x, L, z, B('moss_block' if (x + z) % 3 else 'grass_block'))
            if x in (x0, x1) or z in (z0, z1):
                for y in range(L + 1, L + 4):
                    G.set(x, y, z, B('calcite' if y == L + 1 else 'glass'))
            dz = z - zc
            yv = L + 4 + int(math.sqrt(max(0.0, R * R - dz * dz)))
            G.set(x, yv, z, B('gold_block' if x % 3 == 0 else 'glass'))
            if x in (x0, x1):
                for y in range(L + 4, yv):
                    G.set(x, y, z, B('glass'))
    mx = (x0 + x1) // 2
    dome(mx, round(zc), L + 4 + int(R) - 2, 6, shell='glass', rib='gold_block', ribs=8)
    tree(mx, round(zc), L, 7, 3.2, 'jungle', 'jungle_log')
    for z in range(round(zc) - 1, round(zc) + 2):
        for y in (L + 1, L + 2, L + 3):
            G.clear(x1, y, z)
    MARKERS.append(('gardener', (mx + 3, L + 1, round(zc))))


def clock_tower():
    cells = cells_of('tower')
    xs = [c[0] for c in cells]
    zs = [c[1] for c in cells]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    L = N.PADS['tower']
    Ht = 38
    for y in range(L + 1, L + Ht + 1):
        k = y - L
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    open_ = k > Ht - 7 and not corner and k < Ht
                    G.set(x, y, z, B('quartz_pillar[axis=y]' if corner else 'waxed_cut_copper' if k % 10 == 0 else
                                     'air' if open_ else 'calcite'))
    for (a, b) in N4:                                           # four clock faces
        fx, fz = cx + a * 4, cz + b * 4
        for u in range(-2, 3):
            for v in range(-2, 3):
                if u * u + v * v <= 6:
                    px = fx if a else cx + u
                    pz = fz if b else cz + u
                    G.set(px, L + 26 + v, pz, B('gold_block' if u * u + v * v > 3 else 'white_concrete' if (u, v) != (0, 0) else 'ochre_froglight[axis=y]'))
    for k in range(0, 12):
        r = 4 - (k * 4) // 12
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if max(abs(x - cx), abs(z - cz)) == r:
                    G.set(x, L + Ht + 1 + k, z, B('waxed_oxidized_cut_copper'))
    G.set(cx, L + Ht + 13, cz, B('gold_block'))
    G.set(cx, L + Ht + 14, cz, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    for y in range(L + 1, L + 4):
        G.clear(cx, y, z1)


def templetes():
    """A small domed templete on the promenade at the end of every radial: every street ends on
    something to look at."""
    for (es, en, x, z, k) in N.LANDMARKS:
        if k != 'mirador':
            continue
        cands = [c for c in N.CELL if kind(c) == 'promenade' and math.hypot(c[0] - x, c[1] - z) < 7]
        if not cands:
            continue
        ctr = min(cands, key=lambda c: math.hypot(c[0] - x * 0.97, c[1] - z * 0.97))
        L = TOP[ctr]
        for (px, pz, rr, dx, dz) in ring_xz(ctr[0], ctr[1], 0, 3.4):
            if (px, pz) in TOP:
                for y in range(TOP[(px, pz)] + 1, L + 1):
                    G.set(px, y, pz, B('calcite'))
                G.set(px, L, pz, B('smooth_quartz' if rr > 1 else 'gold_block'))
        for kk in range(8):
            th = 2 * math.pi * kk / 8
            px, pz = ctr[0] + round(3 * math.cos(th)), ctr[1] + round(3 * math.sin(th))
            for y in range(L + 1, L + 6):
                G.set(px, y, pz, B('quartz_pillar[axis=y]'))
        for (px, pz, rr, dx, dz) in ring_xz(ctr[0], ctr[1], 0, 3.4):
            G.set(px, L + 6, pz, B('smooth_quartz'))
        dome(ctr[0], ctr[1], L + 6, 3, shell='waxed_cut_copper', rib='gold_block', ribs=4)
        G.set(ctr[0], L + 5, ctr[1], LANTERN.replace('hanging=false', 'hanging=true'))
        G.set(ctr[0], L + 10, ctr[1], B('lightning_rod[facing=up,powered=false,waterlogged=false]'))


def falls():
    """The Last Falls: where the east canal leaves the rim, a preview column of falling water."""
    lm = N.landmark('Cascada del Fin')
    if not lm:
        return
    x, z = lm
    cands = [c for c in N.CELL if kind(c) == 'canal' and math.hypot(c[0] - x, c[1] - z) < 5]
    if not cands:
        return
    end = max(cands, key=lambda c: math.hypot(*c))
    top = N.CANAL_LEVEL.get(end, TOP[end] + 2) - 1
    ux, uz = end[0] / math.hypot(*end), end[1] / math.hypot(*end)
    for k in range(1, 4):                                   # past the rim, over the edge
        q = (round(end[0] + ux * k), round(end[1] + uz * k))
        if q not in N.CELL:
            break
    for w in (-1, 0, 1):
        for y in range(top - 70, top + 1):
            EXTRA[(q[0] - round(uz * w), y, q[1] + round(ux * w))] = B('water[level=0]')


# ---------------- plazas ----------------
def plaza_cells(name):
    for (nm, centre, cells, lev) in N.PLAZAS:
        if nm == name:
            return centre, cells, lev
    return None


def fountain(cx, cz, y, R, column=6, top='ochre_froglight[axis=y]'):
    for (x, z, rr, dx, dz) in ring_xz(cx, cz, 0, R + 0.4):
        G.set(x, y + 1, z, B('calcite') if rr > R - 1 else B('water[level=0]'))
        G.set(x, y, z, B('prismarine_bricks') if rr <= R - 1 else G.get(x, y, z))
    for k in range(1, column + 1):
        G.set(cx, y + k, cz, B('quartz_pillar[axis=y]'))
    for (a, b) in N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        G.set(cx + a, y + column, cz + b, B('waxed_cut_copper'))
    G.set(cx, y + column + 1, cz, B('gold_block'))
    G.set(cx, y + column + 2, cz, B(top))


def plaza_decor():
    mayor = plaza_cells('Plaza Mayor')
    if mayor:
        (cx, cz), cells, L = mayor
        fountain(cx, cz, L, 6, 7)
        for k in range(4):
            th = math.radians(45 + 90 * k)
            fountain(cx + round(12 * math.cos(th)), cz + round(12 * math.sin(th)), L, 2, 3, 'lantern[hanging=false,waterlogged=false]')
    portal = plaza_cells('Plaza del Portal')
    if portal:
        (cx, cz), cells, L = portal
        gz = cz - 11
        for sx in (-9, 9):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for y in range(L + 1, L + 15):
                        G.set(cx + sx + dx, y, gz + dz, B('quartz_pillar[axis=y]' if abs(dx) + abs(dz) == 2 else 'calcite'))
                    G.set(cx + sx + dx, L + 15, gz + dz, B('gold_block'))
        for x in range(cx - 10, cx + 11):
            G.set(x, L + 16, gz, B('smooth_quartz'))
            G.set(x, L + 17, gz, B('waxed_cut_copper'))
        for (x, y_, z, rr, dx, dy) in ((cx + dx, L + 20 + dy, gz, math.hypot(dx, dy), dx, dy) for dx in range(-4, 5) for dy in range(-3, 4)):
            if rr <= 3.4:
                G.set(x, y_, z, B('ochre_froglight[axis=y]' if rr < 1.6 else 'gold_block' if rr > 2.6 else 'yellow_stained_glass'))
        for k in range(16):                                      # a sun of gold rays in the floor
            th = 2 * math.pi * k / 16
            for r in range(2, 7 if k % 2 == 0 else 5):
                G.set(cx + round(r * math.cos(th)), L, cz + round(r * math.sin(th)), B('gold_block'))
        G.set(cx, L, cz, B('ochre_froglight[axis=y]'))
        MARKERS.append(('arrival', (cx, L + 1, cz)))
    market = plaza_cells('Plaza del Mercado')
    if market:
        (cx, cz), cells, L = market
        for (a, b) in ((3, 3), (3, -3), (-3, 3), (-3, -3)):
            for y in range(L + 1, L + 6):
                G.set(cx + a, y, cz + b, B('quartz_pillar[axis=y]'))
        for k in range(0, 5):
            r = 4 - k
            for x in range(cx - r, cx + r + 1):
                for z in range(cz - r, cz + r + 1):
                    if max(abs(x - cx), abs(z - cz)) == r:
                        G.set(x, L + 6 + k, z, B('waxed_cut_copper'))
        G.set(cx, L + 11, cz, B('gold_block'))
        cs = set(cells)
        for k in range(12):                                     # market stalls round the square
            if k in (0, 5, 6, 10, 11):
                continue                                        # the ways through: west-east, the crafts street
            th = 2 * math.pi * (k + 0.5) / 12
            sx, sz = cx + round(10 * math.cos(th)), cz + round(10 * math.sin(th))
            if not all((sx + a, sz + b) in cs for a in (-1, 0, 1) for b in (-1, 0, 1)):
                continue
            col = ['orange', 'white', 'yellow', 'white', 'red'][k % 5]
            for dx in (0, 1):
                for dz in (0, 1):
                    G.set(sx + dx, L + 3, sz + dz, B('%s_wool' % col))
            for (dx, dz) in ((0, 0), (1, 1)):
                for y in range(L + 1, L + 3):
                    G.set(sx + dx, y, sz + dz, B('spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]'))
            G.set(sx + 1, L + 1, sz, B('barrel[facing=up,open=false]'))
    fuentes = plaza_cells('Plaza de las Fuentes')
    if fuentes:
        (cx, cz), cells, L = fuentes
        cs = set(cells)
        fountain(cx, cz, L, 4, 4)
        for (a, b) in ((8, 0), (-8, 0), (0, 8), (0, -8)):
            if all((cx + a + u, cz + b + v) in cs for u in (-3, 3) for v in (-3, 3)):
                fountain(cx + a, cz + b, L, 2, 2, 'sea_lantern')
    sundial = plaza_cells('Plaza del Reloj de Sol')
    if sundial:
        (cx, cz), cells, L = sundial
        for (x, z, rr, dx, dz) in ring_xz(cx, cz, 0, 6.4):
            G.set(x, L, z, B('smooth_quartz' if rr < 5.5 else 'cut_sandstone'))
        for k in range(12):
            th = 2 * math.pi * k / 12
            G.set(cx + round(5.8 * math.cos(th)), L + 1, cz + round(5.8 * math.sin(th)), B('gold_block' if k % 3 == 0 else 'chiseled_quartz_block'))
        for k in range(0, 7):                                   # the gnomon leans north
            G.set(cx, L + 1 + k, cz - k // 2 + 1, B('gold_block' if k % 2 else 'quartz_pillar[axis=y]'))
        MARKERS.append(('easter:sundial', (cx + 2, L + 1, cz + 2)))
    viaj = plaza_cells('Plaza de los Viajeros')
    if viaj:
        (cx, cz), cells, L = viaj
        stones = ['gold_block', 'calcite', 'lapis_block', 'calcite', 'polished_andesite', 'calcite', 'lapis_block', 'calcite']
        for k in range(8):
            th = -math.pi / 2 + 2 * math.pi * k / 8
            for r in range(1, 8 if k % 2 == 0 else 5):
                G.set(cx + round(r * math.cos(th)), L, cz + round(r * math.sin(th)), B(stones[k]))
        for y in range(L + 1, L + 9):
            G.set(cx, y, cz, B('quartz_pillar[axis=y]' if y < L + 8 else 'gold_block'))
        G.set(cx, L + 9, cz, LANTERN)
        for (a, b) in N4:
            G.set(cx + a, L + 1, cz + b, B('chiseled_quartz_block'))
    faroles = plaza_cells('Plazoleta de los Faroles')
    if faroles:
        (cx, cz), cells, L = faroles
        for dx in range(-6, 7, 3):
            for dz in range(-6, 7, 3):
                if math.hypot(dx, dz) <= 6.5 and (dx, dz) != (0, 0):
                    lamp(cx + dx, cz + dz, L, 3, 'polished_blackstone')
        tree(cx, cz, L, 6, 3.0, 'cherry', 'cherry_log')
    pan = plaza_cells('Plazoleta del Pan')
    if pan:
        (cx, cz), cells, L = pan
        ox, oz = cx - 3, cz
        for (x, z, rr, dx, dz) in ring_xz(ox, oz, 0, 2.4):
            for y in range(L + 1, L + 3):
                G.set(x, y, z, B('bricks'))
        dome(ox, oz, L + 3, 2, shell='bricks', rib='bricks', ribs=4)
        for y in range(L + 3, L + 8):
            G.set(ox + 1, y, oz, B('bricks'))
        G.set(ox + 1, L + 8, oz, B('campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]'))
        G.set(ox, L + 1, oz - 2, B('air'))
        wx, wz = cx + 4, cz
        for (x, z, rr, dx, dz) in ring_xz(wx, wz, 0, 1.5):
            G.set(x, L + 1, z, B('cobblestone') if rr > 0.5 else B('water[level=0]'))
            G.set(x, L, z, B('cobblestone') if rr > 0.5 else B('water[level=0]'))
        for (a, b) in ((1, 1), (-1, -1)):
            for y in range(L + 2, L + 4):
                G.set(wx + a, y, wz + b, B('spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]'))
        for x in range(wx - 1, wx + 2):
            for z in range(wz - 1, wz + 2):
                G.set(x, L + 4, z, B('spruce_planks'))
        tavern(cx, cz + 3, L)


ROOMS = []


def tavern(cx, cz, L):
    """The secret tavern, a cellar under the Bread Close with a hatch in the corner."""
    x0, x1, z0, z1 = cx - 4, cx + 4, cz - 3, cz + 3
    ROOMS.append((x0, L - 6, z0, x1, L - 1, z1))
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            shell = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            for y in range(L - 7, L):
                if shell or y in (L - 7, L - 1):
                    G.set(x, y, z, B('bricks' if shell else 'spruce_planks'))
                else:
                    G.clear(x, y, z)
    for y in range(L - 6, L):
        G.set(x1, y, z0, B('ladder[facing=south,waterlogged=false]'))
    G.set(x1, L, z0, B('spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]'))
    for x in (cx - 2, cx + 2):
        G.set(x, L - 6, cz, B('barrel[facing=up,open=false]'))
    MARKERS.append(('easter:tavern', (cx, L - 6, cz)))


# ---------------- markers ----------------
SHOP_TYPES = ['bookstore', 'rarities', 'parts', 'seeds', 'smithy', 'apothecary', 'maps', 'minerals',
              'records', 'textiles', 'nursery', 'creatures', 'museum', 'bakery', 'apiary', 'curiosities']


def inside_spot(lot):
    """A floor cell inside the lot, two in from the frontage when it can be."""
    dist = lot_dist(lot['cells'])
    cands = sorted(lot['cells'], key=lambda c: (-min(dist[c], 2), c))
    for c in cands:
        y = lot['pad'] + 1
        if not G.filled(c[0], y, c[1]) and not G.filled(c[0], y + 1, c[1]) and G.filled(c[0], y - 1, c[1]):
            return (c[0], y, c[1])
    return None


def lot_markers():
    live = [l for l in N.LOTS if l['cells']]
    crafts = sorted([l for l in live if l['kinds'].get('crafts')], key=lambda l: (min(c[0] for c in l['cells'])))
    market = sorted([l for l in live if l['kinds'].get('plaza') and l['district'] == 'market'], key=lambda l: l['id'])
    shops = []
    for l in crafts + market + sorted(live, key=lambda l: (-l['prio'], l['id'])):
        if l['district'] in ('market', 'workshops') and l not in shops and len(l['cells']) >= 24:
            shops.append(l)
        if len(shops) == len(SHOP_TYPES):
            break
    used = set()
    for l, t in zip(shops, SHOP_TYPES):
        spot = inside_spot(l)
        if spot:
            MARKERS.append(('shop:' + t, spot))
            used.add(l['id'])
    inns = [l for l in live if l['district'] == 'inns' and l['id'] not in used and len(l['cells']) >= 30]
    inns.sort(key=lambda l: (-l['kinds'].get('plaza', 0), -l['prio'], l['id']))
    chosen = []
    for l in inns:
        c0 = l['cells'][0]
        if all(math.dist(c0, o['cells'][0]) > 30 for o in chosen):
            chosen.append(l)
        if len(chosen) == 4:
            break
    for l in chosen:
        spot = inside_spot(l)
        if spot:
            MARKERS.append(('sidequest:%d_inn' % l['id'], spot))
            used.add(l['id'])
    homes = [l for l in live if l['id'] not in used and len(l['cells']) >= 24]
    homes.sort(key=lambda l: l['id'])
    for l in homes[::8]:
        spot = inside_spot(l)
        if spot:
            MARKERS.append(('resident', spot))


def plot_markers(corners):
    for i, (x0, z0) in enumerate(corners):
        lev = N.PADS['plot%d' % i]
        for x in range(x0, x0 + 16):
            for z in range(z0, z0 + 16):
                border = x in (x0, x0 + 15) or z in (z0, z0 + 15)
                G.set(x, lev, z, B('waxed_cut_copper' if border else 'grass_block'))
                for y in range(lev + 1, lev + 41):
                    G.clear(x, y, z)
        MARKERS.append(('player_plot', (x0, lev + 1, z0)))


def rim_barrier(headroom=64):
    """Solsticio ends where the light does: an invisible barrier round the whole shore, from under
    the island to high over the roofs."""
    n = 0
    for (x, z) in list(N.CELL):
        for a, b in N8:
            c = (x + a, z + b)
            if c in N.CELL:
                continue
            near = [q for q in ((c[0] + u, c[1] + v) for u in (-1, 0, 1) for v in (-1, 0, 1)) if q in N.CELL]
            lo = min(BOTTOM[q] for q in near) - 2
            hi = max(TOP[q] for q in near) + headroom
            for y in range(lo, hi + 1):
                if G.setdefault(c[0], y, c[1], B('barrier')):
                    n += 1
    return n


# ---------------- checks ----------------
def leaks(hollow):
    """Hollow ground open to the air: an empty cell inside a column's hollow whose neighbour is
    neither filled nor hollow ground itself. Only the rows where the neighbouring hollows do not
    overlap need looking at; carved rooms must be sealed by a filled shell."""
    bad = []
    carved = defaultdict(set)
    for (x0, y0, z0, x1, y1, z1) in ROOMS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                carved[(x, z)].update(range(y0, y1 + 1))
    for c, (a, b) in hollow.items():
        x, z = c
        cv = carved.get(c, ())
        for yy in (a - 1, b + 1):
            if not G.filled(x, yy, z) and yy not in cv:
                bad.append((x, yy, z))
        for dx, dz in N4:
            n = (x + dx, z + dz)
            hn = hollow.get(n)
            for y in range(a, b + 1):
                if hn and hn[0] <= y <= hn[1] or y in cv:
                    continue
                if not G.filled(x, y, z) and not G.filled(n[0], y, n[1]):
                    bad.append((x, y, z))
    for (x0, y0, z0, x1, y1, z1) in ROOMS:
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                for y in range(y0 - 1, y1 + 2):
                    inner = x0 <= x <= x1 and z0 <= z <= z1 and y0 <= y <= y1
                    if not inner and not G.filled(x, y, z):
                        hc = hollow.get((x, z))
                        if hc and hc[0] <= y <= hc[1]:
                            bad.append((x, y, z))
    return bad


def marker_check():
    issues = []
    names = Counter(m[0].split(':')[0] for m in MARKERS)
    need = {'arrival': 1, 'town_hall_portal': 1, 'town_hall_waystone': 1, 'trading_hall': 1, 'player_plot': 4,
            'mayor': 1, 'inventor': 1, 'gardener': 1, 'priest': 1, 'shop': 16, 'sidequest': 4, 'easter': 3}
    for k, v in need.items():
        if names.get(k, 0) != v:
            issues.append('%s: %d (want %d)' % (k, names.get(k, 0), v))
    eggs = sorted(m[0] for m in MARKERS if m[0].startswith('easter:'))
    if eggs != ['easter:secret_garden', 'easter:sundial', 'easter:tavern']:
        issues.append('eggs ' + str(eggs))
    shops = sorted(m[0][5:] for m in MARKERS if m[0].startswith('shop:'))
    if shops != sorted(SHOP_TYPES):
        issues.append('shops ' + str(shops))
    for name, (x, y, z) in MARKERS:
        if G.filled(x, y, z) and name != 'town_hall_portal':
            issues.append('%s at %s is inside a block (%s)' % (name, (x, y, z), G.get(x, y, z)))
        below = G.get(x, y - 1, z)
        if name not in ('town_hall_portal',) and (below is None or below.split('[')[0] in ('minecraft:water', 'minecraft:air')):
            issues.append('%s at %s stands on %s' % (name, (x, y, z), below))
        if name != 'player_plot' and G.filled(x, y + 1, z):
            issues.append('%s at %s has no headroom (%s)' % (name, (x, y, z), G.get(x, y + 1, z)))
    for name, (x, y, z) in MARKERS:
        if name == 'player_plot':
            for xx in range(x, x + 16):
                for zz in range(z, z + 16):
                    for yy in range(y, y + 41):
                        if G.filled(xx, yy, zz):
                            issues.append('plot at %s blocked at %s by %s' % ((x, y, z), (xx, yy, zz), G.get(xx, yy, zz)))
                            break
                    else:
                        continue
                    break
    return issues


REGISTRY = os.environ.get('ENTRELUMEN_ITEM_REGISTRY', 'E:/Elias/Codex/Entrelumen-ssd/research/item-registry.json')
NOT_ITEMS = {'minecraft:water', 'minecraft:air', 'minecraft:wall_torch', 'minecraft:lava'}


def registry_check():
    """Every block id against the pack's pinned registry (items; water and friends by hand)."""
    try:
        import json
        items = set(json.load(open(REGISTRY, encoding='utf-8'))['items'])
    except Exception as e:  # noqa: BLE001
        return ['registry not read: %s' % e]
    ids = {st.split('[')[0] for st in G.palette[1:]}
    return sorted(i for i in ids if i not in items and i not in NOT_ITEMS)


# ---------------- build ----------------
def build():
    t0 = time.time()
    G.reset()
    MARKERS.clear()
    ROOMS.clear()
    EXTRA.clear()
    TOP.clear()
    BOTTOM.clear()
    APPROACH.clear()
    corners = N.solve_all()
    N.contain()
    REPORT.append('levels solved %.0f s' % (time.time() - t0))
    ground_tops()
    hollow = fill_ground()
    temple()                            # (its approach stair joins the walking cells)
    surfaces()
    palace()
    market_hall()
    workshop()
    palm_house()
    clock_tower()
    nb = massing()
    templetes()
    plaza_decor()
    planting()
    greenery()
    plot_markers(corners)
    nr = rails()
    lot_markers()
    weirs = contain_water()
    falls()
    nbar = rim_barrier()
    REPORT.append('%d buildings, %d rail blocks, %d weirs, %d barrier blocks' % (nb, nr, weirs, nbar))
    REPORT.append('built %.0f s' % (time.time() - t0))
    return hollow


def relief_stats():
    """How the hill was resolved, in numbers."""
    out = []
    stairs_n = 0
    for cc in N.CELL:
        if walkable(cc):
            st = G.get(cc[0], N.LEVEL[cc] + 1, cc[1]) or ''
            if st.split('[')[0].endswith('_stairs'):
                stairs_n += 1
    out.append('%d stair blocks on the walking network' % stairs_n)
    ax = next(p for p in N.PATHS if p.name == 'axis')
    lv = ax.levels
    flights, landings, i = [], [], 0
    while i < len(lv) - 1:
        j = i
        if lv[i + 1] != lv[i]:
            while j + 1 < len(lv) and lv[j + 1] != lv[j]:
                j += 1
            flights.append(abs(lv[j] - lv[i]))
        else:
            while j + 1 < len(lv) and lv[j + 1] == lv[j]:
                j += 1
            landings.append(j - i + 1)
        i = j if j > i else i + 1
    out.append('the Axis falls %d blocks from the Plaza Mayor (%d) to the Plaza del Portal (%d) in %d flights of %s steps, '
               'with landings of %s blocks' % (max(lv) - min(lv), max(lv), min(lv), len(flights),
                                               '-'.join(map(str, (min(flights), max(flights)))),
                                               '-'.join(map(str, (min(landings), max(landings))))))
    drops = Counter()
    tallest = (0, None)
    for cc, t in TOP.items():
        for a, b in ((1, 0), (0, 1)):
            n = (cc[0] + a, cc[1] + b)
            if n in TOP:
                d = abs(t - TOP[n])
                if d >= 2:
                    drops[min(d, 12)] += 1
                if d > tallest[0] and kind(cc) not in ('lake', 'canal') and kind(n) not in ('lake', 'canal'):
                    tallest = (d, (cc, n, kind(cc), kind(n)))
    out.append('retaining faces between neighbouring columns: %d of 2-3 blocks, %d of 4-7, %d of 8 or more; tallest %d (%s)' % (
        drops[2] + drops[3], sum(drops[k] for k in range(4, 8)), sum(drops[k] for k in range(8, 13)), tallest[0],
        '%s by %s' % (tallest[1][2], tallest[1][3]) if tallest[1] else '-'))
    pads = [l['pad'] for l in N.LOTS if l['cells']]
    out.append('%d lots on %d different pad levels' % (len(pads), len(set(pads))))
    return out


def summary(hollow):
    bars = G.histogram().get(B('barrier'), 0)
    total = G.count()
    x0, y0, z0, x1, y1, z1 = G.bounds()
    print('blocks: %d placed (%d without the barrier)' % (total, total - bars))
    print('footprint: x %d..%d, z %d..%d, y %d..%d -> %d x %d x %d' % (x0, x1, z0, z1, y0, y1, x1 - x0 + 1, z1 - z0 + 1, y1 - y0 + 1))
    lk = leaks(hollow)
    print('hollow ground open to the air:', len(lk), lk[:5])
    st = N.step_report()
    print('walking neighbours 2+ apart:', len(st))
    mi = marker_check()
    print('markers:', len(MARKERS), Counter(m[0].split(':')[0] for m in MARKERS), 'issues:', len(mi))
    for m in mi[:40]:
        print('   ', m)
    print('not in the registry:', registry_check())
    print('palette:', len(G.palette) - 1, 'states; mod blocks:', sorted({s.split(':')[0] for s in G.palette[1:]} - {'minecraft'}))
    for r in REPORT + relief_stats():
        print(r)
    for m in N.LOG:
        print('plan:', m)


if __name__ == '__main__':
    hollow = build()
    summary(hollow)
    if '--render' in sys.argv:
        import render8
        which = sys.argv[sys.argv.index('--render') + 1:] or ['all']
        render8.renders(G, EXTRA, N, OUT, which, MARKERS)
