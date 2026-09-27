"""Write the Plan v2 Heliodor ruin templates from the art in art/structures/.

Each art module's build() returns (Voxels, markers dict) in centred coordinates. This tool maps the
markers onto the companion's data-marker convention (DATA structure blocks, see
docs/design/heliodor-ruins.md and RuinMarkers.java) and writes one template per ruin under
companion/src/main/resources/data/entrelumen/structure/ruins/. Only the cells the art defines are
written (explicit air included). Placement shapes the terrain around them (RuinTerrain.java): the land
blends to the ruin's ground layer, thin supports go down to the ground, and the art's soil (grass, dirt,
coarse dirt, podzol, moss) becomes the site's own ground except inside the art's keep_soil boxes.

Blocks the art puts at a marker's cell travel in the marker as block=<state>, so campfires, copper
bulbs, levers, lecterns and barrels stay what they are. Every barrel becomes a Lootr barrel; every
lever joins its ruin's lever lock. Output is deterministic.

The Sunken Workshop also gets its Create engine ("El Motor de Terra"): the transmission from each
wheel to its overhead line, the pumps with their pipes, the seal ring on a bearing, and the markers
the runtime reads. The layout is derived from the art's markers and checked with a model of Create's
rotation propagation (tools/create_kinetics.py), together with the scripted builds the full-pack
GameTests replay, which this tool writes next to those tests.

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
sys.path.insert(0, str(ROOT / "tools"))

import create_kinetics as kinetics  # noqa: E402

OUT = ROOT / "companion/src/main/resources/data/entrelumen/structure/ruins"
SOLUTIONS = ROOT / "companion/src/fullpackGameTest/resources/data/entrelumen/ruin_solution"
DATA_VERSION = 3955  # Minecraft 1.21.1
WORKSHOP_ARM = "entrelumen:chests/ruin_act2_workshop"
WHEELHOUSE_LOOT = "entrelumen:chests/ruin_act2_wheelhouse"
# Vanilla block ids are checked against this client resources jar when it is present, Create's
# block ids and property names against the pack's pinned Create jar (read only).
VANILLA = Path("E:/Elias/Codex/Entrelumen-ssd/companion-build/moddev/artifacts/"
               "neoforge-21.1.249-client-extra-aka-minecraft-resources.jar")
CREATE = Path("G:/curseforge/Instances/ENTRELUMEN/mods/create-1.21.1-6.0.10.jar")
# Create 6.0.10 blocks with a waterlogged property (AbstractShaftBlock, CogWheelBlock, PumpBlock,
# FluidPipeBlock); gauges, water wheels, gearboxes, bearings and chassis have none.
WATERLOGGABLE_CREATE = {"create:shaft", "create:cogwheel", "create:large_cogwheel", "create:mechanical_pump",
                        "create:fluid_pipe"}


def sane(state: str) -> str:
    """Drops a waterlogged property the block does not have (the art writes it on every Create block)."""
    block, props = split(state)
    if not block.startswith("create:") or block in WATERLOGGABLE_CREATE or "waterlogged" not in props:
        return state
    props = {k: v for k, v in props.items() if k != "waterlogged"}
    return block + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")

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
    "void_observatory": ("ruin_void_observatory", "build"),
}

# Each ruin's challenge ids, matching data/entrelumen/heliodor_ruin/<id>.json.
CHALLENGES = {
    "signal_tower": {"relay": "relay", "lantern": "relay"},
    "sunken_workshop": {"levers": "sluices", "drain": "sluices", "boss": "drowned", "pumps": "engine",
                        "seal": "seal"},
    "viaduct": {"boss": "toll_guardian"},
    "dome_greenhouse": {"sockets": "saplings"},
    "nether_foundry": {"boss": "guards", "levers": "furnaces"},
    "cliff_observatory": {"mirrors": "mirrors"},
    "twilight_sanctuary": {"stones": "stones"},
    "sun_antechamber": {"sockets": "offerings"},
    "light_temple": {"sockets": "offerings", "braziers": "lamps", "boss": "keeper"},
    "void_observatory": {"shulker_nests": "nests", "lens_sockets": "lenses"},
}
SOCKET_LOOK = {"dome_greenhouse": "pot", "sun_antechamber": "altar", "light_temple": "altar",
               "void_observatory": "altar"}
LORE = {"signal_tower": (5, 5), "cliff_observatory": (5, 5), "viaduct": (8, 5)}
DIRECTIONS = ["n", "ne", "e", "se", "s", "sw", "w", "nw"]


def direction(dx: int, dz: int) -> int:
    return round(math.degrees(math.atan2(dx, -dz)) / 45.0) % 8


# The gate each ruin's vault or cellar keeps, and how it looks. Seal: a pale light over the shaft;
# floor: a false floor the team falls through and climbs; shaft: the top rung of the ladder shaft,
# looking like the floor around it (the Sunken Workshop, whose seal ring lies over the shafts);
# hatch: the deck cell over a ladder that starts just under the ground layer (the Void Observatory's
# chart room).
GATE_STYLE = {
    "sunken_workshop": ("vault", "shaft"),
    "dome_greenhouse": ("crypt", "seal"),
    "nether_foundry": ("vault", "seal"),
    "twilight_sanctuary": ("cellar", "floor"),
    "void_observatory": ("chart_room", "hatch"),
}


def gates(name: str, v: dict, mk: dict) -> list[tuple[tuple[int, int, int], str]]:
    """Per-team gate cells, found from the art: over each marked vault door, else over each ladder
    shaft that leads down from the ground layer."""
    if name not in GATE_STYLE:
        return []
    gate, style = GATE_STYLE[name]
    look = {"floor": "look=moss_block climb=true", "shaft": "look=tuff_bricks climb=true",
            "hatch": "look=end_stone_bricks climb=true"}.get(style, "look=seal")
    tops = [tuple(p) for p in mk.get("vault_doors", [])]
    if not tops and style == "hatch":
        tops = [(p[0], 0, p[2]) for p, b in sorted(v.items()) if p[1] == -1 and b.startswith("minecraft:ladder")
                and not v.get((p[0], 0, p[2]), "minecraft:air").endswith(":air")
                and not v.get((p[0], 0, p[2]), "").startswith("minecraft:ladder")]
    if not tops:
        tops = [p for p, b in sorted(v.items()) if p[1] == 0 and b.startswith("minecraft:ladder")
                and v.get((p[0], -1, p[2]), "").startswith("minecraft:ladder")]
    if not tops:
        raise SystemExit(f"{name}: no vault door or ladder shaft to put its {gate} gate on")
    out = []
    for x, y, z in tops:
        cell = (x, y, z) if style in ("floor", "shaft", "hatch") else (x, y + 1, z)
        out.append((cell, f"gate id={gate} {look}"))
    return out


# --- The Sunken Workshop's Create engine (docs/design/heliodor-ruins.md, "El Motor de Terra") ----
WORKSHOP = "sunken_workshop"
SANDBOX_RADIUS = 4.5                      # the doc's disc of 9 blocks across
RING = (6.3, 7.6)                         # the seal ring's radii, around the boiler's base
PLUG = "minecraft:waxed_copper_block"     # the ring, the plugs over the shafts, the sluice gates
# The transmission piece each wheelhouse lacks, houses +x, +z, -x, -z (C4 order).
MISSING = ("top", "riser", "cog", "axle")
HOUSES = ("house1", "house2", "house3", "house4")


def c4(p, k: int):
    """k quarter turns of a centred cell about the y axis: +x -> +z -> -x -> -z."""
    x, y, z = p
    for _ in range(k % 4):
        x, z = -z, x
    return (x, y, z)


def c4_state(state: str, k: int) -> str:
    from voxkit import orient

    def turn(v):
        dx, dz = v
        for _ in range(k % 4):
            dx, dz = -dz, dx
        return (dx, dz)

    return orient(state, turn)


def quadrant(x: int, z: int) -> int:
    """The C4 index of a diagonal cell: (+,+) 0, (-,+) 1, (-,-) 2, (+,-) 3."""
    return {(1, 1): 0, (-1, 1): 1, (-1, -1): 2, (1, -1): 3}[(1 if x > 0 else -1, 1 if z > 0 else -1)]


def house_of(x: int, z: int) -> int:
    """The C4 index of the wheelhouse nearest to a cell: +x 0, +z 1, -x 2, -z 3."""
    if abs(x) >= abs(z):
        return 0 if x > 0 else 2
    return 1 if z > 0 else 3


def gauge(axis: str) -> str:
    """A speedometer lying on the floor with its shaft along a horizontal axis (1.21.1 states)."""
    first = "true" if axis == "x" else "false"
    return f"create:speedometer[axis_along_first={first},facing=up,waterlogged=false]"


def shaft(axis: str) -> str:
    return f"create:shaft[axis={axis},waterlogged=false]"


def cog(axis: str) -> str:
    return f"create:cogwheel[axis={axis},waterlogged=false]"


def gearbox(axis: str) -> str:
    return f"create:gearbox[axis={axis}]"


def pipe(connections, waterlogged: bool) -> str:
    props = [f"{d}={'true' if d in connections else 'false'}"
             for d in ("down", "east", "north", "south", "up", "west")]
    props.append(f"waterlogged={'true' if waterlogged else 'false'}")
    return "create:fluid_pipe[" + ",".join(props) + "]"


def wheel_speed(wheel, axis: str, rpm: int = 4) -> int:
    """Create's large water wheel speed for water running along the channel bed under the wheel,
    towards the pit: WaterWheelBlockEntity.determineAndApplyFlowScore reduced to the bottom rim."""
    x, _, z = wheel
    flow = (-1 if x > 0 else 1, 0, 0) if axis == "z" else (0, 0, -1 if z > 0 else 1)
    return kinetics.water_wheel_speed(axis, flow, rpm)


def workshop_create(v: dict, mk: dict):
    """Adds the Create engine to the workshop voxels and returns (markers, block nbt, layout)."""
    marks: dict[tuple, str] = {}
    nbt: dict[tuple, dict] = {}
    wheel = tuple(mk["wheels"][0])
    axle, top = (tuple(p) for p in mk["wheel_to_line"])
    line_near, line_far = (tuple(p) for p in mk["drive_lines"])
    gauge_at = tuple(mk["gauges"][0])
    wheel_axis = kinetics.split(v[wheel])[1]["axis"]
    across = "x" if wheel_axis == "z" else "z"
    if top[1] != line_far[1] or abs(top[2] - line_far[2]) != 1 or axle[0] != top[0] or axle[2] != top[2]:
        raise SystemExit("sunken_workshop: wheel_to_line must rise from the axle to one block beside the line end")
    route: dict[tuple, str] = {axle: gearbox(across)}
    for y in range(axle[1] + 1, top[1]):
        route[(axle[0], y, axle[2])] = gearbox(across) if y == gauge_at[1] else shaft("y")
    step = 1 if gauge_at[2] > axle[2] else -1
    for z in range(axle[2] + step, gauge_at[2], step):
        route[(axle[0], gauge_at[1], z)] = shaft(wheel_axis)
    for z in range(axle[2] - step, -gauge_at[2], -step):          # the mirrored gauge across the pit
        route[(axle[0], gauge_at[1], z)] = shaft(wheel_axis)
    route[top] = gearbox(wheel_axis)
    for x in range(line_far[0] + 2, top[0]):
        route[(x, top[1], top[2])] = shaft("x")
    route[(line_far[0] + 1, top[1], top[2])] = cog("x")
    route[(line_far[0] + 1, line_far[1], line_far[2])] = cog("x")
    line = {(x, line_far[1], line_far[2]): shaft("x") for x in range(line_near[0], line_far[0] + 1)}
    missing = {"top": top, "riser": (axle[0], top[1] - 1, axle[2]), "cog": (line_far[0] + 1, top[1], top[2]),
               "axle": axle}
    wheels, sources, inputs, sockets = [], {}, [], []
    for k in range(4):
        for p, state in route.items():
            q = c4(p, k)
            if q in v and not v[q].endswith(":air") and not v[q].startswith("minecraft:water"):
                raise SystemExit(f"sunken_workshop: the transmission would replace {v[q]} at {q}")
            v[q] = c4_state(state, k)
        for p, state in line.items():          # the art's line, whole again where the hall door cut it
            v[c4(p, k)] = c4_state(state, k)
        socket = c4(missing[MISSING[k]], k)
        marks[socket] = f"part block={v[socket]}"
        sockets.append(socket)
        for sz in (1, -1):
            g = c4((gauge_at[0], gauge_at[1], gauge_at[2] * sz), k)
            v[g] = c4_state(gauge(wheel_axis), k)
        w = c4(wheel, k)
        axis = kinetics.split(v[w])[1]["axis"]
        marks[w] = f"wheel block={v[w]}"
        wheels.append(w)
        sources[w] = wheel_speed(w, axis)
        port = c4(line_near, k)
        marks[port] = f"port role=input block={v[port]}"
        inputs.append(port)
        for p in mk["sluice_gates"]:
            q = c4(tuple(p), k)
            marks[q] = f"sluice id={k + 1} block={PLUG}"
    # Pumps: each floor port drives a cog under it that meshes with a pump beside it; the pump pulls
    # from a pipe down into the pit and pushes into the boiler. Two diagonals must turn one way, two
    # the other (Create 6 pumps follow their facing only; the ruin enforces the turn).
    pumps, pump_ports = [], []
    canonical_port = next(tuple(p) for p in mk["pump_ports"] if p[0] > 0 and p[2] > 0)
    canonical_cog = next(tuple(p) for p in mk["pumps"] if p[0] > 0 and p[2] > 0)
    canonical_intake = next(tuple(p) for p in mk["pump_intakes"] if p[0] > 0 and p[2] > 0)
    pump_at = (canonical_cog[0], canonical_cog[1], canonical_cog[2] + 1)
    chain = {canonical_port: shaft("y")}
    for y in range(canonical_cog[1] + 1, canonical_port[1]):
        chain[(canonical_port[0], y, canonical_port[2])] = shaft("y")
    chain[canonical_cog] = cog("y")
    path = [(pump_at[0], y, pump_at[2]) for y in range(pump_at[1] - 1, canonical_intake[1] - 1, -1)]
    x, y, z = path[-1]
    while x != canonical_intake[0]:
        x += 1 if canonical_intake[0] > x else -1
        path.append((x, y, z))
    while z != canonical_intake[2]:
        z += 1 if canonical_intake[2] > z else -1
        path.append((x, y, z))
    for k in range(4):
        for p, state in chain.items():
            v[c4(p, k)] = c4_state(state, k)
        port = c4(canonical_port, k)
        marks[port] = f"port role=pump block={v[port]}"
        pump_ports.append(port)
        pump = c4(pump_at, k)
        turn = "-" if pump[0] * pump[2] > 0 else "+"
        v[pump] = "create:mechanical_pump[facing=up,waterlogged=false]"
        intake = c4(canonical_intake, k)
        offset = ",".join(str(intake[j] - pump[j]) for j in range(3))
        marks[pump] = f"pump challenge=engine turn={turn} intake={offset} block={v[pump]}"
        pumps.append(pump)
        cells = [c4(p, k) for p in path]
        for i, cell in enumerate(cells):
            before = cells[i - 1] if i else pump
            after = cells[i + 1] if i + 1 < len(cells) else None
            links = {kinetics.nearest(*(before[j] - cell[j] for j in range(3)))}
            if after is not None:
                links.add(kinetics.nearest(*(after[j] - cell[j] for j in range(3))))
            else:                                   # the open end, straight on into the pit
                links.add(kinetics.opposite(next(iter(links))))
            wet = v.get(cell, "").startswith("minecraft:water")
            v[cell] = pipe(links, wet)
    intakes = [c4(canonical_intake, k) for k in range(4)]
    # The seal: the port shaft runs down the boiler to a bearing that hangs over a radial chassis;
    # the chassis carries the base of the boiler and the copper ring that plugs the four shafts.
    seal_port = tuple(mk["seal_port"][0])
    ring_y = mk["seal_bearing"][0][1] + 1
    bearing = (seal_port[0], ring_y + 1, seal_port[2])
    chassis = (seal_port[0], ring_y, seal_port[2])
    marks[seal_port] = f"port role=seal block={shaft('y')}"
    v[seal_port] = shaft("y")
    for y in range(bearing[1] + 1, seal_port[1]):
        v[(seal_port[0], y, seal_port[2])] = shaft("y")
    v[bearing] = "create:mechanical_bearing[facing=down]"
    marks[bearing] = f"seal challenge=seal scroll=2 block={v[bearing]}"
    v[chassis] = ("create:radial_chassis[axis=y,sticky_east=true,sticky_north=true,sticky_south=true,"
                  "sticky_west=true]")
    nbt[chassis] = {"id": "create:radial_chassis", "ScrollValue": 8}
    shafts_top = {(p[0], p[2]) for p in mk["vault_doors"]}
    notches = {(sx * a, sz * b) for sx in (1, -1) for sz in (1, -1) for a, b in ((5, 5), (4, 5), (5, 4))}
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = math.hypot(x, z)
            if RING[0] < d <= RING[1] and (x, z) not in notches:
                v[(x, ring_y, z)] = PLUG
                if (x, z) in shafts_top:
                    marks[(x, ring_y, z)] = f"modblock mod=create block={PLUG}"
    for x, z in shafts_top:
        if (x, ring_y, z) not in marks:
            raise SystemExit(f"sunken_workshop: the seal ring does not cover the shaft at {x},{z}")
    # Terra's notes: the engine's on the walkway, each wheelhouse's where the art put its lectern.
    engine_note = tuple(mk["notes"][0])
    marks[engine_note] = f"note key=engine block={v[engine_note]}"
    house_note = tuple(mk["notes"][1])
    lectern = "minecraft:lectern[facing=north,has_book=false,powered=false]"
    for k in range(4):
        for sz in (1, -1):
            q = c4((house_note[0], house_note[1], house_note[2] * sz), k)
            facing = "north" if sz > 0 else "south"
            v[q] = c4_state(lectern.replace("north", facing), k)
            marks[q] = f"note key={HOUSES[k]} block={v[q]}"
    sandbox = tuple(mk["sandbox"][0])
    hi = tuple(mk["sandbox"][1])
    size = ",".join(str(hi[i] - sandbox[i] + 1) for i in range(3))
    marks[sandbox] = f"sandbox size={size} radius={SANDBOX_RADIUS}"
    returns = (4, 2, -3)
    v[returns] = "minecraft:barrel[facing=up,open=false]"
    marks[returns] = f"returns block={v[returns]}"
    center = ((sandbox[0] + hi[0]) // 2, sandbox[1], (sandbox[2] + hi[2]) // 2)
    layout = {"wheels": wheels, "sources": sources, "inputs": inputs, "sockets": sockets, "pumps": pumps,
              "pump_ports": pump_ports, "intakes": intakes, "seal_port": seal_port, "bearing": bearing,
              "sandbox": (sandbox, hi), "center": center}
    return marks, nbt, layout


# Create 6.0.10 defaults (CStress; the pack overrides none of them): SU per RPM and generated RPM.
WHEEL_CAPACITY, WHEEL_RPM, PUMP_IMPACT, BEARING_IMPACT = 128, 4, 4, 4


def pump_speed(wheels: int = 4, pumps: int = 4) -> int:
    """R: the fastest wheel speed times a power of two at which the pumps still fit in the capacity of
    every wheel merged (RuinRules.pumpSpeed)."""
    r = WHEEL_RPM
    while pumps * PUMP_IMPACT * r * 2 <= wheels * WHEEL_CAPACITY * WHEEL_RPM:
        r *= 2
    return r


def scripted_builds() -> dict[str, dict]:
    """Player builds for the engine room, in template-centred cells, replayed by the full-pack GameTests.

    correct: the four lines merged (gearboxes reconcile the mirrored wheels), five large-to-small cog
    steps (4 -> 128 RPM), and an H of gearboxes that feeds the four pump ports, two of them through an
    extra gearbox so that they turn the other way. wrong: the same H without those two gearboxes.
    overstress: one more cog step before the chain (256 RPM). seal: the merged lines turn the seal port
    through a sequenced gearshift programmed for 45 (or 90) degrees."""
    merge = {}
    for x in (1, 2, 3, 4):
        merge[(x, 4, 0)] = shaft("x")
        merge[(-x, 4, 0)] = shaft("x")
    merge[(0, 4, 0)] = gearbox("z")
    for sz in (1, -1):
        merge[(0, 4, 4 * sz)] = shaft("z")
        merge[(0, 4, 3 * sz)] = gearbox("x")
        merge[(0, 5, 3 * sz)] = shaft("y")
        merge[(0, 6, 3 * sz)] = gearbox("x")
        merge[(0, 6, 2 * sz)] = shaft("z")
        merge[(0, 6, 1 * sz)] = shaft("z")
    merge[(0, 6, 0)] = gearbox("x")
    merge[(0, 5, 0)] = gearbox("x")
    feed = {(0, 3, 0): gearbox("x"), (0, 3, -1): shaft("z"), (0, 3, -2): shaft("z"), (0, 3, -3): gearbox("y"),
            (1, 3, -3): shaft("x"), (2, 3, -3): gearbox("z"), (2, 4, -3): gearbox("x")}
    chain = {}
    steps = [((2, 4, -2), "L"), ((3, 5, -2), "S"), ((3, 5, -1), "L"), ((2, 6, -1), "S"), ((2, 6, 0), "L"),
             ((1, 5, 0), "S"), ((1, 5, 1), "L"), ((2, 4, 1), "S"), ((2, 4, 2), "L"), ((3, 5, 2), "S")]
    for p, kind in steps:
        chain[p] = "create:large_cogwheel[axis=z,waterlogged=false]" if kind == "L" else cog("z")
    chain[(3, 5, 3)] = gearbox("x")
    chain[(3, 4, 3)] = shaft("y")
    chain[(3, 3, 3)] = shaft("y")
    h = {}
    for sz in (1, -1):
        for sx in (1, -1):
            h[(3 * sx, 2, 3 * sz)] = gearbox("z")
            h[(2 * sx, 2, 3 * sz)] = shaft("x")
            h[(1 * sx, 2, 3 * sz)] = shaft("x")
        h[(0, 2, 3 * sz)] = gearbox("y")
    for z in (-2, -1, 0, 1, 2):
        h[(0, 2, z)] = shaft("z")
    correct = {**merge, **feed, **chain, **h, (-1, 2, 3): gearbox("y"), (-1, 2, -3): gearbox("y")}
    wrong = {**merge, **feed, **chain, **h}
    overstress = dict(correct)
    for p in ((0, 3, -2), (0, 3, -3), (1, 3, -3), (2, 3, -3)):
        del overstress[p]
    overstress.update({(0, 3, -1): "create:large_cogwheel[axis=z,waterlogged=false]", (1, 4, -1): cog("z"),
                       (1, 4, -2): shaft("z"), (1, 4, -3): gearbox("y"), (2, 4, -3): gearbox("y")})
    seal = {**merge, (0, 3, 0): "create:sequenced_gearshift[axis=x,state=0,vertical=true]", (0, 2, 0): shaft("y")}
    return {"correct": correct, "wrong": wrong, "overstress": overstress, "seal": seal}


def sandbox_whitelist() -> set[str]:
    tag = ROOT / "companion/src/main/resources/data/entrelumen/tags/block/ruin/sandbox.json"
    values = json.loads(tag.read_text(encoding="utf-8"))["values"] if tag.is_file() else []
    return {x["id"] if isinstance(x, dict) else x for x in values}


def workshop_check(v: dict, layout: dict) -> dict:
    """Checks the engine with the model of Create's propagation and returns the scripted builds,
    relative to the sandbox marker, for the GameTests."""
    base = {p: s for p, s in v.items() if s.startswith("create:")}
    sources = layout["sources"]
    r = kinetics.propagate(base, sources)
    if r.conflicts:
        raise SystemExit(f"sunken_workshop: the wheelhouse transmissions conflict: {r.conflicts[:3]}")
    for port in layout["inputs"]:
        if abs(r.speed.get(port, 0)) != WHEEL_RPM:
            raise SystemExit(f"sunken_workshop: no wheel reaches the line end at {port}")
    for socket in layout["sockets"]:
        cut = kinetics.propagate({p: s for p, s in base.items() if p != socket}, sources)
        reached = [port for port in layout["inputs"] if cut.speed.get(port)]
        if len(reached) != len(layout["inputs"]) - 1:
            raise SystemExit(f"sunken_workshop: the missing piece at {socket} does not cut its line")
    lo, hi = layout["sandbox"]
    cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2
    whitelist = sandbox_whitelist()
    rpm = pump_speed()
    turns = {}
    for pump in layout["pumps"]:
        turns[pump] = -1 if pump[0] * pump[2] > 0 else 1
    out = {}
    for name, build in scripted_builds().items():
        for p, state in build.items():
            if p in base or not (lo[1] <= p[1] <= hi[1]) or math.hypot(p[0] - cx, p[2] - cz) > SANDBOX_RADIUS:
                raise SystemExit(f"sunken_workshop: scripted build '{name}' leaves the sandbox at {p}")
            if whitelist and kinetics.split(state)[0] not in whitelist:
                raise SystemExit(f"sunken_workshop: scripted build '{name}' uses {state}, not in the sandbox tag")
        model = {p: (shaft("y") if "sequenced_gearshift" in s else s) for p, s in build.items()}
        result = kinetics.propagate({**base, **model}, sources)
        if result.conflicts:
            raise SystemExit(f"sunken_workshop: scripted build '{name}' breaks: {result.conflicts[:3]}")
        speeds = [result.speed.get(p, 0) for p in layout["pumps"]]
        stress = sum(PUMP_IMPACT * abs(s) for s in speeds) + BEARING_IMPACT * abs(result.speed.get(layout["bearing"], 0))
        capacity = WHEEL_CAPACITY * WHEEL_RPM * len(layout["wheels"])
        networks = {result.network.get(p) for p in layout["pumps"]}
        right = [s != 0 and (s > 0) == (turns[p] > 0) for p, s in zip(layout["pumps"], speeds)]
        if name == "correct" and not (all(abs(s) == rpm for s in speeds) and all(right) and len(networks) == 1
                                      and stress <= capacity):
            raise SystemExit(f"sunken_workshop: the correct build does not drain: {speeds}, stress {stress}")
        if name == "wrong" and (all(right) or not all(abs(s) == rpm for s in speeds)):
            raise SystemExit(f"sunken_workshop: the wrong build is not wrong: {speeds}")
        if name == "overstress" and not (all(abs(s) == 2 * rpm for s in speeds) and stress > capacity):
            raise SystemExit(f"sunken_workshop: the overstress build does not overstress: {speeds}, {stress}")
        if name == "seal" and not result.speed.get(layout["bearing"]):
            raise SystemExit("sunken_workshop: the seal build does not reach the bearing")
        out[name] = [{"pos": [p[0] - lo[0], p[1] - lo[1], p[2] - lo[2]], "state": state}
                     for p, state in sorted(build.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))]
    return {"ruin": "entrelumen:sunken_workshop", "rpm": rpm, "capacity": WHEEL_CAPACITY * WHEEL_RPM * 4,
            "stress_at_rpm": 4 * PUMP_IMPACT * rpm, "builds": out}


def solution_json(name: str) -> str | None:
    if name != WORKSHOP:
        return None
    v, _, _, _, layout = art(name)
    return json.dumps(workshop_check(v, layout), indent=1, sort_keys=True) + "\n"


# --- The Signal Tower's light relay (docs/design/heliodor-ruins.md, "el relevo de luz") -------------
TURN_WORDS = ("pass", "up", "north", "east", "south", "west")
DIR_STEP = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}
REVERSE = {"north": "south", "south": "north", "east": "west", "west": "east"}
WORD = {"n": "north", "e": "east", "s": "south", "w": "west"}
MIRROR_START = {"north": "s", "east": "w", "south": "n", "west": "e"}     # a mirror starts away from its answer


def solution(text: str) -> dict[str, str]:
    """'red: pass; mirror (0,2): north; collector: up to the lens' -> {'red': 'pass', 'mirror 0,2': 'north'}."""
    out = {}
    for part in text.split(";"):
        if ":" not in part:
            continue
        key, value = (x.strip() for x in part.split(":", 1))
        if key.startswith("mirror"):
            key = "mirror " + key[key.index("(") + 1:key.index(")")].replace(" ", "")
        out[key] = value.split()[0]
    return out


def relay_shafts(v: dict, mk: dict):
    """The white light's shaft from each collector up to its receptor stays open: the art's gallery floor,
    laid after the shaft, closes it at the gallery."""
    for r in mk.get("relay", []):
        if not r.get("collector"):
            continue
        x, y, z = r["collector"]
        rx, ry, rz = r["receptor"]
        if (rx, rz) != (x, z):
            continue
        for yy in range(y + 1, ry):
            v[(x, yy, z)] = "minecraft:air"


def relay_markers(name: str, mk: dict, c: dict):
    """The relay floors of the art as light, vitral, mirror, collector and receptor markers; each piece
    carries the state the art's solution gives it (solve=), and starts elsewhere."""
    out = []
    for r in mk.get("relay", []):
        floor = r["floor"]
        answer = solution(r["solution"])
        hand = "true" if r.get("by_hand") else "false"
        out.append((tuple(r["brazier"]), f"light challenge={c['relay']} floor={floor} hand={hand}"))
        for vitral in r["vitrals"]:
            meta = f"vitral challenge={c['relay']} floor={floor} colour={vitral['colour']} turn=pass"
            if vitral["colour"] in answer:
                if answer[vitral["colour"]] not in TURN_WORDS:
                    raise SystemExit(f"{name}: floor {floor}: unknown turn in the solution: {r['solution']}")
                meta += f" solve={answer[vitral['colour']]}"
            out.append((tuple(vitral["pos"]), meta))
        for mirror in r["mirrors"]:
            key = f"mirror {mirror[0]},{mirror[2]}"
            if key in answer:
                out.append((tuple(mirror), f"mirror challenge={c['relay']} floor={floor} "
                                           f"facing={MIRROR_START[answer[key]]} solve={answer[key][0]}"))
            else:
                out.append((tuple(mirror), f"mirror challenge={c['relay']} floor={floor} facing=n"))
        if r.get("collector"):
            out.append((tuple(r["collector"]), f"collector challenge={c['relay']} floor={floor}"))
        # A floor's receptor is a copper bulb in the ceiling (the art lays the next floor's vitral base over
        # it); the last floor's is the lens itself.
        bulb = "" if r.get("collector") else " block=minecraft:waxed_copper_bulb[lit=false,powered=false]"
        out.append((tuple(r["receptor"]),
                    f"receptor challenge={c['relay']} floor={floor} target={'+'.join(r['target'])}{bulb}"))
    return out


def relay_open(v: dict, cell) -> bool:
    block = v.get(tuple(cell))
    if block is None:
        return True
    name = block.split("[")[0].split(":")[-1]
    return name in ("air", "cave_air", "lightning_rod", "vine") or name.endswith("torch")


def relay_trace(v: dict, floor: dict, states: dict):
    """LightRelay.trace in Python: the colours reaching a floor's receptor with the given piece states
    (cell -> turn for vitrals, cell -> direction for mirrors)."""
    vitrals = {tuple(x["pos"]): x["colour"] for x in floor["vitrals"]}
    mirrors = {tuple(m) for m in floor["mirrors"]}
    collector = tuple(floor["collector"]) if floor.get("collector") else None
    receptor = tuple(floor["receptor"])
    received, collected = set(), set()
    hit = [False]

    def up(cell, colours):
        x, y, z = cell
        for _ in range(64):
            y += 1
            if (x, y, z) == receptor:
                received.update(colours)
                return
            if not relay_open(v, (x, y, z)):
                return

    seen = set()
    rays = [(tuple(floor["brazier"]), d, frozenset()) for d in ("north", "east", "south", "west")]
    while rays:
        cell, d, colours = rays.pop(0)
        colours = set(colours)
        for _ in range(32):
            cell = (cell[0] + DIR_STEP[d][0], cell[1], cell[2] + DIR_STEP[d][1])
            if cell in vitrals:
                key = (cell, d, frozenset(colours))
                if key in seen:
                    break
                seen.add(key)
                colours.add(vitrals[cell])
                turn = states.get(cell, "pass")
                if turn == "pass":
                    continue
                if turn == "up":
                    up(cell, colours)
                    break
                if turn == REVERSE[d]:
                    break
                d = turn
            elif cell in mirrors:
                facing = states.get(cell, "north")
                if facing in (d, REVERSE[d]):
                    break
                d = facing
            elif cell == collector:
                collected.update(colours)
                hit[0] = True
                break
            elif not relay_open(v, cell):
                break
    if hit[0] and collected:
        up(collector, collected)
    return received


def relay_check(name: str, v: dict, mk: dict, marks: dict):
    """Every relay floor reaches its exact colours with the solution and not with the starting states."""
    for floor in mk.get("relay", []):
        start, solved = {}, {}
        for vitral in floor["vitrals"]:
            params = dict(t.split("=", 1) for t in marks[tuple(vitral["pos"])].split()[1:] if "=" in t)
            start[tuple(vitral["pos"])] = params.get("turn", "pass")
            solved[tuple(vitral["pos"])] = params.get("solve", params.get("turn", "pass"))
        for mirror in floor["mirrors"]:
            params = dict(t.split("=", 1) for t in marks[tuple(mirror)].split()[1:] if "=" in t)
            start[tuple(mirror)] = WORD[params["facing"]]
            solved[tuple(mirror)] = WORD[params.get("solve", params["facing"])]
        target = set(floor["target"])
        got = relay_trace(v, floor, solved)
        if got != target:
            raise SystemExit(f"{name}: relay floor {floor['floor']} does not light its receptor with its solution "
                             f"({sorted(got)} != {sorted(target)})")
        if relay_trace(v, floor, start) == target:
            raise SystemExit(f"{name}: relay floor {floor['floor']} is already solved in its starting state")


def markers(name: str, v: dict, mk: dict, extra: dict | None = None) -> dict[tuple[int, int, int], str]:
    """Centred cell -> marker metadata (block= is added from the art cell later unless given)."""
    c = CHALLENGES[name]
    out: dict[tuple[int, int, int], str] = {}

    def put(p, meta):
        p = tuple(p)
        if p in out:
            raise SystemExit(f"{name}: two markers at {p}: {out[p]} / {meta}")
        out[p] = meta

    for p, meta in (extra or {}).items():
        put(p, meta)

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
    for p, meta in relay_markers(name, mk, c):
        put(p, meta)
    arm = [tuple(p) for p in mk.get("arm_chest", [])]
    for p, block in sorted(v.items()):
        if p in out:
            continue
        if block.startswith("minecraft:barrel"):
            if arm and p == arm[0]:
                put(p, f"chest loot={WORKSHOP_ARM}")
            elif name == WORKSHOP and p[1] > 0:
                put(p, f"chest loot={WHEELHOUSE_LOOT}")      # the wheelhouse stores
            else:
                put(p, "chest")
        elif block.startswith("minecraft:lever"):
            sluice = f" sluice={house_of(p[0], p[2]) + 1}" if name == WORKSHOP else ""
            put(p, f"lever challenge={c['levers']}{sluice}")
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
    # The Void Observatory: a lens on each islet (two by symmetry), shulker nests guarding them.
    for p in mk.get("lens_sockets", []):
        put(p, f"socket challenge={c['lens_sockets']} look={SOCKET_LOOK[name]}")
    for p in mk.get("shulker_nests", []):
        put(p, f"boss challenge={c['shulker_nests']}")
    # Standing stones are read like the compass: from the north, clockwise.
    stones = sorted(mk.get("order_stones", []), key=lambda p: math.atan2(p[0], -p[2]) % (2 * math.pi))
    for order, p in enumerate(stones, 1):
        put(p, f"brazier challenge={c['stones']} order={order}")
    # Designed beds keep their soil: the art lists inclusive (lo, hi) boxes in markers["keep_soil"],
    # cut to the art's own extent; the marker stands at the box's low corner.
    extent = [(min(p[i] for p in v), max(p[i] for p in v)) for i in range(3)]
    for lo, hi in mk.get("keep_soil", []):
        lo = tuple(max(lo[i], extent[i][0]) for i in range(3))
        hi = tuple(min(hi[i], extent[i][1]) for i in range(3))
        size = ",".join(str(hi[i] - lo[i] + 1) for i in range(3))
        put(lo, f"keep_soil size={size}")
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
    relay_check(name, v, mk, out)
    ground = next(((x, 0, z) for (x, z) in [(0, 0)] + [(x, z) for (x, y, z) in sorted(v) if y == 0]
                   if (x, 0, z) in v and (x, 0, z) not in out), None)
    if ground is None:
        raise SystemExit(f"{name}: no free cell on the ground layer for the ground marker")
    put(ground, "ground")
    return out


OWN_BLOCK = ("pedestal", "mirror", "socket", "hidden", "gate", "lock")


def art(name: str):
    """The ruin's voxels and markers, with the workshop's Create engine added."""
    module, fn = RUINS[name]
    v, mk = getattr(importlib.import_module(module), fn)()
    v = dict(v)
    extra, nbt, layout = workshop_create(v, mk) if name == WORKSHOP else ({}, {}, None)
    relay_shafts(v, mk)
    for p, state in v.items():
        v[p] = sane(state)
    for p, meta in extra.items():
        extra[p] = " ".join("block=" + sane(t[len("block="):]) if t.startswith("block=") else t for t in meta.split())
    return v, mk, extra, nbt, layout


def template(name: str):
    v, mk, extra, nbt, _ = art(name)
    marks = markers(name, v, mk, extra)
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
            if (block and not block.endswith(":air") and meta.split()[0] not in OWN_BLOCK
                    and " block=" not in meta):
                meta += " block=" + block
            entries.append((local, ("minecraft:structure_block", {"mode": "data"}), meta, None))
        else:
            entries.append((local, split(v[p]), None, nbt.get(p)))
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
    for local, (block, props), meta, data in entries:
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
        elif data:
            entry["nbt"] = (COMPOUND, {k: (STRING, x) if isinstance(x, str) else (INT, x) for k, x in data.items()})
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


def _blockstate_properties(jar: zipfile.ZipFile, namespace: str) -> dict[str, set[str] | None]:
    """Block id -> property names its blockstate file mentions (None: not listed per property)."""
    out: dict[str, set[str] | None] = {}
    prefix = f"assets/{namespace}/blockstates/"
    for entry in jar.namelist():
        if not entry.startswith(prefix) or not entry.endswith(".json"):
            continue
        data = json.loads(jar.read(entry))
        keys: set[str] = set()
        for variant in data.get("variants", {}):
            keys |= {kv.split("=")[0] for kv in variant.split(",") if "=" in kv}
        for part in data.get("multipart", []):
            when = part.get("when", {})
            for alternative in when.get("OR", [when]) if isinstance(when, dict) else []:
                keys |= set(alternative)
        out[f"{namespace}:{Path(entry).stem}"] = keys
    return out


def unknown_blocks() -> dict[str, list[str]]:
    """Block ids the 1.21.1 client resources or the pack's Create jar do not know, and Create block
    states with properties their blockstate file never mentions (empty without the jars)."""
    known: dict[str, set[str] | None] = {}
    if VANILLA.is_file():
        with zipfile.ZipFile(VANILLA) as jar:
            known.update({k: None for k in _blockstate_properties(jar, "minecraft")})
        known.update({f"minecraft:{b}": None for b in ("air", "water", "lava", "structure_block")})
    if CREATE.is_file():
        with zipfile.ZipFile(CREATE) as jar:
            known.update(_blockstate_properties(jar, "create"))
    if not known:
        return {}
    found = {}
    for name in RUINS:
        _, entries, marks = template(name)
        states = {(b, tuple(sorted(props))) for _, (b, props), _, _ in entries}
        for meta in marks.values():
            for token in meta.split()[1:]:
                if token.startswith("block="):
                    block, props = split(token[len("block="):])
                    states.add((block if ":" in block else "minecraft:" + block, tuple(sorted(props))))
        bad = set()
        for block, props in states:
            namespace = block.split(":")[0]
            if namespace == "minecraft" and not VANILLA.is_file() or namespace == "create" and not CREATE.is_file():
                continue
            if namespace not in ("minecraft", "create"):
                continue
            if block not in known:
                bad.add(block)
            elif known[block] is not None:
                extra = [k for k in props if k not in known[block]
                         and not (k == "waterlogged" and block in WATERLOGGABLE_CREATE)]
                if extra:
                    bad.add(f"{block}[{','.join(extra)}]")
        if bad:
            found[name] = sorted(bad)
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--report", action="store_true")
    args = parser.parse_args()
    bad = unknown_blocks()
    if bad:
        print("unknown block ids or properties:", json.dumps(bad), file=sys.stderr)
        return 1
    failed = 0
    for name in RUINS:
        target = OUT / f"{name}.nbt"
        solution = solution_json(name)
        solution_file = SOLUTIONS / f"{name}.json"
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
            if solution is not None and (not solution_file.is_file()
                                         or solution_file.read_text(encoding="utf-8") != solution):
                print(f"{solution_file.relative_to(ROOT)} is missing or stale; run without --check", file=sys.stderr)
                failed += 1
        else:
            data = build(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            print(f"wrote {target.relative_to(ROOT)} ({len(data)} bytes)")
            if solution is not None:
                solution_file.parent.mkdir(parents=True, exist_ok=True)
                with open(solution_file, "w", encoding="utf-8", newline="") as out:
                    out.write(solution)
                print(f"wrote {solution_file.relative_to(ROOT)}")
    if args.check and not failed:
        print(f"{len(RUINS)} ruin templates match their art")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
