"""Generate/check the 22 static integration recipes; never writes a live instance.

21 are shaped crafting recipes whose pattern draws the component (Elias's playtest of 24 September
2026, docs/design/recipe-design-rules.md): left-right symmetric, with every ENTRELUMEN item on the
vertical axis, the corners or the middle row, and each ingredient's count equal to the cells it fills,
so the design inputs and the Atlas delivery of the Ark modules stay equal to the grid. The calibration
frame (precision_bench) is a Mekanism metallurgic infusing recipe: it has no crafting recipe since
24 September 2026, the first two frames are the Act I reward of first_signal, and the infuser itself
needs a frame, the only gate allowed to use a component that its own acquisition needs
(docs/design/progression-functions.md)."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "content/integration-design.json"
TARGET = ROOT / "pack/kubejs/server_scripts/entrelumen_integration_recipes.js"
PROJECTS = ROOT / "companion/src/main/resources/data/entrelumen/campaign/projects.json"
ID = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
INFUSING = "mekanism:metallurgic_infusing"
SHAPED = "minecraft:crafting_shaped"
INFUSER = "mekanism:metallurgic_infuser"
# The only recipe that is not shapeless crafting: the frame, replicated by infusion.
INFUSED = {"entrelumen:integration/precision_bench": "entrelumen:calibration_frame"}
# Campaign frames a team receives before it can infuse: one builds the infuser, one is spare.
STORY_FRAMES = 2


def recipes_from(design):
    projects = design["projects"]
    if len(projects) != 22:
        raise ValueError("Expected 22 integration projects")
    rows, outputs, ids = [], {}, set()
    for project in projects:
        recipe, output = project["recipe"], project["output"]
        rid = recipe["id"]
        if not ID.fullmatch(rid) or not rid.startswith("entrelumen:integration/") or rid in ids:
            raise ValueError(f"Invalid/duplicate reserved recipe ID: {rid}")
        ids.add(rid)
        if recipe["type"] not in (SHAPED, INFUSING):
            raise ValueError(f"Unsupported recipe type (shapeless recipes are gone since the playtest): {rid}")
        if (recipe["type"] == INFUSING) != (rid in INFUSED):
            raise ValueError(f"Exactly {sorted(INFUSED)} is infused: {rid}")
        if set(project["title"]) != {"en_us", "es_es"} or not all(isinstance(v, str) and v.strip() for v in project["title"].values()):
            raise ValueError(f"Missing bilingual project title: {rid}")
        if not ID.fullmatch(output["id"]) or not output["id"].startswith("entrelumen:") or output["id"] in outputs:
            raise ValueError(f"Invalid/duplicate output: {rid}")
        if type(output["count"]) is not int or not 1 <= output["count"] <= 64:
            raise ValueError(f"Invalid yield: {rid}")
        ingredients = []
        for entry in recipe["inputs"]:
            if not ID.fullmatch(entry["id"]) or type(entry["count"]) is not int or not 1 <= entry["count"] <= 9:
                raise ValueError(f"Invalid ingredient: {rid}: {entry}")
            ingredients.extend([{"item": entry["id"]}] * entry["count"])
        outputs[output["id"]] = project
        if recipe["type"] == INFUSING:
            rows.append({"id": rid, "json": infusing(rid, recipe, output)})
            continue
        if not 1 <= len(ingredients) <= 9:
            raise ValueError(f"Crafting grid overflow/empty: {rid}")
        check_drawing(rid, recipe)
        check_delivery(rid, project)
        rows.append({"id": rid, "json": {"type": SHAPED, "category": "misc", "pattern": recipe["pattern"],
                                         "key": {letter: {"item": item} for letter, item in recipe["key"].items()},
                                         "result": {"id": output["id"], "count": output["count"]}}})
    visiting, visited = set(), set()
    def visit(item):
        if item in visiting:
            raise ValueError(f"Circular component dependency: {item}")
        if item in visited:
            return
        visiting.add(item)
        for entry in outputs[item]["recipe"]["inputs"]:
            if entry["id"] in outputs:
                visit(entry["id"])
        visiting.remove(item)
        visited.add(item)
    for item in outputs:
        visit(item)
    check_story_frames(outputs)
    return rows


def check_drawing(rid, recipe):
    """A 3x3 drawing: symmetric left to right, ENTRELUMEN items on the axis, corners or middle row, and
    each input's count equal to the cells it fills."""
    pattern, key = recipe.get("pattern"), recipe.get("key")
    if not isinstance(pattern, list) or not 1 <= len(pattern) <= 3 or len({len(line) for line in pattern}) != 1 \
            or not 1 <= len(pattern[0]) <= 3 or not isinstance(key, dict):
        raise ValueError(f"Invalid drawing: {rid}")
    used = {c for line in pattern for c in line if c != " "}
    if used != set(key) or not all(ID.fullmatch(v) for v in key.values()) or len(set(key.values())) != len(key):
        raise ValueError(f"Pattern and key differ: {rid}")
    cells = {}
    for line in pattern:
        for c in line:
            if c != " ":
                cells[key[c]] = cells.get(key[c], 0) + 1
    if cells != {entry["id"]: entry["count"] for entry in recipe["inputs"]}:
        raise ValueError(f"The drawing does not use exactly the design inputs: {rid}")
    if any(line != line[::-1] for line in pattern):
        raise ValueError(f"The drawing is not symmetric: {rid}")
    height, width = len(pattern), len(pattern[0])
    for r, line in enumerate(pattern):
        for c, symbol in enumerate(line):
            if symbol != " " and key[symbol].startswith("entrelumen:"):
                axis = width % 2 == 1 and c == width // 2
                corner = r in (0, height - 1) and c in (0, width - 1)
                middle = height % 2 == 1 and r == height // 2
                if not (axis or corner or middle):
                    raise ValueError(f"{key[symbol]} is off the axis, corners and middle row: {rid}")


def check_delivery(rid, project):
    """An Ark module's one-time construction delivery costs exactly its crafting recipe."""
    items = project.get("delivery", {}).get("items")
    if items is not None and items != {entry["id"]: entry["count"] for entry in project["recipe"]["inputs"]}:
        raise ValueError(f"Atlas delivery and crafting recipe differ: {rid}")


def infusing(rid, recipe, output):
    """Native Mekanism 10.7 metallurgic infusing JSON: one item stack plus one infusion chemical."""
    if INFUSED[rid] != output["id"] or recipe.get("machine") != INFUSER:
        raise ValueError(f"Infusion must make {INFUSED[rid]} in {INFUSER}: {rid}")
    if len(recipe["inputs"]) != 1 or any(entry["id"] in INFUSED.values() for entry in recipe["inputs"]):
        raise ValueError(f"Infusion takes exactly one item input, never an infused component: {rid}")
    chemical = recipe.get("chemical", {})
    if set(chemical) != {"tag", "amount"} or not ID.fullmatch(chemical["tag"]) \
            or type(chemical["amount"]) is not int or not 1 <= chemical["amount"] <= 1000:
        raise ValueError(f"Infusion needs one chemical tag and a positive amount: {rid}")
    (entry,) = recipe["inputs"]
    return {"type": INFUSING, "chemical_input": {"amount": chemical["amount"], "tag": chemical["tag"]},
            "item_input": {"count": entry["count"], "item": entry["id"]},
            "output": {"count": output["count"], "id": output["id"]}, "per_tick_usage": False}


def check_story_frames(outputs):
    """An infused component has no crafting recipe, so the campaign hands out the first ones.

    The infuser needs a frame (tools/generate_family_balance.py, functions family). With fewer than
    STORY_FRAMES campaign frames a team could deliver its only frame to precision_bench and never
    build the infuser."""
    projects = json.loads(PROJECTS.read_text(encoding="utf-8"))
    for item in INFUSED.values():
        if item not in outputs:
            raise ValueError(f"Infused component is not a project output: {item}")
        granted = sum(p.get("extraRewards", {}).get(item, 0) + (p.get("reward") == item)
                      for p in projects.values() if p["act"] <= outputs[item]["act"])
        if granted < STORY_FRAMES:
            raise ValueError(f"The campaign grants {granted} {item} by its act; {STORY_FRAMES} are needed")


def check_existing_ids(rows):
    reserved = {row["id"] for row in rows}
    for base in [ROOT / "companion/src/main/resources/data", ROOT / "pack/kubejs/data"]:
        if not base.exists():
            continue
        for path in base.glob("*/recipe/**/*.json"):
            rel = path.relative_to(base)
            rid = rel.parts[0] + ":" + "/".join(rel.parts[2:])[:-5]
            if rid in reserved:
                raise ValueError(f"Existing source recipe would be replaced: {rid} ({path})")


def render(rows):
    payload = json.dumps(rows, ensure_ascii=False, indent=2)
    signature = hashlib.sha256(payload.encode()).hexdigest()
    return "// Generated by tools/generate_integration_recipes.py --write; do not edit.\n// Static native recipes (21 shaped and symmetric, the frame by Mekanism infusion); no team, act, gifts, reward or inventory handlers.\nconst entrelumenIntegrationSignature = " + json.dumps(signature) + ";\nconst entrelumenIntegrationRecipes = " + payload + ";\n" + RUNTIME


RUNTIME = r"""
// Shaped rows name their result and key; the infusing row its output and item input.
function entrelumenIntegrationOutput(row) {
  return row.json.result ? row.json.result.id : row.json.output.id;
}
ServerEvents.recipes(event => {
  const errors = [];
  entrelumenIntegrationRecipes.forEach(row => {
    const inputs = row.json.key ? Object.keys(row.json.key).map(k => row.json.key[k].item) : [row.json.item_input.item];
    const ids = [entrelumenIntegrationOutput(row)].concat(inputs);
    ids.forEach(id => {
      if (!Item.exists(id)) errors.push({recipe: row.id, missingItem: id});
    });
    if (event.containsRecipe({id: row.id})) errors.push({recipe: row.id, collision: true});
  });
  if (errors.length) {
    console.error('[ENTRELUMEN_INTEGRATION] ' + JSON.stringify({status: 'failed-preflight', signature: entrelumenIntegrationSignature, added: 0, errors: errors}));
    throw new Error('ENTRELUMEN integration recipes preflight failed; see contextual errors. No integration recipes were added.');
  }
  entrelumenIntegrationRecipes.forEach(row => event.custom(row.json).id(row.id));
  console.info('[ENTRELUMEN_INTEGRATION] ' + JSON.stringify({status: 'registered', signature: entrelumenIntegrationSignature, recipes: entrelumenIntegrationRecipes.length}));
});

ServerEvents.afterRecipes(event => {
  const missing = [];
  entrelumenIntegrationRecipes.forEach(row => {
    if (event.countRecipes({id: row.id, output: entrelumenIntegrationOutput(row)}) !== 1) missing.push(row.id);
  });
  const receipt = {status: missing.length ? 'failed-loaded-check' : 'loaded', signature: entrelumenIntegrationSignature, checked: entrelumenIntegrationRecipes.length, missing: missing};
  if (missing.length) console.error('[ENTRELUMEN_INTEGRATION] ' + JSON.stringify(receipt));
  else console.info('[ENTRELUMEN_INTEGRATION] ' + JSON.stringify(receipt));
});
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    group = ap.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true")
    group.add_argument("--check", action="store_true")
    args = ap.parse_args()
    rows = recipes_from(json.loads(SOURCE.read_text(encoding="utf-8")))
    check_existing_ids(rows)
    expected = render(rows)
    if args.write:
        TARGET.write_text(expected, encoding="utf-8")
    elif not TARGET.exists() or TARGET.read_text(encoding="utf-8") != expected:
        raise ValueError("Generated script differs from source; run --write")
    print("PASS: 22 static recipes (21 shaped and symmetric, the frame by metallurgic infusing), drawings equal to the design inputs and Ark deliveries, valid yields, bilingual project titles, component DAG, two story frames, reserved IDs and source parity. Crafting runtime not tested.")


if __name__ == "__main__":
    main()
