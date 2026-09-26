# Engineering workshop

> **Replaced by [`ark-modules-v2.md`](ark-modules-v2.md) (25 September 2026).** The Ark's first version (the repair workshop) was removed: each module now gives a global effect to its team from its place in the Ark. This page is kept as the record of what existed; the companion no longer implements it.

An assembled Ark is a shared repair workshop. Use its engineering module with a damaged tool in your main hand and its native anvil repair material in your offhand. Each use consumes exactly one material and repairs up to one quarter of maximum durability, rounded down as in vanilla. A partially damaged tool still costs one material. The workshop spends no experience and does not increase prior-work cost. Names, enchantments, prior-work cost and other components remain intact. It neither combines tools nor transfers enchantments. Bypassing the anvil's XP and too-expensive limits is the deliberate late-game benefit; material costs remain.

A single controller and all six modules must be visible in loaded chunks. The check runs only on interaction: no block entity, ticking, chunk tickets, fuel buffer or offline upkeep. Missing or ambiguous structure, spectator mode, remote use, wrong hand, non-repairable items and unsuitable material take nothing. Full durability stops consumption. Creative mode also pays the material.

Anyone allowed to use the block can repair equipment, including an early-game visitor with gifted tools. Repair never reads, merges, completes or rewards a campaign. Normal acquisition of the Ark components supplies the progression cost; possession and sharing stay unrestricted. Claim systems receive the ordinary RightClickBlock event first; a dedicated foreign-claim runtime case remains pending.

## Reference inspected before implementation

The actual engineering module, vanilla targeted-block overlay, inventory-scale icon and empty-hand chat interaction were visually inspected in docs/verification/screenshots/engineering-diagnosis-en.png. Existing model and diagnostic interaction remain; no new item or screen was drawn. The local Minecraft 1.21.1 AnvilMenu.createResult source supplies isValidRepairItem and min(damage, maxDamage / 4). The workshop additionally honors NeoForge IItemStackExtension.isRepairable, so this is not full AnvilMenu/event equivalence. It reuses vanilla anvil audio at reduced volume. Artwork acceptance and rendered bilingual hints remain pending until client review.

This adds one practical Ark service. Other complete module benefits and postgame masteries remain unfinished. Native automated checks cover named/enchanted tools with prior-work cost, partial repairs, intact-item replay, native leather/iron/membrane materials, invalid structure, reach, hand, spectator mode and creative payment; a natural survival playtest and a broad mod-equipment matrix remain pending.
