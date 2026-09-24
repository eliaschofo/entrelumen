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


DESIGNS = {'ruin_act4_observatory': (observatory, 14), 'ruin_act3_greenhouse': (greenhouse, 14)}

if __name__ == '__main__':
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview')
    os.makedirs(out, exist_ok=True)
    for name, (fn, ground) in DESIGNS.items():
        v = fn()
        assert v.is_symmetric(), name + ' is not D4-symmetric'
        iso(v, os.path.join(out, name + '.png'), scale=7, ground=ground)
        print(name, len(v), 'blocks, symmetric')
