"""Unit checks for tools/check_keybinds.py: shipped preset passes, known clash shapes are caught."""
from __future__ import annotations

import copy
import json
import subprocess
import sys
import unittest

import check_keybinds as ck


def binds(data: dict, **overrides: str) -> dict[str, str]:
    """Shipped preset plus recorded defaults, with key names written as key__name (dots as __)."""
    out, errors = ck.effective(data, ck.parse_options(ck.PRESET.read_text(encoding='utf-8')), True)
    assert not errors, errors
    out.update({k.replace('__', '.'): v for k, v in overrides.items()})
    return out


class CheckKeybindsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(ck.CONTEXTS.read_text(encoding='utf-8'))

    def clashes(self, **overrides):
        return ck.check(self.data, binds(self.data, **overrides))[0]

    def test_shipped_preset_has_no_clash(self):
        result = subprocess.run([sys.executable, str(ck.ROOT / 'tools/check_keybinds.py')],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.clashes(), [])

    def test_every_preset_line_has_a_context(self):
        preset = ck.parse_options(ck.PRESET.read_text(encoding='utf-8'))
        self.assertEqual(sorted(k for k in preset if k not in self.data['keys']), [])

    def test_same_world_key_clashes(self):
        found = self.clashes(key__enderio__travel_staff='key.keyboard.g')
        self.assertTrue(any(set(c['keys']) == {'key.curios.open.desc', 'key.enderio.travel_staff'} for c in found))

    def test_raw_key_code_ignores_modifier(self):
        found = self.clashes(key__ars_nouveau__head_curio_hotkey='key.keyboard.h')
        self.assertTrue(any(c['why'].startswith('raw') for c in found), found)

    def test_fixed_owner_cannot_move_or_be_hidden(self):
        moved = self.clashes(key__ftbultimine='key.keyboard.v:ALT')
        self.assertTrue(any('fixed owner' in c['why'] for c in moved))
        hidden = self.clashes(key__easy_villagers__pick_up='key.keyboard.v:SHIFT')
        self.assertTrue(any('hides the fixed owner' in c['why'] for c in hidden), hidden)

    def test_bare_modifier_press_clashes_with_combinations(self):
        found = self.clashes(create__keyinfo__toolbelt='key.keyboard.left.alt')
        self.assertTrue(any('combinations' in c['why'] for c in found))

    def test_screen_and_world_keys_do_not_meet(self):
        # JEI R (recipe screens) and Tool Belt R (world, no screen) are the documented example.
        self.assertEqual(binds(self.data)['key.jei.showRecipe'], 'key.keyboard.r')
        self.assertEqual(binds(self.data)['key.toolbelt.open'], 'key.keyboard.r')
        self.assertEqual(self.clashes(), [])

    def test_hands_and_vehicles_keep_gates_apart(self):
        entry = copy.deepcopy(self.data)
        a, b = entry['keys']['key.little.mirror'], entry['keys']['key.mekanism.mode']
        self.assertIsNone(ck.meet(a, b, {}))
        b = dict(b, gate=None)
        self.assertEqual(ck.meet(a, b, {}), 'world')

    def test_unknown_bound_key_is_an_error(self):
        _, errors = ck.effective(self.data, {'key.mystery.mod': 'key.keyboard.g'}, True)
        self.assertTrue(any('key.mystery.mod' in e for e in errors))


if __name__ == '__main__':
    unittest.main()
