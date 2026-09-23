# Exploration: the chart room

Decision, 2026-09-23. Implemented with isolated headless evidence. Two full-pack cases are written and registered but have not run; they await integration. Client review is pending too. This adds a practical benefit to the existing Exploration module. It does not change campaign deliveries or grant rewards.

## Purpose and native overlap

The earlier candidate was an expedition port that moves crews and aircraft between Arks. It was checked again against the pinned JARs (hashes matched the catalog) and is rejected as not distinct, in addition to the missing all-or-nothing evidence recorded in `ark-services-boundary.md`:

- Waystones 21.1.41: warp plates, warp stones and scrolls, the Portal Scroll with its temporary Warp Portal, the Twinbound Feather that links two players, "Return to Portal", and configurable transport of pets and leashed mobs.
- Tombstone 9.5.5: Tablets of Recall and Home, whose ancient versions also teleport "all the creatures around you", the Tablet of Assistance to another player and Lost Tablets that lead to villages or distant regions.
- Mekanism 10.7.19: the teleporter multiblock and the portable teleporter, charged per teleported entity.
- Ars Nouveau 5.13.1: Warp Portals of up to 21×21 blocks, and stable warp scrolls that open temporary portals, including across dimensions.

Group and cross-dimension travel is therefore already served. Locators are served as well:

- FTB Chunks 2101.1.21: sharing waypoints with a party, the server or a player; "Share Map with Allies"; and long-range location visibility for teammates.
- JourneyMap, Explorer's Compass, Nature's Compass, the Supplementaries Cartographer's Quill and explorer maps, and the pack's own survey station.

A compass-copy or bearing registry would add no distinct service. Recovery is served too: Waystones pet transport, the Supplementaries Flute, Tombstone graves and familiar receptacles, and Immersive Aircraft's own repair.

What no pinned mod covers is the physical vanilla map:

- Zooming a map out creates a blank map. Both the cartography table and the crafting-table extension recipe call `MapItemSavedData.scaled()`, which is `createFresh` without pixels.
- Maps drawn separately of the same place can never be combined.

A scan of the language files of all 197 pinned JARs, and of the 52 the catalog worker had staged by the end of this change, found these map items only:

- Create's track marker
- Immersive Engineering's mineral deposit map
- the Supplementaries quill and slice map
- the Twilight Forest magic, maze and ore maps
- the staged Iron's Spellbooks furled maps, which point to structures

A class-name scan found no map merging or compilation. FTB Chunks and JourneyMap share client minimap data, not map items.

The selected service compiles charts. A held map absorbs the explored detail of the player's other maps of the same area. Several detailed field maps can then become one overview, including one framed on a wall, without walking it all again. That fits the module's horizon chart and "the return marked first". It needs no transport and no locator, and it reads no chunks.

## Player interaction

| Gesture at the Exploration Module | Result |
| --- | --- |
| Empty main hand | Existing team journal, now with one chart-room line |
| Crouched, empty main hand | Existing commissioning deposit, unchanged |
| Filled map in main hand | Compile the chart |
| Map only in the offhand | Unchanged: the empty main hand opens the journal |

Compilation requires:

- the same complete, loaded and unambiguous physical Ark as the other services;
- reach, the main hand and a non-spectator player.

The native `RightClickBlock` path runs first, so a claim on the module can refuse the gesture. Guests and gifted maps work regardless of campaign stage. The service never reads or writes campaign progress, and it keeps no team record.

## One compilation

- **Chart.** The held `minecraft:filled_map` with saved data. A locked chart is refused, because vanilla locked maps never change. Every copy shares one map id, so framed or carried copies show the result.
- **Field maps.** Every other filled map in the 36 main slots and the offhand, counted once per map id. Copies of the chart itself are not sources. Armor, backpacks, shulker boxes and item frames are never read.
- **Fit.** A field map must be in the same dimension and overlap the chart. It must be at the same or a finer scale; a coarser map cannot add detail. Its view must match (see compatibility below). Other maps are left out, and the report counts them.
- **Pixels.** Only blank target pixels (colour 0) are written. For each one, the explored cells of the fitting maps that start inside it vote. Each cell weighs the block area it covers. The heaviest colour wins, and ties go to the lowest colour id. The result does not depend on inventory order, and explored pixels never change, even where a field map disagrees. Later, vanilla map drawing updates whatever a holder walks past, as usual.
- **Transaction.** Every fit and pixel is decided before the first write, and the write loop cannot stop halfway. Nothing is consumed or created, and the field maps are unchanged. The changed chart is marked dirty and saved with the world's map data. A compilation that adds nothing writes nothing and reports that.
- **Bound.** At most 37 field maps of 16,384 pixels each. There is no chunk access, ticking, upkeep or persistent service data.

Workflow: zoom a map out at a cartography table to get a blank, larger chart around it, then use the module with that chart in hand while carrying the detailed maps.

## Mod compatibility

Moonlight 3.5.2 attaches custom data to every map. The pinned Supplementaries 3.9.5 registers four layers:

- `depth_lock` for slice maps
- `antique` for weathered maps
- `palette`, `biomes` and `blocks` for tint
- `lightmap` for light

Only the slice depth changes what a pixel shows, so the depth saved beside each map's data must match. Weathering is presentation and may mix. The tint and light layers are not transferred. Absorbed pixels show vanilla map colours until the chart is redrawn in that area. Moonlight markers, banners, frames and item-stack decorations are not merged. Twilight Forest maps and other non-vanilla map items are ignored.

## Reference inspected before implementation

From the NeoForge 21.1.249 sources:

- `MapItemSavedData`: `createFresh` snapping, `scaled`, `locked`, `updateColor` and `setColorsDirty`.
- `MapItem.update`: the pixel origin `(center / step + pixel - 64) * step` and the mode colour.
- `MapItem.scaleMap` and `lockMap`, `CartographyTableMenu` and `MapExtendingRecipe`.

The vanilla cartography table interface `assets/minecraft/textures/gui/container/cartography_table.png` and `textures/map/map_background.png` were visually inspected. The interface is a 256×256 atlas: map input, modifier slot, plus sign, arrow and result preview above the inventory. The map background is a 64×64 parchment. Both were extracted from `G:/curseforge/Install/versions/1.21.1/1.21.1.jar` into `G:/Elias/Codex/Entrelumen-work/ark-exploration-20260923/vanilla-ref/`.

They inform the gesture: a map plus a modifier gives a result, with the cartography table's own take-result sound. No item, block, texture or screen is introduced; the existing module model, vanilla maps and chat feedback are reused. The chat lines, tooltip, journal line and the Act VI Exploration quest paragraph are authored in EN and ES. Their rendered review in the client is pending.

## Limitations

- An old field map can fill blank pixels with outdated terrain, but it never overwrites an explored one.
- Downsampling picks the most common finer pixel, brightness included, instead of re-reading blocks. That approximates vanilla's per-block choice.
- Colour 0 also means void or air, as in the End, so such pixels stay open to later absorption.
- Maps with non-vanilla centres assign each cell to the chart pixel where the cell starts.
- Supplementaries tint and light layers, markers, banners and frames are not merged.

## Verification

The offline `build qaJar` and `runGameTestServer` runs used the pinned JDK 21 and NeoForge 21.1.249. The receipts are in `G:/Elias/Codex/Entrelumen-work/ark-exploration-20260923`: `build-1.log`, `isolated-tests-1.log`, `baseline-head-tests.log`, `save-timing.json` and the thread dumps.

- Results: 68 JUnit tests passed, 7 of them new. All 45 isolated GameTests passed, 2 of them new, against 43 in the baseline run of the unchanged suite. `generate_quests.py --check` passed, and so did its 27 tests and `check_runtime_content.py`. The release JAR holds `ArkCharts` and no GameTest class; the QA JAR holds `ArkChartsGameTests`.
- JUnit (`ArkChartRulesTest`):
  - The grid matches vanilla snapping and pixel origins at every scale, up to ±30,000,000.
  - Same-scale absorption fills only blank pixels, invents nothing from blank sources and repeats as a no-op.
  - Downsampling votes by weighted area with the lowest-id tie-break.
  - A scale-0 tile lands in its own 8×8 corner of a scale-4 chart.
  - Coarser and distant maps do not fit, and only the overlap of an off-grid map is filled.
  - The result is independent of source order, and slice depth decides view equality while presentation keys do not.
- Isolated GameTests (template `empty`):
  - `explorationChartsCompileFieldMapsWithoutConsumingAnything`. An early-campaign visitor uses the native gesture with a blank scale-1 chart. The field maps are two scale-0 tiles, a third tile in the offhand, a half-explored tile carried twice and an older same-scale chart. Nether, coarser and distant maps and a copy of the chart are also carried. Exactly 6,147 pixels are charted: a pre-drawn pixel stays, the 2×2 vote and its tie resolve as specified, and the uncovered quarter stays blank. The chart is dirty and round-trips through save and load. The field maps are byte-identical afterwards. Every inventory, armor and offhand stack, including an empty map and paper, is unchanged, and campaign data is unchanged. A repeat reports 4 matching and 3 skipped maps, writes nothing and leaves the chart clean. The journal shows the chart-room line.
  - `explorationChartsRefuseWithoutMutation`. Each case below is refused and changes no chart, field map or inventory stack. Afterwards the same request compiles all 16,384 pixels from the one matching map, with 2 skipped:
    - a lone chart;
    - only maps from another dimension or at a coarser scale (2 skipped);
    - a locked chart;
    - a map without an id or without saved data;
    - an incomplete or ambiguous Ark;
    - the offhand, another module, a spectator, a remote player, a native canceled interaction, and an empty main hand with the chart in the offhand.

  A removed module then refuses.
- Full-pack, pending integration: `ArkChartsGameTests` is compiled into the QA JAR and registered in `FullpackQABootstrap`. The QA server was not started for this change.
  - `explorationChartsRespectForeignFtbChunksClaim`: a real `/ftbchunks claim` over the Ark must stop a visitor's chart gesture while the owner compiles.
  - `explorationChartsKeepSupplementariesSliceMapsApart`: with the pinned Supplementaries and Moonlight, a plain map has no view key and a map loaded with `depth_lock` does. The slice map is skipped while a plain field map compiles, and the chart round-trips with the map layers.

GameTest shutdown save time, investigated because the Nature runs showed 8–12 minutes against about 1 minute before. The world lives on `G:`, a 5,400 rpm laptop HDD shared with the other worker's `server-slice` JVM. That JVM started during the baseline run and kept running.

| Run | Isolated tests | Overworld save | Chunks saved |
| --- | --- | --- | --- |
| Logistics era | 40 | 60 s | 909 |
| Nature runs 1–3 | 43 | 460, 723, 250 s | about 1,240 |
| Baseline, unchanged suite at HEAD | 43 | 307 s | 1,246 |
| This change | 45 | 110 s | 824, spawn regions included |

Thread dumps during the save show the server thread idle in `ChunkStorage.flushWorker`. The chunk IO worker sat in a native `pwrite` from `RegionFile.writeHeader`, with 234 ms of CPU over 40 s. Free memory was 0.7 of 15.9 GB.

The larger Nature template adds about 35% more chunks to save, which cannot explain a 4–12× slowdown. Identical companion code also varied 2.8× between runs. The dominant cause is therefore disk-bound writes under contention, not companion work. A rerun of the pre-Nature commit was not taken: it would share the same contention and would not be conclusive.

Pending:

- the two full-pack cases;
- a rendered EN/ES review of chat, tooltip, journal and quest;
- a visual check of compiled charts with Supplementaries tint enabled;
- a survival playtest with real field maps.

Owner: root integrates. A bounded worker owns this service's Java, tests and EN/ES text.
