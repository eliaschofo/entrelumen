"""Build palette.json (and extract the preview textures) for the decorative mod blocks in selection.py.

Everything is read from the pack's JARs, never downloaded:
  * the block's properties and default state from its bytecode (jvm.py), cross-checked against the
    blockstate JSON; blocks registered data-driven (Chipped, Rechiseled) use their base vanilla class;
  * the models for that default state, following parents and #texture variables down to the PNG;
  * a preview shape (cube, slab, thin, pane, cross) from the model elements.

Textures of mods whose licence allows redistribution go to modblocks/textures/<ns>/<path>.png in the
repo; the rest go to LOCAL_TEX outside the repo (preview on this machine only).

    python art/structures/modblocks/build_palette.py [--catalog DIR] [--check]
"""
import argparse
import io
import json
import os
import re
import subprocess
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from jvm import Classes, ClassFile, registrations  # noqa: E402
from selection import S  # noqa: E402

S_OPTS = {(m, b): o for m, b, c, l, o in S}

REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
REPO_TEX = os.path.join(HERE, 'textures')
LOCAL_TEX = os.environ.get('ENTRELUMEN_MODTEX', 'E:/Elias/Codex/Entrelumen-ssd/modblocks-textures')
VANILLA_CLIENT = os.environ.get('ENTRELUMEN_MC_CLIENT', 'E:/curseforge/Install/versions/1.21.1/1.21.1.jar')
VANILLA_SERVER = os.environ.get('ENTRELUMEN_MC_SERVER', 'E:/Elias/Codex/Entrelumen-ssd/server-slice/libraries/net/'
                                'minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar')
sys.path.insert(0, os.path.dirname(HERE))
from voxkit import TEX as VANILLA_TEX  # noqa: E402

PERMISSIVE = {'MIT', 'Apache-2.0', 'CC0-1.0', 'Unlicense'}          # redistributable with a notice
COPYLEFT = {'LGPL v3', 'LGPL-3.0', 'LGPL-3.0-only'}                  # would need the full licence texts
MIT_TEXT = '''Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
'''
DIRECTIONAL = ('facing', 'north', 'south', 'east', 'west', 'half', 'hinge', 'axis')
HANDLED_BY_ORIENT = {'facing', 'north', 'south', 'east', 'west', 'axis', 'hinge', 'shape'}
CONNECTS = re.compile(r'fence|wall|pane|hedge|railing|lattice|bars|balcony')
DIRWORD = re.compile(r'(^|_)(north|south|east|west|ns|ew|ne|nw|se|sw|n|s|e|w|x|z)($|_)')
V = 'net/minecraft/world/level/block/'


# ---------------- catalog ----------------
def catalog_dir(arg):
    cands = [arg] if arg else []
    cands.append(os.path.join(REPO, 'catalog'))
    try:                                   # a worktree: the untracked local-paths.json lives in the main checkout
        common = subprocess.run(['git', '-C', REPO, 'rev-parse', '--git-common-dir'], capture_output=True,
                                text=True, check=True).stdout.strip()
        cands.append(os.path.join(os.path.dirname(os.path.abspath(os.path.join(REPO, common))), 'catalog'))
    except (OSError, subprocess.CalledProcessError):
        pass
    for c in cands:
        if c and os.path.exists(os.path.join(c, 'local-paths.json')):
            return c
    raise SystemExit('catalog/local-paths.json not found; pass --catalog')


def read_json(path):
    with open(path, encoding='utf-8-sig') as f:
        return json.load(f)


class Pack:
    def __init__(self, catalog):
        self.paths = read_json(os.path.join(catalog, 'local-paths.json'))
        self.lock = read_json(os.path.join(catalog, 'curated.json'))
        self.by_id = {}
        for e in self.lock['mods']:
            for m in e['metadata']['mods']:
                self.by_id[m['id']] = (e, m)
        self.zips = {}
        self.classes = {}

    def jar(self, mod):
        e, m = self.by_id[mod]
        return self.paths[e['filename']]

    def zip(self, path):
        if path not in self.zips:
            self.zips[path] = zipfile.ZipFile(path)
        return self.zips[path]

    def deps(self, mod):
        out, seen, queue = [], set(), [mod]
        while queue:
            x = queue.pop(0)
            if x in seen or x not in self.by_id:
                continue
            seen.add(x)
            e, _ = self.by_id[x]
            out.append(self.paths[e['filename']])
            queue.extend(d['id'] for d in e['metadata']['dependencies'] if d.get('required', True))
        return out

    def classpath(self, mod):
        if mod not in self.classes:
            self.classes[mod] = Classes(self.deps(mod) + [VANILLA_SERVER])
        return self.classes[mod]

    def asset_jars(self, mod):
        return self.deps(mod) + [VANILLA_CLIENT]

    def read_asset(self, mod, path):
        for j in self.asset_jars(mod):
            try:
                return self.zip(j).read(path)
            except KeyError:
                continue
        return None

    def reserved_assets(self, mod):
        """Model or texture metadata in the JAR claiming 'All Rights Reserved' despite the JAR licence."""
        z = self.zip(self.jar(mod))
        hits = []
        for n in z.namelist():
            if n.startswith('assets/') and n.endswith(('.json', '.mcmeta')) and '/models/' in n:
                if b'All Rights Reserved' in z.read(n):
                    hits.append(n)
        return hits

    def license(self, mod):
        """(licence, licence files, authors, url) from the JAR's mods.toml."""
        z = self.zip(self.jar(mod))
        for n in z.namelist():
            if n.endswith('mods.toml'):
                txt = z.read(n).decode('utf-8', 'replace')
                m = re.search(r'license\s*=\s*"([^"]*)"', txt)
                if m:
                    files = [x for x in z.namelist() if re.search(r'(^|/)LICEN[CS]E[^/]*$', x) and not x.endswith('.class')]
                    field = lambda k: (re.search(k + r'\s*=\s*"([^"]*)"', txt) or [None, None])[1]
                    return m.group(1), files, field('authors'), field('displayURL')
        return 'unknown', [], None, None


def loads(data):
    txt = data.decode('utf-8-sig', 'replace')
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        return json.loads(re.sub(r',(\s*[}\]])', r'\1', txt))


# ---------------- block states ----------------
def state_for(pack, mod, bid, bs):
    """(properties, default, source) for a block."""
    cl = pack.classpath(mod)
    z = pack.zip(pack.jar(mod))
    if not hasattr(pack, '_reg'):
        pack._reg = {}
    if mod not in pack._reg:
        wanted = {b for m, b, *_ in S if m == mod}
        reg = {}
        for n in z.namelist():
            if not n.endswith('.class'):
                continue
            data = z.read(n)
            if any(w.encode() in data or w.upper().encode() in data for w in wanted):
                for k, v in registrations(cl, ClassFile(data), wanted).items():
                    reg.setdefault(k, v)
        pack._reg[mod] = reg
    target = pack._reg[mod].get(bid)
    source = 'bytecode'
    if target is None and S_OPTS.get((mod, bid), {}).get('cls'):
        target, source = S_OPTS[(mod, bid)]['cls'], 'selection'
    if target is None:
        target, source = named_class(pack, mod, bid), 'class-name'
    if target is None:
        target, source = inferred_class(mod, bid, bs), 'base-class'
    if target is None:
        return None
    props, default = cl.state_definition(*target) if isinstance(target, tuple) else cl.state_definition(target)
    cls = target[0] if isinstance(target, tuple) else target
    return props, default, source, cls


COLOURS = ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan',
           'purple', 'blue', 'brown', 'green', 'red', 'black')


def named_class(pack, mod, bid):
    """Blocks registered in loops (one per dye colour): find <Stem>Block by name in the mod JAR."""
    stem = bid
    for c in sorted(COLOURS, key=len, reverse=True):
        if stem.endswith('_' + c):
            stem = stem[:-len(c) - 1]
            break
        if stem.startswith(c + '_'):
            stem = stem[len(c) + 1:]
            break
    else:
        return None
    want = '/' + ''.join(w.capitalize() for w in stem.split('_')) + 'Block.class'
    cl = pack.classpath(mod)
    hits = [n[:-6] for n in pack.zip(pack.jar(mod)).namelist() if n.endswith(want)]
    hits = [h for h in hits if cl.is_block(h)]
    return hits[0] if len(hits) == 1 else None


def inferred_class(mod, bid, bs):
    """Data-driven registries (Chipped, Rechiseled) reuse the class of their base block."""
    keys = bs_keys(bs)
    if mod == 'chipped':
        if '_pane' in bid:
            return V + 'IronBarsBlock'
        if 'lantern' in bid and 'facing' in keys:
            return 'earth/terrarium/chipped/common/blocks/SpecialLanternBlock'
        if 'froglight' in bid or 'axis' in keys:
            return V + 'RotatedPillarBlock'
        return V + 'Block'
    if mod == 'rechiseled':
        if 'axis' in keys:
            return 'com/supermartijn642/rechiseled/blocks/RechiseledPillarBlock'
        if 'glass' in bid:
            return 'com/supermartijn642/rechiseled/blocks/RechiseledGlassBlock'
        return 'com/supermartijn642/rechiseled/blocks/RechiseledBlock'
    return None


def bs_keys(bs):
    keys = {}
    for k in (bs.get('variants') or {}):
        for part in filter(None, k.split(',')):
            p, v = part.split('=')
            keys.setdefault(p, set()).add(v)

    def walk(w):
        if not isinstance(w, dict):
            return
        for k, v in w.items():
            if k in ('OR', 'AND'):
                for x in v:
                    walk(x)
            else:
                keys.setdefault(k, set()).update(str(v).lower().split('|'))
    for part in bs.get('multipart') or []:
        walk(part.get('when'))
    return keys


def when_ok(w, state):
    if w is None:
        return True
    if 'OR' in w:
        return any(when_ok(x, state) for x in w['OR'])
    if 'AND' in w:
        return all(when_ok(x, state) for x in w['AND'])
    for k, v in w.items():
        vals = str(v).lower().split('|')
        neg = vals[0].startswith('!')
        if neg:
            vals[0] = vals[0][1:]
        if (state.get(k) in vals) == neg:
            return False
    return True


def models_for(bs, state):
    out = []
    if 'variants' in bs:
        for key, v in bs['variants'].items():
            conds = dict(p.split('=') for p in filter(None, key.split(',')))
            if all(state.get(k) == val for k, val in conds.items()):
                v = v[0] if isinstance(v, list) else v
                out.append((v['model'], v.get('x', 0), v.get('y', 0)))
                break
    for part in bs.get('multipart') or []:
        if when_ok(part.get('when'), state):
            a = part['apply']
            a = a[0] if isinstance(a, list) else a
            out.append((a['model'], a.get('x', 0), a.get('y', 0)))
    return out


# ---------------- models ----------------
def rl(s, default_ns='minecraft'):
    return s if ':' in s else default_ns + ':' + s


def load_model(pack, mod, mid):
    ns, path = rl(mid).split(':', 1)
    data = pack.read_asset(mod, 'assets/%s/models/%s.json' % (ns, path))
    return loads(data) if data else None


def resolve_model(pack, mod, mid):
    """Merge a model with its parents: textures (child wins), the nearest elements, and flags."""
    textures, elements, chain, loader, generated = {}, None, [], None, False
    cur = mid
    while cur and len(chain) < 24:
        chain.append(cur)
        if cur.split(':')[-1] in ('builtin/generated', 'item/generated', 'item/handheld'):
            generated = True
            break
        if cur.split(':')[-1] == 'builtin/entity':
            break
        m = load_model(pack, mod, cur)
        if m is None:
            break
        for k, v in (m.get('textures') or {}).items():
            textures.setdefault(k, v)
        own = m.get('elements') or m.get('components')        # framework:open_model calls them components
        if elements is None and own:
            elements = own
        loader = loader or m.get('loader')
        inner = next((v for k, v in m.items() if isinstance(v, dict) and k != 'textures'
                      and ('parent' in v or 'elements' in v)), None)
        if elements is None and inner is not None:              # loaders wrapping a model ("model", "rope"...)
            for k, v in (inner.get('textures') or {}).items():
                textures.setdefault(k, v)
            if inner.get('elements'):
                elements = inner['elements']
            cur = inner.get('parent')
            continue
        nxt = m.get('parent')
        if nxt is None and m.get('loader') and elements is None:
            # custom loaders name their base model under their own keys ("box", "base", ...)
            refs = [v for k, v in m.items() if k not in ('loader', 'textures') and isinstance(v, str)
                    and '/' in v and load_model(pack, mod, v) is not None]
            nxt = refs[0] if refs else None
        cur = nxt
    return {'textures': textures, 'elements': elements or [], 'chain': chain, 'loader': loader,
            'generated': generated}


def tex_ref(textures, ref, depth=0):
    if ref is None or depth > 12:
        return None
    if ref.startswith('#'):
        return tex_ref(textures, textures.get(ref[1:]), depth + 1)
    return rl(ref)


ROT_Y = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}
ROT_X = {'up': 'north', 'north': 'down', 'down': 'south', 'south': 'up', 'east': 'east', 'west': 'west'}


def rotate_face(face, x, y):
    for _ in range((x // 90) % 4):
        face = ROT_X[face]
    for _ in range((y // 90) % 4):
        face = ROT_Y[face]
    return face


def rotate_box(f, t, x, y):
    pts = [(a, b, c) for a in (f[0], t[0]) for b in (f[1], t[1]) for c in (f[2], t[2])]
    for _ in range((x // 90) % 4):
        pts = [(a, 16 - c, b) for a, b, c in pts]
    for _ in range((y // 90) % 4):
        pts = [(16 - c, b, a) for a, b, c in pts]
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


def default_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0], 'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0],
            'south': [x0, 16 - y1, x1, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0],
            'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0]}[face]


def analyse(pack, mod, models):
    """Top/side textures, bounding box, volume and the list of faces of the default-state models."""
    top, side, faces = {}, {}, []
    lo, hi, vol = [16, 16, 16], [0, 0, 0], 0.0
    particle = None
    for mid, rx, ry in models:
        m = resolve_model(pack, mod, mid)
        particle = particle or tex_ref(m['textures'], m['textures'].get('particle'))
        if not m['elements'] and m['textures']:
            # no geometry (loader / entity model): fall back to the named textures
            for name in ('top', 'up', 'end', 'all', 'texture', 'side', 'particle'):
                t = tex_ref(m['textures'], m['textures'].get(name))
                if t:
                    (top if name in ('top', 'up', 'end') else side).setdefault(t, [0, None, -1, None])
        for e in m['elements']:
            f, t = e['from'], e['to']
            a, b = rotate_box(f, t, rx, ry)
            lo = [min(lo[i], a[i]) for i in range(3)]
            hi = [max(hi[i], b[i]) for i in range(3)]
            vol += max(0, t[0] - f[0]) * max(0, t[1] - f[1]) * max(0, t[2] - f[2])
            for face, spec in (e.get('faces') or {}).items():
                tr = tex_ref(m['textures'], spec.get('texture'))
                if not tr:
                    continue
                dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
                area = {'up': dx * dz, 'down': dx * dz, 'north': dx * dy, 'south': dx * dy,
                        'east': dz * dy, 'west': dz * dy}[face]
                uv = spec.get('uv') or default_uv(face, f, t)
                tint = 'tintindex' in spec
                d = rotate_face(face, rx, ry)
                faces.append({'dir': d, 'from': a, 'to': b, 'texture': tr, 'uv': uv, 'tint': tint})
                bucket = top if d == 'up' else side if d != 'down' else None
                if bucket is None:
                    continue
                cur = bucket.setdefault(tr, [0, None, -1, tint])
                cur[0] += area
                if area > cur[2]:
                    bucket[tr] = [cur[0], uv, area, tint]
    pick = lambda b: max(b.items(), key=lambda kv: kv[1][0]) if b else None
    tp, sd = pick(top), pick(side)
    if sd is None and tp is None and particle:
        sd = (particle, [0, None, -1, None])
    if sd is None:
        sd = tp
    if tp is None:
        tp = sd
    box = {'from': lo, 'to': hi} if faces else None
    return tp, sd, box, vol, faces


def front_sprite(pack, mod, faces, box, px=2):
    """Orthographic front view, 16*px square: the sprite a thin or odd-shaped block is drawn with, and
    the side of panels and furniture. Seen from the south, or from the east when the block is deeper
    than wide. Farther faces first."""
    im = Image.new('RGBA', (16 * px, 16 * px))
    east = box and (box['to'][2] - box['from'][2]) > (box['to'][0] - box['from'][0]) + 1
    if east:        # rotate the east view into the south one: x' = 16 - z, z' = x
        faces = [dict(f, dir='south', **{'from': [16 - f['to'][2], f['from'][1], f['from'][0]],
                                        'to': [16 - f['from'][2], f['to'][1], f['to'][0]]})
                 for f in faces if f['dir'] == 'east']
    for fc in sorted((f for f in faces if f['dir'] == 'south'), key=lambda f: (f['to'][2], f['from'][2])):
        tex, _ = texture_image(pack, mod, fc['texture'])
        if tex is None:
            continue
        k = tex.width / 16
        u0, v0, u1, v1 = fc['uv']
        crop = tex.crop((int(min(u0, u1) * k), int(min(v0, v1) * k), max(int(max(u0, u1) * k), int(min(u0, u1) * k) + 1),
                         max(int(max(v0, v1) * k), int(min(v0, v1) * k) + 1)))
        if u0 > u1:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        if v0 > v1:
            crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
        x0, x1 = round(fc['from'][0] * px), round(fc['to'][0] * px)
        y0, y1 = round((16 - fc['to'][1]) * px), round((16 - fc['from'][1]) * px)
        if x1 <= x0 or y1 <= y0:
            continue
        crop = crop.resize((x1 - x0, y1 - y0), Image.NEAREST)
        if fc['tint']:
            crop = tinted(crop, fc['texture'])
        im.alpha_composite(crop, (x0, y0))
    return im if im.getbbox() else None


def top_sprite(pack, mod, faces, px=2):
    """Orthographic view from above (north up), 16*px square: the top of panels, furniture, bridges and
    other partial blocks, so a fence shows a rail and not a lid. Lower faces first."""
    im = Image.new('RGBA', (16 * px, 16 * px))
    for fc in sorted((f for f in faces if f['dir'] == 'up'), key=lambda f: (f['to'][1], f['from'][1])):
        tex, _ = texture_image(pack, mod, fc['texture'])
        if tex is None:
            continue
        k = tex.width / 16
        u0, v0, u1, v1 = fc['uv']
        crop = tex.crop((int(min(u0, u1) * k), int(min(v0, v1) * k), max(int(max(u0, u1) * k), int(min(u0, u1) * k) + 1),
                         max(int(max(v0, v1) * k), int(min(v0, v1) * k) + 1)))
        if u0 > u1:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        if v0 > v1:
            crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
        x0, x1 = round(max(0, fc['from'][0]) * px), round(min(16, fc['to'][0]) * px)
        z0, z1 = round(max(0, fc['from'][2]) * px), round(min(16, fc['to'][2]) * px)
        if x1 <= x0 or z1 <= z0:
            continue
        crop = crop.resize((x1 - x0, z1 - z0), Image.NEAREST)
        if fc['tint']:
            crop = tinted(crop, fc['texture'])
        im.alpha_composite(crop, (x0, z0))
    return im if im.getbbox() else None


GRASS, FOLIAGE = (145, 189, 89), (119, 171, 47)


def tint_kind(tid):
    return 'foliage' if any(w in tid for w in ('leaves', 'hedge', 'vine', 'azalea')) else 'grass'


def tinted(im, tid):
    r, g, b = FOLIAGE if tint_kind(tid) == 'foliage' else GRASS
    px = im.load()
    for i in range(im.width):
        for j in range(im.height):
            p = px[i, j]
            px[i, j] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
    return im


def shape(box, vol):
    """Preview shape from the bounding box: cube, slab (bottom half), thin (floor layer), pane (a
    wall-like panel drawn with its front view) or cross (a sprite for small or odd objects)."""
    if not box:
        return 'cross'
    (x0, y0, z0), (x1, y1, z1) = box['from'], box['to']
    wx, h, wz = x1 - x0, y1 - y0, z1 - z0
    if wx >= 12 and wz >= 12:
        if y1 <= 4.5:
            return 'thin'
        if y1 <= 9 and y0 < 1:
            return 'slab'
        return 'cube'
    if h >= 10 and max(wx, wz) >= 12:
        return 'pane'
    return 'cross'


def item_icon(pack, mod, bid):
    ns = mod
    m = resolve_model(pack, mod, '%s:item/%s' % (ns, bid))
    if m['generated']:
        return tex_ref(m['textures'], m['textures'].get('layer0'))
    return None


# ---------------- textures ----------------
def texture_image(pack, mod, tid):
    ns, path = tid.split(':', 1)
    data = pack.read_asset(mod, 'assets/%s/textures/%s.png' % (ns, path))
    if data is None:
        return None, None
    im = Image.open(io.BytesIO(data)).convert('RGBA')
    info = {'size': list(im.size)}
    if im.height > im.width and im.height % im.width == 0:
        info['frames'] = im.height // im.width
        im = im.crop((0, 0, im.width, im.width))
    return im, info


def write_notice(mods):
    """textures/LICENSES.txt: the notices the in-repo textures carry."""
    lines = ['Textures in this folder are copied unmodified (first animation frame only) from the mod JARs',
             'below, or derived from their block models (sprite/), for local structure previews only.',
             'Each mod keeps its own licence; they are not covered by the ENTRELUMEN licence.', '']
    for mod, m in sorted(mods.items()):
        if m['redistributable']:
            lines += ['== %s %s (%s) %s' % (m['name'], m['version'], m['jar'], m['url'] or ''),
                      'Licence declared in the JAR: %s' % m['license'], '',
                      'Copyright (c) %s' % (m['authors'] or m['name']), '',
                      MIT_TEXT if m['license'] == 'MIT' else '']
    lines += ['Kept outside the repo (%s), preview on this machine only:' % LOCAL_TEX]
    for mod, m in sorted(mods.items()):
        if not m['redistributable']:
            lines.append('  %s %s: %s%s' % (m['name'], m['version'], m['license'],
                                           ' (%s)' % m['licenseNote'] if m.get('licenseNote') else ''))
    os.makedirs(REPO_TEX, exist_ok=True)
    with open(os.path.join(REPO_TEX, 'LICENSES.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines).rstrip() + '\n')


def save_png(im, root, rel):
    dest = os.path.join(REPO_TEX if root == 'repo' else LOCAL_TEX, rel)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    im.save(dest)


def state_string(bid_full, state):
    if not state:
        return bid_full
    return bid_full + '[' + ','.join('%s=%s' % kv for kv in sorted(state.items())) + ']'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--catalog')
    ap.add_argument('--check', action='store_true', help='analyse only, write nothing')
    args = ap.parse_args()
    pack = Pack(catalog_dir(args.catalog))

    mods, textures, blocks, problems = {}, {}, [], []
    for mod, bid, cat, label, opts in S:
        if mod not in pack.by_id:
            problems.append('%s not in catalog' % mod)
            continue
        e, meta = pack.by_id[mod]
        if mod not in mods:
            lic, files, authors, url = pack.license(mod)
            reserved = pack.reserved_assets(mod) if lic in PERMISSIVE else []
            ok = lic in PERMISSIVE and not reserved
            mods[mod] = {'jar': e['filename'], 'version': meta.get('version'), 'name': meta.get('displayName') or mod,
                         'authors': authors, 'url': url, 'license': lic, 'licenseFiles': files,
                         'redistributable': ok, 'textures': 'repo' if ok else 'local'}
            if lic in COPYLEFT:
                mods[mod]['licenseNote'] = ('copyleft: redistributing needs the full LGPL/GPL texts next to the '
                                            'files; kept out of the repo')
            if reserved:
                mods[mod]['licenseNote'] = ('%d model files say "All Rights Reserved" (e.g. %s); kept out of the '
                                            'repo to be safe' % (len(reserved), reserved[0]))
        raw = pack.read_asset(mod, 'assets/%s/blockstates/%s.json' % (mod, bid))
        if raw is None:
            problems.append('%s:%s has no blockstate' % (mod, bid))
            continue
        bs = loads(raw)
        found = state_for(pack, mod, bid, bs)
        if found is None:
            problems.append('%s:%s: no block class found' % (mod, bid))
            continue
        props, default, source, cls = found
        pnames = [p for p, _ in props]
        # cross-check against the blockstate JSON
        for k, vals in bs_keys(bs).items():
            if k not in pnames:
                problems.append('%s:%s blockstate uses %s, not in class %s' % (mod, bid, k, cls))
            else:
                allowed = dict(props)[k]
                bad = {v for v in vals if v not in allowed and not v.startswith('!')}
                if bad and '?' not in allowed:
                    problems.append('%s:%s %s values %s not in %s' % (mod, bid, k, sorted(bad), allowed))
        if any(v == '?' for v in default.values()):
            problems.append('%s:%s default has unresolved values %s' % (mod, bid, default))
        pstate = dict(default)
        values = {p: [v for v in vals if v != '?'] for p, vals in props}
        if 'preview' not in opts and {'east', 'west'} <= set(pnames) and CONNECTS.search(bid + ' ' + cls.lower()):
            # connecting blocks (fences, walls, panes): preview them joined east-west
            for side_ in ('east', 'west'):
                pstate[side_] = next((v for v in ('true', 'low', 'tall', 'side') if v in values[side_]), pstate[side_])
        pstate.update(opts.get('preview', {}))
        models = models_for(bs, pstate)
        if not models:
            problems.append('%s:%s: no model for %s' % (mod, bid, pstate))
            continue
        tp, sd, box, vol, faces = analyse(pack, mod, models)
        mode = opts.get('render') or shape(box, vol)
        if not opts.get('render') and any(c.endswith('/IronBarsBlock') for c in pack.classpath(mod).supers(cls)):
            mode = 'pane'
        if not opts.get('render') and cat == 'lamp' and mode == 'pane':
            mode = 'cross'                     # street lamps and sconces read better as a sprite
        icon = item_icon(pack, mod, bid)
        rec_tex = {}
        for role, pick in (('top', tp), ('side', sd)):
            if not pick:
                continue
            tid, (area, uv, _, tint) = pick
            entry = {'texture': tid}
            if uv and [round(u, 3) for u in uv] != [0, 0, 16, 16]:
                entry['uv'] = uv
            if tint:
                entry['tint'] = tint_kind(tid)
            rec_tex[role] = entry
        if icon:
            rec_tex['icon'] = {'texture': icon}
        # sprites mix every texture of the model: in the repo only when all of them may be there
        # (Macaw's MIT blocks often borrow vanilla textures, which stay local)
        used = {f['texture'].split(':')[0] for f in faces}
        block_root = 'repo' if used and all(mods.get(ns, {}).get('redistributable') for ns in used) else 'local'
        if opts.get('sprite') == 'icon' and icon:
            rec_tex['sprite'] = {'texture': icon}
            faces = []                         # drawn by a block entity: the item icon says more
        full = bool(box and box['from'] == [0, 0, 0] and box['to'] == [16, 16, 16] and vol >= 0.95 * 4096)
        if faces and (mode in ('cross', 'pane') or (mode == 'cube' and not full)):
            sp = front_sprite(pack, mod, faces, box)
            if sp is not None:
                sid = '%s:sprite/%s' % (mod, bid)
                textures[sid] = {'size': list(sp.size), 'root': block_root, 'file': '%s/sprite/%s.png' % (mod, bid),
                                 'alpha': True, 'derived': 'front view of the block model'}
                if not args.check:
                    save_png(sp, block_root, textures[sid]['file'])
                rec_tex['sprite'] = {'texture': sid}
        if faces and mode in ('pane', 'thin', 'slab') or (faces and mode == 'cube' and not full):
            tp_im = top_sprite(pack, mod, faces)
            if tp_im is not None:
                sid = '%s:sprite/%s_top' % (mod, bid)
                textures[sid] = {'size': list(tp_im.size), 'root': block_root,
                                 'file': '%s/sprite/%s_top.png' % (mod, bid), 'alpha': True,
                                 'derived': 'top view of the block model'}
                if not args.check:
                    save_png(tp_im, block_root, textures[sid]['file'])
                rec_tex['spriteTop'] = {'texture': sid}
        opaque = True
        for role, entry in rec_tex.items():
            tid = entry['texture']
            if tid not in textures:
                im, info = texture_image(pack, mod, tid)
                if im is None:
                    problems.append('%s:%s texture %s missing' % (mod, bid, tid))
                    textures[tid] = None
                    continue
                ns, path = tid.split(':', 1)
                owner = ns if ns in mods else mod
                if ns == 'minecraft':
                    root = 'vanilla' if os.path.exists(os.path.join(VANILLA_TEX, path.split('/', 1)[-1] + '.png'))                         and path.startswith('block/') else 'local'
                else:
                    root = 'repo' if mods.get(owner, mods[mod])['redistributable'] else 'local'
                info['root'] = root
                info['file'] = None if root == 'vanilla' else '%s/%s.png' % (ns, path)
                info['alpha'] = im.getextrema()[3][0] < 255
                textures[tid] = info
                if root != 'vanilla' and not args.check:
                    save_png(im, root, info['file'])
            if textures.get(tid) and textures[tid]['size'][0] <= 16:
                entry.pop('uv', None)          # 16px textures: the whole texture reads better than a strip
            if role in ('top', 'side') and textures.get(tid) and textures[tid]['alpha']:
                opaque = False
        rec = {
            'id': '%s:%s' % (mod, bid), 'mod': mod, 'category': cat, 'label': label,
            'default': state_string('%s:%s' % (mod, bid), default),
            'properties': values,
            'directional': {p: values[p] for p in pnames if p in DIRECTIONAL},
            'render': mode, 'solid': bool(mode == 'cube' and full and opaque),
            'top': rec_tex.get('top'), 'side': rec_tex.get('side'),
        }
        for role in ('icon', 'sprite', 'spriteTop'):
            if role in rec_tex:
                rec[role] = rec_tex[role]
        unrot = [p for p in pnames if p not in HANDLED_BY_ORIENT and
                 any(DIRWORD.search(v) for v in values[p]) and p not in ('half',)]
        if unrot:
            rec['unrotated'] = unrot
        if opts.get('preview'):
            rec['preview'] = state_string('%s:%s' % (mod, bid), pstate)
        if box:
            rec['bounds'] = box
        rec['stateSource'] = source
        rec['class'] = cls.replace('/', '.')
        rec['blockEntity'] = 'net/minecraft/world/level/block/EntityBlock' in pack.classpath(mod).ancestors(cls)
        notes = [opts['note']] if opts.get('note') else []
        facing = values.get('facing', [])
        if facing and set(facing) < {'north', 'south', 'east', 'west'}:
            notes.append('facing is an axis here (%s): palette.normalize() folds rotated south/west back'
                         % '|'.join(facing))
        if not models_for(bs, default) and not opts.get('note'):
            notes.append('the default state has no model; set %s' % ', '.join(sorted(opts.get('preview', {}))))
        if notes:
            rec['note'] = ' '.join(notes)
        blocks.append(rec)

    out = {
        'schema': 1,
        'minecraft': '1.21.1', 'loader': 'neoforge 21.1.249',
        'about': 'Decorative mod blocks for the Solsticio generators. Built by build_palette.py from the pack JARs; '
                 'textures are for local previews only.',
        'textureRoots': {'repo': 'textures', 'local': LOCAL_TEX,
                         'vanilla': 'voxkit.TEX (vanilla client textures)'},
        'mods': mods,
        'textures': {k: v for k, v in sorted(textures.items()) if v},
        'blocks': blocks,
    }
    by = {}
    for b in blocks:
        by.setdefault(b['mod'], 0)
        by[b['mod']] += 1
    print(len(blocks), 'blocks;', ', '.join('%s %d' % kv for kv in sorted(by.items())))
    for p in problems:
        print('PROBLEM', p)
    if not args.check:
        write_notice(mods)
        with open(os.path.join(HERE, 'palette.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
            f.write('\n')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
