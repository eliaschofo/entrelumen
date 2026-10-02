"""Quest-book contracts of 25 September 2026 (docs/design/quest-book.md): hub, guides, groups,
node grammar, reading width, chapter images, rewards and theme. Static only: FTB loading and the
look inside the client are runtime questions. Sector chapters (quest book v3) have their own
contracts in tools/test_sector_book.py; here they only take part in the book-wide checks."""
import copy, json, math, re, struct, unittest
from generate_quests import (ROOT, OUT, THEME, GRAMMAR, NODE_PX, VISUAL, ART_PX, LOCALES, generate_book, load_book,
                             load_chapters, load_guides, stable_id, finale_of, story_role, group_order,
                             generate_guide, guide_lines)
import quest_engine
import quest_art
import quest_v2

ART = ROOT / 'companion/src/main/resources/assets/entrelumen/textures/gui/quests'


def png_size(path):
    with open(path, 'rb') as f:
        head = f.read(24)
    assert head[:8] == b'\x89PNG\r\n\x1a\n'
    return struct.unpack('>II', head[16:24])


class QuestBook(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.book = load_book()
        cls.story = load_chapters()
        cls.guides = load_guides(cls.book)
        cls.sectors = quest_engine.load_sectors()
        cls.files = generate_book(cls.story, cls.guides, cls.book, cls.sectors)
        cls.sector_names = {x['chapter'] for x in cls.sectors}
        # Guides and story chapters in presentation v2 draw like sectors (tools/quest_v2.py; test_presentation.py).
        cls.v2_names = {x['chapter'] for x in cls.guides + cls.story if x.get('presentation', 1) >= 2}
        cls.chapters = {p.stem: json.loads(c) for p, c in cls.files.items() if p.parent == OUT / 'chapters'}
        cls.lang = {l: json.loads(cls.files[OUT / 'lang' / (l + '.snbt')]) for l in LOCALES}
        cls.quests = {q['id']: q for c in cls.chapters.values() for q in c['quests']}

    def test_generated_files_are_current(self):
        for path, content in self.files.items():
            with self.subTest(path=path.name):
                self.assertTrue(path.exists())
                self.assertEqual(path.read_text(encoding='utf-8'), content)
        on_disk = {p.stem for p in (OUT / 'chapters').glob('*.snbt')}
        self.assertEqual(on_disk, set(self.chapters))
        tables = {p.stem for p in (OUT / 'reward_tables').glob('*.snbt')}
        self.assertEqual(tables, {p.stem for p in self.files if p.parent == OUT / 'reward_tables'})

    def test_book_structure_hub_story_and_five_groups(self):
        self.assertEqual(len(self.chapters), 1 + len(self.story) + len(self.guides) + len(self.sectors))
        # Every guide and sector file is a chapter; converting guides into sectors never drops one.
        self.assertEqual({g['chapter'] for g in self.guides}, {p.stem for p in (ROOT / 'content/guides').glob('guide_*.json')})
        self.assertEqual(self.sector_names, {p.stem for p in (ROOT / 'content/sectors').glob('sector_*.json')})
        groups = json.loads(self.files[OUT / 'chapter_groups.snbt'])['chapter_groups']
        self.assertEqual([g['id'] for g in groups], [stable_id('chapter_group:' + g['id']) for g in self.book['groups']])
        self.assertEqual([g['id'] for g in self.book['groups']], ['entrelumen', 'qol', 'tech', 'magic', 'exploration'])
        hub = self.chapters[self.book['hub']['chapter']]
        self.assertEqual(hub['order_index'], 0)
        self.assertNotIn('group', hub)
        for i, data in enumerate(self.story):
            c = self.chapters[data['chapter']]
            self.assertEqual((c['order_index'], c.get('group')), (i + 1, None))
        for data in self.guides:
            c = self.chapters[data['chapter']]
            self.assertEqual(c['group'], stable_id('chapter_group:' + data['group']))
        for data in self.sectors:
            c = self.chapters[data['chapter']]
            self.assertEqual(c['group'], stable_id('chapter_group:' + data['group']))
        for group in self.book['groups']:
            members = [g['chapter'] for g in self.guides + self.sectors if g['group'] == group['id']]
            order = sorted(members, key=lambda n: self.chapters[n]['order_index'])
            self.assertEqual([self.chapters[n]['order_index'] for n in order], list(range(len(members))))
            self.assertEqual(order[:len(group['lead'])], group['lead'])
            for lang in LOCALES:
                self.assertEqual(self.lang[lang][f"chapter_group.{stable_id('chapter_group:' + group['id'])}.title"], group['title'][lang])

    def test_guides_keep_their_authored_layout_tasks_and_optional_flags(self):
        seen = set()
        for data in self.guides:
            c = self.chapters[data['chapter']]
            self.assertEqual(len(c['quests']), len(data['quests']))
            for source, q in zip(data['quests'], c['quests']):
                with self.subTest(quest=source['key']):
                    self.assertEqual(q['id'], stable_id('quest:' + source['key']))
                    self.assertNotIn(q['id'], seen)
                    seen.add(q['id'])
                    lay = source['layout']
                    self.assertEqual((q['x'], q['y'], q['size'], q['shape']), (float(lay['x']), float(lay['y']), float(lay['size']), lay['shape']))
                    self.assertEqual(q.get('optional', False), source.get('optional', False))
                    self.assertEqual(q['dependencies'], [stable_id('quest:' + d) for d in source['deps']])
                    task = q['tasks'][0]
                    kind = source.get('type', 'item')
                    carried = kind == 'item' and source.get('item') in quest_engine.carried_items()
                    self.assertEqual(task['type'], quest_engine.CARRIED_TASK if carried else kind)
                    if kind == 'item':   # an equippable is a carried item: never taken, so no consume flag (F25)
                        self.assertEqual((task['item'], task['count'], task.get('consume_items', False)),
                                         ({'id': source['item'], 'count': 1}, source.get('count', 1), False))
                    if kind == 'advancement':
                        self.assertEqual(task['advancement'], source['advancement'])
                    if kind == 'dimension':
                        self.assertEqual(task['dimension'], source['dimension'])
                    if quest_v2.is_v2_copy(source):
                        # v2 copy compiles like a sector's (quest_engine.quest_copy), links resolved the same way.
                        ctx = quest_v2.context(data, self.book, lambda t, w: stable_id('quest:' + t),
                                               lambda t, w: stable_id('chapter:' + t))
                        texts = quest_v2.copy_lines(source, data['chapter'], ctx)
                        for lang in LOCALES:
                            self.assertEqual(self.lang[lang][f"quest.{q['id']}.title"], source[lang]['title'])
                            self.assertEqual(self.lang[lang][f"quest.{q['id']}.quest_desc"], texts[lang][0])
                        continue
                    for lang in LOCALES:
                        title, text = source[lang]
                        self.assertEqual(self.lang[lang][f"quest.{q['id']}.title"], title)
                        lines = [p for p in self.lang[lang][f"quest.{q['id']}.quest_desc"] if p]
                        self.assertEqual(len(lines), len(text.split('\n\n')))
                        for line, paragraph in zip(lines, text.split('\n\n')):
                            if quest_engine.TAG.search(paragraph):
                                # A paragraph with a link is rich text: the same words, the link's text for its markup.
                                shown = quest_engine.TAG.sub(lambda m: m.group(3)[1:], paragraph)
                                self.assertEqual(''.join(s.get('text', '') for s in json.loads(line)[1:]), shown)
                            else:
                                self.assertEqual(line, paragraph)
        # No item-filter mod: the tag tasks check one concrete item (Almost Unified's for metals). One
        # since the Ars guides became the Ars sector chapter (its archwood task names the log), the
        # Mekanism guides the Mekanism sector chapters (their steel task names IE's ingot), the Aether
        # guide the Aether chains (the Moa egg is the aether:obtain_egg advancement) and the Occultism
        # guides the Occultism sector chapters (the first ritual is its advancement now), and the last one
        # left since the Tombstone guide became the Tombstone chain (29/9): no guide has a tag task now.
        tagged = {q['key']: q['item'] for g in self.guides for q in g['quests'] if 'tag' in q}
        self.assertEqual(tagged, {})

    def test_story_node_grammar(self):
        # Hito hexagon 2, act finale hexagon 3, observed journey octagon 2, task square 1,
        # optional diamond 1, informative circle 0.75. Sizes 1, 2 and 3 keep icons on whole texels.
        self.assertEqual(GRAMMAR, {'finale': ('hexagon', 3.0), 'milestone': ('hexagon', 2.0), 'journey': ('octagon', 2.0),
                                   'task': ('square', 1.0), 'optional': ('diamond', 1.0), 'info': ('circle', 0.75)})
        roles = {}
        for data in self.story:
            finale = finale_of(data['quests'])
            c = self.chapters[data['chapter']]
            for source, q in zip(data['quests'], c['quests']):
                if source.get('role') == 'decor':   # a v2 toy: no node grammar, never content
                    self.assertEqual(q['shape'], 'none')
                    self.assertIn(q['size'], quest_art.DECOR_SIZES)
                    continue
                role = story_role(source, finale)
                roles[role] = roles.get(role, 0) + 1
                self.assertEqual((q['shape'], q['size']), GRAMMAR[role], source['key'])
        self.assertEqual(roles['finale'], 7)
        self.assertEqual(roles['journey'], 5)
        # QuestButton draws the icon at int(24 * size * 2/3) px: 16, 32 and 48 are whole multiples of 16.
        for size in (1.0, 2.0, 3.0):
            self.assertEqual(int(NODE_PX * size * 2 / 3) % 16, 0)

    def test_reading_width_on_every_chapter(self):
        # ViewQuestPanel: width = max(200, title + 54), raised to Chapter.default_min_width. 320 GUI px
        # still fits a 426 px wide GUI (1280x720 at GUI scale 3).
        self.assertEqual(self.book['min_width'], 320)
        for name, c in self.chapters.items():
            self.assertEqual(c['default_min_width'], 320, name)

    def test_paragraphs_keep_a_blank_line(self):
        for lang in LOCALES:
            for key, value in self.lang[lang].items():
                if key.endswith('.quest_desc'):
                    self.assertNotIn('', [value[0], value[-1]] if len(value) > 1 else value, key)
                    self.assertFalse(any(a == b == '' for a, b in zip(value, value[1:])), key)

    def test_story_copy_is_not_meta(self):
        # docs/design/quest-copy.md: the story never asks to confirm, names a key or explains the book.
        for data in self.story:
            for q in data['quests']:
                for lang in LOCALES:
                    copy_ = q[lang]
                    text = ' '.join([copy_['title'], *copy_['text']] if isinstance(copy_, dict) else copy_)
                    with self.subTest(quest=q['key'], lang=lang):
                        for phrase in quest_engine.BANNED[lang]:
                            self.assertNotIn(phrase, text.lower())
                        self.assertIsNone(quest_engine.HARD_KEYS.search(text))
                        self.assertNotRegex(text, r'(?i)\bconfirm')

    def test_chapter_images_use_real_textures_at_whole_texel_scales(self):
        for name, px in ART_PX.items():
            self.assertEqual(png_size(ART / (name + '.png')), px, name)
        emblems = {g['emblem'] for g in self.guides + self.sectors}
        ids = set()
        for chapter, c in self.chapters.items():
            if chapter in self.sector_names | self.v2_names:
                self.assertFalse(ids & {i['id'] for i in c.get('images', [])})
                ids.update(i['id'] for i in c.get('images', []))
                continue  # sector and v2 images: tools/test_sector_book.py and tools/test_presentation.py
            for img in c.get('images', []):
                with self.subTest(chapter=chapter, image=img['id']):
                    self.assertNotIn(img['id'], ids)
                    ids.add(img['id'])
                    if img['image'].startswith('entrelumen:textures/gui/quests/'):
                        name = img['image'].rsplit('/', 1)[1][:-4]
                        w, h = ART_PX[name]
                        scale = img['width'] * NODE_PX / w
                        self.assertIn(round(scale, 3), (1.0, 2.0, 3.0))
                        self.assertAlmostEqual(img['height'] * NODE_PX / h, scale, places=3)
                    elif img['image']:
                        self.assertIn(img['image'], emblems)
                        self.assertIn(round(img['width'] * NODE_PX / 16, 3), (2.0, 4.0))
                    else:
                        self.assertTrue(img['text_on_image'])
                    if img.get('text_on_image') or img.get('click_action'):
                        for lang in LOCALES:
                            self.assertTrue(self.lang[lang][f"image.{img['id']}.title"])
                    if img.get('text_on_image'):
                        # Same number of lines in every locale, so FTB keeps the text at one exact scale.
                        rows = {lang: self.lang[lang][f"image.{img['id']}.title"].count('\\n') for lang in LOCALES}
                        self.assertEqual(len(set(rows.values())), 1)
                        self.assertAlmostEqual(img['height'] * NODE_PX / 9 % 1, 0)

    def test_story_chapters_are_decorated_around_the_nodes(self):
        for data in self.story:
            c = self.chapters[data['chapter']]
            pictures = [i['image'].rsplit('/', 1)[-1] for i in c['images']]
            branch = data.get('book', {}).get('branch')
            with self.subTest(chapter=data['chapter']):
                if not branch:
                    self.assertIn(f"numeral_{data['act']}.png", pictures)
                    self.assertIn('divider.png', pictures)
                    emblem = next(i for i in c['images'] if i['image'].endswith(f"act_{data['act']}.png"))
                    self.assertEqual(emblem['click_action'], 'open_quest:' + stable_id('chapter:' + self.book['hub']['chapter']))
                if not branch:
                    self.assertIn('sun_heliodor.png' if data['chapter'] != 'solsticio' else 'medallion.png', pictures)
                if data['chapter'] not in ('solsticio',):
                    self.assertGreaterEqual(pictures.count('corner.png'), 4)
        # The final chapter draws the Sun of Heliodor at 3x behind the ring of its eleven missions.
        sol = self.chapters['solsticio']
        sun = next(i for i in sol['images'] if i['image'].endswith('sun_heliodor.png'))
        self.assertEqual((sun['x'], sun['y'], round(sun['width'] * NODE_PX)), (0.0, 0.0, 384))
        ring = [q for q in sol['quests'] if 5.5 < math.hypot(q['x'], q['y']) < 6.5]
        self.assertEqual(len(ring), 6)
        self.assertTrue(all(q['shape'] == 'hexagon' for q in sol['quests']))

    def test_hub_presents_six_acts_and_the_guides(self):
        hub = self.chapters[self.book['hub']['chapter']]
        self.assertEqual(hub['quests'], [])
        opens = [i['click_action'] for i in hub['images'] if i.get('click_action')]
        acts = {}
        for data in self.story:
            if data.get('act') and not data.get('book', {}).get('branch'):
                acts.setdefault(data['act'], data['chapter'])
        for act, chapter in acts.items():
            self.assertIn('open_quest:' + stable_id('chapter:' + chapter), opens)
        firsts = {}
        for g in sorted(self.guides + self.sectors, key=lambda x: group_order(x, self.book)):
            firsts.setdefault(g['group'], g['chapter'])
        for chapter in firsts.values():
            self.assertIn('open_quest:' + stable_id('chapter:' + chapter), opens)
        self.assertEqual(len(opens), 6 + 5)
        linked = [l['linked_quest'] for l in hub['quest_links']]
        self.assertTrue(all(q in self.quests for q in linked))
        self.assertEqual(linked[0], stable_id('quest:voices_heart'))
        self.assertEqual(hub['autofocus_id'], hub['quest_links'][0]['id'])
        self.assertEqual(len(linked), 7)

    def test_rewards_are_modest_team_rewards(self):
        data = json.loads(self.files[OUT / 'data.snbt'])
        self.assertTrue(data['default_reward_team'])
        self.assertEqual(data['default_autoclaim_rewards'], 'disabled')
        table = self.book['rewards']
        items = [i for i, _ in table['story']['milestone_item'] + table['story']['finale_item']]
        self.assertFalse([i for i in items if i.startswith('entrelumen:')])
        self.assertTrue(all(n <= 8 for _, n in table['story']['milestone_item'] + table['story']['finale_item']))
        ids = set()
        total = {'guide': 0, 'story': 0}
        for chapter, c in self.chapters.items():
            sector = chapter in self.sector_names
            for q in c['quests']:
                task = q['tasks'][0]
                if task['type'] == 'checkmark':
                    self.assertEqual(q['rewards'], [], chapter)  # a free click never pays
                for r in q['rewards']:
                    self.assertNotIn(r['id'], ids)
                    ids.add(r['id'])
                    # Sector chapters add tables, secret toasts and capstone fanfares (test_sector_book.py).
                    allowed = ('xp', 'item', 'choice', 'random', 'loot', 'toast', 'command') if sector else ('xp', 'item')
                    self.assertIn(r['type'], allowed)
                    if r['type'] == 'xp':
                        total['guide' if chapter.startswith(('guide_', 'sector_')) else 'story'] += r['xp']
        for data in self.guides:
            for q in data['quests']:
                c = self.quests[stable_id('quest:' + q['key'])]
                if q.get('type', 'item') != 'checkmark':
                    self.assertEqual(c['rewards'], [{'id': stable_id(f"reward:{q['key']}:xp"), 'type': 'xp', 'xp': table['guides']['xp'][data['act']]}])
        # The whole book, if nothing were spent, stays under 80,000 points for the full ~6,500-quest book
        # (Elias, 27/9: raise the cap rather than pay XP only on milestones; raised again on 28/9 as the
        # depth waves grew the book); 5 to 15 points per guide quest, times its role: still less than a few
        # mob kills each.
        self.assertLess(total['guide'] + total['story'], 80000)
        self.assertTrue(all(x <= 15 for x in table['guides']['xp'].values()))

    def test_every_quest_has_one_colour_tag_and_the_theme_colours_them(self):
        names = {'entrelumen_story', 'entrelumen_guide', 'entrelumen_optional'}
        roles = {f'entrelumen_{r}' for r in quest_engine.ROLES}
        for chapter, c in self.chapters.items():
            for q in c['quests']:
                if chapter in self.sector_names or q['tags'] == ['entrelumen_decor']:
                    self.assertEqual(len(set(q['tags']) & roles), 1)  # the role colours the node (a v2 toy too)
                    continue
                self.assertEqual(len(set(q['tags']) & names), 1)
                self.assertEqual('entrelumen_optional' in q['tags'], q.get('optional', False))
        theme = self.files[THEME]
        for name in names | roles:
            self.assertIn(f'[#{name}]', theme)
        colours = len(self.book['colors']) + len([r for r in self.book['role_colors'] if not r.startswith('_')])
        self.assertEqual(len(re.findall(r'^quest_locked_color: #[0-9A-F]{8}$', theme, re.M)), colours)
        self.assertEqual(len(re.findall(r'^quest_not_started_color: #[0-9A-F]{8}$', theme, re.M)), colours)

    def test_guide_medallions(self):
        for data in self.guides:
            c = self.chapters[data['chapter']]
            pictures = [i['image'] for i in c['images']]
            if data.get('medallion') is False:   # a v2 guide may leave the emblem to its scene
                continue
            # A v2 guide draws its art after the two (tools/quest_v2.py).
            self.assertEqual(pictures[:2], ['entrelumen:textures/gui/quests/medallion.png', data['emblem']])
            if data.get('presentation', 1) < 2:
                self.assertEqual(len(pictures), 2)
            medal, emblem = c['images'][:2]
            self.assertEqual((medal['x'], medal['y']), (emblem['x'], emblem['y']))
            if isinstance(data.get('medallion'), dict):
                continue
            left = min(q['x'] - q['size'] * VISUAL / 2 for q in c['quests'] if q['tags'] != ['entrelumen_decor'])
            self.assertLess(medal['x'] + medal['width'] * VISUAL / 2, left)

    def test_guide_without_concrete_item_rejected(self):
        guides = copy.deepcopy(self.guides)
        # No guide keeps a tag task since 29/9, so make one: a tag without its concrete item must fail.
        quest = next(q for g in guides for q in g['quests'] if 'item' in q)
        quest['tag'] = 'c:ingots/iron'
        del quest['item']
        with self.assertRaisesRegex(AssertionError, 'concrete item'):
            generate_book(self.story, guides, self.book)

    def test_guide_links_open_their_quest_or_chapter(self):
        # Guides link like sectors (28/9): a paragraph with [quest:key|text] or [chapter:name|text] becomes a
        # rich-text line whose click opens the target; other paragraphs keep their & codes as plain text.
        desc = self.lang['en_us'][f"quest.{stable_id('quest:qol_inventory_welcome')}.quest_desc"]
        self.assertIn('Big pack', desc[0])
        link = json.loads(desc[-1])[2]
        self.assertEqual(link['clickEvent'], {'action': 'change_page', 'value': stable_id('quest:qol_inventory_magnet')})
        desc = self.lang['es_es'][f"quest.{stable_id('quest:qol_world_close')}.quest_desc"]
        self.assertEqual(json.loads(desc[-1])[2]['clickEvent']['value'], stable_id('chapter:sector_gadgets'))
        ctx = {'items': set(), 'names': set(), 'keys': set(), 'textures': set(),
               'resolve_quest': lambda t, w: stable_id('quest:' + t), 'resolve_chapter': lambda t, w: stable_id('chapter:' + t)}
        self.assertEqual(guide_lines('Plain &etext&r.\n\nMore.', 'en_us', ctx, 't'), ['Plain &etext&r.', '', 'More.'])
        for bad in ('&eBold&r and [quest:a|a link]', 'A [item:minecraft:stone|Stone].'):
            with self.subTest(text=bad), self.assertRaises(AssertionError):
                guide_lines(bad, 'en_us', ctx, 't')
        keys = {q['key'] for g in self.guides + self.sectors for q in g['quests']}
        names = {g['chapter'] for g in self.guides} | self.sector_names
        for lang, target, error in (('es_es', 'qol_inventory_sort', 'EN and ES link|differ between languages'),
                                    (None, 'no_such_quest', 'unknown quest')):
            guide = copy.deepcopy(next(g for g in self.guides if g['chapter'] == 'guide_qol_inventory'))
            welcome = next(q for q in guide['quests'] if q['key'] == 'qol_inventory_welcome')
            for code in ([lang] if lang else LOCALES):
                # Presentation v2 (30/9): the link sits in one of the quest's text paragraphs.
                welcome[code]['text'] = [p.replace('qol_inventory_magnet', target) for p in welcome[code]['text']]
            with self.subTest(target=target), self.assertRaisesRegex(AssertionError, error):
                generate_guide(guide, 'x', 0, self.book, {l: {} for l in LOCALES}, set(), keys, names)

    def test_held_counts_carry_a_note_in_both_languages(self):
        # F40: a non-consuming item task counts the most items held in the main inventory at one moment, so a count
        # of two or more says so, in the quest's own words or in the note the generator adds.
        checked = 0
        for data in self.story + self.guides + self.sectors:
            for q in data['quests']:
                if not quest_engine.held_counts(q):
                    continue
                checked += 1
                qid = stable_id('quest:' + q['key'])
                for lang, said in (('en_us', 'at once'), ('es_es', 'a la vez')):
                    with self.subTest(quest=q['key'], lang=lang):
                        self.assertIn(said, '\n'.join(self.lang[lang][f'quest.{qid}.quest_desc']))
        self.assertGreater(checked, 100)

    def test_auto_notes(self):
        def quest(*tasks, en=('Eight stones.',), es=('Ocho piedras.',)):
            return {'key': 'x', 'tasks': list(tasks), 'en_us': {'title': 'Stone', 'text': list(en)},
                    'es_es': {'title': 'Piedra', 'text': list(es)}}
        notes = quest_engine.auto_notes(quest({'item': 'minecraft:stone', 'count': 8}))
        self.assertEqual(notes, {'en_us': ["[note] Carry all 8 at once; placed or installed ones don't count."],
                                 'es_es': ['[note] Tené las 8 unidades encima a la vez; las colocadas o instaladas no cuentan.']})
        self.assertEqual(quest_engine.auto_notes(quest({'item': 'a:b', 'count': 2}, {'item': 'a:c', 'count': 3}))['en_us'],
                         ["[note] Carry each full amount at once; placed or installed ones don't count."])
        for tasks in ([{'item': 'a:b', 'count': 64, 'consume': True}], [{'item': 'a:b'}],
                      [{'type': 'kill', 'entity': 'a:b', 'value': 5}]):
            self.assertEqual(quest_engine.auto_notes(quest(*tasks)), {'en_us': [], 'es_es': []})
        said = quest({'item': 'a:b', 'count': 4}, en=('Hold all four at once.',), es=('Tené las cuatro a la vez.',))
        self.assertEqual(quest_engine.auto_notes(said), {'en_us': [], 'es_es': []})
        # F49: unbound keys cited, one or several, unless the text already says where to set them.
        unbound = sorted(quest_engine.unbound_keys())[:2]
        one = quest({'type': 'checkmark'}, en=(f'Press [key:{unbound[0]}].',), es=(f'Tocá [key:{unbound[0]}].',))
        self.assertEqual(quest_engine.auto_notes(one), {'en_us': ['[note] This key ships unbound: set it in Controls.'],
                                                        'es_es': ['[note] Esta tecla viene sin asignar: asignala en Controles.']})
        two = quest({'type': 'checkmark'}, en=(f'[key:{unbound[0]}] or [key:{unbound[1]}].',),
                    es=(f'[key:{unbound[0]}] o [key:{unbound[1]}].',))
        self.assertEqual(quest_engine.auto_notes(two)['es_es'],
                         ['[note] Estas teclas vienen sin asignar: asignalas en Controles.'])
        told = quest({'type': 'checkmark'}, en=(f'Bind [key:{unbound[0]}] in Controls.',),
                     es=(f'Asigná [key:{unbound[0]}] en Controles.',))
        self.assertEqual(quest_engine.auto_notes(told), {'en_us': [], 'es_es': []})
        bound = quest({'type': 'checkmark'}, en=('Press [key:key.jump].',), es=('Tocá [key:key.jump].',))
        self.assertEqual(quest_engine.auto_notes(bound), {'en_us': [], 'es_es': []})
        # A note that would push a one-page text past MAX_PAGE_CHARS opens its own page, in both languages.
        long = quest({'item': 'minecraft:stone', 'count': 8}, en=('Stones. ' * 38 + 'End.',), es=('Piedras. ' * 34 + 'Fin.',))
        ctx = {'items': set(), 'names': set(), 'keys': set(), 'textures': set(), 'accent': '#FFFFFF', 'presentation': 2}
        texts = quest_engine.with_notes(long, 't', ctx)
        self.assertEqual([t[1] for t in texts.values()], ['{page}', '{page}'])
        self.assertEqual(quest_engine.with_notes(quest({'item': 'minecraft:stone', 'count': 8}), 't', ctx)['en_us'][1:],
                         ["[note] Carry all 8 at once; placed or installed ones don't count."])

    def test_unbound_keys_list_and_check(self):
        # tools/unbound_keys.json never lists a key defaultoptions binds, and an unbound key without a word on where to
        # set it fails the book (generate_quests.check_unbound_keys).
        import generate_quests
        bound = set()
        for line in (ROOT / 'pack/config/defaultoptions/keybindings.txt').read_text(encoding='utf-8').splitlines():
            if line.startswith('key_'):
                name, value = line[4:].split(':', 1)
                if not value.startswith('key.keyboard.unknown'):
                    bound.add(name)
        self.assertFalse(bound & quest_engine.unbound_keys())
        key = sorted(quest_engine.unbound_keys())[0]
        line = json.dumps(['', {'text': '', 'extra': [{'keybind': key}]}], separators=(',', ':'))
        bad = {'en_us': {'quest.A.quest_desc': [line]}, 'es_es': {'quest.A.quest_desc': [line, 'Asignala en Controles.']}}
        with self.assertRaisesRegex(AssertionError, r'quest\.A\.quest_desc \(en_us\)'):
            generate_quests.check_unbound_keys(bad)
        bad['en_us']['quest.A.quest_desc'].append('Set it in Controls.')
        generate_quests.check_unbound_keys(bad)


if __name__ == '__main__':
    unittest.main()
