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
Everything is obtainable in act I (copper, calcite, stone bricks, lanterns).

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
R_EQ = 7          # equator radius
SUN_Y = 6         # height of the controller above the platform
DECK = SUN_Y      # export anchor height


def rd(v):
    """Round half away from zero, so shapes stay mirror-symmetric."""
    return int(math.copysign(math.floor(abs(v) + 0.5), v))


def put(x, y, z, b, required=True):
    V[(x, y, z)] = B(b) if ':' not in b else b
    REQ[(x, y, z)] = required


def ring(radius, tilt, yaw, block, cy, required=True, skip=None):
    """A ring of one-block tube round (0, cy, 0), tilted about x by `tilt`, then turned about y."""
    steps = int(2 * math.pi * radius * 3)
    seen = set()
    for i in range(steps):
        th = 2 * math.pi * i / steps
        x, y, z = radius * math.cos(th), 0.0, radius * math.sin(th)
        y, z = y * math.cos(tilt) - z * math.sin(tilt), y * math.sin(tilt) + z * math.cos(tilt)
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        p = (rd(x), rd(cy + y), rd(z))
        if p in seen or p[1] <= 0 or (skip and skip(p)):
            continue
        for q in (p, (-p[0], p[1], p[2])):               # the mirror keeps the ring symmetric
            seen.add(q)
            if q not in SLOTS:
                put(*q, block, required)


def build():
    V.clear(); REQ.clear(); SLOTS.clear()
    # the platform: calcite disc with the sun's rays in copper, a stone-brick rim and four stairs
    for x in range(-10, 11):
        for z in range(-10, 11):
            r = math.hypot(x, z)
            if r > 9.5:
                continue
            th = (math.degrees(math.atan2(z, x)) + 360) % 45
            ray = r > 2 and (th < 4 or th > 41)
            put(x, 0, z, 'stone_bricks' if r > 8.6 else ('cut_copper' if ray else 'calcite'), required=r <= 8.6 and not ray)
            put(x, -1, z, 'stone_bricks', required=False)
    for (dx, dz, facing) in ((0, 10, 'north'), (0, -10, 'south'), (10, 0, 'west'), (-10, 0, 'east')):
        for k in (-1, 0, 1):
            x, z = dx + (k if dx == 0 else 0), dz + (k if dz == 0 else 0)
            put(x, 0, z, 'stone_brick_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]' % facing, required=False)
    # the sun: the controller on its column, with copper rays
    for y in range(1, SUN_Y):
        put(0, y, 0, 'calcite' if y < SUN_Y - 1 else 'chiseled_copper')
    put(0, SUN_Y, 0, 'entrelumen:ark_controller')
    SLOTS[(0, SUN_Y, 0)] = 'ark_controller'
    for (dx, dz, f) in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
        put(dx, SUN_Y, dz, 'lightning_rod[facing=%s,powered=false,waterlogged=false]' % f, required=False)
    put(0, SUN_Y + 1, 0, 'lightning_rod[facing=up,powered=false,waterlogged=false]', required=False)
    # the modules: planets on the equator, each on its pillar with a lantern above
    for name, deg in MODULES:
        th = math.radians(deg)
        x, z = rd(R_EQ * math.cos(th)), rd(R_EQ * math.sin(th))
        put(x, SUN_Y - 2, z, 'entrelumen:%s_module' % name)
        SLOTS[(x, SUN_Y - 2, z)] = '%s_module' % name
        for y in range(1, SUN_Y - 2):
            put(x, y, z, 'calcite')
        put(x, SUN_Y - 1, z, 'lantern[hanging=false,waterlogged=false]', required=False)
    # the rings
    ring(R_EQ, 0.0, 0.0, 'cut_copper', SUN_Y - 2)                       # equator, through the planets
    ring(R_EQ + 1, math.pi / 2, 0.0, 'cut_copper', SUN_Y)               # meridian in the x-y plane
    ring(R_EQ + 1, math.pi / 2, math.pi / 2, 'cut_copper', SUN_Y)       # meridian in the z-y plane
    ring(2, 0.0, 0.0, 'waxed_copper_grate', SUN_Y, required=False)          # the sun's corona
    # where the meridians cross the equator and the top: chiseled copper joints
    for p in ((0, SUN_Y + R_EQ + 1, 0),):
        put(*p, 'chiseled_copper')
    # the gnomon above the crossing
    for y in range(SUN_Y + R_EQ + 2, SUN_Y + R_EQ + 5):
        put(0, y, 0, 'cut_copper' if y < SUN_Y + R_EQ + 4 else 'chiseled_copper', required=False)
    put(0, SUN_Y + R_EQ + 5, 0, 'lantern[hanging=false,waterlogged=false]', required=False)
    put(0, SUN_Y + R_EQ + 6, 0, 'lightning_rod[facing=up,powered=false,waterlogged=false]', required=False)
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
