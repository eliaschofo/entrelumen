package dev.entrelumen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Everything Solsticio persists, in {@code data/entrelumen_solsticio.dat} of the overworld: the
 * city's placement progress and markers, its plots, the definitive portal, which campaigns had
 * their Light Key forged and each player's way home. Callers mark it dirty after mutations.
 */
public final class SolsticioData extends SavedData {
  static final int VERSION = 1;

  public enum Status { NONE, PLACING, READY }

  /** A 16×16 plot: its marker (north-west corner, first free layer) and owning campaign. */
  public static final class Plot {
    public final BlockPos corner;
    public UUID owner;
    public String ownerName = "";
    public long claimedAt;

    public Plot(BlockPos corner) {
      this.corner = corner.immutable();
    }

    public ProtectionRules.Box box() {
      return CityLayout.plotBox(corner.getX(), corner.getY(), corner.getZ());
    }
  }

  /** Where a player left the Overworld, to send them back there. */
  public record ReturnPoint(ResourceKey<Level> dimension, BlockPos pos, float yaw) {}

  public Status status = Status.NONE;
  public boolean provisional;
  /** Completed placements: exactly one per world. */
  public int placements;
  public int slicesDone, slicesTotal;
  public final List<String> templates = new ArrayList<>();
  /** World-space bounds of every template piece; null until the templates were read. */
  public ProtectionRules.Box footprint;
  public int borderRadius;
  public long readyGameTime;
  public BlockPos arrival, portal, waystone, tradingHall;
  public final Map<String, BlockPos> npcs = new TreeMap<>();
  public final List<Plot> plots = new ArrayList<>();

  public final Set<Integer> portalRelics = new TreeSet<>();
  public boolean portalArmed;
  public UUID portalArmedBy;
  public long portalArmedAt;
  /** The Overworld end of the portal pair, only when Waystones is absent. */
  public BlockPos overworldPortal;
  public boolean waystoneRegistered;

  public final Set<UUID> forgedKeys = new TreeSet<>();
  public final Map<UUID, ReturnPoint> returns = new HashMap<>();

  public static SolsticioData get(MinecraftServer server) {
    return server.overworld().getDataStorage()
        .computeIfAbsent(new Factory<>(SolsticioData::new, SolsticioData::load), "entrelumen_solsticio");
  }

  public boolean ready() {
    return status == Status.READY && arrival != null;
  }

  public int plotOf(UUID campaign) {
    for (int i = 0; i < plots.size(); i++) if (campaign.equals(plots.get(i).owner)) return i;
    return -1;
  }

  /** Forget the city layout so the next placement starts over; keeps keys and return points. */
  void resetCity() {
    status = Status.NONE;
    provisional = false;
    slicesDone = slicesTotal = 0;
    templates.clear();
    footprint = null;
    borderRadius = 0;
    readyGameTime = 0;
    arrival = portal = waystone = tradingHall = null;
    npcs.clear();
    plots.clear();
    portalRelics.clear();
    portalArmed = false;
    portalArmedBy = null;
    portalArmedAt = 0;
    overworldPortal = null;
    waystoneRegistered = false;
  }

  private static void putPos(CompoundTag tag, String key, BlockPos pos) {
    if (pos != null) tag.putLong(key, pos.asLong());
  }

  private static BlockPos pos(CompoundTag tag, String key) {
    return tag.contains(key, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(key)) : null;
  }

  public static SolsticioData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION)
      throw new IllegalStateException("Unsupported Entrelumen Solsticio data version");
    SolsticioData data = new SolsticioData();
    try {
      data.status = Status.valueOf(tag.getString("status"));
    } catch (IllegalArgumentException unknown) {
      data.status = Status.NONE;
    }
    data.provisional = tag.getBoolean("provisional");
    data.placements = tag.getInt("placements");
    data.slicesDone = tag.getInt("slicesDone");
    data.slicesTotal = tag.getInt("slicesTotal");
    for (Tag name : tag.getList("templates", Tag.TAG_STRING)) data.templates.add(name.getAsString());
    int[] box = tag.getIntArray("footprint");
    if (box.length == 6) data.footprint = new ProtectionRules.Box(box[0], box[1], box[2], box[3], box[4], box[5]);
    data.borderRadius = tag.getInt("borderRadius");
    data.readyGameTime = tag.getLong("readyGameTime");
    data.arrival = pos(tag, "arrival");
    data.portal = pos(tag, "portal");
    data.waystone = pos(tag, "waystone");
    data.tradingHall = pos(tag, "tradingHall");
    CompoundTag npcs = tag.getCompound("npcs");
    for (String key : npcs.getAllKeys()) data.npcs.put(key, BlockPos.of(npcs.getLong(key)));
    for (Tag element : tag.getList("plots", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) element;
      Plot plot = new Plot(BlockPos.of(entry.getLong("corner")));
      if (entry.hasUUID("owner")) plot.owner = entry.getUUID("owner");
      plot.ownerName = entry.getString("ownerName");
      plot.claimedAt = entry.getLong("claimedAt");
      data.plots.add(plot);
    }
    for (int relic : tag.getIntArray("portalRelics")) if (relic >= 1 && relic <= 3) data.portalRelics.add(relic);
    data.portalArmed = tag.getBoolean("portalArmed");
    if (tag.hasUUID("portalArmedBy")) data.portalArmedBy = tag.getUUID("portalArmedBy");
    data.portalArmedAt = tag.getLong("portalArmedAt");
    data.overworldPortal = pos(tag, "overworldPortal");
    data.waystoneRegistered = tag.getBoolean("waystoneRegistered");
    for (Tag element : tag.getList("forgedKeys", Tag.TAG_STRING)) {
      try {
        data.forgedKeys.add(UUID.fromString(element.getAsString()));
      } catch (IllegalArgumentException ignored) {
        // A malformed id cannot name a campaign; dropping it only allows one more forge.
      }
    }
    CompoundTag returns = tag.getCompound("returns");
    for (String key : returns.getAllKeys()) {
      CompoundTag entry = returns.getCompound(key);
      ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
      if (dimension == null) continue;
      try {
        data.returns.put(UUID.fromString(key), new ReturnPoint(
            ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(entry.getLong("pos")),
            entry.getFloat("yaw")));
      } catch (IllegalArgumentException ignored) {
        // Skip a malformed player id; that player falls back to their bed or the world spawn.
      }
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    tag.putString("status", status.name());
    tag.putBoolean("provisional", provisional);
    tag.putInt("placements", placements);
    tag.putInt("slicesDone", slicesDone);
    tag.putInt("slicesTotal", slicesTotal);
    ListTag names = new ListTag();
    templates.forEach(name -> names.add(StringTag.valueOf(name)));
    tag.put("templates", names);
    if (footprint != null)
      tag.putIntArray("footprint", new int[] {footprint.minX(), footprint.minY(), footprint.minZ(),
          footprint.maxX(), footprint.maxY(), footprint.maxZ()});
    tag.putInt("borderRadius", borderRadius);
    tag.putLong("readyGameTime", readyGameTime);
    putPos(tag, "arrival", arrival);
    putPos(tag, "portal", portal);
    putPos(tag, "waystone", waystone);
    putPos(tag, "tradingHall", tradingHall);
    CompoundTag npcTag = new CompoundTag();
    npcs.forEach((key, pos) -> npcTag.putLong(key, pos.asLong()));
    tag.put("npcs", npcTag);
    ListTag plotList = new ListTag();
    for (Plot plot : plots) {
      CompoundTag entry = new CompoundTag();
      entry.putLong("corner", plot.corner.asLong());
      if (plot.owner != null) entry.putUUID("owner", plot.owner);
      entry.putString("ownerName", plot.ownerName);
      entry.putLong("claimedAt", plot.claimedAt);
      plotList.add(entry);
    }
    tag.put("plots", plotList);
    tag.putIntArray("portalRelics", portalRelics.stream().mapToInt(Integer::intValue).toArray());
    tag.putBoolean("portalArmed", portalArmed);
    if (portalArmedBy != null) tag.putUUID("portalArmedBy", portalArmedBy);
    tag.putLong("portalArmedAt", portalArmedAt);
    putPos(tag, "overworldPortal", overworldPortal);
    tag.putBoolean("waystoneRegistered", waystoneRegistered);
    ListTag forged = new ListTag();
    forgedKeys.forEach(id -> forged.add(StringTag.valueOf(id.toString())));
    tag.put("forgedKeys", forged);
    CompoundTag returnTag = new CompoundTag();
    returns.forEach((player, point) -> {
      CompoundTag entry = new CompoundTag();
      entry.putString("dimension", point.dimension().location().toString());
      entry.putLong("pos", point.pos().asLong());
      entry.putFloat("yaw", point.yaw());
      returnTag.put(player.toString(), entry);
    });
    tag.put("returns", returnTag);
    return tag;
  }
}
