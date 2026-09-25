package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for the vein resonators and the calibration frame's tooltip, on a server without
 * Curios or FTB Ultimine. Excluded from the distributable jar by the {@code RuntimeGameTests*} pattern.
 * Wearing a resonator and Ultimine's real limit are covered by {@code VeinResonatorFullpackGameTests}.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsVeinResonator {
  private RuntimeGameTestsVeinResonator() {}

  private static ServerPlayer join(GameTestHelper helper, String name, Connection[] keep,
      io.netty.channel.embedded.EmbeddedChannel[] channel) {
    var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
        cookie.clientInformation());
    keep[0] = new Connection(PacketFlow.SERVERBOUND);
    channel[0] = new io.netty.channel.embedded.EmbeddedChannel(keep[0]);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(keep[0]);
    player.server.getPlayerList().placeNewPlayer(keep[0], player, cookie);
    player.getInventory().clearContent();
    player.setGameMode(GameType.SURVIVAL);
    var pos = helper.absolutePos(new BlockPos(2, 1, 2));
    player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    return player;
  }

  private static long lines(java.util.List<Component> lines, String key) {
    return lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
        && text.getKey().equals(key)).count();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void sixResonatorTiersShowTheirUltimineReach(GameTestHelper helper) {
    helper.assertTrue(!CuriosCompat.loaded() && !UltimineCompat.loaded(),
        "This isolated suite expects a server without Curios and FTB Ultimine");
    var level = helper.getLevel();
    for (int tier = 1; tier <= VeinResonator.TIERS; tier++) {
      var key = ResourceLocation.fromNamespaceAndPath("entrelumen", "vein_resonator_" + tier);
      helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key) && BuiltInRegistries.ITEM.get(key) == VeinResonator.item(tier),
          key + " is not registered");
      var stack = new ItemStack(VeinResonator.item(tier));
      var rarity = tier <= 2 ? Rarity.COMMON : tier <= 4 ? Rarity.UNCOMMON : tier == 5 ? Rarity.RARE : Rarity.EPIC;
      helper.assertTrue(stack.getMaxStackSize() == 1 && stack.getRarity() == rarity && !stack.isDamageableItem()
          && stack.getAttributeModifiers().modifiers().isEmpty() && stack.is(VeinResonator.CURIOS_CHARM),
          "Tier " + tier + " is not a single, attribute-free charm of rarity " + rarity);
      var lines = stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL);
      int reach = 16 * tier;
      long effect = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
          && text.getKey().equals("entrelumen.vein_resonator.reach") && text.getArgs().length == 1
          && String.valueOf(reach).equals(String.valueOf(text.getArgs()[0]))
          && TextColor.fromLegacyFormat(ChatFormatting.BLUE).equals(line.getStyle().getColor())).count();
      helper.assertTrue(lines(lines, "entrelumen.vein_resonator.tooltip") == 1 && effect == 1 && lines.size() == 3,
          "Tier " + tier + " tooltip is not the name, the lore and 'Ultimine: up to " + reach + " blocks': " + lines);
    }
    // The first recipes live in the pack (KubeJS data); the companion ships none.
    for (var recipe : level.getRecipeManager().getRecipes()) {
      ItemStack result;
      try {
        result = recipe.value().getResultItem(level.registryAccess());
      } catch (RuntimeException special) {
        continue;
      }
      helper.assertTrue(!(result.getItem() instanceof VeinResonator.Resonator),
          "The companion itself makes a resonator: " + recipe.id());
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void resonatorsWithoutCuriosOrUltimineDoNothing(GameTestHelper helper) {
    Connection[] connection = new Connection[1];
    io.netty.channel.embedded.EmbeddedChannel[] channel = new io.netty.channel.embedded.EmbeddedChannel[1];
    var player = join(helper, "ResonatorQA", connection, channel);
    try {
      player.getInventory().add(new ItemStack(VeinResonator.item(6)));
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VeinResonator.item(3)));
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(VeinResonator.item(1)));
      player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(VeinResonator.item(5)));
      for (int age = 0; age <= VeinResonator.INTERVAL_TICKS * 2; age++) {
        player.tickCount = age;
        player.doTick();
      }
      helper.assertTrue(VeinResonator.wornTier(player) == 0 && UltimineCompat.maxBlocksAttribute() == null
          && UltimineCompat.configuredMaxBlocks() == 0 && UltimineCompat.effectiveMaxBlocks(player) == -1,
          "A resonator counted as worn, or Ultimine answered, on a server without them");
      helper.assertTrue(!VeinResonator.sync(player, 6, 0) && !VeinResonator.sync(player, 0, 64),
          "The resonator changed a player without FTB Ultimine's attribute");
    } finally {
      connection[0].disconnect(Component.literal("Entrelumen resonator QA finished"));
      connection[0].handleDisconnection();
      channel[0].finishAndReleaseAll();
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void calibrationFrameSaysTheInfuserCopiesIt(GameTestHelper helper) {
    var level = helper.getLevel();
    var frame = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("entrelumen", IntegrationItems.FRAME));
    helper.assertTrue(frame instanceof IntegrationItems.Frame, "The calibration frame is not the frame item");
    var lines = new ItemStack(frame).getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL);
    helper.assertTrue(lines(lines, "entrelumen.calibration_frame.tooltip") == 1 && lines.size() == 2,
        "The frame's tooltip is not its name and one line on the infuser: " + lines);
    for (var recipe : level.getRecipeManager().getRecipes()) {
      ItemStack result;
      try {
        result = recipe.value().getResultItem(level.registryAccess());
      } catch (RuntimeException special) {
        continue;
      }
      helper.assertTrue(!result.is(frame), "A companion recipe makes the frame: " + recipe.id());
    }
    helper.succeed();
  }
}
