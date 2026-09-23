# Building and expeditions — first content batch

Six pinned NeoForge 1.21.1 releases extend distinct activities. The previous 140 dependency entries remain byte-for-byte unchanged. The catalog now contains 146 client and 116 server dependency JARs, plus the companion. Bundled libraries do not count as separate additions.

| Addition | Introduction | Lasting use and integration |
| --- | --- | --- |
| Create: Connected 1.3.3 | Act II mechanical workshop | Compact drivetrain controls serve Create production of Atlas components; convenient controls remain useful beside digital logistics. |
| Create: Copycats+ 3.0.9 | Act II material-matched mechanisms | Shafts, pipes and moving assemblies can share the observatory's materials. FramedBlocks remains the general stationary construction route. |
| Macaw's Roofs 2.3.2 | Act I shelter | Roof shapes, gutters and finishes give expedition outposts and the inhabited Ark district practical architectural detail. |
| Macaw's Bridges 3.1.2 | Act I local routes | Bridges and railings join farms, workshops and outposts. Later walkways connect machines without requiring flight. |
| Exposure 1.9.18 | Optional early field photography | Physical photographs and albums record discoveries, reconstruction and cooperative expeditions; later displays belong in habitation spaces. |
| Immersive Aircraft 1.5.2 | Act III expedition workshop | Fuelled aircraft survey routes and connect provisioned outposts. Normal acquisition should require an industrial engine, while gifts and operation remain unrestricted. |

These are curatorial roles, not completed optional quest branches. Decorative blocks remain a choice for useful construction; none becomes an arbitrary mandatory campaign ingredient. First-hour survival validation still precedes mass quest authoring.

## Pinned compatibility and provenance

The actual JAR manifests require Create **6.0.10 or newer** for Connected and **6.0.8 or newer** for Copycats+. The existing Create 6.0.10 satisfies both. Both addons require NeoForge 21.1.200 or newer; this pack pins 21.1.249. Exposure optionally integrates with Create 6.0.7 or newer. No additional standalone library was required; Connected carries Sable Companion 1.6.0 internally.

Official version metadata and SHA-1/SHA-512 were verified before computing and recording each local SHA-256:

- [Connected Xe7EqzfQ](https://modrinth.com/mod/create-connected/version/Xe7EqzfQ)
- [Copycats+ bPYeUWZx](https://modrinth.com/mod/copycats/version/bPYeUWZx)
- [Roofs jiXRXiSt](https://modrinth.com/mod/macaws-roofs/version/jiXRXiSt)
- [Bridges aQ7rY7ng](https://modrinth.com/mod/macaws-bridges/version/aQ7rY7ng)
- [Exposure KZR7AUbh](https://modrinth.com/mod/exposure/version/KZR7AUbh)
- [Aircraft ZZTlNkV9](https://modrinth.com/mod/immersive-aircraft/version/ZZTlNkV9)

These dependencies retain their own licenses: AGPL for Connected, the authors' reserved-rights terms for Copycats+/Macaw's mods, MIT for Exposure and GPL for Aircraft. [Copycats+ explicitly permits modpack inclusion](https://modrinth.com/mod/copycats). The repository contains metadata and original integration only. The CurseForge App recognized five additions; its SHA-1 values match the local files and their official Modrinth hashes. Their verified project/file references and distribution metadata are recorded in the catalog. Connected remains unresolved by the App. Final publication still requires the App's official export, rather than a handmade manifest.

## Acquisition and compatibility

Aircraft uses two [static recipe changes](aircraft-balance.md): one power regulator replaces a cobblestone in the engine, and one handling core completes the motorless gyrodyne. These inputs link ordinary aircraft manufacturing to the Act III workshop. No item-use, team or gift permission is added.

Connected ships 16 [optional Dye Depot loot tables](connected-loot-compat.md) whose items are absent in this selection. The compatibility guard preserves native tables for present items and supplies a conditional empty fallback only for the missing optional items. It does not alter the 16 vanilla-color fan catalysts.

Connected's `feature_categories.copycats` is disabled so Copycats+ owns the nine overlapping construction families. Both native migration flags remain enabled; block IDs are retained. After reloading the complete server, all nine Connected output families have zero active recipes, while each equivalent Copycats+ family has one or more. Connected's mechanical and logistics categories retain their defaults.

## Technical budget

The six JARs add 15,694,564 compressed bytes. This is a storage measure, not a memory or performance estimate. Roofs alone carries 2,683 model JSON files, so client model loading and in-world rendering remain necessary checks. No biome overhaul or new dimension is added by this batch.

Do not infer a rendering defect in the pinned version from an old Copycats+ issue, or disable connected textures blindly. Confirm the current options and test populated contraptions. Measure Exposure's native capture and aircraft movement in the client before accepting their cost.

The batch does not establish the final-scale FPS, two-hour stability or survival pacing targets.
