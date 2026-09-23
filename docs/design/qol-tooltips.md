# On-demand inventory and effect information

This additive batch pins three distinct client features and one required library for Minecraft 1.21.1 / NeoForge 21.1.249. The previous 147 catalog entries are unchanged. The lock now has 151 client JARs and the same 116 server JARs. No pack configuration or other pack's content was copied.

| Addition | Official release and lock reference | Role |
| --- | --- | --- |
| Shulker Box Tooltip 5.1.9+1.21.1 | [Modrinth IuqNIoAi](https://modrinth.com/mod/shulkerboxtooltip/version/IuqNIoAi), [version metadata](https://api.modrinth.com/v2/version/IuqNIoAi) | On-demand compact and full shulker/container preview. Client installation is sufficient; the upstream server component is optional. |
| Just Enough Effect Descriptions 1.21-2.3.4 | [Modrinth Rm5m1ASj](https://modrinth.com/mod/just-enough-effect-descriptions-jeed/version/Rm5m1ASj), [version metadata](https://api.modrinth.com/v2/version/Rm5m1ASj) | Effect explanations in the existing EMI viewer. Its JEI plugin is suppressed when EMI is loaded. |
| Equipment Compare 1.3.13 | [Modrinth efoMHHTh](https://modrinth.com/mod/equipment-compare/version/efoMHHTh), [version metadata](https://api.modrinth.com/v2/version/efoMHHTh) | Side-by-side tooltip for worn equipment on demand. |
| Iceberg 1.3.2 | [Modrinth IMssx9du](https://modrinth.com/mod/iceberg/version/IMssx9du), [version metadata](https://api.modrinth.com/v2/version/IMssx9du) | Library required by Equipment Compare. It is client-only in this selection because no server mod requires it. |

Official CDN bytes were checked against each Modrinth SHA-1 and SHA-512 before recording local SHA-256 in `catalog/curated.json`. Each JAR declares the expected mod ID, has no bundled JarJar provider, and has a compatible Minecraft/NeoForge range. Equipment Compare requires `iceberg [1.3.0,)`; the pinned Iceberg is 1.3.2. Its optional Curios integration requires `curios [9.3.0,)`; the existing Curios is 9.5.1. Iceberg requires NeoForge `[21.1.54,)`; the pinned loader is 21.1.249. JEED declares Minecraft `[1.21.1]`, and Shulker Box Tooltip declares `[1.21.1,)`. These metadata checks establish a dependency closure, not a successful client launch.

The Modrinth project licenses are MIT for Shulker Box Tooltip, CC-BY-NC-ND-4.0 for Equipment Compare and Iceberg, and All Rights Reserved for JEED. JEED's JAR says `GLP3` for code and All Rights Reserved for assets, which differs from its project page and is not a standard SPDX identifier. This lock retains both claims as evidence; it grants no redistribution permission. A public pack export must use the official app/provider references and resolve license handling rather than embedding these JARs in source control.

## Feature boundaries

- [Sophisticated Backpacks](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/1.21.x/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/backpack/BackpackItem.java) has its own Shift-hover contents component. Shulker Box Tooltip 5.1.9 adds generic preview for stacks using the vanilla `minecraft:container` component. The observed backpack code provides a custom tooltip component and does not reference that vanilla component; verify the pinned combination in-game, especially a filled and an empty backpack. Keep the Sophisticated preview if any overlap appears. The fallback `preview.genericContainerPreview=false` limits Shulker Box Tooltip to its explicitly supported containers; change it only with runtime evidence.
- [Enchantment Descriptions](https://www.curseforge.com/minecraft/mc-mods/enchantment-descriptions) owns enchantment explanations. JEED owns status effects. [Better Advanced Tooltips](https://www.curseforge.com/minecraft/mc-mods/better-advanced-tooltips) adds technical item details in F3+H mode; it does not replace either explanation or equipment comparison. No additional tooltip styling, shulker interaction, or durability overlay is selected.
- JEED's [EMI plugin](https://github.com/MehVahdJukaar/JustEnoughEffectDescriptions/blob/1.21/common/src/main/java/net/mehvahdjukaar/jeed/plugin/emi/EMIPlugin.java) registers effect pages. Its [JEI plugin](https://github.com/MehVahdJukaar/JustEnoughEffectDescriptions/blob/1.21/common/src/main/java/net/mehvahdjukaar/jeed/plugin/jei/JEIPlugin.java) skips registration when EMI is present. With the pack's existing EMI `effectLocation=TOP`, JEED omits its own effect tooltip while keeping effect pages. Test this in the integrated client.

## Native controls and defaults

These values come from the pinned classes/config builders, checked against Shulker's [general](https://github.com/MisterPeModder/ShulkerBoxTooltip/blob/1.21.1/common/src/main/java/com/misterpemodder/shulkerboxtooltip/impl/config/Configuration.java) and [client control](https://github.com/MisterPeModder/ShulkerBoxTooltip/blob/1.21.1/common/src/main/java/com/misterpemodder/shulkerboxtooltip/impl/config/ClientConfiguration.java) sources, the [Equipment Compare configuration source](https://github.com/AHilyard/EquipmentCompare/blob/1.21-multi/common/src/main/java/com/anthonyhilyard/equipmentcompare/config/EquipmentCompareConfig.java), and [JEED's NeoForge configuration source](https://github.com/MehVahdJukaar/JustEnoughEffectDescriptions/blob/1.21/neoforge/src/main/java/net/mehvahdjukaar/jeed/platform/JeedImpl.java). No always-on tooltip settings are shipped. The isolated QA profile temporarily enabled rendering without held modifiers; both settings were restored after its normal shutdown.

| Mod | Key or config field | Pinned default and meaning |
| --- | --- | --- |
| Shulker Box Tooltip | `controls.previewKey`; `controls.fullPreviewKey`; `controls.lockTooltipKey` | Left Shift compact; Left Alt plus Shift full; Left Control locks an open preview. |
| Shulker Box Tooltip | `preview.enable`; `preview.alwaysOn`; `preview.swapModes` | `true`; `false`; `false`: enabled but only displayed on request, compact first. |
| Shulker Box Tooltip | `preview.genericContainerPreview`; `preview.position`; `preview.defaultMaxRowSize` | `true`; `INSIDE`; `9`. Generic mode accepts the vanilla container component. |
| Shulker Box Tooltip | `tooltip.type`; `tooltip.showKeyHints`; `tooltip.lootTableInfoType` | `MOD`; `true`; `HIDE`. |
| Equipment Compare | `equipmentcompare.key.showTooltips`; `client.control_options.default_on` | Left Shift in inventory; `false`, so the comparison is hidden until held. |
| Equipment Compare | `client.control_options.strict`; `compare_accessories`; `blacklist` | `false`; `false`; empty. Curios comparisons remain opt-in. |
| Equipment Compare | `client.visual_options.max_comparisons`; `override_badge_text` | `3`; `false`, retaining the translatable badge. |
| JEED | `effect_color`; `effect_box`; `ingredients_list`; `ignore_derivative_potions` | `true`; `true`; `true`; `true`. |
| JEED | `sort_ingredients`; `render_slots`; `replace_vanilla_tooltips`; `hidden_effects` | `false`; `false` (REI only); `true`; `[""]`. The existing EMI top placement controls whether JEED draws another effect tooltip. |

## Runtime acceptance

Use a separate client with the locked dependency receipt; retain player options and the live instance. In both `en_us` and `es_es` GUIs:

1. Hover a filled shulker with no modifier, Shift, and Alt+Shift. Confirm compact and slot-preserving full views are legible, contents/counts match, and an empty shulker has no misleading contents. Check left/right screen edges, UI scale, and the key hint.
2. Hover filled and empty Sophisticated backpacks with Shift. Confirm exactly one native contents preview and no stale Shulker window; inspect a plain vanilla container item separately if available.
3. Hover diamond armor while wearing iron armor: plain hover stays compact and Shift shows the equipped item beside the candidate. Confirm no clipped width, sensible text wrapping, and no comparison over a shulker. Verify Curios/accessory comparison remains off by default; test an explicit opt-in only if desired.
4. With Haste active, open JEED's effect page through EMI and confirm a readable description/provider list without a duplicate JEI page or effect tooltip. Check a modded effect with no supplied description for honest fallback. Confirm Enchantment Descriptions and F3+H Better Advanced Tooltips remain usable.
5. Inspect English and Spanish labels/help text. The pinned Shulker JAR has `en_us` and `es_es`. Equipment Compare has `en_us` but no `es_es`; JEED has `en_us` and `es_ar` but no `es_es`. Check the pack's original translations when integrated and report any remaining English fallback. Test key behavior with EN and ES keyboard layouts.

No duplicate UI is accepted merely because both mods load. In particular, JEED's potion/effect page must work through EMI and Shulker's preview must not replace Sophisticated's own backpack view. Client launch, save/reopen, and visual evidence belong to the integration run.

## Recorded integration

The [bounded runtime record](../verification/qol-tooltips-runtime.json) contains native F2 captures at ES GUI 3 and EN GUI 2. Compact shulker contents, empty state, equipment comparison and JEED's Haste page rendered correctly. Search the effect name in EMI and select its virtual icon with the current top-position effect display. The pack supplies 46 independently authored JEED strings and two Equipment Compare strings per locale; third-party translation text was not copied.

Held modifier controls, full slot-preserving previews, tooltip locking and edge cases remain pending. Rendering QA used temporary always-on options because the available input API cannot hold a modifier; the screenshots do not prove those controls.

Two concrete defects were recorded. The existing Accessories/Curios combination emits two backpack slot labels; removing a framework or an item tag would change real functionality, so that integration remains open. The new JEED serializer omits EMI's `type=mob_effect` discriminator, causing a stored effect lookup to be discarded on resource reload. An original, optional client-only companion mixin now adds that field when absent. It does not alter dependency JARs or rewrite old user data. In the real client, a new Haste query survived a resource reload and a subsequent bread query that resaved both history entries. The malformed entry from the earlier QA run was discarded once by EMI at startup; later queries produced no new deserialization error. The companion also passed 50 unit tests and 30 dedicated GameTests without JEED/EMI installed.
