"""Fail when two ENTRELUMEN key bindings act at the same time.

Reads the shipped Default Options fragment (pack/config/defaultoptions/keybindings.txt), falls back to
each key's own default from tools/keybind_contexts.json, and compares every pair of bound keys:

- same key and modifier, both able to act in the same place (the world, or the same screen);
- keys read as raw key codes (Ars Nouveau) also clash with any modifier on the same base key;
- a press action on a bare modifier key (Left Alt...) clashes with every combination using that modifier;
- the fixed owners (G Curios, V Ultimine, B backpack, K claims) must keep their key, and no Shift+/Ctrl+
  world binding may sit on their letter;
- a Shift+ or Ctrl+ world binding may not sit on a key that also has a plain world binding while that
  modifier is held in play (see below).

NeoForge 21.1.249 (KeyMappingLookup.getAll) fires a combination's own bindings instead of the bare key's
while its modifier is held, so Alt+V and V do not both fire. Sneak is Shift and sprint is Ctrl, both held
unless the options say toggleCrouch/toggleSprint (or the sprint/sneak key is not a Shift/Ctrl key). While
one is down, a Shift+/Ctrl+ binding fires instead of the bare key: Ctrl+T would open the World Tier
screen instead of chat for a player running. That shadow is a clash. Alt is not held in play, so Alt+
combinations are the safe place for the pack's own modified bindings. A mod's own default combination
on its own key (same mod) is its design and passes; "shadowAllowed" in the JSON lists reasoned exceptions.

    python tools/check_keybinds.py                          # the shipped preset (exit 1 on a clash)
    python tools/check_keybinds.py --options FILE           # a player's options.txt, read only
    python tools/check_keybinds.py --options FILE --simulate-preset   # that file plus the preset
    python tools/check_keybinds.py --options FILE --diff    # preset keys the player does not have
    python tools/check_keybinds.py --table                  # also print the bound world keys
"""
from __future__ import annotations

import argparse
import itertools
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTEXTS = ROOT / 'tools/keybind_contexts.json'
PRESET = ROOT / 'pack/config/defaultoptions/keybindings.txt'
PRESET_OPTIONS = ROOT / 'pack/config/defaultoptions/options.txt'
UNBOUND = 'key.keyboard.unknown'
MODIFIER_KEYS = {'ALT': {'key.keyboard.left.alt', 'key.keyboard.right.alt'},
                 'CONTROL': {'key.keyboard.left.control', 'key.keyboard.right.control'},
                 'SHIFT': {'key.keyboard.left.shift', 'key.keyboard.right.shift'}}


def parse_options(text: str) -> dict[str, str]:
    out = {}
    for line in text.splitlines():
        if line.startswith('key_') and ':' in line:
            name, value = line[4:].split(':', 1)
            if name in out:
                raise ValueError(f'Duplicate binding: {name}')
            out[name] = value
    return out


def parse_plain_options(text: str) -> dict[str, str]:
    """The non-key lines of an options.txt (toggleSprint, toggleCrouch, narratorHotkey...)."""
    return {name: value for name, _, value in (line.partition(':') for line in text.splitlines())
            if name and not name.startswith('key_') and _}


def held_in_play(binds: dict[str, str], plain: dict[str, str]) -> set[str]:
    """Modifiers a player holds while moving: sprint on a Ctrl key and sneak on a Shift key, unless toggled."""
    held = set()
    for action, toggle, default in (('key.sprint', 'toggleSprint', 'key.keyboard.left.control'),
                                    ('key.sneak', 'toggleCrouch', 'key.keyboard.left.shift')):
        if plain.get(toggle) != 'true':
            held |= {m for m, codes in MODIFIER_KEYS.items() if split(binds.get(action, default))[0] in codes}
    return held


def split(value: str) -> tuple[str, str]:
    key, _, modifier = value.partition(':')
    return key, modifier or 'NONE'


def effective(data: dict, bindings: dict[str, str], preset_mode: bool,
              plain: dict[str, str] | None = None) -> tuple[dict[str, str], list[str]]:
    keys, errors, out = data['keys'], [], {}
    for name in bindings:
        if name not in keys and bindings[name] != UNBOUND:
            errors.append(f'{name}: bound to {bindings[name]} but has no context in {CONTEXTS.name}')
    for name, entry in keys.items():
        if name in bindings:
            out[name] = bindings[name]
        elif preset_mode and entry.get('default'):
            out[name] = entry['default']
        elif preset_mode:
            errors.append(f'{name}: no preset line and no recorded default')
    if (plain or {}).get('narratorHotkey') == 'false' and 'options.narrator' in out:
        out['options.narrator'] = UNBOUND  # vanilla's narrator hotkey is off: Ctrl+B does nothing
    return out, errors


def places(entry: dict) -> set[str]:
    return set(entry['contexts'])


def namespace(name: str) -> str:
    """Mod part of a key name: key.jei.showRecipe -> jei, create.keyinfo.toolbelt -> create."""
    parts = name.split('.')
    return parts[1] if parts[0] in ('key', 'keybind', 'keyinfo') and len(parts) > 2 else parts[0]


def screen_meet(sa: str, sb: str, parents: dict[str, str]) -> str | None:
    if sa == sb or sb == 'gui':
        return sa
    if sa == 'gui':
        return sb
    ida, idb = sa.removeprefix('screen:'), sb.removeprefix('screen:')
    if parents.get(ida) == idb:
        return sa
    if parents.get(idb) == ida:
        return sb
    return None


def meet(a: dict, b: dict, parents: dict[str, str]) -> str | None:
    """Where two keys can act together, or None."""
    pa, pb = places(a), places(b)
    if 'world' in pa and 'world' in pb:
        ga, gb = a.get('gate'), b.get('gate')
        kinds = {g.split(':')[0] for g in (ga, gb) if g}
        if not (ga and gb and ga != gb and len(kinds) == 1 and kinds <= {'held', 'riding'}):
            return 'world'  # one item per hand slot and one vehicle at a time keep held:/riding: gates apart
    ha, hb = a.get('hover'), b.get('hover')
    if ha and hb and ha != hb and 'slot' not in (ha, hb):
        return None  # each acts only on its own kind of thing under the cursor
    if 'jei' in (ha, hb) and ha != hb:
        return None  # JEI's list and recipe screens are not inventory slots
    for sa, sb in itertools.product(pa - {'world'}, pb - {'world'}):
        where = screen_meet(sa, sb, parents)
        if where:
            return where
    return None


def check(data: dict, binds: dict[str, str],
          held: set[str] | None = None) -> tuple[list[dict], list[dict]]:
    held = {'SHIFT', 'CONTROL'} if held is None else held
    keys = data['keys']
    parents = data.get('screenParents', {})
    aliases = {frozenset(a['keys']): a['reason'] for a in data['aliases']}
    exclusive = {frozenset(e['gates']) for e in data['exclusiveGates']}
    bound = {n: split(v) for n, v in binds.items() if v != UNBOUND}
    clashes, allowed = [], []
    for key, info in data.get('protectedKeys', {}).items():
        if binds.get(info['owner']) != key:
            clashes.append({'keys': [info['owner']], 'bindings': [binds.get(info['owner'], 'missing')], 'where': 'world',
                            'why': f'fixed owner must stay on {key}: {info["reason"]}'})
    for a, b in itertools.combinations(sorted(bound), 2):
        (ka, ma), (kb, mb) = bound[a], bound[b]
        ea, eb = keys[a], keys[b]
        same = (ka, ma) == (kb, mb)
        raw = (ea.get('raw') or eb.get('raw')) and ka == kb
        if not (same or raw):
            continue
        if ea.get('hold') and eb.get('hold'):
            continue  # held modifiers are designed to be held together
        if (ea.get('hold') or eb.get('hold')) and any(ka in codes for codes in MODIFIER_KEYS.values()):
            continue  # a press on a bare modifier key is judged against every combination below
        if namespace(a) == namespace(b):
            continue  # one mod dispatching its own shared default (JEI cheat mode, Create modifiers)
        where = meet(ea, eb, parents)
        if where is None or {ea.get('gate'), eb.get('gate')} in exclusive:
            continue
        record = {'keys': [a, b], 'bindings': [binds[a], binds[b]], 'where': where,
                  'why': 'same binding' if same else 'raw key code ignores the modifier'}
        reason = aliases.get(frozenset((a, b)))
        (allowed if reason else clashes).append(record | ({'reason': reason} if reason else {}))
    # A press action on a bare modifier key fires before every combination that uses that modifier.
    for name, (key, mod) in bound.items():
        entry = keys[name]
        modifier = next((m for m, codes in MODIFIER_KEYS.items() if key in codes), None)
        if not modifier or entry.get('hold') or 'world' not in places(entry):
            continue
        users = sorted(n for n, (k, m) in bound.items() if m == modifier and 'world' in places(keys[n]))
        if users:
            clashes.append({'keys': [name] + users[:3], 'bindings': [binds[name]], 'where': 'world',
                            'why': f'press on {key} also starts {len(users)} {modifier}+ combinations'})
    # Sneak (Shift) and sprint (Ctrl) are held in play. While one is down, NeoForge fires a Shift+/Ctrl+
    # binding instead of the bare key, so the combination hides the bare key: a clash unless it is a mod's
    # own default combination on its own key, or a reasoned exception in shadowAllowed.
    protected = data.get('protectedKeys', {})
    shadow_ok = {frozenset(e['keys']): e['reason'] for e in data.get('shadowAllowed', [])}
    for name, (key, mod) in sorted(bound.items()):
        entry = keys[name]
        if mod not in held or 'world' not in places(entry) or entry.get('gateInContext'):
            continue
        shown = 'sneaking' if mod == 'SHIFT' else 'running with Ctrl'
        for other, (k2, m2) in sorted(bound.items()):
            e2 = keys[other]
            if k2 != key or m2 != 'NONE' or 'world' not in places(e2) or e2.get('hold'):
                continue
            if namespace(other) == namespace(name) and binds[name] == entry.get('default'):
                continue  # the mod's own default combination on its own key
            record = {'keys': [name, other], 'bindings': [binds[name], binds[other]], 'where': 'world'}
            reason = shadow_ok.get(frozenset((name, other)))
            if reason:
                allowed.append(record | {'why': 'shadow', 'reason': reason})
            elif mod == 'SHIFT' and e2.get('sneakVariant'):
                clashes.append(record | {'why': f'{other} reads Shift+{key} itself'})
            elif protected.get(key, {}).get('owner') == other and not entry.get('vanilla'):
                clashes.append(record | {'why': f'hides the fixed owner of {key} while {shown}'})
            else:
                clashes.append(record | {'why': f'hides {key} ({other}) while {shown}: the combination fires instead'})
    return clashes, allowed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--options', type=Path, help="audit a player's options.txt instead of the shipped preset")
    parser.add_argument('--simulate-preset', action='store_true', help='with --options: apply the preset in memory')
    parser.add_argument('--diff', action='store_true', help='with --options: list preset keys the file differs on')
    parser.add_argument('--table', action='store_true', help='print every bound world key')
    parser.add_argument('--json', action='store_true', help='machine-readable report')
    args = parser.parse_args()
    if (args.simulate_preset or args.diff) and not args.options:
        parser.error('--simulate-preset and --diff need --options')
    data = json.loads(CONTEXTS.read_text(encoding='utf-8'))
    source = args.options or PRESET
    raw = parse_options(source.read_text(encoding='utf-8'))
    preset = parse_options(PRESET.read_text(encoding='utf-8'))
    if args.diff:
        differs = {k: (raw.get(k), v) for k, v in preset.items() if raw.get(k) != v}
        for k, (have, want) in sorted(differs.items()):
            print(f'{k}: {have or "absent"} -> {want}')
        print(f'{len(differs)} of {len(preset)} preset keys differ in {source.name} (read only, nothing written)')
        return 0
    plain = parse_plain_options((args.options or PRESET_OPTIONS).read_text(encoding='utf-8'))
    if args.simulate_preset:
        raw.update({k: v for k, v in preset.items()})
        plain.update(parse_plain_options(PRESET_OPTIONS.read_text(encoding='utf-8')))
    binds, errors = effective(data, raw, args.options is None, plain)
    clashes, allowed = check(data, binds, held_in_play(binds, plain))
    if args.json:
        print(json.dumps({'source': str(source), 'errors': errors, 'clashes': clashes, 'allowed': allowed}, indent=2))
    else:
        if args.table:
            rows = sorted((split(v), n) for n, v in binds.items()
                          if v != UNBOUND and 'world' in data['keys'][n]['contexts'])
            for (key, mod), name in rows:
                label = key.replace('key.keyboard.', '').replace('key.mouse.', 'mouse.')
                print(f'{(mod + "+" if mod != "NONE" else "") + label:24} {name}')
        for e in errors:
            print('ERROR', e)
        for c in clashes:
            print('CLASH', c['where'], '|', ' vs '.join(f'{k} [{v}]' for k, v in zip(c['keys'], c['bindings'] + [''] * 3)),
                  '|', c['why'])
        print(f'{"FAIL" if errors or clashes else "PASS"}: {len(binds)} keys from {source.name}, '
              f'{len(clashes)} clashes, {len(allowed)} intended shares, '
              f'{len(errors)} errors')
    return 1 if errors or clashes else 0


if __name__ == '__main__':
    sys.exit(main())
