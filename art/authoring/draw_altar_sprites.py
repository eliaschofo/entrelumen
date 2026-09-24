"""Hand-drawn crossed-plane sprites for the altar tops (vanilla sapling/amethyst language) and a
transparency-aware preview. Sprites keep 1 texel per model unit: a 10x11 drawing on a 10x11 plane."""
import sys, json, math
sys.path.insert(0, '.')
from PIL import Image
from palette import RAMPS as R
from grid import dump

def h(s): return tuple(int(s[i:i+2], 16) for i in (1, 3, 5))

SPROUT = [  # renewal: sapling growing out of a teal seed crystal (x 3..12, y 5..15 in the texture)
    "....dd....",
    "...dLLd...",
    ".ddLlLdd..",
    "dLLldlLLd.",
    ".dlLdLld..",
    "..dd sdd..",
    "....s.....",
    "...cWc....",
    "..cWTtc...",
    "..cTTtc...",
    "...ctc....",
]
SPROUT_P = {'d': R['leaf'][1], 'l': R['leaf'][3], 'L': R['leaf'][4], 's': R['wood'][2],
            'c': R['teal'][1], 'W': R['teal'][5], 'T': R['teal'][3], 't': R['teal'][2]}

SURVEY = [  # terraform: copper theodolite on a tripod with a plumb bob
    "..kKKk....",
    ".kHtTKk...",
    "..kKKk....",
    "...ww.....",
    "...w.w..q.",
    "..w..w..q.",
    "..w...w.q.",
    ".w....w.Q.",
    ".w.....wQ.",
    "w......wq.",
    "w.......w.",
]
SURVEY_P = {'k': R['copper'][1], 'K': R['copper'][3], 'H': R['copper'][5], 't': R['teal'][2], 'T': R['teal'][4],
            'w': R['wood'][2], 'q': R['iron'][3], 'Q': R['copper'][4]}


def sprite(rows, pal, name, x0=3, y0=5):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row.ljust(10)):
            if ch in pal:
                im.putpixel((x0 + x, y0 + y), h(pal[ch]) + (255,))
    # dark outline on transparent neighbours (right/bottom only, like vanilla sprites' shading)
    px = im.load(); base = im.copy()
    for y in range(16):
        for x in range(16):
            if base.getpixel((x, y))[3] == 0:
                continue
            for dx, dy in ((1, 0), (0, 1)):
                X, Y = x + dx, y + dy
                if 0 <= X < 16 and 0 <= Y < 16 and base.getpixel((X, Y))[3] == 0 and (dx, dy) == (0, 1) and y + 1 == y0 + len(rows):
                    pass
    im.save(f'grids/{name}.png'); dump(f'grids/{name}.png', f'grids/{name}.txt')
    return im


def cross(e, name, texture, x0=3, x1=13, y0=14, height=11, v0=5):
    """Two axis-aligned crossed planes centred on the block, uv sized to the plane (1 texel/unit)."""
    y1 = y0 + height
    uv = [x0, v0, x1, v0 + height]
    e.append({'name': name + ' NS', 'from': [x0, y0, 8], 'to': [x1, y1, 8], 'shade': False,
              'faces': {'north': {'uv': uv, 'texture': '#' + texture}, 'south': {'uv': uv, 'texture': '#' + texture}}})
    e.append({'name': name + ' EW', 'from': [8, y0, x0], 'to': [8, y1, x1], 'shade': False,
              'faces': {'east': {'uv': uv, 'texture': '#' + texture}, 'west': {'uv': uv, 'texture': '#' + texture}}})


def preview(elements, textures, W=320, H=300, oy=236):
    """Orthographic ray cast (camera (1,1,-1)), transparent texels let the ray continue."""
    im = Image.new('RGB', (W, H), '#101D1C')
    for py in range(H):
        for px in range(W):
            u = (px + .5 - 32) / 8; v = (py + .5 - oy) / 4
            origin = [u / 2 + 64, 64 - v / 2, u / 2 - 64]; d = [-1, -1, 1]
            hits = []
            for e in elements:
                near, far, face = -1e9, 1e9, None
                ok = True
                for ax in range(3):
                    a, b = e['from'][ax], e['to'][ax]
                    lo = (a - origin[ax]) / d[ax]; hi = (b - origin[ax]) / d[ax]
                    ent = ('east', 'up', 'north')[ax]
                    if lo > hi: lo, hi = hi, lo
                    if lo > near: near, face = lo, ent
                    far = min(far, hi)
                if near <= far + 1e-9 and near >= 0:
                    # planes: pick the face the element actually defines
                    if face not in e['faces']:
                        alt = {'east': 'west', 'north': 'south'}.get(face)
                        if e['from'][0] == e['to'][0]: face = 'east' if 'east' in e['faces'] else None
                        elif e['from'][2] == e['to'][2]: face = 'north' if 'north' in e['faces'] else None
                        else: face = None
                    if face: hits.append((near, e, face))
            for dist, e, face in sorted(hits, key=lambda t: t[0]):
                p = [origin[i] + dist * d[i] for i in range(3)]
                a, b = e['from'], e['to']; f = e['faces'][face]; tex = textures[f['texture'][1:]]
                span = lambda i: (b[i] - a[i]) or 1
                if face == 'up': tu, tv = (p[0] - a[0]) / span(0), (p[2] - a[2]) / span(2)
                elif face in ('north', 'south'): tu, tv = (p[0] - a[0]) / span(0), (b[1] - p[1]) / span(1)
                else: tu, tv = (p[2] - a[2]) / span(2), (b[1] - p[1]) / span(1)
                uv = f['uv']
                x = min(15, max(0, int(uv[0] + tu * (uv[2] - uv[0])))); y = min(15, max(0, int(uv[1] + tv * (uv[3] - uv[1]))))
                c = tex.getpixel((x, y))
                if len(c) == 4 and c[3] == 0: continue
                s = 1 if face == 'up' or e.get('shade') is False else {'north': .85, 'south': .85, 'east': .7, 'west': .7}[face]
                im.putpixel((px, py), tuple(int(ch * s) for ch in c[:3])); break
    return im


if __name__ == '__main__':
    sprite(SPROUT, SPROUT_P, 'altar_sprout')
    sprite(SURVEY, SURVEY_P, 'altar_survey')
    import importlib.util
    spec = importlib.util.spec_from_file_location('altars', 'draw_altars.py'); A = importlib.util.module_from_spec(spec); spec.loader.exec_module(A)
    load = lambda n: Image.open(f'grids/{n}.png').convert('RGBA')
    shots = []
    for kind, top, spr in (('renewal', 'renewal_altar_top', 'altar_sprout'), ('terraform', 'terraform_altar_top', 'altar_survey')):
        e = []
        A.box(e, 'plinth', [1, 0, 1], [15, 3, 15], 'stone', top='plinth_top', bottom='plinth_top')
        A.box(e, 'column', [3, 3, 3], [13, 11, 13], 'stone')
        A.box(e, 'top', [1, 11, 1], [15, 14, 15], 'stone', top='top', bottom='plinth_top')
        cross(e, 'decor', 'sprite')
        tex = {'stone': load('altar_stone'), 'plinth_top': load('altar_plinth_top'), 'top': load(top), 'sprite': load(spr)}
        img = preview(e, tex).resize((640, 600), Image.NEAREST); shots.append(img)
        model = {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'render_type': 'minecraft:cutout',
                 'textures': {'particle': 'entrelumen:block/altar_stone', 'stone': 'entrelumen:block/altar_stone',
                              'plinth_top': 'entrelumen:block/altar_plinth_top', 'top': 'entrelumen:block/' + top,
                              'sprite': 'entrelumen:block/' + spr}, 'elements': e}
        json.dump(model, open(f'model-{kind}_altar.json', 'w'), indent=2)
    out = Image.new('RGB', (1280, 600)); out.paste(shots[0], (0, 0)); out.paste(shots[1], (640, 0)); out.save('preview-altars.png')
    big = Image.new('RGBA', (2 * 16 * 12 + 36, 16 * 12 + 24), (139, 139, 139, 255))
    for i, n in enumerate(('altar_sprout', 'altar_survey')):
        big.alpha_composite(load(n).resize((192, 192), Image.NEAREST), (12 + i * 204, 12))
    big.save('sprites-altars.png')
