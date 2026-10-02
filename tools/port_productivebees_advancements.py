"""Write ENTRELUMEN's own advancements for the Productive Bees milestones the bee quests wait for.

productivebees-1.21.1-13.13.5.jar ships its 22 husbandry advancements under
data/productivebees/advancements/ (plural, the pre-1.21 folder). 1.21 only reads advancement/
(singular), so none of them exist at runtime: FTB Quests' advancement task is false for a missing
advancement, and the bee quests behind them never complete (F2).

Productive Bees is All Rights Reserved, so its files are not copied into this public repository
(tools/check_loot_tables.py --copies; THIRD_PARTY_NOTICES.md). Instead, for each advancement a bee quest
names as entrelumen:productivebees/<path>, this tool writes ENTRELUMEN's own file to

    pack/kubejs/data/entrelumen/advancement/productivebees/<path>.json

with only what makes it fire: the mod's triggers and their conditions, read from the JAR and put in the
1.21 shape, and the requirements. No display (no title, description, icon, frame or tab: the quest
says what to do, in its own words and task title), no parent, a neoforge:mod_loaded condition on
productivebees. The mod's own husbandry tab stays as the mod ships it.

What the 1.21 shape changes in the criteria:

  * item filters: {"tag": "ns:t"} becomes {"items": "#ns:t"}, {"item": X} becomes {"items": X}.
  * block predicates: {"tag": "ns:t"} becomes {"blocks": "#ns:t"}.
  * block_state_property: the 1.19 "state" key becomes the loot condition's "properties", with
    string values ("true"), as the 1.21 codec requires.
  * productivebees:* trigger conditions: the 1.21 codecs name the field "bee" (and require it),
    where the 1.19 files said "beeName". productivebees:saddle_bee carries no conditions in the
    JAR; its codec also requires "bee", so it gets {"bee": "any"}.
  * Kept as they are (already 1.21): the location lists of item_used_on_block and placed_block and
    the bred_animals "child" conditions.
  * The configurable_bee entity NBT predicates are already the 1.21 string form
    ("nbt": "{type:\\"productivebees:iron\\"}"). ConfigurableBee.addAdditionalSaveData writes the
    key "type" as the bee id string, which is what they match; a boot must still confirm it
    against a real iron bee. The files that use them carry a "_comment" saying so.

    python tools/port_productivebees_advancements.py           # write the files
    python tools/port_productivebees_advancements.py --check   # verify the committed files
    python tools/port_productivebees_advancements.py --jar PATH

Output is deterministic and the tool re-runnable.
"""
from __future__ import annotations

import argparse
import copy
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_PREFIX = "data/productivebees/advancements/husbandry/"
OUT_DIR = ROOT / "pack/kubejs/data/entrelumen/advancement/productivebees"
NAMESPACE = "entrelumen:productivebees/"
MOD_LOADED = [{"type": "neoforge:mod_loaded", "modid": "productivebees"}]
JAR_NAME = "productivebees-1.21.1-13.13.5.jar"
FALLBACK_MODS = Path("E:/curseforge/Instances/ENTRELUMEN/mods")
EXPECTED_FILES = 22
NBT_COMMENT = (
    "ENTRELUMEN's own trigger for a bee quest (tools/port_productivebees_advancements.py). The "
    "configurable_bee 'nbt' predicates are the 1.21 string form and match the 'type' key "
    "ConfigurableBee writes to its saved data; confirm at boot with a real bee of that type."
)

ITEM_PREDICATE_KEYS = {"items", "count", "components", "predicates"}
BLOCK_PREDICATE_KEYS = {"blocks", "nbt", "state"}
TRIGGERS_WITH_BEE = {
    "productivebees:catch_bee",
    "productivebees:calm_bee",
    "productivebees:fish_bee",
    "productivebees:saddle_bee",
}


class PortError(Exception):
    pass


def jar_path(explicit: str | None = None) -> Path:
    if explicit:
        return Path(explicit)
    local = ROOT / "catalog/local-paths.json"
    if local.is_file():
        found = json.loads(local.read_text(encoding="utf-8")).get(JAR_NAME)
        if found and Path(found).is_file():
            return Path(found)
    fallback = FALLBACK_MODS / JAR_NAME
    if fallback.is_file():
        return fallback
    raise PortError(f"{JAR_NAME} not found (catalog/local-paths.json, {FALLBACK_MODS}); pass --jar")


def item_filter(f: dict, where: str) -> dict:
    """A 1.19 item predicate as a 1.21 one."""
    out = {}
    for key, value in f.items():
        if key == "tag":
            out["items"] = "#" + value
        elif key == "item":
            out["items"] = value
        elif key in ITEM_PREDICATE_KEYS:
            out[key] = value
        else:
            raise PortError(f"{where}: unknown item filter key {key!r}")
    if "items" not in out:
        raise PortError(f"{where}: item filter without items")
    return out


def block_predicate(p: dict, where: str) -> dict:
    out = {}
    for key, value in p.items():
        if key == "tag":
            out["blocks"] = "#" + value
        elif key == "block":
            out["blocks"] = value
        elif key in BLOCK_PREDICATE_KEYS:
            out[key] = value
        else:
            raise PortError(f"{where}: unknown block predicate key {key!r}")
    return out


def string_state(state: dict) -> dict:
    out = {}
    for name, value in state.items():
        if isinstance(value, bool):
            out[name] = "true" if value else "false"
        elif isinstance(value, (str, int)):
            out[name] = str(value)
        else:
            out[name] = value  # a {"min","max"} range
    return out


def loot_condition(c: dict, where: str) -> dict:
    kind = c.get("condition")
    out = dict(c)
    if kind == "minecraft:location_check":
        pred = dict(c["predicate"])
        if "block" in pred:
            pred["block"] = block_predicate(pred["block"], where)
        out["predicate"] = pred
    elif kind == "minecraft:match_tool":
        out["predicate"] = item_filter(c["predicate"], where)
    elif kind == "minecraft:block_state_property":
        if "state" in out:
            out["properties"] = string_state(out.pop("state"))
        elif "properties" in out:
            out["properties"] = string_state(out["properties"])
    elif kind == "minecraft:entity_properties":
        pass  # type/nbt string predicates are already 1.21
    else:
        raise PortError(f"{where}: unknown loot condition {kind!r}")
    return out


def criterion(name: str, crit: dict, where: str) -> dict:
    trigger = crit["trigger"]
    cond = copy.deepcopy(crit.get("conditions", {}))
    out_cond = {}
    for key, value in cond.items():
        w = f"{where}.{name}.{key}"
        if key == "items":
            out_cond[key] = [item_filter(f, w) for f in value]
        elif key == "item":
            out_cond[key] = item_filter(value, w)
        elif key == "location" and isinstance(value, list):
            out_cond[key] = [loot_condition(c, w) for c in value]
        elif key == "child" and isinstance(value, list):
            out_cond[key] = [loot_condition(c, w) for c in value]
        elif key == "beeName" and trigger in TRIGGERS_WITH_BEE:
            out_cond["bee"] = value
        else:
            raise PortError(f"{w}: unknown condition {key!r} on {trigger}")
    if trigger in TRIGGERS_WITH_BEE and "bee" not in out_cond:
        out_cond["bee"] = "any"
    out = {"trigger": trigger}
    if out_cond or trigger in TRIGGERS_WITH_BEE:
        out["conditions"] = out_cond
    return out


def convert(doc: dict, where: str) -> dict:
    """ENTRELUMEN's own file for one of the mod's advancements: its criteria in the 1.21 shape and its
    requirements, loaded only with Productive Bees; nothing of its display or tree."""
    unknown = set(doc) - {"parent", "display", "criteria", "requirements"}
    if unknown:
        raise PortError(f"{where}: unknown top-level keys {sorted(unknown)}")
    out = {"neoforge:conditions": copy.deepcopy(MOD_LOADED),
           "criteria": {n: criterion(n, c, where) for n, c in doc["criteria"].items()},
           "requirements": doc["requirements"],
           "sends_telemetry_event": False}
    if any("nbt" in json.dumps(c) for c in out["criteria"].values()):
        out = {"_comment": NBT_COMMENT, **out}
    return out


def used_advancements(root: Path = ROOT) -> set[str]:
    """The <path>s the bee quests name as entrelumen:productivebees/<path>."""
    used = set()
    for sector in sorted((root / "content/sectors").glob("*.json")):
        used |= set(re.findall(r'"advancement": "' + re.escape(NAMESPACE) + r'([^"#]+)"',
                               sector.read_text(encoding="utf-8")))
    return used


def render(doc: dict) -> str:
    return json.dumps(doc, indent=2, ensure_ascii=False) + "\n"


def ported(jar: Path, used: set[str] | None = None) -> dict[str, str]:
    """Relative path (<path>.json) -> ENTRELUMEN's file, for every advancement a bee quest names; read from the JAR only."""
    used = used_advancements() if used is None else used
    result = {}
    with zipfile.ZipFile(jar) as z:
        names = sorted(n for n in z.namelist() if n.startswith(SRC_PREFIX) and n.endswith(".json"))
        if len(names) != EXPECTED_FILES:
            raise PortError(f"{jar.name}: expected {EXPECTED_FILES} husbandry advancements, found {len(names)}")
        known = {name[len(SRC_PREFIX):-len(".json")]: name for name in names}
        missing = sorted(used - set(known))
        if missing:
            raise PortError(f"{jar.name}: no husbandry advancement for {missing}")
        for path in sorted(used):
            result[path + ".json"] = render(convert(json.loads(z.read(known[path])), path))
    return result


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--check", action="store_true", help="verify the committed files instead of writing")
    ap.add_argument("--jar", help=f"path to {JAR_NAME}")
    args = ap.parse_args(argv)
    try:
        files = ported(jar_path(args.jar))
    except PortError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    if args.check:
        bad = [rel for rel, text in files.items()
               if not (OUT_DIR / rel).is_file() or (OUT_DIR / rel).read_text(encoding="utf-8") != text]
        extra = sorted(str(p.relative_to(OUT_DIR)).replace("\\", "/")
                       for p in OUT_DIR.rglob("*.json") if str(p.relative_to(OUT_DIR)).replace("\\", "/") not in files)
        for rel in bad:
            print(f"stale or missing: {rel}", file=sys.stderr)
        for rel in extra:
            print(f"unexpected: {rel}", file=sys.stderr)
        if bad or extra:
            return 1
        print(f"ok: {len(files)} advancements match the JAR and the bee quests")
        return 0
    for rel, text in files.items():
        target = OUT_DIR / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")
    print(f"wrote {len(files)} advancements to {OUT_DIR.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
