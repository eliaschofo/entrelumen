"""Luminous content art (draft): six animated Luminosities, animated Luminous Ingot, diagonal tools
(vanilla convention), armor icons and 64x32 armor layers. Cream-white gold with teal veins, glass and light."""
import sys, math
sys.path.insert(0, '.')
from PIL import Image
from draw_items import Canvas
from palette import RAMPS as R
from grid import dump

GOLD = ['#5c4418', '#8a6a26', '#bf9a3c', '#e2c46a', '#f3e2a6', '#fff8e6']   # dark -> cream white
TEAL = [R['teal'][1], R['teal'][2], R['teal'][3], R['teal'][4], R['teal'][5]]
GLASS = [R['glass'][1], R['glass'][2], R['glass'][3], R['glass'][4]]
LUM = {   # discipline colour ramps (dark, mid, light, glow)
    'engineering': [R['copper'][1], R['copper'][3], R['copper'][5], R['copper'][6]],
    'arcane': [R['violet'][1], R['violet'][3], R['violet'][4], R['violet'][5]],
    'nature': [R['leaf'][1], R['leaf'][3], R['leaf'][4], R['leaf'][5]],
    'exploration': [R['sky'][0], R['sky'][2], R['sky'][3], R['sky'][4]],
    'logistics': [R['teal'][1], R['teal'][2], R['teal'][4], R['teal'][5]],
    'habitation': [R['brass'][1], R['brass'][3], R['brass'][4], R['brass'][5]],
}
FRAMES = 8

def save_frames(name, frames):
    """frames: list of Canvas -> grids/<name>__fN.txt/png and a preview strip."""
    for i, c in enumerate(frames):
        c.save(f'{name}__f{i}')
    strip = Image.new('RGBA', (16, 16 * len(frames)))
    for i in range(len(frames)):
        strip.alpha_composite(Image.open(f'grids/{name}__f{i}.png').convert('RGBA'), (0, 16 * i))
    strip.save(f'grids/{name}__strip.png')

def luminosity(key):
    d, m, l, g = LUM[key]
    frames = []
    for f in range(FRAMES):
        c = Canvas(dict(O='#141417', d=d, m=m, l=l, g=g, w='#fffdf6'))
        # faceted mote of light: a diamond core with four short rays (symmetric by nature)
        for y in range(16):
            for x in range(16):
                dd = abs(x - 7.5) + abs(y - 7.5)
                if dd <= 4.0: c.px(x, y, 'm')
                if dd <= 2.0: c.px(x, y, 'l')
        for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)): c.px(x, y, 'g')
        for i in (1, 2): c.px(7, 3 - i + 1, 'l'); c.px(8, 3 - i + 1, 'l'); c.px(7, 12 + i - 1, 'l'); c.px(8, 12 + i - 1, 'l')
        for i in (1, 2): c.px(3 - i + 1, 7, 'l'); c.px(3 - i + 1, 8, 'l'); c.px(12 + i - 1, 7, 'l'); c.px(12 + i - 1, 8, 'l')
        # facet shading: lower-right darker
        for y in range(16):
            for x in range(16):
                if c.get(x, y) == 'm' and (x - 7.5) + (y - 7.5) > 1.5: c.px(x, y, 'd')
        # subtle glimmer: a white sparkle orbiting the core, and a one-frame core flash
        t = f / FRAMES * 2 * math.pi
        sx, sy = round(7.5 + 3.2 * math.cos(t)), round(7.5 + 3.2 * math.sin(t))
        if c.get(sx, sy) != '.': c.px(sx, sy, 'w')
        if f == 0: c.px(7, 7, 'w')
        c.outline()
        frames.append(c)
    save_frames('luminosity_' + key, frames)

def ingot():
    frames = []
    shape = []
    # original ingot silhouette: a bevelled bar lying diagonally (top face + front face)
    for y in range(16):
        for x in range(16):
            u = x + y; v = x - y
            if 12 <= u <= 20 and -9 <= v <= 7: shape.append((x, y))
    for f in range(FRAMES):
        c = Canvas(dict(O='#2e220c', a=GOLD[1], b=GOLD[2], c=GOLD[3], d=GOLD[4], e=GOLD[5], t=TEAL[2], T=TEAL[3], w='#ffffff'))
        for (x, y) in shape:
            u = x + y
            c.px(x, y, 'd' if u <= 15 else 'c' if u <= 17 else 'b')
        for (x, y) in shape:
            if (x + 1, y) not in shape or (x, y + 1) not in shape: c.px(x, y, 'a')
            elif (x - 1, y) not in shape or (x, y - 1) not in shape: c.px(x, y, 'e')
        # teal vein along the bar
        for x in range(5, 12):
            y = 21 - x - 5
            if (x, y) in shape and c.get(x, y) not in ('a',): c.px(x, y, 't')
        # glimmer: a bright band sweeping across the bar over the cycle
        k = -10 + f * 3
        for (x, y) in shape:
            if (x - y) in (k, k + 1) and c.get(x, y) in ('c', 'd', 'b'): c.px(x, y, 'e' if (x - y) == k + 1 else 'w')
        c.outline()
        frames.append(c)
    save_frames('luminous_ingot', frames)

def tool(name, head):
    """Diagonal tool (vanilla convention): crystal-glass handle with gold bands, cream-gold head with a teal vein."""
    c = Canvas(dict(O='#2e220c', a=GOLD[1], b=GOLD[2], c=GOLD[3], d=GOLD[4], e=GOLD[5],
                    g=GLASS[1], G=GLASS[2], h=GLASS[3], t=TEAL[2], T=TEAL[3]))
    for i in range(2, 11):                        # handle from bottom-left to the centre
        x, y = i, 15 - i
        c.px(x, y, 'G'); c.px(x + 1, y, 'g')
        if i % 3 == 0: c.px(x, y, 'b'); c.px(x + 1, y, 'a')
    c.px(2, 13, 'h')
    head(c)
    c.outline()
    c.save('luminous_' + name)

def head_sword(c):
    for i in range(6, 15):
        x, y = i, 15 - i
        c.px(x, y, 'd'); c.px(x + 1, y, 'c'); c.px(x, y - 1, 'e')
        if 8 <= i <= 12: c.px(x, y, 't')
    for (x, y) in ((4, 9), (5, 10), (6, 11), (9, 12), (10, 13)): c.px(x, y, 'b')     # crossguard
    c.px(5, 9, 'd'); c.px(9, 11, 'd'); c.px(14, 1, 'e'); c.px(15, 0, 'e')
def head_pickaxe(c):
    for (x, y) in ((4, 2), (5, 2), (6, 2), (7, 2), (8, 3), (9, 3), (10, 4), (11, 5), (12, 6), (12, 7), (13, 8), (13, 9), (13, 10), (13, 11)):
        c.px(x, y, 'd')
    for (x, y) in ((5, 3), (6, 3), (7, 3), (8, 4), (9, 4), (10, 5), (11, 6), (11, 7), (12, 8), (12, 9), (12, 10)):
        c.px(x, y, 'c')
    for (x, y) in ((7, 4), (8, 5), (9, 5), (10, 6), (10, 7), (11, 8)): c.px(x, y, 't')
    c.px(4, 1, 'e'); c.px(14, 11, 'b')
def head_axe(c):
    for y in range(2, 9):
        for x in range(8, 14):
            if (x - 8) + (y - 2) <= 7 and (x - 13) ** 2 + (y - 5) ** 2 <= 16: c.px(x, y, 'c')
    for y in range(2, 9): c.px(8, y, 'd') if c.get(8, y) != '.' else None
    for (x, y) in ((10, 4), (11, 5), (12, 6)): c.px(x, y, 't')
    for (x, y) in ((9, 2), (10, 2), (11, 3)): c.px(x, y, 'e')
def head_shovel(c):
    for y in range(1, 7):
        for x in range(10, 15):
            if abs((x - 12) - (y - 4) * 0) <= 2 and (x - 12) ** 2 + (y - 3.5) ** 2 <= 7: c.px(x, y, 'c')
    for (x, y) in ((11, 2), (12, 2), (11, 3)): c.px(x, y, 'e')
    c.px(12, 4, 't'); c.px(13, 5, 'b')
def head_hoe(c):
    for (x, y) in ((8, 3), (9, 3), (10, 3), (11, 3), (12, 3), (12, 4), (13, 4), (13, 5), (12, 5)):
        c.px(x, y, 'c')
    for (x, y) in ((8, 2), (9, 2), (10, 2)): c.px(x, y, 'e')
    c.px(11, 4, 't'); c.px(10, 4, 'd')

def armor_icon(name, draw):
    c = Canvas(dict(O='#2e220c', a=GOLD[1], b=GOLD[2], c=GOLD[3], d=GOLD[4], e=GOLD[5],
                    g=GLASS[2], h=GLASS[3], t=TEAL[2], T=TEAL[3], y=R['brass'][4]))
    draw(c); c.outline(); c.save('luminous_' + name)
def helm(c):
    c.rect(3, 3, 12, 8, 'c'); c.rect(4, 2, 11, 2, 'd'); c.rect(3, 9, 5, 11, 'c'); c.rect(10, 9, 12, 11, 'c')
    c.rect(5, 6, 10, 7, 'g'); c.px(6, 6, 'h'); c.px(9, 6, 'h')                # glass visor
    for x in range(4, 12): c.px(x, 3, 'e')
    c.px(7, 4, 't'); c.px(8, 4, 't'); c.px(7, 5, 'T'); c.px(8, 5, 'T')        # teal crest vein
def chest(c):
    c.rect(2, 2, 13, 5, 'c'); c.rect(4, 6, 11, 13, 'c')
    c.rect(6, 2, 9, 3, '.'); c.px(6, 4, 'b'); c.px(9, 4, 'b')
    for y in range(6, 13): c.px(7, y, 't'); c.px(8, y, 't')                  # teal spine vein
    c.rect(6, 8, 9, 9, 'g'); c.px(7, 8, 'h'); c.px(8, 8, 'y')                 # glass-and-light core
    for x in range(2, 14): c.px(x, 2, 'e') if c.get(x, 2) != '.' else None
def legs(c):
    c.rect(3, 2, 12, 5, 'c'); c.rect(3, 6, 6, 13, 'c'); c.rect(9, 6, 12, 13, 'c')
    for x in range(3, 13): c.px(x, 2, 'e')
    c.px(4, 8, 't'); c.px(4, 9, 't'); c.px(11, 8, 't'); c.px(11, 9, 't')
    c.rect(6, 3, 9, 4, 'g')
def boots(c):
    c.rect(2, 7, 6, 12, 'c'); c.rect(9, 7, 13, 12, 'c'); c.rect(2, 12, 7, 13, 'b'); c.rect(9, 12, 14, 13, 'b')
    for x in list(range(2, 7)) + list(range(9, 14)): c.px(x, 7, 'e')
    c.px(4, 9, 't'); c.px(11, 9, 't')

def armor_layers():
    """64x32 humanoid armour layers in the vanilla UV layout (regions only; original painting)."""
    pal = {'a': GOLD[1], 'b': GOLD[2], 'c': GOLD[3], 'd': GOLD[4], 'e': GOLD[5], 't': TEAL[2], 'T': TEAL[3], 'g': GLASS[2], 'h': GLASS[3]}
    def region(im, x0, y0, w, h, fill, vein=False, glass=None):
        px = im.load()
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                k = fill
                if y == y0: k = 'e'
                if y == y0 + h - 1: k = 'a'
                px[x, y] = tuple(int(pal[k][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
        if vein:
            cx = x0 + w // 2
            for y in range(y0 + 1, y0 + h - 1):
                for x in (cx - 1, cx):
                    px[x, y] = tuple(int(pal['t'][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
        if glass:
            gx, gy, gw, gh = glass
            for y in range(gy, gy + gh):
                for x in range(gx, gx + gw):
                    px[x, y] = tuple(int(pal['g' if (x + y) % 3 else 'h'][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    l1 = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    # head (0,0)-(32,16): top/bottom 8x8 at (8,0)/(16,0), sides at y 8..16
    for (x, y, w, h) in ((8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)):
        region(l1, x, y, w, h, 'c', vein=(x == 8 and y == 8))
    region(l1, 9, 11, 6, 2, 'c', glass=(9, 11, 6, 2))                          # visor on the face
    # body (16,16)-(40,32)
    for (x, y, w, h) in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12)):
        region(l1, x, y, w, h, 'c', vein=(x in (20, 32) and y == 20))
    region(l1, 22, 24, 4, 3, 'c', glass=(22, 24, 4, 3))
    # arms (40,16)-(56,32) and boots (0,16)-(16,32)
    for (x, y, w, h) in ((44, 16, 4, 4), (48, 16, 4, 4), (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12),
                         (4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)):
        region(l1, x, y, w, h, 'c')
    l2 = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for (x, y, w, h) in ((20, 16, 8, 4), (28, 16, 8, 4), (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12),
                         (4, 16, 4, 4), (8, 16, 4, 4), (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12)):
        region(l2, x, y, w, h, 'b' if y > 16 else 'c', vein=(w == 4 and y == 20))
    l1.save('grids/luminous_layer_1.png'); l2.save('grids/luminous_layer_2.png')

if __name__ == '__main__':
    for k in LUM: luminosity(k)
    ingot()
    for n, h in (('sword', head_sword), ('pickaxe', head_pickaxe), ('axe', head_axe), ('shovel', head_shovel), ('hoe', head_hoe)): tool(n, h)
    for n, d in (('helmet', helm), ('chestplate', chest), ('leggings', legs), ('boots', boots)): armor_icon(n, d)
    armor_layers()
    # preview sheet: first frames + tools + armor at 8x
    names = [f'luminosity_{k}__f0' for k in LUM] + ['luminous_ingot__f0'] + [f'luminous_{n}' for n in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots')]
    S = 8; sheet = Image.new('RGBA', (8 * (16 * S + 8) + 8, 2 * (16 * S + 8) + 8), (139, 139, 139, 255))
    for i, n in enumerate(names):
        im = Image.open(f'grids/{n}.png').convert('RGBA').resize((16 * S, 16 * S), Image.NEAREST)
        sheet.alpha_composite(im, (8 + (i % 8) * (16 * S + 8), 8 + (i // 8) * (16 * S + 8)))
    sheet.save('luminous-sheet.png')
    # animation strip preview for the ingot and one luminosity
    anim = Image.new('RGBA', (FRAMES * 16 * 6 + 8 * FRAMES, 2 * 16 * 6 + 16), (139, 139, 139, 255))
    for r, n in enumerate(('luminous_ingot', 'luminosity_logistics')):
        for f in range(FRAMES):
            im = Image.open(f'grids/{n}__f{f}.png').convert('RGBA').resize((96, 96), Image.NEAREST)
            anim.alpha_composite(im, (4 + f * 104, 4 + r * 104))
    anim.save('luminous-anim.png')
    print('ok')
