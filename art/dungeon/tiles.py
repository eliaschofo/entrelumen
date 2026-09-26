"""The Envés's room templates, made by code: tileset x role x door mask x variant.

One template is one cell of the floor grid: 19x19 blocks, 12 tall. That is a 2-block floor slab
plus 9 of air plus a 2-block ceiling, so floor k+1's ceiling sits right under floor k's slab.
Doors are 3 wide and 4 tall, centred on each side that has one. The generator makes every one of
the 15 door masks directly, so the runtime never has to rotate a template.

Rooms keep their decoration D4-symmetric around the cell centre (pack rule); only the door cuts
break it.

    python art/dungeon/tiles.py [seed]      # assembles floor I of drlg.descent(seed) and renders it
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'structures'))
import drlg  # noqa: E402

B = lambda n: 'minecraft:' + n
S, H = 19, 12
C = 9                                            # centre coordinate
IN0, IN1 = 1, 17                                 # interior of a room (walls are 1 thick, 2 between cells)
FLOOR_Y, CEIL_Y = 0, 10                          # slab top, first ceiling layer

PALETTES = {
    'osarios': dict(wall=B('tuff_bricks'), course=B('calcite'), floor=B('deepslate_tiles'),
                    tile=B('polished_tuff'), accent=B('chiseled_tuff'), pillar=B('chiseled_tuff_bricks'),
                    cap=B('polished_tuff'), corbel=B('tuff_brick_stairs'), ceil=B('tuff_bricks'),
                    lamp=B('soul_lantern[hanging=true,waterlogged=false]'), relic=B('bone_block[axis=y]'),
                    candle=B('white_candle[candles=3,lit=true,waterlogged=false]'), rock=B('deepslate')),
}


def ab(x, z):
    a, b = abs(x - C), abs(z - C)
    return (a, b) if a >= b else (b, a)


def rnd(seed, *k):
    return random.Random(hash((seed,) + k)).random()


def door_cells(doors):
    """(x, z) cells of the door openings through the 2-thick outer wall."""
    out = set()
    for dname in doors:
        for t in (C - 1, C, C + 1):
            for w in (0,):
                out.add({'N': (t, w), 'S': (t, S - 1 - w), 'W': (w, t), 'E': (S - 1 - w, t)}[dname])
    return out


def corridor_band(doors):
    """Interior cells of a corridor: a 5-wide band from the centre to each door."""
    band = {(x, z) for x in range(C - 2, C + 3) for z in range(C - 2, C + 3)}
    for dname in doors:
        dx, dz = drlg.DIRS[dname]
        for k in range(0, C + 1):
            for t in range(-2, 3):
                x, z = C + dx * k + (t if dx == 0 else 0), C + dz * k + (t if dz == 0 else 0)
                if IN0 <= x <= IN1 and IN0 <= z <= IN1:
                    band.add((x, z))
    return band


def template(tileset, role, doors, variant, seed=0):
    P = PALETTES[tileset]
    v = {}
    straight = sorted(doors) in (['E', 'W'], ['N', 'S'])
    corridor = role in ('quiet',) and straight and variant % 3 != 0
    if role in ('fight', 'guard', 'shrine', 'vault', 'start', 'exit'):
        corridor = False
    kind = variant % 3 if role in ('quiet', 'fight') else {'guard': 0, 'shrine': 2, 'vault': 1, 'start': 2, 'exit': 2}[role]

    def room_shape(x, z):
        a, b = ab(x, z)
        if kind == 1:
            return a + b <= 12                                          # an octagon
        if kind == 2:
            return b <= 3 or a <= 5                                     # a cross with four bays
        return True
    open_cells = corridor_band(doors) if corridor else {(x, z) for x in range(IN0, IN1 + 1) for z in range(IN0, IN1 + 1)
                                                        if room_shape(x, z)}
    for dname in doors:                                                 # every door reaches the room
        dx, dz = drlg.DIRS[dname]
        for k in range(0, C + 1):
            for t in (-1, 0, 1):
                x, z = C + dx * k + (t if dx == 0 else 0), C + dz * k + (t if dz == 0 else 0)
                if IN0 <= x <= IN1 and IN0 <= z <= IN1:
                    open_cells.add((x, z))
    doorways = door_cells(doors)
    for x in range(S):
        for z in range(S):
            a, b = ab(x, z)
            v[(x, 0, z)] = P['rock']
            if (x, z) in open_cells or (x, z) in doorways:
                ring = max(a, b)
                v[(x, FLOOR_Y, z)] = P['accent'] if ring == 0 else (P['tile'] if ring in (2, 6) or (a == b and ring > 2) else P['floor'])
                for y in range(1, CEIL_Y):
                    v[(x, y, z)] = B('air')
                if (x, z) in doorways:
                    for y in range(5, CEIL_Y):
                        v[(x, y, z)] = P['wall']
            else:
                for y in range(1, CEIL_Y):
                    v[(x, y, z)] = P['course'] if y in (1, 5) else P['wall']
            for y in (CEIL_Y, CEIL_Y + 1):
                v[(x, y, z)] = P['ceil']

    def is_air(x, y, z):
        return v.get((x, y, z)) == B('air')

    # corbels along every wall at the top of the room (a vaulted look), and door lintels
    for (x, z) in open_cells:
        for dname, (dx, dz) in drlg.DIRS.items():
            n = (x + dx, z + dz)
            if n not in open_cells and n not in doorways:
                facing = {'N': 'south', 'S': 'north', 'W': 'east', 'E': 'west'}[dname]
                v[(x, CEIL_Y - 1, z)] = P['corbel'] + '[facing=%s,half=top,shape=straight,waterlogged=false]' % facing
                break
    for (x, z) in doorways:
        v[(x, 4, z)] = B('air')
        v[(x, 5, z)] = P['accent']

    if corridor:
        # pilasters every four blocks and candle niches between them
        for (x, z) in sorted(open_cells):
            for dname, (dx, dz) in drlg.DIRS.items():
                n = (x + dx, z + dz)
                if n in open_cells or n in doorways:
                    continue
                along = z if dx else x
                if along % 4 == 1:
                    for y in range(1, CEIL_Y - 1):
                        v[(x, y, z)] = P['pillar'] if y % 4 else P['cap']
                elif along % 4 == 3 and v.get((n[0], 2, n[1])) not in (None, B('air')):
                    v[(n[0], 2, n[1])] = B('air')
                    v[(n[0], 1, n[1])] = P['relic']
                    v[(n[0], 2, n[1])] = P['candle']
        for (x, z) in open_cells:
            if max(ab(x, z)) in (0, 6) and min(ab(x, z)) == 0 and rnd(seed, x, z) < 0.9:
                if is_air(x, CEIL_Y - 1, z) or v.get((x, CEIL_Y - 1, z), '').startswith(P['corbel']):
                    v[(x, CEIL_Y - 1, z)] = P['lamp']
        return v

    # ---- rooms: decoration from octant coordinates, so it is D4-symmetric ----
    for x in range(IN0, IN1 + 1):
        for z in range(IN0, IN1 + 1):
            a, b = ab(x, z)
            if kind == 0 and (a, b) in ((5, 5), (5, 1)):                        # crypt of pillars
                for y in range(1, CEIL_Y):
                    v[(x, y, z)] = P['pillar'] if y < CEIL_Y - 1 else P['cap']
                v[(x, 1, z)] = P['course']
            if kind == 0 and (a, b) in ((6, 5), (5, 6)) and (x, 1, z) in v and v[(x, 1, z)] == B('air'):
                v[(x, 1, z)] = P['candle']
            if kind == 0 and a <= 2 and b <= 1 and role != 'guard':             # a sarcophagus of calcite
                v[(x, 1, z)] = P['course']
                v[(x, 2, z)] = B('calcite') if (a, b) == (0, 0) else B('tuff_brick_slab[type=bottom,waterlogged=false]')
            if kind == 0 and a <= 2 and b <= 1 and role != 'guard' and (x, 2, z) in v and (x - C) * (z - C) == 0 and a == 2:
                v[(x, 1, z)] = P['relic']
            if kind == 1 and a <= 2 and b <= 2:                                  # the bone pit
                v[(x, FLOOR_Y, z)] = P['relic'] if a == b == 0 else B('air')
                if (a, b) != (0, 0):
                    v[(x, -1, z)] = P['relic']
            if kind == 1 and max(a, b) == 3:
                v[(x, 1, z)] = P['course']
            if kind == 2 and a <= 1 and b <= 1:                                  # a chapel dais
                v[(x, 1, z)] = P['accent']
            if kind == 2 and (a, b) in ((5, 0), (5, 5)):
                v[(x, 1, z)] = P['course']
                v[(x, 2, z)] = P['candle']
    # ossuary niches: every third wall block round an octagon or a cross holds bones, a skull and a candle
    if kind in (1, 2):
        for (x, z) in sorted(open_cells):
            for dname, (dx, dz) in drlg.DIRS.items():
                wx, wz = x + dx, z + dz
                if (wx, wz) in open_cells or (wx, wz) in doorways or not (0 <= wx < S and 0 <= wz < S):
                    continue
                a, b = ab(wx, wz)
                if (a + 2 * b) % 3 == 0 and v.get((wx, 2, wz)) not in (None, B('air')):
                    v[(wx, 1, wz)] = P['relic']
                    v[(wx, 2, wz)] = B('skeleton_skull[rotation=0]')
                    v[(wx, 3, wz)] = P['candle']
    # hanging lamps over the room's diagonals
    for x in range(IN0, IN1 + 1):
        for z in range(IN0, IN1 + 1):
            if ab(x, z) == (3, 3):
                v[(x, CEIL_Y - 1, z)] = P['lamp']
    if role == 'shrine':
        v[(C, 2, C)] = B('beacon')                                                # placeholder: the sun shrine
    if role == 'vault':
        v[(C, 2, C)] = B('chest[facing=south,type=single,waterlogged=false]')
    if role in ('start', 'exit'):                                                # the stairwell: a spiral round a pillar
        for y in range(1, CEIL_Y):
            v[(C, y, C)] = P['pillar']
        ring = [(C - 2 + i, C - 2) for i in range(5)] + [(C + 2, C - 2 + i) for i in range(1, 5)] + \
               [(C + 2 - i, C + 2) for i in range(1, 5)] + [(C - 2, C + 2 - i) for i in range(1, 4)]
        if role == 'exit':
            for i, (x, z) in enumerate(ring):
                y = FLOOR_Y - i
                for yy in range(y, CEIL_Y):
                    if (x, z) != (C, C):
                        v[(x, yy, z)] = B('air')
                v[(x, y, z)] = P['course']
            for (x, z) in ring:
                v[(x, FLOOR_Y, z)] = v.get((x, FLOOR_Y, z), B('air'))
    return v


def assemble(floor, tileset='osarios', seed=0):
    world = {}
    for c in floor.cells:
        role = floor.role.get(c, 'quiet')
        t = template(tileset, role, floor.doors(c), int(rnd(seed, c[0], c[1]) * 3), seed)
        for (x, y, z), b in t.items():
            world[(c[0] * S + x - S * drlg.W // 2, y, c[1] * S + z - S * drlg.H // 2)] = b
    return world


if __name__ == '__main__':
    seed = int(sys.argv[1]) if len(sys.argv) > 1 else 2609
    floors = drlg.descent(seed)
    f = floors[0]
    world = assemble(f, seed=seed)
    print(len(world), 'blocks in floor I')
    from voxrender import render
    out = os.environ.get('RUIN_OUT', os.environ.get('TEMP', '.'))
    walk = {(x, z) for (x, y, z), b in world.items() if y == 2 and b == B('air')}

    def shell(x, y, z):
        """Preview only: hide the rock that no one sees, keep walls touching a walkable cell."""
        if y <= 0:
            return any((x + i, z + k) in walk for i in (-1, 0, 1) for k in (-1, 0, 1)) or (x, z) in walk
        return (x, z) in walk or any((x + i, z + k) in walk for i in (-1, 0, 1) for k in (-1, 0, 1))
    render(world, os.path.join(out, 'enves_floor1.png'), scale=3, keep=lambda x, y, z: -1 <= y <= 5 and shell(x, y, z),
           sky=((22, 20, 28), (40, 36, 48)))
    sx, sz = f.start
    near = lambda x, y, z: (abs((x + S * drlg.W // 2) // S - sx) <= 1 and abs((z + S * drlg.H // 2) // S - sz) <= 1
                            and -1 <= y <= 6 and shell(x, y, z))
    render(world, os.path.join(out, 'enves_floor1_close.png'), scale=6, keep=near, sky=((22, 20, 28), (40, 36, 48)))
    print('ok')
