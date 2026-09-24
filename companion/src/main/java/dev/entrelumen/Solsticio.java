package dev.entrelumen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Act VI: Solsticio, the Heliodor capital frozen inside the Entrelumen. Registers the Light Key,
 * its broken return form, the three placeholder relics and the portal anchor, and wires the city,
 * travel and protection listeners. The dimension itself is data ({@code dimension/solsticio.json}).
 */
public final class Solsticio {
  public static final ResourceKey<Level> LEVEL =
      ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("entrelumen", "solsticio"));
  public static final String REGION_ID = "entrelumen:solsticio";
  public static final String RIFT_REGION_ID = "entrelumen:solsticio_rift";
  /** Solsticio opens to a team once it activated the Ark (current code: act 6, last_horizon). */
  public static final ProtectionRules.ActGate GATE =
      new ProtectionRules.ActGate(6, CampaignMilestones.LAST_HORIZON);

  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.DataComponents COMPONENTS =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "entrelumen");

  /** The player a broken Light Key answers to. */
  public record KeyOwner(UUID id, String name) {
    public static final Codec<KeyOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(KeyOwner::id),
        Codec.STRING.optionalFieldOf("name", "").forGetter(KeyOwner::name)).apply(instance, KeyOwner::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyOwner> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, KeyOwner::id, ByteBufCodecs.STRING_UTF8, KeyOwner::name, KeyOwner::new);
  }

  public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeyOwner>> KEY_OWNER =
      COMPONENTS.registerComponentType("light_key_owner",
          builder -> builder.persistent(KeyOwner.CODEC).networkSynchronized(KeyOwner.STREAM_CODEC));

  public static final DeferredItem<LightKeyItem> LIGHT_KEY = ITEMS.register("light_key",
      () -> new LightKeyItem(false, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
  public static final DeferredItem<LightKeyItem> LIGHT_KEY_BROKEN = ITEMS.register("light_key_broken",
      () -> new LightKeyItem(true, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
  /** Placeholder non-renewable pieces; future Solsticio missions award them. */
  public static final List<DeferredItem<Item>> RELICS = List.of(relic(1), relic(2), relic(3));

  public static final DeferredBlock<SolsticioPortalBlock> PORTAL = BLOCKS.register("solsticio_portal",
      () -> new SolsticioPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD)
          .strength(-1.0F, 3_600_000.0F).noLootTable().sound(SoundType.AMETHYST).noOcclusion()
          .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false)
          .lightLevel(state -> state.getValue(SolsticioPortalBlock.ARMED) ? 15 : 9)));

  /** Client presentation; the dimension works without it. */
  public static final ModConfigSpec CLIENT_SPEC;
  public static final ModConfigSpec.BooleanValue AMBIENT_MOTES;
  public static final ModConfigSpec.IntValue MOTE_DENSITY;

  static {
    var builder = new ModConfigSpec.Builder();
    builder.push("solsticio");
    AMBIENT_MOTES = builder.comment("Drifting motes of light around you in Solsticio (client only).")
        .translation("entrelumen.config.ambient_motes").define("ambientMotes", true);
    MOTE_DENSITY = builder.comment("Motes spawned per tick at most; the client particle setting also scales them.")
        .translation("entrelumen.config.mote_density").defineInRange("moteDensity", 2, 0, 8);
    builder.pop();
    CLIENT_SPEC = builder.build();
  }

  private Solsticio() {}

  private static DeferredItem<Item> relic(int index) {
    return ITEMS.register("heliodor_relic_" + index,
        () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()));
  }

  /** The relic number (1..3) of a stack, or 0. */
  public static int relicOf(ItemStack stack) {
    for (int i = 0; i < RELICS.size(); i++) if (stack.is(RELICS.get(i).get())) return i + 1;
    return 0;
  }

  static void register(IEventBus bus, ModContainer container) {
    ITEMS.register(bus);
    BLOCKS.register(bus);
    COMPONENTS.register(bus);
    if (container != null) container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    StructureProtection.register();
    StructureProtection.registerProvider(REGION_ID, SolsticioCity::regions);
    SolsticioTravel.register();
    SolsticioCommerce.register(bus, container);
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
      SolsticioCity.tick(event.getServer());
      SolsticioTravel.tick(event.getServer());
    });
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
      var data = SolsticioData.get(event.getServer());
      // Resume an interrupted placement; a finished city is never placed again.
      if (data.status == SolsticioData.Status.PLACING) SolsticioCity.ensure(event.getServer());
      SolsticioTravel.applyBorder(event.getServer());
    });
    NeoForge.EVENT_BUS.addListener(Solsticio::commands);
  }

  // ---- Light Key forging ------------------------------------------------------------------

  /**
   * The activated Ark forges one Light Key per team, handed to the player at the controller. A
   * team whose activation predates the key receives it on its next controller interaction.
   */
  public static boolean forgeKey(ServerPlayer player) {
    var campaign = Entrelumen.current(player);
    if (!campaign.completed.contains(CampaignMilestones.LAST_HORIZON) || campaign.archived) return false;
    UUID id = CampaignActions.campaignId(player);
    SolsticioData data = SolsticioData.get(player.server);
    if (!data.forgedKeys.add(id)) return false;
    data.setDirty();
    give(player, new ItemStack(LIGHT_KEY.get()));
    player.sendSystemMessage(Component.translatable("entrelumen.light_key.forged"));
    // Start placing the city now, so it is ready when the team first crosses.
    SolsticioCity.ensure(player.server);
    return true;
  }

  static void give(ServerPlayer player, ItemStack stack) {
    if (!player.getInventory().add(stack)) player.drop(stack, false);
    player.containerMenu.broadcastChanges();
  }

  // ---- Commands ---------------------------------------------------------------------------

  private static void commands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("protection").then(Commands.literal("bypass").executes(ctx -> {
              var player = ctx.getSource().getPlayerOrException();
              boolean on = StructureProtection.toggleBypass(player);
              ctx.getSource().sendSuccess(() -> Component.translatable(
                  on ? "entrelumen.protection.bypass_on" : "entrelumen.protection.bypass_off"), true);
              return 1;
            })))
            .then(Commands.literal("solsticio")
                .then(Commands.literal("place").executes(ctx -> {
                  boolean ready = SolsticioCity.ensure(ctx.getSource().getServer());
                  ctx.getSource().sendSuccess(() -> Component.literal(ready ? "Solsticio is ready" : "Solsticio placement running"), true);
                  return ready ? 1 : 0;
                }))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("relics").executes(ctx -> {
                  var player = ctx.getSource().getPlayerOrException();
                  RELICS.forEach(relic -> give(player, new ItemStack(relic.get())));
                  return 1;
                }))
                .then(Commands.literal("key").executes(ctx -> {
                  give(ctx.getSource().getPlayerOrException(), new ItemStack(LIGHT_KEY.get()));
                  return 1;
                }))
                .then(Commands.literal("plot").then(Commands.literal("release")
                    .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
                      boolean ok = SolsticioCity.releasePlot(ctx.getSource().getServer(),
                          IntegerArgumentType.getInteger(ctx, "index"));
                      ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Plot released" : "No such claimed plot"), true);
                      return ok ? 1 : 0;
                    })))))));
  }

  private static int info(CommandSourceStack source) {
    var data = SolsticioData.get(source.getServer());
    List<String> lines = new ArrayList<>();
    lines.add("status=" + data.status + " provisional=" + data.provisional + " placements=" + data.placements
        + " slices=" + data.slicesDone + " templates=" + data.templates);
    lines.add("footprint=" + data.footprint + " borderRadius=" + data.borderRadius);
    lines.add("arrival=" + data.arrival + " portal=" + data.portal + " waystone=" + data.waystone
        + " tradingHall=" + data.tradingHall + " npcs=" + data.npcs);
    for (int i = 0; i < data.plots.size(); i++) {
      var plot = data.plots.get(i);
      lines.add("plot " + i + " at " + plot.corner.toShortString() + " owner="
          + (plot.owner == null ? "-" : plot.owner + " (" + plot.ownerName + ")"));
    }
    lines.add("commerce: liberated=" + data.liberated + " sites=" + data.commerce.sites.size() + " easterEggs="
        + data.commerce.easterEggs.keySet() + " (details: /entrelumen admin solsticio commerce)");
    lines.add("portal armed=" + data.portalArmed + " relics=" + data.portalRelics + " waystone="
        + data.waystoneRegistered + " overworldRift=" + data.overworldPortal + " forgedKeys=" + data.forgedKeys.size());
    lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
    return 1;
  }
}
