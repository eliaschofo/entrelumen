"""Solsticio v8, the solarpunk pass (Elias, 26/9: "very colourful and beautiful, but it must be
SOLARPUNK: it lacks glass, stained glass and copper").

The warm city stays underneath (sandstone, calcite, terracotta); over it:
  - glass: glass-roofed arcades along the Axis wherever houses line it, a glass gallery vaulted over
    the whole Calle de los Oficios, glasshouses in the big courtyards, skywalks of glass and copper
    between houses across the streets, conservatories and solar arrays on the flat roofs, gardens
    in the attics under the glass roofs (city8 builds those roofs); clear glass and lightly stained
    glass, panes that join exactly as the game joins them;
  - stained glass: a stepped gable with a rose window of the sun (amber, gold, teal) over the
    middle of every long plaza front, sun vitrales in the four inns (the landmarks' own are in
    landmarks8);
  - copper at its four patinas: cornices, roofs and domes (city8), balconies and parapets of grates
    (dress8, city8), bulbs in the street lamps and set into the arcade beams, lightning rods as
    finials on the ridges and gables, pipes down the workshop facades into rain barrels (Create
    fluid pipes, joined as Create joins them), copper edges along the canals;
  - solar and green: daylight detectors as solar panels, Create windmills (static: sails round a
    bearing nobody has assembled), living facades of azalea and moss between the windows, vines on
    the garden pilasters, glow berries hanging from the glass, rooftop and attic gardens.

bind(city8, dress8) shares the grid and the helpers. finish() runs once everything is placed: it
gives every pane its connections and keeps redstone sources off bulbs, doors and trapdoors.
"""
import math
from collections import deque

NAMES = ('G', 'N', 'B', 'stairs', 'leaves', 'h', 'N4', 'TOP', 'LOT_INFO', 'LOT_TOP', 'STOREY', 'lot_dist', 'walkable',
         'cls', 'axis_part', 'cu', 'patina', 'cu_slab', 'bulb', 'cu_stage', 'rose', 'lancet', 'AMBER', 'GOLD', 'TEAL',
         'cells_of', 'max_rect')
VEC = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}
NAMEOF = {v: k for k, v in VEC.items()}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
ROD = 'lightning_rod[facing=up,powered=false,waterlogged=false]'
DETECTOR = 'daylight_detector[inverted=false,power=15]'          # eternal noon: full sun on the panels
PANE0 = '[east=false,north=false,south=false,waterlogged=false,west=false]'
STATS = {}
SITES = []               # (feature, x, z) of the pieces, for the review renders
NOPLACE = set()          # the player plots and a margin round them: nothing of this pass goes there


def bind(C, D):
    globals().update({k: getattr(C, k) for k in NAMES})
    globals().update(street_front=D.street_front, INNS=D.INNS, WATER_HOLD=D.WATER_HOLD)


def count(k, n=1):
    if n:
        STATS[k] = STATS.get(k, 0) + n


def free(x, y, z):
    return not G.filled(x, y, z)


def soft(x, y, z):
    """Empty, or something a new roof may take the place of: leaves, an awning."""
    st = G.get(x, y, z)
    return st is None or '_leaves' in st or 'awning' in st


def prune(x, y0, y1, z):
    """The street trees' leaves out of a column where a roof or a walk goes."""
    for y in range(y0, y1 + 1):
        st = G.get(x, y, z)
        if st and '_leaves' in st:
            G.clear(x, y, z)


def pane(x, y, z, colour=None):
    """A glass pane (finish() joins it as the game would)."""
    G.set(x, y, z, B(('%s_stained_glass_pane' % colour if colour else 'glass_pane') + PANE0))


def door(x, y, z, face, stage):
    d = cu('door', stage)
    G.set(x, y, z, B('%s[facing=%s,half=lower,hinge=left,open=false,powered=false]' % (d, face)))
    G.set(x, y + 1, z, B('%s[facing=%s,half=upper,hinge=left,open=false,powered=false]' % (d, face)))


def glow_vine(x, y, z, n):
    """Glow berries hanging n blocks under the full block over (x, y, z), grown out (age 25): lit
    and still."""
    if not G.filled(x, y + 1, z) or any(not free(x, y - k, z) for k in range(n)):
        return 0
    for k in range(n):
        G.set(x, y - k, z, B('cave_vines[age=25,berries=true]' if k == n - 1 else
                             'cave_vines_plant[berries=%s]' % ('true' if k % 2 else 'false')))
    count('glow berry vines')
    return 1


def plant(x, y, z, seed):
    """Something green on a moss bed: azaleas, ferns, flowers, moss."""
    r = h(x, z, seed)
    st = ('flowering_azalea' if r < 0.2 else 'azalea' if r < 0.34 else 'fern' if r < 0.48 else
          ['allium', 'blue_orchid', 'lily_of_the_valley', 'azure_bluet'][int(r * 97) % 4] if r < 0.62 else
          'moss_carpet' if r < 0.74 else None)
    if st:
        G.set(x, y, z, B(st))


def plots(corners, margin=2):
    NOPLACE.clear()
    for (x0, z0) in corners:
        for x in range(x0 - margin, x0 + 16 + margin):
            for z in range(z0 - margin, z0 + 16 + margin):
                NOPLACE.add((x, z))


def pick(lid, salt, n):
    """A choice among n for a lot, independent of the seed its style and walls were picked with."""
    return int(h(lid, salt, 555) * n)


def live_lots():
    return [l for l in N.LOTS if l['cells'] and not any(c in NOPLACE for c in l['cells'])]


# ---------------- stained glass ----------------
GABLE = {2: {0: 6, 1: 5, 2: 4}, 3: {0: 7, 1: 6, 2: 5, 3: 2}, 4: {0: 9, 1: 8, 2: 7, 3: 6, 4: 2}}
ROSE_OF = {2: 2, 3: 2, 4: 3}              # the rose window's radius in each gable


def plaza_gables():
    """A stepped gable over the plaza front of every pitched roof on a plaza, of the house's stone
    capped in copper, a lightning rod on its apex and a rose window of the sun in it. The plazas are
    round, so their fronts step: the gable stands on the line most of the front keeps."""
    made = 0
    for lot in live_lots():
        info = LOT_INFO[lot['id']]
        if info['style'] not in ('gable', 'glass', 'dome') or info['floors'] < 2:
            continue
        s = set(lot['cells'])
        fr = street_front(lot)
        best = None
        for d in N4:
            face = [c for (c, dd, n, lev) in fr if dd == d and n in N.PLAZA_OF]
            if len(face) < 5:
                continue
            t = (1, 0) if d[0] == 0 else (0, 1)
            proj = [c[0] * d[0] + c[1] * d[1] for c in face]
            line = max(set(proj), key=lambda p: (proj.count(p), p))
            row = sorted((c for c in s if c[0] * d[0] + c[1] * d[1] == line), key=lambda c: c[0] * t[0] + c[1] * t[1])
            run, runs = [], []
            for c in row:
                if run and (c[0] - run[-1][0], c[1] - run[-1][1]) != t:
                    runs.append(run)
                    run = []
                run.append(c)
            runs.append(run)
            fs = set(face)
            run = max(runs, key=lambda r: (sum(c in fs for c in r), len(r)))
            key = (sum(c in fs for c in run), len(run))
            if len(run) >= 5 and (best is None or key > best[0]):
                best = (key, d, t, run)
        if not best:
            continue
        _, d, t, run = best
        R = 4 if len(run) >= 11 else 3 if len(run) >= 7 else 2
        m = run[len(run) // 2]
        cols = [(m[0] + u * t[0], m[1] + u * t[1]) for u in range(-R, R + 1)]
        if not all(c in s for c in cols):
            continue
        top = LOT_TOP[lot['id']]
        wallb = B(info['wall'])
        st = cu_stage(info)
        prof = GABLE[R]
        for u, c in zip(range(-R, R + 1), cols):
            hh = prof[abs(u)]
            for k in range(1, hh + 1):
                G.set(c[0], top + k, c[1], wallb)
            G.set(c[0], top + hh + 1, c[1], cu_slab(patina(st, c[0], c[1], 23)) if u else B(cu('chiseled', st)))
        G.set(m[0], top + prof[0] + 2, m[1], B(ROD))
        rr = ROSE_OF[R]
        rose(m[0], top + rr + 1, m[1], t, rr, st, back=(-d[0], -d[1]))
        SITES.append(('gable', m[0], m[1], d, top))
        made += 1
    count('plaza gables with rose windows', made)


def inn_vitrales():
    """The four inns' upper windows in sun glass: teal below, amber and gold above."""
    for lid in INNS:
        lot = N.LOTS[lid]
        info = LOT_INFO[lid]
        pad = lot['pad']
        glass = B('glass')
        for (c, d, n, lev) in street_front(lot):
            along = c[1] if d[0] else c[0]
            for s in range(1, info['floors']):
                y0 = pad + STOREY * s
                if G.get(c[0], y0 + 2, c[1]) == glass and G.get(c[0], y0 + 3, c[1]) == glass:
                    G.set(c[0], y0 + 2, c[1], B(TEAL))
                    G.set(c[0], y0 + 3, c[1], B(AMBER if along % 3 == 1 else GOLD))
                    count('inn vitrales')


# ---------------- glass over the streets ----------------
def axis_arcades():
    """Glass-roofed arcades down both sides of the Axis wherever houses line it: over the pavement
    along the houses and the edge of the tree strip, a glass roof on copper beams at the height of
    the houses' second-floor band, over their awnings and first-floor windows and over the edge of
    the cherry trees, which grow on under it; slender quartz columns with copper capitals stand in
    the grass between the trees, a bulb is set in every other beam, glow berries hang between the
    columns. Nothing of the trees grows through the glass."""
    rows = {}
    for c in N.CELL:
        if abs(c[0]) != 9 or c in NOPLACE or cls(c) != 'axis':
            continue
        sgn = 1 if c[0] > 0 else -1
        w, o = (c[0] + sgn, c[1]), (c[0] - sgn, c[1])
        if w not in N.LOT or o not in N.CELL or cls(o) != 'axis':
            continue
        y = N.LOTS[N.LOT[w]]['pad'] + 2 * STOREY
        if y - TOP[c] >= 4 and y - TOP[o] >= 4:
            rows[c] = (o, y)
    for c, (o, y) in sorted(rows.items()):
        z = c[1]
        for q in (c, o):
            prune(q[0], y, y + 8, q[1])                              # no leaves through or over the glass
            if not soft(q[0], y, q[1]):
                continue                                           # a balcony floor closes the roof there
            if z % 4 == 0:
                if q == c and z % 8 == 0:
                    bulb(q[0], y, q[1], 0)
                    count('arcade bulbs')
                else:
                    G.set(q[0], y, q[1], B(cu('cut', patina(1, q[0], q[1], 31))))
            else:
                G.set(q[0], y, q[1], B(GOLD if q == o and z % 4 == 2 else 'glass'))
            count('arcade roof')
        if z % 4 == 0 and all(soft(o[0], yy, o[1]) for yy in range(TOP[o] + 1, y)):
            prune(o[0], TOP[o] + 1, y, o[1])
            for yy in range(TOP[o] + 1, y - 1):
                G.set(o[0], yy, o[1], B('quartz_pillar[axis=y]'))
            G.set(o[0], y - 1, o[1], B(cu('chiseled', 1)))
            count('arcade columns')
        elif z % 8 == 2:
            glow_vine(o[0], y - 1, o[1], 3)
    for c, (o, y) in rows.items():                                 # glass risers where the roof steps
        for dz in (-1, 1):
            n = (c[0], c[1] + dz)
            if n in rows and rows[n][1] < y:
                for q in (c, o):
                    for yy in range(max(rows[n][1] + 1, TOP[q] + 4), y):
                        if free(q[0], yy, q[1]):
                            G.set(q[0], yy, q[1], B('glass'))


def oficios_gallery():
    """The Calle de los Oficios under a glass gallery: a barrel vault from the houses' second-floor
    band, copper ribs every five blocks, a gold crown, grate columns where no house holds it up,
    glow berries hanging from the crown."""
    pi = next(i for i, p in enumerate(N.PATHS) if p.name == 'Calle de los Oficios')
    cells = sorted(c for c, o in N.OWNER.items() if o == pi and cls(c) == 'crafts' and c not in NOPLACE
                   and pi in N.COV.get(c, {}))
    cols = 0
    for c in cells:
        d, si = N.COV[c][pi]
        y = TOP[c] + 8 + (2 if d < 1.2 else 1 if d < 2.4 else 0)
        rib = si % 5 == 0
        prune(c[0], TOP[c] + 1, y, c[1])
        if soft(c[0], y, c[1]):
            G.set(c[0], y, c[1], B(cu('cut', patina(1 + (si // 5) % 2, c[0], c[1], 33))) if rib else
                  B(GOLD if d < 1.2 else 'glass'))
            count('gallery roof')
        if rib and d >= 2.4 and not any((c[0] + a, c[1] + b) in N.LOT for a, b in N4) \
                and all(free(c[0], yy, c[1]) for yy in range(TOP[c] + 1, y)):
            for yy in range(TOP[c] + 1, y):
                G.set(c[0], yy, c[1], B('%s[waterlogged=false]' % cu('grate', 1)))
            cols += 1
        if rib and d < 1.2 and si % 10 == 0:
            glow_vine(c[0], y - 1, c[1], 2)
    count('gallery columns', cols)


def clearable(st):
    """Facade trimmings a skywalk may take the place of: flower boxes, sills, open shutters, wall
    lanterns, banners, leaves."""
    if st is None:
        return True
    n = st.split('[')[0]
    return ('_leaves' in n or 'flower_box' in n or 'wall_lantern' in n or '_wall_banner' in n or
            n.endswith('_stairs') and 'half=top' in st and 'copper' not in n or
            n.endswith('_trapdoor') and 'open=true' in st and 'copper' not in n)


MOVABLE = ('_bed', '_door', 'ladder', 'chest', 'tall_', 'large_', 'sunflower', 'lilac', 'peony', 'rose_bush')


def skywalk_fit(A, c, d, span, Bl, qB):
    """The floor level of a skywalk from lot A at c across span to lot Bl at qB, or None: a floor
    both houses have, clear air round the walk (trimmings aside), a way in behind both doors."""
    t = (abs(d[1]), abs(d[0]))
    sA, sB = set(A['cells']), set(Bl['cells'])
    iA, iB = LOT_INFO[A['id']], LOT_INFO[Bl['id']]
    if not all((c[0] + e * t[0], c[1] + e * t[1]) in sA and (qB[0] + e * t[0], qB[1] + e * t[1]) in sB for e in (-1, 1)):
        return None
    cells = [(q[0] + e * t[0], q[1] + e * t[1]) for q in span for e in (-1, 0, 1)]
    if any(q in N.LOT or q not in TOP or q in NOPLACE for q in cells):
        return None
    ground = max(TOP[q] for q in cells)
    inA, inB = (c[0] - d[0], c[1] - d[1]), (qB[0] + d[0], qB[1] + d[1])
    if not all((p[0] + a, p[1] + b) in sp for p, sp in ((inA, sA), (inB, sB)) for a, b in N4):
        return None                                   # the way in is inside the house, not in a wall
    for sa in range(1, iA['floors']):
        F = A['pad'] + STOREY * sa
        sb, r = divmod(F - Bl['pad'], STOREY)
        if r or not 1 <= sb < iB['floors'] or F - ground < 6:
            continue
        if not all(clearable(G.get(q[0], y, q[1])) for q in cells for y in range(F - 1, F + 6)):
            continue
        if not all(G.filled(p[0], F, p[1]) for p in (inA, inB)):
            continue
        if any(any(k in (G.get(p[0], y, p[1]) or '') for k in MOVABLE) for p in (inA, inB) for y in (F + 1, F + 2)):
            continue
        if not all(G.filled(p[0], y, p[1]) and 'door' not in G.get(p[0], y, p[1]) for p in (c, qB) for y in (F + 1, F + 2)):
            continue
        return F, cu_stage(iA)
    return None


def skywalks(limit=12, spacing=26):
    """Skywalks across the streets between houses that share a floor level: a copper deck with
    glass panels in it, walls of panes, a copper roof with a lantern of light blue glass, a copper
    door into each house, glow berries hanging under the middle."""
    cands = []
    for A in live_lots():
        if LOT_INFO[A['id']]['floors'] < 3:
            continue
        for (c, d, n, lev) in street_front(A):
            if not walkable(n) or cls(n) not in ('street', 'lane', 'crafts'):
                continue
            span, k, bid, q = [], 1, None, None
            while k <= 13:
                q = (c[0] + d[0] * k, c[1] + d[1] * k)
                if q in N.LOT:
                    bid = N.LOT[q]
                    break
                if q not in TOP or q in NOPLACE or q in N.PLAZA_OF or not walkable(q):
                    break
                span.append(q)
                k += 1
            if bid is None or bid == A['id'] or not 3 <= len(span) <= 12 or any(x in NOPLACE for x in N.LOTS[bid]['cells']):
                continue
            fit = skywalk_fit(A, c, d, span, N.LOTS[bid], q)
            if fit:
                cands.append((-(A['prio'] // 10), abs(len(span) - 7), c, d, span, q, A['id'], bid))
    cands.sort(key=lambda t: t[:3])
    done, used = [], set()
    for (_, _, c, d, span, qB, a, b) in cands:
        mid = span[len(span) // 2]
        if a in used or b in used or any(math.dist(mid, m) < spacing for m in done):
            continue
        fit = skywalk_fit(N.LOTS[a], c, d, span, N.LOTS[b], qB)
        if not fit:
            continue
        build_skywalk(c, d, span, qB, *fit)
        SITES.append(('skywalk', mid[0], mid[1]))
        done.append(mid)
        used.update((a, b))
        if len(done) >= limit:
            break
    count('skywalks', len(done))
    return done


def build_skywalk(c, d, span, qB, F, st):
    t = (abs(d[1]), abs(d[0]))
    for p in ((c[0] - d[0], c[1] - d[1]), (qB[0] + d[0], qB[1] + d[1])):     # the way in behind each door
        for y in (F + 1, F + 2):
            G.clear(p[0], y, p[1])
    for j, q in enumerate(span):
        for e in (-1, 0, 1):
            p = (q[0] + e * t[0], q[1] + e * t[1])
            for y in range(F - 1, F + 6):
                G.clear(p[0], y, p[1])
            s = patina(st, p[0], p[1], 41)
            G.set(p[0], F, p[1], B('glass') if e == 0 and j % 2 else B(cu('cut', s)))
            if e:
                pane(p[0], F + 1, p[1])
                pane(p[0], F + 2, p[1])
                G.set(p[0], F + 3, p[1], stairs(cu('cut', s), NAMEOF[(-e * t[0], -e * t[1])]))
            else:
                G.set(p[0], F + 4, p[1], B('light_blue_stained_glass'))
    mid = span[len(span) // 2]
    for e in (-1, 1):
        glow_vine(mid[0] + e * t[0], F - 1, mid[1] + e * t[1], 2)
    door(c[0], F + 1, c[1], NAMEOF[d], st)
    door(qB[0], F + 1, qB[1], NAMEOF[(-d[0], -d[1])], st)


# ---------------- glasshouses, roofs ----------------
def glasshouse(x0, z0, W, D, lev, plinth, stage, toward=None, tint='glass'):
    """A glasshouse W x D on the ground (or roof) at lev: a calcite plinth when plinth, walls of
    panes between copper posts, a glass roof stepped up to its ridge on copper ribs, the gable ends
    glazed, a moss bed with a path down the middle, a copper door in the long side facing toward,
    glow berries hanging from the ridge."""
    x1, z1 = x0 + W - 1, z0 + D - 1
    long_x = W >= D
    half = ((D if long_x else W) - 1) // 2
    wtop = lev + 3
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            r = min(z - z0, z1 - z) if long_x else min(x - x0, x1 - x)
            u = x - x0 if long_x else z - z0
            if x in (x0, x1) or z in (z0, z1):
                along, Lw = (x - x0, W) if z in (z0, z1) else (z - z0, D)
                post = along in (0, Lw - 1) or (along % 3 == 0 if Lw >= 7 else along == Lw // 2)
                if plinth:
                    G.set(x, lev + 1, z, B('calcite'))
                for y in range(lev + (2 if plinth else 1), wtop + 1):
                    if post:
                        G.set(x, y, z, B(cu('cut', patina(stage, x, z, 51, y))))
                    else:
                        pane(x, y, z)
            else:
                G.set(x, lev, z, B('mud_bricks' if r == half else 'moss_block'))
                if r != half:
                    plant(x, lev + 1, z, 57)
            yr = wtop + 1 + r
            rib = u % 3 == 0 and 0 < u < (W if long_x else D) - 1        # transverse ribs; the rest glass
            G.set(x, yr, z, B(cu('cut', patina(stage, x, z, 53))) if rib else B(tint))
            if (x in (x0, x1)) if long_x else (z in (z0, z1)):
                for y in range(wtop + 1, yr):
                    G.set(x, y, z, B('glass'))
    tx, tz = toward if toward else ((x0 + x1) / 2, (z0 + z1) / 2 + 1)
    if long_x:
        zd = z0 if abs(z0 - tz) <= abs(z1 - tz) else z1
        dp, face, inner = (x0 + W // 2, zd), 'north' if zd == z0 else 'south', (x0 + W // 2, zd + (1 if zd == z0 else -1))
    else:
        xd = x0 if abs(x0 - tx) <= abs(x1 - tx) else x1
        dp, face, inner = (xd, z0 + D // 2), 'west' if xd == x0 else 'east', (xd + (1 if xd == x0 else -1), z0 + D // 2)
    door(dp[0], lev + 1, dp[1], face, stage)
    G.set(dp[0], lev + 3, dp[1], B(cu('cut', stage)))
    G.set(inner[0], lev, inner[1], B('mud_bricks'))
    G.clear(inner[0], lev + 1, inner[1])
    mid = (x0 + W // 2, z0 + half) if long_x else (x0 + half, z0 + D // 2)
    glow_vine(mid[0], wtop + half, mid[1], 2)
    SITES.append(('glasshouse' if plinth else 'conservatory', mid[0], mid[1]))


def court_glasshouses(limit=8):
    """A glasshouse in the courtyard and in the pocket gardens big enough, clear of the fountain or
    the well at their heart and its benches."""
    seen, comps = set(), []
    for c0 in sorted(N.CELL):
        k = N.CELL[c0]
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
        comps.append(comp)
    hearts = [(x, z) for (x, y, z) in WATER_HOLD]
    made = 0
    for comp in sorted(comps, key=lambda cs: (-len(cs), min(cs))):
        if len(comp) < 25 or made >= limit:
            continue
        s = set(comp)
        cx = sum(c[0] for c in comp) / len(comp)
        cz = sum(c[1] for c in comp) / len(comp)
        best = None
        for (W, D) in ((7, 5), (5, 7), (5, 5)):
            for (x0, z0) in comp:
                cells = [(x0 + i, z0 + j) for i in range(W) for j in range(D)]
                if not all(q in s and q not in NOPLACE for q in cells):
                    continue
                lev = TOP[(x0, z0)]
                if any(TOP[q] != lev for q in cells):
                    continue
                if any(max(abs(q[0] - hx), abs(q[1] - hz)) <= 3 for q in cells for (hx, hz) in hearts):
                    continue
                if any(not free(q[0], y, q[1]) for q in cells for y in range(lev + 1, lev + 9)):
                    continue
                outside = [(x0 + W // 2, z0 - 1), (x0 + W // 2, z0 + D), (x0 - 1, z0 + D // 2), (x0 + W, z0 + D // 2)]
                if not any(walkable(q) or q in s and q not in cells for q in outside):
                    continue
                score = (math.hypot(x0 + W / 2 - cx, z0 + D / 2 - cz), x0, z0)
                if best is None or score < best[0]:
                    best = (score, x0, z0, W, D, lev)
            if best:
                break
        if best:
            _, x0, z0, W, D, lev = best
            glasshouse(x0, z0, W, D, lev, True, 2, toward=door_side(x0, z0, W, D, s))
            made += 1
    count('garden glasshouses', made)


def door_side(x0, z0, W, D, s):
    """A point beyond the long side of a glasshouse that opens onto the garden or a street."""
    long_x = W >= D
    sides = [((x0 + W / 2, z0 - 5), (x0 + W // 2, z0 - 1)), ((x0 + W / 2, z0 + D + 5), (x0 + W // 2, z0 + D))] if long_x else \
        [((x0 - 5, z0 + D / 2), (x0 - 1, z0 + D // 2)), ((x0 + W + 5, z0 + D / 2), (x0 + W, z0 + D // 2))]
    for far, q in sides:
        if q in s and free(q[0], TOP[q] + 1, q[1]) or walkable(q):
            return far
    return sides[0][0]


def rect_in(cells, maxw=7, maxd=7):
    """The largest rectangle (3 x 3 at least, maxw x maxd at most) inside the cells: (x0, z0, x1, z1)."""
    s = set(cells)
    best = None
    for (x0, z0) in sorted(s):
        w = 0
        while w < maxw and (x0 + w, z0) in s:
            w += 1
        for ww in range(3, w + 1):
            d = 0
            while d < maxd and all((x0 + i, z0 + d) in s for i in range(ww)):
                d += 1
            if d >= 3 and (best is None or ww * d > best[0]):
                best = (ww * d, (x0, z0, x0 + ww - 1, z0 + d - 1))
    return best[1] if best else None


def flat_roofs():
    """The flat roofs: a conservatory (on half of them) and a solar array of daylight detectors in
    rows with walks between, or a roof garden of moss beds; the garden roofs get their azaleas."""
    for lot in live_lots():
        info = LOT_INFO[lot['id']]
        top = LOT_TOP[lot['id']]
        cs = lot['cells']
        dist = lot_dist(cs)
        inner = {c for c in cs if dist[c] >= 1}
        if info['style'] == 'garden':
            for (x, z) in sorted(inner):
                if G.get(x, top, z) in (B('moss_block'), B('grass_block')) and free(x, top + 1, z) and h(x, z, 63) < 0.1:
                    G.set(x, top + 1, z, B('flowering_azalea' if h(x, z, 64) < 0.5 else 'azalea'))
                    count('roof garden azaleas')
            continue
        if info['style'] != 'flat' or not inner:
            continue
        v = pick(lot['id'], 3, 4)
        xs, zs = [c[0] for c in cs], [c[1] for c in cs]
        along_x = max(xs) - min(xs) >= max(zs) - min(zs)
        taken = set()
        rect = rect_in(inner) if v in (0, 2) else None
        if rect:
            x0, z0, x1, z1 = rect
            w, d = x1 - x0 + 1, z1 - z0 + 1
            if w >= 3 and d >= 3:
                W, D = (min(w, 7), min(d, 5)) if w >= d else (min(w, 5), min(d, 7))
                if v == 2:                                             # at the other end of the roof
                    x0, z0 = x1 - W + 1, z1 - D + 1
                cx = sum(c[0] for c in cs) / len(cs)
                cz = sum(c[1] for c in cs) / len(cs)
                glasshouse(x0, z0, W, D, top, False, cu_stage(info), toward=(cx, cz),
                           tint='light_blue_stained_glass' if pick(lot['id'], 5, 3) == 0 else 'glass')
                taken = {(x, z) for x in range(x0 - 1, x0 + W + 1) for z in range(z0 - 1, z0 + D + 1)}
                count('rooftop conservatories')
        if v in (0, 1, 2):
            n = 0
            for (x, z) in sorted(inner - taken):
                if (z if along_x else x) % 3 == 0:
                    continue
                if free(x, top + 1, z) and G.filled(x, top, z) and not near(x, top + 1, z, SENSITIVE):
                    G.set(x, top + 1, z, B(DETECTOR))
                    n += 1
            count('solar panels', n)
            count('solar roofs', 1 if n else 0)
        else:
            for (x, z) in sorted(inner):
                if (x + 2 * z) % 5 in (0, 1) and free(x, top + 1, z):
                    G.set(x, top, z, B('moss_block'))
                    plant(x, top + 1, z, 65)
            count('roof gardens')


def glass_attics():
    """Under every glass roof the attic is a garden: moss on the ceiling boards, azaleas, ferns and
    flowers wherever the glass leaves room."""
    planks = B('spruce_planks')
    for lot in live_lots():
        info = LOT_INFO[lot['id']]
        if info['style'] != 'glass':
            continue
        top = LOT_TOP[lot['id']]
        n = 0
        for (x, z) in lot['cells']:
            if G.get(x, top, z) == planks and free(x, top + 1, z) and free(x, top + 2, z):
                G.set(x, top, z, B('moss_block'))
                plant(x, top + 1, z, 61)
                n += 1
        count('glass attic gardens', 1 if n else 0)


def finials():
    """Lightning rods on the ends of every other ridge."""
    rod = B(ROD)
    for lot in live_lots():
        info = LOT_INFO[lot['id']]
        if info['style'] not in ('gable', 'glass') or pick(lot['id'], 7, 2):
            continue
        cs = lot['cells']
        ext = lot_dist(cs, exterior=True)
        cap = 4 if max(ext.values()) >= 5 else 3
        ring = {c: min(ext[c], cap) for c in cs}
        rmax = max(ring.values())
        if not rmax:
            continue
        ridge = {c for c in cs if ring[c] == rmax}
        ends = sorted(c for c in ridge if sum((c[0] + a, c[1] + b) in ridge for a, b in N4) <= 1)
        y = LOT_TOP[lot['id']] + 2 + rmax
        for c in ends[:2]:
            if free(c[0], y, c[1]) and G.filled(c[0], y - 1, c[1]):
                G.set(c[0], y, c[1], rod)
                count('ridge finials')


# ---------------- copper on the walls ----------------
PIPE = 'create:fluid_pipe[down=%s,east=%s,north=%s,south=%s,up=%s,waterlogged=false,west=%s]'


def pipe(**on):
    return B(PIPE % tuple('true' if on.get(k) else 'false' for k in ('down', 'east', 'north', 'south', 'up', 'west')))


def downpipes(limit=30):
    """Copper downpipes where two workshops meet on the street: from an elbow into the cornice down
    to a rain barrel on the wider pavements, or into the paving. Create fluid pipes, joined as
    Create joins them (a straight run open at both ends, an elbow of two)."""
    made = 0
    for lot in sorted(live_lots(), key=lambda l: l['id']):
        if made >= limit:
            break
        if lot['district'] != 'workshops':
            continue
        info = LOT_INFO[lot['id']]
        top = LOT_TOP[lot['id']]
        dr = info.get('door')
        for (c, d, n, lev) in street_front(lot):
            t = (d[1], d[0])
            other = [N.LOT.get((c[0] + e * t[0], c[1] + e * t[1])) for e in (-1, 1)]
            other = [b for b in other if b is not None and b != lot['id']]
            if not other or min(other) < lot['id'] or (dr and n == dr[2]) or not walkable(n) or cls(n) == 'crafts':
                continue
            pw = N.PATHS[N.OWNER[n]].width if n in N.OWNER else 0
            y0 = TOP[n] + 1
            if pw < 4 or n in NOPLACE or any(not soft(n[0], y, n[1]) for y in range(y0, top + 1)):
                continue
            for y in range(y0, top + 1):
                G.clear(n[0], y, n[1])
            barrel = pw >= 7 and h(n[0], n[1], 91) < 0.6
            if barrel:
                G.set(n[0], y0, n[1], B('water_cauldron[level=3]'))
                count('rain barrels')
            for y in range(y0 + (1 if barrel else 0), top):
                G.set(n[0], y, n[1], pipe(down=True, up=True))
            G.set(n[0], top, n[1], pipe(down=True, **{NAMEOF[(-d[0], -d[1])]: True}))
            made += 1
            break
    count('downpipes', made)


def living_walls():
    """One house in four (not the workshops, not the inns) grows a living facade: azalea, flowering
    azalea and moss in the wall between the windows of its upper floors."""
    for lot in live_lots():
        info = LOT_INFO[lot['id']]
        if lot['district'] == 'workshops' or lot['id'] in INNS or pick(lot['id'], 11, 4):
            continue
        wallb = B(info['wall'])
        pad, top = lot['pad'], LOT_TOP[lot['id']]
        n = 0
        for (c, d, f, lev) in street_front(lot):
            if (c[1] if d[0] else c[0]) % 3:
                continue
            for y in range(pad + STOREY + 1, top):
                if (y - pad) % STOREY and G.get(c[0], y, c[1]) == wallb:
                    r = h(c[0] * 7 + y, c[1], 97)
                    G.set(c[0], y, c[1], leaves('flowering_azalea') if r < 0.45 else leaves('azalea') if r < 0.8 else B('moss_block'))
                    n += 1
        if n:
            count('living facades')
            count('living wall blocks', n)


def pilaster_vines():
    """Vines down the pilasters where two garden houses meet, from the cornice to a head's height
    over the pavement."""
    for lot in live_lots():
        if lot['district'] != 'gardens' or pick(lot['id'], 13, 3):
            continue
        top = LOT_TOP[lot['id']]
        for (c, d, n, lev) in street_front(lot):
            t = (d[1], d[0])
            if not any(N.LOT.get((c[0] + e * t[0], c[1] + e * t[1])) not in (None, lot['id']) for e in (-1, 1)):
                continue
            if not walkable(n) or n in NOPLACE or any(not free(n[0], y, n[1]) for y in range(lev + 3, top)):
                continue
            face = NAMEOF[(-d[0], -d[1])]                          # the vine hangs on the pilaster behind it
            st = B('vine[%s]' % ','.join('%s=%s' % (k, 'true' if k == face else 'false')
                                         for k in ('east', 'north', 'south', 'up', 'west')))
            for y in range(lev + 3, top):
                G.set(n[0], y, n[1], st)
            count('pilaster vines')


def canal_edges():
    """The canals' banks edged in copper at its later patinas."""
    n = 0
    for c, k in N.CELL.items():
        if k != 'canal' or c in N.CULVERT:
            continue
        for a, b in N4:
            q = (c[0] + a, c[1] + b)
            if q in N.CELL and N.CELL[q] != 'canal' and walkable(q) and q not in N.CULVERT and cls(q) != 'axis':
                G.set(q[0], TOP[q], q[1], B(cu('cut', patina(2, q[0], q[1], 71))))
                n += 1
    count('canal copper edges', n)


# ---------------- wind ----------------
def windmill(bx, by, bz, facing, A=4, sails=('white', 'yellow')):
    """A Create windmill as its builder left it: the bearing on the tower, a copper hub in front of
    it, four spars of sail frames with sails trailing them, a pinwheel across the bearing's facing.
    Static: nobody has assembled it."""
    f = VEC[facing]
    t = (abs(f[1]), abs(f[0]))
    hx, hz = bx + f[0], bz + f[1]
    cells = {}
    for i, (au, av) in enumerate(((0, 1), (1, 0), (0, -1), (-1, 0))):
        tu, tv = av, -au
        for k in range(1, A + 1):
            cells[(au * k, av * k)] = 'create:sail_frame[facing=%s]' % facing
            if k >= 2:
                cells[(au * k + tu, av * k + tv)] = 'create:%s_sail[facing=%s]' % (sails[i % len(sails)], facing)
    pos = {(hx + u * t[0], by + v, hz + u * t[1]): st for (u, v), st in cells.items()}
    if not free(bx, by, bz) or not free(hx, by, hz) or any(not free(*p) or p[::2] in NOPLACE for p in pos):
        return False
    G.set(bx, by, bz, B('create:windmill_bearing[facing=%s]' % facing))
    SITES.append(('windmill', bx, bz, by, facing))
    G.set(hx, by, hz, B(cu('chiseled', 1)))
    for p, st in pos.items():
        G.set(p[0], p[1], p[2], B(st))
    count('windmills')
    return True


def windmills():
    """Windmills: on one of the stacks of Terra's Workshop, and on turrets over two big workshops,
    their sails over the street."""
    x0, z0, x1, z1 = max_rect(cells_of('workshop'))
    L = N.PADS['workshop']
    done = False
    for (sx, sz) in ((x0 + 4, z0 + 3), (x1 - 4, z0 + 3)):
        for face in ('north', 'west', 'east', 'south'):
            f = VEC[face]
            if windmill(sx + 2 * f[0], L + 21, sz + 2 * f[1], face, 5, ('orange', 'white')):
                done = True
                break
        if done:
            break
    chosen = []
    lots = [l for l in live_lots() if l['district'] == 'workshops' and LOT_INFO[l['id']].get('door') and len(l['cells']) >= 40]
    for l in sorted(lots, key=lambda l: (-l['prio'], -len(l['cells']), l['id'])):
        if len(chosen) >= 2:
            break
        info = LOT_INFO[l['id']]
        (dc, d, f) = info['door']
        tc = (dc[0] - 2 * d[0], dc[1] - 2 * d[1])
        s = set(l['cells'])
        if not all((tc[0] + a, tc[1] + b) in s for a in (-1, 0, 1) for b in (-1, 0, 1)) or any(math.dist(tc, o) < 40 for o in chosen):
            continue
        top = LOT_TOP[l['id']]
        if not windmill(dc[0], top + 10, dc[1], NAMEOF[d], 4, ('white', 'yellow')):
            continue
        for y in range(top + 1, top + 13):
            k = y - top
            for a in (-1, 0, 1):
                for b in (-1, 0, 1):
                    x, z = tc[0] + a, tc[1] + b
                    if (a, b) == (0, 0):
                        G.set(x, y, z, B('bricks') if k == 1 else None)
                    else:
                        G.set(x, y, z, B(cu('cut', patina(1, x, z, 95, y)) if k % 4 == 0 else
                                         'glass' if (a == 0 or b == 0) and k in (2, 6) else 'bricks'))
        for (k, r) in ((13, 1), (14, 0)):
            for a in range(-r, r + 1):
                for b in range(-r, r + 1):
                    G.set(tc[0] + a, top + k, tc[1] + b, B(cu('cut', 2)))
        G.set(tc[0], top + 15, tc[1], B(ROD))
        chosen.append(tc)
    count('windmill turrets', len(chosen))


# ---------------- finish: panes and redstone ----------------
NOT_FULL = ('_stairs', '_slab', '_fence', '_door', '_trapdoor', '_sign', '_banner', '_carpet', 'lantern', '_rod', 'chain',
            'torch', '_button', '_pressure_plate', 'campfire', 'bell', 'ladder', 'cauldron', 'detector', 'flower_pot',
            'candle', 'lectern', 'grindstone', 'anvil', 'chest', '_bed', 'vine', 'azalea', 'fern', 'dripleaf', 'blossom',
            'roots', 'lichen', 'dripstone', 'water', 'rail', '_head', 'skull', 'decorated_pot', 'composter', 'hopper',
            'cake', 'pickle', 'scaffolding', 'snow', 'enchanting_table', 'stonecutter', 'conduit', 'short_grass',
            'tall_grass', 'allium', 'bluet', 'cornflower', 'daisy', 'lily_of_the_valley', 'orchid', 'tulip', 'poppy',
            'dandelion', 'rose_bush', 'lilac', 'peony', 'sunflower', 'sapling', 'mushroom', 'brewing_stand', 'bamboo')
FULL = {'sea_lantern', 'jack_o_lantern', 'grass_block', 'moss_block', 'bamboo_block', 'stripped_bamboo_block',
        'bamboo_planks', 'bamboo_mosaic', 'mushroom_stem', 'red_mushroom_block', 'brown_mushroom_block'}
NOT_JOINED = {'barrier', 'melon', 'pumpkin', 'carved_pumpkin', 'jack_o_lantern'}
SOURCE = ('daylight_detector', 'lightning_rod', 'redstone_block', 'redstone_torch', 'redstone_wall_torch', 'redstone_wire',
          'lever', '_button', '_pressure_plate', 'observer', 'target', 'trapped_chest', 'sculk_sensor', 'tripwire_hook',
          'comparator', 'repeater')
SENSITIVE = ('_door', '_trapdoor', 'copper_bulb', 'fence_gate', 'piston', 'dispenser', 'dropper', 'note_block',
             'redstone_lamp', 'tnt', 'hopper', 'rail', 'bell', 'crafter')


def props(st):
    if '[' not in st:
        return {}
    return dict(p.split('=') for p in st[:-1].split('[', 1)[1].split(','))


def pane_joins(st, face):
    """Whether a pane joins a neighbour that turns its face `face` to it (IronBarsBlock.attachsTo:
    another pane or bars, a wall, or a sturdy face of anything but the exceptions)."""
    if st is None:
        return False
    ns, n = st.split('[')[0].split(':')
    if n.endswith('_pane') or n == 'iron_bars' or n.endswith('_wall'):
        return True
    if ns != 'minecraft' or n in NOT_JOINED or n.endswith(('_leaves', 'shulker_box')):
        return False
    if n in FULL:
        return True
    if n.endswith('_stairs'):
        return props(st).get('facing') == face
    if n.endswith('_slab'):
        return props(st).get('type') == 'double'
    return not n.startswith('potted_') and not any(p in n for p in NOT_FULL)


def positions(ids):
    """Every cell of the grid holding one of the palette ids (a scan of the raw array)."""
    raw = G.data.tobytes()
    out = []
    for sid in ids:
        pat = sid.to_bytes(2, 'little')
        i = raw.find(pat)
        while i != -1:
            if i % 2:
                i = raw.find(pat, i + 1)
                continue
            k = i // 2
            rest, y = divmod(k, G.ny)
            x, z = divmod(rest, G.nz)
            out.append((x + G.x0, y + G.y0, z + G.z0))
            i = raw.find(pat, i + 2)
    return out


def ids_where(pred):
    return [i for i, st in enumerate(G.palette) if st and pred(st.split('[')[0].split(':')[1])]


def near(x, y, z, kinds):
    for (a, b, c) in N6:
        st = G.get(x + a, y + b, z + c)
        if st and any(k in st.split('[')[0] for k in kinds):
            return True
    return False


def finish():
    """Every pane joined as the game joins it (the city is placed without shape updates, so the
    template's states are what stands), then no redstone source on the face of a bulb, a door, a
    trapdoor or a bell: detectors there become copper slabs, rods come off."""
    n = 0
    for (x, y, z) in positions(ids_where(lambda n: n.endswith('_pane'))):
        st = G.get(x, y, z)
        con = {face: pane_joins(G.get(x + a, y, z + b), OPP[face]) for face, (a, b) in VEC.items()}
        G.set(x, y, z, '%s[east=%s,north=%s,south=%s,waterlogged=false,west=%s]' % (
            st.split('[')[0], *('true' if con[k] else 'false' for k in ('east', 'north', 'south', 'west'))))
        n += 1
    count('panes joined', n)
    fixed = 0
    for (x, y, z) in positions(ids_where(lambda n: any(k in n for k in SOURCE))):
        if near(x, y, z, SENSITIVE):
            st = G.get(x, y, z)
            G.set(x, y, z, cu_slab(1) if 'daylight_detector' in st else None)
            fixed += 1
    count('redstone sources moved off sensitive blocks', fixed)
    bad = [p for p in positions(ids_where(lambda n: any(k in n for k in SENSITIVE))) if near(*p, SOURCE)]
    census()
    return bad


CENSUS = {}


def census():
    """How much glass, stained glass, copper (by patina), sun and green the whole city has now."""
    CENSUS.clear()
    hist = G.histogram()

    def add(k, n):
        CENSUS[k] = CENSUS.get(k, 0) + n
    for st, n in hist.items():
        name = st.split('[')[0].split(':')[1]
        if name in ('glass', 'glass_pane'):
            add('clear glass', n)
        elif name == 'light_blue_stained_glass' or name == 'white_stained_glass':
            add('tinted glass', n)
        elif 'stained_glass' in name:
            add('stained glass', n)
        if 'copper' in name and 'lightning' not in name:
            stage = 3 if 'oxidized' in name else 2 if 'weathered' in name else 1 if 'exposed' in name else 0
            add('copper %s' % ('fresh', 'exposed', 'weathered', 'oxidized')[stage], n)
        for k in ('daylight_detector', 'copper_bulb', 'lightning_rod', 'fluid_pipe', '_sail', 'windmill_bearing',
                  'cave_vines', 'vine', 'flower_box', 'azalea'):
            if k in name:
                add(k.strip('_'), n)
    return CENSUS
