"""Regression for the 85 optional block-loot decodes observed in Act V."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'pack/kubejs/server_scripts/entrelumen_optional_loot_compat.js'
EXPECTED_IDS_SHA256 = '397e3d1bdc589d4e639efa9624dc07851f458743b0e573ece03d178e79a62a64'
SOURCES = {
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
const selected = new Set([allIds[0], allIds[19], allIds[84]]);
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
        self.assertEqual(len(self.ids), 85)
        self.assertEqual(len(set(self.ids)), 85)
        digest = hashlib.sha256('\n'.join(sorted(
            item.replace(':', ':blocks/', 1) for item in self.ids
        )).encode()).hexdigest()
        self.assertEqual(digest, EXPECTED_IDS_SHA256)
        for (path, value), item in zip(self.writes, self.ids):
            self.assertEqual(path, item.replace(':', ':loot_table/blocks/', 1) + '.json')
            self.assertEqual(value, {
                'type': 'minecraft:block',
                'neoforge:conditions': [{'type': 'neoforge:item_exists', 'item': item}],
            })

    def test_present_upstream_tables_are_not_overridden(self):
        self.assertEqual(self.result['present']['writes'], [])
        selected = set(self.result['selected'])
        mixed = self.result['mixed']['writes']
        self.assertEqual(len(mixed), 85 - len(selected))
        self.assertEqual({path.replace(':loot_table/blocks/', ':').removesuffix('.json')
                          for path, _ in mixed}, set(self.ids) - selected)
        self.assertEqual(json.loads(self.result['mixed']['logs'][0].split('] ', 1)[1])['empty'],
                         {'mekmm': 78, 'mekanism_extras': 4, 'extendedae': 0})

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


if __name__ == '__main__':
    unittest.main()
