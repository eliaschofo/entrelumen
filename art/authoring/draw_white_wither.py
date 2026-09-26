"""The Envés boss placeholder: a white, luminous Wither (Elias, 26/9), painted on the Wither model's UV.

It reuses the vanilla structure the way draw_villager_characters.py does:
- the vanilla layer is read only as a UV and shading map (from the pinned client JAR, never
  committed);
- every body colour is repainted from our ivory ramp by luminance rank;
- the light pixels (eyes, teeth, the ribs' sheen) become gold glow.

Writes the base texture and an emissive layer that the renderer draws full-bright:
companion/.../textures/entity/white_wither/{white_wither,white_wither_glow}.png, plus a preview.

    python art/authoring/draw_white_wither.py
"""
import os
import sys
import zipfile
from io import BytesIO

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
CLIENT_JAR = 'G:/curseforge/Install/versions/1.21.1/1.21.1.jar'
OUT = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures', 'entity', 'white_wither')
IVORY = ['#8f8676', '#aea591', '#c9c1ad', '#ded7c6', '#eee9dc', '#faf7ee', '#ffffff']
GOLD = ['#b5842a', '#e0b048', '#f6d77a', '#fff1b8']
RIB = ['#9c7a3a', '#c9a052', '#e8c46e']


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def h(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))


def vanilla(name):
    with zipfile.ZipFile(CLIENT_JAR) as z:
        return Image.open(BytesIO(z.read('assets/minecraft/textures/entity/wither/%s.png' % name))).convert('RGBA')


def paint():
    src = vanilla('wither')
    px = src.load()
    body = sorted({px[x, y][:3] for y in range(64) for x in range(64)
                   if px[x, y][3] >= 128 and lum(px[x, y]) < 110 and not bluish(px[x, y])}, key=lum)
    base = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    bp, gp = base.load(), glow.load()
    for y in range(64):
        for x in range(64):
            p = px[x, y]
            if p[3] < 128:
                continue
            L = lum(p)
            if bluish(p):                                           # the ribs' cold sheen becomes gold trim
                c = h(RIB[min(2, int((L - 60) / 40))]) if L > 60 else h(RIB[0])
                bp[x, y] = c + (255,)
                gp[x, y] = h(GOLD[1]) + (170,)
            elif L >= 110:                                          # eyes and teeth: the light inside
                c = h(GOLD[min(3, int((L - 110) / 36))])
                bp[x, y] = c + (255,)
                gp[x, y] = c + (255,)
            else:
                i = body.index(p[:3])
                bp[x, y] = h(IVORY[round(i * (len(IVORY) - 1) / max(1, len(body) - 1))]) + (255,)
    return base, glow


def bluish(p):
    r, g, b = p[:3]
    return b > r + 12 and g > r + 6


def main():
    os.makedirs(OUT, exist_ok=True)
    base, glow = paint()
    base.save(os.path.join(OUT, 'white_wither.png'))
    glow.save(os.path.join(OUT, 'white_wither_glow.png'))
    sheet = Image.new('RGBA', (64 * 8 * 2 + 24, 64 * 8 + 16), (40, 40, 52, 255))
    sheet.alpha_composite(base.resize((512, 512), Image.NEAREST), (8, 8))
    lit = base.copy()
    lit.alpha_composite(glow)
    sheet.alpha_composite(lit.resize((512, 512), Image.NEAREST), (528, 8))
    sheet.save(os.path.join(os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.')), 'white_wither_preview.png'))
    print('wrote', OUT)


if __name__ == '__main__':
    main()
