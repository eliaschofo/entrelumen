"""Generate Default Options 21.1.8 first-launch fragments from the authored preset.

The mod copies options.txt only if no root options.txt exists. Its key handler
reads keybindings.txt separately and preserves bindings already seen by players.
Resource packs stay in the first-launch options file so existing selections are
not replaced by the mod's one-time defaultResourcePacks config handler.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'pack/config/entrelumen/client-preset.json'
TARGET = ROOT / 'pack/config/defaultoptions'
KEY_LINE = re.compile(r'key_([^:]+):([^:]+)(?::(.+)?)?')  # 21.1.8 KeyMappingDefaultsHandler
SAFE_RESOURCE_PACKS = {'vanilla', 'mod_resources', 'file/entrelumen'}


def render(preset: dict) -> dict[str, str]:
    if preset.get('schema') != 1 or not isinstance(preset.get('options'), dict):
        raise ValueError('Expected the authored client preset schema 1')
    options = preset['options']
    if 'lang' in options:
        raise ValueError('Language is a player choice, not a packaged default')
    packs = json.loads(options['resourcePacks'])
    if (not isinstance(packs, list) or not all(isinstance(pack, str) for pack in packs)
            or len(packs) != len(set(packs))
            or set(packs) != SAFE_RESOURCE_PACKS):
        raise ValueError('Only the authored, shipped resource pack selection is allowed')

    general = []
    bindings = []
    for key, value in sorted(options.items()):
        if not isinstance(key, str) or not re.fullmatch(r'[A-Za-z0-9_.-]+', key):
            raise ValueError(f'Invalid option key: {key!r}')
        if not isinstance(value, str) or '\n' in value or '\r' in value:
            raise ValueError(f'Invalid value for {key}')
        line = f'{key}:{value}'
        if key.startswith('key_'):
            match = KEY_LINE.fullmatch(line)
            if match is None or (match.group(3) and match.group(3) not in {'ALT', 'SHIFT', 'CONTROL'}):
                raise ValueError(f'Invalid Default Options binding: {key}')
            bindings.append(line)
        else:
            general.append(line)
    if not general or not bindings:
        raise ValueError('Both native default categories must be populated')
    return {'options.txt': '\n'.join(general) + '\n',
            'keybindings.txt': '\n'.join(bindings) + '\n'}


def verify_folder(target: Path, artifacts: dict[str, str]) -> None:
    if target.exists():
        unexpected = [p for p in target.rglob('*') if p.is_file()
                      and p.relative_to(target).as_posix() not in artifacts]
        if unexpected:
            raise ValueError(f'Unexpected Default Options file: {unexpected[0]}')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true', help='write generated pack fragments')
    mode.add_argument('--check', action='store_true', help='verify generated pack fragments')
    args = parser.parse_args()
    artifacts = render(json.loads(SOURCE.read_text(encoding='utf-8')))
    verify_folder(TARGET, artifacts)
    if args.write:
        TARGET.mkdir(parents=True, exist_ok=True)
        for name, content in artifacts.items():
            (TARGET / name).write_text(content, encoding='utf-8', newline='\n')
    else:
        stale = [name for name, content in artifacts.items()
                 if not (TARGET / name).is_file() or (TARGET / name).read_text(encoding='utf-8') != content]
        if stale:
            raise SystemExit(f'Stale Default Options fragments: {", ".join(stale)}')
    print(f'Default Options {"written" if args.write else "checked"}: '
          f'{len(artifacts["options.txt"].splitlines())} options, '
          f'{len(artifacts["keybindings.txt"].splitlines())} bindings')


if __name__ == '__main__':
    main()
