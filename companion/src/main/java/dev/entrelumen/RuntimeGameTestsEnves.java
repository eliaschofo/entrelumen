package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.Placement;
import dev.entrelumen.EnvesData.Status;
import dev.entrelumen.EnvesLayout.Role;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated Envés GameTests (excluded from the distributable jar by the RuntimeGameTests* pattern):
 * the Sealed Stair's seal, the offering and the gate, the shared fall pool with its respawns and
 * expulsion, indestructibility, the travel and flight rules, blinks and the depth cap, giving up,
 * attempts that follow a party, the fog map after a reconnect, lazy floors and the continuous
 * stairwell. Every attempt is real: its floors are placed in the Envés dimension of the test server
 * (the fixture mod restores datapack dimensions there).
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsEnves {
  private RuntimeGameTestsEnves() {}

  private static final class QaPlayer implements AutoCloseable {
    ServerPlayer player;
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

    /** The player dies and comes back, as when the client clicks Respawn. */
    void dieAndRespawn() {
      player.kill();
      player = player.server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
    }

    @Override
    public void close() {
      try {
        Enves.forget(player);
        EnvesGuard.forget(player);
        connection.disconnect(net.minecraft.network.chat.Component.literal("Envés QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /**
   * The engine's own cases run on the engine's plain hooks: no echoes in the rooms they walk, seals
   * that light on a touch and no champion at the stair. The content's cases are in
   * {@code RuntimeGameTestsEnvesContent}.
   */
  @net.minecraft.gametest.framework.BeforeBatch(batch = "enves_attempts")
  public static void plainHooks(ServerLevel level) {
    EnvesContent.uninstall();
  }

  @net.minecraft.gametest.framework.AfterBatch(batch = "enves_attempts")
  public static void contentHooks(ServerLevel level) {
    EnvesContent.install();
  }

  private static void frontier(ServerPlayer player) {
    Entrelumen.current(player).act = ApotheosisTiers.FRONTIER_ACT;
    CampaignData.get(player.server).setDirty();
  }

  /** Runs {@code body} once, on the first tick {@code ready} holds. */
  private static void when(GameTestHelper helper, BooleanSupplier ready, Runnable body) {
    var done = new java.util.concurrent.atomic.AtomicBoolean();
    helper.onEachTick(() -> {
      if (done.get() || !ready.getAsBoolean()) return;
      done.set(true);
      body.run();
    });
  }

  private static Attempt openFor(GameTestHelper helper, ServerPlayer player, Tier tier) {
    frontier(player);
    player.getInventory().add(new ItemStack(Items.NETHER_STAR));
    var refusal = Enves.open(player, tier);
    helper.assertTrue(refusal == null, "The gate refused a paid attempt: " + refusal);
    return Enves.attemptOf(player).orElseThrow();
  }

  private static void cleanUp(ServerPlayer player, Attempt attempt) {
    if (attempt.live()) Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
  }

  // ---- The Sealed Stair ---------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100, batch = "enves_entrance")
  public static void theSealedStairOpensOnlyForFrontierAndNeverSpoils(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = EnvesData.get(level.getServer());
    var saved = data.entrance;
    BlockPos heart = helper.absolutePos(new BlockPos(2, 1, 2));
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(heart.offset(dx, 0, dz), Blocks.CHISELED_TUFF.defaultBlockState());
    BlockPos gate = helper.absolutePos(new BlockPos(2, 1, 4));
    EnvesEntrance.record(level, heart, gate, gate.offset(0, 0, -2), new int[] {0, 0, 0, 0, 0, 0});
    try (var novice = new QaPlayer(helper, "SealNovice"); var hero = new QaPlayer(helper, "SealHero")) {
      BlockPos cell = heart.east();
      var hit = new BlockHitResult(Vec3.atCenterOf(cell).add(0, 0.5, 0), Direction.UP, cell, false);
      novice.player.teleportTo(level, heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 3.5, 0, 0);
      novice.player.gameMode.useItemOn(novice.player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
      helper.assertTrue(!level.getBlockState(cell).isAir() && !data.entrance.open, "The seal opened before Frontier");
      helper.assertTrue(Enves.open(novice.player, Tier.HAVEN) == Enves.Refusal.NOT_YET, "The gate answered before Frontier");
      helper.assertTrue(level.getBlockState(gate).is(Enves.GATE.get()) && level.getBlockState(gate.above(3).east()).is(Enves.GATE.get()),
          "The 3x4 gate was not set");
      frontier(hero.player);
      hero.player.teleportTo(level, heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 3.5, 0, 0);
      hero.player.gameMode.useItemOn(hero.player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
      helper.assertTrue(data.entrance.open, "The seal did not open for a Frontier team");
      for (BlockPos seal : data.entrance.sealCells) helper.assertTrue(level.getBlockState(seal).isAir(), "A seal cell stayed");
      helper.assertTrue(level.getBlockState(heart).is(Blocks.CHISELED_TUFF), "The heart's centre must stay under the pedestal");
      helper.assertTrue(EnvesEntrance.closeSeal(level) && level.getBlockState(cell).is(Blocks.CHISELED_TUFF),
          "Closing the heart did not restore the mosaic");
    } finally {
      data.entrance = saved;
      data.setDirty();
    }
    helper.succeed();
  }

  /** A start ruin whose stair falls outside the world (superflat) gets no gate. */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "enves_entrance")
  public static void aSealedStairOutsideTheWorldIsRefused(GameTestHelper helper) {
    var level = helper.getLevel();
    BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
    BlockPos below = new BlockPos(inside.getX(), level.getMinBuildHeight() - 1, inside.getZ());
    BlockPos top = new BlockPos(inside.getX(), level.getMaxBuildHeight() - 2, inside.getZ());
    helper.assertTrue(EnvesEntrance.withinWorld(level, inside, inside, inside.south(4)), "A stair inside the world was refused");
    helper.assertTrue(EnvesEntrance.withinWorld(level, inside, inside, null), "A stair without an antechamber marker was refused");
    helper.assertTrue(!EnvesEntrance.withinWorld(level, below, inside, inside), "An origin under the world floor was accepted");
    helper.assertTrue(!EnvesEntrance.withinWorld(level, inside, below, inside), "A gate under the world floor was accepted");
    helper.assertTrue(!EnvesEntrance.withinWorld(level, inside, top, inside), "A gate whose top row leaves the world was accepted");
    helper.assertTrue(!EnvesEntrance.withinWorld(level, inside, inside, below), "An antechamber under the world floor was accepted");
    helper.succeed();
  }

  /** Chunk generation checks spawns on worker threads: the stair's box is only read on the server thread. */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "enves_entrance")
  public static void theStairsSpawnGuardReadsItsRecordOnlyOnTheServerThread(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = EnvesData.get(level.getServer());
    var saved = data.entrance;
    BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
    var entrance = new EnvesData.Entrance();
    entrance.box = new int[] {at.getX() - 2, at.getY() - 2, at.getZ() - 2, at.getX() + 2, at.getY() + 2, at.getZ() + 2};
    data.entrance = entrance;
    var zombie = EntityType.ZOMBIE.create(level);
    try {
      helper.assertTrue(zombie != null, "No zombie to check");
      zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
      var onServer = new MobSpawnEvent.PositionCheck(zombie, level, MobSpawnType.CHUNK_GENERATION, null);
      EnvesGuard.onSpawnCheck(onServer);
      helper.assertTrue(onServer.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL,
          "A mob may spawn in the Sealed Stair: " + onServer.getResult());
      var onWorker = new MobSpawnEvent.PositionCheck(zombie, level, MobSpawnType.CHUNK_GENERATION, null);
      Thread worker = new Thread(() -> EnvesGuard.onSpawnCheck(onWorker), "Envés QA worldgen");
      worker.start();
      worker.join(10_000);
      helper.assertTrue(!worker.isAlive() && onWorker.getResult() == MobSpawnEvent.PositionCheck.Result.DEFAULT,
          "A worldgen thread read the overworld's saved data: " + onWorker.getResult());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } finally {
      data.entrance = saved;
      data.setDirty();
      if (zombie != null) zombie.discard();
    }
    helper.succeed();
  }

  // ---- The gate and the offering ------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_attempts")
  public static void theOfferingOpensOneAttemptAtAChosenDifficulty(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesPayer");
    var player = qa.player;
    frontier(player);
    helper.assertTrue(Enves.open(player, Tier.FRONTIER) == Enves.Refusal.NO_OFFERING, "An attempt opened without the offering");
    player.getInventory().add(new ItemStack(Items.NETHER_STAR, 2));
    helper.assertTrue(Enves.open(player, Tier.ASCENT) == Enves.Refusal.BAD_TIER, "A difficulty above the player's tier was accepted");
    helper.assertTrue(Enves.open(player, Tier.HAVEN) == null, "A lower difficulty must be allowed");
    helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 1, "The offering was not taken exactly once");
    Attempt attempt = Enves.attemptOf(player).orElseThrow();
    helper.assertTrue(attempt.tier == Tier.HAVEN && attempt.poolTotal == 0 && attempt.poolLeft == 0,
        "The pool starts empty until somebody goes in: " + attempt.poolTotal);
    helper.assertTrue(Enves.open(player, Tier.HAVEN) == Enves.Refusal.ALREADY_OPEN, "A second attempt opened for the team");
    when(helper, () -> attempt.status == Status.OPEN, () -> {
      try {
        helper.assertTrue(Enves.enter(player), "The payer could not enter");
        helper.assertTrue(attempt.poolTotal == 3 && attempt.poolLeft == 3, "The payer's first entry did not add three falls: " + attempt.poolTotal);
        helper.assertTrue(Enves.inEnves(player) && Enves.attemptAt(player.server, player.blockPosition()).isPresent(),
            "The payer is not inside the attempt's slot");
        BlockPos arrival = Enves.arrival(player.server, attempt, 1);
        helper.assertTrue(SolsticioTravel.standable(player.serverLevel(), arrival), "Floor I's arrival is not standable");
        helper.assertTrue(EnvesGeometry.depthAt(player.blockPosition().getY()) == 1, "Not on floor I");
        var layout = Enves.layout(attempt, 1);
        for (int cell : layout.cells()) {
          BlockPos origin = Enves.origin(attempt, 1, cell);
          helper.assertTrue(!player.serverLevel().getBlockState(origin.offset(0, 11, 0)).isAir(), "Cell " + cell + " has no ceiling");
        }
        helper.assertTrue(attempt.floor(2).placement == Placement.NONE, "Floor II was placed before anyone reached the guard");
        helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 1, "Entering cost anything");
      } finally {
        cleanUp(player, attempt);
        qa.close();
      }
      helper.succeed();
    });
  }

  // ---- Falls ----------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_attempts")
  public static void theGroupSharesItsFallsRespawnsOnItsFloorAndIsSentBackWhenTheyRunOut(GameTestHelper helper) {
    var founder = new QaPlayer(helper, "FallFounder");
    var guest = new QaPlayer(helper, "FallGuest");
    PartyTeam party;
    try {
      var manager = FTBTeamsAPI.api().getManager();
      party = (PartyTeam) manager.createPartyTeam(founder.player, "Envés QA " + founder.player.getUUID(), "", Color4I.WHITE);
      helper.assertTrue(party.invite(founder.player, List.of(guest.player.getGameProfile())) == 1
          && party.join(guest.player) == 1, "The party could not be formed");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
      throw new IllegalStateException(e);
    }
    Attempt attempt = openFor(helper, founder.player, Tier.FRONTIER);
    helper.assertTrue(attempt.poolTotal == 0, "The pool must start empty, got " + attempt.poolTotal);
    helper.assertTrue(attempt.team.equals(party.getId()), "The attempt is not the party's");
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      if (stage.get() != 0 || attempt.status != Status.OPEN) return;
      stage.set(1);
      try {
        helper.assertTrue(Enves.enter(founder.player), "The founder could not enter");
        helper.assertTrue(attempt.poolTotal == 3, "The founder's entry adds three falls, got " + attempt.poolTotal);
        helper.assertTrue(Enves.enter(guest.player), "The guest could not enter");
        helper.assertTrue(attempt.poolTotal == 6 && attempt.poolLeft == 6, "A latecomer's first entry adds three more, got " + attempt.poolTotal);
        // Entering again adds nothing: once per player per attempt.
        helper.assertTrue(Enves.enter(guest.player), "The guest could not enter again");
        helper.assertTrue(attempt.poolTotal == 6 && attempt.poolLeft == 6, "A second entry added falls: " + attempt.poolTotal);
        // A real client confirms the dimension change; until then the server keeps the player invulnerable.
        founder.player.hasChangedDimension();
        guest.player.hasChangedDimension();
        guest.player.getInventory().add(new ItemStack(Items.DIAMOND, 7));
        BlockPos arrival = Enves.arrival(founder.player.server, attempt, 1);
        attempt.poolLeft = 2;
        guest.dieAndRespawn();
        helper.assertTrue(attempt.poolLeft == 1 && attempt.live(), "One fall must leave the attempt open");
        helper.assertTrue(Enves.inEnves(guest.player) && guest.player.blockPosition().equals(arrival),
            "The fallen member did not come back at floor I's start: " + guest.player.blockPosition());
        helper.assertTrue(guest.player.getInventory().countItem(Items.DIAMOND) == 7, "The fall cost the inventory");
        founder.dieAndRespawn();
        helper.assertTrue(attempt.poolLeft == 0 && attempt.status == Status.ENDED, "The last fall must end the attempt");
        helper.assertTrue(!Enves.inEnves(guest.player), "The other member was not sent back");
        helper.assertTrue(!Enves.inEnves(founder.player), "The last fallen member came back inside");
        helper.assertTrue(Enves.attemptOf(founder.player).isEmpty(), "The gate still sees the ended attempt");
        helper.assertTrue(!EnvesDeaths.keepInventory(), "keepInventory stayed on");
      } finally {
        cleanUp(founder.player, attempt);
        guest.close();
        founder.close();
      }
      helper.succeed();
    });
  }

  // ---- Rules ----------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_attempts")
  public static void theEnvesIsIndestructibleAndHasNoShortcuts(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesRules");
    Attempt attempt = openFor(helper, qa.player, Tier.FRONTIER);
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      ServerPlayer player = qa.player;
      if (stage.get() == 0 && attempt.status == Status.OPEN) {
        stage.set(1);
        helper.assertTrue(Enves.enter(player), "Could not enter");
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        BlockPos floor = feet.below();
        var before = level.getBlockState(floor);
        helper.assertTrue(!player.gameMode.destroyBlock(floor) && level.getBlockState(floor).equals(before), "A block broke");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(!level.getBlockState(feet).is(Blocks.STONE) && player.getMainHandItem().getCount() == 4, "A block was placed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(level.getFluidState(feet).isEmpty(), "Water was poured");
        player.setInvulnerable(true);
        level.explode(null, feet.getX() + 0.5, feet.getY() + 1, feet.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
        player.setInvulnerable(false);
        helper.assertTrue(level.getBlockState(floor).equals(before), "An explosion broke the floor");
        for (var item : List.of(Items.ENDER_PEARL, Items.CHORUS_FRUIT)) {
          player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 3));
          player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
          helper.assertTrue(player.getMainHandItem().getCount() == 3, item + " was used inside");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var out = player.changeDimension(new DimensionTransition(player.server.overworld(),
            Vec3.atBottomCenterOf(player.server.overworld().getSharedSpawnPos()), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
        helper.assertTrue(out == null && Enves.inEnves(player), "The player left the Envés by another way");
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.doTick();
      } else if (stage.get() >= 1 && stage.incrementAndGet() > 4) {
        try {
          helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "Flight stayed on in the Envés");
          helper.assertTrue(StructureProtection.isGuarded(player.serverLevel(), player.blockPosition()), "The Envés is not guarded");
        } finally {
          cleanUp(player, attempt);
          qa.close();
        }
        helper.succeed();
      }
    });
  }

  /**
   * Blinks and travel staffs: a player's teleport event is refused (a mob's is not, a command's is left
   * to the command guard), a move through the floor with no event is undone the next tick, echoes are
   * not captured, and standing past a closed stairwell sends the player back to their floor.
   */
  @GameTest(template = "empty", timeoutTicks = 36000, batch = "enves_attempts")
  public static void noBlinkStaffOrCaptureSkipsTheMaze(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesBlink");
    Attempt attempt = openFor(helper, qa.player, Tier.FRONTIER);
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      ServerPlayer player = qa.player;
      var server = player.server;
      switch (stage.get()) {
        case 0 -> {
          if (attempt.status != Status.OPEN) return;
          helper.assertTrue(Enves.enter(player), "Could not enter");
          player.hasChangedDimension();
          ServerLevel level = player.serverLevel();
          var blink = new EntityTeleportEvent(player, player.getX() + 8, player.getY(), player.getZ());
          helper.assertTrue(NeoForge.EVENT_BUS.post(blink).isCanceled(), "A player's blink went through");
          var zombie = EntityType.ZOMBIE.create(level);
          helper.assertTrue(zombie != null, "No zombie to check");
          zombie.moveTo(player.getX(), player.getY(), player.getZ(), 0, 0);
          helper.assertTrue(!NeoForge.EVENT_BUS.post(new EntityTeleportEvent(zombie, player.getX() + 8, player.getY(), player.getZ())).isCanceled(),
              "A mob's teleport was refused");
          helper.assertTrue(!NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(player, player.getX(), player.getY() + 1,
              player.getZ())).isCanceled(), "An operator's teleport command was refused");
          // Psi's blink sets the position with no event at all: here straight down through the floor.
          BlockPos arrival = Enves.arrival(server, attempt, 1);
          player.doTick();
          player.setPos(player.getX(), player.getY() - 4, player.getZ());
          player.doTick();
          helper.assertTrue(player.blockPosition().equals(arrival), "A move through the floor was not undone: " + player.blockPosition());
          // A soul vial or a lead on an echo inside does nothing.
          var entry = new EnvesEchoTables.Entry("minecraft:husk", 1, null, 40.0, 1.0, Map.of(), "");
          var echo = EnvesEchoes.spawn(level, new EnvesEchoes.Spec(entry, EnvesBalance.Role.ELITE, attempt, 1, List.of()),
              Vec3.atBottomCenterOf(arrival), null);
          helper.assertTrue(echo != null, "No echo to capture");
          try {
            helper.assertTrue(player.interactOn(echo, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                "An echo could be interacted with inside the Envés");
          } finally {
            echo.discard();
          }
          EnvesPlacer.queue(server, attempt, 2);
          stage.set(1);
        }
        case 1 -> {
          if (attempt.floor(2).placement != Placement.READY) return;
          helper.assertTrue(!attempt.floor(1).stairOpen, "Floor I's stair opened by itself");
          // Floor II stands, but its stair above is shut: a raw move there (no server record) is a skip.
          BlockPos deeper = Enves.arrival(server, attempt, 2);
          player.teleportTo(player.serverLevel(), deeper.getX() + 0.5, deeper.getY(), deeper.getZ() + 0.5, 0, 0);
          stage.set(2);
        }
        case 2 -> {
          if (EnvesGeometry.depthAt(player.blockPosition().getY()) != 1) return;
          try {
            helper.assertTrue(attempt.frontline == 1, "The skipped floor counted as reached: " + attempt.frontline);
            helper.assertTrue(attempt.depthOf.getOrDefault(player.getUUID(), 0) == 1, "The skipped floor became the player's");
          } finally {
            cleanUp(player, attempt);
            qa.close();
          }
          helper.succeed();
        }
        default -> {}
      }
    });
  }

  /** Founding a party keeps the founder's open attempt (it follows the campaign key), and so does disbanding it. */
  @GameTest(template = "empty", timeoutTicks = 200, batch = "enves_attempts")
  public static void aSoloAttemptFollowsItsFounderIntoAPartyAndBackOut(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "SoloFounder");
    Attempt attempt = openFor(helper, qa.player, Tier.FRONTIER);
    try {
      helper.assertTrue(attempt.team.equals(qa.player.getUUID()), "A solo attempt is not the player's");
      var manager = FTBTeamsAPI.api().getManager();
      var party = (PartyTeam) manager.createPartyTeam(qa.player, "Envés rekey " + qa.player.getUUID(), "", Color4I.WHITE);
      helper.assertTrue(Enves.attemptOf(qa.player).map(a -> a.id.equals(attempt.id)).orElse(false),
          "Founding a party orphaned the open attempt");
      helper.assertTrue(attempt.team.equals(party.getId()), "The attempt did not move to the party");
      party.leave(qa.player.getUUID());
      helper.assertTrue(Enves.attemptOf(qa.player).map(a -> a.id.equals(attempt.id)).orElse(false),
          "Disbanding the party orphaned the attempt");
      helper.assertTrue(attempt.team.equals(qa.player.getUUID()), "The attempt did not come back to its payer");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
      throw new IllegalStateException(e);
    } finally {
      cleanUp(qa.player, attempt);
      qa.close();
    }
    helper.succeed();
  }

  /**
   * Giving up asks twice, and while the founder plays inside, a member outside who neither paid nor
   * owns the party cannot end the run; the founder can.
   */
  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_attempts")
  public static void givingUpAsksTwiceAndOnlyTheRightMembersMayWhileOthersAreInside(GameTestHelper helper) {
    var founder = new QaPlayer(helper, "QuitFounder");
    var guest = new QaPlayer(helper, "QuitGuest");
    try {
      var party = (PartyTeam) FTBTeamsAPI.api().getManager().createPartyTeam(founder.player,
          "Envés give up " + founder.player.getUUID(), "", Color4I.WHITE);
      helper.assertTrue(party.invite(founder.player, List.of(guest.player.getGameProfile())) == 1
          && party.join(guest.player) == 1, "The party could not be formed");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
      throw new IllegalStateException(e);
    }
    Attempt attempt = openFor(helper, founder.player, Tier.FRONTIER);
    when(helper, () -> attempt.status == Status.OPEN, () -> {
      try {
        helper.assertTrue(Enves.enter(founder.player), "The founder could not enter");
        helper.assertTrue(!Enves.giveUp(guest.player) && !Enves.giveUp(guest.player) && attempt.live(),
            "A member outside ended a run the founder is playing");
        helper.assertTrue(!Enves.giveUp(guest.player, true) && attempt.live(), "The gate let a member outside end it");
        helper.assertTrue(!Enves.giveUp(founder.player) && attempt.live(), "One ask ended the attempt");
        helper.assertTrue(Enves.giveUp(founder.player) && !attempt.live(), "A second ask within ten seconds did not end it");
        helper.assertTrue(!Enves.inEnves(founder.player), "The founder stayed inside an ended attempt");
      } finally {
        cleanUp(founder.player, attempt);
        guest.close();
        founder.close();
      }
      helper.succeed();
    });
  }

  /** A client that reconnects gets the fog map again, not only when a new room or seal changes it. */
  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_attempts")
  public static void theFogMapIsSentAgainAfterAReconnect(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesMap");
    Attempt attempt = openFor(helper, qa.player, Tier.FRONTIER);
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      ServerPlayer player = qa.player;
      if (stage.get() == 0) {
        if (attempt.status != Status.OPEN) return;
        helper.assertTrue(Enves.enter(player), "Could not enter");
        stage.set(1);
      } else if (stage.get() == 1 && Enves.mapSent(player)) {
        try {
          NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
          helper.assertTrue(!Enves.mapSent(player), "The map stayed marked as sent after the player left");
        } finally {
          cleanUp(player, attempt);
          qa.close();
        }
        helper.succeed();
      }
    });
  }

  // ---- Floors and stairwells ----------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 36000, batch = "enves_attempts")
  public static void theNextFloorComesAtTheGuardAndTheStairwellRunsThroughTheSlab(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesStairs");
    Attempt attempt = openFor(helper, qa.player, Tier.FRONTIER);
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      ServerPlayer player = qa.player;
      var server = player.server;
      switch (stage.get()) {
        case 0 -> {
          if (attempt.status != Status.OPEN) return;
          helper.assertTrue(Enves.enter(player), "Could not enter");
          var layout = Enves.layout(attempt, 1);
          int exit = layout.exit();
          for (var seal : Enves.markers(server, attempt, 1, exit))
            if (seal.marker().kind() == EnvesMarkers.Kind.STAIR_SEAL)
              helper.assertTrue(!player.serverLevel().getBlockState(seal.pos()).isAir(), "The stairwell is open before its seals");
          // Walk into the guard room: floor II is due.
          int guard = layout.guard();
          BlockPos champion = Enves.marker(server, attempt, 1, guard, EnvesMarkers.Kind.ENCOUNTER).orElseThrow();
          player.teleportTo(player.serverLevel(), champion.getX() + 0.5, champion.getY(), champion.getZ() + 0.5, 0, 0);
          stage.set(1);
        }
        case 1 -> {
          if (attempt.floor(2).placement != Placement.READY) return;
          helper.assertTrue(attempt.floor(1).guardReached, "The guard room was not recorded");
          var layout = Enves.layout(attempt, 1);
          for (int seal : layout.seals()) {
            BlockPos pos = Enves.marker(server, attempt, 1, seal, EnvesMarkers.Kind.SEAL).orElseThrow();
            helper.assertTrue(player.serverLevel().getBlockState(pos).is(Enves.SEAL.get()), "No seal block in seal room " + seal);
            helper.assertTrue(Enves.lightSeal(player, pos), "Could not light seal " + seal);
          }
          stage.set(2);
        }
        case 2 -> {
          if (!attempt.floor(1).stairOpen) return;
          ServerLevel level = player.serverLevel();
          int exit = Enves.layout(attempt, 1).exit();
          helper.assertTrue(Enves.layout(attempt, 2).start() == exit, "Floor II does not start under the stairs");
          BlockPos top = Enves.origin(attempt, 1, exit);
          String[] faces = {"west", "north", "east", "south"};
          for (int p = 0; p <= 16; p++) {
            int[] ring = EnvesGeometry.RING[p % 16];
            BlockPos step = top.offset(ring[0], EnvesGeometry.ringY(p), ring[1]);
            var state = level.getBlockState(step);
            helper.assertTrue(state.isFaceSturdy(level, step, Direction.UP) || state.getBlock() instanceof StairBlock,
                "No step at ring cell " + p + ": " + state);
            if (!EnvesGeometry.landing(p))
              helper.assertTrue(state.getBlock() instanceof StairBlock
                  && state.getValue(StairBlock.FACING).getName().equals(faces[EnvesGeometry.ringSide(p)]), "Stair " + p + " faces wrong");
            for (int h = 1; h <= 3; h++)
              helper.assertTrue(level.getBlockState(step.above(h)).getCollisionShape(level, step.above(h)).isEmpty(),
                  "No headroom over ring cell " + p + " at " + step.above(h));
          }
          BlockPos bottom = Enves.marker(server, attempt, 2, exit, EnvesMarkers.Kind.STAIR_BOTTOM).orElseThrow();
          helper.assertTrue(bottom.equals(top.offset(EnvesGeometry.RING[0][0], 1 - EnvesGeometry.FLOOR_H, EnvesGeometry.RING[0][1])),
              "The bottom landing is not under the top one");
          helper.assertTrue(Enves.layout(attempt, 2).role(exit) == Role.START, "The cell under the stairs is not floor II's start");
          cleanUp(player, attempt);
          qa.close();
          helper.succeed();
        }
        default -> {}
      }
    });
  }
}
