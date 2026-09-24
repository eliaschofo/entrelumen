"""Four more symmetric voxel altars on the shared pedestal: ward, growth, slow-motion (time) and gathering."""
import sys, json, importlib.util
sys.path.insert(0, '.')
from PIL import Image
import voxel_altars as VA
import sym_altars as SA
from draw_items import Canvas
from palette import RAMPS as R
from draw_blocks import TUFF

d4 = SA.d4


def ward():
    o = {}
    for y in range(0, 7): o[(5, y, 5)] = 'c' if y % 3 else 'K'          # lantern cage corner posts
    for x in range(5, 8): o[(x, 7, 5)] = 'C'; o[(5, 7, x)] = 'C'         # top ring
    for x in range(6, 8):
        for z in range(6, 8): o[(x, 8, z)] = 'K'; o[(x, 0, z)] = 'k'     # cap and hearth plate
    o[(7, 9, 7)] = 'V'
    for y, k in ((1, 'T'), (2, 'u'), (3, 'U'), (4, 'Y')): o[(7, y, 7)] = k   # lumen flame core
    o[(6, 2, 7)] = 'T'; o[(6, 3, 7)] = 'u'
    return d4(o)


def growth():
    o = {}
    for y in range(0, 5): o[(7, y, 7)] = 'M' if y < 4 else 'N'           # central stalk
    for y, k in ((5, 'S'), (6, 'y'), (7, 'S'), (8, 'y')): o[(7, y, 7)] = k   # golden grain head
    o[(6, 6, 7)] = 'S'; o[(6, 7, 7)] = 'y'
    o[(6, 1, 6)] = 'L'; o[(5, 2, 5)] = 'M'; o[(4, 3, 4)] = 'N'            # leaves arching out
    o[(3, 0, 7)] = 'M'; o[(3, 1, 7)] = 'N'; o[(3, 2, 7)] = 's'            # sprouts on each side
    o[(2, 0, 2)] = 'M'; o[(2, 1, 2)] = 'r'                                # corner flowers
    return d4(o)


def time():
    o = {}
    for x in range(5, 8):
        for z in range(5, 8): o[(x, 0, z)] = 'K'; o[(x, 9, z)] = 'K'      # copper plates
    for y in range(1, 9): o[(5, y, 5)] = 'o' if y % 2 else 'O'            # brass corner rods
    for x in range(6, 8):
        for z in range(6, 8):
            o[(x, 7, z)] = 'A'; o[(x, 8, z)] = 'B'                        # upper glass bulb
            o[(x, 1, z)] = 'S'; o[(x, 2, z)] = 'a'                        # lower bulb with sand
    o[(7, 6, 7)] = 'A'; o[(7, 5, 7)] = 'y'; o[(7, 4, 7)] = 'y'; o[(7, 3, 7)] = 'A'   # neck, trickling sand
    o[(7, 10, 7)] = 'V'
    return d4(o)


def repose():
    o = {}
    # small symmetric coffer: 6x4x6 body with copper corners, darker lid, a teal clasp on every side
    for y in range(0, 5):
        for x in range(5, 8):
            for z in range(5, 8):
                if x == 5 or z == 5 or y in (0, 4):
                    edge = (x == 5 and z == 5)
                    o[(x, y, z)] = 'K' if edge else ('X' if y == 4 else 'W' if y in (0, 3) else 'X')
    for x in range(5, 8): o[(x, 2, 5)] = 'c'; o[(5, 2, x)] = 'c'        # copper band around the body
    o[(7, 2, 4)] = 'U'; o[(7, 3, 4)] = 'Y'                              # clasp (mirrored to all four sides)
    for x in range(6, 8):
        for z in range(6, 8): o[(x, 5, z)] = 'K'                         # lid cap
    o[(7, 6, 7)] = 'V'
    return d4(o)


def ring(c, r, k):
    for y in range(16):
        for x in range(16):
            if max(abs(x - 7.5), abs(y - 7.5)) == r: c.px(x, y, k)


def top(name, draw, extra):
    c = Canvas(dict(O='#141417', b=TUFF[2], c=TUFF[3], K=R['copper'][4], v=R['verdigris'][3], **extra))
    for y in range(16):
        for x in range(16): c.px(x, y, 'c' if SA.mnoise(x, y, 80 + len(name)) < 0.7 else 'b')
    for i in range(16): c.px(i, 0, 'K'); c.px(0, i, 'K'); c.px(i, 15, 'K'); c.px(15, i, 'K')
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)): c.px(x, y, 'v')
    draw(c)
    c.save(name)


def ward_top(c): ring(c, 5.5, 'x'); ring(c, 3.5, 'X')
def growth_top(c):
    for y in range(3, 13):
        for x in range(3, 13): c.px(x, y, 'g' if (x + y) % 2 else 'G')
    ring(c, 5.5, 'f')
def time_top(c):
    ring(c, 5.5, 'o'); ring(c, 1.5, 'o')
    for (x, y) in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)): c.px(x, y, 'P')
def repose_top(c): ring(c, 5.5, 'k'); ring(c, 4.5, 't')

TOPS = {
    'peace_altar_top': (ward_top, dict(x=R['teal'][1], X=R['teal'][2])),
    'growth_altar_top': (growth_top, dict(g=R['wood'][1], G=R['wood'][2], f=R['leaf'][3])),
    'time_altar_top': (time_top, dict(o=R['brass'][2], P=R['brass'][4])),
    'repose_altar_top': (repose_top, dict(k=R['copper'][2], t=R['teal'][2])),
}

if __name__ == '__main__':
    from grid import dump
    VA.palette_texture().save('grids/altar_voxels.png'); dump('grids/altar_voxels.png', 'grids/altar_voxels.txt')
    for n, (fn, extra) in TOPS.items(): top(n, fn, extra)
    spec = importlib.util.spec_from_file_location('altars', 'draw_altars.py'); A = importlib.util.module_from_spec(spec); spec.loader.exec_module(A)
    spec2 = importlib.util.spec_from_file_location('spr', 'draw_altar_sprites.py'); S = importlib.util.module_from_spec(spec2); spec2.loader.exec_module(S)
    load = lambda n: Image.open(f'grids/{n}.png').convert('RGBA')
    shots = []
    for kind, sculpt in (('peace', ward), ('growth', growth), ('time', time), ('repose', repose)):
        e = []
        A.box(e, 'plinth', [1, 0, 1], [15, 3, 15], 'stone', top='plinth_top', bottom='plinth_top')
        A.box(e, 'column', [3, 3, 3], [13, 11, 13], 'stone')
        A.box(e, 'top', [1, 11, 1], [15, 14, 15], 'stone', top='top', bottom='plinth_top')
        V = sculpt()
        assert all(V.get((15 - x, y, z)) == k and V.get((z, y, x)) == k for (x, y, z), k in V.items()), kind
        e += VA.elements(V)
        tex = {'stone': load('altar_stone'), 'plinth_top': load('altar_plinth_top'), 'top': load(f'{kind}_altar_top'), 'voxel': load('altar_voxels')}
        shots.append(S.preview(e, tex, W=320, H=320, oy=250).resize((480, 480), Image.NEAREST))
        for el in e: el.pop('name', None)
        json.dump({'parent': 'minecraft:block/block', 'ambientocclusion': False,
                   'textures': {'particle': 'entrelumen:block/altar_stone', 'stone': 'entrelumen:block/altar_stone',
                                'plinth_top': 'entrelumen:block/altar_plinth_top', 'top': f'entrelumen:block/{kind}_altar_top',
                                'voxel': 'entrelumen:block/altar_voxels'}, 'elements': e}, open(f'model-{kind}_altar.json', 'w'), indent=1)
    out = Image.new('RGB', (1920, 480))
    for i, s_ in enumerate(shots): out.paste(s_, (i * 480, 0))
    out.save('preview-altars-new.png')
    print('ok')
