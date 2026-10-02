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
 * Isolated GameTests for Terra's hydroponic garden, the 3 x 2 x 2 engine (docs/design/terra-garden.md):
 * it stands in every rotation, the grow lamp goes into it and comes back when a block breaks, one batch makes the documented
 * harvest and the outlets export it, the excluded seeds and forbidden drops, and the stop when the store is full. The
 * fixture mod adds torchflower seeds to the exclusion tag and beetroot seeds to the forbidden drops, so
 * the vanilla-only server has something to refuse. Excluded from the distributable jar.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsTerraGarden {
  /** Where the core stands in the 11 x 12 x 11 plot: the front of the engine's middle column, bottom layer. */
  private static final BlockPos CENTRE = new BlockPos(5, 1, 5);
  /** The outlet, behind the core in the drawing (facing south). */
  private static final BlockPos OUTLET = new BlockPos(0, 0, -1);

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

  /** The core's absolute position; the engine turns about it, and fits the plot in every rotation. */
  private static BlockPos corePos(GameTestHelper helper, int rotation) {
    return helper.absolutePos(CENTRE);
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
      // one outlet gone: the garden no longer stands
      BlockPos hole = TerraGardenLayout.world(pos, r, OUTLET);
      helper.assertTrue(helper.getLevel().getBlockState(hole).is(TerraGarden.OUTLET.get()), "rotation " + r + ": no outlet where expected");
      helper.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      core.check(helper.getLevel());
      helper.assertTrue(!core.standing(), "rotation " + r + " stands without an outlet");
      helper.getLevel().setBlock(hole, TerraGarden.OUTLET.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      core.check(helper.getLevel());
      helper.assertTrue(core.standing(), "rotation " + r + " does not stand again once repaired");
      clear(helper, pos, r);
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGrowLampGoesIntoTheEngineAndComesBackWhenABlockBreaks(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = corePos(helper, 0);
    level.setBlock(pos, TerraGarden.CORE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    try (var session = new Session(helper, new BlockPos(5, 1, 10))) {
      var player = session.player;
      for (String recipe : java.util.List.of("terra_garden_plan_copy", "terra_garden_core", "terra_garden_outlet",
          "terra_grow_lamp", "terralight_grounding_rod"))
        helper.assertTrue(level.getRecipeManager().byKey(TerraGarden.id(recipe)).isPresent(),
            "The recipe entrelumen:" + recipe + " did not load");
      var lamps = new ItemStack(TerraGarden.GROW_LAMP.get(), 2);
      // a lone core is not an engine: the lamp stays in the hand
      useOn(player, pos, lamps);
      helper.assertTrue(!core(helper, pos).awake() && player.getMainHandItem().getCount() == 2,
          "A lamp went into a core without its engine");
      build(helper, 0, false);
      useOn(player, pos, player.getMainHandItem());
      var core = core(helper, pos);
      helper.assertTrue(core.awake() && core.standing(), "The lamp did not wake the built engine");
      helper.assertTrue(player.getMainHandItem().getCount() == 1, "Waking the engine did not use the lamp up");
      helper.assertTrue(level.getBlockState(pos).getValue(TerraGardenCoreBlock.GARDEN) == TerraGardenCoreBlock.Garden.GROWING,
          "The woken pot does not glow");
      helper.assertTrue(player.getStats().getValue(Stats.CUSTOM.get(TerraGarden.ACTIVATIONS.get())) == 1, "No activation counted");
      // formed: every member stops drawing itself and the core draws the engine
      for (var part : TerraGardenLayout.builtin().parts()) {
        if (part.core()) continue;
        var member = level.getBlockState(TerraGardenLayout.world(pos, 0, part.offset()));
        helper.assertTrue(member.getValue(TerraEngineMemberBlock.FORMED)
            && member.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE, "A member did not form: " + member);
      }
      // one lamp per engine: a second lamp stays in the hand
      useOn(player, pos, player.getMainHandItem());
      helper.assertTrue(player.getMainHandItem().getCount() == 1, "An awake engine took a second lamp");
      // a player breaks a block of the engine: the block and the lamp drop right there
      BlockPos grille = TerraGardenLayout.world(pos, 0, new BlockPos(-1, 0, 0));
      helper.assertTrue(level.getBlockState(grille).is(TerraGarden.CASING.get()), "No casing where the drawing puts one");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
      player.gameMode.destroyBlock(grille);
      var box = new net.minecraft.world.phys.AABB(grille).inflate(1.5);
      var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
      helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(TerraGarden.GROW_LAMP.get())), "The lamp did not drop: " + drops);
      helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(TerraGarden.CASING_ITEM.get())), "The casing did not drop");
      helper.assertTrue(!core.awake(), "The engine stayed awake without its lamp");
      BlockPos other = TerraGardenLayout.world(pos, 0, new BlockPos(1, 1, -1));
      helper.assertTrue(!level.getBlockState(other).getValue(TerraEngineMemberBlock.FORMED), "The engine did not unform");
      drops.forEach(net.minecraft.world.entity.Entity::discard);
      // any other loss (here a block simply vanishing) gives the lamp back at the next check
      level.setBlock(grille, TerraGarden.CASING.get().defaultBlockState(), Block.UPDATE_ALL);
      useOn(player, pos, new ItemStack(TerraGarden.GROW_LAMP.get()));
      helper.assertTrue(core.awake(), "The repaired engine did not take a new lamp");
      BlockPos corner = TerraGardenLayout.world(pos, 0, new BlockPos(1, 1, -1));
      level.setBlock(corner, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      core.check(level);
      var back = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(4));
      helper.assertTrue(!core.awake() && back.stream().anyMatch(e -> e.getItem().is(TerraGarden.GROW_LAMP.get())),
          "A block lost without a player did not give the lamp back");
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGardenMakesItsHarvestInOneBatchAndExports(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = build(helper, 0, false);
    // behind the engine, touching an outlet from outside
    BlockPos outletPos = TerraGardenLayout.world(pos, 0, OUTLET);
    BlockPos chestPos = outletPos.relative(Direction.NORTH);
    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
    try (var session = new Session(helper, new BlockPos(5, 1, 10))) {
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
      helper.assertTrue(chestWheat > 0, "Nothing went out of the outlet into the chest behind the engine");
      helper.assertTrue(wheat == TerraGardenRules.HARVESTS_PER_BATCH,
          "Ripe wheat drops one wheat a harvest: expected " + TerraGardenRules.HARVESTS_PER_BATCH + ", got " + wheat);
      helper.assertTrue(count(core, Items.WHEAT_SEEDS) > TerraGardenRules.HARVESTS_PER_BATCH / 2,
          "Seeds should come at about 1.7 a harvest: " + count(core, Items.WHEAT_SEEDS));
      helper.assertTrue(session.player.getStats().getValue(Stats.CUSTOM.get(TerraGarden.HARVESTS.get()))
          == TerraGardenRules.HARVESTS_PER_BATCH, "The harvests were not credited to the waker");
      // a pipe on an outlet reaches the core's store; an outlet of no engine offers nothing
      var outletHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, outletPos, Direction.NORTH);
      helper.assertTrue(outletHandler != null && !outletHandler.getStackInSlot(1).isEmpty(), "The outlet does not show the store");
      level.setBlock(chestPos, TerraGarden.OUTLET.get().defaultBlockState(), Block.UPDATE_ALL);
      // (a loose outlet next to the engine is no member of it)
      helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, chestPos, Direction.UP) == null,
          "A loose outlet offers a store");
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
    try (var session = new Session(helper, new BlockPos(10, 1, 5))) {
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
    try (var session = new Session(helper, new BlockPos(0, 1, 5))) {
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

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraGardenIgnoresTheGrowthAltarButAHandHarvestInItsFieldStillDoubles(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = build(helper, 0, false);
    try (var session = new Session(helper, new BlockPos(5, 1, 10))) {
      var core = core(helper, pos);
      core.insertSeed(session.player, new ItemStack(Items.WHEAT_SEEDS));
      useOn(session.player, pos, new ItemStack(TerraGarden.GROW_LAMP.get()));
      helper.assertTrue(core.awake(), "Not awake");
      // the baseline: one batch with no altar
      core.runSoon();
      core.serverTick(level);
      long baseline = count(core, Items.WHEAT);
      helper.assertTrue(baseline == TerraGardenRules.HARVESTS_PER_BATCH, "The baseline batch made " + baseline + " wheat");
      // an active Altar of Growth in front of the engine, whose field (kept inside the plot) covers the core
      BlockPos altarPos = helper.absolutePos(new BlockPos(5, 1, 8));
      level.setBlockAndUpdate(altarPos.below(), Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(altarPos, Altars.GROWTH_ALTAR.get().defaultBlockState());
      var altar = (GrowthAltarEntity) level.getBlockEntity(altarPos);
      altar.configureArea(3, 1);
      altar.configureSamples(0);   // only its field and its loot bonus: no random ticks on the engine's vines
      var fuel = level.getCapability(Capabilities.ItemHandler.BLOCK, altarPos, Direction.UP);
      helper.assertTrue(fuel != null && fuel.insertItem(0, new ItemStack(Items.BONE_BLOCK, 4), false).isEmpty(),
          "The altar did not take its bone blocks");
      altar.serverTick(level);
      helper.assertTrue(AltarRegistry.isInsideActive(level, AltarType.GROWTH, pos), "The core is not inside the active field");
      core.runSoon();
      core.serverTick(level);
      long withAltar = count(core, Items.WHEAT) - baseline;
      helper.assertTrue(withAltar == baseline, "A batch beside an active Altar of Growth made " + withAltar
          + " wheat instead of " + baseline);
      // a ripe wheat broken by hand inside the same field still drops twice
      BlockPos field = helper.absolutePos(new BlockPos(6, 1, 9));
      var ripe = Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
      long wheat = Block.getDrops(ripe, level, field, null).stream().filter(stack -> stack.is(Items.WHEAT))
          .mapToLong(ItemStack::getCount).sum();
      helper.assertTrue(wheat == 2, "Ripe wheat inside the field dropped " + wheat + " wheat, not 2");
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 200)
  public static void terraEngineMembersCannotBePushedAndACoreTakenWithoutItsEntityUnformsThem(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    helper.assertTrue(TerraGarden.CASING.get().defaultBlockState().getPistonPushReaction()
        == net.minecraft.world.level.material.PushReaction.BLOCK, "Pistons and contraptions can push a casing");
    helper.assertTrue(TerraGarden.OUTLET.get().defaultBlockState().getPistonPushReaction()
        == net.minecraft.world.level.material.PushReaction.BLOCK, "Pistons and contraptions can push an outlet");
    int rotation = 2;
    BlockPos pos = build(helper, rotation, false);
    try (var session = new Session(helper, new BlockPos(5, 1, 0))) {
      useOn(session.player, pos, new ItemStack(TerraGarden.GROW_LAMP.get()));
      helper.assertTrue(core(helper, pos).awake(), "Not awake");
    }
    helper.assertTrue(level.getBlockState(pos).getValue(TerraGardenCoreBlock.GARDEN) == TerraGardenCoreBlock.Garden.GROWING,
        "The woken core does not show it");
    // a contraption takes the core's entity first and then the block: the members left behind unform
    level.removeBlockEntity(pos);
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    for (var part : TerraGardenLayout.builtin().parts()) {
      if (part.core()) continue;
      var member = level.getBlockState(TerraGardenLayout.world(pos, rotation, part.offset()));
      helper.assertTrue(!(member.getBlock() instanceof TerraEngineMemberBlock) || !member.getValue(TerraEngineMemberBlock.FORMED),
          "A member stayed formed and invisible: " + member);
    }
    helper.succeed();
  }

  @GameTest(template = "terra_garden_plot", timeoutTicks = 100)
  public static void terraGardenCoreKeepsItsSeedAndStoreWhenBroken(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = corePos(helper, 0);
    level.setBlock(pos, TerraGarden.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    try (var session = new Session(helper, new BlockPos(5, 1, 10))) {
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
