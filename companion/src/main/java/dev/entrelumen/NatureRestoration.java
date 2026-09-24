package dev.entrelumen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/**
 * Landscape restoration at a complete Ark. A team marks a site with a bookmarked compass; bone
 * meal then heals bare natural ground there with the site's own cover, its biome's flora and its
 * biome's native trees. Built blocks, containers, decorations and claimed chunks are left alone.
 * Every change is one deliberate, bounded server operation; there is no ticking or upkeep.
 */
public final class NatureRestoration {
  public enum Status {
    MARKED, ALREADY_MARKED, RESTORED, NOTHING, WAITING, UNAVAILABLE, UNBOUND_COMPASS,
    OTHER_DIMENSION, TOO_FAR, STRUCTURE, NO_SITE, UNLOADED
  }

  /** Counts of applied changes, exact payment, work left for later and spots protected. */
  public record Outcome(Status status, int ground, int trees, int plants, int boneMeal,
      int saplings, int deferred, int guarded) {
    static Outcome of(Status status) {
      return new Outcome(status, 0, 0, 0, 0, 0, 0, 0);
    }

    public boolean success() {
      return status == Status.MARKED || status == Status.ALREADY_MARKED
          || status == Status.RESTORED || status == Status.NOTHING;
    }
  }

  record TreeOption(Holder<ConfiguredFeature<?, ?>> feature, double perChunk) {}

  private static final int NONE = 0;
  private static final int WOODY = 1;
  private static final int INVALID = 2;
  private static final Set<Block> NATURAL_BLOCKS = Set.of(Blocks.GRAVEL, Blocks.CLAY,
      Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.PACKED_ICE,
      Blocks.BLUE_ICE, Blocks.CALCITE, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
      Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM,
      Blocks.MOSS_CARPET, Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK);
  private static final Set<Block> TREE_MARKERS = Set.of(Blocks.BROWN_MUSHROOM_BLOCK,
      Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM);

  private NatureRestoration() {}

  /** A bookmarked compass marks the site; bone meal restores it. Other items pass through. */
  public static Outcome use(ServerPlayer player, BlockPos module, InteractionHand hand) {
    ItemStack held = player.getItemInHand(hand);
    if (held.is(Items.COMPASS)) return mark(player, module, hand);
    if (held.is(Items.BONE_MEAL)) return restore(player, module, hand);
    return Outcome.of(Status.UNAVAILABLE);
  }

  public static Outcome mark(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (!reachable(player, module, hand) || !player.getItemInHand(hand).is(Items.COMPASS))
      return Outcome.of(Status.UNAVAILABLE);
    var tracker = player.getItemInHand(hand).get(DataComponents.LODESTONE_TRACKER);
    if (tracker == null || tracker.target().isEmpty())
      return refuse(player, Status.UNBOUND_COMPASS, Component.translatable("entrelumen.nature.unbound"));
    if (!assembled(player, module))
      return refuse(player, Status.STRUCTURE, Component.translatable("entrelumen.nature.structure"));
    GlobalPos target = tracker.target().get();
    Outcome refused = siteRefusal(player, module, target);
    if (refused != null) return refused;
    boolean changed = NatureRestorationData.get(player.server)
        .mark(CampaignActions.campaignId(player), target);
    BlockPos pos = target.pos();
    player.sendSystemMessage(Component.translatable(
        changed ? "entrelumen.nature.marked" : "entrelumen.nature.already_marked",
        pos.getX(), pos.getY(), pos.getZ(), NatureRestorationRules.RADIUS));
    return Outcome.of(changed ? Status.MARKED : Status.ALREADY_MARKED);
  }

  public static Outcome restore(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (!reachable(player, module, hand)) return Outcome.of(Status.UNAVAILABLE);
    ItemStack boneMeal = player.getItemInHand(hand);
    if (!boneMeal.is(Items.BONE_MEAL) || boneMeal.isEmpty()) return Outcome.of(Status.UNAVAILABLE);
    if (!assembled(player, module))
      return refuse(player, Status.STRUCTURE, Component.translatable("entrelumen.nature.structure"));
    GlobalPos site = NatureRestorationData.get(player.server).site(CampaignActions.campaignId(player));
    if (site == null)
      return refuse(player, Status.NO_SITE, Component.translatable("entrelumen.nature.no_site"));
    Outcome refused = siteRefusal(player, module, site);
    if (refused != null) return refused;
    ServerLevel level = player.serverLevel();
    if (!loaded(level, site.pos()))
      return refuse(player, Status.UNLOADED, Component.translatable("entrelumen.nature.unloaded"));

    ItemStack offhand = player.getOffhandItem();
    ItemStack saplings = offhand.is(ItemTags.SAPLINGS) ? offhand : ItemStack.EMPTY;
    Pass pass = new Pass(player, level, site.pos(), boneMeal, saplings);
    Outcome outcome = pass.run();
    if (outcome.boneMeal() > 0 || outcome.saplings() > 0) {
      player.getInventory().setChanged();
      player.containerMenu.broadcastChanges();
    }
    if (outcome.status() == Status.RESTORED) {
      level.playSound(null, module, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.6f, 1.0f);
      level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, site.pos(), 15);
    }
    report(player, site.pos(), outcome, pass.treesNeedSaplings);
    return outcome;
  }

  /** Book rendering reads the team's site without creating or changing any record. */
  public static List<Component> journalLines(ServerPlayer player) {
    GlobalPos site = NatureRestorationData.get(player.server).site(CampaignActions.campaignId(player));
    List<Component> lines = new ArrayList<>();
    if (site == null) lines.add(Component.translatable("entrelumen.nature.journal.none"));
    else lines.add(Component.translatable("entrelumen.nature.journal.site",
        site.dimension().location().toString(), site.pos().getX(), site.pos().getY(), site.pos().getZ()));
    lines.add(Component.translatable("entrelumen.nature.journal.instructions",
        NatureRestorationRules.RADIUS, NatureRestorationRules.TREE_BONE_MEAL));
    return List.copyOf(lines);
  }

  private static boolean reachable(ServerPlayer player, BlockPos module, InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND && !player.isSpectator()
        && player.canInteractWithBlock(module, 1.0)
        && player.serverLevel().hasChunkAt(module)
        && player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block
        && block.kind() == ArkFieldJournals.Kind.NATURE;
  }

  private static boolean assembled(ServerPlayer player, BlockPos module) {
    return LogisticsModuleActions.controllerForDeposit(
        EngineeringDiagnostics.physicalView(player.serverLevel(), module)) != null;
  }

  private static Outcome siteRefusal(ServerPlayer player, BlockPos module, GlobalPos site) {
    if (!site.dimension().equals(player.serverLevel().dimension()))
      return refuse(player, Status.OTHER_DIMENSION, Component.translatable("entrelumen.nature.other_dimension"));
    if (!NatureRestorationRules.withinReach(module.getX(), module.getZ(), site.pos().getX(), site.pos().getZ()))
      return refuse(player, Status.TOO_FAR, Component.translatable("entrelumen.nature.too_far",
          NatureRestorationRules.horizontalDistance(module.getX(), module.getZ(),
              site.pos().getX(), site.pos().getZ()),
          NatureRestorationRules.MAX_DISTANCE));
    return null;
  }

  /** Never loads chunks: every chunk the operation can read or write must already be present. */
  static boolean loaded(ServerLevel level, BlockPos center) {
    int[] bounds = NatureRestorationRules.chunkBounds(center.getX(), center.getZ());
    for (int x = bounds[0]; x <= bounds[2]; x++)
      for (int z = bounds[1]; z <= bounds[3]; z++)
        if (!level.hasChunk(x, z) || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
    return true;
  }

  private static Outcome refuse(ServerPlayer player, Status status, Component message) {
    player.sendSystemMessage(message);
    return Outcome.of(status);
  }

  private static void report(ServerPlayer player, BlockPos site, Outcome outcome,
      boolean treesNeedSaplings) {
    switch (outcome.status()) {
      case RESTORED -> player.sendSystemMessage(Component.translatable("entrelumen.nature.restored",
          outcome.ground(), outcome.trees(), outcome.plants(), outcome.boneMeal(), outcome.saplings()));
      case WAITING -> player.sendSystemMessage(Component.translatable("entrelumen.nature.waiting",
          outcome.deferred()));
      default -> player.sendSystemMessage(Component.translatable("entrelumen.nature.nothing",
          NatureRestorationRules.RADIUS, site.getX(), site.getY(), site.getZ()));
    }
    if (outcome.status() == Status.RESTORED && outcome.deferred() > 0)
      player.sendSystemMessage(Component.translatable("entrelumen.nature.deferred", outcome.deferred()));
    if (treesNeedSaplings)
      player.sendSystemMessage(Component.translatable("entrelumen.nature.saplings",
          NatureRestorationRules.TREE_BONE_MEAL));
    if (outcome.guarded() > 0)
      player.sendSystemMessage(Component.translatable("entrelumen.nature.guarded", outcome.guarded()));
  }

  /**
   * Terrain and wild vegetation. Any block entity, persistent (placed) leaves, stripped wood,
   * farmland, crops, paths and every other block counts as built and is protected with a margin.
   */
  static boolean natural(BlockState state) {
    if (state.isAir()) return true;
    if (state.hasBlockEntity()) return false;
    if (state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT))
      return false;
    if (state.is(Tags.Blocks.STRIPPED_LOGS) || state.is(Tags.Blocks.STRIPPED_WOODS)) return false;
    if (state.getBlock() instanceof LiquidBlock) return true;
    return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
        || state.is(BlockTags.BASE_STONE_NETHER) || state.is(Tags.Blocks.ORES) || state.is(BlockTags.LOGS)
        || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
        || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.REPLACEABLE)
        || NATURAL_BLOCKS.contains(state.getBlock());
  }

  /** Decorations, vehicles and other non-living entities belong to someone's build. */
  static boolean built(Entity entity) {
    if (entity instanceof ArmorStand) return true;
    return !(entity instanceof LivingEntity || entity instanceof ItemEntity
        || entity instanceof ExperienceOrb || entity instanceof Projectile);
  }

  /** Vegetal-step features made only of vanilla trees and huge mushrooms, with expected counts. */
  static List<TreeOption> nativeTrees(ServerLevel level, Holder<Biome> biome) {
    var steps = biome.value().getGenerationSettings().features();
    int step = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
    if (steps.size() <= step) return List.of();
    List<TreeOption> options = new ArrayList<>();
    for (Holder<PlacedFeature> placed : steps.get(step)) {
      if (classify(placed.value().feature().value(), 0) != WOODY) continue;
      double perChunk = expectedCount(level, placed.value());
      if (perChunk > 0) options.add(new TreeOption(placed.value().feature(), perChunk));
    }
    return List.copyOf(options);
  }

  private static double expectedCount(ServerLevel level, PlacedFeature placed) {
    var context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(placed));
    RandomSource random = RandomSource.create(0x6E61747572654C4CL);
    long total = 0;
    int samples = 64;
    for (int sample = 0; sample < samples; sample++) {
      Stream<BlockPos> positions = Stream.of(BlockPos.ZERO);
      // Only the count and rarity rules describe density; spread and terrain rules need real chunks.
      for (PlacementModifier modifier : placed.placement())
        if (modifier instanceof RepeatingPlacement || modifier instanceof RarityFilter)
          positions = positions.flatMap(pos -> modifier.getPositions(context, random, pos));
      total += positions.count();
    }
    return total / (double) samples;
  }

  private static int classify(ConfiguredFeature<?, ?> feature, int depth) {
    if (depth > 8 || !vanilla(feature.feature()) || !vanilla(feature.config())) return INVALID;
    Feature<?> type = feature.feature();
    FeatureConfiguration config = feature.config();
    if (type == Feature.TREE && config instanceof TreeConfiguration tree)
      return vanillaTree(tree) ? WOODY : INVALID;
    if ((type == Feature.HUGE_BROWN_MUSHROOM || type == Feature.HUGE_RED_MUSHROOM)
        && config instanceof HugeMushroomFeatureConfiguration mushroom)
      return vanilla(mushroom.capProvider) && vanilla(mushroom.stemProvider) ? WOODY : INVALID;
    List<Holder<PlacedFeature>> branches = new ArrayList<>();
    if (type == Feature.RANDOM_SELECTOR && config instanceof RandomFeatureConfiguration random) {
      random.features.forEach(weighted -> branches.add(weighted.feature));
      branches.add(random.defaultFeature);
    } else if (type == Feature.SIMPLE_RANDOM_SELECTOR && config instanceof SimpleRandomFeatureConfiguration simple) {
      simple.features.forEach(branches::add);
    } else if (type == Feature.RANDOM_BOOLEAN_SELECTOR && config instanceof RandomBooleanFeatureConfiguration choice) {
      branches.add(choice.featureTrue);
      branches.add(choice.featureFalse);
    } else {
      return INVALID;
    }
    int result = NONE;
    for (Holder<PlacedFeature> branch : branches) {
      PlacedFeature placed = branch.value();
      for (PlacementModifier modifier : placed.placement()) if (!vanilla(modifier)) return INVALID;
      int nested = classify(placed.feature().value(), depth + 1);
      if (nested == INVALID) return INVALID;
      if (nested == WOODY) result = WOODY;
    }
    return result;
  }

  private static boolean vanillaTree(TreeConfiguration tree) {
    return vanilla(tree.trunkProvider) && vanilla(tree.dirtProvider) && vanilla(tree.trunkPlacer)
        && vanilla(tree.foliageProvider) && vanilla(tree.foliagePlacer) && vanilla(tree.minimumSize)
        && tree.rootPlacer.map(NatureRestoration::vanilla).orElse(true)
        && tree.decorators.stream().allMatch(NatureRestoration::vanilla);
  }

  /** Only Minecraft's own feature code runs in the sandbox; modded tree shapes are skipped. */
  private static boolean vanilla(Object value) {
    return value != null && value.getClass().getName().startsWith("net.minecraft.");
  }

  private static long column(int x, int z) {
    return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
  }

  /** State for one operation: its budget, its caches and what it has done so far. */
  private static final class Pass {
    final ServerPlayer player;
    final ServerLevel level;
    final BlockPos center;
    final ItemStack boneMeal;
    final ItemStack saplings;
    final long seed;
    final BoundingBox permitted;
    final Set<Long> occupied;
    final Map<Holder<Biome>, List<TreeOption>> trees = new HashMap<>();
    final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    int ground;
    int grown;
    int plants;
    int spentBoneMeal;
    int spentSaplings;
    int deferred;
    int guarded;
    boolean treesNeedSaplings;

    Pass(ServerPlayer player, ServerLevel level, BlockPos center, ItemStack boneMeal, ItemStack saplings) {
      this.player = player;
      this.level = level;
      this.center = center.immutable();
      this.boneMeal = boneMeal;
      this.saplings = saplings;
      this.seed = level.getSeed();
      int reach = NatureRestorationRules.writeReach();
      this.permitted = new BoundingBox(center.getX() - reach, level.getMinBuildHeight(),
          center.getZ() - reach, center.getX() + reach, level.getMaxBuildHeight() - 1, center.getZ() + reach);
      this.occupied = occupiedColumns();
    }

    Outcome run() {
      cover();
      trees();
      flora();
      Status status = ground + grown + plants > 0 ? Status.RESTORED
          : deferred > 0 ? Status.WAITING : Status.NOTHING;
      return new Outcome(status, ground, grown, plants, spentBoneMeal, spentSaplings, deferred, guarded);
    }

    private Set<Long> occupiedColumns() {
      int window = NatureRestorationRules.VERTICAL_WINDOW;
      AABB area = new AABB(permitted.minX(), center.getY() - window - 4, permitted.minZ(),
          permitted.maxX() + 1, center.getY() + window + 48, permitted.maxZ() + 1);
      Set<Long> columns = new HashSet<>();
      for (Entity entity : level.getEntities((Entity) null, area, NatureRestoration::built)) {
        AABB box = entity.getBoundingBox();
        int minX = Math.max(permitted.minX(), (int) Math.floor(box.minX) - 1);
        int maxX = Math.min(permitted.maxX(), (int) Math.floor(box.maxX) + 1);
        int minZ = Math.max(permitted.minZ(), (int) Math.floor(box.minZ) - 1);
        int maxZ = Math.min(permitted.maxZ(), (int) Math.floor(box.maxZ) + 1);
        for (int x = minX; x <= maxX; x++)
          for (int z = minZ; z <= maxZ; z++) columns.add(column(x, z));
      }
      return columns;
    }

    /** The top terrain block of a column, ignoring leaves, within the site's vertical window. */
    private BlockPos groundAt(int x, int z) {
      int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
      return NatureRestorationRules.withinWindow(y, center.getY()) ? new BlockPos(x, y, z) : null;
    }

    /** No built block or decoration in the 3x3 columns from below the ground to {@code up} above. */
    private boolean clear(int x, int groundY, int z, int up) {
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          if (occupied.contains(column(x + dx, z + dz))) return false;
          for (int y = groundY - 1; y <= groundY + up; y++)
            if (!natural(level.getBlockState(cursor.set(x + dx, y, z + dz)))) return false;
        }
      return true;
    }

    private void cover() {
      int grass = 0;
      int podzol = 0;
      int mycelium = 0;
      for (var offset : NatureRestorationRules.columns()) {
        BlockPos pos = groundAt(center.getX() + offset.dx(), center.getZ() + offset.dz());
        if (pos == null) continue;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.GRASS_BLOCK)) grass++;
        else if (state.is(Blocks.PODZOL)) podzol++;
        else if (state.is(Blocks.MYCELIUM)) mycelium++;
      }
      BlockState cover = switch (NatureRestorationRules.dominantCover(grass, podzol, mycelium)) {
        case 1 -> Blocks.PODZOL.defaultBlockState();
        case 2 -> Blocks.MYCELIUM.defaultBlockState();
        default -> Blocks.GRASS_BLOCK.defaultBlockState();
      };
      for (var offset : NatureRestorationRules.columns()) {
        int x = center.getX() + offset.dx();
        int z = center.getZ() + offset.dz();
        BlockPos pos = groundAt(x, z);
        if (pos == null || !level.getBlockState(pos).is(Blocks.DIRT)
            || !level.getBlockState(pos.above()).isAir()) continue;
        if (!clear(x, pos.getY(), z, 2)) {
          guarded++;
          continue;
        }
        if (boneMeal.getCount() < 1) {
          deferred++;
          continue;
        }
        LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
        writes.put(pos, cover);
        if (commit(writes, Map.of(), Block.UPDATE_ALL)) {
          ground++;
          pay(1, 0);
        } else {
          guarded++;
        }
      }
    }

    private void trees() {
      for (var offset : NatureRestorationRules.columns()) {
        int x = center.getX() + offset.dx();
        int z = center.getZ() + offset.dz();
        BlockPos base = groundAt(x, z);
        if (base == null) continue;
        BlockPos origin = base.above();
        BlockState at = level.getBlockState(origin);
        if (!level.getBlockState(base).is(BlockTags.DIRT)
            || !(at.isAir() || natural(at) && at.is(BlockTags.REPLACEABLE_BY_TREES))) continue;
        List<TreeOption> options = treeSpot(x, z, origin);
        if (options.isEmpty() || nearTree(origin)) continue;
        double[] weights = options.stream().mapToDouble(TreeOption::perChunk).toArray();
        if (!clear(x, base.getY(), z, 3)) {
          guarded++;
          continue;
        }
        if (saplings.isEmpty()) {
          treesNeedSaplings = true;
          deferred++;
          continue;
        }
        if (boneMeal.getCount() < NatureRestorationRules.TREE_BONE_MEAL
            || grown >= NatureRestorationRules.MAX_TREES) {
          deferred++;
          continue;
        }
        int index = NatureRestorationRules.weightedIndex(weights,
            NatureRestorationRules.unit(seed, x, z, NatureRestorationRules.SPECIES_SALT));
        RandomSource random = RandomSource.create(
            NatureRestorationRules.mix(seed, x, z, NatureRestorationRules.SPECIES_SALT));
        var result = FeatureSandbox.simulate(level, permitted, options.get(index).feature().value(),
            random, origin);
        if (result == null) continue;
        if (!fits(result.writes())) {
          guarded++;
          continue;
        }
        if (commit(result.writes(), result.blockEntities(), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE)) {
          grown++;
          pay(NatureRestorationRules.TREE_BONE_MEAL, 1);
        } else {
          guarded++;
        }
      }
    }

    /**
     * The biome's native trees when this column is one of its stable tree spots. Flora never takes
     * such a spot, so a tree deferred for lack of saplings can still grow on a later pass.
     */
    private List<TreeOption> treeSpot(int x, int z, BlockPos origin) {
      List<TreeOption> options = trees.computeIfAbsent(level.getBiome(origin),
          biome -> nativeTrees(level, biome));
      if (options.isEmpty()) return List.of();
      double perChunk = options.stream().mapToDouble(TreeOption::perChunk).sum();
      return NatureRestorationRules.selected(seed, x, z, NatureRestorationRules.TREE_SALT,
          NatureRestorationRules.treeProbability(perChunk)) ? options : List.of();
    }

    private boolean nearTree(BlockPos origin) {
      for (int dx = -3; dx <= 3; dx++)
        for (int dz = -3; dz <= 3; dz++)
          for (int y = origin.getY() - 1; y <= origin.getY() + 6; y++) {
            BlockState state = level.getBlockState(cursor.set(origin.getX() + dx, y, origin.getZ() + dz));
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS) || TREE_MARKERS.contains(state.getBlock()))
              return true;
          }
      return false;
    }

    /** A simulated tree may only replace wild ground or air and must not touch any build. */
    private boolean fits(Map<BlockPos, BlockState> writes) {
      int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
      for (var entry : writes.entrySet()) {
        BlockPos pos = entry.getKey();
        BlockState before = level.getBlockState(pos);
        boolean replaceable = before.isAir() || natural(before)
            && (before.canBeReplaced() || before.is(BlockTags.REPLACEABLE_BY_TREES)
                || before.is(BlockTags.DIRT) || before.is(entry.getValue().getBlock()));
        if (!replaceable || occupied.contains(column(pos.getX(), pos.getZ()))) return false;
        for (Direction direction : Direction.values()) {
          BlockPos neighbour = pos.relative(direction);
          if (!writes.containsKey(neighbour) && !natural(level.getBlockState(neighbour))) return false;
        }
        minX = Math.min(minX, pos.getX());
        minY = Math.min(minY, pos.getY());
        minZ = Math.min(minZ, pos.getZ());
        maxX = Math.max(maxX, pos.getX());
        maxY = Math.max(maxY, pos.getY());
        maxZ = Math.max(maxZ, pos.getZ());
      }
      AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1);
      return level.getEntities((Entity) null, box, entity -> built(entity) || entity instanceof Player).isEmpty();
    }

    private void flora() {
      for (var offset : NatureRestorationRules.columns()) {
        int x = center.getX() + offset.dx();
        int z = center.getZ() + offset.dz();
        BlockPos base = groundAt(x, z);
        if (base == null) continue;
        BlockPos pos = base.above();
        if (!level.getBlockState(base).is(Blocks.GRASS_BLOCK) || !level.getBlockState(pos).isAir()
            || !NatureRestorationRules.selected(seed, x, z, NatureRestorationRules.FLORA_SALT,
                NatureRestorationRules.FLORA_DENSITY) || !treeSpot(x, z, pos).isEmpty()) continue;
        if (!clear(x, base.getY(), z, 3)) {
          guarded++;
          continue;
        }
        LinkedHashMap<BlockPos, BlockState> writes = plant(x, z, pos);
        if (writes == null) continue;
        if (boneMeal.getCount() < 1) {
          deferred++;
          continue;
        }
        if (commit(writes, Map.of(), Block.UPDATE_ALL)) {
          plants++;
          pay(1, 0);
        } else {
          guarded++;
        }
      }
    }

    /** Short grass, or, as with vanilla bone meal on grass, the biome's own first flower patch. */
    private LinkedHashMap<BlockPos, BlockState> plant(int x, int z, BlockPos pos) {
      BlockState state = null;
      if (NatureRestorationRules.selected(seed, x, z, NatureRestorationRules.FLOWER_SALT,
          NatureRestorationRules.FLOWER_SHARE)) state = flower(x, z, pos);
      if (state == null || !natural(state)) state = Blocks.SHORT_GRASS.defaultBlockState();
      LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
      if (state.getBlock() instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)) {
        BlockPos upper = pos.above();
        if (level.getBlockState(upper).isAir()) {
          BlockState lower = state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
          if (!lower.canSurvive(level, pos)) return null;
          writes.put(pos, lower);
          writes.put(upper, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
          return writes;
        }
        state = Blocks.SHORT_GRASS.defaultBlockState();
      }
      if (!state.canSurvive(level, pos)) return null;
      writes.put(pos, state);
      return writes;
    }

    private BlockState flower(int x, int z, BlockPos pos) {
      var flowers = level.getBiome(pos).value().getGenerationSettings().getFlowerFeatures();
      if (flowers.isEmpty() || !(flowers.getFirst().config() instanceof RandomPatchConfiguration patch)
          || !(patch.feature().value().feature().value().config() instanceof SimpleBlockConfiguration simple))
        return null;
      return simple.toPlace().getState(RandomSource.create(NatureRestorationRules.mix(seed, x, z,
          NatureRestorationRules.FLOWER_SALT)), pos);
    }

    /**
     * Applies a unit, then asks the native placement event as the acting player for every changed
     * block, as block items do. Claim systems can cancel; any refusal restores the whole unit.
     */
    private boolean commit(Map<BlockPos, BlockState> writes, Map<BlockPos, CompoundTag> blockEntities,
        int flags) {
      return LandWorks.commit(player, level, writes, blockEntities, flags);
    }

    /** Creative players pay too; there is no free branch to leak into survival. */
    private void pay(int bone, int sapling) {
      boneMeal.shrink(bone);
      spentBoneMeal += bone;
      if (sapling > 0) {
        saplings.shrink(sapling);
        spentSaplings += sapling;
      }
    }
  }
}
