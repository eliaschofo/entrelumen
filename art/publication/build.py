"""Publication pixel art: processed AI-derived scene, bitmap lettering, nearest x4."""
import argparse
import importlib.util
import io
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('entrelumen_pixel', HERE.parent / 'build_pixel_identity.py')
pixel = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pixel)
P = pixel.P


def png(image):
    out = io.BytesIO()
    image.save(out, format='PNG', optimize=False)
    return out.getvalue()


def svg(image, factor=4):
    # Lossless source: horizontal runs of native pixels, no SVG text or smoothing.
    w, h = image.size
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w*factor}" height="{h*factor}" viewBox="0 0 {w} {h}" shape-rendering="crispEdges">']
    for y in range(h):
        x = 0
        while x < w:
            color = image.getpixel((x, y))
            end = x + 1
            while end < w and image.getpixel((end, y)) == color:
                end += 1
            fill = '#%02X%02X%02X' % color
            parts.append(f'<rect x="{x}" y="{y}" width="{end-x}" height="1" fill="{fill}"/>')
            x = end
    return ('\n'.join(parts) + '\n</svg>\n').encode()


def avatar():
    image = Image.new('RGB', (100, 100), P['ink'])
    draw = ImageDraw.Draw(image)
    for x in range(10, 100, 10):
        draw.line((x, 7, x, 92), fill=P['map'])
        draw.line((7, x, 92, x), fill=P['map'])
    draw.rectangle((5, 5, 94, 94), outline=P['copper'])
    draw.rectangle((8, 8, 91, 91), outline=P['map_hi'])
    for x, y in ((48, 4), (48, 94), (4, 48), (94, 48)):
        draw.rectangle((x, y, x+2, y+2), fill=P['patina'])
    # Render the original 32px item SVG at an exact integer factor.
    colors = {'#172E32': P['ink'], '#254449': P['map_hi'], '#F6E8B5': P['sun']}
    for node in ET.fromstring((HERE.parent/'svg/item/atlas.svg').read_text(encoding='utf-8')):
        a = node.attrib
        color = colors.get(a['fill'], a['fill'])
        if node.tag.endswith('rect'):
            x, y, w, h = [int(a[k]) for k in ('x', 'y', 'width', 'height')]
            draw.rectangle((18+2*x, 16+2*y, 18+2*(x+w)-1, 16+2*(y+h)-1), fill=color)
        elif node.tag.endswith('polygon'):
            draw.polygon([(18+2*int(x), 16+2*int(y)) for x,y in (p.split(',') for p in a['points'].split())], fill=color)
    return image


def background():
    # Integrator supplies the approved processed native source; never regenerate a scene.
    path = HERE.parent / 'menu/title-background-source.png'
    with Image.open(path) as source:
        if source.width not in (384, 480) or source.height < 120:
            raise ValueError('Expected approved native scene width 384 or 480 and height >=120')
        if source.mode not in ('RGB', 'RGBA', 'P'):
            raise ValueError('Expected palette/RGB scene')
        rgba = source.convert('RGBA')
        if set(rgba.getchannel('A').getextrema()) != {255}:
            raise ValueError('Publication background must be opaque')
        return rgba.convert('RGB')


def cover(locale):
    image = Image.new('RGB', (300, 120), P['ink'])
    # Crop at native resolution: dome is a quiet editorial illustration on the right.
    scene = background()
    left = scene.width * 2 // 3
    top = max(0, (scene.height - 120) // 2)
    left = min(left, scene.width - 120)
    image.paste(scene.crop((left, top, left + 120, top + 120)), (180, 0))
    draw = ImageDraw.Draw(image)
    for x in range(12, 180, 12):
        draw.line((x, 0, x, 119), fill=P['map'])
    for y in range(12, 120, 12):
        draw.line((0, y, 179, y), fill=P['map'])
    draw.rectangle((178, 0, 180, 119), fill=P['copper'])
    logo = pixel.logo()
    image.paste(logo, (0, 0), logo)
    subtitle = 'THE LIVING ATLAS' if locale == 'en' else 'EL ATLAS VIVO'
    stage = 'IN DEVELOPMENT' if locale == 'en' else 'EN DESARROLLO'
    concept = 'AI-DERIVED CONCEPT' if locale == 'en' else 'CONCEPTO CON IA'
    pixel.lettering(draw, subtitle, (180-(len(subtitle)*6-1))//2, 67, 'patina')
    draw.rectangle((17, 81, 162, 94), fill=P['map_hi'])
    pixel.lettering(draw, stage, (180-(len(stage)*6-1))//2, 84, 'ivory')
    pixel.lettering(draw, concept, 12, 107, 'sand')
    draw.rectangle((0, 0, 299, 119), outline=P['rust'])
    return image


def outputs():
    results = {}
    palette = {tuple(bytes.fromhex(c[1:])) for c in P.values()}
    source = background()
    palette |= {color for count, color in source.getcolors(source.width * source.height)}
    for name, native in [('avatar-400', avatar()), ('cover-en', cover('en')), ('cover-es', cover('es'))]:
        assert native.mode == 'RGB'
        colors = {color for count, color in native.getcolors(native.width * native.height)}
        assert colors <= palette, f'Out-of-palette pixels in {name}: {colors-palette}'
        enlarged = native.resize((native.width*4, native.height*4), Image.Resampling.NEAREST)
        # Every exported 4x4 block must equal its original single pixel.
        for dy in range(4):
            for dx in range(4):
                assert all(enlarged.getpixel((4*x+dx,4*y+dy)) == native.getpixel((x,y))
                           for y in range(native.height) for x in range(native.width))
        results[HERE/(name+'.png')] = png(enlarged)
        results[HERE/(name+'.svg')] = svg(native)
        results[HERE/(name+'-native.png')] = png(native)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, data in outputs().items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise SystemExit('Stale publication pixel art: '+path.name)
        else:
            path.write_bytes(data)
    print('PASS: native300x120 covers/native100x100 avatar, RGB palette, nearest4x blocks, bitmap-only letters; deterministic outputs.')


if __name__ == '__main__':
    main()
