package dev.entrelumen;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Act VI at runtime ({@code docs/design/act-six.md}): the four named characters, the innkeepers'
 * errands, the easter eggs, the liberation and the elections. Rules and state names are in
 * {@link SolsticioStoryRules}; every mission is a campaign milestone, which the
 * {@code entrelumen:campaign} quest task mirrors in the quest book (read-only: nothing in the book
 * can complete a mission).
 *
 * <p>Also registers the Heliodor clothing ({@code entrelumen:heliodor} villager type) that every
 * villager the city spawns wears, and one profession per named character whose texture is the hook
 * for its distinctive piece (placeholders until the controller draws them).
 */
public final class SolsticioStory {
  private static final Logger LOGGER = LogUtils.getLogger();

  static final DeferredRegister<VillagerType> TYPES = DeferredRegister.create(Registries.VILLAGER_TYPE, "entrelumen");
  static final DeferredRegister<VillagerProfession> PROFESSIONS =
      DeferredRegister.create(Registries.VILLAGER_PROFESSION, "entrelumen");
  /** Cream and copper robes with a teal sash: {@code textures/entity/villager/type/heliodor.png}. */
  public static final DeferredHolder<VillagerType, VillagerType> HELIODOR =
      TYPES.register("heliodor", () -> new VillagerType("entrelumen:heliodor"));
  /**
   * One profession per named character, without job site, so its texture ({@code
   * textures/entity/villager/profession/<character>.png}) carries the character's distinctive piece.
   */
  public static final Map<String, DeferredHolder<VillagerProfession, VillagerProfession>> CHARACTER_PROFESSIONS;

  static {
    Map<String, DeferredHolder<VillagerProfession, VillagerProfession>> professions = new LinkedHashMap<>();
    for (String character : SolsticioStoryRules.CHARACTERS)
      professions.put(character, PROFESSIONS.register(character, () -> new VillagerProfession("entrelumen:" + character,
          PoiType.NONE, PoiType.NONE, ImmutableSet.of(), ImmutableSet.of(), null)));
    CHARACTER_PROFESSIONS = java.util.Collections.unmodifiableMap(professions);
  }

  public static final TagKey<Item> SEEDS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "solsticio/seeds"));
  public static final TagKey<Item> MEALS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "solsticio/meals"));
  /** Create: New Age parts for Terra: four generator coils and two advanced solar heating plates. */
  static final Map<String, Integer> POWER_PARTS = map("create_new_age:generator_coil", 4,
      "create_new_age:advanced_solar_heating_plate", 2);
  static final Map<String, Integer> LANTERN_PARTS = map("minecraft:lantern", 1, "minecraft:waxed_cut_copper", 4);
  static final int HONEY = 4, CANDLES = 4, FLOWERS = 4;

  /** Custom data of an errand's own item (songbook, map, shears). */
  static final String ERRAND = "entrelumen_errand";
  /** Scoreboard tag and persistent key of a team's lost cat. */
  static final String CAT_TAG = "entrelumen.errand_cat", CAT_CAMPAIGN = "entrelumen_errand_cat";
  static final int EGG_RADIUS = 5, EGG_HEIGHT = 3, GARDEN_RADIUS = 7, CAT_RADIUS = 6, CAT_HEIGHT = 16,
      CHAPEL_RADIUS = 14, CHAPEL_HEIGHT = 8;

  private SolsticioStory() {}

  private static Map<String, Integer> map(String a, int countA, String b, int countB) {
    Map<String, Integer> map = new LinkedHashMap<>();
    map.put(a, countA);
    map.put(b, countB);
    return java.util.Collections.unmodifiableMap(map);
  }

  static void register(IEventBus bus) {
    TYPES.register(bus);
    PROFESSIONS.register(bus);
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SolsticioStory::onRightClickBlock);
    NeoForge.EVENT_BUS.addListener(SolsticioStory::onRightClickItem);
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SolsticioStory::onEntityInteract);
    NeoForge.EVENT_BUS.addListener(SolsticioStory::onFished);
    NeoForge.EVENT_BUS.addListener(SolsticioStory::onJoin);
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
        hookInnkeepers(SolsticioData.get(event.getServer()).commerce.sideQuestIds()));
    NeoForge.EVENT_BUS.addListener(SolsticioStory::commands);
  }

  /** The innkeepers of these side-quest ids offer the errands ({@link SolsticioCommerce#registerSideQuest}). */
  public static void hookInnkeepers(Collection<String> ids) {
    for (String id : ids) SolsticioCommerce.registerSideQuest(id, SolsticioStory::innkeeper);
  }

  // ---- what an interaction said ----------------------------------------------------------

  /** The lines an interaction sent, as translation keys, so GameTests can follow the dialogue. */
  public static final class Talk {
    private final ServerPlayer player;
    private final List<String> keys = new ArrayList<>();

    Talk(ServerPlayer player) {
      this.player = player;
    }

    void say(Component speaker, String key, Object... args) {
      keys.add(key);
      player.sendSystemMessage(SolsticioCommerce.says(speaker, Component.translatable(key, args)));
    }

    void narrate(String key, Object... args) {
      keys.add(key);
      player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    public List<String> keys() {
      return List.copyOf(keys);
    }

    public boolean said(String suffix) {
      return keys.stream().anyMatch(key -> key.endsWith(suffix));
    }
  }

  // ---- campaign access -------------------------------------------------------------------

  /** The player's campaign, or null for fake players and players without a team. */
  static Campaigns.Campaign campaign(ServerPlayer player) {
    if (player instanceof FakePlayer) return null;
    try {
      return Entrelumen.current(player);
    } catch (RuntimeException noTeam) {
      return null;
    }
  }

  static UUID campaignId(ServerPlayer player) {
    try {
      return CampaignActions.campaignId(player);
    } catch (RuntimeException noTeam) {
      return null;
    }
  }

  static void saved(ServerPlayer player) {
    CampaignData.get(player.server).setDirty();
  }

  static SolsticioStoryRules.World world(MinecraftServer server) {
    SolsticioData data = SolsticioData.get(server);
    return new SolsticioStoryRules.World(data.liberated, data.electionsHeld);
  }

  static Component character(String key) {
    return Component.translatable("entrelumen.solsticio.character." + key);
  }

  // ---- inventory -------------------------------------------------------------------------

  static String id(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
  }

  static int count(ServerPlayer player, Predicate<ItemStack> test) {
    int count = 0;
    for (ItemStack stack : Entrelumen.deliveryStacks(player)) if (!stack.isEmpty() && test.test(stack)) count += stack.getCount();
    return count;
  }

  /** Per-item totals of the matching stacks, in inventory order. */
  static Map<String, Integer> totals(ServerPlayer player, Predicate<ItemStack> test) {
    Map<String, Integer> totals = new LinkedHashMap<>();
    for (ItemStack stack : Entrelumen.deliveryStacks(player))
      if (!stack.isEmpty() && test.test(stack)) totals.merge(id(stack), stack.getCount(), Integer::sum);
    return totals;
  }

  /** Takes {@code amount} matching items from the inventory; the caller checked they are there. */
  static void take(ServerPlayer player, Predicate<ItemStack> test, int amount) {
    for (ItemStack stack : Entrelumen.deliveryStacks(player)) {
      if (amount <= 0) break;
      if (stack.isEmpty() || !test.test(stack)) continue;
      int taken = Math.min(amount, stack.getCount());
      stack.shrink(taken);
      amount -= taken;
    }
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
  }

  static Predicate<ItemStack> item(String id) {
    return stack -> id(stack).equals(id);
  }

  static boolean exists(String id) {
    ResourceLocation location = ResourceLocation.tryParse(id);
    return location != null && BuiltInRegistries.ITEM.containsKey(location);
  }

  /** "4 × Generator Coil, 2 × ..." for a missing-items line. */
  static Component list(Map<String, Integer> items) {
    MutableComponent line = Component.empty();
    boolean first = true;
    for (var entry : items.entrySet()) {
      if (!first) line.append(", ");
      first = false;
      Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
      line.append(Component.literal(entry.getValue() + " × ")).append(new ItemStack(item).getHoverName());
    }
    return line;
  }

  static ItemStack named(Item item, String key, boolean glint) {
    ItemStack stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key).withStyle(style -> style.withItalic(false)
        .withColor(ChatFormatting.GOLD)));
    stack.set(DataComponents.LORE, new ItemLore(List.of(
        Component.translatable(key + ".lore").withStyle(ChatFormatting.GRAY))));
    if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    return stack;
  }

  static ItemStack loreBook(String id, int pages) {
    ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
    CommerceOffers.loreBook(book, new CommerceRules.LoreBook(id, pages));
    return book;
  }

  // ---- errand items ----------------------------------------------------------------------

  /** The errand's own item: Tobías's songbook, the map for Anselmo or Amparo's shears. */
  public static ItemStack errandItem(SolsticioStoryRules.Errand errand) {
    Item item = switch (errand) {
      case LIBRARY_BOOK -> Items.BOOK;
      case CARTOGRAPHER_MAP -> Items.MAP;
      case RIVER_TOOL -> Items.SHEARS;
      default -> null;
    };
    if (item == null) return ItemStack.EMPTY;
    ItemStack stack = named(item, "entrelumen.solsticio.errand." + errand.id + ".item", false);
    CompoundTag data = new CompoundTag();
    data.putString(ERRAND, errand.id);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return stack;
  }

  static String errandOf(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? null : data.copyTag().contains(ERRAND, Tag.TAG_STRING) ? data.copyTag().getString(ERRAND) : null;
  }

  static Predicate<ItemStack> errandItemOf(SolsticioStoryRules.Errand errand) {
    return stack -> errand.id.equals(errandOf(stack));
  }

  // ---- the named characters ----------------------------------------------------------------

  /** A right-click on Aurelia, Terra, Juan or Bodhi. */
  public static Talk character(ServerPlayer player, Villager npc, String character) {
    Talk talk = new Talk(player);
    Campaigns.Campaign c = campaign(player);
    SolsticioStoryRules.World world = world(player.server);
    String stage = SolsticioStoryRules.stage(character, c, world, heartState(player, c));
    Component name = character(character);
    String base = "entrelumen.solsticio.character." + character + ".";
    switch (character) {
      case "mayor" -> mayor(player, c, stage, talk, name, base);
      case "inventor" -> inventor(player, c, stage, talk, name, base, world);
      case "gardener" -> gardener(player, c, stage, talk, name, base, world);
      case "priest" -> priest(player, c, stage, talk, name, base, world);
      default -> talk.say(name, base + "visitor");
    }
    return talk;
  }

  private static void mayor(ServerPlayer player, Campaigns.Campaign c, String stage, Talk talk, Component name,
      String base) {
    switch (stage) {
      case "welcome" -> {
        if (!SolsticioStoryRules.record(c, SolsticioStoryRules.MAYOR)) return;
        saved(player);
        talk.say(name, base + "welcome.1");
        talk.say(name, base + "welcome.2");
        talk.narrate("entrelumen.solsticio.story.compass");
      }
      case "table", "table_waiting" -> {
        if (SolsticioStoryRules.hear(c, "mayor")) accord(player, talk);
        else {
          saved(player);
          talk.say(name, base + stage);
        }
      }
      case "accord" -> {
        if (SolsticioStoryRules.hear(c, "mayor")) accord(player, talk);
      }
      case "campaign" -> {
        SolsticioData data = SolsticioData.get(player.server);
        talk.say(name, base + "campaign",
            SolsticioStoryRules.daysUntilElection(data.liberatedAt, player.server.overworld().getGameTime()));
      }
      case "elected" -> {
        if (!SolsticioStoryRules.record(c, SolsticioStoryRules.ELECTIONS)) return;
        saved(player);
        talk.say(name, base + "elected");
        talk.narrate("entrelumen.solsticio.story.ending");
      }
      default -> talk.say(name, base + stage);
    }
  }

  /** Both voices heard: the table of two lights ends in the accord (mission 9). */
  private static void accord(ServerPlayer player, Talk talk) {
    saved(player);
    talk.say(character("mayor"), "entrelumen.solsticio.character.mayor.accord");
    talk.say(character("priest"), "entrelumen.solsticio.character.priest.accord");
    talk.narrate("entrelumen.solsticio.story.accord");
  }

  private static void inventor(ServerPlayer player, Campaigns.Campaign c, String stage, Talk talk, Component name,
      String base, SolsticioStoryRules.World world) {
    switch (stage) {
      case "power" -> {
        Map<String, Integer> parts = new LinkedHashMap<>();
        POWER_PARTS.forEach((item, count) -> {
          if (exists(item)) parts.put(item, count);
        });
        Map<String, Integer> missing = SolsticioStoryRules.missing(parts, totals(player, stack -> parts.containsKey(id(stack))));
        ItemStack battery = battery(player);
        if (!missing.isEmpty() || battery == null) {
          talk.say(name, base + "power");
          if (!missing.isEmpty()) talk.narrate("entrelumen.solsticio.story.missing", list(missing));
          if (battery == null) talk.narrate("entrelumen.solsticio.story.battery", SolsticioStoryRules.BATTERY_CAPACITY);
          return;
        }
        parts.forEach((item, count) -> take(player, item(item), count));
        battery.shrink(1);
        SolsticioStoryRules.record(c, SolsticioStoryRules.POWER);
        saved(player);
        Solsticio.give(player, loreBook("terraprism", 3));
        talk.say(name, base + "power_done");
      }
      case "light" -> {
        Map<String, Integer> luminosities = new LinkedHashMap<>();
        for (var luminosity : Luminous.LUMINOSITIES.values())
          luminosities.put(BuiltInRegistries.ITEM.getKey(luminosity.get()).toString(), 1);
        Map<String, Integer> missing = SolsticioStoryRules.missing(luminosities,
            totals(player, stack -> stack.is(Luminous.LUMINOSITIES_TAG)));
        if (!missing.isEmpty()) {
          talk.say(name, base + "light");
          talk.narrate("entrelumen.solsticio.story.missing", list(missing));
          return;
        }
        luminosities.keySet().forEach(item -> take(player, item(item), 1));
        SolsticioStoryRules.record(c, SolsticioStoryRules.TERRAPRISM);
        saved(player);
        Solsticio.give(player, new ItemStack(Solsticio.RELICS.get(1).get()));
        talk.say(name, base + "light_done");
        talk.narrate("entrelumen.solsticio.story.relic", new ItemStack(Solsticio.RELICS.get(1).get()).getHoverName());
      }
      default -> talk.say(name, base + stage);
    }
  }

  /** The first charged battery the player carries (any mod's energy item), or null. */
  static ItemStack battery(ServerPlayer player) {
    for (ItemStack stack : Entrelumen.deliveryStacks(player)) {
      if (stack.isEmpty()) continue;
      var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
      if (energy != null && SolsticioStoryRules.charged(energy.getEnergyStored(), energy.getMaxEnergyStored()))
        return stack;
    }
    return null;
  }

  private static void gardener(ServerPlayer player, Campaigns.Campaign c, String stage, Talk talk, Component name,
      String base, SolsticioStoryRules.World world) {
    if (SolsticioStoryRules.done(c, SolsticioStoryRules.SEEDS)) basket(player, talk, name);
    switch (stage) {
      case "seeds" -> {
        List<String> kinds = new ArrayList<>();
        for (ItemStack stack : Entrelumen.deliveryStacks(player)) if (!stack.isEmpty() && stack.is(SEEDS)) kinds.add(id(stack));
        List<String> chosen = SolsticioStoryRules.distinct(kinds, SolsticioStoryRules.SEED_SPECIES);
        if (chosen.isEmpty()) {
          talk.say(name, base + "seeds");
          talk.narrate("entrelumen.solsticio.story.seeds", new java.util.HashSet<>(kinds).size(),
              SolsticioStoryRules.SEED_SPECIES);
          return;
        }
        chosen.forEach(item -> take(player, item(item), 1));
        SolsticioStoryRules.record(c, SolsticioStoryRules.SEEDS);
        saved(player);
        talk.say(name, base + "seeds_done");
        basket(player, talk, name);
      }
      case "harvest" -> {
        Map<String, Integer> meals = totals(player, stack -> stack.is(MEALS));
        List<String> chosen = SolsticioStoryRules.enough(meals, SolsticioStoryRules.MEAL_KINDS, SolsticioStoryRules.MEALS_EACH);
        if (chosen.isEmpty()) {
          talk.say(name, base + "harvest");
          long ready = meals.values().stream().filter(count -> count >= SolsticioStoryRules.MEALS_EACH).count();
          talk.narrate("entrelumen.solsticio.story.meals", ready, SolsticioStoryRules.MEAL_KINDS,
              SolsticioStoryRules.MEALS_EACH);
          return;
        }
        chosen.forEach(item -> take(player, item(item), SolsticioStoryRules.MEALS_EACH));
        SolsticioStoryRules.record(c, SolsticioStoryRules.HARVEST);
        saved(player);
        Solsticio.give(player, new ItemStack(Solsticio.RELICS.get(0).get()));
        talk.say(name, base + "harvest_done");
        talk.narrate("entrelumen.solsticio.story.relic", new ItemStack(Solsticio.RELICS.get(0).get()).getHoverName());
      }
      default -> talk.say(name, base + stage);
    }
  }

  static final List<Item> BASKET_FOOD = List.of(Items.BREAD, Items.BAKED_POTATO, Items.CARROT, Items.APPLE,
      Items.SWEET_BERRIES, Items.COOKIE, Items.BEETROOT);
  static final List<Item> BASKET_FLOWERS = List.of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.ALLIUM,
      Items.AZURE_BLUET, Items.OXEYE_DAISY, Items.LILY_OF_THE_VALLEY, Items.BLUE_ORCHID);

  /** Juan's restocked gardens (mission 3's reward): food and flowers, once per team per day. */
  static boolean basket(ServerPlayer player, Talk talk, Component name) {
    UUID id = campaignId(player);
    if (id == null) return false;
    SolsticioData data = SolsticioData.get(player.server);
    long day = player.server.overworld().getGameTime() / SolsticioStoryRules.DAY;
    Long last = data.baskets.get(id);
    if (last != null && last == day) return false;
    data.baskets.put(id, day);
    data.setDirty();
    var random = player.getRandom();
    Solsticio.give(player, new ItemStack(BASKET_FOOD.get(random.nextInt(BASKET_FOOD.size())), 6));
    Solsticio.give(player, new ItemStack(BASKET_FLOWERS.get(random.nextInt(BASKET_FLOWERS.size())), 2));
    talk.say(name, "entrelumen.solsticio.character.gardener.basket");
    return true;
  }

  static SolsticioStoryRules.HeartState heartState(ServerPlayer player, Campaigns.Campaign c) {
    if (c == null || !HeliodorHeartRules.recovered(c)) return SolsticioStoryRules.HeartState.WITH_SPIRIT;
    if (HeliodorHeartRules.inAtlas(c)) return SolsticioStoryRules.HeartState.IN_ATLAS;
    if (count(player, stack -> stack.is(HeliodorHeart.ITEM.get())) > 0) return SolsticioStoryRules.HeartState.CARRIED;
    return SolsticioStoryRules.HeartState.ELSEWHERE;
  }

  private static void priest(ServerPlayer player, Campaigns.Campaign c, String stage, Talk talk, Component name,
      String base, SolsticioStoryRules.World world) {
    switch (stage) {
      case "works" -> talk.say(name, base + "works", SolsticioStoryRules.relation(c),
          SolsticioStoryRules.RELATION_NEEDED);
      case "listening" -> {
        if (!SolsticioStoryRules.recordGoodWorks(c)) return;
        saved(player);
        talk.say(name, base + "listening");
      }
      case "release" -> {
        if (!HeliodorHeart.release(player)) return;
        talk.say(name, base + "release");
      }
      case "blessing" -> {
        take(player, stack -> stack.is(HeliodorHeart.ITEM.get()), 1);
        SolsticioStoryRules.record(c, SolsticioStoryRules.BLESSING);
        saved(player);
        Solsticio.give(player, new ItemStack(Solsticio.RELICS.get(2).get()));
        talk.say(name, base + "blessing");
        talk.narrate("entrelumen.solsticio.story.relic", new ItemStack(Solsticio.RELICS.get(2).get()).getHoverName());
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 24,
            0.5, 0.6, 0.5, 0.02);
      }
      case "table", "table_waiting" -> {
        if (SolsticioStoryRules.hear(c, "priest")) accord(player, talk);
        else {
          saved(player);
          talk.say(name, base + stage);
        }
      }
      case "accord" -> {
        if (SolsticioStoryRules.hear(c, "priest")) accord(player, talk);
      }
      default -> talk.say(name, base + stage);
    }
  }

  // ---- the innkeepers' errands ---------------------------------------------------------------

  /** The side-quest hook of every inn: offers, reminds and receives the innkeeper's errands. */
  static boolean innkeeper(ServerPlayer player, Villager npc, String id) {
    return innkeeperTalk(player, npc) != null;
  }

  /** The innkeeper's dialogue, or null when it says its ordinary line (visitors, fake players). */
  public static Talk innkeeperTalk(ServerPlayer player, Villager npc) {
    Campaigns.Campaign c = campaign(player);
    if (!SolsticioStoryRules.arrived(c)) return null;
    CompoundTag data = SolsticioCommerce.data(npc);
    int persona = data == null ? 0 : data.getInt("persona");
    Talk talk = new Talk(player);
    Component name = npc.getDisplayName();
    SolsticioStoryRules.Errand errand = SolsticioStoryRules.current(c, persona);
    if (errand == null) {
      talk.say(name, "entrelumen.solsticio.innkeeper." + persona + ".thanks");
      return talk;
    }
    String base = "entrelumen.solsticio.errand." + errand.id + ".";
    if (SolsticioStoryRules.state(c, errand) == SolsticioStoryRules.ErrandState.OPEN) {
      SolsticioStoryRules.take(c, errand);
      saved(player);
      talk.say(name, base + "offer");
      begin(player, errand, npc, talk);
      return talk;
    }
    if (atInnkeeper(player.server, errand) && ready(player, errand)) {
      consume(player, errand);
      finish(player, c, errand, talk, name);
      return talk;
    }
    begin(player, errand, npc, talk);
    if (errand == SolsticioStoryRules.Errand.LOST_CAT) {
      var cat = SolsticioData.get(player.server).cats.get(campaignId(player));
      talk.say(name, base + "reminder", direction(npc.blockPosition(), cat == null ? npc.blockPosition() : cat.pos()));
    } else talk.say(name, base + "reminder");
    return talk;
  }

  /** Hands out (again, if lost) what the errand starts with. */
  private static void begin(ServerPlayer player, SolsticioStoryRules.Errand errand, Villager innkeeper, Talk talk) {
    SolsticioData data = SolsticioData.get(player.server);
    switch (errand) {
      case LIBRARY_BOOK, RIVER_TOOL -> {
        // The shears come out of the water; only the songbook is handed over here.
        if (errand == SolsticioStoryRules.Errand.LIBRARY_BOOK && count(player, errandItemOf(errand)) == 0)
          Solsticio.give(player, errandItem(errand));
      }
      case CARTOGRAPHER_MAP -> {
        // Without a cartographer in this city the innkeeper hands the map over herself.
        if (!hasShop(data, "maps") && count(player, errandItemOf(errand)) == 0) Solsticio.give(player, errandItem(errand));
        elder(player.server);
      }
      case LOST_CAT -> ensureCat(player,
          innkeeper != null && innkeeper.level().dimension().equals(Solsticio.LEVEL) ? innkeeper.blockPosition() : null);
      default -> {}
    }
  }

  /** Whether the innkeeper receives this errand: its own ones, or any whose place this city lacks. */
  static boolean atInnkeeper(MinecraftServer server, SolsticioStoryRules.Errand errand) {
    SolsticioData data = SolsticioData.get(server);
    return switch (errand.target) {
      case INNKEEPER -> true;
      case SHOP -> !hasShop(data, errand.place);
      case CHAPEL -> !data.npcs.containsKey(errand.place);
      case GARDEN -> !data.commerce.easterEggs.containsKey(errand.place);
      case ELDER -> elder(server) == null;
      case CAT -> false;
    };
  }

  static boolean hasShop(SolsticioData data, String type) {
    return data.commerce.sites.stream().anyMatch(site -> site.role == CommerceRules.Role.SHOP && site.key.equals(type));
  }

  /** Whether the player carries what the errand delivers (the flame for the candles is checked apart). */
  static boolean ready(ServerPlayer player, SolsticioStoryRules.Errand errand) {
    return switch (errand) {
      case BAKERY_HONEY -> count(player, stack -> stack.is(Items.HONEY_BOTTLE)) >= HONEY;
      case LOST_CAT -> false;
      case LIBRARY_BOOK, CARTOGRAPHER_MAP, RIVER_TOOL -> count(player, errandItemOf(errand)) > 0;
      case CHAPEL_CANDLES -> count(player, stack -> stack.is(ItemTags.CANDLES)) >= CANDLES;
      case COPPER_LANTERN -> SolsticioStoryRules.missing(LANTERN_PARTS,
          totals(player, stack -> LANTERN_PARTS.containsKey(id(stack)))).isEmpty();
      case GARDEN_FLOWERS -> !flowers(player).isEmpty();
    };
  }

  private static List<String> flowers(ServerPlayer player) {
    List<String> kinds = new ArrayList<>();
    for (ItemStack stack : Entrelumen.deliveryStacks(player))
      if (!stack.isEmpty() && stack.is(ItemTags.SMALL_FLOWERS) && errandOf(stack) == null) kinds.add(id(stack));
    return SolsticioStoryRules.distinct(kinds, FLOWERS);
  }

  private static void consume(ServerPlayer player, SolsticioStoryRules.Errand errand) {
    switch (errand) {
      case BAKERY_HONEY -> take(player, stack -> stack.is(Items.HONEY_BOTTLE), HONEY);
      case LIBRARY_BOOK, CARTOGRAPHER_MAP, RIVER_TOOL -> take(player, errandItemOf(errand), 1);
      case CHAPEL_CANDLES -> take(player, stack -> stack.is(ItemTags.CANDLES), CANDLES);
      case COPPER_LANTERN -> LANTERN_PARTS.forEach((item, count) -> take(player, item(item), count));
      case GARDEN_FLOWERS -> flowers(player).forEach(item -> take(player, item(item), 1));
      case LOST_CAT -> {}
    }
  }

  /** A completed errand: +1 relation, Good Works at five. */
  static void finish(ServerPlayer player, Campaigns.Campaign c, SolsticioStoryRules.Errand errand, Talk talk,
      Component speaker) {
    boolean works = SolsticioStoryRules.done(c, SolsticioStoryRules.GOOD_WORKS);
    if (!SolsticioStoryRules.complete(c, errand)) return;
    saved(player);
    String done = "entrelumen.solsticio.errand." + errand.id + ".done";
    if (speaker == null) talk.narrate(done);
    else talk.say(speaker, done);
    talk.narrate("entrelumen.solsticio.story.relation", SolsticioStoryRules.relation(c),
        SolsticioStoryRules.RELATION_NEEDED);
    if (!works && SolsticioStoryRules.done(c, SolsticioStoryRules.GOOD_WORKS))
      talk.narrate("entrelumen.solsticio.story.good_works");
    player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
        10, 0.4, 0.5, 0.4, 0.0);
    if (errand == SolsticioStoryRules.Errand.LOST_CAT) removeCat(player.server, campaignId(player));
  }

  /** A shopkeeper receiving (or, for Rumbo, handing out) an errand item; true when it took the click. */
  public static boolean shopErrand(ServerPlayer player, Villager keeper, String shop) {
    return shopTalk(player, keeper, shop) != null;
  }

  public static Talk shopTalk(ServerPlayer player, Villager keeper, String shop) {
    Campaigns.Campaign c = campaign(player);
    if (!SolsticioStoryRules.arrived(c)) return null;
    for (SolsticioStoryRules.Errand errand : SolsticioStoryRules.Errand.values()) {
      if (!SolsticioStoryRules.taken(c, errand)) continue;
      if (errand.target == SolsticioStoryRules.Target.SHOP && errand.place.equals(shop) && ready(player, errand)) {
        Talk talk = new Talk(player);
        consume(player, errand);
        finish(player, c, errand, talk, keeper.getDisplayName());
        return talk;
      }
      if (errand == SolsticioStoryRules.Errand.CARTOGRAPHER_MAP && shop.equals(errand.place)
          && count(player, errandItemOf(errand)) == 0) {
        Talk talk = new Talk(player);
        Solsticio.give(player, errandItem(errand));
        talk.say(keeper.getDisplayName(), "entrelumen.solsticio.errand.cartographer_map.handed");
        BlockPos elder = elder(player.server);
        if (elder != null) talk.narrate("entrelumen.solsticio.errand.cartographer_map.where",
            direction(keeper.blockPosition(), elder));
        return talk;
      }
    }
    return null;
  }

  // ---- Anselmo, the old neighbour -----------------------------------------------------------

  /**
   * The home of Anselmo, the old neighbour of the map errand: the spawned common villager nearest to
   * the cartographer, chosen once and kept; null when the city has no common villagers.
   */
  static BlockPos elder(MinecraftServer server) {
    SolsticioData data = SolsticioData.get(server);
    if (data.elder != null || !data.ready()) return data.elder;
    BlockPos anchor = data.commerce.sites.stream()
        .filter(site -> site.role == CommerceRules.Role.SHOP && site.key.equals("maps")).map(site -> site.pos)
        .findFirst().orElse(data.arrival);
    CommerceSites.Site nearest = null;
    for (CommerceSites.Site home : data.commerce.chosen(SolsticioCommerce.residentCap())) {
      if (home.role != CommerceRules.Role.TOWNSFOLK || home.entity == null) continue;
      if (nearest == null || home.pos.distSqr(anchor) < nearest.pos.distSqr(anchor)) nearest = home;
    }
    if (nearest == null) return null;
    data.elder = nearest.pos;
    data.setDirty();
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    if (level != null && level.getEntity(nearest.entity) instanceof Villager villager) nameElder(villager);
    return data.elder;
  }

  static boolean isElder(MinecraftServer server, Villager villager) {
    CompoundTag data = SolsticioCommerce.data(villager);
    BlockPos elder = SolsticioData.get(server).elder;
    return data != null && elder != null && CommerceRules.Role.TOWNSFOLK.id.equals(data.getString("role"))
        && data.contains("site", Tag.TAG_LONG) && data.getLong("site") == elder.asLong();
  }

  static void nameElder(Villager villager) {
    villager.setCustomName(Component.translatable("entrelumen.solsticio.elder"));
  }

  /** A common villager: Anselmo takes the map; everyone else says the line of the moment. */
  public static boolean townsfolk(ServerPlayer player, Villager villager) {
    return elderTalk(player, villager) != null;
  }

  public static Talk elderTalk(ServerPlayer player, Villager villager) {
    if (!isElder(player.server, villager)) return null;
    Talk talk = new Talk(player);
    Campaigns.Campaign c = campaign(player);
    SolsticioStoryRules.Errand map = SolsticioStoryRules.Errand.CARTOGRAPHER_MAP;
    if (SolsticioStoryRules.taken(c, map) && ready(player, map)) {
      consume(player, map);
      finish(player, c, map, talk, villager.getDisplayName());
    } else talk.say(villager.getDisplayName(), "entrelumen.solsticio.elder.line");
    return talk;
  }

  // ---- Canela, the lost cat ------------------------------------------------------------------

  /**
   * Chooses the team's lost cat's roof near the inn (once) and places the cat there as soon as that
   * chunk is loaded; finding it only needs the spot, never the entity.
   */
  static SolsticioData.ErrandCat ensureCat(ServerPlayer player, BlockPos innkeeper) {
    UUID id = campaignId(player);
    ServerLevel level = player.server.getLevel(Solsticio.LEVEL);
    if (id == null || level == null) return null;
    SolsticioData data = SolsticioData.get(player.server);
    var known = data.cats.get(id);
    if (known == null) {
      BlockPos from = innkeeper != null ? innkeeper : data.arrival != null ? data.arrival : player.blockPosition();
      known = new SolsticioData.ErrandCat(roof(level, data, from).immutable(), null);
      data.cats.put(id, known);
      data.setDirty();
    }
    return placeCat(level, data, id, known);
  }

  /** Spawns the cat on its roof when its chunk and entities are loaded and it is not there yet. */
  static SolsticioData.ErrandCat placeCat(ServerLevel level, SolsticioData data, UUID id, SolsticioData.ErrandCat known) {
    BlockPos spot = known.pos();
    if (!level.isLoaded(spot) || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(spot))) return known;
    if (known.entity() != null && level.getEntity(known.entity()) != null) return known;
    Cat cat = EntityType.CAT.create(level);
    if (cat == null) return known;
    cat.setVariant(BuiltInRegistries.CAT_VARIANT.getHolderOrThrow(CatVariant.RED));
    cat.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
    cat.setNoAi(true);
    cat.setInvulnerable(true);
    cat.setPersistenceRequired();
    cat.setInSittingPose(true);
    cat.setCustomName(Component.translatable("entrelumen.solsticio.errand.lost_cat.name"));
    cat.addTag(CAT_TAG);
    cat.getPersistentData().putUUID(CAT_CAMPAIGN, id);
    var placed = new SolsticioData.ErrandCat(spot, cat.getUUID());
    // Recorded first: the join check lets only the recorded cat in.
    data.cats.put(id, placed);
    data.setDirty();
    if (!level.addFreshEntity(cat)) {
      data.cats.put(id, known);
      return known;
    }
    return placed;
  }

  /**
   * A roof near {@code from}: the top of the nearest loaded common villager's house, else of the
   * inn itself, else the inn's floor. Standing on the street below is close enough to be seen.
   */
  static BlockPos roof(ServerLevel level, SolsticioData data, BlockPos from) {
    List<BlockPos> homes = new ArrayList<>();
    for (CommerceSites.Site site : data.commerce.homes()) if (site.pos.distSqr(from) <= 48 * 48) homes.add(site.pos);
    homes.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(from)));
    homes.add(from);
    for (BlockPos home : homes) {
      if (!level.isLoaded(home) || !level.isLoaded(home.above(40))) continue;
      for (int y = home.getY() + 40; y >= home.getY() + 3; y--) {
        BlockPos top = new BlockPos(home.getX(), y, home.getZ());
        if (!level.getBlockState(top).isAir() && level.getBlockState(top.above()).isAir()
            && level.getBlockState(top.above(2)).isAir()
            && !level.getBlockState(top).getCollisionShape(level, top).isEmpty()) return top.above();
      }
    }
    return from;
  }

  static void removeCat(MinecraftServer server, UUID campaign) {
    if (campaign == null) return;
    SolsticioData data = SolsticioData.get(server);
    var cat = data.cats.remove(campaign);
    if (cat == null) return;
    data.setDirty();
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    Entity entity = level == null || cat.entity() == null ? null : level.getEntity(cat.entity());
    if (entity != null) {
      level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + 0.6, entity.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
      entity.discard();
    }
  }

  /** "north", "south-east"...: where {@code to} lies from {@code from}, as a translated word. */
  static Component direction(BlockPos from, BlockPos to) {
    int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
    if (dx * dx + dz * dz < 4) return Component.translatable("entrelumen.solsticio.direction.here");
    // Minecraft: north is -z, east is +x.
    double angle = Math.toDegrees(Math.atan2(dx, -dz));
    int sector = Math.floorMod((int) Math.round(angle / 45.0), 8);
    return Component.translatable("entrelumen.solsticio.direction." + sector);
  }

  // ---- places: easter eggs, the secret garden and the cat --------------------------------------

  static boolean near(Vec3 at, BlockPos spot, int radius, int height) {
    double dx = at.x - (spot.getX() + 0.5), dz = at.z - (spot.getZ() + 0.5);
    return dx * dx + dz * dz <= radius * radius && Math.abs(at.y - spot.getY()) <= height;
  }

  static void tick(MinecraftServer server) {
    int tick = server.getTickCount();
    if (tick % 20 == 0) {
      ServerLevel level = server.getLevel(Solsticio.LEVEL);
      if (level != null && !level.players().isEmpty() && SolsticioData.get(server).ready())
        for (ServerPlayer player : List.copyOf(level.players())) if (!player.isSpectator()) visit(player);
    }
    if (tick % 100 == 0) electionsIfDue(server);
  }

  /** What a player in Solsticio finds by walking: easter eggs, the secret garden, the lost cat. */
  public static Talk visit(ServerPlayer player) {
    Talk talk = new Talk(player);
    Campaigns.Campaign c = campaign(player);
    if (!SolsticioStoryRules.arrived(c) || !player.level().dimension().equals(Solsticio.LEVEL)) return talk;
    SolsticioData data = SolsticioData.get(player.server);
    Vec3 at = player.position();
    data.commerce.easterEggs.forEach((egg, spots) -> {
      if (SolsticioStoryRules.EGGS.contains(egg) && !SolsticioStoryRules.discovered(c, egg)
          && spots.stream().anyMatch(spot -> near(at, spot, EGG_RADIUS, EGG_HEIGHT)))
        discoverEgg(player, c, egg, talk);
    });
    var garden = SolsticioStoryRules.Errand.GARDEN_FLOWERS;
    if (SolsticioStoryRules.taken(c, garden) && !atInnkeeper(player.server, garden) && ready(player, garden)
        && data.commerce.easterEggs.get(garden.place).stream().anyMatch(spot -> near(at, spot, GARDEN_RADIUS, EGG_HEIGHT + 1))) {
      consume(player, garden);
      finish(player, c, garden, talk, null);
    }
    var lost = SolsticioStoryRules.Errand.LOST_CAT;
    UUID id = campaignId(player);
    var cat = id == null ? null : data.cats.get(id);
    if (SolsticioStoryRules.taken(c, lost) && cat != null) {
      if (near(at, cat.pos(), CAT_RADIUS, CAT_HEIGHT)) finish(player, c, lost, talk, null);
      else if (near(at, cat.pos(), 48, 64)) {
        placeCat(player.serverLevel(), data, id, cat);
        if (near(at, cat.pos(), 24, 32) && player.getRandom().nextInt(4) == 0)
          player.serverLevel().playSound(null, cat.pos(), SoundEvents.CAT_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
      }
    }
    return talk;
  }

  /** An easter egg found for the first time by the team: a lore page and a keepsake, once. */
  static void discoverEgg(ServerPlayer player, Campaigns.Campaign c, String egg, Talk talk) {
    if (!SolsticioStoryRules.discover(c, egg)) return;
    saved(player);
    Solsticio.give(player, loreBook("egg_" + egg, 2));
    Solsticio.give(player, keepsake(egg));
    talk.narrate("entrelumen.solsticio.egg." + egg + ".found");
    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
        0.8F, 1.3F);
    if (SolsticioStoryRules.revealRumour(c)) {
      Solsticio.give(player, loreBook("rumour", 3));
      talk.narrate("entrelumen.solsticio.egg.rumour");
    }
  }

  /** The decorative keepsake of each easter egg. */
  public static ItemStack keepsake(String egg) {
    Item item = switch (egg) {
      case "tavern" -> Items.DECORATED_POT;
      case "secret_garden" -> Items.FLOWERING_AZALEA;
      case "sundial" -> Items.LIGHTNING_ROD;
      default -> Items.PAPER;
    };
    return named(item, "entrelumen.solsticio.egg." + egg + ".keepsake", true);
  }

  // ---- events ----------------------------------------------------------------------------

  /** Tobías's errand: a flame in Bodhi's chapel with four candles in the pack. */
  static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(Solsticio.LEVEL))
      return;
    Talk talk = lightCandles(player, event.getItemStack(), event.getPos());
    if (talk == null) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
  }

  /** The chapel's candles; null when this click is not the errand. */
  public static Talk lightCandles(ServerPlayer player, ItemStack flame, BlockPos clicked) {
    if (!flame.is(Items.FLINT_AND_STEEL) && !flame.is(Items.FIRE_CHARGE)) return null;
    var errand = SolsticioStoryRules.Errand.CHAPEL_CANDLES;
    Campaigns.Campaign c = campaign(player);
    if (!SolsticioStoryRules.taken(c, errand)) return null;
    SolsticioData data = SolsticioData.get(player.server);
    BlockPos chapel = data.npcs.get(errand.place);
    if (chapel == null || !near(Vec3.atCenterOf(clicked), chapel, CHAPEL_RADIUS, CHAPEL_HEIGHT)) return null;
    Talk talk = new Talk(player);
    if (!ready(player, errand)) {
      talk.narrate("entrelumen.solsticio.errand.chapel_candles.more", CANDLES);
      return talk;
    }
    consume(player, errand);
    if (flame.is(Items.FIRE_CHARGE)) flame.shrink(1);
    else flame.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    ServerLevel level = player.server.getLevel(Solsticio.LEVEL);
    if (level != null && level.isLoaded(chapel)) {
      BlockPos.betweenClosedStream(chapel.offset(-CHAPEL_RADIUS, -2, -CHAPEL_RADIUS), chapel.offset(CHAPEL_RADIUS, 4, CHAPEL_RADIUS))
          .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof CandleBlock).limit(16)
          .forEach(pos -> level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
              6, 0.15, 0.1, 0.15, 0.01));
      level.playSound(null, clicked, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
    finish(player, c, errand, talk, character("priest"));
    return talk;
  }

  /** Errand items are keepsakes of a favour: the map never draws, the book never opens. */
  static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
    if (errandOf(event.getItemStack()) == null) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.FAIL);
  }

  /** The team's lost cat answers nobody's hand: it comes down by itself (see {@link #visit}). */
  static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
    if (!event.getTarget().getTags().contains(CAT_TAG)) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
    if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
      player.displayClientMessage(Component.translatable("entrelumen.solsticio.errand.lost_cat.shy"), true);
  }

  /** Amparo's shears lie in Solsticio's water: the first cast with the errand brings them up. */
  static void onFished(ItemFishedEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) fish(player);
  }

  public static boolean fish(ServerPlayer player) {
    var errand = SolsticioStoryRules.Errand.RIVER_TOOL;
    if (!player.level().dimension().equals(Solsticio.LEVEL) || !SolsticioStoryRules.taken(campaign(player), errand)
        || count(player, errandItemOf(errand)) > 0) return false;
    Solsticio.give(player, errandItem(errand));
    player.sendSystemMessage(Component.translatable("entrelumen.solsticio.errand.river_tool.fished")
        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    return true;
  }

  /** A stale lost cat (its errand ended while unloaded) fades when its chunk loads. */
  static void onJoin(EntityJoinLevelEvent event) {
    if (!(event.getEntity() instanceof Cat cat) || !cat.getTags().contains(CAT_TAG)
        || !(event.getLevel() instanceof ServerLevel level)) return;
    CompoundTag data = cat.getPersistentData();
    var known = data.hasUUID(CAT_CAMPAIGN) ? SolsticioData.get(level.getServer()).cats.get(data.getUUID(CAT_CAMPAIGN)) : null;
    if (known == null || !cat.getUUID().equals(known.entity())) event.setCanceled(true);
  }

  /**
   * Every villager the city spawned wears the Heliodor clothing, also those placed before it
   * existed; characters get their profession back and Anselmo his name.
   */
  static void dress(ServerLevel level, Villager villager, CompoundTag data, CommerceRules.Role role) {
    if (role == CommerceRules.Role.MOVED) return;
    var villagerData = villager.getVillagerData();
    if (role == CommerceRules.Role.CHARACTER) {
      var profession = CHARACTER_PROFESSIONS.get(data.getString("key"));
      if (profession != null && villagerData.getProfession() != profession.get())
        villagerData = villagerData.setProfession(profession.get());
    }
    if (villagerData.getType() != HELIODOR.get()) villagerData = villagerData.setType(HELIODOR.get());
    if (villagerData != villager.getVillagerData()) villager.setVillagerData(villagerData);
    if (role == CommerceRules.Role.TOWNSFOLK && isElder(level.getServer(), villager)) nameElder(villager);
  }

  // ---- liberation and elections ------------------------------------------------------------

  /** The first opened portal frees the Entrelumen: prices fall and the elections are called. */
  static void liberate(MinecraftServer server) {
    if (SolsticioData.get(server).liberated) return;
    SolsticioCommerce.setLiberated(server, true);
    server.getPlayerList().broadcastSystemMessage(Component.translatable("entrelumen.solsticio.story.liberated")
        .withStyle(ChatFormatting.GOLD), false);
  }

  /** Holds the elections when their day has come; true when they were held now. */
  public static boolean electionsIfDue(MinecraftServer server) {
    return electionsIfDue(server, server.overworld().getGameTime());
  }

  static boolean electionsIfDue(MinecraftServer server, long now) {
    SolsticioData data = SolsticioData.get(server);
    if (data.liberated && data.liberatedAt == 0) {
      // Liberated before the elections existed: the countdown starts now.
      data.liberatedAt = Math.max(1, now);
      data.setDirty();
    }
    if (!SolsticioStoryRules.electionDue(data.liberated, data.liberatedAt, now, data.electionsHeld)) return false;
    holdElections(server, now);
    return true;
  }

  /** The square votes: Aurelia is re-elected. Changes what Solsticio says from now on. */
  public static void holdElections(MinecraftServer server) {
    holdElections(server, server.overworld().getGameTime());
  }

  static void holdElections(MinecraftServer server, long now) {
    SolsticioData data = SolsticioData.get(server);
    if (data.electionsHeld) return;
    data.electionsHeld = true;
    data.electionsAt = now;
    data.setDirty();
    server.getPlayerList().broadcastSystemMessage(Component.translatable("entrelumen.solsticio.story.elections")
        .withStyle(ChatFormatting.GOLD), false);
    ServerLevel level = server.getLevel(Solsticio.LEVEL);
    if (level != null && data.arrival != null && level.isLoaded(data.arrival)) {
      level.sendParticles(ParticleTypes.FIREWORK, data.arrival.getX() + 0.5, data.arrival.getY() + 3, data.arrival.getZ() + 0.5,
          60, 3, 2, 3, 0.05);
      level.playSound(null, data.arrival, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
    }
    LOGGER.info("Solsticio held its elections at game time {}", data.electionsAt);
  }

  // ---- commands --------------------------------------------------------------------------

  private static void commands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("solsticio")
                .then(Commands.literal("story")
                    .executes(ctx -> status(ctx.getSource()))
                    .then(Commands.literal("elections").executes(ctx -> {
                      var server = ctx.getSource().getServer();
                      if (!SolsticioData.get(server).liberated) {
                        ctx.getSource().sendFailure(Component.literal("The Entrelumen is not liberated yet"));
                        return 0;
                      }
                      holdElections(server);
                      ctx.getSource().sendSuccess(() -> Component.literal("Solsticio held its elections"), true);
                      return 1;
                    }))
                    .then(Commands.literal("reset").executes(ctx -> {
                      var player = ctx.getSource().getPlayerOrException();
                      var campaign = Entrelumen.current(player);
                      campaign.completed.removeIf(id -> id.startsWith(SolsticioStoryRules.PREFIX)
                          || (SolsticioStoryRules.MISSIONS.contains(id) && !id.equals(SolsticioStoryRules.ARRIVAL)));
                      saved(player);
                      removeCat(player.server, campaignId(player));
                      ctx.getSource().sendSuccess(() -> Component.literal("Act VI story reset for your campaign"), true);
                      return 1;
                    }))))));
  }

  private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var player = source.getPlayerOrException();
    var campaign = Entrelumen.current(player);
    var data = SolsticioData.get(source.getServer());
    List<String> lines = new ArrayList<>();
    StringBuilder missions = new StringBuilder("missions:");
    for (String mission : SolsticioStoryRules.MISSIONS)
      missions.append(' ').append(mission.replace("solsticio_", "")).append(campaign.completed.contains(mission) ? "+" : "-");
    lines.add(missions.toString());
    StringBuilder errands = new StringBuilder("relation=" + SolsticioStoryRules.relation(campaign) + " errands:");
    for (var errand : SolsticioStoryRules.Errand.values())
      errands.append(' ').append(errand.id).append('=').append(SolsticioStoryRules.state(campaign, errand));
    lines.add(errands.toString());
    StringBuilder eggs = new StringBuilder("eggs:");
    for (String egg : SolsticioStoryRules.EGGS) eggs.append(' ').append(egg).append(SolsticioStoryRules.discovered(campaign, egg) ? "+" : "-");
    lines.add(eggs + " rumour=" + campaign.completed.contains(SolsticioStoryRules.RUMOUR)
        + " relics=" + SolsticioStoryRules.relicsPresented(campaign) + " heart=" + heartState(player, campaign));
    lines.add("world: liberated=" + data.liberated + " at=" + data.liberatedAt + " elections=" + data.electionsHeld
        + " at=" + data.electionsAt + " now=" + source.getServer().overworld().getGameTime() + " elder=" + data.elder
        + " cats=" + data.cats.size());
    lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
    return 1;
  }

}
