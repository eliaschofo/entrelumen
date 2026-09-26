# Logistics module: local Ark batches

> **Replaced by [`ark-modules-v2.md`](ark-modules-v2.md) (25 September 2026).** The Ark's first version (local batch deposits) was removed: each module now gives a global effect to its team from its place in the Ark. This page is kept as the record of what existed; the companion no longer implements it.

`entrelumen:logistics_module` reuses the commissioned Ark's existing team ledger. Empty-main-hand use displays the current batch, credited amounts and remaining requirements in native chat. Crouched empty-main-hand use attempts one partial delivery from the player's own inventory and then shows the updated batch. It never polls a buffer, extracts a remote inventory, grants a reward or activates the final ending. Gifts remain usable materials.

The interaction point is the placed logistics module. The server checks the clicked block, reach, player mode and gesture. A bounded scan finds controllers whose existing 7×4×7 Ark volume includes that module. A deposit requires exactly one controller, all candidate chunks loaded, and all six physical modules visible in its loaded volume. Missing, ambiguous or unloaded space prevents consumption. The controller transaction in `ArkActions` applies `ArkCommissioning.STEPS`, the current team identity, exact item caps, persistence and replay protection. The existing controller interaction remains valid.

## Interaction reference

The actual `ArkControllerBlock.useWithoutItem` and native Minecraft system messages were inspected during Act VI verification at `docs/verification/act-six-runtime.json`. `EngineeringModuleBlock` and `EngineeringDiagnostics` supply the adjacent on-demand interaction and bounded inverse controller scan. Logistics follows those controls and the existing module model; it adds no block shape, texture, icon or custom screen. This is a behavior extension, not approval of the current module artwork.

Automated validation covers partial and repeated deliveries, team scope, restart serialization, exact inventory changes, unloaded/ambiguous controllers, missing structure, reach and spectator denial, and no automatic ending. Client and full-pack behavior require separate installed verification.

## Recorded verification — 2026-09-23

Source `4b1fbeb4fa086b8f441f6004f41afda3d8747fef` passed five focused unit tests and 27 isolated GameTests. The two new targeted tests also passed in the real dedicated pack with the normal 60-second watchdog. The completed-state query and item hint rendered in English and Spanish in the client; saving preserved the campaign. [Runtime evidence](../verification/logistics-runtime.json) separates those observations from automated partial-delivery checks. Client review found the deposit hint still appeared after completion; its removal is a subsequent source change, outside the tested JAR. Candidate artwork, survival pacing, co-op and performance remain unaccepted.
