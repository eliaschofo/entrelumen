# Arcane service: restoring an item's forging history

The assembled Ark's arcane module restores an item's forging history. Every anvil operation doubles an item's prior-work penalty (`minecraft:repair_cost`: 1, 3, 7, 15 …). The service returns it to 0 and keeps everything else. It creates no enchantment and removes no curse. It replaced the earlier separation of compound books on 23 September 2026 at Elias's direction; the reasons are below.

## Use

- **Hands:** the worked item (anything but a book) in the main hand; ordinary books in the offhand.
- **Where:** use the arcane module of a complete Ark. The server checks one nearby controller and all six modules in loaded chunks, as the other module services do.
- **Cost:** N = ceil(log2(repair_cost + 1)), which is exactly the number of anvil operations for vanilla costs. The service consumes N ordinary books and 5·N experience levels. Creative players also pay.
- **Result:** `repair_cost` becomes 0. Enchantments, curses, levels, damage, name, lore, custom data and modded components such as Apotheosis sockets, affix names and durability bonuses stay identical.

A visitor may use it regardless of their campaign. The service does not read or change campaign progress and issues no reward.

The server rejects before any mutation in these cases:

- the item is an enchanted book, a plain book or a stack;
- the item has no penalty;
- the books or levels are insufficient;
- the item is in the wrong hand;
- the player is a spectator, out of reach, or the Ark is incomplete or ambiguous.

A second use on the same item has nothing left to restore and charges nothing. Existing empty-hand field-journal reading and crouched stabilization deposits are unchanged.

## Why the service changed

Separating compound books is available elsewhere, and earlier than a finished Ark:

- the Apothic Enchanting Library of Alexandria (deposit a book, extract each enchantment losslessly, around Act III);
- the [Atlas Library](apotheosis-family.md#atlas-library) (Act V);
- the Actually Additions Atomic Reconstructor, which splits a book for 155,000 CF (`booklet.actuallyadditions.chapter.bookSplitting`).

Equipment disenchanting is also covered several times: Apothic scrap, improved scrap and extraction tomes, EvilCraft's Purifier with a Blook, Tombstone's Book of Disenchantment, the Draconic Evolution Disenchanter, the Industrial Foregoing Enchantment Extractor, the Actually Additions Lens of Disenchanting and the Create: Enchantment Industry grindstone.

Other candidates were rejected because installed mods already provide them. The overlap was inspected in the pinned JARs:

| Idea | Already provided by | Evidence |
|---|---|---|
| Curse purification with a cost | Apothic Enchanting prismatic web in the anvil; Occultism Spirit Grindstone (removes only curses, keeps other enchantments, refunds 100 % XP) | `info.apothic_enchanting.prismatic_cobweb`; Occultism dictionary entry `crafting_rituals.spirit_grindstone` |
| Combining mutually exclusive enchantments | Apothic Enchanting removes Protection and Sharpness from their exclusive sets; Occultism's iesnium anvil and CEI super-enchanting exceed the natural maximum by one | `data/minecraft/tags/enchantment/exclusive_set/{armor,damage}.json` in Apothic Enchanting 1.6.1 |
| Moving enchantments between items | Apothic extraction tome (keeps the item), Tombstone, EvilCraft, IF applicator | item descriptions in each JAR |
| Removing the anvil "too expensive" cap | Apothic Enchanting `AnvilMenuMixin` (cap 40 → unlimited, charges optimal XP); Occultism iesnium anvil | decompiled `AnvilMenuMixin`, Occultism dictionary |

What no pinned mod does is lower an existing penalty while keeping the enchantments:

- a vanilla grindstone resets it only by stripping them;
- the Occultism iesnium anvil only slows its growth;
- a CEI Blaze Forger avoids adding more only for merges done inside it.

A search of the decompiled Apotheosis suite and of every locked JAR's English texts found no other writer of `REPAIR_COST`. Late equipment keeps collecting anvil operations while Apotheosis gems, Iron's Spells upgrades and staged enchantments are added. This is maintenance for gear carried through the whole campaign, not new power.

## Reference

The interaction reuses the vanilla anvil concept (prior work) and grindstone (reset) semantics on the existing arcane module block, with chat feedback. There is no new GUI or asset. The earlier reference note for the module stands: `docs/verification/screenshots/journal-arcane-en.png` shows the physical block and chat journal. The main quest text does not describe the old separation, so no quest changed. The arcane module tooltip and the four service messages were rewritten in English and Spanish.

## Verification boundary

- **JUnit** (`ArcaneRestorationTest`): the vanilla 2^n − 1 sequence maps to n operations up to 31; non-vanilla costs round up; 0 and negative values cost nothing and are not eligible.
- **Isolated GameTests** (`arcaneRestorationResetsForgingHistoryAndKeepsEverythingElse`, `arcaneRestorationRejectsWithoutPartialPayment`):
  - the real block interaction by an early-campaign visitor on a sword with enchantments, a curse, damage, name, lore and custom data;
  - the exact charge, an unchanged campaign, and a replay that pays nothing;
  - every rejection above without mutation, and creative payment.
- **Full-pack case** (`arcaneRestorationKeepsApotheosisAndModdedDataAtARealArk`): a netherite sword with Apothic Enchanting's Scavenger enchantment and Apotheosis sockets, durability bonus, boss origin and affix name, restored at a real Ark in the installed pack.

Results and logs are in [the Apotheosis runtime receipt](../verification/apotheosis-runtime.json). Survival pacing, client feedback rendering and third-party claim interaction were not tested.
