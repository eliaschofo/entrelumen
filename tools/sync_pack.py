"""Install original pack files into a marked, isolated ENTRELUMEN test instance.

Does not create or change CurseForge manifests or copy launcher credentials.
Existing unrelated files are left alone. An edited managed file stops the sync.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def contains_settings(actual, desired):
    if isinstance(desired, dict):
        return isinstance(actual, dict) and all(key in actual and contains_settings(actual[key], value) for key, value in desired.items())
    return type(actual) is type(desired) and actual == desired


def has_extra_keys(actual, desired):
    if isinstance(actual, dict):
        return any(
            not isinstance(desired, dict)
            or key not in desired
            or has_extra_keys(value, desired[key])
            for key, value in actual.items()
        )
    if isinstance(actual, list):
        return any(
            has_extra_keys(value, desired[index] if isinstance(desired, list) and index < len(desired) else None)
            for index, value in enumerate(actual)
        )
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    destination = args.destination.resolve()
    markers = [destination / ".entrelumen-test-server.json", destination / ".entrelumen-test-client.json"]
    if not any(p.is_file() and json.loads(p.read_text())["owner"] == "entrelumen" for p in markers):
        raise SystemExit("Refusing an unmarked instance; prepare the isolated instance first")
    receipt_path = destination / "entrelumen-managed-files.json"
    previous = json.loads(receipt_path.read_text()) if receipt_path.exists() else {}
    sources = {p.relative_to(ROOT / "pack").as_posix(): p for p in (ROOT / "pack").rglob("*") if p.is_file()}
    jar = args.jar.resolve()
    if not jar.is_file() or not jar.name.startswith("entrelumen-") or jar.suffix != ".jar":
        raise SystemExit("Expected a built entrelumen-*.jar")
    sources["mods/" + jar.name] = jar
    pending = {}
    for name, source in sources.items():
        target = (destination / name).resolve()
        if not target.is_relative_to(destination):
            raise SystemExit(f"Unsafe destination: {name}")
        checksum = digest(source)
        if target.exists():
            actual_checksum = digest(target)
            extra_keys = False
            # NeoForge rewrites TOML comments/indentation on startup. Preserve
            # its serialization and unspecified defaults when every setting
            # explicitly owned by the pack still matches.
            if target.suffix == '.toml':
                try:
                    actual = tomllib.loads(target.read_text(encoding='utf-8'))
                    desired = tomllib.loads(source.read_text(encoding='utf-8'))
                    same_values = contains_settings(actual, desired)
                    extra_keys = has_extra_keys(actual, desired)
                except (ValueError, UnicodeError):
                    same_values = False
                if same_values:
                    pending[name] = actual_checksum
                    continue
            if actual_checksum not in {checksum, previous.get(name)}:
                raise SystemExit(f"Locally changed file needs review: {name}")
            if extra_keys:
                raise SystemExit(f"TOML update would remove additional keys; merge/review required: {name}")
        pending[name] = checksum
    stale = set(previous) - sources.keys()
    if stale:
        raise SystemExit("Managed files were removed or renamed; review before migration: " + ", ".join(sorted(stale)))
    for name, source in sources.items():
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists() or digest(target) != pending[name]:
            shutil.copy2(source, target)
    receipt_path.write_text(json.dumps(pending, indent=2, sort_keys=True) + "\n")
    print(f"Installed {len(sources)} original files; dependencies and user files preserved")


if __name__ == "__main__":
    main()
