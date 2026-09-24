"""Runtime access to palette.json: block states for generators and preview images for voxrender.

    from modblocks import palette as mp
    lamp = mp.state('mcwlights:classic_street_lamp', part='base')    # full default state + overrides
    box = mp.state('supplementaries:flower_box', facing='south')
    mp.ids(category='planter')                                        # every planter id
    v.sym(5, 1, 0, box)                                               # voxkit re-orients facing & co.
    v = {k: mp.normalize(b) for k, b in v.items()}                    # fold axis-like facings back

Texture roots: `textures/` next to this file (redistributable mods), then ENTRELUMEN_MODTEX or the
local folder named in palette.json (the rest), then voxkit.TEX for vanilla textures.
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_P = None
_BY_ID = {}
_IMAGES = {}
GRASS, FOLIAGE = (145, 189, 89), (119, 171, 47)


def data():
    global _P
    if _P is None:
        path = os.path.join(HERE, 'palette.json')
        _P = {'blocks': [], 'textures': {}, 'textureRoots': {'repo': 'textures', 'local': ''}}
        if os.path.exists(path):
            with open(path, encoding='utf-8') as f:
                _P = json.load(f)
        _BY_ID.update({b['id']: b for b in _P['blocks']})
    return _P


def block(state_or_id):
    """The palette record for an id or a full state string, or None."""
    data()
    return _BY_ID.get(state_or_id.split('[')[0])


def ids(category=None, mod=None):
    return [b['id'] for b in data()['blocks']
            if (category is None or b['category'] == category) and (mod is None or b['mod'] == mod)]


def parse(state):
    name, _, rest = state.partition('[')
    props = dict(p.split('=') for p in rest.rstrip(']').split(',') if p) if rest else {}
    return name, props


def state(block_id, **props):
    """Default state of a palette block with some properties replaced; unknown names or values raise."""
    rec = block(block_id)
    if rec is None:
        raise KeyError('%s is not in the mod palette' % block_id)
    name, current = parse(rec['default'])
    for k, v in props.items():
        v = str(v).lower()
        if k not in rec['properties']:
            raise KeyError('%s has no property %s (has %s)' % (block_id, k, ', '.join(rec['properties'])))
        if v not in rec['properties'][k]:
            raise ValueError('%s.%s=%s not in %s' % (block_id, k, v, rec['properties'][k]))
        current[k] = v
    return name + ('[' + ','.join('%s=%s' % kv for kv in sorted(current.items())) + ']' if current else '')


OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def normalize(state):
    """Fold values that a rotation made invalid back into the block's own set (e.g. windows whose
    facing is an axis: south -> north, west -> east). Vanilla and unknown states pass through."""
    rec = block(state)
    if rec is None or '[' not in state:
        return state
    name, props = parse(state)
    for k, v in props.items():
        allowed = rec['properties'].get(k)
        if allowed and v not in allowed and OPPOSITE.get(v) in allowed:
            props[k] = OPPOSITE[v]
    return name + '[' + ','.join('%s=%s' % kv for kv in sorted(props.items())) + ']'


# ---------------- preview images ----------------
def _roots():
    p = data()
    local = os.environ.get('ENTRELUMEN_MODTEX') or p['textureRoots']['local']
    return {'repo': os.path.join(HERE, p['textureRoots']['repo']), 'local': local}


def _vanilla(path):
    import voxkit
    return os.path.join(voxkit.TEX, path.split('/', 1)[-1] + '.png')


def texture_file(tid):
    info = data()['textures'].get(tid)
    if info is None:
        return None
    if info['root'] == 'vanilla':
        return _vanilla(tid.split(':', 1)[1])
    return os.path.join(_roots()[info['root']], info['file'])


def _open(tid):
    path = texture_file(tid)
    try:
        im = Image.open(path).convert('RGBA')
    except Exception:
        return None
    if im.height > im.width:                               # animated strip: first frame
        im = im.crop((0, 0, im.width, im.width))
    return im


def image(key):
    """Image for a voxrender texture key '@<id>#<role>' (role: top, side, sprite). Faces come back
    16x16 (UV region of texture sheets applied, grass/foliage tint applied); sprites keep their size. A missing texture
    returns a magenta square, like voxrender does for vanilla."""
    if key in _IMAGES:
        return _IMAGES[key]
    bid, _, role = key[1:].partition('#')
    rec = block(bid) or {}
    if role == 'side' and rec.get('sprite') and rec.get('render') in ('pane', 'cube') and not rec.get('solid'):
        role = 'sprite'                        # panels and furniture show their front view on the sides
    if role == 'top' and rec.get('spriteTop'):
        role = 'spriteTop'                     # partial blocks show their top view
    spec = rec.get(role) or (rec.get('icon') or rec.get('side') if role == 'sprite' else None)
    im = _open(spec['texture']) if spec else None
    if im is None:
        im = Image.new('RGBA', (16, 16), (200, 60, 200, 255))
    elif role == 'spriteTop' or (spec is rec.get('sprite') and key.endswith('#side')):
        im = im.resize((16, 16), Image.NEAREST)
    elif role != 'sprite' or spec is not rec.get('sprite'):
        if 'uv' in spec:
            k = im.width / 16
            u0, v0, u1, v1 = spec['uv']
            box = (int(min(u0, u1) * k), int(min(v0, v1) * k), int(max(u0, u1) * k), int(max(v0, v1) * k))
            if box[2] > box[0] and box[3] > box[1]:
                im = im.crop(box)
        im = im.resize((16, 16), Image.NEAREST)
        if spec.get('tint'):
            r, g, b = FOLIAGE if spec['tint'] == 'foliage' else GRASS
            px = im.load()
            for i in range(16):
                for j in range(16):
                    p = px[i, j]
                    px[i, j] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
    _IMAGES[key] = im
    return im


def faces(state):
    """(top, side) voxrender texture keys for a palette state."""
    bid = state.split('[')[0]
    return '@%s#top' % bid, '@%s#side' % bid


def sprite(state):
    return '@%s#sprite' % state.split('[')[0]
