# Dependency curation

`curated.json` locks the initial ENTRELUMEN selection by SHA-256, CurseForge project/file IDs, source URL, JAR versions, declared license, dependencies, bundled JarJar providers, side and original content role. Integration descriptions are design intent, not claims that recipes or balance are implemented.

This catalog includes no copied ATM10 configuration, scripts, quests, assets or narrative. The source instance is read-only. Installed dependency JARs retain their authors' licenses. `allowModDistribution` is recorded when present in the installed CurseForge metadata; a missing value remains null and does not assert distribution permission. Public exports must reference official CurseForge files through the App rather than bundling these cached JARs into source control.

## Commands

Run with Python 3.11+ from the repository:

```powershell
python tools/curate_pack.py --refresh 'PATH/TO/REFERENCE_INSTANCE' --check
python tools/curate_pack.py --add-families 'PATH/TO/REFERENCE_INSTANCE' --check
python tools/curate_pack.py --check
python tools/curate_pack.py --install 'PATH/TO/SEPARATE_INSTANCE' --side client
python tools/curate_pack.py --install 'PATH/TO/SEPARATE_SERVER' --side server
```

`catalog/local-paths.json` is ignored and holds machine-local origins. `catalog/downloads/` is ignored and holds additional official dependencies. CurseForge metadata lives in `external-sources.json`; pinned Modrinth additions carry their project/version IDs, metadata and download URLs directly in `curated.json`. Fetch those exact public URLs and verify their recorded hashes before refreshing on a new workstation. No account data is needed or stored. Existing source SHA-1 values are checked during refresh; the final lock verifies every local SHA-256 and recorded provider SHA-1/SHA-512 on check/install.

Do not use a whole-instance refresh to add a small batch: it can select newer files from the reference instance. Preserve the existing lock entries and compare every pre-existing filename/hash when integrating additions. The [building and expeditions batch](../docs/design/building-and-expeditions.md) follows this additive rule.

`catalog/families/*.json` holds separately reviewed roles and exact CurseForge file pins. `--add-families` retains every existing lock entry and local origin, resolves only missing selected mods and dependencies, and validates both sides before writing. A changed family pin or missing dependency rejects the addition. `--check` also verifies these pins. Full `--refresh` remains an explicit rebuild and can update unpinned entries; use additive curation for ordinary integration.

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

The selection grew to 170 client / 135 server dependencies before the large-parity families below. Default Options 21.1.8 is client-only and uses the already pinned Balm. Its native fragments and the companion’s missing-option merge apply authored defaults while preserving existing values; see [runtime evidence](../docs/verification/automatic-defaults-runtime.json). The [on-demand tooltip batch](../docs/design/qol-tooltips.md) adds Shulker Box Tooltip, JEED, Equipment Compare and the required Iceberg library as client-only JARs. The [RFTools/XNet family](../docs/design/rftools-family.md) adds five content projects and McJtyLib without changing the 151 previous lock entries. Dedicated startup, all 11 adjusted crafting recipes and configured quarry-card preservation have [bounded runtime evidence](../docs/verification/rftools-resource-runtime.json); client and machine-workload validation remain pending.

The [magic/AE2 family](../docs/design/magic-automation-family.md) and [cooking/provisions family](../docs/design/cooking-provisions-family.md) add thirteen useful projects while preserving all 157 previous entries exactly. One unaccepted candidate, AE2 Import/Export Card 1.6.0, was replaced by official 1.5.0 after an actual dedicated-side mixin error; no dependency JAR was patched. The final normal dedicated startup/save is clean of ERROR lines and four directed cases passed. Client and active magic-workload validation are still pending; see [bounded runtime evidence](../verification/magic-cooking-teams-runtime.json).

## Large-parity families (23 September)

Families are added one reviewed batch at a time with `--add-families`, preserving every earlier entry byte for byte. Staged acquisition for their power jumps is generated by `tools/generate_family_balance.py` from the pinned JARs (see each design note); acts describe acquisition, never use or gift restrictions.

| Family | Selected / libraries | Lock after the batch | Runtime receipt |
|---|---|---|---|
| [Industrial expansion](../docs/design/industrial-expansion-family.md) | 15 / 4 | 197 client / 162 server | [industrial](../docs/verification/industrial-expansion-runtime.json) |
| [QoL and functional decoration](../docs/design/qol-functional-decor-family.md) | 18 / 2 | 217 client / 178 server | [qol](../docs/verification/qol-functional-decor-runtime.json) |
| [Arcane expansion](../docs/design/arcane-expansion-family.md) | 9 / 4 | 230 client / 191 server | [arcane](../docs/verification/arcane-expansion-runtime.json) |

Decisions against Oritech, Ender IO, Modern Industrialization, Extreme Reactors, Applied Flux, Productive Trees and Super Factory Manager are recorded in the industrial note; the Apotheosis suite is kept out for the reasons in the arcane note. The lock, companion and QA server stay on NeoForge 21.1.249; no loader change was required.
