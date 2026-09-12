# Dedicated server reload and stop — 2026-09-12

An isolated server with 94 curated dependency JARs and companion SHA-256 `41CB40FC7E3715D3AE36084AE77C43D5DBE984CAC53E84C6E11B2A98F9A1B112` started on NeoForge 21.1.249 / Temurin Java 21, with a 4 GB maximum heap. The game client was closed during this check. It listened only on the local test interface.

The server reached its ready state in approximately 65 seconds. The first-hour runtime audit passed for 22 item IDs and six recipe/output pairs, signature `1a80d86a6dce28e7`; the market guard passed with 705 invalid definitions skipped out of 745 inspected. This report predates expanding the diagnostic list to the 54 proposed cross-mod ingredients.

Commands went through the run-specific queue in `tools/runtime.py`. A delivered receipt alone is not execution evidence; the following outcomes were also read from the actual server log:

| Argentina time | Command | Observed outcome |
|---|---|---|
| 16:50:06 | `list` | Zero of six player slots occupied. |
| 16:50:34 | `save-all flush` | All dimensions saved; `Saved the game`. |
| 16:50:48–56 | `reload` | Resource reload completed with no KubeJS errors; a new complete runtime audit passed. |
| 16:51:52–54 | `stop` | All dimensions saved and the owned process exited with code 0. |

The reload paused processing for about eight seconds and emitted a can't-keep-up warning. This is an administrative resource reload, not evidence of gameplay tick performance. Optional compatibility warnings and Immersive Engineering recycling-analysis messages also remain; startup is not described as having a warning-free log.

The control run ID was `6a8c4cf2-32f4-44b9-8906-41a1a27dd278`. The ownership receipt recorded a terminal exit with code 0. No forced termination was used in this run. Separately, three subprocess tests exercise duplicate request handling, conflicting request content, an active-run lock, orderly stop and ambiguous delivery without automatic resend.

This is a startup/reload/shutdown check of an empty local server. External multiplayer, restored-world campaign behavior, hardware performance and full campaign completion remain separate acceptance work.
