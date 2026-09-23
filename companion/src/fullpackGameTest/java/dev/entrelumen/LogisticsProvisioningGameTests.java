package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Installed-pack QA only: real Pipez ticks, native gestures and exact inventory conservation. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class LogisticsProvisioningGameTests {
  private LogisticsProvisioningGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 900)
  public static void logisticsPipezDepotPreparesTwoExactPersonalKits(GameTestHelper helper) throws Exception {
    BlockPos controller = TeamRestartGameTests.ark(helper);
    BlockPos module = module(helper, controller);
    BlockPos pipePos = module.above();
    BlockPos sourcePos = pipePos.above();
    var level = helper.getLevel();
    Block pipe = block("pipez:item_pipe");
    level.setBlockAndUpdate(sourcePos, Blocks.BARREL.defaultBlockState());
    level.setBlockAndUpdate(pipePos, pipe.defaultBlockState());
    Container source = (Container) level.getBlockEntity(sourcePos);
    Container depot = stock(helper, module);
    List<ItemStack> supplied = new ArrayList<>();
    for (int i = 0; i < 8; i++) supplied.add(new ItemStack(item("farmersdelight:beef_stew")));
    for (int i = 0; i < 4; i++) supplied.add(new ItemStack(item("farmersdelight:chicken_soup")));
    supplied.addAll(List.of(new ItemStack(Items.ARROW, 12), rocket(1, 9), healing(), healing(),
        rocket(3, 6), swift(), swift()));
    for (int i = 0; i < supplied.size(); i++) source.setItem(i, supplied.get(i).copy());
    helper.assertTrue(depot.isEmpty(), "New logistics depot was not empty");
    // Use the pinned mod's normal extraction configuration, then let its own server ticks move stock.
    pipe.getClass().getMethod("setExtracting", Level.class, BlockPos.class,
        Direction.class, boolean.class).invoke(pipe, level, pipePos, Direction.UP, true);
    awaitPipe(helper, controller, module, source, supplied, 0);
  }

  private static void awaitPipe(GameTestHelper helper, BlockPos controller, BlockPos module,
      Container source, List<ItemStack> supplied, int waited) {
    Container depot = stock(helper, module);
    assertConserved(helper, supplied, concat(snapshot(source), snapshot(depot)), "Pipez transfer");
    if (!source.isEmpty()) {
      helper.assertTrue(waited < 700, "Native Pipez did not empty its source into the Ark depot");
      helper.runAfterDelay(10, () -> awaitPipe(helper, controller, module, source, supplied, waited + 10));
      return;
    }
    try (var first = connect(helper, "KitA"); var second = connect(helper, "KitB")) {
      ServerPlayer a = first.player;
      ServerPlayer b = second.player;
      near(a, module);
      near(b, module);
      Entrelumen.current(a);
      Entrelumen.current(b);
      var data = CampaignData.get(a.server);
      CompoundTag campaign = data.save(new CompoundTag(), helper.getLevel().registryAccess());
      List<ItemStack> kitA = List.of(new ItemStack(item("farmersdelight:beef_stew")),
          new ItemStack(item("farmersdelight:beef_stew")),
          new ItemStack(Items.ARROW, 4), rocket(1, 3), healing());
      List<ItemStack> kitB = List.of(new ItemStack(item("farmersdelight:chicken_soup"), 1),
          rocket(3, 2), swift());
      recordNative(helper, a, module, kitA);
      recordNative(helper, b, module, kitB);
      reset(a);
      reset(b);
      use(a, module, false);
      assertKit(helper, a, kitA);
      use(b, module, false);
      assertKit(helper, b, kitB);
      assertConserved(helper, supplied, concat(snapshot(depot), supplies(a), supplies(b)),
          "Consecutive personal kits");
      List<ItemStack> depotBefore = snapshot(depot);
      List<ItemStack> aBefore = snapshot(a.getInventory());
      List<ItemStack> bBefore = snapshot(b.getInventory());
      use(a, module, false);
      use(b, module, false);
      same(helper, depotBefore, snapshot(depot), "Completed kits consumed depot stock");
      same(helper, aBefore, snapshot(a.getInventory()), "Completed first kit changed player inventory");
      same(helper, bBefore, snapshot(b.getInventory()), "Completed second kit changed player inventory");
      helper.assertTrue(campaign.equals(data.save(new CompoundTag(), helper.getLevel().registryAccess())),
          "Logistics service changed story/team campaign state");
      helper.assertTrue(a.getMainHandItem().is(item("entrelumen:routing_matrix"))
          && b.getMainHandItem().is(item("entrelumen:routing_matrix")), "Reusable matrix was consumed");
      LogUtils.getLogger().info("ENTRELUMEN_LOGISTICS_PIPEZ twoPlayers=true nativeGestures=true "
          + "food=farmersdelight rockets=1,3 potions=healing,swiftness components=exact replay=unchanged");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void logisticsAllOrNothingPreservesVariantsAndRejectedProfiles(GameTestHelper helper) {
    BlockPos module = module(helper, TeamRestartGameTests.ark(helper));
    Container depot = stock(helper, module);
    try (var session = connect(helper, "KitAtomic")) {
      ServerPlayer player = session.player;
      near(player, module);
      List<ItemStack> kit = List.of(new ItemStack(Items.ARROW, 4), rocket(1, 3), healing());
      recordNative(helper, player, module, kit);
      reset(player);
      depot.setItem(0, new ItemStack(Items.ARROW, 4));
      depot.setItem(1, rocket(3, 3));
      depot.setItem(2, healing());
      unchangedAttempt(helper, player, module, depot, "Wrong rocket components consumed a partial kit");
      depot.setItem(1, rocket(1, 3));
      var otherPotion = new ItemStack(Items.POTION);
      otherPotion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
      depot.setItem(2, otherPotion);
      unchangedAttempt(helper, player, module, depot, "Same potion item with wrong effect consumed a partial kit");
      depot.setItem(2, healing());
      depot.setItem(1, rocket(1, 2));
      unchangedAttempt(helper, player, module, depot, "One missing rocket consumed a partial kit");
      depot.setItem(1, rocket(1, 3));
      for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
      unchangedAttempt(helper, player, module, depot, "Full inventory consumed a partial kit");
      reset(player);
      // A rejected recording must leave the earlier useful profile intact.
      player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
      player.setShiftKeyDown(true);
      helper.assertTrue(LogisticsProvisioning.record(player, module).status()
          == LogisticsProvisioning.Status.UNSUPPORTED, "Damageable sample was accepted");
      var nested = new ItemStack(Items.SHULKER_BOX);
      nested.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
      player.getInventory().setItem(1, nested);
      helper.assertTrue(LogisticsProvisioning.record(player, module).status()
          == LogisticsProvisioning.Status.UNSUPPORTED, "Nested inventory sample was accepted");
      reset(player);
      player.setShiftKeyDown(true);
      helper.assertTrue(LogisticsProvisioning.record(player, module).status()
          == LogisticsProvisioning.Status.EMPTY_SELECTION, "Empty sample was accepted");
      // Offhand stock neither satisfies the kit nor gets moved.
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 12));
      use(player, module, false);
      assertKit(helper, player, kit);
      helper.assertTrue(player.getOffhandItem().getCount() == 12 && depot.isEmpty(),
          "Provisioning counted or changed offhand supplies");
      // Two hotbar samples of the same item form a target above one stack.
      reset(player);
      recordNative(helper, player, module, List.of(new ItemStack(Items.ARROW, 64), new ItemStack(Items.ARROW, 16)));
      reset(player);
      depot.setItem(0, new ItemStack(Items.ARROW, 64));
      depot.setItem(1, new ItemStack(Items.ARROW, 16));
      use(player, module, false);
      helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 80 && depot.isEmpty(),
          "Grouped target above stack size was truncated or duplicated");
      // A malformed future profile cannot create or consume anything.
      reset(player);
      depot.setItem(0, new ItemStack(Items.ARROW, 64));
      var savedKit = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
          .getCompound("entrelumen:logistics_kit");
      savedKit.putInt("version", 999);
      helper.assertTrue(LogisticsProvisioning.prepare(player, module).status()
          == LogisticsProvisioning.Status.INVALID_PROFILE, "Unknown saved profile version was accepted");
      unchangedAttempt(helper, player, module, depot, "Malformed saved profile changed inventory");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void logisticsNativeDenialAndStructureCannotMoveStock(GameTestHelper helper) {
    BlockPos controller = TeamRestartGameTests.ark(helper);
    BlockPos module = module(helper, controller);
    Container depot = stock(helper, module);
    try (var session = connect(helper, "KitDenied")) {
      ServerPlayer player = session.player;
      near(player, module);
      recordNative(helper, player, module, List.of(new ItemStack(Items.ARROW, 4)));
      reset(player);
      depot.setItem(0, new ItemStack(Items.ARROW, 4));
      Consumer<PlayerInteractEvent.RightClickBlock> cancel = event -> {
        if (event.getEntity() == player && event.getPos().equals(module)) event.setCanceled(true);
      };
      NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, cancel);
      try {
        unchangedAttempt(helper, player, module, depot, "Native canceled interaction moved stock");
      } finally {
        NeoForge.EVENT_BUS.unregister(cancel);
      }
      player.setGameMode(GameType.SPECTATOR);
      unchangedAttempt(helper, player, module, depot, "Spectator provisioned a kit");
      player.setGameMode(GameType.SURVIVAL);
      player.teleportTo(module.getX() + 20, module.getY(), module.getZ());
      unchangedAttempt(helper, player, module, depot, "Out-of-reach use provisioned a kit");
      near(player, module);
      helper.getLevel().setBlockAndUpdate(controller.above(), block("entrelumen:ark_controller").defaultBlockState());
      unchangedAttempt(helper, player, module, depot, "Ambiguous Ark provisioned a kit");
      helper.getLevel().setBlockAndUpdate(controller.above(), Blocks.AIR.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(controller, Blocks.AIR.defaultBlockState());
      unchangedAttempt(helper, player, module, depot, "Incomplete Ark provisioned a kit");
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      use(player, module, false);
      helper.assertTrue(player.containerMenu instanceof ChestMenu menu && menu.getContainer() == depot,
          "Incomplete Ark did not allow native depot recovery");
      player.closeContainer();
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void logisticsLegacyInventoryAndRemovalDoNotDuplicate(GameTestHelper helper) {
    BlockPos module = module(helper, TeamRestartGameTests.ark(helper));
    var level = helper.getLevel();
    // Simulate an existing block state whose old version had no saved block entity.
    level.removeBlockEntity(module);
    Container recovered = stock(helper, module);
    helper.assertTrue(recovered.isEmpty(), "Legacy module invented stock");
    recovered.setItem(0, new ItemStack(Items.ARROW, 11));
    recovered.setItem(1, rocket(3, 5));
    List<ItemStack> expected = snapshot(recovered);
    var retainedHandler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
        module, Direction.UP);
    helper.assertTrue(retainedHandler != null, "Depot did not expose automation capability");
    try (var session = connect(helper, "KitRemoved")) {
      ServerPlayer player = session.player;
      near(player, module);
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      use(player, module, false);
      var retainedMenu = player.containerMenu;
      helper.assertTrue(retainedMenu instanceof ChestMenu && retainedMenu.stillValid(player),
          "Native depot menu was not usable before removal");
      level.destroyBlock(module, false);
      level.removeBlock(module, false);
      helper.assertTrue(!retainedMenu.stillValid(player), "Removed depot retained a valid menu");
      helper.assertTrue(retainedHandler.extractItem(0, 64, false).isEmpty()
          && retainedHandler.getStackInSlot(0).isEmpty(), "Stale automation reference extracted duplicate goods");
      var rejected = retainedHandler.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
      helper.assertTrue(rejected.is(Items.DIAMOND) && rejected.getCount() == 3,
          "Stale automation reference swallowed an insertion");
      player.closeContainer();
    }
    List<ItemStack> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(module).inflate(1),
        ItemEntity::isAlive).stream().map(e -> e.getItem().copy()).toList();
    assertConserved(helper, expected, drops, "Depot removal drops");
    helper.assertTrue(recovered.isEmpty(), "Removed depot retained transferable goods");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void logisticsPersonalKitSurvivesNativeDeathClone(GameTestHelper helper) {
    BlockPos module = module(helper, TeamRestartGameTests.ark(helper));
    Container depot = stock(helper, module);
    try (var session = connect(helper, "KitRespawn")) {
      ServerPlayer original = session.player;
      near(original, module);
      var kit = List.of(new ItemStack(Items.ARROW, 4), rocket(3, 2));
      recordNative(helper, original, module, kit);
      var saved = original.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
          .getCompound("entrelumen:logistics_kit").copy();
      helper.assertTrue(!saved.isEmpty(), "Profile was absent before native respawn");
      original.getInventory().clearContent();
      original.setHealth(0);
      // Respawn through the native client command so the connection adopts the clone,
      // exactly as a real client does; a direct PlayerList call would leave it detached.
      original.connection.handleClientCommand(new ServerboundClientCommandPacket(
          ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
      ServerPlayer replacement = original.server.getPlayerList().getPlayer(original.getUUID());
      helper.assertTrue(replacement != null && replacement != original
          && replacement.connection == original.connection && replacement.connection.player == replacement
          && replacement.getUUID().equals(original.getUUID())
          && saved.equals(replacement.getPersistentData()
              .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
              .getCompound("entrelumen:logistics_kit")), "Native death clone lost the personal kit");
      near(replacement, module);
      reset(replacement);
      for (int i = 0; i < kit.size(); i++) depot.setItem(i, kit.get(i));
      use(replacement, module, false);
      assertKit(helper, replacement, kit);
      helper.assertTrue(depot.isEmpty(), "Respawned profile did not use exact physical stock");
    }
    helper.succeed();
  }

  static TeamRestartGameTests.NativePlayerSession connect(GameTestHelper helper, String name) {
    return TeamRestartGameTests.connect(helper, new GameProfile(UUID.randomUUID(), name), true);
  }

  static void near(ServerPlayer player, BlockPos module) {
    player.teleportTo(module.getX() + 0.5, module.getY() + 1, module.getZ() + 0.5);
    player.setGameMode(GameType.SURVIVAL);
    player.getInventory().selected = 0;
  }

  static void reset(ServerPlayer player) {
    player.getInventory().clearContent();
    player.getInventory().selected = 0;
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("entrelumen:routing_matrix")));
    player.setShiftKeyDown(false);
  }

  static void recordNative(GameTestHelper helper, ServerPlayer player, BlockPos module, List<ItemStack> kit) {
    reset(player);
    for (int i = 0; i < kit.size(); i++) player.getInventory().setItem(i + 1, kit.get(i).copy());
    List<ItemStack> before = snapshot(player.getInventory());
    use(player, module, true);
    same(helper, before, snapshot(player.getInventory()), "Recording consumed kit samples");
    player.setShiftKeyDown(false);
  }

  static void use(ServerPlayer player, BlockPos pos, boolean crouched) {
    player.setShiftKeyDown(crouched);
    player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
  }

  static BlockPos module(GameTestHelper helper, BlockPos controller) {
    for (BlockPos pos : BlockPos.betweenClosed(controller.offset(-1, 0, 1), controller.offset(1, 0, 2)))
      if (helper.getLevel().getBlockState(pos).is(block("entrelumen:logistics_module"))) return pos.immutable();
    throw new IllegalStateException("Logistics module missing from fixture");
  }

  static Container stock(GameTestHelper helper, BlockPos pos) {
    var blockEntity = helper.getLevel().getBlockEntity(pos);
    helper.assertTrue(blockEntity instanceof LogisticsStock, "Module lacks its own stock block entity");
    return (Container) blockEntity;
  }

  static Item item(String id) {
    var key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing native QA item " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  static Block block(String id) {
    var key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Missing native QA block " + id);
    return BuiltInRegistries.BLOCK.get(key);
  }

  static ItemStack rocket(int flight, int count) {
    var rocket = new ItemStack(Items.FIREWORK_ROCKET, count);
    rocket.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of()));
    rocket.set(DataComponents.CUSTOM_NAME, Component.literal("Expedition " + flight));
    return rocket;
  }

  static ItemStack healing() {
    var potion = new ItemStack(Items.POTION);
    potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
    return potion;
  }

  static ItemStack swift() {
    var potion = new ItemStack(Items.SPLASH_POTION);
    potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
    return potion;
  }

  static List<ItemStack> snapshot(Container inventory) {
    var result = new ArrayList<ItemStack>();
    for (int i = 0; i < inventory.getContainerSize(); i++) result.add(inventory.getItem(i).copy());
    return result;
  }

  private static List<ItemStack> supplies(ServerPlayer player) {
    return player.getInventory().items.stream().filter(s -> !s.is(item("entrelumen:routing_matrix")))
        .map(ItemStack::copy).toList();
  }

  static void assertKit(GameTestHelper helper, ServerPlayer player, List<ItemStack> kit) {
    assertConserved(helper, kit, supplies(player), "Personal kit");
  }

  private static void unchangedAttempt(GameTestHelper helper, ServerPlayer player, BlockPos module,
      Container stock, String message) {
    var beforePlayer = snapshot(player.getInventory());
    var beforeStock = snapshot(stock);
    use(player, module, false);
    same(helper, beforePlayer, snapshot(player.getInventory()), message + " (player)");
    same(helper, beforeStock, snapshot(stock), message + " (stock)");
  }

  static void same(GameTestHelper helper, List<ItemStack> expected, List<ItemStack> actual, String message) {
    helper.assertTrue(expected.size() == actual.size(), message + " size");
    for (int i = 0; i < expected.size(); i++)
      helper.assertTrue(ItemStack.matches(expected.get(i), actual.get(i)), message + " slot " + i);
  }

  @SafeVarargs
  private static List<ItemStack> concat(List<ItemStack>... sources) {
    var result = new ArrayList<ItemStack>();
    for (var source : sources) result.addAll(source);
    return result;
  }

  static void assertConserved(GameTestHelper helper, List<ItemStack> expected, List<ItemStack> actual,
      String message) {
    for (var stack : concat(expected, actual)) {
      if (stack.isEmpty()) continue;
      int wanted = expected.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack))
          .mapToInt(ItemStack::getCount).sum();
      int found = actual.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack))
          .mapToInt(ItemStack::getCount).sum();
      helper.assertTrue(wanted == found, message + ": " + stack + " expected=" + wanted + " actual=" + found);
    }
  }
}
