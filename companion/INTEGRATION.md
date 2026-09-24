# Companion integration contract
Namespace: `entrelumen`. Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21.

Items: atlas, raw_lens, survey_notes, signal_core.
Block items: engineering_module, arcane_module, nature_module, exploration_module, logistics_module, habitation_module, ark_controller.

Player commands: `/entrelumen status`, `/entrelumen deliver <project>`, `/entrelumen advance`.
Operator level 2: `/entrelumen admin diagnostic`, `/entrelumen admin recover <uuid>`, `/entrelumen admin set <act>`.

Requirements: `data/entrelumen/campaign/projects.json` server datapack resource (bundled defaults, overrideable and reloadable); project key, act (1..6), item-count map. Deliver consumes one complete batch exactly once per campaign. Advancement requires all current act projects. No passive possession completion. Party snapshots and campaign serialization are server authoritative. Items have no use-stage restriction.

Atlas use opens a native client list/detail screen from an authoritative server snapshot; `/entrelumen status` remains available. Controller interaction attempts recoverable commissioning after all six module projects. FTB Quests task type entrelumen:campaign accepts milestone string. Server synchronization projects completed milestones and resets absent task progress once per team per second; live multiplayer verification remains pending.

Projects support optional reward (item ID), granted once after successful consuming delivery. Module projects consume materials and grant their module; controller checks placed modules. Acts2-6 costs are development balancing data, not final kitchen-sink integration. Prerequisites field is requires (project IDs).

Admin recover UUID restores an archived campaign snapshot into the invoking operator's current campaign (personal or party), replacing its progression, retaining the archived source and never reissuing rewards. Diagnostic is console-safe; set/recover require an in-game operator.

Limitations of 0.1 slice: SavedData and player inventory use normal Minecraft persistence; no atomic hard-crash transaction across save files is claimed. Ark currently validates placed modules and persists six interactions; full phased endgame objectives remain integration work. Project JSON reloads through server datapacks. Dedicated external-client behavior remains pending; embedded-server lifecycle tests are covered below.


## Headless runtime evidence — 2026-09-12

`runGameTestServer` passed all 8 required GameTests on Minecraft 1.21.1 / NeoForge 21.1.249, FTB Teams 2101.1.11, FTB Quests 2101.1.34, FTB Library 2101.1.35 and Architectury 13.0.11. Output: `build/gametest-reload-run.log`; reproducible source: `RuntimeGameTests.java` and empty structure. Tests and structure are excluded from the distributable JAR.

Evidence covers actual item/block registration and recipe-manager Atlas crafting; server commands consuming exact inventory quantities once; one Atlas reward despite replay; offhand material consumption and no partial consumption on rejected deliveries; localized status quantity/prerequisite data; SavedData asynchronous disk write and compressed NBT reload; real FTB party creation, invitation, joining, leaving, founder snapshots and dissolution archival; FTB custom task grant/reset and removal of stale completion timestamps.

Tests use server players logged in through EmbeddedChannel with NeoForge's supported mock connection negotiation, not external clients. This proves server/API integration, not rendered clients or network interoperability with six real players. The disk test waits for NeoForge asynchronous IO; no power-loss transaction guarantee follows. Performance, full campaign pacing, real-client reconnect and beta migration remain unverified.

Act I projects now grant Atlas, raw lens and survey notes exactly once. Field survey requires lens assembly. Status lists localized item names with available/required/missing counts and previous projects. Deliveries count and consume main inventory plus offhand; armor slots are excluded. Recipes remain available for replacements and gifts.


## Server datapack definition contract

NeoForge `AddReloadListenerEvent` installs `ProjectReloadListener` (`SimplePreparableReloadListener`). At startup and `/reload`, the server reads the highest-priority `entrelumen:campaign/projects.json` resource. For KubeJS, put the complete document at `kubejs/data/entrelumen/campaign/projects.json`. Standard datapacks use `data/entrelumen/campaign/projects.json`. The bundled resource is the default; overrides replace the whole document rather than merging individual projects.

The object maps stable lowercase project IDs to definitions. Each definition requires integer `act` 1..6 and a nonempty `items` object of item IDs to positive integer counts. Optional `requires` is an array of unique project IDs; optional `reward` is a registered, non-air item ID. Unknown fields, malformed types, fractional/out-of-range counts, unknown items/rewards, duplicate normalized item IDs, missing prerequisites, later-act prerequisites and cycles are rejected. All existing 15 project IDs are mandatory for compatibility; additional project IDs are permitted. Existing acts and costs were preserved.

Preparation reads and validates the entire candidate against the live item registry before publication. Application atomically replaces one immutable snapshot; no partial definition set becomes visible. Invalid input fails the resource reload with resource/pack/field context and retains the previous active project snapshot. A first startup with invalid definitions fails rather than exposing an empty playable campaign. This transaction guarantee covers campaign definitions, not unrelated listeners or hard-crash player saves.

`Projects.all()` returns the active immutable map. No player-specific recipe or registry mutations occur. Existing saved completion IDs are retained across reloads; changing a project's cost does not charge completed projects again. Author revisions should preserve the meaning of existing IDs.

Verification: `build runGameTestServer` succeeded with 10 JUnit tests and 8 required GameTests. The new runtime test enables a real temporary datapack through `MinecraftServer.reloadResources`, verifies its higher-priority cost override, attempts an invalid item/reward document with an earlier changed cost, proves the exact prior snapshot remains active, rejects a cyclic graph, then restores the original datapack selection and definitions. Pure tests also cover immutable definitions, malformed counts, unknown rewards and missing prerequisites. Existing seven runtime scenarios still pass. Test-only classes (including nested fixtures) and the test structure are excluded from the public artifact.


## Atlas GUI and network contract

Using the Atlas requests an opening `entrelumen:atlas_snapshot` (network version 1). It contains the effective campaign UUID, act, Ark phase, active-act projects with completion, prerequisites and inventory cost counts, advancement eligibility and localized message key. The client renders project/item names through EN/ES translations and item display names. Native selection lists provide independent scrolling and narration; buttons deliver, advance, refresh and close. Entries/details are rebuilt on selection or fresh snapshots, not by scanning the complete campaign every frame. There is no continuous server polling.

`entrelumen:atlas_action` carries the expected campaign UUID, action (`REFRESH`, `DELIVER`, `ADVANCE`) and project ID. The server queues it on its main thread and routes through `CampaignActions.perform`, also used by chat commands. A mismatched team UUID rejects the operation before consumption and returns a refreshed snapshot with a team-change message. Delivery and advancement reuse their existing validators and persistence. All outcomes refresh costs/completion/rewards; closing the screen prevents a late action response from reopening it. The client disables actions while awaiting a response and permits a manual refresh after a timeout.

Protocol collections are bounded at 4096 entries and textual IDs at 128 characters. Only server-to-client snapshot handlers reference the client receiver, assigned by a Dist.CLIENT subscriber; dedicated server startup remains safe. No admin actions or item-use restrictions were added. Client and server must install the matching updated companion JAR.

Verification: `build runGameTestServer` passed with 10 JUnit tests and 10 GameTests (`build/gametest-atlas-run.log`). Added real-server tests exercise the GUI service route across party creation/leave, verify stale snapshots consume nothing, replay a GUI delivery through the command without duplicate cost/reward, validate advancement and active-act filtering, and round-trip the snapshot codec. These tests prove server behavior and serialization, **not GUI rendering, mouse/keyboard usability or visual quality**. Native client visual QA in EN/ES at representative GUI scales remains required by the integrator.

## Atlas bootstrap through FTB Quests

Left-clicking an `entrelumen:campaign` task opens the Atlas through the actual FTB `Task.onButtonClicked(Button, boolean)` hook. The button label and tooltip are localized as Open Atlas / Abrir Atlas. This read-only action deliberately does not submit or complete the task, and works independently of its milestone requirements. `/entrelumen` without arguments also opens the interface; the portable Atlas item remains available with its existing reward and recipe.

The new serverbound `entrelumen:atlas_open` payload has an empty body, no campaign UUID, and only requests a fresh opening snapshot. The server resolves the player's current campaign. Mutating `entrelumen:atlas_action` requests retain their expected UUID check; no bootstrap inventory or progression changes occur. Existing explicit refresh actions remain bound to their displayed campaign.

Verification: `build runGameTestServer` passed with the existing 10 JUnit tests and all 11 required GameTests (`build/gametest-atlas-open-run.log`). The added embedded-server test opens with an empty inventory, resolves the changed campaign after actual FTB party creation, invokes the no-argument command without granting items/progression, and verifies the empty payload codec. FTB click routing was checked against the actual installed JAR; rendered task clicking and GUI usability still require the integrator's client QA. Both client and server need this updated JAR.

## Opt-in local capture (initial integrated-server implementation)

Client commands `/entrelumen_capture start` and `/entrelumen_capture stop` explicitly start/stop recording. Start requires a loaded singleplayer/integrated LAN world; remote dedicated capture is intentionally unavailable because cross-process clock alignment has not been implemented. No capture starts automatically. Output is `<game directory>/entrelumen-captures/capture-<random UUID>/`, created without overwriting existing files. The final writer drains asynchronously after stop; the session remains retained until terminal completion. Success/error feedback is issued only after CSV close and the integrity write attempt.

Frames use consecutive RenderFrameEvent.Pre timestamps; ticks copy the updated vanilla tick ring in ServerTickEvent.Post. Both share one process nanoTime origin. CSV columns match benchmark.py for frames, ticks and memory. Focus/pause/loading/dimension changes invalidate the run and break frame pairing; raw rows are retained, not silently spliced into acceptance evidence. Tick gaps/freeze/sprint/non-20 rate invalidate it. A 32768-entry bounded queue is drained by a daemon writer; overflow stops recording with invalid integrity. No uploads or permanent sampler exists. Stop/disconnect/server stop request idempotent asynchronous closure without blocking render or server ticks. The first close request fixes its reason; subsequent lifecycle calls do not alter a run already stopped. Completion is published after stream closure and integrity persistence, with IO/invalidity reported as error. A process crash leaves recording_or_interrupted integrity and cannot be considered complete.

During a session only, a 30-second sampler writes heap usage/committed/max, per-collector cumulative GC count/time, and last post-collection usage per memory pool when supported. post_gc.csv values are snapshots of the last collection, not independently timestamped GC events. Review GC counters alongside pool history before judging memory growth; no forced collection occurs. session.json intentionally omits unverified hardware, world seed, preload status and acceptance configuration. capture-integrity.json always sets acceptance=false; closed_needs_review is not a benchmark pass. benchmark.py now enforces the original collector sidecars and raw streams before metrics; invalid or missing integrity is rejected. Users must prepare/review actual metadata and continuous route spans (at least 300 seconds) and memory spans (two hours) separately.

Verification: build passed, including three new JUnit scenarios covering frame deltas and close/drain behavior, invalid state breaking frame pairs plus tick gaps, and bounded-buffer overflow. Minecraft event/command code compiles against the real NeoForge 21.1.249 API. No client capture, overhead measurement, five-minute route or two-hour session has yet been verified; no FPS/TPS/memory acceptance is claimed. No campaign or project logic changed.

Closure regression coverage added: terminal completion sees flushed artifacts, concurrent repeated close preserves the first request, and an actual filesystem failure writing integrity produces error completion. Tests prepared for the integrator build; no Gradle run was started during their active build.

## Apotheosis integration (2026-09-23)

New IDs, all without blockstate properties. Art is produced separately in `art/grids`.

- Blocks with items: `cartographer_shelf`, `patina_shelf`, `lumen_shelf`, `horizon_shelf` and `atlas_library` (a block entity with the `entrelumen:atlas_library` menu).
- Items: `augment_<modifier>` for burning, echoing, ignore_conditions, ignore_light, ignore_players, initial_health, max_delay, max_nearby, min_delay, no_ai, player_range, redstone_control, silent, spawn_count, spawn_range and youthful.

Shelf stats live in `data/entrelumen/enchanting_stats`, which only Apothic Enchanting reads. Without it the shelves report a vanilla enchanting power. Nothing links against Apotheosis classes. The Atlas Library reads Apothic Eterna through one reflective call to the public `EnchantmentTableStats.gatherStats`, and falls back to the vanilla shelf rule.

`ApotheosisTiers` grants `apotheosis:progression/*` advancements from the player's current campaign:

- Haven always;
- Frontier from Act III;
- Ascent from Act IV;
- Summit from Act VI;
- Pinnacle after `last_horizon`.

It syncs every second, on FTB login-after-team and on party join. It only grants and never revokes, and it skips absent advancements. Its campaign lookup is read-only.

Story-set tiers (2026-09-24): `sync` also moves the player's active World Tier to `story(recorded, target(campaign, unlocked))`, the higher of two values:

- the `entrelumen:story_tier` attachment (saved, `copyOnDeath`);
- the campaign's highest reached tier whose unlock the player holds.

The tier never drops, not even after leaving a team, and `sync` also runs on FTB `PLAYER_CHANGED`. It calls Apotheosis's public `WorldTier.getTier` and `setTier` through reflection resolved once behind `ModList.isLoaded("apotheosis")`, and writes only when the tier differs. A failure disables the step instead of the tick. The pack sets `Enable Manual World Tier Changes = false`. See `docs/design/apotheosis-family.md#story-set-world-tiers`.

Satiety overflow (2026-09-24): `SatietyOverflowEvents` snapshots hunger and saturation on the last `LivingEntityUseItemEvent.Tick` and converts the surplus a meal loses to the caps on `Finish`. Blocks eaten in place (cake, pies) are measured through a bite window: `RightClickBlock` opens it (highest priority, cancelled events included, since Amendments eats cakes from its own handler), the common mixin `FoodDataMixin` (config `entrelumen.common.mixins.json`) measures `FoodData.add` before the caps, and it closes at the next tick boundary. It grants short ambient buffs from the datapack file `data/entrelumen/satiety/overflow.json`, with a 10 s cooldown and decaying glut, and never replaces stronger or infinite effects. The pure rules are in `SatietyOverflow`. See `docs/design/satiety-overflow.md`.

Verification: 127 JUnit tests passed; `runGameTestServer` passed all 62 required GameTests, including five new ones in `RuntimeGameTestsGameplay`. Two full-pack cases are written but pending: `ApotheosisGameTests.worldTierFollowsTheStoryAndCannotBeChosen` and `CookingProvisionsGameTests.farmersDelightPieBiteCountsAsSatietySurplus`.

`AtlasLibraryLedger` holds the pool arithmetic: deposit value floor(b·2^(L−1)/2), price b·2^(L−1) (×2 for treasure), cap clamp(floor(Eterna/2.5), 1, 40). It is pure and unit-tested. The block entity validates every withdrawal on the server against a freshly measured Eterna. Its item handler only accepts books, and the drop keeps the pool in `block_entity_data`. See `docs/design/apotheosis-family.md`.

Verification: an offline `build` ran 80 JUnit tests, and `runGameTestServer` passed all 47 required GameTests, including two new isolated ones in `RuntimeGameTestsApotheosis`. The fixture mod ships stand-in Haven, Frontier and Ascent advancements; Summit is deliberately absent. Apotheosis itself was not loaded in these runs.

Ark arcane service (2026-09-24): `ArcaneRestoration` replaces the compound-book separation. With a worked item (not a book) in the main hand and ordinary books in the offhand, the arcane module of a complete Ark resets `minecraft:repair_cost` to 0. It charges one book and five levels per recorded operation (ceil(log2(cost + 1))), keeps every other component, and rejects before mutation. Covered by JUnit, two isolated GameTests and one full-pack case; see `docs/design/arcane-library.md`.

## Start of the game and the Heliodor Compass (2026-09-24)

Design and verification: [docs/design/heliodor-compass.md](../docs/design/heliodor-compass.md).

- New IDs:
  - item `heliodor_compass`;
  - block and item `heliodor_pedestal` (unbreakable);
  - data component `compass_state`;
  - structure template `heliodor_ruin_start` (provisional);
  - item properties `angle`, `dimension`, `kind`, `spinning` and `state`.
- Data: `entrelumen:compass/targets.json`, copied at build time from `content/compass_targets.json`. Datapacks can override it, and `/reload` re-reads it; a rejected document keeps the previous list.
- SavedData:
  - `entrelumen_ruins`: the ruin registry, with bounding box, ID, act, arrival point and pedestals;
  - `entrelumen_compass`: compasses claimed per UUID, reached objectives per campaign, shared search results.
- Operator command: `/entrelumen admin ruin` places the start ruin once in a world created before this feature and moves the world spawn beside it.
- The Atlas snapshot carries the compass view. The Atlas network version is now 2, so client and server need the same JAR.
- The companion gives nothing on login. A new world gets the start ruin at its spawn, and a brand-new player appears beside it; with no footing on any side, a tuff step beside the ruin becomes the arrival point.
- `FirstJoinGifts` (24 September) sets Ars Nouveau's persisted `an_plush` flag on `PlayerLoggedInEvent` at HIGHEST priority, so Ars's remote Starbuncle plush campaign (no config) never hands a new player an item. Config gifts stay in `pack/config`.
- Compass searches never load a chunk on the server thread: a predicted structure start is generated to `STRUCTURE_STARTS` through the region ticket type `entrelumen_compass` (600-tick lifespan, renewed while in flight, removed on confirmation) and polled on later ticks.

## Solsticio commerce (2026-09-24)

Design and verification: [docs/design/solsticio-commerce.md](../docs/design/solsticio-commerce.md).

- Markers `shop:<type>`, `sidequest:<id>`, `resident` and `easter:<name>` join the Solsticio template contract; the six natives stand around `trading_hall`.
- Data: `entrelumen:solsticio_shops/<type>.json` (16) and `entrelumen:solsticio_natives/<discipline>.json` (6), reloaded by `/reload`; structure tags `entrelumen:on_jungle_temple_maps`, `on_swamp_hut_maps` and `on_ancient_city_maps`.
- Villagers are vanilla `Villager`s tagged `entrelumen.solsticio` with persistent data `entrelumen_commerce`. `VillagerTradingMixin` (in `entrelumen.common.mixins.json`) hooks `Villager.updateSpecialPrices` and `resetSpecialPrices` for the discounts and act locks.
- SavedData `entrelumen_solsticio` gains `liberated` and `commerce` (sites, hall, easter eggs); older saves load with neither.
- Server config `config/entrelumen-commerce-server.toml`; operator commands `/entrelumen admin solsticio liberated [true|false]` and `/entrelumen admin solsticio commerce [populate|list|respawn <index>]`.
- API: `SolsticioCommerce.setLiberated(server, true)` for the final quest and `SolsticioCommerce.registerSideQuest(id, hook)` for side quests.
- Test-only: `RuntimeGameTestsCommerce` and the `commerce_fixture` structure (`tools/build_commerce_fixture.py`), both excluded from the release JAR.

Verification: 189 JUnit tests passed; `runGameTestServer` passed all 88 required GameTests, including five new ones in `RuntimeGameTestsCommerce`.
