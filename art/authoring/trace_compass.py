"""Heliodor compass: the 32 vanilla compass frames traced; the grey case becomes aged copper and the
needle takes the destination dimension's colour (grey when there is no trace)."""
import sys, colorsys
sys.path.insert(0, '.')
from PIL import Image
from palette import RAMPS as R

V = 'ref/vanilla/assets/minecraft/textures/item/'
OUT = 'C:/Users/elias/Documents/Codex/2026-09-12/h/outputs/entrelumen/art/compass/'
def h(s): return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))
def lum(c): return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
COPPER = [h(c) for c in ['#1c1412', R['copper'][0], R['copper'][1], R['copper'][2], R['copper'][3], R['copper'][4], R['copper'][5], R['copper'][6], '#fff0e6']]
NEEDLE = {   # (dark, bright) needle tones per destination
    'overworld': (R['teal'][2], R['teal'][4]),
    'nether': (R['crimson'][2], R['crimson'][4]),
    'end': (R['violet'][2], R['violet'][4]),
    'aether': (R['brass'][3], R['brass'][5]),
    'twilight': (R['leaf'][2], R['leaf'][4]),
    'other': (R['sky'][1], R['sky'][3]),
    'none': (R['iron'][2], R['iron'][3]),
}

def is_red(c):
    hh, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in c))
    return s > 0.6 and (hh < 0.05 or hh > 0.95)

if __name__ == '__main__':
    import os
    os.makedirs(OUT, exist_ok=True)
    frames = [Image.open(V + f'compass_{i:02d}.png').convert('RGBA') for i in range(32)]
    greys = sorted({p[:3] for f in frames for p in f.getdata() if p[3] and not is_red(p[:3])}, key=lum)
    cmap = {c: COPPER[round(i * (len(COPPER) - 1) / (len(greys) - 1))] for i, c in enumerate(greys)}
    reds = sorted({p[:3] for f in frames for p in f.getdata() if p[3] and is_red(p[:3])}, key=lum)
    for dim, (dark, bright) in NEEDLE.items():
        nd = {reds[0]: h(dark)} if reds else {}
        for r in reds[1:]: nd[r] = h(bright)
        for i, f in enumerate(frames):
            out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
            for y in range(16):
                for x in range(16):
                    p = f.getpixel((x, y))
                    if p[3] < 128: continue
                    out.putpixel((x, y), (nd[p[:3]] if is_red(p[:3]) else cmap[p[:3]]) + (255,))
            out.save(OUT + f'{dim}_{i:02d}.png')
    # preview: frame 16 (north) and 20 for every destination
    S = 8; sheet = Image.new('RGBA', (len(NEEDLE) * (16 * S + 8) + 8, 2 * (16 * S + 8) + 8), (139, 139, 139, 255))
    for c, dim in enumerate(NEEDLE):
        for r, fr in enumerate((16, 20)):
            sheet.alpha_composite(Image.open(OUT + f'{dim}_{fr:02d}.png').resize((16 * S, 16 * S), Image.NEAREST), (8 + c * (16 * S + 8), 8 + r * (16 * S + 8)))
    sheet.save('compass-sheet.png')
    print('ok', len(greys), len(reds))
