# Practical Ark services: implementation boundary

> **Replaced by [`ark-modules-v2.md`](ark-modules-v2.md) (25 September 2026).** The Ark's first version (the six module services) was removed: each module now gives a global effect to its team from its place in the Ark. This page is kept as the record of what existed; the companion no longer implements it.

Engineering is an implemented material repair workshop. Arcana is an implemented compound-book library. Both require a complete physical Ark, operate only on deliberate interaction, preserve free gifts and do not award campaign progress. Habitation now offers temporary personal expedition lodging with conservative restoration of the previous home. Its complete physical Ark is checked at registration; an existing reservation then needs only its own module and a safe exit. Bounded evidence and limitations are in `ark-workshop-magic-runtime.json`, `arcane-building-runtime.json` and `habitation-restart-runtime.json`.

Logistics now has a selected implementation contract: a persistent shared depot and exact personal expedition kits, prepared completely or left unchanged. See `ark-logistics-kits.md`; headless full-pack runtime and restart evidence passed on 2026-09-23, client acceptance is pending. Its native-overlap review found existing AE2 per-slot exports and Sophisticated refill/restock, so the contribution is shared departure preparation for different players, not a claim to have invented restocking. The earlier remote-parcel candidate is replaced.

Nature now restores a team-marked landscape site to its own biome. Exposed dirt regains the prevailing cover, bare grass gets its biome's plants, and the biome's native trees return (vanilla feature code, sandboxed, whole tree or none). Builds, containers, decorations and claims are left alone, and payment is exact in bone meal and saplings. See `ark-nature-restoration.md`. Isolated headless evidence passed on 2026-09-23. The FTB Chunks full-pack case is written but pending integration, and client review is pending. Generic reforestation was rejected because pinned Ars Nouveau already provides Forestation, Flowering and Conjure Island rituals.

Exploration now keeps a chart room. A filled map held at the module absorbs the explored detail of the player's other maps of the same area. They must be in the same dimension, at the same or a finer scale and of the same kind of view. Only blank pixels are filled; the result is order-independent and nothing is consumed or created. Zooming out at a cartography table therefore no longer throws detail away, and maps drawn separately can finally be combined. See `ark-exploration-charts.md`. Isolated headless evidence passed on 2026-09-23. Two full-pack cases, an FTB Chunks claim and Supplementaries slice maps, are written but pending integration, and client review is pending.

The expedition port candidate is rejected, not only deferred. Pinned Waystones, Tombstone, Mekanism and Ars Nouveau already move players, groups, pets and entities between places and dimensions (see below and the chart-room document). FTB Chunks party waypoints and map sharing, the compasses and the survey station already cover locators. The existing diagnostics, team journal and recoverable commissioning deposits are unchanged.

Astra's design review retained transport and seed proposals as exploratory options. Both remain unimplemented; the lodging service is described separately in `ark-habitation.md`. Prefer native mechanisms when they fulfill a distinctive player need. Each selected service still needs its own constrained implementation and runtime proof; six generic buffs or repeated journal buttons are not a substitute.

Native Nature overlap inspection (2026-09-23): pinned client JAR hashes matched the catalog. Representative entries were `botanypots` wheat crop, `mysticalagriculture:seed/crafting/iron`, `mysticalagriculture:seed/reprocessor/iron`, Farmer's Delight rich-soil growth and Productive Bees farmer goals. This was static inspection, not new runtime evidence. Potential full-output handling in Botany Pots remains an unconfirmed observation, not a selected Ark mechanic or an authorized upstream behavior change. Restoration overlap inspection (2026-09-23, pinned JARs read in place) covered the following:

- Ars Nouveau 5.13.1 lang and ritual classes. `ForestationRitual` places oak and birch, or spruce and podzol or jungle when augmented, plus bone meal in a disc. `FloweringRitual` spreads flowers and grass, and the Conjure Island rituals rewrite terrain and biome.
- Nature's Aura 41.9: the Ritual of the Forest, Powder of Steady Growth and degradation effects.
- EvilCraft 1.2.94: biome extracts.

The Ark therefore only repairs the existing biome and never creates a forest or changes a biome.

Native Exploration overlap inspection (2026-09-23, pinned Waystones and Immersive Aircraft JARs): Warp Plates already submit an aircraft entity for teleportation. `WarpPlateBlockEntity.onEntityCollision` skips passengers; `EntityTeleportBatch.teleportEntityAndAttached` collects passengers when the initiating entity is itself riding, which does not establish carriage of an aircraft's crew when the aircraft triggers the plate. Aircraft inventory and fuel have native NBT serializers, but their preservation during this route is inferred, not runtime-tested. Waystones loads its destination and checks two blocks of space, rather than an aircraft's complete landing volume; its per-entity cancelable teleport sequence provides no inspected group rollback. Owner: root. The candidate was first deferred and later rejected: the chart-room inspection below found no distinct crew-transport need. Any future transport proposal would still have to prove that passengers, cargo and fuel survive, or that a failure moves none of them. No transport implementation or compatibility patch was added.

Chart-room overlap inspection (2026-09-23, pinned JARs read in place, hashes matched the catalog):

- Waystones 21.1.41 lang: warp plates, the Portal Scroll with its Warp Portal, the Twinbound Feather, Return to Portal, pet and leash transport.
- Tombstone 9.5.5 lang: Tablets of Recall, Home and Assistance, whose ancient versions also move nearby creatures, and Lost Tablets.
- Mekanism 10.7.19 lang: the teleporter and portable teleporter, charged per entity.
- Ars Nouveau 5.13.1 lang: Warp Portals up to 21×21, and stable warp scrolls that open cross-dimension portals.
- FTB Chunks 2101.1.21 lang: waypoint sharing with a party, the server or a player; "Share Map with Allies"; team location visibility.
- Supplementaries 3.9.5 and Moonlight 3.5.2 classes: the Cartographer's Quill, explorer and slice maps, and custom map layers (`depth_lock`, `antique`, tint and `lightmap`).
- A language and class-name scan of all 197 pinned JARs, plus the 52 staged by the end of this change: no map merging or detail-preserving zoom-out.

Vanilla 1.21.1 `MapItemSavedData.scaled()` returns a blank map. This was static inspection, not runtime evidence of those mods.
