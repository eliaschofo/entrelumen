# Act VI campaign and first ending

Continue the full [E2E contract](../spec.md). The previous turn was progress: commit `371e740` resolves the 85 addon loot errors, preserves 295 native tables and passes full-pack reload/drop checks plus CI 35806524971. Only unrelated `docs/verification/weekly-issues.json` is dirty at admission. The full extra-large pack, six complete module experiences, art, survival duration, performance and publication remain required; this implementation does not declare them complete.

## Campaign route

Replace the six provisional vanilla module deliveries with these cross-mod component costs. Keep stable project/item IDs and the existing one-module reward, never consume installed machines. Every project requires `world_network`; exploration additionally requires `end_arrival`. Already completed projects and existing commissioned steps remain credited. Gifts and static recipes remain unrestricted.

| Project / rewarded block | Consumed materials |
| --- | --- |
| `engineering_module` | 2 `calibration_frame`, 2 `energy_coupler`, 1 `ark_bus`, 2 `mekanism:alloy_atomic` |
| `arcane_module` | 2 `spectral_lens`, 2 `containment_seal`, 2 `occultism:iesnium_ingot` |
| `nature_module` | 1 `renewal_engine`, 2 `ecosystem_capsule`, 2 `living_matrix` |
| `exploration_module` | 1 `horizon_chart`, 1 `spectral_lens`, 2 `twilightforest:steeleaf_ingot`, 2 `aether:zanite_gemstone` |
| `logistics_module` | 2 `routing_matrix`, 2 `handling_core`, 1 `ark_bus` |
| `habitation_module` | 1 `habitation_contract`, 2 `ration_bundle`, 2 `living_matrix` |

Unqualified material IDs above use `entrelumen:`. These are initial pacing values, not validated 150–200-hour costs. The six commissioning batches and their persisted 0–6 counter remain unchanged; construction deliveries and commissioning supplies are different costs and must be taught separately.

The existing six static recipes `entrelumen:integration/ark_{engineering,arcana,nature,exploration,logistics,habitation}` already supply repeatable replacement modules. Preserve their IDs and exact inputs; the table above deliberately matches them. All fit a 3×3 grid. They permit rebuilding and free gifts without new campaign rewards or administrative recovery. Crafting a module still does not complete its project. Do not add duplicate recipes. Admission inspection superseded the initially proposed component-only table and duplicate replacement recipes before integration, retaining the existing iesnium, steeleaf and zanite routes rather than replacing them with easier placeholder inputs.

Record `end_arrival` on actual server-observed entry/login/respawn in `minecraft:the_end`, with the same current-team semantics as the three existing journeys. Early visits count; joining a team does not merge prior personal travel. Possession of gifted End materials cannot create this observation.

Expose read-only milestones `ark_calibrated`, `ark_contained`, `ark_renewed`, `ark_routed`, `ark_provisioned`, `ark_charted` for the six credited commissioning steps. Derive them from authoritative campaign state rather than writing duplicate phase counters or granting them through FTB. Reserve these IDs against deliverable-project definitions. Phase mirrors require an active act-VI campaign and its six completed module projects. Their quest dependencies are: all six module projects for the first; the immediately preceding phase for each following one.

The terminal milestone is `last_horizon`. After all six steps, an explicit crouched empty-hand interaction with the controller activates the Ark only when the current campaign is active, in act VI, has `world_network`, all six module projects and `end_arrival`, and all six physical modules are still present in the existing bounded volume. Validate campaign identity and reach before mutation. Store `last_horizon` in the existing completed set; mark SavedData dirty. Replay cannot consume supplies, advance phases or issue rewards. Final activation consumes nothing beyond the completed batches and leaves all blocks intact. Readable EN/ES ending text remains available on subsequent controller inspection and in the terminal FTB task; no new visual asset or custom screen is needed for this route. This is the first narrative ending, not acceptance of the later module benefits or mastery systems.

## Editorial route

Add one focused original EN/ES chapter, `last_horizon`, following the existing FTB visual conventions, with up to 30 meaningful quests. Preserve all 128 prior quests, IDs, text and layout. Teach the six exact deliveries, controller recipe/placement, the six separate commissioning batches, final expedition and explicit activation. Campaign tasks follow server dependencies; no possession or optional checkmark task gates authority. Optional postgame invitations must not claim unimplemented benefits or unlocks. Native End guidance must be sourced; do not require killing another team's dragon or claim automatic detection of an installation.

## Ownership and acceptance

Root integrates, owns this spec, recipe/acquisition notes, `content/integration-design.json`, runtime source audit generation and deployment evidence. Companion worker owns `companion/` only. Quest worker owns the new chapter, `tools/generate_quests.py`, `tools/test_generate_quests.py`, generated FTB files/task map and `content/INTEGRATION.md`; no old source chapters. No redelegation. Keep unrelated work unchanged.

Verify module costs/reward idempotency, wrong-team/stale/out-of-reach/missing-module activation rejection, gift behavior, read-only FTB phase/final mirrors, End observations, partial-batch restart, final replay and disk persistence. Full-pack loading and relevant real client interaction remain integration checks; tests and supplied QA materials do not prove survival pacing, final visual quality or release readiness.
