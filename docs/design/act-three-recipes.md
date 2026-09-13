# Act III: connected workshops

This slice connects five existing static recipes to explicit campaign deliveries. Each consumes one portable prototype. Machines remain installed; gifted prototypes work. The existing `exchange_route` milestone closes the five projects with three paper and one copper. It does not reward another component. Completed historical milestone IDs are preserved.

`signal_exchange`, `nursery_protocol` and `distributed_power` require `lost_workshop`. `measured_logistics` also requires `signal_exchange`; `workshop_hands` also requires `distributed_power`. Optional tutorial inventory checks and installation exercises do not gate these server milestones.

## Acquisition audit, 12 September 2026

Sources: actual pinned JARs in the managed ENTRELUMEN instance and the original pack scripts. This is source evidence, not a survival playtest or timing measurement.

| Prototype | Acquisition and retained infrastructure | Source paths inside the pinned JARs |
|---|---|---|
| Routing matrix | Two AE2 logic processors, one PneumaticCraft PCB, two AA palis crystals and another calibration frame. Requires an energized inscriber, the crystal reconstructor and a pneumatic workshop. The first PCB uses pressure, plastic, UV and acid; the assembly line is a later alternative. The PCB blueprint is an Amadron trade for eight emeralds. | PNC `data/pneumaticcraft/recipe/pressure_chamber/{empty_pcb,capacitor,transistor}.json`, `recipe/amadron/pcb_blueprint.json`; AE2 guide paths below. |
| Propagation core | Two prudentium essence, two wax and another living matrix. The MA route needs an infusion crystal: diamond, four prosperity shards and four inferium; the two prudentium require another eight inferium. A basic centrifuge accepts ordinary honeycomb for wax. No resource bee breeding is required. | `data/mysticalagriculture/recipe/{infusion_crystal,prudentium_essence}.json`; `data/productivebees/recipe/centrifuge/honeycomb.json`. |
| Power regulator | Two basic control circuits, two IE steel plates and another energy coupler. Mekanism's infuser can produce enriched iron and steel dust before smelting; the IE hammer supplies the plate route. No blast furnace is mandatory. | `data/mekanism/recipe/metallurgic_infuser.json`, `processing/iron/enriched.json`, `processing/steel/enriched_iron_to_dust.json`. |
| Inventory sensor | Two crystallized menril chunks, two LaserIO logic chips and another routing matrix. A manual squeezer and drying basin supply menril; raw chips require quartz and furnace processing. | `data/integrateddynamics/recipe/squeezer/base/menril_resin_logs.json`, `recipe/drying_basin/base/crystalized_menril_block.json`; `data/laserio/recipe/logic_chip_raw.json`. |
| Handling core | Two blank router modules, a precision mechanism and another power regulator. Create brass needs heated mixing; deployers use brass hands. Precision assembly runs five cycles and may yield scrap, so teach a small reserve and inspection before scaling. | `data/modularrouters/recipe/blank_module.json`; `data/create/recipe/sequenced_assembly/precision_mechanism.json`, `mixing/brass_ingot.json`. |

AE2 19.2.17 embeds the current acquisition instructions under `assets/ae2/ae2guide/items-blocks-machines/presses.md`, `mysterious_cube.md` and `ae2-mechanics/meteorites.md`. All four processor presses come from the central mysterious cube, broken without Silk Touch. Their IDs are `silicon_press`, `logic_processor_press`, `calculation_processor_press` and `engineering_processor_press` in namespace `ae2`. Do not teach the older scattered chest loot route. `data/ae2/recipe/charger/meteorite_compass.json` charges a vanilla compass; the resulting compass points toward a mysterious cube.

The pack's current resource balance script changes selected Botany Pots, tiers, JAMD and Modular Bees recipes. It leaves the basic MA and Productive Bees acquisition above intact. No cycle through `propagation_core` was found in these inspected routes. Revalidate this conclusion when those base recipes change.

## Verification boundary

Independent full-pack crafting fixtures cover the five new recipes plus the five prior Act II/remainder cases. They must match the actual RecipeManager, produce one expected item in two grid arrangements, reject a missing ingredient and preserve recipe input stacks. The fixture also compares loaded FTB chapters and milestones to authored sources after FTB has finished loading. Results belong in `docs/verification/act-three-runtime.json`; preparing the fixture does not establish a pass.

Server GameTests exercise campaign authority with supplied QA prototypes. They do not establish acquisition time, sufficient generation, safe machine setup, actual exploration, rendered translations or the intended 150–200-hour pacing. Those remain manual acceptance work.
