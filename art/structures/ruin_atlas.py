"""Act I anchor ruin, the Atlas Court (Patio del Atlas): a square cloister of Heliodor where the
Atlas waits on a lectern in the middle of an overgrown courtyard.

Arcade of calcite columns and tuff-brick arches around the court, a broken outer wall with
bookshelves, the copper roof of the walk half fallen in, a cross of calcite paths with rills of
water, and the lectern on a stepped dais. D4-symmetric except the lectern's facing. SKETCH: no
placement code yet; the compass objective list decides where it goes.

    python art/structures/ruin_atlas.py      # preview into art/structures/preview/
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voxkit import Voxels, ab, iso  # noqa: E402

HALF = 10         # footprint 21 x 21
HEIGHT = 8
B = lambda n: 'minecraft:' + n


def crack(a, b):
    return (a * 5 + b * 11 + a * b) % 9


def design():
    v = Voxels()
    for x in range(-HALF, HALF + 1):
        for z in range(-HALF, HALF + 1):
            a, b = ab(x, z)
            if a <= 5:                                            # courtyard
                if a <= 1 and b <= 1:
                    blk = 'polished_tuff'
                elif b == 0 and a <= 5:
                    blk = 'water'
                elif b == 1 or (a == 5 and b <= 5):
                    blk = 'calcite'
                else:
                    blk = 'grass_block'
            elif a <= 9:                                          # arcade walk
                blk = 'polished_tuff' if (a + b) % 2 == 0 else 'tuff_bricks'
                if crack(a, b) == 0: blk = 'moss_block'
            else:
                blk = 'tuff_bricks'
            v.put(x, 0, z, B(blk))
    # stepped dais with the lectern
    for x in range(-1, 2):
        for z in range(-1, 2):
            v.put(x, 1, z, B('polished_tuff_slab[type=bottom,waterlogged=false]'))
    v.put(0, 1, 0, B('chiseled_tuff'))
    v.put(0, 2, 0, B('lectern[facing=south,has_book=false,powered=false]'))
    # courtyard planting: azalea at the four inner corners, flowers along the paths
    v.sym(4, 1, 4, B('flowering_azalea'))
    for (a, b) in ((3, 2), (4, 2), (2, 3), (2, 4)):
        v.sym(a, 1, b, B('lily_of_the_valley') if (a + b) % 2 else B('cornflower'))
    v.sym(3, 1, 3, B('short_grass'))
    # arcade: calcite columns every three blocks along a = 6, tuff-brick arches and a lintel
    for b in (0, 3, 6):
        for y in range(1, 5):
            v.sym(6, y, b, B('calcite') if y < 4 else B('chiseled_tuff_bricks'))
    for y in (1, 2, 3, 4):
        v.sym(6, y, 6, B('calcite') if y < 4 else B('chiseled_tuff_bricks'))
    for b in (1, 4):
        v.sym(6, 4, b, B('tuff_brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]'))
    for b in (2, 5):
        v.sym(6, 4, b, B('tuff_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]'))
    for b in range(0, 7):
        v.sym(6, 5, b, B('tuff_bricks'))
    # outer wall with bookshelves inside and window slits, broken where the crack pattern says so
    for b in range(0, 11):
        top = 3 if b >= 6 else 2 if b >= 3 else 1
        for y in range(1, top + 1):
            window = y == 2 and b == 8
            v.sym(10, y, b, B('tuff_bricks') if not window else B('air'))
        if b in (6, 7, 8):
            for y in (1, 2):
                v.sym(9, y, b, B('bookshelf'))
    for y in range(1, 6):
        v.sym(10, y, 10, B('chiseled_tuff_bricks'))              # corner towers
    v.sym(10, 6, 10, B('waxed_oxidized_cut_copper'))
    # copper roof of the walk, half fallen in
    for a in range(6, 11):
        for b in range(0, a + 1):
            if b >= 7 and not (a == 10 and b == 10) and crack(a, b) != 5:
                v.sym(a, 6, b, B('waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]'))
    # fallen roof pieces on the walk, moss creeping in
    for (a, b) in ((8, 2), (7, 5)):
        v.sym(a, 1, b, B('waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]'))
    for (a, b) in ((8, 0), (7, 7)):
        v.sym(a, 1, b, B('moss_carpet'))
    return v


if __name__ == '__main__':
    v = design()
    lectern = v.pop((0, 2, 0))
    assert v.is_symmetric(), 'Atlas Court is not D4-symmetric'
    v[(0, 2, 0)] = lectern
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview')
    os.makedirs(out, exist_ok=True)
    iso(v, os.path.join(out, 'ruin_atlas.png'), scale=8, ground=14)
    print(len(v), 'blocks, symmetric')
