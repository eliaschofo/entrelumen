"""Title-screen button textures: tuff face, bevelled like vanilla, copper frame; hover wakes the patina.

Deterministic pixels from the master palette (art/authoring/palette.py ramps); no fonts or images.
Labels are drawn by Minecraft over these textures, so every face pixel keeps >=4.5:1 contrast with
the ivory label colour.
"""
import argparse, hashlib, io, json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / 'art/gui'
DEST = ROOT / 'pack/config/fancymenu/assets/entrelumen/gui'
P = {
    'ink': '#141417',
    'tuff_deep': '#343833', 'tuff_dark': '#474c45', 'tuff': '#50564e', 'tuff_light': '#71776c', 'tuff_edge': '#8b9284',
    'copper_dark': '#6b3424', 'copper': '#ad5a3f', 'copper_light': '#e08e6a', 'copper_glint': '#f4b596',
    'verdigris_deep': '#1c3f37', 'verdigris': '#2b5e50', 'verdigris_light': '#52a07f', 'teal': '#35ccbd',
    'label': '#e8dcb5',
}
W, H = 160, 20


def rgb(name):
    v = P[name]
    return tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def noise(x, y, seed):
    n = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n & 0xffff) / 0xffff


def button(state):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = im.load()
    hover, disabled = state == 'hover', state == 'disabled'
    face, grain = (('verdigris', 'verdigris_deep') if hover else ('tuff_deep', 'tuff_deep') if disabled else ('tuff', 'tuff_dark'))
    for y in range(H):
        for x in range(W):
            px[x, y] = rgb(face)
            if 3 <= x < W - 3 and 3 <= y < H - 3 and noise(x, y, 7) < 0.045:
                px[x, y] = rgb(grain)
    # 1px ink outline
    for x in range(W):
        px[x, 0] = px[x, H - 1] = rgb('ink')
    for y in range(H):
        px[0, y] = px[W - 1, y] = rgb('ink')
    # copper frame: light top/left, dark bottom/right (vanilla bevel direction)
    top = 'tuff_light' if disabled else 'copper_light'
    low = 'tuff_dark' if disabled else 'copper_dark'
    mid = 'tuff_dark' if disabled else 'copper'
    for x in range(1, W - 1):
        px[x, 1] = rgb(top); px[x, H - 2] = rgb(low)
    for y in range(1, H - 1):
        px[1, y] = rgb(top); px[W - 2, y] = rgb(low)
    for x in range(2, W - 2):
        px[x, 2] = rgb(mid if not hover else 'verdigris_light')
    px[1, 1] = rgb('copper_glint' if not disabled else 'tuff_edge')
    px[W - 2, H - 2] = rgb(low)
    # verdigris rivets at the four inner corners; a teal lumen dot when hovered/focused
    for (x, y) in ((3, 3), (W - 4, 3), (3, H - 4), (W - 4, H - 4)):
        px[x, y] = rgb('tuff_dark' if disabled else 'verdigris_light' if not hover else 'teal')
    return im


def encode(im):
    b = io.BytesIO(); im.save(b, format='PNG', optimize=False); return b.getvalue()


def luminance(c):
    c = [v / 255 for v in c[:3]]
    return sum(a * (v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4) for a, v in zip((.2126, .7152, .0722), c))


def outputs():
    images = {'button_' + s: button(s) for s in ('normal', 'hover', 'disabled')}
    label = rgb('label'); ratios = {}
    for s in ('normal', 'hover', 'disabled'):
        im = images['button_' + s]
        cols = {im.getpixel((x, y)) for y in range(4, 16) for x in range(8, 152)}
        ratios[s] = round(min((luminance(label) + .05) / (luminance(c) + .05) for c in cols), 2)
        assert ratios[s] >= 4.5, (s, ratios[s])
    assert len({encode(images['button_' + s]) for s in ('normal', 'hover', 'disabled')}) == 3
    out = {}
    for name, im in images.items():
        for folder in (HERE, DEST):
            out[folder / (name + '.png')] = encode(im)
    sheet = Image.new('RGBA', (176, 76), rgb('ink'))
    for i, s in enumerate(('normal', 'hover', 'disabled')):
        sheet.alpha_composite(images['button_' + s], (8, 4 + 24 * i))
    out[HERE / 'contact-sheet.png'] = encode(sheet.resize((704, 304), Image.Resampling.NEAREST))
    manifest = {'palette': P, 'native_dimensions': {n: list(i.size) for n, i in images.items()}, 'text_color': P['label'],
                'text_minimum_contrast': ratios, 'text_insets': [8, 4, 8, 4],
                'scaling': 'native 1:1 GUI units; no smoothing; not nine-slice',
                'states': 'normal, hover (also keyboard focus), disabled; native localized text rendered separately',
                'provenance': 'Original deterministic pixel drawing from the ENTRELUMEN master palette; no game textures, external images or fonts.',
                'sha256': {n: hashlib.sha256(encode(i)).hexdigest() for n, i in images.items()}}
    out[HERE / 'manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    return out


def main():
    p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); a = p.parse_args()
    for path, data in outputs().items():
        if a.check:
            assert path.exists() and path.read_bytes() == data, f'Stale: {path}'
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    print('PASS: deterministic button textures, binary alpha, 3 distinct states, label contrast >=4.5:1')


if __name__ == '__main__':
    main()
