"""Static 1.21.1 check of loot tables: folder, shape and IDs. Runtime loading stays pending.

Minecraft 1.21 renamed data/<ns>/loot_tables/ to loot_table/ and reads only the new folder: a table
left in the old one never loads, and a chest that names it opens empty (LootTable.EMPTY). This check
models the 1.21.1 codecs (field and type names read from the 1.21.1 client JAR) for the table, pool
and entry skeleton, number providers and the functions the pack's ports use, and resolves items,
tags, enchantments and nested tables against the pinned JARs, the vanilla client JAR and the pack's
own data. Whatever it does not model is reported as unchecked, never as passed. 1.20 leftovers that
1.21 rejects or silently drops (set_nbt, copy_nbt, the enchantments and treasure fields, loot_table
entries by name) are errors. Files are read as strict JSON, and the overlay folders a JAR's
pack.mcmeta may declare are not read.

usage:
  python tools/check_loot_tables.py [FILE ...]
      default: every loot table of pack/kubejs/data and of the companion's resources
  python tools/check_loot_tables.py --structures adastra-
      also: every loot table that the structure templates (chest LootTable tags) or processor lists
      (append_loot rules) of the pinned JARs whose file name starts with the prefix name must resolve
      to a table that 1.21 loads
"""
from __future__ import annotations

import argparse
from collections import Counter
import io
import json
from pathlib import Path
import re
import struct
import sys
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'pack/kubejs'
COMPANION = ROOT / 'companion/src/main/resources'
# The vanilla client JAR tools/check_guides.py also reads; without it vanilla IDs stay unchecked.
VANILLA_JAR = Path('G:/Elias/Codex/Entrelumen-work/neoform-cache/artifacts/minecraft_1.21.1_client.jar')

ID = re.compile(r'(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+')
TABLE_PATH = re.compile(r'(?:^|/)data/([a-z0-9_.-]+)/(loot_tables?)/(.+)\.json$')

# LootContextParamSets of 1.21.1; mods may register more in their own namespace.
CONTEXTS = {'empty', 'chest', 'command', 'selector', 'fishing', 'entity', 'equipment', 'archaeology', 'gift',
            'barter', 'vault', 'advancement_reward', 'advancement_entity', 'advancement_location', 'block_use',
            'generic', 'block', 'shearing', 'enchanted_damage', 'enchanted_item', 'enchanted_location',
            'enchanted_entity', 'hit_block'}
TABLE_FIELDS = {'type', 'pools', 'functions', 'random_sequence', 'neoforge:conditions'}
POOL_FIELDS = {'rolls', 'bonus_rolls', 'entries', 'functions', 'conditions', 'name'}  # name: NeoForge
SINGLETON = {'type', 'conditions', 'functions', 'weight', 'quality'}
COMPOSITE = {'type', 'conditions', 'children'}
ENTRY_FIELDS = {'minecraft:item': SINGLETON | {'name'}, 'minecraft:tag': SINGLETON | {'name', 'expand'},
                'minecraft:loot_table': SINGLETON | {'value'}, 'minecraft:dynamic': SINGLETON | {'name'},
                'minecraft:empty': SINGLETON, 'minecraft:alternatives': COMPOSITE,
                'minecraft:group': COMPOSITE, 'minecraft:sequence': COMPOSITE}
FUNCTION_FIELDS = {'minecraft:set_count': {'function', 'conditions', 'count', 'add'},
                   'minecraft:enchant_randomly': {'function', 'conditions', 'options', 'only_compatible'}}
REMOVED_FUNCTIONS = {'minecraft:set_nbt': 'minecraft:set_components or minecraft:set_custom_data',
                     'minecraft:copy_nbt': 'minecraft:copy_custom_data'}
OLD_FIELDS = {('minecraft:enchant_randomly', 'enchantments'): 'options',
              ('minecraft:enchant_with_levels', 'treasure'): 'options',
              ('minecraft:loot_table', 'name'): 'value'}
PROVIDERS = {'minecraft:constant': {'type', 'value'}, 'minecraft:uniform': {'type', 'min', 'max'},
             'minecraft:binomial': {'type', 'n', 'p'}}


def rl(value):
    return value if ':' in value else 'minecraft:' + value


class Index:
    """Resources the pinned JARs, vanilla and the pack ship, by kind, as namespaced IDs."""
    KINDS = {'item': re.compile(r'assets/([^/]+)/models/item/(.+)\.json$'),
             'item_tag': re.compile(r'data/([^/]+)/tags/item/(.+)\.json$'),
             'enchantment': re.compile(r'data/([^/]+)/enchantment/(.+)\.json$'),
             'enchantment_tag': re.compile(r'data/([^/]+)/tags/enchantment/(.+)\.json$'),
             'loot_table': re.compile(r'data/([^/]+)/loot_table/(.+)\.json$')}

    def __init__(self, names=(), *, vanilla=False, pinned=False):
        self.ids = {kind: set() for kind in self.KINDS}
        self.vanilla, self.pinned = vanilla, pinned  # whether each source was read in full
        for name in names:
            self.add(name)

    def add(self, name):
        for kind, pattern in self.KINDS.items():
            match = pattern.match(name)
            if match:
                self.ids[kind].add(f'{match.group(1)}:{match.group(2)}')

    def missing(self, kind, value):
        """None when `value` exists, 'missing' when every source that could ship it was read, else 'unchecked'."""
        if value in self.ids[kind]:
            return None
        if self.vanilla and self.pinned:
            return 'missing'
        if kind == 'item' and (self.vanilla if value.startswith('minecraft:') else self.pinned):
            return 'missing'  # only vanilla registers minecraft: items
        return 'unchecked'


def pinned_jars():
    """{file name: path} of the lock, or None when a pinned JAR is not on this machine."""
    try:
        lock = json.loads((ROOT / 'catalog/curated.json').read_text(encoding='utf-8-sig'))
        paths = json.loads((ROOT / 'catalog/local-paths.json').read_text(encoding='utf-8-sig'))
        jars = {entry['filename']: Path(paths[entry['filename']]) for entry in lock['mods']}
    except (FileNotFoundError, KeyError):
        return None
    return jars if all(path.is_file() for path in jars.values()) else None


def local_names(root):
    """Resource names of a folder laid out like a JAR root (data/..., assets/...)."""
    for prefix in ('data', 'assets'):
        base = root / prefix
        if base.is_dir():
            for path in base.rglob('*.json'):
                yield f'{prefix}/{path.relative_to(base).as_posix()}'


def build_index(*, jars=None, vanilla_jar=VANILLA_JAR, local_roots=(PACK, COMPANION)):
    names = [name for root in local_roots for name in local_names(root)]
    for path in (jars or {}).values():
        with zipfile.ZipFile(path) as jar:
            names.extend(jar.namelist())
    vanilla = vanilla_jar is not None and Path(vanilla_jar).is_file()
    if vanilla:
        with zipfile.ZipFile(vanilla_jar) as jar:
            names.extend(jar.namelist())
    return Index(names, vanilla=vanilla, pinned=jars is not None)


class Check:
    def __init__(self, index):
        self.index, self.errors, self.unchecked = index, [], []

    def error(self, where, message):
        self.errors.append(f'{where}: {message}')

    def skip(self, where, what):
        self.unchecked.append(f'{where}: {what}')

    def fields(self, where, value, allowed):
        for key in value:
            if key not in allowed and not key.startswith('_'):  # _comment and the like: ignored by the codec
                self.error(where, f'unknown field "{key}"')

    def ident(self, where, value, kind=None):
        if not isinstance(value, str) or not ID.fullmatch(value):
            self.error(where, f'not a resource location: {value!r}')
            return
        if kind:
            state = self.index.missing(kind, rl(value))
            if state == 'missing':
                self.error(where, f'no pinned JAR, vanilla or pack data ships {kind} {rl(value)}')
            elif state:
                self.skip(where, f'{kind} {rl(value)}')

    def listed(self, where, value, each):
        if not isinstance(value, list):
            self.error(where, 'expected a list')
            return
        for i, item in enumerate(value):
            each(f'{where}[{i}]', item)

    def integer(self, where, value):
        if isinstance(value, bool) or not isinstance(value, (int, float)) or value != int(value):
            self.error(where, f'not an integer: {value!r}')

    def number(self, where, value):
        if isinstance(value, bool) or not isinstance(value, (int, float, dict)):
            self.error(where, f'not a number provider: {value!r}')
            return
        if not isinstance(value, dict):
            return
        if 'type' not in value:  # NumberProviders.CODEC falls back to an untyped uniform {min, max}
            kind, value = 'minecraft:uniform', dict(value, type='minecraft:uniform')
        elif isinstance(value['type'], str) and ID.fullmatch(value['type']):
            kind = rl(value['type'])
        else:
            self.error(where, f'not a number provider type: {value["type"]!r}')
            return
        if kind in ('minecraft:score', 'minecraft:storage', 'minecraft:enchantment_level') \
                or not kind.startswith('minecraft:'):
            self.skip(where, f'number provider {kind}')
            return
        if kind not in PROVIDERS:
            self.error(where, f'no number provider {kind} in 1.21.1')
            return
        self.fields(where, value, PROVIDERS[kind])
        for key in sorted(PROVIDERS[kind] - {'type'}):
            if key not in value:
                self.error(where, f'{kind} needs "{key}"')
            elif key == 'value':
                if isinstance(value[key], bool) or not isinstance(value[key], (int, float)):
                    self.error(f'{where}.value', 'not a number')
            else:
                self.number(f'{where}.{key}', value[key])

    def conditions(self, where, value):
        def one(at, condition):
            if isinstance(condition, dict) and isinstance(condition.get('condition'), str):
                self.skip(at, f'condition {rl(condition["condition"])}')
            else:
                self.error(at, 'a condition needs "condition"')
        self.listed(where, value, one)

    def old_fields(self, where, kind, value):
        """Report 1.20 fields of `kind` and return them, so they are not reported twice as unknown."""
        old = {field for (owner, field) in OLD_FIELDS if owner == kind and field in value}
        for field in sorted(old):
            self.error(where, f'1.20 field "{field}" of {kind}: 1.21 reads "{OLD_FIELDS[kind, field]}"')
        return old

    def function(self, where, value):
        if not isinstance(value, dict) or not isinstance(value.get('function'), str) \
                or not ID.fullmatch(value['function']):
            self.error(where, 'a function needs a "function" resource location')
            return
        name = rl(value['function'])
        old = self.old_fields(where, name, value)
        if name in REMOVED_FUNCTIONS:
            self.error(where, f'{name} is gone in 1.21; use {REMOVED_FUNCTIONS[name]}')
            return
        if name not in FUNCTION_FIELDS:
            self.skip(where, f'function {name}')
            return
        self.fields(where, value, FUNCTION_FIELDS[name] | old)
        if 'conditions' in value:
            self.conditions(f'{where}.conditions', value['conditions'])
        if name == 'minecraft:set_count':
            if 'count' not in value:
                self.error(where, 'set_count needs "count"')
            else:
                self.number(f'{where}.count', value['count'])
            if not isinstance(value.get('add', False), bool):
                self.error(f'{where}.add', 'not a boolean')
        elif name == 'minecraft:enchant_randomly':
            options = value.get('options')
            if isinstance(options, str) and options.startswith('#'):
                self.ident(f'{where}.options', options[1:], 'enchantment_tag')
            elif isinstance(options, str):
                self.ident(f'{where}.options', options, 'enchantment')
            elif options is not None:
                self.listed(f'{where}.options', options, lambda w, e: self.ident(w, e, 'enchantment'))
            if not isinstance(value.get('only_compatible', True), bool):
                self.error(f'{where}.only_compatible', 'not a boolean')

    def entry(self, where, value):
        if not isinstance(value, dict) or not isinstance(value.get('type'), str) or not ID.fullmatch(value['type']):
            self.error(where, 'an entry needs a "type" resource location')
            return
        kind = rl(value['type'])
        old = self.old_fields(where, kind, value)
        if kind not in ENTRY_FIELDS:
            (self.error if kind.startswith('minecraft:') else self.skip)(where, f'entry type {kind}')
            return
        self.fields(where, value, ENTRY_FIELDS[kind] | old)
        for key in ('weight', 'quality'):
            if key in value:
                self.integer(f'{where}.{key}', value[key])
        if 'conditions' in value:
            self.conditions(f'{where}.conditions', value['conditions'])
        if 'functions' in value:
            self.listed(f'{where}.functions', value['functions'], self.function)
        if kind == 'minecraft:item':
            self.ident(f'{where}.name', value.get('name'), 'item')
        elif kind == 'minecraft:tag':
            self.ident(f'{where}.name', value.get('name'), 'item_tag')
            if not isinstance(value.get('expand'), bool):
                self.error(f'{where}.expand', 'tag entries need a boolean "expand"')
        elif kind == 'minecraft:dynamic':
            self.ident(f'{where}.name', value.get('name'))
        elif kind == 'minecraft:loot_table':
            nested = value.get('value')
            if isinstance(nested, dict):
                self.table(f'{where}.value', nested)
            else:
                self.ident(f'{where}.value', nested, 'loot_table')
        elif kind in ('minecraft:alternatives', 'minecraft:group', 'minecraft:sequence'):
            self.listed(f'{where}.children', value.get('children', []), self.entry)

    def pool(self, where, value):
        if not isinstance(value, dict):
            self.error(where, 'a pool is an object')
            return
        self.fields(where, value, POOL_FIELDS)
        for key in ('rolls', 'entries'):
            if key not in value:
                self.error(where, f'a pool needs "{key}"')
        if 'rolls' in value:
            self.number(f'{where}.rolls', value['rolls'])
        if 'bonus_rolls' in value:
            self.number(f'{where}.bonus_rolls', value['bonus_rolls'])
        if 'name' in value and not isinstance(value['name'], str):
            self.error(f'{where}.name', 'not a string')
        if 'entries' in value:
            self.listed(f'{where}.entries', value['entries'], self.entry)
        if 'functions' in value:
            self.listed(f'{where}.functions', value['functions'], self.function)
        if 'conditions' in value:
            self.conditions(f'{where}.conditions', value['conditions'])

    def table(self, where, value):
        if not isinstance(value, dict):
            self.error(where, 'a loot table is an object')
            return
        self.fields(where, value, TABLE_FIELDS)
        if 'neoforge:conditions' in value:
            self.skip(where, 'neoforge:conditions')
        if 'type' in value:
            self.ident(f'{where}.type', value['type'])
            kind = rl(value['type']) if isinstance(value['type'], str) else ''
            if kind.startswith('minecraft:') and kind.split(':', 1)[1] not in CONTEXTS:
                self.error(f'{where}.type', f'no loot context {kind} in 1.21.1')
            elif kind and not kind.startswith('minecraft:'):
                self.skip(f'{where}.type', f'loot context {kind}')
        if 'random_sequence' in value:
            self.ident(f'{where}.random_sequence', value['random_sequence'])
        self.listed(f'{where}.pools', value.get('pools', []), self.pool)
        if 'functions' in value:
            self.listed(f'{where}.functions', value['functions'], self.function)


def check_table(name, data, index):
    """(errors, unchecked) for one loot table; `name` is its resource path (…/data/<ns>/loot_table/….json)."""
    check = Check(index)
    match = TABLE_PATH.search(name)
    if not match:
        check.error(name, 'not a data/<ns>/loot_table/ path')
    elif match.group(2) == 'loot_tables':
        check.error(name, f'1.21 reads data/{match.group(1)}/loot_table/, never loot_tables/')
    check.table(name, data)
    return check.errors, check.unchecked


def gunzip(raw):
    """Inflate a gzip member without checking its trailer: NbtIo.readCompressed stops reading at the end of
    the root tag, so the game never sees a bad CRC either."""
    flags, at = raw[3], 10
    if flags & 4:
        at += 2 + int.from_bytes(raw[at:at + 2], 'little')
    for flag in (8, 16):
        if flags & flag:
            at = raw.index(b'\0', at) + 1
    if flags & 2:
        at += 2
    return zlib.decompressobj(-zlib.MAX_WBITS).decompress(raw[at:])


def read_nbt(raw):
    """Decode a (gzipped) NBT file into Python values; enough for structure templates."""
    if raw[:2] == b'\x1f\x8b':
        raw = gunzip(raw)
    data = io.BytesIO(raw)

    def take(fmt):
        return struct.unpack('>' + fmt, data.read(struct.calcsize('>' + fmt)))[0]

    def text():
        return data.read(take('H')).decode('utf-8', 'replace')

    def payload(tag):
        if tag in (1, 2, 3, 4, 5, 6):
            return take('bhiqfd'[tag - 1])
        if tag == 7:
            return data.read(take('i'))
        if tag == 8:
            return text()
        if tag == 9:
            inner, size = take('b'), take('i')
            return [payload(inner) for _ in range(size)]
        if tag == 10:
            found = {}
            while (inner := take('b')) != 0:
                key = text()
                found[key] = payload(inner)
            return found
        if tag in (11, 12):
            size = take('i')
            return list(struct.unpack(f'>{size}{"iq"[tag - 11]}', data.read(size * (4 if tag == 11 else 8))))
        raise ValueError(f'unknown NBT tag {tag}')

    tag = take('b')
    text()
    return payload(tag)


def appended_loot(value):
    """Loot tables named by the minecraft:append_loot block entity modifiers inside a processor list."""
    if isinstance(value, dict):
        if isinstance(value.get('type'), str) and rl(value['type']) == 'minecraft:append_loot' \
                and isinstance(value.get('loot_table'), str):
            yield value['loot_table']
        for child in value.values():
            yield from appended_loot(child)
    elif isinstance(value, list):
        for child in value:
            yield from appended_loot(child)


def structure_loot_tables(jar_path, unreadable=None):
    """{loot table ID: Counter(source: references)} for one JAR: the LootTable of every block entity in its
    structure templates and the append_loot rules of its processor lists.

    Files this reader cannot decode are listed in `unreadable` and not checked."""
    found = {}
    with zipfile.ZipFile(jar_path) as jar:
        for name in jar.namelist():
            template = re.match(r'data/([^/]+)/structure/(.+)\.nbt$', name)
            processors = re.match(r'data/([^/]+)/worldgen/processor_list/(.+)\.json$', name)
            if not template and not processors:
                continue
            try:
                data = read_nbt(jar.read(name)) if template else json.loads(jar.read(name))
            except (EOFError, ValueError, IndexError, struct.error, zlib.error):
                if unreadable is not None:
                    unreadable.append(name)
                continue
            if processors:
                tables = list(appended_loot(data))
                source = f'processor list {processors.group(1)}:{processors.group(2)}'
            else:
                blocks = data.get('blocks', []) if isinstance(data, dict) else []
                tables = [t for t in ((b.get('nbt') or {}).get('LootTable') for b in blocks) if isinstance(t, str)]
                source = f'{template.group(1)}:{template.group(2)}'
            for table in tables:
                found.setdefault(rl(table), Counter())[source] += 1
    return found


def default_tables():
    for root in (PACK, COMPANION):
        base = root / 'data'
        if base.is_dir():
            for path in sorted(base.glob('*/loot_table*/**/*.json')):
                yield path


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('files', nargs='*', type=Path)
    parser.add_argument('--structures', action='append', default=[], metavar='JAR_PREFIX')
    args = parser.parse_args(argv)
    jars = pinned_jars()
    index = build_index(jars=jars)
    errors, unchecked, tables = [], Counter(), 0
    for path in args.files or list(default_tables()):
        resolved = path.resolve()
        name = resolved.relative_to(ROOT).as_posix() if resolved.is_relative_to(ROOT) else resolved.as_posix()
        tables += 1
        try:
            data = json.loads(resolved.read_text(encoding='utf-8-sig'))
        except ValueError as error:
            errors.append(f'{name}: not strict JSON ({error})')
            continue
        found, skipped = check_table(name, data, index)
        errors += found
        unchecked.update(item.split(': ', 1)[1] for item in skipped)
    structures = {}
    for prefix in args.structures:
        if jars is None:
            raise SystemExit('--structures needs every pinned JAR on this machine (catalog/local-paths.json)')
        chosen = [path for filename, path in jars.items() if filename.startswith(prefix)]
        if not chosen:
            raise SystemExit(f'No pinned JAR starts with {prefix!r}')
        for path in chosen:
            unreadable = []
            refs = structure_loot_tables(path, unreadable)
            loose = sorted(table for table in refs if table not in index.ids['loot_table'])
            if refs or unreadable:
                structures[path.name] = {'tables': len(refs), 'references': sum(sum(c.values()) for c in refs.values()),
                                         'unresolved': loose, **({'unreadable': unreadable} if unreadable else {})}
            errors += [f'{path.name}: {t} is named {sum(refs[t].values())} times in {len(refs[t])} templates or '
                       'processor lists, and no 1.21 loot_table path ships it' for t in loose]
    for line in errors:
        print(line, file=sys.stderr)
    print(json.dumps({'status': 'static-FAIL' if errors else 'static-PASS', 'tables': tables, 'errors': len(errors),
                      'unchecked': dict(sorted(unchecked.items())), 'vanillaRead': index.vanilla,
                      'pinnedRead': index.pinned, **({'structures': structures} if structures else {}),
                      'runtime': 'pending'}, ensure_ascii=False))
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
