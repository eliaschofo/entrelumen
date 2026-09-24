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


def disabled(path, why):
    """Disable a broken upstream data file with a neoforge:false condition."""
    return {'path': path, 'op': 'disable', 'why': why}


def renamed_key(path, field, old, new, why):
    """Rename one map key in an upstream data file, keeping its value and position."""
    return {'path': path, 'op': 'rename_key', 'field': field, 'old': old, 'new': new, 'why': why}


def with_value(path, field, value, why, *, limit):
    """Append one element to a list in an upstream data file (bounded by the mod's own slot limit)."""
    return {'path': path, 'op': 'append', 'field': field, 'value': value, 'limit': limit, 'why': why}


def without_values(path, field, values, why):
    """Drop exact values from a list in an upstream data file."""
    return {'path': path, 'op': 'remove_values', 'field': field, 'values': list(values), 'why': why}


def tag(value):
    return {'tag': value}


def campaign_tier(tier, trigger, why):
    """Replace an Apotheosis World Tier advancement's criteria with one campaign criterion."""
    return {'path': f'data/apotheosis/advancement/progression/{tier}.json', 'op': 'campaign_tier',
            'trigger': trigger, 'description': f'entrelumen.apotheosis.tier.{tier}.desc', 'why': why}


def augmented(modifier, *, inverse=False):
    """Point one Apothic Spawners modifier (or its quartz inverse) at an ENTRELUMEN augment.

    Apotheosis ships the same paths disabled; the Apothic Spawners original is the only source."""
    folder = '_inverse/' if inverse else ''
    return {'path': f'data/apothic_spawners/recipe/spawner_modifiers/{folder}{modifier}.json', 'op': 'mainhand',
            'item': f'entrelumen:augment_{modifier}', 'source': 'ApothicSpawners-',
            'why': 'Reverse with quartz in the offhand' if inverse else 'Apply'}


def recipe(recipe_id, pattern, key, act, why, *, count=1):
    """A new shaped recipe for an ENTRELUMEN block or item, written as pack data."""
    return {'id': recipe_id, 'pattern': pattern, 'key': key, 'count': count, 'act': act, 'why': why}


LM = 'entrelumen:living_matrix'
PR, RM, HC = 'entrelumen:power_regulator', 'entrelumen:routing_matrix', 'entrelumen:handling_core'
EC, SL, HZ = 'entrelumen:ecosystem_capsule', 'entrelumen:spectral_lens', 'entrelumen:horizon_chart'
CS, RE, AB = 'entrelumen:containment_seal', 'entrelumen:renewal_engine', 'entrelumen:ark_bus'
PC, IS = 'entrelumen:propagation_core', 'entrelumen:inventory_sensor'
CF = 'entrelumen:calibration_frame'

# Spawner augments: act, the Apothic Spawners original item kept as the medallion's core, the
# Apotheosis rarity material and the ENTRELUMEN component. Rarity follows World Tier drops:
# uncommon (Haven+), rare (Haven+, common from Frontier) and epic (from Ascent, Act IV).
AUGMENTS = {
    'min_delay': ('III', item('minecraft:sugar'), 'apotheosis:timeworn_fabric', PR),
    'max_delay': ('III', item('minecraft:clock'), 'apotheosis:timeworn_fabric', PR),
    'spawn_range': ('III', item('minecraft:piston'), 'apotheosis:timeworn_fabric', RM),
    'player_range': ('III', item('minecraft:prismarine_crystals'), 'apotheosis:timeworn_fabric', IS),
    'silent': ('III', tag('minecraft:wool'), 'apotheosis:timeworn_fabric', HC),
    'youthful': ('III', item('minecraft:turtle_egg'), 'apotheosis:timeworn_fabric', PC),
    'spawn_count': ('IV', item('minecraft:fermented_spider_eye'), 'apotheosis:luminous_crystal_shard', EC),
    'max_nearby': ('IV', item('minecraft:ghast_tear'), 'apotheosis:luminous_crystal_shard', EC),
    'initial_health': ('IV', item('minecraft:pointed_dripstone'), 'apotheosis:luminous_crystal_shard', SL),
    'burning': ('IV', item('minecraft:campfire'), 'apotheosis:luminous_crystal_shard', CS),
    'echoing': ('V', item('minecraft:echo_shard'), 'apotheosis:arcane_sands', RE),
    'ignore_conditions': ('V', item('minecraft:conduit'), 'apotheosis:arcane_sands', RE),
    'ignore_light': ('V', item('minecraft:soul_lantern'), 'apotheosis:arcane_sands', RE),
    'ignore_players': ('V', item('minecraft:nether_star'), 'apotheosis:arcane_sands', AB),
    'no_ai': ('V', item('minecraft:chorus_fruit'), 'apotheosis:arcane_sands', AB),
    'redstone_control': ('V', item('minecraft:comparator'), 'apotheosis:arcane_sands', AB),
}


def augment_recipe(modifier):
    act, core, material, component = AUGMENTS[modifier]
    return recipe(f'entrelumen:augment_{modifier}', ['SDS', 'MOM', 'SKS'],
                  {'S': item('create:copper_sheet'), 'D': item('apotheosis:gem_dust'), 'M': item(material),
                   'O': core, 'K': item(component)}, act,
                  'Copper medallion around the original Apothic Spawners item')


RUNE_RECIPES = [f'apotheosis:{name}' for name in (
    'spawner_rune', 'infused_spawner_rune', 'burning_spawner_rune', 'echoing_spawner_rune',
    'ignore_conditions_spawner_rune', 'ignore_light_spawner_rune', 'ignore_players_spawner_rune',
    'initial_health_spawner_rune', 'no_ai_spawner_rune', 'redstone_control_spawner_rune', 'silent_spawner_rune',
    'spawn_range_spawner_rune', 'youthful_spawner_rune', 'frontier_spawner_upgrade_rune',
    'ascent_spawner_upgrade_rune', 'summit_spawner_upgrade_rune', 'pinnacle_spawner_upgrade_rune',
    'fallback/infused_spawner_rune', 'fallback/summit_spawner_upgrade_rune', 'fallback/pinnacle_spawner_upgrade_rune')]
_RUNE_EFFECTS = ('burning', 'echoing', 'ignore_conditions', 'ignore_light', 'ignore_players', 'initial_health',
                 'no_ai', 'redstone_control', 'silent', 'spawn_range', 'youthful')
RUNE_MODIFIERS = ([f'apotheosis:spawner_modifiers/{name}' for name in _RUNE_EFFECTS]
                  + [f'apotheosis:spawner_modifiers/_inverse/{name}' for name in _RUNE_EFFECTS]
                  + [f'apotheosis:spawner_modifiers/tier/{t}' for t in ('frontier', 'ascent', 'summit', 'pinnacle')])

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
            without_values('data/industrialforegoing/curios/entities/entities.json', 'slots', ['example'],
                           'Curios slot type that no selected mod registers (Artifacts registers feet)'),
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
        # The Altar of Peace (companion, Act III reward) replaces the Mega Torch; the rest of
        # Torchmaster (Dread Lamp, Feral Flare Lantern, Frozen Pearl) keeps its native recipes.
        'removals': ['torchmaster:megatorch'],
    },
    'arcane': {
        'script': 'entrelumen_arcane_balance.js',
        'tag': 'ENTRELUMEN_ARCANE_BALANCE',
        'namespaces': {'reliquary', 'theurgy', 'forbidden_arcanus', 'valhelsia_core'},
        'changes': [
            shaped('reliquary:rending_gale', 1, 0, tag('c:ingots/gold'), HZ, 'IV', 'Rending Gale flight'),
            shaped('theurgy:crafting/shaped/sulfuric_flux_emitter', 0, 0, None, CS, 'IV', 'Same-tier sulfur reformation'),
        ],
        'tag_removals': [
            ('block', 'c:ores_in_ground/deepslate', 'forbidden_arcanus:stella_arcanum'),
            ('item', 'c:ores_in_ground/deepslate', 'forbidden_arcanus:stella_arcanum'),
        ],
        'removals': ['reliquary:alkahestry_tome', 'reliquary:uncrafting/spawn_egg'] + [
            f'reliquary:alkahestry/crafting/{name}' for name in (
                'charcoal', 'clay', 'copper_ingot', 'diamond', 'dirt', 'emerald', 'end_stone', 'flint', 'gold_ingot',
                'gravel', 'gunpowder', 'iron_ingot', 'lapis_lazuli', 'nether_star', 'netherrack', 'obsidian', 'sand',
                'sandstone', 'silver_ingot', 'soul_sand', 'steel_ingot', 'tin_ingot')],
        'data': [
            with_value('data/forbidden_arcanus/forbidden_arcanus/hephaestus_forge/ritual/eternal_stella.json', 'inputs',
                       {'amount': 1, 'ingredient': {'item': CS}}, 'Unbreakable-tool modifier', limit=8),  # eight forge pedestals
            renamed_key('data/create_enchantment_industry/data_maps/fluid/unit/experience.json', 'values',
                        'reliquary:xp_juice_still', 'reliquary:xp_still', "Reliquary registers its experience fluid as xp_still"),
            disabled('data/irons_jewelry/loot_table/generate_jewelry_test_materials.json',
                     "Developer test table whose material keys are tags, which the loot codec rejects"),
        ],
    },
    'apotheosis': {
        'script': 'entrelumen_apotheosis_balance.js',
        'tag': 'ENTRELUMEN_APOTHEOSIS_BALANCE',
        'namespaces': {'apotheosis', 'apothic_enchanting', 'apothic_spawners', 'apothic_attributes', 'placebo'},
        'changes': [
            shaped('apothic_enchanting:echoing_sculkshelf', 0, 0, None, SL, 'IV', '80-Eterna sculkshelf'),
            shaped('apothic_enchanting:soul_touched_sculkshelf', 0, 0, None, SL, 'IV', '80-Eterna sculkshelf'),
            shaped('apothic_enchanting:endshelf', 0, 0, item('minecraft:end_stone_bricks'), CS, 'IV',
                   '90-Eterna endshelf and, through it, the pearl endshelf'),
            shaped('apothic_enchanting:draconic_endshelf', 0, 0, None, AB, 'V', '100-Eterna draconic endshelf'),
        ],
        # Apotheosis 8.7 replaces the Apothic Spawners modifiers with rune recipes; the pack keeps the
        # Apothic Spawners set on ENTRELUMEN augments instead, so the runes lose their recipes.
        'removals': RUNE_RECIPES + RUNE_MODIFIERS,
        'data': [
            campaign_tier('haven', 'minecraft:tick', 'Open from the start of every campaign'),
            campaign_tier('frontier', 'minecraft:impossible', 'Granted by the companion after Act II'),
            campaign_tier('ascent', 'minecraft:impossible', 'Granted by the companion after Act III'),
            campaign_tier('summit', 'minecraft:impossible', 'Granted by the companion after Act V'),
            campaign_tier('pinnacle', 'minecraft:impossible', 'Granted by the companion after the Ark activation'),
        ] + [augmented(m) for m in AUGMENTS] + [augmented(m, inverse=True) for m in AUGMENTS],
        'additions': [
            recipe('entrelumen:cartographer_shelf', ['PMP', 'BFB', 'PMP'],
                   {'P': tag('minecraft:planks'), 'M': item('minecraft:map'), 'B': item('minecraft:bookshelf'),
                    'F': item(CF)}, 'II', 'Early Eterna shelf', count=2),
            recipe('entrelumen:patina_shelf', ['OHO', 'HRH', 'OHO'],
                   {'O': item('minecraft:oxidized_copper'), 'H': item('apothic_enchanting:hellshelf'),
                    'R': item(PR)}, 'III', 'Quanta shelf', count=4),
            recipe('entrelumen:lumen_shelf', ['GSG', 'SLS', 'GSG'],
                   {'G': item('minecraft:glowstone'), 'S': item('apothic_enchanting:infused_seashelf'),
                    'L': item(SL)}, 'IV', 'Arcana shelf', count=4),
            recipe('entrelumen:horizon_shelf', ['DHD', 'LRL', 'DHD'],
                   {'D': item('apothic_enchanting:deepshelf'), 'H': item(HZ), 'L': item('entrelumen:lumen_shelf'),
                    'R': item(RE)}, 'V', 'Late shelf with high Eterna, Quanta and Arcana', count=4),
            recipe('entrelumen:atlas_library', ['SAS', 'HEH', 'SRS'],
                   {'S': item(CS), 'A': item(AB), 'H': item('entrelumen:horizon_shelf'),
                    'E': item('apothic_enchanting:ender_library'), 'R': item(RE)}, 'V',
                   'Pooled library beyond the Ender Library'),
        ] + [augment_recipe(m) for m in AUGMENTS],
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


def disabled_placeholder(data):
    return {'type': 'neoforge:false'} in data.get('neoforge:conditions', [])


def build_data(name, found=None):
    """Return {path below pack/kubejs/data: JSON text} for this family's upstream data overrides."""
    family = FAMILIES[name]
    if not family.get('data'):
        return {}
    found = found if found is not None else data_files()
    outputs_by_path = {}
    for spec in family['data']:
        sources = found.get(spec['path'], [])
        if spec.get('source'):
            # Another pinned JAR may ship the same path; it must only be a disabled placeholder.
            others = [s for s in sources if not s[0].startswith(spec['source'])]
            sources = [s for s in sources if s[0].startswith(spec['source'])]
            assert all(disabled_placeholder(json.loads(s[2])) for s in others), \
                f"{spec['path']}: another pinned JAR provides an active version"
        assert len(sources) == 1, f"{spec['path']}: expected one pinned upstream file, found {len(sources)}"
        original = json.loads(sources[0][2])
        result = copy.deepcopy(original)
        if spec['op'] == 'item_exists':
            assert 'neoforge:conditions' not in original, f"{spec['path']}: already conditional"
            result = {'neoforge:conditions': [{'type': 'neoforge:item_exists', 'item': spec['item']}], **original}
            reverse = {k: v for k, v in result.items() if k != 'neoforge:conditions'}
        elif spec['op'] == 'disable':
            assert 'neoforge:conditions' not in original, f"{spec['path']}: already conditional"
            result = {'neoforge:conditions': [{'type': 'neoforge:false'}], **original}
            reverse = {k: v for k, v in result.items() if k != 'neoforge:conditions'}
        elif spec['op'] == 'rename_key':
            mapping = result[spec['field']]
            assert spec['old'] in mapping and spec['new'] not in mapping, f"{spec['path']}: map changed upstream"
            result[spec['field']] = {(spec['new'] if k == spec['old'] else k): v for k, v in mapping.items()}
            assert 'replace' not in original, f"{spec['path']}: upstream already replaces"
            result = {'replace': True, **result}  # data maps merge across packs; the corrected map must replace
            reverse = {k: v for k, v in result.items() if k != 'replace'}
            reverse[spec['field']] = {(spec['old'] if k == spec['new'] else k): v for k, v in result[spec['field']].items()}
        elif spec['op'] == 'append':
            values = result[spec['field']]
            occupied = sum(v.get('amount', 1) for v in values) + spec['value'].get('amount', 1)
            assert spec['value'] not in values and occupied <= spec['limit'], f"{spec['path']}: list changed upstream"
            values.append(spec['value'])
            reverse = copy.deepcopy(result)
            reverse[spec['field']] = reverse[spec['field']][:-1]
        elif spec['op'] == 'campaign_tier':
            assert 'neoforge:conditions' not in original and original.get('criteria'), f"{spec['path']}: changed upstream"
            result['criteria'] = {'campaign': {'trigger': spec['trigger']}}
            result['requirements'] = [['campaign']]
            result['display'] = dict(original['display'], description={'translate': spec['description']})
            reverse = copy.deepcopy(result)
            reverse['criteria'] = original['criteria']
            reverse['requirements'] = original['requirements']
            reverse['display'] = dict(result['display'], description=original['display']['description'])
        elif spec['op'] == 'mainhand':
            assert original.get('type') == 'apothic_spawners:spawner_modifier' and 'neoforge:conditions' not in original, \
                f"{spec['path']}: not an active spawner modifier upstream"
            result['mainhand'] = {'item': spec['item']}
            reverse = copy.deepcopy(result)
            reverse['mainhand'] = original['mainhand']
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


COMPANION_LANG = ROOT / 'companion/src/main/resources/assets/entrelumen/lang/en_us.json'


def item_models():
    """Every item model shipped by a locked JAR, as namespaced IDs."""
    lock, paths = lock_entries()
    models = set()
    for entry in lock['mods']:
        with zipfile.ZipFile(Path(paths[entry['filename']])) as jar:
            for name in jar.namelist():
                match = re.match(r'assets/([^/]+)/models/item/(.+)\.json$', name)
                if match:
                    models.add(f'{match.group(1)}:{match.group(2)}')
    return models


def build_additions(name, models=None):
    """Return {path below pack/kubejs/data: JSON text} for this family's new shaped recipes.

    Every item exists (companion lang for entrelumen, a pinned item model for other mods; vanilla
    items are left to the runtime check) and the declared act equals the latest staged input."""
    family = FAMILIES[name]
    if not family.get('additions'):
        return {}
    models = models if models is not None else item_models()
    lang = read(COMPANION_LANG)
    design_sources, design_acts = component_sources()
    acts = {out: ACTS[a['act']] for a in family['additions'] for out in [a['id']]}
    outputs_by_path = {}
    ids = [a['id'] for a in family['additions']]
    assert len(ids) == len(set(ids)), 'Duplicate addition ID'
    for addition in family['additions']:
        namespace, path = addition['id'].split(':', 1)
        assert namespace == 'entrelumen', f"{addition['id']}: additions only create ENTRELUMEN outputs"
        assert f'item.entrelumen.{path}' in lang or f'block.entrelumen.{path}' in lang, \
            f"{addition['id']}: output is not a named companion item"
        pattern, key = addition['pattern'], addition['key']
        assert 1 <= len(pattern) <= 3 and len({len(row) for row in pattern}) == 1 and len(pattern[0]) <= 3
        used = {c for row in pattern for c in row if c != ' '}
        assert used == set(key), f"{addition['id']}: pattern and key symbols differ"
        latest = 1
        for ingredient in key.values():
            if 'tag' in ingredient:
                continue
            item_id = ingredient['item']
            ns = item_id.split(':', 1)[0]
            if ns == 'entrelumen':
                path_id = item_id.split(':', 1)[1]
                assert f'item.entrelumen.{path_id}' in lang or f'block.entrelumen.{path_id}' in lang, \
                    f"{addition['id']}: unknown companion item {item_id}"
                if item_id in design_acts:
                    latest = max(latest, design_acts[item_id])
                elif item_id in acts:
                    latest = max(latest, acts[item_id])
            elif ns != 'minecraft':
                assert item_id in models, f"{addition['id']}: no pinned item {item_id}"
                if item_id in STAGE_MATERIALS:
                    latest = max(latest, ACTS[STAGE_MATERIALS[item_id]])
        assert latest == ACTS[addition['act']], \
            f"{addition['id']}: declared act {addition['act']} differs from inputs ({latest})"
        data = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key,
                'result': {'id': addition['id'], 'count': addition['count']}}
        outputs_by_path[f'{namespace}/recipe/{path}.json'] = json.dumps(data, indent=2, ensure_ascii=False) + '\n'
    return outputs_by_path


ADDITIONS_RUNTIME = '''
ServerEvents.afterRecipes(event => {
  var failed = [];
  ADDITIONS.forEach(row => {
    var loaded = event.countRecipes({id: row.id, output: row.output});
    if (loaded !== 1) failed.push({recipe: row.id, loadedOutput: loaded});
  });
  console.info('[TAG] ' + JSON.stringify({status: failed.length ? 'failed-addition-check' : 'additions-loaded',
    checked: ADDITIONS.length, failed: failed}));
});
'''


RUNTIME = '''
ServerEvents.recipes(event => {
  var missing = [];
  ROWS.forEach(row => {
    if (!event.containsRecipe({id: row.id})) missing.push({recipe: row.id, cause: 'native recipe absent'});
    if (!Item.exists(row.component)) missing.push({recipe: row.id, cause: 'component absent', component: row.component});
  });
  var absent = REMOVALS.filter(id => !event.containsRecipe({id: id}));
  if (missing.length) {
    console.error('[TAG] ' + JSON.stringify({status: 'failed-preflight', signature: SIGNATURE, missing: missing}));
    throw new Error('TAG preflight failed; native recipes were not changed');
  }
  ROWS.forEach(row => {
    event.remove({id: row.id});
    event.custom(row.json).id(row.id);
  });
  REMOVALS.forEach(id => event.remove({id: id}));
  console.info('[TAG] ' + JSON.stringify({status: 'registered', signature: SIGNATURE, changed: ROWS.length,
    removed: REMOVALS.length - absent.length, alreadyAbsent: absent}));
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


def render_tags(family):
    lines = []
    for registry in ('block', 'item'):
        entries = [(tag_id, value) for kind, tag_id, value in family.get('tag_removals', []) if kind == registry]
        if not entries:
            continue
        lines.append(f"ServerEvents.tags('{registry}', event => {{")
        for tag_id, value in entries:
            lines.append(f"  event.remove({json.dumps(tag_id)}, {json.dumps(value)});")
        lines.append(f"  console.info('[{family['tag']}] ' + JSON.stringify({{status: 'tags-removed', registry: '{registry}', count: {len(entries)}}}));")
        lines.append('});')
    return '\n'.join(lines) + ('\n' if lines else '')


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
            f'const {prefix}Removals = {json.dumps(removals)};\n' + body + render_tags(family)
            + render_additions(family, prefix))


def render_additions(family, prefix):
    additions = [{'id': a['id'], 'output': a['id']} for a in family.get('additions', [])]
    if not additions:
        return ''
    return (f'const {prefix}Additions = {json.dumps(additions, separators=(",", ":"))};\n'
            + ADDITIONS_RUNTIME.lstrip('\n').replace('ADDITIONS', prefix + 'Additions').replace('TAG', family['tag']))


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
        additions = build_additions(name)
        assert not set(overrides) & set(additions), 'Addition path collides with a data override'
        for relative, text in {**overrides, **additions}.items():
            destination = PACK_DATA / relative
            if args.write:
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(text, encoding='utf-8', newline='\n')
            elif not destination.is_file() or destination.read_text(encoding='utf-8') != text:
                raise SystemExit(f'Generated data file {relative} is stale; run --write')
        print(json.dumps({'family': name, 'status': 'static-PASS', 'nativeRecipesIndexed': audited,
                          'changed': len(rows), 'removed': len(removals), 'dataOverrides': len(overrides),
                          'addedRecipes': len(additions),
                          'acts': sorted({r['act'] for r in rows}), 'runtime': 'pending'}))


if __name__ == '__main__':
    main()
