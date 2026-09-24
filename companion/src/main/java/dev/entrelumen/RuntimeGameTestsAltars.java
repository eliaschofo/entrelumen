package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
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
 * Isolated altar GameTests; excluded from the distributable jar by the RuntimeGameTests* pattern.
 * The renewal cases use a flat generator whose layers end exactly on the template floor, so the
 * real sandbox, diff and apply paths run against a known original; the overworld noise generator
 * is exercised separately for determinism and timing. Claims are simulated by cancelling the
 * native events, as in the Nature restoration tests.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsAltars {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int EXTENT = 40;
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

  /** A flat generator whose grass top is the template floor: the "original" land of the test. */
  static TerrainReference.Setup flatSetup(GameTestHelper helper, ResourceKey<Biome> biome, boolean decorate) {
    ServerLevel level = helper.getLevel();
    int ground = at(helper, 0, 0, 0).getY();
    int min = level.getMinBuildHeight();
    var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
    var settings = new FlatLevelGeneratorSettings(Optional.empty(), biomes.getOrThrow(biome), List.of());
    settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
    int stone = ground - 2 - (min + 1);
    helper.assertTrue(stone >= 0, "Template floor is too low for the flat reference");
    if (stone > 0) settings.getLayersInfo().add(new FlatLayerInfo(stone, Blocks.STONE));
    settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
    settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
    settings.updateLayers();
    if (decorate) settings.setDecoration();
    return new TerrainReference.Setup(new FlatLevelSource(settings), level.getChunkSource().randomState(),
        level.getSeed(), level.registryAccess(), level.enabledFeatures(), level.getMinBuildHeight(),
        level.getHeight(), level.dimensionType(), Map.of(), Map.of(), Set.of());
  }

  private static RenewalAltarEntity renewal(GameTestHelper helper, TerrainReference.Setup setup) {
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ALTAR);
    level.setBlockAndUpdate(pos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
    RenewalAltarEntity.SETUPS.put(GlobalPos.of(level.dimension(), pos), world -> setup);
    var altar = (RenewalAltarEntity) level.getBlockEntity(pos);
    altar.configureRadius(RADIUS);
    return altar;
  }

  private static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what,
      Runnable then) {
    if (done.getAsBoolean()) {
      then.run();
      return;
    }
    helper.assertTrue(waited < limit, what + " did not finish in time");
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
    LOGGER.info("ALTAR_PERF scenario={} workingTicks={} wallWithoutGc=[mean={} p50={} p95={} max={}]us gcTicks={} maxGcMillis={} actorMillis={} treeSplitMillis={} totals=[filled={} covered={} trees={} plants={} guarded={} skipped={} fertilizer={} charge={}] guards={}",
        scenario, altar.workingTicks, mean / 1000, percentile(altar.tickSamples, altar.workingTicks, 0.5) / 1000,
        percentile(altar.tickSamples, altar.workingTicks, 0.95) / 1000, altar.maxTickNanos / 1000,
        altar.gcTicks, altar.maxGcMillis, altar.actorNanos / 1_000_000,
        altar.treeNanos / 1_000_000, altar.totals().filled,
        altar.totals().covered, altar.totals().trees, altar.totals().plants, altar.totals().guarded,
        altar.totals().skippedChunks, altar.totals().fertilizer, altar.totals().charge, altar.guards);
  }

  private static void perf(String scenario, TerraformAltarEntity altar) {
    long mean = altar.workingTicks == 0 ? 0 : altar.tickNanos / altar.workingTicks;
    LOGGER.info("ALTAR_PERF scenario={} workingTicks={} wallWithoutGc=[mean={} p50={} p95={} max={}]us gcTicks={} maxGcMillis={} actorMillis={} totals=[columns={} cut={} filled={} swapped={} refused={}] refusals={}",
        scenario, altar.workingTicks, mean / 1000, percentile(altar.tickSamples, altar.workingTicks, 0.5) / 1000,
        percentile(altar.tickSamples, altar.workingTicks, 0.95) / 1000, altar.maxTickNanos / 1000,
        altar.gcTicks, altar.maxGcMillis, altar.actorNanos / 1_000_000,
        altar.totals().columns,
        altar.totals().cut, altar.totals().filled, altar.totals().swapped, altar.totals().refused, altar.refusals());
  }

  // ---- Renewal: terrain ----------------------------------------------------------------------

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void renewalAltarRefillsPitsToTheOriginalSurfaceAndSparesBuilds(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    var altar = renewal(helper, flatSetup(helper, Biomes.PLAINS, false));
    BlockPos altarPos = altar.getBlockPos();
    List<BlockPos> pitA = new ArrayList<>(), pitB = new ArrayList<>(), outside = new ArrayList<>();
    for (int x = 24; x <= 27; x++)
      for (int z = 16; z <= 19; z++) pitA.add(at(helper, x, 0, z));
    for (int x = 14; x <= 16; x++)
      for (int z = 22; z <= 24; z++) pitB.add(at(helper, x, 0, z));
    for (int x = 33; x <= 34; x++) outside.add(at(helper, x, 0, 20));
    for (var top : concat(pitA, pitB, outside))
      for (int dy = 0; dy >= -2; dy--) level.setBlock(top.above(dy), Blocks.AIR.defaultBlockState(), 2);
    BlockPos chestPos = at(helper, 24, -2, 16);
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
    var before = snapshot(helper, -3, 3);

    try (var visitor = new Visitor(helper, altarPos)) {
      visitor.use(altarPos, new ItemStack(Items.BONE_MEAL, 12), false);
      helper.assertTrue(visitor.player.getMainHandItem().isEmpty() && altar.fertilizer().getCount() == 12
          && altar.state() == RenewalAltarEntity.State.RUNNING, "Bone meal did not feed and start the altar");
    }
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, altarPos, Direction.UP);
    helper.assertTrue(handler != null && handler.extractItem(0, 64, false).isEmpty()
        && !handler.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty(),
        "The altar accepted a non-fertilizer or gave fertilizer back");

    await(helper, () -> altar.state() == RenewalAltarEntity.State.WAITING
        || altar.state() == RenewalAltarEntity.State.DONE, 0, 1200, "First renewal pass", () -> {
      helper.assertTrue(altar.state() == RenewalAltarEntity.State.WAITING && altar.fertilizer().isEmpty()
          && altar.charge() < AltarRules.CHARGE_PER_FERTILIZER,
          "Twelve bone meal should run out before these scars are healed: " + altar.state());
      ItemStack rest = handler.insertItem(0, new ItemStack(Items.BONE_MEAL, 32), false);
      helper.assertTrue(rest.isEmpty(), "A hopper could not feed the altar");
      await(helper, () -> altar.state() == RenewalAltarEntity.State.DONE, 0, 1200, "Fed renewal pass", () -> {
        perf("renewal-pits", altar);
        var totals = altar.totals();
        int remaining = altar.fertilizer().getCount();
        helper.assertTrue(12 + 32 - remaining == totals.fertilizer
            && totals.charge == totals.filled + totals.covered + totals.plants
            && totals.fertilizer * AltarRules.CHARGE_PER_FERTILIZER - totals.charge == altar.charge()
            && altar.charge() < AltarRules.CHARGE_PER_FERTILIZER && totals.trees == 0,
            "Fertilizer was not proportional to the work applied: " + totals.fertilizer + " " + totals.charge);
        Set<BlockPos> moat = Set.of(at(helper, 24, 0, 16), at(helper, 25, 0, 16), at(helper, 24, 0, 17));
        for (var top : pitA) {
          boolean kept = moat.contains(top);
          for (int dy = 0; dy >= -2; dy--) {
            BlockState now = level.getBlockState(top.above(dy));
            BlockState expected = dy == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            if (top.above(dy).equals(chestPos)) continue;
            helper.assertTrue(kept ? now.isAir() : now.equals(expected),
                "Pit A column " + top + " dy=" + dy + " is " + now + (kept ? " but borders the chest" : ""));
          }
        }
        for (var top : pitB) {
          boolean kept = top.equals(claimed);
          for (int dy = 0; dy >= -2; dy--) {
            BlockState now = level.getBlockState(top.above(dy));
            helper.assertTrue(kept ? now.isAir() : !now.isAir() && (dy == 0 ? now.is(Blocks.GRASS_BLOCK) : now.is(Blocks.DIRT)),
                "Pit B column " + top + " dy=" + dy + " is " + now);
          }
        }
        for (var top : outside) helper.assertTrue(level.getBlockState(top).isAir(), "Changed a pit outside the radius");
        for (var pos : bare) helper.assertTrue(level.getBlockState(pos).is(Blocks.GRASS_BLOCK), "Bare dirt kept");
        helper.assertTrue(level.getBlockState(besidePlanks).is(Blocks.DIRT) && level.getBlockState(planks).is(Blocks.OAK_PLANKS)
            && level.getBlockState(chestPos).is(Blocks.CHEST)
            && ItemStack.matches(chest.getItem(0), new ItemStack(Items.WHEAT_SEEDS, 7)),
            "A build, container or its margin was changed");
        var changed = changes(helper, before);
        for (var entry : changed.entrySet())
          helper.assertTrue(entry.getValue().is(Blocks.GRASS_BLOCK) || entry.getValue().is(Blocks.DIRT),
              "Unexpected block written: " + entry);
        helper.assertTrue(noDrops(helper), "Renewal dropped items");

        var settled = snapshot(helper, -3, 3);
        try (var visitor = new Visitor(helper, altarPos)) {
          visitor.use(altarPos, ItemStack.EMPTY, true);
        }
        helper.assertTrue(altar.state() == RenewalAltarEntity.State.RUNNING, "Crouched use did not start a new pass");
        int stored = altar.fertilizer().getCount();
        await(helper, () -> altar.state() == RenewalAltarEntity.State.DONE, 0, 1200, "Repeated renewal pass", () -> {
          NeoForge.EVENT_BUS.unregister(claim);
          helper.assertTrue(changes(helper, settled).isEmpty() && altar.totals().charge == 0
              && altar.fertilizer().getCount() == stored, "A repeated pass changed or charged something");
          RenewalAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altarPos));
          helper.succeed();
        });
      });
    });
  }

  @SafeVarargs
  private static List<BlockPos> concat(List<BlockPos>... lists) {
    List<BlockPos> all = new ArrayList<>();
    for (var list : lists) all.addAll(list);
    return all;
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void renewalAltarProgressSurvivesSaveAndReload(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    var altar = renewal(helper, flatSetup(helper, Biomes.PLAINS, false));
    BlockPos altarPos = altar.getBlockPos();
    List<BlockPos> pit = new ArrayList<>();
    for (int x = 13; x <= 18; x++)
      for (int z = 13; z <= 18; z++) pit.add(at(helper, x, 0, z));
    for (var top : pit)
      for (int dy = 0; dy >= -2; dy--) level.setBlock(top.above(dy), Blocks.AIR.defaultBlockState(), 2);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    await(helper, () -> altar.totals().filled > 0 || altar.state() == RenewalAltarEntity.State.DONE
        || altar.state() == RenewalAltarEntity.State.FAILED, 0, 1200, "First work tick", () -> {
      helper.assertTrue(altar.state() == RenewalAltarEntity.State.RUNNING && altar.totals().filled < pit.size() * 3,
          "The pit was restored before a reload could interrupt it: " + altar.totals().filled);
      helper.assertTrue(AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos.offset(RADIUS, 0, -RADIUS))
          && !AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos.offset(RADIUS + 1, 0, 0))
          && !AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos),
          "The active altar's square is not what the registry reports");
      CompoundTag saved = altar.saveWithFullMetadata(level.registryAccess());
      int filled = altar.totals().filled, charge = altar.charge(), pass = altar.pass(), index = altar.index();
      int stored = altar.fertilizer().getCount(), spent = altar.totals().charge;
      level.removeBlockEntity(altarPos);
      BlockEntity loaded = BlockEntity.loadStatic(altarPos, level.getBlockState(altarPos), saved, level.registryAccess());
      helper.assertTrue(loaded instanceof RenewalAltarEntity, "The altar did not load from its saved data");
      level.setBlockEntity(loaded);
      var reloaded = (RenewalAltarEntity) level.getBlockEntity(altarPos);
      helper.assertTrue(reloaded != altar && reloaded.state() == RenewalAltarEntity.State.RUNNING
          && reloaded.totals().filled == filled && reloaded.charge() == charge
          && reloaded.pass() == pass && reloaded.index() == index && reloaded.fertilizer().getCount() == stored
          && reloaded.totals().charge == spent, "Saved progress, charge or fertilizer did not round-trip");
      await(helper, () -> reloaded.state() == RenewalAltarEntity.State.DONE, 0, 1200, "Reloaded renewal", () -> {
        perf("renewal-reload", reloaded);
        var totals = reloaded.totals();
        helper.assertTrue(totals.filled == pit.size() * 3 && totals.charge == totals.filled + totals.covered
            && 64 - reloaded.fertilizer().getCount() == totals.fertilizer
            && totals.fertilizer == Math.ceilDiv(totals.charge, AltarRules.CHARGE_PER_FERTILIZER),
            "Resuming after a reload charged twice or missed blocks: " + totals.filled + " " + totals.charge);
        for (var top : pit)
          helper.assertTrue(level.getBlockState(top).is(Blocks.GRASS_BLOCK) && level.getBlockState(top.below()).is(Blocks.DIRT)
              && level.getBlockState(top.below(2)).is(Blocks.DIRT), "Pit column not restored: " + top);
        helper.assertTrue(!AltarRegistry.isInsideActive(level, AltarType.RENEWAL, altarPos)
            && reloaded.activeTicks() > 0, "A finished altar stayed registered, or no active time was paid");
        RenewalAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altarPos));
        helper.succeed();
      });
    });
  }

  // ---- Renewal: vegetation -------------------------------------------------------------------

  @GameTest(template = "nature_restoration", timeoutTicks = 2400, skyAccess = true)
  public static void renewalAltarRegrowsTheOriginalTreesAndPlantsWhole(GameTestHelper helper) {
    meadow(helper);
    var level = helper.getLevel();
    var setup = flatSetup(helper, Biomes.FOREST, true);
    BlockPos altarPos = helper.absolutePos(ALTAR);
    List<ChunkPos> decorate = new ArrayList<>();
    for (var key : AltarRules.areaChunks(altarPos.getX(), altarPos.getZ(), RADIUS))
      decorate.add(new ChunkPos(key.x(), key.z()));
    decorate.sort(java.util.Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
    long started = System.nanoTime();
    var expected = TerrainReference.build(setup, decorate, () -> false);
    long elapsed = System.nanoTime() - started;
    LOGGER.info("ALTAR_PERF scenario=flat-forest-reference chunks={} decorated={} totalMillis={} failures={} unsupported={}",
        expected.terrainChunks, expected.decoratedChunks, elapsed / 1_000_000, expected.failures, expected.unsupported);
    // A cobblestone post inside one original canopy: that whole tree must stay away.
    BlockPos post = null, blockedAnchor = null;
    int cx = altarPos.getX(), cz = altarPos.getZ(), ground = at(helper, 0, 0, 0).getY();
    for (int x = cx - RADIUS + 2; x <= cx + RADIUS - 2 && post == null; x++)
      for (int z = cz - RADIUS + 2; z <= cz + RADIUS - 2 && post == null; z++) {
        if (x > cx - 2 || !AltarRules.inSquare(x - cx, z - cz, RADIUS - 2)) continue;
        BlockPos trunk = new BlockPos(x, ground + 1, z);
        BlockState state = expected.state(trunk);
        if (state != null && state.is(BlockTags.LOGS) && !expected.state(trunk.below()).is(BlockTags.LOGS)) {
          blockedAnchor = trunk;
          for (int dy = 2; dy <= 8 && post == null; dy++) {
            BlockPos leaf = trunk.offset(1, dy, 0);
            BlockState at = expected.state(leaf);
            if (at != null && at.is(BlockTags.LEAVES)) post = leaf;
          }
          if (post == null) blockedAnchor = null;
        }
      }
    helper.assertTrue(post != null, "The forest reference had no tree to obstruct in the west half");
    BlockPos finalPost = post, finalAnchor = blockedAnchor;
    level.setBlockAndUpdate(finalPost, Blocks.COBBLESTONE.defaultBlockState());
    // Claimed land: every leaf placement east of the altar is refused, so those trees stay whole or absent.
    Consumer<BlockEvent.EntityPlaceEvent> claim = veto(BlockEvent.EntityPlaceEvent.class, event -> {
      // Only this test's land: GameTests run side by side and share the event bus.
      BlockPos corner = at(helper, 0, 0, 0);
      if (event.getPos().getX() > cx + 2 && event.getPos().getX() < corner.getX() + EXTENT
          && event.getPos().getZ() >= corner.getZ() && event.getPos().getZ() < corner.getZ() + EXTENT
          && event.getPlacedBlock().is(BlockTags.LEAVES)) event.setCanceled(true);
    });
    var before = snapshot(helper, -2, 30);
    var altar = renewal(helper, setup);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    long[] digest = {0};
    int[] given = {64};
    TerrainReference.Region[] seen = {null};
    await(helper, () -> {
      if (digest[0] == 0 && altar.region() != null) {
        seen[0] = altar.region();
        digest[0] = seen[0].digest();
      }
      if (altar.state() == RenewalAltarEntity.State.WAITING && altar.fertilizer().isEmpty()) {
        altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
        given[0] += 64;
      }
      return altar.state() == RenewalAltarEntity.State.DONE;
    }, 0, 2000, "Forest renewal", () -> {
      NeoForge.EVENT_BUS.unregister(claim);
      perf("renewal-forest", altar);
      if (digest[0] != expected.digest())
        LOGGER.info("ALTAR_DIFF altarFailures={} altarUnsupported={} testFailures={} first={}", seen[0].failures,
            seen[0].unsupported, expected.failures, seen[0].differences(expected, 12));
      helper.assertTrue(digest[0] == expected.digest(), "The altar's reference differs from an identical regeneration");
      var changed = changes(helper, before);
      changed.remove(altar.getBlockPos());
      int logs = 0;
      for (var entry : changed.entrySet()) {
        BlockState original = expected.state(entry.getKey());
        helper.assertTrue(original != null && RenewalAltarEntity.same(original, entry.getValue()),
            "Wrote something the generator did not make: " + entry + " vs " + original);
        if (entry.getValue().is(BlockTags.LOGS)) {
          logs++;
          helper.assertTrue(entry.getKey().getX() <= cx + 2 + AltarRules.TREE_MARGIN,
              "A tree grew although its leaves were refused");
        }
        if (entry.getValue().is(BlockTags.LEAVES))
          helper.assertTrue(entry.getKey().getX() <= cx + 2, "A refused leaf was placed");
      }
      helper.assertTrue(logs > 0 && altar.totals().trees > 0 && altar.totals().plants > 0,
          "No original tree or plant was restored: " + altar.totals().trees + " " + altar.totals().plants);
      helper.assertTrue(level.getBlockState(finalPost).is(Blocks.COBBLESTONE) && level.getBlockState(finalAnchor).isAir(),
          "The obstructed tree grew in part or the post was replaced");
      var totals = altar.totals();
      helper.assertTrue(totals.charge >= totals.filled + totals.covered + totals.plants + totals.trees
          && totals.charge <= totals.filled + totals.covered + totals.plants + AltarRules.TREE_CHARGE * totals.trees
          && given[0] - altar.fertilizer().getCount() == totals.fertilizer
          && totals.fertilizer * AltarRules.CHARGE_PER_FERTILIZER - totals.charge == altar.charge(),
          "Tree and plant payment did not match the work applied");
      helper.assertTrue(noDrops(helper), "Renewal dropped items");
      RenewalAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altar.getBlockPos()));
      // A third regeneration on the altar's own worker, after the pass: all three must agree.
      CompletableFuture<TerrainReference.Region> third = TerrainReference.generate(setup, decorate, () -> false);
      await(helper, third::isDone, 0, 600, "Third regeneration", () -> {
        helper.assertTrue(third.join().digest() == expected.digest(),
            "A later regeneration differs: " + third.join().differences(expected, 12));
        helper.succeed();
      });
    });
  }

  // ---- Renewal: overworld noise sandbox ------------------------------------------------------

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
    CompletableFuture<TerrainReference.Region> first = TerrainReference.generate(setup, List.of(center), () -> false);
    CompletableFuture<TerrainReference.Region> second = TerrainReference.generate(setup, List.of(center), () -> false);
    CompletableFuture<TerrainReference.Region> wide = TerrainReference.generate(setup, area, () -> false);
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
            else if (RenewalAltarEntity.plantLike(state)) plants++;
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

  // ---- Terraform -----------------------------------------------------------------------------

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

  /** Terrain blocks in the affected box plus every buffered item: conserved by terraforming. */
  private static int matter(GameTestHelper helper, TerraformAltarEntity altar) {
    int count = 0;
    for (int x = 8; x <= 32; x++)
      for (int y = -4; y <= 8; y++)
        for (int z = 8; z <= 32; z++) {
          BlockState state = helper.getLevel().getBlockState(at(helper, x, y, z));
          if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE))
            count++;
        }
    return count + altar.bufferCount();
  }

  private static TerraformAltarEntity terraform(GameTestHelper helper, int size) {
    var level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ALTAR);
    level.setBlockAndUpdate(pos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
    var altar = (TerraformAltarEntity) level.getBlockEntity(pos);
    altar.configureSize(size);
    return altar;
  }

  /** Every column either untouched or at its target with nothing natural above it. */
  private static void assertWholeColumns(GameTestHelper helper, TerraformAltarEntity altar,
      Map<BlockPos, BlockState> before, boolean finished, Set<Long> claimed) {
    var level = helper.getLevel();
    BlockPos center = altar.getBlockPos();
    int size = altar.size(), flat = altar.flatTop();
    for (var column : TerraformRules.columns(size)) {
      if (column.dx() == 0 && column.dz() == 0) continue;
      int x = center.getX() + column.dx(), z = center.getZ() + column.dz();
      if (claimed.contains(ChunkPos.asLong(x, z))) continue;
      int target = TerraformRules.target(column.dx(), column.dz(), size, flat,
          (ox, oz) -> TerraformAltarEntity.outerGround(level, center.getX() + ox, center.getZ() + oz));
      boolean untouched = true;
      for (int y = flat - 8; y <= flat + 10; y++) {
        BlockPos pos = new BlockPos(x, y, z);
        if (before.containsKey(pos) && !before.get(pos).equals(level.getBlockState(pos))) untouched = false;
      }
      boolean built = false;
      for (int y = target - 3; y <= target + 10; y++)
        if (TerraformAltarEntity.cell(level.getBlockState(new BlockPos(x, y, z))) == TerraformRules.Cell.BUILT) built = true;
      if (untouched && (built || !finished)) continue;
      BlockState top = level.getBlockState(new BlockPos(x, target, z));
      helper.assertTrue(LandWorks.ground(top), "Column " + column + " has no ground at its target " + target + ": " + top);
      for (int y = target + 1; y <= target + 10; y++) {
        BlockState above = level.getBlockState(new BlockPos(x, y, z));
        helper.assertTrue(above.isAir() || TerraformAltarEntity.cell(above) == TerraformRules.Cell.BUILT,
            "Column " + column + " kept natural blocks above its target: " + above);
      }
    }
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void terraformAltarFlattensWithLayersAndSlopeWithoutCreatingMatter(GameTestHelper helper) {
    terraformLand(helper);
    var level = helper.getLevel();
    var altar = terraform(helper, 4);
    BlockPos altarPos = altar.getBlockPos();
    BlockPos chestPos = at(helper, 18, 1, 22);
    level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
    ((ChestBlockEntity) level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5));
    BlockPos planks = at(helper, 22, 0, 18);
    level.setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
    altar.setItem(0, new ItemStack(Items.DIRT, 64));
    altar.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
    altar.addFuel(new ItemStack(Items.COAL, 4));
    BlockPos claimedFill = at(helper, 23, 0, 23), claimedCut = at(helper, 17, 3, 17);
    Consumer<BlockEvent.EntityPlaceEvent> placeClaim = veto(BlockEvent.EntityPlaceEvent.class, event -> {
      if (event.getPos().getX() == claimedFill.getX() && event.getPos().getZ() == claimedFill.getZ()) event.setCanceled(true);
    });
    Consumer<BlockEvent.BreakEvent> breakClaim = veto(BlockEvent.BreakEvent.class, event -> {
      if (event.getPos().getX() == claimedCut.getX() && event.getPos().getZ() == claimedCut.getZ()) event.setCanceled(true);
    });
    int matterBefore = matter(helper, altar);
    var before = snapshot(helper, -4, 12);
    try (var visitor = new Visitor(helper, altarPos)) {
      visitor.use(altarPos, ItemStack.EMPTY, false);
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.PREVIEW && changes(helper, before).isEmpty(),
          "The first use must only preview");
      visitor.use(altarPos, ItemStack.EMPTY, true);
      helper.assertTrue(altar.size() == 8, "Crouched use during the preview did not change the size");
      visitor.use(altarPos, ItemStack.EMPTY, true);
      visitor.use(altarPos, ItemStack.EMPTY, true);
      visitor.use(altarPos, ItemStack.EMPTY, true);
      helper.assertTrue(altar.size() == 4, "Sizes did not cycle back");
      visitor.use(altarPos, ItemStack.EMPTY, false);
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.RUNNING, "The second use did not start");
    }
    Set<Long> claimedColumns = Set.of(ChunkPos.asLong(claimedFill.getX(), claimedFill.getZ()),
        ChunkPos.asLong(claimedCut.getX(), claimedCut.getZ()));
    await(helper, () -> altar.state() == TerraformAltarEntity.State.WAITING
        || altar.state() == TerraformAltarEntity.State.DONE, 0, 1400, "Terraform until materials run out", () -> {
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.WAITING,
          "64 dirt and 16 cobblestone should not be enough for this land: " + altar.totals().filled);
      helper.assertTrue(matter(helper, altar) == matterBefore, "Waiting for materials left matter unaccounted");
      assertWholeColumns(helper, altar, before, false, claimedColumns);
      int supplied = matterBefore + 64;
      try (var visitor = new Visitor(helper, altarPos)) {
        visitor.use(altarPos, new ItemStack(Items.DIRT, 64), false);
        helper.assertTrue(visitor.player.getMainHandItem().isEmpty(), "The buffer did not take the supplied dirt");
      }
      await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 1400, "Supplied terraform",
          () -> finishTerraform(helper, altar, altarPos, before, supplied, claimedColumns, claimedFill, claimedCut,
              chestPos, planks, placeClaim, breakClaim));
    });
  }

  private static void finishTerraform(GameTestHelper helper, TerraformAltarEntity altar, BlockPos altarPos,
      Map<BlockPos, BlockState> before, int matterBefore, Set<Long> claimedColumns, BlockPos claimedFill,
      BlockPos claimedCut, BlockPos chestPos, BlockPos planks, Consumer<BlockEvent.EntityPlaceEvent> placeClaim,
      Consumer<BlockEvent.BreakEvent> breakClaim) {
    var level = helper.getLevel();
    {
      perf("terraform-flat", altar);
      helper.assertTrue(matter(helper, altar) == matterBefore,
          "Terraforming created or destroyed matter: " + matterBefore + " -> " + matter(helper, altar));
      assertWholeColumns(helper, altar, before, true, claimedColumns);
      int flat = altar.flatTop();
      for (int x = 16; x <= 24; x++)
        for (int z = 16; z <= 24; z++) {
          BlockPos top = new BlockPos(at(helper, x, 0, z).getX(), flat, at(helper, x, 0, z).getZ());
          if (x == 20 && z == 20 || x == 18 && z == 22 || x == 22 && z == 18 || top.getX() == claimedFill.getX() && top.getZ() == claimedFill.getZ()
              || top.getX() == claimedCut.getX() && top.getZ() == claimedCut.getZ()) continue;
          helper.assertTrue(level.getBlockState(top).is(Blocks.GRASS_BLOCK) && level.getBlockState(top.below()).is(Blocks.DIRT)
              && level.getBlockState(top.above()).isAir(), "Flat column " + x + "," + z + " lacks its layers");
        }
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
      helper.assertTrue(altar.totals().cut > 0 && altar.totals().filled > 0 && altar.totals().refused >= 4, "Unexpected totals");
      helper.assertTrue(noDrops(helper), "Terraforming dropped items");
      int matterDone = matter(helper, altar);
      var settled = snapshot(helper, -4, 12);
      int buffered = altar.bufferCount();
      try (var visitor = new Visitor(helper, altarPos)) {
        visitor.use(altarPos, ItemStack.EMPTY, false);
        visitor.use(altarPos, ItemStack.EMPTY, false);
      }
      await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE, 0, 400, "Repeated terraform", () -> {
        NeoForge.EVENT_BUS.unregister(placeClaim);
        NeoForge.EVENT_BUS.unregister(breakClaim);
        var repeated = changes(helper, settled);
        helper.assertTrue(altar.bufferCount() == buffered && matter(helper, altar) == matterDone && repeated.isEmpty(),
            "A repeated run changed settled land or the buffer: " + repeated.keySet());
        helper.succeed();
      });
    }
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 1600, skyAccess = true)
  public static void terraformAltarPausesOnWholeColumnsAndResumesAfterReload(GameTestHelper helper) {
    terraformLand(helper);
    var level = helper.getLevel();
    var altar = terraform(helper, 8);
    BlockPos altarPos = altar.getBlockPos();
    altar.setItem(0, new ItemStack(Items.DIRT, 64));
    altar.setItem(1, new ItemStack(Items.DIRT, 64));
    altar.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
    int matterBefore = matter(helper, altar);
    var before = snapshot(helper, -4, 12);
    altar.start(level);
    await(helper, () -> altar.state() == TerraformAltarEntity.State.WAITING, 0, 200, "Unfuelled terraform", () -> {
      helper.assertTrue(altar.cursor() == 0 && changes(helper, before).isEmpty() && altar.fuelUsed() == 0
          && !AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos),
          "The altar worked without fuel");
      altar.addFuel(new ItemStack(Items.CHARCOAL, 2));
    await(helper, () -> altar.cursor() > 120 || altar.state() == TerraformAltarEntity.State.DONE
        || altar.state() == TerraformAltarEntity.State.PAUSED, 0, 800, "Partial terraform", () -> {
      helper.assertTrue(altar.state() == TerraformAltarEntity.State.RUNNING, "Terraform stopped early: " + altar.state());
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
      helper.assertTrue(!AltarRegistry.isInsideActive(level, AltarType.TERRAFORM, altarPos),
          "A paused altar stayed registered");
      int cursor = altar.cursor();
      helper.assertTrue(matter(helper, altar) == matterBefore, "A pause left matter unaccounted");
      assertWholeColumns(helper, altar, before, false, Set.of());
      CompoundTag saved = altar.saveWithFullMetadata(level.registryAccess());
      int buffered = altar.bufferCount();
      level.removeBlockEntity(altarPos);
      BlockEntity loaded = BlockEntity.loadStatic(altarPos, level.getBlockState(altarPos), saved, level.registryAccess());
      level.setBlockEntity(loaded);
      var reloaded = (TerraformAltarEntity) level.getBlockEntity(altarPos);
      helper.assertTrue(reloaded != altar && reloaded.cursor() == cursor && reloaded.bufferCount() == buffered
          && reloaded.state() == TerraformAltarEntity.State.PAUSED && reloaded.flatTop() == altar.flatTop()
          && reloaded.palette().equals(altar.palette()) && reloaded.totals().cut == altar.totals().cut,
          "Terraform progress did not round-trip");
      try (var visitor = new Visitor(helper, altarPos)) {
        visitor.use(altarPos, ItemStack.EMPTY, false);
      }
      helper.assertTrue(reloaded.state() == TerraformAltarEntity.State.RUNNING, "Use did not resume");
      await(helper, () -> reloaded.state() == TerraformAltarEntity.State.DONE, 0, 1400, "Resumed terraform", () -> {
        perf("terraform-resumed", reloaded);
        helper.assertTrue(matter(helper, reloaded) == matterBefore, "Resuming created or destroyed matter");
        assertWholeColumns(helper, reloaded, before, true, Set.of());
        helper.assertTrue(noDrops(helper), "Terraforming dropped items");
        helper.succeed();
      });
    });
    });
  }
}
