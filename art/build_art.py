"""Native pixel artwork: editable 16x16 text grids -> PNG textures, models and previews.

Each grid in art/grids/{item,block}/<name>.txt is the source of truth (palette lines
'X #rrggbb', a blank line, then 16 rows; '.' is transparent). Rendering is a direct
texel copy: no resampling, antialiasing or dithering. Provenance for every grid, including
the PixelLab draft it started from when there was one, lives in art/grids/provenance.json.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art'
GRIDS = ART / 'grids'
PACK = ROOT / 'pack/resourcepacks/entrelumen'
ASSETS = PACK / 'assets/entrelumen'
COMPANION = ROOT / 'companion/src/main/resources/assets/entrelumen'

ITEMS = ['atlas', 'raw_lens', 'survey_notes', 'signal_core', 'calibration_frame', 'energy_coupler', 'living_matrix',
         'ration_bundle', 'routing_matrix', 'propagation_core', 'power_regulator', 'inventory_sensor', 'handling_core',
         'spectral_lens', 'horizon_chart', 'ecosystem_capsule', 'containment_seal', 'ark_bus', 'renewal_engine',
         'habitation_contract', 'terra_arm', 'light_key', 'light_key_broken', 'heliodor_relic_1',
         'heliodor_relic_2', 'heliodor_relic_3', 'heart_of_heliodor']
AUGMENTS = ['burning', 'echoing', 'ignore_conditions', 'ignore_light', 'ignore_players', 'initial_health', 'max_delay',
            'max_nearby', 'min_delay', 'no_ai', 'player_range', 'redstone_control', 'silent', 'spawn_count', 'spawn_range', 'youthful']
ITEMS += ['augment_' + a for a in AUGMENTS]
# Luminous content: animated items are stored as frame grids <name>__f<N>.txt and exported as vertical strips.
DISCIPLINES = ['engineering', 'arcane', 'nature', 'exploration', 'logistics', 'habitation']
ANIMATED = {**{'luminosity_' + d: 8 for d in DISCIPLINES}, 'luminous_ingot': 8}
# Luminosities flicker like vanilla soul fire (frametime 2); the ingot rests on frame 0 so its glimmer stays occasional.
FIRE_ANIMATION = {'animation': {'frametime': 2}}
ANIMATION = {'animation': {'frametime': 3, 'frames': [{'index': 0, 'time': 40}, 1, 2, 3, 4, 5, 6, 7]}}
HANDHELD = {'luminous_sword', 'luminous_pickaxe', 'luminous_axe', 'luminous_shovel', 'luminous_hoe'}
ITEMS += list(ANIMATED) + sorted(HANDHELD) + ['luminous_helmet', 'luminous_chestplate', 'luminous_leggings', 'luminous_boots']
ARMOR_LAYERS = ['luminous_layer_1', 'luminous_layer_2']   # 64x32 PNG sources in art/armor/
COMPASS_DIMENSIONS = ['overworld', 'nether', 'end', 'aether', 'twilight', 'other']   # entrelumen:dimension 0..5
# Enchanting shelves (cube_column: side + end) and the Atlas Library (cube_bottom_top).
SHELVES = {'cartographer_shelf': 'shelf_end_wood', 'patina_shelf': 'shelf_end_copper',
           'lumen_shelf': 'shelf_end_tuff', 'horizon_shelf': 'shelf_end_verdigris'}
LIBRARY = {'atlas_library': ('atlas_library', 'atlas_library_top', 'module_bottom')}
MODULES = ['engineering_module', 'arcane_module', 'nature_module', 'exploration_module', 'logistics_module',
           'habitation_module', 'ark_controller']
# Sculpted (voxel) block models authored by art/authoring/sym_altars.py, stored as JSON sources.
SCULPTED = ['renewal_altar', 'terraform_altar', 'peace_altar', 'growth_altar', 'time_altar', 'repose_altar']
BLOCK_MODELS_ONLY = ['solsticio_portal_dormant', 'solsticio_portal_open']   # the mod owns their blockstate; no item form
BLOCKS = (MODULES + ['module_top', 'module_bottom'] + list(SHELVES) + sorted(set(SHELVES.values()))
          + ['atlas_library', 'atlas_library_top', 'altar_stone', 'altar_plinth_top', 'renewal_altar_top',
             'terraform_altar_top', 'peace_altar_top', 'growth_altar_top', 'time_altar_top',
             'repose_altar_top', 'altar_voxels'])
PALETTE_TEXTURES = {'block/altar_voxels'}   # one texel per colour; exempt from the per-texture colour budget

COMPONENT_NAMES = {
    'calibration_frame': ('Calibration Frame', 'Marco de calibración'),
    'energy_coupler': ('Energy Coupler', 'Acoplador de energía'),
    'living_matrix': ('Living Matrix', 'Matriz viva'),
    'ration_bundle': ('Travel Rations', 'Provisiones de viaje'),
    'routing_matrix': ('Routing Matrix', 'Matriz de distribución'),
    'propagation_core': ('Propagation Core', 'Núcleo de propagación'),
    'power_regulator': ('Power Regulator', 'Regulador de energía'),
    'inventory_sensor': ('Inventory Sensor', 'Sensor de inventario'),
    'handling_core': ('Handling Core', 'Núcleo de manipulación'),
    'spectral_lens': ('Spectral Lens', 'Lente espectral'),
    'horizon_chart': ('Horizon Chart', 'Carta de horizontes'),
    'ecosystem_capsule': ('Ecosystem Capsule', 'Cápsula de ecosistema'),
    'containment_seal': ('Containment Seal', 'Sello de contención'),
    'ark_bus': ('Ark Connection Bus', 'Bus de conexión del Arca'),
    'renewal_engine': ('Renewal Engine', 'Motor de renovación'),
    'habitation_contract': ('Habitation Charter', 'Carta de habitabilidad'),
}
CAPTURE_NAMES = {
    'active': ('A capture is already running.', 'Ya hay una captura en curso.'),
    'integrated_only': ('Enter a singleplayer or integrated LAN world first.', 'Primero entrá a un mundo individual o LAN integrado.'),
    'started': ('Capture started: %s', 'Captura iniciada: %s'),
    'error': ('Capture failed: %s', 'La captura falló: %s'),
    'stopped': ('Capture saved: %s', 'Captura guardada: %s'),
}
INK = '#141417'


def read_grid(path):
    palette, rows, body = {}, [], False
    for line in path.read_text(encoding='utf-8').splitlines():
        if not body:
            if not line.strip():
                body = True
                continue
            key, value = line.split()
            assert len(key) == 1 and key != '.' and len(value) == 7 and value.startswith('#'), f'{path}: bad palette line {line!r}'
            palette[key] = tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))
        elif line.strip():
            rows.append(line.rstrip())
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), f'{path}: grid must be 16x16'
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != '.':
                image.putpixel((x, y), palette[key] + (255,))
    return image


def png_bytes(image):
    import io
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=False)
    return buffer.getvalue()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


def expected():
    """Every generated file: relative destination -> bytes."""
    out = {}
    images = {}
    strips = {}
    for name in ITEMS:
        if name in ANIMATED:
            frames = [read_grid(GRIDS / 'item' / f'{name}__f{i}.txt') for i in range(ANIMATED[name])]
            images['item/' + name] = frames[0]
            strip = Image.new('RGBA', (16, 16 * len(frames)), (0, 0, 0, 0))
            for i, frame in enumerate(frames):
                strip.alpha_composite(frame, (0, 16 * i))
            strips['item/' + name] = strip
        else:
            images['item/' + name] = read_grid(GRIDS / 'item' / f'{name}.txt')
    for name in BLOCKS:
        images['block/' + name] = read_grid(GRIDS / 'block' / f'{name}.txt')
    def js(obj):
        return (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    for key, image in images.items():
        data = png_bytes(strips.get(key, image))
        out[('pack', f'textures/{key}.png')] = data
        out[('mod', f'textures/{key}.png')] = data
        if key in strips:
            anim = FIRE_ANIMATION if key.startswith('item/luminosity_') else ANIMATION
            out[('pack', f'textures/{key}.png.mcmeta')] = js(anim)
            out[('mod', f'textures/{key}.png.mcmeta')] = js(anim)
    # Heliodor compass: 32 traced vanilla frames per destination colour (art/authoring/trace_compass.py).
    # Overrides follow vanilla's angle thresholds; later entries win, so higher dimension indices and the
    # grey "no trace" states (entrelumen:state >= 3) come after.
    compass_models = []
    thresholds = [0.0] + [(i + 0.5) / 32 for i in range(32)]
    frame_for = [16] + [(17 + i) % 32 for i in range(32)]
    for dim in COMPASS_DIMENSIONS + ['none']:
        for frame in range(32):
            name = f'heliodor_compass/{dim}_{frame:02d}'
            tex = Image.open(ART / 'compass' / f'{dim}_{frame:02d}.png').convert('RGBA')
            images['item/' + name] = tex
            for dest in ('pack', 'mod'):
                out[(dest, f'textures/item/{name}.png')] = png_bytes(tex)
                out[(dest, f'models/item/{name}.json')] = js({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'entrelumen:item/' + name}})
    for index, dim in enumerate(COMPASS_DIMENSIONS):
        for th, frame in zip(thresholds, frame_for):
            compass_models.append({'predicate': {'entrelumen:dimension': index, 'entrelumen:angle': th},
                                   'model': f'entrelumen:item/heliodor_compass/{dim}_{frame:02d}'})
    for th, frame in zip(thresholds, frame_for):
        compass_models.append({'predicate': {'entrelumen:state': 3, 'entrelumen:angle': th},
                               'model': f'entrelumen:item/heliodor_compass/none_{frame:02d}'})
    for dest in ('pack', 'mod'):
        out[(dest, 'models/item/heliodor_compass.json')] = js({'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'entrelumen:item/heliodor_compass/overworld_16'}, 'overrides': compass_models})
    for name in ARMOR_LAYERS:
        layer = Image.open(ART / 'armor' / f'{name}.png').convert('RGBA')
        assert layer.size == (64, 32), f'{name} must be 64x32'
        images['models/armor/' + name] = layer
        out[('pack', f'textures/models/armor/{name}.png')] = png_bytes(layer)
        out[('mod', f'textures/models/armor/{name}.png')] = png_bytes(layer)
    for name in ITEMS:
        parent = 'minecraft:item/handheld' if name in HANDHELD else 'minecraft:item/generated'
        model = js({'parent': parent, 'textures': {'layer0': 'entrelumen:item/' + name}})
        out[('pack', f'models/item/{name}.json')] = model
        out[('mod', f'models/item/{name}.json')] = model
    for name in MODULES:
        block = js({'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'side': 'entrelumen:block/' + name, 'top': 'entrelumen:block/module_top', 'bottom': 'entrelumen:block/module_bottom'}})
        item = js({'parent': 'entrelumen:block/' + name})
        for dest in ('pack', 'mod'):
            out[(dest, f'models/block/{name}.json')] = block
            out[(dest, f'models/item/{name}.json')] = item
    new_blocks = {name: {'parent': 'minecraft:block/cube_column', 'textures': {
                      'side': 'entrelumen:block/' + name, 'end': 'entrelumen:block/' + end}} for name, end in SHELVES.items()}
    new_blocks.update({name: {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
                      'side': 'entrelumen:block/' + side, 'top': 'entrelumen:block/' + top, 'bottom': 'entrelumen:block/' + bottom}}
                       for name, (side, top, bottom) in LIBRARY.items()})
    for name in SCULPTED:
        new_blocks[name] = json.loads((ART / 'models/block' / f'{name}.json').read_text(encoding='utf-8'))
    for name in BLOCK_MODELS_ONLY:
        model = json.loads((ART / 'models/block' / f'{name}.json').read_text(encoding='utf-8'))
        for dest in ('pack', 'mod'):
            out[(dest, f'models/block/{name}.json')] = js(model)
    for name, model in new_blocks.items():
        for dest in ('pack', 'mod'):
            out[(dest, f'models/block/{name}.json')] = js(model)
            out[(dest, f'models/item/{name}.json')] = js({'parent': 'entrelumen:block/' + name})
        out[('mod', f'blockstates/{name}.json')] = js({'variants': {'': {'model': 'entrelumen:block/' + name}}})
    return images, out, strips


def target(dest, rel):
    return (ASSETS if dest == 'pack' else COMPANION) / rel


def previews(images):
    icon = Image.new('RGBA', (128, 128), (20, 20, 23, 255))
    icon.alpha_composite(images['item/atlas'].resize((128, 128), Image.Resampling.NEAREST))
    images = {k: v for k, v in images.items() if k.startswith(('item/', 'block/')) and not k.startswith('item/heliodor_compass/')}
    sheet_items = [k for k in images if k.startswith('item/')]
    sheet_blocks = [k for k in images if k.startswith('block/')]
    scale, pad = 6, 34
    cols = 7
    rows = (len(sheet_items) + cols - 1) // cols + (len(sheet_blocks) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * (16 * scale + pad) + pad, 70 + rows * (16 * scale + 34)), (48, 48, 52, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=14)
    draw.text((pad, 16), 'ENTRELUMEN native 16x16 textures (grid sources, nearest preview; not a game capture)', font=font, fill=(230, 226, 214, 255))
    y0 = 50
    for group in (sheet_items, sheet_blocks):
        for i, key in enumerate(group):
            x = pad + (i % cols) * (16 * scale + pad)
            y = y0 + (i // cols) * (16 * scale + 34)
            draw.rectangle((x - 2, y - 2, x + 16 * scale + 1, y + 16 * scale + 1), fill=(139, 139, 139, 255))
            sheet.alpha_composite(images[key].resize((16 * scale, 16 * scale), Image.Resampling.NEAREST), (x, y))
            draw.text((x, y + 16 * scale + 6), key.split('/')[1].replace('_', ' '), font=font, fill=(230, 226, 214, 255))
        y0 += ((len(group) + cols - 1) // cols) * (16 * scale + 34)
    return icon, sheet


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    images, out, strips = expected()
    provenance = json.loads((GRIDS / 'provenance.json').read_text(encoding='utf-8'))
    assert set(provenance['item']) == set(ITEMS) and set(provenance['block']) == set(BLOCKS), 'provenance must cover every grid'
    for key, image in images.items():
        if key.startswith(('models/armor/', 'item/heliodor_compass/')):
            continue
        pixels = [image.getpixel((x, y)) for y in range(16) for x in range(16)]
        alphas = {p[3] for p in pixels}
        assert alphas <= {0, 255}, f'{key}: alpha must be binary'
        colours = {p[:3] for p in pixels if p[3]}
        assert key in PALETTE_TEXTURES or len(colours) <= 24, f'{key}: {len(colours)} colours exceeds the native budget'
    if args.check:
        for (dest, rel), data in out.items():
            path = target(dest, rel)
            assert path.exists(), f'missing generated file: {dest}:{rel}'
            if rel.endswith('.png'):
                # Compare decoded texels: PNG encoder bytes may differ between Pillow/zlib builds.
                key = rel[len('textures/'):-len('.png')]
                wanted = strips.get(key, images[key])
                with Image.open(path) as actual:
                    assert actual.mode == 'RGBA' and actual.size == wanted.size, f'{dest}:{rel} must be {wanted.size} RGBA'
                    assert actual.tobytes() == wanted.tobytes(), f'stale texture: {dest}:{rel}'
                if dest == 'mod':
                    assert path.read_bytes() == target('pack', rel).read_bytes(), f'mod/resource-pack texture drift: {rel}'
            else:
                assert path.read_bytes() == data, f'stale generated file: {dest}:{rel}'
        assert json.loads((PACK / 'pack.mcmeta').read_text(encoding='utf-8'))['pack']['pack_format'] == 34
        assert set(COMPONENT_NAMES) <= set(ITEMS)
        for index, locale in enumerate(('en_us', 'es_es')):
            native_lang = json.loads((COMPANION / 'lang' / f'{locale}.json').read_text(encoding='utf-8'))
            for name, names in COMPONENT_NAMES.items():
                assert native_lang['item.entrelumen.' + name] == names[index]
            for name, names in CAPTURE_NAMES.items():
                assert native_lang['entrelumen.capture.' + name] == names[index]
        manifest = json.loads((ART / 'resourcepack-sha256.json').read_text(encoding='utf-8'))
        actual = {p.relative_to(PACK).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in sorted(PACK.rglob('*'), key=lambda p: p.relative_to(PACK).as_posix()) if p.is_file()}
        assert manifest == actual, 'resource-pack hash inventory is stale'
        for path in PACK.rglob('*'):
            if path.is_file() and path.suffix in ('.json', '.mcmeta'):
                assert b'\r' not in path.read_bytes(), f'non-LF generated text: {path}'
        print(f'PASS: {len(ITEMS)} item and {len(BLOCKS)} block grids render byte-identically into the resource pack and companion; '
              'models, EN/ES names and hash inventory current. In-game visual review is separate.')
        return
    for (dest, rel), data in out.items():
        path = target(dest, rel)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    write_json(PACK / 'pack.mcmeta', {'pack': {'pack_format': 34, 'description': {'translate': 'resourcePack.entrelumen.description'}}})
    for index, (locale, description) in enumerate([('en_us', 'ENTRELUMEN · Copper, maps and living light'), ('es_es', 'ENTRELUMEN · Cobre, mapas y luz viva')]):
        names = {'item.entrelumen.' + key: value[index] for key, value in COMPONENT_NAMES.items()}
        write_json(ASSETS / 'lang' / f'{locale}.json', {'resourcePack.entrelumen.description': description, **names})
        native_path = COMPANION / 'lang' / f'{locale}.json'
        native = json.loads(native_path.read_text(encoding='utf-8'))
        before = dict(native)
        native.update(names)
        native.update({'entrelumen.capture.' + key: value[index] for key, value in CAPTURE_NAMES.items()})
        if native != before:
            write_json(native_path, native)
    icon, sheet = previews(images)
    icon.save(PACK / 'pack.png')
    sheet.save(ART / 'contact-sheet.png')
    hashes = {p.relative_to(PACK).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in sorted(PACK.rglob('*'), key=lambda p: p.relative_to(PACK).as_posix()) if p.is_file()}
    write_json(ART / 'resourcepack-sha256.json', hashes)
    print(f'Generated {len(ITEMS)} item and {len(BLOCKS)} block textures, models, pack icon and contact sheet.')


if __name__ == '__main__':
    main()
