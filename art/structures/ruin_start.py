"""The Heliodor start ruin: a sunken sun patio where the Heliodor Compass waits on its pedestal.

A round tuff floor with an oxidized-copper sun mosaic, eight quartz columns (the four on the axes
still carry the stumps of a copper dome, the diagonal ones broke and dropped their capitals on the
grass) and moss taking the cracks. Fully symmetric under the D4 group. Centred coordinates; layer 0
is the floor that replaces the ground surface.

    python art/structures/ruin_start.py      # preview into art/structures/preview/
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voxkit import Voxels, ab, iso  # noqa: E402

HALF = 7          # footprint 15 x 15
HEIGHT = 9
B = lambda n: 'minecraft:' + n
PEDESTAL = 'entrelumen:heliodor_pedestal'


def crack(a, b):
    """Deterministic, symmetric decay pattern."""
    return (a * 7 + b * 13 + a * b * 3) % 11


def design():
    v = Voxels()
    # ---- layer 0: sun mosaic ----
    for x in range(-HALF, HALF + 1):
        for z in range(-HALF, HALF + 1):
            d = math.hypot(x, z)
            if d > HALF + 0.3:
                continue
            a, b = ab(x, z)
            ray = (b == 0 or a == b) and 2 <= a <= (5 if b == 0 else 3)
            if (a, b) == (0, 0):
                blk = 'chiseled_tuff'
            elif (a, b) == (1, 0):
                blk = 'waxed_oxidized_chiseled_copper'
            elif (a, b) == (1, 1):
                blk = 'pearlescent_froglight'
            elif ray:
                blk = 'waxed_oxidized_cut_copper'
            elif 2.9 <= d < 3.9:
                blk = 'calcite' if crack(a, b) not in (0, 4) else 'moss_block'
            elif d >= 6.2:
                blk = 'polished_tuff'
            else:
                c = crack(a, b)
                blk = 'moss_block' if c == 0 else 'tuff' if c in (3, 8) else 'tuff_bricks'
            v.put(x, 0, z, B(blk))
            if blk == 'moss_block':
                v.put(x, 1, z, B('moss_carpet') if d > 4.5 else B('short_grass'))
    # ---- columns: four standing on the axes, four broken on the diagonals ----
    for y in range(1, 6):
        v.sym(6, y, 0, B('chiseled_tuff_bricks') if y == 1 else B('quartz_pillar[axis=y]'))
    v.sym(6, 6, 0, B('waxed_oxidized_cut_copper'))
    for y in (1, 2):
        v.sym(4, y, 4, B('chiseled_tuff_bricks') if y == 1 else B('quartz_pillar[axis=y]'))
    v.sym(4, 3, 4, B('moss_carpet'))
    v.sym(6, 1, 6, B('chiseled_quartz_block'))            # fallen capitals, lying on the grass
    v.sym(7, 1, 5, B('moss_carpet'))
    # ---- dome ribs: stumps rising from the standing columns, broken before the crown ----
    for i in range(0, 13):
        t = math.radians(i * 7.5)
        x = round(6 * math.cos(t))
        y = round(6 + 2.6 * math.sin(t))
        if x < 3:
            break
        v.sym(x, y, 0, B('waxed_oxidized_cut_copper'))
    v.sym(3, 8, 0, B('waxed_oxidized_copper_grate'))
    # ---- plants on the grass just outside the rim ----
    v.sym(7, 1, 3, B('fern'))
    v.sym(5, 1, 6, B('azalea'))
    # the pedestal holds the compass
    v.put(0, 1, 0, PEDESTAL)
    return v


SEAL = [(x, z) for x in (-1, 0, 1) for z in (-1, 0, 1)]     # the sun mosaic's heart is the seal
STAIR_DEPTH = 12                                            # the spiral's foot, below the patio floor


def sealed_stair():
    """The Sealed Stair to the Envés (docs/design/dungeon-enves.md): under the sun mosaic's 3x3 heart
    a spiral winds down round a calcite core to an antechamber with the Envés gate. It stays hidden
    until the seal opens (the offering on the pedestal, act III and on); the companion clears the SEAL
    cells and the pedestal when it opens. Underground and functional: the spiral turns one way, the
    one exception to the D4 rule besides lecterns. Returns voxels with negative y, plus markers."""
    v = Voxels()
    ring = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    for y in range(-STAIR_DEPTH, 0):                        # the shaft: tuff bricks round a 3x3 well
        for x in range(-2, 3):
            for z in range(-2, 3):
                if max(abs(x), abs(z)) == 2:
                    v.put(x, y, z, B('tuff_bricks') if y % 4 else B('calcite'))
                elif (x, z) == (0, 0):
                    v.put(x, y, z, B('calcite'))
                else:
                    v.put(x, y, z, B('air'))
    for i in range(STAIR_DEPTH):                            # one step down per ring cell
        x, z = ring[i % 8]
        nx, nz = ring[(i + 1) % 8]
        px, pz = ring[(i - 1) % 8]
        up = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}[(px - x, pz - z)] if (px - x, pz - z) in (
            (1, 0), (-1, 0), (0, 1), (0, -1)) else 'north'
        y = -1 - i
        v.put(x, y, z, B('tuff_brick_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % up))
        if y - 1 >= -STAIR_DEPTH:
            v.put(x, y - 1, z, B('tuff_bricks'))
    for x in range(-4, 5):                                  # the antechamber
        for z in range(-8, 1):
            for y in range(-STAIR_DEPTH - 1, -STAIR_DEPTH + 5):
                edge = abs(x) == 4 or z in (-8,) or y in (-STAIR_DEPTH - 1, -STAIR_DEPTH + 4)
                if (x, z) in [(a, b) for a in range(-2, 3) for b in range(-2, 1)] and y >= -STAIR_DEPTH:
                    continue
                v.put(x, y, z, (B('tuff_bricks') if y != -STAIR_DEPTH - 1 else B('polished_tuff')) if edge else B('air'))
    for y in range(-STAIR_DEPTH, -STAIR_DEPTH + 4):         # the gate frame on the far wall
        for x in (-2, 2):
            v.put(x, y, -8, B('calcite'))
    for x in range(-2, 3):
        v.put(x, -STAIR_DEPTH + 4, -8, B('waxed_oxidized_chiseled_copper'))
    for y in (-STAIR_DEPTH + 2,):
        for x in (-3, 3):
            v.put(x, y, -7, B('soul_lantern[hanging=false,waterlogged=false]'))
    markers = {'seal': [[x, 0, z] for (x, z) in SEAL], 'enves_gate': [[0, -STAIR_DEPTH, -8]],
               'offering': [[0, 1, 0]], 'antechamber_arrival': [[0, -STAIR_DEPTH, -4]]}
    return v, markers


def template_block(v, x, y, z):
    """Template lookup for tools/build_heliodor_ruin_start.py, in template coordinates."""
    key = (x - HALF, y, z - HALF)
    if key in v:
        return v[key]
    if y == 0:
        return None           # outside the round floor the ground stays as it is
    return 'minecraft:air'    # clear grass and bushes inside the box


if __name__ == '__main__':
    v = design()
    assert v.is_symmetric(), 'start ruin is not D4-symmetric'
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview')
    os.makedirs(out, exist_ok=True)
    iso(v, os.path.join(out, 'ruin_start.png'), scale=10, ground=11)
    st, mk = sealed_stair()
    print(len(v), 'blocks, symmetric;', len(st), 'blocks in the Sealed Stair;', mk)
