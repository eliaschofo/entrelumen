"""The five medium Heliodor ruins (docs/design/heliodor-ruins.md, Plan v2), each 40-60 blocks with an interior:

  greenhouse  III  Overworld  Dome Greenhouse: glass and copper dome over a garden round a great
                              flowering tree; planting four saplings in the beds opens the seed crypt
                              (Mother Seed).
  foundry     III  Nether     Foundry Under the Lava: a blackstone crucible hall on a lava lake;
                              a wave of guards, then four levers open the vault (Heliodor Crucible).
  sanctuary   IV   Twilight   Sanctuary: a ring of standing stones round a pool and a great tree; the
                              stones touched in the right order open the root cellar (Forest Testimony).
  antechamber IV   Aether     Sun Antechamber: a quartz and gold platform between clouds with four
                              floating islets; an offering on each islet opens the sun gates (Sun Key).
  void_obs    V    End        Void Observatory: a purpur telescope ring on obsidian pylons over an
                              open floor; parkour and a fight up to the eyepiece (Star Chart).

Vanilla blocks only for the preview; the Aether and Twilight palettes can swap in their mods' blocks.
D4-symmetric. Centred coordinates, layer 0 is the floor (the dimension ruins float or sink as their
placement decides).

    python art/structures/ruins_medium.py        # previews and markers into $RUIN_OUT
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from voxkit import Voxels, ab  # noqa: E402

B = lambda n: 'minecraft:' + n
S2 = math.sqrt(2)
LEAF = lambda n: B(n + '[distance=7,persistent=true,waterlogged=false]')


def m(x, z):
    a, b = abs(x), abs(z)
    return max(a, b, (a + b) / S2)


def noise(x, y, z, salt=0):
    a, b = ab(x, z)
    return random.Random(hash((a, b, y, salt))).random()


def disc(v, y, r, block, r0=-1.0, metric=math.hypot):
    n = int(r) + 2
    for x in range(-n, n + 1):
        for z in range(-n, n + 1):
            if r0 < metric(x, z) <= r:
                v.put(x, y, z, block)


def tree(v, y0, trunk_h, canopy_r, leaves, trunk, blossom=None):
    """A D4-symmetric great tree: a 3x3 trunk with root flares, a layered canopy."""
    for y in range(y0, y0 + trunk_h):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                v.put(x, y, z, trunk)
    for k in range(1, 4):                                              # roots flaring out on the axes
        for y in range(y0, y0 + 3 - k):
            v.sym(1 + k, y, 0, trunk)
    cy = y0 + trunk_h
    for x in range(-int(canopy_r) - 1, int(canopy_r) + 2):
        for z in range(-int(canopy_r) - 1, int(canopy_r) + 2):
            for dy in range(-3, 5):
                r = math.sqrt(x * x + z * z + (dy * 1.5) ** 2)
                if r <= canopy_r and noise(x, cy + dy, z, 9) < 0.92:
                    blk = blossom if blossom and noise(x, cy + dy, z, 10) < 0.3 else leaves
                    v.put(x, cy + dy, z, blk)
    for y in range(cy - 3, cy + 2):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                v.put(x, y, z, trunk)
    for k in range(1, 5):                                              # four boughs on the diagonals
        v.sym(1 + k, cy - 1 + k // 2, 1 + k, trunk)


# ---------------------------------------------------------------- greenhouse
def greenhouse():
    v, mk = Voxels(), {'pedestal': [], 'offering_sockets': [], 'barrels': [], 'lore': []}
    R = 20.5
    for x in range(-23, 24):                                           # calcite plinth and garden floor
        for z in range(-23, 24):
            d = m(x, z)
            a, b = ab(x, z)
            if d <= R + 1.5:
                v.put(x, 0, z, B('calcite') if d > R - 1 else (B('moss_block') if noise(x, 0, z, 1) < 0.5 else B('grass_block[snowy=false]')))
                if R - 1 < d <= R + 1.5:
                    v.put(x, 1, z, B('calcite') if d <= R else B('waxed_oxidized_cut_copper'))
            if b <= 1 and d <= R - 1 and d > 4:                        # paths on the axes
                v.put(x, 0, z, B('polished_tuff'))
    # the dome: ribs on the axes and diagonals, glass between
    for x in range(-22, 23):
        for z in range(-22, 23):
            for dy in range(0, 22):
                rho = math.sqrt(m(x, z) ** 2 + (dy * 1.0) ** 2)
                if R - 0.9 <= rho <= R:
                    a, b = ab(x, z)
                    rib = b == 0 or a == b or dy in (6, 13)
                    broken = not rib and noise(x, dy, z, 2) < 0.12
                    if not broken:
                        v.put(x, 2 + dy, z, B('waxed_oxidized_cut_copper') if rib else B('glass'))
    for b in range(0, 3):                                              # arched doors on the axes
        for y in range(2, 7):
            if not (b == 2 and y == 6):
                for a in range(19, 22):
                    v.sym(a, y, b, B('air'))
    # four raised beds with an offering socket each (plant a sapling to answer)
    for x in range(-14, 15):
        for z in range(-14, 15):
            a, b = ab(x, z)
            if 8 <= a <= 12 and 3 <= b <= 7 and a != b and abs(a - 10) + abs(b - 5) <= 3:
                v.put(x, 1, z, B('mud_bricks') if abs(a - 10) + abs(b - 5) == 3 else B('rooted_dirt'))
    for (a, b) in ((10, 5),):
        v.sym(a, 1, b, B('moss_block'))
        mk['offering_sockets'] = [[sx * a, 2, sz * b] for sx in (1, -1) for sz in (1, -1)] + \
                                 [[sx * b, 2, sz * a] for sx in (1, -1) for sz in (1, -1)]
    tree(v, 1, 9, 7.5, LEAF('flowering_azalea_leaves'), B('stripped_cherry_wood[axis=y]'), LEAF('cherry_leaves'))
    # the seed crypt under the tree
    for x in range(-6, 7):
        for z in range(-6, 7):
            d = m(x, z)
            for y in range(-6, 0):
                if d <= 6:
                    v.put(x, y, z, B('tuff_bricks') if (d > 5 or y in (-6, -1)) else B('air'))
            if d <= 5:
                v.put(x, -6, z, B('chiseled_tuff') if d <= 1.5 else B('polished_tuff'))
    for y in range(-5, 1):
        v.sym(5, y, 0, B('ladder[facing=west,waterlogged=false]'))
    v.sym(6, 0, 0, B('polished_tuff'))
    v.put(0, -5, 0, B('chiseled_tuff_bricks'))
    mk['pedestal'] = [[0, -4, 0]]
    v.sym(3, -5, 3, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[sx * 3, -5, sz * 3] for sx in (1, -1) for sz in (1, -1)]
    for y in (-2,):
        v.sym(3, y, 0, B('lantern[hanging=true,waterlogged=false]'))
    mk['lore'] = [[0, 1, 17]]
    return v, mk


# ---------------------------------------------------------------- foundry
def foundry():
    v, mk = Voxels(), {'pedestal': [], 'levers': [], 'barrels': [], 'boss': [], 'lore': []}
    for x in range(-24, 25):                                           # lava lake and the hall floor
        for z in range(-24, 25):
            d = m(x, z)
            a, b = ab(x, z)
            if d <= 23:
                v.put(x, -1, z, B('lava') if d > 15.5 else B('polished_blackstone_bricks'))
                if d <= 15.5:
                    v.put(x, 0, z, B('magma_block') if (a + b) % 7 == 0 and d > 5 else B('polished_blackstone'))
            if b <= 1 and 15.5 < d <= 23:                              # four causeways over the lava
                v.put(x, -1, z, B('polished_blackstone_bricks'))
                v.put(x, 0, z, B('polished_blackstone'))
    # walls of the hall: blackstone with gilded courses, basalt buttresses leaning in
    for x in range(-17, 18):
        for z in range(-17, 18):
            d = m(x, z)
            a, b = ab(x, z)
            if 14.5 < d <= 15.5:
                for y in range(1, 14):
                    blk = B('polished_blackstone_bricks')
                    if y % 5 == 0:
                        blk = B('gilded_blackstone')
                    if b <= 1 and y <= 5:
                        blk = B('air')                                 # gates to the causeways
                    if abs(b - a * 0.4142) < 0.6 and y > 11 and noise(x, y, z, 3) < 0.5:
                        blk = B('air')                                 # broken crowns
                    v.put(x, y, z, blk)
            if 15.5 < d <= 17 and abs(b - a * 0.4142) < 0.8:
                for y in range(1, 12 - int((d - 15.5) * 3)):
                    v.put(x, y, z, B('polished_basalt[axis=y]'))
    # the roof ring of copper and the chain hood
    for x in range(-16, 17):
        for z in range(-16, 17):
            d = m(x, z)
            if 12.5 < d <= 15.5:
                v.put(x, 14, z, B('waxed_oxidized_cut_copper') if d > 14.5 or noise(x, 14, z, 4) < 0.6 else B('air'))
    disc(v, 1, 4.5, B('polished_blackstone_bricks'), metric=m)          # the crucible on its plinth
    disc(v, 2, 3.5, B('gilded_blackstone'), 2.3, metric=m)
    disc(v, 3, 3.5, B('polished_blackstone_bricks'), 2.3, metric=m)
    disc(v, 2, 2.3, B('lava'), metric=m)
    disc(v, 3, 2.3, B('lava'), metric=m)
    for y in range(4, 13):
        v.sym(3, y, 3, B('chain[axis=y,waterlogged=false]'))
    disc(v, 13, 3.8, B('waxed_oxidized_copper_grate'), metric=m)
    for a in range(5, 12):                                             # copper pipes to four furnaces
        v.sym(a, 1, 0, B('waxed_copper_grate'))
    for y in range(1, 4):
        v.sym(12, y, 0, B('blast_furnace[facing=west,lit=true]') if y == 1 else B('polished_blackstone_bricks'))
    v.sym(12, 4, 0, B('waxed_copper_bulb[lit=true,powered=false]'))
    v.sym(12, 2, 2, B('polished_blackstone_bricks'))
    v.sym(11, 2, 2, B('lever[face=floor,facing=west,powered=false]'))
    mk['levers'] = [[11, 2, 2]]
    # the vault beneath the crucible
    for x in range(-7, 8):
        for z in range(-7, 8):
            d = m(x, z)
            for y in range(-6, -1):
                if d <= 6.5:
                    v.put(x, y, z, B('polished_blackstone_bricks') if (d > 5.5 or y == -6) else B('air'))
    for y in range(-5, -1):
        v.sym(5, y, 0, B('ladder[facing=west,waterlogged=false]'))
    for y in (-1, 0):
        v.sym(5, y, 0, B('ladder[facing=west,waterlogged=false]'))
    v.put(0, -5, 0, B('gilded_blackstone'))
    mk['pedestal'] = [[0, -4, 0]]
    v.sym(4, -5, 4, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[sx * 4, -5, sz * 4] for sx in (1, -1) for sz in (1, -1)]
    mk['boss'] = [[0, 1, 9]]
    mk['lore'] = [[0, 1, 13]]
    return v, mk


# ---------------------------------------------------------------- sanctuary
def sanctuary():
    v, mk = Voxels(), {'pedestal': [], 'order_stones': [], 'barrels': [], 'lore': []}
    for x in range(-24, 25):
        for z in range(-24, 25):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            if d <= 23.5:
                v.put(x, 0, z, B('moss_block') if noise(x, 0, z, 1) < 0.55 else B('grass_block[snowy=false]'))
                if 9 < d <= 12.5:                                      # the sunken pool
                    v.put(x, 0, z, B('water'))
                    v.put(x, -1, z, B('mud') if noise(x, -1, z, 2) < 0.6 else B('clay'))
                if 12.5 < d <= 13.5 or 8.5 < d <= 9:
                    v.put(x, 0, z, B('mossy_stone_bricks'))
                if 9 < d <= 12.5 and noise(x, 1, z, 3) < 0.08:
                    v.put(x, 1, z, B('lily_pad'))
            if d <= 23.5 and d > 14 and noise(x, 1, z, 4) < 0.08:
                v.put(x, 1, z, B('fern'))
    for (a, b) in ((18, 0), (13, 13)):                                 # eight standing stones
        for y in range(1, 8):
            blk = B('mossy_stone_bricks') if noise(a, y, b, 5) < 0.4 else B('stone_bricks')
            if y == 7:
                blk = B('chiseled_stone_bricks')
            v.sym(a, y, b, blk)
    v.sym(18, 8, 0, B('moss_carpet'))
    v.sym(13, 8, 13, B('moss_carpet'))
    mk['order_stones'] = [[18, 4, 0], [0, 4, 18], [-18, 4, 0], [0, 4, -18], [13, 4, 13], [-13, 4, 13], [13, 4, -13], [-13, 4, -13]]
    for a in range(9, 14):                                             # stepping stones across the pool
        v.sym(a, 0, 0, B('mossy_cobblestone'))
    tree(v, 1, 12, 9.5, LEAF('oak_leaves'), B('oak_wood[axis=y]'), LEAF('flowering_azalea_leaves'))
    for y in (9,):
        v.sym(4, y, 4, B('pearlescent_froglight[axis=y]'))             # fireflies caught in the boughs
        v.sym(6, y + 2, 2, B('ochre_froglight[axis=y]'))
    for (a, b) in ((3, 0),):                                           # lanterns in the roots
        v.sym(a, 1, b + 2, B('lantern[hanging=false,waterlogged=false]'))
    # the root cellar
    for x in range(-6, 7):
        for z in range(-6, 7):
            d = m(x, z)
            for y in range(-6, 0):
                if d <= 6:
                    v.put(x, y, z, B('rooted_dirt') if (d > 5 or y in (-6, -1)) else B('air'))
            if d <= 5:
                v.put(x, -6, z, B('mossy_stone_bricks'))
    for y in range(-5, 0):
        v.sym(3, y, 3, B('oak_wood[axis=y]'))                          # roots holding up the cellar
    for y in range(-5, 1):
        v.sym(5, y, 0, B('ladder[facing=west,waterlogged=false]'))
    v.sym(6, 0, 0, B('mossy_stone_bricks'))
    v.put(0, -5, 0, B('chiseled_stone_bricks'))
    mk['pedestal'] = [[0, -4, 0]]
    v.sym(4, -5, 1, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[4, -5, 1]]
    mk['lore'] = [[0, 1, 15]]
    return v, mk


# ---------------------------------------------------------------- antechamber
def antechamber():
    v, mk = Voxels(), {'pedestal': [], 'offering_sockets': [], 'barrels': [], 'lore': []}
    R = 12.5
    for x in range(-16, 17):                                           # the main platform, cloud-rimmed
        for z in range(-16, 17):
            d = m(x, z)
            a, b = ab(x, z)
            if d <= R:
                v.put(x, 0, z, B('gold_block') if (b == 0 or a == b) and 3 < d < R - 1 else (B('smooth_quartz') if d < R - 1 else B('quartz_bricks')))
                for y in range(-3, 0):
                    if d <= R - 1 - (-y) * 1.5:
                        v.put(x, y, z, B('quartz_bricks') if y > -3 else B('smooth_quartz'))
            if R < d <= R + 3 and noise(x, 0, z, 1) < 0.55:
                v.put(x, -1 + int(noise(x, 1, z, 2) * 2), z, B('white_wool'))    # a cloud skirt
    # four sun gates on the axes
    for b in range(0, 4):
        for y in range(1, 10):
            gate_open = b <= 2 and y <= 7 and not (b == 2 and y == 7)
            if not gate_open or b == 3:
                v.sym(11, y, b, B('quartz_pillar[axis=y]') if b == 3 else B('quartz_bricks'))
            if y == 9 and b <= 3:
                v.sym(11, y, b, B('gold_block'))
    for b in range(0, 3):
        v.sym(11, 8, b, B('smooth_quartz'))
    v.sym(11, 10, 0, B('pearlescent_froglight[axis=y]'))
    # the stair of light up to the throne of the sun
    for k in range(0, 6):
        disc(v, 1 + k, 6.5 - k, B('smooth_quartz') if k % 2 == 0 else B('quartz_bricks'), metric=m)
    for x in range(-2, 3):
        for z in range(-2, 3):
            if m(x, z) <= 1.5:
                v.put(x, 7, z, B('gold_block'))
    v.put(0, 8, 0, B('chiseled_quartz_block'))
    mk['pedestal'] = [[0, 9, 0]]
    for y in range(7, 15):
        v.sym(5, y, 5, B('quartz_pillar[axis=y]'))
    disc(v, 15, 7.5, B('smooth_quartz_slab[type=bottom,waterlogged=false]'), 5.5)
    disc(v, 15, 5.5, B('glass'), metric=m)
    # four floating islets on the diagonals, each with an offering socket
    for x in range(-30, 31):
        for z in range(-30, 31):
            a, b = ab(x, z)
            d = math.hypot(a - 20, b - 20)
            if d <= 4.5:
                depth = int(3 * math.sqrt(max(0.0, 1 - (d / 4.5) ** 2)))
                for y in range(4 - depth, 5):
                    v.put(x, y, z, B('smooth_quartz') if y == 4 else B('white_wool'))
    v.sym(20, 5, 20, B('chiseled_quartz_block'))
    mk['offering_sockets'] = [[sx * 20, 6, sz * 20] for sx in (1, -1) for sz in (1, -1)]
    for k in range(1, 4):                                              # stepping clouds from the platform
        a = 12 + k * 2
        v.sym(a, 1 + k, a - 1, B('white_wool'))
        v.sym(a - 1, 1 + k, a, B('white_wool'))
    v.sym(4, 1, 4, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[sx * 4, 1, sz * 4] for sx in (1, -1) for sz in (1, -1)]
    mk['lore'] = [[0, 1, 9]]
    return v, mk


# ---------------------------------------------------------------- void observatory
def void_observatory():
    v, mk = Voxels(), {'pedestal': [], 'barrels': [], 'boss': [], 'lore': []}
    R = 14.5
    for x in range(-22, 23):                                           # an end-stone rim with an open centre
        for z in range(-22, 23):
            d = m(x, z)
            a, b = ab(x, z)
            if 7.5 < d <= R + 5:
                if d > R or (b <= 1) or noise(x, 0, z, 1) < 0.35:
                    v.put(x, 0, z, B('end_stone_bricks') if d <= R + 1 else B('end_stone'))
                for y in range(-4, 0):
                    if d > R + 0.5 and d <= R + 5 - (-y) * 1.1:
                        v.put(x, y, z, B('end_stone'))
            if b <= 1 and d <= 7.5 and d > 2.5:                        # catwalks to the centre
                v.put(x, 0, z, B('purpur_block'))
    disc(v, 0, 2.5, B('purpur_block'), metric=m)
    # four obsidian pylons on the diagonals with crystals of light
    for x in range(-18, 19):
        for z in range(-18, 19):
            a, b = ab(x, z)
            if max(abs(a - 13), abs(b - 13)) <= 1:
                for y in range(-3, 19):
                    v.put(x, y, z, B('obsidian') if y % 6 else B('crying_obsidian'))
    v.sym(13, 19, 13, B('end_rod[facing=up]'))
    # the purpur telescope ring, raised on the pylons, tilted toward the zenith
    for x in range(-15, 16):
        for z in range(-15, 16):
            d = math.hypot(x, z)
            if abs(d - 13.5) <= 1.0:
                v.put(x, 18, z, B('purpur_block'))
                if abs(d - 13.5) <= 0.4:
                    v.put(x, 19, z, B('purpur_pillar[axis=y]'))
    for k in range(0, 10):                                             # the telescope: a purpur tube over the centre
        for x in range(-2, 3):
            for z in range(-2, 3):
                r = math.hypot(x, z)
                if 1.0 < r <= 2.3:
                    v.put(x, 6 + k, z, B('purpur_block') if k % 3 else B('purpur_pillar[axis=y]'))
                elif r <= 1.0 and k == 9:
                    v.put(x, 6 + k, z, B('glass'))
    for y in range(1, 6):                                              # the mount
        v.sym(2, y, 2, B('obsidian'))
    v.put(0, 1, 0, B('chiseled_quartz_block'))
    mk['pedestal'] = [[0, 2, 0]]
    for k in range(1, 7):                                              # parkour floating steps up to the ring
        v.sym(8 + k, 1 + 2 * k if k < 6 else 17, 3 if k % 2 else 5, B('purpur_block'))
    v.sym(15, 1, 0, B('barrel[facing=up,open=false]'))
    mk['barrels'] = [[15, 1, 0], [-15, 1, 0], [0, 1, 15], [0, 1, -15]]
    mk['boss'] = [[0, 1, 5]]
    mk['lore'] = [[0, 1, 17]]
    return v, mk


RUINS = {'greenhouse': greenhouse, 'foundry': foundry, 'sanctuary': sanctuary,
         'antechamber': antechamber, 'void_observatory': void_observatory}
SKIES = {'foundry': ((70, 20, 16), (140, 50, 30)), 'sanctuary': ((30, 50, 40), (70, 100, 80)),
         'antechamber': ((210, 230, 255), (250, 250, 255)), 'void_observatory': ((20, 12, 32), (50, 30, 70))}

if __name__ == '__main__':
    from voxrender import render
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    for name, fn in RUINS.items():
        V, M = fn()
        assert V.is_symmetric(), name + ' is not D4-symmetric'
        xs = [x for (x, _, _) in V]
        ys = [y for (_, y, _) in V]
        print(name, 'blocks', sum(1 for b in V.values() if not b.endswith(':air')), 'height', max(ys) - min(ys) + 1,
              'width', max(xs) - min(xs) + 1)
        with open(os.path.join(out, 'ruin_%s.markers.json' % name), 'w') as f:
            json.dump(M, f, indent=1)
        kw = {'sky': SKIES[name]} if name in SKIES else {}
        render(V, os.path.join(out, 'ruin_%s.png' % name), scale=4, **kw)
    print('ok')
