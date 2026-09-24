# Arcane library

The assembled Ark separates one enchanted book containing N stored enchantments into N individual enchanted books. A visitor may use it regardless of their current campaign. This gives the arcane module a shared library service without manufacturing enchantments or removing curses.

Use the existing arcane module with the compound book in the main hand and at least N−1 ordinary books in the offhand. The main inventory needs N−1 empty slots. The server checks a unique nearby controller and all six physical modules in loaded chunks. It consumes the ordinary books and replaces the original with the first volume; additional volumes occupy the reserved slots. No overflow drops, remote inventories, persistent queue, XP payment, background ticking or offline upkeep are involved. Creative players also pay the books.

Each stored enchantment and its level is retained exactly, including curses and the stored-enchantment tooltip setting. The first output, chosen by sorted enchantment registry ID, retains the source's other components; additional outputs have ordinary enchanted-book defaults and the source prior anvil work cost. Names, lore and arbitrary custom data are never multiplied across all outputs. Combining the resulting books still follows the native anvil rules. Equipment is not disenchanted and no new levels are rolled.

Wrong hands, spectators, unreachable or absent modules, single-enchantment or stacked source books, missing books, insufficient space and incomplete or ambiguous structures reject before inventory mutation. Every output is constructed before the server-thread transaction. Existing empty-hand field-journal reading and crouched stabilization deposits remain available. The service does not read or modify campaign progress or issue rewards.

## Reference and overlap

The visual reference is Minecraft's existing enchanted book and inventory interaction. Root also inspected the existing arcane-module screenshot at `docs/verification/screenshots/journal-arcane-en.png`; that older image shows the physical block/chat journal, not the current BookViewScreen. This change introduces no asset or new custom GUI; a rendered EN/ES interaction review remains pending under the no-Computer-Use instruction.

Pinned Tombstone and EvilCraft already offer equipment disenchanting with their own costs. This service is restricted to deterministic separation of compound books at a constructed Ark, so those equipment workflows retain their role. The ordinary book inputs keep farming, paper and leather useful without adding a grind currency. Main quest instructions and block hints are authored in English and Spanish.

## Verification boundary

Two native GameTests exercise the actual block interaction and conservative inventory transaction, including an early-campaign gift recipient, curses, levels, custom data, source name/lore, prior work, XP, replay, exact capacity and rejection. Loaded full-pack evidence belongs in `docs/verification/arcane-building-runtime.json`. These tests do not establish survival pacing, client rendering, third-party claims behavior or final performance.

## Replacement proposal

Status: proposal only (23 September 2026). The separation service above stays implemented and unchanged until Elias decides.

### Why the current service is now redundant

With the [Apotheosis family](apotheosis-family.md) in the pack, several installed mods already turn one compound book into single-enchantment books, and some do it earlier and more cheaply than a finished Ark:

- the Apothic Enchanting Library of Alexandria (deposit a book, extract each enchantment losslessly, around Act III);
- the Atlas Library (any enchantment from a pooled balance, Act V);
- the Actually Additions Atomic Reconstructor, which splits a compound book for 155,000 CF (`booklet.actuallyadditions.chapter.bookSplitting`).

Equipment disenchanting is also covered several times: Apothic scrap, improved scrap and extraction tomes, EvilCraft's Purifier with a Blook, Tombstone's Book of Disenchantment, the Draconic Evolution Disenchanter, the Industrial Foregoing Enchantment Extractor, the Actually Additions Lens of Disenchanting and the Create: Enchantment Industry grindstone.

### Rejected replacements (overlap inspected in the pinned JARs)

| Idea | Already provided by | Evidence |
|---|---|---|
| Curse purification with a cost | Apothic Enchanting prismatic web in the anvil; Occultism Spirit Grindstone (removes only curses, keeps other enchantments, refunds 100 % XP) | `info.apothic_enchanting.prismatic_cobweb`; Occultism dictionary entry `crafting_rituals.spirit_grindstone` |
| Combining mutually exclusive enchantments | Apothic Enchanting removes Protection and Sharpness from their exclusive sets; Occultism's iesnium anvil and CEI super-enchanting exceed the natural maximum by one | `data/minecraft/tags/enchantment/exclusive_set/{armor,damage}.json` in Apothic Enchanting 1.6.1 |
| Moving enchantments between items | Apothic extraction tome (keeps the item), Tombstone, EvilCraft, IF applicator | item descriptions in each JAR |
| Removing the anvil "too expensive" cap | Apothic Enchanting `AnvilMenuMixin` (cap 40 → unlimited, charges optimal XP); Occultism iesnium anvil | decompiled `AnvilMenuMixin`, Occultism dictionary |

### Proposed service: restoring an item's forging history

One capability is missing from every pinned mod. Vanilla anvils double an item's prior-work penalty (`minecraft:repair_cost`) with each operation. Apothic Enchanting removes the 40-level ceiling, but it still charges the growing cost in raw XP. None of the pinned mods lowers an existing penalty and keeps the enchantments:

- a vanilla grindstone resets it only by stripping the enchantments;
- the Occultism iesnium anvil only slows its growth;
- a CEI Blaze Forger avoids adding more only for merges done inside it.

A search of the decompiled Apotheosis suite and of every locked JAR's English texts found no other writer of `REPAIR_COST`.

The proposed Ark service would restore an item's forging history, keeping everything else:

- **Use:** the enchanted item goes in the main hand and ordinary books in the offhand, as in the present flow, at the assembled Ark's arcane module (Act VI).
- **Cost:** N = log2(repair_cost + 1), rounded up, which counts the anvil operations recorded on the item. The service consumes N ordinary books and 5·N experience levels, and refuses before any mutation if either is missing.
- **Result:** `repair_cost` returns to 0. Enchantments, curses, levels, name, lore, custom data and durability stay exactly as they were.
- **Excluded:** enchanted books, whose penalty is irrelevant and would launder books from the libraries, and items with no penalty. It never touches campaign data or rewards.
- **Why it belongs to the Ark:** late equipment accumulates dozens of anvil operations while Apotheosis gems, Iron's Spells upgrades and staged enchantments are added. This is maintenance for gear carried through the whole campaign, not a new source of power: it creates no enchantment and removes no curse.
- **Verification it would need:** GameTests for exact component preservation, the book and XP charge, refusal without mutation, a replay that pays nothing, and a gifted item used by an early-campaign visitor.

The implementation is decided afterwards; this document changes no code.
