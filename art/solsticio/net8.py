"""Solsticio v8 levels: the urban plan (art/concepts/solsticio_plan.py) resolved into walkable ground.

The hill (plan.height) falls about one block every three, so nothing is left on the raw slope:

  - every street is rebuilt as a path (the same polylines and rings the plan stamped) and gets a
    designed profile: flat across its whole width, landings along it, flights of 1 to 4 steps where
    the hill demands (one rise per block, so every flight is a run of stair blocks);
  - paths are solved by rank (plazas, the Axis, the Edge Promenade, the ring boulevards, the Street
    of Crafts, the radials, the rings of streets, the lanes, the park paths): a lower path is pinned
    to the level of the one it meets, and the higher one keeps a landing across every crossing;
  - plazas and landmark platforms are flat; building lots take the level of the street they face,
    split where the street steps, so frontages step with the landings;
  - courtyards, pockets and the park are graded between what is fixed around them (never more than
    one block between neighbours in the park); the lake is one flat sheet of water.

Everything here is integer levels: LEVEL[(x, z)] is the y of the top ground block.
"""
import hashlib
import math
import os
import pickle
import sys
from collections import Counter, defaultdict, deque

HERE = os.path.dirname(os.path.abspath(__file__))
CONCEPTS = os.path.join(HERE, '..', 'concepts')
sys.path.insert(0, CONCEPTS)
import solsticio_plan as P  # noqa: E402

CACHE = os.environ.get('SOLSTICIO8_CACHE', 'E:/Elias/Codex/Entrelumen-ssd/solsticio8/cache'
                       if os.path.isdir('E:/Elias/Codex/Entrelumen-ssd') else os.path.join(HERE, '.cache8'))
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
N8 = N4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))

CELL, DISTRICT, LANDMARKS, H0 = {}, {}, [], {}
EDGE_T = []                  # island radius by angle, 3600 steps
LOG = []                     # deviations and decisions, for the report


def log(msg):
    LOG.append(msg)


# ---------------- the plan, cached ----------------
def load_plan():
    """Runs the plan once and caches CELL, DISTRICT, LANDMARKS, the hill and the shore radius."""
    src = b''.join(open(os.path.join(CONCEPTS, f), 'rb').read() for f in ('solsticio_plan.py', 'freevox.py'))
    key = hashlib.sha1(src).hexdigest()[:12]
    path = os.path.join(CACHE, 'plan-%s.pkl' % key)
    if os.path.exists(path):
        with open(path, 'rb') as f:
            data = pickle.load(f)
    else:
        P.plan()
        edge_t = [P.edge(math.cos(2 * math.pi * k / 3600), math.sin(2 * math.pi * k / 3600)) for k in range(3600)]
        h0 = {}
        for (x, z) in P.CELL:
            h0[(x, z)] = P.height(x, z)
        data = (dict(P.CELL), dict(P.DISTRICT), list(P.LANDMARKS), h0, edge_t)
        os.makedirs(CACHE, exist_ok=True)
        with open(path, 'wb') as f:
            pickle.dump(data, f)
    for d in (CELL, DISTRICT, H0):
        d.clear()
    LANDMARKS.clear()
    EDGE_T.clear()
    CELL.update(data[0])
    DISTRICT.update(data[1])
    LANDMARKS.extend(data[2])
    H0.update(data[3])
    EDGE_T.extend(data[4])
    P.CELL.clear()
    P.CELL.update(CELL)


def edge_r(x, z):
    """Island radius in the direction of (x, z) (tabulated plan.edge)."""
    a = (math.atan2(z, x) / (2 * math.pi)) % 1.0 * 3600
    i = int(a)
    t = a - i
    return EDGE_T[i % 3600] * (1 - t) + EDGE_T[(i + 1) % 3600] * t


def inside(x, z, margin=0.0):
    return math.hypot(x, z) < edge_r(x, z) - margin


def h0(x, z):
    v = H0.get((round(x), round(z)))
    return v if v is not None else P.height(x, z)


def landmark(name):
    for (es, en, x, z, kind) in LANDMARKS:
        if es.startswith(name):
            return x, z
    return None


# ---------------- plan fix-ups ----------------
def fix_plots():
    """The plan's four 16x16 player plots round the Travellers' Square; three of them are cut by the
    outer boulevard, and the quarter's blocks between the middle street and the boulevard are only
    about fourteen deep. So the plots go to the free squares nearest the Travellers' Square: whole
    squares of building or garden land, at least one side open on a street, Travellers' Quarter
    first; a plot never takes a street and is never shut in."""
    ok_kinds = {'plot', 'building', 'court', 'pocket', 'park'}
    walk = WALK | {'plaza'}
    px, pz = landmark('Plaza de los Viajeros')
    plan_sq = [(px + dx, pz + dz) for (dx, dz) in ((-28, -28), (12, -28), (-28, 12), (12, 12))]
    cands = []
    for xs in range(px - 100, px + 100):
        for zs in range(pz - 100, pz + 100):
            if CELL.get((xs, zs)) not in ok_kinds or CELL.get((xs + 15, zs + 15)) not in ok_kinds:
                continue
            if any(CELL.get((x, z)) not in ok_kinds for x in range(xs, xs + 16) for z in range(zs, zs + 16)):
                continue
            ring = [(xs - 1, z) for z in range(zs, zs + 16)] + [(xs + 16, z) for z in range(zs, zs + 16)] + \
                [(x, zs - 1) for x in range(xs, xs + 16)] + [(x, zs + 16) for x in range(xs, xs + 16)]
            if sum(CELL.get(c) in walk for c in ring) < 6:
                continue
            quarter = Counter(DISTRICT.get((x, z)) for x in range(xs, xs + 16, 5) for z in range(zs, zs + 16, 5)).most_common(1)[0][0]
            cands.append((quarter != 'travellers', math.hypot(xs + 8 - px, zs + 8 - pz), xs, zs))
    cands.sort()
    plots = []
    for (_, d, xs, zs) in cands:
        if all(abs(xs - p[0]) >= 18 or abs(zs - p[1]) >= 18 for p in plots):
            plots.append((xs, zs))
        if len(plots) == 4:
            break
    for (xs, zs) in plots:
        if (xs, zs) in plan_sq:
            continue
        near = min(plan_sq, key=lambda q: math.hypot(q[0] - xs, q[1] - zs))
        log('player plot at (%d, %d) (%s), %d blocks from the plan square at (%d, %d): the plan squares cross the outer boulevard' % (
            xs, zs, DISTRICT.get((xs + 8, zs + 8)), round(math.hypot(near[0] - xs, near[1] - zs)), near[0], near[1]))
    for c, k in list(CELL.items()):
        if k == 'plot':
            CELL[c] = 'building'
    for (xs, zs) in plots:
        for x in range(xs, xs + 16):
            for z in range(zs, zs + 16):
                CELL[(x, z)] = 'plot'
    return plots


# ---------------- paths ----------------
WALK = {'axis', 'trees', 'boulevard', 'street', 'lane', 'crafts', 'promenade', 'path'}


class Path:
    def __init__(self, name, kinds, prio, width, pts, closed=False, q=None, cls=None):
        self.name, self.kinds, self.prio, self.width = name, set(kinds), prio, width
        self.closed, self.q, self.cls = closed, q, cls or sorted(kinds)[0]
        self.pts = resample(pts, 1.0, closed)
        if not closed and len(self.pts) > 2:
            # reach 4 blocks into whatever the path starts from and ends on, so it gets pinned
            # to that level (the extra samples only own cells nobody else covers)
            (ax, az), (bx, bz) = self.pts[0], self.pts[1]
            head = [(ax - (bx - ax) * k, az - (bz - az) * k) for k in range(4, 0, -1)]
            (ax, az), (bx, bz) = self.pts[-1], self.pts[-2]
            tail = [(ax - (bx - ax) * k, az - (bz - az) * k) for k in range(1, 5)]
            self.pts = head + self.pts + tail
        self.levels = []


def resample(pts, step, closed):
    if closed:
        pts = list(pts) + [pts[0]]
    out = [pts[0]]
    acc = 0.0
    for (a, b) in zip(pts, pts[1:]):
        d = math.dist(a, b)
        if d == 0:
            continue
        t = step - acc
        while t <= d:
            out.append((a[0] + (b[0] - a[0]) * t / d, a[1] + (b[1] - a[1]) * t / d))
            t += step
        acc = d - (t - step)
    if closed and len(out) > 1 and math.dist(out[-1], out[0]) < step * 0.5:
        out.pop()
    return out


def ring_pts(cx, cz, r, wob, seed):
    return [P.ring_point(cx, cz, r, 2 * math.pi * i / 1400, wob, seed) for i in range(1400)]


def build_paths():
    paths = []
    add = lambda *a, **k: paths.append(Path(*a, **k))
    add('axis', ('axis', 'canal', 'trees'), 95, 20, P.polyline(P.MAYOR, P.PORTAL), q=4)
    prom = []
    for k in range(2400):
        th = 2 * math.pi * k / 2400
        r = edge_r(math.cos(th), math.sin(th)) - 4.5
        prom.append((r * math.cos(th), r * math.sin(th)))
    add('promenade', ('promenade',), 90, 9, prom, closed=True)
    add('inner boulevard', ('boulevard',), 80, 8, ring_pts(0, -12, 58, 0.12, 21), closed=True)
    add('outer boulevard', ('boulevard',), 78, 8, ring_pts(0, 2, 104, 0.10, 22), closed=True)
    th0, th1 = math.radians(30), math.radians(-12)
    crafts = [((58 + 46 * t) * math.cos(th0 + (th1 - th0) * t), -12 + 14 * t + (58 + 46 * t) * math.sin(th0 + (th1 - th0) * t))
              for t in [i / 300 for i in range(301)]]
    add('Calle de los Oficios', ('crafts',), 70, 7, crafts)
    for a in P.RADIALS:
        th = math.radians(a)
        start = 30 if a in (30, 150) else 62
        pts = P.polyline((math.cos(th) * start, -12 + math.sin(th) * start), (math.cos(th) * 170, math.sin(th) * 170))
        pts = [p for p in pts if inside(p[0], p[1], 6)]
        add('radial %d' % a, ('street',), 65, 7, pts)
    add('middle street', ('street',), 60, 4, ring_pts(0, -6, 82, 0.12, 23), closed=True)
    add('outer street', ('street',), 58, 4, ring_pts(0, 2, 128, 0.08, 24), closed=True)
    for name, (a0, a1) in P.DISTRICTS.items():
        for f in (1 / 3, 2 / 3):
            th = math.radians(a0 + (a1 - a0) * f)
            pts = P.polyline((math.cos(th) * 64, -12 + math.sin(th) * 64), (math.cos(th) * 170, math.sin(th) * 170))
            pts = [p for p in pts if inside(p[0], p[1], 6)]
            if len(pts) > 2:
                add('street %s %.0f' % (name, f * 3), ('street',), 55, 4, pts)
    for k in range(-50, 51, 17):
        add('lane x=%d' % k, ('lane',), 45, 3, P.polyline((k, -70), (k, 45)))
    for k in range(-50, 51, 17):
        add('lane z=%d' % (k - 12), ('lane',), 44, 3, P.polyline((-60, k - 12), (60, k - 12)))
    lake = (-98, 8)
    add('lake walk', ('path',), 30, 3, [(lake[0] + 28 * math.cos(math.radians(a)), lake[1] + 18 * math.sin(math.radians(a)))
                                        for a in range(0, 360, 3)], closed=True)
    add('park path', ('path',), 29, 3, P.polyline((-64, -30), (-140, 40)))
    # the canals of light: level water, stepping only at weirs; their own profile comes last
    along = lambda a0, a1, r_: [P.ring_point(0, 2, r_, math.radians(a0 + (a1 - a0) * i / int(abs(a1 - a0) * 8)), 0.10, 22)
                                for i in range(int(abs(a1 - a0) * 8) + 1)]
    east = along(90, 5, 97)
    ex, ez = east[-1]
    out = [p for p in P.polyline((ex, ez), (ex * 1.6, ez * 1.6)) if inside(p[0], p[1], 1)]
    add('east canal', ('canal',), 20, 4, east + out, q=1)
    west = along(90, 158, 97)
    west = west + P.polyline(west[-1], lake)
    add('west canal', ('canal',), 19, 3, west, q=1)
    return paths


# ---------------- coverage and ownership ----------------
COV = defaultdict(dict)        # cell -> {path index: (distance, sample index)}
OWNER = {}                     # network cell -> path index
PATHS = []


def cover(paths):
    COV.clear()
    for pi, p in enumerate(paths):
        r = p.width / 2 + 0.75
        ir = int(r) + 1
        for si, (x, z) in enumerate(p.pts):
            rx, rz = round(x), round(z)
            for dx in range(-ir, ir + 1):
                for dz in range(-ir, ir + 1):
                    c = (rx + dx, rz + dz)
                    if c not in CELL:
                        continue
                    d = math.hypot(c[0] - x, c[1] - z)
                    if d > r:
                        continue
                    old = COV[c].get(pi)
                    if old is None or d < old[0]:
                        COV[c][pi] = (d, si)


def within(c, pi):
    e = COV.get(c, {}).get(pi)
    return e is not None and e[0] <= PATHS[pi].width / 2 + 0.3


def own():
    """A walking cell belongs to the highest-ranked path whose width covers it, whatever the plan
    painted there (a boulevard crossing the Axis is Axis: the canal runs on under it); canal cells
    stay with their canal unless the Axis covers them."""
    OWNER.clear()
    for c, k in CELL.items():
        if k not in WALK and k != 'canal':
            continue
        cov = COV.get(c, {})
        if k == 'canal':
            close = [pi for pi in cov if within(c, pi) and 'canal' in PATHS[pi].kinds]
            cands = [pi for pi in cov if 'canal' in PATHS[pi].kinds]
        else:
            close = [pi for pi in cov if within(c, pi) and 'canal' not in PATHS[pi].kinds or
                     within(c, pi) and 'axis' in PATHS[pi].kinds]
            cands = [pi for pi in cov if k in PATHS[pi].kinds]
        if close:
            OWNER[c] = max(close, key=lambda pi: (PATHS[pi].prio, -COV[c][pi][0]))
        elif cands:
            OWNER[c] = min(cands, key=lambda pi: (COV[c][pi][0], -PATHS[pi].prio))
    # leftovers of the rasterisation take a neighbour's path
    todo = deque(c for c, k in CELL.items() if (k in WALK or k == 'canal') and c not in OWNER)
    guard = 0
    while todo and guard < 200000:
        guard += 1
        c = todo.popleft()
        ns = [OWNER[n] for n in ((c[0] + a, c[1] + b) for a, b in N4) if n in OWNER and CELL[c] in PATHS[OWNER[n]].kinds]
        if ns:
            OWNER[c] = max(ns, key=lambda pi: PATHS[pi].prio)
        else:
            todo.append(c)


# ---------------- profiles ----------------
def smooth(vals, w, closed):
    n = len(vals)
    out = []
    for i in range(n):
        acc, k = 0.0, 0
        for j in range(i - w, i + w + 1):
            if closed:
                acc += vals[j % n]
                k += 1
            elif 0 <= j < n:
                acc += vals[j]
                k += 1
        out.append(acc / k)
    return out


def envelope(vals, closed, lower=True):
    """Distance transform: lower=True gives min_k(v_k + |i-k|), else max_k(v_k - |i-k|)."""
    v = list(vals)
    n = len(v)
    rounds = 2 if closed else 1
    op = min if lower else max
    s = 1 if lower else -1
    for _ in range(rounds):
        for i in range(1, n) if not closed else range(n):
            v[i] = op(v[i], v[i - 1] + s)
        for i in range(n - 2, -1, -1) if not closed else range(n - 1, -1, -1):
            v[i] = op(v[i], v[(i + 1) % n] + s)
    return v


def pick_q(g, a, b, closed):
    n = len(g)
    idx = [(a + k) % n for k in range((b - a) % n + 1)] if closed else list(range(a, b + 1))
    if len(idx) < 3:
        return 1
    s = sum(abs(g[idx[k + 1]] - g[idx[k]]) for k in range(len(idx) - 1)) / (len(idx) - 1)
    return 1 if s < 0.1 else 2 if s < 0.22 else 3 if s < 0.34 else 4


def quantize_run(g, idx, La, Lb, q):
    """Levels for the samples idx (between pins La and Lb, either may be None)."""
    if not idx:
        return {}
    ga, gb = g[idx[0]], g[idx[-1]]
    m = len(idx)
    out = {}
    base = La if La is not None else (Lb if Lb is not None else round(ga))
    for k, i in enumerate(idx):
        t = k / max(1, m - 1)
        corr = 0.0
        if La is not None and Lb is not None:
            corr = (La - ga) * (1 - t) + (Lb - gb) * t
        elif La is not None:                       # a free end returns to the hill's own height
            corr = (La - ga) * max(0.0, 1 - k / 12)
        elif Lb is not None:
            corr = (Lb - gb) * max(0.0, 1 - (m - 1 - k) / 12)
        v = g[i] + corr
        out[i] = base + q * round((v - base) / q)
    return out


def centre_ramps(raw, closed):
    """Turn each jump of a terraced sequence into a flight of 1-block steps centred on the jump."""
    n = len(raw)
    L = list(raw)
    rng = range(n) if closed else range(n - 1)
    for j in rng:
        a, b = raw[j], raw[(j + 1) % n]
        J = b - a
        if abs(J) < 2:
            continue
        s = 1 if J > 0 else -1
        start = j - abs(J) // 2 + 1
        for t in range(abs(J) - 1):
            i = start + t
            if not closed and not (0 <= i < n):
                continue
            L[i % n] = a + s * (t + 1)
    return L


def profile(g, pins, closed, q=None):
    n = len(g)
    raw = [None] * n
    ps = sorted(pins)
    if not ps:
        qq = q or pick_q(g, 0, n - 1, closed)
        raw = [v for _, v in sorted(quantize_run(g, list(range(n)), None, None, qq).items())]
    else:
        for p in ps:
            raw[p] = pins[p]
        runs = []
        if closed:
            for k, p in enumerate(ps):
                nxt = ps[(k + 1) % len(ps)]
                length = (nxt - p) % n or n
                runs.append(([(p + t) % n for t in range(1, length)], pins[p], pins[nxt], p, nxt))
        else:
            runs.append((list(range(0, ps[0])), None, pins[ps[0]], 0, ps[0]))
            for p, nxt in zip(ps, ps[1:]):
                runs.append((list(range(p + 1, nxt)), pins[p], pins[nxt], p, nxt))
            runs.append((list(range(ps[-1] + 1, n)), pins[ps[-1]], None, ps[-1], n - 1))
        for idx, La, Lb, a, b in runs:
            if not idx:
                continue
            qq = q or pick_q(g, a, b, closed)
            for i, v in quantize_run(g, idx, La, Lb, qq).items():
                raw[i] = v
    L = centre_ramps(raw, closed)
    for p in ps:
        L[p] = pins[p]
    if ps:
        lo = envelope([pins.get(i, -10 ** 6) for i in range(n)], closed, lower=False)
        hi = envelope([pins.get(i, 10 ** 6) for i in range(n)], closed, lower=True)
        L = [min(max(L[i], lo[i]), hi[i]) for i in range(n)]
    L = envelope(L, closed, lower=True)         # any leftover jump of 2+ becomes a flight
    return L


LEVEL = {}                  # (x, z) -> top ground block y
UNIT = {}                   # (x, z) -> what decided the level: 'net', 'plaza', 'lot', ...
PLAZA_LEVEL = {}            # plaza component id -> level
PLAZA_OF = {}               # cell -> plaza component id
PLAZAS = []                 # (name, centre, cells, level)


def plazas():
    """Each plaza is one flat floor at the hill's height under its centre."""
    PLAZAS.clear()
    PLAZA_OF.clear()
    seen = set()
    named = [(es, (x, z)) for (es, en, x, z, kind) in LANDMARKS if kind in ('plaza', 'small')]
    comps = []
    for c, k in CELL.items():
        if k != 'plaza' or c in seen:
            continue
        comp, q = [], deque([c])
        seen.add(c)
        while q:
            p = q.popleft()
            comp.append(p)
            for a, b in N4:
                m = (p[0] + a, p[1] + b)
                if m not in seen and CELL.get(m) == 'plaza':
                    seen.add(m)
                    q.append(m)
        comps.append(comp)
    comps.sort(key=lambda cm: (-len(cm), min(cm)))
    for comp in comps:
        cs = set(comp)
        name, centre = next(((es, xz) for es, xz in named if xz in cs), (None, None))
        if name is None:            # the Clock Square round its tower, a canal-cut piece of a plaza
            mx = sum(p[0] for p in comp) / len(comp)
            mz = sum(p[1] for p in comp) / len(comp)
            es, xz = min(named, key=lambda t: math.hypot(t[1][0] - mx, t[1][1] - mz))
            if math.hypot(xz[0] - mx, xz[1] - mz) < 12 and xz not in [pl[1] for pl in PLAZAS]:
                name, centre = es, xz
            else:
                name = (es + ' (part)') if math.hypot(xz[0] - mx, xz[1] - mz) < 30 else 'plaza'
                centre = min(comp, key=lambda p: math.hypot(p[0] - mx, p[1] - mz))
        lev = round(h0(*centre))
        if name.endswith('(part)'):
            base = next((pl for pl in PLAZAS if pl[0] == name[:-7]), None)
            if base:
                lev = base[3]
        pid = len(PLAZAS)
        PLAZAS.append((name, centre, comp, lev))
        for p in comp:
            PLAZA_OF[p] = pid
            LEVEL[p] = lev
            UNIT[p] = 'plaza'


def crossings(pi, order):
    """Samples of path pi that lie inside a later (lower) walking path: landings there."""
    p = PATHS[pi]
    later = [pj for pj in order[order.index(pi) + 1:] if 'canal' not in PATHS[pj].kinds]
    groups = []
    n = len(p.pts)
    for pj in later:
        flags = [within((round(x), round(z)), pj) for (x, z) in p.pts]
        i = 0
        while i < n:
            if flags[i]:
                j = i
                while j + 1 < n and flags[j + 1]:
                    j += 1
                # a crossing, not a path running inside this one (the lane down the Axis)
                if j - i + 1 <= PATHS[pj].width * 1.6 + 3:
                    groups.append((i - 1, j + 1))
                i = j + 1
            else:
                i += 1
    return groups


def solve_paths():
    order = sorted(range(len(PATHS)), key=lambda pi: -PATHS[pi].prio)
    for pi in order:
        p = PATHS[pi]
        if 'canal' in p.kinds and 'axis' not in p.kinds:
            continue
        g = smooth([h0(x, z) for (x, z) in p.pts], 5, p.closed)
        n = len(g)
        pins = {}
        for si, (x, z) in enumerate(p.pts):
            c = (round(x), round(z))
            if c in LEVEL and OWNER.get(c) != pi:
                pins[si] = LEVEL[c]
            elif near_lake(c, 3):                          # a level causeway over the lake
                pins[si] = causeway()
        # a landing half the path's width past every junction, so an oblique meeting is flat
        buf = min(4, math.ceil(p.width / 2))
        for si, lev in list(pins.items()):
            for d in range(1, buf + 1):
                for sj in (si - d, si + d):
                    if p.closed:
                        sj %= n
                    if 0 <= sj < n and sj not in pins:
                        pins[sj] = lev
        L = profile(g, pins, p.closed, p.q)
        groups = crossings(pi, order)
        if 'axis' in p.kinds:
            p.levels = L = axis_profile(g, pins, groups, L)
            for c, o in OWNER.items():
                if o == pi:
                    LEVEL[c] = L[COV[c][pi][1]]
                    UNIT[c] = 'net'
            continue
        if groups:
            pins2 = dict(pins)
            for (a, b) in groups:
                span = [i % n for i in range(a, b + 1) if p.closed or 0 <= i < n]
                if any(i in pins for i in span):
                    continue
                lev = L[((a + b) // 2) % n]
                for i in span:
                    pins2.setdefault(i, lev)
            L = profile(g, pins2, p.closed, p.q)
        p.levels = L
        for c, o in OWNER.items():
            if o == pi:
                LEVEL[c] = L[COV[c][pi][1]]
                UNIT[c] = 'net'


AXIS_FLIGHT = 6          # the longest flight on the Axis
AXIS_LANDING = 3         # the shortest landing on the Axis


def axis_profile(g, pins, groups, first):
    """The Axis of the Sun as a designed stair: flat where the plazas hold it and across every street
    that crosses it (those landings take the level the first pass gave them), and between them
    flights of at most AXIS_FLIGHT steps separated by landings of at least AXIS_LANDING blocks,
    the spare length shared out evenly so the rhythm is regular."""
    n = len(g)
    fixed = dict(pins)
    for (a, b) in groups:
        span = [i for i in range(a, b + 1) if 0 <= i < n]
        if any(i in pins for i in span):
            continue
        lev = first[(a + b) // 2]
        for i in span:
            fixed[i] = lev
    zones = []                                   # runs of fixed samples: (start, end, level)
    i = 0
    while i < n:
        if i in fixed:
            j = i
            while j + 1 < n and j + 1 in fixed and fixed[j + 1] == fixed[i]:
                j += 1
            zones.append([i, j, fixed[i], i in pins])
            i = j + 1
        else:
            i += 1
    # the crossings' landings share the fall evenly between the two plazas: each takes the level
    # the free length before it calls for, so no stretch is steeper than the rest
    held = [z for z in zones if z[3]]
    if len(held) >= 2:
        first_z, last_z = held[0], held[-1]
        free_total = sum(z1[0] - z0[1] - 1 for z0, z1 in zip(zones, zones[1:])) or 1
        before = 0
        for z0, z1 in zip(zones, zones[1:]):
            before += z1[0] - z0[1] - 1
            if not z1[3] and first_z[0] <= z1[0] <= last_z[0]:
                z1[2] = round(first_z[2] + (last_z[2] - first_z[2]) * before / free_total)
    zones = [tuple(z[:3]) for z in zones]
    L = list(first)
    for (a, b, lev) in zones:
        for k in range(a, b + 1):
            L[k] = lev
    for (a0, b0, l0), (a1, b1, l1) in zip(zones, zones[1:]):
        free = list(range(b0 + 1, a1))
        m, d = len(free), l1 - l0
        if not free and d == 0:
            continue
        steps = abs(d)
        s = 1 if d > 0 else -1
        nf = max(1, math.ceil(steps / AXIS_FLIGHT)) if steps else 0
        spare = m - steps - AXIS_LANDING * max(0, nf - 1)
        if steps and spare < 0:                   # too steep for full landings: fewer, longer flights
            nf = max(1, math.ceil(steps / (AXIS_FLIGHT + 2)))
            spare = m - steps - AXIS_LANDING * max(0, nf - 1)
            log('Axis: a stretch of %d blocks falls %d; flights up to %d steps there' % (m, steps, AXIS_FLIGHT + 2))
        if steps and spare < 0:
            log('Axis: a stretch of %d blocks cannot fall %d with landings; graded' % (m, steps))
            continue
        sizes = [steps // nf + (1 if k < steps % nf else 0) for k in range(nf)]
        gaps = [spare // (nf + 1) + (1 if k < spare % (nf + 1) else 0) for k in range(nf + 1)] if nf else [m]
        for k in range(1, nf):                    # the landings between flights keep their minimum
            gaps[k] += AXIS_LANDING
        lev, pos = l0, 0
        seq = []
        for k in range(nf):
            seq += [lev] * gaps[k]
            for t in range(sizes[k]):
                lev += s
                seq.append(lev)
        seq += [lev] * gaps[nf] if nf else []
        seq = seq[:m] + [l1] * max(0, m - len(seq))
        for idx, v in zip(free, seq):
            L[idx] = v
    return L


_LAKE = set()


def near_lake(c, r):
    if not _LAKE:
        _LAKE.update(c for c, k in CELL.items() if k == 'lake')
    return any((c[0] + a, c[1] + b) in _LAKE for a in range(-r, r + 1) for b in range(-r, r + 1))


def causeway():
    """The outer boulevard crosses the lake on a level causeway, one block over the water."""
    near_lake((0, 0), 0)
    med = round(sorted(h0(*c) for c in _LAKE)[len(_LAKE) // 2])
    return med + 1


def lake_level():
    """The lake is one sheet of water at the median of the ground it covers; the outer boulevard
    crosses it on a level causeway one block higher."""
    near_lake((0, 0), 0)
    LAKE_LEVEL[0] = causeway() - 1
    log('lake level %d (ground under it %.0f..%.0f); the outer boulevard crosses it on a level causeway at %d' % (
        LAKE_LEVEL[0], min(h0(*c) for c in _LAKE), max(h0(*c) for c in _LAKE), causeway()))


def solve_canals():
    """Canal water follows its own gently smoothed profile, stepping at weirs; where a street, a
    plaza or the promenade crosses it the canal runs on under a glass deck at the street's level
    (CULVERT), never higher than that deck. The west canal ends in the lake at the lake's level.
    CANAL_LEVEL is the canal's bank level (its water one below); LEVEL of a culvert is the deck."""
    for pi, p in enumerate(PATHS):
        if 'canal' not in p.kinds or 'axis' in p.kinds:
            continue
        pins, caps = {}, {}
        for si, (x, z) in enumerate(p.pts):
            c = (round(x), round(z))
            w = walk_level(c, exclude=pi)
            if w is not None:
                caps[si] = w
            elif any(CELL.get((c[0] + a, c[1] + b)) == 'lake' for a in range(-2, 3) for b in range(-2, 3)):
                pins[si] = LAKE_LEVEL[0] + 1
        g = smooth([h0(x, z) for (x, z) in p.pts], 9, False)
        L = profile(g, pins, False, 1)
        L = [min(v, caps.get(i, v)) for i, v in enumerate(L)]
        p.levels = envelope(L, False, lower=True)
        for c, o in OWNER.items():
            if o == pi:
                w = walk_level(c, exclude=pi)
                CANAL_LEVEL[c] = p.levels[COV[c][pi][1]]
                if w is not None:
                    CANAL_LEVEL[c] = min(CANAL_LEVEL[c], w)
                    CULVERT.add(c)
                LEVEL[c] = CANAL_LEVEL[c] if w is None else w
                UNIT[c] = 'canal'


CANAL_LEVEL = {}
CULVERT = set()


def walk_level(c, exclude):
    """Level of the street, plaza or promenade that also covers cell c (a canal crossing)."""
    if c in PLAZA_OF:
        return PLAZA_LEVEL_OF(c)
    best = None
    for pj, (d, si) in COV.get(c, {}).items():
        if pj == exclude or 'canal' in PATHS[pj].kinds and 'axis' not in PATHS[pj].kinds:
            continue
        if d <= PATHS[pj].width / 2 + 0.3 and PATHS[pj].levels:
            if best is None or PATHS[pj].prio > best[0]:
                best = (PATHS[pj].prio, PATHS[pj].levels[si])
    return best[1] if best else None


def PLAZA_LEVEL_OF(c):
    return PLAZAS[PLAZA_OF[c]][3]


def fill_network_gaps():
    """Walking cells no path claimed (rasterisation crumbs) take their neighbours' level."""
    todo = [c for c, k in CELL.items() if (k in WALK or k == 'canal') and c not in LEVEL]
    for _ in range(20):
        rest = []
        for c in todo:
            ns = [LEVEL[n] for n in ((c[0] + a, c[1] + b) for a, b in N4) if n in LEVEL and UNIT.get(n) in ('net', 'plaza', 'canal')]
            if ns:
                LEVEL[c] = round(sum(ns) / len(ns))
                UNIT[c] = 'net'
            else:
                rest.append(c)
        todo = rest
        if not todo:
            break
    for c in todo:
        LEVEL[c] = round(h0(*c))
        UNIT[c] = 'net'


def walking():
    return {c for c, k in CELL.items() if k in WALK or k == 'plaza' or c in CULVERT}


def _transform(vals, graph, lower):
    """Graph distance transform over `graph` (cell -> neighbours) of `vals` (cell -> level):
    lower=True gives min_k(v_k + d(c, k)), else max_k(v_k - d(c, k)). Bucket Dijkstra."""
    out = dict(vals)
    buckets = defaultdict(list)
    for c, v in vals.items():
        buckets[v if lower else -v].append(c)
    keys = sorted(buckets)
    import heapq
    heap = list(keys)
    heapq.heapify(heap)
    done = set()
    while heap:
        k = heapq.heappop(heap)
        cs = buckets.pop(k, [])
        for c in cs:
            if c in done:
                continue
            if (out[c] if lower else -out[c]) != k:
                continue
            done.add(c)
            for n in graph[c]:
                nv = out[c] + 1 if lower else out[c] - 1
                if n not in out or (nv < out[n] if lower else nv > out[n]):
                    out[n] = nv
                    kk = nv if lower else -nv
                    if kk not in buckets:
                        heapq.heappush(heap, kk)
                    buckets[kk].append(n)
    return out


def relax(free, walk):
    """Grade the free cells so no two walking neighbours are more than one block apart while the
    fixed ones keep their level: clamp each free cell into the cone the fixed cells allow, then
    even out what is left symmetrically (landings stay centred on the old ones)."""
    free = set(free)
    graph = {}
    for c in walk:
        if c in LEVEL:
            graph[c] = [n for n in ((c[0] + a, c[1] + b) for a, b in N4) if n in walk and n in LEVEL]
    fixed = {c: LEVEL[c] for c in graph if c not in free}
    hi = _transform(fixed, graph, True)
    lo = _transform(fixed, graph, False)
    bad = 0
    for c in free:
        if c not in graph:
            continue
        a, b = lo.get(c, -10 ** 6), hi.get(c, 10 ** 6)
        if a > b:
            bad += 1
            continue
        LEVEL[c] = min(max(LEVEL[c], a), b)
    vals = {c: LEVEL[c] for c in graph}
    E = _transform(vals, graph, True)
    D = _transform(vals, graph, False)
    for c in free:
        if c in graph and lo.get(c, -10 ** 6) <= hi.get(c, 10 ** 6):
            LEVEL[c] = (E[c] + D[c]) // 2
    return bad


RIM = {}


def plaza_bands(walk):
    """A flat plaza on the slope meets the streets tangent to it with steps inside its rim: a rim
    band as wide as the difference is graded, the core stays flat."""
    band_all = set()
    for (name, centre, cells, lev) in PLAZAS:
        cs = set(cells)
        need = 0
        for c in cells:
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n not in cs and n in walk:
                    need = max(need, abs(LEVEL[n] - lev))
        need = max(need, RIM.get(name, 0))
        if need <= 1:
            continue
        RIM[name] = need
        dist, q = {}, deque()
        for c in cells:
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
        band_all.update(c for c in cells if dist[c] < min(need, max(1, int(max(dist.values()) * 0.6))))
    return band_all


def solve():
    load_plan()
    _LAKE.clear()
    LOG.clear()
    CANAL_LEVEL.clear()
    LEVEL.clear()
    UNIT.clear()
    CULVERT.clear()
    plots = fix_plots()
    PATHS[:] = build_paths()
    cover(PATHS)
    own()
    plazas()
    solve_paths()
    lake_level()
    solve_canals()
    fill_network_gaps()
    walk = walking()
    RIM.clear()
    low = {c for c in walk if c in OWNER and PATHS[OWNER[c]].prio <= 60}
    for _ in range(3):
        relax(low | plaza_bands(walk), walk)
    # last resort: anything still apart (a boulevard's curve, two fixed paths too close) is graded
    # locally, the Axis and the plaza cores excepted
    core = set()
    for (name, centre, cells, lev) in PLAZAS:
        core.update(c for c in cells if LEVEL[c] == lev)
    axis = {c for c in walk if c in OWNER and PATHS[OWNER[c]].name == 'axis'}
    rest = relax(walk - core - axis, walk)
    for name, need in RIM.items():
        log('%s: flat core, steps graded into its rim (up to %d) where the streets around sit lower or higher' % (name, need))
    if rest:
        log('%d walking cells between fixed levels that cannot be joined by steps' % rest)
    return plots


# ---------------- lots ----------------
LOT = {}          # building cell -> lot index
LOTS = []         # dicts: cells, front, pad, district, kinds, prio
FRONT = {}        # frontage building cell -> (rank, level, kind, neighbour)
FRONT_KINDS = WALK | {'plaza', 'canal'}


def rank(n):
    if CELL.get(n) == 'plaza':
        return 100
    if n in OWNER:
        return PATHS[OWNER[n]].prio
    return 0


def components(cells):
    comp, k = {}, 0
    for c in cells:
        if c in comp:
            continue
        q = deque([c])
        comp[c] = k
        while q:
            p = q.popleft()
            for a, b in N4:
                n = (p[0] + a, p[1] + b)
                if n in cells and n not in comp:
                    comp[n] = k
                    q.append(n)
        k += 1
    return comp


def lots():
    """Building land split into lots along the frontage: a lot takes at most nine frontage cells
    and only cells whose street stands within two blocks of its first one, so lots break where the
    street steps; the depth behind goes to the nearest frontage. Each lot is one flat pad at the
    level of the street it faces."""
    LOT.clear()
    LOTS.clear()
    FRONT.clear()
    B = {c for c, k in CELL.items() if k == 'building'}
    comp = components(B)
    for c in B:
        best = None
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if CELL.get(n) in FRONT_KINDS and n in LEVEL:
                cand = (rank(n), LEVEL[n], CELL[n], n)
                if best is None or cand[:2] > best[:2]:
                    best = cand
        if best:
            FRONT[c] = best
    acc = defaultdict(lambda: [0, 0, 0])
    for c in B:
        a = acc[comp[c]]
        a[0] += c[0]
        a[1] += c[1]
        a[2] += 1
    cent = {k: (v[0] / v[2], v[1] / v[2]) for k, v in acc.items()}
    order = sorted(FRONT, key=lambda c: (comp[c], math.atan2(c[1] - cent[comp[c]][1], c[0] - cent[comp[c]][0])))
    for s in order:
        if s in LOT:
            continue
        lid = len(LOTS)
        lev = FRONT[s][1]
        members = [s]
        LOT[s] = lid
        q = deque([s])
        while q and len(members) < 9:
            c = q.popleft()
            for a, b in N8:
                n = (c[0] + a, c[1] + b)
                if n in FRONT and n not in LOT and comp[n] == comp[s] and abs(FRONT[n][1] - lev) <= 2 \
                        and max(abs(n[0] - s[0]), abs(n[1] - s[1])) <= 8:
                    LOT[n] = lid
                    members.append(n)
                    q.append(n)
                    if len(members) >= 9:
                        break
        LOTS.append({'id': lid, 'front': members})
    q = deque(LOT)
    while q:
        c = q.popleft()
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n in B and n not in LOT:
                LOT[n] = LOT[c]
                q.append(n)
    chunks = {}
    for c in B:                                   # building land with no street at all
        if c not in LOT:
            key = (comp[c], c[0] // 9, c[1] // 9)
            if key not in chunks:
                chunks[key] = len(LOTS)
                LOTS.append({'id': chunks[key], 'front': []})
            LOT[c] = chunks[key]
    for lot in LOTS:
        lot['cells'] = []
    for c, lid in LOT.items():
        LOTS[lid]['cells'].append(c)
    # lots too small for a house join the neighbour they share most wall with
    for lot in sorted(LOTS, key=lambda l: len(l['cells'])):
        if not 0 < len(lot['cells']) < 16:
            continue
        share = Counter()
        for c in lot['cells']:
            for a, b in N4:
                n = (c[0] + a, c[1] + b)
                if n in LOT and LOT[n] != lot['id'] and comp.get(n) == comp.get(c):
                    share[LOT[n]] += 1
        if not share:
            continue
        other = LOTS[share.most_common(1)[0][0]]
        for c in lot['cells']:
            LOT[c] = other['id']
        other['cells'] += lot['cells']
        other['front'] += lot['front']
        lot['cells'], lot['front'] = [], []
    for lot in LOTS:
        cs = lot['cells']
        if not cs:
            continue
        fl = sorted(FRONT[c][1] for c in lot['front'] if c in FRONT)
        # the ground floor never sinks below the street it faces: at the low end of a flight
        # it stands a step or two up, on a plinth
        lot['pad'] = fl[-1] if fl else round(sorted(h0(*c) for c in cs)[len(cs) // 2])
        lot['district'] = Counter(DISTRICT.get(c, 'market') for c in cs).most_common(1)[0][0]
        lot['kinds'] = Counter(FRONT[c][2] for c in lot['front'] if c in FRONT)
        lot['prio'] = max([FRONT[c][0] for c in lot['front'] if c in FRONT] or [0])
        for c in cs:
            LEVEL[c] = lot['pad']
            UNIT[c] = 'lot'


# ---------------- green, water, landmarks ----------------
LAKE_LEVEL = [0]
PADS = {}         # landmark name -> floor level


def greens():
    """Courts sit at the middle of the pads round them; pockets at the streets round them."""
    for kind in ('court', 'pocket'):
        cells = {c for c, k in CELL.items() if k == kind}
        comp = components(cells)
        groups = defaultdict(list)
        for c, k in comp.items():
            groups[k].append(c)
        for k, cs in groups.items():
            around = []
            for c in cs:
                for a, b in N4:
                    n = (c[0] + a, c[1] + b)
                    if n in LEVEL and n not in cells and (UNIT.get(n) == 'lot' if kind == 'court' else True):
                        around.append(LEVEL[n])
            around.sort()
            lev = around[len(around) // 2] if around else round(sum(h0(*c) for c in cs) / len(cs))
            for c in cs:
                LEVEL[c] = lev
                UNIT[c] = kind


def landmarks():
    """Platforms of the landmarks: the palace on its acropolis, the market hall, the temple on its
    knoll, Terra's workshop, the palm house, the clock tower, the player plots."""
    mayor = PLAZAS[PLAZA_OF[P.MAYOR]][3]
    PADS['palace'] = mayor + 6
    for c, k in CELL.items():
        if k == 'palace':
            x, z = c
            y = PADS['palace']
            if abs(x) <= 12 and -31 <= z <= -26:          # the grand stair down to the Plaza Mayor
                y = PADS['palace'] - (z + 31)
            LEVEL[c] = y
            UNIT[c] = 'palace'
    def around(kind):
        vals = sorted(LEVEL[n] for c, k in CELL.items() if k == kind for n in ((c[0] + a, c[1] + b) for a, b in N4)
                      if n in LEVEL and CELL.get(n) in FRONT_KINDS)
        return vals
    m = around('market')
    PADS['market'] = PLAZAS[PLAZA_OF[landmark('Plaza del Mercado')]][3] - 5
    PADS['workshop'] = sorted(around('workshop'))[len(around('workshop')) // 2]
    tc = [c for c, k in CELL.items() if k == 'temple']
    PADS['temple'] = round(max(h0(*c) for c in tc)) + 1
    gc = [c for c, k in CELL.items() if k == 'greenhouse']
    PADS['greenhouse'] = round(sorted(h0(*c) for c in gc)[len(gc) // 2])
    tw = landmark('Plaza del Reloj')
    PADS['tower'] = PLAZAS[PLAZA_OF[(tw[0] + 5, tw[1])]][3] if (tw[0] + 5, tw[1]) in PLAZA_OF else round(h0(*tw))
    for c, k in CELL.items():
        if k in ('market', 'workshop', 'temple', 'greenhouse', 'tower'):
            LEVEL[c] = PADS[k]
            UNIT[c] = k
    log('market hall floor at %d (Plaza del Mercado %d; streets round it %d..%d)' % (
        PADS['market'], PADS['market'] + 5, m[0], m[-1]))


def plots(corners):
    for i, (x0, z0) in enumerate(corners):
        lev = round(h0(x0 + 8, z0 + 8))
        PADS['plot%d' % i] = lev
        for x in range(x0, x0 + 16):
            for z in range(z0, z0 + 16):
                LEVEL[(x, z)] = lev
                UNIT[(x, z)] = 'plot'


def park():
    """The Midday Park and the temple knoll: the hill's own ground, graded so no step is higher
    than one block, meeting the streets, the lake shore, the palm house and the temple platform.
    The lake is one sheet of water at the median of the ground it covers."""
    lake = [c for c, k in CELL.items() if k == 'lake']
    free = {c for c, k in CELL.items() if k in ('park', 'temple_ground')}
    for c in free:
        LEVEL[c] = round(h0(*c))
        UNIT[c] = CELL[c]
    for c in lake:
        LEVEL[c] = LAKE_LEVEL[0] + 1                    # the shore it needs, for the grading
        UNIT[c] = 'lake'
    graph = set(free) | set(lake)
    for c in list(free):
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n in LEVEL and (CELL.get(n) in WALK or CELL.get(n) in ('plaza', 'canal', 'greenhouse', 'temple')):
                graph.add(n)
    relax(free, graph)
    for c in lake:
        LEVEL[c] = LAKE_LEVEL[0]


def contain():
    """Water never sits against open air: every open canal and lake cell's neighbours are raised to
    at least its water surface (the canal's water is one below its walking level)."""
    raised = 0
    water = {}
    for c, k in CELL.items():
        if k == 'lake':
            water[c] = LAKE_LEVEL[0]
        elif k == 'canal' and c in CANAL_LEVEL:
            water[c] = CANAL_LEVEL[c] - 1
    for c, w in water.items():
        for a, b in N4:
            n = (c[0] + a, c[1] + b)
            if n in water or n not in LEVEL:
                continue
            if LEVEL[n] < w:
                if n in LOT:                            # a lot keeps one flat pad
                    lot = LOTS[LOT[n]]
                    lot['pad'] = max(lot['pad'], w)
                    for m in lot['cells']:
                        LEVEL[m] = lot['pad']
                else:
                    LEVEL[n] = w
                raised += 1
    if raised:
        log('%d cells raised to hold the water of the canals and the lake' % raised)
    return water


def solve_all():
    """Everything above, cached on disk by the source of this file and of the plan."""
    src = b''.join(open(os.path.join(d, f), 'rb').read() for d, f in
                   ((HERE, 'net8.py'), (CONCEPTS, 'solsticio_plan.py'), (CONCEPTS, 'freevox.py')))
    path = os.path.join(CACHE, 'levels-%s.pkl' % hashlib.sha1(src).hexdigest()[:12])
    state = ('CELL', 'DISTRICT', 'H0', 'LEVEL', 'UNIT', 'OWNER', 'PLAZA_OF', 'FRONT', 'LOT', 'PADS', 'COV', 'CANAL_LEVEL')
    lists = ('LANDMARKS', 'EDGE_T', 'LOG', 'PLAZAS', 'PATHS', 'LOTS', 'LAKE_LEVEL')
    g = globals()
    if os.path.exists(path):
        with open(path, 'rb') as f:
            data = pickle.load(f)
        for k in state:
            g[k].clear()
            g[k].update(data[k])
        for k in lists:
            g[k][:] = data[k]
        CULVERT.clear()
        CULVERT.update(data['CULVERT'])
        P.CELL.clear()
        P.CELL.update(CELL)
        return data['corners']
    corners = solve()
    lots()
    greens()
    landmarks()
    plots(corners)
    park()
    data = {k: dict(g[k]) for k in state}
    data.update({k: list(g[k]) for k in lists})
    data['CULVERT'] = set(CULVERT)
    data['corners'] = corners
    os.makedirs(CACHE, exist_ok=True)
    with open(path, 'wb') as f:
        pickle.dump(data, f)
    return corners


def step_report():
    """Pairs of neighbouring walking cells more than one block apart (not walkable)."""
    bad = []
    walk = {c for c, k in CELL.items() if k in WALK or k == 'plaza' or c in CULVERT}
    for c in walk:
        for a, b in ((1, 0), (0, 1)):
            n = (c[0] + a, c[1] + b)
            if n in walk and abs(LEVEL[c] - LEVEL[n]) >= 2:
                bad.append((c, n, LEVEL[c], LEVEL[n]))
    return bad


def debug_map(path, px=3):
    from PIL import Image, ImageDraw
    E = 172
    im = Image.new('RGB', ((2 * E + 1) * px, (2 * E + 1) * px), (28, 34, 51))
    d = ImageDraw.Draw(im)
    col = {'net': (240, 232, 214), 'plaza': (255, 214, 120), 'lot': (200, 190, 175), 'court': (150, 200, 120),
           'pocket': (150, 200, 120), 'park': (130, 190, 100), 'temple_ground': (170, 210, 140), 'canal': (90, 180, 230),
           'lake': (90, 180, 230), 'palace': (242, 193, 78), 'market': (243, 156, 90), 'plot': (255, 255, 255)}
    for (x, z), y in LEVEL.items():
        c = col.get(UNIT.get((x, z)), (180, 180, 180))
        sh = LEVEL.get((x - 1, z - 1), y)
        k = max(0.55, min(1.25, 1 + 0.12 * (y - sh)))
        c = tuple(min(255, int(v * k)) for v in c)
        X, Y = (x + E) * px, (z + E) * px
        d.rectangle([X, Y, X + px - 1, Y + px - 1], fill=c)
        for a, b in ((1, 0), (0, 1)):
            n = (x + a, z + b)
            if n in LEVEL and abs(LEVEL[n] - y) >= 2:
                d.rectangle([X, Y, X + px - 1, Y + px - 1], fill=(120, 60, 60))
    im.save(path)


if __name__ == '__main__':
    import time
    t0 = time.time()
    solve_all()
    contain()
    debug_map(os.path.join('E:/Elias/Codex/Entrelumen-ssd/solsticio8', 'levels_debug.png'))
    print('solved', round(time.time() - t0, 1), 's;', len(LEVEL), 'levels')
    for p in PATHS:
        if p.levels:
            print('%-26s %4d samples  %3d..%3d  steps %d' % (p.name, len(p.pts), min(p.levels), max(p.levels),
                                                            sum(1 for a, b in zip(p.levels, p.levels[1:]) if a != b)))
    bad = step_report()
    print(len(bad), 'walking pairs 2+ apart')
    for b in bad[:30]:
        print(' ', b, CELL[b[0]], CELL[b[1]], PATHS[OWNER[b[0]]].name if b[0] in OWNER else '-', PATHS[OWNER[b[1]]].name if b[1] in OWNER else '-')
    for (name, centre, cells, lev) in PLAZAS:
        print('plaza', name, centre, len(cells), lev)
    for m in LOG:
        print('LOG', m)
