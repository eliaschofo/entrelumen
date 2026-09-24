"""Terra's Arm (entrelumen:terra_arm): a copper prosthetic arm, drawn on the 16x16 item grid.

Diagonal like a vanilla tool, from the shoulder joint (bottom left) to the hand (top right), and
mirror-symmetric about its own axis (the anti-diagonal x + y = 15): everything is a function of
u = x - y (along the arm) and |v|, v = x + y - 15 (across it). Shading is cylindrical, brightest on
the axis, so it stays symmetric.

    python art/authoring/draw_terra_arm.py        # writes art/grids/item/terra_arm.txt + preview
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

OUT = os.path.join(HERE, '..', 'grids', 'item', 'terra_arm.txt')


def colour(u, a):
    """Colour of the pixel at arm coordinate u and |v| = a, or None."""
    cu, br, te, ir = R['copper'], R['brass'], R['teal'], R['iron']
    # shoulder joint: an iron ball with a teal core, centred on u = -9
    du = u + 9
    r2 = (du * du + a * a) / 2
    if r2 <= 1.0:
        return te[4] if r2 < 0.6 else te[2]
    if r2 <= 3.2:
        return ir[3] if a <= 1 else ir[2]
    if r2 <= 5.2:
        return ir[0]
    # upper arm stub between the joint and the forearm
    if -7 <= u <= -6:
        return (br[3] if a == 0 else br[1]) if a <= 2 else (cu[0] if a == 3 else None)
    # forearm: copper plates with brass rings and a luminous teal vein on the axis
    if -5 <= u <= 4:
        if a <= 2:
            if u % 4 == 0:
                return br[4] if a == 0 else br[2]
            if a == 0:
                return te[3]
            return cu[4] if a == 1 else cu[2]
        if a == 3:
            return cu[0]
        return None
    # wrist ring
    if 5 <= u <= 6:
        if a <= 3:
            return ir[4] if a <= 1 else ir[2]
        return ir[0] if a == 4 else None
    # palm with a glowing core
    if 7 <= u <= 8:
        if a == 0:
            return te[4]
        if a <= 2:
            return cu[5] if a == 1 else cu[4]
        if a == 3:
            return cu[1]
        return None
    # knuckles, then three parallel fingers: the middle one on the axis, two alongside
    if u == 9 and a <= 3:
        return br[3] if a % 2 == 0 else br[2]
    if 10 <= u <= 13 and a <= 1:
        if u == 13:
            return br[5] if a == 0 else None
        return br[4] if a == 0 else br[2]
    if 10 <= u <= 12 and a == 3:
        return br[5] if u == 12 else br[3]
    if 10 <= u <= 12 and a == 4:
        return cu[1]
    return None


def draw():
    grid = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y - 15
            grid[y][x] = colour(u, abs(v))
    # mirror check
    for y in range(16):
        for x in range(16):
            assert grid[y][x] == grid[15 - x][15 - y]
    return grid


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
