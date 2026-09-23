package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA only, pending integration: FTB Chunks must refuse restoration inside another
 * team's claim through the native placement event, while the owner may restore the same chunk.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class NatureRestorationGameTests {
  private static final int EXTENT = 40;

  private NatureRestorationGameTests() {}

  @GameTest(template = "nature_restoration", timeoutTicks = 600, skyAccess = true)
  public static void natureRestorationRespectsForeignFtbChunksClaim(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(helper.absolutePos(new BlockPos(x, -1, z)), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
    BlockPos site = helper.absolutePos(new BlockPos(20, 1, 20));
    await(helper, site, 0);
  }

  private static void await(GameTestHelper helper, BlockPos site, int waited) {
    if (!NatureRestoration.loaded(helper.getLevel(), site)) {
      helper.assertTrue(waited < 200, "Nature restoration claim fixture never finished loading");
      helper.runAfterDelay(1, () -> await(helper, site, waited + 1));
      return;
    }
    try (var owner = new Session(helper, "NatureOwner"); var visitor = new Session(helper, "NatureVisitor")) {
      claimScenario(helper, site, owner.player, visitor.player);
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("FTB Chunks command failed during nature claim QA", failure);
    }
    helper.succeed();
  }

  private static void claimScenario(GameTestHelper helper, BlockPos site, ServerPlayer owner,
      ServerPlayer visitor) throws Exception {
    var level = helper.getLevel();
    BlockPos controller = TeamRestartGameTests.ark(helper);
    BlockPos module = module(helper, controller, "nature_module");
    int groundY = site.getY() - 1;
    List<BlockPos> scars = new ArrayList<>();
    for (int dx = -7; dx <= 7; dx++) {
      BlockPos pos = new BlockPos(site.getX() + dx, groundY, site.getZ());
      level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
      scars.add(pos);
    }
    // The owner claims only the chunk holding the site's eastern end.
    BlockPos east = new BlockPos(site.getX() + 7, site.getY(), site.getZ());
    ChunkPos claimed = new ChunkPos(east);
    helper.assertTrue(claimed.x != new ChunkPos(site.offset(-7, 0, 0)).x,
        "Claim fixture must split the restoration disc across chunks");
    owner.teleportTo(east.getX() + 0.5, east.getY() + 1.0, east.getZ() + 0.5);
    int claimedResult = owner.server.getCommands().getDispatcher().execute("ftbchunks claim",
        owner.createCommandSourceStack().withSuppressedOutput());
    helper.assertTrue(claimedResult > 0, "FTB Chunks did not claim the owner's chunk");
    try {
      for (ServerPlayer player : List.of(owner, visitor))
        player.teleportTo(controller.getX() + 0.5, controller.getY() + 1.0, controller.getZ() + 0.5);

      Map<BlockPos, BlockState> before = snapshot(helper);
      visitor.setItemInHand(InteractionHand.MAIN_HAND, bookmark(helper, site));
      helper.assertTrue(NatureRestoration.mark(visitor, module, InteractionHand.MAIN_HAND).status()
          == NatureRestoration.Status.MARKED, "Visitor could not mark a site");
      visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 64));
      visitor.gameMode.useItemOn(visitor, level, visitor.getMainHandItem(), InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(module), Direction.UP, module, false));
      int spent = 64 - visitor.getMainHandItem().getCount();
      int changedOutside = 0;
      for (var entry : before.entrySet()) {
        BlockState now = level.getBlockState(entry.getKey());
        if (now.equals(entry.getValue())) continue;
        helper.assertTrue(!new ChunkPos(entry.getKey()).equals(claimed),
            "A foreign team's restoration changed the claimed chunk at " + entry.getKey());
        if (entry.getKey().getY() == groundY || entry.getKey().getY() == groundY + 1) changedOutside++;
      }
      helper.assertTrue(spent > 0 && spent == changedOutside,
          "Foreign restoration charged for refused claimed blocks: spent=" + spent + " changed=" + changedOutside);
      for (BlockPos scar : scars)
        helper.assertTrue(level.getBlockState(scar).is(new ChunkPos(scar).equals(claimed)
                ? Blocks.DIRT : Blocks.GRASS_BLOCK),
            "Claim boundary was not respected at " + scar);

      owner.setItemInHand(InteractionHand.MAIN_HAND, bookmark(helper, site));
      NatureRestoration.mark(owner, module, InteractionHand.MAIN_HAND);
      owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 64));
      var outcome = NatureRestoration.restore(owner, module, InteractionHand.MAIN_HAND);
      helper.assertTrue(outcome.status() == NatureRestoration.Status.RESTORED
          && scars.stream().allMatch(scar -> level.getBlockState(scar).is(Blocks.GRASS_BLOCK)),
          "The claim owner could not restore their own chunk: " + outcome);
    } finally {
      owner.teleportTo(east.getX() + 0.5, east.getY() + 1.0, east.getZ() + 0.5);
      owner.server.getCommands().getDispatcher().execute("ftbchunks unclaim",
          owner.createCommandSourceStack().withSuppressedOutput());
    }
  }

  private static Map<BlockPos, BlockState> snapshot(GameTestHelper helper) {
    Map<BlockPos, BlockState> states = new java.util.HashMap<>();
    for (int x = 0; x < EXTENT; x++)
      for (int y = -1; y <= 27; y++)
        for (int z = 0; z < EXTENT; z++) {
          BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
          states.put(pos, helper.getLevel().getBlockState(pos));
        }
    return states;
  }

  private static ItemStack bookmark(GameTestHelper helper, BlockPos site) {
    ItemStack compass = new ItemStack(Items.COMPASS);
    compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
        Optional.of(GlobalPos.of(helper.getLevel().dimension(), site)), false));
    return compass;
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
        connection.disconnect(Component.literal("Entrelumen nature QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }
}
