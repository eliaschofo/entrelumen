"""The Ark as a multiblock: an astrolabe of copper rings round the controller, its sun.

Elias chose the astrolabe over the ship (25 Sept.). Reference (AGENTS.md hook): vanilla armillary
and astrolabe imagery is not in Minecraft, so the reference is the pack's own armillary sun
(art/solsticio/city3.py) and vanilla lightning rods and chiseled copper as the material language.

Layout (x, z across the platform, y up; mirror-symmetric across x = 0 and z = 0):
  - a round calcite platform, radius 9, with the sun's rays inlaid in cut copper and four stairs;
  - the controller on a calcite column at the centre: the sun, with lightning-rod rays;
  - the equator ring of cut copper at the height of the modules, radius 7;
  - the six modules as planets on the equator, at 30, 90, 150, 210, 270 and 330 degrees, each on
    its own calcite pillar with a lantern above;
  - two meridian arches crossing above the sun like a dome, and a corona of grates round it;
  - the gnomon: the axis above the sun with a lantern and a rod on top.
The required core is about 55 cheap pieces (stone bricks, smooth stone slabs); the rest is decoration.

Writes art/structures/out/ark_multiblock.json (positions relative to the controller, the block,
whether it is required, and the slots) and a render.

    python art/structures/ark_multiblock.py
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from voxkit import Voxels  # noqa: E402

V = Voxels()
REQ = {}        # (x, y, z) -> True if the multiblock requires the block, False for decoration
SLOTS = {}      # (x, y, z) -> slot name
B = lambda n: 'minecraft:' + n
MODULES = [('habitation', 90), ('exploration', 30), ('nature', 330), ('arcane', 270), ('logistics', 210), ('engineering', 150)]
R_EQ = 5          # radius of the orbit
SUN_Y = 2         # the controller stands on the raised floor
DECK = SUN_Y      # export anchor height


def rd(v):
    """Round half away from zero, so shapes stay mirror-symmetric."""
    return int(math.copysign(math.floor(abs(v) + 0.5), v))


def put(x, y, z, b, required=True):
    V[(x, y, z)] = B(b) if ':' not in b else b
    REQ[(x, y, z)] = required


def ring(radius, tilt, yaw, block, cy, required=True, floor=False):
    """A one-block ring round (0, cy, 0), tilted about x by `tilt`, then turned about y."""
    steps = int(2 * math.pi * radius * 3)
    for i in range(steps):
        th = 2 * math.pi * i / steps
        x, y, z = radius * math.cos(th), 0.0, radius * math.sin(th)
        y, z = y * math.cos(tilt) - z * math.sin(tilt), y * math.sin(tilt) + z * math.cos(tilt)
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        p = (rd(x), rd(cy + y), rd(z))
        if p[1] < (0 if floor else 1):
            continue
        for q in (p, (-p[0], p[1], p[2])):               # the mirror keeps the ring symmetric
            if q not in SLOTS and (q not in V or floor):
                put(*q, block, required)


def build():
    """Everything on the ground, full cubes only (Elias): a stone-brick orbit in the floor with
    chiseled copper sockets under the modules and the controller, set in a round floor with the
    sun's rays and amethyst on the diagonals; two arches striped in cut copper and tuff bricks on
    calcite feet, with an amethyst keystone."""
    V.clear(); REQ.clear(); SLOTS.clear()
    put(0, 1, 0, 'entrelumen:ark_controller')
    SLOTS[(0, 1, 0)] = 'ark_controller'
    put(0, 0, 0, 'chiseled_copper')
    sockets = []
    for name, deg in MODULES:
        th = math.radians(deg)
        x, z = rd(R_EQ * math.cos(th)), rd(R_EQ * math.sin(th))
        put(x, 1, z, 'entrelumen:%s_module' % name)
        SLOTS[(x, 1, z)] = '%s_module' % name
        sockets.append((x, 0, z))
    # the floor (Elias: «que tenga un PISO»): a disc of stone bricks with a tuff-brick rim, the
    # sun's eight rays in polished tuff, the orbit in calcite and amethyst on the diagonals
    for x in range(-9, 10):
        for z in range(-9, 10):
            r = math.hypot(x, z)
            if r > 8.5 or (x, 0, z) in V:
                continue
            ang = (math.degrees(math.atan2(z, x)) + 360) % 45
            ray = 1.2 < r < 4.4 and (ang < 7 or ang > 38)
            put(x, 0, z, 'tuff_bricks' if r > 7.5 else ('polished_tuff' if ray else 'stone_bricks'))
    ring(R_EQ, 0.0, 0.0, 'calcite', 0, floor=True)                 # the orbit, laid in the floor
    for p in list(V):
        if p[1] == 0 and abs(math.hypot(p[0], p[2]) - R_EQ) < 0.5 and V[p] != B('calcite') and p not in SLOTS:
            V[p] = B('calcite')
    for p in sockets:
        put(*p, 'chiseled_copper')
    for (x, z) in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
        put(x, 0, z, 'amethyst_block')
    ring(R_EQ + 2, math.pi / 2, 0.0, 'stone_bricks', 0)            # two arches over the controller
    ring(R_EQ + 2, math.pi / 2, math.pi / 2, 'stone_bricks', 0)
    top = R_EQ + 2
    for (x, y, z), b in list(V.items()):                           # stripe the arches like voussoirs
        if y >= 1 and b == B('stone_bricks'):
            k = abs(x) + abs(z) + y
            V[(x, y, z)] = B('calcite') if y == 1 else (B('cut_copper') if k % 2 == 0 else B('tuff_bricks'))
    put(0, top, 0, 'amethyst_block')                              # the keystone where they cross
    # four columns on the diagonals, where the arches give no support (Elias): a small flared base
    # with corner stairs and a flared capital of polished-tuff stairs (no corners up there), a
    # shaft of calcite alone, and the beacon's place right on the capital.
    # The beacon is optional: each one adds a level to the modules' effects (ark-modules-v2.md).
    for (cx, cz) in ((5, 5), (-5, 5), (5, -5), (-5, -5)):
        for (dx, dz, toward) in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            put(cx + dx, 1, cz + dz, 'polished_tuff_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % toward)
            put(cx + dx, 7, cz + dz, 'polished_tuff_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % toward)
        for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):   # the base's corners (the game shapes them)
            put(cx + dx, 1, cz + dz, 'polished_tuff_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]'
                % ('north' if dz > 0 else 'south'))
        for y in range(1, 8):
            put(cx, y, cz, 'calcite')
        put(cx, 8, cz, 'beacon', required=False)                  # the beacon sits right on the capital
        SLOTS[(cx, 8, cz)] = 'beacon'
    # raise it all one level (Elias): the platform stands on the ground, a ring of stairs round it
    raised = {(x, y + 1, z): v for (x, y, z), v in V.items()}
    req = {(x, y + 1, z): v for (x, y, z), v in REQ.items()}
    slots = {(x, y + 1, z): v for (x, y, z), v in SLOTS.items()}
    V.clear(); REQ.clear(); SLOTS.clear()
    V.update(raised); REQ.update(req); SLOTS.update(slots)
    floor = {(x, z) for (x, y, z) in V if y == 1}
    border = []
    for x in range(-10, 11):
        for z in range(-10, 11):
            r = math.hypot(x, z)
            if (x, z) in floor or not (8.5 < r <= 9.55):
                continue
            if not any((x + dx, z + dz) in floor for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                continue
            border.append((x, z))
    for (x, z) in border:
        inward = ('west' if x > 0 else 'east') if abs(x) >= abs(z) else ('north' if z > 0 else 'south')
        put(x, 1, z, 'stone_brick_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % inward)
    return V


def export():
    out = os.path.join(HERE, 'out')
    os.makedirs(out, exist_ok=True)
    blocks = []
    for (x, y, z), b in sorted(V.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        cx, cy, cz = 0, DECK, 0
        blocks.append({'pos': [x - cx, y - cy, z - cz], 'block': b, 'required': REQ[(x, y, z)],
                       **({'slot': SLOTS[(x, y, z)]} if (x, y, z) in SLOTS else {})})
    data = {'name': 'entrelumen:ark', 'anchor': 'entrelumen:ark_controller',
            'note': 'positions relative to the controller; copper blocks match any oxidation and waxing',
            'blocks': blocks}
    with open(os.path.join(out, 'ark_multiblock.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=1)
    req = sum(1 for b in blocks if b['required'] and b['block'] != 'minecraft:air')
    print(len(blocks), 'positions,', req, 'required,', len(SLOTS), 'slots')


DIRS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
CCW = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}
CW = {v: k for k, v in CCW.items()}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}


def _props(b):
    return dict(p.split('=') for p in b[:-1].split('[')[1].split(',')) if '[' in b else {}


def stair_shape(vox, pos, b):
    """The shape the game gives a stair from its neighbours (StairBlock.getStairsShape)."""
    pr = _props(b)
    f, half = pr.get('facing'), pr.get('half')
    x, y, z = pos

    def stair_at(d):
        n = vox.get((x + DIRS[d][0], y, z + DIRS[d][1]), '')
        return _props(n) if n.split('[')[0].endswith('_stairs') and _props(n).get('half') == half else None

    def can_take(d):
        n = stair_at(d)
        return not (n and n.get('facing') == f)
    front = stair_at(f)
    if front and DIRS[front['facing']][0] * DIRS[f][0] == 0 and DIRS[front['facing']][1] * DIRS[f][1] == 0             and front['facing'] not in (f, OPP[f]) and can_take(OPP[front['facing']]):
        return 'outer_left' if front['facing'] == CCW[f] else 'outer_right'
    back = stair_at(OPP[f])
    if back and back['facing'] not in (f, OPP[f]) and can_take(back['facing']):
        return 'inner_left' if back['facing'] == CCW[f] else 'inner_right'
    return 'straight'


def _side(d, sx, sz):
    return {'north': sz == 0, 'south': sz == 1, 'west': sx == 0, 'east': sx == 1}[d]


def subdivided(vox):
    """Each block as 2x2x2 sub-blocks, so stairs (with their corner shapes) and slabs read."""
    out = {}
    for (x, y, z), b in vox.items():
        if b.endswith(':air'):
            continue
        name = b.split('[')[0]
        pr = _props(b)
        shape = stair_shape(vox, (x, y, z), b) if name.endswith('_stairs') else None
        texture = name.replace('_stairs', '').replace('_slab', '')
        texture = texture.replace('stone_brick', 'stone_bricks') if texture.endswith('stone_brick') else texture
        for sx in (0, 1):
            for sy in (0, 1):
                for sz in (0, 1):
                    keep = True
                    if name.endswith('_slab'):
                        keep = pr.get('type') == 'double' or (sy == 0) == (pr.get('type') == 'bottom')
                    elif shape:
                        f = pr['facing']
                        full = sy == (0 if pr.get('half') == 'bottom' else 1)
                        if shape == 'straight':
                            part = _side(f, sx, sz)
                        elif shape == 'outer_left':
                            part = _side(f, sx, sz) and _side(CCW[f], sx, sz)
                        elif shape == 'outer_right':
                            part = _side(f, sx, sz) and _side(CW[f], sx, sz)
                        elif shape == 'inner_left':
                            part = _side(f, sx, sz) or _side(CCW[f], sx, sz)
                        else:
                            part = _side(f, sx, sz) or _side(CW[f], sx, sz)
                        keep = full or part
                    if keep:
                        out[(2 * x + sx, 2 * y + sy, 2 * z + sz)] = texture if name.endswith(('_slab', '_stairs')) else name
    return out


if __name__ == '__main__':
    build()
    for (x, y, z), b in V.items():
        m = V.get((-x, y, z))
        ok = m is not None and m.split('[')[0] == b.split('[')[0]
        assert ok or (x, y, z) in SLOTS or (-x, y, z) in SLOTS, ('asymmetric', (x, y, z), b, m)
    export()
    from voxrender import render
    render(subdivided(V), os.path.join(HERE, 'out', 'ark_multiblock.png'), scale=9, ground=24)
