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
