"""Solsticio city generator (draft 2): a D4-symmetric solarpunk capital on a floating island inside the
Entrelumen. Builds a voxel dict {(x,y,z): block_state} of vanilla blocks, previews it (isometric and
top-down, texture-average colours) and exports data/entrelumen/structure/solsticio/city.nbt with the
DATA-structure-block markers of CityLayout (arrival, town_hall_portal, town_hall_waystone,
trading_hall, player_plot, mayor, inventor, gardener, priest).

Symmetry: whole-island passes read only (a, b) = sorted(|x|, |z|), and every placed feature goes
through sym(), which applies the eight transforms of the square and rotates facing/axis states."""
import math, os, sys, gzip, struct, time
from PIL import Image, ImageDraw

R = 52          # island half size
P = 2.6         # superellipse exponent of the island outline (2 = circle, large = square)
BASE = 24       # template y of the first air layer above the plaza floor
V = {}
EXTRA = {}      # preview-only voxels (falling water), never exported

T8 = [(sx, sz, sw) for sw in (False, True) for sx in (1, -1) for sz in (1, -1)]
FACING = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
INV = {v: k for k, v in FACING.items()}

def tf(x, z, t):
    sx, sz, sw = t
    if sw: x, z = z, x
    return sx * x, sz * z

def rot_state(b, t):
    if '[' not in b: return b
    name, props = b[:-1].split('[')
    out = []
    for p in props.split(','):
        k, v = p.split('=')
        if k == 'facing' and v in FACING: v = INV[tf(*FACING[v], t)]
        if k == 'axis' and t[2] and v in 'xz': v = 'z' if v == 'x' else 'x'
        out.append(k + '=' + v)
    return name + '[' + ','.join(out) + ']'

def put(x, y, z, b):
    V[(x, y, z)] = b
def sym(x, y, z, b):
    for t in T8:
        p, q = tf(x, z, t)
        V[(p, y, q)] = rot_state(b, t)
def ab(x, z):
    a, b = abs(x), abs(z)
    return (a, b) if a >= b else (b, a)
def everywhere(r=R):
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            yield x, z

def outline(x, z):
    return ((abs(x) / R) ** P + (abs(z) / R) ** P) ** (1 / P)

B = lambda n: 'minecraft:' + n

# ---------------- island ----------------
def island():
    for x, z in everywhere():
        e = outline(x, z)
        if e > 1: continue
        a, b = ab(x, z)
        depth = int(3 + (1 - e) ** 0.45 * 40 + 6 * (math.cos(a * 0.41) * math.cos(b * 0.41)) ** 2)
        for y in range(BASE - 1 - depth, BASE - 1):
            k = BASE - 1 - y
            if k <= 3: blk = 'dirt'
            elif k % 7 == 0 and k < depth - 3: blk = 'calcite'
            elif k > depth * 0.72: blk = 'deepslate'
            elif k > depth * 0.4: blk = 'tuff'
            else: blk = 'stone'
            put(x, y, z, B(blk))
        put(x, BASE - 1, z, B('grass_block'))
        bottom = BASE - 1 - depth
        if e < 0.8 and (a * 5 + b * 3) % 13 == 0:
            for dy in range(1, 3 + (a + b) % 3):
                put(x, bottom - dy, z, B('hanging_roots') if dy > 1 else B('rooted_dirt'))

# ---------------- plaza, canal ring and paths ----------------
def plaza():
    for x, z in everywhere(26):
        d = math.hypot(x, z); a, b = ab(x, z)
        th = math.degrees(math.atan2(b, a))                   # 0..45 inside the octant
        if d <= 17.5:
            blk = 'calcite'
            if 11 <= d < 12 or 16.5 <= d: blk = 'polished_tuff'
            elif 14 <= d < 15: blk = 'waxed_cut_copper'
            elif d > 12 and (th < 3.5 or th > 41): blk = 'polished_tuff'
            put(x, BASE - 1, z, B(blk))
        elif d < 21.5:
            put(x, BASE - 1, z, B('water')); put(x, BASE - 2, z, B('water')); put(x, BASE - 3, z, B('prismarine_bricks'))
        elif d < 23:
            put(x, BASE - 1, z, B('waxed_oxidized_cut_copper'))
    # railings on the canal's outer lip, open where paths cross
    for x, z in everywhere(26):
        d = math.hypot(x, z); a, b = ab(x, z)
        if 21.5 <= d < 22.6 and b > 3 and a - b > 2:
            put(x, BASE, z, B('waxed_oxidized_copper_grate'))
    # cardinal avenues (bridge + road to the district porch) and diagonal footbridges to the plots
    for w in range(-2, 3):
        for s in range(17, 29):
            sym(w, BASE - 1, s, B('calcite') if abs(w) < 2 else B('waxed_cut_copper'))
    for s in range(12, 23):
        for w in (-1, 0, 1):
            sym(s + w, BASE - 1, s, B('calcite') if w == 0 else B('polished_tuff'))
    for s in (24, 27):
        for side in (-3, 3):
            lamp(side, s)

def lamp(x, z):
    sym(x, BASE, z, B('tuff_brick_wall[up=true]'))
    sym(x, BASE + 1, z, B('tuff_brick_wall[up=true]'))
    sym(x, BASE + 2, z, B('waxed_copper_bulb[lit=true,powered=false]'))

# ---------------- town hall rotunda ----------------
def town_hall():
    for x, z in everywhere(11):
        d = math.hypot(x, z); a, b = ab(x, z)
        th = math.degrees(math.atan2(b, a))
        if d <= 10.5:
            put(x, BASE - 1, z, B('polished_tuff'))
            put(x, BASE, z, B('calcite') if d < 5 or 6.5 <= d else B('waxed_cut_copper'))
            if d < 2.5: put(x, BASE, z, B('waxed_chiseled_copper'))
        if 6.5 <= d < 7.6:                                      # inner wall with tall windows
            for y in range(BASE + 1, BASE + 9):
                win = abs(th - 22.5) < 9 and BASE + 3 <= y <= BASE + 7
                door = b <= 1 and y <= BASE + 4
                if door: V.pop((x, y, z), None)
                else: put(x, y, z, B('glass') if win else B('calcite'))
        if d <= 10.3:                                           # ambulatory roof
            if d >= 6.5: put(x, BASE + 9, z, B('waxed_cut_copper'))
            if 7.8 <= d <= 9.6 and b > 1: put(x, BASE + 10, z, B('daylight_detector'))
        if 5.8 <= d < 7.0:                                      # drum
            for y in range(BASE + 9, BASE + 12):
                put(x, y, z, B('glass') if y == BASE + 10 and abs(th - 22.5) < 12 else B('calcite'))
    for k in range(0, 3):                                       # 16 columns (three per octant, mirrored)
        th = math.radians(k * 22.5)
        cx, cz = round(9.2 * math.cos(th)), round(9.2 * math.sin(th))
        for y in range(BASE + 1, BASE + 8):
            sym(cx, y, cz, B('quartz_pillar[axis=y]'))
        sym(cx, BASE + 8, cz, B('chiseled_quartz_block'))
    # dome: ellipsoid shell, calcite ribs, copper panels aging from the base (oxidized) to the crown
    RH, HD = 7.4, 8.6
    inside = lambda x, y, z: y >= 0 and (x * x + z * z) / RH ** 2 + (y / HD) ** 2 <= 1
    ages = ['oxidized_', 'oxidized_', 'weathered_', 'weathered_', 'exposed_', 'exposed_', '', '', '']
    for x, z in everywhere(8):
        for y in range(0, 9):
            if not inside(x, y, z): continue
            if all(inside(x + dx, y + dy, z + dz) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1))): continue
            a, b = ab(x, z)
            th = math.degrees(math.atan2(b, a)) if a else 0
            if math.hypot(x, z) < 2.2 and y >= 7: blk = 'glass'
            elif th < 6 or th > 39: blk = 'calcite'
            else: blk = 'waxed_' + ages[y] + 'cut_copper'
            put(x, BASE + 12 + y, z, B(blk))
    for y in range(BASE + 21, BASE + 23):
        sym(1, y, 1, B('calcite')); sym(1, y, 0, B('glass'))
    put(0, BASE + 23, 0, B('waxed_cut_copper')); put(0, BASE + 24, 0, B('lightning_rod[facing=up]'))
    # the frozen light machine at the centre; the portal worker replaces it through the marker
    for y in range(BASE + 1, BASE + 3):
        put(0, y, 0, B('waxed_chiseled_copper'))
    put(0, BASE + 3, 0, B('beacon'))
    for (a, b) in ((2, 2),):
        sym(a, BASE + 1, b, B('pearlescent_froglight'))

# ---------------- four district halls: barrel-vault greenhouses ----------------
CZ, HALF_W, HALF_L, SPRING, RISE = 36, 6, 7, 4, 6
def vault_inside(x, y):
    return y >= 0 and (x / (HALF_W + 0.6)) ** 2 + (y / RISE) ** 2 <= 1

def district():
    for x in range(-HALF_W, HALF_W + 1):
        for z in range(CZ - HALF_L, CZ + HALF_L + 1):
            sym(x, BASE - 1, z, B('polished_tuff') if abs(x) != 0 else B('calcite'))
            edge_x, edge_z = abs(x) == HALF_W, abs(z - CZ) == HALF_L
            for y in range(BASE, BASE + SPRING):
                if edge_x or edge_z:
                    sym(x, y, z, B('waxed_cut_copper') if y == BASE else B('calcite'))
            for y in range(0, RISE + 1):                     # vault shell (plus full glass gables)
                if not vault_inside(x, y): continue
                shell = any(not vault_inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1)))
                if shell or edge_z:
                    rib = (z - CZ) % 4 == 0 or edge_z and shell or y == RISE
                    sym(x, BASE + SPRING + y, z, B('waxed_cut_copper') if rib else B('glass'))
    for w in (-1, 0, 1):                                       # porch toward the plaza
        for y in range(BASE, BASE + 3):
            sym(w, y, CZ - HALF_L, B('air'))
    sym(-2, BASE + 3, CZ - HALF_L, B('waxed_copper_bulb[lit=true,powered=false]'))
    interiors()

# Local hall frame: u lateral (mirrored), s radial from the centre; r turns the south hall onto
# east (1), north (2) and west (3). Facing names are in the south hall's frame (north = plaza).
def rot(r, x, z):
    for _ in range(r): x, z = z, -x
    return x, z
def hall(r, u, y, s, b):
    for m in (1, -1):
        def f(vx, vz): return rot(r, m * vx, vz)
        p, q = f(u, s)
        state = b
        if '[' in b:
            name, props = b[:-1].split('[')
            out = []
            for pr in props.split(','):
                k, v = pr.split('=')
                if k == 'facing' and v in FACING: v = INV[f(*FACING[v])]
                if k == 'axis' and r % 2 and v in 'xz': v = 'z' if v == 'x' else 'x'
                out.append(k + '=' + v)
            state = name + '[' + ','.join(out) + ']'
        V[(p, y, q)] = state

def interiors():
    lo, hi = CZ - HALF_L + 1, CZ + HALF_L - 1
    # gardener (Juan): crop beds fed by two rills, composters at the back
    for s in range(lo + 1, hi):
        for u in (2, 3):
            hall(2, u, BASE - 1, s, B('farmland[moisture=7]'))
            hall(2, u, BASE, s, B('wheat[age=7]') if u == 3 else B('carrots[age=7]'))
        hall(2, 4, BASE - 1, s, B('water'))
        hall(2, 5, BASE - 1, s, B('moss_block'))
        hall(2, 5, BASE, s, B('flowering_azalea') if s % 3 == 0 else B('moss_carpet'))
    hall(2, 2, BASE, hi, B('composter')); hall(2, 1, BASE, hi, B('composter'))
    # inventor (Terra): benches of crafters and tables, a caged light engine at the back
    for s in range(lo + 1, hi - 2, 2):
        hall(1, 3, BASE, s, B('crafter'))
        hall(1, 4, BASE, s, B('smithing_table' if s % 4 == 1 else 'cartography_table'))
        hall(1, 5, BASE + 2, s, B('waxed_copper_bulb[lit=true,powered=false]'))
    for u in (0, 1):
        for y in range(BASE, BASE + 3):
            for s in (hi - 2, hi - 1, hi):
                edge = u == 1 or s != hi - 1 or y != BASE + 1
                hall(1, u, y, s, B('waxed_copper_grate') if edge else B('pearlescent_froglight'))
    # priest (Bodhi): birch pews facing the altar, candles on a calcite altar
    for s in range(lo + 1, hi - 2, 2):
        for u in (2, 3, 4):
            hall(3, u, BASE, s, B('stripped_birch_log[axis=x]') if u == 4 else B('birch_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))
    for u in (0, 1, 2):
        hall(3, u, BASE, hi - 1, B('calcite'))
        hall(3, u, BASE + 1, hi - 1, B('candle[candles=3,lit=true,waterlogged=false]') if u != 1 else B('air'))
    hall(3, 0, BASE + 1, hi - 1, B('lodestone'))
    # trading hall: counters with job sites along the walls
    sites = ['barrel[facing=up,open=false]', 'fletching_table', 'cartography_table', 'smithing_table', 'composter']
    for i, s in enumerate(range(lo + 1, hi, 2)):
        hall(0, 5, BASE, s, B(sites[i % len(sites)]))
        hall(0, 3, BASE, s, B('waxed_cut_copper_slab[type=bottom,waterlogged=false]'))

# ---------------- reflecting pools and waterfalls behind the halls ----------------
def pools():
    z0 = CZ + HALF_L + 2
    for x in range(-4, 5):
        for z in range(z0 - 1, R + 1):
            if abs(x) == 4 or z == z0 - 1:
                sym(x, BASE - 1, z, B('waxed_cut_copper')); sym(x, BASE - 2, z, B('calcite'))
            else:
                sym(x, BASE - 1, z, B('water')); sym(x, BASE - 2, z, B('calcite'))
    for x in range(-3, 4):
        for y in range(BASE - 40, BASE - 1):
            for t in T8:
                p, q = tf(x, R + 1, t)
                EXTRA[(p, y, q)] = 'preview:falling_water'

# ---------------- formal gardens between the halls and the plots ----------------
FLOWERS = ['allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet']
def gardens():
    for x, z in everywhere():
        if V.get((x, BASE - 1, z)) != B('grass_block') or (x, BASE, z) in V: continue
        a, b = ab(x, z)
        if 26 <= a <= 44 and 9 <= b <= 19:
            if a in (26, 44) or b in (9, 19):
                put(x, BASE, z, B('flowering_azalea_leaves[persistent=true]'))
            elif (a - 26) % 6 in (2, 3, 4) and 11 <= b <= 17 and b != 14:
                put(x, BASE - 1, z, B('moss_block')); put(x, BASE, z, B(FLOWERS[(a - 26) // 6 % 4]))
            elif b == 14:
                put(x, BASE - 1, z, B('calcite'))
        elif (a * 7 + b * 11) % 17 == 0:
            put(x, BASE, z, B('short_grass'))
    for (tx, tz) in ((9, 27), (18, 22)):
        tree(tx, tz, 7)

def tree(x, z, h=6):
    for y in range(BASE, BASE + h):
        sym(x, y, z, B('stripped_birch_log[axis=y]'))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(0, 3):
                if abs(dx) + abs(dz) + dy <= 3 and (dx or dz or dy):
                    sym(x + dx, BASE + h - 1 + dy, z + dz, B('flowering_azalea_leaves[persistent=true]'))

# ---------------- player plots: four 16x16 in the diagonal corners ----------------
PLOT0 = 22
def plots():
    for x in range(PLOT0, PLOT0 + 16):
        for z in range(PLOT0, PLOT0 + 16):
            for y in range(BASE, BASE + 12):
                for t in T8:
                    p, q = tf(x, z, t); V.pop((p, y, q), None)
            border = x in (PLOT0, PLOT0 + 15) or z in (PLOT0, PLOT0 + 15)
            sym(x, BASE - 1, z, B('waxed_cut_copper') if border else B('grass_block'))
    for (x, z) in ((PLOT0, PLOT0), (PLOT0 + 15, PLOT0), (PLOT0 + 15, PLOT0 + 15)):
        sym(x, BASE, z, B('waxed_copper_bulb[lit=true,powered=false]'))
    V.pop((PLOT0, BASE, PLOT0), None)                          # the corner facing the footbridge stays open
    for t in T8:
        p, q = tf(PLOT0, PLOT0, t); V.pop((p, BASE, q), None)

# ---------------- markers (CityLayout) ----------------
MARKERS = []
def markers():
    MARKERS.clear()
    MARKERS.append(('town_hall_portal', (0, BASE + 1, 0)))
    MARKERS.append(('arrival', (0, BASE + 1, 4)))
    MARKERS.append(('town_hall_waystone', (0, BASE + 1, -4)))
    MARKERS.append(('mayor', (4, BASE + 1, 0)))
    # the four halls: south = trading hall, east = inventor, north = gardener, west = priest
    MARKERS.append(('trading_hall', (0, BASE, CZ)))
    MARKERS.append(('inventor', (CZ, BASE, 0)))
    MARKERS.append(('gardener', (0, BASE, -CZ)))
    MARKERS.append(('priest', (-CZ, BASE, 0)))
    for sx in (1, -1):
        for sz in (1, -1):
            x0 = PLOT0 if sx > 0 else -PLOT0 - 15
            z0 = PLOT0 if sz > 0 else -PLOT0 - 15
            MARKERS.append(('player_plot', (x0, BASE, z0)))

def build():
    V.clear(); EXTRA.clear()
    island(); plaza(); pools(); district(); town_hall(); plots(); gardens(); markers()
    return V

# ---------------- previews ----------------
TEX = 'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/ref/vanilla/assets/minecraft/textures/block/'
SMALL = {'allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet', 'short_grass', 'moss_carpet',
         'lightning_rod', 'hanging_roots', 'tuff_brick_wall', 'daylight_detector', 'wheat', 'carrots', 'candle'}
TEXNAME = {'grass_block': ('grass_block_top', 'grass_block_side'), 'water': ('water_still', 'water_still'),
           'stripped_birch_log': ('stripped_birch_log_top', 'stripped_birch_log'),
           'flowering_azalea': ('flowering_azalea_top', 'flowering_azalea_side'),
           'smooth_quartz': ('quartz_block_bottom',) * 2, 'quartz_pillar': ('quartz_pillar_top', 'quartz_pillar'),
           'chiseled_quartz_block': ('chiseled_quartz_block_top', 'chiseled_quartz_block'),
           'pearlescent_froglight': ('pearlescent_froglight_top', 'pearlescent_froglight_side'),
           'daylight_detector': ('daylight_detector_top', 'daylight_detector_side'),
           'tuff_brick_wall': ('tuff_bricks',) * 2, 'moss_carpet': ('moss_block',) * 2,
           'copper_bulb': ('copper_bulb_lit',) * 2, 'rooted_dirt': ('rooted_dirt',) * 2,
           'beacon': ('beacon',) * 2, 'lightning_rod': ('lightning_rod',) * 2,
           'farmland': ('farmland_moist', 'dirt'), 'wheat': ('wheat_stage7',) * 2, 'carrots': ('carrots_stage3',) * 2,
           'crafter': ('crafter_top', 'crafter_south'), 'smithing_table': ('smithing_table_top', 'smithing_table_front'),
           'cartography_table': ('cartography_table_top', 'cartography_table_side1'),
           'fletching_table': ('fletching_table_top', 'fletching_table_front'),
           'composter': ('composter_top', 'composter_side'), 'barrel': ('barrel_top', 'barrel_side'),
           'birch_stairs': ('birch_planks',) * 2, 'cut_copper_slab': ('cut_copper',) * 2, 'candle': ('candle',) * 2,
           'lodestone': ('lodestone_top', 'lodestone_side')}
COLOR_CACHE = {}
def colours(b):
    name = b.split('[')[0].split(':')[1]
    if name in COLOR_CACHE: return COLOR_CACHE[name]
    if name == 'falling_water': return ((110, 160, 230), (110, 160, 230))
    base = name.replace('waxed_', '')
    out = []
    for t in TEXNAME.get(base, (base, base)):
        try:
            im = Image.open(TEX + t + '.png').convert('RGBA').crop((0, 0, 16, 16))
            px = [p for p in im.get_flattened_data() if p[3] > 0]
            c = tuple(sum(p[i] for p in px) // len(px) for i in range(3))
        except Exception:
            c = (200, 60, 200)
        if base == 'grass_block' and t.endswith('top'): c = (104, 158, 70)
        if base == 'short_grass': c = (88, 140, 60)
        if base == 'water': c = (70, 120, 210)
        if base == 'flowering_azalea_leaves': c = (96, 128, 52)
        out.append(c)
    COLOR_CACHE[name] = out
    return out

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c)

def iso(path, scale=3, crop=None, box=None, ymax=None):
    allv = {k: v for k, v in V.items() if (ymax is None or k[1] <= ymax)
            and (box is None or (box[0] <= k[0] <= box[1] and box[2] <= k[2] <= box[3] and k[1] >= BASE - 2))}
    if box is None: allv.update(EXTRA)
    occ = set(k for k, v in allv.items() if not v.endswith(':air') and v.split('[')[0].split(':')[1] not in SMALL)
    vis = []
    for (x, y, z), bl in allv.items():
        if bl.endswith(':air'): continue
        if crop and not (crop[0] <= x <= crop[1] and crop[0] <= z <= crop[1] and y >= BASE - 4): continue
        if all(n in occ for n in ((x, y + 1, z), (x + 1, y, z), (x, y, z + 1))): continue
        vis.append(((x, y, z), bl))
    s2 = 2 * scale
    proj = lambda x, y, z: ((x - z) * s2, (x + z) * scale - y * s2)
    pts = [proj(*p) for p, _ in vis]
    minu, maxu = min(p[0] for p in pts), max(p[0] for p in pts)
    minv, maxv = min(p[1] for p in pts), max(p[1] for p in pts)
    W, H = int(maxu - minu) + 4 * s2 + 40, int(maxv - minv) + 4 * s2 + 40
    ox, oy = -minu + 20 + s2, -minv + 20
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    for j in range(H):                                           # warm sky gradient
        k = j / H
        d.line([(0, j), (W, j)], fill=(int(252 - 40 * k), int(236 - 20 * k), int(200 + 30 * k)))
    for (x, y, z), bl in sorted(vis, key=lambda t: (t[0][0] + t[0][2] + t[0][1], t[0][1])):
        top, side = colours(bl)
        name = bl.split('[')[0].split(':')[1]
        u, v = proj(x, y, z); u += ox; v += oy
        if name in SMALL:
            r = scale * (0.9 if name != 'daylight_detector' else 1.6)
            cy = v + s2 + (scale if name == 'daylight_detector' else 0)
            d.polygon([(u, cy - r), (u + 2 * r, cy), (u, cy + r), (u - 2 * r, cy)], fill=top)
            if name in ('allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet', 'short_grass'):
                d.line([(u, cy), (u, cy + scale * 1.5)], fill=(70, 110, 50))
            continue
        T = [(u, v), (u + s2, v + scale), (u, v + s2), (u - s2, v + scale)]
        L = [(u - s2, v + scale), (u, v + s2), (u, v + 2 * s2), (u - s2, v + scale + s2)]
        Rr = [(u, v + s2), (u + s2, v + scale), (u + s2, v + scale + s2), (u, v + 2 * s2)]
        d.polygon(L, fill=shade(side, .8)); d.polygon(Rr, fill=shade(side, .64)); d.polygon(T, fill=top)
    im.save(path)
    return len(vis)

def topdown(path, px=6):
    cols = {}
    for (x, y, z), bl in V.items():
        if bl.endswith(':air') or 'hanging_roots' in bl: continue
        if (x, z) not in cols or y > cols[(x, z)][0]: cols[(x, z)] = (y, bl)
    n = 2 * R + 1
    im = Image.new('RGB', (n * px, n * px), (40, 46, 70)); d = ImageDraw.Draw(im)
    for (x, z), (y, bl) in cols.items():
        c = shade(colours(bl)[0], 0.75 + 0.02 * (y - BASE + 1))
        X, Z = (x + R) * px, (z + R) * px
        d.rectangle([X, Z, X + px - 1, Z + px - 1], fill=c)
    for name, (x, y, z) in MARKERS:
        X, Z = (x + R) * px, (z + R) * px
        d.ellipse([X - 4, Z - 4, X + px + 3, Z + px + 3], outline=(255, 40, 120), width=2)
    im.save(path)

# ---------------- NBT export ----------------
DATA_VERSION = 3955   # 1.21.1
def _nbt(tag, name, value):
    out = bytearray([tag]) + struct.pack('>H', len(name.encode())) + name.encode()
    return out + _payload(tag, value)
def _payload(tag, v):
    if tag == 3: return struct.pack('>i', v)
    if tag == 8: return struct.pack('>H', len(v.encode())) + v.encode()
    if tag == 10:
        body = bytearray()
        for k, (t, val) in v.items(): body += _nbt(t, k, val)
        return body + b'\x00'
    if tag == 9:
        t, items = v
        body = bytearray([t if items else 0]) + struct.pack('>i', len(items))
        for it in items: body += _payload(t, it)
        return body
    raise ValueError(tag)

def export(path):
    ys = [p[1] for p in V]
    y0 = min(ys) - 1
    sx = sz = 2 * R + 1
    sy = max(ys) - y0 + 1
    palette, index, blocks = [], {}, []
    def state(b):
        if b not in index:
            name, props = (b[:-1].split('[') if '[' in b else (b, ''))
            entry = {'Name': (8, name)}
            if props:
                entry['Properties'] = (10, {k: (8, v) for k, v in (p.split('=') for p in props.split(','))})
            index[b] = len(palette); palette.append(entry)
        return index[b]
    for (x, y, z), b in sorted(V.items(), key=lambda t: (t[0][1], t[0][2], t[0][0])):
        if b.endswith(':air'): continue
        blocks.append({'pos': (9, (3, [x + R, y - y0, z + R])), 'state': (3, state(b))})
    for name, (x, y, z) in MARKERS:
        blocks.append({'pos': (9, (3, [x + R, y - y0, z + R])), 'state': (3, state('minecraft:structure_block[mode=data]')),
                       'nbt': (10, {'id': (8, 'minecraft:structure_block'), 'mode': (8, 'DATA'), 'metadata': (8, name)})})
    root = {'DataVersion': (3, DATA_VERSION), 'size': (9, (3, [sx, sy, sz])),
            'palette': (9, (10, palette)), 'blocks': (9, (10, blocks)), 'entities': (9, (10, []))}
    with gzip.open(path, 'wb') as f:
        f.write(_nbt(10, '', root))
    return (sx, sy, sz), len(blocks), len(palette), y0

if __name__ == '__main__':
    build()
    print(len(V), 'blocks')
    t = time.time(); n = iso('solsticio-preview.png'); print(n, 'visible voxels', round(time.time() - t, 1), 's')
    iso('solsticio-center.png', scale=6, crop=(-24, 24))
    topdown('solsticio-map.png')
    for r in range(4):
        x0, z0 = rot(r, -8, CZ - 8); x1, z1 = rot(r, 8, CZ + 8)
        iso(f'solsticio-hall{r}.png', scale=6, box=(min(x0, x1), max(x0, x1), min(z0, z1), max(z0, z1)), ymax=BASE + 3)
    if '--export' in sys.argv:
        out = sys.argv[sys.argv.index('--export') + 1]
        print('export', export(out))
