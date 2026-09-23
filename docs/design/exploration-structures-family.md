# Exploration, structures and bosses on a worldgen budget

`catalog/families/exploration-structures.json` pins 15 selected JARs; three libraries resolve from the same reference instance: YUNG's API 5.1.8, Cristel Lib 3.1.7 (Towns and Towers) and Lionfish API 3.1 (Cataclysm). The lock grows from 230 client / 191 server to **248 client / 209 server** JARs; every previous entry is byte-identical. SHA-1 matched the read-only ATM10 8.1 instance metadata; licenses, ranges and sides were read from each JAR. No ATM10 configuration, script, quest, structure or asset was copied.

## Worldgen budget

The rule is one answer per structural role. YUNG's Better modules **replace** vanilla dungeons, mineshafts, strongholds, desert and jungle temples, witch huts, ocean monuments, Nether fortresses and the End island instead of adding a second set; YUNG's Extras adds only small decorative features. Four additive sets remain, each with a distinct role: Towns and Towers (village and outpost variants), Explorify (small vanilla-style points of interest), Hellish Trials (Nether trial chambers) and L_Ender's Cataclysm (boss arenas). Deeper and Darker extends ancient cities and adds the Otherside below them. No biome overhaul and no second small-structure pack were added. New chunks alone receive these structures; existing worlds are not regenerated.

| Mod · file (CF project / file) | Role | Introduction | Later use | Relation to another system |
|---|---|---|---|---|
| YUNG's Better Dungeons · `YungsBetterDungeons-1.21.1-NeoForge-5.1.4.jar` (1015112 / 5954804) | Replaces vanilla dungeons | I: first combat rooms | III: catacomb variants | Lootr keeps chests per player |
| YUNG's Better Mineshafts · `YungsBetterMineshafts-1.21.1-NeoForge-5.1.1.jar` (1015096 / 5812193) | Replaces mineshafts | I: early ore expeditions | II: before the JAMD mining dimension | Complements FTB Ultimine and the mining gadgets |
| YUNG's Better Strongholds · `YungsBetterStrongholds-1.21.1-NeoForge-5.1.3.jar` (1015105 / 6272264) | Replaces the stronghold | III: End route | IV: portal expedition | End access for Draconic, JDT tier-3 goo and Laser Bridges |
| YUNG's Better Desert Temples · `YungsBetterDesertTemples-1.21.1-NeoForge-4.1.5.jar` (1015114 / 6276955) | Replaces desert pyramids | I | III | Archaeology viewer already selected |
| YUNG's Better Jungle Temples · `YungsBetterJungleTemples-1.21.1-NeoForge-3.1.2.jar` (1015123 / 5924482) | Replaces jungle temples | I | III | Native loot through Lootr |
| YUNG's Better Witch Huts · `YungsBetterWitchHuts-1.21.1-NeoForge-4.1.1.jar` (1015144 / 5812532) | Replaces witch huts | I | II | Witch drops feed Malum and Occultism |
| YUNG's Better Ocean Monuments · `YungsBetterOceanMonuments-1.21.1-NeoForge-4.1.2.jar` (1015115 / 5924487) | Replaces ocean monuments | III | IV | Coastal outposts and Aquaculture |
| YUNG's Better Nether Fortresses · `YungsBetterNetherFortresses-1.21.1-NeoForge-3.1.5.jar` (1015118 / 6606621) | Replaces Nether fortresses | II | III | Blaze rods for Powah, JDT tier-2 goo and brewing |
| YUNG's Better End Island · `YungsBetterEndIsland-1.21.1-NeoForge-3.1.2.jar` (1015127 / 6300968) | Replaces the End island and dragon fight | IV | V | Dragon heart/egg route for Draconic |
| YUNG's Extras · `YungsExtras-1.21.1-NeoForge-5.1.1.jar` (1015146 / 5812546) | Small decorative features | I | III | Low-cost points of interest |
| Towns and Towers · `t_and_t-fabric-neoforge-1.13.11.jar` (626761 / 8657120) | Village/outpost variants and ships | I | III | Villages for Easy Villagers and trading |
| Explorify · `Explorify v1.6.5.mod.jar` (698309 / 8082824) | Small vanilla-style structures | I | III | Early exploration loot |
| Deeper and Darker · `deeperdarker-neoforge-1.21.1-1.4.1.jar` (659011 / 8201775) | Ancient-city expansion and the Otherside | IV | V | Echo/sculk for JDT tier-4 goo; warden-tier armour |
| L_Ender's Cataclysm · `L_Ender's Cataclysm 1.21.1-3.33.jar` (551586 / 8706841) | Boss structures and boss gear | IV | VI | Optional late bosses; campaign never requires them |
| Hellish Trials · `HellishTrials-neoforge-1.0.5.jar` (1414295 / 7445277) | Nether trial chambers | II | III | Nether counterpart to vanilla trial chambers |

## Rejected

- **Structory 1.3.7 and Structory: Towers**: a second small-structure pack beside Explorify; both files also have `allowModDistribution=false`.
- **The Undergarden 0.9.6** (45 MB, 238 blockstates, 146 structure files, own ore set): a second underground dimension overlapping Deeper and Darker's Otherside and the JAMD mining dimension.
- **When Dungeons Arise** (917 structure files, 11.8 MB), **Mo' Structures**, **Moog's Voyager/Nether/End/Soaring structures**, **Formations**: additive structure packs redundant with YUNG's replacements plus Explorify and Towns and Towers.
- **Underground Villages**, **Illager Warship**: narrow additions on top of Towns and Towers.
- **Eternal Starlight** (53 MB dimension), **Ice and Fire 2.0 beta** (21 MB, beta, dragon griefing), **Nullscape** (End biome overhaul) and **Repeatable Trial Vaults** (repeatable vault loot farm).

## Keybindings

Bytecode defaults collided with world controls: Deeper and Darker's soul-elytra boost on B (backpack) and transmitter on V (FTB Ultimine); Cataclysm's armour abilities on V, C, Y and V. The preset ships Alt+B and Alt+N for Deeper and Darker and Alt+J/K/L/I for Cataclysm's ability, helmet, chestplate and boots actions; all are modifier overlaps that the audit reports as review-context, not world collisions.

## Weight and staging

The family adds 88 MB of compressed JARs, 73 MB of it L_Ender's Cataclysm (boss models, animations and sound). No recipe is staged: boss gear already comes from optional late bosses, and structure loot goes through Lootr per player. Structure placement, spacing and chunk-generation cost must be measured in the pending new-world benchmark before accepting the budget; existing worlds only receive these structures in newly generated chunks.

Licences to carry into publication credits: Towns and Towers and Cristel Lib use CC-BY-NC-ND 4.0, Explorify and Hellish Trials are All Rights Reserved, Cataclysm's assets are all rights reserved with LGPL-3.0 code; all are referenced only as official CurseForge files with `allowModDistribution=true`.

## Verification boundary

Static: `python tools/curate_pack.py --check`, `python tools/generate_family_balance.py --check`, Default Options generation and a simulated keybinding audit. Dedicated-server evidence is in [the runtime receipt](../verification/exploration-structures-runtime.json): loading, registration, save and shutdown only. On the first load of the existing QA world, YUNG's Better End Island logs one ERROR because its `bei_ExtraDragonFight` key is not yet in the world's level data; the key is written on save and a second start is clean of it. No new chunks were explored, no structure was located or entered, no boss was fought and no client was launched.
