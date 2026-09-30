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

A guide with "presentation": 2 writes each quest's copy as {"title", "subtitle"?, "text": [paragraphs]} instead, and
may carry "motif", "art", "medallion" and decor quests (tools/quest_v2.py; content/guides/README.md).

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

Rewards a sector names itself ("rewards" of a quest, the chapter's "reward_tables" and the quests'
"reward_table"/"reward_choice") need items of the pinned JARs that Almost Unified keeps, counts from 1, weights
above 0, tables of the same chapter, and data components whose types the pinned JARs register as persistent
(component_registry). The values the book documents are read as the mods read them: an entity_data with its
entity id, a Productive Bees configurable bee of a bee type the pack loads, a gene of a real attribute, value and
purity.

Any-of tasks ({"any": [items and #tags]}, an FTB Filter System smart filter) need FTB Filter System and
FTB XMod Compat in catalog/curated.json. Every item they list must exist in the pinned JARs, since one
missing item voids the whole filter. Every tag must be defined by a pinned JAR, vanilla, NeoForge's own
c: tags (its universal JAR in the Gradle cache), the companion or pack/kubejs/data, and must hold at
least one item of the pinned JARs.

Images (image_budget, warnings only): every chapter is compiled in memory (generate_quests.generate_book, nothing
written) and its chapter images counted. Above IMAGE_BUDGET (700) a chapter warns, and the run prints the book
total and the heaviest chapters: FTB syncs every image at login and draws every image of an open chapter each
frame, a cost that client QA has not measured yet. A "presentation": 2 chapter also has to start as a sketch
(Elias, 28/9): at most SKETCH_SHARE of its strong images (alpha above quest_art.FAINT, and every item render) may
show before any quest is done; the rest arrive with their quests.

Guides and story chapters in presentation v2 (tools/quest_v2.py) get the sectors' JAR checks for what v2 adds: the
items, keys, translation keys, inline images and icons of their text, and the textures, sprites and item renders of
their art (check_v2_refs); the image budget and the sketch-first warning count them too (v2_chapters).

Registry caches live outside the repository, one file per set of JARs, so worktrees with different
locks do not rebuild each other's.
"""
import atexit
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path

import quest_art
import quest_engine
import quest_text
import quest_v2

ROOT = Path(__file__).resolve().parents[1]
GUIDES = ROOT / 'content' / 'guides'
VANILLA_JAR = Path('G:/Elias/Codex/Entrelumen-work/neoform-cache/artifacts/minecraft_1.21.1_client.jar')
GROUPS = {'tech', 'magic', 'exploration', 'qol', 'entrelumen'}
TYPES = {'checkmark', 'item', 'dimension', 'advancement'}
ACTS = {'I', 'II', 'III', 'IV', 'V', 'VI', 'any'}
MAX_TITLE, MAX_DESC = 60, 1100
IMAGE_BUDGET = 700     # images per chapter; ATM10's heaviest chapter has 132
SKETCH_SHARE = 0.4     # share of a v2 chapter's strong images that may show on a fresh book
FORMAT_CODE = re.compile(r'&(#[0-9A-Fa-f]{6}|[0-9a-fk-or])')
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
_ITEMS = None


CACHE = Path('E:/Elias/Codex/Entrelumen-ssd/research/item-registry.json')
TEXTURE_CACHE = Path('E:/Elias/Codex/Entrelumen-ssd/research/guide-emblems.json')
TEXTURE_INDEX = Path('E:/Elias/Codex/Entrelumen-ssd/research/texture-index.json')
EMBLEM = re.compile(r'^([a-z0-9_.-]+):(textures/(?:items?|blocks?)/[a-z0-9_./-]+\.png)$')
_TEXTURES = None


def signed_cache(base, signature, version):
    """The cache file for one set of JARs and one cache version."""
    digest = hashlib.sha256(json.dumps([version, signature]).encode()).hexdigest()[:12]
    return base.with_name(f'{base.stem}-{digest}{base.suffix}')


def write_cache(path, value):
    """Replace a cache file in one step: many check_guides runs share these files, and a reader must never load
    half of one."""
    tmp = path.with_name(f'{path.name}.{os.getpid()}.tmp')
    try:
        tmp.write_text(json.dumps(value), encoding='utf-8')
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)  # only left when the replace failed (a reader holding the file on Windows)


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
    jars = scanned_jars()
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
        atexit.register(_save_textures, texture_cache)
    if rel not in _TEXTURES['info']:
        # The index says which JAR holds it (the first in path order, as a scan would find), so only that one opens.
        import io
        from PIL import Image
        hit = texture_index()['files'].get(rel)
        info = None
        if hit:
            try:
                with zipfile.ZipFile(hit[0]) as z, Image.open(io.BytesIO(z.read(rel))) as im:
                    info = [im.width, im.height, hit[1]]
            except Exception:
                info = None
        _TEXTURES['info'][rel] = info
        _TEXTURES['dirty'] = True
    found = _TEXTURES['info'][rel]
    return tuple(found) if found else None


def _save_textures(path):
    """Write the texture sizes found in this run once, at exit, instead of once per new texture."""
    if _TEXTURES and _TEXTURES.pop('dirty', False):
        try:
            write_cache(path, _TEXTURES)
        except Exception:
            pass


def scanned_jars():
    """The pinned JARs in path order, then vanilla's client JAR: the order every texture lookup scans them in."""
    lp = ROOT / 'catalog' / 'local-paths.json'
    jars = sorted(json.loads(lp.read_text(encoding='utf-8')).values()) if lp.exists() else []
    if VANILLA_JAR.exists():
        jars.append(str(VANILLA_JAR))
    return jars


_INDEX = None


def texture_index():
    """Every PNG under assets/ in the pinned JARs and vanilla ('files': path -> [jar, animated], the first JAR in
    path order winning) and the block atlas sources they list ('singles', 'dirs'). One pass over the JARs, cached
    outside the repository per set of JARs: opening ~380 JARs for every texture made a full run take an hour."""
    global _INDEX
    if _INDEX is None:
        jars = scanned_jars()
        cache = signed_cache(TEXTURE_INDEX, jars, 1)
        if cache.exists():
            try:
                cached = json.loads(cache.read_text(encoding='utf-8'))
                if cached.get('jars') == jars:
                    _INDEX = cached
            except Exception:
                pass
        if _INDEX is None:
            files, singles, dirs = {}, set(), {'block', 'item'}
            for jar in jars:
                try:
                    with zipfile.ZipFile(jar) as z:
                        names = z.namelist()
                        raw = z.read('assets/minecraft/atlases/blocks.json')                             if 'assets/minecraft/atlases/blocks.json' in names else None
                except (OSError, zipfile.BadZipFile, KeyError):
                    continue
                present = set(names)
                for name in names:
                    if name.startswith('assets/') and name.endswith('.png') and name not in files:
                        files[name] = [jar, name + '.mcmeta' in present]
                if raw:
                    try:
                        sources = json.loads(raw).get('sources', [])
                    except ValueError:
                        sources = []
                    for src in sources:
                        kind = src.get('type', '').split(':')[-1]
                        if kind == 'single':
                            res = src['resource']
                            singles.add(res if ':' in res else 'minecraft:' + res)
                        elif kind == 'directory':
                            dirs.add(src.get('source', ''))
            _INDEX = {'jars': jars, 'files': files, 'singles': sorted(singles), 'dirs': sorted(dirs)}
            try:
                write_cache(cache, _INDEX)
            except Exception:
                pass
    return _INDEX


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


def custom_crop_items():
    """Items of the Mystical Agriculture crops that pack/config/mysticalcustomization/crops declares. MA registers
    them at load under its own namespace (<file name>_seeds and _essence), so no pinned JAR ships their models."""
    crops = ROOT / 'pack' / 'config' / 'mysticalcustomization' / 'crops'
    return {f'mysticalagriculture:{p.stem}_{kind}' for p in crops.glob('*.json') for kind in ('seeds', 'essence')}


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
                items |= custom_crop_items()
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
        write_cache(cache, {'jars': signature, 'version': 2, 'items': sorted(items),
                            'namespaces': sorted(namespaces)})
    except Exception:
        pass
    comp = ROOT / 'companion' / 'src' / 'main' / 'resources' / 'assets' / 'entrelumen'
    for p in (comp / 'models' / 'item').glob('*.json'):
        items.add('entrelumen:' + p.stem)
    items |= custom_crop_items()
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
            if ch.get('presentation', 1) >= 2:   # v2 copy: tools/quest_v2.py and quest_engine.quest_copy check the rest
                if not (isinstance(pair, dict) and isinstance(pair.get('text'), list) and pair['text']
                        and all(isinstance(t, str) and t.strip() for t in pair['text'])):
                    errors.append(f'{w}: {lang} must be {{"title", "text": [paragraphs]}} in a presentation v2 guide')
                    continue
                text_ok(pair.get('title'), MAX_TITLE, f'{w} {lang} title', errors, False)
                continue
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
    if ch.get('presentation', 1) >= 2:
        check_v2_refs(ch, where, errors)
    return sum(1 for q in quests if quest_art.is_counted(q))


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
        write_cache(cache_file, cache)
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


COMPONENT_CACHE = Path('E:/Elias/Codex/Entrelumen-ssd/research/component-registry.json')
DATA_COMPONENT_TYPE = 'net/minecraft/core/component/DataComponentType'
# Registered without a persistent codec (net.minecraft.core.component.DataComponents, 1.21.1): DataComponentPatch
# refuses them in a saved stack, and FTB Quests then shows its missing item.
TRANSIENT_COMPONENTS = {'minecraft:creative_slot_lock', 'minecraft:map_post_processing'}
# Productive Bees 13.13.5 (util/GeneAttribute, util/GeneValue): attribute genes and their values ("<attribute>.<value>");
# a "type" gene carries a bee type instead. Purity is a percentage: HoneyTreat and CombineGeneRecipe cap it at 100.
GENE_VALUES = {'productivity': ('normal', 'medium', 'high', 'very_high'),
               'endurance': ('weak', 'normal', 'medium', 'strong'),
               'temper': ('passive', 'normal', 'aggressive', 'hostile'),
               'behavior': ('diurnal', 'nocturnal', 'metaturnal'),
               'weather_tolerance': ('none', 'rain', 'any')}
CONFIGURABLE_BEE = 'productivebees:configurable_bee'
_COMPONENTS = None
_BEE_TAGS = None
CONSTANT_SIZES = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3}


def class_constants(data):
    """(string literals, every UTF-8 constant) of a class file's constant pool, or None when it is not one."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return None
    count = int.from_bytes(data[8:10], 'big')
    pos, i, utf8, literals = 10, 1, {}, []
    try:
        while i < count:
            tag = data[pos]
            if tag == 1:
                n = int.from_bytes(data[pos + 1:pos + 3], 'big')
                utf8[i] = data[pos + 3:pos + 3 + n].decode('utf-8', 'replace')
                pos += 3 + n
            elif tag in CONSTANT_SIZES:
                if tag == 8:
                    literals.append(int.from_bytes(data[pos + 1:pos + 3], 'big'))
                pos += CONSTANT_SIZES[tag]
                i += tag in (5, 6)  # longs and doubles take two slots
            else:
                return None
            i += 1
    except IndexError:
        return None
    return {utf8.get(s, '') for s in literals}, set(utf8.values())


def component_registry():
    """What reward stacks may carry, read from the pinned JARs and cached outside the repository per set of JARs:
    {'types': {namespace: [paths]}, 'bees': {bee type: [the conditions of each file that defines it]}}.

    Component types: minecraft's are the names DataComponents registers in the pinned client JAR (its obfuscated
    class found through the Mojang mappings beside it). Any other namespace gets the string literals of the
    classes that reference DataComponentType in the JARs that own it (mod id in neoforge.mods.toml or an assets
    folder), since a registration names its type there: a miss is certain, a hit only likely. Bee types are
    Productive Bees' data/<ns>/productivebees/**/<name>.json, as 'ns:name' (BeeReloadListener keeps the file
    name, and a type defined by several files exists when one of them loads), with the conditions that can
    switch each file off."""
    global _COMPONENTS
    if _COMPONENTS is not None:
        return _COMPONENTS
    lp = ROOT / 'catalog' / 'local-paths.json'
    jars = sorted(json.loads(lp.read_text(encoding='utf-8')).values()) if lp.exists() else []
    mappings = VANILLA_JAR.with_name('minecraft_1.21.1_client_mappings.txt')
    vanilla = str(VANILLA_JAR) if VANILLA_JAR.exists() and mappings.exists() else None
    cache_file = signed_cache(COMPONENT_CACHE, [jars, vanilla], 2)
    if cache_file.exists():
        try:
            cached = json.loads(cache_file.read_text(encoding='utf-8'))
            if cached.get('jars') == jars and cached.get('vanilla') == vanilla and cached.get('version') == 2:
                _COMPONENTS = {'types': {ns: set(v) for ns, v in cached['types'].items()}, 'bees': cached['bees']}
                return _COMPONENTS
        except Exception:
            pass
    types, bees = {}, {}
    if vanilla:
        m = re.search(r'^net\.minecraft\.core\.component\.DataComponents -> (\S+):$',
                      mappings.read_text(encoding='utf-8'), re.M)
        if m:
            with zipfile.ZipFile(VANILLA_JAR) as z:
                found = class_constants(z.read(m.group(1).replace('.', '/') + '.class'))
            if found:
                types['minecraft'] = {s for s in found[0] if re.fullmatch(r'[a-z0-9_./-]+', s)}
    marker = DATA_COMPONENT_TYPE.encode()
    for jar in jars:
        for z in bundled(jar):
            owners, names = set(), set()
            for name in z.namelist():
                m = re.match(r'assets/([^/]+)/', name)
                if m:
                    owners.add(m.group(1))
                if name == 'META-INF/neoforge.mods.toml':
                    owners.update(re.findall(r'(?m)^\s*modId\s*=\s*"([^"]+)"', z.read(name).decode('utf-8', 'replace')))
                elif name.endswith('.class'):
                    data = z.read(name)
                    if marker in data:
                        found = class_constants(data)
                        if found and any(DATA_COMPONENT_TYPE in u for u in found[1]):
                            names |= {s for s in found[0] if re.fullmatch(r'[a-z0-9_./-]+', s)}
                m = re.match(r'data/([^/]+)/productivebees/(?:.+/)?([^/]+)\.json$', name)
                if m:
                    try:
                        spec = json.loads(z.read(name))
                    except Exception:
                        spec = {}
                    conditions = spec.get('conditions', []) if isinstance(spec, dict) else []
                    bees.setdefault(f'{m.group(1)}:{m.group(2)}', []).append(conditions)
            for ns in owners - {'minecraft'}:
                types.setdefault(ns, set()).update(names)
    try:
        write_cache(cache_file, {'jars': jars, 'vanilla': vanilla, 'version': 2, 'bees': bees,
                                 'types': {ns: sorted(v) for ns, v in sorted(types.items())}})
    except Exception:
        pass
    _COMPONENTS = {'types': types, 'bees': bees}
    return _COMPONENTS


def companion_components():
    """Component types the companion registers (string literals of its sources that declare a DataComponentType)."""
    names = set()
    for path in (ROOT / 'companion' / 'src' / 'main' / 'java').rglob('*.java'):
        text = path.read_text(encoding='utf-8', errors='replace')
        if 'DataComponentType' in text:
            names |= set(re.findall(r'"([a-z0-9_./-]+)"', text))
    return names


def condition_holds(cond, items, tags):
    """A NeoForge load condition against the lock: True, False or None when it cannot be told statically."""
    if not isinstance(cond, dict):
        return None
    kind = cond.get('type', '')
    if kind == 'neoforge:true':
        return True
    if kind == 'neoforge:false':
        return False
    if kind == 'neoforge:mod_loaded':
        return cond.get('modid') in locked_mods() | {'minecraft', 'neoforge'}
    if kind == 'neoforge:tag_empty':
        return not any(m in items for m in tag_members(cond.get('tag', ''), tags))
    if kind == 'neoforge:not':
        inner = condition_holds(cond.get('value'), items, tags)
        return None if inner is None else not inner
    if kind in ('neoforge:and', 'neoforge:or'):
        values = [condition_holds(c, items, tags) for c in cond.get('values', [])]
        want = kind == 'neoforge:or'
        if want in values:
            return want
        return None if None in values else not want
    return None


def check_reward_components(item, components, where, errors, items, reg):
    """Every component type registered and persistent, and the values the book documents read as the mod reads them:
    an entity_data with its entity id (CustomData.CODEC_WITH_ID), a Productive Bees configurable bee of a bee type
    the pack loads, and a gene of a real attribute, value and purity."""
    registry = component_registry()
    types = registry['types']
    for key in components:
        ns, _, path = key.partition(':')
        if not ID.match(key):
            errors.append(f'{where}: component type {key!r} is not namespace:path')
        elif key in TRANSIENT_COMPONENTS:
            errors.append(f'{where}: component {key} is not persistent: a saved stack cannot carry it')
        elif ns == 'entrelumen':
            if path not in companion_components():
                errors.append(f'{where}: component {key} is not registered by the companion')
        elif not types or (ns == 'minecraft' and 'minecraft' not in types):
            continue  # no pinned JARs (or no vanilla JAR) to check against
        elif ns not in types:
            errors.append(f'{where}: component {key}: no pinned JAR owns the namespace {ns}')
        elif path not in types[ns]:
            errors.append(f'{where}: component {key} is registered by no pinned JAR of {ns}')
    entity = components.get('minecraft:entity_data')
    if entity is not None:
        if not isinstance(entity, dict) or not isinstance(entity.get('id'), str):
            errors.append(f'{where}: minecraft:entity_data needs the entity "id" (vanilla refuses it without one)')
        elif entity['id'] not in reg['entities']:
            errors.append(f'{where}: minecraft:entity_data names entity {entity["id"]}, which is not found')
        elif entity['id'] == CONFIGURABLE_BEE:
            check_bee_type(entity.get('type'), f'{where}: configurable bee', errors, items, reg)
    gene = components.get('productivebees:gene_group')
    if gene is not None:
        attribute = gene.get('attribute') if isinstance(gene, dict) else None
        value = gene.get('value') if isinstance(gene, dict) else None
        purity = gene.get('purity') if isinstance(gene, dict) else None
        if attribute == 'type':
            check_bee_type(value, f'{where}: type gene', errors, items, reg)
        elif attribute not in GENE_VALUES:
            errors.append(f'{where}: gene attribute {attribute!r} is none of type, {", ".join(GENE_VALUES)}')
        elif not (isinstance(value, str) and value.startswith(attribute + '.')
                  and value[len(attribute) + 1:] in GENE_VALUES[attribute]):
            errors.append(f'{where}: gene value {value!r} is none of '
                          + ', '.join(f'{attribute}.{v}' for v in GENE_VALUES[attribute]))
        if type(purity) is not int or not 1 <= purity <= 100:
            errors.append(f'{where}: gene purity {purity!r} is a percentage, 1..100')


def check_bee_type(bee, where, errors, items, reg):
    bees = component_registry()['bees']
    if not bees:
        return  # no pinned JARs
    if not isinstance(bee, str) or bee not in bees:
        errors.append(f'{where}: bee type {bee!r} is defined by no pinned JAR (data/<ns>/productivebees/.../<name>.json)')
        return
    global _BEE_TAGS
    if _BEE_TAGS is None or _BEE_TAGS[0] is not reg:
        _BEE_TAGS = (reg, known_item_tags(reg))
    tags = _BEE_TAGS[1]
    if all(any(condition_holds(c, items, tags) is False for c in (conditions if isinstance(conditions, list) else []))
           for conditions in bees[bee]):
        errors.append(f'{where}: bee type {bee} is switched off in this pack (the load conditions of every file '
                      'that defines it fail)')


def check_sector_rewards(data, where, errors, items, reg):
    """Rewards a sector names itself (tools/quest_engine.py, extra_rewards and build_local_tables): items that
    exist, counts from 1, components the pinned JARs register, tables that exist in the chapter. Returns the
    items, for the Almost Unified check."""
    rewarded = set()
    tables = data.get('reward_tables', {})
    if not isinstance(tables, dict):
        errors.append(f'{where}: reward_tables maps names to tables')
        tables = {}

    def stack(spec, w, weighted=False):
        if not isinstance(spec, dict) or not isinstance(spec.get('item'), str):
            errors.append(f'{w}: a reward names an "item"')
            return
        item = spec['item']
        rewarded.add(item)
        if item not in items:
            errors.append(f'{w}: item {item} not found in the pinned JARs')
        count = spec.get('count', 1)
        if type(count) is not int or count < 1:
            errors.append(f'{w}: count {count!r} must be a whole number from 1')
        if weighted:
            weight = spec.get('weight', 1)
            if type(weight) not in (int, float) or not weight > 0:
                errors.append(f'{w}: weight {weight!r} must be above 0')
        components = spec.get('components', {})
        if not isinstance(components, dict):
            errors.append(f'{w}: components map component types to values')
        elif components:
            check_reward_components(item, components, f'{w} ({item})', errors, items, reg)

    for name, spec in tables.items():
        entries = spec.get('entries') if isinstance(spec, dict) else None
        if not isinstance(entries, list) or not entries:
            errors.append(f'{where}: reward table {name} has no entries')
            continue
        for i, entry in enumerate(entries):
            stack(entry, f'{where}: reward table {name}, entry {i}', weighted=True)
    used = set()
    for q in data['quests']:
        w = f'{where}:{q["key"]}'
        rewards = q.get('rewards', [])
        if not isinstance(rewards, list):
            errors.append(f'{w}: rewards is a list of items')
            rewards = []
        for i, spec in enumerate(rewards):
            stack(spec, f'{w}: reward {i}')
        for field in ('reward_table', 'reward_choice'):
            if field in q:
                name = q[field] if isinstance(q[field], str) else None
                used.add(name)
                if name not in tables:
                    errors.append(f'{w}: {field} {q[field]!r} is not in this chapter\'s reward_tables')
    for name in sorted(set(tables) - used):
        errors.append(f'{where}: reward table {name} is used by no quest')
    return rewarded


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
    glyph_textures = set()
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
            text_refs(q[lang]['text'], refs, glyph_textures)
        for dep in q['deps']:
            if dep not in all_keys:
                errors.append(f'{where}:{q["key"]}: unknown dependency {dep}')
        if not q.get('sources'):
            errors.append(f'{where}:{q["key"]}: no sources')
    drawn_refs(data, where, refs, glyph_textures, errors)
    for fig in data.get('figures', {}).values():
        for style in fig.get('draw', []):
            refs['textures'].update(style.get('textures', []))
    for item in sorted(refs['items']):
        if item not in items:
            errors.append(f'{where}: item {item} not found in the pinned JARs')
    rewarded = check_sector_rewards(data, where, errors, items, reg)
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
    check_textures(where, refs['textures'], errors)
    for item in sorted(task_items):
        swap = unified(item, reg)
        if swap:
            errors.append(f'{where}: Almost Unified replaces {item} with {swap[0]} ({swap[1]}): ask for that one')
    for item in sorted(rewarded):
        swap = unified(item, reg)
        if swap:
            errors.append(f'{where}: Almost Unified replaces {item} with {swap[0]} ({swap[1]}): reward that one')
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
    return sum(1 for q in data['quests'] if quest_art.is_counted(q))


def text_refs(paragraphs, refs, glyph_textures):
    """What a paragraph list of v2 markup names: items, keybinds, translation keys, inline images and icon glyphs."""
    for para in paragraphs:
        for m in re.finditer(r'\[(item|key|name):([^\]|]+)', para):
            refs[{'item': 'items', 'key': 'keys', 'name': 'names'}[m.group(1)]].add(m.group(2))
        m = re.match(r'\{image:(\S+)', para)
        if m:
            refs['textures'].add(m.group(1))
        for ref in quest_text.icon_refs_in(para):   # presentation v2: icons drawn as font glyphs
            texture, item = quest_text.icon_texture(ref)
            glyph_textures.add(texture)
            if item:
                refs['items'].add(item)


def drawn_refs(data, where, refs, glyph_textures, errors):
    """The art list's textures and items into refs; errors for sprites outside the block atlas and animated icons."""
    for art in data.get('art', []):
        if 'texture' in art:
            refs['textures'].add(art['texture'])
    drawn = quest_art.references(data)   # presentation v2: pictures, sprites and item renders on the canvas
    refs['textures'] |= drawn['textures'] | glyph_textures
    refs['items'] |= drawn['items']
    for sprite in sorted(drawn['sprites']):
        ns, path = sprite.split(':', 1)
        if not any_texture(f'{ns}:textures/{path}.png'):
            errors.append(f'{where}: sprite {sprite} has no texture in the pinned JARs')
        elif not in_block_atlas(sprite):
            errors.append(f'{where}: sprite {sprite} is not in the block atlas (it would draw as the missing texture)')
    for tex in sorted(glyph_textures):
        info = texture_info(tex) if EMBLEM.match(tex) else None
        if info and info[2]:
            errors.append(f'{where}: icon {tex} is animated: a font glyph would squash every frame')


def check_textures(where, textures, errors):
    for tex in sorted(textures):
        rel = ROOT / 'companion/src/main/resources/assets' / tex.split(':')[0] / tex.split(':', 1)[1]
        if rel.exists():
            continue
        info = texture_info(tex) if EMBLEM.match(tex) else None
        if info is None and not any_texture(tex):
            errors.append(f'{where}: texture {tex} not found')


def check_v2_refs(data, where, errors):
    """JAR checks for what presentation v2 adds to a guide or story chapter: its text and its art. Tasks, icons and
    the emblem keep their own checks (check_chapter; the story's in tools/generate_quests.py and the server)."""
    items, _ = registry()
    reg = sector_registry()
    refs = {'items': set(), 'keys': set(), 'names': set(), 'textures': set()}
    glyph_textures = set()
    for q in data['quests']:
        for lang in ('en_us', 'es_es'):
            if isinstance(q.get(lang), dict):
                text_refs(q[lang].get('text', []), refs, glyph_textures)
    drawn_refs(data, where, refs, glyph_textures, errors)
    for item in sorted(refs['items']):
        if item not in items:
            errors.append(f'{where}: item {item} not found in the pinned JARs')
    for key in sorted(refs['keys']):
        if key not in reg['lang']:
            errors.append(f'{where}: keybind {key} not found')
    for key in sorted(refs['names']):
        if key not in reg['lang']:
            errors.append(f'{where}: translation key {key} not found')
    check_textures(where, refs['textures'], errors)


def v2_chapters():
    """Names of the compiled chapters in presentation v2: sectors, guides and story chapters."""
    import generate_quests
    return {d['chapter'] for d in quest_engine.load_sectors() + quest_text.v2_chapters(ROOT)
            if d.get('presentation', 1) >= 2} | {d['chapter'] for d in generate_quests.load_chapters()
                                                  if d.get('presentation', 1) >= 2}


_LOCKED = None


def locked_mods():
    """Mod ids the lock installs (catalog/curated.json)."""
    global _LOCKED
    if _LOCKED is None:
        lock = json.loads((ROOT / 'catalog' / 'curated.json').read_text(encoding='utf-8'))
        _LOCKED = {m['id'] for entry in lock['mods'] for m in entry['metadata']['mods']}
    return _LOCKED


def in_block_atlas(sprite):
    """Whether the block atlas stitches a sprite: vanilla's directory sources (block/, item/) or a single or
    directory source some pinned JAR lists in assets/minecraft/atlases/blocks.json."""
    index = texture_index()
    path = sprite.split(':', 1)[1]
    return sprite in index['singles'] or any(path.startswith(d + '/') for d in index['dirs'] if d)


def any_texture(ref):
    ns, path = ref.split(':', 1)
    rel = f'assets/{ns}/{path}'
    if rel.endswith('.png'):
        return rel in texture_index()['files']
    for jar in scanned_jars():
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


def image_budget(warnings, flt=()):
    """Images of every compiled chapter: a warning above IMAGE_BUDGET or for a v2 chapter that does not start as a
    sketch; prints the book total and the heaviest chapters."""
    import generate_quests
    files = generate_quests.generate_book()
    v2 = v2_chapters()
    counts = {}
    for path, text in files.items():
        if path.parent.name != 'chapters' or path.suffix != '.snbt':
            continue
        images = json.loads(text).get('images', [])
        counts[path.stem] = len(images)
        if not picked(path.stem, flt):
            continue
        if len(images) > IMAGE_BUDGET:
            warnings.append(f'{path.stem}: {len(images)} images, over the budget of {IMAGE_BUDGET}: one large picture '
                            'instead of many chips where it reads the same')
        if path.stem in v2:
            strong = [i for i in images if i['image'].startswith('item:') or i.get('alpha', 255) > quest_art.FAINT]
            fresh = [i for i in strong if not i.get('dependency')]
            if strong and len(fresh) > SKETCH_SHARE * len(strong):
                warnings.append(f'{path.stem}: {len(fresh)} of {len(strong)} strong images show before any quest '
                                f'({len(fresh) / len(strong):.0%}, limit {SKETCH_SHARE:.0%}): start as a sketch, and let '
                                'paths, scene pieces, props and light arrive with their quests (reveal, grow)')
    ranked = sorted(counts.items(), key=lambda kv: -kv[1])
    print(f'IMAGES: {sum(counts.values())} in the book ({len(counts)} chapters); heaviest: '
          + ', '.join(f'{name} {n}' for name, n in ranked[:3]))


def picked(name, flt, *others):
    """Whether a chapter matches the command line: no names means all of them; any name (a substring) picks it."""
    return not flt or any(f in n for f in flt for n in (name,) + others)


def main():
    flt = tuple(sys.argv[1:])   # chapter names or substrings; several are allowed
    errors, chapters, keys, warnings = [], set(), set(), []
    total = 0
    files = sorted(GUIDES.glob('*.json'))
    for p in files:
        if not picked(p.stem, flt):
            continue
        total += check_chapter(p, chapters, keys, errors)
        warnings += copy_warnings(p)
        warnings += quest_art.lint(json.loads(p.read_text(encoding='utf-8')))
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
        if not picked(p.stem, flt):
            continue
        total += check_sector(p, errors, all_keys)
        warnings += quest_art.lint(json.loads(p.read_text(encoding='utf-8')))   # notes no player would see
        sectors += 1
    import generate_quests
    story = 0
    for name in generate_quests.CHAPTER_SOURCES:   # story chapters in presentation v2: their text and art
        data = json.loads((ROOT / 'content' / name).read_text(encoding='utf-8'))
        if data.get('presentation', 1) < 2 or not picked(data['chapter'], flt, name):
            continue
        check_v2_refs(data, name, errors)
        warnings += quest_art.lint(data)
        story += 1
        total += sum(1 for q in data['quests'] if quest_art.is_counted(q))
    if not flt:
        check_reward_tables(errors)
    try:
        image_budget(warnings, flt)
    except AssertionError as e:   # the book does not compile: generate_quests.py reports it in full
        warnings.append(f'image budget not counted: the book does not compile ({e})')
    for w in warnings:
        print('WARN', w)
    for e in errors:
        print('ERROR', e)
    print(f'{"FAIL" if errors else "PASS"}: {len(chapters)} guide chapters, {sectors} sector chapters'
          + (f', {story} v2 story chapters' if story else '') + f', {total} quests, '
          f'{len(errors)} errors, {len(warnings)} warnings')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
