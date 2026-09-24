# Start of the game: no bloat, the start ruin and the Heliodor Compass

Implements Elias's direction of 24 September 2026 ([story bible](story-bible.md): "Inicio sin bloat", "Brújula de Heliodor", "El Atlas") and his follow-up decisions of the same day: the compass follows the **team**, never sends anyone thousands of blocks away, never spins in vain, and ruins are registered so a later protection system can lock them.

Status:

- **The start ruin template is PROVISIONAL.** It is a minimal symmetric tuff patio; the controller designs the real ruin.
- **The objective list is a DRAFT** until Elias defines the anchor ruins.
- **Compass and pedestal models are PROVISIONAL.** They reuse vanilla compass and chiseled tuff textures.
- Nothing here has had in-game visual or pacing review. The full-pack checks below have not run yet; they are pending integration.

## 1. Start without bloat

### Audit

All 272 pinned JARs (`catalog/curated.json`, local copies from `catalog/local-paths.json`) were scanned without launching anything:

1. Class constant pools, for first-join vocabulary: `spawnBook`, `give_book`, `obtainOnSpawn`, `first_join`, `firstLogin`, `startingItems`, `spawnWith*`, `startWith*`, `hasReceived*` and the like.
2. Every class that handles `PlayerLoggedInEvent` and touches an inventory (`Inventory.add`, `setItem`, `giveItemToPlayer`, `ItemHandlerHelper`). Each hit was disassembled with `javap`.
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

All disabled books stay craftable. Two gifts are deliberately **not** disabled:

- **Aether starting loot on entry.** A Book of Lore and Golden Parachutes when a player first *enters the Aether*, not the world. The parachutes are fall safety. **Elias decides:** keep it, or set `"Gives starting loot on entry" = false`.
- **Reliquary.** A Witch Hat, only for a player whose name is exactly `Drullkus` (hard-coded easter egg, no config). It cannot be disabled without patching the JAR; nobody else is affected.

Integrated Dynamics `bookRewards` (tutorial rewards inside the book) only works for someone who already crafted the book; it is untouched.

### Checklist for the full pack (pending integration)

1. Sync `pack/` into the QA server and client instances. `tools/sync_pack.py` refuses a *generated* TOML that already exists with other values ("Locally changed file needs review"). On an instance that already ran, delete or merge the five generated files above before syncing. A fresh instance takes the partial files, and NeoForge fills in the remaining defaults.
2. Start the QA server with the QA JAR and run `/test runall`. Two new tests cover this:
   - `fullpackplayerarrivesemptyhanded`: a new player owns nothing 40 ticks after login.
   - `fullpackfirstjoingiftsstaydisabled`: the five settings read `false` on disk, and the Herbs & Harvest advancement override is loaded.
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
- **Spawn.** The arrival point is the middle of the south side, one block outside the ruin, on safe ground; the other sides are fallbacks. The world spawn moves there, facing the ruin. A brand-new player on first login (zero play time, no bed) is placed exactly on the arrival point. Vanilla spawn fuzz would otherwise scatter them within `spawnRadius`. Returning players are never moved.
- **Provisional template** (`tools/build_heliodor_ruin_start.py`, deterministic, `--check` verifies the committed file). A 7×3×7 patio:
  - polished tuff border and tuff brick floor;
  - four oxidized copper veins leading to a chiseled tuff centre;
  - chiseled tuff brick corner pillars topped with tuff brick wall posts;
  - the pedestal in the middle.

  It is symmetric under every rotation and mirror.

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
- Structures: square rings of chunks up to `ceil(radius / 16)` (94 rings at 1500 blocks, 189×189 chunks at most).
  - For each chunk the search asks each placement `isStructureChunk`. That covers random spread, YUNG's enhanced spread, Twilight Forest landmark grids and custom stronghold placements alike. Concentric rings use their precomputed positions.
  - Only candidate chunks get a presence check. A chunk loads, to `STRUCTURE_STARTS` only, when the presence cache says it is needed.
  - Once found, the search continues ≈√2 rings further for a nearer Euclidean match, then stops.
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
- `models/item/heliodor_compass.json` is PROVISIONAL: the 32 vanilla needle frames driven by `entrelumen:angle`.
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
- Isolated GameTests (`RuntimeGameTestsCompass`):
  - `newPlayerArrivesWithAnEmptyInventory`
  - `startRuinIsAnchoredRegisteredAndPlacedOnce`: dip filled; box equals template size; arrival beside and safe; spawn moved; second call is a no-op; registry reload; newcomer placed and veteran untouched.
  - `pedestalGivesEachPlayerOneCompassEver`
  - `compassMovesToTheNextObjectiveWhenTheTeamMeetsTheCondition`: key-item count; latching; party inheritance; shared objective; act lock; shipped draft; item component; Atlas view and wire codec.
  - `compassSearchesStayBoundedAndCooperative`
- Full pack (QA JAR, pending): `fullpackPlayerArrivesEmptyHanded` and `fullpackFirstJoinGiftsStayDisabled`.

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

A structure step overruns the budget by at most one jigsaw prediction or one chunk confirmation, because those are indivisible. The first version bundled both into one step and peaked at 103 ms; splitting them brought the peak to 26 ms. Real noise terrain and modded jigsaw structures cost more per candidate. **Measuring them on the QA server is pending.**

- Provisional ruin: rendered top view from the generator with vanilla textures (`compass-build/heliodor_ruin_start_top.png`, not an in-game capture). It is symmetric as intended.

Not exercised by these tests:

- The real new-world path (`CreateSpawnPosition` → `ServerStartedEvent`) on noise terrain; the GameTest server skips it by design. Verify with a fresh singleplayer world.
- `/entrelumen admin ruin`.
- Client rendering: item properties, models, the Atlas compass page.

## Design references

No new art was drawn. The provisional compass reuses the vanilla item model and needle frames (`assets/minecraft/models/item/compass.json`, `textures/item/compass_00..31.png` in the 1.21.1 client resources, 16×16). The provisional pedestal reuses `textures/block/chiseled_tuff.png` and `chiseled_tuff_top.png`. The ruin palette (tuff, tuff bricks, oxidized copper) follows vanilla trial chambers, which are also the draft's first dungeon and share Heliodor's copper-and-tuff look.

These placeholders were not reviewed inside the game; they are not design acceptance. The controller should inspect the lodestone and recovery compasses before drawing the Heliodor needle sprites. They are the vanilla precedents for a compass whose target changes meaning.

## Pending

- The definitive ruin template and pedestal art (controller). Compass textures mapped from the properties (controller).
- Full-pack run of the two QA tests, config sync on existing instances, and search cost on real terrain.
- Elias:
  - Aether entry loot;
  - lost-compass rule;
  - the anchor ruins that replace the draft objectives;
  - Atlas interference styling.
- In-game EN/ES review of the Atlas compass page at representative GUI scales.
