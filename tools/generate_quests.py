"""Generate ENTRELUMEN's original bilingual FTB chapters with stable global IDs.

Run with --check for read-only validation and generated-file drift detection.
No third-party content is read. IDs are signed-positive 64-bit hashes of semantic
keys, independent of ordering and translated text. Chapters may carry an optional
subtitle (FTB chapter_subtitle); '&' formatting codes are validated so the Atlas's
interference stays parseable and legible. Runtime verification remains required:
static validation cannot prove FTB loading or campaign synchronization.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import math

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "pack/config/ftbquests/quests"
LOCALES = ("en_us", "es_es")
CHAPTER_SOURCES = ("first_hour.json", "act_two.json", "act_three.json", "act_four.json",
                   "act_five.json", "act_six.json", "inventory_that_remembers.json")


def load_chapters():
    return [json.loads((ROOT / "content" / name).read_text(encoding="utf-8")) for name in CHAPTER_SOURCES]


def stable_id(key):
    value = int.from_bytes(hashlib.sha256(("entrelumen:v1:" + key).encode()).digest()[:8], "big")
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def snbt(value):
    # JSON is a valid quoted-key subset of SNBT; integer task counts fit int32.
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


# FTB Library parses '&' formatting codes in quest text (0-9, a-f, k-o, r and &#RRGGBB).
# The Atlas speaks through interference: struck-through words (&m) and short glitches (&k).
FORMAT_CODE = re.compile(r"&(#[0-9A-Fa-f]{6}|[0-9a-fk-or])")
MAX_GLITCH_CHARS = 8
MAX_GLITCHES = 2


def check_formatting(text, where, allow_codes):
    """Reject text that FTB would fail to parse, style that bleeds or glitches that hide the story."""
    stripped = FORMAT_CODE.sub("", text)
    assert "&" not in stripped, f"invalid or stray formatting code: {where}"
    if not allow_codes:
        assert stripped == text, f"formatting codes are only allowed in descriptions: {where}"
        return
    glitches = 0
    for paragraph in text.split("\n\n"):
        open_style, glitch = False, None
        for part in re.split(r"(&(?:#[0-9A-Fa-f]{6}|[0-9a-fk-or]))", paragraph):
            if FORMAT_CODE.fullmatch(part):
                if glitch is not None:
                    assert 0 < len(glitch.strip()) <= MAX_GLITCH_CHARS, f"unreadable glitch: {where}"
                    glitch = None
                open_style = part != "&r"
                if part == "&k":
                    glitches += 1
                    glitch = ""
            elif glitch is not None:
                glitch += part
        assert not open_style and glitch is None, f"formatting must be reset before the paragraph ends: {where}"
    assert glitches <= MAX_GLITCHES, f"too many glitches to stay legible: {where}"


def generate(data, all_quests=None, order_index=0):
    quests = data["quests"]
    assert quests, "empty chapter"
    keys = [q["key"] for q in quests]
    assert len(keys) == len(set(keys)), "duplicate semantic quest key"
    visiting, done, ids = set(), set(), set()

    def ident(key):
        result = stable_id(key)
        assert result != "0000000000000000" and result not in ids, "ID collision"
        ids.add(result)
        return result

    by_key = {q["key"]: q for q in (all_quests or quests)}
    local_keys = set(keys)
    positions = []
    assert data["layout_groups"] and all(set(labels) == set(LOCALES) for labels in data["layout_groups"].values())
    for q in quests:
        layout = q["layout"]
        assert layout["group"] in data["layout_groups"], "unknown layout group"
        assert all(isinstance(layout[c], (int, float)) and math.isfinite(layout[c]) for c in ("x", "y", "size"))
        assert layout["shape"] in ("square", "circle", "hexagon")
        assert 0.75 <= layout["size"] <= 1.5
        assert ("milestone" not in q or (layout["shape"] == "hexagon" and layout["size"] == 1.1)), "campaign hierarchy lost"
        assert isinstance(layout.get("hide_dependent_lines", False), bool)
        for previous in positions:
            assert math.hypot(layout["x"]-previous["x"], layout["y"]-previous["y"]) >= (layout["size"]+previous["size"])/2 + 0.5, "overlapping quest nodes"
        positions.append(layout)
        for dep in q["deps"]:
            if dep in local_keys:
                assert by_key[dep]["layout"]["y"] <= layout["y"], "dependency runs against reading direction"

    def visit(key):
        assert key in by_key, f"missing dependency: {key}"
        assert key not in visiting, f"dependency cycle: {key}"
        if key in done:
            return
        visiting.add(key)
        for dep in by_key[key]["deps"]:
            visit(dep)
        visiting.remove(key)
        done.add(key)

    for key in keys:
        visit(key)
    assert data["autofocus"] in local_keys and not any(dep in local_keys for dep in by_key[data["autofocus"]]["deps"]), "focus must point to the chapter entry"
    chapter_id = ident("chapter:" + data["chapter"])
    languages = {lang: {f"chapter.{chapter_id}.title": data["title"][lang]} for lang in LOCALES}
    for lang in LOCALES:
        check_formatting(data["title"][lang], f"{data['chapter']}/title/{lang}", False)
        for group, labels in data["layout_groups"].items():
            check_formatting(labels[lang], f"{data['chapter']}/{group}/{lang}", False)
    if "subtitle" in data:
        # Chapter presentation: FTB reads chapter_subtitle as a list of lines.
        assert set(data["subtitle"]) == set(LOCALES), "subtitle locale mismatch"
        for lang in LOCALES:
            lines = data["subtitle"][lang]
            assert isinstance(lines, list) and lines and all(isinstance(line, str) and line.strip() for line in lines)
            for line in lines:
                check_formatting(line, f"{data['chapter']}/subtitle/{lang}", False)
            languages[lang][f"chapter.{chapter_id}.chapter_subtitle"] = lines
    chapter = {"id": chapter_id, "filename": data["chapter"], "order_index": order_index,
               "icon": {"id": "entrelumen:atlas"}, "default_quest_shape": "square",
               "autofocus_id": stable_id("quest:" + data["autofocus"]), "quests": []}
    milestones = {}
    seen_text = {lang: set() for lang in LOCALES}
    for q in quests:
        key = q["key"]
        qid, tid = ident("quest:" + key), ident("task:" + key)
        kinds = sum(field in q for field in ("item", "milestone", "type"))
        assert kinds == 1, f"ambiguous task: {key}"
        task = {"id": tid}
        if "milestone" in q:
            assert q["milestone"] not in milestones, "duplicate campaign milestone"
            task.update(type="entrelumen:campaign", milestone=q["milestone"])
            milestones[q["milestone"]] = {"quest_id": qid, "task_id": tid}
        elif "item" in q:
            assert re.fullmatch(r"[a-z0-9_]+:[a-z0-9_/]+", q["item"])
            assert 1 <= q.get("count", 1) <= 64
            task.update(type="item", item={"id": q["item"], "count": 1}, count=q.get("count", 1), consume_items=False)
        else:
            assert q["type"] == "checkmark" and q.get("optional"), "checkmarks must be optional learning tasks"
            task.update(type="checkmark")
        layout = q["layout"]
        output = {"id": qid, "x": float(layout["x"]), "y": float(layout["y"]),
                  "shape": layout["shape"], "size": float(layout["size"]),
                  "hide_dependent_lines": layout.get("hide_dependent_lines", False),
                  "dependencies": [stable_id("quest:" + dep) for dep in q["deps"]],
                  "icon": {"id": q.get("icon", q.get("item", "minecraft:book"))}, "tasks": [task],
                  "rewards": []}
        if q.get("optional"):
            output["optional"] = True
        chapter["quests"].append(output)
        placeholders = []
        for lang in LOCALES:
            title, description = q[lang]
            assert title.strip() and len(description) >= 80, f"missing text: {key}/{lang}"
            check_formatting(title, f"{key}/title/{lang}", False)
            check_formatting(description, f"{key}/{lang}", True)
            assert description not in seen_text[lang], "duplicate description"
            seen_text[lang].add(description)
            languages[lang][f"quest.{qid}.title"] = title
            # Keep the route label separate from prose; no hardcoded UI shortcut keys.
            languages[lang][f"quest.{qid}.quest_desc"] = [data["layout_groups"][layout["group"]][lang], "", *description.split("\n\n")]
            placeholders.append(re.findall(r"%[0-9$]*[sd]|\{[a-zA-Z_][a-zA-Z_0-9]*\}", title + description))
        assert sorted(placeholders[0]) == sorted(placeholders[1]), f"placeholder mismatch: {key}"
    assert languages["en_us"].keys() == languages["es_es"].keys(), "locale key mismatch"
    assert set(milestones) == set(data.get("milestones", ["atlas_awakened", "travellers_table", "lens_assembled", "field_survey", "first_signal"]))
    files = {OUT / "chapters" / (data["chapter"] + ".snbt"): snbt(chapter),
             OUT / "data.snbt": snbt({"version": 13, "default_consume_items": False, "default_reward_team": True,
                                         "default_autoclaim_rewards": "disabled", "fallback_locale": "en_us", "pause_game": True}),
             OUT / "chapter_groups.snbt": snbt({"chapter_groups": []}),
             ROOT / "content/campaign_task_ids.json": snbt(milestones)}
    for lang in LOCALES:
        files[OUT / "lang" / (lang + ".snbt")] = snbt(languages[lang])
    return files


def generate_all(chapters):
    keys=[q['key'] for data in chapters for q in data['quests']]
    assert len(keys)==len(set(keys)), 'duplicate global quest key'
    assert len({data['chapter'] for data in chapters})==len(chapters), 'duplicate chapter'
    all_quests=[q for data in chapters for q in data['quests']]
    files={}; languages={lang:{} for lang in LOCALES}; milestones={}; ids=set(); descriptions={lang:set() for lang in LOCALES}
    for index,data in enumerate(chapters):
        assert set(data['title'])==set(LOCALES)
        generated=generate(data,all_quests,index)
        chapter_path=OUT/'chapters'/(data['chapter']+'.snbt')
        chapter=json.loads(generated[chapter_path])
        new_ids=[chapter['id']]+[i for q in chapter['quests'] for i in (q['id'],q['tasks'][0]['id'])]
        assert not ids.intersection(new_ids), 'global ID collision'
        ids.update(new_ids)
        for lang in LOCALES:
            for q in data['quests']:
                assert q[lang][1] not in descriptions[lang], 'duplicate global description'
                descriptions[lang].add(q[lang][1])
            path=OUT/'lang'/(lang+'.snbt'); values=json.loads(generated.pop(path))
            assert not languages[lang].keys() & values.keys(), 'duplicate locale key'
            languages[lang].update(values)
        mapping=json.loads(generated.pop(ROOT/'content/campaign_task_ids.json'))
        assert not milestones.keys() & mapping.keys(), 'duplicate global milestone'
        milestones.update(mapping); files.update(generated)
    for lang in LOCALES:files[OUT/'lang'/(lang+'.snbt')]=snbt(languages[lang])
    files[ROOT/'content/campaign_task_ids.json']=snbt(milestones)
    return files


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    chapters=load_chapters()
    files=generate_all(chapters);failures=[]
    for path,content in files.items():
        if args.check:
            if not path.exists() or path.read_text(encoding='utf-8')!=content:failures.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8',newline='\n')
    if failures:raise SystemExit('Generated output missing or stale: '+', '.join(failures))
    print(f"PASS: {len(chapters)} chapters, {sum(len(d['quests']) for d in chapters)} quests, global IDs/DAG, EN/ES parity; runtime not verified.")


if __name__=='__main__':main()
