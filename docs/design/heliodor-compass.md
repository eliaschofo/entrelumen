# Start of the game: no bloat, the start ruin and the Heliodor Compass

Implements Elias's direction of 24 September 2026 ([story bible](story-bible.md): "Inicio sin bloat", "Brújula de Heliodor", "El Atlas") and his follow-up decisions of the same day: the compass follows the **team**, never sends anyone thousands of blocks away, never spins in vain, and ruins are registered so a later protection system can lock them.

Status:

- **The start ruin is the controller's design of 24 September** (a round sun patio, section 2). It is a sketch like the rest of the art: no in-game visual review yet.
- **The objective list is a DRAFT** until Elias defines the anchor ruins.
- **The pedestal model is PROVISIONAL.** It reuses chiseled tuff textures. The compass model is the one `art/build_art.py` generates on `main` (integration of 24 September): Heliodor needle frames coloured by destination dimension, grey for `state` >= 3.
- Nothing here has had in-game visual or pacing review. The full-pack server checks passed on 24 September (§7); the real client is still pending.

## 1. Start without bloat

### Audit

All 272 pinned JARs (`catalog/curated.json`, local copies from `catalog/local-paths.json`) were scanned without launching anything. The audit was repeated on 24 September over the 308 JARs of the merged lock (266 on the server, nested jar-in-jar libraries included) after the first full-pack run handed a new player three items; `python tools/audit_first_join.py <out.json>` now runs the scan:

1. Class constant pools, for first-join vocabulary: `spawnBook`, `give_book`, `obtainOnSpawn`, `first_join`, `firstLogin`, `startingItems`, `spawnWith*`, `startWith*`, `hasReceived*` and the like.
2. Every class that handles `PlayerLoggedInEvent` (also `EntityJoinLevelEvent`, player ticks and dimension changes, including listeners registered with `addListener`, which only name the event in a descriptor) and touches an inventory (`Inventory.add`, `setItem`, `giveItemToPlayer`, `ItemHandlerHelper`, `give*`/`addItem` helpers such as Silent Lib's). Each hit was disassembled with `javap`.
3. Every bundled advancement whose reward is loot and whose trigger is `minecraft:tick`, a location or an unconditional inventory change. This is the datapack route to a first-join gift.

Other sources were checked as well. Pack KubeJS scripts give nothing. Every FTB Quests reward in `pack/config/ftbquests` is empty, and `default_autoclaim_rewards` is `disabled`. Client-only mods cannot put items in a server inventory. The companion gives nothing on login.

### Findings and how the pack disables them

| Mod (pinned) | What it gave | Default | Pack fix (no JAR patch) |
|---|---|---|---|
| Ars Nouveau 5.13.1 | Worn Notebook on login (`EventHandler` → `giveItemToPlayer`, gated by `Config.SPAWN_BOOK`) | on | `pack/config/ars_nouveau-common.toml`: `[general] spawnBook = false` |
| Integrated Dynamics 1.35.0 | *On the Dynamics of Integration* on first spawn (`ItemOnTheDynamicsOfIntegration.onPlayerLoggedIn`, gated by `obtainOnSpawn`) | on | `pack/config/integrateddynamics-common.toml`: `[item.on_the_dynamics_of_integration] obtainOnSpawn = false` |
| Herbs & Harvest 19 | Guide book through the advancement `herbsandharvest:grant_book_on_first_join` (tick trigger → loot table). Its `give_book_on_join` key is **dead code** in this version: no class reads it. | on | `pack/kubejs/data/herbsandharvest/advancement/grant_book_on_first_join.json` replaces it with an impossible criterion. `pack/config/herbsandharvest-common.toml` also sets `give_book_on_join = false` in case a later release honours the key. |
| Actually Additions 1.3.26 | Booklet on the **first craft** of one of its items, not at login (`CommonEvents.onCraftedEvent`) | on | `pack/config/actuallyadditions-common.toml`: `[other] giveBookletOnFirstCraft = false`. A guide gift in the same spirit; the booklet stays craftable. |
| Aether 1.5.10 | Aether Portal Frame on world creation (`start_with_portal`) | **off** | `pack/config/aether-common.toml` pins `"Gives player Aether Portal Frame item" = false` |
| Modern Industrialization 2.5.6 | Guidebook on first spawn and respawn (`GuidebookEvents`, gated by `spawnWithGuideBook`/`respawnWithGuideBook`) | on | `pack/config/modern_industrialization-server.toml` sets both `false` |
| Silent Gear 4.2.1.1 (24 Sep re-audit) | A Blueprint Package and a Material Book on first join, through Silent Lib's `InitialSpawnItems` (registered in `SideProxy`, gated by `spawnWithStarterBlueprints` and `spawnWithMaterialBook`) | on | `pack/config/silentgear-common.toml`: `[item.blueprint] spawn_with_starter_blueprints = false`, `[item.material_book] spawn_with_material_book = false`. The Material Book stays craftable; the package is a pure gift (no recipe). |
| Ars Nouveau 5.13.1 (24 Sep re-audit) | A Starbuncle plush and a store link in chat, once per player, while Ars's remote `starbuncle_plush.json` campaign is on (`EventHandler.playerLogin`, gated only by `Rewards.SEND_ONE_TIME_MESSAGE` from GitHub and the persisted flag `an_plush`). No config exists. | remote | The companion (`FirstJoinGifts`, `PlayerLoggedInEvent` at HIGHEST priority) sets `PlayerPersisted.an_plush` before Ars's handler runs. No JAR is touched; the plush stays craftable. |

All disabled books stay craftable. Found in the re-audit but already silent: Eternal Starlight 0.9.0 has `startWithGuidebook`, **off** by default (`ESConfig`); nothing pins it. Two gifts are deliberately **not** disabled:

- **Aether starting loot on entry.** A Book of Lore and Golden Parachutes when a player first *enters the Aether*, not the world. The parachutes are fall safety. **Elias decides:** keep it, or set `"Gives starting loot on entry" = false`.
- **Reliquary.** A Witch Hat, only for a player whose name is exactly `Drullkus` (hard-coded easter egg, no config). It cannot be disabled without patching the JAR; nobody else is affected.
- **Malum.** A cosmetic Token of Gratitude curio, only for the supporter UUIDs hard-coded in `CurioTokenOfGratitude` (joined the level without one). Same category as Reliquary: nobody else is affected.

Integrated Dynamics `bookRewards` (tutorial rewards inside the book) only works for someone who already crafted the book; it is untouched.

### Checklist for the full pack

1. Sync `pack/` into the QA server and client instances. `tools/sync_pack.py` refuses a *generated* TOML that already exists with other values ("Locally changed file needs review"). On an instance that already ran, delete or merge the generated files above before syncing (on 24 September only the QA server already had a generated `silentgear-common.toml`; its two keys were merged to `false` with a backup). A fresh instance takes the partial files, and NeoForge fills in the remaining defaults.
2. Start the QA server with the QA JAR and run the full-pack GameTests. Two tests cover this:
   - `fullpackplayerarrivesemptyhanded`: a new player owns nothing 40 ticks after login.
   - `fullpackfirstjoingiftsstaydisabled`: the config settings read `false` on disk, the Herbs & Harvest advancement override is loaded, and Ars Nouveau's plush flag is set before its login handler.
3. Log in with a fresh client profile and confirm an empty inventory, both in a new world and on the server.
4. Re-audit whenever the catalog changes. The scan method above is repeatable.

## 2. The start ruin

Implemented by `HeliodorRuins`, with `RuinData` as the registry.

- **When.** NeoForge fires `LevelEvent.CreateSpawnPosition` only while a brand-new overworld chooses its first spawn. The handler marks `pendingStart` in the registry. On `ServerStartedEvent`, a pending world gets the ruin once. Existing worlds are never touched implicitly. An operator can run `/entrelumen admin ruin` to place it in an older world; the command is idempotent. The isolated GameTest server never places it implicitly.
- **Any template.** The code loads `entrelumen:heliodor_ruin_start` (`data/entrelumen/structure/heliodor_ruin_start.nbt`) and reads its size from the template. Conventions for the definitive NBT:
  - Layer 0 is the floor. It replaces the ground surface.
  - Rotation is none.
  - An optional DATA structure block with metadata `spawn` marks the arrival point. It is removed after placement.
  - Every `entrelumen:heliodor_pedestal` in the template is registered as a pedestal. A template without one gets a pedestal beside the arrival point.
- **Site.** The search starts at the vanilla spawn. Candidate centres are sampled every 4 blocks in square rings up to 64 blocks away. A footprint is rejected over water, lava or tree trunks. The first footprint with at most 2 blocks of height spread and no canopy wins. Otherwise the best one seen wins (spread, canopy and distance). The floor height is the most common ground level across the footprint.
- **Anchoring.** Dips under the floor are filled downwards with the floor block itself, or tuff when that block is not a full cube, down to 12 blocks. The ruin never floats.
- **Spawn.** The arrival point is the middle of the south side, one block outside the ruin, on safe ground; the other sides are fallbacks. When none of the four offers footing (a ruin surrounded by open water, as the full-pack test site 4096 blocks east of spawn is), a tuff step is laid at the south middle, level with the ruin floor, and cleared above; the old last resort (the floor at the centre) now only covers a step outside the build height. The world spawn moves there, facing the ruin. A brand-new player on first login (zero play time, no bed) is placed exactly on the arrival point. Vanilla spawn fuzz would otherwise scatter them within `spawnRadius`. Returning players are never moved.
- **Template** (design `art/structures/ruin_start.py`, serialised by `tools/build_heliodor_ruin_start.py`, deterministic; `--check` verifies the committed file). A 15×9×15 sun patio:
  - a round tuff floor (radius 7) with a polished tuff rim, a calcite ring and an oxidized-copper sun of eight rays around a chiseled tuff centre; pearlescent froglights glow at the four diagonals of the pedestal;
  - eight quartz columns on chiseled tuff brick bases: the four on the axes stand, capped in oxidized copper, and carry the broken stumps of a copper dome; the four on the diagonals broke at two blocks and their capitals lie on the grass outside the rim;
  - moss, short grass and moss carpet in a deterministic crack pattern; ferns and azaleas on the grass around;
  - the pedestal in the middle.

  It is symmetric under every rotation and mirror (`Voxels.is_symmetric()` asserts it). Floor cells outside the round patio are left out, so the terrain stays; `pourFoundation` levels a dip there with the ground found under it, floor cell included, instead of pouring tuff under a hole. Every other empty cell of the box is air, clearing grass and bushes.

  References inspected before drawing (rendered from the 1.21.1 server JAR with `art/structures/voxkit.py`): `data/minecraft/structure/trial_chambers/chamber/pedestal/quadrant_2.nbt` (tuff and oxidized copper as a single palette), `trail_ruins/tower/tower_1.nbt` (small buried ruin scale) and `ancient_city/city_center/city_center_1.nbt` (a symmetric frame around a centrepiece).

### Ruin registry for the protection system

The overworld SavedData `data/entrelumen_ruins.dat` (`RuinData`) uses this format:

```
version: int = 1
pendingStart: byte        // new world waiting for its start ruin
ruins: list of {
  id: string              // "entrelumen:heliodor_ruin_start"
  template: string        // structure template ID
  dimension: string       // "minecraft:overworld"
  act: int                // campaign act that owns the ruin (1 here)
  box: int[6]             // minX, minY, minZ, maxX, maxY, maxZ; template plus foundation
  origin: long            // BlockPos.asLong of the template origin (floor corner)
  arrival: long           // spawn / arrival point
  pedestals: long[]       // pedestal positions
  placedGameTime: long
}
```

In Java, `RuinData.get(server).ruins()` returns immutable `RuinData.Ruin` records, and `Ruin.contains(dimension, pos)` tests membership. Only placement code writes the registry. Later anchor ruins should be appended with the same record, so compass `anchor` targets resolve them for free. The pedestal is already unbreakable, so the only compass source cannot be removed. Every other protection (blocks, containers, lecterns, item frames, act locks) belongs to the protection worker.

### Pedestal: one compass per player

Using the pedestal (`entrelumen:heliodor_pedestal`, with any item or an empty hand) gives the player one Heliodor Compass. The rule is one per player UUID, ever, per world, recorded in `CompassData.claimed`. A second attempt only shows "The pedestal already gave you your compass". Every later player takes their own. A lost compass is not replaced. **Pending for Elias:** a recipe, a re-issue rule, or keep-on-death.

## 3. The Heliodor Compass

`entrelumen:heliodor_compass`, stack size 1. While the compass is carried, the server refreshes its `entrelumen:compass_state` component about once a second, and only writes it when the state changed.

### Objective selection (per team)

The campaign is the player's FTB team: the party UUID, or the player's own for a personal team. Objectives are walked in listed order:

- Reached objectives are skipped.
- An objective whose act is higher than the campaign act stops the walk (state `LOCKED`); its condition is not even tested.
- An objective whose condition holds now is **latched** as reached for the team and the walk continues. A team that did things early skips ahead in one step, and losing a key item never rewinds the compass.
- The first remaining objective is the current one.
- A new party inherits its founder's reached set, like the campaign itself.
- Every online member sees the same objective. A new destination is announced to all of them.

Conditions (`advance_when`):

- `milestone`: the campaign milestone is complete. This includes expedition arrivals (`twilight_arrival`, `aether_arrival`, …).
- `advancement`: any online team member has it. Bosses use this, e.g. `aether:gold_dungeon` is "killed the Sun Spirit".
- `item`: any online team member carries `count` of an item or `#tag`.

Guidance must never depend on one structure existing nearby. So `advance_when` prefers campaign milestones and story bosses; a place that is only a suggestion does not hold the compass hostage.

### Targets and the radius rule

| `target.type` | Resolves to | Cost |
|---|---|---|
| `anchor` | Nearest registered ruin with that ID in the dimension (`RuinData`) | map lookup |
| `position` | Fixed `pos` in `dimension` | none |
| `dimension` | "Go there": no position | none |
| `structure` | ID or `#tag`, nearest start within `radius` | bounded search, cached |
| `biome` | ID or `#tag`, nearest sample within `radius` | bounded search, cached |

`radius` defaults to **1500 blocks and cannot exceed 1500** (Elias, 24 September). When nothing lies within it, the compass shows `NOT_FOUND`: the needle rests and the tooltip and Atlas say there is no trace nearby. It does not spin. A target in another dimension shows `ELSEWHERE`, with a resting needle and the dimension value set.

### Searches: never a locate per tick

- `CompassLocator` runs **one search at a time on the server thread, cooperatively**, with a 2 ms budget per tick. The cursor resumes on the next tick. It never runs off-thread, because vanilla's `StructureCheck` caches are not thread-safe.
- **The server thread never loads or waits for a chunk** (since 24 September; the first full-pack run with every test at once was stopped by the 60 s watchdog inside `Level.getChunk(STRUCTURE_STARTS)`).
- Structures: square rings of chunks up to `ceil(radius / 16)` (94 rings at 1500 blocks, 189×189 chunks at most).
  - For each chunk the search asks each placement `isStructureChunk`. That covers random spread, YUNG's enhanced spread, Twilight Forest landmark grids and custom stronghold placements alike. Concentric rings use their precomputed positions.
  - Only candidate chunks get a presence check (vanilla `StructureCheck`: its cache, then the stored chunk's structure starts, then a start prediction). A predicted start is confirmed by generating its chunk to `STRUCTURE_STARTS` only, off the server thread: the search adds its own region ticket (`entrelumen_compass`, at the `STRUCTURE_STARTS` level, so never a full chunk), schedules the generation task once the chunk holder exists, and polls that future on later ticks. At most 8 chunks are in flight and 64 predictions queued; scanning pauses while the queue is full. Tickets are removed as soon as a chunk is confirmed, renewed every 200 ticks while waiting and expire on their own after 600 if a search is dropped. A chunk that is not ready after 2400 ticks is skipped and logged.
  - Once found, the search continues ≈√2 rings further for a nearer Euclidean match, then stops. Confirmations arrive out of scan order, so each nearer match tightens that bound.
- Biomes: the biome source is sampled every 32 blocks at the holder's height (93×93 samples at 1500 blocks). No chunk is loaded.
- Results are **world facts shared by every team** (`CompassData.sites`):
  - found positions, keyed by target;
  - origins of empty searches.

  A holder points at the nearest known site within the radius. A holder within 512 blocks of an empty search origin gets `NOT_FOUND` without a new search. Moving further triggers one new search. Sites persist across restarts.
- The flat GameTest world disables structure generation, so its searches honour that option and report nothing. The cost test deliberately bypasses the option.

### Measured cost

`compassSearchesStayBoundedAndCooperative` runs in the isolated GameTest server: superflat plains, structure starts disabled, cost test bypassing that option, radius 1500. See §6 for the numbers. These are upper bounds for the scan itself on a trivial generator. Full-pack terrain (noise, modded biomes, larger jigsaw assemblies) is **pending measurement** on the QA server. Each search logs one `Compass search … in N ms over T ticks (max step …)` line to make that easy.

### Proposal for Heliodor's own ruins (for the placement worker)

1. **Recommended: concentric rings around spawn, like strongholds.** Use a `ConcentricRingsStructurePlacement` structure set for each anchor ruin, with ring distance chosen so the first ring sits inside 1500 blocks. The positions are precomputed from the seed, so locating is a lookup; the locator already reads ring positions.
2. **Registry anchors.** When a ruin generates, append a `RuinData.Ruin` record (a structure-start hook). Objectives then use `anchor` targets, the cheapest resolution, which works in any dimension.
3. **Fallback, "nearest known".** Already built in: any site found by any team is shared, so a later team is pointed at a known instance within its radius without searching.

The draft list uses `anchor` only for the start ruin. Add anchor objectives when the ruins exist.

### Item properties (stable IDs, textures by the controller)

| Property | Values |
|---|---|
| `entrelumen:angle` | 0..1 like the vanilla compass. Points while `POINTING`, spins while `SEARCHING`, rests at 0 otherwise. |
| `entrelumen:dimension` | Target dimension: 0 overworld, 1 nether, 2 end, 3 aether, 4 twilight, 5 other |
| `entrelumen:kind` | 0 structure, 1 boss, 2 artifact |
| `entrelumen:spinning` | 1 only while a search is running (brief); 0 otherwise |
| `entrelumen:state` | 0 `POINTING`, 1 `ELSEWHERE`, 2 `SEARCHING`, 3 `NOT_FOUND`, 4 `LOCKED`, 5 `COMPLETE` |

- An unattuned stack, one without the component, reports `spinning` 1 and `state` 2 until the first refresh.
- The unclamped properties carry integer codes, so model overrides use `>=` thresholds in ascending order.
- `models/item/heliodor_compass.json` is GENERATED by `art/build_art.py` (source of truth; `--check` verifies it): 32 angle frames for each `dimension` 0..5, then 32 grey frames for `state` >= 3 (`NOT_FOUND`, `LOCKED`, `COMPLETE`), which win because they come last. `kind` and `spinning` are registered but no model reads them yet.
- The component `entrelumen:compass_state` holds `target` (optional GlobalPos), `state`, `dimension`, `kind` and `objective`. It is persistent and network-synchronised.

### Data: `content/compass_targets.json`

The authored source is `content/compass_targets.json`. `processResources` copies it to `data/entrelumen/compass/targets.json`, which a datapack or `kubejs/data` can override as a whole document. The server reloads it on `/reload`. Validation is strict:

- unknown fields;
- ID shape and duplicates;
- act order;
- radius range;
- tags only where allowed;
- items must exist.

A rejected document keeps the previous list. An objective whose `mods` are not all loaded is skipped, so the isolated runtime works without Twilight Forest or the Aether.

```json
{"id": "gold_dungeon", "act": 5, "kind": "boss", "mods": ["aether"],
 "target": {"type": "structure", "structure": "aether:gold_dungeon", "dimension": "aether:the_aether"},
 "advance_when": {"type": "advancement", "advancement": "aether:gold_dungeon"}, "lore": true}
```

**Draft list** (every structure ID, advancement and placement was checked in the pinned JARs):

| Act | ID | Target | Advances when |
|---|---|---|---|
| I | `heliodor_ruin` | anchor, start ruin | `atlas_awakened` |
| I | `village_survey` | `#minecraft:village` | `field_survey` |
| I | `trial_chambers` | `minecraft:trial_chambers` | `first_signal` |
| II | `trail_ruins` | `minecraft:trail_ruins` | `lost_workshop` |
| III | `nether_fortress` | `betterfortresses:fortress` | carrying a blaze rod |
| III | `twilight_forest` | dimension | `twilight_arrival` |
| III | `naga_courtyard` | `twilightforest:naga_courtyard` | `twilightforest:progress_naga` |
| IV | `the_aether` | dimension | `aether_arrival` |
| IV | `bronze_dungeon` | `aether:bronze_dungeon` | `aether:bronze_dungeon` (Slider) |
| IV | `lich_tower` | `twilightforest:lich_tower` | `twilightforest:progress_lich` |
| IV | `bumblezone` | dimension | `bumblezone_arrival` |
| V | `silver_dungeon` | `aether:silver_dungeon` | `aether:silver_dungeon` (Valkyrie Queen) |
| V | `gold_dungeon` | `aether:gold_dungeon` | `aether:gold_dungeon` (Sun Spirit: the Atlas key) |
| VI | `ocean_monument` | `betteroceanmonuments:ocean_monument` | carrying a wet sponge (Elder Guardian) |
| VI | `stronghold` | `betterstrongholds:stronghold` | `end_arrival` |
| VI | `the_end` | dimension | `minecraft:end/kill_dragon` |

Every objective has an EN/ES name and a "why it matters" line. Nine carry a lore fragment for the Atlas; early fragments keep the mystery, and the truth arrives with the Sun Spirit's key.

## 4. The Atlas section

The Atlas snapshot now carries a `CompassView`: objective, state, dimension ID, kind, and recovered lore IDs in campaign order. The network version rose from 1 to 2, so client and server need the same JAR.

The list opens with a **Heliodor Compass** entry. Selecting it shows:

- the objective name;
- kind · dimension;
- why it matters;
- the compass state;
- **Recovered fragments**, newest first.

Delivery stays disabled there. The Atlas opens on this entry when the act has no projects. The book layout and project flow are otherwise unchanged. Interference styling for pre-Sun-Spirit fragments is not implemented; the lore text itself carries the stutter.

## 5. Tests

- JUnit:
  - `CompassTargetsTest`: shipped draft equals the authored file; optional mods; every target and condition; invalid documents.
  - `CompassProgressTest`: first unreached objective; latching; skipping ahead; act gate; completion; lore order.
  - `RingCursorTest`: coverage without duplicates; resumable cursor; 189×189 bound.
- Isolated GameTests (`RuntimeGameTestsCompass`), also registered on the full-pack QA server since the 24 September merge:
  - `newPlayerArrivesWithAnEmptyInventory`. Only the isolated server must lack a start ruin; a real server places one at its first start.
  - `startRuinIsAnchoredRegisteredAndPlacedOnce`: dip filled; box equals template size; arrival beside and safe; spawn moved; second call is a no-op; registry reload; newcomer placed and veteran untouched. The site lies 4096 blocks east of the test; on the full pack that is open ocean, so the case first has worldgen generate the site through a ticket and places the ruin once every chunk is ready (generating ~120 chunks inside one tick hit the 60 s watchdog).
  - `pedestalGivesEachPlayerOneCompassEver`
  - `compassMovesToTheNextObjectiveWhenTheTeamMeetsTheCondition`: key-item count; latching; party inheritance; shared objective; act lock; shipped draft; item component; Atlas view and wire codec. The draft's first objective reads `NOT_FOUND` without a start ruin and `POINTING` at it when the server placed one.
  - `compassSearchesStayBoundedAndCooperative`: on the superflat world every plains village is predicted and rejected over the whole square; on noise terrain a found village must lie within the radius, otherwise the whole square is scanned. An End biome (absent from any overworld source) samples the whole radius, the biome under the origin is found in one tick, an End city (no End biome) is answered without a search, and no step reaches 250 ms. The snowy village and jungle of the first version only worked on the superflat world.
- Full pack (QA JAR): `fullpackPlayerArrivesEmptyHanded` and `fullpackFirstJoinGiftsStayDisabled`.

## 6. Verification record (24 September 2026, branch `feature/compass`)

Tools used: `gradlew --offline --no-daemon`, JDK `E:/Elias/Codex/Entrelumen-ssd/runtime/jdk-21.0.12.1+1`, and the build directory `E:/Elias/Codex/Entrelumen-ssd/compass-build`. The QA server was not started. The worktree has no network, so `downloadAssets` was skipped with `-x downloadAssets`, using the identical asset properties file from another local build.

- `build qaJar`: passed.
  - 130 JUnit tests, including the three new classes.
  - The release JAR ships `data/entrelumen/compass/targets.json` and the ruin template.
  - The release JAR excludes `RuntimeGameTestsCompass`.
- `runGameTestServer`: all 62 required tests passed, including the 5 new ones. Log: `compass-build/gametest-compass-pass.log`.
- Search cost on the isolated superflat world, radius 1500, 2 ms budget per tick:

| Search | Result | Wall time | Ticks | Longest step | Scanned |
|---|---|---|---|---|---|
| `minecraft:village_plains` (structure starts disabled, so every predicted village is confirmed and rejected: worst case) | none | 295 ms | 52 | 26 ms | 35 721 chunk positions, 35 candidates, 35 `STRUCTURE_STARTS` loads |
| biome `minecraft:jungle` (absent) | none | 5 ms | 3 | 2.1 ms | 8 649 samples |
| biome `minecraft:plains` (underfoot) | found | 0.1 ms | 1 | 0.1 ms | 9 samples |
| `minecraft:village_snowy` (cannot generate in this world) | none | 0 | 0 | — | answered without a search |

A structure step overruns the budget by at most one jigsaw prediction or one chunk confirmation, because those are indivisible. The first version bundled both into one step and peaked at 103 ms; splitting them brought the peak to 26 ms. Real noise terrain and modded jigsaw structures cost more per candidate; the full-pack numbers are in §7.

- Provisional ruin: rendered top view from the generator with vanilla textures (`compass-build/heliodor_ruin_start_top.png`, not an in-game capture). It is symmetric as intended.

Not exercised by these tests:

- The real new-world path (`CreateSpawnPosition` → `ServerStartedEvent`) on noise terrain; the GameTest server skips it by design. Verify with a fresh singleplayer world.
- `/entrelumen admin ruin`.
- Client rendering: item properties, models, the Atlas compass page.

## 7. Full-pack verification (24 September 2026, branch `fix/fullpack`)

Receipts: `E:/Elias/Codex/Entrelumen-ssd/fullpack-20260924` and `docs/verification/fullpack-fixes-runtime.json`. Clean owned QA servers (seed 71942026, heap 6 GB, `max-tick-time` 60000), QA JAR, every full-pack GameTest run one at a time.

- All 111 single-JVM cases passed, including every compass, start and gameplay case; the two restart verifiers passed in a second JVM and the live-backup verifier in a third, on a world restored from the native SimpleBackups ZIP.
- Boot to `Done` 189 s wall on the final run (`Done` 23.4 s); the start ruin is placed at the first start around spawn (-15..-1, 63..72, -23..-9 on this seed).
- Search cost on real terrain, radius 1500:

| Search | Result | Wall time | Ticks | Longest step | Scanned |
|---|---|---|---|---|---|
| draft `#minecraft:village` from spawn | found (96, -320) | 1014 ms | 68 | 244 ms | 3 249 positions, 5 candidates, 2 chunks confirmed through tickets |
| `minecraft:village_plains` 8192 blocks south | none | 300 ms | 34 | 16 ms | 35 721 positions, 32 candidates |
| biome `minecraft:the_end` (absent) | none | 236 ms | 117 | 2.3 ms | 8 649 samples |
| biome underfoot (desert) | found | 0.2 ms | 1 | 0.2 ms | 9 samples |
| `minecraft:trail_ruins`, `minecraft:trial_chambers` from spawn | found | 1.5-1.9 ms | 1 | 1.9 ms | stored starts, no confirmation |

  The longest step is one presence check: the first village prediction assembles jigsaw pools whose templates are still being read. Before each check became its own yield point, several village checks of one chunk ran in one step (498-817 ms). No step waits for a chunk any more; the first full-pack run with all tests at once had stopped on the 60 s watchdog inside `Level.getChunk(STRUCTURE_STARTS)`.

## Design references

No new art was drawn on this branch. The compass art now comes from `main` (`art/compass`, traced by `art/authoring/trace_compass.py`); the branch's provisional compass reused the vanilla item model and needle frames (`assets/minecraft/models/item/compass.json`, `textures/item/compass_00..31.png` in the 1.21.1 client resources, 16×16). The provisional pedestal reuses `textures/block/chiseled_tuff.png` and `chiseled_tuff_top.png`. The ruin palette (tuff, tuff bricks, oxidized copper) follows vanilla trial chambers, which are also the draft's first dungeon and share Heliodor's copper-and-tuff look.

These placeholders were not reviewed inside the game; they are not design acceptance. The controller should inspect the lodestone and recovery compasses before drawing the Heliodor needle sprites. They are the vanilla precedents for a compass whose target changes meaning.

## Pending

- The definitive ruin template and pedestal art (controller). In-game review of the generated compass textures.
- A fresh client profile and a new singleplayer world confirming the empty inventory in the real client (the server side passed on 24 September).
- Elias:
  - Aether entry loot;
  - lost-compass rule;
  - the anchor ruins that replace the draft objectives;
  - Atlas interference styling.
- In-game EN/ES review of the Atlas compass page at representative GUI scales.
