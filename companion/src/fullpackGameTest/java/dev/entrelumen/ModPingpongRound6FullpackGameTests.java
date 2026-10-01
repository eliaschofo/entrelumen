package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Mod ping-pong round 6 (docs/design/mod-pingpong.md, «Ronda 6»): the content mods of the outward search. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ModPingpongRound6FullpackGameTests {
  /** Round 6 mods with a server side. */
  static final List<String> ROUND6 = List.of("createrailgrinding", "create_integrated_farming", "croptopiabotany",
      "alshanex_familiars", "familiarslib", "ars_affinity", "ironsspellsnftbteams");
  /** Client-only: the server install leaves it out (catalog clientOnly). Empty since Psionic Utilities was dropped (1 Oct). */
  static final List<String> ROUND6_CLIENT = List.of();
  /** Petrol's Parts stays out: every Petrolpark's Library it accepts requires a JEI newer than the locked one. */
  static final List<String> ROUND6_OUT = List.of("petrolsparts", "petrolpark");
  static final String SUMMON = "io.redspace.ironsspellbooks.entity.mobs.SummonedPolarBear";
  static final String MAGIC_SUMMON = "io.redspace.ironsspellbooks.entity.mobs.IMagicSummon";

  private static final Logger LOGGER = LogUtils.getLogger();

  private ModPingpongRound6FullpackGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongRound6Loaded(GameTestHelper helper) {
    List<String> problems = new ArrayList<>();
    ROUND6.stream().filter(mod -> !ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " not loaded"));
    if (FMLEnvironment.dist.isDedicatedServer())
      ROUND6_CLIENT.stream().filter(mod -> ModList.get().isLoaded(mod))
          .forEach(mod -> problems.add(mod + " is client-only but loaded on the dedicated server"));
    ROUND6_OUT.stream().filter(mod -> ModList.get().isLoaded(mod)).forEach(mod -> problems.add(mod + " is loaded"));
    helper.assertTrue(problems.isEmpty(), "Round 6: " + problems);
    helper.succeed();
  }

  /**
   * Irons Spell N FTB Teams 1.0.0 is one beta mixin at the head of IMagicSummon.isAlliedHelper. Two mock players
   * form a real FTB party and a third stays alone; a polar bear summoned for the owner must count the teammate
   * as an ally and not the stranger. The bear, the party and the players are removed however the case ends.
   */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void ironsSummonsSpareFtbTeammates(GameTestHelper helper) throws Exception {
    var manager = FTBTeamsAPI.api().getManager();
    // Cleanup runs in reverse order of registration: the bear, the teammate leaving, the owner leaving (which
    // ends the party), then the three sessions.
    Deque<AutoCloseable> cleanup = new ArrayDeque<>();
    try {
      var owner = new ModPingpongRound5FullpackGameTests.Session(helper, "R6SummonOwner");
      cleanup.push(owner);
      var mate = new ModPingpongRound5FullpackGameTests.Session(helper, "R6SummonMate");
      cleanup.push(mate);
      var stranger = new ModPingpongRound5FullpackGameTests.Session(helper, "R6Stranger");
      cleanup.push(stranger);
      PartyTeam party = (PartyTeam) manager.createPartyTeam(owner.player, "Entrelumen QA " + owner.player.getUUID(),
          "", Color4I.WHITE);
      cleanup.push(() -> leave(owner.player));
      helper.assertTrue(party.invite(owner.player, List.of(mate.player.getGameProfile())) == 1
          && party.join(mate.player) == 1, "FTB invitation or join failed");
      cleanup.push(() -> leave(mate.player));
      helper.assertTrue(manager.arePlayersInSameTeam(owner.player.getUUID(), mate.player.getUUID())
          && !manager.arePlayersInSameTeam(owner.player.getUUID(), stranger.player.getUUID()), "Unexpected FTB teams");
      Entity bear = (Entity) Class.forName(SUMMON).getConstructor(Level.class, LivingEntity.class)
          .newInstance(helper.getLevel(), owner.player);
      bear.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
      helper.assertTrue(helper.getLevel().addFreshEntity(bear), "The summon was not added");
      cleanup.push(bear::discard);
      var allied = Class.forName(MAGIC_SUMMON).getMethod("isAlliedHelper", Entity.class);
      boolean mateAllied = (boolean) allied.invoke(bear, mate.player);
      boolean strangerAllied = (boolean) allied.invoke(bear, stranger.player);
      LOGGER.info("ENTRELUMEN_ROUND6 summon mateAllied={} strangerAllied={}", mateAllied, strangerAllied);
      helper.assertTrue(mateAllied, "An Iron's summon does not spare its owner's FTB teammate");
      helper.assertFalse(strangerAllied, "An Iron's summon spares a player outside its owner's FTB team");
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

  private static void leave(ServerPlayer player) throws Exception {
    var manager = FTBTeamsAPI.api().getManager();
    if (manager.getTeamForPlayer(player).map(team -> !team.isPlayerTeam()).orElse(false))
      player.server.getCommands().getDispatcher().execute("ftbteams party leave",
          player.createCommandSourceStack().withSuppressedOutput());
  }
}
