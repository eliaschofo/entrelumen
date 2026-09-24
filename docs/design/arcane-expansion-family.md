# Arcane expansion: combat spells, alchemy, relics and Ars depth

`catalog/families/arcane-expansion.json` pins nine selected JARs; four libraries resolve from the same reference instance: Iron's Lib 2.1.0 and Player Animation Lib 2.0.4 (Iron's Spells), Atlas API 1.2.0 (Iron's Jewelry) and Valhelsia Core 1.1.5 (Forbidden and Arcanus). The lock grows from 217 client / 178 server to **230 client / 191 server** JARs and every previous entry is byte-identical. SHA-1 matched the read-only ATM10 8.1 instance metadata before pinning; licenses, ranges and sides come from each JAR. No ATM10 configuration, script, quest or asset was used.

| Mod · file (CF project / file) | Role | Introduction | Later use | Relation to another system |
|---|---|---|---|---|
| Iron's Spells 'n Spellbooks · `irons_spellbooks-1.21.1-3.16.3.jar` (855414 / 8680204) | Combat magic with schools, inks, scrolls and bosses | II: first spellbook and inscription table | IV-VI: epic/legendary inks, school armour, boss drops | Combat counterpart to Ars utility magic; uses Curios and the existing expedition routes |
| Iron's Gems 'n Jewelry · `irons_jewelry-1.21.1-2.0.2.jar` (1101111 / 8365016) | Crafted rings and amulets | II: jeweller's bench | IV-V: gem bonuses | Curios slots beside Artifacts; bonuses support Iron's schools |
| Forbidden and Arcanus · `forbidden_arcanus-2.6.1.jar` (309858 / 6875895) | Dark magic, clibano furnace, Hephaestus forge | II: arcane crystal, darkstone, clibano | IV-V: forge rituals, modifiers, Draco/Tyr armour | Eternal stella now needs a containment seal (IV); clibano residue smelting complements ore processing |
| Theurgy · `theurgy-1.21.1-neoforge-1.76.0.jar` (430636 / 8499344) | Spagyric alchemy and ore processing | II: calcination and liquefaction | IV-V: incubation, digestion, reformation | Magical alternative to Mekanism enrichment; reformation emitter staged at IV |
| Not Enough Glyphs · `not_enough_glyphs-1.21.1-4.6.1.jar` (1023517 / 8305680) | More Ars glyphs | II | IV | Deepens the existing Ars workshop |
| StarbuncleMania · `starbunclemania-1.21.1-1.5.7.jar` (746215 / 8243791) | Fluid/source starbuncles | II | III-IV | Ars familiar logistics beside Pipez |
| Reliquary · `reliquary-1.21.1-2.0.80.1570.jar` (241319 / 8661878) | Mob-drop relics, pedestals, handgun | II: first relics from mob drops | IV-V: Rending Gale flight, pedestal automation | Flight staged with a horizon chart; duplication routes removed |
| Artifacts · `artifacts-neoforge-13.2.3.jar` (312353 / 8683747) | Found wearable artifacts | I: campsites and chests | V: mimic hunts | Exploration loot through Lootr containers |
| Ars Polymorphia · `ars_polymorphia-1.0.3.jar` (1197614 / 6219409) | QoL compatibility | II | All acts | Lets the existing Polymorph choose conflicting Ars apparatus/imbuement outputs |

## Staged acquisition and removed duplication

`tools/generate_family_balance.py --family arcane` edits native recipes in `pack/kubejs/server_scripts/entrelumen_arcane_balance.js` and writes one datapack override, with the same reversible-edit rules as the [industrial family](industrial-expansion-family.md):

| Native definition | Change | Act | Reason |
|---|---|---|---|
| `reliquary:rending_gale` | horizon chart replaces one gold ingot | IV | Rending Gale grants flight; flight tools share the IV exploration stage with elite jetpacks and the JDT flight upgrade |
| `theurgy:crafting/shaped/sulfuric_flux_emitter` | containment seal fills an empty slot | IV | Reformation converts sulfurs within a rarity tier, a controlled transmutation route |
| Forbidden and Arcanus ritual `eternal_stella` | one containment seal added as a third pedestal input (five of eight pedestals used) | IV | The eternal modifier makes tools unbreakable |
| `reliquary:alkahestry/crafting/*` (22 IDs) and `reliquary:alkahestry_tome` | removed | — | Redstone-charged duplication of iron, gold, diamonds, emeralds and nether stars is an EMC-like route the pack excludes. The silver, steel and tin variants use empty `forge:` tags and never load; the script records them as already absent instead of aborting |
| `reliquary:uncrafting/spawn_egg` | removed | — | Spawn eggs from charm fragments would retype vanilla spawners and bypass the staged RFTools/IF/HNN mob routes |

Deliberately native: Iron's Spells inks, scroll forge and alchemist cauldron (their rarity chain and spell-focus drops are the mod's own progression, and its brew recipes have a single input with no repeated element), Iron's Jewelry, Artifacts (loot only), Not Enough Glyphs and StarbuncleMania (Ars glyph costs apply), and Reliquary pedestals and mob charms.

## Apotheosis decision

**Reverted on 23 September 2026 by Elias: the suite is in.** See the [Apotheosis family](apotheosis-family.md) for the pins, World Tiers tied to the campaign, the staged shelf ladder, the Atlas Library and the spawner augments. The original reasoning is kept below as history; each concern now has an explicit answer there.

Apotheosis 8.7.0 with Apothic Enchanting 1.6.1, Apothic Attributes 2.10.1 and Apothic Spawners 1.4.0 stayed **out** of this batch (12.5 MB together):

- Apothic Enchanting rewrites the enchanting table around Eterna/Quanta/Arcana, raises maximum levels and adds new shelves. Every enchant-based route already selected (Create: Enchantment Industry, Ars enchanting apparatus, EvilCraft, Tombstone) would need a new balance pass.
- Its Library of Alexandria pools enchantment points and extracts any level on demand from early-game blocks. The companion's [Ark arcane library](arcane-library.md) deliberately only separates a compound book into its exact vanilla stored enchantments at a finished Ark. Functionally nothing would crash — the library reads `DataComponents.STORED_ENCHANTMENTS` on vanilla enchanted books, which Apotheosis keeps — but the Ark service would become redundant long before the endgame.
- The Adventure module adds affixed loot, gems, rarities and invader bosses: a combat overhaul at odds with the moderate-combat target, while Iron's Spells and Cataclysm already provide optional combat depth.
- Apothic Spawners rewrites vanilla spawner mechanics with modifiers, bypassing the staged RFTools/IF/HNN mob routes.

That redesign happened with Elias's approval. The Ark's book separation became redundant with the Apothic and Atlas libraries, so the arcane module now [restores an item's forging history](arcane-library.md) instead.

## Other rejections

- **Ars Elemancy**: still more elemental armour on the Ars Elemental specialization, as decided in the [magic family](magic-automation-family.md).
- **Relics 0.12.8 with OctoLib and Reliquified Artifacts**: a third trinket-progression system (leveling relics) beside Artifacts and Reliquary, 5 MB plus a library, competing for the same Curios slots.
- **Theurgy KubeJS**: no pack script needs it.
- **Ars Unification**: unchanged earlier decision (extra transmutations without a reviewed economy).

## Keybindings

Iron's Spells registers the spell wheel on R (Tool Belt) and casting on V (FTB Ultimine), plus Left Alt as its spell-bar modifier. The preset moves the wheel to Shift+G and casting to Alt+Q; the Alt modifier keeps its native binding because it only acts while scrolling the spell bar. Theurgy, Reliquary and Artifacts register unbound keys; the others none.

## Upstream data corrections

- Create: Enchantment Industry's experience-fluid data map names `reliquary:xp_juice_still`, but Reliquary registers `reliquary:xp_still` (its own `c:experience` tag lists that ID). The override renames only that key, keeping its `mod_loaded` condition and value, and sets `replace: true` because data-map files merge across data packs; the merged map then contains Reliquary's real fluid, so Reliquary experience counts as liquid XP. NeoForge 21.1.249's `DataMapLoader` resolves each file's keys while merging, before a later file can replace them, so Create: Enchantment Industry's own file still logs one ERROR line for the misspelled ID. It is a harmless upstream typo (the entry is skipped) that cannot be silenced without patching that JAR.
- Forbidden and Arcanus tags its single `stella_arcanum` ore both as a stone and a deepslate `c:ores_in_ground` member, which Almost Unified rejects with two ERROR lines. The arcane script removes it from the deepslate tags (block and item) through a KubeJS tag event; its worldgen and drops are unchanged.
- Iron's Jewelry ships `irons_jewelry:generate_jewelry_test_materials`, a developer test loot table whose material keys are tags that the loot codec rejects. The override adds a `neoforge:false` condition; no real loot table references it.
- With Artifacts registering the `feet` Curios slot, the industrial override of Industrial Foregoing's slot list now removes only `example`.

## Worldgen and weight

Iron's Spells adds its own structure set (245 structure files, two biome modifiers) and Forbidden and Arcanus adds ores and trees (seven biome modifiers); Artifacts adds campsites. These are additive overworld features that must be measured with the other worldgen in the pending benchmark. Iron's Spells is 13.6 MB and Theurgy adds 1,777 processing recipes to the recipe index.

## Verification boundary

Static: `python tools/curate_pack.py --check`, `python tools/generate_family_balance.py --check` (including the three data overrides), `python tools/test_family_balance.py`, Default Options generation and a simulated keybinding audit. Dedicated-server evidence is in [the runtime receipt](../verification/arcane-expansion-runtime.json). The Hephaestus forge ritual and the Theurgy reformation were not performed in the world, and no client was launched: spell casting, curios rendering, animations and client memory remain unmeasured.
