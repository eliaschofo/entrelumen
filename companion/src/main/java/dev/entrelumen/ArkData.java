package dev.entrelumen;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
 * The Ark's persistent state, kept apart from the campaigns: each team's one Ark (where, which
 * facing, whether its core stands, the controller and the modules in their slots), each player's
 * home, command cooldowns and the Solsticio shops each team knows. Effects read this, so they work
 * while the Ark's chunks are unloaded.
 */
public final class ArkData extends SavedData {
  static final String NAME = "entrelumen_ark";
  public static final int VERSION = 1;

  /** A team's Ark. Mutable; callers mark the data dirty after a change. */
  public static final class Ark {
    public ResourceKey<Level> dimension;
    public ArkMultiblock.Anchor anchor;
    public boolean core;
    public boolean controller;
    public final Set<String> modules = new TreeSet<>();
    /** Core blocks still to place, for the screens. */
    public int missingCore;
    /** Beacons on the columns' places. */
    public int beacons;

    public Ark(ResourceKey<Level> dimension, ArkMultiblock.Anchor anchor) {
      this.dimension = dimension;
      this.anchor = anchor;
    }

    public ArkRules.Status status() {
      return new ArkRules.Status(true, core, controller, modules, beacons);
    }

    public boolean empty() {
      return !controller && modules.isEmpty();
    }
  }

  /** A player's single home. */
  public record Home(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
    public BlockPos blockPos() {
      return BlockPos.containing(x, y, z);
    }
  }

  public final Map<UUID, Ark> arks = new HashMap<>();
  public final Map<UUID, Home> homes = new HashMap<>();
  public final Map<UUID, Long> homeUsed = new HashMap<>();
  public final Map<UUID, Long> rtpUsed = new HashMap<>();
  public final Map<UUID, Set<String>> knownShops = new HashMap<>();

  public static ArkData get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(
        new Factory<>(ArkData::new, ArkData::load), NAME);
  }

  /** The Ark status of a team, or {@link ArkRules.Status#NONE}. */
  public ArkRules.Status status(UUID team) {
    Ark ark = arks.get(team);
    return ark == null ? ArkRules.Status.NONE : ark.status();
  }

  /** The team whose Ark sits at this anchor, or null. */
  public UUID owner(ResourceKey<Level> dimension, ArkMultiblock.Anchor anchor) {
    for (var entry : arks.entrySet())
      if (entry.getValue().dimension.equals(dimension) && entry.getValue().anchor.origin().equals(anchor.origin()))
        return entry.getKey();
    return null;
  }

  public static ArkData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION) throw new IllegalStateException("Unsupported Entrelumen Ark data version");
    ArkData data = new ArkData();
    CompoundTag arks = tag.getCompound("arks");
    for (String key : arks.getAllKeys()) {
      CompoundTag entry = arks.getCompound(key);
      ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
      UUID team = uuid(key);
      if (dimension == null || team == null) continue;
      Ark ark = new Ark(ResourceKey.create(Registries.DIMENSION, dimension),
          new ArkMultiblock.Anchor(BlockPos.of(entry.getLong("origin")), entry.getInt("rotation")));
      ark.core = entry.getBoolean("core");
      ark.controller = entry.getBoolean("controller");
      ark.missingCore = entry.getInt("missingCore");
      ark.beacons = Math.clamp(entry.getInt("beacons"), 0, ArkRules.MAX_BEACONS);
      for (Tag module : entry.getList("modules", Tag.TAG_STRING)) {
        if (ArkRules.Module.byId(module.getAsString()) != null) ark.modules.add(module.getAsString());
      }
      data.arks.put(team, ark);
    }
    CompoundTag homes = tag.getCompound("homes");
    for (String key : homes.getAllKeys()) {
      CompoundTag entry = homes.getCompound(key);
      ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
      UUID player = uuid(key);
      if (dimension == null || player == null) continue;
      data.homes.put(player, new Home(ResourceKey.create(Registries.DIMENSION, dimension), entry.getDouble("x"),
          entry.getDouble("y"), entry.getDouble("z"), entry.getFloat("yaw"), entry.getFloat("pitch")));
    }
    readTimes(tag.getCompound("homeUsed"), data.homeUsed);
    readTimes(tag.getCompound("rtpUsed"), data.rtpUsed);
    CompoundTag shops = tag.getCompound("knownShops");
    for (String key : shops.getAllKeys()) {
      UUID team = uuid(key);
      if (team == null) continue;
      Set<String> known = new TreeSet<>();
      for (Tag shop : shops.getList(key, Tag.TAG_STRING)) known.add(shop.getAsString());
      data.knownShops.put(team, known);
    }
    return data;
  }

  private static void readTimes(CompoundTag tag, Map<UUID, Long> target) {
    for (String key : tag.getAllKeys()) {
      UUID id = uuid(key);
      if (id != null) target.put(id, tag.getLong(key));
    }
  }

  private static UUID uuid(String key) {
    try {
      return UUID.fromString(key);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    CompoundTag arks = new CompoundTag();
    this.arks.forEach((team, ark) -> {
      CompoundTag entry = new CompoundTag();
      entry.putString("dimension", ark.dimension.location().toString());
      entry.putLong("origin", ark.anchor.origin().asLong());
      entry.putInt("rotation", ark.anchor.rotation());
      entry.putBoolean("core", ark.core);
      entry.putBoolean("controller", ark.controller);
      entry.putInt("missingCore", ark.missingCore);
      entry.putInt("beacons", ark.beacons);
      ListTag modules = new ListTag();
      ark.modules.forEach(module -> modules.add(StringTag.valueOf(module)));
      entry.put("modules", modules);
      arks.put(team.toString(), entry);
    });
    tag.put("arks", arks);
    CompoundTag homes = new CompoundTag();
    this.homes.forEach((player, home) -> {
      CompoundTag entry = new CompoundTag();
      entry.putString("dimension", home.dimension().location().toString());
      entry.putDouble("x", home.x());
      entry.putDouble("y", home.y());
      entry.putDouble("z", home.z());
      entry.putFloat("yaw", home.yaw());
      entry.putFloat("pitch", home.pitch());
      homes.put(player.toString(), entry);
    });
    tag.put("homes", homes);
    tag.put("homeUsed", writeTimes(homeUsed));
    tag.put("rtpUsed", writeTimes(rtpUsed));
    CompoundTag shops = new CompoundTag();
    knownShops.forEach((team, known) -> {
      ListTag list = new ListTag();
      known.forEach(shop -> list.add(StringTag.valueOf(shop)));
      shops.put(team.toString(), list);
    });
    tag.put("knownShops", shops);
    return tag;
  }

  private static CompoundTag writeTimes(Map<UUID, Long> source) {
    CompoundTag tag = new CompoundTag();
    source.forEach((id, time) -> tag.putLong(id.toString(), time));
    return tag;
  }
}
