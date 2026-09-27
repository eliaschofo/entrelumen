"""The quest book v3 node shapes: sun, star, rosette, bookmark and shield (docs/design/quest-book-v3.md).

The format is FTB Quests' own. Reference inspected 27/9: `assets/ftbquests/textures/shapes/<shape>/` in
ftb-quests-neoforge-2101.1.34.jar (hexagon, gear, heart, diamond). Each shape has three 128x128 white
layers, all with `{"texture": {"blur": true}}`:
- background: a radial gradient, white centre fading to grey at the rim;
- outline: a ring about 4 px thick;
- shape: the solid mask.
FTB tints them by the quest's state and the theme, so these stay white and smooth, not pixel art.

Every shape is mirror-symmetric (pack rule), and the sun, star and rosette are D4-symmetric. Each has a
compact body that holds the 16 px icon at 2/3 of the node, like FTB's.

    python art/authoring/draw_quest_shapes.py        # writes the textures and a preview sheet
"""
import json
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
OUT = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets', 'ftbquests', 'textures', 'shapes')
S, K = 128, 4                      # texture size and supersampling
C = S * K / 2


def poly(points):
    im = Image.new('L', (S * K, S * K), 0)
    ImageDraw.Draw(im).polygon([(x * K, y * K) for x, y in points], fill=255)
    return im


def polar(fn, n=720, cx=64, cy=64):
    return [(cx + fn(t) * math.cos(t), cy + fn(t) * math.sin(t)) for t in (2 * math.pi * i / n for i in range(n))]


def sunburst():
    """A disc with twelve pointed rays: the chapter's summit."""
    pts = []
    for i in range(24):
        a = -math.pi / 2 + math.pi * i / 12
        r = 62 if i % 2 == 0 else 47
        pts.append((64 + r * math.cos(a), 64 + r * math.sin(a)))
    ray = poly(pts)
    disc = Image.new('L', (S * K, S * K), 0)
    ImageDraw.Draw(disc).ellipse([(64 - 48) * K, (64 - 48) * K, (64 + 48) * K, (64 + 48) * K], fill=255)
    return ImageChops.lighter(ray, disc)


def star():
    """A sparkle: four long points and four short ones round a round body, something hidden catching the light."""
    pts = []
    for i in range(16):
        a = -math.pi / 2 + math.pi * i / 8
        r = (63 if i % 4 == 0 else 50) if i % 2 == 0 else 40
        pts.append((64 + r * math.cos(a), 64 + r * math.sin(a)))
    body = Image.new('L', (S * K, S * K), 0)
    ImageDraw.Draw(body).ellipse([(64 - 42) * K, (64 - 42) * K, (64 + 42) * K, (64 + 42) * K], fill=255)
    return ImageChops.lighter(poly(pts), body)


def rosette():
    """A wax seal with a scalloped rim: a job stamped and done again."""
    return poly(polar(lambda t: 57 + 4 * math.cos(18 * t)))


def bookmark():
    """A ribbon bookmark with a swallowtail notch: a page worth marking."""
    return poly([(26, 6), (102, 6), (106, 10), (106, 122), (64, 96), (22, 122), (22, 10)])


def shield():
    """A heater shield: straight flanks, then a curve down to the point. The boss."""
    right = [(64, 8), (112, 8), (114, 12)]
    for i in range(0, 41):
        t = i / 40                               # the flank stays straight to mid-height, then turns in
        y = 12 + 110 * t
        x = 114 if y < 58 else 64 + 50 * math.cos((y - 58) / 64 * math.pi / 2) ** 0.9
        right.append((x, y))
    right.append((64, 122))
    left = [(128 - x, y) for (x, y) in reversed(right)]
    return poly(right + left)


SHAPES = {'el_sunburst': sunburst, 'el_star': star, 'el_rosette': rosette, 'el_tag': bookmark, 'el_shield': shield}


def layers(mask_big):
    mask = mask_big.resize((S, S), Image.LANCZOS)
    inner = mask_big.filter(ImageFilter.MinFilter(4 * K + 1)).resize((S, S), Image.LANCZOS)
    outline = ImageChops.subtract(mask, inner)
    grad = Image.new('L', (S, S), 0)
    gp = grad.load()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 64, y + 0.5 - 64) / 64
            gp[x, y] = int(255 - 105 * min(1.0, d) ** 1.4)
    def white(alpha):
        im = Image.new('RGBA', (S, S), (255, 255, 255, 0))
        im.putalpha(alpha)
        return im
    bg = Image.merge('RGBA', (grad, grad, grad, mask))
    return {'background': bg, 'outline': white(outline), 'shape': white(mask)}


def preview(made, path):
    tints = [(0xEC, 0xC8, 0x66), (0x72, 0xC4, 0x9E), (0xB9, 0x8C, 0xFF), (0xE8, 0xDC, 0xB5), (0xE8, 0x60, 0x6A)]
    sheet = Image.new('RGBA', (len(made) * 150 + 20, 330), (43, 46, 58, 255))
    for i, (name, lay) in enumerate(made.items()):
        x0 = 20 + i * 150
        sheet.alpha_composite(lay['shape'].resize((128, 128)), (x0, 20))
        tint = Image.new('RGBA', (S, S), tints[i] + (255,))
        bg = ImageChops.multiply(lay['background'], tint)
        node = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        node.alpha_composite(bg)
        node.alpha_composite(ImageChops.multiply(lay['outline'], Image.new('RGBA', (S, S), (40, 30, 20, 255))))
        sheet.alpha_composite(node, (x0, 170))
        small = node.resize((32, 32), Image.LANCZOS)
        sheet.alpha_composite(small, (x0 + 96, 280))
        sheet.alpha_composite(node.resize((48, 48), Image.LANCZOS), (x0 + 40, 270))
    sheet.save(path)


def main():
    made = {}
    for name, fn in SHAPES.items():
        lay = layers(fn())
        d = os.path.join(OUT, name)
        os.makedirs(d, exist_ok=True)
        for part, im in lay.items():
            im.save(os.path.join(d, part + '.png'))
            with open(os.path.join(d, part + '.png.mcmeta'), 'w', newline='\n') as f:
                f.write(json.dumps({'texture': {'blur': True}}, indent=2) + '\n')
        for part in ('shape',):                                  # mirror symmetry check on the mask
            px = lay[part].getchannel('A').load()
            asym = sum(abs(px[x, y] - px[S - 1 - x, y]) for y in range(S) for x in range(S // 2))
            assert asym < S * S, (name, asym)                      # under 2/255 per pixel: rasterising noise
        made[name] = lay
    preview(made, os.path.join(os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.')), 'quest_shapes_preview.png'))
    print('wrote', ', '.join(made))


if __name__ == '__main__':
    main()
