"""Repair five stale native Malum 1.8.2 recipe fields without changing mechanics."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
COLORS = ("gold", "purple", "red", "white")
PREFIX = "malum/spirit_repair/occultism/"

def generate(write=False, log=None, audit_token=None):
    lock = json.loads((ROOT / "catalog/curated.json").read_text(encoding="utf-8"))
    paths = json.loads((ROOT / "catalog/local-paths.json").read_text(encoding="utf-8"))
    entry = next(e for e in lock["mods"] if any(m["id"] == "malum" for m in e["metadata"]["mods"]))
    source = Path(paths[entry["filename"]])
    if hashlib.sha256(source.read_bytes()).hexdigest() != entry["sha256"]:
        raise ValueError("Malum JAR differs from lock")
    errors = []
    inventory = []
    expected = {}
    with zipfile.ZipFile(source) as jar:
        schema = json.loads(jar.read("data/malum/kubejs/recipe_schema/spirit_repair.json"))
        assert "validItems" in [k["name"] for k in schema["keys"]]
        for relative in [PREFIX + color + "_chalk.json" for color in COLORS] + ["create/milling/grim_talc.json"]:
            original = json.loads(jar.read("data/malum/recipe/" + relative))
            milling = relative == "create/milling/grim_talc.json"
            color = Path(relative).stem.removesuffix("_chalk")
            if not milling and (original.get("inputs") != ["occultism:chalk_" + color] or "validItems" in original):
                raise ValueError("Upstream changed; review obsolete patch: " + relative)
            fixed = json.loads(json.dumps(original))
            if milling:
                assert original["type"] == "create:milling"
                assert len(original["results"]) == 3
                for result in fixed["results"]:
                    if "id" in result or "item" not in result:
                        raise ValueError("Upstream milling result changed")
                    result["id"] = result.pop("item")
            else:
                assert original["type"] == "malum:spirit_repair"
                fixed["validItems"] = fixed.pop("inputs")
            restored = json.loads(json.dumps(fixed))
            if milling:
                for result in restored["results"]:
                    result["item"] = result.pop("id")
            else:
                restored["inputs"] = restored.pop("validItems")
            assert restored == original  # Every mechanic and condition is unchanged.
            target = ROOT / "pack/kubejs/data/malum/recipe" / relative
            text = json.dumps(fixed, indent=2) + "\n"
            expected["malum:" + relative.removesuffix(".json")] = {k: v for k, v in fixed.items() if k not in ("type", "neoforge:conditions")}
            inventory.append(("malum:" + relative.removesuffix(".json"), hashlib.sha256(jar.read("data/malum/recipe/" + relative)).hexdigest(), hashlib.sha256(text.encode()).hexdigest()))
            if write:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding="utf-8")
            if not target.exists() or target.read_text(encoding="utf-8") != text:
                errors.append(str(target.relative_to(ROOT)))
    if errors:
        raise ValueError("Missing or stale generated recipes: " + ", ".join(errors))
    audit = ROOT / "pack/kubejs/server_scripts/entrelumen_malum_audit.js"
    if audit_token:
        import re
        if not re.fullmatch(r"[A-Za-z0-9_-]{8,80}", audit_token):
            raise ValueError("Use a unique audit token of 8-80 safe characters")
        signature = hashlib.sha256(json.dumps(expected, sort_keys=True).encode()).hexdigest()
        script = "// Generated read-only RecipeManager audit; never modifies recipes.\n"
        script += "const elMalumAuditToken = " + json.dumps(audit_token) + ";\n"
        script += "const elMalumAuditSignature = " + json.dumps(signature) + ";\n"
        script += "const elMalumAuditExpected = " + json.dumps(expected) + ";\n"
        script += MALUM_AUDIT_JS
        if write:
            audit.write_text(script, encoding="utf-8")
        elif not audit.exists() or audit.read_text(encoding="utf-8") != script:
            raise ValueError("Audit script stale: generate/install a fresh token before reload")
    if log:
        if not audit_token:
            raise ValueError("--log requires the fresh --audit-token installed before this reload")
        check_receipt(Path(log), audit_token, signature, expected)
    for row in inventory:
        print(" | ".join(row))
    print("PASS: five exact recipe IDs; source hash, native schema and all non-key semantics preserved.")

MALUM_AUDIT_JS = r"""
ServerEvents.afterRecipes(event => {
  const ops = Java.loadClass('com.mojang.serialization.JsonOps').INSTANCE;
  const run = String(Date.now());
  const emit = row => {
    row.token = elMalumAuditToken; row.signature = elMalumAuditSignature; row.run = run;
    console.info('[ENTRELUMEN_MALUM_AUDIT] ' + JSON.stringify(row));
  };
  emit({kind: 'begin'});
  let failures = 0;
  Object.keys(elMalumAuditExpected).forEach(id => {
    let count = 0;
    try {
      event.forEachRecipe({id: id}, holder => {
        count++;
        const recipe = holder.getRecipe();
        const encoded = holder.getSerializer().codec().codec().encodeStart(ops, recipe).getOrThrow();
        emit({kind: 'recipe', id: id, actual: JSON.parse(String(encoded))});
      });
      if (count !== 1) { failures++; emit({kind: 'error', id: id, count: count}); }
    } catch (error) { failures++; emit({kind: 'error', id: id, message: String(error)}); }
  });
  emit({kind: 'summary', count: 5, failures: failures});
});
"""


def check_receipt(path, token, signature, expected):
    marker = "[ENTRELUMEN_MALUM_AUDIT] "
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    records = [(i, json.loads(line.split(marker, 1)[1])) for i, line in enumerate(lines) if marker in line]
    starts = [(i, r) for i, r in records if r.get("kind") == "begin"]
    if not starts:
        raise ValueError("No actual RecipeManager audit receipt")
    start, begin = starts[-1]  # Never fall back to an older successful run.
    if begin.get("token") != token or begin.get("signature") != signature:
        raise ValueError("Latest audit is stale or has a different installed payload")
    rows = [r for i, r in records if i >= start]
    if any(r.get("run") != begin["run"] or r.get("token") != token or r.get("signature") != signature for r in rows):
        raise ValueError("Mixed audit runs")
    if rows[-1].get("kind") != "summary" or rows[-1].get("failures") != 0:
        raise ValueError("Latest audit incomplete or failed")
    recipes = [r for r in rows if r.get("kind") == "recipe"]
    if len(recipes) != 5 or {r["id"] for r in recipes} != set(expected):
        raise ValueError("Five exact live recipes were not observed")
    # Codec may emit optional defaults; every expected semantic field must match.
    for row in recipes:
        for key, value in expected[row["id"]].items():
            actual = row["actual"].get(key)
            if key == "repairMaterial":
                value = dict(value, count=value.get("count", 1))
                actual = dict(actual or {}, count=(actual or {}).get("count", 1))
            if key == "results":
                value = [dict(v, count=v.get("count", 1), chance=v.get("chance", 1)) for v in value]
                actual = [dict(v, count=v.get("count", 1), chance=v.get("chance", 1)) for v in (actual or [])]
            if actual != value:
                raise ValueError(f"Live field mismatch: {row['id']} {key}")
    # A later reload start invalidates a receipt until that reload produces its own audit.
    import re
    if any(re.search(r"Reloading ResourceManager|Reloading resources|Reloading!", line) for line in lines[records[-1][0] + 1:]):
        raise ValueError("A newer reload started after the receipt")
    print("PASS live RecipeManager: five IDs and all expected semantic fields; latest run/token verified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true", help="Check deterministic outputs (default)")
    parser.add_argument("--log", type=Path, help="Fresh post-fix log with RecipeManager receipt")
    parser.add_argument("--audit-token", help="Unique token generated and installed before reload")
    args = parser.parse_args()
    generate(args.write, args.log, args.audit_token)
