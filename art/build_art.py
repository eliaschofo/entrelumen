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
         'habitation_contract']
MODULES = ['engineering_module', 'arcane_module', 'nature_module', 'exploration_module', 'logistics_module',
           'habitation_module', 'ark_controller']
BLOCKS = MODULES + ['module_top', 'module_bottom']

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
    for name in ITEMS:
        images['item/' + name] = read_grid(GRIDS / 'item' / f'{name}.txt')
    for name in BLOCKS:
        images['block/' + name] = read_grid(GRIDS / 'block' / f'{name}.txt')
    for key, image in images.items():
        data = png_bytes(image)
        out[('pack', f'textures/{key}.png')] = data
        out[('mod', f'textures/{key}.png')] = data
    def js(obj):
        return (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    for name in ITEMS:
        model = js({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'entrelumen:item/' + name}})
        out[('pack', f'models/item/{name}.json')] = model
        out[('mod', f'models/item/{name}.json')] = model
    for name in MODULES:
        block = js({'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'side': 'entrelumen:block/' + name, 'top': 'entrelumen:block/module_top', 'bottom': 'entrelumen:block/module_bottom'}})
        item = js({'parent': 'entrelumen:block/' + name})
        for dest in ('pack', 'mod'):
            out[(dest, f'models/block/{name}.json')] = block
            out[(dest, f'models/item/{name}.json')] = item
    return images, out


def target(dest, rel):
    return (ASSETS if dest == 'pack' else COMPANION) / rel


def previews(images):
    icon = Image.new('RGBA', (128, 128), (20, 20, 23, 255))
    icon.alpha_composite(images['item/atlas'].resize((128, 128), Image.Resampling.NEAREST))
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
    images, out = expected()
    provenance = json.loads((GRIDS / 'provenance.json').read_text(encoding='utf-8'))
    assert set(provenance['item']) == set(ITEMS) and set(provenance['block']) == set(BLOCKS), 'provenance must cover every grid'
    for key, image in images.items():
        pixels = [image.getpixel((x, y)) for y in range(16) for x in range(16)]
        alphas = {p[3] for p in pixels}
        assert alphas <= {0, 255}, f'{key}: alpha must be binary'
        colours = {p[:3] for p in pixels if p[3]}
        assert len(colours) <= 24, f'{key}: {len(colours)} colours exceeds the native budget'
    if args.check:
        for (dest, rel), data in out.items():
            path = target(dest, rel)
            assert path.exists(), f'missing generated file: {dest}:{rel}'
            if rel.endswith('.png'):
                # Compare decoded texels: PNG encoder bytes may differ between Pillow/zlib builds.
                key = rel[len('textures/'):-len('.png')]
                with Image.open(path) as actual:
                    assert actual.mode == 'RGBA' and actual.size == (16, 16), f'{dest}:{rel} must be 16x16 RGBA'
                    assert actual.tobytes() == images[key].tobytes(), f'stale texture: {dest}:{rel}'
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
