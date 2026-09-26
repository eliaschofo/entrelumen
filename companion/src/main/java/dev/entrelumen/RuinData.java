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
  /** 2 since 26 September 2026: ruins keep their placed markers; sites can be reserved. */
  static final int VERSION = 2;

  /** A template marker where placement left it, in world coordinates. */
  public record PlacedMarker(RuinMarkers.Marker marker, BlockPos pos) {
    public PlacedMarker {
      pos = pos.immutable();
    }
  }

  /** A site chosen for a ruin still being placed: a restart resumes there instead of searching. */
  public record Reservation(ResourceLocation id, ResourceKey<Level> dimension, BlockPos origin, long gameTime) {}

  /**
   * One placed ruin. {@code box} covers the template and any foundation poured beneath it;
   * {@code act} is the campaign act that owns the ruin (1 for the start ruin); {@code markers} are
   * the challenge nodes, gates, chests and volumes of a Plan v2 ruin (empty for the start ruin).
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
      long placedGameTime,
      List<PlacedMarker> markers) {
    public Ruin {
      pedestals = List.copyOf(pedestals);
      markers = List.copyOf(markers);
    }

    public Ruin(ResourceLocation id, ResourceLocation template, ResourceKey<Level> dimension, int act,
        BoundingBox box, BlockPos origin, BlockPos arrival, List<BlockPos> pedestals, long placedGameTime) {
      this(id, template, dimension, act, box, origin, arrival, pedestals, placedGameTime, List.of());
    }

    /** The markers of one kind, in template order. */
    public List<PlacedMarker> markers(RuinMarkers.Kind kind) {
      return markers.stream().filter(marker -> marker.marker().kind() == kind).toList();
    }

    /** The markers of one kind that belong to one challenge, in template order. */
    public List<PlacedMarker> markers(RuinMarkers.Kind kind, String challenge) {
      return markers.stream()
          .filter(marker -> marker.marker().kind() == kind && marker.marker().challenge().equals(challenge))
          .toList();
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
  private final Map<ResourceLocation, Reservation> reservations = new LinkedHashMap<>();
  /** Bumped on every change, so indexes built from the ruins know when to rebuild. */
  private int revision;

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
    reservations.remove(ruin.id());
    revision++;
    setDirty();
  }

  /** Removes a ruin from the registry (its blocks stay); true when it was there. */
  boolean remove(ResourceLocation id) {
    boolean removed = ruins.removeIf(ruin -> ruin.id().equals(id));
    if (removed) {
      revision++;
      setDirty();
    }
    return removed;
  }

  public int revision() {
    return revision;
  }

  public Optional<Reservation> reservation(ResourceLocation id) {
    return Optional.ofNullable(reservations.get(id));
  }

  public Collection<Reservation> reservations() {
    return Collections.unmodifiableCollection(reservations.values());
  }

  void reserve(Reservation reservation) {
    reservations.put(reservation.id(), reservation);
    setDirty();
  }

  void release(ResourceLocation id) {
    if (reservations.remove(id) != null) setDirty();
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
      List<PlacedMarker> markers = new ArrayList<>();
      for (Tag element2 : ruin.getList("markers", Tag.TAG_COMPOUND)) {
        CompoundTag marker = (CompoundTag) element2;
        try {
          RuinMarkers.parse(marker.getString("m")).ifPresent(parsed ->
              markers.add(new PlacedMarker(parsed, BlockPos.of(marker.getLong("p")))));
        } catch (IllegalArgumentException ignored) {
          // A marker this version no longer accepts is dropped, never fatal to the registry.
        }
      }
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
              ruin.getLong("placedGameTime"),
              markers));
    }
    for (Tag element : tag.getList("reservations", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) element;
      var id = ResourceLocation.tryParse(entry.getString("id"));
      var dimension = ResourceLocation.tryParse(entry.getString("dimension"));
      if (id == null || dimension == null) continue;
      data.reservations.put(id, new Reservation(id, ResourceKey.create(Registries.DIMENSION, dimension),
          BlockPos.of(entry.getLong("origin")), entry.getLong("gameTime")));
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
      if (!ruin.markers().isEmpty()) {
        ListTag markers = new ListTag();
        for (PlacedMarker marker : ruin.markers()) {
          CompoundTag written = new CompoundTag();
          written.putString("m", marker.marker().metadata());
          written.putLong("p", marker.pos().asLong());
          markers.add(written);
        }
        entry.put("markers", markers);
      }
      list.add(entry);
    }
    tag.put("ruins", list);
    ListTag reserved = new ListTag();
    for (Reservation reservation : reservations.values()) {
      CompoundTag entry = new CompoundTag();
      entry.putString("id", reservation.id().toString());
      entry.putString("dimension", reservation.dimension().location().toString());
      entry.putLong("origin", reservation.origin().asLong());
      entry.putLong("gameTime", reservation.gameTime());
      reserved.add(entry);
    }
    if (!reserved.isEmpty()) tag.put("reservations", reserved);
    return tag;
  }
}
