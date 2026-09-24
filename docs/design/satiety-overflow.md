# Satiety overflow: surplus food becomes short buffs

Elias's direction (24 September 2026): eating varied food is rewarded, and food that would be wasted above the vanilla caps becomes random buffs that scale with the surplus. In vanilla, hunger stops at 20 and saturation stops at the hunger level, so anything above those caps is lost. See the "Estilo de juego" section of the [story bible](story-bible.md).

Variety stays the job of Spice of Life: Carrot Edition, which gives hearts for unique foods. The companion only rewards the *quantity* a meal loses to the caps. It never looks at which food was eaten.

## When it runs

`SatietyOverflowEvents` feeds the same formula, cooldown and glut from two native paths.

**Food eaten from the hand.** NeoForge's item-use events cover every food or drink that is eaten from the hand, whatever mod adds it. That includes Farmer's Delight meals, bowls and drinks, honey bottles and golden apples.

- On the last `LivingEntityUseItemEvent.Tick`, which fires in the same tick just before the food is applied, it records hunger and saturation. It runs at the lowest priority.
- On `LivingEntityUseItemEvent.Finish`, which fires after `FoodData.eat`, it takes the item's `FoodProperties` for that player and runs the formula below. It runs at the highest priority; see [Spice of Life](#spice-of-life-carrot-edition).
- If there is no snapshot from that same tick, the meal is skipped rather than guessed.

**Food eaten in place from a block** (added 24 September 2026). This covers vanilla cake and candle cakes, Farmer's Delight pies and cheesecakes, and any other block that feeds the player directly. These blocks call `FoodData.eat` from their `useWithoutItem` and fire no food event, and their values live in their code: cake is a literal `eat(2, 0.1)`, and a Farmer's Delight pie eats its slice item's `FoodProperties`. So the companion measures the bite itself instead of listing blocks:

- **Before the bite:** `PlayerInteractEvent.RightClickBlock`, at the lowest priority and only if nothing cancelled it (claim protection, for example), opens a *bite window* for that player.
- **During the bite:** `FoodDataMixin` injects at the head of the private `FoodData.add(int, float)`, the single funnel behind both `eat` overloads. While a window is open for that player's food data, it adds `overflow(hunger, saturation, nutrition, saturation gained)` using the values just before vanilla clamps them. That is exactly the formula below, even if a block feeds more than once in one click. Food data without an open window returns at once, so the hook costs one empty-map check.
- **After the bite:** the window closes at the next server tick boundary (`ServerTickEvent.Pre` or `Post`), when the player opens another window, or when an item meal starts its final use tick. The bite's surplus is then converted once, with the same glut and cooldown as item meals. An item meal is therefore never counted twice.

Vanilla only lets a player take a cake or pie bite below 20 hunger (`canEat(false)`). A cake slice at 19 hunger loses 1 point, below the 2-point floor, so on its own it only adds glut. A Farmer's Delight pie slice (3 hunger, 1.8 saturation) at 19 hunger and 19 saturation loses 2 + 0.8 = 2.8 points: one level-I buff at f = 0.14.

Not converted: players in creative or spectator mode, and fake players (Create deployers and similar). Farmer's Delight feasts are served into bowls and eaten from the hand, so they go through the item path.

Vanilla only lets a player eat below 20 hunger, except for always-edible foods (golden apples, chorus fruit, suspicious stew, some drinks) and the honey bottle, which is drunk regardless. In practice most surplus is saturation: a big meal eaten when not very hungry.

## Formula

Units are vanilla food points; one point is half a drumstick. *F* and *S* are the hunger and saturation before the meal; *n* and *s* are the food's nutrition and saturation (`FoodProperties.saturation()` = nutrition × modifier × 2).

1. **Surplus** P = clamp(F + n − 20, 0, n) + clamp(S + s − min(20, F + n), 0, s). The second term is saturation above the *new* hunger level. P is never more than the food provided, so foods with negative values yield nothing.
2. **Glut** G decays with a half-life of 6000 ticks (5 minutes): G′ = G · 0.5^(Δt / 6000).
3. **Worth** E = P · 20 / (20 + G′). Recent surplus makes each new point worth less.
4. G ← G′ + P. This happens for every meal with surplus, even during the cooldown and even when E is too small.
5. **Cooldown**: if a buff was granted less than 200 ticks (10 s) ago, nothing more happens.
6. If E < 2, nothing happens.
7. **Plan**:
   - count = min(3, 1 + ⌊E / 10⌋);
   - fraction f = min(1, E / 20);
   - level II when E ≥ 16 and the pool entry allows II, otherwise level I.
8. Each buff lasts min + (max − min) · f seconds of its pool entry.

Buffs are drawn from the pool by weight without replacement, among the entries that would **improve** what the player has:

- an absent effect;
- the same level with a longer duration (vanilla then extends it);
- a higher level.

A stronger level, an infinite effect (beacon-style or from another mod) or an equal-or-longer one is never touched. If fewer entries qualify than the plan asks for, fewer buffs are given. If none qualify, nothing is granted and no cooldown starts.

### Examples (no glut)

| Meal | Before (hunger / saturation) | P | E | Result |
|---|---|---|---|---|
| Bread (n 5, s 6) | 19 / 15, the fullest bar vanilla lets you eat bread at | 4 + 1 = 5 | 5 | One level-I buff at f = 0.25, e.g. Speed I 67.5 s or Regeneration I 8.75 s |
| Bread | 20 / 20 (only through always-edible rules or other mods) | 5 + 6 = 11 | 11 | Two level-I buffs at f = 0.55, e.g. Haste I 112.5 s + Absorption I 79.5 s |
| Farmer's Delight roast chicken (n 14, s 21) | 14 / 8 | 8 + 9 = 17 | 17 | Two buffs at f = 0.85, level II where allowed, e.g. Haste II 157.5 s + Strength I 79.5 s |
| Roast chicken | 20 / 20 | 14 + 21 = 35 | 35 | Three buffs at f = 1, e.g. Speed II 180 s + Absorption II 120 s + Resistance I 60 s |
| Golden carrot (n 6, s 14.4) | 16 / 10 | 2 + 4.4 = 6.4 | 6.4 | One level-I buff at f = 0.32, e.g. Water Breathing I 136.8 s |
| Golden carrot | 16 / 16 | 2 + 10.4 = 12.4 | 12.4 | Two level-I buffs at f = 0.62 |
| Cookie (n 2, s 0.4) | 19 / 19 | 1 + 0 = 1 | 1 | Nothing: below the 2-point floor |

Spam check: five honey bottles (n 6, s 1.2, drunk at any hunger) in ten seconds at 20 / 20. Each one is P = 7.2.

- The first grants one level-I buff (f = 0.36).
- The other four only add glut, which reaches about 35.7.
- The first bottle after the cooldown is worth E ≈ 7.2 · 20 / 55.5 ≈ 2.6: one level-I buff at f ≈ 0.13, for example Speed I for 50 s.
- After five idle minutes the glut has halved.

A good meal every few minutes keeps most of its value: 10 minutes after a 20-point meal the glut is 5, so the next meal is worth 80 %.

## Default pool

Ambient effects: translucent particles and the beacon-style icon frame.

| Effect | Weight | Duration (s) | Max level | Why |
|---|---|---|---|---|
| `minecraft:regeneration` | 8 | 5–20 | I | Heals at most 8 HP; a golden apple gives Regeneration II for 5 s |
| `minecraft:haste` | 12 | 30–180 | II | Work buff; common |
| `minecraft:speed` | 12 | 30–180 | II | Travel buff; common |
| `minecraft:strength` | 6 | 20–90 | I | Combat power; kept at I |
| `minecraft:absorption` | 8 | 30–120 | II | II is 8 HP, below an enchanted golden apple's IV |
| `minecraft:resistance` | 6 | 20–60 | I | 20 % reduction; II would be too strong |
| `minecraft:water_breathing` | 8 | 60–300 | I | Utility |
| `minecraft:night_vision` | 8 | 60–300 | I | Utility |
| `minecraft:luck` | 6 | 60–300 | II | Loot and fishing luck |

Hard caps enforced in code, which a datapack cannot exceed:

- level II;
- 600 seconds per entry;
- at most five buffs per meal (three by default).

Level III would need a code change and a written reason.

## Configuration (datapack)

`data/entrelumen/satiety/overflow.json` is shipped with exactly these defaults; a JUnit test compares the file with `SatietyOverflow.DEFAULTS`. A datapack that places a file at the same path replaces it, and `/reload` applies the change. Omitted keys keep their defaults; an omitted `pool` keeps the default pool.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `full_points` | 20 | E at which durations reach their maximum |
| `min_points` | 2 | Floor below which nothing is granted |
| `extra_buff_every` | 10 | One more buff per this many points of E |
| `max_buffs` | 3 | Buffs per meal (1–5) |
| `level_two_at` | 16 | E for level II on entries that allow it |
| `glut_softness` | 20 | K in E = P · K / (K + G) |
| `glut_half_life_ticks` | 6000 | Glut half-life |
| `cooldown_ticks` | 200 | Minimum time between grants |
| `announce` | `true` | Action-bar message and particles |
| `pool[]` | see above | `effect`, `weight` ≥ 1, `min_seconds` ≥ 1, `max_seconds` ≤ 600, `max_level` 1–2 |

Validation:

- Pool entries whose effect is absent, instant or harmful are skipped with a warning, so a pool may name effects from optional mods.
- A duplicate effect, level III, an out-of-range number or a fractional tick count rejects the whole file. The error is logged and the previous settings are kept, so the server never fails to start or reload.

## Feedback

When at least one buff is granted, the player sees one action-bar line, `entrelumen.satiety.overflow`, listing the buffs ("Surplus satiety: Haste II, Speed" / "Saciedad sobrante: Prisa II, Velocidad"). Five end-rod particles appear at head height. Effect names and levels come from vanilla translations. There is no chat message. The 10-second cooldown also limits messages to one per window.

## Spice of Life: Carrot Edition

The pinned `solcarrot-1.21.1-1.16.6.jar` was inspected. `FoodTracker.onFoodEaten` listens to the same `Finish` event, reads `getFoodProperties(player)`, records unique foods and updates max health.

Its server config has only these settings:

- milestones: `baseHearts`, `heartsPerMilestone`, `milestones`;
- filtering: `blacklist`, `whitelist`, `minimumFoodValue`;
- miscellaneous: `resetOnDeath`, `limitProgressionToSurvival`.

The pack sets them in `pack/defaultconfigs/solcarrot-server.toml`: one heart at 10/25/45/70/100 unique foods, no reset on death.

Spice of Life has **no penalty** for repetition or for eating when full, so nothing is duplicated. The only anti-spam rule in the pack is the glut and cooldown here, and it does not depend on the food. The companion never changes food values, so Spice of Life's tracking is untouched.

When Spice of Life records a new food it shows its progress above the hotbar. The overflow handler runs first on `Finish`, so in that tick Spice of Life's message is the one left on screen. The effect icons and particles still show the buffs.

## State

Glut, its timestamp and the last grant live in the player's NeoForge persistent data, under `entrelumen_satiety` with the keys `glut`, `glut_at` and `granted_at` (game-time ticks). They survive relogs. Death clears them, which only means the next meal is worth its full value. The pre-meal snapshot and the open bite windows live in memory for at most one tick and are dropped on logout.

## Code and verification

- `SatietyOverflow` (pure rules and parsing) and `SatietyOverflowEvents` (events, bite windows, reload listener, effects and message), wired by one line in `Entrelumen`.
- `mixin/common/FoodDataMixin`, in its own config `entrelumen.common.mixins.json` (required, `defaultRequire` 1), declared in `neoforge.mods.toml` beside the existing client config.
- JUnit `SatietyOverflowTest`, eight cases:
  - overflow arithmetic and negative foods;
  - every documented example;
  - caps for any surplus;
  - glut decay and spam;
  - never replacing stronger or infinite effects;
  - weighted draw without repeats;
  - the shipped file equals the defaults;
  - datapack validation and hard caps.
- Isolated GameTests in `RuntimeGameTestsGameplay`. They eat through `EventHooks.onItemUseTick` → `finishUsingItem` → `EventHooks.onItemUseFinish`, the same order as `LivingEntity.completeUsingItem`.
  - `satietyOverflowAtAFullBarTurnsSurplusIntoBuffs`:
    - the datapack loads the defaults;
    - a hungry meal gives nothing;
    - bread at 20 / 20 gives exactly two ambient level-I pool buffs with formula durations, and records glut and cooldown;
    - a Finish without a snapshot is ignored.
  - `satietyOverflowCooldownAndGlutStopCheapSpam`:
    - a second meal inside the cooldown grants nothing but adds glut;
    - after the cooldown the buff is shortened by exactly 20 / (20 + 22);
    - glut halves over one half-life.
  - `satietyOverflowNeverReplacesStrongerEffects`:
    - Speed II and an infinite Haste are untouched and get no hidden effect;
    - a shorter Luck I is extended;
    - a player whose effects are all stronger gets nothing and no cooldown.
  - `satietyOverflowCountsBitesEatenFromBlocks`, which bites through the real `ServerPlayerGameMode.useItemOn` path (event, block, mixin):
    - vanilla cake at a full bar is refused and nothing is measured;
    - a cake bite at 19 / 19 measures exactly 1 point (glut only, no buff);
    - a fixture block shaped like the Farmer's Delight pie bite (a slice's `FoodProperties` through `FoodData.eat`, nothing special-cased) gives exactly one level-I pool buff for 2.8 points;
    - a right-click on stone followed by bread in the same tick counts the bread once (glut 11);
    - a bite right after an item meal shares its cooldown and adds its glut (13.8);
    - an unclosed window is closed by the tick boundary.
- Full-pack GameTest `CookingProvisionsGameTests.farmersDelightPieBiteCountsAsSatietySurplus` is written and **pending**. It bites the real `farmersdelight:apple_pie` at 19 / 19 and expects 2.8 points of glut.
- Not verified yet: a client session (action bar, particles, EN/ES rendering) and survival pacing on the installed pack with Farmer's Delight and Spice of Life loaded.
