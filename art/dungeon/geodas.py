"""Geodas, floor IV of the Envés: rooms that are the inside of geodes. A shell of smooth basalt, a
band of calcite and a lining of amethyst wrap every cave in three dimensions, domes close over them,
and crystal pillars stand in the way.

Rules (Elias, 27/9): tight sightlines round crystal pillars, for the floor of the vexes, the
Watcher and the amethyst crabs; good light anyway. Clusters glow a little; the light comes from
pearlescent froglights set in the pillars like cores and from sprays of end rods, the white crystals.
Nothing here grows: no budding amethyst, so a room is the same every visit. All art is D4 around the
cell centre (the octant is authored once and copied); only doors and the stairwell break it.
Floors on the slab (kit DECK = 0).

References inspected before drawing (hook of AGENTS.md), rendered with voxkit.load_nbt and
voxrender:
- L_Ender's Cataclysm 1.21.1-3.33, `data/cataclysm/structure/amethyst_nest.nbt` (the amethyst crab's
  nest): a broken geode, smooth basalt outside, one block of calcite, amethyst inside with clusters
  pointing into the hollow. Taken: the three layers and their thickness, clusters only on the inner
  face, the layers showing where the shell is cut (here, at every doorway).
- the vanilla geode (worldgen `configured_feature/amethyst_geode.json` in the 1.21.1 JAR: smooth
  basalt, calcite, amethyst, clusters): the same order of layers.
What is ours: the rooms, the domes, the crystal pillars with glowing cores, the end-rod sprays.
"""
from collections import deque

import kit
from kit import AIR, B, C, CEIL_Y, S, H

NAME = 'geodas'
DECK = 0
P = dict(
    base=B('smooth_basalt'),
    floor=B('smooth_basalt'),
    shell=B('smooth_basalt'),
    band=B('calcite'),
    lining=B('amethyst_block'),
    crystal=B('amethyst_block'),
    core=B('pearlescent_froglight', axis='y'),
    wall=B('smooth_basalt'),
    pillar=B('calcite'),
    capital=B('calcite'),
    landing=B('polished_deepslate'),
    step='minecraft:polished_deepslate_stairs',
    corbel='minecraft:polished_deepslate_stairs',
    well=B('polished_deepslate'),
    threshold=B('calcite'),
    lintel=B('calcite'),
    jamb=B('calcite'),
    door_lamp=B('pearlescent_froglight', axis='y'),
    ceil=B('smooth_basalt'),
    vein=B('calcite'),
    tile=B('polished_deepslate'),
)
ROD = 'end_rod'


def cluster(facing, size=3):
    return B(('small_amethyst_bud', 'medium_amethyst_bud', 'large_amethyst_bud', 'amethyst_cluster')[size],
             facing=facing)


# ------------------------------------------------------------------ the cave
def dome(r2, y, top=9, spring=6):
    """Radius squared of the cave at height y: full up to the spring line, then closing."""
    if y <= spring:
        return r2
    k = (y - spring) / float(top + 1 - spring)
    return r2 * (1 - k * k * 0.9)


def cavern(is_open, floor_at, pillars=(), cores=(), stalactites=()):
    """Voxels of a geode cave. is_open(dx, dz, y) says where the hollow is (a D4-invariant rule);
    floor_at(dx, dz) gives the floor block; pillars and stalactites are canonical (dx, dz[, from_y])."""
    hollow = set()
    pil = set()
    for (dx, dz) in pillars:
        for t in kit.T8:
            px, pz = kit.tf(dx, dz, t)
            pil.add((px, pz))
    for x in range(1, S - 1):
        for z in range(1, S - 1):
            dx, dz = x - C, z - C
            if (dx, dz) in pil:
                continue
            for y in range(1, CEIL_Y):
                if is_open(dx, dz, y):
                    hollow.add((x, y, z))
    stal = {}
    for (dx, dz, low) in stalactites:
        for t in kit.T8:
            px, pz = kit.tf(dx, dz, t)
            stal[(px, pz)] = low
    for (x, y, z) in list(hollow):
        low = stal.get((x - C, z - C))
        if low is not None and y >= low:
            hollow.discard((x, y, z))
    # distance of every solid cell to the hollow, through faces: 1 lining, 2 band, 3+ shell
    dist = {p: 0 for p in hollow}
    q = deque(hollow)
    while q:
        x, y, z = q.popleft()
        d = dist[(x, y, z)]
        if d >= 3:
            continue
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if 0 <= n[0] < S and 0 <= n[1] < H and 0 <= n[2] < S and n not in dist:
                dist[n] = d + 1
                q.append(n)
    v = {}
    for x in range(S):
        for z in range(S):
            for y in range(H):
                p = (x, y, z)
                if p in hollow:
                    v[p] = AIR
                elif y == 0 and (x, 1, z) in hollow:
                    v[p] = floor_at(x - C, z - C)
                else:
                    d = dist.get(p, 9)
                    v[p] = P['lining'] if d == 1 else (P['band'] if d == 2 else P['shell'])
    for (dx, dz) in pil:                                      # pillars: amethyst on a calcite foot
        x, z = C + dx, C + dz
        for y in range(1, CEIL_Y):
            if v.get((x, y, z)) != AIR:
                v[(x, y, z)] = P['crystal'] if y > 1 else P['pillar']
        v[(x, 0, z)] = P['tile']
    for (dx, dz) in pil:                                      # a glowing band where a pillar meets the hollow
        x, z = C + dx, C + dz
        if any((x + fx, 3, z + fz) in hollow for fx, fz in kit.FACE.values()):
            v[(x, 3, z)] = P['core']
    for (dx, dz) in cores:
        kit.sym_put(v, dx, 4, dz, P['core'])
    return v


def grow(v, salt, density=0.35, rods=0.0):
    """Clusters (and a few end-rod crystals) on the amethyst lining, facing into the hollow. Decided in
    the canonical octant and copied, so the growth is D4-symmetric too."""
    for dx in range(0, 9):
        for dz in range(0, dx + 1):
            for y in range(1, CEIL_Y):
                x, z = C + dx, C + dz
                if v.get((x, y, z)) != AIR:
                    continue
                options = []
                if v.get((x, y - 1, z)) == P['lining'] and y >= 2:
                    options.append('up')
                if v.get((x, y + 1, z)) == P['lining'] and y >= 5:
                    options.append('down')
                for f, (fx, fz) in kit.FACE.items():
                    if v.get((x - fx, y, z - fz)) == P['lining']:
                        if dz == 0 and f in ('north', 'south'):
                            continue                          # not mirror-invariant on the axis
                        if dx == dz and f in ('north', 'south', 'east', 'west'):
                            continue
                        options.append(f)
                if not options:
                    continue
                r = kit.rnd(salt, dx, dz, y)
                if r >= density:
                    continue
                f = options[int(kit.rnd(salt + 1, dx, dz, y) * len(options))]
                if y <= 2 and f not in ('up', 'down'):
                    continue                                  # nothing to trip on at the foot of walls
                if rods and kit.rnd(salt + 2, dx, dz, y) < rods and f == 'down':
                    kit.sym_put(v, dx, y, dz, B(ROD, facing=f))
                else:
                    size = 3 if r < density * 0.45 else (2 if r < density * 0.75 else 1)
                    kit.sym_put(v, dx, y, dz, cluster(f, size))


def floor_ring(bands):
    """A floor of rings: bands = [(r2_max, block), ...] from the centre out."""
    def at(dx, dz):
        r2 = dx * dx + dz * dz
        for lim, block in bands:
            if r2 <= lim:
                return block
        return P['floor']
    return at


# ------------------------------------------------------------------ rooms
R2 = 72                                                      # a disc touching the walls at the axes


def disc(r2=R2, top=9, spring=6):
    return lambda dx, dz, y: dx * dx + dz * dz <= dome(r2, y, top, spring)


KIND_NAMES = {0: 'Bosque de cristal', 1: 'Drusa', 2: 'Grieta', 'guard': 'Gran hueco', 'arena': 'Gran hueco',
              'corridor': 'Veta'}


def kind_of(role, variant):
    if role in ('quiet', 'fight'):
        return variant % 3
    return {'guard': 'guard', 'shrine': 1, 'seal': 1, 'vault': 1, 'start': 1, 'exit': 1, 'vestibule': 1,
            'portal': 1, 'arena': 'arena', 'arena_center': 'arena'}[role]


def room(role, variant):
    kind = kind_of(role, variant)
    if kind == 0:                                            # the crystal forest
        v = cavern(disc(), floor_ring([(4, P['tile']), (20, P['floor']), (26, P['vein']), (50, P['floor']),
                                       (58, P['vein'])]),
                   pillars=[(4, 4), (5, 4), (5, 5), (6, 2), (3, 1)], cores=[(5, 5)])
        grow(v, 11, density=0.4, rods=0.5)
    elif kind == 1:                                          # the vug: one great crystal in the middle
        v = cavern(disc(spring=5), floor_ring([(8, P['tile']), (13, P['vein']), (40, P['floor']), (45, P['vein'])]),
                   stalactites=[(0, 0, 8), (1, 0, 9), (1, 1, 9)])
        if role not in ('start', 'exit', 'vestibule', 'shrine', 'seal', 'portal', 'vault'):
            great_crystal(v)
        shards(v, [(6, 3)])
        grow(v, 23, density=0.45, rods=0.5)
    elif kind == 2:                                          # the fissure: a cross of caves
        def cross(dx, dz, y):
            a, b = kit.octant(dx, dz)
            r2 = dx * dx + dz * dz
            return (b <= 2 and a <= 8 and y <= 7 - (1 if a >= 7 else 0)) or r2 <= dome(34, y, spring=5)
        v = cavern(cross, floor_ring([(2, P['core']), (9, P['tile']), (18, P['vein'])]),
                   pillars=[(4, 4), (3, 4)], cores=[])
        grow(v, 37, density=0.4, rods=0.5)
    else:                                                    # guard and arena: the great hollow
        v = cavern(disc(R2 + 8, spring=7), floor_ring([(2, P['tile']), (10, P['vein']), (12, P['tile']),
                                                       (40, P['floor']), (46, P['vein'])]),
                   pillars=[(6, 5), (6, 6), (5, 6)] if kind == 'guard' else [(6, 6)], cores=[(6, 6)])
        shards(v, [(7, 2), (3, 7)] if kind == 'guard' else [(7, 2)])
        grow(v, 53, density=0.35, rods=0.5)
    if role in ('shrine', 'seal', 'portal'):
        dais(v)
    return v


def shards(v, spots):
    """Glowing shards standing on the floor: a froglight foot, amethyst over it, a cluster on top."""
    for (dx, dz) in spots:
        kit.sym_put(v, dx, 1, dz, P['core'])
        kit.sym_put(v, dx, 2, dz, P['crystal'])
        kit.sym_put(v, dx, 3, dz, cluster('up', 3))


def great_crystal(v):
    """A mass of amethyst three wide and four high in the middle, clusters on its shoulders."""
    for dx in range(0, 2):
        for dz in range(0, dx + 1):
            for y in range(1, 5 - dx):
                kit.sym_put(v, dx, y, dz, P['crystal'])
    kit.sym_put(v, 0, 5, 0, cluster('up', 3))
    kit.sym_put(v, 1, 4, 0, cluster('up', 3))
    kit.sym_put(v, 1, 4, 1, cluster('up', 2))
    kit.sym_put(v, 1, 2, 0, P['core'])


def dais(v):
    """A 3x3 calcite dais one step up, amethyst in the middle, stairs on its four sides."""
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            v[(C + dx, 0, C + dz)] = P['tile']
            v[(C + dx, 1, C + dz)] = P['crystal'] if (dx, dz) == (0, 0) else P['band']
            for y in range(2, 5):
                if v.get((C + dx, y, C + dz)) not in (None,):
                    v[(C + dx, y, C + dz)] = AIR
    kit.sym_put(v, 2, 1, 0, kit.stairs(P['step'], 'west'))
    kit.sym_put(v, 2, 1, 1, kit.stairs(P['step'], 'west'))


def corridor(variant):
    """An east-west crack through the rock: a rounded tunnel lined with amethyst, crystals on its
    walls; variant 2 widens it in the middle into a small chamber."""
    def tunnel(dx, dz, y):
        t = abs(dz)
        if variant % 3 == 2 and dx * dx + dz * dz <= dome(20, y, top=7, spring=4):
            return True
        return (t <= 2 and y <= 4) or (t <= 1 and y <= 6) or (t == 0 and y <= 7 and abs(dx) % 4 != 0)
    hollow_v = cavern_d2(tunnel, lambda dx, dz: P['vein'] if dz == 0 else P['floor'])
    if variant % 3 == 2:                                  # the chamber's four glowing shards
        for (dx, dz) in ((2, 3), (3, 2)):
            if hollow_v.get((C + dx, 1, C + dz)) == AIR:
                kit.mirror_put(hollow_v, dx, 1, dz, P['core'])
                kit.mirror_put(hollow_v, dx, 2, dz, P['crystal'])
                kit.mirror_put(hollow_v, dx, 3, dz, cluster('up', 3))
                break
    grow_d2(hollow_v, 71 + variant, density=0.45, rods=0.35)
    return hollow_v


def cavern_d2(is_open, floor_at):
    """cavern() for corridors: the rule only needs the two mirrors."""
    return cavern(is_open, floor_at)


def grow_d2(v, salt, density=0.4, rods=0.3):
    """Clusters for corridors, decided in one quadrant and mirrored across both axes."""
    for dx in range(0, 9):
        for dz in range(0, 9):
            for y in range(1, CEIL_Y):
                x, z = C + dx, C + dz
                if v.get((x, y, z)) != AIR:
                    continue
                options = []
                if v.get((x, y + 1, z)) == P['lining']:
                    options.append('down')
                for f, (fx, fz) in kit.FACE.items():
                    if v.get((x - fx, y, z - fz)) == P['lining']:
                        if dz == 0 and f in ('north', 'south'):
                            continue
                        if dx == 0 and f in ('east', 'west'):
                            continue
                        options.append(f)
                if not options or y <= 2:
                    continue
                r = kit.rnd(salt, dx, dz, y)
                if r >= density:
                    continue
                f = options[int(kit.rnd(salt + 1, dx, dz, y) * len(options))]
                if rods and kit.rnd(salt + 2, dx, dz, y) < rods:
                    kit.mirror_put(v, dx, y, dz, B(ROD, facing=f))
                else:
                    kit.mirror_put(v, dx, y, dz, cluster(f, 3 if r < density * 0.5 else 2))


def rot4(dx, dz):
    return [(C + dx, C + dz), (C - dz, C + dx), (C - dx, C - dz), (C + dz, C - dx)]


SPAWN = {0: 3, 1: 4, 2: 3, 'guard': 4, 'arena': 4}
CHEST = {0: (7, 2), 1: (6, 3), 2: (7, 1), 'guard': (7, 3), 'arena': (7, 3)}


def spots(role, variant, corridor):
    kind = kind_of(role, variant)
    d = SPAWN[kind]
    return {'encounter': [(C - d, C - d), (C + d, C - d), (C + d, C + d), (C - d, C + d)],
            'arrival': [(C - 3, C - 3)],
            'chest': rot4(*CHEST[kind]) + rot4(6, 1) + rot4(5, 2),
            'boss_chest': [(C - 3, C - 3), (C + 3, C - 3)],
            'portal': [(C + 7, C)]}
