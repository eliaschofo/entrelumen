# ENTRELUMEN native pixel artwork

Item and block textures are authored on native 16×16 grids. `grids/item/*.txt` and `grids/block/*.txt` are the editable sources: palette lines `X #rrggbb`, a blank line, then 16 rows where `.` is transparent. `python art/build_art.py` renders them texel-for-texel into the resource pack (`../pack/resourcepacks/entrelumen/`) and the companion mod, writes item/block models, the 128×128 `pack.png`, `contact-sheet.png` and the resource-pack hash inventory. `python art/build_art.py --check` verifies decoded texels, mod/resource-pack parity, models, EN/ES names and hashes without writing.

## Direction (2026-09-23 redo)

- Minecraft item language: readable silhouette filling most of the slot, dark hue-shifted outline, 3–5 tone ramps lit from the top left, no noise pixels. Each icon was compared at 1×, 2× and 3× inside a real vanilla inventory next to vanilla items (`authoring/lineup.py`).
- One master palette (`authoring/palette.py`): aged copper (Minecraft's four oxidation stages), brass, verdigris, teal lumen light, parchment, leather green, iron, wood, crimson, violet, leaf, sky, glass and straw. Every icon uses a subset, so the set reads as one family.
- Ark modules share a tuff-stone frame, a weathered copper rim, verdigris corner caps and a recessed panel with the discipline emblem; the shared top is a copper grate with teal light. Block faces are 16×16 like vanilla.
- PixelLab (`/generate-image-v2`, 16×16, a vanilla texture as `style_image`) produced drafts. Good drafts were promoted by palette remap and despeckle; the rest were hand-drawn. Sketch-guided drafts and PixelLab block faces were noisy and rejected. Nothing is downscaled from a larger illustration.
- `grids/provenance.json` records, per texture, the method, the PixelLab job/candidate when used, the palette ramps and the Minecraft/mod textures inspected. Sanitized job receipts and candidate sheets are in `candidates/pixellab-v2/`; no credential is stored.

## Other surfaces

- `gui/atlas_book.png`: PixelLab 300×210 book background (`../tools/build_atlas_book_art.py`).
- `../tools/build_survey_station_art.py`: the survey station's 24 cuboids and four 16×16 textures (unchanged; approved by Elias).
- `menu/`: title/loading identity; see `../docs/design/menu-identity.md`.
- `publication/`: CurseForge cover and avatar.

## Evidence limits

The checks prove reproducibility and format, not beauty. Previews are software renders, never game captures. In-game review of inventory, held items, placed blocks and lighting is still required.
