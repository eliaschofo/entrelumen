"""Static checks for generated family staging scripts; JAR-backed checks when the lock is local."""
from __future__ import annotations

import functools
import hashlib
from collections import Counter
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
                    if component in balance.LUMINOSITY.values():
                        self.assertEqual(row['act'], balance.LUMINOSITY_ACT)
                    elif component.startswith('entrelumen:'):
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
        # An act material may keep an asymmetric native drawing as it was.
        ok = balance.transform(balance.shaped('x:out', 1, 0, {'item': 'x:a'}, balance.ALLOY_III, 'III', ''), recipe)
        self.assertEqual(ok['pattern'], ['AB ', 'ZA '])
        self.assertEqual(ok['result'], recipe['result'])
        with self.assertRaises(AssertionError):
            balance.transform(balance.shaped('x:out', 0, 1, {'item': 'x:b'}, balance.ALLOY_III, 'III', ''), recipe)
        with self.assertRaises(AssertionError):
            balance.transform(balance.shaped('x:out', 0, 0, {'item': 'x:b'}, balance.ALLOY_III, 'III', ''), recipe)
        filled = balance.transform(balance.shaped('x:out', 0, 2, None, balance.ALLOY_III, 'III', ''), recipe)
        self.assertEqual(filled['pattern'][0], 'ABZ')
        # An ENTRELUMEN component never enters a drawing that is not symmetric (playtest rule 3).
        with self.assertRaises(AssertionError):
            balance.transform(balance.shaped('x:out', 1, 0, {'item': 'x:a'}, balance.PR, 'III', ''), recipe)
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
        for modifier, (act, core, component) in balance.AUGMENTS.items():
            addition = recipes[f'entrelumen:augment_{modifier}']
            self.assertEqual(addition['act'], act)
            self.assertNotIn('rune', json.dumps(addition['key']))
            self.assertNotIn('slate', json.dumps(addition['key']))
            self.assertEqual(addition['key']['O'], core)
            self.assertEqual(addition['key']['K'], {'item': component})
            # Playtest of 24 September 2026: the component replaces Apotheosis's materials, the
            # medallion is a symmetric drawing with the component on the axis and the core in the centre.
            self.assertNotIn('apotheosis:', json.dumps(addition['key']))
            self.assertEqual(addition['pattern'], [' K ', 'SOS', ' S '])
            self.assertEqual(sum(row.count('K') for row in addition['pattern']), 1)
        strong = {'echoing', 'ignore_conditions', 'ignore_light', 'ignore_players', 'no_ai', 'redstone_control'}
        basic = {'min_delay', 'max_delay', 'spawn_count', 'spawn_range', 'player_range'}
        self.assertTrue(all(balance.AUGMENTS[m][0] == 'V' for m in strong))
        self.assertTrue(all(balance.AUGMENTS[m][0] in ('III', 'IV') for m in basic))

    def test_additions_reject_wrong_acts_and_unknown_items(self):
        models = {'apothic_enchanting:hellshelf', 'apothic_enchanting:infused_seashelf', 'apothic_enchanting:deepshelf',
                  balance.SKY,
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

    def test_luminous_recipe_files_and_script_match_the_specification(self):
        family = balance.FAMILIES['luminous']
        for relative, text in balance.creation_files('luminous').items():
            self.assertEqual((balance.PACK_DATA / relative).read_text(encoding='utf-8'), text, relative)
        script = (ROOT / 'pack/kubejs/server_scripts' / family['script']).read_text(encoding='utf-8')
        constants = {}
        for name in ('CreationsSignature', 'Creations', 'Uncraftable'):
            match = re.search(r'^const entrelumenLuminous' + name + r' = (.*);$', script, re.M)
            self.assertTrue(match, name)
            constants[name] = json.loads(match.group(1))
        self.assertEqual([(r['id'], r['output']) for r in constants['Creations']],
                         [(s['id'], s['output']) for s in family['creations']])
        self.assertEqual(constants['Uncraftable'], family['uncraftable'])
        payload = json.dumps({'creations': [balance.creation_json(s) for s in family['creations']],
                              'uncraftable': family['uncraftable']}, ensure_ascii=False, separators=(',', ':'), sort_keys=True)
        self.assertEqual(constants['CreationsSignature'], hashlib.sha256(payload.encode('utf-8')).hexdigest())
        for luminosity in balance.LUMINOSITY.values():
            self.assertIn(luminosity, constants['Uncraftable'])
        self.assertNotIn('native recipe absent', script)  # a creation-only family edits nothing

    def test_luminous_catalogue_is_symmetric_balanced_and_act_six(self):
        uses = balance.check_creations_static('luminous')
        self.assertEqual(uses, {d: 8 for d in balance.DISCIPLINES})
        specs = balance.FAMILIES['luminous']['creations']
        creative = [s for s in specs if s['disciplines']]
        self.assertEqual(len(creative), 12)
        self.assertEqual(len([s for s in specs if s['kind'] == 'smithing']), 9)
        self.assertTrue(all(s['act'] == 'VI' for s in specs))
        cube = next(s for s in specs if s['output'] == 'mekanism:creative_energy_cube')
        self.assertEqual(balance.creation_json(cube)['result']['components'],
                         {'mekanism:energy': {'energy_containers': [9223372036854775807]}})
        ingot = next(s for s in specs if s['output'] == balance.LUMINOUS_INGOT)
        self.assertEqual(sorted(set(balance.creation_inputs(ingot)) - set(balance.LUMINOSITY.values())),
                         ['apotheosis:godforged_pearl', 'mekanism:alloy_atomic', 'naturesaura:sky_ingot'])
        asymmetric = {'A': {'item': 'x:a'}, 'B': {'item': 'x:b'}, 'L': {'item': balance.LUMINOSITY['arcane']},
                      'N': {'item': balance.LUMINOSITY['nature']}}
        self.assertTrue(balance.mirrored(['LAN', 'BAB', 'NBL'], asymmetric))
        self.assertFalse(balance.mirrored(['LAB', 'BAB', 'NBL'], asymmetric))
        for duplicator in ('create:creative_crate', 'ae2:creative_storage_cell', 'mekanism:creative_bin'):
            self.assertIn(duplicator, balance.FAMILIES['luminous']['uncraftable'])
            self.assertNotIn(duplicator, {s['output'] for s in specs})

    def test_luminous_items_are_named_in_both_languages(self):
        lang = ROOT / 'companion/src/main/resources/assets/entrelumen/lang'
        english = json.loads((lang / 'en_us.json').read_text(encoding='utf-8'))
        spanish = json.loads((lang / 'es_es.json').read_text(encoding='utf-8'))
        java = (ROOT / 'companion/src/main/java/dev/entrelumen/Luminous.java').read_text(encoding='utf-8')
        items = [s['output'] for s in balance.FAMILIES['luminous']['creations'] if s['output'].startswith('entrelumen:')]
        items += list(balance.LUMINOSITY.values())
        self.assertEqual(len(set(items)), 16)
        for item_id in items:
            key = 'item.entrelumen.' + item_id.split(':', 1)[1]
            self.assertIn(key, english)
            self.assertIn(key, spanish)
            self.assertNotEqual(english[key], spanish[key])
        for piece in ('helmet', 'chestplate', 'leggings', 'boots', 'sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'ingot'):
            self.assertIn(f'"luminous_{piece}"', java)

    def test_rewrite_override_keeps_other_fields_and_refuses_upstream_changes(self):
        original = {'type': 'farmersdelight:cutting', 'ingredients': [{'item': 'x:log'}], 'result': [{'item': 'x:out'}],
                    'sound': 'x:sound'}
        spec = balance.rewritten('data/x/recipe/y.json', {'result': [{'item': 'x:out'}], 'sound': 'x:sound'},
                                 {'result': [{'item': {'count': 1, 'id': 'x:out'}}], 'sound': {'sound_id': 'x:sound'}}, '')
        found = {spec['path']: [('x.jar', '', json.dumps(original).encode())]}
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = json.loads(balance.build_data('probe', found)['x/recipe/y.json'])
            self.assertEqual(out['ingredients'], original['ingredients'])
            self.assertEqual(out['sound'], {'sound_id': 'x:sound'})
            found[spec['path']] = [('x.jar', '', json.dumps(dict(original, sound='x:other')).encode())]
            with self.assertRaises(AssertionError):
                balance.build_data('probe', found)
        fd = [s for s in balance.FAMILIES['luminous']['data'] if s['op'] == 'rewrite']
        self.assertEqual(len(fd), 1)
        written = json.loads((balance.PACK_DATA / fd[0]['path'][len('data/'):]).read_text(encoding='utf-8'))
        self.assertEqual({k: written[k] for k in fd[0]['value']}, fd[0]['value'])
        self.assertEqual(written['neoforge:conditions'], [{'type': 'neoforge:mod_loaded', 'modid': 'silentgear'}])

    def test_disable_keeps_existing_conditions_and_renames_several_keys(self):
        conditional = {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': 'create'}],
                       'type': 'create:crushing', 'results': [{'item': 'x:out'}]}
        plain = {'type': 'x:recipe', 'result': {'id': 'x:out'}}
        datamap = {'values': {'a:old': {'v': 1}, 'b:old': {'v': 2}, 'c:keep': {'v': 3}}}
        specs = [balance.disabled('data/x/recipe/conditional.json', ''), balance.disabled('data/x/recipe/plain.json', ''),
                 balance.renamed_key('data/x/data_maps/m.json', 'values', 'a:old', 'a:new', '', more={'b:old': 'b:new'})]
        found = {specs[0]['path']: [('x.jar', '', json.dumps(conditional).encode())],
                 specs[1]['path']: [('x.jar', '', json.dumps(plain).encode())],
                 specs[2]['path']: [('x.jar', '', json.dumps(datamap).encode())]}
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': specs}}):
            out = {k: json.loads(v) for k, v in balance.build_data('probe', found).items()}
        self.assertEqual(out['x/recipe/conditional.json']['neoforge:conditions'],
                         [{'type': 'neoforge:false'}, {'type': 'neoforge:mod_loaded', 'modid': 'create'}])
        self.assertEqual(list(out['x/recipe/plain.json'])[0], 'neoforge:conditions')
        self.assertEqual(out['x/recipe/plain.json']['neoforge:conditions'], [{'type': 'neoforge:false'}])
        renamed = out['x/data_maps/m.json']
        self.assertTrue(renamed['replace'])
        self.assertEqual(list(renamed['values']), ['a:new', 'b:new', 'c:keep'])
        found[specs[0]['path']] = [('x.jar', '', json.dumps(out['x/recipe/conditional.json']).encode())]
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': specs[:1]}}):
            with self.assertRaises(AssertionError):
                balance.build_data('probe', found)

    def test_pingpong_stages_use_act_components_and_remove_duplicators(self):
        family = balance.FAMILIES['pingpong']
        by_id = {change['id']: change for change in family['changes']}
        self.assertEqual(by_id['ad_astra:nasa_workbench']['act'], 'V')
        self.assertEqual(by_id['eternal_starlight:orb_of_prophecy']['add'], balance.HZ)
        self.assertEqual(by_id['refinedstorage:controller']['add'], balance.RM)
        self.assertIn('modern_industrialization:electric_age/machine/assembler/replicator', family['removals'])
        self.assertIn('oritech:particle/nether_star', family['removals'])
        # Broken upstream recipes are disabled, Ad Astra's chest loot moves to the 1.21 folder; the only other
        # override is the Gatekeeper's first-win loot.
        self.assertEqual(len([s for s in family['data'] if s['op'] == 'disable']), len(balance.PINGPONG_BROKEN_RECIPES))
        self.assertEqual([s['to'] for s in family['data'] if s['op'] == 'port_loot'],
                         [path.replace('/loot_tables/', '/loot_table/') for path, _ in balance.AD_ASTRA_CHEST_LOOT])
        integrity = ('port_loot', 'alias_loot', 'retarget_template', 'structure_off', 'self_drop')
        self.assertEqual(sum(s['op'] in integrity[1:] for s in family['data']), len(balance.LOOT_INTEGRITY))
        self.assertEqual([s['path'] for s in family['data'] if s['op'] not in ('disable',) + integrity],
                         ['data/eternal_starlight/loot_table/bosses/the_gatekeeper.json'])

    def test_round_four_stages_heliodor_solar_tech_and_psi(self):
        family = balance.FAMILIES['pingpong4']
        by_id = {change['id']: change for change in family['changes']}
        # Playtest of 24 September 2026: plates and coils go by the dozen, so the plate takes the act II
        # alloy, the coil stays native and the coupler moves to the carbon brushes, one per generator.
        self.assertEqual(by_id['create_new_age:shaped/basic_solar_heating_plate']['add'], balance.ALLOY_II)
        self.assertEqual(by_id['create_new_age:shaped/carbon_brushes']['add'], balance.EN)
        self.assertNotIn('create_new_age:shaped/generator_coil', by_id)
        self.assertEqual(balance.UPSTREAM['create_new_age:shaped/generator_coil'], 'create_new_age:shaped/carbon_brushes')
        self.assertEqual(by_id['create_new_age:mechanical_crafting/reactor_rod']['add'], balance.IRONWOOD)
        self.assertNotIn('psi:cad_core_hyperclocked', by_id)
        self.assertEqual(by_id['psi:assembler']['act'], 'III')
        self.assertEqual({change['act'] for change in family['changes']}, {'II', 'III', 'IV'})
        self.assertEqual({spec['op'] for spec in family['data']}, {'copy', 'disable'})

    def test_round_five_compat_removes_large_cells_and_leaves_bridges_upstream(self):
        family = balance.FAMILIES['pingpong5compat']
        # MEGA Cells (Act IV) stays the only route to 1M+ cells; the chunky turtle is off in the server config.
        self.assertEqual(family['changes'], [])
        self.assertEqual({rid for rid in family['removals'] if 'ae_disk_cell' in rid},
                         {f'advancedperipherals:ae_disk_cell_{s}' for s in ('1m', '4m', '16m', '64m', '256m')})
        self.assertIn('advancedperipherals:chunk_controller', family['removals'])
        self.assertEqual(balance.UPSTREAM['ad_astra_giselle_addon:crafting/automation_nasa_workbench'], 'ad_astra:nasa_workbench')
        self.assertEqual(balance.UPSTREAM['advancedperipherals:me_bridge'], 'ae2:network/blocks/controller')
        self.assertEqual(balance.UPSTREAM['advancedperipherals:rs_bridge'], 'refinedstorage:controller')

    def test_copy_keeps_one_owner_of_a_shared_path_and_can_move_the_other(self):
        path = 'data/patchouli/recipe/guide_book.json'
        first = {'type': 'minecraft:crafting_shapeless', 'result': {'id': 'patchouli:guide_book'}, 'n': 1}
        second = {'type': 'psi:trick_crafting', 'output': {'id': 'patchouli:guide_book'}, 'n': 2}
        found = {path: [('alpha-1.jar', '', json.dumps(first).encode()), ('beta-2.jar', '', json.dumps(second).encode())]}
        specs = [balance.copied(path, 'alpha-', '', owners=2),
                 balance.copied(path, 'beta-', '', owners=2, to='data/beta/recipe/book.json')]
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': specs}}):
            out = {k: json.loads(v) for k, v in balance.build_data('probe', found).items()}
        self.assertEqual(out, {'patchouli/recipe/guide_book.json': first, 'beta/recipe/book.json': second})
        # A copy never lands on a path that a pinned JAR already ships.
        taken = dict(found, **{'data/beta/recipe/book.json': [('beta-2.jar', '', b'{}')]})
        for spec, where in ((balance.copied(path, 'alpha-', '', owners=3), found),
                            (balance.copied(path, 'gamma-', '', owners=2), found),
                            (balance.copied(path, 'beta-', '', owners=2, to='data/beta/recipe/book.json'), taken)):
            with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
                with self.assertRaises(AssertionError):
                    balance.build_data('probe', where)

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_every_compact_machines_room_template_loads(self):
        # Compact Machines ships its room templates in an optional datapack no world enables by itself; the
        # industrial family loads all of it, so a template the mod adds later fails here.
        shipped = {path[len(balance.CM_BASIC):] for path in pinned_data()
                   if path.startswith(balance.CM_BASIC + 'data/')}
        copies = {spec['to'] for spec in balance.FAMILIES['industrial']['data'] if spec['op'] == 'copy'}
        self.assertEqual(shipped, set(balance.CM_ROOM_FILES))
        self.assertLessEqual(shipped, copies)
        self.assertEqual(len(shipped), 24)

    def test_loop_check_catches_uncrafting_and_gate_leaks(self):
        lum = balance.LUMINOSITY['arcane']
        ingot = balance.created_shaped('t:ingot', 't:ingot', ['LXL', 'XXX', 'LXL'],
                                       {'L': {'item': lum}, 'X': {'item': 't:metal'}}, '')
        gear = balance.created_smithing('entrelumen:luminous_sword', 'minecraft:netherite_sword', '')
        gear = dict(gear, addition={'item': 't:ingot'})
        clean = balance.find_loops([ingot, gear], [], {}, {lum})
        self.assertEqual(clean, [])
        uncraft = {'type': 'x:recycle', 'ingredient': {'tag': 'x:swords'}, 'result': {'id': 't:ingot'}}
        loops = balance.find_loops([ingot, gear], [uncraft], {'x:swords': {'entrelumen:luminous_sword'}}, {lum})
        self.assertTrue(any(c == 'entrelumen:luminous_sword' and 'turns it back' in reason for c, reason in loops), loops)
        direct = {'type': 'x:melt', 'ingredient': {'item': 't:ingot'}, 'result': {'id': 't:metal'}}
        self.assertTrue(any(c == 't:ingot' for c, _ in balance.find_loops([ingot, gear], [direct], {}, {lum})))
        leak = {'type': 'x:make', 'ingredient': {'item': 't:metal'}, 'result': {'id': lum}}
        self.assertTrue(any(c == lum for c, _ in balance.find_loops([ingot, gear], [leak], {}, {lum})))
        ungated = balance.created_shaped('t:free', 't:free', ['XXX', 'XXX', 'XXX'], {'X': {'item': 't:metal'}}, '')
        self.assertTrue(any(c == 't:free' for c, _ in balance.find_loops([ungated], [], {}, {lum})))

    def test_functions_close_keystones_and_give_members_the_act_material(self):
        family = balance.FAMILIES['functions']
        for change in family['changes']:
            if 'function' not in change:
                continue
            component, act, _ = balance.FUNCTIONS[change['function']]
            self.assertEqual(change['act'], act, change['id'])
            if component == 'luminosity':
                self.assertIn(change['add'], balance.LUMINOSITY.values(), change['id'])
            else:
                self.assertEqual(change['add'], component, change['id'])
        spec = importlib.util.spec_from_file_location('rftools_under_test', Path(__file__).with_name('generate_rftools_balance.py'))
        rftools = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(rftools)
        gates = {c['id']: c for f in balance.FAMILIES.values() for c in f.get('changes', [])}
        adds = {rid: c['add'] for rid, c in gates.items()}
        adds.update({rid: row[0] for rid, row in rftools.CHANGES.items()})
        for function, keystones in balance.FUNCTION_KEYSTONES.items():
            for keystone in keystones:
                self.assertEqual(adds[keystone], balance.FUNCTIONS[function][0], f'{keystone} is a {function} keystone')
        for function, members in balance.FUNCTION_MEMBERS.items():
            component, act, material = balance.FUNCTIONS[function]
            self.assertEqual(balance.STAGE_MATERIALS[material], act, function)
            for member in members:
                self.assertEqual(adds[member], material, f'{member} takes the act {act} material of {function}')
                if member in gates:
                    self.assertEqual(gates[member]['act'], act, member)
        # Members and keystones never overlap, and native recipes left on purpose are no gate at all.
        keystones = {k for ks in balance.FUNCTION_KEYSTONES.values() for k in ks}
        members = {m for ms in balance.FUNCTION_MEMBERS.values() for m in ms}
        self.assertFalse(keystones & members)
        self.assertFalse(set(balance.UPSTREAM) & set(adds))
        self.assertTrue(set(balance.UPSTREAM.values()) <= set(adds))
        # Gap 1 of reference-packs.md: the AE2 controller matches Refined Storage's.
        self.assertEqual(adds['ae2:network/blocks/controller'], adds['refinedstorage:controller'])
        # Gap 3: the inventory sensor gates something.
        self.assertIn(balance.IS, adds.values())

    def test_mekanism_alloys_and_circuits_only_come_from_the_infuser(self):
        removals = balance.FAMILIES['functions']['removals']
        self.assertEqual(removals, balance.MEKANISM_BYPASS)
        self.assertEqual(len([r for r in removals if 'foundry/alloy' in r]), 3)
        self.assertEqual(len([r for r in removals if 'atomicforge' in r]), 4)
        for material, act in balance.STAGE_MATERIALS.items():
            if material.startswith('mekanism:alloy_'):
                self.assertIn(act, ('II', 'III', 'V'))

    def test_paired_cells_keep_the_drawing_and_can_be_reversed(self):
        shelf = {'type': 'minecraft:crafting_shaped', 'pattern': [' T ', 'SBS', 'SCS'],
                 'key': {'T': {'item': 'x:tendril'}, 'S': {'item': 'x:sculk'}, 'B': {'item': 'x:deep'},
                         'C': {'item': 'x:catalyst'}}, 'result': {'id': 'x:shelf', 'count': 1}}
        drawn = balance.transform(balance.paired('x:shelf', [(0, 0), (0, 2)], None, balance.ZANITE, 'IV', ''), shelf)
        self.assertEqual(drawn['pattern'], ['ZTZ', 'SBS', 'SCS'])
        self.assertTrue(balance.symmetric(drawn))
        with self.assertRaises(AssertionError):  # a single cell off the axis breaks the native symmetry
            balance.transform(balance.shaped('x:shelf', 0, 0, None, balance.ZANITE, 'IV', ''), shelf)
        with self.assertRaises(AssertionError):  # both cells must hold the same native ingredient
            balance.transform(balance.paired('x:shelf', [(1, 0), (0, 2)], None, balance.ZANITE, 'IV', ''), shelf)

    def test_recipe_design_rules_pass(self):
        spec = importlib.util.spec_from_file_location('design_under_test', Path(__file__).with_name('check_recipe_design.py'))
        design = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(design)
        tree = design.Tree()
        self.assertEqual(design.check(design.load(tree), tree), [])

    def test_no_gate_touches_what_makes_the_components(self):
        sources, _ = balance.component_sources()
        self.assertIn('mekanism:metallurgic_infuser', sources[balance.CF])
        protected_paths = {p.split(':', 1)[1] for p in balance.PROTECTED}
        for family in balance.FAMILIES.values():
            for change in family.get('changes', []):
                if change['id'] == 'mekanism:metallurgic_infuser':
                    self.assertIn(('mekanism:metallurgic_infuser', change['add']), balance.BOOTSTRAP)
                    continue
                self.assertNotIn(change['id'].split(':', 1)[1], protected_paths, change['id'])
        infuser = next(c for c in balance.FAMILIES['functions']['changes'] if c['id'] == 'mekanism:metallurgic_infuser')
        balance.check_function_gate(infuser, 'mekanism:metallurgic_infuser', sources)
        # The bootstrap is only allowed while the story hands out two frames.
        with unittest.mock.patch.object(balance, 'story_grants', return_value=1):
            with self.assertRaises(AssertionError):
                balance.check_function_gate(infuser, 'mekanism:metallurgic_infuser', sources)
        with self.assertRaises(AssertionError):
            balance.check_function_gate(dict(infuser, add=balance.PR, act='III', function='jetpack'),
                                        'mekanism:metallurgic_infuser', sources)
        # Gating an input of a component with that component is a loop.
        loop = balance.shaped('ae2:x', 0, 0, None, balance.RM, 'III', '')
        with self.assertRaises(AssertionError):
            balance.check_function_gate(loop, 'ae2:logic_processor', sources)

    def test_drawn_gates_take_the_centre_or_axis_and_keep_symmetry(self):
        # Elias's playtest of 24 September 2026: the 3x3 is a drawing.
        machine = {'type': 'minecraft:crafting_shaped', 'pattern': ['I#I', 'ROR', 'I#I'],
                   'key': {'I': {'item': 'x:iron'}, '#': {'item': 'x:furnace'}, 'R': {'item': 'x:red'},
                           'O': {'item': 'x:core'}}, 'result': {'id': 'x:machine', 'count': 1}}
        core = balance.function_gate('mekanism_entry', 'x:machine', 1, 1, {'item': 'x:core'}, '')
        drawn = balance.transform(core, machine)
        self.assertEqual(drawn['pattern'], ['I#I', 'RZR', 'I#I'])
        self.assertNotIn('O', drawn['key'])  # a shaped key may not keep an unused symbol
        self.assertTrue(balance.symmetric(drawn))
        with self.assertRaises(AssertionError):  # off the axis: the drawing loses its symmetry
            balance.transform(balance.function_gate('mekanism_entry', 'x:machine', 0, 1, {'item': 'x:furnace'}, '')
                              | {'col': 0, 'expect': {'item': 'x:iron'}}, machine)
        with self.assertRaises(AssertionError):  # only drawn gates may replace a single ingredient
            balance.transform(balance.shaped('x:machine', 1, 1, {'item': 'x:core'}, balance.CF, 'II', ''), machine)
        script = (ROOT / 'pack/kubejs/server_scripts/entrelumen_functions_balance.js').read_text(encoding='utf-8')
        rows = json.loads(re.search(r'^const entrelumenFunctionsRows = (.*);$', script, re.M).group(1))
        shaped_rows = [r for r in rows if 'pattern' in r['json']]
        self.assertEqual(len(rows), len(balance.FAMILIES['functions']['changes']))
        for row in shaped_rows:
            self.assertTrue(balance.symmetric(row['json']), row['id'])
            where = [(i, line.index('Z')) for i, line in enumerate(row['json']['pattern']) if 'Z' in line]
            self.assertEqual(len(where), 1, row['id'])
            self.assertEqual(where[0][1], 1, f"{row['id']}: the component is not on the vertical axis")
        # Fewer, key recipes per component: the batch adds at most two gates to any existing component.
        added = {}
        for change in balance.FAMILIES['functions']['changes']:
            if change['add'] not in balance.LUMINOSITY.values():
                added[change['add']] = added.get(change['add'], 0) + 1
        self.assertLessEqual(max(added.values()), 2, added)

    def test_top_armor_takes_one_luminosity_per_piece_in_act_six(self):
        armor = [c for c in balance.FAMILIES['functions']['changes'] if c.get('function') == 'top_armor']
        self.assertEqual(len(armor), 12)
        self.assertEqual({c['add'] for c in armor if c['id'].startswith('mekanism:mekasuit_')}, {balance.LUMINOSITY['engineering']})
        self.assertEqual({c['add'] for c in armor if c['id'].startswith('advanced_ae:')}, {balance.LUMINOSITY['logistics']})
        self.assertEqual({c['add'] for c in armor if c['id'].startswith('modern_industrialization:')}, {balance.LUMINOSITY['habitation']})
        self.assertTrue(all(c['act'] == 'VI' for c in armor))
        packer = {'type': 'modern_industrialization:packer', 'item_inputs': [{'amount': 1, 'item': 'minecraft:netherite_boots'},
                  {'amount': 1, 'item': 'modern_industrialization:quantum_upgrade'}],
                  'item_outputs': [{'amount': 1, 'item': 'modern_industrialization:quantum_boots'}]}
        change = next(c for c in armor if c['id'].endswith('quantum/boots'))
        out = balance.transform(change, packer)
        self.assertEqual(out['item_inputs'][-1], {'amount': 1, 'item': balance.LUMINOSITY['habitation']})
        self.assertEqual(balance.outputs(out), {'modern_industrialization:quantum_boots'})
        full = dict(packer, item_inputs=packer['item_inputs'] + [{'amount': 1, 'item': 'x:third'}])
        with self.assertRaises(AssertionError):
            balance.transform(change, full)

    def test_vein_resonator_tiers_chain_by_act(self):
        # Elias's nerf of 25 September 2026: four tiers, each a fork with the previous tier in its centre.
        additions = balance.FAMILIES['functions']['additions']
        self.assertEqual([a['id'] for a in additions], [balance.resonator(t) for t in range(1, 5)])
        self.assertEqual([a['act'] for a in additions], ['I', 'III', 'V', 'VI'])
        self.assertEqual([a['pattern'] for a in additions],
                         [['G G', 'GDG', ' G '], ['E E', 'ERE', ' N '], ['X X', 'XRX', ' S '], [' A ', ' R ', ' B ']])
        expected = [
            {'minecraft:gold_ingot': 5, 'minecraft:diamond': 1},
            {'minecraft:emerald_block': 4, balance.resonator(1): 1, 'minecraft:netherite_ingot': 1},
            {'minecraft:end_stone': 4, balance.resonator(2): 1, 'minecraft:nether_star': 1},
            {balance.LUMINOSITY['exploration']: 1, balance.resonator(3): 1, balance.LUMINOSITY['engineering']: 1},
        ]
        for tier, addition in enumerate(additions, 1):
            inputs = [addition['key'][c]['item'] for row in addition['pattern'] for c in row if c != ' ']
            for row in addition['pattern']:
                self.assertEqual(row, row[::-1], addition['id'])
            self.assertEqual(dict(Counter(inputs)), expected[tier - 1], addition['id'])
            if tier > 1:
                self.assertEqual(addition['key'][addition['pattern'][1][1]]['item'], balance.resonator(tier - 1))
            # No act component any more: the resonators close no milestone of a component.
            self.assertFalse([i for i in inputs if i in balance.component_sources()[1]], addition['id'])
        self.assertIsNone(balance.FUNCTIONS['area_mining'][0])

    def test_frame_is_infused_and_seeded_by_the_story(self):
        spec = importlib.util.spec_from_file_location('integration_under_test', Path(__file__).with_name('generate_integration_recipes.py'))
        integration = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(integration)
        design = json.loads((ROOT / 'content/integration-design.json').read_text(encoding='utf-8'))
        rows = {row['id']: row['json'] for row in integration.recipes_from(design)}
        frame = rows['entrelumen:integration/precision_bench']
        self.assertEqual(frame['type'], 'mekanism:metallurgic_infusing')
        self.assertEqual(frame['item_input'], {'count': 1, 'item': 'entrelumen:raw_lens'})
        self.assertEqual(frame['output'], {'count': 1, 'id': balance.CF})
        self.assertEqual(sum(r['type'] == 'minecraft:crafting_shaped' for r in rows.values()), 15)
        self.assertFalse(any(balance.CF == r.get('result', {}).get('id') for r in rows.values()))
        with unittest.mock.patch.object(integration, 'STORY_FRAMES', 3):
            with self.assertRaises(ValueError):
                integration.recipes_from(design)
        crafted = json.loads(json.dumps(design))
        next(p for p in crafted['projects'] if p['output']['id'] == balance.CF)['recipe']['type'] = 'minecraft:crafting_shapeless'
        with self.assertRaises(ValueError):
            integration.recipes_from(crafted)

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_scripts_are_current_against_pinned_jars(self):
        for name, family in balance.FAMILIES.items():
            with self.subTest(family=name):
                rows, removals, used, _ = balance.build(name)
                if family.get('creations'):
                    recipes, _, _ = balance.load_recipes()
                    used = dict(sorted({**used, **balance.check_creations(name, recipes)}.items()))
                expected = balance.render(name, rows, removals, used)
                self.assertEqual((ROOT / 'pack/kubejs/server_scripts' / family['script']).read_text(encoding='utf-8'), expected)
                for relative, text in {**balance.build_data(name), **balance.build_additions(name),
                                       **balance.creation_files(name)}.items():
                    self.assertEqual((balance.PACK_DATA / relative).read_text(encoding='utf-8'), text)


@functools.lru_cache(maxsize=1)
def pinned_data():
    return balance.data_files()


def upstream(path, data, jar='Mod-x.jar'):
    return {path: [(jar, '', json.dumps(data).encode())]}


AUGMENT = {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': 'apothic_enchanting'}],
           'type': 'apotheosis:attribute',
           'modifier': {'attribute': 'apothic_enchanting:max_eterna', 'operation': 'add_value', 'value': -70.0},
           'modifier_id': 'apotheosis:haven/max_eterna', 'sort_index': 90, 'target': 'players', 'tier': 'haven'}
LADDER = {'base': 100.0, 'min': 0.0, 'max': 100.0,
          'tiers': {'haven': {'ceiling': 45.0}, 'frontier': {'ceiling': 75.0}, 'ascent': {'ceiling': 90.0},
                    'summit': {'ceiling': 100.0}, 'pinnacle': {'ceiling': 100.0}}}


class EternaCeilingTest(unittest.TestCase):
    """World Tier Eterna ceilings (docs/design/apotheosis-family.md#world-tier-eterna-ceilings)."""

    def test_ring_sums_like_the_table_builder(self):
        self.assertEqual(balance.best_eterna([]), 0.0)
        self.assertEqual(balance.best_eterna([(3.0, 45.0)]), 45.0)  # fifteen hellshelves
        self.assertEqual(balance.best_eterna([(3.0, 100.0)]), 96.0)  # 32 blocks never reach 100
        self.assertEqual(balance.best_eterna([(5.0, 90.0), (20.0, 100.0)]), 100.0)  # one draconic on top of 90
        self.assertEqual(balance.best_eterna([(15.0, 60.0), (1.0, 40.0)]), 60.0)  # each bucket clamps to its max

    def test_haven_counts_crafted_shelves_and_infusions_open_with_frontier(self):
        stats = {'x:hell': (3.0, 45.0), 'x:infused': (5.0, 60.0), 'x:deep': (5.0, 75.0), 'x:end': (5.0, 90.0)}
        recipes = [('x:hell', [[1]], 0.0), ('x:infused', [['x:hell']], 45.0), ('x:deep', [['x:infused']], 60.0),
                   ('x:end', [['x:deep'], [4]], 0.0)]
        acts = {'haven': (1, 2), 'frontier': (3, 3), 'ascent': (4, 4), 'summit': (5, 5), 'pinnacle': (6, 6)}
        tiers, first = balance.walk_ladder(recipes, stats, {}, acts, 0.0, 100.0)
        self.assertEqual({t: r['ceiling'] for t, r in tiers.items()},
                         {'haven': 45.0, 'frontier': 75.0, 'ascent': 90.0, 'summit': 90.0, 'pinnacle': 90.0})
        self.assertEqual(first, {'x:hell': 'haven', 'x:infused': 'frontier', 'x:deep': 'frontier', 'x:end': 'ascent'})
        # The infusion runs at Haven's own ceiling: counted there, it would chain the ladder into act I.
        with unittest.mock.patch.object(balance, 'INFUSION_OPENS', 'haven'):
            loose, _ = balance.walk_ladder(recipes, stats, {}, acts, 0.0, 100.0)
        self.assertEqual(loose['haven']['ceiling'], 75.0)

    def test_tiers_read_their_acts_from_the_companion(self):
        self.assertEqual(balance.tier_acts(), {'haven': (1, 2), 'frontier': (3, 3), 'ascent': (4, 4),
                                               'summit': (5, 5), 'pinnacle': (6, 6)})

    def test_augment_override_keeps_its_shape_and_disables_at_the_base(self):
        spec, model = balance.eterna_ceiling('haven'), json.loads(json.dumps(LADDER))
        source = [('Apotheosis-x.jar', '', json.dumps(AUGMENT).encode())]
        out = json.loads(balance.eterna_override(spec, source, model))
        self.assertEqual(out['modifier']['value'], -55.0)
        self.assertEqual({k: v for k, v in out.items() if k != 'modifier'}, {k: v for k, v in AUGMENT.items() if k != 'modifier'})
        model['tiers']['haven']['ceiling'] = 100.0
        off = json.loads(balance.eterna_override(spec, source, model))
        self.assertEqual(off['neoforge:conditions'], [{'type': 'neoforge:false'}] + AUGMENT['neoforge:conditions'])
        self.assertEqual(off['modifier'], AUGMENT['modifier'])
        self.assertIsNone(balance.eterna_override(balance.eterna_ceiling('pinnacle'), [], model))
        model['tiers']['pinnacle']['ceiling'] = 90.0
        with self.assertRaises(AssertionError):  # a tier without an upstream augment would need a new file
            balance.eterna_override(balance.eterna_ceiling('pinnacle'), [], model)
        for drift in ({'target': 'monsters'}, {'extra': 1}, {'neoforge:conditions': []}):
            with self.subTest(drift=drift), self.assertRaises(AssertionError):
                balance.eterna_override(spec, [('Apotheosis-x.jar', '', json.dumps(dict(AUGMENT, **drift)).encode())], model)

    def test_every_pinned_max_eterna_augment_needs_a_spec(self):
        found = upstream('data/apotheosis/tier_augments/haven/max_eterna.json', AUGMENT, 'Apotheosis-x.jar')
        probe = {'probe': {'data': [balance.eterna_ceiling('haven')]}}
        with unittest.mock.patch.object(balance, 'eterna_ceilings', return_value=LADDER), \
                unittest.mock.patch.dict(balance.FAMILIES, probe), unittest.mock.patch.dict(balance.ETERNA_REPORT):
            self.assertEqual(len(balance.build_data('probe', found)), 1)
            found.update(upstream('data/other/tier_augments/x.json', AUGMENT))
            with self.assertRaises(AssertionError):
                balance.build_data('probe', found)

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_ceilings_follow_the_staging_of_the_pinned_jars(self):
        found = pinned_data()
        model = balance.eterna_ceilings(found)
        self.assertEqual((model['base'], model['min'], model['max']), (100.0, 0.0, 100.0))
        self.assertEqual({t: r['ceiling'] for t, r in model['tiers'].items()},
                         {'haven': 45.0, 'frontier': 75.0, 'ascent': 90.0, 'summit': 100.0, 'pinnacle': 100.0})
        self.assertEqual(sorted(balance.eterna_augment_paths(found)),
                         [f'data/apotheosis/tier_augments/{t}/max_eterna.json' for t in ('ascent', 'frontier', 'haven', 'summit')])
        # Nothing is typed by hand: an act material that moves later moves the ceiling with it.
        with unittest.mock.patch.dict(balance.STAGE_MATERIALS, {balance.ZANITE: 'V'}):
            self.assertEqual(balance.eterna_ceilings(found)['tiers']['ascent']['ceiling'], 80.0)


ORB_RECIPE = {'type': 'minecraft:crafting_shaped', 'category': 'misc',
              'key': {'C': {'item': balance.SHARD}, 'G': {'item': 'minecraft:glass'}},
              'pattern': ['GCG', 'CCC', 'GCG'], 'result': {'count': 1, 'id': balance.ORB}}
GATEKEEPER = 'data/eternal_starlight/loot_table/bosses/the_gatekeeper.json'
GATEKEEPER_LOOT = {'type': 'eternal_starlight:boss', 'pools': [
    {'rolls': 1.0, 'entries': [{'type': 'minecraft:item', 'name': 'eternal_starlight:book'}]},
    {'rolls': 1.0, 'entries': [{'type': 'minecraft:item', 'name': balance.ORB, 'conditions': [
        {'condition': 'eternal_starlight:boss_challenge_count', 'boss_id': 'eternal_starlight:the_gatekeeper',
         'max_inclusive': 1}]}]}]}


BOSS_TAG = 'c:bosses'
# NeoForge 21.1.249 defines c:bosses as the Ender Dragon, the Wither and an optional #forge:bosses (no pinned
# JAR ships that one); vanilla has no such tag.
NEOFORGE_BOSSES = ('minecraft:ender_dragon', 'minecraft:wither', '#forge:bosses')
# Quest bosses (role "boss" with a kill task) that stay out of c:bosses. None shows a boss bar (no
# ServerBossEvent in its class) and no mod tags it as a boss. Elias decided on 29 September 2026: only
# Occultism's unbound Afrit and Marid join c:bosses (boss_drops family); these stay allowed to machines,
# each for the same reason, «Elias 29/9: farmable». An entry no quest needs any more fails too.
FARMABLE = 'Elias 29/9: farmable'
UNTAGGED_QUEST_BOSSES = {
    'minecraft:warden': f'{FARMABLE} (vanilla, no boss bar; neither vanilla, NeoForge nor a pinned mod tags it)',
    'evilcraft:werewolf': f'{FARMABLE} (a villager turned on full-moon nights; EvilCraft already keeps its spirit out of boxes)',
    'immersiveengineering:fusilier': f"{FARMABLE} (IE's raid variant of a pillager)",
    'immersiveengineering:commando': f"{FARMABLE} (IE's raid variant of a vindicator, evoker or ravager)",
    'immersiveengineering:bulwark': f"{FARMABLE} (IE's raid variant of a vindicator, evoker or ravager)",
    'irons_spellbooks:ice_spider': f"{FARMABLE} (a snow hunter; Iron's Spells tags only the Dead King and Tyros)",
    'eternal_starlight:permafrost': f"{FARMABLE} (an ESBoss without a boss bar; Eternal Starlight's own c:bosses leaves it out)",
}


def entity_tags(found):
    """Every entity type tag as the pack loads it: the pinned JARs' files, the companion's and the pack's (tags
    merge across datapacks), plus NeoForge's own c:bosses. Values keep their #references."""
    tags = {BOSS_TAG: list(NEOFORGE_BOSSES)}

    def add(tag, data):
        members = tags.setdefault(tag, [])
        for value in data.get('values', []):
            value = value['id'] if isinstance(value, dict) else value
            if value not in members:
                members.append(value)

    for path, owners in found.items():
        match = re.fullmatch(r'data/([^/]+)/tags/entity_type/(.+)\.json', path)
        if match:
            for _, _, raw in owners:
                add(f'{match.group(1)}:{match.group(2)}', json.loads(raw))
    for base in (balance.COMPANION_DATA, balance.PACK_DATA):
        for path in base.glob('*/tags/entity_type/**/*.json'):
            namespace, rest = path.relative_to(base).as_posix().split('/tags/entity_type/', 1)
            add(f"{namespace}:{rest.removesuffix('.json')}", json.loads(path.read_text(encoding='utf-8')))
    return tags


def tag_members(tags, tag, seen=frozenset()):
    """The entity IDs of a tag, nested #tags resolved."""
    out = set()
    for value in tags.get(tag, []):
        if value.startswith('#'):
            if value[1:] not in seen:
                out |= tag_members(tags, value[1:], seen | {tag})
        else:
            out.add(value)
    return out


def quest_boss_kills():
    """{entity: [quest keys]} for every quest with the boss role and a kill task, in sectors and guides."""
    kills = {}
    for folder in ('content/sectors', 'content/guides'):
        for path in sorted((ROOT / folder).glob('*.json')):
            for quest in json.loads(path.read_text(encoding='utf-8')).get('quests', []):
                if quest.get('role') != 'boss':
                    continue
                for task in quest.get('tasks') or [quest.get('task') or {}]:
                    if task.get('type') == 'kill':
                        kills.setdefault(task['entity'], []).append(quest['key'])
    return kills


class BossDropsTest(unittest.TestCase):
    """The balance batch of 27 September 2026 (docs/design/mod-pingpong.md#botines-de-jefe)."""

    def test_boss_drops_close_the_star_loops_the_audit_found(self):
        family = balance.FAMILIES['boss_drops']
        self.assertEqual(set(family['removals']), {'theurgy:incubation/nether_star', 'theurgy:incubation/dragon_egg',
                                                   'rftoolsutility:minecraft_wither', 'rftoolsutility:minecraft_ender_dragon'})
        tags = [s for s in family['data'] if s['op'] == 'tag_values']
        self.assertEqual([(s['path'], s['values']) for s in tags],
                         [('data/c/tags/entity_type/bosses.json',
                           ['deeperdarker:stalker', 'draconicevolution:draconic_guardian', 'friendsandfoes:wildfire',
                            'occultism:afrit_wild', 'occultism:marid_unbound']),
                          ('data/oritech/tags/entity_type/spawner_blacklist.json', ['#c:bosses'])])
        queen = [s for s in family['data'] if s['op'] == 'remove_values']
        self.assertEqual(len(queen), 2)
        self.assertTrue(all(v['item']['id'] == balance.NETHER_STAR for s in queen for v in s['values']))
        # No generated family recreates a star: every addition and creation leaves them to the Wither.
        for name, other in balance.FAMILIES.items():
            for recipe in other.get('additions', []) + other.get('creations', []):
                self.assertNotEqual(recipe.get('output', recipe['id']), balance.NETHER_STAR, name)

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_no_rftools_spawner_recipe_spawns_a_boss(self):
        # NeoForge's c:bosses holds the Wither and the Ender Dragon; pinned mods may add their own bosses.
        found = pinned_data()
        bosses = {'minecraft:wither', 'minecraft:ender_dragon'}
        for _, _, raw in found.get('data/c/tags/entity_type/bosses.json', []):
            bosses |= {v['id'] if isinstance(v, dict) else v for v in json.loads(raw)['values']}
        spawns = {}
        for path, owners in found.items():
            if path.startswith('data/rftoolsutility/recipe/'):
                recipe = json.loads(owners[0][2])
                if recipe.get('type') == 'rftoolsutility:spawner':
                    spawns[balance.recipe_id_from_name(path)] = recipe['entity']
        boss_recipes = {rid for rid, entity in spawns.items() if entity in bosses}
        self.assertEqual(boss_recipes, {'rftoolsutility:minecraft_wither', 'rftoolsutility:minecraft_ender_dragon'})
        self.assertLessEqual(boss_recipes, set(balance.FAMILIES['boss_drops']['removals']))
        self.assertIn('rftoolsutility:minecraft_zombie', spawns)

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_every_quest_boss_is_a_tagged_boss(self):
        # The capture tools and spawners that keep bosses out (Ender IO, Industrial Foregoing, Apothic Spawners,
        # Carry On, Oritech) read c:bosses: a boss the quests send players after must be in it, or be listed.
        tags = entity_tags(pinned_data())
        bosses = tag_members(tags, BOSS_TAG)

        def tagged(entity):
            if entity.startswith('#'):
                return entity[1:] == BOSS_TAG or bool(tag_members(tags, entity[1:])) and tag_members(tags, entity[1:]) <= bosses
            return entity in bosses

        kills = quest_boss_kills()
        self.assertIn('friendsandfoes:wildfire', kills)
        # Elias 29/9: Occultism's unbound Afrit and Marid are bosses now (the bound ones are not).
        self.assertLessEqual({'occultism:afrit_wild', 'occultism:marid_unbound'}, set(kills) & bosses)
        self.assertFalse({'occultism:afrit', 'occultism:marid'} & bosses)
        self.assertTrue(all(reason.startswith(FARMABLE) for reason in UNTAGGED_QUEST_BOSSES.values()))
        untagged = {entity: keys for entity, keys in kills.items() if not tagged(entity)}
        self.assertEqual(sorted(untagged), sorted(UNTAGGED_QUEST_BOSSES), untagged)
        self.assertTrue(all(reason.strip() for reason in UNTAGGED_QUEST_BOSSES.values()))

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_no_boss_spirit_can_be_boxed(self):
        # EvilCraft takes no tags: its spirit blacklist (regular expressions) names every boss of c:bosses.
        config = (ROOT / 'pack/config/evilcraft-common.toml').read_text(encoding='utf-8')
        (listed,) = re.findall(r'^\s*entityBlacklist = (\[.*\])$', config, re.M)
        patterns = json.loads(listed)
        bosses = tag_members(entity_tags(pinned_data()), BOSS_TAG)
        self.assertLessEqual({'friendsandfoes:wildfire', 'deeperdarker:stalker', 'draconicevolution:draconic_guardian',
                              'occultism:afrit_wild', 'occultism:marid_unbound'}, bosses)
        missing = sorted(b for b in bosses if not any(re.fullmatch(p, b) for p in patterns))
        self.assertEqual(missing, [])

    def test_swapped_loot_pays_exactly_the_staged_recipe(self):
        spec = balance.swapped_loot(GATEKEEPER, balance.ORB, balance.SHARD, '', count=4, staged=balance.ORB)
        found = {**upstream(GATEKEEPER, GATEKEEPER_LOOT), **upstream('data/eternal_starlight/recipe/orb_of_prophecy.json', ORB_RECIPE)}
        self.assertEqual(balance.staged_uses(balance.ORB, balance.SHARD, ORB_RECIPE), 4)
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = json.loads(balance.build_data('probe', found)[GATEKEEPER[len('data/'):]])
            entry = out['pools'][1]['entries'][0]
            self.assertEqual(entry['name'], balance.SHARD)
            self.assertEqual(entry['functions'], [{'function': 'minecraft:set_count', 'count': 4}])
            self.assertEqual(entry['conditions'], GATEKEEPER_LOOT['pools'][1]['entries'][0]['conditions'])
            self.assertEqual(out['pools'][0], GATEKEEPER_LOOT['pools'][0])
        twice = json.loads(json.dumps(GATEKEEPER_LOOT))
        twice['pools'][0]['entries'].append({'type': 'minecraft:item', 'name': balance.ORB})
        for bad_spec, bad_found in ((dict(spec, count=5), found), (spec, {**found, **upstream(GATEKEEPER, twice)})):
            with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [bad_spec]}}), self.assertRaises(AssertionError):
                balance.build_data('probe', bad_found)

    def test_tag_values_merge_and_refuse_a_value_already_tagged(self):
        path = 'data/oritech/tags/entity_type/spawner_blacklist.json'
        spec = balance.tagged(path, ['#c:bosses'], '')
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = balance.build_data('probe', upstream(path, {'values': ['minecraft:ender_dragon']}))
            self.assertEqual(json.loads(out[path[len('data/'):]]), {'replace': False, 'values': ['#c:bosses']})
            with self.assertRaises(AssertionError):
                balance.build_data('probe', upstream(path, {'values': ['#c:bosses']}))

    def test_occult_lead_needs_an_ascent_table(self):
        path = 'data/apothic_enchanting/recipe/infusion/occult_ender_lead.json'
        native = {'type': 'apothic_enchanting:infusion', 'input': {'item': 'apothic_enchanting:ender_lead'},
                  'requirements': {'eterna': 75, 'quanta': 85, 'arcana': 60},
                  'max_requirements': {'eterna': -1, 'quanta': -1, 'arcana': 85},
                  'result': {'id': 'apothic_enchanting:occult_ender_lead', 'count': 1}}
        with unittest.mock.patch.object(balance, 'eterna_ceilings', return_value=LADDER):
            for eterna, fits in ((80, True), (75, False), (95, False)):
                spec = balance.infusion_eterna(path, eterna, 'ascent', '')
                with self.subTest(eterna=eterna), unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
                    if fits:
                        out = json.loads(balance.build_data('probe', upstream(path, native))[path[len('data/'):]])
                        self.assertEqual(out['requirements'], {'eterna': 80, 'quanta': 85, 'arcana': 60})
                        self.assertEqual(out['max_requirements'], native['max_requirements'])
                    else:
                        with self.assertRaises(AssertionError):
                            balance.build_data('probe', upstream(path, native))
        (gate,) = [s for s in balance.FAMILIES['apotheosis']['data'] if s['op'] == 'infusion_eterna']
        self.assertEqual((gate['path'], gate['eterna'], gate['tier']), (path, 80, 'ascent'))

    def test_the_gatekeeper_trades_the_orbs_shards_for_the_same_coin(self):
        (swap,) = balance.FAMILIES['pingpong']['offer_swaps']
        self.assertEqual((swap['entity'], swap['old'], swap['new'], swap['count']),
                         ('eternal_starlight:the_gatekeeper', balance.ORB, balance.SHARD, 4))
        (loot,) = [s for s in balance.FAMILIES['pingpong']['data'] if s['op'] == 'swap_loot']
        self.assertEqual((loot['old'], loot['new'], loot['count']), (balance.ORB, balance.SHARD, swap['count']))
        script = (ROOT / 'pack/kubejs/server_scripts/entrelumen_pingpong_balance.js').read_text(encoding='utf-8')
        self.assertIn('const entrelumenPingpongOfferSwaps = [{"entity":"eternal_starlight:the_gatekeeper",'
                      f'"from":"{balance.ORB}","to":"{balance.SHARD}","count":4}}];', script)
        self.assertIn("EntityEvents.spawned(swap.entity", script)
        self.assertEqual(balance.render_offer_swaps({'tag': 'X'}, 'p'), '')


class PortedLootTest(unittest.TestCase):
    """Loot and structures that did not load in 1.21: ports, aliases, a retargeted pool and an empty structure
    turned off (docs/design/mod-pingpong.md)."""

    def test_ported_loot_moves_a_120_table_and_converts_only_what_121_reads_differently(self):
        path = 'data/x/loot_tables/chests/a.json'
        book = {'type': 'minecraft:item', 'name': 'minecraft:book', 'weight': 5,
                'functions': [{'function': 'minecraft:enchant_randomly'}]}
        native = {'_comment': 'legacy', 'type': 'minecraft:chest', 'pools': [{
            'rolls': {'min': 1, 'max': 3}, 'bonus_rolls': 0.0,
            'entries': [{'type': 'item', 'name': 'x:gem', 'weight': 2,
                         'functions': [{'function': 'set_count', 'count': {'type': 'minecraft:uniform', 'min': 1.0, 'max': 2.0},
                                        'add': False}]}, book]}]}
        spec = balance.ported_loot(path, '')
        self.assertEqual(spec['to'], 'data/x/loot_table/chests/a.json')
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = balance.build_data('probe', upstream(path, native))
            self.assertEqual(list(out), ['x/loot_table/chests/a.json'])
            ported = json.loads(out['x/loot_table/chests/a.json'])
            expected = json.loads(json.dumps(native))
            expected['pools'][0]['entries'][1]['functions'][0]['options'] = '#minecraft:on_random_loot'
            self.assertEqual(ported, expected)
            # Upstream now ships the 1.21 path: the port must go instead of overriding it.
            with self.assertRaises(AssertionError):
                balance.build_data('probe', {**upstream(path, native), **upstream(spec['to'], native)})
            # 1.20 forms without a written conversion fail instead of loading with another meaning.
            for broken in (dict(book, functions=[{'function': 'minecraft:set_nbt', 'tag': '{Potion:"minecraft:luck"}'}]),
                           dict(book, functions=[{'function': 'minecraft:enchant_randomly', 'enchantments': ['minecraft:mending']}]),
                           dict(book, conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.5}]),
                           dict(book, type='minecraft:loot_table')):
                table = json.loads(json.dumps(native))
                table['pools'][0]['entries'][1] = broken
                with self.subTest(broken=broken), self.assertRaises(AssertionError):
                    balance.build_data('probe', upstream(path, table))
        with self.assertRaises(AssertionError):
            balance.ported_loot('data/x/loot_table/chests/a.json', '')

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_ad_astra_ports_are_the_tables_its_structure_chests_name(self):
        spec = importlib.util.spec_from_file_location('loot_check_for_family', Path(__file__).with_name('check_loot_tables.py'))
        loot = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(loot)
        found = pinned_data()
        (jar,) = [path for name, path in loot.pinned_jars().items() if name.startswith('adastra-')]
        named = set(loot.structure_loot_tables(jar))
        chests = {path for path, _ in balance.AD_ASTRA_CHEST_LOOT}
        ports = [s for s in balance.FAMILIES['pingpong']['data'] if s['op'] == 'port_loot' and s['path'] in chests]
        ids = {re.sub(r'^data/([^/]+)/loot_table/(.+)\.json$', r'\1:\2', s['to']) for s in ports}
        self.assertEqual(ids, named)
        self.assertTrue(all(len(found[s['path']]) == 1 and s['to'] not in found for s in ports))

    @unittest.skipUnless(lock_available(), 'Pinned dependency JARs are not available on this machine')
    def test_ports_and_aliases_add_no_act_material(self):
        # Vanilla targets are left out: vanilla tables hold no mod item.
        found = pinned_data()
        tables = []
        for spec in balance.FAMILIES['pingpong']['data']:
            if spec['op'] == 'port_loot':
                tables.append(json.loads((balance.PACK_DATA / spec['to'][len('data/'):]).read_text(encoding='utf-8')))
            elif spec['op'] == 'alias_loot':
                namespace, rest = spec['target'].split(':', 1)
                owners = found.get(f'data/{namespace}/loot_table/{rest}.json', [])
                self.assertTrue(owners or namespace == 'minecraft', spec['target'])
                tables += [json.loads(owner[2]) for owner in owners]
        names = set(re.findall(r'"name": "([^"]+)"', json.dumps(tables)))
        self.assertEqual(len(tables), 6 + 6)  # 6 ports; 6 aliases target a mod table
        self.assertFalse({n for n in names if n in balance.STAGE_MATERIALS or n.startswith('entrelumen:')})

    def test_self_drop_is_vanillas_form_written_from_the_block_id_alone(self):
        spec = balance.self_drop('x:tiny_skull', '')
        self.assertEqual(spec['path'], 'data/x/loot_table/blocks/tiny_skull.json')
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            # The mod's own 1.20-folder table is never read: only the block ID reaches the output.
            theirs = upstream('data/x/loot_tables/blocks/tiny_skull.json', {'type': 'minecraft:block', 'pools': [
                {'rolls': 1.0, 'entries': [{'type': 'minecraft:item', 'name': 'x:other_item'}]}]})
            out = json.loads(balance.build_data('probe', theirs)['x/loot_table/blocks/tiny_skull.json'])
            self.assertEqual(out, {'type': 'minecraft:block', 'pools': [{
                'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                'entries': [{'type': 'minecraft:item', 'name': 'x:tiny_skull'}], 'rolls': 1.0}],
                'random_sequence': 'x:blocks/tiny_skull'})
            with self.assertRaises(AssertionError):  # the mod ships a 1.21 table now
                balance.build_data('probe', upstream(spec['path'], out))

    def test_alias_rolls_its_target_once_and_goes_when_the_table_ships(self):
        spec = balance.aliased_loot('x:chests/raw_meat', 'x:chests/meat', '')
        self.assertEqual(spec['path'], 'data/x/loot_table/chests/raw_meat.json')
        meat = {'type': 'minecraft:chest', 'pools': []}
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = balance.build_data('probe', upstream('data/x/loot_table/chests/meat.json', meat))
            self.assertEqual(json.loads(out['x/loot_table/chests/raw_meat.json']),
                             {'type': 'minecraft:chest', 'pools': [{'rolls': 1, 'entries': [
                                 {'type': 'minecraft:loot_table', 'value': 'x:chests/meat'}]}]})
            for found in ({},  # the target does not load
                          {**upstream('data/x/loot_table/chests/meat.json', meat), **upstream(spec['path'], meat)},
                          {**upstream('data/x/loot_table/chests/meat.json', meat),
                           **upstream('data/x/loot_tables/chests/raw_meat.json', meat)}):  # in the 1.20 folder: port it
                with self.subTest(found=sorted(found)), self.assertRaises(AssertionError):
                    balance.build_data('probe', found)
        # A vanilla target is left to tools/check_loot_tables.py, which reads the vanilla JAR.
        vanilla = balance.aliased_loot('x:chests/leatherworker', 'minecraft:chests/village/village_tannery', '')
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [vanilla]}}):
            self.assertIn('x/loot_table/chests/leatherworker.json', balance.build_data('probe', {}))

    def test_retarget_points_only_the_missing_template_at_the_shipped_one(self):
        path = 'data/x/worldgen/template_pool/rooms.json'
        pool = {'fallback': 'minecraft:empty', 'elements': [
            {'weight': 60, 'element': {'element_type': 'minecraft:single_pool_element', 'location': 'x:library',
                                       'processors': 'minecraft:empty', 'projection': 'rigid'}},
            {'weight': 30, 'element': {'element_type': 'minecraft:list_pool_element', 'projection': 'rigid', 'elements': [
                {'element_type': 'minecraft:single_pool_element', 'location': 'x:hall', 'processors': 'minecraft:empty',
                 'projection': 'rigid'}]}}]}
        spec = balance.retargeted_template(path, 'x:library', 'x:libary', '')
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = json.loads(balance.build_data('probe', upstream(path, pool), {'x:libary', 'x:hall'})['x/worldgen/template_pool/rooms.json'])
            expected = json.loads(json.dumps(pool))
            expected['elements'][0]['element']['location'] = 'x:libary'
            self.assertEqual(out, expected)
            for templates in ({'x:libary', 'x:library'}, {'x:hall'}):  # upstream fixed it, or the target is gone
                with self.subTest(templates=sorted(templates)), self.assertRaises(AssertionError):
                    balance.build_data('probe', upstream(path, pool), templates)

    def test_structure_off_empties_the_biome_tag_only_an_empty_structure_uses(self):
        structure = {'type': 'minecraft:jigsaw', 'start_pool': 'x:bullet_start', 'biomes': '#x:has_structure/bullet',
                     'step': 'surface_structures'}
        pool = {'fallback': 'minecraft:empty', 'elements': [{'weight': 1, 'element': {
            'element_type': 'minecraft:single_pool_element', 'location': 'x:bullet', 'processors': 'minecraft:empty',
            'projection': 'rigid'}}]}
        found = {**upstream('data/x/worldgen/structure/bullet.json', structure),
                 **upstream('data/x/worldgen/template_pool/bullet_start.json', pool),
                 **upstream('data/x/tags/worldgen/biome/has_structure/bullet.json', {'values': ['x:venus']})}
        spec = balance.structure_off('x:bullet', '')
        with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
            out = balance.build_data('probe', found, set())
            self.assertEqual(out, {'x/tags/worldgen/biome/has_structure/bullet.json': '{\n  "replace": true,\n  "values": []\n}\n'})
            shared = {**found, **upstream('data/x/worldgen/structure/tower.json', dict(structure, start_pool='x:tower'))}
            for broken, templates in ((found, {'x:bullet'}),  # the template ships: the structure is not empty
                                      (shared, set())):       # another structure uses the same tag
                with self.subTest(templates=sorted(templates)), self.assertRaises(AssertionError):
                    balance.build_data('probe', broken, templates)


if __name__ == '__main__':
    unittest.main()
