"""The Sour Shackle (Grillete Agrio), the Sour Light's unique curio: its 16x16 icon and the cuff worn on the wrist.

Writes
- art/grids/item/sour_shackle.txt, the icon's grid, which art/build_art.py exports to the mod and the resource pack;
- companion/src/main/resources/assets/entrelumen/textures/entity/sour_shackle/cuff.png, the cuff's texture, that
  client/SourShackleClient.java lays on its boxes one texel per unit.

The icon (Elias, 29/9: the first draft was fine but lacked its outline at the top and the bottom and was too thick
and too big): a round ring seen face-on, ivory lit from above, with a dark hole, two gold hinges on its sides and a
drop of sour light set in gold on the axis where it closes. The band is two texels thick at the sides, the whole
piece stands 15 texels high and 12 wide (the draft: 16 by 14) inside a closed dark outline, and it is left-right symmetric by construction
(the left half is drawn, the right half mirrored, and an assert checks it).

The cuff is the same piece put on an arm: an ivory band three texels high around the wrist (5 x 3 x 5 units,
half a unit off the arm, over the sleeve layer), a gold setting on its outer side where the five marks sit as five
studs of sour light (the side the wearer sees in first person), and a gold hinge on its front and its back. Texture layout (64 x 16; box unwrap as vanilla's ModelPart):
- (0, 0)  the band;             (0, 8)  the band cooling down (ivory dimmed, gold gone to dead olive);
- (24, 0) a lit mark (drawn full-bright), (28, 0) an unlit mark, (32, 0) a mark during the cooldown: each a 3 x 2
  block of one tone, as the studs are 0.5 x 1 x 0.5 units and half a unit apart, five along the setting.

Design hook, references inspected (rendered at 10x from the pinned JARs for the first draft, see the review sheet):
- artifacts:textures/item/withered_bracelet.png (Artifacts 13.2.3): a ring with a dark hole, shaded top to bottom;
- ars_elemental:textures/item/fire_bangle.png and base_bangle.png (Ars Elemental 0.7.10.1): a gold ring with a gem
  that carries the colour;
- minecraft:textures/item/nether_star.png: light that comes from the core;
- net.minecraft.client.model.PlayerModel (NeoForge 21.1.249 sources): the arm is 4 (or 3, slim) x 12 x 4 units and
  its sleeve layer sits a quarter unit out, so a cuff half a unit out clears it;
- our own sour_light_shard (draw_enves.py) for the light, and the Sour Light's ivory and gold (draw_white_wither.py).
No third-party texel is copied.

    python art/authoring/draw_enves_curio.py           # write the grid and the cuff texture
    python art/authoring/draw_enves_curio.py --check   # regenerate in memory and compare with the repo
"""
import argparse
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from draw_enves import TIP, BODY, ROT  # noqa: E402  (the sour light)
from draw_white_wither import IVORY, GOLD  # noqa: E402  (ivory and gold)
from palette import RAMPS as R  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
GRID = os.path.join(ROOT, 'art', 'grids', 'item', 'sour_shackle.txt')
CUFF = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures', 'entity',
                    'sour_shackle', 'cuff.png')
INK = R['ink'][1]
HOLE = R['ink'][2]
NAME = 'sour_shackle'

# The ring: centre, outer and inner radius (in texels, on texel centres).
CX, CY, OUTER, INNER = 7.5, 9.5, 5.2, 3.2
# The drop of sour light on its gold setting, left half (x = 5..7; the right half is its mirror).
GEM = {
    2: {7: TIP[2]},
    3: {6: TIP[1], 7: TIP[3]},
    4: {6: TIP[0], 7: BODY[2]},
    5: {5: GOLD[1], 6: GOLD[2], 7: GOLD[3]},
}
# The hinges, set in the band on each side of the ring (x = 3 and mirrored).
HINGE = {9: {3: GOLD[1]}, 10: {3: GOLD[0]}}


def icon():
    """The ring face-on, ivory lit from above (bright crown, dark underside, the inner edge a step darker), its hole,
    hinges and the gem. Drawn on the left half and mirrored, then outlined."""
    g = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(8):
            d = ((x - CX) ** 2 + (y - CY) ** 2) ** 0.5
            if d > OUTER:
                continue
            if d < INNER:
                g[y][x] = HOLE
                continue
            outer = d > (OUTER + INNER) / 2
            band = 0 if y < CY - 1.5 else 2 if y > CY + 1.5 else 1
            g[y][x] = ((IVORY[5], IVORY[4]), (IVORY[4], IVORY[2]), (IVORY[2], IVORY[1]))[band][0 if outer else 1]
    for table in (HINGE, GEM):
        for y, cells in table.items():
            for x, colour in cells.items():
                g[y][x] = colour
    for y in range(16):
        for x in range(8):
            g[y][15 - x] = g[y][x]
    occupied = {(x, y) for y in range(16) for x in range(16) if g[y][x]}
    for y in range(16):
        for x in range(16):
            if (x, y) not in occupied and any((x + a, y + b) in occupied for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g[y][x] = INK
    return g


def symmetric(g):
    return all(g[y][x] == g[y][15 - x] for y in range(16) for x in range(16))


def outlined(g):
    """Every opaque texel on the silhouette's edge is outline: nothing coloured touches transparency or the border."""
    for y in range(16):
        for x in range(16):
            if g[y][x] and g[y][x] != INK:
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + a, y + b
                    if not (0 <= nx < 16 and 0 <= ny < 16) or g[ny][nx] is None:
                        return False
    return True


def footprint(g):
    xs = [x for y in range(16) for x in range(16) if g[y][x]]
    ys = [y for y in range(16) for x in range(16) if g[y][x]]
    return max(xs) - min(xs) + 1, max(ys) - min(ys) + 1


def grid_text(g):
    colours = sorted({c for row in g for c in row if c})
    letters = {c: chr(ord('A') + i) for i, c in enumerate(colours)}
    lines = [f'{letters[c]} {c}' for c in colours] + ['']
    lines += [''.join(letters[c] if c else '.' for c in row) for row in g]
    return '\n'.join(lines) + '\n'


def to_image(g):
    im = Image.new('RGBA', (len(g[0]), len(g)))
    for y, row in enumerate(g):
        for x, c in enumerate(row):
            if c:
                im.putpixel((x, y), rgb(c) + (255,))
    return im


def rgb(c):
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5))


# ---- The cuff --------------------------------------------------------------------------------------------------

CUFF_SIZE = (64, 16)
BAND_UV, BAND_COOL_UV = (0, 0), (0, 8)
PIP_UV = {'lit': (24, 0), 'unlit': (28, 0), 'cool': (32, 0)}
PIP_COLOUR = {'lit': TIP[3], 'unlit': ROT[2], 'cool': ROT[0]}
W, H, D = 5, 3, 5          # the band's box, in units (= texels)


def band(ivory, gold):
    """One band's unwrap (vanilla box layout: top and bottom rims above; west, north, east, south below).
    `ivory` and `gold` are three tones each, light to dark. West and east are the gold settings of the marks (the
    outer side of whichever arm wears it); north and south, the front and the back, carry a hinge; the rims are
    ivory with their west and east edges in gold."""
    t = [[None] * (2 * (D + W)) for _ in range(D + H)]
    light, mid, dark = ivory
    glight, gmid, gdark = gold
    setting = (gmid, gdark, gdark)
    for v in range(D):                                     # rims: columns 0 and W - 1 are the west and east edges
        for u in range(W):
            edge = u in (0, W - 1)
            t[v][D + u] = glight if edge else light                # top rim, toward the shoulder
            t[v][D + W + u] = gdark if edge else dark              # bottom rim, toward the hand
    for row, (iv, gd) in enumerate(((light, glight), (mid, gmid), (dark, gdark))):
        v = D + row
        for u in range(D):
            t[v][u] = setting[row]                                 # west: a gold setting, a step darker so the lit
            t[v][D + W + u] = setting[row]                         # studs stand out; east: its mirror
        for u in range(W):
            t[v][D + u] = gd if u == W // 2 else iv                # north (front): hinge in the middle
            t[v][2 * D + W + u] = gd if u == W // 2 else iv        # south (back): the same
    return t


def cuff():
    im = Image.new('RGBA', CUFF_SIZE)
    for (u0, v0), tones in ((BAND_UV, ((IVORY[5], IVORY[3], IVORY[1]), (GOLD[2], GOLD[1], GOLD[0]))),
                            (BAND_COOL_UV, ((IVORY[2], IVORY[1], IVORY[0]), (ROT[3], ROT[2], ROT[1])))):
        for v, row in enumerate(band(*tones)):
            for u, c in enumerate(row):
                if c:
                    im.putpixel((u0 + u, v0 + v), rgb(c) + (255,))
    for kind, (u0, v0) in PIP_UV.items():                  # a 0.5 x 1 x 0.5 stud unwraps into 2 x 1.5: one tone fills it
        for v in range(2):
            for u in range(3):
                im.putpixel((u0 + u, v0 + v), rgb(PIP_COLOUR[kind]) + (255,))
    return im


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    g = icon()
    assert symmetric(g), 'the shackle must be left-right symmetric'
    assert outlined(g), 'the outline must close all around, top and bottom included'
    assert footprint(g) == (12, 15), footprint(g)
    text, texture = grid_text(g), cuff()
    if args.check:
        assert open(GRID, encoding='utf-8').read() == text, 'stale grid: ' + GRID
        with Image.open(CUFF) as actual:
            assert actual.mode == 'RGBA' and actual.tobytes() == texture.tobytes(), 'stale cuff texture: ' + CUFF
        print('PASS: sour_shackle grid and cuff texture are current')
        return
    with open(GRID, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    os.makedirs(os.path.dirname(CUFF), exist_ok=True)
    texture.save(CUFF)
    print('ok', GRID, CUFF)


if __name__ == '__main__':
    main()
