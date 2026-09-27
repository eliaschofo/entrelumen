"""Write the Heliodor start ruin template from its design in art/structures/ruin_start.py.

The design (a round sun patio with the compass pedestal, D4-symmetric) and the Sealed Stair under
it live with the other structure designs; this tool only serialises them. The patio floor is layer
GROUND of the template, centred on the sun's heart; the stair and its antechamber fill the layers
below. Floor cells outside the round patio, and the underground the stair does not use, are left
out so the terrain stays; every other empty cell above the floor is air so grass and bushes inside
the box are cleared. DATA markers: `entrelumen:ground` stands on the floor layer (HeliodorRuins
sinks the template by its height), `entrelumen:enves_gate` and `entrelumen:enves_antechamber` are
the gate and the arrival point of the antechamber (docs/design/dungeon-enves.md). Output is
deterministic.

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
sys.path.insert(0, str(ROOT / "art/structures"))
import ruin_start  # noqa: E402

TARGET = ROOT / "companion/src/main/resources/data/entrelumen/structure/heliodor_ruin_start.nbt"
DATA_VERSION = 3955  # Minecraft 1.21.1
DESIGN = ruin_start.design()
STAIR, STAIR_MARKERS = ruin_start.sealed_stair()
GROUND = -min(y for (_, y, _) in STAIR)                       # the patio floor's layer in the template
HALF_X = ruin_start.HALF
HALF_Z = max(ruin_start.HALF, *(abs(z) for (_, _, z) in STAIR))  # centred on the heart, stair included
SIZE = (2 * HALF_X + 1, GROUND + ruin_start.HEIGHT, 2 * HALF_Z + 1)


def _design_pos(x: int, y: int, z: int):
    return (x - HALF_X, y - GROUND, z - HALF_Z)


MARKERS = {
    (0, GROUND + 1, 0): "entrelumen:ground",                  # a corner above the floor: air anyway
}
for name, metadata in (("enves_gate", "entrelumen:enves_gate"), ("antechamber_arrival", "entrelumen:enves_antechamber")):
    for (mx, my, mz) in STAIR_MARKERS[name]:
        MARKERS[(mx + HALF_X, my + GROUND, mz + HALF_Z)] = metadata


def block_at(x: int, y: int, z: int):
    if (x, y, z) in MARKERS:
        return ("minecraft:structure_block", {"mode": "data"})
    key = _design_pos(x, y, z)
    if key[1] < 0:
        state = STAIR.get(key)                                # below the floor only the stair is written
    elif key in DESIGN:
        state = DESIGN[key]
    else:
        state = None if key[1] == 0 else "minecraft:air"      # outside the round floor the ground stays
    if state is None:
        return None
    if "[" not in state:
        return (state, {})
    name, props = state[:-1].split("[")
    return (name, dict(p.split("=") for p in props.split(",")))


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


def build_raw() -> bytes:
    """The uncompressed NBT; --check compares this, since gzip bytes differ between zlib builds."""
    palette: list[tuple[str, tuple]] = []
    blocks = []
    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                found = block_at(x, y, z)
                if found is None:
                    continue
                name, properties = found
                key = (name, tuple(sorted(properties.items())))
                if key not in palette:
                    palette.append(key)
                entry = {
                    "pos": (LIST, (INT, [x, y, z])),
                    "state": (INT, palette.index(key)),
                }
                if (x, y, z) in MARKERS:
                    entry["nbt"] = (COMPOUND, {
                        "id": (STRING, "minecraft:structure_block"),
                        "mode": (STRING, "DATA"),
                        "metadata": (STRING, MARKERS[(x, y, z)]),
                    })
                blocks.append(entry)
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
    return raw.getvalue()


def build() -> bytes:
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="") as stream:
        stream.write(build_raw())
    return packed.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        # Windows Python 3.14 compresses with zlib-ng, CI with the system zlib: compare the NBT itself.
        try:
            current = gzip.decompress(TARGET.read_bytes()) if TARGET.is_file() else None
        except OSError:
            current = None
        if current != build_raw():
            print(f"{TARGET} is missing or stale; run without --check", file=sys.stderr)
            return 1
        print("heliodor_ruin_start.nbt matches its design")
        return 0
    data = build()
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f"wrote {TARGET.relative_to(ROOT)} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
