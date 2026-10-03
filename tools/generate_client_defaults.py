"""Generate Default Options 21.1.8 first-launch fragments from the authored preset.

The mod copies options.txt only if no root options.txt exists. Its key handler
reads keybindings.txt separately and preserves bindings already seen by players.
Resource packs stay in the first-launch options file so existing selections are
not replaced by the mod's one-time defaultResourcePacks config handler.

The preset's "extra" section feeds the mod's extra handler: at GameConfig construction,
before any mod reads its config, it copies config/defaultoptions/extra/<path> to <path>
only if that file does not exist. Only the files in EXTRA may be seeded this way, each in
its mod's format: sorted key=value lines for Iris (shaders start off), a TOML of sorted
tables for Distant Horizons (its Overworld-only defaults). A player's later choice stays.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import tomllib


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'pack/config/entrelumen/client-preset.json'
TARGET = ROOT / 'pack/config/defaultoptions'
KEY_LINE = re.compile(r'key_([^:]+):([^:]+)(?::(.+)?)?')  # 21.1.8 KeyMappingDefaultsHandler
SAFE_RESOURCE_PACKS = {'vanilla', 'mod_resources', 'file/entrelumen'}
# Files the extra handler may seed, with their format.
EXTRA = {'config/iris.properties': 'properties', 'config/DistantHorizons.toml': 'toml'}
# A seeded TOML's _version must be the one its pinned mod writes. Distant Horizons 3.3.2
# (ConfigFileHandler.loadFromFile, iconst_4) deletes a file with an older _version and
# starts from its own defaults; a newer one only logs a warning.
TOML_VERSION = {'config/DistantHorizons.toml': 4}


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
    fragments = {'options.txt': '\n'.join(general) + '\n',
                 'keybindings.txt': '\n'.join(bindings) + '\n'}
    extra = preset.get('extra', {})
    if not isinstance(extra, dict):
        raise ValueError('Expected the extra section to map files to settings')
    for path, values in sorted(extra.items()):
        if path not in EXTRA:
            raise ValueError(f'Not a seeded extra file: {path!r}')
        if not isinstance(values, dict) or not values:
            raise ValueError(f'Expected settings for {path}')
        render_extra = render_toml if EXTRA[path] == 'toml' else render_properties
        fragments[f'extra/{path}'] = render_extra(path, values)
    return fragments


def render_properties(path: str, values: dict) -> str:
    lines = []
    for key, value in sorted(values.items()):
        if not re.fullmatch(r'[A-Za-z0-9_.]+', key) or not isinstance(value, str) \
                or not re.fullmatch(r'[A-Za-z0-9_.-]*', value):
            raise ValueError(f'Invalid property in {path}: {key!r}')
        lines.append(f'{key}={value}')
    return '\n'.join(lines) + '\n'


def toml_value(path: str, key: str, value) -> str:
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return repr(value)
    if isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9_:.,/-]*', value):
        return f'"{value}"'
    raise ValueError(f'Invalid setting in {path}: {key!r}')


def render_toml(path: str, values: dict) -> str:
    """Top-level settings first, then one [table] per nested section, keys sorted; read back to check."""
    version = values.get('_version')
    if isinstance(version, bool) or version != TOML_VERSION.get(path):
        raise ValueError(f'{path} needs _version {TOML_VERSION.get(path)}, the one its pinned mod writes')
    lines = []

    def table(name: str, node: dict) -> None:
        if not node:
            raise ValueError(f'Empty table in {path}: {name!r}')
        for key in node:
            if not isinstance(key, str) or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', key):
                raise ValueError(f'Invalid setting in {path}: {key!r}')
        settings = sorted((key, value) for key, value in node.items() if not isinstance(value, dict))
        if name and settings:
            lines.extend(['', f'[{name}]'] if lines else [f'[{name}]'])
        lines.extend(f'{key} = {toml_value(path, key, value)}' for key, value in settings)
        for key, value in sorted((key, value) for key, value in node.items() if isinstance(value, dict)):
            table(f'{name}.{key}' if name else key, value)

    table('', values)
    text = '\n'.join(lines) + '\n'
    if tomllib.loads(text) != values:
        raise ValueError(f'{path} does not read back as authored')
    return text


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
            (TARGET / name).parent.mkdir(parents=True, exist_ok=True)
            (TARGET / name).write_text(content, encoding='utf-8', newline='\n')
    else:
        stale = [name for name, content in artifacts.items()
                 if not (TARGET / name).is_file() or (TARGET / name).read_text(encoding='utf-8') != content]
        if stale:
            raise SystemExit(f'Stale Default Options fragments: {", ".join(stale)}')
    print(f'Default Options {"written" if args.write else "checked"}: '
          f'{len(artifacts["options.txt"].splitlines())} options, '
          f'{len(artifacts["keybindings.txt"].splitlines())} bindings, '
          f'{len(artifacts) - 2} seeded extra file(s)')


if __name__ == '__main__':
    main()
