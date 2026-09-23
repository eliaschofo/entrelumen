"""Pinned aircraft recipe graph and static acquisition-cut checks."""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'pack/kubejs/server_scripts/entrelumen_aircraft_balance.js'
JAR = ROOT / 'catalog/downloads/immersive_aircraft-1.5.2+1.21.1-neoforge.jar'
SHA256 = 'ef5b68c04171d1eadb3bb70e600eb766534ef7588a5b4737f9d55a4f38550ae9'
VEHICLES = {
    'airship', 'biplane', 'gyrodyne', 'quadrocopter',
    'cargo_airship', 'warship', 'bamboo_hopper',
}
ACT_III_ITEMS = {'entrelumen:power_regulator', 'entrelumen:handling_core'}


def parse_overrides() -> dict[str, dict]:
    source = SCRIPT.read_text(encoding='utf-8')
    match = re.search(r'const entrelumenAircraftOverrides = (\[.*?\n\]);', source, re.S)
    if match is None:
        raise AssertionError('Static override table missing')
    rows = json.loads(match.group(1))
    return {row['id']: row['json'] for row in rows}


def native_recipes() -> dict[str, dict]:
    assert hashlib.sha256(JAR.read_bytes()).hexdigest() == SHA256
    with ZipFile(JAR) as archive:
        return {
            'immersive_aircraft:' + Path(name).stem: json.loads(archive.read(name))
            for name in archive.namelist()
            if name.startswith('data/immersive_aircraft/recipe/') and name.endswith('.json')
        }


def ingredient_ids(recipe: dict) -> Counter[str]:
    def ingredient(value: dict) -> str:
        if isinstance(value, list):
            value = value[0]
        return value.get('item', '#' + value['tag'] if 'tag' in value else '')

    return Counter(
        ingredient(recipe['key'][letter])
        for line in recipe['pattern'] for letter in line if letter != ' '
    )


class AircraftBalanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native = native_recipes()
        cls.overrides = parse_overrides()

    def test_two_recipe_cut_covers_every_native_vehicle(self):
        self.assertEqual(set(self.overrides), {'immersive_aircraft:engine', 'immersive_aircraft:gyrodyne'})
        recipes = self.native | self.overrides
        outputs = {recipe['result']['id']: rid for rid, recipe in recipes.items()}

        def requires_act_iii(item: str, visiting: frozenset[str] = frozenset()) -> bool:
            if item in ACT_III_ITEMS:
                return True
            rid = outputs.get(item)
            if rid is None:
                return False
            self.assertNotIn(rid, visiting, f'cycle in aircraft recipe graph: {rid}')
            return any(requires_act_iii(part, visiting | {rid}) for part in ingredient_ids(recipes[rid]))

        for name in VEHICLES:
            with self.subTest(vehicle=name):
                self.assertIn('immersive_aircraft:' + name, outputs)
                self.assertTrue(requires_act_iii('immersive_aircraft:' + name))

        # These are the three inherited routes; they must not need separate edits.
        self.assertIn('immersive_aircraft:airship', ingredient_ids(recipes['immersive_aircraft:cargo_airship']))
        self.assertIn('immersive_aircraft:cargo_airship', ingredient_ids(recipes['immersive_aircraft:warship']))
        self.assertIn('immersive_aircraft:biplane', ingredient_ids(recipes['immersive_aircraft:bamboo_hopper']))

    def test_gyrodyne_is_the_engine_bypass_and_quadrocopter_is_not(self):
        self.assertNotIn('immersive_aircraft:engine', ingredient_ids(self.native['immersive_aircraft:gyrodyne']))
        self.assertIn('immersive_aircraft:engine', ingredient_ids(self.native['immersive_aircraft:quadrocopter']))
        self.assertEqual(ingredient_ids(self.overrides['immersive_aircraft:gyrodyne'])
                         - ingredient_ids(self.native['immersive_aircraft:gyrodyne']),
                         Counter({'entrelumen:handling_core': 1}))
        self.assertEqual(ingredient_ids(self.native['immersive_aircraft:gyrodyne'])
                         - ingredient_ids(self.overrides['immersive_aircraft:gyrodyne']), Counter())

    def test_native_costs_and_outputs_survive_and_components_are_act_iii(self):
        engine_before = ingredient_ids(self.native['immersive_aircraft:engine'])
        engine_after = ingredient_ids(self.overrides['immersive_aircraft:engine'])
        self.assertEqual(engine_before - engine_after, Counter({'minecraft:cobblestone': 1}))
        self.assertEqual(engine_after - engine_before, Counter({'entrelumen:power_regulator': 1}))
        for rid, override in self.overrides.items():
            self.assertEqual(override['result'], self.native[rid]['result'])
            self.assertEqual(override['type'], self.native[rid]['type'])
        design = json.loads((ROOT / 'content/integration-design.json').read_text(encoding='utf-8'))
        producers = {p['output']['id']: p['act'] for p in design['projects']}
        self.assertEqual({producers[item] for item in ACT_III_ITEMS}, {3})

    def test_pinned_jar_has_no_native_loot_or_workstation_route(self):
        with ZipFile(JAR) as archive:
            data_paths = [name for name in archive.namelist() if name.startswith('data/immersive_aircraft/')]
        self.assertFalse([name for name in data_paths if any(segment in name for segment in
                          ('/loot_table/', '/loot_tables/', '/worldgen/', '/structure/', '/trades/'))])
        self.assertEqual({recipe['type'] for recipe in self.native.values()}, {'minecraft:crafting_shaped'})

    def test_runtime_audit_includes_engine_as_well_as_vehicle_outputs(self):
        source = SCRIPT.read_text(encoding='utf-8')
        match = re.search(r'const entrelumenAircraftRouteOutputs = (\[.*?\]);', source, re.S)
        self.assertIsNotNone(match)
        outputs = set(re.findall(r"'([^']+)'", match.group(1)))
        self.assertEqual(outputs, VEHICLES | {'engine'})


if __name__ == '__main__':
    unittest.main()
