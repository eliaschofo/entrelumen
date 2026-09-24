"""Small voxel kit for ENTRELUMEN structures: a block dict with D4-symmetric placement and an
isometric preview in texture-average colours. Coordinates are centred (x, z around 0); templates
translate them when they write NBT."""
import math
from PIL import Image, ImageDraw

T8 = [(sx, sz, sw) for sw in (False, True) for sx in (1, -1) for sz in (1, -1)]
FACING = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
INV = {v: k for k, v in FACING.items()}


def tf(x, z, t):
    sx, sz, sw = t
    if sw:
        x, z = z, x
    return sx * x, sz * z


def rot_state(b, t):
    if '[' not in b:
        return b
    name, props = b[:-1].split('[')
    out = []
    for p in props.split(','):
        k, v = p.split('=')
        if k == 'facing' and v in FACING:
            v = INV[tf(*FACING[v], t)]
        if k == 'axis' and t[2] and v in 'xz':
            v = 'z' if v == 'x' else 'x'
        out.append(k + '=' + v)
    return name + '[' + ','.join(out) + ']'


def ab(x, z):
    """Octant coordinates: anything computed from them is symmetric under the whole D4 group."""
    a, b = abs(x), abs(z)
    return (a, b) if a >= b else (b, a)


class Voxels(dict):
    def put(self, x, y, z, b):
        self[(x, y, z)] = b

    def sym(self, x, y, z, b):
        for t in T8:
            p, q = tf(x, z, t)
            self[(p, y, q)] = rot_state(b, t)

    def is_symmetric(self):
        for (x, y, z), b in self.items():
            for t in T8:
                p, q = tf(x, z, t)
                if self.get((p, y, q)) != rot_state(b, t):
                    return False
        return True


# ---------------- preview ----------------
TEX = 'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/ref/vanilla/assets/minecraft/textures/block/'
SMALL = {'allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet', 'short_grass', 'fern', 'moss_carpet',
         'lightning_rod', 'hanging_roots', 'tuff_brick_wall', 'daylight_detector', 'wheat', 'carrots', 'candle',
         'glow_lichen', 'dead_bush', 'pink_petals'}
TEXNAME = {'grass_block': ('grass_block_top', 'grass_block_side'), 'water': ('water_still',) * 2,
           'quartz_pillar': ('quartz_pillar_top', 'quartz_pillar'),
           'chiseled_quartz_block': ('chiseled_quartz_block_top', 'chiseled_quartz_block'),
           'pearlescent_froglight': ('pearlescent_froglight_top', 'pearlescent_froglight_side'),
           'tuff_brick_wall': ('tuff_bricks',) * 2, 'moss_carpet': ('moss_block',) * 2,
           'copper_bulb': ('copper_bulb_lit',) * 2, 'flowering_azalea': ('flowering_azalea_top', 'flowering_azalea_side'),
           'azalea': ('azalea_top', 'azalea_side'), 'heliodor_pedestal': ('chiseled_tuff_top', 'chiseled_tuff'),
           'chiseled_tuff': ('chiseled_tuff_top', 'chiseled_tuff'),
           'chiseled_tuff_bricks': ('chiseled_tuff_bricks_top', 'chiseled_tuff_bricks'),
           'tuff_brick_slab': ('tuff_bricks',) * 2, 'polished_tuff_slab': ('polished_tuff',) * 2,
           'tuff_brick_stairs': ('tuff_bricks',) * 2, 'cut_copper_slab': ('cut_copper',) * 2,
           'oxidized_cut_copper_slab': ('oxidized_cut_copper',) * 2, 'basalt': ('basalt_top', 'basalt_side'),
           'polished_basalt': ('polished_basalt_top', 'polished_basalt_side'), 'magma_block': ('magma',) * 2,
           'lava': ('lava_still',) * 2, 'crying_obsidian': ('crying_obsidian',) * 2,
           'end_stone_bricks': ('end_stone_bricks',) * 2, 'purpur_pillar': ('purpur_pillar_top', 'purpur_pillar'),
           'fern': ('fern',) * 2, 'short_grass': ('short_grass',) * 2,
           'lectern': ('lectern_top', 'lectern_sides'), 'bookshelf': ('oak_planks', 'bookshelf')}
TINT = {'grass_block_top': (104, 158, 70), 'short_grass': (88, 140, 60), 'fern': (80, 130, 56),
        'water_still': (70, 120, 210)}
_cache = {}


def colours(b):
    name = b.split('[')[0].split(':')[1]
    if name in _cache:
        return _cache[name]
    base = name.replace('waxed_', '')
    out = []
    for t in TEXNAME.get(base, (base, base)):
        if t in TINT:
            out.append(TINT[t])
            continue
        try:
            im = Image.open(TEX + t + '.png').convert('RGBA').crop((0, 0, 16, 16))
            px = [p for p in im.get_flattened_data() if p[3] > 0]
            out.append(tuple(sum(p[i] for p in px) // len(px) for i in range(3)))
        except Exception:
            out.append((200, 60, 200))
    _cache[name] = out
    return out


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def iso(vox, path, scale=8, ground=None):
    """Isometric render. `ground` adds a grass disc of that radius under layer 0 for context."""
    allv = dict(vox)
    if ground:
        for x in range(-ground, ground + 1):
            for z in range(-ground, ground + 1):
                if math.hypot(x, z) <= ground and (x, 0, z) not in allv:
                    allv[(x, 0, z)] = 'minecraft:grass_block'
                if math.hypot(x, z) <= ground:
                    allv.setdefault((x, -1, z), 'minecraft:dirt')
    allv = {k: v for k, v in allv.items() if not v.endswith(':air')}
    occ = {k for k, v in allv.items() if v.split('[')[0].split(':')[1] not in SMALL}
    vis = [(p, b) for p, b in allv.items()
           if not all(n in occ for n in ((p[0], p[1] + 1, p[2]), (p[0] + 1, p[1], p[2]), (p[0], p[1], p[2] + 1)))]
    s2 = 2 * scale
    proj = lambda x, y, z: ((x - z) * s2, (x + z) * scale - y * s2)
    pts = [proj(*p) for p, _ in vis]
    minu, maxu = min(p[0] for p in pts), max(p[0] for p in pts)
    minv, maxv = min(p[1] for p in pts), max(p[1] for p in pts)
    W, H = int(maxu - minu) + 4 * s2 + 40, int(maxv - minv) + 4 * s2 + 40
    ox, oy = -minu + 20 + s2, -minv + 20
    im = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(im)
    for j in range(H):
        k = j / H
        d.line([(0, j), (W, j)], fill=(int(252 - 40 * k), int(236 - 20 * k), int(200 + 30 * k)))
    for (x, y, z), bl in sorted(vis, key=lambda t: (t[0][0] + t[0][2] + t[0][1], t[0][1])):
        top, side = colours(bl)
        name = bl.split('[')[0].split(':')[1]
        u, v = proj(x, y, z)
        u += ox
        v += oy
        if name in SMALL:
            r = scale * 0.9
            cy = v + s2
            d.polygon([(u, cy - r), (u + 2 * r, cy), (u, cy + r), (u - 2 * r, cy)], fill=top)
            continue
        slab = name.endswith('_slab') and 'type=bottom' in bl
        h = scale if slab else s2
        T = [(u, v + s2 - h), (u + s2, v + scale + s2 - h), (u, v + 2 * s2 - h), (u - s2, v + scale + s2 - h)]
        L = [(u - s2, v + scale + s2 - h), (u, v + 2 * s2 - h), (u, v + 2 * s2), (u - s2, v + scale + s2)]
        Rr = [(u, v + 2 * s2 - h), (u + s2, v + scale + s2 - h), (u + s2, v + scale + s2), (u, v + 2 * s2)]
        d.polygon(L, fill=shade(side, .8))
        d.polygon(Rr, fill=shade(side, .64))
        d.polygon(T, fill=top)
    im.save(path)
    return im


# ---------------- NBT reading (for vanilla references) ----------------
def load_nbt(path_or_bytes):
    """Read a gzip structure template into centred Voxels (x, z centred, y from 0)."""
    import gzip, struct
    data = gzip.decompress(path_or_bytes if isinstance(path_or_bytes, bytes) else open(path_or_bytes, 'rb').read())
    pos = [0]

    def rd(fmt):
        n = struct.calcsize(fmt)
        val = struct.unpack_from(fmt, data, pos[0])
        pos[0] += n
        return val[0]

    def string():
        n = rd('>H')
        s = data[pos[0]:pos[0] + n].decode('utf-8', 'replace')
        pos[0] += n
        return s

    def payload(tag):
        if tag == 1: return rd('>b')
        if tag == 2: return rd('>h')
        if tag == 3: return rd('>i')
        if tag == 4: return rd('>q')
        if tag == 5: return rd('>f')
        if tag == 6: return rd('>d')
        if tag == 7: n = rd('>i'); b = data[pos[0]:pos[0] + n]; pos[0] += n; return b
        if tag == 8: return string()
        if tag == 9:
            t = rd('>b'); n = rd('>i')
            return [payload(t) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = rd('>b')
                if t == 0: return out
                k = string(); out[k] = payload(t)
        if tag == 11: n = rd('>i'); return [rd('>i') for _ in range(n)]
        if tag == 12: n = rd('>i'); return [rd('>q') for _ in range(n)]
        raise ValueError(tag)

    rd('>b'); string()
    root = payload(10)
    sx, sy, sz = root['size']
    pal = root['palette'] if 'palette' in root else root['palettes'][0]
    states = []
    for e in pal:
        props = e.get('Properties', {})
        states.append(e['Name'] + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else ''))
    v = Voxels()
    for b in root['blocks']:
        x, y, z = b['pos']
        st = states[b['state']]
        if st in ('minecraft:air', 'minecraft:structure_void', 'minecraft:jigsaw') or st.startswith('minecraft:jigsaw['):
            continue
        v[(x - sx // 2, y, z - sz // 2)] = st
    return v
