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

Phase 2 dresses it (dress8: doors, windows, balconies, shopfronts, roofs, interiors from houses.py,
lanterns, benches, planters, courtyard fountains, signposts), details the landmarks and builds the
Last Falls as a real cascade (landmarks8), and exports the template (export8).

    python art/solsticio/city8.py                   # build, check, report
    python art/solsticio/city8.py --render [names]  # plus renders into SOLSTICIO8_OUT
    python art/solsticio/city8.py --export          # write the companion's solsticio/city.nbt
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
from modblocks import palette as mp  # noqa: E402

mp_state = mp.state

OUT = os.environ.get('SOLSTICIO8_OUT', 'E:/Elias/Codex/Entrelumen-ssd/solsticio8/phase2'
                     if os.path.isdir('E:/Elias/Codex/Entrelumen-ssd') else os.path.join(HERE, 'preview', 'solsticio8'))
EXT = 174
YMIN, YMAX = -96, 174
G = Grid(-EXT, EXT, YMIN, YMAX, -EXT, EXT, alloc=False)   # allocated by build()
MARKERS = []
EXTRA = {}               # preview-only voxels (none left: the falls are real water now)
TOP = {}                 # (x, z) -> y of the top ground block
N4, N8 = N.N4, N.N8
REPORT = []
BLOCK_NBT = {}           # (x, y, z) -> block entity data for the export (the signs' text)
FALLS = set()            # (x, z) outside the island the Last Falls use (spill, column, catch basin)
NOT_BARRIER = set()      # columns the shore barrier must leave to the falls
WATER_OK = set()         # water cells that are meant to flow (the falls): never turned into weirs
PLACE_KEYS = {           # plaza and landmark names -> lang key suffix (entrelumen.solsticio.place.*)
    'Plaza Mayor': 'plaza_mayor', 'Plaza del Portal': 'plaza_portal', 'Plaza del Mercado': 'plaza_mercado',
    'Plaza de las Fuentes': 'plaza_fuentes', 'Plaza del Reloj': 'plaza_reloj', 'Plaza de los Viajeros': 'plaza_viajeros',
    'Plaza del Reloj de Sol': 'plaza_reloj_sol', 'Plazoleta de los Faroles': 'plazoleta_faroles',
    'Plazoleta del Pan': 'plazoleta_pan',
}
PLACE_NAMES = {          # key -> (es_es, en_us), also the lang files' source
    'plaza_mayor': ('Plaza Mayor', 'Main Square'), 'plaza_portal': ('Plaza del Portal', 'Portal Square'),
    'plaza_mercado': ('Plaza del Mercado', 'Market Square'), 'plaza_fuentes': ('Plaza de las Fuentes', 'Fountain Square'),
    'plaza_reloj': ('Plaza del Reloj', 'Clock Square'), 'plaza_viajeros': ('Plaza de los Viajeros', "Travellers' Square"),
    'plaza_reloj_sol': ('Plaza del Reloj de Sol', 'Sundial Square'), 'plazoleta_faroles': ('Plazoleta de los Faroles', 'Lantern Close'),
    'plazoleta_pan': ('Plazoleta del Pan', 'Bread Close'), 'palace': ('Palacio del Solsticio', 'Palace of the Solstice'),
    'market': ('Gran Mercado de la Luz', 'Great Market of Light'), 'temple': ('Templo del Alba', 'Temple of Dawn'),
    'workshop': ('Taller de Terra', "Terra's Workshop"), 'botanical': ('Jardín Botánico', 'Botanical Garden'),
    'clock': ('Torre del Reloj', 'Clock Tower'),
}
TRADE_NAMES = {          # key -> (es_es, en_us): the trades on the shopfronts' signs
    'bakery': ('Panadería', 'Bakery'), 'grocer': ('Almacén', 'Grocer'), 'jeweller': ('Joyería', 'Jeweller'),
    'cafe': ('Café', 'Café'), 'inn': ('Posada', 'Inn'), 'tailor': ('Sastrería', 'Tailor'), 'florist': ('Florería', 'Florist'),
    'cartographer': ('Cartografía', 'Cartographer'), 'chandler': ('Velería', 'Chandler'), 'forge': ('Forja', 'Forge'),
    'carpenter': ('Carpintería', 'Carpenter'), 'glazier': ('Vidriería', 'Glazier'),
}
SHOP_KEYS = {'bookstore', 'rarities', 'parts', 'seeds', 'smithy', 'apothecary', 'maps', 'minerals', 'records', 'textiles',
             'nursery', 'creatures', 'museum', 'bakery', 'apiary', 'curiosities'}
SIGN_ROT = {'south': 0, 'west': 4, 'north': 8, 'east': 12}


def text_json(line):
    """A sign line as a JSON text component: '' empty, ('place', key) and ('shop', type)
    translatable (with the Spanish name as fallback), ('text', s) literal."""
    import json
    if not line:
        return '""'
    kind_, v = line
    if kind_ == 'place':
        return json.dumps({'translate': 'entrelumen.solsticio.place.' + v, 'fallback': PLACE_NAMES[v][0]}, ensure_ascii=False)
    if kind_ == 'shop':
        assert v in SHOP_KEYS, v
        return json.dumps({'translate': 'entrelumen.solsticio.shop.' + v}, ensure_ascii=False)
    if kind_ == 'trade':
        return json.dumps({'translate': 'entrelumen.solsticio.trade.' + v, 'fallback': TRADE_NAMES[v][0]}, ensure_ascii=False)
    return json.dumps({'text': v}, ensure_ascii=False)


def sign(x, y, z, facing, lines, wall=True, glow=True, wood='spruce'):
    """A sign with its text: a wall sign on the block behind it (facing away from it) or a
    standing sign; the text goes into BLOCK_NBT for the export."""
    st = B('%s_wall_sign[facing=%s,waterlogged=false]' % (wood, facing)) if wall else \
        B('%s_sign[rotation=%d,waterlogged=false]' % (wood, SIGN_ROT[facing]))
    G.set(x, y, z, st)
    msgs = [text_json(l) for l in (list(lines) + ['', '', '', ''])[:4]]
    side = lambda m: (10, {'messages': (9, (8, m)), 'color': (8, 'black' if not glow else 'yellow'),
                           'has_glowing_text': (1, 1 if glow else 0)})
    BLOCK_NBT[(x, y, z)] = {'id': (8, 'minecraft:sign'), 'front_text': side(msgs), 'back_text': side(['""'] * 4),
                            'is_waxed': (1, 1)}


def B(n):
    return n if ':' in n.split('[')[0] else 'minecraft:' + n


def stairs(mat, facing, half='bottom', shape='straight'):
    return B('%s_stairs[facing=%s,half=%s,shape=%s,waterlogged=false]' % (mat, facing, half, shape))


def wall(mat, e='none', n='none', s='none', w='none', up='true'):
    return B('%s_wall[east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]' % (mat, e, n, s, up, w))


def leaves(kind):
    return B('%s_leaves[distance=1,persistent=true,waterlogged=false]' % kind)


# ---------------- copper and stained glass (the solarpunk pass) ----------------
PATINA = ('', 'exposed_', 'weathered_', 'oxidized_')
CU_FORM = {'cut': 'cut_copper', 'chiseled': 'chiseled_copper', 'grate': 'copper_grate', 'bulb': 'copper_bulb',
           'door': 'copper_door', 'trapdoor': 'copper_trapdoor'}
AMBER, GOLD, TEAL = 'orange_stained_glass', 'yellow_stained_glass', 'cyan_stained_glass'   # the sun's vitrales


def cu(form, stage=0):
    """A waxed copper block name at one of the four patinas (0 fresh, 1 exposed, 2 weathered,
    3 verdigris). Waxed: the city keeps the patina it was built with."""
    stage = max(0, min(3, stage))
    if form == 'block':
        return 'waxed_copper_block' if stage == 0 else 'waxed_%scopper' % PATINA[stage]
    return 'waxed_%s%s' % (PATINA[stage], CU_FORM[form])


def patina(stage, x, z, seed=0, y=0):
    """Copper weathers in patches: most cells keep their stage, some are a step further on, a few
    a step behind."""
    r = h(x * 31 + y, z * 17 - y, 900 + seed)
    if r < 0.26:
        return min(3, stage + 1)
    if r > 0.9:
        return max(0, stage - 1)
    return stage


def cu_slab(stage, half='bottom'):
    return B('%s_slab[type=%s,waterlogged=false]' % (cu('cut', stage), half))


def bulb(x, y, z, stage=0):
    """A lit copper bulb. A bulb only changes on a redstone pulse, so the solarpunk pass keeps every
    power source (detectors, lightning rods) off its faces."""
    G.set(x, y, z, B('%s[lit=true,powered=false]' % cu('bulb', stage)))


ROSE = {2: ['_KKK_', 'KTYTK', 'KYOYK', 'KTYTK', '_KKK_'],
        3: ['__KKK__', '_KTYTK_', 'KTYOYTK', 'KYOOOYK', 'KTYOYTK', '_KTYTK_', '__KKK__']}


def rose(x, y, z, t, R, stage=1, back=None):
    """A rose window of the sun in the wall plane through (x, y, z), t the wall's horizontal
    direction: an amber sun, gold rays, a teal sky, a copper frame. back: the inward direction; the
    cell behind every pane is closed in white, so the colours read lit."""
    n = 0
    for i, row in enumerate(ROSE[R]):
        v = R - i
        for j, ch in enumerate(row):
            if ch == '_':
                continue
            u = j - R
            px, pz = x + u * t[0], z + u * t[1]
            if ch == 'K':
                G.set(px, y + v, pz, B(cu('chiseled', patina(stage, px, pz, 21, y + v))))
            else:
                G.set(px, y + v, pz, B({'T': TEAL, 'Y': GOLD, 'O': AMBER}[ch]))
                if back:
                    G.set(px + back[0], y + v, pz + back[1], B('smooth_quartz'))
            n += 1
    return n


def lancet(v, n, centre=True):
    """The pane in row v (0 at the bottom) of a window n high: teal below, gold above, the amber sun
    at the top of the centre column."""
    if v == n - 1:
        return AMBER if centre else GOLD
    return GOLD if v >= n // 2 else TEAL


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
    HOLLOW.clear()
    HOLLOW.update(hollow)
    return hollow


HOLLOW = {}              # column -> (first, last) hollow y inside the ground (fill_ground)


# ---------------- surfaces ----------------
STAIR_MAT = {'axis': 'smooth_quartz', 'trees': 'smooth_quartz', 'boulevard': 'stone_brick', 'street': 'polished_andesite',
             'lane': 'mud_brick', 'crafts': 'brick', 'promenade': 'polished_diorite', 'plaza': 'polished_diorite',
             'path': 'mossy_stone_brick', 'palace': 'quartz', 'canal': 'polished_andesite', 'temple_ground': 'polished_diorite'}


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
    if k == 'axis':                                  # pale polished stone, calcite bands, gold inlays
        part = axis_part(c)
        if part == 'trees':
            return B('grass_block')
        if abs(x) == 2:
            return B('waxed_cut_copper')
        if abs(x) >= 10:
            return B('polished_diorite')
        if z % 6 == 0:
            return B('gold_block') if abs(x) == 5 else B('calcite')
        return B('smooth_quartz')
    if k == 'boulevard':                             # stone bricks and smooth stone, andesite curbs
        edge = any(cls((x + a, z + b)) != 'boulevard' for a, b in N4)
        if edge:
            return B('polished_andesite')
        return B('stone_bricks' if (x + z) % 4 else 'smooth_stone')
    if k == 'street':                                # andesite, tuff curbs
        edge = any(not walkable((x + a, z + b)) for a, b in N4)
        if edge:
            return B('polished_tuff')
        return B('polished_andesite' if (x // 2 + z // 2) % 3 else 'andesite')
    if k == 'lane':                                  # packed mud, mud bricks and cobbles
        r = (x + 2 * z) % 7
        return B('mud_bricks' if r < 4 else 'packed_mud' if r < 6 else 'cobblestone')
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
    'Plaza del Mercado': ('smooth_sandstone', 'white_terracotta', 'orange_terracotta'),
    'Plaza de las Fuentes': ('calcite', 'prismarine_bricks', 'polished_diorite'),
    'Plaza del Reloj': ('polished_diorite', 'calcite', 'waxed_cut_copper'),
    'Plaza de los Viajeros': ('calcite', 'polished_andesite', 'lapis_block'),
    'Plaza del Reloj de Sol': ('smooth_quartz', 'cut_sandstone', 'gold_block'),
    'Plazoleta de los Faroles': ('polished_andesite', 'calcite', 'polished_andesite'),
    'Plazoleta del Pan': ('smooth_sandstone', 'bricks', 'smooth_sandstone'),
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
            G.set(x, t, z, B('clay' if h(x, z, 5) < 0.6 else 'moss_block'))
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
            if G.gid(x, y, z) != wid or (x, y, z) in WATER_OK:
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
    """A street lamp: a post, a copper bulb (fresh or exposed, the brightest) under a copper cap.
    Not where the post would stand in something (a shop's sign); the cap only where there is room."""
    if any(G.filled(x, y, z) for y in range(y0 + 1, y0 + height + 2)):
        return
    for y in range(y0 + 1, y0 + height + 1):
        G.set(x, y, z, wall(mat))
    bulb(x, y0 + height + 1, z, (x + z) % 2)
    G.setdefault(x, y0 + height + 2, z, cu_slab(patina(1, x, z, 3)))


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
                off, every, tphase, lphase = -1.5, 9, 0, 5
            elif p.cls == 'street' and p.width >= 7:
                off, every, tphase, lphase = 3.0, 9, None, 4
            elif p.cls in ('street', 'crafts'):
                off, every, tphase, lphase = 1.5 if p.cls == 'street' else 3.0, 9, None, 4
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
        if G.filled(x, y + 1, z) or G.get(x, y, z) != B('grass_block'):
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
# roofs: (stair material, block); 'cu:N' is waxed cut copper weathering in patches round patina N.
# styles: gable (and a dome on a plaza corner), glass (a stepped glasshouse roof on copper ribs, a
# garden in the attic under it), flat (a solar array, a conservatory or a roof garden on it),
# garden (moss and flowers), saw (copper sawtooth with north lights and skylights).
DMAT = {
    'market': dict(walls=['smooth_sandstone', 'cut_sandstone', 'bricks', 'sandstone'], trim='chiseled_sandstone',
                   roofs=[('mcwroofs:orange_terracotta_roof', 'orange_terracotta'), ('cu:1', 'cu:1'),
                          ('mcwroofs:orange_terracotta_roof', 'orange_terracotta'), ('cu:2', 'cu:2')],
                   floors=3, styles=['gable', 'gable', 'glass', 'gable', 'flat']),
    'inns': dict(walls=['sandstone', 'cut_sandstone', 'smooth_sandstone', 'sandstone'], trim='stripped_spruce_log',
                 roofs=[('spruce', 'spruce_planks'), ('cu:1', 'cu:1'), ('brick', 'bricks'), ('cu:2', 'cu:2')], floors=3,
                 styles=['gable', 'gable', 'glass', 'gable', 'flat']),
    'gardens': dict(walls=['calcite', 'smooth_quartz', 'calcite', 'quartz_bricks'], trim='quartz_pillar',
                    roofs=[('cu:2', 'cu:2'), ('cu:3', 'cu:3'), ('mossy_stone_brick', 'moss_block')],
                    floors=2, styles=['garden', 'glass', 'gable', 'garden', 'glass']),
    'travellers': dict(walls=['calcite', 'polished_diorite', 'smooth_quartz', 'calcite'], trim='quartz_bricks',
                       roofs=[('cu:3', 'cu:3'), ('prismarine_brick', 'prismarine_bricks'), ('cu:2', 'cu:2')], floors=2,
                       styles=['gable', 'glass', 'gable', 'flat']),
    'temple': dict(walls=['calcite', 'smooth_quartz', 'calcite', 'white_terracotta'], trim='stripped_cherry_log',
                   roofs=[('cherry', 'cherry_planks'), ('cu:1', 'cu:1'), ('cherry', 'cherry_planks'), ('cu:2', 'cu:2')],
                   floors=2, styles=['gable', 'glass', 'gable', 'garden']),
    'workshops': dict(walls=['bricks', 'mud_bricks', 'tuff_bricks', 'bricks'], trim='waxed_cut_copper',
                      roofs=[('cu:1', 'cu:1'), ('cu:0', 'cu:0'), ('cu:2', 'cu:2'), ('cu:1', 'cu:1'),
                             ('deepslate_tile', 'deepslate_tiles'), ('cu:2', 'cu:2')],
                      floors=2, styles=['saw', 'saw', 'gable']),
}
CU_DISTRICT = {'market': 1, 'inns': 1, 'gardens': 2, 'travellers': 3, 'temple': 2, 'workshops': 1}


def roof_at(info, x, z):
    """(stair material, block state) of a lot's roof at (x, z): copper roofs weather in patches."""
    m = info['roof']
    if m.startswith('cu:'):
        s = patina(int(m[3:]), x, z, info['seed'] % 11)
        return cu('cut', s), B(cu('cut', s))
    return m, B(info['roof_block'])


def cu_stage(info):
    """The patina of a lot's copper (cornice, parapet, balconies): its roof's own, or its district's."""
    m = info['roof']
    return int(m[3:]) if m.startswith('cu:') else CU_DISTRICT[info['district']]
STOREY = 4
LOT_TOP = {}
LOT_INFO = {}


def street_key(n):
    """The street (or plaza) a street cell belongs to, as net8 names rows."""
    if n in N.PLAZA_OF:
        return ('z', N.PLAZA_OF[n])
    if n in N.OWNER:
        return ('p', N.OWNER[n])
    return ('k', N.CELL.get(n))


def lot_plan():
    """Storeys and roof of every lot. A row of houses is one height: the district's storeys, chosen
    once for the street the row faces, one more on the Axis, the boulevards, the plazas and the
    promenade; a corner house stands a storey higher. Rows step with their street by the row's
    own increment (net8.row_levels), so the cornices make a clean staircase. Low round the
    palace, so the palace rules the summit. Roofs by district; a dome where a lot turns a plaza
    corner."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        d = DMAT[lot['district']]
        seed = int(h(lot['id'], 7, 3) * 1000)
        key = lot.get('street') or ('l', lot['id'])
        kval = key[1] if isinstance(key[1], int) else sum(map(ord, str(key[1])))
        street_seed = int(h(kval * 31 + (7 if key[0] == 'z' else 0), len(lot['district']), 5) * 1000)
        prio = 100 if key[0] == 'z' else N.PATHS[key[1]].prio if key[0] == 'p' else lot['prio']
        floors = d['floors'] + (1 if street_seed % 3 == 0 else 0)
        if prio >= 78:
            floors += 1
        corner = len({N.OWNER.get(N.FRONT[c][3]) for c in lot['front'] if c in N.FRONT}) >= 2
        if corner:
            floors += 1                                         # corners stand one higher
        mx = sum(c[0] for c in cs) / len(cs)
        mz = sum(c[1] for c in cs) / len(cs)
        cap = 5 if prio >= 95 else 4
        if math.hypot(mx, mz + 44) < 42:
            cap = 3
        if len(cs) < 24:
            cap = min(cap, 3)
        floors = max(2, min(floors, cap + (1 if corner else 0)))
        kinds = lot['kinds']
        style = d['styles'][seed % len(d['styles'])]
        if kinds.get('plaza', 0) >= 3 and len(kinds) >= 2 and len(cs) >= 30:
            style = 'dome'
        if lot['prio'] >= 90 and style == 'saw':
            style = 'gable'
        roof_, roof_block = d['roofs'][street_seed % len(d['roofs'])]
        LOT_INFO[lot['id']] = dict(floors=floors, style=style, wall=d['walls'][seed % len(d['walls'])], seed=seed,
                                   corner=corner, roof=roof_, roof_block=roof_block, district=lot['district'])
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
    fix_entries()
    yards()
    count = 0
    for lot in N.LOTS:
        if not lot['cells']:
            continue
        building(lot, LOT_INFO[lot['id']])
        count += 1
    firewalls()
    bases()
    retaining_walls()
    return count


# ---------------- houses on the slope ----------------
YARD = {}                # garden-terrace cell -> lot id (cut out of the house where the ground rises)
ROOF_Y = {}              # house cell -> y of its roof line (fire walls follow it)
ARCADE_CELLS = set()     # the open fronts of the Calle de los Oficios porticos (open by design)
STAIRWELL = set()        # the head of a stair inside a doorway: its hole in the ground floor
SLOPE = Counter()        # what the houses on the slope got, for the report
SLOPE_SITES = []         # (piece, x, z) for the review renders
PLINTH = ('stone_bricks', 'polished_andesite')      # rusticated courses, alternating
WATER_TABLE = 'smooth_stone'
COPING = {'market': 'smooth_sandstone', 'inns': 'smooth_sandstone', 'gardens': 'smooth_quartz', 'travellers': 'smooth_quartz',
          'temple': 'smooth_quartz', 'workshops': 'brick'}


def yards():
    """Where the ground beside a house rises more than a step above its ground floor (a street, a
    court or garden, a terrace; never a neighbouring house, whose base is masonry), the house
    stands back from it: that strip of the lot becomes a garden terrace at the house's level, and
    the higher ground's face is its retaining wall. Two deep where the house keeps its depth, one
    where it is shallow; never across the house's own front on its street."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        g0 = lot['pad']
        s = set(cs)
        pressed = set()
        for c in cs:
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n not in s and n not in N.LOT and n in TOP and TOP[n] > g0 + 1:
                    pressed.add(c)
        if not pressed:
            continue
        key = lot.get('street')
        allfront = {c for c in lot['front'] if c in N.FRONT and street_key(N.FRONT[c][3]) == key}
        front = allfront - pressed if len(allfront - pressed) >= 2 else allfront   # a pressed corner may go too
        chosen = None
        for depth in (2, 1):
            yard, ring = set(), set(pressed) - front
            for _ in range(depth):
                yard |= ring
                ring = {(c[0] + a, c[1] + b) for c in ring for a, b in N4} & s - yard - front
            rest = house_part(s - yard, front)          # a scrap cut off by the terrace joins it
            yard = s - rest
            if len(rest) >= 12 and (front & rest or not front) and max(lot_dist(sorted(rest)).values()) >= 1:
                chosen = yard
                break
        if not chosen:
            SLOPE['houses against rising ground (no room for a terrace)'] += 1
            continue
        for c in chosen:
            YARD[c] = lot['id']
            del N.LOT[c]
        lot['cells'] = sorted(s - chosen)
        SLOPE_SITES.append(('terrace', min(chosen)[0], min(chosen)[1]))
        SLOPE['garden terraces'] += 1
        SLOPE['garden terrace cells'] += len(chosen)


def entry_for(c, n, pad, s):
    """How a door in the front cell c reaches the street cell n before it, for a house whose floor
    is at pad: ('water',) a quay on a canal; ('flush',) level with the pavement; ('step',) one step
    up; ('stoop', plan) a landing and a flight outside (stoop_plan); ('inside', k) a door at the
    street's level with k steps up inside the doorway, through the house's base, where there is no
    room outside; or None."""
    lev = N.LEVEL[n]
    dz = pad - lev
    if dz < 0 or grade(c, s, pad) is not None:      # never a door in a wall that retains ground
        return None
    if N.CELL.get(n) == 'canal' and n not in N.CULVERT:
        return ('water',)
    if not walkable(n):
        return None
    if dz <= 1:
        return ('flush',) if dz == 0 else ('step',)
    plan = stoop_plan(n, (n[0] - c[0], n[1] - c[1]), lev, pad)
    if plan:
        return ('stoop', plan)
    d_in = (c[0] - n[0], c[1] - n[1])
    run = [(c[0] + d_in[0] * k, c[1] + d_in[1] * k) for k in range(1, dz + 2)]
    t = (d_in[1], d_in[0])
    if all(q in s for q in run) and all((q[0] + t[0], q[1] + t[1]) in s and (q[0] - t[0], q[1] - t[1]) in s for q in run[:-1]) \
            and not any(G.filled(n[0], y, n[1]) for y in (lev + 1, lev + 2)):
        return ('inside', dz)
    return None


def stoop_plan(n, d, lev, pad):
    """The flight down from the landing before a door two or three above its street (n, the cell
    before the door; d, out of the house): along the facade either way, or straight out into the
    street, until the pavement meets a step. Returns (direction, [(cell, stair y or None where
    the pavement takes over)]) or None when there is no room (the landing's own cell included)."""
    if N.CELL.get(n) == 'canal' and n not in N.CULVERT:
        return (d, [])                              # a water door: its landing is a little quay
    wide = n in N.OWNER and N.PATHS[N.OWNER[n]].width >= 4
    for y in range(lev + 1, pad + 3):               # the landing's own cell (a street stair may give way
        st = G.get(n[0], y, n[1])                   # to it where the street is wide enough to pass)
        if st and not (wide and y == lev + 1 and 'stairs' in st):
            return None
    t = (d[1], d[0])
    for step in (t, (-t[0], -t[1]), d):
        flight = []
        for k in range(1, 8):
            q = (n[0] + step[0] * k, n[1] + step[1] * k)
            if q in N.LOT or q in YARD or q not in TOP or not walkable(q):
                break
            if TOP[q] >= pad - k:
                flight.append((q, None))
                break
            if any(G.filled(q[0], y, q[1]) for y in range(TOP[q] + 1, pad - k + 3)):
                break
            flight.append((q, pad - k))
        if flight and flight[-1][1] is None:
            return step, flight
    return None


def house_part(cells, front):
    """The piece of a lot that stays a house once the terraces are cut out: of its connected parts,
    the one with most of its front, then the biggest."""
    cells = set(cells)
    parts = []
    while cells:
        start = min(cells)
        seen, q = {start}, deque([start])
        while q:
            c = q.popleft()
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in cells and n not in seen:
                    seen.add(n)
                    q.append(n)
        parts.append(seen)
        cells -= seen
    return max(parts, key=lambda p: (len(p & front), len(p), min(p))) if parts else set()


def one_piece(cells):
    cells = set(cells)
    start = next(iter(cells))
    seen, q = {start}, deque([start])
    while q:
        c = q.popleft()
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n in cells and n not in seen:
                seen.add(n)
                q.append(n)
    return len(seen) == len(cells)


def firewalls():
    """Where two houses of different heights meet, the taller one's side is a plain fire wall of
    its own stone (building() leaves the pilasters to the street faces), and it rises one course
    through its roof line with a coping on top, so every step of a row reads as meant."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        lid = lot['id']
        top = LOT_TOP[lid]
        wallb = B(LOT_INFO[lid]['wall'])
        cop = B('%s_slab[type=bottom,waterlogged=false]' % COPING[lot['district']])
        n_ = 0
        for c in cs:
            if not any(N.LOT.get((c[0] + a, c[1] + b), lid) != lid and LOT_TOP.get(N.LOT[(c[0] + a, c[1] + b)], 999) < top
                       for a, b in N4):
                continue
            y = ROOF_Y.get(c)
            if y is None:
                continue
            G.set(c[0], y, c[1], wallb)
            G.set(c[0], y + 1, c[1], cop)
            n_ += 1
        if n_:
            SLOPE['fire walls'] += 1
            SLOPE_SITES.append(('firewall', cs[0][0], cs[0][1]))


def retaining_walls():
    """Where the ground beside a house stays higher than its first course (a street or garden it
    had no room to stand back from, a neighbour's garden terrace, a landmark's podium), the house's
    wall is a retaining wall up to that grade: blind rusticated courses, the water table on top,
    no window into the earth; above the grade the facade goes on as built."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        g0, top = lot['pad'], LOT_TOP[lot['id']]
        s = set(cs)
        n_ = 0
        for c in cs:
            g = grade(c, s, g0)
            if g is None:
                continue
            hi = min(g, top - 1)
            for y in range(g0 + 1, hi + 1):
                st = G.get(c[0], y, c[1])
                if st and 'door' in st:                     # (a balcony's door: its other half goes too)
                    G.set(c[0], y + (1 if 'half=lower' in st else -1), c[1], B(PLINTH[y % 2]))
                G.set(c[0], y, c[1], B(WATER_TABLE if y == hi else PLINTH[y % 2]))
            n_ += 1
        if n_:
            SLOPE['houses retaining higher ground'] += 1
            SLOPE['retaining wall faces'] += n_


def fix_entries():
    """A house whose row put its ground floor where no door can reach it from a street it fronts
    moves one step of its row down (or up) to where one can: the lattice of its street stays."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        s = set(cs)
        fr = [c for c in lot['front'] if c in N.FRONT and c in s]
        ok = lambda p: any(0 <= p - N.LEVEL[N.FRONT[c][3]] <= 6 and entry_for(c, N.FRONT[c][3], p, s) for c in fr)
        if not fr or ok(lot['pad']):
            continue
        st = lot.get('step', 2)
        for p in (lot['pad'] - st, lot['pad'] + st, lot['pad'] - 2 * st):
            if not ok(p):
                continue
            old = lot['pad']
            lot['pad'] = p
            for c in cs:
                N.LEVEL[c] = p
                TOP[c] = p
                for y in range(old + 1, p):                 # raised: solid under the new floor
                    G.set(c[0], y, c[1], B('stone'))
            LOT_TOP[lot['id']] = p + LOT_INFO[lot['id']]['floors'] * STOREY
            SLOPE['houses moved a step of their row for their door'] += 1
            break


def grade(c, s, g0):
    """The highest ground outside a house's cell c (not another house; a garden terrace at its
    house's level) where it stands above the cell's first course, else None."""
    best = None
    for a, b in N4:
        n = (c[0] + a, c[1] + b)
        if n in s or n in N.LOT:
            continue
        g = N.LOTS[YARD[n]]['pad'] if n in YARD else TOP.get(n)
        if g is not None and g > g0 + 1 and (best is None or g > best):
            best = g
    return best


def terrace_stairs():
    """A garden terrace below a walkable street or garden gets a stair up to it along its retaining
    wall: the split-level passage from the street down to the houses' gardens."""
    comps, seen = [], set()
    for c0 in sorted(YARD):
        if c0 in seen:
            continue
        comp, q = [], deque([c0])
        seen.add(c0)
        while q:
            c = q.popleft()
            comp.append(c)
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in YARD and n not in seen and YARD[n] == YARD[c0]:
                    seen.add(n)
                    q.append(n)
        comps.append(comp)
    made = 0
    for comp in comps:
        g = N.LOTS[YARD[comp[0]]]['pad']
        cs = set(comp)
        done = False
        for c in sorted(comp):
            for a, b in N4:
                w = (c[0] + a, c[1] + b)
                if w in cs or w in N.LOT or w not in TOP or not walkable(w) or w in N.PLAZA_OF:
                    continue
                k = TOP[w] - g
                if not 2 <= k <= 6:
                    continue
                for t in ((b, a), (-b, -a)):             # the flight runs along the wall, away from c
                    run = [(c[0] + t[0] * j, c[1] + t[1] * j) for j in range(-(k - 1), 1)]
                    if not all(q in cs for q in run):
                        continue
                    if not all((q[0] + a, q[1] + b) in TOP and TOP[(q[0] + a, q[1] + b)] >= g + j + 2
                               for j, q in enumerate(run[:-1])):
                        continue
                    head = [G.get(w[0], y, w[1]) for y in (TOP[w] + 1, TOP[w] + 2)]
                    if head[1] or head[0] and 'stairs' not in head[0]:
                        continue                                # the street's own furniture stands there
                    for j, q in enumerate(run):
                        for y in range(g + 1, g + j + 1):
                            G.set(q[0], y, q[1], B('stone_bricks'))
                        G.set(q[0], g + j + 1, q[1], stairs('stone_brick', FACING[t]))
                        for y in range(g + j + 2, g + j + 5):
                            G.set(q[0], y, q[1], DOORWAY)
                    for y in range(TOP[w] + 1, TOP[w] + 3):     # the rail leaves the stair's head open
                        if not G.filled(w[0], y, w[1]):
                            G.set(w[0], y, w[1], DOORWAY)
                    TERRACE_STAIR.update(run)
                    SLOPE_SITES.append(('terrace stair', c[0], c[1]))
                    made += 1
                    done = True
                    break
                if done:
                    break
            if done:
                break
    SLOPE['terrace stairs'] = made


TERRACE_STAIR = set()


def plinth(lot, c, d, g, g0):
    """The base of a house on one face, from the ground outside (g) up to its floor (g0): rusticated
    courses, the water table at the floor, a band at every storey of a taller base, and lit
    basement windows in every storey of a base three or more high."""
    x, z = c
    along = c[1] if d[0] else c[0]
    for y in range(g + 1, g0 + 1):
        band = y == g0 or (g0 - g >= 7 and (g0 - y) % STOREY == 0)
        G.set(x, y, z, B(WATER_TABLE if band else PLINTH[y % 2]))
    if g0 - g < 3 or along % 4 != 1:
        return
    inner = (x - d[0], z - d[1])
    for wy in range(g0 - 1, g + 1, -STOREY):
        if wy - g < 2:
            continue
        G.set(x, wy, z, B('glass'))
        if inner in N.LOT and N.LOT[inner] == lot['id'] and G.filled(inner[0], wy + 1, inner[1]):
            G.set(inner[0], wy, inner[1], B('lantern[hanging=true,waterlogged=false]'))
        SLOPE['basement windows'] += 1


def bases():
    """Every house stands on a designed base wherever the ground falls away from it: its own masonry,
    level with it, from the ground (or the neighbouring house's roof, where that hides the rest)
    up to its floor (plinth); on a walkable low side four or more below its floor, an arcaded
    loggia under the house at the street's own level (loggias). The island's rim stays rock."""
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        g0 = lot['pad']
        s = set(cs)
        low = defaultdict(list)
        tall = 0
        for c in cs:
            for d in N4:
                n = (c[0] + d[0], c[1] + d[1])
                if n in s:
                    continue
                if n in N.LOT:
                    m = N.LOT[n]
                    g = max(N.LOTS[m]['pad'], LOT_TOP.get(m, -999))
                elif n in TOP:
                    g = TOP[n]
                else:
                    continue
                if g >= g0:
                    continue
                plinth(lot, c, d, g, g0)
                tall = max(tall, g0 - g)
                if g0 - g >= 4 and n not in N.LOT and n not in YARD and walkable(n):
                    low[d].append((c, n, g))
        if tall:
            SLOPE['houses on a base'] += 1
            SLOPE['bases 3 or more high'] += tall >= 3
        for d, faces in sorted(low.items()):
            loggias(lot, s, d, faces, g0)


def loggias(lot, s, d, faces, g0):
    """An arcade in the base of a house on its low side: piers every third cell and at the ends,
    two-cell openings with their arches, a loggia two deep behind them at the street's level, its
    back wall dressed, lanterns hung from the house's floor."""
    t = (abs(d[1]), abs(d[0]))
    faces = sorted(faces, key=lambda f: f[0][0] * t[0] + f[0][1] * t[1])
    runs, run = [], []
    for f in faces:
        if run and (f[0][0] - run[-1][0][0], f[0][1] - run[-1][0][1]) != t:
            runs.append(run)
            run = []
        run.append(f)
    runs.append(run)
    for run in runs:
        if len(run) < 4:
            continue
        made = 0
        bays = []
        for i, (c, n, g) in enumerate(run):
            inner = (c[0] - d[0], c[1] - d[1])
            walls = [(inner[0] - d[0], inner[1] - d[1]), (inner[0] + t[0], inner[1] + t[1]), (inner[0] - t[0], inner[1] - t[1]),
                     (c[0] + t[0], c[1] + t[1]), (c[0] - t[0], c[1] - t[1])]
            if i % 3 and i != len(run) - 1 and inner in s and all(w in s for w in walls):
                bays.append((i, c, inner, g))
        carved = {q for (i, c, inner, g) in bays for q in (c, inner)}
        for (i, c, inner, g) in bays:
            for q in (c, inner):
                for y in range(g + 1, g0):
                    G.clear(q[0], y, q[1])
                G.set(q[0], g, q[1], B('polished_andesite' if (q[0] + q[1]) % 2 else 'stone_bricks'))
                for y in range(g - 2, g):                           # solid under the loggia's floor
                    G.set(q[0], y, q[1], B('stone'))
                hc = HOLLOW.get(q)
                if hc:
                    if hc[0] <= g - 3:
                        HOLLOW[q] = (hc[0], min(hc[1], g - 3))
                    else:
                        del HOLLOW[q]
                for a, b in N4:                                     # its walls: the base's own courses
                    w = (q[0] + a, q[1] + b)
                    if w in s and w not in carved:
                        for y in range(g - 2, g0):
                            G.set(w[0], y, w[1], B(PLINTH[y % 2]))
            k = i % 3                                             # the arch: its haunch against the pier
            G.set(c[0], g0 - 1, c[1], stairs('stone_brick', FACING[(-t[0], -t[1])] if k == 1 else FACING[t], half='top'))
            if k == 1:
                G.set(inner[0], g0 - 1, inner[1], B('lantern[hanging=true,waterlogged=false]'))
            made += 1
        if made:
            SLOPE['loggias'] += 1
            SLOPE['loggia bays'] += made
            SLOPE_SITES.append(('loggia', run[len(run) // 2][0][0], run[len(run) // 2][0][1]))


def yard_gardens():
    """The garden terraces behind the houses: grass and moss at the house's level, a path of mud
    bricks along the house, flowers and azaleas; the higher ground's face is their retaining wall
    (a balustrade crowns it where a street runs along the top)."""
    terrace_stairs()
    for c, lid in sorted(YARD.items()):
        if c in TERRACE_STAIR:
            continue
        g = N.LOTS[lid]['pad']
        x, z = c
        along_house = any(N.LOT.get((x + a, z + b)) == lid for a, b in N4)
        r = h(x, z, 131)
        G.set(x, g, z, B('mud_bricks' if along_house and r < 0.5 else 'grass_block' if r < 0.8 else 'moss_block'))
        if not along_house or r >= 0.5:
            if r < 0.14:
                G.set(x, g + 1, z, B(['allium', 'azure_bluet', 'cornflower', 'oxeye_daisy'][int(r * 100) % 4]))
            elif r < 0.22:
                G.set(x, g + 1, z, B('flowering_azalea' if r < 0.18 else 'azalea'))


DOORWAY = B('structure_void')   # reserved headroom before every door while the city is dressed


def open_doorways():
    """Everything is placed: the reserved doorways become air again."""
    sid = G.index.get(DOORWAY)
    if not sid:
        return 0
    raw = G.data.tobytes()
    pat = sid.to_bytes(2, 'little')
    n, i = 0, raw.find(pat)
    while i != -1:
        if i % 2 == 0:
            G.data[i // 2] = 0
            n += 1
            i = raw.find(pat, i + 2)
        else:
            i = raw.find(pat, i + 1)
    return n


TERRAIN = {'stone', 'dirt', 'grass_block', 'tuff', 'andesite', 'dripstone_block', 'mossy_stone_bricks', 'quartz_bricks',
           'chiseled_quartz_block', 'pointed_dripstone', 'hanging_roots'}
PASSABLE = ('carpet', 'pressure_plate', 'rail', 'flower', 'short_grass')


def building_checks():
    """The rules of the houses on the slope, counted: one ground level each (a flat pad, a floor
    under every cell of it); no ground above a floor and no terrain block inside a house; no wall
    column cut short (a gap) or pressed by ground above its first course; every street door on
    walkable ground at its sill."""
    out = Counter()
    seen_pairs = set()
    for lot in N.LOTS:
        cs = lot['cells']
        if not cs:
            continue
        lid, g0 = lot['id'], lot['pad']
        top = LOT_TOP[lid]
        s = set(cs)
        out['buildings'] += 1
        if all(TOP[c] == g0 and (G.filled(c[0], g0, c[1]) or c in STAIRWELL) for c in cs):
            out['one ground level'] += 1
        dist = lot_dist(cs)
        for c in cs:
            out['ground above the floor'] += TOP[c] > g0
            if dist[c] > 0:
                for y in range(g0 + 1, top):
                    st = G.get(c[0], y, c[1])
                    if st and st.split('[')[0].split(':')[1] in TERRAIN:
                        out['terrain inside houses'] += 1
                continue
            if c not in ARCADE_CELLS and any(not G.filled(c[0], y, c[1]) for y in range(g0 + 1, top + 1)):
                out['wall columns cut short'] += 1
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in s or n in N.LOT or n not in TOP:
                    continue
                g = N.LOTS[YARD[n]]['pad'] if n in YARD else TOP[n]
                out['walls with ground above their first course'] += g > g0 + 1
                out['walls with ground at their first course'] += g == g0 + 1
                if g > g0 + 1 and any('glass' in (G.get(c[0], y, c[1]) or '') or 'door' in (G.get(c[0], y, c[1]) or '')
                                      for y in range(g0 + 1, min(g, top - 1) + 1)):
                    out['windows or doors below the grade'] += 1
        for c in cs:                                  # the row's rhythm with each neighbour on its street
            for a, b in N4:
                m = N.LOT.get((c[0] + a, c[1] + b))
                if m is not None and m > lid and N.LOTS[m].get('row') == lot.get('row') is not None:
                    pair = (lid, m)
                    if pair not in seen_pairs:
                        seen_pairs.add(pair)
                        out['row neighbours'] += 1
                        out['row steps off the rhythm'] += (N.LOTS[m]['pad'] - g0) % lot['step'] != 0
        dr = LOT_INFO[lid].get('door')
        if not dr:
            out['houses without a street door'] += 1
            continue
        n = dr[2]
        sill = LOT_INFO[lid].get('door_y', g0 + 1)
        at, above, below = G.get(n[0], sill, n[1]), G.get(n[0], sill + 1, n[1]), G.get(n[0], sill - 1, n[1])
        clear = lambda st: st is None or any(k in st for k in PASSABLE)
        step = at is not None and ('stairs' in at or 'slab' in at)
        if clear(above) and (step or clear(at) and below is not None and not clear(below)):
            out['doors on walkable ground'] += 1
        else:
            out['doors off walkable ground'] += 1
    return out


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
    cst = cu_stage(info)
    for c in cs:                                   # one rigid volume: nothing of before stays in it
        for y in range(pad + 1, top + 1):
            G.clear(c[0], y, c[1])
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
    key = lot.get('street')
    allfr = [c for c in lot['front'] if c in N.FRONT and c in s]
    side = lambda c: any(N.LOT.get((c[0] + a, c[1] + b), lid) != lid for a, b in N4)
    # on its own street, flush or up a step, a stoop or steps inside the doorway; else on another
    # street it fronts; else a longer stair (a house the water raised); never a door without its way
    for pool, most in (([c for c in allfr if street_key(N.FRONT[c][3]) == key], 3), (allfr, 3), (allfr, 6)):
        ok = [c for c in pool if 0 <= pad - N.LEVEL[N.FRONT[c][3]] <= most]
        if not ok:
            continue
        mx = sum(c[0] for c in pool) / len(pool)
        mz = sum(c[1] for c in pool) / len(pool)
        cands = sorted(ok, key=lambda c: (max(1, pad - N.LEVEL[N.FRONT[c][3]]), side(c), math.hypot(c[0] - mx, c[1] - mz), c))
        for c in cands:
            entry = entry_for(c, N.FRONT[c][3], pad, s)
            if entry:
                door = c
                info['entry'] = entry
                break
        if door:
            break
    if door is None:
        SLOPE['houses whose door has no way down'] += 1
    for c in cs:
        x, z = c
        G.set(x, pad, z, B('polished_andesite' if (x + z) % 2 else 'stone_bricks'))
        edge = dist[c] == 0
        party = edge and any(N.LOT.get((x + a, z + b), lid) != lid for a, b in N4)
        facade = edge and any((x + a, z + b) not in N.LOT and (x + a, z + b) in TOP for a, b in N4)
        arc = arcade.get(c)
        if arc is not None:
            ARCADE_CELLS.add(c)
        step_side = party and any(N.LOT.get((x + a, z + b), lid) != lid and N.LOTS[N.LOT[(x + a, z + b)]]['pad'] != pad
                                  for a, b in N4 if (x + a, z + b) in N.LOT)
        for y in range(pad + 1, top + 1):
            k = (y - pad) % STOREY
            storey = (y - pad - 1) // STOREY
            if arc is not None and storey == 0 and step_side and y < pad + STOREY:
                G.set(x, y, z, trimb if facade else wallb)          # closed where the next portico steps
                continue
            if arc is not None and storey == 0:
                if y == pad + STOREY:
                    G.set(x, y, z, band if edge or arc < 2 else B('spruce_planks'))
                elif arc == 0 and (x + z) % 3 == 0:
                    G.set(x, y, z, trimb)
                elif arc == 2:
                    G.set(x, y, z, glass if (x + z) % 3 and y < pad + 3 else wallb)
                continue
            if y == top and edge:                                       # the copper cornice
                G.set(x, y, z, B(cu('cut', patina(cst, x, z, 5))))
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
            if party:                              # a pilaster on the street; a plain fire wall behind
                G.set(x, y, z, trimb if facade else wallb)
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
        f = N.FRONT[door][3]
        inside = info['entry'][0] == 'inside'
        for y in (pad + 1, pad + 2):               # (a door down in the base leaves a fanlight here)
            G.set(door[0], y, door[1], glass) if inside else G.clear(door[0], y, door[1])
        info['door'] = (door, (f[0] - door[0], f[1] - door[1]), f)
        info['door_y'] = N.LEVEL[f] + 1 if inside else pad + 1
    roof(lot, info, cs, top)


def roof_stair(mat, facing):
    if mat.startswith('mcwroofs:'):
        return mp_state(mat, facing=facing, half='bottom', shape='straight')
    return stairs(mat, facing)


def roof(lot, info, cs, top):
    style = info['style']
    wallb = B(info['wall'])
    s = set(cs)
    cst = cu_stage(info)
    if style in ('gable', 'dome', 'glass'):
        ext = lot_dist(cs, exterior=True)
        depth = max(ext.values())
        cap = 4 if depth >= 5 else 3
        rmax = max(min(ext[c], cap) for c in cs)
        xs, zs = [c[0] for c in cs], [c[1] for c in cs]
        along_x = max(xs) - min(xs) >= max(zs) - min(zs)          # the ridge runs along the longer side
        for c in cs:
            r = min(ext[c], cap)
            y = top + 1 + r
            up = None
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in s and min(ext[n], cap) > r:
                    up = (a, b)
                    break
            ROOF_Y[c] = y
            if style == 'glass':                                       # glass stepped up on copper ribs
                rib = r in (0, rmax) or (c[0] if along_x else c[1]) % 4 == 0
                G.set(c[0], y, c[1], B(cu('cut', patina(cst, c[0], c[1], 7))) if rib else B('glass'))
            else:
                mat, rb = roof_at(info, c[0], c[1])
                G.set(c[0], y, c[1], roof_stair(mat, FACING[up]) if up else rb)
            if r and any((c[0] + a, c[1] + b) not in s for a, b in N4):
                for yy in range(top + 1, y):                       # the gable end on a party wall
                    G.set(c[0], yy, c[1], wallb)
        if style == 'dome':
            dist = lot_dist(cs)
            peak = max(cs, key=lambda c: dist[c])
            R = min(4, dist[peak] + 1)
            yb = top + 1 + min(ext[peak], cap)
            dome(peak[0], peak[1], yb, R, shell=cu('cut', (1, 2, 3)[info['seed'] % 3]), ribs=4)
            G.set(peak[0], yb + R + 1, peak[1], B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    elif style in ('flat', 'garden'):
        dist = lot_dist(cs)
        for c in cs:
            ROOF_Y[c] = top + 1
            if dist[c] == 0:                                           # a copper grate parapet
                G.set(c[0], top + 1, c[1], B('%s[waterlogged=false]' % cu('grate', patina(cst, c[0], c[1], 9))))
            elif style == 'garden':
                G.set(c[0], top, c[1], B('moss_block' if dist[c] > 1 else 'grass_block'))
                if h(c[0], c[1], 44) < 0.12:
                    G.set(c[0], top + 1, c[1], B(['flowering_azalea', 'azalea', 'allium', 'azure_bluet'][int(h(c[0], c[1], 45) * 4)]))
    elif style == 'saw':
        dist = lot_dist(cs)
        copper = info['roof'].startswith('cu:')
        for c in cs:
            k = c[0] % 6
            ROOF_Y[c] = top + 1 + min(k, 3) if k < 5 else top + 1
            mat, rb = roof_at(info, c[0], c[1])
            if k < 4:
                sky = copper and k == 2 and dist[c] > 0 and c[1] % 4 == 1          # a skylight in the slope
                G.set(c[0], top + 1 + k, c[1], B('glass') if sky else roof_stair(mat, 'east'))
                if dist[c] == 0:
                    for y in range(top + 1, top + 1 + k):
                        G.set(c[0], y, c[1], rb)
            elif k == 4:
                for y in range(top + 1, top + 5):
                    G.set(c[0], y, c[1], B('glass') if dist[c] > 0 else rb)
            elif dist[c] == 0:
                G.set(c[0], top + 1, c[1], rb)


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


def choose_inns():
    """The four side-quest inns: big lots of the Inns quarter, on plazas first, far apart."""
    live = [l for l in N.LOTS if l['cells']]
    import dress8
    inns = [l for l in live if l['district'] == 'inns' and len(l['cells']) >= 30 and LOT_INFO[l['id']].get('door')
            and dress8.frame(l, LOT_INFO[l['id']]['door'])[0]]
    inns.sort(key=lambda l: (-l['kinds'].get('plaza', 0), -l['prio'], l['id']))
    chosen = []
    for l in inns:
        c0 = l['cells'][0]
        if all(math.dist(c0, o['cells'][0]) > 30 for o in chosen):
            chosen.append(l)
        if len(chosen) == 4:
            break
    return {l['id']: 'inn' for l in chosen}


def lot_markers(inns):
    used = set()
    for lid in inns:
        spot = inside_spot(N.LOTS[lid])
        if spot:
            MARKERS.append(('sidequest:%d_inn' % lid, spot))
            used.add(lid)
    live = [l for l in N.LOTS if l['cells']]
    homes = [l for l in live if l['id'] not in used and len(l['cells']) >= 24 and LOT_INFO[l['id']].get('door')]
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
            if c in N.CELL or c in NOT_BARRIER:
                continue
            near = [q for q in ((c[0] + u, c[1] + v) for u in (-1, 0, 1) for v in (-1, 0, 1)) if q in N.CELL]
            lo = min(BOTTOM[q] for q in near) - 2
            hi = max(TOP[q] for q in near) + headroom
            for y in range(lo, hi + 1):
                if G.setdefault(c[0], y, c[1], B('barrier')):
                    n += 1
    if FALLS:                                   # round the falls instead of across them
        lo = min(y for (x, y, z) in WATER_OK) - 8
        hi = max(TOP[q] for q in N.CELL if any((q[0] + a, q[1] + b) in FALLS for a, b in N8)) + headroom
        for (x, z) in FALLS:
            for a, b in N8:
                c = (x + a, z + b)
                if c in N.CELL or c in FALLS:
                    continue
                for y in range(lo, hi + 1):
                    if G.setdefault(c[0], y, c[1], B('barrier')):
                        n += 1
            for y in range(lo, lo + 2):          # and under the catch basin
                if G.setdefault(x, y, z, B('barrier')):
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

    def item_of(i):                     # blocks without an item of their own name
        ns, n = i.split(':')
        cands = [i]
        if '_wall_' in n or n.startswith('wall_'):
            cands.append(ns + ':' + n.replace('wall_', ''))
        if n.startswith('potted_'):
            cands.append(ns + ':flower_pot')
        if n.endswith('_stem'):
            cands.append(ns + ':' + n[:-5])
        if n.startswith('cave_vines'):
            cands.append(ns + ':glow_berries')
        if n.endswith('_cauldron'):
            cands.append(ns + ':cauldron')
        return cands
    return sorted(i for i in ids if i not in NOT_ITEMS and not any(c in items for c in item_of(i)))


# ---------------- build ----------------
def build():
    import dress8
    import landmarks8 as LM
    import solar8
    me = sys.modules[__name__]
    LM.bind(me)
    dress8.bind(me)
    solar8.bind(me, dress8)
    solar8.STATS.clear()
    t0 = time.time()
    G.reset()
    for d in (MARKERS, EXTRA, TOP, BOTTOM, BLOCK_NBT, LOT_INFO, LOT_TOP, YARD, ROOF_Y, SLOPE, SLOPE_SITES):
        d.clear()
    ARCADE_CELLS.clear()
    STAIRWELL.clear()
    TERRACE_STAIR.clear()
    for d in (ROOMS, APPROACH, FALLS, NOT_BARRIER, WATER_OK):
        d.clear()
    dress8.STATS.clear()
    dress8.WATER_HOLD.clear()
    corners = N.solve_all()
    N.contain()
    REPORT.append('levels solved %.0f s' % (time.time() - t0))
    ground_tops()
    fill_ground()
    hollow = HOLLOW
    LM.temple()                         # (its approach stair joins the walking cells)
    surfaces()
    LM.palace()
    LM.market_hall()
    LM.workshop()
    LM.palm_house()
    LM.clock_tower()
    nb = massing()
    inns = choose_inns()
    dress8.INNS.clear()
    dress8.INNS.update(inns)
    dress8.dress_lots()
    yard_gardens()
    dress8.interiors(inns)
    LM.templetes()
    plaza_decor()
    planting()
    dress8.street_life()
    solar8.plots(corners)               # the solarpunk pass: glass, stained glass, copper, sun and green
    solar8.inn_vitrales()
    solar8.plaza_gables()
    solar8.axis_arcades()
    solar8.oficios_gallery()
    solar8.court_glasshouses()
    solar8.flat_roofs()
    solar8.glass_attics()
    solar8.finials()
    solar8.living_walls()
    solar8.pilaster_vines()
    solar8.downpipes()
    solar8.windmills()
    solar8.skywalks()
    solar8.canal_edges()
    greenery()
    plot_markers(corners)
    nr = rails()
    lot_markers(inns)
    falls = LM.falls()
    weirs = contain_water()
    nbar = rim_barrier()
    SLOPE['doorways kept clear'] = open_doorways()
    before = SLOPE['retaining wall faces']
    retaining_walls()
    SLOPE['retaining wall faces'] = before
    unsafe = solar8.finish()
    gone = [p for p in BLOCK_NBT if 'sign' not in (G.get(*p) or '')]
    for p in gone:                      # a sign something later stood in (the Portal's gate): no text left behind
        del BLOCK_NBT[p]
    REPORT.append('signs: %d with their text%s' % (len(BLOCK_NBT), ', %d dropped where a later piece stands' % len(gone) if gone else ''))
    REPORT.append('%d buildings, %d rail blocks, %d weirs, %d barrier blocks' % (nb, nr, weirs, nbar))
    REPORT.append('dressing: ' + ', '.join('%s %d' % kv for kv in sorted(dress8.STATS.items())))
    REPORT.append('solarpunk: ' + ', '.join('%s %d' % kv for kv in sorted(solar8.STATS.items())))
    REPORT.append('redstone sources still on a bulb, door, trapdoor or bell: %d %s' % (len(unsafe), unsafe[:5]))
    REPORT.append('the whole city: ' + ', '.join('%s %d' % kv for kv in sorted(solar8.CENSUS.items())))
    REPORT.append('houses on the slope: ' + ', '.join('%s %d' % kv for kv in sorted(SLOPE.items())))
    if falls:
        REPORT.append('the Last Falls: water at %d falls %d blocks into its catch basin at %d' % (
            falls['water'], falls['fall'], falls['ledge']))
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
    bc = building_checks()
    print('houses: %d buildings, %d with one ground level; ground above a floor %d; terrain inside houses %d; '
          'wall columns cut short %d; walls with ground above their first course %d (at it %d); '
          'windows or doors below the grade %d; '
          'street doors on walkable ground %d, off it %d, none %d; row neighbours %d, steps off the rhythm %d' % (
              bc['buildings'], bc['one ground level'], bc['ground above the floor'], bc['terrain inside houses'],
              bc['wall columns cut short'], bc['walls with ground above their first course'],
              bc['walls with ground at their first course'], bc['windows or doors below the grade'], bc['doors on walkable ground'],
              bc['doors off walkable ground'], bc['houses without a street door'], bc['row neighbours'],
              bc['row steps off the rhythm']))
    gravity = sorted(st for st in G.palette[1:] if st.split('[')[0].split(':')[1] in (
        'sand', 'red_sand', 'gravel', 'suspicious_sand', 'suspicious_gravel') or st.split('[')[0].endswith('_concrete_powder'))
    print('gravity blocks:', gravity)
    print('palette:', len(G.palette) - 1, 'states; mod blocks:', sorted({s.split(':')[0] for s in G.palette[1:]} - {'minecraft'}))
    for r in REPORT + relief_stats():
        print(r)
    for m in N.LOG:
        print('plan:', m)
    broken = [k for k, bad in (('one ground level', bc['one ground level'] != bc['buildings']),
                               ('ground above a floor', bc['ground above the floor']),
                               ('terrain inside houses', bc['terrain inside houses']),
                               ('wall columns cut short', bc['wall columns cut short']),
                               ('windows or doors below the grade', bc['windows or doors below the grade']),
                               ('row steps off the rhythm', bc['row steps off the rhythm']),
                               ('doors off walkable ground', bc['doors off walkable ground'] + bc['houses without a street door']),
                               ('hollow ground open to the air', len(lk))) if bad]
    return broken


if __name__ == '__main__':
    hollow = build()
    broken = summary(hollow)
    if broken:
        print('THE HOUSES BREAK THEIR RULES:', ', '.join(broken))
        if '--export' in sys.argv:
            sys.exit('not exported')
    if '--export' in sys.argv:
        import export8
        print('export', export8.export(G, MARKERS, BLOCK_NBT, export8.CITY))
    if '--render' in sys.argv:
        import render8
        which = sys.argv[sys.argv.index('--render') + 1:] or ['all']
        render8.renders(G, EXTRA, N, OUT, which, MARKERS)
