"""Tests for tools/check_loot_tables.py; JAR-backed checks when the lock and the vanilla JAR are local."""
from __future__ import annotations

import functools
import gzip
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('check_loot_tables_under_test', Path(__file__).with_name('check_loot_tables.py'))
check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check)

INDEX = check.Index(['assets/minecraft/models/item/book.json', 'assets/minecraft/models/item/emerald.json',
                     'assets/x/models/item/gem.json', 'data/minecraft/tags/enchantment/on_random_loot.json',
                     'data/minecraft/enchantment/mending.json', 'data/c/tags/item/gems.json',
                     'data/minecraft/loot_table/chests/simple_dungeon.json'], vanilla=True, pinned=True)
PATH = 'data/x/loot_table/chests/a.json'
TABLE = {'type': 'minecraft:chest', 'pools': [{
    'rolls': {'type': 'minecraft:uniform', 'min': 3.0, 'max': 8.0}, 'bonus_rolls': 0.0,
    'entries': [
        {'type': 'minecraft:item', 'name': 'x:gem', 'weight': 7,
         'functions': [{'function': 'minecraft:set_count', 'count': {'min': 1, 'max': 4}, 'add': False}]},
        {'type': 'minecraft:item', 'name': 'minecraft:book', 'weight': 5,
         'functions': [{'function': 'minecraft:enchant_randomly', 'options': '#minecraft:on_random_loot'}]},
        {'type': 'item', 'name': 'emerald', 'functions': [{'function': 'set_count', 'count': 2}]},
        {'type': 'minecraft:tag', 'name': 'c:gems', 'expand': True},
        {'type': 'minecraft:loot_table', 'value': 'minecraft:chests/simple_dungeon'},
        {'type': 'minecraft:empty', 'weight': 3}]}]}


def edited(change):
    table = json.loads(json.dumps(TABLE))
    change(table)
    return table


def tag(kind, name, payload):
    raw = name.encode()
    return bytes([kind]) + struct.pack('>H', len(raw)) + raw + payload


def text(value):
    raw = value.encode()
    return struct.pack('>H', len(raw)) + raw


def template(*tables):
    """A gzipped structure template whose chests name `tables`, with a zeroed CRC in the gzip trailer."""
    blocks = [tag(3, 'state', struct.pack('>i', 0)) + tag(10, 'nbt', tag(8, 'id', text('minecraft:chest'))
              + tag(8, 'LootTable', text(t)) + b'\0') + b'\0' for t in tables]
    root = tag(10, '', tag(9, 'blocks', bytes([10]) + struct.pack('>i', len(blocks)) + b''.join(blocks))
               + tag(3, 'DataVersion', struct.pack('>i', 3955)) + b'\0')
    packed = gzip.compress(root)
    return packed[:-8] + b'\0\0\0\0' + packed[-4:]


class CheckLootTablesTest(unittest.TestCase):
    def test_a_121_table_is_checked_in_full(self):
        self.assertEqual(check.check_table(PATH, TABLE, INDEX), ([], []))

    def test_120_leftovers_and_broken_shapes_fail(self):
        cases = {
            'old folder': ('data/x/loot_tables/chests/a.json', TABLE),
            'set_nbt': (PATH, edited(lambda t: t['pools'][0]['entries'][0]['functions'].append(
                {'function': 'minecraft:set_nbt', 'tag': '{Potion:"minecraft:healing"}'}))),
            'enchantments': (PATH, edited(lambda t: t['pools'][0]['entries'][1]['functions'][0].update(
                enchantments=['minecraft:mending']))),
            'nested by name': (PATH, edited(lambda t: t['pools'][0]['entries'][4].update(name='minecraft:x'))),
            'no rolls': (PATH, edited(lambda t: t['pools'][0].pop('rolls'))),
            'half uniform': (PATH, edited(lambda t: t['pools'][0].update(rolls={'min': 1}))),
            'unknown item': (PATH, edited(lambda t: t['pools'][0]['entries'][0].update(name='x:nothing'))),
            'unknown tag': (PATH, edited(lambda t: t['pools'][0]['entries'][1]['functions'][0].update(
                options='#minecraft:nothing'))),
            'unknown context': (PATH, edited(lambda t: t.update(type='minecraft:treasure'))),
            'fractional weight': (PATH, edited(lambda t: t['pools'][0]['entries'][0].update(weight=1.5))),
            'typo': (PATH, edited(lambda t: t['pools'][0].update(bonus_roll=1))),
            'tag without expand': (PATH, edited(lambda t: t['pools'][0]['entries'][3].pop('expand'))),
        }
        for label, (path, table) in cases.items():
            with self.subTest(label):
                errors, _ = check.check_table(path, table, INDEX)
                self.assertTrue(errors)

    def test_what_is_not_modelled_is_unchecked_not_passed(self):
        table = edited(lambda t: t['pools'][0]['entries'][0].update(
            conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.5}],
            functions=[{'function': 'x:bonus'}]))
        errors, unchecked = check.check_table(PATH, table, INDEX)
        self.assertEqual(errors, [])
        self.assertEqual([u.split(': ', 1)[1] for u in unchecked], ['condition minecraft:random_chance', 'function x:bonus'])
        # Without the vanilla JAR a vanilla item is unchecked; a mod item still resolves against the pinned JARs.
        partial = check.Index(['assets/x/models/item/gem.json'], vanilla=False, pinned=True)
        self.assertEqual(partial.missing('item', 'minecraft:book'), 'unchecked')
        self.assertEqual(partial.missing('item', 'x:nothing'), 'missing')
        self.assertIsNone(partial.missing('item', 'x:gem'))

    def test_templates_and_processors_name_their_tables_even_with_a_bad_gzip_trailer(self):
        raw = template('ad_astra:chests/a', 'loot', 'ad_astra:chests/a')
        self.assertEqual(check.read_nbt(raw)['DataVersion'], 3955)
        processors = {'processors': [{'processor_type': 'minecraft:rule', 'rules': [{
            'input_predicate': {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:chest'},
            'location_predicate': {'predicate_type': 'minecraft:always_true'}, 'output_state': {'Name': 'minecraft:chest'},
            'block_entity_modifier': {'type': 'minecraft:append_loot', 'loot_table': 'x:chests/b'}}]}]}
        with tempfile.TemporaryDirectory() as folder:
            jar = Path(folder) / 'mod.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('data/ad_astra/structure/room.nbt', raw)
                archive.writestr('data/ad_astra/structure/broken.nbt', b'\x1f\x8b\x08\x00' + b'\0' * 12)
                archive.writestr('data/ad_astra/structures/old.nbt', raw)  # 1.20 folder: not a 1.21 template
                archive.writestr('data/x/worldgen/processor_list/loot.json', json.dumps(processors))
            unreadable = []
            refs = check.structure_loot_tables(jar, unreadable)
        self.assertEqual({k: dict(v) for k, v in refs.items()},
                         {'ad_astra:chests/a': {'ad_astra:room': 2}, 'minecraft:loot': {'ad_astra:room': 1},
                          'x:chests/b': {'processor list x:loot': 1}})
        self.assertEqual(unreadable, ['data/ad_astra/structure/broken.nbt'])


@functools.lru_cache(maxsize=None)
def pinned_index(with_pack=True):
    jars = check.pinned_jars()
    return jars, check.build_index(jars=jars, local_roots=(check.PACK, check.COMPANION) if with_pack else ())


def full_lock():
    return check.pinned_jars() is not None and check.VANILLA_JAR.is_file()


PORTS = sorted((check.PACK / 'data/ad_astra/loot_table/chests').rglob('*.json')) \
    + [check.PACK / 'data/minecraft/loot_table/loot.json']


@unittest.skipUnless(full_lock(), 'Pinned dependency JARs or the vanilla client JAR are not available on this machine')
class AdAstraChestLootTest(unittest.TestCase):
    """Ad Astra 1.16.19 ships its chest loot in the 1.20 folder (docs/design/mod-pingpong.md)."""

    def test_every_port_parses_as_a_121_table_with_nothing_unchecked(self):
        _, index = pinned_index()
        self.assertEqual(len(PORTS), 6)
        for path in PORTS:
            with self.subTest(path.name):
                name = path.relative_to(ROOT).as_posix()
                self.assertEqual(check.check_table(name, json.loads(path.read_text(encoding='utf-8')), index), ([], []))

    def test_every_ad_astra_chest_names_a_table_that_loads_only_with_the_ports(self):
        jars, index = pinned_index()
        (jar,) = [path for name, path in jars.items() if name.startswith('adastra-')]
        refs = check.structure_loot_tables(jar)
        ported = {f'{p.relative_to(check.PACK / "data").parts[0]}:'
                  f'{"/".join(p.relative_to(check.PACK / "data").parts[2:])[:-len(".json")]}' for p in PORTS}
        self.assertEqual(set(refs), ported)
        self.assertLessEqual(set(refs), index.ids['loot_table'])
        _, upstream_only = pinned_index(with_pack=False)
        self.assertFalse(set(refs) & upstream_only.ids['loot_table'])  # the bug: none loads without the pack


if __name__ == '__main__':
    unittest.main()
