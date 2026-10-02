"""Productive Bees milestones: ENTRELUMEN's own criteria-only advancements exist for every bee quest, in 1.21 shape."""
import json
from pathlib import Path
import re
import unittest

import port_productivebees_advancements as port

ROOT = port.ROOT
OUT = port.OUT_DIR


def committed():
    return {p.relative_to(OUT).as_posix(): json.loads(p.read_text(encoding="utf-8"))
            for p in sorted(OUT.rglob("*.json"))}


def walk(node):
    """Every dict inside a JSON document."""
    if isinstance(node, dict):
        yield node
        for v in node.values():
            yield from walk(v)
    elif isinstance(node, list):
        for v in node:
            yield from walk(v)


class CommittedTest(unittest.TestCase):
    def test_one_file_per_quest_advancement_and_no_copy_of_the_mod(self):
        used = port.used_advancements()
        self.assertEqual(len(used), 17)
        self.assertEqual(set(committed()), {f"{path}.json" for path in used})
        # Productive Bees is All Rights Reserved: nothing is written under its namespace (check_loot_tables --copies).
        self.assertFalse((ROOT / "pack/kubejs/data/productivebees").exists())
        for sector in (ROOT / "content/sectors").glob("*.json"):
            self.assertNotIn('"advancement": "productivebees:', sector.read_text(encoding="utf-8"), sector.name)

    def test_every_bee_advancement_task_has_its_own_title(self):
        for sector in (ROOT / "content/sectors").glob("sector_bees_*.json"):
            for match in re.finditer(r'\{"type": "advancement", "advancement": "entrelumen:productivebees/[^"]+"(, "title": \{[^}]+\})?',
                                     sector.read_text(encoding="utf-8")):
                self.assertTrue(match.group(1), f"{sector.name}: {match.group(0)} has no task title")

    def test_criteria_only_and_loaded_only_with_the_mod(self):
        for rel, doc in committed().items():
            self.assertEqual(set(doc) - {"_comment"}, {"neoforge:conditions", "criteria", "requirements",
                                                       "sends_telemetry_event"}, rel)
            self.assertEqual(doc["neoforge:conditions"], [{"type": "neoforge:mod_loaded", "modid": "productivebees"}], rel)
            self.assertNotIn("display", doc, rel)
            self.assertNotIn("parent", doc, rel)

    def test_1_21_format(self):
        for rel, doc in committed().items():
            for node in walk(doc["criteria"]):
                self.assertNotIn("tag", node, f"{rel}: 'tag' filter must be 'items': '#...'")
                if not isinstance(node.get("item"), dict):
                    self.assertNotIn("item", node, f"{rel}: 'item' filter must be 'items'")
                self.assertNotIn("beeName", node, rel)
                if node.get("condition") == "minecraft:block_state_property":
                    self.assertNotIn("state", node, f"{rel}: the loot condition key is 'properties'")
                for value in (node.get("properties") or {}).values():
                    self.assertIsInstance(value, str, f"{rel}: block state values are strings in 1.21")
            for crit in doc["criteria"].values():
                if crit["trigger"].startswith("productivebees:"):
                    self.assertIn("bee", crit["conditions"], f"{rel}: the 1.21 trigger codec requires 'bee'")

    def test_nbt_comments(self):
        docs = committed()
        for rel in ("bee_cage/quartz_nest/catch_crystalline_bee/breed_iron_bee.json",
                    "bee_cage/quartz_nest/catch_crystalline_bee/breed_iron_bee/breed_all_productive_bees.json"):
            self.assertIn("_comment", docs[rel], rel)
            self.assertIn('"nbt": "{type:\\"productivebees:iron\\"}"', json.dumps(docs[rel]))

    def test_matches_the_jar_when_present(self):
        try:
            jar = port.jar_path()
        except port.PortError:
            self.skipTest("productivebees JAR not on this machine")
        for rel, text in port.ported(jar).items():
            self.assertEqual((OUT / rel).read_text(encoding="utf-8"), text, rel)


class ConvertTest(unittest.TestCase):
    def doc(self, criteria):
        return {"parent": "minecraft:husbandry/safely_harvest_honey",
                "display": {"icon": {"item": "minecraft:stick"}, "title": {"translate": "t"},
                            "description": {"translate": "d"}, "frame": "task", "show_toast": True,
                            "announce_to_chat": True, "hidden": False},
                "criteria": criteria, "requirements": [list(criteria)]}

    def test_display_and_parent_are_dropped(self):
        out = port.convert(self.doc({"a": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"item": "ns:i"}]}}}), "x")
        self.assertNotIn("display", out)
        self.assertNotIn("parent", out)
        self.assertEqual(out["requirements"], [["a"]])

    def test_tag_and_item_filters(self):
        out = port.convert(self.doc({"a": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"tag": "ns:t"}, {"item": "ns:i"}, {"items": ["ns:j"]}]}}}), "x")
        self.assertEqual(out["criteria"]["a"]["conditions"]["items"],
                         [{"items": "#ns:t"}, {"items": "ns:i"}, {"items": ["ns:j"]}])

    def test_state_becomes_string_properties(self):
        out = port.convert(self.doc({"a": {"trigger": "minecraft:placed_block", "conditions": {"location": [
            {"condition": "minecraft:block_state_property", "block": "ns:b", "state": {"on": True}}]}}}), "x")
        cond = out["criteria"]["a"]["conditions"]["location"][0]
        self.assertEqual(cond["properties"], {"on": "true"})
        self.assertNotIn("state", cond)

    def test_bee_triggers(self):
        out = port.convert(self.doc({
            "a": {"trigger": "productivebees:catch_bee", "conditions": {"beeName": "any"}},
            "b": {"trigger": "productivebees:saddle_bee"}}), "x")
        self.assertEqual(out["criteria"]["a"]["conditions"], {"bee": "any"})
        self.assertEqual(out["criteria"]["b"]["conditions"], {"bee": "any"})

    def test_unknown_constructs_fail_loudly(self):
        with self.assertRaises(port.PortError):
            port.convert(self.doc({"a": {"trigger": "minecraft:tick", "conditions": {"weird": 1}}}), "x")
        with self.assertRaises(port.PortError):
            port.convert(dict(self.doc({"a": {"trigger": "minecraft:tick"}}), rewards={}), "x")


if __name__ == "__main__":
    unittest.main()
