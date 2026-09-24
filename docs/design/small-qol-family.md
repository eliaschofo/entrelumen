# Small QoL: attribute range, boats, bushes, leaves, fuel and key bundles

`catalog/families/small-qol.json` pins eight small JARs (about 200 KB together) that remove friction without changing progression; Keybind Bundles is client-only and AI Improvements is classified as performance. No library was needed. The lock count after this tanda is in the [workshop family](workshop-integrations-family.md). SHA-1 matched the read-only ATM10 8.1 instance metadata; licenses, ranges and sides were read from each JAR. No ATM10 configuration, script, quest or asset was used. This family was verified in the same dedicated-server run as its companion family.

| Mod · file (CF project / file) | Role |
|---|---|
| AttributeFix · `attributefix-neoforge-1.21.1-21.1.3.jar` (280510 / 7115922) | Raises vanilla attribute caps so Draconic/Mekanism armour and Iron's spell power are not clipped |
| AI Improvements · `AI-Improvements-1.21-0.5.3.jar` (233019 / 5426792) | Server-side mob AI optimisations (performance) |
| Accelerated Decay · `accelerated-decay-neoforge-21.0.0.jar` (699872 / 5433036) | Leaves decay quickly after felling |
| Hey Berry Shut Up · `heyberryshutup-1.21.0-2.0.4.jar` (634227 / 5517177) | Leg/foot armour prevents bush and cactus damage |
| Jump Boat · `jumpboat-1.21.0-1.0.5.jar` (542110 / 5439938) | Boats hop onto the shore |
| Fireproof Boats · `fireproofboats-1.21.1-1.0.4.jar` (830962 / 6426055) | Nether-wood boats survive lava |
| Fuel Goes Here · `fuelgoeshere-1.21.1-1.2.0.jar` (659090 / 6003423) | Shift-clicked fuel fills the fuel slot first |
| Keybind Bundles · `keybindbundles-1.4.0.jar` (1172594 / 7508312) | Client radial menus for rarely used keys; no default binding is shipped |

AttributeFix matters because Draconic, Mekanism and Iron's Spells gear can exceed vanilla attribute caps; it changes only the allowed ranges, not any item's values. Nothing is staged and no key is bound by default (Keybind Bundles' screen key is unbound).

FTB Essentials stays out: `/home`, `/back` and `/tpa` would bypass the staged teleportation of RFTools, Draconic, Just Dire Things and Flux Networks.

## Verification boundary

Dedicated-server evidence is in [the runtime receipt](../verification/small-qol-runtime.json). No client was launched: key bundles, boats and bush protection were not exercised in play.
