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

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


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
        if target.exists() and digest(target) not in {checksum, previous.get(name)}:
            raise SystemExit(f"Locally changed file needs review: {name}")
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
