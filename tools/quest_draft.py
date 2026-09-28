"""A presentation v2 draft of one chapter's quest text, for a writer to review (docs/design/quest-book-v3.md,
"Kit para pasar capítulos"; the form is quest-copy.md, "Forma (presentación v2)").

It rewrites only the "text" lists of content/sectors/<chapter>.json. Keys, tasks, titles, subtitles, sources,
positions and every fact stay: nothing is added or dropped but markup, bullets, and the lead-in words a callout
label replaces ("Careful:", "Ojo:"). Both languages move in lockstep, so paragraphs and pages stay parallel.

Per quest:
1. a warning sentence (Careful…, Never…, Don't…, explodes, destroys, is lost, or a [warn|…]) becomes its own
   [careful] paragraph, after the paragraph it came from; a sentence about the pack itself ("In this pack…",
   "Pack change:", an "(act III)" material) becomes a [note] the same way (two callouts per quest at most);
2. the first sentence becomes the [lead] when it is short in both languages (up to 110 visible characters in EN,
   125 in ES);
3. enumerations become lists: "intro: a, b, c and d" (three or more short items) and "a; b" clauses, one [li]
   each; three or more short sentences in a row after the lead, one [li] each; a long two-sentence paragraph
   becomes two paragraphs;
4. a list item that names an item whose inventory icon is its own flat, still texture opens with that icon,
   [li:<item>]; any other item keeps the plain bullet (tools/preview/mcassets.flat_icon reads the pinned JARs);
5. numbers get [hl|…]; one number that is the quest's star (its task count, a ×N multiplier, or a number in the
   title or subtitle) gets [big|…], where [big] may stand;
6. if the first page passes 330 visible characters in either language, its last paragraphs move to page 2.
Then each quest compiles through quest_engine.quest_copy, the same check the book compiles with; a quest that
fails keeps its v1 text. Quests already in v2 form are left alone. What it could not decide is printed.

usage: python tools/quest_draft.py content/sectors/sector_x.json [--dry-run] [--quest KEY ...] [--no-icons]
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import format_sector  # noqa: E402
import quest_engine as qe  # noqa: E402
import quest_text  # noqa: E402

LOCALES = ("en_us", "es_es")
LEAD_MAX = {"en_us": 110, "es_es": 125}   # visible characters of a lead: about two lines of bold text at GUI 2
ITEM_MAX = 110       # a sentence that becomes a list item
ENUM_ITEM_MAX = 48   # a comma-separated item of "intro: a, b and c"
ENUM_ITEM_WORDS = 6  # and its words (the longest item at most three times the shortest)
PARA_MAX = 120       # a paragraph longer than about two lines gets split when it has two sentences
MAX_CALLOUTS = 2
V2_MARK = re.compile(r"^\[(lead|li|careful|note)\b|\[(big)\||\[icon:")
OPEN, CLOSE = "", ""
HOLE = re.compile(OPEN + "([a-z]+)" + CLOSE)
# A sentence ends at . ! ? or … (with a closing quote or bracket), before a capital, a digit, ¿ ¡ or a tag.
BOUNDARY = re.compile(r"([.!?…][\"”»)]?)\s+(?=[¿¡\"“«(]?[A-ZÁÉÍÓÚÑÜ0-9" + OPEN + r"])")
ABBREVIATIONS = {"e.g", "i.e", "etc", "vs", "approx", "aprox", "ej", "p. ej", "no", "nº", "sr", "sra", "fig"}
NUMBER = re.compile(r"(?<![\w.,/×" + CLOSE + r"])(\d+(?:[.,]\d+)*(?:\s?[×x]\s?\d+)?%?|[×x]\d+|\d+×)(?![\w×])")
WARN_START = {
    "en_us": re.compile(r"^(careful|beware|warning|watch out|mind that|don't|do not|never|avoid|keep \w+ away)\b", re.I),
    "es_es": re.compile(r"^(ojo|cuidado|atención|nunca|no\s+(pongas|mezcles|uses|dejes|rompas))\b", re.I),
}
WARN_ANY = re.compile(r"\b(explod\w*|explosion|destroy\w*|is lost|are lost|gets? lost|burns? up|catch(es)? fire|"
                      r"melts? down|meltdown)\b", re.I)
NOTE_ANY = {
    # "In this pack…", "Pack change:", or a part that takes an act material ("takes a Reinforced Alloy (act III)")
    "en_us": re.compile(r"\b(in this pack|this pack's|the pack's|pack change)\b"
                        r"|\b(takes?|needs?|in place of|instead of)\b[^.]*\(act (I|II|III|IV|V|VI)\)", re.I),
    "es_es": re.compile(r"\b(en este pack|del pack|cambio del pack)\b|\(acto (I|II|III|IV|V|VI)\)", re.I),
}
NOTE_WORDS = {
    "en_us": re.compile(r"^(pack change|note)\s*[:,.-]?\s+", re.I),
    "es_es": re.compile(r"^(cambio del pack|dato|nota)\s*[:,.-]?\s+", re.I),
}
LABEL_WORDS = {
    "en_us": re.compile(r"^(careful|warning|beware|watch out|heads up)\s*[:,!.-]?\s+", re.I),
    "es_es": re.compile(r"^(ojo|cuidado|atención)\s*[:,!.-]?\s+", re.I),
}
BULLET = re.compile(r"^[•·\-–]\s+")
LAST_JOIN = {"en_us": re.compile(r"^(.*?),?\s+(?:and|or)\s+(.+)$"), "es_es": re.compile(r"^(.*?),?\s+(?:y|e|o|u|ni)\s+(.+)$")}


# --------------------------------------------------------------------------------------------------
# Masking: tags become placeholders, so sentence and number rules never look inside them

def _letters(n):
    s = ""
    while True:
        s = chr(97 + n % 26) + s
        n //= 26
        if not n:
            return s


def _number(s):
    n = 0
    for c in s:
        n = n * 26 + ord(c) - 97
    return n


def mask(text):
    tags = []

    def sub(m):
        tags.append(m.group(0))
        return OPEN + _letters(len(tags) - 1) + CLOSE
    return qe.TAG.sub(sub, text), tags


def unmask(text, tags):
    return HOLE.sub(lambda m: tags[_number(m.group(1))], text)


def shown(tag):
    """What a tag shows, roughly: its text, a key cap, an icon."""
    m = qe.TAG.fullmatch(tag)
    name, rest = m.group(1), m.group(3)
    parts = rest.split("|")[1:] if rest else []
    if parts:
        return parts[0]
    return {"key": "[K]", "icon": "#", "tip": "» Pro tip: ", "li": "• "}.get(name, "")


def visible(masked, tags):
    return HOLE.sub(lambda m: shown(tags[_number(m.group(1))]), masked)


def sentences(masked):
    out, start = [], 0
    for m in BOUNDARY.finditer(masked):
        head = masked[start:m.end(1)]
        word = re.search(r"([\w.º]+)[.]$", head)
        if word and word.group(1).lower().rstrip(".") in ABBREVIATIONS:
            continue
        out.append(head.strip())
        start = m.end()
    out.append(masked[start:].strip())
    return [s for s in out if s]


def capital(text):
    """The first letter up, when the text opens with a word (not a number, a tag or a symbol)."""
    stripped = text.lstrip()
    if stripped[:1].isalpha() and not re.match(r"(alt|ctrl|shift)\+", stripped, re.I):   # shift+click stays lower case
        i = len(text) - len(stripped)
        return text[:i] + stripped[0].upper() + stripped[1:]
    return text


# --------------------------------------------------------------------------------------------------
# Blocks: one output paragraph, masked, with the tags of the paragraph it came from

class Block:
    def __init__(self, kind, text, tags, icon=None, whole=False):
        self.kind, self.text, self.tags, self.icon = kind, text, tags, icon   # kind: lead li plain careful tip raw
        self.whole = whole   # a paragraph that could not be cut into sentences: never split or turned into a callout

    def length(self):
        return len(visible(self.text, self.tags))

    def render(self):
        body = unmask(self.text, self.tags)
        if self.kind == "raw":
            return body
        prefix = {"lead": "[lead] ", "careful": "[careful] ", "note": "[note] ", "tip": "[tip] ", "plain": ""}.get(self.kind)
        if self.kind == "li":
            prefix = f"[li:{self.icon}] " if self.icon else "[li] "
        return prefix + body


def is_warning(masked, tags, lang):
    text = visible(masked, tags)
    return bool(WARN_START[lang].search(text) or WARN_ANY.search(text) or "[warn|" in unmask(masked, tags))


def is_pack_note(masked, tags, lang):
    return bool(NOTE_ANY[lang].search(visible(masked, tags)))


def note_text(masked, tags, lang):
    """A sentence about the pack as a [note]: no "Pack change:" in front (the label says "Note:")."""
    m = NOTE_WORDS[lang].match(masked)
    return capital(masked[m.end():]) if m else masked


def careful_text(masked, tags, lang):
    """A warning sentence as callout text: no lead-in word (the label says it), no [warn|…] colour inside."""
    m = LABEL_WORDS[lang].match(masked)
    if m:
        masked = capital(masked[m.end():])
    for i, tag in enumerate(tags):
        if tag.startswith("[warn|"):
            hole = OPEN + _letters(i) + CLOSE
            masked = masked.replace(hole, tag[len("[warn|"):-1])
    return masked


def enumeration(masked, tags, lang):
    """(intro, items) of "intro: a, b, c and d" (3+ short items) or "intro: a; b" (2+ clauses), else None."""
    if ": " not in masked:
        return None
    intro, tail = masked.split(": ", 1)
    if len(visible(intro, tags)) < 6 or "; " in intro:
        return None
    tail = tail.rstrip()
    if tail[-1:] in ".!":
        tail = tail[:-1]
    if "; " in tail:
        items = [t.strip() for t in tail.split("; ")]
        if len(items) < 2 or any(len(visible(t, tags)) < 12 for t in items):
            return None
    else:
        items = [t.strip() for t in re.split(r",\s+", tail)]
        m = LAST_JOIN[lang].match(items[-1])
        if not m or len(items) < 2:
            return None
        items[-1:] = [m.group(1).strip(), m.group(2).strip()]
        if len(items) < 3 or any(not t or len(visible(t, tags)) > ENUM_ITEM_MAX for t in items):
            return None
        # a list is parallel: "the tidy way to tell a piston, bearing or gantry where to stop" is one phrase, not
        # three items (seven words, one word, four words)
        words = [len(visible(t, tags).split()) for t in items]
        if max(words) > ENUM_ITEM_WORDS or max(words) > 3 * min(words):
            return None
    return intro + ":", [capital(t) for t in items]


def clauses(masked, tags):
    """ "a; b" -> [a, b] when every clause is a real clause (12+ visible characters), else None."""
    if "; " not in masked or ": " in masked:
        return None
    parts = [p.strip() for p in masked.split("; ")]
    if any(len(visible(p, tags)) < 12 for p in parts):
        return None
    end = parts[-1][-1:] if parts[-1][-1:] in ".!?" else ""
    return [capital(p) + ("" if p.endswith((".", "!", "?")) else end) for p in parts]


# --------------------------------------------------------------------------------------------------
# One quest

class Draft:
    def __init__(self, chapter, flat_icon=None):
        self.chapter = chapter
        self.flat_icon = flat_icon or (lambda item: None)
        self.notes = []       # (key, what could not be decided)
        self.stats = {"quests": 0, "drafted": 0, "v2": 0, "kept": 0, "lead": 0, "li": 0, "icons": 0, "careful": 0,
                      "note": 0, "big": 0, "hl": 0, "moved": 0}

    def note(self, key, text):
        self.notes.append((key, text))

    # --- paragraph level
    def split_paragraph(self, key, i, src):
        """{lang: [Block…]} for source paragraph i of both languages, in lockstep."""
        masked = {lang: mask(src[lang]) for lang in LOCALES}
        kinds = set()
        for lang in LOCALES:
            text = src[lang]
            if text in ("{page}", "{rule}") or text.startswith("{image:"):
                kinds.add("raw")
            elif text.startswith("[tip]"):
                kinds.add("tip")
            elif BULLET.match(text):
                kinds.add("bullet")
            else:
                kinds.add("plain")
        if len(kinds) > 1:
            self.note(key, f"paragraph {i}: EN and ES are different kinds; left as it was")
            return {lang: [Block("raw", *masked[lang])] for lang in LOCALES}
        kind = kinds.pop()
        if kind == "raw":
            return {lang: [Block("raw", *masked[lang])] for lang in LOCALES}
        if kind == "bullet":   # a v1 list written with "• ": one item, kept whole
            return {lang: [Block("li", BULLET.sub("", masked[lang][0], count=1), masked[lang][1], whole=True)]
                    for lang in LOCALES}
        if kind == "tip":
            out = {}
            for lang in LOCALES:
                text, tags = masked[lang]
                out[lang] = [Block("tip", text[len(OPEN) + 1 + len(CLOSE):].lstrip() if text.startswith(OPEN) else text, tags)]
            return out
        sents = {lang: sentences(masked[lang][0]) for lang in LOCALES}
        if len(sents["en_us"]) != len(sents["es_es"]):
            self.note(key, f"paragraph {i}: {len(sents['en_us'])} sentences in EN, {len(sents['es_es'])} in ES; not split")
            return {lang: [Block("plain", *masked[lang], whole=True)] for lang in LOCALES}
        return {lang: [Block("plain", s, masked[lang][1]) for s in sents[lang]] for lang in LOCALES}   # one per sentence, for now

    def quest(self, q):
        key = q["key"]
        self.stats["quests"] += 1
        src = {lang: q[lang]["text"] for lang in LOCALES}
        if any(V2_MARK.search(p) for lang in LOCALES for p in src[lang]):
            self.stats["v2"] += 1
            return None
        if len(src["en_us"]) != len(src["es_es"]):
            self.note(key, f"{len(src['en_us'])} paragraphs in EN, {len(src['es_es'])} in ES; not drafted")
            self.stats["kept"] += 1
            return None
        # 1. paragraphs cut into sentences (plain), kept whole (tip, raw)
        paras = [self.split_paragraph(key, i, {lang: src[lang][i] for lang in LOCALES}) for i in range(len(src["en_us"]))]
        # 2. warnings and pack notes out of plain paragraphs, into callouts after their paragraph
        careful_after = {}
        cautions = notes = 0
        for i, p in enumerate(paras):
            en, es = p["en_us"], p["es_es"]
            if not en or en[0].kind != "plain":
                continue
            keep_en, keep_es = [], []
            for a, b in zip(en, es):
                kind = None
                if not a.whole and is_warning(a.text, a.tags, "en_us"):
                    kind = "careful"
                elif not a.whole and is_pack_note(a.text, a.tags, "en_us") and is_pack_note(b.text, b.tags, "es_es"):
                    kind = "note"
                if kind:
                    if cautions + notes < MAX_CALLOUTS:
                        make = careful_text if kind == "careful" else note_text
                        careful_after.setdefault(i, []).append(
                            {"en_us": Block(kind, make(a.text, a.tags, "en_us"), a.tags),
                             "es_es": Block(kind, make(b.text, b.tags, "es_es"), b.tags)})
                        if kind == "careful":
                            cautions += 1
                        else:
                            notes += 1
                        continue
                    self.note(key, "more than two callouts: the rest stay in their paragraphs")
                keep_en.append(a)
                keep_es.append(b)
            p["en_us"], p["es_es"] = keep_en, keep_es
        # 3. the lead: the first sentence of a plain first paragraph
        out = {lang: [] for lang in LOCALES}
        lead = False
        first = paras[0]
        if first["en_us"] and first["en_us"][0].kind == "plain" and not first["en_us"][0].whole:
            a, b = first["en_us"][0], first["es_es"][0]
            enum = {lang: enumeration(s.text, s.tags, lang) for lang, s in (("en_us", a), ("es_es", b))}
            if enum["en_us"] and enum["es_es"] and len(enum["en_us"][1]) == len(enum["es_es"][1]) \
                    and all(len(visible(enum[l][0], s.tags)) <= LEAD_MAX[l] for l, s in (("en_us", a), ("es_es", b))):
                lead = True
                for lang, s in (("en_us", a), ("es_es", b)):
                    out[lang].append(Block("lead", enum[lang][0], s.tags))
                    out[lang] += [Block("li", item, s.tags) for item in enum[lang][1]]
                first["en_us"], first["es_es"] = first["en_us"][1:], first["es_es"][1:]
            elif a.length() <= LEAD_MAX["en_us"] and b.length() <= LEAD_MAX["es_es"] and not a.text.endswith(":"):
                lead = True
                out["en_us"].append(Block("lead", a.text, a.tags))
                out["es_es"].append(Block("lead", b.text, b.tags))
                first["en_us"], first["es_es"] = first["en_us"][1:], first["es_es"][1:]
            else:
                self.note(key, f"lead: the first sentence runs {a.length()} characters in EN and {b.length()} in ES "
                               f"(limits {LEAD_MAX['en_us']} and {LEAD_MAX['es_es']}); write a short one")
        elif first["en_us"] and first["en_us"][0].kind == "plain":
            self.note(key, "lead: EN and ES do not cut into the same sentences; write one")
        # 4. lists and paragraphs
        for i, p in enumerate(paras):
            en, es = p["en_us"], p["es_es"]
            if en and en[0].kind in ("tip", "raw", "li"):
                out["en_us"] += en
                out["es_es"] += es
            elif en:
                self.shape(key, en, es, out)
            for c in careful_after.get(i, []):
                out["en_us"].append(c["en_us"])
                out["es_es"].append(c["es_es"])
        if lead:
            self.stats["lead"] += 1
        self.stats["careful"] += cautions
        self.stats["note"] += notes
        # 5. icons, [big], [hl]
        self.icons(key, out)
        self.numbers(key, q, out)
        # 6. overflow to page 2
        texts = {lang: [b.render() for b in out[lang]] for lang in LOCALES}
        texts = self.overflow(key, texts)
        return texts

    def shape(self, key, en, es, out):
        """Plain sentences of one paragraph (EN and ES aligned) -> paragraphs and lists."""
        tags_en, tags_es = en[0].tags, es[0].tags
        if en[0].whole:
            out["en_us"].append(en[0])
            out["es_es"].append(es[0])
            return
        # enumerations and clauses, sentence by sentence
        pieces_en, pieces_es = [], []   # (kind, [texts])
        for a, b in zip(en, es):
            ea, eb = enumeration(a.text, a.tags, "en_us"), enumeration(b.text, b.tags, "es_es")
            if ea and eb and len(ea[1]) == len(eb[1]):
                pieces_en.append(("intro", [ea[0]]))
                pieces_es.append(("intro", [eb[0]]))
                pieces_en.append(("items", ea[1]))
                pieces_es.append(("items", eb[1]))
                continue
            if bool(ea) != bool(eb) or (ea and eb):
                self.note(key, "an enumeration splits differently in EN and ES; kept as a sentence")
            ca, cb = clauses(a.text, a.tags), clauses(b.text, b.tags)
            if ca and cb and len(ca) == len(cb):
                pieces_en.append(("items", ca))
                pieces_es.append(("items", cb))
                continue
            pieces_en.append(("sentence", [a.text]))
            pieces_es.append(("sentence", [b.text]))
        # runs of plain sentences: three or more short ones become a list; a long pair, two paragraphs
        def flush(run_en, run_es):
            if not run_en:
                return
            short = all(len(visible(s, tags_en)) <= ITEM_MAX for s in run_en) and \
                all(len(visible(s, tags_es)) <= ITEM_MAX for s in run_es)
            if len(run_en) >= 3 and short:
                out["en_us"].extend(Block("li", s, tags_en) for s in run_en)
                out["es_es"].extend(Block("li", s, tags_es) for s in run_es)
                return
            joined_en, joined_es = " ".join(run_en), " ".join(run_es)
            if len(run_en) == 2 and max(len(visible(joined_en, tags_en)), len(visible(joined_es, tags_es))) > PARA_MAX:
                out["en_us"].extend(Block("plain", s, tags_en) for s in run_en)
                out["es_es"].extend(Block("plain", s, tags_es) for s in run_es)
                return
            if len(run_en) >= 3:
                self.note(key, f"{len(run_en)} sentences in a row, some long; kept as one paragraph")
            out["en_us"].append(Block("plain", joined_en, tags_en))
            out["es_es"].append(Block("plain", joined_es, tags_es))
        run_en, run_es = [], []
        for (ka, ta), (kb, tb) in zip(pieces_en, pieces_es):
            if ka == "sentence":
                run_en += ta
                run_es += tb
                continue
            flush(run_en, run_es)
            run_en, run_es = [], []
            if ka == "intro":
                out["en_us"].append(Block("plain", ta[0], tags_en))
                out["es_es"].append(Block("plain", tb[0], tags_es))
            else:
                out["en_us"].extend(Block("li", t, tags_en) for t in ta)
                out["es_es"].extend(Block("li", t, tags_es) for t in tb)
        flush(run_en, run_es)

    def icons(self, key, out):
        for a, b in zip(out["en_us"], out["es_es"]):
            if a.kind != "li":
                continue
            self.stats["li"] += 1
            items = [m.group(2) for m in qe.TAG.finditer(unmask(a.text, a.tags)) if m.group(1) == "item"]
            other = {m.group(2) for m in qe.TAG.finditer(unmask(b.text, b.tags)) if m.group(1) == "item"}
            if not items:
                continue
            item = items[0]
            if item not in other:
                self.note(key, f"a list item names {item} in EN only; plain bullet")
                continue
            if self.flat_icon(item):
                a.icon = b.icon = item
                self.stats["icons"] += 1

    def numbers(self, key, q, out):
        """[big|…] for the star number, [hl|…] for the rest (both languages)."""
        stars = set()
        tasks = q.get("tasks") or [q.get("task") or {}]
        for t in tasks:
            if isinstance(t, dict) and int(t.get("count", 1)) >= 2:
                stars.add(str(int(t["count"])))
        for lang in LOCALES:
            for field in ("title", "subtitle"):
                for m in re.finditer(r"\d+", q[lang].get(field) or ""):
                    stars.add(m.group(0))
        big = None
        blocks = list(zip(out["en_us"], out["es_es"]))
        for i, (a, b) in enumerate(blocks):
            if big or a.kind not in ("plain", "li", "careful", "note", "tip") or not self.big_fits(out["en_us"], i):
                continue
            for m in NUMBER.finditer(a.text):
                value = norm(m.group(1))
                multiplier = "×" in m.group(1) and not re.search(r"\d\s?×\s?\d", m.group(1))
                if (value in stars or multiplier) and len(m.group(1)) <= quest_text.MAX_BIG:
                    twin = next((mm for mm in NUMBER.finditer(b.text) if norm(mm.group(1)) == value), None)
                    if twin is None:
                        self.note(key, f"[big] candidate {m.group(1)} has no twin in ES; left as [hl]")
                        continue
                    big = (i, m.span(1), twin.span(1))
                    break
        for i, (a, b) in enumerate(blocks):
            for lang, block in (("en_us", a), ("es_es", b)):
                if block.kind == "raw":
                    continue
                span = None
                if big and big[0] == i:
                    span = big[1] if lang == "en_us" else big[2]
                block.text, count = highlight(block.text, span)
                if lang == "en_us":
                    self.stats["hl"] += count
        if big:
            self.stats["big"] += 1

    @staticmethod
    def big_fits(blocks, i):
        """[big] rises 7 px into the line above: never the first paragraph of a page, never after a list item."""
        if i == 0 or blocks[i - 1].kind == "raw" and blocks[i - 1].text == "{page}":
            return False
        return not (blocks[i].kind == "li" and blocks[i - 1].kind == "li")

    def overflow(self, key, texts):
        """Move the last paragraphs of page 1 to page 2 until page 1 fits (330 visible characters) in both."""
        def page1(paragraphs, lang):
            end = paragraphs.index("{page}") if "{page}" in paragraphs else len(paragraphs)
            return end, first_page_length(paragraphs, lang)
        end, _ = page1(texts["en_us"], "en_us")
        if all(first_page_length(texts[lang], lang) <= qe.MAX_PAGE_CHARS for lang in LOCALES):
            return texts
        for cut in range(end - 1, 0, -1):
            if texts["en_us"][cut - 1].startswith("[li") and texts["en_us"][cut].startswith("[li"):
                continue   # never between two items of a list
            if texts["en_us"][cut - 1].endswith(":"):
                continue   # nor between a list and its intro
            trial = {}
            for lang in LOCALES:
                t = texts[lang]
                rest = t[end + 1:] if end < len(t) else []
                trial[lang] = t[:cut] + ["{page}"] + t[cut:end] + rest
            if all(first_page_length(trial[lang], lang) <= qe.MAX_PAGE_CHARS for lang in LOCALES):
                self.stats["moved"] += 1
                for lang in LOCALES:
                    after = trial[lang].index("{page}") + 1
                    if "[big|" in trial[lang][after]:   # [big] cannot open a page
                        trial[lang][after] = re.sub(r"\[big\|([^\]]*)\]", r"[hl|\1]", trial[lang][after])
                return trial
        self.note(key, "page 1 passes 330 characters and no cut fits; shorten it")
        return texts


def norm(number):
    return number.replace(",", "").replace(".", "").replace(" ", "").replace("x", "×")


def highlight(masked, big_span=None):
    """[hl|…] around every number of a masked paragraph ([big|…] for the one at big_span). Returns (text, count)."""
    out, pos, count = [], 0, 0
    for m in NUMBER.finditer(masked):
        out.append(masked[pos:m.start(1)])
        if big_span and m.span(1) == big_span:
            out.append(f"[big|{m.group(1)}]")
        else:
            out.append(f"[hl|{m.group(1)}]")
            count += 1
        pos = m.end(1)
    out.append(masked[pos:])
    return "".join(out), count


def _ctx():
    return {"items": set(), "names": set(), "keys": set(), "textures": set(), "entities": set(), "structures": set(),
            "advancements": set(), "accent": "#FFFFFF", "presentation": 2,
            "resolve_quest": lambda target, where: qe.stable_id("quest:" + target),
            "resolve_chapter": lambda target, where: qe.stable_id("chapter:" + target)}


def first_page_length(paragraphs, lang):
    try:
        return len(qe.compile_text(paragraphs, lang, _ctx(), "draft")[1])
    except AssertionError:
        return 10 ** 6


# --------------------------------------------------------------------------------------------------
# The chapter

def draft_chapter(data, flat_icon=None, only=None):
    """Draft every quest of a sector (in place). Returns the Draft with its notes and counts."""
    d = Draft(data["chapter"], flat_icon)
    for q in data["quests"]:
        if only and q["key"] not in only:
            continue
        before = {lang: list(q[lang]["text"]) for lang in LOCALES}
        try:
            texts = d.quest(q)
        except AssertionError as e:   # a rule of the draft itself: keep v1
            d.note(q["key"], f"kept v1: {e}")
            d.stats["kept"] += 1
            continue
        if texts is None:
            continue
        for lang in LOCALES:
            q[lang]["text"] = texts[lang]
        try:
            qe.quest_copy(q, data["chapter"], _ctx())
        except AssertionError as e:
            for lang in LOCALES:
                q[lang]["text"] = before[lang]
            d.note(q["key"], f"kept v1: the draft breaks a compile rule ({e})")
            d.stats["kept"] += 1
            continue
        d.stats["drafted"] += 1
    return d


def icon_reader():
    sys.path.insert(0, str(ROOT / "tools" / "preview"))
    from mcassets import Assets
    assets = Assets(ROOT)
    cache = {}

    def flat(item):
        if item not in cache:
            cache[item] = assets.flat_icon(item)
        return cache[item]
    return flat


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("chapter", type=Path, help="content/sectors/sector_x.json")
    ap.add_argument("--dry-run", action="store_true", help="print the report, write nothing")
    ap.add_argument("--quest", action="append", help="only these quest keys (repeatable)")
    ap.add_argument("--no-icons", action="store_true", help="plain bullets only (no JAR reads)")
    args = ap.parse_args(argv)
    path = args.chapter
    data = json.loads(path.read_text(encoding="utf-8"))
    d = draft_chapter(data, None if args.no_icons else icon_reader(), set(args.quest or []))
    s = d.stats
    print(f"{data['chapter']}: drafted {s['drafted']} of {s['quests']} quests "
          f"({s['v2']} already v2, {s['kept']} kept as they were)")
    print(f"  leads {s['lead']}, list items {s['li']} ({s['icons']} with an icon), careful {s['careful']}, note {s['note']}, "
          f"big {s['big']}, hl {s['hl']}, moved to page 2: {s['moved']}")
    if d.notes:
        print("Could not decide (check these by hand):")
        width = max(len(k) for k, _ in d.notes)
        for k, text in d.notes:
            print(f"  {k:<{width}}  {text}")
    if data.get("presentation", 1) < 2:
        print('Reminder: add "presentation": 2 when the canvas follows the v2 rules too.')
    if not args.dry_run:
        path.write_text(format_sector.dumps(data), encoding="utf-8", newline="\n")
        print(f"wrote {path} (review it with git diff)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
