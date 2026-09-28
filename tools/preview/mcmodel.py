"""Inventory-style item renders for ENTRELUMEN previews: JSON block models (cuboid elements, element rotations,
face UVs and UV rotations, parent chains across the pinned JARs) drawn the way Minecraft 1.21.1 draws a 3D item
in a GUI slot (display.gui: rotation [30, 225, 0], scale 0.625, orthographic, "side" lighting). Flat items
(item/generated layers) keep their texture. Models with a custom loader (OBJ, composite) or a built-in renderer
fall back to their flat texture. Not a game screenshot; close enough to judge composition and icons.
"""
import math
from PIL import Image

DIRS = {
    # corners TL, TR, BR, BL as seen from outside the face; default UV (u0, v0, u1, v1) in 0..16 space
    "north": (lambda f, t: [(t[0], t[1], f[2]), (f[0], t[1], f[2]), (f[0], f[1], f[2]), (t[0], f[1], f[2])],
              lambda f, t: (16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]), (0, 0, -1)),
    "south": (lambda f, t: [(f[0], t[1], t[2]), (t[0], t[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], t[2])],
              lambda f, t: (f[0], 16 - t[1], t[0], 16 - f[1]), (0, 0, 1)),
    "west": (lambda f, t: [(f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], t[2]), (f[0], f[1], f[2])],
             lambda f, t: (f[2], 16 - t[1], t[2], 16 - f[1]), (-1, 0, 0)),
    "east": (lambda f, t: [(t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], f[2]), (t[0], f[1], t[2])],
             lambda f, t: (16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]), (1, 0, 0)),
    "up": (lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])],
           lambda f, t: (f[0], f[2], t[0], t[2]), (0, 1, 0)),
    "down": (lambda f, t: [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2]), (f[0], f[1], f[2])],
             lambda f, t: (f[0], 16 - t[2], t[0], 16 - f[2]), (0, -1, 0)),
}
DEFAULT_GUI = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}


def _rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return ((1, 0, 0), (0, c, -s), (0, s, c))
    if axis == "y":
        return ((c, 0, s), (0, 1, 0), (-s, 0, c))
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def _mul(m, v):
    return tuple(sum(m[i][j] * v[j] for j in range(3)) for i in range(3))


def _mm(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


class ModelRenderer:
    def __init__(self, assets):
        self.A = assets
        self.cache = {}

    def resolve(self, ref, depth=0):
        """Merged model: textures, elements, display.gui, loader and whether it is item/generated."""
        m = self.A.model(ref)
        if m is None or depth > 10:
            return None
        parent = m.get("parent")
        base = {"textures": {}, "elements": None, "gui": None, "loader": None, "generated": False, "builtin": False}
        if parent:
            p = parent if ":" in parent else "minecraft:" + parent
            if p in ("minecraft:item/generated", "minecraft:item/handheld", "minecraft:builtin/generated"):
                base["generated"] = True
            elif p.startswith("minecraft:builtin/"):
                base["builtin"] = True
            else:
                got = self.resolve(p, depth + 1)
                if got:
                    base = dict(got, textures=dict(got["textures"]))
        base["textures"].update(m.get("textures") or {})
        if m.get("elements"):
            base["elements"] = m["elements"]
        disp = (m.get("display") or {}).get("gui")
        if disp:
            base["gui"] = disp
        if m.get("loader"):
            base["loader"] = m["loader"]
        if m.get("model"):
            base["obj"] = m["model"]
            base["flip_v"] = bool(m.get("flip_v"))
        return base

    def texture(self, textures, ref, n=0):
        while isinstance(ref, str) and ref.startswith("#") and n < 10:
            ref = textures.get(ref[1:])
            n += 1
        if not isinstance(ref, str):
            return None
        return self.A.texture(ref if ":" in ref else "minecraft:" + ref)

    def render(self, item_id, size):
        key = (item_id, size)
        if key in self.cache:
            return self.cache[key]
        ns, path = item_id.split(":", 1)
        model = self.resolve(f"{ns}:item/{path}")
        img = None
        try:
            if model and model["elements"] and not model["loader"]:
                img = self.render_elements(model, size)
            elif model and model.get("loader") == "neoforge:obj" and model.get("obj"):
                img = self.render_obj(model, size)
        except Exception:
            img = None
        if img is None:
            flat = self.A.item_icon(item_id)
            if flat is not None:
                img = flat.resize((size, size), Image.NEAREST)
        self.cache[key] = img
        return img

    def render_elements(self, model, size):
        gui = model["gui"] or DEFAULT_GUI
        rx, ry, rz = (gui.get("rotation") or [0, 0, 0])
        sx, sy, sz = (gui.get("scale") or [1, 1, 1])
        tx, ty, tz = (gui.get("translation") or [0, 0, 0])
        # ItemTransform.apply: translate, rotate (Quaternionf.rotationXYZ: R = Rx * Ry * Rz), scale
        R = _mm(_mm(_rot("x", rx), _rot("y", ry)), _rot("z", rz))
        k = size / 16.0
        faces = []
        for el in model["elements"]:
            f, t = el["from"], el["to"]
            er = el.get("rotation")
            erm = None
            if er and er.get("angle"):
                erm = (_rot(er["axis"], er["angle"]), er.get("origin", [8, 8, 8]))
            for d, face in (el.get("faces") or {}).items():
                if d not in DIRS:
                    continue
                corners_fn, uv_fn, normal = DIRS[d]
                pts = corners_fn(f, t)
                n = normal
                if erm:
                    m, o = erm
                    pts = [tuple(a + b for a, b in zip(_mul(m, tuple(p[i] - o[i] for i in range(3))), o)) for p in pts]
                    n = _mul(m, n)
                scr = []
                for p in pts:
                    v = (p[0] - 8, p[1] - 8, p[2] - 8)
                    v = (v[0] * sx, v[1] * sy, v[2] * sz)
                    v = _mul(R, v)
                    v = (v[0] + tx, v[1] + ty, v[2] + tz)
                    scr.append((size / 2 + v[0] * k, size / 2 - v[1] * k, v[2]))
                nn = _mul(R, n)
                if nn[2] <= 1e-4:
                    continue  # facing away (camera looks down -z)
                tex = self.texture(model["textures"], face.get("texture"))
                if tex is None:
                    continue
                uv = face.get("uv") or uv_fn(f, t)
                shade = 1.0
                if face.get("shade", True) and el.get("shade", True):
                    # GUI "side" lighting, tuned by eye against inventory renders: tops bright, left sides a
                    # little darker, right sides darker still.
                    shade = 0.62 + 0.38 * max(0.0, nn[1]) + 0.16 * max(0.0, -nn[0]) - 0.02 * max(0.0, nn[0])
                    shade = max(0.45, min(1.0, shade))
                faces.append((sum(p[2] for p in scr) / 4, scr, tex, uv, face.get("rotation", 0), shade))
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        for _, scr, tex, uv, rot, shade in sorted(faces, key=lambda x: x[0]):
            self.draw_face(out, scr, tex, uv, rot, shade)
        return out

    def raw_any(self, rel):
        """Bytes of any file in the pinned JARs (the asset index only lists png/json/mcmeta)."""
        got = self.A.raw(rel)
        if got:
            return got
        import zipfile
        ns = rel.split("/")[1]
        jars = [j for j in self.A.jars if ns.replace("_", "") in j.lower().replace("_", "").replace("-", "")] + list(self.A.jars)
        for jar in jars:
            try:
                with zipfile.ZipFile(jar) as z:
                    return z.read(rel)
            except (KeyError, OSError, zipfile.BadZipFile):
                continue
        return None

    def render_obj(self, model, size):
        """NeoForge OBJ models (Create's water wheel, crushing wheel, blaze burner): triangles in block units,
        textures from the MTL's map_Kd through the model's texture map, the same GUI transform."""
        ns, path = model["obj"].split(":", 1)
        raw = self.raw_any(f"assets/{ns}/{path}")
        if not raw:
            return None
        lines = raw.decode("utf-8", "replace").splitlines()
        base = path.rsplit("/", 1)[0]
        mtl = {}
        verts, uvs, faces = [], [], []
        current = None
        for line in lines:
            parts = line.split()
            if not parts:
                continue
            if parts[0] == "mtllib":
                mraw = self.raw_any(f"assets/{ns}/{base}/{parts[1]}")
                name = None
                for ml in (mraw.decode("utf-8", "replace").splitlines() if mraw else []):
                    mp = ml.split()
                    if mp and mp[0] == "newmtl":
                        name = mp[1]
                    elif mp and mp[0] == "map_Kd" and name:
                        mtl[name] = mp[1]
            elif parts[0] == "v":
                verts.append(tuple(float(c) * 16 for c in parts[1:4]))
            elif parts[0] == "vt":
                u, v = float(parts[1]), float(parts[2])
                uvs.append((u, 1 - v if model.get("flip_v") else v))
            elif parts[0] == "usemtl":
                current = parts[1]
            elif parts[0] == "f":
                idx = []
                for p in parts[1:]:
                    bits = p.split("/")
                    vi = int(bits[0])
                    ti = int(bits[1]) if len(bits) > 1 and bits[1] else None
                    idx.append((vi - 1 if vi > 0 else len(verts) + vi, (ti - 1 if ti > 0 else len(uvs) + ti) if ti else None))
                faces.append((current, idx))
        gui = model["gui"] or DEFAULT_GUI
        rx, ry, rz = gui.get("rotation") or [0, 0, 0]
        sx, sy, sz = gui.get("scale") or [1, 1, 1]
        tx, ty, tz = gui.get("translation") or [0, 0, 0]
        R = _mm(_mm(_rot("x", rx), _rot("y", ry)), _rot("z", rz))
        k = size / 16.0

        def project(p):
            v = ((p[0] - 8) * sx, (p[1] - 8) * sy, (p[2] - 8) * sz)
            v = _mul(R, v)
            return (size / 2 + (v[0] + tx) * k, size / 2 - (v[1] + ty) * k, v[2] + tz)
        tris = []
        texcache = {}
        for mat, idx in faces:
            ref = mtl.get(mat, "")
            if ref not in texcache:
                texcache[ref] = self.texture(model["textures"], ref) if ref.startswith("#") else self.texture({}, ref)
            tex = texcache[ref]
            if tex is None or len(idx) < 3:
                continue
            for a, b in ((1, 2), (2, 3)) if len(idx) == 4 else ((i, i + 1) for i in range(1, len(idx) - 1)):
                tri = [idx[0], idx[a], idx[b]]
                pw = [verts[i] for i, _ in tri]
                e1 = tuple(pw[1][j] - pw[0][j] for j in range(3))
                e2 = tuple(pw[2][j] - pw[0][j] for j in range(3))
                n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
                ln = math.sqrt(sum(c * c for c in n)) or 1.0
                nn = _mul(R, tuple(c / ln for c in n))
                if nn[2] <= 1e-4:
                    continue
                scr = [project(p) for p in pw]
                uv = [uvs[t] if t is not None else (0, 0) for _, t in tri]
                shade = max(0.45, min(1.0, 0.62 + 0.38 * max(0.0, nn[1]) + 0.16 * max(0.0, -nn[0])))
                tris.append((sum(p[2] for p in scr) / 3, scr, uv, tex, shade))
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        from PIL import ImageDraw
        for _, scr, uv, tex, shade in sorted(tris, key=lambda t: t[0]):
            (x0, y0, _), (x1, y1, _), (x2, y2, _) = scr
            det = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(det) < 1e-4:
                continue
            w, h = tex.size
            su = [u * w for u, _ in uv]
            sv = [v * h for _, v in uv]
            # affine dest (x, y) -> source (u, v) through the three corners
            def solve(s):
                a = ((s[1] - s[0]) * (y2 - y0) - (s[2] - s[0]) * (y1 - y0)) / det
                b = ((s[2] - s[0]) * (x1 - x0) - (s[1] - s[0]) * (x2 - x0)) / det
                return a, b, s[0] - a * x0 - b * y0
            a, b, c = solve(su)
            d, e, f = solve(sv)
            piece = tex
            if shade < 0.999:
                r_, g_, b_, al = tex.split()
                r_, g_, b_ = (ch.point(lambda v: int(v * shade)) for ch in (r_, g_, b_))
                piece = Image.merge("RGBA", (r_, g_, b_, al))
            layer = piece.transform(out.size, Image.AFFINE, (a, b, c + (a + b) * 0.5, d, e, f + (d + e) * 0.5),
                                    resample=Image.NEAREST, fillcolor=(0, 0, 0, 0))
            mask = Image.new("L", out.size, 0)
            ImageDraw.Draw(mask).polygon([(x0, y0), (x1, y1), (x2, y2)], fill=255)
            clear = Image.new("RGBA", out.size, (0, 0, 0, 0))
            out.alpha_composite(Image.composite(layer, clear, mask))
        return out

    @staticmethod
    def draw_face(out, scr, tex, uv, rot, shade):
        w, h = tex.size
        u0, v0, u1, v1 = [c * w / 16.0 if i % 2 == 0 else c * h / 16.0 for i, c in enumerate(uv)]
        x0, x1 = sorted((u0, u1))
        y0, y1 = sorted((v0, v1))
        x0, y0 = int(math.floor(x0 + 1e-6)), int(math.floor(y0 + 1e-6))
        x1, y1 = max(x0 + 1, int(math.ceil(x1 - 1e-6))), max(y0 + 1, int(math.ceil(y1 - 1e-6)))
        piece = tex.crop((x0, y0, x1, y1))
        if u0 > u1:
            piece = piece.transpose(Image.FLIP_LEFT_RIGHT)
        if v0 > v1:
            piece = piece.transpose(Image.FLIP_TOP_BOTTOM)
        if rot:
            piece = piece.rotate(-rot, expand=True)
        if shade < 0.999:
            r, g, b, a = piece.split()
            r, g, b = (ch.point(lambda v: int(v * shade)) for ch in (r, g, b))
            piece = Image.merge("RGBA", (r, g, b, a))
        pw, ph = piece.size
        (ax, ay, _), (bx, by, _), _, (dx, dy, _) = scr
        e1 = (bx - ax, by - ay)
        e2 = (dx - ax, dy - ay)
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-3:
            return
        # screen p -> (a, b): p - A = a e1 + b e2; source = (a pw, b ph)
        i00, i01 = e2[1] / det, -e2[0] / det
        i10, i11 = -e1[1] / det, e1[0] / det
        # sample at pixel centres
        c0, c1 = i00 * pw, i01 * pw
        c2 = -(i00 * ax + i01 * ay) * pw + (c0 + c1) * 0.5
        c3, c4 = i10 * ph, i11 * ph
        c5 = -(i10 * ax + i11 * ay) * ph + (c3 + c4) * 0.5
        layer = piece.transform(out.size, Image.AFFINE, (c0, c1, c2, c3, c4, c5), resample=Image.NEAREST,
                                fillcolor=(0, 0, 0, 0))
        out.alpha_composite(layer)
