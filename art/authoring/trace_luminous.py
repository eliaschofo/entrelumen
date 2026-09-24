"""Trace vanilla sprites (Elias: 'trazá sprites vanilla') and repaint them with ENTRELUMEN ramps by
luminance rank per region: tools/armour/ingot from diamond/gold textures, Luminosities as ethereal
fire from the soul campfire flame animation."""
import sys, colorsys, json
sys.path.insert(0, '.')
from PIL import Image
from palette import RAMPS as R
from grid import dump

V = 'ref/vanilla/assets/minecraft/textures/'
def h(s): return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))
GOLD = [h(c) for c in ['#3a2a0e', '#6e5220', '#a8862f', '#d9b95a', '#f1dc98', '#fff6dc', '#ffffff']]
HANDLE = [h(c) for c in [R['teal'][0], R['teal'][1], R['teal'][2], R['glass'][2], R['glass'][3], R['glass'][4]]]
TEALV = [h(R['teal'][2]), h(R['teal'][3]), h(R['teal'][4])]

def lum(c): return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]

def rank_map(colours, ramp):
    """Map each distinct colour to a ramp entry by luminance rank (keeps the source's shading structure)."""
    cs = sorted(set(colours), key=lum)
    if len(cs) == 1: return {cs[0]: ramp[len(ramp) // 2]}
    return {c: ramp[round(i * (len(ramp) - 1) / (len(cs) - 1))] for i, c in enumerate(cs)}

def region_of(c):
    r, g, b = [v / 255 for v in c]
    hh, s, v = colorsys.rgb_to_hsv(r, g, b)
    hue = hh * 360
    if s > 0.2 and 15 <= hue <= 50 and v < 0.75: return 'stick'
    return 'head'

def recolor(src, ramps, vein_rank=None):
    im = Image.open(src).convert('RGBA'); w, hgt = im.size; px = im.load()
    groups = {}
    for y in range(hgt):
        for x in range(w):
            p = px[x, y]
            if p[3] < 128: continue
            groups.setdefault(region_of(p[:3]) if 'stick' in ramps else 'head', []).append(p[:3])
    maps = {g: rank_map(cols, ramps[g]) for g, cols in groups.items()}
    out = Image.new('RGBA', (w, hgt), (0, 0, 0, 0)); op = out.load()
    head = groups.get('head', [])
    head_sorted = sorted(set(head), key=lum)
    vein = None
    if vein_rank is not None and len(head_sorted) >= 5:
        mids = head_sorted[1:-2]
        vein = min(mids, key=lambda c: head.count(c))   # rarest mid-tone becomes the teal vein
    for y in range(hgt):
        for x in range(w):
            p = px[x, y]
            if p[3] < 128: continue
            g = region_of(p[:3]) if 'stick' in ramps else 'head'
            c = maps[g][p[:3]]
            if vein is not None and g == 'head' and p[:3] == vein: c = TEALV[1]
            op[x, y] = c + (255,)
    return out

def save_grid(im, name):
    im.save(f'grids/{name}.png'); dump(f'grids/{name}.png', f'grids/{name}.txt')

LUM = {
    'engineering': [R['copper'][0], R['copper'][2], R['copper'][4], R['copper'][6], '#fff4e8'],
    'arcane': [R['violet'][0], R['violet'][2], R['violet'][3], R['violet'][5], '#fbf5ff'],
    'nature': [R['leaf'][0], R['leaf'][2], R['leaf'][4], R['leaf'][5], '#f6ffe8'],
    'exploration': [R['sky'][0], R['sky'][1], R['sky'][3], R['sky'][4], '#f4faff'],
    'logistics': [R['teal'][1], R['teal'][2], R['teal'][4], R['teal'][5], '#ffffff'],
    'habitation': [R['brass'][1], R['brass'][3], R['brass'][4], R['brass'][5], '#fffdf0'],
}

def ethereal_fire():
    src = Image.open(V + 'block/soul_campfire_fire.png').convert('RGBA')
    n = src.height // 16
    for key, ramp in LUM.items():
        base = [h(c) for c in ramp]
        def mix(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
        ramp_rgb = [base[0], mix(base[0], base[1], .5), base[1], base[2], mix(base[2], base[3], .5), base[3], base[4]]
        # one colour map for all frames so the flicker keeps a stable palette
        cols = [p[:3] for p in src.getdata() if p[3] >= 128]
        cmap = rank_map(cols, ramp_rgb)
        for i in range(n):
            fr = src.crop((0, 16 * i, 16, 16 * i + 16)); out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
            cx, cy, r = 7.5, 10.0, 5.2
            for y in range(16):
                for x in range(16):
                    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                    p = fr.getpixel((x, y))
                    if d <= r:
                        # round fireball body: discrete radial ramp, flickered by the vanilla flame brightness
                        flick = 0 if p[3] < 128 else (1 if lum(p[:3]) > 170 else 0)
                        t = d / r
                        idx = 6 if t < 0.28 else 5 if t < 0.5 else 4 if t < 0.68 else 3 if t < 0.84 else 2
                        idx = min(6, idx + flick) if t > 0.6 else idx
                        out.putpixel((x, y), ramp_rgb[idx] + (255,))
                    elif p[3] >= 128 and y < cy:
                        # tongues of ethereal flame rising from the orb (traced from the vanilla flame)
                        out.putpixel((x, y), cmap[p[:3]] + (255,))
            # rim: darkest ramp on the lower half of the orb edge so it reads round, lit from above
            for y in range(16):
                for x in range(16):
                    if out.getpixel((x, y))[3] and y > cy:
                        if any(not (0 <= x + a < 16 and 0 <= y + b < 16) or out.getpixel((x + a, y + b))[3] == 0 for a, b in ((1, 0), (-1, 0), (0, 1))):
                            out.putpixel((x, y), ramp_rgb[1] + (255,))
            save_grid(out, f'luminosity_{key}__f{i}')
    return n

if __name__ == '__main__':
    tools = {'sword': 'diamond_sword', 'pickaxe': 'diamond_pickaxe', 'axe': 'diamond_axe', 'shovel': 'diamond_shovel', 'hoe': 'diamond_hoe'}
    for ours, van in tools.items():
        save_grid(recolor(V + f'item/{van}.png', {'head': GOLD, 'stick': HANDLE}), f'luminous_{ours}')
    for ours in ('helmet', 'chestplate', 'leggings', 'boots'):
        save_grid(recolor(V + f'item/diamond_{ours}.png', {'head': GOLD}, vein_rank=-3), f'luminous_{ours}')
    # ingot: gold ingot traced, then an occasional light sweep
    base = recolor(V + 'item/gold_ingot.png', {'head': GOLD})
    opaque = [(x, y) for y in range(16) for x in range(16) if base.getpixel((x, y))[3]]
    for f in range(8):
        fr = base.copy(); k = -12 + f * 4
        for (x, y) in opaque:
            if (x - y) in (k, k + 1) and lum(fr.getpixel((x, y))) > 90:
                fr.putpixel((x, y), (h('#ffffff') if (x - y) == k + 1 else GOLD[5]) + (255,))
        save_grid(fr, f'luminous_ingot__f{f}')
    for layer in (1, 2):
        out = recolor(V + f'models/armor/diamond_layer_{layer}.png', {'head': GOLD}, vein_rank=-3)
        out.save(f'grids/luminous_layer_{layer}.png')
    frames = ethereal_fire()
    json.dump({'fire_frames': frames}, open('trace-meta.json', 'w'))
    # previews
    names = [f'luminosity_{k}__f0' for k in LUM] + ['luminous_ingot__f0'] + [f'luminous_{n}' for n in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots')]
    S = 8; sheet = Image.new('RGBA', (8 * (16 * S + 8) + 8, 2 * (16 * S + 8) + 8), (139, 139, 139, 255))
    for i, n in enumerate(names):
        sheet.alpha_composite(Image.open(f'grids/{n}.png').convert('RGBA').resize((16 * S, 16 * S), Image.NEAREST), (8 + (i % 8) * (16 * S + 8), 8 + (i // 8) * (16 * S + 8)))
    sheet.save('luminous-sheet.png')
    anim = Image.new('RGBA', (frames * 104 + 8, 6 * 104 + 8), (139, 139, 139, 255))
    for r, k in enumerate(LUM):
        for f in range(frames):
            anim.alpha_composite(Image.open(f'grids/luminosity_{k}__f{f}.png').convert('RGBA').resize((96, 96), Image.NEAREST), (4 + f * 104, 4 + r * 104))
    anim.save('luminous-anim.png')
    armor = Image.new('RGBA', (2 * 64 * 6 + 24, 32 * 6 + 16), (80, 80, 80, 255))
    for i in (1, 2):
        armor.alpha_composite(Image.open(f'grids/luminous_layer_{i}.png').convert('RGBA').resize((384, 192), Image.NEAREST), (8 + (i - 1) * 392, 8))
    armor.save('luminous-armor-layers.png')
    print('ok', frames)
