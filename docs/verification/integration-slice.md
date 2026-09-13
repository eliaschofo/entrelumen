# Integration slice — 2026-09-12

This is prototype evidence, not campaign, pacing or performance acceptance.

## Historical reproducible build

GitHub Actions run [34715952313](https://github.com/eliaschofo/entrelumen/actions/runs/34715952313) passed on Ubuntu 24.04 / Java 21 for commit `83d64ff4023320dfaa44bcc2f108b538ea25a1a7`: source generation, translations, artwork, runtime command queue tests, companion build and all 11 then-required Minecraft GameTests.

The subsequent local integrated build passed 16 JUnit tests and all 12 Minecraft GameTests, including a complete act-I delivery sequence through the same authoritative route used by the Atlas UI. The test supplies exact materials, rejects an early final delivery, completes all five projects, consumes the expected inventory, retains one portable Atlas, rejects replay, advances to act II and prevents a second advancement. This is a functional test with supplied materials, not a survival playthrough or an estimate of duration.

Built companion SHA-256: `cac1fe9e092206f825ae61c8e142f017b66a02af84a8b55f2383900b3c3538be`. The build also includes the 16 registered component items and original icons, bounded project IDs, capture integrity/async-close corrections and the Atlas render-order fix. Seven Python capture-integrity regression tests pass; they do not establish real FPS or TPS.

## Historical client observations

On the previous companion build `3f9cb6fe0e4be40c76d40775c3247c52a3da16d73293c31e54403e27e815d078`, the isolated `ENTRELUMEN-QA-functional` world loaded. F8 opened the first chapter at its introduction. Spanish descriptions were readable at 1028×803 and 1920×1032 window sizes. Clicking the pending campaign task's **Abrir Atlas** button opened the authoritative panel without requiring its portable item. Missing materials and prior projects were displayed and delivery/advancement buttons were disabled correctly.

The observation exposed blurred Atlas headings while list rows and buttons remained sharp. [Actual screenshot](screenshots/atlas-before-blur-fix-es.png). The fix moves Atlas painting after vanilla's background blur pass; visual confirmation of the new JAR remains pending.

The client saved every dimension at 17:22:17 Argentina and exited normally at 17:24:01. Its QA world and reviewed generated configuration files were backed up locally before update. The new JAR and 81 authored pack files were synchronized only after the client exited. The installed dependency set then expanded to 130 JARs plus the companion. That was the status at this historical checkpoint; the newer runtime evidence below supersedes it for the explicitly audited routes.

A local, unshipped datapack offers opt-in act-I test supplies through `/function entrelumen_qa:first_act`; a player tag prevents an accidental repeated supply call. It does not complete projects or change survival rules. It is present only in the isolated QA world and is excluded from the pack/repository.

## Weekly issue review

The thread heartbeat `entrelumen-revisi-n-semanal-de-issues` was created and read back as ACTIVE, linked to this task, on Mondays at 10:00 in the host's America/Buenos_Aires timezone. It watches the public repository, stays quiet on unchanged/non-actionable state and cannot post, close issues or update mods under its saved instructions. The first scheduled execution is still pending; creation is not evidence of a completed run.

## Current integration checkpoint — 2026-09-12

The locked catalog now contains **140 client dependencies / 110 server dependencies**, excluding the companion (110 both-side and30 client-only entries). This supersedes the130-dependency checkpoint above.

Dedicated owned run `6f1c3c40-bea2-4d1a-8c60-758c8f3eca8d` ended with `exitCode:0`, confirmed in `work/server-slice/owned-process.json`; latest.log records shutdown and chunk saves at18:35. This proves the owned process exited normally, not that its entire log was clean.

- MoreMachine runtime audits passed before and after reload: item/fluid/chemical replication maps were all0; forbidden recipes absent and the intended oxidizing factories retained. The post-reload audit at18:32:35 has run `1789248755203`.
- Five Malum compatibility recipes passed the current runtime Codec inspection: run `1789248688572`, token `malum-five-20260912-1832`, signature `f5cc02fad2f036e3f9a7911828b7e15f3a42f42afc960a38c2bb70b588ba3f8e`, summary count5/failures0. Evidence appears in latest.log and logs/kubejs/server.log. These are the four Occultism chalk repairs and Create grim-talc milling; older errors remain historical, not the current five-recipe result.
- Resource audit reported1421 active outputs and no missing outputs. Its own status is `loaded-output-check-only`: it does **not** establish ingredient correctness, NBT preservation, stage gating or survival balance.

Remaining warnings and the single Windows SavedData AccessDenied during a backup prevent any blanket claim of a clean server. A later write was observed; causal diagnosis and limits are in [backup-consistency](../design/backup-consistency.md). Optional integration warnings require individual interpretation. Resolving the five Malum recipes does not resolve unrelated warnings.

### Current client acceptance boundary

The new companion client build with hash prefix `5AAF` includes a compiled four-widget title-identification hook; runtime UI verification is paused and pending. Prior observations establish that custom GUI button textures work and Drippy3.1.5 displays the Mojang resource-loading overlay with actual progress. Icon placement is not accepted; early NeoForge loading is not covered. The tested backgrounds were rejected and have no final visual approval. No screenshot of an earlier build validates the new hook.

The current singleplayer checkpoint includes the grave interaction and **1/5 act-I projects**, not a full playthrough. The full act-I GameTest described above supplied materials and remains test evidence only. No full six-act campaign,150–200-hour duration,900quests, performance targets, cooperative progression matrix or public-install acceptance is established by this slice.

## Superseding client checkpoint — 19:20 Argentina

Client companion `90A1A21FF9A6F0FE0A6DD0460789C58B97AA0AA675A3ACBF60CAD4A86500DAB9` loaded with 140 dependencies and 125 authored pack files. The real main screen showed the native Atlas image and all four relocated optional widgets. It also exposed footer text overlapping those icons and the last main action. The source now uses 22px row spacing and a separate icon row beneath the right-hand image, with 32px reserved for the footer; the revised eight layouts parse with the pinned FancyMenu parser. Their final placement still needs a new client observation. The world-selection screen also showed an unwanted blurred background; an explicit pine background was added to that screen, with runtime verification pending.

The player respawned in `ENTRELUMEN-QA-functional`. A temporary Resistance V effect was used only in this isolated QA world. Corail's `/tbrestoreinventory` recovered the real grave contents; the game reported that the grave disappeared. No inventory snapshot was duplicated. The retained campaign was still at 1/5 projects.

Through the Atlas UI, the remaining projects completed at 19:13:50, 19:14:06, 19:14:30 and 19:15:07. The Atlas transitioned from act I 5/5 to act II 0/1 at 19:15:15. This also verifies that authoritative requests still execute while the Atlas pauses integrated singleplayer world ticks. The signal core appeared in the inventory. All deliveries used earlier QA supplies; this does not validate gathering, pacing or the placeholder act-II costs. Physical station crafting/placement remains pending.

Runtime audit run `1789251046924`, signature `580f71c66fc3777f`, passed for 99 item IDs and 29 loaded recipe/output pairs. The market audit also passed its selected compatibility checks. These are registry/output checks, not recipe execution or performance evidence. The user saved the world; logs record all dimensions saved at 19:19:54 and normal client shutdown at 19:20:05.

The user rejected the 32×32 Atlas icon as insufficiently Minecraft-like and explicitly rejected the green list interface. A new 16×16 item candidate and an original book GUI were produced after inspecting actual Minecraft book assets. The project now requires real Minecraft/mod references before designing any new piece. PixelLab was subsequently requested for artwork; a new provider candidate is being evaluated. None of these changes constitutes aesthetic acceptance.

The rebuilt companion `9C91246D1EB21A4C05876D4E4361FD971F1FE481509DF2C6E34BF1FE7B80E118` includes the book GUI, 16px item candidates, wooden station sound and an offhand interaction correction. All 13 required GameTests passed at 19:23:52; 16 JUnit tests passed. The offhand test now follows the actual MAIN_HAND-then-OFF_HAND server use route, preventing an empty main hand from opening the Atlas before an offhand compass can bind. This build is not yet installed or visually verified.
