"""Index pinned upstream recipes without changing runtime recipes or copying pack configs."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/design/resource-recipe-inventory.json'
TARGETS = {'jamd', 'botanypotstiers', 'modularbees'}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def tier_of(value):
    return next((tier for tier in ('elite', 'ultra', 'mega', 'creative')
                 if value.split(':')[-1].startswith(tier + '_')), None)


def route(name, recipe):
    if '/electrode/' in name or '/treater/' in name:
        return 'machine_fuel_definition_not_item_creation'
    if 'upgrade_quick' in name:
        return 'quick_upgrade_same_material' if 'same_material' in name else 'quick_upgrade_material_variant'
    if 'same_material' in name:
        return 'same_material_upgrade'
    if name.endswith('_upgrade'):
        return 'upgrade_item' if '/pots/' not in name else 'hopper_upgrade_material_variant'
    if 'waxed' in name:
        return 'waxing_same_tier'
    if 'hopper_botany_pot' in name and recipe['type'] == 'minecraft:crafting_shapeless':
        return 'hopper_conversion_same_tier'
    return 'direct_or_material_variant'


def build():
    lock = read(ROOT / 'catalog/curated.json')
    paths = read(ROOT / 'catalog/local-paths.json')
    sources, rows, code = [], [], []
    for entry in lock['mods']:
        mods = TARGETS & {m['id'] for m in entry['metadata']['mods']}
        if not mods:
            continue
        path = Path(paths[entry['filename']])
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise ValueError('Pinned JAR hash mismatch: ' + entry['filename'])
        sources.append({k: entry[k] for k in ('filename', 'sha256', 'projectID', 'fileID')})
        with zipfile.ZipFile(path) as jar:
            for name in sorted(jar.namelist()):
                if '/recipe/' not in name or not name.endswith('.json'):
                    continue
                raw = jar.read(name)
                recipe = json.loads(raw)
                namespace, local = name[5:-5].split('/recipe/', 1)
                result = recipe.get('result', recipe.get('results'))
                output = result.get('id', '') if isinstance(result, dict) else ''
                ingredients = Counter()
                if 'pattern' in recipe:
                    for char in ''.join(recipe['pattern']):
                        if char != ' ':
                            ingredients[json.dumps(recipe['key'][char], sort_keys=True)] += 1
                elif 'ingredients' in recipe:
                    for ingredient in recipe['ingredients']:
                        ingredients[json.dumps(ingredient, sort_keys=True)] += 1
                else:
                    for field in ('base', 'addition', 'template', 'ingredient'):
                        if field in recipe:
                            ingredients[json.dumps(recipe[field], sort_keys=True)] += 1
                kind = recipe['type']
                preservation = ('copies base components through vanilla smithing_transform; verify supplied result components'
                                if kind == 'minecraft:smithing_transform' else
                                'fresh result; vanilla shaped/shapeless does not copy ingredient components'
                                if kind in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless') else
                                'custom serializer; recipe JSON alone does not establish component or remainder behavior')
                rows.append({'id': namespace + ':' + local, 'sourceSha256': hashlib.sha256(raw).hexdigest(),
                             'type': kind, 'tier': tier_of(output or local), 'route': route('/' + local, recipe),
                             'result': result, 'ingredients': [{'selector': json.loads(key), 'occupiedCount': count}
                                                             for key, count in sorted(ingredients.items())],
                             'conditions': recipe.get('bookshelf:load_conditions', recipe.get('neoforge:conditions', [])),
                             'preservation': preservation,
                             'remainder': 'native item crafting remainder; actual item stack behavior requires runtime verification',
                             'specialFields': {k: v for k, v in recipe.items() if k not in
                                               {'type', 'category', 'group', 'pattern', 'key', 'ingredients', 'base',
                                                'addition', 'template', 'ingredient', 'result', 'results',
                                                'bookshelf:load_conditions', 'neoforge:conditions'}}})
            upgrade = 'com/ultramega/botanypotstiers/common/impl/item/UpgradeItem.class'
            if upgrade in jar.namelist():
                code.append({'class': upgrade, 'sha256': hashlib.sha256(jar.read(upgrade)).hexdigest(),
                             'review': 'javap -c -p: useOn accepts regular->elite or next tier only; saves full block entity metadata, clears old Container, sets replacement block, loads components and shrinks upgrade by one. Material/name suffix retained. No campaign check.',
                             'action': 'Gate acquisition of all three upgrade items as well as direct recipes. Keep useOn and gifts available; test inventory preservation on server.'})
    rows.sort(key=lambda row: row['id'])
    assert len({r['id'] for r in rows}) == len(rows), 'Duplicate recipe ID'
    assert {s['filename'] for s in sources} and len(sources) == 3
    return {'schemaVersion': 1, 'status': 'source inventory and replacement proposal; no runtime recipe edits',
            'coverage': 'Every recipe JSON in all three pinned JARs, including conditional and custom recipes. Does not claim coverage of other mods, active datapacks, loot or arbitrary code-created recipes.',
            'sources': sorted(sources, key=lambda x: x['filename']),
            'counts': {'total': len(rows), 'byNamespace': dict(sorted(Counter(r['id'].split(':')[0] for r in rows).items())),
                       'byTier': dict(sorted(Counter(r['tier'] or 'not_tiered' for r in rows).items())),
                       'byRoute': dict(sorted(Counter(r['route'] for r in rows).items()))},
            'nonCraftingAcquisition': code,
            'replacementPlan': {
                'policy': 'Static obtaining costs only. Never block use, trading or gifted components. No recipe craft completes campaign.',
                'jamd': {'II': ['jamd:portal_block', 'entrelumen:calibration_frame'],
                         'IV': ['jamd:nether_portal_block', 'entrelumen:containment_seal'],
                         'V': ['jamd:end_portal_block', 'entrelumen:horizon_chart'],
                         'method': 'Replace all three portal IDs with explicit static recipes; preserve result IDs/count=1 and distinct dimensional materials.'},
                'pots': {'elite_III': 'entrelumen:propagation_core', 'ultra_IV': 'entrelumen:ecosystem_capsule',
                         'mega_V': 'entrelumen:renewal_engine',
                         'method': 'Require previous-tier same-material pot plus corresponding upgrade. Gate all three upgrade-item recipes with listed component. Replace EVERY cross-tier direct, same-material and quick route; retain same-tier hopper/wax conversions. Preserve output material/color and count, remove alternate direct max-tier routes; do not rely solely on config conditions.',
                         'creative': 'No creative acquisition or rewards. Report any discovered creative result before installation.'},
                'bees': {'IV_controllers': ['entrelumen:ecosystem_capsule', 'entrelumen:handling_core'],
                         'V_ME_and_acceleration': ['entrelumen:routing_matrix', 'entrelumen:power_regulator'],
                         'VI_dragon_and_highest_electrode': ['entrelumen:containment_seal', 'entrelumen:renewal_engine'],
                         'method': 'Classify every modular_*, me_* and electrode_* item-producing recipe in this index. Preserve native serializer and output count; add component costs to explicit static replacements. Keep smithing_transform for netherite electrode to retain base components. Do not mistake electrode/* or treater/* machine-fuel definitions for crafting outputs.'},
                'safeSequence': ['Assert pinned hashes and current loaded IDs before mutation.',
                                 'Resolve every ingredient/result and count slots; shapeless costs must fit nine slots.',
                                 'Prepare full replacements before remove calls; retain native serializer/conditions when relevant.',
                                 'Replace IDs as one deterministic recipe event; verify expected output/count and no competing cross-tier route after recipes.',
                                 'Check EMI alternatives and real crafting, smithing, useOn, full inventories and gifts across two teams.']},
            'recipes': rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    result = build()
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding='utf-8') != text:
            raise SystemExit('Resource recipe inventory is stale; run generator.')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(text, encoding='utf-8', newline='\n')
    print(json.dumps({'status': 'PASS', 'counts': result['counts']}))


if __name__ == '__main__':
    main()
