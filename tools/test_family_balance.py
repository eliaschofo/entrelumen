"""Static checks for generated family staging scripts; JAR-backed checks when the lock is local."""
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import re
import unittest


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

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_scripts_are_current_against_pinned_jars(self):
        for name, family in balance.FAMILIES.items():
            with self.subTest(family=name):
                rows, removals, used, _ = balance.build(name)
                expected = balance.render(name, rows, removals, used)
                self.assertEqual((ROOT / 'pack/kubejs/server_scripts' / family['script']).read_text(encoding='utf-8'), expected)


if __name__ == '__main__':
    unittest.main()
