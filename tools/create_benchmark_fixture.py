"""Generate a bounded, vanilla, QA-only early-base benchmark datapack.

Only an explicitly supplied empty output directory is written. Run ``setup``
once from an operator in a loaded Overworld area, then ``board`` as the player.
No function is scheduled by a load/tick tag and none runs during capture.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


NAMESPACE = "entrelumen_benchmark"
PACK_FORMAT = 48  # Minecraft 1.21.1
HALF_WIDTH = 24
HALF_DEPTH = 20
RAIL_X = 22
RAIL_Z = 18
MARKER_TAG = "entrelumen_benchmark_anchor"
CART_TAG = "entrelumen_benchmark_cart"
LIVESTOCK_TAG = "entrelumen_benchmark_livestock"
WORKSHOP_CENTERS = tuple((x, z) for z in (-12, -6) for x in (-3, 2, 7, 12, 17))


def position(origin: tuple[int, int, int], relative: tuple[int, int, int]) -> tuple[int, int, int]:
    return tuple(a + b for a, b in zip(origin, relative))


def xyz(values: tuple[int | float, int | float, int | float]) -> str:
    return " ".join(str(value) for value in values)


def rail_geometry() -> list[dict]:
    """A single degree-two ring; powered rails only occur on straight sides."""
    rails = []
    corners = {
        (-RAIL_X, -RAIL_Z): "south_east",
        (RAIL_X, -RAIL_Z): "south_west",
        (RAIL_X, RAIL_Z): "north_west",
        (-RAIL_X, RAIL_Z): "north_east",
    }
    for z in range(-RAIL_Z, RAIL_Z + 1):
        for x in range(-RAIL_X, RAIL_X + 1):
            if abs(x) != RAIL_X and abs(z) != RAIL_Z:
                continue
            if (x, z) in corners:
                shape = corners[(x, z)]
            else:
                shape = "north_south" if abs(x) == RAIL_X else "east_west"
            powered = ((abs(x) == RAIL_X and z in (-12, -6, 0, 6, 12)) or
                       (abs(z) == RAIL_Z and x in (-18, -12, -6, 0, 6, 12, 18)))
            rails.append({"relative": [x, 1, z], "shape": shape, "powered": powered})
    return rails


def stack_snbt(item: str, count: int, slot: int) -> str:
    # Verified against local 1.21.1 ItemStack codec (id/count) and
    # ContainerHelper (Items/Slot byte). Pre-1.20.5 Count is invalid here.
    return f'{{Slot:{slot}b,id:"minecraft:{item}",count:{count}}}'


def inventory_snbt(item: str, counts: tuple[int, ...]) -> str:
    return "{Items:[" + ",".join(stack_snbt(item, count, slot)
                                for slot, count in enumerate(counts)) + "]}"


def make_fixture(origin: tuple[int, int, int]) -> tuple[dict[str, str], dict]:
    """Return complete files and manifest data without filesystem effects."""
    ox, oy, oz = origin
    if not all(isinstance(value, int) and not isinstance(value, bool) for value in origin):
        raise ValueError("origin coordinates must be integers")
    if not (-29_999_960 <= ox - HALF_WIDTH and ox + HALF_WIDTH <= 29_999_960 and
            -29_999_960 <= oz - HALF_DEPTH and oz + HALF_DEPTH <= 29_999_960):
        raise ValueError("footprint exceeds the safe Overworld coordinate range")
    if not -64 <= oy <= 314:
        raise ValueError("floor and five-block clearance must fit the build height")
    instance = hashlib.sha256(f"{ox},{oy},{oz}".encode("ascii")).hexdigest()[:12]
    cart_tag = f"{CART_TAG}_{instance}"
    livestock_tag = f"{LIVESTOCK_TAG}_{instance}"

    footprint = [[ox - HALF_WIDTH, oy, oz - HALF_DEPTH],
                 [ox + HALF_WIDTH, oy + 5, oz + HALF_DEPTH]]
    blocks: dict[tuple[int, int, int], str] = {
        (x, 0, z): "minecraft:smooth_stone"
        for x in range(-HALF_WIDTH, HALF_WIDTH + 1)
        for z in range(-HALF_DEPTH, HALF_DEPTH + 1)
    }

    def place(relative: tuple[int, int, int], state: str, *, floor_replace: bool = False) -> None:
        previous = blocks.get(relative)
        if previous is not None and not (floor_replace and previous == "minecraft:smooth_stone"):
            raise ValueError(f"fixture collision at {relative}: {previous} vs {state}")
        blocks[relative] = state

    rails = rail_geometry()
    for rail in rails:
        x, y, z = rail["relative"]
        if rail["powered"]:
            place((x, 0, z), "minecraft:redstone_block", floor_replace=True)
            block = f'minecraft:powered_rail[shape={rail["shape"]},powered=true]'
        else:
            block = f'minecraft:rail[shape={rail["shape"]}]'
        place((x, y, z), block)
        rail["position"] = list(position(origin, (x, y, z)))
        rail["support"] = list(position(origin, (x, 0, z)))

    # Nine-by-nine farm with the water source at its center. Every farmland
    # cell is within four horizontal blocks of water, including the corners.
    farm = {"bounds": [[ox - 17, oy, oz - 8], [ox - 9, oy + 1, oz]],
            "water": [ox - 13, oy, oz - 4], "farmland": 80,
            "wheat": 80, "ages": {}}
    for z in range(-8, 1):
        for x in range(-17, -8):
            if (x, z) == (-13, -4):
                place((x, 0, z), "minecraft:water[level=0]", floor_replace=True)
                continue
            place((x, 0, z), "minecraft:farmland[moisture=7]", floor_replace=True)
            age = (x - z) % 7
            farm["ages"][str(age)] = farm["ages"].get(str(age), 0) + 1
            place((x, 1, z), f"minecraft:wheat[age={age}]")

    # The contained barn leaves all sixteen passive animals on solid floor.
    barn = {"bounds": [[ox + 7, oy + 1, oz + 2], [ox + 17, oy + 1, oz + 14]],
            "fence_blocks": 0, "cow": 8, "sheep": 8}
    for z in range(2, 15):
        for x in range(7, 18):
            if x in (7, 17) or z in (2, 14):
                place((x, 1, z), "minecraft:oak_fence")
                barn["fence_blocks"] += 1

    stations = []
    stock_commands = []
    for index, (x, z) in enumerate(WORKSHOP_CENTERS):
        machine = "furnace" if index < 8 else "smoker"
        item = "raw_iron" if machine == "furnace" else "beef"
        relative = {
            "machine": (x, 2, z),
            "input_hopper": (x, 3, z),
            "input_barrel": (x, 4, z),
            "fuel_hopper": (x + 1, 2, z),
            "fuel_barrel": (x + 1, 3, z),
            "output_hopper": (x, 1, z),
            "output_barrel": (x - 1, 1, z),
        }
        place(relative["machine"], f"minecraft:{machine}[facing=south]")
        place(relative["input_hopper"], "minecraft:hopper[facing=down,enabled=true]")
        place(relative["fuel_hopper"], "minecraft:hopper[facing=west,enabled=true]")
        place(relative["output_hopper"], "minecraft:hopper[facing=west,enabled=true]")
        for key in ("input_barrel", "fuel_barrel", "output_barrel"):
            place(relative[key], "minecraft:barrel")
        input_position = position(origin, relative["input_barrel"])
        fuel_position = position(origin, relative["fuel_barrel"])
        stock_commands.append(f"data merge block {xyz(input_position)} {inventory_snbt(item, (64, 64, 64, 64))}")
        stock_commands.append(f"data merge block {xyz(fuel_position)} {inventory_snbt('coal', (32,))}")
        stations.append({"index": index, "machine_type": machine, "input_item": f"minecraft:{item}",
                         "fuel_item": "minecraft:coal", "input_count": 256, "fuel_count": 32,
                         "positions": {key: list(position(origin, value)) for key, value in relative.items()}})

    # Floor lamps keep the open platform readable through the night. They do
    # not touch the farm, barn enclosure, machine footprints or rail support.
    for z in (-15, -2, 16):
        for x in (-19, -7, 4, 19):
            place((x, 0, z), "minecraft:sea_lantern", floor_replace=True)

    anchor = (ox + 0.5, oy + 1, oz + 0.5)
    anchor_selector = (f"@e[type=minecraft:marker,tag={MARKER_TAG},"
                       f"x={anchor[0]},y={anchor[1]},z={anchor[2]},distance=..0.25,limit=1]")
    cart_selector = (f"@e[type=minecraft:minecart,tag={cart_tag},"
                     f"x={anchor[0]},y={anchor[1]},z={anchor[2]},distance=..32,limit=1,sort=nearest]")
    setup = [
        "# Run once as an operator in a fully loaded Overworld area.",
        f"execute if dimension minecraft:overworld unless entity {anchor_selector} run function {NAMESPACE}:internal/construct",
    ]
    construct = [
        "# Called only by setup. The marker goes first, preventing a second build if interrupted.",
        f'summon minecraft:marker {xyz(anchor)} {{Tags:["{MARKER_TAG}"]}}',
        f"fill {ox - HALF_WIDTH} {oy + 1} {oz - HALF_DEPTH} {ox + HALF_WIDTH} {oy + 5} {oz + HALF_DEPTH} minecraft:air",
        f"fill {ox - HALF_WIDTH} {oy} {oz - HALF_DEPTH} {ox + HALF_WIDTH} {oy} {oz + HALF_DEPTH} minecraft:smooth_stone",
    ]
    for relative, state in sorted(blocks.items(), key=lambda entry: (entry[0][1], entry[0][2], entry[0][0])):
        if state == "minecraft:smooth_stone":
            continue
        construct.append(f"setblock {xyz(position(origin, relative))} {state}")
    construct.extend(stock_commands)

    cart_start = (ox - RAIL_X + 0.5, oy + 1.1, oz - 15 + 0.5)
    construct.append(f'summon minecraft:minecart {xyz(cart_start)} '
                     f'{{Tags:["{cart_tag}"],Motion:[0.0d,0.0d,0.4d]}}')
    livestock = []
    for species, points in (("cow", [(x, z) for z in (5, 7) for x in (9, 11, 13, 15)]),
                            ("sheep", [(x, z) for z in (10, 12) for x in (9, 11, 13, 15)])):
        for x, z in points:
            pos = (ox + x + 0.5, oy + 1, oz + z + 0.5)
            construct.append(f'summon minecraft:{species} {xyz(pos)} '
                             f'{{Tags:["{livestock_tag}"],NoAI:0b,PersistenceRequired:1b}}')
            livestock.append({"type": f"minecraft:{species}", "position": list(pos)})

    # A player can call this from any Overworld position. The first command
    # moves them to the tagged cart, fixing yaw/pitch before mounting nearby.
    board = [
        "# Player-only: moves the caller to the fixture cart and mounts it.",
        f"execute if dimension minecraft:overworld if entity {anchor_selector} at {cart_selector} run tp @s ~ ~ ~ 0 0",
        f"execute if dimension minecraft:overworld if entity {anchor_selector} at @s run ride @s mount "
        f"@e[type=minecraft:minecart,tag={cart_tag},distance=..2,limit=1,sort=nearest]",
    ]
    # /function suppresses command-source success feedback in 1.21.1, so
    # /data get would not reliably show these observations to the caller.
    # /tellraw sends a system message directly to the player; its vanilla NBT
    # components resolve Pos/Items when the command runs.
    def tellraw(component: dict) -> str:
        return "tellraw @s " + json.dumps(component, separators=(",", ":"))

    cart_message = {"text": "[early_base] cart Pos: ",
                    "extra": [{"nbt": "Pos", "entity": cart_selector, "interpret": False}]}
    inspect = [
        "# Player-only, read-only observations; compare output Items before and after the route.",
        f"execute if dimension minecraft:overworld if entity {anchor_selector} if entity {cart_selector} "
        f"run {tellraw(cart_message)}",
        f"execute if dimension minecraft:overworld if entity {anchor_selector} unless entity {cart_selector} "
        f"run {tellraw({'text': '[early_base] cart missing'})}",
    ]
    for index in (0, 4, 8, 9):
        output_position = xyz(tuple(stations[index]["positions"]["output_barrel"]))
        prefix = f"[early_base] output {index} ({output_position}) Items: "
        output_message = {"text": prefix,
                          "extra": [{"nbt": "Items", "block": output_position,
                                     "interpret": False}]}
        inspect.append(f"execute if dimension minecraft:overworld if entity {anchor_selector} "
                       f"if data block {output_position} Items run "
                       f"{tellraw(output_message)}")
        inspect.append(f"execute if dimension minecraft:overworld if entity {anchor_selector} "
                       f"unless data block {output_position} Items run {tellraw({'text': prefix + '[]'})}")

    function_dir = f"data/{NAMESPACE}/function"
    functions = {
        f"{function_dir}/setup.mcfunction": "\n".join(setup) + "\n",
        f"{function_dir}/internal/construct.mcfunction": "\n".join(construct) + "\n",
        f"{function_dir}/board.mcfunction": "\n".join(board) + "\n",
        f"{function_dir}/inspect.mcfunction": "\n".join(inspect) + "\n",
    }
    counts = Counter(state.split("[")[0] for state in blocks.values())
    block_entities = {"minecraft:barrel": 30, "minecraft:hopper": 30,
                      "minecraft:furnace": 8, "minecraft:smoker": 2}
    entities = {"minecraft:marker": 1, "minecraft:minecart": 1,
                "minecraft:cow": 8, "minecraft:sheep": 8}
    manifest = {
        "schema": 1, "fixture": "early_base_vanilla_prototype", "qa_only": True,
        "target": {"minecraft": "1.21.1", "neoforge": "21.1.249", "client_dependencies": 151},
        "origin": list(origin), "origin_meaning": "top floor block", "dimension": "minecraft:overworld",
        "entity_tags": {"anchor": MARKER_TAG, "cart": cart_tag, "livestock": livestock_tag},
        "design_references": [
            {"source": "https://content.instructables.com/F29/SA95/I1NUIN6K/F29SA95I1NUIN6K.png",
             "resolution": [854, 480],
             "observed": "Vanilla furnace: input chest above a downward hopper, side fuel chest above its hopper, output hopper below into a chest. The fixture uses barrels in those roles."},
            {"source": "https://i.ytimg.com/vi/FTESa5eO8dQ/maxresdefault.jpg",
             "resolution": [1280, 720],
             "observed": "Vanilla track: ordinary rails form the curve between straight powered sections on supporting blocks."},
        ],
        "footprint": footprint, "cleared_air_volume": 49 * 41 * 5,
        "block_counts": dict(sorted(counts.items())), "block_total": sum(counts.values()),
        "block_entity_counts": block_entities, "block_entity_total": sum(block_entities.values()),
        "entity_counts": entities, "entity_total": sum(entities.values()),
        "rail_geometry": {"bounds": [[ox - RAIL_X, oy + 1, oz - RAIL_Z],
                                     [ox + RAIL_X, oy + 1, oz + RAIL_Z]],
                          "count": len(rails), "powered_count": sum(rail["powered"] for rail in rails),
                          "cart_spawn": list(cart_start), "cart_initial_motion": [0.0, 0.0, 0.4],
                          "rails": rails},
        "workshop": {"stations": stations, "input_total": 2560, "coal_total": 320,
                     "furnace_processing_ticks_per_item": 200,
                     "smoker_processing_ticks_per_item": 100,
                     "stocked_minimum_minutes_at_full_speed": 256 * 100 / 20 / 60},
        "farm": farm, "barn": barn, "livestock": livestock,
        "functions": {"setup": f"{NAMESPACE}:setup", "board": f"{NAMESPACE}:board",
                      "inspect": f"{NAMESPACE}:inspect"},
        "limitations": [
            "Prototype early-base QA fixture for the active 151-dependency client; not final 600-mod, co-op, campaign or performance acceptance.",
            "Setup replaces only blocks within the stated footprint and clears five blocks above the floor; choose a dedicated empty QA site.",
            "All footprint chunks must be loaded before setup. An interrupted setup leaves the guard marker and needs manual review in a disposable QA save.",
            "No automatic tick/load function, harvesting, livestock feeding, inventory refill, or command activity during capture.",
            "Inputs and coal are finite; full-speed smoker stock lasts about 21 minutes. Minecart motion requires live game validation.",
        ],
    }
    return functions, manifest


def create(output: Path, origin: tuple[int, int, int]) -> dict:
    """Write once to an empty directory; never overwrite or touch pack/."""
    output = Path(output)
    if not output.is_absolute():
        raise ValueError("--output must be an absolute path")
    resolved = output.resolve()
    pack = (Path(__file__).resolve().parents[1] / "pack").resolve()
    if resolved == pack or pack in resolved.parents:
        raise ValueError("QA fixture output cannot be pack/ or a descendant")
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError("--output must be a new or empty directory; refusing to overwrite")
    functions, manifest = make_fixture(origin)
    files = {
        "pack.mcmeta": json.dumps({"pack": {"pack_format": PACK_FORMAT,
                                            "description": "ENTRELUMEN QA early-base vanilla benchmark fixture"}},
                                  indent=2) + "\n",
        **functions,
    }
    command_files = {name: hashlib.sha256(contents.encode("utf-8")).hexdigest()
                     for name, contents in sorted(functions.items())}
    command_bundle = "".join(f"{name}\n{functions[name]}" for name in sorted(functions))
    manifest["command_files_sha256"] = command_files
    manifest["commands_sha256"] = hashlib.sha256(command_bundle.encode("utf-8")).hexdigest()
    files["fixture-manifest.json"] = json.dumps(manifest, indent=2, sort_keys=True) + "\n"
    output.mkdir(parents=True, exist_ok=True)
    for name, contents in files.items():
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(contents, encoding="utf-8", newline="\n")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="new or empty absolute output directory")
    parser.add_argument("--origin", type=int, nargs=3, metavar=("X", "Y", "Z"), required=True,
                        help="top floor block coordinate in the Overworld")
    args = parser.parse_args()
    try:
        manifest = create(args.output, tuple(args.origin))
    except ValueError as exc:
        parser.error(str(exc))
    print(json.dumps({"output": str(args.output.resolve()), "origin": manifest["origin"],
                      "commands_sha256": manifest["commands_sha256"]}))


if __name__ == "__main__":
    main()
