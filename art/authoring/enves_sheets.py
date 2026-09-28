"""Review sheets for the Envés blocks (art/authoring/draw_enves_blocks.py): one PNG per block, never committed.

Each sheet shows both states from three isometric angles, the player's eye in the Envés's light (close and
about ten blocks away), the textures at 8x, the block among the Osarios tileset's own blocks, and the
references it came from, rendered from the pinned JARs. Every image is a software render (model_iso.py),
not a game capture.
"""
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

from model_iso import Assets, Scene, CLIENT_JAR

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
MOD_ASSETS = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets')
ATM10 = 'E:/curseforge/Instances/All the Mods 10 - ATM10/mods/'
ARS = ATM10 + 'ars_nouveau-1.21.1-5.13.1.jar'
OCCULTISM = ATM10 + 'occultism-1.21.1-neoforge-1.224.4.jar'
BG = (38, 38, 44, 255)
PANEL = (52, 52, 60, 255)
TEXT = (232, 228, 216, 255)
DIM = (170, 166, 156, 255)
ISO = ((35, 30), (-55, 30), (15, 62))
ROOM = 7                     # block light next to a soul lantern and candles, where the pieces stand


def font(size):
    for name in ('arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


F_TITLE, F_TEXT, F_SMALL = font(26), font(15), font(13)


class Sheet:
    """A vertical stack of labelled rows of images."""

    def __init__(self, width=1500):
        self.W = width
        self.parts = []

    def title(self, text, sub):
        self.parts.append(('title', text, sub))

    def row(self, label, images, captions=None, note=None):
        self.parts.append(('row', label, images, captions or [''] * len(images), note))

    def render(self, path):
        pad, gap = 20, 14
        blocks = []
        for p in self.parts:
            if p[0] == 'title':
                im = Image.new('RGBA', (self.W, 96), BG)
                d = ImageDraw.Draw(im)
                d.text((pad, 14), p[1], font=F_TITLE, fill=TEXT)
                y = 52
                for line in p[2]:
                    d.text((pad, y), line, font=F_TEXT, fill=DIM)
                    y += 19
                im = im.crop((0, 0, self.W, y + 8))
            else:
                _, label, images, captions, note = p
                x, y, line_h = pad, 34, 0
                placed = []
                for im_, cap in zip(images, captions):
                    w, h = im_.size
                    if x + w > self.W - pad and placed:
                        x, y = pad, y + line_h + 26
                        line_h = 0
                    placed.append((x, y, im_, cap))
                    x += w + gap
                    line_h = max(line_h, h)
                H = y + line_h + 26 + (22 * len(note) if note else 0) + 10
                im = Image.new('RGBA', (self.W, H), BG)
                d = ImageDraw.Draw(im)
                d.rectangle((0, 0, self.W, 26), fill=PANEL)
                d.text((pad, 5), label, font=F_TEXT, fill=TEXT)
                for (px, py, im_, cap) in placed:
                    im.alpha_composite(im_, (px, py))
                    if cap:
                        d.text((px, py + im_.size[1] + 3), cap, font=F_SMALL, fill=DIM)
                if note:
                    ny = y + line_h + 26
                    for line in note:
                        d.text((pad, ny), line, font=F_SMALL, fill=DIM)
                        ny += 22
            blocks.append(im)
        out = Image.new('RGBA', (self.W, sum(b.size[1] for b in blocks)), BG)
        y = 0
        for b in blocks:
            out.alpha_composite(b, (0, y))
            y += b.size[1]
        out.save(path)


def big(image, scale=8):
    return image.resize((image.size[0] * scale, image.size[1] * scale), Image.NEAREST)


def framed(image, colour=(24, 24, 28, 255), pad=2):
    out = Image.new('RGBA', (image.size[0] + 2 * pad, image.size[1] + 2 * pad), colour)
    out.alpha_composite(image, (pad, pad))
    return out


def jar_texture(jar, rel):
    with zipfile.ZipFile(jar) as z:
        from io import BytesIO
        im = Image.open(BytesIO(z.read(rel))).convert('RGBA')
    if im.size[1] > im.size[0]:
        im = im.crop((0, 0, im.size[0], im.size[0]))
    return im


class Kit:
    """Assets with the drafts layered over the repo and the JARs, and scene helpers."""

    def __init__(self, textures, models):
        over = {}
        for name, g in textures.items():
            base = name.split('__f')[0]
            if '__f' in name and not name.endswith('__f0'):
                continue
            over['entrelumen:block/' + base] = g.image()
        self.assets = Assets(dirs=[MOD_ASSETS], jars=[CLIENT_JAR, ARS, OCCULTISM], overrides=over)
        self.models = models
        self.textures = textures

    def scene(self):
        return Scene(self.assets)

    def ours(self, scene, name, at=(0, 0, 0), y=0, light=0):
        return scene.add('entrelumen:block/' + name, at, y=y, data=self.models[name], light=light)

    def iso(self, name, y=0, light='day', level=0, scale=9, angles=ISO):
        out = []
        for yaw, pitch in angles:
            s = self.ours(self.scene(), name, y=y, light=level)
            out.append(s.render(yaw=yaw, pitch=pitch, scale=scale, light=light, room=ROOM, background=BG))
        return out

    def ref(self, rid, y=0, scale=6, angles=((35, 30),), light='day', level=0, data=None):
        return [self.scene().add(rid, y=y, data=data, light=level).render(yaw=a, pitch=p, scale=scale, light=light,
                                                                           background=BG) for a, p in angles]


def floor(s, x0, x1, z0, z1, y=-1, block='minecraft:block/deepslate_tiles'):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.add(block, (x, y, z))


def wall(s, x0, x1, z, h=3, y=0):
    for x in range(x0, x1 + 1):
        s.add('minecraft:block/calcite', (x, y, z))                   # the Osarios course
        for k in range(1, h + 1):
            s.add('minecraft:block/tuff_bricks', (x, y + k, z))


def bars(s, at):
    s.add('minecraft:block/iron_bars_post_ends', at)
    s.add('minecraft:block/iron_bars_side', at, y=90)
    s.add('minecraft:block/iron_bars_side_alt', at, y=90)


def eye(kit, build, dist=2.2, yaw=24, size=(400, 290), light='dark', target=(0.5, 0.62, 0.5)):
    """A player's view: eyes 1.62 above the floor the piece stands one block over (so level with 0.62 of it)."""
    s = kit.scene()
    build(s)
    return s.render_eye(target, yaw=yaw, pitch=0, dist=dist, size=size, light=light, room=ROOM, background=(8, 8, 10, 255))


def far(kit, build, dist=10, yaw=18):
    """About ten blocks away with the default 70-degree view, cropped 1:1 from a 1280x720 frame, then 3x."""
    s = kit.scene()
    build(s)
    frame = s.render_eye((0.5, 0.62, 0.5), yaw=yaw, pitch=0, dist=dist, size=(1280, 720), light='dark', room=ROOM,
                         background=(8, 8, 10, 255))
    crop = frame.crop((640 - 60, 360 - 45, 640 + 60, 360 + 45))
    return big(crop, 3)


def shrine_sheet(kit, out):
    sh = Sheet()
    sh.title('enves_shrine · el relicario', [
        'A drop of sour light held up by a crown of ribs on a spine of vertebrae. Fresh it glows and asks to be touched;',
        'spent it is a dead, broken olive husk. States: spent=false | spent=true (light 13 | 2).'])
    fresh, spent = kit.iso('enves_shrine', level=13), kit.iso('enves_shrine_spent', level=2)
    sh.row('Both states, three isometric angles (daylight shading)', fresh + spent,
           ['fresh 35/30', 'fresh -55/30', 'fresh from above', 'spent 35/30', 'spent -55/30', 'spent from above'])

    def on_dais(name, level):
        def build(s):
            floor(s, -3, 3, -3, 3)
            for x in (-1, 0, 1):
                for z in (-1, 0, 1):
                    s.add('minecraft:block/chiseled_tuff', (x, 0, z))
            kit.ours(s, name, (0, 1, 0), light=level)
        return build
    sh.row("The player's eye in the Envés (room light 7, ambient 0.06): close, and about ten blocks away",
           [eye(kit, on_dais('enves_shrine', 13), target=(0.5, 1.62, 0.5)), eye(kit, on_dais('enves_shrine_spent', 2), target=(0.5, 1.62, 0.5)),
            far(kit, lambda s: kit.ours(s, 'enves_shrine', light=13)), far(kit, lambda s: kit.ours(s, 'enves_shrine_spent', light=2))],
           ['fresh, 2.2 blocks', 'spent, 2.2 blocks', 'fresh, 10 blocks (3x crop)', 'spent, 10 blocks (3x crop)'])
    names = ['enves_shrine_plinth', 'enves_shrine_bone', 'enves_shrine_bone_top', 'enves_gold', 'enves_shrine_heart', 'enves_shrine_heart_dead']
    sh.row('Textures at 8x (native 16x16 grids)', [framed(big(kit.textures[n].image())) for n in names], names)

    def room(name, level):
        def build(s):
            floor(s, -3, 3, -3, 2)
            wall(s, -3, 3, -3)
            for x in (-1, 0, 1):
                for z in (-1, 0, 1):
                    s.add('minecraft:block/chiseled_tuff', (x, 0, z))
            for x in (-3, 3):
                s.add('minecraft:block/calcite', (x, 0, -1))
                s.add('minecraft:block/white_candle_three_candles_lit', (x, 1, -1))
            s.add('minecraft:block/bone_block', (-2, 0, -2))
            s.add('minecraft:block/bone_block', (2, 0, -2))
            s.add('minecraft:block/soul_lantern', (2, 1, -2), light=10)
            s.add('minecraft:block/soul_lantern', (-2, 1, -2), light=10)
            kit.ours(s, name, (0, 1, 0), light=level)
        return build
    ctx = []
    for name, level in (('enves_shrine', 13), ('enves_shrine_spent', 2)):
        for light in ('day', 'dark'):
            s = kit.scene()
            room(name, level)(s)
            ctx.append(s.render(yaw=30, pitch=28, scale=4, light=light, room=4, background=BG))
    sh.row('In the Osarios: on the chapel dais of chiseled tuff, calcite, tuff bricks, bones, candles and soul lanterns', ctx,
           ['fresh, daylight shading', 'fresh, Envés light', 'spent, daylight shading', 'spent, Envés light'])
    refs = (kit.ref('minecraft:block/respawn_anchor_4') + kit.ref('minecraft:block/respawn_anchor_0') +
            kit.ref('minecraft:block/vault_active') + kit.ref('minecraft:block/vault') + kit.ref('minecraft:block/enchanting_table'))
    tex = [framed(big(jar_texture(CLIENT_JAR, 'assets/minecraft/textures/block/%s.png' % n), 4)) for n in
           ('respawn_anchor_top', 'respawn_anchor_top_off', 'vault_front_on', 'vault_front_off')]
    ww = Image.open(os.path.join(MOD_ASSETS, 'entrelumen/textures/entity/white_wither/white_wither.png')).convert('RGBA')
    sh.row('References (rendered from the pinned client JAR; the White Wither is ours)', refs + tex + [framed(big(ww, 2))],
           ['respawn_anchor_4', 'respawn_anchor_0', 'vault active', 'vault (spent)', 'enchanting_table',
            'anchor top', 'anchor top off', 'vault front on', 'vault front off', 'white_wither.png (ours)'],
           ['Respawn anchor: charge is a glowing top and side pips, emptiness a black well; the same block reads full or used. Took: the swap',
            'of a lit core for a dead one as the whole state change, and a light level that falls with it (13 to 2, like 15 to 0).',
            'Trial vault: active vs spent by what glows (orange keyhole and eyes, then grey). Took: nothing else changes, so the eye goes to it.',
            'Enchanting table: a 12-high slab that carries ornaments at its corners. Took: a full-width dark base under a smaller ornament.',
            "White Wither (art/authoring/draw_white_wither.py): the ivory ribs and gold of the vessel that holds the sour light."])
    sh.render(os.path.join(out, 'enves_shrine.png'))


def brazier_sheet(kit, out):
    sh = Sheet()
    sh.title('enves_brazier · la campana del revés', [
        "The Envés copies Heliodor backwards: its bells stand upside down and hold fire, the clapper up like a wick. They already",
        'chime when the gate plays its order. States: lit=false | lit=true (light 0 | 14); the flame is emissive and flickers (4 frames).'])
    cold, lit = kit.iso('enves_brazier'), kit.iso('enves_brazier_lit', level=14)
    sh.row('Both states, three isometric angles (daylight shading)', cold + lit,
           ['cold 35/30', 'cold -55/30', 'cold from above', 'lit 35/30', 'lit -55/30', 'lit from above'])

    def pedestal(name, level):
        def build(s):
            s.add('minecraft:block/calcite', (0, 0, 0))
            kit.ours(s, name, (0, 1, 0), light=level)
        return build
    frames = [framed(big(kit.textures['enves_brazier_flame__f%d' % i].image())) for i in range(4)]
    sh.row("The player's eye in the Envés (room light 7): on a seal's calcite pedestal, close and about ten blocks away",
           [eye(kit, pedestal('enves_brazier', 0), target=(0.5, 1.62, 0.5)), eye(kit, pedestal('enves_brazier_lit', 14), target=(0.5, 1.62, 0.5)),
            far(kit, lambda s: kit.ours(s, 'enves_brazier')), far(kit, lambda s: kit.ours(s, 'enves_brazier_lit', light=14))],
           ['cold, 2.2 blocks', 'lit, 2.2 blocks', 'cold, 10 blocks (3x crop)', 'lit, 10 blocks (3x crop)'])
    names = ['enves_brazier_bell', 'enves_brazier_bell_top', 'enves_brazier_ash', 'enves_brazier_embers', 'enves_gold']
    sh.row('Textures at 8x (native 16x16 grids), with the flame\'s four frames', [framed(big(kit.textures[n].image())) for n in names] + frames,
           names + ['flame f0', 'f1', 'f2', 'f3'])

    def gate(states):
        def build(s):
            floor(s, -3, 3, -2, 2)
            for x in (-3, -2, 2, 3):
                s.add('minecraft:block/calcite', (x, 0, 0))
                for k in (1, 2, 3):
                    s.add('minecraft:block/tuff_bricks', (x, k, 0))
            for i, x in enumerate((-1, 0, 1)):
                kit.ours(s, 'enves_brazier_lit' if states[i] else 'enves_brazier', (x, 0, 0), light=14 if states[i] else 0)
                bars(s, (x, 1, 0))
                bars(s, (x, 2, 0))
                s.add('minecraft:block/chiseled_tuff', (x, 3, 0))
        return build
    ctx = []
    for states in ((True, False, True), (False, False, False)):
        for light in ('day', 'dark'):
            s = kit.scene()
            gate(states)(s)
            ctx.append(s.render(yaw=28, pitch=24, scale=4, light=light, room=4, background=BG))
    sh.row("In the Osarios: a vault's gate, the three braziers in its bottom row, bars above, tuff bricks and calcite beside", ctx,
           ['two lit, daylight', 'two lit, Envés light', 'all cold, daylight', 'all cold, Envés light'])
    bell = bell_model()
    refs = (kit.ref('minecraft:block/soul_campfire', angles=((35, 30),)) + kit.ref('minecraft:block/campfire_off') +
            kit.ref('ars_nouveau:block/ritual_brazier') + kit.ref('occultism:block/sacrificial_bowl', scale=8) +
            kit.ref('minecraft:block/bell_floor', data=bell, scale=7))
    tex = [framed(big(jar_texture(CLIENT_JAR, 'assets/minecraft/textures/block/soul_campfire_fire.png'), 4)),
           framed(big(jar_texture(CLIENT_JAR, 'assets/minecraft/textures/entity/bell/bell_body.png'), 4)),
           framed(big(jar_texture(ARS, 'assets/ars_nouveau/textures/block/ritual_brazier.png'), 4)),
           framed(big(jar_texture(OCCULTISM, 'assets/occultism/textures/block/sacrificial_bowl.png'), 4))]
    sh.row('References (rendered from the pinned JARs)', refs + tex,
           ['soul_campfire', 'campfire_off', "Ars ritual_brazier", 'Occultism sacrificial_bowl', 'bell (body approx.)',
            'soul_campfire_fire', 'bell_body', 'ritual_brazier.png', 'sacrificial_bowl.png'],
           ['Soul campfire: fire as two crossed planes of animated, cold-coloured flame over a base that stays the same lit or out. Took: the',
            'crossed planes (10 x 10 here, 1 texel per unit), the flicker, and that only the flame and embers change with the state.',
            "Ars Nouveau ritual brazier: a raised bowl whose rim and inside glow. Took: the glowing inside of the bowl when lit.",
            'Occultism sacrificial bowl: a small stone bowl, 6 high on a 12 x 12 footprint, feet under it. Took: the proportions that fit the',
            "block's 12 x 7 x 12 shape. Vanilla bell (bell_body.png): the flared lip, the sound bow and the crown's loops, upside down",
            '(the loops became the four feet).'])
    sh.render(os.path.join(out, 'enves_brazier.png'))


def bell_model():
    """The vanilla bell's body (an entity model) as block elements on its 32x32 texture, for the reference render."""
    def boxuv(u, v, w, h, d):
        s = 0.5                                                   # a 32x32 texture: 1 uv unit is 2 texels
        return {'up': [(u + d) * s, v * s, (u + d + w) * s, (v + d) * s],
                'down': [(u + d + w) * s, v * s, (u + d + 2 * w) * s, (v + d) * s],
                'west': [u * s, (v + d) * s, (u + d) * s, (v + d + h) * s],
                'north': [(u + d) * s, (v + d) * s, (u + d + w) * s, (v + d + h) * s],
                'east': [(u + d + w) * s, (v + d) * s, (u + 2 * d + w) * s, (v + d + h) * s],
                'south': [(u + 2 * d + w) * s, (v + d) * s, (u + 2 * d + 2 * w) * s, (v + d + h) * s]}
    body = boxuv(0, 0, 6, 7, 6)
    lip = boxuv(0, 13, 8, 2, 8)
    return {'textures': {'b': 'minecraft:entity/bell/bell_body'}, 'elements': [
        {'from': [5, 6, 5], 'to': [11, 13, 11], 'faces': {f: {'uv': uv, 'texture': '#b'} for f, uv in body.items()}},
        {'from': [4, 4, 4], 'to': [12, 6, 12], 'faces': {f: {'uv': uv, 'texture': '#b'} for f, uv in lip.items()}}]}


def mirror_sheet(kit, out):
    sh = Sheet()
    sh.title('enves_mirror · el biombo', [
        "A two-faced silver plate on the diagonal between two bone posts in the block's corners, each with a pilot light. The posts",
        "and lights mark the corners the mirror joins, so '/' and '\\' read from any side. States: aim=0 ('/') | aim=1 ('\\', turned 90).",
    ])
    a0, a1 = kit.iso('enves_mirror', level=3), kit.iso('enves_mirror', y=90, level=3)
    sh.row('Both states, three isometric angles (daylight shading)', a0 + a1,
           ["aim 0 '/' 35/30", 'aim 0 -55/30', 'aim 0 from above', "aim 1 '\\' 35/30", 'aim 1 -55/30', 'aim 1 from above'])

    def on_pedestal(rot):
        def build(s):
            s.add('minecraft:block/calcite', (0, 0, 0))
            kit.ours(s, 'enves_mirror', (0, 1, 0), y=rot, light=3)
        return build
    views = []
    for rot in (0, 90):
        for yaw in (0, 35):
            views.append(eye(kit, on_pedestal(rot), yaw=yaw, target=(0.5, 1.62, 0.5)))
    views += [far(kit, lambda s: kit.ours(s, 'enves_mirror', light=3), yaw=0),
              far(kit, lambda s: kit.ours(s, 'enves_mirror', y=90, light=3), yaw=0)]
    sh.row("The player's eye in the Envés (room light 7): from the south and from 35 degrees, then about ten blocks away", views,
           ['aim 0, south', 'aim 0, 35 deg', 'aim 1, south', 'aim 1, 35 deg', 'aim 0, 10 blocks', 'aim 1, 10 blocks'])
    names = ['enves_mirror_glass', 'enves_mirror_bone', 'enves_mirror_foot', 'enves_gold', 'enves_shrine_heart']
    sh.row('Textures at 8x (native 16x16 grids; the pilot lights use the sour core of the shrine\'s drop)',
           [framed(big(kit.textures[n].image())) for n in names], names)

    def grid(s):
        floor(s, -2, 2, -2, 2, y=-1)
        for x in range(-2, 3):
            for z in range(-2, 3):
                s.add('minecraft:block/calcite' if (x, z) != (0, 0) else 'minecraft:block/chiseled_tuff', (x, 0, z))
        s.add('minecraft:block/lodestone', (0, 1, 0))                                   # the seal (provisional look)
        s.add('minecraft:block/verdant_froglight', (-2, 1, 2), light=15)                # the light's font
        kit.ours(s, 'enves_mirror', (-2, 1, -1), light=3)
        kit.ours(s, 'enves_mirror', (1, 1, -1), y=90, light=3)
        kit.ours(s, 'enves_mirror', (1, 1, 1), light=3)
        wall(s, -3, 3, -3)
    ctx = []
    for light in ('day', 'dark'):
        s = kit.scene()
        grid(s)
        ctx.append(s.render(yaw=25, pitch=40, scale=4, light=light, room=4, background=BG))
    s = kit.scene()
    grid(s)
    ctx.append(s.render(yaw=0, pitch=89.9, scale=6, background=BG))
    sh.row("In the Osarios: a seal's light grid on calcite, the froglight font, the seal, three mirrors ('/', '\\', '/')", ctx,
           ['daylight', 'Envés light', 'straight down'])
    refs = (kit.ref('entrelumen:block/ruin_mirror', angles=((200, 25),), scale=7) + kit.ref('entrelumen:block/ruin_mirror_diagonal', angles=((200, 25),), scale=7) +
            kit.ref('minecraft:block/observer', angles=((35, 55),)))
    old = kit.scene().add('entrelumen:block/enves_mirror', data=placeholder_mirror()).render(yaw=35, pitch=30, scale=7, background=BG)
    tex = [framed(big(jar_texture(CLIENT_JAR, 'assets/minecraft/textures/block/%s.png' % n), 4)) for n in ('iron_block', 'copper_block', 'observer_top')]
    sh.row('References (our ruin mirrors from the companion; vanilla from the pinned client JAR)', refs + [old] + tex,
           ['ruin_mirror (face)', 'ruin_mirror_diagonal', 'observer', 'provisional enves_mirror', 'iron_block', 'copper_block', 'observer_top'],
           ['Our ruin mirror (companion models/block/ruin_mirror*.json): a plate standing on a small post, an iron face that reflects and',
            'a copper back; the diagonal variant is the same plate turned. Took the shared language: a flat plate, a metal face, a frame on',
            'a foot. The Envés one reflects on both faces, so both are silver, and it turns only between two diagonals.',
            'Observer: its top arrow says which way it looks. Took the idea that the direction must be drawn on the piece, here as two',
            "corner posts with pilot lights and gold edges along the foot, which also read from the side where a top arrow would not."])
    sh.render(os.path.join(out, 'enves_mirror.png'))


def placeholder_mirror():
    return {'parent': 'minecraft:block/block', 'textures': {'base': 'minecraft:block/calcite', 'glass': 'minecraft:block/iron_block',
                                                            'frame': 'minecraft:block/gold_block'},
            'elements': [{'from': [1, 0, 1], 'to': [15, 3, 15], 'faces': {f: {'texture': '#base'} for f in
                                                                           ('north', 'south', 'east', 'west', 'up', 'down')}},
                         {'from': [0.5, 5, 7.5], 'to': [15.5, 14, 8.5], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True},
                          'faces': {'north': {'texture': '#glass'}, 'south': {'texture': '#glass'}, 'east': {'texture': '#frame'},
                                    'west': {'texture': '#frame'}, 'up': {'texture': '#frame'}}}]}


def glyph_sheet(kit, out):
    from draw_enves_blocks import GLYPH_NAMES
    sh = Sheet()
    sh.title('enves_glyph · las piedras de glifo', [
        'Dark Envés stone with a gold inlay. The four glyphs differ in kind, not in detail: a radial sun, a closed eye, the tree over',
        'its reversed copy (the roots), the stair going down. States: glyph=0..3; the clue behind the bars uses the same stones.'])
    iso = []
    for i in range(4):
        iso += kit.iso('enves_glyph_%d' % i, angles=((35, 25),), scale=7)
    small = [kit.iso('enves_glyph_%d' % i, angles=((35, 25),), scale=2)[0] for i in range(4)]
    sh.row('The four states (daylight shading), then the same at 2 px per texel', iso + small,
           ['0 %s' % GLYPH_NAMES[0], '1 %s' % GLYPH_NAMES[1], '2 %s' % GLYPH_NAMES[2], '3 %s' % GLYPH_NAMES[3], '', '', '', ''])
    iso2 = []
    for i in range(4):
        iso2 += kit.iso('enves_glyph_%d' % i, angles=((-55, 30), (20, 60)), scale=6)
    sh.row('Two more angles', iso2)

    def gate(s):
        floor(s, -3, 3, -4, 2)
        for x in (-3, -2, 2, 3):
            s.add('minecraft:block/calcite', (x, 0, 0))
            for k in (1, 2, 3):
                s.add('minecraft:block/tuff_bricks', (x, k, 0))
        for x, g in zip((-1, 0, 1), (3, 1, 0)):
            kit.ours(s, 'enves_glyph_%d' % g, (x, 0, 0))
            bars(s, (x, 1, 0))
            bars(s, (x, 2, 0))
            s.add('minecraft:block/chiseled_tuff', (x, 3, 0))
        for x, g in zip((-1, 0, 1), (0, 1, 3)):                  # the clue: the code, which the gate wants reversed
            s.add('minecraft:block/calcite', (x, 0, -2))
            kit.ours(s, 'enves_glyph_%d' % g, (x, 1, -2))

    def front(s):
        gate(s)
    views = []
    for light in ('day', 'dark'):
        s = kit.scene()
        gate(s)
        views.append(s.render(yaw=20, pitch=22, scale=4, light=light, room=5, background=BG))
    views.append(eye(kit, front, dist=4.5, yaw=10, size=(420, 300), target=(0.5, 1.62, 0.5)))
    names = ['enves_glyph_%d' % i for i in range(4)] + ['enves_glyph_top']
    sh.row("In the Osarios: behind the bars, on calcite, the clue reads sun, eye, stair; the gate's stones open when they read it "
           "backwards (stair, eye, sun, as here)", views,
           ['from outside, daylight', 'Envés light', "the player's eye, 4.5 blocks"])
    sh.row('Textures at 8x (native 16x16 grids)', [framed(big(kit.textures[n].image())) for n in names], names)
    refs = []
    for n in ('chiseled_quartz_block', 'chiseled_red_sandstone', 'chiseled_tuff', 'chiseled_deepslate'):
        refs += kit.ref('minecraft:block/' + n, scale=5)
    pots = [framed(big(jar_texture(CLIENT_JAR, 'assets/minecraft/textures/entity/decorated_pot/%s_pottery_pattern.png' % n), 4))
            for n in ('skull', 'arms_up', 'heart', 'prize')]
    sh.row('References (pinned client JAR): the provisional chiseled faces, and the pottery patterns', refs + pots,
           ['chiseled quartz', 'chiseled red sandstone', 'chiseled tuff', 'chiseled deepslate', 'skull pattern', 'arms_up', 'heart', 'prize'],
           ['Chiseled blocks (the provisional glyphs): a carved motif framed by a border, 1-texel grooves. Took: the frame and the carving,',
            'but they tell apart mostly by material colour, which is what the redesign removes.',
            'Pottery patterns: one silhouette per sherd in a single tone on a flat panel, readable at a glance. Took: one bold silhouette per',
            'stone, all in one colour, each of a different kind of shape (radial, closed, axial, stepped).'])
    sh.render(os.path.join(out, 'enves_glyph.png'))


def sheets(textures, models, out):
    os.makedirs(out, exist_ok=True)
    kit = Kit(textures, models)
    shrine_sheet(kit, out)
    brazier_sheet(kit, out)
    mirror_sheet(kit, out)
    glyph_sheet(kit, out)
    print('review sheets in', out)
