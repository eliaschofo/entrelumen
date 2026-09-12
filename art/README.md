# ENTRELUMEN original pixel-vector identity

This is usable resource-pack artwork, not a mock game screenshot. `contact-sheet.png` previews the actual texture files at 4x nearest-neighbour scale. It was visually inspected after generation: book, lens, field notes and signal silhouettes are distinct; all seven block motifs are distinguishable; copper frames and teal fields stay consistent.

## Files and use

- `../pack/resourcepacks/entrelumen/` is an unpacked Java 1.21.1 resource pack. Enable it above other visual packs. Root integrator owns default enablement in the client's options; this worker does not change client configuration.
- Four 32×32 RGBA item textures override `atlas`, `raw_lens`, `survey_notes`, `signal_core` through original item model JSON.
- Seven 32×32 block faces override `engineering_module`, `arcane_module`, `nature_module`, `exploration_module`, `logistics_module`, `habitation_module`, `ark_controller`. Shared top/bottom faces add coherent casing. Block models use vanilla `cube_bottom_top`; block item models reference the custom block models. Existing companion blockstates resolve these same model names.
- `pack.png` is the Atlas brand mark at 128×128. Description translations are EN/ES. No item names are overridden, preserving the companion glossary.
- `svg/` contains all original vector sources. `build_art.py` produces those declarative rect/polygon SVGs, rasterizes their restricted primitive set through Pillow, and emits all PNGs/models plus a hash inventory. No downloaded or third-party art is read.

## Identity

Palette: dark teal ink, weathered copper edges, ivory cartographic marks, patina and one botanical leaf tone. Every pixel is opaque or transparent; no blurry gradients or animation. The book's compass is the mark. The six disciplines are gear, crystal, sprout, compass, branching transport and lit house. The controller reuses the signal's light surrounded by six terminal marks.

`python art/build_art.py` regenerates. `python art/build_art.py --check` verifies source/PNG equality, sizes, model paths and format metadata without writes. Pillow is the only Python dependency. Preview typography uses a local font for the contact sheet only; no font software is included in the resource pack.

## Format and provenance

`pack_format: 34` matches Java 1.21 resources. Primary reference: [Mojang 1.21 release notes](https://www.minecraft.net/nb-no/article/minecraft-java-edition-1-21). It is also the resource format used by 1.21.1; runtime compatibility remains an integration check.

Original artwork and SVG/code authored for ENTRELUMEN. No AI image-generation service, stock assets, game texture tracing, or assets from ATM10 were used. The project’s restricted source-available license governs these original assets: private use and modification permitted; public redistribution requires Elias's permission. Third-party mods retain their own licenses. Do not describe these assets as OSI open source.

## Evidence limits

PASS: 13 SVG/PNG pairs match deterministically; four item model mappings and seven block model mappings resolve to original textures; all textures are 32×32 RGBA; `pack_format` is 34. Contact-sheet visual inspection completed.

NOT YET VERIFIED: actual 1.21.1 resource reload, inventory view at native scale, held-item view, block lighting/mipmap behavior, compatibility with other enabled visual packs, and translated pack description in the selection screen. No gameplay or performance claims are made from this artwork preview.
