# ENTRELUMEN

Approved acceptance: docs/delivery/spec.md. Root integrates; bounded workers own assigned paths only and never redelegate. Preserve concurrent work. No copied ATM10 narrative, quests or scripts. Use licensed dependencies and public APIs. Build or quest counts do not prove gameplay, performance or publication. All original player-facing text needs en_us and es_es. Windows tools: C:/Users/elias/.codex/bin/pwsh.exe, tty:false; processes hidden unless human interaction is required.

## Design hook — reference before drawing

Before designing or redesigning any item, block, interface, structure or dimension, find and visually inspect an equivalent that actually exists in Minecraft or a compatible mod. Record the exact reference path or source and the relevant resolution, proportions, material treatment or interaction pattern in the design notes. Use it to inform original work; do not copy another pack's assets or claim a reference was inspected without seeing it. Item icons use a native 16×16 editable grid. Redraw for that grid instead of shrinking a more detailed illustration. Compare at native inventory scale and inside Minecraft before accepting the result. These are Elias's explicit instructions of 2026-09-12; see DESIGN.md.

## Local generated storage

On Elias's Windows workstation, keep heavyweight generated servers, worlds, backups, JDKs, dependency downloads and build output off C:. Check C: headroom before build/runtime batches and clean or relocate only owned transients while their processes are stopped. Preserve useful state and unrelated files. This records Elias's explicit 2026-09-23 request to avoid filling C: and keep janitorial work current.

- **Hot state lives on the NVMe `E:/Elias/Codex/Entrelumen-ssd`** (Kingston A1000) since 2026-09-24: the owned QA server `server-slice` with its world, the JDK in `runtime`, and the main copy's `companion-build` and `companion-run`. Run servers, builds and GameTests there.
- Their old paths `G:/Elias/Codex/Entrelumen-work/{server-slice,runtime,companion-build,companion-run}` are junctions to E:, so scripts and receipts that name G: still work. The repo junctions `companion/build` and `companion/run` point straight at E:.
- The G: originals were renamed to `<name>.__old-20260924` after a verified copy; they are stale and only kept until Elias approves deleting them. Receipt: `E:/Elias/Codex/Entrelumen-ssd/move-receipt-20260924.json`.
- Cold state stays on `G:/Elias/Codex/Entrelumen-work`, a shared 5400 rpm HDD: earlier batch receipt folders and world archives, `catalog-downloads` (junction `catalog/downloads`) and `neoform-cache`. New batch folders may go on either disk (the 24 September integration's are in `E:/Elias/Codex/Entrelumen-ssd/head-ab3796a-20260924`). Do not put a server, world or build that runs on G: again; its contention caused 60 s watchdog stops and boots of up to 25 minutes.
- Other workers keep their own worktrees and build folders beside it on E: (for example `wt-altars2`, `altars2-build`). The Gradle user home stays in `C:/Users/elias/.gradle`.

Build with `JAVA_HOME=E:/Elias/Codex/Entrelumen-ssd/runtime/jdk-21.0.12.1+1` and `gradlew --offline --no-daemon`, so no daemon outlives the batch.
The retired `*.__old-20260924` copies on G: were deleted on 2026-09-24 with Elias's approval after the E: copies were verified.
