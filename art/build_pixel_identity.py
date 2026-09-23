"""Menu identity exports: native pixel-art scenes (integer nearest) and the active logo.

Sources:
- art/menu/title-scene.png   243x139 native pixel art: the 12 September title illustration Elias liked,
  converted by PixelLab /image-to-pixelart-pro (51-colour output, receipt in
  art/candidates/pixellab-v2/pp_pixelish/), used unmodified.
- art/menu/scene-observatory.png 384x216 alternative scene (PixelLab /generate-image-v2, bg_observatory),
  kept for the CurseForge cover and as a title alternative.
- art/menu/loading-scene.png 352x199 crop of PixelLab bg_valley: the provider returned a white
  border, which is cut away; pixels are otherwise unmodified.
- art/menu/logo-classic.png  active 180x64 logo (Atlas emblem and bitmap lettering from 12 September).
- art/menu/wordmark.py       alternative copper block wordmark (letters age across Minecraft's copper
  oxidation stages), kept for comparison and the CurseForge cover.
Exports are integer nearest enlargements; nothing is filtered, blurred or re-quantized.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from png_equivalence import preserve_verified_encoding  # noqa: E402

OUT = ROOT / 'art/menu'
SCENES = {'title': ('title-scene.png', (243, 139), 'pp_pixelish', 8),
          'loading': ('loading-scene.png', (352, 199), 'bg_valley', 5)}
MAX_SCENE_COLOURS = 64
LOGO = 'classic'   # 'classic' (logo-classic.png) or 'wordmark' (wordmark.py)


def wordmark():
    spec = importlib.util.spec_from_file_location('entrelumen_wordmark', OUT / 'wordmark.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.build()


def png(im):
    out = io.BytesIO(); im.save(out, format='PNG', optimize=False); return out.getvalue()


def build():
    outputs = {}
    scenes = {}
    for key, (name, size, job, scale) in SCENES.items():
        scene = Image.open(OUT / name).convert('RGB')
        if scene.size != size:
            raise ValueError(f'{name} must be {size}')
        colours = scene.getcolors(size[0] * size[1])
        if colours is None or len(colours) > MAX_SCENE_COLOURS:
            raise ValueError(f'{name} exceeds {MAX_SCENE_COLOURS} colours')
        if not (ROOT / 'art/candidates/pixellab-v2' / job / 'receipt.json').is_file():
            raise ValueError(f'missing PixelLab receipt for {name}')
        scenes[key] = scene
        outputs[OUT / f'{key}-background.png'] = png(scene.resize((size[0] * scale, size[1] * scale), Image.Resampling.NEAREST))
    logo = Image.open(OUT / 'logo-classic.png').convert('RGBA') if LOGO == 'classic' else wordmark()
    if logo.width > 256 or logo.height > 64:
        raise ValueError('wordmark must fit 256x64 GUI units at 1:1')
    outputs[OUT / 'logo.png'] = png(logo)
    outputs = {path: preserve_verified_encoding(path, data) for path, data in outputs.items()}
    files = {p.name: hashlib.sha256(b).hexdigest() for p, b in outputs.items()}
    manifest = {'schemaVersion': 3,
                'method': 'Native PixelLab scenes used as generated (loading scene cropped to remove a provider border); '
                          f'logo: {LOGO}',
                'scenes': {k: {'source': f'art/menu/{v[0]}', 'size': list(v[1]), 'receipt': f'art/candidates/pixellab-v2/{v[2]}/receipt.json',
                               'colours': len(scenes[k].getcolors(v[1][0] * v[1][1]))} for k, v in SCENES.items()},
                'exportScale': {k: v[3] for k, v in SCENES.items()}, 'logoSize': list(logo.size), 'sampling': 'integer nearest; binary alpha',
                'files': files}
    outputs[OUT / 'pixel-manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    outputs[OUT / 'pixel-art-approved.json'] = (json.dumps({'sha256': {n: files[n] for n in ('title-background.png', 'loading-background.png', 'logo.png')},
        'verification': 'Integrity only: native size, colour budget, binary alpha and integer nearest export checked by build_pixel_identity.py. Aesthetic acceptance is a separate in-game review.'}, indent=2) + '\n').encode()
    return outputs, scenes, logo


def validate(outputs, scenes, logo):
    for key, scene in scenes.items():
        scale = SCENES[key][3]
        export = Image.open(io.BytesIO(outputs[OUT / f'{key}-background.png'])).convert('RGB')
        if export.tobytes() != scene.resize((scene.width * scale, scene.height * scale), Image.Resampling.NEAREST).tobytes():
            raise ValueError('non-integer enlargement: ' + key)
    alphas = {logo.getpixel((x, y))[3] for y in range(logo.height) for x in range(logo.width)}
    if not alphas <= {0, 255}:
        raise ValueError('logo alpha must be binary')


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--check', action='store_true'); args = parser.parse_args()
    outputs, scenes, logo = build(); validate(outputs, scenes, logo)
    for path, data in outputs.items():
        if args.check:
            if not path.is_file():
                raise SystemExit('Missing menu artwork: ' + str(path))
            if path.suffix == '.png':
                with Image.open(path) as actual, Image.open(io.BytesIO(data)) as wanted:
                    if actual.convert('RGBA').tobytes() != wanted.convert('RGBA').tobytes():
                        raise SystemExit('Stale menu artwork: ' + str(path))
            elif path.read_bytes() != data:
                raise SystemExit('Stale menu artwork: ' + str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    print(f'PASS: native scenes {", ".join(f"{s.width}x{s.height}" for s in scenes.values())} exported by integer nearest; '
          f'{LOGO} logo {logo.width}x{logo.height}; binary alpha. In-game review separate.')


if __name__ == '__main__':
    main()
