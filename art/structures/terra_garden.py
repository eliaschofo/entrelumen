"""Terra's hydroponic garden as a multiblock: the layout the core validates and the plan projects.

Reference (AGENTS.md hook, inspected before drawing): the pack's own act III greenhouse dome
(art/structures/preview/ruin_act3_greenhouse.png, Juan's garden that fed multitudes) for the
solarpunk language of calcite, verdigris copper, glass and greenery; Immersive Engineering's Garden
Cloche (block/metal_device/cloche.png: a glass bell on a machine base) and Supplementaries' planter
(block/planter_side.png) for how a growing machine reads at block scale; vanilla calcite, oxidized
copper (cut, chiseled, grate, bulb), glass, moss, lanterns and spore blossoms for the materials.

The garden is a stepped pyramid of hydroponic troughs (16, 8 and 1: the cascade) under a glass
canopy on four verdigris pillars, on a calcite plinth with a moss root bed. Grow light comes from the
copper bulb in the canopy's centre, four hanging lanterns and four spore blossoms. The core sits in
the middle of the front edge of the plinth, facing out, so a chest or a pipe goes right in front of it.

Coordinates: x across (mirror-symmetric about x = 0), y up, z toward the front (+z, south in the
drawing); positions are written relative to the core. Copper blocks match any oxidation stage and
waxing (the core normalises them like the Ark does). Every position is required: the plan shows
exactly what the core checks.

Writes companion/src/main/resources/data/entrelumen/terra_garden.json.

    python art/structures/terra_garden.py              # write the layout
    python art/structures/terra_garden.py --check      # compare with the repo
    python art/structures/terra_garden.py --render DIR # review renders (software, not game captures)
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'data', 'entrelumen', 'terra_garden.json')

CORE = 'entrelumen:terra_garden_core'
TROUGH = 'entrelumen:hydroponic_trough'
CORE_AT = (0, 0, 3)          # in drawing coordinates (garden centred on x = z = 0)
HALF = 3                     # 7 x 7


def B(name):
    return name if ':' in name else 'minecraft:' + name


def build():
    V = {}

    def put(x, y, z, block):
        V[(x, y, z)] = B(block)

    def mx(x, y, z, block):
        put(x, y, z, block)
        put(-x, y, z, block)

    ring = lambda r: [(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1) if max(abs(x), abs(z)) == r]
    square = lambda r: [(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1)]

    # y 0: the plinth. Chiseled copper corners, calcite edges, the core in the middle of the front edge,
    # and a 5 x 5 moss root bed inside.
    for x, z in ring(HALF):
        corner = abs(x) == HALF and abs(z) == HALF
        put(x, 0, z, 'oxidized_chiseled_copper' if corner else 'calcite')
    for x, z in square(HALF - 1):
        put(x, 0, z, 'moss_block')
    put(*CORE_AT, CORE)

    # y 1-3: the cascade. 16 troughs on the 5 x 5 ring, 8 on the 3 x 3 ring over a calcite plinth, one
    # on top of a calcite stem.
    for x, z in ring(2):
        put(x, 1, z, TROUGH)
    for x, z in square(1):
        put(x, 1, z, 'calcite')
    for x, z in ring(1):
        put(x, 2, z, TROUGH)
    put(0, 2, 0, 'calcite')
    put(0, 3, 0, TROUGH)

    # the green trim round the plinth: flowering azalea hedges flank each pillar, moss carpet between them,
    # and a lantern stands on the core
    for x, z in ring(HALF):
        if abs(x) == HALF and abs(z) == HALF:
            continue
        hedge = abs(x) == HALF - 1 or abs(z) == HALF - 1
        put(x, 1, z, 'flowering_azalea_leaves' if hedge else 'moss_carpet')
    put(CORE_AT[0], 1, CORE_AT[2], 'lantern[hanging=false,waterlogged=false]')

    # four verdigris pillars, y 1-3, each with a copper bulb for a lamp in the middle, under chiseled capitals
    for sx in (-HALF, HALF):
        for sz in (-HALF, HALF):
            put(sx, 1, sz, 'oxidized_cut_copper')
            put(sx, 2, sz, 'oxidized_copper_bulb[lit=true,powered=false]')
            put(sx, 3, sz, 'oxidized_cut_copper')
            put(sx, 4, sz, 'oxidized_chiseled_copper')

    # y 4: the four arches' shoulders, upside-down stairs leaning on the pillars
    toward = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}
    for t in (-2, 2):
        s = 1 if t > 0 else -1
        for edge in (-HALF, HALF):
            put(t, 4, edge, 'oxidized_cut_copper_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % toward[(s, 0)])
            put(edge, 4, t, 'oxidized_cut_copper_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % toward[(0, s)])

    # y 5-8: the stepped glass dome that answers the cascade. A copper eave (slabs, chiseled corners) on the
    # pillars; then glass, with copper grate ribs on the axes and the diagonals, so from above the dome is
    # a sunburst; an ochre froglight for the sun at the top and a copper rod over it
    rib = lambda x, z: x == 0 or z == 0 or abs(x) == abs(z)
    for x, z in ring(HALF):
        corner = abs(x) == HALF and abs(z) == HALF
        put(x, 5, z, 'oxidized_chiseled_copper' if corner else 'oxidized_cut_copper_slab[type=bottom,waterlogged=false]')
    for x, z in ring(2):
        put(x, 5, z, 'oxidized_copper_grate' if rib(x, z) else 'glass')
        put(x, 6, z, 'oxidized_copper_grate' if rib(x, z) else 'glass')
    for x, z in ring(1):
        put(x, 6, z, 'glass')
        put(x, 7, z, 'oxidized_copper_grate' if abs(x) == abs(z) else 'glass')
    put(0, 7, 0, 'ochre_froglight')
    put(0, 8, 0, 'lightning_rod[facing=up,powered=false,waterlogged=false]')

    # grow light under the dome: lanterns over the corner troughs, spore blossoms over the inner ring and a
    # lantern hanging from the sun over the crown
    for x, z in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
        put(x, 4, z, 'lantern[hanging=true,waterlogged=false]')
    for x, z in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        put(x, 5, z, 'spore_blossom')
    put(0, 6, 0, 'lantern[hanging=true,waterlogged=false]')
    # glow berries hang in each arch, beside its centre (they may grow down; the core counts a grown vine too)
    for t in (-1, 1):
        for edge in (-HALF, HALF):
            put(t, 4, edge, 'cave_vines[age=0,berries=true]')
            put(edge, 4, t, 'cave_vines[age=0,berries=true]')
    return V


def check_symmetry(V):
    """Mirror-symmetric about x = 0, block states mirrored too (a stair facing east answers one facing west)."""
    sys.path.insert(0, HERE)
    from voxkit import rot_state
    for (x, y, z), b in V.items():
        want = rot_state(b, (-1, 1, False))
        assert V.get((-x, y, z)) == want, ('not mirror-symmetric', (x, y, z), b, V.get((-x, y, z)))


def export(V):
    cx, cy, cz = CORE_AT
    blocks = [{'pos': [x - cx, y - cy, z - cz], 'block': b}
              for (x, y, z), b in sorted(V.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))]
    data = {'name': 'entrelumen:terra_garden', 'anchor': CORE, 'front': 'south',
            'note': 'Generated by art/structures/terra_garden.py. Positions relative to the core; the drawing '
                    'faces south (+z). Copper matches any oxidation stage and waxing; every position is required.',
            'blocks': blocks}
    return json.dumps(data, indent=1, ensure_ascii=False) + '\n'


def counts(V):
    out = {}
    for b in V.values():
        name = b.split('[')[0]
        out[name] = out.get(name, 0) + 1
    return dict(sorted(out.items(), key=lambda kv: -kv[1]))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--render', metavar='DIR')
    args = parser.parse_args()
    V = build()
    check_symmetry(V)
    text = export(V)
    if args.check:
        with open(OUT, encoding='utf-8') as f:
            assert f.read() == text, 'stale ' + OUT
        print(f'PASS: terra_garden.json matches ({len(V)} positions)')
    elif not args.render:
        with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
        print(len(V), 'positions:', counts(V))
    if args.render:
        sys.path.insert(0, HERE)
        import terra_garden_render
        terra_garden_render.render_all(V, args.render)


if __name__ == '__main__':
    main()
