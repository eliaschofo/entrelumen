"""Check More Machine balance artifacts and optional actual server receipts."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAPS = ('item/item_replicator.json', 'fluid/fluid_replicator.json',
        'mekanism/chemical/chemical_replicator.json')
PROBES = {'minecraft:iron_ingot', 'minecraft:water', 'mekanism:fissile_fuel', 'mekanism:antimatter'}
RETAINED = {'mekmm:factory/basic/oxidizing', 'mekmm:factory/ultimate/oxidizing'}
MARKER = '[ENTRELUMEN_MORE_MACHINE] '


def validate_receipt(row):
    if not isinstance(row, dict):
        raise ValueError('receipt must be an object')
    if row.get('schema') != 1 or row.get('kind') != 'runtime_maps' or not row.get('run'):
        raise ValueError('wrong receipt schema or missing run')
    if row.get('status') != 'PASS' or row.get('error'):
        raise ValueError('runtime audit did not pass: ' + str(row.get('error', row.get('status'))))
    maps = row.get('maps')
    if not isinstance(maps, dict) or set(maps) != {'item', 'fluid', 'chemical'} or any(type(v) is not int or v != 0 for v in maps.values()):
        raise ValueError('all three loaded data maps must be empty')
    probes = row.get('probes')
    if not isinstance(probes, dict) or set(probes) != PROBES or any(v is not False for v in probes.values()):
        raise ValueError('all actual holder probes must report no attached recipe')
    if row.get('forbiddenRecipes') != [] or set(row.get('retainedRecipes', [])) != RETAINED:
        raise ValueError('loaded recipes do not match the curatorial boundary')


def check_static():
    for name in MAPS:
        path = ROOT / 'pack/kubejs/data/mekmm/data_maps' / name
        if json.loads(path.read_text(encoding='utf-8-sig')) != {'replace': True, 'values': {}}:
            raise ValueError('not an empty replacing data map: ' + name)
    script = (ROOT / 'pack/kubejs/server_scripts/entrelumen_more_machine_balance.js').read_text(encoding='utf-8-sig')
    for value in ['mekmm:replicator', 'mekmm:fluid_replicator', 'mekmm:chemical_replicator'] + [
        'mekmm:factory/' + tier + '/replicating' for tier in ('basic', 'advanced', 'elite', 'ultimate')]:
        if repr(value) not in script:
            raise ValueError('missing exact removal: ' + value)
    for token in ['source.hasPermission(2)', 'getDataMap(', 'getData(type)', 'mysticalagriculture|mysticalagradditions',
                  'dense|multiversal|overclocked|quantum|creative']:
        if token not in script:
            raise ValueError('missing guard contract: ' + token)


def last_receipt(path):
    # Never fall back to an older passing run when the newest run failed or is malformed.
    lines = [line.split(MARKER, 1)[1] for line in path.read_text(encoding='utf-8-sig', errors='replace').splitlines() if MARKER in line]
    if not lines:
        raise ValueError('no actual runtime receipt found')
    return json.loads(lines[-1])


def self_test():
    from copy import deepcopy
    valid = {'schema': 1, 'kind': 'runtime_maps', 'run': 'synthetic-fixture', 'status': 'PASS',
             'maps': dict.fromkeys(('item', 'fluid', 'chemical'), 0), 'probes': dict.fromkeys(PROBES, False),
             'forbiddenRecipes': [], 'retainedRecipes': sorted(RETAINED)}
    validate_receipt(valid)
    for key, value in [('maps', {'item': 0}), ('probes', {}), ('status', 'FAIL'),
                       ('forbiddenRecipes', ['mekmm:replicator']), ('retainedRecipes', []), ('error', 'InternalError: TypeError: redeclaration of var Types. (server_scripts:entrelumen_more_machine_balance.js#24)')]:
        broken = deepcopy(valid); broken[key] = value
        try: validate_receipt(broken)
        except ValueError: pass
        else: raise AssertionError('accepted invalid fixture: ' + key)
    broken = deepcopy(valid); broken['maps']['chemical'] = 1
    try: validate_receipt(broken)
    except ValueError: pass
    else: raise AssertionError('accepted active chemical map')
    print('PASS synthetic receipt rejection tests; not runtime evidence')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', type=Path)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    try:
        check_static()
        print('PASS static balance artifacts; runtime verification still required')
        if args.self_test: self_test()
        if args.log:
            row = last_receipt(args.log)
            validate_receipt(row)
            print('PASS loaded-map/recipe receipt run=' + str(row['run']))
    except (ValueError, OSError, TypeError) as error:
        parser.exit(1, 'FAIL: ' + str(error) + '\n')


if __name__ == '__main__':
    main()
