"""Validate the informational guide chapters in content/guides/*.json.

Each file is one chapter of the pack's manual (mods, QoL, logistics, building, farming, tips). The
schema extends the story chapters' format (see content/act_two.json):

  {"chapter": "guide_create", "group": "tech|magic|exploration|qol|entrelumen", "act": "II",
   "icon": "create:cogwheel", "emblem": "create:textures/item/goggles.png",
   "title": {"en_us": "...", "es_es": "..."}, "subtitle": {"en_us": "...", "es_es": "..."},
   "quests": [{"key": "create_welcome", "deps": [], "type": "checkmark|item|dimension|advancement",
               "item": "create:shaft", "count": 1, "tag": "c:plates/iron", "dimension": "...",
               "advancement": "...", "optional": true, "icon": "...",
               "layout": {"x": 0, "y": 0, "size": 1.0, "shape": "circle"},
               "en_us": ["Title", "Description"], "es_es": ["Título", "Descripción"],
               "sources": ["jar:<file>:<path>", "https://..."]}]}

Checks: schema, unique chapter ids and quest keys (globally, prefixed by the chapter), deps inside
the chapter, EN/ES parity, text limits and formatting codes, sources on every quest, and that every
item, icon and tag namespace exists in the pinned JARs (catalog/local-paths.json) or vanilla.
The emblem is the texture drawn on the chapter's medallion in the quest book
(tools/generate_quests.py): it must exist in a pinned JAR or the companion, be square (16 or 32 px)
and not animated, since FTB draws the whole PNG. The v2 guide generator compiles a "tag" task to one
item, so it also names the concrete "item" that FTB checks (for unified materials, Almost Unified's
choice); sector chapters ask for tags with any-of tasks instead.
Warnings, which never fail the run: descriptions over 500 characters and the meta phrases that the
copy pass bans (see copy_warnings).

    python tools/check_guides.py            # all chapters
    python tools/check_guides.py create     # chapters whose id contains 'create'

Sector chapters (content/sectors, quest book v3) are checked here too, against the same pinned JARs:
every item named by a task, an icon, a reward table or the rich text ([item:…]), the advancements,
entities, structures (or structure tags), keybinds and translation keys they use, and every texture
their images and inline images draw. Items that Almost Unified replaces are errors, with the item the
player really gets (pack/config/almostunified: tags, placeholders and mod priorities).

Any-of tasks ({"any": [items and #tags]}, an FTB Filter System smart filter) need FTB Filter System and
FTB XMod Compat in catalog/curated.json. Every item they list must exist in the pinned JARs, since one
missing item voids the whole filter. Every tag must be defined by a pinned JAR, vanilla, NeoForge's own
c: tags (its universal JAR in the Gradle cache), the companion or pack/kubejs/data, and must hold at
least one item of the pinned JARs.

Registry caches live outside the repository, one file per set of JARs, so worktrees with different
locks do not rebuild each other's.
"""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

import quest_engine

ROOT = Path(__file__).resolve().parents[1]
GUIDES = ROOT / 'content' / 'guides'
VANILLA_JAR = Path('G:/Elias/Codex/Entrelumen-work/neoform-cache/artifacts/minecraft_1.21.1_client.jar')
GROUPS = {'tech', 'magic', 'exploration', 'qol', 'entrelumen'}
TYPES = {'checkmark', 'item', 'dimension', 'advancement'}
ACTS = {'I', 'II', 'III', 'IV', 'V', 'VI', 'any'}
MAX_TITLE, MAX_DESC = 60, 1100
FORMAT_CODE = re.compile(r'&(#[0-9A-Fa-f]{6}|[0-9a-fk-or])')
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
_ITEMS = None


CACHE = Path('E:/Elias/Codex/Entrelumen-ssd/research/item-registry.json')
TEXTURE_CACHE = Path('E:/Elias/Codex/Entrelumen-ssd/research/guide-emblems.json')


def signed_cache(base, signature, version):
    """The cache file for one set of JARs and one cache version."""
    digest = hashlib.sha256(json.dumps([version, signature]).encode()).hexdigest()[:12]
    return base.with_name(f'{base.stem}-{digest}{base.suffix}')
EMBLEM = re.compile(r'^([a-z0-9_.-]+):(textures/(?:items?|blocks?)/[a-z0-9_./-]+\.png)$')
_TEXTURES = None


def texture_info(ref):
    """(width, height, animated) of an 'ns:textures/...png' in the companion or the pinned JARs, or None.
    Cached outside the repository per set of JAR paths and requested textures."""
    global _TEXTURES
    m = EMBLEM.match(ref)
    if not m:
        return None
    rel = f'assets/{m.group(1)}/{m.group(2)}'
    comp = ROOT / 'companion' / 'src' / 'main' / 'resources' / rel
    if comp.exists():
        from PIL import Image
        with Image.open(comp) as im:
            return (im.width, im.height, comp.with_name(comp.name + '.mcmeta').exists())
    lp = ROOT / 'catalog' / 'local-paths.json'
    jars = sorted(json.loads(lp.read_text(encoding='utf-8')).values()) if lp.exists() else []
    if VANILLA_JAR.exists():
        jars.append(str(VANILLA_JAR))
    texture_cache = signed_cache(TEXTURE_CACHE, jars, 1)
    if _TEXTURES is None:
        _TEXTURES = {'jars': jars, 'info': {}}
        if texture_cache.exists():
            try:
                cached = json.loads(texture_cache.read_text(encoding='utf-8'))
                if cached.get('jars') == jars:
                    _TEXTURES = cached
            except Exception:
                pass
    if rel not in _TEXTURES['info']:
        # One pass over the JARs for every emblem the guides name (plus this one).
        import io
        from PIL import Image
        wanted = {rel}
        for path in GUIDES.glob('*.json'):
            try:
                mm = EMBLEM.match(json.loads(path.read_text(encoding='utf-8')).get('emblem', ''))
            except Exception:
                mm = None
            if mm:
                wanted.add(f'assets/{mm.group(1)}/{mm.group(2)}')
        wanted -= set(_TEXTURES['info'])
        for jar in jars:
            if not wanted:
                break
            try:
                with zipfile.ZipFile(jar) as z:
                    names = set(z.namelist())
                    for hit in wanted & names:
                        with Image.open(io.BytesIO(z.read(hit))) as im:
                            _TEXTURES['info'][hit] = [im.width, im.height, hit + '.mcmeta' in names]
                    wanted -= names
            except Exception:
                continue
        for miss in wanted:
            _TEXTURES['info'][miss] = None
        try:
            texture_cache.write_text(json.dumps(_TEXTURES), encoding='utf-8')
        except Exception:
            pass
    found = _TEXTURES['info'][rel]
    return tuple(found) if found else None


def bundled(jar):
    """The JAR and every JAR it bundles under META-INF/jarjar, at any depth: NeoForge loads those as mods too
    (productivelib inside Productive Bees, ponder inside Create). Each one is open only while it is yielded."""
    import io
    try:
        stack = [zipfile.ZipFile(jar)]
    except Exception:
        return
    while stack:
        with stack.pop() as z:
            yield z
            for name in z.namelist():
                if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    try:
                        stack.append(zipfile.ZipFile(io.BytesIO(z.read(name))))
                    except Exception:
                        pass


def registry():
    """Item-like ids known from item models and lang keys of every pinned JAR and the JARs they bundle, plus
    vanilla and the companion. Cached (outside the repository) per set of JAR paths, since reading 300+ JARs
    takes a while."""
    global _ITEMS
    if _ITEMS is not None:
        return _ITEMS
    items, namespaces = set(), set()
    jars = []
    lp = ROOT / 'catalog' / 'local-paths.json'
    if lp.exists():
        jars += [Path(p) for p in json.loads(lp.read_text(encoding='utf-8')).values()]
    signature = sorted(str(j) for j in jars)
    cache = signed_cache(CACHE, signature, 2)
    if cache.exists():
        try:
            c = json.loads(cache.read_text(encoding='utf-8'))
            if c.get('jars') == signature and c.get('version') == 2:
                items, namespaces = set(c['items']), set(c['namespaces'])
                comp = ROOT / 'companion' / 'src' / 'main' / 'resources' / 'assets' / 'entrelumen'
                items |= {'entrelumen:' + p.stem for p in (comp / 'models' / 'item').glob('*.json')}
                _ITEMS = (items, namespaces | {'entrelumen', 'minecraft', 'c', 'neoforge'})
                return _ITEMS
        except Exception:
            pass
    if VANILLA_JAR.exists():
        jars.append(VANILLA_JAR)
    for jar in jars:
        try:
            for z in bundled(jar):
                for name in z.namelist():
                    m = re.match(r'assets/([^/]+)/models/item/(.+)\.json$', name)
                    if m:
                        items.add(f'{m.group(1)}:{m.group(2)}')
                        namespaces.add(m.group(1))
                    m = re.match(r'assets/([^/]+)/lang/en_us\.json$', name)
                    if m:
                        try:
                            for k in json.loads(z.read(name).decode('utf-8', 'replace')):
                                mm = re.match(r'^(item|block)\.([a-z0-9_]+)\.([a-z0-9_./]+)$', k)
                                if mm:
                                    items.add(f'{mm.group(2)}:{mm.group(3)}')
                                    namespaces.add(mm.group(2))
                        except Exception:
                            pass
                    m = re.match(r'data/([^/]+)/', name)
                    if m:
                        namespaces.add(m.group(1))
        except Exception:
            continue
    try:
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_text(json.dumps({'jars': signature, 'version': 2, 'items': sorted(items),
                                     'namespaces': sorted(namespaces)}), encoding='utf-8')
    except Exception:
        pass
    comp = ROOT / 'companion' / 'src' / 'main' / 'resources' / 'assets' / 'entrelumen'
    for p in (comp / 'models' / 'item').glob('*.json'):
        items.add('entrelumen:' + p.stem)
    namespaces |= {'entrelumen', 'minecraft', 'c', 'neoforge'}
    _ITEMS = (items, namespaces)
    return _ITEMS


def text_ok(text, limit, where, errors, codes):
    if not isinstance(text, str) or not text.strip():
        errors.append(f'{where}: empty text')
        return
    plain = FORMAT_CODE.sub('', text)
    if '&' in plain:
        errors.append(f'{where}: stray "&"')
    if not codes and plain != text:
        errors.append(f'{where}: formatting codes only in descriptions')
    if len(plain) > limit:
        errors.append(f'{where}: {len(plain)} chars > {limit}')


def check_chapter(path, seen_chapters, seen_keys, errors):
    try:
        ch = json.loads(path.read_text(encoding='utf-8'))
    except Exception as e:
        errors.append(f'{path.name}: invalid JSON ({e})')
        return 0
    items, namespaces = registry()
    cid = ch.get('chapter', '')
    where = path.name
    if not re.match(r'^guide_[a-z0-9_]+$', cid):
        errors.append(f'{where}: chapter id must be guide_<name>')
    if cid in seen_chapters:
        errors.append(f'{where}: duplicate chapter id {cid}')
    seen_chapters.add(cid)
    if ch.get('group') not in GROUPS:
        errors.append(f'{where}: group must be one of {sorted(GROUPS)}')
    if ch.get('act') not in ACTS:
        errors.append(f'{where}: act must be one of {sorted(ACTS)}')
    for part in ('title', 'subtitle'):
        for lang in ('en_us', 'es_es'):
            text_ok((ch.get(part) or {}).get(lang), MAX_TITLE if part == 'title' else 160, f'{where} {part}.{lang}', errors, False)
    icon = ch.get('icon', '')
    if icon not in items:
        errors.append(f'{where}: chapter icon {icon} not found in the pinned JARs')
    emblem = ch.get('emblem', '')
    info = texture_info(emblem) if EMBLEM.match(emblem) else None
    if not EMBLEM.match(emblem):
        errors.append(f'{where}: emblem must be ns:textures/item|block/....png')
    elif info is None:
        errors.append(f'{where}: emblem {emblem} not found in the pinned JARs')
    elif info[0] != info[1] or info[0] not in (16, 32) or info[2]:
        errors.append(f'{where}: emblem {emblem} is {info[0]}x{info[1]}{" animated" if info[2] else ""}; needs a still 16 or 32 px square')
    quests = ch.get('quests') or []
    keys = {q.get('key') for q in quests}
    if len(quests) < 8:
        errors.append(f'{where}: only {len(quests)} quests; a guide chapter needs at least 8')
    positions = set()
    for q in quests:
        k = q.get('key', '')
        w = f'{where}:{k}'
        if not re.match(r'^[a-z0-9_]+$', k):
            errors.append(f'{w}: bad key')
        if k in seen_keys:
            errors.append(f'{w}: duplicate key across guides')
        seen_keys.add(k)
        for d in q.get('deps', []):
            if d not in keys:
                errors.append(f'{w}: dependency {d} not in this chapter')
        t = q.get('type', 'item' if 'item' in q or 'tag' in q else None)
        if t not in TYPES:
            errors.append(f'{w}: type must be one of {sorted(TYPES)}')
        if t == 'item':
            if 'item' not in q and 'tag' not in q:
                errors.append(f'{w}: item task needs "item" or "tag"')
            if 'item' in q and q['item'] not in items:
                errors.append(f'{w}: item {q["item"]} not found in the pinned JARs')
            if 'tag' in q and (not ID.match(q['tag']) or q['tag'].split(':')[0] not in namespaces):
                errors.append(f'{w}: tag {q["tag"]} has an unknown namespace')
            if 'tag' in q and 'item' not in q:
                errors.append(f'{w}: tag task needs the concrete "item" FTB will check (the v2 guide generator asks for one item)')
            c = q.get('count', 1)
            if not isinstance(c, int) or not 1 <= c <= 4096:
                errors.append(f'{w}: count must be 1..4096')
        if t == 'dimension' and (not ID.match(q.get('dimension', '')) or q['dimension'].split(':')[0] not in namespaces):
            errors.append(f'{w}: dimension {q.get("dimension")} unknown')
        if t == 'advancement' and (not ID.match(q.get('advancement', '')) or q['advancement'].split(':')[0] not in namespaces):
            errors.append(f'{w}: advancement {q.get("advancement")} unknown')
        if 'icon' in q and q['icon'] not in items:
            errors.append(f'{w}: icon {q["icon"]} not found in the pinned JARs')
        for lang in ('en_us', 'es_es'):
            pair = q.get(lang)
            if not (isinstance(pair, list) and len(pair) == 2):
                errors.append(f'{w}: {lang} must be [title, description]')
                continue
            text_ok(pair[0], MAX_TITLE, f'{w} {lang} title', errors, False)
            text_ok(pair[1], MAX_DESC, f'{w} {lang} description', errors, True)
        if not q.get('sources'):
            errors.append(f'{w}: no sources')
        lay = q.get('layout') or {}
        pos = (lay.get('x'), lay.get('y'))
        if None in pos:
            errors.append(f'{w}: layout needs x and y')
        elif pos in positions:
            errors.append(f'{w}: two quests at the same position {pos}')
        positions.add(pos)
    return len(quests)


COPY_MAX_DESC = 500
META_PHRASES = ('este capítulo', 'esta guía', 'en esta quest vas a', 'bienvenido a', 'aprenderás',
                'confirmá al terminar')


def copy_warnings(path):
    """Copy-style warnings, never errors (docs/design/playtest-2026-09-24.md): descriptions longer
    than COPY_MAX_DESC characters and the meta phrases the copy pass bans."""
    warnings = []
    try:
        ch = json.loads(path.read_text(encoding='utf-8'))
    except Exception:
        return warnings
    for q in ch.get('quests') or []:
        for lang in ('en_us', 'es_es'):
            pair = q.get(lang)
            if not (isinstance(pair, list) and len(pair) == 2 and isinstance(pair[1], str)):
                continue
            where = f'{path.name}:{q.get("key", "")} {lang}'
            plain = FORMAT_CODE.sub('', pair[1])
            if len(plain) > COPY_MAX_DESC:
                warnings.append(f'{where}: description {len(plain)} chars > {COPY_MAX_DESC}')
            low = plain.lower()
            for phrase in META_PHRASES:
                if phrase in low:
                    warnings.append(f'{where}: meta phrase "{phrase}"')
    return warnings


SECTOR_CACHE = Path('E:/Elias/Codex/Entrelumen-ssd/research/sector-registry.json')
_SECTOR = None


def neoforge_jar():
    """NeoForge's universal JAR for the lock's loader, from the Gradle cache (its data holds the c: tags NeoForge
    itself defines), or None."""
    lock = json.loads((ROOT / 'catalog' / 'curated.json').read_text(encoding='utf-8'))
    version = lock['loader'].removeprefix('neoforge-')
    base = Path.home() / '.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge' / version
    found = sorted(base.glob(f'*/neoforge-{version}-universal.jar'))
    return str(found[0]) if found else None


def add_tag_values(tags, tag, raw):
    """Merge one tag file's values (ids, #tags; optional entries by their id) into tags[tag]."""
    try:
        values = json.loads(raw).get('values', [])
    except Exception:
        values = []
    members = tags.setdefault(tag, [])
    for v in values:
        v = v.get('id') if isinstance(v, dict) else v
        if isinstance(v, str) and v not in members:
            members.append(v)


def sector_registry():
    """Advancements, entities, structures, structure tags, keybinds and translation keys of the pinned JARs,
    the JARs they bundle and vanilla, plus the c: item tags that Almost Unified unifies and, for any-of tasks,
    every item tag of those JARs and of NeoForge. Cached outside the repository."""
    global _SECTOR
    if _SECTOR is not None:
        return _SECTOR
    lp = ROOT / 'catalog' / 'local-paths.json'
    jars = sorted(json.loads(lp.read_text(encoding='utf-8')).values()) if lp.exists() else []
    if VANILLA_JAR.exists():
        jars.append(str(VANILLA_JAR))
    neoforge = neoforge_jar()
    cache_file = signed_cache(SECTOR_CACHE, [jars, neoforge], 5)
    if cache_file.exists():
        try:
            cached = json.loads(cache_file.read_text(encoding='utf-8'))
            if cached.get('jars') == jars and cached.get('neoforge') == neoforge and cached.get('version') == 5:
                _SECTOR = {k: set(v) if isinstance(v, list) else v for k, v in cached.items()}
                return _SECTOR
        except Exception:
            pass
    out = {'advancements': set(), 'entities': set(), 'structures': set(), 'structure_tags': set(),
           'lang': set(), 'item_tags': {}, 'item_owner': {}, 'item_tags_all': {}}
    for jar in jars:
        for z in bundled(jar):
            for name in z.namelist():
                m = re.match(r'data/([^/]+)/tags/items?/(.+)\.json$', name)
                if m:
                    add_tag_values(out['item_tags_all'], f'{m.group(1)}:{m.group(2)}', z.read(name))
                m = re.match(r'data/([^/]+)/advancements?/(.+)\.json$', name)
                if m and '/recipes/' not in name:
                    out['advancements'].add(f'{m.group(1)}:{m.group(2)}')
                m = re.match(r'data/([^/]+)/worldgen/structure/(.+)\.json$', name)
                if m:
                    out['structures'].add(f'{m.group(1)}:{m.group(2)}')
                m = re.match(r'data/([^/]+)/tags/worldgen/structure/(.+)\.json$', name)
                if m:
                    out['structure_tags'].add(f'{m.group(1)}:{m.group(2)}')
                m = re.match(r'data/c/tags/items?/(.+)\.json$', name)
                if m:
                    try:
                        values = json.loads(z.read(name)).get('values', [])
                    except Exception:
                        values = []
                    tag = out['item_tags'].setdefault('c:' + m.group(1), [])
                    for v in values:
                        v = v.get('id') if isinstance(v, dict) else v
                        if isinstance(v, str) and v not in tag:
                            tag.append(v)
                m = re.match(r'assets/([^/]+)/lang/en_us\.json$', name)
                if m:
                    try:
                        for k in json.loads(z.read(name).decode('utf-8', 'replace')):
                            if k.startswith(('key.', 'item.', 'block.', 'entity.')) or re.fullmatch(r'[a-z0-9_]+(\.[A-Za-z0-9_]+)+', k):
                                out['lang'].add(k)          # mods name keybinds outside key.* (pneumaticcraft.armor.options)
                            mm = re.match(r'^entity\.([a-z0-9_]+)\.([a-z0-9_./]+)$', k)
                            if mm:
                                out['entities'].add(f'{mm.group(1)}:{mm.group(2)}')
                    except Exception:
                        pass
    if neoforge:
        # Only NeoForge's item tags: its lang, advancements and c: values stay out of the other checks.
        for z in bundled(neoforge):
            for name in z.namelist():
                m = re.match(r'data/([^/]+)/tags/items?/(.+)\.json$', name)
                if m:
                    add_tag_values(out['item_tags_all'], f'{m.group(1)}:{m.group(2)}', z.read(name))
    cache = {k: sorted(v) if isinstance(v, set) else v for k, v in out.items()}
    cache.update(jars=jars, neoforge=neoforge, version=5)
    try:
        cache_file.write_text(json.dumps(cache), encoding='utf-8')
    except Exception:
        pass
    _SECTOR = out
    return out


def known_item_tags(reg):
    """Every item tag the pack defines: the cached JAR, vanilla and NeoForge tags plus the companion's and
    pack/kubejs/data's own tag files (read fresh, they change with the repository)."""
    tags = {tag: list(values) for tag, values in reg['item_tags_all'].items()}
    for base in (ROOT / 'companion/src/main/resources/data', ROOT / 'pack/kubejs/data'):
        for path in sorted(base.glob('*/tags/*/**/*.json')):
            parts = path.relative_to(base).parts
            if parts[2] in ('item', 'items'):
                add_tag_values(tags, parts[0] + ':' + '/'.join(parts[3:])[:-len('.json')], path.read_bytes())
    return tags


def tag_members(tag, tags, seen=()):
    members = []
    for v in tags.get(tag, []):
        if v.startswith('#'):
            if v[1:] not in seen:
                members += tag_members(v[1:], tags, seen + (tag,))
        else:
            members.append(v.split('?')[0])
    return members


def unified(item, reg):
    """The item Almost Unified hands out instead of `item`, or None when it keeps it."""
    au = ROOT / 'pack' / 'config' / 'almostunified'
    placeholders = json.loads((au / 'placeholders.json').read_text(encoding='utf-8'))
    for cfg in sorted((au / 'unification').glob('*.json')):
        conf = json.loads(cfg.read_text(encoding='utf-8'))
        priorities = conf['mod_priorities']
        for pattern in conf['tags']:
            key = re.search(r'\{(\w+)\}', pattern).group(1)
            for value in placeholders.get(key, []):
                tag = pattern.replace('{' + key + '}', value)
                if tag in conf.get('ignored_tags', []):
                    continue
                members = [m for m in tag_members(tag, reg['item_tags']) if m not in conf.get('ignored_items', [])]
                if item not in members or len({m.split(':')[0] for m in members}) < 2:
                    continue
                ranked = sorted(members, key=lambda m: priorities.index(m.split(':')[0])
                                if m.split(':')[0] in priorities else len(priorities))
                winner = ranked[0]
                if winner != item and winner.split(':')[0] in priorities:
                    return winner, tag
    return None


def check_sector(path, errors, all_keys):
    """JAR-backed checks for one sector chapter; the structure itself is tools/quest_engine.py's job."""
    data = json.loads(path.read_text(encoding='utf-8'))
    items, namespaces = registry()
    reg = sector_registry()
    where = path.name
    refs = {'items': set(), 'advancements': set(), 'entities': set(), 'structures': set(), 'keys': set(),
            'names': set(), 'textures': set()}

    def add_icon(spec):
        if isinstance(spec, str):
            refs['items'].add(spec)
        elif isinstance(spec, dict) and 'texture' in spec:
            refs['textures'].add(spec['texture'])
        elif isinstance(spec, dict) and 'entity' in spec:
            refs['entities'].add(spec['entity'])
    add_icon(data['icon'])
    refs['textures'].add(data['emblem'])
    task_items = set()
    any_tags, any_tasks = set(), 0
    for q in data['quests']:
        if 'icon' in q:
            add_icon(q['icon'])
        for t in q.get('tasks') or [q.get('task')]:
            if not t:
                continue
            kind = quest_engine.task_kind(t)
            if kind == 'item' and 'any' in t:
                any_tasks += 1
                for entry in t['any']:
                    if entry.startswith('#'):
                        any_tags.add(entry[1:])
                    else:
                        refs['items'].add(entry)
                        task_items.add(entry)
            elif kind == 'item':
                refs['items'].add(t['item'])
                task_items.add(t['item'])
            elif kind == 'advancement':
                refs['advancements'].add(t['advancement'])
            elif kind == 'kill':
                refs['entities'].add(t['entity'])
            elif kind == 'structure':
                refs['structures'].add(t['structure'])
            elif kind == 'observation':
                if t['observe'] == 'entity_type':
                    refs['entities'].add(t['target'])
                elif t['observe'] == 'block':
                    refs['items'].add(t['target'])
            if 'icon' in t:
                add_icon(t['icon'])
        for lang in ('en_us', 'es_es'):
            for para in q[lang]['text']:
                for m in re.finditer(r'\[(item|key|name):([^\]|]+)', para):
                    refs[{'item': 'items', 'key': 'keys', 'name': 'names'}[m.group(1)]].add(m.group(2))
                m = re.match(r'\{image:(\S+)', para)
                if m:
                    refs['textures'].add(m.group(1))
        for dep in q['deps']:
            if dep not in all_keys:
                errors.append(f'{where}:{q["key"]}: unknown dependency {dep}')
        if not q.get('sources'):
            errors.append(f'{where}:{q["key"]}: no sources')
    for art in data.get('art', []):
        if 'texture' in art:
            refs['textures'].add(art['texture'])
    for fig in data.get('figures', {}).values():
        for style in fig.get('draw', []):
            refs['textures'].update(style.get('textures', []))
    for item in sorted(refs['items']):
        if item not in items:
            errors.append(f'{where}: item {item} not found in the pinned JARs')
    for adv in sorted(refs['advancements']):
        if adv not in reg['advancements']:
            errors.append(f'{where}: advancement {adv} not found')
    for ent in sorted(refs['entities']):
        if ent not in reg['entities']:
            errors.append(f'{where}: entity {ent} not found')
    for st in sorted(refs['structures']):
        ok = st[1:] in reg['structure_tags'] if st.startswith('#') else st in reg['structures']
        if not ok:
            errors.append(f'{where}: structure {st} not found')
    for key in sorted(refs['keys']):
        if key not in reg['lang']:
            errors.append(f'{where}: keybind {key} not found')
    for key in sorted(refs['names']):
        if key not in reg['lang']:
            errors.append(f'{where}: translation key {key} not found')
    for tex in sorted(refs['textures']):
        rel = ROOT / 'companion/src/main/resources/assets' / tex.split(':')[0] / tex.split(':', 1)[1]
        if rel.exists():
            continue
        info = texture_info(tex) if EMBLEM.match(tex) else None
        if info is None and not any_texture(tex):
            errors.append(f'{where}: texture {tex} not found')
    for item in sorted(task_items):
        swap = unified(item, reg)
        if swap:
            errors.append(f'{where}: Almost Unified replaces {item} with {swap[0]} ({swap[1]}): ask for that one')
    if any_tasks:
        missing = [mod for mod in quest_engine.FILTER_MODS if mod not in locked_mods()]
        if missing:
            errors.append(f'{where}: any-of tasks need {", ".join(missing)} in catalog/curated.json')
        if quest_engine.SMART_FILTER not in items:
            errors.append(f'{where}: {quest_engine.SMART_FILTER} not found in the pinned JARs (catalog/local-paths.json)')
    if any_tags:
        tags = known_item_tags(reg)
        for tag in sorted(any_tags):
            if tag not in tags:
                errors.append(f'{where}: item tag #{tag} is defined by no pinned JAR, vanilla, NeoForge, the companion '
                              'or pack/kubejs/data')
            elif not any(member in items for member in tag_members(tag, tags)):
                errors.append(f'{where}: item tag #{tag} holds no item of the pinned JARs')
    return len(data['quests'])


_LOCKED = None


def locked_mods():
    """Mod ids the lock installs (catalog/curated.json)."""
    global _LOCKED
    if _LOCKED is None:
        lock = json.loads((ROOT / 'catalog' / 'curated.json').read_text(encoding='utf-8'))
        _LOCKED = {m['id'] for entry in lock['mods'] for m in entry['metadata']['mods']}
    return _LOCKED


def any_texture(ref):
    ns, path = ref.split(':', 1)
    rel = f'assets/{ns}/{path}'
    lp = ROOT / 'catalog' / 'local-paths.json'
    jars = sorted(json.loads(lp.read_text(encoding='utf-8')).values()) if lp.exists() else []
    for jar in jars + ([str(VANILLA_JAR)] if VANILLA_JAR.exists() else []):
        if Path(jar).name.split('-')[0].lower().replace('_', '') not in ns.replace('_', '') and ns not in Path(jar).name.lower():
            continue
        try:
            with zipfile.ZipFile(jar) as z:
                if rel in z.namelist():
                    return True
        except Exception:
            continue
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as z:
                if rel in z.namelist():
                    return True
        except Exception:
            continue
    return False


def check_reward_tables(errors):
    reg = sector_registry()
    items, _ = registry()
    book = json.loads((ROOT / 'content' / 'quest_book.json').read_text(encoding='utf-8'))
    for name, spec in book['reward_tables'].items():
        if name.startswith('_'):
            continue
        for item, _count, _weight in spec['rewards']:
            if item not in items:
                errors.append(f'reward table {name}: {item} not found in the pinned JARs')
            swap = unified(item, reg)
            if swap:
                errors.append(f'reward table {name}: Almost Unified replaces {item} with {swap[0]}')
        if spec['icon'] not in items:
            errors.append(f'reward table {name}: icon {spec["icon"]} not found')


def main():
    flt = sys.argv[1] if len(sys.argv) > 1 else ''
    errors, chapters, keys, warnings = [], set(), set(), []
    total = 0
    files = sorted(GUIDES.glob('*.json'))
    for p in files:
        if flt and flt not in p.stem:
            continue
        total += check_chapter(p, chapters, keys, errors)
        warnings += copy_warnings(p)
    sectors = 0
    all_keys = {q['key'] for p in files for q in json.loads(p.read_text(encoding='utf-8')).get('quests', [])}
    for p in sorted((ROOT / 'content').glob('*.json')):
        try:
            all_keys |= {q['key'] for q in json.loads(p.read_text(encoding='utf-8')).get('quests', [])}
        except Exception:
            pass
    sector_files = sorted((ROOT / 'content' / 'sectors').glob('sector_*.json'))
    for p in sector_files:
        all_keys |= {q['key'] for q in json.loads(p.read_text(encoding='utf-8'))['quests']}
    for p in sector_files:
        if flt and flt not in p.stem:
            continue
        total += check_sector(p, errors, all_keys)
        sectors += 1
    if not flt:
        check_reward_tables(errors)
    for w in warnings:
        print('WARN', w)
    for e in errors:
        print('ERROR', e)
    print(f'{"FAIL" if errors else "PASS"}: {len(chapters)} guide chapters, {sectors} sector chapters, {total} quests, '
          f'{len(errors)} errors, {len(warnings)} copy warnings')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
