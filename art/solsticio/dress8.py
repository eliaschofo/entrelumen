"""Solsticio v8, phase 2: what makes the massing a lived-in city.

  - facades: a door on the street for every building (steps up to it when the pad stands above
    the street, a lantern over it), sills or shutters framing the windows, flower boxes, balconies
    on the wider streets, shopfronts with awnings, banners, crates and barrels on the Axis, in the
    Market and in the Inns, quoins and a lantern on the corners where two streets meet;
  - roofs: dormers on the street slopes, chimneys (smoking over the workshops and the inns);
  - interiors: behind every door the rooms of houses.py (shop, tavern, kitchen, living room,
    bedroom, study), fitted to the largest rectangle the lot has behind its door, a ladder between
    floors, lanterns under every ceiling so no room is dark; forges glowing in the Workshops;
  - street life: lamps every nine blocks, wall lanterns in the lanes, benches, planters, fountains
    or wells in the courtyards, signposts at the crossings naming the plazas.

bind(city8) shares the grid and helpers of the builder.
"""
import math
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import houses  # noqa: E402
from voxkit import orient  # noqa: E402

NAMES = ('G', 'N', 'B', 'stairs', 'wall', 'h', 'kind', 'FACING', 'N4', 'N8', 'MARKERS', 'TOP', 'LOT_INFO', 'LOT_TOP',
         'DMAT', 'STOREY', 'lot_dist', 'exposed', 'walkable', 'cls', 'axis_part', 'mp_state', 'sign', 'LANTERN', 'lamp',
         'PLACE_KEYS', 'cu', 'patina', 'cu_slab', 'roof_at', 'cu_stage')
VEC = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}
NAMEOF = {v: k for k, v in VEC.items()}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}

# per district: door wood, sill stairs, shutter wood, awning, banner colour, flower boxes, chimney smoke
DRESS = {
    'market': dict(door='spruce', sill='sandstone', shutter='spruce', awning='mcwroofs:yellow_striped_awning',
                   banner='orange', boxes=True, smoke=False),
    'inns': dict(door='spruce', sill='sandstone', shutter='spruce', awning='mcwroofs:cyan_striped_awning',
                 banner='yellow', boxes=True, smoke=True),
    'gardens': dict(door='birch', sill='smooth_quartz', shutter=None, awning=None, banner='lime', boxes=True, smoke=False),
    'travellers': dict(door='birch', sill='smooth_quartz', shutter='birch', awning=None, banner='light_blue', boxes=True, smoke=False),
    'temple': dict(door='cherry', sill='smooth_quartz', shutter='cherry', awning=None, banner='pink', boxes=True, smoke=False),
    'workshops': dict(door='dark_oak', sill='brick', shutter=None, awning=None, banner='gray', boxes=False, smoke=True),
}
STATS = {}
INNS = {}                # lot id -> 'inn' for the side-quest inns (set by city8 before dressing)
TRADES = {'market': ['bakery', 'grocer', 'jeweller', 'cafe'], 'inns': ['inn', 'cafe', 'tailor'],
          'gardens': ['florist', 'cafe'], 'travellers': ['cartographer', 'cafe', 'tailor'],
          'temple': ['chandler', 'florist', 'cafe'], 'workshops': ['forge', 'carpenter', 'glazier']}


def bind(C):
    globals().update({k: getattr(C, k) for k in NAMES})


def count(k, n=1):
    STATS[k] = STATS.get(k, 0) + n


def free(x, y, z):
    return not G.filled(x, y, z)


def put(x, y, z, st):
    """Set an empty cell only."""
    return G.setdefault(x, y, z, st)


def street_front(lot):
    """Perimeter cells of the lot with the direction of the open ground in front of them, the cell
    there and its ground level: [(cell, d, front, level)]."""
    s = set(lot['cells'])
    out = []
    for c in lot['cells']:
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n in s or n in N.LOT or n not in TOP:
                continue
            out.append((c, (a, b), n, TOP[n]))
    return out


# ---------------- facades ----------------
def dress_lots():
    for lot in N.LOTS:
        if not lot['cells']:
            continue
        info = LOT_INFO[lot['id']]
        door(lot, info)
        balcony(lot, info)
        windows(lot, info)
        shopfront(lot, info)
        corner(lot, info)
        roofscape(lot, info)


def door(lot, info):
    dr = info.get('door')
    if not dr:
        return
    (c, d, n) = dr
    pad = lot['pad']
    D = DRESS[lot['district']]
    face = NAMEOF[d]
    wood = D['door']
    G.set(c[0], pad + 1, c[1], B('%s_door[facing=%s,half=lower,hinge=left,open=false,powered=false]' % (wood, face)))
    G.set(c[0], pad + 2, c[1], B('%s_door[facing=%s,half=upper,hinge=left,open=false,powered=false]' % (wood, face)))
    count('doors')
    lev = TOP.get(n)
    if lev is not None and walkable(n):
        dz = pad - lev
        if dz == 1 and free(n[0], lev + 1, n[1]):
            G.set(n[0], lev + 1, n[1], stairs(D['sill'] if D['sill'] != 'smooth_quartz' else 'smooth_quartz', OPP[face]))
            count('door steps')
        elif dz == 2:
            m = (n[0] + d[0], n[1] + d[1])
            if TOP.get(m) == lev and walkable(m) and free(m[0], lev + 1, m[1]) and free(n[0], lev + 1, n[1]):
                G.set(n[0], lev + 1, n[1], B('stone_bricks'))
                G.set(n[0], lev + 2, n[1], stairs('stone_brick', OPP[face]))
                G.set(m[0], lev + 1, m[1], stairs('stone_brick', OPP[face]))
                count('door steps', 2)
    y = max(pad, lev if lev is not None else pad) + 3
    if free(n[0], y, n[1]) and G.filled(c[0], y, c[1]):
        G.set(n[0], y, n[1], mp_state('mcwlights:wall_lantern', facing=face))
        count('door lanterns')


def windows(lot, info):
    """Sills (upside-down stairs) or shutters (open trapdoors against the piers) framing the
    windows of the upper storeys, flower boxes under some."""
    D = DRESS[lot['district']]
    pad = lot['pad']
    variant = info['seed'] % 4
    shutters = D['shutter'] and variant in (0, 2)
    boxes = D['boxes'] and variant in (2, 3)
    sills = variant in (1, 3) or (variant == 2 and not D['boxes'])
    glass = B('glass')
    for (c, d, n, lev) in street_front(lot):
        face = NAMEOF[d]
        for s in range(1, info['floors']):
            y0 = pad + STOREY * s
            if G.get(c[0], y0 + 2, c[1]) == glass:
                if n in N.CELL and lev >= y0 + 1:
                    continue
                if boxes and (variant == 2 or (c[0] + c[1]) % 2 == 0) and free(n[0], y0 + 1, n[1]):
                    G.set(n[0], y0 + 1, n[1], mp_state('supplementaries:flower_box', facing=face, face='wall'))
                    count('flower boxes')
                elif sills and free(n[0], y0 + 1, n[1]):
                    G.set(n[0], y0 + 1, n[1], stairs(D['sill'], OPP[face], half='top'))
                    count('sills')
            elif shutters and G.filled(c[0], y0 + 2, c[1]):
                side = [(c[0] + t[0], c[1] + t[1]) for t in ((d[1], d[0]), (-d[1], -d[0]))]
                if any(G.get(q[0], y0 + 2, q[1]) == glass and N.LOT.get(q) == lot['id'] for q in side):
                    for y in (y0 + 2, y0 + 3):
                        if free(n[0], y, n[1]):
                            G.set(n[0], y, n[1], B('%s_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]' % (D['shutter'], face)))
                    count('shutters')


def balcony(lot, info):
    """Balconies over the streets, two houses in three: a copper trapdoor floor, copper grates for
    the rail, a flower box in the middle (a planter on every balcony) and a copper door out of the
    middle window; tall houses stack a second one above."""
    if lot['prio'] < 44 or info['floors'] < 2 or h(lot['id'], 17, 555) > 0.67:
        return
    pad = lot['pad']
    y0 = pad + STOREY
    st = cu_stage(info)
    fronts = {}
    for (c, d, n, lev) in street_front(lot):
        if lev <= pad and walkable(n) and (n[0] + d[0], n[1] + d[1]) in TOP:
            fronts.setdefault(d, []).append(c)
    for d, cs in fronts.items():
        t = (1, 0) if d[0] == 0 else (0, 1)
        cs = sorted(cs, key=lambda c: c[0] * t[0] + c[1] * t[1])
        run = []
        for c in cs:
            if run and (c[0] - run[-1][0], c[1] - run[-1][1]) != t:
                run = []
            run.append(c)
            if len(run) == 3:
                break
        if len(run) < 3:
            continue
        face = NAMEOF[d]
        levels = [y0] + ([y0 + STOREY] if info['floors'] >= 3 and h(lot['id'], 19, 555) < 0.5 else [])
        made = 0
        for yb in levels:
            if any(not free(c[0] + d[0], y, c[1] + d[1]) for c in run for y in (yb, yb + 1)):
                break
            one_balcony(run, d, face, yb, st)
            made += 1
        if made:
            return


def one_balcony(run, d, face, y0, st):
    mid = run[1]
    for i, c in enumerate(run):
        n = (c[0] + d[0], c[1] + d[1])
        s = patina(st, n[0], n[1], 17, y0)
        G.set(n[0], y0, n[1], B('%s[facing=%s,half=top,open=false,powered=false,waterlogged=false]' % (cu('trapdoor', s), face)))
        if i == 1:
            G.set(n[0], y0 + 1, n[1], mp_state('supplementaries:flower_box', facing=face, face='floor'))
            count('balcony planters')
        else:
            G.set(n[0], y0 + 1, n[1], B('%s[waterlogged=false]' % cu('grate', s)))
    door = cu('door', patina(st, mid[0], mid[1], 19))
    G.set(mid[0], y0 + 1, mid[1], B('%s[facing=%s,half=lower,hinge=left,open=false,powered=false]' % (door, face)))
    G.set(mid[0], y0 + 2, mid[1], B('%s[facing=%s,half=upper,hinge=left,open=false,powered=false]' % (door, face)))
    count('balconies')


def shopfront(lot, info):
    """Ground floors on the Axis, in the Market and the Inns: shop windows, an awning over them,
    a banner by the door, crates and barrels on the wide pavements."""
    front_kinds = set(lot['kinds'])
    on_axis = bool(front_kinds & {'axis', 'trees'})
    if not (on_axis or (lot['district'] in ('market', 'inns') and lot['prio'] >= 55) or 'crafts' in front_kinds):
        return
    D = DRESS[lot['district']]
    pad = lot['pad']
    dr = info.get('door')
    glass = B('glass')
    awning = D['awning'] or 'mcwroofs:yellow_striped_awning'
    placed = 0
    for (c, d, n, lev) in street_front(lot):
        if not walkable(n) or lev > pad:
            continue
        face = NAMEOF[d]
        along = c[0] + c[1]
        if dr and c == dr[0]:
            continue
        pier = along % 4 == 0
        if not pier:
            for y in (pad + 1, pad + 2):
                if G.get(c[0], y, c[1]) not in (None,) and 'door' not in (G.get(c[0], y, c[1]) or ''):
                    G.set(c[0], y, c[1], glass)
        if free(n[0], pad + 4, n[1]) and N.CELL.get(n) != 'canal':
            G.set(n[0], pad + 4, n[1], mp_state(awning, facing=face))
            placed += 1
        wide = cls(n) in ('axis', 'boulevard', 'plaza', 'crafts') or N.PATHS[N.OWNER[n]].width >= 7 if n in N.OWNER else cls(n) == 'plaza'
        if wide and pier and free(n[0], lev + 1, n[1]) and not (G.get(n[0], lev + 1, n[1]) or '').endswith('stairs'):
            G.set(n[0], lev + 1, n[1], B('barrel[facing=up,open=false]') if (along // 4) % 2 else mp_state('refurbished_furniture:birch_crate'))
            count('crates and barrels')
        if pier and free(n[0], pad + 6, n[1]) and G.filled(c[0], pad + 6, c[1]) and (along // 4) % 3 == 0:
            G.set(n[0], pad + 6, n[1], B('%s_wall_banner[facing=%s]' % (D['banner'], face)))
            count('banners')
    if placed:
        count('shopfronts')
        count('awnings', placed)
        trade = 'inn' if INNS.get(lot['id']) or lot['district'] == 'inns' and info['seed'] % 3 == 0 else \
            TRADES[lot['district']][info['seed'] % len(TRADES[lot['district']])]
        for (c, d, n, lev) in street_front(lot):
            along = c[0] + c[1]
            if along % 4 != 0 or not walkable(n) or lev > pad or not G.filled(c[0], pad + 3, c[1]):
                continue
            if free(n[0], pad + 3, n[1]) and free(n[0], pad + 2, n[1]):
                sign(n[0], pad + 3, n[1], NAMEOF[d], ['', ('trade', trade), '', ''], wall=True, glow=False,
                     wood=DRESS[lot['district']]['door'])
                count('shop signs')
                break


def corner(lot, info):
    """Where the lot turns a corner between two streets: quoins up the corner and a lantern on
    top of it."""
    pad = lot['pad']
    top = LOT_TOP[lot['id']]
    D = DMAT[lot['district']]
    trim = D['trim']
    trimb = B(trim + ('[axis=y]' if trim.endswith(('_log', '_pillar')) else ''))
    wallb = B(info['wall'])
    s = set(lot['cells'])
    if not info.get('corner'):
        return
    best = None
    for c in lot['cells']:
        outs = [(a, b) for a, b in N4 if (c[0] + a, c[1] + b) not in s and (c[0] + a, c[1] + b) not in N.LOT
                and (c[0] + a, c[1] + b) in TOP and walkable((c[0] + a, c[1] + b))]
        if len(outs) < 2 or outs[0][0] == -outs[1][0] and outs[0][1] == -outs[1][1]:
            continue
        owners = {N.OWNER.get((c[0] + a, c[1] + b)) for a, b in outs}
        score = (len(owners), len(outs), c)
        if best is None or score > best[0]:
            best = (score, c)
    for c in ([best[1]] if best else []):
        for y in range(pad + 1, top):
            st = G.get(c[0], y, c[1])
            if st and 'glass' not in st and 'door' not in st:
                G.set(c[0], y, c[1], trimb if (y - pad) % 2 else wallb)
        if free(c[0], top + 2, c[1]):
            G.set(c[0], top + 1, c[1], trimb)
            G.set(c[0], top + 2, c[1], LANTERN)
            count('corner lanterns')


def roofscape(lot, info):
    """Dormers on the street slopes of the pitched roofs, chimneys on the ridges."""
    style = info['style']
    top = LOT_TOP[lot['id']]
    wallb = B(info['wall'])
    if style in ('gable', 'dome') and len(lot['cells']) >= 30:
        for (c, d, n, lev) in street_front(lot):
            if (c[0] * 3 + c[1] * 5) % 7 != 0:
                continue
            t = (d[1], d[0])
            side = [(c[0] + t[0], c[1] + t[1]), (c[0] - t[0], c[1] - t[1])]
            if not all(N.LOT.get(q) == lot['id'] for q in side):
                continue
            if not all(free(q[0], top + 2, q[1]) for q in side + [c]):
                continue
            G.set(c[0], top + 2, c[1], B('glass'))
            for q in side:
                G.set(q[0], top + 2, q[1], wallb)
            G.set(c[0], top + 3, c[1], roof_at(info, c[0], c[1])[1])
            count('dormers')
    if style in ('gable', 'saw') and info['seed'] % 3 == 0 and len(lot['cells']) >= 24:
        dist = lot_dist(lot['cells'], exterior=True) if style == 'gable' else lot_dist(lot['cells'])
        peak = max(lot['cells'], key=lambda c: (dist[c], c))
        y = top + 1
        while G.filled(peak[0], y, peak[1]) and y < top + 12:
            y += 1
        for k in range(y, y + 3):
            G.set(peak[0], k, peak[1], B('bricks'))
        if DRESS[lot['district']]['smoke']:
            G.set(peak[0], y + 3, peak[1], B('campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]'))
            count('smoking chimneys')
        else:
            G.set(peak[0], y + 3, peak[1], B('brick_slab[type=bottom,waterlogged=false]'))
        count('chimneys')


# ---------------- interiors ----------------
ROOMS = [houses.room_kitchen, houses.room_living, houses.room_bedroom, houses.room_study]
GOODS = {'market': 'bakery', 'inns': 'records', 'gardens': 'nursery', 'travellers': 'maps', 'temple': 'textiles',
         'workshops': 'parts'}


def frame(lot, dr):
    """The largest room rectangle behind the door, in the door's frame (it may slide sideways off
    the door's axis): ((hw, D), s, t, centre) or (None, ...)."""
    (c, d, n) = dr
    s = (-d[0], -d[1])
    t = (-s[1], s[0])
    cells = set(lot['cells'])
    best = None
    for hw in (4, 3, 2):
        for o in (0, 1, -1, 2, -2, 3, -3):
            if abs(o) > hw + 1:
                continue
            c0 = (c[0] + o * t[0], c[1] + o * t[1])
            D = 0
            while D < 10 and all((c0[0] + u * t[0] + D * s[0], c0[1] + u * t[1] + D * s[1]) in cells for u in range(-hw, hw + 1)):
                D += 1
            if D >= 4 and (best is None or (2 * hw + 1) * min(D, 9) > best[0]):
                best = ((2 * hw + 1) * min(D, 9), (hw, min(D, 9)), c0)
        if best and best[1][0] == hw:
            break
    if not best:
        return None, s, t, c
    return best[1], s, t, best[2]


def interiors(markers):
    """houses.py rooms behind every door; a ladder up through the floors; lanterns so no room is
    dark. markers: lot id -> 'inn' for the side-quest inns."""
    for lot in N.LOTS:
        if not lot['cells']:
            continue
        info = LOT_INFO[lot['id']]
        dr = info.get('door')
        pad = lot['pad']
        floors = info['floors']
        dist = lot_dist(lot['cells'])
        ladder = None
        if floors >= 2:
            ladder = ladder_shaft(lot, info, dist)
        if dr:
            fr, s, t, c0 = frame(lot, dr)
            if fr:
                hw, D = fr
                pal = houses.PALETTES[info['seed'] % len(houses.PALETTES)]
                for k in range(floors):
                    P = houses.Plan()
                    if k == 0 and markers.get(lot['id']) == 'inn':
                        houses.room_tavern(P, hw, D, pal)
                        count('taverns')
                    elif k == 0 and shop_lot(lot):
                        houses.room_shop(P, hw, D, GOODS[lot['district']], pal)
                        count('shops furnished')
                    else:
                        fn = ROOMS[(k + info['seed']) % len(ROOMS)] if k else houses.room_kitchen
                        fn(P, hw, D, 0) if fn is houses.room_kitchen else fn(P, hw, D, 0, pal)
                        count('rooms furnished')
                    place_room(P, lot, c0, s, t, pad + STOREY * k, dist, ladder)
        if lot['district'] == 'workshops':
            forge(lot, info, dist)
        light(lot, info, dist, ladder)


def shop_lot(lot):
    kinds = set(lot['kinds'])
    return bool(kinds & {'axis', 'trees', 'crafts'}) or (lot['district'] in ('market', 'inns') and lot['prio'] >= 55)


def place_room(P, lot, c0, s, t, y0, dist, ladder):
    f = lambda v: (v[0] * t[0] + v[1] * s[0], v[0] * t[1] + v[1] * s[1])
    lid = lot['id']
    for (u, y, w), st in P.items():
        if w <= 0 or y < 1 or y > 4 or st.endswith(':air'):
            continue
        x, z = c0[0] + u * t[0] + w * s[0], c0[1] + u * t[1] + w * s[1]
        if N.LOT.get((x, z)) != lid or dist.get((x, z), 0) == 0 or (ladder and (x, z) == ladder[0]):
            continue
        yy = y0 + (3 if y == 4 else y)
        if y == 4 and 'chandelier' not in st and 'lantern' not in st:
            continue
        put(x, yy, z, orient(st, f))


def ladder_shaft(lot, info, dist):
    """A ladder against an inside wall, from the ground floor to the top storey, through holes in
    the floors: the cell next to a wall that is farthest from the door."""
    dr = info.get('door')
    pad = lot['pad']
    top = LOT_TOP[lot['id']]
    cands = []
    for c in lot['cells']:
        if dist[c] != 1:
            continue
        for a, b in N4:
            q = (c[0] + a, c[1] + b)
            if q in dist and dist[q] == 0 and all(G.get(q[0], y, q[1]) not in (None, B('glass')) for y in range(pad + 1, top)):
                far = math.dist(c, dr[0]) if dr else 0
                cands.append((far, c, (-a, -b)))
                break
    if not cands:
        return None
    _, c, v = max(cands)
    face = NAMEOF[v]
    for y in range(pad + 1, top):
        G.set(c[0], y, c[1], B('ladder[facing=%s,waterlogged=false]' % face))
    count('ladders')
    return (c, face)


def light(lot, info, dist, ladder):
    """Lanterns hung under every ceiling on a five-block grid, so no room is left dark."""
    pad = lot['pad']
    for k in range(info['floors']):
        y = pad + STOREY * k + 3
        for c in lot['cells']:
            if dist[c] == 0 or (c[0] % 5, c[1] % 5) != (2, 2):
                continue
            if ladder and c == ladder[0]:
                continue
            if free(c[0], y, c[1]) and G.filled(c[0], y + 1, c[1]):
                G.set(c[0], y, c[1], B('lantern[hanging=true,waterlogged=false]'))
                count('interior lanterns')
        if not any(G.get(c[0], y, c[1]) in (B('lantern[hanging=true,waterlogged=false]'), mp_state('mcwlights:copper_chandelier'))
                   for c in lot['cells']):
            inner = [c for c in lot['cells'] if dist[c] >= 1 and free(c[0], y, c[1]) and G.filled(c[0], y + 1, c[1])
                     and not (ladder and c == ladder[0])]
            if inner:
                c = max(inner, key=lambda q: (dist[q], q))
                G.set(c[0], y, c[1], B('lantern[hanging=true,waterlogged=false]'))
                count('interior lanterns')


def forge(lot, info, dist):
    """The Workshops' ground floors glow: a forge behind the street windows."""
    pad = lot['pad']
    n = 0
    for (c, d, nb, lev) in street_front(lot):
        q = (c[0] - d[0], c[1] - d[1])
        if dist.get(q) != 1 or not free(q[0], pad + 1, q[1]) or n >= 3:
            continue
        face = NAMEOF[d]
        G.set(q[0], pad + 1, q[1], B(['blast_furnace[facing=%s,lit=true]' % face, 'magma_block', 'smoker[facing=%s,lit=true]' % face][n % 3]))
        if n % 3 == 1:
            G.set(q[0], pad + 2, q[1], B('iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]') if free(q[0], pad + 2, q[1]) else G.get(q[0], pad + 2, q[1]))
        for y in (pad + 2, pad + 3):
            st = G.get(c[0], y, c[1])
            if st == B('glass'):
                G.set(c[0], y, c[1], B('orange_stained_glass'))
        n += 1
    if n:
        count('forges')


# ---------------- street life ----------------
def street_life():
    lane_lanterns()
    benches()
    planters()
    courtyards()
    signposts()


def lane_lanterns():
    """Lanes are too narrow for posts: a lantern on the wall every eight blocks, sides alternating."""
    for pi, p in enumerate(N.PATHS):
        if p.cls != 'lane' or not p.levels:
            continue
        for si, (x, z) in enumerate(p.pts):
            if si % 8 != 4:
                continue
            first = 1 if (si // 8) % 2 else -1
            if si + 1 < len(p.pts):
                tx, tz = p.pts[si + 1][0] - x, p.pts[si + 1][1] - z
            else:
                tx, tz = x - p.pts[si - 1][0], z - p.pts[si - 1][1]
            ln = math.hypot(tx, tz) or 1
            done = False
            for side in (first, -first):
                nx, nz = -tz * side / ln, tx * side / ln
                for off in (0.0, 0.5, 1.0, 1.5, 2.0, 2.5):
                    q = (round(x + nx * off), round(z + nz * off))
                    if N.OWNER.get(q) != pi:
                        continue
                    for a, b in N4:
                        w = (q[0] + a, q[1] + b)
                        if w not in N.LOT:
                            continue
                        y = TOP[q] + 4                  # on the floor band: never a window
                        v = (-a, -b)
                        if free(q[0], y, q[1]) and G.filled(w[0], y, w[1]) and 'glass' not in (G.get(w[0], y, w[1]) or ''):
                            G.set(q[0], y, q[1], mp_state('mcwlights:wall_lantern', facing=NAMEOF[v]))
                            count('lane lanterns')
                            done = True
                            break
                    if done:
                        break
                if done:
                    break


def benches():
    """Benches between the trees of the boulevards, round the plazas' rims, along the promenade."""
    for pi, p in enumerate(N.PATHS):
        if p.cls not in ('boulevard', 'promenade') or not p.levels:
            continue
        every, phase, off = (9, 2, 2.5) if p.cls == 'boulevard' else (12, 3, -3.0)
        for si, (x, z) in enumerate(p.pts):
            if si % every != phase:
                continue
            if si + 1 < len(p.pts):
                tx, tz = p.pts[si + 1][0] - x, p.pts[si + 1][1] - z
            else:
                tx, tz = x - p.pts[si - 1][0], z - p.pts[si - 1][1]
            ln = math.hypot(tx, tz) or 1
            nx, nz = -tz / ln, tx / ln
            for side in ((1, -1) if p.cls == 'boulevard' else (1,)):
                q = (round(x + nx * off * side), round(z + nz * off * side))
                if N.OWNER.get(q) != pi or not free(q[0], TOP[q] + 1, q[1]):
                    continue
                v = (-nx * side, -nz * side)
                face = NAMEOF[(round(v[0]), 0)] if abs(v[0]) >= abs(v[1]) else NAMEOF[(0, round(v[1]))]
                if p.cls == 'promenade':
                    face = OPP[face]
                G.set(q[0], TOP[q] + 1, q[1], mp_state('handcrafted:birch_bench', facing=face, shape='single'))
                count('benches')
    for (name, centre, cells, lev) in N.PLAZAS:
        cs = set(cells)
        rim = [c for c in cells if any((c[0] + a, c[1] + b) in N.LOT for a, b in N4) and TOP[c] == lev]
        rim.sort(key=lambda c: math.atan2(c[1] - centre[1], c[0] - centre[0]))
        for i, c in enumerate(rim):
            if i % 7 != 3 or not free(c[0], lev + 1, c[1]):
                continue
            v = (centre[0] - c[0], centre[1] - c[1])
            face = NAMEOF[(1 if v[0] > 0 else -1, 0)] if abs(v[0]) >= abs(v[1]) else NAMEOF[(0, 1 if v[1] > 0 else -1)]
            G.set(c[0], lev + 1, c[1], mp_state('handcrafted:birch_bench', facing=face, shape='single'))
            count('benches')


def planters():
    """Planters with flowers against the facades of the wide streets, every ten blocks."""
    flowers = ['flowering_azalea', 'azalea', 'allium', 'azure_bluet', 'cornflower', 'oxeye_daisy']
    for pi, p in enumerate(N.PATHS):
        if p.width < 7 or p.cls not in ('street', 'crafts', 'boulevard') or not p.levels:
            continue
        for si, (x, z) in enumerate(p.pts):
            if si % 10 != 7:
                continue
            if si + 1 < len(p.pts):
                tx, tz = p.pts[si + 1][0] - x, p.pts[si + 1][1] - z
            else:
                tx, tz = x - p.pts[si - 1][0], z - p.pts[si - 1][1]
            ln = math.hypot(tx, tz) or 1
            nx, nz = -tz / ln, tx / ln
            side = 1 if (si // 10) % 2 else -1
            for off in range(int(p.width / 2) + 1, 0, -1):
                q = (round(x + nx * off * side), round(z + nz * off * side))
                if N.OWNER.get(q) == pi:
                    w = (round(q[0] + nx * side), round(q[1] + nz * side))
                    if w in N.LOT and free(q[0], TOP[q] + 1, q[1]):
                        G.set(q[0], TOP[q] + 1, q[1], mp_state('supplementaries:planter'))
                        put(q[0], TOP[q] + 2, q[1], B(flowers[(si // 10) % len(flowers)]))
                        count('planters')
                    break


def courtyards():
    """Every courtyard, and every pocket garden big enough, gets a fountain or a well at its heart
    and benches round it."""
    seen = set()
    for c0, k in N.CELL.items():
        if k not in ('court', 'pocket') or c0 in seen:
            continue
        comp, q = [], deque([c0])
        seen.add(c0)
        while q:
            c = q.popleft()
            comp.append(c)
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n not in seen and N.CELL.get(n) == k:
                    seen.add(n)
                    q.append(n)
        cs = set(comp)
        dist, q = {}, deque()
        for c in comp:
            if any((c[0] + a, c[1] + b) not in cs for a, b in N4):
                dist[c] = 0
                q.append(c)
        while q:
            c = q.popleft()
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in cs and n not in dist:
                    dist[n] = dist[c] + 1
                    q.append(n)
        heart = max(comp, key=lambda c: (dist[c], -abs(c[0]), c))
        if dist[heart] < (2 if k == 'court' else 1) or len(comp) < (30 if k == 'court' else 16):
            continue
        if any(G.filled(heart[0] + a, TOP[heart] + 1, heart[1] + b) and 'leaves' not in (G.get(heart[0] + a, TOP[heart] + 1, heart[1] + b) or '')
               and 'log' not in (G.get(heart[0] + a, TOP[heart] + 1, heart[1] + b) or '') for a in (-1, 0, 1) for b in (-1, 0, 1)):
            continue
        y = TOP[heart]
        well = h(heart[0], heart[1], 5) < 0.4
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                x, z = heart[0] + a, heart[1] + b
                for yy in range(y + 1, y + 8):
                    if G.get(x, yy, z) and 'leaves' in G.get(x, yy, z):
                        G.clear(x, yy, z)
                if (a, b) == (0, 0):
                    G.set(x, y, z, B('water[level=0]'))
                    G.set(x, y - 1, z, B('sea_lantern'))
                    if not well:
                        for k in range(1, 3):
                            G.set(x, y + k, z, B('quartz_pillar[axis=y]'))
                        G.set(x, y + 3, z, B('gold_block'))
                else:
                    G.set(x, y + 1, z, B('stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]') if well
                          else cu_slab(patina(1, x, z, 13)))
                    G.clear(x, y + 2, z)
        if well:
            for (a, b) in ((1, 1), (-1, -1)):
                for k in (2, 3):
                    G.set(heart[0] + a, y + k, heart[1] + b, B('spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]'))
            for a in (-1, 0, 1):
                for b in (-1, 0, 1):
                    G.set(heart[0] + a, y + 4, heart[1] + b, B('spruce_planks' if (a, b) != (0, 0) else 'spruce_slab[type=bottom,waterlogged=false]'))
            G.set(heart[0], y + 3, heart[1], B('lantern[hanging=true,waterlogged=false]'))
        WATER_HOLD.append((heart[0], y, heart[1]))
        for (a, b), face in (((0, -3), 'south'), ((0, 3), 'north'), ((-3, 0), 'east'), ((3, 0), 'west')):
            x, z = heart[0] + a, heart[1] + b
            if (x, z) in cs and TOP[(x, z)] == y and free(x, y + 1, z):
                G.set(x, y + 1, z, mp_state('handcrafted:birch_bench', facing=face, shape='single'))
                count('benches')
        count('wells' if well else 'courtyard fountains')


WATER_HOLD = []


def signposts():
    """At the crossings of the main streets, a wooden post with a sign on each open face naming
    the nearest plaza straight ahead of whoever reads it."""
    main = [pi for pi, p in enumerate(N.PATHS) if p.levels and p.prio >= 55 and 'canal' not in p.kinds]
    places = [(PLACE_KEYS[name], centre) for (name, centre, cells, lev) in N.PLAZAS if name in PLACE_KEYS]
    posts = []
    for i, pi in enumerate(main):
        for pj in main[i + 1:]:
            A, Bp = N.PATHS[pi], N.PATHS[pj]
            for si, (x, z) in enumerate(A.pts):
                if si % 2:
                    continue
                c = (round(x), round(z))
                cov = N.COV.get(c, {})
                if pj in cov and cov[pj][0] <= 0.8 and pi in cov and cov[pi][0] <= 0.8:
                    if all(math.dist(c, p) > 25 for p in posts):
                        posts.append(c)
    made = 0
    for c in posts:
        spot = None
        for r in range(2, 9):
            ring = [(c[0] + a, c[1] + b) for a in range(-r, r + 1) for b in range(-r, r + 1) if max(abs(a), abs(b)) == r]
            for q in ring:
                if q in N.OWNER and walkable(q) and N.CELL.get(q) not in ('canal',) and any((q[0] + a, q[1] + b) in N.LOT for a, b in N4) \
                        and all(free(q[0], TOP[q] + k, q[1]) for k in range(1, 5)) and not (cls(q) == 'axis' and axis_part(q) != 'pave'):
                    spot = q
                    break
            if spot:
                break
        if not spot:
            continue
        y = TOP[spot]
        for k in range(1, 4):
            G.set(spot[0], y + k, spot[1], B('stripped_spruce_log[axis=y]'))
        G.set(spot[0], y + 4, spot[1], LANTERN)
        faces = 0
        for face, v in VEC.items():
            q = (spot[0] + v[0], spot[1] + v[1])
            if q in N.LOT or not free(q[0], y + 2, q[1]):
                continue
            look = (-v[0], -v[1])                     # the reader stands on this side, looking past the post
            best = None
            for key, (px, pz) in places:
                dx, dz = px - spot[0], pz - spot[1]
                dd = math.hypot(dx, dz)
                if dd < 12:
                    continue
                cosang = (dx * look[0] + dz * look[1]) / dd
                if cosang > 0.6 and (best is None or dd < best[0]):
                    best = (dd, key)
            if best:
                sign(q[0], y + 2, q[1], face, [('text', '↑'), ('place', best[1]), '', ''], wall=True, glow=True, wood='spruce')
                faces += 1
        if faces:
            made += 1
    count('signposts', made)
