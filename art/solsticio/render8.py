"""Review renders for Solsticio v8 from its Grid (local only: textures of the pack are not ours).

  - iso(): voxrender's textured isometric look (same sprites) straight from the grid, with a
    crop box and a quarter-turn so any side can face the camera;
  - topdown(): a plan view of the built city, district colours over the roofs, labels;
  - street(): a street-level perspective, ray-marched through the grid with the block textures,
    noon sun and shadows, glass and water see-through, fog into the sky. Rows are split over
    processes that share the grid through shared memory.
"""
import math
import os
import sys
import time
from array import array

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import voxrender as VR  # noqa: E402
from voxkit import TEXNAME  # noqa: E402

# textures voxrender does not guess from the name
TEXNAME.update({
    'smooth_sandstone': ('sandstone_top',) * 2, 'smooth_red_sandstone': ('red_sandstone_top',) * 2,
    'smooth_sandstone_stairs': ('sandstone_top',) * 2, 'smooth_quartz_stairs': ('quartz_block_bottom',) * 2,
    'quartz_stairs': ('quartz_block_side',) * 2, 'polished_diorite_stairs': ('polished_diorite',) * 2,
    'mud_brick_stairs': ('mud_bricks',) * 2, 'brick_stairs': ('bricks',) * 2, 'bricks': ('bricks',) * 2,
    'mossy_stone_brick_stairs': ('mossy_stone_bricks',) * 2, 'waxed_cut_copper_stairs': ('cut_copper',) * 2,
    'waxed_oxidized_cut_copper_stairs': ('oxidized_cut_copper',) * 2, 'prismarine_brick_stairs': ('prismarine_bricks',) * 2,
    'cherry_stairs': ('cherry_planks',) * 2, 'purpur_stairs': ('purpur_block',) * 2,
    'ochre_froglight': ('ochre_froglight_top', 'ochre_froglight_side'), 'dirt_path': ('dirt_path_top', 'dirt_path_side'),
    'grass_block': ('grass_block_top', 'grass_block_side'), 'waxed_copper_block': ('copper_block',) * 2,
    'diorite_wall': ('diorite',) * 2, 'andesite_wall': ('andesite',) * 2, 'tuff_brick_wall': ('tuff_bricks',) * 2,
    'polished_blackstone_wall': ('polished_blackstone',) * 2, 'brick_wall': ('bricks',) * 2,
    'cherry_log': ('cherry_log_top', 'cherry_log'), 'stripped_cherry_log': ('stripped_cherry_log_top', 'stripped_cherry_log'),
    'jungle_log': ('jungle_log_top', 'jungle_log'), 'oak_log': ('oak_log_top', 'oak_log'), 'birch_log': ('birch_log_top', 'birch_log'),
    'hay_block': ('hay_block_top', 'hay_block_side'), 'barrel': ('barrel_top', 'barrel_side'),
    'campfire': ('campfire_log_lit', 'campfire_log_lit'), 'white_concrete': ('white_concrete',) * 2,
    'lapis_block': ('lapis_block',) * 2, 'sea_lantern': ('sea_lantern',) * 2, 'glowstone': ('glowstone',) * 2,
    'shroomlight': ('shroomlight',) * 2, 'gold_block': ('gold_block',) * 2, 'packed_mud': ('packed_mud',) * 2,
    'mud_bricks': ('mud_bricks',) * 2, 'spruce_trapdoor': ('spruce_trapdoor',) * 2,
    'pointed_dripstone': ('pointed_dripstone_down_tip',) * 2, 'hanging_roots': ('hanging_roots',) * 2,
    'dripstone_block': ('dripstone_block',) * 2, 'clay': ('clay',) * 2, 'sand': ('sand',) * 2,
    'spruce_fence': ('spruce_planks',) * 2, 'white_wool': ('white_wool',) * 2,
})
VR.TINTED.update({'cherry_leaves': (255, 255, 255), 'flowering_azalea_leaves': (255, 255, 255), 'azalea_leaves': (255, 255, 255),
                  'jungle_leaves': (80, 160, 50)})
SKIP = {'minecraft:barrier'}
EMIT = {'ochre_froglight', 'pearlescent_froglight', 'verdant_froglight', 'sea_lantern', 'glowstone', 'shroomlight', 'lantern',
        'waxed_copper_bulb', 'end_rod', 'campfire', 'soul_lantern'}
SEE = {'glass', 'water', 'yellow_stained_glass', 'white_stained_glass', 'pink_stained_glass', 'magenta_stained_glass',
       'light_blue_stained_glass', 'orange_stained_glass'}
PLANTS = VR.CROSS | {'short_grass', 'azure_bluet', 'allium', 'cornflower', 'oxeye_daisy', 'lily_of_the_valley', 'flowering_azalea',
                     'azalea', 'pointed_dripstone', 'hanging_roots', 'lightning_rod'}


def name(st):
    return st.split('[')[0].split(':')[1]


# ---------------- isometric ----------------
def iso(G, path, scale=2, box=None, turn=0, extra=None, sky=((252, 238, 208), (200, 218, 240)), flip=False):
    """voxrender's look from the grid. box = (x0, y0, z0, x1, y1, z1) crops; turn rotates the city
    a quarter at a time so the camera (always from +x, +z, above) sees another side; flip turns
    the island upside down, to look at its underside."""
    x0, y0, z0, x1, y1, z1 = box or (G.x0, G.y0, G.z0, G.x1, G.y1, G.z1)
    pal = G.palette
    skip = {i for i, s in enumerate(pal) if s in SKIP}
    thin = set()
    for i, s in enumerate(pal):
        if not s:
            continue
        n = name(s)
        if n in VR.CROSS or n.startswith('potted_') or n in VR.THIN or n in VR.SEE_THROUGH or \
                n.endswith(('_slab', '_stairs', '_wall', '_leaves', '_pane', '_fence', '_trapdoor', '_door')) or 'glass' in n or 'grate' in n:
            thin.add(i)
    rot = [(lambda x, z: (x, z)), (lambda x, z: (-z, x)), (lambda x, z: (-x, -z)), (lambda x, z: (z, -x))][turn % 4]
    inv = [(lambda u, v: (u, v)), (lambda u, v: (v, -u)), (lambda u, v: (-u, -v)), (lambda u, v: (-v, u))][turn % 4]
    d, ny = G.data, G.ny

    sgn = -1 if flip else 1

    def occ(u, y, v):
        x, z = inv(u, v)
        y = y * sgn
        if not (x0 <= x <= x1 and z0 <= z <= z1 and y0 <= y <= y1):
            return False
        i = G.gid(x, y, z)
        return i != 0 and i not in thin and i not in skip

    items = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            base = G.col(x, z)
            lo, hi = max(0, y0 - G.y0), min(ny - 1, y1 - G.y0)
            seg = d[base + lo:base + hi + 1]
            if not any(seg):
                continue
            u, v = rot(x, z)
            for k, i in enumerate(seg):
                if not i or i in skip:
                    continue
                y = (G.y0 + lo + k) * sgn
                if occ(u, y + 1, v) and occ(u + 1, y, v) and occ(u, y, v + 1):
                    continue
                items.append((u + y + v, y, u - v, u, v, i))
    if extra:
        for (x, y, z), st in extra.items():
            if x0 <= x <= x1 and z0 <= z <= z1:
                u, v = rot(x, z)
                items.append((u + y + v, y, u - v, u, v, G.sid(st)))
    if not items:
        return None
    items.sort()
    s = scale
    s2 = 2 * s
    us = [(it[3] - it[4]) * s2 for it in items]
    vs = [(it[3] + it[4]) * s - it[1] * s2 for it in items]
    W = int(max(us) - min(us)) + 4 * s2 + 60
    H = int(max(vs) - min(vs)) + 4 * s2 + 60
    ox, oy = -min(us) + 30 + s2, -min(vs) + 30
    im = Image.new('RGBA', (W, H))
    dr = ImageDraw.Draw(im)
    for j in range(H):
        k = j / H
        dr.line([(0, j), (W, j)], fill=tuple(int(sky[0][c] * (1 - k) + sky[1][c] * k) for c in range(3)) + (255,))
    for (it, u, v) in zip(items, us, vs):
        _, y, _, a, b, i = it
        st = pal[i]
        n = name(st)
        if st in SKIP:
            continue
        u, v = int(u + ox), int(v + oy)
        if n in VR.CROSS or n.startswith('potted_') or n in PLANTS:
            im.alpha_composite(VR._sprite(st, 'cross', s), (u - s, v + s2 - s))
            continue
        half = (n.endswith('_slab') and 'type=bottom' in st) or (n.endswith('_stairs') and 'half=bottom' in st)
        thin_ = n in VR.THIN
        dy = s if half else (s2 - max(1, s // 3) if thin_ else 0)
        if not occ(a, y + 1, b) or half or thin_:
            im.alpha_composite(VR._sprite(st, 'top', s), (u - s2, v + dy))
        if thin_:
            continue
        if not occ(a, y, b + 1):
            im.alpha_composite(VR._sprite(st, 'left' + ('_half' if half else ''), s), (u - s2, v + s + dy))
        if not occ(a + 1, y, b):
            im.alpha_composite(VR._sprite(st, 'right' + ('_half' if half else ''), s), (u, v + s + dy))
    im.convert('RGB').save(path)
    return im.size


# ---------------- plan view ----------------
DCOL = {'market': (243, 156, 90), 'inns': (242, 200, 90), 'gardens': (130, 196, 100), 'travellers': (110, 160, 220),
        'temple': (230, 130, 190), 'workshops': (160, 130, 230)}
DNAME = {'market': 'Barrio del Mercado', 'inns': 'Barrio de las Posadas', 'gardens': 'Los Jardines',
         'travellers': 'Barrio de los Viajeros', 'temple': 'Barrio del Templo', 'workshops': 'Los Talleres'}


def avg_colour(st):
    top, side = VR.face_textures(st)
    im = VR._texture(top)
    px = [p for p in im.getdata() if p[3] > 0]
    if not px:
        return (128, 128, 128)
    return tuple(sum(p[c] for p in px) // len(px) for c in range(3))


def topdown(G, N, path, markers, px=3):
    E = G.x1
    size = (2 * E + 1) * px
    legend = 430
    im = Image.new('RGB', (size + legend + 40, size + 110), (28, 34, 51))
    dr = ImageDraw.Draw(im)
    cache = {}
    tops = {}
    for x in range(G.x0, G.x1 + 1):
        for z in range(G.z0, G.z1 + 1):
            base = G.col(x, z)
            seg = G.data[base:base + G.ny]
            for k in range(G.ny - 1, -1, -1):
                i = seg[k]
                if i and G.palette[i] not in SKIP:
                    tops[(x, z)] = (G.y0 + k, i)
                    break
    ox, oy = 20, 90
    for (x, z), (y, i) in tops.items():
        if i not in cache:
            cache[i] = avg_colour(G.palette[i])
        c = cache[i]
        lot = N.LOT.get((x, z))
        if lot is not None:
            dcol = DCOL[N.LOTS[lot]['district']]
            c = tuple(int(c[k] * 0.45 + dcol[k] * 0.55) for k in range(3))
        yn = tops.get((x - 1, z - 1), (y, 0))[0]
        k = max(0.6, min(1.3, 1 + 0.08 * (y - yn)))
        c = tuple(min(255, int(v * k)) for v in c)
        X, Y = ox + (x - G.x0) * px, oy + (z - G.z0) * px
        dr.rectangle([X, Y, X + px - 1, Y + px - 1], fill=c)
    font = lambda n, bold=False: ImageFont.truetype('C:/Windows/Fonts/%s' % ('cambriab.ttf' if bold else 'cambria.ttc'), n)
    boxes = []
    for (es, en, x, z, kind) in N.LANDMARKS:
        if kind == 'mirador':
            continue
        X, Y = ox + (x - G.x0) * px, oy + (z - G.z0) * px
        f = font(15 if kind in ('palace', 'market') else 13, bold=kind in ('palace', 'market', 'plaza'))
        tw = dr.textlength(es, font=f)
        for (bx, by) in ((X + 8, Y - 9), (X - 12 - tw, Y - 9), (X - tw / 2, Y + 9), (X - tw / 2, Y - 28)):
            box = (bx, by, bx + tw + 4, by + 18)
            if not any(box[0] < b[2] and b[0] < box[2] and box[1] < b[3] and b[1] < box[3] for b in boxes):
                break
        boxes.append(box)
        dr.ellipse([X - 4, Y - 4, X + 4, Y + 4], fill=(255, 243, 214), outline=(28, 34, 51), width=2)
        dr.rectangle(box, fill=(28, 34, 51))
        dr.text((box[0] + 2, box[1]), es, font=f, fill=(255, 243, 214))
    for (nm, (x, y, z)) in markers:
        if nm in ('arrival', 'town_hall_portal', 'trading_hall', 'player_plot') or nm.startswith(('easter', 'sidequest')):
            X, Y = ox + (x - G.x0) * px, oy + (z - G.z0) * px
            dr.rectangle([X - 2, Y - 2, X + 2, Y + 2], fill=(255, 60, 60))
    dr.text((20, 16), 'Solsticio v8 · fase 1 · planta construida', font=font(34, True), fill=(255, 243, 214))
    dr.text((20, 56), 'Bloques de Minecraft desde el plano urbano; color de barrio sobre los techos; marcas rojas: marcadores.',
            font=font(16), fill=(201, 211, 230))
    lx, ly = size + 50, 100
    dr.text((lx, ly - 34), 'Barrios', font=font(22, True), fill=(255, 243, 214))
    for i, (k, c) in enumerate(DCOL.items()):
        y = ly + i * 26
        dr.rectangle([lx, y, lx + 20, y + 18], fill=c, outline=(14, 18, 32))
        dr.text((lx + 30, y - 1), DNAME[k], font=font(16), fill=(230, 236, 246))
    y = ly + len(DCOL) * 26 + 24
    dr.rectangle([lx + 6, y + 4, lx + 12, y + 10], fill=(255, 60, 60))
    dr.text((lx + 30, y - 1), 'Llegada, portal, mercado, lotes, huevos', font=font(16), fill=(230, 236, 246))
    y += 40
    dr.rectangle([lx, y, lx + 50 * px, y + 6], fill=(255, 243, 214))
    dr.text((lx, y + 10), '50 bloques', font=font(15), fill=(201, 211, 230))
    im.save(path)
    return im.size


# ---------------- street-level perspective ----------------
_W = {}


def _init_worker(shm_name, shape, palette, texinfo, extra):
    from multiprocessing import shared_memory
    shm = shared_memory.SharedMemory(name=shm_name)
    _W['shm'] = shm
    _W['data'] = shm.buf.cast('H')
    _W['shape'] = shape
    _W['pal'] = palette
    _W['tex'] = texinfo
    _W['extra'] = extra


def props(st):
    if '[' not in st:
        return {}
    return dict(p.split('=') for p in st[:-1].split('[')[1].split(','))


def boxes_for(st):
    """Collision-like boxes (x0, y0, z0, x1, y1, z1) inside the unit cube for blocks that are not
    full cubes, or None for a full cube."""
    n = name(st)
    p = props(st)
    if n.endswith('_stairs'):
        lo = p.get('half', 'bottom') == 'bottom'
        base = (0, 0, 0, 1, 0.5, 1) if lo else (0, 0.5, 0, 1, 1, 1)
        y0, y1 = (0.5, 1) if lo else (0, 0.5)
        f = p.get('facing', 'north')
        back = {'north': (0, y0, 0, 1, y1, 0.5), 'south': (0, y0, 0.5, 1, y1, 1),
                'east': (0.5, y0, 0, 1, y1, 1), 'west': (0, y0, 0, 0.5, y1, 1)}[f]
        return [base, back]
    if n.endswith('_slab'):
        t = p.get('type', 'bottom')
        return None if t == 'double' else [(0, 0, 0, 1, 0.5, 1) if t == 'bottom' else (0, 0.5, 0, 1, 1, 1)]
    if n.endswith('_wall'):
        out = [(0.25, 0, 0.25, 0.75, 1, 0.75)] if p.get('up') == 'true' else []
        arms = {'north': (0.3125, 0, 0, 0.6875, 0.875, 0.5), 'south': (0.3125, 0, 0.5, 0.6875, 0.875, 1),
                'east': (0.5, 0, 0.3125, 1, 0.875, 0.6875), 'west': (0, 0, 0.3125, 0.5, 0.875, 0.6875)}
        for k, b in arms.items():
            if p.get(k, 'none') != 'none':
                out.append(b)
        return out or [(0.25, 0, 0.25, 0.75, 1, 0.75)]
    if n.endswith('_fence'):
        return [(0.375, 0, 0.375, 0.625, 1, 0.625)]
    if n in ('lantern', 'soul_lantern'):
        return [(0.3125, 0, 0.3125, 0.6875, 0.5625, 0.6875)] if p.get('hanging') != 'true' else \
            [(0.3125, 0.0625, 0.3125, 0.6875, 0.625, 0.6875), (0.45, 0.625, 0.45, 0.55, 1, 0.55)]
    if n in ('end_rod', 'lightning_rod', 'chain'):
        return [(0.4375, 0, 0.4375, 0.5625, 1, 0.5625)]
    if n == 'campfire':
        return [(0, 0, 0, 1, 0.4375, 1)]
    if n.endswith('_carpet'):
        return [(0, 0, 0, 1, 0.0625, 1)]
    if n.endswith('_trapdoor'):
        return [(0, 0.8125, 0, 1, 1, 1)] if p.get('half') == 'top' else [(0, 0, 0, 1, 0.1875, 1)]
    if n == 'ladder':
        f = p.get('facing', 'north')
        return [{'north': (0, 0, 0.8125, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.1875),
                 'east': (0, 0, 0, 0.1875, 1, 1), 'west': (0.8125, 0, 0, 1, 1, 1)}[f]]
    if n == 'dirt_path':
        return [(0, 0, 0, 1, 0.9375, 1)]
    return None


def texinfo_for(palette):
    """Per palette index: kind flag, the two 16x16 textures as flat RGBA lists, emissive, water,
    and the boxes of a block that is not a full cube."""
    info = []
    for st in palette:
        if not st or st in SKIP:
            info.append(None)
            continue
        n = name(st)
        top, side = VR.face_textures(st)
        t = list(VR._texture(top).convert('RGBA').getdata())
        s_ = list(VR._texture(side).convert('RGBA').getdata())
        flag = 'plant' if (n in PLANTS or n.startswith('potted_')) else 'see' if (n in SEE or 'glass' in n or n == 'water') else \
            'leaf' if n.endswith('_leaves') else 'solid'
        bx = boxes_for(st) if flag in ('solid', 'plant') else None
        if bx is not None and (flag == 'plant' or n.endswith(('_fence', '_wall', '_carpet'))):
            flag = 'thin'
        info.append((flag, t, s_, n in EMIT or 'froglight' in n, n == 'water', bx))
    return info


def box_hit(ex, ey, ez, dx, dy, dz, X, Y, Z, boxes):
    """Nearest entry (t, axis) of the ray into any of the boxes of voxel (X, Y, Z), or None."""
    best = None
    for (a0, b0, c0, a1, b1, c1) in boxes:
        tmin, tmax, axis = -1e30, 1e30, 1
        for k, (o, d, lo, hi) in enumerate(((ex, dx, X + a0, X + a1), (ey, dy, Y + b0, Y + b1), (ez, dz, Z + c0, Z + c1))):
            if abs(d) < 1e-12:
                if o < lo or o > hi:
                    tmin = 1e30
                    break
                continue
            t1, t2 = (lo - o) / d, (hi - o) / d
            if t1 > t2:
                t1, t2 = t2, t1
            if t1 > tmin:
                tmin, axis = t1, k
            tmax = min(tmax, t2)
        if tmin <= tmax and tmax > 0 and (best is None or tmin < best[0]):
            best = (tmin, axis)
    return best


def _render_rows(args):
    rows, W, H, eye, fwd, right, up, fov, sky, sun, fogd = args
    x0, y0, z0, nx, ny, nz = _W['shape']
    data, tex = _W['data'], _W['tex']
    th = math.tan(math.radians(fov) / 2)
    out = []
    for j in rows:
        line = bytearray(W * 3)
        for i in range(W):
            u = (2 * (i + 0.5) / W - 1) * th
            v = (1 - 2 * (j + 0.5) / H) * th * H / W
            dx = fwd[0] + right[0] * u + up[0] * v
            dy = fwd[1] + right[1] * u + up[1] * v
            dz = fwd[2] + right[2] * u + up[2] * v
            ln = math.sqrt(dx * dx + dy * dy + dz * dz)
            dx, dy, dz = dx / ln, dy / ln, dz / ln
            col = trace(eye, (dx, dy, dz), data, tex, (x0, y0, z0, nx, ny, nz), sky, sun, fogd)
            line[3 * i:3 * i + 3] = bytes(col)
        out.append((j, bytes(line)))
    return out


def sky_col(dy, sky):
    t = max(0.0, min(1.0, dy * 1.6 + 0.35))
    return [sky[0][k] * (1 - t) + sky[1][k] * t for k in range(3)]


def trace(eye, d, data, tex, shape, sky, sun, fogd, depth=0, maxd=420):
    x0, y0, z0, nx, ny, nz = shape
    ex, ey, ez = eye
    dx, dy, dz = d
    X, Y, Z = math.floor(ex), math.floor(ey), math.floor(ez)
    sx = 1 if dx > 0 else -1
    sy = 1 if dy > 0 else -1
    sz = 1 if dz > 0 else -1
    tdx = abs(1 / dx) if dx else 1e30
    tdy = abs(1 / dy) if dy else 1e30
    tdz = abs(1 / dz) if dz else 1e30
    tmx = ((X + (sx > 0)) - ex) / dx if dx else 1e30
    tmy = ((Y + (sy > 0)) - ey) / dy if dy else 1e30
    tmz = ((Z + (sz > 0)) - ez) / dz if dz else 1e30
    t = 0.0
    face = 1
    acc = [0.0, 0.0, 0.0]
    trans = 1.0
    ny_nz = ny
    while t < maxd:
        if tmx < tmy:
            if tmx < tmz:
                X += sx
                t = tmx
                tmx += tdx
                face = 0
            else:
                Z += sz
                t = tmz
                tmz += tdz
                face = 2
        else:
            if tmy < tmz:
                Y += sy
                t = tmy
                tmy += tdy
                face = 1
            else:
                Z += sz
                t = tmz
                tmz += tdz
                face = 2
        ix, iy, iz = X - x0, Y - y0, Z - z0
        if ix < 0 or ix >= nx or iz < 0 or iz >= nz:
            if (ix < 0 and sx < 0) or (ix >= nx and sx > 0) or (iz < 0 and sz < 0) or (iz >= nz and sz > 0):
                break
            continue
        if iy < 0 or iy >= ny:
            if (iy < 0 and sy < 0) or (iy >= ny and sy > 0):
                break
            continue
        i = data[(ix * nz + iz) * ny_nz + iy]
        if not i:
            continue
        info = tex[i]
        if info is None:
            continue
        flag, ttex, stex, emit, water, bx = info
        if bx is not None:
            hit = box_hit(ex, ey, ez, dx, dy, dz, X, Y, Z, bx)
            if hit is None:
                continue
            th_, face = hit
            hx, hy, hz = ex + dx * th_, ey + dy * th_, ez + dz * th_
        else:
            hx, hy, hz = ex + dx * t, ey + dy * t, ez + dz * t
        if face == 1:
            uu, vv = hx - X, hz - Z
            texl = ttex
        elif face == 0:
            uu, vv = hz - Z, (1 - (hy - Y)) % 1.0
            texl = stex
        else:
            uu, vv = hx - X, (1 - (hy - Y)) % 1.0
            texl = stex
        pi = min(15, max(0, int(vv * 16))) * 16 + min(15, max(0, int(uu * 16)))
        r, g, b, a = texl[pi]
        if flag == 'plant':
            if abs(hx - X - 0.5) > 0.32 or abs(hz - Z - 0.5) > 0.32 or a < 128:
                continue
        elif flag == 'leaf' and a < 128:
            continue
        shade = 1.0 if face == 1 else (0.82 if face == 0 else 0.68)
        if face == 1 and dy > 0:
            shade = 0.5
        if not emit:
            # the noon sun: a shadow ray from just off the face
            nxv = -sx if face == 0 else 0
            nyv = -sy if face == 1 else 0
            nzv = -sz if face == 2 else 0
            p = (hx + nxv * 0.01, hy + nyv * 0.01, hz + nzv * 0.01)
            if shadowed(p, sun, data, tex, shape):
                shade *= 0.58
            lam = max(0.0, nxv * sun[0] + nyv * sun[1] + nzv * sun[2])
            shade *= 0.72 + 0.34 * lam
        else:
            shade = 1.15
        c = [r * shade, g * shade, b * shade]
        if flag == 'see':
            al = 0.55 if water else 0.3
            if water:
                c = [c[0] * 0.8, c[1] * 0.9 + 10, c[2] + 25]
            for k in range(3):
                acc[k] += trans * al * c[k]
            trans *= 1 - al
            if trans < 0.08:
                break
            continue
        f = 1 - math.exp(-t / fogd)
        s_ = sky_col(dy, sky)
        c = [c[k] * (1 - f) + s_[k] * f for k in range(3)]
        for k in range(3):
            acc[k] += trans * c[k]
        return [max(0, min(255, int(v))) for v in acc]
    s_ = sky_col(dy, sky)
    return [max(0, min(255, int(acc[k] + trans * s_[k]))) for k in range(3)]


def shadowed(p, sun, data, tex, shape, maxd=90):
    x0, y0, z0, nx, ny, nz = shape
    ex, ey, ez = p
    dx, dy, dz = sun
    X, Y, Z = math.floor(ex), math.floor(ey), math.floor(ez)
    sx = 1 if dx > 0 else -1
    sy = 1 if dy > 0 else -1
    sz = 1 if dz > 0 else -1
    tdx = abs(1 / dx) if dx else 1e30
    tdy = abs(1 / dy) if dy else 1e30
    tdz = abs(1 / dz) if dz else 1e30
    tmx = ((X + (sx > 0)) - ex) / dx if dx else 1e30
    tmy = ((Y + (sy > 0)) - ey) / dy if dy else 1e30
    tmz = ((Z + (sz > 0)) - ez) / dz if dz else 1e30
    t = 0.0
    while t < maxd:
        if tmx < tmy:
            if tmx < tmz:
                X += sx
                t = tmx
                tmx += tdx
            else:
                Z += sz
                t = tmz
                tmz += tdz
        else:
            if tmy < tmz:
                Y += sy
                t = tmy
                tmy += tdy
            else:
                Z += sz
                t = tmz
                tmz += tdz
        ix, iy, iz = X - x0, Y - y0, Z - z0
        if iy >= ny or ix < 0 or ix >= nx or iz < 0 or iz >= nz:
            return False
        if iy < 0:
            continue
        i = data[(ix * nz + iz) * ny + iy]
        if i:
            info = tex[i]
            if info is None:
                continue
            if info[0] in ('solid', 'leaf'):
                return True
    return False


def street(G, path, eye, target, W=1280, H=720, fov=70, procs=8, extra=None):
    """Perspective view from eye (x, y, z) toward target, textured, noon sun, shadows, fog."""
    from multiprocessing import Pool, shared_memory
    fx, fy, fz = (target[0] - eye[0], target[1] - eye[1], target[2] - eye[2])
    ln = math.sqrt(fx * fx + fy * fy + fz * fz)
    fwd = (fx / ln, fy / ln, fz / ln)
    rx, rz = -fwd[2], fwd[0]                    # right = fwd x up(0,1,0), then up = right x fwd
    rl = math.hypot(rx, rz)
    right = (rx / rl, 0.0, rz / rl)
    up = (right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2], right[0] * fwd[1] - right[1] * fwd[0])
    if up[1] < 0:
        up = (-up[0], -up[1], -up[2])
    sun = (0.18, 0.95, 0.25)
    sl = math.sqrt(sum(v * v for v in sun))
    sun = tuple(v / sl for v in sun)
    sky = ((236, 226, 206), (122, 170, 226))
    raw = G.data.tobytes()
    shm = shared_memory.SharedMemory(create=True, size=len(raw))
    shm.buf[:len(raw)] = raw
    del raw
    info = texinfo_for(G.palette)
    shape = (G.x0, G.y0, G.z0, G.nx, G.ny, G.nz)
    chunks = [list(range(j, H, procs * 4)) for j in range(procs * 4)]
    args = [(rows, W, H, eye, fwd, right, up, fov, sky, sun, 260.0) for rows in chunks]
    im = Image.new('RGB', (W, H))
    try:
        with Pool(procs, initializer=_init_worker, initargs=(shm.name, shape, G.palette, info, None)) as pool:
            for res in pool.imap_unordered(_render_rows, args):
                for j, line in res:
                    im.paste(Image.frombytes('RGB', (W, 1), line), (0, j))
    finally:
        shm.close()
        shm.unlink()
    im.save(path)
    return im.size


# ---------------- the set ----------------
def renders(G, extra, N, out, which, markers):
    os.makedirs(out, exist_ok=True)
    t0 = time.time()
    all_ = 'all' in which
    if all_ or 'map' in which:
        print('map', topdown(G, N, os.path.join(out, 'solsticio8_map.png'), markers), round(time.time() - t0), 's')
    if all_ or 'iso' in which:
        print('iso', iso(G, os.path.join(out, 'solsticio8_iso.png'), 2, extra=extra),
              round(time.time() - t0), 's')
    if all_ or 'axis' in which:
        pl = next(p for p in N.PLAZAS if p[0] == 'Plaza del Portal')
        eye = (0.5, pl[3] + 2.6, 121.5)
        print('axis', street(G, os.path.join(out, 'solsticio8_axis.png'), eye, (0.5, eye[1] + math.tan(math.radians(17)) * 168, -47), fov=80),
              round(time.time() - t0), 's')
    if all_ or 'market' in which:
        pl = next(p for p in N.PLAZAS if p[0] == 'Plaza del Mercado')
        (cx, cz) = pl[1]
        eye = (cx - 11.5, pl[3] + 2.6, cz + 6.5)
        print('market', street(G, os.path.join(out, 'solsticio8_market.png'), eye, (cx + 22, pl[3] + 9, cz + 6), fov=84),
              round(time.time() - t0), 's')
    if all_ or 'workshops' in which:
        eye, tgt = workshop_view(N)
        print('workshops', street(G, os.path.join(out, 'solsticio8_workshops.png'), eye, tgt), round(time.time() - t0), 's')
    if all_ or 'underside' in which:
        print('underside', iso(G, os.path.join(out, 'solsticio8_underside.png'), 2, flip=True, turn=2,
                               sky=((200, 218, 240), (252, 238, 208))), round(time.time() - t0), 's')
    if all_ or 'iso-axis' in which:
        print('iso-axis', iso(G, os.path.join(out, 'solsticio8_iso_axis.png'), 4, box=(-45, -10, -70, 45, G.y1, 135), turn=0),
              round(time.time() - t0), 's')
    if all_ or 'iso-market' in which:
        pl = next(p for p in N.PLAZAS if p[0] == 'Plaza del Mercado')
        (cx, cz) = pl[1]
        print('iso-market', iso(G, os.path.join(out, 'solsticio8_iso_market.png'), 5, box=(cx - 22, -5, cz - 30, cx + 55, G.y1, cz + 30)),
              round(time.time() - t0), 's')


def workshop_view(N):
    """A street of the Workshops climbing away from the Clock Square: standing at its foot on the
    square, looking west up its flights between the workshops."""
    p = next(p for p in N.PATHS if p.name == 'street workshops 1')
    pi = N.PATHS.index(p)
    own = [i for i, (x, z) in enumerate(p.pts) if N.OWNER.get((round(x), round(z))) == pi]
    foot = max(i for i in own if i < len(p.pts) // 2 + 10)
    k = min(len(p.pts) - 1, foot + 4)
    (x, z) = p.pts[k]
    lev = N.LEVEL.get((round(x), round(z)), p.levels[k])
    t = max(0, foot - 26)
    (tx, tz), tl = p.pts[t], p.levels[t]
    return (x + 0.5, lev + 2.6, z + 0.5), (tx + 0.5, tl + 4.0, tz + 0.5)
