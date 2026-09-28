"""A small model of Create 6.0.10's rotation propagation, for designing and checking ruin layouts.

It follows com.simibubi.create.content.kinetics.RotationPropagator (getRotationSpeedModifier,
getAxisModifier, isLargeToSmallCog) for the parts the Sunken Workshop uses: shafts, small and large
cogwheels, gearboxes, gauges, mechanical pumps (a small cog with no shaft), mechanical bearings (a
shaft at the back) and large water wheels (sources). Speeds are Create's signed RPM along the
block's rotation axis. The GameTests on the full pack are the proof; this model is how the layout
and the scripted solution were designed, and how the exporter catches a layout that no longer
connects after an art change.

    blocks = {(x, y, z): "create:shaft[axis=x]", ...}
    result = propagate(blocks, {(26, -2, 0): -4})
    result.speed[(5, 4, 0)], result.conflicts
"""
from __future__ import annotations

from dataclasses import dataclass, field

AXES = "xyz"
DIRECTIONS = {  # name -> (dx, dy, dz)
    "down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
    "west": (-1, 0, 0), "east": (1, 0, 0),
}
KINETIC = {
    "create:shaft", "create:cogwheel", "create:large_cogwheel", "create:gearbox", "create:mechanical_pump",
    "create:mechanical_bearing", "create:speedometer", "create:stressometer", "create:large_water_wheel",
}
SMALL_COGS = {"create:cogwheel", "create:mechanical_pump"}
LARGE_COGS = {"create:large_cogwheel"}


def split(state: str) -> tuple[str, dict[str, str]]:
    if "[" not in state:
        return state, {}
    name, props = state[:-1].split("[")
    return name, dict(p.split("=") for p in props.split(","))


def axis_of(direction: str) -> str:
    return "x" if direction in ("west", "east") else "y" if direction in ("up", "down") else "z"


def positive(direction: str) -> bool:
    return direction in ("up", "south", "east")


def opposite(direction: str) -> str:
    return {"down": "up", "up": "down", "north": "south", "south": "north", "west": "east", "east": "west"}[direction]


def nearest(dx: int, dy: int, dz: int) -> str:
    """Direction.getNearest for a block offset (ties never occur for the offsets used)."""
    best, score = "north", -1e9
    for name, (x, y, z) in DIRECTIONS.items():
        s = x * dx + y * dy + z * dz
        if s > score:
            best, score = name, s
    return best


@dataclass
class Block:
    name: str
    props: dict[str, str]

    def rotation_axis(self) -> str:
        p = self.props
        if self.name in ("create:mechanical_pump", "create:mechanical_bearing"):
            return axis_of(p["facing"])
        if self.name in ("create:speedometer", "create:stressometer"):
            facing = axis_of(p["facing"])
            first = p.get("axis_along_first", "false") == "true"
            return {"x": "y" if first else "z", "y": "x" if first else "z", "z": "x" if first else "y"}[facing]
        return p["axis"]

    def has_shaft_towards(self, direction: str) -> bool:
        if self.name == "create:gearbox":
            return axis_of(direction) != self.props["axis"]
        if self.name == "create:mechanical_pump":
            return False
        if self.name == "create:mechanical_bearing":
            return direction == opposite(self.props["facing"])
        return axis_of(direction) == self.rotation_axis()

    def small(self) -> bool:
        return self.name in SMALL_COGS

    def large(self) -> bool:
        return self.name in LARGE_COGS


def parse(blocks: dict) -> dict[tuple, Block]:
    out = {}
    for pos, state in blocks.items():
        name, props = split(state)
        if name in KINETIC:
            out[tuple(pos)] = Block(name, props)
    return out


@dataclass
class Result:
    speed: dict = field(default_factory=dict)
    source_face: dict = field(default_factory=dict)   # gearbox -> direction towards its source
    network: dict = field(default_factory=dict)       # pos -> network id (its first source)
    conflicts: list = field(default_factory=list)     # (pos, expected, found)


def _axis_modifier(result: Result, pos, block: Block, direction: str) -> float:
    if block.name != "create:gearbox" or pos not in result.source_face:
        return 1.0
    source = result.source_face[pos]
    if axis_of(direction) == axis_of(source):
        return 1.0 if direction == source else -1.0
    return -1.0 if positive(direction) == positive(source) else 1.0


def _large_to_small(large: Block, small: Block, diff) -> bool:
    axis = large.rotation_axis()
    if axis != small.rotation_axis():
        return False
    i = AXES.index(axis)
    if diff[i] != 0:
        return False
    return all(abs(diff[j]) == 1 for j in range(3) if j != i)


def modifier(result: Result, a_pos, a: Block, b_pos, b: Block) -> float:
    """RotationPropagator.getRotationSpeedModifier(a, b) for the modelled parts (0: not connected)."""
    diff = tuple(b_pos[i] - a_pos[i] for i in range(3))
    direction = nearest(*diff)
    aligned = all(diff[i] == 0 for i in range(3) if AXES[i] != axis_of(direction))
    by_axis = aligned and a.has_shaft_towards(direction) and b.has_shaft_towards(opposite(direction))
    by_gears = a.small() and b.small()
    if by_axis:
        m = _axis_modifier(result, b_pos, b, opposite(direction))
        m = 1 / m if m else 0
        return _axis_modifier(result, a_pos, a, direction) * m
    if a.large() and b.small() and _large_to_small(a, b, diff):
        return -2.0
    if b.large() and a.small() and _large_to_small(b, a, diff):
        return -0.5
    if by_gears:
        if sum(abs(d) for d in diff) != 1:
            return 0.0
        if axis_of(direction) == a.rotation_axis():
            return 0.0
        if a.rotation_axis() == b.rotation_axis():
            return -1.0
    return 0.0


def rotate90(v, axis: str):
    """net.createmod.catnip.math.VecHelper.rotate(v, 90, axis)."""
    x, y, z = v
    return {"x": (x, -z, y), "y": (z, y, -x), "z": (-y, x, z)}[axis]


def water_wheel_speed(axis: str, flow, rpm: int = 4) -> int:
    """A large water wheel's generated speed (WaterWheelBlockEntity.determineAndApplyFlowScore and
    getGeneratedSpeed) when water flows along ``flow`` through the three rim cells under its centre."""
    i = AXES.index(axis)
    across = next(a for a in range(3) if a != i and a != 1)
    score = 0
    for t in (-1, 0, 1):
        off = [0, -2, 0]
        off[across] = t
        length = sum(c * c for c in off) ** 0.5
        tangent = rotate90(tuple(c / length for c in off), axis)
        plane = [0.0 if j == i else float(flow[j]) for j in range(3)]
        norm = sum(c * c for c in plane) ** 0.5
        if norm == 0:
            continue
        d = sum(plane[j] / norm * tangent[j] for j in range(3))
        if abs(d) > 0.5:
            score += 1 if d > 0 else -1
    return max(-1, min(1, score)) * rpm          # clamp(score, -1, 1) * 8 / size, size 2


def neighbours(pos, block: Block):
    x, y, z = pos
    for d in DIRECTIONS.values():
        yield (x + d[0], y + d[1], z + d[2])
    if block.small() or block.large():
        i = AXES.index(block.rotation_axis())
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    off = (dx, dy, dz)
                    if off[i] == 0 and dx * dx + dy * dy + dz * dz == 2:
                        yield (x + dx, y + dy, z + dz)


def propagate(states: dict, sources: dict) -> Result:
    """Speeds reached from {pos: rpm} sources, breadth first; conflicting reaches are recorded."""
    blocks = parse(states)
    result = Result()
    queue = []
    for pos, rpm in sources.items():
        pos = tuple(pos)
        if pos not in blocks or rpm == 0:
            continue
        if pos in result.speed:
            continue
        result.speed[pos] = float(rpm)
        result.network[pos] = pos
        queue.append(pos)
    while queue:
        a_pos = queue.pop(0)
        a = blocks[a_pos]
        for b_pos in neighbours(a_pos, a):
            b = blocks.get(b_pos)
            if b is None:
                continue
            m = modifier(result, a_pos, a, b_pos, b)
            if m == 0:
                continue
            conveyed = result.speed[a_pos] * m
            if b_pos in result.speed:
                if abs(result.speed[b_pos] - conveyed) > 1e-6:
                    result.conflicts.append((b_pos, conveyed, result.speed[b_pos]))
                elif result.network[b_pos] != result.network[a_pos]:
                    old = result.network[b_pos]
                    for p, n in list(result.network.items()):
                        if n == old:
                            result.network[p] = result.network[a_pos]
                continue
            result.speed[b_pos] = conveyed
            result.network[b_pos] = result.network[a_pos]
            if b.name == "create:gearbox":
                result.source_face[b_pos] = nearest(*(a_pos[i] - b_pos[i] for i in range(3)))
            queue.append(b_pos)
    return result
