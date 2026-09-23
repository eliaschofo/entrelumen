# PixelLab artwork workflow

The user's basic-plan allocation authorizes iterative ENTRELUMEN artwork generation. The supplied credential is used only in memory for the named PixelLab endpoint; it is absent from source, pack files, receipts and this document. No plaintext MCP configuration was installed.

## API findings used

- [Official API schema](https://api.pixellab.ai/v2/openapi.json): `/generate-image-v2` accepts `style_image` and up to four subject references. A16×16 Pro request returned64 native16×16 candidates, charged20 generations. These are mostly palette/detail variants, not64 independent compositions.
- `/generate-ui-v2` accepts `concept_image`, exact requested canvas dimensions and a textual palette. The300×210 request returned both raw and quantized PNGs; the selected quantized output has36 opaque colors and binary transparency. This job charged20 generations.
- `/create-image-bitforge` supports native16px generation and style/initial/palette images. Its first brown-cover result was rejected for integration. It used one generation before the user upgraded the allocation.
- Pro results arrive through background jobs. Store each dispatch receipt before polling; a timeout is not permission to regenerate. Both selected jobs completed with billing usage USD0 and20 generations each. Original results and usage are versioned under `art/candidates/`.

## Reference and selection

Inspect actual equivalents first. For the Atlas, compare Minecraft1.21.1 `textures/item/book.png` and `enchanted_book.png`, both16×16, on gray inventory, parchment and dark backgrounds at1× and integer enlargement. Their source sprites contain10 and19 opaque colors respectively. References inform proportions and materials; they are not pack assets.

The selected Pro variant47 has a green leather cover, cream page edge and a small copper map mark. Its31 opaque colors were reduced to18 with median-cut palette19, no dithering, no spatial resize, and invisible RGB cleared for indexed-grid parity. This is a palette refinement of native16px output, not a downsampled illustration. The original PNG, export PNG, editable grid and hashes are retained.

For the GUI, inspect vanilla `textures/gui/book.png` (256×256 source with a192×192 book) and `BookViewScreen`. An original300×210 blank two-page guide controlled composition. Use the provider's quantized image unchanged; inset text/list/control geometry from textured edges. No labels are baked into the artwork, preserving EN/ES and Minecraft's font.

## Evidence limits — earlier review

Artifact byte/pixel checks, binary alpha and compilation pass. The new assets are selected for integration, not final user acceptance. Actual inventory, held-item, title menu, populated Atlas, both languages and representative GUI sizes still need game observation. Computer Use was stopped by physical Escape before that review. Neither the old filtered menu scene nor provisional Ark cubes become accepted through these checks.

## Updated game evidence — 12 September 2026, Argentina

This supersedes the pending-observation status above only for the observations listed here. The integrator observed the native Atlas in inventory and held in hand, then the populated Spanish GUI in a paused singleplayer session. No new PixelLab generations were dispatched during this review.

- [Title, 21:13](../verification/screenshots/title-pixellab-footer-fixed-es.png): actual F2 capture at 1024×768, GUI scale 3, on the preceding JAR (SHA256 prefix `C064`). All six buttons and four icons clear the footer.
- [Atlas after density correction, 21:23](../verification/screenshots/atlas-pixellab-es-cost-visible.png): actual F2 capture on JAR SHA256 `AA3D45EA220B6CA060ED2B799E6D80F313CFB1491C37EA43E5BF2A98D9A2C7EF`. Banco de precisión displays its 16px material icon, name, `0/1` and `faltan 1` without scrolling. The GUI uses the 300×210 book, 12px rows and native 9px font; material names reserve two rows, and costs precede prerequisites. The integrator also observed left scrolling through five projects to Archivo.
- [Atlas before density correction](../verification/screenshots/atlas-pixellab-es-before-density-fix.png) remains as failure evidence: the prior layout hid costs. It is not current acceptance evidence.

The integrator reports test/build PASS in `work/atlas-readability-build.log` for the current JAR. English, other GUI scales, right-page scrolling and station crafting remain unverified. Further Computer Use was left pending because the user was active on the desktop; no English test is claimed. Main-menu/loading identity is still incomplete, and the rejected filtered backgrounds remain inactive.

## Findings from the 2026-09-23 icon redo

- The key is read from Windows Credential Manager (`Llavero/PixelLab/api-key`) through `cred-run` into `PIXELLAB_API_KEY`; `tools/pixellab.py` never writes it. It stores the dispatch receipt before polling and resumes the same job after an interruption.
- Tier 1 allows eight concurrent background jobs; the client waits and retries on HTTP 429.
- `/generate-image-v2` at 16×16 with one vanilla item as `style_image` (outline, detail and shading copied; palette not copied) gave the most Minecraft-like drafts. Its 64 outputs are small variations of one composition, so explore by changing the prompt or style image, not by reading more candidates.
- `/generate-with-style-v2` returned pale, low-contrast variants. Adding a composition sketch as a subject reference made drafts noisier. Block faces from PixelLab were saturated and noisy.
- Drafts still carry mottled mid-tones. Promotion = master-palette remap + despeckle + hand fixes on the text grid; weak drafts were redrawn by hand.
- About 400 generations were spent on this redo; 1,260 of 2,000 remained afterwards.
