"""Generate/check small acquisition changes from the pinned McJty recipe JARs.

The JARs remain authoritative for serializer, result, crafting category, conditions
and all untouched ingredients. This does not install a mod or run Minecraft.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'pack/kubejs/server_scripts/entrelumen_rftools_balance.js'
MODS = {'rftoolsbuilder', 'rftoolsutility', 'rftoolspower', 'xnet'}
FAMILY = MODS | {'rftoolsbase', 'mcjtylib'}

# One slot per native recipe; the old symbol still appears elsewhere. Keeping
# wrapper recipes is essential for configured cards and charged items.
# (component, row, column, original pattern, original ingredient at that slot)
CHANGES = {
    'rftoolsbuilder:builder': ('entrelumen:handling_core', 2, 1, ('BoB', 'rFr', 'BrB'), {'item': 'minecraft:redstone'}),
    # Quarries are a spectral lens function (Act IV) since 24 September 2026, like the other quarries.
    'rftoolsbuilder:shape_card_quarry': ('entrelumen:spectral_lens', 0, 0, ('rPr', 'iMi', 'rSr'), {'item': 'minecraft:redstone'}),
    'rftoolsutility:spawner': ('entrelumen:ecosystem_capsule', 2, 0, ('rzr', 'oFX', 'rPr'), {'item': 'minecraft:redstone'}),
    'rftoolsutility:matter_receiver': ('entrelumen:routing_matrix', 1, 0, ('iii', 'rFr', 'ooo'), {'item': 'minecraft:redstone'}),
    'rftoolsutility:charged_porter': ('entrelumen:routing_matrix', 0, 0, (' o ', 'oRo', 'ioi'), None),
    'rftoolsutility:environmental_controller': ('entrelumen:power_regulator', 2, 2, ('oXo', 'zFI', 'oEo'), {'tag': 'c:ender_pearls'}),
    'rftoolspower:dimensionalcell_simple': ('entrelumen:power_regulator', 2, 0, ('RdR', 'qFq', 'RdR'), {'item': 'minecraft:redstone_block'}),
    'rftoolspower:dimensionalcell': ('entrelumen:power_regulator', 2, 0, ('RdR', 'PFP', 'ReR'), {'item': 'minecraft:redstone_block'}),
    'xnet:controller': ('entrelumen:routing_matrix', 1, 0, ('ICI', 'rFr', 'igi'), {'item': 'minecraft:redstone'}),
    'xnet:router': ('entrelumen:routing_matrix', 1, 0, ('ICI', 'rFr', 'ioi'), {'item': 'minecraft:redstone'}),
    'xnet:wireless_router': ('entrelumen:routing_matrix', 1, 0, ('oCo', 'rFr', 'oro'), {'item': 'minecraft:redstone'}),
}


def read(path: Path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def inner(recipe):
    if recipe['type'] == 'mcjtylib:copy_components':
        assert recipe['recipe']['type'] == 'minecraft:crafting_shaped'
        return recipe['recipe']
    assert recipe['type'] == 'minecraft:crafting_shaped'
    return recipe


def transform(recipe_id, original):
    component, row, col, pattern, old_ingredient = CHANGES[recipe_id]
    result = copy.deepcopy(original)
    craft = inner(result)
    assert tuple(craft['pattern']) == pattern, f'{recipe_id}: native pattern changed'
    assert craft['result']['id'] == recipe_id, f'{recipe_id}: native result changed'
    old_symbol = craft['pattern'][row][col]
    if old_ingredient is None:
        assert old_symbol == ' ', f'{recipe_id}: intended empty slot changed'
    else:
        assert craft['key'][old_symbol] == old_ingredient, f'{recipe_id}: native ingredient changed'
        assert sum(line.count(old_symbol) for line in pattern) > 1, f'{recipe_id}: unique ingredient would be lost'
    assert 'Z' not in craft['key'] and all('Z' not in line for line in pattern)
    craft['pattern'][row] = craft['pattern'][row][:col] + 'Z' + craft['pattern'][row][col + 1:]
    craft['key']['Z'] = {'item': component}
    # A precise reverse edit must recover every native field, including result,
    # serializer, conditions, component-copy behavior and remainders.
    reverse = inner(result)
    reverse['pattern'][row] = reverse['pattern'][row][:col] + old_symbol + reverse['pattern'][row][col + 1:]
    del reverse['key']['Z']
    assert result == original, f'{recipe_id}: an unrelated native field changed'
    craft['pattern'][row] = craft['pattern'][row][:col] + 'Z' + craft['pattern'][row][col + 1:]
    craft['key']['Z'] = {'item': component}
    assert result['type'] == original['type'] and inner(result)['result'] == inner(original)['result']
    return result


def inputs(recipe):
    craft = inner(recipe)
    return {item['item'] for item in craft['key'].values() if 'item' in item}


def output_id(recipe):
    body = recipe['recipe'] if recipe['type'] == 'mcjtylib:copy_components' else recipe
    return body.get('result', {}).get('id')


def check_component_cycles():
    projects = read(ROOT / 'content/integration-design.json')['projects']
    sources = {p['output']['id']: p['recipe']['inputs'] for p in projects}
    visiting, visited = set(), set()

    def visit(item):
        assert item.split(':', 1)[0] not in FAMILY, f'RFTools/XNet is needed to make its own acquisition input: {item}'
        if item not in sources or item in visited:
            return
        assert item not in visiting, f'Circular integration component: {item}'
        visiting.add(item)
        for ingredient in sources[item]:
            visit(ingredient['id'])
        visiting.remove(item)
        visited.add(item)

    for component, *_ in CHANGES.values():
        assert component in sources, f'Uncraftable integration component: {component}'
        visit(component)
    return sorted(visited)


def check_quarry_family(recipes):
    quarry = 'rftoolsbuilder:shape_card_quarry'
    assert quarry in recipes
    producing = {}
    for rid, recipe in recipes.items():
        if rid.startswith('rftoolsbuilder:'):
            if output_id(recipe):
                producing.setdefault(output_id(recipe), []).append(rid)
    family = {item for item in producing if item.startswith(quarry)}
    assert len(family) == 6, f'Quarry output family changed: {sorted(family)}'

    def derived_from_base(item, seen):
        if item == quarry:
            return True
        if item in seen:
            return False
        return any(derived_from_base(ingredient, seen | {item}) for rid in producing.get(item, ())
                   for ingredient in inputs(recipes[rid]) if ingredient.startswith('rftoolsbuilder:shape_card_quarry'))

    assert all(derived_from_base(item, set()) for item in family), 'A quarry variant bypasses the base card'
    # Alternate output for the base card itself must also consume a derived card.
    assert all(rid == quarry or any(derived_from_base(ingredient, set()) for ingredient in inputs(recipes[rid]))
               for rid in producing[quarry]), 'Alternate base quarry recipe bypasses the base card'


def build():
    paths = read(ROOT / 'catalog/local-paths.json')
    lock = read(ROOT / 'catalog/curated.json')
    by_mod = {mod['id']: entry for entry in lock['mods'] for mod in entry['metadata']['mods']}
    assert FAMILY <= by_mod.keys(), f'Missing family JARs: {FAMILY - by_mod.keys()}'
    recipes, sources = {}, []
    for mod_id in sorted(MODS):
        entry = by_mod[mod_id]
        assert entry['side'] == 'both' and entry['metadata']['license'].startswith('MIT License')
        path = Path(paths[entry['filename']])
        assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], f'Pinned JAR changed: {path.name}'
        sources.append({'filename': entry['filename'], 'sha256': entry['sha256']})
        with zipfile.ZipFile(path) as jar:
            for name in sorted(jar.namelist()):
                prefix = f'data/{mod_id}/recipe/'
                if not name.startswith(prefix) or not name.endswith('.json'):
                    continue
                rid = mod_id + ':' + name[len(prefix):-5]
                assert rid not in recipes, f'Duplicate recipe ID: {rid}'
                recipes[rid] = json.loads(jar.read(name))
    assert set(CHANGES) <= recipes.keys(), f'Missing native recipes: {set(CHANGES) - recipes.keys()}'
    assert not any('creative' in rid or 'creative' in (output_id(recipe) or '')
                   for rid, recipe in recipes.items()), 'Creative power has a recipe'
    producing = {}
    for rid, recipe in recipes.items():
        if output_id(recipe):
            producing.setdefault(output_id(recipe), set()).add(rid)
    for rid in CHANGES:
        expected = {rid, 'rftoolsbuilder:shape_card_quarry_dirt'} if rid == 'rftoolsbuilder:shape_card_quarry' else {rid}
        assert producing.get(rid) == expected, f'Alternate acquisition changed for {rid}: {producing.get(rid)}'
    components = check_component_cycles()
    check_quarry_family(recipes)
    rows = []
    for rid in sorted(CHANGES):
        native = recipes[rid]
        rows.append({'id': rid, 'component': CHANGES[rid][0], 'json': transform(rid, native)})
    return rows, sources, components, len(recipes)


RUNTIME = '''
ServerEvents.recipes(event => {
  const missing = [];
  entrelumenRFToolsRecipes.forEach(row => {
    if (!event.containsRecipe({id: row.id})) missing.push({recipe: row.id, cause: 'native recipe absent'});
    if (!Item.exists(row.component)) missing.push({recipe: row.id, cause: 'component absent', component: row.component});
  });
  if (missing.length) {
    console.error('[ENTRELUMEN_RFTOOLS_BALANCE] ' + JSON.stringify({status: 'failed-preflight', missing: missing}));
    throw new Error('RFTools balance preflight failed');
  }
  entrelumenRFToolsRecipes.forEach(row => {
    event.remove({id: row.id});
    event.custom(row.json).id(row.id);
  });
  console.info('[ENTRELUMEN_RFTOOLS_BALANCE] ' + JSON.stringify({status: 'registered', signature: entrelumenRFToolsSignature, recipes: entrelumenRFToolsRecipes.length}));
});
ServerEvents.afterRecipes(event => {
  const failed = entrelumenRFToolsRecipes.filter(row => event.countRecipes({id: row.id, output: row.json.recipe ? row.json.recipe.result.id : row.json.result.id}) !== 1).map(row => row.id);
  console.info('[ENTRELUMEN_RFTOOLS_BALANCE] ' + JSON.stringify({status: failed.length ? 'failed-loaded-check' : 'loaded-output-check-only', signature: entrelumenRFToolsSignature, failed: failed}));
});
'''


def render(rows, sources):
    payload = json.dumps(rows, ensure_ascii=False, separators=(',', ':'))
    signature = hashlib.sha256(payload.encode('utf-8')).hexdigest()
    return ('// Generated by tools/generate_rftools_balance.py; native acquisition only.\n'
            '// No team, use, dimension, origin, gift or reward checks.\n'
            'const entrelumenRFToolsSignature = ' + json.dumps(signature) + ';\n'
            'const entrelumenRFToolsSources = ' + json.dumps(sources, separators=(',', ':')) + ';\n'
            'const entrelumenRFToolsRecipes = ' + payload + ';\n' + RUNTIME)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    rows, sources, components, audited = build()
    output = render(rows, sources)
    if args.write:
        TARGET.write_text(output, encoding='utf-8', newline='\n')
    elif not TARGET.exists() or TARGET.read_text(encoding='utf-8') != output:
        raise ValueError('Generated RFTools balance is stale; run --write')
    print(json.dumps({'status': 'static-PASS', 'nativeRecipesAudited': audited,
                      'changed': len(rows), 'componentDAG': components, 'runtime': 'pending'}))


if __name__ == '__main__':
    main()
