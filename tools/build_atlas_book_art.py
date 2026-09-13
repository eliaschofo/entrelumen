"""Install the versioned PixelLab book texture without re-encoding or resizing.

The original generation and Minecraft reference are documented in
art/gui/atlas_book.json. This checks integrity, not aesthetic acceptance.
"""
from pathlib import Path
import argparse
import hashlib
import io
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def build():
    data = (ROOT / 'art/gui/atlas_book.png').read_bytes()
    receipt = json.loads((ROOT / 'art/gui/atlas_book.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(data).hexdigest() == receipt['sha256'], 'Book source changed without provenance update'
    with Image.open(io.BytesIO(data)) as image:
        assert image.size == (300, 210)
        colors = set(image.convert('RGBA').get_flattened_data())
        assert {color[3] for color in colors} == {0, 255}
        assert len({color for color in colors if color[3]}) == receipt['opaque_colors']
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = build()
    for base in ('companion/src/main/resources', 'pack/resourcepacks/entrelumen'):
        target = ROOT / base / 'assets/entrelumen/textures/gui/atlas_book.png'
        if args.check:
            assert target.read_bytes() == data, target
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    print('PASS native PixelLab book texture bytes and provenance; game QA is separate')


if __name__ == '__main__':
    main()
