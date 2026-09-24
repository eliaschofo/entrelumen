package dev.entrelumen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Registry of placed Heliodor ruins, persisted in {@code data/entrelumen_ruins.dat} of the
 * overworld. Other systems (compass anchors, ruin protection) read it; only placement code writes.
 */
public final class RuinData extends SavedData {
  static final int VERSION = 1;

  /**
   * One placed ruin. {@code box} covers the template and any foundation poured beneath it;
   * {@code act} is the campaign act that owns the ruin (1 for the start ruin).
   */
  public record Ruin(
      ResourceLocation id,
      ResourceLocation template,
      ResourceKey<Level> dimension,
      int act,
      BoundingBox box,
      BlockPos origin,
      BlockPos arrival,
      List<BlockPos> pedestals,
      long placedGameTime) {
    public Ruin {
      pedestals = List.copyOf(pedestals);
    }

    public boolean contains(ResourceKey<Level> level, BlockPos pos) {
      return dimension.equals(level) && box.isInside(pos);
    }

    public BlockPos center() {
      return box.getCenter();
    }
  }

  /** Set when the overworld spawn was created by this server run: the world is new. */
  boolean pendingStart;

  private final List<Ruin> ruins = new ArrayList<>();

  public static RuinData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(new Factory<>(RuinData::new, RuinData::load), "entrelumen_ruins");
  }

  public List<Ruin> ruins() {
    return Collections.unmodifiableList(ruins);
  }

  public Optional<Ruin> find(ResourceLocation id) {
    return ruins.stream().filter(ruin -> ruin.id().equals(id)).findFirst();
  }

  public boolean pendingStart() {
    return pendingStart;
  }

  void add(Ruin ruin) {
    ruins.add(ruin);
    setDirty();
  }

  public static RuinData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION)
      throw new IllegalStateException("Unsupported Entrelumen ruin data version");
    RuinData data = new RuinData();
    data.pendingStart = tag.getBoolean("pendingStart");
    for (Tag element : tag.getList("ruins", Tag.TAG_COMPOUND)) {
      CompoundTag ruin = (CompoundTag) element;
      int[] box = ruin.getIntArray("box");
      if (box.length != 6) continue;
      List<BlockPos> pedestals = new ArrayList<>();
      for (long pos : ruin.getLongArray("pedestals")) pedestals.add(BlockPos.of(pos));
      data.ruins.add(
          new Ruin(
              ResourceLocation.parse(ruin.getString("id")),
              ResourceLocation.parse(ruin.getString("template")),
              ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ruin.getString("dimension"))),
              Math.clamp(ruin.getInt("act"), 1, 6),
              new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]),
              BlockPos.of(ruin.getLong("origin")),
              BlockPos.of(ruin.getLong("arrival")),
              pedestals,
              ruin.getLong("placedGameTime")));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    tag.putBoolean("pendingStart", pendingStart);
    ListTag list = new ListTag();
    for (Ruin ruin : ruins) {
      CompoundTag entry = new CompoundTag();
      entry.putString("id", ruin.id().toString());
      entry.putString("template", ruin.template().toString());
      entry.putString("dimension", ruin.dimension().location().toString());
      entry.putInt("act", ruin.act());
      BoundingBox box = ruin.box();
      entry.putIntArray(
          "box",
          new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
      entry.putLong("origin", ruin.origin().asLong());
      entry.putLong("arrival", ruin.arrival().asLong());
      entry.putLongArray("pedestals", ruin.pedestals().stream().mapToLong(BlockPos::asLong).toArray());
      entry.putLong("placedGameTime", ruin.placedGameTime());
      list.add(entry);
    }
    tag.put("ruins", list);
    return tag;
  }
}
