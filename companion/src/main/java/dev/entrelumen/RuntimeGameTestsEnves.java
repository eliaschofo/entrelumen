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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated Envés GameTests (excluded from the distributable jar by the RuntimeGameTests* pattern):
 * the Sealed Stair's seal, the offering and the gate, the shared fall pool with its respawns and
 * expulsion, indestructibility, the travel and flight rules, lazy floors and the continuous
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
    player.getInventory().add(new ItemStack(Items.NETHERITE_BLOCK));
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

  // ---- The gate and the offering ------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_attempts")
  public static void theOfferingOpensOneAttemptAtAChosenDifficulty(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesPayer");
    var player = qa.player;
    frontier(player);
    helper.assertTrue(Enves.open(player, Tier.FRONTIER) == Enves.Refusal.NO_OFFERING, "An attempt opened without the offering");
    player.getInventory().add(new ItemStack(Items.NETHERITE_BLOCK, 2));
    helper.assertTrue(Enves.open(player, Tier.ASCENT) == Enves.Refusal.BAD_TIER, "A difficulty above the player's tier was accepted");
    helper.assertTrue(Enves.open(player, Tier.HAVEN) == null, "A lower difficulty must be allowed");
    helper.assertTrue(player.getInventory().countItem(Items.NETHERITE_BLOCK) == 1, "The offering was not taken exactly once");
    Attempt attempt = Enves.attemptOf(player).orElseThrow();
    helper.assertTrue(attempt.tier == Tier.HAVEN && attempt.poolTotal == 3 && attempt.poolLeft == 3,
        "One member online makes a pool of three: " + attempt.poolTotal);
    helper.assertTrue(Enves.open(player, Tier.HAVEN) == Enves.Refusal.ALREADY_OPEN, "A second attempt opened for the team");
    when(helper, () -> attempt.status == Status.OPEN, () -> {
      try {
        helper.assertTrue(Enves.enter(player), "The payer could not enter");
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
        helper.assertTrue(player.getInventory().countItem(Items.NETHERITE_BLOCK) == 1, "Entering cost anything");
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
    helper.assertTrue(attempt.poolTotal == 6, "Two members online make six falls, got " + attempt.poolTotal);
    helper.assertTrue(attempt.team.equals(party.getId()), "The attempt is not the party's");
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      if (stage.get() != 0 || attempt.status != Status.OPEN) return;
      stage.set(1);
      try {
        helper.assertTrue(Enves.enter(founder.player) && Enves.enter(guest.player), "The party could not enter");
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
