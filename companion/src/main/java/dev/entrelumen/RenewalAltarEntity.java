package dev.entrelumen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/**
 * The Altar of Renewal: the vegetation altar. Fed with fertilizer, it grows in its square what its
 * biome grows (the biome's trees with curated variants, its flowers and ground plants) and a
 * symmetric dye garden, the same in every biome, whose harvest makes all sixteen dyes. It never
 * changes terrain: it only plants on exposed natural ground that the plant can live on, and never
 * near builds, containers, crops, entities or claims. The plan depends only on the world seed and
 * positions, so a repeated pass grows back only what was harvested and never piles up.
 */
public final class RenewalAltarEntity extends AltarBlockEntity {
  public enum State { IDLE, RUNNING, WAITING, PAUSED, DONE, FAILED }

  /** Counts of grown work and exact payment for the current pass. */
  public static final class Totals {
    public int trees, inkCaps, plants, dyeFlowers, guarded, fertilizer, charge;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putIntArray("values", new int[] {trees, inkCaps, plants, dyeFlowers, guarded, fertilizer, charge});
      return tag;
    }

    void load(CompoundTag tag) {
      int[] values = Arrays.copyOf(tag.getIntArray("values"), 7);
      trees = values[0];
      inkCaps = values[1];
      plants = values[2];
      dyeFlowers = values[3];
      guarded = values[4];
      fertilizer = values[5];
      charge = values[6];
    }
  }

  public static final TagKey<Item> FERTILIZERS = AltarType.RENEWAL.fuel();
  /** Version 2: vegetation. Version 1 (terrain reconstruction) is migrated to a fresh pass. */
  static final int VERSION = 2;
  static final int PREVIEW_TICKS = 100;
  private static final int PASSES = 2;

  private State state = State.IDLE;
  private int radius = AltarRules.RENEWAL_RADIUS;
  private int pass;
  /** Next column of the current pass, in concentric rings. */
  private int index;
  private int charge;
  private final Totals totals = new Totals();
  private List<long[]> columns;
  private long previewUntil;
  private long idleUntil;
  long ticks;
  long tickNanos;
  long maxTickNanos;
  long workingTicks;
  /** Durations of recent working ticks without GC pauses, for measurement only. */
  final long[] tickSamples = new long[4096];
  long gcTicks;
  long maxGcMillis;
  /** Wall time of the most expensive single tree simulation, and the recent ones, for measurement only. */
  long maxTreeNanos;
  /** Wall time of the tree simulated by the current unit, if any; drives the rest after it. */
  private long lastSimulationNanos;
  /** Ticks spent resting after trees, for measurement only. */
  long restedTicks;
  private final List<Long> treeSamples = new ArrayList<>();
  /** Why units were left alone in this pass, and which tree options grew; diagnostics, not saved. */
  final Map<String, Integer> guards = new java.util.TreeMap<>();
  final Map<String, Integer> grown = new java.util.TreeMap<>();

  public RenewalAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.RENEWAL_ENTITY.get(), pos, state, AltarType.RENEWAL);
  }

  public State state() {
    return state;
  }

  public Totals totals() {
    return totals;
  }

  public int charge() {
    return charge;
  }

  public int radius() {
    return radius;
  }

  public ItemStack fertilizer() {
    return fuel();
  }

  int index() {
    return index;
  }

  /** Microseconds of each tree simulation so far, in order; measurement only. */
  List<Long> treeMicros() {
    return List.copyOf(treeSamples);
  }

  int pass() {
    return pass;
  }

  /** Test hook: a smaller square that fits a GameTest template. */
  void configureRadius(int value) {
    radius = value;
    columns = null;
    setChanged();
  }

  void addFertilizer(ItemStack stack) {
    addFuel(stack);
  }

  @Override
  protected int areaRadius() {
    return radius;
  }

  @Override
  protected boolean working() {
    return state == State.RUNNING;
  }

  /** Each unit adds work charge as well as active time. */
  @Override
  protected void onFuelDrawn() {
    charge += AltarRules.CHARGE_PER_FERTILIZER;
    totals.fertilizer++;
  }

  // ---- Gestures -------------------------------------------------------------------------------

  /** Main-hand fertilizer: stored up to one stack; a new pass starts unless one is running. */
  @Override
  public void feed(ServerPlayer player, ItemStack held) {
    int moved = Math.min(acceptsFuel(held), held.getCount());
    if (moved <= 0) {
      player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.full", fuel.getCount()));
      return;
    }
    addFuel(held.copyWithCount(moved));
    held.shrink(moved);
    player.getInventory().setChanged();
    claimOwner(player);
    if (state == State.IDLE || state == State.DONE || state == State.FAILED) start(player);
    player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.fed", moved,
        fuel.getCount(), 2 * radius + 1));
  }

  /** Empty hand: report and show the square. */
  @Override
  public void use(ServerPlayer player) {
    previewUntil = player.serverLevel().getGameTime() + PREVIEW_TICKS;
    for (Component line : statusLines(player.serverLevel())) player.sendSystemMessage(line);
  }

  /** Crouched empty hand: pause or resume; a finished altar starts a fresh pass when it has fuel. */
  @Override
  public void crouchUse(ServerPlayer player) {
    claimOwner(player);
    switch (state) {
      case RUNNING, WAITING -> {
        state = State.PAUSED;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.paused"));
      }
      case PAUSED -> {
        state = State.RUNNING;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.resumed"));
      }
      default -> {
        if (charge <= 0 && fuel.isEmpty()) {
          player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.no_fertilizer"));
          return;
        }
        start(player);
        player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.started", 2 * radius + 1));
      }
    }
    setChanged();
  }

  List<Component> statusLines(ServerLevel world) {
    List<Component> lines = new ArrayList<>();
    int size = columns().size();
    double done = pass >= PASSES ? PASSES : pass + (size == 0 ? 0 : Math.min(1.0, index / (double) size));
    lines.add(Component.translatable("entrelumen.altar.renewal.status",
        Component.translatable("entrelumen.altar.state." + state.name().toLowerCase(java.util.Locale.ROOT)),
        2 * radius + 1, (int) Math.floor(done * 100 / PASSES)));
    var palette = AltarVegetation.palette(world, world.getBiome(worldPosition));
    lines.add(Component.translatable("entrelumen.altar.renewal.biome",
        Component.translatable("entrelumen.altar.renewal.variant." + palette.variant())));
    lines.add(Component.translatable("entrelumen.altar.renewal.progress", totals.trees, totals.inkCaps,
        totals.plants, totals.dyeFlowers, totals.guarded));
    lines.add(Component.translatable("entrelumen.altar.renewal.fuel", fuel.getCount(), charge,
        activeTicks / 20, totals.fertilizer));
    return lines;
  }

  /** A fresh pass over the whole square; the radius follows the team's Ark garden site when known. */
  void start(ServerPlayer by) {
    if (by != null && level instanceof ServerLevel serverLevel
        && (radius == AltarRules.RENEWAL_RADIUS || radius == AltarRules.RENEWAL_ARK_RADIUS)) {
      GlobalPos site = null;
      try {
        site = NatureRestorationData.get(serverLevel.getServer()).site(CampaignActions.campaignId(by));
      } catch (RuntimeException unavailable) {
        // Without a campaign identity the altar keeps its own radius.
      }
      boolean near = site != null && site.dimension().equals(serverLevel.dimension())
          && AltarRules.nearArkSite(worldPosition.getX(), worldPosition.getZ(), site.pos().getX(), site.pos().getZ());
      radius = AltarRules.renewalRadius(near);
    }
    columns = null;
    pass = 0;
    index = 0;
    totals.load(new CompoundTag());
    fuelUsed = 0;
    guards.clear();
    grown.clear();
    state = State.RUNNING;
    setChanged();
  }

  /** Every column of the square in concentric rings from the altar, as offsets and positions. */
  private List<long[]> columns() {
    if (columns == null) {
      int cx = worldPosition.getX(), cz = worldPosition.getZ();
      List<long[]> result = new ArrayList<>();
      for (var column : AltarRules.square(radius))
        result.add(new long[] {cx + column.dx(), cz + column.dz(), column.dx(), column.dz()});
      columns = List.copyOf(result);
    }
    return columns;
  }

  // ---- Ticking -------------------------------------------------------------------------------

  @Override
  public void serverTick(ServerLevel world) {
    long started = System.nanoTime();
    long gc = LandWorks.gcMillis();
    boolean worked = false;
    try {
      worked = tick(world);
    } finally {
      long paused = LandWorks.gcMillis() - gc;
      long spent = Math.max(0, System.nanoTime() - started - paused * 1_000_000L);
      if (worked && paused > 0) {
        gcTicks++;
        maxGcMillis = Math.max(maxGcMillis, paused);
      }
      if (worked) {
        tickSamples[(int) (workingTicks % tickSamples.length)] = spent;
        workingTicks++;
        tickNanos += spent;
        maxTickNanos = Math.max(maxTickNanos, spent);
      }
      ticks++;
      refreshRegistration();
    }
  }

  /**
   * Returns whether any world work was attempted this tick. The whole square advances together, in
   * concentric rings from the altar: first every tree and ink cap, then every plant and flower.
   */
  private boolean tick(ServerLevel world) {
    long now = world.getGameTime();
    if (previewUntil > now && now % 10 == 0) preview(world);
    if (state == State.IDLE && !fuel.isEmpty()) start(null);
    if (state == State.WAITING && fuelAvailable() && AltarRules.affordable(charge, fuel.getCount(), 1))
      state = State.RUNNING;
    if (state != State.RUNNING) return false;
    if (pass >= PASSES) {
      finish(world);
      return false;
    }
    if (now < idleUntil) return false;
    if (!actorWarm(world)) return false;
    if (!areaLoaded(world)) {
      idleUntil = now + 40;
      return false;
    }
    // Only ticks that actually work pay active time; waiting for chunks is free.
    if (!payActiveTick()) {
      state = State.WAITING;
      setChanged();
      return false;
    }
    AltarRules.TickBudget budget = AltarRules.TickBudget.standard();
    Player actor = actor(world);
    List<long[]> square = columns();
    while (budget.open() && state == State.RUNNING) {
      if (pass >= PASSES) {
        finish(world);
        return true;
      }
      if (index >= square.size()) {
        pass++;
        index = 0;
        setChanged();
        continue;
      }
      budget.scan();
      long[] column = square.get(index);
      lastSimulationNanos = 0;
      Unit unit = pass == 0 ? woody(world, actor, column, budget) : flora(world, actor, column, budget);
      if (lastSimulationNanos > 0 && unit != Unit.FERTILIZER && unit != Unit.BUDGET) {
        // A tree cannot be split across ticks. After one, the altar rests in proportion to what it
        // cost, so its average stays within the per-tick budget: a 20 ms giant tree buys 9 idle ticks.
        index++;
        if (unit == Unit.GUARDED) totals.guarded++;
        idleUntil = now + restTicks(lastSimulationNanos);
        restedTicks += restTicks(lastSimulationNanos);
        setChanged();
        return true;
      }
      switch (unit) {
        case FERTILIZER -> {
          state = State.WAITING;
          setChanged();
          return true;
        }
        case BUDGET -> {
          return true;
        }
        case GUARDED -> {
          totals.guarded++;
          index++;
          setChanged();
        }
        default -> {
          index++;
          setChanged();
        }
      }
    }
    return true;
  }

  private void finish(ServerLevel world) {
    state = State.DONE;
    setChanged();
    if (owner != null) {
      ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
      if (player != null)
        player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.done",
            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), totals.trees + totals.inkCaps,
            totals.plants, totals.dyeFlowers, totals.fertilizer));
    }
  }

  /**
   * The square, every canopy it may grow and their neighbours are loaded. The altar never loads a
   * chunk; it waits until players have the whole area loaded, so the rings stay symmetric.
   */
  private boolean areaLoaded(ServerLevel world) {
    int[] bounds = AltarRules.loadedBounds(worldPosition.getX(), worldPosition.getZ(), radius);
    return LandWorks.loaded(world, bounds[0], bounds[1], bounds[2], bounds[3]);
  }

  /** Particles on the square's edge, at ground height, on all four sides. */
  private void preview(ServerLevel world) {
    for (int i = -radius; i <= radius; i += 2)
      for (int[] offset : new int[][] {{i, -radius}, {i, radius}, {-radius, i}, {radius, i}}) {
        int x = worldPosition.getX() + offset[0], z = worldPosition.getZ() + offset[1];
        if (!world.hasChunk(x >> 4, z >> 4)) continue;
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, y + 0.3, z + 0.5, 1, 0, 0, 0, 0);
      }
  }

  // ---- Payment -------------------------------------------------------------------------------

  private boolean affordable(int cost) {
    return AltarRules.affordable(charge, fuel.getCount(), cost);
  }

  /** Draws fertilizer only while the charge does not cover the cost; each unit also pays time. */
  private void pay(int cost) {
    while (charge < cost && drawFuel()) {
      // onFuelDrawn adds the charge
    }
    charge -= cost;
    totals.charge += cost;
    setChanged();
  }

  // ---- Work units ---------------------------------------------------------------------------

  private enum Unit { APPLIED, NONE, GUARDED, FERTILIZER, BUDGET }

  private Unit guard(String reason) {
    guards.merge(reason, 1, Integer::sum);
    return Unit.GUARDED;
  }

  private VegetationRules.Role role(ServerLevel world, long[] column, AltarVegetation.Palette palette) {
    return VegetationRules.role(world.getSeed(), (int) column[0], (int) column[1], (int) column[2],
        (int) column[3], radius, palette.treeProbability());
  }

  /** Pass one: a tree of the biome, a giant cactus or an ink cap, whole or not at all. */
  private Unit woody(ServerLevel world, Player actor, long[] column, AltarRules.TickBudget budget) {
    int x = (int) column[0], z = (int) column[1];
    BlockPos ground = groundAt(world, x, z);
    if (ground == null) return Unit.NONE;
    var palette = AltarVegetation.palette(world, world.getBiome(ground.above()));
    VegetationRules.Role role = role(world, column, palette);
    if (role == VegetationRules.Role.INK_CAP) return grow(world, actor, x, z, ground, null, budget);
    if (role != VegetationRules.Role.TREE) return Unit.NONE;
    List<AltarVegetation.TreeOption> options = palette.treesFor(world.getBlockState(ground));
    if (options.isEmpty()) return Unit.NONE;
    double[] weights = options.stream().mapToDouble(AltarVegetation.TreeOption::weight).toArray();
    int chosen = NatureRestorationRules.weightedIndex(weights,
        NatureRestorationRules.unit(world.getSeed(), x, z, VegetationRules.SPECIES_SALT));
    if (chosen < 0) return Unit.NONE;
    return grow(world, actor, x, z, ground, options.get(chosen), budget);
  }

  /** Idle ticks after a unit that cost this much, so the altar averages one tick budget per tick. */
  static int restTicks(long nanos) {
    return (int) Math.max(0, Math.min(200, Math.ceilDiv(nanos, AltarRules.NANOS_PER_TICK) - 1));
  }

  /** Any entity inside any of these blocks, with one entity query over their bounding box. */
  private static boolean occupiedAny(ServerLevel world, java.util.Collection<BlockPos> blocks) {
    if (blocks.isEmpty()) return false;
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    for (BlockPos pos : blocks) {
      minX = Math.min(minX, pos.getX());
      minY = Math.min(minY, pos.getY());
      minZ = Math.min(minZ, pos.getZ());
      maxX = Math.max(maxX, pos.getX());
      maxY = Math.max(maxY, pos.getY());
      maxZ = Math.max(maxZ, pos.getZ());
    }
    var entities = world.getEntities((net.minecraft.world.entity.Entity) null,
        new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
    for (var entity : entities) {
      var box = entity.getBoundingBox();
      for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++)
        for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++)
          for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++)
            if (blocks.contains(new BlockPos(x, y, z))) return true;
    }
    return false;
  }

  /** Test hook: grows one option (or the ink cap for null) on this ground now, through the same checks. */
  String growForTest(ServerLevel world, BlockPos ground, AltarVegetation.TreeOption option) {
    return grow(world, actor(world), ground.getX(), ground.getZ(), ground, option,
        new AltarRules.TickBudget(4096, 4096, 1_000_000_000L, System::nanoTime)).name();
  }

  /** One tree option, or the ink cap when {@code option} is null, grown whole at a column or not at all. */
  private Unit grow(ServerLevel world, Player actor, int x, int z, BlockPos ground,
      AltarVegetation.TreeOption option, AltarRules.TickBudget budget) {
    BlockState soil = world.getBlockState(ground);
    BlockPos origin = ground.above();
    if (!open(world.getBlockState(origin))) return Unit.NONE;
    if (nearTree(world, origin)) return Unit.NONE;
    // A tree is simulated only at the start of a tick, so it is never simulated twice for want of budget.
    if (budget.used() > 0) return Unit.BUDGET;
    long seed = world.getSeed();
    LinkedHashMap<BlockPos, BlockState> writes;
    String kind;
    if (option == null) {
      var cap = AltarVegetation.garden(world.registryAccess()).inkCap();
      if (cap == null || !soil.is(BlockTags.DIRT)) return Unit.NONE;
      writes = simulate(world, cap.value(), seed, x, z, origin, ground.getY());
      kind = AltarVegetation.INK_CAP;
    } else {
      if (!option.soil().accepts(soil)) return Unit.NONE;
      kind = option.id();
      if (option.giantCactus()) writes = giantCactus(world, seed, x, z, ground);
      else {
        writes = simulate(world, option.feature().value(), seed, x, z, origin, ground.getY());
        if (writes != null && option.cocoa()) cocoa(world, seed, writes, ground.getY());
      }
    }
    if (writes == null) return guard("tree_unplaceable");
    if (writes.isEmpty()) return Unit.NONE;
    for (var entry : writes.entrySet()) {
      BlockState now = world.getBlockState(entry.getKey());
      if (!replaceable(now, entry.getValue()))
        return guard("tree_blocked:" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath());
    }
    if (occupiedAny(world, writes.keySet())) return guard("tree_entity");
    if (!LandWorks.surroundedByLand(world, writes.keySet(), writes.keySet(), pos -> false)) return guard("tree_margin");
    int cost = AltarRules.treeCost(writes.size());
    if (!affordable(cost)) return Unit.FERTILIZER;
    if (!budget.allows(writes.size())) return Unit.BUDGET;
    if (!LandWorks.commit(actor, world, writes, Map.of(), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE))
      return guard("tree_claim");
    pay(cost);
    if (option == null) totals.inkCaps++;
    else totals.trees++;
    grown.merge(kind, 1, Integer::sum);
    budget.spend(writes.size());
    return Unit.APPLIED;
  }

  /** Pass two: a garden bed's dye flower, or a flora column's flower or ground plant. */
  private Unit flora(ServerLevel world, Player actor, long[] column, AltarRules.TickBudget budget) {
    int x = (int) column[0], z = (int) column[1], dx = (int) column[2], dz = (int) column[3];
    BlockPos top = surfaceAt(world, x, z);
    if (top == null) return Unit.NONE;
    var palette = AltarVegetation.palette(world, world.getBiome(top.above()));
    VegetationRules.Role role = role(world, column, palette);
    if (role != VegetationRules.Role.BED && role != VegetationRules.Role.FLORA) return Unit.NONE;
    BlockState surface = world.getBlockState(top);
    BlockPos pos = top.above();
    BlockState cover = world.getBlockState(pos);
    // A single snow layer is weather, not growth: snowy land still grows its garden.
    if (!cover.isAir() && !(cover.is(Blocks.SNOW) && cover.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 1))
      return Unit.NONE;
    long seed = world.getSeed();
    List<BlockState> garden = AltarVegetation.garden(world.registryAccess()).species();
    List<BlockState> candidates = new ArrayList<>();
    boolean dye = false;
    if (surface.is(Blocks.WATER) && surface.getFluidState().isSource()) {
      if (!palette.lilyPads() || role != VegetationRules.Role.FLORA) return Unit.NONE;
      candidates.add(Blocks.LILY_PAD.defaultBlockState());
    } else if (!LandWorks.ground(surface)) {
      return Unit.NONE;
    } else if (role == VegetationRules.Role.BED) {
      int first = VegetationRules.bedSpecies(dx, dz, garden.size());
      for (int i = 0; i < garden.size(); i++) candidates.add(garden.get((first + i) % garden.size()));
      dye = true;
    } else {
      RandomSource random = RandomSource.create(NatureRestorationRules.mix(seed, x, z, VegetationRules.SPECIES_SALT));
      if (VegetationRules.flower(seed, x, z)) {
        if (VegetationRules.dyeFlower(seed, x, z) || palette.flowers().isEmpty()) {
          int pick = VegetationRules.pick(seed, x, z, VegetationRules.SPECIES_SALT, garden.size());
          if (pick >= 0) candidates.add(garden.get(pick));
          dye = true;
        } else {
          var provider = palette.flowers().get(VegetationRules.pick(seed, x, z, VegetationRules.DYE_SALT,
              palette.flowers().size()));
          candidates.add(provider.getState(random, pos));
        }
      }
      for (var provider : palette.grasses()) candidates.add(provider.getState(random, pos));
      candidates.add(Blocks.SHORT_GRASS.defaultBlockState());
    }
    LinkedHashMap<BlockPos, BlockState> writes = null;
    BlockState planted = null;
    for (BlockState candidate : candidates) {
      if (candidate == null || !candidate.is(Blocks.LILY_PAD) && !AltarVegetation.plant(candidate)) continue;
      writes = placement(world, pos, candidate);
      if (writes != null) {
        planted = candidate;
        break;
      }
    }
    if (writes == null) return Unit.NONE;
    for (BlockPos write : writes.keySet()) if (LandWorks.occupied(world, write)) return guard("plant_entity");
    if (!LandWorks.surroundedByLand(world, writes.keySet(), writes.keySet(), p -> false)) return guard("plant_margin");
    if (!budget.allows(writes.size())) return Unit.BUDGET;
    if (!affordable(AltarRules.BLOCK_CHARGE)) return Unit.FERTILIZER;
    if (!LandWorks.commit(actor, world, writes, Map.of(), Block.UPDATE_ALL)) return guard("plant_claim");
    pay(AltarRules.BLOCK_CHARGE);
    totals.plants++;
    if (dye && garden.contains(planted.getBlock().defaultBlockState())) totals.dyeFlowers++;
    grown.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(planted.getBlock()).toString(), 1,
        Integer::sum);
    budget.spend(writes.size());
    return Unit.APPLIED;
  }

  /** The blocks of one plant at a position (two for a double plant), or null if it cannot live there. */
  private static LinkedHashMap<BlockPos, BlockState> placement(ServerLevel world, BlockPos pos, BlockState state) {
    LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
    if (state.getBlock() instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)) {
      BlockPos upper = pos.above();
      if (!world.getBlockState(upper).isAir()) return null;
      BlockState lower = state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
      if (!lower.canSurvive(world, pos)) return null;
      writes.put(pos, lower);
      writes.put(upper, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
      return writes;
    }
    if (!state.canSurvive(world, pos)) return null;
    writes.put(pos, state);
    return writes;
  }

  /**
   * Runs a tree feature in a sandbox over the live level and keeps only what grows above ground:
   * dirt placed under a trunk and roots driven into the soil are dropped, so terrain is never
   * changed, and block entities (bee nests) are never placed. Null when the feature does not place.
   */
  private LinkedHashMap<BlockPos, BlockState> simulate(ServerLevel world, ConfiguredFeature<?, ?> feature, long seed,
      int x, int z, BlockPos origin, int groundY) {
    int reach = radius + AltarRules.TREE_MARGIN;
    BoundingBox permitted = new BoundingBox(worldPosition.getX() - reach, world.getMinBuildHeight(),
        worldPosition.getZ() - reach, worldPosition.getX() + reach, world.getMaxBuildHeight() - 1,
        worldPosition.getZ() + reach);
    long started = System.nanoTime();
    var result = FeatureSandbox.simulate(world, permitted, feature,
        RandomSource.create(NatureRestorationRules.mix(seed, x, z, VegetationRules.TREE_SALT)), origin);
    long spent = System.nanoTime() - started;
    lastSimulationNanos = Math.max(1, spent);
    maxTreeNanos = Math.max(maxTreeNanos, spent);
    if (treeSamples.size() < 256) treeSamples.add(spent / 1000);
    if (result == null) return null;
    LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
    for (var entry : result.writes().entrySet()) {
      BlockPos pos = entry.getKey();
      BlockState state = entry.getValue();
      if (state.hasBlockEntity() || state.isAir()) continue;
      BlockState now = world.getBlockState(pos);
      if (pos.getY() <= groundY && LandWorks.ground(now)) continue;
      if (LandWorks.ground(state) && !state.is(BlockTags.LOGS)) {
        // Dirt or soil a feature lays: terrain, which this altar never places.
        continue;
      }
      writes.put(pos, state);
    }
    return writes;
  }

  /** A giant cactus in sand, as {@link VegetationRules#giantCactus} shapes it, or null when it cannot stand. */
  private static LinkedHashMap<BlockPos, BlockState> giantCactus(ServerLevel world, long seed, int x, int z,
      BlockPos ground) {
    LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
    BlockState cactus = Blocks.CACTUS.defaultBlockState();
    for (var column : VegetationRules.giantCactus(seed, x, z)) {
      int cx = x + column.dx(), cz = z + column.dz();
      BlockPos base = new BlockPos(cx, ground.getY(), cz);
      if (!world.getBlockState(base).is(BlockTags.SAND)) return null;
      for (int h = 1; h <= column.height(); h++) writes.put(base.above(h), cactus);
    }
    // Every cactus block needs non-solid sides; the shape never touches itself face to face.
    for (BlockPos pos : writes.keySet())
      for (Direction side : Direction.Plane.HORIZONTAL) {
        BlockPos next = pos.relative(side);
        if (writes.containsKey(next)) return null;
        BlockState beside = world.getBlockState(next);
        if (beside.isSolid() || !beside.getFluidState().isEmpty()) return null;
      }
    return writes;
  }

  /** Cocoa pods on the lower jungle trunk: about one face in four, stable per position. */
  private static void cocoa(ServerLevel world, long seed, LinkedHashMap<BlockPos, BlockState> writes, int groundY) {
    List<Map.Entry<BlockPos, BlockState>> pods = new ArrayList<>();
    for (var entry : writes.entrySet()) {
      BlockPos log = entry.getKey();
      if (!entry.getValue().is(BlockTags.JUNGLE_LOGS) || log.getY() < groundY + 2 || log.getY() > groundY + 5) continue;
      for (Direction side : Direction.Plane.HORIZONTAL) {
        BlockPos pod = log.relative(side);
        // Pods take the place of trunk vines; anything else of the tree keeps its spot.
        BlockState planned = writes.get(pod);
        if (planned != null && !planned.is(Blocks.VINE) || !world.getBlockState(pod).isAir()) continue;
        if (!NatureRestorationRules.selected(seed, pod.getX() * 31 + side.ordinal(), pod.getZ() + pod.getY() * 131,
            VegetationRules.SHAPE_SALT, 0.25)) continue;
        int age = VegetationRules.pick(seed, pod.getX(), pod.getZ() + pod.getY(), VegetationRules.SPECIES_SALT, 3);
        pods.add(Map.entry(pod, Blocks.COCOA.defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, side.getOpposite()).setValue(CocoaBlock.AGE, age)));
      }
    }
    for (var pod : pods) writes.put(pod.getKey(), pod.getValue());
  }

  /** A write may only go into air, a replaceable wild plant, or water for a waterlogged block. */
  private static boolean replaceable(BlockState now, BlockState written) {
    if (now.isAir() || now.equals(written)) return true;
    if (now.is(Blocks.WATER) && now.getFluidState().isSource())
      return written.hasProperty(BlockStateProperties.WATERLOGGED) && written.getFluidState().is(Fluids.WATER);
    return LandWorks.natural(now) && now.getFluidState().isEmpty() && now.canBeReplaced()
        && !now.is(BlockTags.FLOWERS);
  }

  /** Nothing but air or a replaceable wild plant (grass, snow layer) where a tree's trunk starts. */
  private static boolean open(BlockState state) {
    return state.isAir() || LandWorks.natural(state) && state.canBeReplaced() && state.getFluidState().isEmpty()
        && !state.is(BlockTags.FLOWERS);
  }

  /** A log, stem, sapling or huge mushroom nearby: trees keep their distance. */
  private static boolean nearTree(ServerLevel world, BlockPos origin) {
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    int spacing = VegetationRules.TREE_SPACING;
    for (int dx = -spacing; dx <= spacing; dx++)
      for (int dz = -spacing; dz <= spacing; dz++)
        for (int y = origin.getY() - 1; y <= origin.getY() + 6; y++) {
          BlockState state = world.getBlockState(cursor.set(origin.getX() + dx, y, origin.getZ() + dz));
          if (state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.MUSHROOM_STEM)
              || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
              || state.is(Blocks.CACTUS) || state.is(Blocks.CHORUS_PLANT) || state.is(BlockTags.WART_BLOCKS)
              || state.is(Blocks.CRIMSON_STEM) || state.is(Blocks.WARPED_STEM))
            return true;
        }
    return false;
  }

  /**
   * The exposed natural ground of a column: the top block under grass, flowers and snow layers,
   * ignoring leaves. Null when it is built, a fluid, a plant or a trunk.
   */
  static BlockPos groundAt(ServerLevel world, int x, int z) {
    BlockPos top = surfaceAt(world, x, z);
    if (top == null) return null;
    return LandWorks.ground(world.getBlockState(top)) ? top : null;
  }

  /**
   * The top block of a column that blocks motion or holds a fluid, ignoring leaves, or null
   * outside the world. A snow layer thick enough to block motion counts as cover: the block below.
   */
  static BlockPos surfaceAt(ServerLevel world, int x, int z) {
    int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    if (y < world.getMinBuildHeight()) return null;
    BlockPos top = new BlockPos(x, y, z);
    BlockState state = world.getBlockState(top);
    if (state.is(Blocks.SNOW)) return top.below();
    return top;
  }

  // ---- Persistence ---------------------------------------------------------------------------

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    int version = tag.getInt("version");
    if (version > VERSION) throw new IllegalStateException("Unsupported renewal altar version " + version);
    state = parse(tag.getString("state"));
    radius = tag.contains("radius", Tag.TAG_INT) ? Math.max(1, Math.min(64, tag.getInt("radius")))
        : AltarRules.RENEWAL_RADIUS;
    columns = null;
    charge = Math.max(0, tag.getInt("charge"));
    if (version < 2) {
      // Version 1 rebuilt terrain in three other passes; its progress means nothing now. A running
      // altar starts a fresh vegetation pass, keeping its fertilizer and charge.
      pass = 0;
      index = 0;
      totals.load(new CompoundTag());
      if (state == State.FAILED) state = State.DONE;
      return;
    }
    pass = Math.max(0, Math.min(PASSES, tag.getInt("pass")));
    index = Math.max(0, tag.getInt("index"));
    totals.load(tag.getCompound("totals"));
  }

  private static State parse(String name) {
    for (State value : State.values()) if (value.name().equals(name)) return value;
    return State.IDLE;
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("version", VERSION);
    tag.putString("state", state.name());
    tag.putInt("radius", radius);
    tag.putInt("pass", pass);
    tag.putInt("index", index);
    tag.putInt("charge", charge);
    tag.put("totals", totals.save());
  }
}
