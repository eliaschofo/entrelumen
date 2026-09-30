"""Review renders of Terra's garden engine and its Terralight setup (software renders, never game captures).

The engine is drawn with art/authoring/model_iso.py from the real block models the build writes (the
companion's assets and the vanilla client JAR): unformed (twelve plain blocks), formed (the members draw
nothing and the core draws the whole engine), a mock of the plan's ghost, and a close-up of the pot and its
light in the dark. The crop that the block entity renderer draws in the pot is stood in by a small wheat
model. The Terralight setup keeps the voxel preview (voxrender).

Writes, to the folder given: engine_formed_{front,side,back}.png, engine_unformed_{front,back}.png,
engine_ghost_mock.png, engine_pot_closeup.png, terralight_setup.png.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, 'art', 'authoring'))
from model_iso import Assets, Scene  # noqa: E402

ASSETS_DIR = os.path.join(ROOT, 'companion', 'src', 'main', 'resources', 'assets')
BG = (206, 222, 238, 255)


def crop_model(stage='wheat_stage7', size=9):
    """A crop's four planes, scaled into the pot (the block entity draws the real crop, about 0.56 of a block)."""
    lo, hi = 8 - size / 2, 8 + size / 2
    a, b = lo + size / 4, hi - size / 4
    planes = [((a, 0, lo), (a, size, hi), 'east', 'west'), ((b, 0, lo), (b, size, hi), 'east', 'west'),
              ((lo, 0, a), (hi, size, a), 'north', 'south'), ((lo, 0, b), (hi, size, b), 'north', 'south')]
    elements = []
    for frm, to, f1, f2 in planes:
        faces = {f: {'uv': [0, 0, 16, 16], 'texture': '#crop'} for f in (f1, f2)}
        elements.append({'from': list(frm), 'to': list(to), 'shade': False, 'faces': faces})
    return {'textures': {'crop': 'minecraft:block/' + stage}, 'elements': elements}


def members(formed):
    """(model, offset, y) of the engine facing south with the core at the origin."""
    out = []
    for x in (-1, 0, 1):
        for y in (0, 1):
            for z in (-1, 0):
                if (x, y, z) == (0, 0, 0):
                    continue
                name = 'terra_garden_outlet' if (x, y, z) == (0, 0, -1) else 'terra_engine_casing'
                if not formed:
                    out.append(('entrelumen:block/' + name, (x, y, z), 0))
    if formed:
        out.append(('entrelumen:block/terra_garden_core_formed', (0, 0, 0), 0))
    else:
        out.append(('entrelumen:block/terra_garden_core_built', (0, 0, 0), 180))
    return out


def scene(assets, formed, ground=True, crop=True, emit=13):
    sc = Scene(assets)
    if ground:
        for x in range(-2, 3):
            for z in range(-2, 2):
                sc.add('minecraft:block/moss_block', (x, -1, z))
    for model, offset, y in members(formed):
        sc.add(model, offset, y=y, light=emit if 'formed' in model else 0)
    if formed and crop:
        # the soil is at y 24 of the formed model, centred on x 8, z 0 of the core's cell
        sc.add('crop', (0, 24 / 16, -0.5), data=crop_model())
    return sc


def ghost_mock(assets, folder, font):
    full = scene(assets, False).render(yaw=35, pitch=30, scale=10, background=BG)
    # the bare ground with the same framing: render the ground alone and paste it under the same bounds
    ground = Scene(assets)
    for x in range(-2, 3):
        for z in range(-2, 2):
            ground.add('minecraft:block/moss_block', (x, -1, z))
    ground_im = ground.render(yaw=35, pitch=30, scale=10, background=BG)
    canvas = Image.new('RGBA', full.size, BG)
    canvas.alpha_composite(ground_im, (0, full.size[1] - ground_im.size[1]))
    mock = Image.blend(canvas, full, 0.5)
    d = ImageDraw.Draw(mock)
    title = "Terra's Hydroponic Garden"
    w = d.textlength(title, font=font)
    d.text(((mock.width - w) / 2, 12), title, font=font, fill=(255, 255, 255, 255), stroke_width=2, stroke_fill=(30, 30, 30, 255))
    bar = (mock.width / 2 - 100, 44, mock.width / 2 + 100, 51)
    d.rectangle(bar, fill=(20, 20, 20, 255))
    d.rectangle((bar[0], bar[1], bar[0] + 40, bar[3]), fill=(120, 220, 120, 255))
    d.text((10, mock.height - 20), 'Mock of the ghost (Patchouli view), software render', font=ImageFont.load_default(size=12),
           fill=(40, 40, 40, 255))
    mock.convert('RGB').save(os.path.join(folder, 'engine_ghost_mock.png'))


def render_all(V, folder):
    os.makedirs(folder, exist_ok=True)
    assets = Assets(dirs=[ASSETS_DIR])
    font = ImageFont.load_default(size=18)
    views = {'front': (35, 28), 'side': (110, 28), 'back': (215, 28)}
    for name, (yaw, pitch) in views.items():
        im = scene(assets, True).render(yaw=yaw, pitch=pitch, scale=10, background=BG)
        im.convert('RGB').save(os.path.join(folder, f'engine_formed_{name}.png'))
    for name in ('front', 'back'):
        yaw, pitch = views[name]
        im = scene(assets, False).render(yaw=yaw, pitch=pitch, scale=10, background=BG)
        im.convert('RGB').save(os.path.join(folder, f'engine_unformed_{name}.png'))
    ghost_mock(assets, folder, font)
    # the heart in the dark: what glows (the pot's rim, the strip over the grille, the headlamps)
    close = scene(assets, True, ground=False, emit=0).render(yaw=25, pitch=35, scale=22, light='dark', room=1,
                                                     background=(12, 14, 18, 255))
    close.convert('RGB').save(os.path.join(folder, 'engine_pot_closeup.png'))
    render_crystal_setup(folder)


def render_crystal_setup(folder):
    """The Terralight setup, block scale (voxrender): column A (grass, the crystal, the lamp) beside column B (dirt,
    the rod, two cables; copper blocks stand in for any mod's energy cable), with the four stages beside it."""
    import voxrender
    comp = os.path.join(ASSETS_DIR, 'entrelumen', 'textures')
    voxrender.CROSS = set(voxrender.CROSS) | {'terralight_crystal_0', 'terralight_crystal_1', 'terralight_crystal_2',
                                              'terralight_crystal_3', 'terralight_grounding_rod', 'terra_grow_lamp'}
    base_sprite = voxrender._sprite

    def sprite(state, face, s):
        name = state.split('[')[0].split(':')[1]
        if face == 'cross' and state.startswith('entrelumen:'):
            sub = 'item' if name == 'terra_grow_lamp' else 'block'
            im = Image.open(os.path.join(comp, sub, name + '.png')).convert('RGBA').crop((0, 0, 16, 16))
            return im.resize((2 * s, 2 * s), Image.NEAREST)
        return base_sprite(state, face, s)
    voxrender._sprite = sprite
    V = {(0, 0, 0): 'minecraft:dirt', (0, 1, 0): 'entrelumen:terralight_grounding_rod',
         (0, 2, 0): 'minecraft:oxidized_copper', (0, 3, 0): 'minecraft:oxidized_copper',
         (1, 0, 0): 'minecraft:dirt', (1, 1, 0): 'minecraft:grass_block', (1, 2, 0): 'entrelumen:terralight_crystal_3',
         (1, 3, 0): 'entrelumen:terra_grow_lamp'}
    for i in range(4):
        V[(3 + i, 0, 2)] = 'minecraft:grass_block'
        V[(3 + i, 1, 2)] = 'entrelumen:terralight_crystal_%d' % i
    V = {(x, y + 1, z): b for (x, y, z), b in V.items()}
    voxrender.render(V, os.path.join(folder, 'terralight_setup.png'), scale=18, ground=4)
    voxrender._sprite = base_sprite
