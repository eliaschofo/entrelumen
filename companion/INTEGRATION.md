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
