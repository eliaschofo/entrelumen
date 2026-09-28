package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.Status;
import dev.ftb.mods.ftblibrary.integration.stages.StageHelper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * QA-JAR-only checks of the Envés with the whole pack: the Sealed Stair stands under the real start
 * ruin, chests are Lootr's, FTB Chunks loses its map inside, and the pack's FTB Chunks config asks
 * for the map stage. The engine's own GameTests (RuntimeGameTestsEnves) also run here.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class EnvesFullpackGameTests {
  private EnvesFullpackGameTests() {}

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
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(net.minecraft.network.chat.Component.literal("Envés full-pack QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** The start ruin of this world carries the Sealed Stair: heart, gate, antechamber and the way down. */
  @GameTest(template = "empty", timeoutTicks = 400)
  public static void theSealedStairStandsUnderTheStartRuin(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var entrance = EnvesData.get(server).entrance;
    helper.assertTrue(entrance.heart != null && entrance.gate.size() == 12 && entrance.antechamber != null,
        "The start ruin did not record its Sealed Stair");
    ServerLevel overworld = server.overworld();
    BlockPos heart = entrance.heart;
    helper.assertTrue(RuinData.get(server).find(HeliodorRuins.START).filter(ruin -> ruin.contains(Level.OVERWORLD, heart)).isPresent(),
        "The recorded Sealed Stair is not the world's start ruin's: " + heart);
    overworld.getChunk(heart);
    overworld.getChunk(entrance.gate.getFirst());
    for (BlockPos gate : entrance.gate)
      helper.assertTrue(overworld.getBlockState(gate).is(Enves.GATE.get()), "No gate block at " + gate);
    helper.assertTrue(SolsticioTravel.standable(overworld, entrance.antechamber), "The antechamber is not standable");
    helper.assertTrue(overworld.getBlockState(heart.above()).is(HeliodorContent.PEDESTAL.get()),
        "The pedestal is not on the heart");
    if (!entrance.open)
      for (BlockPos seal : entrance.sealCells)
        helper.assertTrue(!overworld.getBlockState(seal).isAir(), "The sealed heart has a hole at " + seal);
    // The companion's fix to the art: the well's foot opens into the antechamber, over a floor.
    for (int dy : new int[] {-12, -11})
      helper.assertTrue(overworld.getBlockState(heart.offset(-1, dy, -2)).getCollisionShape(overworld, heart).isEmpty(),
          "The doorway from the well's foot is shut");
    helper.assertTrue(overworld.getBlockState(heart.offset(0, -13, 1)).isFaceSturdy(overworld, heart.offset(0, -13, 1), Direction.UP),
        "The well's foot has no floor");
    helper.assertTrue(StructureProtection.isGuarded(overworld, heart.offset(0, -12, -4)), "The antechamber is not protected");
    helper.succeed();
  }

  /** Lootr chests, the map stage and the pack's FTB Chunks config, in one attempt. */
  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_fullpack")
  public static void envesChestsAreLootrsAndFtbChunksLosesItsMapInside(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EnvesPack");
    ServerPlayer player = qa.player;
    Entrelumen.current(player).act = ApotheosisTiers.FRONTIER_ACT;
    player.getInventory().add(new ItemStack(Items.NETHER_STAR));
    helper.assertTrue(Enves.open(player, Tier.FRONTIER) == null, "The gate refused the offering");
    Attempt attempt = Enves.attemptOf(player).orElseThrow();
    var provider = StageHelper.getInstance().getProvider();
    EnvesGuard.mapStage(player);
    helper.assertTrue(provider.has(player, EnvesGuard.MAP_STAGE), "Outside, the player lacks FTB Chunks' map stage");
    try {
      Class<?> config = Class.forName("dev.ftb.mods.ftbchunks.FTBChunksWorldConfig");
      Object value = config.getField("REQUIRE_GAME_STAGE").get(null);
      Object on = value.getClass().getMethod("get").invoke(value);
      helper.assertTrue(Boolean.TRUE.equals(on), "The pack's ftbchunks-world.snbt does not require the map stage");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("FTB Chunks world config not found", e);
    }
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      if (stage.get() != 0 || attempt.status != Status.OPEN) return;
      stage.set(1);
      try {
        helper.assertTrue(Enves.enter(player), "Could not enter");
        player.hasChangedDimension();
        EnvesGuard.mapStage(player);
        helper.assertTrue(!provider.has(player, EnvesGuard.MAP_STAGE), "Inside, FTB Chunks still has its map");
        var floor = Enves.floor(player.serverLevel(), attempt, 1);
        BlockPos spot = Enves.arrival(player.server, attempt, 1).offset(-1, 0, 0);
        var marker = new EnvesHooks.WorldMarker(new EnvesMarkers.Marker(EnvesMarkers.Kind.CHEST, "vault/south"), spot,
            floor.layout().start(), floor.layout().role(floor.layout().start()));
        EnvesPlacer.defaultChest(floor, marker);
        var id = BuiltInRegistries.BLOCK.getKey(player.serverLevel().getBlockState(spot).getBlock());
        helper.assertTrue(id.equals(ResourceLocation.parse("lootr:lootr_chest")), "The chest is not Lootr's: " + id);
        helper.assertTrue(player.serverLevel().getBlockEntity(spot) instanceof RandomizableContainer container
            && container.getLootTable() != null
            && container.getLootTable().location().toString().equals("entrelumen:enves/vault"),
            "The Lootr chest has no Envés loot table");
        Enves.toAntechamber(player);
        EnvesGuard.mapStage(player);
        helper.assertTrue(provider.has(player, EnvesGuard.MAP_STAGE), "Back outside, the map stage did not return");
      } finally {
        if (attempt.live()) Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
        qa.close();
      }
      helper.succeed();
    });
  }
}
