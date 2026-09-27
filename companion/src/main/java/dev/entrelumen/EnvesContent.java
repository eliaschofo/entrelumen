package dev.entrelumen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The content of the Envés (docs/design/dungeon-enves.md, «Contenido»): what it registers (the sour
 * light shard, the puzzle blocks, the White Wither and its skull, the loot vocabulary, the echo
 * attachment), the hooks it plugs into the engine ({@link EnvesHooks}) and its ticking.
 */
public final class EnvesContent {
  private EnvesContent() {}

  static final String MOD = "entrelumen";
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD);
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
  static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD);
  static final DeferredRegister<LootPoolEntryType> LOOT_ENTRIES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, MOD);
  static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, MOD);
  static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, MOD);
  static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD);

  /** The Envés's currency (Elias, 27/9): paid at the gate instead of a Nether star, traded later in Solsticio. */
  public static final DeferredItem<Shard> SOUR_LIGHT_SHARD = ITEMS.register("sour_light_shard",
      () -> new Shard(new Item.Properties().rarity(Rarity.UNCOMMON)));

  private static BlockBehaviour.Properties fixed(MapColor color, SoundType sound, int light) {
    return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3_600_000.0F).noLootTable().sound(sound)
        .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false).lightLevel(state -> light);
  }

  public static final DeferredBlock<EnvesContentBlocks.Brazier> BRAZIER = BLOCKS.register("enves_brazier",
      () -> new EnvesContentBlocks.Brazier(fixed(MapColor.COLOR_BLACK, SoundType.STONE, 0).noOcclusion()
          .lightLevel(state -> state.getValue(EnvesContentBlocks.Brazier.LIT) ? 14 : 0)));
  public static final DeferredBlock<EnvesContentBlocks.Mirror> MIRROR = BLOCKS.register("enves_mirror",
      () -> new EnvesContentBlocks.Mirror(fixed(MapColor.QUARTZ, SoundType.AMETHYST, 3).noOcclusion()));
  public static final DeferredBlock<EnvesContentBlocks.Glyph> GLYPH = BLOCKS.register("enves_glyph",
      () -> new EnvesContentBlocks.Glyph(fixed(MapColor.STONE, SoundType.STONE, 0)));
  public static final DeferredBlock<EnvesContentBlocks.Shrine> SHRINE = BLOCKS.register("enves_shrine",
      () -> new EnvesContentBlocks.Shrine(fixed(MapColor.COLOR_PURPLE, SoundType.LODESTONE, 0)
          .lightLevel(state -> state.getValue(EnvesContentBlocks.Shrine.SPENT) ? 2 : 13)));

  public static final DeferredHolder<EntityType<?>, EntityType<WhiteWither>> WHITE_WITHER = ENTITIES.register("white_wither",
      () -> EntityType.Builder.<WhiteWither>of(WhiteWither::new, MobCategory.MONSTER).sized(0.9F, 3.5F).fireImmune()
          .immuneTo(Blocks.WITHER_ROSE).clientTrackingRange(10).build("white_wither"));
  public static final DeferredHolder<EntityType<?>, EntityType<SourSkull>> SOUR_SKULL = ENTITIES.register("sour_skull",
      () -> EntityType.Builder.<SourSkull>of(SourSkull::new, MobCategory.MISC).sized(0.3125F, 0.3125F).clientTrackingRange(4)
          .updateInterval(10).build("sour_skull"));

  public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> LOOT_GEAR =
      LOOT_ENTRIES.register("enves_gear", () -> new LootPoolEntryType(EnvesLoot.Gear.CODEC));
  public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> LOOT_GEM =
      LOOT_ENTRIES.register("enves_gem", () -> new LootPoolEntryType(EnvesLoot.Gem.CODEC));
  public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> LOOT_MATERIAL =
      LOOT_ENTRIES.register("enves_material", () -> new LootPoolEntryType(EnvesLoot.Material.CODEC));
  public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<EnvesLoot.FloorBonus>> LOOT_FLOOR_BONUS =
      LOOT_FUNCTIONS.register("enves_floor_bonus", () -> new LootItemFunctionType<>(EnvesLoot.FloorBonus.CODEC));
  public static final DeferredHolder<LootItemConditionType, LootItemConditionType> LOOT_FLOOR =
      LOOT_CONDITIONS.register("enves_floor", () -> new LootItemConditionType(EnvesLoot.FloorCondition.CODEC));

  static {
    EnvesEchoes.ATTACHMENT = ATTACHMENTS.register("enves_echo",
        () -> AttachmentType.builder(() -> EnvesEchoes.Data.NONE).serialize(EnvesEchoes.Data.CODEC).build());
  }

  /** Composes the content's lifecycle: cleanup, dressing the seals and the boss chest. */
  static final EnvesHooks.Lifecycle LIFECYCLE = new EnvesHooks.Lifecycle() {
    @Override
    public void floorReady(EnvesHooks.Floor floor) {
      EnvesSeals.floorReady(floor);
    }

    @Override
    public void attemptEnded(EnvesData.Attempt attempt, EnvesHooks.EndReason reason) {
      MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
      EnvesSeals.forget(attempt.id);
      if (server != null) EnvesRuns.get(server).remove(attempt.id);
    }

    @Override
    public void bossDefeated(EnvesData.Attempt attempt) {
      EnvesBoss.bossDefeated(attempt);
    }
  };

  static void register(IEventBus bus) {
    ITEMS.register(bus);
    BLOCKS.register(bus);
    ENTITIES.register(bus);
    LOOT_ENTRIES.register(bus);
    LOOT_FUNCTIONS.register(bus);
    LOOT_CONDITIONS.register(bus);
    ATTACHMENTS.register(bus);
    bus.addListener((EntityAttributeCreationEvent event) -> event.put(WHITE_WITHER.get(), WhiteWither.createAttributes().build()));
    NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new EnvesContentConfig.Listener()));
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
      EnvesPuzzles.forget();
      EnvesSeals.forgetAll();
      EnvesEchoes.LIVE.clear();
    });
    EnvesEchoes.register();
    EnvesShrines.register();
    EnvesPuzzles.register();
    install();
  }

  /** Plugs the content into the engine's hooks. */
  public static void install() {
    EnvesHooks.setEncounters(EnvesEncounters::roomEntered);
    EnvesHooks.setChests(EnvesChests::place);
    EnvesHooks.setShrines(EnvesShrines::place);
    EnvesHooks.setVaults(EnvesVaults::place);
    EnvesHooks.setSeals(EnvesSeals.HOOK);
    EnvesHooks.setBoss(EnvesBoss::floorReady);
    EnvesHooks.setLifecycle(LIFECYCLE);
  }

  static void tick(MinecraftServer server) {
    EnvesEchoes.tick(server);
    EnvesPuzzles.tick(server);
    EnvesSeals.tick(server);
    EnvesShrines.tick(server);
    EnvesBoss.tick(server);
    if (server.getTickCount() % 10 == 7) track(server);
  }

  /**
   * Twice a second, for each member inside their attempt: seal rooms (the guardian, the puzzle's
   * first echo), the braziers' gates they come near, and the stair's champion if it went missing.
   */
  static void track(MinecraftServer server) {
    ServerLevel level = Enves.level(server);
    if (level == null) return;
    for (ServerPlayer player : level.players()) {
      if (player.isSpectator() || player.isDeadOrDying()) continue;
      var found = Enves.attemptAt(server, player.blockPosition());
      if (found.isEmpty() || found.get().status != EnvesData.Status.OPEN || !found.get().team.equals(Enves.teamOf(player))) continue;
      var attempt = found.get();
      int depth = EnvesGeometry.depthAt(player.getBlockY());
      if (depth < 1 || attempt.floor(depth).placement != EnvesData.Placement.READY) continue;
      int cell = EnvesGeometry.cellAt(attempt.slot, player.getBlockX(), player.getBlockZ());
      var layout = Enves.layout(attempt, depth);
      if (!layout.has(cell) || !EnvesGeometry.interior(attempt.slot, player.getBlockX(), player.getBlockZ())) continue;
      var role = layout.role(cell);
      var floor = Enves.floor(level, attempt, depth);
      if (role == EnvesLayout.Role.SEAL) EnvesSeals.presence(floor, cell, player);
      if (role == EnvesLayout.Role.GUARD) EnvesEncounters.checkChampion(level, attempt, depth, player);
      EnvesVaults.near(level, attempt, depth, player);
    }
  }

  /** The sour light shard: a line of lore and what it is for. */
  public static final class Shard extends Item {
    public Shard(Properties properties) {
      super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.entrelumen.sour_light_shard.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      tooltip.add(Component.translatable("item.entrelumen.sour_light_shard.use").withStyle(ChatFormatting.BLUE));
    }
  }

  static boolean shard(ItemStack stack) {
    return stack.is(SOUR_LIGHT_SHARD.get()) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("sour_light_shard");
  }
}
