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
    plain = ck.parse_plain_options(ck.PRESET_OPTIONS.read_text(encoding='utf-8'))
    out, errors = ck.effective(data, ck.parse_options(ck.PRESET.read_text(encoding='utf-8')), True, plain)
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

    def shadows(self, held=None, **overrides):
        return [c for c in ck.check(self.data, binds(self.data, **overrides), held)[0] if 'hides' in c['why']]

    def test_ctrl_and_shift_on_a_bare_key_clash_while_running_or_sneaking(self):
        # The cases found in review: Ctrl is held to sprint and Shift to sneak, and NeoForge then fires the
        # combination instead of the bare key.
        cases = [('key__apotheosis__open_world_tier_select', 'key.keyboard.t:CONTROL', 'key.chat'),
                 ('justdirethings__key__toolUI', 'key.keyboard.t:SHIFT', 'key.chat'),
                 ('key__ad_astra__toggle_suit_flight', 'key.keyboard.f4:CONTROL', 'keybind.ironjetpacks.engine'),
                 ('key__modern_industrialization__toggle_flight', 'key.keyboard.f4:SHIFT',
                  'keybind.ironjetpacks.engine'),
                 ('key__little__undo', 'key.keyboard.z:CONTROL', 'justzoom.keybinds.keybind.zoom'),
                 ('key__apotheosis__toggle_radial_mining', 'key.keyboard.o:CONTROL', 'key.occultism.ender_bag'),
                 ('key__enderio__toggle_magnet', 'key.keyboard.m:CONTROL', 'key.journeymap.create_waypoint'),
                 ('options__narrator', 'key.keyboard.b:CONTROL', 'key.sophisticatedbackpacks.open_backpack')]
        for name, value, bare in cases:
            with self.subTest(binding=name, value=value):
                found = self.shadows(**{name: value})
                self.assertTrue(any(bare in c['keys'] and c['bindings'][0] == value for c in found), found)

    def test_alt_and_keys_without_a_bare_world_binding_are_safe(self):
        self.assertEqual(self.shadows(key__apotheosis__open_world_tier_select='key.keyboard.t:ALT'), [])
        # Delete has no bare world binding (only quest-editor and trash-slot screens use it).
        self.assertEqual(self.shadows(key__apotheosis__open_world_tier_select='key.keyboard.delete:CONTROL',
                                      key__ad_astra__toggle_suit_flight='key.keyboard.delete:SHIFT'), [])

    def test_toggled_sprint_or_sneak_frees_the_modifier(self):
        both = {'toggleSprint': 'false', 'toggleCrouch': 'false'}
        binds_ = binds(self.data)
        self.assertEqual(ck.held_in_play(binds_, both), {'SHIFT', 'CONTROL'})
        self.assertEqual(ck.held_in_play(binds_, {'toggleSprint': 'true'}), {'SHIFT'})
        self.assertEqual(ck.held_in_play(binds_, {'toggleCrouch': 'true'}), {'CONTROL'})
        self.assertEqual(ck.held_in_play(binds_ | {'key.sprint': 'key.keyboard.caps.lock'}, both), {'SHIFT'})
        override = dict(key__apotheosis__open_world_tier_select='key.keyboard.t:CONTROL')
        self.assertTrue(self.shadows(**override))
        self.assertEqual(self.shadows(held={'SHIFT'}, **override), [])

    def test_narrator_hotkey_option_turns_the_binding_off(self):
        data = {'keys': {'options.narrator': self.data['keys']['options.narrator']}}
        on, _ = ck.effective(data, {}, True, {})
        off, _ = ck.effective(data, {}, True, {'narratorHotkey': 'false'})
        self.assertEqual((on['options.narrator'], off['options.narrator']),
                         ('key.keyboard.b:CONTROL', ck.UNBOUND))
        preset = ck.parse_options(ck.PRESET.read_text(encoding='utf-8'))
        plain = ck.parse_plain_options(ck.PRESET_OPTIONS.read_text(encoding='utf-8'))
        self.assertEqual(plain.get('narratorHotkey'), 'false')
        self.assertEqual(preset.get('options.narrator'), ck.UNBOUND)

    def test_a_mods_own_default_combination_on_its_own_key_passes(self):
        # JourneyMap ships Ctrl+J beside J; that is its design, not a pack choice.
        self.assertEqual(binds(self.data)['key.journeymap.minimap_toggle_alt'], 'key.keyboard.j:CONTROL')
        self.assertEqual(self.shadows(), [])
        self.assertTrue(self.shadows(key__journeymap__minimap_toggle_alt='key.keyboard.j:SHIFT'))

    def test_every_shadow_exception_is_still_a_real_shadow(self):
        allowed = ck.check(self.data, binds(self.data))[1]
        live = {frozenset(a['keys']) for a in allowed if a['why'] == 'shadow'}
        entries = self.data['shadowAllowed']
        for entry in entries:
            with self.subTest(keys=entry['keys']):
                self.assertTrue(entry['reason'].strip())
                self.assertIn(frozenset(entry['keys']), live)
        self.assertEqual(len(live), len(entries))

    def test_unknown_bound_key_is_an_error(self):
        _, errors = ck.effective(self.data, {'key.mystery.mod': 'key.keyboard.g'}, True)
        self.assertTrue(any('key.mystery.mod' in e for e in errors))


if __name__ == '__main__':
    unittest.main()
