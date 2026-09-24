"""Voxel sculptures for the altar tops: 1x1x1-unit voxels (Minecraft's texel scale), each face sampling a
single texel of a palette texture. Runs of equal colour along X are merged; hidden faces are culled."""
import sys, json, importlib.util
sys.path.insert(0, '.')
from PIL import Image
from palette import RAMPS as R

def h(s): return tuple(int(s[i:i+2], 16) for i in (1, 3, 5))

# palette texture: one texel per colour key, laid out left-to-right, top-to-bottom
PAL = {
 'w': R['wood'][1], 'W': R['wood'][2], 'X': R['wood'][3],
 'l': R['leaf'][1], 'L': R['leaf'][2], 'M': R['leaf'][3], 'N': R['leaf'][4], 'Q': R['leaf'][5],
 't': R['teal'][1], 'T': R['teal'][2], 'u': R['teal'][3], 'U': R['teal'][4], 'Y': R['teal'][5],
 'k': R['copper'][1], 'K': R['copper'][2], 'c': R['copper'][3], 'C': R['copper'][4], 'H': R['copper'][5],
 'i': R['iron'][2], 'I': R['iron'][3], 'J': R['iron'][4],
 'v': R['verdigris'][2], 'V': R['verdigris'][3],
 'p': R['violet'][4], 'y': R['straw'][3], 'f': R['parch'][5],
}
KEYS = list(PAL)
def texel(key):
    i = KEYS.index(key); return i % 16, i // 16

def palette_texture():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for k, v in PAL.items(): im.putpixel(texel(k), h(v) + (255,))
    return im

def sculpt_renewal():
    """Bonsai-like sapling rising from a teal seed crystal, with shards and a few flowers."""
    V = {}
    def put(x, y, z, k): V[(x, y, z)] = k
    # seed crystal (2x2 core, stepped tip) at the centre
    for y in range(0, 3):
        for (x, z) in ((7, 7), (8, 7), (7, 8), (8, 8)):
            put(x, y, z, 'U' if (x == 7) else 'u' if z == 7 else 'T')
    put(7, 3, 7, 'Y'); put(8, 3, 8, 'u'); put(7, 3, 8, 'U')
    # side shards leaning out
    for (x, y, z, k) in [(6, 0, 9, 'T'), (6, 1, 9, 'u'), (5, 0, 10, 't'), (10, 0, 6, 'T'), (10, 1, 6, 'U'), (11, 0, 5, 't'),
                         (9, 0, 10, 'u'), (10, 0, 10, 't')]:
        put(x, y, z, k)
    # trunk growing out of the crystal tip, slight lean
    for (x, y, z) in [(8, 3, 7), (8, 4, 7), (8, 5, 7), (8, 6, 8), (8, 7, 8), (9, 7, 8), (7, 7, 8)]:
        put(x, y, z, 'W' if y < 6 else 'X')
    put(9, 8, 8, 'W'); put(6, 7, 8, 'W')
    # canopy: two layered leaf clouds
    import math
    for x in range(4, 13):
        for y in range(7, 12):
            for z in range(4, 13):
                d = ((x - 8) ** 2) / 9 + ((y - 9) ** 2) / 3.2 + ((z - 8) ** 2) / 9
                if d <= 1.0 and (x, y, z) not in V:
                    # light from top/left: vary tone by height and side
                    k = 'Q' if y >= 11 or (y == 10 and x < 8) else 'N' if y >= 9 else 'M' if x + z < 17 else 'L'
                    if (x * 7 + y * 3 + z * 5) % 11 == 0: k = 'l'
                    V[(x, y, z)] = k
    # flowers on the moss
    for (x, z, k) in [(4, 5, 'p'), (12, 11, 'y'), (11, 4, 'f'), (4, 11, 'y')]:
        put(x, 0, z, 'M'); put(x, 1, z, k)
    return V

def sculpt_terraform():
    """Copper theodolite on a wooden tripod with a hanging plumb bob."""
    V = {}
    def put(x, y, z, k): V[(x, y, z)] = k
    # tripod legs from three feet stepping in towards (8, 7, 8)
    feet = [(4, 4), (12, 5), (8, 12)]
    for fx, fz in feet:
        for y in range(0, 7):
            t = y / 7
            x = round(fx + (8 - fx) * t); z = round(fz + (8 - fz) * t)
            put(x, y, z, 'W' if y % 3 else 'X')
        put(fx, 0, fz, 'w')
    # head plate
    for x in range(7, 10):
        for z in range(7, 10): put(x, 7, z, 'K')
    put(8, 7, 8, 'c')
    # instrument body (telescope) along X with a teal lens
    for x in range(6, 11):
        for y in (8, 9):
            put(x, y, 8, 'C' if y == 9 else 'c')
    put(5, 8, 8, 'T'); put(5, 9, 8, 'U'); put(11, 8, 8, 'k'); put(11, 9, 8, 'K')
    put(8, 10, 8, 'H'); put(7, 10, 8, 'C'); put(9, 10, 8, 'C')     # top ridge
    put(8, 11, 8, 'V')                                            # verdigris sight
    # bubble level on the side
    put(8, 8, 9, 'Y'); put(7, 8, 9, 'J'); put(9, 8, 9, 'J')
    # plumb line and bob hanging from the head's underside at a free spot
    for y in range(3, 7): put(9, y, 7, 'I')
    put(9, 2, 7, 'C'); put(9, 1, 7, 'k')
    return V

def elements(V, oy=14):
    """Merge equal-colour runs along X; emit only faces not hidden by a neighbour voxel."""
    els = []; seen = set()
    for (x, y, z), k in sorted(V.items(), key=lambda t: (t[0][1], t[0][2], t[0][0])):
        if (x, y, z) in seen: continue
        x1 = x
        while (x1 + 1, y, z) in V and V[(x1 + 1, y, z)] == k and (x1 + 1, y, z) not in seen: x1 += 1
        for xx in range(x, x1 + 1): seen.add((xx, y, z))
        u, v = texel(k); uv = [u, v, u + 1, v + 1]
        faces = {}
        def open_(nx, ny, nz): return (nx, ny, nz) not in V
        if any(open_(xx, y + 1, z) for xx in range(x, x1 + 1)): faces['up'] = {'uv': uv, 'texture': '#voxel'}
        if any(open_(xx, y - 1, z) for xx in range(x, x1 + 1)) and y > 0: faces['down'] = {'uv': uv, 'texture': '#voxel'}
        if any(open_(xx, y, z - 1) for xx in range(x, x1 + 1)): faces['north'] = {'uv': uv, 'texture': '#voxel'}
        if any(open_(xx, y, z + 1) for xx in range(x, x1 + 1)): faces['south'] = {'uv': uv, 'texture': '#voxel'}
        if open_(x - 1, y, z): faces['west'] = {'uv': uv, 'texture': '#voxel'}
        if open_(x1 + 1, y, z): faces['east'] = {'uv': uv, 'texture': '#voxel'}
        if faces:
            els.append({'from': [x, oy + y, z], 'to': [x1 + 1, oy + y + 1, z + 1], 'faces': faces})
    return els

if __name__ == '__main__':
    palette_texture().save('grids/altar_voxels.png')
    spec = importlib.util.spec_from_file_location('altars', 'draw_altars.py'); A = importlib.util.module_from_spec(spec); spec.loader.exec_module(A)
    spec2 = importlib.util.spec_from_file_location('spr', 'draw_altar_sprites.py'); S = importlib.util.module_from_spec(spec2); spec2.loader.exec_module(S)
    load = lambda n: Image.open(f'grids/{n}.png').convert('RGBA')
    shots = []
    for kind, top, sculpt in (('renewal', 'renewal_altar_top', sculpt_renewal), ('terraform', 'terraform_altar_top', sculpt_terraform)):
        e = []
        A.box(e, 'plinth', [1, 0, 1], [15, 3, 15], 'stone', top='plinth_top', bottom='plinth_top')
        A.box(e, 'column', [3, 3, 3], [13, 11, 13], 'stone')
        A.box(e, 'top', [1, 11, 1], [15, 14, 15], 'stone', top='top', bottom='plinth_top')
        vox = elements(sculpt())
        e += vox
        tex = {'stone': load('altar_stone'), 'plinth_top': load('altar_plinth_top'), 'top': load(top), 'voxel': load('altar_voxels')}
        # the preview needs all faces keyed; hidden ones are simply never hit
        img = S.preview(e, tex, W=320, H=320, oy=250).resize((640, 640), Image.NEAREST); shots.append(img)
        model = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                 'textures': {'particle': 'entrelumen:block/altar_stone', 'stone': 'entrelumen:block/altar_stone',
                              'plinth_top': 'entrelumen:block/altar_plinth_top', 'top': 'entrelumen:block/' + top,
                              'voxel': 'entrelumen:block/altar_voxels'}, 'elements': e}
        json.dump(model, open(f'model-{kind}_altar.json', 'w'), indent=1)
        print(kind, 'elements', len(e), 'voxels', len(sculpt()))
    out = Image.new('RGB', (1280, 640)); out.paste(shots[0], (0, 0)); out.paste(shots[1], (640, 0)); out.save('preview-altars-voxel.png')
