# Logistics: complete expedition kits

Decision, 2026-09-23. Implemented; headless full-pack runtime evidence passed on 2026-09-23 (see Evidence). Client visual acceptance is pending. This adds a practical benefit to the existing logistics module; it does not change campaign deliveries or grant resources.

## Purpose and native overlap

A shared Ark depot receives supplies from the team's production. Each player records a personal expedition kit, then deliberately collects all missing supplies in one operation. If stock or inventory space is insufficient, nothing moves. A module that merely refills one stack would not justify this service.

The pinned AE2 Import/Export Card 1.5.0 already describes per-slot target quantities and ME-to-player exports in `assets/ae2/ae2guide/ae2importexportcard-index.md`. Sophisticated Backpacks provides refill and physical-container restock. The selected contribution is therefore the shared physical depot and complete-or-unchanged preparation of different personal kits, independent of carried refill upgrades or a player's ME link. No exclusive invention or untested claim about those mods' transaction semantics is asserted.

## Player interaction

| Gesture at the logistics module | Result |
| --- | --- |
| Empty main hand | Open a native 27-slot chest menu and retain the current Ark inspection report |
| Crouched, empty main hand | Existing partial commissioning delivery, unchanged; do not open the depot |
| Routing Matrix in main hand | Prepare the complete recorded kit |
| Crouched, Routing Matrix in main hand | Record supplies in the other eight hotbar slots |

The Routing Matrix is the existing reusable `entrelumen:routing_matrix`, not a new token. EN/ES tooltips, service feedback and the existing logistics quest must explain these gestures. Stock can always be inserted/recovered through the native menu, even if the Ark is incomplete. Stock also exposes the normal block item-handler capability for machines; there is no tick loop or adjacent-container extraction.

Recording and preparation require the same complete, loaded, unambiguous physical Ark as existing practical services, valid reach and a non-spectator player. Respect the native canceled/denied block-interaction path. Gifts remain useful regardless of campaign stage. No operator command or new client request bypass is added.

## Supply profile and conservation

One personal profile survives reconnect/death without changing team campaign data. Record only the other eight hotbar slots, grouping item stacks with exactly equal items and components. An empty selection or any unsupported supply rejects the entire recording and preserves the previous profile. Do not silently discard entries.

Admit stackable supplies, food/drink and potions (including splash/lingering). Reject damageable items and nested inventories: standard container/bundle components, block inventory data and item-handler capability. Preserve admitted names, lore, firework duration, potion contents and mod components; no fuzzy/tag substitutions. Store a bounded, versioned profile with registry-aware stack serialization. Invalid stored data must not enable transfers or crash world loading.

Preparation counts only the player's 36 main inventory slots. It never opens a backpack or touches armor/offhand. Existing surplus stays put. Calculate every shortage and every destination slot before changing either inventory. On success, transfer only the missing quantities from the module's own 27 slots, conserving exact components. If one ingredient or destination slot is missing, leave both inventories unchanged and explain why. Repeating a completed kit takes nothing. The matrix is reusable; the actual stock is the only per-operation cost. Creative mode does not manufacture missing stock.

The module owns and persists its stock. Removing it drops that stock exactly once. Existing placed modules must gain an empty stock inventory safely after upgrade. A personal profile contains desired supplies, not stored goods; moving teams does not merge profiles. Native player/block-entity persistence is not a promise of cross-file atomicity after a power failure.

## Reference inspected before implementation

Root extracted and visually inspected the unmodified vanilla 1.21.1 `assets/minecraft/textures/gui/container/generic_54.png` from the pinned `minecraft_1.21.1_client.jar`, at `G:/Elias/Codex/Entrelumen-work/ark-logistics-kits-20260923/minecraft-generic-54-reference.png`. Its 256×256 atlas contains the restrained gray beveled chest surface, nine-column rows, 18-unit slot spacing, player inventory and hotbar. The implementation reuses vanilla `ChestMenu`/`ContainerScreen` with three storage rows; it adds no screen class, artwork, slot graphics or font. The existing module model is unchanged and remains subject to the pack's art acceptance. This inspected texture is a design reference, not a screenshot of the new service.

## Acceptance

- Two players with distinct kits use a common depot supplied through a real installed automation route. Include Farmer's Delight food, ammunition, fireworks and different potion components.
- Insufficient stock, wrong component variant and insufficient main-inventory capacity leave both inventories unchanged; complete kits, repeat uses and consecutive players conserve all items.
- Unsupported/empty profile registration preserves the previous profile. Nested containers, armor, offhand and gifted supplies follow the stated rules.
- Native canceled interaction, invalid reach/mode/structure and unloaded/ambiguous controllers cannot provision or overwrite a profile. Depot recovery remains possible without a complete Ark.
- Actual normal save/restart preserves the stock and both personal profiles. Break/removal drops stock once, and an older placed module remains usable after upgrade.
- Render and manually exercise the native menu, instructions and gestures in EN/ES when client use is available. Headless tests do not accept the visual experience, natural progression or the complete endgame.

## Evidence, 2026-09-23

Base commit `c52e724`; offline builds with the pinned JDK 21 on the owned, stopped `server-slice` QA server (NeoForge 21.1.249), receipts in `G:/Elias/Codex/Entrelumen-work/ark-logistics-kits-20260923`.

- 53 JUnit tests, 27 quest-generator tests and 40 isolated GameTests passed. The isolated fixture has synthetic external ingredients and does not prove full-pack compatibility.
- Full-pack QA JAR, installed only on `server-slice` with `-Dentrelumen.qa=true`, cases run by name. First JAR `fe862eae…`: four cases passed; `logisticspersonalkitsurvivesnativedeathclone` failed. Cause: the fixture called `PlayerList.respawn` directly, so the packet listener kept the dead player, the teleport moved that player and the clone stayed out of reach (correctly refused). Correction: the fixture now respawns through the native `PERFORM_RESPAWN` client command and asserts that the connection adopted the clone. No production code changed.
- Corrected QA JAR `4686f87f…`, server run `18669a2c`: all five `LogisticsProvisioningGameTests` passed. They cover a real Pipez pipe filling the depot, two distinct native-gesture kits (Farmer's Delight food, arrows, two rocket durations, healing and splash swiftness potions), exact conservation, unchanged replay and unchanged campaign data. They also cover wrong rocket or potion component, one missing item and a full inventory without partial transfer; rejected damageable, nested-container and empty recordings keeping the previous kit; ignored offhand; a grouped target above one stack; and a refused unknown profile version. Also covered: native canceled interaction, spectator, out-of-reach, ambiguous and incomplete Ark refusals with native depot recovery; a legacy module without block entity; the automation capability; removal dropping stock exactly once, with a stale handler/menu inert; and a kit surviving native death and respawn.
- Restart pair: `preparelogisticsrestartfixture` passed in run `18669a2c` (nonce `771df5a3`, PID 29016), then the server stopped normally with a save (exit 0). `verifylogisticsrestartfixture` passed in a new JVM (run `c4bed545`, PID 29556): identical depot stock, both personal kits and inventories from player `.dat` and reconnect, both kits prepared with conservation, and unchanged replay and campaign data. The server stopped normally again (exit 0).
- Afterwards the previous normal JAR (`1357abfd…`) was restored on `server-slice`; the FTB Chunks client config, managed-files receipt and `local/` files kept their hashes. The world was archived before this run (`resume/server-world-before-resume.zip`). Client profiles were not touched.

Pending: EN/ES client rendering and manual use of the menu, tooltips and gestures; installation into client profiles and integration of the new normal JAR; human co-op, natural progression and endgame acceptance. The QA players are internal mock connections, not external clients.

Owner: root integrates. A bounded Sol worker owns production Java and any focused planner tests; root owns EN/ES content, native QA orchestration and delivery. No mass quests or additional service are included in this change.
