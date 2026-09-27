"""Generate native resource acquisition changes from pinned upstream JARs.

Since Elias's playtest of 24 September 2026 (docs/design/recipe-design-rules.md) a component closes a
few milestones: the three Botany Pots Tiers upgrades carry the act components and every per-colour tier
recipe consumes the upgrade item instead of its old catalyst; hopper pots, Modular Bees parts and add-ons
keep their native recipes; the JAMD portals take their component on the drawing's axis."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'pack/kubejs/server_scripts/entrelumen_resource_balance.js'
TIERS = {'elite': ('minecraft:ender_pearl', 'entrelumen:propagation_core', 'minecraft:iron_block', 'minecraft:iron_ingot'),
         'ultra': ('minecraft:nether_star', 'entrelumen:ecosystem_capsule', 'minecraft:diamond_block', 'minecraft:diamond'),
         'mega': ('minecraft:enchanted_golden_apple', 'entrelumen:renewal_engine', 'minecraft:netherite_block', 'minecraft:netherite_ingot')}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


JAMD = {'jamd:portal_block': 'entrelumen:calibration_frame', 'jamd:nether_portal_block': 'entrelumen:containment_seal',
        'jamd:end_portal_block': 'entrelumen:horizon_chart'}
IRONWOOD = 'twilightforest:ironwood_ingot'  # act IV material (tools/generate_family_balance.py STAGE_MATERIALS)


def replace_cells(recipe, cells, expect, add):
    """Put `add` in the named cells, which hold the repeated ingredient `expect`."""
    pattern = recipe['pattern']
    symbols = {pattern[r][c] for r, c in cells}
    assert len(symbols) == 1, 'cells hold different ingredients'
    (symbol,) = symbols
    assert recipe['key'][symbol] == expect, 'native ingredient changed'
    assert sum(line.count(symbol) for line in pattern) > len(cells), 'unique ingredient would be lost'
    letter = next(c for c in 'ZYXVQ' if c not in recipe['key'])
    for r, c in cells:
        pattern[r] = pattern[r][:c] + letter + pattern[r][c + 1:]
    recipe['key'][letter] = {'item': add}


def symmetric(recipe):
    def kind(symbol):
        return ' ' if symbol == ' ' else json.dumps(recipe['key'][symbol], sort_keys=True)
    return all([kind(c) for c in row] == [kind(c) for c in reversed(row)] for row in recipe['pattern'])


def transform(rid, original):
    recipe = copy.deepcopy(original)
    output = recipe.get('result', {}).get('id', '')
    reason = None
    if rid in JAMD:
        # The component on the axis, above the pickaxe: one of the eight frame blocks gives way.
        replace_cells(recipe, [(0, 1)], recipe['key']['O'], JAMD[rid])
        reason = 'dimensional_portal_milestone'
    elif rid.startswith('botanypotstiers:') and recipe['type'] == 'minecraft:crafting_shaped':
        tier = next((t for t in TIERS if output.startswith('botanypotstiers:' + t + '_')), None)
        if tier:
            catalyst, component, block, material = TIERS[tier]
            upgrade = f'botanypotstiers:{tier}_upgrade'
            found = False
            for ingredient in recipe['key'].values():
                if ingredient.get('item') == catalyst:
                    ingredient['item'] = component if output == upgrade else upgrade
                    found = True
                elif ingredient.get('item') == block:
                    ingredient['item'] = material
            if not found:
                raise ValueError('Unclassified tier crafting route: ' + rid)
            if output == upgrade:
                # The native upgrade is a row of blocks and a catalyst (BBA); the component goes in the middle.
                assert recipe['pattern'] == ['BBA'], rid
                recipe['pattern'] = ['BAB']
                reason = 'tier_upgrade_milestone'
            else:
                reason = 'tier_pot_consumes_upgrade'
    elif rid == 'modularbees:modular_beehive_core':
        # One core per modular hive; its parts and add-ons stay native. Two ironwood ingots, top corners.
        replace_cells(recipe, [(0, 0), (0, 2)], {'item': 'modularbees:scented_plank'}, IRONWOOD)
        reason = 'modular_core_act_material'
    if not reason:
        return None
    assert recipe['type'] == original['type'] and recipe['result'] == original['result']
    for key in ('bookshelf:load_conditions', 'neoforge:conditions'):
        assert recipe.get(key) == original.get(key)
    if 'pattern' in original and symmetric(original):
        assert symmetric(recipe), f'{rid}: the drawing lost its symmetry'
    return {'id': rid, 'reason': reason, 'json': recipe}


def build():
    paths = read(ROOT/'catalog/local-paths.json'); lock = read(ROOT/'catalog/curated.json')
    inventory = read(ROOT/'docs/design/resource-recipe-inventory.json')
    expected = {r['id']:r['sourceSha256'] for r in inventory['recipes']}
    rows=[]; sources=[]; seen=set()
    for entry in lock['mods']:
        if not {'jamd','botanypots','botanypotstiers','modularbees'} & {m['id'] for m in entry['metadata']['mods']}:continue
        data=Path(paths[entry['filename']]).read_bytes()
        assert hashlib.sha256(data).hexdigest()==entry['sha256'], 'Pinned hash changed'
        sources.append({'filename':entry['filename'],'sha256':entry['sha256']})
        with zipfile.ZipFile(paths[entry['filename']]) as jar:
            for name in sorted(jar.namelist()):
                if not name.startswith('data/') or '/recipe/' not in name or not name.endswith('.json'):continue
                ns,local=name[5:-5].split('/recipe/',1);rid=ns+':'+local;raw=jar.read(name)
                if rid in expected:assert hashlib.sha256(raw).hexdigest()==expected[rid], 'Inventory stale: '+rid
                seen.add(rid);row=transform(rid,json.loads(raw))
                if row:rows.append(row)
    assert set(expected)<=seen
    assert all(any(r['id'] == 'botanypotstiers:' + t + '_upgrade' and r['reason'] == 'tier_upgrade_milestone' for r in rows) for t in TIERS)
    assert len({r['id'] for r in rows})==len(rows)
    return sorted(rows,key=lambda r:r['id']),sources


RUNTIME = '''
ServerEvents.recipes(event => {
  // Conditions stay native. Inactive optional/color routes remain inactive.
  entrelumenResourceRecipes.forEach(row => {
    if (event.containsRecipe({id: row.id})) {
      event.remove({id: row.id});
      event.custom(row.json).id(row.id);
    }
  });
});
ServerEvents.afterRecipes(event => {
  // One pass over the loaded recipes. This also runs on the server thread for /reload, where two
  // filtered scans of every recipe per row (about 30 s on the full pack) left no margin under the
  // 60 s watchdog. Each tracked row is a vanilla crafting or smithing recipe; its loaded result is
  // read through the recipe's own codec.
  // Plain var: Rhino treats block-scoped const inside repeated callbacks unreliably.
  var ops = Java.loadClass('com.mojang.serialization.JsonOps').INSTANCE;
  var expected = {};
  entrelumenResourceRecipes.forEach(row => { expected[row.id] = row.json.result.id; });
  var counts = {};
  var outputs = {};
  event.forEachRecipe('*', holder => {
    var id = String(holder.getOrCreateId());
    if (expected[id] === undefined) return;
    counts[id] = (counts[id] || 0) + 1;
    try {
      var encoded = JSON.parse(String(holder.getSerializer().codec().codec()
        .encodeStart(ops, holder.getRecipe()).getOrThrow()));
      outputs[id] = encoded.result ? String(encoded.result.id) : 'no result';
    } catch (error) {
      outputs[id] = 'codec: ' + error;
    }
  });
  var failed = [];
  var mismatches = [];
  var active = 0;
  entrelumenResourceRecipes.forEach(row => {
    if (!counts[row.id]) return;
    active++;
    if (counts[row.id] !== 1 || outputs[row.id] !== row.json.result.id) {
      failed.push(row.id);
      if (mismatches.length < 3) mismatches.push({id: row.id, count: counts[row.id], output: outputs[row.id]});
    }
  });
  console.info('[ENTRELUMEN_RESOURCE_BALANCE] ' + JSON.stringify({signature: entrelumenResourceSignature,
    status: failed.length ? 'FAIL' : 'loaded-output-check-only', active: active, failed: failed, mismatches: mismatches,
    note: 'Output presence does not prove ingredients, NBT preservation or narrative stage gating'}));
});
'''


def self_test():
    smithing = {'type': 'minecraft:smithing_transform', 'template': {'item': 'minecraft:netherite_upgrade_smithing_template'},
                'base': {'item': 'modularbees:electrode_gold'}, 'addition': {'item': 'minecraft:netherite_block'},
                'result': {'id': 'modularbees:electrode_netherite', 'count': 1}}
    assert transform('modularbees:electrode_netherite', smithing) is None
    plain = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:clay_ball'}],
             'result': {'id': 'botanypots:terracotta_botany_pot', 'count': 1}}
    assert transform('botanypots:test', plain) is None
    hopper = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:hopper'},
              {'item': 'botanypots:terracotta_botany_pot'}], 'result': {'id': 'botanypots:terracotta_hopper_botany_pot', 'count': 1}}
    assert transform('botanypots:botanypots/crafting/terracotta_hopper_botany_pot', hopper) is None
    wax = {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'botanypotstiers:elite_terracotta_botany_pot'}, {'item': 'minecraft:honeycomb'}],
           'result': {'id': 'botanypotstiers:elite_terracotta_waxed_botany_pot', 'count': 1}}
    assert transform('botanypotstiers:pots/wax', wax) is None
    upgrade = {'type': 'minecraft:crafting_shaped', 'pattern': ['BBA'], 'key': {'B': {'item': 'minecraft:iron_block'},
               'A': {'item': 'minecraft:ender_pearl'}}, 'result': {'id': 'botanypotstiers:elite_upgrade', 'count': 1}}
    changed = transform('botanypotstiers:elite_upgrade', upgrade)['json']
    assert changed['pattern'] == ['BAB'] and changed['key']['A'] == {'item': 'entrelumen:propagation_core'}
    assert changed['key']['B'] == {'item': 'minecraft:iron_ingot'}
    pot = {'type': 'minecraft:crafting_shaped', 'pattern': ['MAM', 'MPM', 'BMB'],
           'key': {'M': {'item': 'minecraft:terracotta'}, 'A': {'item': 'minecraft:nether_star'},
                   'B': {'item': 'minecraft:diamond_block'}, 'P': {'tag': 'botanypotstiers:elite_botany_pots'}},
           'result': {'id': 'botanypotstiers:ultra_terracotta_botany_pot', 'count': 1}}
    changed = transform('botanypotstiers:pots/ultra_terracotta_botany_pot', pot)['json']
    assert changed['key']['A'] == {'item': 'botanypotstiers:ultra_upgrade'} and changed['key']['B'] == {'item': 'minecraft:diamond'}
    assert 'entrelumen' not in json.dumps(changed), 'a per-colour pot must not take the component itself'
    full = {'type': 'minecraft:crafting_shaped', 'pattern': ['OOO', 'OPO', 'OOO'],
            'key': {'O': {'item': 'minecraft:obsidian'}, 'P': {'item': 'minecraft:diamond_pickaxe'}},
            'result': {'id': 'jamd:portal_block', 'count': 1}}
    changed = transform('jamd:portal_block', full)['json']
    assert changed['key']['P'] == full['key']['P'] and changed['pattern'] == ['OZO', 'OPO', 'OOO']
    assert symmetric(changed)
    print('PASS synthetic transformation checks: native smithing, plain, hopper and wax routes; upgrade drawn BAB with the'
          ' component; per-colour pots take the upgrade; JAMD component on the axis')


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--write',action='store_true');ap.add_argument('--check',action='store_true');ap.add_argument('--self-test',action='store_true');args=ap.parse_args()
    if args.self_test:self_test()
    rows,sources=build();payload=json.dumps(rows,ensure_ascii=False,separators=(',',':'))
    signature=hashlib.sha256(payload.encode()).hexdigest()
    text='// Generated native resource balance; no campaign/use/gift restrictions.\nconst entrelumenResourceSignature = '+json.dumps(signature)+';\nconst entrelumenResourceSources = '+json.dumps(sources)+';\nconst entrelumenResourceRecipes = '+payload+';\n'+RUNTIME
    if args.write:TARGET.write_text(text,encoding='utf-8')
    if args.check and TARGET.read_text(encoding='utf-8-sig')!=text:raise SystemExit('Stale generated resource balance')
    print(json.dumps({'status':'static-PASS','changed':len(rows),'byReason':{reason:sum(r['reason']==reason for r in rows) for reason in sorted({r['reason'] for r in rows})},'runtime':'pending','narrativeActGating':False}))

if __name__=='__main__':main()
