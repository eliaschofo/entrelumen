"""Print the native recipes that produce the given items, from every pinned JAR (catalog/local-paths.json).

usage: python tools/recipe_of.py <item-id> [<item-id> ...]

One line per recipe: result, recipe type, file, and its ingredients in short form (pedestal items,
reagent, inputs, key and pattern, Source cost...). Recipes the pack changes live in pack/kubejs and the
generators (tools/generate_family_balance.py...): check those too before writing a fact.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def short(x):
    if isinstance(x, dict):
        for k in ("item", "id"):
            if isinstance(x.get(k), str):
                return x[k].split(":")[-1] + (f"x{x['count']}" if x.get("count", 1) != 1 else "")
        if "tag" in x:
            return "#" + x["tag"]
        if "fluid" in x:
            return "fluid:" + str(x["fluid"]).split(":")[-1] + (f"x{x.get('amount')}" if x.get("amount") else "")
        if "ingredient" in x:
            return short(x["ingredient"])
        return "{" + ",".join(x) + "}"
    if isinstance(x, list):
        return "[" + "|".join(short(i) for i in x) + "]"
    return str(x)


def results(recipe):
    out = []
    for key in ("result", "results", "output", "outputs"):
        for i in recipe.get(key) if isinstance(recipe.get(key), list) else [recipe.get(key)]:
            if isinstance(i, str):
                out.append(i)
            elif isinstance(i, dict):
                v = i.get("id") or i.get("item") or (i.get("stack") or {}).get("id") or ""
                if isinstance(v, dict):
                    v = v.get("id") or v.get("item") or ""
                if isinstance(v, str):
                    out.append(v)
    return out


def main():
    wanted = set(sys.argv[1:])
    if not wanted:
        print(__doc__)
        return 2
    local = json.loads((ROOT / "catalog/local-paths.json").read_text(encoding="utf-8"))
    for jar in sorted(set(local.values())):
        try:
            z = zipfile.ZipFile(jar)
        except (OSError, zipfile.BadZipFile):
            continue
        for n in z.namelist():
            if not re.match(r"data/[^/]+/recipes?/.+\.json$", n):
                continue
            try:
                r = json.loads(z.read(n))
            except (json.JSONDecodeError, UnicodeDecodeError):
                continue
            if not isinstance(r, dict):
                continue
            hit = [x for x in results(r) if x in wanted]
            if not hit:
                continue
            parts = []
            for key in ("pedestalItems", "reagent", "input", "inputs", "ingredients", "ingredient", "key", "pattern",
                        "sourceCost", "source", "exp", "loops", "sequence"):
                if key in r:
                    v = r[key]
                    if key == "key":
                        v = {k: short(i) for k, i in v.items()}
                    elif key == "sequence":
                        v = [s.get("type", "").split(":")[-1] for s in v]
                    elif key not in ("pattern", "sourceCost", "source", "exp", "loops"):
                        v = short(v)
                    parts.append(f"{key}={v}")
            print(f"{hit[0]} <- {r.get('type')} [{Path(jar).name}:{n}] " + "; ".join(parts)[:400])
    return 0


if __name__ == "__main__":
    sys.exit(main())
