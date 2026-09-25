"""Solsticio v2 buildings: townhouses, tower houses and loggias with furnished interiors.

Reference (AGENTS.md design hook): Dungeons and Taverns 4.4.4, data/nova_structures/structure/
tavern/tavern_house_cherry.nbt (17x18x20), rendered and inspected on 2026-09-25: timber frame with
light infill, stone plinth, upper floor jettied over the street, chimney stack, layered roof edges,
furnished rooms on every floor. Solsticio translates that into its own material language: calcite
and tuff for the walls, quartz pillars as the frame, waxed copper roofs, stained glass, sun motifs.

Local frame as city5.building: u across the street (-hw..hw), w depth (0 = front, local 'north'
faces the street), y from the ground floor (0). Every facade is mirror-symmetric across u = 0; the
ladder runs up the axis at the back. Returns (blocks, markers).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
from modblocks import palette as mp  # noqa: E402

B = lambda n: n if ':' in n.split('[')[0] else 'minecraft:' + n
AIR = 'minecraft:air'
STOREY = 5
FLIP = {'east': 'west', 'west': 'east'}


def mirror(state):
    """The same block seen in the mirror u -> -u: east/west swap, hinges and stair shapes swap."""
    if '[' not in state:
        return state
    name, props = state[:-1].split('[')
    out = []
    for p in props.split(','):
        k, v = p.split('=')
        if k == 'facing' and v in FLIP:
            v = FLIP[v]
        elif k in FLIP:
            k = FLIP[k]
        elif k == 'hinge':
            v = 'right' if v == 'left' else 'left'
        elif k == 'shape' and ('left' in v or 'right' in v):
            v = v.replace('left', 'X').replace('right', 'left').replace('X', 'right')
        out.append(k + '=' + v)
    return name + '[' + ','.join(sorted(out)) + ']'


class Plan(dict):
    def put(self, u, y, w, b):
        self[(u, y, w)] = B(b)

    def sym(self, u, y, w, b):
        b = B(b)
        self[(u, y, w)] = b
        self[(-u, y, w)] = mirror(b) if u else b


def st(block_id, **props):
    return mp.state(block_id, **props)


PALETTES = [
    dict(wall='calcite', frame='quartz_pillar[axis=y]', plinth='polished_tuff', band='waxed_cut_copper',
         roof='waxed_cut_copper', glass=('yellow', 'orange'), accent='yellow'),
    dict(wall='smooth_quartz', frame='chipped:ornate_calcite_pillar', plinth='tuff_bricks', band='waxed_exposed_cut_copper',
         roof='waxed_oxidized_cut_copper', glass=('light_blue', 'cyan'), accent='light_blue'),
    dict(wall='calcite', frame='stripped_cherry_log[axis=y]', plinth='polished_tuff', band='waxed_weathered_cut_copper',
         roof='waxed_weathered_cut_copper', glass=('pink', 'magenta'), accent='pink'),
    dict(wall='chipped:calcite_bricks', frame='quartz_pillar[axis=y]', plinth='tuff_bricks', band='waxed_cut_copper',
         roof='waxed_exposed_cut_copper', glass=('white', 'yellow'), accent='white'),
    dict(wall='calcite', frame='stripped_birch_log[axis=y]', plinth='polished_tuff', band='waxed_oxidized_cut_copper',
         roof='waxed_oxidized_cut_copper', glass=('cyan', 'light_blue'), accent='cyan'),
]
SHOP_GOODS = {
    'bookstore': ('bookshelf', 'chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=false]'),
    'rarities': ('amethyst_block', 'supplementaries:jar'), 'minerals': ('raw_gold_block', 'raw_copper_block'),
    'creatures': ('moss_block', 'supplementaries:jar'), 'bakery': ('hay_block[axis=y]', 'smoker[facing=north,lit=true]'),
    'parts': ('waxed_copper_grate', 'crafter[crafting=false,orientation=north_up,triggered=false]'), 'smithy': ('smithing_table', 'anvil[facing=east]'),
    'textiles': ('pink_wool', 'loom[facing=north]'), 'maps': ('cartography_table', 'supplementaries:globe'),
    'seeds': ('composter[level=4]', 'moss_block'), 'nursery': ('flowering_azalea', 'supplementaries:planter'),
    'apothecary': ('brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]', 'supplementaries:jar'),
    'apiary': ('honeycomb_block', 'beehive[facing=north,honey_level=5]'), 'records': ('jukebox[has_record=false]', 'note_block[instrument=harp,note=0,powered=false]'),
    'museum': ('supplementaries:pedestal', 'supplementaries:pedestal'), 'curiosities': ('supplementaries:hourglass', 'supplementaries:urn'),
}


# ---------------- shell ----------------
def shell(P, hw, D, floors, pal, jetty):
    """Walls, frame posts, floor slabs, bands, the jettied upper storeys and the ladder."""
    back = D - 1
    top = STOREY * floors
    for f in range(floors):
        y0 = f * STOREY
        front = -1 if (jetty and f > 0) else 0
        for u in range(-hw, hw + 1):
            for w in range(front, D):
                edge = abs(u) == hw or w in (front, back)
                P.put(u, y0, w, pal['plinth'] if f == 0 else 'birch_planks' if not edge else pal['band'])
                for y in range(y0 + 1, y0 + STOREY):
                    if edge:
                        corner = abs(u) == hw and w in (front, back)
                        P.put(u, y, w, pal['frame'] if corner else pal['plinth'] if (f == 0 and y == 1) else pal['wall'])
                    else:
                        P.put(u, y, w, AIR)
        if jetty and f > 0:                         # corbels under the overhang
            for u in range(-hw, hw + 1, 2 if hw % 2 == 0 else 1):
                if abs(u) == hw or u % 2 == 0:
                    P.put(u, y0 - 1, -1, 'polished_tuff_stairs[facing=south,half=top,shape=straight,waterlogged=false]')
    for u in range(-hw, hw + 1):                    # the top floor's ceiling
        for w in range(-1 if jetty and floors > 1 else 0, D):
            P.put(u, top, w, pal['band'] if (abs(u) == hw or w in (0, back)) else 'birch_planks')
    for y in range(1, top):                         # ladder on the axis against the back wall
        P.put(0, y, back - 1, 'ladder[facing=north,waterlogged=false]')
        if y % STOREY == 0:
            P.put(0, y, back - 1, 'ladder[facing=north,waterlogged=false]')
    return top


def windows(P, hw, D, floors, pal, jetty, shop):
    back = D - 1
    g1, g2 = pal['glass'][0] + '_stained_glass', pal['glass'][1] + '_stained_glass'
    for f in range(floors):
        y0 = f * STOREY
        front = -1 if (jetty and f > 0) else 0
        cols = [c for c in range(1, hw) if c % 2 == 1]
        for c in cols:
            for sgn in (-1, 1):
                u = sgn * c
                if f == 0 and shop:
                    continue
                if f == 0 and abs(u) <= 1:
                    continue
                P.put(u, y0 + 2, front, st('chipped:clear_leaded_glass_pane', east='true', west='true', north='false', south='false'))
                P.put(u, y0 + 3, front, st('chipped:clear_leaded_glass_pane', east='true', west='true', north='false', south='false'))
                P.put(u, y0 + 4, front, g1 if c % 4 == 0 else g2)
                if f > 0:
                    P.put(u, y0 + 1, front - 1, st('supplementaries:flower_box', facing='north', face='wall'))
                P.put(u, y0 + 2, back, 'glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]')
                P.put(u, y0 + 3, back, 'glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]')
        for w in range(2, D - 2, 3):                # side windows (seen where a row ends)
            for u in (-hw, hw):
                P.put(u, y0 + 2, w, 'glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]')
                P.put(u, y0 + 3, w, 'glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]')


def door(P, pal, shop):
    P.put(0, 1, 0, 'waxed_copper_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
    P.put(0, 2, 0, 'waxed_copper_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    P.put(0, 3, 0, pal['glass'][0] + '_stained_glass')
    P.sym(1, 3, 0, 'chiseled_quartz_block')
    P.put(0, 4, 0, 'ochre_froglight')
    P.sym(1, 1, -1, 'potted_flowering_azalea_bush')
    P.put(0, 0, -1, 'polished_tuff_slab[type=top,waterlogged=false]')
    P.sym(1, 4, -1, st('mcwlights:wall_lantern', facing='north'))


def balcony(P, hw, floor, pal):
    y0 = floor * STOREY
    for u in range(-(hw - 1), hw):
        P.put(u, y0, -1, pal['band'].replace('cut_copper', 'cut_copper_slab') + '[type=top,waterlogged=false]'
              if 'cut_copper' in pal['band'] else 'smooth_quartz_slab[type=top,waterlogged=false]')
        P.put(u, y0 + 1, -2, st('mcwstairs:quartz_balcony', east=str(abs(u) < hw - 1 or u < 0).lower(),
                                 west=str(abs(u) < hw - 1 or u > 0).lower()) if False else
              'diorite_wall[east=low,north=none,south=none,up=%s,waterlogged=false,west=low]' % ('true' if abs(u) == hw - 1 else 'false'))
        P.put(u, y0, -2, 'smooth_quartz_slab[type=top,waterlogged=false]')
    P.put(0, y0 + 1, 0, 'waxed_copper_door[facing=south,half=lower,hinge=left,open=false,powered=false]')
    P.put(0, y0 + 2, 0, 'waxed_copper_door[facing=south,half=upper,hinge=left,open=false,powered=false]')
    P.sym(hw - 1, y0 + 1, -1, st('supplementaries:planter'))
    P.sym(hw - 1, y0 + 2, -1, 'flowering_azalea')


# ---------------- roofs ----------------
def roof_gable(P, hw, D, top, pal, jetty):
    """Steep copper gable along the depth, a sun rosette in the street gable, a chimney at the back."""
    roof = pal['roof']
    back = D - 1
    front = -1 if jetty else 0
    for r in range(hw + 1):
        y = top + 1 + r
        for w in range(front - 1, D + 1):
            if r < hw:
                P.put(hw - r, y, w, roof + '_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
                P.put(-(hw - r), y, w, roof + '_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
            else:
                P.put(0, y, w, roof)
        if r < hw:
            for u in range(-(hw - 1 - r), hw - r):
                for w in (front, back):
                    P.put(u, y, w, pal['wall'])
                for w in range(front + 1, back):
                    P.put(u, y, w, AIR)
    g1, g2 = pal['glass'][0] + '_stained_glass', pal['glass'][1] + '_stained_glass'
    for w in (front,):
        if hw >= 4:
            for u, blk in ((-2, g2), (-1, g1), (0, g1), (1, g1), (2, g2)):
                P.put(u, top + 1, w, blk)
            for u, blk in ((-1, g1), (0, 'ochre_froglight'), (1, g1)):
                P.put(u, top + 2, w, blk)
            P.put(0, top + 3, w, g2)
        else:
            for u, blk in ((-1, g2), (0, g1), (1, g2)):
                P.put(u, top + 1, w, blk)
            P.put(0, top + 2, w, 'ochre_froglight')
    for y in range(top + 1, top + hw + 4):          # chimney stack on the axis at the back
        for u in (-1, 0, 1) if hw >= 4 else (0,):
            P.put(u, y, back, 'tuff_bricks' if u else 'polished_tuff')
    P.put(0, top + hw + 4, back, 'campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]')
    P.put(0, top + hw + 2, front - 1, 'lightning_rod[facing=up,powered=false,waterlogged=false]')


def roof_terrace(P, hw, D, top, pal):
    """Flat roof garden with a pergola, planters and a railing."""
    back = D - 1
    for u in range(-hw, hw + 1):
        for w in range(0, D):
            edge = abs(u) == hw or w in (0, back)
            P.put(u, top + 1, w, 'diorite_wall[east=low,north=none,south=none,up=false,waterlogged=false,west=low]'
                  if edge and w in (0, back) and abs(u) < hw else
                  'diorite_wall[east=none,north=low,south=low,up=false,waterlogged=false,west=none]' if edge and abs(u) == hw and w not in (0, back) else
                  'diorite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]' if edge else AIR)
    for u in (-(hw - 1), hw - 1):
        for w in range(1, back):
            P.put(u, top + 1, w, st('supplementaries:planter') if w % 2 else 'moss_block')
            if w % 2 == 0:
                P.put(u, top + 2, w, 'flowering_azalea')
    for w in range(2, back - 1, 2):
        for u in (-(hw - 2), hw - 2):
            for y in range(top + 1, top + 4):
                P.put(u, y, w, 'stripped_birch_log[axis=y]')
        for u in range(-(hw - 2), hw - 1):
            P.put(u, top + 4, w, 'stripped_birch_log[axis=x]')
            P.put(u, top + 5, w, 'flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]' if (u + w) % 3 else 'azalea_leaves[distance=1,persistent=true,waterlogged=false]')
    P.sym(1, top + 1, back // 2, st('handcrafted:birch_bench', facing='east', shape='single') if False else st('handcrafted:birch_chair', facing='west'))
    P.put(0, top + 1, back // 2, st('handcrafted:birch_side_table'))


def roof_cone(P, hw, D, top, pal):
    """Pyramidal copper spire with a glass lantern and the sun on its tip (tower houses)."""
    roof = pal['roof']
    cz = (D - 1) / 2
    for r in range(hw + 2):
        y = top + 1 + r
        span = hw + 1 - r
        for u in range(-span, span + 1):
            for w in range(round(cz - span), round(cz + span) + 1):
                if max(abs(u), abs(w - cz)) >= span - 0.5:
                    P.put(u, y, w, roof)
    y = top + hw + 3
    for dy in range(3):
        P.put(0, y + dy, round(cz), 'yellow_stained_glass' if dy < 2 else 'ochre_froglight')
    P.put(0, y + 3, round(cz), 'lightning_rod[facing=up,powered=false,waterlogged=false]')


# ---------------- interiors ----------------
def lamp(P, y, w):
    P.put(0, y, w, st('mcwlights:copper_chandelier'))


def room_kitchen(P, hw, D, y0):
    back = D - 1
    for u in range(-(hw - 1), hw):
        if abs(u) >= 2:
            P.put(u, y0 + 1, back - 1, st('handcrafted:birch_counter', facing='north', counter='calcite'))
            if abs(u) == 2:
                P.put(u, y0 + 2, back - 1, st('handcrafted:white_crockery_combo', facing='north'))
    P.sym(hw - 1, y0 + 1, back - 1, 'smoker[facing=north,lit=true]')
    P.sym(hw - 1, y0 + 1, back - 2, 'barrel[facing=up,open=false]')
    mid = D // 2
    P.put(0, y0 + 1, mid - 1, st('handcrafted:birch_table', shape='north_center' if False else 'single'))
    P.put(0, y0 + 1, mid, st('handcrafted:birch_table'))
    P.sym(1, y0 + 1, mid - 1, st('handcrafted:birch_chair', facing='west'))
    P.sym(1, y0 + 1, mid, st('handcrafted:birch_chair', facing='west'))
    lamp(P, y0 + 4, mid)


def room_living(P, hw, D, y0, pal):
    back = D - 1
    mid = D // 2
    for u in range(-(hw - 1), hw):
        P.put(u, y0 + 1, 1, pal['accent'] + '_carpet') if abs(u) <= hw - 2 else None
    P.sym(hw - 1, y0 + 1, 2, 'bookshelf')
    P.sym(hw - 1, y0 + 2, 2, 'bookshelf')
    P.sym(hw - 1, y0 + 1, 3, 'bookshelf')
    P.sym(hw - 1, y0 + 2, 3, st('supplementaries:item_shelf', facing='east') if False else 'bookshelf')
    for u in (-1, 0, 1):
        P.put(u, y0 + 1, mid + 1, st('handcrafted:birch_couch', facing='north',
                                      shape='left' if u == -1 else 'middle' if u == 0 else 'right', color=pal['accent']))
    P.put(0, y0 + 1, mid - 1, st('handcrafted:birch_side_table'))
    P.put(0, y0 + 2, mid - 1, 'potted_blue_orchid')
    P.sym(hw - 1, y0 + 1, back - 1, st('refurbished_furniture:white_lamp'))
    lamp(P, y0 + 4, mid)


def room_bedroom(P, hw, D, y0, pal):
    back = D - 1
    mid = D // 2
    colour = pal['accent']
    for sgn in (-1, 1):
        u = sgn * (hw - 2)
        P.put(u, y0 + 1, 2, colour + '_bed[facing=north,occupied=false,part=head]')
        P.put(u, y0 + 1, 3, colour + '_bed[facing=north,occupied=false,part=foot]')
        P.put(sgn * (hw - 1), y0 + 1, 1, st('handcrafted:birch_side_table'))
        P.put(sgn * (hw - 1), y0 + 2, 1, 'lantern[hanging=false,waterlogged=false]')
        P.put(sgn * (hw - 1), y0 + 1, back - 1, st('handcrafted:birch_drawer', facing='north'))
        P.put(sgn * (hw - 1), y0 + 1, mid, st('handcrafted:birch_cupboard', facing='east' if sgn < 0 else 'west'))
        P.put(sgn * (hw - 1), y0 + 2, mid, st('handcrafted:birch_cupboard', facing='east' if sgn < 0 else 'west', type='2'))
    for u in range(-1, 2):
        P.put(u, y0 + 1, mid, colour + '_carpet')
    lamp(P, y0 + 4, mid)


def room_study(P, hw, D, y0, pal):
    back = D - 1
    mid = D // 2
    for u in range(-(hw - 1), hw):
        if abs(u) >= 2:
            P.put(u, y0 + 1, back - 1, 'bookshelf')
            P.put(u, y0 + 2, back - 1, 'bookshelf')
            P.put(u, y0 + 3, back - 1, 'chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]')
    P.put(0, y0 + 1, mid - 1, 'lectern[facing=south,has_book=false,powered=false]')
    P.sym(1, y0 + 1, mid, st('handcrafted:birch_chair', facing='west'))
    P.put(0, y0 + 1, mid, st('handcrafted:birch_table'))
    P.put(0, y0 + 2, mid, st('supplementaries:globe') if False else 'potted_lily_of_the_valley')
    P.sym(hw - 1, y0 + 1, 1, st('supplementaries:hourglass') if False else 'potted_azure_bluet')
    lamp(P, y0 + 4, mid)


def room_shop(P, hw, D, kind, pal):
    """Display windows, a counter across the room, goods on shelves, a clock over the door."""
    back = D - 1
    goods = SHOP_GOODS.get(kind, ('barrel[facing=up,open=false]', 'barrel[facing=up,open=false]'))
    for u in range(-(hw - 1), hw):
        if u == 0:
            continue
        for y in (1, 2, 3):
            P.put(u, y, 0, 'glass')
        P.put(u, 4, 0, pal['glass'][abs(u) % 2] + '_stained_glass')
    for sgn in (-1, 1):                              # display plinths behind the windows
        for c in range(2, hw):
            P.put(sgn * c, 1, 1, st('supplementaries:pedestal') if kind == 'museum' else goods[0] if c % 2 == 0 else goods[1])
    for u in range(-(hw - 1), hw):                   # the counter
        if abs(u) >= 1:
            P.put(u, 1, D // 2, st('handcrafted:birch_counter', facing='north', counter='quartz_block'))
    for u in range(-(hw - 1), hw):
        if abs(u) >= 2:
            for y in (1, 2, 3):
                P.put(u, y, back - 1, goods[(u + y) % 2] if kind in ('bookstore', 'minerals', 'textiles', 'apiary') else
                      st('handcrafted:birch_shelf', facing='north', shape='middle'))
    for u in range(-hw, hw + 1):                     # striped awning over the windows
        P.put(u, 4, -1, st('mcwroofs:yellow_striped_awning' if pal['accent'] in ('yellow', 'white', 'pink') else 'mcwroofs:cyan_striped_awning', facing='north'))
    P.put(0, 5, -1, st('supplementaries:clock_block', facing='north') if hw >= 4 else 'ochre_froglight')
    lamp(P, 4, D // 2 - 1)


def room_tavern(P, hw, D, pal):
    back = D - 1
    for u in range(-(hw - 1), hw):                   # the bar along the back
        if abs(u) >= 2:
            P.put(u, 1, back - 2, st('handcrafted:birch_counter', facing='north', counter='dark_oak_planks'))
            P.put(u, 1, back - 1, 'barrel[facing=north,open=false]')
            P.put(u, 2, back - 1, 'barrel[facing=north,open=false]')
    for sgn in (-1, 1):                              # two tables with chairs
        cu = sgn * (hw - 2)
        P.put(cu, 1, 3, st('handcrafted:birch_table'))
        P.put(cu, 2, 3, 'candle[candles=3,lit=true,waterlogged=false]')
        P.put(cu, 1, 2, st('handcrafted:birch_chair', facing='south'))
        P.put(cu, 1, 4, st('handcrafted:birch_chair', facing='north'))
    lamp(P, 4, 3)


# ---------------- typologies ----------------
def townhouse(kind, hw, D, floors, seed):
    P = Plan()
    pal = PALETTES[seed % len(PALETTES)]
    shop = kind.startswith('shop:')
    jetty = not shop and seed % 3 != 0
    top = shell(P, hw, D, floors, pal, jetty)
    windows(P, hw, D, floors, pal, jetty, shop)
    door(P, pal, shop)
    marks = []
    rooms = [room_kitchen, room_living, room_bedroom, room_study]
    for f in range(floors):
        y0 = f * STOREY
        if f == 0 and shop:
            room_shop(P, hw, D, kind.split(':')[1], pal)
            marks.append((kind, (0, 1, D - 3)))
        elif f == 0 and kind == 'inn':
            room_tavern(P, hw, D, pal)
            marks.append(('inn', (0, 1, D - 4)))
        else:
            fn = rooms[(f + seed) % len(rooms)] if f else room_kitchen
            fn(P, hw, D, y0) if fn is room_kitchen else fn(P, hw, D, y0, pal)
    if floors >= 3 and not jetty:
        balcony(P, hw, 1, pal)
    if kind == 'resident':
        marks.append(('resident', (0, STOREY + 1, 2)))
    style = seed % 4
    if style == 3 and hw >= 4:
        roof_terrace(P, hw, D, top, pal)
    else:
        roof_gable(P, hw, D, top, pal, jetty)
    for key in list(P):                              # nothing sticks through the ladder shaft
        u, y, w = key
        if u == 0 and w == D - 2 and 0 < y < top and not P[key].startswith('minecraft:ladder'):
            P[key] = 'minecraft:ladder[facing=north,waterlogged=false]'
    return dict(P), marks


def tower(kind, D, floors, seed):
    """A 7x7 tower house with a spire, used to punctuate the rows."""
    P = Plan()
    pal = PALETTES[(seed + 2) % len(PALETTES)]
    hw = 3
    top = shell(P, hw, 7, floors, pal, False)
    windows(P, hw, 7, floors, pal, False, False)
    door(P, pal, False)
    for f in range(1, floors):
        lamp(P, f * STOREY + 4, 3)
    roof_cone(P, hw, 7, top, pal)
    return dict(P), ([('resident', (0, STOREY + 1, 2))] if kind == 'resident' else [])


def building(kind, seed, hw, D, floors):
    if hw == 3 and not kind.startswith('shop:') and kind != 'inn' and seed % 2 == 0:
        return tower(kind, D, floors + 1, seed)
    return townhouse(kind, hw, D, floors, seed)
