"""Solsticio, draft 6: draft 5 dressed with the pack's decorative mods (art/structures/modblocks).

Same city as city5.py; this pass swaps and adds modded blocks:
  - streets paved with Macaw's Paths (basket weave on main streets, honeycomb on the rings, running
    bond on side streets, clover and calcite checkers in the plazas), mossy footpaths;
  - leaded glass and Chipped stained-glass panes in every window, rosette clocks on the shops;
  - Macaw's street lamps, striped awnings, Handcrafted counters and benches, Supplementaries
    planters, wind vanes, flags, a globe in the map shop, pedestals in the museum, notice boards
    in the plazas, flowering azalea hedges along the footpaths.

    python art/solsticio/city6.py [--export path/to/city.nbt]
"""
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import city3  # noqa: E402
import city5  # noqa: E402
from modblocks import palette as mp  # noqa: E402

B = city5.B
AIR = city5.AIR
V = city5.V


def name(state):
    head = state.split('[')[0]
    return head.split(':')[1] if ':' in head else head


def props(state):
    return mp.parse(state)[1]


VITRAL = {'yellow': 'chipped:circular_yellow_stained_glass_pane',
          'light_blue': 'chipped:ornate_light_blue_stained_glass_pane',
          'cyan': 'chipped:large_diamond_cyan_stained_glass_pane'}


def pane(block_id, ew):
    if block_id in VITRAL.values() or block_id.startswith('chipped:'):
        return mp.state(block_id, east=str(ew).lower(), west=str(ew).lower(),
                        north=str(not ew).lower(), south=str(not ew).lower())
    return 'minecraft:%s[east=%s,north=%s,south=%s,waterlogged=false,west=%s]' % (
        block_id, str(ew).lower(), str(not ew).lower(), str(not ew).lower(), str(ew).lower())


_building = city5.building


def building(kind, st, floors, hw=4, D=9):
    L, marks = _building(kind, st, floors, hw, D)
    back = D - 1
    shop = kind.startswith('shop:')
    top = 5 * floors
    seed = sum(map(ord, st['wall'] + st['roof'] + st.get('variant', ''))) + floors
    for (u, y, w), b in list(L.items()):
        n = name(b)
        on_front = w in (0, back) and abs(u) < hw
        on_side = abs(u) == hw and 0 < w < back
        if n.endswith('_stained_glass') and (on_front or on_side):
            color = n[:-len('_stained_glass')]
            L[(u, y, w)] = pane(VITRAL.get(color, color + '_stained_glass_pane'), on_front)
        elif n == 'glass_pane':
            L[(u, y, w)] = pane('chipped:clear_leaded_glass_pane', props(b).get('east') == 'true')
        elif n.endswith('_carpet') and w == -1:
            L[(u, y, w)] = AIR
            awning = 'mcwroofs:yellow_striped_awning' if seed % 2 else 'mcwroofs:cyan_striped_awning'
            L[(u, y - 1, w)] = mp.state(awning, facing='north')
        elif n == 'stripped_birch_wood' and shop:
            L[(u, y, w)] = mp.state('handcrafted:birch_counter', facing='north', counter='calcite')
        elif n == 'lightning_rod':
            L[(u, y, w)] = mp.state('supplementaries:wind_vane') if y > top + 4 or seed % 3 else \
                mp.state('supplementaries:flag_light_blue', facing='north')
        elif n == 'lantern' and 'hanging=true' in b and w == -1 and y == 3:
            L[(u, y, w)] = mp.state('mcwlights:wall_lantern', facing='north')
    if shop:
        kind_ = kind.split(':')[1]
        L[(0, top + 2, 0)] = mp.state('supplementaries:clock_block', facing='north')
        if kind_ == 'maps':
            L[(2, 2, D // 2)] = mp.state('supplementaries:globe', facing='north')
        if kind_ == 'museum':
            for u in (-2, 2):
                L[(u, 1, 1)] = mp.state('supplementaries:pedestal')
                L[(u, 2, 1)] = B('glass')
        if kind_ in ('rarities', 'curiosities', 'apothecary'):
            L[(-2, 2, D // 2)] = mp.state('supplementaries:hourglass') if mp.block('supplementaries:hourglass') else L[(-2, 2, D // 2)]
        L[(0, 4, 3)] = mp.state('mcwlights:copper_chandelier')
    return L, marks


city5.building = building


def dress_streets():
    kinds = {}
    for (pts, sm, width, kind) in city5.PATHS:
        for (x, z) in pts:
            kinds.setdefault((round(x), round(z)), kind)
    paving = {'main': 'mcwpaths:diorite_basket_weave_paving', 'ring': 'mcwpaths:diorite_honeycomb_paving',
              'side': 'mcwpaths:diorite_running_bond_path'}
    for (x, z), (h, prio, kind) in city5.ROAD.items():
        cur = V.get((x, h, z), '')
        if name(cur) not in ('calcite', 'polished_diorite', 'polished_tuff', 'waxed_cut_copper'):
            continue
        if (x, z) in city5.RIVER:
            V[(x, h, z)] = mp.state('mcwpaths:diorite_flagstone')
            continue
        edge = any((x + dx, z + dz) not in city5.ROAD for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if kind == 'plaza':
            V[(x, h, z)] = mp.state('mcwpaths:diorite_clover_paving') if (x + z) % 2 else mp.state('chipped:checkered_calcite_tiles')
        elif edge:
            V[(x, h, z)] = mp.state('chipped:flat_calcite_tiles')
        else:
            V[(x, h, z)] = mp.state(paving.get(kind, 'mcwpaths:diorite_flagstone'))
    for (x, z) in city5.PATHCELLS:
        h = city5.HEIGHT[(x, z)]
        V[(x, h, z)] = mp.state('mcwpaths:mossy_stone_running_bond_path') if (x * 3 + z) % 5 else \
            mp.state('mcwpaths:stone_strewn_rocky_path')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n in city5.PATHCELLS or n in city5.ROAD or n not in city5.HEIGHT:
                continue
            hn = city5.HEIGHT[n]
            above = V.get((n[0], hn + 1, n[1]), AIR)
            if name(above) in ('moss_carpet', 'short_grass', 'azure_bluet') and city5._h(n[0], n[1], 61) < 0.5:
                V[(n[0], hn + 1, n[1])] = mp.state('mcwfences:flowering_azalea_hedge')


def dress_lamps():
    for (x, y, z), b in list(V.items()):
        if name(b) not in ('ochre_froglight', 'pearlescent_froglight'):
            continue
        if name(V.get((x, y - 1, z), '')) != 'waxed_copper_grate' or name(V.get((x, y - 2, z), '')) != 'tuff_brick_wall':
            continue
        base = y - 4
        for yy in range(base + 1, base + 7):
            V.pop((x, yy, z), None)
        for yy, part in ((base + 1, 'bottom'), (base + 2, 'middle'), (base + 3, 'top')):
            V[(x, yy, z)] = mp.state('mcwlights:classic_street_lamp', part=part)
    for (x, y, z), b in list(V.items()):
        if name(b) == 'flowering_azalea' and name(V.get((x, y - 1, z), '')) == 'moss_block' and (x, z) in city5.HEIGHT \
                and y - 2 == city5.HEIGHT.get((x, z), -999):
            V[(x, y - 1, z)] = mp.state('supplementaries:planter')


def dress_plazas():
    for (cx, h, cz) in city5.PLAZAS:
        for (dx, dz, facing) in ((0, 4, 'north'), (0, -4, 'south'), (4, 0, 'west'), (-4, 0, 'east')):
            for k in (-1, 0, 1):
                x, z = cx + dx + (k if dx == 0 else 0), cz + dz + (k if dz == 0 else 0)
                shape = 'middle' if k == 0 else ('left' if k < 0 else 'right')
                V[(x, h + 1, z)] = mp.state('handcrafted:birch_bench', facing=facing, shape=shape)
        V[(cx + 6, h + 1, cz)] = mp.state('supplementaries:notice_board', facing='east')


def build():
    city5.build()
    dress_streets()
    dress_lamps()
    dress_plazas()
    return V


if __name__ == '__main__':
    t0 = time.time()
    build()
    mods = sum(1 for b in V.values() if not b.startswith('minecraft:'))
    print(len(V), 'blocks,', mods, 'modded,', len(city5.LOTS), 'buildings,', len(city5.MARKERS), 'markers',
          round(time.time() - t0, 1), 's')
    from voxrender import render
    out = os.path.join(HERE, 'preview')
    t0 = time.time()
    render(V, os.path.join(out, 'solsticio6.png'), scale=2, extra=city5.EXTRA)
    best = None
    for x0 in range(-110, 80, 6):
        for z0 in range(-110, 80, 6):
            n = sum(1 for l in city5.LOTS if x0 <= l[0] < x0 + 36 and z0 <= l[1] < z0 + 36)
            if best is None or n > best[0]:
                best = (n, x0, z0)
    _, x0, z0 = best
    render(V, os.path.join(out, 'solsticio6-street.png'), scale=9,
           keep=lambda a, b, c: x0 <= a < x0 + 36 and z0 <= c < z0 + 36 and b > -60)
    cx, h, cz = city5.PLAZAS[1]
    render(V, os.path.join(out, 'solsticio6-plaza.png'), scale=10,
           keep=lambda a, b, c: abs(a - cx) <= 12 and abs(c - cz) <= 12 and b > h - 8)
    print('render', round(time.time() - t0, 1), 's')
    if '--export' in sys.argv:
        print('export', city3.export(sys.argv[sys.argv.index('--export') + 1]))
