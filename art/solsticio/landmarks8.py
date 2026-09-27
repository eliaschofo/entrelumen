"""Solsticio v8 landmarks at full scale, with their interiors (phase 2).

  - palace(): the Palace of the Solstice on its acropolis: portico over the grand stair, a
    three-storey body with corner pavilions, the domed Hall of the Solstice (the portal under the
    oculus, the waystone beside it, Aurelia's throne on a dais), the council hall and the archive
    in the wings, the sun tower behind the dome, the secret garden on the roof;
  - market_hall(): the Great Market of Light: a glass nave on copper ribs, a landing level with the
    Plaza del Mercado and a grand stair down into the hall, sixteen stalls under striped awnings,
    one for each shopkeeper (the shop:* markers stand behind their counters);
  - temple(), workshop(), palm_house(), clock_tower(): the Temple of Dawn (Bodhi), Terra's Workshop,
    the Botanical Garden (Juan) and the Clock Tower, each with its interior;
  - falls(): the Last Falls: the east canal ends in an overflow basin on the rim, the water falls
    down the island's side into a terminal catch basin on a rock ledge; the barrier round the shore
    is reshaped to enclose the fall instead of cutting it.

bind(city8) shares the grid and helpers of the builder.
"""
import math

NAMES = ('G', 'N', 'B', 'stairs', 'wall', 'leaves', 'h', 'kind', 'FACING', 'N4', 'N8', 'MARKERS', 'TOP', 'BOTTOM',
         'EXTRA', 'ring_xz', 'dome', 'cells_of', 'max_rect', 'tree', 'lamp', 'LANTERN', 'APPROACH', 'mp_state',
         'sign', 'FALLS', 'NOT_BARRIER', 'WATER_OK', 'cu', 'patina', 'cu_slab', 'bulb', 'rose', 'lancet', 'AMBER',
         'GOLD', 'TEAL')
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
DIRV = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}


def bind(C):
    globals().update({k: getattr(C, k) for k in NAMES})


def hang(x, y, z):
    G.set(x, y, z, B('lantern[hanging=true,waterlogged=false]'))


def chandelier(x, y, z):
    G.set(x, y, z, mp_state('mcwlights:copper_chandelier'))


def bench(x, y, z, facing, shape='single'):
    G.set(x, y, z, mp_state('handcrafted:birch_bench', facing=facing, shape=shape))


def chair(x, y, z, facing):
    G.set(x, y, z, mp_state('handcrafted:birch_chair', facing=facing))


def table(x, y, z):
    G.set(x, y, z, mp_state('handcrafted:birch_table'))


def counter(x, y, z, facing, top='quartz_block'):
    G.set(x, y, z, mp_state('handcrafted:birch_counter', facing=facing, counter=top))


# ---------------- the palace ----------------
def palace():
    """The Palace of the Solstice on its acropolis: a colonnaded front over a grand stair from the
    Plaza Mayor, a three-storey body with corner pavilions, a golden dome on a windowed drum over
    the Hall of the Solstice, and the sun tower behind it, the highest thing on the island."""
    L = N.PADS['palace']
    cx, cz = 0, -47
    X0, X1, Z0, Z1 = -20, 20, -60, -35
    H = 24
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            for y in range(L + 1, L + H + 1):
                k = y - L
                if not edge:
                    if k in (6, 12, 18) and math.hypot(x - cx, z - cz) > 11.5:
                        G.set(x, y, z, B('birch_planks'))
                    continue
                along = z if x in (X0, X1) else x
                if k == H:
                    G.set(x, y, z, B(cu('cut', patina(0, x, z, 81))))
                elif along % 4 == 0 or (x in (X0, X1) and z in (Z0, Z1)):
                    G.set(x, y, z, B('quartz_pillar[axis=y]'))
                elif k % 6 == 0:
                    G.set(x, y, z, B('smooth_quartz'))
                elif k % 6 in (2, 3, 4):                                # a sun over the sea in every bay
                    G.set(x, y, z, B(lancet(k % 6 - 2, 3, along % 4 == 2)))
                else:
                    G.set(x, y, z, B('calcite'))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if math.hypot(x - cx, z - cz) > 12.5 or x in (X0, X1) or z in (Z0, Z1):
                G.set(x, L + H, z, B('smooth_quartz') if not (x in (X0, X1) or z in (Z0, Z1)) else B('waxed_cut_copper'))
            if x in (X0, X1) or z in (Z0, Z1):
                post = (x % 4 == 0 and z in (Z0, Z1)) or (z % 4 == 0 and x in (X0, X1))
                ew = z in (Z0, Z1)
                G.set(x, L + H + 1, z, wall('diorite', 'low' if ew else 'none', 'none' if ew else 'low',
                                            'none' if ew else 'low', 'low' if ew else 'none', 'true' if post else 'false'))
    # the great door (three wide, two leaves of copper) and the portico of columns before it
    for x in range(-3, 4):
        for y in range(L + 1, L + 11):
            G.clear(x, y, Z1)
    for x in (-1, 1):
        G.set(x, L + 1, Z1, B('waxed_copper_door[facing=south,half=lower,hinge=%s,open=true,powered=false]' % ('left' if x < 0 else 'right')))
        G.set(x, L + 2, Z1, B('waxed_copper_door[facing=south,half=upper,hinge=%s,open=true,powered=false]' % ('left' if x < 0 else 'right')))
    for x in range(-13, 14, 3):
        for y in range(L + 1, L + 20):
            G.set(x, y, -33, B('quartz_pillar[axis=y]'))
        G.set(x, L + 19, -33, B('chiseled_quartz_block'))
    for x in range(-14, 15):
        for z in range(-34, -31):
            G.set(x, L + 20, z, B('smooth_quartz'))
            G.set(x, L + 21, z, B('waxed_cut_copper'))
    for r in range(0, 8):
        for x in range(-14 + 2 * r, 15 - 2 * r):
            G.set(x, L + 22 + r, -32, B('calcite'))
            G.set(x, L + 22 + r, -34, B('calcite'))
            G.set(x, L + 22 + r, -33, B('waxed_cut_copper' if abs(x) >= 13 - 2 * r else 'calcite'))
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            rr = math.hypot(dx, dy)
            if rr <= 3.3:
                ray = dx == 0 or dy == 0 or abs(dx) == abs(dy)
                G.set(dx, L + 25 + dy, -31, B('ochre_froglight[axis=y]' if rr < 1.5 else ('gold_block' if ray else AMBER) if rr > 2.5 else GOLD))
    for x in range(-12, 13, 6):                                  # lanterns hung in the portico
        hang(x, L + 19, -33 + 1)
    # corner pavilions with little domes, ladders up their floors
    for (px, pz) in ((-20, -35), (20, -35), (-20, -60), (20, -60)):
        for x in range(px - 4, px + 5):
            for z in range(pz - 4, pz + 5):
                edge = abs(x - px) == 4 or abs(z - pz) == 4
                for y in range(L + 1, L + 31):
                    if edge:
                        corner = abs(x - px) == 4 and abs(z - pz) == 4
                        G.set(x, y, z, B('quartz_pillar[axis=y]' if corner else (lancet((y - L) % 6 - 2, 3) if (y - L) % 6 in (2, 3, 4) and (x + z) % 2 else 'calcite')))
                    elif y == L + 30:
                        G.set(x, y, z, B('smooth_quartz'))
                    elif (y - L) % 6 == 0:
                        G.set(x, y, z, B('birch_planks'))
                    else:
                        G.clear(x, y, z)
        for y in range(L + 1, L + 30):                           # a ladder up every floor
            G.set(px, y, pz + (3 if pz < -47 else -3), B('ladder[facing=%s,waterlogged=false]' % ('north' if pz < -47 else 'south')))
        for k in range(0, 5):
            hang(px, L + 5 + 6 * k, pz)
        dome(px, pz, L + 31, 4, shell=cu('cut', 2 if (px < 0) == (pz < -47) else 3), ribs=4)       # verdigris pavilions
        G.set(px, L + 36, pz, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    # drum and dome over the Hall of the Solstice
    Rd = 12
    for (x, z, rr, dx, dz) in ring_xz(cx, cz, Rd - 0.9, Rd + 0.4):
        th = math.degrees(math.atan2(dz, dx)) % 360
        col = int(th // 10) % 3 == 0
        for y in range(L + H + 1, L + H + 9):
            k2 = y - L - H
            G.set(x, y, z, B('waxed_cut_copper' if y == L + H + 8 else 'quartz_pillar[axis=y]' if col else
                               AMBER if k2 == 4 else GOLD if k2 in (3, 5) else TEAL))
    for (x, z, rr, dx, dz) in ring_xz(cx, cz, 0, 12.4):          # the hall opens into the dome
        for y in range(L + 1, L + H + 1):
            if rr < 11.5 and not (y == L + H and rr > 10.5):
                G.clear(x, y, z)
        G.set(x, L, z, B('gold_block' if 3.4 < rr <= 4.4 else 'yellow_stained_glass' if rr <= 3.4 else
                          'chiseled_quartz_block' if int(rr) % 4 == 0 else 'smooth_quartz'))
    for (x, z, rr, dx, dz) in ring_xz(cx, cz, 10.5, 12.4):       # galleries round the hall on every floor
        if x in (X0, X1) or z in (Z0, Z1):
            continue
        for k in (6, 12, 18):
            G.set(x, L + k, z, B('birch_planks'))
            if 10.5 <= rr < 11.5:
                G.set(x, L + k + 1, z, wall('diorite'))
    dome(cx, cz, L + H + 9, Rd, glass_band=(3, 5), ribs=12, oculus=2.5)
    top = L + H + 9 + Rd
    for y in range(top - 1, top + 6):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) == 2:
                    G.set(cx + dx, y, cz + dz, B('gold_block'))
                elif (dx, dz) != (0, 0):
                    G.set(cx + dx, y, cz + dz, B('yellow_stained_glass'))
    G.set(cx, top + 6, cz, B('gold_block'))
    G.set(cx, top + 5, cz, B('ochre_froglight[axis=y]'))
    for k in range(8):                                          # the columns round the portal
        th = math.radians(22.5 + 45 * k)
        px, pz = cx + round(8 * math.cos(th)), cz + round(8 * math.sin(th))
        for y in range(L + 1, L + 12):
            G.set(px, y, pz, B('quartz_pillar[axis=y]'))
        G.set(px, L + 12, pz, B('ochre_froglight[axis=y]'))
    for (a, b) in ((5, 5), (5, -5), (-5, 5), (-5, -5)):          # chandeliers hung from the drum
        for y in range(L + 14, L + H + 6):
            G.set(cx + a, y, cz + b, B('chain[axis=y,waterlogged=false]'))
        chandelier(cx + a, L + 13, cz + b)
    # the carpet from the door to the portal and on to the throne
    for z in range(-56, Z1):
        for x in (-1, 0, 1):
            if G.get(x, L + 1, z) is None and math.hypot(x - cx, z - cz) > 4.5:
                G.set(x, L + 1, z, B('yellow_carpet'))
    # Aurelia's throne on a dais at the north of the hall, facing the portal
    for x in range(-4, 5):
        for z in range(-59, -55):
            G.set(x, L + 1, z, B('smooth_quartz'))
            if z == -59 or abs(x) >= 3:
                G.set(x, L + 2, z, B('smooth_quartz'))
    for x in range(-4, 5):
        G.set(x, L + 1, -55, stairs('quartz', 'north'))
    for x in (-1, 0, 1):
        for y in range(L + 2, L + 7):
            if x == 0 and y == L + 2:
                continue
            G.set(x, y, -59, B('gold_block' if abs(x) == 1 or y > L + 4 else 'yellow_stained_glass'))
    G.set(0, L + 2, -58, stairs('quartz', 'north'))                  # the seat, facing south
    G.set(-1, L + 2, -58, B('gold_block'))
    G.set(1, L + 2, -58, B('gold_block'))
    G.set(0, L + 7, -59, B('ochre_froglight[axis=y]'))
    for x in (-3, 3):
        G.set(x, L + 3, -57, B('candle[candles=4,lit=true,waterlogged=false]'))
    MARKERS.append(('mayor', (0, L + 2, -56)))
    # the council hall in the west wing: a long table, chairs, the map of the city, shelves
    for z in range(-56, -38):
        for x in (-19, -13):
            G.set(x, L + 1, z, B('bookshelf') if z % 3 else B('lectern[facing=%s,has_book=false,powered=false]' % ('east' if x < -16 else 'west')))
    for z in range(-53, -41):
        table(-16, L + 1, z)
        if z % 2 == 0:
            chair(-17, L + 1, z, 'east')
            chair(-15, L + 1, z, 'west')
    G.set(-16, L + 1, -54, B('cartography_table'))
    for z in (-51, -45):
        chandelier(-16, L + 5, z)
    # the archive in the east wing
    for z in range(-57, -38):
        for x in (13, 19):
            for y in (L + 1, L + 2, L + 3):
                G.set(x, y, z, B('bookshelf' if (z + y) % 5 else 'chiseled_bookshelf[facing=%s,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]' % ('east' if x == 13 else 'west')))
    for z in (-54, -48, -42):
        G.set(16, L + 1, z, B('lectern[facing=north,has_book=false,powered=false]'))
        chandelier(16, L + 5, z)
    # upper floors: lanterns so no room is dark
    for k in (6, 12, 18):
        for x in range(X0 + 3, X1 - 2, 6):
            for z in range(Z0 + 3, Z1 - 2, 6):
                if math.hypot(x - cx, z - cz) > 12.5 and G.get(x, L + k + 5, z) is None:
                    hang(x, L + k + 5, z)
    # the sun tower behind the dome
    tx, tz = 0, -61
    TH = 80
    for y in range(L + 1, L + TH + 1):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if max(abs(dx), abs(dz)) != 3:
                    if y in (L + 1,):
                        G.set(tx + dx, y - 1, tz + dz, B('smooth_quartz'))
                    continue
                corner = abs(dx) == 3 and abs(dz) == 3
                k = y - L
                if corner:
                    st = 'quartz_pillar[axis=y]'
                elif k % 12 == 0:
                    st = cu('cut', (k // 12) % 3)                        # bands at three patinas
                elif k > TH - 12 and abs(dx) <= 1 or k > TH - 12 and abs(dz) <= 1:
                    st = 'air' if k < TH - 2 else 'calcite'
                elif (dx == 0 or dz == 0) and k % 12 in (4, 5, 6, 7):
                    st = lancet(k % 12 - 4, 4)
                else:
                    st = 'calcite'
                G.set(tx + dx, y, tz + dz, B(st))
    for y in range(L + 1, L + TH):
        G.set(tx, y, tz + 2, B('ladder[facing=north,waterlogged=false]'))
    for k in range(1, TH // 12 + 1):
        hang(tx - 1, L + 12 * k - 1, tz)
    ty = L + TH
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if max(abs(dx), abs(dz)) <= 4:
                G.set(tx + dx, ty + 1, tz + dz, B('waxed_cut_copper'))
    for k in range(1, 7):                                       # a spire of gold
        r = 4 - (k * 4) // 7
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) == r:
                    G.set(tx + dx, ty + 1 + k, tz + dz, B('gold_block'))
    sy = ty + 16                                                # the frozen sun
    R = 5
    for dx in range(-R - 1, R + 2):
        for dy in range(-R - 1, R + 2):
            for dz in range(-R - 1, R + 2):
                rr = math.sqrt(dx * dx + dy * dy + dz * dz)
                if rr <= R:
                    G.set(tx + dx, sy + dy, tz + dz, B(('ochre_froglight[axis=y]', 'shroomlight', 'glowstone')[(abs(dx) + abs(dy) + abs(dz)) % 3]))
    for y in range(ty + 7, sy - R):
        G.set(tx, y, tz, B('gold_block'))
    for k in range(R + 1, R + 6):
        for (a, b) in N4:
            G.set(tx + a * k, sy, tz + b * k, B('end_rod[facing=%s]' % FACING[(a, b)]))
        G.set(tx, sy + k, tz, B('end_rod[facing=up]'))
    for k in range(R + 1, R + 4):
        for (a, b) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            G.set(tx + a * k, sy + (k if a > 0 else -k) // 2, tz + b * k, B('gold_block'))
    # the solar array on the roof: rows of daylight detectors round the drum, walks between them
    for x in range(X0 + 2, X1 - 1):
        for z in range(Z0 + 2, Z1 - 1):
            if x % 3 == 0 or math.hypot(x - cx, z - cz) < 14.5:
                continue
            if any(abs(x - px) <= 5 and abs(z - pz) <= 5 for (px, pz) in ((-20, -35), (20, -35), (-20, -60), (20, -60))):
                continue
            if 6 <= x <= 16 and z <= -53 or abs(x) <= 4 and z <= -56:     # the secret garden, the sun tower
                continue
            if G.get(x, L + H + 1, z) is None and G.filled(x, L + H, z):
                G.set(x, L + H + 1, z, B('daylight_detector[inverted=false,power=15]'))
    # markers: the portal under the oculus, the waystone beside it, the secret garden on the roof
    MARKERS.append(('town_hall_portal', (cx, L + 1, cz)))
    MARKERS.append(('town_hall_waystone', (cx + 6, L + 1, cz + 7)))
    gz = -58
    for x in range(8, 15):
        for z in range(-59, -55):
            G.set(x, L + H, z, B('moss_block'))
            if (x + z) % 3 == 0:
                G.set(x, L + H + 1, z, B(['flowering_azalea', 'azure_bluet', 'allium', 'lily_of_the_valley'][(x * 7 + z) % 4]))
    bench(10, L + H + 1, -55, 'north')
    G.clear(12, L + H + 1, gz)
    G.clear(12, L + H + 2, gz)
    MARKERS.append(('easter:secret_garden', (12, L + H + 1, gz)))
    sign(0, L + 12, Z1 + 1, 'south', ['', ('place', 'palace'), '', ''], wall=True, glow=True, wood='dark_oak')
    return top


# ---------------- the Great Market of Light ----------------
SHOP_TYPES = ['bookstore', 'rarities', 'parts', 'seeds', 'smithy', 'apothecary', 'maps', 'minerals',
              'records', 'textiles', 'nursery', 'creatures', 'museum', 'bakery', 'apiary', 'curiosities']
SHOP_COLOUR = {'bookstore': 'red', 'rarities': 'purple', 'parts': 'orange', 'seeds': 'lime', 'smithy': 'gray',
               'apothecary': 'light_blue', 'maps': 'cyan', 'minerals': 'black', 'records': 'magenta', 'textiles': 'pink',
               'nursery': 'green', 'creatures': 'green', 'museum': 'white', 'bakery': 'yellow', 'apiary': 'yellow',
               'curiosities': 'brown'}
SHOP_GOODS = {
    'bookstore': ('bookshelf', 'chiseled_bookshelf[facing=%s,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=false]'),
    'rarities': ('amethyst_block', 'amethyst_cluster[facing=up,waterlogged=false]'),
    'parts': ('waxed_copper_grate', 'crafter[crafting=false,orientation=north_up,triggered=false]'),
    'seeds': ('composter[level=4]', 'moss_block'), 'smithy': ('smithing_table', 'anvil[facing=%s]'),
    'apothecary': ('brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]', 'cauldron'),
    'maps': ('cartography_table', 'lectern[facing=%s,has_book=false,powered=false]'),
    'minerals': ('raw_gold_block', 'raw_copper_block'), 'records': ('jukebox[has_record=false]', 'note_block[instrument=harp,note=0,powered=false]'),
    'textiles': ('pink_wool', 'loom[facing=%s]'), 'nursery': ('flowering_azalea', 'azalea'),
    'creatures': ('moss_block', 'barrel[facing=up,open=false]'), 'museum': ('chiseled_quartz_block', 'decorated_pot[cracked=false,facing=%s,waterlogged=false]'),
    'bakery': ('hay_block[axis=y]', 'smoker[facing=%s,lit=true]'), 'apiary': ('honeycomb_block', 'beehive[facing=%s,honey_level=5]'),
    'curiosities': ('chiseled_tuff_bricks', 'barrel[facing=up,open=false]'),
}


def market_hall():
    """The Great Market of Light: a nave under a glass barrel vault on copper ribs, clerestory of
    glass over a calcite arcade, a sunburst in each end, a glass dome at the crossing. The west end
    is a landing level with the Plaza del Mercado; a grand stair goes down to the market floor,
    where sixteen stalls line the walls, one for each shopkeeper of the city."""
    cells = cells_of('market')
    x0, z0, x1, z1 = max_rect(cells)
    L = N.PADS['market']
    plaza = N.PLAZAS[N.PLAZA_OF[N.landmark('Plaza del Mercado')]]
    PL = plaza[3]                                               # the plaza's own floor
    drop = PL - L
    land = 3                                                    # landing depth inside the west wall
    for c in cells:
        G.set(c[0], TOP[c], c[1], B('polished_diorite' if (c[0] + c[1]) % 2 else 'calcite'))
    # the portico between the plaza and the nave: market cells west of the nave at the plaza's level
    for c in cells:
        if c[0] < x0:
            for y in range(TOP[c] + 1, PL):
                G.set(c[0], y, c[1], B('calcite'))
            G.set(c[0], PL, c[1], B('smooth_quartz' if (c[0] + c[1]) % 3 else 'gold_block'))
            TOP[c] = PL
            N.LEVEL[c] = PL
    W = 11
    zc = (z0 + z1) / 2
    R = (z1 - z0) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            side = z in (z0, z1)
            end = x in (x0, x1)
            if side or end:
                along = x if side else z
                for y in range(L + 1, L + W + drop + 1 if end and x == x0 else L + W + 1):
                    k = y - L
                    pier = along % 4 == 0 or (side and end)
                    if k >= W and not (end and x == x0 and k < W + drop):
                        st = cu('cut', patina(0, x, z, 83)) if k == W else 'glass'
                    elif pier:
                        st = cu('block', 1 + (along // 4) % 2) if k > 4 else 'quartz_pillar[axis=y]'
                    elif k <= 4:
                        st = 'calcite' if k == 4 or along % 8 in (1, 7) else 'glass'
                    elif k == W - 1:                                     # a crown of sun over every bay
                        st = AMBER if along % 4 == 2 else GOLD
                    elif k == W - 2:
                        st = GOLD if along % 4 == 2 else TEAL
                    else:
                        st = 'glass'
                    G.set(x, y, z, B(st))
            dz = z - zc
            yv = L + W + int(math.sqrt(max(0.0, R * R - dz * dz)) * 0.75)
            rib = x % 4 == 0
            G.set(x, yv, z, B(cu('cut', patina(0, x, z, 84)) if rib or end else GOLD if abs(dz) < 0.6 else 'glass'))
            if end:                                                     # the sunburst in each end
                for y in range(L + W + 1, yv):
                    ray = int((math.degrees(math.atan2(y - L - W, dz)) + 360) // 15) % 2
                    G.set(x, y, z, B(AMBER if math.hypot(y - L - W, dz) < 3 else GOLD if ray else TEAL))
    mx = (x0 + x1) // 2
    top = L + W + int(R * 0.75)
    dome(mx, round(zc), top - 2, 7, shell='glass', rib=cu('cut', 1), ribs=8)
    G.set(mx, top + 6, round(zc), B('gold_block'))
    G.set(mx, top + 7, round(zc), B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    # the landing at the plaza's level and the grand stair down to the floor, the full nave wide
    for x in range(x0 + 1, x0 + 1 + land + drop):
        for z in range(z0 + 1, z1):
            k = x - (x0 + land)                                    # 1.. drop on the stair
            lev = PL if k <= 0 else PL - k
            for y in range(L + 1, lev):
                G.set(x, y, z, B('calcite'))
            G.set(x, lev, z, B('smooth_quartz' if k <= 0 else 'polished_diorite'))
            if k > 0:
                G.set(x, lev + 1, z, stairs('quartz', 'west'))
            elif x == x0 + land and abs(z - zc) > 4:
                G.set(x, lev + 1, z, wall('diorite', 'none', 'low', 'low', 'none', 'false' if (z - z0) % 4 else 'true'))
    sx0 = x0 + 1 + land + drop                                     # first floor cell after the stair
    # the doors: the west end opens onto the portico at the landing's level
    for z in range(round(zc) - 2, round(zc) + 3):
        for y in range(PL + 1, PL + 6):
            G.clear(x0, y, z)
    for x in range(x0 - 3, x0):
        for z in range(round(zc) - 2, round(zc) + 3):
            for y in range(PL + 1, PL + 5):
                if G.get(x, y, z) and 'glass' not in G.get(x, y, z):
                    G.clear(x, y, z)
    # side doors on the floor level where a street meets the hall
    for x in range(sx0 + 2, x1 - 1, 8):
        for z in (z0, z1):
            out = (x, z - 1) if z == z0 else (x, z + 1)
            if N.LEVEL.get(out) is not None and abs(N.LEVEL[out] - L) <= 1:
                for y in range(L + 1, L + 4):
                    G.clear(x, y, z)
    # sixteen stalls: along the north and south walls, then across the east end
    stalls = []
    per_side = (x1 - 1 - sx0 + 1) // 3
    for i in range(per_side):
        xa = sx0 + 3 * i
        if xa + 2 > x1 - 1:
            break
        stalls.append(('n', xa))
        stalls.append(('s', xa))
    zs = list(range(z0 + 4, z1 - 3, 3))
    for za in zs:
        if za + 2 <= z1 - 4:
            stalls.append(('e', za))
    stalls = stalls[:len(SHOP_TYPES)]
    for (side, a), shop in zip(stalls, SHOP_TYPES):
        stall(side, a, shop, L, x0, z0, x1, z1)
    if len(stalls) < len(SHOP_TYPES):
        raise RuntimeError('only %d stalls fit in the nave' % len(stalls))
    # light over the aisle and the trading hall's centre
    for x in range(sx0, x1 - 1, 5):
        for y in range(L + W - 4, L + W):
            G.set(x, y, round(zc), B('chain[axis=y,waterlogged=false]'))
        chandelier(x, L + W - 5, round(zc))
    MARKERS.append(('trading_hall', (mx, L + 1, round(zc))))
    sign(x0 - 1, PL + 7, round(zc), 'west', ['', ('place', 'market'), '', ''], wall=True, glow=True, wood='spruce')
    return (x0, z0, x1, z1)


def stall(side, a, shop, L, x0, z0, x1, z1):
    """One stall three wide: goods against the wall, the keeper's floor behind the counter, the
    counter facing the aisle, an awning in the shop's colour with its keeper's name."""
    col = SHOP_COLOUR[shop]
    g1, g2 = SHOP_GOODS[shop]
    if side in ('n', 's'):
        wallz = z0 + 1 if side == 'n' else z1 - 1
        s = 1 if side == 'n' else -1                               # from the wall into the hall
        face = 'south' if side == 'n' else 'north'                 # the counter faces the aisle
        cells = [(a + u, wallz + s * w) for w in range(3) for u in range(3)]
        keeper = (a + 1, wallz + s)
        rowpos = lambda u, w: (a + u, wallz + s * w)
    else:
        wallx = x1 - 1
        face = 'west'
        keeper = (wallx - 1, a + 1)
        rowpos = lambda u, w: (wallx - w, a + u)
        cells = [rowpos(u, w) for w in range(3) for u in range(3)]
    for u in range(3):
        gx, gz = rowpos(u, 0)
        G.set(gx, L + 1, gz, B(g1 if u != 1 else (g2 % OPP[face] if '%s' in g2 else g2)))
        if u != 1:
            G.set(gx, L + 2, gz, B('barrel[facing=up,open=false]') if shop not in ('bookstore',) else B(g1))
        cx_, cz_ = rowpos(u, 2)
        counter(cx_, L + 1, cz_, face, 'quartz_block' if u != 1 else 'spruce_planks')
    for (x, z) in cells:                                           # the awning over the stall (not over a neighbour's sign)
        G.setdefault(x, L + 4, z, B('%s_wool' % col))
    fx, fz = rowpos(1, 2)
    dx, dz = DIRV[face]
    G.setdefault(fx + dx, L + 4, fz + dz, B('%s_wool' % col))
    sign(fx + dx * 2, L + 4, fz + dz * 2, face, ['', ('shop', shop), '', ''], wall=True, glow=False, wood='spruce')
    hang(keeper[0], L + 3, keeper[1])
    MARKERS.append(('shop:' + shop, (keeper[0], L + 1, keeper[1])))


# ---------------- the Temple of Dawn ----------------
def temple():
    """The Temple of Dawn on its knoll: a stylobate with steps all round, a gabled nave of calcite
    under a cherry roof facing the city, a portico and a rose window over the door, buttresses,
    pews either side of a pink aisle, an altar with the sun under the apse, a bell tower with a
    spire of pink terracotta at the apse end."""
    tx, tz = N.landmark('Templo del Alba')
    L = N.PADS['temple']
    X0, X1, Z0, Z1 = tx - 7, tx + 7, tz - 9, tz + 9
    # the stylobate: two steps round the nave
    for x in range(X0 - 2, X1 + 3):
        for z in range(Z0 - 5, Z1 + 3):
            ring = max(0, X0 - x, x - X1, Z0 - 3 - z, z - Z1)
            if ring in (1, 2) and (x, z) in TOP:
                lev = L - (ring - 1)
                for y in range(TOP[(x, z)] + 1, lev):
                    G.set(x, y, z, B('calcite'))
                G.set(x, lev - 1, z, B('smooth_quartz'))
                if (x, z) in TOP and TOP[(x, z)] < lev - 1:
                    TOP[(x, z)] = lev - 1
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            G.set(x, L, z, B('polished_diorite' if (x + z) % 2 else 'calcite'))
            for y in range(L + 1, L + 13):
                if edge:
                    along = z if x in (X0, X1) else x
                    k = y - L
                    dawn = TEAL if k <= 4 else 'pink_stained_glass' if k <= 6 else GOLD if k <= 8 else AMBER
                    st = 'quartz_pillar[axis=y]' if along % 3 == 0 else (dawn if 3 <= k <= 10 else 'calcite')
                    G.set(x, y, z, B(st))
            rr = 8 - abs(x - tx)
            y = L + 13 + min(rr, 7)
            if x - tx:
                G.set(x, y, z, stairs(cu('cut', patina(2, x, z, 85)), 'east' if x < tx else 'west'))
            else:
                G.set(x, y, z, B(cu('chiseled', 1)))
            if z in (Z0, Z1):
                for yy in range(L + 13, y):
                    G.set(x, yy, z, B('calcite'))
            elif not edge:
                G.set(x, L + 12, z, B('air'))
    for z in range(Z0, Z1 + 1):                                    # the ridge crest in fresh copper
        G.set(tx, L + 21, z, B(cu('chiseled', 0)))
    for z in range(Z0, Z1 + 1, 3):                                 # buttresses
        for x, d in ((X0 - 1, 'east'), (X1 + 1, 'west')):
            for y in range(L + 1, L + 10):
                G.set(x, y, z, B('calcite'))
            G.set(x, L + 10, z, stairs('quartz', d))
    for a in range(-4, 5):                                         # the rose window
        for b in range(-4, 5):
            rr = math.hypot(a, b)
            if rr <= 4:                                                # amber sun, gold rays, teal sky, a ring of dawn
                ray = a == 0 or b == 0 or abs(a) == abs(b)
                G.set(tx + a, L + 14 + b, Z1, B(AMBER if rr <= 1.2 else GOLD if rr <= 2.3 else 'pink_stained_glass' if rr > 3.5 else
                                              GOLD if ray else TEAL))
    for x in range(tx - 1, tx + 2):                                # the door
        for y in range(L + 1, L + 6):
            G.clear(x, y, Z1)
    for x in (tx - 1, tx + 1):
        G.set(x, L + 1, Z1, B('cherry_door[facing=south,half=lower,hinge=%s,open=true,powered=false]' % ('left' if x < tx else 'right')))
        G.set(x, L + 2, Z1, B('cherry_door[facing=south,half=upper,hinge=%s,open=true,powered=false]' % ('left' if x < tx else 'right')))
    for x in (tx - 4, tx - 2, tx + 2, tx + 4):                     # the portico
        for y in range(L + 1, L + 10):
            G.set(x, y, Z1 + 2, B('quartz_pillar[axis=y]'))
    for x in range(tx - 5, tx + 6):
        for z in (Z1 + 1, Z1 + 2):
            G.set(x, L + 10, z, B('smooth_quartz'))
            if z == Z1 + 2:
                G.set(x, L + 11, z, stairs(cu('cut', patina(2, x, z, 86)), 'north'))
        G.set(x, L + 11, Z1 + 1, B(cu('cut', patina(2, x, Z1 + 1, 86))))
    for z in (Z1 + 1, Z1 + 2):
        for x in range(tx - 4, tx + 5):
            G.set(x, L, z, B('smooth_quartz'))
    hang(tx, L + 9, Z1 + 1)
    # inside: pews, the aisle, the altar under the sun, candles, chandeliers
    for z in range(tz - 5, Z1 - 1):
        G.set(tx, L + 1, z, B('pink_carpet'))
        if (z - tz) % 2 == 0:
            for x in range(X0 + 2, tx - 1):
                bench(x, L + 1, z, 'north', 'left' if x == X0 + 2 else ('right' if x == tx - 2 else 'middle'))
            for x in range(tx + 2, X1 - 1):
                bench(x, L + 1, z, 'north', 'left' if x == tx + 2 else ('right' if x == X1 - 2 else 'middle'))
    for x in range(tx - 3, tx + 4):
        for z in range(Z0 + 1, Z0 + 4):
            G.set(x, L + 1, z, B('smooth_quartz'))
    for x in range(tx - 3, tx + 4):
        G.set(x, L + 1, Z0 + 4, stairs('quartz', 'north'))
    G.set(tx, L + 2, Z0 + 2, B('chiseled_quartz_block'))
    G.set(tx, L + 3, Z0 + 2, B('candle[candles=4,lit=true,waterlogged=false]'))
    for x in (tx - 2, tx + 2):
        G.set(x, L + 2, Z0 + 2, B('candle[candles=3,lit=true,waterlogged=false]'))
    for dx in range(-3, 4):                                        # the sun over the altar
        for dy in range(-3, 4):
            rr = math.hypot(dx, dy)
            if rr <= 3.4:
                G.set(tx + dx, L + 8 + dy, Z0, B('ochre_froglight[axis=y]' if rr < 1.6 else 'gold_block' if rr > 2.6 else 'yellow_stained_glass'))
    for z in (tz - 4, tz + 2):
        for y in range(L + 9, L + 12):
            G.set(tx, y, z, B('chain[axis=y,waterlogged=false]'))
        chandelier(tx, L + 8, z)
    MARKERS.append(('priest', (tx + 1, L + 2, Z0 + 2)))
    # the bell tower at the apse, a spire of pink terracotta
    bz = Z0 - 3
    for y in range(L + 1, L + 31):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) == 2:
                    k = y - L
                    G.set(tx + dx, y, bz + dz, B('quartz_pillar[axis=y]' if abs(dx) == 2 and abs(dz) == 2 else
                                                'air' if (k > 24 and (dx == 0 or dz == 0)) else 'calcite'))
    for y in range(L + 1, L + 29):                                 # a ladder up to the belfry
        G.set(tx, y, bz - 1, B('ladder[facing=south,waterlogged=false]'))
    for y in (L + 1, L + 2):                                       # the door from the apse
        G.clear(tx, y, Z0)
        G.clear(tx, y, Z0 - 1)
    G.set(tx, L + 29, bz, B('bell[attachment=ceiling,facing=north,powered=false]'))
    G.set(tx, L + 30, bz, B('calcite'))
    for k in range(0, 9):
        r = 3 - (k * 3) // 9
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) == r:
                    G.set(tx + dx, L + 31 + k, bz + dz, B('cherry_planks' if k % 3 else 'stripped_cherry_log[axis=y]'))
    G.set(tx, L + 40, bz, B('gold_block'))
    G.set(tx, L + 41, bz, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    for c, k in N.CELL.items():                                    # the approach stair up the knoll
        if k == 'temple_ground' and abs(c[0] - tx) <= 2 and c[1] > Z1 + 2:
            APPROACH.add(c)
    sign(tx, L + 10, Z1 + 3, 'south', ['', ('place', 'temple'), '', ''], wall=True, glow=True, wood='cherry')


# ---------------- Terra's Workshop ----------------
def workshop():
    """Terra's Workshop: a brick hall under a sawtooth of dark tiles with north lights, copper
    bands and stacks with smoke, the forge glowing through the ground-floor windows, benches,
    anvils and a loft inside, and the great gear on the street front."""
    cells = cells_of('workshop')
    x0, z0, x1, z1 = max_rect(cells)
    L = N.PADS['workshop']
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            G.set(x, L, z, B('polished_andesite' if (x + z) % 2 else 'stone_bricks'))
            if edge:
                for y in range(L + 1, L + 11):
                    along = x if z in (z0, z1) else z
                    k = y - L
                    st = ('waxed_cut_copper' if k in (5, 10) else 'tuff_bricks' if along % 4 == 0 else
                          'orange_stained_glass' if 2 <= k <= 3 else 'glass' if 6 <= k <= 8 else 'bricks')
                    G.set(x, y, z, B(st))
            k = (x - x0) % 6
            cst = patina(1, x, z, 87)
            if k < 4:
                sky = k == 2 and not edge and z % 4 == 1                 # skylights in the copper slopes
                G.set(x, L + 11 + k, z, B('glass') if sky else stairs(cu('cut', cst), 'east'))
                for y in range(L + 11, L + 11 + k):
                    if edge:
                        G.set(x, y, z, B(cu('cut', cst)))
            else:
                for y in range(L + 11, L + 15):
                    G.set(x, y, z, B('glass' if k == 4 else cu('cut', cst)) if edge or k == 4 else B('air'))
            G.set(x, L + 10, z, B('waxed_cut_copper') if edge else B('air'))
    for (sx, sz) in ((x0 + 4, z0 + 3), (x1 - 4, z0 + 3)):           # the stacks
        for y in range(L + 11, L + 28):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (dx, dz) != (0, 0):
                        G.set(sx + dx, y, sz + dz, B('bricks' if (y - L) % 6 else 'waxed_cut_copper'))
        G.set(sx, L + 27, sz, B('campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]'))
        for y in range(L + 1, L + 27):
            if G.get(sx, y, sz) is None:
                G.set(sx, y, sz, B('bricks'))
    gx, gz = x0 - 1, (z0 + z1) // 2                                # the great gear
    for k in range(40):
        th = 2 * math.pi * k / 40
        for rr in (5.0, 5.8):
            G.set(gx, L + 8 + round(rr * math.sin(th)), gz + round(rr * math.cos(th)), B('waxed_copper_block'))
    for k in range(10):
        th = 2 * math.pi * k / 10
        G.set(gx, L + 8 + round(7 * math.sin(th)), gz + round(7 * math.cos(th)), B('waxed_cut_copper'))
    G.set(gx, L + 8, gz, B('gold_block'))
    for y in range(L + 1, L + 5):                                  # the loading door
        for z in range(gz - 1, gz + 2):
            G.clear(x0, y, z)
    # inside: the forge along the back, benches, anvils, the loft
    for x in range(x0 + 2, x1 - 1):
        z = z1 - 1
        G.set(x, L + 1, z, B(['blast_furnace[facing=north,lit=true]', 'furnace[facing=north,lit=true]', 'smoker[facing=north,lit=true]',
                              'bricks'][(x - x0) % 4]))
        G.set(x, L + 2, z, B('magma_block' if (x - x0) % 4 == 3 else 'bricks'))
        G.set(x, L + 3, z, B('bricks'))
    for x in range(x0 + 3, x1 - 2, 4):
        G.set(x, L + 1, z0 + 3, B('crafting_table'))
        G.set(x + 1, L + 1, z0 + 3, B('smithing_table'))
        G.set(x, L + 1, z0 + 6, B('anvil[facing=east]'))
        G.set(x + 1, L + 1, z0 + 6, B('grindstone[face=floor,facing=north]'))
        G.set(x, L + 1, (z0 + z1) // 2 + 2, B('crafter[crafting=false,orientation=north_up,triggered=false]'))
    for x in range(x0 + 1, x1):                                    # the loft along the back
        for z in range(z1 - 4, z1):
            G.set(x, L + 6, z, B('spruce_planks'))
        G.set(x, L + 7, z1 - 5, B('spruce_fence[east=true,north=false,south=false,waterlogged=false,west=true]'))
    for y in range(L + 1, L + 7):
        G.set(x1 - 1, y, z1 - 5, B('ladder[facing=north,waterlogged=false]'))
    for x in range(x0 + 2, x1 - 1, 3):
        G.set(x, L + 7, z1 - 1, B('bookshelf'))
        G.set(x + 1, L + 7, z1 - 2, B('cartography_table'))
    for x in range(x0 + 3, x1 - 1, 5):                             # copper bulbs on chains
        for z in (z0 + 4, (z0 + z1) // 2 + 1):
            for y in range(L + 7, L + 10):
                G.set(x, y, z, B('chain[axis=y,waterlogged=false]'))
            G.set(x, L + 6, z, B('waxed_copper_bulb[lit=true,powered=false]'))
    MARKERS.append(('inventor', ((x0 + x1) // 2, L + 1, (z0 + z1) // 2)))
    sign(x0 - 1, L + 3, gz + 3, 'west', ['', ('place', 'workshop'), '', ''], wall=True, glow=True, wood='dark_oak')


# ---------------- the Botanical Garden ----------------
def palm_house():
    """Juan's Botanical Garden: a palm house of glass, barrel vault on gold ribs, a dome over the
    great tree and its pond, planted beds round paths, benches, spore blossoms under the glass."""
    cells = cells_of('greenhouse')
    x0, z0, x1, z1 = max_rect(cells)
    L = N.PADS['greenhouse']
    zc = (z0 + z1) / 2
    R = (z1 - z0) / 2
    mx = (x0 + x1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            path = abs(z - round(zc)) <= 1 or abs(x - mx) <= 1
            G.set(x, L, z, B('mud_bricks' if path else ('moss_block' if (x + z) % 3 else 'grass_block')))
            if x in (x0, x1) or z in (z0, z1):
                for y in range(L + 1, L + 4):
                    G.set(x, y, z, B('calcite' if y == L + 1 else 'glass'))
            dz = z - zc
            yv = L + 4 + int(math.sqrt(max(0.0, R * R - dz * dz)))
            G.set(x, yv, z, B('gold_block' if x % 3 == 0 else 'glass'))
            if x in (x0, x1):
                for y in range(L + 4, yv):
                    G.set(x, y, z, B('glass'))
            if not path and x0 < x < x1 and z0 < z < z1:
                r = h(x, z, 77)
                plant = ['fern', 'large_fern[half=lower]', 'allium', 'blue_orchid', 'azalea', 'flowering_azalea', 'lily_of_the_valley',
                         'big_dripleaf[facing=north,tilt=none,waterlogged=false]'][int(r * 8)]
                if 'large_fern' in plant:
                    G.set(x, L + 1, z, B('large_fern[half=lower]'))
                    G.set(x, L + 2, z, B('large_fern[half=upper]'))
                elif 'big_dripleaf' in plant:
                    G.set(x, L + 1, z, B('big_dripleaf_stem[facing=north,waterlogged=false]'))
                    G.set(x, L + 2, z, B(plant))
                else:
                    G.set(x, L + 1, z, B(plant))
    dome(mx, round(zc), L + 4 + int(R) - 2, 6, shell='glass', rib='gold_block', ribs=8)
    for (x, z, rr, dx, dz) in ring_xz(mx, round(zc), 0, 3.4):          # the pond round the tree
        if rr > 1.2:
            G.set(x, L, z, B('water[level=0]') if rr < 2.6 else B('mossy_stone_bricks'))
            G.set(x, L - 1, z, B('clay'))
            G.clear(x, L + 1, z)
            if 2.6 <= rr:
                G.set(x, L + 1, z, B('mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]') if (x + z) % 3 == 0 else B('air'))
    tree(mx, round(zc), L, 8, 3.4, 'jungle', 'jungle_log')
    for x in range(x0 + 2, x1 - 1, 3):
        for z in (z0 + 1, z1 - 1):
            if G.get(x, L + int(R) + 2, z) is None:
                G.set(x, L + int(R) + 2, z, B('spore_blossom'))
    for z in (round(zc) - 2, round(zc) + 2):
        for x in (x0 + 3, x1 - 3):
            bench(x, L + 1, z, 'north' if z < zc else 'south')
    for z in range(round(zc) - 1, round(zc) + 2):
        for y in (L + 1, L + 2, L + 3):
            G.clear(x1, y, z)
    for y in range(L + 5, L + 8):
        hang(mx - 4, y, round(zc)) if y == L + 7 else G.set(mx - 4, y, round(zc), B('chain[axis=y,waterlogged=false]'))
    gx_ = next(x for x in range(mx + 4, x1) if not G.filled(x, L + 1, round(zc)) and not G.filled(x, L + 2, round(zc)))
    MARKERS.append(('gardener', (gx_, L + 1, round(zc))))
    sign(x1 + 1, L + 2, round(zc) + 2, 'east', ['', ('place', 'botanical'), '', ''], wall=True, glow=True, wood='birch')


# ---------------- the Clock Tower ----------------
def clock_tower():
    """The Clock Tower in its square: a plinth with a door, ladders and landings inside, a gallery
    under the four clock faces, a belfry with its bell, a verdigris spire."""
    cells = cells_of('tower')
    xs = [c[0] for c in cells]
    zs = [c[1] for c in cells]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    L = N.PADS['tower']
    Ht = 38
    for y in range(L + 1, L + Ht + 1):
        k = y - L
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    open_ = k > Ht - 7 and not corner and k < Ht
                    slit = not corner and (x == cx or z == cz) and k % 10 in (4, 5) and k < 20
                    G.set(x, y, z, B('quartz_pillar[axis=y]' if corner else cu('cut', patina(1, x, z, 89, y)) if k % 10 == 0 else
                                     'air' if open_ else lancet(k % 10 - 4, 2) if slit else 'calcite'))
                elif k % 10 == 0 and k < Ht:
                    G.set(x, y, z, B('spruce_planks'))
    for y in range(L + 1, L + Ht):                                  # the ladder up through the landings
        G.set(cx + 2, y, cz, B('ladder[facing=west,waterlogged=false]'))
    for k in range(1, 4):
        hang(cx - 1, L + 10 * k - 1, cz - 1)
    for (a, b) in N4:                                               # four clock faces
        fx, fz = cx + a * 4, cz + b * 4
        for u in range(-2, 3):
            for v in range(-2, 3):
                if u * u + v * v <= 6:
                    px = fx if a else cx + u
                    pz = fz if b else cz + u
                    G.set(px, L + 26 + v, pz, B('gold_block' if u * u + v * v > 3 else 'white_concrete' if (u, v) != (0, 0) else 'ochre_froglight[axis=y]'))
    for x in range(x0 - 1, x1 + 2):                                 # the gallery under the faces
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                G.setdefault(x, L + 22, z, B('smooth_quartz'))
                G.setdefault(x, L + 23, z, B('%s[waterlogged=false]' % cu('grate', 2)))
    G.set(cx, L + Ht - 1, cz, B('bell[attachment=ceiling,facing=north,powered=false]'))
    for k in range(0, 12):
        r = 4 - (k * 4) // 12
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if max(abs(x - cx), abs(z - cz)) == r:
                    G.set(x, L + Ht + 1 + k, z, B('waxed_oxidized_cut_copper'))
    G.set(cx, L + Ht, cz, B('calcite'))
    G.set(cx, L + Ht + 13, cz, B('gold_block'))
    G.set(cx, L + Ht + 14, cz, B('lightning_rod[facing=up,powered=false,waterlogged=false]'))
    for y in range(L + 1, L + 4):
        G.clear(cx, y, z1)
    G.set(cx, L + 1, z1, B('spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]'))
    G.set(cx, L + 2, z1, B('spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]'))
    sign(cx, L + 4, z1 + 1, 'south', ['', ('place', 'clock'), '', ''], wall=True, glow=True, wood='spruce')


# ---------------- the templetes ----------------
def templetes():
    """A small domed templete on the promenade at the end of every radial: every street ends on
    something to look at."""
    made = 0
    for (es, en, x, z, k) in N.LANDMARKS:
        if k != 'mirador':
            continue
        cands = [c for c in N.CELL if kind(c) == 'promenade' and math.hypot(c[0] - x, c[1] - z) < 7]
        if not cands:
            continue
        ctr = min(cands, key=lambda c: math.hypot(c[0] - x * 0.97, c[1] - z * 0.97))
        L = TOP[ctr]
        for (px, pz, rr, dx, dz) in ring_xz(ctr[0], ctr[1], 0, 3.4):
            if (px, pz) in TOP:
                for y in range(TOP[(px, pz)] + 1, L + 1):
                    G.set(px, y, pz, B('calcite'))
                G.set(px, L, pz, B('smooth_quartz' if rr > 1 else 'gold_block'))
        for kk in range(8):
            th = 2 * math.pi * kk / 8
            px, pz = ctr[0] + round(3 * math.cos(th)), ctr[1] + round(3 * math.sin(th))
            for y in range(L + 1, L + 6):
                G.set(px, y, pz, B('quartz_pillar[axis=y]'))
        for (px, pz, rr, dx, dz) in ring_xz(ctr[0], ctr[1], 0, 3.4):
            G.set(px, L + 6, pz, B('smooth_quartz'))
        dome(ctr[0], ctr[1], L + 6, 3, shell=cu('cut', 1 + made % 3), rib='gold_block', ribs=4)
        made += 1
        G.set(ctr[0], L + 5, ctr[1], LANTERN.replace('hanging=false', 'hanging=true'))
        G.set(ctr[0], L + 10, ctr[1], B('lightning_rod[facing=up,powered=false,waterlogged=false]'))


# ---------------- the Last Falls ----------------
def falls():
    """The Last Falls, a real cascade. The east canal ends in an overflow basin on the rim; its
    outer side has a three-block spillway where the water leaves the island and falls down the
    island's side into a catch basin on a rock ledge, which is its end: the falling column stops
    on the basin's still water, nothing spreads. The water is written in its steady state (sources
    in the basins, flowing water on the lip, falling water in the column) so the result is the same
    whether the game ticks it or not. The barrier round the shore steps outward to enclose the
    fall and the ledge instead of cutting them."""
    lm = N.landmark('Cascada del Fin')
    if not lm:
        return None
    x, z = lm
    cands = [c for c in N.CELL if kind(c) == 'canal' and math.hypot(c[0] - x, c[1] - z) < 8]
    if not cands:
        return None
    end = max(cands, key=lambda c: math.hypot(*c))
    ux, uz = end[0] / math.hypot(*end), end[1] / math.hypot(*end)
    out = ((1 if ux > 0 else -1), 0) if abs(ux) >= abs(uz) else (0, (1 if uz > 0 else -1))
    side = (-out[1], out[0])
    W = N.CANAL_LEVEL.get(end, TOP[end] + 2) - 1                   # the canal's water surface
    # the rim: the island cells along the spill line, the first cell outside is the lip
    line = []
    for w in (-1, 0, 1):
        p = (end[0] + side[0] * w, end[1] + side[1] * w)
        while (p[0] + out[0], p[1] + out[1]) in N.CELL:
            p = (p[0] + out[0], p[1] + out[1])
        line.append(p)
    # the overflow basin: the last three cells before the rim on each of the three lines
    basin = set()
    for p in line:
        for k in range(0, 3):
            basin.add((p[0] - out[0] * k, p[1] - out[1] * k))
    wallring = set()
    for (bx, bz) in basin:
        for a, b in N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            q = (bx + a, bz + b)
            if q not in basin and q in N.CELL and kind(q) != 'canal':
                wallring.add(q)
    for (bx, bz) in basin:
        for y in range(TOP.get((bx, bz), W - 3) + 1, W - 1):
            G.set(bx, y, bz, B('prismarine_bricks'))
        G.set(bx, W - 2, bz, B('sea_lantern'))
        G.set(bx, W - 1, bz, B('water[level=0]'))
        G.set(bx, W, bz, B('water[level=0]'))
        for y in range(W + 1, W + 4):
            G.clear(bx, y, bz)
        WATER_OK.update({(bx, W - 1, bz), (bx, W, bz)})
    for (qx, qz) in wallring:
        for y in range(TOP.get((qx, qz), W - 3) + 1, W + 2):
            G.set(qx, y, qz, B('prismarine_bricks' if y <= W else 'dark_prismarine'))
    # the lip: flowing water one cell outside the rim, then the fall
    lips = [(p[0] + out[0], p[1] + out[1]) for p in line]
    rim_bottom = min(BOTTOM[p] for p in line)
    ledge_y = rim_bottom - 6                                        # the catch basin's still water
    fx, fz = out
    for (lx, lz) in lips:
        G.set(lx, W, lz, B('water[level=1]'))
        for y in range(ledge_y + 1, W):
            G.set(lx, y, lz, B('water[level=8]'))
        WATER_OK.update({(lx, y, lz) for y in range(ledge_y + 1, W + 1)})
        FALLS.add((lx, lz))
    # the lip's cheeks: stone on both sides of the spillway, outside the rim
    for (lx, lz) in (lips[0], lips[-1]):
        for s in (-1, 1):
            q = (lx + side[0] * s, lz + side[1] * s)
            if q in N.CELL or q in lips:
                continue
            for y in range(W - 1, W + 2):
                G.set(q[0], y, q[1], B('dark_prismarine'))
            FALLS.add(q)
    # the catch basin on a ledge of rock, one to three cells out from the fall
    catch = set()
    for (lx, lz) in lips:
        for k in range(-1, 3):
            for s in (-1, 0, 1):
                catch.add((lx + fx * k + side[0] * s, lz + fz * k + side[1] * s))
    catch = {c for c in catch if c not in N.CELL}
    rim = set()
    for (cx, cz) in catch:
        for a, b in N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            q = (cx + a, cz + b)
            if q not in catch:
                rim.add(q)
    for (cx, cz) in catch:
        for y in range(ledge_y - 4, ledge_y):
            G.set(cx, y, cz, B('stone' if y < ledge_y - 1 else 'sea_lantern' if (cx + cz) % 3 == 0 else 'prismarine_bricks'))
        G.set(cx, ledge_y, cz, B('water[level=0]'))
        WATER_OK.add((cx, ledge_y, cz))
        FALLS.add((cx, cz))
    for (qx, qz) in rim:                                            # under the island too: the rock is higher
        for y in range(ledge_y - 5, ledge_y + 2):
            G.setdefault(qx, y, qz, B('stone' if y < ledge_y - 1 else 'prismarine_bricks' if y <= ledge_y else 'dark_prismarine'))
        if (qx, qz) not in N.CELL:
            FALLS.add((qx, qz))
    for (cx, cz) in catch:                                          # the ledge's rock hangs below
        for y in range(ledge_y - 9, ledge_y - 4):
            if h(cx, y + cz, 90) < 0.8 - 0.12 * (ledge_y - 4 - y):
                G.set(cx, y, cz, B('stone' if (cx + y) % 3 else 'calcite'))
    NOT_BARRIER.update(FALLS)
    return {'water': W, 'ledge': ledge_y, 'fall': W - ledge_y - 1, 'lips': lips, 'out': out, 'side': side}
