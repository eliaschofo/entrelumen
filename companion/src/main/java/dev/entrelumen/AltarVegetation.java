package dev.entrelumen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.neoforged.neoforge.common.Tags;

/**
 * What the Altar of Renewal grows in each biome: its trees (the biome's own tree features, with
 * curated variants such as giant cacti in deserts, mega spruces in taigas, mangroves in their
 * swamps, huge fungi in the Nether and chorus in the End), its flowers and ground plants (the
 * biome's own patches), and the dye garden, the same in every biome, whose harvest crafts all
 * sixteen dyes. Palettes are resolved once per biome from the loaded registries and cached.
 */
final class AltarVegetation {
  /** The ground a tree option needs under its trunk. */
  enum Soil {
    DIRT(state -> state.is(BlockTags.DIRT)),
    NYLIUM(state -> state.is(BlockTags.NYLIUM)),
    END_STONE(state -> state.is(Tags.Blocks.END_STONES)),
    SAND(state -> state.is(BlockTags.SAND) && !state.hasBlockEntity());

    private final Predicate<BlockState> test;

    Soil(Predicate<BlockState> test) {
      this.test = test;
    }

    boolean accepts(BlockState ground) {
      return test.test(ground);
    }
  }

  /** A tree the altar can grow: a configured feature, or a giant cactus built by the altar itself. */
  record TreeOption(String id, Holder<ConfiguredFeature<?, ?>> feature, boolean giantCactus, double weight,
      Soil soil, boolean cocoa) {}

  /** Everything the altar grows in one biome. */
  record Palette(String variant, List<TreeOption> trees, double treesPerChunk, List<BlockStateProvider> flowers,
      List<BlockStateProvider> grasses, boolean lilyPads) {
    double treeProbability() {
      return trees.isEmpty() ? 0 : VegetationRules.treeProbability(treesPerChunk);
    }

    /** The options that can grow on this ground, in order. */
    List<TreeOption> treesFor(BlockState ground) {
      List<TreeOption> fit = new ArrayList<>();
      for (TreeOption option : trees) if (option.soil().accepts(ground)) fit.add(option);
      return fit;
    }
  }

  /**
   * The dye garden: one species per primary colour, the same in every biome. Vanilla gives white
   * (lily of the valley), red (poppy), yellow (dandelion) and blue (cornflower). No vanilla flower
   * gives brown, green or black without harm, so the pinned mods fill them: Eternal Starlight's
   * conebloom (brown dye) and swamp rose (green dye), both plain flowers on any dirt, and The
   * Undergarden's ink mushroom (black dye). The ink mushroom only lives in shade or on mycelium,
   * podzol or nylium, so the altar also grows four huge ink mushrooms, whose caps drop ink mushrooms.
   */
  record Garden(List<BlockState> species, Holder<ConfiguredFeature<?, ?>> inkCap) {}

  /** Garden species in bed order, one per primary; a mod that is not loaded leaves its colour out. */
  static final List<String> GARDEN = List.of("minecraft:lily_of_the_valley", "minecraft:poppy", "minecraft:dandelion",
      "minecraft:cornflower", "eternal_starlight:conebloom", "eternal_starlight:swamp_rose", "undergarden:ink_mushroom");
  static final String INK_CAP = "undergarden:huge_ink_mushroom";
  /** Blocks the altar never grows as flora: they hurt, or they are food. Data-driven, extensible. */
  static final TagKey<Block> EXCLUDED = BlockTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen",
      "renewal_altar_excluded_plants"));

  private static final Map<ResourceKey<Biome>, Palette> PALETTES = new ConcurrentHashMap<>();
  private static volatile RegistryAccess cachedFor;
  private static volatile Garden garden;

  private AltarVegetation() {}

  // ---- Curated variants ------------------------------------------------------------------------

  private record Extra(String feature, double weight, Soil soil, boolean cocoa) {
    static Extra of(String feature, double weight) {
      return new Extra(feature, weight, Soil.DIRT, false);
    }
  }

  /** A biome family with its own trees. The first matching variant wins; the rest fall back to the biome. */
  private record Variant(String name, Predicate<Holder<Biome>> matches, double treesPerChunk, boolean lilyPads,
      List<Extra> trees) {}

  private static Predicate<Holder<Biome>> is(List<ResourceKey<Biome>> keys) {
    return biome -> keys.stream().anyMatch(biome::is);
  }

  private static Predicate<Holder<Biome>> tagged(TagKey<Biome> tag) {
    return biome -> biome.is(tag);
  }

  private static final String CACTUS = "entrelumen:giant_cactus";

  private static final List<Variant> VARIANTS = List.of(
      new Variant("mushroom_fields", tagged(Tags.Biomes.IS_MUSHROOM).or(is(List.of(Biomes.MUSHROOM_FIELDS))), 3, false,
          List.of(Extra.of("minecraft:huge_red_mushroom", 1), Extra.of("minecraft:huge_brown_mushroom", 1))),
      new Variant("cherry_grove", is(List.of(Biomes.CHERRY_GROVE)), 4, false, List.of(Extra.of("minecraft:cherry", 1))),
      new Variant("mangrove_swamp", is(List.of(Biomes.MANGROVE_SWAMP)), 5, true,
          List.of(Extra.of("minecraft:mangrove", 3), Extra.of("minecraft:tall_mangrove", 1))),
      new Variant("swamp", is(List.of(Biomes.SWAMP)), 3, true, List.of(Extra.of("minecraft:swamp_oak", 1))),
      new Variant("jungle", tagged(BiomeTags.IS_JUNGLE), 7, false,
          List.of(new Extra("minecraft:mega_jungle_tree", 2, Soil.DIRT, true),
              new Extra("minecraft:jungle_tree", 3, Soil.DIRT, true), Extra.of("minecraft:jungle_bush", 1))),
      new Variant("old_growth_taiga", is(List.of(Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA)), 6, false,
          List.of(Extra.of("minecraft:mega_spruce", 3), Extra.of("minecraft:mega_pine", 2),
              Extra.of("minecraft:spruce", 1))),
      new Variant("taiga", tagged(BiomeTags.IS_TAIGA), 5, false,
          List.of(Extra.of("minecraft:spruce", 4), Extra.of("minecraft:pine", 2), Extra.of("minecraft:mega_spruce", 1))),
      new Variant("snowy", is(List.of(Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_SLOPES, Biomes.GROVE)), 2,
          false, List.of(Extra.of("minecraft:spruce", 2), Extra.of("minecraft:pine", 1))),
      new Variant("savanna", tagged(BiomeTags.IS_SAVANNA), 2, false,
          List.of(Extra.of("minecraft:acacia", 4), Extra.of("minecraft:oak", 1))),
      new Variant("dark_forest", is(List.of(Biomes.DARK_FOREST)), 8, false,
          List.of(Extra.of("minecraft:dark_oak", 6), Extra.of("minecraft:huge_red_mushroom", 1),
              Extra.of("minecraft:huge_brown_mushroom", 1), Extra.of("minecraft:birch", 1))),
      new Variant("birch_forest", is(List.of(Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST)), 6, false,
          List.of(Extra.of("minecraft:birch", 3), Extra.of("minecraft:super_birch_bees_0002", 2))),
      new Variant("desert", tagged(Tags.Biomes.IS_DESERT).or(is(List.of(Biomes.DESERT))), 1.5, false,
          List.of(new Extra(CACTUS, 1, Soil.SAND, false))),
      new Variant("badlands", tagged(BiomeTags.IS_BADLANDS), 1.5, false,
          List.of(new Extra(CACTUS, 2, Soil.SAND, false), Extra.of("minecraft:oak", 1))),
      new Variant("meadow", is(List.of(Biomes.MEADOW)), 1.5, false,
          List.of(Extra.of("minecraft:fancy_oak", 1), Extra.of("minecraft:birch", 1))),
      new Variant("flower_forest", is(List.of(Biomes.FLOWER_FOREST)), 5, false,
          List.of(Extra.of("minecraft:oak", 2), Extra.of("minecraft:birch", 2), Extra.of("minecraft:fancy_oak", 1))),
      new Variant("forest", is(List.of(Biomes.FOREST)), 7, false,
          List.of(Extra.of("minecraft:oak", 4), Extra.of("minecraft:birch", 1), Extra.of("minecraft:fancy_oak", 1))),
      new Variant("plains", is(List.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS)), 2, false,
          List.of(Extra.of("minecraft:oak", 3), Extra.of("minecraft:fancy_oak", 1))),
      new Variant("windswept", is(List.of(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST,
          Biomes.WINDSWEPT_GRAVELLY_HILLS)), 3, false,
          List.of(Extra.of("minecraft:spruce", 2), Extra.of("minecraft:oak", 1))),
      new Variant("crimson_forest", is(List.of(Biomes.CRIMSON_FOREST)), 6, false,
          List.of(new Extra("minecraft:crimson_fungus_planted", 1, Soil.NYLIUM, false))),
      new Variant("warped_forest", is(List.of(Biomes.WARPED_FOREST)), 6, false,
          List.of(new Extra("minecraft:warped_fungus_planted", 1, Soil.NYLIUM, false))),
      new Variant("end", tagged(BiomeTags.IS_END), 3, false,
          List.of(new Extra("minecraft:chorus_plant", 1, Soil.END_STONE, false))));

  /** Curated variant names, for status, tests and the design notes. */
  static List<String> variantNames() {
    return VARIANTS.stream().map(Variant::name).toList();
  }

  // ---- Resolution ------------------------------------------------------------------------------

  private static void checkCache(RegistryAccess registries) {
    if (cachedFor != registries) {
      PALETTES.clear();
      garden = null;
      cachedFor = registries;
    }
  }

  /** The palette of a biome, resolved once per registry set. */
  static Palette palette(ServerLevel level, Holder<Biome> biome) {
    checkCache(level.registryAccess());
    ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
    if (key == null) return resolve(level, biome);
    return PALETTES.computeIfAbsent(key, ignored -> resolve(level, biome));
  }

  static Garden garden(RegistryAccess registries) {
    checkCache(registries);
    Garden known = garden;
    if (known != null) return known;
    List<BlockState> species = new ArrayList<>();
    for (String id : GARDEN) {
      ResourceLocation location = ResourceLocation.parse(id);
      if (BuiltInRegistries.BLOCK.containsKey(location)) species.add(BuiltInRegistries.BLOCK.get(location).defaultBlockState());
    }
    var features = registries.registryOrThrow(Registries.CONFIGURED_FEATURE);
    Holder<ConfiguredFeature<?, ?>> cap = features.getHolder(ResourceKey.create(Registries.CONFIGURED_FEATURE,
        ResourceLocation.parse(INK_CAP))).map(holder -> (Holder<ConfiguredFeature<?, ?>>) holder).orElse(null);
    known = new Garden(List.copyOf(species), cap);
    garden = known;
    return known;
  }

  private static final java.util.concurrent.atomic.AtomicLong RESOLVE_NANOS = new java.util.concurrent.atomic.AtomicLong();

  /** Total time spent resolving palettes since start, for measurement. */
  static long resolveMicros() {
    return RESOLVE_NANOS.get() / 1000;
  }

  private static Palette resolve(ServerLevel level, Holder<Biome> biome) {
    long started = System.nanoTime();
    try {
      return resolveNow(level, biome);
    } finally {
      RESOLVE_NANOS.addAndGet(System.nanoTime() - started);
    }
  }

  private static Palette resolveNow(ServerLevel level, Holder<Biome> biome) {
    var features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
    Variant variant = null;
    for (Variant candidate : VARIANTS)
      if (candidate.matches().test(biome)) {
        variant = candidate;
        break;
      }
    List<TreeOption> trees = new ArrayList<>();
    double perChunk = 0;
    if (variant != null) {
      for (Extra extra : variant.trees()) {
        if (extra.feature().equals(CACTUS)) {
          trees.add(new TreeOption(CACTUS, null, true, extra.weight(), extra.soil(), false));
          continue;
        }
        var holder = features.getHolder(ResourceKey.create(Registries.CONFIGURED_FEATURE,
            ResourceLocation.parse(extra.feature())));
        if (holder.isPresent())
          trees.add(new TreeOption(extra.feature(), holder.get(), false, extra.weight(), extra.soil(), extra.cocoa()));
      }
      perChunk = variant.treesPerChunk();
    }
    if (trees.isEmpty()) {
      Map<String, TreeOption> derived = new LinkedHashMap<>();
      double total = 0;
      for (var step : biome.value().getGenerationSettings().features())
        for (Holder<PlacedFeature> placed : step) {
          String parent = placed.unwrapKey().map(k -> k.location().toString()).orElse("inline");
          Map<String, TreeOption> found = new LinkedHashMap<>();
          collectTrees(placed.value().feature(), 1.0, 0, parent, found);
          if (found.isEmpty()) continue;
          double count = expectedCount(level, placed.value());
          if (!(count > 0)) continue;
          for (TreeOption option : found.values()) {
            double weight = count * option.weight();
            derived.merge(option.id(), new TreeOption(option.id(), option.feature(), false, weight, option.soil(), false),
                (a, b) -> new TreeOption(a.id(), a.feature(), false, a.weight() + b.weight(), a.soil(), false));
            total += weight;
          }
        }
      trees.addAll(derived.values());
      perChunk = total;
    }
    List<BlockStateProvider> flowers = new ArrayList<>(), grasses = new ArrayList<>();
    Set<BlockStateProvider> seen = new LinkedHashSet<>();
    for (ConfiguredFeature<?, ?> feature : biome.value().getGenerationSettings().getFlowerFeatures())
      patchProvider(feature).ifPresent(provider -> {
        if (seen.add(provider)) flowers.add(provider);
      });
    var steps = biome.value().getGenerationSettings().features();
    for (HolderSet<PlacedFeature> step : steps)
      for (Holder<PlacedFeature> placed : step)
        patchProvider(placed.value().feature().value()).ifPresent(provider -> {
          if (!seen.add(provider)) return;
          BlockState sample = sample(provider);
          if (sample == null || !plant(sample)) return;
          if (sample.is(BlockTags.FLOWERS)) flowers.add(provider);
          else grasses.add(provider);
        });
    flowers.removeIf(provider -> {
      BlockState sample = sample(provider);
      return sample == null || !plant(sample);
    });
    boolean lilyPads = variant != null && variant.lilyPads();
    return new Palette(variant == null ? "biome" : variant.name(), List.copyOf(trees), perChunk,
        List.copyOf(flowers), List.copyOf(grasses), lilyPads);
  }

  /** Trees whose feature names what they stand on; everything else stands on dirt. */
  private static Soil soilOf(ConfiguredFeature<?, ?> feature) {
    if (feature.feature() == Feature.HUGE_FUNGUS) return Soil.NYLIUM;
    if (feature.feature() == Feature.CHORUS_PLANT) return Soil.END_STONE;
    return Soil.DIRT;
  }

  private static BlockState sample(BlockStateProvider provider) {
    try {
      return provider.getState(RandomSource.create(0x76656765L), BlockPos.ZERO);
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  /** The block provider of a patch feature (random patch, flower patch) of single blocks, if it is one. */
  static Optional<BlockStateProvider> patchProvider(ConfiguredFeature<?, ?> feature) {
    if (!(feature.config() instanceof RandomPatchConfiguration patch)) return Optional.empty();
    ConfiguredFeature<?, ?> inner = patch.feature().value().feature().value();
    if (inner.config() instanceof SimpleBlockConfiguration simple) return Optional.of(simple.toPlace());
    return Optional.empty();
  }

  /** A plant the altar may grow as flora: natural, plant-like, and neither harmful nor food. */
  static boolean plant(BlockState state) {
    if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
    if (state.is(EXCLUDED) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return false;
    if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) return true;
    if (state.getBlock() instanceof net.minecraft.world.level.block.BushBlock) return true;
    return state.is(BlockTags.REPLACEABLE) && !state.is(Blocks.SNOW) && !state.is(Blocks.FIRE)
        && !state.is(Blocks.SOUL_FIRE) && !state.isAir();
  }

  /**
   * The tree-like features inside a biome feature, with the share of its placements each one gets.
   * A tree is any tree configuration (vanilla or modded placers), a vanilla huge mushroom or fungus, or
   * chorus. Selectors are opened, and branches that are not trees (fallen logs, rocks, modded
   * shapes the sandbox cannot vouch for) are simply left out, so a forest that mixes trees with
   * other things still grows its trees.
   */
  static void collectTrees(Holder<ConfiguredFeature<?, ?>> holder, double share, int depth, String parent,
      Map<String, TreeOption> found) {
    if (depth > 8 || !(share > 0)) return;
    ConfiguredFeature<?, ?> feature = holder.value();
    Feature<?> type = feature.feature();
    FeatureConfiguration config = feature.config();
    if (config instanceof TreeConfiguration || type == Feature.HUGE_BROWN_MUSHROOM || type == Feature.HUGE_RED_MUSHROOM
        || type == Feature.HUGE_FUNGUS || type == Feature.CHORUS_PLANT) {
      String id = holder.unwrapKey().map(k -> k.location().toString()).orElse(parent + "#" + found.size());
      found.merge(id, new TreeOption(id, holder, false, share, soilOf(feature), false),
          (a, b) -> new TreeOption(a.id(), a.feature(), false, a.weight() + b.weight(), a.soil(), false));
      return;
    }
    if (type == Feature.RANDOM_SELECTOR && config instanceof RandomFeatureConfiguration random) {
      double remaining = 1;
      for (var weighted : random.features) {
        double chance = Math.max(0, Math.min(1, weighted.chance)) * remaining;
        collectTrees(weighted.feature.value().feature(), share * chance, depth + 1, parent, found);
        remaining -= chance;
      }
      collectTrees(random.defaultFeature.value().feature(), share * remaining, depth + 1, parent, found);
    } else if (type == Feature.SIMPLE_RANDOM_SELECTOR && config instanceof SimpleRandomFeatureConfiguration simple) {
      int size = simple.features.size();
      for (Holder<PlacedFeature> branch : simple.features)
        collectTrees(branch.value().feature(), share / size, depth + 1, parent, found);
    } else if (type == Feature.RANDOM_BOOLEAN_SELECTOR && config instanceof RandomBooleanFeatureConfiguration choice) {
      collectTrees(choice.featureTrue.value().feature(), share / 2, depth + 1, parent, found);
      collectTrees(choice.featureFalse.value().feature(), share / 2, depth + 1, parent, found);
    }
  }

  /** Expected placements per chunk from the count and rarity rules only, as the Nature service did. */
  private static double expectedCount(ServerLevel level, PlacedFeature placed) {
    var context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(placed));
    RandomSource random = RandomSource.create(0x6E61747572654C4CL);
    long total = 0;
    int samples = 64;
    try {
      for (int sample = 0; sample < samples; sample++) {
        Stream<BlockPos> positions = Stream.of(BlockPos.ZERO);
        for (PlacementModifier modifier : placed.placement())
          if (modifier instanceof RepeatingPlacement || modifier instanceof RarityFilter)
            positions = positions.flatMap(pos -> modifier.getPositions(context, random, pos));
        total += positions.count();
      }
    } catch (RuntimeException unavailable) {
      return 0;
    }
    return total / (double) samples;
  }

  // ---- Harvest and dyes ----------------------------------------------------------------------

  /**
   * Items a player can harvest from what the altar grows in a biome: the dye garden, the ink caps'
   * caps, the biome's flowers and ground plants, giant cacti, cocoa and lily pads. Each block's own
   * loot table is rolled with a bare hand, as a player would break it.
   */
  static Set<String> harvest(ServerLevel level, Holder<Biome> biome) {
    Set<BlockState> blocks = new LinkedHashSet<>();
    Garden garden = garden(level.registryAccess());
    blocks.addAll(garden.species());
    Palette palette = palette(level, biome);
    RandomSource random = RandomSource.create(0x68617276L);
    for (BlockStateProvider provider : palette.flowers())
      for (int i = 0; i < 32; i++) sampleInto(blocks, provider, random, i);
    for (BlockStateProvider provider : palette.grasses())
      for (int i = 0; i < 32; i++) sampleInto(blocks, provider, random, i);
    for (TreeOption option : palette.trees()) {
      if (option.giantCactus()) blocks.add(Blocks.CACTUS.defaultBlockState());
      if (option.cocoa()) blocks.add(Blocks.COCOA.defaultBlockState().setValue(
          net.minecraft.world.level.block.CocoaBlock.AGE, net.minecraft.world.level.block.CocoaBlock.MAX_AGE));
    }
    if (palette.lilyPads()) blocks.add(Blocks.LILY_PAD.defaultBlockState());
    Set<String> items = new java.util.TreeSet<>();
    for (BlockState state : blocks) items.addAll(loot(level, state, 4));
    if (garden.inkCap() != null) {
      ResourceLocation cap = ResourceLocation.parse("undergarden:ink_mushroom_cap");
      if (BuiltInRegistries.BLOCK.containsKey(cap))
        items.addAll(loot(level, BuiltInRegistries.BLOCK.get(cap).defaultBlockState(), 64));
    }
    return items;
  }

  private static void sampleInto(Set<BlockState> blocks, BlockStateProvider provider, RandomSource random, int i) {
    try {
      BlockState state = provider.getState(random, new BlockPos(i * 37, 64, i * 91));
      if (state != null && plant(state)) blocks.add(state);
    } catch (RuntimeException unavailable) {
      // A provider that needs a live position is sampled no further.
    }
  }

  /** Item ids a block drops for a bare hand over several seeded rolls. */
  static Set<String> loot(ServerLevel level, BlockState state, int rolls) {
    Set<String> items = new java.util.TreeSet<>();
    var table = level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
    for (int roll = 0; roll < rolls; roll++) {
      var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
          .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
              net.minecraft.world.phys.Vec3.ZERO)
          .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
              net.minecraft.world.item.ItemStack.EMPTY)
          .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
          .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
      try {
        for (var stack : table.getRandomItems(params, 0x6C6F6F74L + roll))
          if (!stack.isEmpty()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      } catch (RuntimeException unavailable) {
        // A loot table that needs more than a hand and a position gives nothing here.
      }
    }
    return items;
  }

  /** The loaded crafting and smelting recipes, as the dye closure reads them. */
  static List<VegetationRules.Recipe> recipes(ServerLevel level) {
    List<VegetationRules.Recipe> recipes = new ArrayList<>();
    var manager = level.getRecipeManager();
    var registries = level.registryAccess();
    for (var holder : manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING))
      add(recipes, holder.value(), registries);
    for (var holder : manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING))
      add(recipes, holder.value(), registries);
    return recipes;
  }

  private static void add(List<VegetationRules.Recipe> recipes, net.minecraft.world.item.crafting.Recipe<?> recipe,
      RegistryAccess registries) {
    net.minecraft.world.item.ItemStack result;
    try {
      result = recipe.getResultItem(registries);
    } catch (RuntimeException unavailable) {
      return;
    }
    if (result == null || result.isEmpty()) return;
    List<Set<String>> slots = new ArrayList<>();
    for (var ingredient : recipe.getIngredients()) {
      if (ingredient.isEmpty()) continue;
      Set<String> options = new java.util.HashSet<>();
      for (var stack : ingredient.getItems())
        if (!stack.isEmpty()) options.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      if (options.isEmpty()) return;
      slots.add(Set.copyOf(options));
    }
    if (slots.isEmpty()) return;
    recipes.add(new VegetationRules.Recipe(List.copyOf(slots), BuiltInRegistries.ITEM.getKey(result.getItem()).toString()));
  }
}
