# Workshop integrations: AE2 stock, mobile storage, Create transit and measurement

`catalog/families/workshop-integrations.json` pins 12 JARs that connect systems already in the pack (AE2, Sophisticated Storage/Backpacks, Create, Botany Pots, the power mods); none adds a storage, transport or power system of its own and no new library was needed. Together with the [small QoL family](small-qol-family.md) the lock grows from 248 client / 209 server to **268 client / 228 server** JARs; every previous entry is byte-identical. SHA-1 matched the read-only ATM10 8.1 instance metadata; licenses, ranges and sides were read from each JAR. No ATM10 configuration, script, quest or asset was used. This family was verified in the same dedicated-server run as its companion family.

| Mod · file (CF project / file) | Role | Introduction | Later use | Relation to another system |
|---|---|---|---|---|
| ME Requester · `merequester-neoforge-1.21.1-1.4.3.jar` (688367 / 8285803) | Keep AE2 stock through autocrafting | IV | V-VI: Ark component buffers | Uses the existing AE2 patterns; no second store |
| AE2 Network Analyzer · `AE2NetworkAnalyzer-1.21-2.1.5-neoforge.jar` (961856 / 7622554) | QoL: channel and connection view | III | V | Debugs the large ME networks the Ark needs |
| Sophisticated Storage in Motion · `sophisticatedstorageinmotion-1.21.1-0.10.34.358.jar` (1166930 / 8687968) | Storage on contraptions, minecarts and boats | II | IV: expedition trains | Extends the selected Sophisticated Storage |
| Sophisticated Storage Create Integration · `sophisticatedstoragecreateintegration-1.21.1-0.1.21.209.jar` (1226755 / 8503147) | Create interaction for storage | II | III | Deployers and contraptions with existing chests |
| Sophisticated Backpacks Create Integration · `sophisticatedbackpackscreateintegration-1.21.1-0.1.8.134.jar` (1238567 / 8398818) | Create interaction for backpacks | II | III | Backpacks on contraptions |
| Create Hypertube · `create_hypertube-0.6.0-NEOFORGE.jar` (1281336 / 8541877) | Powered player tubes | III | IV-VI: station links | Must be built and powered; complements trains and III aircraft |
| Bells & Whistles · `bellsandwhistles-0.4.7-1.21.1.jar` (905040 / 6299879) | Create train and station parts | II | IV | Finishes rail lines |
| Botany Trees · `botanytrees-neoforge-1.21.1-21.1.7.jar` (411357 / 8188485) | Trees in Botany Pots | I | III | Inherits the staged Botany Pots hopper/tier recipes |
| Elevator · `elevatorid-neoforge-1.21.1-1.11.4.jar` (250832 / 6199696) | Floor-to-floor elevators | I | V-VI: Ark modules | Short vertical travel only; no teleport network |
| Energy Meter · `energymeter-neoforge-1.21.1-0.5.2.jar` (532169 / 8622279) | Inline FE meter | II | IV | Measure Powah/Mekanism/Flux plants before scaling |
| Ranged Pumps · `rangedpumps-1.3.0.jar` (247496 / 5508029) | Powered area pump | III | IV | Lava and water for Powah, Mekanism and IE |
| Dummmmmmy · `dummmmmmy-neoforge-1.21-2.1.0.jar` (225738 / 8510632) | Target dummy | I | V | Test Iron's Spells, Draconic and Mekanism damage safely |

## Staging and keybindings

No recipe is staged: none of these adds a power jump beyond its inputs. Elevators only move a player between elevator blocks in one column, and hypertubes need a powered Create route. Create Hypertube's escape key reads Left Shift directly while inside a tube; it is left native because it acts only in a tube. The other JARs register no key.

## Rejected

- **Advanced Peripherals 0.8.0a**: its CurseForge relation requires the official CC:Tweaked project (282001), while the pack pins CC:Tweaked 1.120.2 from project 1676502. The curation closure correctly rejects it; changing the CC:Tweaked source is a separate decision.
- **Integrated Terminals and Integrated Crafting**: a second storage terminal and autocrafting system beside AE2.
- **Integrated Scripting** (29 MB script engine) and **Transfer Labels** (another item transport).
- **FTB Essentials**: `/home`, `/back` and `/tpa` would bypass the staged teleportation of RFTools, Draconic, JDT and Flux.

## Verification boundary

Static: `python tools/curate_pack.py --check`, `python tools/generate_family_balance.py --check` (alternate-route guard re-run with the new JARs) and Default Options checks. Dedicated-server evidence is in [the runtime receipt](../verification/workshop-integrations-runtime.json). No block was placed and no client was launched: requester behaviour, contraption storage, hypertube travel and rendering are unverified.
