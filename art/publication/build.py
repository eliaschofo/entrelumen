"""CurseForge cover and avatar: native pixel art composed from the pack's own sources, exported x4 nearest.

Cover: a 300x120 crop of the native title scene (PixelLab, see art/menu), the copper wordmark at 1:1
and an original 5x7 bitmap tagline. Avatar: the 16x16 Atlas item at x5 on a copper-framed plate.
No fonts, filtering or fractional scaling.
"""
import argparse
import importlib.util
import io
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ART = HERE.parent
FONT = {
 'A': ['01110', '10001', '10001', '11111', '10001', '10001', '10001'],
 'D': ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
 'E': ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
 'G': ['01111', '10000', '10000', '10111', '10001', '10001', '01111'],
 'H': ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
 'I': ['11111', '00100', '00100', '00100', '00100', '00100', '11111'],
 'L': ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
 'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
 'N': ['10001', '11001', '11001', '10101', '10011', '10011', '10001'],
 'O': ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
 'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
 'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
 'S': ['01111', '10000', '10000', '01110', '00001', '00001', '11110'],
 'T': ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
 'V': ['10001', '10001', '10001', '10001', '10001', '01010', '00100'],
 ' ': ['00000'] * 7,
}
PARCH = (232, 220, 181)
SHADOW = (10, 24, 28)
COPPER = (173, 90, 63)
COPPER_LIGHT = (224, 142, 106)
PLATE = (20, 20, 23)


def wordmark():
    spec = importlib.util.spec_from_file_location('entrelumen_wordmark', ART / 'menu/wordmark.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.build()


def lettering(draw, text, x, y, colour):
    for char in text:
        for row, cells in enumerate(FONT[char]):
            for column, bit in enumerate(cells):
                if bit == '1':
                    draw.point((x + column, y + row), fill=colour)
        x += 6


def text_width(text):
    return len(text) * 6 - 1


def png(image):
    out = io.BytesIO(); image.save(out, format='PNG', optimize=False); return out.getvalue()


def item(name):
    from importlib.util import spec_from_file_location, module_from_spec
    spec = spec_from_file_location('entrelumen_build_art', ART / 'build_art.py')
    module = module_from_spec(spec); spec.loader.exec_module(module)
    return module.read_grid(ART / 'grids/item' / f'{name}.txt')


def avatar():
    image = Image.new('RGBA', (100, 100), PLATE + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 3, 96, 96), outline=COPPER)
    draw.line((4, 4, 95, 4), fill=COPPER_LIGHT); draw.line((4, 4, 4, 95), fill=COPPER_LIGHT)
    for (x, y) in ((3, 3), (94, 3), (3, 94), (94, 94)):
        draw.rectangle((x, y, x + 2, y + 2), fill=(82, 160, 127))
    atlas = item('atlas').resize((80, 80), Image.Resampling.NEAREST)
    image.alpha_composite(atlas, (10, 10))
    return image.convert('RGB')


def cover(locale):
    scene = Image.open(ART / 'menu/title-scene.png').convert('RGBA')
    image = scene.crop((84, 4, 384, 124))
    draw = ImageDraw.Draw(image)
    mark = wordmark()
    image.alpha_composite(mark, (8, 10))
    subtitle = 'THE LIVING ATLAS' if locale == 'en' else 'EL ATLAS VIVO'
    stage = 'IN DEVELOPMENT' if locale == 'en' else 'EN DESARROLLO'
    x = 12
    lettering(draw, subtitle, x + 1, 51, SHADOW)
    lettering(draw, subtitle, x, 50, PARCH)
    w = text_width(stage)
    draw.rectangle((8, 102, 8 + w + 7, 113), fill=PLATE, outline=COPPER)
    lettering(draw, stage, 12, 105, PARCH)
    draw.rectangle((0, 0, 299, 119), outline=PLATE)
    return image.convert('RGB')


def outputs():
    results = {}
    for name, native in [('avatar-400', avatar()), ('cover-en', cover('en')), ('cover-es', cover('es'))]:
        enlarged = native.resize((native.width * 4, native.height * 4), Image.Resampling.NEAREST)
        results[HERE / (name + '.png')] = png(enlarged)
        results[HERE / (name + '-native.png')] = png(native)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, data in outputs().items():
        if args.check:
            with Image.open(path) as actual, Image.open(io.BytesIO(data)) as wanted:
                if actual.convert('RGB').tobytes() != wanted.convert('RGB').tobytes():
                    raise SystemExit('Stale publication pixel art: ' + path.name)
        else:
            path.write_bytes(data)
    print('PASS: native 300x120 covers and 100x100 avatar from pack sources, exported x4 nearest.')


if __name__ == '__main__':
    main()
