"""Terra's Arm (entrelumen:terra_arm): a copper prosthetic arm, drawn on the 16x16 item grid.

A curved, flexed arm (Elias, 24 September: "que sea curvo", then the same curve inverted so the
elbow points to the bottom-right corner, with a touch more elbow): shoulder cap at the bottom left,
upper arm running to an iron elbow joint with a teal core, the forearm bending toward the top right
with brass rings and a luminous teal vein, an iron wrist, a glowing palm and three brass fingers
along the forearm's direction. The centre line is a quadratic curve; each pixel takes its colour
from the nearest point of that curve (position along the arm and signed offset across it), with
cylindrical shading plus vanilla's top-left light. It is not mirror-symmetric: a bent arm cannot be.

    python art/authoring/draw_terra_arm.py        # writes art/grids/item/terra_arm.txt + preview
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

OUT = os.path.join(HERE, '..', 'grids', 'item', 'terra_arm.txt')
# the curve of the first bent draft mirrored across the anti-diagonal (elbow toward the bottom right),
# with the bend pushed a touch further
P0, P1, P2 = (2.4, 11.8), (11.0, 12.2), (11.4, 3.6)     # shoulder, bend control, wrist end


def curve(n=400):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * P0[0] + 2 * (1 - t) * t * P1[0] + t * t * P2[0]
        z = (1 - t) ** 2 * P0[1] + 2 * (1 - t) * t * P1[1] + t * t * P2[1]
        dx = 2 * (1 - t) * (P1[0] - P0[0]) + 2 * t * (P2[0] - P1[0])
        dz = 2 * (1 - t) * (P1[1] - P0[1]) + 2 * t * (P2[1] - P1[1])
        ln = math.hypot(dx, dz)
        pts.append((t, x, z, dx / ln, dz / ln))
    return pts


PTS = curve()
END = PTS[-1]


def colour(px, py):
    cu, br, te, ir = R['copper'], R['brass'], R['teal'], R['iron']
    cx, cy = px + 0.5, py + 0.5
    # fingers beyond the wrist end, along the end tangent: middle finger and two alongside
    _, ex, ey, tx, ty = END
    nx, ny = -ty, tx
    along = (cx - ex) * tx + (cy - ey) * ty
    across = (cx - ex) * nx + (cy - ey) * ny
    if 0.2 < along <= 3.4:
        for off, length in ((0.0, 3.4), (-1.9, 2.6), (1.9, 2.6)):
            if abs(across - off) <= 0.55 and along <= length:
                tip = along > length - 1.0
                return br[5] if tip else (br[4] if off == 0 else br[3])
        if abs(across) <= 2.5 and along <= 1.1:
            return cu[1]
    # nearest point of the arm's centre line
    best = min(PTS, key=lambda p: (cx - p[1]) ** 2 + (cy - p[2]) ** 2)
    t, x, y, tx, ty = best
    d = math.hypot(cx - x, cy - y)
    side = (cx - x) * (-ty) + (cy - y) * tx          # signed: negative toward the top-left
    light = -side                                    # vanilla light comes from the top left
    # shoulder cap and elbow joint, each with a teal core
    for (jt, jr, metal) in ((0.0, 1.7, cu), (0.46, 1.6, ir)):
        jp = PTS[int(jt * (len(PTS) - 1))]
        jd = math.hypot(cx - jp[1], cy - jp[2])
        if jd <= 0.7:
            return te[4]
        if jd <= jr:
            return metal[4] if (cx - jp[1]) + (cy - jp[2]) < 0 else metal[2]
    if t <= 0.46:
        if d <= 1.35:
            return br[4] if light > 0.3 else br[3] if light > -0.5 else br[2]
        return None
    if t <= 0.84:
        if d <= 1.4:
            if int(t * 38) % 5 == 0:
                return br[4] if light > 0 else br[2]
            if d <= 0.45:
                return te[3]
            return cu[5] if light > 0.5 else cu[4] if light > -0.5 else cu[2]
        return None
    if t <= 0.9:
        return (ir[4] if light > 0 else ir[2]) if d <= 1.6 else None
    if d <= 0.6:
        return te[4]
    if d <= 1.8:
        return cu[5] if light > 0 else cu[3]
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
    bg = Image.new('RGBA', (16 * 16 + 32, 16 * 16 + 32), (139, 139, 139, 255))
    bg.alpha_composite(im.resize((256, 256), Image.NEAREST), (16, 16))
    bg.save(os.path.join(os.environ.get('TEMP', '.'), 'terra_arm_preview.png'))
    print(OUT)
