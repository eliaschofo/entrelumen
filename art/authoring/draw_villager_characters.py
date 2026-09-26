"""The four named characters of Solsticio: profession layers over the Heliodor clothing.

References (AGENTS.md design hook), inspected 2026-09-25 composited over the vanilla plains type at
4x: the cleric's stole, the farmer's straw hat, the toolsmith's apron and the cartographer's strap
and monocle (vanilla textures/entity/villager/profession/*.png, 64x64). Each character reuses one
vanilla structure, repainted from our ramps by luminance rank, and adds its own sign:

  Aurelia (mayor):   copper stole with gold trim, a gold seal on the chest, a gold circlet
  Terra (inventor):  copper leather apron, brass goggles, a mechanical glove over the crossed arms
  Juan (gardener):   a green straw hat, a canvas apron with a flower in its pocket
  Bodhi (priest):    a teal stole with gold trim

Front and back faces are mirror-symmetric (pack rule). Writes
companion/.../textures/entity/villager/profession/{mayor,inventor,gardener,priest}.png and a preview.

    python art/authoring/draw_villager_characters.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

ROOT = os.path.join(HERE, '..', '..')
VANILLA = 'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/ref/vanilla/assets/minecraft/textures/entity/villager'
OUT = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures', 'entity',
                   'villager', 'profession')
GOLD = ['#6e5220', '#a8862f', '#d9b95a', '#f1dc98', '#fff6dc']
FACES = ((22, 26, 8, 12), (36, 26, 8, 12), (6, 44, 8, 18), (20, 44, 8, 18), (8, 8, 8, 10), (24, 8, 8, 10))


def h(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def recolour(src, ramp, keep_gold=False):
    """Map every opaque colour of a vanilla layer onto `ramp` by luminance rank."""
    im = Image.open(src).convert('RGBA')
    px = im.load()
    cols = sorted({px[x, y][:3] for y in range(64) for x in range(64) if px[x, y][3] >= 128}, key=lum)
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    op = out.load()
    for y in range(64):
        for x in range(64):
            p = px[x, y]
            if p[3] < 128:
                continue
            r, g, b = p[:3]
            if keep_gold and r > 150 and g > 110 and b < 90:           # vanilla gold trim stays gold
                op[x, y] = h(GOLD[min(4, int(lum(p) / 52))]) + (255,)
                continue
            i = cols.index(p[:3])
            op[x, y] = h(ramp[round(i * (len(ramp) - 1) / max(1, len(cols) - 1))]) + (255,)
    return out


def symmetric(im):
    op = im.load()
    for (u0, v0, w, hgt) in FACES:
        for y in range(v0, v0 + hgt):
            for k in range(w // 2):
                op[u0 + w - 1 - k, y] = op[u0 + k, y]
    return im


def put(im, x, y, c):
    im.putpixel((x, y), h(c) + (255,))


def mayor():
    im = recolour(VANILLA + '/profession/cleric.png', [R['copper'][1], R['copper'][2], R['copper'][3], R['copper'][4]], keep_gold=True)
    for (dx, dy, c) in ((1, 0, GOLD[2]), (2, 0, GOLD[2]), (0, 1, GOLD[1]), (1, 1, GOLD[4]), (2, 1, GOLD[3]),
                        (3, 1, GOLD[1]), (1, 2, GOLD[2]), (2, 2, GOLD[2])):   # the seal on the chest, body and robe
        for (u0, v0) in ((23, 29), (7, 47)):
            put(im, u0 + dx, v0 + dy, c)
    for x in range(0, 32):                                           # a gold circlet round the head
        if x in range(0, 32):
            put(im, x, 9, GOLD[2] if x % 2 else GOLD[3])
    for x in (11, 12):
        put(im, x, 9, GOLD[4])
    return symmetric(im)


def priest():
    return symmetric(recolour(VANILLA + '/profession/cleric.png', [R['teal'][0], R['teal'][1], R['teal'][2], R['teal'][3]], keep_gold=True))


def gardener():
    hat = recolour(VANILLA + '/profession/farmer.png', [R['leaf'][1], R['leaf'][2], R['leaf'][3], R['leaf'][4], R['leaf'][5]])
    hp = hat.load()
    for y in range(20, 64):                                          # only the hat, not the straw on the robe
        for x in range(64):
            hp[x, y] = (0, 0, 0, 0)
    apron = recolour(VANILLA + '/profession/toolsmith.png', ['#5b4a2c', '#7a6440', '#9a845a', '#b8a276', '#d4c29a'])
    # keep the toolsmith apron only on the robe and body, not the eyepatch or tools on the arms
    ap = apron.load()
    for y in range(64):
        for x in range(64):
            if not (y >= 26 and x < 44) and not (y >= 44):
                ap[x, y] = (0, 0, 0, 0)
    hat.alpha_composite(apron)
    for (x, y, c) in ((9, 50, '#f2a3c7'), (10, 50, '#f6e27a'), (11, 50, '#f2a3c7'), (10, 49, '#f2a3c7'), (10, 51, R['leaf'][3])):
        put(hat, x, y, c)
    return symmetric(hat)


def inventor():
    apron = recolour(VANILLA + '/profession/toolsmith.png', [R['copper'][0], R['copper'][1], R['copper'][2], R['copper'][3], R['copper'][4]])
    ap = apron.load()
    for y in range(64):
        for x in range(64):
            if y < 20:
                ap[x, y] = (0, 0, 0, 0)
    for (x, y) in ((9, 12), (10, 12), (13, 12), (14, 12)):           # brass goggles over the eyes
        put(apron, x, y, R['brass'][3])
    for (x, y) in ((9, 13), (10, 13), (13, 13), (14, 13)):
        put(apron, x, y, R['glass'][3])
    for x in range(8, 16):
        if apron.getpixel((x, 12))[3] == 0:
            put(apron, x, 12, R['wood'][1])
    # the mechanical glove: brass plates with rivets on the crossed arms (u 40..63, v 22..29 and 38..47)
    for (u0, v0, u1, v1) in ((40, 38, 56, 42), (44, 22, 52, 26)):
        for y in range(v0, v1):
            for x in range(u0, u1):
                c = R['brass'][2] if (x + y) % 3 else R['brass'][3]
                if (x - u0) % 4 == 0 and (y - v0) % 2 == 0:
                    c = R['iron'][4]
                put(apron, x, y, c)
    return symmetric(apron)


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {'mayor': mayor(), 'inventor': inventor(), 'gardener': gardener(), 'priest': priest()}
    base = Image.open(VANILLA + '/villager.png').convert('RGBA')
    typ = Image.open(os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures',
                                  'entity', 'villager', 'type', 'heliodor.png')).convert('RGBA')
    sheet = Image.new('RGBA', (4 * 264, 264), (60, 60, 70, 255))
    for i, (name, im) in enumerate(made.items()):
        im.save(os.path.join(OUT, name + '.png'))
        comp = base.copy()
        comp.alpha_composite(typ)
        comp.alpha_composite(im)
        sheet.alpha_composite(comp.resize((256, 256), Image.NEAREST), (i * 264 + 4, 4))
    sheet.save(os.path.join(os.environ.get('TEMP', '.'), 'villager_characters_preview.png'))
    # a front view of each character: head, nose and robe as the model shows them
    front = Image.new('RGBA', (4 * 150, 330), (38, 44, 60, 255))
    for i, (name, im) in enumerate(made.items()):
        comp = base.copy()
        comp.alpha_composite(typ)
        comp.alpha_composite(im)
        fig = Image.new('RGBA', (12, 30), (0, 0, 0, 0))
        fig.alpha_composite(comp.crop((8, 8, 16, 18)), (2, 0))       # head front
        fig.alpha_composite(comp.crop((6, 44, 14, 62)), (2, 10))     # robe front
        fig.alpha_composite(comp.crop((24, 2, 26, 6)), (5, 4))       # nose
        hat = im.crop((32, 0, 64, 20))
        front.alpha_composite(fig.resize((120, 300), Image.NEAREST), (i * 150 + 15, 15))
    front.save(os.path.join(os.environ.get('TEMP', '.'), 'villager_characters_front.png'))
    print('wrote', ', '.join(made))


if __name__ == '__main__':
    main()
