package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * QA-JAR-only checks of round 5 of the mod ping-pong (docs/design/mod-pingpong.md, «Ronda 5»), one group per
 * batch. Batch 1 (robustness): the server-side fixes are loaded, the client-only ones stay off the dedicated
 * server, Create Collision Fix only sits beside the Create build it patches, Better Compatibility Checker
 * reports this pack and version, and NaNny cancels damage that is not a number. Batch 2 (information and
 * comfort): the batch is loaded without FindMe, and RightClickHarvest cannot harvest through a foreign
 * FTB Chunks claim. Batch 3 (compat): the batch is loaded, Advanced Peripherals' AE2 disk cells and chunk
 * controller have no recipe, its server settings keep the player detector and chat box local and the chunky
 * turtle off, it gives no book on join, and Carry On refuses the new peripherals and rocket blocks. Batch 4
 * (technology): the batch is loaded, the tesla coil and tower take the act III alloy, the Dyson rail
 * ejector the atomic alloy and EI's quantum nano armor the habitation Luminosity in the loaded recipe
 * manager, and the routes around them are gone.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ModPingpongRound5FullpackGameTests {
  /** Batch 1 mods with a server side (single player runs them on the integrated server too). */
  static final List<String> ROBUSTNESS = List.of("imfast", "packetfixer", "lmft", "nerb", "octolib", "nanny", "bcc",
      "structure_layout_optimizer", "createcollisionfix", "mekpipezfix", "neruina", "configurable", "asynclocator");
  /** Batch 1 client-only mods: the server install leaves them out (catalog clientOnly). */
  static final List<String> ROBUSTNESS_CLIENT = List.of("entityculling", "crash_assistant", "sodium_extra",
      "cmpreviewfixer", "derenderpatcher");
  /** Batch 2 mods with a server side. */
  static final List<String> INFORMATION = List.of("emi_loot", "fzzy_config", "emi_ores", "rightclickharvest", "jamlib",
      "ae2ct", "wits", "chunky", "pingwheel");
  static final List<String> INFORMATION_CLIENT = List.of("bridgingmod", "yet_another_config_lib_v3", "bwncr",
      "yeetusexperimentus", "dynamic_fps");
  /** Batch 3 mods (all have a server side). */
  static final List<String> COMPAT = List.of("apothic_compat", "irons_apothic", "polyeng", "ad_astra_giselle_addon",
      "advancedperipherals");
  /** Removed by the pingpong5compat family: large cells skip MEGA Cells (Act IV); the controller only makes the chunky turtle. */
  static final List<String> COMPAT_REMOVED = List.of("advancedperipherals:ae_disk_cell_1m", "advancedperipherals:ae_disk_cell_4m",
      "advancedperipherals:ae_disk_cell_16m", "advancedperipherals:ae_disk_cell_64m", "advancedperipherals:ae_disk_cell_256m",
      "advancedperipherals:chunk_controller");
  /** Batch 4 mods (all have a server side). */
  static final List<String> TECH = List.of("extended_industrialization", "tesseract_api", "industrialization_overdrive",
      "dysoncubeproject", "morered", "moreredxcctcompat");
  /** pingpong5tech gates: recipe and the act material it must consume. */
  static final Map<String, String> TECH_STAGED = Map.of(
      "extended_industrialization:machines/tesla_coil/craft", "mekanism:alloy_reinforced",
      "extended_industrialization:machines/tesla_tower/craft", "mekanism:alloy_reinforced",
      "dysoncubeproject:em_railejector_controller", "mekanism:alloy_atomic");
  /** EI's quantum nano armor (MI packer): the habitation Luminosity, like MI's quantum armor (Act VI). */
  static final List<String> TECH_TOP_ARMOR = List.of("helmet", "chestplate", "leggings", "boots").stream()
      .map(piece -> "extended_industrialization:tool/nano_suit_" + piece + "_quantum_upgrade").toList();
  /** Routes around those gates, removed by pingpong5tech. */
  static final List<String> TECH_REMOVED = List.of("extended_industrialization:machines/tesla_coil/assembler",
      "extended_industrialization:machines/tesla_tower/assembler",
      "extended_industrialization:machines/tesla_coil/craft/from_tesla_receiver");
  /** Round 5 blocks Carry On must refuse (pack/config/carryon-common.toml). */
  static final List<String> CARRY_ON_REFUSED = new ArrayList<>(List.of("advancedperipherals:me_bridge",
      "advancedperipherals:inventory_manager", "advancedperipherals:player_detector", "ad_astra_giselle_addon:fuel_loader",
      "ad_astra_giselle_addon:automation_nasa_workbench", "extended_industrialization:tesla_coil",
      "extended_industrialization:processing_array", "industrialization_overdrive:multi_processing_array",
      "dysoncubeproject:em_railejector_controller", "morered:soldering_table"));
  /** Create Collision Fix patches exactly this Create build; it goes when Create moves to 6.0.11 (PR #10301). */
  static final String PATCHED_CREATE = "6.0.10";

  private static final Logger LOGGER = LogUtils.getLogger();

  private ModPingpongRound5FullpackGameTests() {}

  static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  static String version(String mod) {
    return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse("absent");
  }

  static String tomlValue(String text, String key) {
    Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"\\s*$").matcher(text);
    return m.find() ? m.group(1) : null;
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound5RobustnessLoaded(GameTestHelper helper) throws IOException {
    List<String> problems = new ArrayList<>();
    ROBUSTNESS.stream().filter(mod -> !ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " not loaded"));
    if (FMLEnvironment.dist.isDedicatedServer())
      ROBUSTNESS_CLIENT.stream().filter(mod -> ModList.get().isLoaded(mod))
          .forEach(mod -> problems.add(mod + " is client-only but loaded on the dedicated server"));
    if (ModList.get().isLoaded("createcollisionfix") && !PATCHED_CREATE.equals(version("create")))
      problems.add("Create Collision Fix patches Create " + PATCHED_CREATE + " but Create is " + version("create")
          + ": remove the hotfix");
    var bcc = FMLPaths.CONFIGDIR.get().resolve("bcc-common.toml");
    String text = Files.isRegularFile(bcc) ? Files.readString(bcc) : "";
    String name = tomlValue(text, "modpackName"), packVersion = tomlValue(text, "modpackVersion");
    if (!"ENTRELUMEN".equals(name)) problems.add("bcc-common.toml modpackName is " + name);
    if (!version("entrelumen").equals(packVersion))
      problems.add("bcc-common.toml modpackVersion " + packVersion + " differs from the companion " + version("entrelumen"));
    LOGGER.info("ENTRELUMEN_ROUND5 robustness create={} bcc={}/{} problems={}", version("create"), name, packVersion, problems);
    helper.assertTrue(problems.isEmpty(), "Round 5 batch 1: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void nannyCancelsNaNDamage(GameTestHelper helper) {
    Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(0, 1, 0));
    float before = pig.getHealth();
    // Vanilla subtracts NaN from the health and leaves the pig unkillable; NaNny cancels the incoming damage.
    pig.hurt(helper.getLevel().damageSources().generic(), Float.NaN);
    float after = pig.getHealth();
    pig.discard();
    helper.assertTrue(Float.isFinite(after) && after == before, "NaN damage changed health " + before + " -> " + after);
    helper.succeed();
  }

  // ---- Batch 2 ---------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound5InformationLoaded(GameTestHelper helper) {
    List<String> problems = new ArrayList<>();
    INFORMATION.stream().filter(mod -> !ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " not loaded"));
    if (FMLEnvironment.dist.isDedicatedServer())
      INFORMATION_CLIENT.stream().filter(mod -> ModList.get().isLoaded(mod))
          .forEach(mod -> problems.add(mod + " is client-only but loaded on the dedicated server"));
    // FindMe 3.3.4 pulls items out of any container in range without asking FTB Chunks; it stays out.
    if (ModList.get().isLoaded("findme")) problems.add("findme is loaded");
    helper.assertTrue(problems.isEmpty(), "Round 5 batch 2: " + problems);
    helper.succeed();
  }

  /**
   * RightClickHarvest 4.6.1 posts a BreakEvent before each harvest (RightClickHarvestPlatformImpl), which a
   * real FTB Chunks claim refuses to another team. High over the test, in one chunk: the owner claims it and
   * harvests their ripe wheat, which replants at age 0; the visitor's right click on the other ripe wheat
   * leaves it untouched. The mock players, the claim and the blocks are given back however the case ends.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void rightClickHarvestRespectsForeignClaims(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
    int y = level.getMaxBuildHeight() - 12;
    BlockPos own = new BlockPos(chunk.getMinBlockX() + 7, y + 1, chunk.getMinBlockZ() + 7);
    BlockPos foreign = own.east();
    List<Runnable> cleanup = new ArrayList<>();
    Runnable close = () -> {
      for (int i = cleanup.size() - 1; i >= 0; i--) cleanup.get(i).run();
      cleanup.clear();
    };
    try {
      for (BlockPos crop : List.of(own, foreign)) {
        level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
        level.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 2);
        cleanup.add(() -> {
          level.setBlock(crop, Blocks.AIR.defaultBlockState(), 2);
          level.setBlock(crop.below(), Blocks.AIR.defaultBlockState(), 2);
        });
      }
      // Player names are at most 16 characters: FTB Teams syncs them with the vanilla name codec.
      Session owner = new Session(helper, "R5HarvestOwner");
      cleanup.add(owner::close);
      Session visitor = new Session(helper, "R5HarvestVisitor");
      cleanup.add(visitor::close);
      owner.player.teleportTo(own.getX() + 0.5, own.getY() + 1.0, own.getZ() - 1.5);
      int claimed = owner.player.server.getCommands().getDispatcher().execute("ftbchunks claim",
          owner.player.createCommandSourceStack().withSuppressedOutput());
      helper.assertTrue(claimed > 0, "FTB Chunks did not claim the owner's chunk");
      cleanup.add(() -> unclaim(owner.player, own));
      visitor.player.teleportTo(foreign.getX() + 0.5, foreign.getY() + 1.0, foreign.getZ() - 1.5);
      rightClick(visitor.player, foreign);
      rightClick(owner.player, own);
      int ownAge = age(level, own), foreignAge = age(level, foreign);
      LOGGER.info("ENTRELUMEN_ROUND5 harvest ownAge={} foreignAge={} ownerWheat={}", ownAge, foreignAge,
          owner.player.getInventory().countItem(Items.WHEAT));
      helper.assertTrue(ownAge == 0, "The owner's right click did not harvest and replant their wheat (age " + ownAge + ")");
      helper.assertTrue(foreignAge == 7, "The visitor harvested wheat inside a foreign claim (age " + foreignAge + ")");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
      close.run();
      throw new IllegalStateException("FTB Chunks claim failed", failure);
    } catch (RuntimeException | Error failure) {
      close.run();
      throw failure;
    }
    close.run();
    helper.succeed();
  }

  private static int age(ServerLevel level, BlockPos pos) {
    var state = level.getBlockState(pos);
    return state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : -1;
  }

  private static void rightClick(ServerPlayer player, BlockPos pos) {
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
  }

  private static void unclaim(ServerPlayer owner, BlockPos pos) {
    try {
      owner.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() - 1.5);
      owner.server.getCommands().getDispatcher().execute("ftbchunks unclaim",
          owner.createCommandSourceStack().withSuppressedOutput());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
      // A claim left over the empty sky is harmless, and the next run's claim reports it.
    }
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final EmbeddedChannel channel;
    private boolean closed;

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
      if (closed) return;
      closed = true;
      try {
        connection.disconnect(Component.literal("Entrelumen round 5 QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  // ---- Batch 3 ---------------------------------------------------------------------------------

  static Object configValue(String holder, String field) throws ReflectiveOperationException {
    Object config = Class.forName("de.srendi.advancedperipherals.common.configuration.APConfig").getField(holder).get(null);
    Object value = config.getClass().getField(field).get(config);
    return value.getClass().getMethod("get").invoke(value);
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound5CompatLoaded(GameTestHelper helper) throws ReflectiveOperationException {
    List<String> problems = new ArrayList<>();
    COMPAT.stream().filter(mod -> !ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " not loaded"));
    var recipes = helper.getLevel().getRecipeManager();
    COMPAT_REMOVED.stream().filter(recipe -> recipes.byKey(id(recipe)).isPresent())
        .forEach(recipe -> problems.add(recipe + " still has a recipe"));
    // pack/defaultconfigs/Advancedperipherals/peripherals.toml and pack/config/Advancedperipherals/world.toml
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("PERIPHERALS_CONFIG.playerDetMaxRange", 128);
    expected.put("PERIPHERALS_CONFIG.playerSpyStatistics", false);
    expected.put("PERIPHERALS_CONFIG.chatBoxMaxRange", 256);
    expected.put("PERIPHERALS_CONFIG.chatBoxPreventRunCommand", true);
    expected.put("PERIPHERALS_CONFIG.enableChunkyTurtle", false);
    expected.put("WORLD_CONFIG.givePlayerBookOnJoin", false);
    for (var entry : expected.entrySet()) {
      String[] path = entry.getKey().split("\\.");
      Object actual = configValue(path[0], path[1]);
      if (!entry.getValue().equals(actual)) problems.add(entry.getKey() + " is " + actual);
    }
    helper.assertTrue(problems.isEmpty(), "Round 5 batch 3: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void carryOnRefusesRound5Blocks(GameTestHelper helper) throws ReflectiveOperationException {
    var permitted = Class.forName("tschipp.carryon.common.config.ListHandler").getMethod("isPermitted", Block.class);
    List<String> carried = new ArrayList<>(), absent = new ArrayList<>();
    for (String name : CARRY_ON_REFUSED) {
      var block = BuiltInRegistries.BLOCK.getOptional(id(name));
      if (block.isEmpty()) absent.add(name);
      else if ((boolean) permitted.invoke(null, block.get())) carried.add(name);
    }
    helper.assertTrue(absent.isEmpty(), "Round 5 blacklist test blocks not registered: " + absent);
    helper.assertTrue(carried.isEmpty(), "Carry On may pick up: " + carried);
    helper.succeed();
  }

  // ---- Batch 4 ---------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound5TechLoaded(GameTestHelper helper) {
    List<String> problems = new ArrayList<>();
    TECH.stream().filter(mod -> !ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " not loaded"));
    var recipes = helper.getLevel().getRecipeManager();
    TECH_STAGED.forEach((recipe, material) -> {
      var holder = recipes.byKey(id(recipe));
      var item = BuiltInRegistries.ITEM.getOptional(id(material));
      if (holder.isEmpty() || item.isEmpty()) {
        problems.add(recipe + (holder.isEmpty() ? " is not loaded" : " names an unregistered " + material));
        return;
      }
      ItemStack stack = new ItemStack(item.get());
      if (holder.get().value().getIngredients().stream().noneMatch(ingredient -> ingredient.test(stack)))
        problems.add(recipe + " does not consume " + material);
    });
    TECH_REMOVED.stream().filter(recipe -> recipes.byKey(id(recipe)).isPresent())
        .forEach(recipe -> problems.add(recipe + " still has a recipe"));
    ItemStack luminosity = new ItemStack(BuiltInRegistries.ITEM.get(id("entrelumen:luminosity_habitation")));
    for (String recipe : TECH_TOP_ARMOR) {
      var holder = recipes.byKey(id(recipe));
      if (holder.isEmpty()) {
        problems.add(recipe + " is not loaded");
        continue;
      }
      try {
        // Modern Industrialization's MachineRecipe keeps its inputs in the public itemInputs list.
        Object value = holder.get().value();
        boolean takes = false;
        for (Object input : (List<?>) value.getClass().getField("itemInputs").get(value))
          takes |= ((net.minecraft.world.item.crafting.Ingredient) input.getClass().getMethod("ingredient").invoke(input))
              .test(luminosity);
        if (!takes) problems.add(recipe + " does not take the habitation Luminosity");
      } catch (ReflectiveOperationException failure) {
        problems.add(recipe + ": " + failure);
      }
    }
    helper.assertTrue(problems.isEmpty(), "Round 5 batch 4: " + problems);
    helper.succeed();
  }
}
