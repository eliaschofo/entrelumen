"""Solsticio's urban plan: the street network, the blocks, the plazas, the green and the water.

One cell is one block. The plan is data first (CELL: (x, z) -> kind, DISTRICT, LANDMARKS) so the
voxel build can follow it; draw() turns it into a map for review.

Principles (Elias, 25 Sept.: "que navegar la ciudad sea bello y no sea una village de la 1.12"):
  - a street hierarchy: the ceremonial Axis of the Sun, two ring boulevards, radials, streets and
    lanes; every street ends on something worth looking at (a landmark, a viewpoint);
  - perimeter blocks: continuous frontage on the street, a green courtyard inside;
  - plazas at the nodes, each with its own character;
  - green: the Midday Park with its lake, tree-lined boulevards, courtyards, the Edge Promenade;
  - water: a canal of light down the Axis, a branch through the Workshops to the Last Falls;
  - six districts with an identity: Market (the trading hall), Inns, Gardens, Travellers
    (the player plots), Temple, Workshops.

    python art/concepts/solsticio_plan.py
"""
import math
import os
import sys
from collections import deque

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from freevox import fbm2, rnd  # noqa: E402

OUT = os.environ.get('ENTRELUMEN_CONCEPTS', os.path.join(HERE, 'out'))
R = 150
EXT = 172

CELL, DISTRICT = {}, {}
LANDMARKS = []          # (name_es, name_en, x, z, kind)
LABELS = []             # (text, x, z, size, colour)


def edge(x, z):
    th = math.atan2(z, x)
    return R * (1 + 0.05 * math.sin(5 * th + 0.4) + 0.04 * math.sin(3 * th + 2.1)) \
        + 14 * (fbm2(math.cos(th) * 60, math.sin(th) * 60, 40, 7) - 0.5)


def inside(x, z, margin=0):
    return math.hypot(x, z) < edge(x, z) - margin


PALACE = (0, -44)
MAYOR = (0, -10)                         # Plaza Mayor, in front of the palace
PORTAL = (0, 124)                        # Plaza del Portal, the arrival


def height(x, z):
    """The hill: highest under the palace, falling to the promenade (for contours and the build)."""
    d = math.hypot(x - PALACE[0] * 0.5, z - PALACE[1] * 0.6)
    e = min(1.0, d / edge(x, z))
    return 58 * (1 - e) ** 1.25 + 5 * (fbm2(x, z, 45, 3) - 0.5)


# ---------------- network ----------------
def ring_point(cx, cz, r, th, wob, seed):
    rr = r * (1 + wob * (fbm2(math.cos(th) * 40, math.sin(th) * 40, 25, seed) - 0.5))
    return cx + rr * math.cos(th), cz + rr * math.sin(th)


def stamp(pts, width, kind, district=None, over=('block', None)):
    r = width / 2
    for (x, z) in pts:
        for dx in range(-int(r) - 1, int(r) + 2):
            for dz in range(-int(r) - 1, int(r) + 2):
                if math.hypot(dx + round(x) - x, dz + round(z) - z) <= r:
                    c = (round(x) + dx, round(z) + dz)
                    if c in CELL and CELL[c] in over:
                        CELL[c] = kind


def polyline(a, b, step=0.5):
    n = int(math.dist(a, b) / step) + 1
    return [(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n) for i in range(n + 1)]


def disc(c, r, kind, over=None):
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            if math.hypot(dx, dz) <= r:
                p = (c[0] + dx, c[1] + dz)
                if p in CELL and (over is None or CELL[p] in over):
                    CELL[p] = kind


def rect(x0, z0, x1, z1, kind, over=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) in CELL and (over is None or CELL[(x, z)] in over):
                CELL[(x, z)] = kind


RADIALS = [30, 150, 210, 270, 330]        # degrees, maths angle with +z pointing south on the map
DISTRICTS = {  # wedge (from, to) in degrees, measured from the island centre
    'market': (30, 90), 'inns': (90, 150), 'gardens': (150, 210),
    'travellers': (210, 270), 'temple': (270, 330), 'workshops': (330, 390),
}
NAMES = {'market': ('Barrio del Mercado', 'Market Quarter'), 'inns': ('Barrio de las Posadas', 'Inns Quarter'),
         'gardens': ('Los Jardines', 'The Gardens'), 'travellers': ('Barrio de los Viajeros', 'Travellers\' Quarter'),
         'temple': ('Barrio del Templo', 'Temple Quarter'), 'workshops': ('Los Talleres', 'The Workshops')}


def district_of(x, z):
    a = (math.degrees(math.atan2(z, x)) + 360) % 360
    for name, (a0, a1) in DISTRICTS.items():
        if a0 <= a < a1 or a0 <= a + 360 < a1:
            return name
    return 'workshops'


def ring(cx, cz, r, width, kind, wob, seed):
    pts = [ring_point(cx, cz, r, 2 * math.pi * i / 1400, wob, seed) for i in range(1400)]
    stamp(pts, width, kind)
    return pts


def plan():
    for x in range(-EXT, EXT + 1):
        for z in range(-EXT, EXT + 1):
            if inside(x, z):
                CELL[(x, z)] = 'block'
                DISTRICT[(x, z)] = district_of(x, z)
    # the Edge Promenade all round
    for (x, z) in list(CELL):
        if not inside(x, z, 9):
            CELL[(x, z)] = 'promenade'
    # rings, the axis, radials
    inner = ring(0, -12, 58, 8, 'boulevard', 0.12, 21)
    outer = ring(0, 2, 104, 8, 'boulevard', 0.10, 22)
    ring(0, -6, 82, 4, 'street', 0.12, 23)                    # the middle street between the rings
    axis = polyline(MAYOR, PORTAL)
    stamp(axis, 20, 'axis')
    stamp(axis, 3, 'canal', over=('axis',))
    for side in (-7, 7):
        stamp([(x + side, z) for (x, z) in axis], 2, 'trees', over=('axis',))
    for a in RADIALS:
        th = math.radians(a)
        start = 30 if a in (30, 150) else 62
        pts = polyline((math.cos(th) * start, -12 + math.sin(th) * start), (math.cos(th) * 170, math.sin(th) * 170))
        pts = [p for p in pts if inside(p[0], p[1], 6)]
        stamp(pts, 7, 'street')
        if pts:
            ex, ez = pts[-1]
            LANDMARKS.append(('Mirador', 'Viewpoint', round(ex), round(ez), 'mirador'))
    # streets at the thirds of every wedge, to keep blocks about 25-35 blocks across
    for a0, a1 in DISTRICTS.values():
        for f in (1 / 3, 2 / 3):
            th = math.radians(a0 + (a1 - a0) * f)
            pts = polyline((math.cos(th) * 64, -12 + math.sin(th) * 64), (math.cos(th) * 170, math.sin(th) * 170))
            stamp([p for p in pts if inside(p[0], p[1], 6)], 4, 'street')
    ring(0, 2, 128, 4, 'street', 0.08, 24)                   # the outer street behind the promenade
    # lanes between the inner ring and the palace: a fine grid of passages
    for k in range(-50, 51, 17):
        stamp(polyline((k, -70), (k, 45)), 3, 'lane')
        stamp(polyline((-60, k - 12), (60, k - 12)), 3, 'lane')
    # the palace on its acropolis, the Plaza Mayor in front
    rect(PALACE[0] - 24, PALACE[1] - 20, PALACE[0] + 24, PALACE[1] + 18, 'palace')
    disc(MAYOR, 17, 'plaza', over=('block', 'lane', 'street', 'boulevard', 'axis', 'trees', 'canal'))
    disc(PORTAL, 15, 'plaza', over=('block', 'axis', 'trees', 'canal', 'promenade', 'boulevard'))
    LANDMARKS.append(('Palacio del Solsticio', 'Palace of the Solstice', PALACE[0], PALACE[1], 'palace'))
    LANDMARKS.append(('Plaza Mayor', 'Main Square', MAYOR[0], MAYOR[1], 'plaza'))
    LANDMARKS.append(('Plaza del Portal', 'Portal Square', PORTAL[0], PORTAL[1], 'plaza'))
    # nodes: plazas where the radials meet the rings
    def node(a, r_, cx, cz, rad, name_es, name_en, kind='plaza'):
        th = math.radians(a)
        p = (round(cx + math.cos(th) * r_), round(cz + math.sin(th) * r_))
        disc(p, rad, 'plaza', over=('block', 'lane', 'street', 'boulevard', 'trees'))
        LANDMARKS.append((name_es, name_en, p[0], p[1], kind))
        return p
    market = node(30, 58, 0, -12, 13, 'Plaza del Mercado', 'Market Square')
    fountains = node(150, 104, 0, 2, 13, 'Plaza de las Fuentes', 'Fountain Square')
    clock = node(-12, 104, 0, 2, 11, 'Plaza del Reloj', 'Clock Square')
    travellers = node(232, 84, 0, -6, 10, 'Plaza de los Viajeros', 'Travellers\' Square')
    sundial = node(300, 82, 0, -6, 9, 'Plaza del Reloj de Sol', 'Sundial Square')
    node(118, 82, 0, -6, 8, 'Plazoleta de los Faroles', 'Lantern Close', kind='small')
    node(62, 82, 0, -6, 8, 'Plazoleta del Pan', 'Bread Close', kind='small')
    # the Great Market of Light: the trading hall, a glass hall on the Market Square
    mx, mz = market
    rect(mx + 10, mz - 6, mx + 50, mz + 18, 'market', over=('block', 'lane', 'street'))
    LANDMARKS.append(('Gran Mercado de la Luz', 'Great Market of Light', mx + 30, mz + 6, 'market'))
    # the Street of Crafts: an arcaded shopping street from the market to the clock tower
    th0, th1 = math.radians(30), math.radians(-12)
    crafts = [((58 + 46 * t) * math.cos(th0 + (th1 - th0) * t), -12 + 14 * t + (58 + 46 * t) * math.sin(th0 + (th1 - th0) * t)) for t in [i / 300 for i in range(301)]]
    stamp(crafts, 7, 'crafts', over=('block', 'lane', 'street', 'boulevard'))
    LABELS.append(('Calle de los Oficios', crafts[150][0], crafts[150][1] - 8, 13, '#8a4a2a'))
    cx, cz = clock
    rect(cx - 3, cz - 3, cx + 3, cz + 3, 'tower')
    LANDMARKS.append(('Torre del Reloj', 'Clock Tower', cx, cz, 'tower'))
    # the Midday Park with its lake, the botanical greenhouse and Juan's hall
    for (x, z) in list(CELL):
        if DISTRICT.get((x, z)) == 'gardens' and CELL[(x, z)] in ('block', 'street', 'lane') and 64 < math.hypot(x, z + 6) < 140:
            CELL[(x, z)] = 'park'
    lake = (-98, 8)
    for dx in range(-22, 23):
        for dz in range(-13, 14):
            if (dx / 22) ** 2 + (dz / 13) ** 2 <= 1 and CELL.get((lake[0] + dx, lake[1] + dz)) == 'park':
                CELL[(lake[0] + dx, lake[1] + dz)] = 'lake'
    LANDMARKS.append(('Parque del Mediodía', 'Midday Park', -100, -20, 'park'))
    rect(-128, -58, -110, -44, 'greenhouse', over=('park',))
    LANDMARKS.append(('Jardín Botánico (Juan)', 'Botanical Garden (Juan)', -119, -51, 'greenhouse'))
    # park paths
    for a in range(0, 360, 3):
        th = math.radians(a)
        p = (round(lake[0] + 28 * math.cos(th)), round(lake[1] + 18 * math.sin(th)))
        disc(p, 1.5, 'path', over=('park',))
    stamp(polyline((-64, -30), (-140, 40)), 3, 'path', over=('park',))
    # the Temple of Dawn on its knoll (Bodhi), the Workshops' harbour and Terra's workshop
    tx, tz = round(math.cos(math.radians(305)) * 118), round(math.sin(math.radians(305)) * 118)
    disc((tx, tz), 13, 'temple_ground', over=('block', 'street', 'lane', 'promenade'))
    rect(tx - 7, tz - 9, tx + 7, tz + 9, 'temple')
    LANDMARKS.append(('Templo del Alba (Bodhi)', 'Temple of Dawn (Bodhi)', tx, tz, 'temple'))
    wx, wz = round(math.cos(math.radians(8)) * 116), round(math.sin(math.radians(8)) * 116) + 4
    rect(wx - 12, wz - 9, wx + 12, wz + 9, 'workshop', over=('block', 'lane', 'street'))
    LANDMARKS.append(('Taller de Terra', 'Terra\'s Workshop', wx, wz, 'workshop'))
    # the canal of light: from the palace spring down the axis, a branch east to the Last Falls
    def along(a0, a1, r_):
        n = int(abs(a1 - a0) * 8)
        return [ring_point(0, 2, r_, math.radians(a0 + (a1 - a0) * i / n), 0.10, 22) for i in range(n + 1)]
    east = along(90, 5, 97)
    stamp(east, 4, 'canal', over=('block', 'lane', 'street', 'plaza', 'crafts', 'boulevard'))
    ex, ez = east[-1]
    out = [p for p in polyline((ex, ez), (ex * 1.6, ez * 1.6)) if inside(p[0], p[1], 1)]
    stamp(out, 4, 'canal', over=('block', 'lane', 'street', 'boulevard', 'promenade', 'plaza'))
    fx, fz = out[-1] if out else (ex, ez)
    LANDMARKS.append(('Cascada del Fin', 'The Last Falls', round(fx), round(fz), 'falls'))
    west = along(90, 158, 97)
    west = west + polyline(west[-1], lake)
    stamp(west, 3, 'canal', over=('block', 'lane', 'street', 'boulevard', 'park', 'plaza'))
    # the four player plots round the Travellers' Square
    px, pz = travellers
    for k, (dx, dz) in enumerate(((-28, -28), (12, -28), (-28, 12), (12, 12))):
        rect(px + dx, pz + dz, px + dx + 15, pz + dz + 15, 'plot', over=('block', 'lane', 'street'))
    LANDMARKS.append(('Lotes de los viajeros', 'Travellers\' plots', px - 20, pz - 20, 'plot'))
    # district labels
    for name, (a0, a1) in DISTRICTS.items():
        th = math.radians((a0 + a1) / 2)
        LABELS.append((NAMES[name][0], math.cos(th) * 92, -6 + math.sin(th) * 92, 17, '#3b3040'))
    blocks()


def blocks():
    """What the streets leave are blocks: a ring of buildings on their edge, a garden inside."""
    seen = set()
    for c in list(CELL):
        if CELL[c] != 'block' or c in seen:
            continue
        comp, q = [], deque([c])
        seen.add(c)
        while q:
            p = q.popleft()
            comp.append(p)
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (p[0] + d[0], p[1] + d[1])
                if n not in seen and CELL.get(n) == 'block':
                    seen.add(n)
                    q.append(n)
        compset = set(comp)
        dist = {}
        q = deque()
        for p in comp:
            if any((p[0] + d[0], p[1] + d[1]) not in compset for d in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                dist[p] = 0
                q.append(p)
        while q:
            p = q.popleft()
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (p[0] + d[0], p[1] + d[1])
                if n in compset and n not in dist:
                    dist[n] = dist[p] + 1
                    q.append(n)
        deep = max(dist.values()) if dist else 0
        for p in comp:
            if len(comp) < 30:
                CELL[p] = 'pocket'            # too small for houses: a pocket garden
            elif deep >= 11 and dist[p] >= 8:
                CELL[p] = 'court'
            else:
                CELL[p] = 'building'


# ---------------- drawing ----------------
COL = {
    'axis': '#fff1c2', 'boulevard': '#f7ecd8', 'street': '#fbf6ec', 'lane': '#f3ecdf', 'crafts': '#ffe0b5',
    'plaza': '#ffdf8f', 'promenade': '#e8dcc0', 'trees': '#7cbf5a', 'canal': '#5fc3ec', 'lake': '#6fcaf0',
    'park': '#9fd57a', 'path': '#efe2c4', 'court': '#b9e39a', 'pocket': '#a8dc86', 'palace': '#f2c14e',
    'market': '#f39c5a', 'tower': '#c98f2a', 'greenhouse': '#9fe0e8', 'temple': '#e9a6d4', 'temple_ground': '#f6e2ee',
    'workshop': '#a98be0', 'plot': '#ffffff',
}
BUILD = {'market': '#f2c3a0', 'inns': '#f2d58e', 'gardens': '#c7e3a8', 'travellers': '#c6dcf2',
         'temple': '#efc9e2', 'workshops': '#cdbff0'}


def draw(path, px=3):
    size = (2 * EXT + 1) * px
    legend_w = 420
    im = Image.new('RGB', (size + legend_w + 40, size + 120), '#1c2233')
    d = ImageDraw.Draw(im)
    ox, oy = 20, 100
    font = lambda n, bold=False: ImageFont.truetype('C:/Windows/Fonts/%s' % ('cambriab.ttf' if bold else 'cambria.ttc'), n)
    for (x, z), kind in CELL.items():
        c = COL.get(kind)
        if kind == 'building':
            c = BUILD[DISTRICT[(x, z)]]
        if c is None:
            continue
        X, Y = ox + (x + EXT) * px, oy + (z + EXT) * px
        d.rectangle([X, Y, X + px - 1, Y + px - 1], fill=c)
    # contours of the hill
    for (x, z), kind in CELL.items():
        h = height(x, z)
        if int(h // 8) != int(height(x + 1, z) // 8) or int(h // 8) != int(height(x, z + 1) // 8):
            X, Y = ox + (x + EXT) * px, oy + (z + EXT) * px
            d.point((X + 1, Y + 1), fill='#8a7a6a')
    # trees in courtyards, pockets and the park
    for (x, z), kind in CELL.items():
        if kind in ('court', 'pocket', 'park', 'promenade', 'trees') and rnd(x, 0, z, 5) < (0.05 if kind != 'trees' else 0.35):
            X, Y = ox + (x + EXT) * px + 1, oy + (z + EXT) * px + 1
            d.ellipse([X - 3, Y - 3, X + 3, Y + 3], fill='#5ea648' if rnd(x, 1, z, 5) > 0.3 else '#f2a7c8')
    # island outline and the barrier
    for (x, z) in CELL:
        if any((x + a, z + b) not in CELL for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            X, Y = ox + (x + EXT) * px, oy + (z + EXT) * px
            d.rectangle([X - 1, Y - 1, X + px, Y + px], fill='#f6d66b')
    # landmarks
    icon = {'palace': '#c98f2a', 'market': '#d35f2e', 'tower': '#8a5a1a', 'temple': '#b0569a', 'greenhouse': '#2f9fb0',
            'workshop': '#6f4fc0', 'mirador': '#e0a020', 'falls': '#2f8fd0', 'plaza': '#b0762a', 'small': '#b0762a',
            'park': '#3e8a2e', 'plot': '#4a6fa0'}
    boxes = []
    for (es, en, x, z, kind) in LANDMARKS:
        X, Y = ox + (x + EXT) * px, oy + (z + EXT) * px
        r = 7 if kind in ('palace', 'market') else 5
        d.ellipse([X - r, Y - r, X + r, Y + r], fill=icon.get(kind, '#888'), outline='#1c2233', width=2)
        if kind == 'mirador':
            continue
        f = font(17 if kind in ('palace', 'market') else 14, bold=kind in ('palace', 'market', 'plaza'))
        tw = d.textlength(es, font=f)
        for (bx, by) in ((X + 9, Y - 10), (X - 13 - tw, Y - 10), (X - tw / 2, Y + 10), (X - tw / 2, Y - 30)):
            box = (bx, by, bx + tw + 4, by + 19)
            if not any(box[0] < b[2] and b[0] < box[2] and box[1] < b[3] and b[1] < box[3] for b in boxes):
                break
        boxes.append(box)
        d.rectangle(box, fill=(28, 34, 51))
        d.text((box[0] + 2, box[1]), es, font=f, fill='#fff3d6')
    for (text, x, z, sz, colour) in LABELS:
        f = font(sz, bold=True)
        X, Y = ox + (x + EXT) * px, oy + (z + EXT) * px
        tw = d.textlength(text, font=f)
        d.text((X - tw / 2, Y - sz / 2), text, font=f, fill=colour)
    # title and legend
    d.text((20, 18), 'Solsticio · plano urbano', font=font(40, True), fill='#fff3d6')
    d.text((20, 64), 'La ciudad del mediodía eterno, dentro de la barrera de luz. Una celda = un bloque.', font=font(17), fill='#c9d3e6')
    lx, ly = size + 50, 110
    items = [('Eje del Sol (avenida ceremonial con canal)', COL['axis']), ('Bulevares en anillo', COL['boulevard']),
             ('Calles y radiales', COL['street']), ('Pasajes', COL['lane']), ('Calle de los Oficios (arcadas)', COL['crafts']),
             ('Plazas', COL['plaza']), ('Paseo del Borde', COL['promenade']), ('Parque del Mediodía', COL['park']),
             ('Patios de manzana', COL['court']), ('Jardines de bolsillo', COL['pocket']), ('Canal de luz y lago', COL['canal']),
             ('Palacio del Solsticio', COL['palace']), ('Gran Mercado (trading hall)', COL['market']),
             ('Templo del Alba', COL['temple']), ('Taller de Terra', COL['workshop']), ('Jardín Botánico', COL['greenhouse']),
             ('Lotes de jugadores', COL['plot'])]
    d.text((lx, ly - 36), 'Referencias', font=font(22, True), fill='#fff3d6')
    for i, (label, c) in enumerate(items):
        y = ly + i * 26
        d.rectangle([lx, y, lx + 20, y + 18], fill=c, outline='#0e1220')
        d.text((lx + 30, y - 1), label, font=font(16), fill='#e6ecf6')
    y = ly + len(items) * 26 + 20
    d.text((lx, y), 'Edificios por barrio', font=font(20, True), fill='#fff3d6')
    for i, (k, c) in enumerate(BUILD.items()):
        yy = y + 32 + i * 26
        d.rectangle([lx, yy, lx + 20, yy + 18], fill=c, outline='#0e1220')
        d.text((lx + 30, yy - 1), NAMES[k][0], font=font(16), fill='#e6ecf6')
    yy = y + 32 + len(BUILD) * 26 + 20
    notes = ['Toda calle termina en algo: el palacio,', 'la torre del reloj, el templo o un mirador.',
             'Manzanas: fachada continua a la calle,', 'patio verde adentro.', 'Curvas de nivel cada 8 bloques.']
    for i, n in enumerate(notes):
        d.text((lx, yy + i * 22), n, font=font(15), fill='#c9d3e6')
    im.save(path)
    return im.size


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    plan()
    from collections import Counter
    print(Counter(CELL.values()).most_common())
    print(draw(os.path.join(OUT, 'solsticio_plan.png')))
