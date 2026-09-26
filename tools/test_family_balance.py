"""Static checks for generated family staging scripts; JAR-backed checks when the lock is local."""
from __future__ import annotations

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
        self.assertTrue(all(spec['op'] == 'disable' for spec in family['data']))

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
        for spec in (balance.copied(path, 'alpha-', '', owners=3), balance.copied(path, 'gamma-', '', owners=2)):
            with unittest.mock.patch.dict(balance.FAMILIES, {'probe': {'data': [spec]}}):
                with self.assertRaises(AssertionError):
                    balance.build_data('probe', found)

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


if __name__ == '__main__':
    unittest.main()
