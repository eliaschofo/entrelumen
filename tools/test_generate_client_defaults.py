"""Validate the limited Default Options fragments without a Minecraft profile."""
from __future__ import annotations

import copy
import functools
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import tomllib
import unittest
import zipfile

import generate_client_defaults as defaults

DH_SEED = 'extra/config/DistantHorizons.toml'
DH_JAR = 'DistantHorizons-3.3.2-1.21.1-fabric-neoforge.jar'
# Dimensions registered in code, without a data/<ns>/dimension file: vanilla's two and AE2's spatial storage
# (appeng.spatial.SpatialStorageDimensionIds). Ars Nouveau's planarium dimensions get random UUID names.
CODE_DIMENSIONS = {'minecraft:the_nether', 'minecraft:the_end', 'ae2:spatial_storage'}


@functools.cache
def local_paths() -> dict:
    path = defaults.ROOT / 'catalog/local-paths.json'
    return json.loads(path.read_text(encoding='utf-8')) if path.is_file() else {}


def pinned_jar(name: str) -> Path | None:
    path = Path(local_paths().get(name, ''))
    return path if name in local_paths() and path.is_file() else None


@functools.cache
def pinned_jars() -> tuple[Path, ...] | None:
    """Every JAR of the lock, or None when any is missing on this machine."""
    lock = json.loads((defaults.ROOT / 'catalog/curated.json').read_text(encoding='utf-8'))
    paths = [pinned_jar(entry['filename']) for entry in lock['mods']]
    return None if any(path is None for path in paths) else tuple(paths)


class ClientDefaultsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.preset = json.loads(defaults.SOURCE.read_text(encoding='utf-8'))

    def test_fresh_fragments_cover_only_authored_keys(self):
        fragments = defaults.render(self.preset)
        general = dict(line.split(':', 1) for line in fragments['options.txt'].splitlines())
        bindings = dict(line.split(':', 1) for line in fragments['keybindings.txt'].splitlines())
        self.assertEqual(general | bindings, self.preset['options'])
        self.assertTrue(all(not key.startswith('key_') for key in general))
        self.assertTrue(all(key.startswith('key_') for key in bindings))
        self.assertNotIn('lang', general)
        self.assertEqual(general['narratorHotkey'], 'false')  # Ctrl+B would hide the backpack key while sprinting
        self.assertNotIn('servers.dat', fragments)
        self.assertEqual(json.loads(general['resourcePacks']),
                         ['vanilla', 'mod_resources', 'file/entrelumen'])
        self.assertEqual(set(fragments), {'options.txt', 'keybindings.txt', 'extra/config/iris.properties',
                                          'extra/config/DistantHorizons.toml'})

    def test_native_key_names_and_modifiers_survive(self):
        bindings = defaults.render(self.preset)['keybindings.txt'].splitlines()
        self.assertTrue(all(defaults.KEY_LINE.fullmatch(line) for line in bindings))
        self.assertIn('key_key.mekanism.head_mode:key.keyboard.up:ALT', bindings)
        self.assertIn('key_key.toolbelt.slot:key.keyboard.right.bracket:SHIFT', bindings)
        self.assertIn('key_supplementaries.keybind.quiver:key.keyboard.apostrophe', bindings)
        self.assertIn('key_key.apotheosis.open_world_tier_select:key.keyboard.f5:ALT', bindings)
        self.assertEqual({line.rsplit(':', 1)[1] for line in bindings if line.count(':') == 2},
                         {'ALT', 'SHIFT', 'CONTROL'})

    def test_unrelated_options_and_private_pack_ids_are_not_exported(self):
        preset = copy.deepcopy(self.preset)
        preset['options']['lang'] = 'es_es'
        with self.assertRaisesRegex(ValueError, 'Language'):
            defaults.render(preset)
        preset = copy.deepcopy(self.preset)
        preset['options']['resourcePacks'] = '["vanilla","file/personal"]'
        with self.assertRaisesRegex(ValueError, 'shipped resource pack'):
            defaults.render(preset)
        preset = copy.deepcopy(self.preset)
        preset['options']['key_key.mekanism.head_mode'] = 'key.keyboard.up:SUPER'
        with self.assertRaisesRegex(ValueError, 'Invalid Default Options binding'):
            defaults.render(preset)

    def test_iris_starts_with_shaders_off_through_a_seed_once_file(self):
        fragments = defaults.render(self.preset)
        seeded = dict(line.split('=', 1) for line in fragments['extra/config/iris.properties'].splitlines())
        self.assertEqual(seeded, {'disableUpdateMessage': 'true', 'enableShaders': 'false'})
        preset = copy.deepcopy(self.preset)
        preset['extra']['config/other.toml'] = {'a': 'b'}
        with self.assertRaisesRegex(ValueError, 'Not a seeded extra file'):
            defaults.render(preset)
        preset = copy.deepcopy(self.preset)
        preset['extra']['config/iris.properties']['shaderPack'] = 'Complementary Reimagined.zip'
        with self.assertRaisesRegex(ValueError, 'Invalid property'):
            defaults.render(preset)

    def test_distant_horizons_seed_holds_the_decided_overworld_defaults(self):
        seeded = tomllib.loads(defaults.render(self.preset)[DH_SEED])
        self.assertEqual(seeded, self.preset['extra']['config/DistantHorizons.toml'])
        self.assertEqual(seeded['_version'], 4)  # DH 3.3.2 deletes a file with an older version
        graphics = seeded['client']['advanced']['graphics']
        quality = graphics['quality']
        self.assertEqual(quality['lodChunkRenderDistanceRadius'], 128)
        self.assertEqual(quality['vanillaFadeMode'], 'DOUBLE_PASS')
        self.assertIs(graphics['enableSsao'], False)
        self.assertIs(graphics['genericRendering']['enableCloudRendering'], True)
        self.assertEqual(graphics['genericRendering']['dimensionEnabledCloudRenderingCsv'], 'minecraft:overworld')
        self.assertEqual(seeded['common']['multiThreading'], {'numberOfThreads': 3, 'threadRunTimeRatio': 1.0})
        generator = seeded['common']['worldGenerator']
        self.assertEqual((generator['generatorPlan'], generator['chunkGeneratorMode']), ('SURFACE_THEN_CHUNKS', 'FEATURES'))
        self.assertIs(seeded['client']['advanced']['autoUpdater']['enableAutoUpdater'], False)
        self.assertIs(seeded['client']['advanced']['autoUpdater']['enableSilentUpdates'], False)
        self.assertIs(seeded['client']['advanced']['debugging']['enableDebugKeybindings'], False)
        self.assertFalse(any(seeded['common']['logging']['warning'].values()))
        ignored = graphics['experimental']['ignoredDimensionCsv'].split(',')
        self.assertIn('entrelumen:enves', ignored)  # the Envés never renders LODs
        self.assertNotIn('minecraft:overworld', ignored)
        self.assertTrue(all(re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', name) for name in ignored), ignored)

    def test_distant_horizons_seed_rejects_a_reset_version_and_unsafe_values(self):
        for version in (3, True, '4'):
            preset = copy.deepcopy(self.preset)
            preset['extra']['config/DistantHorizons.toml']['_version'] = version
            with self.assertRaisesRegex(ValueError, '_version 4'):
                defaults.render(preset)
        preset = copy.deepcopy(self.preset)
        preset['extra']['config/DistantHorizons.toml']['client']['advanced']['graphics']['experimental'][
            'ignoredDimensionCsv'] = 'minecraft:the_end, "x"'
        with self.assertRaisesRegex(ValueError, 'Invalid setting'):
            defaults.render(preset)
        preset = copy.deepcopy(self.preset)
        preset['extra']['config/DistantHorizons.toml']['client']['bad key'] = True
        with self.assertRaisesRegex(ValueError, 'Invalid setting'):
            defaults.render(preset)
        preset = copy.deepcopy(self.preset)
        preset['extra']['config/DistantHorizons.toml']['common']['empty'] = {}
        with self.assertRaisesRegex(ValueError, 'Empty table'):
            defaults.render(preset)

    @unittest.skipUnless(pinned_jar(DH_JAR), f'Pinned Distant Horizons JAR unavailable: {DH_JAR}')
    def test_distant_horizons_settings_exist_in_the_pinned_jar(self):
        """Every seeded setting is a field of the matching Config class (Config$Client$Advanced$Graphics...)."""
        seeded = self.preset['extra']['config/DistantHorizons.toml']
        with zipfile.ZipFile(pinned_jar(DH_JAR)) as jar:
            def walk(path, node):
                for key, value in node.items():
                    if key == '_version':
                        continue
                    if isinstance(value, dict):
                        walk(path + [key], value)
                        continue
                    name = 'com/seibel/distanthorizons/core/config/Config$' + '$'.join(
                        part[0].upper() + part[1:] for part in path) + '.class'
                    self.assertIn(name, jar.namelist(), key)
                    self.assertIn(b'\x01' + len(key).to_bytes(2, 'big') + key.encode(), jar.read(name), (name, key))
            walk([], seeded)

    @unittest.skipUnless(pinned_jars(), 'Pinned dependency JARs are not available on this machine')
    def test_every_other_dimension_is_ignored_by_distant_horizons(self):
        """Round checklist (docs/design/mod-pingpong.md): a mod that adds a dimension adds it to ignoredDimensionCsv."""
        registered = set()
        pattern = re.compile(r'data/([^/]+)/dimension/(.+)\.json')

        def scan(jar):
            for name in jar.namelist():
                match = '/dimension/' in name and pattern.fullmatch(name)
                if match:
                    registered.add(f'{match.group(1)}:{match.group(2)}')
                elif name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    with zipfile.ZipFile(io.BytesIO(jar.read(name))) as nested:
                        scan(nested)
        for path in pinned_jars():
            with zipfile.ZipFile(path) as jar:
                scan(jar)
        for root in (defaults.ROOT / 'companion/src/main/resources', defaults.ROOT / 'pack'):
            for path in root.rglob('dimension/*.json'):
                match = re.search(r'(?:^|/)data/([^/]+)/dimension/(.+)\.json$', path.relative_to(root).as_posix())
                if match:
                    registered.add(f'{match.group(1)}:{match.group(2)}')
        csv = self.preset['extra']['config/DistantHorizons.toml']['client']['advanced']['graphics'][
            'experimental']['ignoredDimensionCsv']
        self.assertEqual(set(csv.split(',')), (registered | CODE_DIMENSIONS) - {'minecraft:overworld'})

    def test_check_is_deterministic_and_rejects_unexpected_files(self):
        fragments = defaults.render(self.preset)
        self.assertEqual(fragments, defaults.render(copy.deepcopy(self.preset)))
        result = subprocess.run([sys.executable, str(defaults.ROOT / 'tools/generate_client_defaults.py'),
                                 '--check'], cwd=defaults.ROOT, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / 'servers.dat').write_bytes(b'private')
            with self.assertRaisesRegex(ValueError, 'Unexpected Default Options file'):
                defaults.verify_folder(folder, fragments)


if __name__ == '__main__':
    unittest.main()
