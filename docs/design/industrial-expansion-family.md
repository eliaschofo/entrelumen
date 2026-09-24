# Industrial expansion: generators, laser tools, portable flight and a Draconic endgame

`catalog/families/industrial-expansion.json` pins 15 selected JARs; four required libraries resolve from the same reference instance. The lock grows from 178 client / 143 server to **197 client / 162 server** JARs and every one of the 178 previous entries is byte-identical. The 19 additions weigh 29,179,856 compressed bytes. All files are official CurseForge files already installed in the read-only ATM10 8.1 reference instance (`E:/curseforge/Instances/All the Mods 10 - ATM10`, reached through the `G:/curseforge` junction). Their SHA-1 matched the instance's CurseForge metadata before pinning; licenses, dependency ranges and sides were read from each JAR. No ATM10 script, quest, configuration, asset or story was used. Pack loader stays NeoForge 21.1.249 (see [NeoForge decision](#neoforge)).

Acts below are the stage at which normal crafting first becomes possible. They never check teams, campaign state or item origin: gifted machines and tools keep working.

| Mod · file (CF project / file) | Role | Introduction | Later use | Relation to another system |
|---|---|---|---|---|
| Iron Furnaces · `ironfurnaces-neoforge-1.21.1-4.3.2.jar` (237664 / 7034259) | Tiered, augmentable furnaces | I: copper/iron furnace for the first smelting line | III: speed/fuel augments keep a compact smelting bank | Complements FastFurnace (tick optimisation) and IE/Mekanism ore processing without a new ore |
| Utilitarian · `utilitarian-1.21.1-0.19.2.jar` (929559 / 7973750) | Workshop utilities | I: tiny coal, trowel, fluid hopper, redstone clock | III: TPS meter and well-behaved dropper in automation | Glue for Create/redstone builds; trader carpets and the outpost claim block serve settlements |
| Create Crafts & Additions · `createaddition-1.7.0.jar` (439890 / 8664915) | Rotation ↔ FE bridge | II: alternator turns the Create drivetrain into FE | III-IV: electric motors, rolling mill, wires | Feeds IE, Mekanism, Powah and RFTools from existing Create power |
| Create: Enchantment Industry · `create-enchantment-industry-2.5.3b.jar` (688768 / 8762719) | Liquid XP and automated enchanting | III: mechanical grindstone and XP handling | IV-V: blaze enchanter/forger lines | Uses Create fluids; parallel to Ars enchanting apparatus and EvilCraft; printing costs liquid XP |
| Industrial Foregoing · `industrialforegoing-1.21-3.6.39.jar` (266515 / 8370717) | Plastic, frames, agriculture and resource machines | II: latex, plastic and pity/simple frames | IV-VI: laser drills, mob machines, infinity tools | Staged with Act III/IV components; resource output joins AE2 and Mekanism processing |
| Powah! · `Powah-6.2.10.jar` (633483 / 8011715) | Energizing and tiered generators | II: starter/basic generators and energizing orb | IV-V: niotic, spirited and nitro reactors, cells, player transmitters | Tier capacitors consume Twilight ironwood, Nature's Aura sky ingot and Mekanism atomic alloy |
| Mekanism Tools · `MekanismTools-1.21.1-10.7.19.85.jar` (268567 / 7904062) | Personal equipment | III: osmium, bronze and steel tools | IV: refined obsidian/glowstone armour | Pure consumer of the existing Mekanism material chain |
| Flux Networks · `FluxNetworks-1.21.1-8.0.0.jar` (248020 / 6089446) | Wireless FE with priorities and limits | III: plugs and points across bases | IV-V: storages, cross-dimension supply and inventory charging | Same stage as RFTools dimensional cells, which stay the cheaper local option |
| Iron Jetpacks · `IronJetpacks-1.21.1-8.0.11.jar` (284497 / 7105279) | Tiered FE jetpacks | III: first jetpack after a power regulator | IV-V: elite and ultimate coils | Parallel to III aircraft; later tiers need Aether and Mekanism V materials |
| Mining Gadgets · `mininggadgets-1.18.7.jar` (351748 / 8414578) | Laser area mining | III: all three gadgets need a power regulator | IV-V: native late size/range/fortune upgrades | Charged from Powah/Mekanism; complements FTB Ultimine and the JAMD quarry |
| Just Dire Things · `justdirethings-1.5.7.jar` (1002348 / 7463040) | Goo-transformed materials, tools and small machines | II: primogel goo, ferricore, clickers/placers | III-V: portal guns, flight, time wand, paradox machine | Goo tiers already need Nether, End and Deep Dark items; staged gates add III-V components |
| Compact Machines · `compactmachines-neoforge-7.0.81.jar` (224218 / 7621553) | Pocket workshop rooms | III: shrinking device after a handling core | IV-VI: compact production cells | An optional compaction tool, not an expedition dimension; rooms load with their machine chunk |
| Hostile Neural Networks · `HostileNeuralNetworks-1.21.1-6.5.1.jar` (552574 / 8525006) | Entity-free mob drop simulation | IV: simulation chamber with an ecosystem capsule | V-VI: trained models for Ark supply | Same stage as the RFTools spawner and IF duplicator; replaces entity farms with a block |
| Draconic Evolution · `Draconic-Evolution-1.21.1-3.1.4.632.jar` (223565 / 7584459) | Endgame fusion crafting, energy core, reactor | III: draconium, crafting core, dislocator | V-VI: wyvern, awakened and chaotic tiers | Wyvern and awakened cores consume Act V Ark bus and renewal engine; chaotic keeps the Chaos Guardian |
| Torchmaster · `torchmaster-neoforge-1.21.1-21.1.9.jar` (254268 / 7197218) | QoL: spawn control | III: the companion's Altar of Peace replaces the mega torch, whose recipe is removed ([ark-altars.md](ark-altars.md#altar-of-peace)) | All acts: dread lamp for deliberate spawn areas | Settlement safety beside FTB Chunks claims; no mob farm on its own |

Required libraries resolved automatically: Cloth Config 15.0.140 (348521 / 5729127, LGPLv3, required by Powah), Brandon's Core 3.2.1.309 (231382 / 7130990) and CodeChicken Lib 4.6.1.529 (242818 / 8491811, LGPL-2.1) for Draconic Evolution, and Create: Dragons Plus 1.11.7b (1216624 / 8633162, LGPL-3.0-or-later) for Create: Enchantment Industry. Titanium, Placebo, Cucumber, GuideMe, Create and Mekanism were already pinned. Draconic Evolution and Brandon's Core use the CoFH "Don't Be a Jerk" license and Utilitarian is All Rights Reserved; all 19 have `allowModDistribution=true` in the instance metadata and are referenced only as CurseForge dependencies. Iron Jetpacks and Utilitarian declare Minecraft `[1.21,1.21.1)`, which FancyModLoader 4.0.44 accepts on 1.21.1 as documented in [catalog/README.md](../../catalog/README.md#loader-version-evidence); Create Crafts & Additions requires NeoForge 21.1.248 or newer.

## Staged acquisition

`tools/generate_family_balance.py --family industrial` reads the pinned JARs, indexes 28,479 native recipes across the whole lock, and rewrites 27 native IDs in `pack/kubejs/server_scripts/entrelumen_industrial_balance.js`. Each edit fills an empty shaped slot, replaces one repeated shaped ingredient, or replaces one repeated element of a machine recipe list; a reverse edit must reproduce the complete native JSON (serializer, result/count/components, conditions, remaining ingredients). The generator also rejects any other native recipe in the lock that produces the same output (config-wipe recipes for Flux storage items are the only whitelisted self-recipes) and any component whose own recipe graph needs an item from this family.

| Native recipe ID | Added material (replaces) | Act | Power jump it stages |
|---|---|---|---|
| `ironjetpacks:strap` | power regulator (empty slot) | III | Every jetpack chain starts from the lowest tier's strap |
| `ironjetpacks:elite_coil` | Aether zanite gemstone (empty slot) | IV | Diamond and platinum cells/thrusters |
| `ironjetpacks:ultimate_coil` | Mekanism atomic alloy (empty slot) | V | Emerald cells/thrusters |
| `mininggadgets:mininggadget_simple`, `mininggadget`, `mininggadget_fancy` | power regulator (one iron ingot) | III | Laser area mining |
| `powah:crafting/capacitor_niotic` | Twilight ironwood ingot (one dielectric paste) | IV | Every niotic generator, reactor, cell and transmitter |
| `powah:crafting/capacitor_spirited` | Nature's Aura sky ingot (one paste) | V | Spirited tier |
| `powah:crafting/capacitor_nitro` | Mekanism atomic alloy (one paste) | V | Nitro tier, which also keeps its nether-star crystal |
| `industrialforegoing:mob_duplicator` | ecosystem capsule (one emerald) | IV | Spawner-class duplication |
| `industrialforegoing:ore_laser_base` | spectral lens (one iron ore) | IV | Ores from power without world mining |
| `industrialforegoing:fluid_laser_base` | power regulator (one bucket) | III | Fluids from power |
| `industrialforegoing:dissolution_chamber/infinity_drill` | power regulator (one diamond block) | III | Large-area powered mining |
| `industrialforegoing:dissolution_chamber/infinity_nuke` | containment seal (one TNT) | IV | Area destruction |
| `fluxnetworks:flux_plug`, `flux_point` | power regulator (one flux core) | III | Wireless cross-dimension FE |
| `fluxnetworks:flux_controller` | routing matrix (empty slot) | III | Network hub and inventory charging |
| `justdirethings:portalgun`, `portalgun_v2` | routing matrix (one blazegold ingot) | III | Portal teleportation |
| `justdirethings:upgrade_flight` | horizon chart (one phantom membrane) | IV | Flight upgrade for JDT armour |
| `justdirethings:time_wand` | renewal engine (one blazegold ingot) | V | Block tick acceleration |
| `justdirethings:paradoxmachine` | Ark bus (one eclipse alloy ingot) | V | Area snapshot/restoration |
| `compactmachines:personal_shrinking_device` | handling core (one iron ingot) | III | Entering and building compact rooms |
| `hostilenetworks:sim_chamber` | ecosystem capsule (one ender pearl) | IV | Entity-free mob drops |
| `draconicevolution:components/wyvern_core` | Ark bus (one draconium ingot) | V | Wyvern tier, energy core, flight module, reactor parts |
| `draconicevolution:components/awakened_core` | renewal engine (one awakened ingot of four) | V | Awakened tier |
| `draconicevolution:tools/dislocator` | routing matrix (one blaze powder) | III | Dislocator teleports and their derivatives |

The pattern follows the existing [RFTools](rftools-family.md) and [resource balance](resource-balance.md) scripts: materials from later acts, one per acquisition point, no new currency. Act III components are the existing power regulator, routing matrix and handling core; IV the spectral lens, horizon chart, ecosystem capsule and containment seal; V the Ark bus and renewal engine ([component graph](act-three-recipes.md)). Twilight ironwood and Aether zanite are Act IV expedition materials with native producers outside this family; Nature's Aura sky ingot and Mekanism atomic alloy are V materials. For Powah the stage lands on the tier capacitor because every tier machine consumes it; a 36-block niotic reactor therefore costs 36 ironwood ingots on top of its native crystals. Chaotic Draconic gear keeps its native Chaos Guardian fragments; no reward, quest or loot table was touched.

Deliberately native: Iron Furnaces, Utilitarian, Create Crafts & Additions, Mekanism Tools and Torchmaster (no power jump beyond their inputs); Create: Enchantment Industry's printer, whose 1×3 recipe has no repeated ingredient or empty slot and whose copies are paid in liquid experience; the goo tiers of Just Dire Things, which already need Nether, End and Deep Dark materials; and Draconic's grinder (a mob killer, not a spawner). Iron Jetpacks generates its jetpack, cell, thruster and capacitor recipes at runtime from its jetpack roster through Cucumber's recipe-manager event, so the stage is applied to the static strap and coil recipes that those generated recipes consume; the pack ships no replacement roster.

After loading, the script logs `[ENTRELUMEN_INDUSTRIAL_BALANCE]`: a registered count, then an after-recipes check that each ID has exactly one loaded recipe with the native output and exactly one whose `Recipe.getIngredients()` accepts the added material. Industrial Foregoing's dissolution chamber recipe returns no `getIngredients()`, so for list-based rows the check falls back to the loaded recipe's own public input list (`DissolutionChamberRecipe.input`) and tests it with the added item. A missing native ID or component aborts before any recipe changes. This proves loaded ingredients, not crafting in a grid, energy costs or balance.

## Upstream data corrections

The first dedicated start with this family logged four ERROR lines from two upstream data files, both harmless but noisy. The generator writes minimal, reversible overrides under `pack/kubejs/data`:

- `create_dragons_plus/loot_table/blocks/fragile_fluid_tank.json` and `levitite_fragile_fluid_tank.json`: Create: Dragons Plus registers these blocks only with the optional Sable physics mod, but ships their loot tables unconditionally. Each override is the original table plus a `neoforge:item_exists` condition, so the table loads again automatically if the block exists.
- `industrialforegoing/curios/entities/entities.json`: Industrial Foregoing's player slot list names `example` and `feet`, which no selected mod registers. The override removes exactly those two values and keeps the other ten slots.

## Keybindings

JAR bytecode defaults collided with world controls already in the preset: Iron Jetpacks engine and Just Dire Things tool toggle on V (FTB Ultimine), jetpack hover on H, Draconic place-item on P and tool config on C (Ars Nouveau reads C as a raw key code), and the HNN deep learner on U. `pack/config/entrelumen/client-preset.json` now ships F4 (engine), Alt+H (hover), Alt+T / Shift+T (JDT toggle / tool UI), Alt+P (place item), Alt+Y (tool config) and Alt+U (deep learner) through the Default Options fragment; jetpack throttle keeps its free comma/period defaults. `tools/audit_keybindings.py` covers the new names. A simulated audit on the current profile options plus these bytecode defaults found no world overlap and no raw-code risk; the modifier overlaps it reports are review-context. No client was launched, so the real options file and combined equipped-item behaviour remain client QA.

## Rejected or deferred

- **Oritech 1.2.11** (11 MB, 965 recipes, 211 blockstates, nine ore biome modifiers): a third complete machine progression beside Mekanism and IE with its own metal set; deferred until measured memory headroom exists.
- **Ender IO 8.2.11-beta**: beta, 825 blockstates and 1,246 recipes; its conduits duplicate Pipez/LaserIO/XNet/Mekanism transport and its SAG/alloy line duplicates IE/Mekanism.
- **Modern Industrialization 2.5.6**: 2,990 recipes, 20 ore biome modifiers and a parallel metal economy.
- **Extreme Reactors 2 + ZeroCore 2**: Mekanism Generators already provides the scalable fission reactor and industrial turbine; ER would add three more ores and a separate fuel chain for the same role.
- **Applied Flux**: FE-in-ME duplicates RFTools dimensional cells and now Flux Networks (consistent with the earlier [magic family](magic-automation-family.md) decision).
- **Productive Trees 1.1.0** (14 MB, 3,931 blockstates, 3,523 recipes, 204 placed features): a full wood palette on top of Pam's orchard and Productive Bees; the weight is not justified by a new mechanic.
- **Super Factory Manager**: an eighth programmable logistics system beside AE2, XNet, Integrated Tunnels, LaserIO, Modular Routers, Pipez and ComputerCraft.
- **Industrial Foregoing Souls, Create: New Age, Generator Galore**: an extra IF resource line, a second Create↔FE bridge and a second tiered generator mod.

## NeoForge

The lock, companion and QA server already run NeoForge **21.1.249** (the reference instance's version), not 21.1.215; no loader change was needed. The strictest new requirement is Create Crafts & Additions' `[21.1.248,)`.

## Verification boundary

Static: `python tools/curate_pack.py --check` (both sides), `python tools/generate_family_balance.py --check`, `python tools/test_family_balance.py`, `python tools/test_curate_families.py`, Default Options generation and its test. Runtime evidence for the owned dedicated server and the two stopped client profiles is in [the runtime receipt](../verification/industrial-expansion-runtime.json). No client was launched: rendering, jetpack/gadget handling, keybinding behaviour, Compact Machines rooms, reactor throughput and client frame time/memory are unmeasured.
