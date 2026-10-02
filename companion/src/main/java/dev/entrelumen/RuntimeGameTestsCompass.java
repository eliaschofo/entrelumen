package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Isolated GameTests for the start of the game: empty inventory, the start ruin, the pedestal and
 * the Heliodor compass. Development-only; excluded from the distributable jar.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsCompass {
  private static final Logger LOGGER = LogUtils.getLogger();

  private RuntimeGameTestsCompass() {}

  /** A logged-in player whose inventory is left exactly as the login produced it. */
  private static ServerPlayer arrive(GameTestHelper helper, String name) {
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
        new GameProfile(UUID.randomUUID(), name), false);
    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
        cookie.gameProfile(), cookie.clientInformation());
    var connection = new net.minecraft.network.Connection(
        net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    return player;
  }

  private static boolean emptyInventory(ServerPlayer player) {
    var inventory = player.getInventory();
    return inventory.items.stream().allMatch(ItemStack::isEmpty)
        && inventory.armor.stream().allMatch(ItemStack::isEmpty)
        && inventory.offhand.stream().allMatch(ItemStack::isEmpty);
  }

  private static int compasses(ServerPlayer player) {
    int count = 0;
    for (var stack : player.getInventory().items)
      if (stack.is(HeliodorContent.COMPASS.get())) count += stack.getCount();
    return count;
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void newPlayerArrivesWithAnEmptyInventory(GameTestHelper helper) {
    var player = arrive(helper, "EmptyHands");
    helper.assertTrue(emptyInventory(player), "Login handed the player items");
    // Only the isolated server must stay without a start ruin: a real server (the full-pack QA
    // run) places it on its first start by design.
    helper.assertTrue(!(player.server instanceof GameTestServer)
            || RuinData.get(player.server).find(HeliodorRuins.START).isEmpty(),
        "The isolated GameTest world must never place the start ruin implicitly");
    // Tick-triggered advancements (a common gift route) fire on the player's first ticks.
    helper.runAfterDelay(10, () -> {
      helper.assertTrue(emptyInventory(player), "A tick after login handed the player items");
      helper.assertTrue(!CompassData.get(player.server).claimed.contains(player.getUUID()),
          "Arriving must not claim the compass: it waits on the pedestal");
      helper.succeed();
    });
  }

  /** Keeps the far test site generated while the start-ruin case waits for it; expires if abandoned. */
  private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> RUIN_SITE =
      net.minecraft.server.level.TicketType.create("entrelumen_qa_ruin_site",
          Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 1200);

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void startRuinIsAnchoredRegisteredAndPlacedOnce(GameTestHelper helper) {
    var level = helper.getLevel();
    var template = level.getStructureManager().get(HeliodorRuins.START).orElseThrow();
    var size = template.getSize();
    // Fresh terrain far from the test grid; the world registry stays untouched.
    var near = helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 0);
    // The site search reads every column within its radius. On the superflat world those chunks
    // cost nothing; on a full-pack noise world they are ~120 fresh chunks, far more than one
    // server tick may generate (a 60 s watchdog stop). A real start has its spawn area generated
    // before the ruin is placed, so the case asks worldgen for the area through a ticket first and
    // places the ruin once every chunk is ready.
    int reach = HeliodorRuins.SEARCH_RADIUS + Math.max(size.getX(), size.getZ()) / 2 + 2;
    int radius = (reach + 15) / 16;
    var site = new net.minecraft.world.level.ChunkPos(near);
    Runnable hold = () -> level.getChunkSource().addRegionTicket(RUIN_SITE, site, radius, site);
    hold.run();
    helper.startSequence()
        .thenWaitUntil(() -> {
          hold.run();
          for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
              helper.assertTrue(level.getChunkSource().getChunkNow(site.x + dx, site.z + dz) != null,
                  "Waiting for the ruin test site to generate");
        })
        .thenExecute(() -> {
          try {
            placeStartRuin(helper, level, size, near);
          } finally {
            level.getChunkSource().removeRegionTicket(RUIN_SITE, site, radius, site);
          }
        })
        .thenSucceed();
  }

  private static void placeStartRuin(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
      net.minecraft.core.Vec3i size, BlockPos near) {
    int x0 = near.getX() - size.getX() / 2, z0 = near.getZ() - size.getZ() / 2;
    // Load the column that is sampled: an unloaded one reads as the bottom of the world.
    level.getChunk((x0 + 1) >> 4, (z0 + 1) >> 4);
    int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        x0 + 1, z0 + 1) - 1;
    // A two-deep dip under the footprint must be filled by the foundation.
    level.setBlockAndUpdate(new BlockPos(x0 + 1, ground, z0 + 1), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(new BlockPos(x0 + 1, ground - 1, z0 + 1), Blocks.AIR.defaultBlockState());
    var data = new RuinData();
    data.pendingStart = true;
    var oldSpawn = level.getSharedSpawnPos();
    float oldAngle = level.getSharedSpawnAngle();
    var stair = EnvesData.get(level.getServer()).entrance;
    RuinData.Ruin ruin;
    try {
      ruin = HeliodorRuins.place(level, data, near, true).orElseThrow();
      helper.assertTrue(level.getSharedSpawnPos().equals(ruin.arrival()),
          "World spawn was not moved beside the ruin");
    } finally {
      level.setDefaultSpawnPos(oldSpawn, oldAngle);
    }
    helper.assertTrue(EnvesData.get(level.getServer()).entrance == stair,
        "A start ruin in a throwaway registry took over the world's Sealed Stair");
    var origin = ruin.origin();
    // The Sealed Stair reaches below the patio: the template sinks by its ground marker's layer.
    var template = level.getStructureManager().get(HeliodorRuins.START).orElseThrow();
    var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
    int sink = HeliodorRuins.groundLayer(template, settings);
    // Ground near the bottom of the world (the superflat test world: the stair is deeper than the
    // ground is high) lifts the template onto the world floor instead of cutting the stair off.
    int minBuild = level.getMinBuildHeight();
    int lift = Math.max(0, minBuild - (ground - sink));
    helper.assertTrue(origin.equals(new BlockPos(x0, ground - sink + lift, z0)) && origin.getY() >= minBuild,
        "Template floor was not anchored at ground level: " + origin + " (sink " + sink + ", lift " + lift + ")");
    // The Envés gate exists, inside the world and on the antechamber floor, and no step of the
    // Sealed Stair hangs over the void.
    BlockPos gate = null;
    for (var info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK))
      if (info.nbt() != null && EnvesEntrance.GATE_MARKER.equals(info.nbt().getString("metadata"))) gate = info.pos();
    helper.assertTrue(gate != null, "The start ruin template has no Envés gate marker");
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = 0; dy < 4; dy++)
        helper.assertTrue(level.isInWorldBounds(gate.offset(dx, dy, 0)), "An Envés gate cell lies outside the world");
    helper.assertTrue(!level.getBlockState(gate.below()).canBeReplaced(), "The Envés gate has no floor under it");
    int steps = 0;
    for (var info : template.filterBlocks(origin, settings, Blocks.TUFF_BRICK_STAIRS)) {
      steps++;
      helper.assertTrue(level.getBlockState(info.pos()).is(Blocks.TUFF_BRICK_STAIRS)
          && level.isInWorldBounds(info.pos().below()) && !level.getBlockState(info.pos().below()).canBeReplaced(),
          "A step of the Sealed Stair is missing or hangs over the void: " + info.pos());
    }
    helper.assertTrue(steps > 0, "The start ruin template has no Sealed Stair");
    helper.assertTrue(ruin.box().getXSpan() == size.getX() && ruin.box().getZSpan() == size.getZ()
        && ruin.box().maxY() == origin.getY() + size.getY() - 1,
        "Registered box does not match the template size " + size + ": " + ruin.box());
    helper.assertTrue(ruin.box().minY() <= ground - 1, "Foundation depth missing from the box");
    int cx = size.getX() / 2, cz = size.getZ() / 2;
    for (int x = 0; x < size.getX(); x++)
      for (int z = 0; z < size.getZ(); z++) {
        if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) continue; // the heart, over the stair's well
        var below = origin.offset(x, sink - 1, z);
        helper.assertTrue(!level.getBlockState(below).canBeReplaced(),
            "The ruin floats above " + below);
      }
    helper.assertTrue(ruin.pedestals().size() == 1
        && level.getBlockState(ruin.pedestals().getFirst()).is(HeliodorContent.PEDESTAL.get()),
        "The template pedestal was not found or registered");
    var arrival = ruin.arrival();
    boolean outside = arrival.getX() < ruin.box().minX() || arrival.getX() > ruin.box().maxX()
        || arrival.getZ() < ruin.box().minZ() || arrival.getZ() > ruin.box().maxZ();
    int gap = Math.max(Math.max(ruin.box().minX() - arrival.getX(), arrival.getX() - ruin.box().maxX()),
        Math.max(ruin.box().minZ() - arrival.getZ(), arrival.getZ() - ruin.box().maxZ()));
    helper.assertTrue(outside && gap == 1 && HeliodorRuins.standable(level, arrival),
        "Arrival point is not a safe spot beside the ruin: " + arrival);
    if (lift > 0) {
      // A lifted ruin's arrival step leads down to the ground by a tuff stair: every tread rests on
      // something, and the last one meets the ground.
      int treads = 0;
      BlockPos support = arrival.offset(0, -2, 1);
      while (level.getBlockState(support).is(Blocks.TUFF_STAIRS)) {
        helper.assertTrue(level.isInWorldBounds(support.below()) && !level.getBlockState(support.below()).canBeReplaced(),
            "A tread of the arrival stair hangs in the air: " + support);
        treads++;
        support = support.offset(0, -1, 1);
      }
      helper.assertTrue(treads > 0 && !level.getBlockState(support).canBeReplaced(),
          "The lifted ruin's arrival step has no stair down to the ground (" + treads + " treads)");
    }

    // Once per world: a second call returns the record and never rebuilds. The probe sits on the
    // floor layer (the template's corner is under ground and may be below this world's bottom).
    BlockPos probe = origin.above(sink);
    level.setBlockAndUpdate(probe, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    var again = HeliodorRuins.place(level, data, near.offset(40, 0, 40), true).orElseThrow();
    helper.assertTrue(again.equals(ruin) && data.ruins().size() == 1
        && level.getBlockState(probe).is(Blocks.MOSSY_COBBLESTONE),
        "The start ruin was placed twice");
    helper.assertTrue(level.getSharedSpawnPos().equals(oldSpawn), "The spawn moved on a repeat call");

    // Persistent: the registry survives a save/load with its box and ID.
    var reloaded = RuinData.load(data.save(new CompoundTag(), level.registryAccess()),
        level.registryAccess());
    helper.assertTrue(reloaded.ruins().equals(data.ruins()) && !reloaded.pendingStart(),
        "Ruin registry did not survive a reload");

    // Brand-new players appear at the arrival point; returning players are left alone.
    var newcomer = arrive(helper, "Newcomer");
    helper.assertTrue(HeliodorRuins.welcome(newcomer, ruin)
        && newcomer.blockPosition().equals(arrival), "A new player did not appear beside the ruin");
    var veteran = arrive(helper, "Veteran");
    veteran.getStats().setValue(veteran, Stats.CUSTOM.get(Stats.PLAY_TIME), 200);
    var before = veteran.blockPosition();
    helper.assertTrue(!HeliodorRuins.welcome(veteran, ruin) && veteran.blockPosition().equals(before),
        "A returning player was moved to the ruin");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void pedestalGivesACompassToEachPlayerWhoCarriesNone(GameTestHelper helper) {
    var pedestal = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pedestal, HeliodorContent.PEDESTAL.get().defaultBlockState());
    helper.assertTrue(helper.getLevel().getBlockState(pedestal)
        .getDestroySpeed(helper.getLevel(), pedestal) < 0, "The pedestal can be broken");
    var first = arrive(helper, "FirstFinder");
    var second = arrive(helper, "SecondFinder");
    var hit = new BlockHitResult(Vec3.atCenterOf(pedestal), Direction.UP, pedestal, false);
    for (var player : List.of(first, second)) {
      player.getInventory().clearContent();
      player.teleportTo(pedestal.getX() + 0.5, pedestal.getY() + 1, pedestal.getZ() + 1.5);
    }
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "The first player did not receive a compass");
    var stack = first.getInventory().items.stream()
        .filter(s -> s.is(HeliodorContent.COMPASS.get())).findFirst().orElseThrow();
    helper.assertTrue(stack.has(HeliodorContent.COMPASS_STATE.get()), "The compass arrived unattuned");
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "The pedestal duplicated a compass");
    // Holding something does not bypass the claim (the compass sits in another slot).
    first.getInventory().selected = 8;
    first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
    first.gameMode.useItemOn(first, helper.getLevel(), first.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "An item in hand bypassed the one-per-player rule");
    second.gameMode.useItemOn(second, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(second) == 1, "A later player could not take their own compass");
    // A lost compass is re-issued: a player who carries none gets a new one, attuned.
    first.getInventory().clearContent();
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "The pedestal did not replace a lost compass");
    helper.assertTrue(first.getInventory().items.stream().filter(s -> s.is(HeliodorContent.COMPASS.get()))
        .allMatch(s -> s.has(HeliodorContent.COMPASS_STATE.get())), "The replacement compass arrived unattuned");
    // ...but never a second one while the first is carried.
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "The pedestal handed a second compass to a player carrying one");
    // A dropped compass survives fire, lava, cactus and explosions.
    var dropped = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), pedestal.getX() + 0.5,
        pedestal.getY() + 3, pedestal.getZ() + 0.5, new ItemStack(HeliodorContent.COMPASS.get()));
    var sources = helper.getLevel().damageSources();
    for (var source : List.of(sources.lava(), sources.inFire(), sources.cactus(),
        sources.explosion(null, null), sources.generic()))
      helper.assertTrue(!dropped.hurt(source, 1000f) && !dropped.isRemoved(),
          "A dropped compass was destroyed by " + source.getMsgId());
    helper.assertTrue(dropped.getItem().has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT),
        "The compass is not fire resistant");
    var data = CompassData.get(first.server);
    var reloaded = CompassData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
        helper.getLevel().registryAccess());
    helper.assertTrue(reloaded.claimed.contains(first.getUUID()) && reloaded.claimed.contains(second.getUUID()),
        "Claims did not survive a reload");
    helper.succeed();
  }

  /**
   * The start ruin's site search runs inside ServerStartedEvent, under the dedicated server's
   * watchdog: it generates a bounded number of new chunks, however rough the terrain.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void startRuinSiteSearchKeepsToItsChunkBudget(GameTestHelper helper) {
    var level = helper.getLevel();
    var size = new net.minecraft.core.Vec3i(16, 8, 16);
    // Chunks nobody has loaded: far from the test grid and from every other case.
    int baseX = (helper.absolutePos(BlockPos.ZERO).getX() >> 4 << 4) - 24_576;
    int baseZ = (helper.absolutePos(BlockPos.ZERO).getZ() >> 4 << 4) + 12_288;
    var budget = new HeliodorRuins.SiteBudget(level, 3, Long.MAX_VALUE);
    for (int i = 0; i < 3; i++)
      helper.assertTrue(budget.afford(baseX + i * 64, baseZ, size), "The budget refused its own chunks");
    helper.assertTrue(!budget.afford(baseX + 3 * 64, baseZ, size) && budget.spent() && budget.newChunks() == 3,
        "The budget let a fourth new chunk through");
    var loaded = helper.absolutePos(BlockPos.ZERO);
    helper.assertTrue(!budget.afford(loaded.getX(), loaded.getZ(), new net.minecraft.core.Vec3i(1, 1, 1)),
        "A spent budget answered again");
    var late = new HeliodorRuins.SiteBudget(level, 64, -1);
    helper.assertTrue(!late.afford(loaded.getX(), loaded.getZ(), new net.minecraft.core.Vec3i(1, 1, 1)) && late.spent(),
        "The budget ignored its time limit");
    var free = new HeliodorRuins.SiteBudget(level, 0, Long.MAX_VALUE);
    helper.assertTrue(free.afford(loaded.getX(), loaded.getZ(), new net.minecraft.core.Vec3i(1, 1, 1)),
        "Chunks already loaded were counted as new");
    if (level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource)
      helper.assertTrue(HeliodorRuins.noiseSpread(level, baseX, baseZ, size) == 0,
          "The noise screen saw relief on a flat world");

    // The whole search, on the real template size, from an area nobody has generated.
    var template = level.getStructureManager().get(HeliodorRuins.START).orElseThrow().getSize();
    var near = new BlockPos(baseX, 0, baseZ + 4096);
    int reach = HeliodorRuins.SEARCH_RADIUS + Math.max(template.getX(), template.getZ()) / 2 + 32;
    int fromX = (near.getX() - reach) >> 4, toX = (near.getX() + reach) >> 4;
    int fromZ = (near.getZ() - reach) >> 4, toZ = (near.getZ() + reach) >> 4;
    int before = 0;
    for (int cx = fromX; cx <= toX; cx++)
      for (int cz = fromZ; cz <= toZ; cz++) if (level.getChunkSource().getChunkNow(cx, cz) != null) before++;
    long started = System.nanoTime();
    var site = HeliodorRuins.chooseSite(level, near, template);
    long millis = (System.nanoTime() - started) / 1_000_000L;
    int after = 0;
    for (int cx = fromX; cx <= toX; cx++)
      for (int cz = fromZ; cz <= toZ; cz++) if (level.getChunkSource().getChunkNow(cx, cz) != null) after++;
    LOGGER.info("Start ruin site search from fresh terrain: {} new chunks in {} ms, site {}", after - before, millis,
        site);
    // The budget, plus the column the fallback reads at the centre.
    helper.assertTrue(after - before <= HeliodorRuins.MAX_NEW_CHUNKS + 1,
        "The site search generated " + (after - before) + " new chunks");
    helper.assertTrue(Math.abs(site.getX() + template.getX() / 2 - near.getX()) <= HeliodorRuins.SEARCH_RADIUS
        && Math.abs(site.getZ() + template.getZ() / 2 - near.getZ()) <= HeliodorRuins.SEARCH_RADIUS,
        "The site search left its radius: " + site);
    helper.succeed();
  }

  /** Anchors are known points: a registered ruin is found however far from the holder it lies. */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void anchorTargetsPointAtARuinAtAnyDistance(GameTestHelper helper) {
    var player = arrive(helper, "FarAnchor");
    var level = helper.getLevel();
    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("entrelumen",
        "qa_far_anchor_" + Long.toHexString(player.getUUID().getMostSignificantBits() & 0xffffffL));
    var origin = helper.absolutePos(BlockPos.ZERO).offset(CompassTargets.MAX_RADIUS * 3, 0, 0);
    var ruin = new RuinData.Ruin(id, id, Level.OVERWORLD, 1,
        new net.minecraft.world.level.levelgen.structure.BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
            origin.getX() + 8, origin.getY() + 8, origin.getZ() + 8),
        origin, origin.above(), List.of(), level.getGameTime());
    var registry = RuinData.get(level.getServer());
    registry.add(ruin);
    try {
      var list = List.of(objective("qa_far_anchor", 1, CompassTargets.Kind.STRUCTURE,
          new CompassTargets.Target(CompassTargets.TargetType.ANCHOR, id.toString(), "minecraft:overworld", null,
              CompassTargets.MIN_RADIUS),
          new CompassTargets.Condition(CompassTargets.ConditionType.MILESTONE, "qa_never", 1)));
      var state = HeliodorCompass.compute(player, list);
      helper.assertTrue(state.state() == CompassState.POINTING
          && state.target().equals(Optional.of(GlobalPos.of(Level.OVERWORLD, ruin.center()))),
          "An anchor " + (CompassTargets.MAX_RADIUS * 3) + " blocks away was not pointed at: " + state);
    } finally {
      registry.remove(id);
      StructureProtection.invalidate(level.getServer());
    }
    helper.succeed();
  }

  /**
   * Dedicated-server spawn protection leaves the shared ruin blocks to everyone: the exempt tag
   * holds the pedestal, the Envés gate and seals and the Solsticio portal, only ENTRELUMEN blocks,
   * all unbreakable, and each with its own use handler so the click never falls through to the item
   * (a block placed, a bucket poured or a fire lit beside it).
   */
  @GameTest(template = "empty", timeoutTicks = 20)
  public static void spawnProtectionExemptsOnlyTheSharedUnbreakableBlocks(GameTestHelper helper) {
    var tag = HeliodorRuins.SPAWN_PROTECTION_EXEMPT;
    for (var block : List.of(HeliodorContent.PEDESTAL.get(), Enves.GATE.get(), Enves.SEAL.get(),
        Solsticio.PORTAL.get(), RuinContent.PEDESTAL.get()))
      helper.assertTrue(block.defaultBlockState().is(tag), block + " is not exempt from spawn protection");
    for (var block : List.of(RuinContent.LOCK.get(), RuinContent.GATE.get()))
      helper.assertTrue(!block.defaultBlockState().is(tag), block + " has no use of its own but is exempt");
    int count = 0;
    for (var holder : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
      count++;
      var key = holder.unwrapKey().orElseThrow().location();
      helper.assertTrue(key.getNamespace().equals("entrelumen"), key + " is not an ENTRELUMEN block");
      helper.assertTrue(holder.value().defaultDestroyTime() < 0, key + " can be broken inside spawn protection");
      helper.assertTrue(answersClicks(holder.value().getClass()), key + " has no use handler of its own");
    }
    helper.assertTrue(count >= 6, "The spawn protection tag lost entries: " + count);
    helper.succeed();
  }

  /** Whether a block class below {@link net.minecraft.world.level.block.Block} overrides useItemOn or useWithoutItem. */
  private static boolean answersClicks(Class<?> type) {
    for (Class<?> c = type; c != null && c != net.minecraft.world.level.block.Block.class; c = c.getSuperclass())
      for (var method : c.getDeclaredMethods())
        if (method.getName().equals("useItemOn") || method.getName().equals("useWithoutItem")) return true;
    return false;
  }

  private static CompassTargets.Objective objective(String id, int act, CompassTargets.Kind kind,
      CompassTargets.Target target, CompassTargets.Condition condition) {
    return new CompassTargets.Objective(id, act, kind, target, condition, true);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void compassMovesToTheNextObjectiveWhenTheTeamMeetsTheCondition(GameTestHelper helper)
      throws Exception {
    var founder = arrive(helper, "CompassLead");
    var guest = arrive(helper, "CompassGuest");
    founder.getInventory().clearContent();
    guest.getInventory().clearContent();
    var marker = helper.absolutePos(new BlockPos(2, 1, 2));
    var list = List.of(
        objective("qa_marker", 1, CompassTargets.Kind.ARTIFACT,
            new CompassTargets.Target(CompassTargets.TargetType.POSITION, "", "minecraft:overworld", marker, 0),
            new CompassTargets.Condition(CompassTargets.ConditionType.ITEM, "minecraft:amethyst_shard", 2)),
        objective("qa_end", 1, CompassTargets.Kind.BOSS,
            new CompassTargets.Target(CompassTargets.TargetType.DIMENSION, "", "minecraft:the_end", null, 0),
            new CompassTargets.Condition(CompassTargets.ConditionType.MILESTONE, "qa_compass_gate", 1)),
        objective("qa_act_two", 2, CompassTargets.Kind.STRUCTURE,
            new CompassTargets.Target(CompassTargets.TargetType.POSITION, "", "minecraft:overworld", marker, 0),
            new CompassTargets.Condition(CompassTargets.ConditionType.MILESTONE, "qa_never", 1)));

    var state = HeliodorCompass.compute(founder, list);
    helper.assertTrue(state.objective().equals("qa_marker") && state.state() == CompassState.POINTING
        && state.target().equals(Optional.of(GlobalPos.of(Level.OVERWORLD, marker)))
        && state.kind() == 2 && state.dimension() == 0, "First objective not shown: " + state);
    founder.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 1));
    helper.assertTrue(HeliodorCompass.compute(founder, list).objective().equals("qa_marker"),
        "Advanced before the full key-item count");
    founder.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 1));
    state = HeliodorCompass.compute(founder, list);
    helper.assertTrue(state.objective().equals("qa_end") && state.state() == CompassState.ELSEWHERE
        && state.dimension() == 2 && state.kind() == 1 && !state.spinning(),
        "Meeting the condition did not move the compass: " + state);
    founder.getInventory().clearContent();
    helper.assertTrue(HeliodorCompass.compute(founder, list).objective().equals("qa_end"),
        "Dropping the key item rewound the compass");

    // A party starts from its founder's progress, and every member follows the same objective.
    var team = FTBTeamsAPI.api().getManager().createPartyTeam(founder, "Compass " + founder.getUUID(),
        "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    ((dev.ftb.mods.ftbteams.data.PartyTeam) team).invite(founder, List.of(guest.getGameProfile()));
    ((dev.ftb.mods.ftbteams.data.PartyTeam) team).join(guest);
    helper.assertTrue(HeliodorCompass.compute(guest, list).objective().equals("qa_end"),
        "A team member does not share the team objective");
    Entrelumen.current(guest).completed.add("qa_compass_gate");
    state = HeliodorCompass.compute(founder, list);
    helper.assertTrue(state.objective().equals("qa_act_two") && state.state() == CompassState.LOCKED
        && !state.spinning(), "A later act's objective was not held: " + state);
    Entrelumen.current(founder).act = 2;
    state = HeliodorCompass.compute(guest, list);
    helper.assertTrue(state.objective().equals("qa_act_two") && state.state() == CompassState.POINTING,
        "Opening the act did not reveal the next objective: " + state);
    helper.assertTrue(CompassData.get(founder.server).reached(team.getId(), null)
        .containsAll(List.of("qa_marker", "qa_end")), "Reached objectives were not persisted");

    // The shipped draft (26 September): the Signal Tower first, where the Atlas waits; visiting it
    // moves the compass on, and the awakened Atlas passes the start ruin. The isolated world has no
    // tower yet (NOT_FOUND); a full-pack server places it when the first team opens act I, and the
    // needle then points at it. Either way it never spins.
    var solo = arrive(helper, "DraftReader");
    var draft = HeliodorCompass.compute(solo);
    var tower = RuinData.get(solo.server).find(net.minecraft.resources.ResourceLocation.parse("entrelumen:signal_tower"))
        .filter(r -> r.dimension().equals(Level.OVERWORLD)
            && CompassData.horizontalDistanceSqr(r.center(), solo.blockPosition())
                <= (long) CompassTargets.DEFAULT_RADIUS * CompassTargets.DEFAULT_RADIUS);
    boolean drafted = tower.isPresent()
        ? draft.state() == CompassState.POINTING
            && draft.target().equals(Optional.of(GlobalPos.of(Level.OVERWORLD, tower.get().center())))
        : draft.state() == CompassState.NOT_FOUND;
    helper.assertTrue(draft.objective().equals("signal_tower") && drafted && !draft.spinning(),
        "Draft does not start at the Signal Tower, or spins without it: " + draft);
    var soloContext = HeliodorCompass.context(solo).orElseThrow();
    RuinProgress.get(solo.server).team(soloContext.campaignId(), soloContext.founder()).visited.add("entrelumen:signal_tower");
    Entrelumen.current(solo).completed.add("atlas_awakened");
    helper.assertTrue(HeliodorCompass.compute(solo).objective().equals("village_survey"),
        "The visited tower and the awakened Atlas did not move the draft compass on");
    // The carried item follows the same state.
    var carried = new ItemStack(HeliodorContent.COMPASS.get());
    HeliodorCompass.refresh(solo, carried);
    var carriedState = carried.get(HeliodorContent.COMPASS_STATE.get());
    helper.assertTrue(carriedState != null && carriedState.objective().equals("village_survey"),
        "The item component does not mirror the objective");
    var view = HeliodorCompass.view(solo);
    helper.assertTrue(view.objective().equals("village_survey") && view.lore().equals(List.of("signal_tower", "heliodor_ruin"))
        && view.dimension().equals("minecraft:overworld"), "Atlas view is wrong: " + view);
    var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
        helper.getLevel().registryAccess());
    try {
      var snapshot = AtlasNetwork.snapshot(solo, true, "");
      AtlasNetwork.Snapshot.CODEC.encode(buffer, snapshot);
      helper.assertTrue(AtlasNetwork.Snapshot.CODEC.decode(buffer).equals(snapshot),
          "The Atlas compass section does not survive the wire");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }

  /**
   * Measures bounded searches. On the isolated superflat world (structure starts disabled, cost
   * test bypassing that option) every predicted plains village is confirmed and rejected: the worst
   * case. On a full-pack server the same searches run on real terrain, where the village may exist.
   * Either way the search stays within the radius, never blocks a tick on worldgen and answers an
   * impossible target without searching. Numbers are logged for the design doc.
   */
  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void compassSearchesStayBoundedAndCooperative(GameTestHelper helper) {
    var level = helper.getLevel();
    var locator = CompassLocator.of(level.getServer());
    var origin = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 8192);
    boolean flat = level.getChunkSource().getGenerator()
        instanceof net.minecraft.world.level.levelgen.FlatLevelSource;
    // The biome under the origin, sampled like BiomeJob does (plains on the superflat world).
    var sampled = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
        net.minecraft.core.QuartPos.fromBlock(origin.getX()),
        net.minecraft.core.QuartPos.fromBlock(origin.getY()),
        net.minecraft.core.QuartPos.fromBlock(origin.getZ()),
        level.getChunkSource().randomState().sampler());
    String underfoot = sampled.unwrapKey().orElseThrow().location().toString();
    long radiusSqr = (long) CompassTargets.MAX_RADIUS * CompassTargets.MAX_RADIUS;
    Map<String, CompassLocator.Outcome> outcomes = new LinkedHashMap<>();
    Map<String, CompassTargets.Target> targets = new LinkedHashMap<>();
    targets.put("village_plains", new CompassTargets.Target(CompassTargets.TargetType.STRUCTURE,
        "minecraft:village_plains", "minecraft:overworld", null, CompassTargets.MAX_RADIUS));
    // An End biome never occurs in an overworld biome source, flat or noise.
    targets.put("absent", new CompassTargets.Target(CompassTargets.TargetType.BIOME,
        "minecraft:the_end", "minecraft:overworld", null, CompassTargets.MAX_RADIUS));
    targets.put("underfoot", new CompassTargets.Target(CompassTargets.TargetType.BIOME,
        underfoot, "minecraft:overworld", null, CompassTargets.MAX_RADIUS));
    AtomicReference<String> failure = new AtomicReference<>();
    targets.forEach((name, target) -> {
      // Ignore the flat world's "no structures" option to measure the real scan cost.
      if (!locator.request("qa-cost-" + name + "-" + origin.toShortString(), level, target, origin, false,
          outcome -> outcomes.put(name, outcome))) failure.set("Search was not queued: " + name);
    });
    helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
    // A structure that cannot generate in this dimension (End cities need End biomes) costs no
    // search at all. The superflat world also lacks snowy villages, a full-pack overworld does not.
    var immediate = new AtomicReference<CompassLocator.Outcome>();
    boolean queued = locator.request("qa-cost-impossible-" + origin.toShortString(), level,
        new CompassTargets.Target(CompassTargets.TargetType.STRUCTURE, "minecraft:end_city",
            "minecraft:overworld", null, CompassTargets.MAX_RADIUS), origin, false, immediate::set);
    helper.assertTrue(!queued && immediate.get() != null && immediate.get().found().isEmpty(),
        "An impossible structure was searched");
    helper.succeedWhen(() -> {
      helper.assertTrue(outcomes.size() == targets.size(), "Searches still running");
      outcomes.forEach((name, outcome) -> LOGGER.info("COMPASS COST {} ({}): found={} {}", name,
          flat ? "superflat" : "full pack", outcome.found().map(BlockPos::toShortString).orElse("none"),
          outcome.cost()));
      var plains = outcomes.get("village_plains");
      if (flat)
        helper.assertTrue(plains.cost().candidates() > 0 && plains.found().isEmpty()
            && plains.cost().positions() == 189 * 189,
            "Plains villages must be predicted, never started, over the whole 1500-block square: "
                + plains.cost());
      else
        helper.assertTrue(plains.found().map(pos -> CompassData.horizontalDistanceSqr(origin, pos) <= radiusSqr)
                .orElse(plains.cost().positions() == 189 * 189),
            "A village search left the 1500-block radius or stopped early: " + plains.found() + " "
                + plains.cost());
      var absent = outcomes.get("absent");
      helper.assertTrue(absent.found().isEmpty() && absent.cost().positions() == 93 * 93,
          "An absent biome must sample the whole radius: " + absent.cost());
      var near = outcomes.get("underfoot");
      helper.assertTrue(near.found().isPresent() && near.cost().ticks() == 1,
          "The biome under the player must be found at once: " + underfoot + " " + near.cost());
      for (var outcome : outcomes.values())
        helper.assertTrue(outcome.cost().maxStepNanos() < 250_000_000L,
            "A single search step blocked the server tick: " + outcome.cost());
    });
  }
}
