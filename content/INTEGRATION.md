# First-hour narrative integration

Source: `first_hour.json`. Generate with `python tools/generate_quests.py`; verify without writes with `--check`. Stable IDs use SHA-256 of namespaced semantic keys, masked to positive signed 64-bit values. Freeze keys after release. Generated FTB structure is original; third-party files were inspected solely for serialization conventions.

Campaign tasks require companion registration `entrelumen:campaign` with string `milestone`, one of `atlas_awakened`, `travellers_table`, `lens_assembled`, `field_survey`, `first_signal`. Generated `campaign_task_ids.json` is the bridge map. Never substitute checkmarks, inventory detection or command rewards for these tasks. Server companion state must set AND clear mirrored task progress when team membership changes. FTB task state is not campaign authority.

Learning item tasks detect possession without consumption. They do not advance companion campaign. Optional checkmarks acknowledge practice/readings only. All FTB rewards are intentionally empty: material deliveries and any rewards belong to companion transactions so team transitions cannot clone resources. Advancing the project and retaining useful infrastructure is the first-hour reward; any later tangible reward must be granted by the server transaction exactly once.

Required companion item IDs: `entrelumen:atlas`, `entrelumen:raw_lens`, `entrelumen:survey_notes`, `entrelumen:signal_core`. Required Farmer's Delight IDs: `flint_knife`, `cutting_board`; validate against selected 1.21.1 JAR before pack release. Other item references are vanilla.

Runtime gate: custom task registration/loading, bilingual text layout, item registry lookup, two-team progress isolation, leaving/rejoining, restarting between deliveries, and first-hour pacing have NOT been validated by this generator. Do not distribute a playable claim until integrated tests pass. No 900-quest generation before first-hour playtest.

Current player text uses `/entrelumen status` and `/entrelumen deliver <milestone>`. Atlas use only reports status. The current field survey validates a consumed delivery, not location or travel; the quest explicitly labels walking as suggested practice. The current signal records a project without an in-world effect. These are prototype limitations, not final campaign acceptance. Root integrator owns final recipe costs, UI and bridge acceptance.
