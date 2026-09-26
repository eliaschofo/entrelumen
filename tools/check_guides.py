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
and not animated, since FTB draws the whole PNG. No item-filter mod is installed, so a "tag" task
also names the concrete "item" that FTB checks (for unified materials, Almost Unified's choice).
Warnings, which never fail the run: descriptions over 500 characters and the meta phrases that the
copy pass bans (see copy_warnings).

    python tools/check_guides.py            # all chapters
    python tools/check_guides.py create     # chapters whose id contains 'create'
"""
import json
import re
import sys
import zipfile
from pathlib import Path

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
    if _TEXTURES is None:
        _TEXTURES = {'jars': jars, 'info': {}}
        if TEXTURE_CACHE.exists():
            try:
                cached = json.loads(TEXTURE_CACHE.read_text(encoding='utf-8'))
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
            TEXTURE_CACHE.write_text(json.dumps(_TEXTURES), encoding='utf-8')
        except Exception:
            pass
    found = _TEXTURES['info'][rel]
    return tuple(found) if found else None


def registry():
    """Item-like ids known from item models and lang keys of every pinned JAR, plus vanilla and the companion.
    Cached (outside the repository) per set of JAR paths, since reading 300+ JARs takes a while."""
    global _ITEMS
    if _ITEMS is not None:
        return _ITEMS
    items, namespaces = set(), set()
    jars = []
    lp = ROOT / 'catalog' / 'local-paths.json'
    if lp.exists():
        jars += [Path(p) for p in json.loads(lp.read_text(encoding='utf-8')).values()]
    signature = sorted(str(j) for j in jars)
    if CACHE.exists():
        try:
            c = json.loads(CACHE.read_text(encoding='utf-8'))
            if c.get('jars') == signature:
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
            with zipfile.ZipFile(jar) as z:
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
        CACHE.parent.mkdir(parents=True, exist_ok=True)
        CACHE.write_text(json.dumps({'jars': signature, 'items': sorted(items), 'namespaces': sorted(namespaces)}),
                         encoding='utf-8')
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
                errors.append(f'{w}: tag task needs the concrete "item" FTB will check (no item-filter mod)')
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
    for w in warnings:
        print('WARN', w)
    for e in errors:
        print('ERROR', e)
    print(f'{"FAIL" if errors else "PASS"}: {len(chapters)} guide chapters, {total} quests, {len(errors)} errors, '
          f'{len(warnings)} copy warnings')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
