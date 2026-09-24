# Apotheosis: World Tiers, staged enchanting, the Atlas Library and spawner augments

Elias reversed the earlier [exclusion](arcane-expansion-family.md#apotheosis-decision) on 23 September 2026: the Apotheosis suite stays in the pack. `catalog/families/apotheosis.json` pins the four modules plus Placebo, using the exact files from the read-only ATM10 reference instance. That instance's CurseForge SHA-1 values matched before pinning. Placebo 9.9.2 was already locked as a dependency of the Shadows_of_Fire performance mods, and its pin matches that entry byte for byte. The lock grows from 268 client / 228 server to **272 client / 232 server** JARs, and all 268 previous entries are unchanged. Licenses, version ranges and sides come from each JAR. No ATM10 configuration, script, quest or asset was used.

| Mod · file (CF project / file) | Role | Acts | Relation to other systems |
|---|---|---|---|
| Apotheosis · `Apotheosis-1.21.1-8.7.0.jar` (313970 / 8564919) | Affixed loot, gems, rarity materials, salvaging, invaders and World Tiers | I–VI | The team campaign sets the World Tier; its rune-based spawner modifiers yield to ENTRELUMEN augments |
| Apothic Enchanting · `ApothicEnchanting-1.21.1-1.6.1.jar` (1063926 / 8682996) | Eterna/Quanta/Arcana table, shelves, infusion, tomes, Library of Alexandria and Ender Library | I–V | Four ENTRELUMEN shelves and the Atlas Library extend it; the 80/90/100-Eterna shelves are staged |
| Apothic Spawners · `ApothicSpawners-1.21.1-1.4.0.jar` (986583 / 8469405) | In-place spawner modifiers | III–V | Sixteen augments carry the modifiers; spawners are not portable |
| Apothic Attributes · `ApothicAttributes-1.21.1-2.10.1.jar` (898963 / 8502288) | Required attribute library (crit, life steal, armour pierce, attribute screen) | — | ATM10 runs it beside AttributeFix, which is also locked here |
| Placebo · `Placebo-1.21.1-9.9.2.jar` (283644 / 8463693) | Library (already locked) | — | — |

Apotheosis requires Patchouli (locked) and optionally integrates with Curios (locked), Gateways to Eternity (not selected: another combat-wave system), Enchantment Descriptions (locked, client), JEI and Jade.

## World Tiers follow the team campaign

Apotheosis checks one advancement per tier (`apotheosis:progression/haven` … `pinnacle`) before letting a player select that tier (`WorldTier.isUnlocked` → `ApothMiscUtil.hasAdvancement`). `tools/generate_family_balance.py --family apotheosis` writes reversible overrides of those five files to `pack/kubejs/data/apotheosis/advancement/progression/`. Each override keeps the parent, icon, title, frame and background. It replaces only the criteria, the requirements and the description, which now uses an ENTRELUMEN EN/ES key:

| Tier | Criterion | Opens when |
|---|---|---|
| Haven | `minecraft:tick` | From the first tick |
| Frontier | `minecraft:impossible` | The team's campaign completes Act II (reaches Act III) |
| Ascent | `minecraft:impossible` | The campaign completes Act III (reaches Act IV) |
| Summit | `minecraft:impossible` | The campaign completes Act V (reaches Act VI) |
| Pinnacle | `minecraft:impossible` | The team activates the Ark (`last_horizon`) |

Ascent opens after Act III rather than Act IV. Act IV is the expedition act: Aether, Twilight Forest, Bumblezone, Cataclysm structures and horizon charts. Opening Ascent as it starts rewards those dungeons with Ascent's uncommon-to-epic loot and makes epic material reachable for Act V spawner augments. The tier ladder then spaces one tier per act: Frontier during III, Ascent during IV–V, Summit during VI and Pinnacle after the ending.

Since 24 September 2026 the story sets the difficulty: completing the milestone raises every team member's *active* World Tier to that tier. It never goes back down, and players cannot pick another one. Manual selection is disabled (`Enable Manual World Tier Changes = false`). See [Story-set World Tiers](#story-set-world-tiers) below. Until then, tier selection was manual and an unlocked tier was only an offer.

`companion/.../ApotheosisTiers.java` grants the remaining criteria through the vanilla `PlayerAdvancements.award` API. It holds pure rules (`reached(Campaign)` and `target(Campaign, unlocked)`, unit-tested), a read-only campaign lookup that never creates a campaign or marks SavedData dirty, and `sync(player)`. Sync runs:

- once per second for online players (tick offset 7, apart from the FTB quest mirror);
- on FTB `PLAYER_LOGGED_IN` (after the team is resolved), which covers reconnects;
- on `PLAYER_JOINED_PARTY`, so a later member receives the team's tiers at once;
- on `PLAYER_CHANGED`, so leaving a party grants the personal campaign's unlocks at once.

It is idempotent, because completed advancements are skipped. Advancements are never revoked: a player who leaves a party keeps the unlocks they already have. This matches vanilla advancement semantics and the pack's rule that possessions and knowledge stay usable regardless of origin. The active tier likewise only rises, as described below. A missing advancement is skipped silently: without Apotheosis, or when a datapack removes one, the grant path stays safe. The only hooks in existing code are two lines in `Entrelumen.java` that register the new content and listeners. Milestone, delivery, advance and Ark semantics are untouched.

## Story-set World Tiers

Elias's direction (24 September 2026): when an act's milestone completes, the World Tier of every team member moves to that act's tier automatically, and the player cannot choose another one, higher or lower. A later decision the same day: the tier stays at the **highest the player ever reached** and never drops, not even after leaving a team.

**API.** Apotheosis 8.7.0 exposes the setter the mod itself uses: `dev.shadowsoffire.apotheosis.tiers.WorldTier.setTier(Player, WorldTier)`, public and static, with `getTier(Player)` beside it. This was checked in the decompiled copy in `G:/Elias/Codex/Entrelumen-work/apotheosis-inspect-20260923`. `setTier` stores the `apotheosis:world_tier` attachment, which is copied on death, and syncs it to the client with `WorldTierPayload`. It swaps the player tier augments and awards the `world_tiers_activated` stat, which also ends Apotheosis's tier tutorial.

The companion reaches these methods by reflection only:

- the handles are resolved once, and only when `ModList.isLoaded("apotheosis")`;
- the enum constants must match `HAVEN`…`PINNACLE` by name;
- any resolution failure or failing call is logged once and turns the tier step off until restart.

Nothing links against Apotheosis, and without it `sync` only grants advancements.

**Target.** `target(campaign, unlocked)` is the campaign's tier: the highest tier that `reached(campaign)` includes *and* whose unlock advancement the player holds. A tier whose advancement a datapack removed is skipped, so the player never sits in a locked tier.

**Record.** The tier the player holds is `story(recorded, target) = max(recorded, target)`:

- `recorded` is the `entrelumen:story_tier` attachment, a NeoForge data attachment that stores the tier name.
- It is saved with the player, so it survives relogs and server restarts.
- It is `copyOnDeath`, and NeoForge copies all serialisable attachments on non-death clones such as the End exit, so it survives both.
- `sync` rewrites it whenever the result is higher.

The record lives on the player, not in the campaign `SavedData`, so the campaign schema is untouched.

After granting, `sync` compares the held tier with `getTier` and calls `setTier` only when they differ, so replays, reconnects and repeated events do nothing. The record is kept even without Apotheosis, so installing it later applies the right tier at once. On a real change the player gets one chat line, `entrelumen.apotheosis.tier.set` ("The story sets your World Tier to Frontier." / "La historia fija tu Nivel de Mundo en Frontera."). The tier name uses Apotheosis's own key.

**Which campaign.** The lookup matches what `Campaigns.current` would create, but never creates anything:

- a party reads its campaign, or its owner's personal campaign if none is stored yet;
- a personal team reads the player's personal campaign, or a fresh Act I campaign if none is stored.

Joining a party at a higher act raises a member to the party's tier. Leaving a party, joining one at a lower act, or `/entrelumen admin set` to an earlier act never lowers anyone. Outside any active campaign the record is still enforced: the FTB Teams manager is not loaded, the player has no team, or the campaign is archived.

**Lock.** `pack/config/apotheosis/apotheosis.cfg` now sets `Enable Manual World Tier Changes = false`. Apotheosis syncs that value to clients in its config payload. The selection screen then shows the activate button as disabled ("tier changes disabled"), and the tutorial's last page shows Apotheosis's own EN/ES text saying tiers are activated automatically in this modpack. The server enforces the lock natively. The first serverbound tier packet of a player who never activated a tier (the tutorial's "done") only marks the tutorial finished. Any later one disconnects the client with `disconnect.apotheosis.tier_changes_disabled`, which a normal client never sends.

As a second layer, anything that moves the tier outside the story is put back by the next sync, within a second. That covers a server that re-enables manual changes and the operator command `/apoth set_world_tier`. Operators can raise a tier by moving the story forward with `/entrelumen admin set <act>`. Lowering one means clearing the player's `entrelumen:story_tier` attachment offline, which is deliberately not a command.

The config was checked by replaying Apotheosis's `AdventureConfig` load calls through the real Placebo 9.9.2 `Configuration` class. The replay reports `changed=false`, `manualTiers=false`, cooldown 12000 and comparisons off, and leaves the file byte-identical, so the mod will not rewrite it.

**Advancement text.** The EN/ES descriptions of the five progression advancements now say the world *rises* to the tier when the act completes, instead of calling it an unlock.

## Enchanting: four ENTRELUMEN shelves and a staged ladder

Apothic Enchanting reads block stats from its `enchanting_stats` dynamic registry. The companion ships four files in `data/entrelumen/enchanting_stats/`, which are ignored without the mod. Without Apothic the shelves still count as bookshelves through `getEnchantPowerBonus`. They are also in `#minecraft:enchantment_power_provider`.

| Shelf (ID) | Act | Eterna / max | Quanta | Arcana | Clues | Recipe (new, `pack/kubejs/data/entrelumen/recipe`) |
|---|---|---|---|---|---|---|
| Cartographer's Shelf (`entrelumen:cartographer_shelf`) | II | 3 / 40 | 0 | 0 | — | 4 planks, 2 empty maps, 2 bookshelves, calibration frame → 2 |
| Patina Shelf (`entrelumen:patina_shelf`) | III | 4 / 60 | 12 | 0 | — | 4 oxidized copper, 4 hellshelves, power regulator → 4 |
| Lumen Shelf (`entrelumen:lumen_shelf`) | IV | 7.5 / 80 | 0 | 15 | +1 | 4 glowstone, 4 infused seashelves, spectral lens → 4 |
| Horizon Shelf (`entrelumen:horizon_shelf`) | V | 12.5 / 100 | 10 | 10 | — | 4 deepshelves, 2 horizon charts, 2 lumen shelves, renewal engine → 4 |

The native Apothic ladder gets the same act spacing through four precise recipe edits, which the generator validates against the pinned JARs:

| Native recipe | Change | Act | Eterna cap it unlocks |
|---|---|---|---|
| `apothic_enchanting:echoing_sculkshelf`, `soul_touched_sculkshelf` | spectral lens fills the empty top-left slot | IV | 80 |
| `apothic_enchanting:endshelf` | containment seal replaces one of six end stone bricks (the pearl endshelf inherits it) | IV | 90 |
| `apothic_enchanting:draconic_endshelf` | Ark bus fills the empty top-left slot | V | 100 |

The resulting table ladder has five steps:

- up to 45 Eterna in Acts I–II (basic, hell and sea shelves, cartographer);
- 60–65 in Act III (infused variants, patina);
- 70–80 in Acts III–IV (deepshelves, sculkshelves, lumen);
- 90 in Act IV (endshelves);
- 100 in Act V (draconic endshelf, horizon).

The Library of Alexandria stays native: its recipe already needs four infused shelves, a 45-Eterna infusion. The Ender Library needs a 100-Eterna infusion and is therefore Act V.

Enchantment maximum levels keep Apothic Enchanting's defaults (`enchantments.cfg` is generated per loaded enchantment and is not shipped). The Eterna ladder bounds which levels the table can roll in each act. Create: Enchantment Industry, the Ars enchanting apparatus, EvilCraft and Tombstone keep working as before. Each reads vanilla definitions or its own recipes, and none of them depends on Apothic's table. CEI super-enchanting and Occultism's iesnium anvil can still exceed a natural maximum by one, as they do natively.

## Atlas Library

`entrelumen:atlas_library` improves on the Ender Library in two ways. Its cap is 40 levels instead of 31. It can also write **any** eligible enchantment in the registry, including ones that were never deposited. Books pay into one shared pool of *lumen*. The level cap comes from the Eterna around the library, measured exactly as an enchanting table at that spot: Apothic and ENTRELUMEN shelves count, and so do vanilla bookshelves.

It is composed rather than subclassed. Apothic's `EnchLibraryTile` stores per-enchantment points as `int` powers of two, which caps at level 31. Its screen lists only deposited entries, computes prices on the client and is bound to Apothic's menu type. Subclassing would also have made the companion hard-depend on Apothic classes. The implementation is:

- `AtlasLibraryLedger`: pure arithmetic with JUnit tests;
- `AtlasLibraryBlockEntity`, `AtlasLibraryMenu`, `AtlasLibraryNetwork` and the client `AtlasLibraryScreen`: native widgets and no new GUI texture;
- `AtlasLibraryEterna`, which calls Apothic's public `EnchantmentTableStats.gatherStats` through a reflection handle resolved once. Without Apothic it falls back to Apothic's own rule for unknown blocks: 2 Eterna per enchant-power point, at most 30.

Formula, with *b* = clamp(ceil(10 / weight), 1, 10), so Sharpness = 1, Unbreaking = 2, Fortune or Mending = 5, Silk Touch = 10:

- **Deposit** of one enchantment at level L adds floor(b · 2^(L−1) / 2).
- **Price** of holding level L is b · 2^(L−1), doubled for `#minecraft:treasure`. Raising a book from c to L costs price(L) − price(c).
- **Cap** at Eterna E is clamp(floor(E / 2.5), 1, 40) for multi-level enchantments; 100 Eterna reaches 40. Single-level enchantments stay at I. Quick Charge and Lure stop at V, because above that the charge time and the bite delay break in vanilla.
- **Excluded**: curses (`#minecraft:curse`) and anything outside `in_enchanting_table`, `tradeable`, `on_random_loot` and `treasure`. Excluded enchantments are worth 0, never listed and never written. A book worth 0 (only curses) is refused by the slot and by automation, so it is never destroyed.

No cycle creates points. A deposit is worth at most half its price, or a quarter for treasure. Upgrading an owned book from c to L and depositing it changes the pool by −(price(L) − price(c)) + deposit(L) − deposit(c) = −b(2^(L−2) − 2^(c−2)) ≤ 0, compared with depositing it as it was. The unit test enumerates this for every weight, level up to 40 and treasure flag.

Numeric example: a Sharpness V book deposits 8 lumen, and Sharpness XX costs 2^19 = 524,288, so it takes **65,536 Sharpness V books** (Eterna ≥ 50). Sharpness X costs 512, or 64 books. Mending costs 10 and returns 2 when deposited.

Server authority: the server builds the list, caps and exact prices for every eligible enchantment and sends them in a snapshot, and the client only displays it. A click sends a vanilla container-button id (enchantment registry id × 64 + target level). The block entity re-checks eligibility, the cap against a freshly measured Eterna, the direction and the pool before writing. Eterna is recomputed for every snapshot and every withdrawal and is never cached. The pool is a saturating `long` and cannot go negative.

Hoppers and ME buses can insert books through a one-slot item handler. Extraction only happens in the GUI. Shift-click moves a book into the output slot, never into the deposit slot. Breaking the block drops one item carrying the pool in `block_entity_data`, and placing it restores the pool. The recipe is Ender Library, Ark bus, renewal engine, 2 horizon shelves and 4 containment seals (Act V).

## Spawner augments replace Apotheosis runes

Apotheosis 8.7.0 disables all 32 Apothic Spawners modifier recipes (`neoforge:false` placeholders at the same paths). It replaces them with eleven rune modifiers plus four tier-upgrade runes built from gem-fused slate, spawner chains and infused spawner runes. The pack restores the Apothic Spawners set on ENTRELUMEN items. The generator overrides each of the 16 modifiers and their 16 quartz inverses from the Apothic Spawners original, after checking that the Apotheosis copy is only a placeholder. Only `mainhand` changes; `stat_changes`, `offhand` and `consumes_offhand` stay native.

The inverse still consumes the augment and keeps the quartz, as the original consumed its item. Apotheosis's 26 rune modifiers and 20 rune crafting recipes (including three inactive fallbacks) are removed, so runes are not a second, cheaper route. Spawner chains still drop but no longer craft anything.

Each augment is a copper medallion: 4 Create copper sheets, 1 Apotheosis gem dust, 2 rarity materials, the original Apothic Spawners item as its core and one ENTRELUMEN component. There are no rune slates. The original core, such as fermented spider eye for spawn count or the clock for maximum delay, keeps the Apothic concept recognisable. The rarity material follows World Tier drop tables:

- **Act III**, timeworn fabric (uncommon, from Haven): min delay (power regulator), max delay (power regulator), spawn range (routing matrix), player range (inventory sensor), silent (handling core), youthful (propagation core).
- **Act IV**, luminous crystal shard (rare): spawn count and max nearby (ecosystem capsule), initial health (spectral lens), burning (containment seal).
- **Act V**, arcane sands (epic, weight 100 from Ascent): echoing, ignore conditions and ignore light (renewal engine); ignore players, no AI and redstone control (Ark bus).

Silk-touch spawner harvesting is disabled (`Spawner Silk Level = -1` in `pack/config/apothic_spawners.cfg`). The only Silk Touch available is level I, which is early. Portable spawners would move mob farms ahead of the Act IV Hostile Neural Networks and Industrial Foregoing routes. Augments therefore improve spawners where they are found: dungeons and Apotheosis rogue spawners. Capturing spawn eggs keep their native 0.5 % per level.

## Adventure module

`pack/config/apotheosis/apotheosis.cfg` is written by replaying the exact `AdventureConfig.load` calls through the real Placebo 9.9.2 classes. A replay reports `hasChanged() = false`, so the mod will not rewrite it. Three values differ from the defaults:

- **Boss Spawn Cooldown** is 12000 ticks (10 minutes) instead of 3600. Invaders only appear from Frontier, which players choose; the longer cooldown keeps combat moderate even in the Twilight Forest, whose native chance is about 2.4–2.8× the overworld's.
- **Enable Equipment Comparisons** is off, because the locked Equipment Compare mod already shows comparisons on the same Shift key.
- **Enable Manual World Tier Changes** is off (24 September 2026), because the campaign sets the tier; see [Story-set World Tiers](#story-set-world-tiers).

Rarity weights, affix pools, invader chances per tier and gem drops stay native. The campaign's tier is the difficulty knob: Haven has no invaders and no epic or mythic rarity.

## Ark arcane service

Book separation at the Ark became redundant with the Apothic and Atlas libraries. The arcane module now [restores an item's forging history](arcane-library.md): it resets the anvil prior-work penalty for one ordinary book and five levels per recorded operation, and keeps every other component, including Apotheosis affixes and sockets.

## Keybindings

Native defaults are kept and now pinned in the client preset, with no world-context collision:

- Ctrl+T opens the World Tier screen. FTB Quests' Ctrl+T acts only in its quest editor GUI.
- Ctrl+O toggles radial mining. JEI's Ctrl+O acts only in GUIs, and NeoForge 21.1's modifier lookup gives the Ctrl binding precedence over Occultism's plain O.
- Shift+T links an item to chat inside inventories. Just Dire Things' Shift+T acts in the world.

The compare key (Left Shift) is inert because comparisons are disabled. Placebo's keypad 8/9 supporter-trail toggles were already present.

`tools/audit_keybindings.py --simulate-preset` ran on the Defaults QA options plus these defaults and the other families' proposed keys. It exited 0 with no world overlap; 16 findings are context-separated and 65 are review-context.

## Art and interface references

Textures, models and blockstates for the four shelves, the library (no blockstate properties) and the sixteen augments are produced by the controller in `art/grids` with `art/build_art.py`. This batch registers only the IDs and EN/ES names.

The Atlas Library screen follows the Apothic Enchanting library. The pinned JAR's `assets/apothic_enchanting/textures/gui/library.png` was inspected as an image: a 176×230 panel with a scrolling list of five 113×20 rows and a filter field on the left, filter, deposit and output slots stacked on the right, and the inventory below. Its layout code is `EnchLibraryScreen`.

The ENTRELUMEN screen keeps that arrangement at 256×222: search field and a two-line list on the left, deposit and output slots on the right, and lumen and Eterna readouts under them. It uses a vanilla grey panel with 18×18 bevelled slot frames drawn with fills and native `EditBox` and `ObjectSelectionList` widgets, and adds no texture. An in-game EN/ES visual review is still pending.

## Verification boundary

Static checks for this batch:

- `python tools/curate_pack.py --check` for both sides;
- `python tools/generate_family_balance.py --check`, including 37 data overrides and 21 new recipes;
- the tool test suites and a simulated keybinding audit;
- an offline companion build (`gradlew --offline build`, 80 JUnit tests passing, 12 of them new) covering the World Tier rule, the ledger formula, the ≥ 50 % loss, saturation and caps;
- `runGameTestServer`: all 47 required GameTests passed, including two new isolated ones without Apotheosis. Stand-in advancements exercise tier grants, idempotence, a later joiner, non-revocation and a missing Summit. The library tests cover the item-handler deposit, curse refusal, extraction refusal, the vanilla-shelf Eterna cap, server rejection above the cap, round-trip loss, saturation, and break/restore without duplication.

Runtime phase (24 September 2026), recorded in [apotheosis-runtime.json](../verification/apotheosis-runtime.json):

- HEAD installed into the owned server and both client profiles with the receipt-managed installer and `curate_pack.py --install` (four new JARs each).
- The dedicated server started, saved and stopped cleanly. The only ERROR line is the known Create: Enchantment Industry data-map entry.
- The first attempt was cut by the watchdog while classes loaded from the contended G: disk. The world was restored from the pre-install backup and the retry used a temporary `max-tick-time` of 180000, since returned to 60000.
- KubeJS reported 4 edits, 43 removals plus 3 inactive fallbacks, 50 loaded checks and 21 additions without failures.
- `apotheosis.cfg` and `apothic_spawners.cfg` kept their installed hashes through every boot.
- Eight full-pack GameTests passed with the QA JAR: Apothic reads the shelf stats, KubeJS recipes match the generator, augments work on a real spawner and runes are gone, the Atlas Library uses real Eterna without duplication, World Tiers open as acts close, and three arcane-restoration cases.

Still pending: a client launch (World Tier screen, Atlas Library GUI, keybindings, textures, EN/ES rendering), survival pacing, co-op with external clients, performance with the new worldgen, and a run at the normal tick limit without disk contention.

Story-set tiers (24 September 2026, branch `feature/gameplay`):

- JUnit covers:
  - the target rule for every act, the Ark, a fresh campaign, missing unlocks, and archived or absent campaigns;
  - `story` never going below the record, for all 25 pairs, including a player who leaves a party at Ascent;
  - the Apotheosis name keys.
- The isolated GameTest `storyTierOnlyRisesAndCannotBeChosen` runs against a stand-in tier store, because Apotheosis is absent there. It passed and covers:
  - Haven in Act I without a write;
  - Frontier after Act II, set exactly once;
  - a higher or lower tier set outside the story being put back;
  - Act VI capped at the fixture's Ascent and recorded on the player;
  - a lower campaign keeping Ascent;
  - a joining member raised to the party's tier, with no second write on replay;
  - a leaving member keeping Ascent;
  - the record surviving save/load and a death copy;
  - `sync` without Apotheosis.
- The full-pack GameTest `worldTierFollowsTheStoryAndCannotBeChosen` in `ApotheosisGameTests` is written and **pending**: it has not run on the installed pack. It checks:
  - the config and client payload lock;
  - the companion tick moving the real tier to Frontier;
  - `/apoth`-style drift reverted;
  - Ascent, then Pinnacle after the Ark;
  - a lower campaign keeping Pinnacle, its record and its unlock;
  - a chosen Haven being put back to Pinnacle.

  The owned server needs this `apotheosis.cfg` and the QA JAR. The `apotheosis.cfg` hash in [apotheosis-runtime.json](../verification/apotheosis-runtime.json) predates the change.
