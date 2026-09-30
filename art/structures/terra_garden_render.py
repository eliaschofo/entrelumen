"""Review renders of Terra's garden engine (software renders through voxrender, never game captures).

Writes, to the folder given:
  garden_front_left.png, garden_front_right.png, garden_back.png: the built engine from three sides;
  garden_ghost_mock.png: a mock of the plan's ghost (Patchouli's multiblock view) on bare ground.
Every block is split into 4 x 4 x 4 sub-blocks, each showing its own part of the face (textures keep their
density), so slabs read and the core's pot can stand on the mirror plane between two blocks, as its
model draws it. The crop is drawn as sprites in the pot.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import voxrender  # noqa: E402
from voxkit import rot_state, tf  # noqa: E402

N = 4                                    # sub-blocks per block edge
voxrender.SEE_THROUGH = set(voxrender.SEE_THROUGH) | {'ghost_placeholder'}
_base_sprite = voxrender._sprite
COMPANION = os.path.join(HERE, '..', '..', 'companion', 'src', 'main', 'resources', 'assets', 'entrelumen', 'textures', 'block')


def _part(tex, col, row):
    """One (16/N)-texel square of a 16 x 16 texture, scaled back to 16 x 16."""
    k = 16 // N
    return tex.crop((col * k, row * k, col * k + k, row * k + k)).resize((16, 16), Image.NEAREST)


def _sub(state):
    if '[' not in state or 'sub=' not in state:
        return state, None
    name, props = state[:-1].split('[', 1)
    kept, sub = [], None
    for p in props.split(','):
        if p.startswith('sub='):
            sub = tuple(int(c) for c in p[4:])
        else:
            kept.append(p)
    return (name + ('[' + ','.join(kept) + ']' if kept else '')), sub


def _own(name):
    path = os.path.join(COMPANION, name + '.png')
    if os.path.exists(path):
        im = Image.open(path).convert('RGBA')
        return im.crop((0, 0, 16, 16))
    return None


def _texture_for(state, face):
    if state.startswith('entrelumen:terra_garden_pot'):
        own = _own('terra_garden_core_soil' if face == 'top' else 'terra_garden_core_pot')
        if own is not None:
            return own
        return voxrender._texture('dirt' if face == 'top' else 'flower_pot')
    top, side = voxrender.face_textures(state)
    return voxrender._texture(top if face == 'top' else side)


ITEM_TEX = os.path.join(COMPANION, '..', 'item')
voxrender.CROSS = set(voxrender.CROSS) | {'terralight_crystal_0', 'terralight_crystal_1', 'terralight_crystal_2',
                                          'terralight_crystal_3', 'terralight_grounding_rod', 'terra_grow_lamp'}


def _cross_own(name, s):
    """Our own cross-drawn pieces: the crystal stages and the rod from their block textures, the lamp from its item."""
    path = os.path.join(ITEM_TEX if name == 'terra_grow_lamp' else COMPANION, name + '.png')
    im = Image.open(path).convert('RGBA').crop((0, 0, 16, 16))
    return im.resize((2 * s, 2 * s), Image.NEAREST)


def _sprite(state, face, s):
    if face == 'cross' and state.startswith('entrelumen:') and state.split('[')[0].split(':')[1] in voxrender.CROSS:
        key = (state, face, s)
        if key not in voxrender._SPRITES:
            voxrender._SPRITES[key] = _cross_own(state.split('[')[0].split(':')[1], s)
        return voxrender._SPRITES[key]
    if state.startswith('entrelumen:ghost_placeholder'):
        return Image.new('RGBA', (4 * s, 3 * s), (0, 0, 0, 0))
    base, sub = _sub(state)
    if sub is not None and base.split('[')[0].endswith(':glass') and face != 'top':
        return Image.new('RGBA', (4 * s, 3 * s), (0, 0, 0, 0))   # glass: only its top shell is drawn
    if sub is None or face == 'cross':
        return _base_sprite(base, face, s)
    key = (state, face, s)
    if key in voxrender._SPRITES:
        return voxrender._SPRITES[key]
    sx, sy, sz = sub
    col, row = {'top': (sx, sz), 'left': (sx, N - 1 - sy), 'right': (N - 1 - sz, N - 1 - sy)}[face.split('_')[0]]
    tex = _part(_texture_for(base, face), col, row)
    if face == 'top':
        sp = tex.transform((4 * s, 2 * s), Image.AFFINE, (4 / s, 8 / s, -8, -4 / s, 8 / s, 8), Image.NEAREST)
    else:
        left = face.startswith('left')
        coeffs = (8 / s, 0, 0, -4 / s, 8 / s, 0) if left else (8 / s, 0, 0, 4 / s, 8 / s, -8)
        sp = voxrender._shade(tex.transform((2 * s, 3 * s), Image.AFFINE, coeffs, Image.NEAREST), 0.8 if left else 0.62)
    voxrender._SPRITES[key] = sp
    return sp


voxrender._sprite = _sprite
voxrender._name = lambda state: _sub(state)[0].split('[')[0].split(':')[1]


def turned(V, quarter):
    """The engine turned by quarter * 90 degrees about y (voxkit keeps block states right)."""
    t = [(1, 1, False), (-1, 1, True), (-1, -1, False), (1, -1, True)][quarter % 4]
    out = {}
    for (x, y, z), b in V.items():
        p, q = tf(x, z, t)
        out[(p, y, q)] = rot_state(b, t) if not b.startswith('entrelumen:terra_garden_core') else b + '#%d' % (quarter % 4)
    return out


def _tag(state, x, y, z):
    tag = 'sub=%d%d%d' % (x % N, y % N, z % N)
    return state[:-1] + ',' + tag + ']' if state.endswith(']') else state + '[' + tag + ']'


def fine(V, crop='minecraft:wheat'):
    """Every block as N x N x N sub-blocks; slabs keep their lower half; the core becomes its pot on the
    mirror plane (the drawing's west edge of the core, turned with the engine) with the crop in it."""
    out = {}
    for (x, y, z), b in V.items():
        name = b.split('[')[0]
        if name.startswith('entrelumen:terra_garden_core'):
            quarter = int(b.split('#')[1]) if '#' in b else 0
            t = [(1, 1, False), (-1, 1, True), (-1, -1, False), (1, -1, True)][quarter]
            dx, dz = tf(-1, 0, t)
            cx, cz = N * x + N // 2 + dx * N // 2, N * z + N // 2 + dz * N // 2
            for px in (cx - 1, cx):
                for pz in (cz - 1, cz):
                    out[(px, N * y, pz)] = _tag('entrelumen:terra_garden_pot', px, N * y, pz)
                    out[(px, N * y + 1, pz)] = crop
            continue
        top = N // 2 if name.endswith('_slab') and 'type=bottom' in b else N
        low = N - 1 if name.endswith(':glass') else 0     # glass: one thin top shell, no inner faces
        for sx in range(N):
            for sy in range(low, top):
                for sz in range(N):
                    p = (N * x + sx, N * y + sy, N * z + sz)
                    out[p] = _tag(b if name.endswith('_slab') is False else name.replace('_slab', ''), *p)
    # one sub-block up: the sump stands on the grass
    return {(x, y + 1, z): v for (x, y, z), v in out.items()}


def render_all(V, folder):
    os.makedirs(folder, exist_ok=True)
    scale, ground = 7, 12
    render_crystal_setup(folder)
    views = [('garden_front_left.png', 0), ('garden_front_right.png', 3), ('garden_back.png', 2)]
    for name, quarter in views:
        voxrender.render(fine(turned(V, quarter)), os.path.join(folder, name), scale=scale, ground=ground)
    # a cutaway: the front-right corner column and the skylight lifted off, to show the heart
    cut = {k: v for k, v in V.items() if not (k[0] == 1 and k[2] == 1 and k[1] in (1, 2, 3)) and 'glass' not in v}
    voxrender.render(fine(turned(cut, 0)), os.path.join(folder, 'garden_cutaway.png'), scale=scale + 2, ground=ground)
    # the ghost mock: the built render blended over the same scene with every block invisible (an empty
    # sprite in the same places keeps the image bounds)
    engine = fine(turned(V, 0))
    full = voxrender.render(engine, os.path.join(folder, '_ghost_full.png'), scale=scale, ground=ground)
    bare = voxrender.render({k: 'entrelumen:ghost_placeholder' for k in engine}, os.path.join(folder, '_ghost_bare.png'),
                            scale=scale, ground=ground)
    for tmp in ('_ghost_full.png', '_ghost_bare.png'):
        os.remove(os.path.join(folder, tmp))
    assert full.size == bare.size, (full.size, bare.size)
    mock = Image.blend(bare.convert('RGBA'), full.convert('RGBA'), 0.45)
    d = ImageDraw.Draw(mock)
    font = ImageFont.load_default(size=22)
    title = "Terra's Hydroponic Garden"
    w = d.textlength(title, font=font)
    d.text(((mock.width - w) / 2, 18), title, font=font, fill=(255, 255, 255, 255), stroke_width=2, stroke_fill=(30, 30, 30, 255))
    bar = (mock.width / 2 - 120, 52, mock.width / 2 + 120, 60)
    d.rectangle(bar, fill=(20, 20, 20, 255))
    d.rectangle((bar[0], bar[1], bar[0] + 24, bar[3]), fill=(120, 220, 120, 255))
    d.text((12, mock.height - 22), 'Mock of the ghost (Patchouli view), software render', font=ImageFont.load_default(size=12),
           fill=(40, 40, 40, 255))
    mock.convert('RGB').save(os.path.join(folder, 'garden_ghost_mock.png'))


def render_crystal_setup(folder):
    """The Terralight setup, block scale: column A (grass, the crystal, the lamp) beside column B (dirt, the
    rod, two cables; copper blocks stand in for any mod's energy cable), with the four stages beside it."""
    V = {(0, 0, 0): 'minecraft:dirt', (0, 1, 0): 'entrelumen:terralight_grounding_rod',
         (0, 2, 0): 'minecraft:oxidized_copper', (0, 3, 0): 'minecraft:oxidized_copper',
         (1, 0, 0): 'minecraft:dirt', (1, 1, 0): 'minecraft:grass_block', (1, 2, 0): 'entrelumen:terralight_crystal_3',
         (1, 3, 0): 'entrelumen:terra_grow_lamp'}
    for i in range(4):
        V[(3 + i, 0, 2)] = 'minecraft:grass_block'
        V[(3 + i, 1, 2)] = 'entrelumen:terralight_crystal_%d' % i
    V = {(x, y + 1, z): b for (x, y, z), b in V.items()}
    voxrender.render(V, os.path.join(folder, 'terralight_setup.png'), scale=18, ground=4)
