# ENTRELUMEN — execution contract

## Outcome
Deliver the approved E2E plan: original Minecraft 1.21.1 NeoForge kitchen sink, six directed acts, 150–200h main campaign, moderate combat, team-specific progression, unrestricted gifts, staged resource farms without EMC, abundant early QoL, EN/ES original content, six-module Ark endgame, source-available own content, approved CurseForge client/server releases and weekly Monday 10:00 Argentina actionable-only issue review.

## Authority and boundaries
User explicitly requested implementation and publication of the complete plan and set goal “terminá el plan E2E”. Local edits, build/testing, dedicated instance, repository creation/public source, CurseForge submission and specified automation are authorized. No unsolicited messages to other people, spending, security changes, credential copying, unrelated deletion or modifications of ATM10 instance/save. Exact-account access via llavero. Reuse downloaded mod JARs only as licensed dependencies; never copy ATM10 quests/story/scripts as own work. Prior goal accounting remains preserved in the app; no invented budget.

## Acceptance
- Reproducible client/server and dependency/version/license/side inventory.
- First-hour playable slice ~25 original bilingual quests validated before mass quest authoring.
- Six acts and six functional Ark modules; meaningful quests at extra-large reference scale. The former ~900 quests / ~36 chapters is superseded below; approximately 150–200 directed campaign milestones with optional learning/mastery branches. No filler to satisfy a counter.
- Team SavedData authoritative; server validation, idempotent deliveries, recoverable phases. FTB Quests mirrors authority. Gifts/materials usable regardless of origin/stage; possession does not complete narrative.
- Party creation copies founder once with personal snapshot retained; joining uses destination without merging; leaving restores personal snapshot; dissolution archives recoverably. No duplicate resources/rewards.
- Full original EN/ES strings, matching keys/placeholders/IDs and rendered readability.
- Beautiful, cohesive original structures, items, dimensions, special content and interfaces. Genuine Minecraft-scale pixel art with restrained shapes/palettes; in-game visual review of representative assets before scaling their family. Technical placeholders, quantized illustrations and palette checks do not satisfy artistic acceptance. See `DESIGN.md`.
- Before designing any new piece, inspect an actual equivalent in Minecraft or a mod and record the exact reference and relevant design decisions. Original item icons use native 16×16 grids. The user rejected the 32×32 Atlas icon and flat green Atlas panel; replacements require fresh in-game review. PixelLab is authorized for artwork generation; API secrets are excluded from pack files, logs and repository content.
- Reference i7-8750H, GTX1070,16GB;1080p/render10/simulation6/no shaders/heap<=8GB. Preloaded routes mean>=60FPS,1%low>=40;20TPS and p95MSPT<50. Two-hour soak with no sustained leak. New worldgen recorded separately. Integrated LAN single memory budget; dedicated tested separately; six external clients.
- Manual new-world main campaign completion; real pacing playtests; team lifecycle/reconnect; clean install, beta upgrade and full backup restoration. Synthetic tests are not manual playthrough.
- Publish own dependency first; official CurseForge App export, unmodified manifest; approved client+server, EN-first bilingual page, real screenshots, credits/license. Verify public install and weekly review.

## Sequencing and ownership
Root integrates. Bounded workers never redelegate. Separate companion, catalog and first-hour content owners. Integrate and verify slice before mass campaign production. Root owns root setup and coordination documents. Scaffolds, counts and draft artifacts do not prove completion. Publication remains gated by its actual verification and provider moderation, not routine reapproval.


## Scope update — extra-large parity (2026-09-12)

The user now requires mod and quest scale equal to or greater than ATM10, FTB Evolution and Craftoria, without bloat. **900 quests is no longer a cap or final target.** Provisional planning: **600 distinct main mod projects/JARs and 5,000–5,500 original unique quests**. Report libraries, QoL and content separately; resource packs, embedded-library duplication, translations, tasks and rewards cannot inflate the relevant counts. Preserve six acts and the 150–200h first-ending objective; most quests remain optional.

Final acceptance compares frozen editions using consistent units and requires parity with the largest reference. See [scale-reference.md](../design/scale-reference.md): ATM10 8.1 has 4,790 structurally counted unique quests locally; Craftoria 1.36.0 exports 560 CF references, not necessarily all mods. FTB Evolution 1.43.1's official export was measured on 2026-09-23: 523 direct mod JARs and 2,072 unique quests in 40 chapters. Export counts do not establish runtime loading; Craftoria's complete loaded quest total remains unmeasured. Provisional targets do not prove parity: close the remaining comparative measurement before final scope acceptance and raise targets if necessary.

The 8GB heap / 16GB system performance targets remain required and unproven at this scale. Grow useful families, benchmark and remove redundancy; do not quietly substitute a smaller pack or filler quests. If measured limits prevent both scale and performance after optimization, present that material tradeoff to the user. This measurement task does not itself change catalog or mass quest content.
