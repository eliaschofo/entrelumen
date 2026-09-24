"""Static checks for generated family staging scripts; JAR-backed checks when the lock is local."""
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import re
import unittest
import unittest.mock


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('family_balance_under_test', Path(__file__).with_name('generate_family_balance.py'))
balance = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(balance)
ROMAN = {'I': 1, 'II': 2, 'III': 3, 'IV': 4, 'V': 5, 'VI': 6}


def parse(script: Path):
    text = script.read_text(encoding='utf-8')
    values = {}
    for name in ('Signature', 'Sources', 'Rows', 'Removals'):
        match = re.search(r'^const entrelumen\w+' + name + r' = (.*);$', text, re.M)
        assert match, f'{script.name}: missing {name}'
        values[name] = json.loads(match.group(1))
    return text, values


def lock_available():
    try:
        paths = json.loads((ROOT / 'catalog/local-paths.json').read_text(encoding='utf-8'))
    except FileNotFoundError:
        return False
    return all(Path(p).is_file() for p in paths.values())


class FamilyBalanceTest(unittest.TestCase):
    def test_generated_scripts_match_specification(self):
        design = json.loads((ROOT / 'content/integration-design.json').read_text(encoding='utf-8'))
        acts = {p['output']['id']: p['act'] for p in design['projects']}
        for name, family in balance.FAMILIES.items():
            with self.subTest(family=name):
                text, values = parse(ROOT / 'pack/kubejs/server_scripts' / family['script'])
                payload = json.dumps({'rows': values['Rows'], 'removals': values['Removals']},
                                     ensure_ascii=False, separators=(',', ':'))
                self.assertEqual(values['Signature'], hashlib.sha256(payload.encode('utf-8')).hexdigest())
                self.assertEqual([r['id'] for r in values['Rows']], [c['id'] for c in family['changes']])
                self.assertEqual(values['Removals'], family['removals'])
                self.assertIn(f"[{family['tag']}]", text)
                for row, change in zip(values['Rows'], family['changes']):
                    self.assertEqual(row['component'], change['add'])
                    self.assertEqual(row['act'], change['act'])
                    component = row['component']
                    if component.startswith('entrelumen:'):
                        self.assertEqual(ROMAN[row['act']], acts[component])
                    else:
                        self.assertEqual(balance.STAGE_MATERIALS[component], row['act'])
                    self.assertIn(component, json.dumps(row['json']))
                    self.assertNotIn('entrelumen:', row['output'])
                self.assertTrue(set(values['Sources']) <= {e['filename'] for e in json.loads(
                    (ROOT / 'catalog/curated.json').read_text(encoding='utf-8'))['mods']})

    def test_stage_never_uses_ark_modules(self):
        for family in balance.FAMILIES.values():
            for change in family['changes']:
                self.assertFalse(change['add'].endswith('_module'), change['id'])

    def test_transform_rejects_unique_or_changed_ingredients(self):
        recipe = {'type': 'minecraft:crafting_shaped', 'pattern': ['AB ', 'AA '], 'key': {'A': {'item': 'x:a'}, 'B': {'item': 'x:b'}},
                  'result': {'id': 'x:out', 'count': 2}}
        ok = balance.transform(balance.shaped('x:out', 1, 0, {'item': 'x:a'}, 'entrelumen:power_regulator', 'III', ''), recipe)
        self.assertEqual(ok['pattern'], ['AB ', 'ZA '])
        self.assertEqual(ok['result'], recipe['result'])
        with self.assertRaises(AssertionError):
            balance.transform(balance.shaped('x:out', 0, 1, {'item': 'x:b'}, 'entrelumen:power_regulator', 'III', ''), recipe)
        with self.assertRaises(AssertionError):
            balance.transform(balance.shaped('x:out', 0, 0, {'item': 'x:b'}, 'entrelumen:power_regulator', 'III', ''), recipe)
        filled = balance.transform(balance.shaped('x:out', 0, 2, None, 'entrelumen:power_regulator', 'III', ''), recipe)
        self.assertEqual(filled['pattern'][0], 'ABZ')
        listed = {'type': 'm:fusion', 'ingredients': [{'consume': True, 'ingredient': {'item': 'x:a'}}] * 2, 'result': {'id': 'x:o'}}
        changed = balance.transform(balance.listed('x:o', 'ingredients', 1, listed['ingredients'][0], 'entrelumen:ark_bus', 'V', '', wrap='fusion'), listed)
        self.assertEqual(changed['ingredients'][1], {'consume': True, 'ingredient': {'item': 'entrelumen:ark_bus'}})

    def test_campaign_tier_and_augment_overrides_are_reversible(self):
        tier = {'parent': 'apotheosis:progression/haven', 'criteria': {'x': {'trigger': 'apotheosis:equipped_item'}},
                'display': {'title': {'translate': 't'}, 'description': {'translate': 'native'}},
                'requirements': [['x']], 'sends_telemetry_event': True}
        modifier = {'type': 'apothic_spawners:spawner_modifier', 'mainhand': {'item': 'minecraft:clock'},
                    'offhand': {'item': 'minecraft:quartz'}, 'consumes_offhand': False,
                    'stat_changes': [{'type': 'apothic_spawners:max_delay', 'value': 20, 'max': 1600}]}
        placeholder = {'neoforge:conditions': [{'type': 'neoforge:false'}], 'type': 'apotheosis:malice'}
        tier_spec = balance.campaign_tier('frontier', 'minecraft:impossible', '')
        mod_spec = balance.augmented('max_delay', inverse=True)
        found = {tier_spec['path']: [('Apotheosis-x.jar', '', json.dumps(tier).encode())],
                 mod_spec['path']: [('ApothicSpawners-x.jar', '', json.dumps(modifier).encode()),
                                    ('Apotheosis-x.jar', '', json.dumps(placeholder).encode())]}
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [tier_spec, mod_spec]}}):
            out = balance.build_data('probe', found)
            overridden = json.loads(out[tier_spec['path'][len('data/'):]])
            self.assertEqual(overridden['criteria'], {'campaign': {'trigger': 'minecraft:impossible'}})
            self.assertEqual(overridden['requirements'], [['campaign']])
            self.assertEqual(overridden['display']['title'], tier['display']['title'])
            self.assertEqual(overridden['display']['description'],
                             {'translate': 'entrelumen.apotheosis.tier.frontier.desc'})
            self.assertEqual(overridden['parent'], tier['parent'])
            augmented = json.loads(out[mod_spec['path'][len('data/'):]])
            self.assertEqual(augmented['mainhand'], {'item': 'entrelumen:augment_max_delay'})
            self.assertEqual({k: v for k, v in augmented.items() if k != 'mainhand'},
                             {k: v for k, v in modifier.items() if k != 'mainhand'})
            # An active second provider of the same path must stop generation.
            found[mod_spec['path']][1] = ('Apotheosis-x.jar', '', json.dumps(modifier).encode())
            with self.assertRaises(AssertionError):
                balance.build_data('probe', found)

    def test_augments_cover_every_companion_modifier_with_staged_recipes(self):
        java = (ROOT / 'companion/src/main/java/dev/entrelumen/SpawnerAugmentItem.java').read_text(encoding='utf-8')
        declared = re.search(r'MODIFIERS = List\.of\((.*?)\);', java, re.S).group(1)
        modifiers = re.findall(r'"([a-z_]+)"', declared)
        self.assertEqual(len(modifiers), 16)
        self.assertEqual(set(modifiers), set(balance.AUGMENTS))
        family = balance.FAMILIES['apotheosis']
        data_paths = {spec['path'] for spec in family['data'] if spec['op'] == 'mainhand'}
        for modifier in modifiers:
            self.assertIn(f'data/apothic_spawners/recipe/spawner_modifiers/{modifier}.json', data_paths)
            self.assertIn(f'data/apothic_spawners/recipe/spawner_modifiers/_inverse/{modifier}.json', data_paths)
        recipes = {a['id']: a for a in family['additions']}
        for modifier, (act, core, material, component) in balance.AUGMENTS.items():
            addition = recipes[f'entrelumen:augment_{modifier}']
            self.assertEqual(addition['act'], act)
            self.assertNotIn('rune', json.dumps(addition['key']))
            self.assertNotIn('slate', json.dumps(addition['key']))
            self.assertEqual(addition['key']['O'], core)
            self.assertEqual(addition['key']['K'], {'item': component})
        strong = {'echoing', 'ignore_conditions', 'ignore_light', 'ignore_players', 'no_ai', 'redstone_control'}
        basic = {'min_delay', 'max_delay', 'spawn_count', 'spawn_range', 'player_range'}
        self.assertTrue(all(balance.AUGMENTS[m][0] == 'V' for m in strong))
        self.assertTrue(all(balance.AUGMENTS[m][0] in ('III', 'IV') for m in basic))

    def test_additions_reject_wrong_acts_and_unknown_items(self):
        models = {'apothic_enchanting:hellshelf', 'apothic_enchanting:infused_seashelf', 'apothic_enchanting:deepshelf',
                  'apothic_enchanting:ender_library', 'create:copper_sheet', 'apotheosis:gem_dust',
                  'apotheosis:timeworn_fabric', 'apotheosis:luminous_crystal_shard', 'apotheosis:arcane_sands'}
        self.assertEqual(len(balance.build_additions('apotheosis', models)), 21)
        wrong = balance.recipe('entrelumen:lumen_shelf', ['GL'], {'G': {'item': 'minecraft:glowstone'},
                                                               'L': {'item': 'entrelumen:spectral_lens'}}, 'III', '')
        unknown = balance.recipe('entrelumen:lumen_shelf', ['GL'], {'G': {'item': 'apotheosis:not_real'},
                                                                 'L': {'item': 'entrelumen:spectral_lens'}}, 'IV', '')
        for addition in (wrong, unknown):
            with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'changes': [], 'additions': [addition]}}):
                with self.assertRaises(AssertionError):
                    balance.build_additions('probe', models)

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_scripts_are_current_against_pinned_jars(self):
        for name, family in balance.FAMILIES.items():
            with self.subTest(family=name):
                rows, removals, used, _ = balance.build(name)
                expected = balance.render(name, rows, removals, used)
                self.assertEqual((ROOT / 'pack/kubejs/server_scripts' / family['script']).read_text(encoding='utf-8'), expected)
                for relative, text in {**balance.build_data(name), **balance.build_additions(name)}.items():
                    self.assertEqual((balance.PACK_DATA / relative).read_text(encoding='utf-8'), text)


if __name__ == '__main__':
    unittest.main()
