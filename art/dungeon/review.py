"""Review renders of the Envés tilesets: a contact sheet of rooms and a whole floor seen from above.

Both draw with the vanilla 16x16 block textures (voxrender), so they stay out of the repo: they go
to $ENVES_REVIEW (default E:/Elias/Codex/Entrelumen-ssd/enves-tiles-review).

    python art/dungeon/review.py cisternas [seed]     # contact sheet + floor II of the seed
    python art/dungeon/review.py all [seed]

- Contact sheet: every room kind of the tileset as a dollhouse cutaway (ceiling off, the two near
  walls cut to the deck), labelled with role, doors and variant, and its light (share of standing
  spots at light 8 or more).
- Floor: drlg.descent(seed) assembled with the tileset's templates as the engine would (variants
  drawn per cell for quiet and fight), drawn top-down at the height of a player's knees, with the
  markers: encounters red, champion crimson, chests gold, shrine yellow, seal teal, vault gate
  grey, arrival white, stairs violet.
"""
import importlib
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
for _p in (HERE, os.path.join(HERE, '..', 'structures')):
    if _p not in sys.path:
        sys.path.insert(0, _p)
import drlg  # noqa: E402
import kit  # noqa: E402
import voxrender  # noqa: E402

OUT = os.environ.get('ENVES_REVIEW', 'E:/Elias/Codex/Entrelumen-ssd/enves-tiles-review')
TILESETS = {'cisternas': (2, 'Cisternas', 'II'), 'fundicion': (3, 'Fundición', 'III'),
            'geodas': (4, 'Geodas', 'IV'), 'eclipse': (5, 'El Eclipse', 'V')}
BG = (24, 22, 30)
INK = (236, 226, 200)
MARK = {'encounter': (214, 60, 50), 'champion': (150, 20, 40), 'chest': (236, 186, 60), 'shrine': (250, 230, 110),
        'seal': (60, 200, 190), 'vault_gate': (150, 150, 160), 'vault_mechanism': (120, 170, 250),
        'arrival': (250, 250, 250), 'stair_top': (170, 120, 230), 'stair_bottom': (170, 120, 230),
        'stair_seal': (110, 80, 160), 'boss_center': (255, 120, 40), 'exit_portal': (240, 210, 120)}


def font(size, bold=False):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/georgia%s.ttf' % ('b' if bold else ''), size)
    except OSError:
        return ImageFont.load_default()


def mask_doors(mask):
    return [d for d in 'NESW' if d.lower() in mask]


# ------------------------------------------------------------------ lighting of the previews
FACE_TEX = {'lava_cauldron': ('lava_still', 'cauldron_side'), 'cauldron': ('cauldron_top', 'cauldron_side'),
            'water_cauldron': ('water_still', 'cauldron_side')}


def faces(state):
    n = kit.core(state)
    if n in FACE_TEX:
        return FACE_TEX[n]
    return voxrender.face_textures(state)


def bright(level):
    """How lit a face looks: a dark room keeps a fifth of its colour, light 15 keeps all of it."""
    return 0.2 + 0.8 * (max(0, min(15, level)) / 15.0) ** 0.85


_LIT = {}
CUT = 9                                                   # the light a cut section is drawn with
STRUCTURAL = ('wall', 'ceil', 'lining', 'band', 'shell', 'course', 'capital', 'plinth', 'pillar', 'base')


def lit(sprite, key, level):
    k = (key, level)
    if k not in _LIT:
        f = bright(level)
        r, g, b, a = sprite.split()
        m = lambda c: int(c * f)
        _LIT[k] = Image.merge('RGBA', (r.point(m), g.point(m), b.point(m), a))
    return _LIT[k]


def render_lit(vox, lv, scale=5, keep=None, bg=BG):
    """voxrender.render with every face shaded by the block light in front of it (kit.light)."""
    s = scale
    allv = {k: v for k, v in vox.items() if not v.endswith(':air') and (keep is None or keep(*k))}
    solid = set()
    for k, v in allv.items():
        n = voxrender._name(v)
        if n in voxrender.CROSS or n in voxrender.THIN or n in voxrender.SEE_THROUGH or n.endswith(
                ('_slab', '_stairs', '_wall', '_leaves', '_pane', '_fence', '_trapdoor', '_door', 'cauldron')):
            continue
        if 'glass' in n or 'grate' in n:
            continue
        solid.add(k)
    occ = solid
    s2 = 2 * s
    proj = lambda x, y, z: ((x - z) * s2, (x + z) * s - y * s2)
    items = [(p, b) for p, b in allv.items()
             if not ((p[0], p[1] + 1, p[2]) in occ and (p[0] + 1, p[1], p[2]) in occ and (p[0], p[1], p[2] + 1) in occ)]
    pts = [proj(*p) for p, _ in items]
    minu, maxu = min(p[0] for p in pts), max(p[0] for p in pts)
    minv, maxv = min(p[1] for p in pts), max(p[1] for p in pts)
    W, Hh = int(maxu - minu) + 4 * s2 + 60, int(maxv - minv) + 4 * s2 + 60
    ox, oy = -minu + 30 + s2, -minv + 30
    im = Image.new('RGBA', (W, Hh), bg + (255,))
    for (x, y, z), b in sorted(items, key=lambda t: (t[0][0] + t[0][1] + t[0][2], t[0][1], t[0][0] - t[0][2])):
        n = voxrender._name(b)
        glow = kit.emission(b) > 0
        u, v = proj(x, y, z)
        u, v = int(u + ox), int(v + oy)

        def level(p):
            if glow:
                return 15
            if keep is not None and p in vox and not vox[p].endswith(':air') and not keep(*p):
                return CUT                                  # a section through a wall: drawn flat
            return lv.get(p, 0)
        top, side = faces(b)
        if n in voxrender.CROSS or n.startswith('potted_'):
            sp = voxrender._sprite(b, 'cross', s)
            im.alpha_composite(lit(sp, ('cross', b, s), level((x, y, z))), (u - s, v + s2 - s))
            continue
        half = (n.endswith('_slab') and 'type=bottom' in b) or (n.endswith('_stairs') and 'half=bottom' in b)
        thin = n in voxrender.THIN
        dy = s if half else (s2 - max(1, s // 3) if thin else 0)
        if (x, y + 1, z) not in occ or half or thin:
            sp = _face_sprite(b, top, side, 'top', s)
            im.alpha_composite(lit(sp, ('top', b, s), level((x, y + 1, z))), (u - s2, v + dy))
        if thin:
            continue
        for face, nb, pos in (('left', (x, y, z + 1), (u - s2, v + s + dy)), ('right', (x + 1, y, z), (u, v + s + dy))):
            if nb in occ:
                continue
            f = face + ('_half' if half else '')
            sp = _face_sprite(b, top, side, f, s)
            im.alpha_composite(lit(sp, (f, b, s), level(nb)), pos)
    return im


_FS = {}


def _face_sprite(b, top, side, face, s):
    k = (top, side, face, s)
    if k in _FS:
        return _FS[k]
    if face == 'top':
        sp = voxrender._texture(top).transform((4 * s, 2 * s), Image.AFFINE, (4 / s, 8 / s, -8, -4 / s, 8 / s, 8),
                                                Image.NEAREST)
    else:
        left = face.startswith('left')
        coeffs = (8 / s, 0, 0, -4 / s, 8 / s, 0) if left else (8 / s, 0, 0, 4 / s, 8 / s, -8)
        tex = voxrender._texture(side)
        size = (2 * s, 3 * s)
        if face.endswith('_half'):
            tex = tex.crop((0, 8, 16, 16))
            size = (2 * s, 2 * s)
        sp = voxrender._shade(tex.transform(size, Image.AFFINE, coeffs, Image.NEAREST), 0.8 if left else 0.62)
    _FS[k] = sp
    return sp


# ------------------------------------------------------------------ rooms
def room_image(ts, name, scale=6):
    role, mask, var = name.rsplit('_', 2)
    ms = []
    v = kit.build(ts, role, mask_doors(mask), int(var), ms)
    deck = ts.DECK
    for (x, y, z, m) in ms:
        if not m.startswith('enves:stair_seal'):
            v[(x, y, z)] = kit.AIR
    cut = deck + 4
    structural = {st for k, st in ts.P.items() if isinstance(st, str) and k in STRUCTURAL}

    def keep(x, y, z):
        if y >= kit.CEIL_Y or ((x >= 18 or z >= 18) and y > deck):
            return False
        if y <= cut:
            return True
        st = v.get((x, y, z))
        if st is None or st in structural or kit.base(st).endswith(('_stairs', '_slab')):
            return False
        return kit.emission(st) > 0 or not kit.full(st) or kit.passable(v.get((x, y - 1, z)))
    lv = kit.light(v)
    im = render_lit(v, lv, scale=scale, keep=keep)
    share, darkest = kit.light_report(v, ms)
    return im, share, darkest, ms


def contact_sheet(ts, names, path, title):
    tiles = []
    for n in names:
        im, share, darkest, ms = room_image(ts, n)
        tiles.append((n, im, share, darkest))
    tw = max(im.size[0] for _, im, _, _ in tiles)
    th = max(im.size[1] for _, im, _, _ in tiles)
    cols = 4 if tw < 500 else 3
    rows = (len(tiles) + cols - 1) // cols
    W, Hh = cols * tw + 40, 130 + rows * (th + 44) + 20
    sheet = Image.new('RGB', (W, Hh), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 20), title, font=font(36, True), fill=INK)
    d.text((26, 68), 'Cortes a la altura de la cabeza; arriba sólo lo que cuelga o alumbra. Sombreado con la luz de '
           'bloque calculada.', font=font(17), fill=(200, 190, 170))
    d.text((26, 92), 'Luz: parte del piso transitable con luz 8 o más, y el punto más oscuro.', font=font(17),
           fill=(200, 190, 170))
    for i, (n, im, share, darkest) in enumerate(tiles):
        c, r = i % cols, i // cols
        x0, y0 = 20 + c * tw, 130 + r * (th + 44)
        d.text((x0 + 12, y0 + 6), '%s   luz %d%% (mín. %d)' % (n, round(share * 100), darkest),
               font=font(17, True), fill=INK)
        sheet.paste(im, (x0 + (tw - im.size[0]) // 2, y0 + 30))
    sheet.save(path)
    return path


# ------------------------------------------------------------------ top-down
_TOP = {}


def top_texture(state, px):
    key = (state, px)
    if key not in _TOP:
        top, side = faces(state)
        _TOP[key] = voxrender._texture(top).convert('RGBA').resize((px, px), Image.NEAREST)
    return _TOP[key]


def top_view(world, markers, path, px=8, cut=None, deck=0, legend=None, title=None, lv=None):
    """world: {(x, y, z): state} in any coordinates; draws the top block at or below `cut`."""
    xs = [p[0] for p in world]
    zs = [p[2] for p in world]
    x0, z0 = min(xs), min(zs)
    Wd, Hd = max(xs) - x0 + 1, max(zs) - z0 + 1
    cut = deck + 3 if cut is None else cut
    head = 150 if title else 0
    pad = max(0, 1500 - Wd * px) // 2 if title else 0
    im = Image.new('RGBA', (Wd * px + 2 * pad, Hd * px + head), BG + (255,))
    cols = {}
    for (x, y, z), s in world.items():
        if y <= cut and not s.endswith(':air'):
            cols.setdefault((x, z), []).append((y, s))
    for (x, z), stack in cols.items():
        stack.sort(reverse=True)
        top_y, top = stack[0]
        layers = [top]
        if (kit.core(top) in ('water', 'lava') or 'grate' in top or kit.base(top) in ('iron_bars', 'glass', 'chain')
                or kit.base(top).endswith(('_rod', '_bud', 'cluster', 'lantern', 'candle'))):
            for (y, s) in stack[1:]:
                layers.insert(0, s)
                if kit.full(s) and kit.core(s) not in ('water',) and 'grate' not in s:
                    break
        u, w = (x - x0) * px + pad, (z - z0) * px + head
        for s in layers:
            tex = top_texture(s, px)
            n = kit.base(s)
            if n.endswith(('_rod', '_bud', 'cluster', 'lantern', 'candle', 'chain')):
                small = tex.resize((max(2, px * 3 // 5),) * 2, Image.NEAREST)
                im.alpha_composite(small, (u + (px - small.size[0]) // 2, w + (px - small.size[1]) // 2))
            elif n == 'water':
                im.alpha_composite(_water(tex), (u, w))
            else:
                im.alpha_composite(tex, (u, w))
        shade = 1.0 if top_y <= deck + 1 else (0.55 if top_y >= cut else 0.8)
        if top_y < deck:
            shade = 0.85
        if lv is not None and top_y < cut and not kit.emission(top):
            shade *= bright(lv.get((x, top_y + 1, z), 0))
        if shade < 1.0:
            dark = Image.new('RGBA', (px, px), (0, 0, 0, int(255 * (1 - shade))))
            im.alpha_composite(dark, (u, w))
    d = ImageDraw.Draw(im)
    for (x, y, z, meta) in markers:
        kind = meta.split(':')[1]
        if kind == 'encounter' and meta.endswith('champion'):
            kind = 'champion'
        c = MARK.get(kind)
        if not c:
            continue
        u, w = (x - x0) * px + px // 2 + pad, (z - z0) * px + px // 2 + head
        r = px * 0.45 if kind not in ('vault_gate', 'stair_seal') else px * 0.3
        d.ellipse((u - r, w - r, u + r, w + r), fill=c, outline=(20, 16, 20))
    if title:
        d.text((24, 18), title[0], font=font(34, True), fill=INK)
        for k, line in enumerate(title[1:]):
            d.text((26, 62 + k * 21), line, font=font(15), fill=(200, 190, 170))
    im.convert('RGB').save(path)
    return path


def _water(tex):
    w = Image.new('RGBA', tex.size, (52, 96, 196, 140))
    return w


# ------------------------------------------------------------------ a floor
def floor_world(ts, floor, seed):
    world, markers, light = {}, [], {}
    arena = [c for c in floor.cells if floor.role.get(c) == 'arena']
    centre = None
    if arena:
        mx = sorted(c[0] for c in arena)[len(arena) // 2]
        mz = sorted(c[1] for c in arena)[len(arena) // 2]
        centre = (mx, mz)
    for c in sorted(floor.cells):
        role = floor.role.get(c, 'quiet')
        if c == centre:
            role = 'arena_center'
        variant = int(kit.rnd(seed, floor.depth, c[0], c[1]) * 3) if role in ('quiet', 'fight') else 0
        ms = []
        v = kit.build(ts, role, floor.doors(c), variant, ms)
        ox, oz = c[0] * kit.S, c[1] * kit.S
        for (x, y, z), s in v.items():
            world[(ox + x, y, oz + z)] = s
        for (x, y, z), l in kit.light(v).items():
            light[(ox + x, y, oz + z)] = l
        for (x, y, z, m) in ms:
            markers.append((ox + x, y, oz + z, m))
            if not m.startswith('enves:stair_seal'):
                world[(ox + x, y, oz + z)] = kit.AIR
    return world, markers, light


def floor_image(ts, seed, path):
    depth, label, roman = TILESETS[ts.NAME]
    floors = drlg.descent(seed)
    f = floors[depth - 1]
    world, markers, light = floor_world(ts, f, seed)
    roles = {}
    for c in f.cells:
        roles[f.role.get(c)] = roles.get(f.role.get(c), 0) + 1
    title = ('%s · %s, semilla %d (%d salas)' % (roman, label, seed, len(f.cells)),
             'Vista cenital a la altura de las rodillas, con la luz de bloque calculada; muros oscurecidos.',
             'Rojo: encuentros; carmesí: campeón; dorado: cofres; amarillo: santuario; turquesa: sello;',
             'violeta: escalera; blanco: llegada; gris: rejas de la bóveda; azul: mecanismo de la bóveda.',
             'Plano del oráculo drlg.py (el port a Java dibuja otros planos con las mismas reglas). ' +
             ', '.join('%s %d' % (k, n) for k, n in sorted(roles.items())))
    return top_view(world, markers, path, px=8, deck=ts.DECK, title=title, lv=light)


# ------------------------------------------------------------------ main
SHEET = ['quiet_nesw_0', 'quiet_nesw_1', 'quiet_nesw_2', 'quiet_ew_1', 'fight_nesw_0', 'fight_nesw_1',
         'fight_nesw_2', 'guard_nesw_0', 'start_nesw_0', 'exit_n_0', 'shrine_new_0', 'seal_s_0', 'vault_e_0',
         'vestibule_x_0', 'arena_center_nesw_0', 'portal_s_0']


def main():
    which = sys.argv[1] if len(sys.argv) > 1 else 'all'
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 2609
    os.makedirs(OUT, exist_ok=True)
    for name in (TILESETS if which == 'all' else [which]):
        ts = importlib.import_module(name)
        depth, label, roman = TILESETS[name]
        p = contact_sheet(ts, SHEET, os.path.join(OUT, '%s_salas.png' % name),
                          '%s · %s: salas' % (roman, label))
        print(p)
        print(floor_image(ts, seed, os.path.join(OUT, '%s_piso_%d.png' % (name, seed))))


if __name__ == '__main__':
    main()
