"""Static 1.21.1 check of loot tables and of the structures that name them. Runtime stays pending.

Minecraft 1.21 renamed data/<ns>/loot_tables/ to loot_table/ and reads only the new folder: a table
left in the old one never loads, and a chest that names it opens empty (LootTable.EMPTY). This check
models the 1.21.1 codecs (field and type names read from the 1.21.1 client JAR) for the table, pool
and entry skeleton, number providers and the functions the pack's ports use, and resolves items,
tags, enchantments and nested tables against the pinned JARs, the vanilla client JAR and the pack's
own data. Whatever it does not model is reported as unchecked, never as passed. 1.20 leftovers that
1.21 rejects or silently drops (set_nbt, copy_nbt, the enchantments and treasure fields, loot_table
entries by name) are errors, and so is an enchant_randomly without options: in 1.21 it draws from
every registered enchantment. Loot tables are read as strict JSON; the overlay folders a JAR's
pack.mcmeta may declare are not read.

usage:
  python tools/check_loot_tables.py [FILE ...]
      default: every loot table of pack/kubejs/data and of the companion's resources
  python tools/check_loot_tables.py --structures adastra-
      also, for the pinned JARs whose file name starts with the prefix: every template that a template
      pool names must exist, and every loot table that a template's chests (LootTable tags) or a
      processor list (append_loot rules) name must be one 1.21 loads. Errors count only what a
      structure that can generate places (it is in a structure set and its biomes resolve, the pack's
      files winning over the JARs'); the rest, and tables of mods the pack does not have, are listed
      as unchecked.
  python tools/check_loot_tables.py --copies
      also: no file under pack/kubejs/data may copy the data of a JAR whose license reserves it (see
      RESTRICTIVE, OPEN and OPEN_DATA below); the repository is public.
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


def pinned_mods():
    """Mod IDs of the lock (for neoforge:mod_loaded conditions)."""
    lock = json.loads((ROOT / 'catalog/curated.json').read_text(encoding='utf-8-sig'))
    return {mod['id'] for entry in lock['mods'] for mod in entry.get('metadata', {}).get('mods', [])}


class Sources:
    """The files the game loads, by resource name, with every source that ships each one in load order:
    vanilla, the pinned JARs, then the local roots (the companion, then pack/kubejs, whose datapack
    loads last and wins at a shared path)."""

    def __init__(self, jars=None, vanilla_jar=VANILLA_JAR, local_roots=(COMPANION, PACK)):
        self.labels, self.files, self.jars, self.folders, self.open, self.names = [], {}, {}, {}, {}, []
        self.vanilla = vanilla_jar is not None and Path(vanilla_jar).is_file()
        self.pinned = jars is not None
        if self.vanilla:
            self.add_jar('minecraft', Path(vanilla_jar))
        for label, path in (jars or {}).items():
            self.add_jar(label, Path(path))
        for root in local_roots:
            index = self.add(str(root))
            self.folders[index] = Path(root)
            for prefix in ('data', 'assets'):
                base = Path(root) / prefix
                if base.is_dir():
                    for path in base.rglob('*'):
                        if path.is_file():
                            self.ship(f'{prefix}/{path.relative_to(base).as_posix()}', index)

    def add(self, label):
        self.labels.append(label)
        self.names.append([])
        return len(self.labels) - 1

    def ship(self, name, index):
        self.files.setdefault(name, []).append(index)
        self.names[index].append(name)

    def add_jar(self, label, path):
        index = self.add(label)
        self.jars[index] = path
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                if name.startswith(('data/', 'assets/')) and not name.endswith('/'):
                    self.ship(name, index)

    def read(self, name, index):
        if index in self.folders:
            return (self.folders[index] / name).read_bytes()
        if index not in self.open:
            self.open[index] = zipfile.ZipFile(self.jars[index])
        return self.open[index].read(name)

    def close(self):
        for jar in self.open.values():
            jar.close()
        self.open.clear()


def build_index(*, jars=None, vanilla_jar=VANILLA_JAR, local_roots=(COMPANION, PACK), sources=None):
    sources = sources or Sources(jars, vanilla_jar, local_roots)
    return Index(sources.files, vanilla=sources.vanilla, pinned=sources.pinned)


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
            if options is None and 'enchantments' not in value:
                self.error(where, 'enchant_randomly without "options" draws from every registered enchantment in '
                           '1.21 (Soul Speed, Swift Sneak, Wind Burst and every mod one); vanilla names '
                           '"#minecraft:on_random_loot"')
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


NAMED_STRING = {key: re.compile(re.escape(b'\x08' + struct.pack('>H', len(key)) + key) + rb'(..)', re.S)
                for key in (b'LootTable', b'pool')}


def scan_template(raw):
    """(LootTable strings, jigsaw pool strings) of a structure template, read straight from its NBT bytes.

    Every string tag named LootTable (block entities, and entities such as chest minecarts) and pool (jigsaw
    blocks) counts; a full parse with read_nbt gives the same values for the templates it can decode."""
    data = gunzip(raw) if raw[:2] == b'\x1f\x8b' else raw

    def strings(key):
        found = []
        for match in NAMED_STRING[key].finditer(data):
            size = struct.unpack('>H', match.group(1))[0]
            found.append(data[match.end():match.end() + size].decode('utf-8', 'replace'))
        return found
    return tuple(rl(t) for t in strings(b'LootTable')), {rl(p) for p in strings(b'pool')}


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


def read_json(raw):
    """Parse a data file the way the game's lenient Gson reader accepts it: strict JSON, or JSON with // and
    /* */ comments. Anything else raises ValueError."""
    text = raw.decode('utf-8-sig') if isinstance(raw, bytes) else raw
    try:
        return json.loads(text)
    except ValueError:
        pass
    out, i, quote = [], 0, None
    while i < len(text):
        char = text[i]
        if quote:
            out.append(char)
            if char == '\\':
                out.append(text[i + 1:i + 2])
                i += 1
            elif char == quote:
                quote = None
        elif char == '"':
            quote = char
            out.append(char)
        elif text.startswith('//', i):
            end = text.find('\n', i)
            i = len(text) if end < 0 else end
            continue
        elif text.startswith('/*', i):
            end = text.find('*/', i + 2)
            i = len(text) if end < 0 else end + 2
            continue
        else:
            out.append(char)
        i += 1
    return json.loads(''.join(out))


class Worldgen:
    """Structures, structure sets, template pools and biome tags as 1.21.1 loads them from `sources`, to follow
    what a structure that can generate places. The pack's files win over the JARs' at a shared path; a file
    whose neoforge:conditions fail is not loaded; conditions other than mod_loaded, not, and, or, true and
    false count as met."""
    EMPTY_POOL = 'minecraft:empty'

    def __init__(self, sources, mods=()):
        self.sources, self.mods = sources, set(mods)
        self.unreadable, self.cache, self.templates, self.sets = [], {}, {}, None

    def path(self, kind, value):
        namespace, rest = rl(value).split(':', 1)
        return f'data/{namespace}/{kind}/{rest}'

    def load(self, name, index):
        if (name, index) not in self.cache:
            try:
                self.cache[name, index] = read_json(self.sources.read(name, index))
            except (ValueError, UnicodeDecodeError):
                self.unreadable.append(f'{self.sources.labels[index]}: {name}')
                self.cache[name, index] = None
        return self.cache[name, index]

    def condition(self, value):
        if not isinstance(value, dict):
            return True
        kind = rl(str(value.get('type', '')))
        if kind == 'neoforge:mod_loaded':
            return value.get('modid') in self.mods | {'minecraft', 'neoforge'}
        if kind == 'neoforge:not':
            return not self.condition(value.get('value'))
        if kind in ('neoforge:and', 'neoforge:or'):
            results = [self.condition(v) for v in value.get('values', [])]
            return all(results) if kind == 'neoforge:and' else any(results)
        return kind != 'neoforge:false'

    def loaded(self, data):
        return isinstance(data, dict) and all(self.condition(c) for c in data.get('neoforge:conditions', []))

    def effective(self, name):
        """The version of a registry file that wins: the last source that ships it with its conditions met."""
        for index in reversed(self.sources.files.get(name, [])):
            data = self.load(name, index)
            if self.loaded(data):
                return data
        return None

    def tag(self, registry, value):
        """Entries of a tag merged over every source in load order; replace: true drops the earlier ones."""
        entries = []
        for index in self.sources.files.get(self.path(f'tags/{registry}', value) + '.json', []):
            data = self.load(self.path(f'tags/{registry}', value) + '.json', index)
            if self.loaded(data):
                entries = (list(data.get('values', [])) if data.get('replace') else entries + list(data.get('values', [])))
        return entries

    def biomes(self, value, seen=None):
        """Biome IDs a structure's biomes field resolves to; optional entries count only when they exist."""
        seen = seen if seen is not None else set()
        if isinstance(value, list):
            return set().union(*(self.biomes(v, seen) for v in value)) if value else set()
        if not isinstance(value, str):
            return set()
        if not value.startswith('#'):
            return {rl(value)}
        if value in seen:
            return set()
        seen.add(value)
        found = set()
        for entry in self.tag('worldgen/biome', value[1:]):
            required = not isinstance(entry, dict) or entry.get('required', True)
            entry = entry.get('id', '') if isinstance(entry, dict) else entry
            resolved = self.biomes(entry, seen)
            if required or entry.startswith('#') or f'{self.path("worldgen/biome", entry)}.json' in self.sources.files:
                found |= resolved
        return found

    def structure_sets(self):
        """{structure ID: [set IDs]} over every structure set that loads."""
        if self.sets is None:
            self.sets = {}
            pattern = re.compile(r'data/([^/]+)/worldgen/structure_set/(.+)\.json$')
            for name in self.sources.files:
                match = pattern.match(name)
                data = self.effective(name) if match else None
                for entry in (data or {}).get('structures', []) if isinstance(data, dict) else []:
                    if isinstance(entry, dict) and isinstance(entry.get('structure'), str):
                        self.sets.setdefault(rl(entry['structure']), []).append(f'{match.group(1)}:{match.group(2)}')
        return self.sets

    def structure(self, value):
        return self.effective(self.path('worldgen/structure', value) + '.json')

    def generates(self, value):
        """Whether a structure can generate: it loads, a structure set names it and its biomes resolve."""
        data = self.structure(value)
        return bool(data) and rl(value) in self.structure_sets() and bool(self.biomes(data.get('biomes')))

    def pool(self, value):
        if rl(value) == self.EMPTY_POOL:
            return {'elements': []}
        return self.effective(self.path('worldgen/template_pool', value) + '.json')

    def elements(self, pool):
        """Template locations of a pool (list elements flattened); any element with a location names a template."""
        def flat(element):
            if isinstance(element, dict):
                if isinstance(element.get('elements'), list):
                    for inner in element['elements']:
                        yield from flat(inner)
                elif isinstance(element.get('location'), str):
                    yield rl(element['location'])
        for entry in (pool or {}).get('elements', []):
            yield from flat(entry.get('element') if isinstance(entry, dict) else None)

    def has_template(self, value):
        return self.path('structure', value) + '.nbt' in self.sources.files

    def template(self, value):
        """(every LootTable string, every jigsaw pool string) of the template the winning source ships, or
        ((), set()) when it is missing or unreadable. Only this summary is kept."""
        if value not in self.templates:
            name = self.path('structure', value) + '.nbt'
            self.templates[value] = ((), set())
            for index in reversed(self.sources.files.get(name, [])):
                try:
                    self.templates[value] = scan_template(self.sources.read(name, index))
                except (EOFError, ValueError, IndexError, struct.error, zlib.error):
                    self.unreadable.append(f'{self.sources.labels[index]}: {name}')
                break
        return self.templates[value]

    def jigsaw_pools(self, value):
        return self.template(value)[1]

    def reach(self, structures):
        """(pools, templates) that the given structures place, following fallbacks and jigsaw blocks."""
        pools, templates = set(), set()
        todo = [rl(s['start_pool']) for s in (self.structure(v) for v in structures)
                if isinstance(s, dict) and isinstance(s.get('start_pool'), str)]
        while todo:
            value = todo.pop()
            if value in pools:
                continue
            pools.add(value)
            data = self.pool(value)
            if isinstance((data or {}).get('fallback'), str):
                todo.append(rl(data['fallback']))
            for location in self.elements(data):
                if location not in templates and self.has_template(location):
                    templates.add(location)
                    todo.extend(self.jigsaw_pools(location))
        return pools, templates


def shipped_ids(names, kind, suffix='.json'):
    """Namespaced IDs of the files of one kind (worldgen/structure, structure...) among resource names."""
    pattern = re.compile(rf'data/([^/]+)/{kind}/(.+){re.escape(suffix)}$')
    return {f'{m.group(1)}:{m.group(2)}' for m in map(pattern.match, names) if m}


def structure_report(worldgen, index, jar_label):
    """(errors, report) for one pinned JAR: templates its template pools name, and the loot tables its
    templates and processor lists name, counted as errors only where a structure that can generate places them."""
    sources, mods = worldgen.sources, worldgen.mods
    (jar_index,) = [i for i, label in enumerate(sources.labels) if label == jar_label]
    shipped = sources.names[jar_index]
    structures = sorted(shipped_ids(shipped, 'worldgen/structure'))
    generating = [s for s in structures if worldgen.generates(s)]
    pools, placed = worldgen.reach(generating)
    errors, unchecked = [], {}
    missing_templates = []
    for pool in sorted(shipped_ids(shipped, 'worldgen/template_pool') | {p for p in pools if p != Worldgen.EMPTY_POOL}):
        for location in worldgen.elements(worldgen.pool(pool)):
            if worldgen.has_template(location):
                continue
            path = location.split(':', 1)[1]
            if path == 'empty' or path.endswith('/empty'):  # an element that places nothing, on purpose
                unchecked.setdefault('pool element naming an empty template', []).append(f'{pool} -> {location}')
            elif pool in pools:
                missing_templates.append(f'{pool} -> {location}')
                errors.append(f'{jar_label}: template pool {pool} names template {location}, which no pinned JAR, '
                              'vanilla or pack data ships')
            else:
                unchecked.setdefault('missing template in a pool no generating structure reaches', []).append(
                    f'{pool} -> {location}')
    refs = {}
    for template in sorted(shipped_ids(shipped, 'structure', '.nbt')):
        for table in worldgen.template(template)[0]:
            refs.setdefault(table, Counter())[template] += 1
    for processors in sorted(shipped_ids(shipped, 'worldgen/processor_list')):
        for table in appended_loot(worldgen.effective(worldgen.path('worldgen/processor_list', processors) + '.json')):
            refs.setdefault(rl(table), Counter())[f'processor list {processors}'] += 1
    table_namespaces = {t.split(':')[0] for t in index.ids['loot_table']}
    loose = []
    for table in sorted(t for t in refs if t not in index.ids['loot_table']):
        where = refs[table]
        in_place = sorted(s for s in where if s in placed or s.startswith('processor list '))
        if table.split(':')[0] not in table_namespaces | mods:
            unchecked.setdefault('loot table of a mod the pack does not have', []).append(
                f'{table} ({sum(where.values())} in {", ".join(sorted(where))})')
        elif not in_place:
            unchecked.setdefault('loot table in a template no generating structure places', []).append(
                f'{table} ({sum(where.values())} in {", ".join(sorted(where))})')
        else:
            loose.append(table)
            errors.append(f'{jar_label}: {table} is named {sum(where[s] for s in in_place)} times in '
                          f'{", ".join(in_place)}, and no 1.21 loot_table path ships it')
    report = {'structures': len(structures), 'generating': len(generating), 'poolsReached': len(pools),
              'templatesPlaced': len(placed), 'tables': len(refs),
              'references': sum(sum(c.values()) for c in refs.values()), 'unresolved': loose,
              'missingTemplates': missing_templates}
    unreadable = sorted({u for u in worldgen.unreadable if u.startswith(f'{jar_label}: ')})
    if unreadable:
        unchecked['unreadable'] = unreadable
    if unchecked:
        report['unchecked'] = unchecked
    return errors, report


# The repository is public, so pack/kubejs/data may not carry a copy of data from a JAR whose license reserves it
# (all rights reserved, no derivatives) or names no open license. A pack file is compared with the files of the
# same namespace and file name in those JARs, load conditions aside: the same bytes or JSON, or JSON sharing at
# least NEAR of its leaves with the larger file when that one has at least LEAVES leaves, is a copy. Vanilla's
# self-drop block table is the one standard form exempted: written from the block ID alone, it looks like the
# mod's own self-drop whoever writes it.
RESTRICTIVE = re.compile(r'all[ -]rights[ -](?:are[ -])?reserved|\bARR\b|-ND\b|\bND\b|no ?deriv', re.I)
OPEN = re.compile(r'\b(?:MIT|Apache|BSD|L?GPL|GNU|MPL|EPL|CC0|CC[- ]BY|Unlicense|ISC|zlib)', re.I)
# Read by hand: Ad Astra's Terrarium License v1 puts every file under resources/data/ under MIT
# (github.com/terrarium-earth/Ad-Astra/blob/1.21.1/LICENSE); THIRD_PARTY_NOTICES.md carries its notice.
OPEN_DATA = {'adastra-': 'Terrarium License v1: files under resources/data/ are MIT'}
NEAR, LEAVES = 0.8, 5


def is_restricted(filename, license):
    """Whether the repository may not copy a JAR's data: its license reserves it, forbids derivatives or names
    no open license, unless its data license was read by hand (OPEN_DATA)."""
    return not filename.startswith(tuple(OPEN_DATA)) and bool(RESTRICTIVE.search(license) or not OPEN.search(license))


def restricted_jars():
    """{JAR file name: license metadata} of the lock's JARs whose data the repository may not copy."""
    lock = json.loads((ROOT / 'catalog/curated.json').read_text(encoding='utf-8-sig'))
    licenses = {entry['filename']: str(entry.get('metadata', {}).get('license') or '') for entry in lock['mods']}
    return {name: license or 'no license metadata' for name, license in licenses.items() if is_restricted(name, license)}


def leaves(value, at=''):
    """Every scalar of a JSON value with its path, keys sorted: what two files share when one copies the other."""
    if isinstance(value, dict):
        for key in sorted(value):
            yield from leaves(value[key], f'{at}/{key}')
    elif isinstance(value, list):
        for i, child in enumerate(value):
            yield from leaves(child, f'{at}[{i}]')
    else:
        yield f'{at}={json.dumps(value)}'


def copy_of(mine, theirs):
    """Why `mine` (bytes) copies `theirs` (bytes), or None. Load conditions do not count: a disabled copy is a copy."""
    if mine == theirs:
        return 'identical bytes'
    try:
        a, b = (read_json(raw) for raw in (mine, theirs))
    except (ValueError, UnicodeDecodeError):
        return None
    a, b = (Counter(leaves({k: v for k, v in data.items() if k != 'neoforge:conditions'} if isinstance(data, dict) else data))
            for data in (a, b))
    if a == b:
        return 'identical JSON'
    larger = max(sum(a.values()), sum(b.values()))
    shared = sum((a & b).values()) / larger
    return f'shares {shared:.0%} of its JSON leaves' if larger >= LEAVES and shared >= NEAR else None


def self_drop_form(name, raw):
    """Whether a pack file is exactly vanilla's self-drop block table for the block its path names."""
    match = re.match(r'data/([^/]+)/loot_table/blocks/(.+)\.json$', name)
    if not match:
        return False
    block = f'{match.group(1)}:{match.group(2)}'
    try:
        data = read_json(raw)
    except (ValueError, UnicodeDecodeError):
        return False
    return data == {'type': 'minecraft:block', 'pools': [{
        'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
        'entries': [{'type': 'minecraft:item', 'name': block}], 'rolls': 1.0}], 'random_sequence': f'{match.group(1)}:blocks/{match.group(2)}'}


def copies(sources, reserved, root=PACK / 'data'):
    """[(pack file, JAR, JAR file, why)] for every file under `root` that copies a file of a restricted JAR."""
    candidates = {}
    for index, label in enumerate(sources.labels):
        if label in reserved:
            for name in sources.names[index]:
                parts = name.split('/')
                if parts[0] == 'data' and len(parts) > 2:
                    candidates.setdefault((parts[1], parts[-1]), []).append((index, name))
    found = []
    for path in sorted(p for p in Path(root).rglob('*') if p.is_file()):
        name = 'data/' + path.relative_to(root).as_posix()
        parts = name.split('/')
        mine = None
        for index, theirs in candidates.get((parts[1], parts[-1]), []):
            mine = mine if mine is not None else path.read_bytes()
            if self_drop_form(name, mine):
                break
            why = copy_of(mine, sources.read(theirs, index))
            if why:
                found.append((name, sources.labels[index], theirs, why))
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
    parser.add_argument('--copies', action='store_true',
                        help='also fail on pack/kubejs/data files that copy data of a JAR whose license reserves it')
    args = parser.parse_args(argv)
    jars = pinned_jars()
    sources = Sources(jars)
    index = build_index(sources=sources)
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
    structures, worldgen = {}, None
    for prefix in args.structures:
        if jars is None:
            raise SystemExit('--structures needs every pinned JAR on this machine (catalog/local-paths.json)')
        chosen = [filename for filename in jars if filename.startswith(prefix)]
        if not chosen:
            raise SystemExit(f'No pinned JAR starts with {prefix!r}')
        worldgen = worldgen or Worldgen(sources, pinned_mods())
        for filename in chosen:
            found, report = structure_report(worldgen, index, filename)
            errors += found
            if report['structures'] or report['tables'] or report.get('unchecked'):
                structures[filename] = report
    copied = []
    if args.copies:
        if jars is None:
            raise SystemExit('--copies needs every pinned JAR on this machine (catalog/local-paths.json)')
        reserved = restricted_jars()
        copied = copies(sources, reserved)
        errors += [f'{mine}: copies {theirs} of {jar} ({reserved[jar]}): {why}' for mine, jar, theirs, why in copied]
    sources.close()
    for line in errors:
        print(line, file=sys.stderr)
    print(json.dumps({'status': 'static-FAIL' if errors else 'static-PASS', 'tables': tables, 'errors': len(errors),
                      'unchecked': dict(sorted(unchecked.items())), 'vanillaRead': index.vanilla,
                      'pinnedRead': index.pinned, **({'structures': structures} if structures else {}),
                      **({'copies': len(copied)} if args.copies else {}), 'runtime': 'pending'}, ensure_ascii=False))
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
