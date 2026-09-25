package dev.entrelumen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Solsticio's commerce at runtime. When the city is placed, every {@code shop:<type>},
 * {@code sidequest:<id>} and {@code resident} marker and the six natives of the trading hall get a
 * villager, once, in the background, resuming after a restart. Shopkeepers, natives and side-quest
 * NPCs are statues (no AI) that restock lazily when someone trades; nothing ticks per villager in
 * this code. Villagers from elsewhere that enter the trading hall settle for good with a discount;
 * natives sell their Luminosity once they have stood in the biome they ask for. Prices drop again
 * when the Entrelumen is liberated. Tables, marker contract and balance: {@code
 * docs/design/solsticio-commerce.md}.
 */
public final class SolsticioCommerce {
  private static final Logger LOGGER = LogUtils.getLogger();
  /** Persistent entity data of every villager with a Solsticio role. */
  public static final String DATA = "entrelumen_commerce";
  /** Scoreboard tag of the same villagers, for selectors ({@code @e[tag=entrelumen.solsticio]}). */
  public static final String TAG = "entrelumen.solsticio";
  /** Registry of a villager's site: the city's ({@link SolsticioData}) or a local (test) one. */
  public static final String CITY = "city", LOCAL = "local";
  static final TicketType<ChunkPos> TICKET =
      TicketType.create("entrelumen_commerce", Comparator.comparingLong(ChunkPos::toLong));
  static final int HALL_SCAN_TICKS = 100, SPAWNS_PER_TICK = 8, SAY_RANGE = 16;

  public static final ModConfigSpec SERVER_SPEC;
  static final ModConfigSpec.IntValue RESIDENT_CAP, HALL_RADIUS, RESTOCK_TICKS;
  static final ModConfigSpec.DoubleValue LIBERATED_PRICES, SETTLED_PRICES, NATIVE_PRICES;

  static {
    var builder = new ModConfigSpec.Builder();
    builder.push("commerce");
    RESIDENT_CAP = builder.comment("Common villagers spawned at the city's resident homes, at most (performance).")
        .translation("entrelumen.config.resident_cap").defineInRange("residentCap", 40, 0, 256);
    HALL_RADIUS = builder.comment("Horizontal radius of the trading hall zone around its marker. Villagers from",
            "elsewhere that enter it settle in Solsticio.")
        .translation("entrelumen.config.trading_hall_radius").defineInRange("tradingHallRadius", 12, 4, 48);
    RESTOCK_TICKS = builder.comment("Game ticks between restocks of shopkeepers, natives and settled villagers,",
            "counted lazily when someone opens their trades.")
        .translation("entrelumen.config.restock_ticks").defineInRange("restockTicks", 24000, 1200, 240000);
    LIBERATED_PRICES = builder.comment("Price multiplier of every Solsticio merchant once the Entrelumen is liberated.")
        .translation("entrelumen.config.liberated_prices").defineInRange("liberatedPriceMultiplier", 0.6, 0.05, 1.0);
    SETTLED_PRICES = builder.comment("Price multiplier of a villager that moved into the trading hall.")
        .translation("entrelumen.config.settled_prices").defineInRange("settledPriceMultiplier", 0.5, 0.05, 1.0);
    NATIVE_PRICES = builder.comment("Price multiplier of a native once it has stood in the biome it asks for.")
        .translation("entrelumen.config.native_prices").defineInRange("nativeHomePriceMultiplier", 0.5, 0.05, 1.0);
    builder.pop();
    SERVER_SPEC = builder.build();
  }

  private static volatile Map<String, CommerceRules.Table> shops = Map.of(), natives = Map.of();
  private static final Map<String, SideQuest> SIDE_QUESTS = new ConcurrentHashMap<>();
  /** Offers closed for the player now trading, with their real uses, restored when trading stops. */
  private static final Map<Villager, List<Locked>> LOCKS = new WeakHashMap<>();
  private static final Map<MinecraftServer, Population> JOBS = new WeakHashMap<>();

  private SolsticioCommerce() {}

  private record Locked(MerchantOffer offer, int uses) {}

  /** A background population of the city's sites; exists only while some site waits. */
  private static final class Population {
    final Set<Long> tickets = new HashSet<>();
    final Set<CommerceSites.Site> failed = Collections.newSetFromMap(new IdentityHashMap<>());
    final long startedAt = System.nanoTime();
    int spawned;
  }

  /**
   * What a side quest does when its NPC is right-clicked. Return true when it handled the click;
   * otherwise the NPC says its default line. The quests themselves are future work.
   */
  @FunctionalInterface
  public interface SideQuest {
    boolean interact(ServerPlayer player, Villager npc, String id);
  }

  /** Hooks a side quest to the NPC of its {@code sidequest:<id>} marker. */
  public static void registerSideQuest(String id, SideQuest quest) {
    SIDE_QUESTS.put(id, quest);
  }

  public static void unregisterSideQuest(String id) {
    SIDE_QUESTS.remove(id);
  }

  static void register(IEventBus modBus, ModContainer container) {
    if (container != null)
      container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "entrelumen-commerce-server.toml");
    NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
      event.addListener(new TableLoader("solsticio_shops", false));
      event.addListener(new TableLoader("solsticio_natives", true));
    });
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SolsticioCommerce::onInteract);
    NeoForge.EVENT_BUS.addListener(SolsticioCommerce::onJoin);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, SolsticioCommerce::onDeath);
    NeoForge.EVENT_BUS.addListener(SolsticioCommerce::onLeave);
    NeoForge.EVENT_BUS.addListener(SolsticioCommerce::onConvert);
    NeoForge.EVENT_BUS.addListener(CommerceOffers::onUseItem);
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> onStarted(event.getServer()));
    NeoForge.EVENT_BUS.addListener(SolsticioCommerce::commands);
  }

  // ---- settings and tables --------------------------------------------------------------

  static int residentCap() {
    return intValue(RESIDENT_CAP);
  }

  static int hallRadius() {
    return intValue(HALL_RADIUS);
  }

  static int restockTicks() {
    return intValue(RESTOCK_TICKS);
  }

  private static int intValue(ModConfigSpec.IntValue value) {
    try {
      return value.getAsInt();
    } catch (IllegalStateException notLoaded) {
      return value.getDefault();
    }
  }

  static CommerceRules.Prices prices() {
    try {
      return new CommerceRules.Prices(LIBERATED_PRICES.getAsDouble(), SETTLED_PRICES.getAsDouble(),
          NATIVE_PRICES.getAsDouble());
    } catch (IllegalStateException notLoaded) {
      return CommerceRules.Prices.DEFAULT;
    }
  }

  public static Map<String, CommerceRules.Table> shops() {
    return shops;
  }

  public static Map<String, CommerceRules.Table> natives() {
    return natives;
  }

  /** Reads {@code data/entrelumen/<directory>/*.json}; reloads with {@code /reload}. */
  static final class TableLoader extends SimpleJsonResourceReloadListener {
    private final boolean nativeTables;
    private final String directory;

    TableLoader(String directory, boolean nativeTables) {
      super(new Gson(), directory);
      this.directory = directory;
      this.nativeTables = nativeTables;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> found, ResourceManager manager, ProfilerFiller profiler) {
      Map<String, CommerceRules.Table> tables = new TreeMap<>();
      for (var entry : found.entrySet()) {
        if (!entry.getKey().getNamespace().equals("entrelumen")) continue;
        String id = entry.getKey().getPath();
        if (nativeTables && discipline(id) == null) {
          LOGGER.warn("Ignoring {}/{}: natives are named after a discipline", directory, id);
          continue;
        }
        CommerceRules.Parsed parsed = CommerceRules.parse(id, entry.getValue(), nativeTables);
        parsed.problems().forEach(problem -> LOGGER.warn("Solsticio {}: {}", directory, problem));
        if (parsed.table() != null) tables.put(id, parsed.table());
      }
      if (nativeTables) natives = Map.copyOf(tables);
      else shops = Map.copyOf(tables);
      LOGGER.info("Loaded {} Solsticio {} tables", tables.size(), directory);
    }
  }

  static LuminousRules.Discipline discipline(String id) {
    for (var discipline : LuminousRules.Discipline.values()) if (discipline.id.equals(id)) return discipline;
    return null;
  }

  // ---- villagers ------------------------------------------------------------------------

  /** The Solsticio data of a villager, or null when it has no role. Mutable. */
  public static CompoundTag data(Entity entity) {
    // The scoreboard tag first: reading persistent data would give every other villager an empty one.
    if (!entity.getTags().contains(TAG)) return null;
    CompoundTag persistent = entity.getPersistentData();
    return persistent.contains(DATA, Tag.TAG_COMPOUND) ? persistent.getCompound(DATA) : null;
  }

  public static CommerceRules.Role role(Entity entity) {
    CompoundTag data = data(entity);
    return data == null ? null : CommerceRules.Role.byId(data.getString("role"));
  }

  private static CompoundTag mark(Villager villager, CommerceRules.Role role, String key, String registry,
      BlockPos site) {
    CompoundTag data = new CompoundTag();
    data.putString("role", role.id);
    data.putString("key", key);
    data.putString("registry", registry);
    if (site != null) data.putLong("site", site.asLong());
    villager.getPersistentData().put(DATA, data);
    villager.addTag(TAG);
    villager.setPersistenceRequired();
    return data;
  }

  /** Spawns the villager of a site now; the caller made sure its chunk and entities are loaded. */
  static Villager spawn(ServerLevel level, CommerceSites sites, CommerceSites.Site site, String registry) {
    BlockPos at = site.pos;
    float yaw;
    if (site.role == CommerceRules.Role.NATIVE) {
      at = nativeSpot(level, site);
      yaw = facing(at, site.pos);
    } else if (site.role == CommerceRules.Role.TOWNSFOLK) {
      yaw = level.getRandom().nextFloat() * 360.0F - 180.0F;
    } else {
      yaw = openFacing(level, at);
    }
    Villager villager = EntityType.VILLAGER.create(level);
    if (villager == null) return null;
    villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
    villager.setYHeadRot(yaw);
    villager.setYBodyRot(yaw);
    villager.setInvulnerable(true);
    CompoundTag data = mark(villager, site.role, site.key, registry, site.pos);
    switch (site.role) {
      case SHOP -> {
        CommerceRules.Table table = shops.get(site.key);
        dress(villager, table, CommerceRules.MERCHANT_LEVEL);
        villager.setNoAi(true);
        villager.setCustomName(Component.translatableWithFallback("entrelumen.solsticio.shop." + site.key,
            "Shopkeeper (" + site.key + ")"));
        if (table != null) restock(level, villager, data, table, false);
        else villager.setOffers(new MerchantOffers());
      }
      case NATIVE -> {
        CommerceRules.Table table = natives.get(site.key);
        dress(villager, table, CommerceRules.MERCHANT_LEVEL);
        villager.setNoAi(true);
        villager.setCustomName(Component.translatable("entrelumen.solsticio.native." + site.key));
        if (table != null) restock(level, villager, data, table, false);
        else villager.setOffers(new MerchantOffers());
      }
      case SIDEQUEST -> {
        int persona = CommerceRules.persona(site.key, sites.sideQuestIds());
        data.putInt("persona", persona);
        villager.setVillagerData(new VillagerData(SolsticioStory.HELIODOR.get(), VillagerProfession.NONE, 1));
        villager.setNoAi(true);
        villager.setCustomName(Component.translatable("entrelumen.solsticio.innkeeper." + persona));
        villager.setOffers(new MerchantOffers());
        // The innkeepers offer act VI's errands.
        SolsticioStory.hookInnkeepers(List.of(site.key));
      }
      case TOWNSFOLK -> {
        villager.setVillagerData(new VillagerData(SolsticioStory.HELIODOR.get(), VillagerProfession.NITWIT, 1));
        villager.restrictTo(site.pos, CommerceRules.TOWNSFOLK_RANGE);
      }
      case CHARACTER -> {
        var profession = SolsticioStory.CHARACTER_PROFESSIONS.get(site.key);
        villager.setVillagerData(new VillagerData(SolsticioStory.HELIODOR.get(),
            profession == null ? VillagerProfession.NONE : profession.get(), CommerceRules.MERCHANT_LEVEL));
        villager.setNoAi(true);
        villager.setCustomName(Component.translatable("entrelumen.solsticio.character." + site.key));
        villager.setOffers(new MerchantOffers());
      }
      case MOVED -> {}
    }
    site.entity = villager.getUUID();
    if (!level.addFreshEntity(villager)) {
      site.entity = null;
      return null;
    }
    return villager;
  }

  private static void dress(Villager villager, CommerceRules.Table table, int level) {
    VillagerProfession profession = VillagerProfession.NONE;
    // Every villager the city spawns wears the Heliodor clothing (act VI); the table's villager_type is
    // no longer used for the city's own merchants.
    VillagerType type = SolsticioStory.HELIODOR.get();
    if (table != null) {
      profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(ResourceLocation.parse(table.profession()))
          .orElse(VillagerProfession.NONE);
    }
    // Set before the offers: a profession change clears a villager's offers.
    villager.setVillagerData(new VillagerData(type, profession, level));
  }

  /** Rebuilds a merchant's offers from its table: a lazy restock that carries demand. */
  static void restock(ServerLevel level, Villager villager, CompoundTag data, CommerceRules.Table table,
      boolean carryDemand) {
    MerchantOffers previous = carryDemand ? villager.getOffers() : new MerchantOffers();
    CommerceOffers.Built built = CommerceOffers.build(level, table, previous);
    villager.setOffers(built.offers());
    ListTag locks = new ListTag();
    for (CommerceOffers.Lock lock : built.locks()) {
      CompoundTag entry = new CompoundTag();
      entry.putInt("act", lock.gate().act());
      entry.putString("milestone", lock.gate().milestone());
      entry.putBoolean("luminosity", lock.luminosity());
      locks.add(entry);
    }
    data.put("locks", locks);
    data.putLong("lastRestock", Math.max(1, level.getGameTime()));
  }

  /** Where a native stands: the first standable spots around the trading hall, one per discipline. */
  static BlockPos nativeSpot(ServerLevel level, CommerceSites.Site site) {
    LuminousRules.Discipline discipline = discipline(site.key);
    int ordinal = discipline == null ? 0 : discipline.ordinal();
    List<BlockPos> spots = new ArrayList<>();
    for (int[] offset : CommerceRules.NATIVE_RING) {
      BlockPos spot = site.pos.offset(offset[0], 0, offset[1]);
      if (level.isLoaded(spot) && level.isLoaded(spot.above()) && SolsticioTravel.standable(level, spot)) spots.add(spot);
      if (spots.size() == LuminousRules.Discipline.values().length) break;
    }
    return spots.isEmpty() ? site.pos : spots.get(Math.min(ordinal, spots.size() - 1));
  }

  /** The yaw that looks from one block toward another. */
  static float facing(BlockPos from, BlockPos to) {
    int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
    if (dx == 0 && dz == 0) return 0.0F;
    return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
  }

  /** Faces the longest free line at head height: from behind a counter, toward the shop door. */
  static float openFacing(ServerLevel level, BlockPos pos) {
    Direction best = Direction.SOUTH;
    int bestRun = -1;
    for (Direction direction : new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
      int run = 0;
      for (int step = 1; step <= 6; step++) {
        BlockPos probe = pos.above().relative(direction, step);
        if (!level.isLoaded(probe) || !level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) break;
        run++;
      }
      if (run > bestRun) {
        bestRun = run;
        best = direction;
      }
    }
    return best.toYRot();
  }

  /** A villager of this site already stands there (a restart lost the record, not the villager). */
  static boolean adopt(ServerLevel level, CommerceSites.Site site) {
    for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(site.pos).inflate(6), v -> belongs(v, site))) {
      site.entity = villager.getUUID();
      return true;
    }
    return false;
  }

  private static boolean belongs(Villager villager, CommerceSites.Site site) {
    CompoundTag data = data(villager);
    return data != null && site.role.id.equals(data.getString("role")) && site.key.equals(data.getString("key"))
        && data.getLong("site") == site.pos.asLong();
  }

  // ---- population -----------------------------------------------------------------------

  /** Starts (or keeps) the background population of the city's pending sites. */
  public static void startPopulation(MinecraftServer server) {
    JOBS.putIfAbsent(server, new Population());
  }

  public static boolean isPopulating(MinecraftServer server) {
    return JOBS.containsKey(server);
  }

  private static void onStarted(MinecraftServer server) {
    SolsticioData data = SolsticioData.get(server);
    if (!data.ready()) return;
    // Cities placed before commerce existed still get their six natives.
    if (data.commerce.sites.isEmpty() && data.commerce.hall == null && data.tradingHall != null) {
      data.commerce.rebuild(List.of(), data.tradingHall);
      data.setDirty();
    }
    // Cities placed before act VI's characters existed get Aurelia, Terra, Juan and Bodhi.
    if (data.commerce.ensureCharacters(data.npcs)) data.setDirty();
    if (!data.commerce.pending(residentCap()).isEmpty()) startPopulation(server);
  }

  static void tick(MinecraftServer server) {
    if (server.getTickCount() % HALL_SCAN_TICKS == 0) scanCityHall(server);
    Population job = JOBS.get(server);
    if (job == null) return;
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    SolsticioData data = SolsticioData.get(server);
    if (level == null) {
      JOBS.remove(server);
      return;
    }
    if (!data.ready()) return;
    List<CommerceSites.Site> pending = new ArrayList<>(data.commerce.pending(residentCap()));
    pending.removeIf(job.failed::contains);
    if (pending.isEmpty()) {
      for (long chunk : job.tickets) {
        ChunkPos pos = new ChunkPos(chunk);
        level.getChunkSource().removeRegionTicket(TICKET, pos, 1, pos);
      }
      JOBS.remove(server);
      LOGGER.info("Solsticio commerce populated: {} villager(s) spawned in {} ms, {} failed", job.spawned,
          (System.nanoTime() - job.startedAt) / 1_000_000, job.failed.size());
      return;
    }
    int budget = SPAWNS_PER_TICK;
    for (CommerceSites.Site site : pending) {
      ChunkPos chunk = new ChunkPos(site.pos);
      // Distance 1: the chunk and its neighbours load fully, for natives standing across a border.
      if (job.tickets.add(chunk.toLong())) level.getChunkSource().addRegionTicket(TICKET, chunk, 1, chunk);
      if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null || !level.areEntitiesLoaded(chunk.toLong()))
        continue;
      if (adopt(level, site)) {
        data.setDirty();
        continue;
      }
      if (spawn(level, data.commerce, site, CITY) == null) {
        job.failed.add(site);
        LOGGER.warn("Could not spawn Solsticio's {}", site);
        continue;
      }
      job.spawned++;
      data.setDirty();
      if (--budget == 0) break;
    }
  }

  /** Spawns every loaded, pending site of a local registry at once (GameTests). */
  static List<Villager> populateNow(ServerLevel level, CommerceSites sites, int townsfolkCap) {
    List<Villager> spawned = new ArrayList<>();
    for (CommerceSites.Site site : sites.pending(townsfolkCap)) {
      if (!level.isLoaded(site.pos) || adopt(level, site)) continue;
      Villager villager = spawn(level, sites, site, LOCAL);
      if (villager != null) spawned.add(villager);
    }
    return spawned;
  }

  private static void vacate(MinecraftServer server, Villager villager, boolean discarded) {
    CompoundTag data = data(villager);
    if (data == null || !CITY.equals(data.getString("registry"))) return;
    CommerceRules.Role role = CommerceRules.Role.byId(data.getString("role"));
    if (role == null || role == CommerceRules.Role.MOVED) return;
    // Natives and common villagers may be carried away (Easy Villagers); only death brings them back.
    if (discarded && !role.bound()) return;
    SolsticioData solsticio = SolsticioData.get(server);
    CommerceSites.Site site = solsticio.commerce.byEntity(villager.getUUID());
    if (site == null) return;
    site.entity = null;
    solsticio.setDirty();
    startPopulation(server);
    LOGGER.info("Solsticio's {} is gone ({}); a new one takes the post", site,
        discarded ? "carried away" : "died");
  }

  // ---- trading hall ---------------------------------------------------------------------

  private static void scanCityHall(MinecraftServer server) {
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    if (level == null || level.players().isEmpty()) return;
    BlockPos hall = SolsticioData.get(server).commerce.hall;
    if (hall == null || !level.isLoaded(hall)) return;
    scanHall(level, hall, hallRadius());
  }

  /** Settles every villager from elsewhere standing in the trading hall zone. */
  public static List<Villager> scanHall(ServerLevel level, BlockPos hall, int radius) {
    AABB box = new AABB(hall.getX() - radius, hall.getY() - CommerceRules.HALL_DOWN, hall.getZ() - radius,
        hall.getX() + radius + 1, hall.getY() + CommerceRules.HALL_UP + 1, hall.getZ() + radius + 1);
    List<Villager> settled = new ArrayList<>();
    for (Villager villager : level.getEntitiesOfClass(Villager.class, box, v -> data(v) == null && inHall(v, hall, radius)))
      if (settle(level, villager)) settled.add(villager);
    return settled;
  }

  private static boolean inHall(Villager villager, BlockPos hall, int radius) {
    BlockPos pos = villager.blockPosition();
    return CommerceRules.inHall(pos.getX(), pos.getY(), pos.getZ(), hall.getX(), hall.getY(), hall.getZ(), radius);
  }

  /** A villager from elsewhere becomes a Solsticio resident: its trades are cheaper for good. */
  static boolean settle(ServerLevel level, Villager villager) {
    if (data(villager) != null || villager.isBaby() || !villager.isAlive()) return false;
    CompoundTag data = mark(villager, CommerceRules.Role.MOVED, "", CITY, null);
    data.putLong("settledAt", Math.max(1, level.getGameTime()));
    sayAround(level, villager, Component.translatable("entrelumen.solsticio.settled", villager.getDisplayName()));
    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 12,
        0.4, 0.5, 0.4, 0.0);
    return true;
  }

  // ---- natives --------------------------------------------------------------------------

  /**
   * A native standing in the Overworld biome it asks for wakes its Luminosity for good. Returns
   * whether it woke now; asleep and asked by a player, it repeats where it wants to go.
   */
  static boolean awaken(ServerLevel level, Villager villager, CompoundTag data, CommerceRules.Table table,
      ServerPlayer asker) {
    if (data.getBoolean("awakened") || table == null) return false;
    String key = data.getString("key");
    if (!level.dimension().equals(Level.OVERWORLD) || !inBiome(level.getBiome(villager.blockPosition()), table.biomes())) {
      if (asker != null)
        asker.sendSystemMessage(says(villager.getDisplayName(),
            Component.translatable("entrelumen.solsticio.native." + key + ".request")));
      return false;
    }
    data.putBoolean("awakened", true);
    data.putLong("awakenedAt", Math.max(1, level.getGameTime()));
    LuminousRules.Discipline discipline = discipline(key);
    Component luminosity = discipline == null ? Component.literal(key)
        : new ItemStack(Luminous.LUMINOSITIES.get(discipline).get()).getHoverName();
    Component message = Component.translatable("entrelumen.solsticio.native.awakened", villager.getDisplayName(), luminosity);
    if (asker != null) asker.sendSystemMessage(message);
    else sayAround(level, villager, message);
    level.sendParticles(ParticleTypes.END_ROD, villager.getX(), villager.getY() + 1.0, villager.getZ(), 24, 0.4, 0.8,
        0.4, 0.02);
    level.playSound(null, villager, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.2F);
    return true;
  }

  /** Biome ids or {@code #tags}, any of which matches. */
  static boolean inBiome(Holder<Biome> biome, List<String> biomes) {
    for (String spec : biomes) {
      boolean tag = spec.startsWith("#");
      ResourceLocation id = ResourceLocation.tryParse(tag ? spec.substring(1) : spec);
      if (id == null) continue;
      if (tag ? biome.is(TagKey.create(Registries.BIOME, id)) : biome.is(id)) return true;
    }
    return false;
  }

  // ---- interaction ----------------------------------------------------------------------

  static void onInteract(PlayerInteractEvent.EntityInteract event) {
    if (!(event.getTarget() instanceof Villager villager) || !(event.getEntity() instanceof ServerPlayer player)) return;
    CompoundTag data = data(villager);
    CommerceRules.Role role = data == null ? null : CommerceRules.Role.byId(data.getString("role"));
    if (role == null) return;
    InteractionResult result = interact(player, villager, data, role, event.getHand());
    if (result != null) {
      event.setCanceled(true);
      event.setCancellationResult(result);
    }
  }

  /**
   * A right-click on a Solsticio villager. Merchants restock or wake before vanilla opens their
   * trades (null lets it); side-quest NPCs and common villagers talk instead.
   */
  static InteractionResult interact(ServerPlayer player, Villager villager, CompoundTag data, CommerceRules.Role role,
      InteractionHand hand) {
    // Fixed NPCs stay at their post: no sneaking pick-ups by carrying mods.
    if (role.bound() && player.isSecondaryUseActive()) return InteractionResult.FAIL;
    boolean main = hand == InteractionHand.MAIN_HAND;
    ServerLevel level = player.serverLevel();
    switch (role) {
      case SIDEQUEST -> {
        if (main) sideQuest(player, villager, data);
        return InteractionResult.SUCCESS;
      }
      case CHARACTER -> {
        if (main) SolsticioStory.character(player, villager, data.getString("key"));
        return InteractionResult.SUCCESS;
      }
      case TOWNSFOLK -> {
        if (main && !SolsticioStory.townsfolk(player, villager))
          player.sendSystemMessage(townsfolkLine(level.getRandom(), SolsticioStory.world(level.getServer())));
        return InteractionResult.SUCCESS;
      }
      case SHOP, NATIVE -> {
        if (!main || villager.isTrading()) return null;
        // An errand for this shop (act VI) takes the click before the trades open.
        if (role == CommerceRules.Role.SHOP && SolsticioStory.shopErrand(player, villager, data.getString("key")))
          return InteractionResult.SUCCESS;
        CommerceRules.Table table = (role == CommerceRules.Role.SHOP ? shops : natives).get(data.getString("key"));
        if (role == CommerceRules.Role.NATIVE) awaken(level, villager, data, table, player);
        if (table == null) {
          if (!villager.getOffers().isEmpty()) return null;
          player.displayClientMessage(Component.translatable("entrelumen.solsticio.shop.closed"), true);
          return InteractionResult.SUCCESS;
        }
        MerchantOffers current = villager.getOffers();
        boolean aligned = !current.isEmpty() && data.getList("locks", Tag.TAG_COMPOUND).size() == current.size();
        if (!aligned || CommerceRules.restockDue(level.getGameTime(), data.getLong("lastRestock"), restockTicks()))
          restock(level, villager, data, table, aligned);
        return null;
      }
      case MOVED -> {
        if (main && !villager.isTrading()
            && CommerceRules.restockDue(level.getGameTime(), data.getLong("lastRestock"), restockTicks())) {
          villager.restock();
          data.putLong("lastRestock", Math.max(1, level.getGameTime()));
        }
        return null;
      }
    }
    return null;
  }

  /** The side quest's hook, else the NPC's line; returns the line said, or null. */
  static Component sideQuest(ServerPlayer player, Villager npc, CompoundTag data) {
    String id = data.getString("key");
    SideQuest quest = SIDE_QUESTS.get(id);
    if (quest != null && quest.interact(player, npc, id)) return null;
    Component line = says(npc.getDisplayName(),
        Component.translatable("entrelumen.solsticio.innkeeper." + data.getInt("persona") + ".line"));
    player.sendSystemMessage(line);
    return line;
  }

  /** An ambient line; after the liberation and after the elections the city talks about other things. */
  static Component townsfolkLine(RandomSource random, SolsticioStoryRules.World world) {
    return says(Component.translatable("entrelumen.solsticio.townsfolk"),
        Component.translatable("entrelumen.solsticio.townsfolk." + SolsticioStoryRules.townsfolkPool(world)
            + random.nextInt(CommerceRules.TOWNSFOLK_LINES)));
  }

  static Component says(Component speaker, Component line) {
    return Component.translatable("entrelumen.solsticio.says", speaker, line);
  }

  private static void sayAround(ServerLevel level, Entity source, Component message) {
    for (ServerPlayer player : level.players())
      if (player.distanceToSqr(source) <= SAY_RANGE * SAY_RANGE) player.sendSystemMessage(message);
  }

  // ---- prices (called by VillagerTradingMixin) ------------------------------------------

  /**
   * When a player starts trading, after vanilla's reputation and hero discounts: applies the
   * Solsticio multiplier and closes, for this player only, the offers of acts they have not
   * reached and a sleeping native's Luminosity (shown out of stock until trading stops).
   */
  public static void onTradeOpen(Villager villager, Player player) {
    if (!(villager.level() instanceof ServerLevel level)) return;
    CompoundTag data = data(villager);
    CommerceRules.Role role = data == null ? null : CommerceRules.Role.byId(data.getString("role"));
    if (role == null) return;
    boolean awakened = data.getBoolean("awakened");
    double multiplier = CommerceRules.multiplier(role, SolsticioData.get(level.getServer()).liberated, awakened, prices());
    MerchantOffers offers = villager.getOffers();
    for (MerchantOffer offer : offers) {
      ItemStack base = offer.getBaseCostA();
      int adjustment = CommerceRules.priceAdjustment(base.getCount(), offer.getDemand(), offer.getPriceMultiplier(),
          offer.getSpecialPriceDiff(), base.getMaxStackSize(), multiplier);
      if (adjustment != 0) offer.addToSpecialPriceDiff(adjustment);
    }
    if (role != CommerceRules.Role.SHOP && role != CommerceRules.Role.NATIVE) return;
    ListTag locks = data.getList("locks", Tag.TAG_COMPOUND);
    if (locks.size() != offers.size()) return;
    ProtectionRules.Actor actor = StructureProtection.actor(player);
    List<Locked> locked = new ArrayList<>();
    boolean gated = false;
    for (int i = 0; i < offers.size(); i++) {
      CompoundTag lock = locks.getCompound(i);
      var gate = new ProtectionRules.ActGate(Math.max(1, lock.getInt("act")), lock.getString("milestone"));
      boolean asleep = lock.getBoolean("luminosity") && !awakened;
      boolean early = !gate.satisfiedBy(actor);
      MerchantOffer offer = offers.get(i);
      if ((asleep || early) && !offer.isOutOfStock()) {
        locked.add(new Locked(offer, offer.getUses()));
        offer.setToOutOfStock();
        gated |= early;
      }
    }
    if (locked.isEmpty()) return;
    LOCKS.put(villager, locked);
    if (gated && player instanceof ServerPlayer serverPlayer)
      serverPlayer.displayClientMessage(Component.translatable("entrelumen.solsticio.shop.later"), true);
  }

  /** When trading stops: closed offers get their real uses back. */
  public static void onTradeClose(Villager villager) {
    List<Locked> locked = LOCKS.remove(villager);
    if (locked == null) return;
    for (Locked entry : locked) {
      entry.offer().resetUses();
      for (int i = 0; i < entry.uses(); i++) entry.offer().increaseUses();
    }
  }

  // ---- lifecycle events -----------------------------------------------------------------

  static void onJoin(EntityJoinLevelEvent event) {
    if (!(event.getEntity() instanceof Villager villager) || !(event.getLevel() instanceof ServerLevel level)) return;
    CompoundTag data = data(villager);
    if (data == null) {
      // Placed straight into the hall (for example from an Easy Villagers item): settles now.
      if (!event.loadedFromDisk() && level.dimension().equals(Solsticio.LEVEL)) {
        BlockPos hall = SolsticioData.get(level.getServer()).commerce.hall;
        if (hall != null && inHall(villager, hall, hallRadius())) settle(level, villager);
      }
      return;
    }
    CommerceRules.Role role = CommerceRules.Role.byId(data.getString("role"));
    if (role == null) return;
    if (role.bound() && CITY.equals(data.getString("registry")) && stale(level.getServer(), villager, data, role)) {
      // A copy carried away from its post: the post already has its villager again.
      LOGGER.info("A copy of Solsticio's {} {} was placed at {}; it fades", role.id, data.getString("key"),
          villager.blockPosition());
      event.setCanceled(true);
      return;
    }
    if (role == CommerceRules.Role.TOWNSFOLK && !villager.isNoAi() && data.contains("site", Tag.TAG_LONG))
      villager.restrictTo(BlockPos.of(data.getLong("site")), CommerceRules.TOWNSFOLK_RANGE);
    SolsticioStory.dress(level, villager, data, role);
    if (role == CommerceRules.Role.NATIVE && !event.loadedFromDisk())
      awaken(level, villager, data, natives.get(data.getString("key")), null);
  }

  private static boolean stale(MinecraftServer server, Villager villager, CompoundTag data, CommerceRules.Role role) {
    if (!data.contains("site", Tag.TAG_LONG)) return false;
    CommerceSites.Site site = SolsticioData.get(server).commerce.find(role, data.getString("key"),
        BlockPos.of(data.getLong("site")));
    return site != null && !villager.getUUID().equals(site.entity);
  }

  static void onDeath(LivingDeathEvent event) {
    if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level)
      vacate(level.getServer(), villager, false);
  }

  static void onLeave(EntityLeaveLevelEvent event) {
    if (event.getEntity() instanceof Villager villager && event.getLevel() instanceof ServerLevel level
        && villager.getRemovalReason() == Entity.RemovalReason.DISCARDED)
      vacate(level.getServer(), villager, true);
  }

  /** Lightning and similar never turn a Solsticio villager into something else. */
  static void onConvert(LivingConversionEvent.Pre event) {
    CommerceRules.Role role = role(event.getEntity());
    if (role != null && role != CommerceRules.Role.MOVED) event.setCanceled(true);
  }

  // ---- liberation and commands ----------------------------------------------------------

  /**
   * The first team to open the portal liberates the Entrelumen (act VI, mission 10): every Solsticio
   * merchant gets cheaper, and the elections are held three days of game time later.
   */
  public static void setLiberated(MinecraftServer server, boolean liberated) {
    SolsticioData data = SolsticioData.get(server);
    if (data.liberated == liberated) return;
    data.liberated = liberated;
    data.liberatedAt = liberated ? Math.max(1, server.overworld().getGameTime()) : 0;
    data.setDirty();
  }

  private static void commands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("solsticio")
                .then(Commands.literal("liberated")
                    .executes(ctx -> {
                      boolean on = SolsticioData.get(ctx.getSource().getServer()).liberated;
                      ctx.getSource().sendSuccess(() -> Component.literal("Entrelumen liberated: " + on), false);
                      return on ? 1 : 0;
                    })
                    .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                      boolean on = BoolArgumentType.getBool(ctx, "value");
                      setLiberated(ctx.getSource().getServer(), on);
                      ctx.getSource().sendSuccess(() -> Component.literal("Entrelumen liberated: " + on
                          + " (price multiplier " + prices().liberated() + ")"), true);
                      return 1;
                    })))
                .then(Commands.literal("commerce")
                    .executes(ctx -> summary(ctx.getSource()))
                    .then(Commands.literal("populate").executes(ctx -> {
                      startPopulation(ctx.getSource().getServer());
                      ctx.getSource().sendSuccess(() -> Component.literal("Solsticio commerce population started"), true);
                      return 1;
                    }))
                    .then(Commands.literal("list").executes(ctx -> {
                      var sites = SolsticioData.get(ctx.getSource().getServer()).commerce.sites;
                      for (int i = 0; i < sites.size(); i++) {
                        String line = i + ": " + sites.get(i) + (sites.get(i).entity == null ? "" : " " + sites.get(i).entity);
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                      }
                      return sites.size();
                    }))
                    .then(Commands.literal("respawn").then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes(ctx -> respawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))))))));
  }

  /**
   * For operators: forgets a site's villager (for example a native whose carrying item was lost)
   * so the population spawns a new one. The old villager, if it still exists, keeps existing.
   */
  private static int respawn(CommandSourceStack source, int index) {
    SolsticioData data = SolsticioData.get(source.getServer());
    if (index >= data.commerce.sites.size()) {
      source.sendFailure(Component.literal("No such site; see /entrelumen admin solsticio commerce list"));
      return 0;
    }
    CommerceSites.Site site = data.commerce.sites.get(index);
    site.entity = null;
    data.setDirty();
    startPopulation(source.getServer());
    source.sendSuccess(() -> Component.literal("Respawning " + site), true);
    return 1;
  }

  private static int summary(CommandSourceStack source) {
    SolsticioData data = SolsticioData.get(source.getServer());
    Map<CommerceRules.Role, int[]> counts = new TreeMap<>();
    for (CommerceSites.Site site : data.commerce.chosen(residentCap()))
      counts.computeIfAbsent(site.role, role -> new int[2])[site.entity == null ? 1 : 0]++;
    List<String> lines = new ArrayList<>();
    lines.add("liberated=" + data.liberated + " hall=" + data.commerce.hall + " populating=" + isPopulating(source.getServer())
        + " residentCap=" + residentCap() + " tables: shops=" + shops.keySet() + " natives=" + natives.keySet());
    counts.forEach((role, count) -> lines.add(role.id + ": " + count[0] + " spawned, " + count[1] + " pending"));
    lines.add("resident homes: " + data.commerce.homes().size() + " (cap " + residentCap() + ")");
    lines.add("easter eggs: " + data.commerce.easterEggs);
    lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
    return 1;
  }
}
