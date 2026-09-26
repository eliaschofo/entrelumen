package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import org.slf4j.Logger;

/**
 * Runs a native feature against a read-through overlay of a loaded level. Every block write is
 * recorded instead of applied, so the caller can inspect, veto or commit the whole feature at once.
 * Positions outside the permitted box, or in unloaded chunks, read as bedrock: the feature sees an
 * obstruction instead of loading a chunk or growing into an unchecked area.
 */
final class FeatureSandbox implements InvocationHandler {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final BlockState OUTSIDE = Blocks.BEDROCK.defaultBlockState();
  private static final Set<String> SILENT = Set.of("setCurrentlyGenerating", "scheduleTick",
      "blockUpdated", "updateNeighborsAt", "updateNeighborsAtExceptFromFacing", "neighborChanged",
      "neighborShapeChanged", "levelEvent", "globalLevelEvent", "playSound", "playLocalSound",
      "gameEvent", "addParticle", "addAlwaysVisibleParticle", "sendBlockUpdated",
      "updateNeighbourForOutputSignal", "setBlocksDirty", "markAndNotifyBlock");
  private static final Set<String> FORBIDDEN = Set.of("addFreshEntity",
      "addFreshEntityWithPassengers", "getLevel", "explode", "setDayTime");

  record Result(Map<BlockPos, BlockState> writes, Map<BlockPos, CompoundTag> blockEntities) {}

  private final ServerLevel level;
  private final BoundingBox permitted;
  private final LinkedHashMap<BlockPos, BlockState> writes = new LinkedHashMap<>();
  private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
  private boolean violated;

  private FeatureSandbox(ServerLevel level, BoundingBox permitted) {
    this.level = level;
    this.permitted = permitted;
  }

  /** Null when the feature did not place, touched a forbidden API or threw. Nothing is applied. */
  static Result simulate(ServerLevel level, BoundingBox permitted, ConfiguredFeature<?, ?> feature,
      RandomSource random, BlockPos origin) {
    var sandbox = new FeatureSandbox(level, permitted);
    var proxy = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
        new Class<?>[] {WorldGenLevel.class}, sandbox);
    boolean placed;
    try {
      placed = feature.place(proxy, level.getChunkSource().getGenerator(), random, origin);
    } catch (RuntimeException failure) {
      LOGGER.debug("An altar discarded a feature that failed in its sandbox", failure);
      return null;
    }
    if (!placed || sandbox.violated) return null;
    LinkedHashMap<BlockPos, BlockState> changed = new LinkedHashMap<>();
    sandbox.writes.forEach((pos, state) -> {
      if (!level.getBlockState(pos).equals(state)) changed.put(pos, state);
    });
    if (changed.isEmpty()) return null;
    Map<BlockPos, CompoundTag> data = new HashMap<>();
    sandbox.entities.forEach((pos, entity) -> {
      if (changed.containsKey(pos) && changed.get(pos).hasBlockEntity())
        data.put(pos, entity.saveCustomOnly(level.registryAccess()));
    });
    return new Result(Collections.unmodifiableMap(changed), Map.copyOf(data));
  }

  /** Chunks already checked in this simulation: a feature reads the same few chunks thousands of times. */
  private final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap chunks =
      new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap();

  private boolean readable(BlockPos pos) {
    if (!permitted.isInside(pos) || level.isOutsideBuildHeight(pos)) return false;
    long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    if (chunks.containsKey(key)) return chunks.get(key);
    boolean loaded = level.hasChunkAt(pos);
    chunks.put(key, loaded);
    return loaded;
  }

  private BlockState stateAt(BlockPos pos) {
    BlockState written = writes.get(pos);
    if (written != null) return written;
    return readable(pos) ? level.getBlockState(pos) : OUTSIDE;
  }

  private boolean record(BlockPos pos, BlockState state) {
    if (!readable(pos)) {
      violated = true;
      return false;
    }
    BlockPos key = pos.immutable();
    writes.put(key, state);
    BlockEntity existing = entities.get(key);
    if (existing != null && !existing.getType().isValid(state)) entities.remove(key);
    return true;
  }

  private BlockEntity entityAt(BlockPos pos) {
    BlockPos key = pos.immutable();
    BlockEntity existing = entities.get(key);
    if (existing != null) return existing;
    BlockState written = writes.get(key);
    // Real block entities are never exposed: a simulated feature cannot mutate stored contents.
    if (written == null || !written.hasBlockEntity()
        || !(written.getBlock() instanceof EntityBlock block)) return null;
    BlockEntity created = block.newBlockEntity(key, written);
    if (created != null) entities.put(key, created);
    return created;
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
        default -> "AltarFeatureSandbox";
      };
    }
    // The two calls a feature makes most, first.
    if (name.equals("getBlockState") && count == 1 && args[0] instanceof BlockPos pos)
      return stateAt(pos);
    if (name.equals("isStateAtPosition") && count == 2 && args[0] instanceof BlockPos pos)
      return ((Predicate<BlockState>) args[1]).test(stateAt(pos));
    if (name.equals("setBlock") && count >= 3 && args[0] instanceof BlockPos pos
        && args[1] instanceof BlockState state) return record(pos, state);
    if ((name.equals("removeBlock") || name.equals("destroyBlock")) && count >= 1
        && args[0] instanceof BlockPos pos)
      return record(pos, stateAt(pos).getFluidState().createLegacyBlock());
    if (name.equals("getFluidState") && count == 1 && args[0] instanceof BlockPos pos)
      return stateAt(pos).getFluidState();
    if (name.equals("isFluidAtPosition") && count == 2 && args[0] instanceof BlockPos pos)
      return ((Predicate<FluidState>) args[1]).test(stateAt(pos).getFluidState());
    if (name.equals("getBlockEntity") && count >= 1 && args[0] instanceof BlockPos pos) {
      BlockEntity entity = entityAt(pos);
      if (count == 1) return entity;
      return entity != null && entity.getType() == (BlockEntityType<?>) args[1]
          ? Optional.of(entity) : Optional.empty();
    }
    if (name.equals("ensureCanWrite") && count == 1 && args[0] instanceof BlockPos pos)
      return readable(pos);
    if (name.equals("getBlockTicks") || name.equals("getFluidTicks"))
      return BlackholeTickAccess.emptyLevelList();
    if (FORBIDDEN.contains(name)) {
      violated = true;
      return defaultValue(method.getReturnType());
    }
    if (SILENT.contains(name)) return defaultValue(method.getReturnType());
    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
    try {
      return method.invoke(level, args);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
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
