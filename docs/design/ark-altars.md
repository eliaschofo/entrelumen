# Ark altars: the common base and six altars

Decision, 23–24 September 2026. The common base and the first two altars are implemented on branch `feature/altars` (worktree `G:/Elias/Codex/Entrelumen-work/wt-altars`). Isolated evidence is recorded under Verification. Full-pack QA, act rewards and client review are pending integration.

24 September 2026: the Altars of Peace, Growth, Time and Repose, the act rewards of all six altars and the removal of the Mega Torch recipe are implemented on branch `feature/altars-effects` (worktree `E:/Elias/Codex/Entrelumen-ssd/wt-altars2`), on top of `main` ab3796a. Their isolated evidence is under Verification; full-pack QA and client review are pending integration.

Elias's idea (23 September), later extended to six altars. Place a block like an altar and feed it. The first one grows the land back like bone meal, but at long range and not at random: it rebuilds what the world generator actually made there, flowers and plants included, and revitalizes dead soil. A second one makes a smooth building surface that still looks like the land around it. Everything in the pack must be symmetric.

| ID | EN / ES | Role | State |
| --- | --- | --- | --- |
| `renewal_altar` | Altar of Renewal / Altar de Renovación | Rebuilds the generator's land: dug holes, bare dirt, the original trees and plants | Implemented on `feature/altars` |
| `terraform_altar` | Altar of Levelling / Altar de Nivelación | Flattens a centred square with a symmetric slope, moving matter without creating it | Implemented on `feature/altars` |
| `peace_altar` | Altar of Peace / Altar de Paz | No natural hostile spawns in a large square; replaces Torchmaster's Mega Torch | Implemented on `feature/altars-effects` |
| `growth_altar` | Altar of Growth / Altar de Crecimiento | Much faster crops and more drops. `c:seeds` seeds are not multiplied; Mystical Agriculture crops grow faster but their essence is not multiplied | Implemented on `feature/altars-effects` |
| `time_altar` | Altar of Time / Altar del Tiempo | Hostile mobs and enemy projectiles in slow motion; much more loot and XP | Implemented on `feature/altars-effects` |
| `repose_altar` | Altar of Repose / Altar de Reposo | A 4-slot chest (symmetric) that repairs stored gear very slowly, like Mending; needs fuel | Implemented on `feature/altars-effects` |

## Common base

Every altar is an `AltarBlock` (block) with an `AltarBlockEntity` (block entity) of an `AltarType`. The base provides:

- **Type.** `AltarType(id, fuel tag, ticks per fuel unit)`. Mine are `RENEWAL` (tag `entrelumen:renewal_fertilizers`, 200 ticks, 10 s per unit) and `TERRAFORM` (tag `entrelumen:terraform_fuels`: `#minecraft:coals` and redstone, 1,200 ticks, one minute per unit). A new altar adds a constant.
- **Fuel and active time.** One fuel slot of the type's tag is fed by hand or by any pipe or hopper (insert-only capability). `payActiveTick()` pays one active tick and draws a unit when the paid time runs out; an altar with neither time nor fuel is inactive. `onFuelDrawn()` lets a type give more per unit: the Altar of Renewal adds work charge. The time is paid on top of any work cost the altar already has.
- **Owner.** The owner is the placer, or the first player who feeds it. Work that changes blocks posts the native events as the owner: the online player, or a fake player with the owner's profile. That fake player is created while the owner is online, or else in a tick of its own (see Performance).
- **Registry.** `AltarRegistry` indexes each loaded level's active altars by every chunk their square touches. `isInsideActive(level, type, pos)` and `activeAt(level, type, pos)` answer in constant time whether a position lies inside an active altar of a type; this is what the Peace, Growth and Time altars need for their area effects. Altars register while `working()` is true and unregister when inactive, removed or unloaded. The registry is not saved: each altar persists its own state and registers again when its chunk loads.
- **Area.** Every altar covers a square centred on itself (`areaRadius()` is its half-width). `areaHalfHeight()` can bound it vertically, symmetric above and below the altar; the default is the whole column. `AltarRules.square(r)` lists its columns in concentric square rings, and the shared VoxelShapes cover the modelled plinth, column and cap.
- **Optional inventory.** An inventory of any size, saved with the altar and dropped with it. Containers, menus and pipes see it through `Container`, and `onContentsChanged()` is the hook. It has 0 slots for the Altar of Renewal, 27 for the Altar of Levelling's buffer and 4 for the Altar of Repose.
- **Gestures and drops.** Fuel in the main hand feeds the altar. An empty main hand calls `use`, and crouching with it calls `crouchUse` (crouching works even with something in the offhand). Breaking the altar drops its fuel and inventory exactly once.
- **Persistence.** Owner, fuel, paid time, units drawn in the current run and the inventory are saved under `altar`. Each type adds its own fields.

**Obtaining and duplicating.** Altars are not crafted from scratch. They are act rewards, and a copy is made the way smithing templates are: the altar plus expensive materials gives two altars. The duplication recipes are written (`data/entrelumen/recipe/*_duplication.json`), each with the act's prototype component and diamonds around the altar:

- Altar of Renewal: `DPD / PAP / DPD`, with `P` = `entrelumen:propagation_core` (Act III, nature and magic) and `D` = diamond.
- Altar of Levelling: `DHD / HAH / DHD`, with `H` = `entrelumen:handling_core` (Act III, engineering).

The Peace, Growth, Time and Repose recipes follow the same pattern. Which project grants each altar is under [Obtaining the six altars](#obtaining-the-six-altars).

## Overlap and references inspected

The Nature service already restores a marked site at the Ark (`ark-nature-restoration.md`), and its overlap review still holds. The Ars Nouveau rituals (`ForestationRitual`, `FloweringRitual`, the Conjure Island rituals, `RitualOvergrowth`), Nature's Aura and EvilCraft's Biome Extracts all create new forests or biomes; none rebuilds a site's own generation. Building Gadgets 2 (in `catalog/curated.json`) places and destroys player-chosen shapes; it does not follow terrain, blend a slope or keep matter. Its JAR was not opened for this change.

Sources read for the implementation, from the NeoForge 21.1.249 sources jar (`companion-build/moddev/artifacts/neoforge-21.1.249-sources.jar`):

- `ChunkGenerator.applyBiomeDecoration`: decoration seed, per-feature seed `setFeatureSeed(seed, index, step)`, feature order from `featuresPerStep` and the structure step.
- `ChunkStatusTasks`: status order and heightmap priming.
- `NoiseBasedChunkGenerator`: `fillFromNoise`, the public `buildSurface(ChunkAccess, WorldGenerationContext, …)` overload and `applyCarvers`.
- `Beardifier.forStructuresInChunk` and `StructureManager.startsForStructure`.
- `WorldGenRegion` (region random, write radius) and `ProtoChunk.setBlockState` (heightmaps by status).
- `FlatLevelSource` and `FlatLevelGeneratorSettings`.
- `LootTable.getRandomItems(LootParams, long)`, `BlockEvent.BreakEvent`, `EventHooks.onBlockPlace` and `FakePlayerFactory`.
- The GameTest fixture code: `StructureUtils` and `GameTestServer`.
- The existing `NatureRestoration`, `FeatureSandbox` and their tests.

## Altar of Renewal

### Gestures

| Gesture | Result |
| --- | --- |
| Fertilizer in the main hand | Stores up to one stack of one fertilizer and starts a pass unless one is running |
| Hopper or pipe | Inserts fertilizer only; nothing can be extracted. A never-started altar starts when fed |
| Empty hand | Status: state, square, progress, restored counts, fertilizer, charge and paid time. Particles mark the square's edge for 5 s |
| Crouch, empty hand | Pause or resume. A finished altar starts a fresh pass if it has fuel |

### Area and the Ark

The altar covers a 49×49 square (half-width 24, 7 chunks at most with its margin). When the player who starts a pass belongs to a team whose Ark Nature restoration site lies within 64 blocks, the square is 65×65 (half-width 32).

The two services complement each other. The Ark's Nature service stays the precise, deliberate tool: radius 8 at one marked site, sapling-based trees, one synchronous use. The altars do the field work: long range, unattended and progressive, rebuilding original terrain the Ark does not fill. Altars near the team's Ark site reach further. Nothing in `NatureRestoration` changed behaviour; its placement commit moved to the shared `LandWorks.commit`, and its tests stay green.

### Reconstructing the original land

`TerrainReference` regenerates what the level's own `ChunkGenerator` produced, in detached `ProtoChunk`s that never touch the live level:

1. **Terrain.** For every chunk of the square plus a one-chunk ring: `createBiomes`, `fillFromNoise` and, for noise generators, `buildSurface`, with the level's `RandomState` and seed.
2. **Structures.** Structure references and starts are copied from loaded chunks, so village beards are rebuilt. A chunk whose structure start is not loaded is skipped rather than guessed. Structure pieces are never placed.
3. **Decoration.** Each chunk of the square is decorated in a fixed order with vanilla's own loop and seeds (`setDecorationSeed`, `setFeatureSeed`, the generator's `featuresPerStep`, the region random). Only the surface steps run: lakes, local modifications, surface structures, vegetation and top-layer snow and ice. Ores, underground features and structures, strongholds, springs and carvers are left out on purpose, so no ore is ever "restored".
4. **Isolation.** Features run against a `WorldGenLevel` proxy. Writes are limited to the 3×3 chunks around the decorated one, as in vanilla, and light reads as zero, as in generating chunks. The live level, entities, the server and lighting are unavailable. A feature that needs them fails alone, is counted and is skipped, where vanilla would crash the whole chunk.
5. **Threading.** One daemon worker builds one reference at a time, server-wide, and splits the original trees. The server thread only captures structure data and reads the finished sandbox.

The reference is deterministic: repeated regenerations give identical states, and it is rebuilt identically after a restart. Vanilla does not fix the order in which neighbouring chunks decorate, so where their features overlap the reference can differ slightly from the real original. Everything the altar writes is still the generator's own output.

**Fallback considered.** The requested fallback was surface-only restoration with biome features seeded per position, as in the Nature service. It was not needed: the full sandbox is affordable (Performance). Generators other than the noise generator still get biomes, `fillFromNoise` and decoration, but their surface rules are skipped; the flat generator exercises that path in tests. Carvers are not replayed: they need a full `WorldGenRegion`, and the supported refill below never fills closed caves anyway.

### Trusting a chunk

The first time one of a chunk's columns or trunks comes up, the altar compares the chunk's 256 current ground tops with the reference, ignoring vegetation and built columns. A chunk is trusted when all of these hold:

- at least 16 columns are comparable;
- at most 10% of them hold natural ground above the original surface;
- at least 20% of them are within one block of it.

Digging only lowers ground, so pits and large digs pass. A chunk made by another generator, or quarried beyond recognition, is left alone and counted as unrecognized.

### Passes, in symmetric rings

The whole square advances in concentric square rings from the altar, so the work spreads outwards evenly on every side. Each of the three passes covers the whole square:

1. **Terrain and cover.** A column is walked down from the original ground top while it stays open (air, or a natural replaceable plant), and it must reach existing ground within 16 blocks. A build, a container or any fluid on the way stops it with nothing filled. The refill then rises from that ground, bottom-up, while the generator had terrain there. Because every refill is supported, closed caves, flooded or bottomless pits, overhangs and floating terrain are never touched. Refills use the original block, never ores or block entities. Exposed dirt or coarse dirt at the original surface then regains its original cover: grass, podzol, mycelium or moss.
2. **Trees, by trunk distance.** Trunks are 26-connected logs, and each canopy block joins the trunk that reaches it first through the canopy, so touching crowns stay separate trees. Only missing blocks are written, and only when the whole unit fits. Every missing block must be air, a replaceable natural block or the same block; the soil must be natural ground; no entity may be in the way; and every neighbour must be natural or original. Bee nests are left out.
3. **Plants.** The original plant on each column comes back when its positions are air and the ground under it matches: short grass, ferns, flowers, double plants, sugar cane or cactus stacks, berries, mushrooms and snow layers. Pumpkins and melons are excluded, so the altar cannot be used as a free harvest.

The altar works only while the whole square, every canopy it may grow and their neighbours are loaded (half-width plus 9 blocks, at most 7×7 chunks); otherwise it waits. It never loads a chunk. A first version used a per-chunk queue that let loaded chunks run ahead; waiting for the whole area keeps the rings symmetric, at the price of needing a player near the altar.

### Protection

- Only open, natural positions are written. "Natural" is the Nature service's definition plus sandstone, red sandstone, terracotta, smooth basalt and tuff. Everything else is protected, including every block entity: containers, crops, farmland, paths, placed leaves and stripped wood. A neighbour that still holds exactly its original block also counts as land, so badlands terracotta and village edges work.
- No block is placed into an entity.
- Every write posts `EntityPlaceEvent` as the owner. Any cancellation restores the whole unit and charges nothing.

### Payment

One fertilizer adds 4 work charge and pays 10 s of active time; only ticks that actually work pay time. A refilled or re-covered block costs 1, and a plant costs 1. A tree costs one per missing block, up to 16 (the Nature service's 4 bone meal). Fertilizer is drawn only when the next unit or tick needs it; without it the altar waits until more arrives. Refused or already-present work costs nothing. A pass therefore costs about a quarter of a fertilizer per restored block, plus one per 10 s of work.

### Progress, persistence and idempotence

The altar saves its state, half-width, pass and next unit, and charge; the base saves owner, fuel, paid time, units drawn and totals. Schema version 1; a future version is refused. A unit finishes before the index moves. After a reload the sandbox is rebuilt identically and the pass resumes at the saved unit. The plan is always recomputed from the world, so a repeated pass over restored land changes nothing and uses no work charge.

## Altar of Levelling

### Gestures

| Gesture | Result |
| --- | --- |
| Coal, charcoal or redstone in the main hand | Fuel: one minute of work each |
| Empty hand, idle or finished | Preview for 10 s: end-rod particles on the flat square's edge and green particles on the slope's outer edge, on all four sides |
| Empty hand during the preview | Start |
| Empty hand while working | Pause after the last whole column, or resume |
| Crouch, empty hand, during the preview | Next size: 9×9, 17×17, 25×25, 33×33 |
| Crouch, empty hand, otherwise | Open the 27-slot buffer |
| Block item in the main hand | Add the stack to the buffer |
| Hopper or pipe | Fuel slot first, then the buffer (insert and extract) |

### Target and slope, symmetric

The area is always a square centred on the altar, never a free rectangle, and its columns are worked in concentric square rings. The flat square sits at the level the altar stands on: its top is the block under the altar. Around it, a 4-block band blends into the land, with the same width and smoothstep profile on all four sides. Each band column projects outwards onto the untouched ring just outside the band and eases from the flat height to that ring's ground height. The projection rounds half away from zero, so mirrored columns reach mirrored ring columns. Water surfaces count as ground there, and built or unloaded ring columns keep the flat height. Symmetric land around the altar therefore gives an exactly symmetric slope; this is unit-tested for every size under all eight symmetries of the square. Where the land itself differs from one side to another, each side still meets its own land.

### Layers and palette

At start, the altar samples the untouched ring. The most common top block becomes the surface, the three below it the subsurface, and the block five below the deep material. The fallbacks are grass, dirt and stone, and the palette is saved with the run.

Filled columns get the surface block on top (dirt is placed as the cover when the surface is grass, podzol or mycelium), subsurface in the next three layers and deep material below. Natural ground in the top four layers that does not match its layer is swapped. The surface falls back to the subsurface's family, and the subsurface to natural stone. Cobblestone and cobbled deepslate count as built once placed, so they only fill deep layers. Otherwise the column waits for materials.

### Conservation

Each column is one atomic unit, and matter is only moved:

- **Cutting.** Everything natural above the target is cut, top-down, up to 32 blocks. Each cut block yields its normal loot for a matching iron tool, seeded per position so a retry is identical: grass gives dirt and stone gives cobblestone.
- **Filling.** Holes are filled bottom-up, up to 24 blocks deep, only from the buffer. Missing materials are pulled from an adjacent container first.
- **Simulation first.** The whole column is simulated on a copy of the buffer, and it waits if its drops do not fit. When fewer than 4 slots are free, items the surface and subsurface do not use go to adjacent containers.
- **Events.** Break events, then place events, are posted as the owner. Any cancellation restores the column, and the buffer is committed only afterwards.
- **Refused columns.** A column is refused whole and counted when there is a build, a container or a fluid source in the touched span or next to a cut, when natural land is taller than 32, or when a hole is deeper than 24. The altar's own column is never touched.

Progress (cursor), size, flat height, palette, buffer, fuel, owner and totals are saved. A reload resumes at the next column, and a repeated run over finished land changes neither the land nor the buffer.

## Area altars: Peace, Growth and Time

The three area altars share `AreaAltarEntity` and `AreaAltarBlock`. An area altar is active while it is not paused and has paid time or fuel. Each active tick pays one tick of time, drawing a fuel unit when the paid time runs out. The altar is in the `AltarRegistry` exactly while it is active, and its effects only ask the registry. None of them walks its area.

| Gesture | Result |
| --- | --- |
| Fuel in the main hand | Stores up to one stack of one fuel item |
| Hopper or pipe | Inserts fuel only; nothing comes out |
| Empty hand | Status (state, size, fuel, time left) and particles on the area's edge for 5 s |
| Crouch, empty hand | Pause or resume. A paused altar burns nothing and affects nothing |

The registry now also stores a vertical half-height, so an area can be a box centred on the altar and symmetric above and below it. The default is still the whole column, which the Renewal and Levelling altars use. It also counts active altars per type, so every effect first checks `anyActive(level, type)`, a constant-time early exit. `isInsideActive` has an overload that allocates nothing, for per-spawn and per-entity checks.

### Fuels

| Altar | Fuel (tag) | Per unit | Why |
| --- | --- | --- | --- |
| Peace | `entrelumen:peace_altar_fuels` = `#minecraft:candles` | 10 min | A vigil light. String and honeycomb, renewable once bees are kept (Act III). About 6 an hour, 64 last 10.7 h of loaded time |
| Growth | `entrelumen:growth_altar_fuels` = bone block | 3 min | Nine bone meal pressed together, the growth fuel of vanilla. The Altar of Renewal already eats loose bone meal as work charge. 20 an hour |
| Time | `entrelumen:time_altar_fuels` = amethyst shard | 2 min | The crystal that keeps time, like a quartz clock. Renewable from geodes. 30 an hour for the altar with the strongest reward |
| Repose | `entrelumen:repose_altar_fuels` = Bottle o' Enchanting | 1 min, paid only while mending | Mending's own food, experience, bottled |

Fuel burns only while the block entity ticks, that is, while a player keeps the area loaded, which is also the only time spawns, crops and mobs there tick.

## Altar of Peace

While it burns candles, no hostile mob spawns naturally in the 129×129×129 cube centred on it (half-width and half-height 64). This is the reach of Torchmaster's Mega Torch, inspected in `torchmaster.toml` of the ATM10 instance (`megaTorchRadius = 64`, "in each direction (cube)", `blockOnlyNaturalSpawns = true`) and its recipe `data/torchmaster/recipe/megatorch.json` (torches, diamonds, gold blocks, logs) in `torchmaster-neoforge-1.21.1-21.1.9.jar`. The cube rather than the whole column keeps deep mines under a base as dangerous as elsewhere, as with the Mega Torch.

- **What it refuses.** Spawns of type `NATURAL` whose entity type is in the `MONSTER` category and not in `c:bosses`. That includes phantoms and structure spawns that use the natural spawner, such as fortress mobs.
- **What it leaves.** Spawners and trial spawners, spawn eggs, dispensers, summons, conversions, reinforcements, jockeys, raids, patrols, zombie sieges and every boss. Unlike the Mega Torch, it does not block village sieges: Elias's rule is that events keep working.
- **How.** `MobSpawnEvent.SpawnPlacementCheck` at the lowest priority sets `FAIL`. It fires before the mob is created, so a refused attempt costs one registry lookup. `FinalizeSpawnEvent` with spawn type `NATURAL` cancels the spawn, as a catch-all for mods that spawn "natural" mobs without vanilla's placement rules. Running last, the altar overrides other mods' spawn rules inside its cube.
- **Replacing the Mega Torch.** `tools/generate_family_balance.py --family qol` now removes `torchmaster:megatorch` in `pack/kubejs/server_scripts/entrelumen_qol_balance.js`. The Dread Lamp, Feral Flare Lantern and Frozen Pearl keep their native recipes. The generator's `afterRecipes` check confirms the removal at runtime.

## Altar of Growth

While it burns bone blocks, crops and saplings in the 33×33 field centred on it, from four blocks below it to four above (nine layers, 9,801 blocks), grow about 20 times as fast. Ripe harvests there drop twice as much, except seeds.

- **Extra random ticks.** Each active tick draws positions uniformly from the field and gives each growing block found there a random tick, exactly as vanilla does per section. There are 137 draws per tick, `ceil(9,801 × 19 × 3/4,096)`: every block gets 19 times vanilla's default rate (randomTickSpeed 3) on top of vanilla's own. That makes 20.1 times in all. The budget is fixed whatever the field holds, capped at 256 draws and 1 ms of wall time per tick. The area is never scanned. Crop growth goes through each block's own `randomTick`, so NeoForge's `CropGrowEvent` and other mods' rules (seasons, light) still apply.
- **What grows.** The block tag `entrelumen:growth_altar_accelerated` (`#minecraft:crops`, `#minecraft:saplings`, sugar cane, cactus, bamboo, nether wart, cocoa, sweet berries, stems, kelp, cave, weeping and twisting vines). The growing classes of vanilla and mods also count: crops, stems, saplings, nether wart, cocoa and berry bushes. Mystical Agriculture's crops are crop blocks and grow faster too.
- **Harvest bonus, by a global loot modifier** (`entrelumen:altar_bonus`, `data/entrelumen/loot_modifiers/growth_altar.json`). It applies to the block's own loot table only, and only when the harvest is ripe inside an active altar. Ripe means crops at their last age, nether wart at 3, cocoa at 2 and sweet berries from 2. The drops double.
  - Seeds (`c:seeds`) keep their count and their rate. Wheat gives two wheat and its usual seeds.
  - Carrots and potatoes double, poisonous potatoes included.
  - Unripe crops get nothing. A carrot planted and broken at once never multiplies, so replanting cannot duplicate anything.
  - Melons, pumpkins, sugar cane, cactus and bamboo grow faster but drop normally: a placed melon block cannot be told from a grown one, so a bonus would be a duplication loop.
  - `entrelumen:growth_altar_no_bonus` excludes crops (block tag: `#mysticalagriculture:crops`) and items (item tag: `#mysticalagriculture:essences`, `#mysticalagriculture:seeds`, `mysticalagriculture:fertilized_essence`), all optional. Mystical Agriculture 8.0.27 has no crop loot tables, so its drops never reach a loot modifier anyway. The tags keep that true if a later version adds them.
  - Only plain stackable items multiply. A named, enchanted or otherwise changed stack, or a single item, keeps its count.

## Altar of Time

While it burns amethyst shards, hostile mobs in the 33×33×33 cube centred on it (half-width and half-height 16) move and strike at a third of their strength. Their projectiles fly the same path at a third of the speed. Mobs a player kills inside drop three times their loot and experience.

- **Who counts.** Hostile means an `Enemy` or the `MONSTER` category, never a player, and never a boss (`c:bosses`). Bosses and their projectiles are left alone: their movement and attacks are not driven by attributes, so slowing them would be partial, and their drops must not be multiplied.
- **Mobs.** Every `Mob` checks its position every 10 ticks, staggered by entity ID. Inside an active altar a hostile mob gets the modifier `entrelumen:time_altar_slow`: `ADD_MULTIPLIED_TOTAL` −2/3 on movement speed, flying speed, attack damage, attack knockback and attack speed, which keeps a third of each. Outside, or when the altar stops, the modifiers are removed at the next check; `LivingDeathEvent` removes them on death. They are transient: never saved, so a reload or a change of dimension cannot leave a mob slowed.
- **Projectiles.** Only a projectile whose owner is a hostile, non-boss mob is slowed. Players' projectiles, fake players' projectiles and projectiles without an owner, such as dispensers', are not. The owner is judged when the projectile enters; a slowed arrow stays slowed even if its archer dies.
  - On entering, the velocity drops to a third, and so does the propulsion of fireballs and other powered projectiles (`accelerationPower`).
  - `EntityTickEvent.Pre` records position and velocity. After the projectile's own tick, if it flew freely (it moved exactly by its slowed velocity), `Post` keeps a third of the change its tick made and a ninth of the gravity. That is a third of gravity over a third of a tick: time runs at a third, so the projectile follows its normal path at a third of the speed, neither braked nor dropping like lead. A third of gravity per tick, the literal reading of the brief, would bend the path three times as much; `AltarEffectRulesTest` shows that version dropping more than ten times further off the path. Fireballs reach a third of their terminal speed.
  - A hit, a bounce or a deflection keeps the projectile's own result.
  - On leaving, velocity and propulsion are restored.
  - The slowed mark is a saved data attachment (`entrelumen:time_altar_slowed`), so a projectile saved slowed is released when it loads outside.
  - While slowed, a projectile resends its position and motion every tick, because clients predict projectiles with full gravity. No mixin was needed.
  - Arrow damage scales with speed, so slowed hostile arrows hit for about a third, like the slowed mobs.
- **Loot ×3**, by the same global loot modifier (`time_altar.json`), on the mob's own loot table only. Its conditions: a hostile, non-boss mob killed inside an active altar, with a last-damage player who is a real player, not a `FakePlayer`. Only plain stackable items multiply, and `entrelumen:time_altar_no_bonus` (the nether star, dragon egg, mob heads and trial keys) keeps its count. Equipment a mob wore or picked up is not loot-table loot and drops once, as is.
- **Experience ×3**, by `LivingExperienceDropEvent` under the same conditions.
- **Decision to review.** The bonus needs a real player's kill. Mob grinders with fake-player swords, and kills by pets or traps, get the slow motion but not the triple loot, so an unattended farm cannot use the altar as a loot multiplier. Allowing them is a one-line change in `AltarLootModifier.time` and `AltarEffects.onExperience`.

## Altar of Repose

A four-slot coffer that mends what it holds very slowly, like Mending without orbs. While it has Bottles o' Enchanting and holds a worn item, every paid tick advances a round; every 100 paid ticks (5 s) each worn item inside regains one durability point. Time is paid only while something is worn, so an idle coffer keeps its fuel.

- **Calibration.** One bottle is one minute: 12 points for one item, 48 with four. An iron chestplate from nothing (240 points) takes 20 minutes, an elytra (432) 36 minutes, a diamond pickaxe (1,561) about 2 h 10 min and 131 bottles alone. Mending with the same bottle's 7 experience restores 14 points at once. The coffer is the slow, unattended, cheaper-per-point alternative, a place to leave gear while resting.
- **What fits.** Only damageable, repairable items (`isDamageableItem()` and `isRepairable()`), one per slot. Unbreakable items, stackables and items marked not repairable are refused by the GUI slots, the container and pipes.
- **Pipes and hoppers** see the fuel slot (insert only), then the four slots. They may insert only worn items and extract only whole ones. A pipe line through the coffer is a repair line: it never pulls a tool out half-mended and never feeds whole tools back in. The GUI accepts any repairable item, whole ones included, to store them.
- **Gestures.** Bottles in the main hand feed it. Any other use opens the coffer, like a chest, so a worn tool can go in straight from the hand. Crouching with an empty hand reports what is being mended, how long the most worn item needs and the fuel left.
- **GUI.** Reference inspected: vanilla's dispenser, `assets/minecraft/textures/gui/container/dispenser.png` (176×166, a 3×3 grid of 18-pixel slots centred on x = 88, title centred). The coffer draws that texture at runtime, covers its grid with the panel colour and draws a 2×2 grid of vanilla slot frames (colours sampled from the texture: face `#C6C6C6`, slot `#8B8B8B`, shadow `#373737`, light `#FFFFFF`) centred on the same line. Slots sit at x 71 and 89 and y 30 and 48, symmetric about x = 88. The title and a status line ("Mending · N bottles · m:ss") are centred above the grid; the player's inventory is vanilla's. No asset is copied or added.
- **Persistence.** Round progress and the total mended are saved under `repose` (version 1) with the common altar state; breaking the altar drops fuel and contents once.

## Obtaining the six altars

Each altar is the reward of one Atlas project. A project's `reward` in `data/entrelumen/campaign/projects.json` is given once, when the campaign completes the project: `Campaigns.deliver` refuses a completed project, so replaying a delivery, reconnecting or reloading grants nothing more. The campaign is the party team's, or the player's own without a party. The item goes to the delivering player, or drops at their feet when the inventory is full. A new chat line announces it, for this and every other project reward: "The Atlas grants your team: …".

| Altar | Project (act) | Why there | Duplication component |
| --- | --- | --- | --- |
| Levelling | `workshop_hands` (III, engineering) | The handling core, the workshop's hands | handling core |
| Growth | `nursery_protocol` (III, nature) | The propagation core, garden and hive | propagation core |
| Peace | `signal_exchange` (III, habitability and exchange) | Safe routes between settlements | routing matrix |
| Renewal | `pollinator_treaty` (IV, nature) | A place that "continued working after the witnesses left" | propagation core (existing recipe) |
| Repose | `horizon_survey` (IV, exploration) | Rest for gear worn in the Aether and the Twilight Forest | horizon chart |
| Time | `sealed_memory` (IV, arcane) | Preserving what time would erase | containment seal |

Each duplication recipe (`data/entrelumen/recipe/*_altar_duplication.json`) is `DCD / CAC / DCD`, with `A` the altar, `C` four of its act's component and `D` four diamonds, and gives two altars. It is symmetric under every mirror and the transpose. Diamonds follow vanilla's smithing templates, whose duplication also costs diamonds; the component ties each copy to the act that granted the original.

The FTB quest texts of these projects are frozen by `tools/test_generate_quests.py` (hashes of the Act I–IV text), and three of them say that a delivery "grants no duplicate item reward", which stays true. They do not mention the altars yet. The campaign owner should add one sentence per project and update the frozen hashes. `exchange_route`, whose text says "there is no item reward", was not given an altar for this reason.

## Art and shape

The controller's piecewise 3D models, blockstates (a single variant each; neither block has state properties), item models and textures are in `main` (commits e8bf470 and c52ee4f). This branch carries none of them. Both blocks use `noOcclusion()` and one VoxelShape, which is also the collision shape, following the model in pixels:

- Plinth: [1,0,1]–[15,3,15].
- Column: [3,3,3]–[13,11,13].
- Cap:
  - Altar of Renewal: [1,11,1]–[15,14,15]. The crystal above it is left outside the shape.
  - Altar of Levelling: [1,11,1]–[15,13,15]. The instrument above it (up to about pixel 25) is left outside the shape.

The Peace, Growth, Time and Repose altars use the controller's models, blockstates and textures from `main` unchanged (`peace_altar`, `growth_altar`, `time_altar`, `repose_altar`). Each model has the same plinth, column and cap [1,11,1]–[15,14,15] as the Altar of Renewal, so the four blocks share its VoxelShape (`Altars.PEDESTAL_SHAPE`), with `noOcclusion()`. Their ornaments above the cap (up to pixel 21–25) stay outside the shape, like the Renewal crystal. They are mined with a pickaxe and drop themselves.

Names, tooltips, chat feedback and states are authored in EN and ES in the companion's lang files. Their rendered review is pending.

## Performance

All figures are wall-clock times on Elias's workstation, from the isolated GameTest server. That server ran 51 tests concurrently on the shared, slow G: disk while other servers loaded it too, so treat the figures as upper bounds. Receipts are in `G:/Elias/Codex/Entrelumen-work/altars-build/logs/full-*.log` (`ALTAR_PERF` lines).

**Sandbox, off the server thread.** Measured on the vanilla overworld noise generator, built from the registries with fixed seed 20260923, on a forest chunk at [0, 0]. The ranges cover runs 2–8; the final run was the fastest.

| Work | Time |
| --- | --- |
| Noise and surface terrain | 58–111 ms per chunk (25 chunks: 1.4–2.8 s) |
| Decoration of the surface steps | 11–111 ms per chunk (9 chunks: 0.10–1.0 s) |
| First single-chunk region, cold JIT (9 terrain + 1 decorated) | 1.2–3.2 s |
| Splitting the original trees (flat test regions) | 4–702 ms |

A full region therefore takes about 2–4 s for the 49×49 square (36 terrain and 16 decorated chunks at most) and about 3–6 s for 65×65 (49 and 25). Only one region is built at a time, server-wide. The noise fill itself runs on vanilla's worldgen pool, like normal chunk generation. The fallback was not needed.

**Server thread, per working tick.** The budget is 32 block writes for renewal and 48 for levelling, 256 inspected columns, and 2 ms of wall time once the first unit has run. The times exclude GC pauses, which are counted separately; the final run had none inside altar ticks.

| Scenario (final run, `full-8.log`) | Working ticks | p50 | p95 | Max |
| --- | --- | --- | --- | --- |
| Renewal, pits (63 blocks, 2 covers) | 11 | 2.3 ms | 16.5 ms | 16.5 ms |
| Renewal, resumed after reload (108 blocks) | 7 | 1.7 ms | 2.4 ms | 2.4 ms |
| Renewal, forest (6 trees, 26 plants) | 14 | 1.4 ms | 3.4 ms | 3.4 ms |
| Levelling, 9×9 with slope | 23 | 2.4 ms | 6.8 ms | 10.1 ms |
| Levelling, 17×17 resumed | 24 | 2.3 ms | 2.6 ms | 2.6 ms |

**Stalls found and removed.** Earlier runs had isolated working ticks of 135–848 ms, even with GC ruled out. They always followed a test player's logout. The first action for an offline owner created a fake player, and a new fake player loads that owner's saved advancements and stats from disk. The base now creates it as soon as the owner is set, while they are online and their data is cached, and otherwise in a tick of its own. After the change, the final run's worst altar tick was 16.5 ms. After a server restart with the owner offline, that load still happens once, in its own tick, and can stall it the way a player login does. Windows thread CPU time has 15.6 ms granularity, so per-tick CPU cost is not reported. TPS with players and the full pack has not been measured.

### Peace, Growth, Time and Repose

Final isolated run (`E:/Elias/Codex/Entrelumen-ssd/altars2-build/logs/full-2.log`, `ALTAR_PERF` lines), on the SSD with 57 tests running concurrently:

| Measurement | Result |
| --- | --- |
| Peace: one natural spawn check inside the cube, the whole NeoForge event post | 617 ns |
| Peace: the altar's handler alone | 321 ns |
| Time: one mob check (each hostile mob is checked every 10 ticks) | 297 ns |
| Growth, real field and rate (137 samples a tick), 200 ticks | mean 48 µs, p50 43 µs, p95 85 µs, max 298 µs |
| Growth, test field at 48 samples a tick while crops ripen and a tree grows, 211 ticks | mean 109 µs, p50 62 µs, p95 357 µs, max 2.7 ms (the tick that grew the oak) |

Projectiles are checked every tick, but only while a Time altar is active in their level or while they are slowed; the check is one registry lookup. The Repose coffer does its work every 100 paid ticks, on four slots. TPS with players and the full pack has not been measured.

## Verification

Offline `gradlew --offline build qaJar runGameTestServer` in the worktree, with the pinned JDK 21 (NeoForge 21.1.249). ModDevGradle's `downloadAssets` still refreshes the vanilla asset index over the network and checks the shared asset cache on G:. Final run, `full-8.log`:

- **JUnit: 90 passed, 22 of them new.**
  - `AltarRulesTest` (12) covers the square's chunks and order, the symmetric ring order, the loaded area and the supported refill (pit, cave, build, fluid, overhang, bottomless and floating cases). It also covers trust thresholds, fertilizer drawing and carry-over, tree cost, the tick budget (writes, scans, time) and the registry index (by type and square, replacement, removal).
  - `TerraformRulesTest` (10) covers the columns and outer ring, the target and smoothstep slope, symmetry under all eight symmetries of the square, layers, cut/fill/swap planning and order, refusals, the ledger, and conservation across a sequence of plans.
  - The Nature tests (8) still pass.
- **Isolated GameTests: all 51 passed, 6 of them new**, in the `nature_restoration` template (40×14×40) and `empty`.
  - `renewalAltarRefillsPitsToTheOriginalSurfaceAndSparesBuilds`.
    - Fed 12 bone meal by the native gesture, the altar waits when it runs out. A hopper-style capability insert resumes it; the capability refuses other items and gives nothing back.
    - Pits are refilled with the original dirt and grass, and bare dirt regains grass.
    - These are left alone: a chest (contents unchanged) with the column above it and its one-block margin, a plank block and the dirt beside it, a column whose placements a simulated claim cancels (it costs nothing), and a pit outside the square.
    - Payment is exact: 65 charge for 17 bone meal, remainder carried. A crouched restart changes nothing and uses no work charge.
  - `renewalAltarProgressSurvivesSaveAndReload`.
    - The block entity is saved mid-pit, removed and loaded back. State, pass, unit, charge, fertilizer and totals round-trip.
    - While it runs, the registry reports the altar's square exactly: its corner is inside, one block beyond is outside, and the other type does not match. After the pass the altar is gone from the registry.
    - The altar finishes all 108 blocks for exactly 27 bone meal.
  - `renewalAltarRegrowsTheOriginalTreesAndPlantsWhole`.
    - With a decorated flat forest as reference, everything written equals the reference.
    - A tree with a cobblestone post in its canopy stays entirely absent. Trees whose leaves a simulated claim refuses grow no log at all.
    - Plants are restored, and payment is bounded by the work applied.
    - The altar's reference, the test's own regeneration and a third one made after the pass have identical digests.
  - `altarReferenceRegeneratesOverworldNoiseDeterministically`: overworld noise, surface rules and forest decoration. Two regenerations are identical, the chunk's trees and plants are present, and there are no feature failures or unsupported calls. This test also produces the sandbox timings above.
  - `terraformAltarFlattensWithLayersAndSlopeWithoutCreatingMatter`.
    - It is driven by native gestures: preview, a full size cycle, then start.
    - A hill and a boulder are cut. A pit and the east slope are filled with grass over dirt, and the slope rises smoothly to the higher land.
    - The altar waits when fill runs out and resumes when a player supplies dirt. Terrain blocks plus buffered items are conserved exactly.
    - A chest and a plank block refuse their columns. Cancelled break and place events leave their columns untouched.
    - Nothing drops, and a repeated run changes neither land nor buffer.
  - `terraformAltarPausesOnWholeColumnsAndResumesAfterReload`.
    - Started without fuel, the altar changes nothing and stays out of the registry. With charcoal it works, draws one unit, and registers its square exactly.
    - At the pause every column is untouched or finished, matter is conserved, and the altar leaves the registry.
    - Cursor, buffer, palette, flat height and totals round-trip through a block entity reload, and the resumed run completes conserving matter.
- **Full-pack (pending integration).** `AltarFullpackGameTests` compiles into the QA JAR but is not registered or run. It covers the Altar of Renewal against the pack's real overworld generator near spawn, compared with an independent regeneration. It also covers real FTB Chunks claims against a foreign team's Altar of Renewal and Altar of Levelling.

**History.** The first run (`full-1.log`) found test-fixture bugs:

- A leaf veto that was not bounded to its template broke the concurrent Nature forest test.
- The save/reload test had a race.
- A claim was removed before the repeated pass.
- A test had too little fill material.

That run also found the fake-player stall, and it reported one reference-digest mismatch in the forest test. The mismatch did not reproduce in the seven later runs, and the forest test now checks three regenerations. Watch for it during integration.

### Peace, Growth, Time and Repose (`feature/altars-effects`)

Offline `gradlew --offline build qaJar runGameTestServer` in the worktree, same JDK and NeoForge, build and run directories on `E:/Elias/Codex/Entrelumen-ssd/altars2-build`. Final run `logs/full-2.log`: **JUnit 115 passed, 0 failed; isolated GameTests: all 57 passed** in 15.7 s, 6 of them new or changed here.

- **JUnit, new.**
  - `AltarEffectRulesTest` (9) covers:
    - The three areas as centred boxes: membership, volume, all eight symmetries of the square and the vertical mirror, at the real sizes.
    - The registry's boxes and per-type counts against a brute-force check at 20,000 random points.
    - Peace's rule over every `MobSpawnType`.
    - Growth's 137 samples a tick and a speed-up of 20.1, with the cap.
    - Bonus eligibility and stack splitting (wheat doubles, its seeds do not).
    - Slow motion: an arrow keeps its path (horizontal within 1 %, height within g·t/3) at a third of the speed, while "a third of gravity per tick" drops over ten times further off; a fireball reaches a third of its terminal speed.
    - The Repose calibration, and the fuels and their durations.
  - `AltarRewardsTest` (2): each of the six altars is the reward of exactly one project of its act, and each duplication recipe is `altar + 4 component + 4 diamonds → 2`, symmetric.
  - `ProjectValidationTest` and every earlier test still pass.
- **GameTests, new** (`RuntimeGameTestsAltarEffects`; areas shrunk so no effect reaches a neighbouring test, real sizes checked through the registry):
  - `peaceAltarRefusesNaturalHostileSpawnsOnlyWhileFuelled`.
    - Unfuelled, it refuses nothing.
    - Fed two candles by the native gesture, it pays ten minutes per candle; its fuel slot takes more of the same candle only and gives nothing back.
    - Inside, it refuses natural zombies, creepers and phantoms, up to the cube's corners. It allows spawners, trial spawners, eggs, sieges, patrols, summons, the Wither, cows, one block beyond a side and one block above.
    - The finalize-spawn catch-all cancels only the natural spawn.
    - Paused, it burns nothing and refuses nothing. Out of time and candles, spawns come back.
    - At its real size the registry covers the 129³ cube's eight corners and nothing one block beyond each face; removed, it leaves the registry.
  - `growthAltarGrowsCropsFastAndDoublesRipeHarvestsButNotSeeds`.
    - Fed bone blocks by a hopper-style insert (bone meal refused), it ripens 78 wheat, carrot and beetroot crops and advances a sapling. A crop outside its field stays at age 0.
    - Sampling stays within its per-tick budget.
    - Rolling the real loot tables with 40 seeds inside and outside: ripe wheat, carrots, potatoes and beetroots give exactly twice, their seeds exactly the same, an unripe carrot and a placed melon exactly the same.
    - A player's real harvest of one ripe wheat drops exactly two wheat.
    - At its real size the sampling is 137 a tick, p95 under 2 ms, and the registry answers its 33×33×9 box symmetrically.
  - `timeAltarSlowsHostilesAndTheirProjectilesAndMultipliesPlayerKills`.
    - A husk inside keeps a third of its movement, attack damage and knockback. A husk outside and a cow inside are untouched.
    - Leaving releases the husk, coming back slows it again, and dying inside releases it.
    - A pillager's arrow advances 0.50 blocks a tick with a gentle drop, while the player's arrow flies at full speed.
    - A blaze's fireball keeps a third of its propulsion and regains it outside.
    - An arrow saved while slowed keeps its mark and regains three times its speed when it loads outside.
    - Loot rolled with 40 seeds is exactly three times for the player's kill and unchanged for a fake player's. A cobblestone stack the husk carried drops once, as is. Experience is 15 inside and 5 outside.
    - At its real size the registry answers the 33³ cube symmetrically.
  - `reposeAltarMendsSlowlyWithoutDuplicatingAndPipesTakeOnlyMendedGear`.
    - Pipes refuse dirt, an unbreakable pickaxe and a whole sword, accept a worn pickaxe alone in its slot, and may take out whole shears but not the worn pickaxe.
    - Use opens the four-slot coffer; its slots refuse dirt and hold one repairable item each.
    - Without fuel nothing mends and no time is paid. Fed two Bottles o' Enchanting by gesture, after 250 ticks each worn item has regained exactly two points, one bottle is drawn, and no item was duplicated or lost.
    - Its state round-trips through a save and load.
    - The mended sword leaves by pipe. With nothing worn, no time is paid.
    - Breaking it drops the one remaining bottle and its pickaxe once.
- **GameTests, changed** (in `RuntimeGameTests`, owned by the campaign): `thirdActConsumesGiftedPrototypesAndGatesClosure` now expects the Peace, Growth and Levelling altars, exactly one each after every delivery and replay. `fourthActRequiresJourneysAndFiveDeliveries` expects each delivered project's reward once and nothing on replay.
- **Static checks.**
  - `generate_family_balance.py --check`: all five families pass, qol now with 1 removal (`torchmaster:megatorch`, found in the pinned Torchmaster JAR).
  - `test_family_balance`, `generate_quests.py --check`, `test_generate_quests` (27) and `curate_pack.py --check` pass.
- **Full-pack, pending integration.** `AltarEffectsFullpackGameTests` compiles into the QA JAR and is neither registered nor run; see the Integration checklist.

## Limitations

- Pits deeper than 16 blocks below the original surface are not refilled (level them first). Neither are overhangs, closed caves, flooded pits or carver ravines.
- Where neighbouring chunks' features overlapped during real generation, the reference may differ slightly from the original. Trees and plants are only placed where everything is free, so a mismatch leaves a gap, not a collision.
- Modded features that need the live level, entities or the server are skipped and counted. Modded chunk generators get no surface-rule pass.
- Chunks next to a structure whose start is not loaded are skipped, and structure pieces are never rebuilt.
- The Altar of Renewal needs its whole square and margin loaded, so it needs a player nearby.
- Levelling drops follow loot tables, including other mods' global loot modifiers.
- Minecraft does not record who placed a block, so isolated placed dirt or stone counts as terrain.
- Fake-player claim checks follow FTB Chunks' own fake-player setting.
- Altar of Peace: a modded hostile outside the `MONSTER` category, or a mod that spawns hostiles without NeoForge's spawn events, is not refused.
- Altar of Growth: modded plants that grow by random ticks but are neither tagged nor of a vanilla growing class are not accelerated; add them to `entrelumen:growth_altar_accelerated`. Its ticks do not follow the `randomTickSpeed` gamerule. A single random tick that grows a tree can take a few milliseconds.
- Altar of Time: melee and ranged attack cadences come from mob goals, not attributes, so slowed mobs hit a third as hard but not a third as often. Creeper fuses, custom flight controllers and self-steering projectiles such as shulker bullets are only partly slowed. Projectiles whose mods apply gravity their own way bend more than vanilla ones.
- Altar of Repose: items whose durability is not vanilla damage (energy, custom bars) are not mended.

## Integration checklist

- Register `AltarFullpackGameTests` in `FullpackQABootstrap`, in both the registration and the expected-name list. Run it on the owned QA server with `-Dentrelumen.qa=true`, after archiving the world.
- Register `AltarEffectsFullpackGameTests` the same way (registration and expected-name list). It covers the Torchmaster recipe removal, the Altar of Peace against every installed hostile type, Mystical Agriculture crops in the Altar of Growth and every installed modded projectile in the Altar of Time.
- Add one sentence to the FTB quest text of `signal_exchange`, `nursery_protocol`, `workshop_hands`, `pollinator_treaty`, `horizon_survey` and `sealed_memory` naming the altar each grants, and update the frozen hashes in `tools/test_generate_quests.py`. The rewards themselves are wired in `projects.json` and announced in chat.
- Review the Repose coffer's screen in a client (EN and ES status line width) with the four new blocks' shapes and tooltips.
- Review the shapes against the models, and the lang keys and messages, in a client in EN and ES.
- The optional Mystical Agriculture fertilizer IDs in `renewal_fertilizers` exist in the pinned `MysticalAgriculture-1.21.1-8.0.27.jar` (`fertilized_essence`, `mystical_fertilizer`, also its `c:fertilizers`), checked 24 September 2026.

Owner: root integrates. A bounded worker owns the altars' Java, tests, data and EN/ES text.
