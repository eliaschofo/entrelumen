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
