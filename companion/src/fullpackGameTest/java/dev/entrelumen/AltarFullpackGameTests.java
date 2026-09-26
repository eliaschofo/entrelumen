package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for the Altars of Renewal and Levelling, registered in {@link FullpackQABootstrap}
 * and run on the owned QA server with {@code -Dentrelumen.qa=true}.
 *
 * <ul>
 *   <li>The Altar of Renewal's harvest makes all sixteen dyes in every biome the pack registers, by
 *       the crafting and smelting recipes the server actually loaded (KubeJS changes included).</li>
 *   <li>Its trees grow in the modded dimensions' biomes through the pinned mods' own features, and
 *       its ink caps and mod flowers live on ordinary soil.</li>
 *   <li>The Altar of Levelling's repair rebuilds a pit dug in land made by the pack's real overworld
 *       generator, compared with an independent regeneration. Run it on untouched land away from the
 *       spawn test area, with the land 28-68 blocks east of the test loaded.</li>
 *   <li>A real FTB Chunks claim refuses a foreign team's Levelling repair, flatten and Renewal
 *       planting inside the claimed chunk, while outside the work goes on.</li>
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

  // ---- Renewal: dyes and modded trees --------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void renewalAltarHarvestMakesEveryDyeInEveryBiome(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var garden = AltarVegetation.garden(level.registryAccess());
    List<String> species = garden.species().stream()
        .map(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()).toList();
    helper.assertTrue(species.equals(AltarVegetation.GARDEN) && garden.inkCap() != null,
        "The pack does not provide the whole dye garden: " + species + " inkCap=" + (garden.inkCap() != null));
    long started = System.nanoTime();
    var closure = new VegetationRules.Closure(AltarVegetation.recipes(level));
    long indexed = System.nanoTime() - started;
    // The garden alone, without any biome flora, must already make every dye.
    Set<String> gardenOnly = new java.util.TreeSet<>();
    for (BlockState state : garden.species()) gardenOnly.addAll(AltarVegetation.loot(level, state, 4));
    gardenOnly.addAll(AltarVegetation.loot(level,
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("undergarden:ink_mushroom_cap")).defaultBlockState(), 64));
    var gardenMissing = closure.missing(gardenOnly);
    helper.assertTrue(gardenMissing.isEmpty(), "The dye garden alone misses " + gardenMissing);
    // Every biome, a few per tick so no tick of the test itself runs long.
    var biomes = level.registryAccess().registryOrThrow(Registries.BIOME).holders().toList();
    Map<String, Integer> variants = new TreeMap<>();
    Map<String, String> failures = new TreeMap<>();
    Map<String, Integer> trees = new TreeMap<>();
    long[] worst = {0};
    dyeBatch(helper, level, biomes, 0, closure, variants, failures, trees, worst, () -> {
      long treeless = trees.values().stream().filter(count -> count == 0).count();
      com.mojang.logging.LogUtils.getLogger().info(
          "ALTAR_DYES fullpack biomes={} recipes={} indexMillis={} totalMillis={} worstBiomeMillis={} variants={} gardenHarvest={} gardenMissing={} treelessBiomes={} failures={}",
          biomes.size(), closure.size(), indexed / 1_000_000, (System.nanoTime() - started) / 1_000_000,
          worst[0] / 1_000_000, variants, gardenOnly, gardenMissing, treeless, failures);
      com.mojang.logging.LogUtils.getLogger().info("ALTAR_TREES fullpack options={}", trees);
      helper.assertTrue(failures.isEmpty(), "Biomes whose harvest misses dyes: " + failures);
      helper.succeed();
    });
  }

  private static void dyeBatch(GameTestHelper helper, ServerLevel level,
      List<? extends net.minecraft.core.Holder<Biome>> biomes, int from, VegetationRules.Closure closure,
      Map<String, Integer> variants, Map<String, String> failures, Map<String, Integer> trees, long[] worst,
      Runnable then) {
    int to = Math.min(biomes.size(), from + 8);
    for (int i = from; i < to; i++) {
      long started = System.nanoTime();
      var biome = biomes.get(i);
      var palette = AltarVegetation.palette(level, biome);
      variants.merge(palette.variant(), 1, Integer::sum);
      trees.put(biome.getRegisteredName(), palette.trees().size());
      var harvest = AltarVegetation.harvest(level, biome);
      var missing = closure.missing(harvest);
      if (!missing.isEmpty()) failures.put(biome.getRegisteredName(), missing + " from " + harvest);
      worst[0] = Math.max(worst[0], System.nanoTime() - started);
    }
    if (to >= biomes.size()) then.run();
    else helper.runAfterDelay(1, () -> dyeBatch(helper, level, biomes, to, closure, variants, failures, trees, worst,
        then));
  }

  private static void biome(GameTestHelper helper, ResourceKey<Biome> biome, int fromX, int fromZ, int toX, int toZ) {
    var level = helper.getLevel();
    var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
    var filled = net.minecraft.server.commands.FillBiomeCommand.fill(level,
        helper.absolutePos(new BlockPos(fromX, -4, fromZ)), helper.absolutePos(new BlockPos(toX, 13, toZ)), holder);
    helper.assertTrue(filled.left().isPresent(), "Biome fixture " + biome.location() + " was refused");
  }

  /** A pad of a mod's soil in a mod's biome; every tree option the palette offers is tried there in turn. */
  private static String growPad(GameTestHelper helper, RenewalAltarEntity altar, String biomeId, String soilId,
      int padX, int padZ) {
    var level = helper.getLevel();
    ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
    if (level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(key).isEmpty()) return "missing biome";
    ResourceLocation soilKey = ResourceLocation.parse(soilId);
    if (!BuiltInRegistries.BLOCK.containsKey(soilKey)) return "missing soil";
    Block soil = BuiltInRegistries.BLOCK.get(soilKey);
    biome(helper, key, padX - 4, padZ - 4, padX + 4, padZ + 4);
    for (int x = padX - 4; x <= padX + 4; x++)
      for (int z = padZ - 4; z <= padZ + 4; z++) {
        level.setBlock(helper.absolutePos(new BlockPos(x, -1, z)), soil.defaultBlockState(), 2);
        level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), soil.defaultBlockState(), 2);
      }
    BlockPos ground = helper.absolutePos(new BlockPos(padX, 0, padZ));
    var palette = AltarVegetation.palette(level, level.getBiome(ground.above()));
    List<String> tried = new ArrayList<>();
    for (var option : palette.treesFor(soil.defaultBlockState())) {
      String result = altar.growForTest(level, ground, option);
      tried.add(option.id() + "=" + result);
      if (result.equals("APPLIED")) return "APPLIED " + tried;
    }
    return "none " + tried + " of " + palette.trees().stream().map(AltarVegetation.TreeOption::id).toList();
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void renewalAltarGrowsTheTreesOfModdedDimensions(GameTestHelper helper) {
    var level = helper.getLevel();
    BlockPos altarPos = helper.absolutePos(new BlockPos(20, 1, 20));
    level.setBlockAndUpdate(altarPos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
    var altar = (RenewalAltarEntity) level.getBlockEntity(altarPos);
    altar.configureRadius(12);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    Map<String, String> results = new LinkedHashMap<>();
    results.put("aether:skyroot_forest", growPad(helper, altar, "aether:skyroot_forest", "aether:aether_grass_block", 10, 10));
    results.put("undergarden:smogstem_forest", growPad(helper, altar, "undergarden:smogstem_forest",
        "undergarden:deepturf_block", 10, 30));
    results.put("eternal_starlight:starlight_forest", growPad(helper, altar, "eternal_starlight:starlight_forest",
        "eternal_starlight:nightfall_grass_block", 30, 10));
    results.put("twilightforest:dark_forest", growPad(helper, altar, "twilightforest:dark_forest",
        "minecraft:grass_block", 30, 30));
    com.mojang.logging.LogUtils.getLogger().info("ALTAR_MODDED_TREES results={} guards={} grown={}", results,
        altar.guards, altar.grown);
    for (var entry : results.entrySet())
      helper.assertTrue(entry.getValue().startsWith("APPLIED"), entry.getKey() + " grew no tree: " + entry.getValue()
          + " guards=" + altar.guards);
    helper.succeed();
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void renewalAltarGrowsInkCapsAndModFlowersOnPlainSoil(GameTestHelper helper) {
    var level = helper.getLevel();
    for (int x = 0; x < EXTENT; x++)
      for (int z = 0; z < EXTENT; z++) {
        level.setBlock(helper.absolutePos(new BlockPos(x, -1, z)), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
      }
    BlockPos altarPos = helper.absolutePos(new BlockPos(20, 1, 20));
    level.setBlockAndUpdate(altarPos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
    var altar = (RenewalAltarEntity) level.getBlockEntity(altarPos);
    altar.configureRadius(12);
    altar.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
    BlockPos ground = helper.absolutePos(new BlockPos(10, 0, 10));
    String cap = altar.growForTest(level, ground, null);
    int caps = 0;
    Block capBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("undergarden:ink_mushroom_cap"));
    for (int x = 4; x <= 16; x++)
      for (int z = 4; z <= 16; z++)
        for (int y = 1; y <= 16; y++)
          if (level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(capBlock)) caps++;
    Map<String, Boolean> survives = new TreeMap<>();
    BlockPos flowerPos = helper.absolutePos(new BlockPos(30, 1, 30));
    for (BlockState species : AltarVegetation.garden(level.registryAccess()).species())
      survives.put(BuiltInRegistries.BLOCK.getKey(species.getBlock()).toString(), species.canSurvive(level, flowerPos));
    level.setBlock(flowerPos.below(), Blocks.MYCELIUM.defaultBlockState(), 2);
    boolean inkOnMycelium = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("undergarden:ink_mushroom"))
        .defaultBlockState().canSurvive(level, flowerPos);
    com.mojang.logging.LogUtils.getLogger().info("ALTAR_GARDEN inkCap={} capBlocks={} survivesOnGrass={} inkOnMycelium={}",
        cap, caps, survives, inkOnMycelium);
    helper.assertTrue(cap.equals("APPLIED") && caps >= 8, "The ink cap did not grow: " + cap + " " + caps + " " + altar.guards);
    for (String flower : List.of("minecraft:lily_of_the_valley", "minecraft:poppy", "minecraft:dandelion",
        "minecraft:cornflower", "eternal_starlight:conebloom", "eternal_starlight:swamp_rose"))
      helper.assertTrue(Boolean.TRUE.equals(survives.get(flower)), flower + " cannot live on grass: " + survives);
    helper.assertTrue(inkOnMycelium, "The ink mushroom cannot live on mycelium");
    helper.succeed();
  }

  // ---- Levelling repair against the pack's generator ------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void levellingAltarRepairsLandFromThePacksOwnGenerator(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos origin = helper.absolutePos(BlockPos.ZERO);
    int x = origin.getX() + 48, z = origin.getZ() + 4;
    int radius = 8;
    await(helper, () -> LandWorks.loaded(level, (x - radius - 28) >> 4, (z - radius - 28) >> 4,
        (x + radius + 28) >> 4, (z + radius + 28) >> 4), 0, 400, "Natural land near spawn loading", () -> {
      // The first spot within 16 blocks whose altar column and pit are open natural ground.
      BlockPos altarPos = null;
      List<BlockPos> pit = new ArrayList<>();
      for (int ring = 0; ring <= 16 && altarPos == null; ring++)
        for (int sx = x - ring; sx <= x + ring && altarPos == null; sx++)
          for (int sz = z - ring; sz <= z + ring && altarPos == null; sz++) {
            if (Math.max(Math.abs(sx - x), Math.abs(sz - z)) != ring) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) - 1;
            BlockPos candidate = new BlockPos(sx, surface + 1, sz);
            if (!LandWorks.ground(level.getBlockState(candidate.below())) || !level.getBlockState(candidate).isAir())
              continue;
            List<BlockPos> blocks = new ArrayList<>();
            for (int dx = 3; dx <= 5; dx++)
              for (int dz = -1; dz <= 1; dz++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx + dx, sz + dz) - 1;
                for (int dy = 0; dy >= -2; dy--) {
                  BlockPos pos = new BlockPos(sx + dx, top + dy, sz + dz);
                  if (LandWorks.ground(level.getBlockState(pos)) && level.getBlockState(pos.above()).getFluidState().isEmpty())
                    blocks.add(pos);
                }
              }
            if (blocks.size() >= 18) {
              altarPos = candidate;
              pit.addAll(blocks);
            }
          }
      helper.assertTrue(altarPos != null, "No open natural ground within 16 blocks; choose another place or world");
      int ax = altarPos.getX(), az = altarPos.getZ();
      BlockPos placed = altarPos;
      level.setBlockAndUpdate(placed, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
      var altar = (TerraformAltarEntity) level.getBlockEntity(placed);
      altar.configureSize(TerraformRules.REPAIR);
      altar.configureRepairRadius(radius);
      List<ChunkPos> decorate = new ArrayList<>();
      for (var key : AltarRules.areaChunks(ax, az, radius)) decorate.add(new ChunkPos(key.x(), key.z()));
      decorate.sort(java.util.Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
      List<ChunkPos> captured = new ArrayList<>();
      for (ChunkPos pos : decorate)
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) captured.add(new ChunkPos(pos.x + dx, pos.z + dz));
      var reference = TerrainReference.build(TerrainReference.Setup.capture(level, captured), decorate, () -> false);
      for (BlockPos pos : pit) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
      altar.addFuel(new ItemStack(Items.COAL, 4));
      altar.start(level);
      await(helper, () -> altar.state() == TerraformAltarEntity.State.DONE
          || altar.state() == TerraformAltarEntity.State.FAILED, 0, 3000, "Real-generator repair", () -> {
        com.mojang.logging.LogUtils.getLogger().info(
            "ALTAR_FULLPACK repair totals filled={} covered={} refused={} skipped={} fuel={} refusals={} reference=[terrain={} decorated={} terrainMillis={} decorationMillis={} failures={} unsupported={}]",
            altar.totals().filled, altar.totals().covered, altar.totals().refused, altar.totals().skippedChunks,
            altar.fuelUsed(), altar.refusals(), reference.terrainChunks, reference.decoratedChunks,
            reference.terrainNanos / 1_000_000, reference.decorationNanos / 1_000_000, reference.failures,
            reference.unsupported);
        helper.assertTrue(altar.state() == TerraformAltarEntity.State.DONE && altar.totals().skippedChunks == 0,
            "The pack's own land was not recognised: " + altar.state() + " skipped=" + altar.totals().skippedChunks);
        for (BlockPos pos : pit) {
          BlockState original = reference.state(pos);
          BlockState now = level.getBlockState(pos);
          if (!TerraformAltarEntity.fillable(original)) continue;
          BlockState expected = TerraformAltarEntity.fill(original, pos.getY());
          helper.assertTrue(now.equals(expected) || now.is(Blocks.SANDSTONE) || now.is(Blocks.RED_SANDSTONE)
              || now.is(Blocks.STONE) || TerraformAltarEntity.basic(now) && now.is(original.getBlock()),
              "Repaired " + pos + " with " + now + " instead of the generator's " + original);
          helper.assertTrue(TerraformAltarEntity.basic(now), "A repair placed something that is not plain terrain: " + now);
        }
        helper.succeed();
      });
    });
  }

  // ---- Claims --------------------------------------------------------------------------------

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

  private static Map<BlockPos, BlockState> chunkSnapshot(ServerLevel level, ChunkPos chunk, int low, int high) {
    Map<BlockPos, BlockState> states = new HashMap<>();
    for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++)
      for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++)
        for (int y = low; y <= high; y++) {
          BlockPos pos = new BlockPos(x, y, z);
          states.put(pos, level.getBlockState(pos));
        }
    return states;
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 6000, skyAccess = true)
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
    Runnable cleanup = () -> cleanup(owner, visitor, east);
    // 1. The visitor's Altar of Levelling repairs the pit: only outside the claim.
    level.setBlockAndUpdate(altarPos, Altars.TERRAFORM_ALTAR.get().defaultBlockState());
    var levelling = (TerraformAltarEntity) level.getBlockEntity(altarPos);
    levelling.setOwner(visitor.player.getUUID(), visitor.player.getGameProfile().getName());
    levelling.configureSize(TerraformRules.REPAIR);
    levelling.configureRepairRadius(10);
    TerraformAltarEntity.SETUPS.put(GlobalPos.of(level.dimension(), altarPos), world -> flat(helper));
    visitor.player.teleportTo(altarPos.getX() + 0.5, altarPos.getY(), altarPos.getZ() + 1.8);
    levelling.addFuel(new ItemStack(Items.COAL, 8));
    levelling.start(level);
    await(helper, () -> levelling.state() == TerraformAltarEntity.State.DONE, 0, 2000, "Claimed repair", () -> {
      try {
        for (BlockPos top : pit) {
          boolean inClaim = new ChunkPos(top).equals(claimed);
          boolean restored = level.getBlockState(top).is(Blocks.GRASS_BLOCK);
          helper.assertTrue(inClaim ? level.getBlockState(top).isAir() && level.getBlockState(top.below(2)).isAir() : restored,
              "The claim boundary was not respected at " + top);
        }
        helper.assertTrue(levelling.refusals().getOrDefault("claim", 0) > 0, "The claim refused no repair column");
        TerraformAltarEntity.SETUPS.remove(GlobalPos.of(level.dimension(), altarPos));
        // 2. The same altar flattens: columns inside the claim are refused whole.
        var claimedBefore = chunkSnapshot(level, claimed, altarPos.getY() - 4, altarPos.getY() + 4);
        for (int x = claimed.getMinBlockX() - 3; x < claimed.getMinBlockX(); x++)
          for (int dy = -1; dy >= -2; dy--)
            level.setBlock(new BlockPos(x, altarPos.getY() + dy, altarPos.getZ() - 5), Blocks.AIR.defaultBlockState(), 2);
        levelling.configureSize(8);
        levelling.start(level);
        await(helper, () -> levelling.state() == TerraformAltarEntity.State.DONE, 0, 1600, "Claimed flatten", () -> {
          try {
            claimedBefore.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state),
                "A foreign Altar of Levelling changed the claimed chunk at " + pos));
            helper.assertTrue(levelling.refusals().getOrDefault("claim", 0) > 0 && levelling.totals().filled > 0,
                "The claim did not refuse columns or nothing outside was filled: " + levelling.refusals());
            level.removeBlock(altarPos, false);
            // 3. The visitor's Altar of Renewal plants outside the claim only. The altar itself stands in
            // the claimed chunk (placed by the test), so the snapshot is taken with it in place.
            level.setBlockAndUpdate(altarPos, Altars.RENEWAL_ALTAR.get().defaultBlockState());
            var plantedBefore = chunkSnapshot(level, claimed, altarPos.getY() - 2, altarPos.getY() + 30);
            var renewal = (RenewalAltarEntity) level.getBlockEntity(altarPos);
            renewal.setOwner(visitor.player.getUUID(), visitor.player.getGameProfile().getName());
            renewal.configureRadius(10);
            renewal.addFertilizer(new ItemStack(Items.BONE_MEAL, 64));
            await(helper, () -> renewal.state() == RenewalAltarEntity.State.DONE
                || renewal.state() == RenewalAltarEntity.State.WAITING, 0, 2000, "Claimed planting", () -> {
              try {
                plantedBefore.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state),
                    "A foreign Altar of Renewal planted in the claimed chunk at " + pos));
                helper.assertTrue(renewal.totals().plants > 0 && renewal.guards.getOrDefault("plant_claim", 0) > 0,
                    "Nothing grew outside or the claim refused nothing: " + renewal.guards);
                com.mojang.logging.LogUtils.getLogger().info(
                    "ALTAR_FULLPACK claims repairRefusals={} renewal=[plants={} trees={} guards={}]",
                    levelling.refusals(), renewal.totals().plants, renewal.totals().trees, renewal.guards);
                level.removeBlock(altarPos, false);
                helper.succeed();
              } finally {
                cleanup.run();
              }
            }, cleanup);
          } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
          }
        }, cleanup);
      } catch (RuntimeException | Error failure) {
        cleanup.run();
        throw failure;
      }
    }, cleanup);
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
        connection.disconnect(Component.literal("Entrelumen altar QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }
}
