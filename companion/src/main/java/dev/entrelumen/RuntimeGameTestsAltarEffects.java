package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Isolated GameTests of the Altars of Peace, Growth, Time and Repose; excluded from the
 * distributable jar by the RuntimeGameTests* pattern. Areas are shrunk with the altars' test hook so
 * no effect reaches a neighbouring test; the real sizes are checked through the registry, whose
 * answers every effect uses. Loot comparisons roll the real loot tables (global loot modifiers
 * included) with the same seed inside and outside an altar.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsAltarEffects {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final BlockPos ALTAR = new BlockPos(20, 1, 20);

  private RuntimeGameTestsAltarEffects() {}

  // ---- Fixtures ------------------------------------------------------------------------------

  private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
    return helper.absolutePos(new BlockPos(x, y, z));
  }

  private static void await(GameTestHelper helper, BooleanSupplier done, int waited, int limit, String what,
      Runnable then) {
    if (done.getAsBoolean()) {
      then.run();
      return;
    }
    helper.assertTrue(waited < limit, what + " did not happen in time");
    helper.runAfterDelay(1, () -> await(helper, done, waited + 1, limit, what, then));
  }

  private static void after(GameTestHelper helper, int ticks, Runnable then) {
    helper.runAfterDelay(ticks, then);
  }

  /** A logged-in mock player standing next to a block, as in the other altar tests. */
  private static final class Visitor implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Visitor(GameTestHelper helper, BlockPos near, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      try {
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
        player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 1.8);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    void use(BlockPos pos, ItemStack held, boolean crouching) {
      player.setItemInHand(InteractionHand.MAIN_HAND, held);
      player.setShiftKeyDown(crouching);
      player.gameMode.useItemOn(player, player.serverLevel(), held, InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
      player.setShiftKeyDown(false);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(net.minecraft.network.chat.Component.literal("Altar QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static <T extends AreaAltarEntity> T placeArea(GameTestHelper helper, net.minecraft.world.level.block.Block block,
      Class<T> type, int radius, int halfHeight) {
    BlockPos pos = helper.absolutePos(ALTAR);
    helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    T altar = type.cast(helper.getLevel().getBlockEntity(pos));
    altar.configureArea(radius, halfHeight);
    return altar;
  }

  private static long percentile(long[] samples, long count, double share) {
    int size = (int) Math.min(count, samples.length);
    if (size == 0) return 0;
    long[] sorted = java.util.Arrays.copyOf(samples, size);
    java.util.Arrays.sort(sorted);
    return sorted[Math.min(size - 1, Math.max(0, (int) Math.ceil(share * size) - 1))];
  }

  private static void perf(String scenario, AreaAltarEntity altar, String extra) {
    long mean = altar.workingTicks == 0 ? 0 : altar.tickNanos / altar.workingTicks;
    LOGGER.info("ALTAR_PERF scenario={} workingTicks={} wall=[mean={} p50={} p95={} max={}]us {}", scenario,
        altar.workingTicks, mean / 1000, percentile(altar.tickSamples, altar.workingTicks, 0.5) / 1000,
        percentile(altar.tickSamples, altar.workingTicks, 0.95) / 1000, altar.maxTickNanos / 1000, extra);
  }

  /** Registry answers on the corners of the altar's real area and one block beyond each face. */
  private static void assertSymmetricArea(GameTestHelper helper, AreaAltarEntity altar, AltarType type) {
    ServerLevel level = helper.getLevel();
    BlockPos c = altar.getBlockPos();
    int r = altar.areaRadius(), h = altar.areaHalfHeight();
    for (int sx : new int[] {-1, 1})
      for (int sy : new int[] {-1, 1})
        for (int sz : new int[] {-1, 1}) {
          helper.assertTrue(AltarRegistry.isInsideActive(level, type, c.offset(r * sx, h * sy, r * sz)),
              "Corner " + sx + "," + sy + "," + sz + " of the " + type.id() + " area is outside");
          helper.assertTrue(!AltarRegistry.isInsideActive(level, type, c.offset((r + 1) * sx, 0, 0))
              && !AltarRegistry.isInsideActive(level, type, c.offset(0, (h + 1) * sy, 0))
              && !AltarRegistry.isInsideActive(level, type, c.offset(0, 0, (r + 1) * sz)),
              "The " + type.id() + " area reaches beyond its faces");
        }
  }

  // ---- Altar of Peace ------------------------------------------------------------------------

  @GameTest(template = "nature_restoration", timeoutTicks = 400, skyAccess = true)
  public static void peaceAltarRefusesNaturalHostileSpawnsOnlyWhileFuelled(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var altar = placeArea(helper, Altars.PEACE_ALTAR.get(), PeaceAltarEntity.class, 10, 6);
    BlockPos c = altar.getBlockPos();
    BlockPos inside = c.offset(-10, 6, 10), beyond = c.offset(11, 0, 0), above = c.offset(0, 7, 0);
    helper.assertTrue(EventHooks.checkSpawnPlacements(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, inside,
        level.random, true), "An unfuelled Altar of Peace refused a spawn");
    try (var visitor = new Visitor(helper, c, "PeaceQA")) {
      visitor.use(c, new ItemStack(Items.CANDLE, 2), false);
      helper.assertTrue(visitor.player.getMainHandItem().isEmpty() && altar.fuel().getCount() == 2,
          "Candles did not feed the Altar of Peace");
    }
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, c, Direction.NORTH);
    helper.assertTrue(handler != null && !handler.insertItem(0, new ItemStack(Items.TORCH), true).isEmpty()
        && handler.insertItem(0, new ItemStack(Items.CANDLE), true).isEmpty()
        && !handler.insertItem(0, new ItemStack(Items.WHITE_CANDLE), true).isEmpty()
        && handler.extractItem(0, 1, true).isEmpty(),
        "The peace fuel slot takes more of its one candle kind only, and gives none back");
    after(helper, 1, () -> {
      helper.assertTrue(altar.fuel().getCount() == 1 && altar.fuelUsed() == 1
          && altar.activeTicks() >= AltarType.PEACE.ticksPerFuel() - 3
          && altar.activeTicks() < AltarType.PEACE.ticksPerFuel() && altar.effectActive(),
          "One candle should pay ten minutes: " + altar.activeTicks());
      record Case(EntityType<?> type, MobSpawnType spawn, BlockPos pos, boolean allowed, String what) {}
      List<Case> cases = List.of(
          new Case(EntityType.ZOMBIE, MobSpawnType.NATURAL, inside, false, "natural zombie at a corner"),
          new Case(EntityType.CREEPER, MobSpawnType.NATURAL, c.offset(10, -6, -10), false, "natural creeper, low corner"),
          new Case(EntityType.PHANTOM, MobSpawnType.NATURAL, c, false, "natural phantom"),
          new Case(EntityType.ZOMBIE, MobSpawnType.SPAWNER, inside, true, "spawner"),
          new Case(EntityType.ZOMBIE, MobSpawnType.TRIAL_SPAWNER, inside, true, "trial spawner"),
          new Case(EntityType.ZOMBIE, MobSpawnType.SPAWN_EGG, inside, true, "spawn egg"),
          new Case(EntityType.ZOMBIE, MobSpawnType.EVENT, inside, true, "siege event"),
          new Case(EntityType.PILLAGER, MobSpawnType.PATROL, inside, true, "patrol"),
          new Case(EntityType.ZOMBIE, MobSpawnType.MOB_SUMMONED, inside, true, "summon"),
          new Case(EntityType.WITHER, MobSpawnType.NATURAL, inside, true, "boss"),
          new Case(EntityType.COW, MobSpawnType.NATURAL, inside, true, "passive mob"),
          new Case(EntityType.ZOMBIE, MobSpawnType.NATURAL, beyond, true, "one block beyond the side"),
          new Case(EntityType.ZOMBIE, MobSpawnType.NATURAL, above, true, "one block above the cube"));
      for (Case check : cases)
        helper.assertTrue(EventHooks.checkSpawnPlacements(check.type(), level, check.spawn(), check.pos(), level.random,
            true) == check.allowed(), "Spawn rule wrong for " + check.what());
      // The catch-all for spawns that skip vanilla's rules.
      for (MobSpawnType spawn : List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER, MobSpawnType.EVENT)) {
        var zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(Vec3.atBottomCenterOf(inside));
        EventHooks.finalizeMobSpawn(zombie, level, level.getCurrentDifficultyAt(inside), spawn, null);
        helper.assertTrue(zombie.isSpawnCancelled() == (spawn == MobSpawnType.NATURAL),
            "Finalize-spawn cancellation wrong for " + spawn);
        zombie.discard();
      }
      // Cost of one natural spawn attempt inside the area: the whole event post, then the handler alone.
      int rounds = 100_000;
      long started = System.nanoTime();
      for (int i = 0; i < rounds; i++)
        EventHooks.checkSpawnPlacements(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, inside, level.random, true);
      long event = (System.nanoTime() - started) / rounds;
      var probe = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE,
          level, MobSpawnType.NATURAL, inside, level.random, true);
      started = System.nanoTime();
      for (int i = 0; i < rounds; i++) AltarEffects.onSpawnPlacement(probe);
      long handlerNs = (System.nanoTime() - started) / rounds;
      LOGGER.info("ALTAR_PERF scenario=peace-spawn-check eventPostNs={} handlerNs={} rounds={}", event, handlerNs, rounds);

      // Pause: no fuel burns and the area is open; resume closes it again.
      try (var visitor = new Visitor(helper, c, "PeaceQA2")) {
        visitor.use(c, ItemStack.EMPTY, true);
      }
      int paidAtPause = altar.activeTicks();
      after(helper, 5, () -> {
        helper.assertTrue(altar.paused() && altar.activeTicks() == paidAtPause
            && !AltarRegistry.isInsideActive(level, AltarType.PEACE, inside)
            && EventHooks.checkSpawnPlacements(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, inside, level.random, true),
            "A paused Altar of Peace burned fuel or still guarded");
        try (var visitor = new Visitor(helper, c, "PeaceQA3")) {
          visitor.use(c, ItemStack.EMPTY, true);
        }
        // The last paid ticks: when time and candles are gone, spawns come back.
        altar.activeTicks = 3;
        altar.fuel = ItemStack.EMPTY;
        after(helper, 5, () -> {
          helper.assertTrue(!altar.paused() && !altar.effectActive() && altar.activeTicks() == 0
              && !AltarRegistry.anyActive(level, AltarType.PEACE)
              && EventHooks.checkSpawnPlacements(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, inside, level.random, true),
              "An Altar of Peace out of fuel still guarded");
          // The real area: 129×129×129, centred and symmetric.
          altar.configureArea(-1, -1);
          altar.fuel = new ItemStack(Items.CANDLE);
          after(helper, 1, () -> {
            helper.assertTrue(altar.areaRadius() == 64 && altar.areaHalfHeight() == 64, "Peace area is not 64/64");
            assertSymmetricArea(helper, altar, AltarType.PEACE);
            level.removeBlock(c, false);
            helper.assertTrue(!AltarRegistry.anyActive(level, AltarType.PEACE), "A removed Altar of Peace stayed registered");
            helper.succeed();
          });
        });
      });
    });
  }

  // ---- Altar of Growth ------------------------------------------------------------------------

  private static List<ItemStack> roll(ServerLevel level, BlockState state, BlockPos origin, long seed) {
    var table = level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
    var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withParameter(LootContextParams.BLOCK_STATE, state)
        .create(LootContextParamSets.BLOCK);
    return table.getRandomItems(params, seed);
  }

  private static int count(List<ItemStack> stacks, Item item) {
    int total = 0;
    for (ItemStack stack : stacks) if (stack.is(item)) total += stack.getCount();
    return total;
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 2400, skyAccess = true)
  public static void growthAltarGrowsCropsFastAndDoublesRipeHarvestsButNotSeeds(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    for (int x = 12; x <= 30; x++)
      for (int z = 12; z <= 28; z++) {
        level.setBlock(at(helper, x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(at(helper, x, 0, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 2);
        for (int y = 1; y <= 8; y++) level.setBlock(at(helper, x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    var altar = placeArea(helper, Altars.GROWTH_ALTAR.get(), GrowthAltarEntity.class, 4, 1);
    altar.configureSamples(48);
    BlockPos c = altar.getBlockPos();
    List<BlockPos> crops = new ArrayList<>();
    for (int dx = -4; dx <= 4; dx++)
      for (int dz = -4; dz <= 4; dz++) {
        if (dx == 0 && dz == 0 || dx == -3 && dz == 3) continue;
        BlockPos pos = c.offset(dx, 0, dz);
        BlockState crop = switch (Math.floorMod(dx, 3)) {
          case 0 -> Blocks.WHEAT.defaultBlockState();
          case 1 -> Blocks.CARROTS.defaultBlockState();
          default -> Blocks.BEETROOTS.defaultBlockState();
        };
        level.setBlock(pos, crop, 2);
        crops.add(pos);
      }
    BlockPos sapling = c.offset(-3, 0, 3);
    level.setBlock(sapling.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
    level.setBlock(sapling, Blocks.OAK_SAPLING.defaultBlockState(), 2);
    BlockPos outsideWheat = c.offset(7, 0, 0);
    level.setBlock(outsideWheat, Blocks.WHEAT.defaultBlockState(), 2);

    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, c, Direction.UP);
    helper.assertTrue(handler != null && !handler.insertItem(0, new ItemStack(Items.BONE_MEAL), true).isEmpty()
        && handler.insertItem(0, new ItemStack(Items.BONE_BLOCK, 8), false).isEmpty(),
        "The growth fuel slot must take bone blocks, not bone meal");
    after(helper, 1, () -> helper.assertTrue(altar.fuelUsed() == 1
        && altar.activeTicks() >= AltarType.GROWTH.ticksPerFuel() - 3
        && altar.activeTicks() < AltarType.GROWTH.ticksPerFuel(), "One bone block should pay three minutes"));
    await(helper, () -> crops.stream().allMatch(pos -> level.getBlockState(pos).getBlock() instanceof CropBlock crop
        && crop.isMaxAge(level.getBlockState(pos))), 0, 1800, "Every crop in the field ripening", () -> {
      long ticks = altar.workingTicks;
      helper.assertTrue(altar.sampled <= 48L * ticks && altar.sampled >= 40L * ticks,
          "Sampling left its per-tick budget: " + altar.sampled + " in " + ticks + " ticks");
      BlockState saplingState = level.getBlockState(sapling);
      helper.assertTrue(!saplingState.is(Blocks.OAK_SAPLING) || saplingState.getValue(SaplingBlock.STAGE) == 1,
          "The sapling did not advance");
      helper.assertTrue(level.getBlockState(outsideWheat).getValue(CropBlock.AGE) == 0,
          "A crop outside the field grew (random ticking is off in tests)");
      perf("growth-test-field", altar, "sampled=" + altar.sampled + " ticked=" + altar.ticked);

      // Harvests: the same loot rolls inside and outside; only non-seed drops of ripe crops double.
      BlockPos outside = c.offset(12, 0, 0);
      BlockState wheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
      BlockState carrots = Blocks.CARROTS.defaultBlockState().setValue(CarrotBlock.AGE, 7);
      BlockState potatoes = Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE, 7);
      BlockState beetroots = Blocks.BEETROOTS.defaultBlockState().setValue(net.minecraft.world.level.block.BeetrootBlock.AGE, 3);
      BlockState youngCarrot = Blocks.CARROTS.defaultBlockState().setValue(CarrotBlock.AGE, 3);
      BlockState melon = Blocks.MELON.defaultBlockState();
      int[] sums = new int[16];
      for (long seed = 1; seed <= 40; seed++) {
        BlockPos in = c.offset(2, 0, -2);
        sums[0] += count(roll(level, wheat, in, seed), Items.WHEAT);
        sums[1] += count(roll(level, wheat, outside, seed), Items.WHEAT);
        sums[2] += count(roll(level, wheat, in, seed), Items.WHEAT_SEEDS);
        sums[3] += count(roll(level, wheat, outside, seed), Items.WHEAT_SEEDS);
        sums[4] += count(roll(level, carrots, in, seed), Items.CARROT);
        sums[5] += count(roll(level, carrots, outside, seed), Items.CARROT);
        sums[6] += count(roll(level, potatoes, in, seed), Items.POTATO)
            + count(roll(level, potatoes, in, seed), Items.POISONOUS_POTATO);
        sums[7] += count(roll(level, potatoes, outside, seed), Items.POTATO)
            + count(roll(level, potatoes, outside, seed), Items.POISONOUS_POTATO);
        sums[8] += count(roll(level, beetroots, in, seed), Items.BEETROOT);
        sums[9] += count(roll(level, beetroots, outside, seed), Items.BEETROOT);
        sums[10] += count(roll(level, beetroots, in, seed), Items.BEETROOT_SEEDS);
        sums[11] += count(roll(level, beetroots, outside, seed), Items.BEETROOT_SEEDS);
        sums[12] += count(roll(level, youngCarrot, in, seed), Items.CARROT);
        sums[13] += count(roll(level, youngCarrot, outside, seed), Items.CARROT);
        sums[14] += count(roll(level, melon, in, seed), Items.MELON_SLICE);
        sums[15] += count(roll(level, melon, outside, seed), Items.MELON_SLICE);
      }
      helper.assertTrue(sums[1] > 0 && sums[0] == 2 * sums[1], "Ripe wheat did not double: " + sums[0] + "/" + sums[1]);
      helper.assertTrue(sums[3] > 0 && sums[2] == sums[3], "Wheat seeds changed: " + sums[2] + "/" + sums[3]);
      helper.assertTrue(sums[5] > 0 && sums[4] == 2 * sums[5], "Carrots did not double: " + sums[4] + "/" + sums[5]);
      helper.assertTrue(sums[7] > 0 && sums[6] == 2 * sums[7], "Potatoes did not double: " + sums[6] + "/" + sums[7]);
      helper.assertTrue(sums[9] > 0 && sums[8] == 2 * sums[9], "Beetroots did not double");
      helper.assertTrue(sums[11] > 0 && sums[10] == sums[11], "Beetroot seeds changed");
      helper.assertTrue(sums[12] == sums[13], "An unripe carrot was multiplied: replanting would duplicate");
      helper.assertTrue(sums[15] > 0 && sums[14] == sums[15], "A placeable melon block was multiplied");

      // A real harvest by a player: one ripe wheat gives exactly two wheat.
      BlockPos harvested = crops.stream().filter(pos -> level.getBlockState(pos).is(Blocks.WHEAT)).findFirst().orElseThrow();
      try (var visitor = new Visitor(helper, c, "GrowthQA")) {
        level.destroyBlock(harvested, true, visitor.player);
      }
      int wheatDropped = 0;
      for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(harvested).inflate(2)))
        if (item.getItem().is(Items.WHEAT)) wheatDropped += item.getItem().getCount();
      helper.assertTrue(wheatDropped == 2, "A harvested ripe wheat dropped " + wheatDropped + " wheat");
      level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(8)).forEach(Entity::discard);

      // The real field and sampling rate, timed on the server thread.
      altar.configureArea(-1, -1);
      altar.configureSamples(-1);
      long before = altar.workingTicks;
      altar.workingTicks = 0;
      altar.tickNanos = 0;
      altar.maxTickNanos = 0;
      after(helper, 200, () -> {
        helper.assertTrue(altar.samplesPerTick() == 137, "Real sampling rate is not 137 per tick");
        assertSymmetricArea(helper, altar, AltarType.GROWTH);
        perf("growth-real-rate", altar, "samples=" + altar.samplesPerTick() + " earlierTicks=" + before);
        helper.assertTrue(percentile(altar.tickSamples, altar.workingTicks, 0.95) < 2_000_000,
            "Growth sampling exceeded 2 ms at p95");
        helper.succeed();
      });
    });
  }

  // ---- Altar of Time -------------------------------------------------------------------------

  private static List<ItemStack> rollEntity(ServerLevel level, LivingEntity killed, BlockPos origin, ServerPlayer killer,
      long seed) {
    var table = level.getServer().reloadableRegistries().getLootTable(killed.getLootTable());
    DamageSource source = level.damageSources().playerAttack(killer);
    var params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, killed)
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin)).withParameter(LootContextParams.DAMAGE_SOURCE, source)
        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer).create(LootContextParamSets.ENTITY);
    return table.getRandomItems(params, seed);
  }

  private static boolean third(LivingEntity mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
    var instance = mob.getAttribute(attribute);
    return instance != null && Math.abs(instance.getValue() - instance.getBaseValue() / 3) < 1e-9;
  }

  private static boolean normal(LivingEntity mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
    var instance = mob.getAttribute(attribute);
    return instance != null && Math.abs(instance.getValue() - instance.getBaseValue()) < 1e-9
        && !instance.hasModifier(AltarEffects.SLOW_ID);
  }

  @GameTest(template = "nature_restoration", timeoutTicks = 800, skyAccess = true)
  public static void timeAltarSlowsHostilesAndTheirProjectilesAndMultipliesPlayerKills(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    for (int x = 0; x < 40; x++)
      for (int z = 0; z < 40; z++) {
        level.setBlock(at(helper, x, 0, z), Blocks.STONE.defaultBlockState(), 2);
        for (int y = 1; y <= 10; y++) level.setBlock(at(helper, x, y, z), Blocks.AIR.defaultBlockState(), 2);
      }
    for (int z = 0; z < 40; z++)
      for (int y = 1; y <= 10; y++) level.setBlock(at(helper, 37, y, z), Blocks.STONE.defaultBlockState(), 2);
    var altar = placeArea(helper, Altars.TIME_ALTAR.get(), TimeAltarEntity.class, 8, 6);
    BlockPos c = altar.getBlockPos();
    Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, ALTAR.offset(-3, 0, 3));
    Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, ALTAR.offset(-12, 0, -12));
    var cow = helper.spawnWithNoFreeWill(EntityType.COW, ALTAR.offset(3, 0, 3));
    var pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, ALTAR.offset(-5, 0, -5));
    var blaze = helper.spawnWithNoFreeWill(EntityType.BLAZE, ALTAR.offset(5, 0, -5));
    helper.assertTrue(!AltarEffects.hostile(EntityType.WITHER.create(level)) && !AltarEffects.hostile(EntityType.ENDER_DRAGON.create(level))
        && AltarEffects.hostile(husk) && !AltarEffects.hostile(cow), "Boss or hostile classification is wrong");
    var visitor = new Visitor(helper, c, "TimeQA");
    visitor.use(c, new ItemStack(Items.AMETHYST_SHARD, 3), false);
    helper.assertTrue(altar.fuel().getCount() == 3, "Amethyst shards did not feed the Altar of Time");

    after(helper, 12, () -> {
      helper.assertTrue(third(husk, Attributes.MOVEMENT_SPEED) && third(husk, Attributes.ATTACK_DAMAGE)
          && third(husk, Attributes.ATTACK_KNOCKBACK), "A husk inside was not slowed to a third");
      helper.assertTrue(normal(far, Attributes.MOVEMENT_SPEED) && normal(cow, Attributes.MOVEMENT_SPEED),
          "A husk outside or a cow inside was slowed");
      // Leaving releases, coming back slows again.
      husk.teleportTo(c.getX() + 12.5, c.getY(), c.getZ() + 0.5);
      after(helper, 11, () -> {
        helper.assertTrue(normal(husk, Attributes.MOVEMENT_SPEED) && normal(husk, Attributes.ATTACK_DAMAGE),
            "A husk that left kept its slow modifiers");
        husk.teleportTo(c.getX() - 2.5, c.getY(), c.getZ() + 2.5);
        after(helper, 11, () -> {
          helper.assertTrue(third(husk, Attributes.MOVEMENT_SPEED), "A husk that came back was not slowed");
          // Projectiles: a hostile arrow flies at a third, the player's at full speed.
          Arrow hostileArrow = new Arrow(level, pillager, new ItemStack(Items.ARROW), null);
          hostileArrow.setPos(c.getX() - 6.5, c.getY() + 3, c.getZ() - 1.5);
          hostileArrow.shoot(1, 0, 0, 1.5f, 0);
          level.addFreshEntity(hostileArrow);
          Arrow playerArrow = new Arrow(level, visitor.player, new ItemStack(Items.ARROW), null);
          playerArrow.setPos(c.getX() - 6.5, c.getY() + 3, c.getZ() + 1.5);
          playerArrow.shoot(1, 0, 0, 1.5f, 0);
          level.addFreshEntity(playerArrow);
          SmallFireball fireball = new SmallFireball(level, blaze, new Vec3(0, 1, 0));
          fireball.setPos(c.getX() + 0.5, c.getY() + 2, c.getZ() - 3.5);
          level.addFreshEntity(fireball);
          double startPlayer = playerArrow.getX();
          List<Double> seen = new ArrayList<>();
          for (int t = 1; t <= 6; t++) after(helper, t, () -> seen.add(hostileArrow.getX()));
          after(helper, 7, () -> {
            helper.assertTrue(seen.size() == 6, "Missed arrow observations");
            for (int i = 1; i < seen.size(); i++) {
              double step = seen.get(i) - seen.get(i - 1);
              helper.assertTrue(Math.abs(step - 0.5) < 0.02, "Hostile arrow step " + step + " is not a third");
            }
            helper.assertTrue(hostileArrow.hasData(AltarEffects.SLOWED_PROJECTILE)
                && !playerArrow.hasData(AltarEffects.SLOWED_PROJECTILE), "Slowed marker on the wrong arrow");
            helper.assertTrue(playerArrow.isRemoved() || playerArrow.getX() - startPlayer > 5,
                "The player's arrow was slowed");
            double drop = hostileArrow.getDeltaMovement().y;
            helper.assertTrue(drop < 0 && drop > -0.05, "The hostile arrow brakes or drops like lead: " + drop);
            helper.assertTrue(Math.abs(fireball.accelerationPower - 0.1 / 3) < 1e-9
                && fireball.getDeltaMovement().length() < 0.2, "The blaze fireball was not slowed");
            // A saved slowed arrow keeps its mark and speeds up when it loads outside.
            CompoundTag saved = new CompoundTag();
            hostileArrow.save(saved);
            var copy = (Arrow) EntityType.create(saved, level).orElseThrow();
            helper.assertTrue(copy.hasData(AltarEffects.SLOWED_PROJECTILE), "The slowed mark was not saved");
            copy.setUUID(UUID.randomUUID());
            copy.setPos(c.getX() + 0.5, c.getY() + 12, c.getZ() + 12.5);
            copy.setNoGravity(true);
            double savedSpeed = copy.getDeltaMovement().horizontalDistance();
            level.addFreshEntity(copy);
            fireball.teleportTo(c.getX() + 0.5, c.getY() + 9, c.getZ() + 0.5);
            after(helper, 2, () -> {
              helper.assertTrue(!copy.hasData(AltarEffects.SLOWED_PROJECTILE)
                  && copy.getDeltaMovement().horizontalDistance() > 2.9 * savedSpeed * 0.97,
                  "A slowed arrow loaded outside did not regain its speed");
              helper.assertTrue(fireball.isRemoved() || Math.abs(fireball.accelerationPower - 0.1) < 1e-9,
                  "The fireball did not regain its power outside");
              copy.discard();
              fireball.discard();
              hostileArrow.discard();
              playerArrow.discard();
              killAndCount(helper, altar, visitor, husk, far, c);
            });
          });
        });
      });
    });
  }

  /** Loot and experience: three times inside for a real player's kill, never for equipment. */
  private static void killAndCount(GameTestHelper helper, TimeAltarEntity altar, Visitor visitor, Husk husk, Husk far,
      BlockPos c) {
    ServerLevel level = helper.getLevel();
    ServerPlayer killer = visitor.player;
    BlockPos outside = c.offset(12, 0, 0);
    int[] flesh = new int[4];
    for (long seed = 1; seed <= 40; seed++) {
      flesh[0] += count(rollEntity(level, husk, c, killer, seed), Items.ROTTEN_FLESH);
      flesh[1] += count(rollEntity(level, husk, outside, killer, seed), Items.ROTTEN_FLESH);
      flesh[2] += count(rollEntity(level, husk, c, FakePlayerFactory.getMinecraft(level), seed), Items.ROTTEN_FLESH);
      flesh[3] += count(rollEntity(level, husk, outside, FakePlayerFactory.getMinecraft(level), seed), Items.ROTTEN_FLESH);
    }
    helper.assertTrue(flesh[1] > 0 && flesh[0] == 3 * flesh[1], "Loot inside is not three times: " + flesh[0] + "/" + flesh[1]);
    helper.assertTrue(flesh[2] == flesh[3], "A fake player's kill got the bonus");
    // Performance of the per-entity checks while slowed and inside.
    int rounds = 100_000;
    long started = System.nanoTime();
    for (int i = 0; i < rounds; i++) AltarEffects.checkMob(level, husk);
    long mobNs = (System.nanoTime() - started) / rounds;
    LOGGER.info("ALTAR_PERF scenario=time-mob-check nsPerCheck={} rounds={} (each mob is checked every {} ticks)", mobNs,
        rounds, AltarEffectRules.MOB_CHECK_INTERVAL);
    // A husk carrying ten cobblestone dies inside to the player: its equipment drops once, as is.
    husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 10));
    husk.setDropChance(EquipmentSlot.MAINHAND, 2.0f);
    killer.teleportTo(c.getX() + 0.5, c.getY() + 30, c.getZ() + 0.5);
    java.util.Map<UUID, Integer> experience = new java.util.concurrent.ConcurrentHashMap<>();
    java.util.function.Consumer<LivingExperienceDropEvent> recorder = event -> {
      if (event.getEntity() == husk || event.getEntity() == far)
        experience.put(event.getEntity().getUUID(), event.getDroppedExperience());
    };
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingExperienceDropEvent.class, recorder);
    husk.hurt(level.damageSources().playerAttack(killer), 1000);
    far.hurt(level.damageSources().playerAttack(killer), 1000);
    helper.assertTrue(husk.isDeadOrDying() && normal(husk, Attributes.MOVEMENT_SPEED) && normal(husk, Attributes.ATTACK_DAMAGE),
        "A husk that died inside kept its slow modifiers");
    int cobble = 0;
    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(husk.blockPosition()).inflate(3)))
      if (item.getItem().is(Items.COBBLESTONE)) cobble += item.getItem().getCount();
    helper.assertTrue(cobble == 10, "Equipment was multiplied: " + cobble);
    await(helper, () -> husk.isRemoved() && far.isRemoved(), 0, 60, "Both husks finishing their death", () -> {
      NeoForge.EVENT_BUS.unregister(recorder);
      Integer inside = experience.get(husk.getUUID()), outsideXp = experience.get(far.getUUID());
      helper.assertTrue(Integer.valueOf(5).equals(outsideXp) && Integer.valueOf(15).equals(inside),
          "Experience inside " + inside + ", outside " + outsideXp);
      visitor.close();
      level.getEntitiesOfClass(Entity.class, new AABB(c).inflate(20),
          entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof LivingEntity
              && !(entity instanceof ServerPlayer)).forEach(Entity::discard);
      // Fuel and time, then the real 33×33×33 area.
      helper.assertTrue(altar.fuelUsed() == 1 && altar.activeTicks() > 0
          && altar.activeTicks() < AltarType.TIME.ticksPerFuel(), "One amethyst shard should pay two minutes");
      altar.configureArea(-1, -1);
      after(helper, 1, () -> {
        helper.assertTrue(altar.areaRadius() == 16 && altar.areaHalfHeight() == 16, "Time area is not 16/16");
        assertSymmetricArea(helper, altar, AltarType.TIME);
        helper.succeed();
      });
    });
  }

  // ---- Altar of Repose -----------------------------------------------------------------------

  private static ItemStack worn(Item item, int damage) {
    ItemStack stack = new ItemStack(item);
    stack.setDamageValue(damage);
    return stack;
  }

  @GameTest(template = "empty", timeoutTicks = 900)
  public static void reposeAltarMendsSlowlyWithoutDuplicatingAndPipesTakeOnlyMendedGear(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos c = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(c, Altars.REPOSE_ALTAR.get().defaultBlockState());
    var altar = (ReposeAltarEntity) level.getBlockEntity(c);
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, c, Direction.UP);
    helper.assertTrue(handler != null && handler.getSlots() == 5, "Pipes should see fuel plus four slots");
    ItemStack unbreakable = new ItemStack(Items.DIAMOND_PICKAXE);
    unbreakable.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
    helper.assertTrue(!handler.insertItem(1, new ItemStack(Items.DIRT), true).isEmpty()
        && !handler.insertItem(1, unbreakable, true).isEmpty()
        && !handler.insertItem(1, new ItemStack(Items.DIAMOND_SWORD), true).isEmpty(),
        "Pipes put in something that is not worn, repairable gear");
    helper.assertTrue(handler.insertItem(1, worn(Items.DIAMOND_PICKAXE, 10), false).isEmpty()
        && !handler.insertItem(1, worn(Items.IRON_AXE, 5), true).isEmpty(), "A worn pickaxe did not go in, alone");
    try (var visitor = new Visitor(helper, c, "ReposeQA")) {
      visitor.use(c, ItemStack.EMPTY, false);
      helper.assertTrue(visitor.player.containerMenu instanceof ReposeAltarMenu, "Use did not open the coffer");
      var menu = (ReposeAltarMenu) visitor.player.containerMenu;
      helper.assertTrue(menu.slots.size() == 4 + 36 && !menu.getSlot(1).mayPlace(new ItemStack(Items.DIRT))
          && menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_SWORD)) && menu.getSlot(1).getMaxStackSize() == 1,
          "The coffer's slots accept the wrong items");
      menu.getSlot(1).set(worn(Items.IRON_SWORD, 3));
      menu.getSlot(2).set(new ItemStack(Items.SHEARS));
      visitor.player.closeContainer();
    }
    helper.assertTrue(altar.state() == 2 && handler.extractItem(1, 1, true).isEmpty()
        && handler.extractItem(3, 1, true).is(Items.SHEARS), "Pipes took worn gear, or not the whole shears");
    after(helper, 150, () -> {
      helper.assertTrue(altar.getItem(0).getDamageValue() == 10 && altar.activeTicks() == 0 && altar.repaired == 0,
          "The coffer mended without fuel");
      try (var visitor = new Visitor(helper, c, "ReposeQA2")) {
        visitor.use(c, new ItemStack(Items.EXPERIENCE_BOTTLE, 2), false);
        helper.assertTrue(altar.fuel().getCount() == 2 && visitor.player.getMainHandItem().isEmpty(),
            "Bottles o' Enchanting did not feed the coffer");
      }
      after(helper, 250, () -> {
        helper.assertTrue(altar.getItem(0).getDamageValue() == 8 && altar.getItem(1).getDamageValue() == 1
            && altar.repaired == 4 && altar.fuelUsed() == 1 && Math.abs(altar.activeTicks() - 950) <= 3,
            "Mending is not one point per item per 100 paid ticks: " + altar.getItem(0).getDamageValue() + " "
                + altar.getItem(1).getDamageValue() + " " + altar.activeTicks());
        int items = 0;
        for (int slot = 0; slot < 4; slot++) items += altar.getItem(slot).getCount();
        helper.assertTrue(items == 3, "Items were duplicated or lost: " + items);
        // Save and load mid-round.
        var registries = level.registryAccess();
        CompoundTag saved = altar.saveWithFullMetadata(registries);
        int progress = altar.progress();
        altar.loadWithComponents(saved, registries);
        helper.assertTrue(altar.progress() == progress && altar.repaired == 4 && altar.getItem(0).getDamageValue() == 8
            && altar.fuel().getCount() == 1, "Repose state did not round-trip");
        await(helper, () -> altar.getItem(1).getDamageValue() == 0, 0, 200, "The sword mending fully", () -> {
          ItemStack sword = handler.extractItem(2, 1, false);
          helper.assertTrue(sword.is(Items.IRON_SWORD) && !sword.isDamaged() && altar.getItem(1).isEmpty(),
              "A pipe could not take the mended sword");
          handler.extractItem(3, 1, false);
          altar.removeItem(0, 1);
          int paid = altar.activeTicks();
          after(helper, 30, () -> {
            helper.assertTrue(altar.activeTicks() == paid && altar.state() == 0,
                "The coffer paid time with nothing to mend");
            altar.setItem(0, worn(Items.DIAMOND_PICKAXE, 6));
            level.destroyBlock(c, false);
            int bottles = 0, picks = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(2))) {
              if (item.getItem().is(Items.EXPERIENCE_BOTTLE)) bottles += item.getItem().getCount();
              if (item.getItem().is(Items.DIAMOND_PICKAXE)) picks += item.getItem().getCount();
            }
            helper.assertTrue(bottles == 1 && picks == 1, "Breaking dropped " + bottles + " bottles and " + picks + " picks");
            helper.succeed();
          });
        });
      });
    });
  }
}
