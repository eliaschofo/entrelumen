"""Texel-exact software renders of Minecraft block models, for review sheets (never a game capture).

Models resolve the way the game resolves them: parent chain, texture variables, per-face UV and UV
rotation, element rotation with rescale, blockstate x/y rotation. Faces shade like vanilla (up 1.0,
north/south 0.8, east/west 0.6, down 0.5; `shade: false` stays at 1.0). Elements that carry NeoForge's
emissive data (`neoforge_data.block_light` 15, the pack's glow method) keep full brightness in the dark
render, so a dark sheet shows what glows. Orthographic, z-buffered, nearest sampling and alpha-tested:
one texel stays one crisp square. Plain Python and Pillow, like the other authoring scripts.

    from model_iso import Assets, Scene
    assets = Assets(dirs=[<repo>/companion/src/main/resources/assets])
    scene = Scene(assets)
    scene.add('minecraft:block/tuff_bricks', (0, 0, 0))
    scene.add('entrelumen:block/enves_mirror', (1, 0, 0), y=90)
    image = scene.render(yaw=35, pitch=30, scale=6, light='day')
"""
import io
import json
import math
import os
import zipfile

from PIL import Image

CLIENT_JAR = 'G:/curseforge/Install/versions/1.21.1/1.21.1.jar'
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def split(rid, kind):
    ns, _, path = rid.partition(':') if ':' in rid else ('minecraft', '', rid)
    return f'{ns}/{kind}/{path}'


class Assets:
    """Reads models and textures from asset folders first, then from JARs (the client, mods)."""

    def __init__(self, dirs=(), jars=(CLIENT_JAR,), overrides=None):
        self.dirs = list(dirs)
        self.jars = [zipfile.ZipFile(j) for j in jars]
        self.overrides = dict(overrides or {})          # texture id -> PIL image (unsaved drafts)
        self.models, self.textures = {}, {}

    def read(self, rel):
        for d in self.dirs:
            p = os.path.join(d, rel)
            if os.path.exists(p):
                with open(p, 'rb') as f:
                    return f.read()
        for z in self.jars:
            try:
                return z.read('assets/' + rel)
            except KeyError:
                pass
        raise KeyError(rel)

    def texture(self, rid):
        if rid not in self.textures:
            if rid in self.overrides:
                im = self.overrides[rid].convert('RGBA')
            else:
                try:
                    im = Image.open(io.BytesIO(self.read(split(rid, 'textures') + '.png'))).convert('RGBA')
                except KeyError:
                    im = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
            w = im.size[0]
            if im.size[1] > w:                                   # animated strip: its first frame
                im = im.crop((0, 0, w, w))
            self.textures[rid] = (w, im.size[1], list(im.getdata()))
        return self.textures[rid]

    def model(self, rid, data=None):
        """(textures, elements) of a model id, or of a model dict passed as `data`."""
        if data is None and rid in self.models:              # a passed dict is resolved every time, never cached
            return self.models[rid]
        chain = []
        m = data if data is not None else json.loads(self.read(split(rid, 'models') + '.json'))
        while m is not None:
            chain.append(m)
            parent = m.get('parent')
            m = json.loads(self.read(split(parent, 'models') + '.json')) if parent and not parent.startswith('builtin/') else None
        textures, elements = {}, None
        for m in reversed(chain):
            textures.update(m.get('textures', {}))
        for m in chain:
            if 'elements' in m:
                elements = m['elements']
                break
        result = (textures, elements or [])
        if data is None:
            self.models[rid] = result
        return result


def resolve(textures, ref):
    for _ in range(12):
        if ref is None or not ref.startswith('#'):
            return ref
        ref = textures.get(ref[1:])
    return None


def default_uv(face, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
            'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0],
            'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0]}[face]


def corners(face, a, b):
    """Top-left, top-right and bottom-left corners of a face, seen from outside (vanilla UV order)."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {'north': ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
            'south': ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
            'east': ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
            'west': ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
            'up': ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)),
            'down': ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0))}[face]


def rotate_element(p, rot):
    if not rot:
        return tuple(p)
    ox, oy, oz = rot['origin']
    t = math.radians(rot['angle'])
    c, s = math.cos(t), math.sin(t)
    vx, vy, vz = p[0] - ox, p[1] - oy, p[2] - oz
    ax = rot['axis']
    if ax == 'x':
        vy, vz = vy * c - vz * s, vy * s + vz * c
    elif ax == 'y':
        vx, vz = vx * c + vz * s, -vx * s + vz * c
    else:
        vx, vy = vx * c - vy * s, vx * s + vy * c
    if rot.get('rescale') and rot['angle']:
        k = 1 / math.cos(math.radians(22.5 if abs(rot['angle']) == 22.5 else 45))
        if ax != 'x':
            vx *= k
        if ax != 'y':
            vy *= k
        if ax != 'z':
            vz *= k
    return (vx + ox, vy + oy, vz + oz)


def rotate_state(p, x=0, y=0):
    """Blockstate rotation about the block centre: x first, then y (clockwise seen from above)."""
    px, py, pz = p
    for _ in range((x // 90) % 4):
        dy, dz = py - 8, pz - 8
        py, pz = 8 + dz, 8 - dy
    for _ in range((y // 90) % 4):
        px, pz = 16 - pz, px
    return (px, py, pz)


def brightness(level, ambient=0.06, gamma=0.5):
    """Vanilla's lightmap for a block light level (1.21: dimension ambient, then the default brightness)."""
    f = level / 15
    b = f / (4 - 3 * f)
    b = b + ambient * (1 - b)
    b = b + ((1 - (1 - b) ** 4) - b) * gamma
    return 0.04 + 0.96 * b


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def span(f0, df, lo, hi):
    """Integer pixels p in [lo, hi) with 0 <= f0 + (p + 0.5) * df < 1."""
    if df == 0:
        return (lo, hi) if 0 <= f0 < 1 else (lo, lo)
    a, b = (0 - f0) / df - 0.5, (1 - f0) / df - 0.5
    if df < 0:
        a, b = b, a
    return max(lo, math.ceil(a - 1e-9)), min(hi, math.ceil(b - 1e-9) if df > 0 else math.floor(b) + 1)


class Scene:
    def __init__(self, assets):
        self.assets = assets
        self.quads = []
        self.emitters = []

    def add(self, model, offset=(0, 0, 0), x=0, y=0, data=None, glow_all=False, light=0):
        """Adds a model at a block offset (in blocks) with a blockstate rotation; `light` is the block's emission."""
        textures, elements = self.assets.model(model, data)
        ox, oy, oz = (16 * o for o in offset)
        if light:
            self.emitters.append((tuple(offset), light))
        for e in elements:
            a, b = e['from'], e['to']
            glow = glow_all or e.get('neoforge_data', {}).get('block_light', 0) >= 15
            shade = e.get('shade', True)
            for face, f in e.get('faces', {}).items():
                tex = resolve(textures, f.get('texture'))
                if tex is None:
                    continue
                uv = f.get('uv') or default_uv(face, a, b)
                pts = []
                for p in corners(face, a, b):
                    q = rotate_state(rotate_element(p, e.get('rotation')), x, y)
                    pts.append((q[0] + ox, q[1] + oy, q[2] + oz))
                glow_face = glow or f.get('neoforge_data', {}).get('block_light', 0) >= 15
                # the cell whose light the face takes: the neighbour when it lies on the block's boundary
                n = cross(sub(pts[2], pts[0]), sub(pts[1], pts[0]))
                size = math.sqrt(dot(n, n)) or 1
                centre = tuple((pts[1][i] + pts[2][i]) / 2 - (ox, oy, oz)[i] for i in range(3))
                cell = tuple(offset)
                for i in range(3):
                    unit = n[i] / size
                    if abs(abs(unit) - 1) < 1e-6 and (abs(centre[i]) < 1e-6 or abs(centre[i] - 16) < 1e-6):
                        cell = tuple(offset[j] + (round(unit) if j == i else 0) for j in range(3))
                self.quads.append((pts, self.assets.texture(tex), uv, f.get('rotation', 0),
                                   SHADE[face] if shade else 1.0, 15 if glow_face else (cell, light)))
        return self

    def level(self, info, room):
        """Block light a face shows: 15 when emissive, else the brightest of the room, its block's own emission
        and every emitter's light fading one level per block (Manhattan distance, like vanilla's flood fill)."""
        if info == 15:
            return 15
        cell = info[0]
        best = room
        for ecell, L in self.emitters:
            best = max(best, L - sum(abs(cell[i] - ecell[i]) for i in range(3)))
        return best

    def render(self, yaw=35, pitch=30, scale=6, light='day', room=6, background=(0, 0, 0, 0), margin=6):
        """yaw: camera azimuth in degrees (0 = from the south, 90 = from the east); pitch: its elevation.

        light='day' shades faces only; light='dark' lights them like the Envés (no sky, ambient 0.06) at the
        room's block light `room`, or at a block's own emission when brighter; emissive elements get 15."""
        ph, th = math.radians(yaw), math.radians(pitch)
        cam = (math.sin(ph) * math.cos(th), math.sin(th), math.cos(ph) * math.cos(th))
        right = (math.cos(ph), 0.0, -math.sin(ph))
        up = cross(cam, right)
        proj = []
        for pts, tex, uv, rot, shade, glow in self.quads:
            n = cross(sub(pts[2], pts[0]), sub(pts[1], pts[0]))
            if dot(n, cam) <= 1e-9:                                  # a back face, or edge-on
                continue
            scr = [(dot(p, right) * scale, -dot(p, up) * scale, -dot(p, cam)) for p in pts]
            proj.append((scr, tex, uv, rot, shade, glow))
        if not proj:
            return Image.new('RGBA', (1, 1), background)
        xs = [c[0] for q in proj for c in q[0]] + [q[0][1][0] + q[0][2][0] - q[0][0][0] for q in proj]
        ys = [c[1] for q in proj for c in q[0]] + [q[0][1][1] + q[0][2][1] - q[0][0][1] for q in proj]
        x0, y0 = math.floor(min(xs)) - margin, math.floor(min(ys)) - margin
        W, H = math.ceil(max(xs)) - x0 + margin, math.ceil(max(ys)) - y0 + margin
        color = [tuple(background)] * (W * H)
        depth = [math.inf] * (W * H)
        for scr, (tw, tH, data), uv, rot, shade, glow in proj:
            p0x, p0y = scr[0][0] - x0, scr[0][1] - y0
            eux, euy = scr[1][0] - scr[0][0], scr[1][1] - scr[0][1]
            evx, evy = scr[2][0] - scr[0][0], scr[2][1] - scr[0][1]
            du, dv = scr[1][2] - scr[0][2], scr[2][2] - scr[0][2]
            det = eux * evy - euy * evx
            if abs(det) < 1e-9:
                continue
            k = shade if light == 'day' else brightness(self.level(glow, room)) * (1.0 if glow == 15 else shade)
            qx = (p0x, p0x + eux, p0x + evx, p0x + eux + evx)
            qy = (p0y, p0y + euy, p0y + evy, p0y + euy + evy)
            bx0, bx1 = max(0, math.floor(min(qx))), min(W, math.ceil(max(qx)))
            by0, by1 = max(0, math.floor(min(qy))), min(H, math.ceil(max(qy)))
            su, sv = (uv[2] - uv[0]) * tw / 16, (uv[3] - uv[1]) * tH / 16
            u0, v0 = uv[0] * tw / 16, uv[1] * tH / 16
            for py in range(by0, by1):
                ry = py + 0.5 - p0y
                # s = (rx * evy - ry * evx) / det, t = (eux * ry - euy * rx) / det, rx = px + 0.5 - p0x
                s0 = (-p0x * evy - ry * evx) / det
                t0 = (eux * ry + euy * p0x) / det
                ds, dt = evy / det, -euy / det
                a0, a1 = span(s0, ds, bx0, bx1)
                b0, b1 = span(t0, dt, bx0, bx1)
                lo, hi = max(a0, b0), min(a1, b1)
                row = py * W
                for px in range(lo, hi):
                    c = px + 0.5
                    s, t = s0 + c * ds, t0 + c * dt
                    if not (0 <= s < 1 and 0 <= t < 1):
                        continue
                    z = scr[0][2] + s * du + t * dv
                    i = row + px
                    if z >= depth[i] - 1e-6:
                        continue
                    if rot:
                        s, t = {90: (t, 1 - s), 180: (1 - s, 1 - t), 270: (1 - t, s)}[rot % 360]
                    ui = min(tw - 1, max(0, int(math.floor(u0 + s * su))))
                    vi = min(tH - 1, max(0, int(math.floor(v0 + t * sv))))
                    texel = data[vi * tw + ui]
                    if texel[3] < 128:
                        continue
                    depth[i] = z
                    color[i] = (min(255, int(texel[0] * k)), min(255, int(texel[1] * k)), min(255, int(texel[2] * k)), 255)
        im = Image.new('RGBA', (W, H))
        im.putdata(color)
        return im

    def render_eye(self, target, yaw=20, pitch=4, dist=3.0, fov=70, size=(320, 240), light='day', room=6,
                   background=(0, 0, 0, 255)):
        """A perspective view as a player sees it: the camera `dist` blocks from `target` (block units, e.g.
        (0.5, 0.5, 0.5) for a block's centre), at azimuth `yaw` and elevation `pitch`, vertical field of view
        `fov` degrees (the game's default is 70). Each pixel's ray meets each face exactly, so texels keep
        perspective without affine drift."""
        W, H = size
        ph, th = math.radians(yaw), math.radians(pitch)
        cam = (math.sin(ph) * math.cos(th), math.sin(th), math.cos(ph) * math.cos(th))
        right = (math.cos(ph), 0.0, -math.sin(ph))
        up = cross(cam, right)
        C = tuple(16 * target[i] + 16 * dist * cam[i] for i in range(3))
        f = (H / 2) / math.tan(math.radians(fov) / 2)
        color = [tuple(background)] * (W * H)
        depth = [math.inf] * (W * H)

        def project(p):
            v = sub(p, C)
            z = -dot(v, cam)
            return (W / 2 + dot(v, right) / z * f, H / 2 - dot(v, up) / z * f, z)

        for pts, (tw, tH, data), uv, rot, shade, glow in self.quads:
            P0, E1, E2 = pts[0], sub(pts[1], pts[0]), sub(pts[2], pts[0])
            n = cross(E2, E1)                                        # outward normal (see Scene.render)
            w = sub(C, P0)
            if dot(n, w) <= 1e-9:
                continue
            corners4 = [P0, pts[1], pts[2], tuple(pts[1][i] + E2[i] for i in range(3))]
            prj = [project(p) for p in corners4]
            if any(p[2] <= 1e-3 for p in prj):
                continue
            bx0 = max(0, math.floor(min(p[0] for p in prj)))
            bx1 = min(W, math.ceil(max(p[0] for p in prj)))
            by0 = max(0, math.floor(min(p[1] for p in prj)))
            by1 = min(H, math.ceil(max(p[1] for p in prj)))
            if bx0 >= bx1 or by0 >= by1:
                continue
            k = shade if light == 'day' else brightness(self.level(glow, room)) * (1.0 if glow == 15 else shade)
            nn = cross(E1, E2)
            a, b = cross(w, E2), cross(E1, w)
            wn = dot(w, nn)
            su, sv = (uv[2] - uv[0]) * tw / 16, (uv[3] - uv[1]) * tH / 16
            u0, v0 = uv[0] * tw / 16, uv[1] * tH / 16
            # ray direction d(px, py) = right * (px - W/2) - up * (py - H/2) - cam * f: every product with it is linear
            def lin(vec):
                return dot(right, vec), -dot(up, vec), -dot(cam, vec) * f - dot(right, vec) * W / 2 + dot(up, vec) * H / 2
            A_, B_, N_ = lin(a), lin(b), lin(nn)
            for py in range(by0, by1):
                cy = py + 0.5
                row = py * W
                for px in range(bx0, bx1):
                    cx = px + 0.5
                    dn = N_[0] * cx + N_[1] * cy + N_[2]
                    if abs(dn) < 1e-12:
                        continue
                    s = (A_[0] * cx + A_[1] * cy + A_[2]) / dn
                    t = (B_[0] * cx + B_[1] * cy + B_[2]) / dn
                    if not (0 <= s < 1 and 0 <= t < 1):
                        continue
                    lam = -wn / dn
                    if lam <= 0:
                        continue
                    i = row + px
                    if lam >= depth[i] - 1e-9:
                        continue
                    if rot:
                        s, t = {90: (t, 1 - s), 180: (1 - s, 1 - t), 270: (1 - t, s)}[rot % 360]
                    ui = min(tw - 1, max(0, int(math.floor(u0 + s * su))))
                    vi = min(tH - 1, max(0, int(math.floor(v0 + t * sv))))
                    texel = data[vi * tw + ui]
                    if texel[3] < 128:
                        continue
                    depth[i] = lam
                    color[i] = (min(255, int(texel[0] * k)), min(255, int(texel[1] * k)), min(255, int(texel[2] * k)), 255)
        im = Image.new('RGBA', (W, H))
        im.putdata(color)
        return im
