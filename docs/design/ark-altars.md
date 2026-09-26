# Ark altars: the common base and six altars

Decision, 23–24 September 2026. The common base and the first two altars are implemented on branch `feature/altars` (worktree `G:/Elias/Codex/Entrelumen-work/wt-altars`). Isolated evidence is recorded under Verification. Full-pack QA, act rewards and client review are pending integration.

24 September 2026: the Altars of Peace, Growth, Time and Repose, the act rewards of all six altars and the removal of the Mega Torch recipe are implemented on branch `feature/altars-effects` (worktree `E:/Elias/Codex/Entrelumen-ssd/wt-altars2`), on top of `main` ab3796a. Their isolated evidence is under Verification; full-pack QA and client review are pending integration.

Integrated into `main` on 24 September 2026 (merges `ab3796a` and `ba505fd`) and installed in both client profiles and the owned server. Full-pack QA ran on the owned server; see [Integration on main](#integration-on-main-24-september). Client review is pending.

25 September 2026, Elias's redesign (branch `feature/altars-vegetation-terrain`): the Altar of Renewal becomes the vegetation altar and absorbs the Ark Nature module's restoration; the Altar of Levelling renews terrain for free, with the biome's own basic blocks, and gains the terrain repair that the Altar of Renewal did before. See [Altar of Renewal: vegetation](#altar-of-renewal-vegetation) and [Altar of Levelling: free terrain](#altar-of-levelling-free-terrain). The sections on the first design below are history.

Elias's idea (23 September), later extended to six altars. Place a block like an altar and feed it. The first one grows the land back like bone meal, but at long range and not at random: it rebuilds what the world generator actually made there, flowers and plants included, and revitalizes dead soil. A second one makes a smooth building surface that still looks like the land around it. Everything in the pack must be symmetric.

| ID | EN / ES | Role | State |
| --- | --- | --- | --- |
| `renewal_altar` | Altar of Renewal / Altar de Renovación | Grows its biome's vegetation (trees, variants, flowers) and a dye garden that makes all 16 dyes; only fertilizer is spent | Redesigned 25 September |
| `terraform_altar` | Altar of Levelling / Altar de Nivelación | Flattens a centred square with a symmetric slope, or repairs a 49×49 square back to the generator's land, free of materials | Redesigned 25 September |
| `peace_altar` | Altar of Peace / Altar de Paz | No natural hostile spawns in a large square; replaces Torchmaster's Mega Torch | In `main` |
| `growth_altar` | Altar of Growth / Altar de Crecimiento | Much faster crops and more drops. `c:seeds` seeds are not multiplied; Mystical Agriculture crops grow faster but their essence is not multiplied | In `main` |
| `time_altar` | Altar of Time / Altar del Tiempo | Hostile mobs and enemy projectiles in slow motion; much more loot and XP | In `main` |
| `repose_altar` | Altar of Repose / Altar de Reposo | A 4-slot chest (symmetric) that repairs stored gear very slowly, like Mending; needs fuel | In `main` |

## Common base

Every altar is an `AltarBlock` (block) with an `AltarBlockEntity` (block entity) of an `AltarType`. The base provides:

- **Type.** `AltarType(id, fuel tag, ticks per fuel unit)`. Mine are `RENEWAL` (tag `entrelumen:renewal_fertilizers`, 200 ticks, 10 s per unit) and `TERRAFORM` (tag `entrelumen:terraform_fuels`: `#minecraft:coals` and redstone, 1,200 ticks, one minute per unit). A new altar adds a constant. Fuel is the only thing either altar spends.
- **Fuel and active time.** One fuel slot of the type's tag is fed by hand or by any pipe or hopper (insert-only capability). `payActiveTick()` pays one active tick and draws a unit when the paid time runs out; an altar with neither time nor fuel is inactive. `onFuelDrawn()` lets a type give more per unit: the Altar of Renewal adds work charge. The time is paid on top of any work cost the altar already has.
- **Owner.** The owner is the placer, or the first player who feeds it. Work that changes blocks posts the native events as the owner: the online player, or a fake player with the owner's profile. That fake player is created while the owner is online, or else in a tick of its own (see Performance).
- **Registry.** `AltarRegistry` indexes each loaded level's active altars by every chunk their square touches. `isInsideActive(level, type, pos)` and `activeAt(level, type, pos)` answer in constant time whether a position lies inside an active altar of a type; this is what the Peace, Growth and Time altars need for their area effects. Altars register while `working()` is true and unregister when inactive, removed or unloaded. The registry is not saved: each altar persists its own state and registers again when its chunk loads.
- **Area.** Every altar covers a square centred on itself (`areaRadius()` is its half-width). `areaHalfHeight()` can bound it vertically, symmetric above and below the altar; the default is the whole column. `AltarRules.square(r)` lists its columns in concentric square rings, and the shared VoxelShapes cover the modelled plinth, column and cap.
- **Optional inventory.** An inventory of any size, saved with the altar and dropped with it. Containers, menus and pipes see it through `Container`, and `onContentsChanged()` is the hook. It has 0 slots for the Altars of Renewal and Levelling (the Levelling buffer was removed on 25 September) and 4 for the Altar of Repose.
- **Gestures and drops.** Fuel in the main hand feeds the altar. An empty main hand calls `use`, and crouching with it calls `crouchUse` (crouching works even with something in the offhand). Breaking the altar drops its fuel and inventory exactly once.
- **Persistence.** Owner, fuel, paid time, units drawn in the current run and the inventory are saved under `altar`. Each type adds its own fields.

**Obtaining and duplicating.** Altars are not crafted from scratch. They are act rewards, and a copy is made the way smithing templates are: the altar plus expensive materials gives two altars. The duplication recipes are written (`data/entrelumen/recipe/*_duplication.json`), each with the act's prototype component and diamonds around the altar:

- Altar of Renewal: `DPD / PAP / DPD`, with `P` = `entrelumen:propagation_core` (Act III, nature and magic) and `D` = diamond.
- Altar of Levelling: `DHD / HAH / DHD`, with `H` = `entrelumen:handling_core` (Act III, engineering).

The Peace, Growth, Time and Repose recipes follow the same pattern. Which project grants each altar is under [Obtaining the six altars](#obtaining-the-six-altars).

## Overlap and references inspected

Redesign of 25 September 2026 (branch `feature/altars-vegetation-terrain`). Elias's decision, in short: the Altar of Renewal absorbs the Nature module's restoration and becomes the vegetation altar. It grows the trees of the biome or place it stands in, with creative variants, and always flowers that make every dye, paid only with its fuel. The Altar of Levelling asks for no materials: it renews real terrain for free with the biome's basic blocks and ignores ores, building blocks and anything else.

- **Vanilla.** Bone meal on grass spreads short grass and the biome's first flower patch. Saplings grow the biome's trees one at a time. Nothing vanilla grows a whole biome's vegetation over an area.
- **Ars Nouveau 5.13.1.** The Forestation, Flowering and Conjure Island rituals are still the pack's way to create a new forest or change a biome (see `ark-nature-restoration.md`). The altar never changes a biome and grows each biome's own trees.
- **Building Gadgets 2 and the Destruction Gadget** place and destroy player-chosen shapes; they do not follow terrain or the generator. The Altar of Levelling stays the tool that renews land with its own blocks.
- **Trees.** Every tree the altar grows is a configured feature from the loaded registries, run through `FeatureSandbox`: vanilla's `TreeFeatures`, `NetherFeatures` (`crimson_fungus_planted`, `warped_fungus_planted`) and `EndFeatures` (`chorus_plant`), read from the NeoForge 21.1.249 sources jar, and whatever tree features each biome of the pinned mods lists. Their biome and feature JSON were read in the pinned JARs of The Aether 1.5.10, Deep Aether 1.1.5.1, Twilight Forest 4.8.3345, The Undergarden 0.9.6 and Eternal Starlight 0.9.0: almost all their trees are `minecraft:tree` features, which the altar grows unchanged.
- **Giant cactus.** No pinned mod has a saguaro or an arm-shaped cactus block: the blockstates of every pinned JAR were searched for "cactus" and "saguaro". Only Croptopia's `saguaro_crop` (a crop), Creeper Overhaul's `tiny_cactus`, Nature's Aura's `aura_cactus`, Deeper and Darker's `gloomy_cactus` and Eternal Starlight's `lunaris_cactus` exist, and none has arms. `CactusBlock.canSurvive` (sources jar) kills any cactus with a solid block on one of its four sides, so arms cannot bend out of a vanilla trunk. The altar's giant cactus is a candelabra: a trunk of 5–7 and two to four arms of 2–4 on the diagonals, each rooted in sand, never touching face to face.
- **Dye recipes.** Every `data/*/recipe` JSON of the 315 pinned JARs (`catalog/local-paths.json`) was scanned for a result or output that is a dye. The findings that decide the garden are in [Dye garden](#the-dye-garden-and-the-sixteen-dyes). Plant block classes were checked with `javap` in the JARs: Eternal Starlight's `conebloom` and `swamp_rose` are vanilla `FlowerBlock`s (poppy properties, suspicious-stew effects only), and The Undergarden's `ink_mushroom` is a vanilla `MushroomBlock` (`UGMushroomBlock`).
- **References for survival.** `BushBlock`, `FlowerBlock`, `MushroomBlock`, `CactusBlock`, `CocoaBlock`, `TreeFeature`, `TrunkPlacer.setDirtAt`, `HugeFungusFeature`, `ChorusPlantFeature` and `BiomeGenerationSettings.getFlowerFeatures`, from the sources jar.

No item, block, texture or screen is added. The two altars keep their models, names and IDs.

## Altar of Renewal: vegetation

### Gestures

| Gesture | Result |
| --- | --- |
| Fertilizer in the main hand | Stores up to one stack of one fertilizer and starts a pass unless one is running |
| Hopper or pipe | Inserts fertilizer only; nothing can be extracted. A never-started altar starts when fed |
| Empty hand | Status: state, square, progress, what it grows here, grown counts, fertilizer, charge and paid time. Particles mark the square's edge for 5 s |
| Crouch, empty hand | Pause or resume. A finished altar starts a fresh pass if it has fuel |

### Area and the Ark garden

The altar covers a 49×49 square (half-width 24). **Decision: the Ark bonus stays.** The Nature module keeps a single job, marking the team's garden with a bookmarked compass (the old restoration site, same data, same rules: in the Ark's dimension, at most 128 blocks from the module). When the player who starts a pass belongs to a team whose garden lies within 64 blocks, the square is 65×65 (half-width 32). It gives the module a reason to exist after its restoration moved here, keeps every site already marked in existing worlds meaningful, and costs one lookup per pass. Bone meal on the module no longer restores: it says where that work went and uses nothing.

The whole square, every canopy it may grow and their neighbours must be loaded (half-width plus 9); the altar never loads a chunk and waits otherwise.

### What it grows, in two symmetric passes

The square advances in concentric rings from the altar. Pass one grows trees and ink caps; pass two grows the dye garden, flowers and ground plants. Each column has one role, decided only by the world seed and its position (or its offset from the altar, for the garden), so a repeated pass finds the same plan: grown columns are left as they are and harvested ones grow back.

| Role | Where | What |
| --- | --- | --- |
| Garden bed | Offsets ≡ 2 (mod 4) on both axes: one column in 16, never on the altar's cross | The dye species of its orbit (see below) |
| Ink cap | The four diagonal points at ±4·round(r/8): ±12 in the 49 square, ±16 in the 65 one | A huge ink mushroom, the black-dye source |
| Tree | A stable per-column number below the biome's density: its trees per chunk clamped to 1.5–8, over 256 | One of the biome's trees, by weight |
| Flora | 28% of the remaining columns | 40% flowers (35% of them garden species, the rest the biome's own flowers), else the biome's ground plants, else short grass |

The altar's own column and its neighbours stay free, and so do two blocks around each ink cap. A trunk needs no log, stem, sapling, cactus, chorus or huge mushroom within 3 blocks.

### Variants per biome

The first matching family decides the trees. Every other biome, including the Aether, the Twilight Forest, the Undergarden, Eternal Starlight and the other Nether biomes, grows the tree features its own generation lists (any `TreeConfiguration`, vanilla huge mushrooms and fungi, and chorus, including the tree branches of random selectors that also hold fallen logs, rocks or modded shapes, which are left out), at its own density clamped to 1.5–8 per chunk. Flowers and ground plants always come from the biome's own patches (its bone-meal flower features and every random patch of single plants), minus the excluded plants.

| Biome family (match) | Trees and structures (weight) | Trees per chunk | Extras |
| --- | --- | --- | --- |
| Mushroom fields | huge red mushroom (1), huge brown mushroom (1) | 3 | |
| Cherry grove | cherry (1) | 4 | pink petals (biome) |
| Mangrove swamp | mangrove (3), tall mangrove (1) | 5 | lily pads on still water |
| Swamp | swamp oak (1) | 3 | lily pads, blue orchids (biome) |
| Jungle, sparse and bamboo jungle (`#is_jungle`) | giant jungle tree with cocoa (2), jungle tree with cocoa (3), jungle bush (1) | 7 | cocoa: brown dye |
| Old-growth pine and spruce taiga | giant spruce (3), giant pine (2), spruce (1) | 6 | ferns (biome) |
| Taiga (`#is_taiga`) | spruce (4), pine (2), giant spruce (1) | 5 | |
| Snowy plains, ice spikes, snowy slopes, grove | spruce (2), pine (1) | 2 | |
| Savanna (`#is_savanna`) | acacia (4), oak (1) | 2 | |
| Dark forest | dark oak (6), huge red and brown mushrooms (1 each), birch (1) | 8 | |
| Birch and old-growth birch forest | birch (3), tall birch (2) | 6 | |
| Desert (`c:is_desert`) | giant cactus with arms, on sand | 1.5 | dead bushes (biome); cactus: green dye by smelting |
| Badlands (`#is_badlands`) | giant cactus on sand (2), oak on grass (1) | 1.5 | dead bushes |
| Meadow | fancy oak (1), birch (1) | 1.5 | many flowers (biome) |
| Flower forest | oak (2), birch (2), fancy oak (1) | 5 | many flowers (biome) |
| Forest | oak (4), birch (1), fancy oak (1) | 7 | |
| Plains, sunflower plains | oak (3), fancy oak (1) | 2 | |
| Windswept hills, forest, gravelly hills | spruce (2), oak (1) | 3 | |
| Crimson forest | huge crimson fungus, on crimson nylium | 6 | crimson roots (biome) |
| Warped forest | huge warped fungus, on warped nylium | 6 | warped roots, sprouts (biome) |
| The End (`#is_end`) | chorus plant, on end stone | 3 | |

Trees grow whole or not at all. A feature runs in `FeatureSandbox` over the live level (reads outside the square plus margin see bedrock), and only what grows above the ground is kept: dirt a trunk placer lays under the trunk, podzol from decorators and roots driven into mud are dropped, so terrain is never changed, and block entities (bee nests) are never placed. Every remaining block must replace air, a replaceable wild plant (short grass, snow layer) or, for a waterlogged block, still water; none may enter an entity, and every neighbour must be natural. Giant jungle trees and jungle trees get cocoa pods on about a quarter of the free faces of their trunk two to five blocks up, in place of trunk vines.

### The dye garden and the sixteen dyes

Every biome grows the same garden: one species per primary colour, dealt out by orbit. Beds are grouped by their orbit under the square's eight symmetries, the pair (larger, smaller) of |dx| and |dz|; orbits are numbered from the centre out and deal the species in turn, so the garden is a symmetric pattern of coloured rings and each species gets whole orbits (at least 16 beds of each in the 49 square). A bed whose species cannot live there takes the next species that can.

| Primary | Plant the altar grows | Recipe (verified in the pinned JAR) |
| --- | --- | --- |
| White | Lily of the valley | vanilla `white_dye_from_lily_of_the_valley` |
| Red | Poppy | vanilla `red_dye_from_poppy` |
| Yellow | Dandelion | vanilla `yellow_dye_from_dandelion` |
| Blue | Cornflower | vanilla `blue_dye_from_cornflower` |
| Brown | Conebloom (Eternal Starlight 0.9.0) | `eternal_starlight:shapeless/brown_dye_from_conebloom` |
| Green | Swamp rose (Eternal Starlight), and also blue + yellow | `eternal_starlight:shapeless/green_dye_from_swamp_rose`; Utilitarian 0.19.2 `utility/green_dye` (shapeless `c:dyes/blue` + `c:dyes/yellow`) |
| Black | Ink mushroom (The Undergarden 0.9.6), from the caps of the four huge ink mushrooms | `undergarden:ink_mushroom_to_dye` |

The other nine mix with vanilla recipes: orange (red + yellow), pink (red + white), purple (red + blue), magenta (purple + pink), light blue (blue + white), lime (green + white), cyan (blue + green), gray (black + white) and light gray (gray + white). Biomes add more on top: cocoa (brown) in jungles, cactus (green by smelting) in deserts and badlands, and their own flowers.

Why these three mods' plants:

- **Black.** The only plant with a crafting recipe to black dye in the pack is The Undergarden's ink mushroom. The wither rose withers whoever touches it, and the other black sources are not plants (ink sacs, Eternal Starlight's snail shell powder, Forbidden Arcanus' edelwood oil) or need machines (charcoal in a Create millstone or Immersive Engineering crusher; Refurbished Furniture's cutting board slices dried kelp). The ink mushroom is a vanilla `MushroomBlock`: it lives on mycelium, podzol or nylium anywhere, or on any solid block in light below 13. On open grass it cannot, so the altar grows four huge ink mushrooms (`undergarden:huge_ink_mushroom`, a tree feature): each cap block drops 0–2 ink mushrooms with a bare hand (loot `uniform -6..2`, about a third of a mushroom per cap block, some twenty cap blocks per mushroom). Small ink mushrooms also grow in the beds wherever they can live. Harmless; the "seeping ink" under the caps is decorative.
- **Brown.** Only cocoa beans in vanilla, and cocoa needs jungle logs. Eternal Starlight's conebloom is a plain flower on any dirt with a crafting recipe to brown dye. Occultism's `otherflower` recipes need the dye itself.
- **Green.** Vanilla only smelts cactus. Eternal Starlight's swamp rose is a plain flower with a crafting recipe to green dye, and Utilitarian's shapeless recipe turns blue + yellow into green, so the vanilla garden alone already gives green in this pack.

No recipe was invented. The rules are unit-tested with these recipes (`VegetationRulesTest`), and the full-pack case checks every registered biome against the recipes the server actually loads (crafting and smelting only, no machines).

### Soil: the one limit of "always"

The altar never changes terrain, so a plant only grows on ground it can live on. Flowers live on dirt, grass, podzol, mycelium, moss, mud and every modded soil tagged `minecraft:dirt` (Aether grass, deepturf, nightfall grass, uberous soil). In a desert, on a beach, on bare stone, in most of the Nether and in the End there is no such soil: the altar grows giant cacti, dead bushes, fungi or chorus there, and the garden grows only where some dirt or grass exists. A few blocks of dirt laid by a player are enough: placed dirt counts as terrain and becomes a garden bed on the next pass. The status line names what the biome grows; the garden is always part of it.

### Protection

- Only exposed natural ground is planted: the top block under grass and snow layers must be natural ground and the block above it air (for trees, air or a replaceable wild plant).
- Every block written has natural neighbours or belongs to the same unit, so nothing grows against a build, a container, a crop, farmland, a path or placed leaves. No block is placed into an entity.
- Every write posts `EntityPlaceEvent` as the owner; any cancellation restores the whole unit and charges nothing.
- Excluded flora (`entrelumen:renewal_altar_excluded_plants`, data-driven): wither roses and sweet berry bushes hurt, cacti hurt and grow only as the giant cactus, and pumpkins, melons and crops are food.

### Payment

One fertilizer adds 4 work charge and pays 10 s of active time; only ticks that actually work pay time. A plant costs 1 charge. A tree, giant cactus or ink cap costs one per block, up to 16 (4 bone meal). Fertilizer is drawn only when needed; without it the altar waits. A pass over a bare 49 square costs roughly 150–250 bone meal; a repeated pass over grown land costs nothing, and a harvest costs exactly what grows back.

### Progress, persistence and idempotence

The altar saves its state, half-width, pass and next column, charge and totals (trees, ink caps, plants, dye flowers, spots left alone, fertilizer, charge). Schema version 2; a future version is refused. A version 1 altar (the old terrain reconstruction) loads as a fresh vegetation pass with its fertilizer and charge. A unit finishes before the index moves, and the plan is recomputed from the world, so a resumed or repeated pass only grows what is missing.

## Altar of Levelling: free terrain

### Gestures

| Gesture | Result |
| --- | --- |
| Coal, charcoal or redstone in the main hand | Fuel: one minute of work each |
| Empty hand, idle or finished | Preview for 10 s. Flatten: end-rod particles on the flat square's edge and green ones on the slope's outer edge. Repair: green particles on the square's edge |
| Empty hand during the preview | Start |
| Empty hand while working | Pause after the last whole column, or resume |
| Crouch, empty hand, during the preview | Next setting: flatten 9×9, 17×17, 25×25, 33×33, then repair 49×49 |
| Crouch, empty hand, otherwise | Status |
| Hopper or pipe | Fuel only; nothing comes out |

**Decision: flatten and repair live in one altar.** Flatten keeps its sizes, symmetric slope and layers because it is the building tool; repair is the old Renewal terrain pass, moved here because it is terrain work. Both are free of materials, so one altar, one fuel and one gesture cycle cover them. The block item in hand no longer goes into a buffer; there is no buffer.

### Flatten, free

The target, the slope and the layers are unchanged: a flat square at the level the altar stands on, a 4-block smoothstep band on every side projected onto the untouched ring outside it, worked in concentric square rings, symmetric under the eight symmetries of the square. At start the altar samples the ring: the most common top block becomes the surface, the three below it the subsurface, and the block five below the deep material, among plain terrain only. When the ring gives nothing, the biome's own basic blocks stand in: netherrack in the Nether, end stone in the End, red sand over terracotta in badlands, sand over sandstone in deserts and on beaches, mycelium in mushroom fields, grass, dirt and stone elsewhere.

Each column is one atomic unit:

- **Cut.** Plain terrain above the target and loose wild plants (grass, flowers, saplings, snow layers, small mushrooms) are removed. They vanish: the altar keeps nothing, just as it asks for nothing.
- **Fill and swap.** Holes up to 24 deep are filled bottom-up and the top four layers swapped to match, with the palette's blocks, free.
- **Refused whole.** A column is left alone when its touched span holds a build, a block entity, a crop, a fluid source, or natural blocks that are not plain terrain: ores, logs, leaves, cacti, ice, powder snow and the like. The altar never removes, buries or creates them. Land taller than 32 or a hole deeper than 24 also refuses. Claims refuse through the native break and place events.

*Plain terrain* is natural ground without a block entity that is neither an ore nor ice nor powder snow: dirt, grass, podzol, mycelium, mud, sand, red sand, sandstone, stone, deepslate, andesite, diorite, granite, tuff, gravel, clay, snow block, calcite, dripstone, plain terracotta, netherrack, basalt, blackstone, soul sand and soil, nylium and end stone.

### Repair, free

The old Renewal terrain pass, unchanged in its rules and now paid only with fuel:

1. `TerrainReference` regenerates what the level's own generator made in the square plus a one-chunk ring, off the server thread (noise, surface rules and the surface decoration steps; no ores, carvers or structures). Reading it pays nothing.
2. A chunk is trusted when at least 16 of its columns are comparable, at most 10% hold natural ground above the original surface and at least 20% are within one block of it. Other chunks are left alone and counted.
3. Each column is walked down from the original surface while it stays open and must reach existing ground within 16 blocks; a build, a container or a fluid on the way stops it. The refill rises bottom-up while the generator had terrain there, so closed caves, flooded or bottomless pits and overhangs are never touched. Exposed dirt or coarse dirt at the original surface then regains its original cover.
4. The refill places the original block when it is plain terrain. An ore in the original becomes the rock around it (stone, deepslate or netherrack), ice and powder snow become snow blocks, and block entities are never placed: the repair never creates anything of value.

A whole column is one unit: if any of its blocks borders a build (other than land still holding its original block, so badlands terracotta and village edges work), enters an entity or is refused by a claim, nothing is placed.

### Persistence and the old buffer

Setting, repair half-width, flat height, palette, cursor, totals and fuel are saved (schema version 2). A reload resumes at the next column; the repair's reference is rebuilt identically. A version 1 altar kept a 27-slot buffer: its contents are read from the old save and dropped at the altar on its first tick, once, and dropped with the altar if it is broken first. Nothing is lost.

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

The first design's figures follow (history); the redesign's are under [Renewal (vegetation) and Levelling (free terrain)](#renewal-vegetation-and-levelling-free-terrain-25-september). All figures are wall-clock times on Elias's workstation, from the isolated GameTest server. That server ran 51 tests concurrently on the shared, slow G: disk while other servers loaded it too, so treat the figures as upper bounds. Receipts are in `G:/Elias/Codex/Entrelumen-work/altars-build/logs/full-*.log` (`ALTAR_PERF` lines).

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

### Renewal (vegetation) and Levelling (free terrain), 25 September

Isolated GameTest server, 113 tests running concurrently on the SSD (`E:/Elias/Codex/Entrelumen-ssd/altars3-build/logs/gt-4.log`, `ALTAR_PERF` and `ALTAR_TREE_COST` lines). Wall time without GC pauses, per working tick.

**What a tree costs.** A tree is one indivisible unit on the server thread, like a growing sapling. `renewalAltarTreeCostAgainstVanillaGrowth` warms each feature up and then times 20 sandboxed simulations against the same feature placed straight into the level, which is what a sapling does when it grows. The sandbox costs no more than vanilla growth (it skips lighting and neighbour updates); the altar's own checks and the commit come on top, at about the cost of vanilla's placement.

| Feature | Sandbox p50 / p95 / max | Vanilla placement p50 / max |
| --- | --- | --- |
| oak | 1.6 / 2.1 / 2.7 ms | 2.1 / 3.6 ms |
| birch | 1.2 / 2.2 / 2.7 ms | 1.5 / 2.9 ms |
| fancy oak | 2.0 / 5.8 / 6.1 ms | 5.3 / 8.4 ms |
| spruce | 0.7 / 1.2 / 1.2 ms | 1.9 / 2.5 ms |
| acacia | 0.8 / 0.9 / 1.0 ms | 1.3 / 1.7 ms |
| dark oak | 1.4 / 2.4 / 2.5 ms | 2.4 / 2.8 ms |
| cherry | 2.2 / 3.5 / 12.8 ms | 4.3 / 5.3 ms |
| jungle tree | 1.7 / 2.1 / 2.2 ms | 1.8 / 2.1 ms |
| giant spruce | 5.5 / 11.8 / 11.8 ms | 8.8 / 10.2 ms |
| giant jungle tree | 6.4 / 7.6 / 7.7 ms | 7.4 / 9.0 ms |
| huge red mushroom | 0.1 / 0.2 / 0.2 ms | 0.5 / 1.3 ms |

The first tree after a server start pays class loading and JIT: 25–38 ms in these runs.

**Pacing.** A tree is simulated only at the start of a tick, never twice for want of budget, and the tick ends after it. The altar then rests ⌈cost / 2 ms⌉ − 1 ticks (at most 200), so a 10 ms giant spruce buys 4 idle ticks and the altar's average stays near the 2 ms tick budget. Plants and repair columns keep the old budget: 32 writes (48 for Levelling), 256 inspected columns and 2 ms per tick. Palettes are resolved once per biome and cached (about 5 ms per biome the first time, then a map lookup). Nothing generates or loads chunks on the server thread: the repair's reference runs on the altar worker thread as before.

| Scenario (`gt-4.log`) | Working ticks | p50 | p95 | Max | Notes |
| --- | --- | --- | --- | --- | --- |
| Renewal, forest 25×25 (8 trees, 148 plants) | 30 | 3.5 ms | 30.9 ms | 42.9 ms | includes the cold first tree (30 ms) under 113 concurrent tests; rested 23 ticks |
| Renewal, desert (4 giant cacti, 128 plants) | 12 | 2.1 ms | 3.3 ms | 3.3 ms | |
| Renewal, plains after a reload | 8 | 1.4 ms | 2.1 ms | 2.1 ms | |
| Levelling repair, pits (84 blocks) | 12 | 2.4 ms | 6.9 ms | 6.9 ms | |
| Levelling repair after a reload (108 blocks) | 4 | 1.6 ms | 2.6 ms | 2.6 ms | |
| Levelling flatten 9×9 with slope | 8 | 2.2 ms | 2.3 ms | 2.3 ms | |
| Levelling flatten 17×17 resumed | 11 | 2.3 ms | 2.4 ms | 2.4 ms | |

The repair's reference, off the server thread, is unchanged: 127 ms per terrain chunk and 33 ms per decorated chunk for the overworld noise generator in this run. A biome's palette is resolved once, on the server thread, the first time an altar works in it: about 0.4 ms per biome (28.5 ms for the 65 vanilla biomes, cumulative `paletteMicros`). In the full pack, resolving the palettes and harvests of all 183 biomes and closing them over the 21,878 loaded recipes took 4.0 s in all, at most 28 ms for one biome; that is the QA case's own cost, not the altar's. TPS with players and the full pack has not been measured.

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
- **Full-pack.** `AltarFullpackGameTests` covers the Altar of Renewal against the pack's real overworld generator, compared with an independent regeneration, and real FTB Chunks claims against a foreign team's Altar of Renewal and Altar of Levelling. It was registered and run at integration; see [Integration on main](#integration-on-main-24-september).

**History.** The first run (`full-1.log`) found test-fixture bugs:

- A leaf veto that was not bounded to its template broke the concurrent Nature forest test.
- The save/reload test had a race.
- A claim was removed before the repeated pass.
- A test had too little fill material.

That run also found the fake-player stall, and it reported one reference-digest mismatch in the forest test. The mismatch did not reproduce in the seven later runs, and the forest test now checks three regenerations. Integration reproduced it and found the cause: bee nests (see [Integration on main](#integration-on-main-24-september)).

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
- **Full-pack.** `AltarEffectsFullpackGameTests` was registered and run at integration; see [Integration on main](#integration-on-main-24-september).

### Integration on main (24 September)

Root integration in `C:/Users/elias/Documents/Codex/2026-09-12/h/outputs/entrelumen`, with the build and run folders on the NVMe `E:/Elias/Codex/Entrelumen-ssd`. Receipts are in `E:/Elias/Codex/Entrelumen-ssd/head-ab3796a-20260924`; the evidence is `docs/verification/altars-runtime.json`.

**Build and isolated tests.** Offline `gradlew --offline --no-daemon build qaJar runGameTestServer`, pinned JDK 21.0.12.1+1.

- The merge of the first two altars (`ab3796a`): JUnit 104 passed. The first isolated run passed 52 of 53: the forest test's digest mismatch came back.
  - **Cause: bee nests.** The two references differed only in a bee nest, one block apart. Vanilla's `BeehiveDecorator` picks the nest's side with an unseeded `Collections.shuffle`, the only unseeded randomness in vanilla world generation. So no regeneration can reproduce where a nest went.
  - **Fix in the sandbox.** Nests stay virtual. The decorator still gets its hive and draws its bees from the feature random, so the random stream is unchanged. The reference keeps air there, and the altar never restores block entities anyway.
- The next two runs failed the pits and reload tests on their tick limits. One of them also failed the forest test's own setup: at that random position its reference had no tree to obstruct in the west half. That did not recur in the eight later runs.
  - **Cause.** The GameTest server ticks without pausing, so a tick limit is short in wall time. The overworld-noise test's three regenerations queued ahead of those altars' references on the shared altar worker.
  - **Fix.** That test now regenerates on its own thread. Then all 53 passed in five consecutive runs.
- After merging the Peace, Growth, Time and Repose altars (`ba505fd`): JUnit 115 passed, and all 57 isolated GameTests passed in three consecutive runs.

**Full pack.** The owned server on E: ran the QA JAR with `-Dentrelumen.qa=true`, a 6 GB heap and `max-tick-time` 60000. The world was archived before each QA run, and restored from that archive after any run that left QA data behind. All eight Apotheosis cases passed again, before and after the second merge.

- `renewalAltarRebuildsLandFromThePacksOwnGenerator` **passed twice**.
  - It cannot run next to spawn. A server without players loads only the spawn chunks: `execute if loaded` is false 48 and 68 blocks east. Earlier tests also replaced that land with barriers and stone test floors.
  - It ran on untouched land: 20 chunks force-loaded around (160, −69), chosen from the region files as open grass, and `execute positioned 112 70 -76 run test run …`.
  - The altar refilled all 27 pit blocks exactly as the independent regeneration has them. It also regrew 2 trees and 5 plants, guarded 3 columns, skipped no chunk and used 13 bone meal.
  - The reference took 1.0–1.8 s for 16 terrain chunks and 0.11–0.22 s for 4 decorated ones. It skipped `pneumaticcraft:oil_lake_underground` once and `waystones:waystone` four times, because they need the live level.
  - A first site 2,000 blocks from spawn was all ocean. Force-loading its 144 new chunks stalled the server for 26 s.
- `altarsRespectForeignFtbChunksClaims` **passed** after three fixes to the test.
  - Mock player names must fit 16 characters: FTB Teams syncs names with the vanilla codec. The 17-character visitor failed at login and left both mock players online. It also left a team record that would break every later login, so the world was restored.
  - The Altar of Levelling had no fuel, so the case waited out its limit and skipped its cleanup.
  - With the land already level, there was nothing to fill outside the claim.
  - With these fixed, the real claim refused the renewal inside the claimed chunk at no charge, and the Altar of Levelling refused the claimed columns but filled a pit outside. The case now also unclaims and logs its players out when a wait expires.
- `torchmasterKeepsItsLightsButLosesTheMegaTorch`, `peaceAltarRefusesEveryInstalledHostileNaturalSpawn` (186 hostile types, none allowed) and `mysticalCropsGrowFasterButTheirEssenceIsNotMultiplied` (inferium essence 400 inside and 400 outside) **passed**.
- `installedHostileProjectilesAreSlowedByTheAltarOfTime` **passed** after four fixes to the test. Its first versions crashed the QA server three times, and each time the world was restored.
  - **Crashes.** Every modded projectile type was created bare, without the data its launcher sets, and 20 shared lanes let them hit each other. The crashes: Immersive Engineering's revolver flare on hitting a projectile, Aquaculture's bobber on its first tick, and Ars Nouveau's wall casting on nearby entities without an emitter.
  - **Separate slots.** Each pair now flies in its own slot, in batches.
  - **Probe.** A detached copy first flies three ticks, to skip types whose own tick fails.
  - **Erroring entities.** NeoForge's `removeErroringEntities` is set in memory for this case only. The setting is marked world-restart, so its cache is cleared, and it is restored and checked afterwards; `neoforge-server.toml` was unchanged.
  - **Self-steering.** The case judges free flight on each tick and reports self-steering projectiles, a documented limitation, instead of failing on them.
  - **Result.** 174 projectile types were slowed. Three steer themselves and were reported: `ars_nouveau:orbit` and Twilight Forest's `chain_block` and `cube_of_annihilation`. 28 do not fly on their velocity, and 16 were removed within three ticks, three of them because their tick threw. Two failed their detached tick.

**Installation.** The final code, `eabad96`, was installed in `ENTRELUMEN`, `ENTRELUMEN Defaults QA` and the server with the receipt-managed installer: 42 files each, 21 of them new. They are the QoL script, the companion JAR, and the altar, shelf and module art in `resourcepacks/entrelumen`. Nothing was retired, no JAR was added, and player and local files were unchanged. Later commits changed only the QA source set, so the normal JAR is byte-identical.

The CI check `tools/check_runtime_content.py` then found the runtime audit script stale, because the altars had become rewards. `6a743eb` synced it and was installed the same way, one file per profile. A normal boot with it passed the runtime content audit: 128 items and 36 recipe/output pairs, including the six altars and their duplication recipes.

### Vegetation and free terrain (`feature/altars-vegetation-terrain`, 25 September)

Offline `gradlew --offline --no-daemon build qaJar runGameTestServer` in the worktree `E:/Elias/Codex/Entrelumen-ssd/wt-altars3`, pinned JDK 21.0.12.1+1 and NeoForge 21.1.249, build and run folders on `E:/Elias/Codex/Entrelumen-ssd/altars3-build` (logs in `altars3-build/logs`, receipts in `E:/Elias/Codex/Entrelumen-ssd/altars3-20260925`).

- **JUnit: 229 passed.**
  - `VegetationRulesTest` (7, new): the garden lattice is symmetric under the eight symmetries of the square, off the altar's cross, one column in 16, and deals every species whole orbits in the 29, 49 and 65 squares (at least 16 beds each in the 49 one); the four ink caps sit on the diagonals off the lattice; roles are stable and follow their shares; tree density clamps; giant cacti are 5–7 tall with two to four diagonal arms that never touch face to face; the dye closure needs every slot and reaches through chains; and the garden's harvest makes all sixteen dyes with the pinned mods' recipes, loses exactly black, brown, gray and light gray without them, and loses green, lime and cyan too without Utilitarian's blue + yellow.
  - `TerraformRulesTest` (10): the conservation ledger tests were replaced by protected blocks (an ore in the surface layers, a trunk above the target) refusing their column and by the repair as the fifth setting of the cycle.
  - `NatureRestorationRulesTest` (5): the disc and density tests went with the restoration; distance, stable numbers, weighted choice and site persistence stay.
- **Isolated GameTests: all 113 passed** (`gt-4.log`), 12 of them new or rewritten in `RuntimeGameTestsAltars`, plus the Nature module test in `RuntimeGameTests`.
  - `renewalAltarGrowsTheForestAndASymmetricDyeGardenSparingBuilds`: fed by the native gesture in a forest, it grows oaks, birches and big oaks, 140–160 plants and 40–50 dye flowers in a 25 square. Every bed holds its orbit's species and every orbit is one colour; at least 70% of the beds flower (the rest border the chest, the planks or claimed land). Nothing below the ground or where something stood changed; nothing touches a build; a chest, planks, farmland with wheat, an armor stand and simulated claimed land are left alone. Payment is exact. A repeated pass grows nothing and charges nothing; three harvested beds grow back the same flowers for exactly three charge.
  - `renewalAltarGrowsGiantCactiInTheDesertAndFlowersOnlyOnSoil`: a giant cactus grows whole through the altar's checks, cacti survive their neighbour updates, dead bushes grow on sand, no flower grows on sand, and the one bed on a patch of grass flowers.
  - `renewalAltarGrowsOverworldVariantsPerBiome` and `renewalAltarGrowsNetherEndSwampAndDesertVariants`: in their own biomes and soils, a giant spruce (≥20 logs), a giant jungle tree with cocoa, a huge red mushroom on mycelium, a cherry, a huge crimson fungus on nylium, chorus on end stone, a mangrove on mud and a giant cactus on sand all grow, and no soil block changes.
  - `renewalAltarHarvestMakesTheDyesOfItsVanillaGarden`: over all 65 vanilla biomes and 22 palette families, the harvest makes white, red, yellow, blue and everything they mix into with the loaded recipes; jungles add brown (cocoa) and deserts green (cactus). What is missing is only what the pinned mods supply.
  - `renewalAltarProgressSurvivesSaveAndReloadAndMigratesVersionOne`: pass, column, charge, fertilizer, totals and radius round-trip, the registry reports the square, and a version 1 save starts a fresh vegetation pass with its charge.
  - `renewalAltarTreeCostAgainstVanillaGrowth`: the tree costs in Performance.
  - `levellingAltarFlattensForFreeWithLayersAndSlopeSparingOresAndTrees`: driven by gestures through all five settings; the altar takes no materials by pipe, flattens with grass over dirt, the slope rises, claimed columns, a chest and planks are left alone, and an iron ore in the hill and a young trunk refuse their columns and stay. No drops; a repeated run changes nothing.
  - `levellingAltarPausesOnWholeColumnsAndResumesAfterReload`: no work without fuel, whole columns at the pause, a full round trip and a resumed run.
  - `levellingAltarRepairsPitsToTheOriginalSurfaceAndSparesBuilds`: the old renewal pits case on the Altar of Levelling, fed coal by gesture. Pits are refilled with grass over dirt, an iron ore layer in the original comes back as stone, bare dirt is re-covered, and a chest, planks, a claimed column and a pit outside the square are left alone. Everything placed is plain terrain; a repeated run changes nothing.
  - `levellingAltarRepairProgressSurvivesSaveAndReload` and `levellingAltarHandsBackItsOldBufferOnce` (a version 1 buffer of 40 dirt and 12 cobblestone drops once at the altar and is not saved again).
  - `natureModuleMarksTheGardenSiteAndNoLongerRestores` (in `RuntimeGameTests`): see `ark-nature-restoration.md`.
- **Python checks.** The Python steps of `.github/workflows/verify.yml` plus `tools/check_guides.py`, `generate_family_balance.py`, `generate_rftools_balance.py`, `generate_resource_balance.py`, `generate_cooking_provisions.py`, `generate_malum_compat.py` and `curate_pack.py --check`: all pass (receipt `ci-checks-*.log`).
- **Full pack, on `server-slice`** (QA JAR of `ad3277f`, `-Dentrelumen.qa=true`, 6 GB, `max-tick-time` 60000). The world was zipped and every file hashed first, and after each run the world and the installed companion JAR were restored from that backup; the QA worlds are kept beside it as `entrelumen-test.__after-altars3-qa-*` (receipts `slice_state.py`, `server-world-before-altars3.json`, `restore-*.json`). Final run C (`qa-C.json`): **all six cases passed.**
  - `renewalAltarHarvestMakesEveryDyeInEveryBiome`: the pack's garden is complete (all seven species and the huge ink mushroom resolve) and alone makes all sixteen dyes; over **all 183 registered biomes** (vanilla, the Aether and Deep Aether, Twilight Forest, the Undergarden, Eternal Starlight, the Bumblezone, Ad Astra, Deeper and Darker, and the pack's dimensions), the harvest makes every dye with the 21,878 crafting and smelting recipes the server loaded. No biome failed.
  - `renewalAltarGrowsTheTreesOfModdedDimensions`: on their own soils and biomes, the altar grew the Aether's golden oak (skyroot forest), the Undergarden's smogstem, Eternal Starlight's lunar tree (starlight forest, a selector that also holds huge mushrooms) and Twilight Forest's dark-forest birch, all through their own features.
  - `renewalAltarGrowsInkCapsAndModFlowersOnPlainSoil`: a huge ink mushroom grew on grass (57 cap blocks); conebloom, swamp rose and the four vanilla species live on grass; the small ink mushroom does not (it needs shade or mycelium, where it lives).
  - `levellingAltarRepairsLandFromThePacksOwnGenerator`: on open land 48 blocks east of the test, found by the test itself, the repair refilled all 27 pit blocks as the independent regeneration of the pack's generator has them, recognised every chunk and used one coal. The reference took 1.3 s for 16 terrain chunks and 0.1 s for 4 decorated ones; it skipped `pneumaticcraft:oil_lake_underground` once and `waystones:waystone` four times, as before.
  - `altarsRespectForeignFtbChunksClaims`: a real FTB Chunks claim refused the foreign Levelling repair inside the claimed chunk while the pit outside was refilled, then refused 135 claimed columns of its flatten (the chunk unchanged, fills outside), and the foreign Altar of Renewal planted 47 plants and 3 trees outside while the claim refused 56 plants and 9 trees inside, charging nothing for them.
  - `natureModuleMarksTheGardenSiteAndNoLongerRestores` passed with the real Ark and team data.
  - **Earlier runs.** Run A found four test bugs, now fixed: the dye case ran all biomes in one 33 s tick; Eternal Starlight's forest selector mixes trees with huge mushrooms, which the first resolution skipped whole (the altar now takes the tree branches of any selector); the claim case snapshotted the claimed chunk before placing its own altar there; and the spawn-side repair spot had no open ground. Run B passed four cases, then the server's watchdog stopped it at the start of the claim case: the harness had run the dye case and four player logins back to back, the server fell a minute behind, and the watchdog's stack was in an Integrated Dynamics item entity's move, not in altar code. Run C gave each case time to settle and passed everything; its worst lag was 2.4 s, during boot. The world was restored after each run.

## Limitations

- Altar of Renewal: flowers need soil they can live on, and the altar never changes terrain. On sand, bare stone, netherrack or end stone the garden grows only where some dirt or grass exists; a few blocks laid by a player are enough. See [Soil](#soil-the-one-limit-of-always).
- Altar of Renewal: the dye guarantee rests on three pinned mods (Eternal Starlight's conebloom and swamp rose, The Undergarden's ink mushroom and its huge ink mushroom feature, and Utilitarian's blue + yellow recipe as a second green). Without them the garden still makes white, red, yellow, blue and everything they mix into, but not black, brown, gray or light gray. The full-pack case fails loudly if a pack update drops one of them.
- Altar of Renewal: biomes whose trees are modded feature types without a tree configuration (for example Twilight Forest's hollow trees, or trees mixed with fallen logs in one selector) grow none of those; a feature that fails in the sandbox is skipped. Curated families replace the biome's own trees in vanilla biomes.
- Altar of Renewal: a tree is simulated whole on the server thread, as vanilla does when a sapling grows; a giant tree can take tens of milliseconds. The altar then rests in proportion (see Performance), so its average stays near 2 ms a tick, but a single tick can still be that long.
- Altar of Renewal: it needs its whole square and margin loaded, so it needs a player nearby.
- Altar of Levelling: repair refills pits no deeper than 16 blocks below the original surface, and never overhangs, closed caves, flooded pits or carver ravines. Where neighbouring chunks' features overlapped during real generation, the reference may differ slightly from the original. Chunks next to a structure whose start is not loaded are skipped, and structure pieces are never rebuilt. Modded chunk generators get no surface-rule pass.
- Altar of Levelling: columns with an ore, a tree, ice or a build in the touched span are refused whole, so flattening a forest or a hill with ore leaves pillars until the trees are felled and the ore is mined. Cut blocks vanish.
- Minecraft does not record who placed a block, so isolated placed dirt or stone counts as terrain.
- Fake-player claim checks follow FTB Chunks' own fake-player setting.
- Altar of Peace: a modded hostile outside the `MONSTER` category, or a mod that spawns hostiles without NeoForge's spawn events, is not refused.
- Altar of Growth: modded plants that grow by random ticks but are neither tagged nor of a vanilla growing class are not accelerated; add them to `entrelumen:growth_altar_accelerated`. Its ticks do not follow the `randomTickSpeed` gamerule. A single random tick that grows a tree can take a few milliseconds.
- Altar of Time: melee and ranged attack cadences come from mob goals, not attributes, so slowed mobs hit a third as hard but not a third as often. Creeper fuses, custom flight controllers and self-steering projectiles such as shulker bullets are only partly slowed; in this pack, also Ars Nouveau's orbit and Twilight Forest's chain block and cube of annihilation. Projectiles whose mods apply gravity their own way bend more than vanilla ones.
- Altar of Repose: items whose durability is not vanilla damage (energy, custom bars) are not mended.

## Integration checklist

- Done 24 September: `AltarFullpackGameTests` and `AltarEffectsFullpackGameTests` are registered in `FullpackQABootstrap` (registration and expected names), and all six cases pass on the owned server. The renewal case needs untouched, loaded land away from spawn; see [Integration on main](#integration-on-main-24-september).
- Add one sentence to the FTB quest text of `signal_exchange`, `nursery_protocol`, `workshop_hands`, `pollinator_treaty`, `horizon_survey` and `sealed_memory` naming the altar each grants, and update the frozen hashes in `tools/test_generate_quests.py`. The rewards themselves are wired in `projects.json` and announced in chat.
- Review the Repose coffer's screen in a client (EN and ES status line width) with the four new blocks' shapes and tooltips.
- Review the shapes against the models, and the lang keys and messages, in a client in EN and ES.
- Done 25–26 September: `AltarFullpackGameTests` (dye coverage over every registered biome, modded trees, ink caps, the repair against the pack's generator and the claims) and the Nature module case pass on `server-slice`; see [Vegetation and free terrain](#vegetation-and-free-terrain-featurealtars-vegetation-terrain-25-september). `NatureRestorationGameTests` was removed from `FullpackQABootstrap`.
- Pending: review the new status lines, tooltips, the Nature module's garden row and the Atlas guide in a client in EN and ES, and a survival look at a grown 49 square (density, garden rings, giant cacti). Install the release JAR in the client profiles and the server with the managed installer; this branch did not.
- The optional Mystical Agriculture fertilizer IDs in `renewal_fertilizers` exist in the pinned `MysticalAgriculture-1.21.1-8.0.27.jar` (`fertilized_essence`, `mystical_fertilizer`, also its `c:fertilizers`), checked 24 September 2026.

Owner: root integrates. A bounded worker owns the altars' Java, tests, data and EN/ES text.
