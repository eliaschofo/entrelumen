# QoL and functional decoration: openings, household appliances, villagers and inventory helpers

`catalog/families/qol-functional-decor.json` pins 18 selected JARs; two small libraries resolve from the same reference instance (MrCrayfish Framework 0.13.11 for Refurbished Furniture, Deimos 2.7 for Laser Bridges & Doors). The lock grows from 197 client / 162 server to **217 client / 178 server** JARs; the 197 previous entries are byte-identical. Four additions are client-only (Colorful Hearts, Overloaded Armor Bar, Not Enough Animations, Model Gap Fix). All files are official CurseForge files installed in the read-only ATM10 8.1 reference instance; their SHA-1 matched its CurseForge metadata, and licenses, ranges and sides were read from each JAR. No ATM10 configuration, keybinding file, script, quest or asset was used.

| Mod · file (CF project / file) | Role | Introduction | Later use | Relation to another system |
|---|---|---|---|---|
| Macaw's Doors · `mcw-doors-1.1.5-mc1.21.1neoforge.jar` (378646 / 7618651) | Functional doors (246 blockstates) | I: shelters and workshops | VI: Ark housing | Completes the selected Macaw windows, fences, roofs, bridges and lights |
| Macaw's Trapdoors · `mcw-trapdoors-1.1.5-mc1.21.1neoforge.jar` (400933 / 7256148) | Trapdoors (179 blockstates) | I: cellars and lofts | V-VI: service hatches | Same set; opens machinery rooms without placeholder blocks |
| Macaw's Paths and Pavings · `mcw-mcwpaths-1.1.1-mc1.21.1neoforge.jar` (629153 / 7029451) | Walkable paving (315 blockstates) | I: routes between farm and base | IV-VI: settlement districts | Pairs with Macaw bridges and waystone routes |
| Macaw's Stairs and Balconies · `mcw-mcwstairs-1.0.2-mc1.21.1neoforge.jar` (1119394 / 7317479) | Balustrade and loft stairs (224 blockstates) | I: two-storey homes | V-VI: multi-level Ark modules | Vertical circulation; complements FramedBlocks shapes |
| Refurbished Furniture · `refurbished_furniture-neoforge-1.21.1-1.0.22.jar` (897116 / 7473565) | Working household appliances (449 blockstates) | I: stove, fridge, sink and lamps | V: habitation district; post boxes between teammates | Cooks and stores food from the [cooking family](cooking-provisions-family.md); Handcrafted stays the decorative furniture set |
| Easy Villagers · `easy-villagers-neoforge-1.21.1-1.1.42.jar` (400514 / 8066730) | Villagers in compact blocks | II: breeder, farmer, converter | III-IV: iron farm and automatic trader | Fewer villager entities; iron farm and auto-trader are staged |
| Ender Storage · `EnderStorage-1.21.1-2.13.0.191.jar` (245174 / 6159037) | Colour-frequency chests, tanks, pouches | III: team frequency between bases | IV-VI: expedition resupply | Arrives with AE2 wireless logistics; vanilla ender chest remains the early personal option |
| Laser Bridges & Doors · `laserbridges-1.21.1-neoforge-6.jar` (776337 / 8602302) | Redstone hard-light bridges and fences | IV: first End crystals | V-VI: Ark walkways and hangar doors | Pairs with Glassential ghostly glass and Create redstone links |
| Spice of Life: Carrot Edition · `solcarrot-1.21.1-1.16.6.jar` (277616 / 7374098) | Food-variety hearts | I: food book | V-VI: late cuisines | Rewards the Pam, Herbs and Harvest, Farmer's Delight and sushi kitchens; pack milestones below |
| Simple Magnets · `simplemagnets-1.1.12c-neoforge-mc1.21.jar` (394140 / 6316527) | QoL item magnets | I: basic magnet | III: advanced magnet and demagnetizing coils near farms | Distinct from the AE2 wireless magnet card and Draconic magnet, which come later |
| Item Collectors · `itemcollectors-1.1.10-neoforge-mc1.21.jar` (395620 / 5550187) | QoL area collectors | II: farm collection points | IV: mob-drop rooms | Feeds Pipez/LaserIO without entity pickup lag |
| Trash Cans · `trashcans-1.1.0-neoforge-mc1.21.jar` (394535 / 8646611) | QoL filtered voiding | II: overflow from farms | IV-VI: fluid/energy overflow | Deliberate disposal beside TrashSlot for automation |
| Colorful Hearts · `colorfulhearts-neoforge-1.21.1-10.5.9.jar` (854213 / 6830399) | Client HUD | With the first extra heart | Readable health above 20 | Needed once Spice of Life adds hearts |
| Overloaded Armor Bar · `overloadedarmorbar-neoforge-1.21-2.jar` (314002 / 5537850) | Client HUD | III armour | V-VI armour above 20 points | Mekanism/Draconic armour values |
| Not Enough Animations · `notenoughanimations-neoforge-1.12.4-mc1.21.1.jar` (433760 / 8274908) | Client animations | I | All acts | Visual only; see distribution note |
| Model Gap Fix · `modelfix-1.21-1.10.jar` (676136 / 5591286) | Client model fix | I | All acts | Removes gaps in extruded item models, including original 16×16 icons |
| AllTheLeaks · `alltheleaks-1.1.12+1.21.1-neoforge.jar` (1091339 / 8648639) | Performance | Both sides | Long sessions | Memory-leak fixes; unrelated to the excluded Allthemodium family |
| No Chat Reports · `NoChatReports-NEOFORGE-1.21.1-v2.9.1.jar` (634062 / 5885735) | Cooperative chat | Both sides | Six-player server | Strips signed-report metadata from server chat |

## Staged acquisition and configuration

`tools/generate_family_balance.py --family qol` rewrites five native recipes in `pack/kubejs/server_scripts/entrelumen_qol_balance.js` with the same rules as the [industrial family](industrial-expansion-family.md):

| Native recipe ID | Added material (replaces) | Act | Reason |
|---|---|---|---|
| `easy_villagers:iron_farm` | living matrix (one glass pane) | II | Iron from a compact golem farm |
| `easy_villagers:auto_trader` | routing matrix (one glass pane) | III | Automated trading with hoppers |
| `enderstorage:ender_chest`, `ender_tank`, `ender_pouch` | routing matrix (one blaze rod / blaze powder) | III | Cross-dimension shared item and fluid transfer |

Ender Storage recolouring recipes need an existing chest, tank or pouch and are the only whitelisted alternate routes. Breeder, farmer, converter, incubator and trader blocks stay native.

`pack/defaultconfigs/solcarrot-server.toml` sets one heart per milestone at 10, 25, 45, 70 and 100 unique foods (five extra hearts in total) instead of the native two hearts at 5/10/15/20/25, which the large food catalogue would reach in the first hours. Base hearts stay ten and progress is not reset on death. On NeoForge 21.1 the mod's server config lives in the instance `config/` folder: when `config/solcarrot-server.toml` does not exist yet, NeoForge creates it from this default and fills the omitted filtering/misc keys with native defaults (logged as a correction warning); a world can still override it in its own `serverconfig/`. On the owned dedicated server the generated file carried `heartsPerMilestone = 1` and milestones `[10, 25, 45, 70, 100]`.

## Keybindings

Bytecode defaults: Easy Villagers pick-up on V and trade cycling on C, Simple Magnets toggle on H. The preset moves pick-up to Shift+V and the magnet toggle to Shift+M; trade cycling acts in the villager trading screen and keeps its native C. The other additions register no key.

## Rejected

- **Macaw's Furniture 3.4.1** (652 blockstates): a third furniture palette beside Handcrafted and Refurbished Furniture/Storage Delight without new function.
- **Additional Lights 2.1.10**: still overlaps Simply Light and Macaw's Lights, as recorded in the [builder family](builder-utilities-family.md).
- **Get It Together, Drops! 1.4**: its NeoForge file lists Fabric API (306612) as a required CurseForge project, which the curation closure correctly rejects; the benefit is cosmetic.

Not Enough Animations has `allowModDistribution=false` and a tr7zw Protective License: it may only be referenced through the official CurseForge manifest, never rehosted.

## Verification boundary

Static: `python tools/curate_pack.py --check`, `python tools/generate_family_balance.py --check`, `python tools/test_family_balance.py`, Default Options generation and a simulated keybinding audit (profile options plus the new bytecode defaults, `--simulate-preset`: no world overlap, no raw-code risk). Dedicated-server evidence is in [the runtime receipt](../verification/qol-functional-decor-runtime.json). No client was launched: HUDs, animations, furniture rendering, magnet behaviour and client memory are unmeasured.
