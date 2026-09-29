"""DRAFT: the Sour Shackle (Grillete Agrio), the Sour Light's unique curio, on the native 16x16 grid.

Proposal only (docs/design/dungeon-enves.md, «Curio único de la Luz Agria»): nothing here is wired into the
game. The grid is not written to art/grids/item, so art/build_art.py does not export it; it goes to a review
folder with a sheet, for Elias to look at before anything is built.

Design hook, references inspected (rendered at 10x from the pinned JARs, see the sheet):
- artifacts:textures/item/withered_bracelet.png (Artifacts 13.2.3): a bracelet is a ring with a dark hole and a
  band shaded top to bottom, about 13 pixels across, seen from above at a tilt; no gem;
- ars_elemental:textures/item/fire_bangle.png and base_bangle.png (Ars Elemental 0.7.10.1): a gold ring at the
  same tilt with a gem set on the lower-right band (asymmetric), the gem carries the colour;
- minecraft:textures/item/nether_star.png: light that comes from the core, symmetric;
- our own sour_light_shard (art/authoring/draw_enves.py): the sour-light ramps TIP, BODY and ROT.
Taken: the ring silhouette, a dark hole and a band shaded from top to bottom from the bracelets; the gem carries
the colour and the glow. Changed: the pack's canon is symmetry, so the ring is seen face-on (not tilted), the gem
sits on the axis (top, where the ring closes) and the two hinges mirror each other on the sides; the metal is
the Sour Light's ivory and gold (draw_white_wither.py).
The left half is drawn by hand and the right half is its mirror, so the sprite is left-right symmetric by
construction (and a test-style assert checks it).

    python art/authoring/draw_enves_curio.py [out_dir]
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from draw_enves import TIP, BODY  # noqa: E402  (the sour light)
from draw_white_wither import IVORY, GOLD  # noqa: E402  (ivory and gold)
from palette import RAMPS as R  # noqa: E402

INK = R['ink'][1]
HOLE = '#2b2124'
DEFAULT_OUT = 'E:/Temp/Elias/claude/C--Users-elias-Documents-Codex-2026-09-12-h/39181316-8996-4d78-bc8f-017b1c5c55c6/scratchpad/enves-curio'
NAME = 'sour_shackle'


# The gem, left half (x = 5..7; the right half is its mirror). Letters:
#   light  t u v w B   TIP[0..3] and BODY[1], the sour light of the gem, edge to core
#   gold   g G         GOLD[0..1], the claws that hold it
GEM = {
    0: {7: 'v'},
    1: {6: 'u', 7: 'w'},
    2: {5: 't', 6: 'v', 7: 'w'},
    3: {5: 't', 6: 'v', 7: 'w'},
    4: {6: 'u', 7: 'v'},
    5: {7: 'B'},
}
CLAWS = {3: {4: 'g'}, 4: {5: 'g', 4: 'G'}, 5: {6: 'G', 5: 'g'}}
# The hinge, one on each side of the ring (gold, x = 2..3 and mirrored)
HINGE = {8: {2: 'h', 3: 'G'}, 9: {2: 'H', 3: 'h'}, 10: {2: 'h', 3: 'G'}, 11: {2: 'G', 3: 'g'}}


def shackle():
    """The cuff as a round ring seen face-on (12 across), ivory and lit from above, with gold hinges at its
    sides and the sour-light gem on the axis where it closes. Symmetric by construction: everything is drawn on
    the left half and mirrored."""
    colour = {'g': GOLD[0], 'G': GOLD[1], 'h': GOLD[2], 'H': GOLD[3],
              't': TIP[0], 'u': TIP[1], 'v': TIP[2], 'w': TIP[3], 'B': BODY[1]}
    cx, cy = 7.5, 9.5
    g = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(8):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 <= 6.1 ** 2:
                if d2 < 3.3 ** 2:
                    g[y][x] = HOLE
                else:                                            # lit from above: bright crown, dark underside
                    band = 0 if y < cy - 2 else 1 if y < cy + 2 else 2
                    g[y][x] = (IVORY[5], IVORY[3], IVORY[2])[band] if d2 > 4.4 ** 2 else (IVORY[4], IVORY[2], IVORY[1])[band]
    for table in (HINGE, CLAWS, GEM):
        for y, cells in table.items():
            for x, ch in cells.items():
                g[y][x] = colour[ch]
    for y in range(16):
        for x in range(8):
            g[y][15 - x] = g[y][x]
    occ = {(x, y) for y in range(16) for x in range(16) if g[y][x]}
    for y in range(16):
        for x in range(16):
            if (x, y) not in occ and any((x + a, y + b) in occ for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g[y][x] = INK
    return g


def symmetric(g):
    return all(g[y][x] == g[y][15 - x] for y in range(16) for x in range(16))


def write_grid(name, g, out):
    colours = sorted({c for row in g for c in row if c})
    letters = {c: chr(ord('A') + i) for i, c in enumerate(colours)}
    lines = [f'{letters[c]} {c}' for c in colours] + ['']
    lines += [''.join(letters[c] if c else '.' for c in row) for row in g]
    with open(os.path.join(out, name + '.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


def to_image(g):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if g[y][x]:
                im.putpixel((x, y), tuple(int(g[y][x][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
    return im


def sheet(g, out):
    """The draft at native scale, in an inventory slot at 4x and 8x, on light and dark, beside its references."""
    sys.path.insert(0, HERE)
    from enves_sheets import Sheet, big, framed, jar_texture, CLIENT_JAR
    import json
    root = os.path.normpath(os.path.join(HERE, '..', '..'))
    paths = json.load(open(os.path.join(root, 'catalog', 'local-paths.json'), encoding='utf-8'))
    im = to_image(g)

    def slot(scale, bg):
        s = Image.new('RGBA', (18 * scale, 18 * scale), bg)
        s.alpha_composite(big(im, scale), (scale, scale))
        return s

    grey, dark, light = (139, 139, 139, 255), (44, 44, 52, 255), (198, 198, 198, 255)
    sh = Sheet(1500)
    sh.title('Sour Shackle (Grillete Agrio): 16x16 draft, not wired into the game',
             ['Bracelet-slot curio proposed as the Sour Light\'s unique drop. Left half by hand, right half mirror: '
              'symmetric = ' + str(symmetric(g)) + '.'])
    sh.row('The draft', [framed(big(im, 16)), slot(8, grey), slot(4, grey), slot(2, grey), slot(1, grey), slot(4, dark), slot(4, light)],
           ['16x', '8x slot', '4x slot', '2x hotbar', '1x native', 'dark bg', 'light bg'])
    refs = [(paths['artifacts-neoforge-13.2.3.jar'], 'assets/artifacts/textures/item/withered_bracelet.png', 'artifacts withered_bracelet'),
            (paths['ars_elemental-1.21.1-0.7.10.1.jar'], 'assets/ars_elemental/textures/item/fire_bangle.png', 'ars_elemental fire_bangle'),
            (paths['ars_elemental-1.21.1-0.7.10.1.jar'], 'assets/ars_elemental/textures/item/base_bangle.png', 'ars_elemental base_bangle'),
            (CLIENT_JAR, 'assets/minecraft/textures/item/nether_star.png', 'vanilla nether_star')]
    shown = [slot(6, grey)]
    caps = ['ours']
    for jar, rel, cap in refs:
        r = jar_texture(jar, rel)
        s = Image.new('RGBA', (18 * 6, 18 * 6), grey)
        s.alpha_composite(big(r, 6), (6, 6))
        shown.append(s)
        caps.append(cap)
    ours_shard = os.path.join(root, 'art', 'grids', 'item', 'sour_light_shard.txt')
    if os.path.exists(ours_shard):
        rows = open(ours_shard, encoding='utf-8').read().split('\n\n')
        legend = dict(line.split() for line in rows[0].splitlines())
        shard = Image.new('RGBA', (16, 16))
        for y, line in enumerate(rows[1].split()):
            for x, ch in enumerate(line):
                if ch in legend:
                    c = legend[ch]
                    shard.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,))
        s = Image.new('RGBA', (18 * 6, 18 * 6), grey)
        s.alpha_composite(big(shard, 6), (6, 6))
        shown.append(s)
        caps.append('ours: sour_light_shard (same light)')
    sh.row('Against its references (rendered from the pinned JARs) and the pack\'s own shard', shown, caps,
           note=['Taken: the ring, dark hole and top-to-bottom shading of the bracelets; the gem carries the colour and the glow.',
                 'Changed: face-on and symmetric (gem on the axis, hinges mirrored), ivory and gold of the Sour Light, sour-light ramps.'])
    sh.render(os.path.join(out, NAME + '_review.png'))


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('ENVES_CURIO_OUT', DEFAULT_OUT)
    os.makedirs(out, exist_ok=True)
    g = shackle()
    assert symmetric(g), 'the shackle must be left-right symmetric'
    write_grid(NAME, g, out)
    to_image(g).save(os.path.join(out, NAME + '.png'))
    sheet(g, out)
    print('ok', out)
