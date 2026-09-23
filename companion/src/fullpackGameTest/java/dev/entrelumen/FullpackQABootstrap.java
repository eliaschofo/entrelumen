package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.slf4j.Logger;

/** QA-JAR-only bridge for installed servers, where NeoForge disables GameTests. */
@EventBusSubscriber(modid = "entrelumen")
public final class FullpackQABootstrap {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static boolean registered;
  private static Results results;

  private FullpackQABootstrap() {}

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent event) {
    if (!Boolean.getBoolean("entrelumen.qa"))
      throw new IllegalStateException("The Entrelumen QA JAR requires -Dentrelumen.qa=true; do not distribute it");
    FullpackGameTests.requireNativeInputs();
    ResourceFarmGameTests.requireNativeInputs();
    if (GameTestHooks.isGametestEnabled()) return;
    if (!registered) {
      GameTestRegistry.register(RuntimeGameTests.class);
      GameTestRegistry.register(FullpackGameTests.class);
      GameTestRegistry.register(ResourceFarmGameTests.class);
      GameTestRegistry.register(Ae2CraftingGameTests.class);
      GameTestRegistry.register(RFToolsRecipeGameTests.class);
      GameTestRegistry.register(CookingProvisionsGameTests.class);
      GameTestRegistry.register(CookingWorldgenGameTests.class);
      GameTestRegistry.register(TeamLifecycleGameTests.class);
      GameTestRegistry.register(ArsCreateGameTests.class);
      GameTestRegistry.register(ArsOccultismGameTests.class);
      GameTestRegistry.register(ArsAe2GameTests.class);
      GameTestRegistry.register(SettlementArchitectureGameTests.class);
      GameTestRegistry.register(BuilderUtilitiesGameTests.class);
      GameTestRegistry.register(MechanicalChiselGameTests.class);
      GameTestRegistry.register(TeamRestartGameTests.class);
      GameTestRegistry.register(BackupRestoreGameTests.class);
      Set<String> expected = new TreeSet<>();
      for (Class<?> owner : List.of(RuntimeGameTests.class, FullpackGameTests.class,
          ResourceFarmGameTests.class, Ae2CraftingGameTests.class, RFToolsRecipeGameTests.class,
          CookingProvisionsGameTests.class, CookingWorldgenGameTests.class, TeamLifecycleGameTests.class,
          ArsCreateGameTests.class, ArsOccultismGameTests.class, ArsAe2GameTests.class,
          SettlementArchitectureGameTests.class, BuilderUtilitiesGameTests.class,
          MechanicalChiselGameTests.class, TeamRestartGameTests.class, BackupRestoreGameTests.class))
        for (var method : owner.getDeclaredMethods())
          if (method.isAnnotationPresent(GameTest.class))
            expected.add(method.getName().toLowerCase(Locale.ROOT));
      if (expected.isEmpty())
        throw new IllegalStateException("Full-pack QA discovered no annotated tests");
      Set<String> registeredNames = new HashSet<>();
      GameTestRegistry.getAllTestFunctions().forEach(test -> registeredNames.add(test.testName()));
      if (!registeredNames.containsAll(expected))
        throw new IllegalStateException("Full-pack QA registration omitted "
            + new TreeSet<>(expected.stream().filter(name -> !registeredNames.contains(name)).toList()));
      results = new Results(Set.copyOf(expected));
      GlobalTestReporter.replaceWith(results);
      LOGGER.info("ENTRELUMEN_QA_READY expected={} names={}", expected.size(), expected);
      registered = true;
    }
    TestCommand.register(event.getDispatcher());
    event.getDispatcher().register(Commands.literal("entrelumen_qa_results")
        .requires(source -> source.hasPermission(2))
        .executes(ctx -> {
          var report = results.summary();
          if (results.complete()) {
            ctx.getSource().sendSuccess(() -> Component.literal(report), true);
            return 1;
          }
          ctx.getSource().sendFailure(Component.literal(report));
          return 0;
        }));
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (Boolean.getBoolean("entrelumen.qa")
        && !GameTestHooks.isGametestEnabled()
        && event.getServer().tickRateManager().runsNormally())
      GameTestTicker.SINGLETON.tick();
  }

  private static final class Results implements TestReporter {
    private final TestReporter delegate = new LogTestReporter();
    private final Set<String> expected;
    private final Map<String, Boolean> finished = new HashMap<>();
    private boolean terminalLogged;

    private Results(Set<String> expected) {
      this.expected = expected;
    }

    @Override
    public void onTestFailed(GameTestInfo info) {
      delegate.onTestFailed(info);
      record(info, false);
    }

    @Override
    public void onTestSuccess(GameTestInfo info) {
      delegate.onTestSuccess(info);
      record(info, true);
    }

    private void record(GameTestInfo info, boolean success) {
      String name = info.getTestName();
      if (!expected.contains(name)) return;
      finished.put(name, success);
      LOGGER.info("ENTRELUMEN_QA_RESULT {} {}", success ? "PASS" : "FAIL", name);
      if (finished.size() == expected.size() && !terminalLogged) {
        terminalLogged = true;
        LOGGER.info("ENTRELUMEN_QA_TERMINAL {}", summary());
      }
    }

    private boolean complete() {
      return finished.size() == expected.size() && finished.values().stream().allMatch(Boolean::booleanValue);
    }

    private String summary() {
      Set<String> failed = new TreeSet<>();
      Set<String> pending = new TreeSet<>(expected);
      finished.forEach((name, success) -> {
        pending.remove(name);
        if (!success) failed.add(name);
      });
      return "ENTRELUMEN_QA_SUMMARY expected=" + expected.size() + " finished=" + finished.size()
          + " failed=" + failed.size() + " passed=" + (finished.size() - failed.size())
          + " failedNames=" + failed + " pendingNames=" + pending;
    }

    @Override
    public void finish() {
      LOGGER.info("ENTRELUMEN_QA_FINISH {}", summary());
      delegate.finish();
    }
  }
}
