package dev.entrelumen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/**
 * The Altar of Levelling: free terrain work with the land's own basic blocks, paid only with fuel.
 *
 * <ul>
 *   <li><b>Flatten</b> (9 to 33 blocks): a flat square at the level the altar stands on, blended into
 *       the land around it by a smooth, symmetric slope. Plain terrain above the target is removed and
 *       holes are filled with the surface, subsurface and deep blocks of the land around it.</li>
 *   <li><b>Repair</b> (49 blocks): compares the square with what the world generator made there
 *       ({@link TerrainReference}), refills dug pits up to the original surface with the original
 *       blocks and gives bare dirt back its cover.</li>
 * </ul>
 *
 * No material is asked for and none is kept. The altar only removes and places basic terrain
 * (dirt, grass, sand, sandstone, stone, gravel, snow and the like): ores, trees, ice, builds, block
 * entities, crops, fluids and claims refuse their column, and it never creates an ore or a valuable
 * block. Each column is one atomic unit; progress is saved and a repeated run changes nothing.
 */
public final class TerraformAltarEntity extends AltarBlockEntity {
  public enum State { IDLE, PREVIEW, RUNNING, WAITING, PAUSED, DONE, FAILED }

  public static final class Totals {
    public int columns, cut, filled, swapped, refused, covered, skippedChunks;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putIntArray("values", new int[] {columns, cut, filled, swapped, refused, covered, skippedChunks});
      return tag;
    }

    void load(CompoundTag tag, int version) {
      int[] values = Arrays.copyOf(tag.getIntArray("values"), 7);
      columns = values[0];
      cut = values[1];
      filled = values[2];
      swapped = values[3];
      refused = values[4];
      // Version 1 kept imported and exported buffer counts in the last two places.
      covered = version >= 2 ? values[5] : 0;
      skippedChunks = version >= 2 ? values[6] : 0;
    }
  }

  /** Materials the flat surface takes after the untouched land around it. */
  record Palette(BlockState surface, BlockState subsurface, BlockState deep) {
    static final Palette DEFAULT = new Palette(Blocks.GRASS_BLOCK.defaultBlockState(),
        Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
  }

  /** Version 2: free materials, no buffer, repair setting. */
  static final int VERSION = 2;
  static final int PREVIEW_TICKS = TerraformRules.PREVIEW_TICKS;
  private static final Set<Block> COVERS = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK);
  private static final Set<Block> BARE = Set.of(Blocks.DIRT, Blocks.COARSE_DIRT);
  /** Natural ground that is not plain terrain: ice is too valuable and powder snow a trap. Rebuilt as snow. */
  private static final Set<Block> COLD = Set.of(Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.POWDER_SNOW);
  /** Test hook: a generator other than the level's own, keyed by altar position. Never set in play. */
  static final Map<GlobalPos, Function<ServerLevel, TerrainReference.Setup>> SETUPS = new ConcurrentHashMap<>();

  private State state = State.IDLE;
  private int size = TerraformRules.DEFAULT_SIZE;
  /** Half-width of the repair square; a test hook may shrink it to fit a GameTest template. */
  private int repairRadius = TerraformRules.REPAIR;
  private int flatTop;
  private int cursor;
  private Palette palette = Palette.DEFAULT;
  private final Totals totals = new Totals();
  private final Map<String, Integer> refusals = new HashMap<>();
  private long previewUntil;
  private long retryAt;
  private String failure = "";
  private List<TerraformRules.Column> columns;
  /** Items a version 1 altar held in its old buffer; dropped at the altar on its first tick. */
  private final List<ItemStack> legacy = new ArrayList<>();
  // Repair setting: the generator's land, built off the server thread, and which chunks match it.
  private CompletableFuture<TerrainReference.Region> job;
  private TerrainReference.Region region;
  private final Map<AltarRules.ChunkKey, Boolean> trust = new HashMap<>();
  long workingTicks;
  long tickNanos;
  long maxTickNanos;
  final long[] tickSamples = new long[4096];
  long gcTicks;
  long maxGcMillis;

  public TerraformAltarEntity(BlockPos pos, BlockState state) {
    super(Altars.TERRAFORM_ENTITY.get(), pos, state, AltarType.TERRAFORM);
  }

  @Override
  protected int areaRadius() {
    return repairing() ? repairRadius : TerraformRules.reach(size);
  }

  @Override
  protected boolean working() {
    return state == State.RUNNING;
  }

  public State state() {
    return state;
  }

  public Totals totals() {
    return totals;
  }

  public int size() {
    return size;
  }

  boolean repairing() {
    return TerraformRules.repair(size);
  }

  int cursor() {
    return cursor;
  }

  int flatTop() {
    return flatTop;
  }

  Palette palette() {
    return palette;
  }

  TerrainReference.Region region() {
    return region;
  }

  Map<String, Integer> refusals() {
    return Map.copyOf(refusals);
  }

  List<ItemStack> legacy() {
    return List.copyOf(legacy);
  }

  /** Test hook: a smaller repair square that fits a GameTest template. */
  void configureRepairRadius(int value) {
    repairRadius = Math.max(1, Math.min(TerraformRules.REPAIR, value));
    columns = null;
    cancelJob();
    region = null;
    setChanged();
  }

  int repairRadius() {
    return repairRadius;
  }

  /** Test hook: pick a size or the repair setting without the preview gesture. */
  void configureSize(int value) {
    if (TerraformRules.validSize(value)) {
      size = value;
      columns = null;
      cancelJob();
      region = null;
      setChanged();
    }
  }

  // ---- Gestures -------------------------------------------------------------------------------

  /** Standing, empty hand: preview, then confirm; while working, pause or resume. */
  @Override
  public void use(ServerPlayer player) {
    claimOwner(player);
    long now = player.serverLevel().getGameTime();
    switch (state) {
      case IDLE, DONE, FAILED -> {
        state = State.PREVIEW;
        previewUntil = now + PREVIEW_TICKS;
        player.sendSystemMessage(previewMessage());
      }
      case PREVIEW -> {
        start((ServerLevel) level);
        player.sendSystemMessage(repairing()
            ? Component.translatable("entrelumen.altar.terraform.started_repair", 2 * repairRadius + 1,
                2 * repairRadius + 1)
            : Component.translatable("entrelumen.altar.terraform.started", 2 * size + 1, 2 * size + 1, flatTop));
      }
      case RUNNING, WAITING -> {
        state = State.PAUSED;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.paused"));
      }
      case PAUSED -> {
        state = State.RUNNING;
        retryAt = 0;
        player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.resumed"));
      }
    }
    for (Component line : statusLines()) player.sendSystemMessage(line);
    setChanged();
  }

  private Component previewMessage() {
    if (repairing())
      return Component.translatable("entrelumen.altar.terraform.preview_repair", 2 * repairRadius + 1,
          2 * repairRadius + 1, PREVIEW_TICKS / 20);
    return Component.translatable("entrelumen.altar.terraform.preview", 2 * size + 1, 2 * size + 1,
        worldPosition.getY() - 1, TerraformRules.BAND, PREVIEW_TICKS / 20);
  }

  /** Crouched, empty hand: the next setting during the preview, otherwise the status. */
  @Override
  public void crouchUse(ServerPlayer player) {
    if (state == State.PREVIEW) {
      size = TerraformRules.nextSize(size);
      columns = null;
      cancelJob();
      region = null;
      previewUntil = player.serverLevel().getGameTime() + PREVIEW_TICKS;
      player.sendSystemMessage(repairing()
          ? Component.translatable("entrelumen.altar.terraform.size_repair", 2 * repairRadius + 1, 2 * repairRadius + 1)
          : Component.translatable("entrelumen.altar.terraform.size", 2 * size + 1, 2 * size + 1));
      setChanged();
      return;
    }
    for (Component line : statusLines()) player.sendSystemMessage(line);
  }

  List<Component> statusLines() {
    List<Component> lines = new ArrayList<>();
    int total = columns().size();
    Component stateName = Component.translatable("entrelumen.altar.state." + state.name().toLowerCase(Locale.ROOT));
    if (repairing())
      lines.add(Component.translatable("entrelumen.altar.terraform.status_repair", stateName, 2 * repairRadius + 1,
          2 * repairRadius + 1, Math.min(cursor, total), total));
    else
      lines.add(Component.translatable("entrelumen.altar.terraform.status", stateName, 2 * size + 1, 2 * size + 1,
          state == State.IDLE || state == State.PREVIEW ? worldPosition.getY() - 1 : flatTop,
          Math.min(cursor, total), total));
    lines.add(Component.translatable("entrelumen.altar.terraform.progress", totals.cut, totals.filled,
        totals.swapped, totals.covered, totals.refused));
    lines.add(Component.translatable("entrelumen.altar.terraform.fuel", fuel.getCount(), activeTicks / 20, fuelUsed));
    if (state == State.RUNNING && repairing() && region == null)
      lines.add(Component.translatable("entrelumen.altar.terraform.generating"));
    if (state == State.WAITING) lines.add(Component.translatable("entrelumen.altar.terraform.waiting"));
    if (state == State.FAILED) lines.add(Component.translatable("entrelumen.altar.terraform.failed", failure));
    return lines;
  }

  /** A fresh run: at the level the altar stands on, or over the repair square. */
  void start(ServerLevel world) {
    flatTop = worldPosition.getY() - 1;
    palette = repairing() ? Palette.DEFAULT : sample(world);
    cursor = 0;
    columns = null;
    cancelJob();
    region = null;
    trust.clear();
    failure = "";
    totals.load(new CompoundTag(), VERSION);
    fuelUsed = 0;
    refusals.clear();
    state = State.RUNNING;
    retryAt = 0;
    setChanged();
  }

  private List<TerraformRules.Column> columns() {
    if (columns == null) columns = repairing() ? TerraformRules.square(repairRadius) : TerraformRules.columns(size);
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

  private enum Result { APPLIED, SKIPPED, REFUSED, BUDGET }

  @Override
  public void serverTick(ServerLevel world) {
    try {
      tick(world);
    } finally {
      refreshRegistration();
    }
  }

  private void tick(ServerLevel world) {
    long now = world.getGameTime();
    if (!legacy.isEmpty()) dropLegacy(world);
    if ((state == State.PREVIEW || previewUntil > now) && now % 10 == 0) preview(world);
    if (state == State.PREVIEW && now > previewUntil) {
      state = State.IDLE;
      setChanged();
    }
    if (state == State.WAITING && now >= retryAt) state = State.RUNNING;
    if (state != State.RUNNING) return;
    if (!actorWarm(world)) return;
    // Reading the original land is free: only ticks that change the world pay time.
    if (repairing() && region == null && !reference(world)) return;
    long started = System.nanoTime();
    long gc = LandWorks.gcMillis();
    try {
      work(world, now);
    } finally {
      long paused = LandWorks.gcMillis() - gc;
      long spent = Math.max(0, System.nanoTime() - started - paused * 1_000_000L);
      if (paused > 0) {
        gcTicks++;
        maxGcMillis = Math.max(maxGcMillis, paused);
      }
      tickSamples[(int) (workingTicks % tickSamples.length)] = spent;
      workingTicks++;
      tickNanos += spent;
      maxTickNanos = Math.max(maxTickNanos, spent);
    }
  }

  private void work(ServerLevel world, long now) {
    int reach = areaRadius() + 2;
    if (!LandWorks.loaded(world, (worldPosition.getX() - reach) >> 4, (worldPosition.getZ() - reach) >> 4,
        (worldPosition.getX() + reach) >> 4, (worldPosition.getZ() + reach) >> 4)) {
      state = State.WAITING;
      retryAt = now + 40;
      return;
    }
    if (!payActiveTick()) {
      state = State.WAITING;
      retryAt = now + 20;
      setChanged();
      return;
    }
    Player actor = actor(world);
    AltarRules.TickBudget budget = new AltarRules.TickBudget(TerraformRules.OPS_PER_TICK,
        AltarRules.NANOS_PER_TICK, System::nanoTime);
    List<TerraformRules.Column> all = columns();
    while (budget.open() && cursor < all.size()) {
      budget.scan();
      TerraformRules.Column column = all.get(cursor);
      Result result = repairing() ? repairColumn(world, actor, column, budget) : column(world, actor, column, budget);
      if (result == Result.BUDGET) return;
      if (result == Result.REFUSED) totals.refused++;
      cursor++;
      setChanged();
    }
    if (cursor >= all.size()) {
      state = State.DONE;
      region = null;
      setChanged();
      if (owner != null) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
        if (player != null)
          player.sendSystemMessage(Component.translatable("entrelumen.altar.terraform.done",
              worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), totals.cut, totals.filled,
              totals.swapped, totals.covered, totals.refused));
      }
    }
  }

  private Result refuse(String reason) {
    refusals.merge(reason, 1, Integer::sum);
    return Result.REFUSED;
  }

  // ---- Flatten -------------------------------------------------------------------------------

  private Result column(ServerLevel world, Player actor, TerraformRules.Column column, AltarRules.TickBudget budget) {
    if (column.dx() == 0 && column.dz() == 0) return Result.SKIPPED;
    int x = worldPosition.getX() + column.dx(), z = worldPosition.getZ() + column.dz();
    int target = TerraformRules.target(column.dx(), column.dz(), size, flatTop,
        (ox, oz) -> outerGround(world, worldPosition.getX() + ox, worldPosition.getZ() + oz));
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    TerraformRules.Plan plan = TerraformRules.column(target, y -> cell(world.getBlockState(probe.set(x, y, z))),
        y -> matches(world.getBlockState(probe.set(x, y, z)), TerraformRules.layer(target - y)));
    if (plan.refused()) return refuse(plan.refusal());
    if (plan.ops().isEmpty()) return Result.SKIPPED;
    if (!budget.allows(plan.ops().size())) return Result.BUDGET;
    for (TerraformRules.Op op : plan.ops())
      if (op.kind() == TerraformRules.Kind.CUT)
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
          BlockState side = world.getBlockState(new BlockPos(x, op.y(), z).relative(direction));
          if (side.getFluidState().isSource()) return refuse("fluid");
        }
    List<BlockPos> removals = new ArrayList<>();
    LinkedHashMap<BlockPos, BlockState> places = new LinkedHashMap<>();
    int cuts = 0, swaps = 0, fills = 0;
    for (TerraformRules.Op op : plan.ops()) {
      BlockPos pos = new BlockPos(x, op.y(), z);
      switch (op.kind()) {
        case CUT -> {
          removals.add(pos);
          cuts++;
        }
        case SWAP -> {
          removals.add(pos);
          places.put(pos, layerBlock(op.layer()));
          swaps++;
        }
        case FILL -> {
          places.put(pos, layerBlock(op.layer()));
          fills++;
        }
      }
    }
    for (BlockPos pos : places.keySet()) if (LandWorks.occupied(world, pos)) return refuse("entity");
    for (BlockPos pos : removals)
      if (!LandWorks.mayBreak(actor, world, pos, world.getBlockState(pos))) return refuse("claim");
    List<net.neoforged.neoforge.common.util.BlockSnapshot> removed = new ArrayList<>(removals.size());
    for (BlockPos pos : removals) {
      removed.add(net.neoforged.neoforge.common.util.BlockSnapshot.create(world.dimension(), world, pos, Block.UPDATE_ALL));
      world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
    if (!places.isEmpty() && !LandWorks.commit(actor, world, places, Map.of(), Block.UPDATE_ALL)) {
      for (int i = removed.size() - 1; i >= 0; i--) removed.get(i).restore(Block.UPDATE_ALL);
      return refuse("claim");
    }
    totals.cut += cuts;
    totals.swapped += swaps;
    totals.filled += fills;
    totals.columns++;
    budget.spend(plan.ops().size());
    return Result.APPLIED;
  }

  /**
   * What a world position means to the planner. Only plain terrain is ground; wild plants that
   * anything may replace are loose; every other natural block (ores, trees, ice, cacti) is
   * protected; flowing fluid is open and sources refuse the column.
   */
  static TerraformRules.Cell cell(BlockState state) {
    if (state.isAir()) return TerraformRules.Cell.AIR;
    if (!state.getFluidState().isEmpty()) {
      if (state.getFluidState().isSource()) return TerraformRules.Cell.FLUID;
      return state.getBlock() instanceof LiquidBlock ? TerraformRules.Cell.AIR : TerraformRules.Cell.FLUID;
    }
    if (!LandWorks.natural(state)) return TerraformRules.Cell.BUILT;
    if (LandWorks.ground(state)) return basic(state) ? TerraformRules.Cell.GROUND : TerraformRules.Cell.PROTECTED;
    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return TerraformRules.Cell.PROTECTED;
    if (state.canBeReplaced() || state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)
        || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.MOSS_CARPET)
        || state.is(BlockTags.SAPLINGS))
      return TerraformRules.Cell.LOOSE;
    return TerraformRules.Cell.PROTECTED;
  }

  /**
   * Plain terrain: natural ground that is neither an ore, ice, powder snow nor a block entity. This is all the
   * altar ever removes or places, so it cannot destroy or create anything of value.
   */
  static boolean basic(BlockState state) {
    return state != null && LandWorks.ground(state) && !state.is(Tags.Blocks.ORES) && !state.hasBlockEntity()
        && !COLD.contains(state.getBlock());
  }

  private boolean matches(BlockState state, TerraformRules.Layer layer) {
    return switch (layer) {
      case SURFACE -> state.is(palette.surface().getBlock());
      case SUBSURFACE -> state.is(palette.subsurface().getBlock()) || family(state) == family(palette.subsurface());
      case DEEP -> true;
    };
  }

  /** Dirt, sand or stone family, so a subsurface of coarse dirt or granite is not swapped for nothing. */
  private static int family(BlockState state) {
    if (state.is(BlockTags.DIRT)) return 0;
    if (state.is(BlockTags.SAND) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)) return 1;
    return 2;
  }

  private BlockState layerBlock(TerraformRules.Layer layer) {
    return switch (layer) {
      case SURFACE -> palette.surface();
      case SUBSURFACE -> palette.subsurface();
      case DEEP -> palette.deep();
    };
  }

  /** Ground top next to the square, water surface included; unknown when built or unloaded. */
  static int outerGround(ServerLevel world, int x, int z) {
    if (!world.hasChunk(x >> 4, z >> 4)) return TerraformRules.UNKNOWN;
    int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (int steps = 0; steps < 64 && y >= world.getMinBuildHeight(); steps++, y--) {
      BlockState state = world.getBlockState(probe.set(x, y, z));
      if (state.getFluidState().isSource()) return y;
      TerraformRules.Cell cell = cell(state);
      if (cell == TerraformRules.Cell.GROUND) return y;
      if (cell == TerraformRules.Cell.BUILT) return TerraformRules.UNKNOWN;
    }
    return TerraformRules.UNKNOWN;
  }

  /**
   * The most common surface, subsurface and deep blocks around the square, among plain terrain
   * only; the biome's own basic blocks when the ring gives nothing.
   */
  private Palette sample(ServerLevel world) {
    Map<Block, Integer> surface = new HashMap<>(), subsurface = new HashMap<>(), deep = new HashMap<>();
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (TerraformRules.Column column : TerraformRules.outerRing(size)) {
      int x = worldPosition.getX() + column.dx(), z = worldPosition.getZ() + column.dz();
      int top = outerGround(world, x, z);
      if (top == TerraformRules.UNKNOWN) continue;
      BlockState at = world.getBlockState(probe.set(x, top, z));
      if (!basic(at)) continue;
      surface.merge(at.getBlock(), 1, Integer::sum);
      for (int depth = 1; depth <= TerraformRules.SUBSURFACE_DEPTH; depth++) {
        BlockState below = world.getBlockState(probe.set(x, top - depth, z));
        if (basic(below)) subsurface.merge(below.getBlock(), 1, Integer::sum);
      }
      BlockState low = world.getBlockState(probe.set(x, top - TerraformRules.SUBSURFACE_DEPTH - 2, z));
      if (basic(low)) deep.merge(low.getBlock(), 1, Integer::sum);
    }
    Palette biome = biomePalette(world);
    return new Palette(dominant(surface, biome.surface()), dominant(subsurface, biome.subsurface()),
        dominant(deep, biome.deep()));
  }

  /** Basic blocks of the biome the altar stands in, for land the ring cannot show. */
  private Palette biomePalette(ServerLevel world) {
    var biome = world.getBiome(worldPosition);
    if (biome.is(BiomeTags.IS_NETHER))
      return new Palette(Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
          Blocks.NETHERRACK.defaultBlockState());
    if (biome.is(BiomeTags.IS_END))
      return new Palette(Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
          Blocks.END_STONE.defaultBlockState());
    if (biome.is(BiomeTags.IS_BADLANDS))
      return new Palette(Blocks.RED_SAND.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
          Blocks.TERRACOTTA.defaultBlockState());
    if (biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BEACH))
      return new Palette(Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(),
          Blocks.SANDSTONE.defaultBlockState());
    if (biome.is(Tags.Biomes.IS_MUSHROOM))
      return new Palette(Blocks.MYCELIUM.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
          Blocks.STONE.defaultBlockState());
    return Palette.DEFAULT;
  }

  private static BlockState dominant(Map<Block, Integer> counts, BlockState fallback) {
    return counts.entrySet().stream()
        .filter(entry -> entry.getKey().asItem() != net.minecraft.world.item.Items.AIR)
        .max(Comparator.<Map.Entry<Block, Integer>>comparingInt(Map.Entry::getValue)
            .thenComparing(entry -> BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString(), Comparator.reverseOrder()))
        .map(entry -> entry.getKey().defaultBlockState()).orElse(fallback);
  }

  // ---- Repair --------------------------------------------------------------------------------

  /** Starts or collects the sandbox generation; true once the reference is available. */
  private boolean reference(ServerLevel world) {
    if (job == null) {
      List<AltarRules.ChunkKey> area = AltarRules.areaChunks(worldPosition.getX(), worldPosition.getZ(), repairRadius);
      List<net.minecraft.world.level.ChunkPos> decorate = new ArrayList<>();
      Set<ChunkPos> captured = new HashSet<>();
      for (AltarRules.ChunkKey key : area) {
        decorate.add(new ChunkPos(key.x(), key.z()));
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) captured.add(new ChunkPos(key.x() + dx, key.z() + dz));
      }
      var override = SETUPS.get(GlobalPos.of(world.dimension(), worldPosition));
      TerrainReference.Setup setup = override != null ? override.apply(world)
          : TerrainReference.Setup.capture(world, captured);
      job = TerrainReference.generate(setup, decorate, this::isRemoved);
      return false;
    }
    if (!job.isDone()) return false;
    try {
      region = job.join();
    } catch (RuntimeException failed) {
      Throwable cause = failed.getCause() == null ? failed : failed.getCause();
      failure = cause.getClass().getSimpleName();
      state = State.FAILED;
      com.mojang.logging.LogUtils.getLogger().warn("Levelling altar at {} could not regenerate its reference",
          worldPosition, cause);
      setChanged();
    } finally {
      job = null;
    }
    return region != null;
  }

  /**
   * One column of the repair: refills an open hole up to the generator's surface, bottom-up, with
   * plain terrain only, then re-covers bare dirt.
   */
  private Result repairColumn(ServerLevel world, Player actor, TerraformRules.Column column,
      AltarRules.TickBudget budget) {
    if (column.dx() == 0 && column.dz() == 0) return Result.SKIPPED;
    int x = worldPosition.getX() + column.dx(), z = worldPosition.getZ() + column.dz();
    if (!trusted(world, x, z, budget)) return Result.SKIPPED;
    int top = referenceGround(region, x, z);
    if (top == Integer.MIN_VALUE) return Result.SKIPPED;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    int[] fills = AltarRules.fillColumn(top, y -> fillable(region.state(probe.set(x, y, z))),
        y -> repairCell(world.getBlockState(probe.set(x, y, z))));
    LinkedHashMap<BlockPos, BlockState> places = new LinkedHashMap<>();
    for (int y : fills) {
      BlockPos pos = new BlockPos(x, y, z);
      places.put(pos, supported(world, pos, fill(region.state(pos), y), places));
    }
    BlockPos surface = new BlockPos(x, top, z);
    BlockState original = region.state(surface);
    BlockState now = places.containsKey(surface) ? places.get(surface) : world.getBlockState(surface);
    boolean cover = original != null && BARE.contains(now.getBlock()) && COVERS.contains(original.getBlock())
        && !now.is(original.getBlock()) && openAbove(world.getBlockState(surface.above()));
    if (cover) places.put(surface, original.getBlock().defaultBlockState());
    if (places.isEmpty()) return Result.SKIPPED;
    if (!budget.allows(places.size())) return Result.BUDGET;
    for (BlockPos pos : places.keySet()) if (LandWorks.occupied(world, pos)) return refuse("entity");
    if (!LandWorks.surroundedByLand(world, places.keySet(), places.keySet(), pos -> {
      BlockState was = region.state(pos);
      return was != null && was.equals(world.getBlockState(pos));
    })) return refuse("margin");
    List<BlockPos> swapped = new ArrayList<>();
    for (BlockPos pos : places.keySet()) if (!world.getBlockState(pos).isAir() && !world.getBlockState(pos).canBeReplaced())
      swapped.add(pos);
    for (BlockPos pos : swapped)
      if (!LandWorks.mayBreak(actor, world, pos, world.getBlockState(pos))) return refuse("claim");
    if (!LandWorks.commit(actor, world, places, Map.of(), Block.UPDATE_ALL)) return refuse("claim");
    totals.filled += fills.length;
    if (cover) totals.covered++;
    totals.columns++;
    budget.spend(places.size());
    return Result.APPLIED;
  }

  /**
   * The chunk holding this column still looks like its generator's output; otherwise the altar
   * leaves the whole chunk alone. Checked once per chunk and remembered until the next run.
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
        int reference = referenceGround(region, x, z);
        int current = currentGround(world, x, z);
        if (reference == Integer.MIN_VALUE || current == Integer.MIN_VALUE) continue;
        natural++;
        if (Math.abs(current - reference) <= 1) agree++;
        else if (current > reference + 1) surplus++;
      }
    return AltarRules.consistent(natural, agree, surplus);
  }

  /** Current ground top of a column under vegetation, or MIN for built or unknown columns. */
  private static int currentGround(ServerLevel world, int x, int z) {
    int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (int steps = 0; steps < 64 && y >= world.getMinBuildHeight(); steps++, y--) {
      BlockState state = world.getBlockState(probe.set(x, y, z));
      if (LandWorks.ground(state)) return y;
      if (!state.isAir() && !LandWorks.natural(state)) return Integer.MIN_VALUE;
    }
    return Integer.MIN_VALUE;
  }

  /** Original ground top of a column under vegetation and water, or MIN outside the sandbox. */
  static int referenceGround(TerrainReference.Region region, int x, int z) {
    int y = region.surface(x, z);
    if (y == Integer.MIN_VALUE) return y;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (int steps = 0; steps < 80 && y >= region.setup.minY(); steps++, y--) {
      BlockState state = region.state(probe.set(x, y, z));
      if (state != null && LandWorks.ground(state)) return y;
    }
    return Integer.MIN_VALUE;
  }

  /** Terrain the repair may rebuild: natural ground without a block entity. Ores are replaced by their rock. */
  static boolean fillable(BlockState state) {
    return state != null && LandWorks.ground(state) && !state.hasBlockEntity();
  }

  /**
   * The block a repair places for the generator's original: itself when it is plain terrain, the
   * surrounding rock for an ore, snow for ice. Nothing of value is ever created.
   */
  static BlockState fill(BlockState original, int y) {
    if (basic(original)) return original.getBlock().defaultBlockState();
    if (COLD.contains(original.getBlock())) return Blocks.SNOW_BLOCK.defaultBlockState();
    if (original.is(Tags.Blocks.ORES_IN_GROUND_STONE)) return Blocks.STONE.defaultBlockState();
    if (original.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK)) return Blocks.NETHERRACK.defaultBlockState();
    if (original.is(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE)) return Blocks.DEEPSLATE.defaultBlockState();
    String path = BuiltInRegistries.BLOCK.getKey(original.getBlock()).getPath();
    if (path.contains("deepslate")) return Blocks.DEEPSLATE.defaultBlockState();
    if (path.contains("nether")) return Blocks.NETHERRACK.defaultBlockState();
    if (path.contains("end")) return Blocks.END_STONE.defaultBlockState();
    return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
  }

  static AltarRules.Cell repairCell(BlockState state) {
    if (!state.getFluidState().isEmpty()) return AltarRules.Cell.BLOCKED;
    if (state.isAir()) return AltarRules.Cell.OPEN;
    if (!LandWorks.natural(state)) return AltarRules.Cell.BLOCKED;
    return state.canBeReplaced() ? AltarRules.Cell.OPEN : AltarRules.Cell.GROUND;
  }

  private static boolean openAbove(BlockState above) {
    return above.isAir() || LandWorks.natural(above) && above.canBeReplaced() && above.getFluidState().isEmpty();
  }

  /** Falling ground over a void gets its own sturdy form, as surface rules do under sand. */
  private static BlockState supported(ServerLevel world, BlockPos pos, BlockState state,
      Map<BlockPos, BlockState> below) {
    if (!(state.getBlock() instanceof FallingBlock)) return state;
    BlockState under = below.containsKey(pos.below()) ? below.get(pos.below()) : world.getBlockState(pos.below());
    if (!under.isAir() && !under.canBeReplaced()) return state;
    if (state.is(Blocks.SAND)) return Blocks.SANDSTONE.defaultBlockState();
    if (state.is(Blocks.RED_SAND)) return Blocks.RED_SANDSTONE.defaultBlockState();
    return Blocks.STONE.defaultBlockState();
  }

  // ---- Preview -------------------------------------------------------------------------------

  /** Flatten: the flat square's edge in white and the slope's outer edge in green. Repair: the square's edge. */
  private void preview(ServerLevel world) {
    int top = state == State.PREVIEW || state == State.IDLE ? worldPosition.getY() - 1 : flatTop;
    int[] rings = repairing() ? new int[] {repairRadius} : new int[] {size, TerraformRules.reach(size)};
    int step = repairing() ? 2 : 1;
    for (int ring : rings)
      for (int i = -ring; i <= ring; i += step)
        for (int[] offset : new int[][] {{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
          int x = worldPosition.getX() + offset[0], z = worldPosition.getZ() + offset[1];
          if (!world.hasChunk(x >> 4, z >> 4)) continue;
          boolean flat = !repairing() && ring == size;
          int y = flat ? top + 1 : world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
          world.sendParticles(flat ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
              x + 0.5, y + 0.2, z + 0.5, 1, 0, 0, 0, 0);
        }
  }

  // ---- Legacy buffer --------------------------------------------------------------------------

  /** A version 1 altar kept a 27-slot buffer; its contents are handed back at the altar, once. */
  private void dropLegacy(ServerLevel world) {
    for (ItemStack stack : legacy)
      if (!stack.isEmpty())
        Containers.dropItemStack(world, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
            worldPosition.getZ() + 0.5, stack);
    legacy.clear();
    setChanged();
  }

  @Override
  void dropContents(net.minecraft.world.level.Level world, BlockPos pos) {
    super.dropContents(world, pos);
    for (ItemStack stack : legacy)
      if (!stack.isEmpty()) Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    legacy.clear();
  }

  // ---- Persistence ---------------------------------------------------------------------------

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    int version = tag.getInt("version");
    if (version > VERSION) throw new IllegalStateException("Unsupported terraform altar version " + version);
    state = State.IDLE;
    for (State value : State.values()) if (value.name().equals(tag.getString("state"))) state = value;
    if (state == State.PREVIEW) state = State.IDLE;
    size = TerraformRules.validSize(tag.getInt("size")) ? tag.getInt("size") : TerraformRules.DEFAULT_SIZE;
    repairRadius = tag.contains("repairRadius", Tag.TAG_INT)
        ? Math.max(1, Math.min(TerraformRules.REPAIR, tag.getInt("repairRadius"))) : TerraformRules.REPAIR;
    flatTop = tag.contains("flatTop", Tag.TAG_INT) ? tag.getInt("flatTop") : worldPosition.getY() - 1;
    columns = null;
    cursor = Math.max(0, Math.min(columns().size(), tag.getInt("cursor")));
    var blocks = registries.lookupOrThrow(Registries.BLOCK);
    palette = tag.contains("palette", Tag.TAG_COMPOUND) ? new Palette(
        NbtUtils.readBlockState(blocks, tag.getCompound("palette").getCompound("surface")),
        NbtUtils.readBlockState(blocks, tag.getCompound("palette").getCompound("subsurface")),
        NbtUtils.readBlockState(blocks, tag.getCompound("palette").getCompound("deep"))) : Palette.DEFAULT;
    if (!basic(palette.surface()) || !basic(palette.subsurface()) || !basic(palette.deep())) palette = Palette.DEFAULT;
    totals.load(tag.getCompound("totals"), version);
    failure = tag.getString("failure");
    region = null;
    trust.clear();
    legacy.clear();
    // Version 1's buffer lived in the common inventory list; version 2 keeps what is left of it here.
    ListTag items = version < 2 ? tag.getCompound("altar").getList("Items", Tag.TAG_COMPOUND)
        : tag.getList("legacy", Tag.TAG_COMPOUND);
    for (Tag raw : items)
      if (raw instanceof CompoundTag entry) {
        ItemStack stack = ItemStack.parseOptional(registries, entry);
        if (!stack.isEmpty()) legacy.add(stack);
      }
    if (repairing() && (state == State.RUNNING || state == State.WAITING)) {
      // The reference is rebuilt identically after a reload; trust is recomputed from the world.
      retryAt = 0;
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("version", VERSION);
    tag.putString("state", (state == State.PREVIEW ? State.IDLE : state).name());
    tag.putInt("size", size);
    tag.putInt("repairRadius", repairRadius);
    tag.putInt("flatTop", flatTop);
    tag.putInt("cursor", cursor);
    CompoundTag materials = new CompoundTag();
    materials.put("surface", NbtUtils.writeBlockState(palette.surface()));
    materials.put("subsurface", NbtUtils.writeBlockState(palette.subsurface()));
    materials.put("deep", NbtUtils.writeBlockState(palette.deep()));
    tag.put("palette", materials);
    tag.put("totals", totals.save());
    tag.putString("failure", failure);
    if (!legacy.isEmpty()) {
      ListTag list = new ListTag();
      for (ItemStack stack : legacy) list.add(stack.save(registries));
      tag.put("legacy", list);
    }
  }
}
