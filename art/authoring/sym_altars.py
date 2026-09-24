"""Symmetric altars (Elias: everything symmetric). Textures use mirrored noise; voxel decor is built in one
octant and mirrored to full D4 symmetry about the block centre (voxel x <-> 15-x, z <-> 15-z, x <-> z)."""
import sys, json, importlib.util
sys.path.insert(0, '.')
from PIL import Image
from draw_items import Canvas
from palette import RAMPS as R
from draw_blocks import TUFF, noise
import voxel_altars as VA

def mnoise(x, y, seed):
    """Mirrored in both axes and across the diagonal (4-fold symmetric texture noise)."""
    a, b = min(x, 15 - x), min(y, 15 - y)
    a, b = min(a, b), max(a, b)
    return noise(a, b, seed)

def mnoise_lr(x, y, seed):   # left-right mirror only (for side faces)
    return noise(min(x, 15 - x), y, seed)

def stone():
    c = Canvas(dict(O='#141417', a=TUFF[0], b=TUFF[1], c=TUFF[2], d=TUFF[3], e=TUFF[4], f=TUFF[5],
                    k=R['copper'][1], K=R['copper'][3], L=R['copper'][4], H=R['copper'][5], v=R['verdigris'][2], V=R['verdigris'][3]))
    for y in range(16):
        for x in range(16): c.px(x, y, 'c' if mnoise_lr(x, y, 71) < 0.7 else 'd')
    for x in range(16):
        c.px(x, 2, 'H'); c.px(x, 3, 'K'); c.px(x, 4, 'k')
    for x in (3, 12): c.px(x, 3, 'V'); c.px(x, 4, 'v')        # mirrored verdigris rivets
    for x in (7, 8): c.px(x, 3, 'L')
    for x in range(16): c.px(x, 5, 'V' if x in (3, 7, 8, 12) else 'v')
    for x in range(16): c.px(x, 8, 'a'); c.px(x, 12, 'a')
    for (y0, y1, joints) in ((6, 7, (5, 10)), (9, 11, (3, 12))):   # joints mirrored about the column centre
        for y in range(y0, y1 + 1):
            for j in joints: c.px(j, y, 'a')
        for x in range(16):
            if x not in joints: c.px(x, y0, 'e')
    for x in range(16): c.px(x, 13, 'f'); c.px(x, 14, 'd'); c.px(x, 15, 'b')
    for x in (3, 7, 8, 12): c.px(x, 14, 'c'); c.px(x, 15, 'a')
    c.save('altar_stone')

def plinth_top():
    c = Canvas(dict(O='#141417', a=TUFF[1], b=TUFF[2], c=TUFF[3], d=TUFF[4], e=TUFF[5]))
    for y in range(16):
        for x in range(16): c.px(x, y, 'c' if mnoise(x, y, 73) < 0.8 else 'b')
    for i in range(1, 15): c.px(i, 1, 'e'); c.px(1, i, 'e'); c.px(i, 14, 'a'); c.px(14, i, 'a')
    c.save('altar_plinth_top')

def moss_top():
    c = Canvas(dict(O='#141417', a=R['leaf'][0], b=R['leaf'][1], c=R['leaf'][2], d=R['leaf'][3],
                    k=R['copper'][2], K=R['copper'][4], v=R['verdigris'][3]))
    for y in range(16):
        for x in range(16):
            n = mnoise(x, y, 63); c.px(x, y, 'c' if n < 0.5 else 'b' if n < 0.75 else 'd')
    for i in range(16):
        c.px(i, 0, 'K'); c.px(0, i, 'K'); c.px(i, 15, 'K'); c.px(15, i, 'K')
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)): c.px(x, y, 'v')
    c.save('renewal_altar_top')

def terra_top():
    c = Canvas(dict(O='#141417', a=TUFF[1], b=TUFF[2], c=TUFF[3], g=R['leaf'][3], s=R['wood'][2], S=R['wood'][3],
                    k=R['copper'][2], K=R['copper'][4], v=R['verdigris'][3]))
    for y in range(16):
        for x in range(16): c.px(x, y, 'b' if mnoise(x, y, 64) < 0.7 else 'c')
    for i in range(16):
        c.px(i, 0, 'K'); c.px(0, i, 'K'); c.px(i, 15, 'K'); c.px(15, i, 'K')
    # concentric strata: stone ring, dirt ring, grass core (symmetric version of the old bands)
    ring = lambda r, k: [c.px(x, y, k) for y in range(16) for x in range(16) if max(abs(x - 7.5), abs(y - 7.5)) == r]
    ring(5.5, 'a'); ring(4.5, 'S'); ring(3.5, 's')
    for y in range(5, 11):
        for x in range(5, 11): c.px(x, y, 'g')
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)): c.px(x, y, 'v')
    c.save('terraform_altar_top')

def d4(octant):
    """octant: {(x,y,z):key} with x,z in 0..7 (lower half); returns the full D4-symmetric voxel set."""
    V = {}
    for (x, y, z), k in octant.items():
        for (a, b) in ((x, z), (z, x)):
            for (p, q) in ((a, b), (15 - a, b), (a, 15 - b), (15 - a, 15 - b)):
                V[(p, y, q)] = k
    return V

def sculpt_renewal():
    o = {}
    # central 2x2 crystal pillar (voxels 7..8), stepped tip — only the (7,7) corner, mirrored
    for y in range(0, 4): o[(7, y, 7)] = 'U' if y < 3 else 'Y'
    o[(7, 4, 7)] = 'Y'
    # four cardinal shards (one written, mirrored to all sides)
    o[(7, 0, 5)] = 'u'; o[(7, 1, 5)] = 'U'; o[(7, 0, 4)] = 'T'
    # sapling crown floating above the crystal on a thin 2x2 stem
    for y in (5, 6): o[(7, y, 7)] = 'W'
    for x in range(4, 8):
        for z in range(4, 8):
            for y in range(7, 11):
                dx, dz, dy = x + 0.5 - 8, z + 0.5 - 8, y - 8.6
                if (dx * dx + dz * dz) / 11 + (dy * dy) / 2.6 <= 1.0:
                    o[(x, y, z)] = 'Q' if y >= 10 else 'N' if y == 9 else 'M' if y == 8 else 'L'
    # corner flowers on the moss
    o[(3, 0, 3)] = 'M'; o[(3, 1, 3)] = 'p'
    return d4(o)

def sculpt_terraform():
    o = {}
    # four legs stepping from the corners to the head (one leg written, mirrored)
    for y in range(0, 7):
        t = y / 7; p = round(3 + (7 - 3) * t)
        o[(p, y, p)] = 'W' if y % 3 else 'X'
    o[(3, 0, 3)] = 'w'
    # square head plate and a symmetric theodolite body with four teal lenses
    for x in range(6, 8):
        for z in range(6, 8): o[(x, 7, z)] = 'K'
    for x in range(6, 8):
        for z in range(6, 8):
            for y in (8, 9): o[(x, y, z)] = 'c' if y == 8 else 'C'
    o[(7, 8, 5)] = 'T'; o[(7, 9, 5)] = 'U'                 # lens on each side (mirrored)
    o[(7, 10, 7)] = 'H'; o[(7, 11, 7)] = 'V'               # sight on top
    # plumb bob hanging on the axis
    for y in range(3, 7): o[(7, y, 7)] = 'I'
    o[(7, 2, 7)] = 'C'; o[(7, 1, 7)] = 'k'
    return d4(o)

if __name__ == '__main__':
    stone(); plinth_top(); moss_top(); terra_top()
    VA.palette_texture().save('grids/altar_voxels.png')
    spec = importlib.util.spec_from_file_location('altars', 'draw_altars.py'); A = importlib.util.module_from_spec(spec); spec.loader.exec_module(A)
    spec2 = importlib.util.spec_from_file_location('spr', 'draw_altar_sprites.py'); S = importlib.util.module_from_spec(spec2); spec2.loader.exec_module(S)
    load = lambda n: Image.open(f'grids/{n}.png').convert('RGBA')
    shots = []
    for kind, top, sculpt in (('renewal', 'renewal_altar_top', sculpt_renewal), ('terraform', 'terraform_altar_top', sculpt_terraform)):
        e = []
        A.box(e, 'plinth', [1, 0, 1], [15, 3, 15], 'stone', top='plinth_top', bottom='plinth_top')
        A.box(e, 'column', [3, 3, 3], [13, 11, 13], 'stone')
        A.box(e, 'top', [1, 11, 1], [15, 14, 15], 'stone', top='top', bottom='plinth_top')
        V = sculpt()
        assert all(V.get((15 - x, y, z)) == k and V.get((z, y, x)) == k for (x, y, z), k in V.items()), 'not D4 symmetric'
        e += VA.elements(V)
        tex = {'stone': load('altar_stone'), 'plinth_top': load('altar_plinth_top'), 'top': load(top), 'voxel': load('altar_voxels')}
        shots.append(S.preview(e, tex, W=320, H=320, oy=250).resize((640, 640), Image.NEAREST))
        json.dump({'parent': 'minecraft:block/block', 'ambientocclusion': False,
                   'textures': {'particle': 'entrelumen:block/altar_stone', 'stone': 'entrelumen:block/altar_stone',
                                'plinth_top': 'entrelumen:block/altar_plinth_top', 'top': 'entrelumen:block/' + top,
                                'voxel': 'entrelumen:block/altar_voxels'}, 'elements': e},
                  open(f'model-{kind}_altar.json', 'w'), indent=1)
        print(kind, len(e), 'elements')
    out = Image.new('RGB', (1280, 640)); out.paste(shots[0], (0, 0)); out.paste(shots[1], (640, 0)); out.save('preview-altars-sym.png')
