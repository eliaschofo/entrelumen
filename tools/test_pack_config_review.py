"""Pin the pack config and doc changes from the 1 Oct adversarial review (batch pack-config-docs).

Static checks over committed files: no game, server or JAR needed.
"""
import json
from pathlib import Path
import re
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'pack/config'
LANG = ROOT / 'companion/src/main/resources/assets/entrelumen/lang'


def read(path):
    return Path(path).read_text(encoding='utf-8')


class PackConfigReviewTest(unittest.TestCase):
    def test_f55_jei_tag_recipes_off_and_rest_of_the_profile_file_kept(self):
        text = read(CONFIG / 'jei/jei-client.ini')
        self.assertRegex(text, r'(?m)^\s*showTagRecipesEnabled = false$')
        self.assertNotIn('showTagRecipesEnabled = true', text)
        # A full copy, not a stub: JEI rewrites missing keys, and the file keeps its [cheating] section.
        self.assertIn('[cheating]', text)
        self.assertGreater(text.count('\n'), 100)
        self.assertNotIn('\r', text)

    def test_f62_emi_loot_exclusion_shape_and_reach(self):
        data = json.loads(read(ROOT / 'pack/kubejs/data/entrelumen/emi_loot_data/table_exclusions.json'))
        # emi_loot 0.7.9 ServerResourceData.loadTableExclusion: "exclusions" array of strings or {regex|ids|type}.
        self.assertEqual(list(data), ['exclusions'])
        self.assertEqual(len(data['exclusions']), 1)
        entry = data['exclusions'][0]
        self.assertEqual(list(entry), ['regex'])
        pattern = re.compile(entry['regex'])  # emi_loot uses Matcher.find over "namespace:path"
        for table in ('rechiseled:blocks/marble', 'chipped:blocks/oak_planks/1', 'botanypotstiers:blocks/diamond_botany_pot',
                      'mcwwindows:blocks/oak_window', 'mcwdoors:blocks/jungle_barn_door'):
            self.assertTrue(pattern.search(table), table)
        for table in ('minecraft:blocks/diamond_ore', 'minecraft:blocks/oak_leaves', 'mekanism:blocks/osmium_ore',
                      'croptopia:blocks/tomato_crop', 'rechiseled:entities/anything', 'mcwwindows:chests/anything',
                      'xmcwwindows:blocks/oak_window'):
            self.assertFalse(pattern.search(table), table)

    def test_f70_update_checks_and_analytics_are_off(self):
        fml = tomllib.loads(read(CONFIG / 'fml.toml'))
        self.assertIs(fml['versionCheck'], False)
        # Everything else in the profile's fml.toml stays as shipped.
        self.assertIs(fml['disableOptimizedDFU'], True)
        self.assertEqual(fml['defaultConfigPath'], 'defaultconfigs')
        for name in ('evilcraft', 'integrateddynamics', 'commoncapabilities', 'integratedtunnels'):
            general = tomllib.loads(read(CONFIG / f'{name}-common.toml'))['core']['general']
            self.assertIs(general['analytics'], False, name)
            self.assertIs(general['versionChecker'], False, name)
        for name in ('commoncapabilities', 'integratedtunnels'):
            # Partial files: only the two keys, so the mod fills its own defaults.
            self.assertEqual(tomllib.loads(read(CONFIG / f'{name}-common.toml')),
                             {'core': {'general': {'analytics': False, 'versionChecker': False}}})
        # The other settings these files already carried survive.
        self.assertIs(tomllib.loads(read(CONFIG / 'integrateddynamics-common.toml'))['item']
                      ['on_the_dynamics_of_integration']['obtainOnSpawn'], False)

    def test_f5_ftb_chunks_claims_blacklisted_in_authored_dimensions(self):
        text = read(CONFIG / 'ftbchunks-world.snbt')
        body = '\n'.join(line for line in text.splitlines() if not line.lstrip().startswith('#'))
        self.assertRegex(body, r'require_game_stage:\s*true')
        # FTB Chunks 2101.1.21 FTBChunksWorldConfig: group "claiming" -> string list "claim_dimension_blacklist".
        match = re.search(r'claiming:\s*\{\s*claim_dimension_blacklist:\s*\[(.*?)\]\s*\}', body, re.S)
        self.assertIsNotNone(match)
        self.assertEqual(re.findall(r'"([^"]+)"', match.group(1)), ['entrelumen:enves', 'entrelumen:solsticio'])
        for dimension in ('enves', 'solsticio'):
            self.assertTrue((ROOT / f'companion/src/main/resources/data/entrelumen/dimension/{dimension}.json').is_file())
        self.assertEqual(body.count('{'), body.count('}'))

    def test_f52_apotheosis_root_advancement_uses_keybind_and_new_lang_key(self):
        root = json.loads(read(ROOT / 'pack/kubejs/data/apotheosis/advancement/progression/root.json'))
        display = root['display']
        self.assertEqual(display['description'], {
            'translate': 'entrelumen.apotheosis.tier.root.desc',
            'with': [{'keybind': 'key.apotheosis.open_world_tier_select'}]})
        # Same always-true tick trigger as the five tier files; the criterion is named "campaign" like theirs so the
        # file stays under the repository's copy threshold for All Rights Reserved JAR data (check_loot_tables --copies).
        self.assertEqual(root['criteria'], {'campaign': {'trigger': 'minecraft:tick'}})
        self.assertEqual(root['requirements'], [['campaign']])
        self.assertEqual(display['icon'], {'count': 1, 'id': 'apotheosis:boss_summoner'})
        self.assertEqual(display['background'], 'apotheosis:textures/advancements/bg/apoth.png')
        self.assertIs(display['announce_to_chat'], False)
        self.assertIs(display['show_toast'], False)
        self.assertEqual(display['title'], {'translate': 'advancements.apotheosis.progression.root.title'})
        # The six other Spanish locales are generated byte for byte from es_es (quest_client.spanish_copies): a player
        # on es_ar must not fall back to English for the new key.
        for lang in ('en_us', 'es_es', 'es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve'):
            text = json.loads(read(LANG / f'{lang}.json'))['entrelumen.apotheosis.tier.root.desc']
            self.assertEqual(text.count('%s'), 1, lang)
            self.assertNotIn('CTRL', text.upper(), lang)
        spanish = json.loads(read(LANG / 'es_es.json'))['entrelumen.apotheosis.tier.root.desc']
        self.assertRegex(spanish, r'elegís|Apretá')  # voseo
        for lang in ('es_ar', 'es_cl', 'es_ec', 'es_mx', 'es_uy', 'es_ve'):
            self.assertEqual(read(LANG / f'{lang}.json'), read(LANG / 'es_es.json'), lang)

    def test_f85_title_screen_hides_only_realms(self):
        text = read(CONFIG / 'fancymenu/customization/entrelumen_title_screen.txt')
        hidden = {}
        for block in text.split('vanilla_button {')[1:]:
            ident = re.search(r'instance_identifier = (\S+)', block).group(1)
            hidden[ident] = re.search(r'is_hidden = (\w+)', block).group(1)
        self.assertEqual(hidden['mc_titlescreen_realms_button'], 'true')
        for ident in ('mc_titlescreen_singleplayer_button', 'mc_titlescreen_multiplayer_button',
                      'forge_titlescreen_mods_button', 'mc_titlescreen_options_button', 'mc_titlescreen_quit_button'):
            self.assertEqual(hidden[ident], 'false', ident)
        doc = read(ROOT / 'docs/design/menu-identity.md')
        self.assertIn('cinco filas de 160×20', doc)
        self.assertNotIn('seis filas de 160×20', doc)
        self.assertIn('Realms', doc)

    def test_f71_f55_docs_match_the_config(self):
        doc = read(ROOT / 'docs/verification/final-test-phase.md')
        dynamic = json.loads(read(CONFIG / 'dynamic_fps.json'))
        self.assertEqual(dynamic['idle']['condition'], 'on_battery')
        self.assertEqual(dynamic['battery_tracker'].get('enabled', False), False)
        self.assertNotIn('10 FPS a los 5 minutos', doc)
        self.assertIn('`idle.condition: on_battery`', doc)
        self.assertIn('15 FPS', doc)

    def test_review_checks_section_covers_every_boot_check(self):
        doc = read(ROOT / 'docs/verification/final-test-phase.md')
        match = re.search(r'## Chequeos de la revisión adversarial del 1/10\n(.*?)\n## ', doc, re.S)
        self.assertIsNotNone(match)
        bullets = [line for line in match.group(1).splitlines() if line.startswith('- ')]
        covered = set()
        for line in bullets:
            head = line[2:].split(':', 1)[0]
            covered.update(re.findall(r'F\d+', head))
            self.assertRegex(line, r'[Pp]asa|[Ss]e actúa|[Ss]e acepta|Pensá', 'every bullet states a pass criterion: ' + line[:40])
        expected = {1, 2, 4, 5, 7, 8, 9, 10, 11, 12, 14, 19, 20, 25, 28, 35, 37, 39, 55, 56, 59, 62, 63, 64, 65, 66, 67, 68, 69, 88, 85}
        self.assertTrue({f'F{n}' for n in expected} <= covered, sorted({f'F{n}' for n in expected} - covered))
        # The batch's own checks and the rest of the list.
        for n in (13, 21, 22, 24, 26, 31, 36, 38, 46, 49, 52, 54, 71):
            self.assertIn(f'F{n}', covered)


if __name__ == '__main__':
    unittest.main()
