"""Terra's Arm (entrelumen:terra_arm): a copper prosthetic arm, drawn on the 16x16 item grid.

A flexed arm (Elias, 24 September: curved, "más codo", elbow toward the bottom-right corner, the
hand easy to read): the shoulder cap at the left, the brass upper arm running to a big iron elbow
joint with a teal core in the bottom-right corner, the copper forearm rising with brass rings and a
luminous teal vein, an iron wrist, and an open hand at the top right: a glowing palm and three brass
fingers pointing up. The centre line is two segments meeting at the elbow; each pixel takes its
colour from the nearest point (position along the arm, offset across it), with cylindrical shading,
vanilla's top-left light and a dark outline. A bent arm cannot be mirror-symmetric.

    python art/authoring/draw_terra_arm.py        # writes art/grids/item/terra_arm.txt + preview
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

OUT = os.path.join(HERE, '..', 'grids', 'item', 'terra_arm.txt')
SHOULDER, ELBOW, WRIST = (2.4, 9.6), (11.4, 12.2), (12.0, 6.6)


def nearest(cx, cy, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    ln2 = dx * dx + dy * dy
    t = max(0.0, min(1.0, ((cx - ax) * dx + (cy - ay) * dy) / ln2))
    px, py = ax + dx * t, ay + dy * t
    return math.hypot(cx - px, cy - py), t, px, py


def colour(px, py):
    cu, br, te, ir = R['copper'], R['brass'], R['teal'], R['iron']
    cx, cy = px + 0.5, py + 0.5
    fx, fy = WRIST[0] - ELBOW[0], WRIST[1] - ELBOW[1]
    fl = math.hypot(fx, fy)
    ux, uy = fx / fl, fy / fl                       # forearm direction (toward the hand)
    nx, ny = -uy, ux
    along = (cx - WRIST[0]) * ux + (cy - WRIST[1]) * uy
    across = (cx - WRIST[0]) * nx + (cy - WRIST[1]) * ny
    light = -((cx - WRIST[0]) + (cy - WRIST[1]))
    # the hand: palm just past the wrist, three fingers beyond it
    if 2.3 < along <= 6.0:
        for off, length in ((0.0, 6.0), (-1.9, 5.3), (1.9, 5.3)):
            if abs(across - off) <= 0.6 and along <= length:
                return br[5] if along > length - 1.0 else br[4] if off <= 0 else br[3]
        return None
    if 0.4 < along <= 2.3 and abs(across) <= 2.4:
        if abs(across) <= 0.7 and 0.9 < along <= 1.9:
            return te[4]
        return cu[5] if across < 0 else cu[4]
    # joints: a big iron elbow and the copper shoulder cap, each with a teal core
    for (jp, jr, metal) in ((ELBOW, 2.2, ir), (SHOULDER, 1.6, cu)):
        jd = math.hypot(cx - jp[0], cy - jp[1])
        if jd <= 0.75:
            return te[4]
        if jd <= 1.2 and metal is ir:
            return te[2]
        if jd <= jr:
            return metal[4] if (cx - jp[0]) + (cy - jp[1]) < 0 else metal[2]
    d1, t1, x1, y1 = nearest(cx, cy, SHOULDER, ELBOW)
    d2, t2, x2, y2 = nearest(cx, cy, ELBOW, WRIST)
    if d1 <= d2:
        side = -((cx - x1) + (cy - y1))
        if d1 <= 1.45:
            return br[4] if side > 0.4 else br[3] if side > -0.6 else br[2]
        return None
    side = -((cx - x2) + (cy - y2))
    if t2 > 0.88:
        return (ir[4] if side > 0 else ir[2]) if d2 <= 1.7 else None
    if d2 <= 1.5:
        if int(t2 * 20) % 4 == 3:
            return br[4] if side > 0 else br[2]
        if d2 <= 0.5:
            return te[3]
        return cu[5] if side > 0.5 else cu[4] if side > -0.5 else cu[2]
    return None


def draw():
    g = [[colour(x, y) for x in range(16)] for y in range(16)]
    dark = R['copper'][0]
    out = [row[:] for row in g]
    for y in range(16):
        for x in range(16):
            if g[y][x]:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and g[y + dy][x + dx]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = dark
    return out


if __name__ == '__main__':
    g = draw()
    cols = sorted({c for row in g for c in row if c})
    keys = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    lines = ['%s %s' % (keys[i], c) for i, c in enumerate(cols)]
    lines.append('')
    for row in g:
        lines.append(''.join(keys[cols.index(c)] if c else '.' for c in row))
    with open(OUT, 'w', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')
    from PIL import Image
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if g[y][x]:
                c = g[y][x]
                im.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,))
    bg = Image.new('RGBA', (16 * 16 + 32 + 16 * 3 + 16, 16 * 16 + 32), (139, 139, 139, 255))
    bg.alpha_composite(im.resize((256, 256), Image.NEAREST), (16, 16))
    bg.alpha_composite(im.resize((48, 48), Image.NEAREST), (16 * 16 + 32, 16))
    bg.alpha_composite(im, (16 * 16 + 32 + 16, 16 + 48 + 16))
    bg.save(os.path.join(os.environ.get('TEMP', '.'), 'terra_arm_preview.png'))
    print(OUT)
