"""Anchor ruins of acts II to V, SKETCHES (story bible: one memorable Heliodor ruin per act).

Every design is D4-symmetric around its vertical axis. No placement code yet: the compass
objective list and the ruin placement decide where each goes. Centred coordinates, layer 0 is the
floor that replaces the ground surface.

    python art/structures/ruins_acts.py      # previews into art/structures/preview/
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voxkit import Voxels, ab, iso  # noqa: E402

B = lambda n: 'minecraft:' + n


def disc(v, y, r, block, ring=None):
    for x in range(-int(r) - 1, int(r) + 2):
        for z in range(-int(r) - 1, int(r) + 2):
            d = math.hypot(x, z)
            if d <= r and (ring is None or d >= ring):
                v.put(x, y, z, block)


# ---------------- Act IV: observatory on a crag ----------------
def observatory():
    """A round calcite tower on a rock plinth; a zenith telescope rises through the broken dome."""
    v = Voxels()
    for y in range(0, 4):                                        # rock plinth, stepping in
        disc(v, y, 8.5 - y * 1.2, B('tuff') if y % 2 else B('stone'))
    disc(v, 3, 5.4, B('polished_tuff'))
    for y in range(4, 17):                                       # tower wall
        for x in range(-6, 7):
            for z in range(-6, 7):
                d = math.hypot(x, z)
                if 4.4 <= d < 5.5:
                    a, b = ab(x, z)
                    window = y in (8, 9, 12, 13) and b == 0
                    door = y in (4, 5, 6) and b == 0 and a >= 4
                    band = y in (7, 11, 15)
                    if door or window:
                        v.put(x, y, z, B('air'))
                    else:
                        v.put(x, y, z, B('waxed_oxidized_cut_copper') if band else B('calcite'))
    for y in range(4, 17):                                       # ladders on the four axes
        v.sym(4, y, 0, B('ladder[facing=west,waterlogged=false]'))
    for y in (10, 14):                                           # two floors, open round the ladders
        for x in range(-4, 5):
            for z in range(-4, 5):
                a, b = ab(x, z)
                if math.hypot(x, z) < 4.4 and not (a >= 3 and b == 0) and a + b > 1:
                    v.put(x, y, z, B('spruce_planks'))
    # dome with four diagonal slits (the ones that opened to the sky)
    for x in range(-6, 7):
        for z in range(-6, 7):
            for dy in range(0, 7):
                rho = math.sqrt(x * x + z * z + (dy * 1.05) ** 2)
                if 4.4 <= rho <= 5.5:
                    a, b = ab(x, z)
                    th = math.degrees(math.atan2(b, a)) if a else 0
                    if th > 34 and dy >= 1:
                        continue
                    if math.hypot(x, z) < 1.5:
                        continue
                    v.put(x, 17 + dy, z, B('waxed_weathered_cut_copper') if dy < 3 else B('waxed_exposed_cut_copper'))
    # zenith telescope: grate mount on the top floor, a copper tube through the oculus, a lens on top
    for x in range(-1, 2):
        for z in range(-1, 2):
            v.put(x, 15, z, B('waxed_copper_grate'))
    for y in range(15, 25):
        v.put(0, y, 0, B('waxed_cut_copper') if y < 24 else B('light_blue_stained_glass'))
    v.sym(1, 22, 0, B('waxed_copper_grate'))
    return v


# ---------------- Act III: the dome greenhouse ----------------
def greenhouse():
    """A glass dome over an overgrown garden, half its panes gone, a flowering tree in the middle."""
    v = Voxels()
    R = 10
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            if d <= R + 0.5:
                if d > R - 0.6:
                    v.put(x, 0, z, B('polished_tuff'))
                elif b <= 1 or a == b:
                    v.put(x, 0, z, B('calcite'))
                elif (a * 3 + b * 5) % 7 == 0:
                    v.put(x, 0, z, B('water'))
                else:
                    v.put(x, 0, z, B('moss_block'))
                    if (a + 2 * b) % 5 == 0:
                        v.put(x, 1, z, B('azure_bluet'))
                    elif (a * b) % 4 == 1:
                        v.put(x, 1, z, B('short_grass'))
    # dome frame: copper ribs on the axes, diagonals and two rings; glass between, one pane in four gone
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            for y in range(1, R + 1):
                rho = math.sqrt(x * x + z * z + (y * 1.1) ** 2)
                if R - 0.7 <= rho <= R + 0.3:
                    a, b = ab(x, z)
                    rib = (b == 0 or a == b) and y <= 8 or y in (3, 7)
                    if rib:
                        v.put(x, y, z, B('waxed_oxidized_cut_copper'))
                    elif y <= 6 and (a * 5 + b * 3 + y) % 4 != 0:
                        v.put(x, y, z, B('glass'))
    # four doorways on the axes
    for y in range(1, 4):
        for w in (0, 1):
            for s in range(R - 2, R + 1):
                v.sym(s, y, w, B('air'))
    # the tree: stripped birch trunk, flowering azalea crown
    for y in range(1, 11):
        v.put(0, y, 0, B('stripped_birch_log[axis=y]'))
    for x in range(-4, 5):
        for z in range(-4, 5):
            for y in range(8, 14):
                if abs(x) + abs(z) + abs(y - 10) * 1.5 <= 5 and (x, y, z) not in v:
                    v.put(x, y, z, B('flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]'))
    return v


# ---------------- Act II: the sunken workshop ----------------
def workshop():
    """A square pit five blocks deep, reached by four stairways, around a drowned copper engine.
    Layer 0 is the ground rim; the workshop floor is at y = -5 (placement sinks the template)."""
    v = Voxels()
    D, H = 5, 8                     # pit depth, half size of the rim
    for x in range(-H - 1, H + 2):
        for z in range(-H - 1, H + 2):
            a, b = ab(x, z)
            if a <= H - 2:
                v.put(x, -D, z, B('polished_tuff') if (a + b) % 2 else B('tuff_bricks'))
                if a <= 2:
                    v.put(x, -D + 1, z, B('water'))
                    v.put(x, -D, z, B('calcite'))
                for y in range(-D + 1, 1):
                    if (x, y, z) not in v:
                        v.put(x, y, z, B('air'))
            elif a == H - 1:
                for y in range(-D, 0):
                    pipe = b % 4 == 2 and y > -D
                    v.put(x, y, z, B('waxed_oxidized_copper_grate') if pipe else B('tuff_bricks'))
                v.put(x, 0, z, B('polished_tuff'))
            else:
                v.put(x, 0, z, B('polished_tuff') if a == H else B('tuff'))
    # stairways down the middle of each side
    for step in range(0, D):
        for w in (0, 1):
            s = H - 1 - step
            v.sym(s, -step, w, B('tuff_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'))
            for y in range(-step + 1, 1):
                v.sym(s, y, w, B('air'))
    # railing around the rim, open at the stairs
    for b in range(2, H + 1):
        v.sym(H, 1, b, B('waxed_oxidized_copper_grate'))
    # the drowned engine: chiseled copper core with a crafter heart, lit bulbs gone dark
    for y in range(-D + 1, -D + 4):
        v.put(0, y, 0, B('waxed_oxidized_chiseled_copper') if y != -D + 2 else B('crafter'))
    v.sym(1, -D + 2, 0, B('waxed_oxidized_copper_bulb[lit=false,powered=false]'))
    v.sym(1, -D + 1, 1, B('waxed_oxidized_cut_copper'))
    # four benches in the corners, with a fallen beam across each
    for (a, b) in ((5, 5), (5, 4), (4, 5)):
        v.sym(a, -D + 1, b, B('smithing_table') if (a, b) == (5, 5) else B('waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]'))
    v.sym(4, -D + 1, 3, B('moss_carpet'))
    return v


# ---------------- Act V: the Temple of the Sacred Light ----------------
def temple():
    """A stepped calcite temple with gold-trimmed terraces; at the top, the fusion chamber is a
    scorched open crater with the broken ring of the machine standing on four pillars."""
    v = Voxels()
    tiers = [(12, 3), (9, 3), (6, 2)]            # (half size, height) of each terrace
    y0 = 0
    for half, height in tiers:
        for x in range(-half, half + 1):
            for z in range(-half, half + 1):
                a, b = ab(x, z)
                for y in range(y0, y0 + height):
                    edge = a == half
                    blk = 'calcite' if not edge else ('smooth_quartz' if y < y0 + height - 1 else 'waxed_cut_copper')
                    v.put(x, y, z, B(blk))
        y0 += height
    # four grand stairways on the axes, up to the top terrace
    for step in range(0, y0):
        for w in range(0, 2):
            v.sym(13 - step, step, w, B('quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
            for y in range(step + 1, y0 + 1):
                v.sym(13 - step, y, w, B('air'))
    # the crater: the top terrace scorched and broken open
    for x in range(-5, 6):
        for z in range(-5, 6):
            d = math.hypot(x, z)
            if d <= 4.5:
                depth = 3 if d < 2 else 2 if d < 3.5 else 1
                for y in range(y0 - depth, y0):
                    v.put(x, y, z, B('air'))
                v.put(x, y0 - depth - 1, z, B('crying_obsidian') if d < 1.5 else B('blackstone') if d < 3.2 else B('basalt[axis=y]'))
    # the machine: a broken copper ring on four gilded pillars
    for (a, b) in ((4, 4),):
        for y in range(y0, y0 + 5):
            v.sym(a, y, b, B('gold_block') if y == y0 + 4 else B('quartz_pillar[axis=y]'))
    for x in range(-5, 6):
        for z in range(-5, 6):
            d = math.hypot(x, z)
            a, b = ab(x, z)
            if 4.6 <= d <= 5.8:
                th = math.degrees(math.atan2(b, a)) if a else 0
                if th > 12:                               # the ring survives only where it rests on the pillars
                    v.put(x, y0 + 5, z, B('waxed_cut_copper'))
    # obelisks at the base corners with end rods
    for y in range(0, 7):
        v.sym(13, y, 13, B('smooth_quartz') if y < 6 else B('gold_block'))
    v.sym(13, 7, 13, B('end_rod[facing=up]'))
    return v


DESIGNS = {'ruin_act2_workshop': (workshop, 12), 'ruin_act3_greenhouse': (greenhouse, 14),
           'ruin_act4_observatory': (observatory, 14), 'ruin_act5_temple': (temple, 18)}

if __name__ == '__main__':
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview')
    os.makedirs(out, exist_ok=True)
    for name, (fn, ground) in DESIGNS.items():
        v = fn()
        assert v.is_symmetric(), name + ' is not D4-symmetric'
        iso(v, os.path.join(out, name + '.png'), scale=7, ground=ground)
        print(name, len(v), 'blocks, symmetric')
