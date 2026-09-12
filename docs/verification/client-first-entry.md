# First client entry — 2026-09-12

Observed on the reference i7-8750H / GTX 1070 / 16 GB PC through the CurseForge-created ENTRELUMEN profile. Minecraft 1.21.1, NeoForge 21.1.249, 119 curated dependency JARs and the companion build with SHA-256 `5AF29EE1E9DFD88CF3FC4D7DBDC053B243CD3D9DE184B388C21311A6F5F2D4CC`.

## Evidence

- The client reached the main menu and entered `ENTRELUMEN-QA-functional`, a separate survival world with commands enabled for functional QA, seed `71942026`.
- Actual resource reload enabled `file/entrelumen`; the original Atlas, lens, survey and signal icons rendered in the quest graph.
- The recipe audit passed for 22 first-hour item IDs and six recipe/output pairs, signature `1a80d86a6dce28e7`.
- The market compatibility guard checked 745 bundled definitions, excluded 705 outputs absent from this selection and retained 40 valid definitions. The complete registry contained 107 market recipes. Both KubeJS server scripts loaded with zero script errors and warnings.
- FTB Quests loaded one group, one chapter and 25 quests with two language tables. The inventory quest button opened the chapter. The Spanish Atlas quest rendered readable, wrapped text.
- Save and quit completed: the integrated server logged all dimensions saved at 16:45:47 Argentina time, followed by normal client shutdown at 16:46:17.

## Findings being corrected

- The initial chapter viewport is centered too far below the welcome quest. Several crossing dependency lines make the route harder to read.
- FancyMenu's customization toolbar is visible; player defaults now disable it, pending the next client launch.
- The Atlas screen needs a bootstrap path before the first project awards the portable item. The companion is adding a task-button entry point.
- The grave-accent quest shortcut did not open the screen through native keyboard injection on this Spanish keyboard layout. The inventory button worked. Shortcut verification remains pending; this is not evidence that the physical keyboard shortcut itself is broken.

This session verifies startup, resource loading, selected recipe references and one Spanish quest view. It does **not** verify a survival first-hour playthrough, the complete campaign, final balance, English rendering, multiplayer client behavior, or the performance targets. Window capture was tested at 1028 × 803 and then a maximized window; no FPS benchmark was collected.
