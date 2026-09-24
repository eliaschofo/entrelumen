# Nature: landscape restoration

Decision, 2026-09-23. Implemented. Isolated headless evidence passed, and so did the full-pack claim case on the owned QA server at `e1cc078` (see Verification). Client review is pending. This adds a practical benefit to the existing Nature module. It does not change campaign deliveries or grant rewards.

## Purpose and native overlap

Generic seed propagation was already rejected. Botany Pots, Mystical Agriculture, Farmer's Delight and Productive Bees cover it; see `ark-services-boundary.md`. The proposal on the table was landscape restoration or reforestation. Before selection it was checked against the pinned JARs:

- Ars Nouveau 5.13.1 (`ars_nouveau-1.21.1-5.13.1.jar`): lang entries and the `ForestationRitual`, `FloweringRitual`, `ConjurePlainsRitual`, `ConjureDesertRitual` and `RitualOvergrowth` classes (bytecode). Forestation places grown oak and birch trees plus bone meal in a 7×7 disc; a brown mushroom switches it to spruce and podzol, glow berries to jungle. Flowering fills the area with flowers and grass. The Conjure Island rituals raise plains or desert islands and rewrite the biome. Overgrowth applies bone meal over time. All of them consume Source.
- Nature's Aura 41.9: the Ritual of the Forest crafts recipe outputs from a sapling growing among offerings, and the Powder of Steady Growth boosts existing plants. Aura effects such as Grass Die degrade the land.
- EvilCraft 1.2.94: Biome Extracts convert an area to the biome captured in the extract.
- Vanilla: bone meal on grass spreads short grass and the biome's first flower patch. Grass also spreads onto lit dirt over time.

Generic "turn this area into forest" therefore duplicates Ars Nouveau. The selected service is narrower: restoring a marked site to **its own** biome while leaving builds alone.

- Exposed dirt regains the cover that prevails around the site.
- Bare grass gets its biome's plants, drawn from the same source vanilla bone meal uses.
- Native trees come back only where the biome grows them, from the biome's own generation features and at the biome's own density. A plains site stays open, a forest recovers trees, and a cherry grove gets cherries.

None of the pinned mods does biome-faithful repair that also protects builds in one bounded operation. The Ars rituals remain the tool for creating new forests or changing biomes. This service creates no biome, fills no terrain and does not accelerate existing crops.

## Player interaction

| Gesture at the Nature Module | Result |
| --- | --- |
| Empty main hand | Existing team journal, now also showing the team's restoration site |
| Crouched, empty main hand | Existing commissioning deposit, unchanged |
| Compass bookmarked at a survey station or lodestone | Mark the team's restoration site |
| Bone meal (optional saplings in offhand) | Restore the marked site |

Both new gestures require the same complete, loaded, unambiguous physical Ark as the other services. They also require reach, the main hand and a non-spectator player. The native `RightClickBlock` path runs first, so a claim on the module can refuse the gesture. Guests can use the service regardless of campaign stage. It never reads or writes campaign progress.

## Site and area

- A site must be in the Ark's dimension and at most 128 blocks horizontally from the module. Each campaign identity (a party, or a player's personal campaign) keeps one site. It is stored in the `entrelumen_nature_restoration` SavedData. Marking the same site again changes nothing and leaves the data clean.
- The work area is a disc of radius 8 around the bookmark: 197 columns, nearest first. A column's ground is its highest terrain block, ignoring leaves, and must lie within 12 blocks of the bookmark's height.
- Before any work, every chunk within 17 blocks must already be loaded, with its entities. That covers every permitted write and each write's neighbours. The service never loads a chunk.

## One use

Each use is one synchronous server operation with three phases, applied nearest-first:

1. **Cover.** Exposed `minecraft:dirt` with air above becomes the most common cover among the disc's grass, podzol and mycelium, or grass when there is none. Cost: 1 bone meal each.
2. **Native trees.** A column is a tree spot when a stable per-world number for it falls below the biome's expected tree placements per chunk divided by 256. That expectation is estimated from the count and rarity rules of the biome's own vegetation features. There must be no log, sapling or huge mushroom within 3 blocks. Eligible features contain only vanilla tree, huge-mushroom and selector code with vanilla placers and decorators; modded tree shapes are skipped. The feature runs in a sandbox that records its writes without applying them, so the whole tree commits or nothing does. Cost: 1 sapling of any species and 4 bone meal per tree, at most 16 trees per use. Without saplings, trees are reported as waiting.
3. **Flora.** One third of the bare grass columns, again stable per world and never a tree spot, get short grass. One in eight of those gets a flower from the biome's first flower patch instead, as with vanilla bone meal. Double flowers need room above. Cost: 1 bone meal each.

The main-hand stack is the budget. When it runs out, the report counts the waiting changes, and the next use continues from the same stable plan. A finished site reports that nothing is left and charges nothing, so retries and reconnects have no double effect. Each block or tree is an atomic unit, and payment covers only units actually applied. Creative players pay too.

## Protection

- *Natural* means air, fluids and wild terrain or vegetation: the vanilla `dirt`, `sand`, base stone, `logs`, `leaves`, `flowers`, `saplings`, `replaceable` and `replaceable_by_trees` tags and the common `c:ores` tag, plus a short list of snow, ice, gravel, clay, mushroom and dripstone blocks. Everything else counts as built. So do block entities (including containers), placed (persistent) leaves, stripped logs or wood, farmland, crops and paths.
- Cover and flora need natural blocks throughout the 3×3 columns, from one below the ground to two or three above it. Trees may replace only air, replaceable plants, natural ground or the same block. Every written block's neighbours must be natural.
- Non-living entities (armor stands, frames, vehicles, displays) block the columns around them. Players block trees.
- After placing a unit, the service posts NeoForge `BlockEvent.EntityPlaceEvent` as the acting player for every changed block, as block items do. FTB Chunks and other claim systems can cancel it. Any cancellation restores the whole unit, and nothing is charged for it.
- Nothing is dropped, the service makes no items, and no block outside the permitted box is written.

## Reference inspected before implementation

The interaction reference is vanilla. From the NeoForge 21.1.249 sources: `GrassBlock.performBonemeal` (first biome flower patch, short grass), `TreeGrower.growTree`, `TreeFeature` (valid tree positions, leaf distance update, `setDirtAt`) and `SaplingBlock`. `BlockSnapshot`, `EventHooks.onBlockPlace` and `BlockEvent.EntityPlaceEvent` supply the native protection path. The overlap references are the Ars Nouveau, Nature's Aura and EvilCraft JARs named above. The existing survey-station bookmark (`SignalStationBlock`) supplies the marker.

No item, block, texture or screen is introduced. The existing Nature Module model, vanilla compass, bone meal and saplings are reused. Chat feedback, the module tooltip, journal lines and the Act VI Nature quest paragraph are authored in EN and ES. Their rendered review in the client is pending.

## Limitations

- Minecraft does not record who placed a block. Isolated dirt, stone or logs that a player placed away from any other build are treated as terrain.
- The service does not reconstruct terrain. It fills no holes or quarries, and bare stone and sand stay as they are. Only plain dirt regains cover.
- Biomes whose tree features use modded code (many Twilight Forest shapes, for example) get cover and flora only. Badlands terracotta counts as built.
- Other mods that track player placement also see the placement events.
- The density estimate uses only a feature's count and rarity rules. Tree spots rejected by the 3-block spacing or by obstruction are skipped silently.

## Verification

Offline `build qaJar` and `runGameTestServer` ran with the pinned JDK 21 (NeoForge 21.1.249). The receipts (`build.log`, `isolated-tests-*.log`) are in `G:/Elias/Codex/Entrelumen-work/ark-nature-restoration-20260923`.

- Results: 61 JUnit tests passed, 8 of them new, and 27 quest-generator tests passed. All 43 isolated GameTests passed, 3 of them new (`isolated-tests-3.log`). The release JAR excludes the test template and `RuntimeGameTests`; the QA JAR contains the full-pack case.
- The first run failed all three new cases for a fixture reason. Without `skyAccess`, GameTest roofs the template with barriers, and the heightmap found that roof instead of the ground. The cases now declare `skyAccess = true`; the production code did not change.
- `NATURE_QA` outcome lines from the final run:
  - Forest with the canopy vetoed: 57 plants for 57 bone meal, 0 trees, 0 saplings.
  - Forest, next pass: 5 whole trees for exactly 20 bone meal and 5 saplings; the pass after that restores nothing and costs nothing.
  - Meadow: 20 ground and 44 plants for 64 bone meal, then 1 plant for 1 bone meal, then nothing.
- JUnit (`NatureRestorationRulesTest`, 8 cases): the disc has 197 columns in nearest-first order, and distance rounding is overflow-safe. The loaded chunk bounds cover every write and its neighbours. Column choice is stable per world and purpose. Tree density follows the per-chunk count, and weighted choice is correct. Covers tie to grass. Site persistence round-trips; a repeated mark stays clean. Malformed entries are skipped, and a future schema is refused.
- Isolated GameTests (the `nature_restoration` template, 40×14×40, excluded from the release JAR):
  - `natureRestorationHealsScarsAtExactCostAndSparesBuilds`. A native compass gesture marks the team's site, and a repeated mark is a no-op. A native bone meal gesture with 5 bone meal restores exactly the five nearest scars. Repeated passes charge exactly the applied ground, plants and trees, stay within the radius and settle to a pass that changes and charges nothing. Dirt stays untouched beside a plank wall and chest (contents unchanged), under an armor stand, in a canceled place event (a simulated claim) and outside the radius. Nothing drops, campaign data is unchanged, and the journal shows the site.
  - `natureRestorationGrowsWholeNativeTreesOrNone`: forest biome via `FillBiomeCommand`. With every leaf placement canceled, no log or leaf remains and no sapling or tree bone meal is charged. Without the veto, whole native trees grow: one trunk per tree, exactly 1 sapling and 4 bone meal each. Writes stay inside the permitted box, never touch the cobblestone post, and trunks are spaced more than 3 blocks apart. Further passes settle.
  - `natureRestorationRefusesWithoutMutation`. Refused without consuming anything or changing the world: no site, an unbound compass, another dimension, a site too far away, an incomplete or ambiguous Ark, the offhand, a spectator, a remote player, another team (whose journal shows no site) and a native canceled interaction. The site also survives a save round trip, creative mode pays, and a removed module refuses.
- Full-pack: `NatureRestorationGameTests.natureRestorationRespectsForeignFtbChunksClaim` passed at `e1cc078` (details under Integration below). A real `/ftbchunks claim` by one team refuses another team's restoration inside the claimed chunk without charge, while the owner can restore it.

The isolated fixture is a flat plains world with a forest biome filled in. It uses mock connections and cancels a test listener in place of FTB Chunks; it does not prove full-pack compatibility. With these tests, the suite's shutdown world save took 8–12 minutes, against about 1 minute in the logistics receipt. That was measured while another worker's QA server loaded the same disk. Follow-up on 23 September (exploration run, `G:/Elias/Codex/Entrelumen-work/ark-exploration-20260923/save-timing.json`): the restoration fixture adds about 35% more chunks to save (909 → ~1,240), but thread dumps showed the chunk writer blocked on disk I/O on a shared 5400 rpm drive with 0.7 GB of free RAM, and the same code varied 2.8× between runs (1 min 50 s to 5 min 7 s). Disk contention is the main cause; a quiet-machine comparison is still pending.

### Integration, 2026-09-23 (`e1cc078`)

Root integration, with receipts in `G:/Elias/Codex/Entrelumen-work/head-e1cc078-20260923` and evidence in `docs/verification/ark-nature-exploration-runtime.json`.

- Offline `build qaJar`: 68 JUnit tests passed, 8 of them `NatureRestorationRulesTest`. All 45 isolated GameTests passed.
- The isolated counts differ from the receipts above. This run gave 59 plants with the canopy vetoed, 4 trees for 16 bone meal and 4 saplings, and 64 then 8 meadow bone meal. Earlier runs gave 56–59 plants, 4–5 trees and a second meadow pass of 1–25. Each run asserts exact cost against its own outcome.
- The QA JAR ran only on the owned `server-slice` with `-Dentrelumen.qa=true` (run `6f8dfc96`). The world was archived first. The full-pack claim case passed on its first run, and no test or production change was needed. FTB Chunks 2101.1.21 logged the owner's claim of chunk `[1, 0]` and the unclaim. The visitor's native gesture left the claimed scars as dirt and paid exactly for the blocks restored outside the claim, and the owner then restored them.
- After a normal save and stop (exit 0), the normal JAR went back on the server. Its dedicated start/save/stop passed (exit 0), with only the known Create: Enchantment Industry `reliquary:xp_juice_still` ERROR.

Pending: the Ars Nouveau and Nature's Aura coexistence check in the installed pack (both only loaded beside the service so far). Also rendered EN/ES review of chat, tooltip, journal and quest, a survival playtest on real mined and logged terrain, and modded-biome samples. Performance is bounded per use (at most 197 columns, 64 bone meal and 16 simulated trees, with no ticking), but TPS has not been measured.

Owner: root integrates. A bounded worker owns this service's Java, tests and EN/ES text.
