# Act IV: journeys and recovered testimonies

Four existing static recipes now feed explicit team deliveries. Each consumes one portable prototype, keeps installed machines intact and accepts gifts. `spectral_archive`, `horizon_survey`, `pollinator_treaty` and `sealed_memory` require `exchange_route`. The horizon also requires recorded Aether and Twilight Forest arrivals; the pollinator project requires a Bumblezone arrival; the seal requires `spectral_archive`. The preserved `atlas_voices` closure consumes three paper and one copper after all four projects. None awards a duplicate prototype.

The server records an actual destination on dimension change, login or respawn. Early journeys count in any act; an item, quest click or client-supplied destination cannot create a visit. Observations belong to the current campaign, persist in its existing completed-milestone ledger, and do not merge on team join. Archived campaigns and repeated observations remain unchanged. These observations are reserved IDs, not deliverable projects.

## Pinned acquisition audit

Source inspection was performed on 12 September 2026 against the installed JARs and the owned server configuration. It is not a survival or portal playtest.

| Prototype | Material route | Pinned source evidence |
|---|---|---|
| Spectral lens | One Occultism crystal, two Malum arcane spirits, one Create precision mechanism and one calibration frame. Four spirit-attuned gems form the crystal; each gem uses a diamond in spirit fire. Ignite dropped datura to start that fire. Malum's initial crude scythe uses three iron, two sticks and one refined soulstone, so this route does not require soul-stained steel first. | Occultism `data/occultism/recipe/crafting/spirit_attuned_crystal.json`, `recipe/spirit_fire/spirit_attuned_gem.json`, book language key `getting_started.spirit_fire.spirit_fire_screenshot.text`; Malum `data/malum/recipe/crude_scythe.json`, book `scythes.2`. |
| Horizon chart | Two ambrosium shards, two ironwood ingots, survey notes and a ration bundle. Ambrosium ore supplies the shards. Raw ironwood combines liveroot, raw iron and a gold nugget, then uses furnace processing. | Aether 1.5.10 `data/aether/loot_table/blocks/ambrosium_ore.json`; Twilight Forest 4.8.3345 `data/twilightforest/recipe/material/raw_ironwood.json`, `recipe/smelted_ironwood_ingot.json`, `loot_table/blocks/liveroot_block.json`. |
| Ecosystem capsule | Two pollen puffs, two wax and two living matrices. A full eight-layer pollen pile drops one to three puffs without Silk Touch; Productive Bees' basic centrifuge can use ordinary honeycomb for wax. Advanced resource-bee breeding is unnecessary. | Bumblezone 7.15.3 `data/the_bumblezone/loot_table/blocks/pile_of_pollen.json`; Productive Bees 13.13.5 `data/productivebees/recipe/centrifuge/honeycomb.json`. |
| Containment seal | Two dark gems, two soul-stained steel ingots and another spectral lens. One steel ingot uses iron, four refined soulstone, three Wicked, one Earthen and one Arcane spirit. Malum's witch data provides two Arcane and two Wicked, and zombie data one Earthen and one Wicked, before modifiers. Raw soulstone smelts into two refined soulstone. | EvilCraft 1.2.94 `data/evilcraft/loot_table/blocks/dark_ore.json`; Malum 1.8.2 `data/malum/recipe/spirit_infusion/soul_stained_steel_ingot.json`, `recipe/soulstone_from_raw_smelting.json`, `spirit_data/entity/{witch,zombie}.json`. Book `spirit_infusion.2` places ingredient holders within four blocks. |

The recipes are authored in `content/integration-design.json` and generated into the pack's static KubeJS integration script. Their expected phase guides acquisition rather than restricting item use or trading.

## Travel preparation

- Aether: glowstone frame activated with water. The inspected `aether-server.toml` enables creation and returns to the Overworld through a portal.
- Twilight Forest: a decorated 2×2 water pool, solid base and dirt border with flowers; one diamond activates it. Pinned `TFPortalBlock.MIN_PORTAL_SIZE` is four. The inspected `twilightforest-common.toml` enables creation and a usable return portal to the Overworld without a second diamond. Portal lightning can ignite nearby blocks.
- Bumblezone: an ender pearl against a hive or nest. Entry and exit are enabled in `the_bumblezone/dimension.toml`; `forceExitToOverworld=false` preserves the original-dimension return, with an Overworld fallback. Pinned `EntityTeleportationHookup.entityTick` exits below Y=-2 or above Y=255. The quests should teach the route and native guide without implying that damaging bees, honey or brood is harmless.

Exact observed dimension IDs are `aether:the_aether`, `twilightforest:twilight_forest` and `the_bumblezone:the_bumblezone`.

## Verification boundary

Embedded server tests cover delivery authority, gifts, prerequisites, a forged destination event, FTB mirroring, exact consumption, replay, team separation, act advancement and saved-data restoration. Their positive observation fixtures use synthetic ledger entries. Full-pack checks must separately exercise loaded recipes, FTB metadata and actual dimension transfers. Administrative QA travel is not evidence for survival acquisition, portal safety, pacing or performance. The 22 September full-pack run passed all four crafts and recorded three actual client dimension transfers. A second Aether visit left the saved campaign unchanged. The Twilight arrival detail was visually checked in EN/ES; full chapter/Atlas review and the six-act survival playthrough remain open. See [runtime evidence](../verification/act-four-runtime.json).
