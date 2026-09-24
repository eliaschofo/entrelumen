package dev.entrelumen;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.slf4j.Logger;

/**
 * Cooperative, radius-bounded structure and biome searches on the server thread. One job runs at a
 * time with a small per-tick budget; worldgen checks are never run off-thread because vanilla's
 * structure check caches are not thread-safe. The server thread never loads or waits for a chunk:
 * a predicted structure start is confirmed by asking worldgen, through a ticket of the search's
 * own, to bring its chunk to {@code STRUCTURE_STARTS}, and polling that future on later ticks.
 * Results become shared world facts in CompassData.
 */
public final class CompassLocator {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Default per-tick budget. One structure check may overrun it; it never stops mid-check. */
  static final long BUDGET_NANOS = 2_000_000L;
  static final int BIOME_STEP = 32;
  /** Chunks one search may have worldgen confirm at a time, and predictions it may queue. */
  static final int MAX_IN_FLIGHT = 8, MAX_WAITING = 64;
  /** A single presence check (a jigsaw prediction or a stored-chunk read) this slow is logged. */
  static final long SLOW_CHECK_NANOS = 250_000_000L;
  /** A chunk that is not at structure starts after this long is skipped (logged). */
  static final int MAX_WAIT_TICKS = 2400;
  /** Tickets expire on their own if a search is dropped; live ones are renewed before that. */
  static final int TICKET_LIFESPAN_TICKS = 600, TICKET_REFRESH_TICKS = 200;
  /** Keeps a predicted chunk loadable up to structure starts only, never to a full chunk. */
  static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
  static final int TICKET_DISTANCE = ChunkLevel.byStatus(FullChunkStatus.FULL) - TICKET_LEVEL;
  static final TicketType<ChunkPos> TICKET = TicketType.create("entrelumen_compass",
      Comparator.comparingLong(ChunkPos::toLong), TICKET_LIFESPAN_TICKS);

  /** Search cost, logged once per job and returned to GameTests. */
  public record Cost(
      int ticks, long nanos, long maxStepNanos, int positions, int candidates, int loads) {}

  public record Outcome(Optional<BlockPos> found, Cost cost) {}

  private static final Map<MinecraftServer, CompassLocator> INSTANCES = new WeakHashMap<>();
  private final LinkedHashMap<String, Job> queue = new LinkedHashMap<>();

  public static synchronized CompassLocator of(MinecraftServer server) {
    return INSTANCES.computeIfAbsent(server, ignored -> new CompassLocator());
  }

  public static synchronized void forget(MinecraftServer server) {
    var locator = INSTANCES.remove(server);
    if (locator != null) locator.queue.values().forEach(Job::cancel);
  }

  public boolean pending(String requestKey) {
    return queue.containsKey(requestKey);
  }

  public int queued() {
    return queue.size();
  }

  /**
   * Queues a search unless one with the same key is already queued. {@code done} runs on the
   * server thread with the outcome.
   */
  public boolean request(
      String requestKey,
      ServerLevel level,
      CompassTargets.Target target,
      BlockPos origin,
      boolean honorWorldOptions,
      Consumer<Outcome> done) {
    if (queue.containsKey(requestKey)) return false;
    Job job =
        switch (target.type()) {
          case STRUCTURE -> StructureJob.create(level, target, origin, honorWorldOptions);
          case BIOME -> BiomeJob.create(level, target, origin);
          default -> null;
        };
    if (job == null) {
      done.accept(new Outcome(Optional.empty(), new Cost(0, 0, 0, 0, 0, 0)));
      return false;
    }
    job.done = done;
    job.description = target.key() + " from " + origin.toShortString();
    queue.put(requestKey, job);
    return true;
  }

  /** Runs the oldest job within the budget; call once per server tick. */
  public void tick(long budgetNanos) {
    if (queue.isEmpty()) return;
    var entry = queue.entrySet().iterator().next();
    Job job = entry.getValue();
    long start = System.nanoTime();
    boolean finished;
    try {
      finished = job.step(start + budgetNanos);
    } catch (RuntimeException e) {
      LOGGER.warn("Compass search {} failed; treated as not found", job.description, e);
      job.cancel();
      job.result = null;
      finished = true;
    }
    long spent = System.nanoTime() - start;
    job.ticks++;
    job.nanos += spent;
    job.maxStep = Math.max(job.maxStep, spent);
    if (!finished) return;
    queue.remove(entry.getKey());
    Cost cost = new Cost(job.ticks, job.nanos, job.maxStep, job.positions, job.candidates, job.loads);
    LOGGER.info(
        "Compass search {} {} in {} ms over {} ticks (max step {} ms; {} positions, {} candidates, {} chunk loads{})",
        job.description, job.result == null ? "found nothing" : "found " + job.result.toShortString(),
        String.format(Locale.ROOT, "%.1f", job.nanos / 1e6), job.ticks,
        String.format(Locale.ROOT, "%.1f", job.maxStep / 1e6), job.positions, job.candidates,
        job.loads, job.extra());
    job.done.accept(new Outcome(Optional.ofNullable(job.result), cost));
  }

  private abstract static class Job {
    final ServerLevel level;
    final BlockPos origin;
    final long radiusSqr;
    Consumer<Outcome> done;
    String description = "";
    BlockPos result;
    long bestDistance = Long.MAX_VALUE;
    int ticks, positions, candidates, loads;
    long nanos, maxStep;

    Job(ServerLevel level, BlockPos origin, int radius) {
      this.level = level;
      this.origin = origin.immutable();
      this.radiusSqr = (long) radius * radius;
    }

    /** True once finished. Must return after the deadline, but always makes progress. */
    abstract boolean step(long deadline);

    /** Releases whatever the job holds in the world (chunk tickets); it will not run again. */
    void cancel() {}

    /** Extra detail for the cost log line. */
    String extra() {
      return "";
    }

    boolean offer(BlockPos pos) {
      long distance = CompassData.horizontalDistanceSqr(origin, pos);
      if (distance > radiusSqr || distance >= bestDistance) return false;
      bestDistance = distance;
      result = pos.immutable();
      return true;
    }
  }

  /** Any placement type: concentric rings, random spread and mod placements such as landmarks. */
  private static final class StructureJob extends Job {
    private record Group(StructurePlacement placement, List<Holder<Structure>> structures,
        LongOpenHashSet ringChunks) {}

    /** A predicted start whose chunk still has to reach {@code STRUCTURE_STARTS}. */
    private record Check(Group group, Holder<Structure> structure) {}

    /** One presence check still to run at a scanned candidate chunk. */
    private record Presence(Group group, Holder<Structure> structure, ChunkPos chunk, int ring) {}

    /**
     * One chunk the search asked for through its own ticket. Worldgen threads bring it to
     * {@code STRUCTURE_STARTS}; the server thread only polls the future, never waits on it.
     */
    private static final class Load {
      final ChunkPos chunk;
      final List<Check> checks = new ArrayList<>();
      int ring;
      int ticketedAt = -1, refreshedAt;
      CompletableFuture<ChunkResult<ChunkAccess>> future;

      Load(ChunkPos chunk, int ring) {
        this.chunk = chunk;
        this.ring = ring;
      }
    }

    private final List<Group> groups;
    private final RingCursor cursor;
    private final int[] offset = new int[2];
    private final ChunkPos center;
    /** Predicted chunks waiting for a free ticket, in scan order, and the ones in flight. */
    private final Long2ObjectLinkedOpenHashMap<Load> waiting = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<Load> inFlight = new Long2ObjectLinkedOpenHashMap<>();
    /** Presence checks of the current candidate chunk; a tag like #village has several. */
    private final ArrayDeque<Presence> checks = new ArrayDeque<>();
    private int stopRing = Integer.MAX_VALUE;
    private boolean scanned;
    private int timeouts;

    private StructureJob(ServerLevel level, BlockPos origin, int radius, List<Group> groups) {
      super(level, origin, radius);
      this.groups = groups;
      this.center = new ChunkPos(origin);
      this.cursor = new RingCursor((radius + 15) / 16);
    }

    static Job create(ServerLevel level, CompassTargets.Target target, BlockPos origin,
        boolean honorWorldOptions) {
      if (honorWorldOptions
          && !level.getServer().getWorldData().worldGenOptions().generateStructures()) return null;
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Optional<? extends HolderSet<Structure>> set;
      if (target.tag())
        set = registry.getTag(TagKey.create(Registries.STRUCTURE,
            ResourceLocation.parse(target.value().substring(1))));
      else
        set = registry.getHolder(ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.parse(target.value()))).map(HolderSet::direct);
      if (set.isEmpty()) {
        LOGGER.warn("Compass structure target {} is not registered", target.value());
        return null;
      }
      var state = level.getChunkSource().getGeneratorState();
      Map<StructurePlacement, List<Holder<Structure>>> byPlacement = new LinkedHashMap<>();
      for (Holder<Structure> holder : set.get())
        for (StructurePlacement placement : state.getPlacementsForStructure(holder))
          byPlacement.computeIfAbsent(placement, ignored -> new ArrayList<>()).add(holder);
      if (byPlacement.isEmpty()) return null;
      List<Group> groups = new ArrayList<>();
      byPlacement.forEach((placement, structures) -> {
        LongOpenHashSet ring = null;
        if (placement instanceof ConcentricRingsStructurePlacement concentric) {
          ring = new LongOpenHashSet();
          List<ChunkPos> positions = state.getRingPositionsFor(concentric);
          if (positions != null) for (ChunkPos pos : positions) ring.add(pos.toLong());
        }
        groups.add(new Group(placement, List.copyOf(structures), ring));
      });
      return new StructureJob(level, origin, target.radius(), groups);
    }

    @Override
    boolean step(long deadline) {
      var state = level.getChunkSource().getGeneratorState();
      var structures = level.structureManager();
      int now = level.getServer().getTickCount();
      // 1. Confirm the chunks worldgen finished since the last tick. Polling is cheap.
      for (var iterator = inFlight.values().iterator(); iterator.hasNext(); ) {
        Load load = iterator.next();
        if (load.ring > stopRing || poll(load, structures, now)) {
          release(load);
          iterator.remove();
        }
      }
      // 2. Hand free tickets to the oldest predictions.
      while (inFlight.size() < MAX_IN_FLIGHT && !waiting.isEmpty()) {
        Load load = waiting.removeFirst();
        if (load.ring > stopRing) continue;
        acquire(load, now);
        inFlight.put(load.chunk.toLong(), load);
      }
      // 3. Scan further while the budget lasts. Each presence check (a jigsaw prediction can take
      // tens of milliseconds) is its own yield point, so one step overruns the budget by at most one.
      while (!scanned && waiting.size() < MAX_WAITING) {
        Presence next = checks.poll();
        if (next != null) {
          if (next.ring() <= stopRing) check(next, structures);
          if (System.nanoTime() >= deadline) break;
          continue;
        }
        if (System.nanoTime() >= deadline) break;
        if (!cursor.next(offset) || cursor.ring() > stopRing) {
          scanned = true;
          break;
        }
        int x = center.x + offset[0], z = center.z + offset[1];
        positions++;
        for (Group group : groups) {
          boolean candidate =
              group.ringChunks() != null
                  ? group.ringChunks().contains(ChunkPos.asLong(x, z))
                  : group.placement().isStructureChunk(state, x, z);
          if (!candidate) continue;
          candidates++;
          ChunkPos chunk = new ChunkPos(x, z);
          for (Holder<Structure> holder : group.structures())
            checks.add(new Presence(group, holder, chunk, cursor.ring()));
        }
      }
      return scanned && inFlight.isEmpty() && waiting.values().stream().allMatch(l -> l.ring > stopRing);
    }

    private void check(Presence next, net.minecraft.world.level.StructureManager structures) {
      long checkStart = System.nanoTime();
      StructureCheckResult presence = structures.checkStructurePresence(
          next.chunk(), next.structure().value(), next.group().placement(), false);
      long took = System.nanoTime() - checkStart;
      if (took >= SLOW_CHECK_NANOS)
        LOGGER.info("Compass search {}: presence check of {} at {} took {} ms ({})", description,
            next.structure().unwrapKey().map(k -> k.location().toString()).orElse("?"), next.chunk(),
            took / 1_000_000, presence);
      if (presence == StructureCheckResult.START_PRESENT)
        found(next.group().placement().getLocatePos(next.chunk()), next.ring());
      else if (presence == StructureCheckResult.CHUNK_LOAD_NEEDED)
        // A prediction is not proof: shared structure sets pick one member per chunk.
        enqueue(next.chunk(), next.ring()).checks.add(new Check(next.group(), next.structure()));
    }

    private Load enqueue(ChunkPos chunk, int ring) {
      long key = chunk.toLong();
      Load load = inFlight.get(key);
      if (load == null) load = waiting.get(key);
      if (load == null) waiting.put(key, load = new Load(chunk, ring));
      return load;
    }

    private void acquire(Load load, int now) {
      level.getChunkSource().addRegionTicket(TICKET, load.chunk, TICKET_DISTANCE, load.chunk);
      load.ticketedAt = load.refreshedAt = now;
    }

    private void release(Load load) {
      if (load.ticketedAt >= 0)
        level.getChunkSource().removeRegionTicket(TICKET, load.chunk, TICKET_DISTANCE, load.chunk);
      load.ticketedAt = -1;
    }

    /** True once {@code load} is settled: confirmed, unloadable or given up. Never blocks. */
    private boolean poll(Load load, net.minecraft.world.level.StructureManager structures, int now) {
      var chunkMap = level.getChunkSource().chunkMap;
      if (load.future == null) {
        // The ticket becomes a holder at the next chunk-source tick; only then can the
        // generation task be scheduled on the worldgen executor.
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(load.chunk.toLong());
        if (holder != null && holder.getTicketLevel() <= TICKET_LEVEL)
          load.future = holder.scheduleChunkGenerationTask(ChunkStatus.STRUCTURE_STARTS, chunkMap);
      }
      if (load.future != null && load.future.isDone()) {
        ChunkAccess access;
        try {
          access = load.future.getNow(GenerationChunkHolder.UNLOADED_CHUNK).orElse(null);
        } catch (RuntimeException e) {
          access = null;
        }
        if (access == null) {
          // Unloaded before generation finished (a ticket race); ask again until the deadline.
          load.future = null;
        } else {
          loads++;
          for (Check check : load.checks) {
            StructureStart start = structures.getStartForStructure(
                SectionPos.bottomOf(access), check.structure().value(), access);
            if (start != null && start.isValid())
              found(check.group().placement().getLocatePos(start.getChunkPos()), load.ring);
          }
          return true;
        }
      }
      if (now - load.ticketedAt > MAX_WAIT_TICKS) {
        timeouts++;
        LOGGER.warn("Compass search {}: chunk {} did not reach structure starts in {} ticks; skipped",
            description, load.chunk, MAX_WAIT_TICKS);
        return true;
      }
      if (now - load.refreshedAt >= TICKET_REFRESH_TICKS) {
        // Re-adding an equal ticket only renews its timestamp.
        level.getChunkSource().addRegionTicket(TICKET, load.chunk, TICKET_DISTANCE, load.chunk);
        load.refreshedAt = now;
      }
      return false;
    }

    @Override
    void cancel() {
      inFlight.values().forEach(this::release);
      inFlight.clear();
      waiting.clear();
      checks.clear();
    }

    @Override
    String extra() {
      return timeouts == 0 ? "" : ", " + timeouts + " chunks timed out";
    }

    private void found(BlockPos pos, int ring) {
      // Chebyshev rings: a nearer Euclidean match can still sit up to sqrt(2) rings out. Chunks
      // are confirmed out of scan order, so every nearer match may tighten the bound.
      if (offer(pos))
        stopRing = Math.min(stopRing, Math.min(cursor.radius(), (int) Math.ceil(ring * 1.4143) + 1));
    }
  }

  /** Samples the biome source every 32 blocks at the origin height; never loads chunks. */
  private static final class BiomeJob extends Job {
    private final HolderSet<Biome> biomes;
    private final RingCursor cursor;
    private final int[] offset = new int[2];
    private int stopRing = Integer.MAX_VALUE;

    private BiomeJob(ServerLevel level, BlockPos origin, int radius, HolderSet<Biome> biomes) {
      super(level, origin, radius);
      this.biomes = biomes;
      this.cursor = new RingCursor(radius / BIOME_STEP);
    }

    static Job create(ServerLevel level, CompassTargets.Target target, BlockPos origin) {
      var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
      Optional<? extends HolderSet<Biome>> set;
      if (target.tag())
        set = registry.getTag(TagKey.create(Registries.BIOME,
            ResourceLocation.parse(target.value().substring(1))));
      else
        set = registry.getHolder(ResourceKey.create(Registries.BIOME,
            ResourceLocation.parse(target.value()))).map(HolderSet::direct);
      if (set.isEmpty()) {
        LOGGER.warn("Compass biome target {} is not registered", target.value());
        return null;
      }
      return new BiomeJob(level, origin, target.radius(), set.get());
    }

    @Override
    boolean step(long deadline) {
      var source = level.getChunkSource().getGenerator().getBiomeSource();
      var sampler = level.getChunkSource().randomState().sampler();
      int y = Math.max(level.getMinBuildHeight(), Math.min(origin.getY(), level.getMaxBuildHeight() - 1));
      do {
        if (!cursor.next(offset)) return true;
        if (cursor.ring() > stopRing) return true;
        int x = origin.getX() + offset[0] * BIOME_STEP, z = origin.getZ() + offset[1] * BIOME_STEP;
        positions++;
        var biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
            QuartPos.fromBlock(z), sampler);
        if (biomes.contains(biome)) {
          candidates++;
          if (offer(new BlockPos(x, y, z)) && stopRing == Integer.MAX_VALUE)
            stopRing = Math.min(cursor.radius(), (int) Math.ceil(cursor.ring() * 1.4143) + 1);
        }
      } while (System.nanoTime() < deadline);
      return false;
    }
  }
}
