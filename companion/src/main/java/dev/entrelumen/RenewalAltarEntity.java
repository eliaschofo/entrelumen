package dev.entrelumen;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Renewal Altar's work. Fed with fertilizer, it compares every chunk of its square with what the
 * world generator originally made there ({@link TerrainReference}) and, a few blocks per tick,
 * refills dug holes up to the original surface, re-covers bare dirt, regrows the original trees
 * and replants the original plants. Only open, natural positions are written; builds, containers,
 * crops, entities and claims are left alone. Progress is saved; the plan is recomputed from the
 * world, so a resumed or repeated pass only finds what is still missing and never pays twice.
 */
public final class RenewalAltarEntity extends AltarBlockEntity {
  public enum State { IDLE, RUNNING, WAITING, PAUSED, DONE, FAILED }

  /** Counts of restored work and exact payment for the current pass. */
  public static final class Totals {
    public int filled, covered, trees, plants, guarded, skippedChunks, fertilizer, charge;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putIntArray("values", new int[] {filled, covered, trees, plants, guarded, skippedChunks, fertilizer, charge});
      return tag;
    }

    void load(CompoundTag tag) {
      int[] values = Arrays.copyOf(tag.getIntArray("values"), 8);
      filled = values[0];
      covered = values[1];
      trees = values[2];
      plants = values[3];
      guarded = values[4];
      skippedChunks = values[5];
      fertilizer = values[6];
      charge = values[7];
    }

    /** Charge the applied work must have cost. */
    public int expectedCharge(int treeCharge) {
      return filled + covered + plants + treeCharge;
    }
  }

  public static final TagKey<Item> FERTILIZERS = AltarType.RENEWAL.fuel();
  static final int VERSION = 1;
  static final int PREVIEW_TICKS = 100;
  private static final int PASSES = 3;
  private static final Set<Block> COVERS = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK);
  private static final Set<Block> BARE = Set.of(Blocks.DIRT, Blocks.COARSE_DIRT);
  /** Wild plants outside the replaceable and flower tags; pumpkins and melons stay out (no free harvest). */
  private static final Set<Block> EXTRA_PLANTS = Set.of(Blocks.CACTUS, Blocks.SUGAR_CANE,
      Blocks.SWEET_BERRY_BUSH, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.BAMBOO, Blocks.MOSS_CARPET);
  private static final Set<Block> CANOPY_EXTRAS = Set.of(Blocks.VINE, Blocks.COCOA, Blocks.BROWN_MUSHROOM_BLOCK,
      Blocks.RED_MUSHROOM_BLOCK, Blocks.MANGROVE_ROOTS, Blocks.MOSS_CARPET, Blocks.HANGING_ROOTS);
  /** Test hook: a generator other than the level's own, keyed by altar position. Never set in play. */
  static final Map<GlobalPos, Function<ServerLevel, TerrainReference.Setup>> SETUPS = new ConcurrentHashMap<>();

  private State state = State.IDLE;
  private int radius = AltarRules.RENEWAL_RADIUS;
  private int pass;
  /** Next unit of the current pass: a disc column (passes 0 and 2) or an original tree (pass 1). */
  private int index;
  private int charge;
  private final Totals totals = new Totals();
  private String failure = "";

  private List<AltarRules.ChunkKey> area;
  private CompletableFuture<Prepared> job;
  private TerrainReference.Region region;
  private List<Tree> trees = List.of();
  long treeNanos;
  /** Disc columns in concentric order, and which chunks still look generated; not saved. */
  private List<long[]> columns;
  private final Map<AltarRules.ChunkKey, Boolean> trust = new java.util.HashMap<>();
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

  int pass() {
    return pass;
  }

  TerrainReference.Region region() {
    return region;
  }

  /** Test hook: a smaller square that fits a GameTest template. */
  void configureRadius(int value) {
    radius = value;
    area = null;
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
    for (Component line : statusLines()) player.sendSystemMessage(line);
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

  List<Component> statusLines() {
    List<Component> lines = new ArrayList<>();
    int size = pass == 1 ? trees.size() : columns().size();
    double done = pass >= PASSES ? PASSES : pass + (size == 0 ? 0 : Math.min(1.0, index / (double) size));
    lines.add(Component.translatable("entrelumen.altar.renewal.status",
        Component.translatable("entrelumen.altar.state." + state.name().toLowerCase(java.util.Locale.ROOT)),
        2 * radius + 1, (int) Math.floor(done * 100 / PASSES)));
    lines.add(Component.translatable("entrelumen.altar.renewal.progress", totals.filled, totals.covered,
        totals.trees, totals.plants, totals.guarded, totals.skippedChunks));
    lines.add(Component.translatable("entrelumen.altar.renewal.fuel", fuel.getCount(), charge,
        activeTicks / 20, totals.fertilizer));
    if (state == State.RUNNING && region == null)
      lines.add(Component.translatable("entrelumen.altar.renewal.generating"));
    if (state == State.FAILED)
      lines.add(Component.translatable("entrelumen.altar.renewal.failed", failure));
    return lines;
  }

  /** A fresh pass over the whole disc; the radius follows the team's Ark site when known. */
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
    cancelJob();
    area = null;
    columns = null;
    trust.clear();
    pass = 0;
    index = 0;
    region = null;
    trees = List.of();
    failure = "";
    totals.load(new CompoundTag());
    fuelUsed = 0;
    guards.clear();
    state = State.RUNNING;
    setChanged();
  }

  private List<AltarRules.ChunkKey> area() {
    if (area == null) area = AltarRules.areaChunks(worldPosition.getX(), worldPosition.getZ(), radius);
    return area;
  }

  /** Every column of the square in concentric rings from the altar. */
  private List<long[]> columns() {
    if (columns == null) {
      int cx = worldPosition.getX(), cz = worldPosition.getZ();
      List<long[]> result = new ArrayList<>();
      for (var column : AltarRules.square(radius)) result.add(new long[] {cx + column.dx(), cz + column.dz()});
      columns = List.copyOf(result);
    }
    return columns;
  }

  void cancelJob() {
    if (job != null) job.cancel(false);
    job = null;
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    cancelJob();
    region = null;
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
   * Returns whether any world work was attempted this tick. The whole disc advances together, in
   * concentric rings from the altar: first every column's terrain and cover, then every original
   * tree by its trunk's distance, then every column's plants.
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
    if (region == null && !reference(world)) return false;
    if (!actorWarm(world)) return false;
    if (!areaLoaded(world)) {
      idleUntil = now + 40;
      return false;
    }
    // Only ticks that actually work pay active time; generating and waiting for chunks are free.
    if (!payActiveTick()) {
      state = State.WAITING;
      setChanged();
      return false;
    }
    AltarRules.TickBudget budget = AltarRules.TickBudget.standard();
    Player actor = actor(world);
    List<long[]> disc = columns();
    while (budget.open() && state == State.RUNNING) {
      if (pass >= PASSES) {
        finish(world);
        return true;
      }
      int size = pass == 1 ? trees.size() : disc.size();
      if (index >= size) {
        pass++;
        index = 0;
        setChanged();
        continue;
      }
      budget.scan();
      Unit unit = switch (pass) {
        case 0 -> terrain(world, actor, disc.get(index), budget);
        case 1 -> tree(world, actor, trees.get(index), budget);
        default -> plant(world, actor, disc.get(index), budget);
      };
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
    region = null;
    trees = List.of();
    setChanged();
    if (owner != null) {
      ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
      if (player != null)
        player.sendSystemMessage(Component.translatable("entrelumen.altar.renewal.done",
            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), totals.filled, totals.covered,
            totals.trees, totals.plants, totals.fertilizer));
    }
  }

  /** Starts or collects the sandbox generation; true once the reference is available. */
  private boolean reference(ServerLevel world) {
    if (job == null) {
      List<ChunkPos> decorate = new ArrayList<>();
      Set<ChunkPos> captured = new HashSet<>();
      for (AltarRules.ChunkKey key : area()) {
        decorate.add(new ChunkPos(key.x(), key.z()));
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) captured.add(new ChunkPos(key.x() + dx, key.z() + dz));
      }
      var override = SETUPS.get(GlobalPos.of(world.dimension(), worldPosition));
      TerrainReference.Setup setup = override != null ? override.apply(world)
          : TerrainReference.Setup.capture(world, captured);
      List<AltarRules.ChunkKey> keys = List.copyOf(area());
      int cx = worldPosition.getX(), cz = worldPosition.getZ(), reach = radius;
      job = TerrainReference.generate(setup, decorate, this::isRemoved)
          .thenApplyAsync(built -> Prepared.of(built, keys, cx, cz, reach), TerrainReference.EXECUTOR);
      return false;
    }
    if (!job.isDone()) return false;
    try {
      Prepared prepared = job.join();
      region = prepared.region();
      trees = prepared.trees();
      treeNanos = prepared.treeNanos();
    } catch (RuntimeException failed) {
      Throwable cause = failed.getCause() == null ? failed : failed.getCause();
      failure = cause.getClass().getSimpleName();
      state = State.FAILED;
      com.mojang.logging.LogUtils.getLogger().warn("Renewal altar at {} could not regenerate its reference",
          worldPosition, cause);
      setChanged();
    } finally {
      job = null;
    }
    return region != null;
  }

  /**
   * The disc, every canopy it may grow and their neighbours are loaded. The altar never loads a
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
  // Only the pass and the unit index are saved. A unit finishes before the index moves, and after a
  // reload positions already restored simply match their reference.

  /** Why units were left alone in this pass; diagnostics for status and tests, not saved. */
  final Map<String, Integer> guards = new java.util.TreeMap<>();

  private Unit guard(String reason) {
    guards.merge(reason, 1, Integer::sum);
    return Unit.GUARDED;
  }

  private enum Unit { APPLIED, NONE, GUARDED, FERTILIZER, BUDGET }

  /** One tree: its blocks in the reference and the lowest trunk block that anchors it. */
  record Tree(BlockPos anchor, List<BlockPos> blocks) {}

  /** A finished reference with its original trees in ring order, both built off the server thread. */
  record Prepared(TerrainReference.Region region, List<Tree> trees, long treeNanos) {
    static Prepared of(TerrainReference.Region region, List<AltarRules.ChunkKey> area, int cx, int cz, int radius) {
      long started = System.nanoTime();
      List<Tree> all = new ArrayList<>();
      for (AltarRules.ChunkKey key : area) all.addAll(treesIn(region, key, cx, cz, radius));
      all.sort(Comparator.comparingLong((Tree t) -> {
            long dx = t.anchor().getX() - cx, dz = t.anchor().getZ() - cz;
            return dx * dx + dz * dz;
          }).thenComparingInt(t -> t.anchor().getX()).thenComparingInt(t -> t.anchor().getZ()));
      return new Prepared(region, List.copyOf(all), System.nanoTime() - started);
    }
  }

  /**
   * The chunk holding this column still looks like its generator's output; otherwise the altar
   * leaves the whole chunk alone. Checked once per chunk and remembered until the next pass.
   */
  private boolean trusted(ServerLevel world, int columnX, int columnZ, AltarRules.TickBudget budget) {
    AltarRules.ChunkKey key = new AltarRules.ChunkKey(columnX >> 4, columnZ >> 4);
    Boolean known = trust.get(key);
    if (known != null) return known;
    budget.spend(8);
    boolean result = consistent(world, key);
    trust.put(key, result);
    int untrusted = 0;
    for (boolean value : trust.values()) if (!value) untrusted++;
    totals.skippedChunks = Math.max(totals.skippedChunks, untrusted);
    return result;
  }

  private boolean consistent(ServerLevel world, AltarRules.ChunkKey key) {
    if (region.unresolved(key.x(), key.z()) || !region.decorated(key.x(), key.z())) return false;
    int natural = 0, agree = 0, surplus = 0;
    for (int x = key.minX(); x < key.minX() + 16; x++)
      for (int z = key.minZ(); z < key.minZ() + 16; z++) {
        int reference = referenceGround(x, z);
        int current = currentGround(world, x, z);
        if (reference == Integer.MIN_VALUE || current == Integer.MIN_VALUE) continue;
        natural++;
        if (Math.abs(current - reference) <= 1) agree++;
        else if (current > reference + 1) surplus++;
      }
    return AltarRules.consistent(natural, agree, surplus);
  }

  /** Refills an open hole up to the original surface, bottom-up, then re-covers bare dirt. */
  private Unit terrain(ServerLevel world, Player actor, long[] column, AltarRules.TickBudget budget) {
    int x = (int) column[0], z = (int) column[1];
    if (!trusted(world, x, z, budget)) return Unit.NONE;
    int top = referenceGround(x, z);
    if (top == Integer.MIN_VALUE) return Unit.NONE;
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    int[] fills = AltarRules.fillColumn(top, y -> fillable(region.state(cursor.set(x, y, z))),
        y -> cell(world.getBlockState(cursor.set(x, y, z))));
    for (int y : fills) {
      if (!budget.allows(1)) return Unit.BUDGET;
      BlockPos pos = new BlockPos(x, y, z);
      BlockState target = supported(pos, region.state(pos));
      if (!guardedWrite(pos, Set.of(pos))) return guard("fill_margin");
      if (!affordable(AltarRules.BLOCK_CHARGE)) return Unit.FERTILIZER;
      if (!LandWorks.commit(actor, world, Map.of(pos, target), Map.of(), Block.UPDATE_ALL)) return guard("fill_claim");
      pay(AltarRules.BLOCK_CHARGE);
      totals.filled++;
      budget.spend(1);
    }
    BlockPos surface = new BlockPos(x, top, z);
    BlockState now = world.getBlockState(surface);
    BlockState original = region.state(surface);
    if (original == null || !BARE.contains(now.getBlock()) || !COVERS.contains(original.getBlock())
        || now.is(original.getBlock())) return Unit.NONE;
    BlockState above = world.getBlockState(surface.above());
    if (!above.isAir() && !(LandWorks.natural(above) && above.canBeReplaced() && above.getFluidState().isEmpty()))
      return Unit.NONE;
    if (!budget.allows(1)) return Unit.BUDGET;
    if (!guardedWrite(surface, Set.of(surface))) return guard("cover_margin");
    if (!affordable(AltarRules.BLOCK_CHARGE)) return Unit.FERTILIZER;
    BlockState cover = original.getBlock().defaultBlockState();
    if (!LandWorks.commit(actor, world, Map.of(surface, cover), Map.of(), Block.UPDATE_ALL)) return guard("cover_claim");
    pay(AltarRules.BLOCK_CHARGE);
    totals.covered++;
    budget.spend(1);
    return Unit.APPLIED;
  }

  /** Regrows or completes one original tree as a whole, or leaves it for a later pass. */
  private Unit tree(ServerLevel world, Player actor, Tree tree, AltarRules.TickBudget budget) {
    if (!trusted(world, tree.anchor().getX(), tree.anchor().getZ(), budget)) return Unit.NONE;
    LinkedHashMap<BlockPos, BlockState> missing = new LinkedHashMap<>();
    for (BlockPos pos : tree.blocks()) {
      if (!LandWorks.loaded(world, pos)) return guard("tree_unloaded");
      BlockState original = region.state(pos);
      BlockState now = world.getBlockState(pos);
      if (same(original, now)) continue;
      boolean open = now.isAir() || LandWorks.natural(now) && now.getFluidState().isEmpty()
          && (now.canBeReplaced() || now.is(original.getBlock()));
      if (!open) return guard("tree_blocked:" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath());
      missing.put(pos, original);
    }
    if (missing.isEmpty()) return Unit.NONE;
    BlockState soil = world.getBlockState(tree.anchor().below());
    if (!LandWorks.ground(soil) && !missing.containsKey(tree.anchor().below())) return guard("tree_soil");
    Set<BlockPos> unit = new HashSet<>(tree.blocks());
    for (BlockPos pos : missing.keySet()) if (LandWorks.occupied(world, pos)) return guard("tree_entity");
    if (!LandWorks.surroundedByLand(world, missing.keySet(), unit, pos -> original(world, pos))) return guard("tree_margin");
    int cost = AltarRules.treeCost(missing.size());
    if (!affordable(cost)) return Unit.FERTILIZER;
    if (!budget.allows(missing.size())) return Unit.BUDGET;
    if (!LandWorks.commit(actor, world, missing, Map.of(), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE))
      return guard("tree_claim");
    pay(cost);
    totals.trees++;
    budget.spend(missing.size());
    return Unit.APPLIED;
  }

  /** Replants the original plant on a column's surface: one block, a double plant or a stack. */
  private Unit plant(ServerLevel world, Player actor, long[] column, AltarRules.TickBudget budget) {
    int x = (int) column[0], z = (int) column[1];
    if (!trusted(world, x, z, budget)) return Unit.NONE;
    int top = referenceGround(x, z);
    if (top == Integer.MIN_VALUE) return Unit.NONE;
    LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
    boolean complete = true;
    for (int y = top + 1; y <= top + 4; y++) {
      BlockPos pos = new BlockPos(x, y, z);
      BlockState original = region.state(pos);
      if (original == null || !plantLike(original)) break;
      BlockState now = world.getBlockState(pos);
      if (same(original, now)) continue;
      if (!now.isAir()) return complete && writes.isEmpty() ? Unit.NONE : Unit.GUARDED;
      complete = false;
      writes.put(pos, original);
    }
    if (writes.isEmpty()) return Unit.NONE;
    BlockPos base = new BlockPos(x, top, z);
    BlockState ground = world.getBlockState(base);
    BlockState originalGround = region.state(base);
    if (originalGround == null || !ground.is(originalGround.getBlock())) return Unit.NONE;
    BlockPos first = writes.keySet().iterator().next();
    if (first.getY() == top + 1 && !writes.get(first).canSurvive(world, first)) return Unit.NONE;
    for (BlockPos pos : writes.keySet()) if (LandWorks.occupied(world, pos)) return guard("plant_entity");
    if (!LandWorks.surroundedByLand(world, writes.keySet(), writes.keySet(), pos -> original(world, pos))) return guard("plant_margin");
    if (!budget.allows(writes.size())) return Unit.BUDGET;
    if (!affordable(AltarRules.BLOCK_CHARGE)) return Unit.FERTILIZER;
    if (!LandWorks.commit(actor, world, writes, Map.of(), Block.UPDATE_ALL)) return guard("plant_claim");
    pay(AltarRules.BLOCK_CHARGE);
    totals.plants++;
    budget.spend(writes.size());
    return Unit.APPLIED;
  }

  /** A neighbour that still holds exactly what the generator put there is part of the land. */
  private boolean original(ServerLevel world, BlockPos pos) {
    BlockState original = region.state(pos);
    return original != null && same(original, world.getBlockState(pos));
  }

  /** Current ground top of a column under vegetation, or MIN for built or unknown columns. */
  private int currentGround(ServerLevel world, int x, int z) {
    int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int steps = 0; steps < 64 && y >= world.getMinBuildHeight(); steps++, y--) {
      BlockState state = world.getBlockState(cursor.set(x, y, z));
      if (LandWorks.ground(state)) return y;
      if (!state.isAir() && !LandWorks.natural(state)) return Integer.MIN_VALUE;
    }
    return Integer.MIN_VALUE;
  }

  /**
   * The reference trees anchored in this chunk and inside the disc. Trunks are 26-connected
   * logs; every canopy block joins the trunk that reaches it first through the canopy, so
   * neighbouring crowns stay separate trees.
   */
  static List<Tree> treesIn(TerrainReference.Region region, AltarRules.ChunkKey key, int cx, int cz, int radius) {
    int margin = AltarRules.TREE_MARGIN;
    int minX = key.minX() - margin, maxX = key.minX() + 15 + margin;
    int minZ = key.minZ() - margin, maxZ = key.minZ() + 15 + margin;
    int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) {
        int ground = referenceGround(region, x, z);
        int surface = region.surface(x, z);
        if (ground != Integer.MIN_VALUE) low = Math.min(low, ground - 1);
        if (surface != Integer.MIN_VALUE) high = Math.max(high, Math.min(surface, ground + AltarRules.CANOPY_HEIGHT));
      }
    if (low > high) return List.of();
    LongOpenHashSet logs = new LongOpenHashSet();
    LongOpenHashSet canopy = new LongOpenHashSet();
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++)
        for (int y = low; y <= high; y++) {
          BlockState state = region.state(cursor.set(x, y, z));
          if (state == null || state.isAir() || state.hasBlockEntity()) continue;
          if (trunk(state)) logs.add(cursor.asLong());
          else if (crown(state)) canopy.add(cursor.asLong());
        }
    Long2IntOpenHashMap owner = new Long2IntOpenHashMap();
    owner.defaultReturnValue(-1);
    List<LongArrayList> members = new ArrayList<>();
    for (long start : logs) {
      if (owner.containsKey(start)) continue;
      int id = members.size();
      LongArrayList trunkBlocks = new LongArrayList();
      LongArrayList frontier = new LongArrayList();
      frontier.add(start);
      owner.put(start, id);
      while (!frontier.isEmpty()) {
        long at = frontier.removeLong(frontier.size() - 1);
        trunkBlocks.add(at);
        int ax = BlockPos.getX(at), ay = BlockPos.getY(at), az = BlockPos.getZ(at);
        for (int dx = -1; dx <= 1; dx++)
          for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) {
              long next = BlockPos.asLong(ax + dx, ay + dy, az + dz);
              if (logs.contains(next) && !owner.containsKey(next)) {
                owner.put(next, id);
                frontier.add(next);
              }
            }
      }
      members.add(trunkBlocks);
    }
    LongArrayList wave = new LongArrayList();
    for (LongArrayList trunk : members) wave.addAll(trunk);
    while (!wave.isEmpty()) {
      LongArrayList next = new LongArrayList();
      for (int i = 0; i < wave.size(); i++) {
        long at = wave.getLong(i);
        int id = owner.get(at);
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
          long neighbour = BlockPos.offset(at, direction);
          if (canopy.contains(neighbour) && !owner.containsKey(neighbour)) {
            owner.put(neighbour, id);
            members.get(id).add(neighbour);
            next.add(neighbour);
          }
        }
      }
      wave = next;
    }
    List<Tree> result = new ArrayList<>();
    for (LongArrayList blocks : members) {
      long anchor = Long.MAX_VALUE;
      int ay = Integer.MAX_VALUE, ax = 0, az = 0;
      for (int i = 0; i < blocks.size(); i++) {
        long at = blocks.getLong(i);
        if (!logs.contains(at)) continue;
        int y = BlockPos.getY(at), x = BlockPos.getX(at), z = BlockPos.getZ(at);
        if (y < ay || y == ay && (x < ax || x == ax && z < az)) {
          anchor = at;
          ay = y;
          ax = x;
          az = z;
        }
      }
      if (anchor == Long.MAX_VALUE || ax >> 4 != key.x() || az >> 4 != key.z()
          || !AltarRules.inSquare(ax - cx, az - cz, radius)) continue;
      List<BlockPos> list = new ArrayList<>(blocks.size());
      for (int i = 0; i < blocks.size(); i++) list.add(BlockPos.of(blocks.getLong(i)));
      list.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getX())
          .thenComparingInt(p -> p.getZ()));
      result.add(new Tree(BlockPos.of(anchor), List.copyOf(list)));
    }
    result.sort(Comparator.comparingLong((Tree t) -> {
          long dx = t.anchor().getX() - cx, dz = t.anchor().getZ() - cz;
          return dx * dx + dz * dz;
        }).thenComparingInt(t -> t.anchor().getX()).thenComparingInt(t -> t.anchor().getZ()));
    return List.copyOf(result);
  }

  // ---- Classification ------------------------------------------------------------------------

  private int referenceGround(int x, int z) {
    return referenceGround(region, x, z);
  }

  /** Original ground top of a column under vegetation and water, or MIN outside the sandbox. */
  static int referenceGround(TerrainReference.Region region, int x, int z) {
    int y = region.surface(x, z);
    if (y == Integer.MIN_VALUE) return y;
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int steps = 0; steps < 80 && y >= region.setup.minY(); steps++, y--) {
      BlockState state = region.state(cursor.set(x, y, z));
      if (state != null && LandWorks.ground(state)) return y;
    }
    return Integer.MIN_VALUE;
  }

  /** Terrain the altar may recreate: natural ground, never ores or block entities. */
  static boolean fillable(BlockState state) {
    return state != null && LandWorks.ground(state) && !state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)
        && !state.hasBlockEntity();
  }

  static AltarRules.Cell cell(BlockState state) {
    if (!state.getFluidState().isEmpty()) return AltarRules.Cell.BLOCKED;
    if (state.isAir()) return AltarRules.Cell.OPEN;
    if (!LandWorks.natural(state)) return AltarRules.Cell.BLOCKED;
    return state.canBeReplaced() ? AltarRules.Cell.OPEN : AltarRules.Cell.GROUND;
  }

  /** Falling ground over a void gets its own sturdy form, as surface rules do under sand. */
  private BlockState supported(BlockPos pos, BlockState state) {
    if (!(state.getBlock() instanceof FallingBlock)) return state;
    BlockState below = level.getBlockState(pos.below());
    if (!below.isAir() && !below.canBeReplaced()) return state;
    if (state.is(Blocks.SAND)) return Blocks.SANDSTONE.defaultBlockState();
    if (state.is(Blocks.RED_SAND)) return Blocks.RED_SANDSTONE.defaultBlockState();
    return Blocks.STONE.defaultBlockState();
  }

  /** No entity inside, and every face neighbour natural, original or part of the unit. */
  private boolean guardedWrite(BlockPos pos, Set<BlockPos> unit) {
    ServerLevel world = (ServerLevel) level;
    if (LandWorks.occupied(world, pos)) return false;
    return LandWorks.surroundedByLand(world, List.of(pos), unit, neighbour -> {
      BlockState original = region.state(neighbour);
      return original != null && same(original, world.getBlockState(neighbour));
    });
  }

  static boolean same(BlockState original, BlockState now) {
    if (original.equals(now)) return true;
    return original.is(BlockTags.LEAVES) && now.is(original.getBlock());
  }

  static boolean trunk(BlockState state) {
    return state.is(BlockTags.LOGS) || state.is(Blocks.MUSHROOM_STEM);
  }

  static boolean crown(BlockState state) {
    return state.is(BlockTags.LEAVES) || CANOPY_EXTRAS.contains(state.getBlock());
  }

  static boolean plantLike(BlockState state) {
    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE))
      return false;
    if (!NatureRestoration.natural(state) && !EXTRA_PLANTS.contains(state.getBlock())) return false;
    return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || EXTRA_PLANTS.contains(state.getBlock());
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
    area = null;
    columns = null;
    trust.clear();
    pass = Math.max(0, Math.min(PASSES, tag.getInt("pass")));
    index = Math.max(0, tag.getInt("index"));
    charge = Math.max(0, tag.getInt("charge"));
    totals.load(tag.getCompound("totals"));
    failure = tag.getString("failure");
    region = null;
    trees = List.of();
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
    tag.putString("failure", failure);
  }
}
