# Act II recipes

Source implementation for the four Act II prototypes; not acceptance of the full 22-project campaign. Each static shapeless recipe yields one prototype. After `first_signal`, explicit authoritative server delivery consumes one prototype per project. Machines stay installed. Gifts remain usable, and neither crafting nor possession advances the story. Tutorial installation exercises are self-reported; no machine demonstration is validated. This supersedes the older non-consuming Act II proposal; later-act narrative proposals remain outside this slice. The closing `lost_workshop` archive requires all four projects and consumes 3 paper + 1 copper ingot (campaign owner).

| Project | Ingredients |
|---|---|
| precision_bench | 2 create:iron_sheet + 2 immersiveengineering:wire_copper + 1 entrelumen:raw_lens |
| crystal_grid | 2 actuallyadditions:restonia_crystal + 1 actuallyadditions:iron_casing + 2 create:andesite_alloy |
| living_workshop | 2 ars_nouveau:magebloom_fiber + 2 ars_nouveau:source_gem + 1 entrelumen:calibration_frame |
| travelling_pantry | 2 farmersdelight:vegetable_soup + 2 aquaculture:fish_fillet_cooked (unchanged) |

## Pinned acquisition evidence

Read directly from JAR paths in `catalog/local-paths.json`; exact item models, source recipe paths and SHA-256 hashes are recorded in `content/integration-design.json:itemEvidence`. Recipe IDs below resolve to `data/<namespace>/recipe/<path>.json` inside those JARs.

- Create 6.0.10: `create:pressing/iron_ingot` presses an iron ingot tag into `create:iron_sheet`. `create:crafting/materials/andesite_alloy` combines 2 andesite and 2 iron nuggets into one alloy; brass is not needed.
- Immersive Engineering 12.4.2-194: `immersiveengineering:crafting/wire_copper` combines a copper plate tag with a wirecutter into one wire. `immersiveengineering:crafting/wirecutter` uses iron and wooden rods; a powered wire-production line is not an acceptance requirement.
- Actually Additions 1.3.26: `actuallyadditions:laser/crystalize_restonia_crystal` converts redstone dust into restonia at declared energy 40. `actuallyadditions:iron_casing` uses 4 iron ingots, 4 wooden rods and black quartz. `actuallyadditions:atomic_reconstructor` uses iron, redstone and that casing; no steel component is needed by this recipe.
- Ars Nouveau 5.13.1: `ars_nouveau:magebloom_fiber` converts one magebloom into 4 fiber. `ars_nouveau:magebloom_crop` uses a seed and 4 source gems in the enchanting apparatus, sourceCost 0. `ars_nouveau:imbuement_amethyst` and `ars_nouveau:imbuement_lapis` output a source gem, with declared source 500. The apparatus recipe includes a diamond, gold and sourcestone; the imbuement chamber uses archwood planks and gold. This is an acquisition cost, not a claim of first-hour accessibility or verified crop harvesting. Nature's Aura forest-altar materials remain for later integrations.
- Farmer's Delight 1.3.3: `farmersdelight:cooking/vegetable_soup` uses carrot, potato, beetroot and leafy-green tags.
- Aquaculture 2.7.21: `aquaculture:cooked_fish_fillet` smelts raw fillet; `_from_smoking` and `_from_campfire` are native alternatives.

## Verification and limits

`python tools/generate_integration_recipes.py --write` then `--check` verifies source parity, native grid sizes/yields, bilingual titles, reserved IDs and the component DAG. This leaf does not run a server, builds or Computer Use. Loaded registry/tags, raw-lens availability, FE setup, actual crop acquisition, bowl remainders, survival pacing, explicit delivery/restart behavior and manual tutorial acceptance remain integration/runtime checks. Native recipes contain no dependency on later ENTRELUMEN components; that does not prove the entire survival acquisition chain. Existing later steel, brass and Nature's Aura recipes are preserved.
