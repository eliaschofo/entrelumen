# First-hour runtime audit

`server_scripts/entrelumen_runtime_audit.js` observes registries and final loaded recipes through `ServerEvents.afterRecipes`. It runs during server data loading/reload; no player or operator action is necessary. It does not edit recipes, grant items, modify teams, complete quests or change campaign state.

The output consists of `[ENTRELUMEN_AUDIT]` JSON lines in the KubeJS server log (also forwarded to the main server log): one begin, selected item records, exact recipe/output records with alternative recipe IDs, and a terminal summary. An absent summary is failure, not a pass. A source signature makes stale results distinguishable after the quests/projects change.

```powershell
python tools/check_runtime_content.py --sync
python tools/check_runtime_content.py --log 'INSTANCE/logs/kubejs/server.log'
```

Use the actual KubeJS log location created by the running instance, or its main `logs/latest.log`. The checker validates the last audit cycle and requires exact current-source coverage. An error after that cycle starts fails validation; errors before it still need normal server-log review. Without `--log` the command checks source coverage only and never claims the game was tested.

Targets come from the first-hour chapter's item/icon references, act-one project delivery items and corresponding companion recipes. Farmer's Delight `flint_knife` and `cutting_board` recipe IDs/results were read from its installed 1.3.3 JAR. The selected alternative recipes are capped at 64 IDs per output with a total count retained. No full registry export or per-tick work is performed.

## Verified API references

The relevant classes are present in the installed KubeJS `2101.7.2-build.374` JAR. The official 2101 source declares:

- [`ServerEvents.afterRecipes`](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/event/ServerEvents.java).
- [`AfterRecipesLoadedKubeEvent.countRecipes` and `forEachRecipe`](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/recipe/AfterRecipesLoadedKubeEvent.java), which inspect loaded recipe holders.
- [`RecipeFilter`](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/recipe/filter/RecipeFilter.java): `id` and `output` combine with AND.
- [`RecipeLikeKJS`](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/core/RecipeLikeKJS.java): `@RemapPrefixForJS("kjs$")` exposes `getOrCreateId()`.
- [`ItemWrapper.exists`](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/wrapper/ItemWrapper.java): registry membership without creating an item stack.

Full runtime verification remains mandatory after installation. This audit proves references and recipe availability, not recipe usability, progression balance, performance or quest synchronization.

## Farming for Blockheads compatibility guard

`entrelumen_market_compat.js` fixes a concrete recipe-load failure in Farming for Blockheads 21.1.13. Its JAR includes 745 market integration recipes without load conditions. The observed server log contains 705 failed recipe decodes across 33 result namespaces: largest groups are BYG 117, Biomes We've Gone 100, Pam crops 96, Croptopia 85 and Pam trees 49. One failure belongs to installed Actually Additions: its current registry no longer contains `actuallyadditions:coffee_seeds`.

The script uses only the exact market recipe IDs and result item IDs extracted from that pinned JAR. On each data load it checks actual `Item.exists` membership, then emits an original condition-false stub only for missing results. Existing-result recipes receive no override: their original prices, presets and bodies are preserved. Adding an item/mod later automatically restores its untouched upstream recipe on reload. There are no JAR edits, logging filters or inventory/progression operations.

The guard runs in `generateData('after_mods')`, not `ServerEvents.recipes`: [ServerScriptManager](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/server/ServerScriptManager.java) inserts the generated resource pack after mod resources and populates it during resource-manager construction. [RecipesKubeEvent](https://github.com/KubeJS-Mods/KubeJS/blob/2101/src/main/java/dev/latvian/mods/kubejs/recipe/RecipesKubeEvent.java) checks `ConditionalOps` before deserialization. [NeoForge data-load conditions](https://docs.neoforged.net/docs/1.21.1/resources/server/conditions/) specify the same early skip and the `neoforge:false` condition. The generated stub retains `type` because KubeJS checks that field before conditions. API symbols were also checked in the actual build-374 JAR.

Regenerate the factual recipe-ID map after intentionally updating Farming for Blockheads:

```powershell
python tools/check_runtime_content.py --sync-market
```

The current baseline should report **745 checked, 705 skipped and 40 untouched**; runtime registry changes may legitimately alter the latter counts. `[ENTRELUMEN_MARKET_COMPAT]` records show these counts plus namespace groups and a terminal after-recipes check that no invalid market recipe survived. `--log` now requires this receipt as well as the first-hour receipt, rejects stale source hashes and remaining market parse errors. Actual restart/reload verification is still required.
