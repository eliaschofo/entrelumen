package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Places Solsticio once per world, the first time anyone needs it: templates under
 * {@code data/entrelumen/structure/solsticio/} are read and cut into column slices off the server
 * thread, and the server places a bounded number of blocks per tick. Progress survives restarts
 * in {@link SolsticioData}; markers become the arrival point, the portal anchor, plots and NPC
 * points. See {@code docs/design/solsticio.md} for the template contract.
 */
public final class SolsticioCity {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Blocks placed per tick before yielding; at least one slice is always placed. */
  static final int BLOCK_BUDGET = 24_576;
  private static final String STRUCTURE_BLOCK = "minecraft:structure_block";

  private static final Map<MinecraftServer, Job> JOBS = new WeakHashMap<>();

  private SolsticioCity() {}

  /** A piece resource and where it lands. */
  record Source(String name, ResourceLocation resource, CityLayout.Placement placement) {}

  /** A marker found in a template, in world coordinates. */
  record FoundMarker(CityLayout.Marker marker, BlockPos pos) {}

  /** One slice ready to place: its template and world origin. */
  record ReadySlice(StructureTemplate template, BlockPos origin, int blocks) {}

  /** A partitioned template: slice tags in slice order plus markers in template coordinates. */
  record Partition(List<CompoundTag> slices, List<int[]> sliceOrigins, List<String> markerNames,
      List<int[]> markerPositions, List<Integer> blockCounts) {}

  private static final class Job {
    final List<Source> sources;
    final List<FoundMarker> markers = new ArrayList<>();
    int piece;
    int globalSlice;
    CompletableFuture<List<ReadySlice>> loading;
    List<ReadySlice> current = List.of();
    int currentIndex;
    boolean failed;

    Job(List<Source> sources) {
      this.sources = sources;
    }
  }

  public static boolean isPlacing(MinecraftServer server) {
    synchronized (JOBS) {
      return JOBS.containsKey(server);
    }
  }

  /**
   * Starts (or resumes) placement unless the city is ready. Safe to call often: it does nothing
   * while a job runs. Returns whether the city is ready now.
   */
  public static boolean ensure(MinecraftServer server) {
    SolsticioData data = SolsticioData.get(server);
    if (data.ready()) return true;
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    if (level == null) {
      LOGGER.error("Dimension {} is missing; Solsticio cannot be placed", Solsticio.LEVEL.location());
      return false;
    }
    synchronized (JOBS) {
      if (JOBS.containsKey(server)) return false;
    }
    List<Source> sources;
    try {
      sources = discover(server);
    } catch (IOException | RuntimeException failure) {
      LOGGER.error("Could not read the Solsticio templates", failure);
      return false;
    }
    if (sources.isEmpty()) {
      LOGGER.error("No Solsticio templates under data/entrelumen/structure/{}/", CityLayout.TEMPLATE_DIR);
      return false;
    }
    List<String> names = sources.stream().map(source -> source.name() + "@" + source.placement().piece().sizeX()
        + "x" + source.placement().piece().sizeY() + "x" + source.placement().piece().sizeZ()).toList();
    if (data.status == SolsticioData.Status.PLACING && !names.equals(data.templates)) {
      LOGGER.warn("Solsticio templates changed during placement; starting over");
      data.slicesDone = 0;
    }
    if (data.status != SolsticioData.Status.PLACING) data.slicesDone = 0;
    data.status = SolsticioData.Status.PLACING;
    data.templates.clear();
    data.templates.addAll(names);
    data.footprint = footprint(sources);
    data.borderRadius = CityLayout.borderRadius(data.footprint);
    data.setDirty();
    StructureProtection.invalidate(server);
    SolsticioTravel.applyBorder(server);
    Job job = new Job(sources);
    synchronized (JOBS) {
      JOBS.put(server, job);
    }
    LOGGER.info("Placing Solsticio from {} template piece(s), resuming at slice {}", sources.size(), data.slicesDone);
    return false;
  }

  private static ProtectionRules.Box footprint(List<Source> sources) {
    int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    int maxY = CityLayout.BASE_Y;
    for (Source source : sources) {
      var placement = source.placement();
      minX = Math.min(minX, placement.x());
      minZ = Math.min(minZ, placement.z());
      maxX = Math.max(maxX, placement.x() + placement.piece().sizeX() - 1);
      maxZ = Math.max(maxZ, placement.z() + placement.piece().sizeZ() - 1);
      maxY = Math.max(maxY, placement.y() + placement.piece().sizeY() - 1);
    }
    return new ProtectionRules.Box(minX, CityLayout.BASE_Y, minZ, maxX, maxY, maxZ);
  }

  /** Finds the pieces and reads only their sizes. */
  static List<Source> discover(MinecraftServer server) throws IOException {
    String prefix = "structure/" + CityLayout.TEMPLATE_DIR + "/";
    Map<ResourceLocation, Resource> found = server.getResourceManager().listResources(
        "structure/" + CityLayout.TEMPLATE_DIR,
        id -> id.getNamespace().equals("entrelumen") && id.getPath().endsWith(".nbt"));
    List<CityLayout.Piece> pieces = new ArrayList<>();
    Map<String, ResourceLocation> byName = new java.util.TreeMap<>();
    for (var entry : found.entrySet()) {
      String path = entry.getKey().getPath();
      if (!path.startsWith(prefix)) continue;
      String name = path.substring(prefix.length(), path.length() - ".nbt".length());
      if (name.contains("/")) continue;
      Optional<int[]> grid = CityLayout.gridOf(name);
      if (grid.isEmpty()) {
        LOGGER.warn("Ignoring {}: Solsticio pieces are named city.nbt or piece_<x>_<z>.nbt", entry.getKey());
        continue;
      }
      CollectFields visitor = new CollectFields(new FieldSelector(ListTag.TYPE, "size"));
      try (InputStream stream = entry.getValue().open()) {
        NbtIo.parseCompressed(stream, visitor, NbtAccounter.unlimitedHeap());
      }
      ListTag size = visitor.getResult() instanceof CompoundTag root ? root.getList("size", Tag.TAG_INT) : new ListTag();
      if (size.size() != 3) throw new IOException("Template " + entry.getKey() + " has no size");
      pieces.add(new CityLayout.Piece(name, grid.get()[0], grid.get()[1], size.getInt(0), size.getInt(1), size.getInt(2)));
      byName.put(name, entry.getKey());
    }
    List<Source> sources = new ArrayList<>();
    for (var placement : CityLayout.place(pieces))
      sources.add(new Source(placement.piece().name(), byName.get(placement.piece().name()), placement));
    return sources;
  }

  /** Called every server tick; places slices while a job runs. */
  public static void tick(MinecraftServer server) {
    Job job;
    synchronized (JOBS) {
      job = JOBS.get(server);
    }
    if (job == null) return;
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    SolsticioData data = SolsticioData.get(server);
    if (level == null || job.failed) {
      synchronized (JOBS) {
        JOBS.remove(server);
      }
      return;
    }
    int placed = 0;
    boolean placedOne = false;
    while (true) {
      if (job.currentIndex >= job.current.size()) {
        if (job.loading == null) {
          if (job.piece >= job.sources.size()) {
            finish(server, level, data, job);
            return;
          }
          Source source = job.sources.get(job.piece);
          job.loading = CompletableFuture.supplyAsync(() -> load(server, source, job), Util.backgroundExecutor());
          return;
        }
        if (!job.loading.isDone()) return;
        try {
          job.current = job.loading.join();
        } catch (RuntimeException failure) {
          LOGGER.error("Could not prepare Solsticio piece {}", job.sources.get(job.piece).name(), failure);
          job.failed = true;
          return;
        }
        job.loading = null;
        job.currentIndex = 0;
        job.piece++;
      }
      if (job.currentIndex >= job.current.size()) continue;
      if (placedOne && placed >= BLOCK_BUDGET) return;
      ReadySlice slice = job.current.get(job.currentIndex++);
      int global = job.globalSlice++;
      if (global < data.slicesDone) continue;
      StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
      slice.template().placeInWorld(level, slice.origin(), slice.origin(), settings, level.getRandom(),
          Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      placed += Math.max(1, slice.blocks());
      placedOne = true;
      data.slicesDone = global + 1;
      data.setDirty();
    }
  }

  /** Reads, datafixes and partitions one piece; collects its markers into the job. */
  private static List<ReadySlice> load(MinecraftServer server, Source source, Job job) {
    CompoundTag tag;
    try {
      Resource resource = server.getResourceManager().getResource(source.resource()).orElseThrow(
          () -> new IOException("Template " + source.resource() + " disappeared"));
      try (InputStream stream = resource.open()) {
        tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
      }
    } catch (IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
    tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, NbtUtils.getDataVersion(tag, 500));
    Partition partition = partition(tag, CityLayout.SLICE);
    var placement = source.placement();
    synchronized (job.markers) {
      for (int i = 0; i < partition.markerNames().size(); i++) {
        String name = partition.markerNames().get(i);
        int[] local = partition.markerPositions().get(i);
        BlockPos world = new BlockPos(placement.x() + local[0], placement.y() + local[1], placement.z() + local[2]);
        var marker = CityLayout.Marker.parse(name);
        if (marker.isPresent()) job.markers.add(new FoundMarker(marker.get(), world));
        else LOGGER.warn("Unknown Solsticio marker '{}' at {} in {}", name, world, source.name());
      }
    }
    List<ReadySlice> slices = new ArrayList<>();
    for (int i = 0; i < partition.slices().size(); i++) {
      StructureTemplate template = new StructureTemplate();
      template.load(BuiltInRegistries.BLOCK.asLookup(), partition.slices().get(i));
      int[] origin = partition.sliceOrigins().get(i);
      slices.add(new ReadySlice(template,
          new BlockPos(placement.x() + origin[0], placement.y(), placement.z() + origin[1]),
          partition.blockCounts().get(i)));
    }
    return slices;
  }

  /**
   * Cuts a structure tag into column slices of at most {@code edge} × {@code edge}, keeping the
   * palette, block entities and entities. DATA structure blocks become air and are returned as
   * markers (metadata and template position).
   */
  static Partition partition(CompoundTag tag, int edge) {
    ListTag size = tag.getList("size", Tag.TAG_INT);
    int sizeX = size.getInt(0), sizeY = size.getInt(1), sizeZ = size.getInt(2);
    ListTag palette = tag.contains("palettes", Tag.TAG_LIST)
        ? tag.getList("palettes", Tag.TAG_LIST).getList(0).copy()
        : tag.getList("palette", Tag.TAG_COMPOUND).copy();
    int air = palette.size();
    CompoundTag airState = new CompoundTag();
    airState.putString("Name", "minecraft:air");
    palette.add(airState);
    var slices = CityLayout.slices(0, sizeX, sizeZ, edge);
    int columns = (sizeX + edge - 1) / edge;
    List<ListTag> blocks = new ArrayList<>(), entities = new ArrayList<>();
    for (int i = 0; i < slices.size(); i++) {
      blocks.add(new ListTag());
      entities.add(new ListTag());
    }
    List<String> markerNames = new ArrayList<>();
    List<int[]> markerPositions = new ArrayList<>();
    for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
      CompoundTag block = (CompoundTag) element;
      ListTag pos = block.getList("pos", Tag.TAG_INT);
      int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
      if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) continue;
      int state = block.getInt("state");
      CompoundTag copy = block.copy();
      if (state >= 0 && state < air
          && STRUCTURE_BLOCK.equals(palette.getCompound(state).getString("Name"))
          && block.contains("nbt", Tag.TAG_COMPOUND)
          && "DATA".equals(block.getCompound("nbt").getString("mode"))) {
        markerNames.add(block.getCompound("nbt").getString("metadata"));
        markerPositions.add(new int[] {x, y, z});
        copy = new CompoundTag();
        copy.putInt("state", air);
      }
      int slice = (z / edge) * columns + (x / edge);
      copy.put("pos", intList(x - (x / edge) * edge, y, z - (z / edge) * edge));
      blocks.get(slice).add(copy);
    }
    for (Tag element : tag.getList("entities", Tag.TAG_COMPOUND)) {
      CompoundTag entity = (CompoundTag) element;
      ListTag blockPos = entity.getList("blockPos", Tag.TAG_INT);
      ListTag pos = entity.getList("pos", Tag.TAG_DOUBLE);
      if (blockPos.size() != 3 || pos.size() != 3) continue;
      int x = Math.clamp(blockPos.getInt(0), 0, sizeX - 1), z = Math.clamp(blockPos.getInt(2), 0, sizeZ - 1);
      int dx = (x / edge) * edge, dz = (z / edge) * edge;
      CompoundTag copy = entity.copy();
      copy.put("blockPos", intList(blockPos.getInt(0) - dx, blockPos.getInt(1), blockPos.getInt(2) - dz));
      ListTag moved = new ListTag();
      moved.add(DoubleTag.valueOf(pos.getDouble(0) - dx));
      moved.add(DoubleTag.valueOf(pos.getDouble(1)));
      moved.add(DoubleTag.valueOf(pos.getDouble(2) - dz));
      copy.put("pos", moved);
      entities.get((z / edge) * columns + (x / edge)).add(copy);
    }
    List<CompoundTag> tags = new ArrayList<>();
    List<int[]> origins = new ArrayList<>();
    List<Integer> counts = new ArrayList<>();
    for (int i = 0; i < slices.size(); i++) {
      var slice = slices.get(i);
      CompoundTag sub = new CompoundTag();
      sub.put("size", intList(slice.width(), sizeY, slice.depth()));
      sub.put("palette", palette.copy());
      sub.put("blocks", blocks.get(i));
      sub.put("entities", entities.get(i));
      if (tag.contains("DataVersion")) sub.putInt("DataVersion", tag.getInt("DataVersion"));
      tags.add(sub);
      origins.add(new int[] {slice.fromX(), slice.fromZ()});
      counts.add(blocks.get(i).size());
    }
    return new Partition(tags, origins, markerNames, markerPositions, counts);
  }

  private static ListTag intList(int... values) {
    ListTag list = new ListTag();
    for (int value : values) list.add(IntTag.valueOf(value));
    return list;
  }

  private static void finish(MinecraftServer server, ServerLevel level, SolsticioData data, Job job) {
    synchronized (JOBS) {
      JOBS.remove(server);
    }
    data.arrival = data.portal = data.waystone = data.tradingHall = null;
    data.npcs.clear();
    List<BlockPos> plotCorners = new ArrayList<>();
    boolean provisional = false;
    for (FoundMarker found : job.markers) {
      switch (found.marker()) {
        case ARRIVAL -> data.arrival = found.pos();
        case TOWN_HALL_PORTAL -> data.portal = found.pos();
        case TOWN_HALL_WAYSTONE -> data.waystone = found.pos();
        case TRADING_HALL -> data.tradingHall = found.pos();
        case PLAYER_PLOT -> plotCorners.add(found.pos());
        case PROVISIONAL -> provisional = true;
        default -> {
          if (found.marker().npc()) data.npcs.put(found.marker().id, found.pos());
        }
      }
    }
    plotCorners.sort(Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)
        .thenComparingInt(BlockPos::getY));
    // Keep claims when a placement resumes over plots that already exist.
    List<SolsticioData.Plot> previous = new ArrayList<>(data.plots);
    data.plots.clear();
    for (BlockPos corner : plotCorners) {
      SolsticioData.Plot plot = previous.stream().filter(old -> old.corner.equals(corner)).findFirst()
          .orElseGet(() -> new SolsticioData.Plot(corner));
      data.plots.add(plot);
    }
    if (data.arrival == null) {
      data.arrival = SolsticioTravel.fallbackArrival(level, data.footprint);
      LOGGER.error("Solsticio has no 'arrival' marker; using {}", data.arrival);
    }
    if (data.portal == null) LOGGER.warn("Solsticio has no 'town_hall_portal' marker; the portal cannot be built");
    else level.setBlock(data.portal, Solsticio.PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
    data.provisional = provisional;
    data.status = SolsticioData.Status.READY;
    data.placements++;
    data.readyGameTime = level.getGameTime();
    data.setDirty();
    StructureProtection.invalidate(server);
    SolsticioTravel.applyBorder(server);
    if (provisional)
      LOGGER.warn("Solsticio was placed from the PROVISIONAL template; replace data/entrelumen/structure/solsticio/");
    LOGGER.info("Solsticio is ready: arrival {}, portal {}, {} plot(s), NPC points {}", data.arrival, data.portal,
        data.plots.size(), data.npcs.keySet());
    for (ServerPlayer player : level.players()) SolsticioTravel.rescue(player);
  }

  // ---- Plots ------------------------------------------------------------------------------

  /**
   * The first member of a team to stand in the ready city claims one plot for the team, if the
   * team unlocked Solsticio. Returns the plot index, or -1.
   */
  public static int claimPlot(ServerPlayer player) {
    SolsticioData data = SolsticioData.get(player.server);
    if (!data.ready() || data.plots.isEmpty()) return -1;
    var actor = StructureProtection.actor(player);
    UUID campaign = actor.campaign();
    if (campaign == null || actor.automaton() || !Solsticio.GATE.satisfiedBy(actor)) return -1;
    List<UUID> owners = new ArrayList<>();
    data.plots.forEach(plot -> owners.add(plot.owner));
    int index = CityLayout.plotFor(owners, campaign);
    if (index < 0) {
      player.sendSystemMessage(Component.translatable("entrelumen.solsticio.plots_full"));
      return -1;
    }
    SolsticioData.Plot plot = data.plots.get(index);
    if (plot.owner == null) {
      plot.owner = campaign;
      plot.ownerName = player.getGameProfile().getName();
      plot.claimedAt = player.serverLevel().getGameTime();
      data.setDirty();
      StructureProtection.invalidate(player.server);
      player.sendSystemMessage(Component.translatable("entrelumen.solsticio.plot_claimed",
          plot.corner.getX(), plot.corner.getY(), plot.corner.getZ()));
    }
    return index;
  }

  /** Operators may free a plot; the blocks inside stay as they are. */
  public static boolean releasePlot(MinecraftServer server, int index) {
    SolsticioData data = SolsticioData.get(server);
    if (index < 0 || index >= data.plots.size() || data.plots.get(index).owner == null) return false;
    SolsticioData.Plot plot = data.plots.get(index);
    plot.owner = null;
    plot.ownerName = "";
    plot.claimedAt = 0;
    data.setDirty();
    StructureProtection.invalidate(server);
    return true;
  }

  /** The protection regions Solsticio contributes: the whole bordered city and the rift. */
  static List<ProtectionRules.Region> regions(MinecraftServer server) {
    SolsticioData data = SolsticioData.get(server);
    List<ProtectionRules.Region> regions = new ArrayList<>();
    if (data.footprint != null && data.borderRadius > 0) {
      ServerLevel level = server.getLevel(Solsticio.LEVEL);
      int minY = level == null ? 0 : level.getMinBuildHeight();
      int maxY = level == null ? 319 : level.getMaxBuildHeight() - 1;
      int r = data.borderRadius;
      List<ProtectionRules.Hole> holes = new ArrayList<>();
      for (int i = 0; i < data.plots.size(); i++) {
        var plot = data.plots.get(i);
        holes.add(new ProtectionRules.Hole("plot_" + i, plot.box(), plot.owner, true));
      }
      regions.add(new ProtectionRules.Region(Solsticio.REGION_ID, Solsticio.LEVEL.location().toString(),
          new ProtectionRules.Box(-r, minY, -r, r, maxY, r), Solsticio.GATE, holes));
    }
    if (data.overworldPortal != null) {
      BlockPos p = data.overworldPortal;
      regions.add(new ProtectionRules.Region(Solsticio.RIFT_REGION_ID, "minecraft:overworld",
          new ProtectionRules.Box(p.getX() - 1, p.getY() - 1, p.getZ() - 1, p.getX() + 1, p.getY() + 2, p.getZ() + 1),
          ProtectionRules.ActGate.NONE, List.of()));
    }
    return regions;
  }
}
