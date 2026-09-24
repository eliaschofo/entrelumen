"""Renewal and terraform altars: cuboid pedestal models + native 16x16 textures (draft)."""
import sys, json, importlib.util
sys.path.insert(0, '.')
from PIL import Image
from draw_items import Canvas
from palette import RAMPS as R
from draw_blocks import TUFF, noise

E = 'C:/Users/elias/Documents/Codex/2026-09-12/h/outputs/entrelumen/'
sys.path.insert(0, E + 'tools')
spec = importlib.util.spec_from_file_location('station', E + 'tools/build_survey_station_art.py')
station = importlib.util.module_from_spec(spec); spec.loader.exec_module(station)


def tuff_bricks(name, seed, band=None):
    c = Canvas(dict(O='#141417', a=TUFF[0], b=TUFF[1], c=TUFF[2], d=TUFF[3], e=TUFF[4],
                    v=R['verdigris'][2], V=R['verdigris'][3], k=R['copper'][2], K=R['copper'][4]))
    for y in range(16):
        for x in range(16):
            c.px(x, y, 'c' if noise(x, y, seed) < 0.6 else 'd' if noise(x, y, seed + 1) < 0.7 else 'b')
    for y in (3, 7, 11, 15):
        for x in range(16): c.px(x, y, 'a')
    for row, y0 in enumerate((0, 4, 8, 12)):
        off = 0 if row % 2 == 0 else 4
        for x in range(off, 16, 8):
            for y in range(y0, y0 + 3): c.px(x, y, 'a')
        for x in range(16):
            if c.get(x, y0) != 'a': c.px(x, y0, 'e')
    if band:
        for x in range(16):
            c.px(x, 7, 'k'); c.px(x, 8, 'K')
        for x in (2, 7, 12): c.px(x, 8, 'V'); c.px(x + 1, 7, 'v')
    c.save(name)


def moss_top(name, seed):
    c = Canvas(dict(O='#141417', a=R['leaf'][0], b=R['leaf'][1], c=R['leaf'][2], d=R['leaf'][3], e=R['leaf'][4],
                    y=R['straw'][3], p=R['violet'][4], w=R['parch'][5], t=R['teal'][3], T=R['teal'][5],
                    k=R['copper'][2], K=R['copper'][4]))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c.px(x, y, 'c' if n < 0.5 else 'b' if n < 0.75 else 'd')
    for i in range(16):
        c.px(i, 0, 'K'); c.px(0, i, 'K'); c.px(i, 15, 'k'); c.px(15, i, 'k')
    for (x, y, k) in [(3, 4, 'y'), (11, 3, 'p'), (4, 11, 'w'), (12, 12, 'y'), (2, 8, 'p'), (13, 7, 'w')]:
        c.px(x, y, k)
    # teal seed-crystal socket in the middle
    c.rect(6, 6, 9, 9, 'a'); c.px(7, 7, 't'); c.px(8, 8, 't'); c.px(7, 8, 'T'); c.px(8, 7, 't')
    c.save(name)


def terra_top(name, seed):
    c = Canvas(dict(O='#141417', a=TUFF[1], b=TUFF[2], c=TUFF[3], g=R['leaf'][3], G=R['leaf'][4], s=R['wood'][2],
                    S=R['wood'][3], k=R['copper'][2], K=R['copper'][4], L=R['copper'][5]))
    for y in range(16):
        for x in range(16): c.px(x, y, 'b' if noise(x, y, seed) < 0.7 else 'c')
    for i in range(16):
        c.px(i, 0, 'L'); c.px(0, i, 'L'); c.px(i, 15, 'k'); c.px(15, i, 'k')
    # strata inlay: grass / dirt / stone bands showing the layering it builds
    for x in range(3, 13):
        c.px(x, 4, 'G'); c.px(x, 5, 'g'); c.px(x, 6, 'S'); c.px(x, 7, 's'); c.px(x, 8, 's'); c.px(x, 9, 'c'); c.px(x, 10, 'a'); c.px(x, 11, 'a')
    for x in range(3, 13, 3): c.px(x, 3, 'K'); c.px(x, 12, 'K')
    c.save(name)


def instrument(name):
    c = Canvas(dict(O='#141417', k=R['copper'][2], K=R['copper'][3], L=R['copper'][4], H=R['copper'][6],
                    t=R['teal'][2], T=R['teal'][4], W=R['teal'][5]))
    c.rect(0, 0, 15, 15, 'K')
    for i in range(16): c.px(i, 0, 'H'); c.px(0, i, 'L'); c.px(i, 15, 'k'); c.px(15, i, 'k')
    c.rect(5, 5, 10, 10, 't'); c.rect(6, 6, 9, 9, 'T'); c.px(7, 7, 'W')
    c.save(name)


def crystal(name):
    # vertical facets: the 2px crystal samples columns 7-9, the shard 6-10
    c = Canvas(dict(O='#141417', t=R['teal'][1], T=R['teal'][2], g=R['teal'][3], G=R['teal'][4], W=R['teal'][5]))
    for y in range(16):
        for x in range(16):
            c.px(x, y, 'G' if x % 3 == 1 else 'g' if x % 3 == 2 else 'T')
    for (x, y) in [(7, 0), (7, 3), (6, 1), (9, 2), (7, 7), (8, 6)]:
        c.px(x, y, 'W')
    c.save(name)


def box(e, name, a, b, side, top=None, bottom=None, full_uv=False):
    """Vanilla-style auto UV: every face samples the texture region at its own block position,
    so all elements keep Minecraft's density of one texel per 1/16 block."""
    faces = {}
    x0, y0, z0 = a; x1, y1, z1 = b
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        mat = top if f == 'up' and top else bottom if f == 'down' and bottom else side
        v0 = max(0, 16 - y1); v1 = v0 + (y1 - y0)   # parts above the block keep 1 texel/unit
        if f in ('north', 'south'):
            uv = [x0, v0, x1, v1]
        elif f in ('east', 'west'):
            uv = [z0, v0, z1, v1]
        else:
            uv = [x0, z0, x1, z1]
        faces[f] = {'uv': uv, 'texture': '#' + mat}
    e.append({'name': name, 'from': a, 'to': b, 'faces': faces})


def altar(kind):
    e = []
    box(e, 'plinth', [1, 0, 1], [15, 3, 15], 'base')
    box(e, 'column', [3, 3, 3], [13, 11, 13], 'band')
    if kind == 'renewal':
        box(e, 'basin', [1, 11, 1], [15, 14, 15], 'base', top='top')
        box(e, 'seed crystal', [7, 14, 7], [9, 18, 9], 'crystal', top='crystal', full_uv=True)
        box(e, 'crystal shard', [9, 14, 6], [10, 16, 7], 'crystal', full_uv=True)
    else:
        box(e, 'table', [1, 11, 1], [15, 13, 15], 'base', top='top')
        box(e, 'level bar', [3, 13, 7], [13, 14, 9], 'instrument', full_uv=True)
        box(e, 'bubble vial', [6, 14, 7], [10, 15, 9], 'instrument', top='instrument', full_uv=True)
        box(e, 'plumb post', [12, 13, 12], [13, 17, 13], 'instrument', full_uv=True)
    return e


if __name__ == '__main__':
    tuff_bricks('altar_base', 61)
    tuff_bricks('altar_band', 62, band=True)
    moss_top('renewal_altar_top', 63)
    terra_top('terraform_altar_top', 64)
    instrument('altar_instrument')
    crystal('altar_crystal')
    load = lambda n: Image.open(f'grids/{n}.png').convert('RGB')
    for kind, top in (('renewal', 'renewal_altar_top'), ('terraform', 'terraform_altar_top')):
        els = altar(kind)
        tex = {'base': load('altar_base'), 'band': load('altar_band'), 'top': load(top),
               'crystal': load('altar_crystal'), 'instrument': load('altar_instrument')}
        station.preview(els, tex).resize((640, 560), Image.NEAREST).save(f'preview-{kind}-altar.png')
        model = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                 'textures': {'particle': 'entrelumen:block/altar_base', 'base': 'entrelumen:block/altar_base',
                              'band': 'entrelumen:block/altar_band', 'top': 'entrelumen:block/' + top,
                              'crystal': 'entrelumen:block/altar_crystal', 'instrument': 'entrelumen:block/altar_instrument'},
                 'elements': els}
        json.dump(model, open(f'model-{kind}_altar.json', 'w'), indent=2)
