"""Heliodor ruins in other dimensions, SKETCHES (story bible: Nether foundry under the lava,
Twilight Forest sanctuary, Aether antechamber of the Sun Spirit, End observatory over the void).

D4-symmetric, vanilla blocks only for now (the Aether and Twilight palettes can swap in their mods'
blocks later). No placement code yet. Centred coordinates; layer 0 is the floor.

    python art/structures/ruins_dims.py      # textured previews into art/structures/preview/
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voxkit import Voxels, ab  # noqa: E402

B = lambda n: 'minecraft:' + n


def disc(v, y, r, block, r0=-1.0):
    n = int(r) + 1
    for x in range(-n, n + 1):
        for z in range(-n, n + 1):
            d = math.hypot(x, z)
            if r0 < d <= r:
                v.put(x, y, z, block)


# ---------------- Nether: the foundry under the lava ----------------
def foundry():
    """A blackstone crucible hall sunk in a lava lake: basalt buttresses, copper pipes, a molten
    crucible at the centre under a hanging chain hood, glowing magma seams."""
    v = Voxels()
    for x in range(-14, 15):
        for z in range(-14, 15):
            a, b = ab(x, z)
            d = math.hypot(x, z)
            if d <= 14.3:
                v.put(x, -1, z, B('lava') if d > 11.5 else B('polished_blackstone_bricks'))
                if d <= 11.5:
                    v.put(x, 0, z, B('magma_block') if (a + b) % 6 == 0 else B('polished_blackstone'))
    # buttresses of basalt on the diagonals and axes, leaning inward
    for k in range(0, 3):
        th = math.radians(k * 22.5)
        for y in range(1, 13):
            r = 10.5 - y * 0.35
            x, z = round(r * math.cos(th)), round(r * math.sin(th))
            v.sym(x, y, z, B('polished_basalt[axis=y]') if y % 4 else B('gilded_blackstone'))
    # ring beam and a copper roof ring
    for x in range(-9, 10):
        for z in range(-9, 10):
            d = math.hypot(x, z)
            if 6.5 <= d <= 7.4:
                v.put(x, 13, z, B('waxed_oxidized_cut_copper'))
            if 5.5 <= d <= 6.4 and ab(x, z)[1] % 3 == 0:
                v.put(x, 14, z, B('waxed_copper_grate'))
    # the crucible: a molten bowl on a stepped plinth, a chain hood above
    disc(v, 1, 3.4, B('polished_blackstone_bricks'))
    disc(v, 2, 2.6, B('gilded_blackstone'), 1.6)
    disc(v, 2, 1.6, B('lava'))
    for y in range(3, 12):
        v.sym(2, y, 2, B('chain[axis=y,waterlogged=false]'))
    disc(v, 12, 2.6, B('waxed_oxidized_copper_grate'))
    # copper pipes running from the crucible to four furnaces on the axes
    for s in range(3, 9):
        v.sym(s, 1, 0, B('waxed_copper_grate'))
    for y in range(1, 4):
        v.sym(9, y, 0, B('blast_furnace[facing=west,lit=true]') if y == 1 else B('polished_blackstone_bricks'))
    v.sym(9, 4, 0, B('waxed_copper_bulb[lit=true,powered=false]'))
    return v


# ---------------- Twilight Forest: the sanctuary ----------------
def sanctuary():
    """A mossy ring of standing stones around a sunken pool, a giant flowering tree at the centre
    whose roots hold four lanterns, fireflies of froglight in the canopy."""
    v = Voxels()
    for x in range(-13, 14):
        for z in range(-13, 14):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            if d <= 13.3:
                v.put(x, 0, z, B('moss_block') if d > 7.5 or (a + b) % 4 else B('mossy_stone_bricks'))
                if 5.2 < d <= 7.5:
                    v.put(x, 0, z, B('water'))
                    v.put(x, -1, z, B('mossy_cobblestone'))
                if d > 8 and (a * 3 + b * 5) % 7 == 0:
                    v.put(x, 1, z, B(('fern', 'azure_bluet', 'lily_of_the_valley')[(a + b) % 3]))
    # standing stones on the ring, capped with lintels between pairs on the axes
    for k in range(0, 3):
        th = math.radians(k * 22.5)
        x, z = round(11 * math.cos(th)), round(11 * math.sin(th))
        h = 6 if k != 1 else 4
        for y in range(1, h + 1):
            v.sym(x, y, z, B('mossy_stone_bricks') if y % 2 else B('chiseled_stone_bricks'))
        v.sym(x, h + 1, z, B('moss_carpet'))
    for w in range(-2, 3):
        v.sym(11, 7, w, B('mossy_stone_brick_slab[type=bottom,waterlogged=false]'))
    # the tree: a 3x3 trunk rising from an island, eight roots bridging the pool
    for y in range(-1, 14):
        for x in (-1, 0, 1):
            for z in (-1, 0, 1):
                v.put(x, y, z, B('dark_oak_log[axis=y]'))
    for s_ in range(2, 8):
        y = 1 if s_ < 5 else 0
        v.sym(s_, y, 0, B('dark_oak_wood[axis=y]'))
        if s_ < 6:
            v.sym(s_ - 1, y, s_ - 1, B('dark_oak_wood[axis=y]'))
    for x in range(-8, 9):
        for z in range(-8, 9):
            for y in range(10, 18):
                d = math.sqrt(x * x + z * z + ((y - 13) * 1.6) ** 2)
                if d <= 7.8 and (x, y, z) not in v:
                    a, b = ab(x, z)
                    v.put(x, y, z, B('verdant_froglight') if (a * 7 + b * 3 + y) % 23 == 0 else
                          B('flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]'))
    return v


# ---------------- Aether: the antechamber of the Sun Spirit ----------------
def antechamber():
    """A floating white-and-gold platform in the clouds, a sun gate on each side, a golden sun disc
    set in the floor and a stair of light rising toward the Sun Spirit's temple."""
    v = Voxels()
    for x in range(-12, 13):
        for z in range(-12, 13):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            if d <= 12.3:
                v.put(x, 0, z, B('smooth_quartz') if d > 5.5 else (B('gold_block') if (a + b) % 2 == 0 else B('yellow_stained_glass')))
                depth = int((12.3 - d) * 0.6) + 1
                for y in range(-depth, 0):
                    v.put(x, y, z, B('quartz_block') if y > -3 else B('white_concrete'))
            if 12.3 < d <= 15 and (a * 5 + b * 3) % 4:
                v.put(x, -1, z, B('white_wool'))
                if (a + b) % 3 == 0:
                    v.put(x, 0, z, B('white_wool'))
    # four sun gates on the axes: quartz pillars with a gold arch
    for w in (3, 4):
        for y in range(1, 9):
            v.sym(11, y, w, B('quartz_pillar[axis=y]'))
    for w in range(0, 5):
        v.sym(11, 9, w, B('gold_block') if w < 3 else B('chiseled_quartz_block'))
    v.sym(11, 10, 0, B('end_rod[facing=up]'))
    # the rising stair of light at the centre
    for step in range(0, 8):
        r = 4.5 - step * 0.5
        disc(v, 1 + step, r, B('white_stained_glass') if step % 2 else B('yellow_stained_glass'), r - 1)
    disc(v, 9, 1.6, B('shroomlight'))
    return v


# ---------------- End: the observatory over the void ----------------
def end_observatory():
    """A purpur-and-end-stone observatory on a broken outcrop, a great ring telescope pointing at the
    zenith, obsidian pylons, and the floor cut open to the void at the centre."""
    v = Voxels()
    for x in range(-13, 14):
        for z in range(-13, 14):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            if 3.5 < d <= 13.3:
                v.put(x, 0, z, B('end_stone_bricks') if d > 6 else B('purpur_block'))
                depth = int((13.3 - d) * 0.8) + 2
                for y in range(-depth, 0):
                    v.put(x, y, z, B('end_stone'))
            if 3.5 < d <= 4.5:
                v.put(x, 1, z, B('purpur_slab[type=bottom,waterlogged=false]'))
    # obsidian pylons on the diagonals with end rods
    for y in range(1, 10):
        v.sym(9, y, 9, B('obsidian') if y % 3 else B('crying_obsidian'))
    v.sym(9, 10, 9, B('end_rod[facing=up]'))
    # the ring telescope: two purpur arches holding a horizontal ring above the void
    for y in range(1, 12):
        v.sym(7, y, 0, B('purpur_pillar[axis=y]'))
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = math.hypot(x, z)
            if 6.5 <= d <= 7.5:
                v.put(x, 12, z, B('purpur_block'))
            if 2.5 <= d <= 3.4:
                v.put(x, 16, z, B('waxed_oxidized_cut_copper'))
    for y in range(13, 16):
        v.sym(5, y, 5, B('end_rod[facing=up]'))
    disc(v, 17, 2.4, B('purple_stained_glass'))
    return v


DESIGNS = {'ruin_nether_foundry': (foundry, None), 'ruin_twilight_sanctuary': (sanctuary, None),
           'ruin_aether_antechamber': (antechamber, None), 'ruin_end_observatory': (end_observatory, None)}

if __name__ == '__main__':
    from voxrender import render
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview')
    os.makedirs(out, exist_ok=True)
    skies = {'ruin_nether_foundry': ((60, 16, 12), (120, 40, 20)), 'ruin_twilight_sanctuary': ((24, 44, 36), (60, 90, 70)),
             'ruin_aether_antechamber': ((200, 225, 255), (250, 250, 255)), 'ruin_end_observatory': ((20, 14, 32), (40, 30, 60))}
    for name, (fn, _) in DESIGNS.items():
        v = fn()
        sym = v.is_symmetric()
        render(v, os.path.join(out, name + '.png'), scale=7, sky=skies[name])
        print(name, len(v), 'blocks', 'symmetric' if sym else 'NOT symmetric')
