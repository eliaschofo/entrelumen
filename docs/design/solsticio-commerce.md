# Solsticio — commerce, natives and residents

Direction: `docs/design/story-bible.md` (24 September 2026), sections «Solsticio como ciudad», «Las tres reliquias y el final único» and «Luminosidades». Technical base: `docs/design/solsticio.md` (template contract, `CityLayout`, `SolsticioCity`, `StructureProtection`, `SolsticioData`). This document covers branch `feature/solsticio-commerce`: shopkeepers and their tables, the liberation discount, villagers that settle in the trading hall, the six natives that sell the Luminosities, side-quest NPCs, common villagers and easter eggs.

Code: `CommerceRules` (pure: tables, prices, restock, hall zone, caps), `CommerceSites` (persisted sites), `SolsticioCommerce` (runtime), `CommerceOffers` (offers, lore books, survey maps), `mixin/common/VillagerTradingMixin` (prices). Data: `data/entrelumen/solsticio_shops/<type>.json`, `data/entrelumen/solsticio_natives/<discipline>.json`, three structure tags under `tags/worldgen/structure/`.

## Markers (additions to the template contract)

Same DATA structure blocks as the rest of the contract (`solsticio.md`). Names accept `entrelumen:` in front and any case. Arguments are lower-case ids (`[a-z0-9_][a-z0-9_./-]*`). A parameterized marker without its argument, or a plain one with an argument, is unknown: logged and ignored like any other unknown marker.

| `metadata` | Count | Meaning |
| --- | --- | --- |
| `shop:<type>` | any | Feet position of the shopkeeper behind the counter. Types: `bookstore`, `rarities`, `parts`, `seeds`, `smithy`, `apothecary`, `maps`, `minerals`, `records`, `textiles`, `nursery`, `creatures`, `museum`, `bakery`, `apiary`, `curiosities`. A type without a table gets a shopkeeper whose shop stays closed until a table exists. |
| `sidequest:<id>` | any | Feet position of a side-quest NPC. Today the ids are `<n>_inn`, one per inn. |
| `resident` | any | Home of a common villager (feet position inside the house). |
| `easter:<name>` | any | An easter egg; only its position is recorded (`SolsticioData.commerce.easterEggs`). |
| `trading_hall` | 0–1 | (Existing) centre of the trading hall: the six natives stand around it and its zone settles villagers from elsewhere. |

For the controller:

- The shopkeeper faces the longest free line at head height from its marker, up to 6 blocks (in `city5.py`'s shops: from behind the counter toward the door). Keep the head-height block in front of the counter free.
- Natives stand on the first standable spots of a fixed ring around `trading_hall` (offsets 2, then diagonals 2, then 3, 4 and 1 blocks): keep a few free floor blocks around the marker.
- The trading hall zone is a box: horizontal radius `tradingHallRadius` (default 12; `city3.py`'s hall is 15 × 23) and from 2 blocks below to 12 above the marker.
- Workstations in the hall let settled villagers keep working; they also restock lazily without them (below).

## Villagers

Every villager is a vanilla `Villager` with persistent data `entrelumen_commerce` (role, key, site, registry, restock time, offer locks) and the scoreboard tag `entrelumen.solsticio` (`@e[tag=entrelumen.solsticio]`).

| Role | Spawned at | AI | Name | Behaviour |
| --- | --- | --- | --- | --- |
| Shopkeeper | `shop:<type>` | none (statue) | yes, per shop | Invulnerable, persistent, Master level (never levels up, never gets vanilla trades). Offers from its table; restocks lazily. |
| Native | around `trading_hall`, one per discipline | none | yes, per discipline | Invulnerable, persistent. Asks for an Overworld biome; its Luminosity stays out of stock until it has stood there. Can be carried away. |
| Side-quest NPC | `sidequest:<id>` | none | one of four innkeepers | Invulnerable, persistent. Right-click: a short line (EN/ES), or the side quest's hook. |
| Common villager | `resident`, up to `residentCap` | vanilla | no | Nitwit (never takes the shops' workstations), invulnerable, strolls within 12 blocks of home. Right-click: one of eight ambient lines. |
| Settled villager | enters the trading hall zone | unchanged | unchanged | A villager from elsewhere; its trades are cheaper for good. |

- **Spawned once, resumable.** When the city finishes placing, `CommerceSites` records one site per marker (and six native sites at the hall) in `SolsticioData`. A background population then loads each site's chunk under a ticket (`entrelumen_commerce`, released at the end), waits until its entities are loaded, adopts a matching villager already standing there (a restart that lost the record) or spawns one, at most 8 per tick. Each site remembers its villager's UUID, so a restart resumes and a finished city never spawns twice. Cities placed before this branch get their six natives on the next start (their other markers were never recorded).
- **Common villager cap.** `residentCap` (server config, default 40, 0–256) chooses homes evenly spread over the marker list; raising it later spawns the rest on the next start. Lowering it removes nobody.
- **Fixed posts.** Shopkeepers and side-quest NPCs refuse sneaking interactions (carrying mods). If one dies (only `/kill` or creative can) or is carried away anyway (Easy Villagers picks up through its own packet), its site is freed and a new one takes the post; the carried copy, if placed later, fades on arrival (its UUID is no longer the site's). Natives and common villagers may be carried away: only their death brings a new one. No Solsticio villager converts (lightning, zombies).
- **Nothing ticks per villager in this code.** Statues have no brain. Restocks, native biome checks and settling happen on events: a right-click, a villager joining a level, and one box query of the trading hall every 100 ticks while a player is in Solsticio.

## Prices

- **Currency.** Emeralds up to 64, emerald blocks (9 emeralds) above. Everything is sold, nothing is bought: no shop turns items into emeralds, so there is no arbitrage loop, and prices sit above what vanilla villagers pay for the same items.
- **Restock.** Lazy: when someone opens a merchant's trades and `restockTicks` (default 24000, one day of game time) passed since the last one, its offers are rebuilt from the current table. Demand carries over as in vanilla (bought-out offers get dearer; demand kept in 0–50), so a `/reload` takes effect at the next restock. Settled villagers get `villager.restock()` on the same schedule, on top of vanilla's job-site restocks.
- **Multipliers** (server config `entrelumen-commerce-server.toml`), applied to the first cost when a player starts trading, on top of vanilla reputation and Hero of the Village, and undone when trading stops:

| Who | Multiplier |
| --- | --- |
| Shopkeeper | 1, or `liberatedPriceMultiplier` (0.6) once liberated |
| Native, before reaching its biome | 1 (its Luminosity is closed) |
| Native, awake | `nativeHomePriceMultiplier` (0.5), times 0.6 once liberated |
| Settled villager | `settledPriceMultiplier` (0.5), times 0.6 once liberated |

  Rounded to the nearest item, never below 1, and always at least one item cheaper when a multiplier applies (so the discount shows even on small prices). The second cost (books, compasses, catalysts) is never discounted.
- **Liberation.** `SolsticioData.liberated`, set by the final quest through `SolsticioCommerce.setLiberated(server, true)` (future work) and by operators with `/entrelumen admin solsticio liberated <true|false>`. A city reset keeps it.
- **Acts.** Offers may carry a gate (`{"act": 5}` or `{"act": 6, "milestone": "last_horizon"}`, the same `ActGate` as the protection). A player whose campaign has not reached it sees the offer out of stock while trading (and an action-bar hint); the offer is restored when trading stops. This matters for teams that reach the city through the open portal or the global waystone before their own Ark.

## Tables

`data/entrelumen/solsticio_shops/<type>.json`, reloaded with `/reload`:

```json
{
  "profession": "minecraft:librarian",
  "villager_type": "minecraft:plains",
  "gate": {"act": 3},
  "offers": [
    {"sell": {"id": "minecraft:enchanted_book", "enchantments": {"minecraft:mending": 1}},
     "price": 40, "extra": {"id": "minecraft:book", "count": 1}, "max_uses": 2},
    {"sell": {"id": "minecraft:elytra"}, "price": {"id": "minecraft:emerald_block", "count": 32},
     "max_uses": 1, "gate": {"act": 6}},
    {"sell": {"tag": "c:music_discs"}, "price": 24, "max_uses": 1}
  ]
}
```

- `sell`: an item id, or `{id, count, components, enchantments, survey, lore_book}` (`components` is the 1.21 data-component JSON; `enchantments` goes into stored enchantments on books). `{"tag": ...}` makes one offer per tagged item the table does not already sell.
- `price`: a number of emeralds or `{id, count}`; `extra`: an optional second cost. `max_uses` 1–128 (default 4), `price_multiplier` (vanilla demand factor, default 0.05), `xp`, `gate` (the table's `gate` is the default).
- Offers whose item or enchantment is not in the instance are skipped (logged once), so the same tables serve the full pack and the isolated test server. Every modded id was checked against the pinned catalog JARs (65 ids, `catalog/curated.json` of main, JARs from `server-mods-r123/mods` and `catalog-downloads`), every vanilla id against the 1.21.1 client-extra JAR.
- **Survey maps.** Blank maps carrying a structure tag. Used in the Overworld they chart the nearest structure of that tag around the player, like a vanilla explorer map (100 chunks, skipping claimed ones); elsewhere, or with nothing in reach, they stay blank. `on_jungle_temple_maps` and `on_swamp_hut_maps` (vanilla has them only in the experimental trade-rebalance pack; they include YUNG's temples and witch huts as optional entries) and `on_ancient_city_maps` are Entrelumen tags.
- **Lore books.** Written books whose pages are translation keys (`entrelumen.solsticio.lore.<id>.<n>`), so each reader sees their language.

| Shop | Keeper (EN / ES) | Profession | Offers | What stands out | Gated |
| --- | --- | --- | --- | --- | --- |
| `bookstore` | Lucerna, Bookseller / la librera | librarian | 24 | Enchanted books at vanilla maximum: **Mending** 40 + book, Unbreaking III, Efficiency V, Fortune III, Silk Touch, Looting III, Protection IV, Sharpness V, Power V, Infinity, Frost Walker II, trident and fishing books | Soul Speed III (3), Swift Sneak III and Wind Burst III (5) |
| `rarities` | Cenit, Dealer in Rarities / comerciante de rarezas | weaponsmith | 8 | Elytra 32 blocks, totem 12, 2 shulker shells 6, heart of the sea 10, trident 12, netherite scrap 4, echo shard 2, 2 breeze rods 2 (blocks) | elytra (6); totem, shells (5); heart, trident, scrap, breeze rods (4); echo shard (3) |
| `parts` | Brasa, Parts Dealer / repuestos | toolsmith | 33 | Create precision mechanisms, electron tubes, sturdy sheets; Mekanism circuits and alloys; IE components and circuit boards; AE2 processors; PneumaticCraft PCBs; Powah capacitors; IF and RFTools frames; flux cores; LaserIO logic chips; Sophisticated upgrade bases; Actually Additions coils | whole shop (3); elite and ultimate circuits, reinforced alloy, advanced electronics, engineering processors, blazing capacitors, advanced frame, flux cores (4–5) |
| `seeds` | Albor, Seed Merchant / semillero | farmer | 24 | Torchflower seeds and pitcher pods (sniffer only), nether wart, cocoa, glow berries, fungi, Farmer's Delight and Supplementaries seeds | chorus flowers (5) |
| `smithy` | Fulgor, Smith / el herrero | armorer | 19 | Netherite upgrade 48, all 18 armor trims (silence 64, ward and spire 40) | netherite upgrade (4), spire (5) |
| `apothecary` | Candela, Apothecary / boticaria | cleric | 19 | Long and strong potions, splash healing II, the unobtainable Luck potion, golden apples, ghast tears, rabbit feet, phantom membranes, scutes, experience bottles | enchanted golden apple 16 blocks (5) |
| `maps` | Rumbo, Cartographer / cartógrafo | cartographer | 12 | Survey maps (monument, mansion, trial chambers, jungle temple, swamp hut, buried treasure, ancient city) + compass; Explorer's and Nature's Compass; spyglass; recovery compass; lodestone | mansion, Explorer's Compass (3); ancient city, recovery compass (4); lodestone (5) |
| `minerals` | Geoda, Mineral Dealer / minerales | mason | 19 | Budding amethyst 24 blocks, reinforced deepslate 8 blocks, calcite, dripstone, crying obsidian, gilded blackstone, sculk catalyst, sky stone, charged certus, rose quartz, fluorite | budding (5), reinforced deepslate and catalyst (4), gilded blackstone (3) |
| `records` | Cadencia, Record Keeper / discos | librarian | 20 | All 19 vanilla discs (16–32) and every other `c:music_discs` item (24) | — |
| `textiles` | Hilaria, Weaver / tejedora | shepherd | 20 | All eight banner patterns, six wool colours, cobweb, string, leather, rabbit hide, Farmer's Delight canvas, flax | skull and piglin patterns (3), Mojang pattern (4) |
| `nursery` | Retoño, Nurseryman / viverista | farmer | 33 | Every sapling, azaleas, spore blossom, dripleaves, mycelium, podzol, grass, flowers, rainbow oak sapling, Pam's fruit trees | spore blossom, rainbow oak (3) |
| `creatures` | Fauna, Animal Keeper / criadora | leatherworker | 14 | Name tags, saddles, horse and wolf armour, sniffer egg, turtle eggs, axolotl, tadpole and tropical fish buckets, **a blue axolotl** (48) | diamond horse armour, sniffer egg (3) |
| `museum` | Memoria, Curator / curadora | cartographer | 29 | Five Heliodor lore books (EN/ES), all 23 pottery sherds, a brush | — |
| `bakery` | Miga, Baker / panadera | farmer | 12 | Bread, cake, pies, cookies, golden carrots and Farmer's Delight sweets: food variety for the satiety system | — |
| `apiary` | Melisa, Beekeeper / apicultora | farmer | 10 | Honey, honeycomb, blocks, beehive, **a bee nest with three bees**, candles, Productive Bees cages, treats and wax | bee nest (3) |
| `curiosities` | Quimera, Curio Dealer / curiosidades | fletcher | 23 | All eight goat horns, heavy core, trial and ominous keys, ominous bottle, light blocks, dragon and mob heads, nautilus shells, Artifacts' whoopee cushion, drinking hat, umbrella and cloud in a bottle | heavy core, dragon head, cloud in a bottle (5); ominous key and bottle, light (4) |

Never sold (a JUnit test enforces it): emeralds, nether stars, dragon eggs, sponges, beacons, wither skulls, spawners and spawn eggs, the Luminous ingot and its three rare ingredients, Light Keys, the portal relics, and any Luminosity outside its native.

## Natives and Luminosities

`data/entrelumen/solsticio_natives/<discipline>.json`: the shop format plus `biomes` (ids or `#tags`, any of which counts) and a `luminosity` offer. A native wakes the first time it stands in one of its biomes in the Overworld, checked at its block position when it is placed there (joins the level) or right-clicked. It stays awake for good, also back in Solsticio: its Luminosity is on sale and all its offers take the home multiplier. Asleep, it repeats where it wants to go whenever it is right-clicked, and its Luminosity shows out of stock. Players carry natives with Easy Villagers (in the pack), a minecart or Carry On.

| Discipline | Native (EN / ES) | Asks for | Why | Outfit | Luminosity |
| --- | --- | --- | --- | --- | --- |
| Engineering (copper-orange) | Ignea, Native of Engineering / nativa de la ingeniería | Badlands (`#minecraft:is_badlands`) | Layered orange earth, gold and mineshafts: the colour and the mines of engineering | toolsmith, desert | 16 → 8 emerald blocks + 8 copper blocks |
| Arcane (violet) | Arcadio, Native of the Arcane / nativo de lo arcano | Swamp or Mangrove Swamp | Witch huts and their purple robes, brewing | cleric, swamp | 8 blocks + 16 amethyst shards |
| Nature (green) | Silvano, Native of Nature / nativo de la naturaleza | Jungle (`#minecraft:is_jungle`) | Where nothing stops growing | farmer, jungle | 8 blocks + 16 moss blocks |
| Exploration (sky blue) | Brisa, Native of Exploration / nativa de la exploración | Jagged, Frozen or Stony Peaks | The top of the world under the sky, like the act IV cliff observatory | cartographer, snow | 8 blocks + 8 prismarine crystals |
| Logistics (turquoise) | Marea, Native of Logistics / nativa de la logística | Warm, Lukewarm or Deep Lukewarm Ocean | Turquoise water where ships met; the player builds it a dock | fisherman, savanna | 8 blocks + 8 ender pearls |
| Habitation (warm gold) | Solana, Native of Habitation / nativa de la habitabilidad | Sunflower Plains | Golden fields where every house faces the sun | shepherd, plains | 8 blocks + 8 honeycombs |

Each Luminosity: up to 4 per restock, 16 emerald blocks nominal, 8 once awake (5 once also liberated), plus its catalyst. None of the six biomes is rare enough to demand a long expedition, and Nature's Compass is in the pack. Each native also sells three discipline goods (redstone blocks, experience bottles, bone meal, flight-3 rockets, hoppers, lanterns...).

## Settled villagers

A villager without a Solsticio role that is inside the trading hall zone becomes a settled resident: when it joins Solsticio there (for example placed from an Easy Villagers item) or in the hall scan every 100 ticks while players are in Solsticio. Babies settle when they grow up. It keeps its profession and trades, gets the settled multiplier forever (wherever it trades later) and lazy restocks. Nearby players see "X has settled in Solsticio".

## Side quests, common villagers and easter eggs

- Four innkeeper personas (Dorotea, Tobías, Amparo, Ciro), assigned by the numeric order of the side-quest ids and stored on the NPC; each has a greeting that hints at a favour.
- **Hook for the quests:** `SolsticioCommerce.registerSideQuest("<id>", (player, npc, id) -> handled)`. Returning true takes the click; otherwise the NPC says its line. The quests themselves are not implemented.
- Common villagers say one of eight ambient lines, a couple of them the posgame rumours about the mayor's spending.
- Easter eggs are only recorded (`/entrelumen admin solsticio commerce` lists them) for future content.

## Commands and config

- `/entrelumen admin solsticio liberated [true|false]` shows or sets the liberation.
- `/entrelumen admin solsticio commerce` lists sites per role (spawned and pending), the hall, loaded tables and easter eggs; `... commerce populate` retries pending sites; `... commerce list` numbers every site and `... commerce respawn <index>` forgets a site's villager so a new one is spawned (for a native whose carrying item was lost; the old one, if it still exists, stays). `/entrelumen admin solsticio info` adds a commerce line.
- Server config `config/entrelumen-commerce-server.toml` (NeoForge 21.1 keeps server configs there; synced to clients): `residentCap` 40, `tradingHallRadius` 12, `restockTicks` 24000, `liberatedPriceMultiplier` 0.6, `settledPriceMultiplier` 0.5, `nativeHomePriceMultiplier` 0.5.

## Tests

- JUnit `CommerceRulesTest` (15): every table parses cleanly and covers the 16 types; the shops sell what Elias asked for (Mending, elytra at act 6, totems at act 5, netherite template and 18 trims, 19 discs plus the tag, surveys, lore books, 23 sherds); natives ask for distinct biomes and sell only their own Luminosity; nothing forbidden is sold and everything costs emeralds; parsing drops bad offers with reasons; multipliers, rounding (liberation always lowers any price from 2 up), vanilla-exact price adjustment with demand, reputation and clamping; lazy restock; cap spread; hall box; innkeeper order; sites from markers, kept UUIDs and round trip; `SolsticioData` keeps the liberation and the commerce. `CityLayoutTest.commerceMarkersCarryTheirArgument` covers the new marker syntax.
- GameTests `RuntimeGameTestsCommerce` (5, isolated server, fixture template `commerce_fixture` from `tools/build_commerce_fixture.py`, never shipped):
  - the fixture's markers go through the loader's partition and parser; three shopkeepers (no AI, invulnerable, persistent, Master, named), the Mending book and a survey map on sale, six natives on distinct spots with their Luminosity, the innkeeper's line and the side-quest hook, two common villagers under a cap of 2, the easter egg, the unknown marker ignored, and a second population spawns nothing;
  - the rarities keeper keeps elytra and echo shards closed at act 1 (restored after trading), sells the elytra at 32 blocks in act 6 and at 19 once liberated, and the discount ends with the trade;
  - a villager in a local hall settles (one outside does not) and trades at 10 instead of 20;
  - an arcane native sells nothing asleep in the plains, wakes in a swamp (biome filled by the test) with its Luminosity at 8 blocks and its bottles at 10, and stays awake back in the plains;
  - in the shared city, the population gives the real trading hall its six natives and a villager placed there settles at once.

## Pending

- The controller's definitive template with `shop:`, `sidequest:`, `resident` and `easter:` markers (the current `city.nbt` is draft 3, which only has `trading_hall`: a world placed with it gets the natives and nothing else). A world placed before the new template needs an explicit migration, as for the rest of the city.
- Side quests, easter-egg content and the final quest that liberates the Entrelumen (`setLiberated`).
- Heliodor clothing for all these villagers (today vanilla professions and biome outfits), and the innkeepers' own names per inn if the quests want them.
- In-game review on a client: trading screens, out-of-stock gated offers, survey map charting in a real world (not exercised by the flat test world), awakening particles, text EN/ES.
- Full-pack check: modded offers, Easy Villagers carrying natives and settling newcomers, Carry On refused on fixed NPCs, Jade tooltips.
- Balance playtest: emerald income against these prices and the Luminosity rhythm (54 for the gear, 48 for the creative items).
