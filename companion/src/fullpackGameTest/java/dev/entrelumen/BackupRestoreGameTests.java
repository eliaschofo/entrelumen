package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-only live SimpleBackups prepare and independent restored-server verification. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class BackupRestoreGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String RECEIPT_ID = "entrelumen_live_backup_qa";
  private static final String ARS_ID = "an_redstone_signals";
  private static final String ARCHIVE_PROPERTY = "entrelumen.qa.backup.archive";

  private BackupRestoreGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void prepareLiveBackupFixture(GameTestHelper helper) throws Exception {
    MinecraftServer server = helper.getLevel().getServer();
    Path receiptPath = dataPath(server, RECEIPT_ID);
    helper.assertTrue(!Files.exists(receiptPath),
        "Live backup receipt already exists; use a fresh copy of the QA world: " + receiptPath);
    var fixture = TeamRestartGameTests.readReceipt(helper,
        TeamRestartGameTests.receiptPath(server));
    assertFixtureTeams(helper, fixture);
    CampaignData campaigns = CampaignData.get(server);
    assertFixtureCampaigns(helper, campaigns, fixture);

    UUID nonce = UUID.randomUUID();
    Map<UUID, Map<String, Integer>> counts = new TreeMap<>();
    String arkMutation;
    try (var ownerA = TeamRestartGameTests.connect(helper, fixture.ownerA(), false);
        var ownerB = TeamRestartGameTests.connect(helper, fixture.ownerB(), false);
        var guest = TeamRestartGameTests.connect(helper, fixture.guestA(), false)) {
      assertRoutes(helper, fixture, ownerA.player, ownerB.player, guest.player);
      arkMutation = depositOnePartialArkItem(helper, campaigns, fixture.teamA(), ownerA.player);
      counts.put(ownerA.player.getUUID(), TeamRestartGameTests.inventory(ownerA.player));
      counts.put(ownerB.player.getUUID(), TeamRestartGameTests.inventory(ownerB.player));
      counts.put(guest.player.getUUID(), TeamRestartGameTests.inventory(guest.player));
    }
    for (UUID id : counts.keySet())
      helper.assertTrue(server.getPlayerList().getPlayer(id) == null,
          "QA player did not complete native logout: " + id);

    CompoundTag players = new CompoundTag();
    for (var entry : counts.entrySet())
      players.put(entry.getKey().toString(), playerSnapshot(helper,
          TeamRestartGameTests.playerDataPath(server, entry.getKey()), entry.getValue()));

    ArsSignals ars = new ArsSignals(server.overworld());
    BlockPos probe = probe(nonce);
    helper.assertTrue(!server.overworld().hasChunkAt(probe)
            && !ars.entries().containsKey(probe)
            && !signalsOnDisk(server).contains(signalKey(probe)),
        "Ars QA probe must be unloaded and absent from prior memory/disk state");
    ars.add(probe, 11, 1_000_000);
    CompoundTag signals = ars.snapshot();
    helper.assertTrue(signals.contains(signalKey(probe))
            && !signalsOnDisk(server).contains(signalKey(probe)),
        "Ars signal was already on disk before native live backup flush");

    CompoundTag receipt = new CompoundTag();
    receipt.putInt("schema", 1);
    receipt.putString("nonce", nonce.toString());
    receipt.putLong("preparedStart", TeamRestartGameTests.processStart());
    receipt.putLong("preparedPid", ProcessHandle.current().pid());
    receipt.putString("sourceWorld", worldPath(server).toString());
    receipt.putString("fixtureNonce", fixture.nonce().toString());
    receipt.putString("teamA", fixture.teamA().toString());
    receipt.putString("teamB", fixture.teamB().toString());
    receipt.putString("arkMutation", arkMutation);
    receipt.put("players", players);
    receipt.put("campaigns", campaigns.save(new CompoundTag(), server.overworld().registryAccess()));
    receipt.put("arsSignals", signals);
    receipt.putString("semanticDigest", semanticDigest(receipt));

    BackupReceiptData data = BackupReceiptData.get(server);
    helper.assertTrue(data.receipt.isEmpty(), "Live backup receipt already loaded in SavedData");
    data.receipt = receipt;
    data.setDirty();
    helper.assertTrue(data.isDirty() && ars.isDirty()
            && (arkMutation.equals("skipped") || campaigns.isDirty()),
        "Expected SavedData is not dirty immediately before live backup");
    LOGGER.info("ENTRELUMEN_LIVE_BACKUP_PREPARE nonce={} semanticDigest={} preparedStart={} "
            + "preparedPid={} arkMutation={} probe={} sourceWorld={} receipt={} "
            + "dirtyReceipt={} dirtyCampaign={} dirtyArs={}",
        nonce, receipt.getString("semanticDigest"), receipt.getLong("preparedStart"),
        receipt.getLong("preparedPid"), arkMutation, probe, worldPath(server), receiptPath,
        data.isDirty(), campaigns.isDirty(), ars.isDirty());
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void verifyRestoredLiveBackup(GameTestHelper helper) throws Exception {
    MinecraftServer server = helper.getLevel().getServer();
    String archiveSetting = System.getProperty(ARCHIVE_PROPERTY, "");
    helper.assertTrue(!archiveSetting.isBlank(),
        "Restored verifier needs -D" + ARCHIVE_PROPERTY + "=<native SimpleBackups ZIP>");
    Path archive = Path.of(archiveSetting).toRealPath();
    Path receiptPath = dataPath(server, RECEIPT_ID);
    helper.assertTrue(Files.isRegularFile(receiptPath),
        "Restored world lacks the live backup SavedData receipt");
    CompoundTag receipt = readReceipt(helper, receiptPath);
    helper.assertTrue(TeamRestartGameTests.processStart() != receipt.getLong("preparedStart")
            && !worldPath(server).toString().equalsIgnoreCase(receipt.getString("sourceWorld")),
        "Verifier must run in a new JVM on a different restored world path");
    CompoundTag archived = archiveReceipt(helper, archive);
    helper.assertTrue(receipt.equals(archived),
        "Restored receipt differs from the receipt inside the native SimpleBackups ZIP");

    var fixture = TeamRestartGameTests.readReceipt(helper,
        TeamRestartGameTests.receiptPath(server));
    helper.assertTrue(fixture.nonce().toString().equals(receipt.getString("fixtureNonce"))
            && fixture.teamA().toString().equals(receipt.getString("teamA"))
            && fixture.teamB().toString().equals(receipt.getString("teamB")),
        "Restored restart fixture identity differs from live backup receipt");
    assertFixtureTeams(helper, fixture);
    assertFixtureCampaigns(helper, CampaignData.get(server), fixture);
    CompoundTag expectedCampaigns = receipt.getCompound("campaigns");
    assertCampaigns(helper, CampaignData.get(server), expectedCampaigns,
        server.overworld().registryAccess(), "loaded");
    Path campaignPath = dataPath(server, "entrelumen_campaigns");
    helper.assertTrue(Files.isRegularFile(campaignPath), "Restored campaign data file is absent");
    CompoundTag diskCampaigns = NbtIo.readCompressed(campaignPath, NbtAccounter.unlimitedHeap());
    assertCampaigns(helper, CampaignData.load(diskCampaigns.getCompound("data"),
        server.overworld().registryAccess()), expectedCampaigns,
        server.overworld().registryAccess(), "disk");

    CompoundTag expectedSignals = receipt.getCompound("arsSignals");
    BlockPos probe = probe(UUID.fromString(receipt.getString("nonce")));
    helper.assertTrue(expectedSignals.contains(signalKey(probe)),
        "Live receipt lacks its new Ars signal");
    helper.assertTrue(signalsOnDisk(server).equals(expectedSignals),
        "Restored Ars data file differs from the prepared signal map");
    helper.assertTrue(new ArsSignals(server.overworld()).snapshot().equals(expectedSignals),
        "Loaded Ars RedstoneSavedData differs from the restored file");

    CompoundTag players = receipt.getCompound("players");
    for (UUID id : List.of(fixture.ownerA().getId(), fixture.ownerB().getId(),
        fixture.guestA().getId())) {
      helper.assertTrue(server.getPlayerList().getPlayer(id) == null,
          "QA player was already connected before native restore check: " + id);
      CompoundTag expected = players.getCompound(id.toString());
      CompoundTag actual = playerSnapshot(helper, TeamRestartGameTests.playerDataPath(server, id),
          readCounts(expected.getCompound("counts")));
      helper.assertTrue(actual.equals(expected), "Restored native player inventory differs: " + id);
    }
    try (var ownerA = TeamRestartGameTests.connect(helper, fixture.ownerA(), false);
        var ownerB = TeamRestartGameTests.connect(helper, fixture.ownerB(), false);
        var guest = TeamRestartGameTests.connect(helper, fixture.guestA(), false)) {
      assertRoutes(helper, fixture, ownerA.player, ownerB.player, guest.player);
      for (ServerPlayer player : List.of(ownerA.player, ownerB.player, guest.player)) {
        Map<String, Integer> expected = readCounts(players.getCompound(player.getUUID().toString())
            .getCompound("counts"));
        helper.assertTrue(TeamRestartGameTests.inventory(player).equals(expected),
            "Reconnected native inventory differs for " + player.getUUID());
      }
    }
    assertFixtureTeams(helper, fixture);
    LOGGER.info("ENTRELUMEN_LIVE_BACKUP_VERIFY PASS nonce={} semanticDigest={} "
            + "preparedStart={} verifiedStart={} preparedPid={} verifiedPid={} "
            + "archive={} teams=2 players=3 arsProbe={} arkMutation={}",
        receipt.getString("nonce"), receipt.getString("semanticDigest"),
        receipt.getLong("preparedStart"), TeamRestartGameTests.processStart(),
        receipt.getLong("preparedPid"), ProcessHandle.current().pid(), archive,
        probe, receipt.getString("arkMutation"));
    helper.succeed();
  }

  private static String depositOnePartialArkItem(GameTestHelper helper, CampaignData data,
      UUID teamId, ServerPlayer owner) {
    Campaigns.Campaign campaign = data.campaigns.parties.get(teamId);
    if (!ArkCommissioning.eligible(campaign)) {
      data.setDirty();
      return "skipped";
    }
    int phase = campaign.arkPhase;
    var step = ArkCommissioning.STEPS.get(phase);
    Map<String, Integer> before = TeamRestartGameTests.inventory(owner);
    boolean inventoryHasStepMaterial = step.requirements().keySet().stream()
        .anyMatch(id -> before.getOrDefault(id, 0) > 0);
    var candidate = step.requirements().entrySet().stream()
        .filter(entry -> entry.getValue() - campaign.arkDeposits.getOrDefault(entry.getKey(), 0) >= 2)
        .sorted(Map.Entry.comparingByKey()).findFirst();
    if (inventoryHasStepMaterial || candidate.isEmpty()) {
      data.setDirty();
      return "skipped";
    }
    String id = candidate.orElseThrow().getKey();
    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    helper.assertTrue(item != Items.AIR, "Ark step material is absent: " + id);
    int depositedBefore = campaign.arkDeposits.getOrDefault(id, 0);
    var controller = TeamRestartGameTests.ark(helper);
    TeamRestartGameTests.nearController(owner, controller);
    helper.assertTrue(owner.getInventory().add(new ItemStack(item)),
        "Could not stage one real Ark partial deposit item");
    helper.assertTrue(ArkActions.deposit(owner, teamId, controller, phase)
            && campaign.arkPhase == phase
            && campaign.arkDeposits.getOrDefault(id, 0) == depositedBefore + 1
            && TeamRestartGameTests.inventory(owner).equals(before),
        "Real Ark deposit did not persist one partial item and preserve player inventory");
    return "phase=" + phase + ",item=" + id + ",count=" + (depositedBefore + 1);
  }

  private static void assertFixtureTeams(GameTestHelper helper,
      TeamRestartGameTests.Receipt fixture) {
    var manager = FTBTeamsAPI.api().getManager();
    TeamRestartGameTests.assertMembers(helper,
        (PartyTeam) manager.getTeamByID(fixture.teamA()).orElseThrow(),
        fixture.ownerA().getId(), fixture.guestA().getId());
    TeamRestartGameTests.assertMembers(helper,
        (PartyTeam) manager.getTeamByID(fixture.teamB()).orElseThrow(),
        fixture.ownerB().getId());
  }

  private static void assertFixtureCampaigns(GameTestHelper helper, CampaignData data,
      TeamRestartGameTests.Receipt fixture) {
    helper.assertTrue(data.campaigns.personal.keySet().containsAll(List.of(
            fixture.ownerA().getId(), fixture.ownerB().getId(), fixture.guestA().getId()))
            && data.campaigns.parties.keySet().containsAll(List.of(fixture.teamA(), fixture.teamB())),
        "Existing two-party/three-player campaign fixture is incomplete");
  }

  private static void assertRoutes(GameTestHelper helper, TeamRestartGameTests.Receipt fixture,
      ServerPlayer ownerA, ServerPlayer ownerB, ServerPlayer guest) {
    helper.assertTrue(CampaignActions.campaignId(ownerA).equals(fixture.teamA())
            && CampaignActions.campaignId(ownerB).equals(fixture.teamB())
            && CampaignActions.campaignId(guest).equals(fixture.teamA()),
        "Native player connection lost FTB team route");
  }

  private static CompoundTag playerSnapshot(GameTestHelper helper, Path path,
      Map<String, Integer> counts) throws IOException {
    helper.assertTrue(Files.isRegularFile(path), "Native player .dat missing: " + path);
    CompoundTag player = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    helper.assertTrue(player.contains("Inventory", Tag.TAG_LIST),
        "Native player .dat has no Inventory list: " + path);
    CompoundTag snapshot = new CompoundTag();
    snapshot.put("inventory", player.getList("Inventory", Tag.TAG_COMPOUND).copy());
    snapshot.put("enderItems", player.getList("EnderItems", Tag.TAG_COMPOUND).copy());
    snapshot.putInt("selectedSlot", player.getInt("SelectedItemSlot"));
    CompoundTag countTag = new CompoundTag();
    counts.forEach(countTag::putInt);
    snapshot.put("counts", countTag);
    return snapshot;
  }

  private static Map<String, Integer> readCounts(CompoundTag tag) {
    Map<String, Integer> counts = new TreeMap<>();
    for (String id : tag.getAllKeys()) counts.put(id, tag.getInt(id));
    return counts;
  }

  private static void assertCampaigns(GameTestHelper helper, CampaignData data,
      CompoundTag expected, HolderLookup.Provider lookup, String stage) {
    helper.assertTrue(data.save(new CompoundTag(), lookup).equals(expected),
        stage + " campaign SavedData differs from live backup receipt");
  }

  private static BlockPos probe(UUID nonce) {
    return new BlockPos(24_000_000 + Math.floorMod(nonce.getLeastSignificantBits(), 1_000_000),
        64, 24_000_000 + Math.floorMod(nonce.getMostSignificantBits(), 1_000_000));
  }

  private static String signalKey(BlockPos pos) {
    return pos.getX() + "," + pos.getY() + "," + pos.getZ();
  }

  private static CompoundTag signalsOnDisk(MinecraftServer server) throws IOException {
    Path path = dataPath(server, ARS_ID);
    if (!Files.exists(path)) return new CompoundTag();
    CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    ListTag list = root.getCompound("data").getList("SignalList", Tag.TAG_COMPOUND);
    CompoundTag signals = new CompoundTag();
    for (Tag value : list) {
      CompoundTag signal = (CompoundTag) value;
      String key = signal.getInt("x") + "," + signal.getInt("y") + "," + signal.getInt("z");
      if (signals.contains(key)) throw new IllegalStateException("Duplicate Ars signal " + key);
      signals.put(key, signal.copy());
    }
    return signals;
  }

  /** The pinned Ars 5.13.1 API is reflected because Ars is not a QA compile dependency. */
  private static final class ArsSignals {
    private final Object data;
    private final Map<BlockPos, Object> entries;
    private final Constructor<?> entryConstructor;
    private final Method entrySave;

    @SuppressWarnings("unchecked")
    private ArsSignals(ServerLevel level) throws ReflectiveOperationException {
      Class<?> type = Class.forName(
          "com.hollingsworth.arsnouveau.common.world.saved_data.RedstoneSavedData");
      Class<?> entry = Class.forName(
          "com.hollingsworth.arsnouveau.common.world.saved_data.RedstoneSavedData$Entry");
      data = type.getMethod("from", ServerLevel.class).invoke(null, level);
      entries = (Map<BlockPos, Object>) type.getField("SIGNAL_MAP").get(data);
      entryConstructor = entry.getConstructor(BlockPos.class, int.class, int.class);
      entrySave = entry.getMethod("save", CompoundTag.class);
    }

    private Map<BlockPos, Object> entries() {
      return entries;
    }

    private void add(BlockPos pos, int power, int ticks) throws ReflectiveOperationException {
      entries.put(pos, entryConstructor.newInstance(pos, power, ticks));
      ((SavedData) data).setDirty();
    }

    private boolean isDirty() {
      return ((SavedData) data).isDirty();
    }

    private CompoundTag snapshot() throws ReflectiveOperationException {
      CompoundTag snapshot = new CompoundTag();
      for (var entry : entries.entrySet()) {
        CompoundTag signal = (CompoundTag) entrySave.invoke(entry.getValue(), new CompoundTag());
        String key = signalKey(entry.getKey());
        if (snapshot.contains(key)) throw new IllegalStateException("Duplicate Ars signal " + key);
        snapshot.put(key, signal);
      }
      return snapshot;
    }
  }

  private static Path worldPath(MinecraftServer server) throws IOException {
    return server.getWorldPath(LevelResource.ROOT).toRealPath();
  }

  private static Path dataPath(MinecraftServer server, String id) {
    return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(id + ".dat");
  }

  private static CompoundTag readReceipt(GameTestHelper helper, Path path) throws IOException {
    CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    CompoundTag receipt = root.getCompound("data").getCompound("receipt");
    helper.assertTrue(receipt.getInt("schema") == 1
            && !receipt.getString("nonce").isBlank()
            && receipt.getLong("preparedStart") > 0
            && receipt.getLong("preparedPid") > 0
            && receipt.getCompound("players").size() == 3
            && semanticDigest(receipt).equals(receipt.getString("semanticDigest")),
        "Live backup SavedData receipt schema or semantic digest is invalid");
    return receipt;
  }

  private static CompoundTag archiveReceipt(GameTestHelper helper, Path archive)
      throws IOException {
    helper.assertTrue(Files.isRegularFile(archive), "SimpleBackups ZIP missing: " + archive);
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      List<ZipEntry> matches = new ArrayList<>();
      zip.stream().filter(entry -> !entry.isDirectory() && entry.getName().endsWith(
          "/data/" + RECEIPT_ID + ".dat")).forEach(matches::add);
      helper.assertTrue(matches.size() == 1,
          "SimpleBackups ZIP needs exactly one live receipt entry; found " + matches.size());
      try (InputStream input = zip.getInputStream(matches.getFirst())) {
        CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        return root.getCompound("data").getCompound("receipt");
      }
    }
  }

  private static String semanticDigest(CompoundTag receipt) {
    CompoundTag copy = receipt.copy();
    copy.remove("semanticDigest");
    StringBuilder canonical = new StringBuilder();
    canonical(copy, canonical);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("JVM has no SHA-256", ex);
    }
  }

  private static void canonical(Tag tag, StringBuilder out) {
    out.append(tag.getId()).append(':');
    if (tag instanceof CompoundTag compound) {
      var keys = new ArrayList<>(compound.getAllKeys());
      keys.sort(String::compareTo);
      out.append(keys.size()).append('{');
      for (String key : keys) {
        out.append(key.length()).append(':').append(key);
        canonical(compound.get(key), out);
      }
      out.append('}');
    } else if (tag instanceof ListTag list) {
      out.append(list.size()).append('[');
      for (Tag value : list) canonical(value, out);
      out.append(']');
    } else {
      String value = tag.getAsString();
      out.append(value.length()).append(':').append(value);
    }
  }

  private static final class BackupReceiptData extends SavedData {
    private CompoundTag receipt = new CompoundTag();

    private static BackupReceiptData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(
          new Factory<>(BackupReceiptData::new, BackupReceiptData::load), RECEIPT_ID);
    }

    private static BackupReceiptData load(CompoundTag tag, HolderLookup.Provider lookup) {
      BackupReceiptData data = new BackupReceiptData();
      data.receipt = tag.getCompound("receipt").copy();
      return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
      tag.put("receipt", receipt.copy());
      return tag;
    }
  }
}
