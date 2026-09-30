"""Presentation v2 for the guide and story chapters (docs/design/quest-book-v3.md, "Presentación v2"; the writer's
checklist is in content/guides/README.md, "Pasar una guía o un capítulo de historia a la v2").

The sector chapters (tools/quest_engine.py) had v2 first. A guide (content/guides/guide_*.json) or a story chapter
(the files of tools/generate_quests.py CHAPTER_SOURCES) opts in with "presentation": 2 and then gets the same two
halves, compiled by the same code:

- text: each quest's copy is {"title", "text": [paragraphs]} ("subtitle" too in a guide), in the markup of
  quest_engine and tools/quest_text.py, compiled and checked by quest_engine.quest_copy (lead first, the 330
  characters of page 1, the banned meta phrases, EN/ES parity of links, items and keys). & codes are not text any
  more: FTB does not read them inside a JSON line, so [hl|…], [b|…], [glitch|…] and [strike|…] replace them;
- canvas: an "art" list of tools/quest_art.py kinds (reveal, through and grow, sketch) and quests with
  "role": "decor", the toys that never count. "motif" picks the palette of content/quest_book.json (accent, line,
  panel); without one the chapter draws in Heliodor's gold.

What does not change: keys and IDs, tasks, dependencies, optional flags, layout and shapes, rewards, the guide's
medallion, the story's node grammar, numeral, act emblem, branch panels, suns and campaign milestones. The story
keeps its own rules on top: every quest of an act says what, then why (the last paragraph is the lore; the optional
branch, "book": {"branch": true}, is exempt), no quest subtitles,
at most two glitches of up to eight characters, and its links reach story quests only. A chapter without
"presentation": 2 compiles exactly as before, and may not carry "art", "motif", decor or v2 copy.
"""
import re

import quest_art
import quest_engine as qe

LOCALES = qe.LOCALES
# Heliodor gold, the book's label cream and bronze panels: the ruins' own colours (quest_book.json "colors").
DEFAULT_PALETTE = {"accent": "#E8B04A", "line": "#E8DCB5", "panel": "#8E6A3A"}
FORMAT_CODE = re.compile(r"&(#[0-9A-Fa-f]{6}|[0-9a-fk-or])")
MAX_GLITCHES = 2
CALLOUT = re.compile(r"^(\[(li|lead|tip|careful|note)\b|\{)")
V2_KEYS = ("art", "motif", "medallion")
NODE_FLAGS = ("hide_lines", "hide_dependent_lines", "reveal", "icon_scale", "role")


def enabled(data):
    """Whether a guide or story chapter is in presentation v2; a v1 chapter carries nothing of v2."""
    name = data["chapter"]
    level = data.get("presentation", 1)
    assert level in (1, 2), f"{name}: presentation is 1 or 2"
    if level == 2:
        return True
    for k in V2_KEYS:
        assert k not in data, f"{name}: {k} needs \"presentation\": 2"
    for q in data["quests"]:
        for lang in LOCALES:
            assert isinstance(q[lang], list), \
                f"{q['key']}: {{title, text}} copy needs \"presentation\": 2 in the chapter (v1 is [title, description])"
        for k in NODE_FLAGS:
            assert k not in q, f"{q['key']}: {k} needs \"presentation\": 2"
    return False


def palette(data, book):
    if "motif" in data:
        assert data["motif"] in book["motifs"], f"{data['chapter']}: unknown motif {data['motif']}"
        motif = book["motifs"][data["motif"]]
        return {"accent": motif["accent"], "line": motif["line"], "panel": motif["panel"]}
    return dict(DEFAULT_PALETTE)


def context(data, book, resolve_quest, resolve_chapter):
    """The compile context of quest_engine (the sets check_guides.py reads back, the accent, presentation 2)."""
    ctx = {"items": set(), "names": set(), "keys": set(), "textures": set(), "entities": set(), "structures": set(),
           "advancements": set(), "accent": palette(data, book)["accent"], "presentation": 2,
           "resolve_quest": resolve_quest, "resolve_chapter": resolve_chapter}
    return ctx


def is_v2_copy(q):
    return isinstance(q["en_us"], dict)


def title_of(q, lang):
    return q[lang]["title"] if isinstance(q[lang], dict) else q[lang][0]


def description_of(q, lang):
    """The whole description as one string, paragraphs joined by a blank line, whatever the format."""
    return "\n\n".join(q[lang]["text"]) if isinstance(q[lang], dict) else q[lang][1]


def copy_lines(q, name, ctx, story=False):
    """{lang: (quest_desc lines, visible first page)} of a v2 quest, after the checks every v2 quest passes and, for
    a story quest, the story's own."""
    key = q["key"]
    allowed = {"title", "text"} | (set() if story else {"subtitle"})
    for lang in LOCALES:
        copy = q[lang]
        assert isinstance(copy, dict) and {"title", "text"} <= set(copy) <= allowed, \
            f"{key}: {lang} copy is {{{', '.join(sorted(allowed))}}}" + (" (a story quest has no subtitle)" if story else "")
        glitches = 0
        for i, para in enumerate(copy["text"]):
            assert isinstance(para, str), f"{key}:{lang}#{i}: a paragraph is a string"
            assert not FORMAT_CODE.search(para), \
                f"{key}:{lang}#{i}: v2 text takes markup, not & codes ([hl|…], [b|…], [i|…], [glitch|…], [strike|…])"
            glitches += para.count("[glitch|")
        assert glitches <= MAX_GLITCHES, f"{key}:{lang}: {glitches} glitches; the Atlas stays legible with {MAX_GLITCHES}"
        if story:
            text = copy["text"]
            what = [p for p in text[:-1] if p not in ("{page}", "{rule}")]
            assert what and not CALLOUT.match(text[-1]), \
                f"{key}:{lang}: a story quest says what, then why: its last paragraph is the lore, plain, after the what"
    return qe.quest_copy(q, name, ctx)


def node_flags(q, out):
    """The canvas flags a v2 node may take, as in a sector: hidden lines, reveal, icon scale."""
    if q.get("hide_lines"):
        out["hide_dependency_lines"] = True
    if q.get("hide_dependent_lines"):
        out["hide_dependent_lines"] = True
    if q.get("reveal"):
        out["hide_until_deps_complete"] = True
    if "icon_scale" in q:
        assert 0.1 <= q["icon_scale"] <= 2.0, f"{q['key']}: icon_scale"
        out["icon_scale"] = float(q["icon_scale"])
    return out


def is_decor(q):
    role = q.get("role")
    assert role in (None, "decor"), f"{q['key']}: a guide or story quest takes no role but decor"
    return role == "decor"


def decor(q, out):
    """A decor toy: one checkmark, shape none, no reward, no toast, no lock icon, no lines, never counted."""
    assert q["layout"]["shape"] == "none", f"{q['key']}: a decor node has shape none"
    out["tags"] = ["entrelumen_decor"]
    return quest_art.decor_node(q["key"], [q.get("type", "item")], q["layout"]["size"], out)


def art_images(data, book, languages, ctx, by_key, placed):
    """The chapter images of the "art" list; paths drawn "through" quests use their layout positions."""
    ctx["placed"] = placed
    pal = palette(data, book)
    images = []
    for i, art in enumerate(data.get("art", [])):
        images += quest_art.art_images(data["chapter"], i, art, pal, languages, ctx, by_key)
    return images
