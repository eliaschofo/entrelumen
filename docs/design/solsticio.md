# Solsticio — technical base of act VI

Direction: `docs/design/story-bible.md` (24 September 2026). Solsticio is the Heliodor capital frozen inside the Entrelumen. This document records the technical skeleton on branch `feature/solsticio`: the dimension, the one-time city placement and its template contract, the common structure protection (also meant for the Overworld ruins), the Light Key and the definitive portal. Missions, named villagers, the trading hall's trades and all final art are out of scope.

Nothing here is accepted art. Models and textures are vanilla references marked `PROVISIONAL`; the in-game look of the sky, motes and portal has not been reviewed on a client.

## Act numbering

The code still has six campaign acts with the Ark commissioned and activated in act 6 (`CampaignMilestones.LAST_HORIZON`). The story bible moves the Ark to act V and Solsticio to act VI; that renumbering is not done. Solsticio therefore opens to a team through one gate, `Solsticio.GATE = ActGate(6, "last_horizon")`: campaign act at least 6 **and** the Ark activation recorded. When acts are renumbered, only that constant changes.

## Dimension `entrelumen:solsticio`

| File | Content |
| --- | --- |
| `data/entrelumen/dimension_type/solsticio.json` | `fixed_time` 6000 (eternal noon), sky light, `ambient_light` 0.3 so interiors are never black, `natural` false (no sleeping, compasses and clocks drift), beds do not explode, no raids, no respawn anchors, height 0–319, `effects` `entrelumen:solsticio`. |
| `data/entrelumen/dimension/solsticio.json` | `minecraft:flat` with **no layers**, no features, no lakes, no structures: empty void except the city. |
| `data/entrelumen/worldgen/biome/solsticio.json` | No precipitation, empty spawners and features. White-gold sky `#FFF1C8`, warm fog `#FFF6DF`, turquoise water `#3FD3C4`. |

References read before writing these (NeoForge 21.1.249 client-extra JAR, Minecraft 1.21.1 data): `data/minecraft/dimension_type/the_end.json` (fixed time 6000 with `natural` false: the model for a timeless dimension, but without sky light and with hostile light levels, which Solsticio does not want), `data/minecraft/worldgen/flat_level_generator_preset/the_void.json` and `worldgen/biome/the_void.json` (void generation; Solsticio drops even the air layer and the start platform).

Runtime additions:

- **No weather.** Every dimension shares the Overworld's weather data, so rain would darken the sky. `SolsticioWeatherMixin` keeps Solsticio's rain and thunder levels at zero and never sends it weather packets.
- **No hostile spawns.** Empty biome spawners plus a `MobSpawnEvent.PositionCheck` refusal for monsters (anything but spawn eggs and commands), so biome modifiers from other mods cannot add them.
- **Bounded world.** Solsticio's own world border is centred on the origin with radius `max(128, city extent + 48)`; it is re-applied every 10 s because vanilla copies Overworld border changes to every dimension. Players below the city floor −24 or beyond the border are brought back to the arrival point, so the void never kills anyone. No terrain is generated anywhere: only empty chunks inside the border and the view distance.
- **Client presentation** (`client/SolsticioClient`): special effects without clouds, rain, sunrise tint or fog darkening; optional drifting motes of light (pale-gold dust, some `end_rod`) around the player, client-side only. Config `entrelumen-client.toml`: `solsticio.ambientMotes` (default true) and `solsticio.moteDensity` (0–8, default 2); the vanilla particle setting halves them on *Decreased* and disables them on *Minimal*.

## City templates — contract for the controller

**Where.** `companion/src/main/resources/data/entrelumen/structure/solsticio/`. Either one `city.nbt`, or a grid of `piece_<x>_<z>.nbt` (x, z ≥ 0). Never both: the loader refuses the mix. Other file names in that folder are ignored with a warning.

**Size.** Any size; the 48-block structure-block limit does not apply. The loader reads each piece's `size`, runs the vanilla structure datafixer when its `DataVersion` is older than 1.21.1 (3955), and never assumes a maximum.

**Position.** Template layer 0 lands on world `y = 64` (`CityLayout.BASE_Y`). The footprint is centred on x = z = 0: for a single `city.nbt` its minimum corner goes to `(-floor(sizeX/2), 64, -floor(sizeZ/2))`. For a grid, each grid column is as wide as its widest piece and each grid row as deep as its deepest; pieces keep their own origin in their cell, and the whole grid is centred. Keep pieces of one column the same width to avoid gaps.

**Content.** Placement is a vanilla `StructureTemplate` placement with known shapes: block states, block entities and their NBT, entities and fluids are placed as saved; saved air overwrites; positions missing from the file stay void. The city's own water may keep flowing inside it; nothing flows in from outside. Jigsaw and structure blocks other than markers are placed as blocks, not processed.

**Markers.** Vanilla structure blocks in DATA mode (`minecraft:structure_block[mode=data]` with block-entity NBT `mode: "DATA"` and `metadata: "<id>"`). They become air when placed, so put each one on the free layer where the thing stands. `metadata` accepts `id` or `entrelumen:id`, any case.

| `metadata` | Count | Meaning |
| --- | --- | --- |
| `arrival` | 1 | Feet position where the Light Key, the Overworld rift and void rescues land, facing the portal. If missing, the loader logs an error and uses the highest free block at x = z = 0. |
| `town_hall_portal` | 1 | Feet position of the portal anchor (`entrelumen:solsticio_portal`, placed dormant by the loader) inside the Ayuntamiento. |
| `town_hall_waystone` | 0–1 | Lower block of the global waystone placed when the portal opens and Waystones is installed (it is two blocks tall; keep the block above free). Without it the paired-portal fallback is used. |
| `trading_hall` | 0–1 | Reference point of the trading hall; recorded for the future trade system. |
| `player_plot` | any | North-west corner of one 16 × 16 plot, on its first free layer. The plot extends +15 in x and z, 5 blocks down and 40 up from the marker. Plots are numbered by (z, x) of their markers. |
| `mayor`, `inventor`, `gardener`, `priest` | 0–1 each | Stand points of the named villagers; recorded for the future NPC system. |
| `provisional` | 0–1 | Only in placeholder templates: logs a warning and marks the city provisional. The definitive city must not carry it. |

Unknown marker names are logged and ignored.

**Placement.** Once per world, the first time something needs the city: the Ark activation (key forging), a Light Key use, anyone entering the dimension, or `/entrelumen admin solsticio place`. Off the server thread, pieces are read (the datafixer only runs for older `DataVersion`s) and cut into 16-block cubes aligned to world chunk sections; empty cubes are dropped. Meanwhile a non-ticking chunk ticket (`entrelumen_solsticio`) loads the footprint's chunks in the background, so nothing is generated inside a tick. The server then places whole cubes, bottom-up per column, until 15 ms of the tick are spent (always at least one cube). Progress (`slicesDone`) is saved, so a restart resumes where it stopped; a changed template list or cutting format during an interrupted placement restarts it. When done, the ticket is released, `SolsticioData.placements` becomes 1 and the city is never placed again: a template changed after a world placed the city needs an explicit migration, not a re-run.

Measured in the isolated GameTest server, with about 50 other tests running at the same time: the controller's 261,316-block city took 47 ticks, 0.9 s of placing in total, worst tick 29 ms, 11.6 s wall clock including the background read and chunk loading. A first version that placed 32 × 32 full-height columns and let the tick generate chunks spent 4.7 s in 7 ticks, with a 1.3 s worst tick.

**Current template.** `solsticio/city.nbt`, made by the controller with `city.py` (outside the repository, in `G:/Elias/Codex/Entrelumen-work/art-redo-20260923/solsticio/`; regenerate there, never edit by hand). SHA-256 `700713c64ee1e0cb937e12550e2f7a4c4a05675848a0f1c466307dbc2034a595`, 105 × 78 × 105, DataVersion 3955, 51 palette states, 261,316 blocks, no entities, no `provisional` marker. The island's lowest layer is template layer 1 and the plaza floor layer 52, so with `BASE_Y` 64 the plaza stands at world y 116: arrival (0, 118, 4), portal (0, 118, 0). `BASE_Y` stays at 64 on purpose: the whole island stays above the y 63 horizon line, where vanilla starts drawing the dark lower sky, and falls into the void are caught long before the void itself. The provisional 61 × 12 × 61 placeholder and its generator (`tools/build_solsticio_provisional.py`) remain only in commit `5081b7b`.

## Protection (common system)

Pure model in `ProtectionRules`, runtime in `StructureProtection`.

- **Region** = stable id, dimension id, inclusive box, `ActGate` (act and optional milestone) and holes. **Hole** = box plus either *open* (exempts everyone) or *claimable* (a plot: protected until a campaign claims it, then only that campaign may build there). Overlapping regions must all allow an action; a hole of one region never opens another.
- **Sources.** Providers registered with `StructureProtection.registerProvider(id, server -> regions)` are read whenever the index is rebuilt (`StructureProtection.invalidate(server)` after their data changes); transient regions (`addTransient`/`removeTransient`) serve tests and temporary sites. The index is per dimension and per chunk, allocation-free when a dimension has no region.
- **Ruins.** `feature/compass` is not merged, so nothing references `RuinData` yet. After merging, one provider protects every placed ruin at its act:

  ```java
  StructureProtection.registerProvider("entrelumen:ruins", server -> RuinData.get(server).ruins().stream()
      .map(r -> StructureProtection.ruin(r.id(), r.dimension().location(), r.box(), r.act())).toList());
  ```

  plus `StructureProtection.invalidate(server)` after `RuinData.add`.
- **Solsticio** contributes one region: the whole bordered square, full height, gated by `Solsticio.GATE`, with one claimable hole per plot. The paired Overworld rift (fallback only) gets a small ungated region.

What is refused, always on the logical server at the moment it happens (spectators, noclip clients and creative mode included):

| Vector | Hook |
| --- | --- |
| Breaking, mining start | `BlockEvent.BreakEvent`, `PlayerInteractEvent.LeftClickBlock` |
| Placing (single and multi-block) | `BlockEvent.EntityPlaceEvent` / `EntityMultiPlaceEvent`, judged where the block lands |
| Items used on guarded blocks (tools, flint, bone meal, spawn eggs...) | `RightClickBlock` → `useItem = FALSE`; `BlockToolModificationEvent` |
| Buckets | `RightClickItem`: both aims (source fluid and solid) and the adjacent block |
| Explosions | `ExplosionEvent.Detonate`: guarded blocks and decorations removed from the lists |
| Pistons | `PistonEvent.Pre`: head, every moved block, its destination and destroyed blocks |
| Fluids | `FluidSpreadMixin` on `FlowingFluid.canSpreadTo`, `FluidPlaceBlockEvent` (lava fire, stone, obsidian) |
| Fire | `FireSpreadMixin`: no burning (`checkBurnOut`) and no spreading (`getIgniteOdds`) into guarded blocks |
| Mobs, trampling | `EntityMobGriefingEvent` (mobs in guarded blocks), `LivingDestroyBlockEvent`, `FarmlandTrampleEvent` |
| Frames, paintings, armor stands | `EntityInvulnerabilityCheckEvent` (every damage source), `EntityInteract(Specific)` gated |

Right-clicks on guarded blocks fall in three classes: **free** (tag `entrelumen:protection/usable`: doors, trapdoors, gates, buttons, levers, bells, beds, stateless workstations, ender chests; also any block without block entity that opens a menu), **gated** (tag `entrelumen:protection/gated`, lecterns, Ark altars, and any block entity that is a container or exposes an item handler: chests, barrels, pots, shelves, jukeboxes, modded machines) and **locked** (everything else that would change the block, plus tag `entrelumen:protection/locked`, anvils). Gated blocks open when the acting team's campaign passes the region's gate; nobody may break them. A frame's shown item may be taken by an unlocked team; the frame stays. Fake players may work inside claimed plots but never open gated blocks. Operators toggle `/entrelumen admin protection bypass` for themselves (not persisted).

**Plots.** The first member of a team who stands in the ready city claims one plot for the team's campaign (party id, or the player for a solo campaign), provided the team passes the gate. One plot per team; it keeps it while the campaign exists (archived parties included, so a recovered party finds its house). The city has **4** symmetric plots (both the provisional placeholder and the controller's `city.nbt`); when all are taken, newcomers are told so and may still visit. An operator frees one with `/entrelumen admin solsticio plot release <index>` (blocks stay). A player who later joins a party keeps their solo plot under their personal campaign.

Residual risks, not covered: dispensers or modded machines inside a plot that place fluids, fire or blocks directly on a neighbouring guarded position (the placed block is then inert: it cannot spread or burn further); projectiles breaking fragile guarded blocks (pots, dripstone, chorus); blocks changed by other mods without a NeoForge event.

## Light Key (`entrelumen:light_key`) and its return form (`entrelumen:light_key_broken`)

- **Forging.** The activated Ark forges one key per team: the crouched empty-hand activation that records `last_horizon` hands a key to the activating player (inventory, else at their feet). Teams that activated the Ark before this existed receive it on their next controller interaction. `SolsticioData.forgedKeys` keeps it to one per campaign. Missing members craft one (`recipe/light_key.json`: amethyst shard, two gold ingots, spectral lens, horizon chart — provisional balance); a crafted key still works only for teams that activated the Ark.
- **Use.** Hold use for 40 ticks (bow pose, a narrowing ring of light); releasing earlier cancels with no change. When the channel completes, the server decides again (`LightKeyRules`):

| Key | Situation | Result |
| --- | --- | --- |
| whole | team activated the Ark, outside Solsticio, city ready | travel to `arrival`, then the key becomes a broken key bound to the traveller (UUID and name, component `entrelumen:light_key_owner`) |
| whole | team has not activated the Ark | nothing, "the key stays dark" |
| whole | inside Solsticio | nothing |
| any | city still being placed | placement starts, nothing consumed, "try again" |
| broken | holder is the owner | Solsticio ↔ home, both ways, forever, no team condition |
| broken | anyone else | nothing |

- **Safety.** Travel first, break after: a refused or cancelled crossing never consumes the key, and exactly one stack is swapped in the same server tick. Inventories are untouched by teleports. Mounts are dismounted and passengers ejected before leaving; neither travels. The destination chunk is loaded and ticketed. Home is the last position the player left the Overworld from (recorded on any departure), else their Overworld bed, else the world spawn; each is checked for a safe standing spot.

## Definitive portal (`entrelumen:solsticio_portal`, skeleton)

- Placed dormant on `town_hall_portal`. A team that passes the gate right-clicks it with each placeholder relic (`entrelumen:heliodor_relic_1..3`, consumed; get them with `/give` or `/entrelumen admin solsticio relics`). With the three set, a right-click by a player carrying **their own** broken Light Key opens it. The key is only shown, not consumed — the story promises it as a way home "whenever you want"; consuming it is a one-line change if Elias prefers.
- Open, it is for everyone: walking in on the Solsticio side sends anyone home (same rules as the return key).
- **Waystones.** The pinned Waystones 21.1.41 (`waystones-neoforge-1.21.1-21.1.41.jar`, SHA-256 `3d38c91a…a590`, read from the QA server's `mods/` folder with `javap`) exposes `WaystonesAPI.placeWaystone(Level, BlockPos, WaystoneStyle)`, `MutableWaystone.setName/setVisibility` and `WaystoneVisibility.GLOBAL`. When the portal opens and Waystones is loaded, `WaystonesBridge` places a named, global end-stone waystone on `town_hall_waystone`, reached by reflection (no compile dependency on that all-rights-reserved JAR). Every waystone in the world can then warp to Solsticio.
- **Fallback.** Without Waystones (or without the marker, or if the API call fails) the portal gets an Overworld twin beside the world spawn; walking into it leads to `arrival`.
- **No bouncing.** Any arrival (key, portal, waystone, command) marks the player; touching a portal within 3 s of arriving makes it rest for that player, and it keeps resting while they stay in it, so a waystone warp that lands on the portal does not throw them back. Stepping out for 5 s re-arms it.

## Assets

Stable IDs: `light_key`, `light_key_broken`, `solsticio_portal`, `heliodor_relic_1/2/3`. All models are `PROVISIONAL` and reference vanilla textures only (no copied files): keys `item/trial_key` and `item/ominous_trial_key`; relics `item/heart_of_the_sea`, `item/echo_shard`, `item/prismarine_crystals`; the portal is a symmetric quartz-and-copper pedestal (dormant) or a quartz sill with an unshaded `shroomlight` column (open). The portal has no item form (it is unbreakable and only the city places it). EN and ES translations are complete.

## Tests

- JUnit: `ProtectionRulesTest` (regions, holes, gates, overlap, fluids), `CityLayoutTest` (grid and centring, world-aligned cuts and cubes, markers, plots, border, template partition with markers and entities, `SolsticioData` round trip), `LightKeyRulesTest`.
- GameTests (`RuntimeGameTestsSolsticio`, isolated server, run against the controller's `city.nbt`): dimension and city placed exactly once with markers, plots, portal anchor, border, biome and spawn refusal; weather ignored; break/place refused and the plot free for its team only (creative included, buckets refused); explosion, piston and incoming water stopped while the plot stays usable; chest locked until the act (spectators too), lever free, frames locked then their item takeable; key crossing, breaking and binding, locked team refused, mount left behind; return key for its owner only, both ways; portal refused to a locked team, opened with three relics and the owner's key, anti-bounce on both sides.
- Vanilla's GameTest server bakes its world with an empty dimension registry, so datapack dimensions never load there. The test-only fixture mod (`src/gameTestFixture`, never shipped) carries `GameTestDatapackDimensionsMixin`, which passes the datapack dimensions exactly as a dedicated server does.
- The existing activation GameTest now expects exactly one forged Light Key and none on replay.

## Pending

- In-game visual review on a client: sky, fog, motes, portal particles and the controller's city under eternal noon (for example whether its daylight detectors toggle any copper bulb on the first tick).
- Final art for the five items and the portal (controller).
- Missions that award the relics, named villagers on their marker points, the trading hall's trades and moved-villager discounts.
- Act renumbering (Ark → V, Solsticio → VI): only `Solsticio.GATE` changes.
- Ruin protection wiring once `feature/compass` merges (provider snippet above).
- Full-pack check with Waystones loaded (the reflection path is not exercised by the isolated tests).
