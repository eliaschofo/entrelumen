"""Static checks of how the ENTRELUMEN KubeJS scripts use the shared recipe helpers (no game, no JARs)."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / 'pack/kubejs/server_scripts'
TOOLS = SCRIPTS / 'entrelumen_recipe_tools.js'
SPEC = importlib.util.spec_from_file_location('recipe_tools_under_test', Path(__file__).with_name('generate_recipe_tools.py'))
generator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(generator)


def scripts():
    return [path for path in sorted(SCRIPTS.glob('*.js')) if path != TOOLS]


class RecipeToolsTest(unittest.TestCase):
    def test_tools_script_is_current_and_loads_first(self):
        text = TOOLS.read_text(encoding='utf-8')
        self.assertEqual(text, generator.render())
        self.assertTrue(text.startswith('// priority: 1000\n'))

    def test_no_script_adds_or_replaces_a_recipe_without_the_helpers(self):
        # A bare event.custom(...).id(...) inside a loop is the shape that lost a native recipe when KubeJS rejected one row.
        for path in scripts():
            with self.subTest(script=path.name):
                text = path.read_text(encoding='utf-8')
                self.assertNotRegex(text, r'event\.custom\(', 'use entrelumenReplaceRecipe / entrelumenAddRecipe')
                self.assertNotRegex(text, r'event\.remove\(\{id: row\.id\}\)')

    def test_helpers_build_the_replacement_before_removing_the_native_recipe(self):
        text = TOOLS.read_text(encoding='utf-8')
        body = text[text.index('function entrelumenCustomRecipe'):text.index('function entrelumenReplaceRecipe')]
        self.assertLess(body.index('event.custom(json)'), body.index('event.remove({id: id})'))
        self.assertIn('creationError', body)  # a schema rejection comes back as an unregistered recipe, not an exception
        self.assertIn("'failed-row'", text)

    def test_rows_that_change_recipes_report_failed_rows(self):
        for path in scripts():
            text = path.read_text(encoding='utf-8')
            if re.search(r'entrelumen(Replace|Add)Recipe\(', text):
                with self.subTest(script=path.name):
                    self.assertIn('failedRows', text)
                    self.assertIn('registered-with-failed-rows', text)


if __name__ == '__main__':
    unittest.main()
