package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated Solsticio GameTests; excluded from the distributable jar by the RuntimeGameTests*
 * pattern. The city tests share the one dimension of the test server (the fixture mod restores
 * datapack dimensions there); the protection tests use a transient region over their own 5×5×5
 * test volume, with a claimed plot strip at x = 3..4.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsSolsticio {
  private RuntimeGameTestsSolsticio() {}

  /** A real server player on an in-memory connection, with its own FTB team and campaign. */
  private static final class QaPlayer implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    QaPlayer(GameTestHelper helper, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      try {
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    public void close() {
      try {
        connection.disconnect(net.minecraft.network.chat.Component.literal("Solsticio QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static void unlock(ServerPlayer player, boolean arkActivated) {
    var campaign = Entrelumen.current(player);
    campaign.act = arkActivated ? 6 : 1;
    if (arkActivated) campaign.completed.add(CampaignMilestones.LAST_HORIZON);
    else campaign.completed.remove(CampaignMilestones.LAST_HORIZON);
    CampaignData.get(player.server).setDirty();
  }

  /** Runs the body once, on the first tick the shared city is ready; then succeeds. */
  private static void whenCityReady(GameTestHelper helper, Runnable body) {
    var server = helper.getLevel().getServer();
    helper.assertTrue(server.getLevel(Solsticio.LEVEL) != null, "The Solsticio dimension is missing");
    AtomicBoolean done = new AtomicBoolean();
    helper.onEachTick(() -> {
      if (done.get() || !SolsticioCity.ensure(server)) return;
      done.set(true);
      body.run();
      helper.succeed();
    });
  }

  private static BlockHitResult top(BlockPos pos) {
    return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
  }

  private static ItemStack returnKey(ServerPlayer owner) {
    ItemStack key = new ItemStack(Solsticio.LIGHT_KEY_BROKEN.get());
    key.set(Solsticio.KEY_OWNER, new Solsticio.KeyOwner(owner.getUUID(), owner.getGameProfile().getName()));
    return key;
  }

  // ---- Dimension and city -----------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void solsticioDimensionAndProvisionalCityArePlacedOnce(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    whenCityReady(helper, () -> {
      ServerLevel level = server.getLevel(Solsticio.LEVEL);
      var type = level.dimensionType();
      helper.assertTrue(type.fixedTime().orElse(-1) == 6000 && type.hasSkyLight() && !type.natural(),
          "Solsticio is not a fixed, sky-lit noon");
      helper.assertTrue(level.getChunkSource().getGenerator() instanceof FlatLevelSource,
          "Solsticio does not use the empty generator");
      var data = SolsticioData.get(server);
      helper.assertTrue(data.placements == 1, "City placed " + data.placements + " times");
      helper.assertTrue(SolsticioCity.ensure(server) && data.placements == 1 && !SolsticioCity.isPlacing(server),
          "A second request placed the city again");
      helper.assertTrue(data.plots.size() == 4, "Expected 4 plots, found " + data.plots.size());
      helper.assertTrue(data.npcs.keySet().equals(Set.of("mayor", "inventor", "gardener", "priest")),
          "NPC points: " + data.npcs.keySet());
      helper.assertTrue(data.tradingHall != null && data.waystone != null && data.portal != null,
          "Trading hall, waystone or portal marker missing");
      helper.assertTrue(level.getBlockState(data.portal).is(Solsticio.PORTAL.get()), "No portal on its marker");
      helper.assertTrue(level.getBlockState(data.portal.below()).isFaceSturdy(level, data.portal.below(), Direction.UP),
          "The town hall floor is not under the portal");
      helper.assertTrue(SolsticioTravel.standable(level, data.arrival), "The arrival point is not standable");
      helper.assertTrue(level.getBlockState(data.npcs.get("mayor")).isAir(), "A data marker was left in the city");
      for (var plot : data.plots)
        helper.assertTrue(!level.getBlockState(plot.corner.below()).isAir() && level.getBlockState(plot.corner).isAir(),
            "Plot marker not on the first free layer");
      helper.assertTrue(data.borderRadius >= CityLayout.MIN_BORDER_RADIUS
          && level.getWorldBorder().getSize() == data.borderRadius * 2.0, "Solsticio is not bounded");
      helper.assertTrue(level.getBlockState(new BlockPos(data.borderRadius - 4, CityLayout.BASE_Y + 4, 0)).isAir(),
          "Terrain outside the city");
      var biome = level.getBiome(data.arrival).value();
      helper.assertTrue(!biome.hasPrecipitation() && biome.getMobSettings().getMobs(MobCategory.MONSTER).isEmpty(),
          "The biome has weather or hostile spawns");
      var zombie = EntityType.ZOMBIE.create(level);
      zombie.moveTo(Vec3.atBottomCenterOf(data.arrival));
      helper.assertTrue(!EventHooks.checkSpawnPosition(zombie, level, MobSpawnType.NATURAL),
          "A hostile mob may spawn naturally");
      helper.assertTrue(StructureProtection.isGuarded(level, data.arrival), "The city is not protected");
    });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void solsticioIgnoresOverworldWeather(GameTestHelper helper) {
    ServerLevel level = helper.getLevel().getServer().getLevel(Solsticio.LEVEL);
    helper.assertTrue(level != null, "The Solsticio dimension is missing");
    level.setRainLevel(0.8F);
    level.setThunderLevel(0.6F);
    helper.runAfterDelay(3, () -> {
      helper.assertTrue(level.getRainLevel(1.0F) == 0.0F && level.getThunderLevel(1.0F) == 0.0F && !level.isRaining(),
          "Rain reached the eternal light");
      helper.succeed();
    });
  }

  // ---- Protection -------------------------------------------------------------------------

  /** Protected 5×5×5 test volume; x = 3..4 is a plot claimed by {@code owner}. */
  private static ProtectionRules.Region isolatedRegion(GameTestHelper helper, String name, UUID owner) {
    BlockPos a = helper.absolutePos(BlockPos.ZERO), b = helper.absolutePos(new BlockPos(4, 4, 4));
    BlockPos p = helper.absolutePos(new BlockPos(3, 0, 0)), q = helper.absolutePos(new BlockPos(4, 4, 4));
    var box = new ProtectionRules.Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
        Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
        Math.max(a.getZ(), b.getZ()));
    var plot = new ProtectionRules.Box(Math.min(p.getX(), q.getX()), Math.min(p.getY(), q.getY()),
        Math.min(p.getZ(), q.getZ()), Math.max(p.getX(), q.getX()), Math.max(p.getY(), q.getY()),
        Math.max(p.getZ(), q.getZ()));
    return new ProtectionRules.Region("entrelumen:qa_" + name + "_" + a.toShortString(),
        helper.getLevel().dimension().location().toString(), box, Solsticio.GATE,
        List.of(new ProtectionRules.Hole("plot", plot, owner, true)));
  }

  private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
    return helper.absolutePos(new BlockPos(x, y, z));
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void protectionRefusesBreakingAndPlacingButFreesTheTeamPlot(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var level = helper.getLevel();
    try (var qa = new QaPlayer(helper, "GuardQA"); var other = new QaPlayer(helper, "NeighbourQA")) {
      var player = qa.player;
      var region = isolatedRegion(helper, "break", CampaignActions.campaignId(player));
      StructureProtection.addTransient(server, region);
      try {
        BlockPos guarded = at(helper, 1, 1, 1), plot = at(helper, 3, 1, 1);
        level.setBlockAndUpdate(guarded, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(plot, Blocks.STONE.defaultBlockState());
        player.teleportTo(guarded.getX() + 0.5, guarded.getY() + 1, guarded.getZ() + 2.5);
        helper.assertTrue(!player.gameMode.destroyBlock(guarded) && level.getBlockState(guarded).is(Blocks.STONE),
            "A guarded block broke");
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(!player.gameMode.destroyBlock(guarded) && level.getBlockState(guarded).is(Blocks.STONE),
            "Creative mode broke a guarded block");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(!other.player.gameMode.destroyBlock(plot) && level.getBlockState(plot).is(Blocks.STONE),
            "Another team broke the plot");
        helper.assertTrue(player.gameMode.destroyBlock(plot) && level.getBlockState(plot).isAir(),
            "The owner could not break in their plot");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, top(guarded));
        helper.assertTrue(level.getBlockState(guarded.above()).isAir() && player.getMainHandItem().getCount() == 4,
            "A block was placed on a guarded position");
        BlockPos ground = at(helper, 4, 1, 2);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, top(ground));
        helper.assertTrue(level.getBlockState(ground.above()).is(Blocks.STONE) && player.getMainHandItem().getCount() == 3,
            "The owner could not build in their plot");
        // A bucket aimed at a guarded block pours nothing.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, top(guarded));
        helper.assertTrue(level.getFluidState(guarded.above()).isEmpty(), "Water was poured on a guarded block");
      } finally {
        StructureProtection.removeTransient(server, region.id());
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void protectionStopsExplosionsPistonsAndIncomingFluids(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var level = helper.getLevel();
    var region = isolatedRegion(helper, "environment", UUID.randomUUID());
    StructureProtection.addTransient(server, region);
    floor(helper);
    BlockPos guardedDirt = at(helper, 1, 1, 1), plotDirt = at(helper, 3, 1, 1);
    level.setBlockAndUpdate(guardedDirt, Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(plotDirt, Blocks.DIRT.defaultBlockState());
    // A frame hanging on the guarded dirt's south face.
    var frame = new ItemFrame(level, at(helper, 1, 1, 2), Direction.SOUTH);
    frame.setItem(new ItemStack(Items.DIAMOND), false);
    level.addFreshEntity(frame);
    level.explode(null, guardedDirt.getX() + 1.5, guardedDirt.getY() + 0.5, guardedDirt.getZ() + 0.5, 2.0F,
        Level.ExplosionInteraction.TNT);
    helper.assertTrue(level.getBlockState(guardedDirt).is(Blocks.DIRT), "An explosion broke a guarded block");
    helper.assertTrue(level.getBlockState(plotDirt).isAir(), "The explosion did not reach the claimed plot");
    helper.assertTrue(frame.isAlive() && frame.getItem().is(Items.DIAMOND), "An explosion broke a guarded frame");
    floor(helper);

    // A piston in the plot pushing into the structure; a control piston pushing inside the plot.
    BlockPos blocked = at(helper, 3, 1, 4), target = at(helper, 2, 1, 4);
    level.setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(blocked, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
    BlockPos control = at(helper, 4, 1, 4), pushed = at(helper, 4, 1, 3);
    level.setBlockAndUpdate(pushed, Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(control, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.NORTH));
    level.setBlockAndUpdate(blocked.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
    level.setBlockAndUpdate(control.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
    // Water poured in the plot beside a guarded gap.
    BlockPos source = at(helper, 3, 1, 2), guardedGap = at(helper, 2, 1, 2);
    level.setBlockAndUpdate(guardedGap, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(at(helper, 3, 1, 3), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
    helper.runAfterDelay(30, () -> {
      try {
        helper.assertTrue(level.getBlockState(target).is(Blocks.DIRT) && level.getBlockState(target.west()).isAir()
            && !level.getBlockState(blocked).getValue(PistonBaseBlock.EXTENDED), "A piston moved a guarded block");
        helper.assertTrue(level.getBlockState(pushed.north()).is(Blocks.DIRT),
            "The control piston could not push inside the plot");
        helper.assertTrue(level.getFluidState(guardedGap).isEmpty(), "Water flowed from the plot into the structure");
        helper.assertTrue(!level.getFluidState(at(helper, 3, 1, 3)).isEmpty(), "Water did not spread inside the plot");
      } finally {
        StructureProtection.removeTransient(server, region.id());
        frame.discard();
      }
      helper.succeed();
    });
  }

  private static void floor(GameTestHelper helper) {
    for (int x = 0; x <= 4; x++)
      for (int z = 0; z <= 4; z++)
        helper.getLevel().setBlockAndUpdate(at(helper, x, 0, z), Blocks.STONE.defaultBlockState());
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void protectionLocksContainersAndFramesUntilTheTeamReachesTheAct(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var level = helper.getLevel();
    try (var qa = new QaPlayer(helper, "SealQA")) {
      var player = qa.player;
      var region = isolatedRegion(helper, "gate", UUID.randomUUID());
      StructureProtection.addTransient(server, region);
      ItemFrame frame = null;
      try {
        unlock(player, false);
        BlockPos chest = at(helper, 1, 1, 1);
        level.setBlockAndUpdate(at(helper, 1, 0, 1), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        player.teleportTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 2.5);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(chest));
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "A locked team opened a guarded chest");
        player.setGameMode(GameType.SPECTATOR);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(chest));
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "A spectator looked into a guarded chest");
        player.setGameMode(GameType.SURVIVAL);
        // Free blocks work before the act.
        BlockPos lever = at(helper, 2, 1, 3);
        level.setBlockAndUpdate(lever.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(lever));
        helper.assertTrue(level.getBlockState(lever).getValue(LeverBlock.POWERED), "A lever was locked");
        // Frames: nothing leaves them while locked (hung beside the chest, never above it).
        level.setBlockAndUpdate(at(helper, 0, 1, 1), Blocks.STONE.defaultBlockState());
        frame = new ItemFrame(level, at(helper, 0, 1, 2), Direction.SOUTH);
        frame.setItem(new ItemStack(Items.EMERALD), false);
        level.addFreshEntity(frame);
        helper.assertTrue(!frame.hurt(level.damageSources().playerAttack(player), 1.0F) && frame.getItem().is(Items.EMERALD),
            "A locked frame gave up its item");

        unlock(player, true);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(chest));
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "The unlocked team could not open the chest");
        player.closeContainer();
        helper.assertTrue(!player.gameMode.destroyBlock(chest) && level.getBlockState(chest).is(Blocks.CHEST),
            "Unlocking allowed breaking");
        frame.hurt(level.damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(frame.getItem().isEmpty() && frame.isAlive(), "The unlocked team could not take the frame's item");
        helper.assertTrue(!frame.hurt(level.damageSources().playerAttack(player), 1.0F) && frame.isAlive(),
            "An empty guarded frame broke");
      } finally {
        StructureProtection.removeTransient(server, region.id());
        if (frame != null) frame.discard();
      }
    }
    helper.succeed();
  }

  /**
   * The start ruin, placed far from the test grid into a local registry, is protected through the
   * same mapping the ruin provider uses: its box (foundation and terrain corners included) cannot
   * be broken or blown up, while its pedestal still hands out the compass.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void startRuinIsProtectedButItsPedestalStaysUsable(GameTestHelper helper) {
    var level = helper.getLevel();
    var server = level.getServer();
    var data = new RuinData();
    var ruin = HeliodorRuins.place(level, data, helper.absolutePos(BlockPos.ZERO).offset(0, 0, 4096), false)
        .orElseThrow();
    var regions = StructureProtection.ruinRegions(data);
    helper.assertTrue(regions.size() == 1 && regions.getFirst().box().equals(StructureProtection.box(ruin.box()))
        && regions.getFirst().gate().equals(ProtectionRules.ActGate.act(1))
        && regions.getFirst().dimension().equals(level.dimension().location().toString()),
        "The ruin region does not match the registered ruin");
    var mapped = regions.getFirst();
    var region = new ProtectionRules.Region("entrelumen:qa_ruin_" + ruin.origin().toShortString(), mapped.dimension(),
        mapped.box(), mapped.gate(), mapped.holes());
    StructureProtection.addTransient(server, region);
    try (var qa = new QaPlayer(helper, "RuinQA")) {
      var player = qa.player;
      var arrival = ruin.arrival();
      player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
      BlockPos pedestal = ruin.pedestals().getFirst();
      BlockPos floor = pedestal.below();
      helper.assertTrue(!player.gameMode.destroyBlock(floor) && !level.getBlockState(floor).isAir(),
          "The ruin floor broke");
      helper.assertTrue(!player.gameMode.destroyBlock(pedestal)
          && level.getBlockState(pedestal).is(HeliodorContent.PEDESTAL.get()), "The pedestal broke");
      var box = ruin.box();
      BlockPos foundation = null, corner = new BlockPos(box.minX(), ruin.origin().getY(), box.minZ());
      for (int x = box.minX(); x <= box.maxX() && foundation == null; x++)
        for (int z = box.minZ(); z <= box.maxZ() && foundation == null; z++) {
          BlockPos candidate = new BlockPos(x, box.minY(), z);
          if (box.minY() < ruin.origin().getY() && !level.getBlockState(candidate).isAir()) foundation = candidate;
        }
      if (foundation != null)
        helper.assertTrue(!player.gameMode.destroyBlock(foundation) && !level.getBlockState(foundation).isAir(),
            "The poured foundation broke");
      if (!level.getBlockState(corner).isAir())
        helper.assertTrue(!player.gameMode.destroyBlock(corner) && !level.getBlockState(corner).isAir(),
            "The natural terrain corner inside the box broke");
      player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(pedestal));
      helper.assertTrue(player.getInventory().countItem(HeliodorContent.COMPASS.get()) == 1,
          "The protected pedestal did not hand out the compass");
      level.explode(null, pedestal.getX() + 0.5, pedestal.getY() + 1.0, pedestal.getZ() + 0.5, 3.0F,
          Level.ExplosionInteraction.TNT);
      helper.assertTrue(level.getBlockState(pedestal).is(HeliodorContent.PEDESTAL.get())
          && !level.getBlockState(floor).isAir(), "An explosion damaged the ruin");
    } finally {
      StructureProtection.removeTransient(server, region.id());
    }
    helper.succeed();
  }

  // ---- Light Key --------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void lightKeyCrossesOnceBreaksAndBindsToItsUser(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var level = helper.getLevel();
      var data = SolsticioData.get(level.getServer());
      try (var qa = new QaPlayer(helper, "KeyQA"); var early = new QaPlayer(helper, "EarlyQA")) {
        var player = qa.player;
        unlock(player, true);
        BlockPos start = at(helper, 2, 1, 2);
        player.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        var pig = EntityType.PIG.create(level);
        pig.moveTo(Vec3.atBottomCenterOf(start));
        level.addFreshEntity(pig);
        player.startRiding(pig, true);
        ItemStack key = new ItemStack(Solsticio.LIGHT_KEY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, key);
        ItemStack after = LightKeyItem.cross(player, key);
        helper.assertTrue(player.level().dimension().equals(Solsticio.LEVEL), "The key did not open the way");
        helper.assertTrue(after.is(Solsticio.LIGHT_KEY_BROKEN.get()) && after.getCount() == 1
            && player.getUUID().equals(after.get(Solsticio.KEY_OWNER).id()), "The key did not break into a bound return key");
        helper.assertTrue(player.blockPosition().distManhattan(data.arrival) <= 2, "Not at the arrival point");
        helper.assertTrue(!player.isPassenger() && pig.isAlive() && pig.level() == level, "The mount travelled");
        var home = data.returns.get(player.getUUID());
        helper.assertTrue(home != null && home.pos().getX() == start.getX() && home.pos().getZ() == start.getZ(),
            "The departure point was not remembered");
        ItemStack spare = new ItemStack(Solsticio.LIGHT_KEY.get());
        helper.assertTrue(LightKeyItem.cross(player, spare) == spare && spare.is(Solsticio.LIGHT_KEY.get())
            && player.level().dimension().equals(Solsticio.LEVEL), "A whole key was spent inside Solsticio");

        unlock(early.player, false);
        early.player.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        ItemStack lockedKey = new ItemStack(Solsticio.LIGHT_KEY.get());
        helper.assertTrue(LightKeyItem.cross(early.player, lockedKey) == lockedKey
            && lockedKey.is(Solsticio.LIGHT_KEY.get()) && early.player.level() == level,
            "A team without the Ark crossed or lost its key");
        pig.discard();
      }
    });
  }

  /** The real item-use path: hold to channel, release early to cancel, hold through to cross. */
  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void lightKeyChannelCancelsOnReleaseAndCrossesWhenHeld(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var level = helper.getLevel();
      try (var qa = new QaPlayer(helper, "ChannelQA")) {
        var player = qa.player;
        unlock(player, true);
        BlockPos start = at(helper, 2, 1, 2);
        player.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Solsticio.LIGHT_KEY.get()));
        player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "The key did not start channelling");
        for (int i = 0; i < LightKeyRules.CHANNEL_TICKS / 2; i++) player.doTick();
        player.releaseUsingItem();
        helper.assertTrue(!player.isUsingItem() && player.level() == level
            && player.getMainHandItem().is(Solsticio.LIGHT_KEY.get()), "Releasing early did not cancel cleanly");
        player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        for (int i = 0; i <= LightKeyRules.CHANNEL_TICKS + 1 && player.level() == level; i++) player.doTick();
        helper.assertTrue(player.level().dimension().equals(Solsticio.LEVEL), "A full channel did not cross");
        helper.assertTrue(player.getMainHandItem().is(Solsticio.LIGHT_KEY_BROKEN.get())
            && player.getInventory().countItem(Solsticio.LIGHT_KEY.get()) == 0
            && player.getInventory().countItem(Solsticio.LIGHT_KEY_BROKEN.get()) == 1,
            "The crossing duplicated or lost the key");
      }
    });
  }

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void brokenKeyCarriesOnlyItsOwnerBothWays(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var level = helper.getLevel();
      try (var qa = new QaPlayer(helper, "OwnerQA"); var other = new QaPlayer(helper, "StrangerQA")) {
        var owner = qa.player;
        var stranger = other.player;
        unlock(stranger, true);
        BlockPos start = at(helper, 2, 1, 2);
        level.setBlockAndUpdate(start.below(), Blocks.STONE.defaultBlockState());
        owner.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        stranger.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        ItemStack key = returnKey(owner);
        // No team condition: the owner's campaign has not even reached the Ark.
        unlock(owner, false);
        helper.assertTrue(LightKeyItem.cross(owner, key) == key && owner.level().dimension().equals(Solsticio.LEVEL),
            "The owner could not use their return key");
        helper.assertTrue(LightKeyItem.cross(stranger, key) == key && stranger.level() == level,
            "Someone else travelled with the key");
        SolsticioTravel.toSolsticio(stranger);
        helper.assertTrue(stranger.level().dimension().equals(Solsticio.LEVEL), "QA could not bring the stranger in");
        LightKeyItem.cross(stranger, key);
        helper.assertTrue(stranger.level().dimension().equals(Solsticio.LEVEL), "Someone else went home with the key");
        LightKeyItem.cross(owner, key);
        helper.assertTrue(owner.level() == level && owner.blockPosition().distManhattan(start) <= 1,
            "The owner did not return to where they left");
        helper.assertTrue(key.is(Solsticio.LIGHT_KEY_BROKEN.get()) && key.getCount() == 1, "The return key was spent");
      }
    });
  }

  // ---- Portal -----------------------------------------------------------------------------

  /** A reused test world may hold an open portal from an earlier run: start from dormant. */
  private static void resetPortal(net.minecraft.server.MinecraftServer server, SolsticioData data) {
    data.portalRelics.clear();
    data.portalArmed = false;
    data.portalArmedBy = null;
    data.waystoneRegistered = false;
    server.getLevel(Solsticio.LEVEL).setBlock(data.portal, Solsticio.PORTAL.get().defaultBlockState(), 3);
    if (data.overworldPortal != null) {
      server.overworld().removeBlock(data.overworldPortal, false);
      data.overworldPortal = null;
    }
    data.setDirty();
    StructureProtection.invalidate(server);
  }

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void portalOpensWithRelicsAndOwnKeyAndRestsAfterArrival(GameTestHelper helper) {
    whenCityReady(helper, () -> {
      var server = helper.getLevel().getServer();
      var data = SolsticioData.get(server);
      ServerLevel solsticio = server.getLevel(Solsticio.LEVEL);
      try (var qa = new QaPlayer(helper, "RiftQA"); var early = new QaPlayer(helper, "RiftEarlyQA")) {
        var player = qa.player;
        resetPortal(server, data);
        unlock(player, true);
        helper.assertTrue(SolsticioTravel.toSolsticio(player), "QA could not enter Solsticio");
        // A team that has not unlocked Solsticio cannot set relics.
        unlock(early.player, false);
        SolsticioTravel.toSolsticio(early.player);
        ItemStack earlyRelic = new ItemStack(Solsticio.RELICS.get(0).get());
        early.player.setItemInHand(InteractionHand.MAIN_HAND, earlyRelic);
        early.player.gameMode.useItemOn(early.player, solsticio, earlyRelic, InteractionHand.MAIN_HAND, top(data.portal));
        helper.assertTrue(earlyRelic.getCount() == 1 && !data.portalRelics.contains(1), "A locked team set a relic");

        for (int relic = 1; relic <= 3; relic++) {
          ItemStack stack = new ItemStack(Solsticio.RELICS.get(relic - 1).get());
          player.setItemInHand(InteractionHand.MAIN_HAND, stack);
          player.gameMode.useItemOn(player, solsticio, stack, InteractionHand.MAIN_HAND, top(data.portal));
          helper.assertTrue(stack.isEmpty() && data.portalRelics.contains(relic), "Relic " + relic + " was not set");
        }
        helper.assertTrue(!data.portalArmed, "The portal opened without the Light Key");
        ItemStack key = returnKey(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(8, key);
        player.gameMode.useItemOn(player, solsticio, ItemStack.EMPTY, InteractionHand.MAIN_HAND, top(data.portal));
        helper.assertTrue(data.portalArmed && solsticio.getBlockState(data.portal).getValue(SolsticioPortalBlock.ARMED),
            "The portal did not open with relics and key");
        helper.assertTrue(SolsticioPortalBlock.carriesOwnKey(player), "Opening the portal consumed the key");
        helper.assertTrue(data.waystoneRegistered || (data.overworldPortal != null && server.overworld()
            .getBlockState(data.overworldPortal).is(Solsticio.PORTAL.get())), "The portal has no other end");

        // Anyone may use it; arriving makes it rest so nobody bounces straight back.
        SolsticioTravel.forget(early.player);
        helper.assertTrue(SolsticioTravel.portalContact(early.player, true) && early.player.level() == server.overworld(),
            "The open portal did not carry a visitor home");
        if (data.overworldPortal != null) {
          helper.assertTrue(!SolsticioTravel.portalContact(early.player, false) && early.player.level() == server.overworld(),
              "The Overworld rift bounced a player who had just arrived");
          SolsticioTravel.forget(early.player);
          helper.assertTrue(SolsticioTravel.portalContact(early.player, false)
              && early.player.level().dimension().equals(Solsticio.LEVEL), "The Overworld rift did not lead to Solsticio");
          helper.assertTrue(!SolsticioTravel.portalContact(early.player, true)
              && early.player.level().dimension().equals(Solsticio.LEVEL), "The Solsticio portal bounced an arrival");
        }
      }
    });
  }
}
