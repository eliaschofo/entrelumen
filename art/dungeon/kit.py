"""Shared kit for the Envés tilesets drawn after Osarios: Cisternas (II), Fundición (III), Geodas (IV)
and El Eclipse (V). tiles.py stays the Osarios reference and is not changed; this kit reuses its
geometry (cell size, door cells, the stairwell ring) and adds what the new tilesets need.

- **D4 by construction.** A room body is authored for one octant of the cell (dx >= dz >= 0 from the
  centre, "east-south-east") and copied to its eight images with block states re-oriented
  (voxkit.rot_state). Corridors are authored running east-west and keep their two mirrors (D2).
  check_symmetry() proves the body before the doors are cut: only doors, the stairwell and the
  arena's open sides break it, as in Osarios.
- **Deck.** A tileset may walk on a deck one block over the slab (DECK = 1, Cisternas) so its water
  has a bed inside the template. Doors, markers and a sunken well round the stairwell follow it.
- **Doors** per mask: 3 wide and 4 tall through the outer wall, with jambs, a lintel and a lamp over
  each, so exits read from across the room.
- **Engine contract.** The stairwell is tiles.stairwell (the ring the Java side and its tests know);
  markers sit at explicit spots and are checked standable; connection states (iron bars, walls,
  stairs corners) are baked, because the engine pastes templates with known shapes.
- **validate()** refuses a template whose fluids can flow (every water or lava block sealed inside
  the template), whose lava touches anything passable, whose doors or markers are not reachable on
  foot, or whose stairwell lost its headroom; light() gives the light map the review draws.
"""
import os
import sys
from collections import deque
from functools import lru_cache

HERE = os.path.dirname(os.path.abspath(__file__))
for _p in (HERE, os.path.join(HERE, '..', 'structures')):
    if _p not in sys.path:
        sys.path.insert(0, _p)
import drlg  # noqa: E402
import tiles  # noqa: E402
from voxkit import T8, tf  # noqa: E402
from voxkit import rot_state as _rot_state  # noqa: E402


@lru_cache(maxsize=None)
def rot_state(state, t):
    return _rot_state(state, t)


S, H, C = tiles.S, tiles.H, tiles.C              # 19 x 12 x 19, centre 9
CEIL_Y = tiles.CEIL_Y                            # first ceiling layer (10); rooms are y 1..9 over a 0 slab
AIR = 'minecraft:air'
DIRS = drlg.DIRS                                 # N E S W -> (dx, dz)
FACE = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
CCW = {v: k for k, v in CW.items()}
DOOR_FACE = {'N': 'north', 'E': 'east', 'S': 'south', 'W': 'west'}
M64 = (1 << 64) - 1


# ------------------------------------------------------------------ block states
WL_SUFFIX = ('_stairs', '_slab', '_wall', '_pane', '_fence', '_trapdoor', '_grate', '_candle', '_bud')
WL_NAMES = {'iron_bars', 'chain', 'lantern', 'soul_lantern', 'lightning_rod', 'amethyst_cluster', 'candle',
            'light', 'pointed_dripstone', 'glow_lichen', 'ladder', 'heavy_core', 'decorated_pot', 'conduit',
            'sea_pickle'}


@lru_cache(maxsize=None)
def base(state):
    """'minecraft:waxed_copper_bulb[lit=true]' -> 'waxed_copper_bulb'."""
    return state.split('[')[0].split(':')[-1]


@lru_cache(maxsize=None)
def core(state):
    """Base name without the waxed_ prefix: what the block is, for tables of light and shape."""
    n = base(state)
    return n[len('waxed_'):] if n.startswith('waxed_') else n


@lru_cache(maxsize=None)
def _props(state):
    if '[' not in state:
        return ()
    return tuple(p.split('=') for p in state[:-1].split('[', 1)[1].split(','))


def props(state):
    return dict(_props(state))


def _fmt(name, kw):
    return name + ('[' + ','.join('%s=%s' % kv for kv in sorted(kw.items())) + ']' if kw else '')


def B(name, **kw):
    """A block state with sorted properties; waterloggable blocks default to waterlogged=false."""
    full = name if ':' in name else 'minecraft:' + name
    n = full.split(':')[1]
    if 'waterlogged' not in kw and (n in WL_NAMES or n.endswith(WL_SUFFIX)):
        kw['waterlogged'] = 'false'
    return _fmt(full, {k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in kw.items()})


def with_props(state, **kw):
    p = props(state)
    p.update({k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in kw.items()})
    return _fmt(state.split('[')[0], p)


def stairs(name, facing, half='bottom', shape='straight'):
    return B(name, facing=facing, half=half, shape=shape)


def slab(name, kind='bottom'):
    return B(name, type=kind)


# ------------------------------------------------------------------ classification
NO_COLLISION = {'air', 'cave_air', 'void_air', 'water', 'lava', 'light', 'glow_lichen', 'structure_void',
                'short_grass', 'fern', 'vine', 'cave_vines', 'cave_vines_plant', 'weeping_vines', 'twisting_vines'}
PARTIAL = ('_stairs', '_slab', '_wall', '_pane', '_fence', '_trapdoor', '_candle', '_bud', '_button', '_carpet',
           '_head', '_skull', '_rod', '_door', '_sign', '_banner')
PARTIAL_NAMES = {'iron_bars', 'chain', 'lantern', 'soul_lantern', 'amethyst_cluster', 'candle', 'end_rod',
                 'lightning_rod', 'pointed_dripstone', 'cauldron', 'lava_cauldron', 'water_cauldron', 'anvil',
                 'hopper', 'lectern', 'enchanting_table', 'bell', 'heavy_core', 'decorated_pot', 'flower_pot',
                 'conduit', 'chest', 'trapped_chest', 'ender_chest', 'scaffolding', 'ladder', 'grindstone',
                 'brewing_stand', 'daylight_detector', 'sea_pickle', 'skeleton_skull', 'wither_skeleton_skull'}
SEE_THROUGH = {'glass', 'tinted_glass', 'iron_bars', 'beacon', 'ice', 'water', 'lava', 'air', 'light'}


@lru_cache(maxsize=None)
def fluid_of(state):
    """'water', 'lava' or None: the fluid a placed block carries."""
    if state is None:
        return None
    n = base(state)
    if n in ('water', 'lava'):
        return n
    if 'waterlogged=true' in state:
        return 'water'
    return None


@lru_cache(maxsize=None)
def passable(state):
    """Nothing to collide with: an entity walks or falls through it."""
    return state is None or base(state) in NO_COLLISION


@lru_cache(maxsize=None)
def full(state):
    """A full cube for standing, supporting and attaching (copper grates and glass count)."""
    if state is None or passable(state):
        return False
    n = base(state)
    if n in PARTIAL_NAMES or n.endswith(PARTIAL):
        return n.endswith('_slab') and ('type=double' in state)
    return True


@lru_cache(maxsize=None)
def support_height(state):
    """Top of what one stands on, relative to the block's y, or None (not a floor)."""
    if state is None or passable(state):
        return None
    n = base(state)
    if full(state):
        return 1.0
    if n.endswith('_slab'):
        return 0.5 if 'type=bottom' in state else 1.0
    if n.endswith('_stairs'):
        return 1.0 if 'half=top' in state else 0.75
    if n in ('cauldron', 'lava_cauldron', 'water_cauldron', 'chest', 'trapped_chest', 'lectern', 'anvil'):
        return 1.0
    return None


# ------------------------------------------------------------------ determinism
def mix(*keys):
    """A 64-bit hash of integers that does not depend on the Python build (unlike hash() of str)."""
    h = 0x9E3779B97F4A7C15
    for k in keys:
        h ^= (k & M64) + 0x9E3779B97F4A7C15 + ((h << 6) & M64) + (h >> 2)
        h = (h * 0xBF58476D1CE4E5B9) & M64
        h ^= h >> 31
    return h


def rnd(*keys):
    return (mix(*keys) >> 11) / float(1 << 53)


# ------------------------------------------------------------------ symmetric placement
def octant(dx, dz):
    a, b = abs(dx), abs(dz)
    return (a, b) if a >= b else (b, a)


def ab(x, z):
    """Octant coordinates of a template cell (distance along the main axis, across)."""
    return octant(x - C, z - C)


def sym_put(v, dx, y, dz, state):
    """Place a state authored at the centred offset (dx, dz) in its eight D4 images."""
    for t in T8:
        px, pz = tf(dx, dz, t)
        v[(C + px, y, C + pz)] = rot_state(state, t) if '[' in state else state


def mirror_put(v, dx, y, dz, state):
    """Place a state at (dx, dz) and its mirrors across both axes (corridors, D2)."""
    for t in T8[:4]:                                   # no swap: identity and the three mirrors
        px, pz = tf(dx, dz, t)
        v[(C + px, y, C + pz)] = rot_state(state, t) if '[' in state else state


def octant_body(column, reach=9):
    """A D4 body from column(a, b) -> {y: state}, authored at the canonical offset (a, b), a >= b."""
    v = {}
    for a in range(reach + 1):
        for b in range(a + 1):
            for y, st in column(a, b).items():
                sym_put(v, a, y, b, st)
    return v


def transpose(v):
    """Swap x and z (and re-orient states): an east-west corridor becomes north-south."""
    t = (1, 1, True)
    return {(z, y, x): (rot_state(st, t) if '[' in st else st) for (x, y, z), st in v.items()}


def check_symmetry(v, group='D4'):
    """Positions whose images disagree: [] when v is symmetric under D4 (or D2, the two mirrors)."""
    ts = T8 if group == 'D4' else T8[:4]
    bad = []
    for (x, y, z), st in v.items():
        for t in ts:
            px, pz = tf(x - C, z - C, t)
            q = (C + px, y, C + pz)
            want = rot_state(st, t) if '[' in st else st
            if v.get(q) != want:
                bad.append(((x, y, z), q, st, v.get(q)))
                break
    return bad


def read_quadrant(text):
    """A 9x9 ASCII quadrant (rows dz 0..8, columns dx 0..8) -> {(dx, dz): char} for the octant dx >= dz.
    The drawing must mirror across its diagonal: the other half is only there to be read."""
    rows = [r.strip() for r in text.strip().splitlines()]
    assert len(rows) == 9 and all(len(r) == 9 for r in rows), 'a quadrant is 9 rows of 9'
    for dz in range(9):
        for dx in range(9):
            assert rows[dz][dx] == rows[dx][dz], 'quadrant not symmetric at %d,%d' % (dx, dz)
    return {(dx, dz): rows[dz][dx] for dz in range(9) for dx in range(dz, 9)}


# ------------------------------------------------------------------ doors
def door_ring(d):
    """The three outer-wall cells of a door, template (x, z)."""
    return [{'N': (t, 0), 'S': (t, S - 1), 'W': (0, t), 'E': (S - 1, t)}[d] for t in (C - 1, C, C + 1)]


def along(d, k, t):
    """Template (x, z) k cells in from door d's wall (k = 0 is the wall) and t across (-1..1)."""
    dx, dz = DIRS[d]
    return (C + dx * (C - k) + (t if dx == 0 else 0), C + dz * (C - k) + (t if dz == 0 else 0))


def cut_door(v, ts, d):
    """Open door d: 3 wide, 4 tall over the deck, jambs and lintel, a lamp over it, and a walkable path
    to the room if its design left none."""
    deck = ts.DECK
    P = ts.P
    face = DOOR_FACE[d]
    for (x, z) in door_ring(d):
        v[(x, 0, z)] = P['base']
        if deck:
            v[(x, deck, z)] = P['threshold']
        for y in range(deck + 1, deck + 5):
            v[(x, y, z)] = AIR
        v[(x, deck + 5, z)] = P['lintel']
    for t in (-2, 2):                                   # jambs
        x, z = along(d, 0, 0)
        x, z = (x + t, z) if d in 'NS' else (x, z + t)
        for y in range(deck + 1, deck + 6):
            v[(x, y, z)] = P.get('jamb', P['wall'])
    x, z = along(d, 0, 0)
    if P.get('door_lamp'):
        v[(x, deck + 6, z)] = P['door_lamp']
    for k in range(1, C):                               # make sure the doorway reaches the room
        cells = [along(d, k, t) for t in (-1, 0, 1)]
        if all(walkable_column(v, cx, cz, deck) for (cx, cz) in cells):
            break
        for (cx, cz) in cells:
            if walkable_column(v, cx, cz, deck):
                continue
            v[(cx, 0, cz)] = P['base']
            if deck:
                v[(cx, deck, cz)] = P['threshold']
            for y in range(deck + 1, deck + 5):
                v[(cx, y, cz)] = AIR
            if v.get((cx, deck + 5, cz)) not in (None, AIR):
                v[(cx, deck + 5, cz)] = P['lintel']
    return face


def walkable_column(v, x, z, deck):
    return (support_height(v.get((x, deck, z))) == 1.0 and base(v.get((x, deck, z))) not in ('water', 'lava')
            and passable(v.get((x, deck + 1, z))) and fluid_of(v.get((x, deck + 1, z))) is None
            and passable(v.get((x, deck + 2, z))))


def open_sides(v, ts, doors):
    """Arena cells: every linked side opens fully, so 3x3 cells read as one hall."""
    deck = ts.DECK
    P = ts.P
    for d in doors:
        for t in range(1, S - 1):
            x, z = {'N': (t, 0), 'S': (t, S - 1), 'W': (0, t), 'E': (S - 1, t)}[d]
            ix, iz = {'N': (t, 1), 'S': (t, S - 2), 'W': (1, t), 'E': (S - 2, t)}[d]
            for y in range(0, deck + 1):
                v[(x, y, z)] = v.get((ix, y, iz), P['base'])
            for y in range(deck + 1, CEIL_Y):
                inner = v.get((ix, y, iz), AIR)
                v[(x, y, z)] = inner if full(inner) else AIR
    drop_unsupported(v)                                    # nothing left hanging from an opened wall
    for a_, b_, (x, z) in (('N', 'E', (S - 1, 0)), ('N', 'W', (0, 0)), ('S', 'E', (S - 1, S - 1)),
                           ('S', 'W', (0, S - 1))):
        if a_ in doors and b_ in doors:
            for y in range(0, deck + 1):
                v[(x, y, z)] = P['base'] if y < deck else P['threshold']
            for y in range(deck + 1, CEIL_Y):
                v[(x, y, z)] = AIR


def needs_support(state):
    n = core(state)
    return n in ('amethyst_cluster', 'large_amethyst_bud', 'medium_amethyst_bud', 'small_amethyst_bud', 'lantern',
                 'soul_lantern') or n.endswith('candle')


def drop_unsupported(v):
    """A door or an opened side can take the wall a crystal or a lantern hung from: take it too."""
    while True:
        gone = [p for p, st in v.items() if needs_support(st) and not supported(v, p, st)]
        if not gone:
            return
        for p in gone:
            v[p] = AIR


def supported(v, p, state):
    """Whether a block that pops off without support has it: clusters on the face they grow from,
    lanterns under a block or a chain (hanging) or on one, candles on one."""
    x, y, z = p
    n = core(state)
    pr = props(state)
    if n.endswith('lantern'):
        if pr.get('hanging') == 'true':
            above = v.get((x, y + 1, z))
            return above is not None and (full(above) or base(above) == 'chain' or support_height(above) == 1.0)
        return support_height(v.get((x, y - 1, z))) is not None
    if n.endswith('candle'):
        return support_height(v.get((x, y - 1, z))) is not None
    f = pr.get('facing', 'up')
    back = {'up': (0, -1, 0), 'down': (0, 1, 0)}.get(f)
    if back is None:
        fx, fz = FACE[f]
        back = (-fx, 0, -fz)
    return full(v.get((x + back[0], y + back[1], z + back[2])))


# ------------------------------------------------------------------ the stairwell (engine contract)
RING = tiles.RING


def stairwell(v, ts, role):
    """tiles.stairwell with the tileset's blocks; a deck tileset sinks the well to the slab and adds
    steps back up to the deck."""
    P = ts.P
    deck = ts.DECK
    TP = {'pillar': P['pillar'], 'wall': P['wall'], 'cap': P['landing'], 'corbel': P['step']}
    if role in ('exit', 'vestibule'):
        if deck:
            for x in range(C - 2, C + 3):
                for z in range(C - 2, C + 3):
                    v[(x, 0, z)] = P['well']
                    for y in range(1, deck + 4):
                        v[(x, y, z)] = AIR
        for y in range(1, CEIL_Y):
            v[(C, y, C)] = P['pillar']
        tiles.stairwell(v, TP, role)
        if deck:
            for d, (dx, dz) in DIRS.items():               # steps up from the well at the four sides
                x, z = C + 3 * dx, C + 3 * dz
                v[(x, deck, z)] = stairs(P['step'], DOOR_FACE[d])
        return
    tiles.stairwell(v, TP, 'start')
    x0, z0 = RING[0]
    v[(x0, 0, z0)] = P['landing']
    for y in range(1, 4 + deck):
        v[(x0, y, z0)] = AIR
    if deck:                                               # the landing sits in the slab: two steps up
        v[(x0 - 1, deck, z0)] = stairs(P['step'], 'west')
        v[(x0, deck, z0 - 1)] = stairs(P['step'], 'north')
        for (x, z) in ((x0 - 1, z0), (x0, z0 - 1)):
            v[(x, 0, z)] = P['base']
            for y in range(deck + 1, deck + 4):
                v[(x, y, z)] = AIR


def ring_steps():
    """(x, y, z, landing) of the stairwell steps in each template: 'exit' (upper) and 'start' (lower)."""
    out = {'exit': [], 'start': []}
    for p in range(17):
        x, z = RING[p % 16]
        kind, y = tiles.ring_step(p) if p < 16 else ('landing', -12)
        if y >= 0:
            out['exit'].append((x, y, z, kind == 'landing'))
        if y + tiles.FLOOR_H < H:
            out['start'].append((x, y + tiles.FLOOR_H, z, kind == 'landing'))
    return out


# ------------------------------------------------------------------ baked connection states
def _attaches_pane(n):
    if n is None or passable(n):
        return False
    b = base(n)
    return b == 'iron_bars' or b.endswith('_pane') or b.endswith('_wall') or full(n)


def _attaches_wall(n):
    if n is None or passable(n):
        return False
    b = base(n)
    if b.endswith('_wall') or b == 'iron_bars' or b.endswith('_pane'):
        return True
    if b.endswith('_fence_gate'):
        return False
    return full(n)


POST_OVERRIDE = ('torch', 'lantern', 'soul_lantern', '_sign', '_banner', 'pressure_plate')


def _stair_shape(v, pos, st):
    f = props(st).get('facing')
    half = props(st).get('half')
    x, y, z = pos

    def at(d):
        dx, dz = FACE[d]
        return v.get((x + dx, y, z + dz))

    def is_st(s):
        return s is not None and base(s).endswith('_stairs')

    def can_take(face):
        s = at(face)
        return not is_st(s) or props(s).get('facing') != f or props(s).get('half') != half
    front = at(f)
    if is_st(front) and props(front).get('half') == half:
        f1 = props(front)['facing']
        if (f1 in ('east', 'west')) != (f in ('east', 'west')) and can_take(OPP[f1]):
            return 'outer_left' if f1 == CCW[f] else 'outer_right'
    back = at(OPP[f])
    if is_st(back) and props(back).get('half') == half:
        f2 = props(back)['facing']
        if (f2 in ('east', 'west')) != (f in ('east', 'west')) and can_take(f2):
            return 'inner_left' if f2 == CCW[f] else 'inner_right'
    return 'straight'


def bake(v):
    """Connection states the world would compute: iron bars and panes, walls, stairs corners."""
    out = dict(v)
    for (x, y, z), st in v.items():
        b = base(st)
        if b == 'iron_bars' or b.endswith('_pane'):
            kw = {d: _attaches_pane(v.get((x + dx, y, z + dz))) for d, (dx, dz) in FACE.items()}
            out[(x, y, z)] = with_props(st, **kw)
        elif b.endswith('_wall'):
            above = v.get((x, y + 1, z))
            tall = above is not None and full(above)
            kw = {}
            for d, (dx, dz) in FACE.items():
                kw[d] = ('tall' if tall else 'low') if _attaches_wall(v.get((x + dx, y, z + dz))) else 'none'
            n, s, e, w = kw['north'], kw['south'], kw['east'], kw['west']
            above_up = above is not None and base(above).endswith('_wall') and 'up=true' in above
            if above_up or (n == s == e == w == 'none') or ((n == 'none') != (s == 'none')) or \
                    ((w == 'none') != (e == 'none')):
                up = True
            elif (n == 'tall' and s == 'tall') or (e == 'tall' and w == 'tall'):
                up = False
            else:
                up = above is not None and (full(above) or base(above).endswith(POST_OVERRIDE))
            kw['up'] = up
            out[(x, y, z)] = with_props(st, **kw)
    for (x, y, z), st in list(out.items()):
        if base(st).endswith('_stairs'):
            out[(x, y, z)] = with_props(st, shape=_stair_shape(out, (x, y, z), st))
    return out


# ------------------------------------------------------------------ markers
def standable(v, x, y, z):
    """Feet at y: a real floor under them (no fluid), nothing to collide with at feet and head."""
    under = v.get((x, y - 1, z))
    return (support_height(under) is not None and fluid_of(under) is None
            and passable(v.get((x, y, z))) and fluid_of(v.get((x, y, z))) is None
            and passable(v.get((x, y + 1, z))) and fluid_of(v.get((x, y + 1, z))) is None)


def first_standable(v, spots, y):
    for (x, z) in spots:
        if standable(v, x, y, z):
            return (x, z)
    return None


def facing_centre(x, z):
    return tiles.facing_centre(x, z)


# ------------------------------------------------------------------ validation
def walk_nodes(v, extra_air=()):
    """{(x, ys, z): height} where one can stand on the block at ys (feet in ys+1, head ys+2)."""
    air = set(extra_air)

    def st(p):
        return AIR if p in air else v.get(p)
    nodes = {}
    for (x, y, z), s in v.items():
        if not (0 <= x < S and 0 <= z < S and 0 <= y < H - 2):
            continue
        s = st((x, y, z))
        h = support_height(s)
        if h is None or base(s) in ('water', 'lava'):
            continue
        feet, head = st((x, y + 1, z)), st((x, y + 2, z))
        if feet is None and y + 1 >= CEIL_Y:
            continue
        if passable(feet) and passable(head) and fluid_of(head) is None and base(feet or AIR) != 'lava':
            nodes[(x, y, z)] = y + h
    return nodes


def walk_graph(nodes):
    """Two-way walking edges: steps of at most half a block, or a full block onto or off stairs."""
    by_col = {}
    for (x, y, z), h in nodes.items():
        by_col.setdefault((x, z), []).append((y, h))
    edges = {n: [] for n in nodes}
    for (x, y, z), h in nodes.items():
        for dx, dz in FACE.values():
            for (y2, h2) in by_col.get((x + dx, z + dz), ()):
                dh = abs(h2 - h)
                if dh <= 0.5 or (dh <= 1.0 and (h % 1 or h2 % 1)):
                    edges[(x, y, z)].append((x + dx, y2, z + dz))
    return edges


def reach(edges, start):
    seen = {start}
    q = deque([start])
    while q:
        n = q.popleft()
        for m in edges[n]:
            if m not in seen:
                seen.add(m)
                q.append(m)
    return seen


LIGHT_SHARE = 0.8                                          # of the standing floor lit to 8 or more


def validate(v, markers, role, doors, ts, corridor=False):
    """Refusals for one template (a list of strings; empty is good)."""
    deck = ts.DECK
    err = []
    for (x, y, z) in v:
        if not (0 <= x < S and 0 <= y < H and 0 <= z < S):
            err.append('voxel outside the template at %s' % ((x, y, z),))
            break
    # fluids stay put: every water or lava block has no passable neighbour beside or below it
    for (x, y, z), st in v.items():
        f = fluid_of(st)
        if f is None:
            continue
        if x in (0, S - 1) or z in (0, S - 1) or y < 1:
            err.append('%s at the template edge %s' % (f, (x, y, z)))
            continue
        for (nx, ny, nz) in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            n = v.get((nx, ny, nz))
            nf = fluid_of(n)
            if nf is not None and nf != f:
                err.append('water meets lava at %s' % ((x, y, z),))
            elif nf is None and passable(n):
                err.append('%s at %s can flow into %s' % (f, (x, y, z), (nx, ny, nz)))
        if f == 'lava':                                   # sealed on every face, bars as planes
            for d, (dx, dz) in list(FACE.items()) + [('up', (0, 0))]:
                ny = y + 1 if d == 'up' else y
                n = v.get((x + dx, ny, z + dz))
                if fluid_of(n) == 'lava' or full(n) or base(n or AIR) in ('lava_cauldron',):
                    continue
                if base(n or AIR) == 'iron_bars' and d != 'up':
                    side = ('east', 'west') if d in ('north', 'south') else ('north', 'south')
                    if all(props(n).get(s) == 'true' for s in side):
                        continue
                err.append('lava at %s is reachable from %s (%s)' % ((x, y, z), d, n))
    for p, st in v.items():
        if needs_support(st) and not supported(v, p, st):
            err.append('%s at %s has nothing to hold it' % (base(st), p))
    # the doorways
    for d in doors:
        for (x, z) in door_ring(d):
            for y in range(deck + 1, deck + 5):
                if not passable(v.get((x, y, z))):
                    err.append('door %s blocked at %s' % (d, (x, y, z)))
                    break
    # markers
    kinds = {}
    feet_marks, block_marks = [], []
    for (x, y, z, meta) in markers:
        k = meta.split(':')[1]
        kinds[k] = kinds.get(k, 0) + 1
        if k in ('arrival', 'stair_top', 'stair_bottom', 'encounter', 'chest', 'vault_mechanism', 'boss_center'):
            if not standable(v, x, y, z):
                err.append('marker %s at %s is not standable' % (meta, (x, y, z)))
            feet_marks.append((x, y - 1, z, meta))
        elif k in ('shrine', 'seal', 'exit_portal'):
            block_marks.append((x, y, z, meta))
    # room for the big ones: an elite gets four blocks of air and most of the ring round it free, the
    # champion a 3x3 floor with five blocks of air (Cataclysm's golems and the Prowler are large)
    for (x, y, z, meta) in markers:
        if not meta.startswith('enves:encounter'):
            continue
        tall = 5 if meta.endswith('champion') else 4
        reach_ = 1 if meta.endswith('champion') else 0
        for dx in range(-reach_, reach_ + 1):
            for dz in range(-reach_, reach_ + 1):
                for h in range(tall):
                    if not passable(v.get((x + dx, y + h, z + dz))):
                        err.append('%s at %s has no room over it (%s)' % (meta, (x, y, z), (x + dx, y + h, z + dz)))
                        break
        ring = sum(1 for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (dx or dz) and standable(v, x + dx, y, z + dz))
        if ring < 5:
            err.append('%s at %s is boxed in (%d free around it)' % (meta, (x, y, z), ring))
    want = {'start': {'arrival': 1, 'stair_bottom': 1}, 'exit': {'stair_top': 1, 'stair_seal': 4},
            'vestibule': {'stair_top': 1, 'exit_portal': 1}, 'shrine': {'shrine': 1}, 'seal': {'seal': 1},
            'arena_center': {'boss_center': 1}, 'portal': {'exit_portal': 1, 'chest': 1},
            'vault': {'chest': 1, 'vault_gate': 12 * len(doors), 'vault_mechanism': len(doors)},
            'guard': {'encounter': 5}, 'fight': {'encounter': 4}}
    for k, n in want.get(role, {}).items():
        if kinds.get(k, 0) != n:
            err.append('%s wants %d %s markers, has %d' % (role, n, k, kinds.get(k, 0)))
    if role == 'quiet' and not corridor and kinds.get('chest', 0) != 1:
        err.append('a quiet room wants one chest spot')
    # on foot: every door, marker and dais reachable from the first door (or the arrival)
    marker_air = [(x, y, z) for (x, y, z, m) in markers]
    nodes = walk_nodes(v, marker_air)
    edges = walk_graph(nodes)
    entries = []
    for d in doors:
        x, z = door_ring(d)[1]
        entries.append((x, deck, z))
    for e in entries:
        if e not in nodes:
            err.append('no floor in doorway %s' % (e,))
    start = next((e for e in entries if e in nodes), None)
    if start is None and role == 'start':
        for (x, y, z, meta) in markers:
            if meta.endswith('arrival'):
                start = (x, y - 1, z)
    if start is not None:
        seen = reach(edges, start)
        for e in entries:
            if e in nodes and e not in seen:
                err.append('door at %s is not reachable from %s' % (e, start))
        for (x, y, z, meta) in feet_marks:
            if (x, y, z) not in seen and not meta.endswith('stair_bottom') and not meta.endswith('stair_top'):
                err.append('marker %s at %s is not reachable on foot' % (meta, (x, y + 1, z)))
        for (x, y, z, meta) in block_marks:
            near = [(x + dx, yy, z + dz) for dx, dz in FACE.values() for yy in (y - 2, y - 1, y)]
            if not any(n in seen for n in near):
                err.append('%s at %s has no floor beside it' % (meta, (x, y, z)))
        if role in ('exit', 'vestibule'):
            x, z = RING[0]
            if (x, 0, z) not in seen:
                err.append('the stairwell top is not reachable')
    # good light (Elias: rooms need readable exits and good light): most of the floor at 8 or more
    share, darkest = light_report(v, markers)
    if share < LIGHT_SHARE:
        err.append('too dark: %d%% of the floor at light 8 or more' % round(100 * share))
    # headroom over the stairwell
    if role in ('start', 'exit', 'vestibule'):
        steps = ring_steps()['start' if role == 'start' else 'exit']
        for (x, y, z, landing) in steps:
            for hgt in range(1, 4):
                if y + hgt < H and not passable(v.get((x, y + hgt, z))) and (x, y + hgt, z) not in marker_air:
                    err.append('no headroom over ring step %s at height %d' % ((x, y, z), hgt))
    return err


# ------------------------------------------------------------------ one template
def is_corridor(role, doors, variant):
    """Osarios's rule: a straight quiet cell is a corridor in two variants of three."""
    return role == 'quiet' and sorted(doors) in (['E', 'W'], ['N', 'S']) and variant % 3 != 0


def build(ts, role, doors, variant, markers=None, check=True):
    """The voxels of one template of tileset module ts; fills `markers` with (x, y, z, metadata)."""
    corridor = is_corridor(role, doors, variant)
    if corridor:
        v = body(ts, 'corridor', variant, 'D2')             # authored running east-west
        if sorted(doors) == ['N', 'S']:
            v = transpose(v)
    else:
        v = body(ts, role, variant, 'D4')
    for d in doors:
        cut_door(v, ts, d)
    if role in ('start', 'exit', 'vestibule'):
        stairwell(v, ts, role)
    if role in ('arena', 'arena_center'):
        open_sides(v, ts, doors)
    if hasattr(ts, 'finish'):
        ts.finish(v, role, doors, variant, corridor)
    drop_unsupported(v)
    v = bake(v)
    ms = standard_markers(v, ts, role, doors, variant, corridor)
    if check:
        err = validate(v, ms, role, doors, ts, corridor)
        if err:
            raise ValueError('%s %s_%s_%d: %s' % (ts.NAME, role, ''.join(doors).lower() or 'x', variant,
                                                  '; '.join(err[:6])))
    if markers is not None:
        markers.extend(ms)
    return v


_BODIES = {}


def body(ts, role, variant, group):
    """The symmetric body of a role and variant, checked once and copied for every door mask."""
    key = (ts.NAME, role, variant)
    if key not in _BODIES:
        v = ts.corridor(variant) if role == 'corridor' else ts.room(role, variant)
        bad = check_symmetry(v, group)
        if bad:
            raise ValueError('%s %s_%d body is not symmetric: %s' % (ts.NAME, role, variant, bad[:3]))
        _BODIES[key] = v
    return dict(_BODIES[key])


def standard_markers(v, ts, role, doors, variant, corridor):
    """The marker contract (docs/design/dungeon-enves.md) at the tileset's spots."""
    deck = ts.DECK
    y = deck + 1
    out = []
    spots = ts.spots(role, variant, corridor)
    x0, z0 = RING[0]

    def need(key):
        spot = first_standable(v, spots[key], y)
        if spot is None:
            raise ValueError('%s %s_%d: no standable %s spot among %s' % (ts.NAME, role, variant, key, spots[key]))
        return spot
    if role == 'start':
        ax, az = need('arrival')
        out.append((ax, y, az, 'enves:arrival'))
        out.append((x0, 1, z0, 'enves:stair_bottom'))
    if role in ('exit', 'vestibule'):
        out.append((x0, 1, z0, 'enves:stair_top'))
    if role == 'exit':
        for (x, z) in RING:
            if v.get((x, 0, z)) == AIR:
                out.append((x, 0, z, 'enves:stair_seal'))
    if role == 'vestibule':
        px, pz = spots.get('portal', [(C + 7, C)])[0]
        out.append((px, y, pz, 'enves:exit_portal:return'))
    if role == 'guard':
        out.append((C, y, C, 'enves:encounter:champion'))
    if role in ('fight', 'guard'):
        for (x, z) in spots['encounter']:
            out.append((x, y, z, 'enves:encounter'))
    if role == 'quiet' and not corridor:
        cx, cz = need('chest')
        out.append((cx, y, cz, 'enves:chest:room/%s' % facing_centre(cx, cz)))
    if role == 'shrine':
        out.append((C, y + 1, C, 'enves:shrine'))
    if role == 'seal':
        out.append((C, y + 1, C, 'enves:seal'))
    if role == 'vault':
        out.append((C, y, C, 'enves:chest:vault/%s' % DOOR_FACE[doors[0] if doors else 'N']))
        for d in doors:
            for (x, z) in door_ring(d):
                for yy in range(deck + 1, deck + 5):
                    out.append((x, yy, z, 'enves:vault_gate:%s' % d.lower()))
            dx, dz = DIRS[d]
            out.append((C + dx * (C - 2), y, C + dz * (C - 2), 'enves:vault_mechanism'))
    if role == 'arena_center':
        out.append((C, y, C, 'enves:boss_center'))
    if role == 'portal':
        out.append((C, y + 1, C, 'enves:exit_portal:victory'))
        bx, bz = need('boss_chest')
        out.append((bx, y, bz, 'enves:chest:boss/%s' % facing_centre(bx, bz)))
    return out


# ------------------------------------------------------------------ light
EMIT = {'lava': 15, 'sea_lantern': 15, 'glowstone': 15, 'shroomlight': 15, 'ochre_froglight': 15,
        'verdant_froglight': 15, 'pearlescent_froglight': 15, 'lantern': 15, 'soul_lantern': 10, 'end_rod': 14,
        'crying_obsidian': 10, 'amethyst_cluster': 5, 'large_amethyst_bud': 4, 'medium_amethyst_bud': 2,
        'small_amethyst_bud': 1, 'magma_block': 3, 'lava_cauldron': 15, 'beacon': 15, 'glow_lichen': 7,
        'torch': 14, 'wall_torch': 14, 'soul_torch': 10, 'redstone_lamp': 15, 'jack_o_lantern': 15,
        'respawn_anchor': 0, 'copper_bulb': 15, 'exposed_copper_bulb': 12, 'weathered_copper_bulb': 8,
        'oxidized_copper_bulb': 4}


@lru_cache(maxsize=None)
def emission(state):
    if state is None:
        return 0
    n = core(state)
    if n.endswith('copper_bulb'):
        return EMIT.get(n, 0) if 'lit=true' in state else 0
    if n == 'light':
        return int(props(state).get('level', '15'))
    if n.endswith('candle'):
        return 3 * int(props(state).get('candles', '1')) if 'lit=true' in state else 0
    if n == 'redstone_lamp':
        return 15 if 'lit=true' in state else 0
    return EMIT.get(n, 0)


@lru_cache(maxsize=None)
def opaque(state):
    if state is None or passable(state):
        return False
    n = core(state)
    if n in SEE_THROUGH or 'glass' in n or 'grate' in n or n.endswith('_leaves'):
        return n == 'tinted_glass'
    return full(state)


def light(v):
    """Block light of every non-opaque position in the template (no sky, as in the Envés)."""
    lv = {}
    q = deque()
    for p, st in v.items():
        e = emission(st)
        if e:
            lv[p] = max(lv.get(p, 0), e)
            q.append(p)
    while q:
        p = q.popleft()
        level = lv[p]
        if level <= 1:
            continue
        x, y, z = p
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if not (0 <= n[0] < S and 0 <= n[1] < H and 0 <= n[2] < S):
                continue
            s = v.get(n)
            if opaque(s):
                continue
            if lv.get(n, 0) < level - 1:
                lv[n] = level - 1
                q.append(n)
    return lv


def light_report(v, markers=()):
    """(share of standing spots lit to 8 or more, darkest standing spot) for the review."""
    lv = light(v)
    nodes = walk_nodes(v, [(x, y, z) for (x, y, z, m) in markers])
    if not nodes:
        return 1.0, 15
    levels = [lv.get((x, y + 1, z), 0) for (x, y, z) in nodes]
    return sum(1 for l in levels if l >= 8) / len(levels), min(levels)
