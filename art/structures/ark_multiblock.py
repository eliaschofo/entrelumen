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
    for x in range(-8, 9):
        for z in range(-8, 9):
            r = math.hypot(x, z)
            if r > 7.4 or (x, 0, z) in V:
                continue
            ang = (math.degrees(math.atan2(z, x)) + 360) % 45
            ray = 1.2 < r < 4.4 and (ang < 7 or ang > 38)
            put(x, 0, z, 'tuff_bricks' if r > 6.5 else ('polished_tuff' if ray else 'stone_bricks'))
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
    # raise it all one level (Elias): the platform stands on the ground, a ring of stairs round it
    raised = {(x, y + 1, z): v for (x, y, z), v in V.items()}
    req = {(x, y + 1, z): v for (x, y, z), v in REQ.items()}
    slots = {(x, y + 1, z): v for (x, y, z), v in SLOTS.items()}
    V.clear(); REQ.clear(); SLOTS.clear()
    V.update(raised); REQ.update(req); SLOTS.update(slots)
    for x in range(-9, 10):
        for z in range(-9, 10):
            r = math.hypot(x, z)
            if (x, 1, z) in V or not (7.4 < r <= 8.45):
                continue
            if not any((x + dx, 1, z + dz) in V for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                continue
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


if __name__ == '__main__':
    build()
    for (x, y, z), b in V.items():
        m = V.get((-x, y, z))
        ok = m is not None and m.split('[')[0] == b.split('[')[0]
        assert ok or (x, y, z) in SLOTS or (-x, y, z) in SLOTS, ('asymmetric', (x, y, z), b, m)
    export()
    from voxrender import render
    shown = {k: v for k, v in V.items() if not v.endswith(':air')}
    render(shown, os.path.join(HERE, 'out', 'ark_multiblock.png'), scale=14, ground=14)
