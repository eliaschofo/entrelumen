"""Quest-book contracts of 25 September 2026 (docs/design/quest-book.md): hub, guides, groups,
node grammar, reading width, chapter images, rewards and theme. Static only: FTB loading and the
look inside the client are runtime questions."""
import copy, json, math, re, struct, unittest
from generate_quests import (ROOT, OUT, THEME, GRAMMAR, NODE_PX, VISUAL, ART_PX, LOCALES, generate_book, load_book,
                             load_chapters, load_guides, stable_id, finale_of, story_role)

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
        cls.files = generate_book(cls.story, cls.guides, cls.book)
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

    def test_book_structure_hub_story_and_five_groups(self):
        self.assertEqual(len(self.chapters), 1 + len(self.story) + 93)
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
        for group in self.book['groups']:
            members = [g['chapter'] for g in self.guides if g['group'] == group['id']]
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
                    self.assertEqual(task['type'], kind)
                    if kind == 'item':
                        self.assertEqual((task['item'], task['count'], task['consume_items']),
                                         ({'id': source['item'], 'count': 1}, source.get('count', 1), False))
                    if kind == 'advancement':
                        self.assertEqual(task['advancement'], source['advancement'])
                    if kind == 'dimension':
                        self.assertEqual(task['dimension'], source['dimension'])
                    for lang in LOCALES:
                        title, text = source[lang]
                        self.assertEqual(self.lang[lang][f"quest.{q['id']}.title"], title)
                        self.assertEqual([p for p in self.lang[lang][f"quest.{q['id']}.quest_desc"] if p], text.split('\n\n'))
        # No item-filter mod: the five tag tasks check one concrete item (Almost Unified's for metals).
        tagged = {q['key']: q['item'] for g in self.guides for q in g['quests'] if 'tag' in q}
        self.assertEqual(tagged['mkb_steel'], 'immersiveengineering:ingot_steel')
        self.assertEqual(tagged['occ_crusher'], 'immersiveengineering:ingot_silver')
        self.assertEqual(len(tagged), 5)

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

    def test_chapter_images_use_real_textures_at_whole_texel_scales(self):
        for name, px in ART_PX.items():
            self.assertEqual(png_size(ART / (name + '.png')), px, name)
        emblems = {g['emblem'] for g in self.guides}
        ids = set()
        for chapter, c in self.chapters.items():
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
        for g in self.guides:
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
            for q in c['quests']:
                task = q['tasks'][0]
                if task['type'] == 'checkmark':
                    self.assertEqual(q['rewards'], [], chapter)  # a free click never pays
                for r in q['rewards']:
                    self.assertNotIn(r['id'], ids)
                    ids.add(r['id'])
                    self.assertIn(r['type'], ('xp', 'item'))
                    if r['type'] == 'xp':
                        total['guide' if chapter.startswith('guide_') else 'story'] += r['xp']
        for data in self.guides:
            for q in data['quests']:
                c = self.quests[stable_id('quest:' + q['key'])]
                if q.get('type', 'item') != 'checkmark':
                    self.assertEqual(c['rewards'], [{'id': stable_id(f"reward:{q['key']}:xp"), 'type': 'xp', 'xp': table['guides']['xp'][data['act']]}])
        # The whole book, if nothing were spent, is under 18,000 points (about level 75) for 150+ hours,
        # 5 to 15 points per guide quest: less than a few mob kills each.
        self.assertLess(total['guide'] + total['story'], 18000)
        self.assertTrue(all(x <= 15 for x in table['guides']['xp'].values()))

    def test_every_quest_has_one_colour_tag_and_the_theme_colours_them(self):
        names = {'entrelumen_story', 'entrelumen_guide', 'entrelumen_optional'}
        for c in self.chapters.values():
            for q in c['quests']:
                self.assertEqual(len(set(q['tags']) & names), 1)
                self.assertEqual('entrelumen_optional' in q['tags'], q.get('optional', False))
        theme = self.files[THEME]
        for name in names:
            self.assertIn(f'[#{name}]', theme)
        self.assertEqual(len(re.findall(r'^quest_locked_color: #[0-9A-F]{8}$', theme, re.M)), 3)
        self.assertEqual(len(re.findall(r'^quest_not_started_color: #[0-9A-F]{8}$', theme, re.M)), 3)

    def test_guide_medallions(self):
        for data in self.guides:
            c = self.chapters[data['chapter']]
            pictures = [i['image'] for i in c['images']]
            self.assertEqual(pictures, ['entrelumen:textures/gui/quests/medallion.png', data['emblem']])
            medal, emblem = c['images']
            self.assertEqual((medal['x'], medal['y']), (emblem['x'], emblem['y']))
            left = min(q['x'] - q['size'] * VISUAL / 2 for q in c['quests'])
            self.assertLess(medal['x'] + medal['width'] * VISUAL / 2, left)

    def test_guide_without_concrete_item_rejected(self):
        guides = copy.deepcopy(self.guides)
        quest = next(q for g in guides for q in g['quests'] if q['key'] == 'mkb_steel')
        del quest['item']
        with self.assertRaisesRegex(AssertionError, 'concrete item'):
            generate_book(self.story, guides, self.book)


if __name__ == '__main__':
    unittest.main()
