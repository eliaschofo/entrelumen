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


def template(*tables, jigsaws=()):
    """A gzipped structure template whose chests name `tables` and whose jigsaw blocks name the pools
    `jigsaws`, with a zeroed CRC in the gzip trailer."""
    def block(fields):
        return tag(3, 'state', struct.pack('>i', 0)) + tag(10, 'nbt', b''.join(
            tag(8, key, text(value)) for key, value in fields.items()) + b'\0') + b'\0'
    blocks = [block({'id': 'minecraft:chest', 'LootTable': t}) for t in tables]
    blocks += [block({'id': 'minecraft:jigsaw', 'pool': p, 'name': 'x:door', 'target': 'x:door'}) for p in jigsaws]
    root = tag(10, '', tag(9, 'blocks', bytes([10]) + struct.pack('>i', len(blocks)) + b''.join(blocks))
               + tag(3, 'DataVersion', struct.pack('>i', 3955)) + b'\0')
    packed = gzip.compress(root)
    return packed[:-8] + b'\0\0\0\0' + packed[-4:]


def pool(*locations):
    return {'fallback': 'minecraft:empty', 'elements': [
        {'weight': 1, 'element': {'element_type': 'minecraft:single_pool_element', 'location': location,
                                  'processors': 'minecraft:empty', 'projection': 'rigid'}} for location in locations]}


def jigsaw_structure(start, biomes):
    return {'type': 'minecraft:jigsaw', 'start_pool': start, 'biomes': biomes, 'step': 'surface_structures', 'size': 3}


def write(root, files):
    for name, value in files.items():
        path = Path(root) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())


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
            'bare enchant_randomly': (PATH, edited(lambda t: t['pools'][0]['entries'][1]['functions'][0].pop('options'))),
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

    def test_licenses_that_reserve_data(self):
        cases = {'ARR': True, 'All Rights Reserved': True, 'LicenseRef-All-Rights-Reserved': True, 'CC-BY-NC-ND-4.0': True,
                 'Assets: All Rights Reserved; Code: GPL-3.0': True,
                 'All assets of The Cataclysm are unlicensed and all rights are reserved to them by MCL_Ender. '
                 'The source code LGPL v3.0 license': True,
                 'Read attached LICENSE.md': True, '': True,
                 'MIT': False, 'GNU GPLv3': False, 'LGPL-3.0-or-later': False, 'CC0-1.0': False}
        for license, reserved in cases.items():
            with self.subTest(license):
                self.assertEqual(check.is_restricted('mod.jar', license), reserved)
        # Ad Astra's license was read by hand: its data files are MIT.
        self.assertFalse(check.is_restricted('adastra-1.21.1-1.16.19-neoforge.jar', 'Terrarium License'))
        self.assertTrue(check.is_restricted('other.jar', 'Terrarium License'))

    def test_copies_of_reserved_data_are_caught_and_a_self_drop_is_not(self):
        ritual = {'type': 'x:ritual', 'input': {'item': 'x:a'}, 'result': {'id': 'x:b', 'count': 2}, 'time': 200, 'essence': 30}
        skull = {'type': 'minecraft:block', 'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [
            {'type': 'minecraft:item', 'name': 'x:skull'}]}], 'random_sequence': 'x:blocks/skull'}
        drop = {'type': 'minecraft:block', 'pools': [{'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                                                      'entries': [{'type': 'minecraft:item', 'name': 'x:skull'}], 'rolls': 1.0}],
                'random_sequence': 'x:blocks/skull'}
        with tempfile.TemporaryDirectory() as folder:
            reserved, open_jar, pack = Path(folder) / 'arr.jar', Path(folder) / 'mit.jar', Path(folder) / 'pack'
            with zipfile.ZipFile(reserved, 'w') as archive:
                for name in ('same_bytes', 'disabled', 'edited', 'own'):
                    archive.writestr(f'data/x/recipe/{name}.json', json.dumps(ritual))
                archive.writestr('data/x/loot_tables/blocks/skull.json', json.dumps(skull))
            with zipfile.ZipFile(open_jar, 'w') as archive:
                archive.writestr('data/x/recipe/open.json', json.dumps(ritual))
            write(pack, {'data/x/recipe/same_bytes.json': json.dumps(ritual).encode(),
                         'data/x/recipe/disabled.json': {'neoforge:conditions': [{'type': 'neoforge:false'}], **ritual},
                         'data/x/recipe/edited.json': dict(ritual, time=100),
                         'data/x/recipe/own.json': {'type': 'x:ritual', 'input': {'item': 'x:c'}, 'result': {'id': 'x:d'}},
                         'data/x/recipe/open.json': ritual,            # an open JAR's data
                         'data/y/recipe/same_bytes.json': ritual,      # another namespace
                         'data/x/loot_table/blocks/skull.json': drop})  # our self-drop, written from the block ID
            sources = check.Sources({'arr.jar': reserved, 'mit.jar': open_jar}, vanilla_jar=None, local_roots=(pack,))
            try:
                found = check.copies(sources, {'arr.jar': 'All Rights Reserved'}, root=pack / 'data')
            finally:
                sources.close()
        self.assertEqual([(mine, why) for mine, _, _, why in found], [
            ('data/x/recipe/disabled.json', 'identical JSON'),
            ('data/x/recipe/edited.json', 'shares 83% of its JSON leaves'),
            ('data/x/recipe/same_bytes.json', 'identical bytes')])

    def test_structures_count_only_what_a_generating_structure_places_and_the_pack_wins(self):
        placement = {'type': 'minecraft:random_spread', 'spacing': 20, 'separation': 8, 'salt': 1}
        jar_files = {
            'data/x/worldgen/structure/camp.json': jigsaw_structure('x:camp/start', '#x:has_camp'),
            'data/x/worldgen/structure/bullet.json': jigsaw_structure('x:bullet_start', '#x:has_bullet'),
            'data/x/worldgen/structure/hut.json': jigsaw_structure('x:hut', 'minecraft:plains'),
            'data/x/worldgen/structure_set/camp.json': {'structures': [{'structure': 'x:camp', 'weight': 1},
                                                                       {'structure': 'x:bullet', 'weight': 1}],
                                                        'placement': placement},
            # Loaded only with Wythers, which the pack does not have: the hut never generates.
            'data/x/worldgen/structure_set/hut.json': {
                'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': 'wythers'}],
                'structures': [{'structure': 'x:hut', 'weight': 1}], 'placement': placement},
            'data/x/tags/worldgen/biome/has_camp.json': {'values': ['minecraft:plains', {'id': 'y:absent', 'required': False}]},
            'data/x/tags/worldgen/biome/has_bullet.json': {'values': ['x:venus']},
            # Comments are fine: the game reads data files with a lenient parser.
            'data/x/worldgen/template_pool/camp/start.json': b'{ // the camp\n' + json.dumps(pool('x:camp/tent'))[1:].encode(),
            'data/x/worldgen/template_pool/camp/rooms.json': pool('x:camp/library'),
            'data/x/worldgen/template_pool/bullet_start.json': pool('x:bullet'),
            'data/x/worldgen/template_pool/hut.json': pool('x:hut'),
            'data/x/structure/camp/tent.nbt': template('x:chests/tent', 'wythers:chests/house', jigsaws=['x:camp/rooms']),
            'data/x/structure/camp/libary.nbt': template('x:chests/lib'),
            'data/x/structure/camp/orphan.nbt': template('x:chests/orphan'),
            'data/x/loot_table/chests/lib.json': {'type': 'minecraft:chest', 'pools': []},
        }
        with tempfile.TemporaryDirectory() as folder:
            jar, pack = Path(folder) / 'mod.jar', Path(folder) / 'pack'
            with zipfile.ZipFile(jar, 'w') as archive:
                for name, value in jar_files.items():
                    archive.writestr(name, value if isinstance(value, bytes) else json.dumps(value))

            def report():
                sources = check.Sources({'mod.jar': jar}, vanilla_jar=None, local_roots=(pack,))
                try:
                    return check.structure_report(check.Worldgen(sources, {'x'}), check.build_index(sources=sources), 'mod.jar')
                finally:
                    sources.close()
            errors, before = report()
            self.assertEqual(sorted(e.split(': ', 1)[1].split(',')[0] for e in errors), [
                'template pool x:bullet_start names template x:bullet',
                'template pool x:camp/rooms names template x:camp/library',
                'x:chests/tent is named 1 times in x:camp/tent'])
            self.assertEqual((before['structures'], before['generating']), (3, 2))
            self.assertEqual(before['unchecked'], {
                'missing template in a pool no generating structure reaches': ['x:hut -> x:hut'],
                'loot table of a mod the pack does not have': ['wythers:chests/house (1 in x:camp/tent)'],
                'loot table in a template no generating structure places': ['x:chests/orphan (1 in x:camp/orphan)']})
            # The pack's files win: the pool names the shipped template, the empty structure's biome tag is
            # replaced by an empty one, and the chest's table becomes an alias.
            write(pack, {'data/x/worldgen/template_pool/camp/rooms.json': pool('x:camp/libary'),
                         'data/x/tags/worldgen/biome/has_bullet.json': {'replace': True, 'values': []},
                         'data/x/loot_table/chests/tent.json': {'type': 'minecraft:chest', 'pools': [{'rolls': 1, 'entries': [
                             {'type': 'minecraft:loot_table', 'value': 'x:chests/lib'}]}]}})
            errors, after = report()
            self.assertEqual(errors, [])
            self.assertEqual((after['generating'], after['templatesPlaced']), (1, 2))
            self.assertEqual(after['unchecked']['missing template in a pool no generating structure reaches'],
                             ['x:bullet_start -> x:bullet', 'x:hut -> x:hut'])


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


@functools.lru_cache(maxsize=1)
def pingpong_data():
    spec = importlib.util.spec_from_file_location('family_balance_for_loot', Path(__file__).with_name('generate_family_balance.py'))
    balance = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(balance)
    return balance.FAMILIES['pingpong']['data']


def generated_tables():
    """Loot tables the pingpong family writes for missing or unloaded upstream tables (ports, aliases, self-drops)."""
    paths = [s['to'] if s['op'] == 'port_loot' else s['path'] for s in pingpong_data()
             if s['op'] in ('port_loot', 'alias_loot', 'self_drop')]
    return [check.PACK / path for path in paths]


FIXED_JARS = ('adastra-', 'deep_aether-', 'dungeons-and-taverns-', "L_Ender's Cataclysm", 't_and_t-')


@unittest.skipUnless(full_lock(), 'Pinned dependency JARs or the vanilla client JAR are not available on this machine')
class LootIntegrityTest(unittest.TestCase):
    """The loot integrity pass of 28 September 2026 (docs/design/mod-pingpong.md)."""

    def test_every_port_and_alias_parses_as_a_121_table_with_nothing_unchecked(self):
        _, index = pinned_index()
        tables = generated_tables()
        self.assertEqual(len(tables), 19)
        for path in tables:
            with self.subTest(path.relative_to(check.PACK).as_posix()):
                name = path.relative_to(ROOT).as_posix()
                errors, unchecked = check.check_table(name, json.loads(path.read_text(encoding='utf-8')), index)
                self.assertEqual(errors, [])
                self.assertFalse([u for u in unchecked if 'survives_explosion' not in u])

    def test_this_pass_copies_no_reserved_data(self):
        sources = check.Sources(check.pinned_jars())
        try:
            flagged = {mine for mine, *_ in check.copies(sources, check.restricted_jars())}
        finally:
            sources.close()
        ours = {path.relative_to(check.PACK).as_posix() for path in generated_tables()}
        ours |= {'data/ad_astra/worldgen/template_pool/dungeon/moon/room.json',
                 'data/ad_astra/tags/worldgen/biome/has_structure/venus_bullet.json'}
        self.assertIn('data/endermanoverhaul/loot_table/blocks/tiny_skull.json', ours)
        self.assertFalse(flagged & ours)

    def test_the_fixed_structures_place_only_templates_and_tables_that_load(self):
        jars, mods = check.pinned_jars(), check.pinned_mods()
        labels = [name for name in jars for prefix in FIXED_JARS if name.startswith(prefix)]
        self.assertEqual(len(labels), len(FIXED_JARS))
        for local_roots in ((check.COMPANION, check.PACK), ()):
            sources = check.Sources(jars, local_roots=local_roots)
            index, worldgen = check.build_index(sources=sources), check.Worldgen(sources, mods)
            try:
                reports = {label: check.structure_report(worldgen, index, label) for label in labels}
            finally:
                sources.close()
            errors = sorted(e for found, _ in reports.values() for e in found)
            loot = [e for e in errors if 'no 1.21 loot_table path ships it' in e]
            if local_roots:
                self.assertEqual(loot, [])
                # Pools of Dungeons and Taverns and Towns and Towers that name missing templates are their own
                # upstream defects, reported and not fixed by this pass; Ad Astra's are fixed.
                self.assertEqual([e for e in errors if e not in loot and not e.startswith(
                    ('dungeons-and-taverns-', 't_and_t-'))], [])
                unchecked = {label: report.get('unchecked', {}) for label, (_, report) in reports.items()}
                (tt,) = [label for label in labels if label.startswith('t_and_t-')]
                self.assertEqual([t.split(' ')[0] for t in unchecked[tt]['loot table of a mod the pack does not have']],
                                 ['wythers:chests/village/forest_ruins_big_house'])
                (dt,) = [label for label in labels if label.startswith('dungeons-and-taverns-')]
                self.assertEqual([t.split(' ')[0] for t in unchecked[dt]['loot table in a template no generating structure places']],
                                 [f'nova_structures:chests/ruin_loot_{i}' for i in range(1, 8)])
                (adastra,) = [label for label in labels if label.startswith('adastra-')]
                self.assertEqual(unchecked[adastra]['missing template in a pool no generating structure reaches'],
                                 ['ad_astra:run_venus_bullet/side_venus_bullet_start -> ad_astra:venus_bullet'])
            else:  # upstream alone: what the pass fixes
                self.assertIn('template pool ad_astra:dungeon/moon/room names template ad_astra:dungeon/moon/library',
                              ' '.join(errors))
                self.assertIn('template pool ad_astra:run_venus_bullet/side_venus_bullet_start names template '
                              'ad_astra:venus_bullet', ' '.join(errors))
                self.assertEqual(len(loot), 6 + 12)


if __name__ == '__main__':
    unittest.main()
