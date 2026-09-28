"""Export the Envés room templates from art/dungeon into the companion's resources.

For every tileset, every role, door mask and variant, its art module gives the voxels and the data
markers of one cell: tiles.template() for Osarios, kit.build() over cisternas.py, fundicion.py,
geodas.py and eclipse.py for floors II-V (kit.build also refuses a template whose fluids could
flow, whose lava is reachable or whose doors and markers are not reachable on foot). This tool
writes each as a 19x19x12 structure template with the markers as DATA structure blocks
(docs/design/dungeon-enves.md, marker contract):

    companion/src/main/resources/data/entrelumen/structure/enves/<tileset>/<role>_<mask>_<variant>.nbt
    companion/src/main/resources/data/entrelumen/enves/templates/<tileset>.json   (the index)

<mask> lists the doors in NESW order (`nes`, `w`...), `x` for none. Voxels outside the template
box are left out and counted in the index as `clipped` (the art's bone pits reach one block below
the floor layer; the companion lays an underlay there until the floor below exists). Air inside the
room (layers 1-9) is left out too: a slot is void or wiped before a floor is placed, so only the
floor and ceiling layers need their air written (the ceiling's, over the underlay of the floor
above). Output is deterministic and the tool re-runnable.

    python tools/export_enves_tiles.py                      # write every tileset
    python tools/export_enves_tiles.py --check              # verify the committed files
    python tools/export_enves_tiles.py --tileset geodas     # only one
"""
from __future__ import annotations

import argparse
import gzip
import importlib
import io
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "art/dungeon"))
import tiles  # noqa: E402
import kit  # noqa: E402

RESOURCES = ROOT / "companion/src/main/resources/data/entrelumen"
DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE = (tiles.S, tiles.H, tiles.S)
ROOM_TOP = tiles.CEIL_Y - 1  # layers 1..9 are the room: their air is not written
TILESETS = ["osarios", "cisternas", "fundicion", "geodas", "eclipse"]  # floors I-V
DIRS = "NESW"
# role -> variants; quiet and fight rooms take their shape from the variant, the rest have one
ROLES = {"quiet": 3, "fight": 3, "start": 1, "exit": 1, "guard": 1, "shrine": 1, "vault": 1, "seal": 1,
         "arena": 1, "arena_center": 1, "portal": 1}
MASKS = [[d for i, d in enumerate(DIRS) if m & (1 << i)] for m in range(1, 16)]


def mask_name(doors):
    return "".join(d.lower() for d in DIRS if d in doors) or "x"


def templates():
    """(name, role, doors, variant) for every template of a tileset."""
    out = [("vestibule_x_0", "vestibule", [], 0)]
    for role, variants in ROLES.items():
        for doors in MASKS:
            for variant in range(variants):
                out.append(("%s_%s_%d" % (role, mask_name(doors), variant), role, doors, variant))
    return sorted(out)


# --- minimal NBT writer -------------------------------------------------------------------
END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


def _string(out, value):
    data = value.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _payload(out, tag, value):
    if tag == INT:
        out.write(struct.pack(">i", value))
    elif tag == STRING:
        _string(out, value)
    elif tag == LIST:
        element, items = value
        out.write(struct.pack(">bi", element if items else END, len(items)))
        for item in items:
            _payload(out, element, item)
    elif tag == COMPOUND:
        for name, (child, child_value) in value.items():
            out.write(struct.pack(">b", child))
            _string(out, name)
            _payload(out, child, child_value)
        out.write(struct.pack(">b", END))
    else:
        raise ValueError(tag)


def parse_state(state):
    if "[" not in state:
        return state, ()
    name, props = state[:-1].split("[")
    return name, tuple(sorted(tuple(p.split("=")) for p in props.split(",")))


def art(tileset, role, doors, variant, markers):
    """The voxels of one template from its tileset's art module; fills `markers`."""
    if tileset == "osarios":
        return tiles.template(tileset, role, doors, variant, markers=markers)
    return kit.build(importlib.import_module(tileset), role, doors, variant, markers)


def build(tileset, role, doors, variant):
    """(raw NBT bytes, marker counts, clipped voxel count) of one template."""
    markers = []
    voxels = art(tileset, role, doors, variant, markers)
    marker_at = {}
    for (x, y, z, metadata) in markers:
        if not (0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2]):
            raise ValueError("marker %s outside the template at %s" % (metadata, (x, y, z)))
        if (x, y, z) in marker_at:
            raise ValueError("two markers at %s" % ((x, y, z),))
        marker_at[(x, y, z)] = metadata
    clipped = sum(1 for (x, y, z) in voxels
                  if not (0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2]))
    palette, blocks = [], []
    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                if (x, y, z) in marker_at:
                    key = ("minecraft:structure_block", (("mode", "data"),))
                else:
                    state = voxels.get((x, y, z))
                    if state is None or (state == "minecraft:air" and 1 <= y <= ROOM_TOP):
                        continue
                    key = parse_state(state)
                if key not in palette:
                    palette.append(key)
                entry = {"pos": (LIST, (INT, [x, y, z])), "state": (INT, palette.index(key))}
                if (x, y, z) in marker_at:
                    entry["nbt"] = (COMPOUND, {"id": (STRING, "minecraft:structure_block"), "mode": (STRING, "DATA"),
                                               "metadata": (STRING, marker_at[(x, y, z)])})
                blocks.append(entry)
    palette_tags = []
    for name, properties in palette:
        tag = {"Name": (STRING, name)}
        if properties:
            tag["Properties"] = (COMPOUND, {k: (STRING, v) for k, v in properties})
        palette_tags.append(tag)
    root = {
        "DataVersion": (INT, DATA_VERSION),
        "size": (LIST, (INT, list(SIZE))),
        "palette": (LIST, (COMPOUND, palette_tags)),
        "blocks": (LIST, (COMPOUND, blocks)),
        "entities": (LIST, (COMPOUND, [])),
    }
    raw = io.BytesIO()
    raw.write(struct.pack(">b", COMPOUND))
    _string(raw, "")
    _payload(raw, COMPOUND, root)
    counts = {}
    for metadata in marker_at.values():
        kind = ":".join(metadata.split(":")[:2])
        counts[kind] = counts.get(kind, 0) + 1
    return raw.getvalue(), dict(sorted(counts.items())), clipped


def pack(raw):
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="") as stream:
        stream.write(raw)
    return packed.getvalue()


def index_json(tileset, entries):
    body = {"tileset": tileset, "size": list(SIZE), "generator": "tools/export_enves_tiles.py",
            "templates": {name: ({"markers": markers, "clipped": clipped} if clipped else {"markers": markers})
                          for name, markers, clipped in entries}}
    return json.dumps(body, indent=1, ensure_ascii=False) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--tileset", choices=TILESETS, help="only this tileset")
    args = parser.parse_args()
    stale = []
    for tileset in ([args.tileset] if args.tileset else TILESETS):
        folder = RESOURCES / "structure/enves" / tileset
        entries, names = [], set()
        for name, role, doors, variant in templates():
            raw, markers, clipped = build(tileset, role, doors, variant)
            entries.append((name, markers, clipped))
            names.add(name + ".nbt")
            target = folder / (name + ".nbt")
            if args.check:
                try:
                    current = gzip.decompress(target.read_bytes()) if target.is_file() else None
                except OSError:
                    current = None
                if current != raw:
                    stale.append(target)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(pack(raw))
        index = RESOURCES / "enves/templates" / (tileset + ".json")
        text = index_json(tileset, entries)
        if args.check:
            if not index.is_file() or index.read_text(encoding="utf-8") != text:
                stale.append(index)
            extra = sorted(p.name for p in folder.glob("*.nbt") if p.name not in names) if folder.is_dir() else []
            stale.extend(folder / n for n in extra)
        else:
            index.parent.mkdir(parents=True, exist_ok=True)
            index.write_text(text, encoding="utf-8", newline="\n")
            for extra in (p for p in folder.glob("*.nbt") if p.name not in names):
                extra.unlink()
            print("wrote %d %s templates and %s" % (len(entries), tileset, index.relative_to(ROOT)))
    if args.check:
        if stale:
            for path in stale[:20]:
                print("stale: %s" % path.relative_to(ROOT), file=sys.stderr)
            print("%d Envés template file(s) missing or stale; run without --check" % len(stale), file=sys.stderr)
            return 1
        print("Envés templates match their art")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
