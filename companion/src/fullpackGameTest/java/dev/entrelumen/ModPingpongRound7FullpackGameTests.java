package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Mod ping-pong round 7 (docs/design/mod-pingpong.md, «Ronda 7»): the outward ASKs Elias approved on 28 September. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ModPingpongRound7FullpackGameTests {
  static final List<String> ROUND7 = List.of("mysticalagradditions", "hardcorerevival", "me_beam_former", "squatgrow");
  /** Animus waits for TeamDman/Animus#156 (the bound spear crashes multiplayer clients in 5.2.13). */
  static final List<String> ROUND7_DEFERRED = List.of("animusnv");
  /** pingpong7 gate: insanium only from supremium blocks around the master crystal, with the Nature Luminosity. */
  static final Map<String, String> ROUND7_STAGED = Map.of(
      "mysticalagradditions:insanium_block_combine", "entrelumen:luminosity_nature");
  static final List<String> ROUND7_REMOVED = List.of("mysticalagradditions:insanium_essence",
      "me_beam_former:wireless_energy_tower");
  static final List<String> CARRY_ON_REFUSED = List.of("me_beam_former:beam_former_block",
      "me_beam_former:omni_beam_former_block");
  static final String REVIVAL = "net.blay09.mods.hardcorerevival.PlayerHardcoreRevivalManager";
  static final String REVIVAL_MANAGER = "net.blay09.mods.hardcorerevival.HardcoreRevivalManager";
  static final String REVIVAL_CONFIG = "net.blay09.mods.hardcorerevival.config.HardcoreRevivalConfig";

  private static final Logger LOGGER = LogUtils.getLogger();

  private ModPingpongRound7FullpackGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound7Loaded(GameTestHelper helper) throws ReflectiveOperationException {
    List<String> problems = new ArrayList<>();
    ROUND7.stream().filter(mod -> !ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " not loaded"));
    ROUND7_DEFERRED.stream().filter(mod -> ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " is loaded"));
    ModPingpongRound5FullpackGameTests.staged(helper, ROUND7_STAGED, problems);
    var recipes = helper.getLevel().getRecipeManager();
    ROUND7_REMOVED.stream().filter(recipe -> recipes.byKey(ResourceLocation.parse(recipe)).isPresent())
        .forEach(recipe -> problems.add(recipe + " still has a recipe"));
    // pack/config/hardcorerevival-common.toml: co-op only.
    Object config = Class.forName(REVIVAL_CONFIG).getMethod("getActive").invoke(null);
    for (String field : List.of("disableInSingleplayer", "disableInLonelyMultiplayer"))
      if (!(boolean) config.getClass().getField(field).get(config)) problems.add("Hardcore Revival " + field + " is off");
    // pack/config/squatgrow-common.yaml: five times slower than the defaults (Elias, 29 September).
    Object squat = Class.forName("dev.wuffs.squatgrow.SquatGrow").getField("config").get(null);
    Map<String, Object> expected = new java.util.LinkedHashMap<>();
    expected.put("chance", 0.2f);
    expected.put("randomTickMultiplier", 2);
    expected.put("range", 2);
    expected.put("sugarcaneMultiplier", 1);
    expected.put("enableMysticalCrops", false);
    expected.put("enableAE2Accelerator", false);
    expected.put("enableDirtToGrass", false);
    expected.put("requireHoe", false);
    expected.forEach((field, want) -> {
      try {
        Object have = squat.getClass().getField(field).get(squat);
        if (!want.equals(have)) problems.add("Squat Grow " + field + " is " + have);
      } catch (ReflectiveOperationException failure) {
        problems.add("Squat Grow " + field + ": " + failure);
      }
    });
    List<?> ignored = (List<?>) squat.getClass().getField("ignoreList").get(squat);
    for (String entry : List.of("minecraft:grass_block", "mysticalagriculture:*", "mysticalagradditions:*", "#mysticalagriculture:crops"))
      if (!ignored.contains(entry)) problems.add("Squat Grow ignoreList lacks " + entry);
    helper.assertTrue(problems.isEmpty(), "Round 7: " + problems);
    helper.succeed();
  }

  /**
   * Squat Grow next to a wheat crop advances it, and next to a Mystical Agriculture crop, which extends CropBlock
   * and would grow through the plain crop action, it does not (the pack's ignoreList). A mock player squats
   * (SquatAction.performAction, what the crouch mixin calls) a few hundred times beside both; the blocks go back
   * to air however the case ends.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void squatGrowAdvancesWheatButNotMysticalCrops(GameTestHelper helper) throws Exception {
    var level = helper.getLevel();
    var chunk = new net.minecraft.world.level.ChunkPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
    int y = level.getMaxBuildHeight() - 12;
    var wheat = new net.minecraft.core.BlockPos(chunk.getMinBlockX() + 7, y + 1, chunk.getMinBlockZ() + 7);
    var mystical = wheat.east(2).south(0);
    var ma = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mysticalagriculture:inferium_crop"));
    Deque<AutoCloseable> cleanup = new ArrayDeque<>();
    try {
      for (var pos : List.of(wheat, mystical)) {
        level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState()
            .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
        cleanup.push(() -> {
          level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
          level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        });
      }
      level.setBlock(wheat, net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState(), 2);
      level.setBlock(mystical, ma.defaultBlockState(), 2);
      var player = new ModPingpongRound5FullpackGameTests.Session(helper, "R7Squatter");
      cleanup.push(player);
      Vec3 stand = Vec3.atBottomCenterOf(wheat.east().south());
      player.player.teleportTo(stand.x, stand.y, stand.z);
      var action = Class.forName("dev.wuffs.squatgrow.SquatAction").getMethod("performAction", Player.class);
      for (int i = 0; i < 400; i++) action.invoke(null, player.player);
      int wheatAge = level.getBlockState(wheat).getValue(net.minecraft.world.level.block.CropBlock.AGE);
      var maState = level.getBlockState(mystical);
      int maAge = maState.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop ? crop.getAge(maState) : -1;
      LOGGER.info("ENTRELUMEN_ROUND7 squat wheatAge={} mysticalAge={}", wheatAge, maAge);
      helper.assertTrue(wheatAge > 0, "Squatting next to wheat did not advance it (age " + wheatAge + ")");
      helper.assertTrue(maAge == 0, "Squatting advanced a Mystical Agriculture crop (age " + maAge + ")");
    } finally {
      Exception first = null;
      while (!cleanup.isEmpty()) {
        try {
          cleanup.pop().close();
        } catch (Exception failure) {
          if (first == null) first = failure;
        }
      }
      if (first != null) throw first;
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void carryOnRefusesRound7Blocks(GameTestHelper helper) throws ReflectiveOperationException {
    var permitted = Class.forName("tschipp.carryon.common.config.ListHandler").getMethod("isPermitted", Block.class);
    List<String> carried = new ArrayList<>(), absent = new ArrayList<>();
    for (String name : CARRY_ON_REFUSED) {
      var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(name));
      if (block.isEmpty()) absent.add(name);
      else if ((boolean) permitted.invoke(null, block.get())) carried.add(name);
    }
    helper.assertTrue(absent.isEmpty(), "Round 7 blacklist test blocks not registered: " + absent);
    helper.assertTrue(carried.isEmpty(), "Carry On may pick up: " + carried);
    helper.succeed();
  }

  /**
   * Hardcore Revival cancels a player's death at high priority and knocks them out. Two mock players are
   * online, so the server is not "lonely": a lethal hit on one leaves them alive and knocked out, and the other
   * rescues them through the mod's own rescue path. Both players leave however the case ends.
   */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void hardcoreRevivalDownsAndRescuesInCoop(GameTestHelper helper) throws Exception {
    Deque<AutoCloseable> cleanup = new ArrayDeque<>();
    try {
      var victim = new ModPingpongRound5FullpackGameTests.Session(helper, "R7Downed");
      cleanup.push(victim);
      var rescuer = new ModPingpongRound5FullpackGameTests.Session(helper, "R7Rescuer");
      cleanup.push(rescuer);
      Vec3 spot = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
      victim.player.teleportTo(spot.x, spot.y, spot.z);
      rescuer.player.teleportTo(spot.x + 1, spot.y, spot.z);
      var revival = Class.forName(REVIVAL);
      var knockedOut = revival.getMethod("isKnockedOut", Player.class);
      victim.player.hurt(helper.getLevel().damageSources().generic(), 1000f);
      boolean downed = (boolean) knockedOut.invoke(null, victim.player);
      LOGGER.info("ENTRELUMEN_ROUND7 revival downed={} alive={} health={}", downed, victim.player.isAlive(),
          victim.player.getHealth());
      helper.assertTrue(downed && victim.player.isAlive() && victim.player.getHealth() > 0,
          "A lethal hit with a teammate online did not knock the player out");
      var manager = Class.forName(REVIVAL_MANAGER);
      manager.getMethod("startRescue", Player.class, Player.class).invoke(null, rescuer.player, victim.player);
      manager.getMethod("finishRescue", Player.class).invoke(null, rescuer.player);
      helper.assertFalse((boolean) knockedOut.invoke(null, victim.player), "The rescue did not wake the player up");
      helper.assertTrue(victim.player.isAlive(), "The rescued player is not alive");
    } finally {
      Exception first = null;
      while (!cleanup.isEmpty()) {
        try {
          cleanup.pop().close();
        } catch (Exception failure) {
          if (first == null) first = failure;
        }
      }
      if (first != null) throw first;
    }
    helper.succeed();
  }
}
