"""Assemble a distributable ENTRELUMEN dedicated-server pack in a new output folder.

The pack holds the server-side dependency JARs (installed by curate_pack.py from its lock, so there is
no second JAR list), the companion JAR, pack/ without client-only paths, a server.properties.template
and start scripts. It never writes into the repository tree or into a QA server (tools/runtime.py and
tools/sync_pack.py stay the isolated QA path). It never starts a server.

    python tools/build_server_pack.py --jar companion/build/libs/entrelumen-0.1.0.jar --zip
"""
from __future__ import annotations

import argparse
import contextlib
import fnmatch
import io
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curate_pack  # noqa: E402
from runtime import NEOFORGE  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
MARKER = '.entrelumen-server-pack.json'
TEST_MARKERS = ('.entrelumen-test-server.json', '.entrelumen-test-client.json')

# Paths below pack/ that only a client uses (resources, menus, client scripts, display settings).
CLIENT_ONLY_DIRS = (
    'resourcepacks',
    'kubejs/client_scripts',
    'kubejs/assets',
    'config/fancymenu',
    'config/defaultoptions',
    'config/jei',
)
CLIENT_ONLY_FILES = (
    'config/dynamic_fps.json',
    'config/jei-client.ini',
    'config/entrelumen/client-preset.json',
)
CLIENT_ONLY_GLOBS = ('config/*-client.*', 'config/*-client-*.*')

JVM_ARGS = (
    '# JVM arguments read by start.sh / start.bat. The NeoForge installer keeps this file if it exists.\n'
    '# 6 GB is the recommended heap for up to 10 players; raise it only if the host has the RAM.\n'
    '-Xmx6G\n'
)
EULA = '# https://aka.ms/MinecraftEULA\n# Read the Minecraft EULA and set eula=true to start the server.\neula=false\n'

START_SH = """#!/usr/bin/env sh
# ENTRELUMEN dedicated server launcher: NeoForge @NEOFORGE@, Minecraft 1.21.1, Java 21.
set -eu
cd "$(dirname "$0")"
NEOFORGE=@NEOFORGE@
INSTALLER="neoforge-$NEOFORGE-installer.jar"
ARGS="libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt"
URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/$INSTALLER"

command -v java >/dev/null 2>&1 || { echo "Java 21 is required and was not found on PATH." >&2; exit 1; }

# The template is copied only once: an existing server.properties is never overwritten.
[ -f server.properties ] || cp server.properties.template server.properties
[ -f eula.txt ] || printf '# https://aka.ms/MinecraftEULA\\neula=false\\n' > eula.txt

if [ ! -f "$ARGS" ]; then
  if [ ! -f "$INSTALLER" ]; then
    echo "Downloading the NeoForge $NEOFORGE installer..."
    if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$INSTALLER" "$URL"; else wget -q -O "$INSTALLER" "$URL"; fi
  fi
  if command -v curl >/dev/null 2>&1; then EXPECTED=$(curl -fsSL "$URL.sha256"); else EXPECTED=$(wget -q -O - "$URL.sha256"); fi
  EXPECTED=$(printf '%s' "$EXPECTED" | tr -d '\\r\\n ' | cut -c1-64)
  if command -v sha256sum >/dev/null 2>&1; then ACTUAL=$(sha256sum "$INSTALLER" | cut -d' ' -f1); else ACTUAL=$(shasum -a 256 "$INSTALLER" | cut -d' ' -f1); fi
  if [ "$EXPECTED" != "$ACTUAL" ]; then echo "The NeoForge installer checksum does not match the official one; delete $INSTALLER and retry." >&2; exit 1; fi
  # The installer may rewrite user_jvm_args.txt; keep ours.
  cp user_jvm_args.txt user_jvm_args.txt.keep
  java -jar "$INSTALLER" --installServer .
  mv -f user_jvm_args.txt.keep user_jvm_args.txt
fi

exec java @user_jvm_args.txt "@$ARGS" nogui "$@"
"""

START_BAT = """@echo off
rem ENTRELUMEN dedicated server launcher: NeoForge @NEOFORGE@, Minecraft 1.21.1, Java 21.
setlocal
cd /d "%~dp0"
set NEOFORGE=@NEOFORGE@
set INSTALLER=neoforge-%NEOFORGE%-installer.jar
set ARGS=libraries\\net\\neoforged\\neoforge\\%NEOFORGE%\\win_args.txt
set URL=https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE%/%INSTALLER%

where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 is required and was not found on PATH.
  exit /b 1
)

rem The template is copied only once: an existing server.properties is never overwritten.
if not exist server.properties copy server.properties.template server.properties >nul
if not exist eula.txt (
  echo # https://aka.ms/MinecraftEULA> eula.txt
  echo eula=false>> eula.txt
)

if exist "%ARGS%" goto run
if not exist "%INSTALLER%" (
  echo Downloading the NeoForge %NEOFORGE% installer...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%INSTALLER%'"
  if errorlevel 1 exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; $c=(Invoke-WebRequest -UseBasicParsing -Uri '%URL%.sha256').Content; if ($c -is [byte[]]) { $c=[Text.Encoding]::ASCII.GetString($c) }; $e=$c.Trim().Split()[0]; $a=(Get-FileHash '%INSTALLER%' -Algorithm SHA256).Hash; if ($e -ine $a) { Write-Host 'The NeoForge installer checksum does not match the official one; delete the installer and retry.'; exit 1 }"
if errorlevel 1 exit /b 1
rem The installer may rewrite user_jvm_args.txt; keep ours.
copy /y user_jvm_args.txt user_jvm_args.txt.keep >nul
java -jar "%INSTALLER%" --installServer .
if errorlevel 1 exit /b 1
move /y user_jvm_args.txt.keep user_jvm_args.txt >nul

:run
java @user_jvm_args.txt @%ARGS% nogui %*
"""


def is_client_only(rel: str) -> bool:
    """True for a pack-relative POSIX path that only a client uses."""
    if any(rel == d or rel.startswith(d + '/') for d in CLIENT_ONLY_DIRS):
        return True
    return rel in CLIENT_ONLY_FILES or any(fnmatch.fnmatchcase(rel, g) for g in CLIENT_ONLY_GLOBS)


def server_properties(pvp: bool) -> str:
    """Template, never an in-place file: the launcher copies it only when server.properties is absent."""
    values = [
        ('allow-flight', 'true'),  # required: vanilla kicks aircraft and jetpack pilots after ~160 flying ticks
        ('spawn-protection', '0'),
        ('simulation-distance', '6'),
        ('view-distance', '10'),
        ('server-ip', ''),
        ('level-seed', ''),
        ('max-players', '10'),
        ('motd', 'ENTRELUMEN - The Living Atlas'),
        ('pvp', 'true' if pvp else 'false'),
    ]
    return '# ENTRELUMEN server.properties template. start.sh / start.bat copy it to server.properties only if none exists.\n' + ''.join(
        f'{key}={value}\n' for key, value in values
    )


def copy_pack(source: Path, destination: Path) -> int:
    """Copy source (pack/) into destination without client-only paths; return the file count."""
    count = 0
    for path in sorted(source.rglob('*')):
        if not path.is_file():
            continue
        rel = path.relative_to(source).as_posix()
        if is_client_only(rel):
            continue
        target = destination / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        count += 1
    return count


def write_text(path: Path, text: str, newline: str = '\n') -> None:
    path.write_bytes(text.replace('\r\n', '\n').replace('\n', newline).encode('utf-8'))


def write_launchers(destination: Path, pvp: bool) -> None:
    write_text(destination / 'server.properties.template', server_properties(pvp))
    write_text(destination / 'eula.txt', EULA)
    write_text(destination / 'user_jvm_args.txt', JVM_ARGS)
    write_text(destination / 'start.sh', START_SH.replace('@NEOFORGE@', NEOFORGE))
    (destination / 'start.sh').chmod(0o755)
    write_text(destination / 'start.bat', START_BAT.replace('@NEOFORGE@', NEOFORGE), '\r\n')


def find_companion_jar() -> Path:
    candidates = [p for p in (ROOT / 'companion/build/libs').glob('entrelumen-*.jar')
                  if not p.stem.endswith(('-sources', '-dev', '-slim', '-qa'))]
    if not candidates:
        raise SystemExit('No built companion JAR found; build it or pass --jar')
    return max(candidates, key=lambda p: p.stat().st_mtime)


def check_destination(destination: Path) -> None:
    """Refuse the repository tree, a QA server or client, and any folder this tool did not create."""
    if destination == ROOT or destination.is_relative_to(ROOT):
        raise SystemExit('Refusing to write the server pack inside the repository tree')
    if ROOT.is_relative_to(destination):
        raise SystemExit('Refusing an output folder that contains the repository')
    for folder in (destination, *destination.parents):
        if any((folder / m).exists() for m in TEST_MARKERS):
            raise SystemExit(f'Refusing a QA instance: {folder}')
    if destination.exists() and not (destination / MARKER).is_file():
        if not destination.is_dir() or any(destination.iterdir()):
            raise SystemExit('Refusing a non-empty folder that is not a previous server pack')


def remove_previous(destination: Path) -> None:
    """Delete a previous server pack folder; stop on any link, never follow one."""
    for current, dirs, files in os.walk(destination):
        for name in dirs + files:
            path = Path(current, name)
            if path.is_symlink() or path.is_junction():
                raise SystemExit(f'Refusing to delete {destination}: it holds a link: {path}')
    def writable(function, path, _error):
        os.chmod(path, stat.S_IWRITE)
        function(path)

    shutil.rmtree(destination, onexc=writable)


def install_jars(destination: Path) -> int:
    """Server-side JARs from the lock, installed by curate_pack.py itself; returns its exit code."""
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        old = sys.argv
        sys.argv = ['curate_pack.py', '--side', 'server', '--install', str(destination)]
        try:
            code = curate_pack.main()
        finally:
            sys.argv = old
    if code:
        raise SystemExit('curate_pack.py rejected the server lock:\n' + out.getvalue())
    return code


def build(output: Path, jar: Path, pvp: bool = False, make_zip: bool = False) -> dict:
    destination = output.resolve()
    jar = jar.resolve()
    if not jar.is_file() or not jar.name.startswith('entrelumen-') or jar.suffix != '.jar':
        raise SystemExit('Expected a built entrelumen-*.jar')
    check_destination(destination)
    if destination.exists():
        remove_previous(destination)
    destination.mkdir(parents=True)
    write_text(destination / MARKER, json.dumps({'owner': 'entrelumen', 'kind': 'server-pack', 'neoforge': NEOFORGE}, indent=2) + '\n')
    install_jars(destination)
    shutil.copy2(jar, destination / 'mods' / jar.name)
    files = copy_pack(ROOT / 'pack', destination)
    write_launchers(destination, pvp)
    result = {'output': str(destination), 'neoforge': NEOFORGE, 'packFiles': files,
              'mods': len(list((destination / 'mods').glob('*.jar')))}
    if make_zip:
        result['zip'] = str(zip_folder(destination))
    return result


def zip_folder(destination: Path) -> Path:
    archive = Path(str(destination) + '.zip')
    if archive.exists():
        archive.unlink()
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(destination.rglob('*')):
            if not path.is_file():
                continue
            info = zipfile.ZipInfo.from_file(path, Path(destination.name, path.relative_to(destination)).as_posix())
            info.compress_type = zipfile.ZIP_DEFLATED
            if path.suffix == '.sh':
                info.external_attr = (stat.S_IFREG | 0o755) << 16
            bundle.writestr(info, path.read_bytes())
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--output', type=Path, default=ROOT.parent / 'server-pack',
                        help='new folder outside the repository (default: server-pack next to it)')
    parser.add_argument('--jar', type=Path, help='built entrelumen-*.jar (default: newest in companion/build/libs)')
    parser.add_argument('--pvp', choices=['true', 'false'], default='false', help='pvp in the template (default false)')
    parser.add_argument('--zip', action='store_true', help='also write <output>.zip')
    args = parser.parse_args()
    result = build(args.output, args.jar or find_companion_jar(), args.pvp == 'true', args.zip)
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
