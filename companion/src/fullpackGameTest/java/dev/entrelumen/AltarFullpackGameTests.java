package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for the altars, registered in {@link FullpackQABootstrap} and run on the owned
 * QA server with {@code -Dentrelumen.qa=true} (evidence: docs/verification/altars-runtime.json).
 *
 * <ul>
 *   <li>The Renewal Altar rebuilds a pit dug in land made by the pack's real overworld generator
 *       (noise, surface rules and modded biome features), next to the test, and what it writes
 *       equals an independent regeneration of the same chunk. Run it on untouched land away from
 *       the spawn test area, with the land 28-68 blocks east of the test loaded (for example
 *       {@code forceload} and {@code execute positioned ... run test run ...}): earlier tests
 *       reshape the land near spawn, and a server without players loads only the spawn chunks.</li>
 *   <li>A real FTB Chunks claim refuses a foreign team's Renewal and Terraform altars inside the
 *       claimed chunk, with no charge for refused work, while outside it the work goes on.</li>
 * </ul>
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class AltarFullpackGameTests {
  private static final int EXTENT = 40;

  private AltarFullpackGameTests() {}

  private static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what,
      Runnable then) {
    await(helper, done, waited, limit, what, then, () -> {});
  }

  /** As above; {@code onTimeout} runs first when the wait expires, e.g. to log mock players out. */
  private static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what,
      Runnable then, Runnable onTimeout) {
    if (done.getAsBoolean()) {
      then.run();
      return;
    }
    if (waited >= limit) onTimeout.run();
    helper.assertTrue(waited < limit, what + " did not finish in time");
    helper.runAfterDelay(1, () -> await(helper, done, waited + 1, limit, what, then, onTimeout));
  }

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void renewalAltarRebuildsLandFromThePacksOwnGenerator(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos origin = helper.absolutePos(BlockPos.ZERO);
    int x = origin.getX() + 48, z = origin.getZ() + 4;
    int radius = 8;
    await(helper, () -> LandWorks.loaded(level, (x - radius - 12) >> 4, (z - radius - 12) >> 4,
        (x + radius + 12) >> 4, (z + radius + 12) >> 4), 0, 400, "Natural land near spawn loading", () -> {
      int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
      BlockPos altarPos = new BlockPos(x, surface + 1, z);
      helper.assertTrue(LandWorks.ground(level.getBlockState(altarPos.below())) && level.getBlockState(altarPos).isAir(),
          "The QA position has no open natural ground; move the test or choose another world");
      List<BlockPos> pit = new ArrayList<>();
      for (int dx = 3; dx <= 5; dx++)
        for (int dz = -1; dz <= 1; dz++) {
          int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
          for (int dy = 0; dy >= -2; dy--) {
            BlockPos pos = new BlockPos(x + dx, top + dy, z + dz);
            if (LandWorks.ground(level.getBlockState(pos)) && level.getBlockState(pos.above()).getFluidState().isEmpty())
              pit.add(pos);
          }
        }
      helper.assertTrue(pit.size() >= 18, "Too little natural ground for the pit fixture");
      level.setBlockAndUpdate(altarPos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
      var altar = (RenewalAltarEntity) level.getBlockEntity(altarPos);
      altar.configureRadius(radius);
      List<ChunkPos> decorate = new ArrayList<>();
      for (var key : AltarRules.areaChunks(x, z, radius)) decorate.add(new ChunkPos(key.x(), key.z()));
      decorate.sort(java.util.Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
      List<ChunkPos> captured = new ArrayList<>();
      for (ChunkPos pos : decorate)
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) captured.add(new ChunkPos(pos.x + dx, pos.z + dz));
      var reference = TerrainReference.build(TerrainReference.Setup.capture(level, captured), decorate, () -> false);
      for (BlockPos pos : pit) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
      altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
      await(helper, () -> altar.state() == RenewalAltarEntity.State.DONE
          || altar.state() == RenewalAltarEntity.State.FAILED, 0, 3000, "Real-generator renewal", () -> {
        com.mojang.logging.LogUtils.getLogger().info(
            "ALTAR_FULLPACK renewal totals filled={} covered={} trees={} plants={} guarded={} skipped={} fertilizer={} reference=[terrain={} decorated={} terrainMillis={} decorationMillis={} failures={} unsupported={}]",
            altar.totals().filled, altar.totals().covered, altar.totals().trees, altar.totals().plants,
            altar.totals().guarded, altar.totals().skippedChunks, altar.totals().fertilizer, reference.terrainChunks,
            reference.decoratedChunks, reference.terrainNanos / 1_000_000, reference.decorationNanos / 1_000_000,
            reference.failures, reference.unsupported);
        helper.assertTrue(altar.state() == RenewalAltarEntity.State.DONE && altar.totals().skippedChunks == 0,
            "The pack's own land was not recognised: " + altar.state() + " skipped=" + altar.totals().skippedChunks);
        for (BlockPos pos : pit) {
          BlockState original = reference.state(pos);
          BlockState now = level.getBlockState(pos);
          if (!RenewalAltarEntity.fillable(original)) continue;
          helper.assertTrue(now.equals(original) || now.is(Blocks.SANDSTONE) || now.is(Blocks.RED_SANDSTONE) || now.is(Blocks.STONE),
              "Refilled " + pos + " with " + now + " instead of the generator's " + original);
        }
        helper.succeed();
      });
    });
  }

  /** Flat reference whose grass top is the template floor, as in the isolated tests. */
  private static TerrainReference.Setup flat(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    int ground = helper.absolutePos(BlockPos.ZERO).getY();
    var settings = new FlatLevelGeneratorSettings(Optional.empty(),
        level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS), List.of());
    settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
    int stone = ground - 2 - (level.getMinBuildHeight() + 1);
    if (stone > 0) settings.getLayersInfo().add(new FlatLayerInfo(stone, Blocks.STONE));
    settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
    settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
    settings.updateLayers();
    return new TerrainReference.Setup(new FlatLevelSource(settings), level.getChunkSource().randomState(),
        level.getSeed(), level.registryAccess(), level.enabledFeatures(), level.getMinBuildHeight(),
        level.getHeight(), level.dimensionType(), Map.of(), Map.of(), Set.of());
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 4000, skyAccess = true)
  public static void altarsRespectForeignFtbChunksClaims(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(helper.absolutePos(new BlockPos(x, -2, z)), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(helper.absolutePos(new BlockPos(x, -1, z)), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
    // Player names are at most 16 characters: FTB Teams syncs them with the vanilla name codec.
    var owner = new Session(helper, "AltarQAOwner");
    Session created;
    try {
      created = new Session(helper, "AltarQAVisitor");
    } catch (RuntimeException | Error failure) {
      owner.close();
      throw failure;
    }
    var visitor = created;
    BlockPos altarPos = helper.absolutePos(new BlockPos(20, 1, 20));
    // A pit that spans two chunks along x; the owner claims the eastern chunk.
    List<BlockPos> pit = new ArrayList<>();
    for (int dx = -7; dx <= 7; dx++) pit.add(altarPos.offset(dx, -1, 3));
    ChunkPos claimed = new ChunkPos(altarPos.offset(7, 0, 3));
    helper.assertTrue(claimed.x != new ChunkPos(altarPos.offset(-7, 0, 3)).x, "The pit must cross a chunk border");
    for (BlockPos top : pit)
      for (int dy = 0; dy >= -2; dy--) level.setBlock(top.above(dy), Blocks.AIR.defaultBlockState(), 2);
    BlockPos east = altarPos.offset(7, 0, 3);
    owner.player.teleportTo(east.getX() + 0.5, east.getY() + 1.0, east.getZ() + 0.5);
    try {
      int result = owner.player.server.getCommands().getDispatcher().execute("ftbchunks claim",
          owner.player.createCommandSourceStack().withSuppressedOutput());
      helper.assertTrue(result > 0, "FTB Chunks did not claim the owner's chunk");
    } catch (Exception failure) {
      throw new IllegalStateException("FTB Chunks claim failed", failure);
    }
    level.setBlockAndUpdate(altarPos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
    var altar = (RenewalAltarEntity) level.getBlockEntity(altarPos);
    altar.setOwner(visitor.player.getUUID(), visitor.player.getGameProfile().getName());
    altar.configureRadius(10);
    RenewalAltarEntity.SETUPS.put(GlobalPos.of(level.dimension(), altarPos), world -> flat(helper));
    visitor.player.teleportTo(altarPos.getX() + 0.5, altarPos.getY(), altarPos.getZ() + 1.8);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    await(helper, () -> altar.state() == RenewalAltarEntity.State.DONE, 0, 2000, "Claimed renewal", () -> {
      try {
        Map<Boolean, Integer> filled = new HashMap<>();
        for (BlockPos top : pit) {
          boolean inClaim = new ChunkPos(top).equals(claimed);
          boolean restored = level.getBlockState(top).is(Blocks.GRASS_BLOCK);
          helper.assertTrue(inClaim ? level.getBlockState(top).isAir() && level.getBlockState(top.below(2)).isAir() : restored,
              "The claim boundary was not respected at " + top);
          filled.merge(inClaim, 1, Integer::sum);
        }
        helper.assertTrue(altar.totals().charge == altar.totals().filled + altar.totals().covered
            && altar.totals().filled == 3 * filled.getOrDefault(false, 0),
            "Refused claimed work was charged: " + altar.totals().charge);
        RenewalAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altarPos));
        level.removeBlock(altarPos, false);
        // The Terraform Altar of the same visitor: columns inside the claim are refused whole.
        level.setBlockAndUpdate(altarPos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
        var terraform = (TerraformAltarEntity) level.getBlockEntity(altarPos);
        terraform.setOwner(visitor.player.getUUID(), visitor.player.getGameProfile().getName());
        terraform.configureSize(8);
        terraform.setItem(0, new ItemStack(Items.DIRT, 64));
        Map<BlockPos, BlockState> claimedBefore = new HashMap<>();
        for (int x = claimed.getMinBlockX(); x <= claimed.getMaxBlockX(); x++)
          for (int z = claimed.getMinBlockZ(); z <= claimed.getMaxBlockZ(); z++)
            for (int y = altarPos.getY() - 4; y <= altarPos.getY() + 4; y++) {
              BlockPos pos = new BlockPos(x, y, z);
              claimedBefore.put(pos, level.getBlockState(pos));
            }
        // The renewal pass left the land outside the claim level: dig a small pit just west of the
        // claimed chunk, inside the square, so the work that goes on there includes fills.
        for (int x = claimed.getMinBlockX() - 3; x < claimed.getMinBlockX(); x++)
          for (int dy = -1; dy >= -2; dy--)
            level.setBlock(new BlockPos(x, altarPos.getY() + dy, altarPos.getZ() - 5), Blocks.AIR.defaultBlockState(), 2);
        terraform.addFuel(new ItemStack(Items.COAL, 4));
        terraform.start(level);
        await(helper, () -> terraform.state() == TerraformAltarEntity.State.DONE, 0, 1600, "Claimed terraform", () -> {
          try {
            claimedBefore.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state),
                "A foreign Terraform Altar changed the claimed chunk at " + pos));
            var done = terraform.totals();
            helper.assertTrue(terraform.refusals().getOrDefault("claim", 0) > 0 && done.filled > 0,
                "The claim did not refuse columns or nothing outside was filled: " + terraform.refusals()
                    + " columns=" + done.columns + " cut=" + done.cut + " filled=" + done.filled + " swapped=" + done.swapped);
            helper.succeed();
          } finally {
            cleanup(owner, visitor, east);
          }
        }, () -> cleanup(owner, visitor, east));
      } catch (RuntimeException | Error failure) {
        cleanup(owner, visitor, east);
        throw failure;
      }
    }, () -> cleanup(owner, visitor, east));
  }

  private static void cleanup(Session owner, Session visitor, BlockPos east) {
    try {
      owner.player.teleportTo(east.getX() + 0.5, east.getY() + 1.0, east.getZ() + 0.5);
      owner.player.server.getCommands().getDispatcher().execute("ftbchunks unclaim",
          owner.player.createCommandSourceStack().withSuppressedOutput());
    } catch (Exception ignored) {
      // The QA world is archived before runs; a leftover claim is reported by the next run.
    } finally {
      owner.close();
      visitor.close();
    }
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final EmbeddedChannel channel;

    Session(GameTestHelper helper, String name) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
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
        connection.disconnect(Component.literal("Entrelumen altar QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }
}
