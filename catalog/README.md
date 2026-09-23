# Dependency curation

`curated.json` locks the initial ENTRELUMEN selection by SHA-256, CurseForge project/file IDs, source URL, JAR versions, declared license, dependencies, bundled JarJar providers, side and original content role. Integration descriptions are design intent, not claims that recipes or balance are implemented.

This catalog includes no copied ATM10 configuration, scripts, quests, assets or narrative. The source instance is read-only. Installed dependency JARs retain their authors' licenses. `allowModDistribution` is recorded when present in the installed CurseForge metadata; a missing value remains null and does not assert distribution permission. Public exports must reference official CurseForge files through the App rather than bundling these cached JARs into source control.

## Commands

Run with Python 3.11+ from the repository:

```powershell
python tools/curate_pack.py --refresh 'PATH/TO/REFERENCE_INSTANCE' --check
python tools/curate_pack.py --check
python tools/curate_pack.py --install 'PATH/TO/SEPARATE_INSTANCE' --side client
python tools/curate_pack.py --install 'PATH/TO/SEPARATE_SERVER' --side server
```

`catalog/local-paths.json` is ignored and holds machine-local origins. `catalog/downloads/` is ignored and holds additional official dependencies. CurseForge metadata lives in `external-sources.json`; pinned Modrinth additions carry their project/version IDs, metadata and download URLs directly in `curated.json`. Fetch those exact public URLs and verify their recorded hashes before refreshing on a new workstation. No account data is needed or stored. Existing source SHA-1 values are checked during refresh; the final lock verifies every local SHA-256 and recorded provider SHA-1/SHA-512 on check/install.

Do not use a whole-instance refresh to add a small batch: it can select newer files from the reference instance. Preserve the existing lock entries and compare every pre-existing filename/hash when integrating additions. The [building and expeditions batch](../docs/design/building-and-expeditions.md) follows this additive rule.

The installer copies only selected JARs, writes an `entrelumen-dependencies.json` receipt, preserves existing identical files, refuses differing file overwrites and refuses source directories or their descendants. It does not remove unexpected files from a nonempty destination; use a dedicated clean instance and inspect its receipt. It never creates the final CurseForge manifest. Client-only exclusions are explicit; all other dependencies are conservatively included on the server until actual runtime evidence narrows them.

## Curatorial boundaries

- One principal digital-storage system (AE2), three complementary expedition dimensions (Aether, Twilight Forest, Bumblezone), no extra biome overhaul stack. RFTools Power stores energy, not items; RFTools Storage and Dimensions are excluded.
- No ProjectE, Allthemodium, Alltheores, Allthecompressed or AlltheTweaks. No imported pack-specific changes.
- Kitchen-sink diversity includes environmental magic, soul magic, lasers, pressure, programmable logistics, fishing, cooking and decorative construction.
- QoL supports inventory, recipes, information, keybinds, construction, navigation, recovery, cooperative claims and accessibility. Power-affecting upgrades still need original stage balance.
- Compasses, tombstone magic/rewards, resource farms, forced chunks and late mobility require configuration/playtesting by the integrator.
- Presence/dependency/hash checks do not establish FPS, memory, recipe compatibility, gameplay pacing or successful launch. Validate client and dedicated server before accepting the selection.

## Loader version evidence

Malum 1.8.2 and Lodestone 1.8.2 declare Minecraft `[1.21,1.21.1)`, as do several installed 1.21.1 dependencies. This is accepted by the unmodified FancyModLoader 4.0.44 `VersionSupportMatrix`: when running 1.21.1 it additionally tests Minecraft 1.21 and NeoForge 21.0.166. Evidence inspected from the official [loader 4.0.44 source artifact](https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/4.0.44/loader-4.0.44-sources.jar), `net/neoforged/fml/loading/VersionSupportMatrix.java`. No dependency JAR is patched. The catalog stores all raw ranges; actual loader verification is still required for the complete combination.

Official additional-file evidence is retained in `external-sources.json`; no third-party rehost is used.

The selection now contains 157 client / 122 server dependencies. Default Options 21.1.8 is client-only and uses the already pinned Balm. Its native fragments and the companion’s missing-option merge apply authored defaults while preserving existing values; see [runtime evidence](../docs/verification/automatic-defaults-runtime.json). The [on-demand tooltip batch](../docs/design/qol-tooltips.md) adds Shulker Box Tooltip, JEED, Equipment Compare and the required Iceberg library as client-only JARs. The [RFTools/XNet family](../docs/design/rftools-family.md) adds five content projects and McJtyLib without changing the 151 previous lock entries. Dedicated startup, all 11 adjusted crafting recipes and configured quarry-card preservation have [bounded runtime evidence](../docs/verification/rftools-resource-runtime.json); client and machine-workload validation remain pending.
