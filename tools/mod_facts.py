"""Facts a quest writer needs from a pinned mod JAR, as JSON (for content/sectors).

usage: python tools/mod_facts.py <out-dir> <modid> [<modid> ...]

Per mod: items and blocks with their EN and ES names (es_es, else es_ar or es_mx), advancements with
title, description, parent and hidden flag, recipe ids grouped by recipe type with their results,
structures and structure tags, entity types, and the mod's documentation-like lang entries (keys with
page, desc, tooltip, guide or lore). JARs come from catalog/curated.json and catalog/local-paths.json,
read only. Write the output outside the repository (it is large and derived).
"""
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def jar_paths():
    curated = json.loads((ROOT / "catalog/curated.json").read_text(encoding="utf-8"))
    local = json.loads((ROOT / "catalog/local-paths.json").read_text(encoding="utf-8"))
    by_mod = {}
    for m in curated["mods"]:
        for mm in m["metadata"]["mods"]:
            path = local.get(m["filename"])
            if path:
                by_mod[mm["id"]] = Path(path)
    return by_mod


def load_lang(z, ns, code):
    name = f"assets/{ns}/lang/{code}.json"
    if name not in z.namelist():
        return {}
    raw = z.read(name).decode("utf-8", errors="replace")
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return dict(re.findall(r'"([^"\\]+)"\s*:\s*"((?:[^"\\]|\\.)*)"', raw))


def result_ids(recipe):
    out = []

    def res(v):
        if isinstance(v, str):
            out.append(v)
        elif isinstance(v, dict):
            for k in ("id", "item", "fluid"):
                if isinstance(v.get(k), str):
                    out.append(v[k])
                    return
            for vv in v.values():
                res(vv)
        elif isinstance(v, list):
            for vv in v:
                res(vv)

    for key in ("result", "results", "output", "outputs"):
        if key in recipe:
            res(recipe[key])
    return sorted({i for i in out if ":" in i})


def facts_for(jar):
    z = zipfile.ZipFile(jar)
    names = z.namelist()
    namespaces = sorted({n.split("/")[1] for n in names if n.startswith(("assets/", "data/")) and n.count("/") >= 2})
    facts = {"jar": jar.name, "namespaces": namespaces, "items": {}, "advancements": {}, "recipes": defaultdict(list),
             "structures": [], "structure_tags": [], "entities": {}, "docs": {}}
    for ns in namespaces:
        en = load_lang(z, ns, "en_us")
        es = load_lang(z, ns, "es_es") or load_lang(z, ns, "es_ar") or load_lang(z, ns, "es_mx")
        for k, v in en.items():
            m = re.fullmatch(rf"(item|block)\.{re.escape(ns)}\.([a-z0-9_./]+)", k)
            if m:
                facts["items"][f"{ns}:{m.group(2)}"] = {"kind": m.group(1), "en": v, "es": es.get(k)}
            m = re.fullmatch(rf"entity\.{re.escape(ns)}\.([a-z0-9_./]+)", k)
            if m:
                facts["entities"][f"{ns}:{m.group(1)}"] = {"en": v, "es": es.get(k)}
            if re.search(r"(page|desc|tooltip|guide|lore)", k) and len(v) > 20:
                facts["docs"][k] = v
        for n in names:
            m = re.fullmatch(rf"data/{re.escape(ns)}/advancements?/(.+)\.json", n)
            if m and not m.group(1).startswith("recipes/"):
                try:
                    a = json.loads(z.read(n))
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
                disp = a.get("display") or {}

                def text(t):
                    return en.get(t.get("translate", ""), t.get("text", t.get("translate"))) if isinstance(t, dict) else t
                facts["advancements"][f"{ns}:{m.group(1)}"] = {"title": text(disp.get("title")),
                                                              "description": text(disp.get("description")),
                                                              "parent": a.get("parent"), "hidden": disp.get("hidden", False)}
            m = re.fullmatch(rf"data/{re.escape(ns)}/recipes?/(.+)\.json", n)
            if m:
                try:
                    r = json.loads(z.read(n))
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
                if isinstance(r, dict):
                    facts["recipes"][r.get("type", "?")].append({"id": f"{ns}:{m.group(1)}", "results": result_ids(r)})
            m = re.fullmatch(rf"data/{re.escape(ns)}/worldgen/structure/(.+)\.json", n)
            if m:
                facts["structures"].append(f"{ns}:{m.group(1)}")
            m = re.fullmatch(rf"data/{re.escape(ns)}/tags/worldgen/structure/(.+)\.json", n)
            if m:
                facts["structure_tags"].append(f"#{ns}:{m.group(1)}")
    facts["recipes"] = dict(sorted(facts["recipes"].items()))
    return facts


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    out = Path(sys.argv[1])
    assert not out.resolve().is_relative_to(ROOT), "write the facts outside the repository"
    out.mkdir(parents=True, exist_ok=True)
    paths = jar_paths()
    for mod in sys.argv[2:]:
        jar = paths.get(mod)
        if not jar or not jar.exists():
            print(mod, "JAR not found")
            continue
        facts = facts_for(jar)
        (out / f"{mod}.json").write_text(json.dumps(facts, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(mod, jar.name, "items", len(facts["items"]), "advancements", len(facts["advancements"]),
              "recipes", sum(len(v) for v in facts["recipes"].values()), "structures", len(facts["structures"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
