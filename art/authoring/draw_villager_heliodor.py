"""Heliodor clothing for Solsticio's villagers (story bible: cream and copper tunics, teal details).

Traced from the vanilla plains villager type layer and repainted by luminance rank: the robe in
cream parchment, its seams and hems in copper, and a teal sash across the body. Front and back faces
are made mirror-symmetric (the vanilla coat is open on one side). Written as the
villager type texture `entrelumen:textures/entity/villager/type/heliodor.png`; registering the
`entrelumen:heliodor` villager type and giving it to Solsticio's villagers is code, not art.

    python art/authoring/draw_villager_heliodor.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

ROOT = os.path.join(HERE, '..', '..')
SRC = 'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/ref/vanilla/assets/minecraft/textures/entity/villager/type/plains.png'
OUT = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures', 'entity',
                   'villager', 'type', 'heliodor.png')


def h(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def main():
    im = Image.open(SRC).convert('RGBA')
    px = im.load()
    colours = sorted({px[x, y][:3] for y in range(im.height) for x in range(im.width) if px[x, y][3] >= 128}, key=lum)
    ramp = [h(R['copper'][1]), h(R['copper'][3])] + [h(c) for c in R['parch'][2:]]
    cmap = {c: ramp[round(i * (len(ramp) - 1) / (len(colours) - 1))] for i, c in enumerate(colours)}
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    op = out.load()
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if p[3] < 128:
                continue
            op[x, y] = cmap[p[:3]] + (255,)
    # mirror-symmetric front and back faces (the vanilla coat is open on one side)
    for (u0, v0, w, hgt) in ((22, 26, 8, 12), (36, 26, 8, 12), (6, 44, 8, 18), (20, 44, 8, 18)):
        for y in range(v0, v0 + hgt):
            for k in range(w // 2):
                op[u0 + w - 1 - k, y] = op[u0 + k, y]
    # a teal sash at the waist, all the way round the body (faces at v 26..37, u 16..43) and over
    # the robe where it has cloth (faces at v 44..61, u 0..27)
    for (u0, u1, v0, whole) in ((16, 44, 33, True), (0, 28, 51, False)):
        for x in range(u0, u1):
            for y, shade in ((v0, R['teal'][3]), (v0 + 1, R['teal'][2])):
                if whole or op[x, y][3]:
                    op[x, y] = h(shade) + (255,)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    base = Image.open(os.path.join(os.path.dirname(SRC), '..', 'villager.png')).convert('RGBA')
    sheet = Image.new('RGBA', (2 * 256 + 36, 268), (139, 139, 139, 255))
    sheet.alpha_composite(out.resize((256, 256), Image.NEAREST), (12, 6))
    combo = base.copy()
    combo.alpha_composite(out)
    sheet.alpha_composite(combo.resize((256, 256), Image.NEAREST), (280, 6))
    sheet.save(os.path.join(os.environ.get('TEMP', '.'), 'villager_heliodor.png'))
    print(OUT)


if __name__ == '__main__':
    main()
