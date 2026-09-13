"""Filter an original image into ENTRELUMEN's tracked 480x270 menu master.

Explicit --source is required. The original is hashed but never copied to the pack.
Normal menu builds use only the resulting master and need no original or network.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter, ImageOps, __version__

OUT = Path(__file__).resolve().parent / 'menu'

def filtered(source):
    with Image.open(source) as original:
        image = ImageOps.fit(original.convert('RGB'), (960, 540), method=Image.Resampling.LANCZOS)
    image = image.filter(ImageFilter.MedianFilter(3)).resize((480, 270), Image.Resampling.BOX)
    image = ImageEnhance.Color(image).enhance(1.06)
    return image.quantize(colors=96, method=Image.Quantize.MEDIANCUT,
                          kmeans=3, dither=Image.Dither.NONE).convert('RGB')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    image = filtered(args.source)
    OUT.mkdir(parents=True, exist_ok=True)
    master = OUT / 'pixel-master.png'
    image.save(master, optimize=False)
    provenance = {
        'schemaVersion': 1,
        'sourceKind': 'ImageGen original; not distributed in pack',
        'sourceSha256': hashlib.sha256(args.source.read_bytes()).hexdigest(),
        'masterSha256': hashlib.sha256(master.read_bytes()).hexdigest(),
        'pillowVersion': __version__,
        'algorithm': ['RGB', 'ImageOps.fit 960x540 LANCZOS center', 'MedianFilter 3',
                      'resize 480x270 BOX', 'Color 1.06', 'quantize 96 MEDIANCUT kmeans=3 dither=NONE', 'RGB PNG'],
        'seed': None,
        'seedExplanation': 'No random sampling is used; Pillow version is recorded for reproducibility.',
        'colorsUsed': len(image.getcolors(480 * 270))
    }
    (OUT / 'pixel-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
    print('Filtered master and provenance written. Run build_pixel_identity.py to export.')

if __name__ == '__main__':
    main()
