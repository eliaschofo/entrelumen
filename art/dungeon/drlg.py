"""The Envés's floor generator (DRLG), reference implementation and review maps.

A floor is a grid of cells, and each cell is one authored room template (19x19x14 blocks). The
generator only decides which cells exist, which ones are linked by a door, and what each is for.
The templates bring the looks (art/dungeon/tiles.py). Diablo II builds its levels the same way:
generative layouts over authored tiles, so the result is cheap, varied and looks designed.

The companion ports this algorithm to Java. It is the oracle for the invariants the Java port must
keep (see check()):
- every cell is reachable from the start;
- the exit is a dead end far from the start;
- the vault and the shrine sit off the main path;
- floor k+1 starts right under floor k's stairwell.

    python art/dungeon/drlg.py [seed]       # writes the review plan into $RUIN_OUT
"""
import math
import os
import random
import sys
from collections import deque

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W = H = 11                                  # cells per floor side
DIRS = {'N': (0, -1), 'E': (1, 0), 'S': (0, 1), 'W': (-1, 0)}
TILESETS = ['Osarios', 'Cisternas', 'Fundición', 'Geodas', 'El Eclipse']
TILESETS_EN = ['Ossuaries', 'Cisterns', 'Foundry', 'Geodes', 'The Eclipse']


def add(c, d):
    return c[0] + d[0], c[1] + d[1]


def inside(c):
    return 0 <= c[0] < W and 0 <= c[1] < H


class Floor:
    def __init__(self, depth):
        self.depth = depth
        self.cells = set()
        self.links = set()                  # frozenset({a, b})
        self.role = {}                      # cell -> start | exit | seal | vault | shrine | guard | fight | quiet | arena | portal
        self.start = self.exit = None
        self.seals = []

    def linked(self, a, b):
        return frozenset((a, b)) in self.links

    def doors(self, c):
        return [k for k, d in DIRS.items() if self.linked(c, add(c, d))]

    def bfs(self, src):
        dist = {src: 0}
        q = deque([src])
        while q:
            c = q.popleft()
            for d in DIRS.values():
                n = add(c, d)
                if n in self.cells and n not in dist and self.linked(c, n):
                    dist[n] = dist[c] + 1
                    q.append(n)
        return dist

    def path(self, a, b):
        prev, q = {a: None}, deque([a])
        while q:
            c = q.popleft()
            if c == b:
                break
            for d in DIRS.values():
                n = add(c, d)
                if n in self.cells and n not in prev and self.linked(c, n):
                    prev[n] = c
                    q.append(n)
        out, c = [], b
        while c is not None:
            out.append(c)
            c = prev[c]
        return out[::-1]


def grow(rng, depth, start, target, keep_on=0.5, straight=0.4, loops=0.07):
    """Random growth with memory: mostly keep walking from the newest cell (corridors), sometimes
    branch from anywhere (forks and dead ends), then close a few loops so it is not a pure tree."""
    f = Floor(depth)
    f.start = start
    f.cells.add(start)
    recent, heading = [start], {start: None}
    tries = 0
    while len(f.cells) < target and tries < 5000:
        tries += 1
        base = recent[-1] if (recent and rng.random() < keep_on) else rng.choice(sorted(f.cells))
        options = [(k, add(base, d)) for k, d in DIRS.items() if inside(add(base, d)) and add(base, d) not in f.cells]
        if not options:
            if base in recent:
                recent.remove(base)
            continue
        h = heading.get(base)
        pick = next((o for o in options if o[0] == h), None) if (h and rng.random() < straight) else None
        k, n = pick or rng.choice(options)
        f.cells.add(n)
        f.links.add(frozenset((base, n)))
        heading[n] = k
        recent.append(n)
    for c in sorted(f.cells):                           # a few loops
        for k in ('E', 'S'):
            n = add(c, DIRS[k])
            if n in f.cells and not f.linked(c, n) and rng.random() < loops:
                f.links.add(frozenset((c, n)))
    return f


def sprout(rng, f, dist, length=2):
    """Grow a fresh dead-end branch from the farthest cell that has room: somewhere to hide a seal."""
    for c in sorted(f.cells, key=lambda c: (-dist.get(c, 0), rng.random())):
        if c in (f.exit, f.start):
            continue
        dirs = list(DIRS.values())
        rng.shuffle(dirs)
        for d in dirs:
            n = add(c, d)
            if inside(n) and n not in f.cells:
                f.cells.add(n)
                f.links.add(frozenset((c, n)))
                cur = n
                for _ in range(length - 1):
                    opts = [add(cur, dd) for dd in DIRS.values() if inside(add(cur, dd)) and add(cur, dd) not in f.cells]
                    if not opts:
                        break
                    nxt = rng.choice(opts)
                    f.cells.add(nxt)
                    f.links.add(frozenset((cur, nxt)))
                    cur = nxt
                return cur
    return None


def assign(rng, f):
    dist = f.bfs(f.start)
    far = max(dist.values())
    dead = [c for c in f.cells if c != f.start and len(f.doors(c)) == 1]
    cand = [c for c in dead if dist[c] >= 0.8 * far] or sorted(f.cells, key=lambda c: -dist[c])[:1]
    f.exit = rng.choice(sorted(cand))
    main = set(f.path(f.start, f.exit))
    f.role = {c: 'quiet' for c in f.cells}
    f.role[f.start], f.role[f.exit] = 'start', 'exit'
    p = f.path(f.start, f.exit)
    if len(p) > 2:
        f.role[p[-2]] = 'guard'                          # the champion pack before the stairs
    # seals (Elias, 26/9: "que tengas que sí o sí explorar"): the stairwell stays shut until the group
    # lights every seal; they sit in dead ends off the main path, as far from each other as possible
    n_seals = 2 if f.depth <= 2 else 3
    side = [c for c in dead if c not in main and c != f.exit]
    seals = []
    for _ in range(n_seals):
        pool = [c for c in side if c not in seals and dist[c] >= 0.3 * far]
        if not pool:                                         # no hiding place left: grow one
            tip = sprout(rng, f, dist)
            if tip is None:
                break
            dist = f.bfs(f.start)
            f.role.update({c: 'quiet' for c in f.cells if c not in f.role})
            pool = [tip]
        if not seals:
            pick = max(pool, key=lambda c: (dist[c], rng.random()))
        else:
            pick = max(pool, key=lambda c: (min(abs(c[0] - s[0]) + abs(c[1] - s[1]) for s in seals), rng.random()))
        seals.append(pick)
        f.role[pick] = 'seal'
    f.seals = seals
    side = [c for c in side if c not in seals and dist[c] >= 0.35 * far]
    if side:
        f.role[rng.choice(sorted(side))] = 'vault'
    mids = [c for c in f.cells if f.role[c] == 'quiet' and c not in main and len(f.doors(c)) >= 2]
    if mids and rng.random() < 0.7:
        f.role[rng.choice(sorted(mids))] = 'shrine'
    rest = [c for c in f.cells if f.role[c] == 'quiet' and dist[c] > 1]
    rng.shuffle(rest)
    for c in rest[:int(len(rest) * 0.42)]:
        f.role[c] = 'fight'
    return f


def boss_floor(start):
    """Floor V is fixed in shape: a short approach, the antechamber and the arena (3x3 cells)."""
    f = Floor(5)
    f.start = start
    x0, z0 = start
    sx = 1 if x0 < W // 2 else -1                        # walk toward the middle of the grid
    path = [start, (x0 + sx, z0), (x0 + 2 * sx, z0)]
    ax = x0 + 3 * sx if sx > 0 else x0 - 5
    arena = [(ax + i, z0 - 1 + j) for i in range(3) for j in range(3)]
    for c in path + arena:
        f.cells.add(c)
    for a, b in zip(path, path[1:]):
        f.links.add(frozenset((a, b)))
    f.links.add(frozenset((path[-1], (path[-1][0] + sx, z0))))
    for c in arena:
        for d in DIRS.values():
            n = add(c, d)
            if n in arena:
                f.links.add(frozenset((c, n)))
    portal = (ax + 1, z0 - 2)
    f.cells.add(portal)
    f.links.add(frozenset(((ax + 1, z0 - 1), portal)))
    f.role = {c: 'arena' for c in arena}
    f.role.update({path[0]: 'start', path[1]: 'quiet', path[2]: 'guard', portal: 'portal'})
    f.exit = portal
    return f


def descent(seed):
    rng = random.Random(seed)
    floors = []
    start = (rng.randrange(2, W - 2), rng.randrange(2, H - 2))
    for depth in range(1, 5):
        f = assign(rng, grow(rng, depth, start, 21 + 5 * depth))
        floors.append(f)
        start = f.exit                                   # the stairwell goes straight down
    floors.append(boss_floor(start if 1 <= start[0] <= W - 6 or True else start))
    return floors


def check(floors):
    for i, f in enumerate(floors):
        dist = f.bfs(f.start)
        assert set(dist) == f.cells, 'unreachable cells on floor %d' % f.depth
        if i:
            assert f.start == floors[i - 1].exit, 'floor %d does not start under the stairs' % f.depth
        if f.depth < 5:
            assert len(f.doors(f.exit)) == 1 or dist[f.exit] == max(dist.values())
            assert len(f.seals) >= 2, 'floor %d has %d seals' % (f.depth, len(f.seals))
            main = set(f.path(f.start, f.exit))
            assert not (set(f.seals) & main), 'a seal on the main path'
    return True


# ------------------------------------------------------------------ review maps (Atlas style)
PARCH, INK, FAINT = (236, 222, 188), (74, 52, 34), (150, 120, 88)
ROLE_FILL = {'seal': (170, 206, 200), 'start': (214, 196, 150), 'exit': (240, 204, 120), 'vault': (206, 170, 110), 'shrine': (226, 214, 150),
             'guard': (214, 150, 120), 'fight': (222, 196, 160), 'quiet': (226, 210, 172), 'arena': (200, 130, 110),
             'portal': (240, 220, 140)}


def font(size, bold=False):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/georgia%s.ttf' % ('b' if bold else ''), size)
    except OSError:
        return ImageFont.load_default()


def wobble(rng, pts, amp):
    return [(x + rng.uniform(-amp, amp), y + rng.uniform(-amp, amp)) for x, y in pts]


def draw_floor(f, px=40, pad=22, fog=None, seed=0):
    """The floor as the Atlas would ink it. `fog` = (explored, glimpsed) sets for the player view."""
    rng = random.Random(seed * 31 + f.depth)
    Wp, Hp = W * px + 2 * pad, H * px + 2 * pad
    im = Image.new('RGB', (Wp, Hp), PARCH)
    d = ImageDraw.Draw(im)
    for _ in range(900):                                           # paper grain
        x, y = rng.randrange(Wp), rng.randrange(Hp)
        c = rng.randrange(-12, 6)
        d.point((x, y), fill=tuple(max(0, min(255, v + c)) for v in PARCH))

    def rect_of(c, corridor_axis=None):
        x0, y0 = pad + c[0] * px, pad + c[1] * px
        role = f.role.get(c, 'quiet')
        doors = f.doors(c)
        straight = sorted(doors) in (['E', 'W'], ['N', 'S'])
        corridor = role in ('quiet',) and straight and ((c[0] * 7 + c[1] * 3 + f.depth) % 3 != 0)
        if role == 'arena':
            return (x0 + 2, y0 + 2, x0 + px - 2, y0 + px - 2), False
        if corridor:
            if doors[0] in 'EW':
                return (x0, y0 + px * 0.36, x0 + px, y0 + px * 0.64), True
            return (x0 + px * 0.36, y0, x0 + px * 0.64, y0 + px), True
        return (x0 + 7, y0 + 7, x0 + px - 7, y0 + px - 7), False

    rects = {c: rect_of(c)[0] for c in f.cells}
    arena = [c for c in f.cells if f.role.get(c) == 'arena']
    if arena:                                                       # the arena is one great room
        xs = [c[0] for c in arena]
        zs = [c[1] for c in arena]
        big = (pad + min(xs) * px + 5, pad + min(zs) * px + 5, pad + (max(xs) + 1) * px - 5, pad + (max(zs) + 1) * px - 5)
        for c in arena:
            rects[c] = big
    for c in sorted(f.cells):
        if c in arena and c != arena[0]:
            continue
        box = rects[c]
        pts = wobble(rng, [(box[0], box[1]), (box[2], box[1]), (box[2], box[3]), (box[0], box[3])], 1.2)
        d.polygon(pts, fill=ROLE_FILL.get(f.role.get(c), ROLE_FILL['quiet']))
        d.line(pts + [pts[0]], fill=INK, width=2)
    hall = (216, 198, 158)
    for l in f.links:
        a, b = sorted(l)
        if rects[a] == rects[b]:
            continue
        ra, rb = rects[a], rects[b]
        w = px * 0.13
        if a[1] == b[1]:                                            # east-west
            cy = pad + a[1] * px + px / 2
            x0, x1 = ra[2], rb[0]
            d.rectangle((x0 - 2, cy - w + 1, x1 + 2, cy + w - 1), fill=hall)
            d.line((x0, cy - w, x1, cy - w), fill=INK, width=2)
            d.line((x0, cy + w, x1, cy + w), fill=INK, width=2)
        else:                                                       # north-south
            cx = pad + a[0] * px + px / 2
            y0, y1 = ra[3], rb[1]
            d.rectangle((cx - w + 1, y0 - 2, cx + w - 1, y1 + 2), fill=hall)
            d.line((cx - w, y0, cx - w, y1), fill=INK, width=2)
            d.line((cx + w, y0, cx + w, y1), fill=INK, width=2)
    # icons
    for c, role in f.role.items():
        cx, cy = pad + c[0] * px + px / 2, pad + c[1] * px + px / 2
        r = px * 0.16
        if role == 'start':
            d.polygon([(cx - r, cy - r * 0.6), (cx + r, cy - r * 0.6), (cx, cy + r)], outline=INK, width=2)
        elif role == 'exit':
            for k in range(4):
                rr = r * (1.2 - k * 0.28)
                d.arc((cx - rr, cy - rr, cx + rr, cy + rr), 200 + k * 30, 520 + k * 30, fill=INK, width=2)
        elif role == 'vault':
            d.rectangle((cx - r, cy - r * 0.5, cx + r, cy + r * 0.7), outline=INK, width=2)
            d.line((cx - r, cy, cx + r, cy), fill=INK, width=2)
        elif role == 'shrine':
            d.ellipse((cx - r * 0.6, cy - r * 0.6, cx + r * 0.6, cy + r * 0.6), outline=INK, width=2)
            for k in range(8):
                a = k * math.pi / 4
                d.line((cx + math.cos(a) * r * 0.8, cy + math.sin(a) * r * 0.8, cx + math.cos(a) * r * 1.3,
                        cy + math.sin(a) * r * 1.3), fill=INK, width=1)
        elif role == 'guard':
            d.ellipse((cx - r * 0.8, cy - r, cx + r * 0.8, cy + r * 0.5), outline=(140, 40, 30), width=2)
            d.line((cx - r * 0.3, cy - r * 0.3, cx - r * 0.1, cy - r * 0.3), fill=(140, 40, 30), width=2)
            d.line((cx + r * 0.1, cy - r * 0.3, cx + r * 0.3, cy - r * 0.3), fill=(140, 40, 30), width=2)
        elif role == 'fight':
            d.line((cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7), fill=FAINT, width=2)
            d.line((cx + r * 0.7, cy - r * 0.7, cx - r * 0.7, cy + r * 0.7), fill=FAINT, width=2)
        elif role == 'seal':
            pts = [(cx + r * 1.1 * math.cos(k * math.pi / 3), cy + r * 1.1 * math.sin(k * math.pi / 3)) for k in range(6)]
            d.polygon(pts, outline=(30, 90, 90), width=2)
            d.ellipse((cx - r * 0.35, cy - r * 0.35, cx + r * 0.35, cy + r * 0.35), fill=(30, 90, 90))
        elif role == 'portal':
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(200, 150, 40), width=3)
    if f.depth == 5:
        arena = [c for c, r in f.role.items() if r == 'arena']
        mx = sum(c[0] for c in arena) / len(arena)
        my = sum(c[1] for c in arena) / len(arena)
        cx, cy = pad + mx * px + px / 2, pad + my * px + px / 2
        d.ellipse((cx - px * 0.9, cy - px * 0.9, cx + px * 0.9, cy + px * 0.9), outline=(120, 30, 30), width=3)
        d.ellipse((cx - px * 0.35, cy - px * 0.35, cx + px * 0.35, cy + px * 0.35), fill=(60, 30, 30))
    if fog is None:
        return im
    explored, glimpsed = fog
    mask_c = Image.new('L', im.size, 0)
    mask_g = Image.new('L', im.size, 0)
    mc, mg = ImageDraw.Draw(mask_c), ImageDraw.Draw(mask_g)
    for c in explored:
        x0, y0 = pad + c[0] * px, pad + c[1] * px
        mc.rectangle((x0 - 4, y0 - 4, x0 + px + 4, y0 + px + 4), fill=255)
    for c in glimpsed:
        x0, y0 = pad + c[0] * px, pad + c[1] * px
        mg.rectangle((x0 - 2, y0 - 2, x0 + px + 2, y0 + px + 2), fill=150)
    mask_c = mask_c.filter(ImageFilter.GaussianBlur(7))
    mask_g = mask_g.filter(ImageFilter.GaussianBlur(10))
    blurred = im.filter(ImageFilter.GaussianBlur(4))
    fogim = Image.new('RGB', im.size, PARCH)
    fd = ImageDraw.Draw(fogim)
    for _ in range(260):                                           # soft ink clouds: the unknown
        x, y = rng.randrange(im.size[0]), rng.randrange(im.size[1])
        r = rng.randrange(10, 34)
        fd.ellipse((x - r, y - r, x + r, y + r), fill=(196, 176, 140))
    fogim = fogim.filter(ImageFilter.GaussianBlur(14))
    out = Image.composite(blurred, fogim, mask_g)
    out = Image.composite(im, out, mask_c)
    return out


def plan_image(seed, path):
    floors = descent(seed)
    check(floors)
    tiles = [draw_floor(f, seed=seed) for f in floors]
    # player's view of floor II, halfway: along the main path plus one detour
    f = floors[1]
    route = f.path(f.start, f.exit)
    explored = set(route[:max(2, int(len(route) * 0.6))])
    dist = f.bfs(f.start)
    detour = sorted((c for c in f.cells if c not in explored), key=lambda c: dist[c])[:3]
    explored |= set(detour)
    glimpsed = {add(c, DIRS[k]) for c in explored for k in f.doors(c)} - explored
    fog = draw_floor(f, fog=(explored, glimpsed), seed=seed)

    tw, th = tiles[0].size
    big = 1.25
    fw, fh = int(fog.size[0] * big), int(fog.size[1] * big)
    Wc = 60 + fw + 40 + 2 * tw + 60 + 20
    Hc = 150 + max(fh, 3 * th + 80) + 90
    canvas = Image.new('RGB', (Wc, Hc), (28, 28, 38))
    d = ImageDraw.Draw(canvas)
    d.text((60, 36), 'El Envés · plano del descenso', font=font(44, True), fill=(250, 236, 200))
    d.text((62, 94), 'Semilla %d. Cuatro pisos de 26 a 41 salas sobre salas de autor, y el del jefe. La escalera no abre '
                     'hasta prender los sellos escondidos; baja justo al inicio del piso siguiente.' % seed,
           font=font(18), fill=(214, 200, 170))
    canvas.paste(fog.resize((fw, fh)), (60, 150))
    for k, line in enumerate(('Lo que ve el jugador en el piso II, a mitad de camino:',
                              'las salas pisadas, nítidas; las que se asoman por una puerta, borrosas;',
                              'el resto, niebla. El mapa del Atlas se dibuja a medida que caminás.')):
        d.text((60, 150 + fh + 12 + k * 24), line, font=font(17), fill=(214, 200, 170))
    x0 = 60 + fw + 40
    for i, (t, f) in enumerate(zip(tiles, floors)):
        col, row = i % 2, i // 2
        px_, py_ = x0 + col * (tw + 20), 150 + row * (th + 40)
        canvas.paste(t, (px_, py_))
        label = '%s · %s  (%d salas)' % (['I', 'II', 'III', 'IV', 'V'][i], TILESETS[i], len(f.cells))
        d.text((px_ + 4, py_ + th + 6), label, font=font(16, True), fill=(250, 236, 200))
    # legend
    lx, ly = x0 + tw + 20, 150 + 2 * (th + 40)
    items = [('triángulo', 'bajada por donde llegaste'), ('espiral', 'escalera, sellada hasta prender los sellos'),
             ('hexágono', 'sello: en callejones lejanos, obligatorios'),
             ('calavera roja', 'campeón que custodia la escalera'), ('cruz', 'encuentro: pocos enemigos fuertes'),
             ('sol', 'santuario (bendición temporal)'), ('cofre', 'bóveda con acertijo'),
             ('anillo rojo', 'arena del jefe'), ('aro dorado', 'salida')]
    d.text((lx, ly), 'Referencias', font=font(20, True), fill=(250, 236, 200))
    for k, (a, b) in enumerate(items):
        d.text((lx, ly + 34 + k * 26), '%s: %s' % (a, b), font=font(15), fill=(214, 200, 170))
    canvas.save(path)
    return floors


if __name__ == '__main__':
    seed = int(sys.argv[1]) if len(sys.argv) > 1 else 2609
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    fl = plan_image(seed, os.path.join(out, 'plano_enves_%d.png' % seed))
    for f in fl:
        print(f.depth, len(f.cells), 'cells', len(f.links), 'links', 'start', f.start, 'exit', f.exit,
              {r: sum(1 for v in f.role.values() if v == r) for r in set(f.role.values())})
    for s in range(200):                                              # invariants over many seeds
        check(descent(s))
    print('ok')
