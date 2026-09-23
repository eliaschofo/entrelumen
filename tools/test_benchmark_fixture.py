"""Structural checks for the generated QA fixture; no game process is started."""

from collections import Counter, deque
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest

import create_benchmark_fixture as fixture


SHAPE_EXITS = {
    "north_south": ((0, -1), (0, 1)),
    "east_west": ((-1, 0), (1, 0)),
    "south_east": ((0, 1), (1, 0)),
    "south_west": ((0, 1), (-1, 0)),
    "north_west": ((0, -1), (-1, 0)),
    "north_east": ((0, -1), (1, 0)),
}


class BenchmarkFixtureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="entrelumen-benchmark-fixture-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.origin = (193, 72, -317)
        self.functions, self.manifest = fixture.make_fixture(self.origin)
        self.construct = self.functions[f"data/{fixture.NAMESPACE}/function/internal/construct.mcfunction"]

    def test_closed_single_rail_loop_with_real_corners_and_powered_support(self):
        rails = self.manifest["rail_geometry"]["rails"]
        by_xz = {(rail["position"][0], rail["position"][2]): rail for rail in rails}
        self.assertEqual(160, len(rails))
        self.assertEqual(len(rails), len(by_xz))
        start = next(iter(by_xz))
        visited = {start}
        pending = deque([start])
        while pending:
            current = pending.popleft()
            rail = by_xz[current]
            self.assertEqual(self.origin[1] + 1, rail["position"][1])
            for dx, dz in SHAPE_EXITS[rail["shape"]]:
                adjacent = (current[0] + dx, current[1] + dz)
                self.assertIn(adjacent, by_xz, (current, rail["shape"]))
                self.assertIn((-dx, -dz), SHAPE_EXITS[by_xz[adjacent]["shape"]])
                if adjacent not in visited:
                    visited.add(adjacent)
                    pending.append(adjacent)
        self.assertEqual(set(by_xz), visited)

        corners = {(-22, -18): "south_east", (22, -18): "south_west",
                   (22, 18): "north_west", (-22, 18): "north_east"}
        for (dx, dz), shape in corners.items():
            self.assertEqual(shape, by_xz[(self.origin[0] + dx, self.origin[2] + dz)]["shape"])
        for rail in rails:
            x, y, z = rail["position"]
            block = "powered_rail" if rail["powered"] else "rail"
            state = f"minecraft:{block}[shape={rail['shape']}"
            self.assertIn(f"setblock {x} {y} {z} {state}", self.construct)
            if rail["powered"]:
                self.assertIn(f"setblock {x} {y - 1} {z} minecraft:redstone_block", self.construct)
                self.assertIn(rail["shape"], ("north_south", "east_west"))
        self.assertEqual(24, sum(rail["powered"] for rail in rails))
        self.assertEqual(24, self.manifest["block_counts"]["minecraft:redstone_block"])
        self.assertIn("Motion:[0.0d,0.0d,0.4d]", self.construct)

    def test_each_machine_has_feeding_and_collection_path_with_real_stock(self):
        stations = self.manifest["workshop"]["stations"]
        self.assertEqual(10, len(stations))
        self.assertEqual(Counter({"furnace": 8, "smoker": 2}),
                         Counter(station["machine_type"] for station in stations))
        self.assertEqual({"minecraft:barrel": 30, "minecraft:hopper": 30,
                          "minecraft:furnace": 8, "minecraft:smoker": 2},
                         self.manifest["block_entity_counts"])
        for station in stations:
            with self.subTest(index=station["index"]):
                p = station["positions"]
                mx, my, mz = p["machine"]
                self.assertEqual([mx, my + 1, mz], p["input_hopper"])
                self.assertEqual([mx, my + 2, mz], p["input_barrel"])
                self.assertEqual([mx + 1, my, mz], p["fuel_hopper"])
                self.assertEqual([mx + 1, my + 1, mz], p["fuel_barrel"])
                self.assertEqual([mx, my - 1, mz], p["output_hopper"])
                self.assertEqual([mx - 1, my - 1, mz], p["output_barrel"])
                self.assertIn(f"setblock {mx} {my} {mz} minecraft:{station['machine_type']}", self.construct)
                for key, direction in (("input_hopper", "down"),
                                       ("fuel_hopper", "west"),
                                       ("output_hopper", "west")):
                    self.assertIn(f"setblock {fixture.xyz(tuple(p[key]))} "
                                  f"minecraft:hopper[facing={direction},enabled=true]", self.construct)
                for key in ("input_barrel", "fuel_barrel", "output_barrel"):
                    self.assertIn(f"setblock {fixture.xyz(tuple(p[key]))} minecraft:barrel", self.construct)
                material = station["input_item"].split(":")[1]
                input_nbt = fixture.inventory_snbt(material, (64, 64, 64, 64))
                fuel_nbt = fixture.inventory_snbt("coal", (32,))
                self.assertIn(f"data merge block {fixture.xyz(tuple(p['input_barrel']))} {input_nbt}",
                              self.construct)
                self.assertIn(f"data merge block {fixture.xyz(tuple(p['fuel_barrel']))} {fuel_nbt}",
                              self.construct)
        self.assertNotIn("Count:", self.construct)
        self.assertEqual(2560, sum(s["input_count"] for s in stations))
        self.assertEqual(320, sum(s["fuel_count"] for s in stations))
        self.assertGreater(self.manifest["workshop"]["stocked_minimum_minutes_at_full_speed"], 15)

    def test_all_hoppers_pull_from_above_and_push_into_the_expected_machine_or_barrel(self):
        placed = {}
        pattern = re.compile(r"^setblock (-?\d+) (-?\d+) (-?\d+) (minecraft:[^\s]+)$", re.MULTILINE)
        for match in pattern.finditer(self.construct):
            x, y, z = map(int, match.group(1, 2, 3))
            placed[(x, y, z)] = match.group(4)
        directions = {"down": (0, -1, 0), "west": (-1, 0, 0)}
        actual = []
        for (x, y, z), state in placed.items():
            if not state.startswith("minecraft:hopper["):
                continue
            facing = re.search(r"facing=(down|west)", state).group(1)
            dx, dy, dz = directions[facing]
            source = placed.get((x, y + 1, z), "")
            target = placed.get((x + dx, y + dy, z + dz), "")
            actual.append((source.split("[")[0], target.split("[")[0]))
        self.assertEqual(30, len(actual))
        self.assertEqual(Counter({("minecraft:barrel", "minecraft:furnace"): 16,
                                  ("minecraft:barrel", "minecraft:smoker"): 4,
                                  ("minecraft:furnace", "minecraft:barrel"): 8,
                                  ("minecraft:smoker", "minecraft:barrel"): 2}),
                         Counter(actual))

    def test_farm_and_barn_are_inside_bounds_and_spawns_remain_contained(self):
        farm = self.manifest["farm"]
        self.assertEqual(80, farm["farmland"])
        self.assertEqual(80, farm["wheat"])
        self.assertEqual(80, sum(farm["ages"].values()))
        self.assertEqual(7, len(farm["ages"]))
        fx, fy, fz = farm["water"]
        self.assertIn(f"setblock {fx} {fy} {fz} minecraft:water[level=0]", self.construct)
        self.assertEqual(80, self.manifest["block_counts"]["minecraft:farmland"])
        self.assertEqual(80, self.manifest["block_counts"]["minecraft:wheat"])
        barn = self.manifest["barn"]
        self.assertEqual(44, barn["fence_blocks"])
        self.assertEqual(16, len(self.manifest["livestock"]))
        for animal in self.manifest["livestock"]:
            x, y, z = animal["position"]
            self.assertTrue(barn["bounds"][0][0] + 1 < x < barn["bounds"][1][0])
            self.assertTrue(barn["bounds"][0][2] + 1 < z < barn["bounds"][1][2])
            self.assertEqual(self.origin[1] + 1, y)
            self.assertIn(f"summon {animal['type']} {fixture.xyz(tuple(animal['position']))}", self.construct)
        self.assertEqual({"minecraft:marker": 1, "minecraft:minecart": 1,
                          "minecraft:cow": 8, "minecraft:sheep": 8},
                         self.manifest["entity_counts"])

    def test_output_is_deterministic_guarded_and_never_overwrites(self):
        first = self.base / "first"
        second = self.base / "second"
        manifest = fixture.create(first, self.origin)
        fixture.create(second, self.origin)
        paths = sorted(path.relative_to(first) for path in first.rglob("*") if path.is_file())
        self.assertEqual([first / relative for relative in paths],
                         sorted(path for path in first.rglob("*") if path.is_file()))
        for relative in paths:
            self.assertEqual((first / relative).read_bytes(), (second / relative).read_bytes())
        self.assertEqual(48, json.loads((first / "pack.mcmeta").read_text())["pack"]["pack_format"])
        self.assertEqual(manifest, json.loads((first / "fixture-manifest.json").read_text()))
        self.assertFalse(any("/tags/function/" in str(path).replace("\\", "/") for path in paths))
        for name, expected in manifest["command_files_sha256"].items():
            self.assertEqual(expected, hashlib.sha256((first / name).read_bytes()).hexdigest())
        setup = self.functions[f"data/{fixture.NAMESPACE}/function/setup.mcfunction"]
        self.assertIn("if dimension minecraft:overworld unless entity", setup)
        self.assertIn(f"run function {fixture.NAMESPACE}:internal/construct", setup)
        self.assertTrue(self.construct.splitlines()[1].startswith("summon minecraft:marker "))
        self.assertEqual(1, self.construct.count("summon minecraft:marker "))
        self.assertNotIn("kill @", "\n".join(self.functions.values()))
        self.assertNotIn("gamerule randomTickSpeed", "\n".join(self.functions.values()))
        self.assertNotIn("schedule function", "\n".join(self.functions.values()))
        self.assertIn("run ride @s mount @e[type=minecraft:minecart,tag=", self.functions[
            f"data/{fixture.NAMESPACE}/function/board.mcfunction"])
        inspect = self.functions[f"data/{fixture.NAMESPACE}/function/inspect.mcfunction"]
        commands = [line for line in inspect.splitlines() if not line.startswith("#")]
        self.assertEqual(10, len(commands))
        self.assertNotIn("data get", inspect)
        for command in commands:
            self.assertIn("execute if dimension minecraft:overworld if entity ", command)
            self.assertIn(" run tellraw @s ", command)
        cart_message = json.loads(commands[0].split(" run tellraw @s ", 1)[1])
        self.assertEqual("[early_base] cart Pos: ", cart_message["text"])
        self.assertEqual("Pos", cart_message["extra"][0]["nbt"])
        self.assertIn(self.manifest["entity_tags"]["cart"], cart_message["extra"][0]["entity"])
        self.assertFalse(cart_message["extra"][0]["interpret"])
        self.assertIn("unless entity", commands[1])
        for pair, station_index in enumerate((0, 4, 8, 9)):
            output_position = fixture.xyz(tuple(self.manifest["workshop"]["stations"][station_index]
                                                ["positions"]["output_barrel"]))
            stocked, empty = commands[2 + pair * 2:4 + pair * 2]
            self.assertIn(f"if data block {output_position} Items", stocked)
            self.assertIn(f"unless data block {output_position} Items", empty)
            stocked_message = json.loads(stocked.split(" run tellraw @s ", 1)[1])
            empty_message = json.loads(empty.split(" run tellraw @s ", 1)[1])
            self.assertEqual(f"[early_base] output {station_index} ({output_position}) Items: ",
                             stocked_message["text"])
            self.assertEqual({"nbt": "Items", "block": output_position,
                              "interpret": False}, stocked_message["extra"][0])
            self.assertEqual(stocked_message["text"] + "[]", empty_message["text"])

        sentinel = first / "sentinel.txt"
        sentinel.write_text("leave me alone")
        with self.assertRaisesRegex(ValueError, "refusing to overwrite"):
            fixture.create(first, self.origin)
        self.assertEqual("leave me alone", sentinel.read_text())
        with self.assertRaisesRegex(ValueError, "absolute path"):
            fixture.create(Path("relative-fixture"), self.origin)

    def test_invalid_origin_fails_before_creating_directory(self):
        for origin in ((0, -65, 0), (0, 315, 0), (29_999_950, 80, 0),
                       (0, 80, -29_999_950)):
            with self.subTest(origin=origin):
                path = self.base / f"bad-{len(list(self.base.iterdir()))}"
                with self.assertRaises(ValueError):
                    fixture.create(path, origin)
                self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
