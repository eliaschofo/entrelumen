"""Free voxel engine for concept art: any colour, light, glass and scale, no Minecraft palette.

A Scene maps (x, y, z) to a packed voxel: 24-bit colour plus flags (EMIT = glows, GLASS = half
transparent). Primitives build shapes; render() draws an isometric view (camera from +x, +y, +z)
back to front with cached per-colour sprites, shades the three faces, darkens by ambient
occlusion from the voxels above, fades by depth into the sky, and blooms the emissive voxels.
"""
import math
import random

from PIL import Image, ImageDraw, ImageFilter, ImageChops

EMIT = 1 << 24
GLASS = 1 << 25


def rgb(h):
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def pack(colour, emit=False, glass=False):
    r, g, b = rgb(colour) if isinstance(colour, str) else colour
    return (r << 16) | (g << 8) | b | (EMIT if emit else 0) | (GLASS if glass else 0)


def mix(a, b, t):
    a, b = rgb(a) if isinstance(a, str) else a, rgb(b) if isinstance(b, str) else b
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------- noise ----------------
def _hash(i, j, k, seed):
    n = (i * 374761393 + j * 668265263 + k * 2147483647 + seed * 144269504) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def noise2(x, z, scale, seed=0):
    x, z = x / scale, z / scale
    i, j = math.floor(x), math.floor(z)
    fx, fz = x - i, z - j
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = _hash(i, j, 0, seed), _hash(i + 1, j, 0, seed)
    c, d = _hash(i, j + 1, 0, seed), _hash(i + 1, j + 1, 0, seed)
    return a + (b - a) * sx + (c - a) * sz + (a - b - c + d) * sx * sz


def fbm2(x, z, scale, seed=0, octaves=4):
    v, amp, tot = 0.0, 1.0, 0.0
    for o in range(octaves):
        v += amp * noise2(x, z, scale / (2 ** o), seed + o * 17)
        tot += amp
        amp *= 0.5
    return v / tot


def rnd(x, y, z, seed=0):
    return _hash(x, y, z, seed)


# ---------------- scene ----------------
class Scene(dict):
    def set(self, x, y, z, v):
        self[(int(x), int(y), int(z))] = v

    def put(self, x, y, z, colour, emit=False, glass=False):
        self[(int(x), int(y), int(z))] = pack(colour, emit, glass)

    def box(self, x0, y0, z0, x1, y1, z1, colour, emit=False, glass=False, hollow=False):
        v = pack(colour, emit, glass)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y1 + 1):
                    if hollow and x0 < x < x1 and z0 < z < z1 and y0 < y < y1:
                        continue
                    self[(x, y, z)] = v

    def sphere(self, cx, cy, cz, r, colour, emit=False, glass=False, shell=None, fn=None):
        """Solid sphere, or a shell `shell` thick; `fn(x, y, z)` may return a colour or None."""
        v = pack(colour, emit, glass)
        ir = int(r) + 1
        for x in range(-ir, ir + 1):
            for y in range(-ir, ir + 1):
                for z in range(-ir, ir + 1):
                    d = math.sqrt(x * x + y * y + z * z)
                    if d > r or (shell and d < r - shell):
                        continue
                    if fn:
                        c = fn(x, y, z)
                        if c is None:
                            continue
                        self[(cx + x, cy + y, cz + z)] = c
                    else:
                        self[(cx + x, cy + y, cz + z)] = v

    def cylinder(self, cx, cz, y0, y1, r, colour, emit=False, glass=False, shell=None):
        v = pack(colour, emit, glass)
        ir = int(r) + 1
        for x in range(-ir, ir + 1):
            for z in range(-ir, ir + 1):
                d = math.hypot(x, z)
                if d > r or (shell and d < r - shell):
                    continue
                for y in range(y0, y1 + 1):
                    self[(cx + x, y, cz + z)] = v

    def cone(self, cx, cz, y0, h, r0, r1, colour, emit=False, glass=False, shell=None):
        v = pack(colour, emit, glass)
        for dy in range(h + 1):
            r = r0 + (r1 - r0) * dy / max(1, h)
            ir = int(r) + 1
            for x in range(-ir, ir + 1):
                for z in range(-ir, ir + 1):
                    d = math.hypot(x, z)
                    if d <= r and not (shell and d < r - shell):
                        self[(cx + x, y0 + dy, cz + z)] = v

    def line(self, a, b, r, colour, emit=False, glass=False):
        """A thick segment from a to b (radius r)."""
        v = pack(colour, emit, glass)
        ax, ay, az = a
        bx, by, bz = b
        n = int(max(abs(bx - ax), abs(by - ay), abs(bz - az), 1) * 1.5) + 1
        ir = int(r + 0.5)
        for i in range(n + 1):
            t = i / n
            px, py, pz = ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t
            for dx in range(-ir, ir + 1):
                for dy in range(-ir, ir + 1):
                    for dz in range(-ir, ir + 1):
                        if dx * dx + dy * dy + dz * dz <= r * r + 0.25:
                            self[(round(px + dx), round(py + dy), round(pz + dz))] = v

    def torus(self, cx, cy, cz, R, r, colour, tilt=0.0, yaw=0.0, emit=False, fn=None):
        """A ring of radius R and tube r, tilted by `tilt` about x then turned by `yaw` about y."""
        v = pack(colour, emit)
        steps = int(2 * math.pi * R * 1.6) + 8
        ca, sa, cb, sb = math.cos(tilt), math.sin(tilt), math.cos(yaw), math.sin(yaw)
        ir = int(r + 0.5)
        for i in range(steps):
            th = 2 * math.pi * i / steps
            x, y, z = R * math.cos(th), 0.0, R * math.sin(th)
            y, z = y * ca - z * sa, y * sa + z * ca
            x, z = x * cb + z * sb, -x * sb + z * cb
            for dx in range(-ir, ir + 1):
                for dy in range(-ir, ir + 1):
                    for dz in range(-ir, ir + 1):
                        if dx * dx + dy * dy + dz * dz <= r * r + 0.25:
                            p = (round(cx + x + dx), round(cy + y + dy), round(cz + z + dz))
                            self[p] = fn(th) if fn else v


# ---------------- render ----------------
def _sprite(c, s, faces, glass):
    """A cube sprite (width 4s, height 4s): top rhombus and the two visible sides."""
    w, h = 4 * s, 4 * s
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r, g, b = c
    a = 150 if glass else 255
    top = [(2 * s, 0), (4 * s - 1, s), (2 * s, 2 * s - 1), (0, s)]
    left = [(0, s), (2 * s - 1, 2 * s), (2 * s - 1, 4 * s - 1), (0, 3 * s)]
    right = [(2 * s, 2 * s), (4 * s - 1, s), (4 * s - 1, 3 * s), (2 * s, 4 * s - 1)]
    if faces & 1:
        d.polygon(top, fill=(min(255, int(r * 1.08)), min(255, int(g * 1.08)), min(255, int(b * 1.08)), a))
    if faces & 2:
        d.polygon(left, fill=(int(r * 0.80), int(g * 0.80), int(b * 0.84), a))
    if faces & 4:
        d.polygon(right, fill=(int(r * 0.62), int(g * 0.62), int(b * 0.68), a))
    return im


def render(scene, path, scale=1, sky=('#fff3d6', '#bcd7f2'), fog=('#e9eef5', 0.0), bloom=6, crop=None,
           background=None, max_size=None):
    """Isometric render. `crop(x, y, z)` keeps voxels; fog=(colour, strength 0..1) by depth."""
    s = scale
    occ = scene
    vis = []
    for p, v in scene.items():
        if crop and not crop(*p):
            continue
        x, y, z = p
        faces = 0
        if (x, y + 1, z) not in occ or (occ[(x, y + 1, z)] & GLASS and not v & GLASS):
            faces |= 1
        if (x, y, z + 1) not in occ or (occ[(x, y, z + 1)] & GLASS and not v & GLASS):
            faces |= 2
        if (x + 1, y, z) not in occ or (occ[(x + 1, y, z)] & GLASS and not v & GLASS):
            faces |= 4
        if faces:
            vis.append((x + y + z, y, x, z, v, faces))
    if not vis:
        raise ValueError('nothing to draw')
    proj = lambda x, y, z: ((x - z) * 2 * s, (x + z) * s - y * 2 * s)
    us = [(t[2] - t[3]) * 2 * s for t in vis]
    vs = [(t[2] + t[3]) * s - t[1] * 2 * s for t in vis]
    minu, maxu, minv, maxv = min(us), max(us), min(vs), max(vs)
    W, H = int(maxu - minu) + 8 * s + 80, int(maxv - minv) + 8 * s + 80
    ox, oy = -minu + 40, -minv + 40
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    c0, c1 = rgb(sky[0]), rgb(sky[1])
    for j in range(H):
        t = j / H
        d.line([(0, j), (W, j)], fill=tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,))
    if background:
        background(im, ox, oy, s)
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    depths = [t[0] for t in vis]
    dmin, dmax = min(depths), max(depths)
    fc, fs = rgb(fog[0]), fog[1]
    cache = {}
    vis.sort(key=lambda t: (t[0], t[1]))
    for (dep, y, x, z, v, faces) in vis:
        col = ((v >> 16) & 255, (v >> 8) & 255, v & 255)
        emit, glass = bool(v & EMIT), bool(v & GLASS)
        if not emit:
            # ambient occlusion: darker when voxels hang overhead
            over = sum(1 for k in (1, 2, 3, 5) if (x, y + k, z) in occ)
            k = 1.0 - 0.07 * over
            col = (int(col[0] * k), int(col[1] * k), int(col[2] * k))
        if fs and not emit:
            t = fs * (1 - (dep - dmin) / max(1, dmax - dmin))
            col = tuple(int(col[i] + (fc[i] - col[i]) * t) for i in range(3))
        key = (col, faces, glass, emit)
        sp = cache.get(key)
        if sp is None:
            sp = _sprite(col, s, 7 if emit else faces, glass)
            cache[key] = sp
            if len(cache) > 60000:
                cache.clear()
        u, vv = (x - z) * 2 * s + ox, (x + z) * s - y * 2 * s + oy
        im.alpha_composite(sp, (int(u), int(vv)))
        if emit:
            glow.alpha_composite(sp, (int(u), int(vv)))
    if bloom:
        for radius, strength in ((bloom, 0.45), (bloom * 4, 0.3)):
            g = glow.filter(ImageFilter.GaussianBlur(radius))
            g = Image.eval(g, lambda px: int(px * strength))
            im = ImageChops.screen(im, g)
    im = im.convert('RGB')
    if max_size and max(im.size) > max_size:
        k = max_size / max(im.size)
        im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
    im.save(path)
    return im.size, len(vis)
