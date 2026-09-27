"""Canonical layout for content/sectors/sector_*.json: one line per figure, link and image; each quest as a
short block (identity and flags, task, icon, position, one line per language, sources). Keeps diffs
readable when several writers add quests to the same chapter.

usage: python tools/format_sector.py [--check] [files...]   (default: every content/sectors/sector_*.json)
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SECTORS = ROOT / "content" / "sectors"
LINE_LISTS = ("figures", "links", "art", "groups")
HEAD = ("key", "role", "deps")
BLOCK = ("task", "tasks", "icon", "at", "en_us", "es_es", "sources")


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(", ", ": "))


def copy_block(lang, copy):
    parts = [f'"title": {compact(copy["title"])}']
    if "subtitle" in copy:
        parts.append(f'"subtitle": {compact(copy["subtitle"])}')
    extra = [k for k in copy if k not in ("title", "subtitle", "text")]
    parts += [f'"{k}": {compact(copy[k])}' for k in extra]
    text = copy["text"]
    lines = f'"text": [{compact(text[0])}' + "".join(f",\n                 {compact(p)}" for p in text[1:]) + "]"
    return f'      "{lang}": {{' + ", ".join(parts) + ",\n        " + lines + "}"


def quest_block(q):
    head = [f'"{k}": {compact(q[k])}' for k in HEAD if k in q]
    head += [f'"{k}": {compact(v)}' for k, v in q.items() if k not in HEAD and k not in BLOCK]
    rows = ["      " + ", ".join(head)]
    for k in ("task", "tasks", "icon", "at"):
        if k in q:
            rows.append(f'      "{k}": {compact(q[k])}')
    for lang in ("en_us", "es_es"):
        rows.append(copy_block(lang, q[lang]))
    rows.append(f'      "sources": {compact(q.get("sources", []))}')
    unknown = [k for k in q if k not in HEAD and k not in BLOCK and False]
    assert not unknown
    return "    {\n" + ",\n".join(rows) + "\n    }"


def dumps(data):
    out = []
    for k, v in data.items():
        if k == "quests":
            out.append('  "quests": [\n' + ",\n".join(quest_block(q) for q in v) + "\n  ]")
        elif k in LINE_LISTS and isinstance(v, dict):
            out.append(f'  "{k}": {{\n' + ",\n".join(f'    "{n}": {compact(x)}' for n, x in v.items()) + "\n  }")
        elif k in LINE_LISTS and isinstance(v, list):
            out.append(f'  "{k}": [\n' + ",\n".join(f"    {compact(x)}" for x in v) + "\n  ]")
        elif k == "subtitle":
            out.append('  "subtitle": {\n' + ",\n".join(f'    "{lang}": {compact(x)}' for lang, x in v.items()) + "\n  }")
        else:
            out.append(f'  "{k}": {compact(v)}')
    text = "{\n" + ",\n".join(out) + "\n}\n"
    assert json.loads(text) == data, "formatter changed the data"
    return text


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("files", nargs="*", type=Path)
    args = ap.parse_args()
    files = args.files or sorted(SECTORS.glob("sector_*.json"))
    stale = []
    for path in files:
        raw = path.read_text(encoding="utf-8")
        text = dumps(json.loads(raw))
        if raw != text:
            stale.append(path.name)
            if not args.check:
                path.write_text(text, encoding="utf-8", newline="\n")
    if args.check and stale:
        print("FAIL: not in canonical layout (run tools/format_sector.py):", ", ".join(stale))
        return 1
    print(f"{'PASS' if args.check else 'OK'}: {len(files)} sector files" + (f", rewrote {len(stale)}" if stale and not args.check else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
