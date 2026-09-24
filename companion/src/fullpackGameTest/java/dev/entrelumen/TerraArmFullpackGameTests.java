package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for Terra's Arm with the real Curios: the arm is accepted in the player's
 * {@code hands} slot, wearing it gives exactly +5 block reach, two arms give the same +5, taking one
 * off keeps the other's bonus, and taking both off removes it. Curios is reached only by reflection.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class TerraArmFullpackGameTests {
  private TerraArmFullpackGameTests() {}

  static void requireSuite() {
    if (!ModList.get().isLoaded(CuriosCompat.MOD_ID))
      throw new IllegalStateException("Required real mod is absent: " + CuriosCompat.MOD_ID);
  }

  /** Curios API calls the test needs besides {@link CuriosCompat}. */
  private record Curios(MethodHandle inventory, MethodHandle itemSlots, MethodHandle entitySlots,
      MethodHandle setEquipped, MethodHandle stacksHandler, MethodHandle slots) {
    static Curios resolve() throws Throwable {
      var lookup = MethodHandles.publicLookup();
      Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
      Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
      Class<?> stacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
      return new Curios(
          lookup.findStatic(api, "getCuriosInventory", MethodType.methodType(Optional.class, LivingEntity.class)),
          lookup.findStatic(api, "getItemStackSlots", MethodType.methodType(Map.class, ItemStack.class, LivingEntity.class)),
          lookup.findStatic(api, "getEntitySlots", MethodType.methodType(Map.class, LivingEntity.class)),
          lookup.findVirtual(handler, "setEquippedCurio",
              MethodType.methodType(void.class, String.class, int.class, ItemStack.class)),
          lookup.findVirtual(handler, "getStacksHandler", MethodType.methodType(Optional.class, String.class)),
          lookup.findVirtual(stacks, "getSlots", MethodType.methodType(int.class)));
    }

    Object handler(LivingEntity entity) throws Throwable {
      return ((Optional<?>) inventory.invoke(entity)).orElseThrow();
    }

    void equip(LivingEntity entity, int index, ItemStack stack) throws Throwable {
      setEquipped.invoke(handler(entity), "hands", index, stack);
    }

    int handsSlots(LivingEntity entity) throws Throwable {
      Optional<?> hands = (Optional<?>) stacksHandler.invoke(handler(entity), "hands");
      return hands.isEmpty() ? 0 : (int) slots.invoke(hands.get());
    }
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Session(GameTestHelper helper, String name, BlockPos relative) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
      var pos = helper.absolutePos(relative);
      player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Entrelumen Terra's Arm full-pack QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** The server's periodic check, run now: a GameTest player has no ticking connection. */
  private static void check(ServerPlayer player) {
    player.tickCount = TerraArm.INTERVAL_TICKS * 100;
    player.doTick();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void terraArmWornInCuriosHandsGivesFiveBlockReach(GameTestHelper helper) {
    Curios curios;
    try {
      curios = Curios.resolve();
    } catch (Throwable error) {
      helper.fail("Curios API unavailable: " + error);
      return;
    }
    helper.assertTrue(CuriosCompat.available(), "The companion cannot reach the Curios API");
    var session = new Session(helper, "TerraArmCurios", new BlockPos(2, 1, 2));
    var player = session.player;
    var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
    var entityReach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
    double base = reach.getValue();
    double entityBase = entityReach.getValue();
    int[] hands = {0};
    helper.startSequence()
        .thenExecute(() -> {
          try {
            var stack = new ItemStack(TerraArm.ITEM.get());
            helper.assertTrue(((Map<?, ?>) curios.entitySlots.invoke(player)).containsKey("hands"),
                "Players have no Curios hands slot");
            helper.assertTrue(((Map<?, ?>) curios.itemSlots.invoke(stack, player)).containsKey("hands"),
                "Curios does not accept the arm on the hands");
            hands[0] = curios.handsSlots(player);
            helper.assertTrue(hands[0] >= 1, "The hands slot has no room");
            curios.equip(player, 0, stack);
          } catch (Throwable error) {
            helper.fail("Equipping through Curios failed: " + error);
          }
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          var modifier = reach.getModifier(TerraArm.REACH);
          helper.assertTrue(TerraArm.worn(player) && modifier != null && modifier.amount() == TerraArm.REACH_BONUS
              && reach.getValue() == base + TerraArm.REACH_BONUS && entityReach.getValue() == entityBase,
              "Wearing the arm did not give exactly +5 block reach: " + reach.getValue() + " from " + base);
          if (hands[0] < 2) return;
          try {
            curios.equip(player, 1, new ItemStack(TerraArm.ITEM.get()));
          } catch (Throwable error) {
            helper.fail("Equipping a second arm failed: " + error);
          }
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          helper.assertTrue(reach.getValue() == base + TerraArm.REACH_BONUS, "Two arms do not give a single +5");
          if (hands[0] < 2) return;
          try {
            curios.equip(player, 0, ItemStack.EMPTY);
          } catch (Throwable error) {
            helper.fail("Taking off the first arm failed: " + error);
          }
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          helper.assertTrue(reach.getValue() == base + TerraArm.REACH_BONUS,
              "Taking off one arm removed the other's bonus");
          try {
            for (int i = 0; i < Math.min(hands[0], 2); i++) curios.equip(player, i, ItemStack.EMPTY);
          } catch (Throwable error) {
            helper.fail("Taking off the arms failed: " + error);
          }
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          helper.assertTrue(!TerraArm.worn(player) && !reach.hasModifier(TerraArm.REACH) && reach.getValue() == base,
              "The bonus outlived the arm");
          session.close();
        })
        .thenSucceed();
  }
}
