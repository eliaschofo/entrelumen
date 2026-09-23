"""Generate/check staged acquisition changes for the large-parity mod families.

Every change is a precise edit of one native recipe read from its pinned JAR:
fill an empty shaped slot, replace one repeated shaped ingredient or replace one
repeated element of a list-based machine recipe. Serializer, result, count,
conditions and every other ingredient stay native; a reverse edit must recover
the original JSON exactly. Removals name exact native IDs. Nothing here checks
teams, acts, provenance or gifts: stages describe acquisition only.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / 'content/integration-design.json'
SCRIPTS = ROOT / 'pack/kubejs/server_scripts'
PACK_DATA = ROOT / 'pack/kubejs/data'

# Stage materials that are not ENTRELUMEN components. Each must have a native
# producer outside the family that consumes it (checked from the pinned JARs).
STAGE_MATERIALS = {
    'aether:zanite_gemstone': 'IV',
    'twilightforest:ironwood_ingot': 'IV',
    'naturesaura:sky_ingot': 'V',
    'mekanism:alloy_atomic': 'V',
}


def shaped(recipe_id, row, col, expect, add, act, why, *, alternates=()):
    return {'id': recipe_id, 'op': 'slot', 'row': row, 'col': col, 'expect': expect,
            'add': add, 'act': act, 'why': why, 'alternates': list(alternates)}


def listed(recipe_id, field, index, expect, add, act, why, *, wrap=None, alternates=()):
    return {'id': recipe_id, 'op': 'list', 'field': field, 'index': index, 'expect': expect,
            'add': add, 'wrap': wrap, 'act': act, 'why': why, 'alternates': list(alternates)}


def item(value):
    return {'item': value}


def when_item_exists(path, item_id, why):
    """Add a NeoForge item_exists condition to an unconditional upstream data file."""
    return {'path': path, 'op': 'item_exists', 'item': item_id, 'why': why}


def without_values(path, field, values, why):
    """Drop exact values from a list in an upstream data file."""
    return {'path': path, 'op': 'remove_values', 'field': field, 'values': list(values), 'why': why}


def tag(value):
    return {'tag': value}


LM = 'entrelumen:living_matrix'
PR, RM, HC = 'entrelumen:power_regulator', 'entrelumen:routing_matrix', 'entrelumen:handling_core'
EC, SL, HZ = 'entrelumen:ecosystem_capsule', 'entrelumen:spectral_lens', 'entrelumen:horizon_chart'
CS, RE, AB = 'entrelumen:containment_seal', 'entrelumen:renewal_engine', 'entrelumen:ark_bus'

FAMILIES = {
    'industrial': {
        'script': 'entrelumen_industrial_balance.js',
        'tag': 'ENTRELUMEN_INDUSTRIAL_BALANCE',
        'namespaces': {'ironjetpacks', 'mininggadgets', 'powah', 'industrialforegoing', 'fluxnetworks',
                       'justdirethings', 'compactmachines', 'hostilenetworks', 'draconicevolution',
                       'brandonscore', 'codechickenlib'},
        'changes': [
            shaped('ironjetpacks:strap', 0, 0, None, PR, 'III', 'Every jetpack chain starts from the lowest-tier strap'),
            shaped('ironjetpacks:elite_coil', 0, 0, None, 'aether:zanite_gemstone', 'IV', 'Diamond/platinum-tier cells and thrusters'),
            shaped('ironjetpacks:ultimate_coil', 0, 0, None, 'mekanism:alloy_atomic', 'V', 'Emerald-tier cells and thrusters'),
            shaped('mininggadgets:mininggadget_simple', 2, 2, tag('c:ingots/iron'), PR, 'III', 'Laser area mining'),
            shaped('mininggadgets:mininggadget', 2, 1, tag('c:ingots/iron'), PR, 'III', 'Laser area mining'),
            shaped('mininggadgets:mininggadget_fancy', 0, 2, tag('c:ingots/iron'), PR, 'III', 'Laser area mining'),
            shaped('powah:crafting/capacitor_niotic', 0, 0, item('powah:dielectric_paste'), 'twilightforest:ironwood_ingot', 'IV', 'Every niotic generator, cell and transmitter'),
            shaped('powah:crafting/capacitor_spirited', 0, 0, item('powah:dielectric_paste'), 'naturesaura:sky_ingot', 'V', 'Every spirited generator, cell and transmitter'),
            shaped('powah:crafting/capacitor_nitro', 0, 0, item('powah:dielectric_paste'), 'mekanism:alloy_atomic', 'V', 'Every nitro generator, cell and transmitter'),
            shaped('industrialforegoing:mob_duplicator', 2, 0, item('minecraft:emerald'), EC, 'IV', 'Spawner-class mob duplication'),
            shaped('industrialforegoing:ore_laser_base', 1, 0, tag('c:ores/iron'), SL, 'IV', 'Ores from power without world mining'),
            shaped('industrialforegoing:fluid_laser_base', 1, 0, item('minecraft:bucket'), PR, 'III', 'Fluids from power'),
            listed('industrialforegoing:dissolution_chamber/infinity_drill', 'input', 0, item('minecraft:diamond_block'), PR, 'III', 'Large-area powered mining tool'),
            listed('industrialforegoing:dissolution_chamber/infinity_nuke', 'input', 0, item('minecraft:tnt'), CS, 'IV', 'Area-destruction tool'),
            shaped('fluxnetworks:flux_plug', 0, 1, item('fluxnetworks:flux_core'), PR, 'III', 'Wireless cross-dimension FE input',
                   alternates=['fluxnetworks:wipe_flux_plug']),
            shaped('fluxnetworks:flux_point', 0, 1, item('fluxnetworks:flux_core'), PR, 'III', 'Wireless cross-dimension FE output',
                   alternates=['fluxnetworks:wipe_flux_point']),
            shaped('fluxnetworks:flux_controller', 1, 1, None, RM, 'III', 'Network hub and wireless inventory charging',
                   alternates=['fluxnetworks:wipe_flux_controller']),
            shaped('justdirethings:portalgun', 0, 1, item('justdirethings:blazegold_ingot'), RM, 'III', 'Portal teleportation'),
            shaped('justdirethings:portalgun_v2', 1, 0, item('justdirethings:blazegold_ingot'), RM, 'III', 'Persistent portal teleportation'),
            shaped('justdirethings:upgrade_flight', 0, 0, item('minecraft:phantom_membrane'), HZ, 'IV', 'Creative-style flight upgrade'),
            shaped('justdirethings:time_wand', 0, 1, item('justdirethings:blazegold_ingot'), RE, 'V', 'Block tick acceleration'),
            shaped('justdirethings:paradoxmachine', 0, 0, item('justdirethings:eclipsealloy_ingot'), AB, 'V', 'Area snapshot and restoration machine'),
            shaped('compactmachines:personal_shrinking_device', 2, 0, tag('c:ingots/iron'), HC, 'III', 'Entering and building compact rooms'),
            shaped('hostilenetworks:sim_chamber', 1, 0, item('minecraft:ender_pearl'), EC, 'IV', 'Entity-free mob drop simulation'),
            shaped('draconicevolution:components/wyvern_core', 0, 0, tag('c:ingots/draconium'), AB, 'V', 'Wyvern tier, energy core, flight module and reactor parts'),
            listed('draconicevolution:components/awakened_core', 'ingredients', 2, {'consume': True, 'ingredient': tag('c:ingots/draconium_awakened')}, RE, 'V', 'Awakened tier', wrap='fusion'),
            shaped('draconicevolution:tools/dislocator', 0, 0, item('minecraft:blaze_powder'), RM, 'III', 'Bound and player dislocator teleportation'),
        ],
        'removals': [],
        'data': [
            when_item_exists('data/create_dragons_plus/loot_table/blocks/fragile_fluid_tank.json',
                             'create_dragons_plus:fragile_fluid_tank', 'Block is registered only with the optional Sable physics mod'),
            when_item_exists('data/create_dragons_plus/loot_table/blocks/levitite_fragile_fluid_tank.json',
                             'create_dragons_plus:levitite_fragile_fluid_tank', 'Block is registered only with the optional Sable physics mod'),
            without_values('data/industrialforegoing/curios/entities/entities.json', 'slots', ['example', 'feet'],
                           'Curios slot types that no selected mod registers'),
        ],
    },
    'qol': {
        'script': 'entrelumen_qol_balance.js',
        'tag': 'ENTRELUMEN_QOL_BALANCE',
        'namespaces': {'easy_villagers', 'enderstorage', 'codechickenlib'},
        'changes': [
            shaped('easy_villagers:iron_farm', 0, 0, tag('c:glass_panes/colorless'), LM, 'II', 'Compact golem iron farm'),
            shaped('easy_villagers:auto_trader', 0, 0, tag('c:glass_panes/colorless'), RM, 'III', 'Automated villager trading'),
            shaped('enderstorage:ender_chest', 0, 0, item('minecraft:blaze_rod'), RM, 'III', 'Cross-dimension shared item storage',
                   alternates=['enderstorage:recolour_ender_chest']),
            shaped('enderstorage:ender_tank', 0, 0, item('minecraft:blaze_rod'), RM, 'III', 'Cross-dimension shared fluid storage',
                   alternates=['enderstorage:recolour_ender_tank']),
            shaped('enderstorage:ender_pouch', 0, 0, item('minecraft:blaze_powder'), RM, 'III', 'Remote access to a shared frequency',
                   alternates=['enderstorage:recolour_ender_pouch']),
        ],
        'removals': [],
    },
}


def read(path: Path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def lock_entries():
    lock = read(ROOT / 'catalog/curated.json')
    paths = read(ROOT / 'catalog/local-paths.json')
    return lock, paths


def recipe_id_from_name(name):
    match = re.match(r'data/([^/]+)/recipes?/(.+)\.json$', name)
    return f'{match.group(1)}:{match.group(2)}' if match else None


def outputs(recipe):
    found = set()

    def collect(value):
        if isinstance(value, dict):
            for key in ('id', 'item'):
                if isinstance(value.get(key), str):
                    found.add(value[key])
        elif isinstance(value, list):
            for child in value:
                collect(child)

    body = recipe.get('recipe') if isinstance(recipe.get('recipe'), dict) else recipe
    for key in ('result', 'output', 'results', 'outputs'):
        if key in body:
            collect(body[key])
    if isinstance(body.get('result'), dict) and isinstance(body['result'].get('result_item'), dict):
        collect(body['result']['result_item'])
    return found


def load_recipes():
    """All native recipes from locked JARs: {id: (json, filename, sha256)}."""
    lock, paths = lock_entries()
    recipes, sources = {}, {}
    for entry in lock['mods']:
        path = Path(paths[entry['filename']])
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                rid = recipe_id_from_name(name)
                if rid is None:
                    continue
                try:
                    recipes.setdefault(rid, (json.loads(jar.read(name)), entry['filename']))
                except (ValueError, UnicodeDecodeError):
                    continue
        sources[entry['filename']] = entry['sha256']
    return recipes, sources, lock


def inner(recipe):
    if 'pattern' in recipe:
        return recipe
    if isinstance(recipe.get('recipe'), dict) and 'pattern' in recipe['recipe']:
        return recipe['recipe']
    raise AssertionError('Expected a shaped recipe body')


def transform(change, original):
    result = copy.deepcopy(original)
    add = change['add']
    if change['op'] == 'slot':
        craft = inner(result)
        pattern = craft['pattern']
        row, col = change['row'], change['col']
        assert row < len(pattern) and col < len(pattern[row]), f"{change['id']}: slot outside native pattern"
        symbol = pattern[row][col]
        if change['expect'] is None:
            assert symbol == ' ', f"{change['id']}: intended empty slot changed"
        else:
            assert craft['key'][symbol] == change['expect'], f"{change['id']}: native ingredient changed"
            assert sum(line.count(symbol) for line in pattern) > 1, f"{change['id']}: unique ingredient would be lost"
        letter = next(c for c in 'ZYXWQ' if c not in craft['key'] and all(c not in line for line in pattern))
        widths = [len(line) for line in pattern]
        craft['pattern'][row] = pattern[row][:col] + letter + pattern[row][col + 1:]
        craft['key'][letter] = {'item': add}
        assert [len(line) for line in craft['pattern']] == widths
        reverse = copy.deepcopy(result)
        body = inner(reverse)
        body['pattern'][row] = body['pattern'][row][:col] + symbol + body['pattern'][row][col + 1:]
        del body['key'][letter]
        assert reverse == original, f"{change['id']}: an unrelated native field changed"
    elif change['op'] == 'list':
        values = result[change['field']]
        index = change['index']
        assert values[index] == change['expect'], f"{change['id']}: native list element changed"
        assert values.count(change['expect']) > 1, f"{change['id']}: unique list element would be lost"
        element = {'item': add}
        if change['wrap'] == 'fusion':
            element = {'consume': True, 'ingredient': element}
        values[index] = element
        reverse = copy.deepcopy(result)
        reverse[change['field']][index] = change['expect']
        assert reverse == original, f"{change['id']}: an unrelated native field changed"
    else:
        raise AssertionError(f"Unknown operation {change['op']}")
    assert outputs(result) == outputs(original) and result['type'] == original['type']
    return result


def component_sources():
    design = read(DESIGN)
    return {p['output']['id']: [i['id'] for i in p['recipe']['inputs']] for p in design['projects']}, \
        {p['output']['id']: p.get('act') for p in design['projects']}


ACTS = {'I': 1, 'II': 2, 'III': 3, 'IV': 4, 'V': 5, 'VI': 6}


def check_component(component, namespaces, sources, acts, producers, recipes):
    if component in STAGE_MATERIALS:
        makers = [rid for rid in producers.get(component, ())
                  if not any(i.split(':', 1)[0] in namespaces for i in ingredient_items(recipes[rid][0]))]
        assert makers, f'No independent native producer for stage material {component}'
        return STAGE_MATERIALS[component]
    assert component in sources, f'Uncraftable integration component: {component}'
    seen = set()

    def visit(item_id):
        assert item_id.split(':', 1)[0] not in namespaces, f'Family item needed for its own gate: {item_id}'
        if item_id in seen or item_id not in sources:
            return
        seen.add(item_id)
        for child in sources[item_id]:
            visit(child)

    visit(component)
    return {1: 'I', 2: 'II', 3: 'III', 4: 'IV', 5: 'V', 6: 'VI'}[acts[component]]


def ingredient_items(recipe):
    items = set()

    def walk(value):
        if isinstance(value, dict):
            if isinstance(value.get('item'), str):
                items.add(value['item'])
            for key, child in value.items():
                if key not in ('result', 'output', 'results', 'outputs'):
                    walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(recipe)
    return items


def build(name):
    family = FAMILIES[name]
    recipes, sources, lock = load_recipes()
    producers = {}
    for rid, (recipe, _) in recipes.items():
        for out in outputs(recipe):
            producers.setdefault(out, []).append(rid)
    comp_sources, comp_acts = component_sources()
    rows, used_files = [], {}
    ids = [c['id'] for c in family['changes']] + list(family['removals'])
    assert len(ids) == len(set(ids)), 'Duplicate recipe ID in family specification'
    for change in family['changes']:
        assert change['id'] in recipes, f"Missing native recipe {change['id']}"
        original, filename = recipes[change['id']]
        used_files[filename] = sources[filename]
        stage = check_component(change['add'], family['namespaces'], comp_sources, comp_acts, producers, recipes)
        assert stage == change['act'], f"{change['id']}: declared act {change['act']} differs from material {stage}"
        result = transform(change, original)
        native_outputs = outputs(original)
        assert len(native_outputs) == 1, f"{change['id']}: expected one native output, got {native_outputs}"
        (out,) = native_outputs
        alternates = sorted(set(producers.get(out, ())) - {change['id']} - set(change['alternates']))
        assert not alternates, f"{change['id']}: alternate native route to {out}: {alternates}"
        row = {'id': change['id'], 'output': out, 'component': change['add'], 'act': change['act'], 'json': result}
        if change['op'] == 'list':
            row['field'] = change['field']
        rows.append(row)
    removals = []
    for rid in family['removals']:
        assert rid in recipes, f'Missing native recipe to remove {rid}'
        used_files[recipes[rid][1]] = sources[recipes[rid][1]]
        removals.append(rid)
    return rows, removals, dict(sorted(used_files.items())), len(recipes)


def data_files():
    lock, paths = lock_entries()
    found = {}
    for entry in lock['mods']:
        with zipfile.ZipFile(Path(paths[entry['filename']])) as jar:
            for name in jar.namelist():
                if name.startswith('data/') and name.endswith('.json'):
                    found.setdefault(name, []).append((entry['filename'], entry['sha256'], jar.read(name)))
    return found


def build_data(name, found=None):
    """Return {path below pack/kubejs/data: JSON text} for this family's upstream data overrides."""
    family = FAMILIES[name]
    if not family.get('data'):
        return {}
    found = found if found is not None else data_files()
    outputs_by_path = {}
    for spec in family['data']:
        sources = found.get(spec['path'], [])
        assert len(sources) == 1, f"{spec['path']}: expected one pinned upstream file, found {len(sources)}"
        original = json.loads(sources[0][2])
        result = copy.deepcopy(original)
        if spec['op'] == 'item_exists':
            assert 'neoforge:conditions' not in original, f"{spec['path']}: already conditional"
            result = {'neoforge:conditions': [{'type': 'neoforge:item_exists', 'item': spec['item']}], **original}
            reverse = {k: v for k, v in result.items() if k != 'neoforge:conditions'}
        elif spec['op'] == 'remove_values':
            values = result[spec['field']]
            assert all(values.count(v) == 1 for v in spec['values']), f"{spec['path']}: values changed upstream"
            result[spec['field']] = [v for v in values if v not in spec['values']]
            reverse = copy.deepcopy(result)
            reverse[spec['field']] = [v for v in original[spec['field']]]
        else:
            raise AssertionError(f"Unknown data operation {spec['op']}")
        assert reverse == original, f"{spec['path']}: unrelated upstream data changed"
        outputs_by_path[spec['path'][len('data/'):]] = json.dumps(result, indent=2, ensure_ascii=False) + '\n'
    return outputs_by_path


RUNTIME = '''
ServerEvents.recipes(event => {
  var missing = [];
  ROWS.forEach(row => {
    if (!event.containsRecipe({id: row.id})) missing.push({recipe: row.id, cause: 'native recipe absent'});
    if (!Item.exists(row.component)) missing.push({recipe: row.id, cause: 'component absent', component: row.component});
  });
  REMOVALS.forEach(id => { if (!event.containsRecipe({id: id})) missing.push({recipe: id, cause: 'native recipe absent'}); });
  if (missing.length) {
    console.error('[TAG] ' + JSON.stringify({status: 'failed-preflight', signature: SIGNATURE, missing: missing}));
    throw new Error('TAG preflight failed; native recipes were not changed');
  }
  ROWS.forEach(row => {
    event.remove({id: row.id});
    event.custom(row.json).id(row.id);
  });
  REMOVALS.forEach(id => event.remove({id: id}));
  console.info('[TAG] ' + JSON.stringify({status: 'registered', signature: SIGNATURE, changed: ROWS.length, removed: REMOVALS.length}));
});
function FIELDCHECK(event, row) {
  // Machine recipes that do not expose getIngredients(): test the recipe's own public list field.
  var found = 0;
  try {
    var stack = Item.of(row.component);
    event.forEachRecipe({id: row.id}, holder => {
      holder.value()[row.field].forEach(ingredient => { if (ingredient.test(stack)) found++; });
    });
  } catch (error) {
    console.warn('[TAG] ' + JSON.stringify({status: 'field-check-error', recipe: row.id, error: String(error)}));
    return -1;
  }
  return found;
}
ServerEvents.afterRecipes(event => {
  var failed = [];
  ROWS.forEach(row => {
    var loaded = event.countRecipes({id: row.id, output: row.output});
    var staged = event.countRecipes({id: row.id, input: row.component});
    if (staged === 0 && row.field) staged = FIELDCHECK(event, row);
    if (loaded !== 1 || staged !== 1) failed.push({recipe: row.id, loadedOutput: loaded, stagedInput: staged});
  });
  REMOVALS.forEach(id => { var left = event.countRecipes({id: id}); if (left !== 0) failed.push({recipe: id, remaining: left}); });
  console.info('[TAG] ' + JSON.stringify({status: failed.length ? 'failed-loaded-check' : 'loaded-ingredient-check',
    signature: SIGNATURE, checked: ROWS.length + REMOVALS.length, failed: failed}));
});
'''


def render(name, rows, removals, used_files):
    family = FAMILIES[name]
    prefix = 'entrelumen' + ''.join(part.title() for part in name.split('_'))
    payload = json.dumps({'rows': rows, 'removals': removals}, ensure_ascii=False, separators=(',', ':'))
    signature = hashlib.sha256(payload.encode('utf-8')).hexdigest()
    body = (RUNTIME.replace('FIELDCHECK', prefix + 'FieldCheck').replace('ROWS', prefix + 'Rows').replace('REMOVALS', prefix + 'Removals')
            .replace('SIGNATURE', prefix + 'Signature').replace('TAG', family['tag']))
    return (f'// Generated by tools/generate_family_balance.py --family {name}; native acquisition only.\n'
            '// No team, act, use, dimension, origin, gift or reward checks.\n'
            f'const {prefix}Signature = {json.dumps(signature)};\n'
            f'const {prefix}Sources = {json.dumps(used_files, separators=(",", ":"))};\n'
            f'const {prefix}Rows = {json.dumps(rows, ensure_ascii=False, separators=(",", ":"))};\n'
            f'const {prefix}Removals = {json.dumps(removals)};\n' + body)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--family', choices=sorted(FAMILIES), action='append')
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for name in args.family or sorted(FAMILIES):
        rows, removals, used_files, audited = build(name)
        target = SCRIPTS / FAMILIES[name]['script']
        output = render(name, rows, removals, used_files)
        if args.write:
            target.write_text(output, encoding='utf-8', newline='\n')
        elif not target.exists() or target.read_text(encoding='utf-8') != output:
            raise SystemExit(f'Generated {target.name} is stale; run --write')
        overrides = build_data(name)
        for relative, text in overrides.items():
            destination = PACK_DATA / relative
            if args.write:
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(text, encoding='utf-8', newline='\n')
            elif not destination.is_file() or destination.read_text(encoding='utf-8') != text:
                raise SystemExit(f'Generated data override {relative} is stale; run --write')
        print(json.dumps({'family': name, 'status': 'static-PASS', 'nativeRecipesIndexed': audited,
                          'changed': len(rows), 'removed': len(removals), 'dataOverrides': len(overrides),
                          'acts': sorted({r['act'] for r in rows}), 'runtime': 'pending'}))


if __name__ == '__main__':
    main()
