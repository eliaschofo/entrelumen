package dev.entrelumen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

/**
 * Where Solsticio's villagers belong: one site per shopkeeper, side-quest NPC, common villager home
 * and native, the trading hall and the easter eggs. A site remembers the villager spawned for it,
 * so population happens once and resumes after a restart. The city keeps one registry in
 * {@link SolsticioData}; GameTests build local ones over fixture templates.
 */
public final class CommerceSites {
  /** A place a villager belongs to; {@code entity} is null until it is spawned (again). */
  public static final class Site {
    public final CommerceRules.Role role;
    /** Shop type, discipline id or side-quest id; empty for common villagers. */
    public final String key;
    /** The marker (for natives: the trading hall marker). */
    public final BlockPos pos;
    public UUID entity;

    public Site(CommerceRules.Role role, String key, BlockPos pos) {
      this.role = role;
      this.key = key;
      this.pos = pos.immutable();
    }

    public boolean sameAs(Site other) {
      return role == other.role && key.equals(other.key) && pos.equals(other.pos);
    }

    @Override
    public String toString() {
      return role.id + (key.isEmpty() ? "" : ":" + key) + "@" + pos.toShortString() + (entity == null ? " (pending)" : "");
    }
  }

  /** A commerce marker found in the template, in world coordinates. */
  public record Found(CityLayout.Marker marker, String argument, BlockPos pos) {}

  public final List<Site> sites = new ArrayList<>();
  public final Map<String, List<BlockPos>> easterEggs = new TreeMap<>();
  /** The trading hall marker, or null when the city has none. */
  public BlockPos hall;

  public void clear() {
    sites.clear();
    easterEggs.clear();
    hall = null;
  }

  /**
   * Rebuilds the sites from the template's markers: shops and side quests in marker order, the
   * six natives at the hall, homes sorted north to south. A site identical to one already known
   * keeps its villager, so re-running the placement never spawns twice.
   */
  public void rebuild(List<Found> markers, BlockPos tradingHall) {
    List<Site> previous = new ArrayList<>(sites);
    clear();
    hall = tradingHall == null ? null : tradingHall.immutable();
    Comparator<BlockPos> order = Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)
        .thenComparingInt(BlockPos::getY);
    List<Found> sorted = new ArrayList<>(markers);
    sorted.sort(Comparator.comparing(Found::pos, order));
    List<Site> fresh = new ArrayList<>();
    for (Found found : sorted) {
      switch (found.marker()) {
        case SHOP -> fresh.add(new Site(CommerceRules.Role.SHOP, found.argument(), found.pos()));
        case SIDEQUEST -> fresh.add(new Site(CommerceRules.Role.SIDEQUEST, found.argument(), found.pos()));
        case RESIDENT -> fresh.add(new Site(CommerceRules.Role.TOWNSFOLK, "", found.pos()));
        case EASTER -> easterEggs.computeIfAbsent(found.argument(), name -> new ArrayList<>()).add(found.pos().immutable());
        default -> {}
      }
    }
    if (hall != null)
      for (var discipline : LuminousRules.Discipline.values())
        fresh.add(new Site(CommerceRules.Role.NATIVE, discipline.id, hall));
    for (Site site : fresh) {
      previous.stream().filter(old -> old.sameAs(site)).findFirst().ifPresent(old -> site.entity = old.entity);
      sites.add(site);
    }
  }

  /** Common villager homes, in site order. */
  public List<Site> homes() {
    return sites.stream().filter(site -> site.role == CommerceRules.Role.TOWNSFOLK).toList();
  }

  /** Every site that should have a villager: all but the homes above the cap. */
  public List<Site> chosen(int townsfolkCap) {
    List<Site> homes = homes();
    java.util.Set<Site> kept = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    for (int index : CommerceRules.spread(homes.size(), townsfolkCap)) kept.add(homes.get(index));
    List<Site> chosen = new ArrayList<>();
    for (Site site : sites)
      if (site.role != CommerceRules.Role.TOWNSFOLK || kept.contains(site)) chosen.add(site);
    return chosen;
  }

  /** Chosen sites still waiting for their villager. */
  public List<Site> pending(int townsfolkCap) {
    return chosen(townsfolkCap).stream().filter(site -> site.entity == null).toList();
  }

  public Site byEntity(UUID entity) {
    for (Site site : sites) if (entity.equals(site.entity)) return site;
    return null;
  }

  public Site find(CommerceRules.Role role, String key, BlockPos pos) {
    for (Site site : sites) if (site.role == role && site.key.equals(key) && site.pos.equals(pos)) return site;
    return null;
  }

  /** Side-quest ids, for innkeeper personas. */
  public List<String> sideQuestIds() {
    return sites.stream().filter(site -> site.role == CommerceRules.Role.SIDEQUEST).map(site -> site.key).toList();
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    if (hall != null) tag.putLong("hall", hall.asLong());
    ListTag list = new ListTag();
    for (Site site : sites) {
      CompoundTag entry = new CompoundTag();
      entry.putString("role", site.role.id);
      entry.putString("key", site.key);
      entry.putLong("pos", site.pos.asLong());
      if (site.entity != null) entry.putUUID("entity", site.entity);
      list.add(entry);
    }
    tag.put("sites", list);
    CompoundTag eggs = new CompoundTag();
    easterEggs.forEach((name, positions) -> {
      ListTag spots = new ListTag();
      positions.forEach(pos -> spots.add(LongTag.valueOf(pos.asLong())));
      eggs.put(name, spots);
    });
    tag.put("easterEggs", eggs);
    return tag;
  }

  public void load(CompoundTag tag) {
    clear();
    if (tag.contains("hall", Tag.TAG_LONG)) hall = BlockPos.of(tag.getLong("hall"));
    for (Tag element : tag.getList("sites", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) element;
      CommerceRules.Role role = CommerceRules.Role.byId(entry.getString("role"));
      if (role == null) continue;
      Site site = new Site(role, entry.getString("key"), BlockPos.of(entry.getLong("pos")));
      if (entry.hasUUID("entity")) site.entity = entry.getUUID("entity");
      sites.add(site);
    }
    CompoundTag eggs = tag.getCompound("easterEggs");
    for (String name : eggs.getAllKeys()) {
      List<BlockPos> positions = new ArrayList<>();
      for (Tag spot : eggs.getList(name, Tag.TAG_LONG)) positions.add(BlockPos.of(((LongTag) spot).getAsLong()));
      easterEggs.put(name, positions);
    }
  }
}
