"""Generate native resource acquisition changes from pinned upstream JARs."""
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


def add_component(recipe, component):
    if recipe['type'] == 'minecraft:crafting_shapeless':
        if len(recipe['ingredients']) >= 9: raise ValueError('shapeless grid full')
        recipe['ingredients'].append({'item': component})
    elif recipe['type'] == 'minecraft:crafting_shaped':
        pattern = recipe['pattern']; flat = ''.join(pattern)
        symbol = next(c for c in 'ZYXVQ' if c not in recipe['key'])
        if ' ' in flat: position = flat.index(' ')
        else:
            # Replace one repeated structural material, never the unique machine/pot input.
            repeated = [c for c in flat if flat.count(c) > 1]
            if not repeated: raise ValueError('No structural slot available')
            position = flat.index(max(set(repeated), key=lambda c: (flat.count(c), c)))
        width = len(pattern[0]); line, col = divmod(position, width)
        pattern[line] = pattern[line][:col] + symbol + pattern[line][col+1:]
        recipe['key'][symbol] = {'item': component}
    else: raise ValueError('Unsupported native recipe')


def transform(rid, original):
    recipe = copy.deepcopy(original)
    output = recipe.get('result', {}).get('id', '')
    reason = None
    if rid.startswith('jamd:'):
        component = {'jamd:portal_block':'entrelumen:calibration_frame', 'jamd:nether_portal_block':'entrelumen:containment_seal', 'jamd:end_portal_block':'entrelumen:horizon_chart'}[rid]
        add_component(recipe, component); reason = 'dimensional_material_and_integration'
    elif rid.startswith('botanypots:') and 'hopper' in output and recipe['type'].startswith('minecraft:crafting_'):
        # Same-color/wax conversions already carry an upgraded pot; charge only initial hopper acquisition.
        encoded = json.dumps(recipe.get('ingredients', recipe.get('key', {})))
        if 'minecraft:hopper' in encoded:
            add_component(recipe, 'entrelumen:calibration_frame'); reason = 'hopper_automation'
    elif rid.startswith('botanypotstiers:'):
        tier = next((t for t in TIERS if output.startswith('botanypotstiers:' + t + '_')), None)
        if tier and recipe['type'] == 'minecraft:crafting_shaped':
            catalyst, component, block, material = TIERS[tier]
            found = False
            for ingredient in recipe['key'].values():
                if ingredient.get('item') == catalyst:
                    ingredient['item'] = component; found = True
                elif ingredient.get('item') == block: ingredient['item'] = material
            if not found: raise ValueError('Unclassified tier crafting route: ' + rid)
            reason = 'tier_catalyst_including_useOn_upgrade'
        elif tier and recipe['type'] == 'minecraft:crafting_shapeless':
            # Same-tier hopper/wax routes preserve tier access and serializer.
            if any(i.get('item') == 'minecraft:hopper' for i in recipe['ingredients']):
                add_component(recipe, 'entrelumen:calibration_frame'); reason = 'same_tier_hopper_automation'
    elif rid.startswith('modularbees:'):
        local = output.split(':')[-1]
        component = None
        if local in {'modular_beehive_part', 'modular_centrifuge_part', 'modular_beehive_core', 'modular_centrifuge_core'}: component = 'entrelumen:ecosystem_capsule'
        elif local.startswith('me_'): component = 'entrelumen:routing_matrix'
        elif 'overclocker' in local or local == 'modular_beehive_stacker': component = 'entrelumen:power_regulator'
        elif local == 'modular_dragon_hive': component = 'entrelumen:renewal_engine'
        elif local == 'electrode_netherite':
            assert recipe['type'] == 'minecraft:smithing_transform'
            recipe['template'] = {'item':'entrelumen:containment_seal'}
            reason = 'highest_electrode_native_smithing'
        if component:
            add_component(recipe, component); reason = 'modular_system_integration'
    if not reason: return None
    assert recipe['type'] == original['type'] and recipe['result'] == original['result']
    for key in ('bookshelf:load_conditions', 'neoforge:conditions'):
        assert recipe.get(key) == original.get(key)
    return {'id':rid, 'reason':reason, 'json':recipe}


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
    assert all(any(r['id']=='botanypotstiers:'+t+'_upgrade' for r in rows) for t in TIERS)
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
    original={'type':'minecraft:smithing_transform','template':{'item':'minecraft:netherite_upgrade_smithing_template'},
              'base':{'item':'modularbees:electrode_gold'},'addition':{'item':'minecraft:netherite_block'},
              'result':{'id':'modularbees:electrode_netherite','count':1}}
    changed=transform('modularbees:electrode_netherite',original)['json']
    assert changed['base']==original['base'] and changed['addition']==original['addition']
    assert original['template']['item']=='minecraft:netherite_upgrade_smithing_template'
    assert changed['type']=='minecraft:smithing_transform'
    plain={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:clay_ball'}],
           'result':{'id':'botanypots:terracotta_botany_pot','count':1}}
    assert transform('botanypots:test',plain) is None
    wax={'type':'minecraft:crafting_shapeless','ingredients':[{'item':'botanypotstiers:elite_terracotta_botany_pot'},{'item':'minecraft:honeycomb'}],
         'result':{'id':'botanypotstiers:elite_terracotta_waxed_botany_pot','count':1}}
    assert transform('botanypotstiers:pots/wax',wax) is None
    full={'type':'minecraft:crafting_shaped','pattern':['OOO','OPO','OOO'],
          'key':{'O':{'item':'minecraft:obsidian'},'P':{'item':'minecraft:diamond_pickaxe'}},
          'result':{'id':'jamd:portal_block','count':1}}
    changed=transform('jamd:portal_block',full)['json']
    assert changed['key']['P']==full['key']['P'] and changed['pattern'][1]=='OPO'
    assert sum(line.count('Z') for line in changed['pattern'])==1
    print('PASS synthetic transformation checks: smithing base/addition, plain/wax routes, unique tool preserved')


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
