package dev.entrelumen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * The active altars of each loaded level, indexed by every chunk their square touches, so an area
 * effect can ask in constant time whether a position lies inside an active altar of some type.
 * Altars register themselves while active and loaded; nothing here is saved, because each altar
 * saves its own state and registers again when its chunk loads.
 */
public final class AltarRegistry {
  /** Half-height of an altar whose square reaches the whole column. */
  public static final int FULL_HEIGHT = Integer.MAX_VALUE;

  /**
   * An active altar: its type, position, square half-width and vertical half-height in blocks. A
   * half-height of {@link #FULL_HEIGHT} covers the whole column; otherwise the area is a box
   * centred on the altar, symmetric above and below it.
   */
  public record Entry(AltarType type, BlockPos pos, int radius, int halfHeight) {
    public Entry(AltarType type, BlockPos pos, int radius) {
      this(type, pos, radius, FULL_HEIGHT);
    }

    public boolean covers(BlockPos target) {
      return covers(target.getX(), target.getY(), target.getZ());
    }

    public boolean covers(int x, int y, int z) {
      return Math.abs(x - pos.getX()) <= radius && Math.abs(z - pos.getZ()) <= radius
          && (halfHeight == FULL_HEIGHT || Math.abs((long) y - pos.getY()) <= halfHeight);
    }
  }

  private static final Map<ServerLevel, Index> LEVELS = new WeakHashMap<>();

  private AltarRegistry() {}

  static Index of(ServerLevel level) {
    return LEVELS.computeIfAbsent(level, ignored -> new Index());
  }

  /** Whether an active altar of this type covers the position, in its level. */
  public static boolean isInsideActive(ServerLevel level, AltarType type, BlockPos pos) {
    return isInsideActive(level, type, pos.getX(), pos.getY(), pos.getZ());
  }

  /** The same test without allocating, for per-entity and per-spawn checks. */
  public static boolean isInsideActive(ServerLevel level, AltarType type, int x, int y, int z) {
    Index index = LEVELS.get(level);
    return index != null && index.anyCovering(type, x, y, z);
  }

  /** Whether any altar of this type is active in the level: a constant-time early exit. */
  public static boolean anyActive(ServerLevel level, AltarType type) {
    Index index = LEVELS.get(level);
    return index != null && index.count(type) > 0;
  }

  /** Every active altar of this type covering the position. */
  public static List<Entry> activeAt(ServerLevel level, AltarType type, BlockPos pos) {
    Index index = LEVELS.get(level);
    return index == null ? List.of() : index.covering(type, pos);
  }

  static void put(ServerLevel level, Entry entry) {
    of(level).put(entry);
  }

  static void remove(ServerLevel level, BlockPos pos) {
    Index index = LEVELS.get(level);
    if (index != null) index.remove(pos);
  }

  /** The spatial index itself, registry-free and unit-tested. */
  static final class Index {
    private final Map<BlockPos, Entry> byPos = new HashMap<>();
    private final Long2ObjectOpenHashMap<List<Entry>> byChunk = new Long2ObjectOpenHashMap<>();
    private final Map<AltarType, Integer> counts = new HashMap<>();

    void put(Entry entry) {
      remove(entry.pos());
      BlockPos pos = entry.pos().immutable();
      Entry stored = new Entry(entry.type(), pos, entry.radius(), entry.halfHeight());
      byPos.put(pos, stored);
      counts.merge(stored.type(), 1, Integer::sum);
      forChunks(stored, key -> byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(stored));
    }

    void remove(BlockPos pos) {
      Entry old = byPos.remove(pos);
      if (old == null) return;
      counts.computeIfPresent(old.type(), (type, count) -> count > 1 ? count - 1 : null);
      forChunks(old, key -> {
        List<Entry> list = byChunk.get(key);
        if (list == null) return;
        list.remove(old);
        if (list.isEmpty()) byChunk.remove(key);
      });
    }

    List<Entry> covering(AltarType type, BlockPos target) {
      List<Entry> list = byChunk.get(ChunkPos.asLong(target.getX() >> 4, target.getZ() >> 4));
      if (list == null) return List.of();
      List<Entry> found = new ArrayList<>(1);
      for (Entry entry : list) if (entry.type().equals(type) && entry.covers(target)) found.add(entry);
      return found;
    }

    boolean anyCovering(AltarType type, int x, int y, int z) {
      List<Entry> list = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
      if (list == null) return false;
      for (int i = 0; i < list.size(); i++) {
        Entry entry = list.get(i);
        if (entry.type().equals(type) && entry.covers(x, y, z)) return true;
      }
      return false;
    }

    int count(AltarType type) {
      return counts.getOrDefault(type, 0);
    }

    int size() {
      return byPos.size();
    }

    private static void forChunks(Entry entry, java.util.function.LongConsumer action) {
      int minX = (entry.pos().getX() - entry.radius()) >> 4, maxX = (entry.pos().getX() + entry.radius()) >> 4;
      int minZ = (entry.pos().getZ() - entry.radius()) >> 4, maxZ = (entry.pos().getZ() + entry.radius()) >> 4;
      for (int x = minX; x <= maxX; x++)
        for (int z = minZ; z <= maxZ; z++) action.accept(ChunkPos.asLong(x, z));
    }
  }
}
