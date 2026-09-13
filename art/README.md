# ENTRELUMEN native pixel artwork

This directory contains candidate resource-pack artwork, not game screenshots or final aesthetic acceptance. `contact-sheet.png` previews the actual texture files using integer nearest-neighbour scaling. The user rejected the previous Atlas icon's distance from Minecraft and the flat Atlas interface; the current work uses real Minecraft references before drawing.

## Files and use

- `../pack/resourcepacks/entrelumen/` is an unpacked Java 1.21.1 resource pack. Enable it above other visual packs. Root integrator owns default enablement in the client's options; this worker does not change client configuration.
- Twenty 16×16 RGBA item textures include the Atlas, lens, field notes, signal core and sixteen integration components. They are authored on that grid rather than downsampled. All item textures and models are also bundled in the companion mod, so its identity does not depend on resource-pack enablement.
- Seven 32×32 block faces override `engineering_module`, `arcane_module`, `nature_module`, `exploration_module`, `logistics_module`, `habitation_module`, `ark_controller`. Shared top/bottom faces add coherent casing. Block models use vanilla `cube_bottom_top`; block item models reference the custom block models. Existing companion blockstates resolve these same model names.
- `pack.png` is the Atlas brand mark at 128×128. Description translations are EN/ES. No item names are overridden, preserving the companion glossary.
- `sprites/atlas.json` is the editable native 16×16 PixelLab Atlas selection, with18 opaque colors and binary transparency. Its paired `atlas.png` is preserved byte-for-byte in the game. Variant47 was selected from64 Pro outputs and reduced only in palette, without dithering or spatial resizing. The actual Minecraft book and enchanted-book sprites were inspected at inventory scale before selection; their pixels are not distributed here.
- `gui/atlas_book.png` is PixelLab's unmodified quantized300×210 book background, generated with an original composition guide after inspecting Minecraft's `book.png` and `BookViewScreen`. `../tools/build_atlas_book_art.py` verifies its provenance/hash and copies exact bytes into both destinations. Text and controls are rendered separately by Minecraft. The texture alone does not validate the populated interface.
- `../tools/build_survey_station_art.py` creates the station's 24 cuboids and four native 16×16 material textures. Its palette is separate from the inventory sprite so an icon redesign cannot invalidate the station generator.
- `svg/` contains deterministic intermediate rect/polygon SVGs. `build_art.py` rasterizes these without antialiasing and emits matching PNGs/models for the resource pack and companion mod, plus a hash inventory. The seven block models and their nine textures are present in both destinations.

## Identity

Palette: dark teal ink, weathered copper edges, ivory cartographic marks, patina and one botanical leaf tone. Every pixel is opaque or transparent; no blurry gradients or animation. The book's compass is the mark. The six disciplines are gear, crystal, sprout, compass, branching transport and lit house. The controller reuses the signal's light surrounded by six terminal marks.

`python art/build_art.py` regenerates. `python art/build_art.py --check` verifies source/PNG equality, sizes, model paths and format metadata without writes. Pillow is the only Python dependency. Preview typography uses a local font for the contact sheet only; no font software is included in the resource pack.

## Format and provenance

`pack_format: 34` matches Java 1.21 resources. Primary reference: [Mojang 1.21 release notes](https://www.minecraft.net/nb-no/article/minecraft-java-edition-1-21). It is also the resource format used by 1.21.1; runtime compatibility remains an integration check.

The artwork and bitmap logo were made for ENTRELUMEN, with no stock assets or copied pack textures. Atlas history: rejected32×32 ImageGen reconstruction, interim hand-drawn16×16 book, then the current PixelLab native16×16 selection. The book GUI also comes from PixelLab. Original provider outputs and sanitized usage receipts remain in `candidates/`; selected assets have separate provenance in `sprites/atlas.json` and `gui/atlas_book.json`. The old menu illustration is AI-derived and processed by `pixel_filter.py`: see `menu/pixel-provenance.json` and `../DESIGN.md`. **That menu direction was rejected and remains inactive.** Its high-resolution source is not distributed in the pack. The project’s restricted source-available license governs its own assets: private use and modification permitted; public redistribution requires Elias's permission. Third-party mods and compatibility assets retain their own licenses and notices. Do not describe these assets as OSI open source.

## Evidence limits

PASS: 29 SVG/PNG pairs match deterministically; twenty item model mappings and seven block model mappings resolve to original textures in both the mod and the resource pack. Item textures are 16×16 and legacy cube faces remain 32×32; `pack_format` is 34. `atlas-inventory-preview.png` shows the current original item at 1× and 3× over three backgrounds. These checks establish reproducibility, not beauty.

NOT YET VERIFIED for the new Atlas: actual 1.21.1 inventory/held-item appearance, lighting, compatibility with other visual packs and final aesthetic acceptance. Current cube-faced Ark models are provisional and do not establish the requested beautiful endgame. No gameplay or performance claims are made from artwork previews.
