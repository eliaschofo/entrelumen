"""Write the PROVISIONAL Heliodor start ruin template (a small symmetric tuff patio).

The controller designs the definitive ruin; any structure NBT saved at the same path replaces this
one without code changes. Layer 0 of the template is its floor. Output is deterministic.

    python tools/build_heliodor_ruin_start.py          # write
    python tools/build_heliodor_ruin_start.py --check  # verify the committed file
"""
from __future__ import annotations

import argparse
import gzip
import io
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "companion/src/main/resources/data/entrelumen/structure/heliodor_ruin_start.nbt"
DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE = (7, 3, 7)

WALL_POST = ("minecraft:tuff_brick_wall", {
    "east": "none", "north": "none", "south": "none", "west": "none", "up": "true", "waterlogged": "false"})


def block_at(x: int, y: int, z: int):
    """Symmetric under 90-degree rotation and both mirrors around the pedestal at (3, 1, 3)."""
    corner = x in (0, 6) and z in (0, 6)
    if y == 0:
        if x in (0, 6) or z in (0, 6):
            return ("minecraft:polished_tuff", {})
        if (x, z) == (3, 3):
            return ("minecraft:chiseled_tuff", {})
        if (x == 3 and z in (2, 4)) or (z == 3 and x in (2, 4)):
            # Dead copper veins leading to the pedestal.
            return ("minecraft:waxed_oxidized_cut_copper", {})
        return ("minecraft:tuff_bricks", {})
    if y == 1:
        if corner:
            return ("minecraft:chiseled_tuff_bricks", {})
        if (x, z) == (3, 3):
            return ("entrelumen:heliodor_pedestal", {})
        return ("minecraft:air", {})
    if corner:
        return WALL_POST
    return ("minecraft:air", {})


# --- minimal NBT writer -------------------------------------------------------------------
END, BYTE, INT, STRING, LIST, COMPOUND = 0, 1, 3, 8, 9, 10


def _string(out: io.BytesIO, value: str) -> None:
    data = value.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _payload(out: io.BytesIO, tag: int, value) -> None:
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


def build() -> bytes:
    palette: list[tuple[str, tuple]] = []
    blocks = []
    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                name, properties = block_at(x, y, z)
                key = (name, tuple(sorted(properties.items())))
                if key not in palette:
                    palette.append(key)
                blocks.append({
                    "pos": (LIST, (INT, [x, y, z])),
                    "state": (INT, palette.index(key)),
                })
    palette_tags = []
    for name, properties in palette:
        entry = {"Name": (STRING, name)}
        if properties:
            entry["Properties"] = (COMPOUND, {k: (STRING, v) for k, v in properties})
        palette_tags.append(entry)
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
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="") as stream:
        stream.write(raw.getvalue())
    return packed.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = build()
    if args.check:
        if not TARGET.is_file() or TARGET.read_bytes() != data:
            print(f"{TARGET} is missing or stale; run without --check", file=sys.stderr)
            return 1
        print("heliodor_ruin_start.nbt matches the provisional generator")
        return 0
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f"wrote {TARGET.relative_to(ROOT)} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
