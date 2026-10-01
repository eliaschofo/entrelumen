"""Validate the limited Default Options fragments without a Minecraft profile."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import generate_client_defaults as defaults


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
        self.assertEqual(set(fragments), {'options.txt', 'keybindings.txt', 'extra/config/iris.properties'})

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
