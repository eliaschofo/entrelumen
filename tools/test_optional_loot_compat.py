"""Regression for the 85 Act V and 16 Connected optional block-loot decodes."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'pack/kubejs/server_scripts/entrelumen_optional_loot_compat.js'
EXPECTED_IDS_SHA256 = '9d61ed27683a98dc7402fcdfcd2d9435e39ceb6247300e00376b5ccdc017ff55'
EXPECTED_ACT_V_IDS_SHA256 = '397e3d1bdc589d4e639efa9624dc07851f458743b0e573ece03d178e79a62a64'
DYE_DEPOT_COLORS = (
    'amber', 'aqua', 'beige', 'coral', 'forest', 'ginger', 'indigo', 'maroon',
    'mint', 'navy', 'olive', 'rose', 'slate', 'tan', 'teal', 'verdant',
)
VANILLA_COLORS = (
    'black', 'blue', 'brown', 'cyan', 'gray', 'green', 'light_blue', 'light_gray',
    'lime', 'magenta', 'orange', 'pink', 'purple', 'red', 'white', 'yellow',
)
SOURCES = {
    'create_connected': 'create_connected-1.3.3-mc1.21.1.jar',
    'extendedae': 'ExtendedAE-1.21-2.2.35-neoforge.jar',
    'mekanism_extras': 'mekanism_extras-1.21.1-1.4.1.jar',
    'mekmm': 'mekmm-1.21.1-1.4.1.jar',
}

HARNESS = r"""
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(process.argv[1], 'utf8');

function run(present) {
  const writes = [];
  const logs = [];
  let callback;
  vm.runInNewContext(source, {
    ServerEvents: {generateData: (phase, fn) => {
      if (phase !== 'after_mods') throw new Error('wrong generation phase');
      callback = fn;
    }},
    Item: {exists: id => present.has(id)},
    console: {info: line => logs.push(line)}
  }, {filename: process.argv[1]});
  if (!callback) throw new Error('no generation callback');
  callback({json: (id, value) => writes.push([id, value])});
  return {writes, logs};
}

const missing = run(new Set());
const allIds = missing.writes.map(([id]) => id.replace(':loot_table/blocks/', ':').replace(/\.json$/, ''));
const selected = new Set([
  'mekmm:creative_centrifuging_factory',
  'extendedae:ex_emc_interface',
  'create_connected:dye_depot_amber_fan_dyeing_catalyst'
]);
process.stdout.write(JSON.stringify({missing, present: run(new Set(allIds)), mixed: run(selected), selected: [...selected]}));
"""


def generated():
    result = subprocess.run(
        ['node', '-e', HARNESS, str(SCRIPT)], capture_output=True, text=True,
        check=True, encoding='utf-8', creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
    )
    return json.loads(result.stdout)


class OptionalLootCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.result = generated()
        cls.writes = cls.result['missing']['writes']
        cls.ids = [path.replace(':loot_table/blocks/', ':').removesuffix('.json')
                   for path, _ in cls.writes]

    def test_exact_observed_failures_and_conditions(self):
        self.assertEqual(len(self.ids), 101)
        self.assertEqual(len(set(self.ids)), 101)
        digest = hashlib.sha256('\n'.join(sorted(
            item.replace(':', ':blocks/', 1) for item in self.ids
        )).encode()).hexdigest()
        self.assertEqual(digest, EXPECTED_IDS_SHA256)
        act_v_ids = [item for item in self.ids if not item.startswith('create_connected:')]
        self.assertEqual(len(act_v_ids), 85)
        self.assertEqual(hashlib.sha256('\n'.join(sorted(
            item.replace(':', ':blocks/', 1) for item in act_v_ids
        )).encode()).hexdigest(), EXPECTED_ACT_V_IDS_SHA256)
        self.assertEqual(
            {item for item in self.ids if item.startswith('create_connected:')},
            {f'create_connected:dye_depot_{color}_fan_dyeing_catalyst'
             for color in DYE_DEPOT_COLORS},
        )
        self.assertTrue({f'create_connected:{color}_fan_dyeing_catalyst'
                         for color in VANILLA_COLORS}.isdisjoint(self.ids))
        for (path, value), item in zip(self.writes, self.ids):
            self.assertEqual(path, item.replace(':', ':loot_table/blocks/', 1) + '.json')
            self.assertEqual(value, {
                'type': 'minecraft:block',
                'neoforge:conditions': [{'type': 'neoforge:item_exists', 'item': item}],
            })

    def test_present_upstream_tables_are_not_overridden(self):
        self.assertEqual(self.result['present']['writes'], [])
        selected = set(self.result['selected'])
        self.assertTrue(selected.issubset(self.ids))
        mixed = self.result['mixed']['writes']
        self.assertEqual(len(mixed), 101 - len(selected))
        self.assertEqual({path.replace(':loot_table/blocks/', ':').removesuffix('.json')
                          for path, _ in mixed}, set(self.ids) - selected)
        receipts = [json.loads(self.result[case]['logs'][0].split('] ', 1)[1])
                    for case in ('missing', 'present', 'mixed')]
        self.assertTrue(all(receipt['checked'] == 101 for receipt in receipts))
        self.assertEqual(receipts[0]['empty'],
                         {'mekmm': 80, 'mekanism_extras': 4, 'extendedae': 1,
                          'create_connected': 16})
        self.assertEqual(receipts[1]['empty'],
                         {'mekmm': 0, 'mekanism_extras': 0, 'extendedae': 0,
                          'create_connected': 0})
        self.assertEqual(receipts[2]['empty'],
                         {'mekmm': 79, 'mekanism_extras': 4, 'extendedae': 0,
                          'create_connected': 15})

    def test_pinned_upstream_resources_when_jars_available(self):
        local_paths = ROOT / 'catalog/local-paths.json'
        if not local_paths.exists():
            self.skipTest('local-paths.json is intentionally absent from CI')
        paths = json.loads(local_paths.read_text(encoding='utf-8'))
        curated = json.loads((ROOT / 'catalog/curated.json').read_text(encoding='utf-8'))
        pins = {entry['filename']: entry['sha256'] for entry in curated['mods']}
        available = [name for name in SOURCES.values()
                     if name in paths and Path(paths[name]).exists()]
        if not available:
            self.skipTest('pinned addon JARs are unavailable in this checkout')
        for namespace, filename in SOURCES.items():
            if filename not in available:
                continue  # Check each available local pin without requiring downloads.
            path = Path(paths[filename])
            with self.subTest(filename=filename):
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), pins[filename])
                with zipfile.ZipFile(path) as jar:
                    for item in (item for item in self.ids if item.startswith(namespace + ':')):
                        resource = f'data/{namespace}/loot_table/blocks/{item.split(":", 1)[1]}.json'
                        table = json.loads(jar.read(resource))
                        self.assertEqual(table['type'], 'minecraft:block')
                        entries = [entry for pool in table['pools'] for entry in pool['entries']]
                        self.assertEqual([entry['name'] for entry in entries], [item])
                    if namespace == 'create_connected':
                        vanilla_ids = {f'create_connected:{color}_fan_dyeing_catalyst'
                                       for color in VANILLA_COLORS}
                        catalyst_resources = {
                            name for name in jar.namelist()
                            if name.startswith('data/create_connected/loot_table/blocks/')
                            and name.endswith('_fan_dyeing_catalyst.json')
                        }
                        expected_ids = vanilla_ids | {
                            item for item in self.ids if item.startswith('create_connected:')
                        }
                        self.assertEqual(catalyst_resources, {
                            f'data/create_connected/loot_table/blocks/{item.split(":", 1)[1]}.json'
                            for item in expected_ids
                        })
                        for item in vanilla_ids:
                            resource = f'data/create_connected/loot_table/blocks/{item.split(":", 1)[1]}.json'
                            table = json.loads(jar.read(resource))
                            entries = [entry for pool in table['pools'] for entry in pool['entries']]
                            self.assertEqual([entry['name'] for entry in entries], [item])


if __name__ == '__main__':
    unittest.main()
