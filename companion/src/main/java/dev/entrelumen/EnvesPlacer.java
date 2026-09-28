package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.FloorState;
import dev.entrelumen.EnvesData.Placement;
import dev.entrelumen.EnvesData.Status;
import dev.entrelumen.EnvesLayout.Role;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Places Envés floors cell by cell and wipes the slots of ended attempts, within a per-tick time
 * budget so nobody feels a stall. Chunks load in the background under a ticket; a cell waits until
 * its chunks are there. Placing a cell pastes its template, lays an underlay under open floor
 * blocks until the floor below exists, and resolves the markers (seal blocks, portals, and the
 * {@link EnvesHooks} for chests, shrines and vaults). An interrupted floor restarts after a server
 * restart; pasting is idempotent.
 */
public final class EnvesPlacer {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Server time spent per tick; at least one cell is always handled. */
  static final long BUDGET_NANOS = 6_000_000L;
  /**
   * Keyed by job: two jobs over the same chunks (the vestibule and floor I share a column) each
   * hold their own ticket, so one finishing never unloads the other's chunks.
   */
  static final TicketType<Integer> TICKET = TicketType.create("entrelumen_enves", Integer::compare);
  private static final java.util.concurrent.atomic.AtomicInteger JOB_IDS = new java.util.concurrent.atomic.AtomicInteger();
  static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
  static final int WIPE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
  /** A wipe clears a cell's 13 layers in this many steps: a whole cell is too much for one tick. */
  static final int WIPE_BANDS = 4;

  private static final class Job {
    final int id = JOB_IDS.incrementAndGet();
    final UUID attempt;
    final int depth;
    final boolean wipe;
    final List<int[]> steps = new ArrayList<>();
    final Set<ChunkPos> chunks = new LinkedHashSet<>();
    int next;
    boolean started;
    long nanos, worst, worstCell;
    String worstName = "";
    int ticks;

    Job(UUID attempt, int depth, boolean wipe) {
      this.attempt = attempt;
      this.depth = depth;
      this.wipe = wipe;
    }
  }

  private static final List<Job> JOBS = new ArrayList<>();

  private EnvesPlacer() {}

  /** Queues a floor (depth 0 is the vestibule) unless it is placed or on its way. */
  static synchronized void queue(MinecraftServer server, Attempt attempt, int depth) {
    FloorState state = attempt.floor(depth);
    if (state.placement == Placement.READY) return;
    for (Job job : JOBS) if (!job.wipe && job.attempt.equals(attempt.id) && job.depth == depth) return;
    Job job = new Job(attempt.id, depth, false);
    // Nearest first: breadth-first from the start, so the rooms by the stairs are ready first.
    List<Integer> order = depth == EnvesGeometry.VESTIBULE ? Enves.cells(attempt, depth) : bfsOrder(Enves.layout(attempt, depth));
    for (int cell : order) job.steps.add(new int[] {depth, cell});
    state.placement = Placement.QUEUED;
    EnvesData.get(server).setDirty();
    JOBS.add(job);
    // Read the floor's templates off the server thread while its chunks load.
    String tileset = Enves.tileset(depth);
    List<String> names = order.stream().map(cell -> Enves.templateName(attempt, depth, cell)).distinct().toList();
    java.util.concurrent.CompletableFuture.runAsync(() -> names.forEach(name -> EnvesTemplates.get(server, tileset, name)),
        net.minecraft.Util.backgroundExecutor());
  }

  private static List<Integer> bfsOrder(EnvesLayout.Floor floor) {
    int[] dist = floor.bfs(floor.start());
    List<Integer> order = new ArrayList<>(floor.cells());
    order.sort(Comparator.comparingInt((Integer c) -> dist[c] < 0 ? Integer.MAX_VALUE : dist[c]).thenComparingInt(c -> c));
    return order;
  }

  /** Queues the wipe of an ended attempt's slot: every floor it placed. */
  static synchronized void queueWipe(MinecraftServer server, Attempt attempt) {
    for (Job job : JOBS) if (job.wipe && job.attempt.equals(attempt.id)) return;
    Job job = new Job(attempt.id, -1, true);
    for (int depth = 0; depth < EnvesGeometry.DEPTHS; depth++) {
      if (attempt.floor(depth).placement == Placement.NONE) continue;
      for (int cell : Enves.cells(attempt, depth))
        for (int band = 0; band < WIPE_BANDS; band++) job.steps.add(new int[] {depth, cell, band});
    }
    JOBS.add(job);
  }

  /** Drops the placement jobs of an attempt (it ended). */
  static synchronized void cancel(Attempt attempt) {
    MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    ServerLevel level = server == null ? null : Enves.level(server);
    JOBS.removeIf(job -> {
      boolean drop = !job.wipe && job.attempt.equals(attempt.id);
      if (drop && level != null) release(level, job);
      return drop;
    });
  }

  /** The server stops: jobs let go of their chunks; their floors stay queued in the saved data for {@link #resume}. */
  static synchronized void stop(MinecraftServer server) {
    ServerLevel level = Enves.level(server);
    if (level != null) for (Job job : JOBS) release(level, job);
    JOBS.clear();
  }

  /** After a restart: placements and wipes that were running start again. */
  static synchronized void resume(MinecraftServer server) {
    JOBS.clear();
    EnvesData data = EnvesData.get(server);
    for (Attempt attempt : data.attempts()) {
      if (attempt.status == Status.ENDED) {
        queueWipe(server, attempt);
        continue;
      }
      for (int depth = 0; depth < EnvesGeometry.DEPTHS; depth++) {
        Placement placement = attempt.floor(depth).placement;
        if (placement == Placement.QUEUED || placement == Placement.PLACING) {
          attempt.floor(depth).placement = Placement.NONE;
          queue(server, attempt, depth);
        }
      }
    }
  }

  public static synchronized boolean busy(Attempt attempt) {
    return JOBS.stream().anyMatch(job -> job.attempt.equals(attempt.id));
  }

  // ---- Ticking ------------------------------------------------------------------------------

  static synchronized void tick(MinecraftServer server) {
    if (JOBS.isEmpty()) return;
    ServerLevel level = Enves.level(server);
    if (level == null) return;
    EnvesData data = EnvesData.get(server);
    long start = System.nanoTime();
    boolean worked = false;
    // Placements before wipes; a job waiting for its chunks lets the next one work.
    List<Job> order = new ArrayList<>(JOBS);
    order.sort(Comparator.comparing(job -> job.wipe));
    for (Job job : order) {
      Optional<Attempt> attempt = data.attempt(job.attempt);
      if (attempt.isEmpty() || (!job.wipe && attempt.get().status == Status.ENDED)) {
        release(level, job);
        JOBS.remove(job);
        continue;
      }
      Attempt a = attempt.get();
      if (!job.started) begin(level, a, job);
      long jobStart = System.nanoTime();
      boolean progressed = false;
      while (job.next < job.steps.size()) {
        if (worked && System.nanoTime() - start >= BUDGET_NANOS) break;
        int[] step = job.steps.get(job.next);
        BlockPos origin = Enves.origin(a, step[0], step[1]);
        if (!chunksReady(level, origin)) break;
        long cellStart = System.nanoTime();
        if (job.wipe) wipeBand(level, origin, step[2]);
        else placeCell(level, a, step[0], step[1]);
        long cell = System.nanoTime() - cellStart;
        if (cell > job.worstCell) {
          job.worstCell = cell;
          job.worstName = job.wipe ? "wipe" : Enves.templateName(a, step[0], step[1]);
        }
        job.next++;
        worked = progressed = true;
      }
      if (progressed) {
        long spent = System.nanoTime() - jobStart;
        job.nanos += spent;
        job.worst = Math.max(job.worst, spent);
        job.ticks++;
      }
      if (job.next >= job.steps.size()) finish(server, level, data, a, job);
      if (worked && System.nanoTime() - start >= BUDGET_NANOS) return;
    }
  }

  private static void begin(ServerLevel level, Attempt attempt, Job job) {
    job.started = true;
    for (int[] step : job.steps) {
      BlockPos origin = Enves.origin(attempt, step[0], step[1]);
      for (int cx = origin.getX() >> 4; cx <= (origin.getX() + EnvesGeometry.CELL - 1) >> 4; cx++)
        for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + EnvesGeometry.CELL - 1) >> 4; cz++)
          job.chunks.add(new ChunkPos(cx, cz));
    }
    for (ChunkPos chunk : job.chunks) level.getChunkSource().addRegionTicket(TICKET, chunk, 0, job.id);
    if (job.wipe) {
      discardEntities(level, attempt);
    } else {
      attempt.floor(job.depth).placement = Placement.PLACING;
      EnvesData.get(level.getServer()).setDirty();
    }
  }

  private static void release(ServerLevel level, Job job) {
    for (ChunkPos chunk : job.chunks) level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, job.id);
    job.chunks.clear();
  }

  private static boolean chunksReady(ServerLevel level, BlockPos origin) {
    for (int cx = origin.getX() >> 4; cx <= (origin.getX() + EnvesGeometry.CELL - 1) >> 4; cx++)
      for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + EnvesGeometry.CELL - 1) >> 4; cz++)
        if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
    return true;
  }

  private static void finish(MinecraftServer server, ServerLevel level, EnvesData data, Attempt attempt, Job job) {
    // A chunk's entities load a little after the chunk: what was not there when the wipe began goes now.
    if (job.wipe) discardEntities(level, attempt);
    release(level, job);
    JOBS.remove(job);
    if (job.wipe) {
      data.remove(attempt.id);
      LOGGER.info("Envés: slot {} wiped ({} cells, {} ms over {} ticks, worst tick {} ms, worst step {} ms)", attempt.slot,
          job.steps.size() / WIPE_BANDS, job.nanos / 1_000_000, job.ticks, job.worst / 1_000_000, job.worstCell / 1_000_000);
      return;
    }
    attempt.floor(job.depth).placement = Placement.READY;
    data.setDirty();
    LOGGER.info("Envés: floor {} of attempt {} placed ({} cells, {} ms over {} ticks, worst tick {} ms, worst cell {} ms: {})",
        job.depth, attempt.id, job.steps.size(), job.nanos / 1_000_000, job.ticks, job.worst / 1_000_000,
        job.worstCell / 1_000_000, job.worstName);
    if (job.depth < 1) return;
    var floor = Enves.floor(level, attempt, job.depth);
    if (job.depth == EnvesLayout.BOSS_DEPTH) {
      int center = floor.layout().withRole(Role.ARENA_CENTER).getFirst();
      var boss = floor.markers(center).stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.BOSS_CENTER).findFirst()
          .orElse(new EnvesHooks.WorldMarker(new EnvesMarkers.Marker(EnvesMarkers.Kind.BOSS_CENTER, ""),
              floor.origin(center).offset(EnvesGeometry.C, 1, EnvesGeometry.C), center, Role.ARENA_CENTER));
      EnvesHooks.boss().floorReady(floor, boss);
    }
    EnvesHooks.lifecycle().floorReady(floor);
  }

  // ---- Cells --------------------------------------------------------------------------------

  static void placeCell(ServerLevel level, Attempt attempt, int depth, int cell) {
    MinecraftServer server = level.getServer();
    String tileset = Enves.tileset(depth);
    String name = Enves.templateName(attempt, depth, cell);
    var loaded = EnvesTemplates.get(server, tileset, name);
    if (loaded.isEmpty() && depth >= 1) {
      int doors = Enves.layout(attempt, depth).doors(cell);
      loaded = EnvesTemplates.get(server, tileset, EnvesTemplates.name(Role.QUIET.id, doors, 0));
    }
    if (loaded.isEmpty()) {
      LOGGER.error("Envés: no template for {} of floor {} (cell {}); the cell stays empty", name, depth, cell);
      return;
    }
    BlockPos origin = Enves.origin(attempt, depth, cell);
    StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true);
    loaded.get().template().placeInWorld(level, origin, origin, settings, level.getRandom(), FLAGS);
    underlay(level, origin, tileset);
    resolveMarkers(level, attempt, depth, cell, origin, loaded.get());
  }

  /** Solid rock under the cell where its floor layer is open, until the floor below replaces it. */
  private static void underlay(ServerLevel level, BlockPos origin, String tileset) {
    String rock = EnvesConfig.tileset(tileset).palette().getOrDefault("minecraft:deepslate", "minecraft:deepslate");
    BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(rock)).defaultBlockState();
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    for (int x = 0; x < EnvesGeometry.CELL; x++)
      for (int z = 0; z < EnvesGeometry.CELL; z++) {
        pos.set(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, FLAGS);
      }
  }

  private static void resolveMarkers(ServerLevel level, Attempt attempt, int depth, int cell, BlockPos origin,
      EnvesTemplates.Loaded loaded) {
    FloorState state = attempt.floor(depth);
    EnvesHooks.Floor context = depth >= 1 ? Enves.floor(level, attempt, depth) : null;
    Role role = context == null ? null : context.layout().role(cell);
    List<EnvesHooks.WorldMarker> gate = new ArrayList<>();
    EnvesHooks.WorldMarker mechanism = null;
    for (var local : loaded.markers()) {
      BlockPos pos = origin.offset(local.pos());
      var marker = new EnvesHooks.WorldMarker(local.marker(), pos, cell, role);
      switch (local.marker().kind()) {
        case STAIR_SEAL -> {
          if (!state.stairOpen) level.setBlock(pos, stairSeal(), Block.UPDATE_ALL);
        }
        case SEAL -> level.setBlock(pos, Enves.SEAL.get().defaultBlockState().setValue(EnvesSealBlock.LIT, state.lit.get(cell)),
            Block.UPDATE_ALL);
        case EXIT_PORTAL -> level.setBlock(pos, Enves.PORTAL.get().defaultBlockState().setValue(EnvesPortalBlock.ACTIVE,
            "return".equals(local.marker().head()) || attempt.bossDefeated), Block.UPDATE_ALL);
        case CHEST -> {
          if (context != null) EnvesHooks.chests().place(context, marker);
        }
        case SHRINE -> {
          if (context != null) EnvesHooks.shrines().place(context, marker);
        }
        case VAULT_GATE -> gate.add(marker);
        case VAULT_MECHANISM -> mechanism = marker;
        default -> {}
      }
    }
    if (context != null && (!gate.isEmpty() || mechanism != null))
      EnvesHooks.vaults().place(context, cell, List.copyOf(gate), mechanism);
  }

  static BlockState stairSeal() {
    var id = ResourceLocation.tryParse(EnvesConfig.settings().stairSealBlock());
    Block block = id == null ? Blocks.REINFORCED_DEEPSLATE : BuiltInRegistries.BLOCK.get(id);
    return (block == Blocks.AIR ? Blocks.REINFORCED_DEEPSLATE : block).defaultBlockState();
  }

  /** Clears one band of a cell's layers, from the underlay (y = -1) to the ceiling. */
  private static void wipeBand(ServerLevel level, BlockPos origin, int band) {
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    BlockState air = Blocks.AIR.defaultBlockState();
    int layers = EnvesGeometry.FLOOR_H + 1;
    for (int y = -1 + band * layers / WIPE_BANDS; y < -1 + (band + 1) * layers / WIPE_BANDS; y++)
      for (int x = 0; x < EnvesGeometry.CELL; x++)
        for (int z = 0; z < EnvesGeometry.CELL; z++) {
          pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
          BlockState state = level.getBlockState(pos);
          if (state.isAir()) continue;
          if (state.hasBlockEntity()) emptyContainer(level, pos);
          level.setBlock(pos, air, WIPE_FLAGS);
        }
  }

  /**
   * A container goes with its room, contents and all: a chest removed by {@code setBlock} drops what it
   * holds, and an unopened one rolls its loot table first, which would leave the loot floating in the
   * slot for the next attempt to find.
   */
  private static void emptyContainer(ServerLevel level, BlockPos pos) {
    var entity = level.getBlockEntity(pos);
    if (entity instanceof RandomizableContainer loot) loot.setLootTable(null);
    Clearable.tryClear(entity);
  }

  private static void discardEntities(ServerLevel level, Attempt attempt) {
    int x0 = EnvesGeometry.slotX(attempt.slot), z0 = EnvesGeometry.slotZ(attempt.slot);
    AABB box = new AABB(x0 - 2, EnvesGeometry.VOID_Y - 32, z0 - 2, x0 + EnvesLayout.W * EnvesGeometry.CELL + 2,
        EnvesGeometry.floorY(0) + EnvesGeometry.FLOOR_H + 8, z0 + EnvesLayout.H * EnvesGeometry.CELL + 2);
    for (Entity entity : level.getEntitiesOfClass(Entity.class, box, entity -> !(entity instanceof Player)))
      entity.discard();
  }

  // ---- Default hooks ------------------------------------------------------------------------

  private static Method lootrReplacement;
  private static boolean lootrResolved;

  /** A Lootr chest (a vanilla chest without Lootr) with the configured loot table; room chests by chance. */
  static void defaultChest(EnvesHooks.Floor floor, EnvesHooks.WorldMarker chest) {
    String kind = chest.marker().head();
    if (kind.equals("room")) {
      long mix = floor.attempt().seed ^ (chest.pos().asLong() * 0x9E3779B97F4A7C15L);
      mix ^= mix >>> 31;
      double roll = (Math.floorMod(mix, 10_000L)) / 10_000.0;
      if (roll >= EnvesConfig.settings().roomChestChance()) return;
    }
    Direction facing = Direction.byName(chest.marker().tail());
    if (facing == null || facing.getAxis().isVertical()) facing = Direction.SOUTH;
    BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
    state = lootr(state);
    ServerLevel level = floor.level();
    level.setBlock(chest.pos(), state, Block.UPDATE_ALL);
    if (level.getBlockEntity(chest.pos()) instanceof RandomizableContainer container) {
      String table = EnvesConfig.settings().lootTable(kind, floor.tier(), floor.depth());
      var id = ResourceLocation.tryParse(table);
      if (id != null)
        container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, id), floor.attempt().seed ^ chest.pos().asLong());
    }
  }

  /** {@code LootrAPI.replacementBlockState}, by reflection; the vanilla state without Lootr. */
  static synchronized BlockState lootr(BlockState vanilla) {
    if (!lootrResolved) {
      lootrResolved = true;
      if (ModList.get() != null && ModList.get().isLoaded("lootr")) {
        try {
          lootrReplacement = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI")
              .getMethod("replacementBlockState", BlockState.class);
        } catch (ReflectiveOperationException | LinkageError e) {
          LOGGER.warn("Lootr is loaded but LootrAPI.replacementBlockState was not found; Envés chests are vanilla", e);
        }
      }
    }
    if (lootrReplacement == null) return vanilla;
    try {
      Object replaced = lootrReplacement.invoke(null, vanilla);
      return replaced instanceof BlockState state ? state : vanilla;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return vanilla;
    }
  }

  /** The placeholder altar: a beacon, as in the prototype. */
  static void defaultShrine(EnvesHooks.Floor floor, EnvesHooks.WorldMarker altar) {
    floor.level().setBlock(altar.pos(), Blocks.BEACON.defaultBlockState(), Block.UPDATE_ALL);
  }

  /** The gate stays shut with the configured gate block; no puzzle yet. */
  static void defaultVault(EnvesHooks.Floor floor, int cell, List<EnvesHooks.WorldMarker> gate,
      EnvesHooks.WorldMarker mechanism) {
    var id = ResourceLocation.tryParse(EnvesConfig.settings().vaultGateBlock());
    Block block = id == null ? Blocks.IRON_BARS : BuiltInRegistries.BLOCK.get(id);
    for (var cellMarker : gate) floor.level().setBlock(cellMarker.pos(), block.defaultBlockState(), Block.UPDATE_ALL);
  }

  /** Opens a vault's gate: its blocks become air. For the vault puzzles. */
  public static void openVault(EnvesHooks.Floor floor, int cell) {
    for (var marker : floor.markers(cell))
      if (marker.marker().kind() == EnvesMarkers.Kind.VAULT_GATE)
        floor.level().setBlock(marker.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
  }
}
