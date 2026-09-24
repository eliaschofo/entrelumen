"""The Solsticio portal in the Ayuntamiento (entrelumen:solsticio_portal), sculpted in 1x1x1-unit voxels
on the altar palette texture (entrelumen:block/altar_voxels), mirror-symmetric under the D4 group.

- dormant (armed=false): a two-step round plinth of calcite and copper, four copper posts on the
  diagonals (three sockets for the relics and one for the Light Key, empty and dark), and a dim
  teal crystal at the centre.
- open (armed=true): the same plinth with the four sockets lit, and a column of light two blocks
  tall with two halo rings; the light elements carry NeoForge's emissive data (full brightness).

    cd art/authoring && python voxel_portal.py   # writes art/models/block/solsticio_portal_*.json + preview
"""
import importlib.util
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
_spec = importlib.util.spec_from_file_location('voxel_altars', os.path.join(HERE, 'voxel_altars.py'))
VA = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(VA)

OUT = os.path.join(HERE, '..', 'models', 'block')
GLOW = {'Y', 'U', 'f'}


def plinth(V):
    for x in range(16):
        for z in range(16):
            d = math.hypot(x - 7.5, z - 7.5)
            if d <= 7.6:
                V[(x, 0, z)] = 'c' if d > 6.6 else 'J'
            if d <= 6.1:
                V[(x, 1, z)] = 'K' if d > 5.2 else 'J'
            if d <= 4.4:
                V[(x, 2, z)] = 'I' if d > 3.6 else 'J'


def posts(V, lit):
    for (cx, cz) in ((3, 3), (3, 11), (11, 3), (11, 11)):
        for y in range(2, 9):
            for dx in (0, 1):
                for dz in (0, 1):
                    V[(cx + dx, y, cz + dz)] = 'K' if y % 3 else 'H'
        # a cup on top, empty (dark) or holding a glowing relic
        for dx in (-1, 0, 1, 2):
            for dz in (-1, 0, 1, 2):
                if dx in (-1, 2) or dz in (-1, 2):
                    if not (dx in (-1, 2) and dz in (-1, 2)):
                        V[(cx + dx, 9, cz + dz)] = 'o'
                else:
                    V[(cx + dx, 9, cz + dz)] = 'Y' if lit else 'k'
        if lit:
            for dx in (0, 1):
                for dz in (0, 1):
                    V[(cx + dx, 10, cz + dz)] = 'U'


def dormant():
    V = {}
    plinth(V)
    posts(V, False)
    for y in range(3, 6):
        for (x, z) in ((7, 7), (8, 7), (7, 8), (8, 8)):
            V[(x, y, z)] = 'T' if y < 5 else 't'
    for (x, z) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        V[(x, 6, z)] = 'u'
    return V


def opened():
    V = {}
    plinth(V)
    posts(V, True)
    for y in range(3, 32):
        for x in range(6, 10):
            for z in range(6, 10):
                inner = 7 <= x <= 8 and 7 <= z <= 8
                taper = y > 27 and not inner
                if taper:
                    continue
                V[(x, y, z)] = 'Y' if inner else 'U'
    for yr in (12, 23):
        for x in range(16):
            for z in range(16):
                d = math.hypot(x - 7.5, z - 7.5)
                if 4.6 <= d <= 5.6:
                    V[(x, yr, z)] = 'f'
    return V


def symmetric(V):
    for (x, y, z), k in V.items():
        for (a, b) in ((15 - x, z), (x, 15 - z), (z, x)):
            if V.get((a, y, b)) != k:
                return False
    return True


def model(V, comment):
    solid = {p: k for p, k in V.items() if k not in GLOW}
    glow = {p: k for p, k in V.items() if k in GLOW}
    elements = VA.elements(solid, oy=0)
    for e in VA.elements(glow, oy=0):
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        e['shade'] = False
        elements.append(e)
    return {'__comment': comment, 'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'particle': 'entrelumen:block/altar_stone', 'voxel': 'entrelumen:block/altar_voxels'},
            'elements': elements}


if __name__ == '__main__':
    shots = {}
    for name, fn, comment in (
            ('solsticio_portal_dormant', dormant, 'Solsticio portal, dormant: plinth, four empty sockets, dim crystal (art/authoring/voxel_portal.py)'),
            ('solsticio_portal_open', opened, 'Solsticio portal, open: lit sockets and a column of light (art/authoring/voxel_portal.py)')):
        V = fn()
        assert symmetric(V), name
        m = model(V, comment)
        with open(os.path.join(OUT, name + '.json'), 'w', newline='\n') as f:
            json.dump(m, f, indent=1)
        print(name, len(m['elements']), 'elements')
