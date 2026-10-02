"""Productive Bees husbandry advancements: the 1.21 port exists, resolves and is in 1.21 format."""
import json
from pathlib import Path
import re
import unittest

import port_productivebees_advancements as port

ROOT = port.ROOT
OUT = port.OUT_DIR
VANILLA_PARENTS = {"minecraft:husbandry/safely_harvest_honey", "minecraft:husbandry/fishy_business"}


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


class CommittedPortTest(unittest.TestCase):
    def test_all_22_files_in_the_singular_folder(self):
        docs = committed()
        self.assertEqual(len(docs), port.EXPECTED_FILES)
        self.assertFalse((OUT.parent.parent / "advancements").exists(), "the plural folder is never loaded")

    def test_every_quest_advancement_resolves(self):
        used = set()
        for sector in (ROOT / "content/sectors").glob("*.json"):
            used |= set(re.findall(r'"advancement": "productivebees:([^"]+)"', sector.read_text(encoding="utf-8")))
        self.assertGreaterEqual(len(used), 17)
        for adv in used:
            self.assertTrue((OUT.parent / f"{adv}.json").is_file(), f"quest advancement missing: {adv}")

    def test_parent_links_resolve(self):
        for rel, doc in committed().items():
            parent = doc.get("parent")
            if parent in VANILLA_PARENTS:
                continue
            self.assertTrue(parent and parent.startswith("productivebees:husbandry/"), f"{rel}: {parent}")
            self.assertTrue((OUT.parent / f"{parent.split(':')[1]}.json").is_file(), f"{rel}: parent {parent}")

    def test_1_21_format(self):
        for rel, doc in committed().items():
            icon = doc["display"]["icon"]
            self.assertIn("id", icon, rel)
            self.assertFalse({"item", "nbt", "type"} & set(icon), f"{rel}: legacy icon keys")
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

    def test_nbt_icons_and_comments(self):
        crystalline = committed()["bee_cage/quartz_nest/catch_crystalline_bee.json"]
        self.assertEqual(crystalline["display"]["icon"], {
            "id": "productivebees:configurable_honeycomb",
            "components": {"productivebees:bee_type": "productivebees:crystalline"}})
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
    def doc(self, criteria, icon=None):
        return {"parent": "minecraft:husbandry/safely_harvest_honey",
                "display": {"icon": icon or {"item": "minecraft:stick"}, "title": {"translate": "t"},
                            "description": {"translate": "d"}, "frame": "task", "show_toast": True,
                            "announce_to_chat": True, "hidden": False},
                "criteria": criteria, "requirements": [list(criteria)]}

    def test_tag_and_item_filters(self):
        out = port.convert(self.doc({"a": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"tag": "ns:t"}, {"item": "ns:i"}, {"items": ["ns:j"]}]}}}), "x")
        self.assertEqual(out["criteria"]["a"]["conditions"]["items"],
                         [{"items": "#ns:t"}, {"items": "ns:i"}, {"items": ["ns:j"]}])
        self.assertEqual(out["display"]["icon"], {"id": "minecraft:stick"})

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
            port.convert(self.doc({"a": {"trigger": "minecraft:tick"}}, {"item": "ns:i", "nbt": "{x:1}"}), "x")


if __name__ == "__main__":
    unittest.main()
