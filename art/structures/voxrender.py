"""Textured isometric render for voxel designs: every visible face drawn with its real vanilla
16x16 texture (top unshaded, the two visible sides shaded like Minecraft's directional light),
plants as upright sprites, slabs and carpets at their height. Mod blocks listed in
modblocks/palette.json use the textures and shape recorded there. Preview only."""
import math
import os

from PIL import Image, ImageDraw

from voxkit import TEX, TEXNAME

try:                                        # decorative mod blocks: modblocks/palette.json
    from modblocks import palette as _modpal
except ImportError:
    _modpal = None

_TEXCACHE, _SPRITES = {}, {}
CROSS = {'allium', 'cornflower', 'lily_of_the_valley', 'azure_bluet', 'short_grass', 'fern', 'dead_bush',
         'hanging_roots', 'wheat', 'carrots', 'sweet_berry_bush', 'poppy', 'dandelion', 'oxeye_daisy',
         'blue_orchid', 'torchflower', 'pink_tulip', 'white_tulip', 'amethyst_cluster', 'large_amethyst_bud',
         'medium_amethyst_bud', 'small_amethyst_bud', 'glow_lichen', 'vine', 'cave_vines', 'cave_vines_plant',
         'weeping_vines', 'weeping_vines_plant', 'twisting_vines', 'twisting_vines_plant', 'chain',
         'lightning_rod', 'end_rod', 'candle', 'spore_blossom', 'tall_grass', 'large_fern', 'lilac',
         'peony', 'rose_bush', 'pitcher_plant', 'torchflower_crop', 'sugar_cane', 'bamboo', 'lantern',
         'soul_lantern', 'pointed_dripstone', 'ladder', 'potatoes', 'beetroots', 'white_candle', 'candle'}
CROSS_TEX = {'white_candle': 'white_candle', 'sugar_cane': 'sugar_cane', 'pink_petals': 'pink_petals', 'wheat': 'wheat_stage7', 'carrots': 'carrots_stage3', 'potatoes': 'potatoes_stage3',
             'pointed_dripstone': 'pointed_dripstone_down_tip', 'tall_grass': 'tall_grass_bottom',
             'large_fern': 'large_fern_bottom', 'candle': 'candle', 'beetroots': 'beetroots_stage3'}
THIN = {'moss_carpet', 'pink_petals', 'daylight_detector', 'white_carpet', 'yellow_carpet',
        'light_blue_carpet', 'lily_pad'}
SEE_THROUGH = {'glass', 'water', 'tinted_glass', 'iron_bars', 'beacon'}
GRASS_TINT, FOLIAGE_TINT, WATER_TINT = (145, 189, 89), (119, 171, 47), (63, 118, 228)
TINTED = {'grass_block_top': GRASS_TINT, 'short_grass': GRASS_TINT, 'fern': GRASS_TINT, 'tall_grass_top': GRASS_TINT,
          'tall_grass_bottom': GRASS_TINT, 'large_fern_top': GRASS_TINT, 'large_fern_bottom': GRASS_TINT,
          'oak_leaves': FOLIAGE_TINT, 'jungle_leaves': FOLIAGE_TINT, 'birch_leaves': (128, 167, 85),
          'vine': FOLIAGE_TINT, 'water_still': WATER_TINT, 'lily_pad': (32, 128, 48)}


def _name(state):
    return state.split('[')[0].split(':')[1]


def _mod(state):
    """Palette record of a mod block state, or None (vanilla and unknown blocks keep the old path)."""
    return _modpal.block(state) if _modpal and not state.startswith('minecraft:') else None


def _exists(t):
    return os.path.exists(TEX + t + '.png')


def _texture(t):
    if t in _TEXCACHE:
        return _TEXCACHE[t]
    if t.startswith('@@'):
        _TEXCACHE[t] = _companion(t[2:]) or Image.new('RGBA', (16, 16), (200, 60, 200, 255))
        return _TEXCACHE[t]
    if t.startswith('@') and _modpal:
        _TEXCACHE[t] = _modpal.image(t)
        return _TEXCACHE[t]
    try:
        im = Image.open(TEX + t + '.png').convert('RGBA')
        im = im.crop((0, 0, 16, 16)) if im.height > 16 else im.resize((16, 16), Image.NEAREST)
    except Exception:
        im = Image.new('RGBA', (16, 16), (200, 60, 200, 255))
    if t in TINTED:
        r, g, b = TINTED[t]
        px = im.load()
        for i in range(16):
            for j in range(16):
                p = px[i, j]
                px[i, j] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
    if t == 'water_still':
        im.putalpha(200)
    _TEXCACHE[t] = im
    return im


COMPANION_TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'companion', 'src', 'main',
                             'resources', 'assets', 'entrelumen', 'textures', 'block')


def _companion(name):
    """Textures of our own blocks (entrelumen:*), from the companion's assets."""
    key = '@entrelumen/' + name
    if key not in _TEXCACHE:
        path = os.path.join(COMPANION_TEX, name + '.png')
        try:
            im = Image.open(path).convert('RGBA')
            _TEXCACHE[key] = im.crop((0, 0, 16, 16)) if im.height > 16 else im.resize((16, 16), Image.NEAREST)
        except Exception:
            _TEXCACHE[key] = None
    return _TEXCACHE[key]


def face_textures(state):
    """(top, side) texture names for a block state, guessed from vanilla naming."""
    if state.startswith('entrelumen:'):
        n = _name(state)
        top = n + '_top' if _companion(n + '_top') is not None else ('module_top' if n.endswith('_module') else n)
        return '@@' + top, '@@' + n
    if _mod(state):
        return _modpal.faces(state)
    base = _name(state).replace('waxed_', '')
    if base == 'water':
        return 'water_still', 'water_still'
    if base.endswith('copper_bulb'):
        return ((base + '_lit',) * 2) if 'lit=true' in state else ((base,) * 2)
    if base in TEXNAME:
        t = TEXNAME[base]
        return t[0], t[1]
    if base.endswith('_carpet'):
        wool = base[:-len('_carpet')] + '_wool'
        return (wool, wool) if _exists(wool) else ('moss_block', 'moss_block')
    if base.endswith('_bed'):
        wool = base[:-len('_bed')] + '_wool'
        return wool, wool
    if base == 'glass_pane' or base.endswith('_stained_glass_pane'):
        g = base[:-len('_pane')]
        return g, g
    if base.endswith('_door'):
        half = 'bottom' if 'half=lower' in state else 'top'
        return base + '_' + half, base + '_' + half
    if base.endswith('_wood') or base.endswith('_hyphae'):
        log = base[:-len('_wood')] + '_log' if base.endswith('_wood') else base[:-len('_hyphae')] + '_stem'
        return log, log
    if base == 'beehive':
        return 'beehive_end', 'beehive_front'
    for suffix in ('_stairs', '_slab', '_wall'):
        if base.endswith(suffix):
            core = base[: -len(suffix)]
            for cand in (core, core + 's', core + '_block', core + '_planks', core.replace('brick', 'bricks')):
                if _exists(cand):
                    return cand, cand
            if _exists(core + '_block_side'):
                return core + '_block_top', core + '_block_side'
    if base.endswith(('_log', '_stem')) and _exists(base + '_top'):
        return base + '_top', base
    top = base + '_top' if _exists(base + '_top') else base
    side = base + '_side' if _exists(base + '_side') else base
    return top, side


def _shade(sp, k):
    r, g, b, a = sp.split()
    f = lambda v: int(v * k)
    return Image.merge('RGBA', (r.point(f), g.point(f), b.point(f), a))


def _sprite(state, face, s):
    key = (state, face, s)
    if key in _SPRITES:
        return _SPRITES[key]
    top, side = face_textures(state)
    name = _name(state)
    if face == 'cross' and _mod(state):
        sp = _texture(_modpal.sprite(state)).resize((2 * s, 2 * s), Image.NEAREST)
    elif face == 'cross':
        if name.startswith('potted_'):
            name = name[len('potted_'):].replace('flowering_azalea_bush', 'flowering_azalea_side')
        t = CROSS_TEX.get(name) or (name if _exists(name) else (name + '_top' if _exists(name + '_top') else side))
        sp = _texture(t).resize((2 * s, 2 * s), Image.NEAREST)
    elif face == 'top':
        sp = _texture(top).transform((4 * s, 2 * s), Image.AFFINE, (4 / s, 8 / s, -8, -4 / s, 8 / s, 8), Image.NEAREST)
    else:
        left = face.startswith('left')
        coeffs = (8 / s, 0, 0, -4 / s, 8 / s, 0) if left else (8 / s, 0, 0, 4 / s, 8 / s, -8)
        tex = _texture(side)
        size = (2 * s, 3 * s)
        if face.endswith('_half'):                       # lower half of the texture, half as tall
            tex = tex.crop((0, 8, 16, 16))
            size = (2 * s, 2 * s)
        sp = tex.transform(size, Image.AFFINE, coeffs, Image.NEAREST)
        sp = _shade(sp, 0.8 if left else 0.62)
    _SPRITES[key] = sp
    return sp


def render(vox, path, scale=6, ground=None, sky=((252, 238, 208), (200, 218, 240)), extra=None,
           keep=None, dark_below=None):
    """Draw `vox` (plus `extra` preview-only voxels) and save to `path`. `keep(x, y, z)` filters
    voxels (cutaways); `ground` adds a grass disc of that radius under layer 0 for context."""
    s = scale
    allv = dict(vox)
    if extra:
        allv.update(extra)
    if ground:
        for x in range(-ground, ground + 1):
            for z in range(-ground, ground + 1):
                if math.hypot(x, z) <= ground:
                    allv.setdefault((x, 0, z), 'minecraft:grass_block')
                    for y in (-1, -2):
                        allv.setdefault((x, y, z), 'minecraft:dirt')
    allv = {k: v for k, v in allv.items() if not v.endswith(':air') and (keep is None or keep(*k))}
    solid = set()
    for k, v in allv.items():
        rec = _mod(v)
        if rec:
            if rec['solid']:
                solid.add(k)
            continue
        n = _name(v)
        if n in CROSS or n.startswith('potted_') or n in THIN or n in SEE_THROUGH or n.endswith(('_slab', '_stairs', '_wall', '_leaves',
                                                                       '_pane', '_fence', '_trapdoor', '_door')):
            continue
        if 'glass' in n or 'grate' in n:
            continue
        solid.add(k)
    leaves = {k for k, v in allv.items() if _name(v).endswith('_leaves')}
    occ = solid | leaves
    s2 = 2 * s
    proj = lambda x, y, z: ((x - z) * s2, (x + z) * s - y * s2)
    items = [((x, y, z), b) for (x, y, z), b in allv.items()
             if not ((x, y + 1, z) in occ and (x + 1, y, z) in occ and (x, y, z + 1) in occ)]
    if not items:
        return None
    pts = [proj(*p) for p, _ in items]
    minu, maxu = min(p[0] for p in pts), max(p[0] for p in pts)
    minv, maxv = min(p[1] for p in pts), max(p[1] for p in pts)
    W, H = int(maxu - minu) + 4 * s2 + 60, int(maxv - minv) + 4 * s2 + 60
    ox, oy = -minu + 30 + s2, -minv + 30
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    for j in range(H):
        k = j / H
        d.line([(0, j), (W, j)], fill=tuple(int(sky[0][i] * (1 - k) + sky[1][i] * k) for i in range(3)) + (255,))
    for (x, y, z), b in sorted(items, key=lambda t: (t[0][0] + t[0][1] + t[0][2], t[0][1], t[0][0] - t[0][2])):
        n = _name(b)
        mode = (_mod(b) or {}).get('render')
        u, v = proj(x, y, z)
        u, v = int(u + ox), int(v + oy)
        if mode == 'cross' or (not mode and (n in CROSS or n.startswith('potted_'))):
            im.alpha_composite(_sprite(b, 'cross', s), (u - s, v + s2 - s))
            continue
        half = (mode == 'slab' and 'type=top' not in b and 'type=double' not in b) if mode else (
            (n.endswith('_slab') and 'type=bottom' in b) or (n.endswith('_stairs') and 'half=bottom' in b))
        thin = mode == 'thin' if mode else n in THIN
        dy = s if half else (s2 - max(1, s // 3) if thin else 0)
        if (x, y + 1, z) not in occ or half or thin:
            im.alpha_composite(_sprite(b, 'top', s), (u - s2, v + dy))
        if thin:
            continue
        for face, nb, pos in (('left', (x, y, z + 1), (u - s2, v + s + dy)), ('right', (x + 1, y, z), (u, v + s + dy))):
            if nb in occ:
                continue
            im.alpha_composite(_sprite(b, face + ('_half' if half else ''), s), pos)
    im.convert('RGB').save(path)
    return im
