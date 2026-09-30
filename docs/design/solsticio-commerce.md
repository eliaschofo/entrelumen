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

- **Currency.** Emeralds up to 64, emerald blocks (9 emeralds) above. Everything is sold, nothing is bought: no shop turns items into emeralds, so there is no arbitrage loop, and prices sit above what vanilla villagers pay for the same items. The one exception is Cenit, who also takes the Envés's sour light shards for seven offers ([below](#sour-light-shards-30-september-2026)); shards never turn into emeralds either.
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
- **Acts.** Offers may carry a gate (`{"act": 5}` or `{"act": 6, "milestone": "last_horizon"}`, the same `ActGate` as the protection). A player whose campaign has not reached it sees the offer out of stock while trading (and an action-bar hint); the offer is restored when trading stops. This matters for teams that reach the city through the open portal or the global waystone before their own Ark. Since the renumbering of 24 September 2026 ([act-renumbering.md](act-renumbering.md)) act 5 is the whole Ark act (the old act 5 was its first half, so gates at 3–5 keep their meaning) and act 6 is Solsticio, reached with the team's own activation. The elytra stay at act 6 on purpose: a team building its Ark already goes to the End and can find elytra in End cities; the shop's copy is the reward of its own crossing, not a shortcut for visitors.

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
| `rarities` | Cenit, Dealer in Rarities / comerciante de rarezas | weaponsmith | 15 | Elytra 32 blocks, totem 12, 2 shulker shells 6, heart of the sea 10, trident 12, netherite scrap 4, echo shard 2, 2 breeze rods 2 (blocks); and, for **sour light shards**, gem dust, rare and epic materials, three Apotheosis sigils and a totem (7 offers, 16 to 56 shards) | elytra (6); totem, shells (5); heart, trident, scrap, breeze rods (4); echo shard (3); every shard offer (6) |
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

## Sour light shards (30 September 2026)

Branch `feature/solsticio-shard-trades`. The Envés's currency ([dungeon-enves.md](dungeon-enves.md)) gets its sink. Elias, 27/9: the shards «también se tradean en Solsticio por cosas (mucho más adelante) así que tiene sentido que sobren para que la ofrenda sea más que nada inicial». Elias, 29/9, on the Luminosities: «no tienen otro método de conseguir que no sea tradeándolas (son baratas y no necesitás muchas...)».

### Who trades: Cenit, no new stall

The city template has 16 stalls, one `shop:` marker each, and it belongs to the controller: a 17th trader would need a marker that `city.nbt` does not have. Cenit («rarezas») is the fit. The Envés's loot is Apotheosis rarities, and he already sells what a deep dungeon is measured by (elytra, totems, shulker shells, echo shards). His table takes shards at the end of its list; the eight emerald offers he had do not change.

### The table

Seven offers in `rarities.json`, priced in `entrelumen:sour_light_shard`. A price is one item slot, so 64 is the ceiling (also the Envés's door). Every offer is gated at **act 6**, like the elytra, and has `price_multiplier` 0 so demand never moves it. The liberation still takes its 40% off (rounding as everywhere: never below one, always at least one less).

| Trade | Shards | Liberated | Uses per restock | Why |
| --- | ---: | ---: | ---: | --- |
| 8 gem dust | 16 | 10 | 16 | Every purity step of the gem cutting table asks dust (1, 3, 5, 7, 9): 25 for the whole climb, 50 shards at two apiece. |
| 4 luminous crystal shards (rare material) | 32 | 19 | 8 | 8 each. The cutting table asks 1 to 27 per step; the flawless step asks 27. |
| 2 arcane sands (epic material) | 40 | 24 | 8 | 20 each, two and a half rares: epic is where the Envés's chests thin out. The flawless step asks 9. |
| 3 Sigils of Rebirth | 48 | 29 | 8 | 16 each. The Reforging Table's fuel; Apotheosis crafts them six at a time from slate and dust. |
| 1 Sigil of Socketing | 56 | 34 | 4 | The gear lever players chase most: one more socket. Priced above the fuel. |
| 1 Sigil of Withdrawal | 40 | 24 | 4 | Takes the gems out to try others, so gems stop being a one-way road. |
| 1 totem of undying | 54 | 32 | 2 | The dungeon's second life. Cenit's emerald totem (12 blocks, act 5) is worth 54 shards at the rate below. |

Buying the whole list once costs **286 shards** (172 liberated). Ids and recipes checked against the pinned `Apotheosis-1.21.1-8.7.0.jar` (items, `rarities/*.json` materials, gem cutting and sigil recipes). If Apotheosis is absent its offers are skipped like any missing item; the totem remains.

### Why these prices

- **The rate.** A repeat full descent nets about 141 shards (below), and an elytra costs 32 emerald blocks (288 emeralds): **one shard is about two emeralds, one full descent about one elytra.** The totem, the only item with an emerald twin, sits exactly on that rate. The rest are rungs of the reforging loop: dust 2, rare material 8, epic 20, fuel 16, sockets 56.
- **Fuel, not power.** Shards buy more of what the Envés already drops (dust, materials) and the levers Apotheosis players use to steer it (sigils). The surplus turns a random loop into a chosen one, which is what a currency is for in a roguelite. Nothing is a stat stick, and nothing skips a gate.
- **The ladder is the sink.** The top gem step needs 27 rare materials, 9 epic and 9 dust: about 414 shards (216 + 180 + 18), three repeat descents, for one gem. The three godforged pearls it also asks stay unsold (mythic material, an ingredient of the Luminous ingot). So players who cut gems keep spending; players who do not have a small, finite shopping list.
- **Late.** Every offer waits for act VI. The Envés opens with Frontier (act III), so a team arrives in Solsticio with the surplus of acts III to V already in its pockets and had nothing to spend it on before.
- **Generous uses.** A merchant's uses are shared by everyone who trades with it, so the caps are high on purpose: the price is the throttle, not the daily cap. The table stays far from making the door's 64 shards hurt again.
- **No loop.** Nothing buys shards back and shards never become emeralds. The totem costs the same in both currencies at the rate above, so neither is the better road.

### Against what the Envés drops

Expected shards per descent, from `EnvesContentDataTest` (200 descents; the group crosses 70% of the rooms and solves three of four vaults; one player):

| | Shards |
| --- | ---: |
| Floor I | 19 |
| Floors I to III | 80 |
| Full descent, first boss | 141 |
| Full descent, repeat boss (the chest gives 64 more instead of the Sour Shackle) | 205 |
| The door | -64 |
| **Net of a first full descent / of a repeat** | **77 / 141** |

- The whole table once is 286 shards: two repeat descents, or 1.2 at liberated prices. One repeat descent alone pays a totem, a socket sigil and three rebirth sigils at liberated prices (95) and leaves change.
- Five full descents across acts III to V bring about 640 shards (77 + 4 x 141): two trips through the table. In a group each player has their own Lootr chests, so the chest shards multiply with the party and the echoes' stay shared.
- Floors I to III barely pay the door (80 against 64), as designed: the surplus is the reward for going deeper, and it is not enough to make the door trivial or the shards worthless.

### What it keeps

- **The Wither stays the only source of Nether stars.** No offer sells a star, a beacon or a wither skull; the door's «star or 64 shards» is untouched.
- **Act gates.** Each shard offer carries act 6 in the same `ActGate` as the elytra.
- **Luminosities only by trading with their native.** Shards buy no Luminosity, no Luminous ingot and none of its three rare ingredients.
- **Never sold** is unchanged. `CommerceRulesTest` now also enforces the shard table: only Cenit takes shards, one stack at most, act 6 or later, no second cost, flat price, exactly these seven items, and no native takes them.

### Para decidir (Elias)

The defaults are the safe side; each is a small data change.

1. **Shards for Luminosities.** Default: no. The six natives keep asking emeralds plus a catalyst, and the biome trip stays the only road (Elias, 29/9). If yes: a second Luminosity offer per native at **64 shards** (32 awake, 19 once liberated) plus the same catalyst, still asleep until the native has been to its biome. The 54 Luminosities of a gear set plus the 48 of the creative items would then be a real shard sink (about 1,000 shards a set). The catch: «son baratas» would mean one run instead of emerald blocks.
2. **A second Sour Shackle.** Default: no («uno solo, con un efecto que se note», 29/9). A player who loses the Grillete cannot get another, because the mark stays. A replacement at 64 shards would be the answer; it is left out until Elias says so.
3. **More power for shards.** Default: no. Sigils of Supremacy (overcharges every affix) and Malice, the Invader Summoner and strong Artifacts curios were left out because they are power, not fuel: each would make the Envés the best road to top gear.
4. **The rate.** One shard about two emeralds, the totem at 54, the ceiling of 64 a trade. If the table should weigh more, scale the prices; the limits are the ceiling and the balance between the reforge rungs.
5. **Liberation discount on shards.** Default: yes, they are a shop price like any other. To make shards hold their price, `SolsticioCommerce` would have to skip non-emerald costs.
6. **Shared uses.** On a big server the sockets' and the totem's daily uses may run out. Raise `max_uses` (128 at most) if the playtest shows it.

### Text and quest

- The shard's tooltip gets a third dark-grey line, `item.entrelumen.sour_light_shard.trade` (EN/ES): «Late in the game, Cenit in Solsticio trades them for rarities.» / «Más adelante, Cenit, en Solsticio, las cambia por rarezas.»
- The Solsticio guide (`content/guides/guide_entrelumen_solsticio.json`) gets an optional node after the shops, `entrelumen_solsticio_shards` («What the Envés Leftovers Buy» / «En qué se gastan las sobras del Envés»), and the book is regenerated.

### Tests

- JUnit `CommerceRulesTest.sourLightShardsBuyOnlyTheWaitingRaritiesAtCenit`; the emerald-only assertion now names Cenit as the one exception.
- GameTest `RuntimeGameTestsCommerce.cenitTakesSourLightShardsOnlyFromActSix`: the shard totem is closed at act 5 while the emerald totem is open, open at act 6 at its nominal price and paid with exactly its shards, and cheaper once liberated. **Pending**: GameTests go to the end of the project (Elias, 29/9), so it only compiles today.

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
- **Hook for the quests:** `SolsticioCommerce.registerSideQuest("<id>", (player, npc, id) -> handled)`. Returning true takes the click; otherwise the NPC says its line. Since act VI ([act-six.md](act-six.md), 24 September 2026) every inn's hook is `SolsticioStory`: two errands per innkeeper that build the team's relation with Bodhi. Visitors still hear the greeting.
- Common villagers say one of eight ambient lines, a couple of them the posgame rumours about the mayor's spending. After the liberation and after the elections they draw from two more pools of eight. The resident nearest the cartographer becomes Anselmo, the old neighbour of the map errand.
- Easter eggs are recorded here and discovered by walking in (act VI): a lore page and a keepsake once per team, and the three together reveal the rumour.
- The four named characters (Aurelia, Terra, Juan, Bodhi) are sites of role `character` on the `mayor`, `inventor`, `gardener` and `priest` markers: statues bound to their post, no trades, act VI's dialogue. Cities placed earlier get them on the next start.

## Commands and config

- `/entrelumen admin solsticio liberated [true|false]` shows or sets the liberation.
- `/entrelumen admin solsticio commerce` lists sites per role (spawned and pending), the hall, loaded tables and easter eggs; `... commerce populate` retries pending sites; `... commerce list` numbers every site and `... commerce respawn <index>` forgets a site's villager so a new one is spawned (for a native whose carrying item was lost; the old one, if it still exists, stays). `/entrelumen admin solsticio info` adds a commerce line.
- Server config `config/entrelumen-commerce-server.toml` (NeoForge 21.1 keeps server configs there; synced to clients): `residentCap` 40, `tradingHallRadius` 12, `restockTicks` 24000, `liberatedPriceMultiplier` 0.6, `settledPriceMultiplier` 0.5, `nativeHomePriceMultiplier` 0.5.

## Tests

- JUnit `CommerceRulesTest` (16): every table parses cleanly and covers the 16 types; the shops sell what Elias asked for (Mending, elytra at act 6, totems at act 5, netherite template and 18 trims, 19 discs plus the tag, surveys, lore books, 23 sherds); natives ask for distinct biomes and sell only their own Luminosity; nothing forbidden is sold and everything costs emeralds, except Cenit's seven shard offers (checked by a test of their own: one stack, act 6, flat, no Luminosity); parsing drops bad offers with reasons; multipliers, rounding (liberation always lowers any price from 2 up), vanilla-exact price adjustment with demand, reputation and clamping; lazy restock; cap spread; hall box; innkeeper order; sites from markers, kept UUIDs and round trip; `SolsticioData` keeps the liberation and the commerce. `CityLayoutTest.commerceMarkersCarryTheirArgument` covers the new marker syntax.
- GameTests `RuntimeGameTestsCommerce` (5, isolated server, fixture template `commerce_fixture` from `tools/build_commerce_fixture.py`, never shipped):
  - the fixture's markers go through the loader's partition and parser; three shopkeepers (no AI, invulnerable, persistent, Master, named), the Mending book and a survey map on sale, six natives on distinct spots with their Luminosity, the innkeeper's line and the side-quest hook, two common villagers under a cap of 2, the easter egg, the unknown marker ignored, and a second population spawns nothing;
  - the rarities keeper keeps elytra and echo shards closed at act 1 (restored after trading), sells the elytra at 32 blocks in act 6 and at 19 once liberated, and the discount ends with the trade;
  - a villager in a local hall settles (one outside does not) and trades at 10 instead of 20;
  - an arcane native sells nothing asleep in the plains, wakes in a swamp (biome filled by the test) with its Luminosity at 8 blocks and its bottles at 10, and stays awake back in the plains;
  - in the shared city, the population gives the real trading hall its six natives and a villager placed there settles at once.
- Manual run (not committed) with the controller's draft 6 exported from main (`city6.py`, 250 × 189 × 239, 557,413 blocks) swapped in for `city.nbt` on a fresh test world: all markers parse (16 shops, 4 inns, 56 homes, 3 easter eggs, the civic markers); the population spawned 66 villagers (16 shopkeepers, 4 innkeepers, 40 of 56 homes, 6 natives) in 8.3 s wall, none failed, no lag warning. The city itself took 23 s wall to place, so two existing Light Key GameTests with an 800-tick timeout ran out of time: their timeouts need raising when draft 6 replaces `city.nbt`.

## Pending

- The controller's definitive template with `shop:`, `sidequest:`, `resident` and `easter:` markers (the current `city.nbt` is draft 3, which only has `trading_hall`: a world placed with it gets the natives and nothing else). A world placed before the new template needs an explicit migration, as for the rest of the city.
- ~~Side quests, easter-egg content and the final quest that liberates the Entrelumen (`setLiberated`)~~: done in act VI ([act-six.md](act-six.md)). The first team to open the portal calls `setLiberated`, which now also records `liberatedAt` for the elections.
- ~~Heliodor clothing~~: done on 24 September. `entrelumen:heliodor` is a registered villager type (texture `entrelumen:textures/entity/villager/type/heliodor.png`, `art/authoring/draw_villager_heliodor.py`) worn by every villager the city spawns, shopkeepers and natives included: the tables' `villager_type` no longer applies to the city's own merchants, only their profession does. Villagers placed before are re-dressed when they load; settled villagers from elsewhere keep their clothes. Pending art: the four characters' distinctive pieces (profession textures, transparent placeholders today; see act-six.md).
- In-game review on a client: trading screens, out-of-stock gated offers, survey map charting in a real world (not exercised by the flat test world), awakening particles, text EN/ES.
- Full-pack check: modded offers, Easy Villagers carrying natives and settling newcomers, Carry On refused on fixed NPCs, Jade tooltips.
- Balance playtest: emerald income against these prices and the Luminosity rhythm (54 for the gear, 48 for the creative items), and the shard table against a real Envés stash ([shards](#sour-light-shards-30-september-2026)).
- The shard GameTest `cenitTakesSourLightShardsOnlyFromActSix` has not run (tests go to the end of the project).
