package dev.entrelumen;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import org.slf4j.Logger;

/**
 * What the world generator originally produced around an area, regenerated in a sandbox. Each
 * chunk runs the level's own generator into a detached proto-chunk: biomes, noise terrain and
 * surface rules, then the biome decoration with the world seed, exactly as vanilla seeds it, for
 * the surface steps (lakes, local modifications, surface structures, vegetation, snow). Ores,
 * underground features, carvers and structures are left out on purpose. Nothing here reads or
 * writes the live level after {@link Setup#capture}, so the work runs off the server thread.
 */
final class TerrainReference {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final Set<GenerationStep.Decoration> STEPS = EnumSet.of(GenerationStep.Decoration.LAKES,
      GenerationStep.Decoration.LOCAL_MODIFICATIONS, GenerationStep.Decoration.SURFACE_STRUCTURES,
      GenerationStep.Decoration.VEGETAL_DECORATION, GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
  private static final ResourceLocation REGION_RANDOM = ResourceLocation.withDefaultNamespace("worldgen_region_random");
  private static final BlockState OUTSIDE = Blocks.BEDROCK.defaultBlockState();
  private static final Map<ChunkGenerator, List<FeatureSorter.StepFeatureData>> STEPS_CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());
  /** One generation at a time, server-wide; it only competes with vanilla's own worldgen threads. */
  static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "Entrelumen altar reference");
    thread.setDaemon(true);
    thread.setPriority(Thread.NORM_PRIORITY - 1);
    return thread;
  });

  private TerrainReference() {}

  /**
   * Everything a generation needs, captured on the server thread. Structure references and starts
   * are copied only from loaded chunks, so terrain near villages keeps its native smoothing; a
   * chunk whose structure starts are not loaded is reported as unresolved instead of guessed.
   */
  record Setup(ChunkGenerator generator, RandomState randomState, long seed, RegistryAccess registries,
      FeatureFlagSet features, int minY, int height, DimensionType dimensionType,
      Map<Long, Map<Structure, LongSet>> references, Map<Long, Map<Structure, StructureStart>> starts,
      Set<Long> unresolved) {
    static Setup capture(ServerLevel level, Collection<ChunkPos> area) {
      return capture(level, level.getChunkSource().getGenerator(),
          level.getChunkSource().randomState(), level.getSeed(), area);
    }

    static Setup capture(ServerLevel level, ChunkGenerator generator, RandomState randomState, long seed,
        Collection<ChunkPos> area) {
      Map<Long, Map<Structure, LongSet>> references = new HashMap<>();
      Map<Long, Map<Structure, StructureStart>> starts = new HashMap<>();
      Set<Long> unresolved = new HashSet<>();
      for (ChunkPos pos : area) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) continue;
        Map<Structure, LongSet> kept = new HashMap<>();
        chunk.getAllReferences().forEach((structure, refs) -> {
          if (structure.terrainAdaptation() == TerrainAdjustment.NONE || refs.isEmpty()) return;
          kept.put(structure, new LongOpenHashSet(refs));
          for (long ref : refs) {
            ChunkPos startPos = new ChunkPos(ref);
            LevelChunk holder = level.getChunkSource().getChunkNow(startPos.x, startPos.z);
            StructureStart start = holder == null ? null : holder.getStartForStructure(structure);
            if (start == null) unresolved.add(pos.toLong());
            else starts.computeIfAbsent(ref, key -> new HashMap<>()).put(structure, start);
          }
        });
        if (!kept.isEmpty()) references.put(pos.toLong(), kept);
      }
      return new Setup(generator, randomState, seed, level.registryAccess(), level.enabledFeatures(),
          level.getMinBuildHeight(), level.getHeight(), level.dimensionType(), Map.copyOf(references),
          Map.copyOf(starts), Set.copyOf(unresolved));
    }
  }

  /** A finished sandbox. Read-only once published; safe to read from the server thread. */
  static final class Region {
    final Setup setup;
    private final Map<Long, ProtoChunk> chunks;
    private final Set<Long> decorated;
    long terrainNanos;
    long decorationNanos;
    int terrainChunks;
    int decoratedChunks;
    final Map<String, Integer> failures = new TreeMap<>();
    final Set<String> unsupported = ConcurrentHashMap.newKeySet();
    boolean surfaceRules;

    private Region(Setup setup, Map<Long, ProtoChunk> chunks, Set<Long> decorated) {
      this.setup = setup;
      this.chunks = chunks;
      this.decorated = decorated;
    }

    boolean decorated(int chunkX, int chunkZ) {
      return decorated.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    boolean unresolved(int chunkX, int chunkZ) {
      return setup.unresolved().contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** The original state, or null outside the sandbox. */
    BlockState state(BlockPos pos) {
      if (pos.getY() < setup.minY() || pos.getY() >= setup.minY() + setup.height()) return null;
      ProtoChunk chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
      return chunk == null ? null : chunk.getBlockState(pos);
    }

    /** Highest original non-air block of a column, or {@link Integer#MIN_VALUE} outside. */
    int surface(int x, int z) {
      ProtoChunk chunk = chunks.get(ChunkPos.asLong(x >> 4, z >> 4));
      return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }

    Holder<Biome> biome(BlockPos pos) {
      ProtoChunk chunk = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
      return chunk == null ? null : chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
    }

    /** Up to {@code limit} positions of the decorated chunks whose states differ, for diagnostics. */
    List<String> differences(Region other, int limit) {
      List<String> found = new ArrayList<>();
      List<Long> keys = new ArrayList<>(decorated);
      keys.sort(Comparator.naturalOrder());
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (long key : keys) {
        ProtoChunk mine = chunks.get(key), theirs = other.chunks.get(key);
        if (theirs == null) {
          found.add("missing chunk " + new ChunkPos(key));
          continue;
        }
        ChunkPos pos = new ChunkPos(key);
        for (int y = setup.minY(); y < setup.minY() + setup.height() && found.size() < limit; y++)
          for (int x = 0; x < 16 && found.size() < limit; x++)
            for (int z = 0; z < 16 && found.size() < limit; z++) {
              cursor.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
              BlockState a = mine.getBlockState(cursor), b = theirs.getBlockState(cursor);
              if (a != b) found.add(cursor.toShortString() + " " + a + " vs " + b);
            }
      }
      return found;
    }

    /** Order-sensitive digest of every block of the decorated chunks, for determinism checks. */
    long digest() {
      long hash = 1125899906842597L;
      List<Long> keys = new ArrayList<>(decorated);
      keys.sort(Comparator.naturalOrder());
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (long key : keys) {
        ProtoChunk chunk = chunks.get(key);
        ChunkPos pos = new ChunkPos(key);
        for (int y = setup.minY(); y < setup.minY() + setup.height(); y++)
          for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
              hash = 31 * hash + System.identityHashCode(
                  chunk.getBlockState(cursor.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z)));
      }
      return hash;
    }
  }

  /** Generates the terrain of the decorated chunks and a ring around them, then decorates them. */
  static CompletableFuture<Region> generate(Setup setup, Collection<ChunkPos> decorate, BooleanSupplier cancelled) {
    List<ChunkPos> order = new ArrayList<>(decorate);
    order.sort(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
    return CompletableFuture.supplyAsync(() -> build(setup, List.copyOf(order), cancelled), EXECUTOR);
  }

  static Region build(Setup setup, List<ChunkPos> decorate, BooleanSupplier cancelled) {
    Set<Long> terrain = new LinkedHashSet<>();
    for (ChunkPos pos : decorate)
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) terrain.add(ChunkPos.asLong(pos.x + dx, pos.z + dz));
    List<Long> terrainOrder = new ArrayList<>(terrain);
    terrainOrder.sort(Comparator.comparingInt((Long key) -> ChunkPos.getX(key)).thenComparingInt(ChunkPos::getZ));
    LevelHeightAccessor height = LevelHeightAccessor.create(setup.minY(), setup.height());
    var biomes = setup.registries().registryOrThrow(Registries.BIOME);
    Map<Long, ProtoChunk> chunks = new HashMap<>();
    for (long key : terrainOrder) {
      ProtoChunk chunk = new ProtoChunk(new ChunkPos(key), UpgradeData.EMPTY, height, biomes, null);
      Map<Structure, LongSet> refs = setup.references().get(key);
      if (refs != null) chunk.setAllReferences(refs);
      chunks.put(key, chunk);
    }
    Map<Long, ProtoChunk> holders = new HashMap<>();
    setup.starts().forEach((key, starts) -> {
      ProtoChunk holder = chunks.get(key);
      if (holder == null) {
        holder = new ProtoChunk(new ChunkPos(key), UpgradeData.EMPTY, height, biomes, null);
        holders.put(key, holder);
      }
      holder.setAllStarts(starts);
    });
    Set<Long> decoratedKeys = new HashSet<>();
    Region region = new Region(setup, Collections.unmodifiableMap(chunks), Collections.unmodifiableSet(decoratedKeys));
    var sandbox = new Sandbox(setup, chunks, holders, height, region);
    WorldGenLevel view = sandbox.proxy();
    StructureManager structures = new StructureManager(view, new WorldOptions(setup.seed(), false, false), null);
    ChunkGenerator generator = setup.generator();
    long started = System.nanoTime();
    for (long key : terrainOrder) {
      if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
      ProtoChunk chunk = chunks.get(key);
      chunk.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
      generator.createBiomes(setup.randomState(), Blender.empty(), structures, chunk).join();
      chunk.setPersistedStatus(ChunkStatus.BIOMES);
      generator.fillFromNoise(Blender.empty(), setup.randomState(), structures, chunk).join();
      chunk.setPersistedStatus(ChunkStatus.NOISE);
      if (generator instanceof NoiseBasedChunkGenerator noise) {
        noise.buildSurface(chunk, new WorldGenerationContext(generator, height), setup.randomState(),
            structures, sandbox.biomeManager, biomes, Blender.empty());
        region.surfaceRules = true;
      }
      chunk.setPersistedStatus(ChunkStatus.SURFACE);
      region.terrainChunks++;
    }
    for (long key : terrainOrder) {
      ProtoChunk chunk = chunks.get(key);
      chunk.setPersistedStatus(ChunkStatus.CARVERS);
      Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
    }
    region.terrainNanos = System.nanoTime() - started;
    started = System.nanoTime();
    List<FeatureSorter.StepFeatureData> steps = featuresPerStep(generator);
    var placed = setup.registries().registryOrThrow(Registries.PLACED_FEATURE);
    for (ChunkPos pos : decorate) {
      if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
      ProtoChunk chunk = chunks.get(pos.toLong());
      sandbox.center = pos;
      sandbox.random = setup.randomState().getOrCreateRandomFactory(REGION_RANDOM).at(pos.getWorldPosition());
      decorate(sandbox, view, chunk, steps, placed);
      chunk.setPersistedStatus(ChunkStatus.FEATURES);
      decoratedKeys.add(pos.toLong());
      region.decoratedChunks++;
    }
    sandbox.center = null;
    region.decorationNanos = System.nanoTime() - started;
    if (!region.failures.isEmpty())
      LOGGER.debug("Altar reference skipped features that need a live level: {}", region.failures);
    return region;
  }

  /** Vanilla's biome decoration for one chunk, seeded identically, skipping structures and deep steps. */
  private static void decorate(Sandbox sandbox, WorldGenLevel view, ProtoChunk chunk,
      List<FeatureSorter.StepFeatureData> steps, net.minecraft.core.Registry<PlacedFeature> placed) {
    ChunkPos pos = chunk.getPos();
    SectionPos section = SectionPos.of(pos, chunk.getMinSection());
    BlockPos origin = section.origin();
    ChunkGenerator generator = sandbox.setup.generator();
    WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
    long decorationSeed = random.setDecorationSeed(sandbox.setup.seed(), origin.getX(), origin.getZ());
    Set<Holder<Biome>> biomes = new ObjectArraySet<>();
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        ProtoChunk near = sandbox.chunks.get(ChunkPos.asLong(pos.x + dx, pos.z + dz));
        if (near == null) continue;
        for (LevelChunkSection levelSection : near.getSections()) levelSection.getBiomes().getAll(biomes::add);
      }
    biomes.retainAll(generator.getBiomeSource().possibleBiomes());
    for (int step = 0; step < steps.size(); step++) {
      if (step >= GenerationStep.Decoration.values().length
          || !STEPS.contains(GenerationStep.Decoration.values()[step])) continue;
      IntSet indices = new IntArraySet();
      FeatureSorter.StepFeatureData data = steps.get(step);
      for (Holder<Biome> biome : biomes) {
        List<net.minecraft.core.HolderSet<PlacedFeature>> features = generator.getBiomeGenerationSettings(biome).features();
        if (step < features.size())
          features.get(step).stream().map(Holder::value).forEach(feature -> {
            int index = data.indexMapping().applyAsInt(feature);
            if (index >= 0) indices.add(index);
          });
      }
      int[] sorted = indices.toIntArray();
      Arrays.sort(sorted);
      for (int index : sorted) {
        PlacedFeature feature = data.features().get(index);
        random.setFeatureSeed(decorationSeed, index, step);
        try {
          feature.placeWithBiomeCheck(view, generator, random, origin);
        } catch (RuntimeException | StackOverflowError failure) {
          String name = placed.getResourceKey(feature).map(key -> key.location().toString()).orElse("inline");
          sandbox.region.failures.merge(name, 1, Integer::sum);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  static List<FeatureSorter.StepFeatureData> featuresPerStep(ChunkGenerator generator) {
    return STEPS_CACHE.computeIfAbsent(generator, owner -> {
      try {
        Field field = ChunkGenerator.class.getDeclaredField("featuresPerStep");
        field.setAccessible(true);
        return ((Supplier<List<FeatureSorter.StepFeatureData>>) field.get(owner)).get();
      } catch (ReflectiveOperationException | RuntimeException unavailable) {
        return FeatureSorter.buildFeaturesPerStep(List.copyOf(owner.getBiomeSource().possibleBiomes()),
            biome -> owner.getBiomeGenerationSettings(biome).features(), true);
      }
    });
  }

  /**
   * A detached {@link WorldGenLevel} over the sandbox chunks. Reads outside the sandbox see
   * bedrock; writes are allowed only within one chunk of the chunk being decorated, as in vanilla.
   * Light reads as zero, as in chunks that are still generating. Entities, the live level, the
   * server and lighting are unavailable: a feature that needs them fails alone and is skipped.
   */
  private static final class Sandbox implements InvocationHandler {
    private static final Set<String> SILENT = Set.of("setCurrentlyGenerating", "blockUpdated",
        "updateNeighborsAt", "updateNeighborsAtExceptFromFacing", "neighborChanged", "neighborShapeChanged",
        "levelEvent", "globalLevelEvent", "playSound", "playLocalSound", "gameEvent", "addParticle",
        "addAlwaysVisibleParticle", "sendBlockUpdated", "updateNeighbourForOutputSignal", "setBlocksDirty",
        "markAndNotifyBlock", "scheduleTick");
    private static final Set<String> UNAVAILABLE = Set.of("getLevel", "getServer", "getChunkSource",
        "getLevelData", "getCurrentDifficultyAt", "getLightEngine", "getBlockTint", "getDifficulty");
    private final Setup setup;
    private final Map<Long, ProtoChunk> chunks;
    private final Map<Long, ProtoChunk> holders;
    private final LevelHeightAccessor height;
    private final Region region;
    private final Map<BlockPos, BlockEntity> entities = new ConcurrentHashMap<>();
    private final WorldBorder border = new WorldBorder();
    /** Answers for chunks outside the sandbox: no terrain, no structure references or starts. */
    private final ProtoChunk empty;
    final BiomeManager biomeManager;
    volatile ChunkPos center;
    volatile RandomSource random;

    Sandbox(Setup setup, Map<Long, ProtoChunk> chunks, Map<Long, ProtoChunk> holders, LevelHeightAccessor height,
        Region region) {
      this.setup = setup;
      this.chunks = chunks;
      this.holders = holders;
      this.height = height;
      this.region = region;
      this.biomeManager = new BiomeManager(this::noiseBiome, BiomeManager.obfuscateSeed(setup.seed()));
      this.empty = new ProtoChunk(new ChunkPos(Integer.MAX_VALUE >> 5, Integer.MAX_VALUE >> 5), UpgradeData.EMPTY,
          height, setup.registries().registryOrThrow(Registries.BIOME), null);
    }

    WorldGenLevel proxy() {
      return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
          new Class<?>[] {WorldGenLevel.class}, this);
    }

    private Holder<Biome> noiseBiome(int x, int y, int z) {
      ProtoChunk chunk = chunks.get(ChunkPos.asLong(x >> 2, z >> 2));
      if (chunk != null && chunk.getHighestGeneratedStatus().isOrAfter(ChunkStatus.BIOMES))
        return chunk.getNoiseBiome(x, y, z);
      return setup.generator().getBiomeSource().getNoiseBiome(x, y, z, setup.randomState().sampler());
    }

    private ProtoChunk chunk(int x, int z) {
      return chunks.get(ChunkPos.asLong(x, z));
    }

    private BlockState state(BlockPos pos) {
      if (height.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
      ProtoChunk chunk = chunk(pos.getX() >> 4, pos.getZ() >> 4);
      return chunk == null ? OUTSIDE : chunk.getBlockState(pos);
    }

    private boolean writable(BlockPos pos) {
      ChunkPos at = center;
      if (at == null || height.isOutsideBuildHeight(pos)) return false;
      int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
      return Math.abs(cx - at.x) <= 1 && Math.abs(cz - at.z) <= 1 && chunk(cx, cz) != null;
    }

    private boolean write(BlockPos pos, BlockState state) {
      if (!writable(pos)) return false;
      chunk(pos.getX() >> 4, pos.getZ() >> 4).setBlockState(pos, state, false);
      BlockEntity existing = entities.get(pos);
      if (existing != null && !existing.getType().isValid(state)) entities.remove(pos);
      return true;
    }

    private BlockEntity entityAt(BlockPos pos) {
      BlockPos key = pos.immutable();
      BlockEntity existing = entities.get(key);
      if (existing != null) return existing;
      BlockState state = state(key);
      if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock block)) return null;
      BlockEntity created = block.newBlockEntity(key, state);
      if (created != null) entities.put(key, created);
      return created;
    }

    /** A sandbox chunk, a structure-start holder, or an empty chunk; never a live one. */
    private ChunkAccess chunkAccess(int x, int z, boolean require) {
      ProtoChunk chunk = chunk(x, z);
      if (chunk == null) chunk = holders.get(ChunkPos.asLong(x, z));
      if (chunk == null && require) return empty;
      return chunk;
    }

    private int heightAt(Heightmap.Types type, int x, int z) {
      ProtoChunk chunk = chunk(x >> 4, z >> 4);
      return chunk == null ? height.getMinBuildHeight() : chunk.getHeight(type, x & 15, z & 15) + 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();
      int count = args == null ? 0 : args.length;
      if (method.getDeclaringClass() == Object.class) {
        return switch (name) {
          case "equals" -> proxy == args[0];
          case "hashCode" -> System.identityHashCode(proxy);
          default -> "EntrelumenAltarReference";
        };
      }
      switch (name) {
        case "getBlockState":
          if (count == 1 && args[0] instanceof BlockPos pos) return state(pos);
          break;
        case "getFluidState":
          if (count == 1 && args[0] instanceof BlockPos pos) return state(pos).getFluidState();
          break;
        case "isStateAtPosition":
          if (count == 2 && args[0] instanceof BlockPos pos) return ((Predicate<BlockState>) args[1]).test(state(pos));
          break;
        case "isFluidAtPosition":
          if (count == 2 && args[0] instanceof BlockPos pos)
            return ((Predicate<FluidState>) args[1]).test(state(pos).getFluidState());
          break;
        case "setBlock":
          if (count >= 3 && args[0] instanceof BlockPos pos && args[1] instanceof BlockState state)
            return write(pos, state);
          break;
        case "removeBlock", "destroyBlock":
          if (count >= 1 && args[0] instanceof BlockPos pos)
            return write(pos, state(pos).getFluidState().createLegacyBlock());
          break;
        case "getBlockEntity":
          if (count >= 1 && args[0] instanceof BlockPos pos) {
            BlockEntity entity = entityAt(pos);
            if (count == 1) return entity;
            return entity != null && entity.getType() == (BlockEntityType<?>) args[1]
                ? Optional.of(entity) : Optional.empty();
          }
          break;
        case "getHeight":
          if (count == 3 && args[0] instanceof Heightmap.Types type)
            return heightAt(type, (Integer) args[1], (Integer) args[2]);
          if (count == 0) return height.getHeight();
          break;
        case "getHeightmapPos":
          if (count == 2 && args[0] instanceof Heightmap.Types type && args[1] instanceof BlockPos pos)
            return new BlockPos(pos.getX(), heightAt(type, pos.getX(), pos.getZ()), pos.getZ());
          break;
        case "getMinBuildHeight":
          if (count == 0) return height.getMinBuildHeight();
          break;
        case "getChunk":
          if (count >= 1 && args[0] instanceof BlockPos pos) return chunkAccess(pos.getX() >> 4, pos.getZ() >> 4, true);
          if (count >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
            boolean require = count < 4 || !(args[3] instanceof Boolean flag) || flag;
            return chunkAccess(x, z, require);
          }
          break;
        case "getChunkForCollisions":
          if (count == 2) return chunkAccess((Integer) args[0], (Integer) args[1], false);
          break;
        case "hasChunk":
          if (count == 2) return chunk((Integer) args[0], (Integer) args[1]) != null;
          break;
        case "ensureCanWrite":
          if (count == 1 && args[0] instanceof BlockPos pos) return writable(pos);
          break;
        case "getSeed":
          return setup.seed();
        case "getRandom":
          RandomSource source = random;
          return source != null ? source : RandomSource.create(setup.seed());
        case "registryAccess":
          return setup.registries();
        case "enabledFeatures":
          return setup.features();
        case "dimensionType":
          return setup.dimensionType();
        case "getSeaLevel":
          return setup.generator().getSeaLevel();
        case "isClientSide":
          return false;
        case "getBiomeManager":
          return biomeManager;
        case "getBiome":
          if (count == 1 && args[0] instanceof BlockPos pos) return biomeManager.getBiome(pos);
          break;
        case "getNoiseBiome", "getUncachedNoiseBiome":
          if (count == 3) return noiseBiome((Integer) args[0], (Integer) args[1], (Integer) args[2]);
          break;
        case "getBrightness", "getRawBrightness", "getMaxLocalRawBrightness", "getSkyDarken":
          return 0;
        case "canSeeSky":
          return false;
        case "getShade":
          return 1.0f;
        case "getBlockTicks", "getFluidTicks":
          return BlackholeTickAccess.emptyLevelList();
        case "addFreshEntity", "addFreshEntityWithPassengers", "tryAddFreshEntityWithPassengers":
          return false;
        case "getEntities", "getEntitiesOfClass", "players", "getEntityCollisions":
          return List.of();
        case "getWorldBorder":
          return border;
        case "dayTime", "nextSubTickCount":
          return 0L;
        default:
          break;
      }
      if (SILENT.contains(name)) return defaultValue(method.getReturnType());
      if (UNAVAILABLE.contains(name) || !method.isDefault()) {
        region.unsupported.add(name);
        throw new UnsupportedOperationException("Altar reference cannot provide " + name);
      }
      return InvocationHandler.invokeDefault(proxy, method, args);
    }

    private static Object defaultValue(Class<?> type) {
      if (type == boolean.class) return false;
      if (type == int.class) return 0;
      if (type == long.class) return 0L;
      if (type == float.class) return 0f;
      if (type == double.class) return 0d;
      if (type == Optional.class) return Optional.empty();
      return null;
    }
  }
}
