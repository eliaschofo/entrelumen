"""Hand-authored 16x16 faces for the six Ark modules and controller: tuff frame, weathered copper, teal lumen."""
import sys, math
sys.path.insert(0, '.')
from draw_items import Canvas
from palette import RAMPS as R

TUFF = ['#343833', '#474c45', '#5b6158', '#71776c', '#8b9284', '#a8ae9e']
DEEP = ['#15191b', '#1d2326', '#262e31', '#313b3e']
BASE = {
    'O': '#141417',
    '0': TUFF[0], '1': TUFF[1], '2': TUFF[2], '3': TUFF[3], '4': TUFF[4], '5': TUFF[5],
    'a': DEEP[0], 'b': DEEP[1], 'c': DEEP[2], 'd': DEEP[3],
    'e': R['copper'][1], 'f': R['copper'][2], 'g': R['copper'][3], 'h': R['copper'][4], 'i': R['copper'][5],
    'j': R['verdigris'][1], 'k': R['verdigris'][2], 'l': R['verdigris'][3], 'm': R['verdigris'][4],
    'n': R['teal'][1], 'o': R['teal'][2], 'p': R['teal'][3], 'q': R['teal'][4], 'r': R['teal'][5],
}


def canvas(**extra):
    pal = dict(BASE)
    pal.update(extra)
    return Canvas(pal)


def noise(x, y, seed=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n & 0xffff) / 0xffff


def frame(c, seed=1):
    # tuff stone border with chisel seams
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c.px(x, y, '3' if n < 0.55 else '2' if n < 0.85 else '4')
    for i in range(16):
        c.px(i, 0, '4'); c.px(0, i, '4'); c.px(i, 15, '1'); c.px(15, i, '1')
    c.px(0, 0, '5'); c.px(15, 15, '0')
    for i in (5, 10):
        for t in range(0, 3):
            c.px(i, t, '1'); c.px(t, i, '1'); c.px(i, 15 - t, '1'); c.px(15 - t, i, '1')
    # recessed dark panel with a weathered copper rim
    for y in range(3, 13):
        for x in range(3, 13):
            c.px(x, y, 'b' if noise(x, y, seed + 3) < 0.8 else 'c')
    for i in range(3, 13):
        c.px(i, 3, 'f'); c.px(3, i, 'f'); c.px(i, 12, 'h'); c.px(12, i, 'h')
    c.px(3, 3, 'e'); c.px(12, 12, 'i')
    for i in range(4, 12):
        c.px(i, 4, 'a'); c.px(4, i, 'a')
    # verdigris corner caps
    for (x, y) in [(0, 0), (13, 0), (0, 13), (13, 13)]:
        c.rect(x, y, x + 2, y + 2, 'l'); c.px(x, y, 'm'); c.px(x + 2, y + 2, 'j'); c.px(x + 1, y + 1, 'k')


def engineering():
    c = canvas(A=R['brass'][1], B=R['brass'][2], C=R['brass'][3], D=R['brass'][4], E=R['brass'][5]); frame(c, 11)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5); a = math.atan2(y - 7.5, x - 7.5)
            tooth = math.cos(8 * a) > 0.3
            if 1.2 < d <= 2.9 or (2.9 < d <= 3.9 and tooth):
                c.px(x, y, 'C' if (x - 7.5) + (y - 7.5) < 0 else 'B')
    c.px(6, 5, 'E'); c.px(5, 6, 'D'); c.px(7, 5, 'D'); c.px(9, 10, 'A'); c.px(10, 9, 'A')
    c.px(7, 7, 'p'); c.px(8, 8, 'o'); c.px(7, 8, 'o'); c.px(8, 7, 'q')
    return c


def arcane():
    c = canvas(A=R['violet'][1], B=R['violet'][2], C=R['violet'][3], D=R['violet'][4], E=R['violet'][5]); frame(c, 12)
    for y in range(4, 12):
        for x in range(5, 11):
            if abs(x - 7.5) * 1.6 + abs(y - 7.5) * 0.95 <= 4:
                c.px(x, y, 'C')
    for (x, y) in [(7, 5), (6, 6), (7, 6), (6, 7)]:
        c.px(x, y, 'D')
    c.px(7, 5, 'E'); c.px(8, 9, 'B'); c.px(9, 8, 'B'); c.px(8, 10, 'A'); c.px(9, 9, 'A')
    for (x, y) in [(4, 5), (11, 10), (10, 4)]:
        c.px(x, y, 'D')
    return c


def nature():
    c = canvas(A=R['leaf'][1], B=R['leaf'][2], C=R['leaf'][3], D=R['leaf'][4], E=R['leaf'][5], W=R['wood'][1], X=R['wood'][2]); frame(c, 13)
    for x in range(5, 11):
        c.px(x, 11, 'X')
    c.px(4, 11, 'W'); c.px(11, 11, 'W'); c.px(5, 10, 'W'); c.px(10, 10, 'W')
    for y in range(6, 11):
        c.px(8, y, 'B')
    for (x, y, k) in [(5, 6, 'D'), (6, 6, 'D'), (6, 5, 'E'), (7, 6, 'C'), (6, 7, 'C'), (7, 7, 'B'),
                      (9, 4, 'D'), (10, 4, 'E'), (10, 5, 'D'), (9, 5, 'C'), (11, 4, 'D'), (8, 5, 'B')]:
        c.px(x, y, k)
    return c


def exploration():
    c = canvas(A=R['parch'][2], B=R['parch'][4], C=R['parch'][5]); frame(c, 14)
    for (x, y) in [(7, 4), (8, 4), (7, 5), (8, 5), (7, 10), (8, 10), (7, 11), (8, 11), (4, 7), (4, 8), (5, 7), (5, 8), (10, 7), (10, 8), (11, 7), (11, 8)]:
        c.px(x, y, 'A')
    for (x, y) in [(7, 4), (7, 5), (4, 7), (5, 7)]:
        c.px(x, y, 'C')
    for (x, y) in [(6, 6), (9, 6), (6, 9), (9, 9)]:
        c.px(x, y, 'A')
    c.rect(6, 7, 9, 8, 'B'); c.rect(7, 6, 8, 9, 'B'); c.px(7, 7, 'C')
    c.px(8, 4, 'h'); c.px(8, 5, 'g'); c.px(7, 4, 'i')
    c.px(8, 8, 'p')
    return c


def logistics():
    c = canvas(); frame(c, 15)
    for (a, b) in [((4, 8), (7, 8)), ((7, 8), (10, 5)), ((7, 8), (11, 8)), ((7, 8), (10, 11))]:
        c.line([a, b], 'o')
    for (x, y) in [(5, 8), (8, 7), (9, 6), (9, 8), (8, 9)]:
        c.px(x, y, 'p')
    for (x, y) in [(4, 8), (11, 5), (11, 8), (11, 11)]:
        c.px(x, y, 'h'); c.px(x, y - 1, 'i')
    c.px(7, 8, 'r')
    return c


def habitation():
    c = canvas(A=R['crimson'][1], B=R['crimson'][2], C=R['crimson'][3], D=R['parch'][3], E=R['parch'][4],
               F=R['straw'][2], G=R['straw'][3], H=R['wood'][2]); frame(c, 16)
    for i, y in enumerate(range(4, 8)):
        for x in range(7 - i, 9 + i):
            c.px(x, y, 'B' if x < 8 else 'A')
    c.px(7, 4, 'C'); c.px(6, 5, 'C')
    c.rect(5, 8, 10, 11, 'D'); c.rect(5, 8, 5, 11, 'E')
    c.rect(7, 9, 8, 11, 'H'); c.px(9, 9, 'G'); c.px(6, 9, 'F')
    c.px(10, 5, '2'); c.px(10, 4, '2')
    return c


def controller():
    c = canvas(); frame(c, 17)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 3.3: c.px(x, y, 'o')
            if d <= 2.3: c.px(x, y, 'p')
            if d <= 1.2: c.px(x, y, 'r')
    for (x, y) in [(7, 4), (4, 7), (11, 8), (8, 11)]:
        c.px(x, y, 'q')
    c.px(6, 6, 'q')
    return c


def top():
    c = canvas()
    for y in range(16):
        for x in range(16):
            c.px(x, y, 'g' if noise(x, y, 21) < 0.7 else 'f')
    for i in range(16):
        c.px(i, 0, 'i'); c.px(0, i, 'i'); c.px(i, 15, 'e'); c.px(15, i, 'e')
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        c.px(x, y, 'e')
    for gy in range(3, 13, 3):
        for gx in range(3, 13, 3):
            c.rect(gx, gy, gx + 1, gy + 1, 'o'); c.px(gx, gy, 'q'); c.px(gx + 1, gy + 1, 'n')
    for (x, y) in [(2, 6), (6, 2), (13, 9), (9, 13), (2, 12), (12, 3)]:
        c.px(x, y, 'l')
    for (x, y) in [(3, 6), (6, 3), (12, 9)]:
        c.px(x, y, 'k')
    return c


def bottom():
    c = canvas()
    for y in range(16):
        for x in range(16):
            c.px(x, y, '2' if noise(x, y, 31) < 0.7 else '1')
    for i in range(16):
        c.px(i, 0, '3'); c.px(0, i, '3'); c.px(i, 15, '0'); c.px(15, i, '0')
    for i in range(4, 12):
        c.px(i, 4, '1'); c.px(4, i, '1'); c.px(i, 11, '3'); c.px(11, i, '3')
    return c


FACES = {'engineering_module': engineering, 'arcane_module': arcane, 'nature_module': nature, 'exploration_module': exploration,
         'logistics_module': logistics, 'habitation_module': habitation, 'ark_controller': controller,
         'module_top': top, 'module_bottom': bottom}

if __name__ == '__main__':
    for n, f in FACES.items():
        f().save('blk_' + n)
