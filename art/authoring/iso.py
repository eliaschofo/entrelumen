"""Isometric cube preview for block faces (top + two sides), texel-exact, nearest upscale."""
from PIL import Image
import sys
R = 'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/grids/'


def shade(p, f):
    return (int(p[0] * f), int(p[1] * f), int(p[2] * f), p[3])


def cube(side, top, S=6):
    s = Image.open(R + side).convert('RGBA'); t = Image.open(R + top).convert('RGBA')
    # 2:1 dimetric: each texel is 2 px wide on the side faces; top is a 32x16 diamond
    iso = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for y in range(16):          # top diamond by inverse mapping
        for x in range(32):
            u = (x / 2 + y) - 8
            v = (y - x / 2) + 8
            ui, vi = int(u), int(v)
            if 0 <= ui < 16 and 0 <= vi < 16:
                iso.putpixel((x, y), t.getpixel((ui, vi)))
    for x in range(16):          # left face
        for h in range(16):
            y = 8 + x // 2 + h
            if y < 32:
                iso.putpixel((x, y), shade(s.getpixel((x, h)), 0.82))
    for x in range(16):          # right face
        for h in range(16):
            y = 16 - (x + 1) // 2 + h
            if y < 32:
                iso.putpixel((16 + x, y), shade(s.getpixel((x, h)), 0.64))
    return iso.resize((32 * S, 32 * S), Image.NEAREST)


if __name__ == '__main__':
    names = sys.argv[1].split(',')
    S = 5
    out = Image.new('RGBA', (len(names) * (32 * S + 16) + 16, 32 * S + 32), (120, 160, 210, 255))
    for i, n in enumerate(names):
        out.alpha_composite(cube(f'blk_{n}.png', 'blk_module_top.png', S), (16 + i * (32 * S + 16), 16))
    out.save('G:/Elias/Codex/Entrelumen-work/art-redo-20260923/iso.png')
