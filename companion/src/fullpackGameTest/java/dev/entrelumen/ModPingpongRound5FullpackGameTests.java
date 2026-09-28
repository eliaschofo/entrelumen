package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
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
 * reports this pack and version, and NaNny cancels damage that is not a number.
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
}
