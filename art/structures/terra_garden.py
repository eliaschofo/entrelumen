"""Terra's hydroponic garden as a multiblock: a little engine with a flower pot at its heart.

Elias, 29 September 2026: «distintísimo de looks, quiero que se vea como un motorcito, 4x4x4, steampunk,
solarpunk, como un car engine pero en el medio hay una macetita y alguna luz verde que le dé vida».

Reference (AGENTS.md hook, inspected before drawing): vanilla's copper family (cut, chiseled, grate and
bulb, fresh and oxidized), the piston's wooden head, the verdant froglight and the flower pot; the pack's
Ark controller and the copper-and-glass Solsticio city for the material language; a car engine's
reading order (cylinder banks with cooling fins on both sides, headlamps and a grille in front, valve
covers on top, the exhaust behind).

4 x 4 x 4, mirror-symmetric about the plane between the two middle columns (u <-> -1 - u):
  - y 0, the sump: chiseled copper corners and cut copper;
  - y 1-2, the block: a cylinder bank down each side (cut copper, oxidized grate fins), headlamps (lit
    copper bulbs) under grilles on the front corners, and behind, the two outlets the harvest leaves by;
    in the middle, open to the front, the heart: a bed of four verdant froglights (the green light that
    gives it life) and on it the core, a small pot drawn by its model on the mirror plane, with the crop
    growing in it and a green glow round its rim;
  - y 3, the head: four pistons for the valve gear on the corners, low valve covers (slabs) over the
    banks, cut copper over the back and a glass skylight over the heart that runs out to the front, so the
    pot shows from above as well as through the open front.
The drawing faces south (+z). Positions are written relative to the core. Copper matches any stage,
waxed or not. The chamber's other seven cells and the front opening are not part of the layout.

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
OUTLET = 'entrelumen:terra_garden_outlet'
CORE_AT = (0, 2, 0)          # drawing coordinates: u in -2..1 across, y 0..3 up, z -2..1 toward the front
U = (-2, -1, 0, 1)
Z = (-2, -1, 0, 1)


def B(name):
    return name if ':' in name else 'minecraft:' + name


def mirror_u(u):
    return -1 - u


def build():
    V = {}

    def put(u, y, z, block):
        V[(u, y, z)] = B(block)

    side = lambda u: u in (-2, 1)          # the cylinder banks
    mid = lambda u: u in (-1, 0)           # the two middle columns
    for u in U:
        for z in Z:
            corner = side(u) and z in (-2, 1)
            # y 0: the sump
            put(u, 0, z, 'waxed_chiseled_copper' if corner else 'waxed_cut_copper')
            # y 1, under the heart: a bed of verdant froglights, the green light the pot stands on
            if mid(u) and z in (-1, 0):
                put(u, 1, z, 'verdant_froglight[axis=y]')
            # y 1-2: the block
            if side(u):
                if z == 1:                               # headlamp under a grille
                    put(u, 1, z, 'waxed_copper_bulb[lit=true,powered=false]')
                    put(u, 2, z, 'waxed_oxidized_copper_grate')
                else:                                    # the bank: casing and cooling fins
                    put(u, 1, z, 'waxed_cut_copper')
                    put(u, 2, z, 'waxed_oxidized_copper_grate')
            elif z == -2:                                # behind: the outlets under a grille
                put(u, 1, z, OUTLET)
                put(u, 2, z, 'waxed_oxidized_copper_grate')
            # y 3: the head
            if corner:
                put(u, 3, z, 'piston[extended=false,facing=up]')
            elif mid(u) and z >= -1:
                put(u, 3, z, 'glass')                  # the skylight over the heart runs to the front
            elif mid(u):
                put(u, 3, z, 'waxed_cut_copper')
            else:
                put(u, 3, z, 'waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]')
    put(*CORE_AT, CORE)
    return V


def check_symmetry(V):
    """Mirror-symmetric about the plane between the middle columns, block states mirrored too."""
    sys.path.insert(0, HERE)
    from voxkit import rot_state
    for (u, y, z), b in V.items():
        if b == CORE:
            continue    # the core's model draws its pot on the mirror plane
        want = rot_state(b, (-1, 1, False))
        assert V.get((mirror_u(u), y, z)) == want, ('not mirror-symmetric', (u, y, z), b, V.get((mirror_u(u), y, z)))
    # the core's mirror cell is part of the open chamber
    cu, cy, cz = CORE_AT
    assert (mirror_u(cu), cy, cz) not in V


def export(V):
    cu, cy, cz = CORE_AT
    blocks = [{'pos': [u - cu, y - cy, z - cz], 'block': b}
              for (u, y, z), b in sorted(V.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))]
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
