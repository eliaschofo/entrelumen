"""Write the Heliodor start ruin template from its design in art/structures/ruin_start.py.

The design (a round sun patio with the compass pedestal, D4-symmetric) lives with the other
structure designs; this tool only serialises it. Layer 0 of the template is its floor; floor cells
outside the round patio are left out so the ground stays, and every other empty cell is air so
grass and bushes inside the box are cleared. Output is deterministic.

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
SIZE = (2 * ruin_start.HALF + 1, ruin_start.HEIGHT, 2 * ruin_start.HALF + 1)
DESIGN = ruin_start.design()


def block_at(x: int, y: int, z: int):
    state = ruin_start.template_block(DESIGN, x, y, z)
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


def build() -> bytes:
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
        print("heliodor_ruin_start.nbt matches its design")
        return 0
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f"wrote {TARGET.relative_to(ROOT)} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
