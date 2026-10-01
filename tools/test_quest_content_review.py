"""Contracts of the quest content fixed after the 1 October 2026 adversarial review (batch quest-content: F3, F7,
F23, F27, F39, F40, F43, F49-F51, F53, F56, F60, F61, F72, F73, F75, F81-F84). Source-only: it reads content/
and never the generated book, so it needs neither Gradle nor the pinned JARs."""
from __future__ import annotations

import glob
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "content"


def load(rel: str) -> dict:
    return json.loads((CONTENT / rel).read_text(encoding="utf-8"))


def quest(rel: str, key: str) -> dict:
    found = [q for q in load(rel)["quests"] if q["key"] == key]
    assert len(found) == 1, f"{rel}: {key}"
    return found[0]


def text(q: dict, lang: str = "en_us") -> str:
    return " ".join(q[lang]["text"])


def tasks(q: dict) -> list[dict]:
    return q.get("tasks") or [q["task"]]


def es_strings(node):
    """Every string under an "es_es" key, at any depth."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "es_es":
                yield from strings(v)
            else:
                yield from es_strings(v)
    elif isinstance(node, list):
        for v in node:
            yield from es_strings(v)


def strings(node):
    if isinstance(node, str):
        yield node
    elif isinstance(node, dict):
        for v in node.values():
            yield from strings(v)
    elif isinstance(node, list):
        for v in node:
            yield from strings(v)


class Softlocks(unittest.TestCase):
    def test_deep_aether_capstone_credits_the_advancement_not_the_kill(self):
        t = tasks(quest("sectors/sector_aether_deep.json", "deep_aether_brass"))[0]
        self.assertEqual(t["type"], "advancement")
        self.assertEqual(t["advancement"], "deep_aether:brass_dungeon")
        self.assertNotIn("entity", t)

    def test_first_signal_text_matches_the_infuser_and_one_frame_reward(self):
        first = quest("first_hour.json", "signal")
        for lang, infuser in (("en_us", "metallurgic_infuser"), ("es_es", "metallurgic_infuser")):
            body = text(first, lang)
            self.assertIn(f"item:mekanism:{infuser}", body)
            self.assertNotRegex(body, r"two of Terra|dos \[item:entrelumen:calibration_frame")
        self.assertIn("Terra left you", text(quest("act_two.json", "crafts_precision")))
        self.assertIn("te dejó Terra", text(quest("act_two.json", "crafts_precision"), "es_es"))
        atlas = quest("guides/guide_entrelumen_atlas.json", "entrelumen_atlas_act2")
        self.assertIn("gives one, plus the metallurgic infuser", text(atlas))
        self.assertIn("da uno, más el infusor metalúrgico", text(atlas, "es_es"))

    def test_act_three_counts_match_their_recipes(self):
        want = {"exchange_processors": 2, "exchange_palis": 2, "exchange_circuits": 2, "exchange_steel": 2,
                "exchange_menril": 2, "exchange_chips": 2, "exchange_modules": 6}
        for key, count in want.items():
            self.assertEqual(quest("act_three.json", key)["count"], count, key)
        reserve = quest("act_three.json", "exchange_living_reserve")
        self.assertIn("Nature Module takes a second core", text(reserve))
        self.assertIn("segundo núcleo", text(reserve, "es_es"))
        self.assertIn("eight additional", text(quest("act_three.json", "exchange_prudentium")))

    def test_terra_power_errand_says_off_hand(self):
        power = quest("act_six.json", "solsticio_power")
        self.assertIn("off hand", text(power))
        self.assertIn("mano secundaria", text(power, "es_es"))


class KeysAndNames(unittest.TestCase):
    def test_no_emi_config_binds_are_written_as_key_tokens(self):
        for path in glob.glob(str(CONTENT / "**" / "*.json"), recursive=True):
            self.assertNotIn("key.emi.", Path(path).read_text(encoding="utf-8"), path)

    def test_carry_on_quest_names_sneak(self):
        chest = quest("sectors/sector_travel.json", "carry_chest")
        for lang in ("en_us", "es_es"):
            self.assertIn("[key:key.sneak]", text(chest, lang))
            self.assertNotIn("key.carry.desc", text(chest, lang))
        self.assertTrue(any("CarryOnKeybinds" in s for s in chest["sources"]))
        self.assertTrue(any("CarryOnCommonClient" in s for s in chest["sources"]))

    def test_construction_stick_keys_are_not_called_current(self):
        keys = quest("sectors/sector_gadgets.json", "cs_keys")
        self.assertNotRegex(text(keys), r"\bnow\b")
        self.assertNotRegex(text(keys, "es_es"), r"\bahora\b")

    def test_jetpack_sprint_bonus_starts_at_copper(self):
        fly = quest("sectors/sector_gadgets.json", "gd_ij_fly")
        self.assertIn("from copper up", text(fly))
        self.assertIn("desde el de cobre", text(fly, "es_es"))

    def test_controls_guide_examples_are_current(self):
        mods = quest("guides/guide_qol_controls.json", "qol_controls_tip_modifiers")
        self.assertNotIn("ender satchel", text(mods))
        self.assertNotIn("morral del ender", text(mods, "es_es"))
        self.assertIn("hotbar slot 9", text(mods))
        self.assertIn("share a key", text(mods))
        self.assertIn("comparten tecla", text(mods, "es_es"))
        apoth = quest("guides/guide_qol_controls.json", "qol_controls_apoth_keys")
        self.assertNotIn("Three Apotheosis keys", text(apoth))
        self.assertIn("Two Apotheosis keys moved off Ctrl", text(apoth))
        layout = quest("guides/guide_qol_controls.json", "qol_controls_tip_layout")
        self.assertNotIn("two keys", text(layout))
        self.assertIn("backslash", text(layout))
        self.assertIn("barra invertida", text(layout, "es_es"))

    def test_spanish_book_says_nivel_de_mundo(self):
        files = ["guides/guide_entrelumen_world_tiers.json", "guides/guide_qol_controls.json",
                 "guides/guide_qol_teams.json", "guides/guide_entrelumen_luminous.json", "sectors/sector_gear.json"]
        for rel in files:
            for s in es_strings(load(rel)):
                self.assertNotIn("World Tier", s, rel)
        self.assertEqual(load("guides/guide_entrelumen_world_tiers.json")["title"]["es_es"],
                         "Niveles de Mundo: decide la historia")
        quirk = text(quest("guides/guide_entrelumen_world_tiers.json", "entrelumen_world_tiers_requires"), "es_es")
        self.assertIn("Requiere Nivel de Mundo", quirk)

    def test_hnn_star_tip_names_the_shard_alternative(self):
        tip = quest("sectors/sector_hnn.json", "hnn_tip_star")
        self.assertIn("item:entrelumen:sour_light_shard", text(tip))
        self.assertIn("64", text(tip))
        self.assertIn("item:entrelumen:sour_light_shard", text(tip, "es_es"))

    def test_home_promise_excludes_solsticio_and_enves(self):
        home = text(quest("first_hour.json", "habitation"))
        self.assertIn("but Solsticio and the Envés", home)
        self.assertIn("salvo Solsticio y el Envés", text(quest("first_hour.json", "habitation"), "es_es"))


class TierAndParty(unittest.TestCase):
    def test_tier_quirk_sends_players_through_the_tutorial(self):
        q = quest("guides/guide_entrelumen_world_tiers.json", "entrelumen_world_tiers_requires")
        body = text(q)
        self.assertNotIn("No button to press", body)
        self.assertNotIn("disabled", body)
        self.assertIn("short tutorial", body)
        self.assertNotIn("No hay botón que apretar", text(q, "es_es"))
        self.assertIn("tutorial", text(q, "es_es"))

    def test_joining_a_party_does_not_promise_clean_histories(self):
        q = quest("guides/guide_qol_teams.json", "qol_teams_join_rules")
        self.assertNotIn("never merges", text(q))
        self.assertIn("Atlas milestones are the party's", text(q))
        self.assertIn("claimed rewards", text(q))
        self.assertNotIn("nunca fusiona", text(q, "es_es"))


class Bounties(unittest.TestCase):
    RARE = {"minecraft:netherite_scrap", "minecraft:netherite_ingot", "minecraft:netherite_block", "minecraft:emerald",
            "minecraft:diamond", "minecraft:nether_star", "draconicevolution:dragon_heart",
            "draconicevolution:awakened_draconium_ingot", "draconicevolution:awakened_draconium_block",
            "reliquary:nebulous_heart", "artifacts:eternal_steak", "artifacts:everlasting_beef",
            "artifacts:umbrella", "artifacts:chorus_totem", "artifacts:crystal_heart"}
    TIER_4_5 = {"platinum", "iridium", "draconium", "uraninite", "end_steel", "vibrant_alloy", "niotic_crystal",
                "spirited_crystal", "pulsating_alloy", "soularium", "dark_steel"}

    def bounties(self):
        for path in sorted(glob.glob(str(CONTENT / "sectors" / "sector_*.json"))):
            data = json.loads(Path(path).read_text(encoding="utf-8"))
            for q in data["quests"]:
                if q["role"] == "bounty":
                    yield Path(path).name, q

    def test_no_bounty_consumes_a_rare_item(self):
        seen = 0
        for name, q in self.bounties():
            for t in tasks(q):
                if not t.get("consume"):
                    continue
                seen += 1
                for item in ([t["item"]] if "item" in t else t["any"]):
                    self.assertNotIn(item, self.RARE, f"{name}:{q['key']}")
        self.assertGreater(seen, 10)

    def test_any_of_bounties_never_use_a_whole_tag_of_rare_things(self):
        for name, q in self.bounties():
            for t in tasks(q):
                for entry in t.get("any", []):
                    self.assertNotEqual(entry, "#artifacts:artifacts", f"{name}:{q['key']}")

    def test_artifacts_bounty_lists_wearables_and_warns_about_the_hotbar(self):
        q = quest("sectors/sector_artifacts.json", "art_bounty")
        entries = tasks(q)[0]["any"]
        self.assertEqual(len(entries), 43)
        for gone in ("eternal_steak", "everlasting_beef", "umbrella", "chorus_totem", "crystal_heart"):
            self.assertNotIn(f"artifacts:{gone}", entries)
        self.assertIn("hotbar first", text(q))
        self.assertIn("empezando por la barra", text(q, "es_es"))

    def test_greenhouse_bounty_has_no_top_tier_essences(self):
        entries = tasks(quest("sectors/sector_ma_greenhouse.json", "ma_gh_bounty"))[0]["any"]
        self.assertEqual(len(entries), 40)
        for name in self.TIER_4_5:
            self.assertNotIn(f"mysticalagriculture:{name}_essence", entries)

    def test_reliquary_bounty_drops_the_nebulous_heart(self):
        entries = tasks(quest("sectors/sector_reliquary.json", "reliq_bounty"))[0]["any"]
        self.assertNotIn("reliquary:nebulous_heart", entries)
        self.assertEqual(len(entries), 12)

    def test_early_crate_bounties_ask_cheap_surplus(self):
        cases = {("sector_gear.json", "gear_bounty"): [("minecraft:leather", 16)],
                 ("sector_structures.json", "struct_bounty"): [("minecraft:bone", 32)],
                 ("sector_draconic_chaos.json", "de_bounty_awakened"): [("draconicevolution:draconium_ingot", 16)],
                 ("sector_ae2_addons.json", "aeadd_bounty"): [("extendedae:concurrent_processor", 2),
                                                               ("megacells:accumulation_processor", 2)]}
        for (rel, key), want in cases.items():
            got = [(t["item"], t["count"]) for t in tasks(quest("sectors/" + rel, key))]
            self.assertEqual(got, want, key)
            for t in tasks(quest("sectors/" + rel, key)):
                self.assertTrue(t["consume"], key)


class SectorActs(unittest.TestCase):
    def act(self, name: str) -> str:
        return load(f"sectors/sector_{name}.json")["act"]

    def test_bumblezone_is_reachable_from_act_one_so_it_is_act_two(self):
        self.assertEqual(self.act("bumblezone_hive"), "II")
        self.assertEqual(self.act("bumblezone_court"), "II")

    def test_deep_aether_is_never_later_than_the_aether_it_hangs_from(self):
        self.assertEqual(self.act("aether_deep"), self.act("aether"))

    def test_the_relabelling_reasons_are_written_down(self):
        readme = (CONTENT / "sectors" / "README.md").read_text(encoding="utf-8")
        for name in ("bumblezone_hive", "aether_deep", "starlight_night", "deep_worlds"):
            self.assertIn(f"`{name}`", readme)


class Dependencies(unittest.TestCase):
    def test_guides_never_make_a_required_quest_wait_for_an_optional_one(self):
        for path in sorted(glob.glob(str(CONTENT / "guides" / "guide_*.json"))):
            data = json.loads(Path(path).read_text(encoding="utf-8"))
            by_key = {q["key"]: q for q in data["quests"]}
            for q in data["quests"]:
                if q.get("optional") or q.get("role") == "decor":
                    continue
                for dep in q["deps"]:
                    other = by_key.get(dep)
                    self.assertFalse(other and other.get("optional"), f"{Path(path).name}: {q['key']} <- {dep}")

    def test_the_backpack_quest_does_not_wait_for_the_tome(self):
        deps = quest("guides/guide_qol_inventory.json", "qol_inventory_backpack_link")["deps"]
        self.assertNotIn("qol_inventory_tome", deps)

    def test_the_nether_star_quest_is_required_because_every_luminous_piece_takes_one(self):
        star = quest("guides/guide_entrelumen_luminous.json", "entrelumen_luminous_boss_drops")
        self.assertFalse(star.get("optional"))
        close = quest("guides/guide_entrelumen_luminous.json", "entrelumen_luminous_close")
        self.assertIn("entrelumen_luminous_boss_drops", close["deps"])


class CarryAllAtOnce(unittest.TestCase):
    RINGS = {("sector_neovitae_altar.json", "nv_tier1"): 8, ("sector_neovitae_altar.json", "nv_tier2"): 16,
             ("sector_neovitae_rites.json", "nv_tier3"): 28, ("sector_neovitae_rites.json", "nv_tier4"): 48,
             ("sector_neovitae_rites.json", "nv_tier5"): 72}

    def test_neo_vitae_rings_count_any_altar_rune_per_ring(self):
        for (rel, key), count in self.RINGS.items():
            q = quest("sectors/" + rel, key)
            runes = [t for t in tasks(q) if "any" in t]
            self.assertEqual(len(runes), 1, key)
            self.assertEqual(runes[0]["any"], ["#neovitae:altar/runes"], key)
            self.assertEqual(runes[0]["count"], count, key)
            self.assertIn("icon", runes[0])
            self.assertIn("this ring only", text(q), key)
            self.assertIn("sólo de este anillo", text(q, "es_es"), key)

    def test_build_leads_say_what_you_carry(self):
        carry = {("sector_rs.json", "rs_mastery"), ("sector_ae2_automation.json", "aea_mastery"),
                 ("sector_computercraft.json", "cc_mastery"), ("sector_draconic_wyvern.json", "de_core_t3"),
                 ("sector_draconic_wyvern.json", "de_core_t4"), ("sector_draconic_wyvern.json", "de_core_t5"),
                 ("sector_draconic_wyvern.json", "de_core_t6"), ("sector_draconic_wyvern.json", "de_core_t7"),
                 ("sector_draconic_chaos.json", "de_core_t8"), ("sector_mekanism_energy.json", "mke_boiler"),
                 ("sector_mekanism_energy.json", "mke_sps"), ("sector_oritech_core.json", "ori_reactor"),
                 ("sector_oritech_core.json", "ori_accelerator")} | set(self.RINGS)
        for rel, key in carry:
            q = quest("sectors/" + rel, key)
            self.assertRegex(text(q), r"\bCarry\b", key)
            self.assertRegex(text(q, "es_es"), r"\bLlev[aá]\b", key)

    def test_hard_keys_stay_out_of_the_text(self):
        hard = re.compile(r"\b(F[1-9]|F1[0-2])\b|\b(Alt|Ctrl|Shift)\+")
        for path in glob.glob(str(CONTENT / "**" / "*.json"), recursive=True):
            data = json.loads(Path(path).read_text(encoding="utf-8"))
            if not isinstance(data, dict) or "quests" not in data:
                continue
            for q in data["quests"]:
                for lang in ("en_us", "es_es"):
                    if lang in q:
                        visible = re.sub(r"\[[a-z]+:[^\]]*\]", "", text(q, lang))
                        self.assertIsNone(hard.search(visible), f"{Path(path).name}:{q['key']}")


if __name__ == "__main__":
    unittest.main()
