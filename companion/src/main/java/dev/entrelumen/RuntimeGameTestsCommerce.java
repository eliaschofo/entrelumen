package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests of Solsticio's commerce; excluded from the distributable jar by the
 * RuntimeGameTests* pattern. They use their own fixture template ({@code commerce_fixture}, built by
 * {@code tools/build_commerce_fixture.py}) and local site registries, never the city's; the last
 * one checks the shared city's trading hall.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsCommerce {
  private RuntimeGameTestsCommerce() {}

  /** A real server player on an in-memory connection, with its own FTB team and campaign. */
  private static final class QaPlayer implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    QaPlayer(GameTestHelper helper, String name) {
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
        player.setGameMode(GameType.SURVIVAL);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    public void close() {
      try {
        connection.disconnect(Component.literal("Commerce QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static void campaign(ServerPlayer player, int act, boolean arkActivated) {
    var campaign = Entrelumen.current(player);
    campaign.act = act;
    if (arkActivated) campaign.completed.add(CampaignMilestones.LAST_HORIZON);
    else campaign.completed.remove(CampaignMilestones.LAST_HORIZON);
    CampaignData.get(player.server).setDirty();
  }

  private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
    return helper.absolutePos(new BlockPos(x, y, z));
  }

  private static void floor(GameTestHelper helper) {
    for (int x = 0; x <= 4; x++)
      for (int z = 0; z <= 4; z++) helper.getLevel().setBlockAndUpdate(at(helper, x, 0, z), Blocks.STONE.defaultBlockState());
  }

  /** Opens the trading screen through the real interaction path (Solsticio hooks included). */
  private static void trade(GameTestHelper helper, ServerPlayer player, Villager villager) {
    player.interactOn(villager, InteractionHand.MAIN_HAND);
    helper.assertTrue(player.containerMenu instanceof MerchantMenu && villager.getTradingPlayer() == player,
        "The trading screen did not open");
  }

  private static MerchantOffer offer(Villager villager, Item result) {
    for (MerchantOffer offer : villager.getOffers()) if (offer.getResult().is(result)) return offer;
    throw new AssertionError(villager.getName().getString() + " does not sell " + result);
  }

  private static String key(Component component) {
    return component != null && component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
  }

  /** The commerce markers of the fixture, read and cleared exactly as the city loader does. */
  private record Fixture(CommerceSites sites, List<String> unknown, BlockPos hall) {}

  private static Fixture fixture(GameTestHelper helper) {
    var level = helper.getLevel();
    CompoundTag tag;
    try (InputStream stream = level.getServer().getResourceManager()
        .getResource(ResourceLocation.fromNamespaceAndPath("entrelumen", "structure/commerce_fixture.nbt")).orElseThrow()
        .open()) {
      tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
    } catch (IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
    var partition = SolsticioCity.partition(tag, CityLayout.CUBE, 0, 0, 0);
    List<CommerceSites.Found> found = new ArrayList<>();
    List<String> unknown = new ArrayList<>();
    BlockPos hall = null;
    for (int i = 0; i < partition.markerNames().size(); i++) {
      int[] local = partition.markerPositions().get(i);
      // The test's structure block sits one below the template: layer 0 is relative y = 1.
      BlockPos world = at(helper, local[0], local[1] + 1, local[2]);
      helper.assertTrue(level.getBlockState(world).is(Blocks.STRUCTURE_BLOCK), "The fixture marker is not at " + world);
      level.setBlockAndUpdate(world, Blocks.AIR.defaultBlockState());
      String name = partition.markerNames().get(i);
      var parsed = CityLayout.parseMarker(name);
      if (parsed.isEmpty()) {
        unknown.add(name);
        continue;
      }
      switch (parsed.get().marker()) {
        case TRADING_HALL -> hall = world;
        case SHOP, SIDEQUEST, RESIDENT, EASTER -> found.add(new CommerceSites.Found(parsed.get().marker(),
            parsed.get().argument(), world));
        default -> {}
      }
    }
    CommerceSites sites = new CommerceSites();
    sites.rebuild(found, hall);
    return new Fixture(sites, unknown, hall);
  }

  // ---- markers and spawning -------------------------------------------------------------

  @GameTest(template = "commerce_fixture", timeoutTicks = 100)
  public static void commerceSpawnsItsVillagersAtTheTemplateMarkers(GameTestHelper helper) {
    var level = helper.getLevel();
    var fixture = fixture(helper);
    List<Villager> spawned = SolsticioCommerce.populateNow(level, fixture.sites(), 2);
    try (var qa = new QaPlayer(helper, "MarketQA")) {
      helper.assertTrue(fixture.unknown().equals(List.of("bogus:marker")), "Unknown markers: " + fixture.unknown());
      helper.assertTrue(spawned.size() == 3 + 1 + 2 + 6, "Spawned " + spawned.size() + " villagers");
      helper.assertTrue(SolsticioCommerce.populateNow(level, fixture.sites(), 2).isEmpty(),
          "A second population spawned again");
      helper.assertTrue(fixture.sites().pending(2).isEmpty(), "Sites still pending after the population");
      Map<CommerceRules.Role, List<Villager>> byRole = new EnumMap<>(CommerceRules.Role.class);
      for (Villager villager : spawned)
        byRole.computeIfAbsent(SolsticioCommerce.role(villager), role -> new ArrayList<>()).add(villager);

      Villager books = null, maps = null;
      for (Villager keeper : byRole.get(CommerceRules.Role.SHOP)) {
        helper.assertTrue(keeper.isNoAi() && keeper.isInvulnerable() && keeper.isPersistenceRequired(),
            "A shopkeeper can move, die or despawn");
        helper.assertTrue(key(keeper.getCustomName()).startsWith("entrelumen.solsticio.shop."), "Unnamed shopkeeper");
        helper.assertTrue(keeper.getVillagerData().getLevel() == CommerceRules.MERCHANT_LEVEL && !keeper.getOffers().isEmpty(),
            "A shopkeeper has no offers");
        helper.assertTrue(!keeper.hurt(level.damageSources().playerAttack(qa.player), 5.0F)
            && keeper.getHealth() == keeper.getMaxHealth(), "A player hurt a shopkeeper");
        String type = SolsticioCommerce.data(keeper).getString("key");
        if (type.equals("bookstore")) books = keeper;
        if (type.equals("maps")) maps = keeper;
      }
      helper.assertTrue(books != null && books.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
          "No librarian at the bookstore");
      boolean mending = false;
      for (MerchantOffer offer : books.getOffers()) {
        var stored = offer.getResult().get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && stored.keySet().stream().anyMatch(holder -> holder.is(Enchantments.MENDING))) mending = true;
      }
      helper.assertTrue(mending, "The bookstore sells no Mending");
      helper.assertTrue(maps != null && maps.getOffers().stream().anyMatch(offer -> CommerceOffers.isSurvey(offer.getResult())),
          "The cartographer sells no survey map");

      List<Villager> natives = byRole.get(CommerceRules.Role.NATIVE);
      helper.assertTrue(natives.size() == 6, "Natives: " + natives.size());
      var spots = new HashSet<BlockPos>();
      for (Villager nativeVillager : natives) {
        spots.add(nativeVillager.blockPosition());
        helper.assertTrue(nativeVillager.isNoAi() && nativeVillager.blockPosition().distManhattan(fixture.hall()) <= 8,
            "A native wanders or stands far from the hall");
        helper.assertTrue(nativeVillager.getOffers().stream().anyMatch(offer -> offer.getResult().is(Luminous.LUMINOSITIES_TAG)),
            "A native has no Luminosity offer");
      }
      helper.assertTrue(spots.size() == 6, "Natives share a spot");

      Villager inn = byRole.get(CommerceRules.Role.SIDEQUEST).getFirst();
      helper.assertTrue(inn.isNoAi() && key(inn.getCustomName()).equals("entrelumen.solsticio.innkeeper.0"), "Innkeeper");
      Component line = SolsticioCommerce.sideQuest(qa.player, inn, SolsticioCommerce.data(inn));
      helper.assertTrue(key(line).equals("entrelumen.solsticio.says")
          && key((Component) ((TranslatableContents) line.getContents()).getArgs()[1]).equals("entrelumen.solsticio.innkeeper.0.line"),
          "The innkeeper did not say its line");
      AtomicBoolean hooked = new AtomicBoolean();
      SolsticioCommerce.registerSideQuest("7_inn", (player, npc, id) -> {
        hooked.set(true);
        return true;
      });
      try {
        helper.assertTrue(SolsticioCommerce.sideQuest(qa.player, inn, SolsticioCommerce.data(inn)) == null && hooked.get(),
            "The side quest hook did not take the click");
      } finally {
        SolsticioCommerce.unregisterSideQuest("7_inn");
      }

      List<Villager> townsfolk = byRole.get(CommerceRules.Role.TOWNSFOLK);
      helper.assertTrue(townsfolk.size() == 2, "The cap of 2 common villagers was not kept: " + townsfolk.size());
      for (Villager local : townsfolk)
        helper.assertTrue(!local.isNoAi() && local.hasRestriction()
            && local.getVillagerData().getProfession() == VillagerProfession.NITWIT, "A common villager is not a strolling local");
      helper.assertTrue(List.of(at(helper, 6, 2, 11)).equals(fixture.sites().easterEggs.get("fountain_coin")),
          "The easter egg was not recorded");
    } finally {
      spawned.forEach(Entity::discard);
    }
    helper.succeed();
  }

  // ---- prices ---------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void shopPricesFallWhenLiberatedAndLaterActsStayClosed(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = SolsticioData.get(level.getServer());
    boolean wasLiberated = data.liberated;
    floor(helper);
    BlockPos post = at(helper, 2, 1, 2);
    var sites = new CommerceSites();
    var site = new CommerceSites.Site(CommerceRules.Role.SHOP, "rarities", post);
    sites.sites.add(site);
    Villager keeper = SolsticioCommerce.spawn(level, sites, site, SolsticioCommerce.LOCAL);
    try (var qa = new QaPlayer(helper, "PriceQA")) {
      helper.assertTrue(keeper != null, "No shopkeeper");
      var player = qa.player;
      player.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 2.5);
      data.liberated = false;
      MerchantOffer elytra = offer(keeper, Items.ELYTRA), echo = offer(keeper, Items.ECHO_SHARD);

      campaign(player, 1, false);
      trade(helper, player, keeper);
      helper.assertTrue(elytra.isOutOfStock() && echo.isOutOfStock(), "Act 1 may buy elytra or echo shards");
      player.closeContainer();
      helper.assertTrue(!elytra.isOutOfStock() && elytra.getUses() == 0, "Closing did not restore the locked offers");

      campaign(player, 6, true);
      trade(helper, player, keeper);
      int normal = elytra.getCostA().getCount();
      helper.assertTrue(!elytra.isOutOfStock() && normal == 32 && elytra.getCostA().is(Items.EMERALD_BLOCK),
          "The last act does not see the elytra at 32 emerald blocks: " + elytra.getCostA());
      player.closeContainer();

      data.liberated = true;
      trade(helper, player, keeper);
      int liberated = elytra.getCostA().getCount();
      player.closeContainer();
      helper.assertTrue(liberated == CommerceRules.discounted(32, SolsticioCommerce.prices().liberated()) && liberated < normal,
          "Liberation did not lower the elytra: " + normal + " -> " + liberated);
      helper.assertTrue(elytra.getCostA().getCount() == normal, "The discount outlived the trade");
    } finally {
      data.liberated = wasLiberated;
      if (keeper != null) keeper.discard();
    }
    helper.succeed();
  }

  private static Villager plainVillager(ServerLevel level, BlockPos pos, int price) {
    Villager villager = EntityType.VILLAGER.create(level);
    villager.moveTo(Vec3.atBottomCenterOf(pos));
    villager.setNoAi(true);
    villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 2));
    MerchantOffers offers = new MerchantOffers();
    offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(Items.BOOKSHELF), 12, 1, 0.05F));
    villager.setOffers(offers);
    level.addFreshEntity(villager);
    return villager;
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void aVillagerFromElsewhereSettlesInTheHallWithADiscount(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = SolsticioData.get(level.getServer());
    boolean wasLiberated = data.liberated;
    floor(helper);
    BlockPos hall = at(helper, 1, 1, 1);
    Villager moved = plainVillager(level, at(helper, 1, 1, 2), 20);
    Villager stranger = plainVillager(level, at(helper, 4, 1, 4), 20);
    try (var qa = new QaPlayer(helper, "SettleQA")) {
      data.liberated = false;
      var settled = SolsticioCommerce.scanHall(level, hall, 1);
      helper.assertTrue(settled.equals(List.of(moved)), "Settled " + settled.size() + " villagers");
      helper.assertTrue(SolsticioCommerce.role(moved) == CommerceRules.Role.MOVED && moved.getTags().contains(SolsticioCommerce.TAG),
          "The villager in the hall did not settle");
      helper.assertTrue(SolsticioCommerce.role(stranger) == null, "A villager outside the hall settled");
      helper.assertTrue(SolsticioCommerce.scanHall(level, hall, 1).isEmpty(), "A settled villager settled twice");
      var player = qa.player;
      player.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
      trade(helper, player, moved);
      int settledPrice = moved.getOffers().getFirst().getCostA().getCount();
      player.closeContainer();
      trade(helper, player, stranger);
      int strangerPrice = stranger.getOffers().getFirst().getCostA().getCount();
      player.closeContainer();
      helper.assertTrue(strangerPrice == 20 && settledPrice == CommerceRules.discounted(20, SolsticioCommerce.prices().moved()),
          "Settled " + settledPrice + " versus " + strangerPrice + " emeralds");
      helper.assertTrue(moved.getOffers().getFirst().getCostA().getCount() == 20, "The discount outlived the trade");
    } finally {
      data.liberated = wasLiberated;
      moved.discard();
      stranger.discard();
    }
    helper.succeed();
  }

  // ---- natives --------------------------------------------------------------------------

  private static void biome(GameTestHelper helper, net.minecraft.resources.ResourceKey<Biome> biome) {
    var level = helper.getLevel();
    Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
    // BiomeManager blurs lookups by up to one 4-block cell: cover a margin around the test.
    var result = FillBiomeCommand.fill(level, at(helper, -4, -4, -4), at(helper, 8, 8, 8), holder);
    helper.assertTrue(result.left().isPresent(), "Could not fill the biome");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void aNativeSellsItsLuminosityOnceItStandsInItsBiome(GameTestHelper helper) {
    var level = helper.getLevel();
    var data = SolsticioData.get(level.getServer());
    boolean wasLiberated = data.liberated;
    floor(helper);
    biome(helper, Biomes.PLAINS);
    BlockPos hall = at(helper, 2, 1, 2);
    var sites = new CommerceSites();
    var site = new CommerceSites.Site(CommerceRules.Role.NATIVE, LuminousRules.Discipline.ARCANE.id, hall);
    sites.sites.add(site);
    Villager arcane = SolsticioCommerce.spawn(level, sites, site, SolsticioCommerce.LOCAL);
    try (var qa = new QaPlayer(helper, "NativeQA")) {
      helper.assertTrue(arcane != null && !SolsticioCommerce.data(arcane).getBoolean("awakened"), "The native woke in the plains");
      data.liberated = false;
      var player = qa.player;
      player.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
      Item luminosity = Luminous.LUMINOSITIES.get(LuminousRules.Discipline.ARCANE).get();
      MerchantOffer sale = offer(arcane, luminosity), bottles = offer(arcane, Items.EXPERIENCE_BOTTLE);

      trade(helper, player, arcane);
      helper.assertTrue(sale.isOutOfStock() && bottles.getCostA().getCount() == 20, "A sleeping native sold its Luminosity");
      player.closeContainer();
      helper.assertTrue(!sale.isOutOfStock(), "The locked Luminosity stayed out of stock");

      biome(helper, Biomes.SWAMP);
      trade(helper, player, arcane);
      int price = sale.getCostA().getCount();
      boolean inStock = !sale.isOutOfStock();
      int bottlePrice = bottles.getCostA().getCount();
      player.closeContainer();
      double home = SolsticioCommerce.prices().nativeHome();
      helper.assertTrue(SolsticioCommerce.data(arcane).getBoolean("awakened"), "The native did not wake in the swamp");
      helper.assertTrue(inStock && sale.getCostA().is(Items.EMERALD_BLOCK) && price == CommerceRules.discounted(16, home)
          && sale.getCostB().is(Items.AMETHYST_SHARD), "The Luminosity is not on sale at its home price: " + price);
      helper.assertTrue(bottlePrice == CommerceRules.discounted(20, home), "The native's other goods are not discounted");

      // Awake for good: back in the plains it still sells.
      biome(helper, Biomes.PLAINS);
      trade(helper, player, arcane);
      helper.assertTrue(!sale.isOutOfStock(), "The native fell asleep again");
      player.closeContainer();
    } finally {
      data.liberated = wasLiberated;
      if (arcane != null) arcane.discard();
    }
    helper.succeed();
  }

  // ---- the city's own trading hall ------------------------------------------------------

  /**
   * In the shared city: the six natives get spawned by the background population, and a villager
   * brought straight into the trading hall settles as it arrives.
   */
  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void theCityHallHasItsNativesAndSettlesNewcomers(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    helper.assertTrue(server.getLevel(Solsticio.LEVEL) != null, "The Solsticio dimension is missing");
    AtomicBoolean done = new AtomicBoolean();
    helper.onEachTick(() -> {
      if (done.get() || !SolsticioCity.ensure(server) || SolsticioCommerce.isPopulating(server)) return;
      done.set(true);
      var data = SolsticioData.get(server);
      ServerLevel solsticio = server.getLevel(Solsticio.LEVEL);
      BlockPos hall = data.commerce.hall;
      helper.assertTrue(hall != null, "The city has no trading hall");
      long natives = data.commerce.sites.stream()
          .filter(site -> site.role == CommerceRules.Role.NATIVE && site.entity != null).count();
      helper.assertTrue(natives == 6, "The city's hall has " + natives + " natives");
      Villager newcomer = EntityType.VILLAGER.create(solsticio);
      newcomer.setNoAi(true);
      newcomer.moveTo(Vec3.atBottomCenterOf(hall));
      solsticio.addFreshEntity(newcomer);
      try {
        helper.assertTrue(SolsticioCommerce.role(newcomer) == CommerceRules.Role.MOVED, "The newcomer did not settle");
      } finally {
        newcomer.discard();
      }
      helper.succeed();
    });
  }
}
