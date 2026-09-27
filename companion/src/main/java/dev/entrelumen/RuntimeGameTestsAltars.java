package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Isolated GameTests of the Altar of Renewal (vegetation) and the Altar of Levelling (free terrain);
 * excluded from the distributable jar by the RuntimeGameTests* pattern. Only vanilla and this mod are
 * loaded here, so the dye garden has its four vanilla species; the full-pack suite checks all
 * sixteen dyes with the pinned mods. The repair setting uses a flat generator whose layers end
 * exactly on the template floor, so the real sandbox, trust check and refill run against a known
 * original. Claims are simulated by cancelling the native events, as in the Nature tests.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsAltars {
  private static final Logger LOGGER = LogUtils.getLogger();
  /**
   * The noise test's own worker. The GameTest server ticks without pausing, so tick limits are
   * short in wall time: its three heavy regenerations must not queue ahead of the altars' own
   * references on the shared altar worker, or the concurrent repair tests time out.
   */
  private static final ExecutorService NOISE_WORKER = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "Entrelumen altar QA noise reference");
    thread.setDaemon(true);
    return thread;
  });
  private static final int EXTENT = 40;
  /** Vegetation square in these tests: 25 blocks, six garden orbits, every canopy inside the template. */
  private static final int GARDEN = 12;
  private static final int RADIUS = 10;
  private static final BlockPos ALTAR = new BlockPos(20, 1, 20);

  private RuntimeGameTestsAltars() {}

  // ---- Fixtures ------------------------------------------------------------------------------

  private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
    return helper.absolutePos(new BlockPos(x, y, z));
  }

  /** Grass at relative y 0 over dirt, the same profile as the flat reference below. */
  private static void meadow(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -2, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
  }

  private static void biome(GameTestHelper helper, ResourceKey<Biome> biome, int fromX, int fromZ, int toX, int toZ) {
    var level = helper.getLevel();
    var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
    var filled = net.minecraft.server.commands.FillBiomeCommand.fill(level, at(helper, fromX, -4, fromZ),
        at(helper, toX, 13, toZ), holder);
    helper.assertTrue(filled.left().isPresent(), "Biome fixture " + biome.location() + " was refused");
  }

  /** A flat generator whose grass top is the template floor: the "original" land of the test. */
  static TerrainReference.Setup flatSetup(GameTestHelper helper, ResourceKey<Biome> biome, Block buried) {
    ServerLevel level = helper.getLevel();
    int ground = at(helper, 0, 0, 0).getY();
    int min = level.getMinBuildHeight();
    var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
    var settings = new FlatLevelGeneratorSettings(Optional.empty(), biomes.getOrThrow(biome), List.of());
    settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
    int stone = ground - 2 - (min + 1) - (buried == null ? 0 : 1);
    helper.assertTrue(stone >= 0, "Template floor is too low for the flat reference");
    if (stone > 0) settings.getLayersInfo().add(new FlatLayerInfo(stone, Blocks.STONE));
    if (buried != null) settings.getLayersInfo().add(new FlatLayerInfo(1, buried));
    settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
    settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
    settings.updateLayers();
    return new TerrainReference.Setup(new FlatLevelSource(settings), level.getChunkSource().randomState(),
        level.getSeed(), level.registryAccess(), level.enabledFeatures(), level.getMinBuildHeight(),
        level.getHeight(), level.dimensionType(), Map.of(), Map.of(), Set.of());
  }

  private static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what,
      Runnable then) {
    if (done.getAsBoolean()) {
      then.run();
      return;
    }
    helper.assertTrue(waited < limit, what + " did not finish in time");
    // The GameTest server ticks as fast as it can, so a tick budget alone gives an async worker only a
    // few seconds on a slow CI runner. Each waiting tick lasts at least 20 ms: the limit is then also a
    // wall-clock floor, paid only while work is still running.
    java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
    helper.runAfterDelay(1, () -> await(helper, done, waited + 1, limit, what, then));
  }

  private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper, int low, int high) {
    Map<BlockPos, BlockState> states = new HashMap<>();
    for (int x = 0; x < EXTENT; x++)
      for (int y = low; y <= high; y++)
        for (int z = 0; z < EXTENT; z++) {
          BlockPos pos = at(helper, x, y, z);
          states.put(pos, helper.getLevel().getBlockState(pos));
        }
    return states;
  }

  private static Map<BlockPos, BlockState> changes(GameTestHelper helper, Map<BlockPos, BlockState> before) {
    Map<BlockPos, BlockState> changed = new HashMap<>();
    before.forEach((pos, state) -> {
      BlockState now = helper.getLevel().getBlockState(pos);
      if (!now.equals(state)) changed.put(pos, now);
    });
    return changed;
  }

  private static boolean noDrops(GameTestHelper helper) {
    var area = new AABB(at(helper, 0, -6, 0)).expandTowards(EXTENT, 50, EXTENT);
    return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty();
  }

  private static BlockHitResult hit(BlockPos pos) {
    return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
  }

  /** A logged-in mock player standing next to a block, as in the Ark service tests. */
  private static final class Visitor implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Visitor(GameTestHelper helper, BlockPos near) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), "AltarQA"), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      try {
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
        player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 1.8);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    void use(BlockPos pos, ItemStack held, boolean crouching) {
      player.setItemInHand(InteractionHand.MAIN_HAND, held);
      player.setShiftKeyDown(crouching);
      player.gameMode.useItemOn(player, player.serverLevel(), held, InteractionHand.MAIN_HAND, hit(pos));
      player.setShiftKeyDown(false);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(net.minecraft.network.chat.Component.literal("Altar QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static <E extends net.neoforged.bus.api.Event> Consumer<E> veto(Class<E> type, Consumer<E> body) {
    Consumer<E> listener = body;
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
    return listener;
  }

  private static long percentile(long[] samples, long count, double share) {
    int size = (int) Math.min(count, samples.length);
    if (size == 0) return 0;
    long[] sorted = java.util.Arrays.copyOf(samples, size);
    java.util.Arrays.sort(sorted);
    return sorted[Math.min(size - 1, Math.max(0, (int) Math.ceil(share * size) - 1))];
  }

  private static void perf(String scenario, RenewalAltarEntity altar) {
    long mean = altar.workingTicks == 0 ? 0 : altar.tickNanos / altar.workingTicks;
    var totals = altar.totals();
    LOGGER.info("ALTAR_PERF scenario={} workingTicks={} wallWithoutGc=[mean={} p50={} p95={} max={}]us gcTicks={} maxGcMillis={} actorMillis={} maxTreeMicros={} restedTicks={} maxSoilCheckMicros={} treeMicros={} paletteMicros={} totals=[trees={} inkCaps={} plants={} dyeFlowers={} guarded={} fertilizer={} charge={}] grown={} guards={}",
        scenario, altar.workingTicks, mean / 1000, percentile(altar.tickSamples, altar.workingTicks, 0.5) / 1000,
        percentile(altar.tickSamples, altar.workingTicks, 0.95) / 1000, altar.maxTickNanos / 1000,
        altar.gcTicks, altar.maxGcMillis, altar.actorNanos / 1_000_000, altar.maxTreeNanos / 1000,
        altar.restedTicks, altar.maxSoilCheckNanos / 1000, altar.treeMicros(), AltarVegetation.resolveMicros(), totals.trees,
        totals.inkCaps, totals.plants, totals.dyeFlowers, totals.guarded, totals.fertilizer, totals.charge,
        altar.grown, altar.guards);
  }

  private static void perf(String scenario, TerraformAltarEntity altar) {
    long mean = altar.workingTicks == 0 ? 0 : altar.tickNanos / altar.workingTicks;
    LOGGER.info("ALTAR_PERF scenario={} workingTicks={} wallWithoutGc=[mean={} p50={} p95={} max={}]us gcTicks={} maxGcMillis={} actorMillis={} maxTrustSliceMicros={} totals=[columns={} cut={} filled={} swapped={} covered={} refused={} skipped={}] refusals={}",
        scenario, altar.workingTicks, mean / 1000, percentile(altar.tickSamples, altar.workingTicks, 0.5) / 1000,
        percentile(altar.tickSamples, altar.workingTicks, 0.95) / 1000, altar.maxTickNanos / 1000,
        altar.gcTicks, altar.maxGcMillis, altar.actorNanos / 1_000_000, altar.maxTrustNanos / 1000, altar.totals().columns,
        altar.totals().cut, altar.totals().filled, altar.totals().swapped, altar.totals().covered,
        altar.totals().refused, altar.totals().skippedChunks, altar.refusals());
    long[] first = java.util.Arrays.copyOf(altar.tickSamples, (int) Math.min(16, altar.workingTicks));
    LOGGER.info("ALTAR_PERF_TICKS scenario={} firstTicksUs={}", scenario,
        java.util.Arrays.toString(java.util.Arrays.stream(first).map(nanos -> nanos / 1000).toArray()));
  }

  // ---- Renewal: vegetation -------------------------------------------------------------------

  private static RenewalAltarEntity renewal(GameTestHelper helper, int radius) {
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ALTAR);
    level.setBlockAndUpdate(pos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
    var altar = (RenewalAltarEntity) level.getBlockEntity(pos);
    altar.configureRadius(radius);
    return altar;
  }

  /** Feeds more bone meal whenever the altar runs dry, until it finishes. */
  private static BooleanSupplier fedUntilDone(RenewalAltarEntity altar, int[] given) {
    return () -> {
      if (altar.state() == RenewalAltarEntity.State.WAITING && altar.fertilizer().isEmpty()) {
        altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
        given[0] += 64;
      }
      return altar.state() == RenewalAltarEntity.State.DONE;
    };
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 3200, skyAccess = true)
  public static void renewalAltarGrowsTheForestAndASymmetricDyeGardenSparingBuilds(GameTestHelper helper) {
    meadow(helper);
    biome(helper, Biomes.FOREST, 0, 0, EXTENT - 1, EXTENT - 1);
    var level = helper.getLevel();
    BlockPos altarPos = helper.absolutePos(ALTAR);
    int cx = altarPos.getX(), cz = altarPos.getZ(), ground = at(helper, 0, 0, 0).getY();
    // Builds, a crop, a decoration and claimed land, all inside the square.
    BlockPos chestPos = at(helper, 26, 1, 27);
    level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
    ((ChestBlockEntity) level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 7));
    List<BlockPos> planks = new ArrayList<>();
    for (int x = 12; x <= 15; x++) planks.add(at(helper, x, 1, 29));
    for (BlockPos pos : planks) level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
    BlockPos farmland = at(helper, 28, 0, 13);
    level.setBlockAndUpdate(farmland, Blocks.FARMLAND.defaultBlockState());
    level.setBlockAndUpdate(farmland.above(), Blocks.WHEAT.defaultBlockState());
    var stand = EntityType.ARMOR_STAND.create(level);
    BlockPos standPos = at(helper, 13, 1, 13);
    stand.moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);
    level.addFreshEntity(stand);
    int claimMinX = cx + 5, claimMaxX = cx + GARDEN, claimMinZ = cz - GARDEN, claimMaxZ = cz - 5;
    Consumer<BlockEvent.EntityPlaceEvent> claim = veto(BlockEvent.EntityPlaceEvent.class, event -> {
      BlockPos pos = event.getPos();
      if (pos.getX() >= claimMinX && pos.getX() <= claimMaxX && pos.getZ() >= claimMinZ && pos.getZ() <= claimMaxZ
          && pos.getY() > ground && pos.getY() < ground + 40) event.setCanceled(true);
    });
    var before = snapshot(helper, -2, 36);
    var altar = renewal(helper, GARDEN);
    try (var visitor = new Visitor(helper, altarPos)) {
      visitor.use(altarPos, new ItemStack(Items.BONE_MEAL, 64), false);
      helper.assertTrue(visitor.player.getMainHandItem().isEmpty() && altar.state() == RenewalAltarEntity.State.RUNNING,
          "Bone meal did not feed and start the altar");
    }
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, altarPos, Direction.UP);
    helper.assertTrue(handler != null && handler.extractItem(0, 64, false).isEmpty()
        && !handler.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty(),
        "The altar accepted a non-fertilizer or gave fertilizer back");
    int[] given = {64};
    await(helper, fedUntilDone(altar, given), 0, 3000, "Forest vegetation pass", () -> {
      perf("renewal-forest", altar);
      var totals = altar.totals();
      helper.assertTrue(totals.trees > 0 && totals.plants > 0 && totals.dyeFlowers > 0,
          "No trees, plants or dye flowers grew: " + totals.trees + " " + totals.plants + " " + totals.dyeFlowers);
      helper.assertTrue(altar.grown.keySet().stream().anyMatch(id -> id.equals("minecraft:oak")
          || id.equals("minecraft:birch") || id.equals("minecraft:fancy_oak")),
          "The forest grew no forest tree: " + altar.grown);
      var changed = changes(helper, before);
      changed.remove(altarPos);
      for (var entry : changed.entrySet()) {
        BlockPos pos = entry.getKey();
        BlockState was = before.get(pos);
        helper.assertTrue(pos.getY() > ground && (was.isAir() || was.canBeReplaced()),
            "Vegetation changed terrain or a block that was there: " + pos + " " + was + " -> " + entry.getValue());
        helper.assertTrue(!(pos.getX() >= claimMinX && pos.getX() <= claimMaxX && pos.getZ() >= claimMinZ
            && pos.getZ() <= claimMaxZ), "A claimed block was planted: " + pos);
        helper.assertTrue(!pos.equals(standPos), "A plant was placed into the armor stand");
        for (Direction side : Direction.values()) {
          BlockPos next = pos.relative(side);
          if (changed.containsKey(next)) continue;
          BlockState neighbour = level.getBlockState(next);
          helper.assertTrue(LandWorks.natural(neighbour),
              "A plant touches a build: " + pos + " beside " + neighbour);
        }
      }
      helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST)
          && ItemStack.matches(((ChestBlockEntity) level.getBlockEntity(chestPos)).getItem(0), new ItemStack(Items.WHEAT_SEEDS, 7))
          && planks.stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.OAK_PLANKS))
          && level.getBlockState(farmland).is(Blocks.FARMLAND) && level.getBlockState(farmland.above()).is(Blocks.WHEAT)
          && stand.isAlive(), "A build, container, crop or decoration was changed");
      // The garden: every bed that grew holds its orbit's species, and every orbit is one colour.
      var garden = AltarVegetation.garden(level.registryAccess()).species();
      helper.assertTrue(garden.size() == 4 && garden.get(0).is(Blocks.LILY_OF_THE_VALLEY)
          && AltarVegetation.garden(level.registryAccess()).inkCap() == null,
          "Isolated tests load only the four vanilla garden species: " + garden);
      Map<String, Set<Block>> orbits = new TreeMap<>();
      int beds = 0, planted = 0;
      Set<Block> species = new java.util.HashSet<>();
      for (int dx = -GARDEN; dx <= GARDEN; dx++)
        for (int dz = -GARDEN; dz <= GARDEN; dz++) {
          if (!VegetationRules.bed(dx, dz)) continue;
          beds++;
          BlockState at = level.getBlockState(new BlockPos(cx + dx, ground + 1, cz + dz));
          if (!garden.contains(at.getBlock().defaultBlockState())) continue;
          planted++;
          species.add(at.getBlock());
          BlockState expected = garden.get(VegetationRules.bedSpecies(dx, dz, garden.size()));
          helper.assertTrue(at.is(expected.getBlock()), "Bed " + dx + "," + dz + " holds " + at + " not " + expected);
          orbits.computeIfAbsent(Math.max(Math.abs(dx), Math.abs(dz)) + ":" + Math.min(Math.abs(dx), Math.abs(dz)),
              key -> new java.util.HashSet<>()).add(at.getBlock());
        }
      for (var orbit : orbits.entrySet())
        helper.assertTrue(orbit.getValue().size() == 1, "Orbit " + orbit.getKey() + " is not one colour: " + orbit.getValue());
      helper.assertTrue(species.size() == garden.size() && planted * 10 >= beds * 7,
          "The dye garden is incomplete: " + planted + " of " + beds + " beds, species " + species);
      helper.assertTrue(totals.charge >= totals.plants + totals.trees + totals.inkCaps
          && totals.charge <= totals.plants + AltarRules.TREE_CHARGE * (totals.trees + totals.inkCaps)
          && given[0] - altar.fertilizer().getCount() == totals.fertilizer
          && totals.fertilizer * AltarRules.CHARGE_PER_FERTILIZER - totals.charge == altar.charge(),
          "Payment did not match the work applied: " + totals.charge + " " + totals.fertilizer);
      helper.assertTrue(noDrops(helper), "The altar dropped items");
      // A repeated pass grows nothing new; a harvest grows back and costs exactly what regrows.
      var settled = snapshot(helper, -2, 36);
      altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 8));
      given[0] += 8;
      try (var visitor = new Visitor(helper, altarPos)) {
        visitor.use(altarPos, ItemStack.EMPTY, true);
      }
      helper.assertTrue(altar.state() == RenewalAltarEntity.State.RUNNING, "Crouched use did not start a new pass");
      await(helper, fedUntilDone(altar, given), 0, 1200, "Repeated vegetation pass", () -> {
        // Leaves settle their distance and grass under a trunk turns to dirt by themselves; the altar
        // itself adds nothing: no block appears where there was air, and nothing is charged.
        var repeated = new HashMap<BlockPos, BlockState>();
        changes(helper, settled).forEach((pos, state) -> {
          if (settled.get(pos).isAir()) repeated.put(pos, state);
        });
        helper.assertTrue(repeated.isEmpty() && altar.totals().charge == 0 && altar.totals().plants == 0
            && altar.totals().trees == 0, "A repeated pass grew or charged something: " + repeated + " "
            + altar.totals().charge);
        List<BlockPos> harvested = new ArrayList<>();
        for (int dx = -GARDEN; dx <= GARDEN && harvested.size() < 3; dx++)
          for (int dz = -GARDEN; dz <= GARDEN && harvested.size() < 3; dz++) {
            BlockPos pos = new BlockPos(cx + dx, ground + 1, cz + dz);
            if (VegetationRules.bed(dx, dz) && garden.contains(level.getBlockState(pos).getBlock().defaultBlockState()))
              harvested.add(pos);
          }
        Map<BlockPos, BlockState> picked = new HashMap<>();
        for (BlockPos pos : harvested) {
          picked.put(pos, level.getBlockState(pos));
          level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 8));
        given[0] += 8;
        altar.crouchUse(helperPlayer(helper, altar));
        await(helper, fedUntilDone(altar, given), 0, 1200, "Regrowth pass", () -> {
          NeoForge.EVENT_BUS.unregister(claim);
          picked.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state),
              "A harvested bed did not grow back the same flower at " + pos));
          helper.assertTrue(altar.totals().charge == harvested.size() && altar.totals().plants == harvested.size(),
              "Regrowth cost " + altar.totals().charge + " for " + harvested.size() + " flowers");
          helper.succeed();
        });
      });
    });
  }

  /** A logged-out stand-in for the owner's gesture in later passes. */
  private static ServerPlayer helperPlayer(GameTestHelper helper, RenewalAltarEntity altar) {
    return net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 2400, skyAccess = true)
  public static void renewalAltarGrowsGiantCactiAndLaysGardenSoilInTheDesert(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -2, z), Blocks.SANDSTONE.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -1, z), Blocks.SAND.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 0, z), Blocks.SAND.defaultBlockState(), 2);
      }
    biome(helper, Biomes.DESERT, 0, 0, EXTENT - 1, EXTENT - 1);
    BlockPos altarPos = helper.absolutePos(ALTAR);
    int cx = altarPos.getX(), cz = altarPos.getZ(), ground = at(helper, 0, 0, 0).getY();
    // One patch of grass laid by a player around the bed at (6, 2): that bed needs no soil of its own.
    for (int dx = 5; dx <= 7; dx++)
      for (int dz = 1; dz <= 3; dz++) level.setBlock(new BlockPos(cx + dx, ground, cz + dz),
          Blocks.GRASS_BLOCK.defaultBlockState(), 2);
    var before = snapshot(helper, -2, 20);
    var altar = renewal(helper, GARDEN);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    var palette = AltarVegetation.palette(level, level.getBiome(altarPos));
    helper.assertTrue(palette.variant().equals("desert") && palette.trees().stream().allMatch(AltarVegetation.TreeOption::giantCactus),
        "The desert palette is not giant cacti: " + palette);
    // A giant cactus grown directly, through the altar's own checks, at a sand column far from the rest.
    BlockPos sand = new BlockPos(cx - 8, ground, cz + 9);
    String result = altar.growForTest(level, sand, palette.trees().getFirst());
    helper.assertTrue(result.equals("APPLIED"), "The giant cactus did not grow: " + result + " " + altar.guards);
    var shape = VegetationRules.giantCactus(VegetationRules.treeSeed(level.getSeed(), sand.getX(), sand.getZ()), 0, 0);
    for (var column : shape)
      for (int h = 1; h <= column.height(); h++)
        helper.assertTrue(level.getBlockState(sand.offset(column.dx(), h, column.dz())).is(Blocks.CACTUS),
            "Giant cactus column " + column + " is incomplete at height " + h);
    int[] given = {64};
    await(helper, fedUntilDone(altar, given), 0, 2000, "Desert vegetation pass", () -> {
      perf("renewal-desert", altar);
      int cacti = 0, bushes = 0, flowers = 0;
      var garden = AltarVegetation.garden(level.registryAccess()).species();
      for (int x = 0; x < EXTENT; x++)
        for (int z = 0; z < EXTENT; z++)
          for (int y = 1; y <= 10; y++) {
            BlockState state = level.getBlockState(at(helper, x, y, z));
            if (state.is(Blocks.CACTUS)) cacti++;
            if (state.is(Blocks.DEAD_BUSH)) bushes++;
            if (garden.contains(state.getBlock().defaultBlockState())) {
              flowers++;
              BlockPos flower = at(helper, x, y, z);
              // Beds lay their own grass; other flowers only grow on grass that was already there.
              helper.assertTrue(level.getBlockState(flower.below()).is(Blocks.GRASS_BLOCK)
                  && (VegetationRules.bed(flower.getX() - cx, flower.getZ() - cz)
                      || before.get(flower.below()).is(Blocks.GRASS_BLOCK)),
                  "A garden flower grew off its bed on sand at " + flower);
            }
          }
      helper.assertTrue(bushes > 0, "The desert grew no dead bushes");
      helper.assertTrue(level.getBlockState(new BlockPos(cx + 6, ground + 1, cz + 2))
          .is(garden.get(VegetationRules.bedSpecies(6, 2, garden.size())).getBlock()),
          "The bed on the player's grass did not flower");
      int onSand = 0;
      for (int dx = -GARDEN; dx <= GARDEN; dx++)
        for (int dz = -GARDEN; dz <= GARDEN; dz++) {
          if (!VegetationRules.bed(dx, dz)) continue;
          BlockPos soil = new BlockPos(cx + dx, ground, cz + dz);
          BlockState above = level.getBlockState(soil.above());
          if (above.is(Blocks.CACTUS)) continue;
          helper.assertTrue(above.is(garden.get(VegetationRules.bedSpecies(dx, dz, garden.size())).getBlock())
              && level.getBlockState(soil).is(Blocks.GRASS_BLOCK), "Bed " + dx + "," + dz + " is empty on the desert");
          if (before.get(soil).is(Blocks.SAND)) onSand++;
        }
      helper.assertTrue(onSand > 0 && altar.totals().soil == onSand,
          "Soil laid " + altar.totals().soil + " for " + onSand + " beds on sand");
      int grown = cacti;
      // Cacti must survive their own neighbour updates: nothing breaks after a while.
      helper.runAfterDelay(40, () -> {
        int still = 0;
        for (int x = 0; x < EXTENT; x++)
          for (int z = 0; z < EXTENT; z++)
            for (int y = 1; y <= 10; y++) if (level.getBlockState(at(helper, x, y, z)).is(Blocks.CACTUS)) still++;
        helper.assertTrue(still == grown && noDrops(helper), "Cactus broke after growing: " + grown + " -> " + still);
        for (var entry : changes(helper, before).entrySet())
          helper.assertTrue(entry.getKey().getY() > ground || entry.getKey().getY() == ground
              && VegetationRules.bed(entry.getKey().getX() - cx, entry.getKey().getZ() - cz)
              && entry.getValue().is(Blocks.GRASS_BLOCK), "Terrain changed outside a garden bed at " + entry.getKey());
        helper.succeed();
      });
    });
  }

  // ---- Renewal: the dye garden lays its own soil ---------------------------------------------

  /** Four quadrants of bare plain ground: sand, stone, end stone and netherrack, over stone. */
  private static Block bareGround(int x, int z) {
    return x < 20 ? (z < 20 ? Blocks.SAND : Blocks.END_STONE) : (z < 20 ? Blocks.STONE : Blocks.NETHERRACK);
  }

  private static void bareQuadrants(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -2, z), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -1, z), bareGround(x, z).defaultBlockState(), 2);
        level.setBlock(at(helper, x, 0, z), bareGround(x, z).defaultBlockState(), 2);
      }
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 2400, skyAccess = true)
  public static void renewalAltarLaysGardenSoilOnBareGroundButNeverOnProtectedCells(GameTestHelper helper) {
    bareQuadrants(helper);
    // The void grows no trees and no flora, so every change is the garden's. End stone stands for the End.
    biome(helper, Biomes.THE_VOID, 0, 0, EXTENT - 1, EXTENT - 1);
    var level = helper.getLevel();
    BlockPos altarPos = helper.absolutePos(ALTAR);
    int cx = altarPos.getX(), cz = altarPos.getZ(), ground = at(helper, 0, 0, 0).getY();
    // Protected beds: an ore, a water source, packed ice, a build, a claimed column, an armor stand,
    // a chest beside the bed's ground and a column whose soil may not be broken.
    Map<String, BlockPos> protectedBeds = new java.util.LinkedHashMap<>();
    java.util.function.BiFunction<Integer, Integer, BlockPos> bedGround = (dx, dz) -> new BlockPos(cx + dx, ground, cz + dz);
    protectedBeds.put("ore", bedGround.apply(-10, -10));
    protectedBeds.put("water", bedGround.apply(-6, -10));
    protectedBeds.put("ice", bedGround.apply(-2, -10));
    protectedBeds.put("build", bedGround.apply(2, -10));
    protectedBeds.put("claim", bedGround.apply(6, -10));
    protectedBeds.put("entity", bedGround.apply(10, -10));
    protectedBeds.put("chest", bedGround.apply(10, 10));
    protectedBeds.put("unbreakable", bedGround.apply(-10, 10));
    level.setBlock(protectedBeds.get("ore"), Blocks.IRON_ORE.defaultBlockState(), 2);
    level.setBlock(protectedBeds.get("water"), Blocks.WATER.defaultBlockState(), 2);
    level.setBlock(protectedBeds.get("ice"), Blocks.PACKED_ICE.defaultBlockState(), 2);
    level.setBlock(protectedBeds.get("build"), Blocks.OAK_PLANKS.defaultBlockState(), 2);
    BlockPos chestPos = protectedBeds.get("chest").east();
    level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
    var stand = EntityType.ARMOR_STAND.create(level);
    BlockPos standPos = protectedBeds.get("entity").above();
    stand.moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);
    level.addFreshEntity(stand);
    BlockPos claimed = protectedBeds.get("claim"), unbreakable = protectedBeds.get("unbreakable");
    Consumer<BlockEvent.EntityPlaceEvent> claim = veto(BlockEvent.EntityPlaceEvent.class, event -> {
      if (event.getPos().getX() == claimed.getX() && event.getPos().getZ() == claimed.getZ()) event.setCanceled(true);
    });
    Consumer<BlockEvent.BreakEvent> noBreak = veto(BlockEvent.BreakEvent.class, event -> {
      if (event.getPos().equals(unbreakable)) event.setCanceled(true);
    });
    var before = snapshot(helper, -2, 20);
    var altar = renewal(helper, GARDEN);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    int[] given = {64};
    await(helper, fedUntilDone(altar, given), 0, 2000, "Bare-ground garden pass", () -> {
      perf("renewal-bare-garden", altar);
      var garden = AltarVegetation.garden(level.registryAccess()).species();
      Set<BlockPos> guardedGround = new java.util.HashSet<>(protectedBeds.values());
      int planted = 0, beds = 0;
      Set<Block> quadrants = new java.util.HashSet<>();
      for (int dx = -GARDEN; dx <= GARDEN; dx++)
        for (int dz = -GARDEN; dz <= GARDEN; dz++) {
          if (!VegetationRules.bed(dx, dz)) continue;
          beds++;
          BlockPos soil = new BlockPos(cx + dx, ground, cz + dz);
          BlockState now = level.getBlockState(soil), above = level.getBlockState(soil.above());
          if (guardedGround.contains(soil)) {
            helper.assertTrue(now.equals(before.get(soil)) && above.equals(before.get(soil.above())),
                "A protected bed was planted or its ground changed at " + dx + "," + dz + ": " + now + " / " + above);
            continue;
          }
          BlockState expected = garden.get(VegetationRules.bedSpecies(dx, dz, garden.size()));
          helper.assertTrue(above.is(expected.getBlock()) && now.is(Blocks.GRASS_BLOCK),
              "Bed " + dx + "," + dz + " on " + before.get(soil) + " holds " + above + " over " + now);
          quadrants.add(before.get(soil).getBlock());
          planted++;
        }
      helper.assertTrue(quadrants.containsAll(List.of(Blocks.SAND, Blocks.STONE, Blocks.END_STONE, Blocks.NETHERRACK)),
          "Not every kind of bare ground got a garden: " + quadrants);
      helper.assertTrue(planted == beds - guardedGround.size() && altar.totals().soil == planted,
          "Soil was not laid exactly once per planted bed: planted=" + planted + " soil=" + altar.totals().soil);
      for (var entry : changes(helper, before).entrySet()) {
        BlockPos pos = entry.getKey();
        if (pos.getY() > ground) continue;
        int dx = pos.getX() - cx, dz = pos.getZ() - cz;
        helper.assertTrue(pos.getY() == ground && VegetationRules.bed(dx, dz) && entry.getValue().is(Blocks.GRASS_BLOCK),
            "Ground changed outside a garden bed: " + pos + " -> " + entry.getValue());
      }
      helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST) && stand.isAlive() && noDrops(helper),
          "The chest, the armor stand or the drops were disturbed");
      helper.assertTrue(altar.totals().charge >= altar.totals().plants + altar.totals().soil,
          "Laid soil was not paid for: " + altar.totals().charge);
      // A second pass over the grown garden lays nothing and charges nothing.
      var settled = snapshot(helper, -2, 20);
      altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 8));
      given[0] += 8;
      altar.crouchUse(helperPlayer(helper, altar));
      await(helper, fedUntilDone(altar, given), 0, 1200, "Repeated bare-ground pass", () -> {
        NeoForge.EVENT_BUS.unregister(claim);
        NeoForge.EVENT_BUS.unregister(noBreak);
        var repeated = new HashMap<BlockPos, BlockState>();
        changes(helper, settled).forEach((pos, state) -> {
          if (!settled.get(pos).isAir() || pos.getY() <= ground) repeated.put(pos, state);
        });
        helper.assertTrue(altar.totals().soil == 0 && altar.totals().charge == 0 && repeated.isEmpty(),
            "A repeated pass laid soil or charged: " + altar.totals().soil + " " + altar.totals().charge + " " + repeated);
        helper.succeed();
      });
    });
  }

  /**
   * The Nether has a roof, so the heightmap finds bedrock. The altar plants on the floor nearest its
   * own level instead: a closed room in the real Nether, with a netherrack and soul sand floor.
   */
  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void renewalAltarPlantsItsGardenUnderTheNetherRoof(GameTestHelper helper) {
    ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    helper.assertTrue(nether != null && nether.dimensionType().hasCeiling(), "No roofed Nether in the test server");
    int x0 = 4096 + 8, z0 = 4096 + 8, y0 = 64, room = 16;
    int[] bounds = AltarRules.loadedBounds(x0, z0, GARDEN);
    for (int chunkX = bounds[0] - 1; chunkX <= bounds[2] + 1; chunkX++)
      for (int chunkZ = bounds[1] - 1; chunkZ <= bounds[3] + 1; chunkZ++) nether.setChunkForced(chunkX, chunkZ, true);
    Runnable release = () -> {
      for (int chunkX = bounds[0] - 1; chunkX <= bounds[2] + 1; chunkX++)
        for (int chunkZ = bounds[1] - 1; chunkZ <= bounds[3] + 1; chunkZ++) nether.setChunkForced(chunkX, chunkZ, false);
    };
    await(helper, () -> LandWorks.loaded(nether, bounds[0], bounds[1], bounds[2], bounds[3]), 0, 1500, "Nether chunks", () -> {
      try {
        // A closed room: floor at y0 (soul sand in one quadrant), air up to y0 + 9, a netherrack roof above.
        for (int x = x0 - room; x <= x0 + room; x++)
          for (int z = z0 - room; z <= z0 + room; z++)
            for (int y = y0 - 3; y <= y0 + 14; y++) {
              boolean wall = Math.abs(x - x0) == room || Math.abs(z - z0) == room;
              BlockState state = y <= y0 ? (y == y0 && x > x0 && z > z0 ? Blocks.SOUL_SAND : Blocks.NETHERRACK).defaultBlockState()
                  : y >= y0 + 10 || wall ? Blocks.NETHERRACK.defaultBlockState() : Blocks.AIR.defaultBlockState();
              nether.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
            }
        // A biome without natural spawns, and no creature left inside: a mob standing on a bed rightly
        // keeps it empty until it moves, which would make this count depend on the Nether's crowd.
        var quiet = nether.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        net.minecraft.server.commands.FillBiomeCommand.fill(nether, new BlockPos(x0 - room, y0 - 4, z0 - room),
            new BlockPos(x0 + room, y0 + 16, z0 + room), quiet);
        for (var entity : nether.getEntities((net.minecraft.world.entity.Entity) null,
            new AABB(x0 - room, y0 - 4, z0 - room, x0 + room + 1, y0 + 16, z0 + room + 1),
            entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) entity.discard();
        Map<BlockPos, BlockState> roof = new HashMap<>();
        for (int x = x0 - GARDEN; x <= x0 + GARDEN; x++)
          for (int z = z0 - GARDEN; z <= z0 + GARDEN; z++)
            for (int y = y0 + 4; y <= y0 + 14; y++) {
              BlockPos pos = new BlockPos(x, y, z);
              roof.put(pos, nether.getBlockState(pos));
            }
        BlockPos altarPos = new BlockPos(x0, y0 + 1, z0);
        nether.setBlockAndUpdate(altarPos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
        var altar = (RenewalAltarEntity) nether.getBlockEntity(altarPos);
        altar.configureRadius(GARDEN);
        altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
        helper.assertTrue(altar.surfaceAt(nether, x0 + 6, z0 + 2) != null
            && altar.surfaceAt(nether, x0 + 6, z0 + 2).getY() == y0, "The floor under the roof was not found");
        int[] given = {64};
        await(helper, fedUntilDone(altar, given), 0, 1800, "Nether garden pass", () -> {
          try {
            perf("renewal-nether", altar);
            var garden = AltarVegetation.garden(nether.registryAccess()).species();
            int planted = 0, beds = 0;
            for (int dx = -GARDEN; dx <= GARDEN; dx++)
              for (int dz = -GARDEN; dz <= GARDEN; dz++) {
                if (!VegetationRules.bed(dx, dz)) continue;
                beds++;
                BlockPos soil = new BlockPos(x0 + dx, y0, z0 + dz);
                BlockState expected = garden.get(VegetationRules.bedSpecies(dx, dz, garden.size()));
                if (nether.getBlockState(soil.above()).is(expected.getBlock()) && nether.getBlockState(soil).is(Blocks.GRASS_BLOCK))
                  planted++;
              }
            boolean roofKept = true;
            for (var entry : roof.entrySet()) if (!nether.getBlockState(entry.getKey()).equals(entry.getValue())) roofKept = false;
            LOGGER.info("ALTAR_NETHER beds={} planted={} soil={} plants={} grown={} guards={} roofKept={}", beds, planted,
                altar.totals().soil, altar.totals().plants, altar.grown, altar.guards, roofKept);
            helper.assertTrue(planted == beds && altar.totals().soil == beds,
                "The garden under the Nether roof is incomplete: " + planted + " of " + beds + " soil " + altar.totals().soil);
            helper.assertTrue(roofKept, "Something grew on or changed the roof");
            nether.removeBlock(altarPos, false);
            helper.succeed();
          } finally {
            release.run();
          }
        });
      } catch (RuntimeException | Error failure) {
        release.run();
        throw failure;
      }
    });
  }

  /**
   * The variant fixture, the same on every run: grass over dirt across the whole template, so every
   * neighbour a tree can touch is natural, and the altar buried under it at the centre, out of every
   * canopy's reach. Trees grow with fixed seeds, so their shape does not depend on where the test runs.
   */
  private static RenewalAltarEntity variantAltar(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -3, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -2, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
    // Buried under the ground at the centre, out of every canopy's reach; the tree grows above it.
    BlockPos pos = at(helper, 20, -3, 20);
    level.setBlockAndUpdate(pos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
    var altar = (RenewalAltarEntity) level.getBlockEntity(pos);
    altar.configureRadius(GARDEN);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    return altar;
  }

  /** Every block a variant wrote stays inside the template with a free block around it. */
  private static void assertInside(GameTestHelper helper, Map<BlockPos, BlockState> before) {
    BlockPos corner = at(helper, 0, 0, 0);
    var changed = changes(helper, before);
    long digest = 17;
    for (var entry : new TreeMap<>(changed.entrySet().stream().collect(java.util.stream.Collectors.toMap(
        e -> e.getKey().subtract(corner).asLong(), e -> e.getValue().toString()))).entrySet())
      digest = digest * 31 + entry.getKey() * 7 + entry.getValue().hashCode();
    Map<String, Map<String, Integer>> perPad = new TreeMap<>();
    for (var entry : changed.entrySet()) {
      BlockPos rel = entry.getKey().subtract(corner);
      String pad = (rel.getX() < 20 ? "w" : "e") + (rel.getZ() < 20 ? "n" : "s");
      perPad.computeIfAbsent(pad, key -> new TreeMap<>()).merge(
          BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()).getPath() + "@" + (rel.getY() <= 0 ? "ground" : "air"),
          1, Integer::sum);
    }
    LOGGER.info("ALTAR_VARIANTS_FOOTPRINT blocks={} digest={} perPad={}", changed.size(), Long.toHexString(digest), perPad);
    for (BlockPos pos : changed.keySet()) {
      int x = pos.getX() - corner.getX(), z = pos.getZ() - corner.getZ();
      helper.assertTrue(x >= 2 && x <= EXTENT - 3 && z >= 2 && z <= EXTENT - 3,
          "A variant grew within a block of its fixture's walls at " + x + "," + z);
    }
  }

  /** One variant grown with a fixed seed on a pad of its own soil and biome; returns what the altar reported. */
  private static String growVariant(GameTestHelper helper, RenewalAltarEntity altar, ResourceKey<Biome> biome,
      Block soil, int padX, int padZ, String option, long treeSeed) {
    var level = helper.getLevel();
    biome(helper, biome, padX - 4, padZ - 4, padX + 4, padZ + 4);
    for (int x = padX - 4; x <= padX + 4; x++)
      for (int z = padZ - 4; z <= padZ + 4; z++) {
        level.setBlock(at(helper, x, -1, z), soil.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 0, z), soil.defaultBlockState(), 2);
      }
    BlockPos ground = at(helper, padX, 0, padZ);
    var palette = AltarVegetation.palette(level, level.getBiome(ground.above()));
    var chosen = palette.trees().stream().filter(tree -> tree.id().equals(option)).findFirst();
    helper.assertTrue(chosen.isPresent(), biome.location() + " has no option " + option + ": " + palette.trees());
    return altar.growForTest(level, ground, chosen.get(), treeSeed);
  }

  private static int count(GameTestHelper helper, int padX, int padZ, java.util.function.Predicate<BlockState> test) {
    int found = 0;
    for (int x = padX - 12; x <= padX + 12; x++)
      for (int z = padZ - 12; z <= padZ + 12; z++)
        for (int y = 1; y <= 40; y++) if (test.test(helper.getLevel().getBlockState(at(helper, x, y, z)))) found++;
    return found;
  }

  /**
   * One variant, alone at the centre of its own template: its trunk and crown come from a fixed seed,
   * the whole template is natural ground, and the tree grows at its centre, about ten blocks from
   * the template's barrier walls, so no neighbour a tree can touch belongs to anything else. Vanilla's
   * vine and propagule decorators walk hash sets of positions, so those few blocks vary with where
   * the test runs; they stay inside the margin.
   */
  private static void variant(GameTestHelper helper, ResourceKey<Biome> biome, Block soil, String option,
      long treeSeed, java.util.function.Predicate<BlockState> part, int least, String what) {
    variant(helper, biome, soil, option, treeSeed, part, least, what, () -> {});
  }

  private static void variant(GameTestHelper helper, ResourceKey<Biome> biome, Block soil, String option,
      long treeSeed, java.util.function.Predicate<BlockState> part, int least, String what, Runnable extra) {
    var altar = variantAltar(helper);
    var before = snapshot(helper, -3, 40);
    String result = growVariant(helper, altar, biome, soil, 20, 20, option, treeSeed);
    LOGGER.info("ALTAR_VARIANT {} result={} guards={} treeMicros={}", option, result, altar.guards, altar.treeMicros());
    helper.assertTrue(result.equals("APPLIED"), option + " did not grow: " + result + " " + altar.guards);
    assertInside(helper, before);
    helper.assertTrue(count(helper, 20, 20, part) >= least, what);
    for (int x = 16; x <= 24; x++)
      for (int z = 16; z <= 24; z++)
        helper.assertTrue(LandWorks.ground(helper.getLevel().getBlockState(at(helper, x, 0, z))),
            "A tree changed its soil at " + x + "," + z);
    helper.assertTrue(noDrops(helper), "Growing " + option + " dropped items");
    extra.run();
    helper.succeed();
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsGiantSpruceInOldTaiga(GameTestHelper helper) {
    variant(helper, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Blocks.PODZOL, "minecraft:mega_spruce", 11L,
        state -> state.is(Blocks.SPRUCE_LOG), 20, "The mega spruce is not a giant");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsGiantJungleTreeWithCocoa(GameTestHelper helper) {
    variant(helper, Biomes.JUNGLE, Blocks.GRASS_BLOCK, "minecraft:mega_jungle_tree", 12L,
        state -> state.is(Blocks.COCOA), 1, "The giant jungle tree has no cocoa",
        () -> helper.assertTrue(count(helper, 20, 20, state -> state.is(Blocks.JUNGLE_LOG)) >= 20,
            "The jungle tree is not a giant"));
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsHugeMushroomInMushroomFields(GameTestHelper helper) {
    variant(helper, Biomes.MUSHROOM_FIELDS, Blocks.MYCELIUM, "minecraft:huge_red_mushroom", 13L,
        state -> state.is(Blocks.RED_MUSHROOM_BLOCK), 1, "No huge red mushroom on mycelium");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsCherryInCherryGrove(GameTestHelper helper) {
    variant(helper, Biomes.CHERRY_GROVE, Blocks.GRASS_BLOCK, "minecraft:cherry", 14L,
        state -> state.is(Blocks.CHERRY_LOG), 1, "No cherry tree");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsHugeCrimsonFungusOnNylium(GameTestHelper helper) {
    variant(helper, Biomes.CRIMSON_FOREST, Blocks.CRIMSON_NYLIUM, "minecraft:crimson_fungus_planted", 21L,
        state -> state.is(Blocks.NETHER_WART_BLOCK), 1, "No huge crimson fungus on nylium");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsChorusOnEndStone(GameTestHelper helper) {
    variant(helper, Biomes.END_HIGHLANDS, Blocks.END_STONE, "minecraft:chorus_plant", 22L,
        state -> state.is(Blocks.CHORUS_PLANT), 1, "No chorus on end stone");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsMangroveOnMud(GameTestHelper helper) {
    variant(helper, Biomes.MANGROVE_SWAMP, Blocks.MUD, "minecraft:mangrove", 23L,
        state -> state.is(Blocks.MANGROVE_LOG), 1, "No mangrove on mud");
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 200, skyAccess = true)
  public static void renewalAltarGrowsGiantCactusOnSand(GameTestHelper helper) {
    variant(helper, Biomes.DESERT, Blocks.SAND, "entrelumen:giant_cactus", 24L,
        state -> state.is(Blocks.CACTUS), 7, "No giant cactus on sand");
  }

  /**
   * What a tree costs the server thread: the altar's sandboxed simulation, warmed up, against the
   * same feature placed straight into the level as a growing sapling would. Evidence only.
   */
  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void renewalAltarTreeCostAgainstVanillaGrowth(GameTestHelper helper) {
    var level = helper.getLevel();
    meadow(helper);
    var features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
    var generator = level.getChunkSource().getGenerator();
    BlockPos sandbox = at(helper, 10, 1, 10), direct = at(helper, 29, 1, 29);
    var permitted = new net.minecraft.world.level.levelgen.structure.BoundingBox(sandbox.getX() - 12,
        level.getMinBuildHeight(), sandbox.getZ() - 12, sandbox.getX() + 12, level.getMaxBuildHeight() - 1,
        sandbox.getZ() + 12);
    Map<String, String> costs = new java.util.LinkedHashMap<>();
    for (String id : List.of("oak", "birch", "fancy_oak", "spruce", "acacia", "dark_oak", "cherry", "jungle_tree",
        "mega_spruce", "mega_jungle_tree", "huge_red_mushroom")) {
      var feature = features.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
      helper.assertTrue(feature != null, "No feature " + id);
      long[] simulated = new long[20];
      for (int round = -5; round < simulated.length; round++) {
        long started = System.nanoTime();
        FeatureSandbox.simulate(level, permitted, feature,
            net.minecraft.util.RandomSource.create(round * 7919L), sandbox);
        if (round >= 0) simulated[round] = System.nanoTime() - started;
      }
      long[] placed = new long[5];
      for (int round = -2; round < placed.length; round++) {
        long started = System.nanoTime();
        feature.place(level, generator, net.minecraft.util.RandomSource.create(round * 104729L), direct);
        long spent = System.nanoTime() - started;
        if (round >= 0) placed[round] = spent;
        for (int x = 21; x <= 37; x++)
          for (int z = 21; z <= 37; z++)
            for (int y = 0; y <= 36; y++)
              level.setBlock(at(helper, x, y, z), y == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                  : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      }
      java.util.Arrays.sort(simulated);
      java.util.Arrays.sort(placed);
      costs.put(id, "sandbox p50=" + simulated[10] / 1000 + " p95=" + simulated[18] / 1000 + " max="
          + simulated[19] / 1000 + " vanilla p50=" + placed[2] / 1000 + " max=" + placed[4] / 1000);
    }
    LOGGER.info("ALTAR_TREE_COST micros={}", costs);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void renewalAltarHarvestMakesTheDyesOfItsVanillaGarden(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var recipes = new VegetationRules.Closure(AltarVegetation.recipes(level));
    // Vanilla alone: white, red, yellow and blue from the garden and everything they mix into. Brown,
    // green and black come from the pinned mods' flowers, and gray, light gray, lime and cyan from those;
    // the full-pack suite proves all sixteen.
    Set<VegetationRules.Dye> vanilla = java.util.EnumSet.of(VegetationRules.Dye.WHITE, VegetationRules.Dye.RED,
        VegetationRules.Dye.YELLOW, VegetationRules.Dye.BLUE, VegetationRules.Dye.ORANGE, VegetationRules.Dye.PINK,
        VegetationRules.Dye.MAGENTA, VegetationRules.Dye.LIGHT_BLUE, VegetationRules.Dye.PURPLE);
    Set<VegetationRules.Dye> modded = java.util.EnumSet.of(VegetationRules.Dye.BROWN, VegetationRules.Dye.GREEN,
        VegetationRules.Dye.BLACK, VegetationRules.Dye.GRAY, VegetationRules.Dye.LIGHT_GRAY, VegetationRules.Dye.LIME,
        VegetationRules.Dye.CYAN);
    Map<String, Integer> variants = new TreeMap<>();
    int biomes = 0;
    for (var biome : level.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
      biomes++;
      var palette = AltarVegetation.palette(level, biome);
      variants.merge(palette.variant(), 1, Integer::sum);
      var harvest = AltarVegetation.harvest(level, biome);
      var missing = recipes.missing(harvest);
      for (var dye : vanilla)
        helper.assertTrue(!missing.contains(dye), biome.getRegisteredName() + " cannot make " + dye + " from " + harvest);
      helper.assertTrue(modded.containsAll(missing), biome.getRegisteredName() + " misses " + missing);
      if (biome.is(BiomeTags.IS_JUNGLE))
        helper.assertTrue(!missing.contains(VegetationRules.Dye.BROWN), "Jungle cocoa gives no brown");
      if (biome.is(Biomes.DESERT))
        helper.assertTrue(!missing.contains(VegetationRules.Dye.GREEN), "Desert cactus gives no green");
    }
    LOGGER.info("ALTAR_DYES isolated biomes={} recipes={} variants={}", biomes, recipes.size(), variants);
    helper.assertTrue(biomes > 50 && variants.size() >= 15, "Too few biomes or variants resolved: " + variants);
    helper.succeed();
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void renewalAltarProgressSurvivesSaveAndReloadAndMigratesVersionOne(GameTestHelper helper) {
    meadow(helper);
    biome(helper, Biomes.PLAINS, 0, 0, EXTENT - 1, EXTENT - 1);
    var level = helper.getLevel();
    var altar = renewal(helper, GARDEN);
    BlockPos altarPos = altar.getBlockPos();
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    int[] given = {64};
    await(helper, () -> altar.pass() == 1 && altar.index() > 40 || altar.state() == RenewalAltarEntity.State.DONE,
        0, 1200, "Partial pass", () -> {
      helper.assertTrue(altar.state() == RenewalAltarEntity.State.RUNNING,
          "The pass finished before a reload could interrupt it");
      helper.assertTrue(AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos.offset(GARDEN, 0, -GARDEN))
          && !AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos.offset(GARDEN + 1, 0, 0))
          && !AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos),
          "The active altar's square is not what the registry reports");
      CompoundTag saved = altar.saveWithFullMetadata(level.registryAccess());
      int pass = altar.pass(), index = altar.index(), charge = altar.charge(), plants = altar.totals().plants;
      int stored = altar.fertilizer().getCount();
      level.removeBlockEntity(altarPos);
      BlockEntity loaded = BlockEntity.loadStatic(altarPos, level.getBlockState(altarPos), saved, level.registryAccess());
      helper.assertTrue(loaded instanceof RenewalAltarEntity, "The altar did not load from its saved data");
      level.setBlockEntity(loaded);
      var reloaded = (RenewalAltarEntity) level.getBlockEntity(altarPos);
      helper.assertTrue(reloaded != altar && reloaded.state() == RenewalAltarEntity.State.RUNNING
          && reloaded.pass() == pass && reloaded.index() == index && reloaded.charge() == charge
          && reloaded.totals().plants == plants && reloaded.fertilizer().getCount() == stored
          && reloaded.radius() == GARDEN, "Saved progress, charge or fertilizer did not round-trip");
      // A version 1 altar (terrain reconstruction) resumes as a fresh vegetation pass with its charge.
      CompoundTag old = saved.copy();
      old.putInt("version", 1);
      old.putInt("pass", 2);
      old.putInt("index", 77);
      old.putString("state", "RUNNING");
      old.putString("failure", "");
      CompoundTag oldTotals = new CompoundTag();
      oldTotals.putIntArray("values", new int[] {5, 2, 1, 3, 0, 0, 9, 30});
      old.put("totals", oldTotals);
      var migrated = (RenewalAltarEntity) BlockEntity.loadStatic(altarPos.above(3), level.getBlockState(altarPos),
          old, level.registryAccess());
      helper.assertTrue(migrated != null && migrated.pass() == 0 && migrated.index() == 0
          && migrated.state() == RenewalAltarEntity.State.RUNNING && migrated.charge() == charge
          && migrated.totals().trees == 0, "A version 1 altar did not start a fresh vegetation pass");
      await(helper, fedUntilDone(reloaded, given), 0, 1200, "Reloaded pass", () -> {
        perf("renewal-reload", reloaded);
        helper.assertTrue(reloaded.state() == RenewalAltarEntity.State.DONE
            && !AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos) && reloaded.activeTicks() >= 0,
            "The reloaded altar did not finish or stayed registered: " + reloaded.state());
        helper.succeed();
      });
    });
  }

  // ---- Renewal reference: overworld noise sandbox (used by the Levelling repair) ------------

  @GameTest(template = "empty", timeoutTicks = 4000)
  public static void altarReferenceRegeneratesOverworldNoiseDeterministically(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var registries = level.registryAccess();
    var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
    var parameters = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
    var generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(parameters), settings);
    long seed = 20260923L;
    var randomState = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
    ChunkPos forest = null;
    for (int ring = 0; ring < 40 && forest == null; ring++)
      for (int x = -ring; x <= ring && forest == null; x++)
        for (int z = -ring; z <= ring && forest == null; z++) {
          if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
          var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x * 16 + 8), QuartPos.fromBlock(72),
              QuartPos.fromBlock(z * 16 + 8), randomState.sampler());
          if (biome.is(BiomeTags.IS_FOREST) && !biome.is(BiomeTags.IS_TAIGA)) forest = new ChunkPos(x, z);
        }
    helper.assertTrue(forest != null, "No forest found near the origin of the fixed seed");
    var setup = new TerrainReference.Setup(generator, randomState, seed, registries, level.enabledFeatures(),
        level.getMinBuildHeight(), level.getHeight(), level.dimensionType(), Map.of(), Map.of(), Set.of());
    ChunkPos center = forest;
    List<ChunkPos> area = new ArrayList<>();
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) area.add(new ChunkPos(center.x + dx, center.z + dz));
    area.sort(java.util.Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
    CompletableFuture<TerrainReference.Region> first = CompletableFuture.supplyAsync(
        () -> TerrainReference.build(setup, List.of(center), () -> false), NOISE_WORKER);
    CompletableFuture<TerrainReference.Region> second = CompletableFuture.supplyAsync(
        () -> TerrainReference.build(setup, List.of(center), () -> false), NOISE_WORKER);
    CompletableFuture<TerrainReference.Region> wide = CompletableFuture.supplyAsync(
        () -> TerrainReference.build(setup, List.copyOf(area), () -> false), NOISE_WORKER);
    await(helper, () -> first.isDone() && second.isDone() && wide.isDone(), 0, 3900, "Noise reference", () -> {
      var one = first.join();
      var two = second.join();
      var three = wide.join();
      int logs = 0, leaves = 0, plants = 0, top = Integer.MIN_VALUE;
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (int x = center.getMinBlockX(); x <= center.getMaxBlockX(); x++)
        for (int z = center.getMinBlockZ(); z <= center.getMaxBlockZ(); z++) {
          top = Math.max(top, one.surface(x, z));
          for (int y = 40; y < 200; y++) {
            BlockState state = one.state(cursor.set(x, y, z));
            if (state.is(BlockTags.LOGS)) logs++;
            else if (state.is(BlockTags.LEAVES)) leaves++;
            else if (AltarVegetation.plant(state)) plants++;
          }
        }
      LOGGER.info("ALTAR_PERF scenario=overworld-noise chunk={} biome={} single=[terrainChunks={} terrainMillis={} decorationMillis={}] wide=[terrainChunks={} decorated={} terrainMillis={} decorationMillis={} perTerrainChunkMillis={} perDecoratedChunkMillis={}] logs={} leaves={} plants={} top={} failures={} unsupported={}",
          center, level.registryAccess().registryOrThrow(Registries.BIOME).getKey(one.biome(new BlockPos(center.getMiddleBlockX(), 72, center.getMiddleBlockZ())).value()),
          one.terrainChunks, one.terrainNanos / 1_000_000, one.decorationNanos / 1_000_000,
          three.terrainChunks, three.decoratedChunks, three.terrainNanos / 1_000_000, three.decorationNanos / 1_000_000,
          three.terrainNanos / 1_000_000.0 / three.terrainChunks, three.decorationNanos / 1_000_000.0 / three.decoratedChunks,
          logs, leaves, plants, top, three.failures, three.unsupported);
      helper.assertTrue(one.digest() == two.digest(), "Two regenerations of the same chunk differ");
      helper.assertTrue(one.surfaceRules && top > 40 && top < 260, "Noise terrain or surface rules missing: top=" + top);
      helper.assertTrue(logs > 0 && leaves > 0, "The forest chunk regenerated without its trees");
      helper.assertTrue(three.failures.isEmpty() && three.unsupported.isEmpty(),
          "Vanilla features needed something the sandbox does not provide: " + three.failures + " " + three.unsupported);
      helper.succeed();
    });
  }

  // ---- Levelling: flatten --------------------------------------------------------------------

  /** Meadow with a hill, a boulder, a pit and higher land to the east of the flat square. */
  private static void terraformLand(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -3, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -4, z), Blocks.STONE.defaultBlockState(), 2);
      }
    for (int x = 17; x <= 19; x++)
      for (int z = 17; z <= 19; z++) {
        level.setBlock(at(helper, x, 1, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 2, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 3, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
    level.setBlock(at(helper, 21, 1, 17), Blocks.STONE.defaultBlockState(), 2);
    level.setBlock(at(helper, 21, 2, 17), Blocks.STONE.defaultBlockState(), 2);
    for (int x = 21; x <= 23; x++)
      for (int z = 21; z <= 23; z++)
        for (int y = -2; y <= 0; y++) level.setBlock(at(helper, x, y, z), Blocks.AIR.defaultBlockState(), 2);
    for (int x = 29; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, 1, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 2, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 3, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
  }

  private static TerraformAltarEntity terraform(GameTestHelper helper, int size) {
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ALTAR);
    level.setBlockAndUpdate(pos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
    var altar = (TerraformAltarEntity) level.getBlockEntity(pos);
    altar.configureSize(size);
    return altar;
  }

  /** Every column either untouched, refused whole, or at its target with nothing natural above it. */
  private static void assertWholeColumns(GameTestHelper helper, TerraformAltarEntity altar,
      Map<BlockPos, BlockState> before, boolean finished, Set<Long> skipped) {
    var level = helper.getLevel();
    BlockPos center = altar.getBlockPos();
    int size = altar.size(), flat = altar.flatTop();
    for (var column : TerraformRules.columns(size)) {
      if (column.dx() == 0 && column.dz() == 0) continue;
      int x = center.getX() + column.dx(), z = center.getZ() + column.dz();
      if (skipped.contains(ChunkPos.asLong(x, z))) continue;
      int target = TerraformRules.target(column.dx(), column.dz(), size, flat,
          (ox, oz) -> altar.outerGroundAt(level, center.getX() + ox, center.getZ() + oz));
      boolean untouched = true;
      for (int y = flat - 8; y <= flat + 10; y++) {
        BlockPos pos = new BlockPos(x, y, z);
        if (before.containsKey(pos) && !before.get(pos).equals(level.getBlockState(pos))) untouched = false;
      }
      boolean kept = false;
      for (int y = target - 3; y <= target + 10; y++) {
        var cell = TerraformAltarEntity.cell(level.getBlockState(new BlockPos(x, y, z)));
        if (cell == TerraformRules.Cell.BUILT || cell == TerraformRules.Cell.PROTECTED) kept = true;
      }
      if (untouched && (kept || !finished)) continue;
      BlockState top = level.getBlockState(new BlockPos(x, target, z));
      helper.assertTrue(TerraformAltarEntity.basic(top), "Column " + column + " has no plain ground at its target " + target + ": " + top);
      for (int y = target + 1; y <= target + 10; y++) {
        BlockState above = level.getBlockState(new BlockPos(x, y, z));
        var cell = TerraformAltarEntity.cell(above);
        helper.assertTrue(above.isAir() || cell == TerraformRules.Cell.BUILT,
            "Column " + column + " kept natural blocks above its target: " + above);
      }
    }
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void levellingAltarFlattensForFreeWithLayersAndSlopeSparingOresAndTrees(GameTestHelper helper) {
    terraformLand(helper);
    var level = helper.getLevel();
    var altar = terraform(helper, 4);
    BlockPos altarPos = altar.getBlockPos();
    BlockPos chestPos = at(helper, 18, 1, 22);
    level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
    ((ChestBlockEntity) level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5));
    BlockPos planks = at(helper, 22, 0, 18);
    level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
    // An ore inside the hill and a young tree beside it: the altar neither destroys nor buries them.
    BlockPos ore = at(helper, 19, 2, 17);
    level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
    BlockPos trunk = at(helper, 16, 1, 22);
    level.setBlockAndUpdate(trunk, Blocks.OAK_LOG.defaultBlockState());
    level.setBlockAndUpdate(trunk.above(), Blocks.OAK_LOG.defaultBlockState());
    BlockPos claimedFill = at(helper, 23, 0, 23), claimedCut = at(helper, 17, 3, 18);
    Consumer<BlockEvent.EntityPlaceEvent> placeClaim = veto(BlockEvent.EntityPlaceEvent.class, event -> {
      if (event.getPos().getX() == claimedFill.getX() && event.getPos().getZ() == claimedFill.getZ()) event.setCanceled(true);
    });
    Consumer<BlockEvent.BreakEvent> breakClaim = veto(BlockEvent.BreakEvent.class, event -> {
      if (event.getPos().getX() == claimedCut.getX() && event.getPos().getZ() == claimedCut.getZ()) event.setCanceled(true);
    });
    altar.addFuel(new ItemStack(Items.COAL, 4));
    var before = snapshot(helper, -4, 12);
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, altarPos, Direction.UP);
    helper.assertTrue(handler != null && handler.getSlots() == 1
        && !handler.insertItem(0, new ItemStack(Items.DIRT, 16), true).isEmpty()
        && handler.extractItem(0, 64, true).isEmpty(), "The altar still takes or gives materials");
    try (var visitor = new Visitor(helper, altarPos)) {
      visitor.use(altarPos, ItemStack.EMPTY, false);
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.PREVIEW && changes(helper, before).isEmpty(),
          "The first use must only preview");
      visitor.use(altarPos, ItemStack.EMPTY, true);
      helper.assertTrue(altar.size() == 8, "Crouched use during the preview did not change the size");
      visitor.use(altarPos, ItemStack.EMPTY, true);
      visitor.use(altarPos, ItemStack.EMPTY, true);
      visitor.use(altarPos, ItemStack.EMPTY, true);
      helper.assertTrue(altar.repairing(), "The fifth setting is not the repair");
      visitor.use(altarPos, ItemStack.EMPTY, true);
      helper.assertTrue(altar.size() == 4, "Settings did not cycle back");
      visitor.use(altarPos, ItemStack.EMPTY, false);
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.RUNNING, "The second use did not start");
    }
    Set<Long> claimedColumns = Set.of(ChunkPos.asLong(claimedFill.getX(), claimedFill.getZ()),
        ChunkPos.asLong(claimedCut.getX(), claimedCut.getZ()));
    await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 1400, "Free flatten", () -> {
      perf("terraform-flat", altar);
      assertWholeColumns(helper, altar, before, true, claimedColumns);
      int flat = altar.flatTop();
      int layered = 0;
      for (int x = 16; x <= 24; x++)
        for (int z = 16; z <= 24; z++) {
          BlockPos top = new BlockPos(at(helper, x, 0, z).getX(), flat, at(helper, x, 0, z).getZ());
          if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) && level.getBlockState(top.below()).is(Blocks.DIRT)
              && level.getBlockState(top.above()).isAir()) layered++;
        }
      helper.assertTrue(layered >= 70, "Too few flat columns got grass over dirt: " + layered);
      int previous = flat;
      for (int x = 25; x <= 28; x++) {
        int height = at(helper, x, 0, 20).getY();
        while (!level.getBlockState(new BlockPos(at(helper, x, 0, 20).getX(), height + 1, at(helper, x, 0, 20).getZ())).isAir()) height++;
        helper.assertTrue(height >= previous && height <= flat + 3, "The slope does not rise smoothly at x=" + x + ": " + height);
        previous = height;
      }
      helper.assertTrue(previous > flat, "The slope never rose towards the higher land");
      helper.assertTrue(level.getBlockState(claimedFill).isAir() && level.getBlockState(claimedFill.below(2)).isAir()
          && level.getBlockState(claimedCut).is(Blocks.GRASS_BLOCK), "A claimed column was changed");
      helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST)
          && ItemStack.matches(((ChestBlockEntity) level.getBlockEntity(chestPos)).getItem(0), new ItemStack(Items.WHEAT_SEEDS, 5))
          && level.getBlockState(planks).is(Blocks.OAK_PLANKS), "A build or container was changed");
      helper.assertTrue(level.getBlockState(ore).is(Blocks.IRON_ORE) && level.getBlockState(ore.above()).is(Blocks.GRASS_BLOCK)
          && level.getBlockState(trunk).is(Blocks.OAK_LOG) && level.getBlockState(trunk.above()).is(Blocks.OAK_LOG)
          && altar.refusals().getOrDefault("protected", 0) >= 2, "An ore or a tree was removed or buried: " + altar.refusals());
      helper.assertTrue(altar.totals().cut > 0 && altar.totals().filled > 0 && altar.totals().refused >= 4,
          "Unexpected totals");
      helper.assertTrue(noDrops(helper) && altar.fuelUsed() >= 1, "Levelling dropped items or used no fuel");
      var settled = snapshot(helper, -4, 12);
      try (var visitor = new Visitor(helper, altarPos)) {
        visitor.use(altarPos, ItemStack.EMPTY, false);
        visitor.use(altarPos, ItemStack.EMPTY, false);
      }
      await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 400, "Repeated flatten", () -> {
        NeoForge.EVENT_BUS.unregister(placeClaim);
        NeoForge.EVENT_BUS.unregister(breakClaim);
        var repeated = changes(helper, settled);
        helper.assertTrue(repeated.isEmpty(), "A repeated run changed settled land: " + repeated.keySet());
        helper.succeed();
      });
    });
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void levellingAltarPausesOnWholeColumnsAndResumesAfterReload(GameTestHelper helper) {
    terraformLand(helper);
    var level = helper.getLevel();
    var altar = terraform(helper, 8);
    BlockPos altarPos = altar.getBlockPos();
    var before = snapshot(helper, -4, 12);
    altar.start(level);
    await(helper, () -> altar.state() == TerraformAltarEntity.State.WAITING, 0, 200, "Unfuelled levelling", () -> {
      helper.assertTrue(altar.cursor() == 0 && changes(helper, before).isEmpty() && altar.fuelUsed() == 0
          && !AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos), "The altar worked without fuel");
      altar.addFuel(new ItemStack(Items.CHARCOAL, 2));
      await(helper, () -> altar.cursor() > 120 || altar.state() == TerraformAltarEntity.State.DONE
          || altar.state() == TerraformAltarEntity.State.PAUSED, 0, 800, "Partial levelling", () -> {
        helper.assertTrue(altar.state() == TerraformAltarEntity.State.RUNNING, "Levelling stopped early: " + altar.state());
        int reach = TerraformRules.reach(altar.size());
        helper.assertTrue(altar.fuelUsed() == 1
            && AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos.offset(-reach, 0, reach))
            && !AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos.offset(0, 0, reach + 1)),
            "Fuel or the registry did not follow the running altar");
        try (var visitor = new Visitor(helper, altarPos)) {
          visitor.use(altarPos, ItemStack.EMPTY, false);
        }
        helper.assertTrue(altar.state() == TerraformAltarEntity.State.PAUSED, "Use did not pause");
        altar.serverTick(level);
        helper.assertTrue(!AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos), "A paused altar stayed registered");
        int cursor = altar.cursor();
        assertWholeColumns(helper, altar, before, false, Set.of());
        CompoundTag saved = altar.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(altarPos);
        BlockEntity loaded = BlockEntity.loadStatic(altarPos, level.getBlockState(altarPos), saved, level.registryAccess());
        level.setBlockEntity(loaded);
        var reloaded = (TerraformAltarEntity) level.getBlockEntity(altarPos);
        helper.assertTrue(reloaded != altar && reloaded.cursor() == cursor
            && reloaded.state() == TerraformAltarEntity.State.PAUSED && reloaded.flatTop() == altar.flatTop()
            && reloaded.palette().equals(altar.palette()) && reloaded.totals().cut == altar.totals().cut
            && reloaded.legacy().isEmpty(), "Levelling progress did not round-trip");
        try (var visitor = new Visitor(helper, altarPos)) {
          visitor.use(altarPos, ItemStack.EMPTY, false);
        }
        helper.assertTrue(reloaded.state() == TerraformAltarEntity.State.RUNNING, "Use did not resume");
        await(helper, () -> reloaded.state() == TerraformAltarEntity.State.DONE, 0, 1400, "Resumed levelling", () -> {
          perf("terraform-resumed", reloaded);
          assertWholeColumns(helper, reloaded, before, true, Set.of());
          helper.assertTrue(noDrops(helper), "Levelling dropped items");
          helper.succeed();
        });
      });
    });
  }

  /**
   * A cave in the Overworld, open to the sky above its roof: the altar finds the stone roof over it,
   * cuts the mound on the floor and keeps the roof and the stalactite hanging from it. The old
   * planner cut every natural block up to 32 over the target, roof included.
   */
  @GameTest(template = "nature_restoration", timeoutTicks = 1200, skyAccess = true)
  public static void levellingAltarKeepsTheRoofOfAnOverworldCave(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -3, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -4, z), Blocks.STONE.defaultBlockState(), 2);
        for (int y = 7; y <= 8; y++) level.setBlock(at(helper, x, y, z), Blocks.STONE.defaultBlockState(), 2);
      }
    List<BlockPos> mound = new ArrayList<>(), stalactite = new ArrayList<>();
    for (int x = 22; x <= 23; x++)
      for (int z = 22; z <= 23; z++)
        for (int y = 1; y <= 2; y++) mound.add(at(helper, x, y, z));
    for (int y = 4; y <= 6; y++) stalactite.add(at(helper, 18, y, 18));
    for (BlockPos pos : concat(mound, stalactite)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
    var altar = terraform(helper, 4);
    altar.addFuel(new ItemStack(Items.COAL, 2));
    var before = snapshot(helper, -4, 12);
    altar.start(level);
    int roof = at(helper, 0, 7, 0).getY();
    helper.assertTrue(altar.roofed() && altar.bandTop() == roof,
        "The altar did not find the cave roof over it: band=" + altar.bandTop() + " roof=" + roof);
    await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 1000, "Overworld cave flatten", () -> {
      perf("terraform-overworld-cave", altar);
      var changes = changes(helper, before);
      changes.remove(altar.getBlockPos());
      for (var entry : changes.entrySet())
        helper.assertTrue(entry.getKey().getY() < roof - 1, "Something at or over the cave roof changed: " + entry);
      for (BlockPos pos : stalactite) helper.assertTrue(!changes.containsKey(pos), "The stalactite was cut at " + pos);
      for (BlockPos pos : mound) helper.assertTrue(level.getBlockState(pos).isAir(), "The mound was not cut at " + pos);
      helper.assertTrue(level.getBlockState(mound.getFirst().below()).is(Blocks.GRASS_BLOCK), "The mound's floor was not resurfaced");
      helper.assertTrue(noDrops(helper) && altar.totals().refused == 0, "Unexpected drops or refusals: " + altar.refusals());
      helper.succeed();
    });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void levellingAltarHandsBackItsOldBufferOnce(GameTestHelper helper) {
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    level.setBlockAndUpdate(pos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
    var fresh = (TerraformAltarEntity) level.getBlockEntity(pos);
    CompoundTag saved = fresh.saveWithFullMetadata(level.registryAccess());
    // A version 1 save: its 27-slot buffer lived in the common "altar" compound.
    saved.putInt("version", 1);
    ListTag items = new ListTag();
    CompoundTag dirt = (CompoundTag) new ItemStack(Items.DIRT, 40).save(level.registryAccess());
    dirt.putByte("Slot", (byte) 0);
    items.add(dirt);
    CompoundTag cobble = (CompoundTag) new ItemStack(Items.COBBLESTONE, 12).save(level.registryAccess());
    cobble.putByte("Slot", (byte) 5);
    items.add(cobble);
    saved.getCompound("altar").put("Items", items);
    CompoundTag oldTotals = new CompoundTag();
    oldTotals.putIntArray("values", new int[] {3, 4, 5, 6, 7, 8, 9});
    saved.put("totals", oldTotals);
    level.removeBlockEntity(pos);
    var loaded = (TerraformAltarEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
    helper.assertTrue(loaded != null && loaded.legacy().size() == 2 && loaded.totals().covered == 0
        && loaded.totals().cut == 4, "The version 1 buffer or totals were not read");
    level.setBlockEntity(loaded);
    var altar = (TerraformAltarEntity) level.getBlockEntity(pos);
    altar.serverTick(level);
    var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    int dirtCount = 0, cobbleCount = 0;
    for (ItemEntity drop : drops) {
      if (drop.getItem().is(Items.DIRT)) dirtCount += drop.getItem().getCount();
      if (drop.getItem().is(Items.COBBLESTONE)) cobbleCount += drop.getItem().getCount();
    }
    helper.assertTrue(dirtCount == 40 && cobbleCount == 12 && altar.legacy().isEmpty(),
        "The old buffer was not handed back exactly once: " + dirtCount + " " + cobbleCount);
    altar.serverTick(level);
    helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).size() == drops.size()
        && !altar.saveWithFullMetadata(level.registryAccess()).contains("legacy"),
        "The old buffer was handed back twice or stayed saved");
    for (ItemEntity drop : drops) drop.discard();
    helper.succeed();
  }

  // ---- Levelling: repair ---------------------------------------------------------------------

  private static TerraformAltarEntity repair(GameTestHelper helper, TerrainReference.Setup setup) {
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ALTAR);
    level.setBlockAndUpdate(pos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
    TerraformAltarEntity.SETUPS.put(GlobalPos.of(level.dimension(), pos), world -> setup);
    var altar = (TerraformAltarEntity) level.getBlockEntity(pos);
    altar.configureSize(TerraformRules.REPAIR);
    altar.configureRepairRadius(RADIUS);
    return altar;
  }

  @SafeVarargs
  private static List<BlockPos> concat(List<BlockPos>... lists) {
    List<BlockPos> all = new ArrayList<>();
    for (var list : lists) all.addAll(list);
    return all;
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void levellingAltarRepairsPitsToTheOriginalSurfaceAndSparesBuilds(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(at(helper, x, -3, z), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(at(helper, x, -4, z), Blocks.STONE.defaultBlockState(), 2);
      }
    // The original land holds an iron ore layer at y -3: a repair rebuilds it as plain stone.
    var altar = repair(helper, flatSetup(helper, Biomes.PLAINS, Blocks.IRON_ORE));
    BlockPos altarPos = altar.getBlockPos();
    List<BlockPos> pitA = new ArrayList<>(), pitB = new ArrayList<>(), outside = new ArrayList<>();
    for (int x = 24; x <= 27; x++)
      for (int z = 16; z <= 19; z++) pitA.add(at(helper, x, 0, z));
    for (int x = 14; x <= 16; x++)
      for (int z = 22; z <= 24; z++) pitB.add(at(helper, x, 0, z));
    for (int x = 33; x <= 34; x++) outside.add(at(helper, x, 0, 20));
    for (var top : concat(pitA, pitB, outside))
      for (int dy = 0; dy >= -3; dy--) level.setBlock(top.above(dy), Blocks.AIR.defaultBlockState(), 2);
    BlockPos chestPos = at(helper, 24, -3, 16);
    level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
    var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
    chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 7));
    BlockPos planks = at(helper, 18, 0, 16);
    level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
    BlockPos besidePlanks = at(helper, 18, 0, 17);
    List<BlockPos> bare = List.of(at(helper, 22, 0, 23), at(helper, 22, 0, 24));
    for (var pos : concat(bare, List.of(besidePlanks))) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
    BlockPos claimed = at(helper, 14, 0, 22);
    Consumer<BlockEvent.EntityPlaceEvent> claim = veto(BlockEvent.EntityPlaceEvent.class, event -> {
      if (event.getPos().getX() == claimed.getX() && event.getPos().getZ() == claimed.getZ()) event.setCanceled(true);
    });
    var before = snapshot(helper, -4, 3);
    try (var visitor = new Visitor(helper, altarPos)) {
      visitor.use(altarPos, new ItemStack(Items.COAL, 2), false);
      helper.assertTrue(visitor.player.getMainHandItem().isEmpty() && altar.fuel().getCount() == 2,
          "Coal did not feed the altar");
    }
    altar.start(level);
    await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE
        || altar.state() == TerraformAltarEntity.State.FAILED, 0, 1400, "Repair pass", () -> {
      perf("terraform-repair-pits", altar);
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.DONE && altar.totals().skippedChunks == 0,
          "The repair did not recognise the land: " + altar.state() + " " + altar.totals().skippedChunks);
      Set<BlockPos> moat = Set.of(at(helper, 24, 0, 16), at(helper, 25, 0, 16), at(helper, 24, 0, 17));
      for (var top : pitA) {
        boolean kept = moat.contains(top);
        for (int dy = 0; dy >= -3; dy--) {
          BlockPos pos = top.above(dy);
          if (pos.equals(chestPos)) continue;
          BlockState now = level.getBlockState(pos);
          BlockState expected = dy == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
              : dy == -3 ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
          helper.assertTrue(kept ? now.isAir() : now.equals(expected),
              "Pit A column " + top + " dy=" + dy + " is " + now + (kept ? " but borders the chest" : ""));
        }
      }
      for (var top : pitB) {
        boolean kept = top.equals(claimed);
        for (int dy = 0; dy >= -3; dy--) {
          BlockState now = level.getBlockState(top.above(dy));
          helper.assertTrue(kept ? now.isAir() : !now.isAir() && !now.is(Blocks.IRON_ORE),
              "Pit B column " + top + " dy=" + dy + " is " + now);
        }
      }
      for (var top : outside) helper.assertTrue(level.getBlockState(top).isAir(), "Changed a pit outside the square");
      for (var pos : bare) helper.assertTrue(level.getBlockState(pos).is(Blocks.GRASS_BLOCK), "Bare dirt kept");
      helper.assertTrue(level.getBlockState(besidePlanks).is(Blocks.DIRT) && level.getBlockState(planks).is(Blocks.OAK_PLANKS)
          && level.getBlockState(chestPos).is(Blocks.CHEST)
          && ItemStack.matches(chest.getItem(0), new ItemStack(Items.WHEAT_SEEDS, 7)),
          "A build, container or its margin was changed");
      for (var entry : changes(helper, before).entrySet())
        helper.assertTrue(TerraformAltarEntity.basic(entry.getValue()),
            "A repair placed something that is not plain terrain: " + entry);
      helper.assertTrue(noDrops(helper) && altar.fuelUsed() >= 1 && altar.fuel().getCount() + altar.fuelUsed() == 2,
          "The repair dropped items or its fuel did not add up");
      var settled = snapshot(helper, -4, 3);
      altar.start(level);
      await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 1200, "Repeated repair", () -> {
        NeoForge.EVENT_BUS.unregister(claim);
        helper.assertTrue(changes(helper, settled).isEmpty(), "A repeated repair changed something: "
            + changes(helper, settled));
        TerraformAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altarPos));
        helper.succeed();
      });
    });
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void levellingAltarRepairProgressSurvivesSaveAndReload(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    var altar = repair(helper, flatSetup(helper, Biomes.PLAINS, null));
    BlockPos altarPos = altar.getBlockPos();
    List<BlockPos> pit = new ArrayList<>();
    for (int x = 13; x <= 18; x++)
      for (int z = 13; z <= 18; z++) pit.add(at(helper, x, 0, z));
    for (var top : pit)
      for (int dy = 0; dy >= -2; dy--) level.setBlock(top.above(dy), Blocks.AIR.defaultBlockState(), 2);
    altar.addFuel(new ItemStack(Items.COAL, 4));
    altar.start(level);
    await(helper, () -> altar.totals().filled > 0 || altar.state() == TerraformAltarEntity.State.DONE
        || altar.state() == TerraformAltarEntity.State.FAILED, 0, 1200, "First repair tick", () -> {
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.RUNNING && altar.totals().filled < pit.size() * 3,
          "The pit was repaired before a reload could interrupt it: " + altar.totals().filled);
      helper.assertTrue(AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos.offset(RADIUS, 0, -RADIUS))
          && !AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos.offset(RADIUS + 1, 0, 0))
          && !AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos),
          "The active altar's square is not what the registry reports");
      CompoundTag saved = altar.saveWithFullMetadata(level.registryAccess());
      int filled = altar.totals().filled, cursor = altar.cursor();
      level.removeBlockEntity(altarPos);
      BlockEntity loaded = BlockEntity.loadStatic(altarPos, level.getBlockState(altarPos), saved, level.registryAccess());
      helper.assertTrue(loaded instanceof TerraformAltarEntity, "The altar did not load from its saved data");
      level.setBlockEntity(loaded);
      var reloaded = (TerraformAltarEntity) level.getBlockEntity(altarPos);
      helper.assertTrue(reloaded != altar && reloaded.state() == TerraformAltarEntity.State.RUNNING
          && reloaded.repairing() && reloaded.repairRadius() == RADIUS && reloaded.cursor() == cursor
          && reloaded.totals().filled == filled, "Saved repair progress did not round-trip");
      await(helper, () -> reloaded.state() == TerraformAltarEntity.State.DONE, 0, 1200, "Reloaded repair", () -> {
        perf("terraform-repair-reload", reloaded);
        helper.assertTrue(reloaded.totals().filled == pit.size() * 3,
            "Resuming after a reload missed or repeated blocks: " + reloaded.totals().filled);
        for (var top : pit)
          helper.assertTrue(level.getBlockState(top).is(Blocks.GRASS_BLOCK) && level.getBlockState(top.below()).is(Blocks.DIRT)
              && level.getBlockState(top.below(2)).is(Blocks.DIRT), "Pit column not repaired: " + top);
        helper.assertTrue(!AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos),
            "A finished altar stayed registered");
        TerraformAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altarPos));
        helper.succeed();
      });
    });
  }

  // ---- Levelling under a roof ----------------------------------------------------------------

  /** Force-loads a square of chunks; the returned action releases them. */
  private static Runnable forceChunks(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
    for (int x = minX; x <= maxX; x++)
      for (int z = minZ; z <= maxZ; z++) level.setChunkForced(x, z, true);
    return () -> {
      for (int x = minX; x <= maxX; x++)
        for (int z = minZ; z <= maxZ; z++) level.setChunkForced(x, z, false);
    };
  }

  /**
   * A biome without natural spawns over a box, and no creature left in it: a mob standing on a
   * column rightly makes the altar leave it, which would tie the counts to the Nether's crowd.
   */
  private static void quiet(ServerLevel level, BlockPos from, BlockPos to, int biomeLow, int biomeHigh) {
    var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
    net.minecraft.server.commands.FillBiomeCommand.fill(level, new BlockPos(from.getX(), biomeLow, from.getZ()),
        new BlockPos(to.getX(), biomeHigh, to.getZ()), holder);
    for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null,
        new AABB(from.getX(), from.getY(), from.getZ(), to.getX() + 1, to.getY() + 1, to.getZ() + 1),
        entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) entity.discard();
  }

  private static Map<BlockPos, BlockState> box(ServerLevel level, BlockPos from, BlockPos to) {
    Map<BlockPos, BlockState> states = new HashMap<>();
    for (BlockPos pos : BlockPos.betweenClosed(from, to)) states.put(pos.immutable(), level.getBlockState(pos));
    return states;
  }

  private static Map<BlockPos, BlockState> changed(ServerLevel level, Map<BlockPos, BlockState> before) {
    Map<BlockPos, BlockState> changed = new HashMap<>();
    before.forEach((pos, state) -> {
      BlockState now = level.getBlockState(pos);
      if (!now.equals(state)) changed.put(pos, now);
    });
    return changed;
  }

  /** Removes a finished test altar without dropping its fuel into the level. */
  private static void removeAltar(ServerLevel level, BlockPos pos) {
    level.removeBlockEntity(pos);
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
  }

  /**
   * A cave in the real Nether with an upper cavern over its roof, so the old planner, which cut
   * every natural block up to 32 over the target, would have cut the roof. On the floor: a mound
   * with nylium and a fungus, a pit, a pillar from floor to roof, a stalactite over a pit, and a
   * higher floor to the east for the slope. From the roof: a stalactite and weeping vines.
   */
  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void levellingAltarFlattensTheNetherCaveFloorAndLeavesItsRoof(GameTestHelper helper) {
    ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    helper.assertTrue(nether != null && nether.dimensionType().hasCeiling(), "No roofed Nether in the test server");
    int x0 = 8192 + 8, z0 = 8192 + 8, y0 = 64, room = 12, size = 4, roof = y0 + 10;
    int minChunkX = (x0 - room - 16) >> 4, minChunkZ = (z0 - room - 16) >> 4;
    int maxChunkX = (x0 + room + 16) >> 4, maxChunkZ = (z0 + room + 16) >> 4;
    Runnable release = forceChunks(nether, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    await(helper, () -> LandWorks.loaded(nether, minChunkX, minChunkZ, maxChunkX, maxChunkZ), 0, 1500, "Nether chunks", () -> {
      try {
        BlockState netherrack = Blocks.NETHERRACK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = x0 - room; x <= x0 + room; x++)
          for (int z = z0 - room; z <= z0 + room; z++)
            for (int y = y0 - 6; y <= y0 + 46; y++) {
              boolean wall = Math.abs(x - x0) == room || Math.abs(z - z0) == room;
              BlockState state = wall || y <= y0 || y >= roof && y <= roof + 3 || y == y0 + 46 ? netherrack
                  : y == y0 + 45 ? Blocks.BEDROCK.defaultBlockState() : air;
              if (!wall && x - x0 >= 9 && y > y0 && y <= y0 + 2) state = netherrack;
              nether.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
            }
        java.util.function.BiFunction<Integer, Integer, BlockPos.MutableBlockPos> column =
            (dx, dz) -> new BlockPos.MutableBlockPos(x0 + dx, 0, z0 + dz);
        // A mound two high under crimson nylium and a fungus.
        for (int dx = 1; dx <= 3; dx++)
          for (int dz = -3; dz <= -1; dz++) {
            nether.setBlock(column.apply(dx, dz).setY(y0 + 1), netherrack, Block.UPDATE_CLIENTS);
            nether.setBlock(column.apply(dx, dz).setY(y0 + 2), Blocks.CRIMSON_NYLIUM.defaultBlockState(), Block.UPDATE_CLIENTS);
          }
        BlockPos fungus = column.apply(2, -2).setY(y0 + 3).immutable();
        nether.setBlock(fungus, Blocks.CRIMSON_FUNGUS.defaultBlockState(), Block.UPDATE_CLIENTS);
        // A pit three deep.
        for (int dx = -3; dx <= -1; dx++)
          for (int dz = 1; dz <= 3; dz++)
            for (int y = y0 - 2; y <= y0; y++) nether.setBlock(column.apply(dx, dz).setY(y), air, Block.UPDATE_CLIENTS);
        // A stalactite, weeping vines that stop short of the floor, a pillar, and a pit under a stalactite.
        List<BlockPos> hanging = new ArrayList<>();
        for (int y = y0 + 5; y < roof; y++) hanging.add(column.apply(-2, -2).setY(y).immutable());
        for (BlockPos pos : hanging) nether.setBlock(pos, netherrack, Block.UPDATE_CLIENTS);
        List<BlockPos> vines = new ArrayList<>();
        for (int y = y0 + 5; y < roof; y++) {
          BlockPos pos = column.apply(0, -4).setY(y).immutable();
          nether.setBlock(pos, y == y0 + 5
              ? Blocks.WEEPING_VINES.defaultBlockState().setValue(net.minecraft.world.level.block.GrowingPlantHeadBlock.AGE, 25)
              : Blocks.WEEPING_VINES_PLANT.defaultBlockState(), Block.UPDATE_CLIENTS);
          vines.add(pos);
        }
        List<BlockPos> pillar = new ArrayList<>();
        for (int y = y0 + 1; y < roof; y++) pillar.add(column.apply(3, 3).setY(y).immutable());
        for (BlockPos pos : pillar) nether.setBlock(pos, netherrack, Block.UPDATE_CLIENTS);
        BlockPos sealedPit = column.apply(0, 3).setY(y0).immutable();
        nether.setBlock(sealedPit, air, Block.UPDATE_CLIENTS);
        nether.setBlock(sealedPit.below(), air, Block.UPDATE_CLIENTS);
        for (int y = y0 + 1; y < roof; y++) {
          BlockPos pos = column.apply(0, 3).setY(y).immutable();
          nether.setBlock(pos, netherrack, Block.UPDATE_CLIENTS);
          hanging.add(pos);
        }
        BlockPos from = new BlockPos(x0 - room, y0 - 6, z0 - room), to = new BlockPos(x0 + room, y0 + 46, z0 + room);
        quiet(nether, from, to, y0 - 4, y0 + 12);
        BlockPos altarPos = new BlockPos(x0, y0 + 1, z0);
        nether.setBlockAndUpdate(altarPos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
        var altar = (TerraformAltarEntity) nether.getBlockEntity(altarPos);
        altar.configureSize(size);
        altar.addFuel(new ItemStack(Items.COAL, 4));
        var before = box(nether, from, to);
        altar.start(nether);
        helper.assertTrue(altar.roofed() && altar.bandTop() == roof,
            "The altar did not find the cave roof over it: roofed=" + altar.roofed() + " band=" + altar.bandTop());
        helper.assertTrue(altar.palette().surface().is(Blocks.NETHERRACK) && altar.palette().deep().is(Blocks.NETHERRACK),
            "The palette did not come from the cave floor: " + altar.palette());
        CompoundTag saved = altar.saveWithFullMetadata(nether.registryAccess());
        var copy = (TerraformAltarEntity) BlockEntity.loadStatic(altarPos, nether.getBlockState(altarPos), saved,
            nether.registryAccess());
        helper.assertTrue(copy != null && copy.roofed() && copy.bandTop() == roof, "The roof over the band was not saved");
        await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 1400, "Nether flatten", () -> {
          try {
            perf("terraform-nether-cave", altar);
            var changes = changed(nether, before);
            changes.remove(altarPos);
            for (var entry : changes.entrySet()) {
              BlockPos pos = entry.getKey();
              helper.assertTrue(pos.getY() < roof - 1, "Something at or over the band changed: " + entry);
              helper.assertTrue(entry.getValue().isAir() || TerraformAltarEntity.basic(entry.getValue()),
                  "The altar placed something that is not plain terrain: " + entry);
            }
            for (BlockPos pos : concat(hanging, vines, pillar))
              helper.assertTrue(!changes.containsKey(pos), "What hangs from the roof or reaches it was touched: " + pos);
            helper.assertTrue(nether.getBlockState(sealedPit).isAir() && nether.getBlockState(sealedPit.below()).isAir(),
                "The pit under the stalactite was sealed against it");
            for (int dx = 1; dx <= 3; dx++)
              for (int dz = -3; dz <= -1; dz++) {
                BlockPos floor = column.apply(dx, dz).setY(y0).immutable();
                helper.assertTrue(nether.getBlockState(floor).is(Blocks.NETHERRACK) && nether.getBlockState(floor.above()).isAir()
                    && nether.getBlockState(floor.above(2)).isAir() && nether.getBlockState(floor.above(3)).isAir(),
                    "The mound was not cut to the floor at " + floor);
              }
            for (int dx = -3; dx <= -1; dx++)
              for (int dz = 1; dz <= 3; dz++)
                for (int y = y0 - 2; y <= y0; y++)
                  helper.assertTrue(nether.getBlockState(column.apply(dx, dz).setY(y)).is(Blocks.NETHERRACK),
                      "The pit was not filled with the cave's rock at " + dx + "," + y + "," + dz);
            int[] slope = new int[4];
            for (int dx = 5; dx <= 8; dx++) {
              BlockPos.MutableBlockPos probe = column.apply(dx, 0).setY(y0);
              while (!nether.getBlockState(probe.above()).isAir()) probe.move(Direction.UP);
              slope[dx - 5] = probe.getY() - y0;
            }
            helper.assertTrue(java.util.Arrays.equals(slope, new int[] {0, 1, 1, 2}),
                "The slope does not follow the higher floor to the east: " + java.util.Arrays.toString(slope));
            helper.assertTrue(altar.refusals().getOrDefault("ceiling", 0) == 2 && altar.totals().refused == 2,
                "Only the pillar and the pit under the stalactite should be left alone: " + altar.refusals());
            helper.assertTrue(nether.getEntitiesOfClass(ItemEntity.class, new AABB(from).expandTowards(2 * room, 52, 2 * room)).isEmpty(),
                "The Nether flatten dropped items");
            var settled = box(nether, from, to);
            altar.start(nether);
            await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 600, "Repeated Nether flatten", () -> {
              try {
                var repeated = changed(nether, settled);
                helper.assertTrue(repeated.isEmpty(), "A repeated run changed the settled cave: " + repeated.keySet());
                removeAltar(nether, altarPos);
                helper.succeed();
              } finally {
                release.run();
              }
            });
          } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
          }
        });
      } catch (RuntimeException | Error failure) {
        release.run();
        throw failure;
      }
    });
  }


  /** Open space over a floor: air or a wild plant, such as the roots and fungi of a crimson forest. */
  private static boolean open(BlockState state) {
    return !TerraformRules.solid(TerraformAltarEntity.cell(state));
  }

  /** Plain ground at {@code y} with {@code height} blocks of open space over it. */
  private static boolean openFloor(ServerLevel level, int x, int y, int z, int height) {
    if (!TerraformAltarEntity.basic(level.getBlockState(new BlockPos(x, y, z)))) return false;
    for (int dy = 1; dy <= height; dy++) if (!open(level.getBlockState(new BlockPos(x, y + dy, z)))) return false;
    return true;
  }

  /** The column's floor nearest {@code flat} in the live level or in a reference, or UNKNOWN when it is not ground. */
  private static int floorOf(java.util.function.IntFunction<BlockState> states, int minY, int flat, int band) {
    return floorOf(states, TerraformAltarEntity::cell, minY, flat, band);
  }

  private static int floorOf(java.util.function.IntFunction<BlockState> states,
      java.util.function.Function<BlockState, TerraformRules.Cell> cells, int minY, int flat, int band) {
    int y = TerraformRules.floorNear(at -> {
      BlockState state = states.apply(at);
      return state == null ? TerraformRules.Cell.BUILT : cells.apply(state);
    }, minY, flat, band);
    return y != TerraformRules.UNKNOWN && states.apply(y) != null && LandWorks.ground(states.apply(y)) ? y
        : TerraformRules.UNKNOWN;
  }

  /**
   * The altar's own trust test of a chunk, run against the test's reference: floors nearest
   * {@code flat}. Returns the comparable, agreeing and surplus column counts.
   */
  private static int[] trust(ServerLevel level, TerrainReference.Region reference, ChunkPos chunk, int flat, int band) {
    return trust(level, reference, chunk, flat, band, TerraformAltarEntity::cell);
  }

  private static int[] trust(ServerLevel level, TerrainReference.Region reference, ChunkPos chunk, int flat, int band,
      java.util.function.Function<BlockState, TerraformRules.Cell> cells) {
    int natural = 0, agree = 0, surplus = 0;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
      for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
        int cx = x, cz = z;
        int original = floorOf(at -> reference.state(probe.set(cx, at, cz)), cells, reference.setup.minY(), flat, band);
        int current = floorOf(at -> level.getBlockState(probe.set(cx, at, cz)), cells, level.getMinBuildHeight(), flat, band);
        if (original == TerraformRules.UNKNOWN || current == TerraformRules.UNKNOWN) continue;
        natural++;
        if (Math.abs(current - original) <= 1) agree++;
        else if (current > original + 1) surplus++;
      }
    return new int[] {natural, agree, surplus};
  }

  /** A repair site: the floor under the altar and the floors of the 3×3 patch east of it that the test digs. */
  private record NetherSite(BlockPos floor, List<BlockPos> patch) {}

  /**
   * The floors of the altar column and of a 3×3 patch two to four blocks east of it, each within a
   * block of the altar's floor, plain ground three deep, with open space over it and no fluid beside
   * it; null when the ground there is not like that.
   */
  private static List<BlockPos> siteFloors(ServerLevel level, int x, int y, int z) {
    List<BlockPos> floors = new ArrayList<>();
    for (int dx = 2; dx <= 4; dx++)
      for (int dz = -1; dz <= 1; dz++) {
        BlockPos floor = null;
        for (int f : new int[] {y, y + 1, y - 1})
          if (floor == null && openFloor(level, x + dx, f, z + dz, 2)) floor = new BlockPos(x + dx, f, z + dz);
        if (floor == null) return null;
        for (int dy = 0; dy >= -2; dy--) {
          BlockPos pos = floor.above(dy);
          if (!TerraformAltarEntity.basic(level.getBlockState(pos))) return null;
          for (Direction side : Direction.Plane.HORIZONTAL)
            if (!level.getBlockState(pos.relative(side)).getFluidState().isEmpty()) return null;
        }
        floors.add(floor);
      }
    return floors;
  }

  /** The generator made the same ground under each floor, three deep, and left open space over it. */
  private static boolean asGenerated(ServerLevel level, TerrainReference.Region reference, List<BlockPos> floors) {
    for (BlockPos floor : floors) {
      for (int dy = 0; dy >= -2; dy--) {
        BlockState original = reference.state(floor.above(dy));
        if (original == null || !level.getBlockState(floor.above(dy)).is(original.getBlock())) return false;
      }
      BlockState over = reference.state(floor.above());
      if (over == null || !open(over)) return false;
    }
    return true;
  }

  /**
   * The first place, ring by ring from the middle of the reference, where the floor is open plain
   * ground, a 3×3 patch two blocks east of it is plain ground three deep within a block of the same
   * height with no fluid beside it, all exactly as the generator made them, and every chunk of the
   * repair square passes the altar's trust test. {@code stats} counts how far the candidates got.
   */
  private static NetherSite netherRepairSite(ServerLevel level, TerrainReference.Region reference, ChunkPos middle,
      int radius, Map<String, Object> stats) {
    int cx = middle.getMiddleBlockX(), cz = middle.getMiddleBlockZ();
    int reach = 16 + 8 - radius - 1;
    Map<String, int[]> chunks = new HashMap<>();
    for (int ring = 0; ring <= reach; ring++)
      for (int x = cx - ring; x <= cx + ring; x++)
        for (int z = cz - ring; z <= cz + ring; z++) {
          if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != ring) continue;
          for (int y = 100; y >= 36; y--) {
            if (!openFloor(level, x, y, z, 4)) continue;
            stats.merge("floors", 1, (a, b) -> (Integer) a + (Integer) b);
            List<BlockPos> patch = siteFloors(level, x, y, z);
            if (patch == null) continue;
            stats.merge("patches", 1, (a, b) -> (Integer) a + (Integer) b);
            List<BlockPos> all = new ArrayList<>(patch);
            all.add(new BlockPos(x, y, z));
            if (!asGenerated(level, reference, all)) continue;
            stats.merge("asGenerated", 1, (a, b) -> (Integer) a + (Integer) b);
            int floor = y;
            int band = TerraformAltarEntity.roofOver(level, new BlockPos(x, y + 1, z), y);
            boolean comparable = true;
            for (var key : AltarRules.areaChunks(x, z, radius)) {
              ChunkPos chunk = new ChunkPos(key.x(), key.z());
              int[] counts = chunks.computeIfAbsent(chunk + "@" + y + "/" + band,
                  ignored -> trust(level, reference, chunk, floor, band));
              if (counts[0] < 96 || !AltarRules.consistent(counts[0], counts[1], counts[2])) {
                comparable = false;
                stats.putIfAbsent("untrusted:" + chunk + "@" + y, counts[0] + "/" + counts[1] + "/" + counts[2]);
              }
            }
            if (comparable) {
              stats.put("trust", AltarRules.areaChunks(x, z, radius).stream()
                  .map(key -> java.util.Arrays.toString(chunks.get(new ChunkPos(key.x(), key.z()) + "@" + floor + "/" + band)))
                  .toList());
              return new NetherSite(new BlockPos(x, y, z), patch);
            }
          }
        }
    return null;
  }

  /** Where the live Nether and the reference differ in a chunk, by block pair: carvers, ores and the like. */
  private static String differences(ServerLevel level, TerrainReference.Region reference, ChunkPos chunk) {
    Map<String, Integer> pairs = new TreeMap<>();
    int compared = 0;
    BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
      for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++)
        for (int y = 1; y < 127; y++) {
          BlockState original = reference.state(probe.set(x, y, z));
          if (original == null) continue;
          compared++;
          BlockState now = level.getBlockState(probe);
          if (now.is(original.getBlock())) continue;
          pairs.merge(BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath() + "<-"
              + BuiltInRegistries.BLOCK.getKey(original.getBlock()).getPath(), 1, Integer::sum);
        }
    List<Map.Entry<String, Integer>> top = new ArrayList<>(pairs.entrySet());
    top.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
    return "compared=" + compared + " differing=" + pairs.values().stream().mapToInt(Integer::intValue).sum()
        + " top=" + top.subList(0, Math.min(12, top.size()));
  }

  /**
   * The repair on the real floor of the fixed-seed Nether, against its own generator: pits dug in
   * the floor are refilled up to the generator's floor with its own blocks, nothing at or over the
   * roof of the altar's band changes, and the altar only rebuilds what the generator made. The old
   * repair read the land's top, which in the Nether is the bedrock roof, and never reached the floor.
   * It runs in a batch of its own: its chunk generation and its two regenerations would otherwise
   * compete with the timing checks of other tests.
   */
  @GameTest(template = "empty", timeoutTicks = 6000, batch = "altars_nether_reference")
  public static void levellingAltarRepairsTheNetherFloorFromItsGenerator(GameTestHelper helper) {
    ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
    helper.assertTrue(nether != null && nether.dimensionType().hasCeiling(), "No roofed Nether in the test server");
    int radius = 8;
    ChunkPos middle = new ChunkPos(-192, 192);
    Runnable release = forceChunks(nether, middle.x - 2, middle.z - 2, middle.x + 2, middle.z + 2);
    await(helper, () -> LandWorks.loaded(nether, middle.x - 2, middle.z - 2, middle.x + 2, middle.z + 2), 0, 2500,
        "Nether chunks", () -> {
      try {
        List<ChunkPos> decorate = new ArrayList<>(), captured = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++)
          for (int dz = -2; dz <= 2; dz++) {
            ChunkPos pos = new ChunkPos(middle.x + dx, middle.z + dz);
            captured.add(pos);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) decorate.add(pos);
          }
        var setup = TerrainReference.Setup.capture(nether, captured);
        CompletableFuture<TerrainReference.Region> built = CompletableFuture.supplyAsync(
            () -> TerrainReference.build(setup, List.copyOf(decorate), () -> false), NOISE_WORKER);
        await(helper, built::isDone, 0, 2500, "Nether reference", () -> {
          try {
            var reference = built.join();
            Map<String, Object> stats = new TreeMap<>();
            NetherSite found = netherRepairSite(nether, reference, middle, radius, stats);
            BlockPos site = found == null ? null : found.floor();
            LOGGER.info("ALTAR_NETHER_SITE site={} stats={} middle=[{}] reference=[terrain={} decorated={} surfaceRules={} failures={} unsupported={}]",
                site, stats, differences(nether, reference, middle), reference.terrainChunks, reference.decoratedChunks,
                reference.surfaceRules, reference.failures, reference.unsupported);
            helper.assertTrue(site != null, "No open Nether floor matching its generator near " + middle + ": " + stats);
            int x = site.getX(), y = site.getY(), z = site.getZ();
            BlockPos from = new BlockPos(x - radius, Math.max(nether.getMinBuildHeight(), y - 24), z - radius);
            BlockPos to = new BlockPos(x + radius, Math.min(nether.getMaxBuildHeight() - 1, y + 64), z + radius);
            quiet(nether, from.offset(-2, 0, -2), to.offset(2, 0, 2), y - 8, y + 16);
            // As a player would: the plants over the pit and the altar go first, so nothing is left floating.
            List<BlockPos> cleared = new ArrayList<>(found.patch());
            cleared.add(site);
            for (BlockPos floor : cleared)
              for (BlockPos pos = floor.above(); open(nether.getBlockState(pos)) && !nether.getBlockState(pos).isAir(); pos = pos.above())
                nether.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockPos altarPos = site.above();
            nether.setBlockAndUpdate(altarPos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
            var altar = (TerraformAltarEntity) nether.getBlockEntity(altarPos);
            altar.configureSize(TerraformRules.REPAIR);
            altar.configureRepairRadius(radius);
            altar.addFuel(new ItemStack(Items.COAL, 4));
            var before = box(nether, from, to);
            List<BlockPos> pit = new ArrayList<>();
            for (BlockPos floor : found.patch()) {
              pit.add(floor);
              pit.add(floor.below());
            }
            for (BlockPos pos : pit) nether.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            altar.start(nether);
            helper.assertTrue(altar.roofed() && altar.bandTop() > y + 4 && altar.bandTop() <= y + TerraformRules.MAX_CUT + 1,
                "The altar did not work under the Nether roof: band=" + altar.bandTop() + " floor=" + y);
            await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE
                || altar.state() == TerraformAltarEntity.State.FAILED, 0, 2500, "Nether repair", () -> {
              try {
                perf("terraform-nether-repair", altar);
                LOGGER.info("ALTAR_NETHER_REPAIR site={} band={} biome={} reference=[terrain={} decorated={} terrainMillis={} decorationMillis={} failures={} unsupported={}]",
                    site, altar.bandTop(), nether.registryAccess().registryOrThrow(Registries.BIOME)
                        .getKey(reference.biome(site).value()), reference.terrainChunks, reference.decoratedChunks,
                    reference.terrainNanos / 1_000_000, reference.decorationNanos / 1_000_000, reference.failures,
                    reference.unsupported);
                helper.assertTrue(altar.state() == TerraformAltarEntity.State.DONE && altar.totals().skippedChunks == 0,
                    "The Nether's own land was not recognised: " + altar.state() + " skipped="
                        + altar.totals().skippedChunks + " refusals=" + altar.refusals());
                BlockState rock = TerraformAltarEntity.defaultRock(nether);
                helper.assertTrue(rock.is(Blocks.NETHERRACK), "The Nether's own rock is not netherrack: " + rock);
                for (BlockPos pos : pit) {
                  BlockState original = reference.state(pos), now = nether.getBlockState(pos);
                  helper.assertTrue(now.equals(TerraformAltarEntity.fill(original, pos.getY(), rock))
                      && now.is(before.get(pos).getBlock()),
                      "Repaired " + pos + " with " + now + " instead of the generator's " + original);
                }
                var changes = changed(nether, before);
                changes.remove(altarPos);
                int elsewhere = 0;
                for (var entry : changes.entrySet()) {
                  BlockPos pos = entry.getKey();
                  helper.assertTrue(pos.getY() <= altar.bandTop() - 2 && TerraformAltarEntity.basic(entry.getValue()),
                      "Something other than plain ground on the floor changed: " + entry + " band=" + altar.bandTop());
                  if (pit.contains(pos)) continue;
                  elsewhere++;
                  BlockState was = before.get(pos);
                  helper.assertTrue((was.isAir() || was.canBeReplaced()) && TerraformAltarEntity.fillable(reference.state(pos)),
                      "The repair changed " + pos + " from " + was + " where the generator had " + reference.state(pos));
                }
                LOGGER.info("ALTAR_NETHER_REPAIR pit={} changed={} elsewhere={} filled={}", pit.size(), changes.size(),
                    elsewhere, altar.totals().filled);
                // What comparing a whole chunk under the roof costs, warm and at best of 20 (the machine may be busy):
                // the altar's own floor searches, with its cache of block states.
                Map<BlockState, TerraformRules.Cell> cache = new java.util.IdentityHashMap<>();
                ChunkPos siteChunk = new ChunkPos(site);
                long[] rounds = new long[20];
                for (int round = 0; round < rounds.length; round++) {
                  long started = System.nanoTime();
                  trust(nether, reference, siteChunk, y, altar.bandTop(), state -> cache.computeIfAbsent(state, TerraformAltarEntity::cell));
                  rounds[round] = System.nanoTime() - started;
                }
                java.util.Arrays.sort(rounds);
                LOGGER.info("ALTAR_PERF scenario=nether-trust-chunk columns=256 bestMicros={} medianMicros={} perColumnBestMicros={}",
                    rounds[0] / 1000, rounds[rounds.length / 2] / 1000, rounds[0] / 256_000.0);
                helper.assertTrue(nether.getEntitiesOfClass(ItemEntity.class, new AABB(from).expandTowards(2 * radius, 88, 2 * radius)).isEmpty(),
                    "The Nether repair dropped items");
              } catch (RuntimeException | Error failure) {
                release.run();
                throw failure;
              }
              // A second run, warm: it rebuilds the reference, compares again and changes nothing.
              var settled = box(nether, from, to);
              altar.workingTicks = altar.tickNanos = altar.maxTickNanos = altar.maxTrustNanos = 0;
              altar.gcTicks = altar.maxGcMillis = 0;
              altar.start(nether);
              await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE
                  || altar.state() == TerraformAltarEntity.State.FAILED, 0, 2500, "Repeated Nether repair", () -> {
                try {
                  perf("terraform-nether-repair-repeated", altar);
                  var repeated = changed(nether, settled);
                  helper.assertTrue(altar.state() == TerraformAltarEntity.State.DONE && repeated.isEmpty()
                      && altar.totals().skippedChunks == 0, "A repeated Nether repair changed something: " + repeated.keySet());
                  removeAltar(nether, altarPos);
                  helper.succeed();
                } finally {
                  release.run();
                }
              });
            });
          } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
          }
        });
      } catch (RuntimeException | Error failure) {
        release.run();
        throw failure;
      }
    });
  }
}
