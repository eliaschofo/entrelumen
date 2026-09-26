"""Pin the cooking family and generate two additive, native crafting routes.

The JARs and CurseForge instance metadata are read only. This never modifies an
instance, the existing travelling_pantry recipe, or any native food recipe.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from curate_pack import jar_metadata

ROOT = Path(__file__).resolve().parents[1]
FAMILY = ROOT / 'catalog/families/cooking-provisions.json'
TARGET = ROOT / 'pack/kubejs/server_scripts/entrelumen_cooking_provisions.js'
WORLDGEN_TARGET = ROOT / 'pack/kubejs/data/pamhc2trees/neoforge/biome_modifier'
ORIGINAL_ID = 'entrelumen:integration/travelling_pantry'
OUTPUT = 'entrelumen:ration_bundle'
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
WORLDGEN_DUPLICATES = (
    'avocado', 'cherry', 'cinnamon', 'lemon', 'lime',
    'orange', 'peach', 'pear', 'plum',
)
NO_FEATURE = '{"type":"neoforge:none"}\n'

# Both paths cost two finished main dishes and two cooked bowl meals for one
# bundle. Bowl return comes from the consumed Farmer's Delight items. No raw
# crop/fish/meat directly enters an ENTRELUMEN ration recipe. Since Elias's
# playtest of 24 September 2026 every ration is the same drawing as the
# travelling pantry (docs/design/recipe-design-rules.md): the bowl meals on the
# axis, the main dishes on either side, a hollow bundle in the middle.
PATTERN = [' A ', 'B B', ' A ']
ALTERNATIVES = {
    'entrelumen:cooking_provisions/homestead_chicken': [
        ('farmersdelight:vegetable_soup', 2),
        ('pamhc2foodcore:chickendinneritem', 2),
    ],
    'entrelumen:cooking_provisions/field_supper': [
        ('farmersdelight:fish_stew', 2),
        ('herbsandharvest:beef_cheddar', 2),
    ],
}

# Explicit native recipe signatures establish finished-food acquisition and
# guard against silently changing a route when an upstream JAR is replaced.
NATIVE = {
    'farmersdelight': {
        'data/farmersdelight/recipe/cooking/vegetable_soup.json': (
            'farmersdelight:cooking', 'farmersdelight:vegetable_soup',
            [('tag', 'c:crops/carrot'), ('tag', 'c:crops/potato'),
             ('tag', 'c:crops/beetroot'), ('tag', 'c:foods/leafy_green')]),
        'data/farmersdelight/recipe/cooking/fish_stew.json': (
            'farmersdelight:cooking', 'farmersdelight:fish_stew',
            [('tag', 'c:foods/safe_raw_fish'), ('item', 'farmersdelight:tomato_sauce'),
             ('tag', 'c:crops/onion')]),
    },
    'pamhc2foodcore': {
        'data/pamhc2foodcore/recipe/chickendinneritem.json': (
            'minecraft:crafting_shapeless', 'pamhc2foodcore:chickendinneritem',
            [('tag', 'c:tool_cuttingboard'), ('item', 'pamhc2foodcore:friedchickenitem'),
             ('item', 'pamhc2foodcore:mashedpotatoesitem'), ('tag', 'c:vegetables')]),
        'data/pamhc2foodcore/recipe/friedchickenitem.json': (
            'minecraft:crafting_shapeless', 'pamhc2foodcore:friedchickenitem',
            [('tag', 'c:tool_pot'), ('tag', 'c:flour'), ('tag', 'c:rawchicken'),
             ('tag', 'c:cookingoil')]),
        'data/pamhc2foodcore/recipe/mashedpotatoesitem.json': (
            'minecraft:crafting_shapeless', 'pamhc2foodcore:mashedpotatoesitem',
            [('tag', 'c:tool_mixingbowl'), ('item', 'minecraft:baked_potato'),
             ('tag', 'c:butter'), ('tag', 'c:salt')]),
    },
    'herbsandharvest': {
        'data/herbsandharvest/recipe/beef_cheddar.json': (
            'minecraft:crafting_shapeless', 'herbsandharvest:beef_cheddar',
            [('tag', 'c:foods/cooked_beef'), ('item', 'herbsandharvest:bread_slice'),
             ('item', 'herbsandharvest:bread_slice'), ('tag', 'c:foods/cheddar'),
             ('tag', 'c:foods/lettuce'), ('tag', 'c:foods/mayonnaise'),
             ('tag', 'c:foods/tomato')]),
        'data/herbsandharvest/recipe/mayo.json': (
            'minecraft:crafting_shapeless', 'herbsandharvest:mayo',
            [('item', 'herbsandharvest:spice_jar_item'), ('tag', 'c:foods/egg'),
             ('tag', 'c:foods_oil'), ('tag', 'c:foods/lemon')]),
        'data/herbsandharvest/recipe/bread_slice.json': (
            'minecraft:crafting_shapeless', 'herbsandharvest:bread_slice',
            [('tag', 'c:foods/bread')]),
    },
}


def read(path: Path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def jar_path(filename: str, source_instance: Path | None, local_paths: dict):
    if source_instance is not None:
        path = source_instance / 'mods' / filename
    else:
        if filename not in local_paths:
            raise ValueError(f'Missing local path for {filename}; pass --source-instance')
        path = Path(local_paths[filename])
    if not path.is_file():
        raise FileNotFoundError(path)
    return path


def check_pins(family, source_instance: Path | None, local_paths):
    if family['schemaVersion'] != 1 or len(family['content']) != 5:
        raise ValueError('Expected exactly five cooking content projects')
    pins = family['pins']
    if {p['modIds'][0] for p in pins} != set(family['content']):
        raise ValueError('Pin and content mod IDs differ')
    installed = {}
    if source_instance is not None:
        installed = {a.get('fileNameOnDisk'): a for a in
                     read(source_instance / 'minecraftinstance.json')['installedAddons']}
    catalog = {e['filename']: e for e in read(ROOT / 'catalog/curated.json')['mods']}
    paths = {}
    for pin in pins:
        filename = pin['filename']
        path = jar_path(filename, source_instance, local_paths)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != pin['sha256']:
            raise ValueError(f'Pinned JAR changed: {filename}')
        metadata = jar_metadata(path)
        if {m['id'] for m in metadata['mods']} != set(pin['modIds']):
            raise ValueError(f'Wrong mod ID: {filename}')
        declared = {f"{d['id']} {d['range']}" for d in metadata['dependencies'] if d['required']}
        if declared != set(pin['dependencies']):
            raise ValueError(f'Dependency metadata changed: {filename}: {declared}')
        if metadata['license'] != pin['license'] or metadata['embedded']:
            raise ValueError(f'License or embedded providers changed: {filename}')
        if filename in installed:
            addon = installed[filename]
            file = addon['installedFile']
            if (addon['addonID'], file['id']) != (pin['projectID'], pin['fileID']):
                raise ValueError(f'CurseForge identity changed: {filename}')
            if addon.get('allowModDistribution') is not True:
                raise ValueError(f'Distribution permission unconfirmed: {filename}')
            cf_required = {d['addonId'] for d in file.get('dependencies', []) if d.get('type') == 3}
            if cf_required != set(pin.get('cfRequiredProjects', [])):
                raise ValueError(f'CurseForge dependencies changed: {filename}')
        elif filename in catalog:
            entry = catalog[filename]
            if (entry['projectID'], entry['fileID']) != (pin['projectID'], pin['fileID']):
                raise ValueError(f'Catalog identity changed: {filename}')
        else:
            raise ValueError(f'CurseForge metadata unavailable for {filename}')
        paths[pin['modIds'][0]] = path
    # Existing closure is reused, not counted as new content projects.
    existing_ids = {m['id'] for entry in catalog.values() for m in entry['metadata']['mods']}
    for mod in ('farmersdelight', 'aquaculture', 'cookingforblockheads',
                'farmingforblockheads', 'botanypots', 'titanium', 'patchouli'):
        if mod not in existing_ids:
            raise ValueError(f'Missing existing dependency: {mod}')
    for mod_id in ('farmersdelight', 'farmingforblockheads'):
        entry = next(e for e in catalog.values()
                     if any(m['id'] == mod_id for m in e['metadata']['mods']))
        path = jar_path(entry['filename'], source_instance, local_paths)
        if hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError(f'Existing dependency JAR changed: {mod_id}')
        paths[mod_id] = path
    return paths


def check_native(paths):
    audited = 0
    for mod, recipes in NATIVE.items():
        with zipfile.ZipFile(paths[mod]) as jar:
            for path, (recipe_type, result_id, ingredients) in recipes.items():
                recipe = json.loads(jar.read(path))
                actual = [(key, value) for ingredient in recipe['ingredients']
                          for key, value in ingredient.items()]
                if (recipe['type'] != recipe_type or
                        recipe['result']['id'] != result_id or actual != ingredients):
                    raise ValueError(f'Native food recipe changed: {mod}: {path}')
                audited += 1
    return audited


def check_original():
    projects = read(ROOT / 'content/integration-design.json')['projects']
    original = next(p for p in projects if p['recipe']['id'] == ORIGINAL_ID)
    if (original['output']['id'] != OUTPUT or original['output']['count'] != 1 or
            original['recipe']['inputs'] != [
                {'id': 'farmersdelight:vegetable_soup', 'count': 2},
                {'id': 'aquaculture:fish_fillet_cooked', 'count': 2}]):
        raise ValueError('Original ration recipe changed; review alternatives')


def check_worldgen_acquisition(paths):
    """Every disabled Pam placement retains a native market + sapling route."""
    with (zipfile.ZipFile(paths['pamhc2trees']) as trees,
          zipfile.ZipFile(paths['farmingforblockheads']) as market,
          zipfile.ZipFile(paths['pamhc2foodcore']) as food):
        tree_names = set(trees.namelist())
        market_names = set(market.namelist())
        preset = json.loads(market.read('data/pamhc2trees/market_presets/saplings.json'))
        if preset != {'enabled': True, 'payment': {
                'ingredient': {'item': 'minecraft:emerald'}, 'count': 1}}:
            raise ValueError('Pam sapling market price or availability changed')
        for species in WORLDGEN_DUPLICATES:
            prefix = 'data/pamhc2trees/'
            modifier_path = prefix + f'neoforge/biome_modifier/{species}_placed.json'
            modifier = json.loads(trees.read(modifier_path))
            if modifier['type'] != 'neoforge:add_features' or modifier['features'] != f'pamhc2trees:{species}_placed':
                raise ValueError(f'Unexpected native tree placement: {species}')
            if (prefix + f'worldgen/placed_feature/{species}_placed.json' not in tree_names or
                    prefix + f'worldgen/configured_feature/{species}.json' not in tree_names):
                raise ValueError(f'Missing native tree feature: {species}')
            sapling_id = f'pamhc2trees:{species}_sapling'
            fruit_id = f'pamhc2trees:{species}item'
            sapling = json.loads(trees.read(prefix + f'recipe/{species}_sapling.json'))
            if (sapling['type'] != 'minecraft:crafting_shapeless' or
                    sapling['result']['id'] != sapling_id or
                    sapling['ingredients'] != [{'item': fruit_id}] * 8 +
                    [{'tag': 'minecraft:saplings'}]):
                raise ValueError(f'Pam tree propagation changed: {species}')
            market_path = f'data/farmingforblockheads/recipe/market/pamhc2trees/{species}_sapling.json'
            if market_path not in market_names:
                raise ValueError(f'Sapling market route missing: {species}')
            recipe = json.loads(market.read(market_path))
            if recipe != {'type': 'farmingforblockheads:market',
                          'category': 'farmingforblockheads:saplings',
                          'preset': 'pamhc2trees:saplings',
                          'result': {'item': sapling_id}}:
                raise ValueError(f'Sapling market route changed: {species}')
        # Fruit has a real use as a prepared meal input, even without Food Extended.
        fruits = json.loads(trees.read('data/c/tags/item/fruits.json'))
        orange = json.loads(trees.read('data/c/tags/item/fruits/orange.json'))
        salad = json.loads(food.read('data/pamhc2foodcore/recipe/fruitsaladitem.json'))
        punch = json.loads(food.read('data/pamhc2foodcore/recipe/fruitpunchitem.json'))
        if ('#c:fruits/orange' not in fruits['values'] or
                'pamhc2trees:orangeitem' not in orange['values'] or
                salad['result']['id'] != 'pamhc2foodcore:fruitsaladitem' or
                sum(i.get('tag') == 'c:fruits' for i in salad['ingredients']) != 2 or
                punch['result']['id'] != 'pamhc2foodcore:fruitpunchitem' or
                sum(i.get('tag') == 'c:fruits' for i in punch['ingredients']) != 3):
            raise ValueError('Pam orchard fruit no longer feeds prepared recipes')
    return len(WORLDGEN_DUPLICATES)


def write_or_check_worldgen(write: bool):
    expected = {f'{species}_placed.json' for species in WORLDGEN_DUPLICATES}
    if write:
        WORLDGEN_TARGET.mkdir(parents=True, exist_ok=True)
        for name in expected:
            (WORLDGEN_TARGET / name).write_text(NO_FEATURE, encoding='utf-8', newline='\n')
    actual = {p.name for p in WORLDGEN_TARGET.glob('*.json')} if WORLDGEN_TARGET.exists() else set()
    if actual != expected:
        raise ValueError(f'Worldgen override set differs: expected {expected}, got {actual}')
    for name in expected:
        if (WORLDGEN_TARGET / name).read_text(encoding='utf-8') != NO_FEATURE:
            raise ValueError(f'Worldgen override changed: {name}')


def rows():
    result = []
    for recipe_id, inputs in ALTERNATIVES.items():
        if not ID.fullmatch(recipe_id) or sum(count for _, count in inputs) != 4:
            raise ValueError(f'Invalid alternative: {recipe_id}')
        for item, count in inputs:
            if not ID.fullmatch(item) or item.startswith('minecraft:') or count != 2:
                raise ValueError(f'Unbalanced alternative: {recipe_id}: {item}')
        (axis, _), (sides, _) = inputs
        result.append({'id': recipe_id, 'json': {
            'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': PATTERN,
            'key': {'A': {'item': axis}, 'B': {'item': sides}}, 'result': {'id': OUTPUT, 'count': 1}}})
    return result


def check_collisions(data):
    ids = {row['id'] for row in data}
    for base in (ROOT / 'companion/src/main/resources/data', ROOT / 'pack/kubejs/data'):
        if not base.exists():
            continue
        for path in base.glob('*/recipe/**/*.json'):
            rel = path.relative_to(base)
            recipe_id = rel.parts[0] + ':' + '/'.join(rel.parts[2:])[:-5]
            if recipe_id in ids:
                raise ValueError(f'Existing source recipe collision: {path}')


RUNTIME = r'''ServerEvents.recipes(event => {
  const errors = [];
  entrelumenCookingRecipes.forEach(row => {
    if (event.containsRecipe({id: row.id})) errors.push({recipe: row.id, collision: true});
    [row.json.result.id].concat(Object.keys(row.json.key).map(k => row.json.key[k].item)).forEach(id => {
      if (!Item.exists(id)) errors.push({recipe: row.id, missingItem: id});
    });
  });
  if (errors.length) {
    console.error('[ENTRELUMEN_COOKING] ' + JSON.stringify({status: 'failed-preflight', errors: errors}));
    throw new Error('Cooking provisions preflight failed');
  }
  entrelumenCookingRecipes.forEach(row => event.custom(row.json).id(row.id));
  console.info('[ENTRELUMEN_COOKING] ' + JSON.stringify({status: 'registered', signature: entrelumenCookingSignature, recipes: entrelumenCookingRecipes.length}));
});
ServerEvents.afterRecipes(event => {
  const checked = entrelumenCookingRecipes.concat([{id: 'entrelumen:integration/travelling_pantry', json: {result: {id: 'entrelumen:ration_bundle'}}}]);
  const failed = checked.filter(row => event.countRecipes({id: row.id, output: row.json.result.id}) !== 1).map(row => row.id);
  const receipt = {status: failed.length ? 'failed-loaded-check' : 'loaded-output-check-only', signature: entrelumenCookingSignature, checked: checked.length, failed: failed};
  if (failed.length) console.error('[ENTRELUMEN_COOKING] ' + JSON.stringify(receipt));
  else console.info('[ENTRELUMEN_COOKING] ' + JSON.stringify(receipt));
});
'''


def render(data, family):
    payload = json.dumps(data, separators=(',', ':'))
    signature = hashlib.sha256(payload.encode('utf-8')).hexdigest()
    sources = [{'filename': p['filename'], 'sha256': p['sha256']}
               for p in family['pins']]
    return ("// Generated by tools/generate_cooking_provisions.py --write.\n"
            "// Additive native crafting: original ration and item remainders remain intact.\n"
            "const entrelumenCookingSignature = " + json.dumps(signature) + ";\n"
            "const entrelumenCookingSources = " + json.dumps(sources, separators=(',', ':')) + ";\n"
            "const entrelumenCookingRecipes = " + payload + ";\n" + RUNTIME)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--write', action='store_true')
    action.add_argument('--check', action='store_true')
    parser.add_argument('--source-instance', type=Path,
                        help='Read-only CurseForge instance holding pinned JARs and minecraftinstance.json')
    args = parser.parse_args()
    source = args.source_instance.resolve() if args.source_instance else None
    family = read(FAMILY)
    local_file = ROOT / 'catalog/local-paths.json'
    local_paths = read(local_file) if local_file.exists() else {}
    paths = check_pins(family, source, local_paths)
    audited = check_native(paths)
    check_original()
    suppressed = check_worldgen_acquisition(paths)
    data = rows()
    if set(family['notes']['recipes']) != {row['id'] for row in data}:
        raise ValueError('Documented recipe IDs differ from generator')
    check_collisions(data)
    output = render(data, family)
    if args.write:
        TARGET.write_text(output, encoding='utf-8', newline='\n')
    elif not TARGET.exists() or TARGET.read_text(encoding='utf-8') != output:
        raise ValueError('Generated cooking provisions are stale; run --write')
    write_or_check_worldgen(args.write)
    print(json.dumps({'status': 'static-PASS', 'newJars': len(family['pins']),
                      'nativeRecipesAudited': audited, 'addedRoutes': len(data),
                      'marketBackedTrees': suppressed, 'overlappingPlacementsDisabled': suppressed,
                      'originalRoute': 'unchanged', 'runtime': 'pending'}))


if __name__ == '__main__':
    main()
