# Third-party notices

ENTRELUMEN is an original modpack, not a fork of ATM10, FTB Evolution or Craftoria. Those packs are design references. Their quests, narrative, scripts and artwork are not licensed by this repository and are not copied into it.

Minecraft and its resources belong to their respective rights holders and are not distributed in this source repository. Mods are obtained from official project files and retain their original license terms. The curated inventory records each dependency and its provenance; unresolved license metadata is reported rather than guessed.

The NeoForge MDK template notice is retained in `companion/TEMPLATE_LICENSE.txt`. Gradle wrapper components retain their upstream licensing.

GitHub's public repository terms allow viewing and in-platform forking. The original pack license does not claim to override those platform rights.

## Mod data included in this repository

Some files under `pack/kubejs/data/` are copied or derived from a mod's data files, only where that mod's license allows it. Each entry names the files, the source and the license. Data from mods whose license does not allow redistribution is never copied; the pack refers to it by ID or writes its own file from scratch.

### Compact Machines

- **Files:** `pack/kubejs/data/compactmachines/` (room templates and machine recipes), copied unchanged from the datapack bundled in `compactmachines-neoforge-7.0.81.jar`. The pack enables them because the bundled datapack is off by default. `tools/generate_family_balance.py` (family `industrial`) writes them.
- **Source:** https://github.com/CompactMods/CompactMachines
- **License:** MIT.

> MIT License
>
> Copyright (c) 2024 Compact Mods
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

### Ad Astra

Some files under `pack/kubejs/data` are derived from data files of Ad Astra 1.16.19 (`adastra-1.21.1-1.16.19-neoforge.jar`). Ad Astra is by Alex Nijjar, the author its mod metadata names, and the contributors it credits (CodexAdrian, Facu, Fizz, MsRandom, ThatGravyBoat and Mrbysco), published by Terrarium at https://github.com/terrarium-earth/Ad-Astra. Its Terrarium License v1 (https://github.com/terrarium-earth/Ad-Astra/blob/1.21.1/LICENSE) licenses every file under the mod's `resources/data/` under the MIT License below; everything else in the mod is all rights reserved and is not copied here. The license text has no separate copyright line.

- `pack/kubejs/data/ad_astra/loot_table/chests/` (the Moon dungeon, large Moon dungeon, Mars temple, Lunarian blacksmith and Lunarian house tables) and `pack/kubejs/data/minecraft/loot_table/loot.json`: the mod's chest tables moved from `loot_tables/` to the 1.21 folder; the enchanted books name `#minecraft:on_random_loot`.
- `pack/kubejs/data/ad_astra/worldgen/template_pool/dungeon/moon/room.json`: the mod's Moon dungeon room pool, its library element pointed at the `libary` template the mod ships.
- The 49 files under `pack/kubejs/data/create/recipe/`, `pack/kubejs/data/mekanism/recipe/` and `pack/kubejs/data/immersiveengineering/recipe/` that share their path with the mod's compat recipes: those recipes, disabled with a `neoforge:false` condition.

`tools/generate_family_balance.py` (family `pingpong`) writes these files from the pinned JAR. `pack/kubejs/data/ad_astra/tags/worldgen/biome/has_structure/venus_bullet.json` replaces a tag of the mod with an empty one and holds nothing of it.

> MIT License
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

### Loot tables written for other mods

These files are ENTRELUMEN's own. None copies the named mod's data; each only names that mod's IDs.

- `pack/kubejs/data/endermanoverhaul/loot_table/blocks/tiny_skull.json`: a self-drop block table written from scratch in vanilla's form for the Enderman Overhaul block (all rights reserved), whose own table is in the 1.20 folder.
- Aliases under `pack/kubejs/data/{minecraft,nova_structures,kaisyn}/loot_table/`: one pool whose only entry is a `minecraft:loot_table` reference to an existing table, at IDs that the structure templates of Deep Aether, L_Ender's Cataclysm, Dungeons and Taverns and Towns and Towers name but do not ship.

### Files written for mods that reserve their data

Apotheosis (its metadata reads «MIT License (code) / All Rights Reserved (assets)» and its repository's `LICENSE_ASSETS` names no scope), Create Deco, Forbidden Arcanus, Iron's Jewelry, Malum and Dungeons and Taverns are all rights reserved or unclear, so no file of theirs is copied. Where the pack changes or turns off one of their data files, the file at the mod's own path holds only `{"neoforge:conditions": [{"type": "neoforge:false"}]}`, and whatever takes its place is ENTRELUMEN's own, written from the pack's specification in `tools/generate_family_balance.py` and `tools/generate_malum_compat.py`, which fail when the pinned JAR no longer matches:

- `pack/kubejs/data/entrelumen/tier_augments/{haven,frontier,ascent}/max_eterna.json`: our World Tier Eterna ceilings, in the codec fields of an Apotheosis tier augment, under IDs of ours; Apotheosis's `tier_augments/*/max_eterna.json` are turned off.
- `pack/kubejs/data/entrelumen/forbidden_arcanus/hephaestus_forge/ritual/eternal_stella.json`: our ritual for the Eternal Stella, with a containment seal; Forbidden Arcanus's ritual of the same name is turned off.
- `pack/kubejs/server_scripts/entrelumen_malum_compat.js`: our recipes for four Occultism chalk repairs and the grim talc milling, whose Malum files ship stale; those files are turned off.
- Turned off and replaced by nothing: Create Deco's placard dye recipe, five Malum Undergarden repair recipes, Iron's Jewelry's developer test loot table and two Dungeons and Taverns hidden advancements.

`tools/check_loot_tables.py --copies` fails when any file under `pack/kubejs/data` copies data of such a mod, and `tools/test_family_balance.py` runs it.
