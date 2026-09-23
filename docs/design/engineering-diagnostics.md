# Engineering module: diagnose an Ark installation

This bounded extension gives `entrelumen:engineering_module` a direct use. It implements the engineering diagnostic already proposed in `mod-integration.md`; it does not complete the other five module experiences or postgame mastery.

Use the placed module with an empty main hand. The server reports the current player's team campaign: construction prerequisites, World Network, the recorded End journey, the active commissioning batch and credited/missing quantities. Structure diagnosis locates a controller whose existing 7×4×7 volume includes the engineering module. The inverse search is ±3 horizontally and −2..+1 vertically. Multiple controllers remain ambiguous; unloaded space remains unverified. Neither condition may be reported as a definitely missing module.

Inspection is bounded and runs only on interaction. It does not load or generate chunks, poll machines, consume inventory, award items, deposit materials, record journeys or change campaign progress. It uses the current team and existing project/commissioning data. Gifts remain freely usable. A successful diagnosis does not activate the ending; that deliberate action remains on the controller.

## Interaction reference

The existing `ArkControllerBlock.useWithoutItem`, `ArkActions.inspect` and native Minecraft system chat are the reference. The actual controller and its in-game messages were inspected during Act VI client verification (`docs/verification/act-six-runtime.json`). This extension reuses that interaction and native text rendering, with translated item names and messages; it introduces no new illustration, icon resolution or custom screen. Original module models remain candidates under the existing art acceptance, not newly approved artwork.

## Acceptance for this extension

Verify incomplete and completed campaigns, remaining partial-batch amounts, independent teams, repeated inspection without state/inventory mutation, missing/ambiguous/unloaded controllers, block/reach/spectator checks, and distinct commissioned/ending states. Render messages and the use hint in English and Spanish. Record automated and real-client evidence separately. This document states the intended behavior; implementation and runtime acceptance require their own results.

## Recorded verification — 2026-09-23

Source `9735d4ee90c6708fda3282ffe8046ac976d570d9` passed three focused unit tests, 25 isolated GameTests and the single new targeted GameTest in the real dedicated pack with the normal 60-second watchdog. The completed-state diagnosis rendered in English and Spanish in the real client; repeated inspections and normal saving left the complete campaign NBT semantically unchanged. See [runtime evidence](../verification/engineering-runtime.json) for hashes, native screenshots and limitations. The translated item tooltip and updated quest paragraph still need visual review; these results do not accept the candidate block art, survival pacing, performance or the remaining module experiences.
