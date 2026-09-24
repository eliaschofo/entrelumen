package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for Terra's Arm on a server without Curios: the item, its workshop chest and the
 * reach modifier logic. Excluded from the distributable jar by the {@code RuntimeGameTests*} pattern.
 * Wearing it in a real Curios slot is covered by the full-pack {@code TerraArmFullpackGameTests}.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsTerraArm {
  private RuntimeGameTestsTerraArm() {}

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
        connection.disconnect(Component.literal("Entrelumen Terra's Arm QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static void requireNoCurios(GameTestHelper helper) {
    helper.assertTrue(!CuriosCompat.loaded() && !CuriosCompat.available(),
        "This isolated suite expects a server without Curios");
  }

  /**
   * One player tick with the given age. A GameTest player has no ticking connection, so the test calls
   * {@code doTick} itself; the age is what {@link TerraArm#INTERVAL_TICKS} counts, advanced by the level.
   */
  private static void tickAt(ServerPlayer player, int age) {
    player.tickCount = age;
    player.doTick();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void terraArmIsASingleEpicFireproofArtifactWithoutRecipe(GameTestHelper helper) {
    requireNoCurios(helper);
    var level = helper.getLevel();
    var key = ResourceLocation.fromNamespaceAndPath("entrelumen", "terra_arm");
    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key) && BuiltInRegistries.ITEM.get(key) == TerraArm.ITEM.get(),
        "entrelumen:terra_arm is not registered");
    var stack = new ItemStack(TerraArm.ITEM.get());
    helper.assertTrue(stack.getMaxStackSize() == 1 && stack.getRarity() == Rarity.EPIC
        && stack.has(DataComponents.FIRE_RESISTANT) && !stack.isDamageableItem() && stack.getMaxDamage() == 0,
        "The arm is not a stack of one, epic, fireproof and without durability");
    helper.assertTrue(stack.getAttributeModifiers().modifiers().isEmpty(),
        "The arm gives attributes when held or worn in a vanilla slot");
    helper.assertTrue(stack.is(TerraArm.CURIOS_HANDS), "The arm is missing from curios:hands");
    for (var recipe : level.getRecipeManager().getRecipes()) {
      ItemStack result;
      try {
        result = recipe.value().getResultItem(level.registryAccess());
      } catch (RuntimeException special) {
        continue;
      }
      helper.assertTrue(!result.is(TerraArm.ITEM.get()), "A recipe makes the arm: " + recipe.id());
    }
    var lines = stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL);
    long lore = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
        && text.getKey().equals("entrelumen.terra_arm.tooltip")).count();
    helper.assertTrue(lore == 1 && lines.size() == 2, "The tooltip is not the name and one line of lore: " + lines);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void terraArmWorkshopChestAlwaysHoldsTheArm(GameTestHelper helper) {
    var level = helper.getLevel();
    helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(TerraArm.WORKSHOP_LOOT) != LootTable.EMPTY,
        "entrelumen:chests/ruin_act2_workshop did not load");
    var pos = new BlockPos(2, 1, 2);
    for (long seed = 0; seed < 8; seed++) {
      helper.setBlock(pos, Blocks.AIR);
      helper.setBlock(pos, Blocks.CHEST);
      if (!(level.getBlockEntity(helper.absolutePos(pos)) instanceof ChestBlockEntity chest)) {
        helper.fail("No chest to fill");
        return;
      }
      chest.setLootTable(TerraArm.WORKSHOP_LOOT, seed);
      chest.unpackLootTable(null);
      int arms = 0;
      int other = 0;
      for (int slot = 0; slot < chest.getContainerSize(); slot++) {
        var stack = chest.getItem(slot);
        if (stack.is(TerraArm.ITEM.get())) arms += stack.getCount();
        else if (!stack.isEmpty()) other += stack.getCount();
      }
      helper.assertTrue(arms == 1 && other == 0,
          "Workshop chest with seed " + seed + " held " + arms + " arms and " + other + " other items");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void terraArmWithoutCuriosIsInertAndItsReachIsExact(GameTestHelper helper) {
    requireNoCurios(helper);
    try (var session = new Session(helper, "TerraArmQA", new BlockPos(2, 1, 2))) {
      var player = session.player;
      var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
      var entityReach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
      helper.assertTrue(reach != null && entityReach != null, "The player lacks interaction ranges");
      double base = reach.getValue();
      double entityBase = entityReach.getValue();
      // Eight blocks from the eyes: out of vanilla survival reach, within reach plus five.
      BlockPos far = BlockPos.containing(player.getEyePosition()).east(8);
      helper.assertTrue(!player.canInteractWithBlock(far, 0.0), "The test block is already within reach");

      // Carried, held and forced into vanilla equipment slots: nothing happens and nothing breaks.
      player.getInventory().add(new ItemStack(TerraArm.ITEM.get()));
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TerraArm.ITEM.get()));
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(TerraArm.ITEM.get()));
      player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TerraArm.ITEM.get()));
      for (int age = 0; age <= TerraArm.INTERVAL_TICKS; age++) tickAt(player, age);
      helper.assertTrue(!TerraArm.worn(player) && !CuriosCompat.equipped(player, TerraArm.ITEM.get())
          && !reach.hasModifier(TerraArm.REACH) && reach.getValue() == base && entityReach.getValue() == entityBase,
          "The arm changed reach outside a Curios slot");

      // The worn state adds exactly one additive +5 to block reach, once, and leaves other modifiers alone.
      var foreign = new AttributeModifier(ResourceLocation.fromNamespaceAndPath("entrelumen", "test_foreign_reach"),
          1.0, AttributeModifier.Operation.ADD_VALUE);
      reach.addTransientModifier(foreign);
      helper.assertTrue(TerraArm.sync(player, true) && !TerraArm.sync(player, true), "Wearing did not apply exactly once");
      var applied = reach.getModifier(TerraArm.REACH);
      helper.assertTrue(applied != null && applied.id().equals(ResourceLocation.parse("entrelumen:terra_arm_reach"))
          && applied.amount() == 5.0 && applied.operation() == AttributeModifier.Operation.ADD_VALUE,
          "Unexpected reach modifier: " + applied);
      helper.assertTrue(reach.getValue() == base + 1.0 + 5.0 && player.blockInteractionRange() == base + 6.0
          && entityReach.getValue() == entityBase && reach.hasModifier(foreign.id()),
          "Reach is not base + 5 on blocks only: " + reach.getValue());
      reach.removeModifier(foreign.id());
      helper.assertTrue(player.canInteractWithBlock(far, 0.0), "Reach + 5 does not reach eight blocks");

      // The server's own check runs every INTERVAL_TICKS; without Curios nothing is ever worn, so the
      // check takes the bonus back off.
      tickAt(player, TerraArm.INTERVAL_TICKS + 1);
      helper.assertTrue(reach.hasModifier(TerraArm.REACH), "The check ran between intervals");
      tickAt(player, TerraArm.INTERVAL_TICKS * 2);
      helper.assertTrue(!reach.hasModifier(TerraArm.REACH) && reach.getValue() == base
          && !player.canInteractWithBlock(far, 0.0), "The periodic check left the bonus without a worn arm");
      helper.assertTrue(!TerraArm.sync(player, false), "Removing an absent bonus reported a change");
    }
    helper.succeed();
  }
}
