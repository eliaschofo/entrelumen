# Recoverable Ark batches

The controller now accepts partial supplies for one module at a time. Four narrative phases contain six module steps; the existing `arkPhase` counter remains 0–6 so earlier saves keep their credited steps.

| Step | Module | Phase | Supplies consumed |
| --- | --- | --- | --- |
| 1 | Engineering | Calibration | 4 calibration frames + 2 power regulators |
| 2 | Arcane | Stabilization | 2 containment seals |
| 3 | Nature | Stabilization | 2 ecosystem capsules |
| 4 | Logistics | Provisioning | 2 routing matrices |
| 5 | Habitation | Provisioning | 8 ration bundles |
| 6 | Exploration | Chart | 1 horizon chart |

Ordinary use reports the current module, stored amounts and missing materials. Crouched use with an empty main hand explicitly deposits only needed supplies from ordinary inventory and offhand. Surplus and unrelated items remain. Each click processes at most one module. Materials from gifts work normally; the receiving campaign must still reach act VI and complete its module projects. There is no reward payout for commissioning, no remote inventory extraction and no background scan or offline decay.

All six physical modules must be within the existing 7×4×7 controller volume at delivery time: three blocks horizontally, one below and two above. Missing modules pause deliveries without changing blocks or stored contributions. The bounded scan checks loaded chunks before reading blocks. Controller placement and ownership are not persisted as a permanent installation; that further endgame experience remains open.

`ArkActions` validates campaign identity, expected step, player reach, controller and structure before delegating to `ArkCommissioning`. The domain caps each material at its remaining cost and calls the inventory consumer before changing the ledger. A completed step clears the current ledger and increments the existing counter. A request for that old step is then rejected. `SavedData` is marked dirty after every accepted partial or complete batch. This follows normal Minecraft saving; it is not a separate crash-atomic inventory/database journal.

Schema 2 saves `arkDeposits`. Version 1 keeps every credited `arkPhase` exactly and starts with no partial deposits. Loading bounds entries to the current module's known costs, ignores unrelated/negative values and rejects future schemas. Founder snapshots copy the ledger independently; joining and leaving continue to use existing team/personal campaign rules.

The crouch gesture follows the [NeoForge 1.21.1 interaction pipeline](https://docs.neoforged.net/docs/1.21.1/items/interactionpipeline/). An event hook applies only to this controller with empty main hand and preserves cancellation or denied block use. It allows an occupied offhand without overriding other blocks or inventing a new global key binding. The exact `PlayerInteractEvent.RightClickBlock` source and `ServerPlayerGameMode.java.patch` in NeoForge 21.1.249 were inspected before implementation.

## Acquisition references

The seven integration components have authored recipes. Earlier act checks already exercised calibration frames, regulators, routing matrices and ration bundles. The following later-act paths were source-audited in the pinned mod JARs:

- `horizon_chart`: Aether 1.5.10 `data/aether/loot_table/blocks/ambrosium_ore.json`; Twilight Forest 4.8.3345 `recipe/material/raw_ironwood.json`, `recipe/material/smelted_ironwood_ingot.json` and `loot_table/blocks/liveroot_block.json`.
- `ecosystem_capsule`: Bumblezone 7.15.3 `data/the_bumblezone/loot_table/blocks/pile_of_pollen.json`; Productive Bees 13.13.5 `data/productivebees/recipe/centrifuge/honeycomb.json` provides a basic wax route from vanilla honeycomb.
- `containment_seal`: EvilCraft 1.2.94 `data/evilcraft/loot_table/blocks/dark_ore.json`; Malum 1.8.2 `data/malum/recipe/spirit_infusion/soul_stained_steel_ingot.json`, `recipe/soulstone_from_raw_smelting.json` and `recipe/spirit_altar.json`.

Those references show direct ingredient routes without an identified cycle. Spirit acquisition, dimension access and real survival pacing have not been validated by this source audit.

## Remaining endgame work

This slice implements recoverable commissioning transactions. It does not establish final exploration evidence, the ending scene, mastery unlocks, a permanent installation record, final module models or six complete module experiences. Later act projects, functional benefits, client presentation and the full survival route remain part of the active E2E goal. Test supplies and automated server checks do not prove a 150–200 hour campaign or performance acceptance.
