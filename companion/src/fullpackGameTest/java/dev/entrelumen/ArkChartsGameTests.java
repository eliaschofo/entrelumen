package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA only, pending integration. FTB Chunks must refuse another team's chart
 * gesture on a claimed Ark, and pinned Supplementaries slice maps (a Moonlight custom map layer)
 * must stay out of a surface chart while plain maps with the pack's map layers still compile.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ArkChartsGameTests {
  private ArkChartsGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void explorationChartsRespectForeignFtbChunksClaim(GameTestHelper helper) throws Exception {
    var level = helper.getLevel();
    BlockPos controller = TeamRestartGameTests.ark(helper);
    BlockPos module = module(helper, controller, "exploration_module");
    try (var owner = new Session(helper, "ChartOwner"); var visitor = new Session(helper, "ChartVisitor")) {
      for (ServerPlayer player : java.util.List.of(owner.player, visitor.player))
        TeamRestartGameTests.nearController(player, controller);
      int claimed = owner.player.server.getCommands().getDispatcher().execute("ftbchunks claim",
          owner.player.createCommandSourceStack().withSuppressedOutput());
      helper.assertTrue(claimed > 0, "FTB Chunks did not claim the Ark's chunk");
      try {
        for (Session session : java.util.List.of(owner, visitor)) {
          var chart = MapItem.create(level, controller.getX(), controller.getZ(), (byte) 0, true, false);
          var field = MapItem.create(level, controller.getX(), controller.getZ(), (byte) 0, true, false);
          fill(MapItem.getSavedData(field, level), colour(MapColor.GRASS));
          session.player.getInventory().selected = 0;
          session.player.getInventory().setItem(0, chart);
          session.player.getInventory().setItem(1, field);
        }
        var visitorChart = MapItem.getSavedData(visitor.player.getMainHandItem(), level);
        visitor.player.gameMode.useItemOn(visitor.player, level, visitor.player.getMainHandItem(),
            InteractionHand.MAIN_HAND, hit(module));
        helper.assertTrue(Arrays.equals(new byte[visitorChart.colors.length], visitorChart.colors),
            "A foreign team's chart gesture ran inside the claim");
        var ownerChart = MapItem.getSavedData(owner.player.getMainHandItem(), level);
        owner.player.gameMode.useItemOn(owner.player, level, owner.player.getMainHandItem(),
            InteractionHand.MAIN_HAND, hit(module));
        helper.assertTrue(ownerChart.colors[0] == colour(MapColor.GRASS),
            "The claim owner could not compile at their own Ark");
      } finally {
        owner.player.server.getCommands().getDispatcher().execute("ftbchunks unclaim",
            owner.player.createCommandSourceStack().withSuppressedOutput());
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void explorationChartsKeepSupplementariesSliceMapsApart(GameTestHelper helper)
      throws Exception {
    helper.assertTrue(ModList.get().isLoaded("supplementaries") && ModList.get().isLoaded("moonlight"),
        "Full-pack chart QA needs the pinned Supplementaries and Moonlight JARs");
    var level = helper.getLevel();
    var registries = level.registryAccess();
    BlockPos controller = TeamRestartGameTests.ark(helper);
    BlockPos module = module(helper, controller, "exploration_module");
    try (var session = new Session(helper, "ChartLayers")) {
      var player = session.player;
      TeamRestartGameTests.nearController(player, controller);
      var chart = MapItem.create(level, controller.getX(), controller.getZ(), (byte) 0, true, false);
      var target = MapItem.getSavedData(chart, level);
      helper.assertTrue(ArkCharts.view(target, registries).isEmpty(),
          "A plain map carries a view key under the pinned map layers");
      var plain = MapItem.create(level, controller.getX(), controller.getZ(), (byte) 0, true, false);
      var plainData = MapItem.getSavedData(plain, level);
      fill(plainData, colour(MapColor.GRASS));
      for (int x = 0; x < 64; x++) plainData.colors[x] = 0;
      // A slice map made through the native load path, as Supplementaries saves one.
      var sliceTag = MapItem.getSavedData(
          MapItem.create(level, controller.getX(), controller.getZ(), (byte) 0, true, false), level)
          .save(new CompoundTag(), registries);
      sliceTag.putInt("depth_lock", controller.getY() - 16);
      var slice = MapItemSavedData.load(sliceTag, registries);
      fill(slice, colour(MapColor.STONE));
      var sliceId = level.getFreeMapId();
      level.setMapData(sliceId, slice);
      helper.assertTrue(!ArkCharts.view(slice, registries).isEmpty(),
          "Supplementaries no longer saves a slice map's depth as depth_lock");
      var sliceMap = new ItemStack(Items.FILLED_MAP);
      sliceMap.set(DataComponents.MAP_ID, sliceId);
      player.getInventory().selected = 0;
      player.getInventory().setItem(0, chart);
      player.getInventory().setItem(1, sliceMap);
      player.getInventory().setItem(2, plain);
      var sliceColours = slice.colors.clone();

      var outcome = ArkCharts.compile(player, module, InteractionHand.MAIN_HAND);
      helper.assertTrue(outcome.status() == ArkCharts.Status.COMPILED && outcome.matched() == 1
          && outcome.skipped() == 1 && outcome.pixels() == 128 * 128 - 64,
          "Slice and surface maps were mixed: " + outcome);
      helper.assertTrue(target.colors[0] == 0 && target.colors[64] == colour(MapColor.GRASS)
          && Arrays.equals(sliceColours, slice.colors), "A slice map supplied surface pixels");
      var reloaded = MapItemSavedData.load(target.save(new CompoundTag(), registries), registries);
      helper.assertTrue(Arrays.equals(reloaded.colors, target.colors)
          && ArkCharts.view(reloaded, registries).isEmpty(),
          "The compiled chart did not round-trip with the pinned map layers");
    }
    helper.succeed();
  }

  private static byte colour(MapColor colour) {
    return colour.getPackedId(MapColor.Brightness.NORMAL);
  }

  private static void fill(MapItemSavedData data, byte colour) {
    Arrays.fill(data.colors, colour);
  }

  private static BlockHitResult hit(BlockPos pos) {
    return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
  }

  private static BlockPos module(GameTestHelper helper, BlockPos controller, String id) {
    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("entrelumen", id));
    for (BlockPos pos : BlockPos.betweenClosed(controller.offset(-1, 0, 1), controller.offset(1, 0, 2)))
      if (helper.getLevel().getBlockState(pos).is(block)) return pos.immutable();
    throw new IllegalStateException("Ark fixture lacks " + id);
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final EmbeddedChannel channel;

    Session(GameTestHelper helper, String name) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
          cookie.gameProfile(), cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new EmbeddedChannel(connection);
      try {
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Entrelumen chart QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }
}
