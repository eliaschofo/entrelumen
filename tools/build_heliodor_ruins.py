"""Write the Plan v2 Heliodor ruin templates from the art in art/structures/.

Each art module's build() returns (Voxels, markers dict) in centred coordinates. This tool maps the
markers onto the companion's data-marker convention (DATA structure blocks, see
docs/design/heliodor-ruins.md and RuinMarkers.java) and writes one template per ruin under
companion/src/main/resources/data/entrelumen/structure/ruins/. Only the cells the art defines are
written (explicit air included); placement clears the rest of the ruin's columns above the ground.

Blocks the art puts at a marker's cell travel in the marker as block=<state>, so campfires, copper
bulbs, levers, lecterns and barrels stay what they are. Every barrel becomes a Lootr barrel; every
lever joins its ruin's lever lock. Output is deterministic.

    python tools/build_heliodor_ruins.py            # write every template
    python tools/build_heliodor_ruins.py --check    # verify the committed files
    python tools/build_heliodor_ruins.py --report   # sizes, markers and palettes
"""
from __future__ import annotations

import argparse
import gzip
import importlib
import io
import json
import math
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "art/structures"))

OUT = ROOT / "companion/src/main/resources/data/entrelumen/structure/ruins"
DATA_VERSION = 3955  # Minecraft 1.21.1
WORKSHOP_ARM = "entrelumen:chests/ruin_act2_workshop"
# Vanilla block ids are checked against this client resources jar when it is present.
VANILLA = Path("E:/Elias/Codex/Entrelumen-ssd/companion-build/moddev/artifacts/"
               "neoforge-21.1.249-client-extra-aka-minecraft-resources.jar")

# ruin id -> (art module, build function)
RUINS = {
    "signal_tower": ("ruin_signal_tower", "build"),
    "sunken_workshop": ("ruin_sunken_workshop", "build"),
    "viaduct": ("ruin_viaduct", "build"),
    "dome_greenhouse": ("ruins_medium", "greenhouse"),
    "nether_foundry": ("ruins_medium", "foundry"),
    "cliff_observatory": ("ruin_cliff_observatory", "build"),
    "twilight_sanctuary": ("ruins_medium", "sanctuary"),
    "sun_antechamber": ("ruins_medium", "antechamber"),
    "light_temple": ("ruin_temple", "build"),
    "void_observatory": ("ruins_medium", "void_observatory"),
}

# Each ruin's challenge ids, matching data/entrelumen/heliodor_ruin/<id>.json.
CHALLENGES = {
    "signal_tower": {"braziers": "braziers", "lantern": "braziers"},
    "sunken_workshop": {"levers": "sluices", "drain": "sluices", "boss": "drowned"},
    "viaduct": {"boss": "toll_guardian"},
    "dome_greenhouse": {"sockets": "saplings"},
    "nether_foundry": {"boss": "guards", "levers": "furnaces"},
    "cliff_observatory": {"mirrors": "mirrors"},
    "twilight_sanctuary": {"stones": "stones"},
    "sun_antechamber": {"sockets": "offerings"},
    "light_temple": {"sockets": "offerings", "braziers": "lamps", "boss": "keeper"},
    "void_observatory": {"boss": "watcher"},
}
SOCKET_LOOK = {"dome_greenhouse": "pot", "sun_antechamber": "altar", "light_temple": "altar"}
LORE = {"signal_tower": (5, 5), "cliff_observatory": (5, 5), "viaduct": (8, 5)}
DIRECTIONS = ["n", "ne", "e", "se", "s", "sw", "w", "nw"]


def direction(dx: int, dz: int) -> int:
    return round(math.degrees(math.atan2(dx, -dz)) / 45.0) % 8


# The gate each ruin's vault or cellar keeps, and how it looks. Seal: a pale light over the shaft;
# floor: a false floor the team falls through and climbs.
GATE_STYLE = {
    "sunken_workshop": ("vault", "seal"),
    "dome_greenhouse": ("crypt", "seal"),
    "nether_foundry": ("vault", "seal"),
    "twilight_sanctuary": ("cellar", "floor"),
}


def gates(name: str, v: dict, mk: dict) -> list[tuple[tuple[int, int, int], str]]:
    """Per-team gate cells, found from the art: over each marked vault door, else over each ladder
    shaft that leads down from the ground layer."""
    if name not in GATE_STYLE:
        return []
    gate, style = GATE_STYLE[name]
    look = "look=moss_block climb=true" if style == "floor" else "look=seal"
    tops = [tuple(p) for p in mk.get("vault_doors", [])]
    if not tops:
        tops = [p for p, b in sorted(v.items()) if p[1] == 0 and b.startswith("minecraft:ladder")
                and v.get((p[0], -1, p[2]), "").startswith("minecraft:ladder")]
    if not tops:
        raise SystemExit(f"{name}: no vault door or ladder shaft to put its {gate} gate on")
    out = []
    for x, y, z in tops:
        cell = (x, y, z) if style == "floor" else (x, y + 1, z)
        out.append((cell, f"gate id={gate} {look}"))
    return out


def markers(name: str, v: dict, mk: dict) -> dict[tuple[int, int, int], str]:
    """Centred cell -> marker metadata (without block=, added from the art cell later)."""
    c = CHALLENGES[name]
    out: dict[tuple[int, int, int], str] = {}

    def put(p, meta):
        p = tuple(p)
        if p in out:
            raise SystemExit(f"{name}: two markers at {p}: {out[p]} / {meta}")
        out[p] = meta

    for p in mk.get("pedestal", []):
        put(p, "pedestal")
    braziers = mk.get("braziers", [])
    if isinstance(braziers, dict):
        for order, cells in sorted(braziers.items(), key=lambda kv: int(kv[0])):
            for p in cells:
                put(p, f"brazier challenge={c['braziers']} order={int(order)}")
    else:
        for p in braziers:
            put(p, f"brazier challenge={c['braziers']}")
    for p in mk.get("lantern", []):
        put(p, f"lamp challenge={c['lantern']}")
    arm = [tuple(p) for p in mk.get("arm_chest", [])]
    for p, block in sorted(v.items()):
        if block.startswith("minecraft:barrel"):
            put(p, f"chest loot={WORKSHOP_ARM}" if arm and p == arm[0] else "chest")
        elif block.startswith("minecraft:lever"):
            put(p, f"lever challenge={c['levers']}")
    if mk.get("drain_volume"):
        lo, hi = mk["drain_volume"]
        size = ",".join(str(hi[i] - lo[i] + 1) for i in range(3))
        put(lo, f"drain challenge={c['drain']} size={size}")
    for p in mk.get("boss", []) + mk.get("drowned", []):
        put(p, f"boss challenge={c['boss']}")
    receptors = mk.get("beam_receptor", [])
    for p in receptors:
        put(p, f"receptor challenge={c['mirrors']}")
    for p in mk.get("mirrors", []):
        aim = direction(receptors[0][0] - p[0], receptors[0][2] - p[2])
        put(p, f"mirror challenge={c['mirrors']} facing={DIRECTIONS[(aim + 4) % 8]}")
    for p in mk.get("offering_sockets", []):
        put(p, f"socket challenge={c['sockets']} look={SOCKET_LOOK[name]}")
    # Standing stones are read like the compass: from the north, clockwise.
    stones = sorted(mk.get("order_stones", []), key=lambda p: math.atan2(p[0], -p[2]) % (2 * math.pi))
    for order, p in enumerate(stones, 1):
        put(p, f"brazier challenge={c['stones']} order={order}")
    radius, height = LORE.get(name, (6, 5))
    for p in mk.get("lore", []):
        put(p, f"lore radius={radius} height={height}")
    xs = [p[0] for p in v]
    zs = [p[2] for p in v]
    for p in mk.get("arrival", []):
        if min(xs) <= p[0] <= max(xs) and min(zs) <= p[2] <= max(zs):
            put(p, "arrival")
    for p, meta in gates(name, v, mk):
        put(p, meta)
    ground = next(((x, 0, z) for (x, z) in [(0, 0)] + [(x, z) for (x, y, z) in sorted(v) if y == 0]
                   if (x, 0, z) in v and (x, 0, z) not in out), None)
    if ground is None:
        raise SystemExit(f"{name}: no free cell on the ground layer for the ground marker")
    put(ground, "ground")
    return out


OWN_BLOCK = ("pedestal", "mirror", "socket", "hidden", "gate", "lock")


def template(name: str):
    module, fn = RUINS[name]
    v, mk = getattr(importlib.import_module(module), fn)()
    v = dict(v)
    marks = markers(name, v, mk)
    cells = set(v) | set(marks)
    x0 = min(p[0] for p in cells)
    y0 = min(p[1] for p in cells)
    z0 = min(p[2] for p in cells)
    size = (max(p[0] for p in cells) - x0 + 1, max(p[1] for p in cells) - y0 + 1, max(p[2] for p in cells) - z0 + 1)
    entries = []
    for p in sorted(cells, key=lambda q: (q[1], q[2], q[0])):
        local = (p[0] - x0, p[1] - y0, p[2] - z0)
        if p in marks:
            meta = marks[p]
            block = v.get(p)
            if block and not block.endswith(":air") and meta.split()[0] not in OWN_BLOCK:
                meta += " block=" + block
            entries.append((local, ("minecraft:structure_block", {"mode": "data"}), meta))
        else:
            entries.append((local, split(v[p]), None))
    return size, entries, marks


def split(state: str):
    if "[" not in state:
        return state, {}
    block, props = state[:-1].split("[")
    return block, dict(p.split("=") for p in props.split(","))


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
        for key, (child, child_value) in value.items():
            out.write(struct.pack(">b", child))
            _string(out, key)
            _payload(out, child, child_value)
        out.write(struct.pack(">b", END))
    else:
        raise ValueError(tag)


def build_raw(name: str) -> bytes:
    size, entries, _ = template(name)
    palette: list[tuple[str, tuple]] = []
    index: dict[tuple, int] = {}
    blocks = []
    for local, (block, props), meta in entries:
        key = (block, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append(key)
        entry = {"pos": (LIST, (INT, list(local))), "state": (INT, index[key])}
        if meta is not None:
            entry["nbt"] = (COMPOUND, {
                "id": (STRING, "minecraft:structure_block"),
                "mode": (STRING, "DATA"),
                "metadata": (STRING, meta),
                "name": (STRING, ""),
                "author": (STRING, "entrelumen"),
            })
        blocks.append(entry)
    palette_tags = []
    for block, props in palette:
        tag = {"Name": (STRING, block)}
        if props:
            tag["Properties"] = (COMPOUND, {k: (STRING, v) for k, v in props})
        palette_tags.append(tag)
    root = {
        "DataVersion": (INT, DATA_VERSION),
        "size": (LIST, (INT, list(size))),
        "palette": (LIST, (COMPOUND, palette_tags)),
        "blocks": (LIST, (COMPOUND, blocks)),
        "entities": (LIST, (COMPOUND, [])),
    }
    raw = io.BytesIO()
    raw.write(struct.pack(">b", COMPOUND))
    _string(raw, "")
    _payload(raw, COMPOUND, root)
    return raw.getvalue()


def build(name: str) -> bytes:
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="") as stream:
        stream.write(build_raw(name))
    return packed.getvalue()


def unknown_blocks() -> dict[str, list[str]]:
    """Block ids of the art that the 1.21.1 client resources do not know (empty without the jar)."""
    if not VANILLA.is_file():
        return {}
    with zipfile.ZipFile(VANILLA) as jar:
        known = {Path(n).stem for n in jar.namelist() if n.startswith("assets/minecraft/blockstates/")}
    known |= {"air", "water", "lava", "structure_block"}
    found = {}
    for name in RUINS:
        _, entries, _ = template(name)
        bad = sorted({b for _, (b, _), _ in entries if b.startswith("minecraft:") and b.split(":")[1] not in known})
        if bad:
            found[name] = bad
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--report", action="store_true")
    args = parser.parse_args()
    bad = unknown_blocks()
    if bad:
        print("unknown block ids:", json.dumps(bad), file=sys.stderr)
        return 1
    failed = 0
    for name in RUINS:
        target = OUT / f"{name}.nbt"
        if args.report:
            size, entries, marks = template(name)
            kinds: dict[str, int] = {}
            for meta in marks.values():
                kinds[meta.split()[0]] = kinds.get(meta.split()[0], 0) + 1
            print(f"{name}: size {size[0]}x{size[1]}x{size[2]}, {len(entries)} entries, markers {kinds}")
        elif args.check:
            try:
                current = gzip.decompress(target.read_bytes()) if target.is_file() else None
            except OSError:
                current = None
            if current != build_raw(name):
                print(f"{target.relative_to(ROOT)} is missing or stale; run without --check", file=sys.stderr)
                failed += 1
        else:
            data = build(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            print(f"wrote {target.relative_to(ROOT)} ({len(data)} bytes)")
    if args.check and not failed:
        print(f"{len(RUINS)} ruin templates match their art")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
