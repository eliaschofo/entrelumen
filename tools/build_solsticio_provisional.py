"""Write the PROVISIONAL Solsticio city template: a symmetric quartz-and-copper plaza.

It exists so the placement, markers, plots, protection and portal can be tested before the
controller delivers the definitive city. It carries the ``provisional`` data marker, which the
game logs as a warning; the definitive templates must not. Any ``city.nbt`` or ``piece_x_z.nbt``
set saved under ``companion/src/main/resources/data/entrelumen/structure/solsticio/`` replaces it
without code changes (delete this file when doing so). Layer 0 is the underside of the floating
plaza; layer 4 is the floor; markers stand on layer 5. Air is omitted: the plaza floats in void.

Layout (61 x 12 x 61, centre 30,30), symmetric under 90-degree rotation except the markers:
  * town hall ("Ayuntamiento") 9 x 9 at the centre, four doorways, glass roof;
  * four 16 x 16 player plots (grass), one per quadrant, framed by an oxidised-copper border;
  * two crossing copper avenues and four open pavilions on them.

    python tools/build_solsticio_provisional.py          # write
    python tools/build_solsticio_provisional.py --check  # verify the committed file
"""
from __future__ import annotations

import argparse
import gzip
import io
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "companion/src/main/resources/data/entrelumen/structure/solsticio/piece_0_0.nbt"
DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE = (61, 12, 61)
C = 30  # centre
FLOOR = 4
PLOTS = [(5, 5), (40, 5), (5, 40), (40, 40)]  # north-west corners, 16 x 16 each
PAVILIONS = [(C, 15), (C, 45), (15, C), (45, C)]
HALL = (26, 34)  # inclusive x/z range of the 9 x 9 town hall

MARKERS = {
    (C, FLOOR + 1, C): "town_hall_portal",
    (C, FLOOR + 1, 27): "town_hall_waystone",
    (C, FLOOR + 1, 58): "arrival",
    (C, FLOOR + 1, 15): "trading_hall",
    (C, FLOOR + 1, 32): "mayor",
    (45, FLOOR + 1, C): "inventor",
    (15, FLOOR + 1, C): "gardener",
    (C, FLOOR + 1, 45): "priest",
    (0, FLOOR + 1, 0): "provisional",
}
for px, pz in PLOTS:
    MARKERS[(px, FLOOR + 1, pz)] = "player_plot"

LANTERN = ("minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
ROOF_SLAB = ("minecraft:waxed_cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
PILLAR = ("minecraft:quartz_pillar", {"axis": "y"})


def in_plot(x: int, z: int) -> bool:
    return any(px <= x < px + 16 and pz <= z < pz + 16 for px, pz in PLOTS)


def on_plot_border(x: int, z: int) -> bool:
    return any((px - 1 <= x <= px + 16 and pz - 1 <= z <= pz + 16) and not (px <= x < px + 16 and pz <= z < pz + 16)
               for px, pz in PLOTS)


def on_avenue(x: int, z: int) -> bool:
    return abs(x - C) <= 1 or abs(z - C) <= 1


def in_hall(x: int, z: int) -> bool:
    return HALL[0] <= x <= HALL[1] and HALL[0] <= z <= HALL[1]


def hall_block(x: int, y: int, z: int):
    edge_x, edge_z = x in HALL, z in HALL
    wall = edge_x or edge_z
    corner = edge_x and edge_z
    doorway = ((edge_z and abs(x - C) <= 1) or (edge_x and abs(z - C) <= 1)) and y <= FLOOR + 3
    if y <= FLOOR + 5:
        if corner:
            return PILLAR
        if wall and not doorway:
            if y == FLOOR + 3 and (abs(x - C) == 3 or abs(z - C) == 3):
                return ("minecraft:glass", {})
            return ("minecraft:quartz_bricks", {})
        if not wall and y == FLOOR + 5 and (x, z) == (C, C):
            return LANTERN
        return None
    if y == FLOOR + 6:
        return ("minecraft:waxed_cut_copper", {}) if wall else ("minecraft:white_stained_glass", {})
    if y == FLOOR + 7 and abs(x - C) <= 1 and abs(z - C) <= 1:
        return ("minecraft:white_stained_glass", {})
    return None


def pavilion_block(x: int, y: int, z: int):
    for vx, vz in PAVILIONS:
        dx, dz = x - vx, z - vz
        if abs(dx) > 2 or abs(dz) > 2:
            continue
        if FLOOR + 1 <= y <= FLOOR + 4 and abs(dx) == 2 and abs(dz) == 2:
            return PILLAR
        if y == FLOOR + 5:
            return ROOF_SLAB
        if y == FLOOR + 4 and dx == 0 and dz == 0:
            return LANTERN
    return None


def block_at(x: int, y: int, z: int):
    """None means air (omitted)."""
    if (x, y, z) in MARKERS:
        return ("minecraft:structure_block", {"mode": "data"})
    if y == 0:
        return ("minecraft:smooth_quartz", {})
    if y < FLOOR:
        return ("minecraft:dirt", {}) if in_plot(x, z) else ("minecraft:calcite", {})
    if y == FLOOR:
        if in_plot(x, z):
            return ("minecraft:grass_block", {"snowy": "false"})
        if in_hall(x, z):
            return ("minecraft:chiseled_quartz_block", {}) if (x, z) == (C, C) else ("minecraft:smooth_quartz", {})
        if on_plot_border(x, z):
            return ("minecraft:waxed_oxidized_cut_copper", {})
        if on_avenue(x, z):
            return ("minecraft:waxed_cut_copper", {})
        return ("minecraft:smooth_quartz", {})
    if in_hall(x, z):
        return hall_block(x, y, z)
    return pavilion_block(x, y, z)


# --- minimal NBT writer -------------------------------------------------------------------
END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


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
                block = block_at(x, y, z)
                if block is None:
                    continue
                name, properties = block
                key = (name, tuple(sorted(properties.items())))
                if key not in palette:
                    palette.append(key)
                entry = {"pos": (LIST, (INT, [x, y, z])), "state": (INT, palette.index(key))}
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
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="") as stream:
        stream.write(raw.getvalue())
    return packed.getvalue()


def check_symmetry() -> None:
    """Every non-marker block matches its 90-degree rotation about the centre."""
    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                if (x, y, z) in MARKERS:
                    continue
                a = block_at(x, y, z)
                rx, rz = 2 * C - z, x  # rotate 90 degrees
                if (rx, y, rz) in MARKERS:
                    continue
                b = block_at(rx, y, rz)
                if (a is None) != (b is None) or (a and b and a[0] != b[0]):
                    raise SystemExit(f"asymmetric at {(x, y, z)}: {a} vs {(rx, y, rz)}: {b}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    check_symmetry()
    data = build()
    if args.check:
        if not TARGET.is_file() or TARGET.read_bytes() != data:
            print(f"{TARGET} is missing or stale; run without --check", file=sys.stderr)
            return 1
        print("solsticio/piece_0_0.nbt matches the provisional generator")
        return 0
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f"wrote {TARGET.relative_to(ROOT).as_posix()} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
