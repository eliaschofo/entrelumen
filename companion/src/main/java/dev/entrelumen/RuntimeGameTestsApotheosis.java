package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for the Apotheosis integration; excluded from the distributable jar by the
 * {@code RuntimeGameTests*} pattern. Apotheosis itself is absent here: the fixture ships stand-in
 * Haven/Frontier/Ascent advancements and the library runs on the vanilla shelf rule.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsApotheosis {
  private static ServerPlayer player(GameTestHelper helper, String name) {
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
        new GameProfile(UUID.randomUUID(), name), false);
    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
        cookie.gameProfile(), cookie.clientInformation());
    var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    player.getInventory().clearContent();
    return player;
  }

  private static boolean done(ServerPlayer player, ApotheosisTiers.Tier tier) {
    var holder = player.server.getAdvancements().get(tier.advancement);
    return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void worldTiersFollowTheTeamCampaignIdempotently(GameTestHelper helper)
      throws Exception {
    var founder = player(helper, "TierFounder");
    var guest = player(helper, "TierGuest");
    var campaigns = CampaignData.get(founder.server).campaigns;
    helper.assertTrue(helper.getLevel().getServer().getAdvancements()
            .get(ApotheosisTiers.Tier.SUMMIT.advancement) == null,
        "Fixture unexpectedly ships Summit; the missing-advancement case is not exercised");
    // A player without a stored campaign only reaches Haven and no campaign is created by reading.
    boolean hadCampaign = campaigns.personal.containsKey(guest.getUUID());
    ApotheosisTiers.sync(guest);
    helper.assertTrue(done(guest, ApotheosisTiers.Tier.HAVEN) && !done(guest, ApotheosisTiers.Tier.FRONTIER)
        && campaigns.personal.containsKey(guest.getUUID()) == hadCampaign,
        "Haven was not granted alone, or reading created a campaign");
    var founderCampaign = Entrelumen.current(founder);
    founderCampaign.act = 2;
    ApotheosisTiers.sync(founder);
    helper.assertTrue(done(founder, ApotheosisTiers.Tier.HAVEN) && !done(founder, ApotheosisTiers.Tier.FRONTIER),
        "Frontier opened before Act II was complete");
    founderCampaign.act = 3;
    helper.assertTrue(ApotheosisTiers.sync(founder) == 1 && done(founder, ApotheosisTiers.Tier.FRONTIER)
        && !done(founder, ApotheosisTiers.Tier.ASCENT), "Completing Act II did not open exactly Frontier");
    helper.assertTrue(ApotheosisTiers.sync(founder) == 0, "Replay granted criteria again");
    founderCampaign.act = 6;
    founderCampaign.completed.add(CampaignMilestones.LAST_HORIZON);
    helper.assertTrue(ApotheosisTiers.sync(founder) == 1 && done(founder, ApotheosisTiers.Tier.ASCENT),
        "Later tiers failed or a missing advancement aborted the grant");
    // A member who joins later receives the team's tiers; leaving never revokes them.
    var team = (dev.ftb.mods.ftbteams.data.PartyTeam) FTBTeamsAPI.api().getManager()
        .createPartyTeam(founder, "Tiers " + founder.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    team.invite(founder, List.of(guest.getGameProfile()));
    team.join(guest);
    ApotheosisTiers.sync(guest);
    helper.assertTrue(done(guest, ApotheosisTiers.Tier.FRONTIER) && done(guest, ApotheosisTiers.Tier.ASCENT),
        "Joining member did not receive the team's tiers");
    team.leave(guest.getUUID());
    helper.assertTrue(ApotheosisTiers.sync(guest) == 0 && done(guest, ApotheosisTiers.Tier.ASCENT),
        "Leaving the team revoked or regranted a tier");
    team.leave(founder.getUUID());
    helper.succeed();
  }

  private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
    return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
  }

  private static ItemStack book(Holder<Enchantment> enchantment, int level) {
    var book = new ItemStack(Items.ENCHANTED_BOOK);
    var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    stored.set(enchantment, level);
    book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
    return book;
  }

  private static int level(ItemStack book, Holder<Enchantment> enchantment) {
    return EnchantmentHelper.getEnchantmentsForCrafting(book).getLevel(enchantment);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasLibraryPoolsBooksCapsByShelvesAndSurvivesBreakingWithoutDuplication(
      GameTestHelper helper) {
    var level = helper.getLevel();
    var player = player(helper, "LibraryQA");
    var pos = helper.absolutePos(new BlockPos(3, 2, 3));
    for (BlockPos clear : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 2, 3)))
      level.setBlockAndUpdate(clear, clear.getY() == pos.getY() - 1 ? Blocks.STONE.defaultBlockState()
          : Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos, ApotheosisContent.ATLAS_LIBRARY.get().defaultBlockState());
    var library = (AtlasLibraryBlockEntity) level.getBlockEntity(pos);
    var sharpness = enchantment(helper, Enchantments.SHARPNESS);
    var curse = enchantment(helper, Enchantments.VANISHING_CURSE);

    // Automation may only insert books; curse-only books stay where they are.
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    helper.assertTrue(handler != null && handler.insertItem(0, book(sharpness, 5), false).isEmpty()
        && library.pool() == 8, "Sharpness V did not add exactly 8 lumen through the item handler");
    var cursed = book(curse, 1);
    helper.assertTrue(handler.insertItem(0, cursed, false) == cursed && library.pool() == 8
        && handler.extractItem(0, 1, false).isEmpty(), "Curse accepted or extraction allowed");

    // Without shelves the cap is level 1; the server rejects anything higher.
    helper.assertTrue(AtlasLibraryEterna.around(level, pos) == 0
        && library.withdraw(sharpness, ItemStack.EMPTY, 2) == null && library.pool() == 8,
        "Withdrawal above the Eterna cap was accepted");
    for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS)
      if (offset.getY() == 0) level.setBlockAndUpdate(pos.offset(offset), Blocks.BOOKSHELF.defaultBlockState());
    float eterna = AtlasLibraryEterna.around(level, pos);
    helper.assertTrue(eterna == AtlasLibraryEterna.VANILLA_MAX, "Sixteen bookshelves did not give 30 Eterna: " + eterna);

    // Menu route: deposit through the slot, withdraw through the vanilla button path.
    var menu = new AtlasLibraryMenu(1, player.getInventory(), pos);
    menu.slots.get(AtlasLibraryMenu.DEPOSIT).set(book(sharpness, 5));
    helper.assertTrue(menu.slots.get(AtlasLibraryMenu.DEPOSIT).getItem().isEmpty() && library.pool() == 16,
        "Slot deposit did not convert the book once");
    int id = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getId(sharpness.value());
    helper.assertTrue(!menu.clickMenuButton(player, AtlasLibraryMenu.encode(id, 13)) && library.pool() == 16,
        "Server accepted a level above the 30-Eterna cap of 12");
    helper.assertTrue(menu.clickMenuButton(player, AtlasLibraryMenu.encode(id, 5))
        && level(menu.output(), sharpness) == 5 && library.pool() == 0, "Sharpness V was not bought for 16");
    helper.assertTrue(!menu.clickMenuButton(player, AtlasLibraryMenu.encode(id, 6)) && library.pool() == 0
        && level(menu.output(), sharpness) == 5, "Empty pool still paid for an upgrade");
    var snapshot = menu.snapshot(library);
    var offer = snapshot.offers().stream().filter(o -> o.enchantment() == id).findFirst().orElseThrow();
    helper.assertTrue(offer.cap() == 12 && offer.current() == 5 && offer.next() == 6 && offer.nextCost() == 16
        && snapshot.offers().stream().noneMatch(o -> o.enchantment()
            == level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getId(curse.value())),
        "Snapshot prices, cap or curse exclusion were wrong");
    // Depositing the bought book returns half of what it cost.
    handler.insertItem(0, menu.output().copy(), false);
    helper.assertTrue(library.pool() == 8, "Round trip did not lose half");

    // Saturation: huge books never overflow or turn negative.
    handler.insertItem(0, book(sharpness, 127), false);
    handler.insertItem(0, book(sharpness, 127), false);
    helper.assertTrue(library.pool() == Long.MAX_VALUE, "Pool did not saturate");
    helper.assertTrue(library.withdraw(sharpness, ItemStack.EMPTY, 12) != null
        && library.pool() == Long.MAX_VALUE - 2048, "Saturated pool did not pay exactly");
    long stored = library.pool();

    // Breaking keeps the pool on exactly one drop; placing it restores the same pool.
    menu.removed(player);
    level.destroyBlock(pos, true, player);
    var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2),
        entity -> entity.getItem().is(ApotheosisContent.ATLAS_LIBRARY.get().asItem()));
    helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1
        && drops.getFirst().getItem().has(DataComponents.BLOCK_ENTITY_DATA), "Library drop lost or duplicated the pool");
    var carried = drops.getFirst().getItem().copy();
    drops.getFirst().discard();
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    player.teleportTo(pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5);
    player.setItemInHand(InteractionHand.MAIN_HAND, carried);
    var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
    player.gameMode.useItemOn(player, level, carried, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(level.getBlockEntity(pos) instanceof AtlasLibraryBlockEntity restored
        && restored.pool() == stored && player.getMainHandItem().isEmpty(), "Placing the drop did not restore the pool once");
    var restored = (AtlasLibraryBlockEntity) level.getBlockEntity(pos);
    helper.assertTrue(restored.withdraw(sharpness, ItemStack.EMPTY, 3) != null && restored.pool() == stored - 4,
        "Restored library could not pay a withdrawal");
    helper.succeed();
  }
}
