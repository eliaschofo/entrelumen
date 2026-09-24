"""Repeatable first-join gift audit over every pinned JAR (docs/design/heliodor-compass.md section 1).

usage: python tools/audit_first_join.py <out.json> [--tree <worktree>]
Reads catalog/curated.json and the local JAR copies in catalog/local-paths.json. Scans without
launching anything, and only reports: every hit still needs a javap review of the handler.
1. class constant pools for first-join vocabulary;
2. classes that reference a join/login/tick player event and an inventory-giving method;
3. bundled advancements with loot or function rewards and their triggers.
Nested jar-in-jar libraries are scanned too.
"""
import io, json, re, struct, sys, zipfile
from pathlib import Path

args = sys.argv[1:]
tree = Path(args[args.index('--tree') + 1]) if '--tree' in args else Path(__file__).resolve().parents[1]
out = Path(args[0])
lock = json.loads((tree / 'catalog/curated.json').read_text(encoding='utf-8'))
paths = json.loads((tree / 'catalog/local-paths.json').read_text(encoding='utf-8'))

VOCAB = re.compile(r'spawn_?book|give_?book|obtain_?on_?spawn|first_?join|first_?login|first_?spawn|'
                   r'starting_?items|starter_?(items|kit|blueprints|book)|spawn_?with|start_?with|has_?received|'
                   r'give_?on_?(join|login|spawn)|on_?first_?(join|login)|book_?on_?(join|login|spawn)', re.I)
EVENTS = {
    'net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent': 'login',
    'net/neoforged/neoforge/event/entity/EntityJoinLevelEvent': 'joinLevel',
    'net/neoforged/neoforge/event/entity/player/PlayerEvent$Clone': None,
    'net/neoforged/neoforge/event/tick/PlayerTickEvent$Post': 'tick',
    'net/neoforged/neoforge/event/tick/PlayerTickEvent$Pre': 'tick',
    'net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerChangedDimensionEvent': 'dimension',
}
GIVES = {
    ('net/minecraft/world/entity/player/Inventory', 'add'),
    ('net/minecraft/world/entity/player/Inventory', 'setItem'),
    ('net/minecraft/world/entity/player/Inventory', 'placeItemBackInInventory'),
    ('net/minecraft/world/entity/player/Player', 'addItem'),
    ('net/minecraft/server/level/ServerPlayer', 'addItem'),
    ('net/minecraft/world/entity/player/Player', 'drop'),
    ('net/minecraft/server/level/ServerPlayer', 'drop'),
    ('net/neoforged/neoforge/items/ItemHandlerHelper', 'giveItemToPlayer'),
}
SUSPECT_TRIGGERS = {'minecraft:tick', 'minecraft:location', 'minecraft:inventory_changed',
                    'minecraft:changed_dimension', 'minecraft:slept_in_bed'}


def constant_pool(data):
    """utf8 strings, class names and (owner, name) member refs of one class file."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return set(), set(), set()
    count = struct.unpack('>H', data[8:10])[0]
    pos, i = 10, 1
    utf8, classes, refs, nat = {}, {}, [], {}
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack('>H', data[pos + 1:pos + 3])[0]
            utf8[i] = data[pos + 3:pos + 3 + n].decode('utf-8', 'replace')
            pos += 3 + n
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag == 7:
            classes[i] = struct.unpack('>H', data[pos + 1:pos + 3])[0]
            pos += 3
        elif tag in (8, 16, 19, 20):
            pos += 3
        elif tag in (9, 10, 11):
            refs.append(struct.unpack('>HH', data[pos + 1:pos + 5]))
            pos += 5
        elif tag == 12:
            nat[i] = struct.unpack('>HH', data[pos + 1:pos + 5])
            pos += 5
        elif tag == 15:
            pos += 4
        elif tag in (17, 18):
            pos += 5
        else:
            raise ValueError(f'bad constant tag {tag}')
        i += 1
    names = {utf8.get(v, '') for v in classes.values()}
    members = set()
    for owner, nt in refs:
        o = utf8.get(classes.get(owner), '')
        n = utf8.get(nat.get(nt, (0, 0))[0], '')
        members.add((o, n))
    return set(utf8.values()), names, members


def scan(zf, label, report):
    for info in zf.infolist():
        name = info.filename
        if name.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(zf.read(info))) as nested:
                scan(nested, f'{label}!{name}', report)
        elif name.endswith('.class'):
            try:
                strings, classes, members = constant_pool(zf.read(info))
            except (ValueError, struct.error, IndexError):
                report['unparsed'].append(f'{label}:{name}')
                continue
            vocab = sorted({s for s in strings if len(s) < 120 and VOCAB.search(s)})
            if vocab:
                report['vocabulary'].setdefault(label, {})[name] = vocab
            # Listeners registered with addListener only name the event in a descriptor.
            events = sorted({tag for event, tag in EVENTS.items() if tag and any(event in s for s in strings)})
            gives = sorted({f'{o.rsplit("/", 1)[-1]}.{n}' for o, n in members
                            if (o, n) in GIVES or re.search(r'^give|giveItem|addItem', n)})
            if events and gives:
                report['eventGivers'].setdefault(label, {})[name] = {'events': events, 'gives': gives}
        elif re.match(r'data/[^/]+/advancements?/.+\.json$', name):
            try:
                doc = json.loads(zf.read(info).decode('utf-8-sig'))
            except (ValueError, UnicodeError):
                continue
            rewards = doc.get('rewards') or {}
            if not (rewards.get('loot') or rewards.get('function')):
                continue
            triggers = sorted({c.get('trigger', '') for c in (doc.get('criteria') or {}).values()
                               if isinstance(c, dict)})
            entry = {'path': name, 'triggers': triggers, 'loot': rewards.get('loot'),
                     'function': rewards.get('function'),
                     'suspect': bool(set(triggers) & SUSPECT_TRIGGERS)}
            report['rewardAdvancements'].setdefault(label, []).append(entry)


report = {'jars': 0, 'vocabulary': {}, 'eventGivers': {}, 'rewardAdvancements': {}, 'unparsed': []}
for entry in lock['mods']:
    path = Path(paths[entry['filename']])
    with zipfile.ZipFile(path) as zf:
        scan(zf, f"{entry['filename']} [{entry['side']}]", report)
    report['jars'] += 1
out.write_text(json.dumps(report, indent=1, sort_keys=True) + '\n', encoding='utf-8')
print(json.dumps({'jars': report['jars'], 'vocabularyJars': len(report['vocabulary']),
                  'eventGiverJars': len(report['eventGivers']),
                  'rewardAdvancementJars': len(report['rewardAdvancements']),
                  'suspectAdvancements': sum(e['suspect'] for v in report['rewardAdvancements'].values() for e in v),
                  'unparsed': len(report['unparsed'])}))
