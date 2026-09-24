package dev.entrelumen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

/**
 * The Heart of Heliodor ({@code entrelumen:heart_of_heliodor}): the sacred crystal the Sun Spirit took
 * from Bodhi. Rules in {@link HeliodorHeartRules}.
 *
 * <p><b>Source.</b> A global loot modifier on the Aether's {@code aether:entities/sun_spirit} loot
 * table (Aether 1.5.10, the pinned JAR: that table always drops the gold dungeon key and the sun
 * altar, and {@code SunSpirit.die} reaches vanilla's death drops). When the player credited with the
 * kill ({@code LAST_DAMAGE_PLAYER}: the one who reflected the killing ice crystal) is a real player
 * whose campaign has no Heart yet, the Heart joins that loot and the campaign records it. Fake
 * players, a second Sun Spirit and archived campaigns get nothing. The dungeon's reward chest was
 * not used: it is one shared chest per dungeon, filled once for whoever opens it first.
 *
 * <p><b>Safety.</b> Stack of one, epic, fireproof. As a dropped item it takes no damage at all
 * (cactus, explosions, lava), never despawns, and when it falls below the world it returns to the
 * last ground it rested on (else it hovers where it first appeared). Aether islands float over the
 * void; that is where it drops.
 */
public final class HeliodorHeart {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
      DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "entrelumen");

  /** The Sun Spirit's own loot table in the pinned Aether JAR. */
  public static final ResourceKey<LootTable> SUN_SPIRIT_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
      ResourceLocation.fromNamespaceAndPath("aether", "entities/sun_spirit"));
  public static final ResourceLocation SUN_SPIRIT = ResourceLocation.fromNamespaceAndPath("aether", "sun_spirit");

  public static final DeferredItem<Heart> ITEM = ITEMS.register("heart_of_heliodor",
      () -> new Heart(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
  static final Supplier<MapCodec<DropModifier>> DROP =
      LOOT_MODIFIERS.register("heart_of_heliodor", () -> DropModifier.CODEC);

  static final String GROUND = "entrelumen_heart_ground";
  static final String ORIGIN = "entrelumen_heart_origin";

  private HeliodorHeart() {}

  static void register(IEventBus bus) {
    ITEMS.register(bus);
    LOOT_MODIFIERS.register(bus);
    NeoForge.EVENT_BUS.addListener(HeliodorHeart::commands);
  }

  /**
   * A defeated Sun Spirit gives {@code player}'s campaign its Heart: true, with the campaign saved and
   * the team told, only the first time for that campaign.
   */
  public static boolean claim(ServerPlayer player) {
    if (player instanceof FakePlayer) return false;
    Campaigns.Campaign campaign;
    try {
      campaign = Entrelumen.current(player);
    } catch (RuntimeException noTeam) {
      return false;
    }
    if (!HeliodorHeartRules.claim(campaign)) return false;
    CampaignData.get(player.server).setDirty();
    var message = Component.translatable("entrelumen.heart.recovered", player.getDisplayName())
        .withStyle(ChatFormatting.GOLD);
    var id = CampaignActions.campaignId(player);
    for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
      try {
        if (CampaignActions.campaignId(member).equals(id)) member.sendSystemMessage(message);
      } catch (RuntimeException noTeam) {
        // A player without a team yet is simply not told.
      }
    }
    return true;
  }

  /**
   * Act VI: the Atlas hands the Heart back once, for Bodhi's blessing. Gives the item to {@code
   * player} (inventory, else at their feet) and records {@link HeliodorHeartRules#RELEASED}.
   */
  public static boolean release(ServerPlayer player) {
    var campaign = Entrelumen.current(player);
    if (!HeliodorHeartRules.release(campaign)) return false;
    CampaignData.get(player.server).setDirty();
    Solsticio.give(player, new ItemStack(ITEM.get()));
    player.sendSystemMessage(Component.translatable("entrelumen.heart.released"));
    return true;
  }

  /** Keeps a dropped Heart in the world: see the class comment. */
  static void keepInWorld(ServerLevel level, ItemEntity entity) {
    CompoundTag data = entity.getPersistentData();
    int floor = level.getMinBuildHeight();
    if (!data.contains(ORIGIN)) data.put(ORIGIN, NbtUtils.writeBlockPos(entity.blockPosition()));
    if (entity.onGround() && entity.getY() >= floor) {
      BlockPos here = entity.blockPosition();
      var previous = NbtUtils.readBlockPos(data, GROUND);
      if (previous.isEmpty() || !previous.get().equals(here)) data.put(GROUND, NbtUtils.writeBlockPos(here));
      return;
    }
    if (entity.getY() >= floor) return;
    var ground = NbtUtils.readBlockPos(data, GROUND);
    BlockPos target = ground.orElseGet(() -> NbtUtils.readBlockPos(data, ORIGIN).orElse(entity.blockPosition()));
    if (target.getY() < floor) target = target.atY(floor + 1);
    Vec3 to = Vec3.atBottomCenterOf(target);
    entity.teleportTo(to.x, to.y + 0.1, to.z);
    entity.setDeltaMovement(Vec3.ZERO);
    // Without ground to return to, it waits where it first appeared instead of falling again.
    if (ground.isEmpty()) entity.setNoGravity(true);
    entity.setGlowingTag(true);
    LOGGER.info("Heart of Heliodor fell below {} and returned to {}", level.dimension().location(), target);
  }

  private static void commands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("heart")
                .then(Commands.literal("give").executes(ctx -> {
                  // Recovery for a lost Heart: records it for the player's campaign and gives one.
                  var player = ctx.getSource().getPlayerOrException();
                  var campaign = Entrelumen.current(player);
                  if (!campaign.completed.contains(HeliodorHeartRules.PROJECT))
                    campaign.completed.add(HeliodorHeartRules.RECOVERED);
                  CampaignData.get(player.server).setDirty();
                  Solsticio.give(player, new ItemStack(ITEM.get()));
                  return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                  var campaign = Entrelumen.current(ctx.getSource().getPlayerOrException());
                  String line = "recovered=" + HeliodorHeartRules.recovered(campaign)
                      + " inAtlas=" + HeliodorHeartRules.inAtlas(campaign)
                      + " released=" + campaign.completed.contains(HeliodorHeartRules.RELEASED)
                      + " canRelease=" + HeliodorHeartRules.canRelease(campaign);
                  ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                  return 1;
                }))
                .then(Commands.literal("release").executes(ctx ->
                    release(ctx.getSource().getPlayerOrException()) ? 1 : 0)))));
  }

  /** Stack of one, no recipe; a single line of lore and the protections of a dropped Heart. */
  public static final class Heart extends Item {
    public Heart(Properties properties) {
      super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
      tooltip.add(Component.translatable("entrelumen.heart_of_heliodor.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
      return false;
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
      return Integer.MAX_VALUE;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
      if (entity.level() instanceof ServerLevel level) keepInWorld(level, entity);
      return false;
    }
  }

  /** Adds the Heart to a Sun Spirit's death loot for a campaign that has none yet. */
  public static final class DropModifier extends LootModifier {
    public static final MapCodec<DropModifier> CODEC =
        RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, DropModifier::new));

    public DropModifier(LootItemCondition[] conditions) {
      super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
      return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
      return addHeart(loot, context);
    }

    /** The modifier's rule without its conditions; GameTests call it with a built loot context. */
    static ObjectArrayList<ItemStack> addHeart(ObjectArrayList<ItemStack> loot, LootContext context) {
      // Only the Sun Spirit's own death loot: a killed entity, a damage source and a credited player.
      if (!SUN_SPIRIT_LOOT.location().equals(context.getQueriedLootTableId())
          || !context.hasParam(LootContextParams.THIS_ENTITY)
          || !context.hasParam(LootContextParams.DAMAGE_SOURCE)) return loot;
      Player player = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
      if (player instanceof ServerPlayer serverPlayer && claim(serverPlayer)) loot.add(new ItemStack(ITEM.get()));
      return loot;
    }
  }
}
