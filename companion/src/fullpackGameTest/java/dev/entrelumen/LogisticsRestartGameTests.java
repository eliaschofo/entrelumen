package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Two QA-only JVM stages proving that the physical depot and personal kits survive a real save. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class LogisticsRestartGameTests {
  private static final String RECEIPT = "entrelumen_logistics_restart_qa_receipt.dat";
  private static final String PROFILE = "entrelumen:logistics_kit";
  private static final BlockPos CONTROLLER = new BlockPos(2008, 180, 2008);
  private static final BlockPos MODULE = CONTROLLER.offset(0, 0, 2);
  private static final List<String> MODULES = List.of("engineering_module", "arcane_module",
      "nature_module", "exploration_module", "logistics_module", "habitation_module");

  private LogisticsRestartGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void prepareLogisticsRestartFixture(GameTestHelper helper) throws Exception {
    MinecraftServer server = helper.getLevel().getServer();
    helper.assertTrue(helper.getLevel() == server.overworld(),
        "Logistics restart fixture must use the overworld QA world");
    Path path = receiptPath(server);
    guardFresh(helper, path);
    guardEmptyArea(helper);
    placeArk(helper);

    Container depot = LogisticsProvisioningGameTests.stock(helper, MODULE);
    helper.assertTrue(depot.isEmpty(), "New fixed Ark depot contains unexpected stock");
    List<ItemStack> initialStock = List.of(new ItemStack(Items.ARROW, 9),
        LogisticsProvisioningGameTests.rocket(1, 4), LogisticsProvisioningGameTests.healing(),
        new ItemStack(LogisticsProvisioningGameTests.item("farmersdelight:beef_stew")),
        new ItemStack(LogisticsProvisioningGameTests.item("farmersdelight:beef_stew")),
        new ItemStack(LogisticsProvisioningGameTests.item("farmersdelight:beef_stew")),
        LogisticsProvisioningGameTests.rocket(3, 3), LogisticsProvisioningGameTests.swift());
    for (int slot = 0; slot < initialStock.size(); slot++)
      depot.setItem(slot, initialStock.get(slot).copy());
    CompoundTag stockExpected = ((LogisticsStock) depot).saveCustomOnly(server.registryAccess());

    GameProfile first = profile("KitRestartA");
    GameProfile second = profile("KitRestartB");
    CompoundTag firstKit;
    CompoundTag secondKit;
    ListTag firstInventory;
    ListTag secondInventory;
    List<ItemStack> expectedA = List.of(new ItemStack(Items.ARROW, 4),
        LogisticsProvisioningGameTests.rocket(1, 2),
        LogisticsProvisioningGameTests.healing());
    List<ItemStack> expectedB = List.of(
        new ItemStack(LogisticsProvisioningGameTests.item("farmersdelight:beef_stew")),
        new ItemStack(LogisticsProvisioningGameTests.item("farmersdelight:beef_stew")),
        LogisticsProvisioningGameTests.rocket(3, 2),
        LogisticsProvisioningGameTests.swift());
    try (var firstSession = TeamRestartGameTests.connect(helper, first, true);
        var secondSession = TeamRestartGameTests.connect(helper, second, true)) {
      ServerPlayer a = firstSession.player;
      ServerPlayer b = secondSession.player;
      LogisticsProvisioningGameTests.near(a, MODULE);
      LogisticsProvisioningGameTests.near(b, MODULE);
      LogisticsProvisioningGameTests.recordNative(helper, a, MODULE, expectedA);
      LogisticsProvisioningGameTests.recordNative(helper, b, MODULE, expectedB);
      firstKit = savedKit(a);
      secondKit = savedKit(b);
      helper.assertTrue(!firstKit.isEmpty() && !secondKit.isEmpty()
              && !firstKit.equals(secondKit),
          "Two distinct kit profiles were not recorded before normal logout");
      LogisticsProvisioningGameTests.assertConserved(helper, expectedA,
          kitStacks(firstKit, server.registryAccess()), "First recorded restart kit");
      LogisticsProvisioningGameTests.assertConserved(helper, expectedB,
          kitStacks(secondKit, server.registryAccess()), "Second recorded restart kit");
      LogisticsProvisioningGameTests.reset(a);
      LogisticsProvisioningGameTests.reset(b);
      firstInventory = a.getInventory().save(new ListTag());
      secondInventory = b.getInventory().save(new ListTag());
      helper.assertTrue(!firstInventory.isEmpty() && !secondInventory.isEmpty(),
          "Fixture must persist the reusable Routing Matrix in both player inventories");
      helper.assertTrue(stockExpected.equals(((LogisticsStock) depot).saveCustomOnly(
              server.registryAccess())), "Profile recording changed depot stock");
    }

    for (var pair : List.of(first, second)) {
      helper.assertTrue(server.getPlayerList().getPlayer(pair.getId()) == null,
          "Native QA player remained connected after logout: " + pair.getId());
      helper.assertTrue(Files.isRegularFile(TeamRestartGameTests.playerDataPath(server, pair.getId())),
          "Native logout did not write playerdata/" + pair.getId() + ".dat");
    }
    assertPlayerDisk(helper, server, first, firstKit, firstInventory);
    assertPlayerDisk(helper, server, second, secondKit, secondInventory);
    helper.assertTrue(server.saveEverything(true, true, false),
        "Server did not flush the physical depot chunk");
    helper.assertTrue(Files.isRegularFile(regionPath(server)),
        "Saved overworld region containing the fixed Ark is absent");

    CompoundTag receipt = new CompoundTag();
    receipt.putInt("schema", 1);
    receipt.putString("stage", "prepared");
    receipt.putString("nonce", UUID.randomUUID().toString());
    receipt.putLong("preparedStart", TeamRestartGameTests.processStart());
    receipt.putLong("preparedPid", ProcessHandle.current().pid());
    receipt.putString("world", worldPath(server).toString());
    receipt.putIntArray("controller", coordinates(CONTROLLER));
    receipt.putIntArray("module", coordinates(MODULE));
    receipt.put("stock", stockExpected);
    receipt.put("playerA", playerReceipt(first, firstKit, firstInventory));
    receipt.put("playerB", playerReceipt(second, secondKit, secondInventory));
    writeReceipt(helper, path, receipt);
    LogUtils.getLogger().info("ENTRELUMEN_LOGISTICS_RESTART_PREPARE nonce={} pid={} "
            + "world={} controller={} module={} players=2 stockSlots={} receipt={}",
        receipt.getString("nonce"), receipt.getLong("preparedPid"), worldPath(server),
        CONTROLLER, MODULE, initialStock.size(), path);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void verifyLogisticsRestartFixture(GameTestHelper helper) throws Exception {
    MinecraftServer server = helper.getLevel().getServer();
    Path path = receiptPath(server);
    helper.assertTrue(Files.isRegularFile(path) && !Files.exists(pendingPath(path)),
        "Logistics restart receipt is absent or a pending write remains");
    CompoundTag receipt = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    validateReceipt(helper, receipt, server);
    helper.assertTrue(TeamRestartGameTests.processStart() != receipt.getLong("preparedStart")
            && ProcessHandle.current().pid() != receipt.getLong("preparedPid"),
        "Logistics verifier requires a different JVM start and PID after normal shutdown");
    helper.assertTrue(Files.isRegularFile(regionPath(server)),
        "Persisted overworld region containing the Ark is absent");
    server.overworld().getChunkAt(CONTROLLER);
    assertArk(helper);
    Container depot = LogisticsProvisioningGameTests.stock(helper, MODULE);
    CompoundTag stockExpected = receipt.getCompound("stock");
    helper.assertTrue(stockExpected.equals(((LogisticsStock) depot).saveCustomOnly(
            server.registryAccess())),
        "Loaded physical depot differs from the stock saved before shutdown");

    CompoundTag aReceipt = receipt.getCompound("playerA");
    CompoundTag bReceipt = receipt.getCompound("playerB");
    GameProfile aProfile = readProfile(aReceipt);
    GameProfile bProfile = readProfile(bReceipt);
    for (var player : List.of(aProfile, bProfile))
      helper.assertTrue(server.getPlayerList().getPlayer(player.getId()) == null,
          "QA player was online before persisted reconnect: " + player.getId());
    assertPlayerDisk(helper, server, aProfile, aReceipt.getCompound("kit"),
        aReceipt.getList("inventory", Tag.TAG_COMPOUND));
    assertPlayerDisk(helper, server, bProfile, bReceipt.getCompound("kit"),
        bReceipt.getList("inventory", Tag.TAG_COMPOUND));

    try (var firstSession = TeamRestartGameTests.connect(helper, aProfile, false);
        var secondSession = TeamRestartGameTests.connect(helper, bProfile, false)) {
      ServerPlayer a = firstSession.player;
      ServerPlayer b = secondSession.player;
      assertPlayerLoaded(helper, a, aReceipt);
      assertPlayerLoaded(helper, b, bReceipt);
      LogisticsProvisioningGameTests.near(a, MODULE);
      LogisticsProvisioningGameTests.near(b, MODULE);
      List<ItemStack> before = combined(depot, a, b);
      CompoundTag campaignBefore = CampaignData.get(server).save(new CompoundTag(),
          server.registryAccess());
      LogisticsProvisioningGameTests.use(a, MODULE, false);
      LogisticsProvisioningGameTests.assertKit(helper, a,
          kitStacks(aReceipt.getCompound("kit"), server.registryAccess()));
      LogisticsProvisioningGameTests.use(b, MODULE, false);
      LogisticsProvisioningGameTests.assertKit(helper, b,
          kitStacks(bReceipt.getCompound("kit"), server.registryAccess()));
      LogisticsProvisioningGameTests.assertConserved(helper, before, combined(depot, a, b),
          "Restarted depot and two personal kits");
      List<ItemStack> completeStock = LogisticsProvisioningGameTests.snapshot(depot);
      List<ItemStack> completeA = LogisticsProvisioningGameTests.snapshot(a.getInventory());
      List<ItemStack> completeB = LogisticsProvisioningGameTests.snapshot(b.getInventory());
      LogisticsProvisioningGameTests.use(a, MODULE, false);
      LogisticsProvisioningGameTests.use(b, MODULE, false);
      LogisticsProvisioningGameTests.same(helper, completeStock,
          LogisticsProvisioningGameTests.snapshot(depot), "Replay consumed restored depot stock");
      LogisticsProvisioningGameTests.same(helper, completeA,
          LogisticsProvisioningGameTests.snapshot(a.getInventory()), "Replay changed player A");
      LogisticsProvisioningGameTests.same(helper, completeB,
          LogisticsProvisioningGameTests.snapshot(b.getInventory()), "Replay changed player B");
      helper.assertTrue(campaignBefore.equals(CampaignData.get(server).save(new CompoundTag(),
              server.registryAccess())), "Kit preparation changed campaign state");
    }
    LogUtils.getLogger().info("ENTRELUMEN_LOGISTICS_RESTART_VERIFY nonce={} oldPid={} newPid={} "
            + "players=2 exactProfiles=true exactStock=true replay=unchanged",
        receipt.getString("nonce"), receipt.getLong("preparedPid"),
        ProcessHandle.current().pid());
    helper.succeed();
  }

  private static void guardEmptyArea(GameTestHelper helper) {
    var level = helper.getLevel();
    level.getChunkAt(CONTROLLER);
    for (BlockPos pos : BlockPos.betweenClosed(CONTROLLER.offset(-4, -2, -4),
        CONTROLLER.offset(4, 3, 4)))
      helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null,
          "Fixed logistics restart area is occupied; no block will be overwritten: " + pos);
  }

  private static void placeArk(GameTestHelper helper) {
    var level = helper.getLevel();
    level.setBlockAndUpdate(CONTROLLER,
        LogisticsProvisioningGameTests.block("entrelumen:ark_controller").defaultBlockState());
    for (int at = 0; at < MODULES.size(); at++)
      level.setBlockAndUpdate(CONTROLLER.offset(at % 3 - 1, 0, at / 3 + 1),
          LogisticsProvisioningGameTests.block("entrelumen:" + MODULES.get(at))
              .defaultBlockState());
    assertArk(helper);
  }

  private static void assertArk(GameTestHelper helper) {
    var level = helper.getLevel();
    helper.assertTrue(level.getBlockState(CONTROLLER)
            .is(LogisticsProvisioningGameTests.block("entrelumen:ark_controller"))
            && level.getBlockState(MODULE)
                .is(LogisticsProvisioningGameTests.block("entrelumen:logistics_module")),
        "Fixed Ark controller/logistics module is absent");
    for (int at = 0; at < MODULES.size(); at++)
      helper.assertTrue(level.getBlockState(CONTROLLER.offset(at % 3 - 1, 0, at / 3 + 1))
              .is(LogisticsProvisioningGameTests.block("entrelumen:" + MODULES.get(at))),
          "Fixed Ark lost module " + MODULES.get(at));
    var physical = EngineeringDiagnostics.physicalView(level, MODULE);
    helper.assertTrue(CONTROLLER.equals(LogisticsModuleActions.controllerForDeposit(physical)),
        "Fixed Ark is incomplete, unloaded or ambiguous");
  }

  private static GameProfile profile(String prefix) {
    UUID id = UUID.randomUUID();
    return new GameProfile(id, prefix + id.toString().substring(0, 5));
  }

  private static CompoundTag savedKit(ServerPlayer player) {
    return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
        .getCompound(PROFILE).copy();
  }

  private static CompoundTag playerReceipt(GameProfile player, CompoundTag kit, ListTag inventory) {
    CompoundTag tag = new CompoundTag();
    tag.putString("id", player.getId().toString());
    tag.putString("name", player.getName());
    tag.put("kit", kit.copy());
    tag.put("inventory", inventory.copy());
    return tag;
  }

  private static GameProfile readProfile(CompoundTag tag) {
    return new GameProfile(UUID.fromString(tag.getString("id")), tag.getString("name"));
  }

  private static void assertPlayerDisk(GameTestHelper helper, MinecraftServer server,
      GameProfile profile, CompoundTag kit, ListTag inventory) throws IOException {
    Path path = TeamRestartGameTests.playerDataPath(server, profile.getId());
    helper.assertTrue(Files.isRegularFile(path), "Persisted player .dat is absent: " + profile.getId());
    CompoundTag disk = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    helper.assertTrue(disk.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG)
            .getCompound(PROFILE).equals(kit)
            && disk.getList("Inventory", Tag.TAG_COMPOUND).equals(inventory),
        "Player .dat kit/inventory differs from receipt: " + profile.getId());
  }

  private static void assertPlayerLoaded(GameTestHelper helper, ServerPlayer player,
      CompoundTag receipt) {
    helper.assertTrue(savedKit(player).equals(receipt.getCompound("kit"))
            && player.getInventory().save(new ListTag()).equals(
                receipt.getList("inventory", Tag.TAG_COMPOUND)),
        "Reconnected player lost kit or native inventory: " + player.getUUID());
  }

  private static List<ItemStack> kitStacks(CompoundTag profile, HolderLookup.Provider registries) {
    List<ItemStack> result = new ArrayList<>();
    ListTag entries = profile.getList("entries", Tag.TAG_COMPOUND);
    for (int at = 0; at < entries.size(); at++) {
      CompoundTag entry = entries.getCompound(at);
      ItemStack sample = ItemStack.parse(registries, entry.getCompound("stack")).orElseThrow();
      result.add(sample.copyWithCount(entry.getInt("count")));
    }
    return result;
  }

  private static List<ItemStack> combined(Container stock, ServerPlayer a, ServerPlayer b) {
    List<ItemStack> result = new ArrayList<>(LogisticsProvisioningGameTests.snapshot(stock));
    result.addAll(LogisticsProvisioningGameTests.snapshot(a.getInventory()));
    result.addAll(LogisticsProvisioningGameTests.snapshot(b.getInventory()));
    return result;
  }

  private static void validateReceipt(GameTestHelper helper, CompoundTag receipt,
      MinecraftServer server) {
    CompoundTag a = receipt.getCompound("playerA");
    CompoundTag b = receipt.getCompound("playerB");
    helper.assertTrue(receipt.getInt("schema") == 1
            && receipt.getString("stage").equals("prepared")
            && !receipt.getString("nonce").isBlank()
            && receipt.getLong("preparedStart") > 0 && receipt.getLong("preparedPid") > 0
            && receipt.getString("world").equals(worldPath(server).toString())
            && java.util.Arrays.equals(receipt.getIntArray("controller"), coordinates(CONTROLLER))
            && java.util.Arrays.equals(receipt.getIntArray("module"), coordinates(MODULE))
            && receipt.getCompound("stock").getList("Items", Tag.TAG_COMPOUND).size() == 8
            && !a.getCompound("kit").isEmpty() && !b.getCompound("kit").isEmpty()
            && !a.getCompound("kit").equals(b.getCompound("kit"))
            && !a.getString("id").equals(b.getString("id")),
        "Logistics restart receipt schema, identity, coordinates or stock is invalid");
  }

  private static Path worldPath(MinecraftServer server) {
    return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
  }

  private static Path receiptPath(MinecraftServer server) {
    return worldPath(server).resolve("data").resolve(RECEIPT);
  }

  private static Path pendingPath(Path path) {
    return path.resolveSibling(RECEIPT + ".pending");
  }

  private static Path regionPath(MinecraftServer server) {
    int x = Math.floorDiv(CONTROLLER.getX() >> 4, 32);
    int z = Math.floorDiv(CONTROLLER.getZ() >> 4, 32);
    return worldPath(server).resolve("region").resolve("r." + x + "." + z + ".mca");
  }

  private static int[] coordinates(BlockPos pos) {
    return new int[] {pos.getX(), pos.getY(), pos.getZ()};
  }

  private static void guardFresh(GameTestHelper helper, Path path) {
    helper.assertTrue(!Files.exists(path) && !Files.exists(pendingPath(path)),
        "Existing logistics restart receipt/pending file must be reviewed before a new prepare: "
            + path);
  }

  private static void writeReceipt(GameTestHelper helper, Path path, CompoundTag receipt)
      throws IOException {
    guardFresh(helper, path);
    Files.createDirectories(path.getParent());
    Path pending = pendingPath(path);
    NbtIo.writeCompressed(receipt, pending);
    Files.move(pending, path);
  }
}
