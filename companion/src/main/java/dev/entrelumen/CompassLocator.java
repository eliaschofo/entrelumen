package dev.entrelumen;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.*;
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
import net.minecraft.server.level.ServerLevel;
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
 * structure check caches are not thread-safe. Results become shared world facts in CompassData.
 */
public final class CompassLocator {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Default per-tick budget. One structure check may overrun it; it never stops mid-check. */
  static final long BUDGET_NANOS = 2_000_000L;
  static final int BIOME_STEP = 32;

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
    INSTANCES.remove(server);
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
        "Compass search {} {} in {} ms over {} ticks (max step {} ms; {} positions, {} candidates, {} chunk loads)",
        job.description, job.result == null ? "found nothing" : "found " + job.result.toShortString(),
        String.format(Locale.ROOT, "%.1f", job.nanos / 1e6), job.ticks,
        String.format(Locale.ROOT, "%.1f", job.maxStep / 1e6), job.positions, job.candidates,
        job.loads);
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

    /** A predicted start whose chunk still has to load; confirmed on a later step. */
    private record Pending(Group group, Holder<Structure> structure, ChunkPos chunk, int ring) {}

    private final List<Group> groups;
    private final RingCursor cursor;
    private final int[] offset = new int[2];
    private final ChunkPos center;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private int stopRing = Integer.MAX_VALUE;

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
      while (true) {
        // Each expensive operation (a jigsaw prediction or a chunk confirmation) is its own
        // yield point, so one step costs at most one of them past the budget.
        Pending next = pending.poll();
        if (next != null) {
          loads++;
          ChunkAccess access =
              level.getChunk(next.chunk().x, next.chunk().z, ChunkStatus.STRUCTURE_STARTS);
          StructureStart start = structures.getStartForStructure(
              SectionPos.bottomOf(access), next.structure().value(), access);
          if (start != null && start.isValid())
            found(next.group().placement().getLocatePos(start.getChunkPos()), next.ring());
          if (System.nanoTime() >= deadline) return false;
          continue;
        }
        if (!cursor.next(offset) || cursor.ring() > stopRing) return true;
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
          for (Holder<Structure> holder : group.structures()) {
            StructureCheckResult presence =
                structures.checkStructurePresence(chunk, holder.value(), group.placement(), false);
            if (presence == StructureCheckResult.START_PRESENT)
              found(group.placement().getLocatePos(chunk), cursor.ring());
            else if (presence == StructureCheckResult.CHUNK_LOAD_NEEDED)
              // A prediction is not proof: shared structure sets pick one member per chunk.
              pending.add(new Pending(group, holder, chunk, cursor.ring()));
          }
        }
        if (System.nanoTime() >= deadline) return false;
      }
    }

    private void found(BlockPos pos, int ring) {
      // Chebyshev rings: a nearer Euclidean match can still sit up to sqrt(2) rings out.
      if (offer(pos) && stopRing == Integer.MAX_VALUE)
        stopRing = Math.min(cursor.radius(), (int) Math.ceil(ring * 1.4143) + 1);
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
