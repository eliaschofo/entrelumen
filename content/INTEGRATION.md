# Narrative integration: Acts I, II and III

Sources: `first_hour.json` (26 existing quests, unchanged) and `act_two.json` (22 new quests). Generate with `python tools/generate_quests.py`; verify without writes with `--check`. Stable IDs use SHA-256 of namespaced semantic keys, masked to positive signed 64-bit values. Freeze keys after release. Generated FTB structure is original; third-party files were inspected solely for serialization conventions.

Campaign tasks require companion registration `entrelumen:campaign` with string `milestone`, Act I: `atlas_awakened`, `travellers_table`, `lens_assembled`, `field_survey`, `first_signal`. Act II: `precision_bench`, `crystal_grid`, `living_workshop`, `travelling_pantry`, `lost_workshop`. Generated `campaign_task_ids.json` is the bridge map. Never substitute checkmarks, inventory detection or command rewards for these tasks. Server companion state must set AND clear mirrored task progress when team membership changes. FTB task state is not campaign authority.

Learning item tasks detect possession without consumption. They do not advance companion campaign. Optional checkmarks acknowledge practice/readings only. All FTB rewards are intentionally empty: material deliveries and any rewards belong to companion transactions so team transitions cannot clone resources. Advancing the project and retaining useful infrastructure is the first-hour reward; any later tangible reward must be granted by the server transaction exactly once.

Required companion item IDs: `entrelumen:atlas`, `entrelumen:raw_lens`, `entrelumen:survey_notes`, `entrelumen:signal_core`. Required Farmer's Delight IDs: `flint_knife`, `cutting_board`; validate against selected 1.21.1 JAR before pack release. Other item references are vanilla.

Runtime gate: custom task registration/loading, bilingual text layout, item registry lookup, two-team progress isolation, leaving/rejoining, restarting between deliveries, and first-hour pacing have NOT been validated by this generator. Do not distribute a playable claim until integrated tests pass. No 900-quest generation before first-hour playtest.

Current UI contract: open the Atlas by clicking an authoritative FTB campaign task or using the portable Atlas. The task is a mirror of server campaign state, not a delivery command. The authoritative project consumes its listed items only after explicit delivery. Commands are diagnostic fallbacks; quests teach the UI. First-hour survival/UI acceptance is recorded separately by the integrator, not inferred from this generator.

## Act II editorial and server contract

The chapter has22 original EN/ES quests:13 non-consuming item tutorials,4 optional self-reported exercises/readings and5 server milestone tasks. Voices follow story_bible.md: Ivo teaches measurement, Mara preserves provisioning, the Atlas reconstructs a shared workshop from incomplete records. No FTB rewards; machinery stays installed. Possession and exercise completion never advance the campaign.

The first four projects require first_signal; living_workshop also requires precision_bench. Closure requires all four. Milestone nodes deliberately do not depend on tutorial inventory/checkmark tasks: gifts can be delivered without requiring the receiver to recreate every machine. Tutorials remain independent learning routes. A calibration frame is consumed for precision_bench and a second is an ingredient in the living matrix; the text teaches this explicitly. The closing archive consumes3paper+1copper. Labels for the first two projects match the integration recipes (A Measure of Iron / Una medida de hierro; A Crystal Connection / Una conexión de cristal).

Layout uses four spaced workshop branches and a shared entry/archive, with larger hexagonal server tasks and optional exercises clearly described as self-reported. Validation checks finite geometry and pairwise clearance within each chapter, local reading direction, global cycles and cross-chapter references. Those checks do not constitute rendered UI approval.

## Pinned recipe/reference evidence

Sources were read from hash-verified catalog/local-paths.json JARs; no quests/configurations copied. Every external item/icon reference has a native item model in its pinned JAR.

- Create6.0.10: crafting/kinetics/water_wheel, mechanical_press, goggles; pressing/iron_ingot. Rotation and electrical energy are distinguished; Ponder is the implementation guide.
- IE12.4.2-194: crafting/plate_copper_hammering and crafting/wire_copper. Copper wire is distinguished from electrical wire coils; no metal press required for the hand route.
- AA1.3.26: coal_generator, iron_casing, atomic_reconstructor, laser/crystalize_restonia_crystal (40energy, ordinary Restonia).
- Ars5.13.1: novice_spell_book, magebloom_fiber (one bloom→four fibers), magebloom_crop (seed+four gems, Source cost0). Acquisition cross-check in docs/design/act-two-recipes.md includes the diamond/gold/sourcestone apparatus cost and imbuement routes. Text does not confuse a zero recipe Source cost with free infrastructure.
- FD1.3.3 and Aquaculture2.7.21: cooking_pot and cooked_fish_fillet_from_campfire (600ticks). Pantry explicitly uses two vegetable soups plus two cooked fillets; individual food reserves are separate from project consumption.

## Verification

`python tools/generate_quests.py --check` checks both chapters, bilingual keys/placeholders, global IDs/DAG, milestone mapping and file drift. `python tools/test_generate_quests.py` freezes the existing chapter/quest/task IDs and exercises cross-chapter cycle/missing-reference rejection, duplicate keys, placeholder mismatch and authoritative dependency/reward rules. There are48quests total because the existing first-hour source already contains26, including survey_station; none of those IDs or source entries was replaced.

Root owns loaded recipe, campaign GameTests, client UI and survival/pacing acceptance. This deliverable does not claim a machine demonstration, full act-II playthrough, first-hour signoff,900quests or publication.


## Act III — Routes of Exchange

`act_three.json` adds one chapter with 27 original EN/ES quests: 15 inventory tutorials, six optional observations/readings and six authoritative campaign tasks. Total: 75 quests; all 26 Act I and 22 Act II quest/task IDs are preserved. `generate_quests.py` now loads all three sources. Prior two-chapter verification statements above describe the predecessor slice.

Every prototype milestone depends directly on `crafts_archive` (`lost_workshop`). `measured_logistics` additionally depends on `exchange_signal` (`signal_exchange`); `workshop_hands` on `exchange_power` (`distributed_power`). Closure `exchange_archive` mirrors the preserved `exchange_route` milestone and depends on all five projects. No tutorial or checkmark is an ancestor of an authoritative milestone in any of the three chapters. Gifts remain valid. Only explicit Atlas delivery consumes one corresponding prototype; closure consumes three paper and one copper ingot. No FTB rewards. Tutorials distinguish possession from operation, and optional trials ask players to observe a concrete result without claiming a detector.

Source evidence: pinned JAR item models confirmed for all eleven external ingredient tutorial references. Native recipes inspected: AE2 inscriber/logic_processor; AA laser/crystalize_palis_crystal (lapis, 40 energy); PNC printed_circuit_board (unassembled PCB, two capacitors, two transistors); MA prudentium_essence and infusion_crystal; Mekanism control_circuit/basic (20 redstone infusion); IE crafting/plate_steel_hammering; ID squeezer/base/menril_resin_logs, drying_basin/base/crystalized_menril_block and crafting/crystalized_menril_chunk; LaserIO logic_chip_raw and logic_chip; Modular Routers blank_module; Create sequenced_assembly/precision_mechanism (five loops and probabilistic output). Parent acquisition audit supplies PCB starting chain, ordinary honeycomb centrifuging and Mekanism steel chain. AE2 guide presses.md and mysterious_cube.md establish four presses from a meteorite mysterious cube broken without Silk Touch; no obsolete chest-loot instruction.

Focused verification freezes the full Act II ID sequence in addition to Act I, checks Act III authority ancestry, task-map correspondence, non-consumption/rewards, bilingual acquisition coverage and encoding, and exercises Act III placeholder mismatch rejection. Generator checks global DAG, geometry, EN/ES parity and drift across all three chapters. These are source checks, not rendered EN/ES, survival pacing or a working-machine demonstration; root owns loaded registry/recipe/campaign integration and records those results separately.


Act I gift-route correction: only the five campaign nodes' dependencies were aligned with `projects.json`: atlas has none; table and lens require atlas; survey requires atlas and lens; signal requires table, lens and survey. Tutorial branches, IDs, text, positions and rewards remain unchanged. This removes extra UI requirements for gifted deliveries without changing server authority or resetting completed progress. Tests compare all three chapters against server prerequisites and traverse complete campaign ancestry without a chapter boundary exception.


## Act IV — Voices of the Atlas

`act_four.json` adds 29 original bilingual quests: 17 non-consuming item tutorials, four optional readings and eight authoritative campaign mirrors. Total: 104 quests and 24 milestones. The 75 prior quest/task IDs, chapter geometry, task behavior and EN/ES strings are frozen by full chapter-output and locale fingerprints. No prior source chapters are edited.

Five deliveries mirror the server project prerequisites exactly: spectral_archive follows exchange_route; horizon_survey also requires aether_arrival and twilight_arrival; pollinator_treaty also requires bumblezone_arrival; sealed_memory also requires spectral_archive. The preserved atlas_voices closes after all four. Each prototype delivery consumes one item; closure consumes three paper and one copper ingot. All FTB rewards remain empty. The lens lesson explicitly budgets two lenses because the archive delivery and containment recipe each consume one.

The three arrival tasks have no prerequisites, item tasks or rewards. They mirror server-observed travel/login/respawn in any act, so early visits count. One member's arrival serves the current team campaign. Joining does not merge historical journeys. Gifts and optional tutorial clicks cannot create observations. Tests exempt only the exact three observer IDs from project lookup; every other campaign task must match a real project and its direct dependencies. No tutorial gates a deliverable.

The chapter reuses the existing restrained FTB conventions: four spaced material branches, square possession nodes, optional circular readings, larger hexagonal campaign tasks and a shared closing archive. Geometry and reading direction are statically checked; rendered layout acceptance remains with the integrator.

Acquisition evidence supplied by the bounded pinned-JAR audit covers Occultism's four diamonds through Spirit Fire into four attuned gems, then a 2×2 crystal; Malum's crude scythe without steel, native spirit sources and Spirit Altar steel inputs; and dimension entry/return behavior. The text includes ordinary honeycomb centrifuging for wax, native guides for breeding and processing, ambrosium ore, raw ironwood ingredients, pollen piles without Silk Touch, and dark-gem ore. No invented ore heights, drop guarantees or machine thresholds. Exact integration inputs follow content/integration-design.json; delivery authority follows the newer Act IV spec and projects.json, superseding that design file's old non-consuming proposal.

Verification: `python tools/test_generate_quests.py` passes 17 focused contracts; `python tools/generate_quests.py --check` confirms four chapters, 104 quests, global IDs/DAG, geometry, EN/ES parity and generated-file drift. These results do not establish FTB runtime loading, survival acquisition, portal travel, rendered translation quality or campaign event integration; those remain separate integrator checks.
