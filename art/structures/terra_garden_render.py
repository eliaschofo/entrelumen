"""Review renders of Terra's garden (software renders through voxrender, never game captures).

Writes, to the folder given:
  garden_front_left.png, garden_front_right.png, garden_back.png: the built garden from three sides;
  garden_ghost_mock.png: a mock of the plan's ghost (Patchouli's multiblock view) on bare ground.
The core's lamp face is drawn on the side the core faces; spore blossoms and lanterns are sprites.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import voxrender  # noqa: E402
from voxkit import rot_state, tf  # noqa: E402

voxrender.CROSS = set(voxrender.CROSS) | {'spore_blossom'}
voxrender.SEE_THROUGH = set(voxrender.SEE_THROUGH) | {'ghost_placeholder'}
_base_sprite = voxrender._sprite


def _quadrant(tex, col, row):
    """One 8 x 8 quarter of a 16 x 16 texture, scaled back to 16 x 16: a sub-block shows its part of the face."""
    return tex.crop((col * 8, row * 8, col * 8 + 8, row * 8 + 8)).resize((16, 16), Image.NEAREST)


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


def _texture_for(state, face):
    """The texture a face of this block shows: the core's lamp on the side it looks at."""
    if state.startswith('entrelumen:terra_garden_core') and face.startswith(('left', 'right')):
        facing = 'south' if face.startswith('left') else 'east'
        if 'facing=' + facing in state:
            return voxrender._companion('terra_garden_core_front_growing')
    top, side = voxrender.face_textures(state)
    return voxrender._texture(top if face == 'top' else side)


def _sprite(state, face, s):
    if state.startswith('entrelumen:ghost_placeholder'):
        return Image.new('RGBA', (4 * s, 3 * s), (0, 0, 0, 0))
    base, sub = _sub(state)
    core_front = base.startswith('entrelumen:terra_garden_core') and face in ('left', 'right')
    if sub is None and not core_front or face == 'cross':
        return _base_sprite(base, face, s)
    key = (state, face, s)
    if key in voxrender._SPRITES:
        return voxrender._SPRITES[key]
    tex = _texture_for(base, face)
    if sub is not None:
        sx, sy, sz = sub
        col, row = {'top': (sx, sz), 'left': (sx, 1 - sy), 'right': (1 - sz, 1 - sy)}[face.split('_')[0]]
        tex = _quadrant(tex, col, row)
    if face == 'top':
        sp = tex.transform((4 * s, 2 * s), Image.AFFINE, (4 / s, 8 / s, -8, -4 / s, 8 / s, 8), Image.NEAREST)
    else:
        left = face.startswith('left')
        coeffs = (8 / s, 0, 0, -4 / s, 8 / s, 0) if left else (8 / s, 0, 0, 4 / s, 8 / s, -8)
        size = (2 * s, 3 * s)
        if face.endswith('_half'):
            tex = tex.crop((0, 8, 16, 16))
            size = (2 * s, 2 * s)
        sp = voxrender._shade(tex.transform(size, Image.AFFINE, coeffs, Image.NEAREST), 0.8 if left else 0.62)
    voxrender._SPRITES[key] = sp
    return sp


voxrender._sprite = _sprite
_base_mod = voxrender._mod
voxrender._name = lambda state: _sub(state)[0].split('[')[0].split(':')[1]


def turned(V, quarter):
    """The garden turned by quarter * 90 degrees about y (the voxkit transform keeps block states right)."""
    t = [(1, 1, False), (-1, 1, True), (-1, -1, False), (1, -1, True)][quarter % 4]
    out = {}
    for (x, y, z), b in V.items():
        p, q = tf(x, z, t)
        out[(p, y, q)] = rot_state(b, t)
    return out


def with_core_facing(V):
    """The core looks south in the drawing."""
    return {k: (b + '[facing=south]' if b == 'entrelumen:terra_garden_core' else b) for k, b in V.items()}


def fine(V):
    """Each block as 2 x 2 x 2 sub-blocks, so the arches' stairs and the eave's slabs read (ark_multiblock's
    subdivision); sprites and carpets keep one sub-block, which draws them at about their real size."""
    from ark_multiblock import subdivided
    out, rest = {}, {}
    for (x, y, z), b in V.items():
        name = b.split('[')[0].split(':')[1]
        if name in voxrender.CROSS:
            out[(2 * x, 2 * y + (1 if 'hanging=true' in b or name == 'spore_blossom' else 0), 2 * z)] = b
        elif name in voxrender.THIN:
            for sx in (0, 1):
                for sz in (0, 1):
                    out[(2 * x + sx, 2 * y, 2 * z + sz)] = b
        elif b.startswith('entrelumen:') or not name.endswith(('_stairs', '_slab')):
            for sx in (0, 1):
                for sy in (0, 1):
                    for sz in (0, 1):
                        out[(2 * x + sx, 2 * y + sy, 2 * z + sz)] = b
        else:
            rest[(x, y, z)] = b
    sub = {}
    for (x, y, z), t in subdivided(rest).items():
        t = t if ':' in t else 'minecraft:' + t
        tag = 'sub=%d%d%d' % (x % 2, y % 2, z % 2)
        sub[(x, y, z)] = t[:-1] + ',' + tag + ']' if t.endswith(']') else t + '[' + tag + ']'
    for (x, y, z), t in out.items():
        if t.startswith('entrelumen:') or t.split('[')[0].split(':')[1] in voxrender.THIN:
            tag = 'sub=%d%d%d' % (x % 2, y % 2, z % 2)
            t = t[:-1] + ',' + tag + ']' if t.endswith(']') else t + '[' + tag + ']'
        sub[(x, y, z)] = t
    # one sub-block up: the plinth stands on the grass
    return {(x, y + 1, z): (v if ':' in v else 'minecraft:' + v) for (x, y, z), v in sub.items()}


def render_all(V, folder):
    os.makedirs(folder, exist_ok=True)
    V = with_core_facing(V)
    views = [('garden_front_left.png', 0), ('garden_front_right.png', 3), ('garden_back.png', 2)]
    for name, quarter in views:
        voxrender.render(fine(turned(V, quarter)), os.path.join(folder, name), scale=5, ground=15)
    # the ghost mock: the built render blended over the same scene with every garden block invisible (an
    # empty sprite in the same places keeps the image bounds)
    full = voxrender.render(fine(turned(V, 0)), os.path.join(folder, '_ghost_full.png'), scale=5, ground=15)
    bare = voxrender.render({k: 'entrelumen:ghost_placeholder' for k in fine(turned(V, 0))}, os.path.join(folder, '_ghost_bare.png'), scale=5, ground=15)
    for tmp in ('_ghost_full.png', '_ghost_bare.png'):
        os.remove(os.path.join(folder, tmp))
    assert full.size == bare.size, (full.size, bare.size)
    full = full.convert('RGBA')
    bare = bare.convert('RGBA').resize(full.size)
    mock = Image.blend(bare, full, 0.45)
    d = ImageDraw.Draw(mock)
    font = ImageFont.load_default(size=22)
    title = "Terra's Hydroponic Garden"
    w = d.textlength(title, font=font)
    d.text(((mock.width - w) / 2, 18), title, font=font, fill=(255, 255, 255, 255), stroke_width=2, stroke_fill=(30, 30, 30, 255))
    bar = (mock.width / 2 - 120, 52, mock.width / 2 + 120, 60)
    d.rectangle(bar, fill=(20, 20, 20, 255))
    d.rectangle((bar[0], bar[1], bar[0] + 24, bar[3]), fill=(120, 220, 120, 255))
    note = 'Mock of the ghost (Patchouli view), software render'
    d.text((12, mock.height - 22), note, font=ImageFont.load_default(size=12), fill=(40, 40, 40, 255))
    mock.convert('RGB').save(os.path.join(folder, 'garden_ghost_mock.png'))
