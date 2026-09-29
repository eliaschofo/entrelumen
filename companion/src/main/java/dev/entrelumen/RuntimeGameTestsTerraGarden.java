package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for Terra's hydroponic garden (docs/design/terra-garden.md): the garden stands in
 * every rotation, the grow lamp wakes it and is never used up, one batch makes the documented harvest
 * and exports it, the excluded seeds and forbidden drops, and the stop when the store is full. The
 * fixture mod adds torchflower seeds to the exclusion tag and beetroot seeds to the forbidden drops, so
 * the vanilla-only server has something to refuse. Excluded from the distributable jar.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsTerraGarden {
  /** The garden's centre in the 11 x 12 x 11 plot; the core sits three blocks out along the front. */
  private static final BlockPos CENTRE = new BlockPos(5, 1, 5);

  private RuntimeGameTestsTerraGarden() {}

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Session(GameTestHelper helper, BlockPos relative) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "terra_gardener"), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
      var pos = helper.absolutePos(relative);
      player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Terra garden QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** The core's absolute position for a garden centred on {@link #CENTRE} at rotation {@code r}. */
  private static BlockPos corePos(GameTestHelper helper, int rotation) {
    BlockPos centre = helper.absolutePos(CENTRE);
    return centre.offset(new BlockPos(0, 0, 3).rotate(TerraGardenLayout.rotation(rotation)));
  }

  /**
   * Builds the garden exactly as the plan draws it, turned by {@code r}, without neighbour updates (so a
   * hanging lantern placed before its ceiling stays). {@code weathered} swaps oxidized copper for waxed
   * weathered copper, which the core must accept too.
   */
  private static BlockPos build(GameTestHelper helper, int rotation, boolean weathered) {
    ServerLevel level = helper.getLevel();
    BlockPos core = corePos(helper, rotation);
    for (var part : TerraGardenLayout.builtin().parts()) {
      BlockPos at = TerraGardenLayout.world(core, rotation, part.offset());
      BlockState state;
      if (part.core()) {
        state = TerraGarden.CORE.get().defaultBlockState()
            .setValue(TerraGardenCoreBlock.FACING, TerraGardenLayout.front(rotation));
      } else {
        String text = part.state();
        if (weathered && text.startsWith("minecraft:oxidized_"))
          text = "minecraft:waxed_weathered_" + text.substring("minecraft:oxidized_".length());
        try {
          state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState()
              .rotate(TerraGardenLayout.rotation(rotation));
        } catch (Exception invalid) {
          throw new IllegalStateException("Bad layout state " + text, invalid);
        }
      }
      level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    return core;
  }

  private static void clear(GameTestHelper helper, BlockPos core, int rotation) {
    for (var part : TerraGardenLayout.builtin().parts())
      helper.getLevel().setBlock(TerraGardenLayout.world(core, rotation, part.offset()), Blocks.AIR.defaultBlockState(),
          Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
  }

  private static TerraGardenCoreEntity core(GameTestHelper helper, BlockPos core) {
    if (helper.getLevel().getBlockEntity(core) instanceof TerraGardenCoreEntity entity) return entity;
    throw new IllegalStateException("No garden core at " + core.toShortString());
  }

  /** Right-clicks the core with the item in the player's main hand (the item's own use). */
  private static void useOn(ServerPlayer player, BlockPos core, ItemStack held) {
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    var hit = new BlockHitResult(Vec3.atCenterOf(core), Direction.UP, core, false);
    held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
  }

  private static long count(TerraGardenCoreEntity core, net.minecraft.world.item.Item item) {
    return core.storeView().entrySet().stream().filter(e -> e.getKey().is(item)).mapToLong(java.util.Map.Entry::getValue).sum();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGardenStandsInEveryRotation(GameTestHelper helper) {
    for (int r = 0; r < 4; r++) {
      BlockPos pos = build(helper, r, r % 2 == 1);
      var core = core(helper, pos);
      core.check(helper.getLevel());
      helper.assertTrue(core.standing() && core.rotation() == r, "rotation " + r + " did not stand: " + core.rotation());
      helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(TerraGardenCoreBlock.GARDEN)
          == TerraGardenCoreBlock.Garden.BUILT, "rotation " + r + ": the core does not show a built garden");
      helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(TerraGardenCoreBlock.FACING)
          == TerraGardenLayout.front(r), "rotation " + r + ": the core does not look out of the front");
      // one trough gone: the garden no longer stands
      var trough = TerraGardenLayout.builtin().parts().stream().filter(p -> p.block().getPath().equals("hydroponic_trough"))
          .findFirst().orElseThrow();
      BlockPos hole = TerraGardenLayout.world(pos, r, trough.offset());
      helper.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      core.check(helper.getLevel());
      helper.assertTrue(!core.standing(), "rotation " + r + " stands without a trough");
      helper.getLevel().setBlock(hole, TerraGarden.TROUGH.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      core.check(helper.getLevel());
      helper.assertTrue(core.standing(), "rotation " + r + " does not stand again once repaired");
      clear(helper, pos, r);
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGrowLampWakesABuiltGardenAndIsNeverUsedUp(GameTestHelper helper) {
    BlockPos pos = corePos(helper, 0);
    helper.getLevel().setBlock(pos, TerraGarden.CORE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    try (var session = new Session(helper, CENTRE.offset(0, 0, 5))) {
      var player = session.player;
      var lamp = new ItemStack(TerraGarden.GROW_LAMP.get());
      helper.assertTrue(lamp.getMaxStackSize() == 1 && !lamp.isDamageableItem() && lamp.getMaxDamage() == 0,
          "The lamp stacks or has durability");
      for (String recipe : java.util.List.of("terra_garden_plan_copy", "terra_garden_core", "hydroponic_trough"))
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(TerraGarden.id(recipe)).isPresent(),
            "The recipe entrelumen:" + recipe + " did not load");
      // a lone core is not a garden: the lamp does nothing
      useOn(player, pos, lamp);
      helper.assertTrue(!core(helper, pos).awake(), "The lamp woke a core without its garden");
      build(helper, 0, false);
      useOn(player, pos, lamp);
      var core = core(helper, pos);
      helper.assertTrue(core.awake() && core.standing(), "The lamp did not wake the built garden");
      helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(TerraGardenCoreBlock.GARDEN)
          == TerraGardenCoreBlock.Garden.GROWING, "The woken core does not show a growing garden");
      helper.assertTrue(player.getMainHandItem().is(TerraGarden.GROW_LAMP.get()) && player.getMainHandItem().getCount() == 1
          && player.getMainHandItem().getDamageValue() == 0, "The lamp was used up or damaged");
      helper.assertTrue(player.getStats().getValue(Stats.CUSTOM.get(TerraGarden.ACTIVATIONS.get())) == 1,
          "The activation was not counted");
      // the same lamp wakes a second garden: move the first one's core out and build another
      clear(helper, pos, 0);
      BlockPos second = build(helper, 2, false);
      useOn(player, second, player.getMainHandItem());
      helper.assertTrue(core(helper, second).awake(), "One lamp did not wake a second garden");
      helper.assertTrue(player.getStats().getValue(Stats.CUSTOM.get(TerraGarden.ACTIVATIONS.get())) == 2,
          "The second activation was not counted");
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGardenMakesItsHarvestInOneBatchAndExports(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = build(helper, 0, false);
    BlockPos chestPos = pos.relative(Direction.SOUTH);
    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
    try (var session = new Session(helper, CENTRE.offset(0, 0, 5))) {
      var core = core(helper, pos);
      core.insertSeed(session.player, new ItemStack(Items.WHEAT_SEEDS));
      useOn(session.player, pos, new ItemStack(TerraGarden.GROW_LAMP.get()));
      helper.assertTrue(core.awake(), "Not awake");
      core.runSoon();
      core.serverTick(level);
      // an accelerator ticking it again in the same game tick gains nothing
      for (int i = 0; i < 50; i++) core.serverTick(level);
      helper.assertTrue(core.harvests() == TerraGardenRules.HARVESTS_PER_BATCH,
          "One batch made " + core.harvests() + " harvests, not " + TerraGardenRules.HARVESTS_PER_BATCH);
      long chestWheat = 0;
      if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
          if (chest.getItem(slot).is(Items.WHEAT)) chestWheat += chest.getItem(slot).getCount();
      long wheat = chestWheat + count(core, Items.WHEAT);
      helper.assertTrue(chestWheat > 0, "Nothing went into the chest in front of the core");
      helper.assertTrue(wheat == TerraGardenRules.HARVESTS_PER_BATCH,
          "Ripe wheat drops one wheat a harvest: expected " + TerraGardenRules.HARVESTS_PER_BATCH + ", got " + wheat);
      helper.assertTrue(count(core, Items.WHEAT_SEEDS) > TerraGardenRules.HARVESTS_PER_BATCH / 2,
          "Seeds should come at about 1.7 a harvest: " + count(core, Items.WHEAT_SEEDS));
      helper.assertTrue(session.player.getStats().getValue(Stats.CUSTOM.get(TerraGarden.HARVESTS.get()))
          == TerraGardenRules.HARVESTS_PER_BATCH, "The harvests were not credited to the waker");
      // a pipe can pull the store through the capability, and never the seed
      var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
      helper.assertTrue(handler != null, "No item capability on the core");
      helper.assertTrue(handler.extractItem(0, 1, false).isEmpty() && core.seed().is(Items.WHEAT_SEEDS),
          "A pipe pulled the seed out");
      long before = core.storeView().values().stream().mapToLong(Long::longValue).sum();
      var pulled = handler.extractItem(1, 64, false);
      helper.assertTrue(!pulled.isEmpty() && core.storeView().values().stream().mapToLong(Long::longValue).sum()
          == before - pulled.getCount(), "Pulling from an output slot did not take from the store");
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGardenRefusesExcludedSeedsAndForbiddenDrops(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = build(helper, 1, false);
    try (var session = new Session(helper, CENTRE.offset(5, 0, 0))) {
      var core = core(helper, pos);
      var torchflower = new ItemStack(Items.TORCHFLOWER_SEEDS, 3);
      helper.assertTrue(torchflower.is(TerraGarden.EXCLUDED_SEEDS), "The fixture did not exclude torchflower seeds");
      core.insertSeed(session.player, torchflower);
      helper.assertTrue(core.seed().isEmpty() && torchflower.getCount() == 3, "An excluded seed went in");
      var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
      helper.assertTrue(handler.insertItem(0, torchflower, false).getCount() == 3 && core.seed().isEmpty(),
          "A pipe put an excluded seed in");
      helper.assertTrue(TerraGarden.crop(new ItemStack(Items.DIAMOND)) == null && handler.insertItem(0,
          new ItemStack(Items.DIAMOND), true).getCount() == 1, "Something that is not a seed went in");
      // beetroot: its seeds are forbidden drops here, so only beetroots come out
      helper.assertTrue(handler.insertItem(0, new ItemStack(Items.BEETROOT_SEEDS), false).isEmpty(),
          "A pipe could not plant beetroot seeds");
      useOn(session.player, pos, new ItemStack(TerraGarden.GROW_LAMP.get()));
      core.runSoon();
      core.serverTick(level);
      helper.assertTrue(core.harvests() == TerraGardenRules.HARVESTS_PER_BATCH, "No beetroot batch");
      helper.assertTrue(count(core, Items.BEETROOT) == TerraGardenRules.HARVESTS_PER_BATCH,
          "Ripe beetroot drops one beetroot a harvest: " + count(core, Items.BEETROOT));
      helper.assertTrue(count(core, Items.BEETROOT_SEEDS) == 0, "The garden made a forbidden drop");
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGardenStopsCleanlyWhenFull(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = build(helper, 3, false);
    try (var session = new Session(helper, CENTRE.offset(-5, 0, 0))) {
      var core = core(helper, pos);
      core.insertSeed(session.player, new ItemStack(Items.WHEAT_SEEDS));
      useOn(session.player, pos, new ItemStack(TerraGarden.GROW_LAMP.get()));
      core.fillStore(new ItemStack(Items.DIRT), TerraGardenRules.STORE_CAPACITY);
      core.runSoon();
      core.serverTick(level);
      helper.assertTrue(core.harvests() == 0 && core.lastStatus() == TerraGardenCoreEntity.Status.FULL,
          "A full core kept growing: " + core.harvests() + ", " + core.lastStatus());
      helper.assertTrue(core.storeView().values().stream().mapToLong(Long::longValue).sum() == TerraGardenRules.STORE_CAPACITY,
          "The store changed while full");
      helper.assertTrue(level.getBlockState(pos).getAnalogOutputSignal(level, pos) == 15, "A full core does not signal 15");
      // room for 10,000 items: the batch is cut evenly to fit, and nothing overflows
      var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
      long taken = 0;
      while (taken < 10_000) taken += handler.extractItem(1, 64, false).getCount();
      core.runSoon();
      core.serverTick(level);
      long stored = core.storeView().values().stream().mapToLong(Long::longValue).sum();
      helper.assertTrue(stored <= TerraGardenRules.STORE_CAPACITY, "The store overflowed: " + stored);
      helper.assertTrue(core.harvests() > 0 && core.harvests() < TerraGardenRules.HARVESTS_PER_BATCH,
          "A batch cut to fit made " + core.harvests() + " harvests");
      helper.assertTrue(core.lastStatus() == TerraGardenCoreEntity.Status.FULL, "A cut batch did not report the store full");
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 100)
  public static void terraGardenCoreKeepsItsSeedAndStoreWhenBroken(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = corePos(helper, 0);
    level.setBlock(pos, TerraGarden.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    try (var session = new Session(helper, CENTRE.offset(0, 0, 5))) {
      var core = core(helper, pos);
      core.insertSeed(session.player, new ItemStack(Items.CARROT));
      core.fillStore(new ItemStack(Items.CARROT), 5_000);
      var drops = Block.getDrops(level.getBlockState(pos), level, pos, core, session.player,
          new ItemStack(Items.DIAMOND_PICKAXE));
      helper.assertTrue(drops.size() == 1 && drops.getFirst().is(TerraGarden.CORE_ITEM.get()), "The core drops " + drops);
      var contents = drops.getFirst().get(TerraGarden.CONTENTS.get());
      helper.assertTrue(contents != null && contents.seed().is(Items.CARROT) && contents.store().size() == 1
          && contents.store().getFirst().count() == 5_000, "The core item lost its seed or store: " + contents);
      // placed again, it has them back
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      level.setBlock(pos, TerraGarden.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
      core(helper, pos).applyComponentsFromItemStack(drops.getFirst());
      helper.assertTrue(core(helper, pos).seed().is(Items.CARROT) && count(core(helper, pos), Items.CARROT) == 5_000,
          "Placing the core again did not restore its contents");
      helper.assertTrue(!core(helper, pos).awake(), "A moved core stayed awake");
    }
    helper.succeed();
  }
}
