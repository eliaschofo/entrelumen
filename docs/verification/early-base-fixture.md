# Active early-base benchmark fixture

This is an isolated QA workload for the current 151-dependency client, not released world generation or accepted artwork. It prepares a repeatable moving baseline for later catalog growth. **No performance capture was completed in this fixture.** The previous stationary collector run remains separate evidence.

## Reproduction

Create a separate Minecraft 1.21.1 / NeoForge 21.1.249 normal world with seed `71942026`, Creative preparation, Normal difficulty and commands enabled. Never run setup in a player's existing base. Generate into a new or empty directory outside the pack:

```text
python tools/create_benchmark_fixture.py --output <absolute-empty-datapack-directory> --origin 0 160 0
```

Install that directory under the stopped world's `datapacks/`, then reopen it. Load the footprint around x/z 0 before invoking `/function entrelumen_benchmark:setup`. Setup replaces the 49×41 floor and clears five blocks above it. A marker guards against repeated setup; an interrupted construction requires inspection in the disposable world, not repeated blind invocation. `/function entrelumen_benchmark:board` mounts the fixture's tagged cart. No tick/load functions, forced chunk loading, automatic refills or repeated command execution are installed.

The fixture contains eight furnaces, two smokers, 30 hoppers, 30 barrels, 80 wheat plants around central irrigation, eight cows, eight sheep and a closed 160-rail route with 24 powered segments. Each processor starts with 256 inputs and 32 coal; the faster smokers have approximately 21 minutes of stock at normal tick rate. Fuel barrels sit above their lateral input hoppers. The platform is a controlled apparatus above the snowy terrain; it is not a representative final industrial base.

## Evidence on 2026-09-23

The dedicated save `ENTRELUMEN Early Benchmark 151` loaded setup successfully. Desktop observations showed the player mounted at different points of the route and lit processors. After the client closed normally, a read-only parse of its saved block entities found five iron ingots in **each of eight** output barrels and ten cooked beef in **each of two** output barrels. The saved tagged entities contain eight cows, eight sheep, one marker and the cart. This proves processing and collection occurred; it does not establish five minutes of uninterrupted movement or frame/tick performance.

The complete stopped save was privately archived and all 83 included files matched both the archive and the unchanged source. Only `session.lock` was excluded. Archive SHA-256: `27f750bccdbc67a1b42c93ebeffc0249829c24b2bed4bdd659f489c298faec1e`. The world archive and player data were not uploaded. Exact processor inventories and provenance are summarized in [early-base-fixture-runtime.json](early-base-fixture-runtime.json).

Computer Use was interrupted before the inspection command completed; the user subsequently requested work without Computer Use. The client is no longer running. No capture was started and no FPS/TPS result is inferred from the saved workload.

Afterward, source inspection established that Minecraft suppresses `/data get` feedback inside functions. The generator's `inspect` now uses explicit `/tellraw` NBT components, including empty-inventory and missing-cart messages. Its rendering remains unverified. Setup, construction and boarding command hashes are unchanged; the private snapshot preserves the original datapack. When restoring it for measurement, replace only the inspection helper from the current generator if its visible output is needed.

## Remaining measurement

Restore the prepared snapshot to a separate QA save, check stock and complete laps, then perform a separate warmup and at least 305 seconds of continuous capture at the agreed 1080p / render 10 / simulation 6 / 8 GB preset. Keep original frame/tick streams, flags, stalls, GC and final inventories. Use the [measurement protocol](benchmark-protocol.md). A result covers this prototype workload only; the final catalog, other bases, exploration, overhead, multiplayer and two-hour soak still require their own evidence.
